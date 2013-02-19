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
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.LayoutInflater;
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
    private boolean[] mMobileVisible;
    private boolean[] mDataVisible;
    private int[] mPhoneColor;

    private int[] mMobileStrengthId, mMobileActivityId, mMobileTypeId;

    private boolean mIsAirplaneMode = false;
    private String[] mMobileDescription, mMobileTypeDescription;
    private String mWifiDescription;

    ViewGroup mWifiGroup, mMobileLayout;
    ImageView mWifi, mWifiActivity;

    ViewGroup[] mMobileDataType;
    View[] mMobileGroup;
    ImageView[] mMobile, mMobileActivity, mMobileType;
    ImageView mAirplane;
    Context mContext;
    
    private int mPhoneNumber = 0;
    
    View mSpacer;

    public SignalClusterView(Context context) {
        this(context, null);
    }

    public SignalClusterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SignalClusterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        init();
    }

    public void setNetworkController(NetworkController nc) {
        if (DEBUG) Slog.d(TAG, "NetworkController=" + nc);
        mNC = nc;
    }
    
    protected void init(){
    	mPhoneNumber = TelephonyManager.getPhoneCount();
    	mMobileVisible = new boolean[mPhoneNumber];
    	mDataVisible = new boolean[mPhoneNumber];
    	mMobileStrengthId = new int[mPhoneNumber];
    	mMobileActivityId = new int[mPhoneNumber];
    	mMobileTypeId = new int[mPhoneNumber];
    	mPhoneColor = new int[mPhoneNumber];

    	mMobileGroup = new ViewGroup[mPhoneNumber];
    	mMobileDataType = new ViewGroup[mPhoneNumber];
    	mMobile = new ImageView[mPhoneNumber];
    	mMobileActivity = new ImageView[mPhoneNumber];
    	mMobileType = new ImageView[mPhoneNumber];
    	mMobileDescription = new String[mPhoneNumber];
    	mMobileTypeDescription= new String[mPhoneNumber];

    	for(int i=0;i<mPhoneNumber;i++){
    		mMobileVisible[i] = false;
    		mDataVisible[i] = false;
    		mMobileStrengthId[i] = 0;
    		mMobileActivityId[i] = 0;
    		mMobileTypeId[i] = 0;
    		mPhoneColor[i] = 0;
    	}
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mWifiGroup      = (ViewGroup) findViewById(R.id.wifi_combo);
        mWifi           = (ImageView) findViewById(R.id.wifi_signal);
        mWifiActivity   = (ImageView) findViewById(R.id.wifi_inout);
        
        mMobileLayout   = (ViewGroup) findViewById(R.id.mobile_layout);
        LayoutInflater inflater = LayoutInflater.from(mContext);
        mSpacer         =             findViewById(R.id.spacer);
        
        for(int i=0;i<mPhoneNumber;i++){
        	mMobileGroup[i] = inflater.inflate(R.layout.signal_cluster_item_view,null);
        	mMobileLayout.addView(mMobileGroup[i], i);
        	mMobileGroup[i]    = (ViewGroup) mMobileGroup[i].findViewById(R.id.mobile_combo);
        	mMobile[i]         = (ImageView) mMobileGroup[i].findViewById(R.id.mobile_signal);
        	mMobileDataType[i] = (ViewGroup) mMobileGroup[i].findViewById(R.id.mobile_data_type);
        	mMobileActivity[i] = (ImageView) mMobileGroup[i].findViewById(R.id.mobile_inout);
        	mMobileType[i]     = (ImageView) mMobileGroup[i].findViewById(R.id.mobile_type);
        	apply(i);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        mWifiGroup      = null;
        mWifi           = null;
        mWifiActivity   = null;
        mMobileLayout   = null;
        for(int i=0;i<mPhoneNumber;i++){
        	mMobileGroup[i]    = null;
        	mMobile[i]         = null;
        	mMobileActivity[i] = null;
        	mMobileType[i]     = null;
        	mMobileDataType[i] = null;
        }
        mMobileGroup    = null;
        mMobile         = null;
        mMobileDataType = null;
        mMobileActivity = null;
        mMobileType     = null;
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

        apply(0);
    }

    public void setMobileDataIndicators(boolean visible, int strengthIcon, boolean mDataConnected, int activityIcon,
            int typeIcon, String contentDescription, String typeContentDescription, int phoneColor,int phoneId) {
    	if(phoneId >= mPhoneNumber){
    		Slog.d(TAG, "setMobileDataIndicators,invalid phoneId=" + phoneId);
    		return;
    	}
    	mMobileVisible[phoneId] = visible;
    	mMobileStrengthId[phoneId] = strengthIcon;
    	mMobileActivityId[phoneId] = activityIcon;
    	mMobileTypeId[phoneId] = typeIcon;
    	mMobileDescription[phoneId] = contentDescription;
    	mMobileTypeDescription[phoneId] = typeContentDescription;
    	mDataVisible[phoneId] = mDataConnected;
    	mPhoneColor[phoneId] =phoneColor;
    	apply(phoneId);
    	
    }

    @Override
    public void setIsAirplaneMode(boolean is) {
        mIsAirplaneMode = is;

        for(int i=0;i<mPhoneNumber;i++){
        	apply(i);
        }
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        // Standard group layout onPopulateAccessibilityEvent() implementations
        // ignore content description, so populate manually
        if (mWifiVisible && mWifiGroup.getContentDescription() != null)
            event.getText().add(mWifiGroup.getContentDescription());
        boolean isMobileVisible = false;
        for(int i=0;i<mPhoneNumber;i++){
        	if(mMobileVisible[i]){ 
        		isMobileVisible = true;
        	}
        }
        if (isMobileVisible && mMobileLayout.getContentDescription() != null)
            event.getText().add(mMobileLayout.getContentDescription());
        return super.dispatchPopulateAccessibilityEvent(event);
    }
    
    public void setMobileSignalColor(int phoneColor,int phoneId){
    	if(phoneId >= mPhoneNumber){
    		Slog.d(TAG, "setMobileSignalColor,invalid phoneId=" + phoneId);
    		return;
    	}
    	mPhoneColor[phoneId] = phoneColor;
    }
    
    // Run after each indicator change.
    private void apply(int phoneId) {
        if (mWifiGroup == null) return;
        if(phoneId >= mPhoneNumber){
        	Slog.d(TAG, "apply,invalid phoneId=" + phoneId);
        	return;
        }

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
        
        if (mMobileVisible[phoneId] && mMobileStrengthId[phoneId] !=0) {
    		mMobileGroup[phoneId].setVisibility(View.VISIBLE);
    		mMobile[phoneId].setImageResource(mMobileStrengthId[phoneId]);
    		mMobileActivity[phoneId].setImageResource(mMobileActivityId[phoneId]);
    		mMobileType[phoneId].setImageResource(mMobileTypeId[phoneId]);
    		mMobile[phoneId].setBackgroundColor(mPhoneColor[phoneId]);
            mMobileDataType[phoneId].setBackgroundColor(mPhoneColor[phoneId]);
    		mMobileGroup[phoneId].setContentDescription(mMobileTypeDescription[phoneId] + " " + mMobileDescription[phoneId]);
    	} else {
    		mMobileGroup[phoneId].setVisibility(View.GONE);
    	}

        boolean mobileVisible = false;
        for(int i=0;i<mPhoneNumber;i++){
        	if(mMobileVisible[i]){
        		mobileVisible = true;
        		break;
        	}
        }
        if (mobileVisible && mWifiVisible && mIsAirplaneMode) {
            mSpacer.setVisibility(View.INVISIBLE);
        } else {
            mSpacer.setVisibility(View.GONE);
        }

        if (DEBUG) Slog.d(TAG,
                String.format("mobile: %s sig=%d act=%d typ=%d",
                    (mMobileVisible[phoneId] ? "VISIBLE" : "GONE"),
                    mMobileStrengthId, mMobileActivityId, mMobileTypeId));
        //mod by TS_LC for data call icon :start
        if (mIsAirplaneMode) {
            mMobileType[phoneId].setVisibility(View.GONE);
            mMobileActivity[phoneId].setVisibility(View.GONE);
        } else {
            mMobileType[phoneId].setVisibility(mMobileVisible[phoneId] ? View.VISIBLE : View.GONE);
            mMobileActivity[phoneId].setVisibility(mDataVisible[phoneId] ? View.VISIBLE : View.GONE);
        }
       //mod by TS_LC for data call icon :end
    }
  
}
