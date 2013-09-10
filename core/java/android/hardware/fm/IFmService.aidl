
package android.hardware.fm;

import android.os.IBinder;

/**
 * @hide
 */
interface IFmService {
    boolean powerUp(IBinder observer);
    boolean powerDown();

    boolean startSearch(int freq, int direction, int timeout);
    boolean cancelSearch();

    boolean setFreq(int freq);
    int getFreq();

    boolean setAudioMode(int mode);
    int getAudioMode();

    boolean setStepType(int type);
    int getStepType();

    boolean setBand(int band);
    int getBand();

    boolean mute();
    boolean unmute();
    boolean isMuted();

    boolean setVolume(int volume);
    int getVolume();

    boolean setRssi(int rssi);
    int getRssi();

    boolean setAudioPath(int path);
    int getAudioPath();

    boolean isFmOn();

    int getError();
}
