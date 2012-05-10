
package com.android.internal.telephony;

import java.io.FileDescriptor;
import java.io.PrintWriter;

import android.os.RemoteException;
import android.os.ServiceManager;

public class CompositePhoneSubInfoProxy extends IPhoneSubInfo.Stub {
    private PhoneSubInfoProxy[] mPhoneSubInfo;

    private int getSimplePolicyPhoneId() {
        return PhoneFactory.getDefaultPhoneId();
    }

    public CompositePhoneSubInfoProxy(PhoneSubInfoProxy[] phoneSubInfo) {
        mPhoneSubInfo = phoneSubInfo;
        if (ServiceManager.getService("iphonesubinfo") == null) {
            ServiceManager.addService("iphonesubinfo", this);
        }
    }

    @Override
    public String getDeviceId() throws RemoteException {
        return mPhoneSubInfo[getSimplePolicyPhoneId()].getDeviceId();
    }

    @Override
    public String getDeviceSvn() throws RemoteException {
        return mPhoneSubInfo[getSimplePolicyPhoneId()].getDeviceSvn();
    }

    @Override
    public String getSubscriberId() throws RemoteException {
        return mPhoneSubInfo[getSimplePolicyPhoneId()].getSubscriberId();
    }

    @Override
    public String getIccSerialNumber() throws RemoteException {
        return mPhoneSubInfo[getSimplePolicyPhoneId()].getIccSerialNumber();
    }

    @Override
    public String getLine1Number() throws RemoteException {
        return mPhoneSubInfo[getSimplePolicyPhoneId()].getLine1Number();
    }

    @Override
    public String getLine1AlphaTag() throws RemoteException {
        return mPhoneSubInfo[getSimplePolicyPhoneId()].getLine1AlphaTag();
    }

    @Override
    public String getVoiceMailNumber() throws RemoteException {
        return mPhoneSubInfo[getSimplePolicyPhoneId()].getVoiceMailNumber();
    }

    @Override
    public String getCompleteVoiceMailNumber() throws RemoteException {
        return mPhoneSubInfo[getSimplePolicyPhoneId()].getCompleteVoiceMailNumber();
    }

    @Override
    public String getVoiceMailAlphaTag() throws RemoteException {
        return mPhoneSubInfo[getSimplePolicyPhoneId()].getVoiceMailAlphaTag();
    }
    
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        mPhoneSubInfo[getSimplePolicyPhoneId()].dump(fd, pw, args);
    }
}
