
package com.android.server;

import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.util.Slog;

import com.android.internal.telephony.IPhoneStateListener;
import com.android.internal.telephony.ITelephonyRegistry;
import com.android.internal.telephony.PhoneFactory;

public class CompositeTelephonyRegistry extends ITelephonyRegistry.Stub {
    private static final String TAG = "CompositeTelephonyRegistry";

    private static final boolean DBG = false;

    private final Context mContext;

    private TelephonyRegistry[] mTelephonyRegistry;

    private int getSimplePolicyPhoneId() {
        return PhoneFactory.getDefaultPhoneId();
    }

    CompositeTelephonyRegistry(Context context, TelephonyRegistry[] telephonyRegistry) {
        mContext = context;
        mTelephonyRegistry = telephonyRegistry;
    }

    @Override
    public void listen(String pkg, IPhoneStateListener callback, int events, boolean notifyNow)
            throws RemoteException {
        if (events == 0) {
            for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
                mTelephonyRegistry[i].listen(pkg, callback, events, notifyNow);
            }
            return;
        }
        if ((events & PhoneStateListener.LISTEN_SERVICE_STATE) != 0) {
            mTelephonyRegistry[getSimplePolicyPhoneId()].listen(pkg, callback, events, notifyNow);
        }
        if ((events & PhoneStateListener.LISTEN_SIGNAL_STRENGTH) != 0) {
            mTelephonyRegistry[getSimplePolicyPhoneId()].listen(pkg, callback, events, notifyNow);
        }
        if ((events & PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR) != 0) {
            mTelephonyRegistry[getSimplePolicyPhoneId()].listen(pkg, callback, events, notifyNow);
        }
        if ((events & PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR) != 0) {
            mTelephonyRegistry[getSimplePolicyPhoneId()].listen(pkg, callback, events, notifyNow);
        }
        if ((events & PhoneStateListener.LISTEN_CELL_LOCATION) != 0) {
            mTelephonyRegistry[getSimplePolicyPhoneId()].listen(pkg, callback, events, notifyNow);
        }
        if ((events & PhoneStateListener.LISTEN_CALL_STATE) != 0) {
            for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
                mTelephonyRegistry[i].listen(pkg, callback, events, notifyNow);
            }
        }
        if ((events & PhoneStateListener.LISTEN_DATA_CONNECTION_STATE) != 0) {
            mTelephonyRegistry[getSimplePolicyPhoneId()].listen(pkg, callback, events, notifyNow);
        }
        if ((events & PhoneStateListener.LISTEN_DATA_ACTIVITY) != 0) {
            mTelephonyRegistry[getSimplePolicyPhoneId()].listen(pkg, callback, events, notifyNow);
        }
        if ((events & PhoneStateListener.LISTEN_SIGNAL_STRENGTHS) != 0) {
            mTelephonyRegistry[getSimplePolicyPhoneId()].listen(pkg, callback, events, notifyNow);
        }
    }

    @Override
    public void notifyCallState(int state, String incomingNumber) throws RemoteException {
        Slog.w(TAG, "Error, should not call CompositeTelephonyRegistry.notifyCallState");
    }

    @Override
    public void notifyServiceState(ServiceState state) throws RemoteException {
        Slog.w(TAG, "Error, should not call CompositeTelephonyRegistry.notifyServiceState");
    }

    @Override
    public void notifySignalStrength(SignalStrength signalStrength) throws RemoteException {
        Slog.w(TAG, "Error, should not call CompositeTelephonyRegistry.notifySignalStrength");
    }

    @Override
    public void notifyMessageWaitingChanged(boolean mwi) throws RemoteException {
        Slog.w(TAG, "Error, should not call CompositeTelephonyRegistry.notifyMessageWaitingChanged");
    }

    @Override
    public void notifyCallForwardingChanged(boolean cfi) throws RemoteException {
        Slog.w(TAG, "Error, should not call CompositeTelephonyRegistry.notifyCallForwardingChanged");
    }

    @Override
    public void notifyCallForwardingChangedByServiceClass(boolean cfi, int sc)
            throws RemoteException {
        Slog.w(TAG,
                "Error, should not call CompositeTelephonyRegistry.notifyCallForwardingChangedByServiceClass");
    }

    @Override
    public void notifyDataActivity(int state) throws RemoteException {
        Slog.w(TAG, "Error, should not call CompositeTelephonyRegistry.notifyDataActivity");
    }

    @Override
    public void notifyDataConnection(int state, boolean isDataConnectivityPossible, String reason,
            String apn, String[] apnTypes, String interfaceName, int networkType, String gateway)
            throws RemoteException {
        Slog.w(TAG, "Error, should not call CompositeTelephonyRegistry.notifyDataConnection");
    }

    @Override
    public void notifyDataConnectionMpdp(int state, int typeState, boolean isDataConnectivityPossible, String reason,
            String apn, String[] apnTypes, String interfaceName, int networkType, String gateway)
            throws RemoteException {
        Slog.w(TAG, "Error, should not call CompositeTelephonyRegistry.notifyDataConnection");
    }

    @Override
    public void notifyDataConnectionFailed(String reason) throws RemoteException {
        Slog.w(TAG, "Error, should not call CompositeTelephonyRegistry.notifyDataConnectionFailed");
    }

    @Override
    public void notifyCellLocation(Bundle cellLocation) throws RemoteException {
        Slog.w(TAG, "Error, should not call CompositeTelephonyRegistry.notifyCellLocation");
    }

}
