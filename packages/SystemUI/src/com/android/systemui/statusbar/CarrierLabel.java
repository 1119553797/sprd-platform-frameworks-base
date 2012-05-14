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

package com.android.systemui.statusbar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Telephony;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.view.View;
import android.widget.TextView;

import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.TelephonyIntents;

/**
 * This widget display an analogic clock with two hands for hours and
 * minutes.
 */
public class CarrierLabel extends TextView {
    private boolean mAttached;
    boolean  mSimMissed = false;
    boolean  mSimBlocked = false;
    boolean  mCardLocked = false;
    boolean  mNetworkLocked = false;
    public CarrierLabel(Context context) {
        this(context, null);
    }

    public CarrierLabel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CarrierLabel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        updateNetworkName(false, null, false, null,"");
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

//        if (!mAttached) {
//            mAttached = true;
//            IntentFilter filter = new IntentFilter();
//            filter.addAction(Telephony.Intents.SPN_STRINGS_UPDATED_ACTION);
//            filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
//            getContext().registerReceiver(mIntentReceiver, filter, null, getHandler());
//        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
//        if (mAttached) {
//            getContext().unregisterReceiver(mIntentReceiver);
//            mAttached = false;
//        }
    }

//    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            String action = intent.getAction();
//            if (Telephony.Intents.SPN_STRINGS_UPDATED_ACTION.equals(action)) {
//                updateNetworkName(intent.getBooleanExtra(Telephony.Intents.EXTRA_SHOW_SPN, false),
//                        intent.getStringExtra(Telephony.Intents.EXTRA_SPN),
//                        intent.getBooleanExtra(Telephony.Intents.EXTRA_SHOW_PLMN, false),
//                        intent.getStringExtra(Telephony.Intents.EXTRA_PLMN),
//                        intent.getStringExtra(Telephony.Intents.EXTRA_NETWORK_TYPE));
//            }
//
//           //Added  start on 2012-01-17
//	    else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
//		String stateExtra = intent.getStringExtra(IccCard.INTENT_KEY_ICC_STATE);
//		Log.i("CarrierLabel", "Receive "+intent.getAction() + " IccCard is "+stateExtra);
//            	if (IccCard.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
//            		mSimMissed = true;
//            		updateForSimCardChanged(com.android.internal.R.string.lockscreen_missing_sim_message_short);
//            	}
//            	else if (IccCard.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)){
//            		mSimBlocked = true;
//            		updateForSimCardChanged(com.android.internal.R.string.lockscreen_sim_locked_message);
//            		
//            	}
//            	else if (IccCard.INTENT_VALUE_ICC_BLOCKED.equals(stateExtra)) {
//            		updateForSimCardChanged(com.android.internal.R.string.lockscreen_blocked_sim_message_short);
//            		
//            	}
//            	else if (IccCard.INTENT_VALUE_ICC_READY.equals(stateExtra)) {
//            		mSimMissed = false;
//           		mSimBlocked = false;
//            	}
//            	
//            	
//            }
//            //Added  end on 2012-01-17
//           
//        }
//
//       
//    };


    void updateForSimCardChanged(int message){
	 setVisibility(View.VISIBLE);
	 setText(message);
    }
	

    void updateNetworkName(boolean showSpn, String spn, boolean showPlmn, String plmn,String networkType) {
        if (false) {
            Slog.d("CarrierLabel", "updateNetworkName showSpn=" + showSpn + " spn=" + spn
                    + " showPlmn=" + showPlmn + " plmn=" + plmn);
        }
        
        if(true)
        {
            Log.i("StatusBar CarrierLabel", "updateNetworkName showSpn=" + showSpn + " spn=" + spn
                   + " showPlmn=" + showPlmn + " plmn=" + plmn+" networkType="+networkType);
        }

     	        // Modify start on 2012-01-16 for 8930/8932
		// StringBuilder str = new StringBuilder();
		// boolean something = false;
		// if (showPlmn && plmn != null) {
		// str.append(plmn);
		// something = true;
		// }
		// if (showSpn && spn != null) {
		// if (something) {
		// str.append('\n');
		// }
		// str.append(spn);
		// something = true;
		// }
		// if (something) {
		// setText(str.toString());
		// } else {
		// setText(com.android.internal.R.string.lockscreen_carrier_default);
		//
		
		if (!showSpn && !showPlmn) {
			setText(com.android.internal.R.string.lockscreen_carrier_default);
		} 
		if(showSpn && !showPlmn){
			if(spn != null){
				setText(spn+networkType);
			}
			else{
				setText(com.android.internal.R.string.lockscreen_carrier_default);
			}
		}
		if(!showSpn && showPlmn){
			if(plmn != null){
				setText(plmn+networkType);
			}
			else{
				setText(com.android.internal.R.string.lockscreen_carrier_default);
			}
		}
		if(showSpn && showPlmn){
			if(spn != null && plmn != null){
				setText(plmn+networkType);
			}
			if(spn == null && plmn != null){
				setText(plmn+networkType);
			}
			if(spn == null && plmn == null){
				setText(com.android.internal.R.string.lockscreen_carrier_default);
			}
		}
		if(mSimMissed){
	        	setVisibility(View.VISIBLE);
	        	setText(com.android.internal.R.string.lockscreen_missing_sim_message_short);
	        }
	        if(mSimBlocked){
	        	setVisibility(View.VISIBLE);
	        	setText(com.android.internal.R.string.lockscreen_blocked_sim_message_short);
	        }
	        if(mCardLocked){
	        	setVisibility(View.VISIBLE);
	        	setText(com.android.internal.R.string.lockscreen_sim_card_locked_message);
	        }
            if(mNetworkLocked){
            	setVisibility(View.VISIBLE);
            	setText(com.android.internal.R.string.lockscreen_sim_network_locked_message);
	        }
      // Modify end on 2012-01-16 for 8930/8932
    }

    
}


