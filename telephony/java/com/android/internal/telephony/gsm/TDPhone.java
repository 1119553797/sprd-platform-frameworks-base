/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony.gsm;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.telephony.CellLocation;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.text.TextUtils;
import android.util.Log;

import static com.android.internal.telephony.CommandsInterface.CF_ACTION_DISABLE;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_ENABLE;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_ERASURE;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_REGISTRATION;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_ALL;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_ALL_CONDITIONAL;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_NO_REPLY;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_NOT_REACHABLE;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_BUSY;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_UNCONDITIONAL;
import static com.android.internal.telephony.CommandsInterface.SERVICE_CLASS_VOICE;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_BASEBAND_VERSION;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DataConnection;
import com.android.internal.telephony.DataConnectionTracker;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccConstants;
import com.android.internal.telephony.IccFileHandler;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.IccSmsInterfaceManager;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.SmsRawData;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.gsm.stk.StkService;
import com.android.internal.telephony.test.SimulatedRadioControl;
import com.android.internal.telephony.IccVmNotSupportedException;

import com.android.internal.telephony.gsm.GsmCall;
import android.view.Surface;
import android.view.SurfaceHolder;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;


/**
 * {@hide}
 */
public final class TDPhone extends GSMPhone {
    // NOTE that LOG_TAG here is "TD", which means that log messages
    // from this file will go into the radio log rather than the main
    // log.  (Use "adb logcat -b radio" to see them.)
    static final String LOG_TAG = "TD";
    private static final boolean LOCAL_DEBUG = true;
	
    private final Object mLock = new Object();
    
    protected final RegistrantList mPreciseVideoCallStateRegistrants
            = new RegistrantList();

    protected final RegistrantList mNewRingingVideoCallRegistrants
            = new RegistrantList();

    protected final RegistrantList mIncomingRingVideoCallRegistrants
            = new RegistrantList();

    protected final RegistrantList mVideoCallDisconnectRegistrants
            = new RegistrantList();

    protected final RegistrantList mVideoCallFallBackRegistrants
            = new RegistrantList();

    protected final RegistrantList mVideoCallFailRegistrants
            = new RegistrantList();

    protected final RegistrantList mVideoCallRemoteCameraRegistrants
            = new RegistrantList();

    protected final RegistrantList mVideoCallCodecRegistrants
            = new RegistrantList();

    // Instance Variables
	VideoCallTracker mVideoCT;
	CallType callType;
	
    protected static final int EVENT_CONTROL_CAMERA_DONE       = 100;
    protected static final int EVENT_CONTROL_AUDIO_DONE       = 101;
    protected static final int EVENT_GSM_AUTHEN_DONE       = 102;
    protected static final int EVENT_USIM_AUTHEN_DONE       = 103;
    protected static final int EVENT_GET_SIM_TYPE_DONE       = 104;
    protected static final int EVENT_GET_REGISTRATION_STATE_DONE       = 105;
    protected static final int EVENT_GET_REMIAN_TIMES_DONE       = 106;
    public static final int SERVICE_CLASS_VIDEO = 16;

    // Constructors

    public
    TDPhone (Context context, CommandsInterface ci, PhoneNotifier notifier) {
        this(context,ci,notifier, false);
    }

    public
    TDPhone (Context context, CommandsInterface ci, PhoneNotifier notifier, boolean unitTestMode) {
        super(context, ci, notifier, unitTestMode);
		
	final Object syncObj = new Object();
	final TDPhone phone = this;
	mVideoCT = new VideoCallTracker(phone);

        if (ci instanceof SimulatedRadioControl) {
            mSimulatedRadioControl = (SimulatedRadioControl) ci;
        }

        mDataConnection = MsmsGsmDataConnectionTrackerProxy.getTrackerInstance(this);
        //mCM.setPhoneType(Phone.PHONE_TYPE_TD);

        //Change the system property
        /*SystemProperties.set(TelephonyProperties.CURRENT_ACTIVE_PHONE,
                new Integer(Phone.PHONE_TYPE_TD).toString());*/

        HandlerThread thread = new HandlerThread("TDSyncSender");
        thread.start();

        mHandler = new SyncHandler(thread.getLooper());
    }

    public void dispose() {
        synchronized(PhoneProxy.lockForRadioTechnologyChange) {
            super.dispose();
        }
    }

    protected void finalize() {
        if(LOCAL_DEBUG) Log.d(LOG_TAG, "TDPhone finalized");
    }
	
    public Phone.State getState() {
		if (LOCAL_DEBUG) Log.d(LOG_TAG, "VideoCT: "+ mVideoCT.state + " CT: " + mCT.state);
		
		if (mVideoCT.isAlive())
			return mVideoCT.state;
		else
			return mCT.state;
    }

    public String getPhoneName() {
        return "TD";
    }

    /*public int getPhoneType() {
        return Phone.PHONE_TYPE_TD;
    }*/

    public DataState getDataConnectionState() {
        DataState ret = DataState.DISCONNECTED;

        if (mSST == null) {
            // Radio Technology Change is ongoning, dispose() and removeReferences() have
            // already been called

            ret = DataState.DISCONNECTED;
        } else if (mSST.getCurrentGprsState()
                != ServiceState.STATE_IN_SERVICE) {
            // If we're out of service, open TCP sockets may still work
            // but no data will flow
            ret = DataState.DISCONNECTED;
        } else { /* mSST.gprsState == ServiceState.STATE_IN_SERVICE */
            switch (mDataConnection.getState()) {
                case FAILED:
                case IDLE:
                    ret = DataState.DISCONNECTED;
                break;

                case CONNECTED:
                case DISCONNECTING:
                    if ( ((mCT.state != Phone.State.IDLE) 
							|| (mVideoCT.state != Phone.State.IDLE))
                            && !mSST.isConcurrentVoiceAndData()) {
                        ret = DataState.SUSPENDED;
                    } else if (mCT.state == Phone.State.IDLE && mVideoCT.state == Phone.State.IDLE
                                  && MsmsGsmDataConnectionTrackerProxy.isAnotherCardVoiceing(getPhoneId())
                                  && !MsmsGsmDataConnectionTrackerProxy.isSupportMultiModem()) {
                        ret = DataState.SUSPENDED;
                    } else {
                        ret = DataState.CONNECTED;
                    }
                break;

                case INITING:
                case CONNECTING:
                case SCANNING:
                    ret = DataState.CONNECTING;
                break;
            }
        }

        return ret;
    }

    public void
    notifyCallForwardingIndicator(int serviceClass) {
        mNotifier.notifyCallForwardingChanged(this, serviceClass);
    }

    public void
    acceptCall() throws CallStateException {
    	if (mVideoCT.isAlive())
		{
			mVideoCT.acceptCall();
		}
		else
		{
	        mCT.acceptCall();
		}
    }

    public void
    rejectCall() throws CallStateException {
    	if (mVideoCT.isAlive())
		{
			mVideoCT.rejectCall();
		}
		else
		{
	        mCT.rejectCall();
		}
    }

    public void
    switchHoldingAndActive() throws CallStateException {
    	if (!(mVideoCT.isAlive()))
	        mCT.switchWaitingOrHoldingAndActive();
    }

    public boolean canConference() {
		if (!(mVideoCT.isAlive()))
	        return mCT.canConference();

		return false;
    }

    public boolean canDial() {
        return (mCT.canDial() || mVideoCT.canDial());
    }

    public void conference() throws CallStateException {
		if (!(mVideoCT.isAlive()))
			mCT.conference();
    }

    public void clearDisconnected() {
        mCT.clearDisconnected();
	 mVideoCT.clearDisconnected();
    }

    public boolean canTransfer() {
        return mCT.canTransfer();
    }

    public void explicitCallTransfer() throws CallStateException {
        mCT.explicitCallTransfer();
    }

    public Call
    getForegroundCall() {
    	//if (mVideoCT.isAlive())
    	if (mVideoCT.foregroundCall.getState() != Call.State.IDLE)
			return mVideoCT.foregroundCall;
		else
	        return mCT.foregroundCall;
    }
	
    public ArrayList<Call>
    getForegroundCalls() {
    	ArrayList<Call> foregroundCalls = new ArrayList<Call>();
    	foregroundCalls.add(mCT.foregroundCall);
    	foregroundCalls.add(mVideoCT.foregroundCall);
	return foregroundCalls;
    }

    public Call
    getBackgroundCall() {
    	if (mVideoCT.isAlive()){
		Log.e(LOG_TAG, "getBackgroundCall(), mVideoCT");
		return mVideoCT.backgroundCall;
	} else{
		Log.e(LOG_TAG, "getBackgroundCall(), mCT");
	        return mCT.backgroundCall;
	}
    }
	
    public ArrayList<Call>
    getBackgroundCalls() {
    	ArrayList<Call> backgroundCalls = new ArrayList<Call>();
    	backgroundCalls.add(mCT.backgroundCall);
    	backgroundCalls.add(mVideoCT.backgroundCall);
	return backgroundCalls;
    }

    public Call
    getRingingCall() {
    	if (mVideoCT.isAlive())
			return mVideoCT.ringingCall;
		else
	        return mCT.ringingCall;
    }
	
    public ArrayList<Call>
    getRingingCalls() {
    	ArrayList<Call> ringingCalls = new ArrayList<Call>();
    	ringingCalls.add(mCT.ringingCall);
    	ringingCalls.add(mVideoCT.ringingCall);
	return ringingCalls;
    }
	
    boolean isInCall() {
        Call.State foregroundCallState = getForegroundCall().getState();
        Call.State backgroundCallState = getBackgroundCall().getState();
        Call.State ringingCallState = getRingingCall().getState();

       return (foregroundCallState.isAlive() ||
                backgroundCallState.isAlive() ||
                ringingCallState.isAlive());
    }

    public void
    sendDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            Log.e(LOG_TAG,
                    "sendDtmf called with invalid character '" + c + "'");
        } else {
            if (mCT.state ==  Phone.State.OFFHOOK) {
                mCM.sendDtmf(c, null);
            }
        }
    }

    public void
    startDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            Log.e(LOG_TAG,
                "startDtmf called with invalid character '" + c + "'");
        } else {
            mCM.startDtmf(c, null);
        }
    }

    public void
    stopDtmf() {
        mCM.stopDtmf(null);
    }

    public void
    sendBurstDtmf(String dtmfString) {
        Log.e(LOG_TAG, "[TDPhone] sendBurstDtmf() is a CDMA method");
    }

    private boolean isValidCommandInterfaceCFReason (int commandInterfaceCFReason) {
        switch (commandInterfaceCFReason) {
        case CF_REASON_UNCONDITIONAL:
        case CF_REASON_BUSY:
        case CF_REASON_NO_REPLY:
        case CF_REASON_NOT_REACHABLE:
        case CF_REASON_ALL:
        case CF_REASON_ALL_CONDITIONAL:
            return true;
        default:
            return false;
        }
    }

    private boolean isValidCommandInterfaceCFAction (int commandInterfaceCFAction) {
        switch (commandInterfaceCFAction) {
        case CF_ACTION_DISABLE:
        case CF_ACTION_ENABLE:
        case CF_ACTION_REGISTRATION:
        case CF_ACTION_ERASURE:
            return true;
        default:
            return false;
        }
    }

    protected  boolean isCfEnable(int action) {
        return (action == CF_ACTION_ENABLE) || (action == CF_ACTION_REGISTRATION);
    }

    public void getCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
        if (isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            if (LOCAL_DEBUG) Log.d(LOG_TAG, "requesting call forwarding query.");
            Message resp;
            if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
                resp = obtainMessage(EVENT_GET_CALL_FORWARD_DONE, onComplete);
            } else {
                resp = onComplete;
            }
            mCM.queryCallForwardStatus(commandInterfaceCFReason,0,null,resp);
        }
    }

    public void setCallForwardingOption(int commandInterfaceCFAction,
            int commandInterfaceCFReason,
            String dialingNumber,
            int timerSeconds,
            Message onComplete) {
        if (    (isValidCommandInterfaceCFAction(commandInterfaceCFAction)) &&
                (isValidCommandInterfaceCFReason(commandInterfaceCFReason))) {

            Message resp;
            if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
                resp = obtainMessage(EVENT_SET_CALL_FORWARD_DONE,
                        isCfEnable(commandInterfaceCFAction) ? 1 : 0, CommandsInterface.SERVICE_CLASS_VOICE, onComplete);
            } else {
                resp = onComplete;
            }
            mCM.setCallForward(commandInterfaceCFAction,
                    commandInterfaceCFReason,
                    CommandsInterface.SERVICE_CLASS_VOICE,
                    dialingNumber,
                    timerSeconds,
                    resp);
        }
    }

    public void getOutgoingCallerIdDisplay(Message onComplete) {
        mCM.getCLIR(onComplete);
    }

    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode,
                                           Message onComplete) {
        mCM.setCLIR(commandInterfaceCLIRMode,
                obtainMessage(EVENT_SET_CLIR_COMPLETE, commandInterfaceCLIRMode, 0, onComplete));
    }

    public void getCallWaiting(Message onComplete) {
        mCM.queryCallWaiting(CommandsInterface.SERVICE_CLASS_VOICE, onComplete);
    }

    public void setCallWaiting(boolean enable, Message onComplete) {
        mCM.setCallWaiting(enable, CommandsInterface.SERVICE_CLASS_VOICE, onComplete);
    }

    public void setOnPostDialCharacter(Handler h, int what, Object obj) {
        mPostDialHandler = new Registrant(h, what, obj);
    }

    public void setMute(boolean muted) {
        mCT.setMute(muted);
    }

    public boolean getMute() {
        return mCT.getMute();
    }


    @Override
    public void handleMessage (Message msg) {
        AsyncResult ar;
        Message onComplete;

        switch (msg.what) {
			case EVENT_CALL_RING:
				if (mVideoCT.isAlive())
				{
					Log.d(LOG_TAG, "Event EVENT_CALL_RING Received state=" + getState());
					ar = (AsyncResult)msg.obj;
					if (ar.exception == null) {
						Phone.State state = getState();
						if ((!mDoesRilSendMultipleCallRing)
								&& ((state == Phone.State.RINGING) || (state == Phone.State.IDLE))) {
							mCallRingContinueToken += 1;
							sendIncomingVideoCallRingNotification(mCallRingContinueToken);
						} else {
							notifyIncomingRingVideoCall();
						}
					}
				}
				else
				{
					super.handleMessage(msg);
				}
				break;
			case EVENT_CALL_RING_CONTINUE:
				if (mVideoCT.isAlive())
				{
	                Log.d(LOG_TAG, "Event EVENT_CALL_RING_CONTINUE Received stat=" + getState());
	                if (getState() == Phone.State.RINGING) {
	                    sendIncomingVideoCallRingNotification(msg.arg1);
	                }
				}
				else
				{
					super.handleMessage(msg);
				}
                break;

			case EVENT_CONTROL_CAMERA_DONE:
			case EVENT_CONTROL_AUDIO_DONE:
				break;

            case EVENT_SET_CALL_FORWARD_DONE:
                ar = (AsyncResult)msg.obj;
                if (ar.exception == null) {
                    if ((msg.arg2 & SERVICE_CLASS_VOICE) != 0) {
                        mSIMRecords.setVoiceCallForwardingFlag(1, msg.arg1 == 1);
                    } else if (SERVICE_CLASS_VIDEO == msg.arg2) {
                        mSIMRecords.setVideoCallForwardingFlag(1, msg.arg1 == 1);
                    }
                }
                onComplete = (Message) ar.userObj;
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                    onComplete.sendToTarget();
                }
                break;

            case EVENT_GET_CALL_FORWARD_DONE:
                ar = (AsyncResult)msg.obj;
                if (ar.exception == null) {
                    handleCfuQueryResult((CallForwardInfo[])ar.result);
                }
                onComplete = (Message) ar.userObj;
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                    onComplete.sendToTarget();
                }
                break;

			default:
				super.handleMessage(msg);

        }
    }

	/******** 	New Added for VideoCall 	********/

	public void registerForPreciseVideoCallStateChanged(Handler h, int what, Object obj){
        checkCorrectThread(h);

        mPreciseVideoCallStateRegistrants.addUnique(h, what, obj);
    }

	public void unregisterForPreciseVideoCallStateChanged(Handler h){
        mPreciseVideoCallStateRegistrants.remove(h);
    }

	public void registerForNewRingingVideoCall(Handler h, int what, Object obj){
        checkCorrectThread(h);

        mNewRingingVideoCallRegistrants.addUnique(h, what, obj);
    }

	public void unregisterForNewRingingVideoCall(Handler h){
        mNewRingingVideoCallRegistrants.remove(h);
    }

	public void registerForIncomingRingVideoCall(Handler h, int what, Object obj){
        checkCorrectThread(h);

        mIncomingRingVideoCallRegistrants.addUnique(h, what, obj);
    }

	public void unregisterForIncomingRingVideoCall(Handler h){
        mIncomingRingVideoCallRegistrants.remove(h);
    }

	public void registerForVideoCallDisconnect(Handler h, int what, Object obj){
        checkCorrectThread(h);

        mVideoCallDisconnectRegistrants.addUnique(h, what, obj);
    }

	public void unregisterForVideoCallDisconnect(Handler h){
        mVideoCallDisconnectRegistrants.remove(h);
    }

	public void registerForVideoCallFallBack(Handler h, int what, Object obj){
        checkCorrectThread(h);

        mVideoCallFallBackRegistrants.addUnique(h, what, obj);
    }

	public void unregisterForVideoCallFallBack(Handler h){
        mVideoCallFallBackRegistrants.remove(h);
    }
	
	public void registerForVideoCallFail(Handler h, int what, Object obj){
        checkCorrectThread(h);

        mVideoCallFailRegistrants.addUnique(h, what, obj);
    }

	public void unregisterForVideoCallFail(Handler h){
        mVideoCallFailRegistrants.remove(h);
    }
	
	public void registerForRemoteCamera(Handler h, int what, Object obj){
        checkCorrectThread(h);

        mVideoCallRemoteCameraRegistrants.addUnique(h, what, obj);
    }

	public void unregisterForRemoteCamera(Handler h){
        mVideoCallRemoteCameraRegistrants.remove(h);
    }
	
	public void registerForVideoCallCodec(Handler h, int what, Object obj){
        checkCorrectThread(h);

        mVideoCallCodecRegistrants.addUnique(h, what, obj);
    }

	public void unregisterForVideoCallCodec(Handler h){
        mVideoCallCodecRegistrants.remove(h);
    }

     void notifyPreciseVideoCallStateChanged() {
	Log.d(LOG_TAG, " notifyPreciseVideoCallStateChanged");
        AsyncResult ar = new AsyncResult(null, this, null);
        mPreciseVideoCallStateRegistrants.notifyRegistrants(ar);
    }
	
     void notifyNewRingingVideoCall(Connection cn) {
	Log.d(LOG_TAG, " notifyNewRingingVideoCall");
        AsyncResult ar = new AsyncResult(null, cn, null);
        mNewRingingVideoCallRegistrants.notifyRegistrants(ar);
    }

    void notifyIncomingRingVideoCall() {
	Log.d(LOG_TAG, " notifyVideoCallCodec");
        AsyncResult ar = new AsyncResult(null, this, null);
        mIncomingRingVideoCallRegistrants.notifyRegistrants(ar);
    }
	
	void notifyVideoCallDisconnect(Connection cn) {
	Log.d(LOG_TAG, " notifyVideoCallDisconnect");
        AsyncResult ar = new AsyncResult(null, cn, null);
        mVideoCallDisconnectRegistrants.notifyRegistrants(ar);
    }
	
	void notifyVideoCallFallBack(AsyncResult ar){
	Log.d(LOG_TAG, " notifyVideoCallFallBack");
        mVideoCallFallBackRegistrants.notifyRegistrants(ar);
    }
	
	void notifyVideoCallFail(AsyncResult ar){
	Log.d(LOG_TAG, " notifyVideoCallFail");
        mVideoCallFailRegistrants.notifyRegistrants(ar);
    }
	
	void notifyVideoCallCodec(AsyncResult ar){
	Log.d(LOG_TAG, " notifyVideoCallCodec");
        mVideoCallCodecRegistrants.notifyRegistrants(ar);
    }

	public CallType getCallType() {
		if (mVideoCT.isAlive())
			return CallType.VIDEO;
		
        Call.State foregroundCallState = getForegroundCall().getState();
        Call.State backgroundCallState = getBackgroundCall().getState();
        Call.State ringingCallState = getRingingCall().getState();
		Log.d(LOG_TAG, "getCallType: " + foregroundCallState + ", " + backgroundCallState + ", " + ringingCallState);
/*		if (foregroundCallState.isAlive()
			|| backgroundCallState.isAlive()
			|| ringingCallState.isAlive())*/
			return CallType.VOICE;

//		return CallType.NONE;
	}

	public Connection
    dialVP (String dialString) throws CallStateException {
        // Need to make sure dialString gets parsed properly
        String newDialString = PhoneNumberUtils.stripSeparators(dialString);
		Log.d(LOG_TAG,"dialVP '" + dialString);

        // handle in-call MMI first if applicable
        if (handleInCallMmiCommands(newDialString)) {
            return null;
        }

        // Only look at the Network portion for mmi
        String networkPortion = PhoneNumberUtils.extractNetworkPortionAlt(newDialString);
        GsmMmiCode mmi = GsmMmiCode.newFromDialString(networkPortion, this);
        if (LOCAL_DEBUG) Log.d(LOG_TAG,
                               "dialing w/ mmi '" + mmi + "'...");

        if (mmi == null) {
			callType = CallType.VIDEO;
            return mVideoCT.dial(newDialString);
        } else if (mmi.isTemporaryModeCLIR()) {
            return mCT.dial(mmi.dialingNumber, mmi.getCLIRMode());
        } else {
            mPendingMMIs.add(mmi);
            mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
            mmi.processCode();

            // FIXME should this return null or something else?
            return null;
        }
    }

	public void  fallBack() throws CallStateException{
		mVideoCT.fallBack();
	}
	
	public void  acceptFallBack() throws CallStateException{
		mVideoCT.acceptFallBack();
	}

	public void  controlCamera(boolean bEnable) throws CallStateException{
		mCM.controlVPCamera(bEnable, obtainMessage(EVENT_CONTROL_CAMERA_DONE, bEnable?1:0, 0, 0));
	}

	public void  controlAudio(boolean bEnable) throws CallStateException{
		mCM.controlVPAudio(bEnable, obtainMessage(EVENT_CONTROL_AUDIO_DONE, bEnable?1:0, 0, 0));
	}
	
	public void  codecVP(int type, Bundle param) {
		mCM.codecVP(type, param, null);
	}


/*	public VideoCallTracker.VideoCallState getVideoCallState(){
		return mVideoCT.callState;
	}*/

  /**
     * Send the incoming videocall Ring notification if conditions are right.
     */
    private void sendIncomingVideoCallRingNotification(int token) {
        if (!mDoesRilSendMultipleCallRing && (token == mCallRingContinueToken)) {
            Log.d(LOG_TAG, "Sending notifyIncomingRing");
            notifyIncomingRingVideoCall();
            sendMessageDelayed(
                    obtainMessage(EVENT_CALL_RING_CONTINUE, token, 0), mCallRingDelay);
        } else {
            Log.d(LOG_TAG, "Ignoring ring notification request,"
                    + " mDoesRilSendMultipleCallRing=" + mDoesRilSendMultipleCallRing
                    + " token=" + token
                    + " mCallRingContinueToken=" + mCallRingContinueToken);
        }
    }

    private String mGsmAuthen = null;
    private String mUsimAuthen = null ;
    private String mSimType = null;
    private String[] mRegistrationState = null;
    private int mRemainTimes = -1;
    SyncHandler mHandler;
    class SyncHandler extends Handler 
    {
        
        SyncHandler (Looper looper) {
            
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            Log.d(LOG_TAG, "handleMessage msg.what:"+msg.what);
            switch (msg.what) {
                case EVENT_GSM_AUTHEN_DONE:
                    ar = (AsyncResult) msg.obj;
                    synchronized (mLock) {
                    	if (ar.exception == null) {
                    		mGsmAuthen = (String ) ar.result;	
                    	}
                    	else {
                    		Log.d(LOG_TAG,"handleMessage GSM error!");
                    		mGsmAuthen = null;
                    	}                    		
                        mLock.notifyAll();
                    }
                    break;
                case EVENT_USIM_AUTHEN_DONE:
                    ar = (AsyncResult)msg.obj;
                    synchronized (mLock) {
                    	if (ar.exception == null) {
                    		mUsimAuthen = (String ) ar.result;	
                    	}
                    	else {
                    		Log.d(LOG_TAG,"handleMessage USIM error!");
                    		mUsimAuthen = null;
                    	}                    
                        mLock.notifyAll();
                    }
                    break;
                case EVENT_GET_SIM_TYPE_DONE:
                	ar = (AsyncResult) msg.obj;
                    synchronized (mLock) {
                    	if (ar.exception == null) {
                    		mSimType = (String ) ar.result;	
                    	}
                    	else {
                    		Log.d(LOG_TAG,"handleMessage SIM type error!");
                    		mSimType = null;
                    	}                    
                        mLock.notifyAll();
                    }
                    break;
                case EVENT_GET_REGISTRATION_STATE_DONE:
                	ar = (AsyncResult) msg.obj;
                    synchronized (mLock) {
                    	if (ar.exception == null) {
                    		mRegistrationState = (String []) ar.result;	
                    	}
                    	else {
                    		Log.d(LOG_TAG,"handleMessage registration state error!");
                    		mRegistrationState = null;
                    	}                    
                        mLock.notifyAll();
                    }
                    break;
                case EVENT_GET_REMIAN_TIMES_DONE:
                  	ar = (AsyncResult) msg.obj;
                        Log.d(LOG_TAG, "handleMessage EVENT_GET_REMIAN_TIMES_DONE");
                    synchronized (mLock) {
                    	if (ar.exception == null) {
                    		mRemainTimes = (((int []) ar.result))[0];	
                    	}
                    	else {
                    		Log.d(LOG_TAG,"handleMessage registration state error!");
                    		mRemainTimes = -1;
                    	}                    
                        mLock.notifyAll();
                    }
                    break;
                	
            }
        }
    }
    
    /**
     * Return gam Authenticate
     */
	public String Mbbms_Gsm_Authenticate(String nonce) {
		Log.d(LOG_TAG, "Mbbms_Gsm_Authenticate nonce:"+nonce);
		
		synchronized(mLock) {
            Message response = mHandler.obtainMessage(EVENT_GSM_AUTHEN_DONE);
            mCM.Mbbms_Gsm_Authenticate(nonce, response);
            try {
                mLock.wait();
            } catch (InterruptedException e) {
            	Log.d(LOG_TAG,"interrupted while trying to authenticate the SIM");
            }
        }
		Log.d(LOG_TAG, "Mbbms_Gsm_Authenticate mGsmAuthen:"+mGsmAuthen);
		return mGsmAuthen;
	}

    /**
     * Return usim Authenticate
     */    
	public String  Mbbms_USim_Authenticate(String nonce, String autn) {
		Log.d(LOG_TAG, "Mbbms_USim_Authenticate nonce:"+nonce + " autn:"+autn);

		synchronized(mLock) {
            Message response = mHandler.obtainMessage(EVENT_USIM_AUTHEN_DONE);
            mCM.Mbbms_USim_Authenticate(nonce, autn, response);
            try {
                mLock.wait();
            } catch (InterruptedException e) {
            	Log.d(LOG_TAG,"interrupted while trying to authenticate the USIM");
            }
        }

		Log.d(LOG_TAG, "Mbbms_USim_Authenticate mUsimAuthen:"+mUsimAuthen);
		return mUsimAuthen;
	}
    
    /**
     * Return sim type
     */
	public String getSimType() {	
		Log.d(LOG_TAG, "getSimType");
		
		synchronized(mLock) {
            Message response = mHandler.obtainMessage(EVENT_GET_SIM_TYPE_DONE);
            mCM.getSimType(response);
            try {
                mLock.wait();
            } catch (InterruptedException e) {
            	Log.d(LOG_TAG,"interrupted while trying to get sim type");
            }
        }
		
		Log.d(LOG_TAG, "getSimType:"+mSimType);
		return mSimType;
	}
	
    public String[] getRegistrationState() {
        Log.d(LOG_TAG, "getRegistrationState");
        
        synchronized(mLock) {
            Message response = mHandler.obtainMessage(EVENT_GET_REGISTRATION_STATE_DONE);
            mCM.getRegistrationState(response);
            try {
                mLock.wait();
            } catch (InterruptedException e) {
                Log.d(LOG_TAG,"interrupted while trying to get registration state");
            }
        }
        
        Log.d(LOG_TAG, "getRegistrationState:"+mRegistrationState);
        return mRegistrationState;
    }


	public void getCallForwardingOption(int commandInterfaceCFReason, int serviceClass, Message onComplete) {
		if (isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
			if (LOCAL_DEBUG) Log.d(LOG_TAG, "requesting call forwarding query.");
			Message resp;
			if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
				resp = obtainMessage(EVENT_GET_CALL_FORWARD_DONE, onComplete);
			} else {
				resp = onComplete;
			}
			mCM.queryCallForwardStatus(commandInterfaceCFReason,serviceClass,null,resp);
		}
	}
	
	public boolean isVTCall() {
		return (getCallType() == CallType.VIDEO? true:false);
	}
	
	public void setCallForwardingOption(int commandInterfaceCFAction,
			int commandInterfaceCFReason,
			int serviceClass,
			String dialingNumber,
			int timerSeconds,
			Message onComplete) {
		if (	(isValidCommandInterfaceCFAction(commandInterfaceCFAction)) &&
				(isValidCommandInterfaceCFReason(commandInterfaceCFReason))) {

			Message resp;
			if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
				resp = obtainMessage(EVENT_SET_CALL_FORWARD_DONE,
						isCfEnable(commandInterfaceCFAction) ? 1 : 0, serviceClass, onComplete);
			} else {
				resp = onComplete;
			}
			mCM.setCallForward(commandInterfaceCFAction,
					commandInterfaceCFReason,
					serviceClass,
					dialingNumber,
					timerSeconds,
					resp);
		}
	}
	

    private void handleCfuQueryResult(CallForwardInfo[] infos) {
        if (infos == null || infos.length == 0) {
            // Assume the default is not active
            // Set unconditional CFF in SIM to false
            mSIMRecords.setVoiceCallForwardingFlag(1, false);
            mSIMRecords.setVideoCallForwardingFlag(1, false);
        } else {
            for (int i = 0, s = infos.length; i < s; i++) {
                if ((infos[i].serviceClass & SERVICE_CLASS_VOICE) != 0) {
                    mSIMRecords.setVoiceCallForwardingFlag(1, (infos[i].status == 1));
                    // should only have the one
                    break;
                } else if (SERVICE_CLASS_VIDEO == infos[i].serviceClass) {
                    mSIMRecords.setVideoCallForwardingFlag(1, (infos[i].status == 1));
                    break;
                }
            }
        }
    }

    public boolean getCallForwardingIndicator(int serviceClass) {
        return mSIMRecords.getCallForwardingFlag(serviceClass);
    }

	public int getRemainTimes(int type) {	
		Log.d(LOG_TAG, "getRemainTimes type:"+type);
		
		synchronized(mLock) {
            Message response = mHandler.obtainMessage(EVENT_GET_REMIAN_TIMES_DONE);
            mCM.getRemainTimes(type,response);
            Log.d(LOG_TAG, "enter mLock.wait");
            try {
                mLock.wait();
                Log.d(LOG_TAG, "leave mLock.wait");
            } catch (InterruptedException e) {
            	Log.d(LOG_TAG,"interrupted while trying to get remain times");
            }
        }
		
		Log.d(LOG_TAG, "getRemainTimes:"+mRemainTimes);
		return mRemainTimes;
	}

    public int getPhoneId() {
        return mCM.getPhoneId();
    }

}

