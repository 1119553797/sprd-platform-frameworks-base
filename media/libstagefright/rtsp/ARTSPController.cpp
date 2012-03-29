/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "ARTSPController.h"

#include "MyHandler.h"

#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>

namespace android {

ARTSPController::ARTSPController(const sp<ALooper> &looper)
    : mState(DISCONNECTED),
      mLooper(looper),
      mSeekDoneCb(NULL),
      mPauseDoneCb(NULL),
      mPlayDoneCb(NULL),
      mSeekDoneCookie(NULL),
      mPlayDoneCookie(NULL),
      mPauseDoneCookie(NULL),
      mLastSeekCompletedTimeUs(-1),
      mLastPauseCompletedTimeUs(-1),
      mLastPlayCompletedTimeUs(-1)
      
      {
    mReflector = new AHandlerReflector<ARTSPController>(this);
    looper->registerHandler(mReflector);
}

ARTSPController::~ARTSPController() {
    CHECK_EQ((int)mState, (int)DISCONNECTED);
    mLooper->unregisterHandler(mReflector->id());
}

status_t ARTSPController::connect(const char *url) {
    Mutex::Autolock autoLock(mLock);

    if (mState != DISCONNECTED) {
        return ERROR_ALREADY_CONNECTED;
    }

    sp<AMessage> msg = new AMessage(kWhatConnectDone, mReflector->id());

    mHandler = new MyHandler(url, mLooper);

    mState = CONNECTING;

    mHandler->connect(msg);

    while (mState == CONNECTING) {
        mCondition.wait(mLock);
    }

    if (mState != CONNECTED) {
        mHandler.clear();
    }

    return mConnectionResult;
}

void ARTSPController::disconnect() {
    Mutex::Autolock autoLock(mLock);

    if (mState == CONNECTING) {
        mState = DISCONNECTED;
        mConnectionResult = ERROR_IO;
        mCondition.broadcast();

        mHandler.clear();
        return;
    } else if (mState != CONNECTED) {
        return;
    }

    sp<AMessage> msg = new AMessage(kWhatDisconnectDone, mReflector->id());
    mHandler->disconnect(msg);

    while (mState == CONNECTED) {
        mCondition.wait(mLock);
    }

    mHandler.clear();
}

void ARTSPController::seekAsync(
        int64_t timeUs,
        void (*seekDoneCb)(void *,int32_t), void *cookie) {
    Mutex::Autolock autoLock(mLock);

    CHECK(seekDoneCb != NULL);
    // Ignore seek requests that are too soon after the previous one has
    // completed, we don't want to swamp the server.
    if (mState != CONNECTED ) {
        (*seekDoneCb)(cookie,0);
		LOGE("ARTSPController::seekAsync tooEarly so return") ;
        return;
    }

    mSeekDoneCb = seekDoneCb;
    mSeekDoneCookie = cookie;

    sp<AMessage> msg = new AMessage(kWhatSeekDone, mReflector->id());
    mHandler->seek(timeUs, msg);
}


void ARTSPController::pauseAsync(
        int64_t timeUs,
        void (*pauseDoneCb)(void *,int32_t), void *cookie) {
    Mutex::Autolock autoLock(mLock);

    CHECK(pauseDoneCb != NULL);
    // Ignore seek requests that are too soon after the previous one has
    // completed, we don't want to swamp the server.
    if (mState != CONNECTED) {
        (*pauseDoneCb)(cookie,0);
		LOGE("ARTSPController::pauseAsync tooEarly so return") ;
		return;
    }

    mPauseDoneCb = pauseDoneCb;
    mPauseDoneCookie = cookie;

    sp<AMessage> msg = new AMessage(kWhatPauseDone, mReflector->id());
	LOGE("ARTSPController::pauseAsync to time %lld",timeUs) ;
    mHandler->pause(timeUs, msg);
}

void ARTSPController::playAsync(
        int64_t timeUs,
        void (*playDoneCb)(void *,int32_t), void *cookie) {
    Mutex::Autolock autoLock(mLock);

    CHECK(playDoneCb != NULL);
    // Ignore seek requests that are too soon after the previous one has
    // completed, we don't want to swamp the server.
    if (mState != CONNECTED) {
        (*playDoneCb)(cookie,0);
	    LOGE("ARTSPController::playAsync tooEarly so return") ;
        return;
    }

    mPlayDoneCb = playDoneCb;
    mPlayDoneCookie = cookie;

    sp<AMessage> msg = new AMessage(kWhatPlayDone, mReflector->id());
	LOGE("ARTSPController::playAsync to time %lld",timeUs) ;
    mHandler->play(timeUs, msg);
}


size_t ARTSPController::countTracks() {
    if (mHandler == NULL) {
        return 0;
    }

    return mHandler->countTracks();
}

sp<MediaSource> ARTSPController::getTrack(size_t index) {
    CHECK(mHandler != NULL);

    return mHandler->getPacketSource(index);
}

sp<MetaData> ARTSPController::getTrackMetaData(
        size_t index, uint32_t flags) {
    CHECK(mHandler != NULL);

    return mHandler->getPacketSource(index)->getFormat();
}

bool ARTSPController::getSeekable()
{
	   CHECK(mHandler != NULL);
	   return mHandler->getSeekable();
	   
}


void ARTSPController::onMessageReceived(const sp<AMessage> &msg) {
    switch (msg->what()) {
        case kWhatConnectDone:
        {
            Mutex::Autolock autoLock(mLock);

            CHECK(msg->findInt32("result", &mConnectionResult));
            mState = (mConnectionResult == OK) ? CONNECTED : DISCONNECTED;

            mCondition.signal();
            break;
        }

        case kWhatDisconnectDone:
        {
            Mutex::Autolock autoLock(mLock);
            mState = DISCONNECTED;
            mCondition.signal();
            break;
        }

        case kWhatSeekDone:
        {
            LOGI("seek done");

			int32_t status = -1;
            CHECK(msg->findInt32("result", &status));

            mLastSeekCompletedTimeUs = ALooper::GetNowUs();

            void (*seekDoneCb)(void *,int32_t) = mSeekDoneCb;
            (*seekDoneCb)(mSeekDoneCookie,status);

			break;
        }
		case kWhatPauseDone:
        {
            LOGE("ARTSPController PauseDone done");
			int32_t status = -1;
			CHECK(msg->findInt32("result", &status));
            mLastPauseCompletedTimeUs = ALooper::GetNowUs();
        	void (*pauseDoneCb)(void *,int32_t) = mPauseDoneCb;

	       (*pauseDoneCb)(mPauseDoneCookie,status);

             break;
        }

		case kWhatPlayDone:
        {
            LOGE("ARTSPController PlayDone done");
			int32_t status = -1;
			CHECK(msg->findInt32("result", &status));

            mLastPlayCompletedTimeUs = ALooper::GetNowUs();

  			void (*playDoneCb)(void *,int32_t) = mPlayDoneCb;
				
			(*playDoneCb)(mPlayDoneCookie,status);
       			
            break;
        }

        default:
            //TRESPASS();
            LOGE("ARTSPController onMessageReceived no do process ");
            break;
    }
}

int64_t ARTSPController::getNormalPlayTimeUs() {
    CHECK(mHandler != NULL);
    return mHandler->getNormalPlayTimeUs();
}

int64_t ARTSPController::getQueueDurationUs(bool *eos) {
    *eos = true;

    int64_t minQueuedDurationUs = 0;
    LOGI("getQueueDurationUs");
    for (size_t i = 0; i < mHandler->countTracks(); ++i) {
        sp<APacketSource> source = mHandler->getPacketSource(i);

        bool newEOS;
        int64_t queuedDurationUs = source->getQueueDurationUs(&newEOS);

        if (!newEOS) {
            *eos = false;
        }

        if (i == 0 || queuedDurationUs < minQueuedDurationUs) {
            minQueuedDurationUs = queuedDurationUs;
        }
    }

    return minQueuedDurationUs;
}

void  ARTSPController::stopSource() {  //@hong   

    LOGI("stopSource");
    for (size_t i = 0; i < mHandler->countTracks(); ++i) {
        sp<APacketSource> source = mHandler->getPacketSource(i);

        source->stop();

    }

    return;
}

}  // namespace android
