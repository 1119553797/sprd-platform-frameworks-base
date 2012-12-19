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

import com.android.internal.telephony.Phone;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Slog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Schedule the feature request from different SIM on Multi-Sim Multi-Standby System
 * Should not use on Multi-Sim Multi-Active System
 *
 * {@hide}
 */
public class MsmsFeatureManager {

    private static final String TAG = "MsmsFeatureManager";

    /**
     * Used to notice when the calling process dies so we can self-expire
     *
     * Also used to know if the process has cleaned up after itself when
     * our auto-expire timer goes off.  The timer has a link to an object.
     *
     */
    private class FeatureUser implements IBinder.DeathRecipient, Comparable<FeatureUser> {
        int mNetworkType;
        String mFeature;
        IBinder mBinder;
        int mPid;
        int mUid;
        int mPriority;
        int mPhoneId;
        boolean mIsMainSimFeature;

        FeatureUser(int type, String feature, IBinder binder, int pid, int uid, int phoneId, boolean isMainSimFeature) {
            super();
            mNetworkType = type;
            mFeature = feature;
            mBinder = binder;
            mPid = pid;
            mUid = uid;

            mPriority = getPriority(feature);
            mPhoneId = phoneId;
            mIsMainSimFeature = isMainSimFeature;

            try {
                mBinder.linkToDeath(this, 0);
            } catch (RemoteException e) {
                binderDied();
            }
        }

        /**
         * Todo: priority to be extended
         * the lower the figure the higher the priority
         */
        private int getPriority(String feature) {
            if (feature.indexOf(Phone.FEATURE_ENABLE_MMS) != -1) {
                return 0; //high
            } else if (feature.indexOf(Phone.FEATURE_ENABLE_WAP) != -1) {
                return 1; //mid
            } else {
                return 2; //low
            }
        }

        void unlinkDeathRecipient() {
            try {
                mBinder.unlinkToDeath(this, 0);
            } catch (NoSuchElementException e) {
                Slog.e(TAG, "NoSuchElementException while unlinkToDeath");
            } catch (Exception e) {
                Slog.e(TAG, "Unexcepted Exception while unlinkToDeath");
            }
        }

        public void binderDied() {
            Slog.d(TAG, "MsmsFeatureManager FeatureUser binderDied(" +
                    mNetworkType + ", " + mFeature + ", " + mBinder + ")");
            stopUsingFeature(mNetworkType, mFeature, mPid, mUid, mPhoneId);
        }

        public String toString() {
            return "FeatureUser(" + mNetworkType + "," + mFeature + "," + mPid + "," + mUid + ")";
        }

        @Override
        public int compareTo(FeatureUser another) {
            return mPriority - another.mPriority;
        }

        public boolean equals(FeatureUser u) {
            return mNetworkType == u.mNetworkType && mFeature == u.mFeature && mUid == u.mUid && mPid == u.mPid;
        }
    }

    private List<FeatureUser>[] mRunQueue;
    private int mActiveSim;
    private Context mContext;
    private int mPhoneCount;

    private static MsmsFeatureManager mInstance = null;

    private MsmsFeatureManager(int phoneCount, Context context) {
        mPhoneCount = phoneCount;
        mRunQueue = new ArrayList[phoneCount];
        for (int i = 0; i < phoneCount; i++) {
            mRunQueue[i] = new ArrayList<FeatureUser>();
        }
        mActiveSim = -1;
        mContext = context;

        // since it is used in ConnectivityService, no disposer is provided
        ContentResolver cr = context.getContentResolver();
        cr.registerContentObserver(
                Settings.System.getUriFor(Settings.System.MULTI_SIM_DATA_CALL), true,
                mDefaultDataPhoneIdObserver);
    }

    public static MsmsFeatureManager getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new MsmsFeatureManager(TelephonyManager.getPhoneCount(), context);
        }
        return mInstance;
    }

    private ContentObserver mDefaultDataPhoneIdObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            defaultDataChanged();
        }
    };

    private synchronized void defaultDataChanged() {
        int defaultDataPhoneId = TelephonyManager.getDefaultDataPhoneId(mContext);

        Slog.d(TAG, "defaultDataChanged:" + defaultDataPhoneId);
        for (int phoneId = 0; phoneId < mPhoneCount; phoneId++) {
            if (phoneId == defaultDataPhoneId) continue;
            for (int i = 0; i < mRunQueue[phoneId].size(); i++) {
                FeatureUser u = mRunQueue[phoneId].get(i);
                if (u.mIsMainSimFeature) {
                    mRunQueue[phoneId].remove(i);
                    mRunQueue[defaultDataPhoneId].add(u);
                }
            }
            Slog.d(TAG, "sorted mRunQueue[" + phoneId + "] " + mRunQueue[phoneId]);
        }
        Collections.sort(mRunQueue[defaultDataPhoneId]);
        Slog.d(TAG, "sorted mRunQueue[" + defaultDataPhoneId + "] " + mRunQueue[defaultDataPhoneId]);
    }

    /**
     * Check weather a feature can be started now
     *
     * @param networkType
     * @param feature
     * @param binder
     * @param pid
     * @param uid
     * @param phoneId
     * @param isMainSimFeature
     * @return true: can startUsing current feature; false: pending current feature
     */
    public synchronized boolean tryStartUsingFeature(int networkType, String feature,
            IBinder binder, int pid, int uid, int phoneId, boolean isMainSimFeature) {
        Slog.d(TAG, "tryStartUsingFeature(" + networkType + ", " + feature + ")");
        // if the feature is invalid, return true and let ConnectivityService
        // to handle
        if (phoneId < 0 || phoneId >= mPhoneCount) {
            return true;
        }
        for (int i = 0; i < mRunQueue[phoneId].size(); i++) {
            FeatureUser u = (FeatureUser) mRunQueue[phoneId].get(i);
            if (uid == u.mUid && pid == u.mPid && networkType == u.mNetworkType
                    && TextUtils.equals(feature, u.mFeature)) {
                Slog.d(TAG, "tryStartUsingFeature duplicate is found");
                Slog.d(TAG, "sorted mRunQueue[" + phoneId + "] " + mRunQueue[phoneId]);
                return mActiveSim == phoneId;
            }
        }

        FeatureUser f = new FeatureUser(networkType, feature, binder, pid, uid, phoneId, isMainSimFeature);
        mRunQueue[phoneId].add(f);
        Collections.sort(mRunQueue[phoneId]);
        Slog.d(TAG, "sorted mRunQueue[" + phoneId + "] " + mRunQueue[phoneId]);

        // call feature is previous pending
        if (schedule()) {
            // TODO: evoke may causing other problem, such as incorrect evoke
            // after main-sim switch. However, evoke is important when application
            // does not retry startUsing.
            //evokeFeatureExcept(f);
        }
        return mActiveSim == phoneId;
    }

    /**
     * Stop using the feature, remove it from the runqueue
     *
     * @param networkType
     * @param feature
     * @param pid
     * @param uid
     * @param phoneId
     */
    public synchronized void stopUsingFeature(int networkType, String feature, int pid, int uid,
            int phoneId) {
        Slog.d(TAG, "stopUsingFeature(" + networkType + ", " + feature + ")");
        for (int i = 0; i < mRunQueue[phoneId].size(); i++) {
            FeatureUser u = (FeatureUser) mRunQueue[phoneId].get(i);
            if (uid == u.mUid && pid == u.mPid && networkType == u.mNetworkType
                    && TextUtils.equals(feature, u.mFeature)) {
                u.unlinkDeathRecipient();
                mRunQueue[phoneId].remove(i);
                break;
            }
        }
        Slog.d(TAG, "sorted mRunQueue[" + phoneId + "] " + mRunQueue[phoneId]);

        // call feature is previous pending
        if (schedule()) {
            // TODO: evoke may causing other problem, such as incorrect evoke
            // after main-sim switch. However, evoke is important when application
            // dose not retry startUsing.
            //evokeFeatureExcept(null);
        }
    }

    private synchronized void evokeFeatureExcept(FeatureUser f) {
        int minSim = mActiveSim;
        if (minSim != -1 && !mRunQueue[minSim].isEmpty()) {
            IBinder b = ServiceManager.getService(Context.CONNECTIVITY_SERVICE);
            IConnectivityManager service = IConnectivityManager.Stub.asInterface(b);
            Slog.d(TAG, "evoke pending:" + mRunQueue[minSim]);
            for (int i = 0; i < mRunQueue[minSim].size(); i++) {
                FeatureUser u = (FeatureUser) mRunQueue[minSim].get(i);
                if (u == f) continue;
                try {
                    service.startUsingNetworkFeature(u.mNetworkType, u.mFeature, u.mBinder);
                } catch (Exception e) {
                }
            }
        }
    }

    /**
     * Schedule the runqueue
     *
     * the lower the figure the higher the priority
     *
     * @return true if active sim is changed
     */
    private synchronized boolean schedule() {
        int oldActiveSim = mActiveSim;
        int minPriority = 10;
        int minSim = -1;
        if (mActiveSim != -1 && !mRunQueue[mActiveSim].isEmpty()) {
            minPriority = ((FeatureUser) mRunQueue[mActiveSim].get(0)).mPriority;
            minSim = mActiveSim;
        }
        for (int i = 0; i < mPhoneCount; i++) {
            if (!mRunQueue[i].isEmpty()) {
                int priority = ((FeatureUser) mRunQueue[i].get(0)).mPriority;
                if (priority < minPriority) {
                    minPriority = priority;
                    minSim = i;
                }
            }
        }
        Slog.d(TAG, "schedule:" + oldActiveSim + "=>" + minSim);
        mActiveSim = minSim;
        return mActiveSim != oldActiveSim;
    }

}
