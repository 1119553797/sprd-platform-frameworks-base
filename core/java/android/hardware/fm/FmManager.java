
package android.hardware.fm;

import android.os.IBinder;
import android.os.Binder;
import android.content.Context;
import android.os.ServiceManager;
import android.os.RemoteException;
import android.hardware.fm.FmConsts.*;
import android.util.Log;

public class FmManager {
    private static final String TAG = "FmManager";

    private IBinder mObserver = new Binder();

    private final Context mContext;

    private static IFmService mService;

    public FmManager(Context context) {
        mContext = context;
    }

    private static IFmService getService() {
        if (mService != null) {
            return mService;
        }

        IBinder binder = ServiceManager.getService(Context.FM_SERVICE);
        mService = IFmService.Stub.asInterface(binder);

        return mService;
    }

    public boolean powerUp() {
        IFmService service = getService();
        boolean result = false;
        try {
            result = service.powerUp(mObserver);
        } catch(RemoteException e) {
            Log.e(TAG, "Dead object in powerUp()", e);
        } catch(NullPointerException e) {
            Log.e(TAG, "No fm service in powerUp()", e);
        }

        return result;
    }

    public boolean powerDown() {
        IFmService service = getService();
        boolean result = false;
        try {
            result = service.powerDown();
        } catch(RemoteException e) {
            Log.e(TAG, "Dead object in powerDown()", e);
        } catch(NullPointerException e) {
            Log.e(TAG, "No fm service in powerDown()", e);
        }

        return result;
    }

    public boolean startSearch(int freq, FmSearchDirection direction, int timeout) {
        IFmService service = getService();
        boolean result = false;
        try {
            result = service.startSearch(freq, direction.ordinal(), timeout);
        } catch(RemoteException e) {
            Log.e(TAG, "Dead object in startSearch()", e);
        } catch(NullPointerException e) {
            Log.e(TAG, "No fm service in startSearch()", e);
        }

        return result;
    }

    public boolean cancelSearch() {
        IFmService service = getService();
        boolean result = false;
        try {
            result = service.cancelSearch();
        } catch(RemoteException e) {
            Log.e(TAG, "Dead object in cancelSearch()", e);
        } catch(NullPointerException e) {
            Log.e(TAG, "No fm service in cancelSearch()", e);
        }

        return result;
    }

    public boolean setFreq(int freq) {
        IFmService service = getService();
        boolean result = false;
        try {
            result = service.setFreq(freq);
        } catch(RemoteException e) {
            Log.e(TAG, "Dead object in setFreq()", e);
        } catch(NullPointerException e) {
            Log.e(TAG, "No fm service in setFreq()", e);
        }

        return result;
    }

    public int getFreq() {
        IFmService service = getService();
        int result = -1;
        try {
            result = service.getFreq();
        } catch(RemoteException e) {
            Log.e(TAG, "Dead object in getFreq()", e);
        } catch(NullPointerException e) {
            Log.e(TAG, "No fm service in getFreq()", e);
        }

        return result;
    }

    public boolean setAudioMode(FmAudioMode mode) {
        IFmService service = getService();
        boolean result = false;
        try {
            result = service.setAudioMode(mode.ordinal());
        } catch(RemoteException e) {
            Log.e(TAG, "Dead object in setAudioMode()", e);
        } catch(NullPointerException e) {
            Log.e(TAG, "No fm service in setAudioMode()", e);
        }

        return result;
    }

    public FmAudioMode getAudioMode() {
        IFmService service = getService();
        FmAudioMode result = FmAudioMode.FM_AUDIO_MODE_UNKNOWN;
        try {
            result = FmAudioMode.values()[service.getAudioMode()];
        } catch(RemoteException e) {
            Log.e(TAG, "Dead object in getAudioMode()", e);
        } catch(NullPointerException e) {
            Log.e(TAG, "No fm service in getAudioMode()", e);
        }

        return result;
    }

    public boolean setStepType(FmStepType type) {
        IFmService service = getService();
        boolean result = false;
        try {
            result = service.setStepType(type.ordinal());
        } catch(RemoteException e) {
            Log.e(TAG, "Dead object in setStepType()", e);
        } catch(NullPointerException e) {
            Log.e(TAG, "No fm service in setStepType()", e);
        }

        return result;
    }

    public FmStepType getStepType() {
        IFmService service = getService();
        FmStepType result = FmStepType.FM_STEP_UNKNOWN;
        try {
            result = FmStepType.values()[service.getStepType()];
        } catch(RemoteException e) {
            Log.e(TAG, "Dead object in getStepType()", e);
        } catch(NullPointerException e) {
            Log.e(TAG, "No fm service in getStepType()", e);
        }

        return result;
    }

    public boolean setBand(FmBand band) {
        IFmService service = getService();
        boolean result = false;
        try {
            result = service.setBand(band.ordinal());
        } catch(RemoteException e) {
            Log.e(TAG, "Dead object in setBand()", e);
        } catch(NullPointerException e) {
            Log.e(TAG, "No fm service in setBand()", e);
        }

        return result;
    }

    public FmBand getBand() {
        IFmService service = getService();
        FmBand result = FmBand.FM_BAND_UNKNOWN;
        try {
            result = FmBand.values()[service.getBand()];
        } catch(RemoteException e) {
            Log.e(TAG, "Dead object in getBand()", e);
        } catch(NullPointerException e) {
            Log.e(TAG, "No fm service in getBand()", e);
        }

        return result;
    }

    public boolean mute() {
        IFmService service = getService();
        boolean result = false;
        try {
            result = service.mute();
        } catch(RemoteException e) {
            Log.e(TAG, "Dead object in mute()", e);
        } catch(NullPointerException e) {
            Log.e(TAG, "No fm service in mute()", e);
        }

        return result;
    }

    public boolean unmute() {
        IFmService service = getService();
        boolean result = false;
        try {
            result = service.unmute();
        } catch(RemoteException e) {
            Log.e(TAG, "Dead object in unmute()", e);
        } catch(NullPointerException e) {
            Log.e(TAG, "No fm service in unmute()", e);
        }

        return result;
    }

    public boolean isMuted() {
        IFmService service = getService();
        boolean result = false;
        try {
            result = service.isMuted();
        } catch(RemoteException e) {
            Log.e(TAG, "Dead object in isMuted()", e);
        } catch(NullPointerException e) {
            Log.e(TAG, "No fm service in isMuted()", e);
        }

        return result;
    }

    public boolean setVolume(int volume) {
        IFmService service = getService();
        boolean result = false;
        try {
            result = service.setVolume(volume);
        } catch(RemoteException e) {
            Log.e(TAG, "Dead object in setVolume()", e);
        } catch(NullPointerException e) {
            Log.e(TAG, "No fm service in setVolume()", e);
        }

        return result;
    }

    public int getVolume() {
        IFmService service = getService();
        int result = -1;
        try {
            result = service.getVolume();
        } catch(RemoteException e) {
            Log.e(TAG, "Dead object in getVolume()", e);
        } catch(NullPointerException e) {
            Log.e(TAG, "No fm service in getVolume()", e);
        }

        return result;
    }

    public boolean setRssi(int rssi) {
        IFmService service = getService();
        boolean result = false;
        try {
            result = service.setRssi(rssi);
        } catch(RemoteException e) {
            Log.e(TAG, "Dead object in setRssi()", e);
        } catch(NullPointerException e) {
            Log.e(TAG, "No fm service in setRssi()", e);
        }

        return result;
    }

    public int getRssi() {
        IFmService service = getService();
        int result = -1;
        try {
            result = service.getRssi();
        } catch(RemoteException e) {
            Log.e(TAG, "Dead object in getRssi()", e);
        } catch(NullPointerException e) {
            Log.e(TAG, "No fm service in getRssi()", e);
        }

        return result;
    }

    public boolean setAudioPath(FmAudioPath path) {
        IFmService service = getService();
        boolean result = false;
        try {
            result = service.setAudioPath(path.ordinal());
        } catch(RemoteException e) {
            Log.e(TAG, "Dead object in setAudioPath()", e);
        } catch(NullPointerException e) {
            Log.e(TAG, "No fm service in setAudioPath()", e);
        }

        return result;
    }

    public FmAudioPath getAudioPath() {
        IFmService service = getService();
        FmAudioPath result = FmAudioPath.FM_AUDIO_PATH_UNKNOWN;
        try {
            result = FmAudioPath.values()[service.getAudioPath()];
        } catch(RemoteException e) {
            Log.e(TAG, "Dead object in getAudioPath()", e);
        } catch(NullPointerException e) {
            Log.e(TAG, "No fm service in getAudioPath()", e);
        }

        return result;
    }

    public boolean isFmOn() {
        IFmService service = getService();
        boolean result = false;
        try {
            result = service.isFmOn();
        } catch(RemoteException e) {
            Log.e(TAG, "Dead object in isFmOn()", e);
        } catch(NullPointerException e) {
            Log.e(TAG, "No fm service in isFmOn()", e);
        }

        return result;
    }

    public int getError() {
        IFmService service = getService();
        int result = FmConsts.FM_STATE_UNINITIALIZED;

        try {
            result = service.getError();
        } catch(RemoteException e) {
            Log.e(TAG, "Dead object in getError()", e);
        } catch(NullPointerException e) {
            Log.e(TAG, "No fm service in getError()", e);
        }   

        return result;
    }
}
