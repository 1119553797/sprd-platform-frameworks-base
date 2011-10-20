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
	// add end
	private ArrayList<byte[]> mEmailFileRecord;
	private Map<Integer, ArrayList<String>> mEmailsForAdnRec;
	private int mAdnCount = 0;
	private int mAdnSize = 0;
	protected int recordSize[] = new int[3];

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
	private int mDoneAdnCount = 0;
	private int mAnrNum = 0;
	private LinkedList<SubjectIndexOfAdn> mAnrInfoFromPBR = null;
	

	public class SubjectIndexOfAdn {

		public int[] efids;
		public Map<Integer, ArrayList<byte[]>> record;

	};

	public static final int USIM_TYPE1_TAG = 0xA8;
	public  static final int USIM_TYPE2_TAG = 0xA9;
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
	private static final int USIM_EFEMAIL_TAG = 0xCA;
	private static final int USIM_EFCCP1_TAG = 0xCB;

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
		// add end
		mLock = cache.getLock();
	}

	public void reset() {
		mPhoneBookRecords.clear();
		mIapFileRecord = null;
		mEmailFileRecord = null;
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
		usedEmailNumSet = null;
		// add end
	}

	public ArrayList<AdnRecord> loadEfFilesFromUsim() {
		synchronized (mLock) {
			if (!mPhoneBookRecords.isEmpty())
				return mPhoneBookRecords;
			if (!mIsPbrPresent)
				return null;
			mAnrInfoFromPBR = new LinkedList<SubjectIndexOfAdn>();
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
			usedEmailNumSet = new HashSet<Integer>();
			mAdnRecordSizeArray = new int[numRecs];
			mEmailRecordSizeArray = new int[numRecs];
			mIapRecordSizeArray = new int[numRecs];
			mAnrRecordSizeArray = new int[numRecs];
			mAasRecordSizeArray = new int[numRecs];
			mSneRecordSizeArray = new int[numRecs];
			mGrpRecordSizeArray = new int[numRecs];
			mGasRecordSizeArray = new int[numRecs];

			// add end

			mAdnCount = 0;
			mDoneAdnCount = 0;
			Log.i("UsimPhoneBookManager", "loadEfFilesFromUsim" + numRecs);
			for (int i = 0; i < numRecs; i++) {
				readAdnFileAndWait(i);
				readEmailFileAndWait(i);

				// add begin for add multi record and email in usim
				readAnrFileAndWait(i);

				updatePhoneAdnRecord(i);
				// add end
			}
			// All EF files are loaded, post the response.
		}
		return mPhoneBookRecords;
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
		if (getNumRecs() == 0) {

			return 0;
		}

		Log.i("UsimPhoneBookManager", "getNumRecs " + getNumRecs() + "mAnrNum "
				+ mAnrNum);
		num = mAnrNum / getNumRecs();

		return num;

	}

	public int getEmailNum() {

		if (ishaveEmail) {
			Log.i("UsimPhoneBookManager", "ishaveEmail " + ishaveEmail);
			return 1;
		} else {

			return 0;
		}

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

	public void setIapFileRecord(int recNum, int index, byte value) {

		ArrayList<byte[]> tmpIapFileRecord = (ArrayList<byte[]>) mIapFileRecordArray[recNum];
		byte[] record = tmpIapFileRecord.get(index);
		record[mEmailTagNumberInIap] = value;
		tmpIapFileRecord.set(index, record);
		mIapFileRecordArray[recNum] = tmpIapFileRecord;

	}

	public int getEmailTagNumberInIap() {

		return mEmailTagNumberInIap;
	}

      public int     getEmailType(){

		if(ishaveEmail ){
			
                  if(mEmailPresentInIap)  {
				  	
		           return  USIM_TYPE2_TAG;		    	
  		     }else{
  
                         return   USIM_TYPE1_TAG;
		     }  

		}

	       return 0;

	}   
	  	
	public int getNewEmailNumber() {
		int newEmailNum = -1;
		int numEmailRec = 0;
		try {
			numEmailRec = mEmailRecordSizeArray[getNumRecs() - 1];
			Log.i(LOG_TAG, "getNewEmailNumber numEmailRec " + numEmailRec);

		} catch (IndexOutOfBoundsException e) {
			Log.e(LOG_TAG, "Error: Improper ICC card");
		}
		for (int i = 1; i <= numEmailRec; i++) {
			Integer emailNum = new Integer(i);
			Log.i(LOG_TAG, "getNewEmailNumber  emailNum (0)" + emailNum);
			if (!usedEmailNumSet.contains(emailNum)) {
				newEmailNum = (int) emailNum;

				Log.i(LOG_TAG, "getNewEmailNumber  emailNum(1) " + emailNum);
				usedEmailNumSet.add(emailNum);
				break;
			}
		}
		return newEmailNum;

	}

	public int getNewAnrNumber(int num, int efid, int index, int adnNum,
			boolean isInIap) {

		int newAnrNum = -1;
		int count = 0;
		Log.i(LOG_TAG, "getNewAnrNumber  num " + num + "adnNum " + adnNum
				+ "efid " + efid);
		if (mAnrInfoFromPBR == null) {

			return newAnrNum;
		}

		SubjectIndexOfAdn anrInfo = null;

		anrInfo = mAnrInfoFromPBR.get(num);

		if (anrInfo != null) {

			Map<Integer, ArrayList<byte[]>> record = null;

			record = anrInfo.record;

			if (record != null) {

				ArrayList<byte[]> recs = null;

				recs = record.get(efid);

				if (recs != null) {
					count = recs.size();
				}
			}

		}

		Log.i(LOG_TAG, "getNewAnrNumber  count " + count + "adnNum " + adnNum);

		if (adnNum > count) {

			return newAnrNum;
		} else {

			return adnNum;
		}

	}

	public ArrayList<Integer> getAnrFileIdsByTagAdn(int num) {

		ArrayList<Integer> efids = null;
		int i = 0;

		int count = 0;
		Log.i(LOG_TAG, "getAnrFileIdsByTagAdn  num " + num);
		if (mAnrInfoFromPBR == null) {

			return null;
		}
		SubjectIndexOfAdn record = null;

		record = mAnrInfoFromPBR.get(num);

		if (record != null) {

			efids = new ArrayList<Integer>();
			int[] efidsArray = record.efids;

			for (i = 0; i < efidsArray.length; i++) {

				efids.add(record.efids[i]);
			}

		}
		Log.i(LOG_TAG, "getAnrFileIdsByTagAdn  efids " + efids);
		return efids;

	}

	public int[] getEmailRecordSizeArray() {
		return mEmailRecordSizeArray;
	}

	public int[] getIapRecordSizeArray() {
		return mIapRecordSizeArray;

	}

	public void removeEmailNumFromSet(int recNum) {
		Integer emailNum = new Integer(recNum);
		usedEmailNumSet.remove(emailNum);
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

		if(fileIds  == null){
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

		if(fileIds == null){
                     Log.e(LOG_TAG, "Error: fileIds is empty");
                    return -1;
	       }

		Log.i(LOG_TAG, "findExtensionEFInfo fileIds " +  fileIds);

	      if (fileIds.containsKey(USIM_EFEXT1_TAG)) {
		  	
			return  fileIds.get(USIM_EFEXT1_TAG);
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
			Log.i(LOG_TAG,
					"findEFEmailInfo  fileIds == null  index :" + index);
			return -1;
		}
             if (fileIds.containsKey(USIM_EFEMAIL_TAG)){
			 	
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
			Log.i(LOG_TAG,
					"findEFAnrInfo  fileIds == null  index :" + index);
			return -1;
		}
             if (fileIds.containsKey(USIM_EFANR_TAG)){
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
			Log.i(LOG_TAG,
					"findEFIapInfo  fileIds == null  index :" + index);
			return -1;
		} 
             if (fileIds.containsKey(USIM_EFIAP_TAG)){
		      return fileIds.get(USIM_EFIAP_TAG);
             }

              return -1;
	}

	// add end

	public int[] getAdnRecordsSize() {
		int size[] = new int[3];

		Log.i("UsimPhoneBookManager", "getEFLinearRecordSize");

		size[2] = mPhoneBookRecords.size();
		Log.i("UsimPhoneBookManager", "getEFLinearRecordSize size" + size[2]);

		/*	 
		 * * synchronized (mLock) {
		 * 
		 * if (mPbrFile == null) { readPbrFileAndWait(); }
		 * 
		 * if (mPbrFile == null) return null;
		 * 
		 * int numRecs = mPbrFile.mFileIds.size(); Log.i("UsimPhoneBookManager"
		 * ,"loadEfFilesFromUsim" +numRecs); mAdnCount = 0; mAdnSize = 0; for
		 * (int i = 0; i < numRecs; i++) { readAdnFileSizeAndWait(i);
		 * 
		 * } // All EF files are loaded, post the response. }
		 */
		return size;
	}

	private int readAdnFileSizeAndWait(int recNum) {
		Map<Integer, Integer> fileIds;
		fileIds = mPbrFile.mFileIds.get(recNum);
		Log.i("UsimPhoneBookManager", "recNum" + recNum);

		if (fileIds == null || fileIds.isEmpty())
			return 0;

		mPhone.getIccFileHandler().getEFLinearRecordSize(
				fileIds.get(USIM_EFADN_TAG),
				obtainMessage(EVENT_ADN_RECORD_COUNT));
		try {
			mLock.wait();
		} catch (InterruptedException e) {
			Log.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
		}
		Log.i("UsimPhoneBookManager", "mAdnCount" + mAdnCount);
		return mAdnCount;
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

	private void readAdnFileAndWait(int recNum) {
		Log.i("UsimPhoneBookManager", "readAdnFileAndWait");
		synchronized (mLock) {
			if (mPbrFile == null) {
				readPbrFileAndWait();
			}
		}
		if (mPbrFile == null) {
			Log.e(LOG_TAG, "Error: Pbr file is empty");
			return;
		}
		// END 20110413
		Map<Integer, Integer> fileIds;
		fileIds = mPbrFile.mFileIds.get(recNum);
		if (fileIds == null || fileIds.isEmpty())
			return;

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
	}

	private void readEmailFileAndWait(int recNum) {
		Log.i("UsimPhoneBookManager", "readEmailFileAndWait");
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
			if (mEmailPresentInIap) {
				readIapFileAndWait(fileIds.get(USIM_EFIAP_TAG));
				if (mIapFileRecord == null) {
					Log.e(LOG_TAG, "Error: IAP file is empty");
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
				return;
			}

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

	private void readAnrFileAndWait(int recNum) {
		Log.i("UsimPhoneBookManager", "readAnrFileAndWait");
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

		if (fileIds == null)
			return;
		if(mAnrInfoFromPBR == null){
                   return;
		}
		SubjectIndexOfAdn records = mAnrInfoFromPBR.get(recNum);

		records.record = new HashMap<Integer, ArrayList<byte[]>>();
		if (fileIds.containsKey(USIM_EFANR_TAG)) {
			ishaveAnr = true;
			ArrayList<Integer> efids = mPbrFile.getFileId(recNum,
					USIM_EFANR_TAG);
			if (efids.size() == 0) {
				return;
			}
			anrFileCount = efids.size();
			log("efids =  " + efids);
			records.efids = new int[anrFileCount];
			// Read the anr file.
			for (int i = 0; i < anrFileCount; i++) {
				log("anr efids.get(i) is " + efids.get(i));
				mFh.loadEFLinearFixedAll(efids.get(i),
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
					return;
				}
				records.efids[i] = efids.get(i);
				records.record.put(efids.get(i), mTempAnrFileRecord);
				mTempAnrFileRecord = null;
			}
			mAnrInfoFromPBR.set(recNum, records);
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

	private String getType1Anr(int num, SubjectIndexOfAdn anrInfo, int adnNum) {

		int efid = 0;
		String anr = "";
		int anrTagNumberInIap;
		ArrayList<byte[]> anrFileRecord;
		byte[] anrRec;
		String anrI;
		boolean isSet = false;

		Log.i("AdnRecord", "getType1Anr, num " + num + "adnNum " + adnNum);
		for (int i = 0; i < anrInfo.efids.length; i++) {

			efid = anrInfo.efids[i];
			anrFileRecord = anrInfo.record.get(efid);

			if (anrFileRecord == null) {

				continue;
			}
			Log.i("AdnRecord", "getType1Anr, size " + anrFileRecord.size());
			if (adnNum < anrFileRecord.size()) {
				anrRec = anrFileRecord.get(adnNum);
				anrI = PhoneNumberUtils.calledPartyBCDToString(anrRec, 2,
						(0xff & anrRec[2]));

			} else {

				anrI = "";
			}
			// SIM record numbers are 1 based
			if (i == 0) {
				anr = anrI;
			} else {
				anr = anr + ";" + anrI;
			}
		}
		log("getType1Anr anr:" + anr);
		return anr;

	}

	private void updatePhoneAdnRecord(int num) {

		int numAdnRecs = mPhoneBookRecords.size();

		mAdnRecordSizeArray[num] = mPhoneBookRecords.size();
		Log.i("LOG_TAG", "updatePhoneAdnRecord mAdnRecordSizeArray[num] : "
				+ numAdnRecs + "num " + num);

		int numIapRec = 0;
		SubjectIndexOfAdn anrInfo = null;
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
			if (mEmailFileRecord != null) {
				mEmailRecordSizeArray[num] = mEmailFileRecord.size();
				Log.i(LOG_TAG,
						"updatePhoneAdnRecord mEmailRecordSizeArray[num] : "
								+ mEmailFileRecord.size());
			}
			mIapRecordSizeArray[num] = mIapFileRecord.size();
			if (mAnrFileRecord != null) {
				mAnrRecordSizeArray[num] = mAnrFileRecord.size();
			}
			Log.i("AdnRecord", "updatePhoneAdnRecord,numIapRec  " + numIapRec);

			numIapRec = (numAdnRecs > numIapRec) ? numIapRec : numAdnRecs;
			if (mEmailFileRecord != null) {

				for (int i = mDoneAdnCount; i < numIapRec; i++) {
					byte[] record = null;
					try {
						record = mIapFileRecord.get(i);

					} catch (IndexOutOfBoundsException e) {
						Log
								.e(LOG_TAG,
										"Error: Improper ICC card: No IAP record for ADN, continuing");
						break;
					}
					int recNum = (int) (record[mEmailTagNumberInIap] & 0xFF);
					recNum = ((recNum == 0xFF) ? (-1) : recNum);
					Log.e("AdnRecord", "iap recNum == " + recNum);
					if (recNum != -1) {

						String[] emails = new String[1];
						String[] nEmails = null;
						// SIM record numbers are 1 based
						emails[0] = readEmailRecord(recNum - 1);
						Log.i("AdnRecord", "updatePhoneAdnRecord,emails[0] "
								+ emails[0]);
						if (emails[0] == null || emails[0] == "") {

							Log.i("AdnRecord",
									"updatePhoneAdnRecord,emails[0]==null");
							setIapFileRecord(num, i, (byte) 0xFF);

							continue;

						} else {

							nEmails = emails;
						}
						usedEmailNumSet.add(new Integer(recNum));

						int adnNum = i;

						Log.i("AdnRecord", "updatePhoneAdnRecord,numIapRec  "
								+ numIapRec + "numAdnRecs " + numAdnRecs + "i "
								+ i);

						AdnRecord rec = mPhoneBookRecords.get(adnNum);

						Log.i("AdnRecord", "updatePhoneAdnRecord,rec name:"
								+ rec.getAlphaTag() + "num " + rec.getNumber());

						if (rec != null) {
							rec.setEmails(nEmails);
						} else {
							// might be a record with only email
							rec = new AdnRecord("", "", nEmails);
							Log.i("AdnRecord",
									"updatePhoneAdnRecord AdnRecord  emails"
											+ emails[0]);
						}

						mPhoneBookRecords.set(adnNum, rec);
					}
				}
			}
		} else {
			//mIapFileRecordArray[num] = mIapFileRecord;
			if (mEmailFileRecord != null) {
				mEmailRecordSizeArray[num] = mEmailFileRecord.size();
				Log.i(LOG_TAG,
						"updatePhoneAdnRecord mEmailRecordSizeArray[num] : "
								+ mEmailFileRecord.size());
			}
			//mIapRecordSizeArray[num] = mIapFileRecord.size();
			if (mAnrFileRecord != null) {
				mAnrRecordSizeArray[num] = mAnrFileRecord.size();
			}
			if (mEmailFileRecord != null) {
				int len = mEmailFileRecord.size();

				// Type 1 file, the number of records is the same as the number
				// of
				// records in the ADN file.
				Log.i(LOG_TAG,"updatePhoneAdnRecord mEmailsForAdnRec: " + mEmailsForAdnRec );
				if (mEmailsForAdnRec == null) {
					parseType1EmailFile(len);
				}
				for (int i = mDoneAdnCount; i < numAdnRecs; i++) {
					ArrayList<String> emailList = null;
					try {
						emailList = mEmailsForAdnRec.get(i);
					} catch (IndexOutOfBoundsException e) {
						break;
					}
					if (emailList == null)
						continue;

					AdnRecord rec = mPhoneBookRecords.get(i);
					Log.i(LOG_TAG, "updatePhoneAdnRecord emailList.size(): "
							+ emailList.size());
					String[] emails = new String[emailList.size()];
					System.arraycopy(emailList.toArray(), 0, emails, 0,
							emailList.size());
					rec.setEmails(emails);
					mPhoneBookRecords.set(i, rec);
				}
			}

		}

		// ICC cards can be made such that they have an IAP file but all
		// records are empty. So we read both type 1 and type 2 file
		// email records, just to be sure.

		Log.i(LOG_TAG, "updatePhoneAdnRecord ANR >>>>>>>>>>>>>> ");
		if (mAnrInfoFromPBR != null) {

			anrInfo = mAnrInfoFromPBR.get(num);

		}

		if (num >= 0 && ishaveAnr && anrInfo != null) {
			Log.i(LOG_TAG, "updatePhoneAdnRecord mAnrRecordSizeArray[num] : "
					+ mAnrFileRecord.size());

			// int count = (mAnrFileRecord.size()/anrFileCount > numAdnRecs) ?
			// numAdnRecs :mAnrFileRecord.size()/anrFileCount;
			int count = numAdnRecs;
			Log.i(LOG_TAG, "updatePhoneAdnRecord ANR  count " + count);
			for (int i = mDoneAdnCount; i < count; i++) {
				// SIM record numbers are 1 based
				String anr = null;
				// anr = readAnrRecord(i);
				anr = getType1Anr(num, anrInfo, (i - mDoneAdnCount));
				AdnRecord rec = mPhoneBookRecords.get(i);
				if (rec != null) {
					rec.setAnr(anr);

				}
				mPhoneBookRecords.set(i, rec);
			}
		}
		mDoneAdnCount += numAdnRecs;
	}

	void parseType1EmailFile(int numRecs) {
		mEmailsForAdnRec = new HashMap<Integer, ArrayList<String>>();
		byte[] emailRec = null;
		Log.i(LOG_TAG, "parseType1EmailFile  numRecs " +numRecs);
		for (int i = 0; i < numRecs; i++) {
			try {
				emailRec = mEmailFileRecord.get(i);
			} catch (IndexOutOfBoundsException e) {
				Log
						.e(LOG_TAG,
								"Error: Improper ICC card: No email record for ADN, continuing");
				break;
			}
			int adnRecNum = i+1   ;//emailRec[emailRec.length - 1];

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
					anr = anr1 + ";" + anr2 + ";" + anr3;
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
					anr = anr1 + ";" + anr2 + ";" + anr3;
					log("readAnrRecord anr:" + anr);
					return anr;
				}
			} else {
				log("the total anr size is exceed mAnrFileRecord.size()  "
						+ mAnrFileRecord.size());
			}
			anr = anr1 + ";" + anr2;
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
			int size = ((ArrayList<AdnRecord>) ar.result).size();
			log("EVENT_USIM_ADN_LOAD_DONE size" + size);
			mAdnCount += size;
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
				if (mEmailFileRecord == null) {
					mEmailFileRecord = new ArrayList<byte[]>();
				}
				mEmailFileRecord.addAll((ArrayList<byte[]>) ar.result);
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
					mAdnCount += recordSize[2];
					mAdnSize += recordSize[1];
					recordSize[2] = mAdnCount;
					recordSize[1] = mAdnSize;
					mLock.notifyAll();
				}
			}
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
			mAnrNum = 0;
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
					if (!subTlv.nextObject())
						return ints;
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
			Map<Integer, Integer> val = new HashMap<Integer, Integer>();

			do {
				tag = tlv.getTag();
				switch (tag) {
				case USIM_TYPE1_TAG: // A8
				case USIM_TYPE3_TAG: // AA
				case USIM_TYPE2_TAG: // A9
					data = tlv.getData();
					tlvEf = new SimTlv(data, 0, data.length);
					parseEf(tlvEf, val, tag);
					break;
				}
			} while (tlv.nextObject());
			mFileIds.put(recNum, val);
			Log.i(LOG_TAG, "parseTag mAnrNum" + mAnrNum);
		}

		void parseEf(SimTlv tlv, Map<Integer, Integer> val, int parentTag) {
			int tag;
			byte[] data;
			int tagNumberWithinParentTag = 0;
			SubjectIndexOfAdn anrInfo = new SubjectIndexOfAdn();
			boolean hasAnr = false;

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
					val.put(tag, efid);
					if ( tag == USIM_EFEMAIL_TAG) {
						
						if(parentTag == USIM_TYPE2_TAG ){
						      mEmailPresentInIap = true;
						      mEmailTagNumberInIap = tagNumberWithinParentTag;
						}
						Log.i(LOG_TAG, "parseEf   email  TAG  " + parentTag
								+ "num " + tagNumberWithinParentTag);
					}
					if (tag == USIM_EFANR_TAG) {

						Log.i(LOG_TAG, "parseEf   ANR  TAG  " + parentTag
								+ "num " + tagNumberWithinParentTag);
						Log.i(LOG_TAG, "parseEf   ANR  efid  " + efid);
						hasAnr = true;
						mAnrNum++;
					}
					break;
				}
				tagNumberWithinParentTag++;
			} while (tlv.nextObject());

			Log.i(LOG_TAG, "parseTag hasAnr " + hasAnr);
			if (hasAnr) {
				mAnrInfoFromPBR.add(anrInfo);

			}
		}

		void parsePBRData(byte[] data) {
			log("enter parsePBRData");
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
			tlvList.add(tlv);
		}

	}

	private void log(String msg) {
		if (DBG)
			Log.d(LOG_TAG, msg);
	}

}
