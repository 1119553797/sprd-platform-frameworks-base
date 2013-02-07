/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.systemui.statusbar;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import com.android.systemui.statusbar.policy.NetworkController;

import com.android.systemui.R;

// Intimately tied to the design of res/layout/signal_cluster_view.xml
public class SignalClusterView
        extends LinearLayout
        implements NetworkController.SignalCluster {

    static final boolean DEBUG = false;
    static final String TAG = "SignalClusterView";

    NetworkController mNC;

    private boolean mWifiVisible = false;
    //add by TS_LC for show data call icon :start
    private boolean mWifiConnected = false;
    //add by TS_LC for show data call icon :end
    private int mWifiStrengthId = 0, mWifiActivityId = 0;
    private boolean mMobileVisible = false;
    private boolean mDataVisible = false;
    private int mPhoneColor=0;
    //add DSDS start
    private boolean mMobileVisible1= false;
    private boolean mDataVisible1 = false;
    private int mPhoneColor1=0;
    //add DSDS end
    private int mMobileStrengthId = 0, mMobileActivityId = 0, mMobileTypeId = 0;

    //add DSDS start
    private int mMobileStrengthId1 = 0,mMobileActivityId1= 0, mMobileTypeId1 =0;
    //add DSDS end

    private boolean mIsAirplaneMode = false;
    private String mWifiDescription, mMobileDescription, mMobileTypeDescription;
    //add DSDS start
    private String mMobileDescription1, mMobileTypeDescription1;
    //add DSDS end
    ViewGroup mWifiGroup, mMobileGroup, mMobileDataType;
    ImageView mWifi, mMobile, mWifiActivity, mMobileActivity, mMobileType;

    //add DSDS start
    ViewGroup mMobileGroup1, mMobileDataType1;
    ImageView mMobile1,mMobileActivity1,mMobileType1;
    //add DSDS end

    View mSpacer;

    public SignalClusterView(Context context) {
        this(context, null);
    }

    public SignalClusterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SignalClusterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setNetworkController(NetworkController nc) {
        if (DEBUG) Slog.d(TAG, "NetworkController=" + nc);
        mNC = nc;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mWifiGroup      = (ViewGroup) findViewById(R.id.wifi_combo);
        mWifi           = (ImageView) findViewById(R.id.wifi_signal);
        mWifiActivity   = (ImageView) findViewById(R.id.wifi_inout);
        mMobileGroup    = (ViewGroup) findViewById(R.id.mobile_combo);
        mMobile         = (ImageView) findViewById(R.id.mobile_signal);
        mMobileDataType = (ViewGroup) findViewById(R.id.mobile_data_type);
        mMobileActivity = (ImageView) findViewById(R.id.mobile_inout);
        mMobileType     = (ImageView) findViewById(R.id.mobile_type);

        //add DSDS start
        mMobileGroup1    = (ViewGroup) findViewById(R.id.mobile_combo1);
        mMobile1         = (ImageView) findViewById(R.id.mobile_signal1);
        mMobileDataType1 = (ViewGroup) findViewById(R.id.mobile_data_type1);
        mMobileActivity1 = (ImageView) findViewById(R.id.mobile_inout1);
        mMobileType1     = (ImageView) findViewById(R.id.mobile_type1);
        //add DSDS end

        mSpacer         =             findViewById(R.id.spacer);

        apply();
        apply2();
    }

    @Override
    protected void onDetachedFromWindow() {
        mWifiGroup      = null;
        mWifi           = null;
        mWifiActivity   = null;
        mMobileGroup    = null;
        mMobile         = null;
        mMobileDataType = null;
        mMobileActivity = null;
        mMobileType     = null;
        //add DSDS start
        mMobileGroup1    = null;
        mMobile1         = null;
        mMobileDataType1 = null;
        mMobileActivity1 = null;
        mMobileType1     = null;
        //add DSDS end
        super.onDetachedFromWindow();
    }

    public void setWifiIndicators(boolean visible,boolean connected, int strengthIcon, int activityIcon,
            String contentDescription) {
        mWifiVisible = visible;
        //add by TS_LC for data call icon :start
        mWifiConnected = connected;
        //add by TS_LC for data call icon :end
        mWifiStrengthId = strengthIcon;
        mWifiActivityId = activityIcon;
        mWifiDescription = contentDescription;

        apply();
    }

    public void setMobileDataIndicators(boolean visible, int strengthIcon, boolean mDataConnected, int activityIcon,
            int typeIcon, String contentDescription, String typeContentDescription, int phoneColor,int phoneId) {
        if (phoneId == 0) {
            mMobileVisible = visible;
            mMobileStrengthId = strengthIcon;
            mMobileActivityId = activityIcon;
            mMobileTypeId = typeIcon;
            mMobileDescription = contentDescription;
            mMobileTypeDescription = typeContentDescription;
            mDataVisible = mDataConnected;
            mPhoneColor=phoneColor;
            apply();
        } else {
            mMobileVisible1 = visible;
            mMobileStrengthId1 = strengthIcon;
            mMobileActivityId1 = activityIcon;
            mMobileTypeId1 = typeIcon;
            mMobileDescription1 = contentDescription;
            mMobileTypeDescription1 = typeContentDescription;
            mDataVisible1 = mDataConnected;
            mPhoneColor1=phoneColor;
            apply2();
        }
    }

    @Override
    public void setIsAirplaneMode(boolean is) {
        mIsAirplaneMode = is;

        apply();
        apply2();
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        // Standard group layout onPopulateAccessibilityEvent() implementations
        // ignore content description, so populate manually
        if (mWifiVisible && mWifiGroup.getContentDescription() != null)
            event.getText().add(mWifiGroup.getContentDescription());
        if (mMobileVisible && mMobileGroup.getContentDescription() != null)
            event.getText().add(mMobileGroup.getContentDescription());
        return super.dispatchPopulateAccessibilityEvent(event);
    }
    public void setMobileSignalColor(int phoneColor,int phoneId){
		if (phoneId == 0) {
			mPhoneColor = phoneColor;
		} else if (phoneId == 1) {
			mPhoneColor1 = phoneColor;
		} 
     }
    // Run after each indicator change.
    private void apply() {
        if (mWifiGroup == null) return;

        if (mWifiVisible) {
            mWifiGroup.setVisibility(View.VISIBLE);
            mWifi.setImageResource(mWifiStrengthId);
            mWifiActivity.setImageResource(mWifiActivityId);
            mWifiGroup.setContentDescription(mWifiDescription);
        } else {
            mWifiGroup.setVisibility(View.GONE);
        }

        if (DEBUG) Slog.d(TAG,
                String.format("wifi: %s sig=%d act=%d",
                    (mWifiVisible ? "VISIBLE" : "GONE"),
                    mWifiStrengthId, mWifiActivityId));

        if (mMobileVisible && mMobileStrengthId!=0) {
            mMobileGroup.setVisibility(View.VISIBLE);
            mMobile.setBackgroundColor(mPhoneColor);
            mMobile.setImageResource(mMobileStrengthId);
            mMobileDataType.setBackgroundColor(mPhoneColor);
            mMobileActivity.setImageResource(mMobileActivityId);
            mMobileType.setImageResource(mMobileTypeId);
            mMobileGroup.setContentDescription(mMobileTypeDescription + " " + mMobileDescription);
        } else {
            mMobileGroup.setVisibility(View.GONE);
        }

        if (mMobileVisible && mWifiVisible && mIsAirplaneMode) {
            mSpacer.setVisibility(View.INVISIBLE);
        } else {
            mSpacer.setVisibility(View.GONE);
        }

        if (DEBUG) Slog.d(TAG,
                String.format("mobile: %s sig=%d act=%d typ=%d",
                    (mMobileVisible ? "VISIBLE" : "GONE"),
                    mMobileStrengthId, mMobileActivityId, mMobileTypeId));
        //mod by TS_LC for data call icon :start
        if (mIsAirplaneMode) {
            mMobileType.setVisibility(View.GONE);
            mMobileActivity.setVisibility(View.GONE);
        } else {
            mMobileType.setVisibility(mMobileVisible ? View.VISIBLE : View.GONE);
            mMobileActivity.setVisibility(mDataVisible ? View.VISIBLE : View.GONE);
        }
       //mod by TS_LC for data call icon :end
    }
    //add DSDS start
    private void apply2() {
        if (mWifiGroup == null) return;

        if (mWifiVisible) {
            mWifiGroup.setVisibility(View.VISIBLE);
            mWifi.setImageResource(mWifiStrengthId);
            mWifiActivity.setImageResource(mWifiActivityId);
            mWifiGroup.setContentDescription(mWifiDescription);
        } else {
            mWifiGroup.setVisibility(View.GONE);
        }

        if (DEBUG) Slog.d(TAG,
                String.format("wifi: %s sig=%d act=%d",
                    (mWifiVisible ? "VISIBLE" : "GONE"),
                    mWifiStrengthId, mWifiActivityId));

        if (mMobileVisible1 && mMobileStrengthId1!=0) {
            mMobileGroup1.setVisibility(View.VISIBLE);
            mMobile1.setBackgroundColor(mPhoneColor1);
            mMobile1.setImageResource(mMobileStrengthId1);
            mMobileDataType1.setBackgroundColor(mPhoneColor1);
            mMobileActivity1.setImageResource(mMobileActivityId1);
            mMobileType1.setImageResource(mMobileTypeId1);
            mMobileGroup1.setContentDescription(mMobileTypeDescription1 + " " + mMobileDescription1);
        } else {
            mMobileGroup1.setVisibility(View.GONE);
        }

        if (mMobileVisible1 && mWifiVisible && mIsAirplaneMode) {
            mSpacer.setVisibility(View.INVISIBLE);
        } else {
            mSpacer.setVisibility(View.GONE);
        }

        if (DEBUG) Slog.d(TAG,
                String.format("mobile: %s sig=%d act=%d typ=%d",
                    (mMobileVisible1 ? "VISIBLE" : "GONE"),
                    mMobileStrengthId1, mMobileActivityId1, mMobileTypeId1));

        //mod by TS_LC for data call icon :start
        if (mIsAirplaneMode) {
            mMobileType1.setVisibility(View.GONE);
            mMobileActivity1.setVisibility(View.GONE);
        } else {
            mMobileType1.setVisibility(mMobileVisible1 ? View.VISIBLE : View.GONE);
            mMobileActivity1.setVisibility(mDataVisible1 ? View.VISIBLE : View.GONE);
        }
        //mod by TS_LC for data call icon :end
    }
    //add DSDS end
}
