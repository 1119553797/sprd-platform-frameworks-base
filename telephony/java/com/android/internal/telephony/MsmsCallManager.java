package com.android.internal.telephony;

import java.util.ArrayList;

import android.util.Log;

import com.android.internal.telephony.gsm.TDPhone;

public class MsmsCallManager extends CallManager {

    static {
        INSTANCE = new MsmsCallManager();
    }

    private MsmsCallManager() {

    }

    public static CallManager getInstance() {
        return INSTANCE;
    }

    /**
     * Register phone to CallManager
     * @param phone to be registered
     * @return true if register successfully
     */
    public boolean registerPhone(Phone phone) {
        Phone basePhone = getPhoneBase(phone);

        if (basePhone != null && !mPhones.contains(basePhone)) {

            if (DBG) {
                Log.d(LOG_TAG, "registerPhone(" +
                        phone.getPhoneName() + " " + phone + ")");
            }

            if (mPhones.isEmpty()) {
                mDefaultPhone = basePhone;
            }
            mPhones.add(basePhone);

         if (basePhone instanceof TDPhone) {
            TDPhone tdPhone = (TDPhone)basePhone;
            ArrayList<Call> calls = tdPhone.getRingingCalls();
            for (Call call : calls) {
                mRingingCalls.add(call);
            }
            calls = tdPhone.getBackgroundCalls();
            for (Call call : calls) {
                mBackgroundCalls.add(call);
            }
            calls = tdPhone.getForegroundCalls();
            for (Call call : calls) {
                mForegroundCalls.add(call);
            }
        } else {
            mRingingCalls.add(basePhone.getRingingCall());
            mBackgroundCalls.add(basePhone.getBackgroundCall());
            mForegroundCalls.add(basePhone.getForegroundCall());
        }
            registerForPhoneStates(basePhone);
            return true;
        }

        return false;
    }

}
