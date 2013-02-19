/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccFileHandler;
import com.android.internal.telephony.IccRecords;

import android.util.Config;

import java.io.ByteArrayOutputStream;


public class StkServiceProxy extends Handler {
    static StkServiceProxy sStkServiceProxy;
    static CatService[] mStkServiceArray;
    private static String LOG_TAG = "StkServiceProxy";

    public static CatService getInstance(CommandsInterface ci, IccRecords ir,
            Context context, IccFileHandler fh, IccCard ic) {
        if (ci == null) {
            return null;
        }
        int phoneId = ci.getPhoneId();
        if (sStkServiceProxy == null) {
            sStkServiceProxy = new StkServiceProxy();
        }
        if (mStkServiceArray[phoneId] == null) {
            if (ci == null || ir == null || context == null || fh == null
                    || ic == null) {
                return null;
            }
            mStkServiceArray[phoneId] = CatService.getInstance(ci, ir, context, fh, ic);
        } else  {
            mStkServiceArray[phoneId].update(ci);
        }
        return mStkServiceArray[phoneId];
    }
    
    public static CatService getInstance(int phoneId) {
        if (sStkServiceProxy == null) {
            sStkServiceProxy = new StkServiceProxy();
        }
        if (PhoneFactory.getPhoneCount() == 1) {
            phoneId = 0;
        }
        if (mStkServiceArray[phoneId] == null) {
       	    return null;
        }
        return mStkServiceArray[phoneId];
    }

    private StkServiceProxy(){
        mStkServiceArray = new CatService[PhoneFactory.getPhoneCount()];
    }
}
