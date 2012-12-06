
package android.hardware.fm;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.os.IBinder;
import android.os.Binder;
import java.util.ArrayList;
import java.util.Iterator;
import android.os.RemoteException;
import android.hardware.fm.FmConsts.*;
import android.util.Log;

/**
 * @hide
 */
public class FmService extends IFmService.Stub {

    private final String TAG = "FmService";

    private final int FM_AUDIO_PATH_SPEAKER = 0;

    private final int FM_AUDIO_PATH_HEADSET = 1;

    private final int FM_AUDIO_PATH_NONE = 2;

    private Context mContext = null;

    private FmController mController = null;

    private int mFmAudioPath = FM_AUDIO_PATH_NONE;

    private Object mFmAudioPathLock = new Object();

    private class ObserverHandler implements IBinder.DeathRecipient {
        private IBinder mObserver;

        private int mPid;

        ObserverHandler(IBinder observer, int pid) {
            mObserver = observer;
            mPid = pid;
        }

        public void binderDied() {
            int index = mObserverHandlers.indexOf(this);
            if (index >= 0) {
                powerDown();
            }
        }

        public int getPid() {
            return mPid;
        }

        public IBinder getObserver() {
            return mObserver;
        }
    }

    private ArrayList <ObserverHandler> mObserverHandlers = new ArrayList <ObserverHandler>();

    public FmService(Context context) {
        mContext = context;
        mController = new FmController();
    }

    private ObserverHandler findObserver(int pid) {
        ObserverHandler value = null;

        Iterator iter = mObserverHandlers.iterator();
        while (iter.hasNext()) {
            ObserverHandler oh = (ObserverHandler)iter.next();
            if (oh.getPid() == pid) {
                value = oh;
                break;
            }
        }

        return value;
    }

    public boolean powerUp(IBinder observer) {
        if (mController == null) {
            Log.e(TAG, "powerUp() mController is null");
            return false;
        }

        if (observer == null) {
            Log.e(TAG, "powerUp() observer is null");
            return false;
        }

        boolean value = mController.powerUp();

        if (value) {
            synchronized(mObserverHandlers) {
                ObserverHandler oh = findObserver(Binder.getCallingPid());
                if (oh == null) {
                    oh = new ObserverHandler(observer, Binder.getCallingPid());
                    try {
                        observer.linkToDeath(oh, 0);
                        mObserverHandlers.add(oh);
                    } catch (RemoteException e) {
                        Log.w(TAG, "powerUp() could not link to " + observer + " binder death");
                        mController.powerDown();
                        value = false;
                    }
                }
            }
        }

        return value;
    }

    public boolean powerDown() {
        if (mController == null) {
            Log.e(TAG, "powerDown() mController is null");
            return false;
        }

        synchronized(mObserverHandlers) {
            ObserverHandler oh = findObserver(Binder.getCallingPid());
            if (oh != null) {
                oh.getObserver().unlinkToDeath(oh, 0);
                mObserverHandlers.remove(oh);
            }
        }

        return mController.powerDown();
    }

    public boolean startSearch(int freq, int direction, int timeout) {
        if (mController == null) {
            Log.e(TAG, "startSearch() mController is null");
            return false;
        }

        return mController.startSearch(freq, direction, timeout);
    }

    public boolean cancelSearch() {
        if (mController == null) {
            Log.e(TAG, "cancelSearch() mController is null");
            return false;
        }

        return mController.cancelSearch();
    }

    public boolean setFreq(int freq) {
        if (mController == null) {
            Log.e(TAG, "setFreq() mController is null");
            return false;
        }

        return mController.setFreq(freq);
    }

    public int getFreq() {
       if (mController == null) {
            Log.e(TAG, "getFreq() mController is null");
            return FmConsts.FM_STATE_UNINITIALIZED;
        }

        return mController.getFreq();
    }

    public boolean setAudioMode(int mode) {
        if (mController == null) {
            Log.e(TAG, "setAudioMode() mController is null");
            return false;
        }

        return mController.setAudioMode(mode);
    }

    public int getAudioMode() {
        if (mController == null) {
            Log.e(TAG, "getAudioMode() mController is null");
            return FmConsts.FM_STATE_UNINITIALIZED;
        }

        return mController.getAudioMode();
    }

    public boolean setStepType(int type) {
        if (mController == null) {
            Log.e(TAG, "setStepType() mController is null");
            return false;
        }

        return mController.setStepType(type);
    }

    public int getStepType() {
        if (mController == null) {
            Log.e(TAG, "getStepType() mController is null");
            return FmConsts.FM_STATE_UNINITIALIZED;
        }

        return mController.getStepType();
    }

    public boolean setBand(int band) {
        if (mController == null) {
            Log.e(TAG, "setBand() mController is null");
            return false;
        }

        return mController.setBand(band);
    }

    public int getBand() {
        if (mController == null) {
            Log.e(TAG, "getBand() mController is null");
            return FmConsts.FM_STATE_UNINITIALIZED;
        }

        return mController.getBand();
    }

    public boolean mute() {
        if (mController == null) {
            Log.e(TAG, "mute() mController is null");
            return false;
        }

        return mController.setMuteMode(1);
    }

    public boolean unmute() {
        if (mController == null) {
            Log.e(TAG, "mute() mController is null");
            return false;
        }

        return mController.setMuteMode(0);
    }

    public boolean isMuted() {
        if (mController == null) {
            Log.e(TAG, "isMuted() mController is null");
            return false;
        }

        return (mController.getMuteMode() == 1);
    }

    public boolean setVolume(int volume) {
        if (mController == null) {
            Log.e(TAG, "setVolume() mController is null");
            return false;
        }

        return mController.setVolume(volume);
    }

    public int getVolume() {
        if (mController == null) {
            Log.e(TAG, "getVolume() mController is null");
            return FmConsts.FM_STATE_UNINITIALIZED;
        }

        return mController.getVolume();
    }

    public boolean setRssi(int rssi) {
        if (mController == null) {
            Log.e(TAG, "setRssi() mController is null");
            return false;
        }

        return mController.setRssi(rssi);
    }

    public int getRssi() {
        if (mController == null) {
            Log.e(TAG, "getRssi() mController is null");
            return FmConsts.FM_STATE_UNINITIALIZED;
        }

        return mController.getVolume();
    }

    public boolean setAudioPath(int path) {
        if (mController == null) {
            Log.e(TAG, "setAudioPath() mController is null");
            return false;
        }

        synchronized(mFmAudioPathLock) {
            if (mFmAudioPath != path) {
                AudioManager am = ((AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE));
                if (path == FM_AUDIO_PATH_NONE) {
                    am.setWiredDeviceConnectionState(AudioManager.DEVICE_OUT_FM_SPEAKER, AudioSystem.DEVICE_STATE_UNAVAILABLE, "");
                    am.setWiredDeviceConnectionState(AudioManager.DEVICE_OUT_FM_HEADSET, AudioSystem.DEVICE_STATE_UNAVAILABLE, "");
                } else if (path == FM_AUDIO_PATH_SPEAKER) {
                    am.setWiredDeviceConnectionState(AudioManager.DEVICE_OUT_FM_HEADSET, AudioSystem.DEVICE_STATE_UNAVAILABLE, "");
                    am.setWiredDeviceConnectionState(AudioManager.DEVICE_OUT_FM_SPEAKER, AudioSystem.DEVICE_STATE_AVAILABLE, "");
                } else if (path == FM_AUDIO_PATH_HEADSET) {
                    am.setWiredDeviceConnectionState(AudioManager.DEVICE_OUT_FM_SPEAKER, AudioSystem.DEVICE_STATE_UNAVAILABLE, "");
                    am.setWiredDeviceConnectionState(AudioManager.DEVICE_OUT_FM_HEADSET, AudioSystem.DEVICE_STATE_AVAILABLE, "");
                }

                mFmAudioPath = path;
            }
        }

        return true;
    }

    public int getAudioPath() {
        return mFmAudioPath;
    }

    public boolean isFmOn() {
        if (mController == null) {
            Log.e(TAG, "isFmOn() mController is null");
            return false;
        }

        return mController.isFmOn();
    }

    public int getError() {
        if (mController == null) {
            Log.e(TAG, "getError() mController is null");
            return FmConsts.FM_STATE_UNINITIALIZED;
        }

        return mController.getError();
    }
}
