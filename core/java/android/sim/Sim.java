/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.sim;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Value type that represents a Sim in the {@link SimManager}. This object is
 * {@link Parcelable} and also overrides {@link #equals} and {@link #hashCode},
 * making it suitable for use as the key of a {@link java.util.Map}
 */
public class Sim implements Parcelable {

    /*
     * provider the SIM name and color to the APP
     */

    private int mPhoneId = -1;// You can get mName or mColor directly through
                              // the mPhoneId

    private String mIccId;

    private String mName;

    private int mColorIndex; //the index of the sim color in SimManager.COLORS or COLORS_IMAGES

    private int mSerialNum;
    
    public Sim(int phoneId, String iccId, String name, int colorIndex) {
        this.mPhoneId = phoneId;
        this.mIccId = iccId;
        this.mName = name;
        this.mColorIndex = colorIndex;
    }

    public Sim(Parcel in) {

        this.mPhoneId = in.readInt();
        this.mIccId = in.readString();
        this.mName = in.readString();
        this.mColorIndex = in.readInt();
        this.mSerialNum = in.readInt();

    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPhoneId);
        dest.writeString(mIccId);
        dest.writeString(mName);
        dest.writeInt(mColorIndex);
        dest.writeInt(mSerialNum);
    }

    public static final Creator<Sim> CREATOR = new Creator<Sim>() {
        public Sim createFromParcel(Parcel source) {
            return new Sim(source);
        }

        public Sim[] newArray(int size) {
            return new Sim[size];
        }
    };

    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof Sim))
            return false;
        final Sim other = (Sim) o;
        return this.mIccId.equals(other.mIccId);
    }

    public int hashCode() {
        int result = 17;
        result = 31 * result + mIccId.hashCode();
        return result;
    }

    public String toString() {
        return "SIM {name=" + mName + ", colorIndex=" + mColorIndex + ", serialNum=" + mSerialNum
                + ", iccId=" + mIccId + ", phoneId=" + mPhoneId + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public String getName() {
        return mName;
    }

    public int getColor() {
        return SimManager.COLORS[mColorIndex];
    }

    public int getColorIndex() {
        return mColorIndex;
    }

    public int getSerialNum() {
        return mSerialNum;
    }

    public String getIccId() {
        return mIccId;
    }

    public int getPhoneId() {
        return mPhoneId;
    }
    
    public void setName(String name) {
        this.mName = name;
    }

    public void setColorIndex(int colorIndex) {
        this.mColorIndex = colorIndex;
    }

    public void setPhoneId(int phoneId) {
        this.mPhoneId = phoneId;
    }

    public void setSerialNum(int serialNum) {
        this.mSerialNum = serialNum;
    }

}
