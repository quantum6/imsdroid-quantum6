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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.TimerTask;

import net.quantum6.mediacodec.AndroidVideoEncoder;
import net.quantum6.mediacodec.MediaCodecData;
import net.quantum6.mediacodec.MediaCodecKit;

import org.doubango.ngn.NgnApplication;
import org.doubango.ngn.utils.NgnTimer;
import org.doubango.tinyWRAP.ProxyVideoProducer;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.View;

/**
 * MyProxyVideoProducer
 */
public class NgnProxyVideoProducer extends NgnProxyVideoProducerAbstract implements Camera.PreviewCallback {
    
	private static final String TAG = NgnProxyVideoProducer.class.getCanonicalName();
	private static final int CALLABACK_BUFFERS_COUNT = 3;

	private static final boolean sAddCallbackBufferSupported = NgnCameraProducer.isAddCallbackBufferSupported();
	
    private final static int DATA_SEND_TIMES    = 4;
    private final static int DATA_SEND_INTERVAL = 200;

    
    private static boolean isVideoStealth = false;
    
	private byte[] mVideoCallbackData;
	
    // if encoder early, remote has not accpeted yet. sps/key frame can't send to remote.
	private boolean dataSendFlag = true;
	private int     dataSendCount;
	private long    dataSendTime;
	private byte[]  dataSendKeyFrame;
	 
	private boolean useVideoEncoder = true && MediaCodecKit.hasH264Encoder();
	private AndroidVideoEncoder mVideoEncoder;
	private MediaCodecData      mInputData;
	private MediaCodecData      mOutputData;

	public NgnProxyVideoProducer(BigInteger id, ProxyVideoProducer producer){
		super(id, producer);
    }
	
	@Override
	public final View startPreview(Context context){
		mContext = context == null ? mContext : context;
		if(mPreview == null && mContext != null){
		    if (isVideoStealth)
		    {
                mPreview = new MyProxyVideoProducerPreviewStealth(this);
		    }
		    else
		    {
		        mPreview = new MyProxyVideoProducerPreviewCamera(this);
		    }
		}
		if(mPreview != null){
			mPreview.setVisibility(View.VISIBLE);
			mPreview.getHolder().setSizeFromLayout();
			mPreview.bringToFront();
		}
		
		return mPreview;
	}

	public static void setStealth(final boolean v)
	{
	    isVideoStealth = v;
	}
	
	@Override
	public void pushBlankPacket(){
		if(super.mValid && mProducer != null){
			if(mVideoFrame == null){
				mVideoFrame = ByteBuffer.allocateDirect((mWidth * mHeight * 3) >> 1);
			}
			//final ByteBuffer buffer = ByteBuffer.allocateDirect(mVideoFrame.capacity());
			//mProducer.push(buffer, buffer.capacity());
			mProducer.push(mVideoFrame, mVideoFrame.capacity());
		}
	}
	
    @Override
	public void toggleCamera(){
		if(super.mValid && super.mStarted && !super.mPaused && mProducer != null){
			final Camera camera = NgnCameraProducer.toggleCamera();
			try{
				startCameraPreview(camera);
			}
			catch (Exception exception) {
				Log.e(TAG, exception.toString());
			}
		}
	}

	@Override
	public int getNativeCameraHardRotation(boolean preview){
		// only for 2.3 and above
		if(NgnApplication.getSDKVersion() >= 9){			
			try {
				
				int orientation = 0;
				int cameraId = 0;
				int numOfCameras = NgnCameraProducer.getNumberOfCameras();
				if (numOfCameras > 1) {
					if (NgnCameraProducer.isFrontFacingCameraEnabled()) {
						cameraId = numOfCameras-1;
					}
				}
				
				Class<?> clsCameraInfo = null;

				final Class<?>[] classes = android.hardware.Camera.class.getDeclaredClasses();
				for (Class<?> c : classes) {
					if (c.getSimpleName().equals("CameraInfo")) {
						clsCameraInfo = c;
						break;
					}
				}
				
				final Object info = clsCameraInfo.getConstructor((Class[]) null).newInstance((Object[]) null);
				Method getCamInfoMthd = android.hardware.Camera.class.getDeclaredMethod("getCameraInfo", int.class, clsCameraInfo);
				getCamInfoMthd.invoke(null, cameraId, info);
				
				Display display = NgnApplication.getDefaultDisplay();
				if (display != null) {
					orientation = display.getOrientation();
				}
				orientation = (orientation + 45) / 90 * 90;     
				int rotation = 0;

				final Field fieldFacing = clsCameraInfo.getField("facing");
				final Field fieldOrient = clsCameraInfo.getField("orientation");
				final Field fieldFrontFacingConst = clsCameraInfo.getField("CAMERA_FACING_FRONT");
								
				if (fieldFacing.getInt(info) == fieldFrontFacingConst.getInt(info)) {
					rotation = (fieldOrient.getInt(info) - orientation + 360) % 360;     					
				}
				else {
					// back-facing camera         
					rotation = (fieldOrient.getInt(info) + orientation) % 360;
				}
				
				return rotation;
			} 
			catch (Exception e) {
				e.printStackTrace();
				return 0;
			} 
		}
		else {
			int     terminalRotation   = getTerminalRotation();
			boolean isFront            = NgnCameraProducer.isFrontFacingCameraEnabled();
			if (NgnApplication.isSamsung() && !NgnApplication.isSamsungGalaxyMini()){
				if (preview){
					if (isFront){
						if (terminalRotation == 0) return 0;
						else return 90;
					}
					else return 0 ;
				}
				else{
					if (isFront){
						if (terminalRotation == 0) return -270;
						else return 90;
					}
					else{
						if (terminalRotation == 0) return 0;
						else return 0;
					}
				}
			}
			else if (NgnApplication.isToshiba()){
				if (preview){
					if (terminalRotation == 0) return 0;
					else return 270;
				}
				else{
					return 0;
				}
			}
			else{
				return 0;
			}
		}
	}

	@Override
	public int compensCamRotation(boolean preview){

		final int cameraHardRotation = getNativeCameraHardRotation(preview);
		final android.content.res.Configuration conf = NgnApplication.getContext().getResources().getConfiguration();
		if(conf.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE){
			return 0;
		}

		if (NgnApplication.getSDKVersion() >= 9) {
			if (preview) {
				return cameraHardRotation;
			}
			else{
				switch (cameraHardRotation) {
					case 0:
					case 180:
					default:
						return 0;
					case 90:
					case 270:
						return 90;
				}
			}			
		}
		else {
			int     terminalRotation   = getTerminalRotation();
			int rotation = 0;
			rotation = (terminalRotation-cameraHardRotation) % 360;
			return rotation;
		}
	}
	
	@Override
	public void setOnPause(boolean pause){
		if(super.mPaused == pause){
			return;
		}
		try {
			if(super.mStarted){
				final Camera camera = NgnCameraProducer.getCamera();
				if(pause){
					camera.stopPreview();
				}
				else{
					camera.startPreview();
				}
			}
		} catch(Exception e){
			Log.e(TAG, e.toString());
		}
		
		super.mPaused = pause;
	}
	
	@Override
	public void startDataSend()
	{
	    dataSendFlag  = true;
	    //dataSendCount = 0;
	}

	@Override
    protected synchronized int startCallback(){
    	Log.d(TAG, "startCallback");
		mStarted = true;
		
		if (mPreview != null) {
			startCameraPreview(mPreview.getCamera());
    	}
		return 0;
    }

    private Camera.Size getCameraBestPreviewSize(Camera camera){
    	final List<Camera.Size> prevSizes = camera.getParameters().getSupportedPreviewSizes();
    	
    	Camera.Size minSize = null;
    	int minScore = Integer.MAX_VALUE;
    	for(Camera.Size size : prevSizes){
    		final int score = Math.abs(size.width - mWidth) + Math.abs(size.height - mHeight);
    		if(minScore > score){
    			minScore = score;
    			minSize = size;
    		}
    	}
    	return minSize;
    }

    @Override
	public void initEncoder(final int width, final int height)
	{
		stopCameraPreview();

		mFrameWidth  = width;
		mFrameHeight = height;

		if (useVideoEncoder)
		{
			if (mVideoEncoder != null)
			{
				mVideoEncoder.release();
				mVideoEncoder = null;
			}
			mVideoEncoder = new AndroidVideoEncoder(mFrameWidth, mFrameHeight, mFps, 0);
			mInputData    = new MediaCodecData(mFrameWidth, mFrameHeight);
			mOutputData   = new MediaCodecData(mFrameWidth, mFrameHeight);
			NgnProxyPluginMgr.setVideoEncoderPassthrough(true);
		}
		//init consumer and codec A first, then producer and codec B. and consumer use codec B.
		if (NgnProxyVideoConsumer.useVideoDecoder)
		{
			NgnProxyPluginMgr.setVideoDecoderPassthrough(true);
		}
	}

	@Override
    protected synchronized void startCameraPreview(Camera camera){
    	if(!mStarted){
    		Log.w(TAG, "Someone requested to start camera preview but producer not ready ...delaying");
    		return;
    	}
    
		if(camera != null && mProducer != null){
			try{
				Camera.Parameters parameters = camera.getParameters();
				final Camera.Size prevSize = getCameraBestPreviewSize(camera);
				parameters.setPreviewSize(prevSize.width, prevSize.height);
				camera.setParameters(parameters);
				
				if(prevSize != null && super.isValid() && (mWidth != prevSize.width || mHeight != prevSize.height)){
					mFrameWidth  = prevSize.width;
					mFrameHeight = prevSize.height;
				}
				
				// alert the framework that we cannot respect the negotiated size
				mProducer.setActualCameraOutputSize(mFrameWidth, mFrameHeight);
				
				// allocate buffer
				Log.d(TAG, String.format("setPreviewSize [%d x %d ]", mFrameWidth, mFrameHeight));
				mVideoFrame = ByteBuffer.allocateDirect((mFrameWidth * mFrameHeight * 3) >> 1);				
			} catch(Exception e){
				Log.e(TAG, e.toString());
			}
			
            //pushBlankPacket();
			initEncoder(mFrameWidth, mFrameHeight);

			try {
				int terminalRotation = getTerminalRotation();
								
				Camera.Parameters parameters = camera.getParameters();
				
				if (terminalRotation == 0) {
					parameters.set("orientation", "landscape");
				} else {
					parameters.set("orientation", "portrait");
				}

				camera.setParameters(parameters);
			} catch (Exception e) {
				Log.e(TAG, e.toString());
			}
			
			// Camera Orientation
			int rotation = compensCamRotation(false);
			Log.d(TAG, String.format("setDisplayOrientation [%d] ",rotation ));
			NgnCameraProducer.setDisplayOrientation(camera, rotation);
			
			// Callback Buffers
			if(NgnProxyVideoProducer.sAddCallbackBufferSupported){
				for(int i=0; i<NgnProxyVideoProducer.CALLABACK_BUFFERS_COUNT; i++){
					if(i == 0 || (mVideoCallbackData == null)){
						mVideoCallbackData = new byte[mVideoFrame.capacity()];
					}
					NgnCameraProducer.addCallbackBuffer(camera, new byte[mVideoFrame.capacity()]);
				}
			}
			
			try{
    			camera.startPreview();
    		}catch (Exception e) {
				Log.e(TAG, e.toString());
			}
	    }
    }

    @Override
	protected synchronized void stopCameraPreview(){
        
        Camera camera = mPreview.getCamera();
    	if(camera != null){
    		try{
				camera.setPreviewCallback(null);
				camera.stopPreview();
				mPreview.setCamera(null);
    		}catch (Exception e) {
				Log.e(TAG, e.toString());
			}
    	}

		if (null != mVideoEncoder)
		{
			mVideoEncoder.release();
			mVideoEncoder = null;
		}
	}
    
	  @Override
	  public void onPreviewFrame(byte[] _data, Camera _camera) {
		  if (!mStarted){
		      return;
		  }

		  if (NgnProxyVideoProducer.super.mValid && mVideoFrame != null && _data != null){
		      if (fpsController.control())
		      {
	              if (NgnProxyVideoProducer.sAddCallbackBufferSupported)
	              {
	                  NgnCameraProducer.addCallbackBuffer(_camera, _data == null ? mVideoCallbackData : _data);
	              }
		          return;
		      }
		      
			  fpsCounter.count();
			  if (mVideoEncoder != null)
			  {
			      mInputData.setData(_data);
			      int size2 = mVideoEncoder.process(mInputData, mOutputData);
			      if (size2 > 0)
			      {
			          if (dataSendFlag && dataSendCount < DATA_SEND_TIMES)
			          {
                          long currTime = System.currentTimeMillis();
			              if (NgnProxyVideoConsumer.isH264KeyFrame(mOutputData.mDataArray))
			              {
                              Log.e(TAG, "dataSendCount2="+dataSendCount);
    			              dataSendTime = currTime;
    			              dataSendKeyFrame = new byte[size2];
    			              System.arraycopy(mOutputData.mDataArray, 0, dataSendKeyFrame, 0, size2);
			              }
			              else if (dataSendKeyFrame != null
			                      && (currTime-dataSendTime) > DATA_SEND_INTERVAL)
			              {
	                          Log.e(TAG, "dataSendCount3="+dataSendCount);
	                          dataSendTime = currTime;
	                          dataSendCount++;
	                          
	                          mVideoFrame.put(dataSendKeyFrame);
	                          mProducer.push(mVideoFrame, dataSendKeyFrame.length);
	                          mVideoFrame.rewind();
			              }
			          }
			          
			          if (mVideoFrame.capacity() < mOutputData.mDataArray.length)
			          {
			              mVideoFrame = ByteBuffer.allocateDirect(mOutputData.mDataArray.length);
			          }
			          mVideoFrame.put(mOutputData.mDataArray);
			          mProducer.push(mVideoFrame, size2);
			      }
			  }
			  else
			  {
                  mVideoFrame.put(_data);
                  mProducer.push(mVideoFrame, mVideoFrame.capacity());
			  }
			  mVideoFrame.rewind();
		  }
		  
          //Log.e(TAG, "onPreviewFrame() 629="+NgnProxyVideoProducer.sAddCallbackBufferSupported);
		  if (NgnProxyVideoProducer.sAddCallbackBufferSupported){
			  // do not use "_data" which could be null (e.g. on GSII)
			  NgnCameraProducer.addCallbackBuffer(_camera, _data == null ? mVideoCallbackData : _data);
		  }
	}

	private class MyProxyVideoProducerPreviewCamera extends MyProxyVideoProducerPreviewAbstract {
	
		MyProxyVideoProducerPreviewCamera(NgnProxyVideoProducer _producer) {
			super(_producer);
		}
	
		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			Log.d(TAG,"surfaceCreated()");
			try {
				mCamera = NgnCameraProducer.openCamera(myProducer.mFps, 
						myProducer.mWidth, 
						myProducer.mHeight, 
						this,
						NgnProxyVideoProducer.this
						);
                if (mCamera != null){
                    myProducer.startCameraPreview(mCamera);
                }

			} catch (Exception exception) {
				Log.e(TAG, exception.toString());
			}
		}
	
		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			Log.d(TAG,"surfaceDestroyed()");
			try {
		        if (null != mVideoEncoder)
		        {
		            mVideoEncoder.release();
		            mVideoEncoder = null;
		        }
				if (mCamera != null) {
					NgnCameraProducer.releaseCamera(mCamera);
					mCamera = null;
				}
			}
			catch (Exception exception) {
				Log.e(TAG, exception.toString());
			}
		}
	
		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
			Log.d(TAG,"Surface Changed Callback");
			try {
			}
			catch (Exception exception) {
				Log.e(TAG, exception.toString());
			}
		}
	}
    
    private class MyProxyVideoProducerPreviewStealth extends MyProxyVideoProducerPreviewAbstract {
        private final byte[] csd0 = 
            {
                0x0, 0x0, 0x0, 0x1, 0x67, 0x42, 0x0, 0x29, (byte)0x8d, (byte)0x8d, 0x40, 0x28, 0x2, (byte)0xdd, 0x0, (byte)0xf0, (byte)0x88, 0x45, 0x38,
                0x0, 0x0, 0x0, 0x1, 0x68, (byte)0xca, 0x43, (byte)0xc8 
            };

        private final NgnTimer mTimerStealth = new NgnTimer();
        
        MyProxyVideoProducerPreviewStealth(NgnProxyVideoProducerAbstract _producer) {
            super(_producer);
        }
        
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.d(TAG,"surfaceCreated()");
            if (useVideoEncoder)
            {
                NgnProxyPluginMgr.setVideoEncoderPassthrough(true);
            }
            if (NgnProxyVideoConsumer.useVideoDecoder)
            {
                NgnProxyPluginMgr.setVideoDecoderPassthrough(true);
            }
            
            mVideoFrame = ByteBuffer.allocateDirect((mFrameWidth * mFrameHeight * 3) >> 1);             
            mTimerStealth.schedule(mTimerTaskStealth, 0, 1000/DEFAULT_VIDEO_FPS);
        }
    
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.d(TAG,"surfaceDestroyed()");
            mTimerStealth.cancel();
        }
    
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            Log.d(TAG,"Surface Changed Callback");
        }
        
        private final TimerTask mTimerTaskStealth = new TimerTask(){
            @Override
            public void run() {
                if (!mStarted){
                    return;
                }

                fpsCounter.count();
                
                mVideoFrame.rewind();
                mVideoFrame.put(csd0);
                mProducer.push(mVideoFrame, csd0.length);
            }
        };
        
    }

}
