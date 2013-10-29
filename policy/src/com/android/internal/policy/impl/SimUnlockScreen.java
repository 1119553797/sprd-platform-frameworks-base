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
import android.os.Debug;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.widget.LockPatternUtils;

import android.telephony.TelephonyManager;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.internal.R;

import android.util.Log;
import android.view.inputmethod.InputMethodManager;

/**
 * Displays a dialer like interface to unlock the SIM PIN.
 */
public class SimUnlockScreen extends LinearLayout implements KeyguardScreen, View.OnClickListener {

    private static final int DIGIT_PRESS_WAKE_MILLIS = 5000;
    private static final boolean DEBUG = Debug.isDebug();
    private final KeyguardUpdateMonitor mUpdateMonitor;
    private final KeyguardScreenCallback mCallback;

    private TextView mHeaderText;
    private TextView mPinText;

    private TextView mOkButton;

    private View mBackSpaceButton;

    private final int[] mEnteredPin = {0, 0, 0, 0, 0, 0, 0, 0};
    private int mEnteredDigits = 0;

    private ProgressDialog mSimUnlockProgressDialog = null;

    private LockPatternUtils mLockPatternUtils;

    private int mCreationOrientation;

    private int mKeyboardHidden;

    private KeyguardStatusViewManager mKeyguardStatusViewManager;

    private static final char[] DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
    private TelephonyManager mTelePhoneManager;
    private int remainTimes;
    private Context mContext;

    private int mSub;
    private final int MIN_PIN_LENGTH = 4;

    private IntentFilter filter = new IntentFilter();
    private BroadcastReceiver mBroadcastReceiver;

    private Handler mHandler;

    public SimUnlockScreen(Context context, Configuration configuration,
            KeyguardUpdateMonitor updateMonitor, KeyguardScreenCallback callback,
            LockPatternUtils lockpatternutils,int subscription) {
        super(context);
        mContext = context;
        mUpdateMonitor = updateMonitor;
        mCallback = callback;
        mSub=subscription;
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

        mOkButton = (TextView) findViewById(R.id.ok);
        mTelePhoneManager = (TelephonyManager) mContext.getSystemService(PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE, mSub));
        remainTimes = mTelePhoneManager.getRemainTimes(TelephonyManager.UNLOCK_PIN);
        mHeaderText.setText(getHeaderText());
        mPinText.setFocusable(false);
        hideSoftKeyboard();

        mOkButton.setOnClickListener(this);

        mKeyguardStatusViewManager = new KeyguardStatusViewManager(this, updateMonitor,
                lockpatternutils, callback, true);

        setFocusableInTouchMode(true);

        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mHandler= new Handler();
        mBroadcastReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                if (DEBUG) {
                    Log.d("SimUnlockScreen", "recieved ACTION_AIRPLANE_MODE_CHANGED");
                }
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

		 mContext.registerReceiver(mBroadcastReceiver, filter);
    }

     private void hideSoftKeyboard() {
     // Hide soft keyboard, if visible
         InputMethodManager inputMethodManager = (InputMethodManager)
         getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
         inputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);
     }

    /** {@inheritDoc} */
    public boolean needsInput() {
        return true;
    }

    /** {@inheritDoc} */
    public void onPause() {
        mKeyguardStatusViewManager.onPause();
    }

    /** {@inheritDoc} */
    public void onResume() {
        // start fresh
        mHeaderText.setText(getHeaderText());

        // make sure that the number of entered digits is consistent when we
        // erase the SIM unlock code, including orientation changes.
        mPinText.setText("");
        mEnteredDigits = 0;

        mKeyguardStatusViewManager.onResume();
        //mContext.registerReceiver(mBroadcastReceiver, filter);
    }

    /** {@inheritDoc} */
    public void cleanUp() {
        // dismiss the dialog.
        if (mSimUnlockProgressDialog != null) {
            mSimUnlockProgressDialog.dismiss();
            mSimUnlockProgressDialog = null;
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
//                final boolean result = ITelephony.Stub.asInterface(ServiceManager
//                        .checkService("phone")).supplyPin(mPin);
                //add DSDS start
                final boolean result = ITelephony.Stub.asInterface(ServiceManager.getService(
                        PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE,mSub))).supplyPin(mPin);
                //add DSDS end

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
        } else if (v == mOkButton) {
            if(!checkPinLength()){
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
        getSimUnlockProgressDialog().show();

        new CheckSimPin(mPinText.getText().toString()) {
            void onSimLockChangedResponse(final boolean success) {
                mPinText.post(new Runnable() {
                    public void run() {
                        if (mSimUnlockProgressDialog != null) {
                            mSimUnlockProgressDialog.hide();
                        }
                        if (success) {
                            // before closing the keyguard, report back that
                            // the sim is unlocked so it knows right away
                            mUpdateMonitor.reportSimUnlocked(mSub);
                            mCallback.goToUnlockScreen();
                        }
                        //Added for bug#213435 sim lock begin
                        else if(TelephonyManager.checkSimLocked(mContext, mSub)) {
                            mCallback.goToUnlockScreen();
                        }
                        //Added for bug#213435 sim lock end
                        else {
//                            remainTimes = remainTimes - 1;
//                            String headerText = mContext.getResources().getString(R.string.keyguard_password_wrong_pin_code)
//                                +  "(" + remainTimes + ")";
//                            mHeaderText.setText(headerText);
//                            mPinText.setText("");
//                            mEnteredDigits = 0;
                            try {
                                // Displays No. of attempts remaining to unlock PIN1 in
                                // case of wrong entry.
                                remainTimes = ITelephony.Stub.asInterface(
                                                ServiceManager.getService(PhoneFactory.getServiceName(
                                                                Context.TELEPHONY_SERVICE, mSub)))
                                        .getRemainTimes(TelephonyManager.UNLOCK_PIN);
                                if (remainTimes >= 1) {
                                    clearDigits();
                                    String headerText = mContext.getResources().getString(R.string.keyguard_password_wrong_pin_code)
                                        +  "(" + remainTimes + ")";
                                    mHeaderText.setText(headerText);
                                } else {
                                    mHeaderText.setText(R.string.keyguard_password_wrong_pin_code);
                                }
                            } catch (Exception ex) {
                                mHeaderText.setText(R.string.keyguard_password_wrong_pin_code);
                            }
                        }
                        mCallback.pokeWakelock();
                    }
                });
            }
        }.start();
    }

    private String getHeaderText() {
        String headerText = null;

        if (TelephonyManager.isMultiSim()) {
            if (1 == mSub) {
                headerText = mContext.getResources().getString(R.string.keyguard_password_enter_sim2_pin_code)
                    + "(" + remainTimes + ")";
            } else {
                headerText = mContext.getResources().getString(R.string.keyguard_password_enter_sim1_pin_code)
                    + "(" + remainTimes + ")";
            }
        } else {
            headerText = mContext.getResources().getString(R.string.keyguard_password_enter_pin_code)
                + "(" + remainTimes + ")";
        }
        return headerText;
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
                mPinText.setText(""); // clear the PIN entry field if the user cancels
                mCallback.updatePinUnlockCancel(mSub);
                /*20130620 BUG 171343 WennyCheng START*/
                //mCallback.goToLockScreen();
                mCallback.goToUnlockScreen();
                /*20130620 BUG 171343 WennyCheng END*/
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
}
