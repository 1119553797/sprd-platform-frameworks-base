package com.android.internal.telephony;

import android.os.Bundle;
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

    /**
	 * used in intent extra field
	 */
    public static final String PHONE_ID = "phone_id";


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

    public void registerForGprsAttached(Handler h, int what, Object obj);

    public void unregisterForGprsAttached(Handler h);

	public void registerForVideoCallCodec(Handler h, int what, Object obj);

	public void unregisterForVideoCallCodec(Handler h);

	public CallType getCallType() ;

	public Connection  dialVP(String dialString) throws CallStateException;
	
	public void  fallBack() throws CallStateException;
	
	public void  acceptFallBack() throws CallStateException;
	
	public void  controlCamera(boolean bEnable) throws CallStateException;

	public void  controlAudio(boolean bEnable) throws CallStateException;

	public void codecVP(int type, Bundle param);

	/**
     * Return gam Authenticate
     */
	public String Mbbms_Gsm_Authenticate(String nonce);

    /**
     * Return usim Authenticate
     */    
	public String Mbbms_USim_Authenticate(String nonce, String autn);
    
    /**
     * Return sim type
     */
	public String getSimType();
	
	public String[] getRegistrationState();
	
	public boolean isVTCall();
	public void getCallForwardingOption(int commandInterfaceCFReason, int serviceClass, Message onComplete);

	public void setCallForwardingOption(int commandInterfaceCFAction, int commandInterfaceCFReason, int serviceClass,
			String dialingNumber, int timerSeconds, Message onComplete);
/*	public void notifyPreciseVideoCallStateChanged();
	public void notifyNewRingingVideoCall(Connection cn);
	public void notifyIncomingRingVideoCall();
	public void notifyVideoCallDisconnect(Connection cn);
	public void notifyVideoCallFallBack();
	public void notifyVideoCallFail();*/
	

    /**
     * Get voice call forwarding or video call forwarding indicator status
     * @param serviceClass
     * @return true if there is a voice call forwarding or a video call forwarding
     */
    boolean getCallForwardingIndicator(int serviceClass);

	public int getRemainTimes(int type);

    int getPhoneId();

    /**
     * Set the iccCard to on or off
     */
    public void setIccCard(boolean turnOn);

}

