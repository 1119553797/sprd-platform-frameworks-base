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
import android.os.SystemProperties;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;

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
    private int mWifiStrengthId = 0, mWifiActivityId = 0;

    /* SPRD: for multi-sim @{ */
    //private boolean mMobileVisible = false;
    //private int mMobileStrengthId = 0, mMobileActivityId = 0, mMobileTypeId = 0;
    private boolean[] mMobileVisible;
    private boolean[] mDataVisible;

    private int[] mMobileStrengthId, mMobileActivityId, mMobileTypeId, mMobileCardId;
    /* @} */
    private boolean mIsAirplaneMode = false;
    private int mAirplaneIconId = 0;

    /* SPRD: for multi-sim @{ */
    //private String mWifiDescription, mMobileDescription, mMobileTypeDescription;
    //ViewGroup mWifiGroup, mMobileGroup;
    //ImageView mWifi, mMobile, mWifiActivity, mMobileActivity, mMobileType, mAirplane;
    private String mWifiDescription;
    private String[] mMobileDescription, mMobileTypeDescription;
    ViewGroup mWifiGroup, mMobileLayout;

    ViewGroup[] mMobileDataType, mMobileSignalType;
    View[] mMobileGroup;
    ImageView mWifi, mWifiActivity, mAirplane;
    ImageView[] mMobile, mMobileActivity, mMobileType, mMobileCard;
    Context mContext;
    private int mPhoneNumber = 0;
    /* @} */

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

    /*  SPRD:  for  multi-sim  @{ */
    protected void init(){
        mPhoneNumber = TelephonyManager.getPhoneCount();
        mMobileVisible = new boolean[mPhoneNumber];
        mDataVisible = new boolean[mPhoneNumber];
        mMobileStrengthId = new int[mPhoneNumber];
        mMobileActivityId = new int[mPhoneNumber];
        mMobileCardId = new int[mPhoneNumber];

        mMobileTypeId = new int[mPhoneNumber];

        mMobileGroup = new ViewGroup[mPhoneNumber];
        mMobileSignalType = new ViewGroup[mPhoneNumber];
        // mMobileDataType = new ViewGroup[mPhoneNumber];
        mMobile = new ImageView[mPhoneNumber];
        // mMobileSignalBar = new ImageView[mPhoneNumber];
        mMobileCard = new ImageView[mPhoneNumber];
        mMobileActivity = new ImageView[mPhoneNumber];
        mMobileType = new ImageView[mPhoneNumber];
        mMobileDescription = new String[mPhoneNumber];
        mMobileTypeDescription = new String[mPhoneNumber];

        for (int i = 0; i < mPhoneNumber; i++) {
            mMobileVisible[i] = false;
            mDataVisible[i] = false;
            mMobileStrengthId[i] = 0;
            mMobileActivityId[i] = 0;
            mMobileCardId[i] = 0;
            mMobileTypeId[i] = 0;
        }
    }
    /* @}  */

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mWifiGroup      = (ViewGroup) findViewById(R.id.wifi_combo);
        /* SPRD：ADD for universe_ui_support on 20130831 @{ */
        if (!mWifiVisible) {
            mWifiGroup.setVisibility(View.GONE);
        }
        /* @} */
        /* SPRD: for multi-sim @{ */
        if(!mWifiVisible)mWifiGroup.setVisibility(View.GONE);
        mWifi           = (ImageView) findViewById(R.id.wifi_signal);
        mWifiActivity   = (ImageView) findViewById(R.id.wifi_inout);
//        mMobileGroup    = (ViewGroup) findViewById(R.id.mobile_combo);
//        mMobile         = (ImageView) findViewById(R.id.mobile_signal);
//        mMobileActivity = (ImageView) findViewById(R.id.mobile_inout);
//        mMobileType     = (ImageView) findViewById(R.id.mobile_type);
        mMobileLayout   = (ViewGroup) findViewById(R.id.mobile_layout);
        LayoutInflater inflater = LayoutInflater.from(mContext);
        mSpacer         =             findViewById(R.id.spacer);
        mAirplane       = (ImageView) findViewById(R.id.airplane);


        //apply();
        for(int i=0;i<mPhoneNumber;i++){
            mMobileGroup[i] = inflater.inflate(R.layout.signal_cluster_item_view,null);
            mMobileLayout.addView(mMobileGroup[i], i);
            mMobileGroup[i]    = (ViewGroup) mMobileGroup[i].findViewById(R.id.mobile_combo);
           /** SPRD: add for cucc no sim sinal icon @{  */
            mMobileSignalType[i] = (ViewGroup) mMobileGroup[i].findViewById(R.id.mobile_signal_type);
            mMobileCard[i] = (ImageView) mMobileGroup[i].findViewById(R.id.mobile_card);
           /** @} */
            //mMobileSignalBar[i] = (ImageView) mMobileGroup[i].findViewById(R.id.mobile_card);
            mMobile[i]         = (ImageView) mMobileGroup[i].findViewById(R.id.mobile_signal);
            //mMobileDataType[i] = (ViewGroup) mMobileGroup[i].findViewById(R.id.mobile_data_type);
            mMobileActivity[i] = (ImageView) mMobileGroup[i].findViewById(R.id.mobile_inout);
            mMobileType[i]     = (ImageView) mMobileGroup[i].findViewById(R.id.mobile_type);
            apply(i);
        }
        /* @} */
    }

    @Override
    protected void onDetachedFromWindow() {
        mWifiGroup      = null;
        mWifi           = null;
        mWifiActivity   = null;
        mMobileGroup    = null;
        mMobile         = null;
        mMobileActivity = null;
        mMobileType     = null;
        mSpacer         = null;
        mAirplane       = null;
        /* SPRD: for multi-sim @{ */
        for(int i=0;i<mPhoneNumber;i++){
            mMobileGroup[i]    = null;
            mMobileCard[i] = null;
            //mMobileSignalBar[i] = null;
            mMobile[i]         = null;
            mMobileActivity[i] = null;
            mMobileType[i]     = null;
           // mMobileDataType[i] = null;
           mMobileSignalType[i] = null;
        }
        mMobileCard = null;
        mMobileSignalType = null;
        /* @} */
        super.onDetachedFromWindow();
    }

    @Override
    public void setWifiIndicators(boolean visible, int strengthIcon, int activityIcon,
            String contentDescription) {
        mWifiVisible = visible;
        mWifiStrengthId = strengthIcon;
        mWifiActivityId = activityIcon;
        mWifiDescription = contentDescription;

        //apply();
        apply(0);
    }

    /* SPRD: for multi-sim @{ */
    @Override
    public void setMobileDataIndicators(boolean visible, int strengthIcon, boolean mDataConnected, int activityIcon,
            int typeIcon, String contentDescription, String typeContentDescription,int cardIcon, int phoneId) {
//        mMobileVisible = visible;
//        mMobileStrengthId = strengthIcon;
//        mMobileActivityId = activityIcon;
//        mMobileTypeId = typeIcon;
//        mMobileDescription = contentDescription;
//        mMobileTypeDescription = typeContentDescription;
//        apply();
        Log.d("lile", "## "+strengthIcon+" "+visible+" "+phoneId);
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
        mMobileCardId[phoneId] = cardIcon;
        apply(phoneId);
    }
    /* @} */

    @Override
    public void setIsAirplaneMode(boolean is, int airplaneIconId) {
        mIsAirplaneMode = is;
        mAirplaneIconId = airplaneIconId;
        /* SPRD: for multi-sim @{ */
        //apply();
        for(int i=0;i<mPhoneNumber;i++){
            apply(i);
        }
        /* @} */
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        // Standard group layout onPopulateAccessibilityEvent() implementations
        // ignore content description, so populate manually
        if (mWifiVisible && mWifiGroup.getContentDescription() != null)
            event.getText().add(mWifiGroup.getContentDescription());
        /* SPRD: for multi-sim @{ */
        // if (mMobileVisible && mMobileGroup.getContentDescription() != null)
        //     event.getText().add(mMobileGroup.getContentDescription());
        boolean isMobileVisible = false;
        for(int i=0;i<mPhoneNumber;i++){
            if (mMobileVisible[i]) {
                isMobileVisible = true;
            }
        }
        if (isMobileVisible && mMobileLayout.getContentDescription() != null)
            event.getText().add(mMobileLayout.getContentDescription());
        /* @} */
        return super.dispatchPopulateAccessibilityEvent(event);
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);

        if (mWifi != null) {
            mWifi.setImageDrawable(null);
        }
        if (mWifiActivity != null) {
            mWifiActivity.setImageDrawable(null);
        }

        if(mAirplane != null) {
            mAirplane.setImageDrawable(null);
        }
        /* SPRD: for multi-sim @{ */
        for (int i = 0; i < mPhoneNumber; i++) {
            if (mMobile != null && mMobile[i]!=null ) {
                mMobile[i].setImageDrawable(null);
            }
            if (mMobileActivity != null && mMobileActivity[i] != null) {
                mMobileActivity[i].setImageDrawable(null);
            }
            if (mMobileType != null && mMobileType[i] != null) {
                mMobileType[i].setImageDrawable(null);
            }
            apply(i);
        }
        /* @} */
    }

    // Run after each indicator change.
    private void apply(int phoneId) {
        if (mWifiGroup == null) return;
        /* SPRD: for multi-sim @{ */
        if(phoneId >= mPhoneNumber){
            Slog.d(TAG, "apply,invalid phoneId=" + phoneId);
            return;
        }
        /* @} */

        if (mWifiVisible) {
                mWifi.setImageResource(mWifiStrengthId);
            mWifiActivity.setImageResource(mWifiActivityId);

            mWifiGroup.setContentDescription(mWifiDescription);
            mWifiGroup.setVisibility(View.VISIBLE);
        } else {
            mWifiGroup.setVisibility(View.GONE);
        }

        if (DEBUG) Slog.d(TAG,
                String.format("wifi: %s sig=%d act=%d",
                    (mWifiVisible ? "VISIBLE" : "GONE"),
                    mWifiStrengthId, mWifiActivityId));
        /* SPRD: for multi-sim @{ */
        //if (mMobileVisible && !mIsAirplaneMode) {
        Log.d("lile", "## "+mMobileVisible[phoneId]+" "+mMobileStrengthId[phoneId]+" "+mIsAirplaneMode+" "+phoneId);
        if (mMobileVisible[phoneId] && mMobileStrengthId[phoneId] != 0 && !mIsAirplaneMode) {
            mMobileGroup[phoneId].setVisibility(View.VISIBLE);
            mMobileCard[phoneId].setImageResource(mMobileCardId[phoneId]);
            mMobile[phoneId].setImageResource(mMobileStrengthId[phoneId]);
            mMobileActivity[phoneId].setImageResource(mMobileActivityId[phoneId]);
            mMobileType[phoneId].setImageResource(mMobileTypeId[phoneId]);
            mMobileGroup[phoneId].setContentDescription(mMobileTypeDescription[phoneId] + " "
                    + mMobileDescription[phoneId]);
            //mMobileSignalBar[phoneId].setImageResource(mMobileCardId[phoneId]);
        } else {
            mMobileGroup[phoneId].setVisibility(View.GONE);
        }
        /* @} */
        if (mIsAirplaneMode) {
            mAirplane.setImageResource(mAirplaneIconId);
            mAirplane.setVisibility(View.VISIBLE);
        } else {
            mAirplane.setVisibility(View.GONE);
        }
        /* SPRD: for multi-sim @{ */
        boolean mobileVisible = false;
        for (int i = 0; i < mPhoneNumber; i++) {
            if (mMobileVisible[i]) {
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

        // mMobileType.setVisibility(
        // !mWifiVisible ? View.VISIBLE : View.GONE);

        if (mIsAirplaneMode) {
            mMobileType[phoneId].setVisibility(View.GONE);
            mMobileActivity[phoneId].setVisibility(View.GONE);
           // mMobileSignalBar[phoneId].setVisibility(View.GONE);
            mMobileCard[phoneId].setVisibility(View.GONE);
        } else {
            mMobileType[phoneId].setVisibility(mMobileVisible[phoneId] ? View.VISIBLE : View.GONE);
            mMobileActivity[phoneId].setVisibility(mDataVisible[phoneId] ? View.VISIBLE : View.GONE);
           // mMobileSignalBar[phoneId].setVisibility((mMobileVisible[phoneId]&&"cucc".equals(SystemProperties.get("ro.operator", "")))? View.VISIBLE : View.GONE);
            // SPRD:  add for cucc no sim signal icon
            mMobileCard[phoneId].setVisibility(mMobileVisible[phoneId] && "cucc".equals(SystemProperties.get("ro.operator", "")) ? View.VISIBLE : View.GONE);
       }
        /* @} */
    }
}

