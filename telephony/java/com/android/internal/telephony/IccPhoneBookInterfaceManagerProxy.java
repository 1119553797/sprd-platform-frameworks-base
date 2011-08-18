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

import android.content.pm.PackageManager;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ServiceManager;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;


/**
 * SimPhoneBookInterfaceManager to provide an inter-process communication to
 * access ADN-like SIM records.
 */
public class IccPhoneBookInterfaceManagerProxy extends IIccPhoneBook.Stub {
    private IccPhoneBookInterfaceManager mIccPhoneBookInterfaceManager;

    public IccPhoneBookInterfaceManagerProxy(IccPhoneBookInterfaceManager
            iccPhoneBookInterfaceManager) {
        mIccPhoneBookInterfaceManager = iccPhoneBookInterfaceManager;
        if(ServiceManager.getService("simphonebook") == null) {
            ServiceManager.addService("simphonebook", this);
        }
    }

    public void setmIccPhoneBookInterfaceManager(
            IccPhoneBookInterfaceManager iccPhoneBookInterfaceManager) {
        this.mIccPhoneBookInterfaceManager = iccPhoneBookInterfaceManager;
    }

     public int  getInsertIndex(){
		return mIccPhoneBookInterfaceManager.getInsertIndex();
	}
  /*  public boolean
    updateAdnRecordsInEfBySearch (int efid,
            String oldTag, String oldPhoneNumber,
            String newTag, String newPhoneNumber,
            String pin2) throws android.os.RemoteException {
        return mIccPhoneBookInterfaceManager.updateAdnRecordsInEfBySearch(
                efid, oldTag, oldPhoneNumber, newTag, newPhoneNumber, pin2);
    }
*/
    //yeezone:jinwei Add a new contact in SimCard.
    public 	int addAdnRecordsInEf(int efid, String newTag, String newPhoneNumber, String pin2){
        return mIccPhoneBookInterfaceManager.addAdnRecordsInEf(efid, newTag, newPhoneNumber, pin2);
    }
	//end Add a new contact in SimCard.
	
  /*  public boolean
    updateAdnRecordsInEfByIndex(int efid, String newTag,
            String newPhoneNumber, int index, String pin2) throws android.os.RemoteException {
        return mIccPhoneBookInterfaceManager.updateAdnRecordsInEfByIndex(efid,
                newTag, newPhoneNumber, index, pin2);
    }
*/
    public int[] getAdnRecordsSize(int efid) throws android.os.RemoteException {
        return mIccPhoneBookInterfaceManager.getAdnRecordsSize(efid);
    }

    public int[] getEmailRecordsSize() throws android.os.RemoteException {
        return mIccPhoneBookInterfaceManager.getEmailRecordsSize();
    }

    public int[] getAnrRecordsSize() throws android.os.RemoteException {
        return mIccPhoneBookInterfaceManager.getAnrRecordsSize();
    }
    public int getEmailNum() throws android.os.RemoteException {
        return mIccPhoneBookInterfaceManager.getEmailNum();
    }

    public int getAnrNum() throws android.os.RemoteException {
        return mIccPhoneBookInterfaceManager.getAnrNum();
    }

    public List<AdnRecord> getAdnRecordsInEf(int efid) throws android.os.RemoteException {
        return mIccPhoneBookInterfaceManager.getAdnRecordsInEf(efid);
    }

    public boolean
    updateAdnRecordsInEfBySearch (int efid,
            String oldTag, String oldPhoneNumber,String[] oldEmailList,String oldAnr,
            String newTag, String newPhoneNumber,String[] newEmailList,
            String newAnr,String newAas, String newSne, String newGrp, 
            String newGas,  String pin2){


            return mIccPhoneBookInterfaceManager.updateAdnRecordsInEfBySearch(efid,oldTag, oldPhoneNumber, oldEmailList,oldAnr,
             newTag,  newPhoneNumber, newEmailList,
             newAnr, newAas,  newSne,  newGrp, 
             newGas,   pin2);
	}

     public boolean updateAdnRecordsInEfByIndex(int efid, String newTag, String newPhoneNumber,
				List<String> newEmailList,String newAnr,String newAas, String newSne,String newGrp, String newGas,  int index, String pin2){

             return mIccPhoneBookInterfaceManager.updateAdnRecordsInEfByIndex( efid,  newTag, newPhoneNumber,
				 newEmailList, newAnr, newAas,  newSne, newGrp,  newGas,   index,  pin2);


	 }
	
}
