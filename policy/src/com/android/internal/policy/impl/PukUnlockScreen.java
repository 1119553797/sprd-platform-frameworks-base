/*
 * Copyright (C) 2011 SpreadTrum
 *
 */

package com.android.internal.policy.impl;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.widget.LockPatternUtils;

import android.text.Editable;
import android.util.Log;
import android.view.Gravity;
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
public class PukUnlockScreen extends LinearLayout implements KeyguardScreen, View.OnClickListener,
        KeyguardUpdateMonitor.InfoCallback {

    private static final int DIGIT_PRESS_WAKE_MILLIS = 5000;

    private static final int STATUS_INPUT_PUK   = 0;
    private static final int STATUS_INPUT_PIN_1 = 1;
    private static final int STATUS_INPUT_PIN_2 = 2;

    private int mCallSub;
    
    private final KeyguardUpdateMonitor mUpdateMonitor;
    private final KeyguardScreenCallback mCallback;

    private TextView mHeaderText;
    private TextView mPukText;

    private TextView mOkButton;
    private Button mEmergencyCallButton;

    private View mBackSpaceButton;

    private int mEnteredDigits = 0;

    private ProgressDialog mPukUnlockProgressDialog = null;

    private LockPatternUtils mLockPatternUtils;

    private int mCreationOrientation;

    private int mKeyboardHidden;

    private int remainTimes;
    private static final char[] DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};

    private String mPukString;
    private String mNewPinString;
    private int mPhoneCount=1;
    private int mCurrentStatus;
    private Context mContext;
    private TelephonyManager mTelePhoneManager; 
    
    private static final int STATE_PUK = 1;
    private final int MAX_PUK_LENGTH = 8;
    private int mSub;
    private int mState = STATE_PUK;
    public PukUnlockScreen(Context context, Configuration configuration,
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
        mPhoneCount= TelephonyManager.getPhoneCount();
        LayoutInflater inflater = LayoutInflater.from(context);
        if (mKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO) {
            inflater.inflate(R.layout.keyguard_screen_sim_pin_landscape, this, true);
        } else {
            inflater.inflate(R.layout.keyguard_screen_sim_pin_portrait, this, true);
            new TouchInput();
        }

		mTelePhoneManager = (TelephonyManager) mContext
				.getSystemService(PhoneFactory.getServiceName(
						Context.TELEPHONY_SERVICE, mSub));

        mHeaderText = (TextView) findViewById(R.id.headerText);
        mPukText = (TextView) findViewById(R.id.pinDisplay);
        mBackSpaceButton = findViewById(R.id.backspace);
        mBackSpaceButton.setOnClickListener(this);

        mEmergencyCallButton = (Button) findViewById(R.id.emergencyCall);
        mLockPatternUtils.updateEmergencyCallButtonState(mEmergencyCallButton);
        mOkButton = (TextView) findViewById(R.id.ok);

        //PUK Code Remain times modify start
        remainTimes = mTelePhoneManager.getRemainTimes(TelephonyManager.UNLOCK_PUK);
        //String headerText = mContext.getResources().getString(R.string.keyguard_password_enter_puk_code);
        //mHeaderText.setText(headerText + "(" + remainTimes + ")");
      //-----------------------------------
		if (mUpdateMonitor.getSimState(mSub) == IccCard.State.PUK_REQUIRED) {
			mState = STATE_PUK;

			//if (TelephonyManager.getPhoneCount() < 2) {
				mHeaderText.setText(updatePukRemainningTimes(true));
			//} else {
			//	mHeaderText.setText((mSub == 0) ? R.string.sim1_puk_requried
			//			: R.string.sim2_puk_requried);
		//	}
		}    
        //----------------------------------
        
        //PUK Code Remain times modify end
        mPukText.setFocusable(false);

        mEmergencyCallButton.setOnClickListener(this);
        mOkButton.setOnClickListener(this);
        mUpdateMonitor.registerInfoCallback(this);
        mCurrentStatus = STATUS_INPUT_PUK;
        setFocusableInTouchMode(true);
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
        // start fresh
        //PUK Code Remain times modify start
        //String headerText = mContext.getResources().getString(R.string.keyguard_password_enter_puk_code);
        mHeaderText.setText(updatePukRemainningTimes(true));
        //PUK Code Remain times modify end

        mCurrentStatus = STATUS_INPUT_PUK;

        // make sure that the number of entered digits is consistent when we
        // erase the SIM unlock code, including orientation changes.
        mPukText.setText("");
        mEnteredDigits = 0;

        mLockPatternUtils.updateEmergencyCallButtonState(mEmergencyCallButton);
    }
    /**
     * update puk remainningTimes tip information
     * @return
     */
    private String updatePukRemainningTimes(boolean successfull) {
        Log.d("PukUnlockScreen", "phone id="+mSub + " successfull = "+successfull);
        String headerText = "";
        if (mPhoneCount < 2) {
            headerText = getContext().getString(
                    (successfull ? R.string.sim_puk_requried
                            : R.string.keyguard_password_wrong_puk_code))
                    + getContext().getString(R.string.pinpuk_attempts) + remainTimes;
        } else {
            if (1 == mSub) {
                headerText = getContext().getString(
                        (successfull ? R.string.sim2_puk_requried
                                : R.string.keyguard_password_wrong_puk_code))
                        + getContext().getString(R.string.pinpuk_attempts) + remainTimes;
            } else {
                headerText = getContext().getString(
                        (successfull ? R.string.sim1_puk_requried
                                : R.string.keyguard_password_wrong_puk_code))
                        + getContext().getString(R.string.pinpuk_attempts) + remainTimes;
            }
        }
        return headerText;
    }
    /** {@inheritDoc} */
    public void cleanUp() {
        // hide the dialog.
        if (mPukUnlockProgressDialog != null) {
            mPukUnlockProgressDialog.hide();
        }
        mUpdateMonitor.removeCallback(this);
    }


    /**
     * Since the IPC can block, we want to run the request in a separate thread
     * with a callback.
     */
    private abstract class CheckSimPuk extends Thread {

        private final String mPuk;
        private final String mPin;

        protected CheckSimPuk(String puk,String pin) {
            mPuk = puk;
            mPin = pin;
        }

        abstract void onSimLockChangedResponse(boolean success);

        @Override
        public void run() {
            try {

                final boolean result = ITelephony.Stub.asInterface(ServiceManager
                          .checkService(PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE, mSub))).supplyPuk(mPuk,mPin);
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
            final Editable digits = mPukText.getEditableText();
            final int len = digits.length();
            if (len > 0) {
                digits.delete(len-1, len);
                mEnteredDigits--;
            }
            mCallback.pokeWakelock();
        } else if (v == mEmergencyCallButton) {
            mCallback.takeEmergencyCallAction(mCallSub);
        } else if (v == mOkButton) {
            if(!checkPukLength()){
                updateState();
            }
        }
    }

    private void updateState() {
        switch (mCurrentStatus) {
            case STATUS_INPUT_PUK:
                mPukString = mPukText.getText().toString();
                mCurrentStatus = STATUS_INPUT_PIN_1;
                mHeaderText.setText(R.string.keyguard_password_enter_new_pin_first_code);
                mPukText.setText("");
                break;

            case STATUS_INPUT_PIN_1:
                mNewPinString = mPukText.getText().toString();
                if (mNewPinString == null || mNewPinString.length() < 4
                        || mNewPinString.length() > 8) {
                    String title = mContext.getResources().getString(R.string.invalidPin)
                            + "\r\n"
                            + mContext.getResources().getString(
                                    R.string.keyguard_password_enter_new_pin_second_code);
                    mHeaderText.setText(title);
                } else {
                    mCurrentStatus = STATUS_INPUT_PIN_2;
                    mHeaderText.setText(R.string.keyguard_password_enter_new_pin_second_code);
                }
                mPukText.setText("");
                break;

            case STATUS_INPUT_PIN_2:
                String mCurrentPinText = mPukText.getText().toString();
                if (mCurrentPinText == null || mCurrentPinText.length() < 4
                        || mCurrentPinText.length() > 8) {
                    String title = mContext.getResources().getString(R.string.invalidPin)
                            + "\n"
                            + mContext.getResources().getString(
                                    R.string.keyguard_password_enter_new_pin_second_code);
                    mHeaderText.setText(title);
                    mPukText.setText("");
                    break;
                }

                if (!mNewPinString.equals(mCurrentPinText)) {
                    mCurrentStatus = STATUS_INPUT_PIN_1;
                    String title = mContext.getResources().getString(R.string.mismatchPin)
                            + "\n"
                            + mContext.getResources().getString(
                                    R.string.keyguard_password_enter_new_pin_first_code);
                    mHeaderText.setText(title);
                    mPukText.setText("");
                } else {
                    checkPuk();
                    mCurrentStatus = STATUS_INPUT_PUK;
                }
                break;
        }
    }
    private Dialog getSimUnlockProgressDialog() {
        if (mPukUnlockProgressDialog == null) {
            mPukUnlockProgressDialog = new ProgressDialog(mContext);
            mPukUnlockProgressDialog.setMessage(
                    mContext.getString(R.string.lockscreen_sim_unlock_progress_dialog_message));
            mPukUnlockProgressDialog.setIndeterminate(true);
            mPukUnlockProgressDialog.setCancelable(false);
            mPukUnlockProgressDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
            if (!mContext.getResources().getBoolean(
                    com.android.internal.R.bool.config_sf_slowBlur)) {
                mPukUnlockProgressDialog.getWindow().setFlags(
                        WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                        WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            }
        }
        return mPukUnlockProgressDialog;
    }

    private void checkPuk() {
        getSimUnlockProgressDialog().show();
        new CheckSimPuk(mPukString,mNewPinString) {
            void onSimLockChangedResponse(boolean success) {
                if (mPukUnlockProgressDialog != null) {
                    mPukUnlockProgressDialog.hide();
                }
                LayoutInflater inflater = LayoutInflater.from(mContext);
                View layout = inflater.inflate(R.layout.transient_notification,
                                   (ViewGroup) findViewById(R.id.toast_layout_root));

                TextView text = (TextView) layout.findViewById(R.id.message);

                Toast toast = new Toast(mContext);
                toast.setDuration(Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                toast.setView(layout);
                if (success) {
                    text.setText(R.string.puk_accept);
                    toast.show(); 
                    // before closing the keyguard, report back that
                    // the sim is unlocked so it knows right away
                    mUpdateMonitor.reportSimPinUnlocked(mSub);
                    mCallback.goToUnlockScreen();
                    //add by niezhong for NEWMS00118673 09-17-11 begin
                    mContext.sendBroadcast(new Intent(TelephonyIntents.ACTION_SIM_STATE_CHANGED));
                  //add by niezhong for NEWMS00118673 09-17-11 end
                } else {
                	int attemptsRemaining = 1;
					text.setText(R.string.unblocking_fail);
					toast.show();
					mState = STATE_PUK;
					clearDigits();
					try {
						attemptsRemaining = ITelephony.Stub.asInterface(
		                        ServiceManager.getService(PhoneFactory.getServiceName(
		                                Context.TELEPHONY_SERVICE, mSub)))
						                .getRemainTimes(TelephonyManager.UNLOCK_PUK);
						if (attemptsRemaining >= 1) {
							// clearDigits();
							remainTimes = attemptsRemaining;
							mHeaderText.setText(updatePukRemainningTimes(false));
						} else {
							//mOkButton.setEnabled(false);
							String displayMessage = getContext().getString(
							        R.string.unblocking_fail);
							mHeaderText.setText(displayMessage);
						}
					} catch (Exception e) {
						mHeaderText
								.setText(R.string.keyguard_password_wrong_puk_code);
					}
                }
                mCallback.pokeWakelock();
            }
        }.start();
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
                mPukText.onKeyDown(keyCode, event);
                mEnteredDigits--;
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_ENTER) {

            onClick(mOkButton);

            return true;
        }

        return false;
    }

    private void reportDigit(int digit) {
        if (mEnteredDigits == 0) {
            mPukText.setText("");
        }
        
        mPukText.append(Integer.toString(digit));
        mEnteredDigits++;
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

    private boolean checkPukLength() {
        // make sure that the PUK code is 8 digits long.
        if (mCurrentStatus == STATUS_INPUT_PUK && mEnteredDigits != MAX_PUK_LENGTH) {
            mHeaderText.setText(R.string.invalidPuk);
            mPukText.setText("");
            mEnteredDigits = 0;
            mCallback.pokeWakelock();
            return true;
        }
        return false;
    }

    private void clearDigits() {
        final Editable digits = mPukText.getEditableText();
        final int len = digits.length();
        if (len > 0) {
            digits.delete(0, len);
            mEnteredDigits = 0;
        }
    }

}
