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

package android.inputmethodservice;

import android.content.Context;
import android.util.Log;
import android.util.AttributeSet;
import android.view.inputmethod.ExtractedText;
import android.widget.EditText;
import android.view.inputmethod.EditorInfo;

/***
 * Specialization of {@link EditText} for showing and interacting with the
 * extracted text in a full-screen input method.
 */
public class ExtractEditText extends EditText {
    private InputMethodService mIME;
    private int mSettingExtractedText;
    private int contextMenuClickedId;//add by yangqingan 2011-11-27 for NEWMS00144928
    
    public ExtractEditText(Context context) {
        this(context, null);
    }

    public ExtractEditText(Context context, AttributeSet attrs) {
    	this(context, attrs, com.android.internal.R.attr.editTextStyle);
    }

    public ExtractEditText(Context context, AttributeSet attrs, int defStyle) {
    	super(context, attrs, defStyle);
    }
    
    void setIME(InputMethodService ime) {
        mIME = ime;
    }
    
    /**
     * Start making changes that will not be reported to the client.  That
     * is, {@link #onSelectionChanged(int, int)} will not result in sending
     * the new selection to the client
     */
    public void startInternalChanges() {
        mSettingExtractedText += 1;
    }
    
    /**
     * Finish making changes that will not be reported to the client.  That
     * is, {@link #onSelectionChanged(int, int)} will not result in sending
     * the new selection to the client
     */
    public void finishInternalChanges() {
        mSettingExtractedText -= 1;
    }
    
    /**
     * Implement just to keep track of when we are setting text from the
     * client (vs. seeing changes in ourself from the user).
     */
    @Override public void setExtractedText(ExtractedText text) {
        try {
        	//add by yangqingan 2011-11-27 for NEWMS00144928 begin
        	CharSequence oldText = text.text;
        	if(contextMenuClickedId == ID_START_SELECTING_TEXT || contextMenuClickedId == ID_STOP_SELECTING_TEXT){
        		text.text = null;
            }
            mSettingExtractedText++;
            super.setExtractedText(text);
            contextMenuClickedId = 0;
            text.text = oldText;
          //add by yangqingan 2011-11-27 for NEWMS00144928 end
        } finally {
            mSettingExtractedText--;
        }
    }
    
    /**
     * Report to the underlying text editor about selection changes.
     */
    @Override protected void onSelectionChanged(int selStart, int selEnd) {
        if (mSettingExtractedText == 0 && mIME != null && selStart >= 0 && selEnd >= 0) {
            mIME.onExtractedSelectionChanged(selStart, selEnd);
        }
    }
    
    /**
     * Redirect clicks to the IME for handling there.  First allows any
     * on click handler to run, though.
     */
    @Override public boolean performClick() {
        if (!super.performClick() && mIME != null) {
            mIME.onExtractedTextClicked();
            return true;
        }
        return false;
    }
    
    @Override public boolean onTextContextMenuItem(int id) {
    	contextMenuClickedId = id;
        // Horrible hack: select word option has to be handled by original view to work.
        if (mIME != null && id != android.R.id.startSelectingText) {
            if (mIME.onExtractTextContextMenuItem(id)) {
                return true;
            }
        }
        return super.onTextContextMenuItem(id);
    }
    
    /**
     * We are always considered to be an input method target.
     */
    @Override
    public boolean isInputMethodTarget() {
        return true;
    }
    
    /**
     * Return true if the edit text is currently showing a scroll bar.
     */
    public boolean hasVerticalScrollBar() {
        return computeVerticalScrollRange() > computeVerticalScrollExtent();
    }
    
    /**
     * Pretend like the window this view is in always has focus, so its
     * highlight and cursor will be displayed.
     */
    @Override public boolean hasWindowFocus() {
        return this.isEnabled();
    }

    /**
     * Pretend like this view always has focus, so its
     * highlight and cursor will be displayed.
     */
    @Override public boolean isFocused() {
        return this.isEnabled();
    }

    /**
     * Pretend like this view always has focus, so its
     * highlight and cursor will be displayed.
     */
    @Override public boolean hasFocus() {
        return this.isEnabled();
    }
  //add by yangqingan 2011-11-26 for NEWMS00144694 begin
    @Override
    protected boolean isVisiblePasswordInputType(int inputType) {
    	Log.d("yqa", "==========isVisiblePasswordInputType======" + inputType);
        final int variation = inputType & (EditorInfo.TYPE_MASK_CLASS
                | EditorInfo.TYPE_MASK_VARIATION);
//        return variation
//        == (EditorInfo.TYPE_CLASS_TEXT
//                | EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        boolean b = variation
        == (EditorInfo.TYPE_CLASS_TEXT
                | EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) || variation
                == (EditorInfo.TYPE_CLASS_NUMBER
                        | EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        Log.d("yqa", "==========isPasswordInputType======" + b);
        return b;
    }
    @Override
    protected boolean isPasswordInputType(int inputType) {
    	//add by yangqingan 2011-11-26 for NEWMS00144694 begin
//    	if(password){
//    		return true;
//    	}
    	Log.d("yqa", "==========isPasswordInputType==inputType====" + inputType);
        final int variation = inputType & (EditorInfo.TYPE_MASK_CLASS
                | EditorInfo.TYPE_MASK_VARIATION);
//        final int variation = inputType & EditorInfo.TYPE_TEXT_VARIATION_PASSWORD;
//        return variation
//        == (EditorInfo.TYPE_CLASS_TEXT
//                | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
        boolean b = variation
        == (EditorInfo.TYPE_CLASS_TEXT
                | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD) || variation
                == (EditorInfo.TYPE_CLASS_NUMBER
                        | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
        Log.d("yqa", "==========isPasswordInputType======" + b);
        return b;
//        return variation
//                == (EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
      //add by yangqingan 2011-11-26 for NEWMS00144694 end
      
    }
  //add by yangqingan 2011-11-26 for NEWMS00144694 end
}
