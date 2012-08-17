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

#define LOG_NDEBUG 0
#define LOG_TAG "FakeCameraSource"
#include <utils/Log.h>
#include <cutils/properties.h>

#include <OMX_Component.h>
#include <binder/IPCThreadState.h>
#include <media/stagefright/FakeCameraSource.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MetaData.h>
#include <surfaceflinger/Surface.h>
#include <utils/String8.h>
#include <cutils/properties.h>

#include <linux/delay.h>

#include <SkStream.h>
#include <SkImageDecoder.h>
#include <SkBitmap.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <binder/MemoryHeapBase.h>
#include <binder/MemoryHeapPmem.h>
#include <linux/android_pmem.h>

namespace android {
static bool s_bDebug = false;
#define DEBUG_LOGD if(s_bDebug)LOGV
#define PMEM_DEV "/dev/pmem_adsp"

static const int64_t CAMERA_SOURCE_TIMEOUT_NS = 3000000000LL;
static const char* FAKE_IMAGE = "/data/data/com.android.phone/files/thumb_local.jpg";

#if 0

#define CLIP(x) ((x)<0) ? 0:((x>255)? 255:(x))
#define EXTRACT_RGBA8888(r, g, b, data){ b = (unsigned char)(((data) & 0xff0000) >> 16); \
                                          g = (unsigned char)(((data) & 0xff00) >> 8); \
                                          r = (unsigned char)(((data) & 0xff));}
#define RGBA_32_SIZE 4
#define GENY16(r, g, b) CLIP(  ( ( (80593 * r)+(77855 * g)+(30728 * b)) >> 15))
#define GENU16(r, g, b) CLIP(128+ ( ( -(45483 * r)-(43936 * g)+(134771 * b)) >> 15 ))
#define GENV16(r, g, b) CLIP(128+ ( ( (134771 * r)-(55532 * g)-(21917 * b)) >> 15  ))

void RGBA8888toYUV420(unsigned char *pIn, unsigned char *pOut, int height, int width)
{
    int   col, row;
        unsigned char     *pu8_yn, *pu8_ys, *pu8_uv;
        unsigned char     *pu8_y_data, *pu8_uv_data;
        unsigned char     *pu8_rgbn_data, *pu8_rgbn;

       unsigned int   u32_pix1, u32_pix2, u32_pix3, u32_pix4;

       int    i32_r00, i32_r01, i32_r10, i32_r11;
       int    i32_g00, i32_g01, i32_g10, i32_g11;
        int    i32_b00, i32_b01, i32_b10, i32_b11;

       int    i32_y00, i32_y01, i32_y10, i32_y11;
       int    i32_u00, i32_u01, i32_u10, i32_u11;
       int    i32_v00, i32_v01, i32_v10, i32_v11;
    
        pu8_rgbn_data   = pIn;
    
        pu8_y_data = pOut;
        pu8_uv_data = pOut + height*width ;
    
    for(row = height; row != 0; row-=2 ){
        /* Current Y plane row pointers */
            pu8_yn = pu8_y_data;
            /* Next Y plane row pointers */
            pu8_ys = pu8_yn + width;
            /* Current U plane row pointer */
            pu8_uv = pu8_uv_data;

            pu8_rgbn = pu8_rgbn_data;
        
        for(col = width; col != 0; col-=2){
                 /* Get four RGB 565 samples from input data */
                        u32_pix1 = *( (unsigned int *) pu8_rgbn);
                        u32_pix2 = *( (unsigned int *) (pu8_rgbn + RGBA_32_SIZE));
                        u32_pix3 = *( (unsigned int *) (pu8_rgbn + width*RGBA_32_SIZE));
                        u32_pix4 = *( (unsigned int *) (pu8_rgbn + width*RGBA_32_SIZE + RGBA_32_SIZE));
                        /* Unpack RGB565 to 8bit R, G, B */
                        /* (x,y) */
                        EXTRACT_RGBA8888(i32_r00,i32_g00,i32_b00,u32_pix1);
                        /* (x+1,y) */
                        EXTRACT_RGBA8888(i32_r10,i32_g10,i32_b10,u32_pix2);
                        /* (x,y+1) */
                        EXTRACT_RGBA8888(i32_r01,i32_g01,i32_b01,u32_pix3);
                        /* (x+1,y+1) */
                        EXTRACT_RGBA8888(i32_r11,i32_g11,i32_b11,u32_pix4);

                /* Convert RGB value to YUV */
                        i32_u00 = GENU16(i32_r00, i32_g00, i32_b00);
                        i32_v00 = GENV16(i32_r00, i32_g00, i32_b00);
                        /* luminance value */
                        i32_y00 = GENY16(i32_r00, i32_g00, i32_b00);

                        i32_u10 = GENU16(i32_r10, i32_g10, i32_b10);
                        i32_v10 = GENV16(i32_r10, i32_g10, i32_b10);
                        /* luminance value */
                        i32_y10 = GENY16(i32_r10, i32_g10, i32_b10);

                        i32_u01 = GENU16(i32_r01, i32_g01, i32_b01);
                        i32_v01 = GENV16(i32_r01, i32_g01, i32_b01);
                        /* luminance value */
                        i32_y01 = GENY16(i32_r01, i32_g01, i32_b01);

                        i32_u11 = GENU16(i32_r11, i32_g11, i32_b11);
                        i32_v11 = GENV16(i32_r11, i32_g11, i32_b11);
                        /* luminance value */
                        i32_y11 = GENY16(i32_r11, i32_g11, i32_b11);

                        /* Store luminance data */
                        pu8_yn[0] = (unsigned char)i32_y00;
                        pu8_yn[1] = (unsigned char)i32_y10;
                        pu8_ys[0] = (unsigned char)i32_y01;
                        pu8_ys[1] = (unsigned char)i32_y11;

                        /* Store chroma data */
                        pu8_uv[0] = (unsigned char)((i32_u00 + i32_u01 + i32_u10 + i32_u11 + 2) >> 2);
                        pu8_uv[1] = (unsigned char)((i32_v00 + i32_v01 + i32_v10 + i32_v11 + 2) >> 2);
                        
                        /* Prepare for next column */
                        pu8_rgbn += 2*RGBA_32_SIZE;
                         /* Update current Y plane line pointer*/
                        pu8_yn += 2;
                        /* Update next Y plane line pointer*/
                        pu8_ys += 2;
                        /* Update U plane line pointer*/
                        pu8_uv +=2;                                        
            }
        /* Prepare pointers for the next row */
        pu8_y_data += width*2;
        pu8_uv_data += width;//width*2;
        pu8_rgbn_data += width*2*RGBA_32_SIZE;
    }
        
}

#define EXTRACT_RGB565(r, g, b, data) { b = (unsigned char)(((data) & 0x1F00) >> 8); g =\
             (unsigned char)((((data) & 0x7) << 3) | (((data) & 0xE000) >> 13)); r =\
             (unsigned char)(((data) & 0xF8) >> 3);}
#endif
#define EXTRACT_RGB565(b, g, r, data) \
    r = ((data) & 31); \
    g = ((data >> 5) & 63); \
    b = ((data >> 11) & 31 );

#define RGB_16_SIZE 2
#define CLIP(v) ( (v)<0 ? 0 : ((v) > 255 ? 255 : (v)) )
#define GENY16(r, g, b) CLIP((( (80593 * r)+(77855 * g)+(30728 * b)) >> 15))
#define GENU16(r, g, b) CLIP(128+ ( ( -(45483 * r)-(43936 * g)+(134771 * b)) >> 15 ))
#define GENV16(r, g, b) CLIP(128+ ( ( (134771 * r)-(55532 * g)-(21917 * b)) >> 15  ))

void RGB565toYUV420(unsigned char *pIn, unsigned char *pOut, int height, int width)
{
    int   col, row;
        unsigned char     *pu8_yn, *pu8_ys, *pu8_uv;
        unsigned char     *pu8_y_data, *pu8_uv_data;
        unsigned char     *pu8_rgbn_data, *pu8_rgbn;

       unsigned short   u16_pix1, u16_pix2, u16_pix3, u16_pix4;

       int    i32_r00, i32_r01, i32_r10, i32_r11;
       int    i32_g00, i32_g01, i32_g10, i32_g11;
        int    i32_b00, i32_b01, i32_b10, i32_b11;

       int    i32_y00, i32_y01, i32_y10, i32_y11;
       int    i32_u00, i32_u01, i32_u10, i32_u11;
       int    i32_v00, i32_v01, i32_v10, i32_v11;
    
        pu8_rgbn_data   = pIn;
    
        pu8_y_data = pOut;
        pu8_uv_data = pOut + height*width ;
    
    for(row = height; row != 0; row-=2 ){
        /* Current Y plane row pointers */
            pu8_yn = pu8_y_data;
            /* Next Y plane row pointers */
            pu8_ys = pu8_yn + width;
            /* Current U plane row pointer */
            pu8_uv = pu8_uv_data;

            pu8_rgbn = pu8_rgbn_data;
        
        for(col = width; col != 0; col-=2){
                 /* Get four RGB 565 samples from input data */
                        u16_pix1 = *( (unsigned short *) pu8_rgbn);
                        u16_pix2 = *( (unsigned short *) (pu8_rgbn + RGB_16_SIZE));
                        u16_pix3 = *( (unsigned short *) (pu8_rgbn + width*RGB_16_SIZE));
                        u16_pix4 = *( (unsigned short *) (pu8_rgbn + width*RGB_16_SIZE + RGB_16_SIZE));
                        /* Unpack RGB565 to 8bit R, G, B */
                        /* (x,y) */
                        EXTRACT_RGB565(i32_r00,i32_g00,i32_b00,u16_pix1);
                        /* (x+1,y) */
                        EXTRACT_RGB565(i32_r10,i32_g10,i32_b10,u16_pix2);
                        /* (x,y+1) */
                        EXTRACT_RGB565(i32_r01,i32_g01,i32_b01,u16_pix3);
                        /* (x+1,y+1) */
                        EXTRACT_RGB565(i32_r11,i32_g11,i32_b11,u16_pix4);

                /* Convert RGB value to YUV */
                        i32_u00 = GENU16(i32_r00, i32_g00, i32_b00);
                        i32_v00 = GENV16(i32_r00, i32_g00, i32_b00);
                        /* luminance value */
                        i32_y00 = GENY16(i32_r00, i32_g00, i32_b00);

                        i32_u10 = GENU16(i32_r10, i32_g10, i32_b10);
                        i32_v10 = GENV16(i32_r10, i32_g10, i32_b10);
                        /* luminance value */
                        i32_y10 = GENY16(i32_r10, i32_g10, i32_b10);

                        i32_u01 = GENU16(i32_r01, i32_g01, i32_b01);
                        i32_v01 = GENV16(i32_r01, i32_g01, i32_b01);
                        /* luminance value */
                        i32_y01 = GENY16(i32_r01, i32_g01, i32_b01);

                        i32_u11 = GENU16(i32_r11, i32_g11, i32_b11);
                        i32_v11 = GENV16(i32_r11, i32_g11, i32_b11);
                        /* luminance value */
                        i32_y11 = GENY16(i32_r11, i32_g11, i32_b11);

                        /* Store luminance data */
                        pu8_yn[0] = (unsigned char)i32_y00;
                        pu8_yn[1] = (unsigned char)i32_y10;
                        pu8_ys[0] = (unsigned char)i32_y01;
                        pu8_ys[1] = (unsigned char)i32_y11;

                        /* Store chroma data */
                        pu8_uv[0] = (unsigned char)((i32_u00 + i32_u01 + i32_u10 + i32_u11 + 2) >> 2);
                        pu8_uv[1] = (unsigned char)((i32_v00 + i32_v01 + i32_v10 + i32_v11 + 2) >> 2);
                        
                        /* Prepare for next column */
                        pu8_rgbn += 2*RGB_16_SIZE;
                         /* Update current Y plane line pointer*/
                        pu8_yn += 2;
                        /* Update next Y plane line pointer*/
                        pu8_ys += 2;
                        /* Update U plane line pointer*/
                        pu8_uv +=2;                                        
            }
        /* Prepare pointers for the next row */
        pu8_y_data += width*2;
        pu8_uv_data += width;//width*2;
        pu8_rgbn_data += width*2*RGB_16_SIZE;
    }
        
}

#if 0

static int loadExifInfo(const char* FileName, int readJPG) {
    int Modified = false;
    ReadMode_t ReadMode = READ_METADATA;
   /* if (readJPG) {
        // Must add READ_IMAGE else we can't write the JPG back out.
        ReadMode |= READ_IMAGE;
    }*/

    ResetJpgfile();

    // Start with an empty image information structure.
    memset(&ImageInfo, 0, sizeof(ImageInfo));
    ImageInfo.FlashUsed = -1;
    ImageInfo.MeteringMode = -1;
    ImageInfo.Whitebalance = -1;

    // Store file date/time.
    /*{
        struct stat st;
        if (stat(FileName, &st) >= 0) {
            ImageInfo.FileDateTime = st.st_mtime;
            ImageInfo.FileSize = st.st_size;
        }
    }*/

    strncpy(ImageInfo.FileName, FileName, PATH_MAX);

    return ReadJpegFile(FileName, ReadMode);
}

static int getOrientationAttributes(const char *filename)
{
    int result = 0;
    loadExifInfo(filename, false);  
    result = ImageInfo.Orientation;    
    DiscardData();
    
    return result;
}
#endif
FakeCameraSource *FakeCameraSource::Create() {
    return new FakeCameraSource(-1, -1, -1);
}

/*
 * Initialize the FakeCameraSource to so that it becomes
 * ready for providing the video input streams as requested.
 * @param camera the camera object used for the video source
 * @param cameraId if camera == 0, use camera with this id
 *      as the video source
 * @param videoSize the target video frame size. If both
 *      width and height in videoSize is -1, use the current
 *      width and heigth settings by the camera
 * @param frameRate the target frame rate in frames per second.
 *      if it is -1, use the current camera frame rate setting.
 * @param storeMetaDataInVideoBuffers request to store meta
 *      data or real YUV data in video buffers. Request to
 *      store meta data in video buffers may not be honored
 *      if the source does not support this feature.
 *
 * @return OK if no error.
 */
status_t FakeCameraSource::init() {

    LOGV("init");
    status_t err = OK;
    mMeta = new MetaData;
    mMeta->setCString(kKeyMIMEType,  MEDIA_MIMETYPE_VIDEO_RAW);
    mMeta->setInt32(kKeyColorFormat, OMX_COLOR_FormatYUV420SemiPlanar);
    mMeta->setInt32(kKeyWidth,       mVideoWidth);
    mMeta->setInt32(kKeyHeight,      mVideoHeight);
    mMeta->setInt32(kKeyStride,      mVideoWidth);
    mMeta->setInt32(kKeySliceHeight, mVideoHeight);
    //mMeta->setInt32(kKeyFrameRate,   mVideoFrameRate);
    return err;
}

FakeCameraSource::FakeCameraSource(
    int32_t videoWidth,
    int32_t vidoeHeight,
    int32_t frameRate)
    : mVideoFrameRate(-1),
      mStarted(false),
      mStartTimeUs(0),
      mNumFrames(0){
    LOGV("FakeCameraSource()");
    mVideoWidth  = 176;
    mVideoHeight = 144;
    mVideoFrameRate = frameRate;

	char propBuf[PROPERTY_VALUE_MAX];  
        property_get("debug.videophone", propBuf, "");	
	LOGI("property_get: %s.", propBuf);
	if (!strcmp(propBuf, "1")) {
		s_bDebug = true;
	} else {
		s_bDebug = false;
	}
    s_bDebug = true;

    mInitCheck = init();
}

FakeCameraSource::~FakeCameraSource() {
    LOGV("~FakeCameraSource()");
    if (mStarted) {
        stop();
    } else if (mInitCheck == OK) {
        // Camera is initialized but because start() is never called,
        // the lock on Camera is never released(). This makes sure
        // Camera's lock is released in this case.
    }
}

status_t FakeCameraSource::start(MetaData *meta) {
    LOGV("start");
    CHECK(!mStarted);
    if (mInitCheck != OK) {
        LOGE("FakeCameraSource is not initialized yet");
        return mInitCheck;
    }

    mStartTimeUs = 0;
    int64_t startTimeUs;
    if (meta && meta->findInt64(kKeyTime, &startTimeUs)) {
        mStartTimeUs = startTimeUs;
    }

    mStarted = true;
    return OK;
}

status_t FakeCameraSource::stop() {
    LOGD("stop: E");
    Mutex::Autolock autoLock(mLock);
    mStarted = false;

    LOGD("stop: X");
    return OK;
}

void FakeCameraSource::signalBufferReturned(MediaBuffer *buffer) {
    DEBUG_LOGD("signalBufferReturned: %p", buffer->data());
    Mutex::Autolock autoLock(mLock);
    buffer->setObserver(0);
    buffer->release();
    return;
}

static void writeToFile(const char* fn, void* data, int len){
    static int iWrite = 0;
    if (iWrite >= 2)
        return;
	LOGE("fhy: writeToFile(%s) len: %d", fn, len);	
    FILE* file = fopen(fn,"ab");
	if (file == NULL){
	    LOGE("fhy: fopen(%s) fail: %d", fn, file);	
        return;
	}
	int wLen = fwrite(data, 1, len, file);
	LOGE("fhy: fwrite(), wLen: %d", wLen);	
	fclose(file);
    iWrite++;
}
status_t FakeCameraSource::read(
        MediaBuffer **buffer, const ReadOptions *options) {
    DEBUG_LOGD("read");

    *buffer = NULL;

    int64_t seekTimeUs;
    ReadOptions::SeekMode mode;
    if (options && options->getSeekTo(&seekTimeUs, &mode)) {
        return ERROR_UNSUPPORTED;
    }
    //decode the picture and copy picture data decoded to swap buffer
    SkBitmap bp;
    SkImageDecoder::Format fmt;	
    //uint32_t orientation = getOrientationAttributes(FAKE_IMAGE);
    uint32_t degree = 0;
    /*LOGI("wxz:stub camera : orientation: %d.", orientation);
    if(0 != orientation){
            switch(orientation){
                    case 3:
                            degree = 180;
                            break;
                    case 6:
                            degree = 90;
                            break;
                    case 8:
                            degree = 270;
                            break;
            }
    }*/
	char propBuf[PROPERTY_VALUE_MAX];  
    property_get("gsm.camera.picture", propBuf, "");
	DEBUG_LOGD("property_get: %s.", propBuf);
    bool result = SkImageDecoder::DecodeFile(propBuf, &bp,SkBitmap::kRGB_565_Config, SkImageDecoder::kDecodePixels_Mode, &fmt);
    if(!result){
        LOGE("decoder file fail!");
        return ERROR_UNSUPPORTED;
    }
    if(fmt!= SkImageDecoder::kJPEG_Format){
        LOGE("decoder file not jpeg!");
        return ERROR_UNSUPPORTED;
    }
    DEBUG_LOGD("width %d,height %d,rowBytesAsPixels %d,config %d,bytesPerPixel %d",bp.width(),bp.height(),bp.rowBytesAsPixels(),bp.config(),bp.bytesPerPixel());

	sp<MemoryHeapBase> masterHeap;
	sp<MemoryHeapPmem>  SwapHeap;
	int phys_addr=0;
	uint8_t *virt_addr = NULL;
    int width=0, height=0;
    
    width = bp.width();
    height = bp.height();	
    int mem_size = width * height * 1.5;
    masterHeap = new MemoryHeapBase(PMEM_DEV,mem_size,MemoryHeapBase::NO_CACHING);
    SwapHeap = new MemoryHeapPmem(masterHeap,MemoryHeapBase::NO_CACHING);
    if (SwapHeap->getHeapID() >= 0) {
        DEBUG_LOGD("(SwapHeap->getHeapID() >= 0)");
        SwapHeap->slap();
        masterHeap.clear();  
        struct pmem_region region;
        int fd_pmem = 0;
        fd_pmem = SwapHeap->getHeapID();	
        ::ioctl(fd_pmem,PMEM_GET_PHYS,&region);
        phys_addr = region.offset;
        virt_addr = (uint8_t *)SwapHeap->getBase();
        DEBUG_LOGD("phys_addr: %d, virt_addr: %p", phys_addr, virt_addr);
    }
    //writeToFile("/data/fhy_in", pBufferIn, 176*144*2);
    RGB565toYUV420((unsigned char *)bp.getPixels(), virt_addr, 144, 176);
    //writeToFile("/data/fhy_out", pBufferOut, 176*144*2);
    //RGBA8888toYUV420((unsigned char *)bp.getPixels(), pBufferOut, 144, 176);    
    
    *buffer = new MediaBuffer((void*)virt_addr, 176*144*1.5); 
    (*buffer)->setObserver(this);
    (*buffer)->add_ref();
    (*buffer)->meta_data()->setInt32(kKeyWidth, bp.width());
    (*buffer)->meta_data()->setInt32(kKeyHeight, bp.height());
    (*buffer)->meta_data()->setInt32(kKeyPlatformPrivate, phys_addr);
    if (mStartTimeUs == 0) {
        mStartTimeUs = systemTime();
        mLastFrameTimestampUs = mStartTimeUs;
        LOGI("first frame");
    } else {
        nsecs_t now = systemTime();
        mLastFrameTimestampUs = now + milliseconds_to_nanoseconds(100);
        if (now < mLastFrameTimestampUs) {
            if (ns2us(mLastFrameTimestampUs - now) > 0) {
                usleep(ns2us(mLastFrameTimestampUs - now));
            }
        }
        DEBUG_LOGD("mNumFrames: %d, interval: %ld", mNumFrames, ns2ms(mLastFrameTimestampUs - now));
    }
    DEBUG_LOGD("mLastFrameTimestampUs: %ld", ns2ms(mLastFrameTimestampUs));
    (*buffer)->meta_data()->setInt64(kKeyTime, mLastFrameTimestampUs);
    mNumFrames++;
    return OK;
}

sp<MetaData> FakeCameraSource::getFormat() {
    //LOGI("getFormat");
    mMeta->setInt32(kKeyIsVideoCall, true);
    return mMeta;
}

void FakeCameraSource::DeathNotifier::binderDied(const wp<IBinder>& who) {
    LOGI("Camera recording proxy died");
}

}  // namespace android
