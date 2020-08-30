/* Copyright (C) 2010-2011, Mamadou Diop.
*  Copyright (C) 2011, Doubango Telecom.
*  Copyright (C) 2011, Philippe Verney <verney(dot)philippe(AT)gmail(dot)com>
*
* Contact: Mamadou Diop <diopmamadou(at)doubango(dot)org>
*	
* This file is part of imsdroid Project (http://code.google.com/p/imsdroid)
*
* imsdroid is free software: you can redistribute it and/or modify it under the terms of 
* the GNU General Public License as published by the Free Software Foundation, either version 3 
* of the License, or (at your option) any later version.
*	
* imsdroid is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
* without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  
* See the GNU General Public License for more details.
*	
* You should have received a copy of the GNU General Public License along 
* with this program; if not, write to the Free Software Foundation, Inc., 
* 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/
package org.doubango.ngn.media;

import java.math.BigInteger;
import java.nio.ByteBuffer;

import net.quantum6.mediacodec.AndroidVideoDecoder;
import net.quantum6.mediacodec.MediaCodecData;

import org.doubango.ngn.events.NgnMediaPluginEventArgs;
import org.doubango.ngn.events.NgnMediaPluginEventTypes;
import org.doubango.tinyWRAP.ProxyVideoConsumer;
import org.doubango.tinyWRAP.ProxyVideoConsumerCallback;
import org.doubango.tinyWRAP.ProxyVideoFrame;

import android.content.Context;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

/**
 * Video consumer using SurfaceView
 */
public class NgnProxyVideoConsumerHW extends NgnProxyVideoConsumer{
	private static final String TAG = NgnProxyVideoConsumerHW.class.getCanonicalName();
	
	private final MyProxyVideoConsumerCallback mCallback;
	private final ProxyVideoConsumer mConsumer;
	private Context mContext;
	private MyProxyVideoConsumerPreview mPreview;
	private ByteBuffer mVideoFrame;

	private static Surface videoSurface;

    private static AndroidVideoDecoder videoDecoder;
    private static MediaCodecData mInputData;
    private static MediaCodecData mOutputData;
    private static byte[] dataBuffer;
    private static byte[] dataBufferKeyFrame;
    private static boolean startDecoderWithKeyFrame = false;


    protected NgnProxyVideoConsumerHW(BigInteger id, ProxyVideoConsumer consumer){
    	super(id, consumer);
    	mConsumer = consumer;
    	mCallback = new MyProxyVideoConsumerCallback(this);
    	mConsumer.setCallback(mCallback);

    	// Initialize video stream parameters with default values
    	mWidth  = DEFAULT_VIDEO_WIDTH;
    	mHeight = DEFAULT_VIDEO_HEIGHT;
    	mFps    = DEFAULT_VIDEO_FPS;
    	
    	videoSurface = null;
    	if (videoDecoder != null)
    	{
    	    videoDecoder.release();
    	    videoDecoder = null;
    	}
    }
    
    @Override
    public void invalidate(){
    	super.invalidate();
    	mVideoFrame = null;
    	System.gc();
    }
    
    @Override
    public void setContext(Context context){
    	mContext = context;
    }
    
    @Override
    public final View startPreview(Context context){
    	mContext = context == null ? mContext : context;
    	if(mPreview == null && mContext != null){
			
			final Thread previewThread = new Thread() {
				@Override
				public void run() {
					android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY);
					Looper.prepare();
					
					synchronized (this) {
						mPreview = new MyProxyVideoConsumerPreview(mContext, mWidth, mHeight, mFps);
						notify();
					}
				}
			};
			previewThread.setPriority(Thread.MAX_PRIORITY);
			synchronized(previewThread) {
				previewThread.start();
				try {
					previewThread.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
					return null;
				}
	        }
		}
		else{
			Log.e(TAG, "Invalid state");
		}
		return mPreview;
    }
    
    @Override
	public final View startPreview(){
		return startPreview(null);
	}
    
    private int prepareCallback(int width, int height, int fps){
    	Log.d(TAG, "prepareCallback("+width+","+height+","+fps+")");
    	
    	// Update video stream parameters with real values (negotiated)
		mWidth  = width;
		mHeight = height;
		mFps    = fps;
		
		mVideoFrame = ByteBuffer.allocateDirect((mWidth * mHeight) << 1);
		mConsumer.setConsumeBuffer(mVideoFrame, mVideoFrame.capacity());
		
		super.mPrepared = true;
		return 0;
    }
    
    private int startCallback(){
    	Log.d(TAG, "startCallback");
    	super.mStarted = true;
    	return 0;
    }

    private int bufferCopiedCallback(long nCopiedSize, long nAvailableSize) {
    	if(!super.mValid){
			Log.e(TAG, "Invalid state");
			return -1;
		}
		if(mPreview == null || videoSurface == null){
			// Not on the top
			return 0;
		}
	    
		fpsCounter.count();
        
        if(mVideoFrame == null || mVideoFrame.capacity() < nAvailableSize){
            mVideoFrame = ByteBuffer.allocateDirect((int)nAvailableSize);
            mConsumer.setConsumeBuffer(mVideoFrame, mVideoFrame.capacity());
            mRenderedAtLeastOneFrame = true; // after "width=x, height=y" and before "broadcastEvent"
            NgnMediaPluginEventArgs.broadcastEvent(new NgnMediaPluginEventArgs(mConsumer.getId(), NgnMediaType.Video, 
                    NgnMediaPluginEventTypes.VIDEO_INPUT_SIZE_CHANGED));
            return 0; // Draw the picture next time
        }
        
        mRenderedAtLeastOneFrame = true; // after "width=x, height=y"
        drawFrame((int)nAvailableSize);
		return 0;
    }
    
   // @deprecated: never called
    private int consumeCallback(ProxyVideoFrame _frame){
		if (!super.mValid){
			Log.e(TAG, "Invalid state");
			return -1;
		}
		if (mPreview == null || videoSurface == null){
			// Not on the top
			return 0;
		}
		
		// Get video frame content from native code
		_frame.getContent(mVideoFrame, mVideoFrame.capacity());
		drawFrame();
	    
		return 0;
    }

    private int pauseCallback(){
    	Log.d(TAG, "pauseCallback");
    	super.mPaused = true;
    	return 0;
    }
    
    private synchronized int stopCallback(){
    	Log.d(TAG, "stopCallback");
    	super.mStarted = false;
    	if (videoDecoder != null)
    	{
    	    videoDecoder.release();
    	    videoDecoder = null;
    	}
    	mPreview = null;
    	return 0;
    }

    private void decodeData(final byte[] data, final int size)
    {
        mInputData.setData(data, size);

        int result = videoDecoder.process(mInputData, mOutputData);
        if (result != -1)
        {
            mWidth = videoDecoder.getWidth();
            mHeight= videoDecoder.getHeight();
        }
    }
    
    private synchronized void drawFrame(final int dataSize)
    {
        try
        {
            if (dataBuffer == null || dataBuffer.length < dataSize)
            {
                dataBuffer = new byte[dataSize];
            }
            mVideoFrame.get(dataBuffer, 0, dataSize);
            mVideoFrame.rewind();

            if (startDecoderWithKeyFrame) {
                if (isH264KeyFrame(dataBuffer)) {
                    if (videoSurface == null) {
                        dataBufferKeyFrame = new byte[dataSize];
                        System.arraycopy(dataBuffer, 0, dataBufferKeyFrame, 0, dataSize);
                        return;
                    }
                } else {
                    if (videoDecoder == null) {
                        return;
                    }
                }
            }

            if (videoDecoder == null)
            {
                videoDecoder = new AndroidVideoDecoder(videoSurface, mWidth, mHeight);
                mInputData   = new MediaCodecData(mWidth, mHeight);
                mOutputData  = new MediaCodecData(mWidth, mHeight);
                if (dataBufferKeyFrame != null)
                {
                    decodeData(dataBufferKeyFrame, dataBufferKeyFrame.length);
                    dataBufferKeyFrame = null;
                }
            }
            decodeData(dataBuffer, dataSize);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    
    private synchronized void drawFrame()
    {
        drawFrame(mVideoFrame.capacity());
    }
    
	
	/**
	 * MyProxyVideoConsumerCallback
	 */
	static class MyProxyVideoConsumerCallback extends ProxyVideoConsumerCallback
    {
        final NgnProxyVideoConsumerHW myConsumer;

        public MyProxyVideoConsumerCallback(NgnProxyVideoConsumerHW consumer){
        	super();
            myConsumer = consumer;
        }
        
        @Override
        public int prepare(int width, int height, int fps){
            int ret = myConsumer.prepareCallback(width, height, fps);
            NgnMediaPluginEventArgs.broadcastEvent(new NgnMediaPluginEventArgs(myConsumer.mId, NgnMediaType.Video, 
            		ret == 0 ? NgnMediaPluginEventTypes.PREPARED_OK : NgnMediaPluginEventTypes.PREPARED_NOK));
            return ret;
        }
        
        @Override
        public int start(){
            int ret = myConsumer.startCallback();
            NgnMediaPluginEventArgs.broadcastEvent(new NgnMediaPluginEventArgs(myConsumer.mId, NgnMediaType.Video, 
            		ret == 0 ? NgnMediaPluginEventTypes.STARTED_OK : NgnMediaPluginEventTypes.STARTED_NOK));
            return ret;
        }

        @Override
        public int consume(ProxyVideoFrame frame){
            return myConsumer.consumeCallback(frame);
        }        
        
        @Override
		public int bufferCopied(long nCopiedSize, long nAvailableSize) {
			return myConsumer.bufferCopiedCallback(nCopiedSize, nAvailableSize);
		}

		@Override
        public int pause(){
            int ret = myConsumer.pauseCallback();
            NgnMediaPluginEventArgs.broadcastEvent(new NgnMediaPluginEventArgs(myConsumer.mId, NgnMediaType.Video, 
            		ret == 0 ? NgnMediaPluginEventTypes.PAUSED_OK : NgnMediaPluginEventTypes.PAUSED_NOK));
            return ret;
        }
        
        @Override
        public int stop(){
            int ret = myConsumer.stopCallback();
            NgnMediaPluginEventArgs.broadcastEvent(new NgnMediaPluginEventArgs(myConsumer.mId, NgnMediaType.Video, 
            		ret == 0 ? NgnMediaPluginEventTypes.STOPPED_OK : NgnMediaPluginEventTypes.STOPPED_NOK));
            return ret;
        }
    }
	
	/**
	 * MyProxyVideoConsumerPreview
	 */
	static class MyProxyVideoConsumerPreview extends SurfaceView implements SurfaceHolder.Callback {
	    private SurfaceHolder mHolder;

		MyProxyVideoConsumerPreview(Context context, int width, int height, int fps) {
			super(context);
			
			mHolder = getHolder();
			mHolder.addCallback(this);
			// You don't need to enable GPU or Hardware acceleration by yourself
			mHolder.setType(SurfaceHolder.SURFACE_TYPE_HARDWARE);
		}
		
		public void surfaceCreated(SurfaceHolder holder) {
		}
	
		public void surfaceDestroyed(SurfaceHolder holder) {
		}
	
        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            if(holder != null){
                if (videoDecoder != null)
                {
                    videoDecoder.release();
                    videoDecoder = null;
                }
                videoSurface = holder.getSurface();

            }
        }
    }
    
}
