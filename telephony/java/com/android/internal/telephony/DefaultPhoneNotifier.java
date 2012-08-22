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

import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.ITelephonyRegistry;
import static com.android.internal.telephony.CommandsInterface.SERVICE_CLASS_VOICE;

/**
 * broadcast intents
 */
public class DefaultPhoneNotifier implements PhoneNotifier {

    static final String LOG_TAG = "GSM";
    private static final boolean DBG = true;
    private ITelephonyRegistry mRegistry;
	private int mPhoneId;

    /*package*/
    DefaultPhoneNotifier() {
        this(PhoneFactory.getDefaultPhoneId());
    }

    DefaultPhoneNotifier(int phoneId) {
	    mPhoneId = phoneId;
        mRegistry = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService(
                    PhoneFactory.getServiceName("telephony.registry", phoneId)));
    }

    public void notifyPhoneState(Phone sender) {
        Call ringingCall = sender.getRingingCall();
        String incomingNumber = "";
        if (ringingCall != null && ringingCall.getEarliestConnection() != null){
            incomingNumber = ringingCall.getEarliestConnection().getAddress();
        }
        try {
            mRegistry.notifyCallState(convertCallState(sender.getState()), incomingNumber);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    public void notifyServiceState(Phone sender) {
        try {
            mRegistry.notifyServiceState(sender.getServiceState());
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    public void notifySignalStrength(Phone sender) {
        try {
            mRegistry.notifySignalStrength(sender.getSignalStrength());
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    public void notifyMessageWaitingChanged(Phone sender) {
        try {
            mRegistry.notifyMessageWaitingChanged(sender.getMessageWaitingIndicator());
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    public void notifyCallForwardingChanged(Phone sender) {
        try {
            mRegistry.notifyCallForwardingChanged(sender.getCallForwardingIndicator());
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    public void notifyCallForwardingChanged(Phone sender, int serviceClass) {
        try {
            mRegistry.notifyCallForwardingChangedByServiceClass(sender.getCallForwardingIndicator(serviceClass), serviceClass);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    public void notifyDataActivity(Phone sender) {
        try {
            mRegistry.notifyDataActivity(convertDataActivityState(sender.getDataActivityState()));
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    public void notifyDataConnection(Phone sender, String reason) {
        TelephonyManager telephony = TelephonyManager.getDefault(sender.getPhoneId());
        try {
            mRegistry.notifyDataConnection(
                    convertDataState(sender.getDataConnectionState()),
                    sender.isDataConnectivityPossible(), reason,
                    sender.getActiveApn(),
                    sender.getActiveApnTypes(),
                    sender.getInterfaceName(null),
                    ((telephony!=null) ? telephony.getNetworkType() :
                    TelephonyManager.NETWORK_TYPE_UNKNOWN),
                    sender.getGateway(null));
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    public void notifyDataConnectionMpdp(String apnType, Phone sender, String reason) {
        TelephonyManager telephony = TelephonyManager.getDefault();
        try {
            if(DBG){
                Log.d(LOG_TAG, "notifyDataConnection,apnType=" + apnType);
                Log.d(LOG_TAG, ">>>sender.getDataConnectionState()="
                        + sender.getDataConnectionState());
                Log.d(LOG_TAG, ">>>sender.getDataConnectionState(" + apnType + ")="
                        + sender.getDataConnectionState(apnType));
                Log.d(LOG_TAG, ">>>sender.isDataConnectivityPossible()="
                        + sender.isDataConnectivityPossible());
                Log.d(LOG_TAG, ">>>reason=" + reason);
                Log.d(LOG_TAG, ">>>sender.getActiveApn()=" + sender.getActiveApn(apnType));
                if (sender.getActiveApnTypes(apnType) != null) {
                    for (int i = 0; i < sender.getActiveApnTypes(apnType).length; i++) {
                        Log.d(LOG_TAG, ">>>sender.getActiveApnTypes[" + i + "]="
                                + sender.getActiveApnTypes(apnType)[i]);
                    }
                } else {
                    Log.d(LOG_TAG, ">>>sender.getActiveApnTypes()="
                            + sender.getActiveApnTypes(apnType));
                }
                Log.d(LOG_TAG, ">>>sender.getInterfaceName()=" + sender.getInterfaceName(apnType));
                Log.d(LOG_TAG, ">>>telephony.getNetworkType()=" + telephony.getNetworkType());
            }
            mRegistry.notifyDataConnectionMpdp(
                    convertDataState(sender.getDataConnectionState()),
                    convertDataState(sender.getDataConnectionState(apnType)),
                    sender.isDataConnectivityPossible(), reason,
                    sender.getActiveApn(apnType),
                    sender.getActiveApnTypes(apnType),
                    sender.getInterfaceName(apnType),
                    ((telephony!=null) ? telephony.getNetworkType() :
                    TelephonyManager.NETWORK_TYPE_UNKNOWN),
                    sender.getGateway(apnType));
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    public void notifyDataConnectionFailed(Phone sender, String reason) {
        try {
            mRegistry.notifyDataConnectionFailed(reason);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    public void notifyCellLocation(Phone sender) {
        Bundle data = new Bundle();
        sender.getCellLocation().fillInNotifierBundle(data);
        try {
            mRegistry.notifyCellLocation(data);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    private void log(String s) {
        Log.d(LOG_TAG, "[PhoneNotifier] " + s);
    }

    /**
     * Convert the {@link State} enum into the TelephonyManager.CALL_STATE_* constants
     * for the public API.
     */
    public static int convertCallState(Phone.State state) {
        switch (state) {
            case RINGING:
                return TelephonyManager.CALL_STATE_RINGING;
            case OFFHOOK:
                return TelephonyManager.CALL_STATE_OFFHOOK;
            default:
                return TelephonyManager.CALL_STATE_IDLE;
        }
    }

    /**
     * Convert the TelephonyManager.CALL_STATE_* constants into the {@link State} enum
     * for the public API.
     */
    public static Phone.State convertCallState(int state) {
        switch (state) {
            case TelephonyManager.CALL_STATE_RINGING:
                return Phone.State.RINGING;
            case TelephonyManager.CALL_STATE_OFFHOOK:
                return Phone.State.OFFHOOK;
            default:
                return Phone.State.IDLE;
        }
    }

    /**
     * Convert the {@link DataState} enum into the TelephonyManager.DATA_* constants
     * for the public API.
     */
    public static int convertDataState(Phone.DataState state) {
        switch (state) {
            case CONNECTING:
                return TelephonyManager.DATA_CONNECTING;
            case CONNECTED:
                return TelephonyManager.DATA_CONNECTED;
            case SUSPENDED:
                return TelephonyManager.DATA_SUSPENDED;
            default:
                return TelephonyManager.DATA_DISCONNECTED;
        }
    }

    /**
     * Convert the TelephonyManager.DATA_* constants into {@link DataState} enum
     * for the public API.
     */
    public static Phone.DataState convertDataState(int state) {
        switch (state) {
            case TelephonyManager.DATA_CONNECTING:
                return Phone.DataState.CONNECTING;
            case TelephonyManager.DATA_CONNECTED:
                return Phone.DataState.CONNECTED;
            case TelephonyManager.DATA_SUSPENDED:
                return Phone.DataState.SUSPENDED;
            default:
                return Phone.DataState.DISCONNECTED;
        }
    }

    /**
     * Convert the {@link DataState} enum into the TelephonyManager.DATA_* constants
     * for the public API.
     */
    public static int convertDataActivityState(Phone.DataActivityState state) {
        switch (state) {
            case DATAIN:
                return TelephonyManager.DATA_ACTIVITY_IN;
            case DATAOUT:
                return TelephonyManager.DATA_ACTIVITY_OUT;
            case DATAINANDOUT:
                return TelephonyManager.DATA_ACTIVITY_INOUT;
            case DORMANT:
                return TelephonyManager.DATA_ACTIVITY_DORMANT;
            default:
                return TelephonyManager.DATA_ACTIVITY_NONE;
        }
    }

    /**
     * Convert the TelephonyManager.DATA_* constants into the {@link DataState} enum
     * for the public API.
     */
    public static Phone.DataActivityState convertDataActivityState(int state) {
        switch (state) {
            case TelephonyManager.DATA_ACTIVITY_IN:
                return Phone.DataActivityState.DATAIN;
            case TelephonyManager.DATA_ACTIVITY_OUT:
                return Phone.DataActivityState.DATAOUT;
            case TelephonyManager.DATA_ACTIVITY_INOUT:
                return Phone.DataActivityState.DATAINANDOUT;
            case TelephonyManager.DATA_ACTIVITY_DORMANT:
                return Phone.DataActivityState.DORMANT;
            default:
                return Phone.DataActivityState.NONE;
        }
    }
}
