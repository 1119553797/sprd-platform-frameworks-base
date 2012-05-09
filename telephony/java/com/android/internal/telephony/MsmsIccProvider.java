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

package com.android.internal.telephony;

import android.content.ContentProvider;
import android.content.UriMatcher;
import android.content.ContentValues;
import android.database.AbstractCursor;
import android.database.Cursor;
import android.database.CursorWindow;
import android.net.Uri;
import android.os.SystemProperties;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;

import com.android.internal.telephony.IccConstants;
import com.android.internal.telephony.AdnRecord;
import com.android.internal.telephony.IIccPhoneBook;


/**
 * {@hide}
 */
public class MsmsIccProvider extends IccProvider {
    private static final String TAG = "MsmsIccProvider";
    private static final boolean DBG = true;


    private static final String[] ADDRESS_BOOK_COLUMN_NAMES = new String[] {
            "name", "number", "email", "anr", "aas", "sne", "grp", "gas",
            "sim_index" // add sim index column
    };

    private static final int ADN = 1;
    private static final int FDN = 2;
    private static final int SDN = 3;
    private static final int FDN_S = 4;

    private static final String STR_TAG = "tag";
    private static final String STR_NUMBER = "number";
    private static final String STR_EMAILS = "email";
    private static final String STR_PIN2 = "pin2";
    private static final String STR_ANR = "anr";
    private static final String STR_AAS = "aas";
    private static final String STR_SNE = "sne";
    private static final String STR_GRP = "grp";
    private static final String STR_GAS = "gas";

    private static final int  PHONE_COUNT = PhoneFactory.getPhoneCount();
    private static final int  MAX_MATCH_TYPE = 8;

    private static final UriMatcher URL_MATCHER =
                            new UriMatcher(UriMatcher.NO_MATCH);

    static {
    	for(int i = 0;i <= PHONE_COUNT; i++){
    			URL_MATCHER.addURI(getIccName(i), "adn", getCompoundMatchValue(i, ADN));
                URL_MATCHER.addURI(getIccName(i), "fdn", getCompoundMatchValue(i, FDN));
                URL_MATCHER.addURI(getIccName(i), "sdn", getCompoundMatchValue(i, SDN));
                URL_MATCHER.addURI(getIccName(i), "fdn_s", getCompoundMatchValue(i, FDN_S));
    	}
    }


    private boolean mSimulator;

    public static class AdnComparator implements Comparator<AdnRecord> {
        public final int compare(AdnRecord a, AdnRecord b) {
            String alabel = a.getAlphaTag();
            String blabel = b.getAlphaTag();
            // modified by XJF 2011-5-23 for the bug: can't compare records when
            // some label is null
            if (alabel != null && blabel != null) {
                // return alabel.compareToIgnoreCase(blabel);
            } else if (alabel == null) {
                Log.e(TAG, "alabel == null");
                alabel = "";
            } else if (blabel == null) {
                Log.e(TAG, "blabel == null");
                blabel = "";
            }
            return alabel.compareToIgnoreCase(blabel);
            // modified end.
        }
    }

    private AdnComparator mAdnComparator = new AdnComparator();

    @Override
    public boolean onCreate() {
        String device = SystemProperties.get("ro.product.device");
        if (!TextUtils.isEmpty(device)) {
            mSimulator = false;
        } else {
            // simulator
            mSimulator = true;
        }

        return true;
    }

    @Override
    public Cursor query(Uri url, String[] projection, String selection,
            String[] selectionArgs, String sort) {
        ArrayList<ArrayList> results;

        if (!mSimulator) {
        	int match = URL_MATCHER.match(url);
            switch (getMatch(match)) {
                case ADN:
                    results = loadFromEf(IccConstants.EF_ADN, getPhoneId(match));
                    break;

                case FDN:
                    results = loadFromEf(IccConstants.EF_FDN, getPhoneId(match));
                    break;

                case SDN:
                    results = loadFromEf(IccConstants.EF_SDN, getPhoneId(match));
                    break;

                case FDN_S:
                    results = getEfSize(IccConstants.EF_FDN, getPhoneId(match));
                    break;

                default:
                    throw new IllegalArgumentException("Unknown URL " + url);
            }
        } else {
            // Fake up some data for the simulator
            results = new ArrayList<ArrayList>(4);
            ArrayList<String> contact;

            contact = new ArrayList<String>();
            contact.add("Ron Stevens/H");
            contact.add("512-555-5038");
            results.add(contact);

            contact = new ArrayList<String>();
            contact.add("Ron Stevens/M");
            contact.add("512-555-8305");
            results.add(contact);

            contact = new ArrayList<String>();
            contact.add("Melissa Owens");
            contact.add("512-555-8305");
            results.add(contact);

            contact = new ArrayList<String>();
            contact.add("Directory Assistence");
            contact.add("411");
            results.add(contact);
        }

        return new ArrayListCursor(ADDRESS_BOOK_COLUMN_NAMES, results);
    }

    @Override
    public String getType(Uri url) {
        switch (getMatch(URL_MATCHER.match(url))) {
            case ADN:
            case FDN:
            case SDN:
                return "vnd.android.cursor.dir/sim-contact";

            default:
                throw new IllegalArgumentException("Unknown URL " + url);
        }
    }

    @Override
    public Uri insert(Uri url, ContentValues initialValues) {
        Uri resultUri;
        int efType;
        String pin2 = null;

        if (DBG) log("insert");

        int match = URL_MATCHER.match(url);
        int phoneId = getPhoneId(match);
        switch (getMatch(match)) {
            case ADN:
                efType = IccConstants.EF_ADN;
                break;

            case FDN:
                efType = IccConstants.EF_FDN;
                pin2 = initialValues.getAsString("pin2");
                break;

            default:
                throw new UnsupportedOperationException(
                        "Cannot insert into URL: " + url);
        }

        String tag = initialValues.getAsString("newTag");
        if (tag == null) {
            Log.e(TAG, "error, no name input");
            return null;
        }
        String number = initialValues.getAsString("newNumber");
        // TODO(): Read email instead of sending null.

        String mEmail = initialValues.getAsString("newEmail");
        String[] emails = null;
        if (mEmail != null) {
            emails = new String[1];
            emails[0] = mEmail;
        }

        String anr = initialValues.getAsString("newAnr");
        String aas = initialValues.getAsString("aas");
        String sne = initialValues.getAsString("sne");
        String grp = initialValues.getAsString("grp");
        String gas = initialValues.getAsString("gas");

        if (DBG)
            log("insert tag: " + tag + ",number : " + number + ",anr : " + anr
                    + ",aas : " + aas + ",sne : " + sne + ",grp : " + grp
                    + ",gas :" + gas + ",Email  :" + (emails == null ? "null":emails[0]));


        // yeezone:jinwei return sim index after add a new contact in SimCard.

        //int simIndex = addIccRecordToEf(efType, tag, number, emails, anr, aas,
        //        sne, grp, gas, pin2);



        boolean success = addIccRecordToEf(efType, tag, number, emails, anr, aas,
                sne, grp, gas, pin2, phoneId);

        if (!success) {
            return null;
        }

        StringBuilder buf = new StringBuilder("content://"+getIccName(phoneId)+"/");
        switch (match) {
            case ADN:
                buf.append("adn/");
                break;

            case FDN:
                buf.append("fdn/");
                break;
        }

        // TODO: we need to find out the rowId for the newly added record
        // yeezone:jinwei
        // buf.append(0);
        //buf.append(simIndex);
        // end
        /*synchronized (ArrayListCursor.mIdIndexMap) {
            ArrayListCursor.mMaxId++;
            ArrayListCursor.mIdIndexMap.put(ArrayListCursor.mMaxId, simIndex);
        }
        if (DBG)
            log("GSM insert: ID: " + ArrayListCursor.mMaxId
            +  ",Map size: "
                    + ArrayListCursor.mIdIndexMap.size());*/
        resultUri = Uri.parse(buf.toString());

          if (DBG)
            log("insert resultUri  " +resultUri );

        if (resultUri != null){
            getContext().getContentResolver().notifyChange(resultUri, null, false);
        }
        return resultUri;
    }

    private String normalizeValue(String inVal) {
        int len = inVal.length();
        String retVal = inVal;

        if (inVal.charAt(0) == '\'' && inVal.charAt(len-1) == '\'') {
            retVal = inVal.substring(1, len-1);
        }

        return retVal;
    }

    @Override
    public int delete(Uri url, String where, String[] whereArgs) {
        int efType;
        boolean isFdn = false;

        if (DBG) log("delete");

        int match = URL_MATCHER.match(url);
        int phoneId = getPhoneId(match);
        switch (getMatch(match)) {
            case ADN:
                efType = IccConstants.EF_ADN;
                break;

            case FDN:
                efType = IccConstants.EF_FDN;
                isFdn = true;
                break;

            default:
                throw new UnsupportedOperationException(
                        "Cannot insert into URL: " + url);
        }

        boolean success = false;
        /*
         * if(where.equals("sim_index=?")){ int sim_index =
         * Integer.valueOf(whereArgs[0]); success =
         * deleteIccRecordFromEfByIndex(efType, sim_index,null); //end }else{
         *
         * }
         */

        // parse where clause
        String tag = "";
        String number = "";
        String[] emails = new String[1];;
        String pin2 = null;
        String anr = "";

        if(whereArgs == null || whereArgs.length == 0){
            String[] tokens = where.split("AND");
            int n = tokens.length;
            while (--n >= 0) {
                String param = tokens[n];
                if (DBG) log("parsing '" + param + "'");

                String[] pair = param.split("=");

                if (pair.length != 2) {
                    Log.e(TAG, "resolve: bad whereClause parameter: " + param);
                    continue;
                }

                String key = pair[0].trim();
                String val = pair[1].trim();

                if (STR_TAG.equals(key)) {
                    tag = normalizeValue(val);
                } else if (STR_NUMBER.equals(key)) {
                    number = normalizeValue(val);
                } else if (STR_EMAILS.equals(key)) {
                    // TODO(): Email is null.
                    emails[0] = normalizeValue(val);
                    if (DBG)
                        log("delete emails[0] " + emails[0]);
                } else if (STR_PIN2.equals(key)) {
                    pin2 = normalizeValue(val);
                } else if (STR_ANR.equals(key)) {
                    anr = normalizeValue(val);
                }
            }

        } else {
    		if(isFdn){
            	tag = whereArgs[0];
            	number = whereArgs[1];
    		    pin2 = whereArgs[2];

    		}else{
            	tag = whereArgs[0];
            	number = whereArgs[1];
            	anr = whereArgs[2];
            	emails[0] = whereArgs[3];
				if(whereArgs.length  == 5){
					pin2 = whereArgs[4];
    			}
    		}
        }

        /*
         * if (TextUtils.isEmpty(tag)) { return 0; }
         */
        /*
         * (pair.length != 2) { Log.e(TAG, "resolve: bad whereClause: " +
         * where); return 0; }
         */

        /*if ("_id".equals(key)) {
            delId = Long.parseLong(normalizeValue(val));
            if (ArrayListCursor.mIdIndexMap.containsKey(delId)) {
                delIndex = (int) (ArrayListCursor.mIdIndexMap.get(delId));
            } else {
                return 0;
            }
        }*/

        if (efType == FDN && TextUtils.isEmpty(pin2)) {
            return 0;
        }

        if (DBG)
            log("delete tag: " + tag + ",number : " + number + ",anr : " + anr + ",Email  :" + emails[0]);

        success = deleteIccRecordFromEf(efType, tag, number, emails, anr, pin2, phoneId);

        if (DBG)
            log("delete  success " + success);

        if (!success) {
            return 0;
        } else {
            /*synchronized (ArrayListCursor.mIdIndexMap) {
                ArrayListCursor.mIdIndexMap.remove(delId);
                if (DBG)
                    log("after delete Map size:"
                            + ArrayListCursor.mIdIndexMap.size());
            }*/
            getContext().getContentResolver().notifyChange(
            		Uri.parse("content://"+getIccName(phoneId)+"/adn"), null);
        }
        return 1;
    }

    @Override
    public int update(Uri url, ContentValues values, String where, String[] whereArgs) {
        int efType;
        String pin2 = null;

        if (DBG) log("update");

        int match = URL_MATCHER.match(url);
        int phoneId = getPhoneId(match);
        switch (getMatch(match)) {
            case ADN:
                efType = IccConstants.EF_ADN;
                break;

            case FDN:
                efType = IccConstants.EF_FDN;
                pin2 = values.getAsString("pin2");
                break;

            default:
                throw new UnsupportedOperationException(
                        "Cannot insert into URL: " + url);
        }

        String tag = values.getAsString("tag");
        String number = values.getAsString("number");
        String anr = values.getAsString("anr");
        String aas = values.getAsString("aas");
        String sne = values.getAsString("sne");
        String grp = values.getAsString("grp");
        String gas = values.getAsString("gas");

        String[] emails = new String[1];
        String mEmail = values.getAsString("email");

        emails[0] = mEmail;

        if (DBG)
            log("update tag: " + tag + ",number : " + number + ",anr : " + anr
                    + ",aas : " + aas + ",sne : " + sne + ",grp : " + grp
                    + ",gas :" + gas + ",Email  :" + (emails == null ? "null":emails[0]));

        String newTag = values.getAsString("newTag");
        String newNumber = values.getAsString("newNumber");
        Integer sim_index = values.getAsInteger("sim_index"); // yeezone:jinwei
        String[] newemails = new String[1];
        String nEmail = values.getAsString("newEmail");

        newemails[0] = nEmail;
        /*if (TextUtils.isEmpty(newTag)) {
            newTag = tag;
        }
        if (TextUtils.isEmpty(newNumber)) {
            newNumber = number;
        }*/

        String newanr = values.getAsString("newAnr");
        String newaas = values.getAsString("newAas");
        String newsne = values.getAsString("newAne");
        String newgrp = values.getAsString("newGrp");
        String newgas = values.getAsString("newGas");

        int updateIndex = -1;
        long updateId = -1;

        if (DBG)
            log("update  new >>> tag: " + newTag + ",number : " + newNumber
                    + ",anr : " + newanr + ",aas : " + newaas + ",sne : " + newsne
                    + ",grp : " + newgrp + ",gas :" + newgas + ",Email  :"
                    + newemails[0]);
        /*
         * String[] pair = where.split("=");
         * Log.e("TAG","wangtong0: pair_.length is   " + pair.length); if
         * (pair.length != 2) { Log.e(TAG, "resolve: bad whereClause: " +
         * where); return 0; }
         *
         *
         * String key = pair[0].trim(); String val = pair[1].trim(); if
         * ("_id".equals(key)) { updateId = Long.parseLong(normalizeValue(val));
         * if(ArrayListCursor.mIdIndexMap.containsKey(updateId)) { updateIndex =
         * (int) (ArrayListCursor.mIdIndexMap.get(updateId)); }else { return 0;
         * } }
         */

        boolean success = false;

        success = updateIccRecordInEf(efType, tag, number, emails, anr, newTag,
                newNumber, newemails, newanr, newaas, newsne, newgrp, newgas,
                pin2, phoneId);


        if (!success) {
            return 0;
        } else {
            getContext().getContentResolver().notifyChange(
                    Uri.parse("content://"+getIccName(phoneId)+"/adn"), null);
        }

        return 1;
    }

    private ArrayList<ArrayList> getEfSize(int efType, int phoneId) {
        ArrayList<ArrayList> results = new ArrayList<ArrayList>();
        int[] adnRecordSize = null;

        if (DBG) log("getEfSize: efType=" + efType);

        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                    ServiceManager.getService(PhoneFactory.getServiceName("simphonebook", phoneId)));
            if (iccIpb != null) {
                adnRecordSize = iccIpb.getAdnRecordsSize(efType);
            }
        } catch (RemoteException ex) {
            if (DBG) log(ex.toString());
        } catch (SecurityException ex) {
            if (DBG) log(ex.toString());
        }
        if (adnRecordSize != null) {
            // Load the results
            ArrayList al = new ArrayList();
            al.add(adnRecordSize[2]);
            results.add(al);
        } else {
            Log.w(TAG, "Cannot load ADN records");
            results.clear();
        }
        if (DBG) log("loadFromEf: return results");
        return results;
    }

    private ArrayList<ArrayList> loadFromEf(int efType, int phoneId) {
        ArrayList<ArrayList> results = new ArrayList<ArrayList>();
        List<AdnRecord> adnRecords = null;

        if (DBG) log("loadFromEf: efType=" + efType + ", phoneId=" + phoneId);

        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                    ServiceManager.getService(PhoneFactory.getServiceName("simphonebook", phoneId)));
            if (iccIpb != null) {
                adnRecords = iccIpb.getAdnRecordsInEf(efType);
            }
        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG) log(ex.toString());
        }
        if (adnRecords != null) {
            // Load the results

            int N = adnRecords.size();
            if (DBG) log("adnRecords.size=" + N);
            for (int i = 0; i < N ; i++) {
                loadRecord(adnRecords.get(i), results);
            }
        } else {
            // No results to load
            Log.w(TAG, "Cannot load ADN records");
            results.clear();
        }
        if (DBG) log("loadFromEf: return results");
        return results;
    }

    private boolean addIccRecordToEf(int efType, String name, String number,
            String[] emails, String anr, String aas, String sne, String grp,
            String gas, String pin2, int phoneId) {
        if (DBG)
            log("addIccRecordToEf: efType=" + efType + ", name=" + name
                    + ", number=" + number + ",anr= "+anr+", emails=" + emails);
        boolean success = false;

        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub
                    .asInterface(ServiceManager.getService(PhoneFactory.getServiceName("simphonebook", phoneId)));
            if (iccIpb != null) {
                success = iccIpb.updateAdnRecordsInEfBySearch(efType, "", "",
                        null, "", name, number, emails, anr, aas, sne, grp,
                        gas, pin2);
            }

        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG)
                log(ex.toString());
        }
        if (DBG)
            log("updateIccRecordInEf: " + success);
        return success;
    }

    private boolean updateIccRecordInEf(int efType, String oldName,
            String oldNumber, String[] oldEmailList, String oldAnr,
            String newName, String newNumber, String[] newEmailList,
            String newAnr, String newAas, String newSne, String newGrp,
            String newGas, String pin2, int phoneId) {
        if (DBG)
            log("updateIccRecordInEf: efType = " + efType + ", oldname = "
                    + oldName + ", oldnumber = " + oldNumber + ",oldAnr = " + oldAnr
                    + ", newname = " + newName + ", newnumber = " + newNumber
                    + ",newAnr = " + newAnr);
        boolean success = false;

        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                    ServiceManager.getService(PhoneFactory.getServiceName("simphonebook", phoneId)));
            if (iccIpb != null) {
                success = iccIpb.updateAdnRecordsInEfBySearch(efType, oldName,
                        oldNumber, oldEmailList, oldAnr, newName, newNumber,
                        newEmailList, newAnr, newAas, newSne, newGrp, newGas,
                        pin2);
            }
        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG) log(ex.toString());
        }
        if (DBG) log("updateIccRecordInEf: " + success);
        return success;
    }

    // yeezone:jinwei update icc record from sim
    private boolean updateIccRecordInEfByIndex(int efType, String newName,
            String newNumber, List<String> newEmailList, String newAnr,
            String newAas, String newSne, String newGrp, String newGas,
            int sim_index, String pin2, int phoneId) {
        if (DBG)
            log("updateIccRecordInEfByIndex: efType=" + efType + ", newname="
                    + newName + ", newnumber=" + newNumber + ", newEmailList="
                    + newEmailList + ", newAnr=" + newAnr + ", newSne="
                    + newSne + ", index=" + sim_index);
        boolean success = false;

        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                    ServiceManager.getService(PhoneFactory.getServiceName("simphonebook", phoneId)));
            if (iccIpb != null) {
                success = iccIpb.updateAdnRecordsInEfByIndex(efType, newName,
                        newNumber, newEmailList, newAnr, newAas, newSne,
                        newGrp, newGas, sim_index, pin2);
            }
        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG) log(ex.toString());
        }
        if (DBG) log("updateIccRecordInEf: " + success);
        return success;
    }


    private boolean deleteIccRecordFromEf(int efType, String name,
            String number, String[] emails, String anr, String pin2, int phoneId) {
        if (DBG)
            log("deleteIccRecordFromEf: efType=" + efType + ", name=" + name
                    + ", number=" + number +",anr=" + anr + ", pin2=" + pin2);

        boolean success = false;

        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub
                    .asInterface(ServiceManager.getService(PhoneFactory.getServiceName("simphonebook", phoneId)));
            if (iccIpb != null) {
                success = iccIpb.updateAdnRecordsInEfBySearch(efType, name,
                        number, emails, anr, "", "", null, "", "", "", "", "",
                        pin2);

            }
        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG) log(ex.toString());
        }
        if (DBG) log("deleteIccRecordFromEf: " + success);
        return success;
    }

    private boolean deleteIccRecordFromEfByIndex(int efType, int index,
            String pin2) {
        if (DBG)
            log("deleteIccRecordFromEfByIndex: efType=" + efType + ", index="
                    + index + ", pin2=" + pin2);

        boolean success = false;

        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub
                    .asInterface(ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                success = iccIpb.updateAdnRecordsInEfByIndex(efType, "", "",
                        null, "", "", "", "", "", index, pin2);

            }
        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG)
                log(ex.toString());
        }
        if (DBG)
            log("deleteIccRecordFromEfByIndex: " + success);
        return success;
    }

    // end delete icc record from sim

    /**
     * Loads an AdnRecord into an ArrayList. Must be called with mLock held.
     *
     * @param record the ADN record to load from
     * @param results the array list to put the results in
     */
    private void loadRecord(AdnRecord record,
            ArrayList<ArrayList> results) {
        if (!record.isEmpty()) {
            ArrayList<String> contact = new ArrayList<String>();
            String alphaTag = record.getAlphaTag();
            String number = record.getNumber();
            String[] emails = record.getEmails();
            String anr = record.getAnr();
            String aas = record.getAas();
            String sne = record.getSne();
            String grp = record.getGrp();
            String gas = record.getGas();
            int index = record.getIndex();

            // yeezone:jinwei get sim index from adn record
            String sim_index = String.valueOf(record.getRecordNumber());
            Log.d("IccProvider", "**loadRecord::sim_index = " + sim_index);

            if (DBG)
                log("loadRecord: " + alphaTag + ", " + number + "," + anr);

            contact.add(alphaTag);
            contact.add(number);
            StringBuilder emailString = new StringBuilder();

            if (emails != null) {
                for (int i = 0; i < emails.length; i++) {
                    if (emails[i] != null) {
                        if (DBG)
                            log("loadRecord: email " + emails[i]);
                    }
                }
                for (String email : emails) {
                    if (DBG)
                        log("Adding email:" + email);
                    emailString.append(email);
                    emailString.append(",");
                }
                String mEmail = emails[0];
                contact.add(mEmail);
                // contact.add(emailString.toString());
            } else {
                contact.add(null);
            }
            contact.add(anr);
            contact.add(aas);
            contact.add(sne);
            contact.add(grp);
            contact.add(gas);
            contact.add(sim_index); // yeezone:jinwei
            results.add(contact);
        }
    }

    private void log(String msg) {
        Log.d(TAG, "[IccProvider] " + msg);
    }


    private int getPhoneId(int compoundMatch){
    	return compoundMatch / MAX_MATCH_TYPE;
    }

    private int getMatch(int compoundMatch){
    	return  compoundMatch % MAX_MATCH_TYPE;
    }


    private static int getCompoundMatchValue(int phoneId, int match)
	{
	    return phoneId * MAX_MATCH_TYPE + match;
	}

    private static String getIccName(int phoneId) {
        if (PhoneFactory.isMultiSim()) {
            if (phoneId == PhoneFactory.getPhoneCount()) {
                return "icc";
            }
            return "icc" + phoneId;
        } else {
            return "icc";
        }
    }
}
