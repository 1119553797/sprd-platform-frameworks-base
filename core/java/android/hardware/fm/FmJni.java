
package android.hardware.fm;

/**
 * @hide
 */
public class FmJni {

    public static native int powerUp();
    public static native int powerDown();

    public static native int setFreq(int freq);
    public static native int getFreq();

    public static native int startSearch(int freq, int direction, int timeout);
    public static native int cancelSearch();

    public static native int setAudioMode(int mode);
    public static native int getAudioMode();

    public static native int setStepType(int type);
    public static native int getStepType();

    public static native int setBand(int band);
    public static native int getBand();

    public static native int setMuteMode(int mode);
    public static native int getMuteMode();

    public static native int setVolume(int volume);
    public static native int getVolume();

    public static native int setRssi(int rssi);
    public static native int getRssi();
}
