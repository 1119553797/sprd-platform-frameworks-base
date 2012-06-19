/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.android.internal.telephony.AdnRecord;
import com.android.internal.telephony.AdnRecordCache;
import com.android.internal.telephony.IccConstants;
import com.android.internal.telephony.IccFileHandler;
import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.PhoneBase;

import org.apache.harmony.luni.lang.reflect.ListOfTypes;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import android.text.TextUtils;
import android.telephony.PhoneNumberUtils;
import com.android.internal.telephony.IccUtils;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

/**
 * This class implements reading and parsing USIM records. Refer to Spec 3GPP TS
 * 31.102 for more details.
 *
 * {@hide}
 */
public class UsimPhoneBookManager extends Handler implements IccConstants {
	private static final String LOG_TAG = "UsimPhoneBookManager";
	private static final boolean DBG = true;
	public PbrFile mPbrFile;
	private Boolean mIsPbrPresent;
	private PhoneBase mPhone;
	private IccFileHandler mFh;
	private AdnRecordCache mAdnCache;
	private Object mLock = new Object();
	private ArrayList<AdnRecord> mPhoneBookRecords;
	private boolean mEmailPresentInIap = false;
	private int mEmailTagNumberInIap = 0;
	private ArrayList<byte[]> mIapFileRecord;
	// add begin for add multi record and email in usim
	private Object[] mIapFileRecordArray;
	private int newEmailNum;
	private Set<Integer> usedEmailNumSet;
	public int[] mAdnRecordSizeArray;
	private int[] mEmailRecordSizeArray;
	private int[] mIapRecordSizeArray;
	protected int mTotalSize[] = null;
	protected int recordSize[] = new int[3];

	// add end
	private ArrayList<byte[]> mEmailFileRecord;
	private Map<Integer, ArrayList<String>> mEmailsForAdnRec;
	private ArrayList<byte[]> mPbcFileRecord;




	private static final int EVENT_PBR_LOAD_DONE = 1;
	private static final int EVENT_USIM_ADN_LOAD_DONE = 2;
	private static final int EVENT_IAP_LOAD_DONE = 3;
	private static final int EVENT_EMAIL_LOAD_DONE = 4;
	private static final int EVENT_ADN_RECORD_COUNT = 5;

	// add begin for add multi record and email in usim
	private static final int EVENT_AAS_LOAD_DONE = 6;
	private static final int EVENT_SNE_LOAD_DONE = 7;
	private static final int EVENT_GRP_LOAD_DONE = 8;
	private static final int EVENT_GAS_LOAD_DONE = 9;
	private static final int EVENT_ANR_LOAD_DONE = 10;
	private static final int EVENT_PBC_LOAD_DONE = 11;
	private static final int EVENT_EF_CC_LOAD_DONE = 12;
	private static final int EVENT_UPDATE_RECORD_DONE = 13;
	private static final int EVENT_LOAD_EF_PBC_RECORD_DONE = 14;
	// add end

	private ArrayList<byte[]> mAnrFileRecord;
	private ArrayList<byte[]> mTempAnrFileRecord;
	private int[] mAnrRecordSizeArray;
	public boolean ishaveAnr;
	public boolean ishaveEmail;

	private ArrayList<byte[]> mAasFileRecord;
	private int[] mAasRecordSizeArray;
	public boolean ishaveAas;

	private ArrayList<byte[]> mSneFileRecord;
	private int[] mSneRecordSizeArray;
	public boolean ishaveSne;

	private ArrayList<byte[]> mGrpFileRecord;
	private int[] mGrpRecordSizeArray;
	public boolean ishaveGrp;

	private ArrayList<byte[]> mGasFileRecord;
	private int[] mGasRecordSizeArray;
	public boolean ishaveGas;

	public int anrFileCount = 0;

	public static final int USIM_TYPE1_TAG = 0xA8;
	public static final int USIM_TYPE2_TAG = 0xA9;
	private static final int USIM_TYPE3_TAG = 0xAA;
	private static final int USIM_EFADN_TAG = 0xC0;
	private static final int USIM_EFIAP_TAG = 0xC1;
	private static final int USIM_EFEXT1_TAG = 0xC2;
	public static final int USIM_EFSNE_TAG = 0xC3;
	public static final int USIM_EFANR_TAG = 0xC4;
	private static final int USIM_EFPBC_TAG = 0xC5;
	public static final int USIM_EFGRP_TAG = 0xC6;
	public static final int USIM_EFAAS_TAG = 0xC7;
	public static final int USIM_EFGAS_TAG = 0xC8;
	private static final int USIM_EFUID_TAG = 0xC9;
	public static final int USIM_EFEMAIL_TAG = 0xCA;
	private static final int USIM_EFCCP1_TAG = 0xCB;
	public static final int USIM_SUBJCET_EMAIL = 0;
	public static final int USIM_SUBJCET_ANR = 1;
	private LinkedList<SubjectIndexOfAdn> mAnrInfoFromPBR = null;
	private LinkedList<SubjectIndexOfAdn> mEmailInfoFromPBR = null;
	private boolean mAnrPresentInIap = false;
	private int mDoneAdnCount = 0;

	public class SubjectIndexOfAdn {

             public int adnEfid;
 		public int[] type;
		// <efid, record >
		public Map<Integer, Integer> recordNumInIap;
		// map <efid,ArrayList<byte[]>fileRecord >
		public Map<Integer, ArrayList<byte[]>> record;

		public int[] efids;

		// ArrayList<int[]> usedNumSet;
		Object[] usedSet;

	};

	public UsimPhoneBookManager(PhoneBase phone, IccFileHandler fh,
			AdnRecordCache cache) {
		super();
		mFh = fh;
		mPhone = phone;
		mPhoneBookRecords = new ArrayList<AdnRecord>();
		mPbrFile = null;
		// We assume its present, after the first read this is updated.
		// So we don't have to read from UICC if its not present on subsequent
		// reads.
		mIsPbrPresent = true;
		mAdnCache = cache;
		// add begin for add multi record and email in usim
		ishaveEmail = false;
		ishaveAnr = false;
		ishaveAas = false;
		ishaveSne = false;
		ishaveGrp = false;
		ishaveGas = false;
		mTotalSize = null;
		// add end
		mLock = cache.getLock();
	}

	public void reset() {
		mPhoneBookRecords.clear();
		mIapFileRecord = null;
		mEmailFileRecord = null;
		mPbcFileRecord = null;
		mPbrFile = null;
		mIsPbrPresent = true;
		// add begin for add multi record and email in usim
		mAnrFileRecord = null;
		mAasFileRecord = null;
		mSneFileRecord = null;
		mGrpFileRecord = null;
		mGasFileRecord = null;
		ishaveEmail = false;
		ishaveAnr = false;
		ishaveAas = false;
		ishaveSne = false;
		ishaveGrp = false;
		ishaveGas = false;
		anrFileCount = 0;
		mAnrInfoFromPBR = null;
		mEmailInfoFromPBR = null;
		mTotalSize = null;
		// add end
	}

	public synchronized ArrayList<AdnRecord> loadEfFilesFromUsim() {
		synchronized (mLock) {
			if (!mPhoneBookRecords.isEmpty())
				return mPhoneBookRecords;
			if (!mIsPbrPresent)
				return null;
            mAdnCache.markAdnRecordLoaded(false);
			mAnrInfoFromPBR = new LinkedList<SubjectIndexOfAdn>();
			mEmailInfoFromPBR = new LinkedList<SubjectIndexOfAdn>();
			// Check if the PBR file is present in the cache, if not read it
			// from the USIM.
			Log.i("UsimPhoneBookManager", "loadEfFilesFromUsim");
			if (mPbrFile == null) {
				readPbrFileAndWait();
			}

			if (mPbrFile == null)
				return null;

			int numRecs = mPbrFile.mFileIds.size();

			// add begin for add multi record and email in usim
			mIapFileRecordArray = new Object[numRecs];
			mAdnRecordSizeArray = new int[numRecs];
			mEmailRecordSizeArray = new int[numRecs];
			mIapRecordSizeArray = new int[numRecs];
			mAnrRecordSizeArray = new int[numRecs];
			mAasRecordSizeArray = new int[numRecs];
			mSneRecordSizeArray = new int[numRecs];
			mGrpRecordSizeArray = new int[numRecs];
			mGasRecordSizeArray = new int[numRecs];

			// add end

            mTotalSize = null;
			mDoneAdnCount = 0;
			Log.i("UsimPhoneBookManager", "loadEfFilesFromUsim" + numRecs);
			for (int i = 0; i < numRecs; i++) {
				readAdnFileAndWait(i);
                int[] size = readAdnFileSizeAndWait(i);
                if (mTotalSize == null) {
                    mTotalSize = new int[3];
                }
                if (size != null) {
                    mTotalSize[0] = size[0];
                    mTotalSize[1] += size[1];
                    mTotalSize[2] += size[2];
                }
				readIapFile(i);
				readEmailFileAndWait(i);

				// add begin for add multi record and email in usim
				readAnrFileAndWait(i);
				updateAdnRecord(i);
				// updatePhoneAdnRecord(i);
				// add end
			}
			CheckRepeatType2Ef();
			// All EF files are loaded, post the response.
		}
        mAdnCache.markAdnRecordLoaded(true);
        // for cta case8.1.1,update the EFpbc&Efcc
        updatePbcAndCc();
		return mPhoneBookRecords;
	}

   private void updatePbcAndCc() {
        Log.i(LOG_TAG, "update EFpbcbegin");
        Map<Integer, Integer> fileIds;
        fileIds = mPbrFile.mFileIds.get(0);
        if (fileIds == null || fileIds.isEmpty())
            return;
        Integer efPbcId = fileIds.get(USIM_EFPBC_TAG);
        Log.i(LOG_TAG, " USIM_EFPBC_TAG = "
                + Integer.toHexString(efPbcId));
        if (efPbcId == null)
            return;
        int changeCounter = 0;
        mFh.loadEFLinearFixedAll(efPbcId,obtainMessage(EVENT_LOAD_EF_PBC_RECORD_DONE));
        try {
            mLock.wait();
        } catch (InterruptedException e) {
            Log.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
        }
        for (int i = 0; i < mPbcFileRecord.size(); i++) {
            byte[] temp = null;
            temp = mPbcFileRecord.get(i);
            if (temp != null && ((temp[0]&0xFF) == 0x01)) {
                changeCounter++;
                byte[] data = new byte[2];
                data[0] = (byte) 0x00;
                data[1] = (byte) 0x00;
                //udpate EF pbc
                mFh.updateEFLinearFixed(efPbcId, i + 1, data, null,
                        obtainMessage(EVENT_UPDATE_RECORD_DONE));
           }

       }
        Log.i(LOG_TAG, "update EFpbc end, validAdnCount " + changeCounter);
        // update EFcc
       if (changeCounter > 0) {
           // get Change Counter
            mFh.loadEFTransparent(IccConstants.EF_CC,
                    obtainMessage(EVENT_EF_CC_LOAD_DONE, changeCounter));
        }
        return;
    }

    // add begin for add multi record and email in usim
	public int getNumRecs() {
		// add begin 2010-11-25 for reload the pbr file when the pbr
		// file is null
		synchronized (mLock) {
			if (mPbrFile == null) {
				readPbrFileAndWait();
			}
			if (mPbrFile == null) {
				Log.e(LOG_TAG, "Error: Pbr file is empty");
				return 0;
			}
		}
		return mPbrFile.mFileIds.size();
	}

	public int getAnrNum() {

		int num = 0;

             int[] efids =  getSubjectEfids(USIM_SUBJCET_ANR,0);

		if(efids == null){

                   return 0;
		}
		num = efids.length;

		log( "getAnrNum " +  num);

		return num;

	}

	public int getEmailNum() {

		if (ishaveEmail) {
			Log.i("UsimPhoneBookManager", "ishaveEmail " + ishaveEmail);
                   int[] efids =  getSubjectEfids(USIM_SUBJCET_EMAIL,0);

		      if(efids == null){

                      return 0;
		     }
		     int num = efids.length;

		      log( "getEmailNum " +  num);

			return num;
		} else {

			return 0;
		}

	}
public int[] getValidNumToMatch(AdnRecord adn,int type, int[] subjectNums)
{
           int []  ret = null;
	     int  efid = 0;

           for (int num = 0; num < getNumRecs(); num++) {

			efid = findEFInfo(num);

			if (efid < 0 ) {

				return null;
			}
			Log.i(LOG_TAG, "getEfIdToMatch ");
			ArrayList<AdnRecord> oldAdnList;

			Log.e(LOG_TAG, "efid is " + efid);

			oldAdnList = mAdnCache.getRecordsIfLoaded(efid);
			if (oldAdnList == null) {

				return null;
			}
			Log.i(LOG_TAG, "getEfIdToMatch (2)");



			int count = 1;
			int adnIndex = 0;

			for (Iterator<AdnRecord> it = oldAdnList.iterator(); it.hasNext();) {
				if (adn.isEqual(it.next())) {
					Log.i(LOG_TAG, "we got the index " + count);
                                adnIndex = count;

                                ret = getAvalibleSubjectCount(num, type,efid,adnIndex,subjectNums);
					if(ret != null){

						return ret;

					}


				}
				count++;
			}



	        }

		  return null;

      	}
private int getAvalibleAdnCount(){

       List<AdnRecord> adnRecords = mPhoneBookRecords;
       int totalCount = 0;
	 int count = 0;
	 if(mPhoneBookRecords == null){

              return 0;
	 }

       totalCount = mPhoneBookRecords.size();
       AdnRecord adnRecord;
	 for(int i=0; i< totalCount; i++){
            adnRecord =  mPhoneBookRecords.get(i);
            if(adnRecord.isEmpty()){

                 count++;
	     }


	 }


       return count;

}
public int[] getAvalibleSubjectCount(int num, int type, int efid ,int adnNum, int[] subjectNums){


	     SubjectIndexOfAdn index = null;
            int count = 0;
	     int avalibleNum = 0;
	     int[] ret = null;
	     int n = 0;


	     log("getAvalibleSubjectCount efid " +  efid + " num " +num );
	     log("getAvalibleSubjectCount  " +  " type " + type + " adnNum " + adnNum + "  subjectNums " + subjectNums);

	     index = getSubjectIndex( type,  num);

		if (index == null ) {

		     return null;
		}

             ret = new int [subjectNums.length];
		log("getAvalibleSubjectCount adnEfid " + index.adnEfid);

		if(index != null  && index.adnEfid == efid &&index.record != null && index.efids!= null && index.type!= null){


		     for(int j=0; j< index.efids.length ;j++){

                     log("getAvalibleSubjectCount efid " +  index.efids[j] );

			  for(int l=0; l<subjectNums.length; l++){

        	              log("getAvalibleSubjectCount efid " +  subjectNums[l]  );
                           if(subjectNums[l] == 1 && index.record.containsKey(index.efids[j]) &&index.record.get(index.efids[j])!= null ){

				        count = index.record.get(index.efids[j]).size();
					  // log("getAvalibleSubjectCount index.type[j] " +  index.type[j]  );
                                  if(index.type[j] == USIM_TYPE1_TAG ){

						 ret[n] = getAvalibleAdnCount();
						 n++;
						 break;

					  }else if(index.type[j] == USIM_TYPE2_TAG ){

						       int idx = getUsedNumSetIndex( index.efids[j],  index);
							 log("getAvalibleSubjectCount idx " +  idx );

                                             if(idx >=0 ){
								Set<Integer> usedSet =  (Set<Integer>) index.usedSet[idx];
                                                   avalibleNum =  count -usedSet.size();
								ret[n] =  avalibleNum;
						             n++;

                                                   break;
						       }



					  }



			             }
				  }
			    }
		 }

             log("getAvalibleSubjectCount  n " +  n  );
	       if( n == 0){

		       ret = null;
			 return ret;

		}
		for(int i= 0; i<ret.length; i++ ){

                   log("getAvalibleSubjectCount  ret[] " +  ret[i]  );
		}
             return ret;
	}


	public  int [] getAvalibleAnrCount(String name, String number,
			String[] emails, String anr, int[] anrNums){
              AdnRecord   adn  = new AdnRecord(name, number, emails,
						anr,"","","","" );
              return getValidNumToMatch(adn,USIM_SUBJCET_ANR,anrNums);
	}

	public int [] getAvalibleEmailCount(String name, String number,
			String[] emails, String anr, int[] emailNums){
            AdnRecord   adn  = new AdnRecord(name, number, emails,
						anr,"","","","" );
            return getValidNumToMatch(adn,USIM_SUBJCET_EMAIL,emailNums);
	}

	public ArrayList<byte[]> getIapFileRecord(int recNum) {
		int efid = findEFIapInfo(recNum);
		// add begin 2010-11-29 for avoid some exception because ril
		// restart
		if (efid < 0) {
			return null;
		}
		return (ArrayList<byte[]>) mIapFileRecordArray[recNum];
	}

	public PbrFile getPbrFile() {
		return mPbrFile;
	}

	public void setIapFileRecord(int recNum, int index, byte value, int numInIap) {
             log("setIapFileRecord >>  recNum: " + recNum + "index: "
				+ index + " numInIap: " +numInIap + " value: " +value );
		ArrayList<byte[]> tmpIapFileRecord = (ArrayList<byte[]>) mIapFileRecordArray[recNum];
		byte[] record = tmpIapFileRecord.get(index);
		record[numInIap] = value;
		tmpIapFileRecord.set(index, record);
		mIapFileRecordArray[recNum] = tmpIapFileRecord;

	}

	private SubjectIndexOfAdn getSubjectIndex(int type, int num) {

		LinkedList<SubjectIndexOfAdn> lst = null;
		SubjectIndexOfAdn index = null;
		switch (type) {

		case USIM_SUBJCET_EMAIL:

			lst = mEmailInfoFromPBR;
			break;

		case USIM_SUBJCET_ANR:

			lst = mAnrInfoFromPBR;
			break;
		default:
			break;
		}

		if (lst != null && lst.size() != 0) {
			index = lst.get(num);
			return index;

		}

		return null;

	}

	public int[] getSubjectEfids(int type, int num) {

		SubjectIndexOfAdn index = getSubjectIndex(type, num);

		if (index == null) {

			return null;

		}
		int[] result = index.efids;
		if(result != null){
             Log.i(LOG_TAG, "getSubjectEfids  "  + "length "
			+ result.length );
		}
		return result;

	}

	public int[][] getSubjectTagNumberInIap(int type, int num) {

		Map<Integer, Integer> anrTagMap = null;
		SubjectIndexOfAdn index = getSubjectIndex(type, num);
             boolean isInIap = false;
		if (index == null) {

			return null;

		}
		int[][] result = new int[index.efids.length][2];
		anrTagMap = index.recordNumInIap;
		if(anrTagMap == null || anrTagMap.size() == 0 ){
			log("getSubjectTagNumberInIap recordNumInIap == null");
                   return null;
		}
		for (int i = 0; i < index.efids.length; i++) {

			if(anrTagMap.containsKey(index.efids[i])){
			     result[i][1] = anrTagMap.get(index.efids[i]);
			     result[i][0] = index.efids[i];
			     isInIap = true;
			}
		}

		if(!isInIap){

                   result = null;
		      log("getSubjectTagNumberInIap isInIap == false");
		}

		return result;

	}

	public int[][] getAnrTagNumberInIap(int num) {

		Map<Integer, Integer> anrTagMap;
		int[][] result = new int[mAnrInfoFromPBR.get(num).efids.length][2];
		anrTagMap = mAnrInfoFromPBR.get(num).recordNumInIap;
		for (int i = 0; i < mAnrInfoFromPBR.get(num).efids.length; i++) {
			result[i][0] = mAnrInfoFromPBR.get(num).efids[i];
			result[i][1] = anrTagMap.get(mAnrInfoFromPBR.get(num).efids[i]);
		}

		return result;

	}

	private void setSubjectIndex(int type, int num,
			SubjectIndexOfAdn subjectIndex) {

		SubjectIndexOfAdn index = null;

		switch (type) {

		case USIM_SUBJCET_EMAIL:
			if (mEmailInfoFromPBR == null) {

				return;
			}

			mEmailInfoFromPBR.set(num, subjectIndex);
			break;

		case USIM_SUBJCET_ANR:
			if (mAnrInfoFromPBR == null) {

				return;
			}
			mAnrInfoFromPBR.set(num, subjectIndex);

			break;
		default:
			break;
		}

	}

	public  Set<Integer>  getUsedNumSet( Set<Integer>  set1,  Set<Integer> set2, int count){

            Set<Integer> totalSet =  set1 ;

	     for (int i = 1; i < count; i++) {
			Integer subjectNum = new Integer(i);
			Log.i(LOG_TAG, "getUsedNumSet  subjectNum (0)"
						+ subjectNum);
			if (!totalSet.contains(subjectNum) && set2.contains(subjectNum)) {

				Log.i(LOG_TAG, "getUsedNumSet  subjectNum(1) "
							+ subjectNum);
				totalSet.add(subjectNum);


			}
	     }

           return totalSet;


	}

	public  Set<Integer> getRepeatUsedNumSet(LinkedList<SubjectIndexOfAdn> lst,int idx, int efid, Set<Integer> set, int count  ){
            SubjectIndexOfAdn index = null;

	      Set<Integer> totalSet = set;

            for(int m=idx+1; m<lst.size(); m++){

                    index = lst.get(m);

			 if(index != null  )
                    {
		                 int num = getUsedNumSetIndex( efid,  index);
                              if(num >=0){
                              totalSet =  getUsedNumSet((Set<Integer>) index.usedSet[num],totalSet,count);

                              }


			   }


		 }





             return totalSet;
	}
	private void SetRepeatUsedNumSet(int type,int efid, Set<Integer> totalSet){

	     SubjectIndexOfAdn index = null;
            LinkedList<SubjectIndexOfAdn> lst = null;

		switch (type) {

		case USIM_SUBJCET_EMAIL:

			lst = mEmailInfoFromPBR;
			break;

		case USIM_SUBJCET_ANR:

			lst = mAnrInfoFromPBR;
			break;
		default:
			break;
		}

		if(lst ==  null){
                  return;

		}

            for(int m=0; m<lst.size(); m++){

                    index = lst.get(m);

			 if(index != null && index.recordNumInIap !=null)
                    {

                               int num = getUsedNumSetIndex( efid,  index);
		                  if(num >= 0){
                                     log(" SetRepeatUsedNumSet efid  " + efid + " num  " + num   + "  totalSet.size  " + totalSet.size());
                                     index.usedSet[num] = totalSet;
                                     setSubjectIndex(type,m,index);


					}

			       }



		       }





	}
	private int getUsedNumSetIndex(int efid, SubjectIndexOfAdn index){
             int count = -1;

             if(index != null && index.efids != null ){

			for(int k=0; k<index.efids.length; k++){
				log("getUsedNumSetIndex index.type[k] " + index.type[k]);

		             if( index.type[k] == USIM_TYPE2_TAG ){
                                 count++;
                                 if(index.efids[k] == efid ){
	                                 log("getUsedNumSetIndex count " + count);

                                        return count;
					 }

			       }
		     }

             }

	       return -1;

	}
	private void SetMapOfRepeatEfid(int type, int efid){

             LinkedList<SubjectIndexOfAdn> lst = null;
		SubjectIndexOfAdn index = null;
		int efids[];
       	Set<Integer> set ;
             Set<Integer> totalSet = new HashSet<Integer>() ;
		switch (type) {

		case USIM_SUBJCET_EMAIL:

			lst = mEmailInfoFromPBR;
			break;

		case USIM_SUBJCET_ANR:

			lst = mAnrInfoFromPBR;
			break;
		default:
			break;
		}

		if (lst != null && lst.size() != 0) {
                   int i =0,j=0;



                   for(i=0; i<lst.size(); i++){

                             index = lst.get(i);

				    if(index != null && index.recordNumInIap !=null && index.record != null && index.record.containsKey(efid) ){
				      	  int count = index.record.get(efid).size();
			               Log.i(LOG_TAG, "SetMapOfRepeatEfid  "  + "count "	+ count );

					  int num = getUsedNumSetIndex( efid,  index);

					  if(num >= 0){
                                      set = (Set<Integer>) index.usedSet[num];
						if(set != null){
                                           log("SetMapOfRepeatEfid  size " + set.size() );
						}
        				      totalSet = getUsedNumSet(totalSet,set,count);
					   }
					}

				   }




			}

                   if(totalSet != null){
                         log("SetMapOfRepeatEfid  size " + totalSet.size() );
		      }
		      SetRepeatUsedNumSet(type,efid,totalSet);


	}

	public int getNewSubjectNumber(int type, int num, int efid, int index,
			int adnNum, boolean isInIap) {

            	Log.i(LOG_TAG, "getNewSubjectNumber  "  + " adnNum "
			+ adnNum + " isInIap " + isInIap + " efid " +efid + " index " + index );
		SubjectIndexOfAdn idx = getSubjectIndex(type, num);
		int newSubjectNum = -1;
		if (idx == null) {

			return -1;
		}

		if(idx.record == null || !idx.record.containsKey(efid) ){
                   log("getNewSubjectNumber idx.record == null || !idx.record.containsKey(efid)  ");
                   return -1;
		}



		int count = idx.record.get(efid).size();

     		Log.i(LOG_TAG, "getNewSubjectNumber  count " + count + "adnNum "
				+ adnNum);

		if (isInIap) {

			Set<Integer> set = (Set<Integer>) idx.usedSet[index];
			for (int i = 1; i <= count; i++) {
				Integer subjectNum = new Integer(i);
				//Log.i(LOG_TAG, "getNewSubjectNumber  subjectNum (0)"
				//		+ subjectNum);
				if (!set.contains(subjectNum)) {

					newSubjectNum = subjectNum;
					Log.i(LOG_TAG, "getNewSubjectNumber  subjectNum(1) "
							+ subjectNum);
					set.add(subjectNum);
					idx.usedSet[index] = set;
					setSubjectIndex(type, num, idx);
					SetRepeatUsedNumSet(type,efid,set);
					break;
				}
			}
		} else {

			if (adnNum > count) {

				return newSubjectNum;
			} else {

				return adnNum;
			}
		}
		return newSubjectNum;

	}

	public void removeSubjectNumFromSet(int type, int num, int efid, int index,
			int anrNum) {
		Integer delNum = new Integer(anrNum);
		SubjectIndexOfAdn subject = getSubjectIndex(type, num);

		if (subject == null) {
			return;

		}
		int count = subject.record.get(efid).size();

		Set<Integer> set = (Set<Integer>) subject.usedSet[index];
		set.remove(delNum);
		Log.i(LOG_TAG, "removeSubjectNumFromSet  delnum(1) " + delNum);

		subject.usedSet[index] = set;
		setSubjectIndex(type, num, subject);

	}

	public int[] getEmailRecordSizeArray() {
		return mEmailRecordSizeArray;
	}

	public int[] getIapRecordSizeArray() {
		return mIapRecordSizeArray;

	}

	public void setPhoneBookRecords(int index, AdnRecord adn) {
		mPhoneBookRecords.set(index, adn);
	}

	public int[] getAdnRecordSizeArray() {
		return mAdnRecordSizeArray;
	}

	public int findEFInfo(int index) {
		Map<Integer, Integer> fileIds;
		synchronized (mLock) {
			if (mPbrFile == null) {
				readPbrFileAndWait();
			}
		}
		if (mPbrFile == null) {
			Log.e(LOG_TAG, "Error: Pbr file is empty");
			return -1;
		}
		fileIds = mPbrFile.mFileIds.get(index);

		if (fileIds == null) {
			Log.e(LOG_TAG, "Error: fileIds is empty");
			return -1;

		}

		if (fileIds.containsKey(USIM_EFADN_TAG)) {

			return fileIds.get(USIM_EFADN_TAG);
		}

		return -1;
	}

	public int findExtensionEFInfo(int index) {

		Map<Integer, Integer> fileIds;

		synchronized (mLock) {
			if (mPbrFile == null) {
				readPbrFileAndWait();
			}
		}
		if (mPbrFile == null) {
			Log.e(LOG_TAG, "Error: Pbr file is empty");
			return -1;
		}
		fileIds = mPbrFile.mFileIds.get(index);

		if (fileIds == null) {
			Log.e(LOG_TAG, "Error: fileIds is empty");
			return -1;
		}

		Log.i(LOG_TAG, "findExtensionEFInfo fileIds " + fileIds);

		if (fileIds.containsKey(USIM_EFEXT1_TAG)) {

			return fileIds.get(USIM_EFEXT1_TAG);
		}

		return 0;

	}

	public int findEFEmailInfo(int index) {

		Map<Integer, Integer> fileIds;

		synchronized (mLock) {
			if (mPbrFile == null) {
				readPbrFileAndWait();
			}
		}
		if (mPbrFile == null) {
			Log.e(LOG_TAG, "Error: Pbr file is empty");
			return -1;
		}
		fileIds = mPbrFile.mFileIds.get(index);
		if (fileIds == null) {
			Log.i(LOG_TAG, "findEFEmailInfo  fileIds == null  index :" + index);
			return -1;
		}
		if (fileIds.containsKey(USIM_EFEMAIL_TAG)) {

			return fileIds.get(USIM_EFEMAIL_TAG);
		}

		return 0;
	}

	public int findEFAnrInfo(int index) {

		Map<Integer, Integer> fileIds;
		// for reload the pbr file when the pbr file is null
		synchronized (mLock) {
			if (mPbrFile == null) {
				readPbrFileAndWait();
			}
		}
		if (mPbrFile == null) {
			Log.e(LOG_TAG, "Error: Pbr file is empty");
			return -1;
		}
		fileIds = mPbrFile.mFileIds.get(index);
		if (fileIds == null) {
			Log.i(LOG_TAG, "findEFAnrInfo  fileIds == null  index :" + index);
			return -1;
		}
		if (fileIds.containsKey(USIM_EFANR_TAG)) {
			return fileIds.get(USIM_EFANR_TAG);
		}

		return 0;
	}

	public int findEFIapInfo(int index) {

		Map<Integer, Integer> fileIds;

		synchronized (mLock) {
			if (mPbrFile == null) {
				readPbrFileAndWait();
			}
		}
		if (mPbrFile == null) {
			Log.e(LOG_TAG, "Error: Pbr file is empty");
			return -1;
		}
		fileIds = mPbrFile.mFileIds.get(index);
		if (fileIds == null) {
			Log.i(LOG_TAG, "findEFIapInfo  fileIds == null  index :" + index);
			return -1;
		}
		if (fileIds.containsKey(USIM_EFIAP_TAG)) {
			return fileIds.get(USIM_EFIAP_TAG);
		}

		return -1;
	}

	// add end

	public int[] getAdnRecordsSize() {
		int size[] = new int[3];


		Log.i("UsimPhoneBookManager", "getEFLinearRecordSize");

		//size[2] = mPhoneBookRecords.size();
		//Log.i("UsimPhoneBookManager", "getEFLinearRecordSize size" + size[2]);
             if(mTotalSize != null){

                    return mTotalSize;
		 }

		 synchronized (mLock) {

		  if (mPbrFile == null)  {

			readPbrFileAndWait();

		  }

		  if (mPbrFile == null) return null;

		  int numRecs = mPbrFile.mFileIds.size();

		  Log.i("UsimPhoneBookManager" ,"loadEfFilesFromUsim" +numRecs);

		  for  (int i = 0; i < numRecs; i++)
		  {

		      size = readAdnFileSizeAndWait(i);

			if(mTotalSize == null){

			     mTotalSize = new int[3];

			}
			if(size != null){
			     mTotalSize[0] = size[0];
			     mTotalSize[1] += size[1];
			     mTotalSize[2] += size[2];

			}
		  } // All EF files are loaded, post the response. }

		 }
		return mTotalSize;
	}

	private int[] readAdnFileSizeAndWait(int recNum) {
	     synchronized (mLock) {
			if (mPbrFile == null) {
				readPbrFileAndWait();
			}
		}
		if (mPbrFile == null) {
			Log.e(LOG_TAG, "Error: Pbr file is empty");
			return null;
		}
		Map<Integer, Integer> fileIds;

		fileIds = mPbrFile.mFileIds.get(recNum);
		Log.i("UsimPhoneBookManager", "recNum" + recNum);

		if (fileIds == null || fileIds.isEmpty())
			return null;

		mPhone.getIccFileHandler().getEFLinearRecordSize(
				fileIds.get(USIM_EFADN_TAG),
				obtainMessage(EVENT_ADN_RECORD_COUNT));
		try {
			mLock.wait();
		} catch (InterruptedException e) {
			Log.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
		}

		return recordSize;
	}

	public int[] getEfFilesFromUsim() {

		int[] efids = null;

		int len = 0;

		len = mPbrFile.mFileIds.size();
		Log.i("UsimPhoneBookManager", "getEfFilesFromUsim" + len);
		efids = new int[len];

		for (int i = 0; i < len; i++) {
			Map<Integer, Integer> fileIds = mPbrFile.mFileIds.get(i);
			efids[i] = fileIds.get(USIM_EFADN_TAG);
			Log.i("UsimPhoneBookManager", "getEfFilesFromUsim" + efids[i]);
		}

		return efids;
	}

	private void readPbrFileAndWait() {
		Log.i("UsimPhoneBookManager", "readPbrFileAndWait");
		mPhone.getIccFileHandler().loadEFLinearFixedAll(EF_PBR,
				obtainMessage(EVENT_PBR_LOAD_DONE));
		try {
			mLock.wait();
		} catch (InterruptedException e) {
			Log.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
		}
	}

	private int[] readAdnFileAndWait(int recNum) {
		Log.i("UsimPhoneBookManager", "readAdnFileAndWait");
		synchronized (mLock) {
			if (mPbrFile == null) {
				readPbrFileAndWait();
			}
		}
		if (mPbrFile == null) {
			Log.e(LOG_TAG, "Error: Pbr file is empty");
			return null;
		}
		// END 20110413
		Map<Integer, Integer> fileIds;
		fileIds = mPbrFile.mFileIds.get(recNum);
		if (fileIds == null || fileIds.isEmpty())
			return null;

		int extEf = 0;
		// Only call fileIds.get while EFEXT1_TAG is available
		if (fileIds.containsKey(USIM_EFEXT1_TAG)) {
			extEf = fileIds.get(USIM_EFEXT1_TAG);
		}

		mAdnCache.requestLoadAllAdnLike(fileIds.get(USIM_EFADN_TAG), extEf,
				obtainMessage(EVENT_USIM_ADN_LOAD_DONE));
		try {
			mLock.wait();
		} catch (InterruptedException e) {
			Log.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
		}
		return recordSize;
	}

	private void readIapFile(int recNum) {

		log("readIapFile recNum " + recNum);

		synchronized (mLock) {
			if (mPbrFile == null) {
				readPbrFileAndWait();
			}
		}
		if (mPbrFile == null) {
			Log.e(LOG_TAG, "Error: Pbr file is empty");
			return;
		}
		Map<Integer, Integer> fileIds;
		fileIds = mPbrFile.mFileIds.get(recNum);
		if (fileIds == null)
			return;
		if (mAnrPresentInIap || mEmailPresentInIap) {

			readIapFileAndWait(fileIds.get(USIM_EFIAP_TAG));

		}

	}

	private void readEmailFileAndWait(int recNum) {
		log("readEmailFileAndWait");
		synchronized (mLock) {
			if (mPbrFile == null) {
				readPbrFileAndWait();
			}
		}
		if (mPbrFile == null) {
			Log.e(LOG_TAG, "Error: Pbr file is empty");
			return;
		}
		Map<Integer, Integer> fileIds;
		fileIds = mPbrFile.mFileIds.get(recNum);
		if (fileIds == null)
			return;

		if (fileIds.containsKey(USIM_EFEMAIL_TAG)) {
			ishaveEmail = true;
			int efid = fileIds.get(USIM_EFEMAIL_TAG);
			// Check if the EFEmail is a Type 1 file or a type 2 file.
			// If mEmailPresentInIap is true, its a type 2 file.
			// So we read the IAP file and then read the email records.
			// instead of reading directly.
			SubjectIndexOfAdn records = getSubjectIndex(USIM_SUBJCET_EMAIL,recNum);

			if(records == null){
                        log("readEmailFileAndWait  records == null ");
                        return;
			}
			if (mEmailPresentInIap) {
				// readIapFileAndWait(fileIds.get(USIM_EFIAP_TAG));
				if (mIapFileRecord == null) {
					Log.e(LOG_TAG, "Error: IAP file is empty");
					records = null;
					setSubjectIndex(USIM_SUBJCET_EMAIL,recNum,records);
					return;
				}
			}
			// Read the EFEmail file.
			mPhone.getIccFileHandler().loadEFLinearFixedAll(
					fileIds.get(USIM_EFEMAIL_TAG),
					obtainMessage(EVENT_EMAIL_LOAD_DONE));
			try {
				mLock.wait();
			} catch (InterruptedException e) {
				Log.e(LOG_TAG, "Interrupted Exception in readEmailFileAndWait");
			}


			if (mEmailFileRecord == null) {
				Log.e(LOG_TAG, "Error: Email file is empty");
				records = null;
				setSubjectIndex(USIM_SUBJCET_EMAIL,recNum,records);
				return;
			}


			records.record = new HashMap<Integer, ArrayList<byte[]>>();
			records.record.put(efid, mEmailFileRecord);
                   log("readEmailFileAndWait recNum "+ recNum + "  mEmailFileRecord  size " + mEmailFileRecord.size() );
			setSubjectIndex(USIM_SUBJCET_EMAIL,recNum,records);
			setSubjectUsedNum(USIM_SUBJCET_EMAIL, recNum);

			mEmailFileRecord = null;
		}

	}

	private void readIapFileAndWait(int efid) {
		Log.i("UsimPhoneBookManager", "readIapFileAndWait");
		mPhone.getIccFileHandler().loadEFLinearFixedAll(efid,
				obtainMessage(EVENT_IAP_LOAD_DONE));
		try {
			mLock.wait();
		} catch (InterruptedException e) {
			Log.e(LOG_TAG, "Interrupted Exception in readIapFileAndWait");
		}
	}
      private void handleReadFileResult(SubjectIndexOfAdn records ){
            log("handleReadFileResult  " );
		int i=0;
	      ArrayList<Integer> efs = new  ArrayList<Integer>();
            if(records == null ||records.efids == null){
                    log("handleReadFileResult records == null ||records.efids == null ");
                    return;
	      }


             for(i=0; i< records.efids.length; i++){

		     if(records.efids[i] != 0){

                        efs.add(records.efids[i]);
		     }else{
                        log("handleReadFileResult err efid " +  records.efids[i]);
			     if(records.recordNumInIap != null && records.recordNumInIap.containsKey(records.efids[i])){
                              records.recordNumInIap.remove(records.efids[i]);
			     }
		     }

		}
		 log("handleReadFileResult  efs " + efs );
             int[] validEf = new int[efs.size()];
             for(i=0; i<efs.size();i++){

                  validEf[i] = efs.get(i);

	      }

	      records.efids = validEf;


      }

	private void readAnrFileAndWait(int recNum) {
		log("readAnrFileAndWait recNum " + recNum);
		synchronized (mLock) {
			if (mPbrFile == null) {
				readPbrFileAndWait();
			}
		}
		if (mPbrFile == null) {
			Log.e(LOG_TAG, "Error: Pbr file is empty");
			return;
		}
		log("readAnrFileAndWait recNum is	" + recNum);
		Map<Integer, Integer> fileIds;
		fileIds = mPbrFile.mFileIds.get(recNum);

		if (fileIds == null){
			log("readAnrFileAndWait  fileIds == null" );
			return;
		}

      		log("readAnrFileAndWait  mAnrInfoFromPBR !=null fileIds.size()	 " + fileIds.size() );
		if (fileIds.containsKey(USIM_EFANR_TAG)) {
			ishaveAnr = true;

			SubjectIndexOfAdn records = getSubjectIndex(USIM_SUBJCET_ANR,recNum);

			if(records == null){
                        log("readAnrFileAndWait  records == null ");
                        return;
			}

			records.record = new HashMap<Integer, ArrayList<byte[]>>();

			if(records.efids == null || records.efids.length == 0){

			      log("readAnrFileAndWait  records.efids == null || records.efids.length == 0");
                         return;
			}
			anrFileCount = records.efids.length;
			boolean isFail =false;
			log("readAnrFileAndWait anrFileCount" + anrFileCount);
			// Read the anr file.
			for (int i = 0; i < anrFileCount; i++) {

				mFh.loadEFLinearFixedAll(records.efids[i],
						obtainMessage(EVENT_ANR_LOAD_DONE));
				try {
					mLock.wait();
				} catch (InterruptedException e) {
					Log.e(LOG_TAG,
							"Interrupted Exception in readEmailFileAndWait");
				}

				log("load ANR times ...... " + (i + 1));

				if (mAnrFileRecord == null) {
					Log.e(LOG_TAG, "Error: ANR file is empty");
					records.efids[i] = 0;
					isFail = true;
					continue;
				}

				records.record.put(records.efids[i], mAnrFileRecord);
				mAnrFileRecord = null;
			}
			//if(isFail)//@ temp
			{
                         handleReadFileResult(records);
			}
			setSubjectIndex(USIM_SUBJCET_ANR,recNum,records);
			setSubjectUsedNum(USIM_SUBJCET_ANR, recNum);
		}

	}

	private void updatePhoneAdnRecord() {
		if (mEmailFileRecord == null)
			return;
		int numAdnRecs = mPhoneBookRecords.size();
		if (mIapFileRecord != null) {
			// The number of records in the IAP file is same as the number of
			// records in ADN file.
			// The order of the pointers in an EFIAP shall be the same as the
			// order of file IDs
			// that appear in the TLV object indicated by Tag 'A9' in the
			// reference file record.
			// i.e value of mEmailTagNumberInIap

			for (int i = 0; i < numAdnRecs; i++) {
				byte[] record = null;
				try {
					record = mIapFileRecord.get(i);

				} catch (IndexOutOfBoundsException e) {
					Log
							.e(LOG_TAG,
									"Error: Improper ICC card: No IAP record for ADN, continuing");
					break;
				}

				int recNum = record[mEmailTagNumberInIap];

				if (recNum != -1) {
					String[] emails = new String[1];
					// SIM record numbers are 1 based
					emails[0] = readEmailRecord(recNum - 1);
					AdnRecord rec = mPhoneBookRecords.get(i);
					if (rec != null) {
						rec.setEmails(emails);
					} else {
						// might be a record with only email
						rec = new AdnRecord("", "", emails);
					}
					mPhoneBookRecords.set(i, rec);
				}
			}
		}

		// ICC cards can be made such that they have an IAP file but all
		// records are empty. So we read both type 1 and type 2 file
		// email records, just to be sure.

		int len = mPhoneBookRecords.size();
		// Type 1 file, the number of records is the same as the number of
		// records in the ADN file.
		if (mEmailsForAdnRec == null) {
			parseType1EmailFile(len);
		}
		for (int i = 0; i < numAdnRecs; i++) {
			ArrayList<String> emailList = null;
			try {
				emailList = mEmailsForAdnRec.get(i);
			} catch (IndexOutOfBoundsException e) {
				break;
			}
			if (emailList == null)
				continue;

			AdnRecord rec = mPhoneBookRecords.get(i);

			String[] emails = new String[emailList.size()];
			System.arraycopy(emailList.toArray(), 0, emails, 0, emailList
					.size());
			rec.setEmails(emails);
			mPhoneBookRecords.set(i, rec);
		}
	}

	private String getType2Email(int num, SubjectIndexOfAdn emailInfo,
			byte[] record, int andNum, int efid) {


		String emails = null;
	       int index = -1;
		log(" getType2Email >>  emailInfo.recordNumInIap.size() "
				+ emailInfo.recordNumInIap.size() + " adnNum " + andNum + " efid " +efid);
		if(record == null){

                   return emails;
		}


		index = getUsedNumSetIndex(efid,emailInfo );


		if(index == -1){

                   return emails;
		}

		mEmailTagNumberInIap = emailInfo.recordNumInIap.get(efid);
            //log(" getType2Email mEmailTagNumberInIap "
		//		+ mEmailTagNumberInIap);
	      mEmailFileRecord = emailInfo.record.get(efid);
             //log("getType2Email size " +  mEmailFileRecord.size());
		if (mEmailFileRecord == null) {

	              return emails;
		}

		int recNum = (int) (record[mEmailTagNumberInIap] & 0xFF);
		recNum = ((recNum == 0xFF) ? (-1) : recNum);
		log("getType2Email  iap recNum == " + recNum);
		if (recNum != -1) {

			// SIM record numbers are 1 based
		    emails = readEmailRecord(recNum - 1);
		    log( "getType2Email,emails " + emails);
		    // set email
		    if (TextUtils.isEmpty(emails)) {

			  log("getType2Email,emails ==null");
			  setIapFileRecord(num, andNum, (byte) 0xFF, mEmailTagNumberInIap);

			  return null;
		    }
		    Set<Integer> set = (Set<Integer>) emailInfo.usedSet[index];
                 log("getType2Email  size (0)" +  set.size()  + " index " + index);
		    set.add(new Integer(recNum));
	  	    emailInfo.usedSet[index] = set;
		    log("getType2Email  size (1)" +  set.size());
		    setSubjectIndex(USIM_SUBJCET_EMAIL,num,emailInfo);
		}


		return emails;

	}
       private void CheckRepeatType2Ef(){

            ArrayList<Integer> efs = getType2Ef(USIM_SUBJCET_EMAIL);
	      int i = 0;
	      log("CheckRepeatType2Ef ");
	      for( i=0 ; i<efs.size(); i++){

                  SetMapOfRepeatEfid(USIM_SUBJCET_EMAIL,efs.get(i));

	      }

            efs = getType2Ef(USIM_SUBJCET_ANR);
	      for( i=0 ; i<efs.size(); i++){

                  SetMapOfRepeatEfid(USIM_SUBJCET_ANR,efs.get(i));

	      }

	}

       private  ArrayList<Integer>  getType2Ef(int type){

            ArrayList<Integer> efs = new ArrayList<Integer>();
	      LinkedList<SubjectIndexOfAdn> lst = null;
		SubjectIndexOfAdn index = null;
		boolean isAdd = false;
		switch (type) {

		case USIM_SUBJCET_EMAIL:

			lst = mEmailInfoFromPBR;
			break;

		case USIM_SUBJCET_ANR:

			lst = mAnrInfoFromPBR;
			break;
		default:
			break;
		}

            if (lst != null && lst.size() != 0) {
                   log("getType2Ef size " + lst.size() );
			for(int i = 0; i<lst.size() ; i++){

			     index = lst.get(i);

                        if(index != null && index.efids != null&& index.type != null ){


                             for(int j=0; j<index.efids.length; j++){

                                  if(index.type[j] == USIM_TYPE2_TAG ){
                                        isAdd = true;
						  for(int k=0; k<efs.size();k++){

                                             if(efs.get(k) == index.efids[j]){

                                                    isAdd = false;
							}

						  }

                                        if(isAdd){
                                             efs.add(index.efids[j]);
                                        }


		       		   }

				    }


                     }
		    }

            }
		log("getType2Ef  type "+ type + " efs " +efs );
		return efs;
      }

      	private void setUsedNumOfEfid(int type,int idx,  int efid, Object obj ) {

	      LinkedList<SubjectIndexOfAdn> lst = null;
		SubjectIndexOfAdn index = null;
		switch (type) {

		case USIM_SUBJCET_EMAIL:

			lst = mEmailInfoFromPBR;
			break;

		case USIM_SUBJCET_ANR:

			lst = mAnrInfoFromPBR;
			break;
		default:
			break;
		}

		if (lst != null && lst.size() != 0) {
                   log("setUsedNumOfEfid size " + lst.size() );
			for(int i = 0; i<lst.size() ; i++){

			     index = lst.get(i);

                        if(index != null && index.efids != null ){
                             for(int j=0; j<index.efids.length; j++){

                                 if(index.efids[j] == efid){

                                 index.usedSet[idx] =  obj;
								 setSubjectIndex(type,i,index);					
								 break;					
            				}
				   }
			   }
			}
	}}
	private void setSubjectUsedNum(int type, int num) {

		SubjectIndexOfAdn index = getSubjectIndex(type, num);

		log(" setSubjectUsedNum num " + num);

		if (index == null) {

			return;
		}
		int size = index.efids.length;
		log(" setSubjectUsedNum size " + size);

		index.usedSet = new Object[size];

		for (int i = 0; i < size; i++) {

			index.usedSet[i] = new HashSet<Integer>();
		}

		setSubjectIndex(type, num, index);

	}

	private String getType2Anr(int num, SubjectIndexOfAdn anrInfo,
			byte[] record, int adnNum, int efid) {

		String anr = "";
		int anrTagNumberInIap;
		ArrayList<byte[]> anrFileRecord;
		byte[] anrRec;

		int index = 0;
		//boolean isSet = false;
		log(" getType2Anr  >> anrInfo.recordNumInIap.size() "
				+ anrInfo.recordNumInIap.size() + "adnNum  " + adnNum);
		if(record == null){

                   return anr;
		}

        index = getUsedNumSetIndex( efid, anrInfo);
        if(index == -1){

               return anr;
		}


		anrTagNumberInIap = anrInfo.recordNumInIap.get(efid);

		anrFileRecord = anrInfo.record.get(efid);

		if (anrFileRecord == null) {

			return anr;
		}
             log(" getType2Anr anrTagNumberInIap"
				+ anrTagNumberInIap);
		int recNum = (int) (record[anrTagNumberInIap] & 0xFF);
		recNum = ((recNum == 0xFF) ? (-1) : recNum);
		log(" getType2Anr iap recNum == " + recNum);

		if (recNum != -1) {

			anrRec = anrFileRecord.get(recNum -1);
			anr = PhoneNumberUtils.calledPartyBCDToString(anrRec, 2,
						(0xff & anrRec[2]));
			log("getAnrInIap anr:" + anr);
				// SIM record numbers are 1 based
		      if(TextUtils.isEmpty(anr)){
                         log("getAnrInIap anr is emtry");
                         setIapFileRecord(num, adnNum, (byte) 0xFF, anrTagNumberInIap);
				return anr;
			}

			Set<Integer> set = (Set<Integer>) anrInfo.usedSet[index];
			set.add(new Integer(recNum));
			anrInfo.usedSet[index] = set;
			setSubjectIndex(USIM_SUBJCET_ANR,num,anrInfo);

		}


             log( "getType2Anr  >>>>>>>>>>>> anr " + anr);
		return anr;

	}

	private String getType1Email(int num, SubjectIndexOfAdn emailInfo,
			int adnNum, int efid) {


		String emails = null;

		mEmailFileRecord = emailInfo.record.get(efid);
		log("getType1Email size " +  mEmailFileRecord.size());

		if (mEmailFileRecord == null) {

			return null;
		}

		emails = readEmailRecord(adnNum);
		log( "getType1Email,emails " + emails);

		if (TextUtils.isEmpty(emails)) {

			log("getType1Email,emails==null");

			return null;

		}

		return emails;

	}

	private String getType1Anr(int num, SubjectIndexOfAdn anrInfo, int adnNum, int efid) {

		String anr = "";
		int anrTagNumberInIap;
		ArrayList<byte[]> anrFileRecord;
		byte[] anrRec;
		anrFileRecord = anrInfo.record.get(efid);

		if (anrFileRecord == null) {

			return anr;
		}

		if (adnNum < anrFileRecord.size()) {
			anrRec = anrFileRecord.get(adnNum);
			anr = PhoneNumberUtils.calledPartyBCDToString(anrRec, 2,
						(0xff & anrRec[2]));

		} else {

			anr = "";
		}
			// SIM record numbers are 1 based



		return anr;

	}

	private void setEmailandAnr(int adnNum, String[] emails, String anr) {

		AdnRecord rec = mPhoneBookRecords.get(adnNum);

		log( "updatePhoneAdnRecord,rec name:" + rec.getAlphaTag()
				+ "num " + rec.getNumber() + " adnNum " + adnNum);

		if (rec == null && (emails != null || anr != null)) {

			rec = new AdnRecord("", "");
		}

		if (emails != null) {
			rec.setEmails(emails);

			log( "updatePhoneAdnRecord AdnRecord  emails"
					+ emails[0]);
		}
		if (anr != null) {
			log( "updatePhoneAdnRecord AdnRecord  anr"
					+ anr);
			rec.setAnr(anr);
		}
		mPhoneBookRecords.set(adnNum, rec);

	}

	private void setAnrIapFileRecord(int num, int index, byte value,
			int numInIap) {
		log("setAnrIapFileRecord >> num:" + num + "index: "
				+ index + "value: " + value + " numInIap:" + numInIap);
		ArrayList<byte[]> tmpIapFileRecord = (ArrayList<byte[]>) mIapFileRecordArray[num];
		byte[] record = tmpIapFileRecord.get(index);
		record[numInIap] = value;
		tmpIapFileRecord.set(index, record);
		mIapFileRecordArray[num] = tmpIapFileRecord;

	}

	public boolean isSubjectRecordInIap(int type, int num, int indexOfEfids) {

		SubjectIndexOfAdn index = getSubjectIndex(type, num);

		if (index == null) {

			return false;

		}
		if (index.type[indexOfEfids] == USIM_TYPE2_TAG && index.recordNumInIap.size() > 0) {

			return true;
		} else if (index.type[indexOfEfids] == USIM_TYPE1_TAG) {

			return false;

		}

		return false;

	}
	private String  getAnr(int num, SubjectIndexOfAdn anrInfo, byte[] record,int adnNum){

             log( "getAnr adnNum: " + adnNum + "num " + num);

             String anrGroup = null;
		String anr = null;

		if(anrInfo.efids == null ||anrInfo.efids.length == 0){

			log( "getAnr anrInfo.efids == null ||anrInfo.efids.length == 0 ");
                   return null;
		}


             for (int i = 0; i < anrInfo.efids.length; i++) {

                   if(anrInfo.type[i] == USIM_TYPE1_TAG){

                         anr = getType1Anr( num,  anrInfo,  adnNum,  anrInfo.efids[i]);

			}

			if(anrInfo.type[i] == USIM_TYPE2_TAG && anrInfo.recordNumInIap != null){

                         anr = getType2Anr( num,  anrInfo, record,  adnNum, anrInfo.efids[i]);

			}
            if (i == 0) {
				anrGroup = anr;
			} else {
				anrGroup = anrGroup + AdnRecord.ANR_SPLIT_FLG + anr;
			}


             }

	       return anrGroup;
	}

      private  String[] getEmail(int num, SubjectIndexOfAdn emailInfo,
			byte[] record, int adnNum){
			 

             log( "getEmail adnNum: " + adnNum + "num " + num);


             String[] emails = null;
		boolean isEmpty = true;

		if(emailInfo.efids == null ||emailInfo.efids.length == 0){

			log( "getEmail emailInfo.efids == null ||emailInfo.efids.length == 0 ");
            return null;
		}

		emails = new String[emailInfo.efids.length];

             for (int i = 0; i < emailInfo.efids.length; i++) {

                   if(emailInfo.type[i] == USIM_TYPE1_TAG){

                         emails[i] = getType1Email( num,  emailInfo,  adnNum,  emailInfo.efids[i]);

			}

			if(emailInfo.type[i] == USIM_TYPE2_TAG && emailInfo.recordNumInIap != null){

                         emails[i] = getType2Email( num,  emailInfo, record,  adnNum,  emailInfo.efids[i]);

			}

             }

		for(int i=0; i< emails.length; i++){

		     if(!TextUtils.isEmpty(emails[i])){

                        isEmpty = false;
		     }

		}

		if(isEmpty){

                  return null;
		}

	       return emails;

     }

	private void updateAdnRecord(int num) {

		SubjectIndexOfAdn emailInfo = null;
		int emailType = 0;
		String[] emails = null;
		SubjectIndexOfAdn anrInfo = null;
		int anrType = 0;
		String anr = null;
		int numAdnRecs = mPhoneBookRecords.size();

		mAdnRecordSizeArray[num] = mPhoneBookRecords.size();
		log( "updateAdnRecord mAdnRecordSizeArray[num] : "
				+ numAdnRecs + "num " + num);

		int numIapRec = 0;
		int efid = 0;
            	byte[] record = null;

		emailInfo = getSubjectIndex(USIM_SUBJCET_EMAIL, num);

		anrInfo = getSubjectIndex(USIM_SUBJCET_ANR, num);



		if (mIapFileRecord != null) {
			// The number of records in the IAP file is same as the number of
			// records in ADN file.
			// The order of the pointers in an EFIAP shall be the same as the
			// order of file IDs
			// that appear in the TLV object indicated by Tag 'A9' in the
			// reference file record.
			// i.e value of mEmailTagNumberInIap

			numIapRec = mIapFileRecord.size();
			Log.i(LOG_TAG, "updatePhoneAdnRecord mIapRecordSizeArray[num] : "
					+ mIapFileRecord.size());

			mIapFileRecordArray[num] = mIapFileRecord;
			mIapRecordSizeArray[num] = mIapFileRecord.size();

			log("updatePhoneAdnRecord,numIapRec  " + numIapRec);

		}

 		numIapRec = ((numAdnRecs-mDoneAdnCount) > numIapRec) ? numIapRec : (numAdnRecs -mDoneAdnCount) ;

             log("updatePhoneAdnRecord,numIapRec  " + numIapRec + " mDoneAdnCount " + mDoneAdnCount);
		for (int i = mDoneAdnCount; i < (mDoneAdnCount+numIapRec); i++) {

                  record = null;

                  if(mIapFileRecord != null){

				try {
					record = mIapFileRecord.get((i-mDoneAdnCount));

				} catch (IndexOutOfBoundsException e) {

					Log.e(LOG_TAG,"Error: Improper ICC card: No IAP record for ADN, continuing");

             		}



			}
			if(emailInfo != null){
       		      emails = getEmail(num, emailInfo, record, (i-mDoneAdnCount));
	        		setEmailandAnr(i, emails, null);
			}
		      if(anrInfo !=null){
			     anr = getAnr(num, anrInfo, record, (i-mDoneAdnCount));
			     setEmailandAnr(i, null, anr);
		      	}

		}

		mIapFileRecord = null;

		mDoneAdnCount += numAdnRecs;
	}



	void parseType1EmailFile(int numRecs) {
		mEmailsForAdnRec = new HashMap<Integer, ArrayList<String>>();
		byte[] emailRec = null;
		Log.i(LOG_TAG, "parseType1EmailFile  numRecs " + numRecs);
		for (int i = 0; i < numRecs; i++) {
			try {
				emailRec = mEmailFileRecord.get(i);
			} catch (IndexOutOfBoundsException e) {
				Log
						.e(LOG_TAG,
								"Error: Improper ICC card: No email record for ADN, continuing");
				break;
			}
			int adnRecNum = i + 1;// emailRec[emailRec.length - 1];

			if (adnRecNum == -1) {
				continue;
			}

			String email = readEmailRecord(i);

			if (email == null || email.equals("")) {
				continue;
			}

			// SIM record numbers are 1 based.
			ArrayList<String> val = mEmailsForAdnRec.get(adnRecNum - 1);
			if (val == null) {
				val = new ArrayList<String>();
			}
			val.add(email);
			// SIM record numbers are 1 based.

			mEmailsForAdnRec.put(adnRecNum - 1, val);
		}
	}

	private String readEmailRecord(int recNum) {
		byte[] emailRec = null;
		try {
			emailRec = mEmailFileRecord.get(recNum);
		} catch (IndexOutOfBoundsException e) {
			return null;
		}

		// The length of the record is X+2 byte, where X bytes is the email
		// address
		String email = IccUtils.adnStringFieldToString(emailRec, 0,
				emailRec.length - 2);
		return email;
	}

	private String readAnrRecord(int recNum) {
		byte[] anr1Rec = null;
		byte[] anr2Rec = null;
		byte[] anr3Rec = null;
		String anr1 = null;
		String anr2 = null;
		String anr3 = null;
		String anr = null;

		int firstAnrFileRecordCount = mAnrRecordSizeArray[0] / anrFileCount;
		// log("firstAnrFileRecordCount is "+firstAnrFileRecordCount);
		if (anrFileCount == 0x1) {
			anr1Rec = mAnrFileRecord.get(recNum);
			anr = PhoneNumberUtils.calledPartyBCDToString(anr1Rec, 2,
					(0xff & anr1Rec[2]));
			log("readAnrRecord anr:" + anr);
			return anr;
		} else {
			if (recNum < firstAnrFileRecordCount) {
				try {
					anr1Rec = mAnrFileRecord.get(recNum);
					anr2Rec = mAnrFileRecord.get(recNum
							+ firstAnrFileRecordCount);
					if (anrFileCount > 0x2) {
						anr3Rec = mAnrFileRecord.get(recNum + 2
								* firstAnrFileRecordCount);
					}

				} catch (IndexOutOfBoundsException e) {
					return null;
				}

				anr1 = PhoneNumberUtils.calledPartyBCDToString(anr1Rec, 2,
						(0xff & anr1Rec[2]));
				anr2 = PhoneNumberUtils.calledPartyBCDToString(anr2Rec, 2,
						(0xff & anr2Rec[2]));
				if (anrFileCount > 0x2) {
					anr3 = PhoneNumberUtils.calledPartyBCDToString(anr3Rec, 2,
							(0xff & anr3Rec[2]));
					anr = anr1 + AdnRecord.ANR_SPLIT_FLG + anr2 + AdnRecord.ANR_SPLIT_FLG + anr3;
					log("readAnrRecord anr:" + anr);
					return anr;
				}
			} else if (recNum >= firstAnrFileRecordCount
					&& recNum < mAnrFileRecord.size() / anrFileCount) {
				int secondAnrFileRecordCount = (mAnrFileRecord.size() - mAnrRecordSizeArray[0])
						/ anrFileCount;
				// log("secondAnrFileRecordCount is "+secondAnrFileRecordCount);
				try {
					int secondAnrfileread = mAnrRecordSizeArray[0] + recNum
							% firstAnrFileRecordCount;
					anr1Rec = mAnrFileRecord.get(secondAnrfileread);
					anr2Rec = mAnrFileRecord.get(secondAnrfileread
							+ secondAnrFileRecordCount);
					if (anrFileCount > 0x2) {
						anr3Rec = mAnrFileRecord.get(secondAnrfileread + 2
								* secondAnrFileRecordCount);
					}
				} catch (IndexOutOfBoundsException e) {
					return null;
				}
				anr1 = PhoneNumberUtils.calledPartyBCDToString(anr1Rec, 2,
						(0xff & anr1Rec[2]));
				anr2 = PhoneNumberUtils.calledPartyBCDToString(anr2Rec, 2,
						(0xff & anr2Rec[2]));
				if (anrFileCount > 0x2) {
					anr3 = PhoneNumberUtils.calledPartyBCDToString(anr3Rec, 2,
							(0xff & anr3Rec[2]));
					anr = anr1 + AdnRecord.ANR_SPLIT_FLG + anr2 + AdnRecord.ANR_SPLIT_FLG + anr3;
					log("readAnrRecord anr:" + anr);
					return anr;
				}
			} else {
				log("the total anr size is exceed mAnrFileRecord.size()  "
						+ mAnrFileRecord.size());
			}
			anr = anr1 + AdnRecord.ANR_SPLIT_FLG + anr2;
			log("readAnrRecord anr:" + anr);
			return anr;
		}
	}

	private void createPbrFile(ArrayList<byte[]> records) {
		if (records == null) {
			mPbrFile = null;
			mIsPbrPresent = false;
			return;
		}
		mPbrFile = new PbrFile(records);
	}

	@Override
	public void handleMessage(Message msg) {
		AsyncResult ar;
		byte data[];
		switch (msg.what) {
		case EVENT_PBR_LOAD_DONE:
			ar = (AsyncResult) msg.obj;
			if (ar.exception == null) {
				createPbrFile((ArrayList<byte[]>) ar.result);
			}
			synchronized (mLock) {
				mLock.notify();
			}
			break;
		case EVENT_USIM_ADN_LOAD_DONE:
			log("Loading USIM ADN records done");
			ar = (AsyncResult) msg.obj;
			// add by liguxiang 10-24-11 for NEWMS00132125 begin
			// int size = ((ArrayList<AdnRecord>) ar.result).size();
			int size = 0;
			if ((ar != null) && ((ArrayList<AdnRecord>) ar.result != null)) {
				size = ((ArrayList<AdnRecord>) ar.result).size();
			}
			// add by liguxiang 10-24-11 for NEWMS00132125 end
			log("EVENT_USIM_ADN_LOAD_DONE size" + size);

			if (ar.exception == null) {
				mPhoneBookRecords.addAll((ArrayList<AdnRecord>) ar.result);
			}
			synchronized (mLock) {
				mLock.notify();
			}
			break;
		case EVENT_IAP_LOAD_DONE:
			log("Loading USIM IAP records done");
			ar = (AsyncResult) msg.obj;
			if (ar.exception == null) {
				if (mIapFileRecord == null) {
					mIapFileRecord = new ArrayList<byte[]>();
				}
				mIapFileRecord.addAll((ArrayList<byte[]>) ar.result);
			}
			synchronized (mLock) {
				mLock.notify();
			}
			break;

		case EVENT_ANR_LOAD_DONE:
			log("Loading USIM ANR records done");
			ar = (AsyncResult) msg.obj;
			if (ar.exception == null) {
				if (mAnrFileRecord == null) {
					mAnrFileRecord = new ArrayList<byte[]>();
				}
				if (mTempAnrFileRecord == null) {

					mTempAnrFileRecord = new ArrayList<byte[]>();
				}
				mAnrFileRecord.addAll((ArrayList<byte[]>) ar.result);
				mTempAnrFileRecord.addAll((ArrayList<byte[]>) ar.result);
				log("mAnrFileRecord.size() is " + mAnrFileRecord.size());
			}

			synchronized (mLock) {
				mLock.notify();
			}
			break;

		case EVENT_EMAIL_LOAD_DONE:
			log("Loading USIM Email records done");
			ar = (AsyncResult) msg.obj;
			if (ar.exception == null) {
				//if (mEmailFileRecord == null) {
				mEmailFileRecord = new ArrayList<byte[]>();
				//}
				mEmailFileRecord.addAll((ArrayList<byte[]>) ar.result);
				log("Loading USIM Email records done size "+ mEmailFileRecord.size());
			}

			synchronized (mLock) {
				mLock.notify();
			}
			break;
		case EVENT_ADN_RECORD_COUNT:
			Log.i(LOG_TAG, "Loading EVENT_ADN_RECORD_COUNT");
			ar = (AsyncResult) msg.obj;
			synchronized (mLock) {
				if (ar.exception == null) {
					recordSize = (int[]) ar.result;
					// recordSize[0] is the record length
					// recordSize[1] is the total length of the EF file
					// recordSize[2] is the number of records in the EF file
					Log.i(LOG_TAG, "EVENT_ADN_RECORD_COUNT Size "
							+ recordSize[0] + " total " + recordSize[1]
							+ " #record " + recordSize[2]);
			}
				mLock.notify();
			}
			break;
		case EVENT_LOAD_EF_PBC_RECORD_DONE:
		    Log.i(LOG_TAG, "Loading EVENT_LOAD_EF_PBC_RECORD_DONE");
		    ar = (AsyncResult)(msg.obj);
		    if (ar.exception == null) {
                //if (mEmailFileRecord == null) {
                mPbcFileRecord = new ArrayList<byte[]>();
                //}
                mPbcFileRecord.addAll((ArrayList<byte[]>) ar.result);
                log("Loading USIM PBC records done size "+ mPbcFileRecord.size());
            }
		    synchronized (mLock) {
                mLock.notify();
            }
		    break;
        case EVENT_EF_CC_LOAD_DONE:
               ar = (AsyncResult)(msg.obj);
                data = (byte[])(ar.result);
                int temp = (Integer)(ar.userObj);
                if (ar.exception != null) {
                    Log.i(LOG_TAG,"EVENT_EF_CC_LOAD_DONE has exception " + ar.exception);
                    throw new RuntimeException("load failed", ar.exception);
                }
                Log.i(LOG_TAG,"EVENT_EF_CC_LOAD_DONE "+ IccUtils.bytesToHexString(data));
                // update EFcc
                byte[] counter = new byte[2];
                int cc = (data[0]<<8)+data[1];
                cc+=temp;
               if (cc > 0xFFFF) {
                   counter[0] = (byte) 0x00;
                    counter[1] = (byte) 0x01;
               }else{
                   counter[0] = (byte)(cc>>8 & 0xFF);
                    counter[1] = (byte)(cc & 0xFF);

               }
               Log.i(LOG_TAG,"EVENT_EF_CC_LOAD_DONE "+ IccUtils.bytesToHexString(counter));
                mFh.updateEFTransparent(IccConstants.EF_CC, counter, obtainMessage(EVENT_UPDATE_RECORD_DONE));
               break;
           case EVENT_UPDATE_RECORD_DONE:
                ar = (AsyncResult) (msg.obj);
               if (ar.exception != null) {
                   throw new RuntimeException("update EF records failed",
                           ar.exception);
                }
                Log.i(LOG_TAG,"update_record_success");
               break;
	    }
	}

	public class PbrFile {
		// RecNum <EF Tag, efid>
		HashMap<Integer, Map<Integer, Integer>> mFileIds;
		public ArrayList<SimTlv> tlvList;

		PbrFile(ArrayList<byte[]> records) {
			mFileIds = new HashMap<Integer, Map<Integer, Integer>>();
			tlvList = new ArrayList<SimTlv>();
			SimTlv recTlv;
			int recNum = 0;

			for (byte[] record : records) {
				log("before making TLVs, data is "
						+ IccUtils.bytesToHexString(record));
				if (IccUtils.bytesToHexString(record).startsWith("ffff")) {
					continue;
				}
				recTlv = new SimTlv(record, 0, record.length);
				parsePBRData(record);
				parseTag(recTlv, recNum);
				recNum++;
			}
		}

		public ArrayList<Integer> getFileId(int recordNum, int fileTag) {
			ArrayList<Integer> ints = new ArrayList<Integer>();

			try {

				SimTlv recordTlv = tlvList.get(recordNum * tlvList.size() / 2); // tlvList.size()
				// =6

				SimTlv subTlv = new SimTlv(recordTlv.getData(), 0, recordTlv
						.getData().length);
				for (; subTlv.isValidObject();) {


					if (subTlv.getTag() == fileTag) {
						// get the file tag
						int i = subTlv.getData()[0] << 8;
						ints.add(i + (int) (subTlv.getData()[1] & 0xff));

					}
					//if (!subTlv.nextObject())
						//return ints;
				}
			} catch (IndexOutOfBoundsException ex) {
				log("IndexOutOfBoundsException: " + ex);
				return ints;
			}
			return ints;
		}

		public ArrayList<Integer> getFileIdsByTagAdn(int tag, int ADNid) {
			log("enter getFileIdsByTagAdn.");
			ArrayList<Integer> ints = new ArrayList<Integer>();
			boolean adnBegin = false;
			for (int i = 0, size = tlvList.size(); i < size; i++) {
				SimTlv recordTlv = tlvList.get(i);
				SimTlv subTlv = new SimTlv(recordTlv.getData(), 0, recordTlv
						.getData().length);
				do {
					if (subTlv.getData().length <= 2)
						continue;
					int x = subTlv.getData()[0] << 8;
					x += (int) (subTlv.getData()[1] & 0xff);
					if (subTlv.getTag() == UsimPhoneBookManager.USIM_EFADN_TAG) {
						if (x == ADNid)
							adnBegin = true;
						else
							adnBegin = false;
					}
					if (adnBegin) {
						if (subTlv.getTag() == tag) {
							if (subTlv.getData().length < 2)
								continue;
							int y = subTlv.getData()[0] << 8;
							ints.add(y + (int) (subTlv.getData()[1] & 0xff));
						}
					}
				} while (subTlv.nextObject());
			}
			return ints;
		}

		void parseTag(SimTlv tlv, int recNum) {
			SimTlv tlvEf;
			int tag;
			byte[] data;
			ArrayList<Integer> emailEfs = new  ArrayList<Integer>();
			ArrayList<Integer> anrEfs = new  ArrayList<Integer>();
			ArrayList<Integer> emailType = new  ArrayList<Integer>();
			ArrayList<Integer> anrType = new  ArrayList<Integer>();
			int i =0;
			Map<Integer, Integer> val = new HashMap<Integer, Integer>();
			SubjectIndexOfAdn emailInfo = new SubjectIndexOfAdn();

			emailInfo.recordNumInIap = new HashMap<Integer, Integer>();

			SubjectIndexOfAdn anrInfo = new SubjectIndexOfAdn();

			anrInfo.recordNumInIap = new HashMap<Integer, Integer>();

			do {
				tag = tlv.getTag();
				switch (tag) {
				case USIM_TYPE1_TAG: // A8
				case USIM_TYPE3_TAG: // AA
				case USIM_TYPE2_TAG: // A9
					data = tlv.getData();
					tlvEf = new SimTlv(data, 0, data.length);
					parseEf(tlvEf, val, tag,emailInfo,anrInfo,emailEfs,anrEfs,emailType,anrType);
					break;
				}
			} while (tlv.nextObject());

		      	if(emailEfs.size() > 0){

                         emailInfo.efids = new int[emailEfs.size()];
       		      emailInfo.type = new int[emailEfs.size()];

			      for(i=0;i<emailEfs.size();i++){

                              emailInfo.efids[i] = emailEfs.get(i);
                              emailInfo.type[i] = emailType.get(i);
		            }
        		      log("parseTag email ef " +emailEfs + " types " +emailType );
			}
			 if(anrEfs.size() > 0){
					
                         anrInfo.efids = new int[anrEfs.size()];
    			      anrInfo.type = new int[anrEfs.size()];

			      for(i=0;i<anrEfs.size();i++){
                 
                              anrInfo.efids[i] = anrEfs.get(i);
              		    anrInfo.type[i] = anrType.get(i);

		            }
			      log("parseTag anr ef " +anrEfs + " types " + anrType);
			}
			if(mPhoneBookRecords != null && mPhoneBookRecords.isEmpty()){
			    if(mAnrInfoFromPBR != null ){
			         mAnrInfoFromPBR.add(anrInfo);
			    }
			    if(mEmailInfoFromPBR != null){
			         mEmailInfoFromPBR.add(emailInfo);
			     }
			}

			mFileIds.put(recNum, val);

		}

		void parseEf(SimTlv tlv, Map<Integer, Integer> val, int parentTag,
			SubjectIndexOfAdn emailInfo,SubjectIndexOfAdn anrInfo, ArrayList<Integer> emailEFS,ArrayList<Integer> anrEFS,ArrayList<Integer> emailTypes,
			ArrayList<Integer>  anrTypes) {
			int tag;
			byte[] data;
			int tagNumberWithinParentTag = 0;

			do {
				tag = tlv.getTag();

				switch (tag) {
				case USIM_EFEMAIL_TAG:
				case USIM_EFADN_TAG:
				case USIM_EFEXT1_TAG:
				case USIM_EFANR_TAG:
				case USIM_EFPBC_TAG:
				case USIM_EFGRP_TAG:
				case USIM_EFAAS_TAG:
				case USIM_EFGAS_TAG:
				case USIM_EFUID_TAG:
				case USIM_EFCCP1_TAG:
				case USIM_EFIAP_TAG:
				case USIM_EFSNE_TAG:
					data = tlv.getData();

					int efid = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                    mPhone.getIccFileHandler().addDualMapFile(efid);

					if(tag == USIM_EFADN_TAG ){

						emailInfo.adnEfid = efid;
						anrInfo.adnEfid = efid;

					}

					if (parentTag == USIM_TYPE2_TAG && tag == USIM_EFEMAIL_TAG) {
						mEmailPresentInIap = true;
						mEmailTagNumberInIap = tagNumberWithinParentTag;

					}
					if (tag == USIM_EFEMAIL_TAG) {

						Log.i(LOG_TAG, "parseEf   email  efid " +efid +"  TAG  " + parentTag + "tagNumberWithinParentTag " +tagNumberWithinParentTag);
						if (parentTag == USIM_TYPE2_TAG) {
							emailInfo.recordNumInIap.put(efid,
									tagNumberWithinParentTag);

						}

						emailEFS.add(efid);
						emailTypes.add(parentTag);
					}

					if (tag == USIM_EFANR_TAG) {


						log("parseEf   ANR  efid " +efid +" TAG  " + parentTag + "tagNumberWithinParentTag " +tagNumberWithinParentTag);
						if (parentTag == USIM_TYPE2_TAG) {

							mAnrPresentInIap = true;
							anrInfo.recordNumInIap.put(efid,
									tagNumberWithinParentTag);

						}

						anrEFS.add(efid);
						anrTypes.add(parentTag);

					}
					//Log.i(LOG_TAG, "parseTag tag " +tag +" efid   "+ efid);
					val.put(tag, efid);
					break;
				}
				tagNumberWithinParentTag++;
			} while (tlv.nextObject());


		}
		void parseEf(SimTlv tlv, Map<Integer, Integer> val, int parentTag) {
			int tag;
			byte[] data;
			int tagNumberWithinParentTag = 0;
			boolean hasEmail = false;
			boolean hasAnr = false;
			SubjectIndexOfAdn emailInfo = new SubjectIndexOfAdn();

			emailInfo.recordNumInIap = new HashMap<Integer, Integer>();

			SubjectIndexOfAdn anrInfo = new SubjectIndexOfAdn();

			anrInfo.recordNumInIap = new HashMap<Integer, Integer>();


			do {
				tag = tlv.getTag();

				switch (tag) {
				case USIM_EFEMAIL_TAG:
				case USIM_EFADN_TAG:
				case USIM_EFEXT1_TAG:
				case USIM_EFANR_TAG:
				case USIM_EFPBC_TAG:
				case USIM_EFGRP_TAG:
				case USIM_EFAAS_TAG:
				case USIM_EFGAS_TAG:
				case USIM_EFUID_TAG:
				case USIM_EFCCP1_TAG:
				case USIM_EFIAP_TAG:
				case USIM_EFSNE_TAG:
					data = tlv.getData();

					int efid = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);

					if (parentTag == USIM_TYPE2_TAG && tag == USIM_EFEMAIL_TAG) {
						mEmailPresentInIap = true;
						mEmailTagNumberInIap = tagNumberWithinParentTag;

					}
					if (tag == USIM_EFEMAIL_TAG) {
						hasEmail = true;
						Log.i(LOG_TAG, "parseEf   email  TAG  " + parentTag);
						if (parentTag == USIM_TYPE2_TAG) {
							emailInfo.recordNumInIap.put(efid,
									tagNumberWithinParentTag);

						}
					}

					if (tag == USIM_EFANR_TAG) {

						hasAnr = true;

						Log.i(LOG_TAG, "parseEf   ANR  TAG  " + parentTag);
						if (parentTag == USIM_TYPE2_TAG) {

							mAnrPresentInIap = true;
							anrInfo.recordNumInIap.put(efid,
									tagNumberWithinParentTag);

						}

					}
					Log.i(LOG_TAG, "parseTag tag " +tag +" efid   "+ efid);
					val.put(tag, efid);
					break;
				}
				tagNumberWithinParentTag++;
			} while (tlv.nextObject());

			Log.i(LOG_TAG, "parseTag hasAnr " + hasAnr);
			if (hasAnr) {
				mAnrInfoFromPBR.add(anrInfo);

			}

			if (hasEmail) {

				mEmailInfoFromPBR.add(emailInfo);
			}
		}

		void parsePBRData(byte[] data) {

			SimTlv tlv;
			int totalLength = 0;
			int validLength = getValidData(data);

			do {
				tlv = new SimTlv(data, totalLength, validLength);

				totalLength += tlv.getData().length + 2;

				addRecord(tlv);
			} while (totalLength < validLength);
		}

		int getValidData(byte[] data) {
			for (int i = 0; i < data.length; i++) {
				if ((data[i] & 0xff) == 0xff)
					return i;
			}
			return data.length;
		}

		void addRecord(SimTlv tlv) {
			log("addRecord " );
			tlvList.add(tlv);
		}

	}

	private void log(String msg) {
		if (DBG)
			Log.d(LOG_TAG, msg);
	}

}
