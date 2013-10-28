/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.internal.policy.impl.keyguard;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.widget.LockPatternUtils;

public class KeyguardSecurityModel {
    /**
     * The different types of security available for {@link Mode#UnlockScreen}.
     * @see com.android.internal.policy.impl.LockPatternKeyguardView#getUnlockMode()
     */
    enum SecurityMode {
        Invalid, // NULL state
        None, // No security enabled
        Pattern, // Unlock by drawing a pattern.
        Password, // Unlock by entering an alphanumeric password
        PIN, // Strictly numeric password
        Biometric, // Unlock with a biometric key (e.g. finger print or face unlock)
        Account, // Unlock by entering an account's login and password.
        SimPin, // Unlock by entering a sim pin.
        /* SPRD: Modify 20130911 Spreadst of 210537 keyguard support multi-card @{ */
        Sim2Pin,
        SimPuk, // Unlock by entering a sim puk
        Sim2Puk
        /* @} */
    }

    private Context mContext;
    private LockPatternUtils mLockPatternUtils;
    /* SPRD: Modify 20130911 Spreadst of 210537 keyguard support multi-card @{ */
    private final String TAG = "KeyguardSecurityModel";
    /* @} */

    /* SPRD: Modify 20130912 Spreadst of 215617 support 3sim to init var for Pin and Puk @{ */
    boolean[] mIsPinUnlockCancelled;
    boolean[] mIsPukUnlockCancelled;
    /* @} */

    KeyguardSecurityModel(Context context) {
        mContext = context;
        mLockPatternUtils = new LockPatternUtils(context);
        /* SPRD: Modify 20130912 Spreadst of 215617 support 3sim to init var for Pin and Puk @{ */
        final int phoneCount = TelephonyManager.getPhoneCount();
        mIsPinUnlockCancelled = new boolean[phoneCount];
        mIsPukUnlockCancelled = new boolean[phoneCount];
        for (int i = 0; i < phoneCount; i++) {
            mIsPinUnlockCancelled[i] = false;
            mIsPukUnlockCancelled[i] = false;
        }
        /* @} */
    }

    void setLockPatternUtils(LockPatternUtils utils) {
        mLockPatternUtils = utils;
    }

    /**
     * Returns true if biometric unlock is installed and selected.  If this returns false there is
     * no need to even construct the biometric unlock.
     */
    boolean isBiometricUnlockEnabled() {
        return mLockPatternUtils.usingBiometricWeak()
                && mLockPatternUtils.isBiometricWeakInstalled();
    }

    /**
     * Returns true if a condition is currently suppressing the biometric unlock.  If this returns
     * true there is no need to even construct the biometric unlock.
     */
    private boolean isBiometricUnlockSuppressed() {
        KeyguardUpdateMonitor monitor = KeyguardUpdateMonitor.getInstance(mContext);
        final boolean backupIsTimedOut = monitor.getFailedUnlockAttempts() >=
                LockPatternUtils.FAILED_ATTEMPTS_BEFORE_TIMEOUT;
        return monitor.getMaxBiometricUnlockAttemptsReached() || backupIsTimedOut
                || !monitor.isAlternateUnlockEnabled()
                || monitor.getPhoneState() != TelephonyManager.CALL_STATE_IDLE;
    }

    SecurityMode getSecurityMode() {
        KeyguardUpdateMonitor updateMonitor = KeyguardUpdateMonitor.getInstance(mContext);
        /* SPRD: Modify 20130911 Spreadst of 210537 keyguard support multi-card @{ */
        // final IccCardConstants.State simState = updateMonitor.getSimState();
        SecurityMode mode = SecurityMode.None;
        int phoneCount = TelephonyManager.getPhoneCount();
        final IccCardConstants.State[] simState = new IccCardConstants.State[phoneCount];
        for (int i = 0; i < phoneCount; i++) {
            simState[i] = updateMonitor.getSimState(i);
            Log.d(TAG, "simState = " + simState[i] + ", i = " + i);
            if (simState[i] == IccCardConstants.State.PIN_REQUIRED && !updateMonitor.mIsPinUnlockCancelled[i]) {
                mode = i == 0 ? SecurityMode.SimPin : SecurityMode.Sim2Pin;
                return mode;
            } else if (simState[i] == IccCardConstants.State.PUK_REQUIRED && !updateMonitor.mIsPukUnlockCancelled[i]) {
                mode = i == 0 ? SecurityMode.SimPuk : SecurityMode.Sim2Puk;
                return mode;
            }
        }
        /* if (simState == IccCardConstants.State.PIN_REQUIRED) {
            mode = SecurityMode.SimPin;
        } else if (simState == IccCardConstants.State.PUK_REQUIRED
                && mLockPatternUtils.isPukUnlockScreenEnable()) {
            mode = SecurityMode.SimPuk;
        } else { */
        final int security = mLockPatternUtils.getKeyguardStoredPasswordQuality();
        switch (security) {
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC:
                mode = mLockPatternUtils.isLockPasswordEnabled() ?
                        SecurityMode.PIN : SecurityMode.None;
                break;
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC:
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_COMPLEX:
                mode = mLockPatternUtils.isLockPasswordEnabled() ?
                        SecurityMode.Password : SecurityMode.None;
                break;

            case DevicePolicyManager.PASSWORD_QUALITY_SOMETHING:
            case DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED:
                if (mLockPatternUtils.isLockPatternEnabled()) {
                    mode = mLockPatternUtils.isPermanentlyLocked() ?
                            SecurityMode.Account : SecurityMode.Pattern;
                }
                break;

            default:
                throw new IllegalStateException("Unknown unlock mode:" + mode);
        }
        /* } */
        /* @} */
        return mode;
    }

    /**
     * Some unlock methods can have an alternate, such as biometric unlocks (e.g. face unlock).
     * This function decides if an alternate unlock is available and returns it. Otherwise,
     * returns @param mode.
     *
     * @param mode the mode we want the alternate for
     * @return alternate or the given mode
     */
    SecurityMode getAlternateFor(SecurityMode mode) {
        if (isBiometricUnlockEnabled() && !isBiometricUnlockSuppressed()
                && (mode == SecurityMode.Password
                        || mode == SecurityMode.PIN
                        || mode == SecurityMode.Pattern)) {
            return SecurityMode.Biometric;
        }
        return mode; // no alternate, return what was given
    }

    /**
     * Some unlock methods can have a backup which gives the user another way to get into
     * the device. This is currently only supported for Biometric and Pattern unlock.
     *
     * @return backup method or current security mode
     */
    SecurityMode getBackupSecurityMode(SecurityMode mode) {
        switch(mode) {
            case Biometric:
                return getSecurityMode();
            case Pattern:
                return SecurityMode.Account;
        }
        return mode; // no backup, return current security mode
    }
}
