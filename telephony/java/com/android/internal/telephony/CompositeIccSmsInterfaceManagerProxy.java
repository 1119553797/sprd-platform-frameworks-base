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

import android.app.PendingIntent;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.Message;

import java.util.List;

public class CompositeIccSmsInterfaceManagerProxy extends ISms.Stub {
    private IccSmsInterfaceManagerProxy[] mIccSmsInterfaceManager;

    public CompositeIccSmsInterfaceManagerProxy(IccSmsInterfaceManagerProxy[]
            iccSmsInterfaceManager) {
        this.mIccSmsInterfaceManager = iccSmsInterfaceManager;
        if(ServiceManager.getService("isms") == null) {
            ServiceManager.addService("isms", this);
        }
    }

    private int getSimplePolicyPhoneId() {
        return PhoneFactory.getDefaultPhoneId();
    }

    public boolean
    updateMessageOnIccEf(int index, int status, byte[] pdu) throws android.os.RemoteException {
         return mIccSmsInterfaceManager[getSimplePolicyPhoneId()].updateMessageOnIccEf(index, status, pdu);
    }

    public boolean copyMessageToIccEf(int status, byte[] pdu,
            byte[] smsc) throws android.os.RemoteException {
        return mIccSmsInterfaceManager[getSimplePolicyPhoneId()].copyMessageToIccEf(status, pdu, smsc);
    }
    //fix for bug 4197
    public String copyMessageToIccEfWithResult(int status, byte[] pdu, byte[] smsc) throws android.os.RemoteException {
        return mIccSmsInterfaceManager[getSimplePolicyPhoneId()].copyMessageToIccEfWithResult(status, pdu, smsc);
    }

    public String getSimCapacity() throws android.os.RemoteException {
        return mIccSmsInterfaceManager[getSimplePolicyPhoneId()].getSimCapacity();
    }

    public List<SmsRawData> getAllMessagesFromIccEf() throws android.os.RemoteException {
        return mIccSmsInterfaceManager[getSimplePolicyPhoneId()].getAllMessagesFromIccEf();
    }

    public void sendData(String destAddr, String scAddr, int destPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        mIccSmsInterfaceManager[getSimplePolicyPhoneId()].sendData(destAddr, scAddr, destPort, data,
                sentIntent, deliveryIntent);
    }

/*Start liuhongxing 20110602 */
    public void sendDmData(String destAddr, String scAddr, int destPort, int srcPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        mIccSmsInterfaceManager[getSimplePolicyPhoneId()].sendDmData(destAddr, scAddr, destPort, srcPort, data,
                sentIntent, deliveryIntent);
    }
/*End liu 20110602 */

    public void sendText(String destAddr, String scAddr,
            String text, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        mIccSmsInterfaceManager[getSimplePolicyPhoneId()].sendText(destAddr, scAddr, text, sentIntent, deliveryIntent);
    }

    public void sendMultipartText(String destAddr, String scAddr,
            List<String> parts, List<PendingIntent> sentIntents,
            List<PendingIntent> deliveryIntents) throws android.os.RemoteException {
        mIccSmsInterfaceManager[getSimplePolicyPhoneId()].sendMultipartText(destAddr, scAddr,
                parts, sentIntents, deliveryIntents);
    }


    //TS for compile
    public boolean saveMultipartText(String destinationAddress, String scAddress,
            List<String> parts, boolean isOutbox, String timestring,
			int savestatus) throws RemoteException {
        return mIccSmsInterfaceManager[getSimplePolicyPhoneId()].saveMultipartText(destinationAddress, scAddress,
                parts, isOutbox, timestring, savestatus);
	}

    public boolean enableCellBroadcast(int messageIdentifier) throws android.os.RemoteException {
        return mIccSmsInterfaceManager[getSimplePolicyPhoneId()].enableCellBroadcast(messageIdentifier);
    }

    public boolean disableCellBroadcast(int messageIdentifier) throws android.os.RemoteException {
        return mIccSmsInterfaceManager[getSimplePolicyPhoneId()].disableCellBroadcast(messageIdentifier);
    }

    public boolean enableCellBroadcastRange(int startMessageId, int endMessageId)
            throws android.os.RemoteException {
        return mIccSmsInterfaceManager[getSimplePolicyPhoneId()].enableCellBroadcastRange(startMessageId, endMessageId);
    }

    public boolean disableCellBroadcastRange(int startMessageId, int endMessageId)
            throws android.os.RemoteException {
        return mIccSmsInterfaceManager[getSimplePolicyPhoneId()].disableCellBroadcastRange(startMessageId, endMessageId);
    }
    public void activateCellBroadcastSms(int activate) throws android.os.RemoteException {
        mIccSmsInterfaceManager[getSimplePolicyPhoneId()].activateCellBroadcastSms(activate);
    }
 
    public void setCellBroadcastSmsConfig(int[] configValuesArray) throws android.os.RemoteException {
        mIccSmsInterfaceManager[getSimplePolicyPhoneId()].setCellBroadcastSmsConfig(configValuesArray);
    }

    public void getCellBroadcastSmsConfig(Message response) throws android.os.RemoteException {
        mIccSmsInterfaceManager[getSimplePolicyPhoneId()].getCellBroadcastSmsConfig(response);
    }
}
