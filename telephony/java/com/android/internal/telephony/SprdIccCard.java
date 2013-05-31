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

import android.util.Log;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.TelephonyProperties;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;

/**
 * {@hide}
 */
public final class SprdIccCard extends IccCard {

    private static final int EVENT_SIM_NOT_DETECTED = 1;
    private static final int EVENT_TURN_RADIO_OFF_COMPLETE = 2;
    private static final int EVENT_TURN_SIM_ON_COMPLETE = 3;
    private static final int EVENT_TURN_RADIO_ON_COMPLETE = 4;

    public SprdIccCard(PhoneBase phone, String logTag, Boolean is3gpp, Boolean dbg) {
        super(phone, logTag, is3gpp, dbg);
        mPhone.mCM.registerForSimNotDetected(mOwnHandler, EVENT_SIM_NOT_DETECTED, null);
    }

    @Override
    public void dispose() {
        super.dispose();
        //Unregister for all events
        mPhone.mCM.unregisterForSimNotDetected(mOwnHandler);
    }

    private Handler mOwnHandler = new Handler() {
        @Override
        public void handleMessage(Message msg){

            switch (msg.what) {
                case EVENT_SIM_NOT_DETECTED:
                    if(mDbg) Log.d(mLogTag, "receive EVENT_SIM_NOT_DETECTED!");
                    mPhone.mCM.setRadioPower(false,
                            mOwnHandler.obtainMessage(EVENT_TURN_RADIO_OFF_COMPLETE));
                    break;

                case EVENT_TURN_RADIO_OFF_COMPLETE:
                    if(mDbg) Log.d(mLogTag, "receive EVENT_TURN_RADIO_OFF_COMPLETE!");
                    mPhone.mCM.setSIMPower(true,
                            mOwnHandler.obtainMessage(EVENT_TURN_SIM_ON_COMPLETE));
                    break;

                case EVENT_TURN_SIM_ON_COMPLETE:
                    if(mDbg) Log.d(mLogTag, "receive EVENT_TURN_SIM_ON_COMPLETE!");
                    mPhone.mCM.setRadioPower(true, null);
                    break;
            }
        }
    };

}
