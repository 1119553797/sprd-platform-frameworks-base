/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.sim;

import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.provider.Settings.System;
import android.provider.Telephony.Intents;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class SimManagerService extends ISimManager.Stub {

    private static final String TAG = "SimManagerService";

    public static final String PREFS_NAME = "sim.detail.ui.info";

    public static final String SIM_CHANGED = "sim";

    public static final String SIM_PHONE_ID = "phone_id";

    public static final String SIM_NAME = "name";

    public static final String SIM_ICC_ID = "icc_id";

    public static final String SIM_COLOR_INDEX = "color_index";

    public static final String SIM_COUNT = "count";

    private final Context mContext;

    private Map<String, Sim> mSimCache = new HashMap<String, Sim>();

    private Map<Integer, Sim> mSimCacheByPhoneId = new TreeMap<Integer, Sim>();

    Set<Integer> mUsedColors = new HashSet<Integer>();

    private SimLoadedReceiver mReceiver;

    private final SharedPreferences mPreferences;

    private int mSimCount = 0;

    private static final Intent SIMS_CHANGED_INTENT;
    static {
        SIMS_CHANGED_INTENT = new Intent(SimManager.INSERT_SIMS_CHANGED_ACTION);
        SIMS_CHANGED_INTENT.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
    }

    public SimManagerService(Context context) {
        Context temp = null;
        try {
            temp = context.createPackageContext("com.android.settings", Context.CONTEXT_IGNORE_SECURITY);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        };
  
        mContext = temp;
        mReceiver = new SimLoadedReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_SIM_ACTIVED_STATE);
        filter.addAction(Intents.SPN_STRINGS_UPDATED_ACTION);
        mContext.registerReceiver(mReceiver, filter);

        mPreferences = mContext.getSharedPreferences(PREFS_NAME, 0);
        mSimCount = mPreferences.getInt(SIM_COUNT, 0);

        Log.i(TAG, "mSimCount:" + mSimCount);

        for (int i = 1; i <= mSimCount; i++) {
            String iccId = mPreferences.getString(SIM_ICC_ID + i, "");
            String name = mPreferences.getString(SIM_NAME + i, "");
            int color = mPreferences.getInt(SIM_COLOR_INDEX + i, 0);

            // set serial num
            Sim sim = new Sim(-1, iccId, name, color);
            sim.setSerialNum(i);
            mSimCache.put(iccId, sim);

            Log.i(TAG, "1--sim:" + sim);
        }
    }

    private void sendSimsChangedBroadcast(ArrayList<Sim> changedSims) {

        Log.i(TAG, "sendSimsChangedBroadcast android.sim.INSERT_SIMS_CHANGED, changedSims:" + changedSims);
        SIMS_CHANGED_INTENT.putParcelableArrayListExtra(SIM_CHANGED, changedSims);
        mContext.sendBroadcast(SIMS_CHANGED_INTENT);
    }

    private class SimLoadedReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {

            int phoneId = intent.getIntExtra(IccCard.INTENT_KEY_PHONE_ID, 0);
            TelephonyManager telManager = (TelephonyManager) context.getSystemService(PhoneFactory .getServiceName(Context.TELEPHONY_SERVICE, phoneId));

            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {
                String state = intent.getStringExtra(IccCard.INTENT_KEY_ICC_STATE);

                Log.i(TAG, "ACTION_SIM_STATE_CHANGED:phoneId=" + phoneId + ", state=" + state);
                if (!IccCard.INTENT_VALUE_ICC_LOADED.equals(state)) {
                    Log.i(TAG, "sim didn't loaded");
                    return;
                }
            } else if (intent.getAction().startsWith(Intents.SPN_STRINGS_UPDATED_ACTION)) {

                Sim sim = mSimCacheByPhoneId.get(phoneId);

                if (sim == null || !TextUtils.isEmpty(sim.getName())) {
                    return;
                }

                String operator = telManager.getNetworkOperatorName();
                if (TextUtils.isEmpty(operator)) {
                    Log.i(TAG, "Can not get the operator info now, and the operator is " + operator);
                    return;
                }

                String name = "";
                int serial = sim.getSerialNum();
                if (serial < 10) {
                    name = operator + " 0" + serial;
                } else {
                    name = operator + " " + serial;
                }
                SharedPreferences.Editor editor = mPreferences.edit();
                editor.putString(SIM_NAME + serial, name);
                editor.commit();
                sim.setName(name);
                return;
            }

            Log.i(TAG, "onReceive:" + intent.getAction());
            Log.i(TAG, "phoneId=" + phoneId);

            if (!telManager.hasIccCard()) {
                Log.i(TAG, "slot " + phoneId + " has no card");
                return;
            }
            String iccId = telManager.getSimIccId(phoneId);

            if (TextUtils.isEmpty(iccId)) {
                Log.i(TAG, "iccId=" + iccId);
                return;
            }
            if (mSimCache.containsKey(iccId)) {
                Sim sim = mSimCache.get(iccId);
                sim.setPhoneId(phoneId);

                mSimCacheByPhoneId.put(phoneId, sim);
                mUsedColors.add(sim.getColorIndex());
                Log.i(TAG, "2--sim:" + sim);
            } else {
                mSimCount++;

                // get default name
                String operator = telManager.getSimOperatorName();
                if (TextUtils.isEmpty(operator)) {
                    operator = telManager.getNetworkOperatorName();
                }
                String name = "";
                if (!TextUtils.isEmpty(operator)) {
                    if (mSimCount < 10) {
                        name = operator + " 0" + mSimCount;
                    } else {
                        name = operator + " " + mSimCount;
                    }
                }

                // get the default color index
                int colorIndex = 0;
                for (int i = 0; i < SimManager.COLORS.length; i++) {
                    if (!mUsedColors.contains(i)) {
                        colorIndex = i;
                        break;
                    }
                }
                SharedPreferences.Editor editor = mPreferences.edit();
                editor.putInt(SIM_COUNT, mSimCount);
                editor.putInt(SIM_PHONE_ID + mSimCount, phoneId);
                editor.putString(SIM_ICC_ID + mSimCount, iccId);
                editor.putString(SIM_NAME + mSimCount, name);
                editor.putInt(SIM_COLOR_INDEX + mSimCount, colorIndex);
                editor.commit();
                Sim sim = new Sim(phoneId, iccId, name, colorIndex);
                sim.setSerialNum(mSimCount);
                mSimCache.put(iccId, sim);
                mSimCacheByPhoneId.put(phoneId, sim);
                mUsedColors.add(sim.getColorIndex());
                Log.i(TAG, "3--sim:" + sim);
            }
            // notify the status bar to update the sim color
            ArrayList<Sim> insertSims = new ArrayList<Sim>();
            insertSims.add(mSimCacheByPhoneId.get(phoneId));
            sendSimsChangedBroadcast(insertSims);
        }
    }

    public String getName(int phoneId) {
        Sim sim = mSimCacheByPhoneId.get(phoneId);
        if (sim == null) {
            return "";
        } else {
            return sim.getName();
        }
    }

    public void setName(int phoneId, String name) {
        Sim sim = mSimCacheByPhoneId.get(phoneId);
        if (sim != null) {
            sim.setName(name);
        }
        // update preference
        int serialNum = sim.getSerialNum();
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putString(SIM_NAME + serialNum, name);
        editor.commit();

        // tell the app to update the changed sims
        ArrayList<Sim> changedSims = new ArrayList<Sim>();
        changedSims.add(sim);
        sendSimsChangedBroadcast(changedSims);

    }

    public int getColor(int phoneId) {
        Sim sim = mSimCacheByPhoneId.get(phoneId);
        if (sim == null) {
            return 0;
        } else {
            return sim.getColor();
        }
    }

    public int getColorIndex(int phoneId) {
        Sim sim = mSimCacheByPhoneId.get(phoneId);
        if (sim == null) {
            return 0;
        } else {
            return sim.getColorIndex();
        }
    }

    public void setColorIndex(int phoneId, int colorIndex) {
        Sim sim = mSimCacheByPhoneId.get(phoneId);
        if (sim != null) {
            sim.setColorIndex(colorIndex);
        }
        // update preference
        int serialNum = sim.getSerialNum();
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putInt(SIM_COLOR_INDEX + serialNum, colorIndex);
        editor.commit();

        // tell the app to update the changed sims
        ArrayList<Sim> changedSims = new ArrayList<Sim>();
        changedSims.add(sim);
        sendSimsChangedBroadcast(changedSims);

    }

    public String getIccId(int phoneId) {
        Sim sim = mSimCacheByPhoneId.get(phoneId);
        if (sim == null) {
            return "";
        } else {
            return sim.getIccId();
        }
    }

    /**
     * @param phoneId
     * @return the SIM object whose phone id is phoneId
     */
    public Sim getSimById(int phoneId) {
        return mSimCacheByPhoneId.get(phoneId);
    }

    /**
     * @param iccId
     * @return the SIM object whose icc id is iccId
     */
    public Sim getSimByIccId(String iccId) {
        return mSimCache.get(iccId);
    }

    // return the SIMs which are active
    public Sim[] getActiveSims() {
        int len = mSimCacheByPhoneId.size();
        Sim[] sims = new Sim[len];

        Set<Integer> phoneIdSet = mSimCacheByPhoneId.keySet();
        int activeCount = 0;
        Sim sim = null;
        for (Integer phoneId : phoneIdSet) {
            sim = mSimCacheByPhoneId.get(phoneId);
            if (null == sim) continue;

            boolean isSimEnabled = System.getInt(mContext.getContentResolver(),
                    PhoneFactory.getSetting(System.SIM_STANDBY, phoneId), 1) == 1;
            if (isSimEnabled) {
                sims[activeCount++] = sim;
            }
            Log.i(TAG, "getActiveSims, the SIM in slot " + phoneId + " is " + sim);
        }

        Sim[] retSims = new Sim[activeCount];
        for (int i = 0; i < activeCount; i++) {
            retSims[i] = sims[i];
        }
        return retSims;
    }

    // return the SIMs which are in the slots
    public Sim[] getSims() {
        int len = mSimCacheByPhoneId.size();
        Sim[] retSims = new Sim[len];
        Set<Integer> phoneIdSet = mSimCacheByPhoneId.keySet();
        int i = 0;
        Sim sim = null;
        for (Integer phoneId : phoneIdSet) {
            sim = mSimCacheByPhoneId.get(phoneId);
            if (null == sim) continue;

            retSims[i++] = sim;
            Log.i(TAG, "getSims, the SIM in slot " + phoneId + " is " + sim);
        }
        return retSims;
    }

    // return all of the SIMs which were used in the phone
    public Sim[] getAllSims() {
        int len = mSimCache.size();
        Sim[] retSims = new Sim[len];
        Set<String> iccIdSet = mSimCache.keySet();
        int i = 0;
        Sim sim = null;
        for (String iccId : iccIdSet) {
            sim = mSimCache.get(iccId);
            if (null == sim) continue;

            retSims[i++] = sim;
            Log.i(TAG, "getAllSims, the SIM with icc id " + iccId + " is " + sim);
        }
        return retSims;
    }

}
