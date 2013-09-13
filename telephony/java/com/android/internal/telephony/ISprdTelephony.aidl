/* Created by Spreadst */

package com.android.internal.telephony;

/**
 * Interface used to interact with the phone.  Mostly this is used by the
 * TelephonyManager class.  A few places are still using this directly.
 * Please clean them up if possible and use TelephonyManager insteadl.
 *
 * {@hide}
 */
interface ISprdTelephony {

    void holdCall();

    String getSmsc();

    boolean setSmsc(String smscAddr);

    /**
     * Set the iccCard to on or off
     */
    boolean setIccCard(boolean turnOn);

    /**
     * Check to see if the iccCard is on or not.
     * @return returns true if the radio is on.
     */
    boolean isIccCardOn();

    int getDataStatebyApnType(String apnType);

    /**
     * @return true if a IccFdn enabled
     */
    boolean getIccFdnEnabled();

    /**
     * Return gam Authenticate
     */
    String[] Mbbms_Gsm_Authenticate(String nonce);

    /**
     * Return usim Authenticate
     */
    String[] Mbbms_USim_Authenticate(String nonce, String autn);

    boolean isVTCall();

    int getRemainTimes(int type);

    boolean setApnActivePdpFilter(String apntype,boolean filterenable);

    boolean getApnActivePdpFilter(String apntype);

    String[] getActiveApnTypes();
}

