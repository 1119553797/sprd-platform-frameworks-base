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
import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.Phone;

import com.android.internal.telephony.IccException;
import com.android.internal.telephony.IccFileTypeMismatch;
import com.android.internal.telephony.IccIoResult;
import com.android.internal.telephony.gsm.SIMFileHandler;
import com.android.internal.telephony.gsm.UsimPhoneBookManager;
import java.util.Map;
import java.util.HashMap;

/**
 * s {@hide}
 */
public final class TDUSIMFileHandler extends SIMFileHandler implements
		IccConstants {

	// ***** types of files UICC 12.1.1.3
	static protected final byte TYPE_FCP = 0x62;
	static protected final byte RESPONSE_DATA_FCP_FLAG = 0;
	static protected final byte TYPE_FILE_DES = (byte) 0x82;
	static protected final byte TYPE_FCP_SIZE = (byte) 0x80;
	static protected final byte RESPONSE_DATA_FILE_DES_FLAG = 2;
	static protected final byte RESPONSE_DATA_FILE_DES_LEN_FLAG = 3;
	static protected final byte TYPE_FILE_DES_LEN = 5;
	static protected final byte RESPONSE_DATA_FILE_RECORD_LEN_1 = 6;
	static protected final byte RESPONSE_DATA_FILE_RECORD_LEN_2 = 7;
	static protected final byte RESPONSE_DATA_FILE_RECORD_COUNT_FLAG = 8;

	static private final byte USIM_RECORD_SIZE_1 = 4;
	static private final byte USIM_RECORD_SIZE_2 = 5;
	static private final byte USIM_RECORD_COUNT = 6;
   static private final int USIM_DATA_OFFSET_2 = 2;
   static private final int USIM_DATA_OFFSET_3 = 3;
	static private final int EVENT_GET_USIM_ECC_DONE = 0x10;
	static final String LOG_TAG = "TD-SCDMA";
	private Phone mPhone;
	private Object mLock = new Object();

	//private final int mDualMapFile[]  ={EF_ADN,EF_ARR,EF_FDN,EF_SMS,EF_MSISDN,
	//	EF_SMSP,EF_SMSS,EF_SMSR,EF_SDN,EF_EXT2,EF_EXT3,EF_EXT4,EF_BDN,EF_TEST}; 
	private final int mDualMapFile[]  ={EF_SMS,EF_PBR}; 
	private Map<Integer, String> mDualMapFileList;
       private ArrayList<Integer> mFileList; 
  

	// ***** Instance Variables

	// ***** Constructor

	/*
	 * static class LoadLinearFixedContext {
	 * 
	 * int efid; int recordNum, recordSize, countRecords; boolean loadAll;
	 * 
	 * Message onLoaded;
	 * 
	 * ArrayList<byte[]> results;
	 * 
	 * LoadLinearFixedContext(int efid, int recordNum, Message onLoaded) {
	 * this.efid = efid; this.recordNum = recordNum; this.onLoaded = onLoaded;
	 * this.loadAll = false; }
	 * 
	 * LoadLinearFixedContext(int efid, Message onLoaded) { this.efid = efid;
	 * this.recordNum = 1; this.loadAll = true; this.onLoaded = onLoaded; } }
	 */

	TDUSIMFileHandler(GSMPhone phone) {
		super(phone);
		mPhone = phone;
		initDualMapFileSet();
		
	}

      private void initDualMapFileSet(){

            mDualMapFileList =  new HashMap<Integer, String>();
		mFileList =  new ArrayList<Integer>();

            mDualMapFileList.put(mDualMapFile[0],MF_SIM+DF_ADF);
            mDualMapFileList.put(mDualMapFile[1],MF_SIM+DF_TELECOM+DF_PHONEBOOK);
	      

	}

	private void clearDualMapFileSet(){
             if(mFileList != null){

                   mFileList= null;
             }

		if(mDualMapFileList != null){
                 mDualMapFileList.clear();
		     mDualMapFileList =  null;
		}

	}

      	@Override
	public void addDualMapFile(int efid){
        Log.d(LOG_TAG, "addDualMapFile efid = " + efid);
        boolean isExist = false;
        if (mFileList != null ) {
            for (int i= 0; i<mFileList.size(); i++) {
                if (mFileList.get(i) == efid) {
                    isExist = true;
                    break;
                }
            }
            if (!isExist) {
                mFileList.add(efid);
            }
        }
	}

     private void UpdatePathOfDualMapFile(int efid, String path){

               loge("UpdatePathOfDualMapFile  efid " +efid + " path " +path );
			   
               if(mDualMapFileList != null){
                      
                    mDualMapFileList.put(efid,path); 
		
	        }




	}

		
	public void dispose() {
		
		super.dispose();
		clearDualMapFileSet();
	}

	protected void finalize() {
		Log.d(LOG_TAG, "TDUSIMFileHandler finalized");
	}

	// ***** Private Methods
	private void sendResult(Message response, Object result, Throwable ex) {
		if (response == null) {
			return;
		}

		AsyncResult.forMessage(response, result, ex);

		response.sendToTarget();
	}

        /**
     * Load a SIM Transparent EF
     *
     * @param fileid EF id
     * @param onLoaded
     *
     * ((AsyncResult)(onLoaded.obj)).result is the byte[]
     *
     */

    private boolean isDualMapFile(int fileId){

         Log.i(LOG_TAG,"isDualMapFile  fileId "+ fileId + " mDualMapFileList "  + mDualMapFileList);
	   if(mDualMapFileList ==  null){

                 return false;
	   }
         
	
         if(mDualMapFileList.containsKey(fileId) ){
		 	
                       return true;
         }
	    

           return false;
    }

    private boolean isFinishLoadFile(int fileId, int pathNum){

          Log.i(LOG_TAG,"isFinishLoadFile  fileId "+ fileId +  "pathNum " +  pathNum);

          if(isDualMapFile(fileId)){
		  	
                 if(pathNum == 1){

			    return true;

		    }
                 if(pathNum == 0)
                 {

                       return false;   
		     }
	   }


	    return true;

    }
 
    protected String getEFPathofUsim(int efid ){
           String oldPath = getEFPath(efid);
           IccCard card = phone.getIccCard();
	     boolean isUsim =false;
	     if (card != null && card.isApplicationOnIcc(IccCardApplication.AppType.APPTYPE_USIM)){
               isUsim = true;
	    }
	     if(!isUsim){
		 	
                return null;
	    }

	     String pathFirst="";
	     String pathSecond ="";
	     String pathLast  ="";
	 
	
	     if(oldPath.length() <8){
                     return null;					 
		 }else{

			 pathFirst = oldPath.substring(0,4); 
                    pathSecond = oldPath.substring(4,8); 
					
			 if(oldPath.length() > 8){
			    pathLast = oldPath.substring(8,oldPath.length());
			 }
		 }	

		 Log.i(LOG_TAG,"getEFPathofUsim false , try again pathFirst " + pathFirst + "pathSecond " +pathSecond + "pathLast " +pathLast);
              if(pathSecond.equals(DF_ADF)){
			  	
                    pathSecond = DF_TELECOM;
			 
                   
		 }else  if(pathSecond.equals(DF_TELECOM)){
			  	
                    pathSecond = DF_ADF;
                  
		 }           
		  else{

                  
                    return null;
		 } 

		 String newPath = pathFirst+pathSecond+pathLast;
              UpdatePathOfDualMapFile(efid,newPath);
		 return newPath;

    }


    public boolean loadFileAgain(int fileId, int pathNum , int event,Object obj){

	
	  IccCard card = phone.getIccCard();
	  boolean isUsim =false;
	  if (card != null && card.isApplicationOnIcc(IccCardApplication.AppType.APPTYPE_USIM)){
               isUsim = true;
	  }
      
         if(!isUsim){
		 	
                return false;
	  }
  

        if(isFinishLoadFile(fileId, pathNum)){
              
               return false;
	 }else{


              String newPath = getEFPathofUsim(fileId);
              if(newPath ==  null){

                   return false;
		 }
	
              Message response = obtainMessage(event,
                        fileId, 1, obj);
			
		 Log.i(LOG_TAG,"isFinishLoadFile  try again newPath   " + newPath);	
              phone.mCM.iccIO(COMMAND_GET_RESPONSE, fileId,newPath,
                    0, 0, GET_RESPONSE_EF_SIZE_BYTES, null, null, response);
	 }


	 return true;


    }

   
    @Override
    public void loadEFTransparent(int fileid, Message onLoaded) {
       
       IccCard card = phone.getIccCard();
       Log.i(LOG_TAG,"loadEFTransparent fileid " + Integer.toHexString(fileid));
       if (card != null && card.isApplicationOnIcc(IccCardApplication.AppType.APPTYPE_USIM) && fileid == EF_ECC){
           Log.i(LOG_TAG,"loadEFTransparent is usim card");
	  
           loadEFLinearFixedAll(fileid,onLoaded); 
	  }else{
             Message response = obtainMessage(EVENT_GET_BINARY_SIZE_DONE,
                        fileid, 0, onLoaded);
		phone.mCM.iccIO(COMMAND_GET_RESPONSE, fileid, getEFPath(fileid),
                        0, 0, GET_RESPONSE_EF_SIZE_BYTES, null, null, response);

	  }
    }


	// ***** Overridden from IccFileHandler

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
		int index = 0;
		boolean isUsim = false;
		int pathNum = msg.arg2;
		String path;
             IccCard card = phone.getIccCard();
		if (card != null && card.isApplicationOnIcc(IccCardApplication.AppType.APPTYPE_USIM)){
                    isUsim = true;
		}
		try {
			switch (msg.what) {
			case EVENT_READ_IMG_DONE:
				ar = (AsyncResult) msg.obj;
				lc = (IccFileHandler.LoadLinearFixedContext) ar.userObj;
				result = (IccIoResult) ar.result;
				response = lc.onLoaded;

                if (ar.exception != null) {
                    Log.d(LOG_TAG, "EVENT_READ_IMG_DONE ar fail");
                    sendResult(response, null, ar.exception);
                    break;
                }
				iccException = result.getException();
                //Icon Display Start
				if (iccException != null) {
                    Log.d(LOG_TAG, "EVENT_READ_IMG_DONE icc fail");
                    sendResult(response, null, iccException);
                    break;
                }
                data = result.payload;
                fileid = lc.efid;
                recordNum = lc.recordNum;
                Log.d(LOG_TAG, "data = " + IccUtils.bytesToHexString(data) +
                        " fileid = " + fileid + " recordNum = " + recordNum);
                if (TYPE_EF != data[RESPONSE_DATA_FILE_TYPE]) {
                    Log.d(LOG_TAG, "EVENT_READ_IMG_DONE TYPE_EF mismatch");
                    throw new IccFileTypeMismatch();
                }
                if (EF_TYPE_LINEAR_FIXED != data[RESPONSE_DATA_STRUCTURE]) {
                    Log.d(LOG_TAG, "EVENT_READ_IMG_DONE EF_TYPE_LINEAR_FIXED mismatch");
                    throw new IccFileTypeMismatch();
                }
                lc.recordSize = data[RESPONSE_DATA_RECORD_LENGTH] & 0xFF;
                size = ((data[RESPONSE_DATA_FILE_SIZE_1] & 0xff) << 8)
                       + (data[RESPONSE_DATA_FILE_SIZE_2] & 0xff);
                lc.countRecords = size / lc.recordSize;
                if (lc.loadAll) {
                    lc.results = new ArrayList<byte[]>(lc.countRecords);
                }
                Log.d(LOG_TAG, "recordsize:" + lc.recordSize + "counts:" + lc.countRecords);
                phone.mCM.iccIO(COMMAND_READ_RECORD, lc.efid, getEFPath(lc.efid),
                                lc.recordNum,
                                READ_RECORD_MODE_ABSOLUTE,
                                lc.recordSize, null, null,
                                obtainMessage(EVENT_READ_RECORD_DONE, lc));
                //Icon Display End
				break;
			case EVENT_READ_ICON_DONE:
				ar = (AsyncResult) msg.obj;
				response = (Message) ar.userObj;
				result = (IccIoResult) ar.result;

				iccException = result.getException();
                //Icon Display Start
				if (iccException != null) {
					sendResult(response, result.payload, ar.exception);
                } else {
                    sendResult(response, result.payload, null);                }
                //Icon Display End
				break;
			case EVENT_GET_EF_LINEAR_RECORD_SIZE_DONE:
				ar = (AsyncResult) msg.obj;
				lc = (IccFileHandler.LoadLinearFixedContext) ar.userObj;
				result = (IccIoResult) ar.result;
				response = lc.onLoaded;
				data = result.payload;
				
				                         
				if (ar.exception != null) {
					Log.i(LOG_TAG,"EVENT_GET_EF_LINEAR_RECORD_SIZE_DONE exception ");
					sendResult(response, null, ar.exception);
					break;
				}
				
				if(data != null){
				     logbyte(data);
				}
                        
				iccException = result.getException();
                			
		             if(isUsim)                  
				{
			            int fileId = lc.efid;
                            
                                if(iccException!= null|| (fileId==EF_PBR && !isDataValid(data))){
						  Log.i(LOG_TAG, "EVENT_GET_EF_LINEAR_RECORD_SIZE_DONE pathNum "+ pathNum);

						if(!loadFileAgain(fileId, pathNum , msg.what,lc)){
                                 
						     sendResult(response, null, iccException);
						}
					      break;
					}
					if (iccException != null) {
						loge("EVENT_GET_EF_LINEAR_RECORD_SIZE_DONE ar.exception");
					      sendResult(response, null, iccException);
					      break;
				      }

					for (int i = 0; i < data.length; i++) {
						if (data[i] == TYPE_FILE_DES) {

							index = i;
							break;
						}

					}
					Log.i(LOG_TAG,
							"EVENT_GET_EF_LINEAR_RECORD_SIZE_DONE  index"
									+ index);
					if (index < 2) {
						throw new IccFileTypeMismatch();
					}
					// UICC 12.1.1.4.3
					recordSize = new int[3];

					recordSize[0] = ((data[index + USIM_RECORD_SIZE_1] & 0xff) << 8)
							+ (data[index + USIM_RECORD_SIZE_2] & 0xff);
					recordSize[2] = data[index + USIM_RECORD_COUNT] & 0xff;
					recordSize[1] = recordSize[0] * recordSize[2];
					for (int i = 0; i < 3; i++) {
						Log.i(LOG_TAG,
								"EVENT_GET_EF_LINEAR_RECORD_SIZE_DONE  recordSize"
										+ recordSize[i]);
					}
					response.arg2 = msg.arg2;
					sendResult(response, recordSize, null);
					break;
				}
			         Log.i(LOG_TAG, "EVENT_GET_EF_LINEAR_RECORD_SIZE_DONE (4)");
				if (iccException != null) {
					sendResult(response, null, iccException);
					break;
				}

                //NEWMS00170745
                if(data == null){
                    Log.i(LOG_TAG, "data == null");
                    throw new IccFileTypeMismatch();
                }


				if (TYPE_EF != data[RESPONSE_DATA_FILE_TYPE]
						|| EF_TYPE_LINEAR_FIXED != data[RESPONSE_DATA_STRUCTURE]) {
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
				ar = (AsyncResult) msg.obj;
				lc = (IccFileHandler.LoadLinearFixedContext) ar.userObj;
				result = (IccIoResult) ar.result;
				response = lc.onLoaded;
				if (ar.exception != null) {
					loge("EVENT_GET_RECORD_SIZE_DONE ar.exception");
					sendResult(response, null, ar.exception);
					break;
				}
                data = result.payload;
                fileid = lc.efid;
                recordNum = lc.recordNum;
				iccException = result.getException();
				
				if(isUsim)                  
				{
			             int fileId = lc.efid;
                               
                                if(iccException != null || (fileId==EF_PBR && !isDataValid(data))){
                                  
						if(!loadFileAgain(fileId, pathNum ,msg.what,lc)){
							sendResult(response, null, iccException);
						}
					      break;
					}
				}
			

				if (iccException != null) {
					loge("EVENT_GET_RECORD_SIZE_DONE iccException");
					sendResult(response, null, iccException);
					break;
				}

				logbyte(data);
				Log.d(LOG_TAG,"FCP:"
										+ Integer
												.toHexString(data[RESPONSE_DATA_FCP_FLAG])
										+ "DES:"
										+ Integer
												.toHexString(data[RESPONSE_DATA_FILE_DES_FLAG])
										+ "DES_LEN:"
										+ Integer
												.toHexString(data[RESPONSE_DATA_FILE_DES_LEN_FLAG]));
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
				loge("recordsize:" + lc.recordSize + "counts:"
						+ lc.countRecords);
				if (lc.loadAll) {
					lc.results = new ArrayList<byte[]>(lc.countRecords);
				}
				
				path =  getEFPath(lc.efid);
				loge("EVENT_GET_RECORD_SIZE_DONE path " + path);
				phone.mCM.iccIO(COMMAND_READ_RECORD, lc.efid,
						path, lc.recordNum,
						READ_RECORD_MODE_ABSOLUTE, lc.recordSize, null, null,
						obtainMessage(EVENT_READ_RECORD_DONE,0,pathNum,lc));
				break;
			case EVENT_GET_BINARY_SIZE_DONE:
				ar = (AsyncResult) msg.obj;
				response = (Message) ar.userObj;
				result = (IccIoResult) ar.result;
				if (ar.exception != null) {
					sendResult(response, null, ar.exception);
					break;
				}

				iccException = result.getException();

				if(isUsim)
				{
			           
                                  int fileId = msg.arg1;
                                if(iccException != null ){
                                  
						if(!loadFileAgain(fileId, pathNum , msg.what,response)){
                                              sendResult(response, null, iccException);
						}
					      break;
					}
				}
               if (iccException != null) {
                   loge("EVENT_GET_BINARY_SIZE_DONE iccException");
                   sendResult(response, null, iccException);
                   break;
               }
               data = result.payload;
               logbyte(data);
               fileid = msg.arg1;
               if (TYPE_FCP == data[RESPONSE_DATA_FCP_FLAG]) {
                   Log.i(LOG_TAG,"EVENT_GET_BINARY_SIZE_DONE fileid "+ Integer.toHexString(fileid));
                   for (int i = 0;i < data.length; i++) {
                      if (data[i] == TYPE_FILE_DES) {

                           index = i;
                           break;
                       }

                   }
                   Log.i(LOG_TAG,"TYPE_FILE_DES index "+ index);

                   if((data[index + RESPONSE_DATA_FILE_DES_FLAG] & 0x01) != 1){
                       Log.i(LOG_TAG,"EVENT_GET_BINARY_SIZE_DONE the efid "+Integer.toHexString(fileid)+" is not transparent file");
                       throw new IccFileTypeMismatch();
                   }
                   for (int i = index ; i < data.length ; i++) {
                       if (data[i] == TYPE_FCP_SIZE) {
                           index = i;
                          break;
                       }else{
                           i+=(data[i+1]+1);
                       }
                   }
                   Log.i(LOG_TAG,"TYPE_FCP_SIZE index "+index);

                  size = ((data[index + USIM_DATA_OFFSET_2] & 0xff) << 8)
                   + (data[index + USIM_DATA_OFFSET_3] & 0xff);
               } else {
                   if (TYPE_EF != data[RESPONSE_DATA_FILE_TYPE]) {
                      throw new IccFileTypeMismatch();
                  }

                  if (EF_TYPE_TRANSPARENT != data[RESPONSE_DATA_STRUCTURE]) {
                       throw new IccFileTypeMismatch();
                   }

                   size = ((data[RESPONSE_DATA_FILE_SIZE_1] & 0xff) << 8)
                   + (data[RESPONSE_DATA_FILE_SIZE_2] & 0xff);
               }

               path =  getEFPath(fileid);
               loge("EVENT_GET_BINARY_SIZE_DONE path " + path);

               phone.mCM.iccIO(COMMAND_READ_BINARY, fileid, path,
                       0, 0, size, null, null, obtainMessage(
                               EVENT_READ_BINARY_DONE, fileid, pathNum, response));
              break;

			case EVENT_READ_RECORD_DONE:

				ar = (AsyncResult) msg.obj;
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
                        //path = getEFPath(lc.efid) ;
						phone.mCM.iccIO(COMMAND_READ_RECORD, lc.efid, getEFPath(lc.efid),
                                        lc.recordNum,
										READ_RECORD_MODE_ABSOLUTE,
										lc.recordSize, null, null,
										obtainMessage(EVENT_READ_RECORD_DONE,0,pathNum,
												lc));
					}
				}

				break;

			case EVENT_READ_BINARY_DONE:
				ar = (AsyncResult) msg.obj;
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

			}
		} catch (Exception exc) {
			if (response != null) {
				sendResult(response, null, exc);
			} else {
				loge("uncaught exception" + exc);
			}
		}
	}

     private String getEfPathFromList(int efid){
	 	
          String path = null;
	   if(mDualMapFileList ==  null){

                 return null;
	   }

	   if(mDualMapFileList.containsKey(efid)){

                 path = mDualMapFileList.get(efid);

		    if(path != null){

                       return path;
		    }
	    }

	   if(mFileList == null){

               return null;
	   }
         
	   for(int i=0; i < mFileList.size(); i++){
                
                 if(mFileList.get(i) ==  efid){
				 	
			    path = mDualMapFileList.get(EF_PBR); 
				
                       if( path != null){

                              return  path ;
			    }else{

                              break;
			    }
		    }
	    }

	 

           return null;


    }
	 
     private String getCommonIccEFPathOfUsim(int efid) {
        switch(efid) {
        case EF_ADN:
        case EF_FDN:
        case EF_MSISDN:
        case EF_SDN:
        case EF_EXT1:
        case EF_EXT2:
        case EF_EXT3:
	  
            //return MF_SIM + DF_ADF;
            return MF_SIM + DF_TELECOM;

        case EF_ICCID:
            return MF_SIM;
        case EF_IMG:
            return MF_SIM + DF_TELECOM + DF_GRAPHICS;
        }
        return null;
    }


	protected String getEFPath(int efid) {
		IccCard card = phone.getIccCard();
		boolean isUsim = false;
		String path = null;

		
             if (card != null && card.isApplicationOnIcc(IccCardApplication.AppType.APPTYPE_USIM)) {
			isUsim = true;
			path = getEfPathFromList(efid);
			
		       if(path != null){
                          //loge("getEFPath  path  "  + path );
                          return path;
			}
		}

		
        Log.d(LOG_TAG, "tdusimfilehandler efid = " + efid);
		
		switch (efid) {
		case EF_SMS:
			if (isUsim) {
				return MF_SIM + DF_ADF;
			}
			return MF_SIM + DF_TELECOM;
            case EF_ECC:
			if (isUsim) {
				return MF_SIM + DF_ADF;
			}
                   
			return MF_SIM + DF_GSM;
		case EF_EXT6:
		case EF_MWIS:
		case EF_MBI:
		case EF_SPN:
        case EF_AD:
            if (isUsim) {
                return MF_SIM + DF_ADF;
            }
            break;
		case EF_MBDN:
		case EF_PNN:
		case EF_SPDI:
		case EF_SST:
		case EF_CFIS:
	                  
			return MF_SIM + DF_GSM;
		case EF_FDN:
			if (isUsim) {
				return MF_SIM + DF_ADF;
			}
                   break;
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
     	 
		 path = getCommonIccEFPath(efid);
		
		if (path == null) {
			// The EFids in USIM phone book entries are decided by the card
			// manufacturer.
			// So if we don't match any of the cases above and if its a USIM
			// return
			// the phone book path.

			if (isUsim) {
				//return MF_SIM + DF_ADF + DF_PHONEBOOK;
				//return MF_SIM + DF_TELECOM + DF_PHONEBOOK;
			    return mDualMapFileList.get(EF_PBR);
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
		for (int i = 0; i < data.length; i++) {
			test = Integer.toHexString(data[i] & 0xFF);
			if (test.length() == 1) {
				test = '0' + test;
			}
			Log.d(LOG_TAG, "payload:" + test);
		}
	}
    protected boolean isDataValid(byte data[]) {
        boolean isValid = false;
        for (int i = 0; i < data.length; i++) {
            if(data[i]!=0xFF){
                isValid = true;
                break;
            }
        }

        Log.d(LOG_TAG, "isDataValid:" + isValid);
        return isValid;
    }
}
