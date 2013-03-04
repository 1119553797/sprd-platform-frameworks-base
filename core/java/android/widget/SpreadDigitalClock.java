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

import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
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
    private final static String m12 = "h:mm aa";
    private final static String m24 = "k:mm";
    private FormatChangeObserver mFormatChangeObserver;

    private Runnable mTicker;
    private Handler mHandler;

    private boolean mTickerStopped = false;

    String mFormat;

    private TextView mWeek;
    private TextView mDate;
    private TextView mTime;

    private static final int DATE_ID = 1;
    private static final int TIME_ID = 2;

    private static final int DATE_SIZE = 20;
    private static final int TIME_SIZE = 55;

    public SpreadDigitalClock(Context context) {
        super(context);
        initClock(context);
    }

    public SpreadDigitalClock(Context context, AttributeSet attrs) {
        super(context, attrs);
        initClock(context);
    }

    private void initClock(Context context) {
        Resources r = mContext.getResources();

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
        mHandler = new Handler();

        mFormatChangeObserver = new FormatChangeObserver();
        getContext().getContentResolver().registerContentObserver(
                Settings.System.CONTENT_URI, true, mFormatChangeObserver);

        setFormat();

        /**
         * requests a tick on the next hard-second boundary
         */
        mTicker = new Runnable() {
                public void run() {
                    if (mTickerStopped) return;
                    mCalendar.setTimeInMillis(System.currentTimeMillis());
                    CharSequence week = DateFormat.format("EEEE", mCalendar.getTime());
                    CharSequence date = DateFormat.getDateFormat(SpreadDigitalClock.this.getContext()).format(mCalendar.getTime());
                    CharSequence dow = SpreadDigitalClock.this.getContext().getString(com.android.internal.R.string.digtal_formatter, week, date);
                    mWeek.setText(week);
                    mDate.setText(dow);
                    invalidate();
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
		if (mFormatChangeObserver != null) {
			mContext.getContentResolver().unregisterContentObserver(
					mFormatChangeObserver);
			mFormatChangeObserver = null;
		}
    }

    /**
     * Pulls 12/24 mode from system settings
     */
    private boolean get24HourMode() {
        return android.text.format.DateFormat.is24HourFormat(getContext());
    }

    private void setFormat() {
        if (get24HourMode()) {
            mFormat = m24;
        } else {
            mFormat = m12;
        }
    }

    private class FormatChangeObserver extends ContentObserver {
        public FormatChangeObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            setFormat();
        }
    }
}
