// bug 205246 begin
package com.android.internal.telephony;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import android.util.Log;


public class DupSmsFilterExt  {

    private static String TAG = "DupSmsFilterExt";

    protected static final String KEY_DUP_SMS_KEEP_PERIOD = "dev.dup_sms_keep_period";
    protected static final long DEFAULT_DUP_SMS_KEEP_PERIOD = 5 * 60 * 1000;
    protected static final int EVENT_CLEAR_SMS_LIST = 0x01;

    protected Context mContext = null;

    protected HashMap<Long, byte[]> mSmsMap = null;

    public DupSmsFilterExt(Context context) {
        Log.d(TAG, "call constructor");
        if(context == null) {
            Log.d(TAG, "FAIL! context is null");
            return;
        }

        mContext = context;
        mSmsMap = new HashMap<Long, byte[]>();

    }

    public boolean containDupSms(byte[] pdu) {
        Log.d(TAG, "call containDupSms");

        /* Test SIM card should not use the duplicate mechanism
        if (isTestIccCard()) {
            return false;
        }*/

        removeExpiredItem();
        Iterator<Map.Entry<Long, byte[]>> iter = mSmsMap.entrySet().iterator();
        while(iter.hasNext()) {
            Map.Entry<Long, byte[]> entry = (Map.Entry<Long, byte[]>)iter.next();
            if (isDupSms(pdu, entry.getValue())) {
                return true;
            }
        }
        synchronized(mSmsMap) {
            mSmsMap.put(System.currentTimeMillis(), pdu);
        }
        return false;
    }

    protected boolean isDupSms(byte[] newPdu, byte[] oldPdu) {
        Log.d(TAG, "call isDupSms");
        if (Arrays.equals(newPdu, oldPdu)) {
            Log.d(TAG, "find a duplicated sms");
            return true;
        }

        return false;
    }

    synchronized private void removeExpiredItem() {
        Log.d(TAG, "call removeExpiredItem");
        long delayedPeriod = SystemProperties.getLong(
                KEY_DUP_SMS_KEEP_PERIOD,
                DEFAULT_DUP_SMS_KEEP_PERIOD);
        Iterator<Map.Entry<Long, byte[]>> iter = mSmsMap.entrySet().iterator();
        while(iter.hasNext()) {
            Map.Entry<Long, byte[]> entry = (Map.Entry<Long, byte[]>)iter.next();
            if (entry.getKey() < System.currentTimeMillis() - delayedPeriod) {
                iter.remove();
            }
        }
        Log.d(TAG, "mSmsMap has " + mSmsMap.size() + " items after removeExpiredItem");
    }

/*
    private boolean isTestIccCard() {
        int ret = -1;
        if (mSimId == PhoneConstants.GEMINI_SIM_1) {
            ret = SystemProperties.getInt("gsm.sim.ril.testsim", -1);
        } else if (mSimId == PhoneConstants.GEMINI_SIM_2) {
            ret = SystemProperties.getInt("gsm.sim.ril.testsim.2", -1);
        }
        Log.d(TAG, "Sim id: " + mSimId + "isTestIccCard: " + ret);
        return (ret == 1);
    }
*/
}
// bug 205246 end
