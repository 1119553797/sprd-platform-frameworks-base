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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.provider.Telephony.Sms.Intents;
import android.util.Config;
import android.util.Log;

import android.text.TextUtils; // Add liuhongxing 20110603


/**
 * WAP push handler class.
 *
 * @hide
 */
public class WapPushOverSms {
    private static final String LOG_TAG = "WAP PUSH";

    private final Context mContext;
    private WspTypeDecoder pduDecoder;
    private SMSDispatcher mSmsDispatcher;

    /**
     * Hold the wake lock for 5 seconds, which should be enough time for
     * any receiver(s) to grab its own wake lock.
     */
    private final int WAKE_LOCK_TIMEOUT = 5000;

    public WapPushOverSms(Phone phone, SMSDispatcher smsDispatcher) {
        mSmsDispatcher = smsDispatcher;
        mContext = phone.getContext();
    }

    /**
     * Dispatches inbound messages that are in the WAP PDU format. See
     * wap-230-wsp-20010705-a section 8 for details on the WAP PDU format.
     *
     * @param pdu The WAP PDU, made up of one or more SMS PDUs
     * @return a result code from {@link Telephony.Sms.Intents}, or
     *         {@link Activity#RESULT_OK} if the message has been broadcast
     *         to applications
     */
    
    // Start liuhongxing 20110603
    public int dispatchWapPdu(byte[] pdu) 
    {
        return dispatchWapPdu(pdu, null, "");
    }
    
    public int dispatchWapPdu(byte[] pdu, byte[][] pdus, String number) {
    // End liu 20110603

        if (Config.LOGD) Log.d(LOG_TAG, "Rx: " + IccUtils.bytesToHexString(pdu));

        int index = 0;
        int transactionId = pdu[index++] & 0xFF;
        int pduType = pdu[index++] & 0xFF;
        int headerLength = 0;

        if ((pduType != WspTypeDecoder.PDU_TYPE_PUSH) &&
                (pduType != WspTypeDecoder.PDU_TYPE_CONFIRMED_PUSH)) {
            if (Config.LOGD) Log.w(LOG_TAG, "Received non-PUSH WAP PDU. Type = " + pduType);
            return Intents.RESULT_SMS_HANDLED;
        }
        Log.d(LOG_TAG, "Start new wap pdu");
        pduDecoder = new WspTypeDecoder(pdu);

        /**
         * Parse HeaderLen(unsigned integer).
         * From wap-230-wsp-20010705-a section 8.1.2
         * The maximum size of a uintvar is 32 bits.
         * So it will be encoded in no more than 5 octets.
         */
        if (pduDecoder.decodeUintvarInteger(index) == false) {
            if (Config.LOGD) Log.w(LOG_TAG, "Received PDU. Header Length error.");
            return Intents.RESULT_SMS_GENERIC_ERROR;
        }
        headerLength = (int)pduDecoder.getValue32();
        index += pduDecoder.getDecodedDataLength();

        int headerStartIndex = index;

        /**
         * Parse Content-Type.
         * From wap-230-wsp-20010705-a section 8.4.2.24
         *
         * Content-type-value = Constrained-media | Content-general-form
         * Content-general-form = Value-length Media-type
         * Media-type = (Well-known-media | Extension-Media) *(Parameter)
         * Value-length = Short-length | (Length-quote Length)
         * Short-length = <Any octet 0-30>   (octet <= WAP_PDU_SHORT_LENGTH_MAX)
         * Length-quote = <Octet 31>         (WAP_PDU_LENGTH_QUOTE)
         * Length = Uintvar-integer
         */
        Log.d(LOG_TAG, "Start parse");
        if (pduDecoder.decodeContentType(index) == false) {
            if (Config.LOGD) Log.w(LOG_TAG, "Received PDU. Header Content-Type error.");
            return Intents.RESULT_SMS_GENERIC_ERROR;
        }
        int binaryContentType;
        String mimeType = pduDecoder.getValueString();
        Log.d(LOG_TAG, "mimeType= "+mimeType);
        if (mimeType == null) {
            binaryContentType = (int)pduDecoder.getValue32();
            Log.d(LOG_TAG, "binaryContentType 1= "+binaryContentType);
            // TODO we should have more generic way to map binaryContentType code to mimeType.
            switch (binaryContentType) {
                case WspTypeDecoder.CONTENT_TYPE_B_DRM_RIGHTS_XML:
                    mimeType = WspTypeDecoder.CONTENT_MIME_TYPE_B_DRM_RIGHTS_XML;
                    break;
                case WspTypeDecoder.CONTENT_TYPE_B_DRM_RIGHTS_WBXML:
                    mimeType = WspTypeDecoder.CONTENT_MIME_TYPE_B_DRM_RIGHTS_WBXML;
                    break;
                case WspTypeDecoder.CONTENT_TYPE_B_PUSH_SI:
                    mimeType = WspTypeDecoder.CONTENT_MIME_TYPE_B_PUSH_SI;
                    break;
                case WspTypeDecoder.CONTENT_TYPE_B_PUSH_SL:
                    mimeType = WspTypeDecoder.CONTENT_MIME_TYPE_B_PUSH_SL;
                    break;
                case WspTypeDecoder.CONTENT_TYPE_B_PUSH_CO:
                    mimeType = WspTypeDecoder.CONTENT_MIME_TYPE_B_PUSH_CO;
                    break;
                case WspTypeDecoder.CONTENT_TYPE_B_MMS:
                    mimeType = WspTypeDecoder.CONTENT_MIME_TYPE_B_MMS;
                    break;
                case WspTypeDecoder.CONTENT_TYPE_B_VND_DOCOMO_PF:
                    mimeType = WspTypeDecoder.CONTENT_MIME_TYPE_B_VND_DOCOMO_PF;
                    break;
/*1026 for compile
                // Start liuhongxing 20110603  
                case WspTypeDecoder.CONTENT_TYPE_B_DM_WBXML:
                    mimeType = WspTypeDecoder.CONTENT_MIME_TYPE_B_DM_WBXML;
                    break;
                case WspTypeDecoder.CONTENT_TYPE_B_DM_XML:
                    mimeType = WspTypeDecoder.CONTENT_MIME_TYPE_B_DM_XML;
                    break;
                case WspTypeDecoder.CONTENT_TYPE_B_DM_NOTIFICATION:
                    mimeType = WspTypeDecoder.CONTENT_MIME_TYPE_B_DM_NOTIFICATION;
                    break;        
                // End liu 20110603
*/
                case WspTypeDecoder.CONTENT_TYPE_B_SUPL_INIT:
                    mimeType = WspTypeDecoder.CONTENT_MIME_TYPE_B_SUPL_INIT;
                    break;
                case WspTypeDecoder.CONTENT_TYPE_B_PUSH_SYNCML_NOTI:
                    mimeType = WspTypeDecoder.CONTENT_MIME_TYPE_B_PUSH_SYNCML_NOTI;
                    break;

                default:
                    if (Config.LOGD) {
                        Log.w(LOG_TAG,
                                "Received PDU. Unsupported Content-Type = " + binaryContentType);
                    }
                return Intents.RESULT_SMS_HANDLED;
            }
        } else {
            if (mimeType.equals(WspTypeDecoder.CONTENT_MIME_TYPE_B_DRM_RIGHTS_XML)) {
                binaryContentType = WspTypeDecoder.CONTENT_TYPE_B_DRM_RIGHTS_XML;
            } else if (mimeType.equals(WspTypeDecoder.CONTENT_MIME_TYPE_B_DRM_RIGHTS_WBXML)) {
                binaryContentType = WspTypeDecoder.CONTENT_TYPE_B_DRM_RIGHTS_WBXML;
            } else if (mimeType.equals(WspTypeDecoder.CONTENT_MIME_TYPE_B_PUSH_SI)) {
                binaryContentType = WspTypeDecoder.CONTENT_TYPE_B_PUSH_SI;
            } else if (mimeType.equals(WspTypeDecoder.CONTENT_MIME_TYPE_B_PUSH_SL)) {
                binaryContentType = WspTypeDecoder.CONTENT_TYPE_B_PUSH_SL;
            } else if (mimeType.equals(WspTypeDecoder.CONTENT_MIME_TYPE_B_PUSH_CO)) {
                binaryContentType = WspTypeDecoder.CONTENT_TYPE_B_PUSH_CO;
            } else if (mimeType.equals(WspTypeDecoder.CONTENT_MIME_TYPE_B_MMS)) {
                binaryContentType = WspTypeDecoder.CONTENT_TYPE_B_MMS;
            } else if (mimeType.equals(WspTypeDecoder.CONTENT_MIME_TYPE_B_VND_DOCOMO_PF)) {
                binaryContentType = WspTypeDecoder.CONTENT_TYPE_B_VND_DOCOMO_PF;

            // Start liuhongxing 20110603    
            } else if (mimeType.equals(WspTypeDecoder.CONTENT_MIME_TYPE_B_DM_WBXML)) {
                binaryContentType = WspTypeDecoder.CONTENT_TYPE_B_DM_WBXML;
            } else if (mimeType.equals(WspTypeDecoder.CONTENT_MIME_TYPE_B_DM_XML)) {
                binaryContentType = WspTypeDecoder.CONTENT_TYPE_B_DM_XML;
            } else if (mimeType.equals(WspTypeDecoder.CONTENT_MIME_TYPE_B_DM_NOTIFICATION)) {
                binaryContentType = WspTypeDecoder.CONTENT_TYPE_B_DM_NOTIFICATION;           
            // End liu 20110603

            } else if (mimeType.equals(WspTypeDecoder.CONTENT_MIME_TYPE_B_SUPL_INIT)) {
                binaryContentType = WspTypeDecoder.CONTENT_TYPE_B_SUPL_INIT;
            } else if (mimeType.equals(WspTypeDecoder.CONTENT_MIME_TYPE_B_PUSH_SYNCML_NOTI)) {
                binaryContentType = WspTypeDecoder.CONTENT_TYPE_B_PUSH_SYNCML_NOTI;

            } else {
                if (Config.LOGD) Log.w(LOG_TAG, "Received PDU. Unknown Content-Type = " + mimeType);
                return Intents.RESULT_SMS_HANDLED;
            }
        }
        index += pduDecoder.getDecodedDataLength();

        boolean dispatchedByApplication = false;
        Log.d(LOG_TAG, "binaryContentType 2= "+binaryContentType);
        switch (binaryContentType) {
            case WspTypeDecoder.CONTENT_TYPE_B_PUSH_CO:
                dispatchWapPdu_PushCO(pdus, pdu, transactionId, pduType, headerStartIndex, headerLength);
                dispatchedByApplication = true;
                break;
            case WspTypeDecoder.CONTENT_TYPE_B_MMS:
                dispatchWapPdu_MMS(pdu, transactionId, pduType, headerStartIndex, headerLength);
                dispatchedByApplication = true;
                break;
            default:
                break;
        }
        if (dispatchedByApplication == false) {
            Log.d(LOG_TAG, "dispatch default");
            dispatchWapPdu_default(pdus, pdu, transactionId, pduType, mimeType,
                                   headerStartIndex, headerLength, number /* Add liuhongxing 20110603 */);
        }
        return Activity.RESULT_OK;
    }

    private void dispatchWapPdu_default(byte[][] pdus, byte[] pdu, int transactionId, int pduType,
                                        String mimeType, int headerStartIndex, int headerLength, String number /* Add liuhongxing 20110603 */) {
        byte[] header = new byte[headerLength];
        System.arraycopy(pdu, headerStartIndex, header, 0, header.length);
        int dataIndex = headerStartIndex + headerLength;
        byte[] data;

        Log.d(LOG_TAG, "dispatchWapPdu_default transactionId="+transactionId+" pduType="+pduType+" mimeType="+mimeType
              +" headerStartIndex="+headerStartIndex+" headerLength="+headerLength);
        data = new byte[pdu.length - dataIndex];
        System.arraycopy(pdu, dataIndex, data, 0, data.length);

        Intent intent = new Intent(Intents.WAP_PUSH_RECEIVED_ACTION);
        intent.setType(mimeType);
        intent.putExtra("transactionId", transactionId);
        intent.putExtra("pduType", pduType);
        intent.putExtra("header", header);
        intent.putExtra("data", data);
        intent.putExtra("pdus", pdus);
        // Start liuhongxing 20110603
        if (!TextUtils.isEmpty(number))
        {
            intent.putExtra("from", number);
        }
        // End liu 20110603

        mSmsDispatcher.dispatch(intent, "android.permission.RECEIVE_WAP_PUSH");
    }

    private void dispatchWapPdu_PushCO(byte[][] pdus, byte[] pdu, int transactionId, int pduType,
                                       int headerStartIndex, int headerLength) {
        byte[] header = new byte[headerLength];
        System.arraycopy(pdu, headerStartIndex, header, 0, header.length);

        Intent intent = new Intent(Intents.WAP_PUSH_RECEIVED_ACTION);
        intent.setType(WspTypeDecoder.CONTENT_MIME_TYPE_B_PUSH_CO);
        intent.putExtra("transactionId", transactionId);
        intent.putExtra("pduType", pduType);
        intent.putExtra("header", header);
        intent.putExtra("data", pdu);
        intent.putExtra("pdus", pdus);

        mSmsDispatcher.dispatch(intent, "android.permission.RECEIVE_WAP_PUSH");
    }

    private void dispatchWapPdu_MMS(byte[] pdu, int transactionId, int pduType,
                                    int headerStartIndex, int headerLength) {
        byte[] header = new byte[headerLength];
        System.arraycopy(pdu, headerStartIndex, header, 0, header.length);
        int dataIndex = headerStartIndex + headerLength;
        byte[] data = new byte[pdu.length - dataIndex];
        System.arraycopy(pdu, dataIndex, data, 0, data.length);

        Intent intent = new Intent(Intents.WAP_PUSH_RECEIVED_ACTION);
        intent.setType(WspTypeDecoder.CONTENT_MIME_TYPE_B_MMS);
        intent.putExtra("transactionId", transactionId);
        intent.putExtra("pduType", pduType);
        intent.putExtra("header", header);
        intent.putExtra("data", data);

        mSmsDispatcher.dispatch(intent, "android.permission.RECEIVE_MMS");
    }
}

