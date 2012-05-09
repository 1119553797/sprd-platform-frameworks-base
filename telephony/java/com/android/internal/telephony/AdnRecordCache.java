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

import android.content.Intent;
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
	// ***** Instance Variables
	static String LOG_TAG = "AdnRecordCache";
	private IccFileHandler mFh;
	public UsimPhoneBookManager mUsimPhoneBookManager;
	PhoneBase phone;

	// Indexed by EF ID
	SparseArray<ArrayList<AdnRecord>> adnLikeFiles = new SparseArray<ArrayList<AdnRecord>>();

	// People waiting for ADN-like files to be loaded
	SparseArray<ArrayList<Message>> adnLikeWaiters = new SparseArray<ArrayList<Message>>();

	// People waiting for adn record to be updated
	SparseArray<Message> userWriteResponse = new SparseArray<Message>();

	// ***** Event Constants

	static final int EVENT_LOAD_ALL_ADN_LIKE_DONE = 1;
	static final int EVENT_UPDATE_ADN_DONE = 2;

	// add multi record and email in usim begin
	static final int EVENT_UPDATE_USIM_ADN_DONE = 3;

	public int mInsertId = -1;
	protected final Object mLock = new Object();
	public boolean updateOthers = true;

	/*
	 * public AdnRecordCache(IccFileHandler fh) { mFh = fh;
	 * mUsimPhoneBookManager = new UsimPhoneBookManager(mFh, this); }
	 */
	public int getAdnLikeSize() {
		return adnLikeFiles.size();
	}

	public UsimPhoneBookManager getUsimPhoneBookManager() {
		if(phone.getIccCard().isApplicationOnIcc(
				IccCardApplication.AppType.APPTYPE_USIM)){
			return mUsimPhoneBookManager;
		}
		return null;
	}

	// add multi record and email in usim end

	// ***** Constructor

	public AdnRecordCache(PhoneBase phone) {
		this.phone = phone;
		mFh = phone.getIccFileHandler();
		mUsimPhoneBookManager = new UsimPhoneBookManager(phone, mFh, this);
	}

	public Object getLock() {
		return mLock;
	}

	// ***** Called from SIMRecords

	/**
	 * Called from SIMRecords.onRadioNotAvailable and
	 * SIMRecords.handleSimRefresh.
	 */
	public void reset() {
	      Log.i("AdnRecordCache", "reset");
		adnLikeFiles.clear();
		mUsimPhoneBookManager.reset();

		clearWaiters();
		clearUserWriters();

	}

	private void clearWaiters() {
		int size = adnLikeWaiters.size();
		for (int i = 0; i < size; i++) {
			ArrayList<Message> waiters = adnLikeWaiters.valueAt(i);
			AsyncResult ar = new AsyncResult(null, null, new RuntimeException(
					"AdnCache reset"));
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
	 *         radio session, or null if we haven't
	 */
	public synchronized ArrayList<AdnRecord> getRecordsIfLoaded(int efid) {
		return adnLikeFiles.get(efid);
	}

	/**
	 * Returns extension ef associated with ADN-like EF or -1 if we don't know.
	 *
	 * See 3GPP TS 51.011 for this mapping
	 */
	int extensionEfForEf(int efid) {
		switch (efid) {
		case EF_MBDN:
			return EF_EXT6;
		case EF_ADN:
			return EF_EXT1;
		case EF_SDN:
			return EF_EXT3;
		case EF_FDN:
			return EF_EXT2;
		case EF_MSISDN:
			return EF_EXT1;
		case EF_PBR:
			return 0; // The EF PBR doesn't have an extension record
		default:
			return 0;
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
	 * @param efid
	 *            must be one among EF_ADN, EF_FDN, and EF_SDN
	 * @param adn
	 *            is the new adn to be stored
	 * @param recordIndex
	 *            is the 1-based adn record index
	 * @param pin2
	 *            is required to update EF_FDN, otherwise must be null
	 * @param response
	 *            message to be posted when done response.exception hold the
	 *            exception in error
	 */
	public void updateAdnByIndex(int efid, AdnRecord adn, int recordIndex,
			String pin2, Message response) {

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

		/*
		 * new AdnRecordLoader(phone).updateEF(adn, efid, extensionEF,
		 * recordIndex, pin2, obtainMessage(EVENT_UPDATE_ADN_DONE, efid,
		 * recordIndex, adn));
		 */
		// add multi record and email in usim
		new AdnRecordLoader(mFh).updateEF(adn, efid, extensionEF, recordIndex,
				pin2, obtainMessage(EVENT_UPDATE_ADN_DONE, efid, recordIndex,
						adn));
	}

	// add multi record and email begin
	private String[] getAnrNumGroup(String anr) {
		String[] pair = null;
		Log.i(LOG_TAG, "getAnrNumGroup anr =" + anr);
		if (!TextUtils.isEmpty(anr)) {

			pair = anr.split(AdnRecord.ANR_SPLIT_FLG);
		}

		return pair;

	}

	private boolean compareSubject(int type, AdnRecord oldAdn, AdnRecord newAdn) {

		boolean isEqual = true;
		switch (type) {

		case UsimPhoneBookManager.USIM_SUBJCET_EMAIL:

			isEqual = oldAdn.stringCompareEmails(oldAdn.emails, newAdn.emails);
			break;

		case UsimPhoneBookManager.USIM_SUBJCET_ANR:
			isEqual = oldAdn.stringCompareAnr(oldAdn.anr, newAdn.anr);
			break;

		default:
			break;
		}

		return isEqual;

	}

	private String[] getSubjectString(int type, AdnRecord adn) {

		String[] s1 = null;
		switch (type) {

		case UsimPhoneBookManager.USIM_SUBJCET_EMAIL:
			s1 = adn.emails;
			break;

		case UsimPhoneBookManager.USIM_SUBJCET_ANR:
			s1 = getAnrNumGroup(adn.anr);
			break;
		default:
			break;

		}

		return s1;

	}

	private int[] getUpdateSubjectFlag(int num,int type, AdnRecord oldAdn,
			AdnRecord newAdn) {

		int[] flag = null;
		int oldCount = 0, newCount = 0,  count = 0,i = 0;
		String str1, str2;
		String[] strArr1, strArr2;
		int efids[] =null;

		strArr1 = getSubjectString(type, oldAdn);
		strArr2 = getSubjectString(type, newAdn);

		if (strArr1 != null) {

			oldCount = strArr1.length;

		}

		if (strArr2 != null) {

			newCount = strArr2.length;

		}
             Log.i(LOG_TAG, "getUpdateSubjectFlag oldCount =" + oldCount + " newCount " +newCount );
             efids = mUsimPhoneBookManager.getSubjectEfids(type,num);
		if(efids == null){
                  return null;
		}
		count = efids.length;
		flag = new int[count];
             Log.i(LOG_TAG, "getUpdateSubjectFlag count =" + count);
		for (i = 0; i < count; i++) {

			if (i < oldCount) {
				str1 = strArr1[i];
			} else {

				str1 = "";
			}

			if (i < newCount) {
				str2 = strArr2[i];
			} else {

				str2 = "";
			}

			flag[i] = (str1.trim().equals(str2.trim())) ? 0 : 1;
			Log.i(LOG_TAG, "getUpdateSubjectFlag flag[i] =" + flag[i]);
		}

		return flag;

	}

	boolean isCleanRecord(int num,int type, AdnRecord oldAdn, AdnRecord newAdn, int index) {

		int oldCount = 0, newCount = 0, count = 0, i = 0;
		String str1, str2;
		String[] strArr1, strArr2;
		int efids[] =null;

		strArr1 = getSubjectString(type, oldAdn);
		strArr2 = getSubjectString(type, newAdn);

		if (strArr1 != null) {

			oldCount = strArr1.length;

		}

		if (strArr2 != null) {

			newCount = strArr2.length;

		}
             Log.i(LOG_TAG, "isCleanRecord oldCount =" + oldCount + " newCount " +newCount );
		efids = mUsimPhoneBookManager.getSubjectEfids(type,num);
		if(efids == null){
                  return false;
		}
		count = efids.length;

             Log.i(LOG_TAG, "isCleanRecord count =" + count);

		for (i = 0; i < count; i++) {

			if (i < oldCount) {
				str1 = strArr1[i];
			} else {

				str1 = "";
			}

			if (i < newCount) {
				str2 = strArr2[i];
			} else {

				str2 = "";
			}

			if (index == i && !(str1.trim().equals(str2.trim()))
					&& TextUtils.isEmpty(str2)) {

				return true;
			}
		}

		return false;
	}

	private void updateSubjectOfAdn(int type, int num,
			AdnRecordLoader adnRecordLoader, int adnNum,int index, int efid,
			AdnRecord oldAdn, AdnRecord newAdn,int iapEF, String pin2) {

		int[][] anrNumInIap = null;
		int[] subjectNum = null;
		boolean newAnr = false;
		int[] updateSubjectFlag = null;
        int[] updateRecordInIap = null;

		ArrayList<Integer> subjectEfids;
		ArrayList<Integer> subjectNums;
        ArrayList<Integer> inIapNums;
		int m = 0,n =0 ;
		int[][] anrTagMap; // efid ,numberInIap
		int efids[] = mUsimPhoneBookManager.getSubjectEfids( type,  num);

		Log.i(LOG_TAG, "Begin : updateSubjectOfAdn num =" + num + "adnNum " + adnNum + "index " + index);

		if (compareSubject(type, oldAdn, newAdn)) {

			return;
		}

		updateSubjectFlag = getUpdateSubjectFlag(num,type, oldAdn, newAdn);
		subjectEfids = new ArrayList<Integer>();
		subjectNums = new ArrayList<Integer>();
             inIapNums =  new ArrayList<Integer>();
             if(updateSubjectFlag == null || efids == null ||efids.length == 0){

                   return;
		}

		anrTagMap = mUsimPhoneBookManager.getSubjectTagNumberInIap(type,
					num);


		subjectNum = new int[efids.length];

             for(m=0 ;m<efids.length;m++ ){

		 subjectEfids.add(efids[m]);

		if (mUsimPhoneBookManager.isSubjectRecordInIap(type, num, m)) {

			Log.i(LOG_TAG, "updateSubjectOfAdn  in iap  ");

			byte[] record = null;


			try {
				ArrayList<byte[]> mIapFileRecord = mUsimPhoneBookManager.getIapFileRecord(num);

				if (mIapFileRecord != null) {
					record = mIapFileRecord.get(index-1);

				} else {
					Log.i(LOG_TAG,
									"updateSubjectOfAdn mIapFileRecord == null ");
                                subjectNums.add(0);
        				n++;
					continue;

				}

			} catch (IndexOutOfBoundsException e) {
				Log
						.e(LOG_TAG,
								"Error: Improper ICC card: No IAP record for ADN, continuing");
			}

			if(anrTagMap == null ){

                         subjectNums.add(0);
        			n++;
                         continue;
			}



			if (record != null) {


				Log.i(LOG_TAG, "subjectNumberInIap =" + anrTagMap[m][1]);
				subjectNum[m] = (int) (record[anrTagMap[m][1]] & 0xFF);
				Log.i(LOG_TAG, "subjectNumber =" + subjectNum[m]);
				subjectNum[m] = subjectNum[m] == 0xFF ? (-1)
							: subjectNum[m];
				Log.i(LOG_TAG, "subjectNum[m] =" + subjectNum[m]);



			} else {


				subjectNum[m] = -1;
				Log.i(LOG_TAG, "subjectNum[m] =" + subjectNum[m]);

			}
			boolean isFull = false;
			{

				if (subjectNum[m] == -1 && updateSubjectFlag[m] == 1)
				{
					subjectNum[m] = mUsimPhoneBookManager.getNewSubjectNumber(type, num,
										anrTagMap[m][0], n, index, true);

					if (subjectNum[m] == -1) {

						isFull = true;
						Log.i(LOG_TAG, "updateSubjectOfAdn   is full  ");
                                       n++;
						subjectNums.add(0);
						continue;

					}
				}

				Log.i(LOG_TAG, "updateSubjectOfAdn   subjectNum  "
						+ subjectNum[m]);

			}


			Log.i(LOG_TAG, "updateSubjectOfAdn   updateSubjectFlag  "
						+ updateSubjectFlag[m] + "subjectNum[m] "
						+ subjectNum[m]);

			if (updateSubjectFlag[m] == 1 && subjectNum[m] != -1) {

		             subjectNums.add(subjectNum[m]);
			}else{

                          subjectNums.add(0);
			}

			if (updateSubjectFlag[m] == 1) {

				if (isCleanRecord(num,type, oldAdn, newAdn, m)) {

					Log.i(LOG_TAG, " clean anrTagMap[m][0]     "
					+ Integer.toHexString(anrTagMap[m][0]));

					mUsimPhoneBookManager.removeSubjectNumFromSet(type,
							num, anrTagMap[m][0], n, subjectNum[m]);//
					mUsimPhoneBookManager.setIapFileRecord(num,
								index-1, (byte) 0xFF, anrTagMap[m][1]);
					record[anrTagMap[m][1]] = (byte) 0xFF;

				} else {
					      	Log.i(LOG_TAG, "  anrTagMap[m][0]     "
						+ Integer.toHexString(anrTagMap[m][0]));
						mUsimPhoneBookManager.setIapFileRecord(num,
								index-1, (byte) (subjectNum[m] & 0xFF),
								anrTagMap[m][1]);
						record[anrTagMap[m][1]] = (byte) (subjectNum[m] & 0xFF);
				}

				if (anrTagMap[m][0] > 0) {
						Log.e(LOG_TAG, "begin to update IAP ---IAP id  "
								+ adnNum + "iapEF " + Integer.toHexString(iapEF));
						adnRecordLoader = new AdnRecordLoader(mFh);
						adnRecordLoader.updateEFIapToUsim(newAdn,
								iapEF, index, record, pin2,

								null);
				}


			}

			n++;


		} else {

			if(updateSubjectFlag[m] == 1 ){

			     if (mUsimPhoneBookManager.getNewSubjectNumber(type, num,
						efids[m], 0, index, false) == index) {
				     subjectNums.add(index);

			     }else{

                              subjectNums.add(0);
			          Log.e(LOG_TAG,
								"updateSubjectOfAdn fail to get  new subject ");

					  }
			 }else {
                              subjectNums.add(0);
				    Log.e(LOG_TAG,
								"updateSubjectOfAdn don't need to update subject ");
			 }

		  }

            }

            	Log.e(LOG_TAG, " END :updateSubjectOfAdn  updateSubjectOfAdn efids is "
					+ subjectEfids  +  " subjectNums " + subjectNums);


		for(int i=0; i<subjectEfids.size(); i++ ){

		    if(subjectNums.get(i) !=0){

                  ArrayList<Integer> toUpdateNums = new ArrayList<Integer>();
		     ArrayList<Integer> toUpdateIndex = new ArrayList<Integer>();
                  ArrayList<Integer> toUpdateEfids = new ArrayList<Integer>();

		     toUpdateEfids.add(subjectEfids.get(i));
		     toUpdateIndex.add(i);
		     toUpdateNums.add(subjectNums.get(i));

	           adnRecordLoader = new AdnRecordLoader(mFh);

		     if (type == UsimPhoneBookManager.USIM_SUBJCET_EMAIL) {

			   adnRecordLoader.updateEFEmailToUsim(newAdn, toUpdateEfids, toUpdateNums,efid,
						index,toUpdateIndex, pin2, null);

		      }
		     if (type == UsimPhoneBookManager.USIM_SUBJCET_ANR) {
			      adnRecordLoader.updateEFAnrToUsim(newAdn, toUpdateEfids, efid,index,
						toUpdateNums,toUpdateIndex ,pin2, null);
		     }

		     }
		}



	}


	public synchronized void updateUSIMAdnBySearch(int efid, AdnRecord oldAdn,
			AdnRecord newAdn, String pin2, Message response) {

		int extensionEF = 0;
		int index = -1;
		int emailEF = 0;
		int iapEF = 0;

		int recNum = 0;

		int iapRecNum = 0;

		Log.i(LOG_TAG, "updateUSIMAdnBySearch efid " + efid);
		for (int num = 0; num < mUsimPhoneBookManager.getNumRecs(); num++) {

			efid = mUsimPhoneBookManager.findEFInfo(num);
			extensionEF = mUsimPhoneBookManager.findExtensionEFInfo(num);

			iapEF = mUsimPhoneBookManager.findEFIapInfo(num);

			Log.e(LOG_TAG, "efid : " + efid + "extensionEF :" + extensionEF
					+ " iapEF:" + iapEF);

			if (efid < 0 || extensionEF < 0) {
				sendErrorResponse(response, "EF is not known ADN-like EF:"
						+ "efid" + efid + ",extensionEF=" + extensionEF);
				return;
			}
			Log.i(LOG_TAG, "updateUSIMAdnBySearch (1)");
			ArrayList<AdnRecord> oldAdnList;

			Log.e(LOG_TAG, "efid is " + efid);

			oldAdnList = getRecordsIfLoaded(efid);
			if (oldAdnList == null) {
				sendErrorResponse(response, "Adn list not exist for EF:" + efid);
				return;
			}
			Log.i(LOG_TAG, "updateUSIMAdnBySearch (2)");

			int count = 1;
			boolean find_index = false;
			for (Iterator<AdnRecord> it = oldAdnList.iterator(); it.hasNext();) {
				if (oldAdn.isEqual(it.next())) {
					Log.e("GSM", "we got the index " + count);
					find_index = true;
					index = count;

					mInsertId = index;

					break;
				}
				count++;
			}

			if (find_index) {
				recNum = num;

				if (num > 0 ) {
					mInsertId += mUsimPhoneBookManager.mAdnRecordSizeArray[num - 1];

				}

				Log.i(LOG_TAG, "updateUSIMAdnBySearch (3)");
				Log.i(LOG_TAG, "mInsertId" + mInsertId);

				break;
			}
		}

		if (index == -1) {
			sendErrorResponse(response, "Adn record don't exist for " + oldAdn);
			return;
		}


		Message pendingResponse = userWriteResponse.get(efid);

		if (pendingResponse != null) {
			sendErrorResponse(response, "Have pending update for EF:" + efid);
			return;
		}

		userWriteResponse.put(efid, response);
		AdnRecordLoader adnRecordLoader = new AdnRecordLoader(mFh);

		updateSubjectOfAdn(UsimPhoneBookManager.USIM_SUBJCET_ANR, recNum,
				adnRecordLoader, mInsertId,index, efid, oldAdn, newAdn,iapEF, pin2);
		updateSubjectOfAdn(UsimPhoneBookManager.USIM_SUBJCET_EMAIL, recNum,
				adnRecordLoader, mInsertId,index, efid, oldAdn, newAdn,iapEF,pin2);

		adnRecordLoader.updateEFAdnToUsim(newAdn, efid, extensionEF, index,
				pin2, obtainMessage(EVENT_UPDATE_USIM_ADN_DONE, efid, index,
						newAdn));

		Log.i(LOG_TAG, "updateUSIMAdnBySearch  finish");

	}

	// add multi record and email in usim end

	public int getAdnIndex(int efid, AdnRecord oldAdn) {

		ArrayList<AdnRecord> oldAdnList;
		oldAdnList = getRecordsIfLoaded(efid);
		Log.i("AdnRecordCache", "getAdnIndex efid " + efid);
		if (oldAdnList == null) {

			return -1;
		}
		Log.i("AdnRecordCache", "updateAdnBySearch (2)");
		int index = -1;
		int count = 1;
		for (Iterator<AdnRecord> it = oldAdnList.iterator(); it.hasNext();) {
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
	 * @param efid
	 *            must be one of EF_ADN, EF_FDN, and EF_SDN
	 * @param oldAdn
	 *            is the adn to be replaced If oldAdn.isEmpty() is ture, it
	 *            insert the newAdn
	 * @param newAdn
	 *            is the adn to be stored If newAdn.isEmpty() is true, it delete
	 *            the oldAdn
	 * @param pin2
	 *            is required to update EF_FDN, otherwise must be null
	 * @param response
	 *            message to be posted when done response.exception hold the
	 *            exception in error
	 */
	public void updateAdnBySearch(int efid, AdnRecord oldAdn, AdnRecord newAdn,
			String pin2, Message response) {

		int extensionEF;
		extensionEF = extensionEfForEf(efid);

		if (extensionEF < 0) {
			sendErrorResponse(response, "EF is not known ADN-like EF:" + efid);
			return;
		}

		ArrayList<AdnRecord> oldAdnList;
		oldAdnList = getRecordsIfLoaded(efid);

		if (oldAdnList == null) {
			sendErrorResponse(response, "Adn list not exist for EF:" + efid);
			return;
		}

		int index = -1;
		int count = 1;
		for (Iterator<AdnRecord> it = oldAdnList.iterator(); it.hasNext();) {
			if (oldAdn.isEqual(it.next())) {
				index = count;
				mInsertId = index;
				break;
			}
			count++;
		}

		if (index == -1) {

			sendErrorResponse(response, "Adn record don't exist for " + oldAdn);
			return;

		}

		Message pendingResponse = userWriteResponse.get(efid);

		if (pendingResponse != null) {
			sendErrorResponse(response, "Have pending update for EF:" + efid);
			return;
		}

		userWriteResponse.put(efid, response);

		/*
		 * new AdnRecordLoader(phone).updateEF(newAdn, efid, extensionEF, index,
		 * pin2, obtainMessage(EVENT_UPDATE_ADN_DONE, efid, index, newAdn));
		 */
		// add multi record and email in usim
		new AdnRecordLoader(mFh)
				.updateEF(newAdn, efid, extensionEF, index, pin2,
						obtainMessage(EVENT_UPDATE_ADN_DONE, efid, index,
								newAdn));

	}

	/**
	 * Responds with exception (in response) if efid is not a known ADN-like
	 * record
	 */
	public void requestLoadAllAdnLike(int efid, int extensionEf,
			Message response) {
		ArrayList<Message> waiters;
		ArrayList<AdnRecord> result;

		if (efid == EF_PBR) {
			result = mUsimPhoneBookManager.loadEfFilesFromUsim();
		} else {
			result = getRecordsIfLoaded(efid);
            if(result != null){
                if(result.size() == 0){
                result = null;
            }
          }
		}

		// Have we already loaded this efid?
		if (result != null) {
			Log.i("AdnRecordCache"," result != null " );
			if (response != null) {
				AsyncResult.forMessage(response).result = result;
				response.sendToTarget();
			}

			return;
		}
        if(efid == EF_ADN){
            // mark sim card
            markAdnRecordLoaded(false);
        }
             Log.i("AdnRecordCache", "requestLoadAllAdnLike (2)efid " + efid);
		// Have we already *started* loading this efid?

		waiters = adnLikeWaiters.get(efid);

		if (waiters != null) {
			// There's a pending request for this EF already
			// just add ourselves to it
                   Log.i("AdnRecordCache"," waiters != null " );
			waiters.add(response);
			return;
		}
             Log.i("AdnRecordCache", "requestLoadAllAdnLike (3)efid " + efid);
		// Start loading efid

		waiters = new ArrayList<Message>();
		waiters.add(response);

		adnLikeWaiters.put(efid, waiters);

		if (extensionEf < 0) {
			// respond with error if not known ADN-like record

			if (response != null) {
				AsyncResult.forMessage(response).exception = new RuntimeException(
						"EF is not known ADN-like EF:" + efid);
				response.sendToTarget();
			}
                    Log.i("AdnRecordCache"," extensionEf < 0 " );
			return;
		}
             Log.i("AdnRecordCache", "requestLoadAllAdnLike (4)efid " + efid);
		/*
		 * new AdnRecordLoader(phone).loadAllFromEF(efid, extensionEf,
		 * obtainMessage(EVENT_LOAD_ALL_ADN_LIKE_DONE, efid, 0));
		 */
		new AdnRecordLoader(mFh).loadAllFromEF(efid, extensionEf,
				obtainMessage(EVENT_LOAD_ALL_ADN_LIKE_DONE, efid, 0));
	}

    public void broadcastForAdnRecordLoaded(String value, String reason) {
        Log.d("AdnRecordCache", "AdnRecordCache is loaded and send broadcast");
        Intent intent = new Intent(TelephonyIntents.SIM_ADNCACHE_LOADED);
        intent.putExtra(IccCard.INTENT_KEY_PHONE_ID, phone.getPhoneId());
        phone.getContext().sendBroadcast(intent, null);
    }

	// ***** Private methods

	private void notifyWaiters(ArrayList<Message> waiters, AsyncResult ar) {

		if (waiters == null) {
			return;
		}

		for (int i = 0, s = waiters.size(); i < s; i++) {
			Message waiter = waiters.get(i);

			AsyncResult.forMessage(waiter, ar.result, ar.exception);
			waiter.sendToTarget();
		}
	}

    public void markAdnRecordLoaded(boolean loaded){
        String flag = loaded ? "1" : "0";
        phone.setSystemProperty(TelephonyProperties.ADNCACHE_LOADED_STATE,flag);
        Log.d("AdnRecordCache", "set value of " + TelephonyProperties.ADNCACHE_LOADED_STATE + " : " + flag);
        if(loaded){
            broadcastForAdnRecordLoaded(null,null);
        }
    }

	// ***** Overridden from Handler


	public void handleMessage(Message msg) {
		AsyncResult ar;
		int efid;
		int index;
		AdnRecord adn;
		Message response;

		switch (msg.what) {
		case EVENT_LOAD_ALL_ADN_LIKE_DONE:
			/* arg1 is efid, obj.result is ArrayList<AdnRecord> */
			ar = (AsyncResult) msg.obj;
			efid = msg.arg1;
			ArrayList<Message> waiters;

			waiters = adnLikeWaiters.get(efid);
			adnLikeWaiters.delete(efid);
                    Log.i("AdnRecordCache","EVENT_LOAD_ALL_ADN_LIKE_DONE efid " + efid +  " ar.exception " + ar.exception );
			if (ar.exception == null) {
			      Log.i("AdnRecordCache","size " + ((ArrayList<AdnRecord>) ar.result).size());
				adnLikeFiles.put(efid, (ArrayList<AdnRecord>) ar.result);
			    if(efid == EF_ADN){
                    //mark sim card
                    markAdnRecordLoaded(true);
                }
            }
			notifyWaiters(waiters, ar);
			break;
		case EVENT_UPDATE_ADN_DONE:
			ar = (AsyncResult) msg.obj;
			efid = msg.arg1;
			index = msg.arg2;
			adn = (AdnRecord) (ar.userObj);

			// yeezone:jinwei add sim_index.
			Log.i("AdnRecordCache", "efid " + efid + "index " + index);
			adn.setRecordNumber(index);
			// end

			if (ar.exception == null) {
				adnLikeFiles.get(efid).set(index - 1, adn);
			}
			Log.i("AdnRecordCache", "efid" + efid);
			response = userWriteResponse.get(efid);
			Log.i("AdnRecordCache", "response" + response + "index " + index);
			userWriteResponse.delete(efid);

			// yeezone:jinwei return sim_index after add a new contact in
			// SimCard.
			// AsyncResult.forMessage(response, null, ar.exception);
			AsyncResult.forMessage(response, index, ar.exception);
			Log.i("AdnRecordCache", "response" + response + "index " + index
					+ "target " + response.getTarget());
			response.sendToTarget();
			break;
		// add multi record and email in usim begin
		case EVENT_UPDATE_USIM_ADN_DONE:
			Log.i("AdnRecordCache", "EVENT_UPDATE_USIM_ADN_DONE");
			ar = (AsyncResult) msg.obj;
			efid = msg.arg1;
			index = msg.arg2;
			adn = (AdnRecord) (ar.userObj);
			int recNum = -1;
			for (int num = 0; num < mUsimPhoneBookManager.getNumRecs(); num++) {
				int adnEF = mUsimPhoneBookManager.findEFInfo(num);
				if (efid == adnEF) {
					recNum = num;
				}
			}
			int[] mAdnRecordSizeArray = mUsimPhoneBookManager
					.getAdnRecordSizeArray();
			int adnRecNum;
			if (recNum == -1) {
				break;
			} else if (recNum == 0) {
				adnRecNum = index - 1;
			} else {
				adnRecNum = mAdnRecordSizeArray[recNum - 1] + index - 1;
			}

			if (ar.exception == null) {
				mUsimPhoneBookManager.setPhoneBookRecords(adnRecNum, adn);
				adnLikeFiles.get(efid).set(index - 1, adn);
				updateOthers = true;
			} else {
				Log.e("GSM", " fail to Update Usim Adn");
			}

			response = userWriteResponse.get(efid);
			userWriteResponse.delete(efid);

			AsyncResult.forMessage(response, null, ar.exception);
			response.sendToTarget();

			Log.i("AdnRecordCache", "EVENT_UPDATE_USIM_ADN_DONE finish");

			break;
		// add multi record and email in usim end
		}

	}

}
