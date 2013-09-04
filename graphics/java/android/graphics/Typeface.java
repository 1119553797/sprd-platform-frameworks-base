/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.graphics;

import android.content.res.AssetManager;
import android.util.SparseArray;
import android.os.SystemProperties;
import android.util.Log;

import java.io.File;

/**
 * The Typeface class specifies the typeface and intrinsic style of a font.
 * This is used in the paint, along with optionally Paint settings like
 * textSize, textSkewX, textScaleX to specify
 * how text appears when drawn (and measured).
 */
public class Typeface {

    /** The default NORMAL typeface object */
    public static final Typeface DEFAULT;
    /**
     * SPRD: add for "fonts setting":user set typeface @{
     * @hide
     */
    public static Typeface mUserSetTf;
    /**
     * SPRD: add for "fonts setting": path of *.ttf that user set
     * @hide
     */
    public static String mUserSetTfPath;
    /*SPRD: add for "fonts setting":system default typeface backup */
    /**
     *  @hide
     */ 
    public static  Typeface DEFAULT_USER;
    /** @hide 
     * 
     */
    public static  Typeface DEFAULT_BOLD_USER;
    
    private static  Typeface[]  sDefaults_user;
    private static Typeface mPreUserSetTf;
    private static String mPreUserSetTfPath;
    /* @} */
    /**
     * The default BOLD typeface object. Note: this may be not actually be
     * bold, depending on what fonts are installed. Call getStyle() to know
     * for sure.
     */
    public static final Typeface DEFAULT_BOLD;
    /** The NORMAL style of the default sans serif typeface. */
    public static final Typeface SANS_SERIF;
    /** The NORMAL style of the default serif typeface. */
    public static final Typeface SERIF;
    /** The NORMAL style of the default monospace typeface. */
    public static final Typeface MONOSPACE;

    static Typeface[] sDefaults;
    private static final SparseArray<SparseArray<Typeface>> sTypefaceCache =
            new SparseArray<SparseArray<Typeface>>(3);

    int native_instance;

    // Style
    public static final int NORMAL = 0;
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int BOLD_ITALIC = 3;

    private int mStyle = 0;

    /** Returns the typeface's intrinsic style attributes */
    public int getStyle() {
        return mStyle;
    }

    /** Returns true if getStyle() has the BOLD bit set. */
    public final boolean isBold() {
        return (mStyle & BOLD) != 0;
    }

    /** Returns true if getStyle() has the ITALIC bit set. */
    public final boolean isItalic() {
        return (mStyle & ITALIC) != 0;
    }

    /**
     * Create a typeface object given a family name, and option style information.
     * If null is passed for the name, then the "default" font will be chosen.
     * The resulting typeface object can be queried (getStyle()) to discover what
     * its "real" style characteristics are.
     *
     * @param familyName May be null. The name of the font family.
     * @param style  The style (normal, bold, italic) of the typeface.
     *               e.g. NORMAL, BOLD, ITALIC, BOLD_ITALIC
     * @return The best matching typeface.
     */
    public static Typeface create(String familyName, int style) {
        return new Typeface(nativeCreate(familyName, style));
    }

    /**
     * Create a typeface object that best matches the specified existing
     * typeface and the specified Style. Use this call if you want to pick a new
     * style from the same family of an existing typeface object. If family is
     * null, this selects from the default font's family.
     *
     * @param family May be null. The name of the existing type face.
     * @param style  The style (normal, bold, italic) of the typeface.
     *               e.g. NORMAL, BOLD, ITALIC, BOLD_ITALIC
     * @return The best matching typeface.
     */
    public static Typeface create(Typeface family, int style) {
        int ni = 0;        
        if (family != null) {
            // Return early if we're asked for the same face/style
            if (family.mStyle == style) {
                return family;
            }

            ni = family.native_instance;
        }

        Typeface typeface;
        SparseArray<Typeface> styles = sTypefaceCache.get(ni);

        if (styles != null) {
            typeface = styles.get(style);
            if (typeface != null) {
                return typeface;
            }
        }

        typeface = new Typeface(nativeCreateFromTypeface(ni, style));
        if (styles == null) {
            styles = new SparseArray<Typeface>(4);
            sTypefaceCache.put(ni, styles);
        }
        styles.put(style, typeface);

        return typeface;
    }

    /**
     * Returns one of the default typeface objects, based on the specified style
     *
     * @return the default typeface that corresponds to the style
     */
    public static Typeface defaultFromStyle(int style) {
        // SPRD: modify for "fonts setting"
        return mUserSetTf != null ? sDefaults_user[style] : sDefaults[style];
    }
    
    /**
     * Create a new typeface from the specified font data.
     * @param mgr The application's asset manager
     * @param path  The file name of the font data in the assets directory
     * @return The new typeface.
     */
    public static Typeface createFromAsset(AssetManager mgr, String path) {
        return new Typeface(nativeCreateFromAsset(mgr, path));
    }

    /**
     * Create a new typeface from the specified font file.
     *
     * @param path The path to the font data. 
     * @return The new typeface.
     */
    public static Typeface createFromFile(File path) {
        return new Typeface(nativeCreateFromFile(path.getAbsolutePath()));
    }

    /**
     * Create a new typeface from the specified font file.
     *
     * @param path The full path to the font data. 
     * @return The new typeface.
     */
    public static Typeface createFromFile(String path) {
        return new Typeface(nativeCreateFromFile(path));
    }

    // don't allow clients to call this directly
    private Typeface(int ni) {
        if (ni == 0) {
            throw new RuntimeException("native typeface cannot be made");
        }

        native_instance = ni;
        mStyle = nativeGetStyle(ni);
    }
    /* SPRD: modify for "font setting" @{ */    
    static {
        DEFAULT = create((String) null, 0);
        DEFAULT_BOLD = create((String) null, Typeface.BOLD);

        sDefaults = new Typeface[] {
                DEFAULT,
                DEFAULT_BOLD,
                create((String) null, Typeface.ITALIC),
                create((String) null, Typeface.BOLD_ITALIC),
        };
        SANS_SERIF = create("sans-serif", 0);
        SERIF = create("serif", 0);
        MONOSPACE = create("monospace", 0);

        boolean userSet = "1".equals(SystemProperties.get("persist.sys.settypeface", "0"));
        String path = SystemProperties.get("persist.sys.usertf.path", "");
        // if user set the font,use the user typeface
        if (userSet && path != null && (new File(path)).exists()) {
            DEFAULT_USER = createFromFile(path);
            DEFAULT_BOLD_USER = DEFAULT_USER;

            sDefaults_user = new Typeface[] {
                        DEFAULT_USER,
                        DEFAULT_USER,
                        DEFAULT_USER,
                        DEFAULT_USER,
                };
            mUserSetTf = DEFAULT_USER;
            mUserSetTfPath = path;

        } else {
            DEFAULT_USER = DEFAULT;
            DEFAULT_BOLD_USER = DEFAULT_BOLD;

            sDefaults_user = sDefaults;
        }
    }

    /**
     * add for "fonts setting":check the *.ttf is ok
     * @hide
     * @param path
     * @return true:is ok; false:*.ttf is invalid
     */
    static public boolean isTypefaceOk(String path) {
        boolean ret = false;
        if (path == null || (path != null && !(new File(path)).exists())) {
            return ret;
        }
        try {
            Typeface tf = new Typeface(nativeCreateFromFile(path));
            if (tf != null) {
                ret = true;
            }
        } catch (RuntimeException re) {
            ret = false;
        }
        return ret;
    }
    /**
     * add for "fonts setting":userSetFlag is true,replace the default typeface with user typeface
     * reset the system default typeface when userSetFalg is false
     * @hide
     * @param userSetFlag
     * @param path
     */
    static public void reloadDefaultTf(boolean userSetFlag, String path) {
        Typeface tmpTf = null;
        String tmpPath = null;
        if (userSetFlag) {
            if (mUserSetTf != null && path != null && path.equals(mUserSetTfPath))
                return;

            if (mUserSetTf != null && path != null && !path.equals(mUserSetTfPath)
                    && path.equals(mPreUserSetTfPath)) {
                //change a new font(pre user font)
                tmpTf = mPreUserSetTf;
                tmpPath = mPreUserSetTfPath;
                mPreUserSetTf = mUserSetTf;
                mPreUserSetTfPath = mUserSetTfPath;
                mUserSetTf = tmpTf;
                mUserSetTfPath = tmpPath;
                return;
            } else if (mUserSetTf != null && path != null && !path.equals(mUserSetTfPath)
                    && !path.equals(mPreUserSetTfPath)) {
                //change a new font,but not pre user font
                mPreUserSetTf = mUserSetTf;
                mPreUserSetTfPath = mUserSetTfPath;
            }

            if (mUserSetTf == null && path != null && path.equals(mPreUserSetTfPath)) {
                // set a pre user font
                mUserSetTf = mPreUserSetTf;
                mUserSetTfPath = mPreUserSetTfPath;
                return;
            }
            if (path != null && !(new File(path)).exists()) {
                Log.w("Typeface","error:process could not accress file (path= " + path + ")");
                return;
            }
            DEFAULT_USER = createFromFile(path);
            DEFAULT_BOLD_USER = DEFAULT_USER;

            sDefaults_user = new Typeface[] {
                    DEFAULT_USER,
                    DEFAULT_USER,
                    DEFAULT_USER,
                    DEFAULT_USER,
            };
            mUserSetTf = DEFAULT_USER;
            mUserSetTfPath = path;

        } else {
            if (mUserSetTf == null) {
                return;
            }
            mPreUserSetTf = mUserSetTf;
            mPreUserSetTfPath = mUserSetTfPath;

            DEFAULT_USER = DEFAULT;
            DEFAULT_BOLD_USER = DEFAULT_BOLD;

            sDefaults_user = sDefaults;
            mUserSetTf = null;
            mUserSetTfPath = null;
        }
    }
    /* @} */

    protected void finalize() throws Throwable {
        try {
            nativeUnref(native_instance);
        } finally {
            super.finalize();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Typeface typeface = (Typeface) o;

        return mStyle == typeface.mStyle && native_instance == typeface.native_instance;
    }

    @Override
    public int hashCode() {
        int result = native_instance;
        result = 31 * result + mStyle;
        return result;
    }

    private static native int  nativeCreate(String familyName, int style);
    private static native int  nativeCreateFromTypeface(int native_instance, int style); 
    private static native void nativeUnref(int native_instance);
    private static native int  nativeGetStyle(int native_instance);
    private static native int  nativeCreateFromAsset(AssetManager mgr, String path);
    private static native int nativeCreateFromFile(String path);
}
