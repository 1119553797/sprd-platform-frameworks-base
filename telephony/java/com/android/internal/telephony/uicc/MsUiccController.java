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

package com.android.internal.telephony.uicc;

import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.cdma.CDMALTEPhone;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.gsm.GSMPhone;

import android.util.Log;

/* This class is responsible for keeping all knowledge about
 * ICCs in the system. It is also used as API to get appropriate
 * applications to pass them to phone and service trackers.
 */
public class MsUiccController {
    private static UiccController[] mInstance = null;

    public static synchronized UiccController getInstance(PhoneBase phone) {
        int phoneId = phone.getPhoneId();
        if (mInstance == null) {
            mInstance = new UiccController[PhoneFactory.getPhoneCount()];
        }
        if (mInstance[phoneId] == null) {
            mInstance[phoneId] = new UiccController(phone);
        } else {
            mInstance[phoneId].setNewPhone(phone);
        }
        return mInstance[phoneId];
    }

}