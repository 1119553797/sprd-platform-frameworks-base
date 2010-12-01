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

package com.android.internal.telephony.gsm;

import android.os.*;

import java.util.ArrayList;

import android.util.Log;

import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCardApplication;
import com.android.internal.telephony.IccConstants;
import com.android.internal.telephony.IccFileHandler;
import com.android.internal.telephony.Phone;

import com.android.internal.telephony.IccException;
import com.android.internal.telephony.IccFileTypeMismatch;
import com.android.internal.telephony.IccIoResult;
import com.android.internal.telephony.gsm.SIMFileHandler;
/**s
 * {@hide}
 */
public final class TDUSIMFileHandler extends SIMFileHandler implements IccConstants {

	//***** types of files  UICC 12.1.1.3 
    static protected final byte TYPE_FCP  = 0x62;
    static protected final byte RESPONSE_DATA_FCP_FLAG = 0;
    static protected final byte TYPE_FILE_DES  = (byte)0x82;
    static protected final byte RESPONSE_DATA_FILE_DES_FLAG = 2;
    static protected final byte RESPONSE_DATA_FILE_DES_LEN_FLAG = 3;
    static protected final byte TYPE_FILE_DES_LEN  = 5;
    static protected final byte RESPONSE_DATA_FILE_RECORD_LEN_1 = 6;
    static protected final byte RESPONSE_DATA_FILE_RECORD_LEN_2 = 7;
    static protected final byte RESPONSE_DATA_FILE_RECORD_COUNT_FLAG = 8;

    static final String LOG_TAG = "TD-SCDMA";
    private Phone mPhone;

    //***** Instance Variables

    //***** Constructor

/*
    static class LoadLinearFixedContext {

        int efid;
        int recordNum, recordSize, countRecords;
        boolean loadAll;

        Message onLoaded;

        ArrayList<byte[]> results;

        LoadLinearFixedContext(int efid, int recordNum, Message onLoaded) {
            this.efid = efid;
            this.recordNum = recordNum;
            this.onLoaded = onLoaded;
            this.loadAll = false;
        }

        LoadLinearFixedContext(int efid, Message onLoaded) {
            this.efid = efid;
            this.recordNum = 1;
            this.loadAll = true;
            this.onLoaded = onLoaded;
        }
    }
*/

    TDUSIMFileHandler(GSMPhone phone) {
        super(phone);
        mPhone = phone;
    }

    public void dispose() {
        super.dispose();
    }

    protected void finalize() {
        Log.d(LOG_TAG, "TDUSIMFileHandler finalized");
    }


    //***** Private Methods
    private void sendResult(Message response, Object result, Throwable ex) {
        if (response == null) {
            return;
        }

        AsyncResult.forMessage(response, result, ex);

        response.sendToTarget();
    }

    //***** Overridden from IccFileHandler

    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;
        IccIoResult result;
        Message response = null;
        String str;
        IccFileHandler.LoadLinearFixedContext lc;

        IccException iccException;
        byte data[];
        int size, fcp_size;
        int fileid;
        int recordNum;
        int recordSize[];

        try {
            switch (msg.what) {
            case EVENT_READ_IMG_DONE:
                ar = (AsyncResult) msg.obj;
                lc = (IccFileHandler.LoadLinearFixedContext) ar.userObj;
                result = (IccIoResult) ar.result;
                response = lc.onLoaded;

                iccException = result.getException();
                if (iccException != null) {
                    sendResult(response, result.payload, ar.exception);
                }
                break;
            case EVENT_READ_ICON_DONE:
                ar = (AsyncResult) msg.obj;
                response = (Message) ar.userObj;
                result = (IccIoResult) ar.result;

                iccException = result.getException();
                if (iccException != null) {
                    sendResult(response, result.payload, ar.exception);
                }
                break;
            case EVENT_GET_EF_LINEAR_RECORD_SIZE_DONE:
                ar = (AsyncResult)msg.obj;
                lc = (IccFileHandler.LoadLinearFixedContext) ar.userObj;
                result = (IccIoResult) ar.result;
                response = lc.onLoaded;

                if (ar.exception != null) {
                    sendResult(response, null, ar.exception);
                    break;
                }

                iccException = result.getException();
                if (iccException != null) {
                    sendResult(response, null, iccException);
                    break;
                }

                data = result.payload;

                if (TYPE_EF != data[RESPONSE_DATA_FILE_TYPE] ||
                    EF_TYPE_LINEAR_FIXED != data[RESPONSE_DATA_STRUCTURE]) {
                    throw new IccFileTypeMismatch();
                }

                recordSize = new int[3];
                recordSize[0] = data[RESPONSE_DATA_RECORD_LENGTH] & 0xFF;
                recordSize[1] = ((data[RESPONSE_DATA_FILE_SIZE_1] & 0xff) << 8)
                       + (data[RESPONSE_DATA_FILE_SIZE_2] & 0xff);
                recordSize[2] = recordSize[1] / recordSize[0];

                sendResult(response, recordSize, null);
                break;
             case EVENT_GET_RECORD_SIZE_DONE:
                ar = (AsyncResult)msg.obj;
                lc = (IccFileHandler.LoadLinearFixedContext) ar.userObj;
                result = (IccIoResult) ar.result;
                response = lc.onLoaded;
                if (ar.exception != null) {
                    loge("ar.exception");
                	sendResult(response, null, ar.exception);
                    break;
                }
                iccException = result.getException();

                if (iccException != null) {
                    loge("iccException");
                    sendResult(response, null, iccException);
                    break;
                }
                data = result.payload;
                fileid = lc.efid;
                recordNum = lc.recordNum;

                logbyte(data);
    	        Log.d(LOG_TAG, "FCP:" + Integer.toHexString(data[RESPONSE_DATA_FCP_FLAG]) +
    	        		"DES:" + Integer.toHexString(data[RESPONSE_DATA_FILE_DES_FLAG]) +
    	        		"DES_LEN:" + Integer.toHexString(data[RESPONSE_DATA_FILE_DES_LEN_FLAG]));
                // Use FCP flag to indicate GSM or TD simcard
                if (TYPE_FCP == data[RESPONSE_DATA_FCP_FLAG]) {
	                fcp_size = data[RESPONSE_DATA_FILE_SIZE_1] & 0xff;
	                if (TYPE_FILE_DES != data[RESPONSE_DATA_FILE_DES_FLAG]) {
	                    loge("TYPE_FILE_DES exception");
	                    throw new IccFileTypeMismatch();
	                }
	                if (TYPE_FILE_DES_LEN != data[RESPONSE_DATA_FILE_DES_LEN_FLAG]) {
	                    loge("TYPE_FILE_DES_LEN exception");
	                    throw new IccFileTypeMismatch();
	                }
	                lc.recordSize = ((data[RESPONSE_DATA_FILE_RECORD_LEN_1] & 0xff) << 8)
	                                 + (data[RESPONSE_DATA_FILE_RECORD_LEN_2] & 0xff);
	                lc.countRecords = data[RESPONSE_DATA_FILE_RECORD_COUNT_FLAG] & 0xFF;
                } else {
                    if (TYPE_EF != data[RESPONSE_DATA_FILE_TYPE]) {
                        loge("GSM: TYPE_EF exception");
                        throw new IccFileTypeMismatch();
                    }
                    if (EF_TYPE_LINEAR_FIXED != data[RESPONSE_DATA_STRUCTURE]) {
                        loge("GSM: EF_TYPE_LINEAR_FIXED exception");
                        throw new IccFileTypeMismatch();
                    }
                    lc.recordSize = data[RESPONSE_DATA_RECORD_LENGTH] & 0xFF;
                    size = ((data[RESPONSE_DATA_FILE_SIZE_1] & 0xff) << 8)
                           + (data[RESPONSE_DATA_FILE_SIZE_2] & 0xff);

                    lc.countRecords = size / lc.recordSize;
                }
                loge("recordsize:" + lc.recordSize + "counts:" + lc.countRecords);
                 if (lc.loadAll) {
                     lc.results = new ArrayList<byte[]>(lc.countRecords);
                 }
                 phone.mCM.iccIO(COMMAND_READ_RECORD, lc.efid, getEFPath(lc.efid),
                         lc.recordNum,
                         READ_RECORD_MODE_ABSOLUTE,
                         lc.recordSize, null, null,
                         obtainMessage(EVENT_READ_RECORD_DONE, lc));
                 break;
            case EVENT_GET_BINARY_SIZE_DONE:
                ar = (AsyncResult)msg.obj;
                response = (Message) ar.userObj;
                result = (IccIoResult) ar.result;

                if (ar.exception != null) {
                    sendResult(response, null, ar.exception);
                    break;
                }

                iccException = result.getException();

                if (iccException != null) {
                    sendResult(response, null, iccException);
                    break;
                }

                data = result.payload;

                fileid = msg.arg1;

                if (TYPE_EF != data[RESPONSE_DATA_FILE_TYPE]) {
                    throw new IccFileTypeMismatch();
                }

                if (EF_TYPE_TRANSPARENT != data[RESPONSE_DATA_STRUCTURE]) {
                    throw new IccFileTypeMismatch();
                }

                size = ((data[RESPONSE_DATA_FILE_SIZE_1] & 0xff) << 8)
                       + (data[RESPONSE_DATA_FILE_SIZE_2] & 0xff);

                phone.mCM.iccIO(COMMAND_READ_BINARY, fileid, getEFPath(fileid),
                                0, 0, size, null, null,
                                obtainMessage(EVENT_READ_BINARY_DONE,
                                              fileid, 0, response));
            break;

            case EVENT_READ_RECORD_DONE:

                ar = (AsyncResult)msg.obj;
                lc = (IccFileHandler.LoadLinearFixedContext) ar.userObj;
                result = (IccIoResult) ar.result;
                response = lc.onLoaded;

                if (ar.exception != null) {
                    sendResult(response, null, ar.exception);
                    break;
                }

                iccException = result.getException();

                if (iccException != null) {
                    sendResult(response, null, iccException);
                    break;
                }

                if (!lc.loadAll) {
                    sendResult(response, result.payload, null);
                } else {
                    lc.results.add(result.payload);

                    lc.recordNum++;

                    if (lc.recordNum > lc.countRecords) {
                        sendResult(response, lc.results, null);
                    } else {
                        phone.mCM.iccIO(COMMAND_READ_RECORD, lc.efid, getEFPath(lc.efid),
                                    lc.recordNum,
                                    READ_RECORD_MODE_ABSOLUTE,
                                    lc.recordSize, null, null,
                                    obtainMessage(EVENT_READ_RECORD_DONE, lc));
                    }
                }

            break;

            case EVENT_READ_BINARY_DONE:
                ar = (AsyncResult)msg.obj;
                response = (Message) ar.userObj;
                result = (IccIoResult) ar.result;

                if (ar.exception != null) {
                    sendResult(response, null, ar.exception);
                    break;
                }

                iccException = result.getException();

                if (iccException != null) {
                    sendResult(response, null, iccException);
                    break;
                }

                sendResult(response, result.payload, null);
            break;

        }} catch (Exception exc) {
            if (response != null) {
                sendResult(response, null, exc);
            } else {
                loge("uncaught exception" + exc);
            }
        }
    }

    protected String getEFPath(int efid) {
        switch(efid) {
        case EF_SMS:
            return MF_SIM + DF_TELECOM;

        case EF_EXT6:
        case EF_MWIS:
        case EF_MBI:
        case EF_SPN:
        case EF_AD:
        case EF_MBDN:
        case EF_PNN:
        case EF_SPDI:
        case EF_SST:
        case EF_CFIS:
            return MF_SIM + DF_GSM;

        case EF_MAILBOX_CPHS:
        case EF_VOICE_MAIL_INDICATOR_CPHS:
        case EF_CFF_CPHS:
        case EF_SPN_CPHS:
        case EF_SPN_SHORT_CPHS:
        case EF_INFO_CPHS:
            return MF_SIM + DF_GSM;

        case EF_PBR:
            // we only support global phonebook.
            return MF_SIM + DF_TELECOM + DF_PHONEBOOK;
        }
        String path = getCommonIccEFPath(efid);
        if (path == null) {
            // The EFids in USIM phone book entries are decided by the card manufacturer.
            // So if we don't match any of the cases above and if its a USIM return
            // the phone book path.
            IccCard card = phone.getIccCard();
            if (card != null && card.isApplicationOnIcc(IccCardApplication.AppType.APPTYPE_USIM)) {
                return MF_SIM + DF_TELECOM + DF_PHONEBOOK;
            }
            Log.e(LOG_TAG, "Error: EF Path being returned in null");
        }
        return path;
    }

    protected void logd(String msg) {
        Log.d(LOG_TAG, "[TDUSIMFileHandler] " + msg);
    }

    protected void loge(String msg) {
        Log.e(LOG_TAG, "[TDUSIMFileHandler] " + msg);
    }
    
    protected void logbyte(byte data[]) {
	    String test = new String();
	    for (int i = 0; i < data.length; i++)  
	    {  
	        test = Integer.toHexString(data[i] & 0xFF);  
	        if (test.length() == 1)  
	        {  
	            test = '0' + test;  
	        }  
	        Log.d(LOG_TAG, "payload:" + test);
	    }
    }
}
