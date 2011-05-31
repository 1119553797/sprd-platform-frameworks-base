/*
 * Copyright (C) 2009 The Android Open Source Project
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

#include <stddef.h>
#include <sys/time.h>

#include <media/stagefright/TimeSource.h>

namespace android {

SystemTimeSource::SystemTimeSource()
    : mStartTimeUs(GetSystemTimeUs()) {
}

int64_t SystemTimeSource::getRealTimeUs() {
    return GetSystemTimeUs() - mStartTimeUs;
}

// static
int64_t SystemTimeSource::GetSystemTimeUs() {
    struct timeval tv;
    gettimeofday(&tv, NULL);

    return (int64_t)tv.tv_sec * 1000000 + tv.tv_usec;
}

SystemTimeSourceForSync::SystemTimeSourceForSync()
    : mStartTimeUs(GetSystemTimeUs()),mPauseTimeUs(-1),mTotalPauseTimeUs(0),mIsPaused(false) {
}

int64_t SystemTimeSourceForSync::getRealTimeUs() {
    Mutex::Autolock autoLock(mLock);	
    if(mIsPaused){
    	return mPauseTimeUs - mStartTimeUs -mTotalPauseTimeUs;	
    }else{
    	return GetSystemTimeUs() - mStartTimeUs - mTotalPauseTimeUs;
    }
}

void SystemTimeSourceForSync::increaseRealTimeUs(int64_t deltaTime)
{
    Mutex::Autolock autoLock(mLock);	
    mStartTimeUs -= deltaTime;
}
	
// static
int64_t SystemTimeSourceForSync::GetSystemTimeUs() {
    struct timeval tv;
    gettimeofday(&tv, NULL);

    return (int64_t)tv.tv_sec * 1000000 + tv.tv_usec;
}

 void SystemTimeSourceForSync::pause(){
        Mutex::Autolock autoLock(mLock);	
 	if(mIsPaused)
		return;
 	mPauseTimeUs = GetSystemTimeUs();
 	mIsPaused = true;
 }
 
 void SystemTimeSourceForSync::resume(){
        Mutex::Autolock autoLock(mLock);	
 	if(!mIsPaused)
		return;
 	mTotalPauseTimeUs += GetSystemTimeUs() - mPauseTimeUs;
 	mIsPaused = false;
 }
 
void SystemTimeSourceForSync::reset(){
        Mutex::Autolock autoLock(mLock);	
	mStartTimeUs = GetSystemTimeUs();
	mPauseTimeUs = -1;
	mTotalPauseTimeUs	 = 0;
	mIsPaused = false;
}
	
}  // namespace android

