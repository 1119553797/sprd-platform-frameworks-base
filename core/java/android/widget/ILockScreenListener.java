/** Create by Spreadst */

package android.widget;

import com.android.internal.telephony.IccCardConstants;

/**
 * {@hide}
 */
public interface ILockScreenListener {

    /**
     * {@hide} Transition to the unlock screen.
     */
    void goToUnlockScreen();

    /**
     * {@hide} Take action to send an emergency call.
     */
    void takeEmergencyCallAction();

    /**
     * {@hide}
     */
    IccCardConstants.State getSimState();

    /**
     * {@hide}
     */
    IccCardConstants.State getSimState(int i);

    /**
     * {@hide}
     */
    boolean isDeviceProvisioned();

    /**
     * {@hide}
     */
    void pokeWakelock();

    /***
     * {@hide}
     */
    void pokeWakelock(int millis);

    /**
     * {@hide}
     */
    boolean isDeviceCharged();

    /**
     * {@hide}
     */
    boolean shouldShowBatteryInfo();

    /**
     * {@hide}
     */
    boolean isDevicePluggedIn();

    /**
     * {@hide}
     */
    int getBatteryLevel();

    /**
     * {@hide}
     */
    CharSequence getTelephonyPlmn(int i);

    /**
     * {@hide}
     */
    CharSequence getTelephonySpn(int i);
}
