package com.android.systemui.statusbar.phone;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.ContentObserver;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IPowerManager;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Slog;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneStateIntentReceiver;
import com.android.internal.telephony.TelephonyIntents;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.ToggleViewGroup;

public class ToggleListener extends BroadcastReceiver implements View.OnClickListener {
    private static final String TAG = "ToggleListener";
    private static final boolean DEBUG = true;

    /**
     * Minimum and maximum brightnesses. Don't go to 0 since that makes the
     * display unusable
     */
    private static final int MINIMUM_BACKLIGHT = android.os.PowerManager.BRIGHTNESS_DIM + 10;
    private static final int MAXIMUM_BACKLIGHT = android.os.PowerManager.BRIGHTNESS_ON;
    private static final int DEFAULT_BACKLIGHT = (int) (android.os.PowerManager.BRIGHTNESS_ON * 0.4f);
    /** Minimum brightness at which the indicator is shown at half-full and ON */
    private static final int HALF_BRIGHTNESS_THRESHOLD = (int) (0.3 * MAXIMUM_BACKLIGHT);
    /** Minimum brightness at which the indicator is shown at full */
    private static final int FULL_BRIGHTNESS_THRESHOLD = (int) (0.8 * MAXIMUM_BACKLIGHT);

    private static final int[] sDataNetSyncImages = {
            R.drawable.quick_switch_mobile_data_on_sprd,
            R.drawable.quick_switch_mobile_data_off_sprd
    };

    private ToggleViewGroup mToggleViewGroup;
    private final Context mContext;
    private final ContentResolver mResolver;
    private AudioManager mAudioManager;
    private WifiManager mWifiManager;
    private ConnectivityManager mConnManager;
    private BluetoothAdapter mBluetoothAdapter;
    private TelephonyManager[] mTelephonyManagers;
    private int mWifiState;
    private int mBluetoothState;
    private int numPhones = 1;
    private boolean[] hasCard;
    private boolean[] isStandby;
    private PhoneStateIntentReceiver mPhoneStateReceiver;
    private PhoneStateIntentReceiver mPhoneStateReceiver2;
    private static final int EVENT_SERVICE_STATE_CHANGED = 3;
    protected static final int EVENT_AIRPLANE_SWITCH_DELAY_MESSAGE = 100;
    protected static final int DELAY_AIRPLANE_SET_TIME = 5000;
    private static final String RINGER_MODE = "ringer_mode";

    private boolean mAirplaneEnable;
    private boolean mGpsEnable;
    private boolean mAutorotateEnable;

    // indicate whether the data network is on
    private boolean mDataDefaultNetworkOn = false;

    public ToggleListener(ToggleViewGroup toggleViewGroup) {
        mToggleViewGroup = toggleViewGroup;
        mContext = mToggleViewGroup.getContext();
        mResolver = mContext.getContentResolver();
        numPhones = TelephonyManager.getPhoneCount();
        hasCard = new boolean[numPhones];
        isStandby = new boolean[numPhones];
    }

    // initialize the current state of every quick switch items
    public void init() {
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mConnManager = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        mTelephonyManagers = new TelephonyManager[numPhones];
        mPhoneStateReceiver = new PhoneStateIntentReceiver(mContext, mHandler);
        mPhoneStateReceiver.notifyServiceState(EVENT_SERVICE_STATE_CHANGED);
        if (numPhones > 1) {
            mPhoneStateReceiver2 = new PhoneStateIntentReceiver(mContext, mHandler, 1);
            mPhoneStateReceiver2.notifyServiceState(EVENT_SERVICE_STATE_CHANGED);
        }

        mPhoneStateReceiver.registerIntent();
        if (numPhones > 1) {
            mPhoneStateReceiver2.registerIntent();
        }

        for (int i = 0; i < numPhones; i++) {
            mTelephonyManagers[i] = (TelephonyManager) mContext.getSystemService(PhoneFactory
                    .getServiceName(Context.TELEPHONY_SERVICE, i));
        }

        updateWifiButton();
        updateBluetoothButton();
        updateDataNetworkButton();
        updateSoundModeButton();
        updateGpsButton();
        updateBrightnessButton();
        updateAirplaneButton();
        updateAutorotateButton();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.quick_switch_wifi_toggle:
                if (mWifiState == WifiManager.WIFI_STATE_DISABLED
                        || mWifiState == WifiManager.WIFI_STATE_ENABLED
                        || mWifiState == WifiManager.WIFI_STATE_UNKNOWN) {
                    toggleWifi();
                }
                break;
            case R.id.quick_switch_bluetooth_toggle:
                if (mBluetoothState == BluetoothAdapter.STATE_OFF
                        || mBluetoothState == BluetoothAdapter.STATE_ON) {
                    toggleBluetooth();
                }
                break;
            case R.id.quick_switch_mobile_net_toggle:
                toggleDataNetwork();
                break;
            case R.id.quick_switch_sound_toggle:
                toggleSoundMode();
                break;
            case R.id.quick_switch_gps_toggle:
                toggleGps();
                break;
            case R.id.quick_switch_brightness_toggle:
                toggleBrightness();
                break;
            case R.id.quick_switch_airplane_toggle:
                toggleAirplane();
                break;
            case R.id.quick_switch_auto_rotate_toggle:
                toggleAutoRotate();
                break;
            default:
                break;
        }
    }

    public void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction("android.settings.action.soundmode");
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        filter.addAction(AudioManager.VIBRATE_SETTING_CHANGED_ACTION);
        filter.addAction(Intent.ACTION_DEFAULT_PHONE_CHANGE);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mToggleViewGroup.getContext().registerReceiver(this, filter);

        // listen the fields changed, then refresh the UI
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.AIRPLANE_MODE_ON),
                true, mAirPlaneChangedObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                true, mBrightnessChangedObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
                true, mBrightnessAutoModeObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.Secure.LOCATION_PROVIDERS_ALLOWED),
                true, mGpsChangedObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                true, mAutoRotateChangedObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.MOBILE_DATA),
                true, mMobileDataObserver);
    }

    public void unregisterReceiver() {
        mToggleViewGroup.getContext().unregisterReceiver(this);
        mContext.getContentResolver().unregisterContentObserver(mAirPlaneChangedObserver);
        mContext.getContentResolver().unregisterContentObserver(mBrightnessChangedObserver);
        mContext.getContentResolver().unregisterContentObserver(mBrightnessAutoModeObserver);
        mContext.getContentResolver().unregisterContentObserver(mGpsChangedObserver);
        mContext.getContentResolver().unregisterContentObserver(mAutoRotateChangedObserver);
        mContext.getContentResolver().unregisterContentObserver(mMobileDataObserver);
        mPhoneStateReceiver.unregisterIntent();
        if (numPhones > 1) {
            if (mPhoneStateReceiver2 != null) {
                mPhoneStateReceiver2.unregisterIntent();
            }
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "action = " + action);
        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            updateBluetoothButton();
        } else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
            Log.d(TAG, "ACTION_SIM_STATE_CHANGED");
            getSimCardStatus();
            updateDataNetworkButton();
        } else if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
            updateWifiButton();
        } else if (action.equals(AudioManager.RINGER_MODE_CHANGED_ACTION) ||
                action.equals(AudioManager.VIBRATE_SETTING_CHANGED_ACTION)) {
            updateSoundModeButton();
        } else if (action.equals(Intent.ACTION_DEFAULT_PHONE_CHANGE)) {
            Log.d(TAG, "ACTION_DEFAULT_PHONE_CHANGE");
            // getSimCardStatus();
            updateDataNetworkButton();
        }
    }

    private void toggleWifi() {
        switch (mWifiState) {
            case WifiManager.WIFI_STATE_DISABLED:
            case WifiManager.WIFI_STATE_UNKNOWN:
                // Disable tethering if enabling Wifi
                int wifiApState = mWifiManager.getWifiApState();
                if ((wifiApState == WifiManager.WIFI_AP_STATE_ENABLING) ||
                        (wifiApState == WifiManager.WIFI_AP_STATE_ENABLED)) {
                    mWifiManager.setWifiApEnabled(null, false);
                }
                mWifiManager.setWifiEnabled(true);
                break;
            case WifiManager.WIFI_STATE_ENABLED:
                mWifiManager.setWifiEnabled(false);
                break;
        }
    }

    private void updateWifiButton() {
        int iconId = R.drawable.quick_switch_wifi_off_sprd;
        this.mWifiState = mWifiManager.getWifiState();

        switch (mWifiState) {
            case WifiManager.WIFI_STATE_DISABLED:
            case WifiManager.WIFI_STATE_UNKNOWN:
                iconId = R.drawable.quick_switch_wifi_off_sprd;
                if (mWifiState == WifiManager.WIFI_STATE_UNKNOWN) {
                    Log.d(TAG, "WIFI_STATE_UNKNOWN");
                }
                break;
            case WifiManager.WIFI_STATE_ENABLED:
                iconId = R.drawable.quick_switch_wifi_on_sprd;
                break;
            case WifiManager.WIFI_STATE_DISABLING:
            case WifiManager.WIFI_STATE_ENABLING:
                iconId = R.drawable.quick_switch_wifi_ing_sprd;
                break;
        }

        updateButtonImage(R.id.quick_switch_wifi_icon, iconId,
                (mWifiState == WifiManager.WIFI_STATE_DISABLED ||
                mWifiState == WifiManager.WIFI_STATE_ENABLED) ? true : false);
    }

    private void toggleBluetooth() {
        // The Bluetooth is disabled when the phone is in aeroplane mode.
        if (mAirplaneEnable) {
            Toast.makeText(mContext, R.string.bluetooth_error_airplane,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        switch (mBluetoothState) {
            case BluetoothAdapter.STATE_OFF:
                mBluetoothAdapter.enable();
                break;
            case BluetoothAdapter.STATE_ON:
                mBluetoothAdapter.disable();
                break;
        }
    }

    private void updateBluetoothButton() {
        if (mBluetoothAdapter == null) {
            return;
        }

        int iconId = R.drawable.quick_switch_bluetooth_off_sprd;
        this.mBluetoothState = mBluetoothAdapter.getState();

        switch (mBluetoothState) {
            case BluetoothAdapter.STATE_OFF:
                iconId = R.drawable.quick_switch_bluetooth_off_sprd;
                break;
            case BluetoothAdapter.STATE_ON:
                iconId = R.drawable.quick_switch_bluetooth_on_sprd;
                break;
            case BluetoothAdapter.STATE_TURNING_ON:
            case BluetoothAdapter.STATE_TURNING_OFF:
                iconId = R.drawable.quick_switch_bluetooth_ing_sprd;
                break;
        }

        Log.d(TAG, "mBluetoothState = " + mBluetoothState);
        updateButtonImage(R.id.quick_switch_bluetooth_icon, iconId,
                (mBluetoothState == BluetoothAdapter.STATE_OFF ||
                mBluetoothState == BluetoothAdapter.STATE_ON));
    }

    private void toggleDataNetwork() {
        // The DataNetwork is disabled when the phone is airoplane mode.
        if (mAirplaneEnable) {
            Toast.makeText(mContext, R.string.datanetwork_error_airplane,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        int imageIndex = 1;
        int[] dataNetSyncImages = new int[2];
        dataNetSyncImages = sDataNetSyncImages;

        if (noCanUsedCard()) {
            updateButtonImage(R.id.quick_switch_mobile_net_icon, dataNetSyncImages[imageIndex]);
            return;
        }

        if (numPhones > 1) {
            mDataDefaultNetworkOn = !mConnManager.getMobileDataEnabledByPhoneId(TelephonyManager
                    .getDefaultDataPhoneId(mContext));
            mConnManager.setMobileDataEnabled(mDataDefaultNetworkOn);
        } else {
            mDataDefaultNetworkOn = !mConnManager.getMobileDataEnabled();
            mConnManager.setMobileDataEnabled(mDataDefaultNetworkOn);
        }

        imageIndex = mDataDefaultNetworkOn ? 0 : 1;
        updateButtonImage(R.id.quick_switch_mobile_net_icon, dataNetSyncImages[imageIndex]);
    }

    private void updateDataNetworkButton() {
        Log.d(TAG, "updateDataNetworkBtn");
        int imageIndex = 1;// init datanetwork is off
        int[] dataNetSyncImages = new int[2];
        dataNetSyncImages = sDataNetSyncImages;
        // isAirplaneMode or no card can use so update View display and return
        if (mAirplaneEnable || noCanUsedCard()) {
            updateButtonImage(R.id.quick_switch_mobile_net_icon, dataNetSyncImages[imageIndex]);
            return;
        }

        if (numPhones > 1) {
            mDataDefaultNetworkOn = mConnManager.getMobileDataEnabledByPhoneId(TelephonyManager
                    .getDefaultDataPhoneId(mContext));
        } else {
            mDataDefaultNetworkOn = mConnManager.getMobileDataEnabled();
        }
        Log.d(TAG, "mDataDefaultNetworkOn = " + mDataDefaultNetworkOn);
        imageIndex = mDataDefaultNetworkOn ? 0 : 1;
        updateButtonImage(R.id.quick_switch_mobile_net_icon, dataNetSyncImages[imageIndex]);
    }

    private void toggleSoundMode() {
        SharedPreferences mRingerModeSharePre = mContext.getSharedPreferences(RINGER_MODE,
                Context.MODE_PRIVATE);
        boolean isSilent = mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT;
        if (isSilent) {
            // reset before mode and set
            int lastMode = mRingerModeSharePre.getInt("soundmode", 2);
            boolean isVibrate = mRingerModeSharePre.getBoolean("vibratemode", false);
            if (lastMode == AudioManager.RINGER_MODE_NORMAL) {
                mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                if (isVibrate) {
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ON);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION,
                            AudioManager.VIBRATE_SETTING_ON);
                } else {
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_OFF);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION,
                            AudioManager.VIBRATE_SETTING_OFF);
                }
            } else if (lastMode == AudioManager.RINGER_MODE_VIBRATE) {
                mAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                        AudioManager.VIBRATE_SETTING_ON);
                mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION,
                        AudioManager.VIBRATE_SETTING_ON);
            }
        } else {
            // save current mode and set silent
            int currentMode = mAudioManager.getRingerMode();
            boolean isVibrate = mAudioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER) == 1;
            Editor editer = mRingerModeSharePre.edit();
            editer.putInt("soundmode", currentMode);
            editer.putBoolean("vibratemode", isVibrate);
            editer.commit();
            mAudioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                    AudioManager.VIBRATE_SETTING_OFF);
            mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION,
                    AudioManager.VIBRATE_SETTING_OFF);
        }
    }

    private void updateSoundModeButton() {
        boolean isSilent = mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT;
        int iconId = R.drawable.quick_switch_general_on_sprd;
        if (isSilent) {
            iconId = R.drawable.quick_switch_silent_on_sprd;
        } else {
            iconId = R.drawable.quick_switch_general_on_sprd;
        }
        updateButtonImage(R.id.quick_switch_sound_icon, iconId);
    }

    private void toggleGps() {
        Settings.Secure.setLocationProviderEnabled(this.mResolver, LocationManager.GPS_PROVIDER,
                !mGpsEnable);
    }

    private void updateGpsButton() {
        int iconId = R.drawable.quick_switch_gps_off_sprd;
        this.mGpsEnable = Settings.Secure.isLocationProviderEnabled(
                this.mResolver, LocationManager.GPS_PROVIDER);

        if (!mGpsEnable) {
            iconId = R.drawable.quick_switch_gps_off_sprd;
        } else {
            iconId = R.drawable.quick_switch_gps_on_sprd;
        }

        updateButtonImage(R.id.quick_switch_gps_icon, iconId);
    }

    private void updateBrightnessButton() {
        int iconId = R.drawable.quick_switch_brightness_off_sprd;

        if (getBrightnessMode(mContext)) {
            iconId = R.drawable.quick_switch_brightness_auto_sprd;
        } else {
            final int brightness = getBrightness(mContext);
            // Set the icon
            if (brightness > FULL_BRIGHTNESS_THRESHOLD) {
                iconId = R.drawable.quick_switch_brightness_on_sprd;
            } else if (brightness > HALF_BRIGHTNESS_THRESHOLD) {
                iconId = R.drawable.quick_switch_brightness_mid_sprd;
            } else {
                iconId = R.drawable.quick_switch_brightness_off_sprd;
            }
        }

        updateButtonImage(R.id.quick_switch_brightness_icon, iconId);
    }

    private void toggleBrightness() {
        try {
            IPowerManager power = IPowerManager.Stub.asInterface(
                    ServiceManager.getService("power"));
            if (power != null) {
                ContentResolver cr = mContext.getContentResolver();
                int brightness = Settings.System.getInt(cr,
                        Settings.System.SCREEN_BRIGHTNESS);
                int brightnessMode = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;
                // Only get brightness setting if available
                if (mContext.getResources().getBoolean(
                        com.android.internal.R.bool.config_automatic_brightness_available)) {
                    brightnessMode = Settings.System.getInt(cr,
                            Settings.System.SCREEN_BRIGHTNESS_MODE);
                }

                // Rotate AUTO -> MINIMUM -> DEFAULT -> MAXIMUM
                // Technically, not a toggle...
                if (brightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                    brightness = MINIMUM_BACKLIGHT;
                    brightnessMode = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;
                } else if (brightness < DEFAULT_BACKLIGHT) {
                    brightness = DEFAULT_BACKLIGHT;
                } else if (brightness < MAXIMUM_BACKLIGHT) {
                    brightness = MAXIMUM_BACKLIGHT;
                } else {
                    brightnessMode = Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
                    brightness = MINIMUM_BACKLIGHT;
                }

                if (mContext.getResources().getBoolean(
                        com.android.internal.R.bool.config_automatic_brightness_available)) {
                    // Set screen brightness mode (automatic or manual)
                    Settings.System.putInt(mContext.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            brightnessMode);
                } else {
                    // Make sure we set the brightness if automatic mode isn't
                    // available
                    brightnessMode = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;
                }
                if (brightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {
                    power.setBacklightBrightness(brightness);
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, brightness);
                }
            }
        } catch (RemoteException e) {
            Log.d(TAG, "toggleBrightness: " + e);
        } catch (Settings.SettingNotFoundException e) {
            Log.d(TAG, "toggleBrightness: " + e);
        }
    }

    /**
     * Gets state of brightness mode.
     *
     * @param context
     * @return true if auto brightness is on.
     */
    private static boolean getBrightnessMode(Context context) {
        try {
            IPowerManager power = IPowerManager.Stub.asInterface(
                    ServiceManager.getService("power"));
            if (power != null) {
                int brightnessMode = Settings.System.getInt(context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE);
                return brightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
            }
        } catch (Exception e) {
            Log.d(TAG, "getBrightnessMode: " + e);
        }
        return false;
    }

    /**
     * Gets brightness level.
     *
     * @param context
     * @return brightness level between 0 and 255.
     */
    private static int getBrightness(Context context) {
        try {
            IPowerManager power = IPowerManager.Stub.asInterface(
                    ServiceManager.getService("power"));
            if (power != null) {
                int brightness = Settings.System.getInt(context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS);
                return brightness;
            }
        } catch (Exception e) {
        }
        return 0;
    }

    private void toggleAirplane() {
        if (!noCanUsedCard()) {
            airPlaneViewEnable(false);
        }
        try {
            mAirplaneEnable = Settings.System.getInt(
                    this.mContext.getContentResolver(),
                    Settings.System.AIRPLANE_MODE_ON) == 1;
        } catch (SettingNotFoundException e) {
            e.printStackTrace();
        }
        Settings.System.putInt(this.mContext.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, mAirplaneEnable ? 0 : 1);
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", !mAirplaneEnable);
        this.mContext.sendBroadcast(intent);
    }

    private void updateAirplaneButton() {
        int status = Settings.System.getInt(this.mResolver, Settings.System.AIRPLANE_MODE_ON, 0);
        int iconId = R.drawable.quick_switch_airplane_off_sprd;
        switch (status) {
            case 0:
                iconId = R.drawable.quick_switch_airplane_off_sprd;
                break;
            case 1:
                iconId = R.drawable.quick_switch_airplane_on_sprd;
                break;
        }
        updateButtonImage(R.id.quick_switch_airplane_icon, iconId);
    }

    private void updateAirplaneStatus() {
        int status = Settings.System.getInt(this.mResolver,
                Settings.System.AIRPLANE_MODE_ON, 0);
        // status is 1 meaning is airplame mode
        switch (status) {
            case 0:
                this.mAirplaneEnable = false;
                // need to obtain the sim state
                getSimCardStatus();
                break;
            case 1:
                this.mAirplaneEnable = true;
                break;
        }
        updateBluetoothButton();
        updateDataNetworkButton();
    }

    private void getSimCardStatus() {
        for (int i = 0; i < numPhones; i++) {
            hasCard[i] = mTelephonyManagers[i].hasIccCard();
            isStandby[i] = isCardDisabled(i);
            Log.d(TAG, "hasCard[" + i + "]=" + hasCard[i] + " , isStandby[" + i + "]="
                    + isStandby[i]);
        }
    }

    private boolean isCardDisabled(int phoneid) {
        return Settings.System.getInt(mContext.getContentResolver(),
                PhoneFactory.getSetting(Settings.System.SIM_STANDBY, phoneid), 1) == 0;
    }

    private boolean noCanUsedCard() {
        if (numPhones > 1) {
            if ((!hasCard[0] || isStandby[0]) && (!hasCard[1] || isStandby[1])) {
                Log.d(TAG, "updateDataNetworkBtn dsds  no card or standby");
                return true;
            }
        } else {
            if (!hasCard[0] || isStandby[0]) {
                Log.d(TAG, "updateDataNetworkBtn single  no card or standby");
                return true;
            }
        }
        return false;
    }

    private void onAirplaneModeChanged() {
        ServiceState serviceState = mPhoneStateReceiver.getServiceState();
        boolean airplaneModeEnabled = false;
        if (numPhones > 1) {
            ServiceState serviceState2 = mPhoneStateReceiver2.getServiceState();
            Log.d(TAG, "serviceState =" + serviceState.getState()
                    + " serviceState2 =" + serviceState2.getState());
            if ((hasCard[0] && !isStandby[0]) && (hasCard[1] && !isStandby[1])) {
                if (isAirplaneModeOn(mContext)) {
                    airplaneModeEnabled = ((serviceState.getState() == ServiceState.STATE_POWER_OFF) && (serviceState2
                            .getState() == ServiceState.STATE_POWER_OFF));
                } else {
                    airplaneModeEnabled = !((serviceState.getState() != ServiceState.STATE_POWER_OFF) && (serviceState2
                            .getState() != ServiceState.STATE_POWER_OFF));
                }
            } else if ((hasCard[0] && !isStandby[0]) && (!hasCard[1] || isStandby[1])) {
                Log.d(TAG, "sim1 ready sim2 standby or has not");
                airplaneModeEnabled = serviceState.getState() == ServiceState.STATE_POWER_OFF;
            } else if ((hasCard[1] && !isStandby[1]) && (!hasCard[0] || isStandby[0])) {
                Log.d(TAG, "sim1 standby or has not sim2 ready");
                airplaneModeEnabled = serviceState2.getState() == ServiceState.STATE_POWER_OFF;
            }
        } else {
            if (!hasCard[0] || isStandby[0]) {
                airplaneModeEnabled = isAirplaneModeOn(mContext);
            } else {
                airplaneModeEnabled = ((serviceState.getState() == ServiceState.STATE_POWER_OFF));
            }
        }

        Log.d(TAG, "airplaneModeEnabled is " + airplaneModeEnabled
                + "  isAirplaneModeOn(mContext) =" + isAirplaneModeOn(mContext));

        if (isAirplaneModeOn(mContext) != airplaneModeEnabled) {
            return;
        }

        updateAirplaneButton();
        airPlaneViewEnable(true);
    }

    private boolean isAirplaneModeOn(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) != 0;
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_SERVICE_STATE_CHANGED:
                    onAirplaneModeChanged();
                    break;
            }
        }
    };

    private void airPlaneViewEnable(boolean enable) {
        LinearLayout airPlaneView = (LinearLayout) mToggleViewGroup
                .findViewById(R.id.quick_switch_airplane_toggle);
        airPlaneView.setEnabled(enable);
    }

    private void toggleAutoRotate() {
        try {
            Settings.System.putInt(mContext.getContentResolver(), Settings.System.
                    ACCELEROMETER_ROTATION, mAutorotateEnable ? 0 : 1);
        } catch (Exception e) {
            Slog.e(TAG, e.getMessage());
        }
    }

    private void updateAutorotateButton() {
        int status = 0;
        int iconId = R.drawable.quick_switch_auto_rotate_off_sprd;
        try {
            status = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION);
        } catch (Exception e) {
            Slog.e(TAG, e.getMessage());
        }
        // status is 0 meaning auto rotate is off,status is 1 meaning auto
        // rotate is on
        switch (status) {
            case 0:
                iconId = R.drawable.quick_switch_auto_rotate_off_sprd;
                mAutorotateEnable = false;
                break;
            case 1:
                iconId = R.drawable.quick_switch_auto_rotate_on_sprd;
                mAutorotateEnable = true;
                break;
        }

        updateButtonImage(R.id.quick_switch_auto_rotate_icon, iconId);
    }

    private void updateButtonImage(int viewId, int drawableId) {
        ImageView itemView = (ImageView) mToggleViewGroup.findViewById(viewId);
        itemView.setImageResource(drawableId);
    }

    private void updateButtonImage(int viewId, int drawableId, boolean clickable) {
        ImageView itemView = (ImageView) mToggleViewGroup.findViewById(viewId);
        itemView.setImageResource(drawableId);
    }

    private ContentObserver mMobileDataObserver = new ContentObserver(
            new Handler()) {

        @Override
        public boolean deliverSelfNotifications() {
            return super.deliverSelfNotifications();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Log.d(TAG, "mMobileDataObserver");
            getSimCardStatus();
            updateDataNetworkButton();
        }
    };

    private ContentObserver mGpsChangedObserver = new ContentObserver(
            new Handler()) {

        @Override
        public boolean deliverSelfNotifications() {
            return super.deliverSelfNotifications();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Log.d(TAG, "mGpsChangedObserver");
            updateGpsButton();
        }
    };

    private ContentObserver mBrightnessChangedObserver = new ContentObserver(
            new Handler()) {

        @Override
        public boolean deliverSelfNotifications() {
            return super.deliverSelfNotifications();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Log.d(TAG, "mBrightnessChangedObserver");
            updateBrightnessButton();
        }
    };

    private ContentObserver mBrightnessAutoModeObserver = new ContentObserver(
            new Handler()) {

        @Override
        public boolean deliverSelfNotifications() {
            return super.deliverSelfNotifications();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Log.d(TAG, "mBrightnessAutoModeObserver");
            updateBrightnessButton();
        }
    };

    private ContentObserver mAirPlaneChangedObserver = new ContentObserver(
            new Handler()) {

        @Override
        public boolean deliverSelfNotifications() {
            return super.deliverSelfNotifications();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Log.d(TAG, "mAirPlaneChangedObserver");
            updateAirplaneStatus();
            if (!noCanUsedCard()) {
                // has used card
                airPlaneViewEnable(false);
            } else {
                // no card can used
                updateAirplaneButton();
            }
        }
    };

    private ContentObserver mAutoRotateChangedObserver = new ContentObserver(
            new Handler()) {

        @Override
        public boolean deliverSelfNotifications() {
            return super.deliverSelfNotifications();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Log.d(TAG, "mAutoRotateChangedObserver");
            updateAutorotateButton();
        }
    };

}
