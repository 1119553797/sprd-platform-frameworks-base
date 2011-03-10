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
import android.os.Handler;
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
import com.android.internal.telephony.IccFileHandler;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.IccSmsInterfaceManager;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneSubInfo;
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
public class TDPhone extends GSMPhone {
    // NOTE that LOG_TAG here is "TD", which means that log messages
    // from this file will go into the radio log rather than the main
    // log.  (Use "adb logcat -b radio" to see them.)
    static final String LOG_TAG = "TD";
    private static final boolean LOCAL_DEBUG = true;
	

    // Instance Variables
	VideoCallTracker mVideoCT;
	CallType callType;
	
    // Constructors

    public
    TDPhone (Context context, CommandsInterface ci, PhoneNotifier notifier) {
        this(context,ci,notifier, false);
    }

    public
    TDPhone (Context context, CommandsInterface ci, PhoneNotifier notifier, boolean unitTestMode) {
        super(context, ci, notifier, unitTestMode);
	mVideoCT = new VideoCallTracker(this);

        if (ci instanceof SimulatedRadioControl) {
            mSimulatedRadioControl = (SimulatedRadioControl) ci;
        }

        mCM.setPhoneType(Phone.PHONE_TYPE_TD);

        //Change the system property
        SystemProperties.set(TelephonyProperties.CURRENT_ACTIVE_PHONE,
                new Integer(Phone.PHONE_TYPE_TD).toString());
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

    public int getPhoneType() {
        return Phone.PHONE_TYPE_TD;
    }

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
    notifyCallForwardingIndicator() {
        mNotifier.notifyCallForwardingChanged(this);
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
    }

    public boolean canTransfer() {
        return mCT.canTransfer();
    }

    public void explicitCallTransfer() throws CallStateException {
        mCT.explicitCallTransfer();
    }

    public Call
    getForegroundCall() {
    	if (mVideoCT.isAlive())
			return mVideoCT.foregroundCall;
		else
	        return mCT.foregroundCall;
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

    public Call
    getRingingCall() {
    	if (mVideoCT.isAlive())
			return mVideoCT.ringingCall;
		else
	        return mCT.ringingCall;
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
                        isCfEnable(commandInterfaceCFAction) ? 1 : 0, 0, onComplete);
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
            case EVENT_SET_CALL_FORWARD_DONE:
                break;

            case EVENT_GET_CALL_FORWARD_DONE:
                break;


            case EVENT_SET_CLIR_COMPLETE:
                break;
				
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
			default:
				super.handleMessage(msg);

        }
    }

	/******** 	New Added for VideoCall 	********/
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
    dialVideo (String dialString) throws CallStateException {
        // Need to make sure dialString gets parsed properly
        String newDialString = PhoneNumberUtils.stripSeparators(dialString);
		Log.d(LOG_TAG,"dialVideo '" + dialString);

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

	public void setLocalDisplay(SurfaceHolder sh) {
		mVideoCT.setLocalDisplay(sh);
	}

	public void setRemoteDisplay(SurfaceHolder sh)  {
		mVideoCT.setRemoteDisplay(sh);
	}

	private void backToVoiceCall(){
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

  
     void notifyPreciseVideoCallStateChanged() {
        super.notifyPreciseVideoCallStateChangedP();
    }
	
    void notifyNewRingingVideoCall(Connection cn) {
        super.notifyNewRingingVideoCallP(cn);
    }
	
	void notifyVideoCallDisconnect(Connection cn) {
        super.notifyVideoCallDisconnectP(cn);
	}
}

