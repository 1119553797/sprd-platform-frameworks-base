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


/**
 * This class implements reading and parsing USIM records.
 * Refer to Spec 3GPP TS 31.102 for more details.
 *
 * {@hide}
 */
public class UsimPhoneBookManager extends Handler implements IccConstants {
    private static final String LOG_TAG = "GSM";
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
// zhanglj add begin for add multi record and email in usim 
    private Object[] mIapFileRecordArray;
    private int newEmailNum;
    private Set<Integer> usedEmailNumSet;
    public int[] mAdnRecordSizeArray;
    private int[] mEmailRecordSizeArray;
    private int[] mIapRecordSizeArray;
// zhanglj add end
    private ArrayList<byte[]> mEmailFileRecord;
    private Map<Integer, ArrayList<String>> mEmailsForAdnRec;
    private int mAdnCount = 0;
    private int mAdnSize = 0;
    protected int recordSize[] = new int [3];
	
    private static final int EVENT_PBR_LOAD_DONE = 1;
    private static final int EVENT_USIM_ADN_LOAD_DONE = 2;
    private static final int EVENT_IAP_LOAD_DONE = 3;
    private static final int EVENT_EMAIL_LOAD_DONE = 4;
    private static final int EVENT_ADN_RECORD_COUNT = 5;

// zhanglj add begin for add multi record and email in usim
    private static final int EVENT_AAS_LOAD_DONE = 6;
    private static final int EVENT_SNE_LOAD_DONE = 7;
    private static final int EVENT_GRP_LOAD_DONE = 8;
    private static final int EVENT_GAS_LOAD_DONE = 9;
    private static final int EVENT_ANR_LOAD_DONE = 10;
// zhanglj add end
    
    private ArrayList<byte[]> mAnrFileRecord;
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

    private static final int USIM_TYPE1_TAG   = 0xA8;
    private static final int USIM_TYPE2_TAG   = 0xA9;
    private static final int USIM_TYPE3_TAG   = 0xAA;
    private static final int USIM_EFADN_TAG   = 0xC0;
    private static final int USIM_EFIAP_TAG   = 0xC1;
    private static final int USIM_EFEXT1_TAG  = 0xC2;
    public static final int USIM_EFSNE_TAG   = 0xC3;
    public static final int USIM_EFANR_TAG   = 0xC4;
    private static final int USIM_EFPBC_TAG   = 0xC5;
    public static final int USIM_EFGRP_TAG   = 0xC6;
    public static final int USIM_EFAAS_TAG   = 0xC7;
    public static final int USIM_EFGAS_TAG   = 0xC8;
    private static final int USIM_EFUID_TAG   = 0xC9;
    private static final int USIM_EFEMAIL_TAG = 0xCA;
    private static final int USIM_EFCCP1_TAG  = 0xCB;

    public UsimPhoneBookManager(PhoneBase phone,IccFileHandler fh,AdnRecordCache cache) {
        mFh = fh;
	  mPhone = phone;
        mPhoneBookRecords = new ArrayList<AdnRecord>();
        mPbrFile = null;
        // We assume its present, after the first read this is updated.
        // So we don't have to read from UICC if its not present on subsequent reads.
        mIsPbrPresent = true;
        mAdnCache = cache;
        // zhanglj add begin for add multi record and email in usim
        ishaveEmail = false;
        ishaveAnr = false;
        ishaveAas = false;
        ishaveSne = false;
        ishaveGrp = false;
        ishaveGas = false;
        // zhanglj add end
    }

    public void reset() {
        mPhoneBookRecords.clear();
        mIapFileRecord = null;
        mEmailFileRecord = null;
        mPbrFile = null;
        mIsPbrPresent = true;
        // zhanglj add begin for add multi record and email in usim
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
        // zhanglj add end
    }

    public ArrayList<AdnRecord> loadEfFilesFromUsim() {
        synchronized (mLock) {
            if (!mPhoneBookRecords.isEmpty()) return mPhoneBookRecords;
            if (!mIsPbrPresent) return null;

            // Check if the PBR file is present in the cache, if not read it
            // from the USIM.
            Log.i("UsimPhoneBookManager","loadEfFilesFromUsim");
            if (mPbrFile == null) {
                readPbrFileAndWait();
            }

            if (mPbrFile == null) return null;

            int numRecs = mPbrFile.mFileIds.size();

            // zhanglj add begin for add multi record and email in usim
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
            // zhanglj add end

	      mAdnCount = 0;
            Log.i("UsimPhoneBookManager" ,"loadEfFilesFromUsim" +numRecs);	 	
            for (int i = 0; i < numRecs; i++) {
                readAdnFileAndWait(i);
                readEmailFileAndWait(i);

                // zhanglj add begin for add multi record and email in usim
                readAnrFileAndWait(i);
                	readAasFileAndWait(i);
                updatePhoneAdnRecord(i);
                // zhanglj add end
            }
            // All EF files are loaded, post the response.
        }
        return mPhoneBookRecords;
    }

    // zhanglj add begin for add multi record and email in usim
    public int getNumRecs() {
		// zhanglj add begin 2010-11-25 for reload the pbr file when the pbr file is null
		synchronized (mLock) {
			if(mPbrFile == null){
				readPbrFileAndWait();	
			}
			if(mPbrFile == null){
				Log.e(LOG_TAG, "Error: Pbr file is empty");
				return 0;
			}
		}
        return mPbrFile.mFileIds.size();
    }
    
	public ArrayList<byte[]> getIapFileRecord(int recNum){
		int efid = findEFIapInfo(recNum);
		// zhanglj add begin 2010-11-29 for avoid some exception because ril restart
		if(efid < 0){
			return null;
		}

		return (ArrayList<byte[]>)mIapFileRecordArray[recNum];

	}

	public PbrFile getPbrFile(){
		return mPbrFile;
	}

	public void setIapFileRecord(int recNum, int index, byte value){
		
		ArrayList<byte[]> tmpIapFileRecord = (ArrayList<byte[]>)mIapFileRecordArray[recNum];
		byte[] record = tmpIapFileRecord.get(index);
		record[mEmailTagNumberInIap] = value;
		tmpIapFileRecord.set(index, record);
		mIapFileRecordArray[recNum] = tmpIapFileRecord;

	}

	public int getEmailTagNumberInIap(){
		
		return mEmailTagNumberInIap;
	}

	public int getNewEmailNumber(){
		int newEmailNum = -1;
		int numEmailRec = 0;
		try{
			numEmailRec = mEmailRecordSizeArray[getNumRecs()-1];
		}catch (IndexOutOfBoundsException e) {
       		Log.e(LOG_TAG, "Error: Improper ICC card");
		}
		for(int i=1;i<=numEmailRec;i++){
           Integer emailNum = new Integer(i);
           if(!usedEmailNumSet.contains(emailNum)){
               newEmailNum = (int)emailNum;
			   usedEmailNumSet.add(emailNum);
               break;
           }
		}	
		return newEmailNum;
	
	}

	public int[] getEmailRecordSizeArray(){
		return mEmailRecordSizeArray;
	}
	
	public int[] getIapRecordSizeArray(){
		return mIapRecordSizeArray;
	
	}

	public void removeEmailNumFromSet(int recNum){
		Integer emailNum = new Integer(recNum);
		usedEmailNumSet.remove(emailNum);
	}
   
   public void setPhoneBookRecords(int index, AdnRecord adn){
	   mPhoneBookRecords.set(index, adn);
	}

	public int[] getAdnRecordSizeArray(){
		return mAdnRecordSizeArray;	
	}

    public int findEFInfo(int index) {
        Map <Integer,Integer> fileIds;
		synchronized (mLock) {
			if(mPbrFile == null){
				readPbrFileAndWait();	
			}
		}
		if(mPbrFile == null){
			Log.e(LOG_TAG, "Error: Pbr file is empty");
			return -1;
		}
        fileIds = mPbrFile.mFileIds.get(index);
        return fileIds.get(USIM_EFADN_TAG);
    }
    public int findExtensionEFInfo(int index) {
        
        Map <Integer,Integer> fileIds;

		synchronized (mLock) {
			if(mPbrFile == null){
				readPbrFileAndWait();	
			}
		}
		if(mPbrFile == null){
			Log.e(LOG_TAG, "Error: Pbr file is empty");
			return -1;
		}
        fileIds = mPbrFile.mFileIds.get(index);
		
        return fileIds.get(USIM_EFEXT1_TAG);  

    }

    public int findEFEmailInfo(int index) {
        
        Map <Integer,Integer> fileIds;

		synchronized (mLock) {
			if(mPbrFile == null){
				readPbrFileAndWait();	
			}
		}
		if(mPbrFile == null){
			Log.e(LOG_TAG, "Error: Pbr file is empty");
			return -1;
		}
        fileIds = mPbrFile.mFileIds.get(index);

        return fileIds.get(USIM_EFEMAIL_TAG);
    }

    public int findEFSneInfo(int index) {
        
        Map <Integer,Integer> fileIds;
		//for reload the pbr file when the pbr file is null
		synchronized (mLock) {
			if(mPbrFile == null){
				readPbrFileAndWait();
			}
		}
		if(mPbrFile == null){
			Log.e(LOG_TAG, "Error: Pbr file is empty");
			return -1;
		}
        fileIds = mPbrFile.mFileIds.get(index);

        return fileIds.get(USIM_EFSNE_TAG);
    }

    public int findEFAnrInfo(int index) {
        
        Map <Integer,Integer> fileIds;
		// for reload the pbr file when the pbr file is null
		synchronized (mLock) {
			if(mPbrFile == null){
				readPbrFileAndWait();	
			}
		}
		if(mPbrFile == null){
			Log.e(LOG_TAG, "Error: Pbr file is empty");
			return -1;
		}
        fileIds = mPbrFile.mFileIds.get(index);

        return fileIds.get(USIM_EFANR_TAG);
    }
	public int findEFAasInfo(int index) {
		   
		   Map <Integer,Integer> fileIds;
		   //for reload the pbr file when the pbr file is null
		   synchronized (mLock) {
			   if(mPbrFile == null){
				   readPbrFileAndWait();   
			   }
		   }
		   if(mPbrFile == null){
			   Log.e(LOG_TAG, "Error: Pbr file is empty");
			   return -1;
		   }
		   fileIds = mPbrFile.mFileIds.get(index);
	
		   return fileIds.get(USIM_EFAAS_TAG);
	   }

public int findEFGrpInfo(int index) {
	   
	   Map <Integer,Integer> fileIds;
	   //for reload the pbr file when the pbr file is null
	   synchronized (mLock) {
		   if(mPbrFile == null){
			   readPbrFileAndWait();   
		   }
	   }
	   if(mPbrFile == null){
		   Log.e(LOG_TAG, "Error: Pbr file is empty");
		   return -1;
	   }
	   fileIds = mPbrFile.mFileIds.get(index);

	   return fileIds.get(USIM_EFGRP_TAG);
   }
public int findEFGasInfo(int index) {
	   
	   Map <Integer,Integer> fileIds;
	   //for reload the pbr file when the pbr file is null
	   synchronized (mLock) {
		   if(mPbrFile == null){
			   readPbrFileAndWait();   
		   }
	   }
	   if(mPbrFile == null){
		   Log.e(LOG_TAG, "Error: Pbr file is empty");
		   return -1;
	   }
	   fileIds = mPbrFile.mFileIds.get(index);

	   return fileIds.get(USIM_EFGAS_TAG);
   }


    public int findEFIapInfo(int index) {
        
        Map <Integer,Integer> fileIds;

		synchronized (mLock) {
			if(mPbrFile == null){
				readPbrFileAndWait();	
			}
		}
		if(mPbrFile == null){
			Log.e(LOG_TAG, "Error: Pbr file is empty");
			return -1;
		}
        fileIds = mPbrFile.mFileIds.get(index);

        return fileIds.get(USIM_EFIAP_TAG);

    }

// zhanglj add end

    public int[] getAdnRecordsSize(int efid) {
       
      
          Log.i("UsimPhoneBookManager" ,"getEFLinearRecordSize" +efid);	 
          
	   recordSize[2] =  mAdnCount; 
	/*   synchronized (mLock) {
          
            if (mPbrFile == null) {
                readPbrFileAndWait();
            }

            if (mPbrFile == null) return null;

            int numRecs = mPbrFile.mFileIds.size();
            Log.i("UsimPhoneBookManager" ,"loadEfFilesFromUsim" +numRecs);	 
	      mAdnCount = 0;
             mAdnSize = 0;
            for (int i = 0; i < numRecs; i++) {
                readAdnFileSizeAndWait(i);
               
            }
            // All EF files are loaded, post the response.
        }*/
      
        return recordSize;
    }


    
    private int readAdnFileSizeAndWait(int recNum) {
        Map <Integer,Integer> fileIds;
        fileIds = mPbrFile.mFileIds.get(recNum);
	  Log.i("UsimPhoneBookManager" ,"recNum" +recNum);
	  
        if (fileIds == null || fileIds.isEmpty()) return 0;

            mPhone.getIccFileHandler().getEFLinearRecordSize(fileIds.get(USIM_EFADN_TAG),
             obtainMessage(EVENT_ADN_RECORD_COUNT));
        try {
            mLock.wait();
        } catch (InterruptedException e) {
            Log.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
        }
          Log.i("UsimPhoneBookManager" ,"mAdnCount" +mAdnCount);	 
	  return 	mAdnCount;
    }
     public int[] getEfFilesFromUsim() {

	   int[] efids = null;

	  int len = 0;

	  

	   len = mPbrFile.mFileIds.size();
          Log.i("UsimPhoneBookManager" ,"getEfFilesFromUsim" +len );	 
	   efids = new int [len];

	   for(int i=0; i<len; i++){
              Map <Integer,Integer> fileIds = mPbrFile.mFileIds.get(i);
               efids[i]= fileIds.get(USIM_EFADN_TAG);
		  Log.i("UsimPhoneBookManager" ,"getEfFilesFromUsim" +efids[i] );	 
	   }
	   
         return efids;
    }

    private void readPbrFileAndWait() {
        Log.i("UsimPhoneBookManager","readPbrFileAndWait");
        mPhone.getIccFileHandler().loadEFLinearFixedAll(EF_PBR, obtainMessage(EVENT_PBR_LOAD_DONE));
        try {
            mLock.wait();
        } catch (InterruptedException e) {
            Log.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
        }
    }
    private void readAdnFileAndWait(int recNum) {
		 Log.i("UsimPhoneBookManager","readAdnFileAndWait");
		synchronized (mLock) {
			if(mPbrFile == null){
				readPbrFileAndWait();	
			}
		}
		if(mPbrFile == null){
			Log.e(LOG_TAG, "Error: Pbr file is empty");
          return;
		}
		//END 20110413       
        Map <Integer,Integer> fileIds;
        fileIds = mPbrFile.mFileIds.get(recNum);
        if (fileIds == null || fileIds.isEmpty()) return;


        int extEf = 0;
        // Only call fileIds.get while EFEXT1_TAG is available
        if (fileIds.containsKey(USIM_EFEXT1_TAG)) {
            extEf = fileIds.get(USIM_EFEXT1_TAG);
        }

        mAdnCache.requestLoadAllAdnLike(fileIds.get(USIM_EFADN_TAG),
            extEf, obtainMessage(EVENT_USIM_ADN_LOAD_DONE));
        try {
            mLock.wait();
        } catch (InterruptedException e) {
            Log.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
        }
    }
    private void readEmailFileAndWait(int recNum) {
            	Log.i("UsimPhoneBookManager","readEmailFileAndWait");
		synchronized (mLock) {
			if(mPbrFile == null){
				readPbrFileAndWait();	
			}
		}
		if(mPbrFile == null){
			Log.e(LOG_TAG, "Error: Pbr file is empty");
			return;
		}
        Map <Integer,Integer> fileIds;
        fileIds = mPbrFile.mFileIds.get(recNum);
        if (fileIds == null) return;

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
            mPhone.getIccFileHandler().loadEFLinearFixedAll(fileIds.get(USIM_EFEMAIL_TAG),
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
            //updatePhoneAdnRecord(recNum);
        }

    }

    private void readIapFileAndWait(int efid) {
	  Log.i("UsimPhoneBookManager","readIapFileAndWait");
        mPhone.getIccFileHandler().loadEFLinearFixedAll(efid, obtainMessage(EVENT_IAP_LOAD_DONE));
        try {
            mLock.wait();
        } catch (InterruptedException e) {
            Log.e(LOG_TAG, "Interrupted Exception in readIapFileAndWait");
        }
    }

private void readAnrFileAndWait(int recNum) {
	 Log.i("UsimPhoneBookManager","readAnrFileAndWait");
	 synchronized (mLock) {
		 if(mPbrFile == null){
			 readPbrFileAndWait();	 
		 }
	 }
	 if(mPbrFile == null){
		 Log.e(LOG_TAG, "Error: Pbr file is empty");
		 return;
	 }
	 log("readAnrFileAndWait recNum is	" + recNum);
	 Map <Integer,Integer> fileIds;
	 fileIds = mPbrFile.mFileIds.get(recNum);
	 
 // print hashmap key and value
 /*
 Iterator iter = fileIds.entrySet().iterator();
 while(iter.hasNext())
 {
	 Map.Entry entry = (Map.Entry) iter.next();
	 Object key = entry.getKey();
	 Object value = entry.getValue();
	 Log.e("yuyong","key= "+key + "   value=" + value);
 }	 
 */
	 if (fileIds == null) return;
	 if (fileIds.containsKey(USIM_EFANR_TAG)) {
	 	ishaveAnr = true;
		 ArrayList<Integer> efids = mPbrFile.getFileId(recNum, USIM_EFANR_TAG); 	
	 	 if(efids.size()==0) {
			 return;	   
	 	}
		 anrFileCount = efids.size();
		 log("efids =  " +efids);

		 // Read the anr file.
		 for(int i =0; i<anrFileCount; i++)
		 {
			 log("anr efids.get(i) is "+efids.get(i));
			 mFh.loadEFLinearFixedAll(efids.get(i), obtainMessage(EVENT_ANR_LOAD_DONE));
			 try {
		 		mLock.wait();
			 } catch (InterruptedException e) {
				 Log.e(LOG_TAG, "Interrupted Exception in readEmailFileAndWait");
			 }
			 log("load ANR times ...... "+(i+1));
	 		if (mAnrFileRecord == null) {
		 		Log.e(LOG_TAG, "Error: Email file is empty");
		 		return;
	 		}
		 }
	 }

 }

private void readSneFileAndWait(int recNum) {
	    Log.i("UsimPhoneBookManager","readSneFileAndWait");
	   synchronized (mLock) {
		   if(mPbrFile == null){
			   readPbrFileAndWait();   
		   }
	   }
	   if(mPbrFile == null){
		   Log.e(LOG_TAG, "Error: Pbr file is empty");
		   return;
	   }
  	 log("readSneFileAndWait recNum is  " + recNum);
	   Map <Integer,Integer> fileIds;
	   fileIds = mPbrFile.mFileIds.get(recNum);
	   if (fileIds == null) return;

	if (fileIds.containsKey(USIM_EFSNE_TAG)) {
		   ishaveSne = true;
		   int efid = fileIds.get(USIM_EFSNE_TAG);
		   log("sne efid is "+efid);
		  
		   // Read the sne file.
		  mFh.loadEFLinearFixedAll(fileIds.get(USIM_EFSNE_TAG),
				   obtainMessage(EVENT_SNE_LOAD_DONE));
		   try {
			   mLock.wait();
		   } catch (InterruptedException e) {
			   Log.e(LOG_TAG, "Interrupted Exception in readEmailFileAndWait");
		   }

		   if (mSneFileRecord == null) {
			   Log.e(LOG_TAG, "Error: Email file is empty");
			   return;
		   }
	   }
   }


private void readGrpFileAndWait(int recNum) {
	    Log.i("UsimPhoneBookManager","readGrpFileAndWait");
	synchronized (mLock) {
		if(mPbrFile == null){
			readPbrFileAndWait();	
		}
	}
	if(mPbrFile == null){
		Log.e(LOG_TAG, "Error: Pbr file is empty");
		return;
	}
	log("readGrpFileAndWait recNum is  " + recNum);
	Map <Integer,Integer> fileIds;
	fileIds = mPbrFile.mFileIds.get(recNum);
	if (fileIds == null) return;

	if (fileIds.containsKey(USIM_EFGRP_TAG)) {
		ishaveGrp = true;
		int efid = fileIds.get(USIM_EFGRP_TAG);
		log("grp efid is "+efid);
	   
		// Read the grp file.
		mFh.loadEFLinearFixedAll(fileIds.get(USIM_EFGRP_TAG),obtainMessage(EVENT_GRP_LOAD_DONE));
		try {
			mLock.wait();
		} catch (InterruptedException e) {
			Log.e(LOG_TAG, "Interrupted Exception in readEmailFileAndWait");
		}

		if (mGrpFileRecord == null) {
			Log.e(LOG_TAG, "Error: Email file is empty");
			return;
		}
	}

}

private void readGasFileAndWait(int recNum) {
	Log.i("UsimPhoneBookManager","readGasFileAndWait");
	synchronized (mLock) {
		if(mPbrFile == null){
			readPbrFileAndWait();	
		}
	}
	if(mPbrFile == null){
		Log.e(LOG_TAG, "Error: Pbr file is empty");
		return;
	}
	log("readGasFileAndWait recNum is  " + recNum);
	Map <Integer,Integer> fileIds;
	fileIds = mPbrFile.mFileIds.get(recNum);
	if (fileIds == null) return;

	if (fileIds.containsKey(USIM_EFGAS_TAG)) {
		ishaveGas = true;
		int efid = fileIds.get(USIM_EFGAS_TAG);
		log("gas efid is "+efid);
	   
		// Read the gas file.
		mFh.loadEFLinearFixedAll(fileIds.get(USIM_EFGAS_TAG),obtainMessage(EVENT_GAS_LOAD_DONE));
		try {
			mLock.wait();
		} catch (InterruptedException e) {
			Log.e(LOG_TAG, "Interrupted Exception in readEmailFileAndWait");
		}

		if (mGasFileRecord == null) {
			Log.e(LOG_TAG, "Error: Email file is empty");
			return;
		}
	}

}
private void readAasFileAndWait(int recNum) {
	Log.i("UsimPhoneBookManager","readAasFileAndWait");
	synchronized (mLock) {
		if(mPbrFile == null){
			readPbrFileAndWait();	
		}
	}
	if(mPbrFile == null){
		Log.e(LOG_TAG, "Error: Pbr file is empty");
		return;
	}
	log("readAasFileAndWait recNum is  " + recNum);
	Map <Integer,Integer> fileIds;
	fileIds = mPbrFile.mFileIds.get(recNum);
	if (fileIds == null) return;

	if (fileIds.containsKey(USIM_EFAAS_TAG)) {
		
		ishaveAas = true;
		int efid = fileIds.get(USIM_EFAAS_TAG);
		log("aas efid is "+efid);
	  	// Read the aas file.
		mFh.loadEFLinearFixedAll(fileIds.get(USIM_EFAAS_TAG),obtainMessage(EVENT_AAS_LOAD_DONE));
		try {
			mLock.wait();
		} catch (InterruptedException e) {
			Log.e(LOG_TAG, "Interrupted Exception in readEmailFileAndWait");
		}

		if (mAasFileRecord == null) {
			Log.e(LOG_TAG, "Error: Email file is empty");
			return;
		}
	}

}

    private void updatePhoneAdnRecord() {
        if (mEmailFileRecord == null) return;
        int numAdnRecs = mPhoneBookRecords.size();
        if (mIapFileRecord != null) {
            // The number of records in the IAP file is same as the number of records in ADN file.
            // The order of the pointers in an EFIAP shall be the same as the order of file IDs
            // that appear in the TLV object indicated by Tag 'A9' in the reference file record.
            // i.e value of mEmailTagNumberInIap

            for (int i = 0; i < numAdnRecs; i++) {
                byte[] record = null;
                try {
                    record = mIapFileRecord.get(i);
                } catch (IndexOutOfBoundsException e) {
                    Log.e(LOG_TAG, "Error: Improper ICC card: No IAP record for ADN, continuing");
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
            if (emailList == null) continue;

            AdnRecord rec = mPhoneBookRecords.get(i);

            String[] emails = new String[emailList.size()];
            System.arraycopy(emailList.toArray(), 0, emails, 0, emailList.size());
            rec.setEmails(emails);
            mPhoneBookRecords.set(i, rec);
        }
    }

    private void updatePhoneAdnRecord(int num) {
        if (mEmailFileRecord == null) return;
        int numAdnRecs = mPhoneBookRecords.size();
  
	mAdnRecordSizeArray[num] = mPhoneBookRecords.size();
	 Log.i("LOG_TAG","updatePhoneAdnRecord mAdnRecordSizeArray[num] : "+numAdnRecs + "num "+num );
	//yuyong 20110505 add for usim phonebook
         int numIapRec = 0;
	//add end
	  Log.i("LOG_TAG","updatePhoneAdnRecord mIapFileRecord : " +mIapFileRecord );
        if (mIapFileRecord != null) {
            // The number of records in the IAP file is same as the number of records in ADN file.
            // The order of the pointers in an EFIAP shall be the same as the order of file IDs
            // that appear in the TLV object indicated by Tag 'A9' in the reference file record.
            // i.e value of mEmailTagNumberInIap

			numIapRec = mIapFileRecord.size();
			mIapFileRecordArray[num] = mIapFileRecord;
			mEmailRecordSizeArray[num] = mEmailFileRecord.size();
			mIapRecordSizeArray[num] = mIapFileRecord.size();

			mAnrRecordSizeArray[num] = mAnrFileRecord.size();
			mAasRecordSizeArray[num] = mAasFileRecord.size();
 
            for (int i = 0; i < numIapRec; i++) {
                byte[] record = null;
                try {
                    record = mIapFileRecord.get(i);
                } catch (IndexOutOfBoundsException e) {
                    Log.e(LOG_TAG, "Error: Improper ICC card: No IAP record for ADN, continuing");
                    break;
                }
                int recNum = (int) (record[mEmailTagNumberInIap]& 0xFF);
				recNum = ((recNum ==0xFF)? (-1):recNum);
					//Log.e(LOG_TAG,"iap recNum=="+recNum);
                if (recNum != -1) {
                    // zhanglj add 2010-11-01 for add email function in usim card
                    usedEmailNumSet.add(new Integer(recNum));

                    String[] emails = new String[1];
                    // SIM record numbers are 1 based
                    emails[0] = readEmailRecord(recNum - 1);
					if (emails[0] == null)
					{emails[0] = " ";Log.e(LOG_TAG,"emails[0]==null");}
                    // zhanglj modify begin 2010-11-01 for add email function in usim card
                    int adnNum = numAdnRecs - numIapRec +i;
                    // AdnRecord rec = mPhoneBookRecords.get(i);
                    AdnRecord rec = mPhoneBookRecords.get(adnNum);
                    // zhanglj modify end
                    if (rec != null) {
                        rec.setEmails(emails);
                    } else {
                        // might be a record with only email
                        rec = new AdnRecord("", "", emails);
                    }
                    // zhanglj modify 2010-11-01 for add email function in usim card
                    // mPhoneBookRecords.set(i, rec);
                    mPhoneBookRecords.set(adnNum, rec);
                }
            }
        }else{
        mIapFileRecordArray[num] = mIapFileRecord;
        mEmailRecordSizeArray[num] = mEmailFileRecord.size();
        mIapRecordSizeArray[num] = mIapFileRecord.size();
	 mAnrRecordSizeArray[num] = mAnrFileRecord.size();
	 mAasRecordSizeArray[num] = mAasFileRecord.size();
        }
   
      Log.i(LOG_TAG,"updatePhoneAdnRecord mEmailRecordSizeArray[num] : " + mEmailFileRecord.size() );
      Log.i(LOG_TAG,"updatePhoneAdnRecord mIapRecordSizeArray[num] : " + mIapFileRecord.size() );
      Log.i(LOG_TAG,"updatePhoneAdnRecord mAnrRecordSizeArray[num] : " + mAnrFileRecord.size() );
      Log.i(LOG_TAG,"updatePhoneAdnRecord mAasRecordSizeArray[num] : " + mAasFileRecord.size() );
        // ICC cards can be made such that they have an IAP file but all
        // records are empty. So we read both type 1 and type 2 file
        // email records, just to be sure.
        // zhanglj modify 2010-11-01 for add email function in usim card
        // int len = mPhoneBookRecords.size();
        int len = mEmailFileRecord.size();
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
            if (emailList == null) continue;

            AdnRecord rec = mPhoneBookRecords.get(i);

            String[] emails = new String[emailList.size()];
            System.arraycopy(emailList.toArray(), 0, emails, 0, emailList.size());
            rec.setEmails(emails);
            mPhoneBookRecords.set(i, rec);
        }
	//yuyong 20110505  add for usim phonebook
	byte[] aasIndex = new byte[3];
	aasIndex[0] = 0;
	if(num > 0 && ishaveAnr)
	{
		for (int i = 0; i < mAnrFileRecord.size()/anrFileCount; i++) {
                    // SIM record numbers are 1 based
                    String anr = null;
                    anr = readAnrRecord(i);
		    aasIndex =  readAasIndex(i);
		   String aas = null;

		   if(aasIndex[0] != 0)
		   {	
			 aas = readAasRecord(aasIndex[0]);
		   }
                    int adnNum = numAdnRecs - numIapRec +i;
                    AdnRecord rec = mPhoneBookRecords.get(adnNum);
                    if (rec != null) {
                        rec.setAnr(anr);
			rec.setAas(aas);
                    } else {
                        // might be a record with only anr
                        //rec = new AdnRecord("", "", null, anr, "", "", "", "");
                    }
                    mPhoneBookRecords.set(adnNum, rec);
            }
	}


	if(num >= 0 && ishaveAas)
	{
		for (int i = 0; i < mAasFileRecord.size(); i++) {
                
                    String aas = null;
                    // SIM record numbers are 1 based
                    aas = readAasRecord(i);
		    if(TextUtils.isEmpty(aas)) break;
			
                    int adnNum = numAdnRecs - numIapRec +i;
		    AdnRecord rec = mPhoneBookRecords.get(adnNum);   
		    
                    if (rec != null) {
                        rec.setAas(aas);
                    } else {
                        // might be a record with only aas
                        //rec = new AdnRecord("", "", "", "", aas);
                    }
                    mPhoneBookRecords.set(adnNum, rec);					
            }
	}	
/*
	if( ishaveGas)
	{
		for (int i = 0; i < mGasFileRecord.size(); i++) {
		
		    String gas = null;
                    // SIM record numbers are 1 based
                    gas = readGasRecord(i);
		    if(TextUtils.isEmpty(gas)) break;
                    int adnNum = numAdnRecs - numIapRec +i;
                    AdnRecord rec = mPhoneBookRecords.get(adnNum);
                    if (rec != null) {
                        rec.setGas(gas);
                    } else {
                        // might be a record with only gas
                        //rec = new AdnRecord("", "", "", "", "", "", gas);
                    }
                    mPhoneBookRecords.set(adnNum, rec);	   
            }
	}

	if(ishaveSne)
	{
		for (int i = 0; i < mSneFileRecord.size(); i++) {
	                    String sne = null;
	                    // SIM record numbers are 1 based
	                    sne = readSneRecord(i);
	                    int adnNum = numAdnRecs - numIapRec +i;
	                    AdnRecord rec = mPhoneBookRecords.get(adnNum);
	                    if (rec != null) {
	                        rec.setSne(sne);
	                    } else {
	                        // might be a record with only sne
	                        //rec = new AdnRecord("", "", null, "", "", sne, "", "");
	                    }
	                    mPhoneBookRecords.set(adnNum, rec);
	            }
	}
	
	if(ishaveGrp)
	{
		for (int i = 0; i < mGrpFileRecord.size(); i++) {
               
                    String grp = null;
	                    // SIM record numbers are 1 based
	                    grp = readGrpRecord(i);
			    if(TextUtils.isEmpty(grp)) break;
	                    int adnNum = numAdnRecs - numIapRec +i;
	                    AdnRecord rec = mPhoneBookRecords.get(adnNum);
	                    if (rec != null) {
	                        rec.setGrp(grp);
	                    } else {
	                        // might be a record with only grp
	                        //rec = new AdnRecord("", "", "", "", "", "", grp);
	                    }
	                    mPhoneBookRecords.set(adnNum, rec);
	            }
	}
	*/

    }

    void parseType1EmailFile(int numRecs) {
        mEmailsForAdnRec = new HashMap<Integer, ArrayList<String>>();
        byte[] emailRec = null;
        for (int i = 0; i < numRecs; i++) {
            try {
                emailRec = mEmailFileRecord.get(i);
            } catch (IndexOutOfBoundsException e) {
                Log.e(LOG_TAG, "Error: Improper ICC card: No email record for ADN, continuing");
                break;
            }
            int adnRecNum = emailRec[emailRec.length - 1];

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

        // The length of the record is X+2 byte, where X bytes is the email address
        String email = IccUtils.adnStringFieldToString(emailRec, 0, emailRec.length - 2);
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
	
	int firstAnrFileRecordCount = mAnrRecordSizeArray[0]/anrFileCount;
	//log("firstAnrFileRecordCount is "+firstAnrFileRecordCount);
	if(anrFileCount == 0x1)
	{
		anr1Rec = mAnrFileRecord.get(recNum);
		anr = PhoneNumberUtils.calledPartyBCDToString(anr1Rec, 2, (0xff & anr1Rec[2]));   
		log("readAnrRecord--anr:" + anr);    
		return anr;
	}
	else
	{
		if(recNum < firstAnrFileRecordCount)
		{
		        try {
		            anr1Rec = mAnrFileRecord.get(recNum);
			    anr2Rec = mAnrFileRecord.get(recNum + firstAnrFileRecordCount);
			    if(anrFileCount > 0x2)
			    {
			    	anr3Rec = mAnrFileRecord.get(recNum + 2*firstAnrFileRecordCount);
			    }
			    
		        } catch (IndexOutOfBoundsException e) {
		            return null;
		        }

		        // The total length is 17 byte, 10 byte of them is anr number
			//log("before parse--anr1Rec  is :" +IccUtils.bytesToHexString(anr1Rec)); 

		        anr1 = PhoneNumberUtils.calledPartyBCDToString(anr1Rec, 2, (0xff & anr1Rec[2]));   
		        anr2 = PhoneNumberUtils.calledPartyBCDToString(anr2Rec, 2, (0xff & anr2Rec[2]));   
			if(anrFileCount > 0x2)
		        {
		        	anr3 = PhoneNumberUtils.calledPartyBCDToString(anr3Rec, 2, (0xff & anr3Rec[2]));   
				anr = anr1 +";"+ anr2 +";"+ anr3;
				log("readAnrRecord--anr:" + anr);    
	       			return anr;
			}
		}
		else if(recNum >= firstAnrFileRecordCount && recNum < mAnrFileRecord.size()/anrFileCount)
		{
			int secondAnrFileRecordCount = (mAnrFileRecord.size() -mAnrRecordSizeArray[0])/anrFileCount;
			//log("secondAnrFileRecordCount is "+secondAnrFileRecordCount);
			try {
			    int secondAnrfileread = mAnrRecordSizeArray[0] + recNum%firstAnrFileRecordCount;
		            anr1Rec = mAnrFileRecord.get(secondAnrfileread);
			    anr2Rec = mAnrFileRecord.get(secondAnrfileread + secondAnrFileRecordCount);
			    if(anrFileCount > 0x2)
			    {
			   	anr3Rec = mAnrFileRecord.get(secondAnrfileread + 2*secondAnrFileRecordCount);
			    }
		        } catch (IndexOutOfBoundsException e) {
		            return null;
		        }
		        anr1 = PhoneNumberUtils.calledPartyBCDToString(anr1Rec, 2, (0xff & anr1Rec[2]));   
		        anr2 = PhoneNumberUtils.calledPartyBCDToString(anr2Rec, 2, (0xff & anr2Rec[2]));   
		        if(anrFileCount > 0x2)
		        {
		        	anr3 = PhoneNumberUtils.calledPartyBCDToString(anr3Rec, 2, (0xff & anr3Rec[2]));   
				anr = anr1 +";"+ anr2 +";"+ anr3;
				log("readAnrRecord--anr:" + anr);    
	       			return anr;
			}   
		}
		else
		{
			log("the total anr size is exceed mAnrFileRecord.size()  "+mAnrFileRecord.size());
		}
		anr = anr1 +";"+ anr2;
		log("readAnrRecord--anr:" + anr);    
	        return anr;  
	}
    }
    private String readAasRecord(int recNum) {
        byte[] aasRec = null;
        try {
            aasRec = mAasFileRecord.get(recNum);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }

        // The length of the record is 15 byte
        String aas = IccUtils.adnStringFieldToString(aasRec, 0, aasRec.length);
        return aas;
    }

    private byte[] readAasIndex(int recNum) {
        byte[] anr1Rec = null;
	byte[] anr2Rec = null;
	byte[] anr3Rec = null;
	String anr1 = null;
	String anr2 = null;
	String anr3 = null;
	String anr = null;
	byte[]   aasIndex = new byte[3];
	int firstAnrFileRecordCount = mAnrRecordSizeArray[0]/anrFileCount;
	//log("firstAnrFileRecordCount is "+firstAnrFileRecordCount);
	if(anrFileCount == 0x1)
	{
		anr1Rec = mAnrFileRecord.get(recNum);
		aasIndex[0] = anr1Rec[0];
		return aasIndex;
	}
	else
	{
		if(recNum < firstAnrFileRecordCount)
		{
		        try {
		            anr1Rec = mAnrFileRecord.get(recNum);
			    anr2Rec = mAnrFileRecord.get(recNum + firstAnrFileRecordCount);
			    if(anrFileCount > 0x2)
			    {
			    	anr3Rec = mAnrFileRecord.get(recNum + 2*firstAnrFileRecordCount);
			    }
			    
		        } catch (IndexOutOfBoundsException e) {
		            return null;
		        }

		        // The total length is 17 byte, 10 byte of them is anr number
			//log("before parse--anr1Rec  is :" +IccUtils.bytesToHexString(anr1Rec)); 

		       // anr1 = PhoneNumberUtils.calledPartyBCDToString(anr1Rec, 2, (0xff & anr1Rec[2]));   
		        //anr2 = PhoneNumberUtils.calledPartyBCDToString(anr2Rec, 2, (0xff & anr2Rec[2]));  
			aasIndex[0] = anr1Rec[0];
			aasIndex[1] = anr2Rec[0];
			if(anrFileCount > 0x2)
		        {
		        	aasIndex[2] = anr3Rec[0];
		
	       			return aasIndex;
			}
		}
		else if(recNum >= firstAnrFileRecordCount && recNum < mAnrFileRecord.size()/anrFileCount)
		{
			int secondAnrFileRecordCount = (mAnrFileRecord.size() -mAnrRecordSizeArray[0])/anrFileCount;
			//log("secondAnrFileRecordCount is "+secondAnrFileRecordCount);
			try {
			    int secondAnrfileread = mAnrRecordSizeArray[0] + recNum%firstAnrFileRecordCount;
		            anr1Rec = mAnrFileRecord.get(secondAnrfileread);
			    anr2Rec = mAnrFileRecord.get(secondAnrfileread + secondAnrFileRecordCount);
			    aasIndex[0] = anr1Rec[0];
			    aasIndex[1] = anr2Rec[0];
			    if(anrFileCount > 0x2)
			    {
			   	anr3Rec = mAnrFileRecord.get(secondAnrfileread + 2*secondAnrFileRecordCount);
				aasIndex[2] = anr3Rec[0];
			    }
		        } catch (IndexOutOfBoundsException e) {
		            return null;
		        }
		   
	       	        return aasIndex;

		}
		else
		{
			log("the total anr size is exceed mAnrFileRecord.size()  "+mAnrFileRecord.size());
		}
	
	        return aasIndex;  
	}
    }

    private String readSneRecord(int recNum) {
        byte[] sneRec = null;
        try {
            sneRec = mSneFileRecord.get(recNum);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }

        // The length of the record is 17 byte
        String sne = IccUtils.adnStringFieldToString(sneRec, 0, sneRec.length -2);
	log("readSneRecord--sne:" + sne);   
        return sne;
    }
    private String readGrpRecord(int recNum) {
        byte[] grpRec = null;
        try {
            grpRec = mGrpFileRecord.get(recNum);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }

        // The length of the record is 8 byte
        String grp = IccUtils.adnStringFieldToString(grpRec, 0, grpRec.length);
        return grp;
    }
    private String readGasRecord(int recNum) {
        byte[] gasRec = null;
        try {
            gasRec = mGasFileRecord.get(recNum);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }

        // The length of the record is 15 byte
        String gas = IccUtils.adnStringFieldToString(gasRec, 0, gasRec.length);
        return gas;
    }



    private void g(int recNum) {
		synchronized (mLock) {
			if(mPbrFile == null){
				readPbrFileAndWait();	
			}
		}
		if(mPbrFile == null){
			Log.e(LOG_TAG, "Error: Pbr file is empty");
          return;
		}
        Map <Integer,Integer> fileIds;
        fileIds = mPbrFile.mFileIds.get(recNum);
        if (fileIds == null || fileIds.isEmpty()) return;

        mAdnCache.requestLoadAllAdnLike(fileIds.get(USIM_EFADN_TAG),
            fileIds.get(USIM_EFEXT1_TAG), obtainMessage(EVENT_USIM_ADN_LOAD_DONE));
        try {
            mLock.wait();
        } catch (InterruptedException e) {
            Log.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
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

        switch(msg.what) {
        case EVENT_PBR_LOAD_DONE:
            ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                createPbrFile((ArrayList<byte[]>)ar.result);
            }
            synchronized (mLock) {
                mLock.notify();
            }
            break;
        case EVENT_USIM_ADN_LOAD_DONE:
            log("Loading USIM ADN records done");
            ar = (AsyncResult) msg.obj;
	      int size = ((ArrayList<AdnRecord>)ar.result).size();
	      log("EVENT_USIM_ADN_LOAD_DONE size"+ size);
	      mAdnCount += size;
            if (ar.exception == null) {
                mPhoneBookRecords.addAll((ArrayList<AdnRecord>)ar.result);
            }
            synchronized (mLock) {
                mLock.notify();
            }
            break;
        case EVENT_IAP_LOAD_DONE:
            log("Loading USIM IAP records done");
            ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                if(mIapFileRecord == null){
					    mIapFileRecord = new ArrayList<byte[]>();	
                }
				   mIapFileRecord.addAll((ArrayList<byte[]>)ar.result);
            }
            synchronized (mLock) {
                mLock.notify();
            }
            break;

	case EVENT_ANR_LOAD_DONE:
		log("Loading USIM ANR records done");
		ar = (AsyncResult) msg.obj;
		if (ar.exception == null) {
			if(mAnrFileRecord == null){
				mAnrFileRecord = new ArrayList<byte[]>();
			}
			mAnrFileRecord.addAll((ArrayList<byte[]>)ar.result);
			/*
			for(int i = 0; i < mAnrFileRecord.size(); i ++)
			{
				byte[] test = mAnrFileRecord.get(i);
				Log.e("yuyong555", "anr record: "+IccUtils.bytesToHexString(test) );
			}
			*/
			log("mAnrFileRecord.size() is "+mAnrFileRecord.size());
		}

		synchronized (mLock) {
			mLock.notify();
		}
		break;
	case EVENT_SNE_LOAD_DONE:
			log("Loading USIM SNE records done");
			ar = (AsyncResult) msg.obj;
			if (ar.exception == null) {
				if(mSneFileRecord == null){
					mSneFileRecord = new ArrayList<byte[]>();
				}
				mSneFileRecord.addAll((ArrayList<byte[]>)ar.result);
			}

			synchronized (mLock) {
				mLock.notify();
			}
			break;
	case EVENT_AAS_LOAD_DONE:
			log("Loading USIM AAS records done");
			ar = (AsyncResult) msg.obj;
			if (ar.exception == null) {
				if(mAasFileRecord == null){
					mAasFileRecord = new ArrayList<byte[]>();
				}
				
				mAasFileRecord.addAll((ArrayList<byte[]>)ar.result);
/*
				 for(int i = 0; i < mAasFileRecord.size(); i ++)
			{
				byte[] test = mAasFileRecord.get(i);
				Log.e("yuyong111", "anr record: "+IccUtils.bytesToHexString(test) );
			}*/
				}
			synchronized (mLock) {
				mLock.notify();
			}
				
			break;
	case EVENT_GRP_LOAD_DONE:
			log("Loading USIM GRP records done");
			ar = (AsyncResult) msg.obj;
			if (ar.exception == null) {
				if(mGrpFileRecord == null){
					mGrpFileRecord = new ArrayList<byte[]>();
				}
				mGrpFileRecord.addAll((ArrayList<byte[]>)ar.result);
			}

			synchronized (mLock) {
				mLock.notify();
			}
			break;
	case EVENT_GAS_LOAD_DONE:
			log("Loading USIM GAS records done");
			ar = (AsyncResult) msg.obj;
			if (ar.exception == null) {
				if(mGasFileRecord == null){
					mGasFileRecord = new ArrayList<byte[]>();
				}
				mGasFileRecord.addAll((ArrayList<byte[]>)ar.result);
			}

			synchronized (mLock) {
				mLock.notify();
			}
			break;
        case EVENT_EMAIL_LOAD_DONE:
            log("Loading USIM Email records done");
            ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                if(mEmailFileRecord == null){
                    mEmailFileRecord = new ArrayList<byte[]>();
                }
				mEmailFileRecord.addAll((ArrayList<byte[]>)ar.result);
            }

            synchronized (mLock) {
                mLock.notify();
            }
            break;
	  case EVENT_ADN_RECORD_COUNT:
	      Log.i(LOG_TAG,"Loading EVENT_ADN_RECORD_COUNT");
             ar = (AsyncResult) msg.obj;
             synchronized (mLock) {
                        if (ar.exception == null) {
                            recordSize = (int[])ar.result;
                            // recordSize[0]  is the record length
                            // recordSize[1]  is the total length of the EF file
                            // recordSize[2]  is the number of records in the EF file
                            Log.i(LOG_TAG,"EVENT_ADN_RECORD_COUNT Size " + recordSize[0] +
                                    " total " + recordSize[1] +
                                    " #record " + recordSize[2]);
				  mAdnCount +=   recordSize[2];
                            mAdnSize += recordSize[1];
				  recordSize[2] =  mAdnCount;
				  recordSize[1] = mAdnSize;
                            mLock.notifyAll();
                        }
                    }
                    break;
        }
    }

    public class PbrFile {
        // RecNum <EF Tag, efid>
        HashMap<Integer,Map<Integer,Integer>> mFileIds;
	    public ArrayList<SimTlv> tlvList;

        PbrFile(ArrayList<byte[]> records) {
            mFileIds = new HashMap<Integer, Map<Integer, Integer>>();
	        tlvList = new ArrayList<SimTlv>();
            SimTlv recTlv;
            int recNum = 0;
            for (byte[] record: records) {
			    log("before making TLVs, data is " + IccUtils.bytesToHexString(record));
			    if (IccUtils.bytesToHexString(record).startsWith("ffff")) {
					continue;
				}
                recTlv = new SimTlv(record, 0, record.length);
	         	parsePBRData(record);
                parseTag(recTlv, recNum);
                recNum ++;
            }
        }

    public ArrayList<Integer> getFileId(int recordNum, int fileTag) {
        ArrayList<Integer> ints = new ArrayList<Integer>();
        try {
            SimTlv recordTlv = tlvList.get(recordNum*tlvList.size()/2);  //tlvList.size() =6
            SimTlv subTlv = new SimTlv(recordTlv.getData(), 0, recordTlv.getData().length);
            for(; subTlv.isValidObject();) {
                if(subTlv.getTag() == fileTag) {
                    //get the file tag
                    int i = subTlv.getData()[0]<<8;
                    ints.add(i+(int)(subTlv.getData()[1]&0xff));
                }
                if(!subTlv.nextObject())
                    return ints;
            }
        } catch (IndexOutOfBoundsException ex) {
            log("IndexOutOfBoundsException: "+ex);
            return ints;
        }
        return ints;
    }

    public ArrayList<Integer> getFileIdsByTagAdn(int tag, int ADNid){
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
	                x += (int)(subTlv.getData()[1]&0xff);
	                if ( subTlv.getTag() == UsimPhoneBookManager.USIM_EFADN_TAG){
	                	if(x==ADNid)
	                		adnBegin = true;
	                	else  adnBegin = false;
	                }
	                if(adnBegin){
			                if(subTlv.getTag()==tag) {
			                    if(subTlv.getData().length<2)
			                        continue;
			                    int y = subTlv.getData()[0]<<8;
			                    ints.add(y+(int)(subTlv.getData()[1]&0xff));
			                }
	                }
            }while (subTlv.nextObject());
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
                switch(tag) {
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
        }

        void parseEf(SimTlv tlv, Map<Integer, Integer> val, int parentTag) {
            int tag;
            byte[] data;
            int tagNumberWithinParentTag = 0;
            do {
                tag = tlv.getTag();
                if (parentTag == USIM_TYPE2_TAG && tag == USIM_EFEMAIL_TAG) {
                    mEmailPresentInIap = true;
                    mEmailTagNumberInIap = tagNumberWithinParentTag;
                }
                switch(tag) {
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
                        break;
                }
                tagNumberWithinParentTag ++;
            } while(tlv.nextObject());
        }


	    void parsePBRData(byte[] data) {
	    	log("enter parsePBRData");
	        SimTlv tlv ;
	        int totalLength = 0;
	        int validLength = getValidData(data);
	        do{
	            tlv = new SimTlv(data, totalLength, validLength);
	            totalLength += tlv.getData().length+2;
	            addRecord(tlv);
	        }while(totalLength<validLength);
	    }

	   
	    int getValidData(byte[] data) {
	        for(int i=0; i<data.length; i++) {
	            if((data[i]&0xff) == 0xff)
	                return i;
	        }
	        return data.length;
	    }

	    void addRecord(SimTlv tlv) {
	        tlvList.add(tlv);
	    }
	    
    }

    private void log(String msg) {
        if(DBG) Log.d(LOG_TAG, msg);
    }
}
