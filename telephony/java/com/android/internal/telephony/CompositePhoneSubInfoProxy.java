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

package com.android.internal.telephony;

import java.io.FileDescriptor;
import java.io.PrintWriter;

import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.RemoteException;
import android.os.ServiceManager;


public class CompositePhoneSubInfoProxy extends IPhoneSubInfo.Stub {
    private PhoneSubInfoProxy[] mPhoneSubInfo;

    public CompositePhoneSubInfoProxy(PhoneSubInfoProxy[] phoneSubInfo) {
        mPhoneSubInfo = phoneSubInfo;
        if(ServiceManager.getService("iphonesubinfo") == null) {
            ServiceManager.addService("iphonesubinfo", this);
        }
    }

    private int getSimplePolicyPhoneId() {
        return PhoneFactory.getDefaultPhoneId();
    }

    public String getDeviceId() {
        return mPhoneSubInfo[getSimplePolicyPhoneId()].getDeviceId();
    }

    public String getDeviceSvn() {
        return mPhoneSubInfo[getSimplePolicyPhoneId()].getDeviceSvn();
    }

    /**
     * Retrieves the unique subscriber ID, e.g., IMSI for GSM phones.
     */
    public String getSubscriberId() {
        return mPhoneSubInfo[getSimplePolicyPhoneId()].getSubscriberId();
    }

    /**
     * Retrieves the serial number of the ICC, if applicable.
     */
    public String getIccSerialNumber() {
        return mPhoneSubInfo[getSimplePolicyPhoneId()].getIccSerialNumber();
    }

    /**
     * Retrieves the phone number string for line 1.
     */
    public String getLine1Number() {
        return mPhoneSubInfo[getSimplePolicyPhoneId()].getLine1Number();
    }

    /**
     * Retrieves the alpha identifier for line 1.
     */
    public String getLine1AlphaTag() {
        return mPhoneSubInfo[getSimplePolicyPhoneId()].getLine1AlphaTag();
    }

    /**
     * Retrieves the MSISDN Number.
     */
    public String getMsisdn() {
        return mPhoneSubInfo[getSimplePolicyPhoneId()].getMsisdn();
    }

    /**
     * Retrieves the voice mail number.
     */
    public String getVoiceMailNumber() {
        return mPhoneSubInfo[getSimplePolicyPhoneId()].getVoiceMailNumber();
    }

    /**
     * Retrieves the alpha identifier associated with the voice mail number.
     */
    public String getVoiceMailAlphaTag() {
        return mPhoneSubInfo[getSimplePolicyPhoneId()].getVoiceMailAlphaTag();
    }

    /**
     * Returns the IMS private user identity (IMPI) that was loaded from the ISIM.
     * @return the IMPI, or null if not present or not loaded
     */
    public String getIsimImpi() {
        return mPhoneSubInfo[getSimplePolicyPhoneId()].getIsimImpi();
    }

    /**
     * Returns the IMS home network domain name that was loaded from the ISIM.
     * @return the IMS domain name, or null if not present or not loaded
     */
    public String getIsimDomain() {
        return mPhoneSubInfo[getSimplePolicyPhoneId()].getIsimDomain();
    }

    /**
     * Returns the IMS public user identities (IMPU) that were loaded from the ISIM.
     * @return an array of IMPU strings, with one IMPU per string, or null if
     *      not present or not loaded
     */
    public String[] getIsimImpu() {
        return mPhoneSubInfo[getSimplePolicyPhoneId()].getIsimImpu();
    }

    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        mPhoneSubInfo[getSimplePolicyPhoneId()].dump(fd, pw, args);
    }

	@Override
	public String getCompleteVoiceMailNumber() throws RemoteException {
		return mPhoneSubInfo[getSimplePolicyPhoneId()].getCompleteVoiceMailNumber();
	}
}
