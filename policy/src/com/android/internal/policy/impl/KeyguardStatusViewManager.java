/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.internal.R;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCard.State;
import com.android.internal.widget.DigitalClock;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.TransportControlView;
import com.android.internal.policy.impl.KeyguardUpdateMonitor.InfoCallbackImpl;
import com.android.internal.policy.impl.KeyguardUpdateMonitor.SimStateCallback;

import java.util.ArrayList;
import java.util.Date;

import libcore.util.MutableInt;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/***
 * Manages a number of views inside of LockScreen layouts. See below for a list of widgets
 *
 */
class KeyguardStatusViewManager implements OnClickListener {
    private static final boolean DEBUG = false;
    private static final String TAG = "KeyguardStatusView";

    public static final int LOCK_ICON = 0; // R.drawable.ic_lock_idle_lock;
    public static final int ALARM_ICON = R.drawable.ic_lock_idle_alarm;
    public static final int CHARGING_ICON = 0; //R.drawable.ic_lock_idle_charging;
    public static final int BATTERY_LOW_ICON = 0; //R.drawable.ic_lock_idle_low_battery;
    private static final long INSTRUCTION_RESET_DELAY = 2000; // time until instruction text resets

    private static final int INSTRUCTION_TEXT = 10;
    private static final int CARRIER_TEXT = 11;
    private static final int CARRIER_HELP_TEXT = 12;
    private static final int HELP_MESSAGE_TEXT = 13;
    private static final int OWNER_INFO = 14;
    private static final int BATTERY_INFO = 15;
    //add newfeature for DSDS start
    private static final int MESSAGE_COUNT=16;
    //add newfeature for DSDS end
    private StatusMode mStatus;
    private String mDateFormatString;
    private TransientTextManager mTransientTextManager;
    private TransientTextManager mTransientTextManager1;

    // Views that this class controls.
    // NOTE: These may be null in some LockScreen screens and should protect from NPE
    private TextView mCarrierView;
    //add DSDS start
    private TextView mCarrierView1;
    //add DSDS end
    private TextView mDateView;
    private TextView mStatus1View;
    private TextView mOwnerInfoView;
    private TextView mAlarmStatusView;
    private TransportControlView mTransportView;

    // Top-level container view for above views
    private View mContainer;

    // are we showing battery information?
    private boolean mShowingBatteryInfo = false;

    // last known plugged in state
    private boolean mPluggedIn = false;

    // last known battery level
    private int mBatteryLevel = 100;

    // last known SIM state
    //add DSDS start
//    protected State mSimState;
//    protected State mSimState1;
    protected State[] mSimState;
    //add DSDS end

    private LockPatternUtils mLockPatternUtils;
    private KeyguardUpdateMonitor mUpdateMonitor;
    private Button mEmergencyCallButton;
    private boolean mEmergencyButtonEnabledBecauseSimLocked;

    // Shadowed text values
    private CharSequence mCarrierText;
    //add DSDS start
    private CharSequence mCarrierText1;
    //add DSDS end
    private CharSequence mCarrierHelpText;
    private String mHelpMessageText;
    private String mInstructionText;
    private CharSequence mOwnerInfoText;
    private boolean mShowingStatus;
    private KeyguardScreenCallback mCallback;
    private final boolean mEmergencyCallButtonEnabledInScreen;
    //add DSDS start
    private int mphoneId;
//    private CharSequence mPlmn;
//    private CharSequence mSpn;
//    private CharSequence mPlmn1;
//    private CharSequence mSpn1;
    private CharSequence[] mPlmn;
    private CharSequence[] mSpn;
    //add DSDS end
    //add newfeature for DSDS start
    private int mMessageCount=0;
    private TextView mMessageCoutView;
    private TextView mPhoneCoutView;
    private CharSequence mMessageCountInfo;
    private boolean mUnreadMessageEnabledInLockScreen;
    private int mMissedCallCount=0;
    private TextView mMissedCallTextView;
    //add DSDS missed call end

    protected int mPhoneState;
    private DigitalClock mDigitalClock;
  
    private class TransientTextManager {
        private TextView mTextView;
        private class Data {
            final int icon;
            final CharSequence text;
            Data(CharSequence t, int i) {
                text = t;
                icon = i;
            }
        };
        private ArrayList<Data> mMessages = new ArrayList<Data>(5);

        TransientTextManager(TextView textView) {
            mTextView = textView;
        }

        /* Show given message with icon for up to duration ms. Newer messages override older ones.
         * The most recent message with the longest duration is shown as messages expire until
         * nothing is left, in which case the text/icon is defined by a call to
         * getAltTextMessage() */
        void post(final CharSequence message, final int icon, long duration) {
            if (mTextView == null) {
                return;
            }
            mTextView.setText(message);
            mTextView.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
            final Data data = new Data(message, icon);
            mContainer.postDelayed(new Runnable() {
                public void run() {
                    mMessages.remove(data);
                    int last = mMessages.size() - 1;
                    final CharSequence lastText;
                    final int lastIcon;
                    if (last > 0) {
                        final Data oldData = mMessages.get(last);
                        lastText = oldData.text;
                        lastIcon = oldData.icon;
                    } else {
                        final MutableInt tmpIcon = new MutableInt(0);
                        lastText = getAltTextMessage(tmpIcon);
                        lastIcon = tmpIcon.value;
                    }
                    mTextView.setText(lastText);
                    mTextView.setCompoundDrawablesWithIntrinsicBounds(lastIcon, 0, 0, 0);
                }
            }, duration);
        }
    };

    public KeyguardStatusViewManager(View view, KeyguardUpdateMonitor updateMonitor,
            LockPatternUtils lockPatternUtils, KeyguardScreenCallback callback,
            boolean emergencyButtonEnabledInScreen){
    	this(view,updateMonitor,lockPatternUtils,callback,emergencyButtonEnabledInScreen,false);
    }

    /**
     *
     * @param view the containing view of all widgets
     * @param updateMonitor the update monitor to use
     * @param lockPatternUtils lock pattern util object
     * @param callback used to invoke emergency dialer
     * @param emergencyButtonEnabledInScreen whether emergency button is enabled by default
     */
    public KeyguardStatusViewManager(View view, KeyguardUpdateMonitor updateMonitor,
                LockPatternUtils lockPatternUtils, KeyguardScreenCallback callback,
                boolean emergencyButtonEnabledInScreen,boolean unreadMessageEnabledInLockScreen) {
        if (DEBUG) Log.v(TAG, "KeyguardStatusViewManager()");
        mContainer = view;
        mDateFormatString = getContext().getString(R.string.abbrev_wday_month_day_no_year);
        mLockPatternUtils = lockPatternUtils;
        mUpdateMonitor = updateMonitor;
        mCallback = callback;
        mCarrierView = (TextView) findViewById(R.id.carrier);
        mCarrierView1 = (TextView) findViewById(R.id.carrier1);
        if (!TelephonyManager.isMultiSim()) {
              mCarrierView1.setVisibility(View.GONE);
        }
        mPlmn=new CharSequence[TelephonyManager.getPhoneCount()];
        mSpn =new CharSequence[TelephonyManager.getPhoneCount()];
        mSimState =new State[TelephonyManager.getPhoneCount()];
        mMessageCoutView = (TextView) findViewById(R.id.messageCount);
        if (mMessageCoutView != null) {
            if (mMessageCount == 0) {
                mMessageCoutView.setVisibility(View.GONE);
                messageCountViewsetOnClickListener(false);
            } else {
                mMessageCoutView.setVisibility(View.VISIBLE);
                messageCountViewsetOnClickListener(true);
            }
        }
        mDateView = (TextView) findViewById(R.id.date);
        mStatus1View = (TextView) findViewById(R.id.status1);
        mAlarmStatusView = (TextView) findViewById(R.id.alarm_status);
        mOwnerInfoView = (TextView) findViewById(R.id.propertyOf);
        mTransportView = (TransportControlView) findViewById(R.id.transport);
        mEmergencyCallButton = (Button) findViewById(R.id.emergencyCallButton);
        mEmergencyCallButtonEnabledInScreen = emergencyButtonEnabledInScreen;
        mDigitalClock = (DigitalClock) findViewById(R.id.time);

        // Hide transport control view until we know we need to show it.
        if (mTransportView != null) {
            mTransportView.setVisibility(View.GONE);
        }

        if (mEmergencyCallButton != null) {
            mEmergencyCallButton.setText(R.string.lockscreen_emergency_call);
            mEmergencyCallButton.setOnClickListener(this);
            mEmergencyCallButton.setFocusable(false); // touch only!
        }

            if (TelephonyManager.getPhoneCount() > 1)
            {
                mTransientTextManager = new TransientTextManager(mCarrierView);
                mTransientTextManager1 = new TransientTextManager(mCarrierView1);
            }
            else
            {
                mTransientTextManager = new TransientTextManager(mCarrierView);
            }

        mUpdateMonitor.registerInfoCallback(mInfoCallback);
        mUpdateMonitor.registerSimStateCallback(mSimStateCallback);

        resetStatusInfo();
        refreshDate();
        updateOwnerInfo();

        // Required to get Marquee to work.
        // add DSDS start
//        final View scrollableViews[] = { mCarrierView,mCarrierView1, mDateView, mStatus1View, mOwnerInfoView,
//                mAlarmStatusView };

            if(TelephonyManager.getPhoneCount()>1)
            {
                final View scrollableViews[] = { mCarrierView,mCarrierView1, mDateView, mStatus1View, mOwnerInfoView,
                        mAlarmStatusView };
                for (View v : scrollableViews) {
                    if (v != null) {
                        v.setSelected(true);
                    }
                }
            }
            else
            {
                final View scrollableViews[] = { mCarrierView, mDateView, mStatus1View, mOwnerInfoView,
                        mAlarmStatusView };
                for (View v : scrollableViews) {
                    if (v != null) {
                        v.setSelected(true);
                    }
                }
            }

//        for (View v : scrollableViews) {
//            if (v != null) {
//                v.setSelected(true);
//            }
//        }
        //add DSDS end
    }

    private boolean inWidgetMode() {
        return mTransportView != null && mTransportView.getVisibility() == View.VISIBLE;
    }

    void setInstructionText(String string) {
        mInstructionText = string;
        update(INSTRUCTION_TEXT, string);
    }

    void setCarrierText(CharSequence string,int phoneId) {
            if(phoneId ==0)
            {
                mCarrierText = string;
            }
            else
            {
                mCarrierText1= string;
            }

        mphoneId=phoneId;
        update(CARRIER_TEXT, string);
    }

    void setOwnerInfo(CharSequence string) {
        mOwnerInfoText = string;
        update(OWNER_INFO, string);
    }

    private void messageCountViewsetOnClickListener(boolean enable){
        if(mMessageCoutView !=null){
            if(enable){
                mMessageCoutView.setOnClickListener(new OnClickListener() {
                     @Override
                     public void onClick(View v) {
                         if(mMessageCount == 0){
                             return;
                         }
                         Intent intent = new Intent(Intent.ACTION_MAIN);
                         intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                 | Intent.FLAG_ACTIVITY_SINGLE_TOP
                                 | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                         intent.setType("vnd.android-dir/mms-sms");
                         getContext().startActivity(intent);
                         mCallback.goToUnlockScreen();
                     }
                 });
           }else{
                 mMessageCoutView.setOnClickListener(null);
           }
        }
    }

    //add newfeature for DSDS start
    void setMessageCountText(int messagecount)
    {
    	mMessageCountInfo=getContext().getString(R.string.unread_message, messagecount);

    	if(messagecount > 0){
    		messageCountViewsetOnClickListener(true);
    	}else{
    		messageCountViewsetOnClickListener(false);
    	}
    	mMessageCount = messagecount;
    	update(MESSAGE_COUNT, String.valueOf(messagecount));
    }
    //add newfeature for DSDS end

    /**
     * Sets the carrier help text message, if view is present. Carrier help text messages are
     * typically for help dealing with SIMS and connectivity.
     *
     * @param resId resource id of the message
     */
    public void setCarrierHelpText(int resId) {
        mCarrierHelpText = getText(resId);
        update(CARRIER_HELP_TEXT, mCarrierHelpText);
    }

    private CharSequence getText(int resId) {
        return resId == 0 ? null : getContext().getText(resId);
    }

    /**
     * Unlock help message.  This is typically for help with unlock widgets, e.g. "wrong password"
     * or "try again."
     *
     * @param textResId
     * @param lockIcon
     */
    public void setHelpMessage(int textResId, int lockIcon) {
        final CharSequence tmp = getText(textResId);
        mHelpMessageText = tmp == null ? null : tmp.toString();
        update(HELP_MESSAGE_TEXT, mHelpMessageText);
    }

    private void update(int what, CharSequence string) {
        if (inWidgetMode()) {
            if (DEBUG) Log.v(TAG, "inWidgetMode() is true");
            // Use Transient text for messages shown while widget is shown.
            switch (what) {
                case INSTRUCTION_TEXT:
                case CARRIER_HELP_TEXT:
                case HELP_MESSAGE_TEXT:
                case BATTERY_INFO:
                    mTransientTextManager.post(string, 0, INSTRUCTION_RESET_DELAY);
                    break;

                case OWNER_INFO:
                case CARRIER_TEXT:
                case MESSAGE_COUNT:
                default:
                    if (DEBUG) Log.w(TAG, "Not showing message id " + what + ", str=" + string);
            }
        } else {
            updateStatusLines(mShowingStatus);
        }
    }

    public void onPause() {
        if (DEBUG) Log.v(TAG, "onPause()");
        mUpdateMonitor.removeCallback(mInfoCallback);
        mUpdateMonitor.removeCallback(mSimStateCallback);
    }

    /** {@inheritDoc} */
    public void onResume() {
        if (DEBUG) Log.v(TAG, "onResume()");

        // First update the clock, if present.
        if (mDigitalClock != null) {
            mDigitalClock.updateTime();
        }

        mUpdateMonitor.registerInfoCallback(mInfoCallback);
        mUpdateMonitor.registerSimStateCallback(mSimStateCallback);
        resetStatusInfo();
        // Issue the biometric unlock failure message in a centralized place
        // TODO: we either need to make the Face Unlock multiple failures string a more general
        // 'biometric unlock' or have each biometric unlock handle this on their own.
        if (mUpdateMonitor.getMaxBiometricUnlockAttemptsReached()) {
            setInstructionText(getContext().getString(R.string.faceunlock_multiple_failures));
        }
    }

    void resetStatusInfo() {
        mInstructionText = null;
        mShowingBatteryInfo = mUpdateMonitor.shouldShowBatteryInfo();
        mPluggedIn = mUpdateMonitor.isDevicePluggedIn();
        mBatteryLevel = mUpdateMonitor.getBatteryLevel();
        updateStatusLines(true);
    }

    /**
     * Update the status lines based on these rules:
     * AlarmStatus: Alarm state always gets it's own line.
     * Status1 is shared between help, battery status and generic unlock instructions,
     * prioritized in that order.
     * @param showStatusLines status lines are shown if true
     */
    void updateStatusLines(boolean showStatusLines) {
        if (DEBUG) Log.v(TAG, "updateStatusLines(" + showStatusLines + ")");
        mShowingStatus = showStatusLines;
        updateAlarmInfo();
        updateOwnerInfo();
        updateStatus1();
        updateCarrierText();
        updateMessageCountInfoForLockScreen();
    }

    private void updateAlarmInfo() {
        if (mAlarmStatusView != null) {
            String nextAlarm = mLockPatternUtils.getNextAlarm();
            boolean showAlarm = mShowingStatus && !TextUtils.isEmpty(nextAlarm);
            mAlarmStatusView.setText(nextAlarm);
            mAlarmStatusView.setCompoundDrawablesWithIntrinsicBounds(ALARM_ICON, 0, 0, 0);
            mAlarmStatusView.setVisibility(showAlarm ? View.VISIBLE : View.GONE);
        }
    }

    private void updateOwnerInfo() {
        final ContentResolver res = getContext().getContentResolver();
        final boolean ownerInfoEnabled = Settings.Secure.getInt(res,
                Settings.Secure.LOCK_SCREEN_OWNER_INFO_ENABLED, 1) != 0;
        mOwnerInfoText = ownerInfoEnabled ?
                Settings.Secure.getString(res, Settings.Secure.LOCK_SCREEN_OWNER_INFO) : null;
        if (mOwnerInfoView != null) {
            mOwnerInfoView.setText(mOwnerInfoText);
            mOwnerInfoView.setVisibility(TextUtils.isEmpty(mOwnerInfoText) ? View.GONE:View.VISIBLE);
        }
    }

    private void updateStatus1() {
        if (mStatus1View != null) {
            MutableInt icon = new MutableInt(0);
            CharSequence string = getPriorityTextMessage(icon);
            mStatus1View.setText(string);
            mStatus1View.setCompoundDrawablesWithIntrinsicBounds(icon.value, 0, 0, 0);
            mStatus1View.setVisibility(mShowingStatus ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void updateCarrierText() {
            if (!inWidgetMode() && mCarrierView != null) {
                mCarrierView.setText(mCarrierText);
            }

            if (TelephonyManager.isMultiSim())
            {
                if (!inWidgetMode() && mCarrierView1 != null) {
                    mCarrierView1.setText(mCarrierText1);
                }
            }


    }

    //add new feature for lockscreen start
    private void updateMessageCountInfoForLockScreen()
    {
    	Log.i("KeyguardUpdateMonitor","show the Message Count Info : "+mMessageCountInfo);
    	if(mMessageCoutView != null)
    	{
        	mMessageCoutView.setText(mMessageCountInfo);
        	mMessageCoutView.setVisibility(View.VISIBLE);
    	}

    }

    private void deleteMessageCountInfoForLockScreen()
    {
    	Log.i("KeyguardUpdateMonitor","delete the Message Count Info : "+mMessageCountInfo);
    	if(mMessageCoutView != null)
    	{
        	mMessageCoutView.setText("");
        	mMessageCoutView.setVisibility(View.GONE);
    	}
    }

    private void missedCallTextViewSetOnClickListener(boolean enable){
        if(mMissedCallTextView !=null){
            if(enable){
                mMissedCallTextView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(Intent.ACTION_CALL_BUTTON);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                        getContext().startActivity(intent);
                        mCallback.goToUnlockScreen();
                    }
                });
            }else{
                mMissedCallTextView.setOnClickListener(null);
            }
        }
    }

    private void updateMissedCallInfoForLockScreen(int count) {
        Log.i(TAG, "updateMissedCallInfoForLockScreen : " + count);
        if (mMissedCallTextView != null) {
            if (count > 0) {
            	missedCallTextViewSetOnClickListener(true);
                mMissedCallCount = count;
                mMissedCallTextView.setVisibility(View.VISIBLE);
                mMissedCallTextView.setText(getContext().getString(R.string.missed_call,
                        mMissedCallCount));
            } else {
                mMissedCallTextView.setVisibility(View.GONE);
                missedCallTextViewSetOnClickListener(false);
            }
        }
    }
    //add new feature for lockscreen end

    private CharSequence getAltTextMessage(MutableInt icon) {
        // If we have replaced the status area with a single widget, then this code
        // prioritizes what to show in that space when all transient messages are gone.
        CharSequence string = null;
        if (mShowingBatteryInfo) {
            // Battery status
            if (mPluggedIn) {
                // Charging or charged
                if (mUpdateMonitor.isDeviceCharged()) {
                    string = getContext().getString(R.string.lockscreen_charged);
                } else {
                    string = getContext().getString(R.string.lockscreen_plugged_in, mBatteryLevel);
                }
                icon.value = CHARGING_ICON;
            } else if (mBatteryLevel < KeyguardUpdateMonitor.LOW_BATTERY_THRESHOLD) {
                // Battery is low
                string = getContext().getString(R.string.lockscreen_low_battery);
                icon.value = BATTERY_LOW_ICON;
            }
        } else {
            string = mCarrierText;
        }
        return string;
    }

    private CharSequence getPriorityTextMessage(MutableInt icon) {
        CharSequence string = null;
        if (!TextUtils.isEmpty(mInstructionText)) {
            // Instructions only
            string = mInstructionText;
            icon.value = LOCK_ICON;
        } else if (mShowingBatteryInfo) {
            // Battery status
            if (mPluggedIn) {
                // Charging or charged
                if (mUpdateMonitor.isDeviceCharged()) {
                    string = getContext().getString(R.string.lockscreen_charged);
                } else {
                    string = getContext().getString(R.string.lockscreen_plugged_in, mBatteryLevel);
                }
                icon.value = CHARGING_ICON;
            } else if (mBatteryLevel < KeyguardUpdateMonitor.LOW_BATTERY_THRESHOLD) {
                // Battery is low
                string = getContext().getString(R.string.lockscreen_low_battery);
                icon.value = BATTERY_LOW_ICON;
            }
        } else if (!inWidgetMode() && mOwnerInfoView == null && mOwnerInfoText != null) {
            // OwnerInfo shows in status if we don't have a dedicated widget
            string = mOwnerInfoText;
        }
        return string;
    }

    void refreshDate() {
        if (mDateView != null) {
            mDateView.setText(DateFormat.format(mDateFormatString, new Date()));
        }
    }

    /**
     * Determine the current status of the lock screen given the sim state and other stuff.
     */
    public StatusMode getStatusForIccState(IccCard.State simState) {
        // Since reading the SIM may take a while, we assume it is present until told otherwise.
        if (simState == null) {
            return StatusMode.Normal;
        }

        final boolean missingAndNotProvisioned = (!mUpdateMonitor.isDeviceProvisioned()
                && (simState == IccCard.State.ABSENT || simState == IccCard.State.PERM_DISABLED));

        // Assume we're NETWORK_LOCKED if not provisioned
        simState = missingAndNotProvisioned ? State.NETWORK_LOCKED : simState;
        switch (simState) {
            case ABSENT:
                return StatusMode.SimMissing;
            case NETWORK_LOCKED:
                return StatusMode.NetworkLocked;
            case NOT_READY:
                return StatusMode.SimNotReady;
            case SIM_LOCKED:
                return StatusMode.SimLocked;
            case PIN_REQUIRED:
                return StatusMode.SimLocked;
            case PUK_REQUIRED:
                return StatusMode.SimPukLocked;
            case READY:
                return StatusMode.Normal;
            case BLOCKED:
            case PERM_DISABLED:
                return StatusMode.SimPermDisabled;
            case UNKNOWN:
                return StatusMode.SimMissing;
        }
        return StatusMode.SimMissing;
    }

    private Context getContext() {
        return mContainer.getContext();
    }

    /**
     * Update carrier text, carrier help and emergency button to match the current status based
     * on SIM state.
     *
     * @param simState
     */
    private void updateCarrierStateWithSimStatus(State simState,int phoneId) {
        if (DEBUG) Log.d(TAG, "updateCarrierTextWithSimStatus(), simState = " + simState);

//      CharSequence carrierText = null;
//      CharSequence carrierText1 = null;
        CharSequence[] carrierText=new CharSequence[TelephonyManager.getPhoneCount()];
        int carrierHelpTextId = 0;
        mEmergencyButtonEnabledBecauseSimLocked = false;
        mStatus = getStatusForIccState(simState);
        mSimState[phoneId] = simState;
            switch (mStatus) {
                // fix bug 116364 start
                case SimNotReady:
                    carrierText[phoneId] = makeCarierString(mPlmn[phoneId], mSpn[phoneId]);
                    break;
                // fix bug 116364 end
                case Normal:
                    carrierText[phoneId] = makeCarierString(mPlmn[phoneId], mSpn[phoneId]);
                    break;

                case NetworkLocked:
                    carrierText[phoneId] = makeCarierString(mPlmn[phoneId],
                            getContext().getText(R.string.lockscreen_network_locked_message));
                    carrierHelpTextId = R.string.lockscreen_instructions_when_pattern_disabled;
                    break;

                case SimMissing:
                    // Shows "No SIM card | Emergency calls only" on devices that are voice-capable.
                    // This depends on mPlmn containing the text "Emergency calls only" when the radio
                    // has some connectivity. Otherwise, it should be null or empty and just show
                    // "No SIM card"
                	carrierText[phoneId] = getContext().getText(R.string.lockscreen_missing_sim_message_short);
                    /*if (mLockPatternUtils.isEmergencyCallCapable()) {
                    	carrierText[phoneId] = makeCarierString(mPlmn[phoneId],carrierText[phoneId]);
                    }*/
                    carrierHelpTextId = R.string.lockscreen_missing_sim_instructions_long;
                    break;

                case SimPermDisabled:
                    carrierText[phoneId] = makeCarierString(mPlmn[phoneId],
                            getContext().getText(R.string.lockscreen_blocked_sim_message_short));
                    carrierHelpTextId = R.string.lockscreen_permanent_disabled_sim_instructions;
                    mEmergencyButtonEnabledBecauseSimLocked = true;
                    break;

                case SimMissingLocked:
                	carrierText[phoneId] = makeCarierString(mPlmn[phoneId],
                            getContext().getText(R.string.lockscreen_missing_sim_message_short));
                    carrierHelpTextId = R.string.lockscreen_missing_sim_instructions;
                    mEmergencyButtonEnabledBecauseSimLocked = true;
                    break;

                case SimLocked:
                	carrierText[phoneId] = makeCarierString(mPlmn[phoneId],
                            getContext().getText(R.string.lockscreen_sim_locked_message));
                    mEmergencyButtonEnabledBecauseSimLocked = true;
                    break;

                case SimPukLocked:
                	carrierText[phoneId] = makeCarierString(mPlmn[phoneId],
                            getContext().getText(R.string.lockscreen_sim_puk_locked_message));
                    if (!mLockPatternUtils.isPukUnlockScreenEnable()) {
                        // This means we're showing the PUK unlock screen
                        mEmergencyButtonEnabledBecauseSimLocked = true;
                    }
                    break;
            }
        setCarrierText(carrierText[phoneId],phoneId);
        Log.d("KeyguardStatusViewManager", "updateCarrierTextWithSimStatus()--" +
                  ", carrierText["+phoneId+"]="+carrierText[phoneId]+", mStatus="+mStatus+", phoneId="+phoneId);
        setCarrierHelpText(carrierHelpTextId);
        updateEmergencyCallButtonState(mPhoneState);
    }


    /*
     * Add emergencyCallMessage to carrier string only if phone supports emergency calls.
     */
    private CharSequence makeCarrierStringOnEmergencyCapable(
            CharSequence simMessage, CharSequence emergencyCallMessage) {
        if (mLockPatternUtils.isEmergencyCallCapable()) {
            return makeCarierString(simMessage, emergencyCallMessage);
        }
        return simMessage;
    }

    //add new feature for DSDS start
    private void updateMessageCountForLockScreen(int messagecount){
    	setMessageCountText(messagecount);
    }
    //add new feature for DSDS end
    private View findViewById(int id) {
        return mContainer.findViewById(id);
    }

    /**
     * The status of this lock screen. Primarily used for widgets on LockScreen.
     */
    enum StatusMode {
        /**
         * Normal case (sim card present, it's not locked)
         */
        Normal(true),

        /**
         * The sim card is 'network locked'.
         */
        NetworkLocked(true),

        /**
         * The sim card is missing.
         */
        SimMissing(false),

        /**
         * The sim card is not ready.
         */
        SimNotReady(false),

        /**
         * The sim card is missing, and this is the device isn't provisioned, so
         * we don't let them get past the screen.
         */
        SimMissingLocked(false),

        /**
         * The sim card is PUK locked, meaning they've entered the wrong sim unlock code too many
         * times.
         */
        SimPukLocked(false),

        /**
         * The sim card is locked.
         */
        SimLocked(true),

        /**
         * The sim card is permanently disabled due to puk unlock failure
         */
        SimPermDisabled(false);

        private final boolean mShowStatusLines;

        StatusMode(boolean mShowStatusLines) {
            this.mShowStatusLines = mShowStatusLines;
        }

        /**
         * @return Whether the status lines (battery level and / or next alarm) are shown while
         *         in this state.  Mostly dictated by whether this is room for them.
         */
        public boolean shouldShowStatusLines() {
            return mShowStatusLines;
        }
    }

    private void updateEmergencyCallButtonState(int phoneState) {
        if (mEmergencyCallButton != null) {
            //add DSDS start
//            boolean enabledBecauseSimLocked =
//                    mLockPatternUtils.isEmergencyCallEnabledWhileSimLocked()
//                    && mEmergencyButtonEnabledBecauseSimLocked;
//            boolean shown = mEmergencyCallButtonEnabledInScreen || enabledBecauseSimLocked;
            boolean shown = mEmergencyCallButtonEnabledInScreen;
            //add DSDS end
            mLockPatternUtils.updateEmergencyCallButtonState(mEmergencyCallButton,
                    phoneState, shown);
        }
    }

    private InfoCallbackImpl mInfoCallback = new InfoCallbackImpl() {

        @Override
        public void onRefreshBatteryInfo(boolean showBatteryInfo, boolean pluggedIn,
                int batteryLevel) {
            mShowingBatteryInfo = showBatteryInfo;
            mPluggedIn = pluggedIn;
            mBatteryLevel = batteryLevel;
            final MutableInt tmpIcon = new MutableInt(0);
            update(BATTERY_INFO, getAltTextMessage(tmpIcon));
        }

        @Override
        public void onTimeChanged() {
            refreshDate();
        }

        public void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn, int phoneId) {
           Log.i(TAG,"onRefreshCarrierInfo implements: plmn ="+plmn+", spn ="+spn+", phoneId"+phoneId);
//           if(phoneId ==0)
//           {
//               mPlmn = plmn;
//               mSpn = spn;
//        	   updateCarrierStateWithSimStatus(mSimState,phoneId);
//           }
//           else
//           {
//               mPlmn1 = plmn;
//               mSpn1 = spn;
//        	   updateCarrierStateWithSimStatus(mSimState1,phoneId);
//           }
             mPlmn[phoneId]=plmn;
             mSpn [phoneId]=spn;
             updateCarrierStateWithSimStatus(mSimState[phoneId],phoneId);
        }

        @Override
        public void onPhoneStateChanged(int phoneState) {
            mPhoneState = phoneState;
            updateEmergencyCallButtonState(phoneState);
        }

        /** {@inheritDoc} */
        public void onClockVisibilityChanged() {
            // ignored
        }

        public void onDeviceProvisioned() {
            // ignored
        }

        public void onMessageCountChanged(int messageCount) {
            if (messageCount > 0) {
                updateMessageCountForLockScreen(messageCount);
            }
        };
    };

    private SimStateCallback mSimStateCallback = new SimStateCallback() {

        public void onSimStateChanged(IccCard.State simState,int subscription) {
            updateCarrierStateWithSimStatus(simState,subscription);
        }
    };

    public void onClick(View v) {
        if (v == mEmergencyCallButton) {
            mCallback.takeEmergencyCallAction();
        }
    }

    /**
     * Performs concentenation of PLMN/SPN
     * @param plmn
     * @param spn
     * @return
     */
    private static CharSequence makeCarierString(CharSequence plmn, CharSequence spn) {
        final boolean plmnValid = !TextUtils.isEmpty(plmn);
        final boolean spnValid = !TextUtils.isEmpty(spn);
        if (plmnValid && spnValid) {
            return plmn + "|" + spn;
        } else if (plmnValid) {
            return plmn;
        } else if (spnValid) {
            return spn;
        } else {
            return "";
        }
    }
}
