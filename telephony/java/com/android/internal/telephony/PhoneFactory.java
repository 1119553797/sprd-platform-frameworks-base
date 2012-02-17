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

import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.net.LocalServerSocket;
import android.net.Uri;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.os.SystemProperties;

import com.android.internal.telephony.IccCard.State;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.gsm.GSMPhone;

import com.android.internal.telephony.gsm.TDPhone;

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

    static private Phone[] sProxyPhone = null;
    static private CommandsInterface[] sCommandsInterface = null;

    static private boolean sMadeDefaults = false;
    static private PhoneNotifier[] sPhoneNotifier;
    static private Looper sLooper;
    static private Context sContext;

    static final int preferredNetworkMode = RILConstants.PREFERRED_NETWORK_MODE;

    static final int preferredCdmaSubscription = RILConstants.PREFERRED_CDMA_SUBSCRIPTION;
    
    // zhanglj add 2011-05-20
    public static final int DEFAULT_PHONE_COUNT = 2;
    public static final int DEFAULT_PHONE_ID = 0;

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

                //Get preferredNetworkMode from Settings.System
                int networkMode = Settings.Secure.getInt(context.getContentResolver(),
                        Settings.Secure.PREFERRED_NETWORK_MODE, preferredNetworkMode);
                Log.i(LOG_TAG, "Network Mode set to " + Integer.toString(networkMode));

                //Get preferredNetworkMode from Settings.System
                int cdmaSubscription = Settings.Secure.getInt(context.getContentResolver(),
                        Settings.Secure.PREFERRED_CDMA_SUBSCRIPTION, preferredCdmaSubscription);
                Log.i(LOG_TAG, "Cdma Subscription set to " + Integer.toString(cdmaSubscription));

                //reads the system properties and makes commandsinterface
                
                // zhanglj modify begin 2011-05-20
                int phoneType = getPhoneType(networkMode);
                sCommandsInterface = new SprdRIL[phoneCount];
                sProxyPhone = new SprdPhoneProxy[phoneCount];
                for(int i = 0; i < phoneCount;i++) {
                	sCommandsInterface[i] = new SprdRIL(context, networkMode, cdmaSubscription, i);

                if (phoneType == Phone.PHONE_TYPE_GSM) {
                    sProxyPhone[i] = new SprdPhoneProxy(new TDPhone(context,
                            sCommandsInterface[i], sPhoneNotifier[i]));
                    Log.i(LOG_TAG, "Creating TDPhone");
                }/* else if (phoneType == Phone.PHONE_TYPE_CDMA) {
                    sProxyPhone = new PhoneProxy(new CDMAPhone(context,
                            sCommandsInterface, sPhoneNotifier));
                    Log.i(LOG_TAG, "Creating CDMAPhone");
                }*/
                
                }
                // zhanglj modify end

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

        case RILConstants.NETWORK_MODE_GLOBAL:
            return Phone.PHONE_TYPE_CDMA;
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
       return sProxyPhone[DEFAULT_PHONE_ID];
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
        synchronized(PhoneProxy.lockForRadioTechnologyChange) {
            //Phone phone = new CDMAPhone(sContext, sCommandsInterface, sPhoneNotifier);
            //return phone;
            return null;
        }
    }

    public static Phone getGsmPhone() {
        synchronized(PhoneProxy.lockForRadioTechnologyChange) {
            Phone phone = new TDPhone(sContext, sCommandsInterface[DEFAULT_PHONE_ID], sPhoneNotifier[DEFAULT_PHONE_ID]);
            return phone;
        }
    }

	
    public static CommandsInterface getDefaultCM() {
        if (sLooper != Looper.myLooper()) {
            throw new RuntimeException(
                "PhoneFactory.getDefaultCM must be called from Looper thread");
        }

        if (null == sCommandsInterface[DEFAULT_PHONE_ID]) {
            throw new IllegalStateException("Default CommandsInfterface haven't been made yet!");
        }
       return sCommandsInterface[DEFAULT_PHONE_ID];
    }
    // zhanglj add begin 2011-05-20
    public static int getPhoneCount() {
    	return SystemProperties.getInt("persist.msms.phone_count", DEFAULT_PHONE_COUNT);  	
    }
    
    public static String getServiceName(String defaultServiceName, int phoneId){
    	if(phoneId == DEFAULT_PHONE_ID){
    		return defaultServiceName; 		
    	}
    	return defaultServiceName + phoneId;
    }
    
    public static Uri getUri(Uri defaultUri, int phoneId){
    	String uriName = defaultUri.getPath();
    	if(phoneId == DEFAULT_PHONE_ID){
    		return defaultUri; 		
    	}
    	return Uri.parse(uriName + phoneId);
    }

    // supported uri
    //     content://authority/path/morepath
    // unsupported uri
    //     content://authority/path/morepath/#
    public static Uri getUri2(Uri defaultUri, int phoneId) {
        Uri.Builder builder = defaultUri.buildUpon();

        List<String> paths = defaultUri.getPathSegments();
        builder.path("");
        builder.appendPath((String)paths.get(0) + phoneId);
        for (int i = 1; i < paths.size(); i++) {
            builder.appendPath(paths.get(i));
        }
        return builder.build();
    }
    
    public static String getFeature(String defaultFeature, int phoneId){
    	if(phoneId == DEFAULT_PHONE_ID){
    		return defaultFeature; 		
    	}
    	return defaultFeature + phoneId;
    }
	
	public static String getProperty(String defaultProperty, int phoneId){
    	if(phoneId == DEFAULT_PHONE_ID){
    		return defaultProperty; 		
    	}
    	return defaultProperty + phoneId;
    }

	public static String getSetting(String defaultSetting, int phoneId){
    	if(phoneId == DEFAULT_PHONE_ID){
    		return defaultSetting; 		
    	}
    	return defaultSetting + phoneId;
    }
	
	public static String getAction(String defaultAction, int phoneId){
    	if(phoneId == DEFAULT_PHONE_ID){
    		return defaultAction; 		
    	}
    	return defaultAction + phoneId;
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
            boolean hasCard1 = (PhoneFactory.isCardReady(0))
                    && (PhoneFactory.getSimState(0) == State.READY);
            boolean hasCard2 = (PhoneFactory.isCardReady(1))
                    && (PhoneFactory.getSimState(1) == State.READY);
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
                settingPhoneId = TelephonyManager.getSettingPhoneId(sContext);
            }
            if (isUpdate && settingPhoneId != defaultPhoneId) {
                updateDefaultPhoneId(settingPhoneId);
            }
        } else {
            Log.i(LOG_TAG, "autoSetDefaultPhoneId,defaultPhoneId=" + defaultPhoneId
                            + ",phoneId=" + phoneId + ",isCard1ok=" + isCard1ok + ",isCard2ok="
                            + isCard2ok);
        }
        return settingPhoneId;
    }

    public static void updateDefaultPhoneId(int settingPhoneId) {
        Log.i(LOG_TAG, "updateDefaultPhoneId=" + settingPhoneId);
        TelephonyManager.setDefaultDataPhoneId(sContext, settingPhoneId);
        sContext.sendBroadcast(new Intent(Intent.ACTION_DEFAULT_PHONE_CHANGE));
    }

    public static Phone getPhone(int phoneId) {
        return getPhones()[phoneId];
    }

    /**
     * Makes a {@link SipPhone} object.
     * @param sipUri the local SIP URI the phone runs on
     * @return the {@code SipPhone} object or null if the SIP URI is not valid
     */
    public static SipPhone makeSipPhone(String sipUri) {
        return SipPhoneFactory.makePhone(sipUri, sContext, sPhoneNotifier[DEFAULT_PHONE_ID]);

    }
}
