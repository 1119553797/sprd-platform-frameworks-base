/*
 * Copyright (C) 2007 Esmertec AG.
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

package com.google.android.mms.pdu;

import com.android.internal.telephony.PhoneFactory;
import com.google.android.mms.InvalidHeaderValueException;

public class GenericPdu {
    /**
     * The headers of pdu.
     */
    PduHeaders mPduHeaders = null;
    protected int mPhoneId;

    /**
     * Constructor.
     */
    public GenericPdu() {
        mPduHeaders = new PduHeaders();
        mPhoneId = PhoneFactory.DEFAULT_PHONE_ID;
    }

    /**
     * Constructor.
     *
     * @param headers Headers for this PDU.
     */
    GenericPdu(PduHeaders headers) {
        mPduHeaders = headers;
        mPhoneId = PhoneFactory.DEFAULT_PHONE_ID;
    }

    public GenericPdu(int phoneId) {
        mPduHeaders = new PduHeaders();
        mPhoneId = phoneId;
    }

    GenericPdu(PduHeaders headers, int phoneId) {
        mPduHeaders = headers;
        mPhoneId = phoneId;
    }

    /**
     * Get the headers of this PDU.
     *
     * @return A PduHeaders of this PDU.
     */
    /* Modify 20121218 Spreadst of AppBackup  start */
    /*
    PduHeaders getPduHeaders() {
    */
    public PduHeaders getPduHeaders() {
    /* Modify 20121218 Spreadst of AppBackup end */
        return mPduHeaders;
    }

    /**
     * Get X-Mms-Message-Type field value.
     *
     * @return the X-Mms-Report-Allowed value
     */
    public int getMessageType() {
        return mPduHeaders.getOctet(PduHeaders.MESSAGE_TYPE);
    }

    /**
     * Set X-Mms-Message-Type field value.
     *
     * @param value the value
     * @throws InvalidHeaderValueException if the value is invalid.
     *         RuntimeException if field's value is not Octet.
     */
    public void setMessageType(int value) throws InvalidHeaderValueException {
        mPduHeaders.setOctet(value, PduHeaders.MESSAGE_TYPE);
    }

    /**
     * Get X-Mms-MMS-Version field value.
     *
     * @return the X-Mms-MMS-Version value
     */
    public int getMmsVersion() {
        return mPduHeaders.getOctet(PduHeaders.MMS_VERSION);
    }

    /**
     * Set X-Mms-MMS-Version field value.
     *
     * @param value the value
     * @throws InvalidHeaderValueException if the value is invalid.
     *         RuntimeException if field's value is not Octet.
     */
    public void setMmsVersion(int value) throws InvalidHeaderValueException {
        mPduHeaders.setOctet(value, PduHeaders.MMS_VERSION);
    }

    /**
     * Get From value.
     * From-value = Value-length
     *      (Address-present-token Encoded-string-value | Insert-address-token)
     *
     * @return the value
     */
    public EncodedStringValue getFrom() {
       return mPduHeaders.getEncodedStringValue(PduHeaders.FROM);
    }

    /**
     * Set From value.
     *
     * @param value the value
     * @throws NullPointerException if the value is null.
     */
    public void setFrom(EncodedStringValue value) {
        mPduHeaders.setEncodedStringValue(value, PduHeaders.FROM);
    }

    public int getPhoneId(){
        return mPhoneId;
    }
}
