package android.util;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import android.os.Process;
import android.os.SystemProperties;

/**
 * @author liwd@spreadst.com
 * @since 2012.09.26
 */
public class MonkeyUtils {

	
	private static boolean isMonkey = false;
	
	/**
	 * Eat some exception in monkey test, just ignore it.
	 * 
	 * In monkey test, sometimes we don't want exceptions be thrown because they won't happen
	 * in normal use, but they make the monkey time bad indeed.
	 * 
	 * @param tag
	 * @param msg
	 */
	public static void eatExceptionInMonkey(String tag, String msg, Throwable e) throws Throwable {
		checkArguments(tag, msg, e);
		
    	if (isMonkey()) {
    		Log.d(tag, msg + "\n" + e.getMessage());
    	} else {
    		throw e;
    	}
	}
	
	public static void eatExceptionInMonkey(String tag, String msg, Throwable e, Runnable run) throws Throwable {
		checkArguments(tag, msg, e);
    	
    	if (isMonkey()) {
    		Log.d(tag, msg + "\n" + e.getMessage());
    		if (run != null) run.run();
    	} else {
    		throw e;
    	}
	}
	
	public static boolean isMonkey() {
		boolean isMonkey = false;
    	try {
    		isMonkey = SystemProperties.getBoolean("ro.monkey", false);
    	} catch (Exception e1) {
    		isMonkey = false;
    		e1.printStackTrace();
    	}
    	return isMonkey;
	}

    public static void outputHProfile(String pname) {
        String file = "/data/misc/hprofs/";
        File dir = new File(file);

        if (dir.exists() && dir.isDirectory() && dir.canWrite()) {
            File[] files = dir.listFiles();
            for (File f : files) {
                String p = f.getPath();
                if (f.isFile() && p.contains(pname) && p.endsWith("hprof")) {
                    f.delete();
                }
            }
            int pid = Process.myPid();
            Date d = new Date();
            String date = d.getDate() + "-" + d.getHours() + "-" + d.getMinutes() + "-" + d.getSeconds();
            file += pname +  "_" + pid + "_" + date + ".hprof";

            try {
                android.os.Debug.dumpHprofData(file);
            } catch (IOException e2) {
                e2.printStackTrace();
            }
        }
    }

	private static void checkArguments(String tag, String msg, Throwable e) throws MonkeyUtilException {
		if (tag == null || msg == null || e == null) 
			throw new MonkeyUtilException("You must supply full info. \ntag = " + tag + "\nmsg = " + msg + "\ne = " + e);
	}
	
	public static class MonkeyUtilException extends Exception {
		
		public MonkeyUtilException() {
			super();
		}
		
		public MonkeyUtilException(String detailMessage) {
			super(detailMessage);
		}
	}
}
