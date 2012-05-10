
package com.android.internal.telephony;

import java.util.List;

import android.os.RemoteException;
import android.os.ServiceManager;

public class CompositeIccPhoneBookInterfaceManagerProxy extends IIccPhoneBook.Stub {
    private static final String TAG = "CompositeIccPhoneBookInterfaceManagerProxy";

    private static final boolean DBG = false;

    private IccPhoneBookInterfaceManagerProxy[] mIccPhoneBookInterfaceManager;

    private int getSimplePolicyPhoneId() {
        return PhoneFactory.getDefaultPhoneId();
    }

    public CompositeIccPhoneBookInterfaceManagerProxy(
            IccPhoneBookInterfaceManagerProxy[] iccPhoneBookInterfaceManager) {
        mIccPhoneBookInterfaceManager = iccPhoneBookInterfaceManager;
        if (ServiceManager.getService("simphonebook") == null) {
            ServiceManager.addService("simphonebook", this);
        }
    }

    @Override
    public List<AdnRecord> getAdnRecordsInEf(int efid) throws RemoteException {
        return mIccPhoneBookInterfaceManager[getSimplePolicyPhoneId()].getAdnRecordsInEf(efid);
    }

    @Override
    public int[] getAdnRecordsSize(int efid) throws RemoteException {
        return mIccPhoneBookInterfaceManager[getSimplePolicyPhoneId()].getAdnRecordsSize(efid);
    }

    @Override
    public int[] getEmailRecordsSize() throws RemoteException {
        return mIccPhoneBookInterfaceManager[getSimplePolicyPhoneId()].getEmailRecordsSize();
    }

    @Override
    public int[] getAnrRecordsSize() throws RemoteException {
        return mIccPhoneBookInterfaceManager[getSimplePolicyPhoneId()].getAnrRecordsSize();
    }

    @Override
    public int getAnrNum() throws RemoteException {
        return mIccPhoneBookInterfaceManager[getSimplePolicyPhoneId()].getAnrNum();
    }

    @Override
    public int getEmailNum() throws RemoteException {
        return mIccPhoneBookInterfaceManager[getSimplePolicyPhoneId()].getEmailNum();
    }

    @Override
    public int getInsertIndex() throws RemoteException {
        return mIccPhoneBookInterfaceManager[getSimplePolicyPhoneId()].getInsertIndex();
    }

    @Override
    public boolean updateAdnRecordsInEfBySearch(int efid, String oldTag, String oldPhoneNumber,
            String[] oldEmailList, String oldAnr, String newTag, String newPhoneNumber,
            String[] newEmailList, String newAnr, String newAas, String newSne, String newGrp,
            String newGas, String pin2) throws RemoteException {
        return mIccPhoneBookInterfaceManager[getSimplePolicyPhoneId()]
                .updateAdnRecordsInEfBySearch(efid, oldTag, oldPhoneNumber, oldEmailList, oldAnr,
                        newTag, newPhoneNumber, newEmailList, newAnr, newAas, newSne, newGrp,
                        newGas, pin2);
    }

    @Override
    public boolean updateAdnRecordsInEfByIndex(int efid, String newTag, String newPhoneNumber,
            List<String> newEmailList, String newAnr, String newAas, String newSne, String newGrp,
            String newGas, int index, String pin2) throws RemoteException {
        return mIccPhoneBookInterfaceManager[getSimplePolicyPhoneId()].updateAdnRecordsInEfByIndex(
                efid, newTag, newPhoneNumber, newEmailList, newAnr, newAas, newSne, newGrp, newGas,
                index, pin2);
    }

    @Override
    public int[] getAvalibleEmailCount(String name, String number, String[] emails, String anr,
            int[] emailNums) throws RemoteException {
        return mIccPhoneBookInterfaceManager[getSimplePolicyPhoneId()].getAvalibleEmailCount(name,
                number, emails, anr, emailNums);
    }

    @Override
    public int[] getAvalibleAnrCount(String name, String number, String[] emails, String anr,
            int[] anrNums) throws RemoteException {
        return mIccPhoneBookInterfaceManager[getSimplePolicyPhoneId()].getAvalibleAnrCount(name,
                number, emails, anr, anrNums);
    }

}
