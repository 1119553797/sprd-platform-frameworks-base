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
import android.database.ContentObserver;
import android.net.IConnectivityManager;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.ServiceManager;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.DataConnection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.DataConnectionTracker.State;
import com.android.internal.telephony.Phone.DataState;

public class MsmsGsmDataConnectionTracker extends GsmDataConnectionTracker {

    MsmsGsmDataConnectionTracker(GSMPhone p) {
        super(p);
//        mMasterDataEnabled = TelephonyManager.getDefaultDataPhoneId(phone.getContext()) == phone
//                .getPhoneId();
        log("MasterDataEnabled=" + mMasterDataEnabled);

        ContentResolver cr = phone.getContext().getContentResolver();
        cr.registerContentObserver(
                Settings.System.getUriFor(Settings.System.MULTI_SIM_DATA_CALL), true,
                mDefaultDataPhoneIdObserver);

        if (dataEnabled[APN_DEFAULT_ID]) {
            MsmsGsmDataConnectionTrackerProxy.setActivePhoneId(phone.getPhoneId());
        }
    }

    public void dispose() {
        ContentResolver cr = phone.getContext().getContentResolver();
        cr.unregisterContentObserver(mDefaultDataPhoneIdObserver);
    }

    protected void defaultDataChanged() {
        log("Default Data Phone Id is changed");
        int defaultDataPhoneId = TelephonyManager.getDefaultDataPhoneId(phone.getContext());

        if (DBG) log("defaultDataPhoneId=" +defaultDataPhoneId+" dataEnabled[APN_DEFAULT_ID]="+dataEnabled[APN_DEFAULT_ID]);
        if (defaultDataPhoneId != phone.getPhoneId()) {
            if (isAllPdpDisconnectDone()) {
                // check if we need to switch phone.
                setDataDisabledOfDefaultAPN();
                sendMessage(obtainMessage(EVENT_SWITCH_PHONE));
            } else {
                log("isApnTypeActive(Phone.APN_TYPE_DEFAULT)="
                        + isApnTypeActive(Phone.APN_TYPE_DEFAULT));
                disableApnType(Phone.APN_TYPE_DEFAULT);
            }
        }

        if (defaultDataPhoneId == phone.getPhoneId()) {
            boolean dataEnabledSetting = true;
            try {
                dataEnabledSetting = IConnectivityManager.Stub.asInterface(ServiceManager.
                    getService(Context.CONNECTIVITY_SERVICE)).getMobileDataEnabledByPhoneId(phone.getPhoneId());
            } catch (Exception e) {
                // nothing to do - use the old behavior and leave data on
            }
            if (dataEnabledSetting) {
                setDataEnabledOfDefaultAPN();
            }
        }
    }

    private ContentObserver mDefaultDataPhoneIdObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            defaultDataChanged();
        }
    };

    //@Override
    protected boolean trySetupData(String reason) {
        if (DBG) log("***trySetupData due to " + (reason == null ? "(unspecified)" : reason));

        Log.d(LOG_TAG, "[DSAC DEB] " + "trySetupData with mIsPsRestricted=" + mIsPsRestricted);

        if (phone.getSimulatedRadioControl() != null) {
            // Assume data is connected on the simulator
            // FIXME  this can be improved
            setState(State.CONNECTED);
            phone.notifyDataConnection(reason);

            Log.i(LOG_TAG, "(fix?) We're on the simulator; assuming data is connected");
            return true;
        }

        int gprsState = mGsmPhone.mSST.getCurrentGprsState();
        boolean desiredPowerState = mGsmPhone.mSST.getDesiredPowerState();

        if ((state == State.IDLE || state == State.SCANNING)
                && (gprsState == ServiceState.STATE_IN_SERVICE || noAutoAttach)
                && mGsmPhone.mSIMRecords.getRecordsLoaded()
                && (MsmsGsmDataConnectionTrackerProxy.isAllPhoneIdle() || mGsmPhone.mSST.isConcurrentVoiceAndData())
                && isDataAllowed()
                && !mIsPsRestricted
                && desiredPowerState ) {

            if (state == State.IDLE) {
                waitingApns = buildWaitingApns();
                waitingApnsPermanentFailureCountDown = waitingApns.size();
                if (waitingApns.isEmpty()) {
                    if (DBG) log("No APN found");
                    notifyNoData(GsmDataConnection.FailCause.MISSING_UNKNOWN_APN);
                    return false;
                } else {
                    log ("Create from allApns : " + apnListToString(allApns));
                }
            }

            if (DBG) {
                log ("Setup waitngApns : " + apnListToString(waitingApns));
            }
            return setupData(reason);
        } else {
            if (DBG)
                log("trySetupData: Not ready for data: " +
                    " dataState=" + state +
                    " gprsState=" + gprsState +
                    " sim=" + mGsmPhone.mSIMRecords.getRecordsLoaded() +
                    " UMTS=" + mGsmPhone.mSST.isConcurrentVoiceAndData() +
                    " allPhoneState=" + MsmsGsmDataConnectionTrackerProxy.getAllPhoneStateString() +
                    " isDataAllowed=" + isDataAllowed() +
                    " dataEnabled=" + getAnyDataEnabled() +
                    " roaming=" + phone.getServiceState().getRoaming() +
                    " dataOnRoamingEnable=" + getDataOnRoamingEnabled() +
                    " ps restricted=" + mIsPsRestricted +
                    " desiredPowerState=" + desiredPowerState +
                    " MasterDataEnabled=" + mMasterDataEnabled);
            return false;
        }
    }

    @Override
    protected synchronized void cleanUpConnection(boolean tearDown, String reason) {
        if (DBG) log("Clean up connection due to " + reason);

        // Clear the reconnect alarm, if set.
        if (mReconnectIntent != null) {
            AlarmManager am =
                (AlarmManager) phone.getContext().getSystemService(Context.ALARM_SERVICE);
            am.cancel(mReconnectIntent);
            mReconnectIntent = null;
        }

        setState(State.DISCONNECTING);

        if (!MsmsGsmDataConnectionTrackerProxy.isAllPhoneIdle() && !mGsmPhone.mSST.isConcurrentVoiceAndData()) {
            if (DBG) log("CleanUpConnection: Not ready for disconnect");
            return;
        }

        boolean notificationDeferred = false;
        for (DataConnection conn : pdpList) {
            if (conn.isActive() || conn.isActiving()) {
                if (tearDown) {
                    if (DBG)
                        log("cleanUpConnection: teardown, call conn.disconnect");
                    removeActiveCid(cidActive);
                    conn.disconnect(obtainMessage(EVENT_DISCONNECT_DONE, reason));
                    notificationDeferred = true;
                } else {
                    if (DBG)
                        log("cleanUpConnection: !tearDown, call conn.resetSynchronously");
                    conn.resetSynchronously();
                    notificationDeferred = false;
                }
            }
        }
        stopNetStatPoll();

        if (!notificationDeferred) {
            if (DBG) log("cleanupConnection: !notificationDeferred");
            gotoIdleAndNotifyDataConnection(reason);
        }
    }

    @Override
    protected void onVoiceCallStarted() {
        MsmsGsmDataConnectionTrackerProxy.onVoiceCallStart(phone.getPhoneId());
    }
    public void onVoiceCallStartInternal(int phoneId) {
        log("onVoiceCallStartInternal[" + phone.getPhoneId() + "]: state=" + state +" phoneId="+phoneId);
        if(phoneId == phone.getPhoneId()) {
            if (state == State.CONNECTED && ! mGsmPhone.mSST.isConcurrentVoiceAndData()) {
                stopNetStatPoll();
                phone.notifyDataConnection(Phone.REASON_VOICE_CALL_STARTED);
            }
        } else if(!MsmsGsmDataConnectionTrackerProxy.isSupportMultiModem()){
            if (state == State.CONNECTED) {
                stopNetStatPoll();
                phone.notifyDataConnection(Phone.REASON_VOICE_CALL_STARTED);
            }
        }
    }
    @Override
    protected void onVoiceCallEnded() {
        MsmsGsmDataConnectionTrackerProxy.onVoiceCallEnded(phone.getPhoneId());
    }

    public void onVoiceCallEndedInternal(int phoneId) {
        log("onVoiceCallEndedInternal[" + phone.getPhoneId() + "]: state=" + state +" phoneId="+phoneId);
        if (state == State.CONNECTED && phoneId == phone.getPhoneId()) {
            if (!mGsmPhone.mSST.isConcurrentVoiceAndData()) {
                startNetStatPoll();
                phone.notifyDataConnection(Phone.REASON_VOICE_CALL_ENDED);
            } else {
                // clean slate after call end.
                resetPollStats();
            }
        } else if (state == State.CONNECTED && !MsmsGsmDataConnectionTrackerProxy.isSupportMultiModem()) {
            startNetStatPoll();
            phone.notifyDataConnection(Phone.REASON_VOICE_CALL_ENDED);
        } else if (state == State.DISCONNECTING) {
            cleanUpConnection(true, Phone.REASON_VOICE_CALL_ENDED);
        } else if(MsmsGsmDataConnectionTrackerProxy.isActivePhoneId(phone.getPhoneId())) {
            // reset reconnect timer
            mRetryMgr.resetRetryCount();
            mReregisterOnReconnectFailure = false;
            // in case data setup was attempted when we were on a voice call
            //trySetupData(Phone.REASON_VOICE_CALL_ENDED);
            //MsmsGsmDataConnectionTrackerProxy.trySetupData(phone.getPhoneId(),
            //      Phone.REASON_VOICE_CALL_ENDED);
            MsmsGsmDataConnectionTrackerProxy.onEnableNewApn(MsmsGsmDataConnectionTrackerProxy
                    .getRequestPhoneIdBeforeVoiceCallEnd());
        }
    }

    public synchronized void setDataEnabledOfDefaultAPN() {
        if (!dataEnabled[APN_DEFAULT_ID]) {
            dataEnabled[APN_DEFAULT_ID] = true;
            enabledCount++;
        }
    }
    public synchronized void setDataDisabledOfDefaultAPN() {
        if (dataEnabled[APN_DEFAULT_ID]) {
            dataEnabled[APN_DEFAULT_ID] = false;
            enabledCount--;
        }
    }
    @Override
    protected void onEnableNewApn() {
        MsmsGsmDataConnectionTrackerProxy.onEnableNewApn(phone.getPhoneId());
    }

    @Override
    protected void onDisconnectDone(AsyncResult ar) {
        //super.onDisconnectDone(ar);
        //Message msg = MsmsGsmDataConnectionTrackerProxy.getInstance().
        //    obtainMessage(MsmsGsmDataConnectionTrackerProxy.EVENT_DISCONNECT_DONE,
        //            mGsmPhone.getPhoneId(), 0);
        //msg.sendToTarget();
        MsmsGsmDataConnectionTrackerProxy.onDisconnectDone(phone.getPhoneId(), ar, phone.getContext());
    }

    public void cleanupConnections(boolean tearDown, String reason) {
        onCleanUpConnection(tearDown, reason);
    }

    public void onEnableNewApnInternal() {
        super.onEnableNewApn();
    }

    public void onDisconnectDoneInternal(AsyncResult ar) {
        super.onDisconnectDone(ar);
    }

    public void onDisconnectDoneInternalWithoutRetry(AsyncResult ar) {
        String reason = null;
        if(DBG) log("EVENT_DISCONNECT_DONE");
        if (ar.userObj instanceof String) {
           reason = (String) ar.userObj;
        }
        setState(State.IDLE);
        phone.notifyDataConnection(reason);
        mActiveApn = null;
    }

    public boolean getMasterDataEnabled() {
        return mMasterDataEnabled;
    }

    public Phone.State getPhoneState() {
        return phone.getState();
    }

    public int getCurrentGprsState() {
        return mGsmPhone.mSST.getCurrentGprsState();
    }
    protected void log(String s) {
        Log.d(LOG_TAG, "[MsmGsmDataConnectionTracker-phoneId" + mGsmPhone.getPhoneId() + "] " + s);
    }
    public int getDefaultDataPhoneId() {
        return TelephonyManager.getDefaultDataPhoneId(phone.getContext());
    }

    @Override
    protected String getActiveApnString(String apntype) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected String[] getActiveApnTypes(String apntype) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DataState getDataConnectionState(String apnType) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected boolean allPhoneIdle() {
        return (MsmsGsmDataConnectionTrackerProxy.isAllPhoneIdle());
    }
    /*
    protected synchronized void onEnableApn(int apnId, int enabled) {
        if (DBG) {
            Log.d(LOG_TAG, "EVENT_APN_ENABLE_REQUEST " + apnId + ", " + enabled);
            Log.d(LOG_TAG, " dataEnabled = " + dataEnabled[apnId] +
                    ", enabledCount = " + enabledCount +
                    ", isApnTypeActive = " + isApnTypeActive(apnIdToType(apnId)));
        }
        if (enabled == ENABLED) {
            if (!dataEnabled[apnId]) {
                dataEnabled[apnId] = true;
                enabledCount++;
            }
            String type = apnIdToType(apnId);
            if (!isApnTypeActive(type)) {
                mRequestedApnType = type;
                onEnableNewApn();
            }
        } else {
            // disable
            if (dataEnabled[apnId]) {
                dataEnabled[apnId] = false;
                enabledCount--;
                if (enabledCount == 0) {
                    onCleanUpConnection(true, Phone.REASON_DATA_DISABLED);
                } else {
                    int defaultDataPhoneId = Settings.System.getInt(phone.getContext().getContentResolver(),
                            Settings.System.MULTI_SIM_DATA_CALL, PhoneFactory.DEFAULT_PHONE_ID);
                    if (defaultDataPhoneId == phone.getPhoneId()) {
                        Log.d(LOG_TAG, "enable default of default data phone");
                        if (dataEnabled[APN_DEFAULT_ID] == true &&
                                !isApnTypeActive(Phone.APN_TYPE_DEFAULT)) {
                            mRequestedApnType = Phone.APN_TYPE_DEFAULT;
                            onEnableNewApn();
                        }
                    } else {
                        Log.d(LOG_TAG, "cleanup un default phone");
                        onCleanUpConnection(true, Phone.REASON_DATA_DISABLED);
                    }
                }
            }
        }
    }
     */
}
