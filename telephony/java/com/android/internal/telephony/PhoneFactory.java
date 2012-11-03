/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.internal.telephony;

import android.content.Context;
import android.content.Intent;
import android.net.LocalServerSocket;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.os.SystemProperties;

import com.android.internal.telephony.IccCard.State;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.cdma.CDMALTEPhone;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.gsm.SimPhoneBookInterfaceManager;
import com.android.internal.telephony.gsm.SimSmsInterfaceManager;
import com.android.internal.telephony.sip.SipPhone;
import com.android.internal.telephony.sip.SipPhoneFactory;
import com.android.internal.telephony.gsm.TDPhone;

/**
 * {@hide}
 */
public class PhoneFactory {
    static final String LOG_TAG = "PHONE";
    static final int SOCKET_OPEN_RETRY_MILLIS = 2 * 1000;
    static final int SOCKET_OPEN_MAX_RETRY = 3;

    //***** Class Variables

    static private Phone[] sProxyPhone = null;
    static private CommandsInterface[] sCommandsInterface = null;

    static private boolean sMadeDefaults = false;
    static private PhoneNotifier[] sPhoneNotifier;
    static private Looper sLooper;
    static private Context sContext;

    static final int preferredCdmaSubscription =
                         CdmaSubscriptionSourceManager.PREFERRED_CDMA_SUBSCRIPTION;

    // zhanglj add 2011-05-20
    public static final int DEFAULT_PHONE_COUNT = 1;
    public static final int DEFAULT_PHONE_ID = 0;
    public static final int DEFAULT_DUAL_SIM_INIT_PHONE_ID = -1;

    private static boolean isCard1ok = false;
    private static boolean isCard2ok = false;

    //***** Class Methods

    public static void makeDefaultPhones(Context context) {
        makeDefaultPhone(context);
    }

    /**
     * FIXME replace this with some other way of making these
     * instances
     */
    public static void makeDefaultPhone(Context context) {
        synchronized(Phone.class) {
            if (!sMadeDefaults) {
                sLooper = Looper.myLooper();
                sContext = context;

                if (sLooper == null) {
                    throw new RuntimeException(
                        "PhoneFactory.makeDefaultPhone must be called from Looper thread");
                }

                int retryCount = 0;
                for(;;) {
                    boolean hasException = false;
                    retryCount ++;

                    try {
                        // use UNIX domain socket to
                        // prevent subsequent initialization
                        new LocalServerSocket("com.android.internal.telephony");
                    } catch (java.io.IOException ex) {
                        hasException = true;
                    }

                    if ( !hasException ) {
                        break;
                    } else if (retryCount > SOCKET_OPEN_MAX_RETRY) {
                        throw new RuntimeException("PhoneFactory probably already running");
                    } else {
                        try {
                            Thread.sleep(SOCKET_OPEN_RETRY_MILLIS);
                        } catch (InterruptedException er) {
                        }
                    }
                }

                int phoneCount = getPhoneCount();
                sPhoneNotifier = new DefaultPhoneNotifier[phoneCount];
                for (int i = 0; i < phoneCount; i++) {
                    sPhoneNotifier[i] = new DefaultPhoneNotifier(i);
                }

                // Get preferred network mode
                int preferredNetworkMode = RILConstants.PREFERRED_NETWORK_MODE;
                if (BaseCommands.getLteOnCdmaModeStatic() == Phone.LTE_ON_CDMA_TRUE) {
                    preferredNetworkMode = Phone.NT_MODE_GLOBAL;
                }
                int networkMode = Settings.Secure.getInt(context.getContentResolver(),
                        Settings.Secure.PREFERRED_NETWORK_MODE, preferredNetworkMode);
                Log.i(LOG_TAG, "Network Mode set to " + Integer.toString(networkMode));

                // Get cdmaSubscription
                // TODO: Change when the ril will provides a way to know at runtime
                //       the configuration, bug 4202572. And the ril issues the
                //       RIL_UNSOL_CDMA_SUBSCRIPTION_SOURCE_CHANGED, bug 4295439.
                int cdmaSubscription;
                int lteOnCdma = BaseCommands.getLteOnCdmaModeStatic();
                switch (lteOnCdma) {
                    case Phone.LTE_ON_CDMA_FALSE:
                        cdmaSubscription = CdmaSubscriptionSourceManager.SUBSCRIPTION_FROM_NV;
                        Log.i(LOG_TAG, "lteOnCdma is 0 use SUBSCRIPTION_FROM_NV");
                        break;
                    case Phone.LTE_ON_CDMA_TRUE:
                        cdmaSubscription = CdmaSubscriptionSourceManager.SUBSCRIPTION_FROM_RUIM;
                        Log.i(LOG_TAG, "lteOnCdma is 1 use SUBSCRIPTION_FROM_RUIM");
                        break;
                    case Phone.LTE_ON_CDMA_UNKNOWN:
                    default:
                        //Get cdmaSubscription mode from Settings.System
                        cdmaSubscription = Settings.Secure.getInt(context.getContentResolver(),
                                Settings.Secure.PREFERRED_CDMA_SUBSCRIPTION,
                                preferredCdmaSubscription);
                        Log.i(LOG_TAG, "lteOnCdma not set, using PREFERRED_CDMA_SUBSCRIPTION");
                        break;
                }
                Log.i(LOG_TAG, "Cdma Subscription set to " + cdmaSubscription);

                //reads the system properties and makes commandsinterface

                int phoneType = getPhoneType(networkMode);
                sCommandsInterface = new SprdRIL[phoneCount];
                sProxyPhone = new SprdPhoneProxy[phoneCount];
                for(int i = 0; i < phoneCount;i++) {
                    sCommandsInterface[i] = new SprdRIL(context, networkMode, cdmaSubscription, i);
                    if (phoneType == Phone.PHONE_TYPE_GSM) {
                        sProxyPhone[i] = new SprdPhoneProxy(new TDPhone(context,
                                sCommandsInterface[i], sPhoneNotifier[i]));
                        Log.i(LOG_TAG, "Creating TDPhone");
                    } /*else if (phoneType == Phone.PHONE_TYPE_CDMA) {
                        switch (BaseCommands.getLteOnCdmaModeStatic()) {
                            case Phone.LTE_ON_CDMA_TRUE:
                                Log.i(LOG_TAG, "Creating CDMALTEPhone");
                                sProxyPhone = new PhoneProxy(new CDMALTEPhone(context,
                                    sCommandsInterface, sPhoneNotifier));
                                break;
                            case Phone.LTE_ON_CDMA_FALSE:
                            default:
                                Log.i(LOG_TAG, "Creating CDMAPhone");
                                sProxyPhone = new PhoneProxy(new CDMAPhone(context,
                                        sCommandsInterface, sPhoneNotifier));
                                break;
                        }
                    }*/
                }
                // zhanglj modify end
                IccSmsInterfaceManagerProxy[] mIccSmsInterfaceManagerProxy = new IccSmsInterfaceManagerProxy[phoneCount];
                IccPhoneBookInterfaceManagerProxy[] mIccPhoneBookInterfaceManagerProxy = new IccPhoneBookInterfaceManagerProxy[phoneCount];
                PhoneSubInfoProxy[] mPhoneSubInfoProxy = new PhoneSubInfoProxy[phoneCount];
                for (int i = 0; i < phoneCount; i++) {
                    mIccSmsInterfaceManagerProxy[i] = ((SprdPhoneProxy)sProxyPhone[i]).getIccSmsInterfaceManagerProxy();
                    mIccPhoneBookInterfaceManagerProxy[i] = ((SprdPhoneProxy)sProxyPhone[i]).getIccPhoneBookInterfaceManagerProxy();
                    mPhoneSubInfoProxy[i] = ((SprdPhoneProxy)sProxyPhone[i]).getPhoneSubInfoProxy();
                }
                new CompositeIccSmsInterfaceManagerProxy(mIccSmsInterfaceManagerProxy);
                //new CompositeIccPhoneBookInterfaceManagerProxy(mIccPhoneBookInterfaceManagerProxy);
                new CompositePhoneSubInfoProxy(mPhoneSubInfoProxy);

                sMadeDefaults = true;
            }
        }
    }

    /*
     * This function returns the type of the phone, depending
     * on the network mode.
     *
     * @param network mode
     * @return Phone Type
     */
    public static int getPhoneType(int networkMode) {
        switch(networkMode) {
        case RILConstants.NETWORK_MODE_CDMA:
        case RILConstants.NETWORK_MODE_CDMA_NO_EVDO:
        case RILConstants.NETWORK_MODE_EVDO_NO_CDMA:
            return Phone.PHONE_TYPE_CDMA;

        case RILConstants.NETWORK_MODE_WCDMA_PREF:
        case RILConstants.NETWORK_MODE_GSM_ONLY:
        case RILConstants.NETWORK_MODE_WCDMA_ONLY:
        case RILConstants.NETWORK_MODE_GSM_UMTS:
            return Phone.PHONE_TYPE_GSM;

        // Use CDMA Phone for the global mode including CDMA
        case RILConstants.NETWORK_MODE_GLOBAL:
        case RILConstants.NETWORK_MODE_LTE_CDMA_EVDO:
        case RILConstants.NETWORK_MODE_LTE_CMDA_EVDO_GSM_WCDMA:
            return Phone.PHONE_TYPE_CDMA;

        case RILConstants.NETWORK_MODE_LTE_ONLY:
            if (BaseCommands.getLteOnCdmaModeStatic() == Phone.LTE_ON_CDMA_TRUE) {
                return Phone.PHONE_TYPE_CDMA;
            } else {
                return Phone.PHONE_TYPE_GSM;
            }
        default:
            return Phone.PHONE_TYPE_GSM;
        }
    }

    public static Phone getDefaultPhone() {
        if (sLooper != Looper.myLooper()) {
            throw new RuntimeException(
                "PhoneFactory.getDefaultPhone must be called from Looper thread");
        }

        if (!sMadeDefaults) {
            throw new IllegalStateException("Default phones haven't been made yet!");
        }
        return sProxyPhone[getDefaultPhoneId()];
    }

    public static Phone[] getPhones() {
        if (sLooper != Looper.myLooper()) {
            throw new RuntimeException(
                "PhoneFactory.getDefaultPhone must be called from Looper thread");
        }

        if (!sMadeDefaults) {
            throw new IllegalStateException("Default phones haven't been made yet!");
        }
       return sProxyPhone;
    }

    public static Phone getCdmaPhone() {
        /*Phone phone;
        synchronized(PhoneProxy.lockForRadioTechnologyChange) {
            switch (BaseCommands.getLteOnCdmaModeStatic()) {
                case Phone.LTE_ON_CDMA_TRUE: {
                    phone = new CDMALTEPhone(sContext, sCommandsInterface, sPhoneNotifier);
                    break;
                }
                case Phone.LTE_ON_CDMA_FALSE:
                case Phone.LTE_ON_CDMA_UNKNOWN:
                default: {
                    phone = new CDMAPhone(sContext, sCommandsInterface, sPhoneNotifier);
                    break;
                }
            }
        }
        return phone;*/
        return null;
    }

    public static Phone getGsmPhone() {
        synchronized(PhoneProxy.lockForRadioTechnologyChange) {
            Phone phone = new TDPhone(sContext, sCommandsInterface[getDefaultPhoneId()], sPhoneNotifier[getDefaultPhoneId()]);
            return phone;
        }
    }

    /**
     * Makes a {@link SipPhone} object.
     * @param sipUri the local SIP URI the phone runs on
     * @return the {@code SipPhone} object or null if the SIP URI is not valid
     */
    public static SipPhone makeSipPhone(String sipUri) {
        return SipPhoneFactory.makePhone(sipUri, sContext, sPhoneNotifier[getDefaultPhoneId()]);
    }

    public static CommandsInterface getDefaultCM() {
        if (sLooper != Looper.myLooper()) {
            throw new RuntimeException(
                "PhoneFactory.getDefaultCM must be called from Looper thread");
        }

        if (null == sCommandsInterface[getDefaultPhoneId()]) {
            throw new IllegalStateException("Default CommandsInfterface haven't been made yet!");
        }
       return sCommandsInterface[getDefaultPhoneId()];
    }
    // zhanglj add begin 2011-05-20
    public static int getPhoneCount() {
        return SystemProperties.getInt("persist.msms.phone_count", DEFAULT_PHONE_COUNT);
    }

    public static boolean isMultiSim() {
        return getPhoneCount() > 1;
    }

    public static String getServiceName(String defaultServiceName, int phoneId) {
        if (isMultiSim()) {
            if (phoneId == getPhoneCount()) {
                return defaultServiceName;
            }
            return defaultServiceName + phoneId;
        } else {
            return defaultServiceName;
        }
    }
/*
    public static Uri getUri(Uri defaultUri, int phoneId){
        String uriName = defaultUri.getPath();
        if (phoneId == getPhoneCount()) {
            return defaultUri;
        }
        return Uri.parse(uriName + phoneId);
    }
*/
    public static String getFeature(String defaultFeature, int phoneId){
        if (isMultiSim()) {
            if (phoneId == getPhoneCount()) {
                return defaultFeature;
            }
            return defaultFeature + phoneId;
        } else {
            return defaultFeature;
        }
    }

    public static String getProperty(String defaultProperty, int phoneId){
        if (isMultiSim()) {
            if (phoneId == getPhoneCount()) {
                return defaultProperty;
            }
            return defaultProperty + phoneId;
        } else {
            return defaultProperty;
        }
    }

    public static String getSetting(String defaultSetting, int phoneId){
        if (isMultiSim()) {
            if (phoneId == getPhoneCount()) {
                return defaultSetting;
            }
            return defaultSetting + phoneId;
        } else {
            return defaultSetting;
        }
    }

    public static String getAction(String defaultAction, int phoneId){
        if (isMultiSim()) {
            if (phoneId == getPhoneCount()) {
                return defaultAction;
            }
            return defaultAction + phoneId;
        } else {
            return defaultAction;
        }
    }

    /**
     * get simCard is exist
     * @param phoneId
     * @return
     */
    public static boolean isCardExist(int phoneId) {
        if (sProxyPhone!=null&&phoneId<sProxyPhone.length) {
            return sProxyPhone[phoneId].getIccCard().hasIccCard();
        }else{
            return false;
        }
    }
    /**
     * get simCard state
     * @param phoneId
     * @return
     */
    public static State getSimState(int phoneId) {
        if (sProxyPhone!=null&&phoneId<sProxyPhone.length) {
            return sProxyPhone[phoneId].getIccCard().mState;
        }else{
            return State.UNKNOWN;
        }
    }
    public static State getIccCardState(int phoneId) {
        if (sProxyPhone!=null&&phoneId<sProxyPhone.length) {
            return sProxyPhone[phoneId].getIccCard().getIccCardState();
        }else{
            return State.UNKNOWN;
        }
    }
    /**
     * get simCard is ready,can use
     * @param phoneId
     * @return
     */
    public static boolean isCardReady(int phoneId) {
        boolean isCardExist = isCardExist(phoneId);
        if (isCardExist) {
            boolean isAirplaneModeOn = Settings.System.getInt(sContext.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0;
            boolean isStandby = Settings.System.getInt(sContext.getContentResolver(),getSetting(
                    Settings.System.SIM_STANDBY, phoneId), 1) == 1;
            return !isAirplaneModeOn&&isStandby;
        }else{
            return false;
        }
    }

    public static int autoSetDefaultPhoneId(boolean isUpdate) {
        return autoSetDefaultPhoneId(isUpdate, -1);
    }
    /**
     * send simcard status changed broadcast
     */
    /**
     * set system default PhoneId
     *
     * @param context
     * @return
     */
    public synchronized static int autoSetDefaultPhoneId(boolean isUpdate, int phoneId) {
        int defaultPhoneId = TelephonyManager.getDefaultDataPhoneId(sContext);
        int settingPhoneId = -1;
        if (phoneId == 0) {
            isCard1ok = true;
        }
        if (phoneId == 1) {
            isCard2ok = true;
        }
        if (isCard1ok && isCard2ok) {
            boolean hasCard1 = (PhoneFactory.isCardReady(0));
            boolean hasCard2 = (PhoneFactory.isCardReady(1));
            Log.i(LOG_TAG, "autoSetDefaultPhoneId,hasCard1=" + hasCard1 + ",hasCard2="
                    + hasCard2 + ",defaultPhoneId=" + defaultPhoneId);
            if (hasCard1 && !hasCard2) {
                settingPhoneId = 0;
            } else if (!hasCard1 && hasCard2) {
                settingPhoneId = 1;
            } else if (!hasCard1 && !hasCard2) {
                return settingPhoneId;
            }
            if (settingPhoneId == -1) {
                if (getSimState(0) != State.READY || getSimState(1) != State.READY) {
                    Log.i(LOG_TAG, "getSimState(0)" + getSimState(0) + ", getSimState(1)" +getSimState(1));
                    return defaultPhoneId;
                }
                settingPhoneId = TelephonyManager.getSettingPhoneId(sContext);
            }
            if (isUpdate && settingPhoneId != defaultPhoneId) {
                updateDefaultPhoneId(settingPhoneId);
            }
            setDefaultValue();
        } else {
            Log.i(LOG_TAG, "autoSetDefaultPhoneId,defaultPhoneId=" + defaultPhoneId
                            + ",phoneId=" + phoneId + ",isCard1ok=" + isCard1ok + ",isCard2ok="
                            + isCard2ok);
        }
        return settingPhoneId;
    }

    private static void setDefaultValue(){
        int settingPhoneId = -1;
        boolean hasCard1 = (PhoneFactory.isCardReady(0));
        boolean hasCard2 = (PhoneFactory.isCardReady(1));
        if (hasCard1 && !hasCard2) {
            settingPhoneId = 0;
        } else if (!hasCard1 && hasCard2) {
            settingPhoneId = 1;
        }
        if (settingPhoneId != -1) {
            TelephonyManager.setVoiceDefaultSim(sContext, settingPhoneId);
            TelephonyManager.setVideoDefaultSim(sContext, settingPhoneId);
            TelephonyManager.setMmsDefaultSim(sContext, settingPhoneId);
        }
    }

    public static void updateDefaultPhoneId(int settingPhoneId) {
        if (getSimState(settingPhoneId) != State.READY) {
            Log.i(LOG_TAG, "PhoneId " + settingPhoneId + " not ready");
            return;
        }
        Log.i(LOG_TAG, "updateDefaultPhoneId=" + settingPhoneId);
        TelephonyManager.setDefaultDataPhoneId(sContext, settingPhoneId);
        sContext.sendBroadcast(new Intent(Intent.ACTION_DEFAULT_PHONE_CHANGE));
    }

    public static Phone getPhone(int phoneId) {
        return getPhones()[phoneId];
    }

	public static int getDefaultPhoneId() {
		return SystemProperties.getInt("persist.msms.phone_default", DEFAULT_PHONE_ID);
	}

}
