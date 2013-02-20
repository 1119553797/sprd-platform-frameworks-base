/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wimax.WimaxManagerConstants;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.Telephony;
import android.provider.Telephony.Intents;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Slog;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.app.IBatteryStats;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.cdma.EriInfo;
import com.android.server.am.BatteryStatsService;
import com.android.internal.util.AsyncChannel;

//add by spreadst_lc for cmcc wifi feature start
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.provider.Downloads;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.os.SystemProperties;
import android.sim.Sim;
import android.sim.SimManager;
import android.app.StatusBarManager;
import android.graphics.Color;
import android.util.Log;
//add by spreadst_lc for cmcc wifi feature end

import com.android.systemui.R;

public class NetworkController extends BroadcastReceiver {
    // debug
    static final String TAG = "StatusBar.NetworkController";
    private static final String INSERT_SIMS_CHANGED_ACTION = "android.sim.INSERT_SIMS_CHANGED";
    public static boolean UNIVERSEUI_SUPPORT = SystemProperties.getBoolean("universe_ui_support",false);
    static final boolean DEBUG = true;
    static final boolean CHATTY = false; // additional diagnostics, but not logspew

    // telephony
    boolean mHspaDataDistinguishable;
    final TelephonyManager[] mPhone;
    boolean[] mDataConnected;
    IccCard.State[] mSimState;
    int[] mPhoneState;
    int[] mDataNetType;
    int[] mDataState;
    int[] mDataActivity;
    int[] mSimColor;
    ServiceState[] mServiceState;
    SignalStrength[] mSignalStrength;
    private PhoneStateListener[] mPhoneStateListener;
    int[][] mDataIconList = {TelephonyIcons.DATA_G[0],TelephonyIcons.DATA_G[0]};
    String[] mNetworkName;
    String mNetworkNameDefault;
    String mNetworkNameSeparator;
    int[] mPhoneSignalIconId;
    int[] mDataDirectionIconId; // data + data direction on phones
    int[] mDataSignalIconId;
    int[] mDataTypeIconId;
    int mAirplaneIconId;
    boolean mDataActive;
    int[] mMobileActivityIconId; // overlay arrows for data direction
    int[] mLastSignalLevel;
    boolean mShowPhoneRSSIForData = false;
    boolean mShowAtLeastThreeGees = false;
    boolean mAlwaysShowCdmaRssi = false;

    String[] mContentDescriptionPhoneSignal;
    String mContentDescriptionWifi;
    String mContentDescriptionWimax;
    String[] mContentDescriptionCombinedSignal;
    String[] mContentDescriptionDataType;

    // wifi
    final WifiManager mWifiManager;
    AsyncChannel mWifiChannel;
    boolean mWifiEnabled, mWifiConnected;
    int mWifiRssi, mWifiLevel;
    String mWifiSsid;
    int mWifiIconId = 0;
    int mWifiActivityIconId = 0; // overlay arrows for wifi direction
    int mWifiActivity = WifiManager.DATA_ACTIVITY_NONE;

    // bluetooth
    private boolean mBluetoothTethered = false;
    private int mBluetoothTetherIconId =
        com.android.internal.R.drawable.stat_sys_tether_bluetooth;

    //wimax
    private boolean mWimaxSupported = false;
    private boolean mIsWimaxEnabled = false;
    private boolean mWimaxConnected = false;
    private boolean mWimaxIdle = false;
    private int mWimaxIconId = 0;
    private int mWimaxSignal = 0;
    private int mWimaxState = 0;
    private int mWimaxExtraState = 0;

    // data connectivity (regardless of state, can we access the internet?)
    // state of inet connection - 0 not connected, 100 connected
    private boolean mConnected = false;
    private int mConnectedNetworkType = ConnectivityManager.TYPE_NONE;
    private String mConnectedNetworkTypeName;
    private int mInetCondition = 0;
    private static final int INET_CONDITION_THRESHOLD = 50;

    private boolean mAirplaneMode = false;
    private boolean mLastAirplaneMode = true;

    // our ui
    Context mContext;
    ArrayList<ImageView> mPhoneSignalIconViews = new ArrayList<ImageView>();
    ArrayList<ImageView> mDataDirectionIconViews = new ArrayList<ImageView>();
    ArrayList<ImageView> mDataDirectionOverlayIconViews = new ArrayList<ImageView>();
    ArrayList<ImageView> mWifiIconViews = new ArrayList<ImageView>();
    ArrayList<ImageView> mWimaxIconViews = new ArrayList<ImageView>();
    ArrayList<ImageView> mCombinedSignalIconViews = new ArrayList<ImageView>();
    ArrayList<ImageView> mDataTypeIconViews = new ArrayList<ImageView>();
    ArrayList<TextView> mCombinedLabelViews = new ArrayList<TextView>();
    ArrayList<TextView> mMobileLabelViews = new ArrayList<TextView>();
    ArrayList<TextView> mMobileLabelViews1 = new ArrayList<TextView>();
    ArrayList<TextView> mWifiLabelViews = new ArrayList<TextView>();
    ArrayList<TextView> mEmergencyLabelViews = new ArrayList<TextView>();
    ArrayList<SignalCluster> mSignalClusters = new ArrayList<SignalCluster>();
    int[] mLastPhoneSignalIconId;
    int[] mLastDataDirectionIconId;
    int[] mLastDataDirectionOverlayIconId;
    int mLastWifiIconId = -1;
    int mLastWimaxIconId = -1;
    int mLastWifiActivityIconId;
    int[] mLastCombinedSignalIconId;
    int[] mLastDataTypeIconId;
    int[] mLastSimColor;
    String mLastLabel = "";
    String mLastCombinedLabel = "";

    int numPhones;
    int mDDS = 0;
    private boolean mHasMobileDataFeature;

    boolean mDataAndWifiStacked = false;

    // yuck -- stop doing this here and put it in the framework
    IBatteryStats mBatteryStats;

    //add by spreadst_lc for cmcc wifi feature start
    public static final String TRUSTED_LIST_CONTENT = "content://settings/trusted_list";
    public static final Uri TRUSTED_LIST_URI = Uri.parse(TRUSTED_LIST_CONTENT);
    private String[] mSSIDs;
    private int mIndex = 0;
    private boolean isConnected = false;
    private AlertDialog weakSignalDialog = null;
    private ConnectivityManager mConnectivityManager;
    private boolean supportCMCC = false;
    //add by spreadst_lc for cmcc wifi feature

    public interface SignalCluster {
        //mod by TS_LC for data call icon :start
        void setWifiIndicators(boolean visible, boolean connected,int strengthIcon, int activityIcon,
                String contentDescription);
        void setMobileDataIndicators(boolean visible, int strengthIcon, boolean mDataConnected, int activityIcon,
                int typeIcon, String contentDescription, String typeContentDescription, int phoneColor,int phoneId);
        void setIsAirplaneMode(boolean is);
    }

    /**
     * Construct this controller object and register for updates.
     */
    public NetworkController(Context context) {
        mContext = context;
        final Resources res = context.getResources();

        numPhones = TelephonyManager.getPhoneCount();
        Log.d(TAG, "numPhones = "+numPhones);
        mPhone = new TelephonyManager[numPhones];
        mSignalStrength = new SignalStrength[numPhones];
        mServiceState = new ServiceState[numPhones];
        mSimState = new IccCard.State[numPhones];
        mSimColor = new int[numPhones];
        mPhoneState = new int[numPhones];
        mDataNetType = new int[numPhones];
        mDataState = new int[numPhones];
        mPhoneSignalIconId = new int[numPhones];
        mDataTypeIconId = new int[numPhones];
        mMobileActivityIconId = new int[numPhones];
        mContentDescriptionPhoneSignal = new String[numPhones];
        mNetworkName = new String[numPhones];
        mDataConnected = new boolean[numPhones];
        mDataSignalIconId = new int[numPhones];
        mDataDirectionIconId = new int[numPhones];
        mLastPhoneSignalIconId = new int[numPhones];
        mLastSimColor = new int[numPhones];
        mLastDataDirectionIconId = new int[numPhones];
        mLastDataDirectionOverlayIconId = new int[numPhones];
        mLastCombinedSignalIconId = new int[numPhones];
        mLastDataTypeIconId = new int[numPhones];
        mDataActivity = new int[numPhones];
        mContentDescriptionCombinedSignal = new String[numPhones];
        mContentDescriptionDataType = new String[numPhones];
        mLastSignalLevel = new int[numPhones];

        ConnectivityManager cm = (ConnectivityManager)mContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        mHasMobileDataFeature = cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE);

        mShowPhoneRSSIForData = res.getBoolean(R.bool.config_showPhoneRSSIForData);
        mShowAtLeastThreeGees = res.getBoolean(R.bool.config_showMin3G);
        mAlwaysShowCdmaRssi = res.getBoolean(
                com.android.internal.R.bool.config_alwaysUseCdmaRssi);

        // set up the default wifi icon, used when no radios have ever appeared
        updateWifiIcons();
        updateWimaxIcons();

        // telephony
        /*mPhone = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
        mPhone.listen(mPhoneStateListener,
                          PhoneStateListener.LISTEN_SERVICE_STATE
                        | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                        | PhoneStateListener.LISTEN_CALL_STATE
                        | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                        | PhoneStateListener.LISTEN_DATA_ACTIVITY);
        mHspaDataDistinguishable = mContext.getResources().getBoolean(
                R.bool.config_hspa_data_distinguishable);*/
        mNetworkNameSeparator = mContext.getString(R.string.status_bar_network_name_separator);
        mNetworkNameDefault = mContext.getString(
                com.android.internal.R.string.lockscreen_carrier_default);
        mPhoneStateListener = new PhoneStateListener[numPhones];
        for (int i=0; i < numPhones; i++) {
            mPhone[i] = (TelephonyManager)context.getSystemService(PhoneFactory.getServiceName(
                    Context.TELEPHONY_SERVICE, i));
            //mPhone[i] = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);

            mPhoneStateListener[i] = getPhoneStateListener(i);
            // register for phone state notifications.

            mPhone[i].listen(mPhoneStateListener[i],
                              PhoneStateListener.LISTEN_SERVICE_STATE
                            | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                            | PhoneStateListener.LISTEN_CALL_STATE
                            | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                            | PhoneStateListener.LISTEN_DATA_ACTIVITY);

            mDataConnected[i] = false;
            mSimState[i] = IccCard.State.READY;
            mPhoneState[i] = TelephonyManager.CALL_STATE_IDLE;
            mDataNetType[i] = TelephonyManager.NETWORK_TYPE_UNKNOWN;
            mDataState[i] = TelephonyManager.DATA_DISCONNECTED;
            mDataActivity[i] = TelephonyManager.DATA_ACTIVITY_NONE;
            mLastPhoneSignalIconId[i] = -1;
            mLastDataDirectionIconId[i] = -1;
            mLastDataDirectionOverlayIconId[i] = -1;
            mLastCombinedSignalIconId[i] = -1;
            mLastDataTypeIconId[i] = -1;
            mLastSimColor[i] = -1;
            mNetworkName[i] = mNetworkNameDefault;
        }
        mHspaDataDistinguishable = mContext.getResources().getBoolean(
                R.bool.config_hspa_data_distinguishable);

        // wifi
        //add by spreadst_lc for cmcc wifi feature start
        mConnectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        supportCMCC = SystemProperties.get("ro.operator").equals("cmcc");
        //add by spreadst_lc for cmcc wifi feature start
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        Handler handler = new WifiHandler();
        mWifiChannel = new AsyncChannel();
        Messenger wifiMessenger = mWifiManager.getWifiServiceMessenger();
        if (wifiMessenger != null) {
            mWifiChannel.connect(mContext, handler, wifiMessenger);
        }

        // broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        filter.addAction("interruptforfaraway"); //add by spreadst_lc for cmcc wifi feature
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(Telephony.Intents.SPN_STRINGS_UPDATED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(ConnectivityManager.INET_CONDITION_ACTION);
        filter.addAction(SimManager.INSERT_SIMS_CHANGED_ACTION);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mWimaxSupported = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_wimaxEnabled);
        if(mWimaxSupported) {
            filter.addAction(WimaxManagerConstants.WIMAX_NETWORK_STATE_CHANGED_ACTION);
            filter.addAction(WimaxManagerConstants.SIGNAL_LEVEL_CHANGED_ACTION);
            filter.addAction(WimaxManagerConstants.NET_4G_STATE_CHANGED_ACTION);
        }
        context.registerReceiver(this, filter);

        // AIRPLANE_MODE_CHANGED is sent at boot; we've probably already missed it
        updateAirplaneMode();

        // yuck
        mBatteryStats = BatteryStatsService.getService();
    }

    public boolean hasMobileDataFeature() {
        return mHasMobileDataFeature;
    }

    public boolean isEmergencyOnly() {
      boolean  isEmg = false;
      int count = mServiceState.length;
      for(int i = 0; count > i;i++){
         isEmg = isEmg || (mServiceState[i] != null && mServiceState[i].isEmergencyOnly());
       }
     return isEmg;
    }

    public void addPhoneSignalIconView(ImageView v) {
        mPhoneSignalIconViews.add(v);
    }

    public void addDataDirectionIconView(ImageView v) {
        mDataDirectionIconViews.add(v);
    }

    public void addDataDirectionOverlayIconView(ImageView v) {
        mDataDirectionOverlayIconViews.add(v);
    }

    public void addWifiIconView(ImageView v) {
        mWifiIconViews.add(v);
    }
    public void addWimaxIconView(ImageView v) {
        mWimaxIconViews.add(v);
    }

    public void addCombinedSignalIconView(ImageView v) {
        mCombinedSignalIconViews.add(v);
    }

    public void addDataTypeIconView(ImageView v) {
        mDataTypeIconViews.add(v);
    }

    public void addCombinedLabelView(TextView v) {
        mCombinedLabelViews.add(v);
    }

    public void addMobileLabelView(TextView v) {
        mMobileLabelViews.add(v);
    }

    public void addWifiLabelView(TextView v) {
        mWifiLabelViews.add(v);
    }

    public void addMobileLabelView1(TextView v) {
        mMobileLabelViews1.add(v);
    }

    public void addEmergencyLabelView(TextView v) {
        mEmergencyLabelViews.add(v);
    }

    public void addSignalCluster(SignalCluster cluster) {
        mSignalClusters.add(cluster);
        refreshSignalCluster(cluster);
    }

    public void refreshSignalCluster(SignalCluster cluster) {
        cluster.setWifiIndicators(
                mWifiEnabled, // only show wifi in the cluster if enabled
                mWifiConnected, // only show wifi wether connected an AP
                mWifiIconId,
                mWifiActivityIconId,
                mContentDescriptionWifi);

        if (mIsWimaxEnabled && mWimaxConnected) {
            // wimax is special
            cluster.setMobileDataIndicators(
                    true,
                    mWimaxIconId,
                    mDataConnected[mDDS],
                    mMobileActivityIconId[mDDS],
                    mDataTypeIconId[mDDS],
                    mContentDescriptionWimax,
                    mContentDescriptionDataType[mDDS],Color.TRANSPARENT, mDDS);
        } else {
            // normal mobile data
            for (int i=0; i < numPhones; i++) {
                if(UNIVERSEUI_SUPPORT){
                    int simColor = mSimColor[i];
                    if(mAirplaneMode){
                        simColor = Color.TRANSPARENT;
                    }
                cluster.setMobileDataIndicators(
                        mHasMobileDataFeature,
                        mShowPhoneRSSIForData ? mPhoneSignalIconId[i] : mDataSignalIconId[i],
                        mDataConnected[i] || mPhone[i].isNetworkRoaming(),
                        mMobileActivityIconId[i],
                        mDataTypeIconId[i],
                        mContentDescriptionPhoneSignal[i],
                        mContentDescriptionDataType[i],simColor, i);
                }else{
                    cluster.setMobileDataIndicators(
                            mHasMobileDataFeature,
                            mShowPhoneRSSIForData ? mPhoneSignalIconId[i] : mDataSignalIconId[i],
                            mDataConnected[i] || mPhone[i].isNetworkRoaming(),
                            mMobileActivityIconId[i],
                            mDataTypeIconId[i],
                            mContentDescriptionPhoneSignal[i],
                            mContentDescriptionDataType[i],Color.TRANSPARENT, i);
                }
            }
        }
        cluster.setIsAirplaneMode(mAirplaneMode);
    }
    public void setStackedMode(boolean stacked) {
        mDataAndWifiStacked = true;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        Log.d(TAG, "action: "+intent.getAction());
        mDDS = TelephonyManager.getDefault().getDefaultDataPhoneId(context);
        if (action.equals(WifiManager.RSSI_CHANGED_ACTION)
                || action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)
                || action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
            updateWifiState(intent);
            refreshViews(mDDS);
        } else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
            int phoneId = intent.getIntExtra(IccCard.INTENT_KEY_PHONE_ID, 0);
            updateSimState(intent);
            updateDataIcon(phoneId);
            refreshViews(phoneId);
        } else if (action.equals(Telephony.Intents.SPN_STRINGS_UPDATED_ACTION)) {
            final int phoneId = intent.getIntExtra(Intents.EXTRA_PHONE_ID, 0);
            updateNetworkName(intent.getBooleanExtra(Telephony.Intents.EXTRA_SHOW_SPN, false),
                        intent.getStringExtra(Telephony.Intents.EXTRA_SPN),
                        intent.getBooleanExtra(Telephony.Intents.EXTRA_SHOW_PLMN, false),
                        intent.getStringExtra(Telephony.Intents.EXTRA_PLMN), phoneId);
            refreshViews(phoneId);
        } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION) ||
                 action.equals(ConnectivityManager.INET_CONDITION_ACTION)) {
            updateConnectivity(intent);
            refreshViews(mDDS);
        } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
            refreshViews(mDDS);
        } else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
            updateAirplaneMode();
            for (int i=0; i < numPhones; i++) {
                updateTelephonySignalStrength(i);
                refreshViews(i);
            }
        } else if (action.equals(SimManager.INSERT_SIMS_CHANGED_ACTION)){            
            ArrayList<Sim> changedSims= intent.getParcelableArrayListExtra("sim");
            for (Sim sim : changedSims) {
                mSimColor[sim.getPhoneId()] = sim.getColor();
                Log.d(TAG, "mSimColor["+sim.getPhoneId()+"]: "+sim.getColor());
                refreshViews(sim.getPhoneId());
                }
           
        } else if (action.equals(WimaxManagerConstants.NET_4G_STATE_CHANGED_ACTION) ||
                action.equals(WimaxManagerConstants.SIGNAL_LEVEL_CHANGED_ACTION) ||
                action.equals(WimaxManagerConstants.WIMAX_NETWORK_STATE_CHANGED_ACTION)) {
            updateWimaxState(intent);
            refreshViews(mDDS);
        //add by spreadst_lc for cmcc wifi feature start
        } else if (action.equals("interruptforfaraway")) {
                String mLastSsid = intent.getStringExtra("wifi_last_ssid");
                if (Settings.Secure.getInt(mContext.getContentResolver(),Settings.Secure.WIFI_AUTO_CONNECT,0) == 0) {
                    showDialog(mLastSsid);
                } else {
                    autoConnectOtherTrustAp(mLastSsid);
                }

                if (beforeShowDisconnDialog(mLastSsid) ) {
                    showWifiDisconnDialog();
                }
        }
        //add by spreadst_lc for cmcc wifi feature end
    }


    // ===== Telephony ==============================================================

    private PhoneStateListener getPhoneStateListener(final int phoneId) {
    PhoneStateListener phoneStateListener = new PhoneStateListener() {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            if (DEBUG) {
                Slog.d(TAG, "onSignalStrengthsChanged on phoneId" + phoneId + "signalStrength=" + signalStrength +
                    ((signalStrength == null) ? "" : (" level=" + signalStrength.getLevel())));
            }
            mSignalStrength[phoneId] = signalStrength;
            updateTelephonySignalStrength(phoneId);
            refreshViews(phoneId);
        }

        @Override
        public void onServiceStateChanged(ServiceState state) {
            if (DEBUG) {
                Slog.d(TAG, "onServiceStateChanged on phoneId" + phoneId + "state=" + state.getState());
            }
            mServiceState[phoneId] = state;
            updateTelephonySignalStrength(phoneId);
            updateDataNetType(phoneId);
            updateDataIcon(phoneId);
            refreshViews(phoneId);
        }

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            if (DEBUG) {
                Slog.d(TAG, "onCallStateChanged state=" + state);
            }
            // In cdma, if a voice call is made, RSSI should switch to 1x.
            if (isCdma(phoneId)) {
                updateTelephonySignalStrength(phoneId);
                refreshViews(phoneId);
            }
        }

        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
            if (DEBUG) {
                Slog.d(TAG, "onDataConnectionStateChanged: state=" + state
                        + " type=" + networkType);
            }
            mDataState[phoneId] = state;
            mDataNetType[phoneId] = networkType;
            updateDataNetType(phoneId);
            updateDataIcon(phoneId);
            refreshViews(phoneId);
        }

        @Override
        public void onDataActivity(int direction) {
            if (DEBUG) {
                Slog.d(TAG, "onDataActivity: direction=" + direction);
            }
            mDataActivity[phoneId] = direction;
            updateDataIcon(phoneId);
            refreshViews(phoneId);
        }
    };
    return phoneStateListener;
    }

    private final void updateSimState(Intent intent) {
        String stateExtra = intent.getStringExtra(IccCard.INTENT_KEY_ICC_STATE);
        int phoneId = intent.getIntExtra(IccCard.INTENT_KEY_PHONE_ID, 0);
        if (IccCard.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
            mSimState[phoneId] = IccCard.State.ABSENT;
        }
        else if (IccCard.INTENT_VALUE_ICC_READY.equals(stateExtra)) {
            mSimState[phoneId] = IccCard.State.READY;
        }
        else if (IccCard.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
            final String lockedReason = intent.getStringExtra(IccCard.INTENT_KEY_LOCKED_REASON);
            if (IccCard.INTENT_VALUE_LOCKED_ON_PIN.equals(lockedReason)) {
                mSimState[phoneId] = IccCard.State.PIN_REQUIRED;
            }
            else if (IccCard.INTENT_VALUE_LOCKED_ON_PUK.equals(lockedReason)) {
                mSimState[phoneId] = IccCard.State.PUK_REQUIRED;
            }
            else {
                mSimState[phoneId] = IccCard.State.NETWORK_LOCKED;
            }
        } else {
            mSimState[phoneId] = IccCard.State.UNKNOWN;
        }
    }

    private boolean isCdma(int phoneId) {
        return (mSignalStrength[phoneId] != null) && !mSignalStrength[phoneId].isGsm();
    }

    private boolean hasService(int phoneId) {
        if (mServiceState[phoneId] != null) {
            switch (mServiceState[phoneId].getState()) {
                case ServiceState.STATE_OUT_OF_SERVICE:
                case ServiceState.STATE_POWER_OFF:
                    return false;
                default:
                    return true;
            }
        } else {
            return false;
        }
    }

    private void updateAirplaneMode() {
        mAirplaneMode = (Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.AIRPLANE_MODE_ON, 0) == 1);
    }

    private final void updateTelephonySignalStrength(int phoneId) {
        if (mAirplaneMode) {
            mAirplaneIconId = R.drawable.stat_sys_signal_flightmode;
            if (numPhones>1) {
                mPhoneSignalIconId[numPhones-1] = mDataSignalIconId[numPhones-1] = R.drawable.stat_sys_signal_flightmode;
                for (int i=0; i < numPhones-1; i++) {
                    mPhoneSignalIconId[i] = mDataSignalIconId[i] = 0;
                }
            } else {
                mPhoneSignalIconId[phoneId] = mDataSignalIconId[phoneId] = R.drawable.stat_sys_signal_flightmode;
            }
            return;
        }
        if (!mPhone[phoneId].hasIccCard()) {

            mPhoneSignalIconId[phoneId] = R.drawable.stat_sys_no_sim_sprd;
            mDataSignalIconId[phoneId] = R.drawable.stat_sys_no_sim_sprd;

            return;
        }
        if (!hasService(phoneId)) {
            if (CHATTY) Slog.d(TAG, "updateTelephonySignalStrength: !hasService()");
            mPhoneSignalIconId[phoneId] = R.drawable.stat_sys_signal_0_sprd;
            mDataSignalIconId[phoneId] = R.drawable.stat_sys_signal_0_sprd;
        } else {
            if (mSignalStrength[phoneId] == null) {
                if (CHATTY) Slog.d(TAG, "updateTelephonySignalStrength: mSignalStrength == null");
                mPhoneSignalIconId[phoneId] = R.drawable.stat_sys_signal_0_sprd;
                mDataSignalIconId[phoneId] = R.drawable.stat_sys_signal_0_sprd;
                mContentDescriptionPhoneSignal[phoneId] = mContext.getString(
                        AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0]);
            } else {
                int iconLevel;
                int[] iconList;
                mLastSignalLevel[phoneId] = iconLevel = mSignalStrength[phoneId].getLevel();
                if (isCdma(phoneId)) {
                    if (isCdmaEri(phoneId)) {
                        iconList = TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH_ROAMING_UUI[mInetCondition];
                    } else {
                        iconList = TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH_UUI[mInetCondition];
                    }
                } else {
                    // Though mPhone is a Manager, this call is not an IPC
                    if (mPhone[phoneId].isNetworkRoaming()) {
                        iconList = TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH_ROAMING_UUI[mInetCondition];
                    } else {
                        iconList = TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH_UUI[mInetCondition];
                    }
                }
                mPhoneSignalIconId[phoneId] = iconList[iconLevel];
                mContentDescriptionPhoneSignal[phoneId] = mContext.getString(
                        AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[iconLevel]);

                mDataSignalIconId[phoneId] = TelephonyIcons.DATA_SIGNAL_STRENGTH_UUI[mInetCondition][iconLevel];
            }
        }
    }

    private final void updateDataNetType(int phoneId) {
        int mDataCondition = 0;
        if (mDataState[phoneId] == TelephonyManager.DATA_CONNECTED) {
            mDataCondition=1;
        }
        if (mIsWimaxEnabled && mWimaxConnected) {
            // wimax is a special 4g network not handled by telephony
            mDataIconList[phoneId] = TelephonyIcons.DATA_4G[mDataCondition];
            mDataTypeIconId[phoneId] = R.drawable.stat_sys_data_connected_4g_sprd;
            mContentDescriptionDataType[phoneId] = mContext.getString(
                    R.string.accessibility_data_connection_4g);
        } else {
            switch (mDataNetType[phoneId]) {
                case TelephonyManager.NETWORK_TYPE_UNKNOWN:
                    if (!mShowAtLeastThreeGees) {
                        mDataIconList[phoneId] = TelephonyIcons.DATA_G[mDataCondition];
                        mDataTypeIconId[phoneId] = 0;
                        mContentDescriptionDataType[phoneId] = mContext.getString(
                                R.string.accessibility_data_connection_gprs);
                        break;
                    } else {
                        // fall through
                    }
                case TelephonyManager.NETWORK_TYPE_EDGE:
                    if (!mShowAtLeastThreeGees) {
                        mDataIconList[phoneId] = TelephonyIcons.DATA_E[mDataCondition];
                        mDataTypeIconId[phoneId] = R.drawable.stat_sys_data_connected_e_sprd;
                        mContentDescriptionDataType[phoneId] = mContext.getString(
                                R.string.accessibility_data_connection_edge);
                        break;
                    } else {
                        // fall through
                    }
                case TelephonyManager.NETWORK_TYPE_UMTS:
                    mDataIconList[phoneId] = TelephonyIcons.DATA_3G[mDataCondition];
                    mDataTypeIconId[phoneId] = R.drawable.stat_sys_data_connected_3g_sprd;
                    mContentDescriptionDataType[phoneId] = mContext.getString(
                            R.string.accessibility_data_connection_3g);
                    break;
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                    if (mHspaDataDistinguishable) {
                        mDataIconList[phoneId] = TelephonyIcons.DATA_3G[mDataCondition];
                        mDataTypeIconId[phoneId] = R.drawable.stat_sys_data_connected_3g_sprd;
                        mContentDescriptionDataType[phoneId] = mContext.getString(
                                R.string.accessibility_data_connection_3_5g);
                    } else {
                        mDataIconList[phoneId] = TelephonyIcons.DATA_T[mDataCondition];
                        mDataTypeIconId[phoneId] = R.drawable.stat_sys_data_connected_t;
                        mContentDescriptionDataType[phoneId] = mContext.getString(
                                R.string.accessibility_data_connection_3g);
                    }
                    break;
                case TelephonyManager.NETWORK_TYPE_CDMA:
                    // display 1xRTT for IS95A/B
                    mDataIconList[phoneId] = TelephonyIcons.DATA_1X[mDataCondition];
                    mDataTypeIconId[phoneId] = R.drawable.stat_sys_data_connected_1x;
                    mContentDescriptionDataType[phoneId] = mContext.getString(
                            R.string.accessibility_data_connection_cdma);
                    break;
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                    mDataIconList[phoneId] = TelephonyIcons.DATA_1X[mDataCondition];
                    mDataTypeIconId[phoneId] = R.drawable.stat_sys_data_connected_1x;
                    mContentDescriptionDataType[phoneId] = mContext.getString(
                            R.string.accessibility_data_connection_cdma);
                    break;
                case TelephonyManager.NETWORK_TYPE_EVDO_0: //fall through
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:
                case TelephonyManager.NETWORK_TYPE_EHRPD:
                    mDataIconList[phoneId] = TelephonyIcons.DATA_3G[mDataCondition];
                    mDataTypeIconId[phoneId] = R.drawable.stat_sys_data_connected_3g_sprd;
                    mContentDescriptionDataType[phoneId] = mContext.getString(
                            R.string.accessibility_data_connection_3g);
                    break;
                case TelephonyManager.NETWORK_TYPE_LTE:
                    mDataIconList[phoneId] = TelephonyIcons.DATA_4G[mDataCondition];
                    mDataTypeIconId[phoneId] = R.drawable.stat_sys_data_connected_4g_sprd;
                    mContentDescriptionDataType[phoneId] = mContext.getString(
                            R.string.accessibility_data_connection_4g);
                    break;
                default:
                    if (!mShowAtLeastThreeGees) {
                        mDataIconList[phoneId] = TelephonyIcons.DATA_G[mDataCondition];
                        mDataTypeIconId[phoneId] = R.drawable.stat_sys_data_connected_g_sprd;
                        mContentDescriptionDataType[phoneId] = mContext.getString(
                                R.string.accessibility_data_connection_gprs);
                    } else {
                        mDataIconList[phoneId] = TelephonyIcons.DATA_T[mDataCondition];
                        mDataTypeIconId[phoneId] = R.drawable.stat_sys_data_connected_t;
                        mContentDescriptionDataType[phoneId] = mContext.getString(
                                R.string.accessibility_data_connection_3g);
                    }
                    break;
            }
        }

        if ((isCdma(phoneId) && isCdmaEri(phoneId)) || mPhone[phoneId].isNetworkRoaming()) {
            mDataIconList[phoneId] = TelephonyIcons.DATA_R[mDataCondition];
            mDataTypeIconId[phoneId] = R.drawable.stat_sys_data_connected_roam_sprd;
        }
    }

    boolean isCdmaEri(int phoneId) {
        if (mServiceState[phoneId] != null) {
            final int iconIndex = mServiceState[phoneId].getCdmaEriIconIndex();
            if (iconIndex != EriInfo.ROAMING_INDICATOR_OFF) {
                final int iconMode = mServiceState[phoneId].getCdmaEriIconMode();
                if (iconMode == EriInfo.ROAMING_ICON_MODE_NORMAL
                        || iconMode == EriInfo.ROAMING_ICON_MODE_FLASH) {
                    return true;
                }
            }
        }
        return false;
    }

    private final void updateDataIcon(int phoneId) {
        int iconId;
        boolean visible = true;

        if (!isCdma(phoneId)) {
            // GSM case, we have to check also the sim state
            if (mSimState[phoneId] == IccCard.State.READY || mSimState[phoneId] == IccCard.State.UNKNOWN) {
                if (hasService(phoneId) && mDataState[phoneId] == TelephonyManager.DATA_CONNECTED) {
                    switch (mDataActivity[phoneId]) {
                        case TelephonyManager.DATA_ACTIVITY_IN:
                            iconId = mDataIconList[phoneId][1];
                            break;
                        case TelephonyManager.DATA_ACTIVITY_OUT:
                            iconId = mDataIconList[phoneId][2];
                            break;
                        case TelephonyManager.DATA_ACTIVITY_INOUT:
                            iconId = mDataIconList[phoneId][3];
                            break;
                        default:
                            iconId = mDataIconList[phoneId][0];
                            break;
                    }
                    mDataDirectionIconId[phoneId] = iconId;
                } else {
                    iconId = 0;
                    visible = false;
                }
            } else {
                iconId = R.drawable.stat_sys_no_sim;
                visible = false; // no SIM? no data
            }
        } else {
            // CDMA case, mDataActivity can be also DATA_ACTIVITY_DORMANT
            if (hasService(phoneId) && mDataState[phoneId] == TelephonyManager.DATA_CONNECTED) {
                switch (mDataActivity[phoneId]) {
                    case TelephonyManager.DATA_ACTIVITY_IN:
                        iconId = mDataIconList[phoneId][1];
                        break;
                    case TelephonyManager.DATA_ACTIVITY_OUT:
                        iconId = mDataIconList[phoneId][2];
                        break;
                    case TelephonyManager.DATA_ACTIVITY_INOUT:
                        iconId = mDataIconList[phoneId][3];
                        break;
                    case TelephonyManager.DATA_ACTIVITY_DORMANT:
                    default:
                        iconId = mDataIconList[phoneId][0];
                        break;
                }
            } else {
                iconId = 0;
                visible = false;
            }
        }

        // yuck - this should NOT be done by the status bar
        long ident = Binder.clearCallingIdentity();
        try {
            mBatteryStats.notePhoneDataConnectionState(mPhone[phoneId].getNetworkType(), visible);
        } catch (RemoteException e) {
        } finally {
            Binder.restoreCallingIdentity(ident);
        }

        mDataDirectionIconId[phoneId] = iconId;
        mDataConnected[phoneId] = visible;
    }

    void updateNetworkName(boolean showSpn, String spn, boolean showPlmn, String plmn, int phoneId) {
        if (true) {
            Slog.d("CarrierLabel", "updateNetworkName showSpn=" + showSpn + " spn=" + spn
                    + " showPlmn=" + showPlmn + " plmn=" + plmn + " phoneId" + phoneId);
        }
        StringBuilder str = new StringBuilder();

        boolean something = false;
        boolean hasSim = PhoneFactory.isCardExist(phoneId);
        if (showPlmn && plmn != null) {
            str.append(plmn);
            something = true;
        }
        if (showSpn && spn != null) {
            if (something) {
                str.append(" | ");
            }
            str.append(spn);
            something = true;
        }

        if (something) {
            if (showPlmn
                    && plmn != null
                    && plmn.equals(mContext
                            .getString(com.android.internal.R.string.lockscreen_carrier_default))) {
                if (!hasSim) {
                    str.append(" | ");
                    str.append(mContext
                            .getString(com.android.internal.R.string.lockscreen_missing_sim_message_short));
                }
            }
            mNetworkName[phoneId] = str.toString();
        } else {
            str.append(mContext.getString(com.android.internal.R.string.lockscreen_carrier_default));
            if (!hasSim) {
                str.append(" | ");
                str.append(mContext
                        .getString(com.android.internal.R.string.lockscreen_missing_sim_message_short));
            }
            mNetworkName[phoneId] = str.toString();
        }
    }

    // ===== Wifi ===================================================================

    class WifiHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AsyncChannel.CMD_CHANNEL_HALF_CONNECTED:
                    if (msg.arg1 == AsyncChannel.STATUS_SUCCESSFUL) {
                        mWifiChannel.sendMessage(Message.obtain(this,
                                AsyncChannel.CMD_CHANNEL_FULL_CONNECTION));
                    } else {
                        Slog.e(TAG, "Failed to connect to wifi");
                    }
                    break;
                case WifiManager.DATA_ACTIVITY_NOTIFICATION:
                    if (msg.arg1 != mWifiActivity) {
                        mWifiActivity = msg.arg1;
                        refreshViews(mDDS);
                    }
                    break;
                default:
                    //Ignore
                    break;
            }
        }
    }

    private void updateWifiState(Intent intent) {
        final String action = intent.getAction();
        if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
            mWifiEnabled = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                    WifiManager.WIFI_STATE_UNKNOWN) == WifiManager.WIFI_STATE_ENABLED;

        } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
            final NetworkInfo networkInfo = (NetworkInfo)
                    intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            boolean wasConnected = mWifiConnected;
            mWifiConnected = networkInfo != null && networkInfo.isConnected();
            // If we just connected, grab the inintial signal strength and ssid
            if (mWifiConnected && !wasConnected) {
                // try getting it out of the intent first
                WifiInfo info = (WifiInfo) intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
                if (info == null) {
                    info = mWifiManager.getConnectionInfo();
                }
                if (info != null) {
                    mWifiSsid = huntForSsid(info);
                } else {
                    mWifiSsid = null;
                }
            } else if (!mWifiConnected) {
                mWifiSsid = null;
            }
        } else if (action.equals(WifiManager.RSSI_CHANGED_ACTION)) {
            mWifiRssi = intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI, -200);
            mWifiLevel = WifiManager.calculateSignalLevel(
                        mWifiRssi, WifiIcons.WIFI_LEVEL_COUNT - 1) + 1;
            //add by spreadst_lc for cmcc wifi feature start
            if (supportCMCC) {

                if (mWifiRssi <= -85 && mWifiConnected ) {
                    String mLastSsid = intent.getStringExtra("wifi_last_ssid");
                    if(Settings.Secure.getInt(mContext.getContentResolver(),Settings.Secure.WIFI_AUTO_CONNECT,0) == 0){
                        showDialog(mLastSsid);
                    } else {
                        setCurrentApLowest(mLastSsid);
                        autoConnectOtherTrustAp(mLastSsid);
                    }
                } else if (weakSignalDialog != null && mWifiRssi > -85) {
                    weakSignalDialog.dismiss();
                }
            }
            //add by spreadst_lc for cmcc wifi feature end
        }

        updateWifiIcons();
    }

    private void updateWifiIcons() {
        if (mWifiConnected) {
            mWifiIconId = WifiIcons.WIFI_SIGNAL_STRENGTH_UUI[mInetCondition][mWifiLevel];
            mContentDescriptionWifi = mContext.getString(
                    AccessibilityContentDescriptions.WIFI_CONNECTION_STRENGTH[mWifiLevel]);
        } else {
            if (mDataAndWifiStacked) {
                mWifiIconId = 0;
            } else {
                mWifiIconId = mWifiEnabled ? R.drawable.stat_sys_wifi_signal_null : 0;
            }
            mContentDescriptionWifi = mContext.getString(R.string.accessibility_no_wifi);
        }
    }

    private String huntForSsid(WifiInfo info) {
        String ssid = info.getSSID();
        if (ssid != null) {
            return ssid;
        }
        // OK, it's not in the connectionInfo; we have to go hunting for it
        List<WifiConfiguration> networks = mWifiManager.getConfiguredNetworks();
        for (WifiConfiguration net : networks) {
            if (net.networkId == info.getNetworkId()) {
                return net.SSID;
            }
        }
        return null;
    }


    // ===== Wimax ===================================================================
    private final void updateWimaxState(Intent intent) {
        final String action = intent.getAction();
        boolean wasConnected = mWimaxConnected;
        if (action.equals(WimaxManagerConstants.NET_4G_STATE_CHANGED_ACTION)) {
            int wimaxStatus = intent.getIntExtra(WimaxManagerConstants.EXTRA_4G_STATE,
                    WimaxManagerConstants.NET_4G_STATE_UNKNOWN);
            mIsWimaxEnabled = (wimaxStatus ==
                    WimaxManagerConstants.NET_4G_STATE_ENABLED);
        } else if (action.equals(WimaxManagerConstants.SIGNAL_LEVEL_CHANGED_ACTION)) {
            mWimaxSignal = intent.getIntExtra(WimaxManagerConstants.EXTRA_NEW_SIGNAL_LEVEL, 0);
        } else if (action.equals(WimaxManagerConstants.WIMAX_NETWORK_STATE_CHANGED_ACTION)) {
            mWimaxState = intent.getIntExtra(WimaxManagerConstants.EXTRA_WIMAX_STATE,
                    WimaxManagerConstants.NET_4G_STATE_UNKNOWN);
            mWimaxExtraState = intent.getIntExtra(
                    WimaxManagerConstants.EXTRA_WIMAX_STATE_DETAIL,
                    WimaxManagerConstants.NET_4G_STATE_UNKNOWN);
            mWimaxConnected = (mWimaxState ==
                    WimaxManagerConstants.WIMAX_STATE_CONNECTED);
            mWimaxIdle = (mWimaxExtraState == WimaxManagerConstants.WIMAX_IDLE);
        }
        updateDataNetType(mDDS);
        updateWimaxIcons();
    }

    private void updateWimaxIcons() {
        if (mIsWimaxEnabled) {
            if (mWimaxConnected) {
                if (mWimaxIdle)
                    mWimaxIconId = WimaxIcons.WIMAX_IDLE;
                else
                    mWimaxIconId = WimaxIcons.WIMAX_SIGNAL_STRENGTH[mInetCondition][mWimaxSignal];
                mContentDescriptionWimax = mContext.getString(
                        AccessibilityContentDescriptions.WIMAX_CONNECTION_STRENGTH[mWimaxSignal]);
            } else {
                mWimaxIconId = WimaxIcons.WIMAX_DISCONNECTED;
                mContentDescriptionWimax = mContext.getString(R.string.accessibility_no_wimax);
            }
        } else {
            mWimaxIconId = 0;
        }
    }

    // ===== Full or limited Internet connectivity ==================================

    private void updateConnectivity(Intent intent) {
        if (CHATTY) {
            Slog.d(TAG, "updateConnectivity: intent=" + intent);
        }

        final ConnectivityManager connManager = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo info = connManager.getActiveNetworkInfo();

        // Are we connected at all, by any interface?
        mConnected = info != null && info.isConnected();
        if (mConnected) {
            mConnectedNetworkType = info.getType();
            mConnectedNetworkTypeName = info.getTypeName();
        } else {
            mConnectedNetworkType = ConnectivityManager.TYPE_NONE;
            mConnectedNetworkTypeName = null;
        }

        int connectionStatus = intent.getIntExtra(ConnectivityManager.EXTRA_INET_CONDITION, 0);

        if (CHATTY) {
            Slog.d(TAG, "updateConnectivity: networkInfo=" + info);
            Slog.d(TAG, "updateConnectivity: connectionStatus=" + connectionStatus);
        }

        mInetCondition = (connectionStatus > INET_CONDITION_THRESHOLD ? 1 : 0);

        if (info != null && info.getType() == ConnectivityManager.TYPE_BLUETOOTH) {
            mBluetoothTethered = info.isConnected();
        } else {
            mBluetoothTethered = false;
        }

        // We want to update all the icons, all at once, for any condition change
        updateDataNetType(mDDS);
        updateWimaxIcons();
        updateDataIcon(mDDS);
        updateTelephonySignalStrength(mDDS);
        updateWifiIcons();
    }


    // ===== Update the views =======================================================

    void refreshViews(int phoneId) {
        Context context = mContext;
        Log.d(TAG, "mSimColor[PhoneId]: "+mSimColor[phoneId]);
        int combinedSignalIconId = 0;
        int combinedActivityIconId = 0;
        String combinedLabel = "";
        String wifiLabel = "";
        String mobileLabel = "";
        int N;
        final boolean emergencyOnly = (mServiceState[phoneId] != null && mServiceState[phoneId].isEmergencyOnly());

        if (!mHasMobileDataFeature) {
            mDataSignalIconId[phoneId] = mPhoneSignalIconId[phoneId] = 0;
            mobileLabel = "";
        } else {
            // We want to show the carrier name if in service and either:
            //   - We are connected to mobile data, or
            //   - We are not connected to mobile data, as long as the *reason* packets are not
            //     being routed over that link is that we have better connectivity via wifi.
            // If data is disconnected for some other reason but wifi (or ethernet/bluetooth)
            // is connected, we show nothing.
            // Otherwise (nothing connected) we show "No internet connection".

            if (mDataConnected[phoneId]) {
                mobileLabel = mNetworkName[phoneId];
            } else if (mConnected || emergencyOnly) {
                if (hasService(phoneId) || emergencyOnly) {
                    // The isEmergencyOnly test covers the case of a phone with no SIM
                    mobileLabel = mNetworkName[phoneId];
                } else {
                    // Tablets, basically
                    mobileLabel = "";
                }
            } else {
                mobileLabel
                    = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
            }

            // Now for things that should only be shown when actually using mobile data.
            if (mDataConnected[phoneId]) {
                combinedSignalIconId = mDataSignalIconId[phoneId];
                switch (mDataActivity[phoneId]) {
                    case TelephonyManager.DATA_ACTIVITY_IN:
                        mMobileActivityIconId[phoneId] = R.drawable.stat_sys_signal_in_sprd;
                        break;
                    case TelephonyManager.DATA_ACTIVITY_OUT:
                        mMobileActivityIconId[phoneId] = R.drawable.stat_sys_signal_out_sprd;
                        break;
                    case TelephonyManager.DATA_ACTIVITY_INOUT:
                        mMobileActivityIconId[phoneId] = R.drawable.stat_sys_signal_inout_sprd;
                        break;
                    default:
                        mMobileActivityIconId[phoneId] = R.drawable.stat_sys_signal_default_sprd;
                        break;
                }

                combinedLabel = mobileLabel;
                combinedActivityIconId = mMobileActivityIconId[phoneId];
                combinedSignalIconId = mDataSignalIconId[phoneId]; // set by updateDataIcon()
                mContentDescriptionCombinedSignal = mContentDescriptionDataType;
            } else {
                mMobileActivityIconId[phoneId] = 0;
            }
        }

        if (mWifiConnected) {
            if (mWifiSsid == null) {
                wifiLabel = context.getString(R.string.status_bar_settings_signal_meter_wifi_nossid);
                mWifiActivityIconId = 0; // no wifis, no bits
            } else {
                wifiLabel = mWifiSsid;
                if (DEBUG) {
                    wifiLabel += "xxxxXXXXxxxxXXXX";
                }
                switch (mWifiActivity) {
                    case WifiManager.DATA_ACTIVITY_IN:
                        mWifiActivityIconId = R.drawable.stat_sys_wifi_in;
                        break;
                    case WifiManager.DATA_ACTIVITY_OUT:
                        mWifiActivityIconId = R.drawable.stat_sys_wifi_out;
                        break;
                    case WifiManager.DATA_ACTIVITY_INOUT:
                        mWifiActivityIconId = R.drawable.stat_sys_wifi_inout;
                        break;
                    case WifiManager.DATA_ACTIVITY_NONE:
                        mWifiActivityIconId = 0;
                        break;
                }
            }

            combinedActivityIconId = mWifiActivityIconId;
            combinedLabel = wifiLabel;
            combinedSignalIconId = mWifiIconId; // set by updateWifiIcons()
            mContentDescriptionCombinedSignal[phoneId] = mContentDescriptionWifi;
        }

        if (mDataConnected[phoneId]) {
            mobileLabel = mNetworkName[phoneId];
            combinedSignalIconId = mDataSignalIconId[phoneId];
            switch (mDataActivity[phoneId]) {
                case TelephonyManager.DATA_ACTIVITY_IN:
                    mMobileActivityIconId[phoneId] = R.drawable.stat_sys_signal_in_sprd;
                    break;
                case TelephonyManager.DATA_ACTIVITY_OUT:
                    mMobileActivityIconId[phoneId] = R.drawable.stat_sys_signal_out_sprd;
                    break;
                case TelephonyManager.DATA_ACTIVITY_INOUT:
                    mMobileActivityIconId[phoneId] = R.drawable.stat_sys_signal_inout_sprd;
                    break;
                default:
                    mMobileActivityIconId[phoneId] = R.drawable.stat_sys_signal_default_sprd;
                    break;
            }
            combinedActivityIconId = mMobileActivityIconId[phoneId];
            combinedSignalIconId = mDataSignalIconId[phoneId]; // set by updateDataIcon()
            mContentDescriptionCombinedSignal[phoneId] = mContentDescriptionDataType[phoneId];
        }

        if (mBluetoothTethered) {
            combinedLabel = mContext.getString(R.string.bluetooth_tethered);
            combinedSignalIconId = mBluetoothTetherIconId;
            mContentDescriptionCombinedSignal[phoneId] = mContext.getString(
                    R.string.accessibility_bluetooth_tether);
        }

        final boolean ethernetConnected = (mConnectedNetworkType == ConnectivityManager.TYPE_ETHERNET);
        if (ethernetConnected) {
            // TODO: icons and strings for Ethernet connectivity
            combinedLabel = mConnectedNetworkTypeName;
        }

        if (mAirplaneMode &&
                (mServiceState[phoneId] == null || (!hasService(phoneId) && !mServiceState[phoneId].isEmergencyOnly()))) {
            // Only display the flight-mode icon if not in "emergency calls only" mode.

            // look again; your radios are now airplanes
            mContentDescriptionPhoneSignal[phoneId] = mContext.getString(
                    R.string.accessibility_airplane_mode);
            mAirplaneIconId = R.drawable.stat_sys_signal_flightmode;
            //mPhoneSignalIconId[phoneId] = mDataSignalIconId[phoneId] = R.drawable.stat_sys_signal_flightmode;
            mDataTypeIconId[phoneId] = 0;

            // combined values from connected wifi take precedence over airplane mode
            if (mWifiConnected) {
                // Suppress "No internet connection." from mobile if wifi connected.
                mobileLabel = "";
            } else {
                if (mHasMobileDataFeature) {
                    // let the mobile icon show "No internet connection."
                    wifiLabel = "";
                } else {
                    wifiLabel = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
                    combinedLabel = wifiLabel;
                }
                mContentDescriptionCombinedSignal[phoneId] = mContentDescriptionPhoneSignal[phoneId];
                combinedSignalIconId = mDataSignalIconId[phoneId];
            }
        }
        else if (!mDataConnected[phoneId] && !mWifiConnected && !mBluetoothTethered && !mWimaxConnected && !ethernetConnected) {
            // pretty much totally disconnected

            combinedLabel = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
            // On devices without mobile radios, we want to show the wifi icon
            combinedSignalIconId =
                mHasMobileDataFeature ? mDataSignalIconId[phoneId] : mWifiIconId;
            mContentDescriptionCombinedSignal[phoneId] = mHasMobileDataFeature
                ? mContentDescriptionDataType[phoneId] : mContentDescriptionWifi;

            if ((isCdma(phoneId) && isCdmaEri(phoneId)) || mPhone[phoneId].isNetworkRoaming()) {
                mDataTypeIconId[phoneId] = R.drawable.stat_sys_data_connected_roam;
            } else {
                mDataTypeIconId[phoneId] = 0;
            }
        }

        if (DEBUG) {
            Slog.d(TAG, "refreshViews connected={"
                    + (mWifiConnected?" wifi":"")
                    + (mDataConnected[phoneId]?" data":"")
                    + " } level="
                    + ((mSignalStrength[phoneId] == null)?"??":Integer.toString(mSignalStrength[phoneId].getLevel()))
                    + " combinedSignalIconId=0x"
                    + Integer.toHexString(combinedSignalIconId)
                    + "/" + getResourceName(combinedSignalIconId)
                    + " combinedActivityIconId=0x" + Integer.toHexString(combinedActivityIconId)
                    + " mobileLabel=" + mobileLabel
                    + " wifiLabel=" + wifiLabel
                    + " emergencyOnly=" + emergencyOnly
                    + " combinedLabel=" + combinedLabel
                    + " mAirplaneMode=" + mAirplaneMode
                    + " mDataActivity=" + mDataActivity
                    + " mPhoneSignalIconId=0x" + Integer.toHexString(mPhoneSignalIconId[phoneId])
                    + " mDataDirectionIconId=0x" + Integer.toHexString(mDataDirectionIconId[phoneId])
                    + " mDataSignalIconId=0x" + Integer.toHexString(mDataSignalIconId[phoneId])
                    + " mDataTypeIconId=0x" + Integer.toHexString(mDataTypeIconId[phoneId])
                    + " mWifiIconId=0x" + Integer.toHexString(mWifiIconId)
                    + " mBluetoothTetherIconId=0x" + Integer.toHexString(mBluetoothTetherIconId));
        }

        if (mLastPhoneSignalIconId[phoneId]          != mPhoneSignalIconId[phoneId]
         || mLastDataDirectionOverlayIconId[phoneId] != combinedActivityIconId
         || mLastWifiIconId                 != mWifiIconId
         || mLastWimaxIconId                != mWimaxIconId
         || mLastDataTypeIconId[phoneId]             != mDataTypeIconId[phoneId]
         || mLastSimColor[phoneId]          != mSimColor[phoneId]
         || mLastWifiActivityIconId         != mWifiActivityIconId)
        {
            // NB: the mLast*s will be updated later
            for (SignalCluster cluster : mSignalClusters) {
                refreshSignalCluster(cluster);
            }
        }

        if (mLastAirplaneMode != mAirplaneMode) {
            mLastAirplaneMode = mAirplaneMode;
        }

        // the phone icon on phones
        if (mLastPhoneSignalIconId[phoneId] != mPhoneSignalIconId[phoneId]) {
            mLastPhoneSignalIconId[phoneId] = mPhoneSignalIconId[phoneId];
            N = mPhoneSignalIconViews.size();
            for (int i=0; i<N; i++) {
                final ImageView v = mPhoneSignalIconViews.get(i);
                v.setImageResource(mPhoneSignalIconId[phoneId]);
                v.setContentDescription(mContentDescriptionPhoneSignal[phoneId]);
            }
        }

        // the data icon on phones
        if (mLastDataDirectionIconId[phoneId] != mDataDirectionIconId[phoneId]) {
            mLastDataDirectionIconId[phoneId] = mDataDirectionIconId[phoneId];
            N = mDataDirectionIconViews.size();
            for (int i=0; i<N; i++) {
                final ImageView v = mDataDirectionIconViews.get(i);
                v.setImageResource(mDataDirectionIconId[phoneId]);
                v.setContentDescription(mContentDescriptionDataType[phoneId]);
            }
        }
        if (mLastSimColor[phoneId] != mSimColor[phoneId]) {
            mLastSimColor[phoneId] = mSimColor[phoneId];
        }

        // the wifi icon on phones
        if (mLastWifiIconId != mWifiIconId) {
            mLastWifiIconId = mWifiIconId;
            N = mWifiIconViews.size();
            for (int i=0; i<N; i++) {
                final ImageView v = mWifiIconViews.get(i);
                if (mWifiIconId == 0) {
                    v.setVisibility(View.INVISIBLE);
                } else {
                    v.setVisibility(View.VISIBLE);
                    v.setImageResource(mWifiIconId);
                    v.setContentDescription(mContentDescriptionWifi);
                }
            }
        }

        if(mLastWifiActivityIconId != mWifiActivityIconId){
            mLastWifiActivityIconId = mWifiActivityIconId;
        }

        // the wimax icon on phones
        if (mLastWimaxIconId != mWimaxIconId) {
            mLastWimaxIconId = mWimaxIconId;
            N = mWimaxIconViews.size();
            for (int i=0; i<N; i++) {
                final ImageView v = mWimaxIconViews.get(i);
                if (mWimaxIconId == 0) {
                    v.setVisibility(View.INVISIBLE);
                } else {
                    v.setVisibility(View.VISIBLE);
                    v.setImageResource(mWimaxIconId);
                    v.setContentDescription(mContentDescriptionWimax);
                }
           }
        }
        // the combined data signal icon
        if (mLastCombinedSignalIconId[phoneId] != combinedSignalIconId) {
            mLastCombinedSignalIconId[phoneId] = combinedSignalIconId;
            N = mCombinedSignalIconViews.size();
            for (int i=0; i<N; i++) {
                final ImageView v = mCombinedSignalIconViews.get(i);
                v.setImageResource(combinedSignalIconId);
                v.setContentDescription(mContentDescriptionCombinedSignal[phoneId]);
            }
        }

        // the data network type overlay
        if (mLastDataTypeIconId[phoneId] != mDataTypeIconId[phoneId]) {
            mLastDataTypeIconId[phoneId] = mDataTypeIconId[phoneId];
            N = mDataTypeIconViews.size();
            for (int i=0; i<N; i++) {
                final ImageView v = mDataTypeIconViews.get(i);
                if (mDataTypeIconId[phoneId] == 0) {
                    v.setVisibility(View.INVISIBLE);
                } else {
                    v.setVisibility(View.VISIBLE);
                    v.setImageResource(mDataTypeIconId[phoneId]);
                    v.setContentDescription(mContentDescriptionDataType[phoneId]);
                }
            }
        }

        // the data direction overlay
        if (mLastDataDirectionOverlayIconId[phoneId] != combinedActivityIconId) {
            if (DEBUG) {
                Slog.d(TAG, "changing data overlay icon id to " + combinedActivityIconId);
            }
            mLastDataDirectionOverlayIconId[phoneId] = combinedActivityIconId;
            N = mDataDirectionOverlayIconViews.size();
            for (int i=0; i<N; i++) {
                final ImageView v = mDataDirectionOverlayIconViews.get(i);
                if (combinedActivityIconId == 0) {
                    v.setVisibility(View.INVISIBLE);
                } else {
                    v.setVisibility(View.VISIBLE);
                    v.setImageResource(combinedActivityIconId);
                    v.setContentDescription(mContentDescriptionDataType[phoneId]);
                }
            }
        }

        // the combinedLabel in the notification panel
        if (!mLastCombinedLabel.equals(combinedLabel)) {
            mLastCombinedLabel = combinedLabel;
            N = mCombinedLabelViews.size();
            for (int i=0; i<N; i++) {
                TextView v = mCombinedLabelViews.get(i);
                v.setText(combinedLabel);
            }
        }

        // wifi label
        N = mWifiLabelViews.size();
        for (int i=0; i<N; i++) {
            TextView v = mWifiLabelViews.get(i);
            v.setText(wifiLabel);
            if ("".equals(wifiLabel)) {
                v.setVisibility(View.GONE);
            } else {
                v.setVisibility(View.VISIBLE);
            }
        }

        // mobile label
        if (UNIVERSEUI_SUPPORT) {
            TextView v = mMobileLabelViews.get(phoneId);
            v.setText(mNetworkName[phoneId]);
            if ("".equals(mNetworkName[phoneId])) {
                v.setVisibility(View.GONE);
            } else {
                v.setVisibility(View.VISIBLE);
            }
        } else {
            if (phoneId == 0) {
                N = mMobileLabelViews.size();
                for (int i = 0; i < N; i++) {
                    TextView v = mMobileLabelViews.get(i);
                    v.setText(mNetworkName[phoneId]);
                    if ("".equals(mNetworkName[phoneId])) {
                        v.setVisibility(View.GONE);
                    } else {
                        v.setVisibility(View.VISIBLE);
                    }
                }
            } else if (phoneId == 1) {
                N = mMobileLabelViews1.size();
                for (int i = 0; i < N; i++) {
                    TextView v = mMobileLabelViews1.get(i);
                    v.setText(mNetworkName[phoneId]);
                    if ("".equals(mNetworkName[phoneId])) {
                        v.setVisibility(View.GONE);
                    } else {
                        v.setVisibility(View.VISIBLE);
                    }
                }
            }
        }

        // e-call label
        N = mEmergencyLabelViews.size();
        for (int i=0; i<N; i++) {
            TextView v = mEmergencyLabelViews.get(i);
            if (!emergencyOnly) {
                v.setVisibility(View.GONE);
            } else {
                v.setText(mobileLabel); // comes from the telephony stack
                v.setVisibility(View.VISIBLE);
            }
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        for (int i=0; i < numPhones; i++) {
        pw.println("NetworkController state:");
//        pw.println(String.format("  %s network type %d (%s)",
//                mConnected?"CONNECTED":"DISCONNECTED",
//                mConnectedNetworkType, mConnectedNetworkTypeName));
        pw.println("  - telephony ------");
        pw.print("  phoneId=");
        pw.println(i);
        pw.print("  hasService()=");
        pw.println(hasService(i));
        pw.print("  mHspaDataDistinguishable=");
        pw.println(mHspaDataDistinguishable);
        pw.print("  mDataConnected=");
        pw.println(mDataConnected[i]);
        pw.print("  mSimState=");
        pw.println(mSimState[i]);
        pw.print("  mPhoneState=");
        pw.println(mPhoneState[i]);
        pw.print("  mDataState=");
        pw.println(mDataState[i]);
        pw.print("  mDataActivity=");
        pw.println(mDataActivity[i]);
        pw.print("  mDataNetType=");
        pw.print(mDataNetType[i]);
        pw.print("/");
        pw.println(mPhone[i].getNetworkTypeName(mDataNetType[i]));
        pw.print("  mServiceState=");
        pw.println(mServiceState[i]);
        pw.print("  mSignalStrength=");
        pw.println(mSignalStrength[i]);
        pw.print("  mLastSignalLevel=");
        pw.println(mLastSignalLevel[i]);
        pw.print("  mNetworkName=");
        pw.println(mNetworkName[i]);
        pw.print("  mNetworkNameDefault=");
        pw.println(mNetworkNameDefault);
        pw.print("  mNetworkNameSeparator=");
        pw.println(mNetworkNameSeparator.replace("\n","\\n"));
        pw.print("  mPhoneSignalIconId=0x");
        pw.print(Integer.toHexString(mPhoneSignalIconId[i]));
        pw.print("/");
        pw.println(getResourceName(mPhoneSignalIconId[i]));
        pw.print("  mDataDirectionIconId=");
        pw.print(Integer.toHexString(mDataDirectionIconId[i]));
        pw.print("/");
        pw.println(getResourceName(mDataDirectionIconId[i]));
        pw.print("  mDataSignalIconId=");
        pw.print(Integer.toHexString(mDataSignalIconId[i]));
        pw.print("/");
        pw.println(getResourceName(mDataSignalIconId[i]));
        pw.print("  mDataTypeIconId=");
        pw.print(Integer.toHexString(mDataTypeIconId[i]));
        pw.print("/");
        pw.println(getResourceName(mDataTypeIconId[i]));

        pw.println("  - wifi ------");
        pw.print("  mWifiEnabled=");
        pw.println(mWifiEnabled);
        pw.print("  mWifiConnected=");
        pw.println(mWifiConnected);
        pw.print("  mWifiRssi=");
        pw.println(mWifiRssi);
        pw.print("  mWifiLevel=");
        pw.println(mWifiLevel);
        pw.print("  mWifiSsid=");
        pw.println(mWifiSsid);
        pw.println(String.format("  mWifiIconId=0x%08x/%s",
                    mWifiIconId, getResourceName(mWifiIconId)));
        pw.print("  mWifiActivity=");
        pw.println(mWifiActivity);

        if (mWimaxSupported) {
            pw.println("  - wimax ------");
            pw.print("  mIsWimaxEnabled="); pw.println(mIsWimaxEnabled);
            pw.print("  mWimaxConnected="); pw.println(mWimaxConnected);
            pw.print("  mWimaxIdle="); pw.println(mWimaxIdle);
            pw.println(String.format("  mWimaxIconId=0x%08x/%s",
                        mWimaxIconId, getResourceName(mWimaxIconId)));
            pw.println(String.format("  mWimaxSignal=%d", mWimaxSignal));
            pw.println(String.format("  mWimaxState=%d", mWimaxState));
            pw.println(String.format("  mWimaxExtraState=%d", mWimaxExtraState));
        }

        pw.println("  - Bluetooth ----");
        pw.print("  mBtReverseTethered=");
        pw.println(mBluetoothTethered);

        pw.println("  - connectivity ------");
        pw.print("  mInetCondition=");
        pw.println(mInetCondition);

        pw.println("  - icons ------");
        pw.print("  mLastPhoneSignalIconId=0x");
        pw.print(Integer.toHexString(mLastPhoneSignalIconId[i]));
        pw.print("/");
        pw.println(getResourceName(mLastPhoneSignalIconId[i]));
        pw.print("  mLastDataDirectionIconId=0x");
        pw.print(Integer.toHexString(mLastDataDirectionIconId[i]));
        pw.print("/");
        pw.println(getResourceName(mLastDataDirectionIconId[i]));
        pw.print("  mLastDataDirectionOverlayIconId=0x");
        pw.print(Integer.toHexString(mLastDataDirectionOverlayIconId[i]));
        pw.print("/");
        pw.println(getResourceName(mLastDataDirectionOverlayIconId[i]));
        pw.print("  mLastWifiIconId=0x");
        pw.print(Integer.toHexString(mLastWifiIconId));
        pw.print("/");
        pw.println(getResourceName(mLastWifiIconId));
        pw.print("  mLastCombinedSignalIconId=0x");
        pw.print(Integer.toHexString(mLastCombinedSignalIconId[i]));
        pw.print("/");
        pw.println(getResourceName(mLastCombinedSignalIconId[i]));
        pw.print("  mLastDataTypeIconId=0x");
        pw.print(Integer.toHexString(mLastDataTypeIconId[i]));
        pw.print("/");
        pw.println(getResourceName(mLastDataTypeIconId[i]));
        pw.print("  mLastCombinedLabel=");
        pw.print(mLastCombinedLabel);
        pw.println("");
        }
    }

    private String getResourceName(int resId) {
        if (resId != 0) {
            final Resources res = mContext.getResources();
            try {
                return res.getResourceName(resId);
            } catch (android.content.res.Resources.NotFoundException ex) {
                return "(unknown)";
            }
        } else {
            return "(null)";
        }
    }

    //add by spreadst_lc for cmcc wifi feature start
    private void showDialog(String mLastSsid) {
        /*if (alwaysAutoConnect) {
            WifiInfo mWifiInfo = mWifiManager.getConnectionInfo();
            int networkId = mWifiInfo.getNetworkId();
            String ssid = mWifiInfo.getSSID();
            setCurrentApLowest(ssid);
            updateNeworkPriority(ssid);
            mWifiManager.disableNetwork(networkId);
        } else {*/
            if(getListData() <= 0) return;
            final String []otherTrustSsids = filterNoRssiAps(mSSIDs,mLastSsid);
            if(otherTrustSsids == null) return;
            View warningView = View.inflate(mContext, R.layout.weak_signal_warning, null);

            /*final CheckBox autoConnect = (CheckBox)warningView.findViewById(R.id.auto_connect);
            autoConnect.setOnClickListener(null);*/

            final ListView mList = (ListView)warningView.findViewById(R.id.trusted_list);
            mList.setOnItemClickListener(new OnItemClickListener(){

                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    mIndex = position;
                }
            });

            ArrayAdapter mArrayAdapter = new ArrayAdapter(mContext,
                    android.R.layout.simple_list_item_single_choice,
                    android.R.id.text1, otherTrustSsids);
            mList.setAdapter(mArrayAdapter);
            mList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            mList.setItemChecked(mIndex, true);

            AlertDialog.Builder weakSignalDialogBuilder = new AlertDialog.Builder(mContext);
            weakSignalDialogBuilder.setCancelable(true);
            weakSignalDialogBuilder.setView(warningView);
            weakSignalDialogBuilder.setTitle(R.string.weak_signal_title);
            weakSignalDialogBuilder.setIcon(android.R.drawable.ic_dialog_alert);
            weakSignalDialogBuilder.setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        enableSelectedNework(otherTrustSsids[mIndex]);
                        /*if(autoConnect.isChecked())
                            alwaysAutoConnect = true;*/
                    }
            });
            weakSignalDialogBuilder.setNegativeButton(android.R.string.cancel, null);
            weakSignalDialog = weakSignalDialogBuilder.create();
            weakSignalDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            weakSignalDialog.show();
        //}
    }

    // add
    private void addPriority(String ssid, int priority) {
            if (null == ssid || ssid.length() == 0) {
                    return;
            }
            ContentValues values = new ContentValues();
            values.put("ssid", ssid);
            values.put("priority", new Integer(priority));
            mContext.getContentResolver().insert(TRUSTED_LIST_URI, values);
            mWifiManager.setTrustListPriority(ssid, priority);
    }

    // delete
    private void deletePriority(String ssid) {
            if (null == ssid || ssid.length() == 0) {
                    return;
            }
            mContext.getContentResolver().delete(TRUSTED_LIST_URI, "ssid=?",
                            new String[] { ssid });
    }

    // set current ap lowest priority
    private void setCurrentApLowest(String ssid) {
        int num = getListData();
        deletePriority(ssid);
        addPriority(ssid , num);
        updateTrustedList();
    }

    // set
    private int setPriority(String ssid, int priority) {
            if (null == ssid || ssid.length() == 0) {
                    return -1;
            }
            ContentValues values = new ContentValues();
            values.put("priority", new Integer(priority));
            return mContext.getContentResolver().update(TRUSTED_LIST_URI, values, "ssid=?",
                            new String[] { ssid });
    }

    // init
    private int getListData() {
        Cursor cursor = mContext.getContentResolver().query(TRUSTED_LIST_URI, null,
                        null, null, "priority ASC");
        int num = 0;
        try {
                num = cursor.getCount();
                int i = 0;
                int ssidColumnIndex = cursor.getColumnIndex("ssid");
                mSSIDs = new String[num];
                while (cursor.moveToNext()) {
                        mSSIDs[i] = cursor.getString(ssidColumnIndex);
                        i++;
                }
        } finally {
                if (cursor != null) {
                    cursor.close();
                }
        }
        return num;
    }

    private void updateTrustedList() {
        // init list data
        int mListSize = getListData();
        // add default trusted AP
        if (mListSize == 0) {
            mSSIDs = new String[2];
            mSSIDs[0] = "CMCC";
            mSSIDs[1] = "CMCC-EDU";
            addPriority(mSSIDs[0], 0);
            addPriority(mSSIDs[1], 1);
            mListSize = 2;
        }
        // after delete action,we need update priority for the list
        for (int i = 0; i < mListSize; i++) {
            setPriority(mSSIDs[i], i);
        }
        // pass the trusted list
        mWifiManager.setTrustListPriority("whole", -1);
        for (int i = 0; i < mListSize; i++) {
             mWifiManager.setTrustListPriority(mSSIDs[i], i);
        }
        mWifiManager.setTrustListPriority("whole", -2);
    }

    private void updateNeworkPriority(String ssid) {
        //add by TS_LC for case : none ssid will results NullpointerException.
        if (null == ssid || ssid.length() == 0) {
            return;
        }
        //end by TS_LC
        List<WifiConfiguration> configs = mWifiManager.getConfiguredNetworks();
        int length = getListData();
        if (configs != null) {
            for (WifiConfiguration config : configs) {
                if (!ssid.equals(config.SSID)) {
                    WifiConfiguration newConfig = new WifiConfiguration();
                    newConfig.networkId = config.networkId;
                    newConfig.priority = config.priority + length;
                    mWifiManager.updateNetwork(newConfig);
                }
                mWifiManager.saveConfiguration();
            }
        }
    }

    private void enableSelectedNework(String ssid) {
        List<WifiConfiguration> configs = mWifiManager.getConfiguredNetworks();
        if (configs != null) {
            for (WifiConfiguration config : configs) {
                String mSsid = (config.SSID == null ? "" : removeDoubleQuotes(config.SSID));
                if (mSsid.equals(ssid)) {
                    mWifiManager.enableNetwork(config.networkId, true);
                    mWifiManager.reconnectAP();
                    break;
                }
            }
        }
    }

    static String removeDoubleQuotes(String string) {
        int length = string.length();
        if ((length > 1) && (string.charAt(0) == '"')
                && (string.charAt(length - 1) == '"')) {
            return string.substring(1, length - 1);
        }
        return string;
    }

    private String[] filterNoRssiAps(String[] ssids, String delSsid) {
        String mDelSsid = (delSsid == null ? null : removeDoubleQuotes(delSsid));
        List<ScanResult> results = mWifiManager.getScanResults();
        if (results == null || results.size() == 0) return null;
        List<String> listSsids = new ArrayList<String> ();
        int length = ssids.length;
        for (ScanResult result : results) {
            for (int i = 0; i < length; i++) {
                if (ssids[i].equals(result.SSID)) {
                    if (!ssids[i].equals(mDelSsid)) {
                        listSsids.add(ssids[i]);
                        break;
                    }
                }
            }
        }
        if (listSsids.size() <= 0) return null;
        String[] filterSsids = new String[listSsids.size()];
        int num = 0;
        for (String listSsid : listSsids) {
            filterSsids[num++] = listSsid;
        }

        return filterSsids;
    }

    private void autoConnectOtherTrustAp(String ssid) {
        if(getListData() <= 0) return;
        String []otherTrustSsids = filterNoRssiAps(mSSIDs,ssid);
        if(otherTrustSsids == null) return;
        List<WifiConfiguration> configs = mWifiManager.getConfiguredNetworks();
        if (configs != null) {
            for (WifiConfiguration config : configs) {
                String mSsid = (config.SSID == null ? "" : removeDoubleQuotes(config.SSID));
                if (mSsid.equals(otherTrustSsids[0])) {
                    mWifiManager.enableNetwork(config.networkId, false);
                    break;
                }
            }
        }
    }

    private void showWifiDisconnDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(mContext);
        b.setCancelable(true);
        b.setTitle(R.string.network_disconnect_title);
        b.setMessage(R.string.network_disconnect_message);
        b.setPositiveButton(R.string.mobile_data_connect_enable,
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    mConnectivityManager.setMobileDataEnabled(true);
                }
        });
        b.setNegativeButton(R.string.mobile_data_connect_disable,
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    mContext.sendBroadcast(new Intent("android.download.spstoptask"));
                }
        });
        AlertDialog d = b.create();
        d.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        d.show();
    }

    private boolean beforeShowDisconnDialog(String mLastSsid) {
        boolean isDownload = false;
        getListData();
        final String []otherTrustSsids = filterNoRssiAps(mSSIDs,mLastSsid);
        if(otherTrustSsids != null) return false;

        Cursor cursor = mContext.getContentResolver().query(Downloads.Impl.CONTENT_URI,null,
                Downloads.Impl.COLUMN_STATUS + "=?",new String[]{""+Downloads.Impl.STATUS_RUNNING},null);
        if(cursor != null) {
            if(cursor.moveToNext()) {
                isDownload = true;
            }
            cursor.close();
        }
        if (isDownload) return true;

        isDownload = false;
        cursor = mContext.getContentResolver().query(Downloads.Impl.CONTENT_URI,null,
                Downloads.Impl.COLUMN_STATUS + "=?",new String[]{""+Downloads.Impl.STATUS_WAITING_TO_RETRY},null);
        if(cursor != null) {
            if(cursor.moveToNext()) {
                isDownload = true;
            }
            cursor.close();
        }
        if (isDownload) return true;
        return false;
    }
    //add by spreadst_lc for cmcc wifi feature end
}
