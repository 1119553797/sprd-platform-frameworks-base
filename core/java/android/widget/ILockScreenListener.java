package android.widget;

import com.android.internal.telephony.IccCard;

/**
 * {@hide}
 * 
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
	IccCard.State getSimState();

	/**
	 * {@hide}
	 */
	IccCard.State getSimState(int i);

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