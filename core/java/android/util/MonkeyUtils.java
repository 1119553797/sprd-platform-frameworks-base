package android.util;

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
