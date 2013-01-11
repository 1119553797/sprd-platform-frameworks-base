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

package com.android.internal.telephony.cat;

import android.util.Log;

public abstract class CatLog {
    static final boolean DEBUG = true;

    public static void d(Object caller, String msg) {
        if (!DEBUG) {
            return;
        }

        String className = caller.getClass().getName();

        if (className.contains("com.android.stk1")) {
            Log.d("STK", "<1>" + className.substring(className.lastIndexOf('.') + 1) + ": " + msg);
        } else if(className.contains("com.android.stk2")) {
            Log.d("STK", "<2>" + className.substring(className.lastIndexOf('.') + 1) + ": " + msg);
        } else if(className.contains("com.android.stk")) {
            Log.d("STK", "<0>" + className.substring(className.lastIndexOf('.') + 1) + ": " + msg);
        }else{
            Log.d("STK", className.substring(className.lastIndexOf('.') + 1) + ": " + msg);
        }
    }

    public static void d(String caller, String msg) {
        if (!DEBUG) {
            return;
        }

        Log.d("STK", caller + ": " + msg);
    }
}
