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

/* Usage:
  * Please add following lines into your *.h or *.cpp file 
  *     #include <media/stagefright/Innofidei.h>
  */

#ifndef INNOFIDEI_H_

#define INNOFIDEI_H_

namespace android {

enum {
    ERROR_INNOFIDEI_BASE = -2000,
    ERROR_UNKNOWN     = ERROR_INNOFIDEI_BASE,    
    ERROR_UNSUPPORTED_TYPE     = ERROR_INNOFIDEI_BASE - 1,   
    ERROR_NO_DATA     = ERROR_INNOFIDEI_BASE - 2,
    ERROR_INVALIDDATA = ERROR_INNOFIDEI_BASE - 3,
};
// #define USE_ARM_ASSEMBLY	// since emulator doesn't support it, we enable it only when run the *.so on devkit8000 board

// for codec options to select using software or hardware codecs, or other third part codecs.
#define USE_FFMPEG_CODEC
// #define USE_INNOHW_CODEC

// give priority to output rather than decode in DPB management
#define DPB_OUTPUT_FIRST

// for AV sync
#define AVSYNC_LATENESS_THR1 40000    // us
#define AVSYNC_LATENESS_THR2 -200000    

/*********** below is for profiling and debug  **************/

//#define AVC_PROFILE
//#define AVC_DEBUG

//#define AAC_PROFILE
//#define AAC_DEBUG
//#define AAC_TO_FILE

#define AWESOME_PLAYER_PROFILE
//define AWESOME_PLAYER_DEBUG

}  // namespace android

#endif  // INNOFIDEI_H_
