
#ifndef KT_FM_IMPL_H
#define KT_FM_IMPL_H

#include "FmInterface.h"
#include <hardware/fm.h>
#include <utils/threads.h>

namespace android {

    class KTFmImpl : public FmInterface {

    public:
        static FmInterface* getInstance();

        virtual ~KTFmImpl();

        virtual int powerUp();
        virtual int powerDown();

        virtual int setFreq(int freq);
        virtual int getFreq();

        virtual int startSearch(int freq, int direction, int timeout);
        virtual int cancelSearch();

        virtual int setAudioMode(int mode);
        virtual int getAudioMode();

        virtual int setStepType(int type);
        virtual int getStepType();

        virtual int setBand(int band);
        virtual int getBand();

        virtual int setMuteMode(int mode);
        virtual int getMuteMode();

        virtual int setVolume(int volume);
        virtual int getVolume();

        virtual int setRssi(int rssi);
        virtual int getRssi();

    private:
        KTFmImpl();

        static hw_module_t* mModule;
        static Mutex        mModuleLock;

        fm_device_t*        mDevice;
        Mutex               mDeviceLock;

        volatile bool       mIsSearching;

        int                 mMuteState;
        int                 mVolume;
        int                 mRssi;
        int                 mStepType;
    };

};

#endif
