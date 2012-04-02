/*
 * Copyright (C) 2012 Spreadtrum
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

package com.android.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.FileWriter;

import android.os.Binder;
import android.os.Environment;
import android.os.SystemProperties;
import android.util.Slog;

public class AnotherWatchdogService extends Binder {
    private static final String TAG = "AnotherWatchdogService";
    private static final int FEED_THE_DOG = 1;
    private static final int WATCHDOG_FEED_PERIOD = 2*1000; //2sec

    private FileWriter mWatchdogWriter = null;
    private String mWatchdogDevice;
    private Thread mThread;

    public AnotherWatchdogService() {
        this("/dev/watchdog");
    }

    public AnotherWatchdogService(String watchdogDevice) {
        if (watchdogDevice == null) { throw new NullPointerException("watchdogDevice"); }
        mWatchdogDevice = watchdogDevice;

        mThread = new Thread("AnotherWatchDog") {
            public void run() {
                try {
                    File file = new File(mWatchdogDevice);
                    mWatchdogWriter = new FileWriter(file);
                    while (true) {
                        //Slog.v(TAG, "Another Watchdog Looping...");
                        feedWatchdog();
                        try {
                            Thread.sleep(WATCHDOG_FEED_PERIOD);
                        } catch(InterruptedException e) {
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
	        }
            }
        };
        mThread.start();
    }

    private void feedWatchdog() {
        try {
            mWatchdogWriter.write("V\n");
            mWatchdogWriter.flush();
        } catch (IOException e) {
            Slog.w(TAG, "unable to write watchdog", e);
        }
    }
}
