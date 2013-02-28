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

package com.android.server;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.ContentResolver;
import android.content.ContextWrapper;
import android.content.Intent;
import android.database.ContentObserver;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.net.ConnectivityManager;
import android.net.IConnectivityManager;
import android.net.MobileDataStateTracker;
import android.net.NetworkInfo;
import android.net.NetworkStateTracker;
import android.net.wifi.WifiStateTracker;
import android.net.wimax.WimaxManagerConstants;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.EventLog;
import android.util.Slog;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

import com.android.server.connectivity.Tethering;
import dalvik.system.DexClassLoader;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import android.net.Proxy;
import java.net.InetAddress;


/**
 * @hide
 */
public class ConnectivityService extends IConnectivityManager.Stub {

    private static final boolean DBG = true;
    private static final String TAG = "ConnectivityService";

    // how long to wait before switching back to a radio's default network
    private static final int RESTORE_DEFAULT_NETWORK_DELAY = 5 * 60 * 1000;
    // system property that can override the above value
    private static final String NETWORK_RESTORE_DELAY_PROP_NAME =
            "android.telephony.apn-restore";


    private Tethering mTethering;
    private boolean mTetheringConfigValid = false;

    /**
     * Sometimes we want to refer to the individual network state
     * trackers separately, and sometimes we just want to treat them
     * abstractly.
     */
    private NetworkStateTracker mNetTrackers[];

    /**
     * A per Net list of the PID's that requested access to the net
     * used both as a refcount and for per-PID DNS selection
     */
    private List mNetRequestersPids[];

    // priority order of the nettrackers
    // (excluding dynamically set mNetworkPreference)
    // TODO - move mNetworkTypePreference into this
    private int[] mPriorityList;

    private Context mContext;
    private int mNetworkPreference;
    private int mActiveDefaultNetwork = -1;
    // 0 is full bad, 100 is full good
    private int mDefaultInetCondition = 0;
    private int mDefaultInetConditionPublished = 0;
    private boolean mInetConditionChangeInFlight = false;
    private int mDefaultConnectionSequence = 0;

    private int mNumDnsEntries;

    private boolean mTestMode;
    private static ConnectivityService sServiceInstance;
    private static final int ENABLED  = 1;
    private static final int DISABLED = 0;

    // Share the event space with NetworkStateTracker (which can't see this
    // internal class but sends us events).  If you change these, change
    // NetworkStateTracker.java too.
    private static final int MIN_NETWORK_STATE_TRACKER_EVENT = 1;
    private static final int MAX_NETWORK_STATE_TRACKER_EVENT = 100;

    /**
     * used internally as a delayed event to make us switch back to the
     * default network
     */
    private static final int EVENT_RESTORE_DEFAULT_NETWORK =
            MAX_NETWORK_STATE_TRACKER_EVENT + 1;

    /**
     * used internally to change our mobile data enabled flag
     */
    private static final int EVENT_CHANGE_MOBILE_DATA_ENABLED =
            MAX_NETWORK_STATE_TRACKER_EVENT + 2;

    /**
     * used internally to change our network preference setting
     * arg1 = networkType to prefer
     */
    private static final int EVENT_SET_NETWORK_PREFERENCE =
            MAX_NETWORK_STATE_TRACKER_EVENT + 3;

    /**
     * used internally to synchronize inet condition reports
     * arg1 = networkType
     * arg2 = condition (0 bad, 100 good)
     */
    private static final int EVENT_INET_CONDITION_CHANGE =
            MAX_NETWORK_STATE_TRACKER_EVENT + 4;

    /**
     * used internally to mark the end of inet condition hold periods
     * arg1 = networkType
     */
    private static final int EVENT_INET_CONDITION_HOLD_END =
            MAX_NETWORK_STATE_TRACKER_EVENT + 5;

    /**
     * used internally to set the background data preference
     * arg1 = TRUE for enabled, FALSE for disabled
     */
    private static final int EVENT_SET_BACKGROUND_DATA =
            MAX_NETWORK_STATE_TRACKER_EVENT + 6;

    /**
     * used internally to set enable/disable cellular data
     * arg1 = ENBALED or DISABLED
     */
    private static final int EVENT_SET_MOBILE_DATA =
            MAX_NETWORK_STATE_TRACKER_EVENT + 7;

    private Handler mHandler;

    // list of DeathRecipients used to make sure features are turned off when
    // a process dies
    private List mFeatureUsers;
    private List mMmsFeatureRequest;

    //private String mMmsFeatureStopping;
    enum FeatureState {
        IDLE,
        CONNECTING,
        DISCONNECTING
    }
    FeatureState mMmsFeatureState; // state of the fist feature in mMmsFeatureRequest

    private boolean mSystemReady;
    private Intent mInitialBroadcast;

    // used in DBG mode to track inet condition reports
    private static final int INET_CONDITION_LOG_MAX_SIZE = 15;
    private ArrayList mInetLog;

    private static class NetworkAttributes {
        /**
         * Class for holding settings read from resources.
         */
        public String mName;
        public int mType;
        public int mRadio;
        public int mPriority;
        public NetworkInfo.State mLastState;
        public NetworkAttributes(String init) {
            String fragments[] = init.split(",");
            mName = fragments[0].toLowerCase();
            mType = Integer.parseInt(fragments[1]);
            mRadio = Integer.parseInt(fragments[2]);
            mPriority = Integer.parseInt(fragments[3]);
            mLastState = NetworkInfo.State.UNKNOWN;
        }
        public boolean isDefault() {
            return (mType == mRadio);
        }
    }
    NetworkAttributes[] mNetAttributes;
    int mNetworksDefined;

    private static class RadioAttributes {
        public int mSimultaneity;
        public int mType;
        public RadioAttributes(String init) {
            String fragments[] = init.split(",");
            mType = Integer.parseInt(fragments[0]);
            mSimultaneity = Integer.parseInt(fragments[1]);
        }
    }
    RadioAttributes[] mRadioAttributes;

    private static class ConnectivityThread extends Thread {
        private Context mContext;

        private ConnectivityThread(Context context) {
            super("ConnectivityThread");
            mContext = context;
        }

        @Override
        public void run() {
            Looper.prepare();
            synchronized (this) {
                sServiceInstance = new ConnectivityService(mContext);
                notifyAll();
            }
            Looper.loop();
        }

        public static ConnectivityService getServiceInstance(Context context) {
            ConnectivityThread thread = new ConnectivityThread(context);
            thread.start();

            synchronized (thread) {
                while (sServiceInstance == null) {
                    try {
                        // Wait until sServiceInstance has been initialized.
                        thread.wait();
                    } catch (InterruptedException ignore) {
                        Slog.e(TAG,
                            "Unexpected InterruptedException while waiting"+
                            " for ConnectivityService thread");
                    }
                }
            }

            return sServiceInstance;
        }
    }

    public static ConnectivityService getInstance(Context context) {
        return ConnectivityThread.getServiceInstance(context);
    }

    private ConnectivityService(Context context) {
        if (DBG) Slog.v(TAG, "ConnectivityService starting up");

        // setup our unique device name
        String id = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        if (id != null && id.length() > 0) {
            String name = new String("android_").concat(id);
            SystemProperties.set("net.hostname", name);
        }

        mContext = context;
        mNetTrackers = new NetworkStateTracker[
                ConnectivityManager.MAX_NETWORK_TYPE+1];
        mHandler = new MyHandler();

        mNetworkPreference = getPersistedNetworkPreference();

        mRadioAttributes = new RadioAttributes[ConnectivityManager.MAX_RADIO_TYPE+1];
        mNetAttributes = new NetworkAttributes[ConnectivityManager.MAX_NETWORK_TYPE+1];

        // Load device network attributes from resources
        String[] raStrings = context.getResources().getStringArray(
                com.android.internal.R.array.radioAttributes);
        for (String raString : raStrings) {
            RadioAttributes r = new RadioAttributes(raString);
            if (r.mType > ConnectivityManager.MAX_RADIO_TYPE) {
                Slog.e(TAG, "Error in radioAttributes - ignoring attempt to define type " + r.mType);
                continue;
            }
            if (mRadioAttributes[r.mType] != null) {
                Slog.e(TAG, "Error in radioAttributes - ignoring attempt to redefine type " +
                        r.mType);
                continue;
            }
            mRadioAttributes[r.mType] = r;
        }

        String[] naStrings = context.getResources().getStringArray(
                com.android.internal.R.array.networkAttributes);
        for (String naString : naStrings) {
            try {
                NetworkAttributes n = new NetworkAttributes(naString);
                if (n.mType > ConnectivityManager.MAX_NETWORK_TYPE) {
                    Slog.e(TAG, "Error in networkAttributes - ignoring attempt to define type " +
                            n.mType);
                    continue;
                }
                if (mNetAttributes[n.mType] != null) {
                    Slog.e(TAG, "Error in networkAttributes - ignoring attempt to redefine type " +
                            n.mType);
                    continue;
                }
                if (mRadioAttributes[n.mRadio] == null) {
                    Slog.e(TAG, "Error in networkAttributes - ignoring attempt to use undefined " +
                            "radio " + n.mRadio + " in network type " + n.mType);
                    continue;
                }
                mNetAttributes[n.mType] = n;
                mNetworksDefined++;
            } catch(Exception e) {
                // ignore it - leave the entry null
            }
        }

        // high priority first
        mPriorityList = new int[mNetworksDefined];
        {
            int insertionPoint = mNetworksDefined-1;
            int currentLowest = 0;
            int nextLowest = 0;
            while (insertionPoint > -1) {
                for (NetworkAttributes na : mNetAttributes) {
                    if (na == null) continue;
                    if (na.mPriority < currentLowest) continue;
                    if (na.mPriority > currentLowest) {
                        if (na.mPriority < nextLowest || nextLowest == 0) {
                            nextLowest = na.mPriority;
                        }
                        continue;
                    }
                    mPriorityList[insertionPoint--] = na.mType;
                }
                currentLowest = nextLowest;
                nextLowest = 0;
            }
        }

        mNetRequestersPids = new ArrayList[ConnectivityManager.MAX_NETWORK_TYPE+1];
        for (int i : mPriorityList) {
            mNetRequestersPids[i] = new ArrayList();
        }

        mFeatureUsers = new ArrayList();
        mMmsFeatureRequest = new ArrayList();
        //mMmsFeatureStopping = null;
        mMmsFeatureState = FeatureState.IDLE;

        mNumDnsEntries = 0;

        mTestMode = SystemProperties.get("cm.test.mode").equals("true")
                && SystemProperties.get("ro.build.type").equals("eng");
        /*
         * Create the network state trackers for Wi-Fi and mobile
         * data. Maybe this could be done with a factory class,
         * but it's not clear that it's worth it, given that
         * the number of different network types is not going
         * to change very often.
         */
        boolean noMobileData = !getMobileDataEnabledByPhoneId(TelephonyManager
                .getDefaultDataPhoneId(mContext));
        for (int netType : mPriorityList) {
            switch (mNetAttributes[netType].mRadio) {
            case ConnectivityManager.TYPE_WIFI:
                if (DBG) Slog.v(TAG, "Starting Wifi Service.");
                WifiStateTracker wst = new WifiStateTracker(context, mHandler);
                WifiService wifiService = new WifiService(context, wst);
                ServiceManager.addService(Context.WIFI_SERVICE, wifiService);
                wifiService.startWifi();
                mNetTrackers[ConnectivityManager.TYPE_WIFI] = wst;
                wst.startMonitoring();

                break;
            case ConnectivityManager.TYPE_MOBILE:
                mNetTrackers[netType] = new MobileDataStateTracker(context, mHandler,
                    netType, mNetAttributes[netType].mName);
                mNetTrackers[netType].startMonitoring();
                if (noMobileData) {
                    if (DBG) Slog.d(TAG, "tearing down Mobile networks due to setting");
                    mNetTrackers[netType].teardown();
                }
                break;
            case ConnectivityManager.TYPE_WIMAX:
                NetworkStateTracker nst = makeWimaxStateTracker();
                if (nst != null) {
                    nst.startMonitoring();
                }
                mNetTrackers[netType] = nst;
                if (noMobileData) {
                    if (DBG) Slog.d(TAG, "tearing down WiMAX networks due to setting");
                    mNetTrackers[netType].teardown();
                }
                break;
            default:
                Slog.e(TAG, "Trying to create a DataStateTracker for an unknown radio type " +
                        mNetAttributes[netType].mRadio);
                continue;
            }
        }

        mTethering = new Tethering(mContext, mHandler.getLooper());
        mTetheringConfigValid = (((mNetTrackers[ConnectivityManager.TYPE_MOBILE_DUN] != null) ||
                                  !mTethering.isDunRequired()) &&
                                 (mTethering.getTetherableUsbRegexs().length != 0 ||
                                  mTethering.getTetherableWifiRegexs().length != 0) &&
                                 mTethering.getUpstreamIfaceRegexs().length != 0);
        ContentResolver cr = mContext.getContentResolver();
        cr.registerContentObserver(
               Settings.System.getUriFor(Settings.System.MULTI_SIM_DATA_CALL), true,
               mDefaultDataPhoneIdObserver);
    }

    private NetworkStateTracker makeWimaxStateTracker() {
        //Initialize Wimax
        DexClassLoader wimaxClassLoader;
        Class wimaxStateTrackerClass = null;
        Class wimaxServiceClass = null;
        Class wimaxManagerClass;
        String wimaxJarLocation;
        String wimaxLibLocation;
        String wimaxManagerClassName;
        String wimaxServiceClassName;
        String wimaxStateTrackerClassName;

        NetworkStateTracker wimaxStateTracker = null;

        boolean isWimaxEnabled = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_wimaxEnabled);

        if (isWimaxEnabled) {
            try {
                wimaxJarLocation = mContext.getResources().getString(
                        com.android.internal.R.string.config_wimaxServiceJarLocation);
                wimaxLibLocation = mContext.getResources().getString(
                        com.android.internal.R.string.config_wimaxNativeLibLocation);
                wimaxManagerClassName = mContext.getResources().getString(
                        com.android.internal.R.string.config_wimaxManagerClassname);
                wimaxServiceClassName = mContext.getResources().getString(
                        com.android.internal.R.string.config_wimaxServiceClassname);
                wimaxStateTrackerClassName = mContext.getResources().getString(
                        com.android.internal.R.string.config_wimaxStateTrackerClassname);

                wimaxClassLoader =  new DexClassLoader(wimaxJarLocation,
                        new ContextWrapper(mContext).getCacheDir().getAbsolutePath(),
                        wimaxLibLocation,ClassLoader.getSystemClassLoader());

                try {
                    wimaxManagerClass = wimaxClassLoader.loadClass(wimaxManagerClassName);
                    wimaxStateTrackerClass = wimaxClassLoader.loadClass(wimaxStateTrackerClassName);
                    wimaxServiceClass = wimaxClassLoader.loadClass(wimaxServiceClassName);
                } catch (ClassNotFoundException ex) {
                    ex.printStackTrace();
                    return null;
                }
            } catch(Resources.NotFoundException ex) {
                Slog.e(TAG, "Wimax Resources does not exist!!! ");
                return null;
            }

            try {
                Slog.v(TAG, "Starting Wimax Service... ");

                Constructor wmxStTrkrConst = wimaxStateTrackerClass.getConstructor
                        (new Class[] {Context.class,Handler.class});
                wimaxStateTracker = (NetworkStateTracker)wmxStTrkrConst.newInstance(mContext,mHandler);

                Constructor wmxSrvConst = wimaxServiceClass.getDeclaredConstructor
                        (new Class[] {Context.class,wimaxStateTrackerClass});
                wmxSrvConst.setAccessible(true);
                IBinder svcInvoker = (IBinder) wmxSrvConst.newInstance(mContext,wimaxStateTracker);
                wmxSrvConst.setAccessible(false);

                ServiceManager.addService(WimaxManagerConstants.WIMAX_SERVICE, svcInvoker);

            } catch(ClassCastException ex) {
                ex.printStackTrace();
                return null;
            } catch (NoSuchMethodException ex) {
                ex.printStackTrace();
                return null;
            } catch (InstantiationException ex) {
                ex.printStackTrace();
                return null;
            } catch(IllegalAccessException ex) {
                ex.printStackTrace();
                return null;
            } catch(InvocationTargetException ex) {
                ex.printStackTrace();
                return null;
            } catch(Exception ex) {
                ex.printStackTrace();
                return null;
            }
        } else {
            Slog.e(TAG, "Wimax is not enabled or not added to the network attributes!!! ");
            return null;
        }

        return wimaxStateTracker;
    }

    /**
     * Sets the preferred network.
     * @param preference the new preference
     */
    public void setNetworkPreference(int preference) {
        enforceChangePermission();

        mHandler.sendMessage(mHandler.obtainMessage(EVENT_SET_NETWORK_PREFERENCE, preference, 0));
    }

    public int getNetworkPreference() {
        enforceAccessPermission();
        int preference;
        synchronized(this) {
            preference = mNetworkPreference;
        }
        return preference;
    }

    private void persistNetworkPreference(int networkPreference) {
        final ContentResolver cr = mContext.getContentResolver();
        Settings.Secure.putInt(cr, Settings.Secure.NETWORK_PREFERENCE,
                networkPreference);
    }

    private void handleSetNetworkPreference(int preference) {
        if (ConnectivityManager.isNetworkTypeValid(preference) &&
                mNetAttributes[preference] != null &&
                mNetAttributes[preference].isDefault()) {
            if (mNetworkPreference != preference) {
                final ContentResolver cr = mContext.getContentResolver();
                Settings.Secure.putInt(cr, Settings.Secure.NETWORK_PREFERENCE, preference);
                synchronized(this) {
                    mNetworkPreference = preference;
                }
                enforcePreference();
            }
        }
    }

    private int getPersistedNetworkPreference() {
        final ContentResolver cr = mContext.getContentResolver();

        final int networkPrefSetting = Settings.Secure
                .getInt(cr, Settings.Secure.NETWORK_PREFERENCE, -1);
        if (networkPrefSetting != -1) {
            return networkPrefSetting;
        }

        return ConnectivityManager.DEFAULT_NETWORK_PREFERENCE;
    }

    /**
     * Make the state of network connectivity conform to the preference settings
     * In this method, we only tear down a non-preferred network. Establishing
     * a connection to the preferred network is taken care of when we handle
     * the disconnect event from the non-preferred network
     * (see {@link #handleDisconnect(NetworkInfo)}).
     */
    private void enforcePreference() {
        if (mNetTrackers[mNetworkPreference].getNetworkInfo().isConnected())
            return;

        if (!mNetTrackers[mNetworkPreference].isAvailable())
            return;

        for (int t=0; t <= ConnectivityManager.MAX_RADIO_TYPE; t++) {
            if (t != mNetworkPreference && mNetTrackers[t] != null &&
                    mNetTrackers[t].getNetworkInfo().isConnected()) {
                if (DBG) {
                    Slog.d(TAG, "tearing down " +
                            mNetTrackers[t].getNetworkInfo() +
                            " in enforcePreference");
                }
                teardown(mNetTrackers[t]);
            }
        }
    }

    private boolean teardown(NetworkStateTracker netTracker) {
        if (netTracker.teardown()) {
            netTracker.setTeardownRequested(true);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Return NetworkInfo for the active (i.e., connected) network interface.
     * It is assumed that at most one network is active at a time. If more
     * than one is active, it is indeterminate which will be returned.
     * @return the info for the active network, or {@code null} if none is
     * active
     */
    public NetworkInfo getActiveNetworkInfo() {
        enforceAccessPermission();
        if (mActiveDefaultNetwork != -1) {
            return mNetTrackers[mActiveDefaultNetwork].getNetworkInfo();
        }
        return null;
    }

    /**
     * used for get mainsim info in multisim version
     * mainsim could be any slot but called with the same network type
     * for 3rdparty app.
     */
    private int getRealNetworkType(int networkType) {
        if (networkType == ConnectivityManager.TYPE_MOBILE_MMS) {
            networkType = ConnectivityManager.getMmsTypeByPhoneId(TelephonyManager
                    .getDefaultDataPhoneId(mContext));
        }
        return networkType;
    }

    private NetworkInfo getRealNetworkInfo(NetworkStateTracker t) {
        NetworkInfo info = t.getNetworkInfo();
        if (info.getType() == ConnectivityManager.TYPE_MOBILE_MMS) {
            info = mNetTrackers[getRealNetworkType(ConnectivityManager.TYPE_MOBILE_MMS)]
                    .getNetworkInfo();
        }
        return info;
    }

    public NetworkInfo getNetworkInfo(int networkType) {
        enforceAccessPermission();
        networkType = getRealNetworkType(networkType);
        if (ConnectivityManager.isNetworkTypeValid(networkType)) {
            NetworkStateTracker t = mNetTrackers[networkType];
            if (t != null) {
                return t.getNetworkInfo();
	    } else {
		Slog.e ("sunway","NetworkStateTracker is null");
	    }
        }
	Slog.e ("sunway","getNetworkInfo returns null");
        return null;
    }

    public NetworkInfo[] getAllNetworkInfo() {
        enforceAccessPermission();
        NetworkInfo[] result = new NetworkInfo[mNetworksDefined];
        int i = 0;
        for (NetworkStateTracker t : mNetTrackers) {
            if(t != null) result[i++] = getRealNetworkInfo(t);
        }
        return result;
    }

    public boolean setRadios(boolean turnOn) {
        boolean result = true;
        enforceChangePermission();
        for (NetworkStateTracker t : mNetTrackers) {
            if (t != null) result = t.setRadio(turnOn) && result;
        }
        return result;
    }

    public boolean setRadio(int netType, boolean turnOn) {
        enforceChangePermission();
        if (!ConnectivityManager.isNetworkTypeValid(netType)) {
            return false;
        }
        NetworkStateTracker tracker = mNetTrackers[netType];
        return tracker != null && tracker.setRadio(turnOn);
    }

    /**
     * Used to notice when the calling process dies so we can self-expire
     *
     * Also used to know if the process has cleaned up after itself when
     * our auto-expire timer goes off.  The timer has a link to an object.
     *
     */
    private class FeatureUser implements IBinder.DeathRecipient {
        int mNetworkType;
        String mFeature;
        IBinder mBinder;
        int mPid;
        int mUid;
        long mCreateTime;

        FeatureUser(int type, String feature, IBinder binder) {
            super();
            mNetworkType = type;
            mFeature = feature;
            mBinder = binder;
            mPid = getCallingPid();
            mUid = getCallingUid();
            mCreateTime = System.currentTimeMillis();

            try {
                mBinder.linkToDeath(this, 0);
            } catch (RemoteException e) {
                binderDied();
            }
        }

        void unlinkDeathRecipient() {
            mBinder.unlinkToDeath(this, 0);
        }

        public void binderDied() {
            Slog.d(TAG, "ConnectivityService FeatureUser binderDied(" +
                    mNetworkType + ", " + mFeature + ", " + mBinder + "), created " +
                    (System.currentTimeMillis() - mCreateTime) + " mSec ago");
            stopUsingNetworkFeature(this, false);
        }

        public void expire() {
            Slog.d(TAG, "ConnectivityService FeatureUser expire(" +
                    mNetworkType + ", " + mFeature + ", " + mBinder +"), created " +
                    (System.currentTimeMillis() - mCreateTime) + " mSec ago");
            stopUsingNetworkFeature(this, false);
        }

        public String toString() {
            return "FeatureUser("+mNetworkType+","+mFeature+","+mPid+","+mUid+"), created " +
                    (System.currentTimeMillis() - mCreateTime) + " mSec ago";
        }
    }

    // javadoc from interface
    public int startUsingNetworkFeature(int networkType, String feature,
            IBinder binder) {
        if (DBG) {
            Slog.d(TAG, "startUsingNetworkFeature for net " + networkType +
                    ": " + feature);
        }
        // avoid the case in which the second startUsingNetworkFeature call is started very soon
        // after the first one, thus the detach caused by first call is not finished, it will cause
        // the disconnect done message cased by second call is immediately received.
        boolean isAlreadyConnecting = false;
        FeatureUser f = new FeatureUser(networkType, feature, binder);
        synchronized(this) {
            if (isMmsFeature(feature)) {
                if (mMmsFeatureRequest.isEmpty()) {
                    // current data connection is default
                    mMmsFeatureRequest.add(f);
                    mMmsFeatureState = FeatureState.CONNECTING;
                    if (DBG) {
                        Slog.d(TAG, "startUsing Mms Feature");
                    }
                    /*
                    if (mMmsFeatureStopping == null) {
                        if (DBG) {
                            Slog.d(TAG, "startUsing Mms Feature");
                        }
                    } else {
                        if (DBG) {
                            Slog.d(TAG, "startUsing Mms Feature but stopping is not finished");
                        }
                        return Phone.APN_REQUEST_STARTED;
                    }*/
                } else {
                    // current data connection is mms
                    FeatureUser k = (FeatureUser)mMmsFeatureRequest.get(0);
                    if (TextUtils.equals(k.mFeature, feature) && mMmsFeatureState != FeatureState.DISCONNECTING) {
                        if (mMmsFeatureState == FeatureState.CONNECTING) {
                             //isAlreadyConnecting = true;
                        }
                        if (DBG) {
                            Slog.d(TAG, "startUsing same Mms Feature as current mMmsFeatureState=" + mMmsFeatureState);
                        }
                        mMmsFeatureState = FeatureState.CONNECTING;
                        /*
                        if (mMmsFeatureStopping == null) {
                            if (DBG) {
                                Slog.d(TAG, "startUsing same Mms Feature as current");
                            }
                            isAlreadyConnecting = true; // network is connecting in this case
                        } else {
                            if (DBG) {
                                Slog.d(TAG, "startUsing same Mms Feature as current but stopping is not finished:"
                                        + mMmsFeatureStopping);
                            }
                            return Phone.APN_REQUEST_STARTED;
                        }
                        */
                    } else {
                        boolean found = false;
                        for (int i = 0; i < mMmsFeatureRequest.size(); i++) {
                            FeatureUser u = (FeatureUser)mMmsFeatureRequest.get(i);
                            if (TextUtils.equals(feature, u.mFeature)) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            if (DBG) {
                                Slog.d(TAG, "startUsing Mms pending " + feature);
                            }
                            mMmsFeatureRequest.add(f);
                        } else {
                            if (DBG) {
                                Slog.d(TAG, "startUsing duplicate Mms pending " + feature);
                            }
                        }
                        return Phone.APN_REQUEST_STARTED;
                    }
                }
            }
        }
        enforceChangePermission();
        if (!ConnectivityManager.isNetworkTypeValid(networkType) ||
                mNetAttributes[networkType] == null) {
            return Phone.APN_REQUEST_FAILED;
        }


        boolean skipAvailableCheck = false;
        // TODO - move this into the MobileDataStateTracker
        int usedNetworkType = networkType;
        if(networkType == ConnectivityManager.TYPE_MOBILE) {
            //if type is MMS,continue setup data call
            Slog.d(TAG, "if type is MMS,continue setup data call");
            if (!getMobileDataEnabledByPhoneId(getPhoneIdByFeature(feature,true)) &&
                    //modify for <Bug#130570> DM in dual sim mode start
            		!(feature.indexOf(Phone.FEATURE_ENABLE_DM)!=-1) &&
                    //modify for <Bug#130570> DM in dual sim mode end
                    !(feature.indexOf(Phone.FEATURE_ENABLE_MMS)!=-1) &&
                    !(feature.indexOf(Phone.FEATURE_ENABLE_WAP)!=-1)) {
            //if (!getMobileDataEnabledByPhoneId(getPhoneIdByFeature(feature))) {
                if (DBG) Slog.d(TAG, "requested special network with data disabled - rejected");
                return Phone.APN_TYPE_NOT_AVAILABLE;
            }
            //modify for <Bug#130570> DM in dual sim mode start
            //if (TextUtils.equals(feature, Phone.FEATURE_ENABLE_DM)) {
            //    usedNetworkType = ConnectivityManager.TYPE_MOBILE_DM;
            //}
            
            if (TextUtils.equals(feature.substring(0, Phone.FEATURE_ENABLE_DM.length()), Phone.FEATURE_ENABLE_DM)) {
                skipAvailableCheck = true;
                if (TextUtils.equals(feature, Phone.FEATURE_ENABLE_DM)) {
                    usedNetworkType = ConnectivityManager.TYPE_MOBILE_DM;
                } else {
                    int phoneId = getPhoneIdByFeature(feature,true);
                    usedNetworkType = ConnectivityManager.TYPE_MOBILE_MMS_EXTERNAL + phoneId + 1;
                }
            //modify for <Bug#130570> DM in dual sim mode end
            } else if (TextUtils.equals(feature.substring(0, Phone.FEATURE_ENABLE_MMS.length()), Phone.FEATURE_ENABLE_MMS)) {
                skipAvailableCheck = true;
                usedNetworkType = ConnectivityManager.getMmsTypeByPhoneId(getPhoneIdByFeature(feature,false));
            } else if (TextUtils.equals(feature, Phone.FEATURE_ENABLE_SUPL)) {
                usedNetworkType = ConnectivityManager.TYPE_MOBILE_SUPL;
            } else if (TextUtils.equals(feature, Phone.FEATURE_ENABLE_DUN)) {
                usedNetworkType = ConnectivityManager.TYPE_MOBILE_DUN;
            } else if (TextUtils.equals(feature, Phone.FEATURE_ENABLE_HIPRI)) {
                usedNetworkType = ConnectivityManager.TYPE_MOBILE_HIPRI;
            } else if (TextUtils.equals(feature, Phone.FEATURE_ENABLE_WAP)) {
                usedNetworkType = ConnectivityManager.getMmsTypeByPhoneId(getPhoneIdByFeature(feature,true));
            }
        }
        NetworkStateTracker network = mNetTrackers[usedNetworkType];
        if (DBG) Slog.d(TAG, "network = " + network);
        if (network != null) {
            if (usedNetworkType != networkType) {
                Integer currentPid = new Integer(getCallingPid());

                NetworkStateTracker radio = mNetTrackers[networkType];
                NetworkInfo ni = network.getNetworkInfo();

                // TODO: using serviceState to check is better for Msms
                if (!skipAvailableCheck && ni.isAvailable() == false) {
                    if (DBG) Slog.d(TAG, "special network not available");
                    return Phone.APN_TYPE_NOT_AVAILABLE;
                }

                synchronized(this) {
                    mFeatureUsers.add(f);
                    if (!mNetRequestersPids[usedNetworkType].contains(currentPid)) {
                        // this gets used for per-pid dns when connected
                        mNetRequestersPids[usedNetworkType].add(currentPid);
                    }
                }
                mHandler.sendMessageDelayed(mHandler.obtainMessage(EVENT_RESTORE_DEFAULT_NETWORK,
                        f), getRestoreDefaultNetworkDelay());

                if (DBG) Slog.d(TAG, "ni.isConnectedOrConnecting()="+ni.isConnectedOrConnecting());

                if ((ni.isConnectedOrConnecting() == true) &&
                        !network.isTeardownRequested()) {
                    if (ni.isConnected() == true) {
                        // add the pid-specific dns
                        handleDnsConfigurationChange(networkType);
                        if (DBG) Slog.d(TAG, "special network already active");
                        return Phone.APN_ALREADY_ACTIVE;
                    }
                    if (DBG) Slog.d(TAG, "special network already connecting");
                    return Phone.APN_REQUEST_STARTED;
                }

                // check if the radio in play can make another contact
                // assume if cannot for now

                if (!isAlreadyConnecting) {
                    if (DBG) Slog.d(TAG, "reconnecting to special network");
                    synchronized(this) {
                        if(!network.reconnect()){
                            //if the mms apn is not available, remove the request from mMmsFeatureRequest
                            //if not remove it, the othe mms send request will be pending.
                            if (isMmsFeature(feature) && mMmsFeatureRequest.size() > 0) {
                                mMmsFeatureRequest.remove(0);
                            }
                            return Phone.APN_TYPE_NOT_AVAILABLE;
                        }
                    }
                } else {
                    if (DBG) Slog.d(TAG, "already reconnecting to special network");
                }
                return Phone.APN_REQUEST_STARTED;
            } else {
                synchronized(this) {
                    mFeatureUsers.add(f);
                }
                mHandler.sendMessageDelayed(mHandler.obtainMessage(EVENT_RESTORE_DEFAULT_NETWORK,
                        f), getRestoreDefaultNetworkDelay());

                return network.startUsingNetworkFeature(feature,
                        getCallingPid(), getCallingUid());
            }
        }
        return Phone.APN_TYPE_NOT_AVAILABLE;
    }

    // javadoc from interface
    public int stopUsingNetworkFeature(int networkType, String feature) {
        enforceChangePermission();

        int pid = getCallingPid();
        int uid = getCallingUid();

        FeatureUser u = null;
        boolean found = false;

        synchronized(this) {
            for (int i = 0; i < mFeatureUsers.size() ; i++) {
                u = (FeatureUser)mFeatureUsers.get(i);
                if (uid == u.mUid && pid == u.mPid &&
                        networkType == u.mNetworkType &&
                        TextUtils.equals(feature, u.mFeature)) {
                    found = true;
                    break;
                }
            }
        }
        if (found && u != null) {
            // stop regardless of how many other time this proc had called start
            return stopUsingNetworkFeature(u, true);
        } else {
            // none found!
            if (DBG) Slog.d(TAG, "ignoring stopUsingNetworkFeature - not a live request");
            return 1;
        }
    }

    private int stopUsingNetworkFeature(FeatureUser u, boolean ignoreDups) {
        int networkType = u.mNetworkType;
        String feature = u.mFeature;
        int pid = u.mPid;
        int uid = u.mUid;

        NetworkStateTracker tracker = null;
        boolean callTeardown = false;  // used to carry our decision outside of sync block

        if (DBG) {
            Slog.d(TAG, "stopUsingNetworkFeature for net " + networkType +
                    ": " + feature);
        }

        if (!ConnectivityManager.isNetworkTypeValid(networkType)) {
            return -1;
        }

        // need to link the mFeatureUsers list with the mNetRequestersPids state in this
        // sync block
        synchronized(this) {
            // check if this process still has an outstanding start request
            if (!mFeatureUsers.contains(u)) {
                if (DBG) Slog.d(TAG, "ignoring - this process has no outstanding requests");
                return 1;
            }
            u.unlinkDeathRecipient();
            mFeatureUsers.remove(mFeatureUsers.indexOf(u));
            // If we care about duplicate requests, check for that here.
            //
            // This is done to support the extension of a request - the app
            // can request we start the network feature again and renew the
            // auto-shutoff delay.  Normal "stop" calls from the app though
            // do not pay attention to duplicate requests - in effect the
            // API does not refcount and a single stop will counter multiple starts.
            if (ignoreDups == false) {
                for (int i = 0; i < mFeatureUsers.size() ; i++) {
                    FeatureUser x = (FeatureUser)mFeatureUsers.get(i);
                    if (x.mUid == u.mUid && x.mPid == u.mPid &&
                            x.mNetworkType == u.mNetworkType &&
                            TextUtils.equals(x.mFeature, u.mFeature)) {
                        if (DBG) Slog.d(TAG, "ignoring stopUsingNetworkFeature as dup is found");
                        return 1;
                    }
                }
            }

            // TODO - move to MobileDataStateTracker
            int usedNetworkType = networkType;
            if (networkType == ConnectivityManager.TYPE_MOBILE) {
                //modify for <Bug#130570> DM in dual sim mode start
                //if (TextUtils.equals(feature, Phone.FEATURE_ENABLE_DM)) {
                //    usedNetworkType = ConnectivityManager.TYPE_MOBILE_DM;
                
                if (TextUtils.equals(feature.substring(0, Phone.FEATURE_ENABLE_DM.length()), Phone.FEATURE_ENABLE_DM)) {
                    if (TextUtils.equals(feature, Phone.FEATURE_ENABLE_DM)) {
                        usedNetworkType = ConnectivityManager.TYPE_MOBILE_DM;
                    } else {
                        int phoneId = getPhoneIdByFeature(feature,true);
                        usedNetworkType = ConnectivityManager.TYPE_MOBILE_MMS_EXTERNAL + phoneId + 1;
                    }
                //modify for <Bug#130570> DM in dual sim mode end
                } else if (TextUtils.equals(feature.subSequence(0, Phone.FEATURE_ENABLE_MMS.length()), Phone.FEATURE_ENABLE_MMS)) {
                    usedNetworkType = ConnectivityManager.getMmsTypeByPhoneId(getPhoneIdByFeature(feature,false));
                } else if (TextUtils.equals(feature, Phone.FEATURE_ENABLE_SUPL)) {
                    usedNetworkType = ConnectivityManager.TYPE_MOBILE_SUPL;
                } else if (TextUtils.equals(feature, Phone.FEATURE_ENABLE_DUN)) {
                    usedNetworkType = ConnectivityManager.TYPE_MOBILE_DUN;
                } else if (TextUtils.equals(feature, Phone.FEATURE_ENABLE_HIPRI)) {
                    usedNetworkType = ConnectivityManager.TYPE_MOBILE_HIPRI;
                } else if (TextUtils.equals(feature, Phone.FEATURE_ENABLE_WAP)) {
                    if(PhoneFactory.getPhoneCount() < 2) {
                        usedNetworkType = ConnectivityManager.getMmsTypeByPhoneId(getPhoneIdByFeature(feature,true));
                    } else {
                        Integer currentPid = new Integer(pid);
                        int mmsType;

                        mmsType = ConnectivityManager.getMmsTypeByPhoneId(0);
                        if(mNetRequestersPids[mmsType].contains(currentPid)) {
                            usedNetworkType = mmsType;
                        } else {
                            mmsType = ConnectivityManager.getMmsTypeByPhoneId(1);
                            if(mNetRequestersPids[mmsType].contains(currentPid)) {
                                usedNetworkType = mmsType;
                            } else {
                                usedNetworkType = ConnectivityManager.getMmsTypeByPhoneId(getPhoneIdByFeature(feature,true));
                            }
                        }
                    }
                }
            }
            tracker =  mNetTrackers[usedNetworkType];
            if (tracker == null) {
                if (DBG) Slog.d(TAG, "ignoring - no known tracker for net type " + usedNetworkType);
                return -1;
            }
            if (usedNetworkType != networkType) {
                Integer currentPid = new Integer(pid);
                mNetRequestersPids[usedNetworkType].remove(currentPid);
                reassessPidDns(pid, true);
                if (mNetRequestersPids[usedNetworkType].size() != 0) {
                    if (DBG) Slog.d(TAG, "not tearing down special network - " +
                           "others still using it");
                    return 1;
                }
                callTeardown = true;
            }

            if (callTeardown) {
                if (isMmsFeature(feature) && !mMmsFeatureRequest.isEmpty()) {
                    FeatureUser f = (FeatureUser) mMmsFeatureRequest.get(0);
                    if (DBG) Slog.d(TAG, "stopUsing " + feature + " first is " + f.mFeature);
                    if (TextUtils.equals(f.mFeature, feature)) {
                        mMmsFeatureRequest.remove(f);
                        //mMmsFeatureStopping = f.mFeature;
                        mMmsFeatureState = FeatureState.DISCONNECTING;
                    } else {
                            Slog.e(TAG, "stoping feature is not the first one in the list!");
                    }
                }
            }
        }
        if (DBG) Slog.d(TAG, "Doing network teardown");
        if (callTeardown) {
            tracker.teardown();
            return 1;
        } else {
            // do it the old fashioned way
            return tracker.stopUsingNetworkFeature(feature, pid, uid);
        }
    }

    /**
     * Ensure that a network route exists to deliver traffic to the specified
     * host via the specified network interface.
     * @param networkType the type of the network over which traffic to the
     * specified host is to be routed
     * @param hostAddress the IP address of the host to which the route is
     * desired
     * @return {@code true} on success, {@code false} on failure
     */
    public boolean requestRouteToHost(int networkType, int hostAddress) {
        enforceChangePermission();
        if (!ConnectivityManager.isNetworkTypeValid(networkType)) {
            return false;
        }
        networkType = getRealNetworkType(networkType);
        NetworkStateTracker tracker = mNetTrackers[networkType];

        if (tracker == null || !tracker.getNetworkInfo().isConnected() ||
                tracker.isTeardownRequested()) {
            if (DBG) {
                Slog.d(TAG, "requestRouteToHost on down network (" + networkType + ") - dropped");
            }
            return false;
        }
        return tracker.requestRouteToHost(hostAddress);
    }

    /**
     * @see ConnectivityManager#getBackgroundDataSetting()
     */
    public boolean getBackgroundDataSetting() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.BACKGROUND_DATA, 1) == 1;
    }

    /**
     * @see ConnectivityManager#setBackgroundDataSetting(boolean)
     */
    public void setBackgroundDataSetting(boolean allowBackgroundDataUsage) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.CHANGE_BACKGROUND_DATA_SETTING,
                "ConnectivityService");

        mHandler.sendMessage(mHandler.obtainMessage(EVENT_SET_BACKGROUND_DATA,
                (allowBackgroundDataUsage ? ENABLED : DISABLED), 0));
    }

    private void handleSetBackgroundData(boolean enabled) {
        if (enabled != getBackgroundDataSetting()) {
            Settings.Secure.putInt(mContext.getContentResolver(),
                    Settings.Secure.BACKGROUND_DATA, enabled ? 1 : 0);
            Intent broadcast = new Intent(
                    ConnectivityManager.ACTION_BACKGROUND_DATA_SETTING_CHANGED);
            mContext.sendBroadcast(broadcast);
        }
    }

    /**
     * @see ConnectivityManager#getMobileDataEnabled()
     */
    public boolean getMobileDataEnabled() {
        enforceAccessPermission();
        boolean retVal = Settings.Secure.getIntAtIndex(mContext.getContentResolver(),
                Settings.Secure.MOBILE_DATA, PhoneFactory.getDefaultPhoneId(), 1) == 1;
        if (DBG) Slog.d(TAG, "getMobileDataEnabled returning " + retVal);
        return retVal;
    }

    /**
     * @see ConnectivityManager#setMobileDataEnabled(boolean)
     */
    public void setMobileDataEnabled(boolean enabled) {
        enforceChangePermission();
        if (DBG) Slog.d(TAG, "setMobileDataEnabled(" + enabled + ")");

        mHandler.sendMessage(mHandler.obtainMessage(EVENT_SET_MOBILE_DATA,
            (enabled ? ENABLED : DISABLED), 0));
    }

    private void handleSetMobileData(boolean enabled) {
        if (getMobileDataEnabled() == enabled) return;

        Settings.Secure.putIntAtIndex(mContext.getContentResolver(), Settings.Secure.MOBILE_DATA,
                PhoneFactory.getDefaultPhoneId(), enabled ? 1 : 0);
        if (enabled) {
            if (mNetTrackers[ConnectivityManager.TYPE_MOBILE] != null) {
                if (DBG) {
                    Slog.d(TAG, "starting up " + mNetTrackers[ConnectivityManager.TYPE_MOBILE]);
                }
                mNetTrackers[ConnectivityManager.TYPE_MOBILE].reconnect();
            }
            if (mNetTrackers[ConnectivityManager.TYPE_WIMAX] != null) {
                if (DBG) {
                    Slog.d(TAG, "starting up " + mNetTrackers[ConnectivityManager.TYPE_WIMAX]);
                }
                mNetTrackers[ConnectivityManager.TYPE_WIMAX].reconnect();
            }
        } else {
            for (NetworkStateTracker nt : mNetTrackers) {
                if (nt == null) continue;
                int netType = nt.getNetworkInfo().getType();
                if (mNetAttributes[netType].mRadio == ConnectivityManager.TYPE_MOBILE  && !isMmsType(netType)) {
                    if (DBG) Slog.d(TAG, "tearing down " + nt);
                    nt.teardown();
                }
            }
            if (mNetTrackers[ConnectivityManager.TYPE_WIMAX] != null) {
                mNetTrackers[ConnectivityManager.TYPE_WIMAX].teardown();
            }
        }
    }

    private int getNumConnectedNetworks() {
        int numConnectedNets = 0;

        for (NetworkStateTracker nt : mNetTrackers) {
            if (nt != null && nt.getNetworkInfo().isConnected() &&
                    !nt.isTeardownRequested()) {
                ++numConnectedNets;
            }
        }
        return numConnectedNets;
    }

    private void enforceAccessPermission() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.ACCESS_NETWORK_STATE,
                "ConnectivityService");
    }

    private void enforceChangePermission() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.CHANGE_NETWORK_STATE,
                "ConnectivityService");
    }

    // TODO Make this a special check when it goes public
    private void enforceTetherChangePermission() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.CHANGE_NETWORK_STATE,
                "ConnectivityService");
    }

    private void enforceTetherAccessPermission() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.ACCESS_NETWORK_STATE,
                "ConnectivityService");
    }

    /**
     * Handle a {@code DISCONNECTED} event. If this pertains to the non-active
     * network, we ignore it. If it is for the active network, we send out a
     * broadcast. But first, we check whether it might be possible to connect
     * to a different network.
     * @param info the {@code NetworkInfo} for the network
     */
    private void handleDisconnect(NetworkInfo info) {

        int prevNetType = info.getType();

        mNetTrackers[prevNetType].setTeardownRequested(false);
        /*
         * If the disconnected network is not the active one, then don't report
         * this as a loss of connectivity. What probably happened is that we're
         * getting the disconnect for a network that we explicitly disabled
         * in accordance with network preference policies.
         */
        if (!mNetAttributes[prevNetType].isDefault()) {
            List pids = mNetRequestersPids[prevNetType];
            for (int i = 0; i<pids.size(); i++) {
                Integer pid = (Integer)pids.get(i);
                // will remove them because the net's no longer connected
                // need to do this now as only now do we know the pids and
                // can properly null things that are no longer referenced.
                reassessPidDns(pid.intValue(), false);
            }
        }

        Intent intent = new Intent(ConnectivityManager.CONNECTIVITY_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra(ConnectivityManager.EXTRA_NETWORK_INFO, info);
        if (info.isFailover()) {
            intent.putExtra(ConnectivityManager.EXTRA_IS_FAILOVER, true);
            info.setFailover(false);
        }
        if (info.getReason() != null) {
            intent.putExtra(ConnectivityManager.EXTRA_REASON, info.getReason());
        }
        if (info.getExtraInfo() != null) {
            intent.putExtra(ConnectivityManager.EXTRA_EXTRA_INFO,
                    info.getExtraInfo());
        }

        if (mNetAttributes[prevNetType].isDefault()) {
            tryFailover(prevNetType);
            if (mActiveDefaultNetwork != -1) {
                NetworkInfo switchTo = mNetTrackers[mActiveDefaultNetwork].getNetworkInfo();
                intent.putExtra(ConnectivityManager.EXTRA_OTHER_NETWORK_INFO, switchTo);
            } else {
                mDefaultInetConditionPublished = 0; // we're not connected anymore
                intent.putExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, true);
            }
        }
        intent.putExtra(ConnectivityManager.EXTRA_INET_CONDITION, mDefaultInetConditionPublished);
        // do this before we broadcast the change
        handleConnectivityChange(prevNetType);

        sendStickyBroadcast(intent);
        /*
         * If the failover network is already connected, then immediately send
         * out a followup broadcast indicating successful failover
         */
        if (mActiveDefaultNetwork != -1) {
            sendConnectedBroadcast(mNetTrackers[mActiveDefaultNetwork].getNetworkInfo());
        }

        synchronized(this) {
            for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
                if (prevNetType == ConnectivityManager.getMmsTypeByPhoneId(i)) {
                    Slog.d(TAG, "prevNetType " + prevNetType);
                    //if (mMmsFeatureStopping != null) {
                    if (mMmsFeatureState == FeatureState.DISCONNECTING) {
                        if (!mMmsFeatureRequest.isEmpty()) {
                            FeatureUser f = (FeatureUser) mMmsFeatureRequest.get(0);
                            if (getPhoneIdByFeature(f.mFeature,true) != i) {
                                Slog.e(TAG, "disconnected mms is " + i + " but stopping is "
                                        + f.mFeature);
                            } else {
                                Slog.d(TAG, "remove disconnect " + f.mFeature);
                                // mMmsFeatureStopping = null;
                                mMmsFeatureRequest.remove(0);
                                mMmsFeatureState = FeatureState.IDLE;
                            }
                        }
                    } else {
                        // disconnect by network
                        Slog.d(TAG, "mms disconnect by network");
                        if (!mMmsFeatureRequest.isEmpty()) {
                            FeatureUser f = (FeatureUser) mMmsFeatureRequest.get(0);
                            if (getPhoneIdByFeature(f.mFeature,true) != i) {
                                Slog.e(TAG, "disconnected mms is " + i + " but first is "
                                        + f.mFeature);
                            } else {
                                Slog.d(TAG, "remove disconnect " + f.mFeature);
                                mMmsFeatureRequest.remove(0);
                                mMmsFeatureState = FeatureState.IDLE;
                            }
                        }
                    }
                }
            }
        }
    }

    private void tryFailover(int prevNetType) {
        /*
         * If this is a default network, check if other defaults are available
         * or active
         */
        if (mNetAttributes[prevNetType].isDefault()) {
            if (mActiveDefaultNetwork == prevNetType) {
                mActiveDefaultNetwork = -1;
            }

            int newType = -1;
            int newPriority = -1;
            boolean noMobileData = !getMobileDataEnabledByPhoneId(TelephonyManager
                    .getDefaultDataPhoneId(mContext));
            for (int checkType=0; checkType <= ConnectivityManager.MAX_NETWORK_TYPE; checkType++) {
                if (checkType == prevNetType) continue;
                if (mNetAttributes[checkType] == null) continue;
                if (mNetAttributes[checkType].isDefault() == false) continue;
                if (mNetAttributes[checkType].mRadio == ConnectivityManager.TYPE_MOBILE &&
                        noMobileData) {
                    Slog.e(TAG, "not failing over to mobile type " + checkType +
                            " because Mobile Data Disabled");
                    continue;
                }
                if (mNetAttributes[checkType].mRadio == ConnectivityManager.TYPE_WIMAX &&
                        noMobileData) {
                    Slog.e(TAG, "not failing over to mobile type " + checkType +
                            " because Mobile Data Disabled");
                    continue;
                }
                NetworkStateTracker checkTracker = mNetTrackers[checkType];
                NetworkInfo checkInfo = checkTracker.getNetworkInfo();
                if (!checkInfo.isConnectedOrConnecting() || checkTracker.isTeardownRequested()) {
                    checkInfo.setFailover(true);
                    checkTracker.reconnect();
                }
                if (DBG) Slog.d(TAG, "Attempting to switch to " + checkInfo.getTypeName());
            }
        }
    }

    private void sendConnectedBroadcast(NetworkInfo info) {
        sendGeneralBroadcast(info, ConnectivityManager.CONNECTIVITY_ACTION);
    }

    private void sendInetConditionBroadcast(NetworkInfo info) {
        sendGeneralBroadcast(info, ConnectivityManager.INET_CONDITION_ACTION);
    }

    private void sendGeneralBroadcast(NetworkInfo info, String bcastType) {
        Intent intent = new Intent(bcastType);
        intent.putExtra(ConnectivityManager.EXTRA_NETWORK_INFO, info);
        if (info.isFailover()) {
            intent.putExtra(ConnectivityManager.EXTRA_IS_FAILOVER, true);
            info.setFailover(false);
        }
        if (info.getReason() != null) {
            intent.putExtra(ConnectivityManager.EXTRA_REASON, info.getReason());
        }
        if (info.getExtraInfo() != null) {
            intent.putExtra(ConnectivityManager.EXTRA_EXTRA_INFO,
                    info.getExtraInfo());
        }
        intent.putExtra(ConnectivityManager.EXTRA_INET_CONDITION, mDefaultInetConditionPublished);
        sendStickyBroadcast(intent);
    }

    /**
     * Called when an attempt to fail over to another network has failed.
     * @param info the {@link NetworkInfo} for the failed network
     */
    private void handleConnectionFailure(NetworkInfo info) {
        mNetTrackers[info.getType()].setTeardownRequested(false);

        String reason = info.getReason();
        String extraInfo = info.getExtraInfo();

        String reasonText;
        if (reason == null) {
            reasonText = ".";
        } else {
            reasonText = " (" + reason + ").";
        }
        Slog.e(TAG, "Attempt to connect to " + info.getTypeName() + " failed" + reasonText);

        Intent intent = new Intent(ConnectivityManager.CONNECTIVITY_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra(ConnectivityManager.EXTRA_NETWORK_INFO, info);
        if (getActiveNetworkInfo() == null) {
            intent.putExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, true);
        }
        if (reason != null) {
            intent.putExtra(ConnectivityManager.EXTRA_REASON, reason);
        }
        if (extraInfo != null) {
            intent.putExtra(ConnectivityManager.EXTRA_EXTRA_INFO, extraInfo);
        }
        if (info.isFailover()) {
            intent.putExtra(ConnectivityManager.EXTRA_IS_FAILOVER, true);
            info.setFailover(false);
        }

        if (mNetAttributes[info.getType()].isDefault()) {
            tryFailover(info.getType());
            if (mActiveDefaultNetwork != -1) {
                NetworkInfo switchTo = mNetTrackers[mActiveDefaultNetwork].getNetworkInfo();
                intent.putExtra(ConnectivityManager.EXTRA_OTHER_NETWORK_INFO, switchTo);
            } else {
                mDefaultInetConditionPublished = 0;
                intent.putExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, true);
            }
        }

        intent.putExtra(ConnectivityManager.EXTRA_INET_CONDITION, mDefaultInetConditionPublished);
        sendStickyBroadcast(intent);
        /*
         * If the failover network is already connected, then immediately send
         * out a followup broadcast indicating successful failover
         */
        if (mActiveDefaultNetwork != -1) {
            sendConnectedBroadcast(mNetTrackers[mActiveDefaultNetwork].getNetworkInfo());
        }
    }

    private void sendStickyBroadcast(Intent intent) {
        synchronized(this) {
            if (!mSystemReady) {
                mInitialBroadcast = new Intent(intent);
            }
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            mContext.sendStickyBroadcast(intent);
        }
    }

    void systemReady() {
        synchronized(this) {
            mSystemReady = true;
            if (mInitialBroadcast != null) {
                mContext.sendStickyBroadcast(mInitialBroadcast);
                mInitialBroadcast = null;
            }
        }
    }

    private void handleConnect(NetworkInfo info) {
        int type = info.getType();

        // snapshot isFailover, because sendConnectedBroadcast() resets it
        boolean isFailover = info.isFailover();
        NetworkStateTracker thisNet = mNetTrackers[type];

        // if this is a default net and other default is running
        // kill the one not preferred
        if (mNetAttributes[type].isDefault()) {
            if (mActiveDefaultNetwork != -1 && mActiveDefaultNetwork != type) {
                if ((type != mNetworkPreference &&
                        mNetAttributes[mActiveDefaultNetwork].mPriority >
                        mNetAttributes[type].mPriority) ||
                        mNetworkPreference == mActiveDefaultNetwork) {
                        // don't accept this one
                        if (DBG) Slog.v(TAG, "Not broadcasting CONNECT_ACTION " +
                                "to torn down network " + info.getTypeName());
                        teardown(thisNet);
                        return;
                } else {
                    // tear down the other
                    NetworkStateTracker otherNet =
                            mNetTrackers[mActiveDefaultNetwork];
                    if (DBG) Slog.v(TAG, "Policy requires " +
                            otherNet.getNetworkInfo().getTypeName() +
                            " teardown");
                    if (!teardown(otherNet)) {
                        Slog.e(TAG, "Network declined teardown request");
                        teardown(thisNet);
                        return;
                    }
                    if (isFailover) {
                        otherNet.releaseWakeLock();
                    }
                }
            }
            mActiveDefaultNetwork = type;
            // this will cause us to come up initially as unconnected and switching
            // to connected after our normal pause unless somebody reports us as reall
            // disconnected
            mDefaultInetConditionPublished = 0;
            mDefaultConnectionSequence++;
            mInetConditionChangeInFlight = false;
            // Don't do this - if we never sign in stay, grey
            //reportNetworkCondition(mActiveDefaultNetwork, 100);
        }
        thisNet.setTeardownRequested(false);
        thisNet.updateNetworkSettings();
        handleConnectivityChange(type);
        sendConnectedBroadcast(info);
    }

    private void handleScanResultsAvailable(NetworkInfo info) {
        int networkType = info.getType();
        if (networkType != ConnectivityManager.TYPE_WIFI) {
            if (DBG) Slog.v(TAG, "Got ScanResultsAvailable for " +
                    info.getTypeName() + " network. Don't know how to handle.");
        }

        mNetTrackers[networkType].interpretScanResultsAvailable();
    }

    private void handleNotificationChange(boolean visible, int id,
            Notification notification) {
        NotificationManager notificationManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);

        if (visible) {
            notificationManager.notify(id, notification);
        } else {
            notificationManager.cancel(id);
        }
    }

    /**
     * After a change in the connectivity state of any network, We're mainly
     * concerned with making sure that the list of DNS servers is setupup
     * according to which networks are connected, and ensuring that the
     * right routing table entries exist.
     */
    private void handleConnectivityChange(int netType) {
        /*
         * If a non-default network is enabled, add the host routes that
         * will allow it's DNS servers to be accessed.
         */
        handleDnsConfigurationChange(netType);

        if (mNetTrackers[netType].getNetworkInfo().isConnected()) {
            if (mNetAttributes[netType].isDefault()) {
                mNetTrackers[netType].addDefaultRoute();
                Intent intent = new Intent(Proxy.PROXY_CHANGE_ACTION);
                intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING
                        | Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                mContext.sendStickyBroadcast(intent);
            } else {
                // many radios add a default route even when we don't want one.
                // remove the default interface unless we need it for our active network
                if (mActiveDefaultNetwork != -1) {
                    String defaultIface = mNetTrackers[mActiveDefaultNetwork].getInterfaceName();
                    if (defaultIface != null &&
                            !defaultIface.equals(mNetTrackers[netType].getInterfaceName())) {
                        mNetTrackers[netType].removeDefaultRoute();
                    }
                }
                //modify for <Bug#130570> DM in dual sim mode start
                if(netType != ConnectivityManager.TYPE_MOBILE_DM && netType != (ConnectivityManager.TYPE_MOBILE_MMS_EXTERNAL + 1) && netType != (ConnectivityManager.TYPE_MOBILE_MMS_EXTERNAL + 2)){	// mod for DM
                //modify for <Bug#130570> DM in dual sim mode end
                    mNetTrackers[netType].addPrivateDnsRoutes();
                }
                
            }
        } else {
            if (mNetAttributes[netType].isDefault()) {
                mNetTrackers[netType].removeDefaultRoute();
            } else {
                mNetTrackers[netType].removePrivateDnsRoutes();
            }
        }
    }

    /**
     * Adjust the per-process dns entries (net.dns<x>.<pid>) based
     * on the highest priority active net which this process requested.
     * If there aren't any, clear it out
     */
    private void reassessPidDns(int myPid, boolean doBump)
    {
        if (DBG) Slog.d(TAG, "reassessPidDns for pid " + myPid);
        for(int i : mPriorityList) {
            if (mNetAttributes[i].isDefault()) {
                continue;
            }
            NetworkStateTracker nt = mNetTrackers[i];
            if (nt.getNetworkInfo().isConnected() &&
                    !nt.isTeardownRequested()) {
                List pids = mNetRequestersPids[i];
                for (int j=0; j<pids.size(); j++) {
                    Integer pid = (Integer)pids.get(j);
                    if (pid.intValue() == myPid) {
                        String[] dnsList = nt.getNameServers();
                        writePidDns(dnsList, myPid);
                        if (doBump) {
                            bumpDns();
                        }
                        return;
                    }
                }
           }
        }
        // nothing found - delete
        for (int i = 1; ; i++) {
            String prop = "net.dns" + i + "." + myPid;
            if (SystemProperties.get(prop).length() == 0) {
                if (doBump) {
                    bumpDns();
                }
                return;
            }
            SystemProperties.set(prop, "");
        }
    }
    private static int lookupHost(String hostname) {
        InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByName(hostname);
        } catch (Exception e) {
            return -1;
        }
        byte[] addrBytes;
        int addr;
        addrBytes = inetAddress.getAddress();
        addr = ((addrBytes[3] & 0xff) << 24)
                | ((addrBytes[2] & 0xff) << 16)
                | ((addrBytes[1] & 0xff) << 8)
                |  (addrBytes[0] & 0xff);
        return addr;
    }
    //modify for <Bug#130570> DM in dual sim mode start 
    private boolean writePidDns(String[] dnsList, int pid) {
        int j = 1;
        boolean changed = false;
        for (String dns : dnsList) {
            if (dns != null && !TextUtils.equals(dns, "0.0.0.0")) {
                SystemProperties.set("net.dns" + j++ + "." + pid, dns);
            changed = true;
            }
        }
        return changed;
    }
    //modify for <Bug#130570> DM in dual sim mode end
    //modify for <Bug#130570> DM in dual sim mode start    
  private boolean writePidDnsDM(String[] dnsList, int pid, int netType) {
        int j = 1;
        boolean changed = false;
        for (String dns : dnsList) {
            if (dns != null && !TextUtils.equals(dns, "0.0.0.0")) {
               SystemProperties.set("net.dns" + j++ + "." + pid, dns);

				int  inetAddr = lookupHost(dns);
                
 				//requestRouteToHost(ConnectivityManager.TYPE_MOBILE_DM,inetAddr);
                requestRouteToHost(netType,inetAddr);

  
             }
         }
        return changed;
     }
    //modify for <Bug#130570> DM in dual sim mode end
    private void bumpDns() {
        /*
         * Bump the property that tells the name resolver library to reread
         * the DNS server list from the properties.
         */
        String propVal = SystemProperties.get("net.dnschange");
        int n = 0;
        if (propVal.length() != 0) {
            try {
                n = Integer.parseInt(propVal);
            } catch (NumberFormatException e) {}
        }
        SystemProperties.set("net.dnschange", "" + (n+1));
        /*
         * Tell the VMs to toss their DNS caches
         */
        Intent intent = new Intent(Intent.ACTION_CLEAR_DNS_CACHE);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        /*
         * Connectivity events can happen before boot has completed ...
         */
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mContext.sendBroadcast(intent);
    }

    private void handleDnsConfigurationChange(int netType) {
        // add default net's dns entries
        NetworkStateTracker nt = mNetTrackers[netType];
        //modify for <Bug#130570> DM in dual sim mode start 
        boolean changed = false;
        //modify for <Bug#130570> DM in dual sim mode start 
        if (nt != null && nt.getNetworkInfo().isConnected() && !nt.isTeardownRequested()) {
            //String[] dnsList = nt.getNameServers();
            String[] dnsList;
        //modify for <Bug#130570> DM in dual sim mode start
	    if(netType == ConnectivityManager.TYPE_MOBILE_DM || (netType > ConnectivityManager.TYPE_MOBILE_MMS_EXTERNAL && netType <= ConnectivityManager.MAX_NETWORK_TYPE))
        //modify for <Bug#130570> DM in dual sim mode end
		dnsList=nt.getNameServersDM();
            else
		dnsList=nt.getNameServers();

	    if (mNetAttributes[netType].isDefault()) {
                int j = 1;
                for (String dns : dnsList) {
                    if (dns != null && !TextUtils.equals(dns, "0.0.0.0")) {
                        if (DBG) {
                            Slog.d(TAG, "adding dns " + dns + " for " +
                                    nt.getNetworkInfo().getTypeName());
                        }
                        String value = SystemProperties.get("net.gprs.http-proxy");
                        if (DBG)
                            Slog.d(TAG, "get net.gprs.http-proxy value=" + value);
                        SystemProperties.set("net.dns" + j++, dns);
                    }
                }
                for (int k=j ; k<mNumDnsEntries; k++) {
                    if (DBG) Slog.d(TAG, "erasing net.dns" + k);
                    SystemProperties.set("net.dns" + k, "");
                }
                mNumDnsEntries = j;
                changed = true;
            } else {
                // set per-pid dns for attached secondary nets
                List pids = mNetRequestersPids[netType];
                for (int y=0; y< pids.size(); y++) {
                    Integer pid = (Integer)pids.get(y);
                   // writePidDns(dnsList, pid.intValue());
          //modify for <Bug#130570> DM in dual sim mode start
		    if(netType == ConnectivityManager.TYPE_MOBILE_DM || (netType > ConnectivityManager.TYPE_MOBILE_MMS_EXTERNAL && netType <= ConnectivityManager.MAX_NETWORK_TYPE))	//mod for DM

                changed = writePidDnsDM(dnsList, pid.intValue(), netType);
		    else
			    changed = writePidDns(dnsList, pid.intValue());
                }
            }
        }
          if(changed) bumpDns();
          //modify for <Bug#130570> DM in dual sim mode end
    }

    private int getRestoreDefaultNetworkDelay() {
        String restoreDefaultNetworkDelayStr = SystemProperties.get(
                NETWORK_RESTORE_DELAY_PROP_NAME);
        if(restoreDefaultNetworkDelayStr != null &&
                restoreDefaultNetworkDelayStr.length() != 0) {
            try {
                return Integer.valueOf(restoreDefaultNetworkDelayStr);
            } catch (NumberFormatException e) {
            }
        }
        return RESTORE_DEFAULT_NETWORK_DELAY;
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump ConnectivityService " +
                    "from from pid=" + Binder.getCallingPid() + ", uid=" +
                    Binder.getCallingUid());
            return;
        }
        pw.println();
        for (NetworkStateTracker nst : mNetTrackers) {
            if (nst != null) {
                if (nst.getNetworkInfo().isConnected()) {
                    pw.println("Active network: " + nst.getNetworkInfo().
                            getTypeName());
                }
                pw.println(nst.getNetworkInfo());
                pw.println(nst);
                pw.println();
            }
        }

        pw.println("Network Requester Pids:");
        for (int net : mPriorityList) {
            String pidString = net + ": ";
            for (Object pid : mNetRequestersPids[net]) {
                pidString = pidString + pid.toString() + ", ";
            }
            pw.println(pidString);
        }
        pw.println();

        pw.println("FeatureUsers:");
        for (Object requester : mFeatureUsers) {
            pw.println(requester.toString());
        }
        pw.println();

        mTethering.dump(fd, pw, args);

        if (mInetLog != null) {
            pw.println();
            pw.println("Inet condition reports:");
            for(int i = 0; i < mInetLog.size(); i++) {
                pw.println(mInetLog.get(i));
            }
        }
    }

    // must be stateless - things change under us.
    private class MyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            NetworkInfo info;
            switch (msg.what) {
                case NetworkStateTracker.EVENT_STATE_CHANGED:
                    info = (NetworkInfo) msg.obj;
                    int type = info.getType();
                    NetworkInfo.State state = info.getState();
                    // only do this optimization for wifi.  It going into scan mode for location
                    // services generates alot of noise.  Meanwhile the mms apn won't send out
                    // subsequent notifications when on default cellular because it never
                    // disconnects..  so only do this to wifi notifications.  Fixed better when the
                    // APN notifications are standardized.
                    if (mNetAttributes[type].mLastState == state &&
                            mNetAttributes[type].mRadio == ConnectivityManager.TYPE_WIFI) {
                        if (DBG) {
                            // TODO - remove this after we validate the dropping doesn't break
                            // anything
                            Slog.d(TAG, "Dropping ConnectivityChange for " +
                                    info.getTypeName() + ": " +
                                    state + "/" + info.getDetailedState());
                        }
                        return;
                    }
                    mNetAttributes[type].mLastState = state;

                    if (DBG) Slog.d(TAG, "ConnectivityChange for " +
                            info.getTypeName() + ": " +
                            state + "/" + info.getDetailedState());

                    // Connectivity state changed:
                    // [31-13] Reserved for future use
                    // [12-9] Network subtype (for mobile network, as defined
                    //         by TelephonyManager)
                    // [8-3] Detailed state ordinal (as defined by
                    //         NetworkInfo.DetailedState)
                    // [2-0] Network type (as defined by ConnectivityManager)
                    int eventLogParam = (info.getType() & 0x7) |
                            ((info.getDetailedState().ordinal() & 0x3f) << 3) |
                            (info.getSubtype() << 9);
                    EventLog.writeEvent(EventLogTags.CONNECTIVITY_STATE_CHANGED,
                            eventLogParam);

                    if (info.getDetailedState() ==
                            NetworkInfo.DetailedState.FAILED) {
                        handleConnectionFailure(info);
                    } else if (state == NetworkInfo.State.DISCONNECTED) {
                        handleDisconnect(info);
                    } else if (state == NetworkInfo.State.SUSPENDED) {
                        // TODO: need to think this over.
                        // the logic here is, handle SUSPENDED the same as
                        // DISCONNECTED. The only difference being we are
                        // broadcasting an intent with NetworkInfo that's
                        // suspended. This allows the applications an
                        // opportunity to handle DISCONNECTED and SUSPENDED
                        // differently, or not.
                        handleDisconnect(info);
                    } else if (state == NetworkInfo.State.CONNECTED) {
                        handleConnect(info);
                    }
                    break;

                case NetworkStateTracker.EVENT_SCAN_RESULTS_AVAILABLE:
                    info = (NetworkInfo) msg.obj;
                    handleScanResultsAvailable(info);
                    break;

                case NetworkStateTracker.EVENT_NOTIFICATION_CHANGED:
                    handleNotificationChange(msg.arg1 == 1, msg.arg2,
                            (Notification) msg.obj);
                    break;

                case NetworkStateTracker.EVENT_CONFIGURATION_CHANGED:
                    info = (NetworkInfo) msg.obj;
                    type = info.getType();
                    handleDnsConfigurationChange(type);
                    break;

                case NetworkStateTracker.EVENT_ROAMING_CHANGED:
                    // fill me in
                    break;

                case NetworkStateTracker.EVENT_NETWORK_SUBTYPE_CHANGED:
                    // fill me in
                    break;
                case EVENT_RESTORE_DEFAULT_NETWORK:
                    FeatureUser u = (FeatureUser)msg.obj;
                    u.expire();
                    break;
                case EVENT_INET_CONDITION_CHANGE:
                {
                    int netType = msg.arg1;
                    int condition = msg.arg2;
                    handleInetConditionChange(netType, condition);
                    break;
                }
                case EVENT_INET_CONDITION_HOLD_END:
                {
                    int netType = msg.arg1;
                    int sequence = msg.arg2;
                    handleInetConditionHoldEnd(netType, sequence);
                    break;
                }
                case EVENT_SET_NETWORK_PREFERENCE:
                {
                    int preference = msg.arg1;
                    handleSetNetworkPreference(preference);
                    break;
                }
                case EVENT_SET_BACKGROUND_DATA:
                {
                    boolean enabled = (msg.arg1 == ENABLED);
                    handleSetBackgroundData(enabled);
                    break;
                }
                case EVENT_SET_MOBILE_DATA:
                {
                    boolean enabled = (msg.arg1 == ENABLED);
                    handleSetMobileData(enabled);
                    break;
                }
            }
        }
    }

    // javadoc from interface
    public int tether(String iface) {
        enforceTetherChangePermission();

        if (isTetheringSupported()) {
            return mTethering.tether(iface);
        } else {
            return ConnectivityManager.TETHER_ERROR_UNSUPPORTED;
        }
    }

    // javadoc from interface
    public int untether(String iface) {
        enforceTetherChangePermission();

        if (isTetheringSupported()) {
            return mTethering.untether(iface);
        } else {
            return ConnectivityManager.TETHER_ERROR_UNSUPPORTED;
        }
    }

    //add by liguxiang 08-28-11 for spreadtrum usb settings <udcpower && gser && vser> begin
    public boolean enableUdcpower(boolean enabled){
    	return mTethering.enableUsbUdcpower(enabled);
    }

    public boolean isUsbUdcpowerStarted(){
    	return mTethering.isUsbUdcpowerStarted();
    }

    public boolean enableGser(boolean enabled){
    	return mTethering.enableUsbGser(enabled);
    }

    public boolean isUsbGserStarted(){
    	return mTethering.isUsbGserStarted();
    }

    public boolean enableVser(boolean enabled){
    	return mTethering.enableUsbVser(enabled);
    }

    public boolean isUsbVserStarted(){
    	return mTethering.isUsbVserStarted();
    }

    public boolean isUsbRNDISStarted(){
    	return mTethering.isUsbRNDISStarted();
    }

    public boolean isUsbConnected(){
    	return mTethering.isUsbConnected();
    }
    //add by liguxiang 08-28-11 for spreadtrum usb settings <udcpower && gser && vser> end

    // javadoc from interface
    public int getLastTetherError(String iface) {
        enforceTetherAccessPermission();

        if (isTetheringSupported()) {
            return mTethering.getLastTetherError(iface);
        } else {
            return ConnectivityManager.TETHER_ERROR_UNSUPPORTED;
        }
    }

    // TODO - proper iface API for selection by property, inspection, etc
    public String[] getTetherableUsbRegexs() {
        enforceTetherAccessPermission();
        if (isTetheringSupported()) {
            return mTethering.getTetherableUsbRegexs();
        } else {
            return new String[0];
        }
    }

    public String[] getTetherableWifiRegexs() {
        enforceTetherAccessPermission();
        if (isTetheringSupported()) {
            return mTethering.getTetherableWifiRegexs();
        } else {
            return new String[0];
        }
    }

    // TODO - move iface listing, queries, etc to new module
    // javadoc from interface
    public String[] getTetherableIfaces() {
        enforceTetherAccessPermission();
        return mTethering.getTetherableIfaces();
    }

    public String[] getTetheredIfaces() {
        enforceTetherAccessPermission();
        return mTethering.getTetheredIfaces();
    }

    public String[] getTetheringErroredIfaces() {
        enforceTetherAccessPermission();
        return mTethering.getErroredIfaces();
    }

    // if ro.tether.denied = true we default to no tethering
    // gservices could set the secure setting to 1 though to enable it on a build where it
    // had previously been turned off.
    public boolean isTetheringSupported() {
        enforceTetherAccessPermission();
        int defaultVal = (SystemProperties.get("ro.tether.denied").equals("true") ? 0 : 1);
        boolean tetherEnabledInSettings = (Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.TETHER_SUPPORTED, defaultVal) != 0);
        return tetherEnabledInSettings && mTetheringConfigValid;
    }


    private boolean isMmsFeature(String feature) {
        return (TextUtils.equals(feature, Phone.FEATURE_ENABLE_MMS))
                || (TextUtils.equals(feature, Phone.FEATURE_ENABLE_MMS +"0"))
                || (TextUtils.equals(feature, Phone.FEATURE_ENABLE_MMS +"1"));
    }

    private boolean isMmsType(int netType) {
        //modify for <Bug#130570> DM in dual sim mode start
        if (netType == ConnectivityManager.TYPE_MOBILE_MMS || (netType > ConnectivityManager.TYPE_MOBILE_DM && netType <= ConnectivityManager.TYPE_MOBILE_MMS_EXTERNAL)) {
        //modify for <Bug#130570> DM in dual sim mode end
            return true;
        }
        return false;
    }

    private int getPhoneIdByFeature(String feature, boolean bRealPhone) {
        int phoneId;
        if (isMmsFeature(feature)) {
            if (TextUtils.equals(feature, Phone.FEATURE_ENABLE_MMS)) {
                if(bRealPhone || PhoneFactory.getPhoneCount() == 1) {
                    phoneId = PhoneFactory.getDefaultPhoneId();
                } else {
                    phoneId = PhoneFactory.getPhoneCount();
                }
            } else if (feature.length() > Phone.FEATURE_ENABLE_MMS.length()){
                phoneId = Integer.parseInt(feature.substring(Phone.FEATURE_ENABLE_MMS.length()));
            } else {
                throw new IllegalArgumentException("Illeagal Feature: " + feature);
            }
        } 
        //modify for <Bug#130570> DM in dual sim mode start
        else if(isDmFeature(feature)) {
            if (TextUtils.equals(feature, Phone.FEATURE_ENABLE_DM)) {
                phoneId = PhoneFactory.getDefaultPhoneId();
            } else {
                phoneId = Integer.parseInt(feature.substring(Phone.FEATURE_ENABLE_DM.length()));
            }//for dual sim
        }
        //modify for <Bug#130570> DM in dual sim mode end
        else {
            if (TextUtils.equals(feature, Phone.FEATURE_ENABLE_WAP)) {
                return TelephonyManager.getDefaultDataPhoneId(mContext);
            }
            phoneId = PhoneFactory.getDefaultPhoneId();
        }
        return phoneId;
    }

    //modify for <Bug#130570> DM in dual sim mode start
    private boolean isDmFeature(String feature) {
        return (TextUtils.equals(feature, Phone.FEATURE_ENABLE_DM))
                || (TextUtils.equals(feature, Phone.FEATURE_ENABLE_DM +"0"))
                || (TextUtils.equals(feature, Phone.FEATURE_ENABLE_DM +"1"));
    }
    //modify for <Bug#130570> DM in dual sim mode end

    private int getPhoneIdByNetworkType(int netType) {
        if (netType <= ConnectivityManager.TYPE_MOBILE_DM) {
            return TelephonyManager.getDefaultDataPhoneId(mContext);
        //modify for <Bug#130570> DM in dual sim mode start
        } else if (netType <= ConnectivityManager.TYPE_MOBILE_MMS_EXTERNAL) {
            return netType - ConnectivityManager.TYPE_MOBILE_DM - 1;
        } else {
            return netType - ConnectivityManager.TYPE_MOBILE_MMS_EXTERNAL - 1;
        //modify for <Bug#130570> DM in dual sim mode end
        }
    }

    /**
     * @see ConnectivityManager#getMobileDataEnabledByPhoneId(int)
     */
    public boolean getMobileDataEnabledByPhoneId(int phoneId) {
        enforceAccessPermission();
        boolean retVal = Settings.Secure.getIntAtIndex(mContext.getContentResolver(),
                Settings.Secure.MOBILE_DATA, phoneId, 1) == 1;
        if (DBG) Slog.d(TAG, "getMobileDataEnabled[" + phoneId + "] returning " + retVal);
        return retVal;
    }

    /**
     * @see ConnectivityManager#setMobileDataEnabledByPhoneId(int, boolean)
     */
    public synchronized void setMobileDataEnabledByPhoneId(int phoneId, boolean enabled) {
        enforceChangePermission();
        if (DBG) Slog.d(TAG, "setMobileDataEnabled(" + phoneId + "," + enabled + ")");

        if (getMobileDataEnabledByPhoneId(phoneId) == enabled) return;

        Settings.Secure.putIntAtIndex(mContext.getContentResolver(),
                Settings.Secure.MOBILE_DATA, phoneId, enabled ? 1 : 0);

        int defaultDataPhoneId = TelephonyManager.getDefaultDataPhoneId(mContext);
        if (enabled) {
            if (phoneId == defaultDataPhoneId) {
                if (mNetTrackers[ConnectivityManager.TYPE_MOBILE] != null) {
                    if (DBG) Slog.d(TAG, "starting up " + mNetTrackers[ConnectivityManager.TYPE_MOBILE]);
                    mNetTrackers[ConnectivityManager.TYPE_MOBILE].reconnect();
                }
            }
        } else {
            for (NetworkStateTracker nt : mNetTrackers) {
                if (nt == null) continue;
                int netType = nt.getNetworkInfo().getType();
                if (getPhoneIdByNetworkType(netType) != phoneId) continue;
                if (mNetAttributes[netType].mRadio == ConnectivityManager.TYPE_MOBILE  && !isMmsType(netType)) {
                    if (DBG) Slog.d(TAG, "tearing down " + nt);
                    nt.teardown();
                }
            }
        }
    }


    // 100 percent is full good, 0 is full bad.
    public void reportInetCondition(int networkType, int percentage) {
        if (DBG) Slog.d(TAG, "reportNetworkCondition(" + networkType + ", " + percentage + ")");
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.STATUS_BAR,
                "ConnectivityService");

        if (DBG) {
            int pid = getCallingPid();
            int uid = getCallingUid();
            String s = pid + "(" + uid + ") reports inet is " +
                (percentage > 50 ? "connected" : "disconnected") + " (" + percentage + ") on " +
                "network Type " + networkType + " at " + GregorianCalendar.getInstance().getTime();
            mInetLog.add(s);
            while(mInetLog.size() > INET_CONDITION_LOG_MAX_SIZE) {
                mInetLog.remove(0);
            }
        }
        mHandler.sendMessage(mHandler.obtainMessage(
            EVENT_INET_CONDITION_CHANGE, networkType, percentage));
    }

    private void handleInetConditionChange(int netType, int condition) {
        if (DBG) {
            Slog.d(TAG, "Inet connectivity change, net=" +
                    netType + ", condition=" + condition +
                    ",mActiveDefaultNetwork=" + mActiveDefaultNetwork);
        }
        if (mActiveDefaultNetwork == -1) {
            if (DBG) Slog.d(TAG, "no active default network - aborting");
            return;
        }
        if (mActiveDefaultNetwork != netType) {
            if (DBG) Slog.d(TAG, "given net not default - aborting");
            return;
        }
        mDefaultInetCondition = condition;
        int delay;
        if (mInetConditionChangeInFlight == false) {
            if (DBG) Slog.d(TAG, "starting a change hold");
            // setup a new hold to debounce this
            if (mDefaultInetCondition > 50) {
                delay = Settings.Secure.getInt(mContext.getContentResolver(),
                        Settings.Secure.INET_CONDITION_DEBOUNCE_UP_DELAY, 500);
            } else {
                delay = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.INET_CONDITION_DEBOUNCE_DOWN_DELAY, 3000);
            }
            mInetConditionChangeInFlight = true;
            mHandler.sendMessageDelayed(mHandler.obtainMessage(EVENT_INET_CONDITION_HOLD_END,
                    mActiveDefaultNetwork, mDefaultConnectionSequence), delay);
        } else {
            // we've set the new condition, when this hold ends that will get
            // picked up
            if (DBG) Slog.d(TAG, "currently in hold - not setting new end evt");
        }
    }

    private void handleInetConditionHoldEnd(int netType, int sequence) {
        if (DBG) {
            Slog.d(TAG, "Inet hold end, net=" + netType +
                    ", condition =" + mDefaultInetCondition +
                    ", published condition =" + mDefaultInetConditionPublished);
        }
        mInetConditionChangeInFlight = false;

        if (mActiveDefaultNetwork == -1) {
            if (DBG) Slog.d(TAG, "no active default network - aborting");
            return;
        }
        if (mDefaultConnectionSequence != sequence) {
            if (DBG) Slog.d(TAG, "event hold for obsolete network - aborting");
            return;
        }
        if (mDefaultInetConditionPublished == mDefaultInetCondition) {
            if (DBG) Slog.d(TAG, "no change in condition - aborting");
            return;
        }
        NetworkInfo networkInfo = mNetTrackers[mActiveDefaultNetwork].getNetworkInfo();
        if (networkInfo.isConnected() == false) {
            if (DBG) Slog.d(TAG, "default network not connected - aborting");
            return;
        }
        mDefaultInetConditionPublished = mDefaultInetCondition;
        sendInetConditionBroadcast(networkInfo);
        return;
    }
    private ContentObserver mDefaultDataPhoneIdObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            Slog.d(TAG,"Default Data Phone Id is changed  ---------");
            int defaultDataPhoneId = TelephonyManager.getDefaultDataPhoneId(mContext);
            if (mNetTrackers[ConnectivityManager.TYPE_MOBILE] != null) {
            if(mNetTrackers[ConnectivityManager.TYPE_MOBILE].isTeardownRequested()){
                Slog.w(TAG, "mTeardownRequested is true, do nothing " );
            }else{
                if(getMobileDataEnabledByPhoneId(defaultDataPhoneId)){
                    if (DBG) Slog.d(TAG, "setDataEnable(true) " );
                        mNetTrackers[ConnectivityManager.TYPE_MOBILE].setDataEnable(true);
                    }else{
                        if (DBG) Slog.d(TAG, "setDataEnable(false) " );
                            mNetTrackers[ConnectivityManager.TYPE_MOBILE].setDataEnable(false);
                    }
                }
            }
        }
    };
}
