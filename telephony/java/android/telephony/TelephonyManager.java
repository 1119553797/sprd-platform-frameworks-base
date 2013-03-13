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
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.telephony.IPhoneSubInfo;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.ITelephonyRegistry;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
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

    private static Context sContext;
    private static ITelephonyRegistry sRegistry;
    private int mPhoneId;

    //About default sim card setting for vioce,video,mms
    public static final int MODE_VOICE = 0;
    public static final int MODE_VEDIO = 1;
    public static final int MODE_MMS = 2;
    public static final int PHONE_ID_INVALID = -1;
    private static final String dualCardDefaultPhone = "com.android.dualcard_settings_preferences";
    private static final String simCardFavoritekey = "sim_card_favorite";
    private static final String simCardForwardSettingKey = "sim_forward_setting";
    private static final String simCardFavoriteVoicekey = "sim_card_favorite_voice";
    private static final String simCardFavoriteVideokey = "sim_card_favorite_video";
    private static final String simCardFavoriteMmskey = "sim_card_favorite_mms";
    private static final String sharedActivityName = "com.android.phone";

    /** @hide */
    public TelephonyManager(Context context) {
        if (sContext == null) {
            Context appContext = context.getApplicationContext();
            if (appContext != null) {
                sContext = appContext;
            } else {
                sContext = context;
            }

            sRegistry = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService(
                    "telephony.registry"));
            mPhoneId = PhoneFactory.getPhoneCount();
        }
    }

    /** @hide */
    //private TelephonyManager() {
    public TelephonyManager(Context context, int phoneId) {
        sContext = context;
        sRegistry = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService(
                    PhoneFactory.getServiceName("telephony.registry", phoneId)));
        mPhoneId = phoneId;
    }

    /** @hide */
    private TelephonyManager(int phoneId) {
        mPhoneId = phoneId;
    }

    //private static TelephonyManager sInstance = new TelephonyManager();
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

    /** @hide
    /* @deprecated - use getSystemService as described above */
    public static TelephonyManager getDefault() {
        if (PhoneFactory.isMultiSim()) {
            return sInstance[PhoneFactory.getPhoneCount()];
        } else {
            return sInstance[0];
        }
    }

    /** @hide */
    public static TelephonyManager getDefault(int phoneId) {
        if (phoneId >= PhoneFactory.getPhoneCount()) {
            throw new IllegalArgumentException("phoneId exceeds phoneCount");
        }
        return sInstance[phoneId];
    }

    /** {@hide} */
    public static TelephonyManager from(Context context) {
        return (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
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
    /** Phone is via SIP. */
    public static final int PHONE_TYPE_SIP = Phone.PHONE_TYPE_SIP;

    /**
     * Returns the current phone type.
     * TODO: This is a last minute change and hence hidden.
     *
     * @see #PHONE_TYPE_NONE
     * @see #PHONE_TYPE_GSM
     * @see #PHONE_TYPE_CDMA
     * @see #PHONE_TYPE_SIP
     *
     * {@hide}
     */
    public int getCurrentPhoneType() {
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

    /**
     * Returns a constant indicating the device phone type.  This
     * indicates the type of radio used to transmit voice calls.
     *
     * @see #PHONE_TYPE_NONE
     * @see #PHONE_TYPE_GSM
     * @see #PHONE_TYPE_CDMA
     * @see #PHONE_TYPE_SIP
     */
    public int getPhoneType() {
        if (!isVoiceCapable()) {
            return PHONE_TYPE_NONE;
        }
        return getCurrentPhoneType();
    }

    private int getPhoneTypeFromProperty() {
        String currentActivePhoneProperty = PhoneFactory.getProperty(
                TelephonyProperties.CURRENT_ACTIVE_PHONE, mPhoneId);
        int type =
            //SystemProperties.getInt(TelephonyProperties.CURRENT_ACTIVE_PHONE,
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
        //return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ALPHA);
        String operatorAlphaProperty = PhoneFactory.getProperty(
                TelephonyProperties.PROPERTY_OPERATOR_ALPHA, mPhoneId);
        return SystemProperties.get(operatorAlphaProperty);
    }

    public String getSimIccId(int phoneId) {
        String iccidProperty = PhoneFactory.getProperty(TelephonyProperties.PROPERTY_SIM_ICCID,
                phoneId);
        return SystemProperties.get(iccidProperty);
    }

    /**
     * Returns the numeric name (MCC+MNC) of current registered operator.
     * <p>
     * Availability: Only when user is registered to a network. Result may be
     * unreliable on CDMA networks (use {@link #getPhoneType()} to determine if
     * on a CDMA network).
     */
    public String getNetworkOperator() {
        //return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC);
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
        //return "true".equals(SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ISROAMING));
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
        //return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY);
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
    /** Current network is LTE */
    public static final int NETWORK_TYPE_LTE = 13;
    /** Current network is eHRPD */
    public static final int NETWORK_TYPE_EHRPD = 14;
    /** Current network is HSPA+ */
    public static final int NETWORK_TYPE_HSPAP = 15;

    /**
     * Returns a constant indicating the radio technology (network type)
     * currently in use on the device for data transmission.
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
     * @see #NETWORK_TYPE_IDEN
     * @see #NETWORK_TYPE_LTE
     * @see #NETWORK_TYPE_EHRPD
     * @see #NETWORK_TYPE_HSPAP
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

    /** Unknown network class. {@hide} */
    public static final int NETWORK_CLASS_UNKNOWN = 0;
    /** Class of broadly defined "2G" networks. {@hide} */
    public static final int NETWORK_CLASS_2_G = 1;
    /** Class of broadly defined "3G" networks. {@hide} */
    public static final int NETWORK_CLASS_3_G = 2;
    /** Class of broadly defined "4G" networks. {@hide} */
    public static final int NETWORK_CLASS_4_G = 3;

    /**
     * Return general class of network type, such as "3G" or "4G". In cases
     * where classification is contentious, this method is conservative.
     *
     * @hide
     */
    public static int getNetworkClass(int networkType) {
        switch (networkType) {
            case NETWORK_TYPE_GPRS:
            case NETWORK_TYPE_EDGE:
            case NETWORK_TYPE_CDMA:
            case NETWORK_TYPE_1xRTT:
            case NETWORK_TYPE_IDEN:
                return NETWORK_CLASS_2_G;
            case NETWORK_TYPE_UMTS:
            case NETWORK_TYPE_EVDO_0:
            case NETWORK_TYPE_EVDO_A:
            case NETWORK_TYPE_HSDPA:
            case NETWORK_TYPE_HSUPA:
            case NETWORK_TYPE_HSPA:
            case NETWORK_TYPE_EVDO_B:
            case NETWORK_TYPE_EHRPD:
            case NETWORK_TYPE_HSPAP:
                return NETWORK_CLASS_3_G;
            case NETWORK_TYPE_LTE:
                return NETWORK_CLASS_4_G;
            default:
                return NETWORK_CLASS_UNKNOWN;
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
        return getNetworkTypeName(getNetworkType());
    }

    /** {@hide} */
    public static String getNetworkTypeName(int type) {
        switch (type) {
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
            case NETWORK_TYPE_LTE:
                return "LTE";
            case NETWORK_TYPE_EHRPD:
                return "CDMA - eHRPD";
            case NETWORK_TYPE_IDEN:
                return "iDEN";
            case NETWORK_TYPE_HSPAP:
                return "HSPA+";
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
        //String prop = SystemProperties.get(TelephonyProperties.PROPERTY_SIM_STATE);
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
        //return SystemProperties.get(TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC);
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
        //return SystemProperties.get(TelephonyProperties.PROPERTY_ICC_OPERATOR_ALPHA);
        String iccOperatorAlphaProperty = PhoneFactory.getProperty(
                TelephonyProperties.PROPERTY_ICC_OPERATOR_ALPHA, mPhoneId);
        return SystemProperties.get(iccOperatorAlphaProperty);
    }

    /**
     * Returns the ISO country code equivalent for the SIM provider's country code.
     */
    public String getSimCountryIso() {
        //return SystemProperties.get(TelephonyProperties.PROPERTY_ICC_OPERATOR_ISO_COUNTRY);
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

    /**
     * Return if the current radio is LTE on CDMA. This
     * is a tri-state return value as for a period of time
     * the mode may be unknown.
     *
     * @return {@link Phone#LTE_ON_CDMA_UNKNOWN}, {@link Phone#LTE_ON_CDMA_FALSE}
     * or {@link Phone#LTE_ON_CDMA_TRUE}
     *
     * @hide
     */
    public int getLteOnCdmaMode() {
        try {
            return getITelephony().getLteOnCdmaMode();
        } catch (RemoteException ex) {
            // Assume no ICC card if remote exception which shouldn't happen
            return Phone.LTE_ON_CDMA_UNKNOWN;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return Phone.LTE_ON_CDMA_UNKNOWN;
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
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
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
     * Returns the MSISDN string.
     * for a GSM phone. Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     *
     * @hide
     */
    public String getMsisdn() {
        try {
            return getSubscriberInfo().getMsisdn();
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

    /**
     * Returns the IMS private user identity (IMPI) that was loaded from the ISIM.
     * @return the IMPI, or null if not present or not loaded
     * @hide
     */
    public String getIsimImpi() {
        try {
            return getSubscriberInfo().getIsimImpi();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the IMS home network domain name that was loaded from the ISIM.
     * @return the IMS domain name, or null if not present or not loaded
     * @hide
     */
    public String getIsimDomain() {
        try {
            return getSubscriberInfo().getIsimDomain();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the IMS public user identities (IMPU) that were loaded from the ISIM.
     * @return an array of IMPU strings, with one IMPU per string, or null if
     *      not present or not loaded
     * @hide
     */
    public String[] getIsimImpu() {
        try {
            return getSubscriberInfo().getIsimImpu();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    private IPhoneSubInfo getSubscriberInfo() {
        // get it each time because that process crashes a lot
        //return IPhoneSubInfo.Stub.asInterface(ServiceManager.getService("iphonesubinfo"));
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

    /** Data connection state: Unknown.  Used before we know the state.
     * @hide
     */
    public static final int DATA_UNKNOWN        = -1;
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

    /**
     * @hide
     */
    public static final int MODEM_TYPE_GSM = 0;

    /**
     * @hide
     */
    public static final int MODEM_TYPE_TDSCDMA = 1;

    /**
     * @hide
     */
    public static final int MODEM_TYPE_WCDMA = 2;


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

    /**
     * Returns a constant indicating the current data connection state
     * (cellular).
     *
     * @see #DATA_DISCONNECTED
     * @see #DATA_CONNECTING
     * @see #DATA_CONNECTED
     * @see #DATA_SUSPENDED
     */
    public int getDataStatebyApnType(String apnType) {
        try {
            return getITelephony().getDataStatebyApnType(apnType);
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return DATA_DISCONNECTED;
        } catch (NullPointerException ex) {
            return DATA_DISCONNECTED;
        }
    }

    private ITelephony getITelephony() {
        //return ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE));
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
        String pkgForDebug = sContext != null ? sContext.getPackageName() : "<unknown>";
        try {
            Boolean notifyNow = (getITelephony() != null);
            sRegistry.listen(pkgForDebug, listener.callback, events, notifyNow);
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

    /**
     * @return true if the current device is "voice capable".
     * <p>
     * "Voice capable" means that this device supports circuit-switched
     * (i.e. voice) phone calls over the telephony network, and is allowed
     * to display the in-call UI while a cellular voice call is active.
     * This will be false on "data only" devices which can't make voice
     * calls and don't support any in-call UI.
     * <p>
     * Note: the meaning of this flag is subtly different from the
     * PackageManager.FEATURE_TELEPHONY system feature, which is available
     * on any device with a telephony radio, even if the device is
     * data-only.
     *
     * @hide pending API review
     */
    public boolean isVoiceCapable() {
        if (sContext == null) return true;
        return sContext.getResources().getBoolean(
                com.android.internal.R.bool.config_voice_capable);
    }

    /**
     * @return true if the current device supports sms service.
     * <p>
     * If true, this means that the device supports both sending and
     * receiving sms via the telephony network.
     * <p>
     * Note: Voicemail waiting sms, cell broadcasting sms, and MMS are
     *       disabled when device doesn't support sms.
     *
     * @hide pending API review
     */
    public boolean isSmsCapable() {
        if (sContext == null) return true;
        return sContext.getResources().getBoolean(
                com.android.internal.R.bool.config_sms_capable);
    }

   /**
    * @hide
    */
    public static int getPhoneCount(){
        return PhoneFactory.getPhoneCount();
    }

    /**
     * check if is multi sim
     *
     * @return
     */
    public static boolean isMultiSim(){
        return PhoneFactory.isMultiSim();
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
//            Log.d(TAG, "baseband = "+baseBand);
            modemValue =  baseBand.split("\\|")[1];
//            Log.d(TAG, "modemValue = "+modemValue);
            //if(modemValue.equals("sc8805_sp8805")){//fix bug 7294 close
            if ((modemValue.equals("sc8810_modem"))
                || (modemValue.equals("sc8825_modem"))){//fix bug 7294 add
                return MODEM_TYPE_TDSCDMA;
            }else if ((modemValue.equals("sc6810_sp6810"))
                || (modemValue.equals("sc6825_modem"))) {
                return MODEM_TYPE_GSM;
            }else if (modemValue.equals("sc7702_modem")) {
                return MODEM_TYPE_WCDMA;
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
     * Returns all observed cell information of the device.
     *
     * @return List of CellInfo or null if info unavailable.
     *
     * <p>Requires Permission:
     * (@link android.Manifest.permission#ACCESS_COARSE_UPDATES}
     *
     * @hide pending API review
     */
    public List<CellInfo> getAllCellInfo() {
        try {
            return getITelephony().getAllCellInfo();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
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

    /**
     * @hide
     */
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

    /**
     * @hide
     */
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

    /**
     * @hide
     */
    public static final int UNLOCK_PIN   = 0;

    /**
     * @hide
     */
    public static final int UNLOCK_PIN2   = 1;

    /**
     * @hide
     */
    public static final int UNLOCK_PUK   = 2;

    /**
     * @hide
     */
    public static final int UNLOCK_PUK2   = 3;

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

    /**
     * @hide
     */
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

    /**
     * @hide
     */
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
     * @hide
     */
    public int getAdnCachestate() {
        String adnCacheStateProperty = PhoneFactory.getProperty(
                TelephonyProperties.ADNCACHE_LOADED_STATE, mPhoneId);
        return Integer.valueOf(SystemProperties.get(adnCacheStateProperty,"0"));
    }

    /**
     * @hide
     */
    public static boolean setDefaultSim(Context context, int mode, int phoneId) {

        String phoneIdKey = "";
        switch (mode) {
            case MODE_VOICE:
                phoneIdKey = Settings.System.MULTI_SIM_VOICE_CALL;
                break;
            case MODE_VEDIO:
                phoneIdKey = Settings.System.MULTI_SIM_VIDEO_CALL;
                break;
            case MODE_MMS:
                phoneIdKey = Settings.System.MULTI_SIM_MMS;
                break;
            default:
                break;
        }
        Log.d(TAG, "setDefaultSim:phoneIdKey " + phoneIdKey + " phoneId " + phoneId);
        return Settings.System.putInt(context.getContentResolver(),
                phoneIdKey, phoneId);
    }

    /**
     * @hide
     */
    public static int getDefaultSim(Context context, int mode) {
        if (getPhoneCount() == 1) {
            return 0;
        }
        String phoneIdKey = "";
        int phoneId = PHONE_ID_INVALID;
        switch (mode) {
            case MODE_VOICE:
                phoneIdKey = Settings.System.MULTI_SIM_VOICE_CALL;
                phoneId = Settings.System.getInt(context.getContentResolver(),
                        phoneIdKey, PhoneFactory.DEFAULT_DUAL_SIM_INIT_PHONE_ID);
                break;
            case MODE_VEDIO:
                phoneIdKey = Settings.System.MULTI_SIM_VIDEO_CALL;
                phoneId = Settings.System.getInt(context.getContentResolver(),
                        phoneIdKey, PhoneFactory.DEFAULT_DUAL_SIM_INIT_PHONE_ID);
                break;
            case MODE_MMS:
                phoneIdKey = Settings.System.MULTI_SIM_MMS;
                phoneId = Settings.System.getInt(context.getContentResolver(),
                        phoneIdKey, PhoneFactory.DEFAULT_DUAL_SIM_INIT_MMS_PHONE_ID);
                break;
            default:
                break;
        }
        int iccCount = 0;
        int avtivedPhone = PHONE_ID_INVALID;
        for (int i = 0; i < getPhoneCount(); ++i) {
            TelephonyManager tmp = getDefault(i);
            if (tmp != null) {
                if (tmp.hasIccCard()) {
                    iccCount++;
                    avtivedPhone = i;
                } else if (phoneId == i) {
                    phoneId = PHONE_ID_INVALID;
                }
            }
        }
        if (PHONE_ID_INVALID == phoneId && iccCount > 1) {
            return PHONE_ID_INVALID;
        }
        if (iccCount == 1) {
            phoneId = avtivedPhone;
            TelephonyManager.setDefaultSim(context, MODE_MMS, phoneId);
            TelephonyManager.setDefaultSim(context, MODE_VOICE, phoneId);
            TelephonyManager.setDefaultSim(context, MODE_VEDIO, phoneId);
        }
        return phoneId;
    }

    /**
     * @hide
     */
    public static int getConnectionDefaultSim(Context context) {
        return getDefaultDataPhoneId(context);
    }

    /**
     * @hide
     */
    public static boolean setConnectionDefaultSim(Context context, int phoneId) {
        return setDefaultDataPhoneId(context, phoneId);
    }

    /**
     * @hide
     */
    public static int getDefaultDataPhoneId(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.MULTI_SIM_DATA_CALL, PhoneFactory.getDefaultPhoneId());
    }

    /**
     * @hide
     */
    public static boolean setDefaultDataPhoneId(Context context, int phoneId) {
        setPropertyDataPhoneId(phoneId);
        return Settings.System.putInt(context.getContentResolver(),
                Settings.System.MULTI_SIM_DATA_CALL, phoneId);
    }

    /**
     * @hide
     */
    public static void setPropertyDataPhoneId(int phoneId) {
        SystemProperties.set("persist.msms.phone_default", String.valueOf(phoneId));
    }
    /**
     * @hide
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
     * @hide
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

    /**
     * @hide
     */
    public static void setSubscriberDesiredSim(Context context, int mode, int setPhoneId) {
        SharedPreferences settings = getPhoneSetting(context);
        String phoneIdKey = "";

        switch (mode) {
            case MODE_VOICE:
                phoneIdKey = simCardFavoriteVoicekey;
                break;
            case MODE_VEDIO:
                phoneIdKey = simCardFavoriteVideokey;
                break;
            case MODE_MMS:
                phoneIdKey = simCardFavoriteMmskey;
                break;
            default:
                break;
        }
        Log.d(TAG, "setDefaultSim:phoneIdKey " + phoneIdKey + " phoneId " + setPhoneId);

        int DefaultId = settings.getInt(phoneIdKey, PhoneFactory.DEFAULT_DUAL_SIM_INIT_PHONE_ID);
        if (setPhoneId != DefaultId) {
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt(phoneIdKey, setPhoneId);
            editor.commit();
        }
    }

    /**
     * @hide
     */
    public static int getSubscriberDesiredSim(Context context, int mode) {
        SharedPreferences settings = getPhoneSetting(context);
        String phoneIdKey = "";
        int setPhoneId = PhoneFactory.DEFAULT_DUAL_SIM_INIT_PHONE_ID;
        switch (mode) {
            case MODE_VOICE:
                phoneIdKey = simCardFavoriteVoicekey;
                setPhoneId = settings.getInt(phoneIdKey, PhoneFactory.DEFAULT_DUAL_SIM_INIT_PHONE_ID);
                break;
            case MODE_VEDIO:
                phoneIdKey = simCardFavoriteVideokey;
                setPhoneId = settings.getInt(phoneIdKey, PhoneFactory.DEFAULT_DUAL_SIM_INIT_PHONE_ID);
                break;
            case MODE_MMS:
                phoneIdKey = simCardFavoriteMmskey;
                setPhoneId = settings.getInt(phoneIdKey, PhoneFactory.DEFAULT_DUAL_SIM_INIT_MMS_PHONE_ID);
                break;
            default:
                break;
        }
        Log.d(TAG, "getSettingDefaultSim:phoneIdKey " + phoneIdKey);

        return setPhoneId;
    }

    /**
     * @hide
     */
    public static int getCallForwardSetting(Context context,int phoneId,int reason) {
        SharedPreferences settings = getPhoneSetting(context);
        String setKey = simCardForwardSettingKey+"_"+phoneId+"_"+reason;
        return settings.getInt(setKey, -1);
    }

    /**
     * @hide
     */
    public static void setCallForwardSetting(Context context,int phoneId,int value,int reason) {
        SharedPreferences settings = getPhoneSetting(context);
        String setKey = simCardForwardSettingKey+"_"+phoneId+"_"+reason;
        int DefaultValue = settings.getInt(setKey, -1);
        if (value != DefaultValue) {
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt(setKey, value);
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

    /**
     * @hide
     */
    public static boolean isCurrentCard(int phoneId) {
        String operator = SystemProperties.get("ro.operator", "");
        String numeric = SystemProperties
        .get(PhoneFactory.getProperty(TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC, phoneId));
        Log.d("zhaishaohua","isCuccCard called: numeric = " + numeric);
        if ("cucc".equals(operator)) {
            if (numeric.equals("46001") || numeric.equals("46006")) {
                return true;
            } else {
                return false;
            }
        } else if ("cmcc".equals(operator)) {
            if (numeric.equals("46000") || numeric.equals("46002") || numeric.equals("46007")) {
                return true;
            } else {
                return false;
            }
        } else if ("ctcc".equals(operator)) {
            if (numeric.equals("46003")) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

}
