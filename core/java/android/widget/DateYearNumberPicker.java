/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.widget;


import android.annotation.Widget;
import android.content.Context;
import android.text.InputType;
import android.text.Spanned;
import android.text.method.NumberKeyListener;
import android.util.AttributeSet;
import android.util.Log;

/**
 * A view for selecting a number of Year
 *
 * @hide
 */
@Widget
public class DateYearNumberPicker extends NumberPicker {

    /**
     * Previous value of this DateYearNumberPicker.
     */

    /**
     * Create a new number picker
     * @param context the application environment
     */
    public DateYearNumberPicker(Context context) {
        this(context, null);
    }

    /**
     * Create a new number picker
     * @param context the application environment
     * @param attrs a collection of attributes
     */
    public DateYearNumberPicker(Context context, AttributeSet attrs) {
        super(context, attrs);
        mNumberInputFilter = new NumberRangeKeyListener();
    }

    private static final char[] DIGIT_CHARACTERS = new char[] {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'
    };

    private class NumberRangeKeyListener extends NumberKeyListener {

        // XXX This doesn't allow for range limits when controlled by a
        // soft input method!
        public int getInputType() {
            return InputType.TYPE_CLASS_NUMBER;
        }

        @Override
        protected char[] getAcceptedChars() {
            return DIGIT_CHARACTERS;
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end,
                Spanned dest, int dstart, int dend) {

            CharSequence filtered = super.filter(source, start, end, dest, dstart, dend);
            if (filtered == null) {
                filtered = source.subSequence(start, end);
            }

            String result = String.valueOf(dest.subSequence(0, dstart))
                    + filtered
                    + dest.subSequence(dend, dest.length());
            if ("".equals(result)) {
                return result;
            }
            int val = getSelectedPos(result);

            /*  We  only allow the year more than 1970.
             */
            
            if(val > getEndRange()){
                return "";
            } else if (val >1000 && val < getBeginRange()) {
                return "";
            } else {
                return filtered;
            }
        }
    }

}
