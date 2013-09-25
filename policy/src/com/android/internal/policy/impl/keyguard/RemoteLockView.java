/** Create by Spreadst */
package com.android.internal.policy.impl.keyguard;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import android.app.PendingIntent;
import android.content.Context;
import android.os.Debug;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.AbsLockScreen;
import android.widget.Button;
import android.widget.ILockScreenListener;
import android.widget.ILockScreenProxy;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.android.internal.policy.impl.keyguard.KeyguardUpdateMonitor.BatteryStatus;
import com.android.internal.policy.impl.keyguard.KeyguardViewMediator.ViewMediatorCallback;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.widget.LockPatternUtils;

public class RemoteLockView extends RelativeLayout implements KeyguardSecurityView,
        ViewMediatorCallback {
    private LockPatternUtils mLockPatternUtils;
    private KeyguardSecurityCallback mCallback;
    private KeyguardUpdateMonitor mUpdateMonitor;
    private Context mContext;
    private boolean isExecCreate = false;
    private static final String TAG = "RemoteLockView";
    private static final boolean DEBUG = Debug.isDebug();

    RemoteLockView(Context context, LockPatternUtils lockPatternUtils,
            ClassLoader lockClassLoader) throws IllegalArgumentException {
        super(context);
        mContext = context;
        mLockPatternUtils = lockPatternUtils;
        mUpdateMonitor = KeyguardUpdateMonitor.getInstance(mContext);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        mLockScreenProxy = createRemoteLockView(lockClassLoader);
        if (mLockScreenProxy != null) {
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                    LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT);
            addView(mLockScreenProxy, lp);
        } else {
            mUpdateMonitor.removeCallback(mInfoCallback);
            throw new IllegalArgumentException(
                    "Create remote view false ,to create default lock view .");
        }
        mUpdateMonitor.registerCallback(mInfoCallback);
        isExecCreate = true;
    }

    private AbsLockScreen createRemoteLockView(ClassLoader classLoader) {
        Class c;
        if (DEBUG) {
            Log.d(TAG, "createRemoteLockView...");
        }
        try {
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
                mCallback.dismiss(false);
            }
        }

        public void takeEmergencyCallAction() {
        }

        public void pokeWakelock() {

            if (mCallback != null) {
                mCallback.userActivity(0);
            }
        }

        public void pokeWakelock(int millis) {

            if (mCallback != null) {
                mCallback.userActivity(millis);
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
                return mUpdateMonitor.getTelephonyPlmn()[i];
            }
            return null;
        }

        public CharSequence getTelephonySpn(int i) {
            if (mUpdateMonitor != null) {
                return mUpdateMonitor.getTelephonySpn()[i];
            }
            return null;
        }

        public State getSimState() {
            return null;
        }

    };

    public void cleanUp() {
        if (mLockScreenProxy != null) {
            mLockScreenProxy.onStopAnim();
            mLockScreenProxy.cleanUp();
        }
        mUpdateMonitor.removeCallback(mInfoCallback); // this must be first
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
                isExecCreate = false;
            } else {
                mLockScreenProxy.onStartAnim();
            }
            mLockScreenProxy.onRefreshBatteryInfo(mUpdateMonitor.shouldShowBatteryInfo(),
                    mUpdateMonitor.isDevicePluggedIn(), mUpdateMonitor.getBatteryLevel());
        }
    }

    KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        void onRefreshBatteryInfo(BatteryStatus status) {
            if (mLockScreenProxy != null) {
                mLockScreenProxy.onRefreshBatteryInfo(mUpdateMonitor.shouldShowBatteryInfo(),
                        mUpdateMonitor.isDevicePluggedIn(), mUpdateMonitor.getBatteryLevel());
            }
        }

        @Override
        void onTimeChanged() {
            if (mLockScreenProxy != null) {
                mLockScreenProxy.onTimeChanged();
            }
        }

        @Override
        void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn, int subscription) {
            if (mLockScreenProxy != null) {
                mLockScreenProxy.onRefreshCarrierInfo(plmn, spn, subscription);
            }
        }

        @Override
        void onRingerModeChanged(int state) {
            if (mLockScreenProxy != null) {
                mLockScreenProxy.onRingerModeChanged(state);
            }
        }

        @Override
        void onPhoneStateChanged(int phoneState) {
            if (mLockScreenProxy != null) {
                Button button = mLockScreenProxy.onPhoneStateChanged(phoneState);
                if (button != null) {
                    mLockPatternUtils.updateEmergencyCallButtonState(button, phoneState, true);
                }
            }
        }

        @Override
        void onKeyguardVisibilityChanged(boolean showing) {
            // TODO Auto-generated method stub
        }

        @Override
        void onClockVisibilityChanged() {
            if (mLockScreenProxy != null) {
                mLockScreenProxy.onClockVisibilityChanged();
            }
        }

        @Override
        void onDeviceProvisioned() {
            if (mLockScreenProxy != null) {
                mLockScreenProxy.onDeviceProvisioned();
            }
        }

        @Override
        void onDevicePolicyManagerStateChanged() {
            // TODO Auto-generated method stub
        }

        @Override
        void onUserSwitching(int userId) {
            // TODO Auto-generated method stub
        }

        @Override
        void onUserSwitchComplete(int userId) {
            // TODO Auto-generated method stub
        }

        @Override
        void onSimStateChanged(State simState, int subscription) {
            if (mLockScreenProxy != null) {
                mLockScreenProxy.onSimStateChanged(simState, subscription);
            }
        }

        @Override
        void onUserRemoved(int userId) {
            // TODO Auto-generated method stub
        }

        @Override
        void onUserInfoChanged(int userId) {
            // TODO Auto-generated method stub
        }

        @Override
        void onBootCompleted() {
            // TODO Auto-generated method stub
        }

        @Override
        void onMusicClientIdChanged(int clientGeneration, boolean clearing, PendingIntent intent) {
            // TODO Auto-generated method stub
        }

        @Override
        public void onMusicPlaybackStateChanged(int playbackState, long eventTime) {
            // TODO Auto-generated method stub
        }

        @Override
        void onMessageCountChanged(int messageCount) {
            if (mLockScreenProxy != null) {
                mLockScreenProxy.onMessageCountChanged(messageCount);
            }
        }

        @Override
        void onMissedCallCountChanged(int count) {
            if (mLockScreenProxy != null) {
                mLockScreenProxy.onMissedCallCountChanged(count);
            }
        }

    };

    @Override
    public void setKeyguardCallback(KeyguardSecurityCallback callback) {
        mCallback = callback;

    }

    @Override
    public void setLockPatternUtils(LockPatternUtils utils) {
        // TODO Auto-generated method stub

    }

    @Override
    public void reset() {
        // TODO Auto-generated method stub

    }

    @Override
    public void onResume(int reason) {
        if (mLockScreenProxy != null) {
            mLockScreenProxy.onResume();
            if (isExecCreate) {
                isExecCreate = false;
            } else {
                mLockScreenProxy.onStartAnim();
            }
            mLockScreenProxy.onRefreshBatteryInfo(mUpdateMonitor.shouldShowBatteryInfo(),
                    mUpdateMonitor.isDevicePluggedIn(), mUpdateMonitor.getBatteryLevel());
        }

    }

    @Override
    public KeyguardSecurityCallback getCallback() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void showUsabilityHint() {
        // TODO Auto-generated method stub

    }

    @Override
    public void showBouncer(int duration) {
        // TODO Auto-generated method stub

    }

    @Override
    public void hideBouncer(int duration) {
        // TODO Auto-generated method stub

    }

    @Override
    public void wakeUp() {
        // TODO Auto-generated method stub

    }

    @Override
    public void userActivity() {
        // TODO Auto-generated method stub
    }

    @Override
    public void userActivity(long millis) {
        // TODO Auto-generated method stub

    }

    @Override
    public void keyguardDone(boolean authenticated) {
        // TODO Auto-generated method stub

    }

    @Override
    public void keyguardDoneDrawing() {
        // TODO Auto-generated method stub

    }

    @Override
    public void setNeedsInput(boolean needsInput) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onUserActivityTimeoutChanged() {
        // TODO Auto-generated method stub

    }

    @Override
    public void keyguardDonePending() {
        // TODO Auto-generated method stub

    }

    @Override
    public void setSubId(int subId) {
    }
}
