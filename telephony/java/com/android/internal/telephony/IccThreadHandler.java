package com.android.internal.telephony;

import android.os.Looper;
import android.util.LogPrinter;
import android.os.HandlerThread;
import android.os.Handler;
import android.util.Log;

public class IccThreadHandler extends Handler {
    static HandlerThread sHandlerThread=new HandlerThread("icc handler thread");
    static {
	sHandlerThread.start();
    }
    
    public IccThreadHandler() {
	super(sHandlerThread.getLooper());
    }
    public IccThreadHandler(Looper looper) {
	super(looper);
    }
    
}