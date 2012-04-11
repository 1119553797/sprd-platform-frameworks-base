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

#define LOG_TAG "TextLayoutCache"

#include "SkFontHost.h"

#include "TextLayoutCache.h"
#include "TextLayout.h"

extern "C" {
  #include "harfbuzz-unicode.h"
}

namespace android {

//--------------------------------------------------------------------------------------------------
#if USE_TEXT_LAYOUT_CACHE
    ANDROID_SINGLETON_STATIC_INSTANCE(TextLayoutCache);
#endif
//--------------------------------------------------------------------------------------------------

TextLayoutCache::TextLayoutCache() :
        mCache(GenerationCache<TextLayoutCacheKey, sp<TextLayoutCacheValue> >::kUnlimitedCapacity),
        mSize(0), mMaxSize(MB(DEFAULT_TEXT_LAYOUT_CACHE_SIZE_IN_MB)),
        mCacheHitCount(0), mNanosecondsSaved(0) {
    init();
}

TextLayoutCache::~TextLayoutCache() {
    mCache.clear();
}

void TextLayoutCache::init() {
    mCache.setOnEntryRemovedListener(this);

    mDebugLevel = readRtlDebugLevel();
    mDebugEnabled = mDebugLevel & kRtlDebugCaches;
    LOGD("Using debug level: %d - Debug Enabled: %d", mDebugLevel, mDebugEnabled);

    mCacheStartTime = systemTime(SYSTEM_TIME_MONOTONIC);

    if (mDebugEnabled) {
        LOGD("Initialization is done - Start time: %lld", mCacheStartTime);
    }

    mInitialized = true;
}

/*
 * Size management
 */

uint32_t TextLayoutCache::getSize() {
    return mSize;
}

uint32_t TextLayoutCache::getMaxSize() {
    return mMaxSize;
}

void TextLayoutCache::setMaxSize(uint32_t maxSize) {
    mMaxSize = maxSize;
    removeOldests();
}

void TextLayoutCache::removeOldests() {
    while (mSize > mMaxSize) {
        mCache.removeOldest();
    }
}

/**
 *  Callbacks
 */
void TextLayoutCache::operator()(TextLayoutCacheKey& text, sp<TextLayoutCacheValue>& desc) {
    if (desc != NULL) {
        size_t totalSizeToDelete = text.getSize() + desc->getSize();
        mSize -= totalSizeToDelete;
        if (mDebugEnabled) {
            LOGD("Cache value deleted, size = %d", totalSizeToDelete);
        }
        desc.clear();
    }
}

/*
 * Cache clearing
 */
void TextLayoutCache::clear() {
    mCache.clear();
}

/*
 * Caching
 */
sp<TextLayoutCacheValue> TextLayoutCache::getValue(SkPaint* paint,
            const jchar* text, jint start, jint count, jint contextCount, jint dirFlags) {
    AutoMutex _l(mLock);
    nsecs_t startTime = 0;
    if (mDebugEnabled) {
        startTime = systemTime(SYSTEM_TIME_MONOTONIC);
    }

    // Create the key
    TextLayoutCacheKey key(paint, text, start, count, contextCount, dirFlags);

    // Get value from cache if possible
    sp<TextLayoutCacheValue> value = mCache.get(key);

    // Value not found for the key, we need to add a new value in the cache
    if (value == NULL) {
        if (mDebugEnabled) {
            startTime = systemTime(SYSTEM_TIME_MONOTONIC);
        }

        value = new TextLayoutCacheValue();

        // Compute advances and store them
        value->computeValues(paint, text, start, count, contextCount, dirFlags);

        nsecs_t endTime = systemTime(SYSTEM_TIME_MONOTONIC);

        // Don't bother to add in the cache if the entry is too big
        size_t size = key.getSize() + value->getSize();
        if (size <= mMaxSize) {
            // Cleanup to make some room if needed
            if (mSize + size > mMaxSize) {
                if (mDebugEnabled) {
                    LOGD("Need to clean some entries for making some room for a new entry");
                }
                while (mSize + size > mMaxSize) {
                    // This will call the callback
                    mCache.removeOldest();
                }
            }

            // Update current cache size
            mSize += size;

            // Copy the text when we insert the new entry
            key.internalTextCopy();
            mCache.put(key, value);

            if (mDebugEnabled) {
                // Update timing information for statistics
                value->setElapsedTime(endTime - startTime);

                LOGD("CACHE MISS: Added entry with "
                        "count=%d, entry size %d bytes, remaining space %d bytes"
                        " - Compute time in nanos: %d - Text='%s' ",
                        count, size, mMaxSize - mSize, value->getElapsedTime(),
                        String8(text, count).string());
            }
        } else {
            if (mDebugEnabled) {
                LOGD("CACHE MISS: Calculated but not storing entry because it is too big "
                        "with start=%d count=%d contextCount=%d, "
                        "entry size %d bytes, remaining space %d bytes"
                        " - Compute time in nanos: %lld - Text='%s'",
                        start, count, contextCount, size, mMaxSize - mSize, endTime,
                        String8(text, count).string());
            }
            value.clear();
        }
    } else {
        // This is a cache hit, just log timestamp and user infos
        if (mDebugEnabled) {
            nsecs_t elapsedTimeThruCacheGet = systemTime(SYSTEM_TIME_MONOTONIC) - startTime;
            mNanosecondsSaved += (value->getElapsedTime() - elapsedTimeThruCacheGet);
            ++mCacheHitCount;

            if (value->getElapsedTime() > 0) {
                float deltaPercent = 100 * ((value->getElapsedTime() - elapsedTimeThruCacheGet)
                        / ((float)value->getElapsedTime()));
                LOGD("CACHE HIT #%d with start=%d count=%d contextCount=%d"
                        "- Compute time in nanos: %d - "
                        "Cache get time in nanos: %lld - Gain in percent: %2.2f - Text='%s' ",
                        mCacheHitCount, start, count, contextCount,
                        value->getElapsedTime(), elapsedTimeThruCacheGet, deltaPercent,
                        String8(text, count).string());
            }
            if (mCacheHitCount % DEFAULT_DUMP_STATS_CACHE_HIT_INTERVAL == 0) {
                dumpCacheStats();
            }
        }
    }
    return value;
}

void TextLayoutCache::dumpCacheStats() {
    float remainingPercent = 100 * ((mMaxSize - mSize) / ((float)mMaxSize));
    float timeRunningInSec = (systemTime(SYSTEM_TIME_MONOTONIC) - mCacheStartTime) / 1000000000;
    LOGD("------------------------------------------------");
    LOGD("Cache stats");
    LOGD("------------------------------------------------");
    LOGD("pid       : %d", getpid());
    LOGD("running   : %.0f seconds", timeRunningInSec);
    LOGD("entries   : %d", mCache.size());
    LOGD("size      : %d bytes", mMaxSize);
    LOGD("remaining : %d bytes or %2.2f percent", mMaxSize - mSize, remainingPercent);
    LOGD("hits      : %d", mCacheHitCount);
    LOGD("saved     : %lld milliseconds", mNanosecondsSaved / 1000000);
    LOGD("------------------------------------------------");
}

/**
 * TextLayoutCacheKey
 */
TextLayoutCacheKey::TextLayoutCacheKey(): text(NULL), start(0), count(0), contextCount(0),
        dirFlags(0), typeface(NULL), textSize(0), textSkewX(0), textScaleX(0), flags(0),
        hinting(SkPaint::kNo_Hinting)  {
}

TextLayoutCacheKey::TextLayoutCacheKey(const SkPaint* paint, const UChar* text,
        size_t start, size_t count, size_t contextCount, int dirFlags) :
            text(text), start(start), count(count), contextCount(contextCount),
            dirFlags(dirFlags) {
    typeface = paint->getTypeface();
    textSize = paint->getTextSize();
    textSkewX = paint->getTextSkewX();
    textScaleX = paint->getTextScaleX();
    flags = paint->getFlags();
    hinting = paint->getHinting();
}

TextLayoutCacheKey::TextLayoutCacheKey(const TextLayoutCacheKey& other) :
        text(NULL),
        textCopy(other.textCopy),
        start(other.start),
        count(other.count),
        contextCount(other.contextCount),
        dirFlags(other.dirFlags),
        typeface(other.typeface),
        textSize(other.textSize),
        textSkewX(other.textSkewX),
        textScaleX(other.textScaleX),
        flags(other.flags),
        hinting(other.hinting) {
    if (other.text) {
        textCopy.setTo(other.text, other.contextCount);
    }
}

int TextLayoutCacheKey::compare(const TextLayoutCacheKey& lhs, const TextLayoutCacheKey& rhs) {
    int deltaInt = lhs.start - rhs.start;
    if (deltaInt != 0) return (deltaInt);

    deltaInt = lhs.count - rhs.count;
    if (deltaInt != 0) return (deltaInt);

    deltaInt = lhs.contextCount - rhs.contextCount;
    if (deltaInt != 0) return (deltaInt);

    if (lhs.typeface < rhs.typeface) return -1;
    if (lhs.typeface > rhs.typeface) return +1;

    if (lhs.textSize < rhs.textSize) return -1;
    if (lhs.textSize > rhs.textSize) return +1;

    if (lhs.textSkewX < rhs.textSkewX) return -1;
    if (lhs.textSkewX > rhs.textSkewX) return +1;

    if (lhs.textScaleX < rhs.textScaleX) return -1;
    if (lhs.textScaleX > rhs.textScaleX) return +1;

    deltaInt = lhs.flags - rhs.flags;
    if (deltaInt != 0) return (deltaInt);

    deltaInt = lhs.hinting - rhs.hinting;
    if (deltaInt != 0) return (deltaInt);

    deltaInt = lhs.dirFlags - rhs.dirFlags;
    if (deltaInt) return (deltaInt);

    return memcmp(lhs.getText(), rhs.getText(), lhs.contextCount * sizeof(UChar));
}

void TextLayoutCacheKey::internalTextCopy() {
    textCopy.setTo(text, contextCount);
    text = NULL;
}

size_t TextLayoutCacheKey::getSize() {
    return sizeof(TextLayoutCacheKey) + sizeof(UChar) * contextCount;
}

/**
 * TextLayoutCacheValue
 */
TextLayoutCacheValue::TextLayoutCacheValue() :
        mTotalAdvance(0), mElapsedTime(0), mClusterCount(0){
}

void TextLayoutCacheValue::setElapsedTime(uint32_t time) {
    mElapsedTime = time;
}

uint32_t TextLayoutCacheValue::getElapsedTime() {
    return mElapsedTime;
}

void TextLayoutCacheValue::computeValues(SkPaint* paint, const UChar* chars,
        size_t start, size_t count, size_t contextCount, int dirFlags) {
#if DEBUG_GLYPHS
    LOGD("computeValues -- chars[0]:%04x, start:%d, count:%d, dirFlags:%d.", chars[0], start, count, dirFlags);
#endif
    // Give a hint for advances, glyphs and log clusters vectors size
    mAdvances.setCapacity(contextCount);
    mGlyphs.setCapacity(contextCount);
	mClusterCount = 0;

    computeValuesWithHarfbuzz(paint, chars, start, count, contextCount, dirFlags,
            &mAdvances, &mTotalAdvance, &mGlyphs);
#if DEBUG_ADVANCES
    LOGD("Advances - start=%d, count=%d, countextCount=%d, totalAdvance=%f", start, count,
            contextCount, mTotalAdvance);
#endif
}

SkTypeface* TextLayoutCacheValue::backupPaintTypeface(SkPaint* paint) {
	SkTypeface* backupTypeface = paint->getTypeface();
    if (backupTypeface != NULL) {
        SkSafeRef(backupTypeface);
    }
	return backupTypeface;
}
void TextLayoutCacheValue::restorePaintTypeface(SkPaint* paint, SkTypeface* backupTypeface) {
    paint->setTypeface(backupTypeface);
    if (backupTypeface != NULL) {
        SkSafeUnref(backupTypeface);
    }
}

void TextLayoutCacheValue::drawGlyphCustom(FuncTypeDrawGlyphs doDrawGlyphs, SkCanvas* canvas,
		jfloat x, jfloat y, int flags, SkPaint* paint) {
	SkTypeface* backupTypeface = backupPaintTypeface(paint);
	const jchar* glyphs = getGlyphs();
	int start = 0, count = mGlyphs.size();
	jfloat x_offset = 0;
	jfloat x_start = x;
	SkPaint::Align textAlign = paint->getTextAlign();

	for (int i = 0; i < mClusterCount; i++)
	{
		paint->setTypeface(mClusterTypefaces[i]);
		count = mClusterLengths[i];
		if (textAlign == SkPaint::kLeft_Align)
			x_start = x + x_offset;
		else if (textAlign == SkPaint::kCenter_Align)
			x_start = x + (x_offset - ((mTotalAdvance - mClusterAdvances[i])/2));
		else if (textAlign == SkPaint::kRight_Align)
			x_start = x + (x_offset - (mTotalAdvance - mClusterAdvances[i]));
#if DEBUG_GLYPHS
		LOGD("drawGlyphCustom -- %d/%d, doDrawGlyphs(start:%d, count:%d, textAlign:%d, x_start:%f, y:%f), typeface:%08x, advance:%f",
			 i, mClusterCount, start, count, textAlign, x_start, y, mClusterTypefaces[i], mClusterAdvances[i]);
		for (int j = start; j < start + count; j++)
			LOGD("         -- glyphs[%d] = %d ", j, glyphs[j]);
#endif
		doDrawGlyphs(canvas, glyphs, start, count, x_start, y, flags, paint);
		start += count;
		x_offset += mClusterAdvances[i];
	}

	restorePaintTypeface(paint, backupTypeface);
}

size_t TextLayoutCacheValue::getSize() {
    return sizeof(TextLayoutCacheValue) + sizeof(jfloat) * mAdvances.capacity() +
		   sizeof(jchar) * mGlyphs.capacity() +
		   sizeof(SkTypeface*) * mClusterTypefaces.capacity() +
		   sizeof(size_t) * mClusterLengths.capacity() +
		   sizeof(jfloat) * mClusterAdvances.capacity();
}

void TextLayoutCacheValue::initShaperItem(HB_ShaperItem& shaperItem, HB_FontRec* font,
        FontData* fontData, SkPaint* paint, const UChar* chars, size_t contextCount) {
    // Zero the Shaper struct
    memset(&shaperItem, 0, sizeof(shaperItem));

    font->klass = &harfbuzzSkiaClass;
    font->userData = 0;

    // The values which harfbuzzSkiaClass returns are already scaled to
    // pixel units, so we just set all these to one to disable further
    // scaling.
    font->x_ppem = 1;
    font->y_ppem = 1;
    font->x_scale = 1;
    font->y_scale = 1;

    // Reset kerning
    shaperItem.kerning_applied = false;

#if DEBUG_GLYPHS
    LOGD("initShaperItem -- paint->getTypeface() is %08x ", paint->getTypeface());
#endif

    // Define font data
    fontData->typeFace = paint->getTypeface();
    fontData->complexTypeFace = NULL;
    fontData->script = HB_Script_Common;
    fontData->textSize = paint->getTextSize();
    fontData->textSkewX = paint->getTextSkewX();
    fontData->textScaleX = paint->getTextScaleX();
    fontData->flags = paint->getFlags();
    fontData->hinting = paint->getHinting();

    shaperItem.font = font;
    shaperItem.font->userData = fontData;

    //shaperItem.face = HB_NewFace((void*)fontData->typeFace, harfbuzzSkiaGetTable);
	shaperItem.face = NULL;

    // We cannot know, ahead of time, how many glyphs a given script run
    // will produce. We take a guess that script runs will not produce more
    // than twice as many glyphs as there are code points plus a bit of
    // padding and fallback if we find that we are wrong.
    createGlyphArrays(shaperItem, (contextCount + 2) * 2);

    // Set the string properties
    shaperItem.string = chars;
    shaperItem.stringLength = contextCount;
}

void TextLayoutCacheValue::freeShaperItem(HB_ShaperItem& shaperItem) {
    deleteGlyphArrays(shaperItem);
    HB_FreeFace(shaperItem.face);
}

const static char* paths[] = {
    "/system/fonts/Lohit-Bengali.ttf",
    "/system/fonts/Lohit-Devanagari.ttf",
    "/system/fonts/DroidSansHebrew-Regular.ttf",
    "/system/fonts/DroidSansHebrew-Bold.ttf",
    "/system/fonts/DroidNaskh-Regular.ttf",
    "/system/fonts/DroidKufi-Regular.ttf",
    "/system/fonts/Lohit-Tamil.ttf",
    "/system/fonts/DroidSansThai.ttf"
};
enum CustomScript {
	Bengali,
	Devanagari,
	Hebrew,
	HebrewBold,
	Naskh,
	Kufi,
	Tamil,
	Thai,
	NUM_SCRIPTS
};
#if DEBUG_GLYPHS
const static char *hb_langs[] = {
	"latn(Common)",	// Common
	"grek(Greek)",	// Greek
	"cyrl(Cyrillic)",	// Cyrillic
	"armn(Armenian)",	// Armenian
	"hebr(Hebrew)",	// Hebrew
	"arab(Arabic)",	// Arabic
	"syrc(Syriac)",	// Syriac
	"thaa(Thaana)",	// Thaana
	"deva(Devanagari)",	// Devanagari
	"beng(Bengali)",	// Bengali
	"guru(Gurmukhi)",	// Gurmukhi
	"gujr(Gujarati)",	// Gujarati
	"orya(Oriya)",	// Oriya
	"taml(Tamil)",	// Tamil
	"telu(Telugu)",	// Telugu
	"knda(Kannada)",	// Kannada
	"mlym(Malayalam)",	// Malayalam
	"sinh(Sinhala)",	// Sinhala
	"thai(Thai)",	// Thai
	"lao (Lao)",	// Lao
	"tibt(Tibetan)",	// Tibetan
	"mymr(Myanmar)",	// Myanmar
	"geor(Georgian)",	// Georgian
	"hang(Hangul)",	// Hangul
	"ogam(Ogham)",	// Ogham
	"runr(Runic)",	// Runic
	"khmr(Khmer)",	// Khmer
	"nko (N'Ko)",	// N'Ko
};
#endif
void TextLayoutCacheValue::setupFaceForScript(HB_ShaperItem& shaperItem)
{
	HB_Script hb_script = shaperItem.item.script;
	FontData* fontData = reinterpret_cast<FontData*>(shaperItem.font->userData);
	SkTypeface* typeface = fontData->typeFace;
	SkTypeface* typeface_new = NULL;
	CustomScript script;

	switch(hb_script){
		case HB_Script_Bengali:
			script = Bengali;
			break;
        //case HB_Script_Devanagari:
        //    script = Devanagari;
        //    break;
        //case HB_Script_Hebrew:
        //    switch (typeface->style()) {
        //        case SkTypeface::kBold:
        //        case SkTypeface::kBoldItalic:
        //            script = HebrewBold;
        //            break;
        //        case SkTypeface::kNormal:
        //        case SkTypeface::kItalic:
        //        default:
        //            script = Hebrew;
        //            break;
        //    }
        //    break;
        //case HB_Script_Arabic:
        //    script = Naskh;//Kufi;
        //    break;
        //case HB_Script_Tamil:
        //    script = Tamil;
        //    break;
        //case HB_Script_Thai:
        //    script = Thai;
        //    break;
        default:
            // HB_Script_Common; includes Ethiopic
            script = NUM_SCRIPTS;
            break;
	}

#if DEBUG_GLYPHS
    LOGD("setupFaceForScript -- fontData->typeFace is %08x ", fontData->typeFace);
    LOGD("                   -- fontData->script is %d ", fontData->script);
    LOGD("                   -- hb_script is %d ", hb_script);
    LOGD("                   -- shaperItem.face is %08x ", shaperItem.face);
#endif
	if (fontData->script == hb_script && hb_script != HB_Script_Common) {
#if DEBUG_GLYPHS
		LOGD("                   -- check point 1 ");
#endif
		mClusterTypefaces.add(typeface);
		return;
	}
	fontData->script = hb_script;

	if (script != NUM_SCRIPTS) {
#if DEBUG_GLYPHS
		LOGD("                   -- check point 2 ");
#endif
		typeface_new = SkFontHost::GetComplexTypefaceFromPath(paths[script]);
#if DEBUG_GLYPHS
		LOGD("                   -- paths[script] is %s ", paths[script]);
		LOGD("                   -- typeface_new is %08x ", typeface_new);
#endif
		if (typeface_new != NULL && typeface_new != typeface){
#if DEBUG_GLYPHS
			LOGD("                   -- check point 3 ");
#endif
			typeface = typeface_new;
		}
		fontData->complexTypeFace = typeface;
	} else {
#if DEBUG_GLYPHS
		LOGD("                   -- check point 4 ");
#endif
		fontData->complexTypeFace = NULL;
	}
#if DEBUG_GLYPHS
    LOGD("                   -- typeface is %08x ", typeface);
#endif
	mClusterTypefaces.add(typeface);

	if (shaperItem.face == NULL) {
#if DEBUG_GLYPHS
		LOGD("                   -- check point 5 ");
#endif
		shaperItem.face = HB_NewFace((void*)typeface, harfbuzzSkiaGetTable);
	} else if (hb_script != HB_Script_Common) {
#if DEBUG_GLYPHS
		LOGD("                   -- check point 6 ");
#endif
		HB_FreeFace(shaperItem.face);
		shaperItem.face = HB_NewFace((void*)typeface, harfbuzzSkiaGetTable);
	}
#if DEBUG_GLYPHS
	if (hb_script != HB_Script_Common)
		if (shaperItem.face) {
			LOGD("              -- shaperItem.face support langs :");
			for (unsigned int i = 0; i < HB_ScriptCount; ++i)
				if (shaperItem.face->supported_scripts[i])
					LOGD("           -- %s ", hb_langs[i]);
		}
    LOGD("                   -- shaperItem.face is %08x ", shaperItem.face);
#endif
	//if (typeface_new != NULL)
	//{
	//	SkSafeUnref(typeface_new);
	//}
}

void TextLayoutCacheValue::shapeRun(HB_ShaperItem& shaperItem, size_t start, size_t count,
        bool isRTL) {
    // Update Harfbuzz Shaper
    shaperItem.item.pos = start;
    shaperItem.item.length = count;
    shaperItem.item.bidiLevel = isRTL;

#if DEBUG_GLYPHS
	LOGD("shapeRun -- start = %d ", start);
	LOGD("         -- count = %d ", count);
	LOGD("         -- string = %s ", String8(shaperItem.string + start, count).string());
	LOGD("         -- isRTL = %d ", isRTL);
#endif

    //shaperItem.item.script = isRTL ? HB_Script_Arabic : HB_Script_Common;
	/*if (isRTL) {
		shaperItem.item.script = HB_Script_Arabic;
	} else {
		ssize_t start_pos = start;
		hb_utf16_script_run_next(NULL, &shaperItem.item,
								 shaperItem.string, count, &start_pos);
		shaperItem.item.length = count;
	}*/

	//set complex font Typeface
	setupFaceForScript(shaperItem);

#if DEBUG_GLYPHS
    LOGD("shapeRun -- shaperItem.item.pos = %d ", shaperItem.item.pos);
    LOGD("         -- shaperItem.item.length = %d ", shaperItem.item.length);
    LOGD("         -- shaperItem.item.bidiLevel = %d ", shaperItem.item.bidiLevel);
    LOGD("         -- shaperItem.item.script = %d ", shaperItem.item.script);
#endif

    // Shape
    assert(shaperItem.item.length > 0); // Harfbuzz will overwrite other memory if length is 0.
    while (!HB_ShapeItem(&shaperItem)) {
        // We overflowed our arrays. Resize and retry.
        // HB_ShapeItem fills in shaperItem.num_glyphs with the needed size.
        deleteGlyphArrays(shaperItem);
        createGlyphArrays(shaperItem, shaperItem.num_glyphs << 1);
    }
}

void TextLayoutCacheValue::computeValuesWithHarfbuzz(SkPaint* paint, const UChar* chars,
        size_t start, size_t count, size_t contextCount, int dirFlags,
        Vector<jfloat>* const outAdvances, jfloat* outTotalAdvance,
        Vector<jchar>* const outGlyphs) {
        if (!count) {
            *outTotalAdvance = 0;
            return;
        }
	
	#if DEBUG_GLYPHS
		LOGD("computeValuesWithHarfbuzz -- dirFlags:%d.", dirFlags);
	#endif
        UBiDiLevel bidiReq = 0;
        bool forceLTR = false;
        bool forceRTL = false;

        switch (dirFlags) {
            case kBidi_LTR: bidiReq = 0; break; // no ICU constant, canonical LTR level
            case kBidi_RTL: bidiReq = 1; break; // no ICU constant, canonical RTL level
            case kBidi_Default_LTR: bidiReq = UBIDI_DEFAULT_LTR; break;
            case kBidi_Default_RTL: bidiReq = UBIDI_DEFAULT_RTL; break;
            case kBidi_Force_LTR: forceLTR = true; break; // every char is LTR
            case kBidi_Force_RTL: forceRTL = true; break; // every char is RTL
        }

        HB_ShaperItem shaperItem;
        HB_FontRec font;
        FontData fontData;

        // Initialize Harfbuzz Shaper
        initShaperItem(shaperItem, &font, &fontData, paint, chars, contextCount);

        bool useSingleRun = false;
        bool isRTL = forceRTL;
        if (forceLTR || forceRTL) {
            useSingleRun = true;
        } else {
            UBiDi* bidi = ubidi_open();
            if (bidi) {
                UErrorCode status = U_ZERO_ERROR;
#if DEBUG_GLYPHS
                LOGD("computeValuesWithHarfbuzz -- bidiReq=%d", bidiReq);
#endif
                ubidi_setPara(bidi, chars, contextCount, bidiReq, NULL, &status);
                if (U_SUCCESS(status)) {
                    int paraDir = ubidi_getParaLevel(bidi) & kDirection_Mask; // 0 if ltr, 1 if rtl
                    ssize_t rc = ubidi_countRuns(bidi, &status);
#if DEBUG_GLYPHS
                    LOGD("computeValuesWithHarfbuzz -- dirFlags=%d run-count=%d paraDir=%d",
                            dirFlags, rc, paraDir);
#endif
                    if (U_SUCCESS(status) && rc == 1) {
                        // Normal case: one run, status is ok
                        isRTL = (paraDir == 1);
                        useSingleRun = true;
                    } else if (!U_SUCCESS(status) || rc < 1) {
                        LOGW("computeValuesWithHarfbuzz -- need to force to single run");
                        isRTL = (paraDir == 1);
                        useSingleRun = true;
                    } else {
                        int32_t end = start + count;
                        for (size_t i = 0; i < size_t(rc); ++i) {
                            int32_t startRun = -1;
                            int32_t lengthRun = -1;
                            UBiDiDirection runDir = ubidi_getVisualRun(bidi, i, &startRun, &lengthRun);

                            if (startRun == -1 || lengthRun == -1) {
                                // Something went wrong when getting the visual run, need to clear
                                // already computed data before doing a single run pass
                                LOGW("computeValuesWithHarfbuzz -- visual run is not valid");
                                outGlyphs->clear();
                                outAdvances->clear();
                                *outTotalAdvance = 0;
                                isRTL = (paraDir == 1);
                                useSingleRun = true;
                                break;
                            }

                            if (startRun >= end) {
                                continue;
                            }
                            int32_t endRun = startRun + lengthRun;
                            if (endRun <= int32_t(start)) {
                                continue;
                            }
                            if (startRun < int32_t(start)) {
                                startRun = int32_t(start);
                            }
                            if (endRun > end) {
                                endRun = end;
                            }

                            lengthRun = endRun - startRun;
                            isRTL = (runDir == UBIDI_RTL);
                            jfloat runTotalAdvance = 0;
#if DEBUG_GLYPHS
                            LOGD("computeValuesWithHarfbuzz -- run-start=%d run-len=%d isRTL=%d",
                                    startRun, lengthRun, isRTL);
#endif
                            computeRunValuesWithHarfbuzz(shaperItem, paint,
                                    startRun, lengthRun, isRTL,
                                    outAdvances, &runTotalAdvance, outGlyphs);

                            *outTotalAdvance += runTotalAdvance;
                        }
                    }
                } else {
                    LOGW("computeValuesWithHarfbuzz -- cannot set Para");
                    useSingleRun = true;
                    isRTL = (bidiReq = 1) || (bidiReq = UBIDI_DEFAULT_RTL);
                }
                ubidi_close(bidi);
            } else {
                LOGW("computeValuesWithHarfbuzz -- cannot ubidi_open()");
                useSingleRun = true;
                isRTL = (bidiReq = 1) || (bidiReq = UBIDI_DEFAULT_RTL);
            }
        }

        // Default single run case
        if (useSingleRun){
#if DEBUG_GLYPHS
            LOGD("computeValuesWithHarfbuzz -- Using a SINGLE Run "
                    "-- run-start=%d run-len=%d isRTL=%d", start, count, isRTL);
#endif
            computeRunValuesWithHarfbuzz(shaperItem, paint,
                    start, count, isRTL,
                    outAdvances, outTotalAdvance, outGlyphs);
        }

        // Cleaning
        freeShaperItem(shaperItem);

#if DEBUG_GLYPHS
        LOGD("computeValuesWithHarfbuzz -- total-glyphs-count=%d", outGlyphs->size());
#endif
}

static void logGlyphs(HB_ShaperItem shaperItem) {
    LOGD("Got glyphs - count=%d", shaperItem.num_glyphs);
    for (size_t i = 0; i < shaperItem.num_glyphs; i++) {
        LOGD("      glyph[%d]=%d - offset.x=%f offset.y=%f", i, shaperItem.glyphs[i],
                HBFixedToFloat(shaperItem.offsets[i].x),
                HBFixedToFloat(shaperItem.offsets[i].y));
    }
}

void TextLayoutCacheValue::setClusterCount(size_t clusterCount) {
	mClusterTypefaces.setCapacity(clusterCount);
	mClusterLengths.setCapacity(clusterCount);
	mClusterAdvances.setCapacity(clusterCount);
	mClusterCount = clusterCount;
}

void TextLayoutCacheValue::computeRunValuesWithHarfbuzz(HB_ShaperItem& shaperItem, SkPaint* paint,
        size_t start, size_t count, bool isRTL,
        Vector<jfloat>* const outAdvances, jfloat* outTotalAdvance,
        Vector<jchar>* const outGlyphs) {

	if (isRTL) {
		setClusterCount(mClusterCount + 1);
		shaperItem.item.script = HB_Script_Arabic;
		computeRunValuesWithHarfbuzzSub(shaperItem, paint,
										start, count, isRTL,
										outAdvances, outTotalAdvance, outGlyphs);
	} else {
		jfloat runTotalAdvance = 0;
		ssize_t start_pos = start;
		int32_t startRun = -1;
		int32_t lengthRun = -1;
		do {
			hb_utf16_script_run_next(NULL, &shaperItem.item,
									 shaperItem.string, start+count, &start_pos);
			setClusterCount(mClusterCount + 1);
			startRun = shaperItem.item.pos;
			lengthRun = shaperItem.item.length;
			computeRunValuesWithHarfbuzzSub(shaperItem, paint,
											startRun, lengthRun, isRTL,
											outAdvances, &runTotalAdvance, outGlyphs);

			mClusterAdvances.add(runTotalAdvance);
			*outTotalAdvance += runTotalAdvance;
		}while(start_pos < start + count);
	}
}

void TextLayoutCacheValue::computeRunValuesWithHarfbuzzSub(HB_ShaperItem& shaperItem, SkPaint* paint,
        size_t start, size_t count, bool isRTL,
        Vector<jfloat>* const outAdvances, jfloat* outTotalAdvance,
        Vector<jchar>* const outGlyphs) {
    if (!count) {
        *outTotalAdvance = 0;
        return;
    }

    shapeRun(shaperItem, start, count, isRTL);

#if DEBUG_GLYPHS
    LOGD("HARFBUZZ -- num_glypth=%d - kerning_applied=%d", shaperItem.num_glyphs,
            shaperItem.kerning_applied);
    LOGD("         -- string= '%s'", String8(shaperItem.string + start, count).string());
    LOGD("         -- isDevKernText=%d", paint->isDevKernText());

    logGlyphs(shaperItem);
#endif

	mClusterLengths.add(shaperItem.num_glyphs);
    if (shaperItem.advances == NULL || shaperItem.num_glyphs == 0) {
#if DEBUG_GLYPHS
    LOGD("HARFBUZZ -- advances array is empty or num_glypth = 0");
#endif
        outAdvances->insertAt(0, outAdvances->size(), count);
        *outTotalAdvance = 0;
        return;
    }

#if DEBUG_ADVANCES
	{
		LOGD("HARFBUZZ -- out advance:");
		jfloat currentAdvance_tmp;
		jfloat totalAdvance_tmp = 0;
		for (size_t i = 0; i < shaperItem.num_glyphs; i++) {
			currentAdvance_tmp = HBFixedToFloat(shaperItem.advances[i]);
			totalAdvance_tmp += currentAdvance_tmp;
			LOGD("         -- advances[%d] = %f - total = %f", i,
				 currentAdvance_tmp, totalAdvance_tmp);
		}
	}
#endif
#if 0
    // Get Advances and their total
    jfloat currentAdvance = HBFixedToFloat(shaperItem.advances[shaperItem.log_clusters[0]]);
    jfloat totalAdvance = currentAdvance;
    outAdvances->add(currentAdvance);
    for (size_t i = 1; i < count; i++) {
        size_t clusterPrevious = shaperItem.log_clusters[i - 1];
        size_t cluster = shaperItem.log_clusters[i];
        if (cluster == clusterPrevious) {
            outAdvances->add(0);
        } else {
            currentAdvance = HBFixedToFloat(shaperItem.advances[shaperItem.log_clusters[i]]);
            totalAdvance += currentAdvance;
            outAdvances->add(currentAdvance);
        }
    }
    *outTotalAdvance = totalAdvance;

#if DEBUG_ADVANCES
    for (size_t i = 0; i < count; i++) {
        LOGD("hb-adv[%d] = %f - log_clusters = %d - total = %f", i,
                (*outAdvances)[i], shaperItem.log_clusters[i], totalAdvance);
    }
#endif
#else
    // Get Advances and their total
    jfloat totalAdvance = 0;
    size_t clusterNext = 0;
	size_t j = 0;
    for (size_t i = 0; i < count; i++) {
        size_t cluster = shaperItem.log_clusters[i];

		if (clusterNext == cluster)
		{
			if (i < count - 1)
				for(size_t k = i + 1; k < count; k++) {
					clusterNext = shaperItem.log_clusters[k];
					if (clusterNext != cluster)
						break;
				}
			if (clusterNext == cluster)
				clusterNext = shaperItem.num_glyphs;
		}
		if (j < clusterNext && j < shaperItem.num_glyphs)
		{
			jfloat currentAdvance = HBFixedToFloat(shaperItem.advances[j]);
			totalAdvance += currentAdvance;
			outAdvances->add(currentAdvance);
			j++;
		}
		else
		{
			outAdvances->add(0);
		}
	}
	for (; j < shaperItem.num_glyphs; j++) {
		totalAdvance += HBFixedToFloat(shaperItem.advances[j]);
	}
    *outTotalAdvance = totalAdvance;

#if DEBUG_ADVANCES
    for (size_t i = 0; i < count; i++) {
        LOGD("hb-adv[%d] = %f - log_clusters = %d - total = %f", i,
			 (*outAdvances)[i], shaperItem.log_clusters[i], totalAdvance);
    }
#endif
#endif

    // Get Glyphs and reverse them in place if RTL
    if (outGlyphs) {
        size_t countGlyphs = shaperItem.num_glyphs;
        for (size_t i = 0; i < countGlyphs; i++) {
            jchar glyph = (jchar) shaperItem.glyphs[(!isRTL) ? i : countGlyphs - 1 - i];
#if DEBUG_GLYPHS
            LOGD("HARFBUZZ  -- glyph[%d]=%d", i, glyph);
#endif
            outGlyphs->add(glyph);
        }
    }
}

void TextLayoutCacheValue::deleteGlyphArrays(HB_ShaperItem& shaperItem) {
    delete[] shaperItem.glyphs;
    delete[] shaperItem.attributes;
    delete[] shaperItem.advances;
    delete[] shaperItem.offsets;
    delete[] shaperItem.log_clusters;
}

void TextLayoutCacheValue::createGlyphArrays(HB_ShaperItem& shaperItem, int size) {
    shaperItem.num_glyphs = size;

    // These arrays are all indexed by glyph
    shaperItem.glyphs = new HB_Glyph[size];
    shaperItem.attributes = new HB_GlyphAttributes[size];
    shaperItem.advances = new HB_Fixed[size];
    shaperItem.offsets = new HB_FixedPoint[size];

    // Although the log_clusters array is indexed by character, Harfbuzz expects that
    // it is big enough to hold one element per glyph.  So we allocate log_clusters along
    // with the other glyph arrays above.
    shaperItem.log_clusters = new unsigned short[size];
}

} // namespace android
