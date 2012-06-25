/*
 * Copyright (C) 2007 The Android Open Source Project
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

#include <stdlib.h>
#include <stdint.h>
#include <math.h>
#include <sys/types.h>

#include <utils/Errors.h>
#include <utils/Log.h>
#include <utils/StopWatch.h>

#include <ui/GraphicBuffer.h>
#include <ui/PixelFormat.h>
#include <ui/FramebufferNativeWindow.h>
#include <ui/Rect.h>
#include <ui/Region.h>

#include <hardware/copybit.h>

#include "LayerBuffer.h"
#include "SurfaceFlinger.h"
#include "DisplayHardware/DisplayHardware.h"

#include "mali_wrapper.h"
#include "egl_impl.h"

namespace android {

// ---------------------------------------------------------------------------

gralloc_module_t const* LayerBuffer::sGrallocModule = 0;

// ---------------------------------------------------------------------------

LayerBuffer::LayerBuffer(SurfaceFlinger* flinger, DisplayID display,
        const sp<Client>& client)
    : LayerBaseClient(flinger, display, client),
      mNeedsBlending(false), mBlitEngine(0)
{
}

LayerBuffer::~LayerBuffer()
{
    if (mBlitEngine) {
        copybit_close(mBlitEngine);
    }
}

void LayerBuffer::onFirstRef()
{
    LayerBaseClient::onFirstRef();
    mSurface = new SurfaceLayerBuffer(mFlinger, this);

    hw_module_t const* module = (hw_module_t const*)sGrallocModule;
    if (!module) {
        // NOTE: technically there is a race here, but it shouldn't
        // cause any problem since hw_get_module() always returns
        // the same value.
        if (hw_get_module(GRALLOC_HARDWARE_MODULE_ID, &module) == 0) {
            sGrallocModule = (gralloc_module_t const *)module;
        }
    }

    if (hw_get_module(COPYBIT_HARDWARE_MODULE_ID, &module) == 0) {
        copybit_open(module, &mBlitEngine);
    }
}

sp<LayerBaseClient::Surface> LayerBuffer::createSurface() const
{
    return mSurface;
}

status_t LayerBuffer::ditch()
{
    mSurface.clear();
    return NO_ERROR;
}

bool LayerBuffer::needsBlending() const {
    return mNeedsBlending;
}

void LayerBuffer::setNeedsBlending(bool blending) {
    mNeedsBlending = blending;
}

void LayerBuffer::finishPageFlip()
{
    sp<Source> source(getSource());
    if (LIKELY(source != 0)) {
        source->finishPageFlip();
    }
}

void LayerBuffer::postBuffer(ssize_t offset)
{
    sp<Source> source(getSource());
    if (source != 0)
        source->postBuffer(offset);
}

void LayerBuffer::unregisterBuffers()
{
    sp<Source> source(clearSource());
    if (source != 0)
        source->unregisterBuffers();
}

uint32_t LayerBuffer::doTransaction(uint32_t flags)
{
    sp<Source> source(getSource());
    if (source != 0)
        source->onTransaction(flags);
    uint32_t res = LayerBase::doTransaction(flags);
    // we always want filtering for these surfaces
    mNeedsFiltering = !(mFlags & DisplayHardware::SLOW_CONFIG);
    return res;
}

void LayerBuffer::unlockPageFlip(const Transform& planeTransform,
        Region& outDirtyRegion)
{
    // this code-path must be as tight as possible, it's called each time
    // the screen is composited.
    sp<Source> source(getSource());
    if (source != 0)
        source->onVisibilityResolved(planeTransform);
    LayerBase::unlockPageFlip(planeTransform, outDirtyRegion);
}

void LayerBuffer::validateVisibility(const Transform& globalTransform)
{
    sp<Source> source(getSource());
    if (source != 0)
        source->onvalidateVisibility(globalTransform);
    LayerBase::validateVisibility(globalTransform);
}

void LayerBuffer::drawForSreenShot() const
{
    const DisplayHardware& hw(graphicPlane(0).displayHardware());
    clearWithOpenGL( Region(hw.bounds()) );
}

void LayerBuffer::onDraw(const Region& clip) const
{
    sp<Source> source(getSource());
    if (LIKELY(source != 0)) {
        source->onDraw(clip);
    } else {
        clearWithOpenGL(clip);
    }
}

void LayerBuffer::serverDestroy()
{
    sp<Source> source(clearSource());
    if (source != 0) {
        source->destroy();
    }
}

/**
 * This creates a "buffer" source for this surface
 */
status_t LayerBuffer::registerBuffers(const ISurface::BufferHeap& buffers)
{
    Mutex::Autolock _l(mLock);
    if (mSource != 0)
        return INVALID_OPERATION;

    sp<BufferSource> source = new BufferSource(*this, buffers);

    status_t result = source->getStatus();
    if (result == NO_ERROR) {
        mSource = source;
    }
    return result;
}

/**
 * This creates an "overlay" source for this surface
 */
sp<OverlayRef> LayerBuffer::createOverlay(uint32_t w, uint32_t h, int32_t f,
        int32_t orientation)
{
    sp<OverlayRef> result;
    Mutex::Autolock _l(mLock);
    if (mSource != 0)
        return result;

    sp<OverlaySource> source = new OverlaySource(*this, &result, w, h, f, orientation);
    if (result != 0) {
        mSource = source;
    }
    return result;
}

sp<LayerBuffer::Source> LayerBuffer::getSource() const {
    Mutex::Autolock _l(mLock);
    return mSource;
}

sp<LayerBuffer::Source> LayerBuffer::clearSource() {
    sp<Source> source;
    Mutex::Autolock _l(mLock);
    source = mSource;
    mSource.clear();
    return source;
}

// ============================================================================
// LayerBuffer::SurfaceLayerBuffer
// ============================================================================

LayerBuffer::SurfaceLayerBuffer::SurfaceLayerBuffer(
        const sp<SurfaceFlinger>& flinger, const sp<LayerBuffer>& owner)
    : LayerBaseClient::Surface(flinger, owner->getIdentity(), owner)
{
}

LayerBuffer::SurfaceLayerBuffer::~SurfaceLayerBuffer()
{
    unregisterBuffers();
}

status_t LayerBuffer::SurfaceLayerBuffer::registerBuffers(
        const ISurface::BufferHeap& buffers)
{
    sp<LayerBuffer> owner(getOwner());
    if (owner != 0)
        return owner->registerBuffers(buffers);
    return NO_INIT;
}

void LayerBuffer::SurfaceLayerBuffer::postBuffer(ssize_t offset)
{
    sp<LayerBuffer> owner(getOwner());
    if (owner != 0)
        owner->postBuffer(offset);
}

void LayerBuffer::SurfaceLayerBuffer::unregisterBuffers()
{
    sp<LayerBuffer> owner(getOwner());
    if (owner != 0)
        owner->unregisterBuffers();
}

sp<OverlayRef> LayerBuffer::SurfaceLayerBuffer::createOverlay(
        uint32_t w, uint32_t h, int32_t format, int32_t orientation) {
    sp<OverlayRef> result;
    sp<LayerBuffer> owner(getOwner());
    if (owner != 0)
        result = owner->createOverlay(w, h, format, orientation);
    return result;
}

// ============================================================================
// LayerBuffer::Buffer
// ============================================================================

LayerBuffer::Buffer::Buffer(const ISurface::BufferHeap& buffers,
        ssize_t offset, size_t bufferSize)
    : mBufferHeap(buffers), mSupportsCopybit(false)
{
    NativeBuffer& src(mNativeBuffer);
    switch(buffers.format)
    {
    case HAL_PIXEL_FORMAT_YCbCr_420_P_MP4V:
        src.crop.l = 16;
        src.crop.t = 16;
        src.crop.r = 16 + buffers.w;
        src.crop.b = 16 + buffers.h;

        src.img.w       = buffers.hor_stride;
        src.img.h       = buffers.ver_stride;
        src.img.format  = HAL_PIXEL_FORMAT_YCbCr_420_P;
        break;
    case HAL_PIXEL_FORMAT_YCrCb_420_P_H264:
        src.crop.l = 24;
        src.crop.t = 24;
        src.crop.r = 24 + buffers.w;
        src.crop.b = 24 + buffers.h;

        src.img.w       = buffers.hor_stride;
        src.img.h       = buffers.ver_stride;
        src.img.format  = HAL_PIXEL_FORMAT_YCrCb_420_P;
        break;
    default:
        src.crop.l = 0;
        src.crop.t = 0;
        src.crop.r = buffers.w;
        src.crop.b = buffers.h;

        src.img.w       = buffers.hor_stride ?: buffers.w;
        src.img.h       = buffers.ver_stride ?: buffers.h;
        src.img.format  = buffers.format;
    }
    src.img.base    = (void*)(intptr_t(buffers.heap->base()) + offset);
    src.img.handle  = 0;

    gralloc_module_t const * module = LayerBuffer::getGrallocModule();
    if (module && module->perform) {
        int err;
        int size;
        PixelFormatInfo info;

        getPixelFormatInfo(buffers.format, &info);
        size = info.getScanlineSize(src.img.w) * src.img.h;
        err = module->perform(module,
                    GRALLOC_MODULE_PERFORM_CREATE_HANDLE_FROM_BUFFER,
                    buffers.heap->heapID(), size,
                    offset, buffers.heap->base(),
                    &src.img.handle);
        mSupportsCopybit = (err == NO_ERROR);
    }
 }

LayerBuffer::Buffer::~Buffer()
{
    NativeBuffer& src(mNativeBuffer);

    if (src.img.handle) {
        gralloc_module_t const * module = LayerBuffer::getGrallocModule();
        module->perform(module,
                        GRALLOC_MODULE_PERFORM_FREE_HANDLE,
                        &src.img.handle);
        native_handle_delete(src.img.handle);
    }
}

// ============================================================================
// LayerBuffer::Source
// LayerBuffer::BufferSource
// LayerBuffer::OverlaySource
// ============================================================================

LayerBuffer::Source::Source(LayerBuffer& layer)
    : mLayer(layer)
{
}
LayerBuffer::Source::~Source() {
}
void LayerBuffer::Source::onDraw(const Region& clip) const {
}
void LayerBuffer::Source::onTransaction(uint32_t flags) {
}
void LayerBuffer::Source::onVisibilityResolved(
        const Transform& planeTransform) {
}
void LayerBuffer::Source::postBuffer(ssize_t offset) {
}
void LayerBuffer::Source::unregisterBuffers() {
}
void LayerBuffer::Source::finishPageFlip() {
}

// ---------------------------------------------------------------------------

LayerBuffer::BufferSource::BufferSource(LayerBuffer& layer,
        const ISurface::BufferHeap& buffers)
    : Source(layer), mStatus(NO_ERROR), mBufferSize(0),mInComposing(false),mIsSync(false),
      mUseEGLImageDirectly(true)
{
    if((buffers.transform>>24)==0x12){//jgdu push buffer sync
        mIsSync = true;
    }
    LOGI("mIsSync is %d @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@",mIsSync);

    if (buffers.heap == NULL) {
        // this is allowed, but in this case, it is illegal to receive
        // postBuffer(). The surface just erases the framebuffer with
        // fully transparent pixels.
        mBufferHeap = buffers;
        mLayer.setNeedsBlending(false);
        return;
    }

    status_t err = (buffers.heap->heapID() >= 0) ? NO_ERROR : NO_INIT;
    if (err != NO_ERROR) {
        LOGE("LayerBuffer::BufferSource: invalid heap (%s)", strerror(err));
        mStatus = err;
        return;
    }

    PixelFormatInfo info;
    err = getPixelFormatInfo(buffers.format, &info);
    if (err != NO_ERROR) {
        LOGE("LayerBuffer::BufferSource: invalid format %d (%s)",
                buffers.format, strerror(err));
        mStatus = err;
        return;
    }

    if (buffers.hor_stride<0 || buffers.ver_stride<0) {
        LOGE("LayerBuffer::BufferSource: invalid parameters "
             "(w=%d, h=%d, xs=%d, ys=%d)",
             buffers.w, buffers.h, buffers.hor_stride, buffers.ver_stride);
        mStatus = BAD_VALUE;
        return;
    }

    mBufferHeap = buffers;
    mBufferHeap.transform = buffers.transform&0x00ffffff;//jgdu push buffer sync
    mLayer.setNeedsBlending((info.h_alpha - info.l_alpha) > 0);
    mBufferSize = info.getScanlineSize(buffers.hor_stride)*buffers.ver_stride;
    mLayer.forceVisibilityTransaction();
}

LayerBuffer::BufferSource::~BufferSource()
{
    class MessageDestroyTexture : public MessageBase {
        SurfaceFlinger* flinger;
        GLuint name;
    public:
        MessageDestroyTexture(
                SurfaceFlinger* flinger, GLuint name)
            : flinger(flinger), name(name) { }
        virtual bool handler() {
            glDeleteTextures(1, &name);
            return true;
        }
    };

    if (mTexture.name != -1U) {
        // GL textures can only be destroyed from the GL thread
        getFlinger()->mEventQueue.postMessage(
                new MessageDestroyTexture(getFlinger(), mTexture.name) );
    }
    if (mTexture.image != EGL_NO_IMAGE_KHR) {
        EGLDisplay dpy(getFlinger()->graphicPlane(0).getEGLDisplay());
        eglDestroyImageKHR(dpy, mTexture.image);
    }
}

void LayerBuffer::BufferSource::postBuffer(ssize_t offset)
{
    ISurface::BufferHeap buffers;
    { // scope for the lock
        Mutex::Autolock _l(mBufferSourceLock);
        buffers = mBufferHeap;
        if (buffers.heap != 0) {
            const size_t memorySize = buffers.heap->getSize();
            if ((size_t(offset) + mBufferSize) > memorySize) {
                LOGE("LayerBuffer::BufferSource::postBuffer() "
                     "invalid buffer (offset=%d, size=%d, heap-size=%d",
                     int(offset), int(mBufferSize), int(memorySize));
                return;
            }
        }
    }

    sp<Buffer> buffer;
    if (buffers.heap != 0) {
        buffer = new LayerBuffer::Buffer(buffers, offset, mBufferSize);
        if (buffer->getStatus() != NO_ERROR)
            buffer.clear();
        setBuffer(buffer);
    	if(mIsSync) {//jgdu push buffer sync
    	 	Mutex::Autolock autoLock(mBufLock);
    	 	mInComposing = true;
        }
        mLayer.invalidate();
    	if(mIsSync) {//jgdu push buffer sync
    		Mutex::Autolock autoLock(mBufLock);
    		int count = 0;
    		while(mInComposing) {
    			mBufCondition.waitRelative(mBufLock,100000000);
    			count++;
    			if(count>=2) {
    				mInComposing = false;
    				LOGI("video rendering timeout");
    				break;
    			}
    		}
    	}
    }
}

void LayerBuffer::BufferSource::unregisterBuffers()
{
    Mutex::Autolock _l(mBufferSourceLock);
    mBufferHeap.heap.clear();
    mBuffer.clear();
    mLayer.invalidate();
}

sp<LayerBuffer::Buffer> LayerBuffer::BufferSource::getBuffer() const
{
    Mutex::Autolock _l(mBufferSourceLock);
    return mBuffer;
}

void LayerBuffer::BufferSource::setBuffer(const sp<LayerBuffer::Buffer>& buffer)
{
    Mutex::Autolock _l(mBufferSourceLock);
    mBuffer = buffer;
}

void LayerBuffer::BufferSource::onDraw(const Region& clip) const
{
    sp<Buffer> ourBuffer(getBuffer());
    if (UNLIKELY(ourBuffer == 0))  {
        // nothing to do, we don't have a buffer
        mLayer.clearWithOpenGL(clip);
        return;
    }

    status_t err = NO_ERROR;
    NativeBuffer src(ourBuffer->getBuffer());
    const Rect transformedBounds(mLayer.getTransformedBounds());

    Rect crop;
    crop.left   = src.crop.l;
    crop.top    = src.crop.t;
    crop.right  = src.crop.r;
    crop.bottom = src.crop.b;

#if defined(EGL_ANDROID_image_native_buffer)
    if (GLExtensions::getInstance().haveDirectTexture()) {
        err = INVALID_OPERATION;
        if (ourBuffer->supportsCopybit()) {
            if (mUseEGLImageDirectly) {
                sp<GraphicBuffer> buffer = new GraphicBuffer(
                                    src.img.w, src.img.h, src.img.format,
                                    GraphicBuffer::USAGE_HW_TEXTURE,
                                    src.img.w, src.img.handle, false);
                EGLDisplay dpy(getFlinger()->graphicPlane(0).getEGLDisplay());
                mTexture.dirty = true;
                err = mTextureManager.initEglImage(&mTexture, dpy, buffer);
                if (err == NO_ERROR) {
                    uint32_t offset = 0;
                    bool base_need_bias = false;
                    gralloc_module_t const* module = LayerBuffer::getGrallocModule();
                    if (module && module->perform) {
                        status_t status = module->perform(module,
                                        GRALLOC_MODULE_GET_MALI_INTERNAL_BUF_OFF,
                                        &offset,
                                        src.img.handle);
                        if (!status) {
                            base_need_bias = !!offset;
                        }
                    }
                    switch(mBufferHeap.format)
                    {
                    case HAL_PIXEL_FORMAT_YCbCr_420_SP:
                        if (base_need_bias) {
                        // there are constraints on buffers used by the GPU
                            err = fixYUV420SP(&src.img, offset);
                        }
                        break;
                    case HAL_PIXEL_FORMAT_YCbCr_420_P_MP4V:
                        if (base_need_bias) {
                            err = fixYUV420P(&src.img, offset);
                        }
                        break;
                    case HAL_PIXEL_FORMAT_YCrCb_420_P_H264:
                        if (base_need_bias) {
                            err = fixYVU420P(&src.img, offset);
                        }
                        break;
                    }
                }
                if (err != NO_ERROR) {
                    mUseEGLImageDirectly = false;
                }
            }

            copybit_device_t* copybit = mLayer.mBlitEngine;
            if (copybit && err != NO_ERROR) {
                // create our EGLImageKHR the first time
                err = initTempBuffer();
                if (err == NO_ERROR) {
                    // NOTE: Assume the buffer is allocated with the proper USAGE flags
                    const NativeBuffer& dst(mTempBuffer);
                    region_iterator clip(Region(Rect(dst.crop.r, dst.crop.b)));
                    copybit->set_parameter(copybit, COPYBIT_TRANSFORM, 0);
                    copybit->set_parameter(copybit, COPYBIT_PLANE_ALPHA, 0xFF);
                    copybit->set_parameter(copybit, COPYBIT_DITHER, COPYBIT_ENABLE);
                    err = copybit->stretch(copybit, &dst.img, &src.img,
                            &dst.crop, &src.crop, &clip);
                    if (err != NO_ERROR) {
                        clearTempBufferImage();
                    }
                    else
                    {
                        crop.left   = dst.crop.l;
                        crop.top    = dst.crop.t;
                        crop.right  = dst.crop.r;
                        crop.bottom = dst.crop.b;
                    }
                }
            }
        }
    }
    else
#endif
    {
        err = INVALID_OPERATION;
    }

    if (err != NO_ERROR) {
        // slower fallback
        GGLSurface t;
        t.version = sizeof(GGLSurface);
        t.width  = src.crop.r;
        t.height = src.crop.b;
        t.stride = src.img.w;
        t.vstride= src.img.h;
        t.format = src.img.format;
        t.data = (GGLubyte*)src.img.base;
        const Region dirty(Rect(t.width, t.height));
        mTextureManager.loadTexture(&mTexture, dirty, t);
    }

    mLayer.setBufferCrop(crop);
    mLayer.setBufferTransform(mBufferHeap.transform);
    mLayer.drawWithOpenGL(clip, mTexture);

    if (!mUseEGLImageDirectly) {
        if(mIsSync){//jgdu push buffer sync
        	Mutex::Autolock autoLock(mBufLock);
        	mInComposing = false;
        	mBufCondition.signal();
        }
    }
}

status_t LayerBuffer::BufferSource::initTempBuffer() const
{
    // figure out the size we need now
    const ISurface::BufferHeap& buffers(mBufferHeap);
    uint32_t w = mLayer.mTransformedBounds.width();
    uint32_t h = mLayer.mTransformedBounds.height();
    if (buffers.w * h != buffers.h * w) {
        int t = w; w = h; h = t;
    }

    // we're in the copybit case, so make sure we can handle this blit
    // we don't have to keep the aspect ratio here
    copybit_device_t* copybit = mLayer.mBlitEngine;
    const int down = copybit->get(copybit, COPYBIT_MINIFICATION_LIMIT);
    const int up = copybit->get(copybit, COPYBIT_MAGNIFICATION_LIMIT);
    if (buffers.w > w*down)     w = buffers.w / down;
    else if (w > buffers.w*up)  w = buffers.w*up;
    if (buffers.h > h*down)     h = buffers.h / down;
    else if (h > buffers.h*up)  h = buffers.h*up;

    if (mTexture.image != EGL_NO_IMAGE_KHR) {
        // we have an EGLImage, make sure the needed size didn't change
        if (w!=mTexture.width || h!= mTexture.height) {
            // delete the EGLImage and texture
            clearTempBufferImage();
        } else {
            // we're good, we have an EGLImageKHR and it's (still) the
            // right size
            return NO_ERROR;
        }
    }

    // figure out if we need linear filtering
    if (buffers.w * h == buffers.h * w) {
        // same pixel area, don't use filtering
        mLayer.mNeedsFiltering = false;
    }

    // Allocate a temporary buffer and create the corresponding EGLImageKHR
    // once the EGLImage has been created we don't need the
    // graphic buffer reference anymore.
    sp<GraphicBuffer> buffer = new GraphicBuffer(
            w, h, HAL_PIXEL_FORMAT_RGB_565,
            GraphicBuffer::USAGE_HW_TEXTURE |
            GraphicBuffer::USAGE_HW_2D);

    status_t err = buffer->initCheck();
    if (err == NO_ERROR) {
        NativeBuffer& dst(mTempBuffer);
        dst.img.w = buffer->getStride();
        dst.img.h = h;
        dst.img.format = buffer->getPixelFormat();
        dst.img.handle = (native_handle_t *)buffer->handle;
        dst.img.base = 0;
        dst.crop.l = 0;
        dst.crop.t = 0;
        dst.crop.r = w;
        dst.crop.b = h;

        EGLDisplay dpy(getFlinger()->graphicPlane(0).getEGLDisplay());
        err = mTextureManager.initEglImage(&mTexture, dpy, buffer);
    }

    return err;
}

void LayerBuffer::BufferSource::clearTempBufferImage() const
{
    // delete the image
    EGLDisplay dpy(getFlinger()->graphicPlane(0).getEGLDisplay());
    eglDestroyImageKHR(dpy, mTexture.image);

    // and the associated texture (recreate a name)
    glDeleteTextures(1, &mTexture.name);
    Texture defaultTexture;
    mTexture = defaultTexture;
}

void LayerBuffer::BufferSource::finishPageFlip()
{
    if (mUseEGLImageDirectly) {
        if (mIsSync) {//jgdu push buffer sync
            // in fact, the compositeComplete handler already can sync it
            glFinish();
            Mutex::Autolock autoLock(mBufLock);
            mInComposing = false;
            mBufCondition.signal();
        }
    }
}

// here the caller mush be assure the native image is the image attached to
// mTexture.image
status_t LayerBuffer::BufferSource::fixYUV420SP(copybit_image_t const *img, uint32_t offset) const
{
    status_t status = INVALID_OPERATION;
    EGLImageKHR mali_egl_img;
    mali_wrapper mw;

    if (mTexture.image == EGL_NO_IMAGE_KHR) {
        return status;
    }
    mali_egl_img = egl_get_image_for_current_context(mTexture.image);
    status = mw.getStatus();
    if (NO_ERROR == status) {
        mali_wrapper::EGLImageMALI* internal_image = mw.lock_ptr(mali_egl_img);
        if (internal_image) {
            void *internal_mali_data = NULL;
            gralloc_module_t const * module = LayerBuffer::getGrallocModule();

            status = INVALID_OPERATION;
            if (module && module->perform) {
                status = module->perform(module, GRALLOC_MODULE_PERFORM_GET_MALI_DATA,
                            &internal_mali_data, img->handle);
                if (!status) {
                    if (offset) {
                        EGLint attrs_y[] = {
                            MALI_EGL_IMAGE_PLANE,   MALI_EGL_IMAGE_PLANE_Y,
                            MALI_EGL_IMAGE_MIPLEVEL,0,
                            EGL_NONE,               EGL_NONE
                        };
                        if (EGL_FALSE == mw.set_data(internal_image,
                                    attrs_y, offset, internal_mali_data))
                        {
                            LOGE("In fixYUV420SP %s failed, "
                                 "while set Y plane buffer "
                                 "error code 0x%08x\n",
                                 "mali_egl_image_set_data",
                                 mw.get_error());
                            status = -EINVAL;
                        }
                    }
                    if (!status) {
                        EGLint attrs_uv[] = {
                            MALI_EGL_IMAGE_PLANE,   MALI_EGL_IMAGE_PLANE_UV,
                            MALI_EGL_IMAGE_MIPLEVEL,0,
                            EGL_NONE,               EGL_NONE
                        };
                        EGLint uv_ofs = img->w * img->h;
                        if (EGL_FALSE == mw.set_data(internal_image,
                                    attrs_uv, uv_ofs+offset, internal_mali_data))
                        {
                            LOGE("In fixYUV420SP %s failed, "
                                 "while set UV plane buffer "
                                 "error code 0x%08x\n",
                                 "mali_egl_image_set_data",
                                 mw.get_error());
                            status = -EINVAL;
                        }
                    }
                }
            }
            if (EGL_FALSE == mw.unlock_ptr(mali_egl_img)) {
                LOGE("fixYUV420SP() failed, error code 0x%08x\n",
                    mw.get_error());
                status = INVALID_OPERATION;
            }
        }
    }
    else {
        LOGE("mali_wrapper open failed\n");
    }

    return status;
}


// here the caller mush be assure the native image is the image attached to
// mTexture.image
status_t LayerBuffer::BufferSource::fixYUV420P(copybit_image_t const *img, uint32_t offset) const
{
    status_t status = INVALID_OPERATION;
    EGLImageKHR mali_egl_img;
    mali_wrapper mw;
    if (mTexture.image == EGL_NO_IMAGE_KHR) {
        return status;
    }
    mali_egl_img = egl_get_image_for_current_context(mTexture.image);
    status = mw.getStatus();
    if (NO_ERROR == status) {
        mali_wrapper::EGLImageMALI* internal_image = mw.lock_ptr(mali_egl_img);
        if (internal_image) {
            void *internal_mali_data = NULL;
            gralloc_module_t const * module = LayerBuffer::getGrallocModule();

            status = INVALID_OPERATION;
            if (module && module->perform) {
                status = module->perform(module, GRALLOC_MODULE_PERFORM_GET_MALI_DATA,
                            &internal_mali_data, img->handle);
                if (!status) {
                    if (!status) {
                        EGLint attrs_y[] = {
                            MALI_EGL_IMAGE_PLANE,   MALI_EGL_IMAGE_PLANE_Y,
                            MALI_EGL_IMAGE_MIPLEVEL,0,
                            EGL_NONE,               EGL_NONE
                        };
                        EGLint y_ofs = 0;
                        if (EGL_FALSE == mw.set_data(internal_image,
                                    attrs_y, y_ofs+offset, internal_mali_data))
                        {
                            LOGE("In fixYUV420P %s failed, "
                                 "while set Y plane buffer "
                                 "error code 0x%08x\n",
                                 "mali_egl_image_set_data",
                                 mw.get_error());
                            status = -EINVAL;
                        }
                    }
                    if (!status) {
                        EGLint attrs_u[] = {
                            MALI_EGL_IMAGE_PLANE,   MALI_EGL_IMAGE_PLANE_U,
                            MALI_EGL_IMAGE_MIPLEVEL,0,
                            EGL_NONE,               EGL_NONE
                        };
                        EGLint u_ofs = img->w*img->h;
                        if (EGL_FALSE == mw.set_data(internal_image,
                                    attrs_u, u_ofs+offset, internal_mali_data))
                        {
                            LOGE("In fixYUV420P %s failed, "
                                 "while set U plane buffer "
                                 "error code 0x%08x\n",
                                 "mali_egl_image_set_data",
                                 mw.get_error());
                            status = -EINVAL;
                        }
                    }
                    if (!status) {
                        EGLint attrs_v[] = {
                            MALI_EGL_IMAGE_PLANE,   MALI_EGL_IMAGE_PLANE_V,
                            MALI_EGL_IMAGE_MIPLEVEL,0,
                            EGL_NONE,               EGL_NONE
                        };
                        EGLint v_ofs = img->w*img->h*5/4;
                        if (EGL_FALSE == mw.set_data(internal_image,
                                    attrs_v, v_ofs+offset, internal_mali_data))
                        {
                            LOGE("In fixYUV420P %s failed, "
                                 "while set V plane buffer "
                                 "error code 0x%08x\n",
                                 "mali_egl_image_set_data",
                                 mw.get_error());
                            status = -EINVAL;
                        }
                    }
                }
            }
            if (EGL_FALSE == mw.unlock_ptr(mali_egl_img)) {
                LOGE("fixYUV420P() failed, error code 0x%08x\n",
                    mw.get_error());
                status = INVALID_OPERATION;
            }
        }
    }
    else {
        LOGE("mali_wrapper open failed\n");
    }

    return status;
}


// here the caller mush be assure the native image is the image attached to
// mTexture.image
status_t LayerBuffer::BufferSource::fixYVU420P(copybit_image_t const *img, uint32_t offset) const
{
    status_t status = INVALID_OPERATION;
    EGLImageKHR mali_egl_img;
    mali_wrapper mw;
    if (mTexture.image == EGL_NO_IMAGE_KHR) {
        return status;
    }
    mali_egl_img = egl_get_image_for_current_context(mTexture.image);
    status = mw.getStatus();
    if (NO_ERROR == status) {
        mali_wrapper::EGLImageMALI* internal_image = mw.lock_ptr(mali_egl_img);
        if (internal_image) {
            void *internal_mali_data = NULL;
            gralloc_module_t const * module = LayerBuffer::getGrallocModule();

            status = INVALID_OPERATION;
            if (module && module->perform) {
                status = module->perform(module, GRALLOC_MODULE_PERFORM_GET_MALI_DATA,
                            &internal_mali_data, img->handle);
                if (!status) {
                    if (!status) {
                        EGLint attrs_y[] = {
                            MALI_EGL_IMAGE_PLANE,   MALI_EGL_IMAGE_PLANE_Y,
                            MALI_EGL_IMAGE_MIPLEVEL,0,
                            EGL_NONE,               EGL_NONE
                        };
                        EGLint y_ofs = 0;
                        if (EGL_FALSE == mw.set_data(internal_image,
                                    attrs_y, y_ofs+offset, internal_mali_data))
                        {
                            LOGE("In fixYUV420P %s failed, "
                                 "while set Y plane buffer "
                                 "error code 0x%08x\n",
                                 "mali_egl_image_set_data",
                                 mw.get_error());
                            status = -EINVAL;
                        }
                    }
                    if (!status) {
                        EGLint attrs_v[] = {
                            MALI_EGL_IMAGE_PLANE,   MALI_EGL_IMAGE_PLANE_V,
                            MALI_EGL_IMAGE_MIPLEVEL,0,
                            EGL_NONE,               EGL_NONE
                        };
                        EGLint v_ofs = img->w*img->h;
                        if (EGL_FALSE == mw.set_data(internal_image,
                                    attrs_v, v_ofs+offset, internal_mali_data))
                        {
                            LOGE("In fixYVU420P %s failed, "
                                 "while set V plane buffer "
                                 "error code 0x%08x\n",
                                 "mali_egl_image_set_data",
                                 mw.get_error());
                            status = -EINVAL;
                        }
                    }
                    if (!status) {
                        EGLint attrs_u[] = {
                            MALI_EGL_IMAGE_PLANE,   MALI_EGL_IMAGE_PLANE_U,
                            MALI_EGL_IMAGE_MIPLEVEL,0,
                            EGL_NONE,               EGL_NONE
                        };
                        EGLint u_ofs = img->w*img->h*5/4;
                        if (EGL_FALSE == mw.set_data(internal_image,
                                    attrs_u, u_ofs+offset, internal_mali_data))
                        {
                            LOGE("In fixYVU420P %s failed, "
                                 "while set U plane buffer "
                                 "error code 0x%08x\n",
                                 "mali_egl_image_set_data",
                                 mw.get_error());
                            status = -EINVAL;
                        }
                    }
                }
            }
            if (EGL_FALSE == mw.unlock_ptr(mali_egl_img)) {
                LOGE("fixYVU420P() failed, error code 0x%08x\n",
                    mw.get_error());
                status = INVALID_OPERATION;
            }
        }
    }
    else {
        LOGE("mali_wrapper open failed\n");
    }

    return status;
}

// ---------------------------------------------------------------------------

LayerBuffer::OverlaySource::OverlaySource(LayerBuffer& layer,
        sp<OverlayRef>* overlayRef,
        uint32_t w, uint32_t h, int32_t format, int32_t orientation)
    : Source(layer), mVisibilityChanged(false),
    mOverlay(0), mOverlayHandle(0), mOverlayDevice(0), mOrientation(orientation)
{
    overlay_control_device_t* overlay_dev = getFlinger()->getOverlayEngine();
    if (overlay_dev == NULL) {
        // overlays not supported
        return;
    }

    mOverlayDevice = overlay_dev;
    overlay_t* overlay = overlay_dev->createOverlay(overlay_dev, w, h, format);
    if (overlay == NULL) {
        // couldn't create the overlay (no memory? no more overlays?)
        return;
    }

    // enable dithering...
    overlay_dev->setParameter(overlay_dev, overlay,
            OVERLAY_DITHER, OVERLAY_ENABLE);

    mOverlay = overlay;
    mWidth = overlay->w;
    mHeight = overlay->h;
    mFormat = overlay->format;
    mWidthStride = overlay->w_stride;
    mHeightStride = overlay->h_stride;
    mInitialized = false;

    mOverlayHandle = overlay->getHandleRef(overlay);

    sp<OverlayChannel> channel = new OverlayChannel( &layer );

    *overlayRef = new OverlayRef(mOverlayHandle, channel,
            mWidth, mHeight, mFormat, mWidthStride, mHeightStride);
    getFlinger()->signalEvent();
}

LayerBuffer::OverlaySource::~OverlaySource()
{
    if (mOverlay && mOverlayDevice) {
        overlay_control_device_t* overlay_dev = mOverlayDevice;
        overlay_dev->destroyOverlay(overlay_dev, mOverlay);
    }
}

void LayerBuffer::OverlaySource::onDraw(const Region& clip) const
{
    // this would be where the color-key would be set, should we need it.
    GLclampf red = 0;
    GLclampf green = 0;
    GLclampf blue = 0;
    mLayer.clearWithOpenGL(clip, red, green, blue, 0);
}

void LayerBuffer::OverlaySource::onTransaction(uint32_t flags)
{
    const Layer::State& front(mLayer.drawingState());
    const Layer::State& temp(mLayer.currentState());
    if (temp.sequence != front.sequence) {
        mVisibilityChanged = true;
    }
}

void LayerBuffer::OverlaySource::onvalidateVisibility(const Transform&)
{
    mVisibilityChanged = true;
}

void LayerBuffer::OverlaySource::onVisibilityResolved(
        const Transform& planeTransform)
{
    // this code-path must be as tight as possible, it's called each time
    // the screen is composited.
    if (UNLIKELY(mOverlay != 0)) {
        if (mVisibilityChanged || !mInitialized) {
            mVisibilityChanged = false;
            mInitialized = true;
            const Rect bounds(mLayer.getTransformedBounds());
            int x = bounds.left;
            int y = bounds.top;
            int w = bounds.width();
            int h = bounds.height();

            // we need a lock here to protect "destroy"
            Mutex::Autolock _l(mOverlaySourceLock);
            if (mOverlay) {
                overlay_control_device_t* overlay_dev = mOverlayDevice;
                overlay_dev->setPosition(overlay_dev, mOverlay, x,y,w,h);
                // we need to combine the layer orientation and the
                // user-requested orientation.
                Transform finalTransform(Transform(mLayer.getOrientation()) *
                        Transform(mOrientation));
                overlay_dev->setParameter(overlay_dev, mOverlay,
                        OVERLAY_TRANSFORM, finalTransform.getOrientation());
                overlay_dev->commit(overlay_dev, mOverlay);
            }
        }
    }
}

void LayerBuffer::OverlaySource::destroy()
{
    // we need a lock here to protect "onVisibilityResolved"
    Mutex::Autolock _l(mOverlaySourceLock);
    if (mOverlay && mOverlayDevice) {
        overlay_control_device_t* overlay_dev = mOverlayDevice;
        overlay_dev->destroyOverlay(overlay_dev, mOverlay);
        mOverlay = 0;
    }
}

// ---------------------------------------------------------------------------
}; // namespace android
