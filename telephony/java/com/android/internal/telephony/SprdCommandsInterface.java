
package com.android.internal.telephony;

import android.os.Message;
import android.os.Handler;
import android.os.Bundle;
import android.os.Registrant;

/**
 * {@hide}
 */
public interface SprdCommandsInterface {
    public void dialVP(String address, String sub_address, int clirMode, Message result);
    public void hangupVP(Message result, int reason);
	public void acceptVP(Message result);
	public void fallBackVP(Message result);
	public void sendVPString(String str, Message result);
	public void controlVPLocalMedia(int datatype, int sw, boolean bReplaceImg, Message result);
	public void recordVPVideo(boolean bStart, Message result);
	public void recordVPAudio(boolean bStart, int mode, Message result);
	public void testVP(int flag, int value, Message result);
	public void codecVP(int type, Bundle param, Message result);
	public void getCurrentVideoCalls (Message result);
	public void controlVPCamera (boolean bEnable, Message result);
	public void controlVPAudio(boolean bEnable, Message result);
	public void controlIFrame(boolean isIFrame, boolean needIFrame,Message result);
	
	public void setOnVPData(Handler h, int what, Object obj);
	public void unSetOnVPData(Handler h);
	public void setOnVPCodec(Handler h, int what, Object obj);
	public void unSetOnVPCodec(Handler h);
	public void setOnVPString(Handler h, int what, Object obj);
	public void unSetOnVPString(Handler h);
	public void setOnVPRemoteMedia(Handler h, int what, Object obj);
	public void unSetOnVPRemoteMedia(Handler h);
	public void setOnVPMMRing(Handler h, int what, Object obj);
	public void unSetOnVPMMRing(Handler h);
	public void setOnVPRecordVideo(Handler h, int what, Object obj);
	public void unSetOnVPRecordVideo(Handler h);
	public void setOnVPFallBack(Handler h, int what, Object obj);
	public void unSetOnVPFallBack(Handler h);
	public void setOnVPFail(Handler h, int what, Object obj);
	public void unSetOnVPFail(Handler h);
	public void setOnVPRemoteCamera(Handler h, int what, Object obj);
	public void unSetOnVPRemoteCamera(Handler h);
	public void setOnVPMediaStart(Handler h, int what, Object obj);
	public void unSetOnVPMediaStart(Handler h);
    public void registerForIccStatusChanged(Handler h, int what, Object obj);
    public void unregisterForIccStatusChanged(Handler h);
	
	public void registerForVideoCallStateChanged(Handler h, int what, Object obj);
	public void unregisterForVideoCallStateChanged(Handler h);

    public int getPhoneId();
	public void  Mbbms_Gsm_Authenticate(String nonce, Message result);
	public void  Mbbms_USim_Authenticate(String nonce, String autn, Message result);   
	public void  getSimType(Message result);
	public void  getRegistrationState(Message result);
	
	public void setGprsAttach(Message response);
	public void setGprsDetach(Message response);
	
	public void getRemainTimes(int type, Message response);
	public void getSimCapacity(Message result);
    public void setOnStkStin(Handler h, int what, Object obj);
    public void unsetOnStkStin(Handler h);
    
   
    public void mmiEnterSim(String dailString, Message result);
    public void setSIMPower(boolean on, Message result);


}

