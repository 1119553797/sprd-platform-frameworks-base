#ifndef FM_INTERFACE_H
#define FM_INTERFACE_H

namespace android {

    class FmInterface {

    public:
        virtual ~FmInterface() {};

        /**
         * power up fm
         * return value: success=0, fail!=0
         */
        virtual int powerUp() = 0;

        /**
         * power down fm
         * return value: success=0, fail!=0
         */
        virtual int powerDown() = 0;

        /**
         * tune frequency(unit: KHz)
         * return value: success=0, fail!=0
         */
        virtual int setFreq(int freq) = 0;

        /**
         * get current frequency
         * return value: success=frequency(unit: KHz), fail<0
         */
        virtual int getFreq() = 0;

        /**
         * do search
         * freq: start frequency(unit: KHz)
         * direction: down=0, up=1
         * timeout: maximum time for search(unit: ms)
         * return value: success=0, fail!=0
         */
        virtual int startSearch(int freq, int direction, int timeout) = 0;

        /**
         * cancelSearch
         * return value: success=0, fail!=0
         */
        virtual int cancelSearch() = 0;

        /**
         * set audio mode
         * mode: auto=0, stereo=1, mono=2
         * return value: success=0, fail!=0
         */
        virtual int setAudioMode(int mode) = 0;

        /**
         * get audio mode
         * return value: success=audio mode, fail<0
         */
        virtual int getAudioMode() = 0;

        /**
         * set search step type
         * type: 50KHz/step=0, 100KHz/step=1
         * return value: success=0, fail!=0
         */
        virtual int setStepType(int type) = 0;

        /**
         * get search step type
         * return value: success=step type, fail<0
         */
        virtual int getStepType() = 0;

        /** 
         * set fm band
         * band: NA=0, EU=1, JP_STD=2, JP_WIDE=3
         * return value: success=0, fail!=0
         */
        virtual int setBand(int band) = 0;

        /**
         * get fm band
         * return value: success=band type, fail<0
         */
        virtual int getBand() = 0;

        /**
         * mute control
         * mode: unmute=0, mute=1
         * return value: success=0, fail!=0
         */
        virtual int setMuteMode(int mode) = 0;

        /**
         * get mute state
         * return value: success=mute type, fail<0
         */
        virtual int getMuteMode() = 0;

        /**
         * set volume
         * volume: range(0-15)
         * return value: success=0, fail!=0
         */
        virtual int setVolume(int volume) = 0;

        /**
         * get volume
         * return value: success=volume, fail<0
         */
        virtual int getVolume() = 0;

        /**
         * set rssi
         * return value: success=0, fail!=0
         */
        virtual int setRssi(int rssi) = 0;

        /**
         * get rssi
         * return value: success=rssi, fail<0
         */
        virtual int getRssi() = 0;
    };

};

#endif
