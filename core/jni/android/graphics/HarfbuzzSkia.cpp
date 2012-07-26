/*
 * Copyright 2011, The Android Open Source Project
 * Copyright 2011, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#define LOG_TAG "HarfbuzzSkia"

#include "HarfbuzzSkia.h"

#include "SkFontHost.h"

#include "SkPaint.h"
#include "SkPath.h"
#include "SkPoint.h"
#include "SkRect.h"
#include "SkTypeface.h"

#include "String8.h"

#include <utils/Log.h>

extern "C" {
#include "harfbuzz-shaper.h"
}

#define DEBUG_BY_LJK 0

// This file implements the callbacks which Harfbuzz requires by using Skia
// calls. See the Harfbuzz source for references about what these callbacks do.

namespace android {

static void setupPaintWithFontData(SkPaint* paint, FontData* data) {
    if (data->complexTypeFace)
        paint->setTypeface(data->complexTypeFace);
    else
        paint->setTypeface(data->typeFace);
    paint->setTextSize(data->textSize);
    paint->setTextSkewX(data->textSkewX);
    paint->setTextScaleX(data->textScaleX);
    paint->setFlags(data->flags);
    paint->setHinting(data->hinting);
}

static HB_Bool stringToGlyphs(HB_Font hbFont, const HB_UChar16* characters, hb_uint32 length,
        HB_Glyph* glyphs, hb_uint32* glyphsSize, HB_Bool isRTL)
{
#if DEBUG_BY_LJK
    LOGD("stringToGlyphs -- characters is %s ", String8(characters, length).string());
    LOGD("stringToGlyphs -- length is %d ", length);
#endif
    FontData* data = reinterpret_cast<FontData*>(hbFont->userData);
    SkPaint paint;
    setupPaintWithFontData(&paint, data);

    paint.setTextEncoding(SkPaint::kUTF16_TextEncoding);
    uint16_t* skiaGlyphs = reinterpret_cast<uint16_t*>(glyphs);
    int numGlyphs = paint.textToGlyphs(characters, length * sizeof(uint16_t), skiaGlyphs);

    // HB_Glyph is 32-bit, but Skia outputs only 16-bit numbers. So our
    // |glyphs| array needs to be converted.
    for (int i = numGlyphs - 1; i >= 0; --i) {
        glyphs[i] = skiaGlyphs[i];
    }

    *glyphsSize = numGlyphs;
    return 1;
}

static void glyphsToAdvances(HB_Font hbFont, const HB_Glyph* glyphs, hb_uint32 numGlyphs,
        HB_Fixed* advances, int flags)
{
#if DEBUG_BY_LJK
    LOGD("glyphsToAdvances -- numGlyphs is %d ", numGlyphs);
#endif
    FontData* data = reinterpret_cast<FontData*>(hbFont->userData);
    SkPaint paint;
    setupPaintWithFontData(&paint, data);

    paint.setTextEncoding(SkPaint::kGlyphID_TextEncoding);

    uint16_t* glyphs16 = new uint16_t[numGlyphs];
    if (!glyphs16)
        return;
    for (unsigned i = 0; i < numGlyphs; ++i)
        glyphs16[i] = glyphs[i];
    SkScalar* scalarAdvances = reinterpret_cast<SkScalar*>(advances);
    paint.getTextWidths(glyphs16, numGlyphs * sizeof(uint16_t), scalarAdvances);

    // The |advances| values which Skia outputs are SkScalars, which are floats
    // in Chromium. However, Harfbuzz wants them in 26.6 fixed point format.
    // These two formats are both 32-bits long.
    for (unsigned i = 0; i < numGlyphs; ++i) {
        advances[i] = SkScalarToHBFixed(scalarAdvances[i]);
#if DEBUG_ADVANCES
        LOGD("glyphsToAdvances -- advances[%d]=%d", i, advances[i]);
#endif
    }
    delete glyphs16;
}

static HB_Bool canRender(HB_Font hbFont, const HB_UChar16* characters, hb_uint32 length)
{
#if DEBUG_BY_LJK
    LOGD("canRender -- characters is %s ", String8(characters, length).string());
    LOGD("          -- length is %d ", length);
#endif
    FontData* data = reinterpret_cast<FontData*>(hbFont->userData);
    SkPaint paint;
    setupPaintWithFontData(&paint, data);

    paint.setTextEncoding(SkPaint::kUTF16_TextEncoding);

    uint16_t* glyphs16 = new uint16_t[length];
    int numGlyphs = paint.textToGlyphs(characters, length * sizeof(uint16_t), glyphs16);

    bool result = true;
    for (int i = 0; i < numGlyphs; ++i) {
        if (!glyphs16[i]) {
            result = false;
            break;
        }
    }
    delete glyphs16;
    return result;
}

static HB_Error getOutlinePoint(HB_Font hbFont, HB_Glyph glyph, int flags, hb_uint32 point,
        HB_Fixed* xPos, HB_Fixed* yPos, hb_uint32* resultingNumPoints)
{
#if DEBUG_BY_LJK
    LOGD("getOutlinePoint -- glyph is %d", glyph);
#endif
    FontData* data = reinterpret_cast<FontData*>(hbFont->userData);
    SkPaint paint;
    setupPaintWithFontData(&paint, data);

    if (flags & HB_ShaperFlag_UseDesignMetrics)
        // This is requesting pre-hinted positions. We can't support this.
        return HB_Err_Invalid_Argument;

    paint.setTextEncoding(SkPaint::kGlyphID_TextEncoding);
    uint16_t glyph16 = glyph;
    SkPath path;
    paint.getTextPath(&glyph16, sizeof(glyph16), 0, 0, &path);
    uint32_t numPoints = path.getPoints(0, 0);
    if (point >= numPoints)
        return HB_Err_Invalid_SubTable;
    SkPoint* points = reinterpret_cast<SkPoint*>(malloc(sizeof(SkPoint) * (point + 1)));
    if (!points)
        return HB_Err_Invalid_SubTable;
    // Skia does let us get a single point from the path.
    path.getPoints(points, point + 1);
    *xPos = SkScalarToHBFixed(points[point].fX);
    *yPos = SkScalarToHBFixed(points[point].fY);
    *resultingNumPoints = numPoints;
    delete points;

    return HB_Err_Ok;
}

static void getGlyphMetrics(HB_Font hbFont, HB_Glyph glyph, HB_GlyphMetrics* metrics)
{
#if DEBUG_BY_LJK
    LOGD("getOutlinePoint -- glyph is %d ", glyph);
#endif
    FontData* data = reinterpret_cast<FontData*>(hbFont->userData);
    SkPaint paint;
    setupPaintWithFontData(&paint, data);

    paint.setTextEncoding(SkPaint::kGlyphID_TextEncoding);
    uint16_t glyph16 = glyph;
    SkScalar width;
    SkRect bounds;
    paint.getTextWidths(&glyph16, sizeof(glyph16), &width, &bounds);

    metrics->x = SkScalarToHBFixed(bounds.fLeft);
    metrics->y = SkScalarToHBFixed(bounds.fTop);
    metrics->width = SkScalarToHBFixed(bounds.width());
    metrics->height = SkScalarToHBFixed(bounds.height());

    metrics->xOffset = SkScalarToHBFixed(width);
    // We can't actually get the |y| correct because Skia doesn't export
    // the vertical advance. However, nor we do ever render vertical text at
    // the moment so it's unimportant.
    metrics->yOffset = 0;
}

static HB_Fixed getFontMetric(HB_Font hbFont, HB_FontMetric metric)
{
#if DEBUG_BY_LJK
    LOGD("getOutlinePoint  ");
#endif
    FontData* data = reinterpret_cast<FontData*>(hbFont->userData);
    SkPaint paint;
    setupPaintWithFontData(&paint, data);

    SkPaint::FontMetrics skiaMetrics;
    paint.getFontMetrics(&skiaMetrics);

    switch (metric) {
    case HB_FontAscent:
        return SkScalarToHBFixed(-skiaMetrics.fAscent);
    // We don't support getting the rest of the metrics and Harfbuzz doesn't seem to need them.
    default:
        return 0;
    }
    return 0;
}

const HB_FontClass harfbuzzSkiaClass = {
    stringToGlyphs,
    glyphsToAdvances,
    canRender,
    getOutlinePoint,
    getGlyphMetrics,
    getFontMetric,
};

HB_Error harfbuzzSkiaGetTable(void* voidface, const HB_Tag tag, HB_Byte* buffer, HB_UInt* len)
{
    SkTypeface* typeface = reinterpret_cast<SkTypeface*>(voidface);

#if DEBUG_BY_LJK
    LOGD("harfbuzzSkiaGetTable -- typeface=%08x", typeface);
    LOGD("                     -- tag=%08x", tag);
    LOGD("                     -- len(in)=%d", *len);
#endif
    if (!voidface)
        return HB_Err_Invalid_Argument;
    const size_t tableSize = SkFontHost::GetTableSize(typeface->uniqueID(), tag);
#if DEBUG_BY_LJK
    LOGD("                     -- typeface->uniqueID()=%d", typeface->uniqueID());
    LOGD("                     -- tableSize=%d", tableSize);
#endif
    if (!tableSize)
        return HB_Err_Invalid_Argument;
    // If Harfbuzz specified a NULL buffer then it's asking for the size of the table.
    if (!buffer) {
        *len = tableSize;
        return HB_Err_Ok;
    }

    if (*len < tableSize)
        return HB_Err_Invalid_Argument;
    SkFontHost::GetTableData(typeface->uniqueID(), tag, 0, tableSize, buffer);
#if DEBUG_BY_LJK
    LOGD("                     -- return HB_Err_Ok");
#endif
    return HB_Err_Ok;
}

}  // namespace android

#if USE_HARFBUZZ_NG
#ifdef HB_UNUSED
#undef HB_UNUSED
#endif

#include "hb-private.hh"
#include "hb.h"
#include "hb-font-private.hh"

namespace android {

static void setupPaintFromUserData(SkPaint* paint, void *user_data)
{
    setupPaintWithFontData(paint, reinterpret_cast<FontData*>(user_data));
}

static hb_bool_t
hb_skia_get_glyph (hb_font_t *font HB_UNUSED,
                 void *font_data,
                 hb_codepoint_t unicode,
                 hb_codepoint_t variation_selector,
                 hb_codepoint_t *glyph,
                 void *user_data HB_UNUSED)

{
    SkPaint paint;
    setupPaintFromUserData(&paint, font_data);

    HB_UChar16 character;
    uint16_t skiaGlyph;

    paint.setTextEncoding(SkPaint::kUTF16_TextEncoding);
    character = (HB_UChar16)unicode;
    int numGlyphs = paint.textToGlyphs(&character, 1 * sizeof(skiaGlyph), &skiaGlyph);

    if (numGlyphs == 1)
    {
        *glyph = (hb_codepoint_t)skiaGlyph;
        return 1;
    }
    return 0;
}

static hb_position_t
hb_skia_get_glyph_h_advance (hb_font_t *font HB_UNUSED,
                           void *font_data,
                           hb_codepoint_t glyph,
                           void *user_data HB_UNUSED)
{
    SkPaint paint;
    setupPaintFromUserData(&paint, font_data);

    paint.setTextEncoding(SkPaint::kGlyphID_TextEncoding);
    uint16_t glyph16 = glyph;
    SkScalar width;
//    SkRect bounds;
    paint.getTextWidths(&glyph16, sizeof(glyph16), &width);//, &bounds);

    return SkScalarToHBFixed(width);
}

static hb_position_t
hb_skia_get_glyph_v_advance (hb_font_t *font HB_UNUSED,
                           void *font_data,
                           hb_codepoint_t glyph,
                           void *user_data HB_UNUSED)
{
    /*
    SkPaint paint;
    setupPaintFromUserData(&paint, font_data);
    */
    return 0;
}

static hb_bool_t
hb_skia_get_glyph_h_origin (hb_font_t *font HB_UNUSED,
                          void *font_data HB_UNUSED,
                          hb_codepoint_t glyph HB_UNUSED,
                          hb_position_t *x HB_UNUSED,
                          hb_position_t *y HB_UNUSED,
                          void *user_data HB_UNUSED)
{
    /* We always work in the horizontal coordinates. */
    return true;
}

static hb_bool_t
hb_skia_get_glyph_v_origin (hb_font_t *font HB_UNUSED,
                          void *font_data,
                          hb_codepoint_t glyph,
                          hb_position_t *x,
                          hb_position_t *y,
                          void *user_data HB_UNUSED)
{
    /*
    SkPaint paint;
    setupPaintFromUserData(&paint, font_data);
    */
    return true;
}

static hb_position_t
hb_skia_get_glyph_h_kerning (hb_font_t *font HB_UNUSED,
                           void *font_data,
                           hb_codepoint_t left_glyph,
                           hb_codepoint_t right_glyph,
                           void *user_data HB_UNUSED)
{
    /*
    SkPaint paint;
    setupPaintFromUserData(&paint, font_data);
    */
    return 0;
}

static hb_position_t
hb_skia_get_glyph_v_kerning (hb_font_t *font HB_UNUSED,
                           void *font_data HB_UNUSED,
                           hb_codepoint_t top_glyph HB_UNUSED,
                           hb_codepoint_t bottom_glyph HB_UNUSED,
                           void *user_data HB_UNUSED)
{
    /* FreeType API doesn't support vertical kerning */
    return 0;
}

static hb_bool_t
hb_skia_get_glyph_extents (hb_font_t *font HB_UNUSED,
                         void *font_data,
                         hb_codepoint_t glyph,
                         hb_glyph_extents_t *extents,
                         void *user_data HB_UNUSED)
{
    SkPaint paint;
    setupPaintFromUserData(&paint, font_data);

    paint.setTextEncoding(SkPaint::kGlyphID_TextEncoding);
    uint16_t glyph16 = glyph;
    SkScalar width;
    SkRect bounds;
    paint.getTextWidths(&glyph16, sizeof(glyph16), &width, &bounds);
    
    extents->x_bearing = SkScalarToHBFixed(bounds.fLeft);
    extents->y_bearing = SkScalarToHBFixed(bounds.fTop);
    extents->width = SkScalarToHBFixed(bounds.width());
    extents->height = SkScalarToHBFixed(bounds.height());

    return true;
}

static hb_bool_t
hb_skia_get_glyph_contour_point (hb_font_t *font HB_UNUSED,
                               void *font_data,
                               hb_codepoint_t glyph,
                               unsigned int point_index,
                               hb_position_t *x,
                               hb_position_t *y,
                               void *user_data HB_UNUSED)
{
    SkPaint paint;
    setupPaintFromUserData(&paint, font_data);
    
    paint.setTextEncoding(SkPaint::kGlyphID_TextEncoding);
    uint16_t glyph16 = glyph;
    SkPath path;
    paint.getTextPath(&glyph16, sizeof(glyph16), 0, 0, &path);
    uint32_t numPoints = path.getPoints(0, 0);
    if (point_index >= numPoints)
        return HB_Err_Invalid_SubTable;
    SkPoint* points = reinterpret_cast<SkPoint*>(malloc(sizeof(SkPoint) * (point_index + 1)));
    if (!points)
        return HB_Err_Invalid_SubTable;
    // Skia does let us get a single point from the path.
    path.getPoints(points, point_index + 1);
    *x = SkScalarToHBFixed(points[point_index].fX);
    *y = SkScalarToHBFixed(points[point_index].fY);
//    *resultingNumPoints = numPoints;
    delete points;

    return true;
}

static hb_bool_t
hb_skia_get_glyph_name (hb_font_t *font,
                      void *font_data,
                      hb_codepoint_t glyph,
                      char *name, unsigned int size,
                      void *user_data HB_UNUSED)
{
    /*
    SkPaint paint;
    setupPaintFromUserData(&paint, font_data);
    */
    snprintf (name, size, "gid%u", glyph);
    return 0;
}

static hb_bool_t
hb_skia_get_glyph_from_name (hb_font_t *font,
                           void *font_data,
                           const char *name, int len, /* -1 means nul-terminated */
                           hb_codepoint_t *glyph,
                           void *user_data HB_UNUSED)
{
    /*
    SkPaint paint;
    setupPaintFromUserData(&paint, font_data);
    */
    
    *glyph = 0;
    
    return *glyph != 0;
}

static hb_font_funcs_t *
_hb_skia_get_font_funcs (void)
{
    static const hb_font_funcs_t skia_ffuncs = {
        HB_OBJECT_HEADER_STATIC,

        true, /* immutable */

        {
#define HB_FONT_FUNC_IMPLEMENT(name) hb_skia_get_##name,
            HB_FONT_FUNCS_IMPLEMENT_CALLBACKS
#undef HB_FONT_FUNC_IMPLEMENT
        }
    };

    return const_cast<hb_font_funcs_t *> (&skia_ffuncs);
}

static hb_blob_t *
reference_table  (hb_face_t *face HB_UNUSED, hb_tag_t tag, void *user_data)
{
    SkTypeface* typeface = reinterpret_cast<SkTypeface*>(user_data);
    char *buffer;

    if (!user_data)
        return NULL;
    const size_t tableSize = SkFontHost::GetTableSize(typeface->uniqueID(), tag);

    if (!tableSize)
        return NULL;

    buffer = (char *) malloc (tableSize);
    if (buffer == NULL)
        return NULL;

    
    SkFontHost::GetTableData(typeface->uniqueID(), tag, 0, tableSize, buffer);

    return hb_blob_create ((const char *) buffer, tableSize,
                           HB_MEMORY_MODE_WRITABLE,
                           buffer, free);
}

hb_face_t * hb_skia_face_create(FontData* data,
                           hb_destroy_func_t destroy)
{
    hb_face_t *face;
    SkTypeface* typeface = NULL;

    if (data->complexTypeFace)
        typeface = data->complexTypeFace;
    else
        typeface = data->typeFace;

    face = hb_face_create_for_tables (reference_table, typeface, destroy);

    hb_face_set_index (face, 0/*ft_face->face_index*/);
    //hb_face_set_upem (face, ft_face->units_per_EM);

    return face;
}

static void
_do_nothing (void)
{
}


hb_font_t *
hb_skia_font_create (FontData* data,
                   hb_destroy_func_t destroy)
{
    hb_font_t *font;
    hb_face_t *face;

    face = hb_skia_face_create (data, destroy);
    font = hb_font_create (face);
    hb_face_destroy (face);
    hb_font_set_funcs (font,
                       _hb_skia_get_font_funcs (),
                       data, (hb_destroy_func_t) _do_nothing);
    
    hb_font_set_scale(font, 1, 1);
    hb_font_set_ppem(font, 1, 1);

    return font;
}

}  // namespace android

#endif
