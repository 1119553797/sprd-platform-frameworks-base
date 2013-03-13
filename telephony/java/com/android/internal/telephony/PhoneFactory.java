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


/**
 * {@hide}
 */
public class PhoneFactory {
    static final String LOG_TAG = "PHONE";
    static final int SOCKET_OPEN_RETRY_MILLIS = 2 * 1000;
    static final int SOCKET_OPEN_MAX_RETRY = 3;

    //***** Class Variables

    static protected Phone[] sProxyPhone = null;
    static protected CommandsInterface[] sCommandsInterface = null;

    static protected boolean sMadeDefaults = false;
    static protected PhoneNotifier[] sPhoneNotifier;
    static protected Looper sLooper;
    static protected Context sContext;

    static final int preferredCdmaSubscription =
                         CdmaSubscriptionSourceManager.PREFERRED_CDMA_SUBSCRIPTION;

    // zhanglj add 2011-05-20
    public static final int DEFAULT_PHONE_COUNT = 1;
    public static final int DEFAULT_PHONE_ID = 0;
    public static final int DEFAULT_DUAL_SIM_INIT_PHONE_ID = -1;
    public static final int DEFAULT_DUAL_SIM_INIT_MMS_PHONE_ID = -1;
    public static boolean UNIVERSEUI_SUPPORT = SystemProperties.getBoolean("universe_ui_support",false);
    
    
    protected static boolean isCardHandled[];

    //***** Class Methods

    public static void makeDefaultPhones(Context context) {
        makeDefaultPhone(context);
    }

    /**
     * FIXME replace this with some other way of making these
     * instances
     */
    public static void makeDefaultPhone(Context context) {

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
//        synchronized(PhoneProxy.lockForRadioTechnologyChange) {
//            Phone phone = new TDPhone(sContext, sCommandsInterface[getDefaultPhoneId()], sPhoneNotifier[getDefaultPhoneId()]);
//            return phone;
//        }
          throw new IllegalStateException(" getGsmPhone is err ! ");
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
        if (phoneId >= 0 && phoneId < TelephonyManager.getPhoneCount()) {
            isCardHandled[phoneId] = true;
        }
        Log.i(LOG_TAG, "autoSetDefaultPhoneId,defaultPhoneId=" + defaultPhoneId);
        if (isAllCardHandled()) {
            if (!isAllSimReady()) {
                for (int i = 0; i < TelephonyManager.getPhoneCount(); i++) {
                    Log.i(LOG_TAG, "isCardReady(" + i + ") = " + PhoneFactory.isCardReady(i));
                    if (UNIVERSEUI_SUPPORT) {
                        boolean isCardExist = isCardExist(i);
                        if (isCardExist) {
                            boolean isAirplaneModeOn = Settings.System.getInt(
                                    sContext.getContentResolver(),
                                    Settings.System.AIRPLANE_MODE_ON, 0) != 0;
                            if (!isAirplaneModeOn) {
                                settingPhoneId = i;
                                break;
                            }
                        }
                    } else {
                        if (PhoneFactory.isCardReady(i)) {
                            settingPhoneId = i;
                            break;
                        }
                    }

                }
            }
            if (settingPhoneId == -1) {
//                for (int i = 0; i < TelephonyManager.getPhoneCount(); i++) {
//                    if (getSimState(i) != State.READY)
//                    Log.i(LOG_TAG, "getSimState(" +i + ")" + getSimState(i));
//                    return defaultPhoneId;
//                }
                Log.i(LOG_TAG, "autoSetDefaultPhoneId,getSettingPhoneId");
                settingPhoneId = TelephonyManager.getSettingPhoneId(sContext);
            }
            if(UNIVERSEUI_SUPPORT){
                if(isUpdate && settingPhoneId != defaultPhoneId&& !isCardExist(defaultPhoneId)){
                    updateDefaultPhoneId(settingPhoneId);
                }
            }else{
                if (isUpdate && settingPhoneId != defaultPhoneId) {
                    updateDefaultPhoneId(settingPhoneId);
                }
            }

            if (!UNIVERSEUI_SUPPORT) {
                setDefaultValue();
            }   
        } else {
            for (int i = 0; i < TelephonyManager.getPhoneCount(); i++) {
                Log.i(LOG_TAG, "phoneId" + "[" + i + "]" + isCardHandled[i]);
            }
        }
        return settingPhoneId;
    }

    private static void setDefaultValue(){
        int settingPhoneId = -1;
        if (!isAllSimReady()) {
            for (int i = 0; i < TelephonyManager.getPhoneCount(); i++) {
                if (PhoneFactory.isCardReady(i)) {
                    settingPhoneId = i;
                    break;
                }
            }
        }
        Log.d(LOG_TAG, "setDefaultValue settingPhoneId = " + settingPhoneId);
        if (settingPhoneId != -1) {
            TelephonyManager.setDefaultSim(sContext, TelephonyManager.MODE_VOICE, settingPhoneId);
            TelephonyManager.setDefaultSim(sContext, TelephonyManager.MODE_VEDIO, settingPhoneId);
            TelephonyManager.setDefaultSim(sContext, TelephonyManager.MODE_MMS, settingPhoneId);
        } else {
            int settingVoicePhoneId = TelephonyManager.getSubscriberDesiredSim(sContext, TelephonyManager.MODE_VOICE);
            int settingVideoPhoneId = TelephonyManager.getSubscriberDesiredSim(sContext, TelephonyManager.MODE_VEDIO);
            int settingMmsPhoneId = TelephonyManager.getSubscriberDesiredSim(sContext, TelephonyManager.MODE_MMS);
            Log.d(LOG_TAG, "setDefaultValue,settingVoicePhoneId=" + settingVoicePhoneId
                    + " settingVideoPhoneId=" + settingVideoPhoneId
                    + " settingMmsPhoneId=" + settingMmsPhoneId);
            TelephonyManager.setDefaultSim(sContext, TelephonyManager.MODE_VOICE, settingVoicePhoneId);
            TelephonyManager.setDefaultSim(sContext, TelephonyManager.MODE_VEDIO, settingVideoPhoneId);
            TelephonyManager.setDefaultSim(sContext, TelephonyManager.MODE_MMS, settingMmsPhoneId);
        }
    }

    public static boolean isAllSimReady() {
        boolean allSimReady = true;
        for (int i = 0; i < TelephonyManager.getPhoneCount(); i++) {
            if (!PhoneFactory.isCardReady(i)) {
                allSimReady = false;
                break;
            }
        }
        return allSimReady;
    }

    public static boolean isAllCardHandled() {
        boolean allCardHandled = true;
        for (int i = 0; i < TelephonyManager.getPhoneCount(); i++) {
            if (!isCardHandled[i]) {
                allCardHandled = false;
                break;
            }
        }
        return allCardHandled;
    }

    public static void updateDefaultPhoneId(int settingPhoneId) {
        if (!UNIVERSEUI_SUPPORT) {
            if (getSimState(settingPhoneId) != State.READY) {
                Log.i(LOG_TAG, "PhoneId " + settingPhoneId + " not ready");
                return;
            }
        } else {
            if (!isCardExist(settingPhoneId)) {
                Log.i(LOG_TAG, "PhoneId " + settingPhoneId + " not exist");
                return;
            }
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
