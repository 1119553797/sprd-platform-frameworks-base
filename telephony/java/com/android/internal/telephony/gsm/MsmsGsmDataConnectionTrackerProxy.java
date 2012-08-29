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
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.DataConnection;
import com.android.internal.telephony.DataConnectionTracker;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneFactory;

public class MsmsGsmDataConnectionTrackerProxy extends Handler {
    private static final String LOG_TAG = "GSM";

    public static final int EVENT_DISCONNECT_DONE = 10;

    private static final int INVALID_PHONE_ID = -1;

    private static final boolean MULTI_MODEM_SUPPORT = false;

    private static final Object sLock = new Object();
    public static int mPhoneID=-1;
    private static MsmsGsmDataConnectionTrackerProxy sInstance = new MsmsGsmDataConnectionTrackerProxy();
    private static MsmsGsmDataConnectionTracker[] sTracker;

    //private static int sRequestDisconnectPhoneId = INVALID_PHONE_ID;
    private static int sRequestConnectPhoneId = INVALID_PHONE_ID;
    private static int sActivePhoneId = INVALID_PHONE_ID;

    // sRequestPhoneIdBeforeVoiceCallEnd is used to record
    // sRequestConnectPhoneId when voiceCall Started and setup dataCall after
    // voiceCall ended.
    private static int sRequestPhoneIdBeforeVoiceCallEnd = INVALID_PHONE_ID;
    private static int sVoicePhoneId = INVALID_PHONE_ID;

    public static MsmsGsmDataConnectionTrackerProxy getInstance() {
        //if (sInstance == null) {
        //    sInstance = new MsmsGsmDataConnectionTrackerProxy();
        //}
        return sInstance;
    }

    public static GsmDataConnectionTracker getTrackerInstance(GSMPhone phone) {
        int phoneId = phone.getPhoneId();
        mPhoneID = phoneId;
        boolean supportMpdp = SystemProperties.getBoolean("persist.telephony.mpdp", true);
        if (supportMpdp) {
            Log.d(LOG_TAG, "this version support mpdp !!!");
            if (sTracker == null) {
                sTracker = new MpdpMsmsGsmDataConnectionTracker[PhoneFactory.getPhoneCount()];
            }
            if (sTracker[phoneId] == null) {
                sTracker[phoneId] = new MpdpMsmsGsmDataConnectionTracker(phone);
            }
        } else {
            Log.d(LOG_TAG, "this version do not support mpdp !!!");
            if (sTracker == null) {
                sTracker = new MsmsGsmDataConnectionTracker[PhoneFactory
                        .getPhoneCount()];
            }
            if (sTracker[phoneId] == null) {
                sTracker[phoneId] = new MsmsGsmDataConnectionTracker(phone);
            }
        }
        return sTracker[phoneId];
    }

    private MsmsGsmDataConnectionTrackerProxy() {
        super();
    }

    /** ensure only one or none connection state is not idle
     *
     * @return
     */
    private static boolean checkAllConnectionState() {
        int notIdleCount = 0;
        for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
            if (sTracker[i].getState() != DataConnectionTracker.State.IDLE) {
                notIdleCount++;
            }
        }
        return (notIdleCount <= 1);
    }

    private static void dumpAllConnectionState() {
        StringBuilder builder = new StringBuilder("All State: ");
        for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
            builder.append(i).append(" ").append(sTracker[i].getStateInString()).append("; ");
        }
        log(builder.toString());
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

    public static void onEnableNewApn(int phoneId) {
        synchronized (sInstance) {
/*
            if (!checkAllConnectionState()) {
                dumpAllConnectionState();
            }
            log("onEnableNewApn(" + phoneId + ") state:" + sTracker[0].getState() + " "
                    + sTracker[1].getState());
            if (sTracker[phoneId].getState() != DataConnectionTracker.State.IDLE) {
                // current phone is in an "activate" state, enable new apn
                // directly
                log("onEnableNewApn: switch Apn on the same phone" + phoneId);
                sTracker[phoneId].onEnableNewApnInternal();
                sRequestConnectPhoneId = INVALID_PHONE_ID;
                sRequestDisconnectPhoneId = INVALID_PHONE_ID;
            } else {
                // disconnect the "activate" phone, enable request apn later
                sRequestConnectPhoneId = phoneId;
                sRequestDisconnectPhoneId = INVALID_PHONE_ID;
                for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
                    if (i == phoneId) {
                        continue;
                    }
                    if (sTracker[i].getState() != DataConnectionTracker.State.IDLE) {
                        sRequestDisconnectPhoneId = i;
                        log("disableData(" + i + ")");
                        sTracker[i].setDataEnabled(false);
                        // sTracker[i].cleanupConnections(true,
                        // "switchConnection");
                        break;
                    }
                }
                log("onEnableNewApn: switch Apn from phone" + sRequestDisconnectPhoneId
                        + " to phone" + sRequestConnectPhoneId);
                // no phone is "activate", enable request apn directly
                if (sRequestDisconnectPhoneId == INVALID_PHONE_ID) {
                    sTracker[phoneId].onEnableNewApnInternal();
                    sRequestConnectPhoneId = INVALID_PHONE_ID;
                    sRequestDisconnectPhoneId = INVALID_PHONE_ID;
                }
            }
*/
            log("onEnableNewApn(" + phoneId + ") activePhoneId:" + sActivePhoneId);
            if (phoneId == INVALID_PHONE_ID) {
                log("phoneId is invalid,onEnableNewApn out!!!");
                return;
            }
            sRequestConnectPhoneId = phoneId;
            sRequestPhoneIdBeforeVoiceCallEnd = sRequestConnectPhoneId;
            //if (sActivePhoneId == phoneId) {
                // current phone is in an "activate" state, enable new apn directly
                //log("onEnableNewApn: switch Apn on the same phone" + phoneId);
            //} else {
                // disconnect the "activate" phone, enable request apn later
                //log("disableData(" + sActivePhoneId + ")");
                //sTracker[sActivePhoneId].setDataEnabled(false);
                //sTracker[sActivePhoneId].cleanupConnections(true,
                //        "switchConnection");
            //}
            if (sActivePhoneId != INVALID_PHONE_ID) {
                if(sRequestConnectPhoneId!=sActivePhoneId){
                    //sTracker[sActivePhoneId].cleanupConnections(true, Phone.REASON_DATA_DISABLED);
                    if (sTracker[sActivePhoneId].isAllPdpDisconnectDone()) {
                        checkAndSwitchPhone(sActivePhoneId, null);
                    } else {
                        sTracker[sActivePhoneId].cleanupConnections(true,
                        Phone.REASON_DATA_DISABLED);
                    }
                }else{
                    sTracker[sActivePhoneId].onEnableNewApnInternal();
                }
            } else {
                //sTracker[sRequestConnectPhoneId].trySetupData("switchConnection");
                //sTracker[sRequestConnectPhoneId].mGsmPhone.mCM.setGprsAttach(null);
                //sActivePhoneId = sRequestConnectPhoneId;
                //sTracker[sRequestConnectPhoneId].onEnableNewApnInternal();
                // sTracker[sRequestConnectPhoneId].trySetupData("switchConnection");
                if (sTracker[sRequestConnectPhoneId].getCurrentGprsState() == ServiceState.STATE_IN_SERVICE) {
                    sTracker[sRequestConnectPhoneId].trySetupData(Phone.REASON_APN_SWITCHED);
                    sActivePhoneId = sRequestConnectPhoneId;
                } else {
                    sTracker[sRequestConnectPhoneId].mGsmPhone.mCM.setGprsAttach(null);
                    sActivePhoneId = sRequestConnectPhoneId;
                    // sTracker[sRequestConnectPhoneId].onEnableNewApnInternal();
                }
            }
            if(phoneId == sActivePhoneId){
                // do not need to switch phone, so we initialize sRequestConnectPhoneId.
                sRequestConnectPhoneId=INVALID_PHONE_ID;
            }
            log("onEnableNewApn out");
        }
    }

    public static void onDisconnectDone(int phoneId, AsyncResult ar, Context context) {
        synchronized(sInstance) {
/*
            log("onDisconnectDone(" + phoneId + ") RequestDisconnect="
                    + sRequestDisconnectPhoneId + " RequestConnect=" + sRequestConnectPhoneId);
            if (sRequestDisconnectPhoneId == phoneId) {
                // when the phone request disconnect is disconnected, it is caused by a switching
                log("onDisconnectDone: switch Apn from phone" + sRequestDisconnectPhoneId
                        + " to phone" + sRequestConnectPhoneId);
                sTracker[sRequestDisconnectPhoneId].onDisconnectDoneInternalWithoutRetry(ar);
                //SystemClock.sleep(2000);
                // enable the phone's requestApn
                sTracker[sRequestConnectPhoneId].setDataEnabled(true);
                sRequestDisconnectPhoneId = INVALID_PHONE_ID;
                sRequestConnectPhoneId = INVALID_PHONE_ID;
            } else if (sRequestDisconnectPhoneId == INVALID_PHONE_ID
                    && sRequestConnectPhoneId == INVALID_PHONE_ID) {
                // when the only connection is disconnect or connect on the same phone
                // enable the default phone's requestApn if needed
//                ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
//                int defaultDataPhoneId = connMgr.getDefaultDataPhoneId();
                int defaultDataPhoneId = TelephonyManager.getDefaultDataPhoneId(context);
                if (phoneId != defaultDataPhoneId) {
                    log("onDisconnectDone: switch Apn back from phone" + phoneId + " to phone" + defaultDataPhoneId);
                    sTracker[phoneId].onDisconnectDoneInternalWithoutRetry(ar);
                    if (sTracker[phoneId].getMasterDataEnabled()) {
                        sTracker[phoneId].setDataEnabled(false);
                        //sTracker[defaultDataPhoneId].setDataEnabledOfDefaultAPN();
                        sTracker[defaultDataPhoneId].setDataEnabled(true);
                    }
                } else {
                    // default phone's apn switching
                    log("onDisconnectDone: switch Apn on the same phone" + phoneId);
                    sTracker[phoneId].onDisconnectDoneInternal(ar);
                }
                //sTracker[connMgr.getDefaultDataPhoneId()].onDisconnectDoneInternal(ar);
            } else {
                log("Request Disconnect phone id is not the disconnected phone id!");
            }
*/
            //if (sActivePhoneId == INVALID_PHONE_ID) {
            //    log("set activePhoneId to default when startup");
            //    sActivePhoneId = TelephonyManager.getDefaultDataPhoneId(context);
            //}
            log("onDisconnectDone(" + phoneId + ") activePhoneId=" + sActivePhoneId
                      + " RequestConnectPhoneId=" + sRequestConnectPhoneId);
            if (sActivePhoneId != phoneId) {
                log("sActivePhoneId should equal to phoneId!!!");
                log("onDisconnectDone out");
                sTracker[phoneId].onDisconnectDoneInternalWithoutRetry(ar);
                return;
            }
            if (sRequestConnectPhoneId == INVALID_PHONE_ID) {
                String reason = null;
                if (ar.userObj instanceof String) {
                   reason = (String) ar.userObj;
                }
                if (TextUtils.equals(reason, Phone.REASON_PDP_LOST)
                        || TextUtils.equals(reason, Phone.REASON_PDP_RESET)) {
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
                    sTracker[sActivePhoneId].onDisconnectDoneInternalWithoutRetry(ar);
                    if (sTracker[sActivePhoneId].isAllPdpDisconnectDone()) {
                        detachGprs(sTracker[sActivePhoneId].mGsmPhone);
                    }else{
                        log("isAllPdpDisconnectDone==false, waiting...");
                        return;
                    }
                    //SystemClock.sleep(2000);
                }

                log("onDisconnectDone: switch Apn from phone" + sActivePhoneId
                        + " to phone" + sRequestConnectPhoneId);
                //sTracker[sRequestConnectPhoneId].trySetupData("switchConnection");
                sTracker[sRequestConnectPhoneId].mGsmPhone.mCM.setGprsAttach(null);
                sActivePhoneId = sRequestConnectPhoneId;
                sRequestConnectPhoneId = INVALID_PHONE_ID;
            } else {
                // switch apn on the same phone
                log("onDisconnectDone: switch Apn on the same phone" + phoneId);
                sTracker[phoneId].onDisconnectDoneInternal(ar);
                sRequestConnectPhoneId = INVALID_PHONE_ID;
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
                    if (sTracker[sActivePhoneId].isAllPdpDisconnectDone()) {
                        detachGprs(sTracker[sActivePhoneId].mGsmPhone);
                    } else {
                        log("isAllPdpDisconnectDone==false, return false");
                        return false;
                    }
                    // SystemClock.sleep(2000);
                } else {
                    if (sTracker[phoneId].isAllPdpDisconnectDone()) {
                        detachGprs(sTracker[phoneId].mGsmPhone);
                    } else {
                        log("isAllPdpDisconnectDone==false, return false");
                        return false;
                    }
                }

                log("checkAndSwitchPhone: switch Apn from phone" + sActivePhoneId + " to phone"
                        + sRequestConnectPhoneId);
                // sTracker[sRequestConnectPhoneId].trySetupData("switchConnection");
                sTracker[sRequestConnectPhoneId].mGsmPhone.mCM.setGprsAttach(null);
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
    static boolean trySetupData(int phoneId, String reason) {
        log("trySetupData: sActivePhoneId=" + sActivePhoneId + " sRequestConnectPhoneId="
                + sRequestConnectPhoneId + " phoneId=" + phoneId + " reason:" + reason);
        if (sActivePhoneId != INVALID_PHONE_ID && sActivePhoneId == phoneId) {
            return sTracker[sActivePhoneId].trySetupData(reason);
        }
        return false;
    }

    static boolean isSupportMultiModem() {
        return MULTI_MODEM_SUPPORT;
    }

    static boolean isAnotherCardVoiceing(int phoneId) {
        boolean ret = false;

        if((sVoicePhoneId == INVALID_PHONE_ID) || (sVoicePhoneId == phoneId)) {
            ret = false;
        } else {
            ret = true;
        }
        log("isAnotherCardVoiceing phoneId: "+phoneId+" sVoicePhoneId: " + sVoicePhoneId +" result: " + ret);
        return ret;
    }

    static void onVoiceCallStart(int phoneId) {
        log("onVoiceCallStart sVoicePhoneId=" + phoneId);
        sVoicePhoneId = phoneId;
        for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
            sTracker[i].onVoiceCallStartInternal(phoneId);
        }
        //sTracker[phoneId].onVoiceCallStartInternal(phoneId);
    }

    static void onVoiceCallEnded(int phoneId) {
        sVoicePhoneId = INVALID_PHONE_ID;
        for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
            sTracker[i].onVoiceCallEndedInternal(phoneId);
        }
        //sTracker[phoneId].onVoiceCallEndedInternal();
    }

    static void setActivePhoneId(int phoneId) {
        log("active phone id is changed:sActivePhoneId=" + sActivePhoneId + ",phoneId=" + phoneId);
        sActivePhoneId = phoneId;
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
    public static boolean isActiveOrDefaultPhoneId(int phoneId) {
        int defaultPhoneId = PhoneFactory.getDefaultPhoneId();

        log("isActiveOrDefaultPhoneId sActivePhoneId=" + sActivePhoneId + ", defaultPhoneId="+defaultPhoneId+", phoneId=" + phoneId);
        if(sActivePhoneId != INVALID_PHONE_ID) {
            return (sActivePhoneId == phoneId);
        } else if(defaultPhoneId != INVALID_PHONE_ID){
            return (defaultPhoneId == phoneId);
        }
        return false;
    }
/*
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_DISCONNECT_DONE:
                log("receive EVENT_DISCONNECT_DONE");
                if (sRequestDisconnectPhoneId == msg.arg1) {
                    // it will enable the request apn
                    sTracker[sRequestConnectPhoneId].setDataEnabled(true);
                    sRequestDisconnectPhoneId = INVALID_PHONE_ID;
                    sRequestConnectPhoneId = INVALID_PHONE_ID;
                } else if (sRequestDisconnectPhoneId == INVALID_PHONE_ID
                        && sRequestConnectPhoneId == INVALID_PHONE_ID) {
                    // do nothing
                    //ConnectivityManager ConnMgr = (ConnectivityManager) phone.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                    //sTracker[]
                } else {
                    log("Request Disconnect phone id is not the disconnected phone id!");
                }
                break;
        }
    }
*/

    private static void detachGprs(GSMPhone phone) {
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
        Log.d(LOG_TAG, "[MsmGsmDataConnectionTrackerProxy-phoneId" + mPhoneID + "] " + s);
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

