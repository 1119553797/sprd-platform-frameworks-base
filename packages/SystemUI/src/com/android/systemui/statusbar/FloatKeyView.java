package com.android.systemui.statusbar;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.ServiceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import com.android.systemui.R;

public final class FloatKeyView extends View implements OnTouchListener {
    private String TAG = "FloatKey";

    private Bitmap[] mKeyIcons;
    private int[] mKeyCodes;

    private Bitmap mHideIcon;
    private Bitmap mHideIconPressed;

    private boolean mIsShown = false;

    private Paint mPaint;
    private float mOffsetX;
    private float mOffsetY;
    private long mDownTime;
    private int mIconHeight;
    private int mIconWidth;

    private int mKeyWidth;
    private int mIndex;
    private int mBoundWidth;

    private int mShowWidth;
    private int mShowHeight;
    private int mHideWidth;
    private int mHideHeight;

    private int mDragTrigger;

    private boolean mOnDrag;
    private float mDownX;
    private float mDownY;

    private boolean mAddToWindow = false;

    private WindowManager mWm;
    private WindowManager.LayoutParams mLp;
    private InputManager mInputManager;
    private Handler mHandler;

    public FloatKeyView(Context context) {
        super(context);
        mWm = (WindowManager) context.getSystemService(context.WINDOW_SERVICE);
        mInputManager = InputManager.getInstance();

        mLp = new WindowManager.LayoutParams();
        mLp.width = LayoutParams.WRAP_CONTENT;
        mLp.height = LayoutParams.WRAP_CONTENT;
        mLp.format = PixelFormat.RGBA_8888;
        mLp.type = LayoutParams.TYPE_SYSTEM_ERROR;
        mLp.flags |= LayoutParams.FLAG_NOT_FOCUSABLE;
        mLp.gravity = Gravity.LEFT | Gravity.TOP;

        Resources res = getResources();
        mKeyIcons = new Bitmap[] {
                BitmapFactory.decodeResource(res, R.drawable.ic_floatkey_home),
                BitmapFactory.decodeResource(res, R.drawable.ic_floatkey_menu),
                BitmapFactory.decodeResource(res, R.drawable.ic_floatkey_back),
                // BitmapFactory.decodeResource(res, R.drawable.ic_floatkey_search),
                BitmapFactory.decodeResource(res, R.drawable.ic_floatkey_close) };

        mKeyCodes = new int[] { 
                KeyEvent.KEYCODE_HOME,
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_BACK
                // KeyEvent.KEYCODE_SEARCH
        };

        mHideIcon = BitmapFactory.decodeResource(res, R.drawable.ic_floatkey_drag_icon);
        mHideIconPressed = BitmapFactory.decodeResource(res, R.drawable.ic_floatkey_drag_icon_pressed);
        mHideHeight = mHideIcon.getHeight();
        mHideWidth = mHideIcon.getWidth();

        mIconHeight = mKeyIcons[0].getHeight();
        mIconWidth = mKeyIcons[0].getWidth();

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        setOnTouchListener(this);

        HandlerThread handlerThread = new HandlerThread("floatkey");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper()) {

            @Override
            public void handleMessage(Message msg) {
                KeyEvent ev = (KeyEvent) msg.obj;
                if (!mInputManager.injectInputEvent(ev, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)) {
                    Log.w(TAG, "injectKeyEvent failed event = " + ev.toString());
                }
            }
        };
    }

    public void show() {
        mIsShown = true;
        setMeasure();
        mWm.updateViewLayout(this, mLp);
        invalidate();
    }

    public void hide() {
        mIsShown = false;
        setMeasure();
        mWm.updateViewLayout(this, mLp);
        invalidate();
    }

    public void addToWindow() {
        if (!mAddToWindow) {
            mWm.addView(this, mLp);
            mAddToWindow = true;
        }
    }

    public void removeFromWindow() {
        if (mAddToWindow) {
            if (isPressed()) {
                setPressed(false);
                if (mIsShown && pressKey()) {
                    removeCallbacks(mCheckLongPress);
                }
                mOnDrag = false;
            }
            mWm.removeView(this);
            mAddToWindow = false;
        }
    }

    public boolean isShown() {
        return mIsShown;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        mShowHeight = (int) (metrics.density * 20 + mIconHeight);
        mShowWidth = metrics.widthPixels;

        mKeyWidth = metrics.widthPixels / mKeyIcons.length;
        mBoundWidth = (int) (metrics.density * 7);

        mDragTrigger = (int) (metrics.density * 10);

        setMeasure();
    }

    private void setMeasure() {
        if (mIsShown)
            setMeasuredDimension(mShowWidth, mShowHeight);
        else
            setMeasuredDimension(mHideWidth, mHideHeight);
    }

    @Override
    public void draw(Canvas canvas) {
        if (!mIsShown) {
            if (isPressed())
                canvas.drawBitmap(mHideIconPressed, 0, 0, null);
            else
                canvas.drawBitmap(mHideIcon, 0, 0, null);
            return;
        }
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(0xa0000000);
        Rect r = new Rect(0, 0, getWidth(), mShowHeight);
        canvas.drawRect(r, mPaint);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setColor(0xccaaaaaa);
        mPaint.setStrokeWidth(mBoundWidth);
        canvas.drawRect(r, mPaint);

        int len = mKeyIcons.length;
        float top = (mShowHeight - mIconHeight) / 2;
        float left = (mKeyWidth - mIconWidth) / 2;

        for (int i = 0; i < len; i++) {
            canvas.drawBitmap(mKeyIcons[i], left, top, null);
            left += mKeyWidth;
        }

        if (isPressed()) {
            r.left = mIndex * mKeyWidth;
            r.right = r.left + mKeyWidth;
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(0xccaaaaaa);
            canvas.drawRect(r, mPaint);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (!mAddToWindow)
            return false;

        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            mDownTime = SystemClock.uptimeMillis();

            setPressed(true);
            invalidate();

            mDownX = event.getRawX();
            mDownY = event.getRawY();

            mOffsetX = mDownX - mLp.x;
            mOffsetY = mDownY - mLp.y;

            if (!mIsShown)
                break;

            mIndex = (int) (event.getRawX() / mKeyWidth);

            if (!pressKey()) {
                mOffsetY = event.getRawY() - mLp.y;
            } else {
                int keyCode = mKeyCodes[mIndex];
                sendEvent(KeyEvent.ACTION_DOWN, keyCode, mKeyCodes[mIndex]);

                removeCallbacks(mCheckLongPress);
                postDelayed(mCheckLongPress,
                        ViewConfiguration.getLongPressTimeout());
            }
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            break;

        case MotionEvent.ACTION_MOVE:
            if (!mIsShown || !pressKey()) {
                if (mOnDrag) {
                    if (!mIsShown)
                        mLp.x = (int) (event.getRawX() - mOffsetX);
                    mLp.y = (int) (event.getRawY() - mOffsetY);
                    mWm.updateViewLayout(this, mLp);
                } else {
                    if (Math.abs(event.getRawX() - mDownX) > mDragTrigger
                            || Math.abs(event.getRawY() - mDownY) > mDragTrigger)
                        mOnDrag = true;
                }
            }
            break;

        case MotionEvent.ACTION_CANCEL:
            setPressed(false);
            invalidate();
            if (mIsShown && pressKey()) {
                removeCallbacks(mCheckLongPress);
            }
            mOnDrag = false;
            break;

        case MotionEvent.ACTION_UP:
            final boolean doIt = isPressed();
            setPressed(false);
            invalidate();

            if (mIsShown && pressKey()) {
                int keyCode = mKeyCodes[mIndex];

                if (doIt) {
                    sendEvent(KeyEvent.ACTION_UP, keyCode, 0);
                    sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
                    playSoundEffect(SoundEffectConstants.CLICK);
                } else {
                    sendEvent(KeyEvent.ACTION_UP, keyCode, KeyEvent.FLAG_CANCELED);
                }

                removeCallbacks(mCheckLongPress);
            } else {
                if (!mOnDrag) {
                    if (mIsShown)
                        hide();
                    else
                        show();
                    playSoundEffect(SoundEffectConstants.CLICK);
                } else {
                    mOnDrag = false;
                }
            }

            break;
        }

        return false;
    }

    private boolean pressKey() {
        return mIndex >= 0 && mIndex != mKeyIcons.length - 1;
    }

    void sendEvent(int action, int code, int flags) {
        sendEvent(action, code, flags, SystemClock.uptimeMillis());
    }

    void sendEvent(int action, int code, int flags, long when) {
        final int repeatCount = (flags & KeyEvent.FLAG_LONG_PRESS) != 0 ? 1 : 0;
        final KeyEvent ev = new KeyEvent(mDownTime, when, action, code,
                repeatCount, 0, KeyCharacterMap.BUILT_IN_KEYBOARD, 0, flags
                        | KeyEvent.FLAG_FROM_SYSTEM
                        | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                InputDevice.SOURCE_KEYBOARD);
        mHandler.sendMessage(mHandler.obtainMessage(1, ev));
    }

    Runnable mCheckLongPress = new Runnable() {
        public void run() {
            if (isPressed()) {
                if (pressKey()) {
                    int keyCode = mKeyCodes[mIndex];
                    sendEvent(KeyEvent.ACTION_DOWN, keyCode,
                            KeyEvent.FLAG_LONG_PRESS);
                    sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
                } else {
                    performLongClick();
                }
            }
        }
    };
}
