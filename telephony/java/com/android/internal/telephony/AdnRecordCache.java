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

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.telephony.gsm.UsimPhoneBookManager;

import java.util.ArrayList;
import java.util.Iterator;

import com.android.internal.telephony.IccConstants;
import android.text.TextUtils;


/**
 * {@hide}
 */
public final class AdnRecordCache extends Handler implements IccConstants {
    //***** Instance Variables

	private IccFileHandler mFh;
	public UsimPhoneBookManager mUsimPhoneBookManager;
    PhoneBase phone;
   
    // Indexed by EF ID
    SparseArray<ArrayList<AdnRecord>> adnLikeFiles
        = new SparseArray<ArrayList<AdnRecord>>();

    // People waiting for ADN-like files to be loaded
    SparseArray<ArrayList<Message>> adnLikeWaiters
        = new SparseArray<ArrayList<Message>>();

    // People waiting for adn record to be updated
    SparseArray<Message> userWriteResponse = new SparseArray<Message>();

    //***** Event Constants

    static final int EVENT_LOAD_ALL_ADN_LIKE_DONE = 1;
    static final int EVENT_UPDATE_ADN_DONE = 2;
    private int[] mEfid = null;	
    
    //add multi record and email in usim begin
    static final int EVENT_UPDATE_USIM_ADN_DONE = 3;
	
    public int mInsetIndex = -1;
	protected final Object mLock = new Object();
	public boolean updateUsimOthers = true;

  /*  public AdnRecordCache(IccFileHandler fh) {
	     mFh = fh;
	     mUsimPhoneBookManager = new UsimPhoneBookManager(mFh, this);
	}
	*/
	public int getAdnLikeSize(){
		return adnLikeFiles.size();
	}
	
	public UsimPhoneBookManager getUsimPhoneBookManager(){
		return mUsimPhoneBookManager;
	}
	// add multi record and email in usim end

	//***** Constructor



    public AdnRecordCache(PhoneBase phone) {
        this.phone = phone;
	  mFh = phone.getIccFileHandler();
        mUsimPhoneBookManager = new UsimPhoneBookManager(phone,mFh, this);
    }

    //***** Called from SIMRecords

    /**
     * Called from SIMRecords.onRadioNotAvailable and SIMRecords.handleSimRefresh.
     */
    public void reset() {
        adnLikeFiles.clear();
        mUsimPhoneBookManager.reset();

        clearWaiters();
        clearUserWriters();

    }

    private void clearWaiters() {
        int size = adnLikeWaiters.size();
        for (int i = 0; i < size; i++) {
            ArrayList<Message> waiters = adnLikeWaiters.valueAt(i);
            AsyncResult ar = new AsyncResult(null, null, new RuntimeException("AdnCache reset"));
            notifyWaiters(waiters, ar);
        }
        adnLikeWaiters.clear();
    }

    private void clearUserWriters() {
        int size = userWriteResponse.size();
        for (int i = 0; i < size; i++) {
            sendErrorResponse(userWriteResponse.valueAt(i), "AdnCace reset");
        }
        userWriteResponse.clear();
    }

    /**
     * @return List of AdnRecords for efid if we've already loaded them this
     * radio session, or null if we haven't
     */
    public ArrayList<AdnRecord>
    getRecordsIfLoaded(int efid) {
        return adnLikeFiles.get(efid);
    }
    public int[] getRecordsEfId(int efid){

    
          int efIds[] = null;
          Log.i("AdnRecordCache","getRecordsEfId"+efid);
	    if(EF_PBR == efid){

		   efIds = mUsimPhoneBookManager.getEfFilesFromUsim();

	    }else{



                efIds = new int[1];
		   efIds[0] = efid;


	   }
         mEfid = efIds;
         return efIds;





   }


    /**
     * Returns extension ef associated with ADN-like EF or -1 if
     * we don't know.
     *
     * See 3GPP TS 51.011 for this mapping
     */
    int extensionEfForEf(int efid) {
        switch (efid) {
            case EF_MBDN: return EF_EXT6;
            case EF_ADN: return EF_EXT1;
            case EF_SDN: return EF_EXT3;
            case EF_FDN: return EF_EXT2;
            case EF_MSISDN: return EF_EXT1;
            case EF_PBR: return 0; // The EF PBR doesn't have an extension record
            default: return 0;
        }
    }

    private void sendErrorResponse(Message response, String errString) {
        if (response != null) {
            Exception e = new RuntimeException(errString);
            AsyncResult.forMessage(response).exception = e;
            response.sendToTarget();
        }
    }

    /**
     * Update an ADN-like record in EF by record index
     *
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param adn is the new adn to be stored
     * @param recordIndex is the 1-based adn record index
     * @param pin2 is required to update EF_FDN, otherwise must be null
     * @param response message to be posted when done
     *        response.exception hold the exception in error
     */
    public void updateAdnByIndex(int efid, AdnRecord adn, int recordIndex, String pin2,
            Message response) {

        int extensionEF = extensionEfForEf(efid);
        if (extensionEF < 0) {
            sendErrorResponse(response, "EF is not known ADN-like EF:" + efid);
            return;
        }

        Message pendingResponse = userWriteResponse.get(efid);
        if (pendingResponse != null) {
            sendErrorResponse(response, "Have pending update for EF:" + efid);
            return;
        }

        userWriteResponse.put(efid, response);

        /*new AdnRecordLoader(phone).updateEF(adn, efid, extensionEF,
                recordIndex, pin2,
                obtainMessage(EVENT_UPDATE_ADN_DONE, efid, recordIndex, adn));*/
        //add multi record and email in usim
        new AdnRecordLoader(mFh).updateEF(adn, efid, extensionEF,
                recordIndex, pin2,
                obtainMessage(EVENT_UPDATE_ADN_DONE, efid, recordIndex, adn));
    }
    //add multi record and email begin 
    public void updateUSIMAdnByIndex(int efid, AdnRecord newAdn, int recordIndex, String pin2,
            Message response) {
            
	int extensionEF = 0;

        int emailEF = 0;
        int iapEF = 0;
	int sneEF= 0;
	
        int emailNum = -1;
        int recNum = 0;
        boolean isUpdateIap = false;
        boolean isUpdateEmail = true;
        int numRecs = 0;

        for (int num = 0; num < mUsimPhoneBookManager.getNumRecs(); num++) { 

            efid = mUsimPhoneBookManager.findEFInfo(num);
            extensionEF = mUsimPhoneBookManager.findExtensionEFInfo(num);

            iapEF =mUsimPhoneBookManager.findEFIapInfo(num);

	     if(mUsimPhoneBookManager.ishaveSne)
	     {
	     	sneEF =mUsimPhoneBookManager.findEFSneInfo(num);
	     }

	    if(efid < 0 || extensionEF < 0){
                sendErrorResponse(response, "EF is not known ADN-like EF:" + "efid"+efid +",extensionEF="+extensionEF);
                return;
            }
		
            Log.e("GSM", "efid is " + efid);
            numRecs = num;

	    if(recordIndex <= mUsimPhoneBookManager.mAdnRecordSizeArray[num])
            {
            	Log.e("GSM"," recordIndex is small,so break;");
                break;
            }			
	}

        if(numRecs > 0) {
	    recordIndex -= mUsimPhoneBookManager.mAdnRecordSizeArray[numRecs - 1];
        }
        Log.e("GSM", "recordIndex is " + recordIndex);
        
			byte[] record = null;
			int emailNumInIap =0;
			int iapRecNum = 0;
			boolean newEmail = false;
			try {
				ArrayList<byte[]> mIapFileRecord = mUsimPhoneBookManager.getIapFileRecord(numRecs);
				// record = mUsimPhoneBookManager.getIapFileRecord(recNum).get(index-1);
				if(numRecs == 0){
					iapRecNum = recordIndex;	
				}else {
					iapRecNum = mUsimPhoneBookManager.getIapRecordSizeArray()[numRecs] - 
						mUsimPhoneBookManager.getIapRecordSizeArray()[numRecs - 1] + recordIndex;
				}

				if (mIapFileRecord != null){
					record = mIapFileRecord.get(iapRecNum - 1);
					
				}else{
					return;
				}

        	} catch (IndexOutOfBoundsException e) {
            	Log.e("GSM", "Error: Improper ICC card: No IAP record for ADN, continuing");
        	}
				if(record !=null){
					emailNumInIap = mUsimPhoneBookManager.getEmailTagNumberInIap();
        			emailNum = (int) (record[emailNumInIap] & 0XFF);
					emailNum = emailNum==0xFF? (-1):emailNum;
				}else{
					emailNum = -1;	
				}
		//Log.e("GSM","emailNumInIap ="+emailNumInIap);
		//Log.e("GSM","emailNum =="+emailNum);
        if(emailNum == -1){
			if(newAdn.emails == null){
				isUpdateEmail = false;
			}else{	
            	emailNum = mUsimPhoneBookManager.getNewEmailNumber();
				if(emailNum == -1){
					Log.e("GSM","Email space is full!");
					isUpdateIap = false;
					isUpdateEmail = false;
					newAdn.setEmails(null);
				}else{
					isUpdateIap = true ;
					newEmail = true;
				}
			}
        }else if(newAdn.emails == null){
			isUpdateIap = true;			
		}
		//Log.e("GSM","isUpdateIap =="+isUpdateIap);
        Message pendingResponse = userWriteResponse.get(efid);

        if (pendingResponse != null) {
            sendErrorResponse(response, "Have pending update for EF:" + efid);
            return;
        }

        userWriteResponse.put(efid, response);

        AdnRecordLoader adnRecordLoader = new AdnRecordLoader(mFh);
		synchronized(mLock){
        adnRecordLoader.updateEFAdnToUsim(newAdn, efid, extensionEF,
                recordIndex, pin2,
                obtainMessage(EVENT_UPDATE_USIM_ADN_DONE, efid, recordIndex, newAdn));

		updateUsimOthers = false;
		try{
		mLock.wait();
		}catch (InterruptedException e){
			Log.e("GSM"," InterruptedException at Adn update");
		}

		if (!updateUsimOthers){
			if (newEmail){
			mUsimPhoneBookManager.removeEmailNumFromSet(emailNum);
			}
			return;
		}


		int tmpEmailInIap = -1;
	   	if(isUpdateIap){

			if(newAdn.emails ==null){
				mUsimPhoneBookManager.removeEmailNumFromSet(emailNum);
				mUsimPhoneBookManager.setIapFileRecord(numRecs, iapRecNum-1, (byte)0xFF);
				tmpEmailInIap = -1;
			}else{
				mUsimPhoneBookManager.setIapFileRecord(numRecs,iapRecNum-1,(byte)(emailNum & 0xFF));
				tmpEmailInIap = emailNum;
			}
			if(iapEF > 0){
			Log.e("GSM","begin to update IAP ---IAP id " + recordIndex);
			adnRecordLoader = new AdnRecordLoader(mFh);
			adnRecordLoader.updateEFIapToUsim(newAdn, iapEF, recordIndex, tmpEmailInIap, pin2,
					//obtainMessage(EVENT_UPDATE_USIM_ADN_DONE, efid, recordIndex, newAdn)); 
					null);
			}
		}

		int emailRecordCount = 0;
		int [] mEmailRecordSize = mUsimPhoneBookManager.getEmailRecordSizeArray();
		for(int num = 0; num < mUsimPhoneBookManager.getNumRecs(); num++){
			if(emailNum > mEmailRecordSize[num]){
				//Log.e("GSM","emailNum =="+emailNum+"mEmailRecordSize["+num+"] =="+mEmailRecordSize[num]);
				emailRecordCount++;	
			}
		}
		if(emailRecordCount != 0){
			emailNum -= mEmailRecordSize[emailRecordCount -1];
			emailEF = mUsimPhoneBookManager.findEFEmailInfo(emailRecordCount);
		}else{
			emailEF = mUsimPhoneBookManager.findEFEmailInfo(0);			
		}

		if(isUpdateEmail && emailEF > 0){
			Log.e("GSM","begin to update Email ---emailNum= " + emailNum);
			adnRecordLoader = new AdnRecordLoader(mFh);
			adnRecordLoader.updateEFEmailToUsim(newAdn, emailEF, emailNum, efid, recordIndex,emailNumInIap, pin2,
					//obtainMessage(EVENT_UPDATE_USIM_ADN_DONE, efid, recordIndex, newAdn));
					null);
		}
		
		if(mUsimPhoneBookManager.ishaveAnr)
		{
			ArrayList<Integer> anrefids =  mUsimPhoneBookManager.mPbrFile.getFileIdsByTagAdn(UsimPhoneBookManager.USIM_EFANR_TAG, efid);
			Log.e("GSM","begin to update Anr ---anrefids is " + anrefids);
			adnRecordLoader = new AdnRecordLoader(mFh);
			adnRecordLoader.updateEFAnrToUsim(newAdn, anrefids, efid, recordIndex, pin2,
					//obtainMessage(EVENT_UPDATE_USIM_ADN_DONE, efid, recordIndex, newAdn));
					null);
		}
		
		if(mUsimPhoneBookManager.ishaveSne)
		{	
			Log.e("GSM","begin to update Sne ---sneEF is " + sneEF);
			adnRecordLoader = new AdnRecordLoader(mFh);
			adnRecordLoader.updateEFSneToUsim(newAdn, sneEF, efid, recordIndex, pin2,
					//obtainMessage(EVENT_UPDATE_USIM_ADN_DONE, efid, recordIndex, newAdn));
					null);
		}		
		}
    }



   
    public void updateUSIMAdnBySearch(int efid, AdnRecord oldAdn, AdnRecord newAdn,
            String pin2, Message response) {
        
        int extensionEF = 0;
        int index = -1;

        int emailEF = 0;
        int iapEF = 0;

	int sneEF= 0;
	int aasEF = 0;
	int gasEF = 0;
	int grpEF = 0;

        int emailNum = -1;
        int recNum = 0;
        boolean isUpdateIap = false;
        boolean isUpdateEmail = true;
		boolean newEmail = false;
        Log.i("AdnRecordCache","updateUSIMAdnBySearch efid" +efid);
        for (int num = 0; num < mUsimPhoneBookManager.getNumRecs(); num++) {

            efid = mUsimPhoneBookManager.findEFInfo(num);
            extensionEF = mUsimPhoneBookManager.findExtensionEFInfo(num);

           	 iapEF =mUsimPhoneBookManager.findEFIapInfo(num);

		 if(mUsimPhoneBookManager.ishaveSne)
		 {
		 	sneEF =mUsimPhoneBookManager.findEFSneInfo(num);
		  }
		 if(mUsimPhoneBookManager.ishaveAas)
		 {
		 	aasEF =mUsimPhoneBookManager.findEFAasInfo(num);
		  }
		 if(mUsimPhoneBookManager.ishaveGrp)
		 {
		 	grpEF =mUsimPhoneBookManager.findEFGrpInfo(num);
		  }
		 if(mUsimPhoneBookManager.ishaveGas)
		 {
		 	gasEF =mUsimPhoneBookManager.findEFGasInfo(num);
		  }
		 Log.e("yuyong", "efid : " + efid +"extensionEF :"+extensionEF + " iapEF:" + iapEF + " sneEF: "+ sneEF + " aasEF: " + aasEF +" grpEF:  "+grpEF + " gasEF: "+ gasEF);

		if(efid < 0  || extensionEF < 0 ){
                sendErrorResponse(response, "EF is not known ADN-like EF:" + "efid"+efid +",extensionEF="+extensionEF);
                return;
            }
            Log.i("AdnRecordCache","updateUSIMAdnBySearch (1)" );
            ArrayList<AdnRecord> oldAdnList;

            Log.e("GSM", "efid is " + efid);

            oldAdnList = getRecordsIfLoaded(efid);
            if (oldAdnList == null) {
                sendErrorResponse(response, "Adn list not exist for EF:" + efid);
                return;
            }
            Log.i("AdnRecordCache","updateUSIMAdnBySearch (2)" );
            if(mUsimPhoneBookManager.anrFileCount == 0x3 && TextUtils.isEmpty(oldAdn.anr))
            {
            	oldAdn.anr = ";;"; 
            }
	        else if(mUsimPhoneBookManager.anrFileCount == 0x2 && TextUtils.isEmpty(oldAdn.anr))
	        {
	    	    oldAdn.anr = ";";
	        }       

            int count = 1;
            boolean find_index = false;
            for (Iterator<AdnRecord> it = oldAdnList.iterator(); it.hasNext();) {
                if (oldAdn.isEqual(it.next())) {
                    Log.e("GSM", "we got the index " + count);
                    find_index = true;
                    index = count;

		    mInsetIndex = index;

                    break;
                }
                count++;
            }
            
            if (find_index) {
            	recNum = num;

		if(num > 0) {
                	    mInsetIndex += mUsimPhoneBookManager.mAdnRecordSizeArray[num - 1];
		}
             Log.i("AdnRecordCache","updateUSIMAdnBySearch (3)" );
		Log.e("yuyong ","we got the mInsetIndex" + mInsetIndex);
            	Log.e("GSM", "find the index!");
               	Log.e("GSM", "find the mInsetIndex:" +mInsetIndex);

				break;
            }
        }
        
        if (index == -1) {
            sendErrorResponse(response, "Adn record don't exist for " + oldAdn);
            return;
        }

            Log.i("AdnRecordCache","updateUSIMAdnBySearch (4)" );
			byte[] record = null;
			int emailNumInIap =0;
			int iapRecNum = 0;
			try {
				ArrayList<byte[]> mIapFileRecord = mUsimPhoneBookManager.getIapFileRecord(recNum);
				if(recNum == 0){
					iapRecNum = index;	
				}else {
					iapRecNum = mUsimPhoneBookManager.getIapRecordSizeArray()[recNum] - 
						mUsimPhoneBookManager.getIapRecordSizeArray()[recNum - 1] + index;
				}

				if (mIapFileRecord != null){
					record = mIapFileRecord.get(iapRecNum - 1);
					
				}else{
					return;
				}

        	} catch (IndexOutOfBoundsException e) {
            	Log.e("GSM", "Error: Improper ICC card: No IAP record for ADN, continuing");
        	}
				if(record !=null){
					emailNumInIap = mUsimPhoneBookManager.getEmailTagNumberInIap();
        			emailNum = (int)(record[emailNumInIap] & 0xFF);
					emailNum = emailNum==0xFF? (-1):emailNum;
				}else{
					emailNum = -1;	
				}
		//Log.e("GSM","emailNumInIap ="+emailNumInIap);
		//Log.e("GSM","emailNum =="+emailNum);
        if(emailNum == -1){
			if(newAdn.emails == null){
				isUpdateEmail = false;
			}else{	
            	emailNum = mUsimPhoneBookManager.getNewEmailNumber();
				if(emailNum == -1){
					Log.e("GSM","Email space is full!");
					isUpdateIap = false;
					isUpdateEmail = false;
					newAdn.setEmails(null);
				}else{
					isUpdateIap = true ;
					newEmail = true;
				}
			}
        }else if(newAdn.emails == null){
			isUpdateIap = true;			
		}

        Message pendingResponse = userWriteResponse.get(efid);

        if (pendingResponse != null) {
            sendErrorResponse(response, "Have pending update for EF:" + efid);
            return;
        }

        userWriteResponse.put(efid, response);

		synchronized(mLock){

        AdnRecordLoader adnRecordLoader = new AdnRecordLoader(mFh);


        adnRecordLoader.updateEFAdnToUsim(newAdn, efid, extensionEF,
                index, pin2,
                obtainMessage(EVENT_UPDATE_USIM_ADN_DONE, efid, index, newAdn));
		updateUsimOthers = false;
		try{
		mLock.wait();
		}catch (InterruptedException e){
			Log.e("GSM","xiaojf1 InterruptedException at Adn update");
		}

		if (!updateUsimOthers){
			if (newEmail){
			mUsimPhoneBookManager.removeEmailNumFromSet(emailNum);
			}
			return;
		}

	   	if(isUpdateIap){

			if(newAdn.emails ==null){
				mUsimPhoneBookManager.removeEmailNumFromSet(emailNum);
				mUsimPhoneBookManager.setIapFileRecord(recNum, iapRecNum-1, (byte)0xFF);
				emailNum = -1;
			}else{
				mUsimPhoneBookManager.setIapFileRecord(recNum,iapRecNum-1,(byte)(emailNum & 0xFF));	
			}
			if(iapEF > 0){
			Log.e("GSM","begin to update IAP ---IAP id " + index);
			adnRecordLoader = new AdnRecordLoader(mFh);
			adnRecordLoader.updateEFIapToUsim(newAdn, iapEF, index, emailNum, pin2,
					//obtainMessage(EVENT_UPDATE_USIM_ADN_DONE, efid, index, newAdn));
					null);
			}
		}

		int emailRecordCount = 0;
		int [] mEmailRecordSize = mUsimPhoneBookManager.getEmailRecordSizeArray();
		for(int num = 0; num < mUsimPhoneBookManager.getNumRecs(); num++){
			if(emailNum > mEmailRecordSize[num]){
				emailRecordCount++;	
			}
		}
		if(emailRecordCount != 0){
			emailNum -= mEmailRecordSize[emailRecordCount -1];
			emailEF = mUsimPhoneBookManager.findEFEmailInfo(emailRecordCount);
		}else{
			emailEF = mUsimPhoneBookManager.findEFEmailInfo(0);			
		}
		if(isUpdateEmail && emailEF > 0){
			Log.e("GSM","begin to update Email ---Email id " + emailNum);
			adnRecordLoader = new AdnRecordLoader(mFh);
			adnRecordLoader.updateEFEmailToUsim(newAdn, emailEF, emailNum, efid, index,emailNumInIap, pin2,
					//obtainMessage(EVENT_UPDATE_USIM_ADN_DONE, efid, index, newAdn));
					null);
		}
	
		if(mUsimPhoneBookManager.ishaveAnr)
		{
			ArrayList<Integer> anrefids =  mUsimPhoneBookManager.mPbrFile.
				getFileIdsByTagAdn(UsimPhoneBookManager.USIM_EFANR_TAG, efid);
			Log.e("GSM","begin to update Anr ---anrefids is " + anrefids);
			adnRecordLoader = new AdnRecordLoader(mFh);
			adnRecordLoader.updateEFAnrToUsim(newAdn, anrefids, efid, index, pin2,
					//obtainMessage(EVENT_UPDATE_USIM_ADN_DONE, efid, index, newAdn));
					null);	
		}
		if(mUsimPhoneBookManager.ishaveSne)
		{	
			Log.e("GSM","begin to update Sne ---sneEF is " + sneEF);
			adnRecordLoader = new AdnRecordLoader(mFh);
			adnRecordLoader.updateEFSneToUsim(newAdn, sneEF, efid, index, pin2,
					//obtainMessage(EVENT_UPDATE_USIM_ADN_DONE, efid, index, newAdn));
					null);
		}	
		/*if(mUsimPhoneBookManager.ishaveAas)
		{
			Log.e("GSM","begin to update Aas ---aasEF is " + aasEF);
			adnRecordLoader.updateEFAasToUsim(newAdn, aasEF, efid, index, pin2,
					obtainMessage(EVENT_UPDATE_USIM_ADN_DONE, efid, index, newAdn));
		}	*/
		/*
		if(mUsimPhoneBookManager.ishaveGrp)
		{	
			Log.e("yuyong","begin to update Grp ---grpEF is " + grpEF +  " index : " + index + " grp: " + newAdn.getGrp());
			adnRecordLoader.updateEFGrpToUsim(newAdn, grpEF, efid, index, pin2,
					obtainMessage(EVENT_UPDATE_USIM_ADN_DONE, efid, index, newAdn));
		}	*/
		/*
		if(mUsimPhoneBookManager.ishaveGas)
		{	
			Log.e("yuyong","begin to update Gas ---gasEF is " + gasEF + " index : " + index + " grp: " + newAdn.getGas());
			adnRecordLoader.updateEFGasToUsim(newAdn, gasEF, efid, index, pin2,
					obtainMessage(EVENT_UPDATE_USIM_ADN_DONE, efid, index, newAdn));
		}	*/
		
		}
    }
    
    
    //add multi record and email in usim end
     private boolean isLastAdn(int efid){
        int i=0;
	  Log.i("AdnRecordCache","addAdnRecordsInEf: efid=" + efid +"mEfid.length"+ mEfid.length);
           
	  for( i=0; i<mEfid.length;i++){
	  	
            Log.i("AdnRecordCache","addAdnRecordsInEf: mEfid=" + mEfid[i]);

	      if(efid ==  mEfid[i]){



                 break;
	     }


		  
	  }

	  if(i == mEfid.length-1 ){
             Log.i("AdnRecordCache","addAdnRecordsInEf: true");
             return true;
	  }
           Log.i("AdnRecordCache","addAdnRecordsInEf: false");
         return false;
     }

     public int getAdnIndex(int efid, AdnRecord oldAdn){


        ArrayList<AdnRecord>  oldAdnList;
        oldAdnList = getRecordsIfLoaded(efid);
        Log.i("AdnRecordCache","getAdnIndex efid "+efid );
        if (oldAdnList == null) {
     
            return -1;
        }
        Log.i("AdnRecordCache","updateAdnBySearch (2)");
        int index = -1;
        int count = 1;
        for (Iterator<AdnRecord> it = oldAdnList.iterator(); it.hasNext(); ) {
            if (oldAdn.isEqual(it.next())) {
                index = count;
                break;
            }
            count++;
        }


       return index;





    }



    /**
     * Replace oldAdn with newAdn in ADN-like record in EF
     *
     * The ADN-like records must be read through requestLoadAllAdnLike() before
     *
     * @param efid must be one of EF_ADN, EF_FDN, and EF_SDN
     * @param oldAdn is the adn to be replaced
     *        If oldAdn.isEmpty() is ture, it insert the newAdn
     * @param newAdn is the adn to be stored
     *        If newAdn.isEmpty() is true, it delete the oldAdn
     * @param pin2 is required to update EF_FDN, otherwise must be null
     * @param response message to be posted when done
     *        response.exception hold the exception in error
     */
    public boolean updateAdnBySearch(int efid, AdnRecord oldAdn, AdnRecord newAdn,
            String pin2, Message response) {

        int extensionEF;
        extensionEF = extensionEfForEf(efid);
        Log.i("AdnRecordCache","updateAdnBySearch");
        if (extensionEF < 0) {
            sendErrorResponse(response, "EF is not known ADN-like EF:" + efid);
            return false;
        }
        Log.i("AdnRecordCache","updateAdnBySearch (1)");
        ArrayList<AdnRecord>  oldAdnList;
        oldAdnList = getRecordsIfLoaded(efid);

        if (oldAdnList == null) {
            sendErrorResponse(response, "Adn list not exist for EF:" + efid);
            return false;
        }
        Log.i("AdnRecordCache","updateAdnBySearch (2)");
        int index = -1;
        int count = 1;
        for (Iterator<AdnRecord> it = oldAdnList.iterator(); it.hasNext(); ) {
            if (oldAdn.isEqual(it.next())) {
                index = count;
            	mInsetIndex = index;
                break;
            }
            count++;
        }
        Log.i("AdnRecordCache","updateAdnBySearch (3) index" + index);
        if (index == -1 ) {
            if(isLastAdn(efid)){
                 sendErrorResponse(response, "Adn record don't exist for " + oldAdn);
            }
            Log.i("AdnRecordCache","updateAdnBySearch >>>>>>>>>>>>>>>>>>>>>>>> Adn record don't exist for" );
            return false;
        }

        Message pendingResponse = userWriteResponse.get(efid);

        if (pendingResponse != null) {
            sendErrorResponse(response, "Have pending update for EF:" + efid);
            return false;
        }

        userWriteResponse.put(efid, response);
        Log.i("AdnRecordCache","updateAdnBySearch (4) index" + index);
        /*new AdnRecordLoader(phone).updateEF(newAdn, efid, extensionEF,
                index, pin2,
                obtainMessage(EVENT_UPDATE_ADN_DONE, efid, index, newAdn));
        */
        //add multi record and email in usim
        new AdnRecordLoader(mFh).updateEF(newAdn, efid, extensionEF,
                index, pin2,
                obtainMessage(EVENT_UPDATE_ADN_DONE, efid, index, newAdn));

	 return true;
    }
    public  int[] getAdnRecordsSize(int efid){

            Log.i("AdnRecordCache","getAdnRecordsSize");
            return mUsimPhoneBookManager.getAdnRecordsSize(efid);
       
    }


     
	
    /**
     * Responds with exception (in response) if efid is not a known ADN-like
     * record
     */
    public void
    requestLoadAllAdnLike (int efid, int extensionEf, Message response) {
        ArrayList<Message> waiters;
        ArrayList<AdnRecord> result;

        if (efid == EF_PBR) {
            result = mUsimPhoneBookManager.loadEfFilesFromUsim();
        } else {
            result = getRecordsIfLoaded(efid);
        }

        // Have we already loaded this efid?
        if (result != null) {
            if (response != null) {
                AsyncResult.forMessage(response).result = result;
                response.sendToTarget();
            }

            return;
        }

        // Have we already *started* loading this efid?

        waiters = adnLikeWaiters.get(efid);

        if (waiters != null) {
            // There's a pending request for this EF already
            // just add ourselves to it

            waiters.add(response);
            return;
        }

        // Start loading efid

        waiters = new ArrayList<Message>();
        waiters.add(response);

        adnLikeWaiters.put(efid, waiters);


        if (extensionEf < 0) {
            // respond with error if not known ADN-like record

            if (response != null) {
                AsyncResult.forMessage(response).exception
                    = new RuntimeException("EF is not known ADN-like EF:" + efid);
                response.sendToTarget();
            }

            return;
        }

        /*new AdnRecordLoader(phone).loadAllFromEF(efid, extensionEf,
            obtainMessage(EVENT_LOAD_ALL_ADN_LIKE_DONE, efid, 0));
            */
        new AdnRecordLoader(mFh).loadAllFromEF(efid, extensionEf,
                obtainMessage(EVENT_LOAD_ALL_ADN_LIKE_DONE, efid, 0));
    }

    //***** Private methods

    private void
    notifyWaiters(ArrayList<Message> waiters, AsyncResult ar) {

        if (waiters == null) {
            return;
        }

        for (int i = 0, s = waiters.size() ; i < s ; i++) {
            Message waiter = waiters.get(i);

            AsyncResult.forMessage(waiter, ar.result, ar.exception);
            waiter.sendToTarget();
        }
    }

    //***** Overridden from Handler

    public void
    handleMessage(Message msg) {
        AsyncResult ar;
        int efid;
    	int index;
		AdnRecord adn;
		Message response;

        switch(msg.what) {
            case EVENT_LOAD_ALL_ADN_LIKE_DONE:
                /* arg1 is efid, obj.result is ArrayList<AdnRecord>*/
                ar = (AsyncResult) msg.obj;
                efid = msg.arg1;
                ArrayList<Message> waiters;

                waiters = adnLikeWaiters.get(efid);
                adnLikeWaiters.delete(efid);

                if (ar.exception == null) {
                    adnLikeFiles.put(efid, (ArrayList<AdnRecord>) ar.result);
                }
                notifyWaiters(waiters, ar);
                break;
            case EVENT_UPDATE_ADN_DONE:
                ar = (AsyncResult)msg.obj;
                efid = msg.arg1;
                index = msg.arg2;
                 adn = (AdnRecord) (ar.userObj);
				
                //yeezone:jinwei add sim_index.
                Log.i("AdnRecordCache","efid "+efid+"index " +index);
                adn.setRecordNumber(index);
                //end
				
                if (ar.exception == null) {
                    adnLikeFiles.get(efid).set(index - 1, adn);
                }
                Log.i("AdnRecordCache","efid" +efid);
                response = userWriteResponse.get(efid);
		      Log.i("AdnRecordCache","response" +response +"index " + index);
                userWriteResponse.delete(efid);

                //yeezone:jinwei return sim_index after add a new contact in SimCard.
                //AsyncResult.forMessage(response, null, ar.exception);
                AsyncResult.forMessage(response, index, ar.exception);
		   Log.i("AdnRecordCache","response" +response +"index " + index + "target "+ response.getTarget());
                response.sendToTarget();
                break;
           //add multi record and email in usim begin
            case EVENT_UPDATE_USIM_ADN_DONE:

                ar = (AsyncResult)msg.obj;
                efid = msg.arg1;
                index = msg.arg2;
                adn = (AdnRecord) (ar.userObj);
				int recNum = -1;
				for (int num = 0; num < mUsimPhoneBookManager.getNumRecs(); num++) {
					int adnEF = mUsimPhoneBookManager.findEFInfo(num);
					if(efid == adnEF){
						recNum = num;
					}
				}
				int [] mAdnRecordSizeArray = mUsimPhoneBookManager.getAdnRecordSizeArray();
				int adnRecNum;
				if(recNum == -1){
					break;
				}else if(recNum == 0){
					adnRecNum = index - 1;
				}else{
					adnRecNum = mAdnRecordSizeArray[recNum - 1] + index - 1;
				}

                if (ar.exception == null) {
					mUsimPhoneBookManager.setPhoneBookRecords(adnRecNum, adn);
                    adnLikeFiles.get(efid).set(index - 1, adn);
					updateUsimOthers = true;
                }else{
					Log.e("GSM","xiaojf1 fail to Update Usim Adn");
				}
				synchronized(mLock){
					mLock.notifyAll();
				}

                response = userWriteResponse.get(efid);
                userWriteResponse.delete(efid);

                AsyncResult.forMessage(response, null, ar.exception);
                response.sendToTarget();
                break;     
           //add multi record and email in usim   end
        }

    }


}
