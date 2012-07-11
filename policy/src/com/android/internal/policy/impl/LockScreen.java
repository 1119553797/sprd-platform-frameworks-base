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

import com.android.internal.R;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.SlidingTab;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.FontMetrics;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.BaseColumns;
import android.provider.CallLog;
import android.provider.Settings;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;

import java.util.Date;
import java.io.File;

/**
 * The screen within {@link LockPatternKeyguardView} that shows general
 * information about the device depending on its state, and how to get
 * past it, as applicable.
 */
class LockScreen extends LinearLayout implements KeyguardScreen, KeyguardUpdateMonitor.InfoCallback,
        KeyguardUpdateMonitor.SimStateCallback, SlidingTab.OnTriggerListener {

    private static final boolean DBG = true;
    private static final String TAG = "LockScreen";
    private static final String ENABLE_MENU_KEY_FILE = "/data/local/enable_menu_key";

    private Status[] mStatus;

    private LockPatternUtils mLockPatternUtils;
    private KeyguardUpdateMonitor mUpdateMonitor;
    private KeyguardScreenCallback mCallback;

    private int mCallSub;
    
//    private TextView mCarrier;
    private TextView[] mCarrier;
    private SlidingTab mSelector;
    private TextView mTime;
    private TextView mDate;
    private TextView mStatus1;
    private TextView mStatus2;
    private TextView mScreenLocked;
    private TextView mEmergencyCallText;
    private Button mEmergencyCallButton;

    //PUK Input Add Start
    private Button mPukButton;
    //PUK Input Add End
    // current configuration state of keyboard and display
    private int mKeyboardHidden;
    private int mCreationOrientation;

    // are we showing battery information?
    private boolean mShowingBatteryInfo = false;

    // last known plugged in state
    private boolean mPluggedIn = false;

    // last known battery level
    private int mBatteryLevel = 100;

    private String mNextAlarm = null;
    private Drawable mAlarmIcon = null;
    private String mCharging = null;
    private Drawable mChargingIcon = null;

    private boolean mSilentMode;
    private AudioManager mAudioManager;
    private String mDateFormatString;
    private java.text.DateFormat mTimeFormat;
    private boolean mEnableMenuKeyInLockScreen;
    private int[] mResId = new int[]{R.id.carrier, R.id.carrier_sub2};
    
 // ************Modify by luning at01-07-01 begin************
    private Context mContext;
    private IccText mSimText;
    private IccText mRuimText;
    private IccText mIccText;
    private PhoneStateListener[] mPhoneStateListener;
 // ************Modify by luning at01-07-01 end************
    private int numPhones = 0;
    
	private static final String INTENT_MISSMMS = "android.intent.action.missMms";
	private static final String INTENT_MISSPHONE = "com.android.phone.action.RECENT_CALLS";
	
	private boolean hasMissPhone =false;
	private boolean hasMissMms =false;
	
	private CObserver smsObserver;
	private CObserver callObserver;
	private CObserver mmsObserver;

   	private static Paint mPaint = new Paint();
   	private static final String INFINITY_UNICODE = "\u221e";
   	private static final int MAX_SIZE=100;
   	private static float mFewTextSize;
   	private static float mManytextSize;
   	private static final int mOffsetY = 3;
   	private static final Canvas sCanvas = new Canvas();
   	private Bitmap mLeftBitmap;
   	private Bitmap mRightBitmap;

	private static final int ON_KEYDOWN_TIMEOUT_MS = 3600000;
    
    /**
     * The status of this lock screen.
     */
    enum Status {
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
        SimMissing(true),

        /**
         * The sim card is missing, and this is the device isn't provisioned, so we don't let
         * them get past the screen.
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
        /*
         * The card is locked
         */
        CardLocked(true);
        private final boolean mShowStatusLines;

        Status(boolean mShowStatusLines) {
            this.mShowStatusLines = mShowStatusLines;
        }

        /**
         * @return Whether the status lines (battery level and / or next alarm) are shown while
         *         in this state.  Mostly dictated by whether this is room for them.
         */
        public boolean showStatusLines() {
            return mShowStatusLines;
        }
    }

    /**
     * In general, we enable unlocking the insecure key guard with the menu key. However, there are
     * some cases where we wish to disable it, notably when the menu button placement or technology
     * is prone to false positives.
     *
     * @return true if the menu key should be enabled
     */
    private boolean shouldEnableMenuKey() {
        final Resources res = getResources();
        final boolean configDisabled = res.getBoolean(R.bool.config_disableMenuKeyInLockScreen);
        final boolean isMonkey = SystemProperties.getBoolean("ro.monkey", false);
        final boolean fileOverride = (new File(ENABLE_MENU_KEY_FILE)).exists();
        return false;
//      return !configDisabled || isMonkey || fileOverride;
    }

    /**
     * @param context Used to setup the view.
     * @param configuration The current configuration. Used to use when selecting layout, etc.
     * @param lockPatternUtils Used to know the state of the lock pattern settings.
     * @param updateMonitor Used to register for updates on various keyguard related
     *    state, and query the initial state at setup.
     * @param callback Used to communicate back to the host keyguard view.
     */
    LockScreen(Context context, Configuration configuration, LockPatternUtils lockPatternUtils,
            KeyguardUpdateMonitor updateMonitor,
            KeyguardScreenCallback callback) {
        super(context);
        
     // ************Modify by luning at01-07-01 begin************
        mContext = context;
     // ************Modify by luning at01-07-01 end************
        
        mLockPatternUtils = lockPatternUtils;
        mUpdateMonitor = updateMonitor;
        mCallback = callback;

        mEnableMenuKeyInLockScreen = shouldEnableMenuKey();

        mCreationOrientation = configuration.orientation;

        mKeyboardHidden = configuration.hardKeyboardHidden;

        if (LockPatternKeyguardView.DEBUG_CONFIGURATION) {
            Log.v(TAG, "***** CREATING LOCK SCREEN", new RuntimeException());
            Log.v(TAG, "Cur orient=" + mCreationOrientation
                    + " res orient=" + context.getResources().getConfiguration().orientation);
        }

        final LayoutInflater inflater = LayoutInflater.from(context);
        if (DBG) Log.v(TAG, "Creation orientation = " + mCreationOrientation);
        if (mCreationOrientation != Configuration.ORIENTATION_LANDSCAPE) {
            inflater.inflate(R.layout.keyguard_screen_tab_unlock, this, true);
        } else {
            inflater.inflate(R.layout.keyguard_screen_tab_unlock_land, this, true);
        }

//        mCarrier = (TextView) findViewById(R.id.carrier);
//        // Required for Marquee to work
//        mCarrier.setSelected(true);
//        mCarrier.setTextColor(0xffffffff);
        numPhones=TelephonyManager.getPhoneCount();
        // Sim States for the subscription
        mCarrier = new TextView[numPhones];
        mStatus = new Status[numPhones];
        for (int i = 0; i < numPhones; i++) {
            mCarrier[i] = (TextView) findViewById(mResId[i]);
            // Required for Marquee to work
            mCarrier[i].setSelected(true);
            mCarrier[i].setTextColor(0xffffffff);
            mCarrier[i].setVisibility(View.VISIBLE);
			mStatus[i] = Status.Normal;
        }
        

        mDate = (TextView) findViewById(R.id.date);
        mStatus1 = (TextView) findViewById(R.id.status1);
        mStatus2 = (TextView) findViewById(R.id.status2);

        mScreenLocked = (TextView) findViewById(R.id.screenLocked);
        mSelector = (SlidingTab) findViewById(R.id.tab_selector);
        mSelector.setHoldAfterTrigger(true, false);
        mSelector.setLeftHintText(R.string.lockscreen_unlock_label);

        mEmergencyCallText = (TextView) findViewById(R.id.emergencyCallText);
        mEmergencyCallButton = (Button) findViewById(R.id.emergencyCallButton);
        mEmergencyCallButton.setText(R.string.lockscreen_emergency_call);

        mLockPatternUtils.updateEmergencyCallButtonState(mEmergencyCallButton);
        mEmergencyCallButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCallback.takeEmergencyCallAction(mCallSub);
            }
        });

        //PUK Input Add Start
        mPukButton = (Button) findViewById(R.id.pukCallButton);
        mPukButton.setText(R.string.keyguard_password_enter_puk_code);
        mPukButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCallback.goToUnlockScreen();
            }
        });
        //PUK Input Add End

        setFocusable(true);
        setFocusableInTouchMode(true);
        setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        mUpdateMonitor.registerInfoCallback(this);
        mUpdateMonitor.registerSimStateCallback(this);

        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        mSilentMode = isSilentMode();

        resetSlidingTab();
        registerObserver();

        mSelector.setOnTriggerListener(this);

        resetStatusInfo(updateMonitor);
        
        mPhoneStateListener = new PhoneStateListener[numPhones];
        
		for (int i = 0; i < numPhones; i++) {
			// register for phone state notifications.
			mPhoneStateListener[i] = getPhoneStateListener(i);
			((TelephonyManager) mContext
					.getSystemService(PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE,i))).listen(
					mPhoneStateListener[i],
					PhoneStateListener.LISTEN_SERVICE_STATE);
		}
		//add by longhai
		initValues();
    }

    private boolean isSilentMode() {
        return mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
    }

    private void updateRightTabResources() {
    	if(hasMissPhone||hasMissMms){
    		return;
    	}
        boolean vibe = mSilentMode
            && (mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE);

        mSelector.setRightTabResources(
                mSilentMode ? ( vibe ? R.drawable.ic_jog_dial_vibrate_on
                                     : R.drawable.ic_jog_dial_sound_off )
                            : R.drawable.ic_jog_dial_sound_on,
                mSilentMode ? R.drawable.jog_tab_target_yellow
                            : R.drawable.jog_tab_target_gray,
                mSilentMode ? R.drawable.jog_tab_bar_right_sound_on
                            : R.drawable.jog_tab_bar_right_sound_off,
                mSilentMode ? R.drawable.jog_tab_right_sound_on
                            : R.drawable.jog_tab_right_sound_off);
    }

    private void resetStatusInfo(KeyguardUpdateMonitor updateMonitor) {
        mShowingBatteryInfo = updateMonitor.shouldShowBatteryInfo();
        mPluggedIn = updateMonitor.isDevicePluggedIn();
        mBatteryLevel = updateMonitor.getBatteryLevel();
//        mStatus = getCurrentStatus(updateMonitor.getSimState());
//        updateLayout(mStatus);
        
        for (int i = 0; i < TelephonyManager.getPhoneCount(); i++) {
            mStatus[i] = getCurrentStatus(updateMonitor.getSimState(i));
            updateLayout(mStatus[i], i);
        }

        refreshBatteryStringAndIcon();
        refreshAlarmDisplay();

        mTimeFormat = DateFormat.getTimeFormat(getContext());
        mDateFormatString = getContext().getString(R.string.full_wday_month_day_no_year);
        refreshTimeAndDateDisplay();
        updateStatusLines();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && mEnableMenuKeyInLockScreen) {
            mCallback.goToUnlockScreen();
        }
        return false;
    }

    /** {@inheritDoc} */
    public void onTrigger(View v, int whichHandle) {
        if (whichHandle == SlidingTab.OnTriggerListener.LEFT_HANDLE) {
            mCallback.goToUnlockScreen();
			if (hasMissPhone && hasMissMms) {
				Intent it = new Intent(Intent.ACTION_VIEW);
                it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                it.setType("vnd.android.cursor.dir/calls");
                mContext.startActivity(it);
			}
        } else if (whichHandle == SlidingTab.OnTriggerListener.RIGHT_HANDLE) {
        	if(hasMissMms){
        		mCallback.goToUnlockScreen();
                Intent it = new Intent(INTENT_MISSMMS);
                it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(it);
        		return;
        	}else if(hasMissPhone){
        		mCallback.goToUnlockScreen();
        		Intent it = new Intent(Intent.ACTION_VIEW);
                it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                it.setType("vnd.android.cursor.dir/calls");
                mContext.startActivity(it);
        		return;
        	}
        	
            // toggle silent mode
            mSilentMode = !mSilentMode;
            
         // ************Modify by luning at01-07-01 begin************
            if (mSilentMode) {
//                final boolean vibe = (Settings.System.getInt(
//                    getContext().getContentResolver(),
//                    Settings.System.VIBRATE_IN_SILENT, 1) == 1);
//
//                mAudioManager.setRingerMode(vibe
//                    ? AudioManager.RINGER_MODE_VIBRATE
//                    : AudioManager.RINGER_MODE_SILENT);
                mAudioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                if (mAudioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER) == AudioManager.VIBRATE_SETTING_ON) {
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_OFF);
                  mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION,
                          AudioManager.VIBRATE_SETTING_OFF);
                }
            } else {
                mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            }
                      
//            if(mSilentMode)
//        	{
//        		//change to profiles mode:silent
//        		String currMode = mAudioManager.getCurrProfilesMode(mContext);
//        		mAudioManager.saveLastProfilesMode(mContext,currMode);
//        		mAudioManager.saveCurrProfilesMode(mContext, Settings.System.PROFILES_MODE_SILENT);
//    			mAudioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
//        	}
//        	else
//        	{
//        		//restore last profiles mode
//        		String lastMode = mAudioManager.getLastProfilesMode(mContext);
//        		String currMode = mAudioManager.getCurrProfilesMode(mContext);
//        		mAudioManager.saveCurrProfilesMode(mContext, lastMode);
//        		mAudioManager.saveLastProfilesMode(mContext,currMode);
//
//        		//restore last vibrateSetting
//        		int callsVibrateSetting;
//        		if(lastMode.equals(Settings.System.PROFILES_MODE_OUTDOOR)||lastMode.equals(Settings.System.PROFILES_MODE_MEETING))
//        		{
//
//            		callsVibrateSetting = AudioManager.VIBRATE_SETTING_ON;
//        		}
//        		else
//        		{
//        			callsVibrateSetting = AudioManager.VIBRATE_SETTING_OFF;
//        		}
//        		mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
//        				callsVibrateSetting);
//        		mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION,
//        				callsVibrateSetting);
//
//
//        		//restore last phone volume
//        		int lastVolume = mAudioManager.getProfilesVolume(mContext, lastMode, 5);
//        		mAudioManager.setStreamVolume(AudioManager.STREAM_RING, lastVolume, 0);
//
//        		//sync phone call volume
//        		mAudioManager.synPhoneVolume(mContext,lastVolume);
//
//        		//restore last ring mode
//        		mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
//        	}
             		
         // ************Modify by luning at01-07-01 end************   
    		
            updateRightTabResources();

            String message = mSilentMode ?
                    getContext().getString(R.string.global_action_silent_mode_on_status) :
                    getContext().getString(R.string.global_action_silent_mode_off_status);

            final int toastIcon = mSilentMode
                ? R.drawable.ic_lock_ringer_off
                : R.drawable.ic_lock_ringer_on;

            final int toastColor = mSilentMode
                ? getContext().getResources().getColor(R.color.keyguard_text_color_soundoff)
                : getContext().getResources().getColor(R.color.keyguard_text_color_soundon);
            toastMessage(mScreenLocked, message, toastColor, toastIcon);
            mCallback.pokeWakelock();
        }
    }
 
    
    /** {@inheritDoc} */
    public void onGrabbedStateChange(View v, int grabbedState) {

		if (grabbedState != SlidingTab.OnTriggerListener.NO_HANDLE) {
			mCallback.pokeWakelock(ON_KEYDOWN_TIMEOUT_MS);
		} else {
			if (mCallback != null) {
				mCallback.pokeWakelock();
			}
		}

    	if(hasMissPhone||hasMissMms){
    		return;
    	}
        if (grabbedState == SlidingTab.OnTriggerListener.RIGHT_HANDLE) {
            mSilentMode = isSilentMode();
            mSelector.setRightHintText(mSilentMode ? R.string.lockscreen_sound_on_label
                    : R.string.lockscreen_sound_off_label);
        }
//        if(grabbedState == SlidingTab.OnTriggerListener.LEFT_HANDLE){
//        	 mSelector.setLeftHintText(R.string.lockscreen_unlock_label);
//        }

    }

    /**
     * Displays a message in a text view and then restores the previous text.
     * @param textView The text view.
     * @param text The text.
     * @param color The color to apply to the text, or 0 if the existing color should be used.
     * @param iconResourceId The left hand icon.
     */
    private void toastMessage(final TextView textView, final String text, final int color, final int iconResourceId) {
        if (mPendingR1 != null) {
            textView.removeCallbacks(mPendingR1);
            mPendingR1 = null;
        }
        if (mPendingR2 != null) {
            mPendingR2.run(); // fire immediately, restoring non-toasted appearance
            textView.removeCallbacks(mPendingR2);
            mPendingR2 = null;
        }

        final String oldText = textView.getText().toString();
        final ColorStateList oldColors = textView.getTextColors();

        mPendingR1 = new Runnable() {
            public void run() {
                textView.setText(text);
                if (color != 0) {
                    textView.setTextColor(color);
                }
                textView.setCompoundDrawablesWithIntrinsicBounds(iconResourceId, 0, 0, 0);
            }
        };

        textView.postDelayed(mPendingR1, 0);
        mPendingR2 = new Runnable() {
            public void run() {
                textView.setText(oldText);
                textView.setTextColor(oldColors);
                textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            }
        };
        textView.postDelayed(mPendingR2, 3500);
    }
    private Runnable mPendingR1;
    private Runnable mPendingR2;

    private void refreshAlarmDisplay() {
        mNextAlarm = mLockPatternUtils.getNextAlarm();
        if (mNextAlarm != null) {
            mAlarmIcon = getContext().getResources().getDrawable(R.drawable.ic_lock_idle_alarm);
        }
        updateStatusLines();
    }

    /** {@inheritDoc} */
    public void onRefreshBatteryInfo(boolean showBatteryInfo, boolean pluggedIn,
            int batteryLevel) {
        if (DBG) Log.d(TAG, "onRefreshBatteryInfo(" + showBatteryInfo + ", " + pluggedIn + ")");
        mShowingBatteryInfo = showBatteryInfo;
        mPluggedIn = pluggedIn;
        mBatteryLevel = batteryLevel;

        refreshBatteryStringAndIcon();
        updateStatusLines();
    }

    private void refreshBatteryStringAndIcon() {
        if (!mShowingBatteryInfo) {
            mCharging = null;
            return;
        }

        if (mChargingIcon == null) {
            mChargingIcon =
                    getContext().getResources().getDrawable(R.drawable.ic_lock_idle_charging);
        }

        if (mPluggedIn) {
            if (mUpdateMonitor.isDeviceCharged()) {
                mCharging = getContext().getString(R.string.lockscreen_charged);
            } else {
                mCharging = getContext().getString(R.string.lockscreen_plugged_in, mBatteryLevel);
            }
        } else {
            mCharging = getContext().getString(R.string.lockscreen_low_battery);
        }
    }

    /** {@inheritDoc} */
    public void onTimeChanged() {
        refreshTimeAndDateDisplay();
    }

    private void refreshTimeAndDateDisplay() {
        mDate.setText(DateFormat.format(mDateFormatString, new Date()));
    }

    private void updateStatusLines() {
        if ((mCharging == null && mNextAlarm == null)) {
            mStatus1.setVisibility(View.INVISIBLE);
            mStatus2.setVisibility(View.INVISIBLE);
        } else if (mCharging != null && mNextAlarm == null) {
            // charging only
            mStatus1.setVisibility(View.VISIBLE);
            mStatus2.setVisibility(View.INVISIBLE);

            mStatus1.setText(mCharging);
            mStatus1.setCompoundDrawablesWithIntrinsicBounds(mChargingIcon, null, null, null);
        } else if (mNextAlarm != null && mCharging == null) {
            // next alarm only
            mStatus1.setVisibility(View.VISIBLE);
            mStatus2.setVisibility(View.INVISIBLE);

            mStatus1.setText(mNextAlarm);
            mStatus1.setCompoundDrawablesWithIntrinsicBounds(mAlarmIcon, null, null, null);
        } else if (mCharging != null && mNextAlarm != null) {
            // both charging and next alarm
            mStatus1.setVisibility(View.VISIBLE);
            mStatus2.setVisibility(View.VISIBLE);

            mStatus1.setText(mCharging);
            mStatus1.setCompoundDrawablesWithIntrinsicBounds(mChargingIcon, null, null, null);
            mStatus2.setText(mNextAlarm);
            mStatus2.setCompoundDrawablesWithIntrinsicBounds(mAlarmIcon, null, null, null);
        }
    }

    /** {@inheritDoc} */
    public void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn, int phoneId) {
        if (DBG) Log.d(TAG, "onRefreshCarrierInfo(" + plmn + ", " + spn + ")");
        updateLayout(mStatus[phoneId],phoneId);
    }

    /**
     * Determine the current status of the lock screen given the sim state and other stuff.
     */
    private Status getCurrentStatus(IccCard.State simState) {
        boolean missingAndNotProvisioned = (!mUpdateMonitor.isDeviceProvisioned()
                && simState == IccCard.State.ABSENT);
        if (missingAndNotProvisioned) {
            return Status.SimMissingLocked;
        }

        switch (simState) {
            case ABSENT:
                return Status.SimMissing;
            case NETWORK_LOCKED:
                return Status.NetworkLocked;
            case NOT_READY:
                return Status.SimMissing;
            case PIN_REQUIRED:
                return Status.SimLocked;
            case PUK_REQUIRED:
                return Status.SimPukLocked;
            case SIM_LOCKED:
                return Status.CardLocked;
            case READY:
                return Status.Normal;
            case UNKNOWN:
                return Status.SimMissing;
        }
        return Status.SimMissing;
    }

    /**
     * Update the layout to match the current status.
     */
    private void updateLayout(Status status,int phoneId) {
        // The emergency call button no longer appears on this screen.
        if (true) Log.i(TAG, "updateLayout: status=" + status+",mCarrier= "
        +getSprdCarrierString(mUpdateMonitor.getTelephonyPlmn(phoneId),mUpdateMonitor.getTelephonySpn(phoneId))
        +",radioType="+mUpdateMonitor.getRadioType()+",phoneId="+phoneId);

        mEmergencyCallButton.setVisibility(View.VISIBLE); // in almost all cases
        //PUK Input Add Start
        mPukButton.setVisibility(View.GONE);
        //PUK Input Add End
        mIccText = getCurrentText(phoneId);
        //modify by liguxiang 08-25-11 for display radiotype(3G) on LockScreen begin
        //change getCarrierString to getSprdCarrierString
        int subscription = phoneId;
        switch (status) {
            case Normal:
                // text
//                mCarrier.setText(
//                		getSprdCarrierString(
//                                mUpdateMonitor.getTelephonyPlmn(),
//                                mUpdateMonitor.getTelephonySpn()));
			mCarrier[subscription].setVisibility(View.VISIBLE);
				CharSequence carrierText = getCarrierString(
						mUpdateMonitor.getTelephonyPlmn(subscription),
						mUpdateMonitor.getTelephonySpn(subscription));
				if ("".equals(carrierText)) {
					mCarrier[subscription].setText(mContext
							.getResources().getText(
									R.string.lockscreen_carrier_default));
				} else {
					mCarrier[subscription].setText(carrierText+mUpdateMonitor.getNetworkType(subscription).toString());
				}

                // Empty now, but used for sliding tab feedback
                mScreenLocked.setText("");

                // layout
                mScreenLocked.setVisibility(View.VISIBLE);
                mSelector.setVisibility(View.VISIBLE);
                mEmergencyCallText.setVisibility(View.GONE);
                break;
            case NetworkLocked:
                // The carrier string shows both sim card status (i.e. No Sim Card) and
                // carrier's name and/or "Emergency Calls Only" status
           
                //Modify start on 2012-01-17
//                mCarrier.setText(
//                		getSprdCarrierString(
//                                mUpdateMonitor.getTelephonyPlmn(),
//                                getContext().getText(R.string.lockscreen_network_locked_message)));
            	//mCarrier.setText(getContext().getText(R.string.lockscreen_network_locked_message));
			mCarrier[subscription].setVisibility(View.VISIBLE);
            	mCarrier[subscription].setText(mIccText.networkLockedMessage);
                //Modify end on 2012-01-17

                // layout
                mScreenLocked.setVisibility(View.VISIBLE);
                mSelector.setVisibility(View.VISIBLE);
                mEmergencyCallText.setVisibility(View.GONE);
                break;
            case SimMissing:
                // text
                //mCarrier.setText(R.string.lockscreen_missing_sim_message_short);
            	mCarrier[subscription].setText(mIccText.iccMissingMessageShort);
                mScreenLocked.setText(R.string.lockscreen_missing_sim_instructions);

                // layout
                mSelector.setVisibility(View.VISIBLE);
                mCarrier[subscription].setVisibility(View.VISIBLE);
				mScreenLocked.setVisibility(View.VISIBLE);
				mEmergencyCallText.setVisibility(View.VISIBLE);
				if (numPhones > 1) {
					Log.d(TAG, "mStatus[0]="+mStatus[0]+"  mStatus[1]="+mStatus[1]);
					if (mStatus[0] == status.Normal || mStatus[1] == status.Normal) {
						mCarrier[subscription].setVisibility(View.GONE);
						mScreenLocked.setVisibility(View.GONE);
						mEmergencyCallText.setVisibility(View.GONE);
					} else if (mStatus[0] != status.SimMissing
							|| mStatus[1] != status.SimMissing) {
						mCarrier[subscription].setVisibility(View.GONE);
						mScreenLocked.setVisibility(View.GONE);
					}else {
						mCarrier[0].setVisibility(View.VISIBLE);
						mCarrier[1].setVisibility(View.VISIBLE);
						mScreenLocked.setVisibility(View.VISIBLE);
						mEmergencyCallText.setVisibility(View.VISIBLE);
					}
				}
                // do not need to show the e-call button; user may unlock
                break;
            case SimMissingLocked:
                // text
                //Modify start on 2012-01-17
//                mCarrier.setText(
//                		getSprdCarrierString(
//                                mUpdateMonitor.getTelephonyPlmn(),
//                                getContext().getText(R.string.lockscreen_missing_sim_message_short)));
            	//mCarrier.setText(getContext().getText(R.string.lockscreen_missing_sim_message_short));
            	mCarrier[subscription].setText(mIccText.iccMissingMessageShort);
                //Modify end on 2012-01-17

                mScreenLocked.setText(R.string.lockscreen_missing_sim_instructions);

                // layout
                mScreenLocked.setVisibility(View.VISIBLE);
                mSelector.setVisibility(View.VISIBLE); // cannot unlock
                mEmergencyCallText.setVisibility(View.VISIBLE);
                mEmergencyCallButton.setVisibility(View.VISIBLE);
                break;
            case SimLocked:
                // text
            	
                //Modify start on 2012-01-17
//                mCarrier.setText(
//                		getSprdCarrierString(
//                                mUpdateMonitor.getTelephonyPlmn(),
//                                getContext().getText(R.string.lockscreen_sim_locked_message)));
            	//mCarrier.setText(getContext().getText(R.string.lockscreen_sim_locked_message));
            	mCarrier[subscription].setText(mIccText.iccPinLockedMessage);
                //Modify end on 2012-01-17

                // layout
                mScreenLocked.setVisibility(View.INVISIBLE);
                mSelector.setVisibility(View.VISIBLE);
                mEmergencyCallText.setVisibility(View.VISIBLE);
                if (numPhones > 1) {
                	if (mStatus[0] == status.Normal || mStatus[1] == status.Normal) {
						mEmergencyCallText.setVisibility(View.GONE);
					}
                }
                break;
            case SimPukLocked:
                // text

            	//Modify start on 2012-01-17
//                mCarrier.setText(
//                		getSprdCarrierString(
//                                mUpdateMonitor.getTelephonyPlmn(),
//                                getContext().getText(R.string.lockscreen_sim_puk_locked_message)));
                //mCarrier.setText(getContext().getText(R.string.lockscreen_sim_puk_locked_message));
            	mCarrier[subscription].setText(mIccText.iccPukLockedMessage);
                //Modify end on 2012-01-17

                mScreenLocked.setText(R.string.lockscreen_sim_puk_locked_instructions);

                // layout
                mScreenLocked.setVisibility(View.VISIBLE);
                mSelector.setVisibility(View.VISIBLE); // cannot unlock
                mEmergencyCallText.setVisibility(View.VISIBLE);
                mEmergencyCallButton.setVisibility(View.VISIBLE);
                //PUK Input Add Start
                mPukButton.setVisibility(View.VISIBLE);
                //PUK Input Add End
                if (numPhones > 1) {
                	if (mStatus[0] == status.Normal || mStatus[1] == status.Normal) {
						mEmergencyCallText.setVisibility(View.GONE);
					}
                }
                break;
            case CardLocked:
                mCarrier[subscription].setVisibility(View.VISIBLE);
                mCarrier[subscription].setText(mIccText.iccSimCardLockedMessage);
                mScreenLocked.setVisibility(View.VISIBLE);
                mSelector.setVisibility(View.VISIBLE);
                mEmergencyCallText.setVisibility(View.VISIBLE);
                mEmergencyCallButton.setVisibility(View.VISIBLE);
                break;
        }
        //modify by liguxiang 08-25-11 for display radiotype(3G) on LockScreen end
    }

    static CharSequence getCarrierString(CharSequence telephonyPlmn, CharSequence telephonySpn) {
        if (telephonyPlmn != null && telephonySpn == null) {
            return telephonyPlmn;
        } else if (telephonyPlmn != null && telephonySpn != null) {
            return telephonyPlmn;
        } else if (telephonyPlmn == null && telephonySpn != null) {
            return telephonySpn;
        } else {
            return "";
        }
    }
    
    //add by liguxiang 08-25-11 for display radiotype(3G) on LockScreen begin
    public  CharSequence getSprdCarrierString(CharSequence telephonyPlmn, CharSequence telephonySpn) {
        CharSequence radioType = mUpdateMonitor.getRadioType();
        Log.i(TAG, "getSprdCarrierString:"+" telephonyPlmn="+telephonyPlmn+" telephonySpn="+telephonySpn+" radioType="+radioType);

        if (telephonyPlmn != null && telephonySpn == null) {
//       	if(radioType != null){
//       		telephonyPlmn = telephonyPlmn.toString() + " " + radioType;
//        	}
            if(radioType != null){
                telephonyPlmn = telephonyPlmn.toString() + " " + radioType;
            }

            return telephonyPlmn;
        } else if (telephonyPlmn != null && telephonySpn != null) {
        	//if(radioType != null){
        		//telephonySpn = telephonySpn.toString() + radioType;
        	//}
               // return telephonyPlmn + "|" + telephonySpn;
            if(radioType != null){
                telephonyPlmn = telephonyPlmn.toString() + radioType;
            }
            //Modify start on 2012-01-16 for 8930/8932
            //return telephonyPlmn + "|" + telephonySpn;
            return telephonyPlmn;
            //Modify end on 2012-01-16 for 8930/8932
        } else if (telephonyPlmn == null && telephonySpn != null) {
        	//if(radioType != null){
        		//telephonySpn = telephonySpn.toString() + " " + radioType;
        	//}
            //return telephonySpn;
            if(radioType != null){
                telephonySpn = telephonySpn.toString() + " " + radioType;
            }
            return telephonySpn;
        } else {
            return "";
        }
    }
    //add by liguxiang 08-25-11 for display radio type on LockScreen end

    public void onSimStateChanged(IccCard.State simState,int phoneId) {
        if (DBG) Log.d(TAG, "onSimStateChanged(" + simState + ")");
        mStatus[phoneId] = getCurrentStatus(simState);
        updateLayout(mStatus[phoneId],phoneId);
        updateStatusLines();
    }

    void updateConfiguration() {
        Configuration newConfig = getResources().getConfiguration();
        if (newConfig.orientation != mCreationOrientation) {
            mCallback.recreateMe(newConfig);
        } else if (newConfig.hardKeyboardHidden != mKeyboardHidden) {
            mKeyboardHidden = newConfig.hardKeyboardHidden;
            final boolean isKeyboardOpen = mKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO;
            if (mUpdateMonitor.isKeyguardBypassEnabled() && isKeyboardOpen) {
                mCallback.goToUnlockScreen();
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (LockPatternKeyguardView.DEBUG_CONFIGURATION) {
            Log.v(TAG, "***** LOCK ATTACHED TO WINDOW");
            Log.v(TAG, "Cur orient=" + mCreationOrientation
                    + ", new config=" + getResources().getConfiguration());
        }
        updateConfiguration();
    }

    /** {@inheritDoc} */
    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (LockPatternKeyguardView.DEBUG_CONFIGURATION) {
            Log.w(TAG, "***** LOCK CONFIG CHANGING", new RuntimeException());
            Log.v(TAG, "Cur orient=" + mCreationOrientation
                    + ", new config=" + newConfig);
        }
        updateConfiguration();
    }

    /** {@inheritDoc} */
    public boolean needsInput() {
        return false;
    }

    /** {@inheritDoc} */
    public void onPause() {
    	for (int i = 0; i < numPhones; i++) {
			((TelephonyManager) mContext
					.getSystemService(PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE,i))).listen(
					mPhoneStateListener[i],
					PhoneStateListener.LISTEN_NONE);
		}
    }

    /** {@inheritDoc} */
    public void onResume() {
    	for (int i = 0; i < numPhones; i++) {
			// register for phone state notifications.
			((TelephonyManager) mContext
					.getSystemService(PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE,i))).listen(
					mPhoneStateListener[i],
					PhoneStateListener.LISTEN_SERVICE_STATE);
		}
	mSelector.reset(false);//clear animate when press canell button 2012-2-7
        resetStatusInfo(mUpdateMonitor);
        mLockPatternUtils.updateEmergencyCallButtonState(mEmergencyCallButton);
        updateSlidingTab();
    }

    /** {@inheritDoc} */
    public void cleanUp() {
    	if(DBG){
    		Log.d(TAG, "cleanUp....");
    	}
    	for (int i = 0; i < numPhones; i++) {
			((TelephonyManager) mContext
					.getSystemService(PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE,i))).listen(
					mPhoneStateListener[i],
					PhoneStateListener.LISTEN_NONE);
		}
        mUpdateMonitor.removeCallback(this); // this must be first
        mLockPatternUtils = null;
        mUpdateMonitor = null;
        mCallback = null;
        unregisterObserver();
        if (mLeftBitmap != null && !mLeftBitmap.isRecycled()) {
            mLeftBitmap.recycle();
        } else if (mRightBitmap != null && !mRightBitmap.isRecycled()) {
            mRightBitmap.recycle();
        }
    }

    /** {@inheritDoc} */
    public void onRingerModeChanged(int state) {
        boolean silent = AudioManager.RINGER_MODE_NORMAL != state;
        if (silent != mSilentMode) {
            mSilentMode = silent;
            updateRightTabResources();
        }
    }
    
    private PhoneStateListener getPhoneStateListener(int subscription) {
    	//add by liguxiang 08-25-11 for display radiotype(3G) on LockScreen begin
    	return new PhoneStateListener(subscription) {
    		@Override
    		public void onServiceStateChanged(ServiceState state) {
    			if(mUpdateMonitor!=null && state!=null){
    				mUpdateMonitor.setRadioType(state.getRadioTechnology());
    				updateLayout(mStatus[mSubscription],mSubscription);
    			}
    		}
    		
    	};
    	//add by liguxiang 08-25-11 for display radiotype(3G) on LockScreen end
    }

    public void onPhoneStateChanged(String newState,int sub) {
		mCallSub = sub;
        mLockPatternUtils.updateEmergencyCallButtonState(mEmergencyCallButton,sub);
    }

    //add start
    private class IccText {
        int iccPukLockedMessage;
        int iccPukLockedInstructions;
        int iccMissingMessage;
        int iccMissingInstructions;
        int iccErrorMessage;
        int iccInstructionsWhenPatternEnabled;
        int iccInstructionsWhenPatternDisabled;
        int iccPinLockedMessage;
        int iccMissingMessageShort;
        int iccErrorMessageShort;
        int networkLockedMessage;
        int iccBlockMessageShort;
        int iccSimCardLockedMessage;
    }
    private IccText createSimText() {
        IccText simText = new IccText();
        simText.iccPukLockedMessage = R.string.lockscreen_sim_puk_locked_message;
        simText.iccPukLockedInstructions = R.string.lockscreen_sim_puk_locked_instructions;
        simText.iccMissingMessage = R.string.lockscreen_missing_sim_message;
        simText.iccMissingInstructions = R.string.lockscreen_missing_sim_instructions;
        simText.iccErrorMessage = R.string.lockscreen_sim_error_message;
        simText.iccInstructionsWhenPatternEnabled = R.string.lockscreen_instructions_when_pattern_enabled;
        simText.iccInstructionsWhenPatternDisabled = R.string.lockscreen_instructions_when_pattern_disabled;
        simText.iccPinLockedMessage = R.string.lockscreen_pin_locked_message;
        simText.iccMissingMessageShort = R.string.lockscreen_missing_sim_message_short;
        simText.iccErrorMessageShort = R.string.lockscreen_sim_error_message_short;
        simText.networkLockedMessage = R.string.lockscreen_sim_network_locked_message;
        simText.iccBlockMessageShort = R.string.lockscreen_blocked_sim_message_short;
        simText.iccSimCardLockedMessage = R.string.lockscreen_sim_card_locked_message;
        return simText;
    }

    private IccText createRuimText() {
        IccText ruimText = new IccText();
        ruimText.iccPukLockedMessage = R.string.lockscreen_ruim_puk_locked_message;
        ruimText.iccPukLockedInstructions = R.string.lockscreen_ruim_puk_locked_instructions;
        ruimText.iccMissingMessage = R.string.lockscreen_missing_ruim_message;
        ruimText.iccMissingInstructions = R.string.lockscreen_missing_ruim_instructions;
        ruimText.iccErrorMessage = R.string.lockscreen_ruim_error_message;
        ruimText.iccInstructionsWhenPatternEnabled = R.string.lockscreen_instructions_when_pattern_enabled;
        ruimText.iccInstructionsWhenPatternDisabled = R.string.lockscreen_instructions_when_pattern_disabled;
        ruimText.iccPinLockedMessage = R.string.lockscreen_pin_locked_message;
        ruimText.iccMissingMessageShort = R.string.lockscreen_missing_ruim_message_short;
        ruimText.iccErrorMessageShort = R.string.lockscreen_ruim_error_message_short;
        ruimText.networkLockedMessage = R.string.lockscreen_ruim_network_locked_message;
        ruimText.iccBlockMessageShort = R.string.lockscreen_blocked_ruim_message_short;
        return ruimText;
    }
    private IccText getCurrentText(int subscription) {
        int activePhoneType;
        try {
            activePhoneType = TelephonyManager.getDefault(subscription).getPhoneType();
        } catch (Exception e) {
            Log.e(TAG, "Exception occured while trying to get the phone.");
            activePhoneType = TelephonyManager.getDefault().getPhoneType();
        }
        boolean isGsm = TelephonyManager.PHONE_TYPE_GSM == activePhoneType;
        Log.d(TAG, "Updating Lock Screen text to " + (isGsm ? "Sim" : "Ruim"));
        if (isGsm) {
            if (mSimText == null) {
                mSimText = createSimText();
            }
            return mSimText;
        } else {
            if (mRuimText == null) {
                mRuimText = createRuimText();
            }
            return mRuimText;
        }
    }
    //add end
	private class CObserver extends ContentObserver {
		
		public CObserver(){
			super(new Handler());
		}
		 @Override
		public void onChange(boolean selfChange) {
			if (DBG) {
				Log.d(TAG, "ContentObserver onChange...");
			}
			updateSlidingTab();
		}
	};
	
	private void registerObserver() {
		smsObserver = new CObserver();
		mmsObserver = new CObserver();
		callObserver = new CObserver();
		mContext.getContentResolver().registerContentObserver(
				Sms.Inbox.CONTENT_URI, true, smsObserver);
		mContext.getContentResolver().registerContentObserver(
				Mms.Inbox.CONTENT_URI, true, mmsObserver);
		mContext.getContentResolver().registerContentObserver(
				CallLog.Calls.CONTENT_URI, true, callObserver);
	}
	
	private void unregisterObserver() {
		if (smsObserver != null) {
			mContext.getContentResolver()
					.unregisterContentObserver(smsObserver);
		}
		if (callObserver != null) {
			mContext.getContentResolver()
					.unregisterContentObserver(callObserver);
		}
		if (mmsObserver != null) {
			mContext.getContentResolver()
					.unregisterContentObserver(mmsObserver);
		}
	}
	
	
	private void updateSlidingTab() {
		int missPhoneCount = getMissCallCount();
		int missMmsCount = getUnReadSmsCount()+getUnReadMmsCount();
		hasMissPhone = missPhoneCount > 0;
		hasMissMms = missMmsCount > 0;
		if (DBG) {
			Log.d(TAG, "hasMissPhone=" + hasMissPhone + " hasMissMms="
					+ hasMissMms);
		}
		if (hasMissPhone && hasMissMms) {
			mSelector.setLeftHintText(R.string.lockscreen_miss_phone);
			mSelector.setRightHintText(R.string.lockscreen_miss_mms);
			mSelector.setRightIcon(createBitmap(false,
					R.drawable.ic_message_dial_unlock, missMmsCount));
			mSelector.setLeftIcon(createBitmap(true,
					R.drawable.ic_phone_dial_unlock, missPhoneCount));
		} else if (hasMissMms) {
			mSelector.setRightHintText(R.string.lockscreen_miss_mms);
			mSelector.setRightIcon(createBitmap(false,
					R.drawable.ic_message_dial_unlock, missMmsCount));
		} else if (hasMissPhone) {
			mSelector.setRightHintText(R.string.lockscreen_miss_phone);
			mSelector.setRightIcon(createBitmap(false,
					R.drawable.ic_phone_dial_unlock, missPhoneCount));
		} else {
			resetSlidingTab();
		}

	}
	
	private void resetSlidingTab() {
		mSelector.setLeftTabResources(R.drawable.ic_jog_dial_unlock,
				R.drawable.jog_tab_target_green,
				R.drawable.jog_tab_bar_left_unlock,
				R.drawable.jog_tab_left_unlock);

		updateRightTabResources();
	}
	
    private void initValues() {
        mPaint.setAntiAlias(true);
        mPaint.setColor(Color.WHITE);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        final float density = metrics.density;
        mFewTextSize = 14 * density;
        mManytextSize = 18 * density;
    }

    private Bitmap createBitmap(boolean isLeft ,int resId,int count) {
//        if (isLeft && mLeftBitmap != null && !mLeftBitmap.isRecycled()) {
//            mLeftBitmap.recycle();
//        } else if (mRightBitmap != null && !mRightBitmap.isRecycled()) {
//            mRightBitmap.recycle();
//        }
        Bitmap iconBitmap = BitmapFactory.decodeResource(getResources(), resId).copy(Bitmap.Config.ARGB_8888, true);
        Canvas cv = new Canvas(iconBitmap);
        Bitmap countBp =  drawSubBitmap(getContext(),count);
        cv.drawBitmap(countBp, iconBitmap.getWidth() - countBp.getWidth(), 0, mPaint);
        if(isLeft){
            mLeftBitmap = iconBitmap;
        }else {
            mRightBitmap = iconBitmap;
        }
        countBp.recycle();
        return iconBitmap;
    }

    private Bitmap drawSubBitmap(Context context, int drawText) {
        String endstr = drawText < MAX_SIZE ? ("" + drawText) : INFINITY_UNICODE;
        if (drawText < MAX_SIZE) {
            mPaint.setTextSize(mFewTextSize);
        } else {
            mPaint.setTextSize(mManytextSize);
        }
        Drawable mIcon = getResources().getDrawable(R.drawable.ic_count_dial_unlock);

        int width = mIcon.getIntrinsicWidth();
        int height = mIcon.getIntrinsicHeight();

        int textWidth = (int) mPaint.measureText(endstr, 0, endstr.length());
        if (textWidth > width) {
            width = textWidth + 6;
        }

        final Bitmap thumb = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas canvas = sCanvas;
        canvas.setBitmap(thumb);
        Rect r = new Rect(0, 0, width, height);
        mIcon.setBounds(r);
        mIcon.draw(canvas);
        int x = (int) ((width - textWidth) * 0.5);
        FontMetrics fm = mPaint.getFontMetrics();
        int y = (int) ((height - (fm.descent - fm.ascent)) * 0.5 - fm.ascent);
        canvas.drawText(endstr, x, y-mOffsetY, mPaint);
        return thumb;
    }
    
    private int getMissCallCount() {
		Cursor cur = null;
		int unRead = 3;
		int black = unRead + 1;
		try {
			ContentResolver cr = mContext.getContentResolver();
			cur = cr.query(CallLog.Calls.CONTENT_URI, new String[] {
					CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
					CallLog.Calls.DATE, CallLog.Calls.NEW }, "(type = "
					+ unRead + " AND new = 1)", null, "calls.date desc");// limit
			// ?distinct?
			if (cur != null) {
				return cur.getCount();
			}
		} catch (SQLiteException ex) {
			Log.d("SQLiteException in getMissCallCount", ex.getMessage());
		} finally {
			if (cur != null && !cur.isClosed()) {
				cur.close();
			}
		}
		return 0;
	}

    private int getUnReadMmsCount() {
		String selection = "(read=0 AND m_type != 134)";
		Cursor cur = null;
		try {
			ContentResolver cr = mContext.getContentResolver();
			cur = cr.query(Mms.Inbox.CONTENT_URI, new String[] {
					BaseColumns._ID,Mms.DATE }, selection, null, "date desc");// limit
			if (cur != null) {
				return cur.getCount();
			}
		} catch (SQLiteException ex) {
			Log.e(TAG, "SQLiteException in getUnReadMmsCount" + ex.getMessage());
		} finally {
			if (cur != null && !cur.isClosed()) {
				cur.close();
			}
		}
		return 0;
	}

    private int getUnReadSmsCount() {
		String selection = "(read=0 OR seen=0)";
		Cursor cur = null;
		try {
			ContentResolver cr = mContext.getContentResolver();
			cur = cr.query(Sms.Inbox.CONTENT_URI, new String[] {
					BaseColumns._ID, Sms.ADDRESS, Sms.PERSON, Sms.BODY,
					Sms.DATE }, selection, null, "date desc");// limit
			if (cur != null) {
				return cur.getCount();
			}
		} catch (SQLiteException ex) {
			Log.e(TAG, "SQLiteException in getUnReadSmsCount" + ex.getMessage());
		} finally {
			if (cur != null && !cur.isClosed()) {
				cur.close();
			}
		}
		return 0;
	}

	@Override
	public void onPhoneStateChanged(String newState) {
		// TODO Auto-generated method stub
		mLockPatternUtils.updateEmergencyCallButtonState(mEmergencyCallButton);
	}
}
