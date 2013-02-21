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

import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.TextUtils;
import android.util.Log;

import java.util.Comparator;
import java.util.List;


/**
 * {@hide}
 */
public class MsmsIccProvider extends IccProvider {
    private static final String TAG = "MsmsIccProvider";
    private static final boolean DBG = true;


    private static final String[] ADDRESS_BOOK_COLUMN_NAMES = new String[] {
            "name", "number", "email", "anr", "aas", "sne", "grp", "gas",
            "index",    // add sim index column
            "_id",      // for SimpleCursorAdapter in ADNList
    };

    private static final String[] FDN_S_COLUMN_NAMES = new String[] {
        "size"
    };
    private static final String[] SIM_GROUP_PROJECTION = new String[] {
        "gas", "index" 
    };

    private static final int ADN = 1;
    private static final int FDN = 2;
    private static final int SDN = 3;
    private static final int FDN_S = 4;
    private static final int GAS = 5;

    private static final String STR_TAG = "tag";
    private static final String STR_NUMBER = "number";
    private static final String STR_EMAILS = "email";
    private static final String STR_PIN2 = "pin2";
    private static final String STR_ANR = "anr";
    private static final String STR_AAS = "aas";
    private static final String STR_SNE = "sne";
    private static final String STR_GRP = "grp";
    private static final String STR_GAS = "gas";
    private static final String STR_INDEX= "index";
    private static final String STR_NEW_TAG= "newTag";
    private static final String STR_NEW_NUMBER= "newNumber";
        
    private static final String AUTHORITY = "icc";
    private static final String CONTENT_URI = "content://" + AUTHORITY + "/";
    private static final int  PHONE_COUNT = PhoneFactory.getPhoneCount();
    private static final int INVALID_PHONE_ID = 10000;
    public static final String WITH_EXCEPTION = "with_exception";
    private static final UriMatcher URL_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    /** this is only to match phoneId, it do not care whether the uri is illegal **/
    private static final UriMatcher PHONE_ID_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        URL_MATCHER.addURI(AUTHORITY, "adn", ADN);
        URL_MATCHER.addURI(AUTHORITY, "fdn", FDN);
        URL_MATCHER.addURI(AUTHORITY, "sdn", SDN);
        URL_MATCHER.addURI(AUTHORITY, "fdn_s", FDN_S);
        URL_MATCHER.addURI(AUTHORITY, "gas", GAS);

        URL_MATCHER.addURI(AUTHORITY, "#/adn", ADN);
        URL_MATCHER.addURI(AUTHORITY, "#/fdn", FDN);
        URL_MATCHER.addURI(AUTHORITY, "#/sdn", SDN);
        URL_MATCHER.addURI(AUTHORITY, "#/fdn_s", FDN_S);
        URL_MATCHER.addURI(AUTHORITY, "#/gas", GAS);

        // to match phoneId
        for (int i = 0; i < PHONE_COUNT; i++) {
            PHONE_ID_MATCHER.addURI(AUTHORITY, i + "/*", i);
        }
        PHONE_ID_MATCHER.addURI(AUTHORITY, "#/*", INVALID_PHONE_ID);
        PHONE_ID_MATCHER.addURI(AUTHORITY, "*", PHONE_COUNT);
    }


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
        return true;
    }

    @Override
    public Cursor query(Uri url, String[] projection, String selection,
            String[] selectionArgs, String sort) {
        Log.e(TAG, "query, uri:"+url);
        int match = URL_MATCHER.match(url);
        switch (match) {
            case ADN:
                return loadFromEf(IccConstants.EF_ADN, getPhoneId(url));

            case FDN:
                return loadFromEf(IccConstants.EF_FDN, getPhoneId(url));

            case SDN:
                return loadFromEf(IccConstants.EF_SDN, getPhoneId(url));

            case FDN_S:
                return getEfSize(IccConstants.EF_FDN, getPhoneId(url));

            case GAS:
                return loadGas(getPhoneId(url));

            default:
                throw new IllegalArgumentException("Unknown URL " + url);
        }
    }

    @Override
    public String getType(Uri url) {
        switch (URL_MATCHER.match(url)) {
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
        int efType = -1;
        String pin2 = null;
        boolean isGas = false;

        if (DBG)
            log("insert");
        Log.i(TAG, "insert, uri:"+url+" initialValues:"+initialValues);

        int match = URL_MATCHER.match(url);
        int phoneId = getPhoneId(url);
        if (DBG) Log.i(TAG, "insert, match:" + match + ", getPhoneId:" + phoneId);
        switch (match) {
            case ADN:
                efType = IccConstants.EF_ADN;
                break;

            case FDN:
                efType = IccConstants.EF_FDN;
                pin2 = initialValues.getAsString("pin2");
                break;

            case GAS:
                isGas = true;
                break;
                
            default:
                throw new UnsupportedOperationException(
                        "Cannot insert into URL: " + url);
        }

        String tag = initialValues.getAsString("tag");
        
        //for bugzilla 608
        if (tag == null) {
            Log.e(TAG, "no name input");
            //return null;
        }
        String number = initialValues.getAsString("number");
        // TODO(): Read email instead of sending null.

        String mEmail = initialValues.getAsString("email");
        String[] emails = null;
        if (mEmail != null) {
            emails = new String[1];
            emails[0] = mEmail;
        }

        String anr = initialValues.getAsString("anr");
        String aas = initialValues.getAsString("aas");
        String sne = initialValues.getAsString("sne");
        String grp = initialValues.getAsString("grp");
        String gas = initialValues.getAsString("gas");     

        if (DBG)
            log("insert, tag:" + tag + ",  number:" + number + ",  anr:" + anr
                    + ",  aas:" + aas + ",  sne:" + sne + ",  grp:" + grp
                    + ",  gas:" + gas + ",  Email:" + (emails == null ? "null":emails[0]));

        int index = -1;         //maybe simIndex or groupId
        if(isGas){
            
            index= addUsimGroup(gas, phoneId);

        }else{
            //boolean success = addIccRecordToEf(efType, tag, number, null, pin2);
            index = addIccRecordToEf(efType, tag, number, emails, anr, aas,
                    sne, grp, gas, pin2, phoneId);
        }
             
        if(index < 0){
            int errorCode = index + 1;
            if (url.getBooleanQueryParameter(WITH_EXCEPTION, false)) {
                Log.d(TAG, "throw exception");
                throwException(errorCode);
            }
            return null;
        }

        StringBuilder buf = new StringBuilder(CONTENT_URI);
        switch (match) {
            case ADN:
                buf.append(getPathName("adn", phoneId));
                buf.append("/" + index);
                break;

            case GAS:
                buf.append(getPathName("gas", phoneId));
                buf.append("/" + index);
                break;

            case FDN:
                buf.append(getPathName("fdn", phoneId));
                break;
        }

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
        boolean isGas = false;

        if (DBG)
            log("delete");

        Log.i(TAG, "delete, uri:"+url+" where:"+where+" whereArgs:"+whereArgs);

        int match = URL_MATCHER.match(url);
        int phoneId = getPhoneId(url);
        switch (match) {
            case ADN:
                efType = IccConstants.EF_ADN;
                break;

            case FDN:
                efType = IccConstants.EF_FDN;
                isFdn = true;
                break;

            case GAS:
                efType = IccConstants.EF_ADN;
                isGas = true;
                break;
                
            default:
                throw new UnsupportedOperationException(
                        "Cannot insert into URL: " + url);
        }

        boolean success = false;
        int recIndex = -1;
        // parse where clause
        int index = -1;         //maybe simIndex or groupId
        String tag = "";
        String number = "";
        String pin2 = null;

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
                } else if (STR_INDEX.equals(key)) {
                    index= Integer.valueOf(normalizeValue(val));
                } else if (STR_PIN2.equals(key)) {
                    pin2 = normalizeValue(val);
                } 
            }
        }        
        else {
            tag = whereArgs[0];
            number = whereArgs[1]; 
            pin2 = whereArgs[2];
        }

        if (efType == FDN && TextUtils.isEmpty(pin2)) {
            return 0;
        }

        if (DBG)
            log("delete tag: " + tag + ", number:" + number + ", index:" + index);

        if(isFdn){
            if(-1!=deleteIccRecordFromEf(efType, tag, number, null, "", "", "", pin2, phoneId)) success = true;
        }else if(isGas){
            success = updateUsimGroupById("", index, phoneId);
        }else{
            recIndex = deleteIccRecordFromEfByIndex(efType, index, pin2, phoneId);
            if (recIndex < 0) {
                success = false;         
            }else {
                success = true;
            }
        }       
        if (DBG)
            log("delete result: " + success);

        if (!success) {
            return 0;
        } else {
            getContext().getContentResolver().notifyChange(
                    Uri.parse(CONTENT_URI + getPathName("adn", phoneId)), null);
        }
        return 1;
    }

    @Override
    public int update(Uri url, ContentValues values, String where, String[] whereArgs) {
        int efType = -1;
        String pin2 = null;
        boolean isFdn = false;
        boolean isGas = false;
        if (DBG)
            log("update");

        Log.i(TAG, "update, uri:"+url+" where: "+where+" value: " + values);

        int match = URL_MATCHER.match(url);
        int phoneId = getPhoneId(url);
        switch (match) {
            case ADN:
                efType = IccConstants.EF_ADN;
                break;

            case FDN:
                efType = IccConstants.EF_FDN;
                pin2 = values.getAsString("pin2");
                isFdn = true;
                break;

            case GAS:
                isGas = true;
                break;

            default:
                throw new UnsupportedOperationException(
                        "Cannot insert into URL: " + url);
        }
        /*
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
            log("update, new tag: " + tag + ",  number:" + number + ",  anr:" + anr
                + ",  aas:" + aas + ",  sne:" + sne + ",  grp:" + grp
                + ",  gas:" + gas + ",  Email:" + (emails == null ? "null":emails[0])
                );
         */
        String newTag = values.getAsString(STR_TAG);
        String newNumber = values.getAsString(STR_NUMBER);
        Integer index = values.getAsInteger(STR_INDEX);         //maybe simIndex or groupId
        String newanr = values.getAsString(STR_ANR);
        String newaas = values.getAsString(STR_AAS);
        String newsne = values.getAsString(STR_SNE);
        String newgrp = values.getAsString(STR_GRP);
        String newgas = values.getAsString(STR_GAS);

        String[] newemails = null;
        String newEmail = values.getAsString(STR_EMAILS);
        if(newEmail!=null){
            newemails = new String[1];
            newemails[0] = newEmail;
        }
        
        if (DBG)
            log("update, new tag: " + newTag + ",  number:" + newNumber
                    + ",  anr:" + newanr + ",  aas: " + newaas + ",  sne:" + newsne
                    + ",  grp:" + newgrp + ",  gas :" + newgas + ",  email:"
                    + newEmail + ",  index:"+index);

        boolean success = false;
        int recIndex = -1;

        if(isFdn){
            //added for fdn
            String tag = "";
            String number = "";
            tag = values.getAsString(STR_TAG);
            number = values.getAsString(STR_NUMBER);
            newTag = values.getAsString(STR_NEW_TAG);
            newNumber = values.getAsString(STR_NEW_NUMBER);
            if(0 <= updateIccRecordInEf(efType, tag, number, null, "", "", "", newTag,
                    newNumber, null, "", "", "", "", "", pin2, phoneId))
                success = true;
        }else if(isGas){
            success = updateUsimGroupById(newgas, index, phoneId);
        }else{
            recIndex = updateIccRecordInEfByIndex(efType, newTag,
                    newNumber, newemails, newanr, newaas, newsne, newgrp, newgas,index,
                    pin2, phoneId);
            if (recIndex < 0) {
                success = false;
                if (url.getBooleanQueryParameter(WITH_EXCEPTION, false)) {
                    Log.d(TAG, "throw exception");
                    throwException(recIndex + 1);
                }
            } else {
                success = true;
            }
        }

        if (!success) {
            return 0;
        } else {
            getContext().getContentResolver().notifyChange(
                    Uri.parse(CONTENT_URI + getPathName("adn", phoneId)), null);
        }

        return 1;
    }

    private MatrixCursor getEfSize(int efType, int phoneId) {
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
            MatrixCursor cursor = new MatrixCursor(FDN_S_COLUMN_NAMES, 1);
            Object[] size = new Object[1];
            size[0] = adnRecordSize[2];
            cursor.addRow(size);
			return cursor;
        } else {
            Log.w(TAG, "Cannot load ADN records");
            return new MatrixCursor(FDN_S_COLUMN_NAMES);
        }
    }

    private MatrixCursor loadFromEf(int efType, int phoneId) {
        if (DBG) log("loadFromEf: efType=" + Integer.toHexString(efType) + ", phoneId=" + phoneId);

        List<AdnRecord> adnRecords = null;
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
            final int N = adnRecords.size();
            final MatrixCursor cursor = new MatrixCursor(ADDRESS_BOOK_COLUMN_NAMES, N);
            if (DBG) log("adnRecords.size=" + N);
            for (int i = 0; i < N ; i++) {
                loadRecord(adnRecords.get(i), cursor, i);
            }
            return cursor;
        } else {
            // No results to load
            Log.w(TAG, "Cannot load ADN records");
            return new MatrixCursor(ADDRESS_BOOK_COLUMN_NAMES);
        }
    }
    
    private MatrixCursor loadGas(int phoneId) {
        log("loadGas,phoneId=" + phoneId);

        List<String> adnGas = null;
        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                    ServiceManager.getService(PhoneFactory.getServiceName("simphonebook", phoneId)));
            if (iccIpb != null) {
                adnGas = iccIpb.getGasInEf();
            }
        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG) log(ex.toString());
        }

        if (adnGas != null) {
            // Load the results
            final int N = adnGas.size();
            final MatrixCursor cursor = new MatrixCursor(SIM_GROUP_PROJECTION, N);
            if (DBG) log("adnGas.size=" + N);
            for (int i = 0; i < N ; i++) {
                if(TextUtils.isEmpty(adnGas.get(i))) continue;

                Object[] group = new Object[SIM_GROUP_PROJECTION.length];
                group[0] = adnGas.get(i);
                group[1] = i+1;
                cursor.addRow(group);
                if (DBG) log("loadGas: " + group[1] + ", " + group[0]);                    
            }
            return cursor;
        } else {
            // No results to load
            Log.w(TAG, "Cannot load Gas records");
            return new MatrixCursor(SIM_GROUP_PROJECTION);
        }
    }




    private int addIccRecordToEf(int efType, String name, String number,
            String[] emails, String anr, String aas, String sne, String grp,
            String gas, String pin2, int phoneId) {
        if (DBG)
            log("addIccRecordToEf: efType=" + Integer.toHexString(efType) + ", name=" + name
                    + ", number=" + number + ",anr= "+anr+", emails=" + emails+", grp="+grp);
        int retIndex=-1;
    
        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub
                    .asInterface(ServiceManager.getService(PhoneFactory.getServiceName("simphonebook", phoneId)));
            if (iccIpb != null) {
                retIndex = iccIpb.updateAdnRecordsInEfBySearchEx(efType, "", "",
                        null, "",  "", "", name, number, emails, anr, aas, sne, grp,
                        gas, pin2);
            }
            
        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG)
                log(ex.toString());
        }
        if (DBG)
            log("addIccRecordToEf: " + retIndex);
        return retIndex;
    }
    
    private int updateIccRecordInEf(int efType, String oldName,
            String oldNumber, String[] oldEmailList, String oldAnr,
            String oldSne, String oldGrp,
            String newName, String newNumber, String[] newEmailList,
            String newAnr, String newAas, String newSne, String newGrp,
            String newGas, String pin2, int phoneId) {
        if (DBG)
             log("updateIccRecordInEf: efType = " + Integer.toHexString(efType) + ",  oldname = "
                 + oldName + ",  oldnumber = " + oldNumber + ",  oldAnr = " + oldAnr
                 + ",  newname = " + newName + ",  newnumber = " + newNumber
                 + ",  newAnr = " + newAnr);
        
        int retIndex=-1;

        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                    ServiceManager.getService(PhoneFactory.getServiceName("simphonebook", phoneId)));
            if (iccIpb != null) {
                retIndex = iccIpb.updateAdnRecordsInEfBySearchEx(efType, oldName,
                        oldNumber, oldEmailList, oldAnr, oldSne, oldGrp, newName, newNumber,
                        newEmailList, newAnr, newAas, newSne, newGrp, newGas,
                        pin2);
            }
        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG) log(ex.toString());
        }
        if (DBG) log("updateIccRecordInEf: " + retIndex);
        return retIndex;
    }
    private void throwException(int errorCode)  {     
        switch (errorCode) {
            case IccPhoneBookOperationException.WRITE_OPREATION_FAILED:
                throw new IccPBForRecordException(IccPBForRecordException.WRITE_RECORD_FAILED,
                		"write record failed");   
            
            case IccPhoneBookOperationException.ADN_CAPACITY_FULL:
                throw new IccPBForRecordException(IccPBForRecordException.ADN_RECORD_CAPACITY_FULL,
                		"adn record capacity full");     
                
            case IccPhoneBookOperationException.EMAIL_CAPACITY_FULL:
                throw new IccPBForMimetypeException(IccPBForMimetypeException.CAPACITY_FULL,
                        Email.CONTENT_ITEM_TYPE, "email capacity full");
                
            case IccPhoneBookOperationException.LOAD_ADN_FAIL:
                throw new IccPBForRecordException(IccPBForRecordException.LOAD_RECORD_FAILED,
                		"load adn failed");    
                
            case IccPhoneBookOperationException.OVER_NAME_MAX_LENGTH:
                throw new IccPBForMimetypeException(IccPBForMimetypeException.OVER_LENGTH_LIMIT,
                        CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,"over the length of name ");
                
            case IccPhoneBookOperationException.OVER_NUMBER_MAX_LENGTH:
                throw new IccPBForMimetypeException(IccPBForMimetypeException.OVER_LENGTH_LIMIT,
                        Phone.CONTENT_ITEM_TYPE,"over the length of phone number");
            default:
                break;
        }
    }
    // yeezone:jinwei update icc record from sim
    private int updateIccRecordInEfByIndex(int efType, String newName,
            String newNumber, String[]  newEmailList, String newAnr,
            String newAas, String newSne, String newGrp, String newGas,
            int simIndex, String pin2, int phoneId) {
        if (DBG)
            log("updateIccRecordInEfByIndex: efType=" + efType + ", newname="
                    + newName + ", newnumber=" + newNumber + ", newEmailList="
                    + newEmailList + ", newAnr=" + newAnr + ", newSne="
                    + newSne + ", index=" + simIndex + ", phoneId:"+phoneId);
       // boolean success = false;

        int recIndex = -1;
        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                    ServiceManager.getService(PhoneFactory.getServiceName("simphonebook", phoneId)));
            if (iccIpb != null) {
                recIndex = iccIpb.updateAdnRecordsInEfByIndexEx(efType, newName,
                        newNumber, newEmailList, newAnr, newAas, newSne,
                        newGrp, newGas, simIndex, pin2);
            }
        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG) log(ex.toString());
        }
        if (DBG) log("updateIccRecordInEfByIndex: " + recIndex);
        return recIndex;
    }
    
    private int deleteIccRecordFromEf(int efType, String name,
            String number, String[] emails, String anr, String sne, String grp, String pin2, int phoneId) {
        if (DBG)
            log("deleteIccRecordFromEf: efType=" + efType + ", name=" + name
                    + ", number=" + number +",anr=" + anr + ", pin2=" + pin2);
        
        int retIndex=-1;

        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub
                    .asInterface(ServiceManager.getService(PhoneFactory.getServiceName("simphonebook", phoneId)));
            if (iccIpb != null) {
                retIndex = iccIpb.updateAdnRecordsInEfBySearchEx(efType, name,
                        number, emails, anr, sne, grp, "", "", null, "", "", "", "", "",
                        pin2);

            }
        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG) log(ex.toString());
        }
        if (DBG) log("deleteIccRecordFromEf: " + retIndex);
        return retIndex;
    }

    private int deleteIccRecordFromEfByIndex(int efType, int index, String pin2, int phoneId) {
        if (DBG)
            log("deleteIccRecordFromEfByIndex: efType=" + Integer.toHexString(efType) + ", index="
                    + index + ", pin2=" + pin2);

        boolean success = false;
        int recIndex = -1;

        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub
                    .asInterface(ServiceManager.getService(PhoneFactory.getServiceName("simphonebook", phoneId)));
            if (iccIpb != null) {
                recIndex = iccIpb.updateAdnRecordsInEfByIndexEx(efType, "", "",
                        null, "", "", "", "", "", index, pin2);

            }
        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG)
                log(ex.toString());
        }
        if (recIndex < 0) {
            success = false;
        }else {
            success = true;
        }
        if (DBG)
            log("deleteIccRecordFromEfByIndex: " + success + " recIndex = "+recIndex);
        return recIndex;
    }

    // end delete icc record from sim

    /**
     * Loads an AdnRecord into a MatrixCursor. Must be called with mLock held.
     *
     * @param record the ADN record to load from
     * @param cursor the cursor to receive the results
     */
    private void loadRecord(AdnRecord record, MatrixCursor cursor, int id) {
        if (!record.isEmpty()) {
            Object[] contact = new Object[ADDRESS_BOOK_COLUMN_NAMES.length];
            String alphaTag = record.getAlphaTag();
            String number = record.getNumber();

            if (DBG) log("loadRecord: " + alphaTag + ", " + number + ",");
            contact[0] = alphaTag;
            contact[1] = number;

            String[] emails = record.getEmails();
            String anr = record.getAnr();
            String aas = record.getAas();
            String sne = record.getSne();
            String grp = record.getGrp();
            String gas = record.getGas();

            // yeezone:jinwei get sim index from adn record
            String sim_index = String.valueOf(record.getRecordNumber());
            Log.d("MsmsIccProvider", "loadRecord::sim_index = " + sim_index);


            if (emails != null) {
                StringBuilder emailString = new StringBuilder();
                for (String email: emails) {
                    // if (DBG) log("Adding email:" + email);
                    // emailString.append(email);
                    // emailString.append(",");

                    // only fetch the first item
                    emailString.append(email);
                    break;
                }
                contact[2] = emailString.toString();
            }
            contact[3] = anr;
            contact[4] = aas;
            contact[5] = sne;
            contact[6] = grp;
            contact[7] = gas;
            contact[8] = sim_index;
            contact[9] = id;
            cursor.addRow(contact);
        }
    }

    private void log(String msg) {
        Log.d(TAG, "[MsmsIccProvider] " + msg);
    }

    private int getPhoneId(Uri uri) {
        int match = PHONE_ID_MATCHER.match(uri);
        if (match == UriMatcher.NO_MATCH) {
            throw new IllegalArgumentException("UnKnow URI : " + uri);
        } else if (match == INVALID_PHONE_ID) {
            List<String> paths = uri.getPathSegments();
            if (paths != null && paths.size() > 0) {
                String id = paths.get(0);
                Log.e(TAG, "Invalid phoneId : " + id);
                //no matter whether the phoneId is invalid
                return Integer.parseInt(id);
            }
            throw new IllegalArgumentException("Cannot parse URI : " + uri);
        }
        return match;
    }

    private static String getPathName(String path, int phoneId) {
        if (PhoneFactory.isMultiSim()) {
            if (phoneId == PhoneFactory.getPhoneCount()) {
                return path;
            }
            return phoneId + "/" + path;
        } else {
            return path;
        }
    }

    private int addUsimGroup(String groupName, int phoneId) {
        if (DBG)
            log("addUsimGroup: groupName=" + groupName + ", phoneId=" + phoneId);
        int groupId=-1;

        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub
                    .asInterface(ServiceManager.getService(PhoneFactory.getServiceName("simphonebook", phoneId)));
            if (iccIpb != null) {
                groupId = iccIpb.updateUsimGroupBySearchEx("", groupName);
            }

        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG)
                log(ex.toString());
        }
        if (DBG)
            log("addUsimGroup: " + groupId);
        return groupId;
    } 

    private boolean updateUsimGroupById(String newName,int groupId, int phoneId) {
        if (DBG)
            log("updateUsimGroupById: newName=" + newName + ", groupId="
                    + groupId + ", phoneId=" + phoneId);
        boolean success = false;

        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                    ServiceManager.getService(PhoneFactory.getServiceName("simphonebook", phoneId)));
            if (iccIpb != null) {
                success = iccIpb.updateUsimGroupById(newName, groupId);
            }
        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG) log(ex.toString());
        }
        if (DBG) 
            log("updateUsimGroupById: " + success);
        return success;
    }   
    

}
