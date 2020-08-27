/* Copyright (C) 2012, Doubango Telecom <http://www.doubango.org>
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

import net.quantum6.kit.SystemKit;
import net.quantum6.mediacodec.MediaCodecKit;

import org.doubango.ngn.NgnApplication;
import org.doubango.ngn.NgnEngine;
import org.doubango.ngn.sip.NgnAVSession;
import org.doubango.ngn.utils.NgnConfigurationEntry;
import org.doubango.tinyWRAP.ProxyPlugin;
import org.doubango.tinyWRAP.ProxyVideoConsumer;
import org.doubango.tinyWRAP.QoS;

import android.content.Context;
import android.view.View;

public abstract class NgnProxyVideoConsumer extends NgnProxyPlugin{
	protected boolean mFullScreenRequired;
	protected static int mWidth;
	protected static int mHeight;
	protected static int mFps;
	protected boolean mRenderedAtLeastOneFrame;
	
	/**
	 * 解码器初始化失败，怎么办？
	 * 现在的SV要求由ＹＵＶ转换为ＲＧＢ，才能绘制。
	 */
	public static boolean useVideoDecoder = true && MediaCodecKit.hasH264Decoder();
	
	public NgnProxyVideoConsumer(BigInteger id, ProxyPlugin plugin) {
		super(id, plugin);
		
		mFullScreenRequired = NgnEngine.getInstance().getConfigurationService().getBoolean(
				NgnConfigurationEntry.GENERAL_FULL_SCREEN_VIDEO, 
				NgnConfigurationEntry.DEFAULT_GENERAL_FULL_SCREEN_VIDEO);
	}
	
	public static NgnProxyVideoConsumer createInstance(BigInteger id, ProxyVideoConsumer consumer){
	    if (useVideoDecoder)
	    {
	        return new NgnProxyVideoConsumerHW(id, consumer);
	    }
		return NgnApplication.isGlEs2Supported() ? new NgnProxyVideoConsumerGL(id, consumer) : new NgnProxyVideoConsumerSV(id, consumer);
	}
	
	public int getVideoWidthNegotiated() {
		return mWidth;
	}
	public int getVideoHeightNegotiated() {
		return mHeight;
	}
	public int getVideoWidthReceived() {
		return mRenderedAtLeastOneFrame ? mWidth : 0; // zero means unknown
	}
	public int getVideoHeightReceived() {
		return mRenderedAtLeastOneFrame ? mHeight : 0; // zero means unknown
	}
	
    @Override
    public String getCurrentInfo()
    {
        final QoS qos = NgnAVSession.getSession(getSipSessionId()).getQoSVideo();
        return "VR="+SystemKit.intToText(mWidth , 4)+"x"+SystemKit.intToText(mHeight, 4)
                +"x"+getRunningInfo((int)qos.getVideoDataReceived(), (int)qos.getVideoDataLost());
	}

    public static boolean isH264KeyFrame(final byte[] data)
    {
        return ((data[4] & 0x1F) == 0x07);
    }
	
	public abstract void setContext(Context context);
	public abstract View startPreview(Context context);
	public abstract View startPreview();
}
