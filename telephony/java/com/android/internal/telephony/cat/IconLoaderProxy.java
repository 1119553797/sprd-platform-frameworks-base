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

package com.android.internal.telephony.cat;

import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.IccFileHandler;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.util.HashMap;

/**
 * Class for loading icons from the SIM card. Has two states: single, for loading
 * one icon. Multi, for loading icons list.
 *
 */
class IconLoaderProxy extends Handler {
	static IconLoaderProxy sIconLoaderProxy;
	static IconLoader[] mIconLoaderArray;

    static IconLoader getInstance(Handler caller, IccFileHandler fh, int phoneId) {
        if (sIconLoaderProxy == null) {
        	if (fh == null || phoneId >= PhoneFactory.getPhoneCount()) {
        		return null;
        	}
        	sIconLoaderProxy = new IconLoaderProxy();
        	mIconLoaderArray = new IconLoader[PhoneFactory.getPhoneCount()];
        	mIconLoaderArray[phoneId] = IconLoader.getInstance(caller, fh);       		
        } else {
            if (fh  == null || phoneId >= PhoneFactory.getPhoneCount()) {
                return null;
            }
        	if (mIconLoaderArray[phoneId] == null){
                mIconLoaderArray[phoneId] = IconLoader.getInstance(caller, fh);
        	}
        }
        return mIconLoaderArray[phoneId];
    }

    private IconLoaderProxy() {
        mIconLoaderArray = new IconLoader[PhoneFactory.getPhoneCount()];
    }

}
