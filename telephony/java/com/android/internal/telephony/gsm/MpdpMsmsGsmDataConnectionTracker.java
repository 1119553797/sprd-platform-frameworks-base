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
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.EventLog;
import android.util.Log;

import com.android.internal.telephony.DataConnection;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.DataConnectionTracker.State;
import com.android.internal.telephony.Phone.DataState;
import com.android.internal.telephony.gsm.GsmDataConnectionTracker.DisconnectData;

public class MpdpMsmsGsmDataConnectionTracker extends MsmsGsmDataConnectionTracker {

    MpdpMsmsGsmDataConnectionTracker(GSMPhone p) {
        super(p);
    }

    protected GsmDataConnection dataServiceTable[] =new GsmDataConnection[APN_NUM_TYPES];

    @Override
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
                    waitingApnsPermanentFailureCountDown = waitingApns.size();
                    if (waitingApns.isEmpty()) {
                        if (DBG)
                            log("No APN found");
                        if (!isAnyPdpActive()) {
                            notifyNoData(GsmDataConnection.FailCause.MISSING_UNKNOWN_APN);
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
    protected boolean setupData(String reason) {
        ApnSetting apn;
        GsmDataConnection pdp;
        //if now a pdp's state is connecting,another setupdata request should not be supported
        if(mActivePdp!=null&&mActivePdp.isActiving()){
            if (DBG)
                log("*** setupData: a pdp is setup now,another setup request is not supported!!!");
            return false;
        }
        apn = getNextApn();
        if (apn == null) return false;
        pdp = findFreePdp();
        if (pdp == null) {
            if (DBG)
                log("*** setupData: No free GsmDataConnection found!");
           // return false;
            cleanUpOnePdp(reason);
            return false;
        }
        mActiveApn = apn;

        Message msg = obtainMessage();
        msg.what = EVENT_DATA_SETUP_COMPLETE;
        msg.obj = reason;
        pdp.connect(msg, apn);
        mActivePdp= pdp;
        //add pdp to pdp table
        if (mActiveApn != null) {
            for (String type : mActiveApn.types) {
                if (apnTypeToId(type) != APN_INVALID_ID
                        && dataServiceTable[apnTypeToId(type)] == null) {
                    dataServiceTable[apnTypeToId(type)] = mActivePdp;
                    if (mRequestedApnType == Phone.APN_TYPE_DEFAULT) {
                        setHttpProxy(mActiveApn.proxy, mActiveApn.port);
                    } else if (mRequestedApnType == Phone.APN_TYPE_MMS) {
                     // do nothing even though proxy is set
                    } else {
                        if (apn.proxy != null && apn.proxy.length() > 0) {
                            setHttpProxy(mActiveApn.proxy, mActiveApn.port);
                        }
                    }
                    if (DBG)
                        log("setupData,type=" + type + " mActivePdp=" + mActivePdp);
                } else if (apnTypeToId(type) == APN_INVALID_ID && type.equals(Phone.APN_TYPE_ALL)) {
                    // if apn type is APN_TYPE_ALL,add mActivepdp to all
                    for (int i = 0; i < APN_NUM_TYPES; i++) {
                        if (dataServiceTable[i] == null) {
                            dataServiceTable[i] = mActivePdp;
                            if (mRequestedApnType == Phone.APN_TYPE_DEFAULT) {
                                setHttpProxy(mActiveApn.proxy, mActiveApn.port);
                            } else if (mRequestedApnType == Phone.APN_TYPE_MMS) {
                             // do nothing even though proxy is set
                            } else {
                                if (apn.proxy != null && apn.proxy.length() > 0) {
                                    setHttpProxy(mActiveApn.proxy, mActiveApn.port);
                                }
                            }
                        }
                    }
                }
            }
        }
        if(state == State.IDLE || state == State.FAILED){
            setState(State.INITING);
        }
        if (mActiveApn != null) {
            for (String type : mActiveApn.types) {
                if (apnTypeToId(type) != APN_INVALID_ID && dataEnabled[apnTypeToId(type)]
                        && mActiveApn.canHandleType(type)
                        && dataServiceTable[apnTypeToId(type)] == null) {
                        phone.notifyDataConnection(type, reason);
                }
            }
        }
        //phone.notifyDataConnection(reason);
        return true;
    }

    @Override
    protected void onDataSetupComplete(AsyncResult ar) {
        String reason = null;
        if (ar.userObj instanceof String) {
            reason = (String) ar.userObj;
        }

        if (ar.exception == null) {
            // everything is setup
            for(GsmDataConnection pdp: pdpList){
                if(pdp.getCid()==cidActive && pdp.getApn()!=null){
                    for(String type: pdp.getApn().types){
                        if (type.equals(Phone.APN_TYPE_DEFAULT) && isApnTypeActive(type)) {
                            SystemProperties.set("gsm.defaultpdpcontext.active", "true");
                                    if (canSetPreferApn && preferredApn == null) {
                                        Log.d(LOG_TAG, "PREFERED APN is null");
                                        preferredApn = pdp.getApn();
                                        setPreferredApn(preferredApn.id);
                                        apnChangeFlag=false;
                                    }
                        } else {
                            SystemProperties.set("gsm.defaultpdpcontext.active", "false");
                        }
                        MsmsGsmDataConnectionTrackerProxy.resetRequestPhoneIdBeforeVoiceCallEnd();
                        MsmsGsmDataConnectionTrackerProxy.setActivePhoneId(phone.getPhoneId());
                        MpdpMsmsGsmDataConnectionTracker.this.sendEmptyMessageDelayed(EVENT_UPDATE_SNTP_TIME, 10000);
                        // notifyDataConnection(type, reason);
                    }
                }
            }
          //notify all service types if needed.
            if (mActiveApn != null) {
                setState(State.CONNECTED);
                for (String type : mActiveApn.types) {
                    if (apnTypeToId(type) != APN_INVALID_ID
                            && dataServiceTable[apnTypeToId(type)] != null
                            && dataEnabled[apnTypeToId(type)] == true) {

                        notifyDataConnection(type, reason);
                    } else if (apnTypeToId(type) == APN_INVALID_ID
                            && type.equals(Phone.APN_TYPE_ALL)) {
                        //if apn type is APN_TYPE_ALL,only notifyDataConnection when dataEnabled is true
                        for (int i = 0; i < APN_NUM_TYPES; i++) {
                            if (dataEnabled[i] == true && dataServiceTable[i] != null) {
                                notifyDataConnection(apnIdToType(i), reason);
                            }
                        }
                    }

                }
            }
            if(DBG) dumpDataServiceTable();
            mActivePdp=null;
            // For simultaneous PDP support, we need to build another
            // trigger another TRY_SETUP_DATA for the next APN type.  (Note
            // that the existing connection may service that type, in which
            // case we should try the next type, etc.
            // check if we have other data connection to setup
            for (int i = 0; i < dataEnabled.length; i++) {
                log("dataEnabled[" + i + "]=" + dataEnabled[i] + " PDP=" + dataServiceTable[i]);
                if (dataEnabled[i] == true && dataServiceTable[i] == null) {
                    trySetupData(reason);
                    break;
                }
            }
        } else {
            GsmDataConnection.FailCause cause;
            cause = (GsmDataConnection.FailCause) (ar.result);
            if(DBG) log("PDP setup failed " + cause);
                    // Log this failure to the Event Logs.
            if (cause.isEventLoggable()) {
                GsmCellLocation loc = ((GsmCellLocation)phone.getCellLocation());
                EventLog.writeEvent(EventLogTags.PDP_SETUP_FAIL,
                        cause.ordinal(), loc != null ? loc.getCid() : -1,
                        TelephonyManager.getDefault().getNetworkType());
            }

            // Count permanent failures and remove the APN we just tried
            waitingApnsPermanentFailureCountDown -= cause.isPermanentFail() ? 1 : 0;
            if (waitingApns!=null&&!waitingApns.isEmpty()) {
                waitingApns.remove(0);
            }
            if (DBG)
                log("onDataSetupComplete: waitingApns.size=" + Integer.toString(waitingApns.size())
                        + " waitingApnsPermanenatFailureCountDown="
                        + Integer.toString(waitingApnsPermanentFailureCountDown));

            // See if there are more APN's to try
            if (waitingApns.isEmpty()) {
                if (waitingApnsPermanentFailureCountDown == 0) {
                    if (DBG) log("onDataSetupComplete: Permanent failures stop retrying");
                    if (!isAnyPdpActive()) {
                        notifyNoData(cause);
                    }
                    phone.notifyDataConnection(mRequestedApnType, Phone.REASON_APN_FAILED);
                } else {
                    if (DBG) log("onDataSetupComplete: Not all permanent failures, retry");
                    startDelayedRetry(cause, reason);
                }
            } else {
                if (DBG) log("onDataSetupComplete: Try next APN");
                setState(State.SCANNING);
                // Wait a bit before trying the next APN, so that
                // we're not tying up the RIL command channel
                sendMessageDelayed(obtainMessage(EVENT_TRY_SETUP_DATA, reason), APN_DELAY_MILLIS);
            }
          //delete PDP from data service table
            for (int i = 0; i < dataServiceTable.length; i++) {
                if (dataServiceTable[i] == mActivePdp) {
                    dataServiceTable[i] = null;
                }
            }
            dumpDataServiceTable();
            mActivePdp=null;
        }
    }

  @Override
    protected void cleanUpConnection(boolean tearDown, String reason) {
        boolean closeAll = false;

        if (DBG) log("Clean up connection due to " + reason);

        // Clear the reconnect alarm, if set.
        if (mReconnectIntent != null) {
            AlarmManager am =
                (AlarmManager) phone.getContext().getSystemService(Context.ALARM_SERVICE);
            am.cancel(mReconnectIntent);
            mReconnectIntent = null;
        }

        if (!MsmsGsmDataConnectionTrackerProxy.isAllPhoneIdle() && !mGsmPhone.mSST.isConcurrentVoiceAndData()) {
            if (DBG) log("CleanUpConnection: Not ready for disconnect");
            return;
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

        boolean notificationDeferred = true;
        GsmDataConnection conn = null;
        for(int i=0;i<dataEnabled.length;i++){
            if ((closeAll || dataEnabled[i] == false) && dataServiceTable[i] != null) {
                if (conn == dataServiceTable[i]) {
                    log("cleanUpConnection:conn already handled,ignor!");
                    phone.notifyDataConnection(apnIdToType(i),reason);
                    break;
                } else {
                    conn = dataServiceTable[i];
                }
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
                        removeActiveCid(conn.getCid());
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
                stopNetStatPoll();
            }
        }
        //Already done this at line 359
        //if (!notificationDeferred) {
        //    if (DBG) log("cleanupConnection: !notificationDeferred");
        //    gotoNotifyDataConnection(reason);
        //}
    }

    @Override
    public void onDisconnectDoneInternal(AsyncResult ar) {
        DisconnectData cd=null;
        if(DBG) log("EVENT_DISCONNECT_DONE");
//        if (ar.userObj instanceof String) {
//           reason = (String) ar.userObj;
//        }
        if(ar.userObj  instanceof DisconnectData){
            cd=(DisconnectData)ar.userObj;
        }


        mActiveApn = null;
        for(int i=0;i<dataServiceTable.length;i++){
           if(dataServiceTable[i]==cd.getGsmDataConnection()){
               log("***Disconnected DC="+cd.getGsmDataConnection());
               phone.notifyDataConnection(apnIdToType(i),cd.getReason());
               dataServiceTable[i]=null;
           }
       }
       if(DBG)  dumpDataServiceTable();

       if(isAllPdpDisconnectDone()){
           log("All Connections are disconnected!");
           setState(State.IDLE);
           phone.notifyDataConnection(Phone.APN_TYPE_DEFAULT, Phone.REASON_DATA_DISABLED);
       }
       if (retryAfterDisconnected(cd.getReason())) {
           trySetupData(cd.getReason());
       }
    }

    @Override
    public synchronized int enableApnType(String type) {
        int id = apnTypeToId(type);
        if (id == APN_INVALID_ID) {
            return Phone.APN_REQUEST_FAILED;
        }

        if (DBG) log2("[" + phone.getPhoneId() + "]enableApnType("+type+"), isApnTypeActive = "
                + isApnTypeActive(type) + " and state = " + state);

        if (!isApnTypeAvailable(type)) {
            if (DBG) log2("type not available");
            return Phone.APN_TYPE_NOT_AVAILABLE;
        }

        if (isApnTypeFilters(type)) {
            if (DBG) log2("type is fiters");
            return Phone.APN_TYPE_NOT_AVAILABLE;
        }


        // just because it's active doesn't mean we had it explicitly requested before
        // (a broad default may handle many types).  make sure we mark it enabled
        // so if the default is disabled we keep the connection for others
        setEnabled(id, true);

        if (type.equals(Phone.APN_TYPE_DEFAULT)) {
            if (isApnTypeActive(type) && isPreferApnActive()
                    && (phone.getState() == Phone.State.IDLE || isConVoiceAndData())) {
                if (state == State.INITING) return Phone.APN_REQUEST_STARTED;
                else if (state == State.CONNECTED) return Phone.APN_ALREADY_ACTIVE;
            }
            return Phone.APN_REQUEST_STARTED;
        }
        if (isApnTypeActive(type) && (phone.getState() == Phone.State.IDLE || isConVoiceAndData())) {
            if (state == State.INITING) return Phone.APN_REQUEST_STARTED;
            else if (state == State.CONNECTED) return Phone.APN_ALREADY_ACTIVE;
        }
        return Phone.APN_REQUEST_STARTED;
    }

    @Override
    protected synchronized void onEnableApn(int apnId, int enabled) {
        if (DBG) {
            log2("EVENT_APN_ENABLE_REQUEST " + apnId + ", " + enabled);
            log2(" dataEnabled = " + dataEnabled[apnId] +
                    ", enabledCount = " + enabledCount +
                    ", isApnTypeActive = " + isApnTypeActive(apnIdToType(apnId)));
        }

        log2("onEnableApn enter: " + apnId + ", " + enabled+"  dataEnabled["+apnId+"]="+dataEnabled[apnId]);

        if (enabled == ENABLED) {
            if (!dataEnabled[apnId]) {
                dataEnabled[apnId] = true;
                enabledCount++;
            }
            String type = apnIdToType(apnId);
            if (type.equals(Phone.APN_TYPE_DEFAULT) && !isPreferApnActive() && dataServiceTable[apnId] != null) {
                    dataServiceTable[apnId] = null;
                    mRequestedApnType = type;
                    onEnableNewApn();
            }
            if (!isApnTypeActive(type) || state == State.DISCONNECTING) {
                log2("type:" + type+"mRequestedApnType:"+mRequestedApnType);
                mRequestedApnType = type;
                onEnableNewApn();
            }
        } else {
            // disable
            if (dataEnabled[apnId]) {
                dataEnabled[apnId] = false;
                enabledCount--;

                onCleanUpConnection(true, Phone.REASON_APN_SWITCHED);

                //if (enabledCount == 0) {
                //    onCleanUpConnection(true, Phone.REASON_DATA_DISABLED);
                //} else
                log2("dataEnabled[APN_DEFAULT_ID]=" + dataEnabled[APN_DEFAULT_ID]
                        + ",isApnTypeActive(Phone.APN_TYPE_DEFAULT)="
                        + isApnTypeActive(Phone.APN_TYPE_DEFAULT) + ",mIsWifiConnected="
                        + mIsWifiConnected);
                if ((dataEnabled[APN_DEFAULT_ID] == true &&
                        !isApnTypeActive(Phone.APN_TYPE_DEFAULT))
                        &&!mIsWifiConnected) {
                    log2("type:  " + apnIdToType(apnId)+"mRequestedApnType:"+mRequestedApnType);
                    mRequestedApnType = Phone.APN_TYPE_DEFAULT;
                    onEnableNewApn();

                }
            }
        }
    }

    @Override
    public void onEnableNewApnInternal() {
        // change our retry manager to use the appropriate numbers for the new APN
        if (mRequestedApnType.equals(Phone.APN_TYPE_DEFAULT)) {
            mRetryMgr = mDefaultRetryManager;
        } else {
            mRetryMgr = mSecondaryRetryManager;
        }
        mRetryMgr.resetRetryCount();

        // TODO:  To support simultaneous PDP contexts, this should really only call
        // cleanUpConnection if it needs to free up a GsmDataConnection.
        //cleanUpConnection(true, Phone.REASON_APN_SWITCHED);
        // here we try to setup a new pdp connection
        String reason =Phone.REASON_APN_SWITCHED;
        Log.i(LOG_TAG, "***********************try to setup new pdp Connection");
        trySetupData(reason);
    }

    private boolean isAnyPdpActive() {
        for (int i = 0; i < dataServiceTable.length; i++) {
            if (dataServiceTable[i] != null && !dataServiceTable[i].isInactive()) {
                return true;
            }
        }
        return false;
    }

    private void cleanUpOnePdp(String reason) {
        //GsmDataConnection conn=(GsmDataConnection)pdpList.remove(0);
        for (GsmDataConnection pdp : pdpList) {
            if (pdp.isActive()) {
                DisconnectData cd=new DisconnectData(pdp,reason);
                if (DBG)
                    log("cleanUpConnection: teardown, disconnect pdp="+pdp);
                pdp.disconnect(obtainMessage(EVENT_DISCONNECT_DONE,cd));
                return;
            }
        }
     }

    protected void dumpDataServiceTable(){
        for(int i=0;i<dataServiceTable.length;i++){
            Log.d(LOG_TAG, "dataServiceTable[" + apnIdToType(i)+"]="+dataServiceTable[i]);
        }
    }

    @Override
    protected String getActiveApnString(String apnType) {
        GsmDataConnection pdp = null;
        if(apnType==null){
            return null;
        }

        if (dataServiceTable != null && dataServiceTable.length > 0
                && apnTypeToId(apnType) != APN_INVALID_ID) {
            pdp = this.dataServiceTable[apnTypeToId(apnType)];
        }
        if (pdp != null && pdp.getApn() != null) {
            return pdp.getApn().apn;
        } else {
            return null;
        }
    }

    @Override
    public String[] getActiveApnTypes(String apnType) {
        GsmDataConnection pdp = null;
        String[] result;
        if(apnType==null){
            return null;
        }
        if (dataServiceTable != null && dataServiceTable.length > 0
                && apnTypeToId(apnType) != APN_INVALID_ID) {
            pdp = this.dataServiceTable[apnTypeToId(apnType)];
        }
        if (pdp != null && pdp.getApn() != null) {
            result=pdp.getApn().types;
        } else {
            result=new String[1];
            result[0]=new String(apnType);
        }
        return result;

    }

    @Override
    protected boolean isApnTypeActive(String type) {
        // TODO: support simultaneous with List instead
        if (Phone.APN_TYPE_DUN.equals(type)) {
            ApnSetting dunApn = fetchDunApn();
            if (dunApn != null) {
                return ((mActiveApn != null) && (dunApn.toString().equals(mActiveApn.toString())));
            }
        }
        for (GsmDataConnection pdp : pdpList) {
            if (pdp.isActive() && pdp.getApn() != null && pdp.getApn().canHandleType(type)) {
                if (DBG)
                    Log.d(LOG_TAG, "isApnTypeActive=true type=" + type);
                return true;
            }
        }
        // return mActiveApn != null && mActiveApn.canHandleType(type);
        if (DBG)
            Log.d(LOG_TAG, "isApnTypeActive=false type=" + type);
        return false;
    }

    protected boolean isPreferApnActive() {
        for (GsmDataConnection pdp : pdpList) {
            if (pdp.isActive() && pdp.getApn() == getPreferredApn()) {
                if (DBG)
                    Log.d(LOG_TAG, "isPreferApnActive=true");
                return true;
            }
        }
        // return mActiveApn != null && mActiveApn.canHandleType(type);
        if (DBG)
            Log.d(LOG_TAG, "isPreferApnActive=false");
        return false;
    }

    @Override
    protected String getInterfaceName(String apnType) {
        GsmDataConnection pdp = null;
        if(apnType==null){
            return null;
        }
        if (dataServiceTable != null && dataServiceTable.length > 0
                && apnTypeToId(apnType) != APN_INVALID_ID) {
            pdp = this.dataServiceTable[apnTypeToId(apnType)];
        }
        if (pdp != null && pdp.isActive()) {
            return pdp.getInterface();
        }

        return null;
    }

    @Override
    protected String getIpAddress(String apnType) {
        GsmDataConnection pdp = null;
        if(apnType==null){
            return null;
        }
        if (dataServiceTable != null && dataServiceTable.length > 0
                && apnTypeToId(apnType) != APN_INVALID_ID) {
            pdp = this.dataServiceTable[apnTypeToId(apnType)];
        }
        if (pdp != null && pdp.isActive()) {
            return pdp.getIpAddress();
        }
        return null;
    }

    @Override
    public String getGateway(String apnType) {
        GsmDataConnection pdp = null;
        if(apnType==null){
            return null;
        }
        if (dataServiceTable != null && dataServiceTable.length > 0
                && apnTypeToId(apnType) != APN_INVALID_ID) {
            pdp = this.dataServiceTable[apnTypeToId(apnType)];
        }
        if (pdp != null && pdp.isActive()) {
            return pdp.getGatewayAddress();
        }
        return null;
    }

    @Override
    protected String[] getDnsServers(String apnType) {
        GsmDataConnection pdp = null;
        if(apnType==null){
            return null;
        }
        if (dataServiceTable != null && dataServiceTable.length > 0
                && apnTypeToId(apnType) != APN_INVALID_ID) {
            pdp = this.dataServiceTable[apnTypeToId(apnType)];
        }
        if (pdp != null && pdp.isActive()) {
            return pdp.getDnsServers();
        }
        return null;
    }

    @Override
    public DataState getDataConnectionState(String apnType) {
        GsmDataConnection pdp = null;
        if(apnType==null){
            return DataState.DISCONNECTED;
        }
        DataState ret;
        if (apnTypeToId(apnType) != APN_INVALID_ID) {
            pdp = this.dataServiceTable[apnTypeToId(apnType)];
        }
        if (pdp != null) {
            if ((pdp.isActive() || pdp.isDisconnecting())
                    && dataEnabled[apnTypeToId(apnType)] == true) {
                ret = DataState.CONNECTED;
            } else if (pdp.isInactive()) {
                ret = DataState.DISCONNECTED;
            } else if (pdp.isActiving()) {
                ret = DataState.CONNECTING;
            } else {
                ret = DataState.DISCONNECTED;
            }
        }else{
            if (mActivePdp != null) {
                ret=DataState.CONNECTING;
           } else {
               ret = DataState.DISCONNECTED;
           }
        }
        if (DBG)
            Log.d(LOG_TAG, "getDataConnectionState=" + ret + " apnType=" + apnType);
        return ret;
    }
    @Override
    public boolean isAllPdpDisconnectDone(){
        for(int i=0;i<dataServiceTable.length;i++){
            if((dataServiceTable[i]!=null) && (dataServiceTable[i].isInactive())){
                dataServiceTable[i]=null;
                log("warning!!!  dataServiceTable["+i+"] is Inactivatestate, need reset to NULL");
            }
            if(dataServiceTable[i]!=null){
                return false;
            }
        }
        return true;
    }

    public void notifyDataConnection(String type,String reason){
        //setState(State.CONNECTED);
        phone.notifyDataConnection(type,reason);
        startNetStatPoll();
        // reset reconnect timer
        mRetryMgr.resetRetryCount();
        mReregisterOnReconnectFailure = false;
    }

    protected void gotoNotifyDataConnection(String reason) {
        if (DBG) log("gotoNotifyDataConnection: reason=" + reason);
        phone.notifyDataConnection(reason);
       // mActiveApn= null;
    }
    protected void cleanAllPdp() {
        log("cleanAllPdp");
        for(int i=0;i<dataEnabled.length;i++){
            dataEnabled[i] = false;
        }
        enabledCount = 0;
        cleanUpConnection(true,Phone.REASON_APN_CHANGED);
    }
    @Override
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
                cleanAllPdp();
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
    @Override
    protected void log(String s) {
        Log.d(LOG_TAG, "[MpdpMsmGsmDataConnectionTracker-phoneId" + mGsmPhone.getPhoneId() + "] " + s);
    }
    private void setHttpProxy(String httpProxy, String httpPort) {
        if (httpProxy == null || httpProxy.length() == 0) {
            phone.setSystemProperty("net.gprs.http-proxy", null);
            return;
        }

        if (httpPort == null || httpPort.length() == 0) {
            httpPort = "8080";     // Default to port 8080
        }

        phone.setSystemProperty("net.gprs.http-proxy",
                "http://" + httpProxy + ":" + httpPort + "/");
    }
}
