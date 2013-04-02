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

import android.app.AlarmManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.IConnectivityManager;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.ServiceManager;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.ApnContext;
import com.android.internal.telephony.DataConnection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.DataConnectionTracker.State;
import com.android.internal.telephony.gsm.GsmDataConnectionTracker;
import com.android.internal.telephony.IccCard;

public class MsmsGsmDataConnectionTracker extends GsmDataConnectionTracker {

    MsmsGsmDataConnectionTracker(GSMPhone p) {
        super(p);

        ContentResolver cr = mPhone.getContext().getContentResolver();
        cr.registerContentObserver(
                Settings.System.getUriFor(Settings.System.MULTI_SIM_DATA_CALL), true,
                mDefaultDataPhoneIdObserver);

//        if (isApnTypeEnabled(Phone.APN_TYPE_DEFAULT)) {
//            MsmsGsmDataConnectionTrackerProxy.setActivePhoneId(mPhone.getPhoneId());
//        }
    }

    public void dispose() {
        ContentResolver cr = mPhone.getContext().getContentResolver();
        cr.unregisterContentObserver(mDefaultDataPhoneIdObserver);
        super.dispose();
    }

    protected void broadcastMessenger() {
        Intent intent = new Intent(ACTION_DATA_CONNECTION_TRACKER_MESSENGER);
        intent.putExtra(EXTRA_MESSENGER, new Messenger(this));
        intent.putExtra(Phone.PHONE_ID, mPhone.getPhoneId());
        mPhone.getContext().sendBroadcast(intent);
    }

    protected void onSetUserDataEnabled(boolean enabled) {
        synchronized (mDataEnabledLock) {
            final boolean prevEnabled = getAnyDataEnabled();
            if (mUserDataEnabled != enabled) {
                mUserDataEnabled = enabled;
                Settings.Secure.putIntAtIndex(mPhone.getContext().getContentResolver(),
                        Settings.Secure.MOBILE_DATA, mPhone.getPhoneId(), enabled ? 1 : 0);
                if (getDataOnRoamingEnabled() == false &&
                        mPhone.getServiceState().getRoaming() == true) {
                    if (enabled) {
                        notifyOffApnsOfAvailability(Phone.REASON_ROAMING_ON);
                    } else {
                        notifyOffApnsOfAvailability(Phone.REASON_DATA_DISABLED);
                    }
                }
                if (prevEnabled != getAnyDataEnabled()) {
                    if (!prevEnabled) {
                        resetAllRetryCounts();
                        onTrySetupData(Phone.REASON_DATA_ENABLED);
                    } else {
                        onCleanUpAllConnections(Phone.REASON_DATA_DISABLED);
                    }
                } else if (!mUserDataEnabled && isConnected()) {
                    log("mUserDataEnabled: " + mUserDataEnabled + " onCleanUpAllConnections");
                    onCleanUpAllConnections(Phone.REASON_DATA_DISABLED);
                }
            }
        }
    }

    private ContentObserver mDefaultDataPhoneIdObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            log("Default Data Phone Id is changed");
            int defaultDataPhoneId = TelephonyManager.getDefaultDataPhoneId(mPhone.getContext());
            if (DBG)
                log("defaultDataPhoneId=" + defaultDataPhoneId + " dataEnabled[APN_DEFAULT_ID]="
                        + mApnContexts.get(Phone.APN_TYPE_DEFAULT).isEnabled());
            if (defaultDataPhoneId != mPhone.getPhoneId()) {
                if (isDisconnected()) {
                    // check if we need to switch phone.
                    setDataDisabledOfDefaultAPN();
                    sendMessage(obtainMessage(EVENT_SWITCH_PHONE));
                } else {
                    for (ApnContext apnContext : mApnContexts.values()) {
                        if (apnContext.isEnabled()) {
                            apnContext.setEnabled(false);
                        }
                        if (!apnContext.isDisconnected()) {
//                            cleanUpConnection(true, apnContext);
                            disableApnType(apnContext.getApnType());
                        }
                    }
                }
            }
            if (defaultDataPhoneId == mPhone.getPhoneId()) {
                boolean dataEnabledSetting = true;
                try {
                    dataEnabledSetting = IConnectivityManager.Stub.asInterface(ServiceManager.
                        getService(Context.CONNECTIVITY_SERVICE)).getMobileDataEnabledByPhoneId(mPhone.getPhoneId());
                } catch (Exception e) {
                    // nothing to do - use the old behavior and leave data on
                }
//                if (dataEnabledSetting) {
                    setDataEnabledOfDefaultAPN();
//                }
            }
        }
    };

    @Override
    protected void onVoiceCallStarted() {
        MsmsGsmDataConnectionTrackerProxy.onVoiceCallStarted(mPhone.getPhoneId());
    }

    @Override
    protected void onVoiceCallEnded() {
        MsmsGsmDataConnectionTrackerProxy.onVoiceCallEnded(mPhone.getPhoneId());
    }

    public void onVoiceCallStartedInternal(int phoneId) {
        log("onVoiceCallStartInternal[" + mPhone.getPhoneId() + "]: isConnected=" + isConnected() +" phoneId="+phoneId);
        if(phoneId == mPhone.getPhoneId()) {
            if (isConnected() && ! mGsmPhone.mSST.isConcurrentVoiceAndDataAllowed()) {
                stopNetStatPoll();
                mPhone.notifyDataConnection(Phone.REASON_VOICE_CALL_STARTED);
            }
        } else if(!MsmsGsmDataConnectionTrackerProxy.isSupportMultiModem()){
            if (isConnected()) {
                stopNetStatPoll();
                mPhone.notifyDataConnection(Phone.REASON_VOICE_CALL_STARTED);
            }
        }
    }

    public void onVoiceCallEndedInternal(int phoneId) {
        log("onVoiceCallEndedInternal[" + mPhone.getPhoneId() + "]");
        if (isConnected()) {
            if (phoneId == mPhone.getPhoneId()) {
                if (!mGsmPhone.mSST.isConcurrentVoiceAndDataAllowed()) {
                    startNetStatPoll();
                    startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
                    notifyDataConnection(Phone.REASON_VOICE_CALL_ENDED);
                    // when VoiceCall is started,new DataCall setup is
                    // stopped,so if
                    // VoiceCall is ended,we still
                    // need to setup DataCall which is stopped just now.
                    // MsmsGsmDataConnectionTrackerProxy.onEnableNewApn(MsmsGsmDataConnectionTrackerProxy
                    // .getRequestPhoneIdBeforeVoiceCallEnd());
                } else {
                    // clean slate after call end.
                    resetPollStats();
                }
            } else if (!MsmsGsmDataConnectionTrackerProxy.isSupportMultiModem()) {
                startNetStatPoll();
                mPhone.notifyDataConnection(Phone.REASON_VOICE_CALL_ENDED);
            }
        } else if (isDisconnecting()) {
        	for(ApnContext apnContext : mApnContexts.values()) {
        		if (apnContext.getState() == State.DISCONNECTING)
                    cleanUpConnection(true, apnContext);
        	}
        } else {
            // reset reconnect timer
        	resetAllRetryCounts();
            mReregisterOnReconnectFailure = false;
            // in case data setup was attempted when we were on a voice call
//            MsmsGsmDataConnectionTrackerProxy.onEnableNewApn(MsmsGsmDataConnectionTrackerProxy
//                    .getRequestPhoneIdBeforeVoiceCallEnd());
        }
    }

    public synchronized void setDataEnabledOfDefaultAPN() {
        if (!dataEnabled[APN_DEFAULT_ID]) {
            dataEnabled[APN_DEFAULT_ID] = true;
            enabledCount++;
        }
        if (!isApnTypeEnabled(Phone.APN_TYPE_DEFAULT)) {
        	mApnContexts.get(Phone.APN_TYPE_DEFAULT).setEnabled(true);
        }
    }
    public synchronized void setDataDisabledOfDefaultAPN() {
        if (dataEnabled[APN_DEFAULT_ID]) {
            dataEnabled[APN_DEFAULT_ID] = false;
            enabledCount--;
        }
        if (isApnTypeEnabled(Phone.APN_TYPE_DEFAULT)) {
        	mApnContexts.get(Phone.APN_TYPE_DEFAULT).setEnabled(false);
        }
    }

    @Override
    protected void onEnableNewApn(ApnContext apnContext) {
        MsmsGsmDataConnectionTrackerProxy.onEnableNewApn(apnContext, mPhone.getPhoneId());
    }
    @Override
    protected void onDisconnectDone(int connId, AsyncResult ar) {
        MsmsGsmDataConnectionTrackerProxy.onDisconnectDone(connId, mPhone.getPhoneId(), ar, mPhone.getContext());
    }
    boolean tearDownNeeded = true;
    public void cleanupConnections(boolean tearDown, String reason) {
        super.cleanUpAllConnections(tearDown, reason);
    }

    public void onDisconnectDoneInternal(int connId, AsyncResult ar) {
        super.onDisconnectDone(connId, ar);
    }

    @Deprecated
    public void onDisconnectDoneInternalWithoutRetry(AsyncResult ar) {
        String reason = null;
        if(DBG) log("EVENT_DISCONNECT_DONE");
        if (ar.userObj instanceof String) {
           reason = (String) ar.userObj;
        }
        setState(State.IDLE);
        mPhone.notifyDataConnection(reason);
        mActiveApn = null;
    }

    public Phone.State getPhoneState() {
        return mPhone.getState();
    }

    public int getCurrentGprsState() {
        return mGsmPhone.mSST.getCurrentGprsState();
    }

    protected boolean isIccCardReady() {
        return mPhone.getIccCard().getState() == IccCard.State.READY;
    }

    public boolean isAutoAttachOnCreation() {
        return mAutoAttachOnCreation;
    }

    protected int apnTypeToId(String type) {
        if (TextUtils.equals(type, Phone.APN_TYPE_DM)) {
            return APN_DM_ID;
        } else if (TextUtils.equals(type, Phone.APN_TYPE_WAP)) {
            return APN_WAP_ID;
        } else {
            return super.apnTypeToId(type);
        }
    }

    protected String apnIdToType(int id) {
        switch (id) {
        case APN_DM_ID:
            return Phone.APN_TYPE_DM;
        case APN_WAP_ID:
            return Phone.APN_TYPE_WAP;
        default:
            return super.apnIdToType(id);
        }
    }
}
