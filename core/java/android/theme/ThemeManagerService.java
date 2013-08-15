package android.theme;

import android.content.res.AssetManager;
import android.content.IntentFilter;
import android.content.Intent;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.util.HashMap;
import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import android.content.Intent;
import java.io.PrintWriter;
import java.io.FileDescriptor;
import android.util.Log;
import android.content.ContentResolver;
import android.content.res.Configuration;
import android.provider.Settings;
import android.content.pm.PackageInfo;
import java.io.IOException;
import java.io.FileWriter;
import java.io.FileReader;
import android.app.ActivityManager;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.util.ArrayList;
import android.text.TextUtils;
import android.os.Bundle;
import android.content.pm.ApplicationInfo;
import android.os.ServiceManager;
import android.app.IActivityManager;
import android.content.pm.PackageManager;
import java.util.List;
import android.content.Context;
import android.os.Environment;
import java.io.File;
import com.android.internal.content.PackageMonitor;


public class ThemeManagerService extends IThemeManager.Stub {
    private Context mContext;
    private PackageMonitor mPackageMonitor;
    private PackageManager mPackageManager;
    private ActivityManager mActivityManager;

    private ThemeLoader mThemeLoader;

    private static final boolean AUTO_APPLY_ALL_THEME=true;
    
    public ThemeManagerService(Context context) {
	Log.e ("sunway","ThemeManagerService: start");
	mActivityManager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);

	mPackageManager=context.getPackageManager();
	mContext=context;
	
	mThemeLoader=new ThemeLoader();

	mPackageMonitor=new PackageMonitor() {
		@Override
		public void onPackageUpdateStarted(String packageName, int uid) {
		    onPackageRemoved(packageName,uid);
		}

		@Override
		public void onPackageUpdateFinished(String packageName, int uid) {
		    onPackageAdded(packageName,uid);
		}

		@Override
		public void onPackageAdded(String packageName, int uid) {
		    Log.e ("sunway","ThemeManagerService:onPackageAdded:"+packageName);
		    ThemeInfo info=getThemeInfo(packageName);
		    if (info==null) {
			return;
		    }
		    ThemeManagerService.this.addTheme(info);
		}

		@Override    
		public void onPackageRemoved(String packageName, int uid) {
		    Log.e ("sunway","ThemeManagerService:onPackageRemoved:"+packageName);
		    ThemeInfo info=getThemeInfo(packageName);
		    if (info==null) {
			return;
		    }
		    ThemeManagerService.this.removeTheme(info);
		}

	    };

	if (AUTO_APPLY_ALL_THEME) {
	    for (ThemeInfo theme:mThemeLoader.getThemes()) {
		setTheme(theme);
	    }
	} 

    }

    private void addTheme(ThemeInfo info) {
	mThemeLoader.addTheme(info);
	// onThemePackageChanged();
    }

    private void removeTheme(ThemeInfo info) {
	mThemeLoader.removeTheme(info);
	// TODO
	// onThemePackageChanged();
    }

    private ThemeInfo getThemeInfo(String packageName) {
	ApplicationInfo aInfo=null;
	try {
	    aInfo=mPackageManager.getApplicationInfo(
		packageName,
		PackageManager.GET_META_DATA|PackageManager.GET_UNINSTALLED_PACKAGES);
	    
	} catch (PackageManager.NameNotFoundException e) {
	    e.printStackTrace();
	    return null;
	} 

	Bundle bundle=aInfo.metaData;
	if (bundle==null) {
	    return null;
	}

	String targetPackageName=bundle.getString(ThemeManager.KEY_TARGET_PACKAGE_NAME,null);
	Boolean isThemePackage=bundle.getBoolean(ThemeManager.KEY_IS_THEME_PACKAGE,false);

	if (!isThemePackage || targetPackageName==null) {
	    return null;	    
	} 

	ThemeInfo info=new ThemeInfo(packageName,targetPackageName);

	// parse theme name, theme preview, ...
	int themeNameResId=bundle.getInt(ThemeManager.KEY_THEME_NAME,0);
	int themePreviewResId=bundle.getInt(ThemeManager.KEY_THEME_PREVIEW,0);

	if (themeNameResId!=0) {
	    info.setThemeName(themeNameResId);
	} 

	if (themePreviewResId!=0) {
	    info.setThemePreview(themePreviewResId);
	}

	// parse resDir
	info.setResDir(aInfo.publicSourceDir);

	// parse targetResDir
	if (targetPackageName.equals("android")) {
	    info.setTargetResDir("/system/framework/framework-res.apk");
	} else {
	    try {
		ApplicationInfo targetAInfo=mPackageManager.getApplicationInfo(
		    targetPackageName,
		    PackageManager.GET_META_DATA|PackageManager.GET_UNINSTALLED_PACKAGES);
		info.setTargetResDir(targetAInfo.publicSourceDir);
	    } catch (PackageManager.NameNotFoundException e) {
		e.printStackTrace();
		return null;
	    } 
	}
	return info;
    }

    @Override
    public ThemeInfo getAppliedTheme(String resDir) {
	List<ThemeInfo> themes=getThemes();
	for (ThemeInfo info:themes) {
	    if (info.getTargetResDir()==null) {
		Log.e ("sunway","getAppliedTheme: info resdir null");
	    }
	    
	    if (info.mApplied && info.getTargetResDir().equals(resDir)) {
		return info;
	    } 
	} 
	return null;
    }
    
    @Override
    public List<ThemeInfo> getThemes() {
	return mThemeLoader.getThemes();
    }
      
    private boolean setThemeInternal(ThemeInfo theme, boolean isSet) {
	if (theme==null) {
	    return false;
	}
        long identityToken = clearCallingIdentity();
        try {
	    Log.e ("sunway","setTheme:"+theme);

	    if (isSet) {
		mThemeLoader.setTheme(theme);		
	    } else {
		mThemeLoader.unsetTheme(theme);		
	    }

	    // onThemeChanged();
	    return true;         
        } finally {
            restoreCallingIdentity(identityToken);
        }
    }
    
    @Override
    public boolean unsetTheme(ThemeInfo origTheme) {
	return setThemeInternal(origTheme,false);
    }
    
    @Override
    public boolean setTheme(ThemeInfo newTheme) {
	return setThemeInternal(newTheme, true);
    }
    
    private void onThemePackageChanged() {
	mContext.sendBroadcast(new Intent(ThemeManager.INTENT_ACTION_THEME_PACKAGE_CHANGED));
    }

    private void onThemeChanged() {
	mContext.sendBroadcast(new Intent(ThemeManager.INTENT_ACTION_THEME_CHANGED));
    }
    
    protected void dump(FileDescriptor fd, PrintWriter fout, String[] args) {
	fout.println("Themes:");
	for (ThemeInfo info:mThemeLoader.getThemes()) {
	    fout.println(info.toString());
	}
	fout.println();
    }

    class ThemeLoader {
	private HashMap<String, ThemeInfo>  mAppliedThemes=new HashMap<String,ThemeInfo>();
	private List<ThemeInfo> mThemes;

	ThemeLoader() {
	    mThemes=restore();
	    // load from pm
	    List<ThemeInfo> themesFromPM=new ArrayList<ThemeInfo>();
	    List<PackageInfo> packages=mPackageManager.getInstalledPackages(0);
	    for (PackageInfo pkgInfo:packages) {
		String pkgName=pkgInfo.packageName;
		ThemeInfo info=getThemeInfo(pkgName);
		if (info!=null) {
		    Log.e ("sunway","ThemeManagerService:got theme info:"+info.mPackageName);
		    themesFromPM.add(info);
		}
	    }

	    // join it
	    List<ThemeInfo> tmp=new ArrayList<ThemeInfo>();
	    for (ThemeInfo info:mThemes) {
		if (themesFromPM.contains(info)) {
		    tmp.add(info);
		} 
	    }

	    for (ThemeInfo info: themesFromPM) {
		if (!mThemes.contains(info)) {
		    tmp.add(info);
		} 
	    } 
	    mThemes=tmp;

	    // rebuild mAppliedThemes
	    for (ThemeInfo info:mThemes) {
		if (info.mApplied) {
		    mAppliedThemes.put(info.mTargetPackageName, info);
		} 
	    }
	}
	
	String getToken() {
	    int ret=1;
	    for (ThemeInfo info: mAppliedThemes.values()) {
		String pkg=info.mPackageName;
		ret=31*ret+pkg.hashCode();
	    }
	    return Integer.toString(ret,16);
	}

	private ThemeInfo check(ThemeInfo info) {
	    int index=mThemes.indexOf(info);
	    if (index==-1) {
		return null;
	    }
	    return mThemes.get(index);
	}
	
	void addTheme(ThemeInfo theme) {
	    mThemes.add(theme);
	    save();
	}

	void removeTheme(ThemeInfo theme) {
	    mThemes.remove(theme);
	    // TODO
	    save();
	}
	
	void setTheme(ThemeInfo newTheme) {
	    newTheme=check(newTheme);
	    if (newTheme==null) {
		return;
	    } 
	    newTheme.mApplied=true;
	    mAppliedThemes.put(newTheme.mTargetPackageName, newTheme);
	    save();
	}

	void unsetTheme(ThemeInfo newTheme) {
	    Log.e ("sunway","unsetTheme 1:");
	    newTheme=check(newTheme);
	    Log.e ("sunway","unsetTheme 2:");
	    if (newTheme==null) {
		return;
	    }
	    Log.e ("sunway","unsetTheme 3:"+newTheme.toString());
	    newTheme.mApplied=false;
	    mAppliedThemes.remove(newTheme.mTargetPackageName);
	    save();
	}

	List<ThemeInfo> getThemes() {
	    return mThemes;
	}
	
	List<ThemeInfo> restore() {
	    List<ThemeInfo> ret=new ArrayList<ThemeInfo>();

	    File f = new File(new File(Environment.getDataDirectory(),"system"), "themes");
	    if (!f.exists()) {
		return ret;
	    } 
	    ObjectInputStream in=null;
	    try {
		in=new ObjectInputStream(new FileInputStream(f));
		ret=(ArrayList<ThemeInfo>) in.readObject();
		return ret;	    
	    } catch (Exception e) {
		e.printStackTrace();
		return ret;
	    } finally {
		if (in!=null) {
		    try {
			in.close();			
		    } catch (Exception e) {} 
		} 
	    }
	}

	void save() {
	    File f = new File(new File(Environment.getDataDirectory(),"system"), "themes");
	    if (!f.exists()) {
		try {
		    f.createNewFile();		    
		} catch (Exception e) {
		    return;
		} 
	    }
	    ObjectOutputStream out=null;
	    try {
		out=new ObjectOutputStream(new FileOutputStream(f));
		out.writeObject(mThemes);
	    } catch (IOException e) {
		e.printStackTrace();
	    } finally {
		if (out!=null) {
		    try {
			out.close();			
		    } catch (Exception e) {} 
		} 
	    }
	}
    }
}

