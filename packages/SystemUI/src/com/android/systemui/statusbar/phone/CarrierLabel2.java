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

package com.android.systemui.statusbar.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Telephony;
import android.provider.Telephony.Intents;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.View;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import com.android.internal.R;
import com.android.internal.telephony.PhoneFactory;

/**
 * This widget display an analogic clock with two hands for hours and
 * minutes.
 */
public class CarrierLabel2 extends TextView {
    private boolean mAttached;

    public CarrierLabel2(Context context) {
        this(context, null);
    }

    public CarrierLabel2(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CarrierLabel2(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        updateNetworkName(false, null, false, null, 1);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(Telephony.Intents.SPN_STRINGS_UPDATED_ACTION);
            getContext().registerReceiver(mIntentReceiver, filter, null, getHandler());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            getContext().unregisterReceiver(mIntentReceiver);
            mAttached = false;
        }
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Telephony.Intents.SPN_STRINGS_UPDATED_ACTION.equals(action)) {
                updateNetworkName(intent.getBooleanExtra(Telephony.Intents.EXTRA_SHOW_SPN, false),
                        intent.getStringExtra(Telephony.Intents.EXTRA_SPN),
                        intent.getBooleanExtra(Telephony.Intents.EXTRA_SHOW_PLMN, false),
                        intent.getStringExtra(Telephony.Intents.EXTRA_PLMN),
                        intent.getIntExtra(Intents.EXTRA_PHONE_ID, 0));
            }
        }
    };

    void updateNetworkName(boolean showSpn, String spn, boolean showPlmn, String plmn, int phoneid) {
        if (phoneid != 0) return;
        if (true) {
            Slog.d("CarrierLabel", "updateNetworkName showSpn=" + showSpn + " spn=" + spn
                    + " showPlmn=" + showPlmn + " plmn=" + plmn + "phoneid" + phoneid);
        }
        StringBuilder str = new StringBuilder();
        boolean something = false;
        boolean hasSim = PhoneFactory.isCardExist(phoneid);
        if (showPlmn && plmn != null) {
            str.append(plmn);
            something = true;
        }
        if (showSpn && spn != null) {
            if (something) {
                str.append('\n');
            }
            str.append(spn);
            something = true;
        }

        if (something) {
            if (showPlmn
                    && plmn != null
                    && plmn.equals(mContext
                            .getString(com.android.internal.R.string.lockscreen_carrier_default))) {
                if (!hasSim) {
                    str.append(" | ");
                    str.append(mContext
                            .getString(com.android.internal.R.string.lockscreen_missing_sim_message_short));
                }
            }
            setText(str.toString());
        } else {
            str.append(mContext.getString(com.android.internal.R.string.lockscreen_carrier_default));
            if (!hasSim) {
                str.append(" | ");
                str.append(mContext
                        .getString(com.android.internal.R.string.lockscreen_missing_sim_message_short));
            }
            setText(str.toString());
        }
    }


}


