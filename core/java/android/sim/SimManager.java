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

import com.google.android.collect.Maps;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.SQLException;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class SimManager {
    private static final String TAG = "SimManager";

    private final Context mContext;

    private final ISimManager mService;

    private final Handler mMainHandler;

    private static SimManager sInstance;

    public static final int ABSENT_SIM_COLOR = 0xFF808080;

    public static final int[] COLORS = {
            0xFFFF383A, 0xFFD9BE97, 0xFFA372D9, 0xFFFF49A7, 0xFFFFAF21, 0xFF49C737, 0xFF3ADED5,
            0xFF4274DB
    };

    public static final int[] COLORS_IMAGES = {
            com.android.internal.R.drawable.sim_color_1_sprd,
            com.android.internal.R.drawable.sim_color_2_sprd,
            com.android.internal.R.drawable.sim_color_3_sprd,
            com.android.internal.R.drawable.sim_color_4_sprd,
            com.android.internal.R.drawable.sim_color_5_sprd,
            com.android.internal.R.drawable.sim_color_6_sprd,
            com.android.internal.R.drawable.sim_color_7_sprd,
            com.android.internal.R.drawable.sim_color_8_sprd
    };

    /**
     * Action sent as a broadcast Intent by the SimsService when a sim is
     * changed.
     */
    public static final String INSERT_SIMS_CHANGED_ACTION = "android.sim.INSERT_SIMS_CHANGED";

    public SimManager(Context context, ISimManager service) {
        mContext = context;
        mService = service;
        mMainHandler = new Handler(mContext.getMainLooper());
    }

    /**
     * Gets a SimManager instance associated with a Context.
     * 
     * @param context The {@link Context} to use when necessary
     * @return A {@link SimManager} instance
     */
    public static SimManager get(Context context) {
        if (context == null)
            throw new IllegalArgumentException("context is null");
        if (sInstance == null) {
            sInstance = new SimManager(context, ISimManager.Stub.asInterface(ServiceManager
                    .getService("sim_manager")));
        }
        return sInstance;
    }

    private final HashMap<OnSimsUpdateListener, Handler> mSimsUpdatedListeners = Maps.newHashMap();

    /**
     * BroadcastReceiver that listens for the INSERT_SIMS_CHANGED_ACTION intent
     * so that it can read the updated list of sims and send them to the
     * listener in mSimsUpdatedListeners.
     */
    private final BroadcastReceiver mSimsChangedBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(final Context context, final Intent intent) {
            final Sim[] sims = getSims();
            // send the result to the listeners
            synchronized (mSimsUpdatedListeners) {
                for (Map.Entry<OnSimsUpdateListener, Handler> entry : mSimsUpdatedListeners
                        .entrySet()) {
                    postToHandler(entry.getValue(), entry.getKey(), sims);
                }
            }
        }
    };

    /**
     * @return the SIM objects  which are in the slots
     */
    public Sim[] getSims() {
        try {
            return mService.getSims();
        } catch (RemoteException e) {
            // won't ever happen
            throw new RuntimeException(e);
        }
    }

    /**
     * @return the SIMs which are active
     */
    public Sim[] getActiveSims() {
        try {
            return mService.getActiveSims();
        } catch (RemoteException e) {
            // won't ever happen
            throw new RuntimeException(e);
        }
    }

    /**
     * @return all of the SIM objects which were used in the phone
     */
    public Sim[] getAllSims() {
        try {
            return mService.getAllSims();
        } catch (RemoteException e) {
            // won't ever happen
            throw new RuntimeException(e);
        }
    }

    /**
     * @param phoneId
     * @return the SIM object whose phone id is phoneId
     */
    public Sim getSimById(int phoneId) {

        try {
            return mService.getSimById(phoneId);
        } catch (RemoteException e) {
            // won't ever happen
            throw new RuntimeException(e);
        }
    }

    /**
     * @param iccId
     * @return the SIM object whose icc id is iccId
     */
    public Sim getSimByIccId(String  iccId) {

        try {
            return mService.getSimByIccId(iccId);
        } catch (RemoteException e) {
            // won't ever happen
            throw new RuntimeException(e);
        }
    }

    public String getName(int phoneId) {
        try {
            return mService.getName(phoneId);
        } catch (RemoteException e) {
            // won't ever happen
            throw new RuntimeException(e);
        }
    }

    public void setName(int phoneId, String name) {
        try {
            mService.setName(phoneId, name);
        } catch (RemoteException e) {
            // won't ever happen
            throw new RuntimeException(e);
        }

    }

    public int getColor(int phoneId) {
        try {
            return mService.getColor(phoneId);
        } catch (RemoteException e) {
            // won't ever happen
            throw new RuntimeException(e);
        }
    }

    public int getColorIndex(int phoneId) {
        try {
            return mService.getColorIndex(phoneId);
        } catch (RemoteException e) {
            // won't ever happen
            throw new RuntimeException(e);
        }
    }

    public void setColorIndex(int phoneId, int colorIndex) {
        try {
            mService.setColorIndex(phoneId, colorIndex);
        } catch (RemoteException e) {
            // won't ever happen
            throw new RuntimeException(e);
        }

    }    

    public String getIccId(int phoneId) {
        try {
            return mService.getIccId(phoneId);
        } catch (RemoteException e) {
            // won't ever happen
            throw new RuntimeException(e);
        }
    }

    private void postToHandler(Handler handler, final OnSimsUpdateListener listener, final Sim[] sims) {
        final Sim[] simsCopy = new Sim[sims.length];
        // send a copy to make sure that one doesn't
        // change what another sees
        System.arraycopy(sims, 0, simsCopy, 0, simsCopy.length);
        handler = (handler == null) ? mMainHandler : handler;
        handler.post(new Runnable() {
            public void run() {
                try {
                    listener.onSimUpdated(simsCopy);
                } catch (SQLException e) {
                    // Better luck next time. If the problem was disk-full,
                    // the STORAGE_OK intent will re-trigger the update.
                    Log.e(TAG, "Can't update sims", e);
                }
            }
        });
    }

    /**
     * An interface that contains the callback used by the SimManager
     */
    public interface OnSimsUpdateListener {
        /**
         * This invoked whenever the sim set changes.
         * 
         * @param sims the current sims
         */
        void onSimUpdated(Sim[] sims);
    }

    /**
     * Adds an {@link OnSimsUpdateListener} to this instance of the
     * {@link SimManager}. This listener will be notified whenever the list
     * of sims on the device changes.
     * <p>
     * As long as this listener is present, the SimManager instance will not
     * be garbage-collected, and neither will the {@link Context} used to
     * retrieve it, which may be a large Activity instance. To avoid memory
     * leaks, you must remove this listener before then. Normally listeners are
     * added in an Activity or Service's {@link Activity#onCreate} and removed
     * in {@link Activity#onDestroy}.
     * <p>
     * It is safe to call this method from the main thread.
     * <p>
     * No permission is required to call this method.
     * 
     * @param listener The listener to send notifications to
     * @param handler {@link Handler} identifying the thread to use for
     *            notifications, null for the main thread
     * @param updateImmediately If true, the listener will be invoked (on the
     *            handler thread) right away with the current account list
     * @throws IllegalArgumentException if listener is null
     * @throws IllegalStateException if listener was already added
     */
    public void addOnSimsUpdatedListener(final OnSimsUpdateListener listener,
            Handler handler, boolean updateImmediately) {
        if (listener == null) {
            throw new IllegalArgumentException("the listener is null");
        }
        synchronized (mSimsUpdatedListeners) {
            if (mSimsUpdatedListeners.containsKey(listener)) {
                throw new IllegalStateException("this listener is already added");
            }
            final boolean wasEmpty = mSimsUpdatedListeners.isEmpty();

            mSimsUpdatedListeners.put(listener, handler);

            if (wasEmpty) {
                // Register a broadcast receiver to monitor account changes
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(INSERT_SIMS_CHANGED_ACTION);
                // To recover from disk-full.
                intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_OK);
                mContext.registerReceiver(mSimsChangedBroadcastReceiver, intentFilter);
            }
        }

        if (updateImmediately) {
            postToHandler(handler, listener, getSims());
        }
    }

    /**
     * Removes an {@link OnSimsUpdateListener} previously registered with
     * {@link #addOnSimsUpdatedListener}. The listener will no longer
     * receive notifications of account changes.
     * <p>
     * It is safe to call this method from the main thread.
     * <p>
     * No permission is required to call this method.
     * 
     * @param listener The previously added listener to remove
     * @throws IllegalArgumentException if listener is null
     * @throws IllegalStateException if listener was not already added
     */
    public void removeOnSimsUpdatedListener(OnSimsUpdateListener listener) {
        if (listener == null)
            throw new IllegalArgumentException("listener is null");
        synchronized (mSimsUpdatedListeners) {
            if (!mSimsUpdatedListeners.containsKey(listener)) {
                Log.e(TAG, "Listener was not previously added");
                return;
            }
            mSimsUpdatedListeners.remove(listener);
            if (mSimsUpdatedListeners.isEmpty()) {
                mContext.unregisterReceiver(mSimsChangedBroadcastReceiver);
            }
        }
    }
    
}
