/*
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package com.android.internal.telephony.gsm;

import android.os.Message;
import android.util.Log;

import com.android.internal.telephony.IccCardApplication;
import com.android.internal.telephony.IccConstants;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;

/**
 * SimPhoneBookInterfaceManager to provide an inter-process communication to
 * access ADN-like SIM records.
 */


public class SimPhoneBookInterfaceManager extends IccPhoneBookInterfaceManager {
    static final String LOG_TAG = "GSM";

    public SimPhoneBookInterfaceManager(GSMPhone phone) {
        super(phone);
        adnCache = phone.mSIMRecords.getAdnCache();
        //NOTE service "simphonebook" added by IccSmsInterfaceManagerProxy
        mLock = adnCache.getLock();
    }

    public void dispose() {
        super.dispose();
    }

    protected void finalize() {
        try {
            super.finalize();
        } catch (Throwable throwable) {
            Log.e(LOG_TAG, "Error while finalizing:", throwable);
        }
        if(DBG) Log.d(LOG_TAG, "SimPhoneBookInterfaceManager finalized");
    }
   
    private int[] getRecordsSize(int efid) {
        if (DBG) logd("getAdnRecordsSize: efid=" + efid);
        synchronized(mLock) {
            checkThread();
            recordSize = new int[3];

            //Using mBaseHandler, no difference in EVENT_GET_SIZE_DONE handling
            Message response = mBaseHandler.obtainMessage(EVENT_GET_SIZE_DONE);

            phone.getIccFileHandler().getEFLinearRecordSize(efid, response);
            try {
                mLock.wait();
            } catch (InterruptedException e) {
                logd("interrupted while trying to load from the SIM");
            }
        }

        return recordSize;
    }


	public int[] getAdnRecordsSize(int efid){

		if(phone.getIccCard().isApplicationOnIcc(IccCardApplication.AppType.APPTYPE_USIM)
				&&(efid == IccConstants.EF_ADN)){
			return getUsimAdnRecordsSize();
		}else{
			return getRecordsSize(efid);
		}

	}

	private int[] getUsimAdnRecordsSize(){
		if(adnCache == null){
			return null;	
		}
		UsimPhoneBookManager mUsimPhoneBookManager = adnCache.getUsimPhoneBookManager();
		if(mUsimPhoneBookManager == null){
			return null;	
		}
		int efid;
		int [] recordSizeAdn,recordSizeTotal = new int[3];
		for (int num = 0; num < mUsimPhoneBookManager.getNumRecs(); num++) {
			efid = mUsimPhoneBookManager.findEFInfo(num);

			if(efid == -1){
				return null;	
			}
			recordSizeAdn = getRecordsSize(efid);
			recordSizeTotal[0] = recordSizeAdn[0];
			recordSizeTotal[1] += recordSizeAdn[1];
			recordSizeTotal[2] += recordSizeAdn[2];
		}
		return recordSizeTotal;
	}
    

    public int[] getEmailRecordsSize(){
		if(adnCache == null){
			return null;	
		}
		UsimPhoneBookManager mUsimPhoneBookManager = adnCache.getUsimPhoneBookManager();
		if(mUsimPhoneBookManager == null){
			return null;	
		}
		int efid;
		int [] recordSizeEmail,recordSizeTotal = new int[3];
		for (int num = 0; num < mUsimPhoneBookManager.getNumRecs(); num++) {
			efid = mUsimPhoneBookManager.findEFEmailInfo(num);
			if(efid < -1){
				return null;
			}
			// zhanglj add end
			recordSizeEmail = getRecordsSize(efid);
			recordSizeTotal[0] = recordSizeEmail[0];
			recordSizeTotal[1] += recordSizeEmail[1];
			recordSizeTotal[2] += recordSizeEmail[2];
		}
		return recordSizeTotal;
    }

    public int getEmailNum()
    {
          int[] record = null ;  
          if(phone.getIccCard().isApplicationOnIcc(IccCardApplication.AppType.APPTYPE_USIM)){
           if(adnCache == null){
			return 0;	
	    }
	    UsimPhoneBookManager mUsimPhoneBookManager = adnCache.getUsimPhoneBookManager();
	    if(mUsimPhoneBookManager == null){
			return 0;	
	    }

	    return mUsimPhoneBookManager.getEmailNum();
	
	   }


	   return 0;
    }

    public int getAnrNum(){
	   if(phone.getIccCard().isApplicationOnIcc(IccCardApplication.AppType.APPTYPE_USIM)){
           if(adnCache == null){
			return 0;	
	    }
	    UsimPhoneBookManager mUsimPhoneBookManager = adnCache.getUsimPhoneBookManager();
	    if(mUsimPhoneBookManager == null){
			return 0;	
	    }

	    return mUsimPhoneBookManager.getAnrNum();

	   }


	   return 0;
          

    }
    public int[] getAnrRecordsSize(){
       if(adnCache == null){
			return null;	
		}
		UsimPhoneBookManager mUsimPhoneBookManager = adnCache.getUsimPhoneBookManager();
		if(mUsimPhoneBookManager == null){
			return null;	
		}
		int efid;
		int [] recordSizeAnr,recordSizeTotal = new int[3];
		for (int num = 0; num < mUsimPhoneBookManager.getNumRecs(); num++) {
			efid = mUsimPhoneBookManager.findEFAnrInfo(num);
			if(efid < -1){
				return null;
			}
			recordSizeAnr = getRecordsSize(efid);
			recordSizeTotal[0] = recordSizeAnr[0];
			recordSizeTotal[1] += recordSizeAnr[1];
			recordSizeTotal[2] += recordSizeAnr[2];
		}
		return recordSizeTotal;
   }

    protected void logd(String msg) {
        Log.d(LOG_TAG, "[SimPbInterfaceManager] " + msg);
    }

    protected void loge(String msg) {
        Log.e(LOG_TAG, "[SimPbInterfaceManager] " + msg);
    }
}

