/** Created by Spreadst */

package android.net;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
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
public class MsmsMobileDataStateTracker extends MobileDataStateTracker {
    private int mPhoneId;
    protected static final boolean DBG = true;

    public MsmsMobileDataStateTracker(int netType, String tag, int phoneId) {
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
        filter.addAction(TelephonyIntents.ACTION_DATA_CONNECTION_TRACKER_MESSENGER);

        mContext.registerReceiver(new MsmsMobileDataStateReceiver(), filter);
        mMobileDataState = PhoneConstants.DataState.DISCONNECTED;
    }

    protected class MsmsMobileDataStateReceiver extends MobileDataStateReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int phoneId = intent.getIntExtra(TelephonyIntents.EXTRA_PHONE_ID, -1);
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
        mPhoneService = ITelephony.Stub.asInterface(ServiceManager.getService(TelephonyManager
                .getServiceName("phone", mPhoneId)));
    }

    @ Override
    protected void log(String s) {
        Slog.d(TAG, mApnType + mPhoneId + ": " + s);
    }

}
