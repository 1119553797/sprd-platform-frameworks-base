
package com.android.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import com.android.internal.content.PackageHelper;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageDeleteObserver;
import android.content.pm.IPackageManager;
import android.content.pm.IPackageMoveObserver;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.format.Formatter;
import android.util.Slog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

/**
 * this Activity will called by ShowStorage Manage to
 * show application storage situation under /data filesystem
 * and free memory by "clear data" "application uninstall" "move to sd card" .
 *
 */
public class AppManage extends Activity {

    private static final String TAG = "AppManager";

    private AppInfoAdapter myAppInfoAdapter;

    private ListView mListView;

    private PackageManager mPm;

    private LayoutInflater mInflater;

    private AppInfoMap mCache;

    private List<PackageInfo> mPackageList;

    private Button mAppStartBtn;

    private ClearUserDataObserver mClearDataObserver;

    private PackageMoveObserver mPackageMoveObserver;

    private PackageUninstallObserver mPackageUninstallobserver;

    private static final String MAILPACKAGE = "com.android.email";

    private static final String MMSPACKAGE = "com.android.mms";

    private static final int SIZE_INVALID = -1;

    private static final int FREE_SPACE = 1;

    private static final int GET_RESOURCE = 2;

    private static final int DLG_BASE = 0;

    private static final int DLG_LOADING = DLG_BASE + 1;

    private static final int DLG_PROCESSING = DLG_BASE + 2;

    private List<String> mClearDataList = new ArrayList<String>();

    private List<String> mMove2SdList = new ArrayList<String>();

    private List<String> mUninstallList = new ArrayList<String>();

    private List<String> mMove2SdCheckList = new ArrayList<String>();

    private List<String> mClearDataCheckList = new ArrayList<String>();

    private List<String> mUninstallCheckList = new ArrayList<String>();

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            // If the activity is gone, don't process any more messages.
            if (isFinishing()) {
                return;
            }
            switch (msg.what) {
                case FREE_SPACE:
                    refreshUI();
                case GET_RESOURCE:
                    initListView();
                default:
                    break;
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.android.internal.R.layout.app_mng_storage);

        mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPm = getPackageManager();

        mPackageList = new ArrayList<PackageInfo>();
        List<ApplicationInfo> appList = getAppsOnMemory();
        myAppInfoAdapter = new AppInfoAdapter(this, appList);

        ListView lv = (ListView) findViewById(com.android.internal.R.id.myApp_List);
        lv.setItemsCanFocus(true);
        mListView = lv;

        new GetResource().start();
        showDialog(DLG_LOADING);

        mAppStartBtn = (Button) findViewById(com.android.internal.R.id.myApp_Start_Btn_Id);
        mAppStartBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                showDialog(DLG_PROCESSING);
                new FreeMemory().start();
            }
        });
    }

    @Override
    public Dialog onCreateDialog(int id, Bundle args) {

        if (id == DLG_LOADING) {
            ProgressDialog dlg = new ProgressDialog(this);
            dlg.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            dlg.setMessage(getText(com.android.internal.R.string.myAppLoading));
            dlg.setIndeterminate(true);
            dlg.setCancelable(false);
            return dlg;
        }
        if (id == DLG_PROCESSING) {
            ProgressDialog dlg = new ProgressDialog(this);
            dlg.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            dlg.setMessage(getText(com.android.internal.R.string.myAppProcessing));
            dlg.setIndeterminate(true);
            dlg.setCancelable(false);
            return dlg;
        }
        return null;
    }

    private void initListView() {
        // Create list view from the adapter here. Wait till the sort order
        // of list is defined. its either by label or by size. So atleast one of
        // the
        // first steps should have been completed before the list gets filled.
        myAppInfoAdapter.sortList();
        mListView.setAdapter(myAppInfoAdapter);
        dismissDialog(DLG_LOADING);
    }

    protected void refreshUI() {
        // TODO Auto-generated method stub
        mClearDataList.clear();
        mMove2SdList.clear();
        mUninstallList.clear();

        mUninstallCheckList.clear();
        mMove2SdCheckList.clear();
        mClearDataCheckList.clear();

        if (myAppInfoAdapter.mAppList == null) {
            myAppInfoAdapter.mAppList = getAppsOnMemory();

            mCache = new AppInfoMap();
            boolean refreshUI = myAppInfoAdapter.resetAppList();
            if (!refreshUI) {
                Slog.e(TAG, "rebuild GUI failed.");
            }
	} else {
	    myAppInfoAdapter.notifyDataSetChanged();
	}
        dismissDialog(DLG_PROCESSING);
    }


    /**
     *
     *  The Callback function to observe clear Application data
     *
     */
    class ClearUserDataObserver extends IPackageDataObserver.Stub {
        private CountDownLatch mCount;

        public void onRemoveCompleted(final String packageName, final boolean succeeded) {
            if (!succeeded) {
                Slog.w(TAG, "<" + packageName + "> ClearData FAILED");
            }
            mCount.countDown();
        }

        public void invokeClearData(String packageName, CountDownLatch count) {
            mCount = count;
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            am.clearApplicationUserData(packageName, this);
        }

    }

    /**
     *
     * The Callback function to observe package move to sd card
     *
     */
    class PackageMoveObserver extends IPackageMoveObserver.Stub {
        private CountDownLatch mCount;

        public void packageMoved(final String packageName, final int returnCode)
                throws RemoteException {
            if (returnCode != PackageManager.MOVE_SUCCEEDED) {
                Slog.w(TAG, "<" + packageName + "> Move to SD Card FAILED");
            }
            mCount.countDown();
        }

        public void invokeMovePackage(String packageName, CountDownLatch count) {
            mCount = count;
            mPm.movePackage(packageName, this, PackageManager.MOVE_EXTERNAL_MEDIA);
        }
    }

    /**
     *
     * The Callback function to observe package uninstall
     *
     */
    private class PackageUninstallObserver extends IPackageDeleteObserver.Stub {
        private CountDownLatch mCount;

        public void packageDeleted(final boolean succeeded) {
            if (!succeeded) {
                Slog.w(TAG, "Uninstall FAILED");
            }
            mCount.countDown();
        }

        public void invokedeletePackage(String packageName, CountDownLatch count) {
            mCount = count;
            mPm.deletePackage(packageName, this, 0);
            // For fix Bug24091. PackageManagerService.deletePackage() don't delete the package immediately,
            // and UI will be refresh after the package was deleted. If we delete the item now, there maybe
            // get a IllegalStateException for "The content of the adapter has changed but ListView did not receive a notification".
//            myAppInfoAdapter.deleteItem(packageName);
	}
    }

    /**
     *
     * get all the PackageData which will showed on ListView.
     *
     */
    private class GetResource extends Thread {
        public void run() {
            mCache = new AppInfoMap();
            final Message msg = mHandler.obtainMessage(GET_RESOURCE);
            mHandler.sendMessage(msg);
        }
    }

    /**
     *
     * FreeMemorySpace when StartButton is pressed
     *
     */
    private class FreeMemory extends Thread {

        public void run() {
            freeMemorySpace();
        }

        private void freeMemorySpace() {
            // TODO Auto-generated method stub

            if (mClearDataList != null) {
                for (String pkgName : mClearDataList) {
                    CountDownLatch count1 = new CountDownLatch(1);
                    if (mClearDataObserver == null) {
                        mClearDataObserver = new ClearUserDataObserver();
                    }

                    mClearDataObserver.invokeClearData(pkgName, count1);
                    try {
                        count1.await();
                    } catch (InterruptedException e) {
                        Slog.w(TAG, "Failed to clear package data : " + pkgName);
                    }
                }
            }

            if (!mMove2SdList.isEmpty()) {
                for (String pkgName : mMove2SdList) {
                    // when Move2Sd is selected,did not do Uninstall
                    if (mUninstallList.contains(pkgName)) {
                        mUninstallList.remove(pkgName);
                    }

                    CountDownLatch count1 = new CountDownLatch(1);
                    if (mPackageMoveObserver == null) {
                        mPackageMoveObserver = new PackageMoveObserver();
                    }

                    mPackageMoveObserver.invokeMovePackage(pkgName, count1);

                    try {
                        count1.await();
                    } catch (InterruptedException e) {
                        Slog.w(TAG, "Failed to Move package to SD card : " + pkgName);
                    }
                }
            }

            if (!mUninstallList.isEmpty()) {
                for (String pkgName : mUninstallList) {
                    // when Move2Sd is selected,did not do Uninstall

                    CountDownLatch count1 = new CountDownLatch(1);
                    if (mPackageUninstallobserver == null) {
                        mPackageUninstallobserver = new PackageUninstallObserver();
                    }

                    mPackageUninstallobserver.invokedeletePackage(pkgName, count1);

                    try {
                        count1.await();
                    } catch (InterruptedException e) {
                        Slog.w(TAG, "Failed to uninstall : " + pkgName);
                    }
                }

                // Delete the item after the packages were deleted.
                for (String pkgName : mUninstallList) {
                    myAppInfoAdapter.deleteItem(pkgName);
                }
            }

            final Message msg = mHandler.obtainMessage(FREE_SPACE);
            mHandler.sendMessageAtFrontOfQueue(msg);// Update UI immediately.
        }
    }

    private List<ApplicationInfo> getAppsOnMemory() {
        // TODO Auto-generated method stub

        List<PackageInfo> installedPackageList = mPm
                .getInstalledPackages(PackageManager.GET_UNINSTALLED_PACKAGES);
        if (installedPackageList == null) {
            return new ArrayList<ApplicationInfo>();
        }

        List<ApplicationInfo> appList = new ArrayList<ApplicationInfo>();

        for (PackageInfo packageinfo : installedPackageList) {
            if ((packageinfo.applicationInfo.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) == 0) {
                if (packageinfo.packageName == MAILPACKAGE) {
                    continue;
                }
                if (packageinfo.packageName == MMSPACKAGE) {
                    continue;
                }
                mPackageList.add(packageinfo);
                appList.add(packageinfo.applicationInfo);
            }
        }

        return appList;
    }

    // move to DeviceStorageMonitor to format App Size
    private CharSequence getSizeStr(long size) {
        CharSequence appSize = null;

        if (size == SIZE_INVALID) {
            Slog.e(TAG, "getSizeStr SizeInvalid");
            return null;
        }
        appSize = Formatter.formatFileSize(AppManage.this, size);
        return appSize;
    }

    /**
     *
     * View Holder used when displaying views
     *
     */
    static class AppViewHolder {
        TextView appName;

        ImageView appIcon;

        TextView appSize;

        CheckBox appMv2SdFlag;

        CheckBox appCleanDataFlag;

        CheckBox appUninstallFlag;

    }

    /**
     *
     * Adapter for the application list view
     *
     */
    class AppInfoAdapter extends BaseAdapter {
        private List<ApplicationInfo> mAppList;

        private AppInfo mInfo;

        private SizeComparator mSizeComparator = new SizeComparator();

        public AppInfoAdapter(Context c, List<ApplicationInfo> appList) {
            mAppList = appList;
        }

	public void deleteItem(String packageName) {
	    int imax = mAppList.size();
	    
	    mCache.removeEntry(packageName);
	    for (int i = 0; i < imax; i++) {
	        ApplicationInfo info = mAppList.get(i);
		if (packageName == info.packageName) {
		    mAppList.remove(i);
		    break;
		}
	    }
	}

        public boolean resetAppList() {
            // TODO Auto-generated method stub

            // Check for all properties in map before sorting. Populate values
            // from cache
            if (mAppList.size() > 0) {
                sortList();
            }
            notifyDataSetChanged();

            return true;
        }

        public int getCount() {
            // TODO Auto-generated method stub
            return mAppList.size();
        }

        public Object getItem(int position) {
            // TODO Auto-generated method stub
            return mAppList.get(position);
        }

        public long getItemId(int position) {
            // TODO Auto-generated method stub
            int imax = mAppList.size();
            if ((position < 0) || (position >= imax)) {
                Slog.w(TAG, "Position out of bounds in List Adapter");
                return -1;
            }
            AppInfo aInfo = mCache.getEntry(mAppList.get(position).packageName);
            if (aInfo == null) {
                Slog.w(TAG, "getItemId return -1");
                return -1;
            }
            return aInfo.index;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            // TODO Auto-generated method stub
            if (position >= mAppList.size()) {
                Slog.w(TAG, "Invalid view position:" + position + ", actual size is:"
                        + mAppList.size());
                return null;
            }

            AppViewHolder holder;

            if (convertView == null) {

                convertView = mInflater.inflate(com.android.internal.R.layout.app_mng_list_item,
                        null);
                holder = new AppViewHolder();
                holder.appName = (TextView) convertView
                        .findViewById(com.android.internal.R.id.app_name);
                holder.appIcon = (ImageView) convertView
                        .findViewById(com.android.internal.R.id.app_icon);
                holder.appSize = (TextView) convertView
                        .findViewById(com.android.internal.R.id.app_size);
                holder.appMv2SdFlag = (CheckBox) convertView
                        .findViewById(com.android.internal.R.id.Move2Sd_ChkBox_Id);
                holder.appCleanDataFlag = (CheckBox) convertView
                        .findViewById(com.android.internal.R.id.ClearData_ChkBox_Id);
                holder.appUninstallFlag = (CheckBox) convertView
                        .findViewById(com.android.internal.R.id.Uninstall_ChkBox_Id);

                convertView.setTag(holder);
            } else {
                holder = (AppViewHolder) convertView.getTag();
            }

            // Bind the data efficiently with the holder
            final ApplicationInfo appInfo = mAppList.get(position);

            mInfo = mCache.getEntry(appInfo.packageName);
            if (mInfo != null) {
                if (mInfo.appName != null) {
                    holder.appName.setText(mInfo.appName);
                }
                if (mInfo.appIcon != null) {
                    holder.appIcon.setImageDrawable(mInfo.appIcon);
                }
                if (mInfo.appSize != null) {
                    holder.appSize.setText(mInfo.appSize);
                }

                holder.appMv2SdFlag.setId(position);
                holder.appCleanDataFlag.setId(position);
                holder.appUninstallFlag.setId(position);

                holder.appMv2SdFlag.setEnabled(mInfo.move2SdFlag);
                holder.appCleanDataFlag.setEnabled(mInfo.clearDataFlag);
                holder.appUninstallFlag.setEnabled(mInfo.uninstallFlag);

                holder.appMv2SdFlag.setChecked(mMove2SdCheckList.contains(appInfo.packageName));
                holder.appCleanDataFlag.setChecked(mClearDataCheckList.contains(appInfo.packageName));
                holder.appUninstallFlag.setChecked(mUninstallCheckList.contains(appInfo.packageName));

                if (mInfo.move2SdFlag == true) {
                    holder.appMv2SdFlag.setOnClickListener(new OnClickListener() {
                        public void onClick(View v) {
                            CheckBox checkBox = (CheckBox) v;
                            boolean isChecked = checkBox.isChecked();

                            if (isChecked) {
                                if (!mMove2SdList.contains(appInfo.packageName)) {
                                    mMove2SdList.add(appInfo.packageName);
                                    mMove2SdCheckList.add(appInfo.packageName);
                                    //add to handle the conflict issue when both Move2SD and Uninstall has been selected
                                    if(mUninstallCheckList.contains(appInfo.packageName)){
                                        mUninstallList.remove(appInfo.packageName);
                                        mUninstallCheckList.remove(appInfo.packageName);
                                        notifyDataSetChanged();
                                    }
                                }
                            } else {
                                if (mMove2SdList.contains(appInfo.packageName)) {
                                    mMove2SdList.remove(appInfo.packageName);
                                    mMove2SdCheckList.remove(appInfo.packageName);
                                }
                            }
                        }
                    });
                }
                if (mInfo.clearDataFlag == true) {
                    holder.appCleanDataFlag.setOnClickListener(new OnClickListener() {
                        public void onClick(View v) {
                            CheckBox checkBox = (CheckBox) v;
                            boolean isChecked = checkBox.isChecked();

                            if (isChecked) {
                                if (!mClearDataList.contains(appInfo.packageName)) {
                                    mClearDataList.add(appInfo.packageName);
                                    mClearDataCheckList.add(appInfo.packageName);
                                }
                            } else {
                                if (mClearDataList.contains(appInfo.packageName)) {
                                    mClearDataList.remove(appInfo.packageName);
                                    mClearDataCheckList.remove(appInfo.packageName);
                                }
                            }
                        }
                    });
                }
                if (mInfo.uninstallFlag == true) {
                    holder.appUninstallFlag.setOnClickListener(new OnClickListener() {
                        public void onClick(View v) {
                            CheckBox checkBox = (CheckBox) v;
                            boolean isChecked = checkBox.isChecked();

                            if (isChecked) {
                                if (!mUninstallList.contains(appInfo.packageName)) {
                                    mUninstallList.add(appInfo.packageName);
                                    mUninstallCheckList.add(appInfo.packageName);
                                    //add to handle the conflict issue when both Move2SD and Uninstall has been selected
                                    if(mMove2SdCheckList.contains(appInfo.packageName)){
                                        mMove2SdList.remove(appInfo.packageName);
                                        mMove2SdCheckList.remove(appInfo.packageName);
                                        notifyDataSetChanged();
                                    }
                                }
                            } else {
                                if (mUninstallList.contains(appInfo.packageName)) {
                                    mUninstallList.remove(appInfo.packageName);
                                    mUninstallCheckList.remove(appInfo.packageName);
                                }
                            }
                        }
                    });
                }

            } else {
                Slog.w(TAG, "No info for package:" + appInfo.packageName + " in property map");
            }
            return convertView;
        }

        private void adjustIndex() {
            int imax = mAppList.size();
            for (int i = 0; i < imax; i++) {
                ApplicationInfo info = mAppList.get(i);
                mCache.getEntry(info.packageName).index = i;
            }
        }

        public void sortList() {
            Collections.sort(mAppList, mSizeComparator);
            adjustIndex();
        }

    } // AppInfoAdapter()

    /**
     *
     * The Callback function to observe package size compute
     *
     */
    private class SizeObserver extends IPackageStatsObserver.Stub {
        private CountDownLatch mCount;

        PackageStats stats;

        boolean succeeded;

        public void invokeGetSize(String packageName, CountDownLatch count) {
            mCount = count;
            mPm.getPackageSizeInfo(packageName, this);
        }

        public void onGetStatsCompleted(PackageStats pStats, boolean pSucceeded) {
            succeeded = pSucceeded;
            stats = pStats;
            mCount.countDown();
        }
    }

    /**
     *
     * make the map of (Packagename , AppInfo)
     *
     */
    class AppInfoMap {

        private Map<String, AppInfo> mAppPropMap = new HashMap<String, AppInfo>();

        private AppInfo getEntry(String pkgName) {
            return mAppPropMap.get(pkgName);
        }

        public void addEntry(AppInfo aInfo) {
            if ((aInfo != null) && (aInfo.pkgName != null)) {
                mAppPropMap.put(aInfo.pkgName, aInfo);
            }
        }

        public void removeEntry(String pkgName) {
            if (pkgName != null) {
                mAppPropMap.remove(pkgName);
            }
        }

        public AppInfoMap() {
            mAppPropMap.clear();
            boolean err = false;

            PackageStats myPackageStats;
            SizeObserver mSizeObserver = new SizeObserver();

            String myPkgName;
            CharSequence myAppName;
            Drawable myAppIcon;
            CharSequence myAppSize;
            int myIdx = -1;
            long mySize;
            boolean myMove2SdFlag = false;
            boolean myClearDataFlag = false;
            boolean myUninstallFlag = false;

            int count = mPackageList.size();

            for (int p = 0; p < count; p++, mySize = 0) {
                PackageInfo info = mPackageList.get(p);
                myPkgName = info.packageName;

                if ((info.applicationInfo.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0) {
                    continue;
                } else {
                    CountDownLatch count1 = new CountDownLatch(1);
                    mSizeObserver.invokeGetSize(myPkgName, count1);

                    try {
                        count1.await();
                    } catch (InterruptedException e) {
                        Slog.w(TAG, "Failed computing size for pkg : " + myPkgName);
                    }

                    // Process the package statistics
                    myPackageStats = mSizeObserver.stats;
                    boolean succeeded = mSizeObserver.succeeded;

                    if (succeeded && myPackageStats == null) {
                        err = true;
                    }

                    if ((info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                        mySize = myPackageStats.dataSize;
                        myMove2SdFlag = false;
                        if (info.applicationInfo.manageSpaceActivityName != null) {
                            myClearDataFlag = false;
                        }else{
                            if (myPackageStats.dataSize > 0) {
                                myClearDataFlag = true;
                            } else {
                                myClearDataFlag = false;
                            }
                        }
                        myUninstallFlag = false;
                    } else {
                        mySize = myPackageStats.dataSize + myPackageStats.codeSize;
                        ApplicationInfo info1 = new ApplicationInfo();
                        try {
                            info1 = mPm.getApplicationInfo(info.packageName, 0);
                        } catch (NameNotFoundException e) {
                            //do nothing
                        }

                        if (info1 != null) {
                            if ((info.applicationInfo.flags & ApplicationInfo.FLAG_FORWARD_LOCK) == 0
                                    && (info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                                    && info != null) {
                                if (info.installLocation == PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL
                                        || info.installLocation == PackageInfo.INSTALL_LOCATION_AUTO) {
                                    myMove2SdFlag = true;
                                } else if (info.installLocation == PackageInfo.INSTALL_LOCATION_UNSPECIFIED) {
                                    IPackageManager ipm = IPackageManager.Stub
                                            .asInterface(ServiceManager.getService("package"));
                                    int loc;
                                    try {
                                        loc = ipm.getInstallLocation();
                                    } catch (RemoteException e) {
                                        Slog.e(TAG, "Is Pakage Manager running?");
                                        return;
                                    }
                                    if (loc == PackageHelper.APP_INSTALL_EXTERNAL) {
                                        // For apps with no preference and the
                                        // default value set
                                        // to install on sdcard.
                                        myMove2SdFlag = true;
                                    }
                                }
                            }
                        }

                        if (info.applicationInfo.manageSpaceActivityName != null) {
                            myClearDataFlag = false;
                        }else{
                            if (myPackageStats.dataSize > 0) {
                                myClearDataFlag = true;
                            } else {
                                myClearDataFlag = false;
                            }
                        }
                        boolean mUpdatedSysApp = (info.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                        if (mUpdatedSysApp) {
                            myUninstallFlag = false;
                        } else {
                            myUninstallFlag = true;
                        }
                    }

                    myAppName = info.applicationInfo.loadLabel(mPm);
                    myAppIcon = info.applicationInfo.loadIcon(mPm);
                    myAppSize = getSizeStr(mySize);

                    AppInfo aInfo = new AppInfo(myPkgName, myIdx, myAppName, myAppIcon, mySize,
                            myAppSize, myMove2SdFlag, myClearDataFlag, myUninstallFlag);
                    mAppPropMap.put(aInfo.pkgName, aInfo);
                }
            }

            if (err) {
                Slog.w(TAG, "Failed to load cache. Not using cache for now.");
                // Clear cache and bail out
                mAppPropMap.clear();
            } // end if
        } // end method AppInfoCache

    } // end class AppInfoCache

    /**
     *
     * Hold the information of applications,which will showed on listview
     *
     */
    public class AppInfo {

        String pkgName;

        int index;

        CharSequence appName;

        Drawable appIcon;

        CharSequence appSize;

        long size;

        boolean move2SdFlag;

        boolean clearDataFlag;

        boolean uninstallFlag;

        /**
         *
         * class AppInfo 's constructed function
         * @param pName             packageName
         * @param pIndex            Index in the AppInfo List
         * @param aName             application Label
         * @param aIcon             application Icon
         * @param pSize             application's size under /data filesystem
         * @param pSizeStr          string of pSize
         * @param pMove2SdFlag      if the application can be moved to sdcard, the value is TRUE
         * @param pClearDataFlag    if the application's data size is not 0, the value is TRUE
         * @param pUnistallFlag     if the application can be uninstall, the value is TRUE
         */
        public AppInfo(String pName, int pIndex, CharSequence aName, Drawable aIcon, long pSize,
                CharSequence pSizeStr, boolean pMove2SdFlag, boolean pClearDataFlag,
                boolean pUnistallFlag) {
            index = pIndex;
            pkgName = pName;
            appName = aName;
            appIcon = aIcon;
            size = pSize;
            appSize = pSizeStr;
            move2SdFlag = pMove2SdFlag;
            clearDataFlag = pClearDataFlag;
            uninstallFlag = pUnistallFlag;
        }
    } // end class AppInfo

    /**
     *
     * used to sort installed packages by size
     *
     */
    private class SizeComparator implements Comparator<ApplicationInfo> {
        public final int compare(ApplicationInfo a, ApplicationInfo b) {
            AppInfo ainfo = mCache.getEntry(a.packageName);
            AppInfo binfo = mCache.getEntry(b.packageName);

            long atotal = ainfo.size;
            long btotal = binfo.size;
            long ret = atotal - btotal;
            // negate result to sort in descending order
            if (ret < 0) {
                return 1;
            }
            if (ret == 0) {
                return 0;
            }
            return -1;
        }
    } // end of Size Comparator

} // end class AppManage

