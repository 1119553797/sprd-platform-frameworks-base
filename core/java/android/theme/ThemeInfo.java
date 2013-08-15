package android.theme;

import java.io.IOException;
import java.lang.ClassNotFoundException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import android.content.res.AssetManager;
import android.os.IBinder;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;

public class ThemeInfo implements Parcelable, Serializable {
    public String mPackageName;
    public String mTargetPackageName;

    public boolean mApplied;
    // for settings
    private int mPreviewResId;
    private int mNameResId;

    // for asset
    private String mResDir;
    private String mTargetResDir;

    /* package */ ThemeInfo(String packageName, String targetPackageName) {
	mPackageName=packageName;
	mTargetPackageName=targetPackageName;
    }

    public String getTargetResDir() {
	return mTargetResDir;
    }

    public void setTargetResDir(String targetResDir) {
	mTargetResDir=targetResDir;
    }
    
    public String getResDir() {
	return mResDir;
    }

    public void setResDir(String resDir) {
	mResDir=resDir;
    }
    
    public String getPackageName() {
	return mPackageName;
    }
    
    public void setThemePreview(int resId) {
	mPreviewResId=resId;
    }

    public void setThemeName(int resId) {
	mNameResId=resId;
    }
    
    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
	dest.writeString(mPackageName);
	dest.writeString(mTargetPackageName);
	dest.writeInt(mPreviewResId);
	dest.writeInt(mNameResId);
	dest.writeString(mResDir);
	dest.writeString(mTargetResDir);
    }

    public static final Creator<ThemeInfo> CREATOR =
        new Creator<ThemeInfo>() {
            public ThemeInfo createFromParcel(Parcel in) {
                ThemeInfo result = new ThemeInfo(in.readString(),in.readString());
		result.setThemePreview(in.readInt());
		result.setThemeName(in.readInt());
		result.setResDir(in.readString());
		result.setTargetResDir(in.readString());
                return result;
            }

            public ThemeInfo[] newArray(int size) {
                return new ThemeInfo[size];
            }
        };

    public boolean equals(Object info) {
	if (info instanceof ThemeInfo) {
	    return mPackageName.equals(((ThemeInfo)info).mPackageName);
	}
	return false;
    }

    public  int hashCode() {
        return mPackageName.hashCode();
    }

    public String toString() {
	StringBuilder sb=new StringBuilder();
	sb.append("pkgName       :"+mPackageName+"\n");
	sb.append("targetPkgName:"+mTargetPackageName+"\n");
	sb.append("resDir        :"+mResDir+"\n");
	sb.append("targetResDir :"+mTargetResDir+"\n");
	sb.append("applied        :"+mApplied+"\n");
	return sb.toString();
    }

    private static final long serialVersionUID = 0L;

    private void writeObject(ObjectOutputStream out)
	throws IOException {
	out.writeUTF(mPackageName);
	out.writeUTF(mTargetPackageName);
	out.writeBoolean(mApplied);
	out.writeInt(mPreviewResId);
	out.writeInt(mNameResId);
	out.writeUTF(mResDir);
	out.writeUTF(mTargetResDir);
    }

    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException {
	mPackageName=in.readUTF();
	mTargetPackageName=in.readUTF();
	mApplied=in.readBoolean();
	mPreviewResId=in.readInt();
	mNameResId=in.readInt();
	mResDir=in.readUTF();
	mTargetResDir=in.readUTF();
    }
}

