package android.widget;

import android.media.AudioManager;

import com.android.internal.telephony.IccCard;

/***
 * {@hide} Add this interface for lock screen .
 */
public interface ILockScreen {
	/**
	 * {@hide}
	 * @param showBatteryInfo
	 * @param pluggedIn
	 * @param batteryLevel
	 */
	void onRefreshBatteryInfo(boolean showBatteryInfo, boolean pluggedIn,
			int batteryLevel);

	/**
	 * {@hide}
	 */
	void onTimeChanged();

	boolean needsInput();

	Button onPhoneStateChanged(int phoneState);

	/**
	 * {@hide}
	 * @param plmn
	 *            The operator name of the registered network. May be null if it
	 *            shouldn't be displayed.
	 * @param spn
	 *            The service provider name. May be null if it shouldn't be
	 *            displayed.
	 */
	void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn,
			int subscription);

	/**
	 * {@hide} Called when the ringer mode changes.
	 * @param state
	 *            the current ringer state, as defined in
	 *            {@link AudioManager#RINGER_MODE_CHANGED_ACTION}
	 */
	void onRingerModeChanged(int state);

	/**
	 * {@hide}
	 * @param simState
	 */
	void onSimStateChanged(IccCard.State simState, int subscription);

	/**
	 * {@hide}
	 */
	void onPause();

	/**
	 * {@hide}
	 */
	void onResume();

	/**
	 * {@hide}
	 */
	void cleanUp();

	/**
	 * {@hide}
	 */
	void onStartAnim();

	/**
	 * {@hide}
	 */
	void onStopAnim();

	/**
	 * {@hide}
	 */
	void onClockVisibilityChanged();

	/**
	 * {@hide}
	 */
	void onDeviceProvisioned();

	/**
	 * {@hide}
	 * @param messagecount
	 */
	void onMessageCountChanged(int messagecount);

	/**
	 * {@hide}
	 * @param messagecount
	 */
	void onDeleteMessageCount(int messagecount);

	/**
	 * {@hide}
	 * @param count
	 */
	void onMissedCallCountChanged(int count);
}
