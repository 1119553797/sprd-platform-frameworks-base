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

package android.widget;

import java.util.Calendar;
import java.util.TimeZone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.RemoteViews.RemoteView;
import com.android.internal.R;


/**
 * Like AnalogClock, but digital.  Shows seconds.
 *
 * FIXME: implement separate views for hours/minutes/seconds, so
 * proportional fonts don't shake rendering
 */
@RemoteView
public class SpreadDigitalClock extends RelativeLayout {

    Calendar mCalendar;

    private Runnable mTicker;
    private Handler mHandler;

    private boolean mTickerStopped = false;

    private boolean mAttached;

    private TextView mWeek;
    private TextView mDate;
    private TextView mTime;

    public SpreadDigitalClock(Context context) {
        super(context);
        initClock(context);
    }

    public SpreadDigitalClock(Context context, AttributeSet attrs) {
        super(context, attrs);
        initClock(context);
    }

    private void initClock(Context context) {
        if (mCalendar == null) {
            mCalendar = Calendar.getInstance();
        }

        final LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.spread_digtal_clock, this,true);

        mDate = (TextView) this.findViewById(R.id.spread_date);
        mWeek = (TextView) this.findViewById(R.id.spread_week);
    }

    @Override
    protected void onAttachedToWindow() {
        mTickerStopped = false;
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();

            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);

            getContext().registerReceiver(mIntentReceiver, filter, null, mHandler);
        }

        mHandler = new Handler();

        /**
         * requests a tick on the next hard-second boundary
         */
        mTicker = new Runnable() {
            public void run() {
                if (mTickerStopped) {
                    return;
                }
                onTimeChanged();
                long now = SystemClock.uptimeMillis();
                long next = now + (1000 - now % 1000);
                mHandler.postAtTime(mTicker, next);
            }
        };
        mTicker.run();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mTickerStopped = true;

        if (mAttached) {
            getContext().unregisterReceiver(mIntentReceiver);
            mAttached = false;
        }
    }

    private void onTimeChanged() {
        mCalendar.setTimeInMillis(System.currentTimeMillis());
        CharSequence week = DateFormat.format("EEEE", mCalendar.getTime());
        CharSequence date = DateFormat.getDateFormat(SpreadDigitalClock.this.getContext()).format(
                mCalendar.getTime());
        CharSequence dow = SpreadDigitalClock.this.getContext().getString(
                com.android.internal.R.string.digtal_formatter, week, date);
        mWeek.setText(week);
        mDate.setText(dow);
        invalidate();
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_TIMEZONE_CHANGED)) {
                String tz = intent.getStringExtra("time-zone");
                mCalendar.setTimeZone(TimeZone.getTimeZone(tz));
            }

            onTimeChanged();
        }
    };
}
