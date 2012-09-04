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

import android.app.AlertDialog;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.R;
import com.android.internal.app.ShutdownThread;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.google.android.collect.Lists;

import java.util.ArrayList;

/**
 * Helper to show the global actions dialog.  Each item is an {@link Action} that
 * may show depending on whether the keyguard is showing, and whether the device
 * is provisioned.
 */
class GlobalActions implements DialogInterface.OnDismissListener, DialogInterface.OnClickListener  {

    private static final String TAG = "GlobalActions";

    private StatusBarManager mStatusBar;

    private final Context mContext;
    private final AudioManager mAudioManager;

    private ArrayList<Action> mItems;
    private AlertDialog mDialog;

    private ToggleAction mSilentModeToggle;
    private ToggleAction mAirplaneModeOn;

    private MyAdapter mAdapter;
    private TelephonyManager[] mTelephonyManagers;
    private boolean mKeyguardShowing = false;
    private boolean mDeviceProvisioned = false;
    private ToggleAction.State mAirplaneState = ToggleAction.State.Off;
    private boolean mIsWaitingForEcmExit = false;

    private boolean[] isStandby;
    private boolean[] hasCardReady;
    private int mPhoneCount = 1;
    /**
     * @param context everything needs a context :(
     */
    public GlobalActions(Context context) {
        mContext = context;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mPhoneCount = PhoneFactory.getPhoneCount();
        mTelephonyManagers = new TelephonyManager[mPhoneCount];
        isStandby=new boolean[mPhoneCount];
        hasCardReady=new boolean[mPhoneCount];
        // receive broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
        // add by niezhong 0907 for NEWMS00120274 begin
        filter.addAction("com.android.contacts.SIM_OPERATE_END_PUT");
        filter.addAction("com.android.contacts.SIM_OPERATE_START_PUT");
        // add by niezhong 0907 for NEWMS00120274 end
        context.registerReceiver(mBroadcastReceiver, filter);

        // get notified of phone state changes
        for (int i = 0; i <mPhoneCount; i++) {
            mTelephonyManagers[i] = (TelephonyManager) mContext.getSystemService(PhoneFactory
                    .getServiceName(Context.TELEPHONY_SERVICE, i));
            mTelephonyManagers[i].listen(mPhoneStateListener(i), PhoneStateListener.LISTEN_SERVICE_STATE);
        }
    }

    /**
     * Show the global actions dialog (creating if necessary)
     * @param keyguardShowing True if keyguard is showing
     */
    public void showDialog(boolean keyguardShowing, boolean isDeviceProvisioned) {
        mKeyguardShowing = keyguardShowing;
        mDeviceProvisioned = isDeviceProvisioned;
        if (mDialog == null) {
            mStatusBar = (StatusBarManager)mContext.getSystemService(Context.STATUS_BAR_SERVICE);
            mDialog = createDialog();
        }
        prepareDialog();

        mStatusBar.disable(StatusBarManager.DISABLE_EXPAND);
        mDialog.show();
        mDialog.getListView().setFocusable(false);
    }

    /**
     * Create the global actions dialog.
     * @return A new dialog.
     */
    private AlertDialog createDialog() {
        mSilentModeToggle = new ToggleAction(
                R.drawable.ic_lock_silent_mode,
                R.drawable.ic_lock_silent_mode_off,
                R.string.global_action_toggle_silent_mode,
                R.string.global_action_silent_mode_on_status,
                R.string.global_action_silent_mode_off_status) {

            void willCreate() {
                // XXX: FIXME: switch to ic_lock_vibrate_mode when available
                mEnabledIconResId = (Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.VIBRATE_IN_SILENT, 1) == 1)
                    ? R.drawable.ic_lock_silent_mode_vibrate
                    : R.drawable.ic_lock_silent_mode;
            }

            void onToggle(boolean on) {
            	// ************Modify by luning at01-07-01 begin************
                if (on) {
//                    mAudioManager.setRingerMode((Settings.System.getInt(mContext.getContentResolver(),
//                        Settings.System.VIBRATE_IN_SILENT, 1) == 1)
//                        ? AudioManager.RINGER_MODE_VIBRATE
//                        : AudioManager.RINGER_MODE_SILENT);
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
             
//            	if(on)
//            	{
//            		//change to profiles mode:silent
//            		String currMode = mAudioManager.getCurrProfilesMode(mContext);
//            		mAudioManager.saveLastProfilesMode(mContext,currMode);
//            		mAudioManager.saveCurrProfilesMode(mContext, Settings.System.PROFILES_MODE_SILENT);
//        			mAudioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
//            	}
//            	else
//            	{
//            		//restore last profiles mode
//            		String lastMode = mAudioManager.getLastProfilesMode(mContext);
//            		String currMode = mAudioManager.getCurrProfilesMode(mContext);
//            		mAudioManager.saveCurrProfilesMode(mContext, lastMode);
//            		mAudioManager.saveLastProfilesMode(mContext,currMode);
//
//            		//restore last vibrateSetting
//            		int callsVibrateSetting;
//            		if(lastMode.equals(Settings.System.PROFILES_MODE_OUTDOOR)||lastMode.equals(Settings.System.PROFILES_MODE_MEETING))
//            		{
//
//                		callsVibrateSetting = AudioManager.VIBRATE_SETTING_ON;
//            		}
//            		else
//            		{
//            			callsVibrateSetting = AudioManager.VIBRATE_SETTING_OFF;
//            		}
//            		mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
//            				callsVibrateSetting);
//            		mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION,
//            				callsVibrateSetting);
//
//
//            		//restore last phone volume
//            		int lastVolume = mAudioManager.getProfilesVolume(mContext, lastMode, 5);
//            		mAudioManager.setStreamVolume(AudioManager.STREAM_RING, lastVolume, 0);
//
//            		//sync phone call volume
//            		mAudioManager.synPhoneVolume(mContext,lastVolume);
//
//            		//restore last ring mode
//	        		mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
//            	}
            	// ************Modify by luning at01-07-01 end************
            }

            public boolean showDuringKeyguard() {
                return true;
            }

            public boolean showBeforeProvisioning() {
                return false;
            }
        };

        mAirplaneModeOn = new ToggleAction(
                R.drawable.ic_lock_airplane_mode,
                R.drawable.ic_lock_airplane_mode_off,
                R.string.global_actions_toggle_airplane_mode,
                R.string.global_actions_airplane_mode_on_status,
                R.string.global_actions_airplane_mode_off_status) {

            private boolean isCalling = false;

            void onToggle(boolean on) {
                if (on && (isCalling = isCalling())) {
                    Toast.makeText(mContext, R.string.cant_open_airplane_mode_when_phone_busy,
                            Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "mAirplaneModeOn onToggle(" + on + "), but phone is busy now");
                    return;
                }

                if (Boolean.parseBoolean(
                        SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE))) {
                    mIsWaitingForEcmExit = true;
                    // Launch ECM exit dialog
                    Intent ecmDialogIntent =
                            new Intent(TelephonyIntents.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS, null);
                    ecmDialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(ecmDialogIntent);
                } else {
                    changeAirplaneModeSystemSetting(on);
                }
            }

            @Override
            protected void changeStateFromPress(boolean buttonOn) {
                if (buttonOn && isCalling) {
                    return;
                }
                // In ECM mode airplane state cannot be changed
                if (!(Boolean.parseBoolean(SystemProperties
                        .get(TelephonyProperties.PROPERTY_INECM_MODE)))) {
                    if (isCardExist()) {
                        if (isCardActivated()) {
                            mState = buttonOn ? State.TurningOn : State.TurningOff;
                            mAirplaneState = mState;
                            if(mHandler.hasMessages(MESSAGE_REFLUSH_AIRPLANE_STATE)){
                                mHandler.removeMessages(MESSAGE_REFLUSH_AIRPLANE_STATE);
                            }
                            Message msg=mHandler.obtainMessage(MESSAGE_REFLUSH_AIRPLANE_STATE, buttonOn);
                            mHandler.sendMessageDelayed(msg, 2000);
                        } else {
                            mState = buttonOn ? State.On : State.Off;
                            mAirplaneState = mState;
                           // updateAirplaneState(isAirplaneModeOn(mContext));
                        }
                    } else {
                        mState = buttonOn ? State.On : State.Off;
                        mAirplaneState = mState;
                    }
                    Log.d(TAG, " mAirplaneState=" + mAirplaneState.inTransition() + " mState="
                            + buttonOn);
                }
            }

            public boolean showDuringKeyguard() {
                return true;
            }

            public boolean showBeforeProvisioning() {
                return false;
            }

            private boolean isCalling() {
                final TelephonyManager tm = TelephonyManager.getDefault();
                if (null == tm) {
                    return false;
                }
                return tm.getCallState() != TelephonyManager.CALL_STATE_IDLE;
            }
        };

        mItems = Lists.newArrayList(
                // silent mode
                mSilentModeToggle,            
                // next: airplane mode
               mAirplaneModeOn, 
                // last: power off
                new SinglePressAction(
                        com.android.internal.R.drawable.ic_lock_power_off,
                        R.string.global_action_power_off) {

                    public void onPress() {
                        // shutdown by making sure radio and power are handled accordingly.
                        ShutdownThread.shutdown(mContext, true);
                    }

                    public boolean showDuringKeyguard() {
                        return true;
                    }

                    public boolean showBeforeProvisioning() {
                        return true;
                    }
                });

        mAdapter = new MyAdapter();

        final AlertDialog.Builder ab = new AlertDialog.Builder(mContext);

        ab.setAdapter(mAdapter, this)
                .setInverseBackgroundForced(true);

        final AlertDialog dialog = ab.create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
        if (!mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_sf_slowBlur)) {
            dialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                    WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        }

        dialog.setOnDismissListener(this);

        return dialog;
    }

    private void prepareDialog() {
        final boolean silentModeOn =
                mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
        mSilentModeToggle.updateState(
                silentModeOn ? ToggleAction.State.On : ToggleAction.State.Off);
        final boolean inNoAirplaneMode =  !isAirplaneModeOn(mContext);
        if (inNoAirplaneMode) {
            mAirplaneState = ToggleAction.State.Off;
        }
        Log.d(TAG, " prepareDialog mAirplaneState=" + mAirplaneState);
        mAirplaneModeOn.updateState(mAirplaneState);
        mAdapter.notifyDataSetChanged();
        if (mKeyguardShowing) {
            mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        } else {
            mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
        }
        mDialog.setTitle(R.string.global_actions);
    }


    /** {@inheritDoc} */
    public void onDismiss(DialogInterface dialog) {
        mStatusBar.disable(StatusBarManager.DISABLE_NONE);
    }

    /** {@inheritDoc} */
    public void onClick(DialogInterface dialog, int which) {
        dialog.dismiss();
        mAdapter.getItem(which).onPress();
    }


    /**
     * The adapter used for the list within the global actions dialog, taking
     * into account whether the keyguard is showing via
     * {@link GlobalActions#mKeyguardShowing} and whether the device is provisioned
     * via {@link GlobalActions#mDeviceProvisioned}.
     */
    private class MyAdapter extends BaseAdapter {

        public int getCount() {
            int count = 0;

            for (int i = 0; i < mItems.size(); i++) {
                final Action action = mItems.get(i);

                if (mKeyguardShowing && !action.showDuringKeyguard()) {
                    continue;
                }
                if (!mDeviceProvisioned && !action.showBeforeProvisioning()) {
                    continue;
                }
                count++;
            }
            return count;
        }

        @Override
        public boolean isEnabled(int position) {
            return getItem(position).isEnabled();
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        public Action getItem(int position) {

            int filteredPos = 0;
            for (int i = 0; i < mItems.size(); i++) {
                final Action action = mItems.get(i);
                if (mKeyguardShowing && !action.showDuringKeyguard()) {
                    continue;
                }
                if (!mDeviceProvisioned && !action.showBeforeProvisioning()) {
                    continue;
                }
                if (filteredPos == position) {
                    return action;
                }
                filteredPos++;
            }

            throw new IllegalArgumentException("position " + position + " out of "
                    + "range of showable actions, filtered count = "
                    + "= " + getCount() + ", keyguardshowing=" + mKeyguardShowing
                    + ", provisioned=" + mDeviceProvisioned);
        }


        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            Action action = getItem(position);
            return action.create(mContext, convertView, parent, LayoutInflater.from(mContext));
        }
    }

    // note: the scheme below made more sense when we were planning on having
    // 8 different things in the global actions dialog.  seems overkill with
    // only 3 items now, but may as well keep this flexible approach so it will
    // be easy should someone decide at the last minute to include something
    // else, such as 'enable wifi', or 'enable bluetooth'

    /**
     * What each item in the global actions dialog must be able to support.
     */
    private interface Action {
        View create(Context context, View convertView, ViewGroup parent, LayoutInflater inflater);

        void onPress();

        /**
         * @return whether this action should appear in the dialog when the keygaurd
         *    is showing.
         */
        boolean showDuringKeyguard();

        /**
         * @return whether this action should appear in the dialog before the
         *   device is provisioned.
         */
        boolean showBeforeProvisioning();

        boolean isEnabled();
    }

    /**
     * A single press action maintains no state, just responds to a press
     * and takes an action.
     */
    private static abstract class SinglePressAction implements Action {
        private final int mIconResId;
        private final int mMessageResId;

        protected SinglePressAction(int iconResId, int messageResId) {
            mIconResId = iconResId;
            mMessageResId = messageResId;
        }

        public boolean isEnabled() {
            return true;
        }

        abstract public void onPress();

        public View create(
                Context context, View convertView, ViewGroup parent, LayoutInflater inflater) {
            View v = (convertView != null) ?
                    convertView :
                    inflater.inflate(R.layout.global_actions_item, parent, false);

            ImageView icon = (ImageView) v.findViewById(R.id.icon);
            TextView messageView = (TextView) v.findViewById(R.id.message);

            v.findViewById(R.id.status).setVisibility(View.GONE);

            icon.setImageDrawable(context.getResources().getDrawable(mIconResId));
            messageView.setText(mMessageResId);

            return v;
        }
    }

    /**
     * A toggle action knows whether it is on or off, and displays an icon
     * and status message accordingly.
     */
    private static abstract class ToggleAction implements Action {

        enum State {
            Off(false),
            TurningOn(true),
            TurningOff(true),
            On(false);

            private final boolean inTransition;

            State(boolean intermediate) {
                inTransition = intermediate;
            }

            public boolean inTransition() {
                return inTransition;
            }
        }

        protected State mState = State.Off;

        // prefs
        protected int mEnabledIconResId;
        protected int mDisabledIconResid;
        protected int mMessageResId;
        protected int mEnabledStatusMessageResId;
        protected int mDisabledStatusMessageResId;
        // add by niezhong 0907 for NEWMS00120274 begin
        protected String isExportSim;
        // add by niezhong 0907 for NEWMS00120274 end

        /**
         * @param enabledIconResId The icon for when this action is on.
         * @param disabledIconResid The icon for when this action is off.
         * @param essage The general information message, e.g 'Silent Mode'
         * @param enabledStatusMessageResId The on status message, e.g 'sound disabled'
         * @param disabledStatusMessageResId The off status message, e.g. 'sound enabled'
         */
        public ToggleAction(int enabledIconResId,
                int disabledIconResid,
                int essage,
                int enabledStatusMessageResId,
                int disabledStatusMessageResId) {
            mEnabledIconResId = enabledIconResId;
            mDisabledIconResid = disabledIconResid;
            mMessageResId = essage;
            mEnabledStatusMessageResId = enabledStatusMessageResId;
            mDisabledStatusMessageResId = disabledStatusMessageResId;
        }

        /**
         * Override to make changes to resource IDs just before creating the
         * View.
         */
        void willCreate() {

        }

        public View create(Context context, View convertView, ViewGroup parent,
                LayoutInflater inflater) {
            willCreate();

            View v = (convertView != null) ?
                    convertView :
                    inflater.inflate(R
                            .layout.global_actions_item, parent, false);

            ImageView icon = (ImageView) v.findViewById(R.id.icon);
            TextView messageView = (TextView) v.findViewById(R.id.message);
            TextView statusView = (TextView) v.findViewById(R.id.status);
            // add by niezhong 0907 for NEWMS00120274 begin
            String mStr = Settings.System.getString(context.getContentResolver(), "sim_init_state");
            setSimState(mStr);
            // add by niezhong 0907 for NEWMS00120274 end
            messageView.setText(mMessageResId);

            boolean on = ((mState == State.On) || (mState == State.TurningOn));
            icon.setImageDrawable(context.getResources().getDrawable(
                    (on ? mEnabledIconResId : mDisabledIconResid)));
            statusView.setText(on ? mEnabledStatusMessageResId : mDisabledStatusMessageResId);
            statusView.setVisibility(View.VISIBLE);

            final boolean enabled = isEnabled();
            messageView.setEnabled(enabled);
            statusView.setEnabled(enabled);
            icon.setEnabled(enabled);
            v.setEnabled(enabled);

            return v;
        }

        public final void onPress() {
        	// add by niezhong 0907 for NEWMS00120274 begin
        	if((mMessageResId == R.string.global_actions_toggle_airplane_mode) && 
        			"false".equals(isExportSim)) {
        		return;
        	}
        	// add by niezhong 0907 for NEWMS00120274 end
            if (mState.inTransition()) {
                Log.w(TAG, "shouldn't be able to toggle when in transition");
                return;
            }

            final boolean nowOn = !(mState == State.On);
            onToggle(nowOn);
            changeStateFromPress(nowOn);
        }

        public boolean isEnabled() {
        	// add by niezhong 0907 for NEWMS00120274 begin
        	if((mMessageResId == R.string.global_actions_toggle_airplane_mode) && 
        			"false".equals(isExportSim)) {
        		return false;
        	}
        	// add by niezhong 0907 for NEWMS00120274 end
            return !mState.inTransition();
        }

        /**
         * Implementations may override this if their state can be in on of the intermediate
         * states until some notification is received (e.g airplane mode is 'turning off' until
         * we know the wireless connections are back online
         * @param buttonOn Whether the button was turned on or off
         */
        protected void changeStateFromPress(boolean buttonOn) {
            mState = buttonOn ? State.On : State.Off;
        }

        abstract void onToggle(boolean on);

        public void updateState(State state) {
            mState = state;
            Log.d(TAG, " updateState:mState="+mState.inTransition());
        }
        // add by niezhong 0907 for NEWMS00120274 begin
        public void setSimState(String state) {
        	isExportSim = state;
        }
        // add by niezhong 0907 for NEWMS00120274 end
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)
                    || Intent.ACTION_SCREEN_OFF.equals(action)) {
                String reason = intent.getStringExtra(PhoneWindowManager.SYSTEM_DIALOG_REASON_KEY);
                if (!PhoneWindowManager.SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS.equals(reason)) {
                    mHandler.sendEmptyMessage(MESSAGE_DISMISS);
                }
            } else if (TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED.equals(action)) {
                // Airplane mode can be changed after ECM exits if airplane toggle button
                // is pressed during ECM mode
                if (!(intent.getBooleanExtra("PHONE_IN_ECM_STATE", false)) &&
                        mIsWaitingForEcmExit) {
                    mIsWaitingForEcmExit = false;
                    changeAirplaneModeSystemSetting(true);
                }
            }
            // add by niezhong 0907 for NEWMS00120274 begin
            else if("com.android.contacts.SIM_OPERATE_END_PUT".equals(action)) {
            	mHandler.sendEmptyMessage(SIM_OPERATE_END);
            }
            else if("com.android.contacts.SIM_OPERATE_START_PUT".equals(action)) {
            	mHandler.sendEmptyMessage(SIM_OPERATE_START);
            }
        }
    };
    private  boolean isAirplaneModeOn(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) != 0;
    }

    private boolean isCardDisabled(int phoneid) {
        return Settings.System.getInt(mContext.getContentResolver(),
                PhoneFactory.getSetting(Settings.System.SIM_STANDBY, phoneid), 1) == 0;
    }
    private PhoneStateListener mPhoneStateListener(int phoneId) {
        PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
            @Override
            public void onServiceStateChanged(ServiceState serviceState) {
                Log.d(TAG, "serviceState:" + serviceState + " airplane="
                        + isAirplaneModeOn(mContext));
                final boolean inAirplaneMode = serviceState.getState() == ServiceState.STATE_POWER_OFF;
                if (isAirplaneModeOn(mContext) && inAirplaneMode) {
                    updateAirplaneState(inAirplaneMode);
                } else if (!isAirplaneModeOn(mContext) && !inAirplaneMode) {
                    updateAirplaneState(inAirplaneMode);
                }
            }
        };
        return mPhoneStateListener;
    }
    private static final int MESSAGE_DISMISS = 0;
    // add by niezhong 0907 for NEWMS00120274 begin
    private static final int SIM_OPERATE_END = 1;
    
    private static final int SIM_OPERATE_START = 2 ;
    private static final int MESSAGE_REFLUSH_AIRPLANE_STATE =3 ;
    // add by niezhong 0907 for NEWMS00120274 end
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == MESSAGE_DISMISS) {
                if (mDialog != null) {
                    mDialog.dismiss();
                }
            }
            // add by niezhong 0907 for NEWMS00120274 begin
            else if(msg.what == SIM_OPERATE_END) {
            	setAirViewTrue(true);
            }
            else if(msg.what == SIM_OPERATE_START) {
            	setAirViewTrue(false);
            }else if(msg.what == MESSAGE_REFLUSH_AIRPLANE_STATE){
                boolean airplane=(Boolean)msg.obj;
              updateAirplaneState(airplane);
            }
        }
    };

    /**
     * Change the airplane mode system setting
     */
    private void changeAirplaneModeSystemSetting(boolean on) {
        Settings.System.putInt(
                mContext.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON,
                on ? 1 : 0);
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra("state", on);
        mContext.sendBroadcast(intent);
    }
 // add by niezhong 0907 for NEWMS00120274 begin
    private void setAirViewTrue(boolean operate) {
    	if(operate && (mDialog != null)) {
    		mDialog.dismiss();
    	}	
    }
 // add by niezhong 0907 for NEWMS00120274 end
    private void updateAirplaneState(boolean airplane){
        mAirplaneState = airplane ? ToggleAction.State.On : ToggleAction.State.Off;
        mAirplaneModeOn.updateState(mAirplaneState);
        mAdapter.notifyDataSetChanged();
    }
    private boolean isCardExist(){
        for(int i=0;i<mPhoneCount;i++){
            hasCardReady[i]=mTelephonyManagers[i].hasIccCard();
            Log.d(TAG, " isCardExist:hasCardReady["+i+"]="+hasCardReady[i]);
        }
        if(mPhoneCount>1){
           if(!hasCardReady[0] && !hasCardReady[1]) {
               return false;
           }
        }else{
            if(!hasCardReady[0]) {
                return false;
            }
        }
        return true;
    }
    private boolean isCardActivated(){
        for(int i=0;i<mPhoneCount;i++){
            isStandby[i]=isCardDisabled(i);
            Log.d(TAG, " isCardActivated:isCardDisabled["+i+"]="+isStandby[i]);
        }
        if(mPhoneCount>1){
           if(isStandby[0] && isStandby[1]) {
               return false;
           }
        }else{
            if(isStandby[0]) {
                return false;
            }
        }
        return true;
    }
}
