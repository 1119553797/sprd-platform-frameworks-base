
package com.android.internal.telephony;

import java.util.List;

import android.app.PendingIntent;
import android.os.RemoteException;
import android.os.ServiceManager;

public class CompositeIccSmsInterfaceManagerProxy extends ISms.Stub {

    private IccSmsInterfaceManagerProxy[] mIccSmsInterfaceManager;

    private int getSimplePolicyPhoneId() {
        return PhoneFactory.getDefaultPhoneId();
    }

    public CompositeIccSmsInterfaceManagerProxy(IccSmsInterfaceManagerProxy[] iccSmsInterfaceManager) {
        this.mIccSmsInterfaceManager = iccSmsInterfaceManager;
        if (ServiceManager.getService("isms") == null) {
            ServiceManager.addService("isms", this);
        }
    }

    @Override
    public List<SmsRawData> getAllMessagesFromIccEf() throws RemoteException {
        return mIccSmsInterfaceManager[getSimplePolicyPhoneId()].getAllMessagesFromIccEf();
    }

    @Override
    public boolean updateMessageOnIccEf(int messageIndex, int newStatus, byte[] pdu)
            throws RemoteException {
        return mIccSmsInterfaceManager[getSimplePolicyPhoneId()].updateMessageOnIccEf(messageIndex,
                newStatus, pdu);
    }

    @Override
    public boolean copyMessageToIccEf(int status, byte[] pdu, byte[] smsc) throws RemoteException {
        return mIccSmsInterfaceManager[getSimplePolicyPhoneId()].copyMessageToIccEf(status, pdu,
                smsc);
    }

    @Override
    public int copyMessageToIccEfReturnIndex(int status, byte[] pdu, byte[] smsc) throws RemoteException {
        return mIccSmsInterfaceManager[getSimplePolicyPhoneId()].copyMessageToIccEfReturnIndex(status, pdu,
                smsc);
    }

    @Override
    public String getSimCapacity() throws RemoteException {
        return mIccSmsInterfaceManager[getSimplePolicyPhoneId()].getSimCapacity();
    }

    @Override
    public void sendData(String destAddr, String scAddr, int destPort, byte[] data,
            PendingIntent sentIntent, PendingIntent deliveryIntent) throws RemoteException {
        mIccSmsInterfaceManager[getSimplePolicyPhoneId()].sendData(destAddr, scAddr, destPort,
                data, sentIntent, deliveryIntent);
    }

    @Override
    public void sendDmData(String destAddr, String scAddr, int destPort, int srcPort, byte[] data,
            PendingIntent sentIntent, PendingIntent deliveryIntent) throws RemoteException {
        mIccSmsInterfaceManager[getSimplePolicyPhoneId()].sendDmData(destAddr, scAddr, destPort,
                srcPort, data, sentIntent, deliveryIntent);

    }

    @Override
    public void sendText(String destAddr, String scAddr, String text, PendingIntent sentIntent,
            PendingIntent deliveryIntent) throws RemoteException {
        mIccSmsInterfaceManager[getSimplePolicyPhoneId()].sendText(destAddr, scAddr, text,
                sentIntent, deliveryIntent);

    }

    @Override
    public void sendMultipartText(String destinationAddress, String scAddress, List<String> parts,
            List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents)
            throws RemoteException {
        mIccSmsInterfaceManager[getSimplePolicyPhoneId()].sendMultipartText(destinationAddress,
                scAddress, parts, sentIntents, deliveryIntents);
    }

    @Override
    public boolean saveMultipartText(String destinationAddress, String scAddress,
            List<String> parts, boolean isOutbox, String timestring, int savestatus)
            throws RemoteException {
        mIccSmsInterfaceManager[getSimplePolicyPhoneId()].saveMultipartText(destinationAddress,
                scAddress, parts, isOutbox, timestring, savestatus);
        return false;
    }

    @Override
    public boolean enableCellBroadcast(int messageIdentifier) throws RemoteException {
        return mIccSmsInterfaceManager[getSimplePolicyPhoneId()]
                .enableCellBroadcast(messageIdentifier);
    }

    @Override
    public boolean disableCellBroadcast(int messageIdentifier) throws RemoteException {
        return mIccSmsInterfaceManager[getSimplePolicyPhoneId()]
                .disableCellBroadcast(messageIdentifier);
    }

    @Override
    public boolean enableCellBroadcastRange(int startMessageId, int endMessageId)
            throws RemoteException {
        return mIccSmsInterfaceManager[getSimplePolicyPhoneId()].enableCellBroadcastRange(
                startMessageId, endMessageId);
    }

    @Override
    public boolean disableCellBroadcastRange(int startMessageId, int endMessageId)
            throws RemoteException {
        return mIccSmsInterfaceManager[getSimplePolicyPhoneId()].disableCellBroadcastRange(
                startMessageId, endMessageId);
    }

    @Override
    public void setMaxSendRetries(int smsRetryTimes) throws RemoteException {
        mIccSmsInterfaceManager[getSimplePolicyPhoneId()].setMaxSendRetries(smsRetryTimes);
    }

}
