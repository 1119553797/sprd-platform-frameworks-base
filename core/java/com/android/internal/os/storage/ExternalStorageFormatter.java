package com.android.internal.os.storage;

import android.app.ProgressDialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.storage.IMountService;
import android.os.storage.StorageEventListener;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import com.android.internal.R;

import java.util.ArrayList;

/**
 * Takes care of unmounting and formatting external storage.
 */
public class ExternalStorageFormatter extends Service
        implements DialogInterface.OnCancelListener {
    static final String TAG = "ExternalStorageFormatter";

    public static final String FORMAT_ONLY = "com.android.internal.os.storage.FORMAT_ONLY";
    public static final String FORMAT_AND_FACTORY_RESET = "com.android.internal.os.storage.FORMAT_AND_FACTORY_RESET";

    public static final String EXTRA_ALWAYS_RESET = "always_reset";
    // {@hide}
    public static final String EXTRA_FORMAT_INTERNAL = "format_internal";
    // {@hide}
    public static final String EXTRA_FORMAT_EXTERNAL = "format_external";

    // If non-null, the volume to format. Otherwise, will use the default external storage directory
    private StorageVolume mStorageVolume;

    public static final ComponentName COMPONENT_NAME
            = new ComponentName("android", ExternalStorageFormatter.class.getName());

    // Access using getMountService()
    private IMountService mMountService = null;

    private StorageManager mStorageManager = null;

    private PowerManager.WakeLock mWakeLock;

    private ProgressDialog mProgressDialog = null;

    private boolean mFactoryReset = false;
    private boolean mAlwaysReset = false;

    private String mInternalStoragePath = null;
    private String mExternalStoragePath = null;

    private int mFormatStorageIndex = 0;
    private int mFormatStorageCount = 0;
//    private String mFormatStoragePath = null;
//    private ArrayList<String> mFormatStoragePaths
//            = new ArrayList<String>();
    private StorageInfo mFormatStorageInfo = null;
    private ArrayList<StorageInfo> mFormatStorageInfos
            = new ArrayList<StorageInfo>();

    private Handler mNextStorageHandler = null;

    private static final int STORAGE_TYPE_INVALID = -1;
    private static final int STORAGE_TYPE_INTERNAL = 0;
    private static final int STORAGE_TYPE_EXTERNAL = 1;

    class StorageInfo {
        String mPath;
        int mType;
        public StorageInfo() {
            mPath = null;
            mType = STORAGE_TYPE_INVALID;
        }
        public StorageInfo(String path, int type) {
            mPath = path;
            mType = type;
        }
        public String toString() {
            return "StorageInfo [ path="+mPath+", type="+mType+" ]";
        }
    };

    private static final int MESSAGE_TYPE_UNMOUNTING = 0;
    private static final int MESSAGE_TYPE_ERASING = 1;
    private static final int MESSAGE_TYPE_FORMAT_ERROR = 2;
    private static final int MESSAGE_TYPE_BAD_REMOVAL = 3;
    private static final int MESSAGE_TYPE_CHECKING = 4;
    private static final int MESSAGE_TYPE_REMOVED = 5;
    private static final int MESSAGE_TYPE_SHARED = 6;
    private static final int MESSAGE_TYPE_UNKNOWN_STATE = 7;

    //add for bug87011
    private boolean mFinished = false;

    StorageEventListener mStorageListener = new StorageEventListener() {
        @Override
        public void onStorageStateChanged(String path, String oldState, String newState) {
            Log.i(TAG, "Received storage state changed notification that " +
                    path + " changed state from " + oldState +
                    " to " + newState);
            //add for bug 87011 do not update when finished.
            if(mFinished){
                Log.d(TAG, "not to updateProgressState for finished :");
                return;
            }
            updateProgressState();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        if (mStorageManager == null) {
            mStorageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
            mStorageManager.registerListener(mStorageListener);
        }

        mWakeLock = ((PowerManager)getSystemService(Context.POWER_SERVICE))
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ExternalStorageFormatter");
        mWakeLock.acquire();
    }

    private String getInternalStoragePath() {
        if (mInternalStoragePath == null) {
            if (Environment.getSecondStorageType() == Environment.SECOND_STORAGE_TYPE_INTERNAL) {
                mInternalStoragePath = Environment.getSecondStorageDirectory().getPath();
            }
            if (Environment.getSecondStorageType() == Environment.SECOND_STORAGE_TYPE_EXTERNAL) {
                mInternalStoragePath = Environment.getExternalStorageDirectory().getPath();
            }
        }

        return mInternalStoragePath;
    }

    private String getExternalStoragePath() {
        if (mExternalStoragePath == null) {
            if(Environment.getSecondStorageType() == Environment.SECOND_STORAGE_TYPE_NAND
                || Environment.getSecondStorageType() == Environment.SECOND_STORAGE_TYPE_INTERNAL) {
                mExternalStoragePath = Environment.getExternalStorageDirectory().getPath();
            } else {
                mExternalStoragePath = Environment.getSecondStorageDirectory().getPath();
            }
        }

        return mExternalStoragePath;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        boolean formatInternal = false;
        boolean formatExternal = false;

        if (FORMAT_AND_FACTORY_RESET.equals(intent.getAction())) {
            mFactoryReset = true;
        }
        if (intent.getBooleanExtra(EXTRA_ALWAYS_RESET, false)) {
            mAlwaysReset = true;
        }
        formatInternal = intent.getBooleanExtra(EXTRA_FORMAT_INTERNAL, false);
        formatExternal = intent.getBooleanExtra(EXTRA_FORMAT_EXTERNAL, false);

        if (formatInternal || formatExternal) {
            Log.d(TAG,"onStartCommand format : "+ (formatInternal ? "internal" : "")
                + ((formatInternal & formatExternal) ? "," : "")
                + (formatExternal ? "external" : ""));
        }

        //add for nug 87011
        mFinished = false;
        mStorageVolume = intent.getParcelableExtra(StorageVolume.EXTRA_STORAGE_VOLUME);
        Log.d(TAG,"onStartCommand format : "+mStorageVolume);

        if (mFormatStorageCount > 0) {
            mFormatStorageInfos.clear();
        }

        String storagePath = null;
        if (mStorageVolume != null) {
            storagePath = mStorageVolume.getPath();
            String internalStoragePath = getInternalStoragePath();
            if (internalStoragePath != null && storagePath.equals(internalStoragePath)) {
                mFormatStorageInfos.add(new StorageInfo(storagePath, STORAGE_TYPE_INTERNAL));
            }
            if (storagePath.equals(getExternalStoragePath())) {
                mFormatStorageInfos.add(new StorageInfo(storagePath, STORAGE_TYPE_EXTERNAL));
            }
        }
        if (formatInternal == false && formatExternal == false) {
            if (mStorageVolume == null) {
                mFormatStorageInfos.add(new StorageInfo(getExternalStoragePath(), STORAGE_TYPE_EXTERNAL));
            }
        } else {
            if (formatInternal) {
                String internalStoragePath = getInternalStoragePath();
                if (internalStoragePath != null) {
                    if (storagePath == null || !internalStoragePath.equals(storagePath)) {
                        mFormatStorageInfos.add(new StorageInfo(internalStoragePath, STORAGE_TYPE_INTERNAL));
                    }
                }
            }
            if (formatExternal) {
                String externalStoragePath = getExternalStoragePath();
                if (externalStoragePath != null ) {
                    if (storagePath == null || !externalStoragePath.equals(storagePath)) {
                        mFormatStorageInfos.add(new StorageInfo(externalStoragePath, STORAGE_TYPE_EXTERNAL));
                    }
                }
            }
        }

        mFormatStorageCount = mFormatStorageInfos.size();
        mFormatStorageIndex = 0;
        if (mFormatStorageCount > 0) {
            mFormatStorageInfo = mFormatStorageInfos.get(0);
            mNextStorageHandler = new Handler();
        }

        Log.d(TAG,"onStartCommand mFormatStorageCount is " + mFormatStorageCount);
        for (int index = 0; index < mFormatStorageCount; index++)
            Log.d(TAG,"onStartCommand mFormatStorageInfos["+index+"] is " + mFormatStorageInfos.get(index));

        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCancelable(false);//changed true to false for bug 87011
            mProgressDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            if (!mAlwaysReset) {
                mProgressDialog.setOnCancelListener(this);
            }
            updateProgressState();
            mProgressDialog.show();
        }

        return Service.START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        if (mStorageManager != null) {
            mStorageManager.unregisterListener(mStorageListener);
        }
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
        mWakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        IMountService mountService = getMountService();
        try {
            mFormatStorageIndex = mFormatStorageCount;
            if (mFormatStorageInfo != null)
                mountService.mountVolume(mFormatStorageInfo.mPath);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed talking with mount service", e);
        }
        stopSelf();
    }

    void fail(int msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        nextStorageOrFactoryReset(mAlwaysReset);
        stopSelf();
    }

    /*
     * return if has next storage need to format
     */
    private boolean nextStorageOrFactoryReset(boolean factoryReset) {
        mFormatStorageIndex++;
        if (mFormatStorageIndex < mFormatStorageCount) {
            mNextStorageHandler.post(new Runnable(){
                public void run() {
                    updateProgressState();
                }
            });
            return true;
        }
        if (factoryReset) {
            sendBroadcast(new Intent("android.intent.action.MASTER_CLEAR"));
        }
        return false;
    }

    private int getMessageID(int messageType) {
        int storageType = STORAGE_TYPE_EXTERNAL;
        if (mFormatStorageInfo != null) {
            storageType = mFormatStorageInfo.mType;
        }
        if (storageType == STORAGE_TYPE_INTERNAL) {
            switch(messageType) {
                case MESSAGE_TYPE_UNMOUNTING:
                    return R.string.progress_unmounting1;
                case MESSAGE_TYPE_ERASING:
                    return R.string.progress_erasing1;
                case MESSAGE_TYPE_FORMAT_ERROR:
                    return R.string.format_error1;
                case MESSAGE_TYPE_BAD_REMOVAL:
                    return R.string.media_bad_removal1;
                case MESSAGE_TYPE_CHECKING:
                    return R.string.media_checking1;
                case MESSAGE_TYPE_REMOVED:
                    return R.string.media_removed1;
                case MESSAGE_TYPE_SHARED:
                    return R.string.media_shared1;
                case MESSAGE_TYPE_UNKNOWN_STATE:
                    return R.string.media_unknown_state1;
            }
        } else {
            switch(messageType) {
                case MESSAGE_TYPE_UNMOUNTING:
                    return R.string.progress_unmounting;
                case MESSAGE_TYPE_ERASING:
                    return R.string.progress_erasing;
                case MESSAGE_TYPE_FORMAT_ERROR:
                    return R.string.format_error;
                case MESSAGE_TYPE_BAD_REMOVAL:
                    return R.string.media_bad_removal;
                case MESSAGE_TYPE_CHECKING:
                    return R.string.media_checking;
                case MESSAGE_TYPE_REMOVED:
                    return R.string.media_removed;
                case MESSAGE_TYPE_SHARED:
                    return R.string.media_shared;
                case MESSAGE_TYPE_UNKNOWN_STATE:
                    return R.string.media_unknown_state;
            }
        }
        return R.string.untitled;
    }

    void updateProgressState() {
        if (mFormatStorageIndex < mFormatStorageCount) {
            mFormatStorageInfo = mFormatStorageInfos.get(mFormatStorageIndex);
        } else {
            if (mFormatStorageCount == 0 && (mFactoryReset || mAlwaysReset)) {
                sendBroadcast(new Intent("android.intent.action.MASTER_CLEAR"));
            }
            stopSelf();
            return;
        }
        final String formatStoragePath = mFormatStorageInfo.mPath;
        String status = mStorageManager.getVolumeState(formatStoragePath);
        if (Environment.MEDIA_MOUNTED.equals(status)
                || Environment.MEDIA_MOUNTED_READ_ONLY.equals(status)) {
            updateProgressDialog(getMessageID(MESSAGE_TYPE_UNMOUNTING));
            IMountService mountService = getMountService();
            try {
                // Remove encryption mapping if this is an unmount for a factory reset.
                mountService.unmountVolume(formatStoragePath, true, mFactoryReset);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed talking with mount service", e);
            }
        } else if (Environment.MEDIA_NOFS.equals(status)
                || Environment.MEDIA_UNMOUNTED.equals(status)
                || Environment.MEDIA_UNMOUNTABLE.equals(status)) {
            updateProgressDialog(getMessageID(MESSAGE_TYPE_ERASING));
            final IMountService mountService = getMountService();
            if (mountService != null) {
                new Thread() {
                    @Override
                    public void run() {
                        boolean success = false;
                        try {
                            mountService.formatVolume(formatStoragePath);
                            success = true;
                        } catch (Exception e) {
                            Toast.makeText(ExternalStorageFormatter.this,
                                    getMessageID(MESSAGE_TYPE_FORMAT_ERROR), Toast.LENGTH_LONG).show();
                        }
                        if (success) {
                            if (mFactoryReset) {
                                if (nextStorageOrFactoryReset(true)) {
                                    return;
                                }
                                // Intent handling is asynchronous -- assume it will happen soon.
                                stopSelf();
                                return;
                            }
                        }
                        // If we didn't succeed, or aren't doing a full factory
                        // reset, then it is time to remount the storage.
                        if (!success && mAlwaysReset) {
                            nextStorageOrFactoryReset(true);
                        } else {
                            try {
                                mountService.mountVolume(formatStoragePath);
                                //add for bug 87011
                                //set finish flag to avoid handling mount state again
                                mFinished = true;
                            } catch (RemoteException e) {
                                Log.w(TAG, "Failed talking with mount service", e);
                            }
                        }
                        stopSelf();
                        return;
                    }
                }.start();
            } else {
                Log.w(TAG, "Unable to locate IMountService");
            }
        } else if (Environment.MEDIA_BAD_REMOVAL.equals(status)) {
            fail(getMessageID(MESSAGE_TYPE_BAD_REMOVAL));
        } else if (Environment.MEDIA_CHECKING.equals(status)) {
            fail(getMessageID(MESSAGE_TYPE_CHECKING));
        } else if (Environment.MEDIA_REMOVED.equals(status)) {
            fail(getMessageID(MESSAGE_TYPE_REMOVED));
        } else if (Environment.MEDIA_SHARED.equals(status)) {
            fail(getMessageID(MESSAGE_TYPE_SHARED));
        } else {
            fail(getMessageID(MESSAGE_TYPE_UNKNOWN_STATE));
            Log.w(TAG, "Unknown storage state: " + status);
            //stopSelf();
        }
    }

    public void updateProgressDialog(int msg) {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCancelable(false);
            mProgressDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            mProgressDialog.show();
        }

        mProgressDialog.setMessage(getText(msg));
    }

    IMountService getMountService() {
        if (mMountService == null) {
            IBinder service = ServiceManager.getService("mount");
            if (service != null) {
                mMountService = IMountService.Stub.asInterface(service);
            } else {
                Log.e(TAG, "Can't get mount service");
            }
        }
        return mMountService;
    }
}
