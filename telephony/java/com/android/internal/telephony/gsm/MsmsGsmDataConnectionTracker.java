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
import com.android.internal.telephony.gsm.GsmDataConnectionTracker;
import com.android.internal.telephony.gsm.GsmDataConnectionTracker.DisconnectData;

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

    private ContentObserver mDefaultDataPhoneIdObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            log("Default Data Phone Id is changed");
            int defaultDataPhoneId = TelephonyManager.getDefaultDataPhoneId(phone.getContext());
            if (DBG)
                log("defaultDataPhoneId=" + defaultDataPhoneId + " dataEnabled[APN_DEFAULT_ID]="
                        + dataEnabled[APN_DEFAULT_ID]);
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
    };

    //@Override
    protected synchronized boolean trySetupData(String reason) {
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


        //here I get state is not IDLE or SCASNNING
        //state=State.IDLE;

       // if ((state == State.IDLE || state == State.SCANNING)
        if ((gprsState == ServiceState.STATE_IN_SERVICE || noAutoAttach)
                && mGsmPhone.mSIMRecords.getRecordsLoaded()
                && (MsmsGsmDataConnectionTrackerProxy.isAllPhoneIdle() || mGsmPhone.mSST.isConcurrentVoiceAndData())
                && isDataAllowed()
                && !mIsPsRestricted
                && desiredPowerState ) {
            for (int i = 0; i < dataEnabled.length; i++) {
                log("dataEnabled[" + i + "]=" + dataEnabled[i] + " PDP=" + dataServiceTable[i]);
                if (dataEnabled[i] == true && dataServiceTable[i] == null) {
                    mRequestedApnType = apnIdToType(i);
                    if (mRequestedApnType == Phone.APN_TYPE_DEFAULT && mIsWifiConnected) {
                        // if WIFI is connected, ignore the GPRS (default type)
                        // data setup
                        log("Ignore default type datacall setup because WIFI is connected.");
                        continue;
                    }
                    waitingApns = buildWaitingApns();
                    if (waitingApns.isEmpty()) {
                        if (DBG)
                            log("No APN found");
                        if (!isAnyPdpActive()) {
                            notifyNoData(GsmDataConnection.FailCause.MISSING_UKNOWN_APN);
                        }
                        continue;
                    } else {
                        log("Create from allApns : " + apnListToString(allApns));
                    }
                    if (DBG) {
                        log("Setup waitngApns : " + apnListToString(waitingApns));
                    }
                    return setupData(reason);
                }
            }
            log("Not found any service to setup.");
            return true;
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
    protected  synchronized void cleanUpConnection(boolean tearDown, String reason) {
        boolean closeAll = false;

        if (DBG) log("Clean up connection due to " + reason);

        // Clear the reconnect alarm, if set.
        if (mReconnectIntent != null) {
            AlarmManager am =
                (AlarmManager) phone.getContext().getSystemService(Context.ALARM_SERVICE);
            am.cancel(mReconnectIntent);
            mReconnectIntent = null;
        }
        if (reason != null) {
            if (reason.equals(Phone.REASON_DATA_DISABLED)
                    || reason.equals(Phone.REASON_RADIO_TURNED_OFF)
                    || reason.equals(Phone.REASON_APN_CHANGED)
                    || reason.equals(Phone.REASON_PDP_LOST)
                    || reason.equals(Phone.REASON_PDP_RESET)
                    || reason.equals(Phone.REASON_ROAMING_ON)) {
                closeAll = true;
            }
        }
        //setState(State.DISCONNECTING);
        if (!MsmsGsmDataConnectionTrackerProxy.isAllPhoneIdle()) {
            if (DBG) log("CleanUpConnection: Not ready for disconnect");
            return;
        }

        boolean notificationDeferred = true;
        for(int i=0;i<dataEnabled.length;i++){
            if ((closeAll || dataEnabled[i] == false) && dataServiceTable[i] != null) {
                GsmDataConnection conn = dataServiceTable[i];
                if (tearDown) {
                    if (DBG)
                        log("cleanUpConnection: teardown, call conn.disconnect. cid="+i+" conn="+conn);
                    if (conn.isActive() || conn.isActiving()) {
                        for(int j=0;j<dataServiceTable.length;j++){
                            if((i!=j)&&(conn==dataServiceTable[j]) && dataEnabled[j] != false && !closeAll){
                                log("cleanUpConnection:conn is used by other service,ignore!");
                                notificationDeferred = false;
                                break;
                                }
                        }
                        if (notificationDeferred == false) {
                            phone.notifyDataConnection(apnIdToType(i),reason);
                            break;
                        }
                        DisconnectData cd = this.getDisconnectData();
                        cd.setGsmDataConnection(conn);
                        cd.setReason(reason);
                        conn.disconnect(obtainMessage(EVENT_DISCONNECT_DONE, cd));
                    }
                } else {
                    if (DBG)
                        log("cleanUpConnection: !tearDown, call conn.resetSynchronously. conn="+conn);
                    conn.resetSynchronously();
                    notificationDeferred = false;
                }
            }
        }
        stopNetStatPoll();
        //set phone DataConnectionState as DISCONNECTING when all pdp state is DISCONNECTING
        boolean disConnectingSetFlag=true;
        boolean hasValidPDP = false;
        if (dataServiceTable != null) {
            for (GsmDataConnection pdp : dataServiceTable) {
                if (pdp!=null){
                    hasValidPDP = true;
                    if(!pdp.isDisconnecting()) {
                        disConnectingSetFlag=false;
                        break;
                    }
                }
            }
            if(hasValidPDP && disConnectingSetFlag){
                setState(State.DISCONNECTING);
            }
        }
        if (!notificationDeferred) {
            if (DBG) log("cleanupConnection: !notificationDeferred");
            //gotoIdleAndNotifyDataConnection(reason);
            gotoNotifyDataConnection(reason);
        }
    }

    @Override
    protected void onVoiceCallEnded() {
        MsmsGsmDataConnectionTrackerProxy.onVoiceCallEnded(phone.getPhoneId());
    }

    public void onVoiceCallEndedInternal() {
        log("onVoiceCallEndedInternal[" + phone.getPhoneId() + "]: state=" + state);
        if (state == State.CONNECTED) {
            if (!mGsmPhone.mSST.isConcurrentVoiceAndData()) {
                startNetStatPoll();
                phone.notifyDataConnection(Phone.REASON_VOICE_CALL_ENDED);
                // when VoiceCall is started,new DataCall setup is stopped,so if
                // VoiceCall is ended,we still
                // need to setup DataCall which is stopped just now.
                MsmsGsmDataConnectionTrackerProxy.onEnableNewApn(MsmsGsmDataConnectionTrackerProxy
                        .getRequestPhoneIdBeforeVoiceCallEnd());
            } else {
                // clean slate after call end.
                resetPollStats();
            }
        } else if (state == State.DISCONNECTING) {
            cleanUpConnection(true, Phone.REASON_VOICE_CALL_ENDED);
        } else {
            // reset reconnect timer
            mRetryMgr.resetRetryCount();
            mReregisterOnReconnectFailure = false;
            // in case data setup was attempted when we were on a voice call
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
    boolean tearDownNeeded = true;
    public void cleanupConnections(boolean tearDown, String reason) {
        onCleanUpConnection(tearDown, reason);
    }

    public void onEnableNewApnInternal() {
        super.onEnableNewApn();
    }

    public void onDisconnectDoneInternal(AsyncResult ar) {
        super.onDisconnectDone(ar);
    }

    @Deprecated
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
