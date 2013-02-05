
package com.android.internal.policy.impl;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.AbsLockScreen;
import android.widget.Button;
import android.widget.ILockScreenListener;
import android.widget.ILockScreenProxy;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCard.State;
import com.android.internal.widget.LockPatternUtils;

import dalvik.system.PathClassLoader;

public class RemoteLockView extends RelativeLayout implements KeyguardScreen,
        KeyguardUpdateMonitor.InfoCallback, KeyguardUpdateMonitor.SimStateCallback {
    private LockPatternUtils mLockPatternUtils;
    private KeyguardScreenCallback mCallback;
    private KeyguardUpdateMonitor mUpdateMonitor;
    private Context mContext;
    // private static LockClassLoader mTSClassLoader = null ;
    private boolean isExecCreate = false;
    private static final String TAG = "RemoteLockView";

    RemoteLockView(Context context, LockPatternUtils lockPatternUtils,
            KeyguardUpdateMonitor updateMonitor, KeyguardScreenCallback callback,
            ClassLoader lockClassLoader) throws IllegalArgumentException {
        super(context);
        mCallback = callback;
        mLockPatternUtils = lockPatternUtils;
        mUpdateMonitor = updateMonitor;
        mContext = context;
        setFocusable(true);
        setFocusableInTouchMode(true);
        setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        mUpdateMonitor.registerInfoCallback(this);
        mUpdateMonitor.registerSimStateCallback(this);
        mLockScreenProxy = createRemoteLockView(lockClassLoader);
        if (mLockScreenProxy != null) {
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                    LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT);
            addView(mLockScreenProxy, lp);
        } else {
            mUpdateMonitor.removeCallback(this);
            throw new IllegalArgumentException(
                    "Create remote view false ,to create default lock view .");
        }
        isExecCreate = true;
    }

    private AbsLockScreen createRemoteLockView(ClassLoader classLoader) {
        Class c;
        Log.d(TAG, "createRemoteLockView...");
        try {
            /*
             * c = Class.forName("com.android.launcher2.LockscreenPoxy", true,
             * classLoader);
             */
            c = classLoader.loadClass("com.spreadst.lockscreen.LockscreenPoxy");

            Constructor<ILockScreenProxy> constructor = c.getConstructor(Context.class,
                    ILockScreenListener.class);

            ILockScreenProxy lockScreenProxy = (ILockScreenProxy) constructor.newInstance(mContext,
                    mLockScreenListener);
            return lockScreenProxy.getLockViewOfCustom();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            throw new IllegalArgumentException(
                    "Create remote Proxy false . because IllegalArgumentException");
        } catch (SecurityException e) {
            e.printStackTrace();
            throw new IllegalArgumentException(
                    "Create remote Proxy false . because SecurityException");
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new IllegalArgumentException(
                    "Create remote Proxy false . because IllegalAccessException");
        } catch (InvocationTargetException e) {
            e.printStackTrace();
            throw new IllegalArgumentException(
                    "Create remote Proxy false . because InvocationTargetException");
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            throw new IllegalArgumentException(
                    "Create remote Proxy false . because NoSuchMethodException");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new IllegalArgumentException(
                    "Create remote Proxy false . because ClassNotFoundException");
        } catch (InstantiationException e) {
            e.printStackTrace();
            throw new IllegalArgumentException(
                    "Create remote Proxy false . because InstantiationException");
        }

    }

    private AbsLockScreen mLockScreenProxy;
    private ILockScreenListener mLockScreenListener = new ILockScreenListener() {

        public void goToUnlockScreen() {
            if (mCallback != null) {
                mCallback.goToUnlockScreen();
            }
        }

        public void takeEmergencyCallAction() {
            if (mCallback != null) {
                mCallback.takeEmergencyCallAction();
            }
        }

        public void pokeWakelock() {

            if (mCallback != null) {
                mCallback.pokeWakelock();
            }
        }

        public void pokeWakelock(int millis) {

            if (mCallback != null) {
                mCallback.pokeWakelock(millis);
            }
        }

        public boolean isDeviceCharged() {
            if (mUpdateMonitor != null) {
                return mUpdateMonitor.isDeviceCharged();
            }
            return false;
        }

        public State getSimState(int i) {
            if (mUpdateMonitor != null) {
                return mUpdateMonitor.getSimState(i);
            }
            return null;
        }

        public boolean isDeviceProvisioned() {
            if (mUpdateMonitor != null) {
                return mUpdateMonitor.isDeviceProvisioned();
            }
            return false;
        }

        public int getBatteryLevel() {
            if (mUpdateMonitor != null) {
                mUpdateMonitor.getBatteryLevel();
            }
            return 0;
        }

        public boolean isDevicePluggedIn() {
            if (mUpdateMonitor != null) {
                return mUpdateMonitor.isDevicePluggedIn();
            }
            return false;
        }

        public boolean shouldShowBatteryInfo() {
            if (mUpdateMonitor != null) {
                return mUpdateMonitor.shouldShowBatteryInfo();
            }
            return false;
        }

        public CharSequence getTelephonyPlmn(int i) {
            if (mUpdateMonitor != null) {
                return mUpdateMonitor.getmTelephonyPlmn()[i];
            }
            return null;
        }

        public CharSequence getTelephonySpn(int i) {
            if (mUpdateMonitor != null) {
                return mUpdateMonitor.getmTelephonySpn()[i];
            }
            return null;
        }

        @Override
        public State getSimState() {
            // TODO Auto-generated method stub
            return null;
        }

    };

    public void cleanUp() {
        if (mLockScreenProxy != null) {
            mLockScreenProxy.onStopAnim();
            mLockScreenProxy.cleanUp();
        }
        mUpdateMonitor.removeCallback(this); // this must be first
        mLockPatternUtils = null;
        mUpdateMonitor = null;
        mCallback = null;
    }

    public boolean needsInput() {
        if (mLockScreenProxy != null) {
            return mLockScreenProxy.needsInput();
        }
        return false;
    }

    public void onPause() {
        if (mLockScreenProxy != null) {
            mLockScreenProxy.onStopAnim();
            mLockScreenProxy.onPause();
        }
    }

    public void onResume() {
        if (mLockScreenProxy != null) {
            mLockScreenProxy.onResume();
            if (isExecCreate) {
                // if(mUpdateMonitor.isFirstStart()){
                // mLockScreenProxy.onStartAnim();
                // }
                isExecCreate = false;
            } else {
                mLockScreenProxy.onStartAnim();
            }
            mLockScreenProxy.onRefreshBatteryInfo(mUpdateMonitor.shouldShowBatteryInfo(),
                    mUpdateMonitor.isDevicePluggedIn(), mUpdateMonitor.getBatteryLevel());
        }
    }

    public void onPhoneStateChanged(int phoneState) {
        if (mLockScreenProxy != null) {
            Button button = mLockScreenProxy.onPhoneStateChanged(phoneState);
            if (button != null) {
                mLockPatternUtils.updateEmergencyCallButtonState(button, phoneState, true);
            }
        }
    }

    public void onRefreshBatteryInfo(boolean showBatteryInfo, boolean pluggedIn, int batteryLevel) {
        if (mLockScreenProxy != null) {
            mLockScreenProxy.onRefreshBatteryInfo(showBatteryInfo, pluggedIn, batteryLevel);
        }
    }

    public void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn, int subscription) {
        if (mLockScreenProxy != null) {
            mLockScreenProxy.onRefreshCarrierInfo(plmn, spn, subscription);
        }
    }

    public void onRingerModeChanged(int state) {
        if (mLockScreenProxy != null) {
            mLockScreenProxy.onRingerModeChanged(state);
        }
    }

    public void onTimeChanged() {
        if (mLockScreenProxy != null) {
            mLockScreenProxy.onTimeChanged();
        }
    }

    public void onSimStateChanged(State simState, int subscription) {
        if (mLockScreenProxy != null) {
            mLockScreenProxy.onSimStateChanged(simState, subscription);
        }
    }

    @Override
    public void onClockVisibilityChanged() {
        if (mLockScreenProxy != null) {
            mLockScreenProxy.onClockVisibilityChanged();
        }

    }

    @Override
    public void onDeviceProvisioned() {
        if (mLockScreenProxy != null) {
            mLockScreenProxy.onDeviceProvisioned();
        }

    }

    // @Override
    // public void onMessageCountChanged(int messagecount) {
    // if (mLockScreenProxy != null) {
    // mLockScreenProxy.onMessageCountChanged(messagecount);
    // }
    //
    // }
    //
    // @Override
    // public void onDeleteMessageCount(int messagecount) {
    // if (mLockScreenProxy != null) {
    // mLockScreenProxy.onDeleteMessageCount(messagecount);
    // }
    //
    // }
    //
    // @Override
    // public void onMissedCallCountChanged(int count) {
    // if (mLockScreenProxy != null) {
    // mLockScreenProxy.onMissedCallCountChanged(count);
    // }
    //
    // }

    @Override
    public void onDevicePolicyManagerStateChanged() {
        // TODO Auto-generated method stub

    }

    @Override
    public void onUserChanged(int userId) {
        // TODO Auto-generated method stub

    }

    @Override
    public void  onMessageCountChanged(int count) {


    }
}
