package android.net;

import com.android.internal.telephony.DataConnectionTracker;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.ServiceManager;
import android.telephony.TelephonyManager;
import android.util.Slog;

/**
 * Track the state of mobile data connectivity. This is done by
 * receiving broadcast intents from the Phone process whenever
 * the state of data connectivity changes.
 *
 * {@hide}
 */
public class MsMobileDataStateTracker extends MobileDataStateTracker {
    private int mPhoneId;
    protected static final boolean DBG = true;

    public MsMobileDataStateTracker(int netType, String tag, int phoneId) {
        super(netType, tag);
        mNetworkInfo = new NetworkInfo(netType,
                TelephonyManager.getDefault().getNetworkType(), tag,
                TelephonyManager.getDefault().getNetworkTypeName(), phoneId);
        mApnType = networkTypeToApnType(netType);
        mPhoneId = phoneId;
    }

    @ Override
    public void startMonitoring(Context context, Handler target) {
        mTarget = target;
        mContext = context;

        mHandler = new MdstHandler(target.getLooper(), this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_DATA_CONNECTION_FAILED);
        filter.addAction(DataConnectionTracker.ACTION_DATA_CONNECTION_TRACKER_MESSENGER);

        mContext.registerReceiver(new MsMobileDataStateReceiver(), filter);
        mMobileDataState = Phone.DataState.DISCONNECTED;
    }

    protected class MsMobileDataStateReceiver extends MobileDataStateReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int phoneId = intent.getIntExtra(Phone.PHONE_ID, -1);
            if (phoneId == mPhoneId) {
                if (DBG) {
                    log("receive " + intent.getAction());
                }
                super.onReceive(context, intent);
            }
        }
    }

    @ Override
    protected void getPhoneService(boolean forceRefresh) {
        mPhoneService = ITelephony.Stub.asInterface(ServiceManager.getService(PhoneFactory
                .getServiceName("phone", mPhoneId)));
    }

    public static String networkTypeToApnType(int netType) {
        switch(netType) {
            case ConnectivityManager.TYPE_MOBILE_DM:
                return Phone.APN_TYPE_DM;
            case ConnectivityManager.TYPE_MOBILE_WAP:
                return Phone.APN_TYPE_WAP;
            default:
                return MobileDataStateTracker.networkTypeToApnType(netType);
        }
    }

    @ Override
    protected void log(String s) {
        Slog.d(TAG, mApnType + mPhoneId + ": " + s);
    }

}
