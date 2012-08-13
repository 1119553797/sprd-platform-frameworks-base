package com.android.systemui.statusbar;

import android.app.StatusBarManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.systemui.R;

public class ToggleListener extends BroadcastReceiver implements View.OnClickListener,
        OnLongClickListener {
    private static final String TAG = "ScrollLayout ToggleListener";
    private static final boolean DEBUG = true;

    //these actions are used to notify settings that the option is enabled or disabled
    private static final String THUNDERST_AUTOROTATE_ACTION = "com.android.systemui.statusbar.Autorotate";
    public static final String THUNDERST_DATANETWORK_ACTION = "com.android.systemui.statusbar.DataNetwork";
    private PhoneStateListener[] mPhoneStateListener;
    private int numPhones = 1;

    //brightness value corresponding to dim, normal and highlight
    private static final int MINIMUM_BACKLIGHT = android.os.Power.BRIGHTNESS_OFF;
    private static final int MAXIMUM_BACKLIGHT = android.os.Power.BRIGHTNESS_ON;
    private static final int MIDDLE_BACKLIGHT = 102;

    //sound mode is the same with that in the settings
    private static final String SOUND_MODE = "sound_mode";
    private static final String SOUND_MODE_VIBRATE = "vibrate";
    private static final String SOUND_MODE_NORMAL = "sound";
    private static final String SOUND_MODE_OUTDOOR = "outdoor";
    private static final String SOUND_MODE_SILENT = "silent";

    private static final int[] sDataNetSyncImages = {
            R.drawable.quick_operation_mobile_data_on,
            R.drawable.quick_operation_mobile_data_off };
//    private static final int[] sDataNetSyncImages_c = {
//            R.drawable.quick_operation_sync_c_on,
//            R.drawable.quick_operation_sync_c_off };
//    private static final int[] sDataNetSyncImages_g = {
//            R.drawable.quick_operation_sync_g_on,
//            R.drawable.quick_operation_sync_g_off };
//    private static final int[] sDataNetSyncImages_na = {
//        R.drawable.quick_operation_sync_na};

    private ToggleViewGroup mToggleViewGroup;
    private AudioManager mAudioManager;
    private BluetoothAdapter mBluetoothAdapter;
    private WifiManager mWifiManager;
    private ConnectivityManager mConnManager;
    private TelephonyManager[] mTelephonyManagers;
    private boolean[] isStandby;
    private boolean[] hasCard;
    
    private final Context mContext;
    private final ContentResolver mResolver;

    private boolean mAirplaneEnable;
    private boolean mGpsEnable;
    private boolean mAutorotateEnable;
    private boolean mSoundModeClickable;

    //indicate whether the data network is on
    private boolean mDataDefaultNetworkOn = false;
    private boolean mDataCdmaNetworkOn = false;
    private boolean mDataGsmNetworkOn = false;

    private static int sDataNetworkSpn = 0;
    public static int mDataNetSyncText = 0;
    public static int mSoundModeText = 0;

    private int mBluetoothState;
    private int mWifiState;

    //save the available long operation actions
//    private static HashMap<Integer, String> mLongClickActions;

    public ToggleListener(ToggleViewGroup toggleViewGroup) {
        mToggleViewGroup = toggleViewGroup;
        mContext = mToggleViewGroup.getContext();
        mResolver = mContext.getContentResolver();
        numPhones = TelephonyManager.getPhoneCount();
        isStandby = new boolean[numPhones];
        hasCard = new boolean[numPhones];

//        mLongClickActions = new HashMap<Integer, String>();
//        mLongClickActions.put(R.id.quick_operation_wifi, "com.android.settings/.wifi.WifiSettings");
//        mLongClickActions.put(R.id.quick_operation_sync, "com.android.phone/.MobileNetworkSettings");
//        mLongClickActions.put(R.id.quick_operation_bluetooth, "com.android.settings/.bluetooth.BluetoothSettings");
//        mLongClickActions.put(R.id.quick_operation_brightness, "com.android.settings/.DisplaySettings");
//        mLongClickActions.put(R.id.quick_operation_auto_rotate, "com.android.settings/.DisplaySettings");
//        mLongClickActions.put(R.id.quick_operation_gps, "com.android.settings/.SecuritySettings");
//        mLongClickActions.put(R.id.quick_operation_soundmode, "com.android.settings/.SoundSettings");

    }

    private Animation MakeAnimation() {
        RotateAnimation ra =  new RotateAnimation(0.0f, -360.0f,
                Animation.RELATIVE_TO_SELF,0.5f,Animation.RELATIVE_TO_SELF, 0.5f);
        ra.setDuration(1000);
        ra.setInterpolator(new AccelerateInterpolator(1.0f));
        ra.setRepeatCount(5);
        return ra;
    }

    //initialize the current state of every quick operation items
    public void init() {
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mWifiManager = (WifiManager)mContext.getSystemService(Context.WIFI_SERVICE);
        mConnManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        mTelephonyManagers = new TelephonyManager[numPhones];
        for (int i = 0; i < numPhones; i++) {
            mTelephonyManagers[i] = (TelephonyManager) mContext.getSystemService(PhoneFactory
                    .getServiceName(Context.TELEPHONY_SERVICE, i));
        }
        mSoundModeClickable = true;

        updateWifiButton();
        updateBluetoothButton();
        updateSoundModeButton();
        updateDataNetworkBtn();
        updateAutorotateButton();
//        updateAirplaneButton();
//        updateGpsButton();
//        updateBrightnessButton();
    }

    @Override
    public boolean onLongClick(View v) {
//        int i = v.getId();
//        Object localObject = (String) mLongClickActions.get(i);
//
//        if (localObject == null) {
//            return false;
//        }
//
//        localObject = ComponentName.unflattenFromString((String)localObject);
//        Intent localIntent = new Intent("android.intent.action.MAIN");
//        localIntent.setComponent((ComponentName) localObject);
//        localIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
//                | Intent.FLAG_ACTIVITY_SINGLE_TOP
//                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
//        this.mContext.startActivity(localIntent);
//        v.setPressed(true);
//
//        //collapse the expanded list after starting the activity
//        try {
//            StatusBarManager statusBarManager = (StatusBarManager) mContext
//                    .getSystemService(Context.STATUS_BAR_SERVICE);
//            statusBarManager.collapse();
//        } catch (Exception ex) {
//            Slog.e(TAG, ex.toString());
//        }

        return true;
    }

    @Override
    public void onClick(View v) {
        Log.d(TAG, "v.getId() = " + v.getId());
        switch (v.getId()) {
            case R.id.quick_operation_wifi:
                Log.d(TAG, "onClick       mWifiState = " + mWifiState);
                if (mWifiState == WifiManager.WIFI_STATE_DISABLED
                        || mWifiState == WifiManager.WIFI_STATE_ENABLED) {
                    toggleWifi();
                }
                break;
            case R.id.quick_operation_bluetooth:
                Log.d(TAG, "onClick       mBluetoothState = " + mBluetoothState);
                if (mBluetoothState == BluetoothAdapter.STATE_OFF
                        || mBluetoothState == BluetoothAdapter.STATE_ON) {
                    toggleBluetooth();
                }
                break;
            case R.id.quick_operation_sound:
                ComponentName con = ComponentName.unflattenFromString("com.android.settings/.SoundSettings");
                Intent localIntent = new Intent("android.intent.action.MAIN");
                localIntent.setComponent(con);
                localIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                this.mContext.startActivity(localIntent);
                try {
                    StatusBarManager statusBarManager = (StatusBarManager) mContext
                            .getSystemService(Context.STATUS_BAR_SERVICE);
                    statusBarManager.collapse();
                } catch (Exception ex) {
                    Slog.e(TAG, ex.toString());
                }
//                toggleSoundMode();
                break;
            case R.id.quick_operation_mobile_net:
                toggleDataNetwork();
                break;
            case R.id.quick_operation_auto_rotate:
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
        filter.addAction(THUNDERST_DATANETWORK_ACTION);
        filter.addAction("android.settings.action.soundmode.changed");
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        filter.addAction(AudioManager.VIBRATE_SETTING_CHANGED_ACTION);
        filter.addAction(Intent.ACTION_DEFAULT_PHONE_CHANGE);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);

        mToggleViewGroup.getContext().registerReceiver(this, filter);

        //listen the fields changed, then refresh the UI
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.AIRPLANE_MODE_ON),
                true, mAirPlaneChangedObserver);
//        mContext.getContentResolver().registerContentObserver(
//                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
//                true, mBrightnessChangedObserver);
//        mContext.getContentResolver().registerContentObserver(
//                Settings.System.getUriFor(Settings.Secure.LOCATION_PROVIDERS_ALLOWED),
//                true, mGpsChangedObserver);
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
        mContext.getContentResolver().unregisterContentObserver(mAutoRotateChangedObserver);
        mContext.getContentResolver().unregisterContentObserver(mMobileDataObserver);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            updateBluetoothButton();
        } else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
            Log.d(TAG, "ACTION_SIM_STATE_CHANGED");
            getSimCardStatus();
            updateDataNetworkBtn();
        } else if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
            updateWifiButton();
        } else if (action.equals( "android.settings.action.soundmode.changed") ||
                action.equals(AudioManager.RINGER_MODE_CHANGED_ACTION) ||
                action.equals(AudioManager.VIBRATE_SETTING_CHANGED_ACTION)) {
            updateSoundModeButton();
            mSoundModeClickable = true;
        } else if (action.equals(Intent.ACTION_DEFAULT_PHONE_CHANGE)) {
            Log.d(TAG, "ACTION_DEFAULT_PHONE_CHANGE");
            updateDataNetworkBtn();
        }
    }

//    private void toggleAirplane() {
//        Settings.System.putInt(this.mContext.getContentResolver(),
//                Settings.System.AIRPLANE_MODE_ON, mAirplaneEnable ? 0 : 1);
//        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
//        intent.putExtra("state", !mAirplaneEnable);
//        this.mContext.sendBroadcast(intent);
//    }

//    private void updateAirplaneButton() {
//        int i = Settings.System.getInt(this.mResolver, Settings.System.AIRPLANE_MODE_ON, 0);
//
//        if (i != 0) {
//            this.mAirplaneEnable = true;
//            i = R.drawable.quick_operation_airplane_on;
//        } else {
//            this.mAirplaneEnable = false;
//            i = R.drawable.quick_operation_airplane_off;
//        }
//
//        updateButtonImage(R.id.quick_operation_airplane, i);
//    }

    //add the soundMode switching
    private void toggleSoundMode() {
        if (!mSoundModeClickable) {
            return;
        }
        Intent intent = new Intent("android.settings.action.soundmode");
        String soundModeValue = getCurrentSoundMode();
        if (DEBUG) {
            Slog.i(TAG,"soundModeValue is " + soundModeValue);
        }

        if (TextUtils.isEmpty(soundModeValue)) {
            Slog.i(TAG,"toggle vibrate failed because getting sound mode is null");
            return;
        }

        if (soundModeValue.equals(SOUND_MODE_OUTDOOR)) {
            intent.putExtra(SOUND_MODE, SOUND_MODE_SILENT);
        } else if (soundModeValue.equals(SOUND_MODE_VIBRATE)){
            intent.putExtra(SOUND_MODE, SOUND_MODE_NORMAL);
        } else if (soundModeValue.equals(SOUND_MODE_SILENT)) {
            intent.putExtra(SOUND_MODE, SOUND_MODE_VIBRATE);
        } else if (soundModeValue.equals(SOUND_MODE_NORMAL)) {
            intent.putExtra(SOUND_MODE, SOUND_MODE_OUTDOOR);
        }

        this.mContext.sendBroadcast(intent);
        mSoundModeClickable = false;
    }

    private String getCurrentSoundMode() {
        final int ringerMode = mAudioManager.getRingerMode();
        int vibrateOn = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.VIBRATE_ON, AudioManager.VIBRATE_SETTING_OFF);
        int vibrateMMS = Settings.System.getInt(mContext.getContentResolver(),
                "vibrate_mms", AudioManager.VIBRATE_SETTING_OFF);
        String mode = judgeSoundMode(ringerMode, vibrateOn, vibrateMMS);

        if (TextUtils.isEmpty(mode)) {
            return null;
        }

        return mode;
    }

    private String judgeSoundMode(int ringerMode,int vibrateOn,int vibrateMMS){
        String mode=null;
        if (AudioManager.RINGER_MODE_SILENT == ringerMode) {
            if (0 == vibrateOn && 0 == vibrateMMS) {
                mode = SOUND_MODE_SILENT;
            } else {
                mode = SOUND_MODE_VIBRATE;
            }
        } else if (AudioManager.RINGER_MODE_VIBRATE == ringerMode) {
            mode = SOUND_MODE_VIBRATE;
        } else if (AudioManager.RINGER_MODE_NORMAL == ringerMode) {
            if (0 == vibrateOn && 0 == vibrateMMS) {
                mode = SOUND_MODE_NORMAL;
            } else {
                mode = SOUND_MODE_OUTDOOR;
            }
        }
        Slog.d(TAG,"ringerMode: " + ringerMode +" vibrateOn: " + vibrateOn +" vibrateMMS: " + vibrateMMS);
        Slog.d(TAG,"judgeSoundMode mode:" + mode);
        return mode;
    }

    private void updateSoundModeButton() {
        int i = 0;
        int imagebarId = 0;
        String soundMode = getCurrentSoundMode();
        if (DEBUG) {
            Slog.i(TAG,"soundMode is " + soundMode);
        }

        if (TextUtils.isEmpty(soundMode)) {
            Slog.i(TAG,"updateVibrateButton failed because getting sound mode is null");
            return;
        }

        if (soundMode.equals(SOUND_MODE_VIBRATE)) {
            i = R.drawable.quick_operation_vibrate_on;
            imagebarId = R.drawable.bottom_bar_icon_on;
            mSoundModeText = R.string.mode_vibrate_name;
        } else if (soundMode.equals(SOUND_MODE_OUTDOOR)) {
            i = R.drawable.quick_operation_sound_on;
            imagebarId = R.drawable.bottom_bar_icon_off;
            mSoundModeText = R.string.mode_sound_name;
        } else if (soundMode.equals(SOUND_MODE_NORMAL)) {
            i = R.drawable.quick_operation_sound_on;
            imagebarId = R.drawable.bottom_bar_icon_off;
            mSoundModeText = R.string.mode_sound_name;
        } else if (soundMode.equals(SOUND_MODE_SILENT)) {
            i = R.drawable.quick_operation_sound_off;
            imagebarId = R.drawable.bottom_bar_icon_on;
            mSoundModeText = R.string.mode_silent_name;
        }

        updateButtonImageAndText(R.id.quick_operation_sound_icon, R.id.quick_operation_sound_name, i, mSoundModeText);
        updateButtonBottomImageBar(R.id.quick_operation_sound_bottom_bar, imagebarId);
    }

//    private void toggleScreenLock() {
//        PowerManager pm = (PowerManager)mContext.getSystemService(Context.POWER_SERVICE);
//        long l = SystemClock.uptimeMillis() + 1L;
//        pm.goToSleep(l);
//    }
//
//    private void toggleGps() {
//        Settings.Secure.setLocationProviderEnabled(this.mResolver, "gps",
//                !mGpsEnable);
//    }
//
//    private void updateGpsButton() {
//        int i = 0;
//
//        this.mGpsEnable = Settings.Secure.isLocationProviderEnabled(
//                this.mResolver, "gps");
//
//        if (!mGpsEnable) {
//            i = R.drawable.quick_operation_gps_off;
//        } else {
//            i = R.drawable.quick_operation_gps_on;
//        }
//
//        updateButtonImage(R.id.quick_operation_gps, i);
//    }

    private void updateButtonImage(int viewid, int drawableid) {
        ImageView localImageView = (ImageView)mToggleViewGroup.findViewById(viewid);
        localImageView.setImageResource(drawableid);
    }

    private void updateButtonImageAndText(int viewid, int textid, int drawableid, int contextid) {
        ImageView localImageView = (ImageView)mToggleViewGroup.findViewById(viewid);
        TextView localTextView = (TextView) mToggleViewGroup.findViewById(textid);
        localImageView.setImageResource(drawableid);
        localTextView.setText(contextid);
    }

    private void updateButtonImage(int viewid, int drawableid, boolean clickable) {
        ImageView localImageView = (ImageView) mToggleViewGroup.findViewById(viewid);
        localImageView.setImageResource(drawableid);
    }

    private void updateButtonBottomImageBar(int viewid, int drawableid) {
        ImageView localImageView = (ImageView)mToggleViewGroup.findViewById(viewid);
        localImageView.setImageResource(drawableid);
    }

    private void toggleWifi() {
        if (mWifiState == WifiManager.WIFI_STATE_DISABLED) {
            mWifiManager.setWifiEnabled(true);
        } else if (mWifiState == WifiManager.WIFI_STATE_ENABLED) {
            mWifiManager.setWifiEnabled(false);
        }
    }

    private void updateWifiButton() {
        int i = 0;
        int imagebarId = 0;
        this.mWifiState = mWifiManager.getWifiState();

        if (mWifiState == WifiManager.WIFI_STATE_DISABLED) {
            i = R.drawable.quick_operation_wifi_off;
            imagebarId = R.drawable.bottom_bar_icon_off;
        } else if (mWifiState == WifiManager.WIFI_STATE_ENABLED) {
            i = R.drawable.quick_operation_wifi_on;
            imagebarId = R.drawable.bottom_bar_icon_on;
        } else if (mWifiState == WifiManager.WIFI_STATE_DISABLING || mWifiState == WifiManager.WIFI_STATE_ENABLING) {
            i = R.drawable.quick_operation_wifi_ing;
            imagebarId = R.drawable.bottom_bar_icon_off;
        }

        updateButtonImage(R.id.quick_operation_wifi_icon, i, (mWifiState == WifiManager.WIFI_STATE_DISABLED ||
                mWifiState == WifiManager.WIFI_STATE_ENABLED) ? true:false);
        updateButtonBottomImageBar(R.id.quick_operation_wifi_bottom_bar, imagebarId);
    }

    private void toggleAutoRotate() {
        try {
            Settings.System.putInt(mContext.getContentResolver(), Settings.System.
                    ACCELEROMETER_ROTATION, mAutorotateEnable ? 0 : 1);
        } catch (Exception e) {
            Slog.e(TAG, e.getMessage());
        }

        Intent intent = new Intent(THUNDERST_AUTOROTATE_ACTION);
        this.mContext.sendBroadcast(intent);
    }

    private void updateAutorotateButton() {
        int i = 0;
        int imagebarId = 0;
        try {
            i = Settings.System.getInt(mContext.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION);
        } catch (Exception e) {
            Slog.e(TAG, e.getMessage());
        }

        if (i == 0) {
            i = R.drawable.quick_operation_auto_rotate_off;
            imagebarId = R.drawable.bottom_bar_icon_off;
            mAutorotateEnable = false;
        } else {
            i = R.drawable.quick_operation_auto_rotate_on;
            imagebarId = R.drawable.bottom_bar_icon_on;
            mAutorotateEnable = true;
        }

        updateButtonImage(R.id.quick_operation_auto_rotate_icon, i);
        updateButtonBottomImageBar(R.id.quick_operation_auto_rotate_bottom_bar, imagebarId);
    }

    private void toggleDataNetwork() {
        // The DataNetwork is disabled when the phone is in aeroplane mode.
        if (mAirplaneEnable) {
            Toast.makeText(mContext, R.string.datanetwork_error_airplane,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        int imageIndex = 1;
        int[] dataNetSyncImages = new int[2];
        dataNetSyncImages = sDataNetSyncImages;

        if (noCanUsedCard(imageIndex)) return;
        if (numPhones > 1) {
            mDataDefaultNetworkOn = !mConnManager.getMobileDataEnabledByPhoneId(TelephonyManager.getDefaultDataPhoneId(mContext));
            mConnManager.setMobileDataEnabled(mDataDefaultNetworkOn);
        } else {
            mDataDefaultNetworkOn = !mConnManager.getMobileDataEnabled();
            mConnManager.setMobileDataEnabled(mDataDefaultNetworkOn);
        }

        imageIndex = mDataDefaultNetworkOn ? 0 : 1;
        updateButtonImage(R.id.quick_operation_mobile_net_icon, dataNetSyncImages[imageIndex]);
        updateButtonBottomImageBar(R.id.quick_operation_mobile_net_bottom_bar, mDataDefaultNetworkOn ? R.drawable.bottom_bar_icon_on : R.drawable.bottom_bar_icon_off);
    }

    private final void updateDataNetworkBtn() {
        Log.d(TAG, "updateDataNetworkBtn");
        int imageIndex = 1;
        int[] dataNetSyncImages = new int[2];
        dataNetSyncImages = sDataNetSyncImages;
        if (mAirplaneEnable) {
            updateButtonImage(R.id.quick_operation_mobile_net_icon, dataNetSyncImages[imageIndex]);
            updateButtonBottomImageBar(R.id.quick_operation_mobile_net_bottom_bar, R.drawable.bottom_bar_icon_off);
            return;
        }
        
        if (noCanUsedCard(imageIndex)) return;

        if (numPhones > 1) {
            mDataDefaultNetworkOn = mConnManager.getMobileDataEnabledByPhoneId(TelephonyManager.getDefaultDataPhoneId(mContext));
        } else {
            mDataDefaultNetworkOn = mConnManager.getMobileDataEnabled();
        }
        Log.d(TAG, "mDataDefaultNetworkOn = " + mDataDefaultNetworkOn);
        imageIndex = mDataDefaultNetworkOn ? 0 : 1;
        updateButtonImage(R.id.quick_operation_mobile_net_icon, dataNetSyncImages[imageIndex]);
        updateButtonBottomImageBar(R.id.quick_operation_mobile_net_bottom_bar, mDataDefaultNetworkOn ? R.drawable.bottom_bar_icon_on : R.drawable.bottom_bar_icon_off);
    }

//    private void getDataNetworkSpn() {
//        // Update icon only if DDS in properly set
//        sDataNetworkSpn = Settings.System.getInt(mContext.getContentResolver(),
//                Settings.System.MULTI_SIM_DATA_CALL, 0);
//        int curDataSimState = mTeleManager.getSimState(sDataNetworkSpn);
//        int absent = TelephonyManager.SIM_STATE_ABSENT;
//        if (absent == curDataSimState) {
//            switch (sDataNetworkSpn) {
//            case 0:
//                if (absent != mTeleManager.getSimState(1)) {
//                    sDataNetworkSpn = 1;
//                } else {
//                    sDataNetworkSpn = -1;
//                }
//                break;
//            case 1:
//                if (absent != mTeleManager.getSimState(0)) {
//                    sDataNetworkSpn = 0;
//                } else {
//                    sDataNetworkSpn = -1;
//                }
//                break;
//            }
//        }
//    }

    private void toggleBluetooth() {
        //The Bluetooth is disabled when the phone is in aeroplane mode.
        if (mAirplaneEnable) {
            Toast.makeText(mContext, R.string.bluetooth_error_airplane,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (mBluetoothState == BluetoothAdapter.STATE_OFF) {
            mBluetoothAdapter.enable();
        } else if (mBluetoothState == BluetoothAdapter.STATE_ON) {
            mBluetoothAdapter.disable();
        }
    }

    private void updateBluetoothButton() {
        if (mBluetoothAdapter == null)
        {
            return;
        }

        int i = 0;
        int imagebarId = 0;
        this.mBluetoothState = mBluetoothAdapter.getState();

        if (mBluetoothState == BluetoothAdapter.STATE_OFF) {
            i = R.drawable.quick_operation_bluetooth_off;
            imagebarId = R.drawable.bottom_bar_icon_off;
        } else if (mBluetoothState == BluetoothAdapter.STATE_ON) {
            i = R.drawable.quick_operation_bluetooth_on;
            imagebarId = R.drawable.bottom_bar_icon_on;
        } else if (mBluetoothState == BluetoothAdapter.STATE_TURNING_ON || mBluetoothState == BluetoothAdapter.STATE_TURNING_OFF) {
            i = R.drawable.quick_operation_bluetooth_ing;
            imagebarId = R.drawable.bottom_bar_icon_off;
        }
        //the blue tooth state is STATE_TURNING_ON or STATE_TURNING_OFF, it is forbidden to
        //click the button
        Log.d(TAG, "mBluetoothState = " + mBluetoothState);
        updateButtonImage(R.id.quick_operation_bluetooth_icon, i, (mBluetoothState == BluetoothAdapter.STATE_OFF ||
                mBluetoothState == BluetoothAdapter.STATE_ON));
        updateButtonBottomImageBar(R.id.quick_operation_bluetooth_bottom_bar, imagebarId);
    }

//    private void updateBrightnessButton() {
//        int i = R.drawable.quick_operation_brightness_on;
//
//        if (getBrightnessMode(mContext)) {
//            i = R.drawable.quick_operation_brightness_on;
//        } else {
//            int j = getBrightness(mContext);
//            if (j < MIDDLE_BACKLIGHT) {
//                i = R.drawable.quick_operation_brightness_off;
//            } else if (j >= MIDDLE_BACKLIGHT && j < MAXIMUM_BACKLIGHT ) {
//                i = R.drawable.quick_operation_brightness_low;
//            } else if (j == MAXIMUM_BACKLIGHT) {
//                i = R.drawable.quick_operation_brightness_high;
//            }
//        }
//
//        //updateButtonImage(R.id.quick_operation_brightness, i);
//    }
//
//    private void toggleBrightness() {
//        // we can toggle the brightness just the brightness mode is manual, not automatic
//        if (!getBrightnessMode(mContext)) {
//            int i = getBrightness(mContext);
//
//            if (i < MIDDLE_BACKLIGHT) {
//                setBrightness(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL, MIDDLE_BACKLIGHT);
//            } else if (i >= MIDDLE_BACKLIGHT && i< MAXIMUM_BACKLIGHT) {
//                setBrightness(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL, MAXIMUM_BACKLIGHT);
//            } else if (i == MAXIMUM_BACKLIGHT) {
//                setBrightness(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL, MINIMUM_BACKLIGHT);
//            }
//        }
//
//        Intent intent = new Intent("com.thunderst.CHANGE_SYSTE_BRIGHTNESS_SWITCH");
//        this.mContext.sendBroadcast(intent);
//    }

//    private static boolean getBrightnessMode(Context context) {
//        try {
//            int brightnessMode = Settings.System.getInt(context
//                    .getContentResolver(),
//                    Settings.System.SCREEN_BRIGHTNESS_MODE);
//            return brightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
//        } catch (Exception e) {
//            Slog.d(TAG, "getBrightnessMode: ", e);
//        }
//
//        return false;
//    }
//
//    private int getBrightness(Context context) {
//        int brightness = 0;
//
//        try {
//            brightness = Settings.System.getInt(context.getContentResolver(),
//                    Settings.System.SCREEN_BRIGHTNESS);
//        } catch (Exception e) {
//            Slog.d(TAG, "getBrightness: " + e);
//        }
//
//        return brightness;
//    }

//    private void setBrightness(int brightmode, int brightness) {
//        try {
//            IPowerManager localIPowerManager = IPowerManager.Stub
//                    .asInterface(ServiceManager.getService("power"));
//
//            if (localIPowerManager != null) {
//                if (brightmode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {
//                    localIPowerManager.setBacklightBrightness(brightness);
//                    Settings.System.putInt(this.mResolver, Settings.System.SCREEN_BRIGHTNESS, brightness);
//                }
//            }
//        } catch (RemoteException e) {
//            Slog.d(TAG, "setBrightness", e);
//        }
//    }

    private ContentObserver mAirPlaneChangedObserver = new ContentObserver(
            new Handler()) {

        @Override
        public boolean deliverSelfNotifications() {
            return super.deliverSelfNotifications();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
//            updateAirplaneButton();
              updateAirplaneStatus();
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
//            updateBrightnessButton();
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
//            updateGpsButton();
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
            Log.d(TAG, "mAutoRotateChangedObserver  onChange");
            updateAutorotateButton();
        }
    };

    private ContentObserver mMobileDataObserver = new ContentObserver(
            new Handler()) {

        @Override
        public boolean deliverSelfNotifications() {
            return super.deliverSelfNotifications();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Log.d(TAG, "mMobileDataObserver  onChange");
            getSimCardStatus();
            updateDataNetworkBtn();
        }
    };
    
    private void updateAirplaneStatus() {
        int i = Settings.System.getInt(this.mResolver,
                Settings.System.AIRPLANE_MODE_ON, 0);

        if (i != 0) {
            this.mAirplaneEnable = true;
        } else {
            this.mAirplaneEnable = false;
        }
        if (this.mAirplaneEnable == false) {
            getSimCardStatus();
        }
        updateBluetoothButton();
        updateDataNetworkBtn();
    }
    
    private void getSimCardStatus(){
        for(int i=0;i < numPhones;i++){
            hasCard[i] = mTelephonyManagers[i].hasIccCard();
            isStandby[i]=isCardDisabled(i);
            Log.d(TAG, "hasCard["+i+"]="+hasCard[i] + " , isStandby["+i+"]="+isStandby[i]);
        }
    }

    private boolean isCardDisabled(int phoneid) {
        return Settings.System.getInt(mContext.getContentResolver(),
                PhoneFactory.getSetting(Settings.System.SIM_STANDBY, phoneid), 1) == 0;
    }

    private boolean noCanUsedCard(int index) {
        if (numPhones > 1) {
            if ((!hasCard[0] || isStandby[0]) && (!hasCard[1] || isStandby[1])) {
                Log.d(TAG, "updateDataNetworkBtn dsds  no card or standby");
                updateButtonImage(R.id.quick_operation_mobile_net_icon, sDataNetSyncImages[index]);
                updateButtonBottomImageBar(R.id.quick_operation_mobile_net_bottom_bar, R.drawable.bottom_bar_icon_off);
                return true;
            }
        } else {
            if (!hasCard[0] || isStandby[0]) {
                Log.d(TAG, "updateDataNetworkBtn single  no card or standby");
                updateButtonImage(R.id.quick_operation_mobile_net_icon, sDataNetSyncImages[index]);
                updateButtonBottomImageBar(R.id.quick_operation_mobile_net_bottom_bar, R.drawable.bottom_bar_icon_off);
                return true;
            }
        }
        return false;
    }
}
