package android.theme;

import android.content.Intent;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Context;
import android.os.ServiceManager;
import java.util.List;
import android.os.IBinder;
import android.os.RemoteException;

public class ThemeManager {
    public static final String KEY_IS_THEME_PACKAGE="is_theme_package";
    public static final String KEY_TARGET_PACKAGE_NAME="target_package";
    public static final String KEY_THEME_NAME="theme_name";
    public static final String KEY_THEME_PREVIEW="theme_preview";

    public static final String INTENT_ACTION_THEME_PACKAGE_CHANGED="android.intent.action.theme_package_changed";
    public static final String INTENT_ACTION_THEME_CHANGED="android.intent.action.theme_changed";
    
    private final IThemeManager mService;
    private final Context mContext;

    private OnThemeChangedListener mThemeChangedListener;
    private OnThemePackageChangedListener mThemePackageChangedListener;

    private static IntentFilter sFilter;

    static {
	sFilter=new IntentFilter();
	sFilter.addAction(INTENT_ACTION_THEME_PACKAGE_CHANGED);
	sFilter.addAction(INTENT_ACTION_THEME_CHANGED);
    }

    public ThemeManager(Context context, IThemeManager service) {
	mContext = context;
        mService = service;
	mContext.registerReceiver(new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
		    String action=intent.getAction();
		    if (action==null) {
			return;
		    }
		    if (action.equals(INTENT_ACTION_THEME_PACKAGE_CHANGED)) {
			if (mThemePackageChangedListener!=null) {
			    mThemePackageChangedListener.onThemePackageChanged();
			} 
		    } else if (action.equals(INTENT_ACTION_THEME_CHANGED)) {
			if (mThemeChangedListener!=null) {
			    mThemeChangedListener.onThemeChanged();
			} 
		    } else {
			Log.e ("sunway","ThemeManager: unknown action: "+action);
			return;
		    }
		}
	    }, sFilter);
    }

    public List<ThemeInfo> getThemes() {
        try {
            return mService.getThemes();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    public ThemeInfo getAppliedTheme(String resDir) {
        try {
            return mService.getAppliedTheme(resDir);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }
    
    public boolean setTheme(ThemeInfo info) {
        try {
            return mService.setTheme(info);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean unsetTheme(ThemeInfo info) {
        try {
            return mService.unsetTheme(info);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    public void registerOnThemeChangedListener(OnThemeChangedListener listener) {
	mThemeChangedListener=listener;
    }

    public void registerOnThemePackageChangedListener(OnThemePackageChangedListener listener) {
	mThemePackageChangedListener=listener;
    }

    public interface OnThemePackageChangedListener {
	void onThemePackageChanged();
    }

    public interface OnThemeChangedListener {
	void onThemeChanged();
    }
}