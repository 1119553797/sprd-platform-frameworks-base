package com.sprd.android.config;

import android.os.SystemProperties;

/**
 * @hide
 */
public class OptConfig {

	/**
	 * Spreadst Low Cost Case Support
	 * persist.sys.lowcost is configed in /device/sprd/xxx/BoardConfig.mk
	 * @hide
	 */
	public static final boolean TARGET_LOWCOST_SUPPORT = SystemProperties.getBoolean("persist.sys.lowcost", false);
	
	/**
	 * @hide
	 */
	public static final boolean LC_RAM_SUPPORT = TARGET_LOWCOST_SUPPORT;
}
