/**
 * PowerManager UnitTests.
 * Wangliwei.
 *
 */

package com.sprd.tests.powermanagertests;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;

public class PowerManagerTest extends AndroidTestCase {
    
    private PowerManager mPm;
    public static final long TIME = 3000;
    public static final int MORE_TIME = 300;

    
    /**
     * Setup any common data for the upcoming tests.
     */
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mPm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
    }
    
    /**
     * Confirm that the setup is good.
     * 
     * @throws Exception
     */
    @MediumTest
    public void testPreconditions() throws Exception {
        assertNotNull(mPm);
    }

    /**
     * Confirm that we can create functional wakelocks.
     * 
     * @throws Exception
     */
    @MediumTest
    public void testNewWakeLock() throws Exception {

        PowerManager.WakeLock wl = mPm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "FULL_WAKE_LOCK");
        doTestWakeLock(wl);

        wl = mPm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "SCREEN_BRIGHT_WAKE_LOCK");
        doTestWakeLock(wl);

        wl = mPm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "SCREEN_DIM_WAKE_LOCK");
        doTestWakeLock(wl);

        wl = mPm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PARTIAL_WAKE_LOCK");
        doTestWakeLock(wl);
        
        doTestSetBacklightBrightness();

        // TODO: Some sort of functional test (maybe not in the unit test here?) 
        // that confirms that things are really happening e.g. screen power, keyboard power.
}
    
    /**
     * Confirm that we can't create dysfunctional wakelocks.
     * 
     * @throws Exception
     */
    @MediumTest
    public void testBadNewWakeLock() throws Exception {
        
        final int badFlags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK 
                            | PowerManager.SCREEN_DIM_WAKE_LOCK;
        // wrap in try because we want the error here
        try {
            PowerManager.WakeLock wl = mPm.newWakeLock(badFlags, "foo");
        } catch (IllegalArgumentException e) {
            return;
        }
        fail("Bad WakeLock flag was not caught.");
    }


    /**
     * test points:
     * 1 Get a wake lock at the level of the flags parameter
     * 2 Force the device to go to sleep
     * 3 User activity happened
     */
    public void testPowerManager() throws InterruptedException {


        long baseTime = SystemClock.uptimeMillis();
        try {
            mPm.goToSleep(baseTime + 1);
            fail("goToSleep should throw SecurityException");
        } catch (SecurityException e) {
            // expected
        }
        Thread.sleep(PowerManagerTest.TIME);

        baseTime = SystemClock.uptimeMillis();
        mPm.userActivity(baseTime + 1, false);
        Thread.sleep(PowerManagerTest.MORE_TIME);
    }
    
    /**
     * Apply a few tests to a wakelock to make sure it's healthy.
     * 
     * @param wl The wakelock to be tested.
     */
    private void doTestWakeLock(PowerManager.WakeLock wl) {
        // First try simple acquire/release
        wl.acquire();
        assertTrue(wl.isHeld());
        wl.release();
        assertFalse(wl.isHeld());
        
        // Try ref-counted acquire/release
        wl.setReferenceCounted(true);
        wl.acquire();
        assertTrue(wl.isHeld());
        wl.acquire();
        assertTrue(wl.isHeld());
        wl.release();
        assertTrue(wl.isHeld());
        wl.release();
        assertFalse(wl.isHeld());
        
        // Try non-ref-counted
        wl.setReferenceCounted(false);
        wl.acquire();
        assertTrue(wl.isHeld());
        wl.acquire();
        assertTrue(wl.isHeld());
        wl.release();
        assertFalse(wl.isHeld());

        // test acquire(long)
        wl.acquire(PowerManagerTest.TIME);
        assertTrue(wl.isHeld());
		try {
        	Thread.sleep(PowerManagerTest.TIME + PowerManagerTest.MORE_TIME);
		} catch(InterruptedException e) { }
        assertFalse(wl.isHeld());

        
    }
    
 
    /**
     * Test that calling {@link android.os.IHardwareService#setBacklights(int)} requires
     * permissions.
     * <p>Tests permission:
     *   {@link android.Manifest.permission#DEVICE_POWER}
     */
    private void doTestSetBacklightBrightness() {
        try {
            mPm.setBacklightBrightness(100);
            fail("setBacklights did not throw SecurityException as expected");
        } catch (SecurityException e) {
            // expected
        }
    }

}
