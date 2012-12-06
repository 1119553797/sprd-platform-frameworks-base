
package android.hardware.fm;

import android.hardware.fm.FmConsts.*;
import android.util.Log;

/**
 * @hide
 */
class FmController {
    private final String TAG = "FmController";

    private final int FM_BAND_NA = 0;

    private final int FM_BAND_EU = 1;

    private final int FM_BAND_JP_STD = 2;

    private final int FM_BAND_JP_WIDE = 3;

    private final int FM_BAND_UNKNOWN = 4;

    private final int FM_AUDIO_MODE_UNKNOWN = 3;

    private final int FM_STEP_UNKNOWN = 2;

    private final int FM_AUDIO_PATH_UNKNOWN = 3;

    private final int FM_MUTE_MODE_UNKNOWN = 2;

    private final int MIN_VOLUME = 0;

    private final int MAX_VOLUME = 15;

    private int mError;

    private volatile boolean mIsOn;

    private volatile int mBand;

    public FmController() {
        mError = 0;
        mIsOn = false;
        mBand = FM_BAND_NA;
    }

    public boolean powerUp() {
        mError = 0;

        if (!mIsOn) {
            mError = FmJni.powerUp();
            mIsOn = (mError == 0);
        }

        return (mError == 0);
    }

    public boolean powerDown() {
        mError = 0;

        if (mIsOn) {
            mError = FmJni.powerDown();
            mIsOn = false;
        }

        return (mError == 0);
    }

    private boolean isFreqValid(int freq) {
        int minFreq = -1;
        int maxFreq = -1;

        switch (mBand) {
        case FM_BAND_NA: {
            minFreq = 87500;
            maxFreq = 108000;
        }
        break;

        case FM_BAND_EU: {
            minFreq = 87500;
            maxFreq = 108000;
        }
        break;

        case FM_BAND_JP_STD: {
            minFreq = 76000;
            maxFreq = 90000;
        }
        break;

        case FM_BAND_JP_WIDE: {
            minFreq = 76000;
            maxFreq = 90000;
        }
        break;

        default: {
            minFreq = 87500;
            maxFreq = 108000;
        }
        }

        return (freq >= minFreq && freq <=maxFreq);
    }

    public boolean startSearch(int freq, int direction, int timeout) {
        mError = FmConsts.FM_STATE_UNINITIALIZED;

        if (!isFreqValid(freq)) {
            mError = FmConsts.FM_STATE_INVALID_VALUE;
        } else if (mIsOn) {
            mError = FmJni.startSearch(freq, direction, timeout);
        }

        return (mError == 0);
    }

    public boolean cancelSearch() {
        mError = FmConsts.FM_STATE_UNINITIALIZED;

        if (mIsOn) {
            mError = FmJni.cancelSearch();
        }

        return (mError == 0);
    }

    public boolean setFreq(int freq) {
        mError = FmConsts.FM_STATE_UNINITIALIZED;

        if (!isFreqValid(freq)) {
            mError = FmConsts.FM_STATE_INVALID_VALUE;
        } else if (mIsOn) {
            mError = FmJni.setFreq(freq);
        }

        return (mError == 0);
    }

    public int getFreq() {
        mError = FmConsts.FM_STATE_UNINITIALIZED;
        int value = -1;

        if (mIsOn) {
            int result = FmJni.getFreq();
            if (result < 0) {
                mError = result;
            } else {
                mError = 0;
                value = result;
            }
        }

        return value;
    }

    public boolean setAudioMode(int mode) {
        mError = FmConsts.FM_STATE_UNINITIALIZED;

        if (mode == FM_AUDIO_MODE_UNKNOWN) {
            mError = FmConsts.FM_STATE_INVALID_VALUE;
        } else if (mIsOn) {
            mError = FmJni.setAudioMode(mode);
        }

        return (mError == 0);
    }

    public int getAudioMode() {
        mError = FmConsts.FM_STATE_UNINITIALIZED;
        int value = FM_AUDIO_MODE_UNKNOWN;

        if (mIsOn) {
            int result = FmJni.getAudioMode();
            if (result < 0) {
                mError = result;
            } else {
                mError = 0;
                value = result;
            }
        }

        return value;
    }

    public boolean setStepType(int type) {
        mError = FmConsts.FM_STATE_UNINITIALIZED;

        if (type == FM_STEP_UNKNOWN) {
            mError = FmConsts.FM_STATE_INVALID_VALUE;
        } else if (mIsOn) {
            mError = FmJni.setStepType(type);
        }

        return (mError == 0);
    }

    public int getStepType() {
        mError = FmConsts.FM_STATE_UNINITIALIZED;
        int value = FM_STEP_UNKNOWN;

        if (mIsOn) {
            int result = FmJni.getStepType();
            if (result < 0) {
                mError = result;
            } else {
                mError = 0;
                value = result;
            }
        }

        return value;
    }

    public boolean setBand(int band) {
        mError = FmConsts.FM_STATE_UNINITIALIZED;

        if (band == FM_BAND_UNKNOWN) {
            mError = FmConsts.FM_STATE_INVALID_VALUE;
        } else if (mIsOn) {
            mError = FmJni.setBand(band);
            if (mError == 0) {
                mBand = band;
            }
        }

        return (mError == 0);
    }

    public int getBand() {
        mError = FmConsts.FM_STATE_UNINITIALIZED;
        int value = FM_BAND_UNKNOWN;

        if (mIsOn) {
            int result = FmJni.getBand();
            if (result < 0) {
                mError = result;
            } else {
                mError = 0;
                value = result;
            }
        }

        return value;
    }

    public boolean setMuteMode(int mode) {
       mError = FmConsts.FM_STATE_UNINITIALIZED;

        if (mode == FM_MUTE_MODE_UNKNOWN) {
            mError = FmConsts.FM_STATE_INVALID_VALUE;
        } else {
            mError = FmJni.setMuteMode(mode);
        }

        return (mError == 0);
    }

    public int getMuteMode() {
        mError = FmConsts.FM_STATE_UNINITIALIZED;
        int value = FM_MUTE_MODE_UNKNOWN;

        if (mIsOn) {
            int result = FmJni.getMuteMode();
            if (result < 0) {
                mError = result;
            } else {
                mError = 0;
                value = result;
            }
        }

        return value;
    }

    private boolean isVolumeValid(int volume) {
        return (volume >= MIN_VOLUME && volume <= MAX_VOLUME);
    }

    public boolean setVolume(int volume) {
        mError = FmConsts.FM_STATE_UNINITIALIZED;

        if (!isVolumeValid(volume)) {
            mError = FmConsts.FM_STATE_INVALID_VALUE;
        } else if (mIsOn) {
            mError = FmJni.setVolume(volume);
        }

        return (mError == 0);
    }

    public int getVolume() {
        mError = FmConsts.FM_STATE_UNINITIALIZED;
        int value = -1; 

        if (mIsOn) {
            int result = FmJni.getVolume();
            if (result < 0) {
                mError = result;
            } else {
                mError = 0;
                value = result;
            }
        }

        return value;
    }

    public boolean setRssi(int rssi) {
        mError = FmConsts.FM_STATE_UNINITIALIZED;

        if (rssi <= 0) {
            mError = FmConsts.FM_STATE_INVALID_VALUE;
        } else if (mIsOn) {
            mError = FmJni.setRssi(rssi);
        }

        return (mError == 0);
    }

    public int getRssi() {
        mError = FmConsts.FM_STATE_UNINITIALIZED;
        int value = -1; 

        if (mIsOn) {
            int result = FmJni.getRssi();
            if (result < 0) {
                mError = result;
            } else {
                mError = 0;
                value = result;
            }
        }

        return value;
    }

    public boolean isFmOn() {
        return mIsOn;
    }

    public int getError() {
        return mError;
    }
}
