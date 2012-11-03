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

package com.android.internal.telephony.cdma;

import java.util.concurrent.atomic.AtomicBoolean;

import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.telephony.IccPhoneBookInterfaceManager;

/**
 * RuimPhoneBookInterfaceManager to provide an inter-process communication to
 * access ADN-like SIM records.
 */


public class RuimPhoneBookInterfaceManager extends IccPhoneBookInterfaceManager {
    static final String LOG_TAG = "CDMA";

    public RuimPhoneBookInterfaceManager(CDMAPhone phone) {
        super(phone);
        adnCache = phone.mIccRecords.getAdnCache();
        //NOTE service "simphonebook" added by IccSmsInterfaceManagerProxy
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
        if(DBG) Log.d(LOG_TAG, "RuimPhoneBookInterfaceManager finalized");
    }

    public int[] getAdnRecordsSize(int efid) {
        if (DBG) logd("getAdnRecordsSize: efid=" + efid);
        synchronized(mLock) {
            checkThread();
            recordSize = new int[3];

            //Using mBaseHandler, no difference in EVENT_GET_SIZE_DONE handling
            AtomicBoolean status = new AtomicBoolean(false);
            Message response = mBaseHandler.obtainMessage(EVENT_GET_SIZE_DONE, status);

            phone.getIccFileHandler().getEFLinearRecordSize(efid, response);
            waitForResult(status);
        }

        return recordSize;
    }

    protected void logd(String msg) {
        Log.d(LOG_TAG, "[RuimPbInterfaceManager] " + msg);
    }

    protected void loge(String msg) {
        Log.e(LOG_TAG, "[RuimPbInterfaceManager] " + msg);
    }
	@Override
	public int updateAdnRecordsInEfBySearchEx(int efid, String oldTag,
			String oldPhoneNumber, String[] oldEmailList, String oldAnr,
			String oldSne, String oldGrp,
			String newTag, String newPhoneNumber, String[] newEmailList,
			String newAnr, String newAas, String newSne, String newGrp,
			String newGas, String pin2) {
		// TODO Auto-generated method stub
		return -1;
	}

	@Override
	public int[] getAvalibleEmailCount(String name, String number,
			String[] emails, String anr, int[] emailNums) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int[] getAvalibleAnrCount(String name, String number,
			String[] emails, String anr, int[] anrNums) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int[] getEmailRecordsSize() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int[] getAnrRecordsSize() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getEmailNum() {
		// TODO Auto-generated method stub
		return 0;
	}

    @Override
    public int getAnrNum() {
        // TODO Auto-generated method stub
        return 0;
    }

    public int getEmailMaxLen() {
        return 0;
    }

    public int getPhoneNumMaxLen() {
        return 0;
    }
}

