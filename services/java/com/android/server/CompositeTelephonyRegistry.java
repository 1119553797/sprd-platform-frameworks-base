/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.server;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.LinkCapabilities;
import android.net.LinkProperties;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Telephony.Intents;
import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Slog;

import java.util.ArrayList;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.net.NetworkInterface;

import com.android.internal.app.IBatteryStats;
import com.android.internal.telephony.ITelephonyRegistry;
import com.android.internal.telephony.IPhoneStateListener;
import com.android.internal.telephony.DefaultPhoneNotifier;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.TelephonyIntents;
import com.android.server.am.BatteryStatsService;

/**
 * Since phone process can be restarted, this class provides a centralized place
 * that applications can register and be called back from.
 */
class CompositeTelephonyRegistry extends ITelephonyRegistry.Stub {
    private static final String TAG = "CompositeTelephonyRegistry";
    private static final boolean DBG = false;

    private final Context mContext;
    private TelephonyRegistry[] mTelephonyRegistry;


    // we keep a copy of all of the state so we can send it out when folks
    // register for it
    //
    // In these calls we call with the lock held. This is safe becasuse remote
    // calls go through a oneway interface and local calls going through a
    // handler before they get to app code.

    CompositeTelephonyRegistry(Context context, TelephonyRegistry[] telephonyRegistry) {
        mContext = context;
        mTelephonyRegistry = telephonyRegistry;

        // TODO: should receive all telephony broadcast, and broadcast the default ones
    }

    private int getSimplePolicyPhoneId() {
        return PhoneFactory.getDefaultPhoneId();
    }

    public void listen(String pkgForDebug, IPhoneStateListener callback, int events,
            boolean notifyNow) {
        if (events == PhoneStateListener.LISTEN_NONE) {
            for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
                mTelephonyRegistry[i].listen(pkgForDebug, callback, events, notifyNow);
            }
            return;
        }
        if ((events & PhoneStateListener.LISTEN_SERVICE_STATE) != 0) {
            mTelephonyRegistry[getSimplePolicyPhoneId()].listen(pkgForDebug, callback, events, notifyNow);
        }
        if ((events & PhoneStateListener.LISTEN_SIGNAL_STRENGTH) != 0) {
            mTelephonyRegistry[getSimplePolicyPhoneId()].listen(pkgForDebug, callback, events, notifyNow);
        }
        if ((events & PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR) != 0) {
            mTelephonyRegistry[getSimplePolicyPhoneId()].listen(pkgForDebug, callback, events, notifyNow);
        }
        if ((events & PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR) != 0) {
            mTelephonyRegistry[getSimplePolicyPhoneId()].listen(pkgForDebug, callback, events, notifyNow);
        }
        if ((events & PhoneStateListener.LISTEN_CELL_LOCATION) != 0) {
            mTelephonyRegistry[getSimplePolicyPhoneId()].listen(pkgForDebug, callback, events, notifyNow);
        }
        if ((events & PhoneStateListener.LISTEN_CALL_STATE) != 0) {
//            mTelephonyRegistry[getSimplePolicyPhoneId()].listen(pkgForDebug, callback, events, notifyNow);
            for (int i=0; i<PhoneFactory.getPhoneCount(); i++) {
                mTelephonyRegistry[i].listen(pkgForDebug, callback, events, notifyNow);
            }
        }
        if ((events & PhoneStateListener.LISTEN_DATA_CONNECTION_STATE) != 0) {
            mTelephonyRegistry[getSimplePolicyPhoneId()].listen(pkgForDebug, callback, events, notifyNow);
        }
        if ((events & PhoneStateListener.LISTEN_DATA_ACTIVITY) != 0) {
            mTelephonyRegistry[getSimplePolicyPhoneId()].listen(pkgForDebug, callback, events, notifyNow);
        }
        if ((events & PhoneStateListener.LISTEN_SIGNAL_STRENGTHS) != 0) {
            mTelephonyRegistry[getSimplePolicyPhoneId()].listen(pkgForDebug, callback, events, notifyNow);
        }
        if ((events & PhoneStateListener.LISTEN_OTASP_CHANGED) != 0) {
            mTelephonyRegistry[getSimplePolicyPhoneId()].listen(pkgForDebug, callback, events, notifyNow);
        }
    }

    public void notifyCallState(int state, String incomingNumber) {
        Slog.w(TAG, "should not call CompositeTelephonyRegistry.notifyCallState");
    }

    public void notifyServiceState(ServiceState state) {
        Slog.w(TAG, "should not call CompositeTelephonyRegistry.notifyServiceState");
    }

    public void notifySignalStrength(SignalStrength signalStrength) {
        Slog.w(TAG, "should not call CompositeTelephonyRegistry.notifySignalStrength");
    }

    public void notifyMessageWaitingChanged(boolean mwi) {
        Slog.w(TAG, "should not call CompositeTelephonyRegistry.notifyMessageWaitingChanged");
    }

    public void notifyCallForwardingChanged(boolean cfi) {
        Slog.w(TAG, "should not call CompositeTelephonyRegistry.notifyCallForwardingChanged");
    }

    public void notifyCallForwardingChangedByServiceClass(boolean cfi, int sc){
        Slog.w(TAG, "should not call CompositeTelephonyRegistry.notifyCallForwardingChangedByServiceClass");
    }

    public void notifyDataActivity(int state) {
        Slog.w(TAG, "should not call CompositeTelephonyRegistry.notifyDataActivity");
    }

    public void notifyDataConnection(int state, boolean isDataConnectivityPossible,
            String reason, String apn, String apnType, LinkProperties linkProperties,
            LinkCapabilities linkCapabilities, int networkType, boolean roaming) {
        Slog.w(TAG, "should not call CompositeTelephonyRegistry.notifyDataConnection");
    }

    public void notifyDataConnectionFailed(String reason, String apnType) {
        Slog.w(TAG, "should not call CompositeTelephonyRegistry.notifyDataConnectionFailed");
    }

    public void notifyCellLocation(Bundle cellLocation) {
        Slog.w(TAG, "should not call CompositeTelephonyRegistry.notifyCellLocation");
    }

    public void notifyOtaspChanged(int otaspMode) {
        Slog.w(TAG, "should not call CompositeTelephonyRegistry.notifyOtaspChanged");
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
    }

	@Override
	public void notifyCellInfo(CellInfo cellInfo) throws RemoteException {
		Slog.w(TAG, "should not call CompositeTelephonyRegistry.notifyCellInfo");
	}

}
