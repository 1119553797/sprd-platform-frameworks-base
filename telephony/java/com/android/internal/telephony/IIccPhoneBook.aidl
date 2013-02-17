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

package com.android.internal.telephony;

import com.android.internal.telephony.AdnRecord;



/** Interface for applications to access the ICC phone book.
 *
 * <p>The following code snippet demonstrates a static method to
 * retrieve the IIccPhoneBook interface from Android:</p>
 * <pre>private static IIccPhoneBook getSimPhoneBookInterface()
            throws DeadObjectException {
    IServiceManager sm = ServiceManagerNative.getDefault();
    IIccPhoneBook spb;
    spb = IIccPhoneBook.Stub.asInterface(sm.getService("iccphonebook"));
    return spb;
}
 * </pre>
 */

interface IIccPhoneBook {

    /**
     * Loads the AdnRecords in efid and returns them as a
     * List of AdnRecords
     *
     * @param efid the EF id of a ADN-like SIM
     * @return List of AdnRecord
     */
    List<AdnRecord> getAdnRecordsInEf(int efid);

    /**
     * Replace oldAdn with newAdn in ADN-like record in EF
     *
     * getAdnRecordsInEf must be called at least once before this function,
     * otherwise an error will be returned
     *
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param oldTag adn tag to be replaced
     * @param oldPhoneNumber adn number to be replaced
     *        Set both oldTag and oldPhoneNubmer to "" means to replace an
     *        empty record, aka, insert new record
     * @param newTag adn tag to be stored
     * @param newPhoneNumber adn number ot be stored
     *        Set both newTag and newPhoneNubmer to "" means to replace the old
     *        record with empty one, aka, delete old record
     * @param pin2 required to update EF_FDN, otherwise must be null
     * @return true for success
     */
    boolean updateAdnRecordsInEfBySearch(int efid,
            String oldTag, String oldPhoneNumber,
            String newTag, String newPhoneNumber,
            String pin2);

    /**
     * Get the max munber of email in efid
     *
     *
     * @return  int[3] array
     *            recordSizes[0]  is the single record length
     *            recordSizes[1]  is the total length of the EF file
     *            recordSizes[2]  is the number of records in the EF file
     */
    int[] getEmailRecordsSize();
    /**
     * Get the max munber of anr in efid
     *
     *
     * @return  int[3] array
     *            recordSizes[0]  is the single record length
     *            recordSizes[1]  is the total length of the EF file
     *            recordSizes[2]  is the number of records in the EF file
     */
    int[] getAnrRecordsSize();

    int getAnrNum();

    int getEmailNum();

    int getInsertIndex();



    //add multi record and email in usim
    //yeezone:jinwei Add a new contact in SimCard.
     /**Add newAdn in ADN-like record in EF
      * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
      * @param newTag adn tag to be stored
      * @param newPhoneNumber adn number ot be stored
      *        Set both newTag and newPhoneNubmer to "" means to replace the old
      *        record with empty one, aka, delete old record
      * @param pin2 required to update EF_FDN, otherwise must be null
      * @return sim_index
     */
    int
    updateAdnRecordsInEfBySearchEx(int efid,
            String oldTag, String oldPhoneNumber,in String[] oldEmailList, String oldAnr,
            String oldSne, String oldGrp,
            String newTag, String newPhoneNumber,in String[] newEmailList,
            String newAnr,String newAas, String newSne, String newGrp,
            String newGas,  String pin2);
       /**
     * Update an ADN-like EF record by record index
     *
     * This is useful for iteration the whole ADN file, such as write the whole
     * phone book or erase/format the whole phonebook
     *
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param newTag adn tag to be stored
     * @param newPhoneNumber adn number to be stored
     *        Set both newTag and newPhoneNubmer to "" means to replace the old
     *        record with empty one, aka, delete old record
     * @param index is 1-based adn record index to be updated
     * @param pin2 required to update EF_FDN, otherwise must be null
     * @return true for success
     */
    boolean updateAdnRecordsInEfByIndex(int efid, String newTag,
            String newPhoneNumber, int index,
            String pin2);

    /**
     * Get the max munber of records in efid
     *
     * @param efid the EF id of a ADN-like SIM
     * @return  int[3] array
     *            recordSizes[0]  is the single record length
     *            recordSizes[1]  is the total length of the EF file
     *            recordSizes[2]  is the number of records in the EF file
     */
    int[] getAdnRecordsSize(int efid);
    int updateAdnRecordsInEfByIndexEx(int efid, String newTag, String newPhoneNumber,
				in String[] newEmailList,String newAnr,String newAas, String newSne,String newGrp, String      newGas,  int index, String pin2);
    int[] getAvalibleEmailCount(String name, String number,in String[] emails, String anr, in int[] emailNums);
    int [] getAvalibleAnrCount(String name, String number,in String[] emails, String anr, in int[] anrNums);
    boolean isApplicationOnIcc(int appType);
    int updateUsimGroupBySearchEx(String oldName,String newName);
    boolean updateUsimGroupById(String newName,int groupId);
    List<String> getGasInEf();
    int getEmailMaxLen();
    int getPhoneNumMaxLen();
}
