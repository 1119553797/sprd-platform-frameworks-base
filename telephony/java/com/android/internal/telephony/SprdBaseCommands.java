package com.android.internal.telephony;

import android.content.Context;
import android.os.Handler;
import android.os.Registrant;
import android.os.RegistrantList;

/*
 * @hide
 */
public abstract class SprdBaseCommands extends BaseCommands {

    protected Registrant mVPDataRegistrant;
    protected RegistrantList mVPCodecRegistrants = new RegistrantList();
    protected Registrant mVPStrsRegistrant;
    protected Registrant mVPRemoteMediaRegistrant;
    protected Registrant mVPMMRingRegistrant;
    protected Registrant mVPRecordVideoRegistrant;
    protected Registrant mVPFallBackRegistrant;
    protected Registrant mVPFailRegistrant;
    protected Registrant mVPRemoteCameraRegistrant;
    protected Registrant mVPMediaStartRegistrant;
    protected RegistrantList mVideoCallStateRegistrants = new RegistrantList();
    protected Registrant mStkStinRegistrant;

    public SprdBaseCommands(Context context) {
        super(context);
    }

    public void setOnVPData(Handler h, int what, Object obj) {
        mVPDataRegistrant = new Registrant (h, what, obj);
    }

    public void unSetOnVPData(Handler h) {
        mVPDataRegistrant.clear();
    }

    public void setOnVPCodec(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mVPCodecRegistrants.add(r);
    }

    public void unSetOnVPCodec(Handler h) {
        mVPCodecRegistrants.remove(h);
    }

    public void setOnVPString(Handler h, int what, Object obj) {
        mVPStrsRegistrant = new Registrant (h, what, obj);
    }

    public void unSetOnVPString(Handler h) {
        mVPStrsRegistrant.clear();
    }

    public void setOnVPRemoteMedia(Handler h, int what, Object obj) {
        mVPRemoteMediaRegistrant = new Registrant (h, what, obj);
    }

    public void unSetOnVPRemoteMedia(Handler h) {
        mVPRemoteMediaRegistrant.clear();
    }

    public void setOnVPMMRing(Handler h, int what, Object obj) {
        mVPMMRingRegistrant = new Registrant (h, what, obj);
    }

    public void unSetOnVPMMRing(Handler h) {
        mVPMMRingRegistrant.clear();
    }

    public void setOnVPRecordVideo(Handler h, int what, Object obj) {
        mVPRecordVideoRegistrant = new Registrant (h, what, obj);
    }

    public void unSetOnVPRecordVideo(Handler h) {
        mVPRecordVideoRegistrant.clear();
    }

    public void setOnVPFallBack(Handler h, int what, Object obj) {
        mVPFallBackRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnVPFallBack(Handler h) {
        mVPFallBackRegistrant.clear();
    }

    public void setOnVPFail(Handler h, int what, Object obj) {
        mVPFailRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnVPFail(Handler h) {
        mVPFailRegistrant.clear();
    }

    public void setOnVPRemoteCamera(Handler h, int what, Object obj) {
        mVPRemoteCameraRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnVPRemoteCamera(Handler h) {
        mVPRemoteCameraRegistrant.clear();
    }

    public void setOnVPMediaStart(Handler h, int what, Object obj) {
        mVPMediaStartRegistrant = new Registrant (h, what, obj);
    }

    public void unSetOnVPMediaStart(Handler h) {
        mVPMediaStartRegistrant.clear();
    }

    public void registerForVideoCallStateChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mVideoCallStateRegistrants.add(r);
    }

    public void unregisterForVideoCallStateChanged(Handler h) {
        mVideoCallStateRegistrants.remove(h);
    }

    public void setOnStkStin(Handler h, int what, Object obj) {
        mStkStinRegistrant = new Registrant (h, what, obj);
    }

    public void unsetOnStkStin(Handler h) {
        mStkStinRegistrant.clear();
    }
}
