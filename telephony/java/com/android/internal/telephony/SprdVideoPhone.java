package com.android.internal.telephony;

import android.os.Handler;
import android.os.Message;
import android.telephony.PhoneStateListener;

import com.android.internal.telephony.CallStateException;

/**
 * Internal interface used to control the phone; SDK developers cannot
 * obtain this interface.
 *
 * {@hide}
 *
 */
public interface SprdVideoPhone {


    public enum CallType {
        NONE, VOICE, VIDEO;
    };


	public void registerForPreciseVideoCallStateChanged(Handler h, int what, Object obj);

	public void unregisterForPreciseVideoCallStateChanged(Handler h);

	public void registerForNewRingingVideoCall(Handler h, int what, Object obj);

	public void unregisterForNewRingingVideoCall(Handler h);

	public void registerForIncomingRingVideoCall(Handler h, int what, Object obj);

	public void unregisterForIncomingRingVideoCall(Handler h);

	public void registerForVideoCallDisconnect(Handler h, int what, Object obj);

	public void unregisterForVideoCallDisconnect(Handler h);

	public void registerForVideoCallFallBack(Handler h, int what, Object obj);

	public void unregisterForVideoCallFallBack(Handler h);

	public void registerForVideoCallFail(Handler h, int what, Object obj);

	public void unregisterForVideoCallFail(Handler h);

	public void registerForRemoteCamera(Handler h, int what, Object obj);

	public void unregisterForRemoteCamera(Handler h);

	public CallType getCallType() ;

	public Connection  dialVP(String dialString) throws CallStateException;
	
	public void  fallBack() throws CallStateException;
	
	public void  acceptFallBack() throws CallStateException;
	
	public void  controlCamera(boolean bEnable) throws CallStateException;

	public void  controlAudio(boolean bEnable) throws CallStateException;

/*	public void notifyPreciseVideoCallStateChanged();
	public void notifyNewRingingVideoCall(Connection cn);
	public void notifyIncomingRingVideoCall();
	public void notifyVideoCallDisconnect(Connection cn);
	public void notifyVideoCallFallBack();
	public void notifyVideoCallFail();*/

}

