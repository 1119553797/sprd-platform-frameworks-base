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
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Debug;
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
import android.os.UserHandle;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.text.format.Formatter;
import android.util.EventLog;
import android.util.Slog;
import android.util.TimeUtils;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
/* SPRD: add low mem alert @{ */
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
/* @} */

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

    private static final boolean DEBUG = Debug.isDebug();

    private static final boolean localLOGV = false;

    private static final int DEVICE_MEMORY_WHAT = 1;
    private static final int MONITOR_INTERVAL = 1; //in minutes
    private static final int LOW_MEMORY_NOTIFICATION_ID = 1;

    private static final int DEFAULT_FREE_STORAGE_LOG_INTERVAL_IN_MINUTES = 12*60; //in minutes
    private static final long DEFAULT_DISK_FREE_CHANGE_REPORTING_THRESHOLD = 2 * 1024 * 1024; // 2MB
    private static final long DEFAULT_CHECK_INTERVAL = MONITOR_INTERVAL*60*1000;
    // SPRD: add for low mem alert
    private static final int DEFAULT_FULL_THRESHOLD_BYTES = 5*1024*1024; // 1MB
    private long mFreeMem;  // on /data
    private long mFreeMemAfterLastCacheClear;  // on /data
    private long mLastReportedFreeMem;
    private long mLastReportedFreeMemTime;
    private boolean mLowMemFlag=false;
    private boolean mMemFullFlag=false;
    private Context mContext;
    private ContentResolver mResolver;
    private long mTotalMemory;  // on /data
    private StatFs mDataFileStats;
    private StatFs mSystemFileStats;
    private StatFs mCacheFileStats;

    private static final File DATA_PATH = Environment.getDataDirectory();
    private static final File SYSTEM_PATH = Environment.getRootDirectory();
    private static final File CACHE_PATH = Environment.getDownloadCacheDirectory();

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
    // This is the raw threshold that has been set at which we consider
    // storage to be low.
    private long mMemLowThreshold;

    // This is the threshold at which we start trying to flush caches
    // to get below the low threshold limit.  It is less than the low
    // threshold; we will allow storage to get a bit beyond the limit
    // before flushing and checking if we are actually low.
    private long mMemCacheStartTrimThreshold;
    // This is the threshold that we try to get to when deleting cache
    // files.  This is greater than the low threshold so that we will flush
    // more files than absolutely needed, to reduce the frequency that
    // flushing takes place.
    private long mMemCacheTrimToThreshold;
    private long mMemFullThreshold;
    /* SPRD: add low mem alert @{ */
    private static final int DEFAULT_THRESHOLD_CRITICAL = 10*1024*1024; //10MB
    private boolean mUpdateMemory = false;
    private boolean bootCompleted = false;
    private static final int GET_MEMORY_ERROR = -1;
    private static final int GET_MEMORY_SUCCUESS = 0;
    private boolean mIsCheckingMemory = false;
    private boolean mUpdateStorageData = false;
    /* @} */

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
            /* SPRD: modify for adding low mem alert @{ */
            final boolean tmp = (msg.arg1 == _TRUE);
            new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        checkMemory(tmp);
                    }
                }
            ).start();
            /* @} */
        }
    };
    /* SPRD: add low mem alert @{ */
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
    /* @} */

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
            mDataFileStats.restat(DATA_PATH.getAbsolutePath());
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
        long freeMemLogInterval = Settings.Global.getLong(mResolver,
                Settings.Global.SYS_FREE_STORAGE_LOG_INTERVAL,
                DEFAULT_FREE_STORAGE_LOG_INTERVAL_IN_MINUTES)*60*1000;
        //log the amount of free memory in event log
        long currTime = SystemClock.elapsedRealtime();
        if((mLastReportedFreeMemTime == 0) ||
           (currTime-mLastReportedFreeMemTime) >= freeMemLogInterval) {
            mLastReportedFreeMemTime = currTime;
            long mFreeSystem = -1, mFreeCache = -1;
            try {
                mSystemFileStats.restat(SYSTEM_PATH.getAbsolutePath());
                mFreeSystem = (long) mSystemFileStats.getAvailableBlocks() *
                    mSystemFileStats.getBlockSize();
            } catch (IllegalArgumentException e) {
                // ignore; report -1
            }
            try {
                mCacheFileStats.restat(CACHE_PATH.getAbsolutePath());
                mFreeCache = (long) mCacheFileStats.getAvailableBlocks() *
                    mCacheFileStats.getBlockSize();
            } catch (IllegalArgumentException e) {
                // ignore; report -1
            }
            EventLog.writeEvent(EventLogTags.FREE_STORAGE_LEFT,
                                mFreeMem, mFreeSystem, mFreeCache);
        }
        // Read the reporting threshold from secure settings
        long threshold = Settings.Global.getLong(mResolver,
                Settings.Global.DISK_FREE_CHANGE_REPORTING_THRESHOLD,
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
                    freeStorageAndNotify(mMemCacheTrimToThreshold, mClearCacheObserver);
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed to get handle for PackageManger Exception: "+e);
            mClearingCache = false;
            mClearSucceeded = false;
        }
    }

    private final void checkMemory(boolean checkCache) {
        /* SPRD: add low mem alert @{ */
        if(mIsCheckingMemory == true) {
            Slog.i(TAG, "other thread is checking memory now ,return");
            return;
        }
        mIsCheckingMemory = true;
        /* @} */

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
                /* SPRD: add low mem alert @{ */
                if (!mUpdateMemory && mFreeMem < DEFAULT_THRESHOLD_CRITICAL) {
                    Slog.w(TAG, "Risk, Running low on memory. Start activity automaticly.");   
                    startShowStorageActivity();
                }
                
                mUpdateMemory = false;
                /* @} */

                if (checkCache) {
                    // We are allowed to clear cache files at this point to
                    // try to get down below the limit, because this is not
                    // the initial call after a cache clear has been attempted.
                    // In this case we will try a cache clear if our free
                    // space has gone below the cache clear limit.
                    if (mFreeMem < mMemCacheStartTrimThreshold) {
                        // We only clear the cache if the free storage has changed
                        // a significant amount since the last time.
                        if ((mFreeMemAfterLastCacheClear-mFreeMem)
                                >= ((mMemLowThreshold-mMemCacheStartTrimThreshold)/4)) {
                            // See if clearing cache helps
                            // Note that clearing cache is asynchronous and so we do a
                            // memory check again once the cache has been cleared.
                            mThreadStartTime = System.currentTimeMillis();
                            mClearSucceeded = false;
                            clearCache();
                        }
                    }
                } else {
                    // This is a call from after clearing the cache.  Note
                    // the amount of free storage at this point.
                    mFreeMemAfterLastCacheClear = mFreeMem;
                    if (!mLowMemFlag) {
                        // We tried to clear the cache, but that didn't get us
                        // below the low storage limit.  Tell the user.
                        Slog.i(TAG, "Running low on memory. Sending notification");
                        sendNotification();
                        mLowMemFlag = true;
                    } else {
                        if (localLOGV) Slog.v(TAG, "Running low on memory " +
                                "notification already sent. do nothing");
                    }
                }
            } else {
                mFreeMemAfterLastCacheClear = mFreeMem;

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
        // SPRD: add low mem alert
        mIsCheckingMemory = false;
    }

    private void postCheckMemoryMsg(boolean clearCache, long delay) {
        // Remove queued messages
        mHandler.removeMessages(DEVICE_MEMORY_WHAT);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(DEVICE_MEMORY_WHAT,
                clearCache ?_TRUE : _FALSE, 0),
                delay);
    }

    /**
    * Constructor to run service. initializes the disk space threshold value
    * and posts an empty message to kickstart the process.
    */
    public DeviceStorageMonitorService(Context context) {
        mLastReportedFreeMemTime = 0;
        mContext = context;
        mResolver = mContext.getContentResolver();
        //create StatFs object
        mDataFileStats = new StatFs(DATA_PATH.getAbsolutePath());
        mSystemFileStats = new StatFs(SYSTEM_PATH.getAbsolutePath());
        mCacheFileStats = new StatFs(CACHE_PATH.getAbsolutePath());
        //initialize total storage on device
        mTotalMemory = (long)mDataFileStats.getBlockCount() *
                        mDataFileStats.getBlockSize();
        mStorageLowIntent = new Intent(Intent.ACTION_DEVICE_STORAGE_LOW);
        mStorageLowIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mStorageOkIntent = new Intent(Intent.ACTION_DEVICE_STORAGE_OK);
        mStorageOkIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mStorageFullIntent = new Intent(Intent.ACTION_DEVICE_STORAGE_FULL);
        mStorageFullIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mStorageNotFullIntent = new Intent(Intent.ACTION_DEVICE_STORAGE_NOT_FULL);
        mStorageNotFullIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);

        // cache storage thresholds
        final StorageManager sm = StorageManager.from(context);
        mMemLowThreshold = sm.getStorageLowBytes(DATA_PATH);
        mMemFullThreshold = sm.getStorageFullBytes(DATA_PATH);

        mMemCacheStartTrimThreshold = ((mMemLowThreshold*3)+mMemFullThreshold)/4;
        mMemCacheTrimToThreshold = mMemLowThreshold
                + ((mMemLowThreshold-mMemCacheStartTrimThreshold)*2);
        mFreeMemAfterLastCacheClear = mTotalMemory;

        /* SPRD: add low mem alert @{ */
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        filter.addAction(Intent.ACTION_SHOW_STORAGE);
        mContext.registerReceiver(bootCompletedReceiver, filter);

        postCheckMemoryMsg(true, 0);
        //checkMemory(true);
        /* @} */

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

        /* SPRD: Pack up the values and broadcast them to everyone @{ */
        /*Intent lowMemIntent = new Intent(Environment.isExternalStorageEmulated()
                ? Settings.ACTION_INTERNAL_STORAGE_SETTINGS
                : Intent.ACTION_MANAGE_PACKAGE_STORAGE);
        lowMemIntent.putExtra("memory", mFreeMem);*/


        Intent lowMemIntent = new Intent(mContext, com.android.server.ShowStorage.class);
        /* @} */

        lowMemIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        NotificationManager mNotificationMgr =
                (NotificationManager)mContext.getSystemService(
                        Context.NOTIFICATION_SERVICE);
        CharSequence title = mContext.getText(
                com.android.internal.R.string.low_internal_storage_view_title);
        CharSequence details = mContext.getText(
                com.android.internal.R.string.low_internal_storage_view_text);

        PendingIntent intent = PendingIntent.getActivityAsUser(mContext, 0,  lowMemIntent, 0,
                null, UserHandle.CURRENT);

        Notification notification = new Notification();
        notification.icon = com.android.internal.R.drawable.stat_notify_disk_full;
        notification.tickerText = title;
        notification.flags |= Notification.FLAG_NO_CLEAR;
        // SPRD: add low mem alert
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        notification.setLatestEventInfo(mContext, title, details, intent);
        mNotificationMgr.notifyAsUser(null, LOW_MEMORY_NOTIFICATION_ID, notification,
                UserHandle.ALL);
        mContext.sendStickyBroadcastAsUser(mStorageLowIntent, UserHandle.ALL);
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
        mNotificationMgr.cancelAsUser(null, LOW_MEMORY_NOTIFICATION_ID, UserHandle.ALL);

        mContext.removeStickyBroadcastAsUser(mStorageLowIntent, UserHandle.ALL);
        mContext.sendBroadcastAsUser(mStorageOkIntent, UserHandle.ALL);
    }

    /**
     * Send a notification when storage is full.
     */
    private final void sendFullNotification() {
        if(localLOGV) Slog.i(TAG, "Sending memory full notification");
        mContext.sendStickyBroadcastAsUser(mStorageFullIntent, UserHandle.ALL);
    }

    /**
     * Cancels memory full notification and sends "not full" intent.
     */
    private final void cancelFullNotification() {
        if(localLOGV) Slog.i(TAG, "Canceling memory full notification");
        mContext.removeStickyBroadcastAsUser(mStorageFullIntent, UserHandle.ALL);
        mContext.sendBroadcastAsUser(mStorageNotFullIntent, UserHandle.ALL);
    }

    public void updateMemory() {
        int callingUid = getCallingUid();
        if(callingUid != Process.SYSTEM_UID) {
            return;
        }
        // SPRD: add low mem alert
        mUpdateMemory = true;
        // force an early check
        postCheckMemoryMsg(true, 0);
    }
    /* SPRD: add low mem alert @{*/
    private void startShowStorageActivity(){
        if (bootCompleted) {
            Intent showStorageIntent = new Intent(mContext,com.android.server.ShowStorage.class);
            showStorageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            mContext.startActivity(showStorageIntent);
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
                myPm.getPackageSizeInfo(packageName, UserHandle.USER_ALL, this);
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
    
    public List<PackageInfo> getInstalledPackages(int flags) {
            ParceledListSlice<PackageInfo> slice;
            try {
                slice = myPm.getInstalledPackages(flags, UserHandle.USER_ALL);
            } catch (Exception e) {
                Slog.e(TAG,"getInstalledPackages error");
                return null;
            }
            return slice.getList();
    }
    
    public void setUpdateStorageDataFlag(boolean updateFlag) {
        mUpdateStorageData = updateFlag;
        postCheckMemoryMsg(true, 0);
    }
    private static GetShowStorageData gssdInstance = null;

    private class GetShowStorageData implements Runnable {
        long myApplicationSize = 0;
        long myMailSize = 0;
        long mySmsMmsSize = 0;
        final String mMAILPACKAGE = "com.android.email";
        final String mMMSPACKAGE = "com.android.mms";
        List<PackageInfo> packages = null;
        int pkgsCount = 0;
        int pkgIndex = -1;
        PackageInfo pkgInfo;
        CountDownLatch count1 = new CountDownLatch(1);

        private long myTotalMemory = 0;
        private long myFreeMemory = 0;
        private long myApplicationMemory = 0;
        private long myMailMemory = 0;
        private long mySmsMmsMemory = 0;
        private long mySystemMemory = 0;

        static final int GET_SIZE_NEXT = 0;
        static final int GET_SIZE_WAIT = 1;
        static final int GET_SIZE_OK = 2;
        int mGetSizeState = GET_SIZE_NEXT;

        private boolean mRunning = false;

        PackageStats myPackageStats;
        SizeObserver mSizeObserver = new SizeObserver();

        private GetShowStorageData() {
        }

        synchronized public long[] result() {
            if (mRunning) {
                return null;
            }

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

            return mySize;
        }

        synchronized public void start() {
            if(DEBUG)Slog.w(TAG, "start() mRunning is " + mRunning);
            if (mRunning)
                return;
            mRunning = true;
            myTotalMemory = -1;
            myFreeMemory = 0;
            if(DEBUG)Slog.w(TAG, "really start()");

            try {
                mDataFileStats.restat(DATA_PATH.getAbsolutePath());
                myFreeMemory = (long) mDataFileStats.getAvailableBlocks() *  mDataFileStats.getBlockSize();
                myTotalMemory = (long) (mDataFileStats.getBlockCount() * mDataFileStats.getBlockSize());
            } catch (IllegalArgumentException e) {
                // use the old value of mFreeMem
            }

            myApplicationSize = 0;
            myMailSize = 0;
            mySmsMmsSize = 0;
            packages = getInstalledPackages(PackageManager.GET_UNINSTALLED_PACKAGES);
            pkgsCount = packages.size();
            pkgIndex = -1;
            mGetSizeState = GET_SIZE_NEXT;
            nextRun();
        }

        synchronized private void end() {
            if(DEBUG)Slog.w(TAG, "end() mRunning is " + mRunning);
            myApplicationMemory = myApplicationSize;
            myMailMemory = myMailSize;
            mySmsMmsMemory = mySmsMmsSize;
            mySystemMemory = myTotalMemory - myFreeMemory - myMailMemory - mySmsMmsMemory
                             - myApplicationMemory;
            if(DEBUG)Slog.w(TAG, "end()[myFreeMemory:"+myFreeMemory+
                                "myApplicationMemory:"+myApplicationMemory+
                                ",myMailMemory:"+myMailMemory+
                                ",mySmsMmsMemory:"+mySmsMmsMemory+
                                ",mySystemMemory:"+mySystemMemory+"]");
            if(mySystemMemory < 0)
            {
                mySystemMemory = 0;
            }
            mRunning = false;
        }

        private void nextRun() {
            mHandler.post(this);
        }

        public void run() {
            if (mGetSizeState == GET_SIZE_NEXT) {
                pkgIndex++;
                if (pkgIndex >= pkgsCount) {
                    end();
                    return;
                }
                pkgInfo = packages.get(pkgIndex);

                if(DEBUG)Slog.w(TAG, "package:["+pkgInfo.packageName+"]");

                if("android".equals(pkgInfo.packageName)) {
                    nextRun();
                    return;
                }

                if ((pkgInfo.applicationInfo.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0) {
                    nextRun();
                    return;
                }
                count1 = new CountDownLatch(1);
                mSizeObserver.invokeGetSize(pkgInfo.packageName, count1);
                mGetSizeState = GET_SIZE_WAIT;
            }
            if (mGetSizeState == GET_SIZE_WAIT) {
                boolean waitOK = true;
                try {
                    waitOK = count1.await(500, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Slog.e(TAG, "Failed computing size for pkg : " + pkgInfo.packageName);
                }
                if (waitOK) {
                    mGetSizeState = GET_SIZE_OK;
                }
            }
            if (mGetSizeState == GET_SIZE_OK && pkgInfo != null) {
                // Process the package statistics
                myPackageStats = mSizeObserver.stats;
                boolean succeeded = mSizeObserver.succeeded;
                if (myPackageStats == null) {
                    if (succeeded)
                        Slog.v(TAG, "Failed getting size for pkg : " + pkgInfo.packageName);
                    else
                        Slog.v(TAG, "Time out getting size for pkg : " + pkgInfo.packageName);
                    nextRun();
                    return;
                }
                if(DEBUG) {
                    boolean isSystemApp = (pkgInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

                    String msg = "";
                    if (isSystemApp)
                        msg += "is system app";
                    else
                        msg += "is data app";
                    if (myPackageStats.dataSize != 0) {
                        msg += ", dataSize:["+myPackageStats.dataSize+"]";
                    }
                    if (!isSystemApp && myPackageStats.codeSize != 0) {
                        msg += ", codeSize:["+myPackageStats.codeSize+"]";
                    }
                    Slog.w(TAG, msg);
                }

                if ((pkgInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    if ( mMAILPACKAGE.equals(pkgInfo.packageName)) {
                        myMailSize += myPackageStats.dataSize;
                    } else if (mMMSPACKAGE.equals(pkgInfo.packageName)) {
                        mySmsMmsSize += myPackageStats.dataSize;
                    } else {
                        myApplicationSize += myPackageStats.dataSize;
                    }
                } else {
                    if (mMAILPACKAGE.equals(pkgInfo.packageName)) {
                        myMailSize += myPackageStats.dataSize + myPackageStats.codeSize;
                    } else if (mMMSPACKAGE.equals(pkgInfo.packageName)) {
                        mySmsMmsSize += myPackageStats.dataSize + myPackageStats.codeSize;
                    } else {
                        myApplicationSize += myPackageStats.dataSize + myPackageStats.codeSize;
                    }
                }
                if(DEBUG)Slog.w(TAG, "myApplicationSize:"+myApplicationSize+
                                    ",myMailSize:"+myMailSize+
                                    ",mySmsMmsSize:"+mySmsMmsSize);
                mGetSizeState = GET_SIZE_NEXT;
            }
            nextRun();
        }
    }

    public void beginGetShowStorageData() {
        if (gssdInstance == null) {
            gssdInstance = new GetShowStorageData();
        }
        gssdInstance.start();
    }

    public long[] getShowStorageDataOK() {
        return gssdInstance.result();
    }
    /* @} */

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

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {

            pw.println("Permission Denial: can't dump " + SERVICE + " from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }

        pw.println("Current DeviceStorageMonitor state:");
        pw.print("  mFreeMem="); pw.print(Formatter.formatFileSize(mContext, mFreeMem));
                pw.print(" mTotalMemory=");
                pw.println(Formatter.formatFileSize(mContext, mTotalMemory));
        pw.print("  mFreeMemAfterLastCacheClear=");
                pw.println(Formatter.formatFileSize(mContext, mFreeMemAfterLastCacheClear));
        pw.print("  mLastReportedFreeMem=");
                pw.print(Formatter.formatFileSize(mContext, mLastReportedFreeMem));
                pw.print(" mLastReportedFreeMemTime=");
                TimeUtils.formatDuration(mLastReportedFreeMemTime, SystemClock.elapsedRealtime(), pw);
                pw.println();
        pw.print("  mLowMemFlag="); pw.print(mLowMemFlag);
                pw.print(" mMemFullFlag="); pw.println(mMemFullFlag);
        pw.print("  mClearSucceeded="); pw.print(mClearSucceeded);
                pw.print(" mClearingCache="); pw.println(mClearingCache);
        pw.print("  mMemLowThreshold=");
                pw.print(Formatter.formatFileSize(mContext, mMemLowThreshold));
                pw.print(" mMemFullThreshold=");
                pw.println(Formatter.formatFileSize(mContext, mMemFullThreshold));
        pw.print("  mMemCacheStartTrimThreshold=");
                pw.print(Formatter.formatFileSize(mContext, mMemCacheStartTrimThreshold));
                pw.print(" mMemCacheTrimToThreshold=");
                pw.println(Formatter.formatFileSize(mContext, mMemCacheTrimToThreshold));
    }
}
