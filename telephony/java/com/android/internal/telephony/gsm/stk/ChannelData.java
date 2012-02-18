package com.android.internal.telephony.gsm.stk;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

//Base ChannelData
public class ChannelData {

    public int openChannelType = 0;
    public String text = null;
    public Bitmap icon = null;
    public boolean iconSelfExplanatory = false;
    public boolean isNullAlphaId = false;

    public ChannelData() {
    }
    
    public int getChannelType() {
        return openChannelType;
    }

    public void setChannelType(int type) {
        openChannelType = type;
    }

    public ChannelData(Parcel in) {
        openChannelType = in.readInt();
        text = in.readString();
        icon = in.readParcelable(null);
        iconSelfExplanatory = in.readInt() == 1 ? true : false;
        isNullAlphaId = in.readInt() == 1 ? true : false;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(openChannelType);
        dest.writeString(text);
        dest.writeParcelable(icon, 0);
        dest.writeInt(iconSelfExplanatory ? 1 : 0);
        dest.writeInt(isNullAlphaId ? 1 : 0);
    }

    public boolean setIcon(Bitmap Icon) { return true; }
}


class CloseChannelData extends ChannelData implements Parcelable {

    public CloseChannelData() {
        super();
    }

    public CloseChannelData(Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<ChannelData> CREATOR = new Parcelable.Creator<ChannelData>() {
       public ChannelData createFromParcel(Parcel in) {
           return new CloseChannelData(in);
       }

       public CloseChannelData[] newArray(int size) {
           return new CloseChannelData[size];
       }
    };

    @Override
    public int describeContents() {
        return 0;
    }
}


class GetChannelStatus extends ChannelData implements Parcelable {

    public GetChannelStatus() {
        super();
    }

    public GetChannelStatus(Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<GetChannelStatus> CREATOR = new Parcelable.Creator<GetChannelStatus>() {
       public GetChannelStatus createFromParcel(Parcel in) {
           return new GetChannelStatus(in);
       }

       public GetChannelStatus[] newArray(int size) {
           return new GetChannelStatus[size];
       }
    };

    @Override
    public int describeContents() {
        return 0;
    }
}
