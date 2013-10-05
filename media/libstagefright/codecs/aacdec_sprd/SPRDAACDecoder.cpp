/*
 * Copyright (C) 2011 The Android Open Source Project
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

//#define LOG_NDEBUG 0
#define LOG_TAG "SPRDAACDecoder"
#include <utils/Log.h>

#include "SPRDAACDecoder.h"

#include "aac_dec_api.h"

#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/hexdump.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>

namespace android {

template<class T>
static void InitOMXParams(T *params) {
    params->nSize = sizeof(T);
    params->nVersion.s.nVersionMajor = 1;
    params->nVersion.s.nVersionMinor = 0;
    params->nVersion.s.nRevision = 0;
    params->nVersion.s.nStep = 0;
}

SPRDAACDecoder::SPRDAACDecoder(
        const char *name,
        const OMX_CALLBACKTYPE *callbacks,
        OMX_PTR appData,
        OMX_COMPONENTTYPE **component)
    : SprdSimpleOMXComponent(name, callbacks, appData, component),
       mIsADTS(false),
       mDecoderBuf(NULL),
       mSamplingRate(44100),
      mPcm_out_l(NULL),
      mPcm_out_r(NULL),
      mInputBufferCount(0),
      mAnchorTimeUs(0),
      mNumSamplesOutput(0),
      mSignalledError(false),
      mOutputPortSettingsChange(NONE) {
    initPorts();
    CHECK_EQ(initDecoder(), (status_t)OK);
}

SPRDAACDecoder::~SPRDAACDecoder() {
    AAC_MemoryFree(&mDecoderBuf);
    mDecoderBuf = NULL;

    delete []mPcm_out_l;
    mPcm_out_l = NULL;
    delete []mPcm_out_r;
    mPcm_out_r = NULL;
}

void SPRDAACDecoder::initPorts() {
    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);

    def.nPortIndex = 0;
    def.eDir = OMX_DirInput;
    def.nBufferCountMin = kNumInputBuffers;
    def.nBufferCountActual = def.nBufferCountMin;
    def.nBufferSize = 8192;
    def.bEnabled = OMX_TRUE;
    def.bPopulated = OMX_FALSE;
    def.eDomain = OMX_PortDomainAudio;
    def.bBuffersContiguous = OMX_FALSE;
    def.nBufferAlignment = 1;

    def.format.audio.cMIMEType = const_cast<char *>(MEDIA_MIMETYPE_AUDIO_AAC);
    def.format.audio.pNativeRender = NULL;
    def.format.audio.bFlagErrorConcealment = OMX_FALSE;
    def.format.audio.eEncoding = OMX_AUDIO_CodingAAC;

    addPort(def);

    def.nPortIndex = 1;
    def.eDir = OMX_DirOutput;
    def.nBufferCountMin = kNumOutputBuffers;
    def.nBufferCountActual = def.nBufferCountMin;
    def.nBufferSize = 8192;
    def.bEnabled = OMX_TRUE;
    def.bPopulated = OMX_FALSE;
    def.eDomain = OMX_PortDomainAudio;
    def.bBuffersContiguous = OMX_FALSE;
    def.nBufferAlignment = 2;

    def.format.audio.cMIMEType = const_cast<char *>("audio/raw");
    def.format.audio.pNativeRender = NULL;
    def.format.audio.bFlagErrorConcealment = OMX_FALSE;
    def.format.audio.eEncoding = OMX_AUDIO_CodingPCM;

    addPort(def);
}

status_t SPRDAACDecoder::initDecoder() {
    if(AAC_MemoryAlloc(&mDecoderBuf))
    {
        ALOGE("Failed to initialize AAC audio decoder");
        return UNKNOWN_ERROR;
    }

    mPcm_out_l = new uint16_t[AAC_PCM_OUT_SIZE*2];
    mPcm_out_r = new uint16_t[AAC_PCM_OUT_SIZE*2];

    return OK;
}

OMX_ERRORTYPE SPRDAACDecoder::internalGetParameter(
        OMX_INDEXTYPE index, OMX_PTR params) {
    switch (index) {
        case OMX_IndexParamAudioAac:
        {
            OMX_AUDIO_PARAM_AACPROFILETYPE *aacParams =
                (OMX_AUDIO_PARAM_AACPROFILETYPE *)params;

            if (aacParams->nPortIndex != 0) {
                return OMX_ErrorUndefined;
            }

            aacParams->nBitRate = 0;
            aacParams->nAudioBandWidth = 0;
            aacParams->nAACtools = 0;
            aacParams->nAACERtools = 0;
            aacParams->eAACProfile = OMX_AUDIO_AACObjectMain;

            aacParams->eAACStreamFormat =
                mIsADTS
                    ? OMX_AUDIO_AACStreamFormatMP4LATM
                    : OMX_AUDIO_AACStreamFormatMP4FF;

            aacParams->eChannelMode = OMX_AUDIO_ChannelModeStereo;

            if (!isConfigured()) {
                aacParams->nChannels = 1;
                aacParams->nSampleRate = 44100;
                aacParams->nFrameLength = 0;
            } else {
                aacParams->nChannels = 2;
                aacParams->nSampleRate = mSamplingRate;
                //cParams->nFrameLength = mConfig->frameLength;
            }

            return OMX_ErrorNone;
        }

        case OMX_IndexParamAudioPcm:
        {
            OMX_AUDIO_PARAM_PCMMODETYPE *pcmParams =
                (OMX_AUDIO_PARAM_PCMMODETYPE *)params;

            if (pcmParams->nPortIndex != 1) {
                return OMX_ErrorUndefined;
            }

            pcmParams->eNumData = OMX_NumericalDataSigned;
            pcmParams->eEndian = OMX_EndianBig;
            pcmParams->bInterleaved = OMX_TRUE;
            pcmParams->nBitPerSample = 16;
            pcmParams->ePCMMode = OMX_AUDIO_PCMModeLinear;
            pcmParams->eChannelMapping[0] = OMX_AUDIO_ChannelLF;
            pcmParams->eChannelMapping[1] = OMX_AUDIO_ChannelRF;

            if (!isConfigured()) {
                pcmParams->nChannels = 1;
                pcmParams->nSamplingRate = 44100;
            } else {
                pcmParams->nChannels = 2;
                pcmParams->nSamplingRate = mSamplingRate;
            }

            return OMX_ErrorNone;
        }

        default:
            return SprdSimpleOMXComponent::internalGetParameter(index, params);
    }
}

OMX_ERRORTYPE SPRDAACDecoder::internalSetParameter(
        OMX_INDEXTYPE index, const OMX_PTR params) {
    switch (index) {
        case OMX_IndexParamStandardComponentRole:
        {
            const OMX_PARAM_COMPONENTROLETYPE *roleParams =
                (const OMX_PARAM_COMPONENTROLETYPE *)params;

            if (strncmp((const char *)roleParams->cRole,
                        "audio_decoder.aac",
                        OMX_MAX_STRINGNAME_SIZE - 1)) {
                return OMX_ErrorUndefined;
            }

            return OMX_ErrorNone;
        }

        case OMX_IndexParamAudioAac:
        {
            const OMX_AUDIO_PARAM_AACPROFILETYPE *aacParams =
                (const OMX_AUDIO_PARAM_AACPROFILETYPE *)params;

            if (aacParams->nPortIndex != 0) {
                return OMX_ErrorUndefined;
            }

            mSamplingRate = aacParams->nSampleRate;

            ALOGI("sampleRate : %d", mSamplingRate);

            if (aacParams->eAACStreamFormat == OMX_AUDIO_AACStreamFormatMP4FF) {
                mIsADTS = false;
            } else if (aacParams->eAACStreamFormat
                        == OMX_AUDIO_AACStreamFormatMP4ADTS) {
                mIsADTS = true;
            } else {
                return OMX_ErrorUndefined;
            }

            return OMX_ErrorNone;
        }

        case OMX_IndexParamAudioPcm:
        {
            const OMX_AUDIO_PARAM_PCMMODETYPE *pcmParams =
                (OMX_AUDIO_PARAM_PCMMODETYPE *)params;

            if (pcmParams->nPortIndex != 1) {
                return OMX_ErrorUndefined;
            }

            return OMX_ErrorNone;
        }

        default:
            return SprdSimpleOMXComponent::internalSetParameter(index, params);
    }
}

bool SPRDAACDecoder::isConfigured() const {
    return mInputBufferCount > 0;
}

void SPRDAACDecoder::onQueueFilled(OMX_U32 portIndex) {
    if (mSignalledError || mOutputPortSettingsChange != NONE) {
        return;
    }

    List<BufferInfo *> &inQueue = getPortQueue(0);
    List<BufferInfo *> &outQueue = getPortQueue(1);

    if (portIndex == 0 && mInputBufferCount == 0) {
        ++mInputBufferCount;

        BufferInfo *info = *inQueue.begin();
        OMX_BUFFERHEADERTYPE *header = info->mHeader;
        int16_t initRet;
        uint8_t *pInputBuffer =  header->pBuffer + header->nOffset;
        uint32_t inputBufferCurrentLength =  header->nFilledLen;

        if (header->nFlags & OMX_BUFFERFLAG_CODECCONFIG) {

        }

        initRet = AAC_DecInit((int8_t *)pInputBuffer,inputBufferCurrentLength,mSamplingRate,0,mDecoderBuf);
        if(initRet){
            ALOGW("AAC decoder init returned error %d", initRet);
            mSignalledError = true;
            notify(OMX_EventError, OMX_ErrorUndefined, initRet, NULL);
            return;
	}

        // Check on the sampling rate to see whether it is changed.
        int32_t sampleRate = AAC_RetrieveSampleRate(mDecoderBuf);
        if (mSamplingRate != sampleRate) {
            ALOGI("aac sampleRate is %d, but now is %d", mSamplingRate, sampleRate);
            mSamplingRate = sampleRate;
            initRet = AAC_DecInit((int8_t *)pInputBuffer,inputBufferCurrentLength,mSamplingRate,0,mDecoderBuf);
            if(initRet){
                ALOGW("AAC decoder init returned error %d", initRet);
                mSignalledError = true;
                notify(OMX_EventError, OMX_ErrorUndefined, initRet, NULL);
                return;
            }
        }

        inQueue.erase(inQueue.begin());
        info->mOwnedByUs = false;
        notifyEmptyBufferDone(header);

        notify(OMX_EventPortSettingsChanged, 1, 0, NULL);
        mOutputPortSettingsChange = AWAITING_DISABLED;
        return;
    }

    while (!inQueue.empty() && !outQueue.empty()) {
        BufferInfo *inInfo = *inQueue.begin();
        OMX_BUFFERHEADERTYPE *inHeader = inInfo->mHeader;

        BufferInfo *outInfo = *outQueue.begin();
        OMX_BUFFERHEADERTYPE *outHeader = outInfo->mHeader;

        if (inHeader->nFlags & OMX_BUFFERFLAG_EOS) {
            inQueue.erase(inQueue.begin());
            inInfo->mOwnedByUs = false;
            notifyEmptyBufferDone(inHeader);

            outHeader->nFilledLen = 0;
            outHeader->nFlags = OMX_BUFFERFLAG_EOS;

            outQueue.erase(outQueue.begin());
            outInfo->mOwnedByUs = false;
            notifyFillBufferDone(outHeader);
            return;
        }

        if (inHeader->nOffset == 0) {
            mAnchorTimeUs = inHeader->nTimeStamp;
            mNumSamplesOutput = 0;
        }

        uint8_t *pInputBuffer;
        uint32_t inputBufferCurrentLength;

        if (mIsADTS) {
            // skip 30 bits, aac_frame_length follows.

            const uint8_t *adtsHeader = inHeader->pBuffer + inHeader->nOffset;

            bool signalError = false;
            if (inHeader->nFilledLen < 7) {
                ALOGE("Audio data too short to contain even the ADTS header. "
                      "Got %ld bytes.", inHeader->nFilledLen);
                hexdump(adtsHeader, inHeader->nFilledLen);
                signalError = true;
            } else {
                bool protectionAbsent = (adtsHeader[1] & 1);

                unsigned aac_frame_length =
                    ((adtsHeader[3] & 3) << 11)
                    | (adtsHeader[4] << 3)
                    | (adtsHeader[5] >> 5);

                if (inHeader->nFilledLen < aac_frame_length) {
                    ALOGE("Not enough audio data for the complete frame. "
                          "Got %ld bytes, frame size according to the ADTS "
                          "header is %u bytes.",
                          inHeader->nFilledLen, aac_frame_length);
                    hexdump(adtsHeader, inHeader->nFilledLen);
                    signalError = true;
                } else {
                    size_t adtsHeaderSize = (protectionAbsent ? 7 : 9);

                    pInputBuffer = (uint8_t *)adtsHeader + adtsHeaderSize;
                    inputBufferCurrentLength = aac_frame_length - adtsHeaderSize;

                    inHeader->nOffset += adtsHeaderSize;
                    inHeader->nFilledLen -= adtsHeaderSize;
                }
            }

            if (signalError) {
                mSignalledError = true;

                notify(OMX_EventError,
                       OMX_ErrorStreamCorrupt,
                       ERROR_MALFORMED,
                       NULL);

                return;
            }
        } else {
            pInputBuffer =  inHeader->pBuffer + inHeader->nOffset;
            inputBufferCurrentLength =  inHeader->nFilledLen;
        }

        uint16_t    frm_pcm_len;
        int16_t decoderRet = 0;
       int16_t decodedBytes = 0;
       if(inputBufferCurrentLength>0) {
            //ALOGI("AACDecoder %d,%d:0x%x,0x%x,0x%x,0x%x,0x%x,0x%x,0x%x,0x%x\n",(int)mNumDecodedBuffers,inputBufferCurrentLength,((uint8_t *)mStreamBuf)[0],((uint8_t *)mStreamBuf)[1],((uint8_t *)mStreamBuf)[2],((uint8_t *)mStreamBuf)[3],((uint8_t *)mStreamBuf)[4],((uint8_t *)mStreamBuf)[5],((uint8_t *)mStreamBuf)[6],((uint8_t *)mStreamBuf)[7]);

            decoderRet = AAC_FrameDecode(pInputBuffer,inputBufferCurrentLength,mPcm_out_l,mPcm_out_r,&frm_pcm_len,mDecoderBuf,1, &decodedBytes);
        }  else {
            ALOGW("AAC decoder stream buf size error %d",inputBufferCurrentLength);
            decoderRet = 2;
	   frm_pcm_len = 2048;
        }

        if((decoderRet!=0)&&(decoderRet!=1)){ //decoderRet == 2 decode error
            ALOGW("AAC decoder returned error %d, substituting silence", decoderRet);
            // Discard input buffer.
            inHeader->nFilledLen = 0;
            // fall through
            } else if ((decoderRet==0)&&(decodedBytes <= inputBufferCurrentLength)) {
                inHeader->nFilledLen -= decodedBytes;
                inHeader->nOffset += decodedBytes;
         } else if(decoderRet == 0) {
            // Discard input buffer.
            inHeader->nFilledLen = 0;
        }

         size_t numOutBytes =  frm_pcm_len * sizeof(int16_t) *2;
         uint16_t * pOutputBuffer = reinterpret_cast<uint16_t *>(outHeader->pBuffer + outHeader->nOffset);

         for(int i=0;i<frm_pcm_len;i++){
                 if(decoderRet==2){
                     memset(pOutputBuffer, 0, numOutBytes);
                 }else{
                     pOutputBuffer[2*i] = mPcm_out_l[i];
                     pOutputBuffer[2*i+1] = mPcm_out_r[i];
                 }
             }

        if ( (decoderRet == 0) || (mNumSamplesOutput > 0)) {
            // We'll only output data if we successfully decoded it or
            // we've previously decoded valid data, in the latter case
            // (decode failed) we'll output a silent frame.
            outHeader->nFilledLen = numOutBytes;
            outHeader->nFlags = 0;

            outHeader->nTimeStamp =
                mAnchorTimeUs
                    + (mNumSamplesOutput * 1000000ll) / mSamplingRate;

            mNumSamplesOutput += frm_pcm_len;

            outInfo->mOwnedByUs = false;
            outQueue.erase(outQueue.begin());
            outInfo = NULL;
            notifyFillBufferDone(outHeader);
            outHeader = NULL;
        }

        if (inHeader->nFilledLen == 0) {
            inInfo->mOwnedByUs = false;
            inQueue.erase(inQueue.begin());
            inInfo = NULL;
            notifyEmptyBufferDone(inHeader);
            inHeader = NULL;
        }

        if (decoderRet == 0) {
            ++mInputBufferCount;
        }
    }
}

void SPRDAACDecoder::onPortFlushCompleted(OMX_U32 portIndex) {
    if (portIndex == 0) {
        // Make sure that the next buffer output does not still
        // depend on fragments from the last one decoded.
         AAC_DecStreamBufferUpdate(1,mDecoderBuf);
    }
}

void SPRDAACDecoder::onPortEnableCompleted(OMX_U32 portIndex, bool enabled) {
    if (portIndex != 1) {
        return;
    }

    switch (mOutputPortSettingsChange) {
        case NONE:
            break;

        case AWAITING_DISABLED:
        {
            CHECK(!enabled);
            mOutputPortSettingsChange = AWAITING_ENABLED;
            break;
        }

        default:
        {
            CHECK_EQ((int)mOutputPortSettingsChange, (int)AWAITING_ENABLED);
            CHECK(enabled);
            mOutputPortSettingsChange = NONE;
            break;
        }
    }
}

}  // namespace android

android::SprdOMXComponent *createSprdOMXComponent(
        const char *name, const OMX_CALLBACKTYPE *callbacks,
        OMX_PTR appData, OMX_COMPONENTTYPE **component) {
    return new android::SPRDAACDecoder(name, callbacks, appData, component);
}
