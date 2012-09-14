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

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import com.android.internal.telephony.PhoneFactory;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.ITelephony;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.telephony.IccCard;
import android.text.Editable;
import android.view.Gravity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.R;

/**
 * Displays a dialer like interface to unlock the SIM PIN.
 */
public class SimUnlockScreen extends LinearLayout implements KeyguardScreen, View.OnClickListener,
        KeyguardUpdateMonitor.InfoCallback {

    private static final int DIGIT_PRESS_WAKE_MILLIS = 5000;

    private final KeyguardUpdateMonitor mUpdateMonitor;
    private final KeyguardScreenCallback mCallback;

    private int mCallSub;
    
    private TextView mHeaderText;
    private TextView mPinText;

    private TextView mOkButton;
    private Button mEmergencyCallButton;

    private View mBackSpaceButton;

    private final int[] mEnteredPin = {0, 0, 0, 0, 0, 0, 0, 0};
    private int mEnteredDigits = 0;

    private ProgressDialog mSimUnlockProgressDialog = null;

    private LockPatternUtils mLockPatternUtils;

    private int mCreationOrientation;

    private int mKeyboardHidden;

    private static final char[] DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
    private TelephonyManager mTelePhoneManager;
    private Context mContext;
    private int attemptsRemaining;
    private int mPhoneCount=1;
    private static final int STATE_PIN = 0;
    private final int MIN_PIN_LENGTH = 4;
    private int mSub;
    private int mState = STATE_PIN;
    
    private IntentFilter filter = new IntentFilter();
    private BroadcastReceiver mBroadcastReceiver;
    
    private Handler mHandler;
    
    public SimUnlockScreen(Context context, Configuration configuration,
            KeyguardUpdateMonitor updateMonitor, KeyguardScreenCallback callback,
            LockPatternUtils lockpatternutils, int sub) {
        super(context);
        mContext = context;
        mUpdateMonitor = updateMonitor;
        mCallback = callback;
        mSub = sub;
        mCreationOrientation = configuration.orientation;
        mKeyboardHidden = configuration.hardKeyboardHidden;
        mLockPatternUtils = lockpatternutils;

        LayoutInflater inflater = LayoutInflater.from(context);
        if (mKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO) {
            inflater.inflate(R.layout.keyguard_screen_sim_pin_landscape, this, true);
        } else {
            inflater.inflate(R.layout.keyguard_screen_sim_pin_portrait, this, true);
            new TouchInput();
        }

        mHeaderText = (TextView) findViewById(R.id.headerText);
        mPinText = (TextView) findViewById(R.id.pinDisplay);
        mBackSpaceButton = findViewById(R.id.backspace);
        mBackSpaceButton.setOnClickListener(this);

        mEmergencyCallButton = (Button) findViewById(R.id.emergencyCall);
        mLockPatternUtils.updateEmergencyCallButtonState(mEmergencyCallButton);
        mOkButton = (TextView) findViewById(R.id.ok);
        mPhoneCount= TelephonyManager.getPhoneCount();
        if (mUpdateMonitor.getSimState(mSub) == IccCard.State.PIN_REQUIRED) {

            mState = STATE_PIN;
            try {
                attemptsRemaining = getPinOrPukRemainingTimes(mSub,TelephonyManager.UNLOCK_PIN);
                mHeaderText.setText(updatePinRemainningTimes(true));
            } catch (Exception e) {
                mHeaderText.setText(R.string.keyguard_password_wrong_puk_code);
            }
        }        
        //----------------------------------
        
        //PIN Remain times Modify end
        mPinText.setFocusable(false);

        mEmergencyCallButton.setOnClickListener(this);
        mOkButton.setOnClickListener(this);
        setFocusableInTouchMode(true);
        mUpdateMonitor.registerInfoCallback(this);
        
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mHandler= new Handler();
		mBroadcastReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
					boolean isAirPlaneMode = intent.getBooleanExtra("state",
							false);
					if (isAirPlaneMode) {
						mHandler.post(new Runnable() {
							public void run() {
								mCallback.goToUnlockScreen(); 
							}
						});
					}
				}
			}
		};
		
    }

    /** {@inheritDoc} */
    public boolean needsInput() {
        return true;
    }

    /** {@inheritDoc} */
    public void onPause() {

    }

    /** {@inheritDoc} */
    public void onResume() {
        mHeaderText.setText(updatePinRemainningTimes(true));
        //PIN Remain times Modify end
        // make sure that the number of entered digits is consistent when we
        // erase the SIM unlock code, including orientation changes.
        mPinText.setText("");
        mEnteredDigits = 0;
        mLockPatternUtils.updateEmergencyCallButtonState(mEmergencyCallButton);
        mContext.registerReceiver(mBroadcastReceiver, filter);
    }

    /**
     * update pin remainningTimes tip information
     * @return
     */
    private String updatePinRemainningTimes(boolean successfull) {
        Log.d("SimUnlockScreen", "phone id="+mSub + " successfull = "+successfull);
        String headerText = "";
        if (mPhoneCount < 2) {
            headerText = getContext().getString(
                    (successfull ? R.string.keyguard_password_enter_sim_pin_code
                            : R.string.keyguard_password_wrong_pin_code))
                    + getContext().getString(R.string.pinpuk_attempts) + attemptsRemaining;
        } else {
            if (1 == mSub) {
                headerText = getContext().getString(
                        (successfull ? R.string.keyguard_password_enter_sim2_pin_code
                                : R.string.keyguard_password_wrong_pin_code))
                        + getContext().getString(R.string.pinpuk_attempts) + attemptsRemaining;
            } else {
                headerText = getContext().getString(
                        (successfull ? R.string.keyguard_password_enter_sim1_pin_code
                                : R.string.keyguard_password_wrong_pin_code))
                        + getContext().getString(R.string.pinpuk_attempts) + attemptsRemaining;
            }
        }
        return headerText;
    }
    /** {@inheritDoc} */
    public void cleanUp() {
        // hide the dialog.
        if (mSimUnlockProgressDialog != null) {
            mSimUnlockProgressDialog.hide();
            mOkButton.setEnabled(true);
        }
        mUpdateMonitor.removeCallback(this);
    	mContext.unregisterReceiver(mBroadcastReceiver);
    }


    /**
     * Since the IPC can block, we want to run the request in a separate thread
     * with a callback.
     */
    private abstract class CheckSimPin extends Thread {

        private final String mPin;

        protected CheckSimPin(String pin) {
            mPin = pin;
        }

        abstract void onSimLockChangedResponse(boolean success);

        @Override
        public void run() {
            try {
                final boolean result = ITelephony.Stub.asInterface(
                        ServiceManager.getService(PhoneFactory.getServiceName(
                                Context.TELEPHONY_SERVICE, mSub))).supplyPin(mPin);
                
                post(new Runnable() {
                    public void run() {
                        onSimLockChangedResponse(result);
                    }
                });
            } catch (RemoteException e) {
                post(new Runnable() {
                    public void run() {
                        onSimLockChangedResponse(false);
                    }
                });
            }
        }
    }

    public void onClick(View v) {
        if (v == mBackSpaceButton) {
            final Editable digits = mPinText.getEditableText();
            final int len = digits.length();
            if (len > 0) {
                digits.delete(len-1, len);
                mEnteredDigits--;
            }
            mCallback.pokeWakelock();
        } else if (v == mEmergencyCallButton) {
            mCallback.takeEmergencyCallAction(mCallSub);
        } else if (v == mOkButton) {
            if(!checkPinLength()){
                 Log.d("SimUnlockScreen", "on click ok Button ");
                 mOkButton.setEnabled(false);
                 getSimUnlockProgressDialog().show();
                 checkPin();
            }
        }
    }

    private Dialog getSimUnlockProgressDialog() {
        if (mSimUnlockProgressDialog == null) {
            mSimUnlockProgressDialog = new ProgressDialog(mContext);
            mSimUnlockProgressDialog.setMessage(
                    mContext.getString(R.string.lockscreen_sim_unlock_progress_dialog_message));
            mSimUnlockProgressDialog.setIndeterminate(true);
            mSimUnlockProgressDialog.setCancelable(false);
            mSimUnlockProgressDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
            if (!mContext.getResources().getBoolean(
                    com.android.internal.R.bool.config_sf_slowBlur)) {
                mSimUnlockProgressDialog.getWindow().setFlags(
                        WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                        WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            }
        }
        return mSimUnlockProgressDialog;
    }

	private void checkPin() {
		// make sure that the pin is at least 4 digits long.
		if (mEnteredDigits < 4) {
			// otherwise, display a message to the user, and don't submit.
			mHeaderText.setText(R.string.invalidPin);
			mPinText.setText("");
			mEnteredDigits = 0;
			mCallback.pokeWakelock();
			return;
		}
		new CheckSimPin(mPinText.getText().toString()) {
			void onSimLockChangedResponse(boolean success) {
				if (mSimUnlockProgressDialog != null) {
					mSimUnlockProgressDialog.hide();
					mOkButton.setEnabled(true);
				}
				if (success) {
					// ----------------------
					LayoutInflater inflater = LayoutInflater.from(mContext);
					View layout = inflater.inflate(
							R.layout.transient_notification,
							(ViewGroup) findViewById(R.id.toast_layout_root));

					TextView text = (TextView) layout
							.findViewById(R.id.message);
					// text.setText(R.string.keyguard_pin_accepted);
					if (TelephonyManager.getPhoneCount() < 2) {
						text.setText(R.string.keyguard_sim_pin_accepted);
					} else {
						if (mSub == 1) {
							text.setText(R.string.keyguard_sim2_pin_accepted);
						} else {
							text.setText(R.string.keyguard_sim1_pin_accepted);
						}
					}

					Toast toast = new Toast(mContext);
					toast.setDuration(Toast.LENGTH_LONG);
					toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
					toast.setView(layout);
					toast.show();
					// before closing the keyguard, report back that
					// the sim is unlocked so it knows right away
					mUpdateMonitor.reportSimPinUnlocked(mSub);
					mCallback.goToUnlockScreen();
				} else {
					try {
						// Displays No. of attempts remaining to unlock PIN1 in
						// case of wrong entry.
						 attemptsRemaining = getPinOrPukRemainingTimes(mSub,TelephonyManager.UNLOCK_PIN);
						if (attemptsRemaining >= 1) {
							clearDigits();
							mHeaderText.setText(updatePinRemainningTimes(false));
						} else {
							mHeaderText
									.setText(R.string.keyguard_password_wrong_pin_code);
						}
					} catch (Exception ex) {
						mHeaderText
								.setText(R.string.keyguard_password_wrong_pin_code);
					}
					// ----------------------
				}
				mCallback.pokeWakelock();
			}
		}.start();
	}

    public int getPinOrPukRemainingTimes(int phoneId, int type) {
        int mRemainingTimes = -1;
        try {
            mRemainingTimes = ITelephony.Stub.asInterface(
                    ServiceManager.getService(PhoneFactory.getServiceName(
                            Context.TELEPHONY_SERVICE, phoneId))).getRemainTimes(type);
            Log.d("SimUnlockScreen", "getPinOrPukRemainingTimes:phoneId=" + phoneId + " type="+type +"remainningTimes= "+mRemainingTimes);
        } catch (Exception ex) {
            Log.d("SimUnlockScreen", "getRemainTimes exception" + ex.toString());
        }
        return mRemainingTimes;
    }
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            mCallback.goToLockScreen();
            return true;
        }

        final char match = event.getMatch(DIGITS);
        if (match != 0) {
            reportDigit(match - '0');
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            if (mEnteredDigits > 0) {
                mPinText.onKeyDown(keyCode, event);
                mEnteredDigits--;
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            checkPin();
            return true;
        }

        return false;
    }

    private void reportDigit(int digit) {
        if (mEnteredDigits == 0) {
            mPinText.setText("");
        }
        if (mEnteredDigits == 8) {
            return;
        }
        mPinText.append(Integer.toString(digit));
        mEnteredPin[mEnteredDigits++] = digit;
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
        updateConfiguration();
    }

    /** {@inheritDoc} */
    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateConfiguration();
    }

    /**
     * Helper class to handle input from touch dialer.  Only relevant when
     * the keyboard is shut.
     */
    private class TouchInput implements View.OnClickListener {
        private TextView mZero;
        private TextView mOne;
        private TextView mTwo;
        private TextView mThree;
        private TextView mFour;
        private TextView mFive;
        private TextView mSix;
        private TextView mSeven;
        private TextView mEight;
        private TextView mNine;
        private TextView mCancelButton;

        private TouchInput() {
            mZero = (TextView) findViewById(R.id.zero);
            mOne = (TextView) findViewById(R.id.one);
            mTwo = (TextView) findViewById(R.id.two);
            mThree = (TextView) findViewById(R.id.three);
            mFour = (TextView) findViewById(R.id.four);
            mFive = (TextView) findViewById(R.id.five);
            mSix = (TextView) findViewById(R.id.six);
            mSeven = (TextView) findViewById(R.id.seven);
            mEight = (TextView) findViewById(R.id.eight);
            mNine = (TextView) findViewById(R.id.nine);
            mCancelButton = (TextView) findViewById(R.id.cancel);

            mZero.setText("0");
            mOne.setText("1");
            mTwo.setText("2");
            mThree.setText("3");
            mFour.setText("4");
            mFive.setText("5");
            mSix.setText("6");
            mSeven.setText("7");
            mEight.setText("8");
            mNine.setText("9");

            mZero.setOnClickListener(this);
            mOne.setOnClickListener(this);
            mTwo.setOnClickListener(this);
            mThree.setOnClickListener(this);
            mFour.setOnClickListener(this);
            mFive.setOnClickListener(this);
            mSix.setOnClickListener(this);
            mSeven.setOnClickListener(this);
            mEight.setOnClickListener(this);
            mNine.setOnClickListener(this);
            mCancelButton.setOnClickListener(this);
        }


        public void onClick(View v) {
            if (v == mCancelButton) {
                mCallback.goToLockScreen();
                return;
            }

            final int digit = checkDigit(v);
            if (digit >= 0) {
                mCallback.pokeWakelock(DIGIT_PRESS_WAKE_MILLIS);
                reportDigit(digit);
            }
        }

        private int checkDigit(View v) {
            int digit = -1;
            if (v == mZero) {
                digit = 0;
            } else if (v == mOne) {
                digit = 1;
            } else if (v == mTwo) {
                digit = 2;
            } else if (v == mThree) {
                digit = 3;
            } else if (v == mFour) {
                digit = 4;
            } else if (v == mFive) {
                digit = 5;
            } else if (v == mSix) {
                digit = 6;
            } else if (v == mSeven) {
                digit = 7;
            } else if (v == mEight) {
                digit = 8;
            } else if (v == mNine) {
                digit = 9;
            }
            return digit;
        }
    }
    
    public void onPhoneStateChanged(String newState){
    	
    	mLockPatternUtils.updateEmergencyCallButtonState(mEmergencyCallButton);
    }

    public void onPhoneStateChanged(String newState,int sub) {
    	mCallSub = sub;
        mLockPatternUtils.updateEmergencyCallButtonState(mEmergencyCallButton,sub);
    }

    public void onRefreshBatteryInfo(boolean showBatteryInfo, boolean pluggedIn, int batteryLevel) {

    }

    public void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn, int phoneId) {

    }

    public void onRingerModeChanged(int state) {

    }

    public void onTimeChanged() {

    }
    
    //--------------
	private boolean checkPinLength() {
		// make sure that the pin is at least 4 digits long.
		if (mEnteredDigits < MIN_PIN_LENGTH) {
			// otherwise, display a message to the user, and don't submit.
			mHeaderText.setText(R.string.invalidPin);
			mPinText.setText("");
			mEnteredDigits = 0;
			mCallback.pokeWakelock();
			return true;
		}
		return false;

	}
    private void clearDigits() {
        final Editable digits = mPinText.getEditableText();
        final int len = digits.length();
        if (len > 0) {
            digits.delete(0, len);
            mEnteredDigits = 0;
        }
    }
    //--------------
}
