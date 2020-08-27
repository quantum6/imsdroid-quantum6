/* Copyright (C) 2010-2011, Mamadou Diop.
*  Copyright (C) 2011, Doubango Telecom.
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

import net.quantum6.fps.FpsCounter;
import net.quantum6.kit.SystemKit;

import org.doubango.tinyWRAP.ProxyPlugin;

/**
 * MyProxyPlugin
 */
public abstract class NgnProxyPlugin implements Comparable<NgnProxyPlugin>{
	
    private final static int COUNT_TIME = 10;
    public  static final int DEFAULT_VIDEO_WIDTH  = 640;
    public  static final int DEFAULT_VIDEO_HEIGHT = 360;
    public  static final int DEFAULT_VIDEO_FPS    = 15;

	protected boolean mValid;
	protected boolean mStarted;
	protected boolean mPaused;
	protected boolean mPrepared;
	protected final BigInteger mId;
	protected final ProxyPlugin mPlugin;
	protected long mSipSessionId;
	
	protected FpsCounter fpsCounter = new FpsCounter();
	private int[][] running_info    = new int[COUNT_TIME][3];
	private int     last_index      = 0;
	
	public abstract String getCurrentInfo();
	
	public NgnProxyPlugin(BigInteger id, ProxyPlugin plugin){
		mId = id;
		mPlugin = plugin;
		mValid = true;
		mSipSessionId = -1;
	}
	
	public long getSipSessionId(){
		return mSipSessionId;
	}
	
	public void setSipSessionId(long id){
		mSipSessionId = id;
	}
	
	public boolean isValid(){
		return mValid;
	}
	
	public boolean isStarted(){
		return mStarted;
	}
	
	public boolean isPaused(){
		return mPaused;
	}
	
	public boolean isPrepared(){
		return mPrepared;
	}
	
	public void invalidate(){
		mValid = false;
	}

    protected String getRunningInfo(final int left, final int right)
    {
        int[] info = new int[3];
        int fps = fpsCounter.getFpsAndClear();
        
        for (int i=0; i<running_info.length; i++)
        {
            int[] unit = running_info[i];
            if (unit == null)
            {
                break;
            }
            info[0] += unit[0];
            info[1] += unit[1];
            info[2] += unit[2];
        }
        
        if (running_info[last_index] == null)
        {
            running_info[last_index] = new int[3];
        }
        running_info[last_index][0] = fps;
        running_info[last_index][1] = left;
        running_info[last_index][2] = right;
        last_index ++;
        if (last_index >= running_info.length)
        {
            last_index = 0;
        }
        
        return       SystemKit.intToText(fps,      2)+"/"+SystemKit.intToText(info[0], 3)
                +"\n   "
                +" ("+SystemKit.intToText(left,    3)+"/"+SystemKit.intToText(right,   3)+")"
                +" ("+SystemKit.intToText(info[1], 3)+"/"+SystemKit.intToText(info[2], 3)+")";
    }

	@Override
	public int compareTo(NgnProxyPlugin another) {
		return (mId.intValue() - another.mId.intValue());
	}
}
