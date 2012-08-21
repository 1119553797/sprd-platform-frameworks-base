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

package com.android.internal.telephony;

import static android.Manifest.permission.READ_PHONE_STATE;
import android.app.ActivityManagerNative;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.telephony.TelephonyManager;
import android.util.Config;
import android.util.Log;

import com.android.internal.telephony.IccCardStatus.CardState;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.CommandsInterface.RadioState;


/**
 * {@hide}
 */
public abstract class IccCard {
    protected String mLogTag;
    protected boolean mDbg = true;

    private IccCardStatus mIccCardStatus = null;
    protected State mState = null;
    protected PhoneBase mPhone;
    private RegistrantList mAbsentRegistrants = new RegistrantList();
    private RegistrantList mPinLockedRegistrants = new RegistrantList();
    private RegistrantList mNetworkLockedRegistrants = new RegistrantList();
    private boolean mDesiredPinLocked;
    private boolean mDesiredFdnEnabled;
    private boolean mDesiredNetworkEnabled;
    private boolean mDesiredSimLockEnabled;
    private boolean mIccPinLocked = true; // Default to locked
    private boolean mIccFdnEnabled = false; // Default to disabled.
                                            // Will be updated when SIM_READY.
    private boolean mIccSimEnabled = false;
    private boolean mIccNetworkEnabled = false;


    /* The extra data for broacasting intent INTENT_ICC_STATE_CHANGE */
    static public final String INTENT_KEY_ICC_STATE = "ss";
    /* NOT_READY means the ICC interface is not ready (eg, radio is off or powering on) */
    static public final String INTENT_VALUE_ICC_NOT_READY = "NOT_READY";
    /* ABSENT means ICC is missing */
    static public final String INTENT_VALUE_ICC_ABSENT = "ABSENT";
    /* LOCKED means ICC is locked by pin or by network */
    static public final String INTENT_VALUE_ICC_LOCKED = "LOCKED";
    /* BLOCKED means ICC is blocked */
    static public final String INTENT_VALUE_ICC_BLOCKED = "BLOCKED";
    /* READY means ICC is ready to access */
    static public final String INTENT_VALUE_ICC_READY = "READY";
    /* IMSI means ICC IMSI is ready in property */
    static public final String INTENT_VALUE_ICC_IMSI = "IMSI";
    /* LOADED means all ICC records, including IMSI, are loaded */
    static public final String INTENT_VALUE_ICC_LOADED = "LOADED";
    /* The extra data for broacasting intent INTENT_ICC_STATE_CHANGE */
    static public final String INTENT_KEY_LOCKED_REASON = "reason";
    /* PIN means ICC is locked on PIN1 */
    static public final String INTENT_VALUE_LOCKED_ON_PIN = "PIN";
    /* PUK means ICC is locked on PUK1 */
    static public final String INTENT_VALUE_LOCKED_ON_PUK = "PUK";
    /* NETWORK means ICC is locked on NETWORK PERSONALIZATION */
    static public final String INTENT_VALUE_LOCKED_NETWORK = "NETWORK";
    static public final String INTENT_VALUE_LOCKED_SIM = "SIM";
    /* REFRESH means ICC is refreshed */
    static public final String INTENT_VALUE_ICC_REFRESH= "REFRESH";

    static public final String INTENT_KEY_PHONE_ID = "phone_id";

    static public final String INTENT_KEY_FDN_STAUS = "fdn_status";

    static public final String INTENT_KEY_FDN_SIM_REFRESH = "fdn_sim_refresh";


    protected static final int EVENT_ICC_LOCKED_OR_ABSENT = 1;
    private static final int EVENT_GET_ICC_STATUS_DONE = 2;
    protected static final int EVENT_RADIO_OFF_OR_NOT_AVAILABLE = 3;
    private static final int EVENT_PINPUK_DONE = 4;
    private static final int EVENT_REPOLL_STATUS_DONE = 5;
    protected static final int EVENT_ICC_READY = 6;
    private static final int EVENT_QUERY_FACILITY_LOCK_DONE = 7;
    private static final int EVENT_CHANGE_FACILITY_LOCK_DONE = 8;
    private static final int EVENT_CHANGE_ICC_PASSWORD_DONE = 9;
    private static final int EVENT_QUERY_FACILITY_FDN_DONE = 10;
    private static final int EVENT_CHANGE_FACILITY_FDN_DONE = 11;
    private static final int EVENT_QUERY_FACILITY_NETWORK_DONE = 13;
    private static final int EVENT_CHANGE_FACILITY_NETWORK_DONE = 14;
    private static final int EVENT_QUERY_FACILITY_SIM_DONE = 15;
    private static final int EVENT_CHANGE_FACILITY_SIM_DONE = 16;
    protected static final int EVENT_ICC_STATUS_CHANGED = 12;


    /*
      UNKNOWN is a transient state, for example, after uesr inputs ICC pin under
      PIN_REQUIRED state, the query for ICC status returns UNKNOWN before it
      turns to READY
     */
    public enum State {
        UNKNOWN,
        ABSENT,
        PIN_REQUIRED,
        PUK_REQUIRED,
        NETWORK_LOCKED,
        READY,
        NOT_READY,
        BLOCKED,
        SIM_LOCKED;

        public boolean isPinLocked() {
            return ((this == PIN_REQUIRED) || (this == PUK_REQUIRED));
        }
    }

    public State getState() {
        if (mState == null) {
            switch(mPhone.mCM.getRadioState()) {
                /* This switch block must not return anything in
                 * State.isLocked() or State.ABSENT.
                 * If it does, handleSimStatus() may break
                 */
                case RADIO_OFF:
                case RADIO_UNAVAILABLE:
                case SIM_NOT_READY:
                case RUIM_NOT_READY:
                    return State.UNKNOWN;
                case SIM_LOCKED_OR_ABSENT:
                case RUIM_LOCKED_OR_ABSENT:
                    //this should be transient-only
                    return State.UNKNOWN;
                case SIM_READY:
                case RUIM_READY:
                case NV_READY:
                    return State.READY;
                case NV_NOT_READY:
                    return State.ABSENT;
            }
        } else {
            return mState;
        }

        Log.e(mLogTag, "IccCard.getState(): case should never be reached");
        return State.UNKNOWN;
    }

    public IccCard(PhoneBase phone, String logTag, Boolean dbg) {
        mPhone = phone;
        mLogTag = logTag;
        mDbg = dbg;
    }

    abstract public void dispose();

    protected void finalize() {
        if(mDbg) Log.d(mLogTag, "IccCard finalized");
    }

    /**
     * Notifies handler of any transition into State.ABSENT
     */
    public void registerForAbsent(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        mAbsentRegistrants.add(r);

        if (getState() == State.ABSENT) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForAbsent(Handler h) {
        mAbsentRegistrants.remove(h);
    }

    /**
     * Notifies handler of any transition into State.NETWORK_LOCKED
     */
    public void registerForNetworkLocked(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        mNetworkLockedRegistrants.add(r);

        if (getState() == State.NETWORK_LOCKED) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForNetworkLocked(Handler h) {
        mNetworkLockedRegistrants.remove(h);
    }

    /**
     * Notifies handler of any transition into State.isPinLocked()
     */
    public void registerForLocked(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        mPinLockedRegistrants.add(r);

        if (getState().isPinLocked()) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForLocked(Handler h) {
        mPinLockedRegistrants.remove(h);
    }


    /**
     * Supply the ICC PIN to the ICC
     *
     * When the operation is complete, onComplete will be sent to its
     * Handler.
     *
     * onComplete.obj will be an AsyncResult
     *
     * ((AsyncResult)onComplete.obj).exception == null on success
     * ((AsyncResult)onComplete.obj).exception != null on fail
     *
     * If the supplied PIN is incorrect:
     * ((AsyncResult)onComplete.obj).exception != null
     * && ((AsyncResult)onComplete.obj).exception
     *       instanceof com.android.internal.telephony.gsm.CommandException)
     * && ((CommandException)(((AsyncResult)onComplete.obj).exception))
     *          .getCommandError() == CommandException.Error.PASSWORD_INCORRECT
     *
     *
     */

    public void supplyPin (String pin, Message onComplete) {
        mPhone.mCM.supplyIccPin(pin, mHandler.obtainMessage(EVENT_PINPUK_DONE, onComplete));
    }

    public void supplyPuk (String puk, String newPin, Message onComplete) {
        mPhone.mCM.supplyIccPuk(puk, newPin,
                mHandler.obtainMessage(EVENT_PINPUK_DONE, onComplete));
    }

    public void supplyPin2 (String pin2, Message onComplete) {
        mPhone.mCM.supplyIccPin2(pin2,
                mHandler.obtainMessage(EVENT_PINPUK_DONE, onComplete));
    }

    public void supplyPuk2 (String puk2, String newPin2, Message onComplete) {
        mPhone.mCM.supplyIccPuk2(puk2, newPin2,
                mHandler.obtainMessage(EVENT_PINPUK_DONE, onComplete));
    }

    public void supplyNetworkDepersonalization (String pin, Message onComplete) {
        if(mDbg) log("Network Despersonalization: " + pin);
        mPhone.mCM.supplyNetworkDepersonalization(pin,
                mHandler.obtainMessage(EVENT_PINPUK_DONE, onComplete));
    }

    /**
     * Check whether ICC pin lock is enabled
     * This is a sync call which returns the cached pin enabled state
     *
     * @return true for ICC locked enabled
     *         false for ICC locked disabled
     */
    public boolean getIccLockEnabled() {
        return mIccPinLocked;
     }

    /**
     * Set ICC pin lock
     */
    public void setIccLockEnabled(boolean enabled) {
        mIccPinLocked = enabled;
     }
    /*
     * add for sim card lock
     */
    public boolean getIccSimEnabled(){
        return mIccSimEnabled;
    }
    public void setIccSimEnabled(boolean enabled){
        mIccSimEnabled = enabled;
    }
    /*
     * add for network lock
     */
    public boolean getIccNetworkEnabled(){
        return mIccNetworkEnabled;
    }
    public void setIccNetworkEnabled(boolean enabled){
        mIccNetworkEnabled = enabled;
    }
    /**
     * Check whether ICC fdn (fixed dialing number) is enabled
     * This is a sync call which returns the cached pin enabled state
     *
     * @return true for ICC fdn enabled
     *         false for ICC fdn disabled
     */
     public boolean getIccFdnEnabled() {
        return mIccFdnEnabled;
     }

     /**
      * Set the ICC pin lock enabled or disabled
      * When the operation is complete, onComplete will be sent to its handler
      *
      * @param enabled "true" for locked "false" for unlocked.
      * @param password needed to change the ICC pin state, aka. Pin1
      * @param onComplete
      *        onComplete.obj will be an AsyncResult
      *        ((AsyncResult)onComplete.obj).exception == null on success
      *        ((AsyncResult)onComplete.obj).exception != null on fail
      */
     public void setIccLockEnabled (boolean enabled,
             String password, Message onComplete) {
         int serviceClassX;
         serviceClassX = CommandsInterface.SERVICE_CLASS_VOICE +
                 CommandsInterface.SERVICE_CLASS_DATA +
                 CommandsInterface.SERVICE_CLASS_FAX;

         mDesiredPinLocked = enabled;

         mPhone.mCM.setFacilityLock(CommandsInterface.CB_FACILITY_BA_SIM,
                 enabled, password, serviceClassX,
                 mHandler.obtainMessage(EVENT_CHANGE_FACILITY_LOCK_DONE, onComplete));
     }

     /**
      * Set the ICC fdn enabled or disabled
      * When the operation is complete, onComplete will be sent to its handler
      *
      * @param enabled "true" for locked "false" for unlocked.
      * @param password needed to change the ICC fdn enable, aka Pin2
      * @param onComplete
      *        onComplete.obj will be an AsyncResult
      *        ((AsyncResult)onComplete.obj).exception == null on success
      *        ((AsyncResult)onComplete.obj).exception != null on fail
      */
     public void setIccFdnEnabled (boolean enabled,
             String password, Message onComplete) {
         int serviceClassX;
         serviceClassX = CommandsInterface.SERVICE_CLASS_VOICE +
                 CommandsInterface.SERVICE_CLASS_DATA +
                 CommandsInterface.SERVICE_CLASS_FAX +
                 CommandsInterface.SERVICE_CLASS_SMS;

         mDesiredFdnEnabled = enabled;

         mPhone.mCM.setFacilityLock(CommandsInterface.CB_FACILITY_BA_FD,
                 enabled, password, serviceClassX,
                 mHandler.obtainMessage(EVENT_CHANGE_FACILITY_FDN_DONE, onComplete));
     }

     public void setSimCardLockEnabled (boolean enabled,
             String password, Message onComplete) {
         int serviceClassX = 0;
         mDesiredSimLockEnabled = enabled;
         mPhone.mCM.setFacilityLock(CommandsInterface.CB_FACILITY_BA_PS,
                 enabled, password, serviceClassX,
                 mHandler.obtainMessage(EVENT_CHANGE_FACILITY_SIM_DONE, onComplete));
     }
     public void setNetworkLockEnabled (boolean enabled,
             String password, Message onComplete) {
         int serviceClassX = 0;
         mDesiredNetworkEnabled = enabled;
         mPhone.mCM.setFacilityLock(CommandsInterface.CB_FACILITY_BA_PN,
                 enabled, password, serviceClassX,
                 mHandler.obtainMessage(EVENT_CHANGE_FACILITY_NETWORK_DONE, onComplete));
     }
     /**
      * Change the ICC password used in ICC pin lock
      * When the operation is complete, onComplete will be sent to its handler
      *
      * @param oldPassword is the old password
      * @param newPassword is the new password
      * @param onComplete
      *        onComplete.obj will be an AsyncResult
      *        ((AsyncResult)onComplete.obj).exception == null on success
      *        ((AsyncResult)onComplete.obj).exception != null on fail
      */
     public void changeIccLockPassword(String oldPassword, String newPassword,
             Message onComplete) {
         if(mDbg) log("Change Pin1 old: " + oldPassword + " new: " + newPassword);
         mPhone.mCM.changeIccPin(oldPassword, newPassword,
                 mHandler.obtainMessage(EVENT_CHANGE_ICC_PASSWORD_DONE, onComplete));

     }

     /**
      * Change the ICC password used in ICC fdn enable
      * When the operation is complete, onComplete will be sent to its handler
      *
      * @param oldPassword is the old password
      * @param newPassword is the new password
      * @param onComplete
      *        onComplete.obj will be an AsyncResult
      *        ((AsyncResult)onComplete.obj).exception == null on success
      *        ((AsyncResult)onComplete.obj).exception != null on fail
      */
     public void changeIccFdnPassword(String oldPassword, String newPassword,
             Message onComplete) {
         if(mDbg) log("Change Pin2 old: " + oldPassword + " new: " + newPassword);
         mPhone.mCM.changeIccPin2(oldPassword, newPassword,
                 mHandler.obtainMessage(EVENT_CHANGE_ICC_PASSWORD_DONE, onComplete));

     }


    /**
     * Returns service provider name stored in ICC card.
     * If there is no service provider name associated or the record is not
     * yet available, null will be returned <p>
     *
     * Please use this value when display Service Provider Name in idle mode <p>
     *
     * Usage of this provider name in the UI is a common carrier requirement.
     *
     * Also available via Android property "gsm.sim.operator.alpha"
     *
     * @return Service Provider Name stored in ICC card
     *         null if no service provider name associated or the record is not
     *         yet available
     *
     */
    public abstract String getServiceProviderName();

    protected void updateStateProperty() {
        mPhone.setSystemProperty(TelephonyProperties.PROPERTY_SIM_STATE, getState().toString());
    }

    private void getIccCardStatusDone(AsyncResult ar) {
        if (ar.exception != null) {
            Log.e(mLogTag,"Error getting ICC status. "
                    + "RIL_REQUEST_GET_ICC_STATUS should "
                    + "never return an error", ar.exception);
            return;
        }
        handleIccCardStatus((IccCardStatus) ar.result);
    }

    private void handleIccCardStatus(IccCardStatus newCardStatus) {
        boolean transitionedIntoPinLocked;
        boolean transitionedIntoAbsent;
        boolean transitionedIntoNetworkLocked;
        boolean transitionedIntoIccBlocked;
        boolean transitionedIntoSimBlocked;
        boolean transitionedIntoCardPresent;
        boolean transitionedIntoIccReady;

        State oldState, newState;
        oldState = mState;
        mIccCardStatus = newCardStatus;
        Log.d(mLogTag, mPhone.getPhoneName()
                + " phone= "
                + mPhone.getPhoneId()
                + " mIccCardStatus="
                + (mIccCardStatus.getCardState() == CardState.CARDSTATE_ABSENT ? "absent"
                        : (mIccCardStatus.getCardState() == CardState.CARDSTATE_ERROR ? "error"
                                : "present")));
        newState = getIccCardState();
        mState = newState;

        PhoneFactory.autoSetDefaultPhoneId(true, mPhone.getPhoneId());

        updateStateProperty();

        transitionedIntoPinLocked = (
                 ( newState == State.PIN_REQUIRED)
              || ( newState == State.PUK_REQUIRED));
        transitionedIntoAbsent = (oldState != State.ABSENT && newState == State.ABSENT);
        transitionedIntoNetworkLocked = (oldState != State.NETWORK_LOCKED
                && newState == State.NETWORK_LOCKED);
        transitionedIntoIccBlocked = (oldState != State.BLOCKED && newState == State.BLOCKED);
        transitionedIntoSimBlocked = (oldState != State.SIM_LOCKED && newState == State.SIM_LOCKED);
        transitionedIntoCardPresent =  !transitionedIntoAbsent;
        transitionedIntoIccReady = (oldState != State.READY && newState == State.READY);
        if (transitionedIntoPinLocked) {
            if(mDbg) log("Notify SIM pin or puk locked.");
            mPinLockedRegistrants.notifyRegistrants();
            broadcastIccStateChangedIntent(INTENT_VALUE_ICC_LOCKED,
                    (newState == State.PIN_REQUIRED) ?
                            INTENT_VALUE_LOCKED_ON_PIN : INTENT_VALUE_LOCKED_ON_PUK);
        } else if (transitionedIntoAbsent) {
            if(mDbg) log("Notify SIM missing.");
            mAbsentRegistrants.notifyRegistrants();
            broadcastIccStateChangedIntent(INTENT_VALUE_ICC_ABSENT, null);
        } else if (transitionedIntoNetworkLocked) {
            if(mDbg) log("Notify SIM network locked.");
            mNetworkLockedRegistrants.notifyRegistrants();
            broadcastIccStateChangedIntent(INTENT_VALUE_ICC_LOCKED,
                  INTENT_VALUE_LOCKED_NETWORK);
        } else if (transitionedIntoIccBlocked) {
            if(mDbg) log("Notify ICC blocked.");
            broadcastIccStateChangedIntent(INTENT_VALUE_ICC_BLOCKED, null);
        } else if (transitionedIntoSimBlocked){
            if(mDbg) log("Notify SIM locked.");
            broadcastIccStateChangedIntent(INTENT_VALUE_ICC_LOCKED, INTENT_VALUE_LOCKED_SIM);
        } else if(transitionedIntoIccReady){
            if(mDbg) log("Notify SIM ready.");
            broadcastGetIccStatusDoneIntent();
        } else if(transitionedIntoCardPresent){
            if(mDbg) log("Notify SIM present.");
            broadcastIccCardPresentIntent();
        }
}

    /**
     * a interface ,to indicate which type of your sim card .if current card
     * app_type is USIM card or TD card,return is true else false
     * @return
     */
    public boolean isUsimCard() {
        if (mIccCardStatus == null){
            Log.d(mLogTag,
                    "isUsimCard: " + mPhone.getPhoneName() + " phone= " + mPhone.getPhoneId()
                            + "IccCardstatus is null,please check your card");
            return false;
        }
        for (int i = 0; i < mIccCardStatus.getNumApplications(); i++) {
            IccCardApplication app = mIccCardStatus.getApplication(i);
            if (app != null && (IccCardApplication.AppType.APPTYPE_USIM == app.app_type)) {
                Log.d(mLogTag,
                        "isUsimCard: " + mPhone.getPhoneName() + " phone= " + mPhone.getPhoneId()
                                + "mIccCardStatus="
                                + mIccCardStatus.getApplication(i).app_type.toString());
                return true;
            }
        }
        return false;

    }
    /**
     * Interperate EVENT_QUERY_FACILITY_LOCK_DONE
     * @param ar is asyncResult of Query_Facility_Locked
     */
    private void onQueryFdnEnabled(AsyncResult ar) {
        if(ar.exception != null) {
            if(mDbg) log("Error in querying facility lock:" + ar.exception);
            return;
        }

        int[] ints = (int[])ar.result;
        if(ints.length != 0) {
            mIccFdnEnabled = (0!=ints[0]);
            if(mDbg) log("Query facility lock : "  + mIccFdnEnabled);
        } else {
            Log.e(mLogTag, "[IccCard] Bogus facility lock response");
        }
    }

    private void onQuerySimEnabled(AsyncResult ar) {
        if(ar.exception != null) {
            if(mDbg) log("Error in querying sim lock:" + ar.exception);
            return;
        }

        int[] ints = (int[])ar.result;
        if(ints.length != 0) {
            mIccSimEnabled = (0!=ints[0]);
            if(mDbg) log("Query sim lock : "  + mIccSimEnabled);
        } else {
            Log.e(mLogTag, "[IccCard] Bogus sim lock response");
        }
    }
    private void onQueryNetworkEnabled(AsyncResult ar) {
        if(ar.exception != null) {
            if(mDbg) log("Error in querying Network lock:" + ar.exception);
            return;
        }

        int[] ints = (int[])ar.result;
        if(ints.length != 0) {
            mIccNetworkEnabled = (0!=ints[0]);
            if(mDbg) log("Query Network lock : "  + mIccNetworkEnabled);
        } else {
            Log.e(mLogTag, "[IccCard] Bogus network lock response");
        }
    }
    /**
     * Interperate EVENT_QUERY_FACILITY_LOCK_DONE
     * @param ar is asyncResult of Query_Facility_Locked
     */
    private void onQueryFacilityLock(AsyncResult ar) {
        if(ar.exception != null) {
            if (mDbg) log("Error in querying facility lock:" + ar.exception);
            return;
        }

        int[] ints = (int[])ar.result;
        if(ints.length != 0) {
            mIccPinLocked = (0!=ints[0]);
            if(mDbg) log("Query facility lock : "  + mIccPinLocked);
        } else {
            Log.e(mLogTag, "[IccCard] Bogus facility lock response");
        }
    }

    public void broadcastIccStateChangedIntent(String value, String reason) {
        Intent intent = new Intent(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra(Phone.PHONE_NAME_KEY, mPhone.getPhoneName());
        intent.putExtra(INTENT_KEY_ICC_STATE, value);
        intent.putExtra(INTENT_KEY_PHONE_ID, mPhone.getPhoneId());
        intent.putExtra(INTENT_KEY_LOCKED_REASON, reason);
        if(mDbg) log("Broadcasting intent ACTION_SIM_STATE_CHANGED " +  value
                + " reason " + reason + " phoneid " + mPhone.getPhoneId());
        ActivityManagerNative.broadcastStickyIntent(intent, READ_PHONE_STATE);

        String simAction = TelephonyIntents.ACTION_SIM_STATE_CHANGED + mPhone.getPhoneId();
        Intent simintent = new Intent(simAction);
        simintent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        simintent.putExtra(Phone.PHONE_NAME_KEY, mPhone.getPhoneName());
        simintent.putExtra(INTENT_KEY_ICC_STATE, value);
        simintent.putExtra(INTENT_KEY_PHONE_ID, mPhone.getPhoneId());
        simintent.putExtra(INTENT_KEY_LOCKED_REASON, reason);
        if (mDbg)
            log("Broadcasting intent " + simAction + "  " + value + " reason " + reason
                    + " phoneid " + mPhone.getPhoneId());
        ActivityManagerNative.broadcastStickyIntent(simintent, READ_PHONE_STATE);
    }

    public void broadcastGetIccStatusDoneIntent() {
        Intent intent = new Intent(PhoneFactory.getAction(TelephonyIntents.ACTION_GET_ICC_STATUS_DONE, mPhone.getPhoneId()));
     
        if(mDbg) log("Broadcasting intent ACTION_GET_ICC_STATUS_DONE , phoneid is " + mPhone.getPhoneId());

        ActivityManagerNative.broadcastStickyIntent(intent, READ_PHONE_STATE);
    }
    public void broadcastIccCardPresentIntent() {
        Intent intent = new Intent(TelephonyIntents.SIM_CARD_PRESENT);
        intent.putExtra(INTENT_KEY_PHONE_ID, mPhone.getPhoneId());
        if(mDbg) log("Broadcasting intent SIM_CARD_PRESENT , phoneid is " + mPhone.getPhoneId());
        ActivityManagerNative.broadcastStickyIntent(intent, READ_PHONE_STATE);
    }
    public void queryFacilityFdnDone() {
        Log.e(mLogTag, "IccCard  queryFacilityFdnDone");
        int serviceClassX = CommandsInterface.SERVICE_CLASS_VOICE +
                            CommandsInterface.SERVICE_CLASS_DATA +
                            CommandsInterface.SERVICE_CLASS_FAX;

        Message msg = mHandler.obtainMessage(EVENT_QUERY_FACILITY_FDN_DONE);
        mPhone.mCM.queryFacilityLock (
                            CommandsInterface.CB_FACILITY_BA_FD, "", serviceClassX,
                            msg);

        Log.e(mLogTag," queryFacilityFdnDone sendBroadcast FDN_STATE_CHANGED");
        Intent intent = new Intent("android.intent.action.FDN_STATE_CHANGED"+ mPhone.getPhoneId());
        intent.putExtra(INTENT_KEY_PHONE_ID, mPhone.getPhoneId());
        intent.putExtra(INTENT_KEY_FDN_STAUS, getIccFdnEnabled());
        intent.putExtra(INTENT_KEY_FDN_SIM_REFRESH, INTENT_VALUE_ICC_REFRESH);
        mPhone.getContext().sendBroadcast(intent);
        broadcastIccStateChangedIntent(INTENT_VALUE_ICC_REFRESH, null);
    }

    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg){
            AsyncResult ar;
            int serviceClassX;

            serviceClassX = CommandsInterface.SERVICE_CLASS_VOICE +
                            CommandsInterface.SERVICE_CLASS_DATA +
                            CommandsInterface.SERVICE_CLASS_FAX;

            switch (msg.what) {
                case EVENT_RADIO_OFF_OR_NOT_AVAILABLE:
                    //mState = null;
                    // updateStateProperty();
                    //broadcastIccStateChangedIntent(INTENT_VALUE_ICC_NOT_READY, null);
                    if(Config.DEBUG)Log.d(mLogTag, "EVENT_RADIO_OFF_OR_NOT_AVAILABLE,update property");
                    mPhone.mCM.getIccCardStatus(obtainMessage(EVENT_GET_ICC_STATUS_DONE));
                    break;
                case EVENT_ICC_READY:
                    if (Config.DEBUG)
                        Log.d(mLogTag, "EVENT_ICC_READY,get property");
                    //TODO: put facility read in SIM_READY now, maybe in REG_NW
                    mPhone.mCM.getIccCardStatus(obtainMessage(EVENT_GET_ICC_STATUS_DONE));
                    mPhone.mCM.queryFacilityLock (
                            CommandsInterface.CB_FACILITY_BA_SIM, "", serviceClassX,
                            obtainMessage(EVENT_QUERY_FACILITY_LOCK_DONE));
                    mPhone.mCM.queryFacilityLock (
                            CommandsInterface.CB_FACILITY_BA_FD, "", serviceClassX,
                            obtainMessage(EVENT_QUERY_FACILITY_FDN_DONE));
                    mPhone.mCM.queryFacilityLock (
                            CommandsInterface.CB_FACILITY_BA_PS, "", 0,
                            obtainMessage(EVENT_QUERY_FACILITY_SIM_DONE));
                    mPhone.mCM.queryFacilityLock (
                            CommandsInterface.CB_FACILITY_BA_PN, "", 0,
                            obtainMessage(EVENT_QUERY_FACILITY_NETWORK_DONE));
                    break;
                case EVENT_ICC_LOCKED_OR_ABSENT:
                    if (Config.DEBUG)
                        Log.d(mLogTag, "EVENT_ICC_LOCKED_OR_ABSENT,get property");
                    mPhone.mCM.getIccCardStatus(obtainMessage(EVENT_GET_ICC_STATUS_DONE));
                    mPhone.mCM.queryFacilityLock (
                            CommandsInterface.CB_FACILITY_BA_SIM, "", serviceClassX,
                            obtainMessage(EVENT_QUERY_FACILITY_LOCK_DONE));
                    mPhone.mCM.queryFacilityLock (
                            CommandsInterface.CB_FACILITY_BA_PS, "", 0,
                            obtainMessage(EVENT_QUERY_FACILITY_SIM_DONE));
                    mPhone.mCM.queryFacilityLock (
                            CommandsInterface.CB_FACILITY_BA_PN, "", 0,
                            obtainMessage(EVENT_QUERY_FACILITY_NETWORK_DONE));
                    break;
                case EVENT_GET_ICC_STATUS_DONE:
                    ar = (AsyncResult)msg.obj;

                    getIccCardStatusDone(ar);
                    break;
                case EVENT_PINPUK_DONE:
                    // a PIN/PUK/PIN2/PUK2/Network Personalization
                    // request has completed. ar.userObj is the response Message
                    // Repoll before returning
                    ar = (AsyncResult)msg.obj;
                    // TODO should abstract these exceptions
                    AsyncResult.forMessage(((Message)ar.userObj)).exception
                                                        = ar.exception;
                    mPhone.mCM.getIccCardStatus(
                        obtainMessage(EVENT_REPOLL_STATUS_DONE, ar.userObj));
                    break;
                case EVENT_REPOLL_STATUS_DONE:
                    // Finished repolling status after PIN operation
                    // ar.userObj is the response messaeg
                    // ar.userObj.obj is already an AsyncResult with an
                    // appropriate exception filled in if applicable

                    ar = (AsyncResult)msg.obj;
                    getIccCardStatusDone(ar);
                    ((Message)ar.userObj).sendToTarget();
                    break;
                case EVENT_QUERY_FACILITY_LOCK_DONE:
                    ar = (AsyncResult)msg.obj;
                    onQueryFacilityLock(ar);
                    break;
                case EVENT_QUERY_FACILITY_FDN_DONE:
                    ar = (AsyncResult)msg.obj;
                    onQueryFdnEnabled(ar);
                    break;
                case EVENT_QUERY_FACILITY_SIM_DONE:
                    ar = (AsyncResult)msg.obj;
                    onQuerySimEnabled(ar);
                    break;
                case EVENT_QUERY_FACILITY_NETWORK_DONE:
                    ar = (AsyncResult)msg.obj;
                    onQueryNetworkEnabled(ar);
                    break;
                case EVENT_CHANGE_FACILITY_LOCK_DONE:
                    ar = (AsyncResult)msg.obj;
                    if (ar.exception == null) {
                        mIccPinLocked = mDesiredPinLocked;
                        if (mDbg) log( "EVENT_CHANGE_FACILITY_LOCK_DONE: " +
                                "mIccPinLocked= " + mIccPinLocked);
                    } else {
                        Log.e(mLogTag, "Error change facility lock with exception "
                            + ar.exception);
                    }
                    AsyncResult.forMessage(((Message)ar.userObj)).exception
                                                        = ar.exception;
                    ((Message)ar.userObj).sendToTarget();
                    break;
                case EVENT_CHANGE_FACILITY_FDN_DONE:
                    ar = (AsyncResult)msg.obj;

                    if (ar.exception == null) {
                        mIccFdnEnabled = mDesiredFdnEnabled;
                        if (mDbg) log("EVENT_CHANGE_FACILITY_FDN_DONE: " +
                                "mIccFdnEnabled=" + mIccFdnEnabled);
                    } else {
                        Log.e(mLogTag, "Error change facility fdn with exception "
                                + ar.exception);
                    }
                    AsyncResult.forMessage(((Message)ar.userObj)).exception
                                                        = ar.exception;
                    ((Message)ar.userObj).sendToTarget();
                    break;
                case EVENT_CHANGE_FACILITY_NETWORK_DONE:
                    ar = (AsyncResult)msg.obj;

                    if (ar.exception == null) {
                        mIccNetworkEnabled = mDesiredNetworkEnabled;
                        if (mDbg) log("EVENT_CHANGE_FACILITY_FDN_DONE: " +
                                "mIccNetworkEnabled=" + mIccNetworkEnabled);
                    } else {
                        Log.e(mLogTag, "Error change facility network with exception "
                                + ar.exception);
                    }
                    AsyncResult.forMessage(((Message)ar.userObj)).exception
                                                        = ar.exception;
                    ((Message)ar.userObj).sendToTarget();
                    break;
                case EVENT_CHANGE_FACILITY_SIM_DONE:
                    AsyncResult arc = (AsyncResult)msg.obj;
                    log( "EVENT_QUERY_FACILITY_SIM_DONE arc " + arc);
                    if (arc.exception == null) {
                        mIccSimEnabled = mDesiredSimLockEnabled;
                        if (mDbg) log("EVENT_CHANGE_FACILITY_SIM_DONE: " +
                                "mIccSimEnabled=" + mIccSimEnabled);
                    } else {
                        Log.e(mLogTag, "Error change facility sim lock with exception "
                                + arc.exception);
                    }
                    AsyncResult.forMessage(((Message)arc.userObj)).exception
                                                        = arc.exception;
                    ((Message)arc.userObj).sendToTarget();
                    break;
                case EVENT_CHANGE_ICC_PASSWORD_DONE:
                    ar = (AsyncResult)msg.obj;
                    if(ar.exception != null) {
                        Log.e(mLogTag, "Error in change sim password with exception"
                            + ar.exception);
                    }
                    AsyncResult.forMessage(((Message)ar.userObj)).exception
                                                        = ar.exception;
                    ((Message)ar.userObj).sendToTarget();
                    break;
                case EVENT_ICC_STATUS_CHANGED:
                    if (Config.DEBUG)
                        Log.d(mLogTag, "Received EVENT_ICC_STATUS_CHANGED, calling getIccCardStatus");
                    mPhone.mCM.getIccCardStatus(obtainMessage(EVENT_GET_ICC_STATUS_DONE));
                    break;
                default:
                    Log.e(mLogTag, "[IccCard] Unknown Event " + msg.what);
            }
        }
    };

    public State getIccCardState() {
        if (Config.DEBUG)
            Log.e(mLogTag, "getIccCardState where you called:");
        if (mIccCardStatus == null) {
            Log.e(mLogTag, "[IccCard] IccCardStatus is null");
            return IccCard.State.ABSENT;
        }

        // this is common for all radio technologies
        if (!mIccCardStatus.getCardState().isCardPresent()) {
            return IccCard.State.ABSENT;
        }

        RadioState currentRadioState = mPhone.mCM.getRadioState();
        Log.e(mLogTag,
                "1 currentRadioState="
                        + (currentRadioState == RadioState.SIM_NOT_READY ? "SIM_NOT_READY"
                                : (currentRadioState == RadioState.RADIO_OFF ? "RADIO_OFF"
                                        : (currentRadioState == RadioState.RADIO_UNAVAILABLE ? "RADIO_UNAVAILABLE"
                                                : "other"))));
        if (currentRadioState == RadioState.RADIO_OFF) {
            // if mIccCardStatus is CardPresent, it means SIM card is exist.
            // So we return ready
            return IccCard.State.READY;
        }
        // check radio technology
        if( /*currentRadioState == RadioState.RADIO_OFF         ||
            currentRadioState == RadioState.RADIO_UNAVAILABLE ||*/
            currentRadioState == RadioState.SIM_NOT_READY     ||
            currentRadioState == RadioState.RUIM_NOT_READY    ||
            currentRadioState == RadioState.NV_NOT_READY      ||
            currentRadioState == RadioState.NV_READY) {
            return IccCard.State.NOT_READY;
        }
        Log.e(mLogTag, "2 currentRadioState="
                + (currentRadioState == RadioState.SIM_LOCKED_OR_ABSENT ? "SIM_LOCKED_OR_ABSENT"
                        : (currentRadioState == RadioState.SIM_READY ? "SIM_READY" : "other")));
        if( currentRadioState == RadioState.SIM_LOCKED_OR_ABSENT  ||
            currentRadioState == RadioState.SIM_READY             ||
            currentRadioState == RadioState.RUIM_LOCKED_OR_ABSENT ||
            currentRadioState == RadioState.RUIM_READY) {
            Log.e(mLogTag, " start ");
            int index;

            // check for CDMA radio technology
            if (currentRadioState == RadioState.RUIM_LOCKED_OR_ABSENT ||
                currentRadioState == RadioState.RUIM_READY) {
                index = mIccCardStatus.getCdmaSubscriptionAppIndex();
            }
            else {
                index = mIccCardStatus.getGsmUmtsSubscriptionAppIndex();
            }

            IccCardApplication app = mIccCardStatus.getApplication(index);

            if (app == null) {
                Log.e(mLogTag, "[IccCard] Subscription Application in not present");
                return IccCard.State.ABSENT;
            }
            // check if PIN required
            if (app.app_state.isPinRequired()) {
                return IccCard.State.PIN_REQUIRED;
            }
            if (app.app_state.isPukRequired()) {
                return IccCard.State.PUK_REQUIRED;
            }
            if (app.app_state.isIccBlocked()) {
                return IccCard.State.BLOCKED;
            }
            if (app.app_state.isSubscriptionPersoEnabled()) {
                if(app.perso_substate.isSimBlocked()){
                    return IccCard.State.SIM_LOCKED;
                }else if(app.perso_substate.isNetworkBlocked()){
                    return IccCard.State.NETWORK_LOCKED;
                }
            }
            if (app.app_state.isAppReady()) {
                return IccCard.State.READY;
            }
            if (app.app_state.isAppNotReady()) {
                return IccCard.State.NOT_READY;
            }
            return IccCard.State.NOT_READY;
        }
        Log.e(mLogTag,"other radio state");
        return IccCard.State.ABSENT;
    }


    public boolean isApplicationOnIcc(IccCardApplication.AppType type) {
        if (mIccCardStatus == null) return false;

        for (int i = 0 ; i < mIccCardStatus.getNumApplications(); i++) {
            IccCardApplication app = mIccCardStatus.getApplication(i);
            if (app != null && app.app_type == type) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true if a ICC card is present
     */
    public boolean hasIccCard() {
        boolean isIccPresent;

        Log.i("hasIccCard",mPhone.getPhoneName()+" phone= "+mPhone.getPhoneId());
        if (mIccCardStatus==null) {
            return false;
        }
        Log.i("hasIccCard",mPhone.getPhoneName()+" phone= "+mPhone.getPhoneId()+"   card state="+mIccCardStatus.getCardState().toString());
        if (mPhone.getPhoneName().equals("GSM") || mPhone.getPhoneName().equals("TD")) {
            return mIccCardStatus.getCardState().isCardPresent();
        }else {
            // TODO: Make work with a CDMA device with a RUIM card.
            return false;
        }
    }

    /**
     * @return true if ICC card is on
     */
    public boolean isIccCardOn() {
        return mPhone.mCM.getRadioState() != RadioState.RADIO_UNAVAILABLE;
    }

    private void log(String msg) {
        Log.d(mLogTag, "[IccCard] " + msg);
    }
}
