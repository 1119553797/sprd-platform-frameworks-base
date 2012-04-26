/*
 * Copyright (C) 2008 The Android Open Source Project
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


import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.util.Log;
import android.view.SurfaceHolder;

import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.gsm.NetworkInfo;
import com.android.internal.telephony.gsm.GsmDataConnection;
import com.android.internal.telephony.test.SimulatedRadioControl;

import java.util.List;

public class SprdPhoneProxy extends PhoneProxy {
    private static final String LOG_TAG = "SPRDPHONE";

    //***** Class Methods
    public SprdPhoneProxy(Phone phone) {
    	super(phone);
    }

	public void registerForPreciseVideoCallStateChanged(Handler h, int what, Object obj) {
		/*if (mActivePhone.getPhoneType() == Phone.PHONE_TYPE_TD)*/{
			mActivePhone.registerForPreciseVideoCallStateChanged(h, what, obj);
		}
	}

	public void unregisterForPreciseVideoCallStateChanged(Handler h) {
		/*if (mActivePhone.getPhoneType() == Phone.PHONE_TYPE_TD)*/{
			mActivePhone.unregisterForPreciseVideoCallStateChanged(h);
		}
	}

	public void registerForNewRingingVideoCall(Handler h, int what, Object obj) {
		/*if (mActivePhone.getPhoneType() == Phone.PHONE_TYPE_TD)*/{
			mActivePhone.registerForNewRingingVideoCall(h, what, obj);
		}
	}

	public void unregisterForNewRingingVideoCall(Handler h) {
		/*if (mActivePhone.getPhoneType() == Phone.PHONE_TYPE_TD)*/{
			mActivePhone.unregisterForNewRingingVideoCall(h);
		}
	}

	public void registerForIncomingRingVideoCall(Handler h, int what, Object obj) {
		/*if (mActivePhone.getPhoneType() == Phone.PHONE_TYPE_TD)*/{
			mActivePhone.registerForIncomingRingVideoCall(h, what, obj);
		}
	}

	public void unregisterForIncomingRingVideoCall(Handler h) {
		/*if (mActivePhone.getPhoneType() == Phone.PHONE_TYPE_TD)*/{
			mActivePhone.unregisterForIncomingRingVideoCall(h);
		}
	}

	public void registerForVideoCallDisconnect(Handler h, int what, Object obj) {
		/*if (mActivePhone.getPhoneType() == Phone.PHONE_TYPE_TD)*/{
			mActivePhone.registerForVideoCallDisconnect(h, what, obj);
		}
	}

	public void unregisterForVideoCallDisconnect(Handler h) {
		/*if (mActivePhone.getPhoneType() == Phone.PHONE_TYPE_TD)*/{
			mActivePhone.unregisterForVideoCallDisconnect(h);
		}
	}

	public void registerForVideoCallFallBack(Handler h, int what, Object obj){
		/*if (mActivePhone.getPhoneType() == Phone.PHONE_TYPE_TD)*/{
			mActivePhone.registerForVideoCallFallBack(h, what, obj);
		}
	}

	public void unregisterForVideoCallFallBack(Handler h){
		/*if (mActivePhone.getPhoneType() == Phone.PHONE_TYPE_TD)*/{
			mActivePhone.unregisterForVideoCallFallBack(h);
		}
	}

	public void registerForVideoCallFail(Handler h, int what, Object obj){
		/*if (mActivePhone.getPhoneType() == Phone.PHONE_TYPE_TD)*/{
			mActivePhone.registerForVideoCallFail(h, what, obj);
		}
	}

	public void unregisterForVideoCallFail(Handler h){
		/*if (mActivePhone.getPhoneType() == Phone.PHONE_TYPE_TD)*/{
			mActivePhone.unregisterForVideoCallFail(h);
		}
	}
	
	public void registerForRemoteCamera(Handler h, int what, Object obj){
		/*if (mActivePhone.getPhoneType() == Phone.PHONE_TYPE_TD)*/{
			mActivePhone.registerForRemoteCamera(h, what, obj);
		}
	}

	public void unregisterForRemoteCamera(Handler h){
		/*if (mActivePhone.getPhoneType() == Phone.PHONE_TYPE_TD)*/{
			mActivePhone.unregisterForRemoteCamera(h);
		}
	}

	public void registerForVideoCallCodec(Handler h, int what, Object obj){
		/*if (mActivePhone.getPhoneType() == Phone.PHONE_TYPE_TD)*/{
			mActivePhone.registerForVideoCallCodec(h, what, obj);
		}
	}

	public void unregisterForVideoCallCodec(Handler h){
		/*if (mActivePhone.getPhoneType() == Phone.PHONE_TYPE_TD)*/{
			mActivePhone.unregisterForVideoCallCodec(h);
		}
	}

    public void registerForGprsAttached(Handler h,int what, Object obj) {
        mActivePhone.registerForGprsAttached(h, what, obj);
    }

    public void unregisterForGprsAttached(Handler h) {
        mActivePhone.unregisterForGprsAttached(h);
    }

	public CallType getCallType() {
		return mActivePhone.getCallType();
	}
	
	public Connection  dialVP(String dialString) throws CallStateException{
		/*if (mActivePhone.getPhoneType() == Phone.PHONE_TYPE_TD)*/{
			return mActivePhone.dialVP(dialString);
		}
	//	return null;
	}

	public void  fallBack() throws CallStateException{
		/*if (mActivePhone.getPhoneType() == Phone.PHONE_TYPE_TD)*/{
			mActivePhone.fallBack();
		}
	}
	
	public void  acceptFallBack() throws CallStateException{
		/*if (mActivePhone.getPhoneType() == Phone.PHONE_TYPE_TD)*/{
			mActivePhone.acceptFallBack();
		}
	}

	public void  controlCamera(boolean bEnable) throws CallStateException{
		/*if (mActivePhone.getPhoneType() == Phone.PHONE_TYPE_TD)*/{
			mActivePhone.controlCamera(bEnable);
		}
	}

	public void  controlAudio(boolean bEnable) throws CallStateException{
		/*if (mActivePhone.getPhoneType() == Phone.PHONE_TYPE_TD)*/{
			mActivePhone.controlAudio(bEnable);
		}
	}
	
	public void codecVP(int type, Bundle param){
		/*if (mActivePhone.getPhoneType() == Phone.PHONE_TYPE_TD)*/{
			mActivePhone.codecVP(type, param);
		}
	}
	
	public void getCallForwardingOption(int commandInterfaceCFReason, int serviceClass, Message onComplete){
		mActivePhone.getCallForwardingOption(commandInterfaceCFReason, serviceClass, onComplete);
	}
	
	public void setCallForwardingOption(int commandInterfaceCFAction, int commandInterfaceCFReason, int serviceClass,
				String dialingNumber, int timerSeconds, Message onComplete){
		mActivePhone.setCallForwardingOption(commandInterfaceCFAction, commandInterfaceCFReason, serviceClass, dialingNumber,
			timerSeconds, onComplete);
	}

	public boolean getCallForwardingIndicator(int serviceClass) {
        return mActivePhone.getCallForwardingIndicator(serviceClass);
    }

    public int getPhoneId() {
        return mActivePhone.getPhoneId();
    }

    public void setIccCard(boolean turnOn) {
        mActivePhone.setIccCard(turnOn);
    }

}
