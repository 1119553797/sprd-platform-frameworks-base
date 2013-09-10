
package android.hardware.fm;

public class FmConsts {
    public enum FmBand {
        FM_BAND_NA,
        FM_BAND_EU,
        FM_BAND_JP_STD,
        FM_BAND_JP_WIDE,
        FM_BAND_UNKNOWN
    };

    public enum FmAudioMode {
        FM_AUDIO_MODE_AUTO,
        FM_AUDIO_MODE_STEREO,
        FM_AUDIO_MODE_MONO,
        FM_AUDIO_MODE_UNKNOWN
    };

    public enum FmSearchDirection {
        FM_SEARCH_DOWN,
        FM_SEARCH_UP
    };

    public enum FmStepType {
        FM_STEP_50KHZ,
        FM_STEP_100KHZ,
        FM_STEP_UNKNOWN
    };

    public enum FmAudioPath {
        FM_AUDIO_PATH_SPEAKER,
        FM_AUDIO_PATH_HEADSET,
        FM_AUDIO_PATH_NONE,
        FM_AUDIO_PATH_UNKNOWN
    };

    public enum FmMuteMode {
        FM_MUTE_MODE_UNMUTE,
        FM_MUTE_MODE_MUTE,
        FM_MUTE_MODE_UNKNOWN
    };

    public static final int FM_STATE_NO_ERROR = 0;

    public static final int FM_STATE_UNINITIALIZED = -(1<<10);

    public static final int FM_STATE_INVALID_VALUE = -(1<<20);

    public static final int FM_STATE_UNKNOWN = -(1<<30);
}
