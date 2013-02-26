/*
 * Copyright (C) 2007-2008 The Android Open Source Project
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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.EventLog;
import android.util.Slog;
import android.content.BroadcastReceiver;

import android.content.pm.PackageInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.content.pm.IPackageStatsObserver;

import android.content.pm.ParceledListSlice;
import java.util.ArrayList;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


/**
 * This class implements a service to monitor the amount of disk
 * storage space on the device.  If the free storage on device is less
 * than a tunable threshold value (a secure settings parameter;
 * default 10%) a low memory notification is displayed to alert the
 * user. If the user clicks on the low memory notification the
 * Application Manager application gets launched to let the user free
 * storage space.
 *
 * Event log events: A low memory event with the free storage on
 * device in bytes is logged to the event log when the device goes low
 * on storage space.  The amount of free storage on the device is
 * periodically logged to the event log. The log interval is a secure
 * settings parameter with a default value of 12 hours.  When the free
 * storage differential goes below a threshold (again a secure
 * settings parameter with a default value of 2MB), the free memory is
 * logged to the event log.
 */
public class DeviceStorageMonitorService extends Binder {
    private static final String TAG = "DeviceStorageMonitorService";
    private static final boolean DEBUG = false;
    private static final boolean localLOGV = false;
    private static final int DEVICE_MEMORY_WHAT = 1;
    private static final int MONITOR_INTERVAL = 1; //in minutes
    private static final int LOW_MEMORY_NOTIFICATION_ID = 1;
    private static final int DEFAULT_THRESHOLD_PERCENTAGE = 10;
    private static final int DEFAULT_THRESHOLD_MAX_BYTES = 500*1024*1024; // 500MB
    private static final int DEFAULT_FREE_STORAGE_LOG_INTERVAL_IN_MINUTES = 12*60; //in minutes
    private static final long DEFAULT_DISK_FREE_CHANGE_REPORTING_THRESHOLD = 2 * 1024 * 1024; // 2MB
    private static final long DEFAULT_CHECK_INTERVAL = MONITOR_INTERVAL*60*1000;
    private static final int DEFAULT_FULL_THRESHOLD_BYTES = 5*1024*1024; // 1MB
    private long mFreeMem;  // on /data
    private long mLastReportedFreeMem;
    private long mLastReportedFreeMemTime;
    private boolean mLowMemFlag=false;
    private boolean mMemFullFlag=false;
    private Context mContext;
    private ContentResolver mContentResolver;
    private long mTotalMemory;  // on /data
    private StatFs mDataFileStats;
    private StatFs mSystemFileStats;
    private StatFs mCacheFileStats;
    private static final String DATA_PATH = "/data";
    private static final String SYSTEM_PATH = "/system";
    private static final String CACHE_PATH = "/cache";
    private long mThreadStartTime = -1;
    private boolean mClearSucceeded = false;
    private boolean mClearingCache;
    private Intent mStorageLowIntent;
    private Intent mStorageOkIntent;
    private Intent mStorageFullIntent;
    private Intent mStorageNotFullIntent;
    private CachePackageDataObserver mClearCacheObserver;
    private final CacheFileDeletedObserver mCacheFileDeletedObserver;
    private static final int _TRUE = 1;
    private static final int _FALSE = 0;
    private long mMemLowThreshold;
    private int mMemFullThreshold;
    
    private static final int DEFAULT_THRESHOLD_CRITICAL = 10*1024*1024; //10MB
    private boolean mUpdateMemory = false;
    private boolean bootCompleted = false;
    private static final int GET_MEMORY_ERROR = -1;
    private static final int GET_MEMORY_SUCCUESS = 0;
    private long myTotalMemory = 0;
    private long myFreeMemory = 0;
    private long myApplicationMemory = 0;
    private long myMailMemory = 0;
    private long mySmsMmsMemory = 0;
    private long mySystemMemory = 0;
    private boolean mGetMemorySuccuess = true;
    private boolean mIsCheckingMemory = false;
    private boolean mUpdateStorageData = false;


    /**
     * This string is used for ServiceManager access to this class.
     */
    public static final String SERVICE = "devicestoragemonitor";

    /**
    * Handler that checks the amount of disk space on the device and sends a
    * notification if the device runs low on disk space
    */
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            //don't handle an invalid message
            if (msg.what != DEVICE_MEMORY_WHAT) {
                Slog.e(TAG, "Will not process invalid message");
                return;
            }
            final boolean tmp = (msg.arg1 == _TRUE);
            new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        checkMemory(tmp);
                    }
                }
            ).start();
        }
    };

    private final BroadcastReceiver bootCompletedReceiver = new BroadcastReceiver(){

        @Override
        public void onReceive(Context context, Intent intent) {

            if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))
            {
            	bootCompleted = true;
            } else if (Intent.ACTION_SHOW_STORAGE.equals(intent.getAction())){
                //show storage
                postCheckMemoryMsg(true, 0);
            }
        }
    };
    
    class CachePackageDataObserver extends IPackageDataObserver.Stub {
        public void onRemoveCompleted(String packageName, boolean succeeded) {
            mClearSucceeded = succeeded;
            mClearingCache = false;
            if(localLOGV) Slog.i(TAG, " Clear succeeded:"+mClearSucceeded
                    +", mClearingCache:"+mClearingCache+" Forcing memory check");
            postCheckMemoryMsg(false, 0);
        }
    }

    private final void restatDataDir() {
        try {
            mDataFileStats.restat(DATA_PATH);
            mFreeMem = (long) mDataFileStats.getAvailableBlocks() *
                mDataFileStats.getBlockSize();
        } catch (IllegalArgumentException e) {
            // use the old value of mFreeMem
        }
        // Allow freemem to be overridden by debug.freemem for testing
        String debugFreeMem = SystemProperties.get("debug.freemem");
        if (!"".equals(debugFreeMem)) {
            mFreeMem = Long.parseLong(debugFreeMem);
        }
        // Read the log interval from secure settings
        long freeMemLogInterval = Settings.Secure.getLong(mContentResolver,
                Settings.Secure.SYS_FREE_STORAGE_LOG_INTERVAL,
                DEFAULT_FREE_STORAGE_LOG_INTERVAL_IN_MINUTES)*60*1000;
        //log the amount of free memory in event log
        long currTime = SystemClock.elapsedRealtime();
        if((mLastReportedFreeMemTime == 0) ||
           (currTime-mLastReportedFreeMemTime) >= freeMemLogInterval) {
            mLastReportedFreeMemTime = currTime;
            long mFreeSystem = -1, mFreeCache = -1;
            try {
                mSystemFileStats.restat(SYSTEM_PATH);
                mFreeSystem = (long) mSystemFileStats.getAvailableBlocks() *
                    mSystemFileStats.getBlockSize();
            } catch (IllegalArgumentException e) {
                // ignore; report -1
            }
            try {
                mCacheFileStats.restat(CACHE_PATH);
                mFreeCache = (long) mCacheFileStats.getAvailableBlocks() *
                    mCacheFileStats.getBlockSize();
            } catch (IllegalArgumentException e) {
                // ignore; report -1
            }
            EventLog.writeEvent(EventLogTags.FREE_STORAGE_LEFT,
                                mFreeMem, mFreeSystem, mFreeCache);
        }
        // Read the reporting threshold from secure settings
        long threshold = Settings.Secure.getLong(mContentResolver,
                Settings.Secure.DISK_FREE_CHANGE_REPORTING_THRESHOLD,
                DEFAULT_DISK_FREE_CHANGE_REPORTING_THRESHOLD);
        // If mFree changed significantly log the new value
        long delta = mFreeMem - mLastReportedFreeMem;
        if (delta > threshold || delta < -threshold) {
            mLastReportedFreeMem = mFreeMem;
            EventLog.writeEvent(EventLogTags.FREE_STORAGE_CHANGED, mFreeMem);
        }
    }

    private final void clearCache() {
        if (mClearCacheObserver == null) {
            // Lazy instantiation
            mClearCacheObserver = new CachePackageDataObserver();
        }
        mClearingCache = true;
        try {
            if (localLOGV) Slog.i(TAG, "Clearing cache");
            IPackageManager.Stub.asInterface(ServiceManager.getService("package")).
                    freeStorageAndNotify(mMemLowThreshold, mClearCacheObserver);
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed to get handle for PackageManger Exception: "+e);
            mClearingCache = false;
            mClearSucceeded = false;
        }
    }

    private final void checkMemory(boolean checkCache) {
        if(mIsCheckingMemory == true) {
            Slog.i(TAG, "other thread is checking memory now ,return");
            return;
        }
        mIsCheckingMemory = true;

        //if the thread that was started to clear cache is still running do nothing till its
        //finished clearing cache. Ideally this flag could be modified by clearCache
        // and should be accessed via a lock but even if it does this test will fail now and
        //hopefully the next time this flag will be set to the correct value.
        if(mClearingCache) {
            if(localLOGV) Slog.i(TAG, "Thread already running just skip");
            //make sure the thread is not hung for too long
            long diffTime = System.currentTimeMillis() - mThreadStartTime;
            if(diffTime > (10*60*1000)) {
                Slog.w(TAG, "Thread that clears cache file seems to run for ever");
            }
        } else {
            restatDataDir();
            if (localLOGV)  Slog.v(TAG, "freeMemory="+mFreeMem);

            //post intent to NotificationManager to display icon if necessary
            if (mFreeMem < mMemLowThreshold) {
                if (!mUpdateMemory && mFreeMem < DEFAULT_THRESHOLD_CRITICAL) {
                    Slog.w(TAG, "Risk, Running low on memory. Start activity automaticly.");   
                    startShowStorageActivity();
                }

                if (mFreeMem >= DEFAULT_THRESHOLD_CRITICAL) {
                    getShowStorageData();//get data for Notification
                }

                mUpdateMemory = false;
                mUpdateStorageData = false;

                if (!mLowMemFlag) {
                    if (checkCache) {
                        // See if clearing cache helps
                        // Note that clearing cache is asynchronous and so we do a
                        // memory check again once the cache has been cleared.
                        mThreadStartTime = System.currentTimeMillis();
                        mClearSucceeded = false;
                        clearCache();
                    } else {
                        Slog.i(TAG, "Running low on memory. Sending notification");
                        sendNotification();
                        mLowMemFlag = true;
                    }
                } else {
                    if (localLOGV) Slog.v(TAG, "Running low on memory " +
                            "notification already sent. do nothing");
                }
            } else {
                if (mUpdateStorageData) {
                    Slog.i(TAG, "update StorageData");
                    mUpdateStorageData = false;
                    getShowStorageData();
                }
                if (mLowMemFlag) {
                    Slog.i(TAG, "Memory available. Cancelling notification");
                    cancelNotification();
                    mLowMemFlag = false;
                }
            }
            if (mFreeMem < mMemFullThreshold) {
                if (!mMemFullFlag) {
                    sendFullNotification();
                    mMemFullFlag = true;
                }
            } else {
                if (mMemFullFlag) {
                    cancelFullNotification();
                    mMemFullFlag = false;
                }
            }
        }
        if(localLOGV) Slog.i(TAG, "Posting Message again");
        //keep posting messages to itself periodically
        postCheckMemoryMsg(true, DEFAULT_CHECK_INTERVAL);
        mIsCheckingMemory = false;
    }

    private void postCheckMemoryMsg(boolean clearCache, long delay) {
        // Remove queued messages
        mHandler.removeMessages(DEVICE_MEMORY_WHAT);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(DEVICE_MEMORY_WHAT,
                clearCache ?_TRUE : _FALSE, 0),
                delay);
    }

    /*
     * just query settings to retrieve the memory threshold.
     * Preferred this over using a ContentObserver since Settings.Secure caches the value
     * any way
     */
    private long getMemThreshold() {
        long value = Settings.Secure.getInt(
                              mContentResolver,
                              Settings.Secure.SYS_STORAGE_THRESHOLD_PERCENTAGE,
                              DEFAULT_THRESHOLD_PERCENTAGE);
        if(localLOGV) Slog.v(TAG, "Threshold Percentage="+value);
        value *= mTotalMemory;
        long maxValue = Settings.Secure.getInt(
                mContentResolver,
                Settings.Secure.SYS_STORAGE_THRESHOLD_MAX_BYTES,
                DEFAULT_THRESHOLD_MAX_BYTES);
        //evaluate threshold value
        return value < maxValue ? value : maxValue;
    }

    /*
     * just query settings to retrieve the memory full threshold.
     * Preferred this over using a ContentObserver since Settings.Secure caches the value
     * any way
     */
    private int getMemFullThreshold() {
        int value = Settings.Secure.getInt(
                              mContentResolver,
                              Settings.Secure.SYS_STORAGE_FULL_THRESHOLD_BYTES,
                              DEFAULT_FULL_THRESHOLD_BYTES);
        if(localLOGV) Slog.v(TAG, "Full Threshold Bytes="+value);
        return value;
    }

    /**
    * Constructor to run service. initializes the disk space threshold value
    * and posts an empty message to kickstart the process.
    */
    public DeviceStorageMonitorService(Context context) {
        mLastReportedFreeMemTime = 0;
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        //create StatFs object
        mDataFileStats = new StatFs(DATA_PATH);
        mSystemFileStats = new StatFs(SYSTEM_PATH);
        mCacheFileStats = new StatFs(CACHE_PATH);
        //initialize total storage on device
        mTotalMemory = ((long)mDataFileStats.getBlockCount() *
                        mDataFileStats.getBlockSize())/100L;
        mStorageLowIntent = new Intent(Intent.ACTION_DEVICE_STORAGE_LOW);
        mStorageLowIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mStorageOkIntent = new Intent(Intent.ACTION_DEVICE_STORAGE_OK);
        mStorageOkIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mStorageFullIntent = new Intent(Intent.ACTION_DEVICE_STORAGE_FULL);
        mStorageFullIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mStorageNotFullIntent = new Intent(Intent.ACTION_DEVICE_STORAGE_NOT_FULL);
        mStorageNotFullIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);

		//merge from 2.3.5 for create temp file
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        filter.addAction(Intent.ACTION_SHOW_STORAGE);
        mContext.registerReceiver(bootCompletedReceiver, filter);
		
        // cache storage thresholds
        mMemLowThreshold = getMemThreshold();
        mMemFullThreshold = getMemFullThreshold();

        postCheckMemoryMsg(true, 0);
        //checkMemory(true);

        mCacheFileDeletedObserver = new CacheFileDeletedObserver();
        mCacheFileDeletedObserver.startWatching();
    }


    /**
    * This method sends a notification to NotificationManager to display
    * an error dialog indicating low disk space and launch the Installer
    * application
    */
    private final void sendNotification() {
        if(localLOGV) Slog.i(TAG, "Sending low memory notification");
        //log the event to event log with the amount of free storage(in bytes) left on the device
        EventLog.writeEvent(EventLogTags.LOW_STORAGE, mFreeMem);

        //  Pack up the values and broadcast them to everyone
        /*Intent lowMemIntent = new Intent(Environment.isExternalStorageEmulated()
                ? Settings.ACTION_INTERNAL_STORAGE_SETTINGS
                : Intent.ACTION_MANAGE_PACKAGE_STORAGE);
        lowMemIntent.putExtra("memory", mFreeMem);*/


        Intent lowMemIntent = new Intent(mContext, com.android.server.ShowStorage.class);

        lowMemIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        NotificationManager mNotificationMgr =
                (NotificationManager)mContext.getSystemService(
                        Context.NOTIFICATION_SERVICE);
        CharSequence title = mContext.getText(
                com.android.internal.R.string.low_internal_storage_view_title);
        CharSequence details = mContext.getText(
                com.android.internal.R.string.low_internal_storage_view_text);

        PendingIntent intent = PendingIntent.getActivity(mContext, 0,  lowMemIntent, 0);
        
        Notification notification = new Notification();
        notification.icon = com.android.internal.R.drawable.stat_notify_disk_full;
        notification.tickerText = title;
        notification.flags |= Notification.FLAG_NO_CLEAR;
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        notification.setLatestEventInfo(mContext, title, details, intent);
        mNotificationMgr.notify(LOW_MEMORY_NOTIFICATION_ID, notification);
        mContext.sendStickyBroadcast(mStorageLowIntent);
    }

    /**
     * Cancels low storage notification and sends OK intent.
     */
    private final void cancelNotification() {
        if(localLOGV) Slog.i(TAG, "Canceling low memory notification");
        NotificationManager mNotificationMgr =
                (NotificationManager)mContext.getSystemService(
                        Context.NOTIFICATION_SERVICE);
        //cancel notification since memory has been freed
        mNotificationMgr.cancel(LOW_MEMORY_NOTIFICATION_ID);

        mContext.removeStickyBroadcast(mStorageLowIntent);
        mContext.sendBroadcast(mStorageOkIntent);
    }

    /**
     * Send a notification when storage is full.
     */
    private final void sendFullNotification() {
        if(localLOGV) Slog.i(TAG, "Sending memory full notification");
        mContext.sendStickyBroadcast(mStorageFullIntent);
    }

    /**
     * Cancels memory full notification and sends "not full" intent.
     */
    private final void cancelFullNotification() {
        if(localLOGV) Slog.i(TAG, "Canceling memory full notification");
        mContext.removeStickyBroadcast(mStorageFullIntent);
        mContext.sendBroadcast(mStorageNotFullIntent);
    }

    public void updateMemory() {
        int callingUid = getCallingUid();
        if(callingUid != Process.SYSTEM_UID) {
            return;
        }
        mUpdateMemory = true;
        // force an early check
        postCheckMemoryMsg(true, 0);
    }

   private void getShowStorageData() {
        // TODO Auto-generated method stub
        final int mKiloSize = 1024;
        int ret = 0;

        try {
            mDataFileStats.restat(DATA_PATH);
            mFreeMem = (long) mDataFileStats.getAvailableBlocks()
              *  mDataFileStats.getBlockSize();
        } catch (IllegalArgumentException e) {
            // use the old value of mFreeMem
        }

        myTotalMemory = (long) (mDataFileStats.getBlockCount() * mDataFileStats.getBlockSize()) / mKiloSize;

        myFreeMemory = mFreeMem / mKiloSize;

        ret = getApplicationSize(); // get Application Mail SmsMms Size
        if (ret == GET_MEMORY_ERROR) {
            Slog.e(TAG, "Get (MailMemory SmsMmsMemory ApplicationMemory) Error");
        }
        mGetMemorySuccuess = (ret == GET_MEMORY_SUCCUESS);
        mySystemMemory = myTotalMemory - myFreeMemory - myMailMemory - mySmsMmsMemory
                - myApplicationMemory;
    }

    private Intent createShowStorageIntent(boolean hasData){
        Intent showStorageIntent = null;
        getShowStorageData();
        if (mGetMemorySuccuess) {
            showStorageIntent = new Intent(mContext,com.android.server.ShowStorage.class);
            showStorageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            //showStorageIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

            if (hasData) {
                final int mTOTALLOCATION = 0;
                final int mFREELOCATION = 1;
                final int mAPPLICATIONLOCATION = 2;
                final int mMAILLOCATION = 3;
                final int mSMSMMSLOCATION = 4;
                final int mSYSTEMLOCATION = 5;
                final int mELEMENTCOUNT = 6;

                long mySize[] = new long[mELEMENTCOUNT];
                mySize[mTOTALLOCATION] = myTotalMemory;
                mySize[mFREELOCATION] = myFreeMemory;
                mySize[mAPPLICATIONLOCATION] = myApplicationMemory;
                mySize[mMAILLOCATION] = myMailMemory;
                mySize[mSMSMMSLOCATION] = mySmsMmsMemory;
                mySize[mSYSTEMLOCATION] = mySystemMemory;

                Bundle data = new Bundle();
                data.putSerializable("lastSize", mySize);
                showStorageIntent.putExtras(data);
            }
        }
        return showStorageIntent;
    }

    private void startShowStorageActivity(){
        if (bootCompleted) {
            Intent showStorageIntent = createShowStorageIntent(true);
            if (showStorageIntent != null) {
                mContext.startActivity(showStorageIntent);
            }
        }
    }

    /** 
     * This class implements a service to observer the action of get PackageSize
     */
    private class SizeObserver extends IPackageStatsObserver.Stub {
        private CountDownLatch mCount;

        PackageStats stats;

        boolean succeeded;

        public void invokeGetSize(String packageName, CountDownLatch count) {
            mCount = count;
            try {
                myPm.getPackageSizeInfo(packageName, this);
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                Slog.e(TAG, "RemoteException e");
            }
        }

        public void onGetStatsCompleted(PackageStats pStats, boolean pSucceeded) {
            succeeded = pSucceeded;
            stats = pStats;
            mCount.countDown();
        }
    }

    IPackageManager myPm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
    
    private int getApplicationSize() {

        long myApplicationSize = 0;
        long myMailSize = 0;
        long mySmsMmsSize = 0;
        final String mMAILPACKAGE = "com.android.email";
        final String mMMSPACKAGE = "com.android.mms";

        PackageStats myPackageStats;
        SizeObserver mSizeObserver = new SizeObserver();

        if (myPm == null) {
            Slog.e(TAG, "PM service is not running");
            return GET_MEMORY_ERROR;
        }

//        try {
        	/*
            String lastread = null;
            ParceledListSlice<PackageInfo> slice = myPm.getInstalledPackages(PackageManager.GET_UNINSTALLED_PACKAGES, lastread);
            final List<PackageInfo> packages = new ArrayList<PackageInfo>();
            slice.populateList(packages, PackageInfo.CREATOR);
        	*/ 
        //bug#12297
        	final List<PackageInfo> packages = getInstalledPackages(PackageManager.GET_UNINSTALLED_PACKAGES);
            int count = packages.size();

            for (int p = 0; p < count; p++) {
                PackageInfo info = packages.get(p);
                
                if(DEBUG)Slog.w(TAG, "package:["+info.packageName+"]");

                if("android".equals(info.packageName)) {
                    continue;
                }

                if ((info.applicationInfo.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0) {
                    continue;
                } else {
                    CountDownLatch count1 = new CountDownLatch(1);
                    mSizeObserver.invokeGetSize(info.packageName, count1);

                    try {
                        count1.await(500, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Slog.e(TAG, "Failed computing size for pkg : " + info.packageName);
                    }

                    // Process the package statistics
                    myPackageStats = mSizeObserver.stats;
                    boolean succeeded = mSizeObserver.succeeded;
                    if (myPackageStats == null) {
                        if (succeeded)
                            Slog.v(TAG, "Failed getting size for pkg : " + info.packageName);
                        else
                            Slog.v(TAG, "Time out getting size for pkg : " + info.packageName);
                        return GET_MEMORY_ERROR;
                    }

                    if ((info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                        if ( mMAILPACKAGE.equals(info.packageName)) {
                            myMailSize += myPackageStats.dataSize;
                            continue;
                        }
                        if (mMMSPACKAGE.equals(info.packageName)) {
                            mySmsMmsSize += myPackageStats.dataSize;
                            continue;
                        }
                        myApplicationSize += myPackageStats.dataSize;

                    } else {
                        if (mMAILPACKAGE.equals(info.packageName)) {
                            myMailSize += myPackageStats.dataSize + myPackageStats.codeSize;
                            continue;
                        }
                        if (mMMSPACKAGE.equals(info.packageName)) {
                            mySmsMmsSize += myPackageStats.dataSize + myPackageStats.codeSize;
                            continue;
                        }
                        myApplicationSize += myPackageStats.dataSize + myPackageStats.codeSize;

                    }
                }
            }
//        } catch (RemoteException e) {
//            Slog.e(TAG, "PM service is not running");
//        }
        final int mKiloSize = 1024;
        myApplicationMemory = myApplicationSize / mKiloSize;
        myMailMemory = myMailSize / mKiloSize;
        mySmsMmsMemory = mySmsMmsSize / mKiloSize;
        if(DEBUG)Slog.w(TAG, "myMailMemory :"+myMailMemory+" KB , mySmsMmsMemory :"+mySmsMmsMemory+" KB");
        return GET_MEMORY_SUCCUESS;
    }
    
    public List<PackageInfo> getInstalledPackages(int flags) {
        try {
            final List<PackageInfo> packageInfos = new ArrayList<PackageInfo>();
            PackageInfo lastItem = null;
            ParceledListSlice<PackageInfo> slice;

            do {
                final String lastKey = lastItem != null ? lastItem.packageName : null;
                slice = myPm.getInstalledPackages(flags, lastKey);
                lastItem = slice.populateList(packageInfos, PackageInfo.CREATOR);
            } while (!slice.isLastSlice());

            return packageInfos;
        } catch (RemoteException e) {
            throw new RuntimeException("Package manager has died", e);
        }
    }
    
    public void setUpdateStorageDataFlag(boolean updateFlag) {
        mUpdateStorageData = updateFlag;
        postCheckMemoryMsg(true, 0);
    }

    public long[] callOK() {

        final int mTOTALLOCATION = 0;
        final int mFREELOCATION = 1;
        final int mAPPLICATIONLOCATION = 2;
        final int mMAILLOCATION = 3;
        final int mSMSMMSLOCATION = 4;
        final int mSYSTEMLOCATION = 5;
        final int mELEMENTCOUNT = 6;

        Slog.i(TAG, "remote call is accepted callOK");
        //getShowStorageData();

        long mySize[] = new long[mELEMENTCOUNT];
        mySize[mTOTALLOCATION] = myTotalMemory;
        mySize[mFREELOCATION] = myFreeMemory;
        mySize[mAPPLICATIONLOCATION] = myApplicationMemory;
        mySize[mMAILLOCATION] = myMailMemory;
        mySize[mSMSMMSLOCATION] = mySmsMmsMemory;
        mySize[mSYSTEMLOCATION] = mySystemMemory;

        return mySize;
    }

    public boolean getCallOKSuccuess() {
        return mGetMemorySuccuess;
    }

    /**
     * Callable from other things in the system service to obtain the low memory
     * threshold.
     * 
     * @return low memory threshold in bytes
     */
    public long getMemoryLowThreshold() {
        return mMemLowThreshold;
    }

    /**
     * Callable from other things in the system process to check whether memory
     * is low.
     * 
     * @return true is memory is low
     */
    public boolean isMemoryLow() {
        return mLowMemFlag;
    }

    public static class CacheFileDeletedObserver extends FileObserver {
        public CacheFileDeletedObserver() {
            super(Environment.getDownloadCacheDirectory().getAbsolutePath(), FileObserver.DELETE);
        }

        @Override
        public void onEvent(int event, String path) {
            EventLogTags.writeCacheFileDeleted(path);
        }
    }
}
