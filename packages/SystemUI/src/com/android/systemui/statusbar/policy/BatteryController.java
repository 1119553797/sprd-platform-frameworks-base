/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import java.util.ArrayList;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
/* Add 20130108 Spreadst of 113654 add changer animation start */
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
/* Add 20130108 Spreadst of 113654 add changer animation end */
import android.os.BatteryManager;
import android.util.Slog;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;

public class BatteryController extends BroadcastReceiver {
    private static final String TAG = "StatusBar.BatteryController";

    private Context mContext;
    private ArrayList<ImageView> mIconViews = new ArrayList<ImageView>();
    private ArrayList<TextView> mLabelViews = new ArrayList<TextView>();
    /* Add 20130108 Spreadst of 113654 add changer animation start */
    private AnimationDrawable m_Anim_Charge_a = null;
    private AnimationDrawable m_Anim_Charge_b = null;
    private AnimationDrawable m_Anim_Charge_c = null;
    private AnimationDrawable m_Anim_Charge_d = null;
    private AnimationDrawable m_Anim_Charge_e = null;
    private AnimationDrawable m_Anim_Charge_f = null;
    private AnimationDrawable m_Anim_Charge_g = null;
    private static final int LEVEL_G = 85;
    private static final int LEVEL_F = 71;
    private static final int LEVEL_E = 57;
    private static final int LEVEL_D = 43;
    private static final int LEVEL_C = 28;
    private static final int LEVEL_B = 15;
    /* Add 20130108 Spreadst of 113654 add changer animation end */
    public BatteryController(Context context) {
        mContext = context;

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        context.registerReceiver(this, filter);
        /* Add 20130108 Spreadst of 113654 add changer animation start */
        m_Anim_Charge_a = (AnimationDrawable) context.getResources().getDrawable(R.anim.stat_sys_battery_charge_anim_a);
        m_Anim_Charge_b = (AnimationDrawable) context.getResources().getDrawable(R.anim.stat_sys_battery_charge_anim_b);
        m_Anim_Charge_c = (AnimationDrawable) context.getResources().getDrawable(R.anim.stat_sys_battery_charge_anim_c);
        m_Anim_Charge_d = (AnimationDrawable) context.getResources().getDrawable(R.anim.stat_sys_battery_charge_anim_d);
        m_Anim_Charge_e = (AnimationDrawable) context.getResources().getDrawable(R.anim.stat_sys_battery_charge_anim_e);
        m_Anim_Charge_f = (AnimationDrawable) context.getResources().getDrawable(R.anim.stat_sys_battery_charge_anim_f);
        m_Anim_Charge_g = (AnimationDrawable) context.getResources().getDrawable(R.anim.stat_sys_battery_charge_anim_g);
        /* Add 20130108 Spreadst of 113654 add changer animation end */
    }

    public void addIconView(ImageView v) {
        mIconViews.add(v);
    }

    public void addLabelView(TextView v) {
        mLabelViews.add(v);
    }

    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
            final int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            /* Modify 20130108 Spreadst of 113654 add changer animation start */
            /*
            final boolean plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            final int icon = plugged ? R.drawable.stat_sys_battery_charge
                                     : R.drawable.stat_sys_battery;
            int N = mIconViews.size();
            for (int i=0; i<N; i++) {
                ImageView v = mIconViews.get(i);
                v.setImageResource(icon);
                v.setImageLevel(level);
                v.setContentDescription(mContext.getString(R.string.accessibility_battery_level,
                        level));
            */
            int status = intent.getIntExtra("status",
                    BatteryManager.BATTERY_STATUS_UNKNOWN);
            int icon;
            int N = 0;
            if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                // icon = R.drawable.stat_sys_battery_charge;
                // replace by animation
                if (m_Anim_Charge_a == null) {
                    m_Anim_Charge_a = (AnimationDrawable) mContext
                            .getResources().getDrawable(
                                    R.anim.stat_sys_battery_charge_anim_a);
                }
                if (m_Anim_Charge_b == null) {
                    m_Anim_Charge_b = (AnimationDrawable) mContext
                            .getResources().getDrawable(
                                    R.anim.stat_sys_battery_charge_anim_b);
                }
                if (m_Anim_Charge_c == null) {
                    m_Anim_Charge_c = (AnimationDrawable) mContext
                            .getResources().getDrawable(
                                    R.anim.stat_sys_battery_charge_anim_c);
                }
                if (m_Anim_Charge_d == null) {
                    m_Anim_Charge_d = (AnimationDrawable) mContext
                            .getResources().getDrawable(
                                    R.anim.stat_sys_battery_charge_anim_d);
                }
                if (m_Anim_Charge_e == null) {
                    m_Anim_Charge_e = (AnimationDrawable) mContext
                            .getResources().getDrawable(
                                    R.anim.stat_sys_battery_charge_anim_e);
                }
                if (m_Anim_Charge_f == null) {
                    m_Anim_Charge_f = (AnimationDrawable) mContext
                            .getResources().getDrawable(
                                    R.anim.stat_sys_battery_charge_anim_f);
                }
                if (m_Anim_Charge_g == null) {
                    m_Anim_Charge_g = (AnimationDrawable) mContext
                            .getResources().getDrawable(
                                    R.anim.stat_sys_battery_charge_anim_g);
                }
                N = mIconViews.size();
                for (int i = 0; i < N; i++) {
                    ImageView v = mIconViews.get(i);
                    v.setImageDrawable(null);
                    if (level > LEVEL_G) {
                        v.setBackgroundDrawable(m_Anim_Charge_g);
                    } else if (level > LEVEL_F) {
                        v.setBackgroundDrawable(m_Anim_Charge_f);
                    } else if (level > LEVEL_E) {
                        v.setBackgroundDrawable(m_Anim_Charge_e);
                    } else if (level > LEVEL_D) {
                        v.setBackgroundDrawable(m_Anim_Charge_d);
                    } else if (level > LEVEL_C) {
                        v.setBackgroundDrawable(m_Anim_Charge_c);
                    } else if (level > LEVEL_B) {
                        v.setBackgroundDrawable(m_Anim_Charge_b);
                    } else {
                        v.setBackgroundDrawable(m_Anim_Charge_a);
                    }
                    v.setImageLevel(level);
                    AnimationDrawable d = (AnimationDrawable) v.getBackground();
                    d.setAlpha(255);
                    d.start();
                    v.setContentDescription(mContext.getString(
                            R.string.accessibility_battery_level, level));
                }
            } else {
                icon = R.drawable.stat_sys_battery;
                N = mIconViews.size();
                for (int i = 0; i < N; i++) {
                    ImageView v = mIconViews.get(i);
                    // v.setBackgroundDrawable(null);//add for clean animation
                    Drawable d = v.getBackground();
                    if (d != null && d instanceof AnimationDrawable) {
                        ((AnimationDrawable) d).setAlpha(0);
                        ((AnimationDrawable) d).stop();
                    }
                    v.setImageResource(icon);
                    v.setImageLevel(level);
                    v.setContentDescription(mContext.getString(R.string.accessibility_battery_level, level));
                }
                /* Modify 20130108 Spreadst of 113654 add changer animation end*/
            }
            N = mLabelViews.size();
            for (int i=0; i<N; i++) {
                TextView v = mLabelViews.get(i);
                v.setText(mContext.getString(R.string.status_bar_settings_battery_meter_format,
                        level));
            }
        }
    }
}
