/*
 * SPRD: 
 */

package android.telephony;

import android.os.SystemProperties;

/**
 * @hide
 **/
public class SprdPhoneSupport {
    private static final String TAG = "SprdPhoneSupport";

    public static final int DEFAULT_PHONE_COUNT = 1;
    public static final int DEFAULT_PHONE_ID = 0;

    public static final int MMS_SET_AUTO = 3;
    public static final int DEFAULT_DUAL_SIM_INIT_PHONE_ID = -1;
    public static final int DEFAULT_DUAL_SIM_INIT_MMS_PHONE_ID = MMS_SET_AUTO;

    public static int getPhoneCount() {
        return SystemProperties.getInt("persist.msms.phone_count", DEFAULT_PHONE_COUNT);
    }

    public static int getDefaultPhoneId() {
        return SystemProperties.getInt("persist.msms.phone_default", DEFAULT_PHONE_ID);
    }
    
    public static void setDefaultPhoneId(int phoneId){
        SystemProperties.set("persist.msms.phone_default", String.valueOf(phoneId));
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

    public static String getFeature(String defaultFeature, int phoneId) {
        if (isMultiSim()) {
            if (phoneId == getPhoneCount()) {
                return defaultFeature;
            }
            return defaultFeature + phoneId;
        } else {
            return defaultFeature;
        }
    }

    public static String getProperty(String defaultProperty, int phoneId) {
        if (isMultiSim()) {
            if (phoneId == getPhoneCount()) {
                return defaultProperty;
            }
            return defaultProperty + phoneId;
        } else {
            return defaultProperty;
        }
    }

    public static String getSetting(String defaultSetting, int phoneId) {
        if (isMultiSim()) {
            if (phoneId == getPhoneCount()) {
                return defaultSetting;
            }
            return defaultSetting + phoneId;
        } else {
            return defaultSetting;
        }
    }

    public static String getAction(String defaultAction, int phoneId) {
        if (isMultiSim()) {
            if (phoneId == getPhoneCount()) {
                return defaultAction;
            }
            return defaultAction + phoneId;
        } else {
            return defaultAction;
        }
    }
}
