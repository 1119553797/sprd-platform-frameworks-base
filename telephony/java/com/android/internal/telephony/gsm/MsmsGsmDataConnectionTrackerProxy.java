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

import java.util.ArrayList;

import android.content.Context;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.ApnContext;
import com.android.internal.telephony.DataConnection;
import com.android.internal.telephony.DataConnectionTracker;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneFactory;

public class MsmsGsmDataConnectionTrackerProxy extends Handler {
    private static final String LOG_TAG = "GSM";

    public static final int EVENT_DISCONNECT_DONE = 10;

    private static final int INVALID_PHONE_ID = -1;

    private static MsmsGsmDataConnectionTrackerProxy sInstance = new MsmsGsmDataConnectionTrackerProxy();
    private static MsmsGsmDataConnectionTracker[] sTracker;

    //private static int sRequestDisconnectPhoneId = INVALID_PHONE_ID;
    private static int sRequestConnectPhoneId = INVALID_PHONE_ID;
    private static int sActivePhoneId = INVALID_PHONE_ID;
    // sRequestPhoneIdBeforeVoiceCallEnd is used to record
    // sRequestConnectPhoneId when voiceCall Started and setup dataCall after
    // voiceCall ended.
    private static int sRequestPhoneIdBeforeVoiceCallEnd = INVALID_PHONE_ID;
    public static MsmsGsmDataConnectionTrackerProxy getInstance() {
        //if (sInstance == null) {
        //	sInstance = new MsmsGsmDataConnectionTrackerProxy();
        //}
    	return sInstance;
    }

    public static GsmDataConnectionTracker getTrackerInstance(GSMPhone phone) {
    	int phoneId = phone.getPhoneId();
    	if (sTracker == null) {
    		sTracker = new MsmsGsmDataConnectionTracker[PhoneFactory.getPhoneCount()];
    	}
        if (sTracker[phoneId] == null) {
        	sTracker[phoneId] = new MsmsGsmDataConnectionTracker(phone);
        }
        sActivePhoneId = TelephonyManager.getDefaultDataPhoneId(phone.getContext());
        return sTracker[phoneId];
    }

    private MsmsGsmDataConnectionTrackerProxy() {
        super();
    }

    public static boolean isAllPhoneIdle() {
        boolean isIdle = true;
        for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
            if (sTracker[i] != null && sTracker[i].getPhoneState() != Phone.State.IDLE) {
                isIdle = false;
                break;
            }
        }
        return isIdle;
    }

    public static String getAllPhoneStateString() {
        StringBuilder builder = new StringBuilder("");
        for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
            builder.append("Phone").append(i).append(":");
            if (sTracker[i] != null) {
                builder.append(sTracker[i].getPhoneState());
            } else {
                builder.append("null");
            }
            builder.append("; ");
        }
        return builder.toString();
    }

    public static boolean onEnableNewApn(ApnContext apnContext, int phoneId) {
        synchronized (sInstance) {
            boolean ret = false;
            log("onEnableNewApn(" + phoneId + ") activePhoneId:" + sActivePhoneId);
            if (phoneId == INVALID_PHONE_ID) {
                log("phoneId is invalid,onEnableNewApn out!!!");
                return ret;
            }
            if (sTracker == null || sTracker[phoneId] == null) {
                log("call trySetupData in Constructor");
                return ret;
            }
            sRequestConnectPhoneId = phoneId;
            sRequestPhoneIdBeforeVoiceCallEnd = sRequestConnectPhoneId;
            if (sActivePhoneId != INVALID_PHONE_ID) {
                if(sRequestConnectPhoneId!=sActivePhoneId){
                    if (sTracker[sActivePhoneId].isDisconnected()) {
                        checkAndSwitchPhone(sActivePhoneId, null);
                    } else {
                        sTracker[sActivePhoneId].cleanUpAllConnections(true,
                                "switchConnection");
                    }
                }else{
                    //sTracker[sActivePhoneId].setupDataOnReadyApns(Phone.REASON_DATA_ENABLED);
                    ret = sTracker[sActivePhoneId].trySetupData(apnContext);
                }
            } else {
                if (!sTracker[sRequestConnectPhoneId].isAutoAttachOnCreation()) {
                    // do not use this case
                    if (sTracker[sRequestConnectPhoneId].getCurrentGprsState() == ServiceState.STATE_IN_SERVICE) {
                        sTracker[sRequestConnectPhoneId].setupDataOnReadyApns("switchConnection");
                        sActivePhoneId = sRequestConnectPhoneId;
                    } else {
                        sTracker[sRequestConnectPhoneId].mGsmPhone.mCM.setGprsAttach(null);
                        sActivePhoneId = sRequestConnectPhoneId;
                    }
                } else {
                    //sTracker[sRequestConnectPhoneId].setupDataOnReadyApns("switchConnection");
                    ret = sTracker[sRequestConnectPhoneId].trySetupData(apnContext);
                    sActivePhoneId = sRequestConnectPhoneId;
                }
            }
            if(phoneId == sActivePhoneId){
                // do not need to switch phone, so we initialize sRequestConnectPhoneId.
                sRequestConnectPhoneId=INVALID_PHONE_ID;
            }
            log("onEnableNewApn out");
            return ret;
        }
    }

    private static boolean isAnyIccCardReadyExceptProvided(int exceptPhoneId) {
        boolean find = false;
        for (int i = 0; i < TelephonyManager.getPhoneCount(); i++) {
            if (i == exceptPhoneId) continue;
            if (sTracker[i].isIccCardReady()) {
                find = true;
            }
        }
        return find;
    }

    public static void onDisconnectDone(int connId, int phoneId, AsyncResult ar, Context context) {
        synchronized(sInstance) {
        	ApnContext apnContext = null;
            if (sRequestConnectPhoneId == INVALID_PHONE_ID) {
                if(ar.userObj  instanceof ApnContext){
                	apnContext = (ApnContext)ar.userObj;
                }
                if (TextUtils.equals(apnContext.getReason(), Phone.REASON_PDP_RESET)) {
                    log("set RequestConnectPhoneId to active when pdp lost/reset");
                    sRequestConnectPhoneId = sActivePhoneId;
                } else {
                    sRequestConnectPhoneId = TelephonyManager.getDefaultDataPhoneId(context);
                    log("set RequestConnectPhoneId to default when it is not specified:RequestConnectPhoneId="
                            + sRequestConnectPhoneId);
                }
            }
            if (sActivePhoneId != sRequestConnectPhoneId) {
                // switch connection
                if (sActivePhoneId != INVALID_PHONE_ID) {
                    sTracker[sActivePhoneId].onDisconnectDoneInternal(connId, ar);
                    if (sTracker[sActivePhoneId].isDisconnected()) {
                        detachGprs(sTracker[sActivePhoneId].mGsmPhone);
                    } else {
                        log("isAllPdpDisconnectDone==false, waiting...");
                        return;
                    }
                    //SystemClock.sleep(2000);
                }
                log("onDisconnectDone: switch Apn from phone" + sActivePhoneId
                        + " to phone" + sRequestConnectPhoneId);
                if (!sTracker[sRequestConnectPhoneId].isAutoAttachOnCreation()) {
                    // in the case that gprs state will be checked before setup pdp
                    if (sTracker[sRequestConnectPhoneId].getCurrentGprsState() == ServiceState.STATE_IN_SERVICE) {
                        sTracker[sRequestConnectPhoneId].setupDataOnReadyApns("switchConnection");
                    } else {
                        sTracker[sRequestConnectPhoneId].mGsmPhone.mCM.setGprsAttach(null);
                    }
                } else {
                    // in the case that gprs state will NOT be checked before setup pdp
                    sTracker[sRequestConnectPhoneId].setupDataOnReadyApns("switchConnection");
                }
                sActivePhoneId = sRequestConnectPhoneId;
                sRequestConnectPhoneId = INVALID_PHONE_ID;
            } else {
                // switch apn on the same phone
                log("onDisconnectDone: switch Apn on the same phone" + phoneId);
                sTracker[phoneId].onDisconnectDoneInternal(connId, ar);
                sRequestConnectPhoneId = INVALID_PHONE_ID;
                // detach if any other sim card is ready
                if (isAnyIccCardReadyExceptProvided(sActivePhoneId)) {
                    // detach when all pdp is disconnected
                    if (sTracker[sActivePhoneId].isDisconnected()
                            && !sTracker[sActivePhoneId].getAnyDataEnabled()) {
                        detachGprs(sTracker[sActivePhoneId].mGsmPhone);
                    }
                }
            }
            log("onDisconnectDone out");
		}
	}

    public static boolean checkAndSwitchPhone(int phoneId, Context context) {
        synchronized (sInstance) {
            log("checkAndSwitchPhone: sActivePhoneId=" + sActivePhoneId
                    + " sRequestConnectPhoneId=" + sRequestConnectPhoneId);
            if (sActivePhoneId != INVALID_PHONE_ID && sActivePhoneId != phoneId) {
                log("sActivePhoneId should be INVALID_PHONE_ID or equal to phoneId " + phoneId);
                return false;
            }
            if (sRequestConnectPhoneId == INVALID_PHONE_ID && context != null) {
                sRequestConnectPhoneId = TelephonyManager.getDefaultDataPhoneId(context);
                log("set RequestConnectPhoneId to default when it is not specified:RequestConnectPhoneId="
                        + sRequestConnectPhoneId);
            }
            if (sActivePhoneId != sRequestConnectPhoneId) {
                // switch connection
                if (sActivePhoneId != INVALID_PHONE_ID) {
                    if (sTracker[sActivePhoneId].isDisconnected()) {
                        detachGprs(sTracker[sActivePhoneId].mGsmPhone);
                    } else {
                        log("isAllPdpDisconnectDone==false, return false");
                        return false;
                    }
                    // SystemClock.sleep(2000);
                } else {
                    if (sTracker[phoneId].isDisconnected()) {
                        detachGprs(sTracker[phoneId].mGsmPhone);
                    } else {
                        log("isAllPdpDisconnectDone==false, return false");
                        return false;
                    }
                }

                log("checkAndSwitchPhone: switch Apn from phone" + sActivePhoneId + " to phone"
                        + sRequestConnectPhoneId);
                if (!sTracker[sRequestConnectPhoneId].isAutoAttachOnCreation()) {
                    // in the case that gprs state will be checked before setup pdp
                    if (sTracker[sRequestConnectPhoneId].getCurrentGprsState() == ServiceState.STATE_IN_SERVICE) {
                        sTracker[sRequestConnectPhoneId].setupDataOnReadyApns("switchConnection");
                    } else {
                        sTracker[sRequestConnectPhoneId].mGsmPhone.mCM.setGprsAttach(null);
                    }
                } else {
                    // in the case that gprs state will NOT be checked before setup pdp
                    sTracker[sRequestConnectPhoneId].setupDataOnReadyApns("switchConnection");
                }
                sActivePhoneId = sRequestConnectPhoneId;
                sRequestConnectPhoneId = INVALID_PHONE_ID;
                return true;
            } else {
                log("checkAndSwitchPhone: same phone, return false");
                sRequestConnectPhoneId = INVALID_PHONE_ID;
                return false;
            }
        }
    }

    static void onVoiceCallStarted(int phoneId) {
        for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
            sTracker[i].onVoiceCallStartedInternal();
        }
    }

    static void onVoiceCallEnded(int phoneId) {
        for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
            sTracker[i].onVoiceCallEndedInternal();
        }
    }

    static void setActivePhoneId(int phoneId) {
        if (sActivePhoneId != phoneId) {
            log("active phone id is changed:sActivePhoneId=" + sActivePhoneId + ",phoneId=" + phoneId);
            sActivePhoneId = phoneId;
        }
    }
    public static int getActivePhoneId() {
        if (sActivePhoneId != INVALID_PHONE_ID) {
            return sActivePhoneId;
        } else {
            return INVALID_PHONE_ID;
        }
    }
    public static boolean isActivePhoneId(int phoneId) {
        sActivePhoneId = ((sActivePhoneId != INVALID_PHONE_ID) ? sActivePhoneId : INVALID_PHONE_ID);
        if (sActivePhoneId == phoneId) {
            return true;
        } else {
            return false;
        }
    }

    private static void detachGprs(GSMPhone phone) {
        if (!PhoneFactory.isCardReady(phone.getPhoneId())) return;
        final DetachGprs detachGprs = new DetachGprs(phone);
        detachGprs.start();
        detachGprs.detach();
    }

    /**
     * Helper thread to turn async call to {@link SimCard#supplyPin} into
     * a synchronous one.
     */
    private static class DetachGprs extends Thread {

        private final GSMPhone mGsmPhone;

        private boolean mDone = false;
        private boolean mResult = false;

        // For replies from SimCard interface
        private Handler mHandler;

        // For async handler to identify request type
        private static final int DETACH_GPRS_COMPLETE = 100;

        public DetachGprs(GSMPhone phone) {
            mGsmPhone = phone;
        }

        @Override
        public void run() {
            Looper.prepare();
            synchronized (DetachGprs.this) {
                mHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        AsyncResult ar = (AsyncResult) msg.obj;
                        switch (msg.what) {
                            case DETACH_GPRS_COMPLETE:
                                Log.d(LOG_TAG, "DETACH_GPRS_COMPLETE");
                                synchronized (DetachGprs.this) {
                                    mResult = (ar.exception == null);
                                    mDone = true;
                                    DetachGprs.this.notifyAll();
                                }
                                break;
                        }
                    }
                };
                DetachGprs.this.notifyAll();
            }
            Looper.loop();
        }

        synchronized void detach() {

            while (mHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            Message callback = Message.obtain(mHandler, DETACH_GPRS_COMPLETE);

            mGsmPhone.mCM.setGprsDetach(callback);

            while (!mDone) {
                try {
                    Log.d(LOG_TAG, "wait for done");
                    wait();
                } catch (InterruptedException e) {
                    // Restore the interrupted status
                    Thread.currentThread().interrupt();
                }
            }
            Log.d(LOG_TAG, "done");
        }
    }

    private static void log(String s) {
        Log.d(LOG_TAG, "[MsmsDataConnectionTrackerProxy]" + s);
    }

    public static int getRequestPhoneIdBeforeVoiceCallEnd() {
        if (sRequestPhoneIdBeforeVoiceCallEnd == INVALID_PHONE_ID) {
            sRequestPhoneIdBeforeVoiceCallEnd = sActivePhoneId;
        }
        return sRequestPhoneIdBeforeVoiceCallEnd;
    }

    public static void resetRequestPhoneIdBeforeVoiceCallEnd() {
        sRequestPhoneIdBeforeVoiceCallEnd = INVALID_PHONE_ID;
    }
}
