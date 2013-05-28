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

import android.content.Context;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.telephony.PhoneNumberUtils;
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
import static com.android.internal.telephony.CommandsInterface.SERVICE_CLASS_DATA_SYNC;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_BASEBAND_VERSION;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.TelephonyProperties;

import com.android.internal.telephony.gsm.GsmCall;


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

    protected final RegistrantList mVideoCallDisconnectRegistrants
            = new RegistrantList();

    protected final RegistrantList mVideoCallFallBackRegistrants
            = new RegistrantList();

    protected final RegistrantList mVideoCallFailRegistrants
            = new RegistrantList();

    protected final RegistrantList mVideoCallCodecRegistrants
            = new RegistrantList();

    // Instance Variables
    CallType callType;

    protected static final int EVENT_FALLBACK = 100;
    protected static final int EVENT_VIDEOCALLFAIL = 101;
    protected static final int EVENT_VIDEOCALLCODEC = 102;
    protected static final int EVENT_GSM_AUTHEN_DONE       = 103;
    protected static final int EVENT_USIM_AUTHEN_DONE       = 104;
    protected static final int EVENT_GET_SIM_TYPE_DONE       = 105;
    protected static final int EVENT_GET_REGISTRATION_STATE_DONE       = 106;
    protected static final int EVENT_GET_REMIAN_TIMES_DONE       = 107;

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

//        if (ci instanceof SimulatedRadioControl) {
//            mSimulatedRadioControl = (SimulatedRadioControl) ci;
//        }

        mDataConnectionTracker = MsmsGsmDataConnectionTrackerProxy.getTrackerInstance(this);
        //mCM.setPhoneType(Phone.PHONE_TYPE_TD);

        //Change the system property
        /*SystemProperties.set(TelephonyProperties.CURRENT_ACTIVE_PHONE,
                new Integer(Phone.PHONE_TYPE_TD).toString());*/

        mCM.setOnVPFallBack(this, EVENT_FALLBACK, null);
        mCM.setOnVPFail(this, EVENT_VIDEOCALLFAIL, null);
        mCM.setOnVPCodec(this, EVENT_VIDEOCALLCODEC, null);
        HandlerThread thread = new HandlerThread("TDSyncSender");
        thread.start();

        mHandler = new SyncHandler(thread.getLooper());
    }

    public void dispose() {
        mCM.unSetOnVPFallBack(this);
        mCM.unSetOnVPFail(this);
        mCM.unSetOnVPCodec(this);
        synchronized (PhoneProxy.lockForRadioTechnologyChange) {
            super.dispose();
        }
    }

    protected void finalize() {
        if(LOCAL_DEBUG) Log.d(LOG_TAG, "TDPhone finalized");
    }

    public String getPhoneName() {
        return "TD";
    }

    /*public int getPhoneType() {
        return Phone.PHONE_TYPE_TD;
    }*/

    public void
    notifyCallForwardingIndicator(int serviceClass) {
        mNotifier.notifyCallForwardingChanged(this, serviceClass);
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

    public void getCallWaiting(Message onComplete) {
        mCM.queryCallWaiting(CommandsInterface.SERVICE_CLASS_VOICE, onComplete);
    }

    @Override
    public void handleMessage (Message msg) {
        AsyncResult ar;
        Message onComplete;

        switch (msg.what) {
            case EVENT_SET_CALL_FORWARD_DONE:
                ar = (AsyncResult)msg.obj;
                if (ar.exception == null) {
                    Log.d(LOG_TAG, "Event EVENT_SET_CALL_FORWARD_DONE Received msg.arg2=" + msg.arg2);
                    if ((msg.arg2 & SERVICE_CLASS_VOICE) != 0) {
                        ((SIMRecords)mIccRecords).setVoiceCallForwardingFlag(1, msg.arg1 == 1);
                    } else if (SERVICE_CLASS_DATA_SYNC == msg.arg2) {
                        ((SIMRecords)mIccRecords).setVideoCallForwardingFlag(1, msg.arg1 == 1);
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

            case EVENT_FALLBACK:
                notifyVideoCallFallBack((AsyncResult)msg.obj);
            break;
            case EVENT_VIDEOCALLFAIL:
                notifyVideoCallFail((AsyncResult)msg.obj);
            break;
            case EVENT_VIDEOCALLCODEC:
                notifyVideoCallCodec((AsyncResult)msg.obj);
            break;

            default:
                super.handleMessage(msg);

        }
    }

    /******** 	New Added for VideoCall    ********/

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

    public void registerForVideoCallCodec(Handler h, int what, Object obj){
        checkCorrectThread(h);

        mVideoCallCodecRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForVideoCallCodec(Handler h){
        mVideoCallCodecRegistrants.remove(h);
    }

    public void registerForGprsAttached(Handler h,int what, Object obj) {
        Log.i(LOG_TAG, "registerForGprsAttached()");
        mSST.registerForDataConnectionAttached(h, what, obj);
    }

    public void unregisterForGprsAttached(Handler h) {
        Log.i(LOG_TAG, "unregisterForGprsAttached()");
        mSST.unregisterForDataConnectionAttached(h);
    }

    public void registerForGprsDetached(Handler h, int what, Object obj) {
        Log.i(LOG_TAG, "registerForGprsDetached()");
        mSST.registerForDataConnectionDetached(h, what, obj);
    }

    public void unregisterForGprsDetached(Handler h) {
        Log.i(LOG_TAG, "unregisterForGprsDetached()");
        mSST.unregisterForDataConnectionDetached(h);
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
        if ((!getRingingCall().isIdle() && getRingingCall().isVideo())
        || (!getForegroundCall().isIdle() && getForegroundCall().isVideo())){
            return CallType.VIDEO;
        }
        return CallType.VOICE;
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
            return mCT.dialVP(newDialString);
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
        mCT.fallBack();
    }

    public void  controlCamera(boolean bEnable) throws CallStateException{
        mCM.controlVPCamera(bEnable, null);
    }

    public void  controlAudio(boolean bEnable) throws CallStateException{
        mCM.controlVPAudio(bEnable, null);
    }

    public void  codecVP(int type, Bundle param) {
        mCM.codecVP(type, param, null);
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
            mCM.getVoiceRegistrationState(response);
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
        if ((isValidCommandInterfaceCFAction(commandInterfaceCFAction)) &&
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
            ((SIMRecords)mIccRecords).setVoiceCallForwardingFlag(1, false);
            ((SIMRecords)mIccRecords).setVideoCallForwardingFlag(1, false);
        } else {
            for (int i = 0, s = infos.length; i < s; i++) {
                Log.d(LOG_TAG, "handleCfuQueryResult i: " + i + ", serviceClass:" + infos[i].serviceClass + ", status: " + infos[i].status );
                if ((infos[i].serviceClass & SERVICE_CLASS_VOICE) != 0) {
                    ((SIMRecords)mIccRecords).setVoiceCallForwardingFlag(1, (infos[i].status == 1));
                    // should only have the one
                    break;
                } else if ((infos[i].serviceClass & SERVICE_CLASS_DATA_SYNC) != 0) {
                    ((SIMRecords)mIccRecords).setVideoCallForwardingFlag(1, (infos[i].status == 1));
                    break;
                }
            }
        }
    }

    public boolean getCallForwardingIndicator(int serviceClass) {
        return ((SIMRecords)mIccRecords).getCallForwardingFlag(serviceClass);
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

    public void setIccCard(boolean turnOn) {
        mCM.setSIMPower(turnOn, null);
    }

    public int getPhoneId() {
        return mCM.getPhoneId();
    }

    //added for 6058 phone_01
    /**
     *  use this method to synchronize card using state between framework and  upper levels
     *  An extreme situation this method needed is that one card create MO and the other has MT at the sametime
     */
    public void recordPhoneState(Phone.State state){
        mCT.recordPhoneState(state);
    }
}

