package com.android.internal.telephony;

import android.content.Context;
import android.content.Intent;
import android.net.LocalServerSocket;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.os.SystemProperties;

import com.android.internal.telephony.IccCard.State;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.cdma.CDMALTEPhone;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.gsm.SimPhoneBookInterfaceManager;
import com.android.internal.telephony.gsm.SimSmsInterfaceManager;
import com.android.internal.telephony.sip.SipPhone;
import com.android.internal.telephony.sip.SipPhoneFactory;
import com.android.internal.telephony.gsm.TDPhone;

public class MsmsPhoneFactory extends PhoneFactory {

     /**
     * FIXME replace this with some other way of making these
     * instances
     */
    public static void makeDefaultPhone(Context context) {
        synchronized(Phone.class) {
            if (!sMadeDefaults) {
                sLooper = Looper.myLooper();
                sContext = context;

                if (sLooper == null) {
                    throw new RuntimeException(
                        "PhoneFactory.makeDefaultPhone must be called from Looper thread");
                }

                int retryCount = 0;
                for(;;) {
                    boolean hasException = false;
                    retryCount ++;

                    try {
                        // use UNIX domain socket to
                        // prevent subsequent initialization
                        new LocalServerSocket("com.android.internal.telephony");
                    } catch (java.io.IOException ex) {
                        hasException = true;
                    }

                    if ( !hasException ) {
                        break;
                    } else if (retryCount > SOCKET_OPEN_MAX_RETRY) {
                        throw new RuntimeException("PhoneFactory probably already running");
                    } else {
                        try {
                            Thread.sleep(SOCKET_OPEN_RETRY_MILLIS);
                        } catch (InterruptedException er) {
                        }
                    }
                }

                int phoneCount = getPhoneCount();
                sPhoneNotifier = new DefaultPhoneNotifier[phoneCount];
                for (int i = 0; i < phoneCount; i++) {
                    sPhoneNotifier[i] = new DefaultPhoneNotifier(i);
                }

                // Get preferred network mode
                int preferredNetworkMode = RILConstants.PREFERRED_NETWORK_MODE;
                if (BaseCommands.getLteOnCdmaModeStatic() == Phone.LTE_ON_CDMA_TRUE) {
                    preferredNetworkMode = Phone.NT_MODE_GLOBAL;
                }
                int networkMode = Settings.Secure.getInt(context.getContentResolver(),
                        Settings.Secure.PREFERRED_NETWORK_MODE, preferredNetworkMode);
                Log.i(LOG_TAG, "Network Mode set to " + Integer.toString(networkMode));

                // Get cdmaSubscription
                // TODO: Change when the ril will provides a way to know at runtime
                //       the configuration, bug 4202572. And the ril issues the
                //       RIL_UNSOL_CDMA_SUBSCRIPTION_SOURCE_CHANGED, bug 4295439.
                int cdmaSubscription;
                int lteOnCdma = BaseCommands.getLteOnCdmaModeStatic();
                switch (lteOnCdma) {
                    case Phone.LTE_ON_CDMA_FALSE:
                        cdmaSubscription = CdmaSubscriptionSourceManager.SUBSCRIPTION_FROM_NV;
                        Log.i(LOG_TAG, "lteOnCdma is 0 use SUBSCRIPTION_FROM_NV");
                        break;
                    case Phone.LTE_ON_CDMA_TRUE:
                        cdmaSubscription = CdmaSubscriptionSourceManager.SUBSCRIPTION_FROM_RUIM;
                        Log.i(LOG_TAG, "lteOnCdma is 1 use SUBSCRIPTION_FROM_RUIM");
                        break;
                    case Phone.LTE_ON_CDMA_UNKNOWN:
                    default:
                        //Get cdmaSubscription mode from Settings.System
                        cdmaSubscription = Settings.Secure.getInt(context.getContentResolver(),
                                Settings.Secure.PREFERRED_CDMA_SUBSCRIPTION,
                                preferredCdmaSubscription);
                        Log.i(LOG_TAG, "lteOnCdma not set, using PREFERRED_CDMA_SUBSCRIPTION");
                        break;
                }
                Log.i(LOG_TAG, "Cdma Subscription set to " + cdmaSubscription);

                //reads the system properties and makes commandsinterface

                int phoneType = getPhoneType(networkMode);
                sCommandsInterface = new SprdRIL[phoneCount];
                sProxyPhone = new SprdPhoneProxy[phoneCount];
                isCardHandled = new boolean [phoneCount];
                for(int i = 0; i < phoneCount;i++) {
                    sCommandsInterface[i] = new SprdRIL(context, networkMode, cdmaSubscription, i);
                    if (phoneType == Phone.PHONE_TYPE_GSM) {
                        sProxyPhone[i] = new SprdPhoneProxy(new TDPhone(context,
                                sCommandsInterface[i], sPhoneNotifier[i]));
                        Log.i(LOG_TAG, "Creating TDPhone");
                    } /*else if (phoneType == Phone.PHONE_TYPE_CDMA) {
                        switch (BaseCommands.getLteOnCdmaModeStatic()) {
                            case Phone.LTE_ON_CDMA_TRUE:
                                Log.i(LOG_TAG, "Creating CDMALTEPhone");
                                sProxyPhone = new PhoneProxy(new CDMALTEPhone(context,
                                    sCommandsInterface, sPhoneNotifier));
                                break;
                            case Phone.LTE_ON_CDMA_FALSE:
                            default:
                                Log.i(LOG_TAG, "Creating CDMAPhone");
                                sProxyPhone = new PhoneProxy(new CDMAPhone(context,
                                        sCommandsInterface, sPhoneNotifier));
                                break;
                        }
                    }*/
                }
                // zhanglj modify end
                IccSmsInterfaceManagerProxy[] mIccSmsInterfaceManagerProxy = new IccSmsInterfaceManagerProxy[phoneCount];
                IccPhoneBookInterfaceManagerProxy[] mIccPhoneBookInterfaceManagerProxy = new IccPhoneBookInterfaceManagerProxy[phoneCount];
                PhoneSubInfoProxy[] mPhoneSubInfoProxy = new PhoneSubInfoProxy[phoneCount];
                for (int i = 0; i < phoneCount; i++) {
                    mIccSmsInterfaceManagerProxy[i] = ((SprdPhoneProxy)sProxyPhone[i]).getIccSmsInterfaceManagerProxy();
                    mIccPhoneBookInterfaceManagerProxy[i] = ((SprdPhoneProxy)sProxyPhone[i]).getIccPhoneBookInterfaceManagerProxy();
                    mPhoneSubInfoProxy[i] = ((SprdPhoneProxy)sProxyPhone[i]).getPhoneSubInfoProxy();
                }
                new CompositeIccSmsInterfaceManagerProxy(mIccSmsInterfaceManagerProxy);
                //new CompositeIccPhoneBookInterfaceManagerProxy(mIccPhoneBookInterfaceManagerProxy);
                new CompositePhoneSubInfoProxy(mPhoneSubInfoProxy);

                sMadeDefaults = true;
            }
        }
    }

    public static Phone getGsmPhone() {
        synchronized(PhoneProxy.lockForRadioTechnologyChange) {
            Phone phone = new TDPhone(sContext, sCommandsInterface[getDefaultPhoneId()], sPhoneNotifier[getDefaultPhoneId()]);
            return phone;
        }
    }

}
