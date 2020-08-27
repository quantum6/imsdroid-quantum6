/* Copyright (C) 2010-2015, Mamadou Diop.
*  Copyright (C) 2011, Doubango Telecom.
*  Copyright (C) 2011, Philippe Verney <verney(dot)philippe(AT)gmail(dot)com>
*  Copyright (C) 2011, Tiscali
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

import net.quantum6.fps.FpsController;
import net.quantum6.kit.SystemKit;


import org.doubango.ngn.NgnApplication;
import org.doubango.ngn.sip.NgnAVSession;
import org.doubango.tinyWRAP.ProxyVideoProducer;
import org.doubango.tinyWRAP.ProxyVideoProducerCallback;
import org.doubango.tinyWRAP.QoS;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;


/**
 * MyProxyVideoProducer
 */
public abstract class NgnProxyVideoProducerAbstract extends NgnProxyPlugin{
	private static final String TAG = NgnProxyVideoProducer.class.getCanonicalName();
    
	protected final ProxyVideoProducer mProducer;
	protected final MyProxyVideoProducerCallback mCallback;
	protected Context mContext;
	protected MyProxyVideoProducerPreviewAbstract mPreview;
	protected int mWidth; // negotiated width
	protected int mHeight; // negotiated height
	protected int mFps;
	protected int mFrameWidth; // camera picture output width
	protected int mFrameHeight; // camera picture output height
	
	protected ByteBuffer mVideoFrame;

	protected FpsController fpsController;

	public NgnProxyVideoProducerAbstract(BigInteger id, ProxyVideoProducer producer){
		super(id, producer);
        mCallback = new MyProxyVideoProducerCallback(this);
        mProducer = producer;
        mProducer.setCallback(mCallback);
        
     	// Initialize video stream parameters with default values
        mFrameWidth  = mWidth  = DEFAULT_VIDEO_WIDTH;
        mFrameHeight = mHeight = DEFAULT_VIDEO_HEIGHT;
		mFps                   = DEFAULT_VIDEO_FPS;
		
		fpsController = new FpsController(DEFAULT_VIDEO_FPS);
    }
	
	@Override
	public void finalize()
	{
	    //
	}
	
	@Override
	public void invalidate() {
		super.invalidate();
		mVideoFrame = null;
		System.gc();
	}
	

    @Override
    public String getCurrentInfo()
    {
        final QoS qos = NgnAVSession.getSession(getSipSessionId()).getQoSVideo();
        return "VL="+SystemKit.intToText(mFrameWidth, 4)+"x"+SystemKit.intToText(mFrameHeight, 4)
                +"x"+getRunningInfo((int)qos.getVideoDataSended(), (int)qos.getVideoDataResend());
    }
    
	public void setContext(Context context){
    	mContext = context;
    }
    
    public final int[] getResolution()
    {
        return new int[]{mFrameWidth, mFrameHeight};
    }

	public    abstract void setOnPause(boolean pause);
	public    abstract void startDataSend();

    public    abstract View startPreview(Context context);
    public    abstract void pushBlankPacket();
    public    abstract void toggleCamera();
    protected abstract void startCameraPreview(Camera camera);
    protected abstract int  startCallback();
    public    abstract int  getNativeCameraHardRotation(boolean preview);
    public    abstract int  compensCamRotation(boolean preview);
    protected abstract void stopCameraPreview(Camera camera);
    

    protected int getTerminalRotation(){
        final android.content.res.Configuration conf = NgnApplication.getContext().getResources().getConfiguration();
        int     terminalRotation  = 0 ;
        switch(conf.orientation){
            case android.content.res.Configuration.ORIENTATION_LANDSCAPE:
                terminalRotation = 0;//The starting position is 0 (landscape).
                break;
            case android.content.res.Configuration.ORIENTATION_PORTRAIT:
                terminalRotation = 90;
                break;
        }
        return terminalRotation;
    }
    
	public boolean isFrontFacingCameraEnabled() {
		return NgnCameraProducer.isFrontFacingCameraEnabled();
	}

	public boolean setRotation(int rot){
		if(mProducer != null && super.mValid){
			return mProducer.setRotation(rot);
		}
		return false;
	}
	
	public boolean setMirror(boolean mirror){
		if(mProducer != null && super.mValid){
			return mProducer.setMirror(mirror);
		}
		return false;
	}
	
	private synchronized int prepareCallback(int width, int height, int fps){
		Log.d(NgnProxyVideoProducerAbstract.TAG, "prepareCallback("+width+","+height+","+fps+")");
		
		mFrameWidth  = mWidth  = width;
		mFrameHeight = mHeight = height;
		mFps = fps;
		
		super.mPrepared = true;
		
		return 0;
    }

    private synchronized int pauseCallback(){
    	Log.d(TAG, "pauseCallback");
    	setOnPause(true);
    	return 0;
    }

    private synchronized int stopCallback(){
    	Log.d(TAG, "stopCallback");
    	
    	if (mPreview != null) {
    		stopCameraPreview(mPreview.getCamera());
    	}
    	
		mStarted = false;
		
		return 0;
    }

    /***
     * MyProxyVideoProducerPreview
     */
	public abstract class MyProxyVideoProducerPreviewAbstract extends SurfaceView implements SurfaceHolder.Callback {
		protected SurfaceHolder mHolder;
		protected final NgnProxyVideoProducerAbstract myProducer;
		protected Camera mCamera;
	
		MyProxyVideoProducerPreviewAbstract(NgnProxyVideoProducerAbstract _producer) {
			super(_producer.mContext);
			
			myProducer = _producer;
			mHolder    = getHolder();
			mHolder.addCallback(this);
			mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}
		
		public Camera getCamera(){
			return mCamera;
		}
		
		// Change the camera withour releasing
		public void setCamera(final Camera camera) {
			mCamera = camera;
		}

	}
    
	/**
	 * MyProxyVideoProducerCallback
	 */
	static class MyProxyVideoProducerCallback extends ProxyVideoProducerCallback
    {
        final NgnProxyVideoProducerAbstract myProducer;
        public MyProxyVideoProducerCallback(NgnProxyVideoProducerAbstract producer){
        	super();
            myProducer = producer;
        }

        @Override
        public int prepare(int width, int height, int fps){
            return myProducer.prepareCallback(width, height, fps);
        }

        @Override
        public int start(){
            return myProducer.startCallback();
        }

        @Override
        public int pause(){
            return myProducer.pauseCallback();
        }

        @Override
        public int stop(){
            return myProducer.stopCallback();
        }
    }
}
