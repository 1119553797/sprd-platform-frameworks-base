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

package android.telephony;

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.Settings.System;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import com.android.internal.telephony.IPhoneSubInfo;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.ITelephonyRegistry;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;

import java.util.List;

/**
 * Provides access to information about the telephony services on
 * the device. Applications can use the methods in this class to
 * determine telephony services and states, as well as to access some
 * types of subscriber information. Applications can also register
 * a listener to receive notification of telephony state changes.
 * <p>
 * You do not instantiate this class directly; instead, you retrieve
 * a reference to an instance through
 * {@link android.content.Context#getSystemService
 * Context.getSystemService(Context.TELEPHONY_SERVICE)}.
 * <p>
 * Note that access to some telephony information is
 * permission-protected. Your application cannot access the protected
 * information unless it has the appropriate permissions declared in
 * its manifest file. Where permissions apply, they are noted in the
 * the methods through which you access the protected information.
 */
public class TelephonyManager {
    private static final String TAG = "TelephonyManager";

    private static Context mContext;
    private ITelephonyRegistry mRegistry;
    private int mPhoneId;

    private static final String dualCardDefaultPhone = "com.android.dualcard_settings_preferences";
    private static final String simCardFavoritekey = "sim_card_favorite";
    private static final String sharedActivityName = "com.android.phone";
    
    public static final int UNLOCK_PIN   = 0;
    public static final int UNLOCK_PIN2   = 1;
    public static final int UNLOCK_PUK   = 2;
    public static final int UNLOCK_PUK2   = 3;
    /** @hide */
    public TelephonyManager(Context context) {
        this(context,PhoneFactory.getPhoneCount());
    }

    /** @hide */
    public TelephonyManager(Context context, int phoneId) {
        mContext = context;
        mRegistry = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService(
                    PhoneFactory.getServiceName("telephony.registry", phoneId)));
        mPhoneId = phoneId;
    }

    /** @hide */
    private TelephonyManager(int phoneId) {
    	mPhoneId = phoneId;
    }

    private static TelephonyManager[] sInstance;

    static {
        if (PhoneFactory.isMultiSim()) {
            sInstance = new TelephonyManager[PhoneFactory.getPhoneCount() + 1];
            for (int i = 0; i <= PhoneFactory.getPhoneCount(); i++) {
                sInstance[i] = new TelephonyManager(i);
            }
        } else {
            sInstance = new TelephonyManager[1];
            sInstance[0] = new TelephonyManager(0);
        }
    }

    /** @hide */
    public static TelephonyManager getDefault() {
        if (PhoneFactory.isMultiSim()) {
            return sInstance[PhoneFactory.getPhoneCount()];
        } else {
            return sInstance[0];
        }
    }

    /** @hide */
    public static TelephonyManager getDefault(int phoneId) {
        if (phoneId > PhoneFactory.getPhoneCount()) {
            //phoneId can equal to getPhoneCount
            throw new IllegalArgumentException("phoneId exceeds phoneCount");
        }
        return sInstance[phoneId];
    }


    //
    // Broadcast Intent actions
    //

    /**
     * Broadcast intent action indicating that the call state (cellular)
     * on the device has changed.
     *
     * <p>
     * The {@link #EXTRA_STATE} extra indicates the new call state.
     * If the new state is RINGING, a second extra
     * {@link #EXTRA_INCOMING_NUMBER} provides the incoming phone number as
     * a String.
     *
     * <p class="note">
     * Requires the READ_PHONE_STATE permission.
     *
     * <p class="note">
     * This was a {@link android.content.Context#sendStickyBroadcast sticky}
     * broadcast in version 1.0, but it is no longer sticky.
     * Instead, use {@link #getCallState} to synchronously query the current call state.
     *
     * @see #EXTRA_STATE
     * @see #EXTRA_INCOMING_NUMBER
     * @see #getCallState
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_PHONE_STATE_CHANGED =
            "android.intent.action.PHONE_STATE";

    /**
     * The lookup key used with the {@link #ACTION_PHONE_STATE_CHANGED} broadcast
     * for a String containing the new call state.
     *
     * @see #EXTRA_STATE_IDLE
     * @see #EXTRA_STATE_RINGING
     * @see #EXTRA_STATE_OFFHOOK
     *
     * <p class="note">
     * Retrieve with
     * {@link android.content.Intent#getStringExtra(String)}.
     */
    public static final String EXTRA_STATE = Phone.STATE_KEY;

    /**
     * Value used with {@link #EXTRA_STATE} corresponding to
     * {@link #CALL_STATE_IDLE}.
     */
    public static final String EXTRA_STATE_IDLE = Phone.State.IDLE.toString();

    /**
     * Value used with {@link #EXTRA_STATE} corresponding to
     * {@link #CALL_STATE_RINGING}.
     */
    public static final String EXTRA_STATE_RINGING = Phone.State.RINGING.toString();

    /**
     * Value used with {@link #EXTRA_STATE} corresponding to
     * {@link #CALL_STATE_OFFHOOK}.
     */
    public static final String EXTRA_STATE_OFFHOOK = Phone.State.OFFHOOK.toString();

    /**
     * The lookup key used with the {@link #ACTION_PHONE_STATE_CHANGED} broadcast
     * for a String containing the incoming phone number.
     * Only valid when the new call state is RINGING.
     *
     * <p class="note">
     * Retrieve with
     * {@link android.content.Intent#getStringExtra(String)}.
     */
    public static final String EXTRA_INCOMING_NUMBER = "incoming_number";


    //
    //
    // Device Info
    //
    //

    /**
     * Returns the software version number for the device, for example,
     * the IMEI/SV for GSM phones. Return null if the software version is
     * not available.
     *
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getDeviceSoftwareVersion() {
        try {
            return getSubscriberInfo().getDeviceSvn();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
    }

    /**
     * Returns the unique device ID, for example, the IMEI for GSM and the MEID
     * or ESN for CDMA phones. Return null if device ID is not available.
     *
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getDeviceId() {
        try {
            return getSubscriberInfo().getDeviceId();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
    }

    /**
     * Returns the current location of the device.
     * Return null if current location is not available.
     *
     * <p>Requires Permission:
     * {@link android.Manifest.permission#ACCESS_COARSE_LOCATION ACCESS_COARSE_LOCATION} or
     * {@link android.Manifest.permission#ACCESS_COARSE_LOCATION ACCESS_FINE_LOCATION}.
     */
    public CellLocation getCellLocation() {
        try {
            Bundle bundle = getITelephony().getCellLocation();
            CellLocation cl = CellLocation.newFromBundle(bundle);
            if (cl.isEmpty())
                return null;
            return cl;
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
    }

    public boolean getSimLoaded() {
        boolean simLoaded = false;
        try {
            simLoaded = getITelephony().getSimLoaded();
        } catch (RemoteException ex) {
         Log.d(TAG, "RemoteException:isSimLoaded"+ex.toString());
        } catch (NullPointerException ex) {
         Log.d(TAG, "RemoteException:isSimLoaded"+ex.toString());
        }
        return simLoaded;
    }
    /**
     * Enables location update notifications.  {@link PhoneStateListener#onCellLocationChanged
     * PhoneStateListener.onCellLocationChanged} will be called on location updates.
     *
     * <p>Requires Permission: {@link android.Manifest.permission#CONTROL_LOCATION_UPDATES
     * CONTROL_LOCATION_UPDATES}
     *
     * @hide
     */
    public void enableLocationUpdates() {
        try {
            getITelephony().enableLocationUpdates();
        } catch (RemoteException ex) {
        } catch (NullPointerException ex) {
        }
    }

    /**
     * Disables location update notifications.  {@link PhoneStateListener#onCellLocationChanged
     * PhoneStateListener.onCellLocationChanged} will be called on location updates.
     *
     * <p>Requires Permission: {@link android.Manifest.permission#CONTROL_LOCATION_UPDATES
     * CONTROL_LOCATION_UPDATES}
     *
     * @hide
     */
    public void disableLocationUpdates() {
        try {
            getITelephony().disableLocationUpdates();
        } catch (RemoteException ex) {
        } catch (NullPointerException ex) {
        }
    }

    /**
     * Returns the neighboring cell information of the device.
     *
     * @return List of NeighboringCellInfo or null if info unavailable.
     *
     * <p>Requires Permission:
     * (@link android.Manifest.permission#ACCESS_COARSE_UPDATES}
     */
    public List<NeighboringCellInfo> getNeighboringCellInfo() {
        try {
            return getITelephony().getNeighboringCellInfo();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
    }

    /** No phone radio. */
    public static final int PHONE_TYPE_NONE = Phone.PHONE_TYPE_NONE;
    /** Phone radio is GSM. */
    public static final int PHONE_TYPE_GSM = Phone.PHONE_TYPE_GSM;
    /** Phone radio is CDMA. */
    public static final int PHONE_TYPE_CDMA = Phone.PHONE_TYPE_CDMA;

    /**
     * Returns a constant indicating the device phone type.
     *
     * @see #PHONE_TYPE_NONE
     * @see #PHONE_TYPE_GSM
     * @see #PHONE_TYPE_CDMA
     */
    public int getPhoneType() {
        try{
            ITelephony telephony = getITelephony();
            if (telephony != null) {
                return telephony.getActivePhoneType();
            } else {
                // This can happen when the ITelephony interface is not up yet.
                return getPhoneTypeFromProperty();
            }
        } catch (RemoteException ex) {
            // This shouldn't happen in the normal case, as a backup we
            // read from the system property.
            return getPhoneTypeFromProperty();
        } catch (NullPointerException ex) {
            // This shouldn't happen in the normal case, as a backup we
            // read from the system property.
            return getPhoneTypeFromProperty();
        }
    }


    private int getPhoneTypeFromProperty() {
        String currentActivePhoneProperty = PhoneFactory.getProperty(
                TelephonyProperties.CURRENT_ACTIVE_PHONE, mPhoneId);
        int type =
            SystemProperties.getInt(currentActivePhoneProperty,
                    getPhoneTypeFromNetworkType());
        return type;
    }

    private int getPhoneTypeFromNetworkType() {
        // When the system property CURRENT_ACTIVE_PHONE, has not been set,
        // use the system property for default network type.
        // This is a fail safe, and can only happen at first boot.
        int mode = SystemProperties.getInt("ro.telephony.default_network", -1);
        if (mode == -1)
            return PHONE_TYPE_NONE;
        return PhoneFactory.getPhoneType(mode);
    }
    //
    //
    // Current Network
    //
    //

    /**
     * Returns the alphabetic name of current registered operator.
     * <p>
     * Availability: Only when user is registered to a network. Result may be
     * unreliable on CDMA networks (use {@link #getPhoneType()} to determine if
     * on a CDMA network).
     */
    public String getNetworkOperatorName() {
        String operatorAlphaProperty = PhoneFactory.getProperty(
                TelephonyProperties.PROPERTY_OPERATOR_ALPHA, mPhoneId);
        return SystemProperties.get(operatorAlphaProperty);
    }

    /**
     * Returns the numeric name (MCC+MNC) of current registered operator.
     * <p>
     * Availability: Only when user is registered to a network. Result may be
     * unreliable on CDMA networks (use {@link #getPhoneType()} to determine if
     * on a CDMA network).
     */
    public String getNetworkOperator() {
        String operatorNumericProperty = PhoneFactory.getProperty(
                TelephonyProperties.PROPERTY_OPERATOR_NUMERIC, mPhoneId);
        return SystemProperties.get(operatorNumericProperty);
    }

    /**
     * Returns true if the device is considered roaming on the current
     * network, for GSM purposes.
     * <p>
     * Availability: Only when user registered to a network.
     */
    public boolean isNetworkRoaming() {
        String operatorIsRoamingProperty = PhoneFactory.getProperty(
                TelephonyProperties.PROPERTY_OPERATOR_ISROAMING, mPhoneId);
        return "true".equals(SystemProperties.get(operatorIsRoamingProperty));
    }

    /**
     * Returns the ISO country code equivalent of the current registered
     * operator's MCC (Mobile Country Code).
     * <p>
     * Availability: Only when user is registered to a network. Result may be
     * unreliable on CDMA networks (use {@link #getPhoneType()} to determine if
     * on a CDMA network).
     */
    public String getNetworkCountryIso() {
        String operatorIsoCountryProperty = PhoneFactory.getProperty(
                TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY, mPhoneId);
        return SystemProperties.get(operatorIsoCountryProperty);
    }

    /** Network type is unknown */
    public static final int NETWORK_TYPE_UNKNOWN = 0;
    /** Current network is GPRS */
    public static final int NETWORK_TYPE_GPRS = 1;
    /** Current network is EDGE */
    public static final int NETWORK_TYPE_EDGE = 2;
    /** Current network is UMTS */
    public static final int NETWORK_TYPE_UMTS = 3;
    /** Current network is CDMA: Either IS95A or IS95B*/
    public static final int NETWORK_TYPE_CDMA = 4;
    /** Current network is EVDO revision 0*/
    public static final int NETWORK_TYPE_EVDO_0 = 5;
    /** Current network is EVDO revision A*/
    public static final int NETWORK_TYPE_EVDO_A = 6;
    /** Current network is 1xRTT*/
    public static final int NETWORK_TYPE_1xRTT = 7;
    /** Current network is HSDPA */
    public static final int NETWORK_TYPE_HSDPA = 8;
    /** Current network is HSUPA */
    public static final int NETWORK_TYPE_HSUPA = 9;
    /** Current network is HSPA */
    public static final int NETWORK_TYPE_HSPA = 10;
    /** Current network is iDen */
    public static final int NETWORK_TYPE_IDEN = 11;
    /** Current network is EVDO revision B*/
    public static final int NETWORK_TYPE_EVDO_B = 12;
    /** @hide */
    public static final int NETWORK_TYPE_LTE = 13;
    /** @hide */
    public static final int NETWORK_TYPE_EHRPD = 14;

    /**
     * Returns a constant indicating the radio technology (network type)
     * currently in use on the device.
     * @return the network type
     *
     * @see #NETWORK_TYPE_UNKNOWN
     * @see #NETWORK_TYPE_GPRS
     * @see #NETWORK_TYPE_EDGE
     * @see #NETWORK_TYPE_UMTS
     * @see #NETWORK_TYPE_HSDPA
     * @see #NETWORK_TYPE_HSUPA
     * @see #NETWORK_TYPE_HSPA
     * @see #NETWORK_TYPE_CDMA
     * @see #NETWORK_TYPE_EVDO_0
     * @see #NETWORK_TYPE_EVDO_A
     * @see #NETWORK_TYPE_EVDO_B
     * @see #NETWORK_TYPE_1xRTT
     */
    public int getNetworkType() {
        try{
            ITelephony telephony = getITelephony();
            if (telephony != null) {
                return telephony.getNetworkType();
            } else {
                // This can happen when the ITelephony interface is not up yet.
                return NETWORK_TYPE_UNKNOWN;
            }
        } catch(RemoteException ex) {
            // This shouldn't happen in the normal case
            return NETWORK_TYPE_UNKNOWN;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return NETWORK_TYPE_UNKNOWN;
        }
    }

    /**
     * Returns a string representation of the radio technology (network type)
     * currently in use on the device.
     * @return the name of the radio technology
     *
     * @hide pending API council review
     */
    public String getNetworkTypeName() {
        switch (getNetworkType()) {
            case NETWORK_TYPE_GPRS:
                return "GPRS";
            case NETWORK_TYPE_EDGE:
                return "EDGE";
            case NETWORK_TYPE_UMTS:
                return "UMTS";
            case NETWORK_TYPE_HSDPA:
                return "HSDPA";
            case NETWORK_TYPE_HSUPA:
                return "HSUPA";
            case NETWORK_TYPE_HSPA:
                return "HSPA";
            case NETWORK_TYPE_CDMA:
                return "CDMA";
            case NETWORK_TYPE_EVDO_0:
                return "CDMA - EvDo rev. 0";
            case NETWORK_TYPE_EVDO_A:
                return "CDMA - EvDo rev. A";
            case NETWORK_TYPE_EVDO_B:
                return "CDMA - EvDo rev. B";
            case NETWORK_TYPE_1xRTT:
                return "CDMA - 1xRTT";
            default:
                return "UNKNOWN";
        }
    }

    //
    //
    // SIM Card
    //
    //

    /** SIM card state: Unknown. Signifies that the SIM is in transition
     *  between states. For example, when the user inputs the SIM pin
     *  under PIN_REQUIRED state, a query for sim status returns
     *  this state before turning to SIM_STATE_READY. */
    public static final int SIM_STATE_UNKNOWN = 0;
    /** SIM card state: no SIM card is available in the device */
    public static final int SIM_STATE_ABSENT = 1;
    /** SIM card state: Locked: requires the user's SIM PIN to unlock */
    public static final int SIM_STATE_PIN_REQUIRED = 2;
    /** SIM card state: Locked: requires the user's SIM PUK to unlock */
    public static final int SIM_STATE_PUK_REQUIRED = 3;
    /** SIM card state: Locked: requries a network PIN to unlock */
    public static final int SIM_STATE_NETWORK_LOCKED = 4;
    /** SIM card state: Ready */
    public static final int SIM_STATE_READY = 5;

    /**
     * @return true if a ICC card is present
     */
    public boolean hasIccCard() {
        try {
            return getITelephony().hasIccCard();
        } catch (RemoteException ex) {
            // Assume no ICC card if remote exception which shouldn't happen
            return false;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return false;
        }
    }
    /**
     * @return true if a IccFdn enabled
     */
    public boolean getIccFdnEnabled() {
        try {
            return getITelephony().getIccFdnEnabled();
        } catch (RemoteException ex) {
            // Assume no ICC card if remote exception which shouldn't happen
            return false;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return false;
        }
    }
    /**
     * @return true if sim card is USIM/TD
     */
    public boolean isUsimCard() {
        try {
            return getITelephony().isUsimCard();
        } catch (RemoteException ex) {
            return false;
        } catch (NullPointerException ex) {
            return false;
        }

    }
    /**
     * Returns a constant indicating the state of the
     * device SIM card.
     *
     * @see #SIM_STATE_UNKNOWN
     * @see #SIM_STATE_ABSENT
     * @see #SIM_STATE_PIN_REQUIRED
     * @see #SIM_STATE_PUK_REQUIRED
     * @see #SIM_STATE_NETWORK_LOCKED
     * @see #SIM_STATE_READY
     */
    public int getSimState() {
        String simStateProperty = PhoneFactory.getProperty(TelephonyProperties.PROPERTY_SIM_STATE,
                mPhoneId);
        String prop = SystemProperties.get(simStateProperty);
        if ("ABSENT".equals(prop)) {
            return SIM_STATE_ABSENT;
        }
        else if ("PIN_REQUIRED".equals(prop)) {
            return SIM_STATE_PIN_REQUIRED;
        }
        else if ("PUK_REQUIRED".equals(prop)) {
            return SIM_STATE_PUK_REQUIRED;
        }
        else if ("NETWORK_LOCKED".equals(prop)) {
            return SIM_STATE_NETWORK_LOCKED;
        }
        else if ("READY".equals(prop)) {
            return SIM_STATE_READY;
        }
        else {
            return SIM_STATE_UNKNOWN;
        }
    }

    /**
     * Returns the MCC+MNC (mobile country code + mobile network code) of the
     * provider of the SIM. 5 or 6 decimal digits.
     * <p>
     * Availability: SIM state must be {@link #SIM_STATE_READY}
     *
     * @see #getSimState
     */
    public String getSimOperator() {
        String iccOperatorNumericProperty = PhoneFactory.getProperty(
                TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC, mPhoneId);
        return SystemProperties.get(iccOperatorNumericProperty);
    }

    /**
     * Returns the Service Provider Name (SPN).
     * <p>
     * Availability: SIM state must be {@link #SIM_STATE_READY}
     *
     * @see #getSimState
     */
    public String getSimOperatorName() {
        String iccOperatorAlphaProperty = PhoneFactory.getProperty(
                TelephonyProperties.PROPERTY_ICC_OPERATOR_ALPHA, mPhoneId);
        return SystemProperties.get(iccOperatorAlphaProperty);
    }

    /**
     * Returns the ISO country code equivalent for the SIM provider's country code.
     */
    public String getSimCountryIso() {
        String iccOperatorIsoCountryProperty = PhoneFactory.getProperty(
                TelephonyProperties.PROPERTY_ICC_OPERATOR_ISO_COUNTRY, mPhoneId);
        return SystemProperties.get(iccOperatorIsoCountryProperty);
    }

    /**
     * Returns the serial number of the SIM, if applicable. Return null if it is
     * unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getSimSerialNumber() {
        try {
            return getSubscriberInfo().getIccSerialNumber();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    //
    //
    // Subscriber Info
    //
    //

    /**
     * Returns the unique subscriber ID, for example, the IMSI for a GSM phone.
     * Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getSubscriberId() {
        try {
            return getSubscriberInfo().getSubscriberId();
        } catch (RemoteException ex) {
        Log.d("TelephonyManager","getSubscriberId Exception!!! ");
            return "";
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            Log.d("TelephonyManager","getSubscriberId get NullPointerException!!! ");
            return "";
        }
    }

    /**
     * Returns the phone number string for line 1, for example, the MSISDN
     * for a GSM phone. Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getLine1Number() {
        try {
            return getSubscriberInfo().getLine1Number();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the alphabetic identifier associated with the line 1 number.
     * Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     * @hide
     * nobody seems to call this.
     */
    public String getLine1AlphaTag() {
        try {
            return getSubscriberInfo().getLine1AlphaTag();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the voice mail number. Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getVoiceMailNumber() {
        try {
            return getSubscriberInfo().getVoiceMailNumber();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the complete voice mail number. Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#CALL_PRIVILEGED CALL_PRIVILEGED}
     *
     * @hide
     */
    public String getCompleteVoiceMailNumber() {
        try {
            return getSubscriberInfo().getCompleteVoiceMailNumber();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the voice mail count. Return 0 if unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     * @hide
     */
    public int getVoiceMessageCount() {
        try {
            return getITelephony().getVoiceMessageCount();
        } catch (RemoteException ex) {
            return 0;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return 0;
        }
    }

    /**
     * Retrieves the alphabetic identifier associated with the voice
     * mail number.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getVoiceMailAlphaTag() {
        try {
            return getSubscriberInfo().getVoiceMailAlphaTag();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    private IPhoneSubInfo getSubscriberInfo() {
        // get it each time because that process crashes a lot
        return IPhoneSubInfo.Stub.asInterface(ServiceManager.getService(PhoneFactory.getServiceName("iphonesubinfo", mPhoneId)));
    }


    /** Device call state: No activity. */
    public static final int CALL_STATE_IDLE = 0;
    /** Device call state: Ringing. A new call arrived and is
     *  ringing or waiting. In the latter case, another call is
     *  already active. */
    public static final int CALL_STATE_RINGING = 1;
    /** Device call state: Off-hook. At least one call exists
      * that is dialing, active, or on hold, and no calls are ringing
      * or waiting. */
    public static final int CALL_STATE_OFFHOOK = 2;

    /**
     * Returns a constant indicating the call state (cellular) on the device.
     */
    public int getCallState() {
        try {
            return getITelephony().getCallState();
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return CALL_STATE_IDLE;
        } catch (NullPointerException ex) {
          // the phone process is restarting.
          return CALL_STATE_IDLE;
      }
    }

    /** Data connection activity: No traffic. */
    public static final int DATA_ACTIVITY_NONE = 0x00000000;
    /** Data connection activity: Currently receiving IP PPP traffic. */
    public static final int DATA_ACTIVITY_IN = 0x00000001;
    /** Data connection activity: Currently sending IP PPP traffic. */
    public static final int DATA_ACTIVITY_OUT = 0x00000002;
    /** Data connection activity: Currently both sending and receiving
     *  IP PPP traffic. */
    public static final int DATA_ACTIVITY_INOUT = DATA_ACTIVITY_IN | DATA_ACTIVITY_OUT;
    /**
     * Data connection is active, but physical link is down
     */
    public static final int DATA_ACTIVITY_DORMANT = 0x00000004;

    /**
     * Returns a constant indicating the type of activity on a data connection
     * (cellular).
     *
     * @see #DATA_ACTIVITY_NONE
     * @see #DATA_ACTIVITY_IN
     * @see #DATA_ACTIVITY_OUT
     * @see #DATA_ACTIVITY_INOUT
     * @see #DATA_ACTIVITY_DORMANT
     */
    public int getDataActivity() {
        try {
            return getITelephony().getDataActivity();
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return DATA_ACTIVITY_NONE;
        } catch (NullPointerException ex) {
          // the phone process is restarting.
          return DATA_ACTIVITY_NONE;
      }
    }

    /** Data connection state: Disconnected. IP traffic not available. */
    public static final int DATA_DISCONNECTED   = 0;
    /** Data connection state: Currently setting up a data connection. */
    public static final int DATA_CONNECTING     = 1;
    /** Data connection state: Connected. IP traffic should be available. */
    public static final int DATA_CONNECTED      = 2;
    /** Data connection state: Suspended. The connection is up, but IP
     * traffic is temporarily unavailable. For example, in a 2G network,
     * data activity may be suspended when a voice call arrives. */
    public static final int DATA_SUSPENDED      = 3;

	public static final int MODEM_TYPE_GSM = 0;

	public static final int MODEM_TYPE_TDSCDMA = 1;
	

    /**
     * Returns a constant indicating the current data connection state
     * (cellular).
     *
     * @see #DATA_DISCONNECTED
     * @see #DATA_CONNECTING
     * @see #DATA_CONNECTED
     * @see #DATA_SUSPENDED
     */
    public int getDataState() {
        try {
            return getITelephony().getDataState();
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return DATA_DISCONNECTED;
        } catch (NullPointerException ex) {
            return DATA_DISCONNECTED;
        }
    }

    private ITelephony getITelephony() {
        return ITelephony.Stub.asInterface(ServiceManager.getService(PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE, mPhoneId)));
    }

    //
    //
    // PhoneStateListener
    //
    //

    /**
     * Registers a listener object to receive notification of changes
     * in specified telephony states.
     * <p>
     * To register a listener, pass a {@link PhoneStateListener}
     * and specify at least one telephony state of interest in
     * the events argument.
     *
     * At registration, and when a specified telephony state
     * changes, the telephony manager invokes the appropriate
     * callback method on the listener object and passes the
     * current (udpated) values.
     * <p>
     * To unregister a listener, pass the listener object and set the
     * events argument to
     * {@link PhoneStateListener#LISTEN_NONE LISTEN_NONE} (0).
     *
     * @param listener The {@link PhoneStateListener} object to register
     *                 (or unregister)
     * @param events The telephony state(s) of interest to the listener,
     *               as a bitwise-OR combination of {@link PhoneStateListener}
     *               LISTEN_ flags.
     */
    public void listen(PhoneStateListener listener, int events) {
        String pkgForDebug = mContext != null ? mContext.getPackageName() : "<unknown>";
        try {
            Boolean notifyNow = (getITelephony() != null);
            mRegistry.listen(pkgForDebug, listener.callback, events, notifyNow);
        } catch (RemoteException ex) {
            // system process dead
        } catch (NullPointerException ex) {
            // system process dead
        }
    }

    /**
     * Returns the CDMA ERI icon index to display
     *
     * @hide
     */
    public int getCdmaEriIconIndex() {
        try {
            return getITelephony().getCdmaEriIconIndex();
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return -1;
        } catch (NullPointerException ex) {
            return -1;
        }
    }

    /**
     * Returns the CDMA ERI icon mode,
     * 0 - ON
     * 1 - FLASHING
     *
     * @hide
     */
    public int getCdmaEriIconMode() {
        try {
            return getITelephony().getCdmaEriIconMode();
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return -1;
        } catch (NullPointerException ex) {
            return -1;
        }
    }
    
    /**
     * Returns the CDMA ERI text,
     *
     * @hide
     */
    public String getCdmaEriText() {
        try {
            return getITelephony().getCdmaEriText();
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
    }

    public static int getPhoneCount(){
        return PhoneFactory.getPhoneCount();
    }

   	/**
     * {@hide}
     */
    public int getModemType(){
        String baseBand = SystemProperties.get(
                PhoneFactory.getProperty(TelephonyProperties.PROPERTY_BASEBAND_VERSION, mPhoneId),
                "");
		String modemValue = null;

		if(baseBand != null && !baseBand.equals("")){
			Log.d(TAG, "baseband = "+baseBand);
			modemValue =  baseBand.split("\\|")[1];
			Log.d(TAG, "modemValue = "+modemValue);
			//if(modemValue.equals("sc8805_sp8805")){//fix bug 7294 close
			if(modemValue.equals("sc8810_modem")){//fix bug 7294 add
				return MODEM_TYPE_TDSCDMA;
			}else if(modemValue.equals("sc6810_sp6810")){
				return MODEM_TYPE_GSM;
			} else if (modemValue.equals("sc6820_sp6820")) {
			    return MODEM_TYPE_GSM;
			}
		}
		Log.d(TAG, "can not get the baseband version");
		return MODEM_TYPE_GSM;
    }

    /**
     * Returns the array，String[0] - sres,String[1] - kc,
     *
     * @hide
     */
    public String[] Mbbms_Gsm_Authenticate(String nonce) {
    	String[] authen;
    	try {
    		authen = getITelephony().Mbbms_Gsm_Authenticate(nonce);
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
    	return authen;
    }
    /**
     * Returns the array，String[0] ，“1” -need GBA recynchronization，“0” - succeed。
     * String[1] - res, String[2] -ck, String[3] - ik;
     *
     * @hide
     */
    public String[] Mbbms_USim_Authenticate(String nonce, String autn) {
    	String[] authen;
    	try {
    		authen = getITelephony().Mbbms_USim_Authenticate(nonce, autn);
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
        return authen;    	
    }
    
    /**
     * Returns the type，0 --SIM，1 -- USIM,
     *
     * @hide
     */
    public String getSimType() {

        try {
        	return getITelephony().getSimType();
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
    }
    
    public String[] getRegistrationState() {
    	 try {
         	return getITelephony().getRegistrationState();
         } catch (RemoteException ex) {
             // the phone process is restarting.
             return null;
         } catch (NullPointerException ex) {
             return null;
         }
    }
    
    public boolean isVTCall() {
    	 try {
         	return getITelephony().isVTCall();
         } catch (RemoteException ex) {
             // the phone process is restarting.
             return false;
         } catch (NullPointerException ex) {
             return false;
         }	
    }
    
    public int getAdnCachestate() {
        String adnCacheStateProperty = PhoneFactory.getProperty(
                TelephonyProperties.ADNCACHE_LOADED_STATE, mPhoneId);
        return Integer.valueOf(SystemProperties.get(adnCacheStateProperty,"0"));
    }

// return -1 if invalid
    public int getRemainTimes(int type) {
        try {
        	return getITelephony().getRemainTimes(type);
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return -1;
        } catch (NullPointerException ex) {
            return -1;
        }
    	
    }
    
    public  boolean setApnActivePdpFilter(String apntype,boolean filterenable) {
        try {
           return getITelephony().setApnActivePdpFilter(apntype,filterenable);
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return false;
        } catch (NullPointerException ex) {
            return false;
        }  
   }

    public  boolean  getApnActivePdpFilter(String apntype) {
        try {
           return getITelephony().getApnActivePdpFilter(apntype);
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return false;
        } catch (NullPointerException ex) {
            return false;
        }  
    }
    
    /**
     * Set phoneId which Data connection attach on now.
     * <p>
     * May be it is better named getCurrentDataAttachOn
     * </p>
     * @see #setDefaultDataPhoneId(Context, int)
     */
    public static int getDefaultDataPhoneId(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.MULTI_SIM_DATA_CALL, PhoneFactory.getDefaultPhoneId());
    }

    /**
     * Get phoneId which Data connection attach on in current state.
     * <p>
     * May be it is better named setCurrentDataAttachOn
     * </p>
     * @see #getDefaultDataPhoneId(Context)
     */
    public static boolean setDefaultDataPhoneId(Context context, int phoneId) {
        setPropertyDataPhoneId(phoneId);
        return Settings.System.putInt(context.getContentResolver(),
                Settings.System.MULTI_SIM_DATA_CALL, phoneId);
    }
    public static void setPropertyDataPhoneId(int phoneId) {
        SystemProperties.set("persist.msms.phone_default", String.valueOf(phoneId));
    }
    /**
     * Get phoneId which User have set in settings that data connection should attach on default.
     * @see #setAutoDefaultPhoneId(Context, int)
     */
    public static int getSettingPhoneId(Context context) {
        SharedPreferences settings = getPhoneSetting(context);
        int setPhoneId = settings.getInt(simCardFavoritekey, -1);
        if (setPhoneId == -1) {
            setPhoneId = TelephonyManager.getDefaultDataPhoneId(context);
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt(simCardFavoritekey, setPhoneId);
            editor.commit();
        }
        return setPhoneId;
    }

    /**
     * Set phoneId which data connection should attach on default
     * @see #getSettingPhoneId(Context)
     */
    public static void setAutoDefaultPhoneId(Context context, int setPhoneId) {
        SharedPreferences settings = getPhoneSetting(context);

        int DefaultId = settings.getInt(simCardFavoritekey, -1);
        if (setPhoneId != DefaultId) {
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt(simCardFavoritekey, setPhoneId);
            editor.commit();
        }
    }

    private static SharedPreferences getPhoneSetting(Context context) {
        Context aimContext = null;
        try {
            aimContext = context.createPackageContext(sharedActivityName,
                    Context.CONTEXT_IGNORE_SECURITY);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        return aimContext.getSharedPreferences(dualCardDefaultPhone,
                Context.MODE_WORLD_READABLE + Context.MODE_WORLD_WRITEABLE);
    }

    //About default sim card setting for vioce,video,mms
    public static final int MODE_TEL = 0;
    public static final int MODE_MMS = 1;
    public static final int MODE_VTEL = 2;
    public static final int PHONE_ID_INVALID = -1;
    private static final String DUAL_SIM_VOICE_ID_KEY = "dual_sim_tel_id";
    private static final String DUAL_SIM_VOICE_IMSI_KEY = "dual_sim_tel_imsi";
    private static final String DUAL_SIM_VIDEO_ID_KEY = "dual_sim_vtel_id";
    private static final String DUAL_SIM_VIDEO_IMSI_KEY = "dual_sim_vtel_imsi";
    private static final String DUAL_SIM_MMS_ID_KEY = "dual_sim_mms_id";
    private static final String DUAL_SIM_MMS_IMSI_KEY = "dual_sim_mms_imsi";

    private static boolean isStandby[];
    /**
     * Set default sim card for vioce,video, mms
     *
     * @param context
     * @param mode
     * @param phoneId
     */
    public static void setDefaultSim(Context context, int mode, int phoneId) {
        // get imsi
//        String imsi = "";
//        if (phoneId >= 0) {
//            TelephonyManager tm = getDefault(phoneId);
//            imsi = tm.getSubscriberId();
//        }

        // get imsiKey and phoneIdKey
        String phoneIdKey = "";
        String imsiKey = "";
        switch (mode) {
            case MODE_TEL:
                phoneIdKey = DUAL_SIM_VOICE_ID_KEY;
                imsiKey = DUAL_SIM_VOICE_IMSI_KEY;
                break;
            case MODE_MMS:
                phoneIdKey = DUAL_SIM_MMS_ID_KEY;
                imsiKey = DUAL_SIM_MMS_IMSI_KEY;
                break;
            case MODE_VTEL:
                phoneIdKey = DUAL_SIM_VIDEO_ID_KEY;
                imsiKey = DUAL_SIM_VIDEO_IMSI_KEY;
                break;
            default:
                break;
        }
        Log.d(TAG, "setDefaultSim:phoneIdKey "+phoneIdKey +" phoneId "+phoneId);
        // set the value into the database
        Settings.System.putInt(context.getContentResolver(), phoneIdKey, phoneId);
//        Settings.System.putString(context.getContentResolver(), imsiKey, imsi);
    }

    /**
     * Get default sim card for vioce,video, mms,return -1 if null
     *
     * @param context
     * @param mode
     * @return default phoneId
     */
    public static int getDefaultSim(Context context, int mode) {
        if (getPhoneCount() == 1) {
            return 0;
        }
        ContentResolver cr = context.getContentResolver();
        int phoneId = PHONE_ID_INVALID;
        String imsi = "";
        switch (mode) {
            case MODE_TEL: {
                phoneId = Settings.System.getInt(cr, DUAL_SIM_VOICE_ID_KEY, PHONE_ID_INVALID);
                imsi = Settings.System.getString(cr, DUAL_SIM_VOICE_IMSI_KEY);
                break;
            }
            case MODE_MMS:
                phoneId = Settings.System.getInt(cr, DUAL_SIM_MMS_ID_KEY, PHONE_ID_INVALID);
                imsi = Settings.System.getString(cr, DUAL_SIM_MMS_IMSI_KEY);
                break;
            case MODE_VTEL:
                phoneId = Settings.System.getInt(cr, DUAL_SIM_VIDEO_ID_KEY, PHONE_ID_INVALID);
                imsi = Settings.System.getString(cr, DUAL_SIM_VIDEO_IMSI_KEY);
                break;
            default:
                break;
        }

        Log.d(TAG, "getDefaultSim :phoneId" + phoneId);

        int iccCount =0;//count card numbers
        int avtivedPhone=-1;//find available card
        for (int i = 0; i < getPhoneCount(); ++i) {
            TelephonyManager tmp = getDefault(i);
            if (tmp != null && tmp.hasIccCard()) {
                iccCount++;
                avtivedPhone=i;
            }
        }
        if (PHONE_ID_INVALID == phoneId&& iccCount > 1) {
            return PHONE_ID_INVALID;
        }
        if (iccCount == 1) {
            phoneId = avtivedPhone;
            TelephonyManager.setDefaultSim(mContext, TelephonyManager.MODE_MMS, phoneId);
            TelephonyManager.setDefaultSim(mContext, TelephonyManager.MODE_TEL, phoneId);
            TelephonyManager.setDefaultSim(mContext, TelephonyManager.MODE_VTEL, phoneId);
        }
        Log.d(TAG, "getDefaultSim :avtivedPhone " + phoneId);

        return phoneId;
    }
}

