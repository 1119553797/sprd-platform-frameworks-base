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

package android.net;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.RemoteException;
import android.os.Handler;
import android.os.ServiceManager;
import android.os.SystemProperties;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import android.net.NetworkInfo.DetailedState;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import com.android.internal.telephony.gsm.MsmsGsmDataConnectionTrackerProxy;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.text.TextUtils;

/**
 * Track the state of mobile data connectivity. This is done by
 * receiving broadcast intents from the Phone process whenever
 * the state of data connectivity changes.
 *
 * {@hide}
 */
public class MobileDataStateTracker extends NetworkStateTracker {

    private static final String TAG = "MobileDataStateTracker";
    private static final boolean DBG = true;

    private Phone.DataState mMobileDataState;
    private ITelephony mPhoneService;

    private String mApnType;
    private String mApnTypeToWatchFor;
    private String mApnName;
    private boolean mEnabled;
    private BroadcastReceiver mStateReceiver;
    private int mNetType;
    // DEFAULT and HIPRI are the same connection.  If we're one of these we need to check if
    // the other is also disconnected before we reset sockets
    private boolean mIsDefaultOrHipri = false;
    private static final String MSG_FROM_SELF = "msgfromself";

    private PhoneStateListener mPhoneStateListener;
    /**
     * Create a new MobileDataStateTracker
     * @param context the application context of the caller
     * @param target a message handler for getting callbacks about state changes
     * @param netType the ConnectivityManager network type
     * @param apnType the Phone apnType
     * @param tag the name of this network
     */
    public MobileDataStateTracker(Context context, Handler target, int netType, String tag) {
        super(context, target, netType,
                TelephonyManager.getDefault().getNetworkType(), tag,
                TelephonyManager.getDefault().getNetworkTypeName());
        mNetType = netType;
        mApnType = networkTypeToApnType(netType);
        //if (TextUtils.equals(mApnType, Phone.APN_TYPE_HIPRI)) {
        //    mApnTypeToWatchFor = Phone.APN_TYPE_DEFAULT;
        //} else {
            mApnTypeToWatchFor = mApnType;
        //}
        if (netType == ConnectivityManager.TYPE_MOBILE ||
                netType == ConnectivityManager.TYPE_MOBILE_HIPRI) {
            mIsDefaultOrHipri = true;
        }

        mPhoneService = null;
        if(netType == ConnectivityManager.TYPE_MOBILE) {
            mEnabled = true;
        } else {
            mEnabled = false;
        }

        mDnsPropNames = new String[] {
                "net.rmnet0.dns1",
                "net.rmnet0.dns2",
                "net.eth0.dns1",
                "net.eth0.dns2",
                "net.eth0.dns3",
                "net.eth0.dns4",
                "net.gprs.dns1",
                "net.gprs.dns2",
                "net.veth0.dns1",
                "net.veth0.dns2",
                "net.ppp0.dns1",
                "net.ppp0.dns2"};

    }

    private PhoneStateListener getPhoneStateListener(int subscription) {
        PhoneStateListener phoneStateListener = new PhoneStateListener(subscription) {
            public void onServiceStateChanged(ServiceState state) {
                boolean bSet = false;
                boolean available = true;
                int mmsPhoneId = networkTypeToMMSPhoneId(mNetworkInfo.getType());

                Log.d(TAG, "ListenServiceState: available:"+mNetworkInfo.isAvailable()+ " newState:"+
                        state.getState()+" subscription:" + mSubscription+" mmsPhoneId="+mmsPhoneId);
                if((mmsPhoneId >= 0) && (mSubscription == mmsPhoneId)) {
                    boolean bActivate = MsmsGsmDataConnectionTrackerProxy.isActiveOrDefaultPhoneId(mmsPhoneId);

                    if(bActivate) {
                        return;
                    }
                    if((mNetworkInfo.isAvailable() && (state.getState() != ServiceState.STATE_IN_SERVICE)) ||
                       (!mNetworkInfo.isAvailable() && (state.getState() == ServiceState.STATE_IN_SERVICE))){
                        bSet = true;
                    }
                    if(bSet) {
                        if(state.getState() == ServiceState.STATE_IN_SERVICE) {
                            available = true;
                        } else {
                            available = false;
                        }
                        Log.d(TAG, "Listen [type=" + mNetType + "]setIsAvailable="+available);
                        mNetworkInfo.setIsAvailable(available);
                    }
                }
            }
        };
        return phoneStateListener;
    }
    /**
     * Begin monitoring mobile data connectivity.
     */
    public void startMonitoring() {
        IntentFilter filter =
                new IntentFilter(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_DATA_CONNECTION_FAILED);
        filter.addAction(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);

        mStateReceiver = new MobileDataStateReceiver();
        Intent intent = mContext.registerReceiver(mStateReceiver, filter);
        if (intent != null)
            mMobileDataState = getMobileDataState(intent);
        else
            mMobileDataState = Phone.DataState.DISCONNECTED;

        int mmsPhoneId = networkTypeToMMSPhoneId(mNetType);
        if(mmsPhoneId >=0) {
            mPhoneStateListener = getPhoneStateListener(mmsPhoneId);
            // register for phone state notifications.
            ((TelephonyManager) mContext
                    .getSystemService(PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE,mmsPhoneId))).listen(
                    mPhoneStateListener,
                    PhoneStateListener.LISTEN_SERVICE_STATE);
        }
    }

    private Phone.DataState getMobileDataState(Intent intent) {
        String str = intent.getStringExtra(Phone.STATE_KEY);
        if (str != null) {
            /*String apnTypeList =
                    intent.getStringExtra(Phone.DATA_APN_TYPES_KEY);
            if (isApnTypeIncluded(apnTypeList)) {
                return Enum.valueOf(Phone.DataState.class, str);
            }*/
            return Enum.valueOf(Phone.DataState.class, str);
        }
        return Phone.DataState.DISCONNECTED;
    }

    private boolean isApnTypeIncluded(String typeList) {
        /* comma seperated list - split and check */
        if (typeList == null)
            return false;

        String[] list = typeList.split(",");
        for(int i=0; i< list.length; i++) {
            if (TextUtils.equals(list[i], mApnTypeToWatchFor) ||
                TextUtils.equals(list[i], Phone.APN_TYPE_ALL)) {
                return true;
            }
        }
        return false;
    }

    private class MobileDataStateReceiver extends BroadcastReceiver {
        ConnectivityManager mConnectivityManager;
        public void onReceive(Context context, Intent intent) {
            synchronized(this) {
                // update state and roaming before we set the state - only state changes are
                // noticed
                TelephonyManager tm = TelephonyManager.getDefault(getPhoneId());
                setRoamingStatus(tm.isNetworkRoaming());
                setSubtype(tm.getNetworkType(), tm.getNetworkTypeName());
                setSubId(getPhoneId());
                if (intent.getAction().equals(TelephonyIntents.
                        ACTION_ANY_DATA_CONNECTION_STATE_CHANGED)) {
                    int phoneId = intent.getIntExtra(Phone.PHONE_ID, -1);
                    Log.d(TAG, "["
                            + "]receive ACTION_ANY_DATA_CONNECTION_STATE_CHANGED with phoneid:"
                            + phoneId);
                    Phone.DataState state = getMobileDataState(intent);
                    String reason = intent.getStringExtra(Phone.STATE_CHANGE_REASON_KEY);
                    String apnName = intent.getStringExtra(Phone.DATA_APN_KEY);
                    String apnTypeList = intent.getStringExtra(Phone.DATA_APN_TYPES_KEY);
                    boolean bIsBroadcastMsg = !intent.getBooleanExtra(MSG_FROM_SELF, false);
                    mApnName = apnName;

                    if (state == Phone.DataState.CONNECTED && bIsBroadcastMsg) {
                        if (DBG) Log.d(TAG, "replacing old mInterfaceName (" +
                                mInterfaceName + ") with " +
                                intent.getStringExtra(Phone.DATA_IFACE_NAME_KEY) +
                                " for " + mApnType);
                        mInterfaceName = intent.getStringExtra(Phone.DATA_IFACE_NAME_KEY);
                    }

                    boolean unavailable = intent.getBooleanExtra(Phone.NETWORK_UNAVAILABLE_KEY,
                            false);
                    int mmsPhoneId = networkTypeToMMSPhoneId(mNetworkInfo.getType());
                    // set this regardless of the apnTypeList.  It's all the same radio/network
                    // underneath
                    if(mmsPhoneId < 0) {
                        Log.d(TAG, "[type=" + mNetType + "]setIsAvailable=" + !unavailable);
                        mNetworkInfo.setIsAvailable(!unavailable);
                    } else {
                        boolean bActivate = MsmsGsmDataConnectionTrackerProxy.isActiveOrDefaultPhoneId(mmsPhoneId);
                        Log.d(TAG, "[type=" + mNetType + "]setIsAvailable=" + !unavailable+" mmsPhoneId["+mmsPhoneId+"] activate is "+bActivate);
                        if(bActivate) {
                            mNetworkInfo.setIsAvailable(!unavailable);
                        }
                    }

                    if (isApnTypeIncluded(apnTypeList)) {
                        if (mEnabled == false) {
                            // if we're not enabled but the APN Type is supported by this connection
                            // we should record the interface name if one's provided.  If the user
                            // turns on this network we will need the interfacename but won't get
                            // a fresh connected message - TODO fix this when we get per-APN
                            // notifications
                            return;
                        } else {
                            if ((mmsPhoneId >= 0) && (phoneId >= 0)
                                    && (mmsPhoneId != phoneId)) {
                                return;
                            }
                        }
                    } else {
                        return;
                    }

                    if (DBG) Log.d(TAG, mApnType + " Received state= " + state + ", old= " +
                            mMobileDataState + ", reason= " +
                            (reason == null ? "(unspecified)" : reason) +
                            ", apnTypeList= " + apnTypeList);

                    if (mMobileDataState != state) {
                        mMobileDataState = state;
                        switch (state) {
                            case DISCONNECTED:
                                if(isTeardownRequested()) {
                                    mEnabled = false;
                                    setTeardownRequested(false);
                                }

                                setDetailedState(DetailedState.DISCONNECTED, reason, apnName);
                                boolean doReset = true;
                                if (mIsDefaultOrHipri == true) {
                                    // both default and hipri must go down before we reset
                                    int typeToCheck = (Phone.APN_TYPE_DEFAULT.equals(mApnType) ?
                                            ConnectivityManager.TYPE_MOBILE_HIPRI :
                                            ConnectivityManager.TYPE_MOBILE);
                                    if (mConnectivityManager == null) {
                                        mConnectivityManager =
                                                (ConnectivityManager)context.getSystemService(
                                                Context.CONNECTIVITY_SERVICE);
                                    }
                                    if (mConnectivityManager != null) {
                                        NetworkInfo info = mConnectivityManager.getNetworkInfo(
                                                    typeToCheck);
                                        if (info != null && info.isConnected() == true) {
                                            doReset = false;
                                        }
                                    }
                                }
                                if (doReset && mInterfaceName != null) {
                                    NetworkUtils.resetConnections(mInterfaceName);
                                }
                                // can't do this here - ConnectivityService needs it to clear stuff
                                // it's ok though - just leave it to be refreshed next time
                                // we connect.
                                //if (DBG) Log.d(TAG, "clearing mInterfaceName for "+ mApnType +
                                //        " as it DISCONNECTED");
                                //mInterfaceName = null;
                                //mDefaultGatewayAddr = 0;
                                break;
                            case CONNECTING:
                                setDetailedState(DetailedState.CONNECTING, reason, apnName);
                                break;
                            case SUSPENDED:
                                setDetailedState(DetailedState.SUSPENDED, reason, apnName);
                                break;
                            case CONNECTED:
                                if(bIsBroadcastMsg) {
                                    mInterfaceName = intent.getStringExtra(Phone.DATA_IFACE_NAME_KEY);
                                }
                                if (mInterfaceName == null) {
                                    Log.d(TAG, "CONNECTED event did not supply interface name.");
                                }
                                if(bIsBroadcastMsg) {
                                    mDefaultGatewayAddr = intent.getIntExtra(Phone.DATA_GATEWAY_KEY, 0);
                                }
                                if (mDefaultGatewayAddr == 0) {
                                    Log.d(TAG, "CONNECTED event did not supply a default gateway.");
                                }
                                setDetailedState(DetailedState.CONNECTED, reason, apnName);
                                break;
                        }
                    }
                } else if (intent.getAction().
                        equals(TelephonyIntents.ACTION_DATA_CONNECTION_FAILED)) {
                    mEnabled = false;
                    String reason = intent.getStringExtra(Phone.FAILURE_REASON_KEY);
                    String apnName = intent.getStringExtra(Phone.DATA_APN_KEY);
                    if (DBG) Log.d(TAG, "Received " + intent.getAction() + " broadcast" +
                            reason == null ? "" : "(" + reason + ")");
                    setDetailedState(DetailedState.FAILED, reason, apnName);
                }
            }
        }
    }

    private void getPhoneService(boolean forceRefresh) {
        //if ((mPhoneService == null) || forceRefresh) {
        mPhoneService = ITelephony.Stub.asInterface(ServiceManager.getService(PhoneFactory
                .getServiceName("phone", getPhoneId())));
        //}
    }

    /**
     * Report whether data connectivity is possible.
     */
    public boolean isAvailable() {
        getPhoneService(false);

        /*
         * If the phone process has crashed in the past, we'll get a
         * RemoteException and need to re-reference the service.
         */
        for (int retry = 0; retry < 2; retry++) {
            if (mPhoneService == null) break;

            try {
                return mPhoneService.isDataConnectivityPossible();
            } catch (RemoteException e) {
                // First-time failed, get the phone service again
                if (retry == 0) getPhoneService(true);
            }
        }

        return false;
    }

    /**
     * {@inheritDoc}
     * The mobile data network subtype indicates what generation network technology is in effect,
     * e.g., GPRS, EDGE, UMTS, etc.
     */
    public int getNetworkSubtype() {
        return TelephonyManager.getDefault().getNetworkType();
    }

    /**
     * Return the system properties name associated with the tcp buffer sizes
     * for this network.
     */
    public String getTcpBufferSizesPropName() {
        String networkTypeStr = "unknown";
        TelephonyManager tm = new TelephonyManager(mContext);
        //TODO We have to edit the parameter for getNetworkType regarding CDMA
        switch(tm.getNetworkType()) {
        case TelephonyManager.NETWORK_TYPE_GPRS:
            networkTypeStr = "gprs";
            break;
        case TelephonyManager.NETWORK_TYPE_EDGE:
            networkTypeStr = "edge";
            break;
        case TelephonyManager.NETWORK_TYPE_UMTS:
            networkTypeStr = "umts";
            break;
        case TelephonyManager.NETWORK_TYPE_HSDPA:
            networkTypeStr = "hsdpa";
            break;
        case TelephonyManager.NETWORK_TYPE_HSUPA:
            networkTypeStr = "hsupa";
            break;
        case TelephonyManager.NETWORK_TYPE_HSPA:
            networkTypeStr = "hspa";
            break;
        case TelephonyManager.NETWORK_TYPE_CDMA:
            networkTypeStr = "cdma";
            break;
        case TelephonyManager.NETWORK_TYPE_1xRTT:
            networkTypeStr = "1xrtt";
            break;
        case TelephonyManager.NETWORK_TYPE_EVDO_0:
            networkTypeStr = "evdo";
            break;
        case TelephonyManager.NETWORK_TYPE_EVDO_A:
            networkTypeStr = "evdo";
            break;
        case TelephonyManager.NETWORK_TYPE_EVDO_B:
            networkTypeStr = "evdo";
            break;
        }
        return "net.tcp.buffersize." + networkTypeStr;
    }

    /**
     * Tear down mobile data connectivity, i.e., disable the ability to create
     * mobile data connections.
     */
    @Override
    public boolean teardown() {
        // since we won't get a notification currently (TODO - per APN notifications)
        // we won't get a disconnect message until all APN's on the current connection's
        // APN list are disabled.  That means privateRoutes for DNS and such will remain on -
        // not a problem since that's all shared with whatever other APN is still on, but
        // ugly.
        setTeardownRequested(true);
        return (setEnableApn(mApnType, false) != Phone.APN_REQUEST_FAILED);
    }

    /**
     * Re-enable mobile data connectivity after a {@link #teardown()}.
     */
    public boolean reconnect() {
        setTeardownRequested(false);
        if (DBG) Log.d(TAG,  " reconnect :setEnableApn"+ mApnType );
        switch (setEnableApn(mApnType, true)) {
            case Phone.APN_ALREADY_ACTIVE:
                // TODO - remove this when we get per-apn notifications
                 if (DBG) Log.d(TAG,  " reconnect :setEnableApn return  APN_ALREADY_ACTIVE" );
                mEnabled = true;
                // need to set self to CONNECTING so the below message is handled.
                mMobileDataState = Phone.DataState.CONNECTING;
                setDetailedState(DetailedState.CONNECTING, Phone.REASON_APN_CHANGED, null);
                //send out a connected message
                Intent intent = new Intent(TelephonyIntents.
                        ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
                intent.putExtra(Phone.STATE_KEY, Phone.DataState.CONNECTED.toString());
                intent.putExtra(Phone.STATE_CHANGE_REASON_KEY, Phone.REASON_APN_CHANGED);
                intent.putExtra(Phone.DATA_APN_TYPES_KEY, mApnTypeToWatchFor);
                intent.putExtra(Phone.DATA_APN_KEY, mApnName);
                intent.putExtra(Phone.DATA_IFACE_NAME_KEY, mInterfaceName);
                intent.putExtra(Phone.NETWORK_UNAVAILABLE_KEY, false);
                intent.putExtra(Phone.DATA_GATEWAY_KEY, mDefaultGatewayAddr);
                intent.putExtra(MSG_FROM_SELF, true);
                if (mStateReceiver != null) mStateReceiver.onReceive(mContext, intent);
                break;
            case Phone.APN_REQUEST_STARTED:
                mEnabled = true;
                // no need to do anything - we're already due some status update intents
                if (DBG) Log.d(TAG,  " reconnect :setEnableApn return  APN_REQUEST_STARTED" );
                break;
            case Phone.APN_REQUEST_FAILED:
                if (DBG) Log.d(TAG,  " reconnect :setEnableApn return  APN_REQUEST_FAILED" );
                if (mPhoneService == null && mApnType.equals(Phone.APN_TYPE_DEFAULT)) {
                    // on startup we may try to talk to the phone before it's ready
                    // since the phone will come up enabled, go with that.
                    // TODO - this also comes up on telephony crash: if we think mobile data is
                    // off and the telephony stuff crashes and has to restart it will come up
                    // enabled (making a data connection).  We will then be out of sync.
                    // A possible solution is a broadcast when telephony restarts.
                    mEnabled = true;
                    return false;
                }
                // else fall through
            case Phone.APN_TYPE_NOT_AVAILABLE:
                 if (DBG) Log.d(TAG,  " reconnect :setEnableApn return  APN_TYPE_NOT_AVAILABLE" );
                // Default is always available, but may be off due to
                // AirplaneMode or E-Call or whatever..
                 if (!mApnType.equals(Phone.APN_TYPE_DEFAULT)) {
                    mEnabled = false;
                    return false;
                }
                break;
            default:
                Log.e(TAG, "Error in reconnect - unexpected response.");
                mEnabled = false;
                break;
        }
        return mEnabled;
    }

    /**
     * Turn on or off the mobile radio. No connectivity will be possible while the
     * radio is off. The operation is a no-op if the radio is already in the desired state.
     * @param turnOn {@code true} if the radio should be turned on, {@code false} if
     */
    public boolean setRadio(boolean turnOn) {
        getPhoneService(false);
        /*
         * If the phone process has crashed in the past, we'll get a
         * RemoteException and need to re-reference the service.
         */
        for (int retry = 0; retry < 2; retry++) {
            if (mPhoneService == null) {
                Log.w(TAG,
                    "Ignoring mobile radio request because could not acquire PhoneService");
                break;
            }

            try {
                return mPhoneService.setRadio(turnOn);
            } catch (RemoteException e) {
                if (retry == 0) getPhoneService(true);
            }
        }

        Log.w(TAG, "Could not set radio power to " + (turnOn ? "on" : "off"));
        return false;
    }

    /**
     * Tells the phone sub-system that the caller wants to
     * begin using the named feature. The only supported features at
     * this time are {@code Phone.FEATURE_ENABLE_MMS}, which allows an application
     * to specify that it wants to send and/or receive MMS data, and
     * {@code Phone.FEATURE_ENABLE_SUPL}, which is used for Assisted GPS.
     * @param feature the name of the feature to be used
     * @param callingPid the process ID of the process that is issuing this request
     * @param callingUid the user ID of the process that is issuing this request
     * @return an integer value representing the outcome of the request.
     * The interpretation of this value is feature-specific.
     * specific, except that the value {@code -1}
     * always indicates failure. For {@code Phone.FEATURE_ENABLE_MMS},
     * the other possible return values are
     * <ul>
     * <li>{@code Phone.APN_ALREADY_ACTIVE}</li>
     * <li>{@code Phone.APN_REQUEST_STARTED}</li>
     * <li>{@code Phone.APN_TYPE_NOT_AVAILABLE}</li>
     * <li>{@code Phone.APN_REQUEST_FAILED}</li>
     * </ul>
     */
    public int startUsingNetworkFeature(String feature, int callingPid, int callingUid) {
        return -1;
    }

    /**
     * Tells the phone sub-system that the caller is finished
     * using the named feature. The only supported feature at
     * this time is {@code Phone.FEATURE_ENABLE_MMS}, which allows an application
     * to specify that it wants to send and/or receive MMS data.
     * @param feature the name of the feature that is no longer needed
     * @param callingPid the process ID of the process that is issuing this request
     * @param callingUid the user ID of the process that is issuing this request
     * @return an integer value representing the outcome of the request.
     * The interpretation of this value is feature-specific, except that
     * the value {@code -1} always indicates failure.
     */
    public int stopUsingNetworkFeature(String feature, int callingPid, int callingUid) {
        return -1;
    }

    /**
     * Ensure that a network route exists to deliver traffic to the specified
     * host via the mobile data network.
     * @param hostAddress the IP address of the host to which the route is desired,
     * in network byte order.
     * @return {@code true} on success, {@code false} on failure
     */
    @Override
    public boolean requestRouteToHost(int hostAddress) {
        if (DBG) {
            Log.d(TAG, "Requested host route to " + Integer.toHexString(hostAddress) +
                    " for " + mApnType + "(" + mInterfaceName + ")");
        }
        if (mInterfaceName != null && hostAddress != -1) {
            return NetworkUtils.addHostRoute(mInterfaceName, hostAddress) == 0;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer("Mobile data state: ");

        sb.append(mMobileDataState);
        return sb.toString();
    }

   /**
     * Internal method supporting the ENABLE_MMS feature.
     * @param apnType the type of APN to be enabled or disabled (e.g., mms)
     * @param enable {@code true} to enable the specified APN type,
     * {@code false} to disable it.
     * @return an integer value representing the outcome of the request.
     */
    private int setEnableApn(String apnType, boolean enable) {
        getPhoneService(false);
        /*
         * If the phone process has crashed in the past, we'll get a
         * RemoteException and need to re-reference the service.
         */
        for (int retry = 0; retry < 2; retry++) {
            if (mPhoneService == null) {
                Log.w(TAG,
                    "Ignoring feature request because could not acquire PhoneService");
                break;
            }

            try {
                if (enable) {
                    return mPhoneService.enableApnType(apnType);
                } else {
                    return mPhoneService.disableApnType(apnType);
                }
            } catch (RemoteException e) {
                if (retry == 0) getPhoneService(true);
            }
        }

        Log.w(TAG, "Could not " + (enable ? "enable" : "disable")
                + " APN type \"" + apnType + "\"");
        return Phone.APN_REQUEST_FAILED;
    }

    public static String networkTypeToApnType(int netType) {
        switch(netType) {
            case ConnectivityManager.TYPE_MOBILE:
                return Phone.APN_TYPE_DEFAULT;  // TODO - use just one of these
            case ConnectivityManager.TYPE_MOBILE_MMS:
                return Phone.APN_TYPE_MMS;
            case ConnectivityManager.TYPE_MOBILE_SUPL:
                return Phone.APN_TYPE_SUPL;
            case ConnectivityManager.TYPE_MOBILE_DUN:
                return Phone.APN_TYPE_DUN;
            case ConnectivityManager.TYPE_MOBILE_HIPRI:
                return Phone.APN_TYPE_HIPRI;
            case ConnectivityManager.TYPE_MOBILE_DM:
                return Phone.APN_TYPE_DM;
            default:
            	// Msms MMS netType @zha
                if (ConnectivityManager.isNetworkTypeValid(netType)) {
                    return Phone.APN_TYPE_MMS;
            	} else {
                    Log.e(TAG, "Error mapping networkType " + netType + " to apnType.");
                    return null;
            	}
        }
    }
    private int getPhoneId() {
        int phoneId = PhoneFactory.getDefaultPhoneId();
        switch(mNetType) {
            case ConnectivityManager.TYPE_MOBILE:
            case ConnectivityManager.TYPE_MOBILE_MMS:
            case ConnectivityManager.TYPE_MOBILE_SUPL:
            case ConnectivityManager.TYPE_MOBILE_DUN:
            case ConnectivityManager.TYPE_MOBILE_HIPRI:
                phoneId = TelephonyManager.getDefaultDataPhoneId(mContext);
                break;
            default:
                for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
                    if (mNetType == ConnectivityManager.getMmsTypeByPhoneId(i)) {
                        phoneId = i;
                        break;
                    }
                }
                break;
        }
        return phoneId;
    }
    public void setDataEnable(boolean mEnabled){
        this.mEnabled = mEnabled;
    }
    private int networkTypeToMMSPhoneId(int netType) {
        if(netType == ConnectivityManager.TYPE_MOBILE_MMS) {
            return TelephonyManager.getDefaultDataPhoneId(mContext);
        } else if (netType > ConnectivityManager.TYPE_MOBILE_DM
                && netType <= ConnectivityManager.MAX_NETWORK_TYPE) {
            return netType - ConnectivityManager.TYPE_MOBILE_DM - 1;
        } else {
            return -1;
        }
    }
}
