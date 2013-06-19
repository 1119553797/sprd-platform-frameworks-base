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

import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.text.TextUtils;

import com.android.internal.telephony.GsmAlphabet;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import com.android.internal.telephony.IccUtils;

/**
 *
 * Used to load or store ADNs (Abbreviated Dialing Numbers).
 *
 * {@hide}
 *
 */
public class AdnRecord implements Parcelable {
    static final String LOG_TAG = "AdnRecord";

    //***** Instance Variables

    String alphaTag = null;
    String number = null;
    String[] emails = null;
    int extRecord = 0xff;
    int efid;                   // or 0 if none
    int recordNumber;           // or 0 if none

    // add multi record and email in usim
    String anr = null;
    String aas = null;
    String sne = null;
    String grp = null;
    String gas = null;
    int index = -1;

    //***** Constants

    // In an ADN record, everything but the alpha identifier
    // is in a footer that's 14 bytes
    static final int FOOTER_SIZE_BYTES = 14;

    // Maximum size of the un-extended number field
    static final int MAX_NUMBER_SIZE_BYTES = 11;

    static final int EXT_RECORD_LENGTH_BYTES = 13;
    static final int EXT_RECORD_TYPE_ADDITIONAL_DATA = 2;
    static final int EXT_RECORD_TYPE_MASK = 3;
    static final int MAX_EXT_CALLED_PARTY_LENGTH = 0xa;

    // ADN offset
    static final int ADN_BCD_NUMBER_LENGTH = 0;
    static final int ADN_TON_AND_NPI = 1;
    static final int ADN_DIALING_NUMBER_START = 2;
    static final int ADN_DIALING_NUMBER_END = 11;
    static final int ADN_CAPABILITY_ID = 12;
    static final int ADN_EXTENSION_ID = 13;

    // add multi record and email in usim
    static final int ADN_SFI = 0;
    static final int ADN_REC_ID = 1;

    // Maximum size of the un-extended number field
    static final int TYPE1_DATA_LENGTH = 15;
    static final int NONE_TYPE1_DATA_LENGTH = 17;

    public static final String ANR_SPLIT_FLG = ";";

    public static final int MAX_LENTH_ADN = (ADN_DIALING_NUMBER_END
            - ADN_DIALING_NUMBER_START + 1) * 2;
    public static final int MAX_LENTH_NUMBER = (ADN_DIALING_NUMBER_END
            - ADN_DIALING_NUMBER_START + 1) * 2
            + MAX_EXT_CALLED_PARTY_LENGTH * 2;

    //***** Static Methods

    public static final Parcelable.Creator<AdnRecord> CREATOR
            = new Parcelable.Creator<AdnRecord>() {
        public AdnRecord createFromParcel(Parcel source) {
            int efid;
            int recordNumber;
            String alphaTag;
            String number;
            String[] emails;
            // add multi record and email in usim
            String anr;
            String aas;
            String sne;
            String grp;
            String gas;

            efid = source.readInt();
            recordNumber = source.readInt();
            alphaTag = source.readString();
            number = source.readString();
            emails = source.readStringArray();
            // add multi record and email in usim
            anr = source.readString();
            aas = source.readString();
            sne = source.readString();
            grp = source.readString();
            gas = source.readString();
            return new AdnRecord(efid, recordNumber, alphaTag, number, emails,
                    anr, aas, sne, grp, gas);
        }

        public AdnRecord[] newArray(int size) {
            return new AdnRecord[size];
        }
    };


    //***** Constructor
    public AdnRecord (byte[] record) {
        this(0, 0, record);
    }

    public AdnRecord (int efid, int recordNumber, byte[] record) {
        this.efid = efid;
        this.recordNumber = recordNumber;
        parseRecord(record);
    }

    public AdnRecord (String alphaTag, String number) {
        this(0, 0, alphaTag, number);
    }

    public AdnRecord (String alphaTag, String number, String[] emails) {
        this(0, 0, alphaTag, number, emails);
    }

    public AdnRecord (int efid, int recordNumber, String alphaTag, String number, String[] emails) {
        this.efid = efid;
        this.recordNumber = recordNumber;
        this.alphaTag = alphaTag;
        this.number = number;
        this.emails = emails;
    }

    public AdnRecord(int efid, int recordNumber, String alphaTag, String number) {
        this.efid = efid;
        this.recordNumber = recordNumber;
        this.alphaTag = alphaTag;
        this.number = number;
        this.emails = null;
    }

    // add multi record and email in usim
    public AdnRecord(String alphaTag, String number, String[] emails,
            String anr, String aas, String sne, String grp, String gas) {
        this(0, 0, alphaTag, number, emails, anr, aas, sne, grp, gas);
    }

    public AdnRecord(int efid, int recordNumber, String alphaTag,
            String number, String[] emails, String anr, String aas, String sne,
            String grp, String gas) {
        this.efid = efid;
        this.recordNumber = recordNumber;
        this.alphaTag = alphaTag;
        this.number = number;
        this.emails = emails;
        this.anr = anr;
        this.aas = aas;
        this.sne = sne;
        this.grp = grp;
        this.gas = gas;
    }

    //***** Instance Methods

    public String getAlphaTag() {
        return alphaTag;
    }

    public String getNumber() {
        return number;
    }

    public String[] getEmails() {
        return emails;
    }

    public void setEmails(String[] emails) {
        this.emails = emails;
    }

    public String toString() {
        return "ADN Record '" + alphaTag + "' '" + number + " " + emails + "'";
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(alphaTag) && TextUtils.isEmpty(number) && emails == null
                && (isEmptyAnr(anr));  //Jun, no care about GRP info
    }

    public void setRecordNumber(int sim_index) {
        recordNumber = sim_index;
    }

    public int getRecordNumber() {
        return recordNumber;
    }

	// end for sim index jinwei

	// add multi record and email in usim begin
	public String getAnr() {
		return anr;
	}

	public void setAnr(String anr) {
		this.anr = anr;
	}

	public String getAas() {
		return aas;
	}

	public void setAas(String aas) {
		this.aas = aas;
	}

	public String getSne() {
		return sne;
	}

	public void setSne(String sne) {
		this.sne = sne;
	}

	public String getGrp() {
		return grp;
	}

	public void setGrp(String grp) {
		this.grp = grp;
	}

	public String getGas() {
		return gas;
	}

	public void setGas(String gas) {
		this.gas = gas;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	// add multi record and email in usim end
	public boolean isEmptyAnr(String s) {
		if (TextUtils.isEmpty(s)) {
			return true;
		}
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) != ';') {
				return false;
			}
		}
		return true;

	}

	public boolean stringCompareAnr(String s1, String s2) {
		String[] pair;
		if (TextUtils.isEmpty(s1) || isEmptyAnr(s1)) {
			s1 = "";
		}
		if (TextUtils.isEmpty(s2) || isEmptyAnr(s2)) {
			s2 = "";
		}
		return stringCompareNullEqualsEmpty(s1, s2);

	}

    public boolean hasExtendedRecord() {
        return extRecord != 0 && extRecord != 0xff;
    }

    // add multi record and email in usim begin
    /** Helper function for {@link #isEqual}. */
    private static boolean stringCompareNullEqualsEmpty(String s1, String s2) {
        if (s1 == s2) {
            return true;
        }
        if (s1 == null) {
            s1 = "";
        }
        if (s2 == null) {
            s2 = "";
        }

        return (s1.trim().equals(s2.trim()));
    }

    public boolean isEqual(AdnRecord adn) {
        return ( stringCompareNullEqualsEmpty(alphaTag, adn.alphaTag) &&
                stringCompareNullEqualsEmpty(number, adn.number) &&
                stringCompareEmails(emails, adn.emails) &&
                stringCompareAnr(anr, adn.anr) /*&&
                stringCompareAnr(grp, adn.grp)*/);      //Jun, no care about GRP info
    }

	public boolean stringCompareEmails(String[] e1, String[] e2) {
		String e = "";
		if (e1 == null) {
			e1 = new String[1];
			e1[0] = e;
		}
		if (e2 == null) {
			e2 = new String[1];
			e2[0] = e;
		}
		// return Arrays.equals(e1, e2);
		//Log.i("AdnRecord", "isEqual  adn  e1[0]:" + e1[0] + "e2[0]:" + e2[0]);
		return stringCompareNullEqualsEmpty(e1[0], e2[0]);
	}
    // add multi record and email in usim end

    //***** Parcelable Implementation

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(efid);
        dest.writeInt(recordNumber);
        dest.writeString(alphaTag);
        dest.writeString(number);
        dest.writeStringArray(emails);
        // add multi record and email in usim
        dest.writeString(anr);
        dest.writeString(aas);
        dest.writeString(sne);
        dest.writeString(grp);
        dest.writeString(gas);
    }

    /**
     * Build adn hex byte array based on record size
     * The format of byte array is defined in 51.011 10.5.1
     *
     * @param recordSize is the size X of EF record
     * @return hex byte[recordSize] to be written to EF record
     *          return null for wrong format of dialing number or tag
     */

    public byte[] buildAdnString(int recordSize) {
        byte[] bcdNumber = null;
        byte[] byteTag = null;
        byte[] adnString = null;
        int footerOffset = recordSize - FOOTER_SIZE_BYTES;
        // create an empty record
        adnString = new byte[recordSize];
        for (int i = 0; i < recordSize; i++) {
            adnString[i] = (byte) 0xFF;
        }
        String numberNoPlus = number;
        if (!TextUtils.isEmpty(number)) {
            if (number.charAt(0) == '+') {
                numberNoPlus = numberNoPlus.substring(1);
            }
        }
        Log.i("AdnRecord", "buildAdnString number:" + number + ", alphaTag:"
                + alphaTag+"numberNoplus = "+ numberNoPlus);
        if (TextUtils.isEmpty(number) && TextUtils.isEmpty(alphaTag)) {
            Log.w(LOG_TAG, "[buildAdnString] Empty dialing number");
            return adnString;   // return the empty record (for delete)
        } else if (!TextUtils.isEmpty(numberNoPlus)
                && numberNoPlus.length() > MAX_LENTH_NUMBER){
            Log.w(LOG_TAG,
                    "[buildAdnString] Max length of dialing number is "+MAX_LENTH_NUMBER);
            throw new IccPhoneBookOperationException(IccPhoneBookOperationException.OVER_NUMBER_MAX_LENGTH,
                     "Max length of dialing number is "+MAX_LENTH_NUMBER);
        } else if (alphaTag != null && alphaTag.length() > footerOffset) {
            Log.w(LOG_TAG,
                    "[buildAdnString] Max length of tag is " + footerOffset);
            throw new IccPhoneBookOperationException(IccPhoneBookOperationException.OVER_NAME_MAX_LENGTH,
                    "Max length of name is "+footerOffset);
        } else {
            if (!TextUtils.isEmpty(numberNoPlus) && numberNoPlus.length() <= MAX_LENTH_ADN) {
                Log.d(LOG_TAG, "number.length < " + MAX_LENTH_ADN);
                bcdNumber = PhoneNumberUtils.numberToCalledPartyBCD(number);
                System.arraycopy(bcdNumber, 0, adnString,
                        footerOffset + ADN_TON_AND_NPI, bcdNumber.length);

                adnString[footerOffset + ADN_BCD_NUMBER_LENGTH] = (byte) (bcdNumber.length);
                adnString[footerOffset + ADN_CAPABILITY_ID] = (byte) 0xFF; // Capacility
                // Id
                adnString[footerOffset + ADN_EXTENSION_ID] = (byte) 0xFF; // Extension
                // Record
                // Id
            } else if (!TextUtils.isEmpty(numberNoPlus)
                    && numberNoPlus.length() <= MAX_LENTH_NUMBER) {
                String adnNumber;
                if (number.charAt(0) == '+') {
                    adnNumber = number.substring(0, MAX_LENTH_ADN);
                } else {
                    adnNumber = number.substring(0, MAX_LENTH_ADN - 1);
                }
                if (!TextUtils.isEmpty(adnNumber)) {
                    byte[] adnBcdNumber = PhoneNumberUtils.numberToCalledPartyBCD(adnNumber);

                    System.arraycopy(adnBcdNumber, 0, adnString,
                            footerOffset + ADN_TON_AND_NPI, adnBcdNumber.length);
                    adnString[footerOffset + ADN_BCD_NUMBER_LENGTH] = (byte) (adnBcdNumber.length);
                }
                adnString[footerOffset + ADN_CAPABILITY_ID] = (byte) 0xFF; // Capacility
                adnString[footerOffset + ADN_EXTENSION_ID] = (byte) 0; // Extension
			}

			// alphaTag format
			if (!TextUtils.isEmpty(alphaTag)) {
				try {
					byteTag = GsmAlphabet
							.stringToGsmAlphaSS(alphaTag);
					System.arraycopy(byteTag, 0, adnString, 0, byteTag.length);
				} catch (EncodeException ex) {
					try {
						byteTag = alphaTag.getBytes("utf-16be");
						System.arraycopy(byteTag, 0, adnString,
								ADN_TON_AND_NPI, byteTag.length);
						adnString[0] = (byte) 0x80;
					} catch (java.io.UnsupportedEncodingException ex2) {
						Log.e(LOG_TAG,
								"[AdnRecord]alphaTag convert byte excepiton");
					}
				}
				Log.w(LOG_TAG, "alphaTag length="
						+ (byteTag != null ? byteTag.length : "null")
						+ " footoffset =" + footerOffset);
				if (byteTag != null && byteTag.length > footerOffset) {
					Log.w(LOG_TAG, "[buildAdnString] Max length of tag is "
							+ footerOffset);
					throw new IccPhoneBookOperationException(IccPhoneBookOperationException.OVER_NAME_MAX_LENGTH,
		                     "Max length of name is "+footerOffset);
				}
			}

			return adnString;

		}

	}

    public byte[] buildExtString() {
        String extNumber;
        if (number.charAt(0) == '+') {
            extNumber = number.substring(MAX_LENTH_ADN + 1);
        } else {
            extNumber = number.substring(MAX_LENTH_ADN);
        }
        byte[] extBcdNumber = PhoneNumberUtils.numberToCalledPartyBCD(extNumber);
        byte[] extString = new byte[EXT_RECORD_LENGTH_BYTES];
        for (int i = 0; i < EXT_RECORD_LENGTH_BYTES; i++) {
            extString[i] = (byte) 0xFF;
        }
        extString[0] = (byte) EXT_RECORD_TYPE_ADDITIONAL_DATA;// ext record type
        extString[1] = (byte) extBcdNumber.length;
        if (extBcdNumber.length <= MAX_EXT_CALLED_PARTY_LENGTH) {
            System.arraycopy(extBcdNumber, 0, extString, 2, extBcdNumber.length);
        }
        extString[12] = (byte) 0xFF; // only support one ext record
        return extString;
    }

    /**
     * See TS 51.011 10.5.10
     */
    public void
    appendExtRecord (byte[] extRecord) {
        try {
            if (extRecord.length != EXT_RECORD_LENGTH_BYTES) {
                return;
            }

            if ((extRecord[0] & EXT_RECORD_TYPE_MASK)
                    != EXT_RECORD_TYPE_ADDITIONAL_DATA) {
                return;
            }

            if ((0xff & extRecord[1]) > MAX_EXT_CALLED_PARTY_LENGTH) {
                // invalid or empty record
                return;
            }

            number += PhoneNumberUtils.calledPartyBCDFragmentToString(
                                        extRecord, 2, 0xff & extRecord[1]);

            // We don't support ext record chaining.

        } catch (RuntimeException ex) {
            Log.w(LOG_TAG, "Error parsing AdnRecord ext record", ex);
        }
    }

	// add multi record and email in usim begin
	public byte[] buildEmailString(int recordSize, int recordSeq, int efid,
			int adnNum) {
		byte[] byteTag;
		byte[] emailString;
		String emailRecord;
		int footerOffset = recordSize - 2;

		// create an empty record
		emailString = new byte[recordSize];
		for (int i = 0; i < recordSize; i++) {
			emailString[i] = (byte) 0xFF;

		}
		emailString[footerOffset + ADN_SFI] = (byte) efid; // Adn Sfi
		emailString[footerOffset + ADN_REC_ID] = (byte) adnNum; // Adn Record Id

		if (emails == null) {
			return emailString;
		} else {
			emailRecord = emails[recordSeq];
		}
		if (!TextUtils.isEmpty(emailRecord)) {
			try {
				byteTag = GsmAlphabet
						.isAsciiStringToGsm8BitUnpackedField(emailRecord);
				System.arraycopy(byteTag, 0, emailString, 0, byteTag.length);
			} catch (EncodeException ex) {
				try {
					byteTag = emailRecord.getBytes("utf-16be");
					System.arraycopy(byteTag, 0, emailString, ADN_TON_AND_NPI,
							byteTag.length);
					emailString[0] = (byte) 0x80;
				} catch (java.io.UnsupportedEncodingException ex2) {
					Log.e(LOG_TAG,
							"[AdnRecord]emailRecord convert byte exception");
					
				}
			}
		}
		// Log.e(LOG_TAG,"emailRecord for adn["+adnNum+"]=="+emailRecord);
		return emailString;
	}

	public byte[] buildAnrString(int recordSize, int anrCount, int efid,
			int adnNum) {
		byte[] anrString;
		String anrRecord = null;
		byte[] anrNumber;
		Log.e(LOG_TAG, "enter buildAnrString");
		// create an empty record
		anrString = new byte[recordSize];
		for (int i = 0; i < recordSize; i++) {
			anrString[i] = (byte) 0xFF;
		}
		if (TextUtils.isEmpty(anr) || anr.equals(";") || anr.equals(";;")) {
			Log.e(LOG_TAG, "[buildAnrString] anr number is empty. ");
			return anrString;
		} else {
			Log.e(LOG_TAG, "anr = " + anr);
			String[] ret = null;
			if (!TextUtils.isEmpty(anr)) {
				ret = (anr + "1").split(";");
				ret[ret.length - 1] = ret[ret.length - 1].substring(0,
						ret[ret.length - 1].length() - 1);
			}
			anrRecord = ret[anrCount];
			Log.e(LOG_TAG, "anrRecord = " + anrRecord);
			if (TextUtils.isEmpty(anrRecord)) {
				Log.e(LOG_TAG, "[buildAnrString] anrRecord is empty. ");
			} else if (anrRecord.length() > 20) {
				Log.e(LOG_TAG,
						"[buildAnrString] Max length of dailing number is 20,throw exception");
                throw new IccPhoneBookOperationException(
                        IccPhoneBookOperationException.OVER_NUMBER_MAX_LENGTH,
                        "Max length of dialing number is " + MAX_LENTH_ADN);
			} else {
				anrString[0] = (byte) 0x01;
				anrNumber = PhoneNumberUtils.numberToCalledPartyBCD(anrRecord);
				if (anrNumber == null) {
				    return anrString;
                }
				anrString[ADN_BCD_NUMBER_LENGTH + 1] = (byte) (anrNumber.length);
				System.arraycopy(anrNumber, 0, anrString, 2, anrNumber.length);
				if (recordSize > TYPE1_DATA_LENGTH) {
					anrString[recordSize - 4] = (byte) 0xFF; // Capacility Id
					anrString[recordSize - 3] = (byte) 0xFF; // Extension Record
					// Id
					anrString[recordSize - 2] = (byte) efid; // Adn Sfi
					anrString[recordSize - 1] = (byte) adnNum; // Adn Record Id
				} else {
					anrString[recordSize - 2] = (byte) 0xFF; // Capacility Id
					anrString[recordSize - 1] = (byte) 0xFF; // Extension Record
					// Id
				}
			}
			return anrString;
		}
	}

	// a end
	public byte[] buildIapString(int recordSize, int recNum) {
		// byte[] byteTag;
		byte[] iapString = null;
		// String iapRecord;
		int footerOffset = recordSize - FOOTER_SIZE_BYTES;

		// create an empty record
		iapString = new byte[recordSize];
		for (int i = 0; i < recordSize; i++) {
			iapString[i] = (byte) 0xFF;
		}
		iapString[0] = (byte) recNum;

		return iapString;
	}

	// add multi record and email in usim end


    //***** Private Methods

    /**
     * alphaTag and number are set to null on invalid format
     */
    private void
    parseRecord(byte[] record) {
        try {
            int footerOffset = record.length - FOOTER_SIZE_BYTES;
            alphaTag = IccUtils.adnStringFieldToString(
                            record, 0, footerOffset);

            int numberLength = 0xff & record[footerOffset];

            if (numberLength > MAX_NUMBER_SIZE_BYTES) {
                // Invalid number length
                Log.i("AdnRecord", "parseRecord number is empty length"
                        + numberLength);
                number = null;
                return;
            }

            // Please note 51.011 10.5.1:
            //
            // "If the Dialling Number/SSC String does not contain
            // a dialling number, e.g. a control string deactivating
            // a service, the TON/NPI byte shall be set to 'FF' by
            // the ME (see note 2)."

            number = PhoneNumberUtils.calledPartyBCDToString(
                            record, footerOffset + 1, numberLength);


            extRecord = 0xff & record[record.length - 1];

            emails = null;

        } catch (RuntimeException ex) {
            Log.w(LOG_TAG, "Error parsing AdnRecord", ex);
            number = "";
            alphaTag = "";
            emails = null;
        }
    }
}
