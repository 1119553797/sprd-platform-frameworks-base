/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.internal.policy.impl;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import static android.os.BatteryManager.BATTERY_STATUS_FULL;
import static android.os.BatteryManager.BATTERY_STATUS_UNKNOWN;
import static android.os.BatteryManager.BATTERY_HEALTH_UNKNOWN;
import static android.os.BatteryManager.EXTRA_STATUS;
import static android.os.BatteryManager.EXTRA_PLUGGED;
import static android.os.BatteryManager.EXTRA_LEVEL;
import static android.os.BatteryManager.EXTRA_HEALTH;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.provider.Telephony;
import static android.provider.Telephony.Intents.EXTRA_PLMN;
import static android.provider.Telephony.Intents.EXTRA_SHOW_PLMN;
import static android.provider.Telephony.Intents.EXTRA_SHOW_SPN;
import static android.provider.Telephony.Intents.EXTRA_SPN;
import static android.provider.Telephony.Intents.SPN_STRINGS_UPDATED_ACTION;

import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.TelephonyIntents;

import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.R;
import com.google.android.collect.Lists;

import java.util.ArrayList;

/**
 * Watches for updates that may be interesting to the keyguard, and provides
 * the up to date information as well as a registration for callbacks that care
 * to be updated.
 *
 * Note: under time crunch, this has been extended to include some stuff that
 * doesn't really belong here.  see {@link #handleBatteryUpdate} where it shutdowns
 * the device, and {@link #getFailedAttempts()}, {@link #reportFailedAttempt()}
 * and {@link #clearFailedAttempts()}.  Maybe we should rename this 'KeyguardContext'...
 */
public class KeyguardUpdateMonitor {

    static private final String TAG = "KeyguardUpdateMonitor";
    static private final boolean DEBUG = false;

    /* package */ static final int LOW_BATTERY_THRESHOLD = 20;

    private final Context mContext;

    //add DSDS start
    //private IccCard.State mSimState = IccCard.State.READY;
    private IccCard.State[] mSimState = {IccCard.State.READY};
    //add DSDS end

    private boolean mKeyguardBypassEnabled;

    private boolean mDeviceProvisioned;

    private BatteryStatus mBatteryStatus;

    //add DSDS start
    protected static final String EXTRA_PHONE_ID = "phone_id";
    private int mPhoneId;
    private CharSequence[] mTelephonyPlmn;
    private CharSequence[] mTelephonySpn;
    //add DSDS end
//    private CharSequence mTelephonyPlmn;
//    private CharSequence mTelephonySpn;

    private int mUnreadMessageCount;

    private int mFailedAttempts = 0;
    private int mFailedBiometricUnlockAttempts = 0;
    private static final int FAILED_BIOMETRIC_UNLOCK_ATTEMPTS_BEFORE_BACKUP = 3;

    private boolean mClockVisible;

    private Handler mHandler;

    private ArrayList<InfoCallback> mInfoCallbacks = Lists.newArrayList();
    private ArrayList<SimStateCallback> mSimStateCallbacks = Lists.newArrayList();
    private ContentObserver mContentObserver;
    private int mRingMode;
    private int mPhoneState;

    //add DSDS missed call extra data key start
    public static String EXTRA_MISSED_CALL_KEY ="EXTRA_MISSED_CALL_KEY";
    //add DSDS missed call extra data key end

    // messages for the handler
    private static final int MSG_TIME_UPDATE = 301;
    private static final int MSG_BATTERY_UPDATE = 302;
    private static final int MSG_CARRIER_INFO_UPDATE = 303;
    private static final int MSG_SIM_STATE_CHANGE = 304;
    private static final int MSG_RINGER_MODE_CHANGED = 305;
    private static final int MSG_PHONE_STATE_CHANGED = 306;
    private static final int MSG_CLOCK_VISIBILITY_CHANGED = 307;
    private static final int MSG_DEVICE_PROVISIONED = 308;
    protected static final int MSG_DPM_STATE_CHANGED = 309;
    protected static final int MSG_USER_CHANGED = 310;
    //add newfeature for DSDS start
    private static final int MSG_UNREAD_MESSAGE_COUNT = 320;
    private static final int MSG_DELETE_UNREAD_MESSAGE_COUNT = 321;
    private final static String ACTION_UNREAD_MESSAGE_COUNT = "com.android.mms.NEW_MASSAGE_RECEVICE_COUNT";
    private final static String EXTRA_MESSAGE_COUNT = "newMessagecount";
    private final static String ACTION_DELETE_UNREAD_MESSAGE_COUNT="com.android.mms.DELETE_UNREAD_MESSAGE_COUNT";
    //add newfeature for DSDS end

    //add missed call start
    private static final int MSG_MISSED_CALL_COUNT = 322;
    private static final int MSG_MISSED_CALL_CANCEL = 323;
    public final static String ACTION_MISSED_CALL_COUNT = "com.android.call.MISSED_CALL";
    public final static String ACTION_MISSED_CALL_CANCEL = "com.android.call.MISSED_CALL_CANCEL";
    public int mMissedCallCount = 0;
    //add missed call end

    protected static final boolean DEBUG_SIM_STATES = DEBUG || false;

    /**
     * When we receive a
     * {@link com.android.internal.telephony.TelephonyIntents#ACTION_SIM_STATE_CHANGED} broadcast,
     * and then pass a result via our handler to {@link KeyguardUpdateMonitor#handleSimStateChange},
     * we need a single object to pass to the handler.  This class helps decode
     * the intent and provide a {@link SimCard.State} result.
     */
    private static class SimArgs {
        public final IccCard.State[] simState;
        //add DSDS start
        public final int subscription;
//        SimArgs(IccCard.State state) {
//            simState = state;
//        }
        SimArgs(IccCard.State[] state,int sub){
        	simState = state;
        	subscription=sub;
        }
        //add DSDS end
        static SimArgs fromIntent(Intent intent) {
            IccCard.State[] state=new IccCard.State[TelephonyManager.getPhoneCount()];
            int simSubscription=intent.getIntExtra(EXTRA_PHONE_ID,0);
            if (!TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {
                throw new IllegalArgumentException("only handles intent ACTION_SIM_STATE_CHANGED");
            }
            String stateExtra = intent.getStringExtra(IccCard.INTENT_KEY_ICC_STATE);
            if (IccCard.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
                final String absentReason = intent
                    .getStringExtra(IccCard.INTENT_KEY_LOCKED_REASON);

                if (IccCard.INTENT_VALUE_ABSENT_ON_PERM_DISABLED.equals(
                        absentReason)) {
                    state[simSubscription] = IccCard.State.PERM_DISABLED;
                } else {
                    state[simSubscription] = IccCard.State.ABSENT;
                }
            } else if (IccCard.INTENT_VALUE_ICC_READY.equals(stateExtra)) {
                state[simSubscription] = IccCard.State.READY;
            } else if (IccCard.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
                final String lockedReason = intent
                        .getStringExtra(IccCard.INTENT_KEY_LOCKED_REASON);
                if (IccCard.INTENT_VALUE_LOCKED_ON_PIN.equals(lockedReason)) {
                    state[simSubscription] = IccCard.State.PIN_REQUIRED;
                } else if (IccCard.INTENT_VALUE_LOCKED_ON_PUK.equals(lockedReason)) {
                    state[simSubscription] = IccCard.State.PUK_REQUIRED;
                }  else if (IccCard.INTENT_VALUE_LOCKED_NETWORK.equals(lockedReason)) {
                    state[simSubscription] = IccCard.State.NETWORK_LOCKED;
                } else if (IccCard.INTENT_VALUE_LOCKED_SIM.equals(lockedReason)) {
                    state[simSubscription] = IccCard.State.SIM_LOCKED;
                } else {
                    state[simSubscription] = IccCard.State.UNKNOWN;
                }
            } else if (IccCard.INTENT_VALUE_ICC_NOT_READY.equals(stateExtra)) {
                state[simSubscription] = IccCard.State.NOT_READY;
            } else if (IccCard.INTENT_VALUE_ICC_BLOCKED.equals(stateExtra)) {
                state[simSubscription] = IccCard.State.BLOCKED;
            } else {
                state[simSubscription] = IccCard.State.UNKNOWN;
            }
            //add DSDS start
            //return new SimArgs(state);
            return new SimArgs(state,simSubscription);
            //add DSDS end
        }

        public String toString() {
            return simState.toString();
        }
    }

    private static class BatteryStatus {
        public final int status;
        public final int level;
        public final int plugged;
        public final int health;
        public BatteryStatus(int status, int level, int plugged, int health) {
            this.status = status;
            this.level = level;
            this.plugged = plugged;
            this.health = health;
        }

    }

    public KeyguardUpdateMonitor(Context context) {
        mContext = context;

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_TIME_UPDATE:
                        handleTimeUpdate();
                        break;
                    case MSG_BATTERY_UPDATE:
                        handleBatteryUpdate((BatteryStatus) msg.obj);
                        break;
                    case MSG_CARRIER_INFO_UPDATE:
                    	//add DSDS start
                        //handleCarrierInfoUpdate();
                    	handleCarrierInfoUpdate(msg.arg1);
                    	//add DSDS end
                        break;
                    case MSG_SIM_STATE_CHANGE:
                        handleSimStateChange((SimArgs) msg.obj);
                        break;
                    case MSG_RINGER_MODE_CHANGED:
                        handleRingerModeChange(msg.arg1);
                        break;
                    case MSG_PHONE_STATE_CHANGED:
                        handlePhoneStateChanged((String)msg.obj);
                        break;
                    case MSG_CLOCK_VISIBILITY_CHANGED:
                        handleClockVisibilityChanged();
                        break;
                    case MSG_DEVICE_PROVISIONED:
                        handleDeviceProvisioned();
                        break;
                    case MSG_DPM_STATE_CHANGED:
                        handleDevicePolicyManagerStateChanged();
                        break;
                    case MSG_USER_CHANGED:
                        handleUserChanged(msg.arg1);
                        break;
                    case MSG_UNREAD_MESSAGE_COUNT:
                        handleMessageCountChanged(msg.arg1);
                        break;
                }
            }
        };

        mDeviceProvisioned = Settings.Secure.getInt(
                mContext.getContentResolver(), Settings.Secure.DEVICE_PROVISIONED, 0) != 0;

        // Since device can't be un-provisioned, we only need to register a content observer
        // to update mDeviceProvisioned when we are...
        if (!mDeviceProvisioned) {
            mContentObserver = new ContentObserver(mHandler) {
                @Override
                public void onChange(boolean selfChange) {
                    super.onChange(selfChange);
                    mDeviceProvisioned = Settings.Secure.getInt(mContext.getContentResolver(),
                        Settings.Secure.DEVICE_PROVISIONED, 0) != 0;
                    if (mDeviceProvisioned) {
                        mHandler.sendMessage(mHandler.obtainMessage(MSG_DEVICE_PROVISIONED));
                    }
                    if (DEBUG) Log.d(TAG, "DEVICE_PROVISIONED state = " + mDeviceProvisioned);
                }
            };

            mContext.getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.DEVICE_PROVISIONED),
                    false, mContentObserver);

            // prevent a race condition between where we check the flag and where we register the
            // observer by grabbing the value once again...
            boolean provisioned = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.DEVICE_PROVISIONED, 0) != 0;
            if (provisioned != mDeviceProvisioned) {
                mDeviceProvisioned = provisioned;
                if (mDeviceProvisioned) {
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_DEVICE_PROVISIONED));
                }
            }
        }

        // take a guess to start
        //add DSDS start
        // mSimState = IccCard.State.READY;
        //add DSDS end

        mBatteryStatus = new BatteryStatus(BATTERY_STATUS_UNKNOWN, 100, 0, 0);

        //add DSDS start
        //mTelephonyPlmn = getDefaultPlmn();
        int phonecount=TelephonyManager.getPhoneCount();
        mTelephonyPlmn=new CharSequence[phonecount];
        mTelephonySpn=new CharSequence[phonecount];
        mSimState =new IccCard.State[phonecount];
        if(phonecount>1)
        {
        	for(int i=0;i<phonecount;i++)
        	{
        	   mTelephonyPlmn[i]=getDefaultPlmn();
        	   mSimState[i]=IccCard.State.READY;
        	}
        }
        else
        {
        	mTelephonyPlmn[phonecount-1]=getDefaultPlmn();
        	mSimState[phonecount-1]=IccCard.State.READY;
        }
        //add DSDS end
        // setup receiver
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        filter.addAction(SPN_STRINGS_UPDATED_ACTION);
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        filter.addAction(DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED);
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        filter.addAction(Intent.ACTION_USER_REMOVED);
        //add newfeature for lockscreen start
        filter.addAction(ACTION_UNREAD_MESSAGE_COUNT);
        filter.addAction(ACTION_DELETE_UNREAD_MESSAGE_COUNT);
        filter.addAction(ACTION_MISSED_CALL_COUNT);
        filter.addAction(ACTION_MISSED_CALL_CANCEL);
        //add newfeature for lockscreen end
        context.registerReceiver(new BroadcastReceiver() {

            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (DEBUG) Log.d(TAG, "received broadcast " + action);

                if (Intent.ACTION_TIME_TICK.equals(action)
                        || Intent.ACTION_TIME_CHANGED.equals(action)
                        || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_TIME_UPDATE));
                } else if (SPN_STRINGS_UPDATED_ACTION.equals(action)) {
                	//add DSDS start
//                    mTelephonyPlmn = getTelephonyPlmnFrom(intent);
//                    mTelephonySpn = getTelephonySpnFrom(intent);
//                    mHandler.sendMessage(mHandler.obtainMessage(MSG_CARRIER_INFO_UPDATE));
                    mPhoneId =intent.getIntExtra(EXTRA_PHONE_ID,0);
                    mTelephonyPlmn[mPhoneId]=getTelephonyPlmnFrom(intent);
                    mTelephonySpn[mPhoneId]=getTelephonySpnFrom(intent);
                    final Message msg = mHandler.obtainMessage(MSG_CARRIER_INFO_UPDATE);
                    msg.arg1=mPhoneId;
                    mHandler.sendMessage(msg);
                    //add DSDA end
                } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                    final int status = intent.getIntExtra(EXTRA_STATUS, BATTERY_STATUS_UNKNOWN);
                    final int plugged = intent.getIntExtra(EXTRA_PLUGGED, 0);
                    final int level = intent.getIntExtra(EXTRA_LEVEL, 0);
                    final int health = intent.getIntExtra(EXTRA_HEALTH, BATTERY_HEALTH_UNKNOWN);
                    final Message msg = mHandler.obtainMessage(
                            MSG_BATTERY_UPDATE, new BatteryStatus(status, level, plugged, health));
                    mHandler.sendMessage(msg);
                } else if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
                    if (DEBUG_SIM_STATES) {
                        Log.v(TAG, "action " + action + " state" +
                            intent.getStringExtra(IccCard.INTENT_KEY_ICC_STATE));
                    }
                    mHandler.sendMessage(mHandler.obtainMessage(
                            MSG_SIM_STATE_CHANGE, SimArgs.fromIntent(intent)));
                } else if (AudioManager.RINGER_MODE_CHANGED_ACTION.equals(action)) {
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_RINGER_MODE_CHANGED,
                            intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1), 0));
                } else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
                    String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_PHONE_STATE_CHANGED, state));
                } else if (DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED
                        .equals(action)) {
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_DPM_STATE_CHANGED));
                } else if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_USER_CHANGED,
                            intent.getIntExtra(Intent.EXTRA_USERID, 0), 0));
                }
                //add newfeature for lockscreen start
                else if(action.equals(ACTION_UNREAD_MESSAGE_COUNT))
                {
                	int messagecount = intent.getIntExtra(EXTRA_MESSAGE_COUNT,0);
                	mUnreadMessageCount=messagecount;
                	Log.i(TAG,"ACTION_UNREAD_MESSAGE_COUNT:get num of unread message :"+messagecount);
                	mHandler.sendMessage(mHandler.obtainMessage(MSG_UNREAD_MESSAGE_COUNT,messagecount,0));
                }
                else if(action.equals(ACTION_DELETE_UNREAD_MESSAGE_COUNT))
                {
                	mUnreadMessageCount=0;
                	mHandler.sendMessage(mHandler.obtainMessage(MSG_DELETE_UNREAD_MESSAGE_COUNT));
                }
                else if(action.equals(ACTION_MISSED_CALL_COUNT)){
                	int mMissedCallCount = intent.getIntExtra(EXTRA_MISSED_CALL_KEY,0);
                	Log.i(TAG,"ACTION_MISSED_CALL_COUNT:get num of unread message :"+mMissedCallCount);
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_MISSED_CALL_COUNT, mMissedCallCount,0));
                }
                else if(action.equals(ACTION_MISSED_CALL_CANCEL)){
                    int mMissedCallCount = intent.getIntExtra(EXTRA_MISSED_CALL_KEY,0);
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_MISSED_CALL_CANCEL, mMissedCallCount,0));
                }
                //add newfeature for lockscreen end
            }
        }, filter);
    }

    protected void handleDevicePolicyManagerStateChanged() {
        for (int i = 0; i < mInfoCallbacks.size(); i++) {
            mInfoCallbacks.get(i).onDevicePolicyManagerStateChanged();
        }
    }

    protected void handleUserChanged(int userId) {
        for (int i = 0; i < mInfoCallbacks.size(); i++) {
            mInfoCallbacks.get(i).onUserChanged(userId);
        }
    }

    protected void handleDeviceProvisioned() {
        for (int i = 0; i < mInfoCallbacks.size(); i++) {
            mInfoCallbacks.get(i).onDeviceProvisioned();
        }
        if (mContentObserver != null) {
            // We don't need the observer anymore...
            mContext.getContentResolver().unregisterContentObserver(mContentObserver);
            mContentObserver = null;
        }
    }

    protected void handlePhoneStateChanged(String newState) {
        if (DEBUG) Log.d(TAG, "handlePhoneStateChanged(" + newState + ")");
        if (TelephonyManager.EXTRA_STATE_IDLE.equals(newState)) {
            mPhoneState = TelephonyManager.CALL_STATE_IDLE;
        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(newState)) {
            mPhoneState = TelephonyManager.CALL_STATE_OFFHOOK;
        } else if (TelephonyManager.EXTRA_STATE_RINGING.equals(newState)) {
            mPhoneState = TelephonyManager.CALL_STATE_RINGING;
        }
        for (int i = 0; i < mInfoCallbacks.size(); i++) {
            mInfoCallbacks.get(i).onPhoneStateChanged(mPhoneState);
        }
    }

    protected void handleRingerModeChange(int mode) {
        if (DEBUG) Log.d(TAG, "handleRingerModeChange(" + mode + ")");
        mRingMode = mode;
        for (int i = 0; i < mInfoCallbacks.size(); i++) {
            mInfoCallbacks.get(i).onRingerModeChanged(mode);
        }
    }

    /**
     * Handle {@link #MSG_TIME_UPDATE}
     */
    private void handleTimeUpdate() {
        if (DEBUG) Log.d(TAG, "handleTimeUpdate");
        for (int i = 0; i < mInfoCallbacks.size(); i++) {
            mInfoCallbacks.get(i).onTimeChanged();
        }
    }

    /**
     * Handle {@link #MSG_BATTERY_UPDATE}
     */
    private void handleBatteryUpdate(BatteryStatus batteryStatus) {
        if (DEBUG) Log.d(TAG, "handleBatteryUpdate");
        final boolean batteryUpdateInteresting =
                isBatteryUpdateInteresting(mBatteryStatus, batteryStatus);
        mBatteryStatus = batteryStatus;
        if (batteryUpdateInteresting) {
            for (int i = 0; i < mInfoCallbacks.size(); i++) {
                // TODO: pass BatteryStatus object to onRefreshBatteryInfo() instead...
                mInfoCallbacks.get(i).onRefreshBatteryInfo(
                    shouldShowBatteryInfo(),isPluggedIn(batteryStatus), batteryStatus.level);
            }
        }
    }

//    /**
//     * Handle {@link #MSG_CARRIER_INFO_UPDATE}
//     */
//    private void handleCarrierInfoUpdate() {
//        if (DEBUG) Log.d(TAG, "handleCarrierInfoUpdate: plmn = " + mTelephonyPlmn
//            + ", spn = " + mTelephonySpn);
//
//        for (int i = 0; i < mInfoCallbacks.size(); i++) {
//            mInfoCallbacks.get(i).onRefreshCarrierInfo(mTelephonyPlmn, mTelephonySpn);
//        }
//    }

    //add DSDS start
    /**
     * Handle by phoneId {@link #MSG_CARRIER_INFO_UPDATE}
     */
    private void handleCarrierInfoUpdate(int phoneId){
    	if(true) Log.d(TAG,"handleCarrierInfoUpdate: plmn = " + mTelephonyPlmn[phoneId]
            + ", spn = " + mTelephonySpn[phoneId] + ",phoneId = "+ phoneId);
    	for (int i=0; i< mInfoCallbacks.size(); i++)
    	{
    		mInfoCallbacks.get(i).onRefreshCarrierInfo(mTelephonyPlmn[phoneId], mTelephonySpn[phoneId], phoneId);
    	}
    }
    //add DSDS end
    /**
     * Handle {@link #MSG_SIM_STATE_CHANGE}
     */
    private void handleSimStateChange(SimArgs simArgs) {
    	 //add DSDS start
        //final IccCard.State state = simArgs.simState;
        final IccCard.State[] state = simArgs.simState;
        final int subscription=simArgs.subscription;
        //add DSDS end

        if (true) {
            Log.d(TAG, "handleSimStateChange: intentValue = " + simArgs + " "
                    + ",state resolved to " + state[subscription].toString() +", PhoneId = "+subscription);
        }

        if (state[subscription] != IccCard.State.UNKNOWN /*&& state[subscription] != mSimState[subscription]*/) {
            mSimState[subscription] = state[subscription];
            for (int i = 0; i < mSimStateCallbacks.size(); i++) {
                mSimStateCallbacks.get(i).onSimStateChanged(state[subscription],subscription);
            }
        }
    }

    private void handleClockVisibilityChanged() {
        if (DEBUG) Log.d(TAG, "handleClockVisibilityChanged()");
        for (int i = 0; i < mInfoCallbacks.size(); i++) {
            mInfoCallbacks.get(i).onClockVisibilityChanged();
        }
    }

    // add DSDS missed call for lockscreen end
    // add newfeature for lockscreen start
    private void handleMessageCountChanged(int messageCount) {
        if (true)
            Log.d(TAG, "handleMessageCountChanged() :" + messageCount);
        for (int i = 0; i < mInfoCallbacks.size(); i++) {
            mInfoCallbacks.get(i).onMessageCountChanged(messageCount);
        }
    }

    /**
     * @param pluggedIn state from {@link android.os.BatteryManager#EXTRA_PLUGGED}
     * @return Whether the device is considered "plugged in."
     */
    private static boolean isPluggedIn(BatteryStatus status) {
        return status.plugged == BatteryManager.BATTERY_PLUGGED_AC
                || status.plugged == BatteryManager.BATTERY_PLUGGED_USB;
    }

    private static boolean isBatteryUpdateInteresting(BatteryStatus old, BatteryStatus current) {
        final boolean nowPluggedIn = isPluggedIn(current);
        final boolean wasPluggedIn = isPluggedIn(old);
        final boolean stateChangedWhilePluggedIn =
            wasPluggedIn == true && nowPluggedIn == true
            && (old.status != current.status);

        // change in plug state is always interesting
        if (wasPluggedIn != nowPluggedIn || stateChangedWhilePluggedIn) {
            return true;
        }

        // change in battery level while plugged in
        if (nowPluggedIn && old.level != current.level) {
            return true;
        }

        // change where battery needs charging
        if (!nowPluggedIn && isBatteryLow(current) && current.level != old.level) {
            return true;
        }
        return false;
    }

    private static boolean isBatteryLow(BatteryStatus status) {
        return status.level < LOW_BATTERY_THRESHOLD;
    }

    /**
     * @param intent The intent with action {@link Telephony.Intents#SPN_STRINGS_UPDATED_ACTION}
     * @return The string to use for the plmn, or null if it should not be shown.
     */
    private CharSequence getTelephonyPlmnFrom(Intent intent) {
        if (intent.getBooleanExtra(EXTRA_SHOW_PLMN, false)) {
            final String plmn = intent.getStringExtra(EXTRA_PLMN);
            if (plmn != null) {
                return plmn;
            } else {
                return getDefaultPlmn();
            }
        }
        return null;
    }

    /**
     * @return The default plmn (no service)
     */
    private CharSequence getDefaultPlmn() {
        return mContext.getResources().getText(
                        R.string.lockscreen_carrier_default);
    }

    /**
     * @param intent The intent with action {@link Telephony.Intents#SPN_STRINGS_UPDATED_ACTION}
     * @return The string to use for the plmn, or null if it should not be shown.
     */
    private CharSequence getTelephonySpnFrom(Intent intent) {
        if (intent.getBooleanExtra(EXTRA_SHOW_SPN, false)) {
            final String spn = intent.getStringExtra(EXTRA_SPN);
            if (spn != null) {
                return spn;
            }
        }
        return null;
    }

    /**
     * Remove the given observer from being registered from any of the kinds
     * of callbacks.
     * @param observer The observer to remove (an instance of {@link ConfigurationChangeCallback},
     *   {@link InfoCallback} or {@link SimStateCallback}
     */
    public void removeCallback(Object observer) {
        mInfoCallbacks.remove(observer);
        mSimStateCallbacks.remove(observer);
    }

    /**
     * Callback for general information relevant to lock screen.
     */
    interface InfoCallback {
        void onRefreshBatteryInfo(boolean showBatteryInfo, boolean pluggedIn, int batteryLevel);
        void onTimeChanged();

        /**
         * @param plmn The operator name of the registered network.  May be null if it shouldn't
         *   be displayed.
         * @param spn The service provider name.  May be null if it shouldn't be displayed.
         */
        //void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn);
        //add DSDS start
        /**DSDS
         * @param plmn[] The operator name of the registered network.  May be null if it shouldn't
         *   be displayed.
         * @param spn[] The service provider name.  May be null if it shouldn't be displayed.
         */
        void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn,int phoneId);
        //add DSDS end
        /**
         * Called when the ringer mode changes.
         * @param state the current ringer state, as defined in
         * {@link AudioManager#RINGER_MODE_CHANGED_ACTION}
         */
        void onRingerModeChanged(int state);

        /**
         * Called when the phone state changes. String will be one of:
         * {@link TelephonyManager#EXTRA_STATE_IDLE}
         * {@link TelephonyManager@EXTRA_STATE_RINGING}
         * {@link TelephonyManager#EXTRA_STATE_OFFHOOK
         */
        void onPhoneStateChanged(int phoneState);

        /**
         * Called when visibility of lockscreen clock changes, such as when
         * obscured by a widget.
         */
        void onClockVisibilityChanged();

        /**
         * Called when the device becomes provisioned
         */
        void onDeviceProvisioned();

        /**
         * Called when the device policy changes.
         * See {@link DevicePolicyManager#ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED}
         */
        void onDevicePolicyManagerStateChanged();

        /**
         * Called when the user changes.
         */
        void onUserChanged(int userId);

        /**
         * Called when recevie the new Sms/Mms
         *
         * @param messagecount The count of unread Message
         */
        void onMessageCountChanged(int messageCount);
    }

    // Simple class that allows methods to easily be overwritten
    public static class InfoCallbackImpl implements InfoCallback {
        public void onRefreshBatteryInfo(boolean showBatteryInfo, boolean pluggedIn,
                int batteryLevel) {
        }

        public void onTimeChanged() {
        }

        public void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn) {
        }

        public void onRingerModeChanged(int state) {
        }

        public void onPhoneStateChanged(int phoneState) {
        }

        public void onClockVisibilityChanged() {
        }

        public void onDeviceProvisioned() {
        }

        public void onDevicePolicyManagerStateChanged() {
        }

        public void onUserChanged(int userId) {
        }

		@Override
		public void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn,
				int phoneId) {
			// TODO Auto-generated method stub

		}

        @Override
        public void onMessageCountChanged(int messageCount) {
        }
    }

    /**
     * Callback to notify of sim state change.
     */
    interface SimStateCallback {
        //void onSimStateChanged(IccCard.State simState);
        //add DSDS start
        void onSimStateChanged(IccCard.State simState,int subscription);
        //add DSDS end
    }

    /**
     * Register to receive notifications about general keyguard information
     * (see {@link InfoCallback}.
     * @param callback The callback.
     */
    public void registerInfoCallback(InfoCallback callback) {
        if (!mInfoCallbacks.contains(callback)) {
            mInfoCallbacks.add(callback);
            // Notify listener of the current state
            callback.onRefreshBatteryInfo(shouldShowBatteryInfo(),isPluggedIn(mBatteryStatus),
                    mBatteryStatus.level);
            callback.onTimeChanged();
            callback.onRingerModeChanged(mRingMode);
            callback.onPhoneStateChanged(mPhoneState);
            //add DSDS start
			//callback.onRefreshCarrierInfo(mTelephonyPlmn, mTelephonySpn);
            for(int i=0;i<TelephonyManager.getPhoneCount();i++)
            {
    			callback.onRefreshCarrierInfo(mTelephonyPlmn[i], mTelephonySpn[i],i);
            }
			//add DSDS end

            callback.onClockVisibilityChanged();
            callback.onMessageCountChanged(mUnreadMessageCount);
        } else {
            if (DEBUG) Log.e(TAG, "Object tried to add another INFO callback",
                    new Exception("Whoops"));
        }
    }

    /**
     * Register to be notified of sim state changes.
     * @param callback The callback.
     */
    public void registerSimStateCallback(SimStateCallback callback) {
        if (!mSimStateCallbacks.contains(callback)) {
            mSimStateCallbacks.add(callback);
            // Notify listener of the current state
            //add DSDS start
            for(int i=0;i<TelephonyManager.getPhoneCount();i++)
            {
            	callback.onSimStateChanged(mSimState[i],i);
            }
            //add DSDS end
        } else {
            if (DEBUG) Log.e(TAG, "Object tried to add another SIM callback",
                    new Exception("Whoops"));
        }
    }

    public void reportClockVisible(boolean visible) {
        mClockVisible = visible;
        mHandler.obtainMessage(MSG_CLOCK_VISIBILITY_CHANGED).sendToTarget();
    }

    public IccCard.State getSimState() {
        return mSimState[0];
    }
    /**
     * Get the simstate for the subscription.
     * @param subscription the subscription for which sim state is requested.
     */
    public IccCard.State getSimState(int subscription) {
        return mSimState[subscription];
    }

    /**
     * Report that the user successfully entered the SIM PIN or PUK/SIM PIN so we
     * have the information earlier than waiting for the intent
     * broadcast from the telephony code.
     *
     * NOTE: Because handleSimStateChange() invokes callbacks immediately without going
     * through mHandler, this *must* be called from the UI thread.
     */
    public void reportSimUnlocked(int subscription) {
//        mSimState = IccCard.State.READY;
//        handleSimStateChange(new SimArgs(mSimState));

          mSimState[subscription]=IccCard.State.READY;
    	  handleSimStateChange(new SimArgs(mSimState,subscription));
    }

    public boolean isKeyguardBypassEnabled() {
        return mKeyguardBypassEnabled;
    }

    public boolean isDevicePluggedIn() {
        return isPluggedIn(mBatteryStatus);
    }

    public boolean isDeviceCharged() {
        return mBatteryStatus.status == BATTERY_STATUS_FULL
                || mBatteryStatus.level >= 100; // in case particular device doesn't flag it
    }

    public int getBatteryLevel() {
        return mBatteryStatus.level;
    }

    public boolean shouldShowBatteryInfo() {
        return isPluggedIn(mBatteryStatus) || isBatteryLow(mBatteryStatus);
    }

   //add DSDS start
    public CharSequence[] getmTelephonyPlmn() {
		return mTelephonyPlmn;
	}

	public CharSequence[] getmTelephonySpn() {
		return mTelephonySpn;
	}

//	public CharSequence getTelephonyPlmn() {
//        return mTelephonyPlmn;
//    }
//
//    public CharSequence getTelephonySpn() {
//        return mTelephonySpn;
//    }
    //add DSDS end
    /**
     * @return Whether the device is provisioned (whether they have gone through
     *   the setup wizard)
     */
    public boolean isDeviceProvisioned() {
        return mDeviceProvisioned;
    }

    public int getFailedAttempts() {
        return mFailedAttempts;
    }

    public void clearFailedAttempts() {
        mFailedAttempts = 0;
        mFailedBiometricUnlockAttempts = 0;
    }

    public void reportFailedAttempt() {
        mFailedAttempts++;
    }

    public boolean isClockVisible() {
        return mClockVisible;
    }

    public int getPhoneState() {
        return mPhoneState;
    }

    public void reportFailedBiometricUnlockAttempt() {
        mFailedBiometricUnlockAttempts++;
    }

    public boolean getMaxBiometricUnlockAttemptsReached() {
        return mFailedBiometricUnlockAttempts >= FAILED_BIOMETRIC_UNLOCK_ATTEMPTS_BEFORE_BACKUP;
    }

    public boolean isSimLocked() {
        return mSimState[mPhoneId] == IccCard.State.PIN_REQUIRED
            || mSimState[mPhoneId] == IccCard.State.PUK_REQUIRED
            || mSimState[mPhoneId] == IccCard.State.PERM_DISABLED;
    }
}