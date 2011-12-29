/*
 * Copyright (C) 2006-2007 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.internal.telephony.gsm.stk;

import com.android.internal.telephony.EncodeException;
import com.android.internal.telephony.GsmAlphabet;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;

abstract class ResponseData {
    /**
     * Format the data appropriate for TERMINAL RESPONSE and write it into
     * the ByteArrayOutputStream object.
     */
    public abstract void format(ByteArrayOutputStream buf);
}

class SelectItemResponseData extends ResponseData {
    // members
    private int id;

    public SelectItemResponseData(int id) {
        super();
        this.id = id;
    }

    @Override
    public void format(ByteArrayOutputStream buf) {
        // Item identifier object
        int tag = 0x80 | ComprehensionTlvTag.ITEM_ID.value();
        buf.write(tag); // tag
        buf.write(1); // length
        buf.write(id); // identifier of item chosen
    }
}

class GetInkeyInputResponseData extends ResponseData {
    // members
    private boolean mIsUcs2;
    private boolean mIsPacked;
    private boolean mIsYesNo;
    private boolean mYesNoResponse;
    public String mInData;

    // GetInKey Yes/No response characters constants.
    protected static final byte GET_INKEY_YES = 0x01;
    protected static final byte GET_INKEY_NO = 0x00;

    public GetInkeyInputResponseData(String inData, boolean ucs2, boolean packed) {
        super();
        this.mIsUcs2 = ucs2;
        this.mIsPacked = packed;
        this.mInData = inData;
        this.mIsYesNo = false;
    }

    public GetInkeyInputResponseData(boolean yesNoResponse) {
        super();
        this.mIsUcs2 = false;
        this.mIsPacked = false;
        this.mInData = "";
        this.mIsYesNo = true;
        this.mYesNoResponse = yesNoResponse;
    }

    @Override
    public void format(ByteArrayOutputStream buf) {
        if (buf == null) {
            return;
        }

        // Text string object
        int tag = 0x80 | ComprehensionTlvTag.TEXT_STRING.value();
        buf.write(tag); // tag

        byte[] data;

        if (mIsYesNo) {
            data = new byte[1];
            data[0] = mYesNoResponse ? GET_INKEY_YES : GET_INKEY_NO;
        } else if (mInData != null && mInData.length() > 0) {
            try {
                if (mIsUcs2) {
                    data = mInData.getBytes("UTF-16BE");
                } else if (mIsPacked) {
                    int size = mInData.length();

                    byte[] tempData = GsmAlphabet
                            .stringToGsm7BitPacked(mInData, 0, 0);
                    data = new byte[size];
                    // Since stringToGsm7BitPacked() set byte 0 in the
                    // returned byte array to the count of septets used...
                    // copy to a new array without byte 0.
                    System.arraycopy(tempData, 1, data, 0, size);
                } else {
                    data = GsmAlphabet.stringToGsm8BitPacked(mInData);
                }
            } catch (UnsupportedEncodingException e) {
                data = new byte[0];
            } catch (EncodeException e) {
                data = new byte[0];
            }
        } else {
            data = new byte[0];
        }

        // length - one more for data coding scheme.
        // 11.14 Annex D
        if (data.length < 0x80) {
            buf.write(data.length + 1);
        } else {
            buf.write(0x81);
            buf.write(data.length + 1);
        }

        // data coding scheme
        if (mIsUcs2) {
            buf.write(0x08); // UCS2
        } else if (mIsPacked) {
            buf.write(0x00); // 7 bit packed
        } else {
            buf.write(0x04); // 8 bit unpacked
        }

        for (byte b : data) {
            buf.write(b);
        }
    }
}

class LanguageResponseData extends ResponseData {
    //local info type
    private String mlangCode ;

    public LanguageResponseData(String langCode) {
        super();
        this.mlangCode = langCode;
    }

    @Override
    public void format(ByteArrayOutputStream buf) {
        // Item identifier object
        int tag = 0x80 | ComprehensionTlvTag.LANGUAGE.value();
        buf.write(tag); // tag

        StkLog.d(this, "mlangCode: " + mlangCode);
        buf.write(2); // length
        buf.write(mlangCode.charAt(0));
        buf.write(mlangCode.charAt(1));

    }
}

class DateTimeResponseData extends ResponseData {
    //local info type
    private byte[] mDateTime ;

    public DateTimeResponseData() {
        super();
        Calendar rightNow = Calendar.getInstance();

        byte[] data = new byte[7];

        data[0] = (byte) ((rightNow.get(Calendar.YEAR) - 1900) % 100);
        data[1] = (byte) (rightNow.get(Calendar.MONTH) + 1);
        data[2] = (byte) (rightNow.get(Calendar.DATE));
        data[3] = (byte) (rightNow.get(Calendar.HOUR_OF_DAY));
        data[4] = (byte) (rightNow.get(Calendar.MINUTE));
        data[5] = (byte) (rightNow.get(Calendar.SECOND));

        int tmp = rightNow.getTimeZone().getOffset(rightNow.getTimeInMillis());
        data[6] = (byte)(tmp / 1000 / 900); // quarters

        for (int i = 0; i < 6; i++) {
            data[i] = (byte) (((data[i] / 10) & 0xF) | ((data[i] % 10) << 4));
        }

        tmp = Math.abs(data[6]);
        tmp = ((tmp / 10) & 0xF) | ((tmp % 10) << 4);
        if (data[6] < 0) {
            tmp |= 0x8;
        }
        data[6] = (byte)tmp;

        mDateTime = data;
        //StkLog.d(this, "mDateTime: " + IccUtils.bytesToHexString(mDateTime));
    }

    @Override
    public void format(ByteArrayOutputStream buf) {
        // Item identifier object
        int tag = 0x80 | ComprehensionTlvTag.DATE_TIME_TIMEZONE.value();
        buf.write(tag); // tag
        buf.write(7); // length

        for (byte b : mDateTime) {
            buf.write(b);
        }

        // byte[] rawData = buf.toByteArray();
        // String hexString = IccUtils.bytesToHexString(rawData);
        // StkLog.d(this, "format: " + hexString);
    }
}



