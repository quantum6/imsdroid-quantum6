/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 3.0.12
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package org.doubango.tinyWRAP;

public class QoS {
  private transient long swigCPtr;
  protected transient boolean swigCMemOwn;

  protected QoS(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(QoS obj) {
    return (obj == null) ? 0 : obj.swigCPtr;
  }

  protected void finalize() {
    delete();
  }

  public synchronized void delete() {
    if (swigCPtr != 0) {
      if (swigCMemOwn) {
        swigCMemOwn = false;
        tinyWRAPJNI.delete_QoS(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  public float getQavg() {
    return tinyWRAPJNI.QoS_getQavg(swigCPtr, this);
  }

  public float getQ1() {
    return tinyWRAPJNI.QoS_getQ1(swigCPtr, this);
  }

  public float getQ2() {
    return tinyWRAPJNI.QoS_getQ2(swigCPtr, this);
  }

  public float getQ3() {
    return tinyWRAPJNI.QoS_getQ3(swigCPtr, this);
  }

  public float getQ4() {
    return tinyWRAPJNI.QoS_getQ4(swigCPtr, this);
  }

  public float getQ5() {
    return tinyWRAPJNI.QoS_getQ5(swigCPtr, this);
  }

  public long getVideoInWidth() {
    return tinyWRAPJNI.QoS_getVideoInWidth(swigCPtr, this);
  }

  public long getVideoOutWidth() {
    return tinyWRAPJNI.QoS_getVideoOutWidth(swigCPtr, this);
  }

  public long getVideoInHeight() {
    return tinyWRAPJNI.QoS_getVideoInHeight(swigCPtr, this);
  }

  public long getVideoOutHeight() {
    return tinyWRAPJNI.QoS_getVideoOutHeight(swigCPtr, this);
  }

  public long getBandwidthDownKbps() {
    return tinyWRAPJNI.QoS_getBandwidthDownKbps(swigCPtr, this);
  }

  public long getBandwidthUpKbps() {
    return tinyWRAPJNI.QoS_getBandwidthUpKbps(swigCPtr, this);
  }

  public long getVideoInAvgFps() {
    return tinyWRAPJNI.QoS_getVideoInAvgFps(swigCPtr, this);
  }

  public long getVideoDecAvgTime() {
    return tinyWRAPJNI.QoS_getVideoDecAvgTime(swigCPtr, this);
  }

  public long getVideoEncAvgTime() {
    return tinyWRAPJNI.QoS_getVideoEncAvgTime(swigCPtr, this);
  }

  public long getVideoDataReceived() {
    return tinyWRAPJNI.QoS_getVideoDataReceived(swigCPtr, this);
  }

  public long getVideoDataLost() {
    return tinyWRAPJNI.QoS_getVideoDataLost(swigCPtr, this);
  }

  public long getVideoDataSended() {
    return tinyWRAPJNI.QoS_getVideoDataSended(swigCPtr, this);
  }

  public long getVideoDataResend() {
    return tinyWRAPJNI.QoS_getVideoDataResend(swigCPtr, this);
  }

}
