
package android.hardware.fm;

/**
 * @hide
 */
public class FmJni {

    public static synchronized native int powerUp();
    public static synchronized native int powerDown();

    public static synchronized native int setFreq(int freq);
    public static synchronized native int getFreq();

    public static synchronized native int startSearch(int freq, int direction, int timeout);
    public static native int cancelSearch();

    public static synchronized native int setAudioMode(int mode);
    public static synchronized native int getAudioMode();

    public static synchronized native int setStepType(int type);
    public static synchronized native int getStepType();

    public static synchronized native int setBand(int band);
    public static synchronized native int getBand();

    public static synchronized native int setMuteMode(int mode);
    public static synchronized native int getMuteMode();

    public static synchronized native int setVolume(int volume);
    public static synchronized native int getVolume();

    public static synchronized native int setRssi(int rssi);
    public static synchronized native int getRssi();
}
