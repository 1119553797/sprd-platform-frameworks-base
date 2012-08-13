
package com.android.systemui.statusbar;

import java.util.HashMap;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Scroller;

import com.android.systemui.R;

public class ToggleViewGroup extends ViewGroup {
    private static final String TAG = "ScrollLayout";

    private static final int PORT_CHILD_COUNT = 5;

    private static final int LAND_CHILD_COUNT = 5;

    private static final int NULL_CHILD_COUNT = 5;

    private static final int TOUCH_STATE_REST = 0;

    private static final int TOUCH_STATE_SCROLLING = 1;

    private static final int SNAP_VELOCITY = 600;

    private Scroller mScroller;

    private VelocityTracker mVelocityTracker;

    private int mCurScreen;

    private int mDefaultScreen = 0;

    private int mTouchState = TOUCH_STATE_REST;

    private int mTouchSlop;

    private float mLastMotionX;

    private int mChildWidth ;

    private int mChildHeight ;

    private boolean mAttached;

    private ToggleListener mToggleListener;

    private int mOrientation =Configuration.ORIENTATION_PORTRAIT;

    private final HashMap<Integer, View> mChildCache = new HashMap<Integer, View>();

    public ToggleViewGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ToggleViewGroup(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private boolean isLandscape() {
        return this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void init(){
        Log.d(TAG, "init()");
        final Context context = this.getContext();
        mScroller = new Scroller(context);
        mCurScreen = mDefaultScreen;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mToggleListener = new ToggleListener(this);
        mChildWidth = context.getResources().getDimensionPixelSize(R.dimen.status_bar_toggle_childview_width);
        mChildHeight = context.getResources().getDimensionPixelSize(R.dimen.status_bar_toggle_childview_height);
        Log.d(TAG, "init() mChildWidth = " + mChildWidth + "| mChildHeight = " + mChildHeight);
    }
    
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        Log.d(TAG, "onLayout changed = " + changed + " | l = " + l + "| t = " + t + "| r = " + r + "| b = " + b);
        mChildWidth = isLandscape() ? (r - l) / LAND_CHILD_COUNT : (r - l) / PORT_CHILD_COUNT;
        final int orientation =this.getResources().getConfiguration().orientation;
        if (changed || orientation!= this.mOrientation) {
            this.mOrientation = orientation;
            int childLeft = 0;
            final int childCount = getChildCount();
            Log.d(TAG, "onLayout childCount = " + childCount);
            mChildCache.clear();
            for (int i = 0; i < childCount; i++) {
                final View childView = getChildAt(i);
                mChildCache.put(childView.getId(), childView);
                if (childView.getVisibility() != View.GONE) {
                   final int childWidth = mChildWidth;
                	if(i % 2 == 1){
                		 childView.layout(childLeft -1 , 0, childLeft, mChildHeight);
                	}else{
                		 childView.setOnClickListener(mToggleListener);
//                         childView.setOnLongClickListener(mToggleListener);
                         childView.layout(childLeft, 0, childLeft + childWidth - 1, mChildHeight);
                         childLeft += childWidth;
                	}


                }
            }
            scrollTo(0, 0);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        // The children are given the same width and height as the scrollLayout
        final int count = getChildCount();
        Log.d(TAG, "onMeasure count = " + count);
        for (int i = 0; i < count; i++) {
            View view = getChildAt(i);
            Log.d(TAG, "onMeasure mChildWidth = " + mChildWidth + " | mChildHeight = " + mChildHeight);
            view.measure(mChildWidth, mChildHeight);
        }
        //scrollTo(0, 0);
    }

    /**
     * According to the position of current layout scroll to the destination
     * page.
     */
    public void snapToDestination() {
//        final int screenWidth = getWidth();
//        final int destScreen = (getScrollX() + screenWidth / 2) / screenWidth;
//        Log.d(TAG, "snapToDestination  screenWidth = " + screenWidth + "| destScreen = "  + destScreen);
//        snapToScreen(destScreen);
    }

    public void snapToScreen(int whichScreen) {
        // get the valid layout page
        // whichScreen = Math.max(0, Math.min(whichScreen, getChildCount() -
        // 1));
        Log.d(TAG, "snapToScreen whichScreen = " + whichScreen);
        whichScreen = Math.min(getMaxCount() - 1, whichScreen);
        if (getScrollX() != (whichScreen * getWidth())) {
            final int delta = whichScreen * getWidth() - getScrollX();
            mScroller.startScroll(getScrollX(), 0, delta, 0, Math.abs(delta) * 2);
            mCurScreen = whichScreen;
            invalidate();
        }
    }

    public void setToScreen(int whichScreen) {
        Log.d(TAG, "setToScreen whichScreen = " + whichScreen);
        whichScreen = Math.max(0, Math.min(whichScreen, getChildCount() - 1));
        mCurScreen = whichScreen;
        scrollTo(whichScreen * getWidth(), 0);
    }

    private int getMaxCount() {
        int showChildCount = isLandscape() ? LAND_CHILD_COUNT : PORT_CHILD_COUNT;
        int notNullChildCount = this.getChildCount()-NULL_CHILD_COUNT;
        return notNullChildCount % showChildCount == 0 ? notNullChildCount / showChildCount
                : notNullChildCount / showChildCount + 1;
    }

    public int getCurScreen() {
        return mCurScreen;
    }

    @Override
    public void computeScroll() {
        Log.d(TAG, "computeScroll");
        if (mScroller.computeScrollOffset()) {
            Log.d(TAG, "computeScroll123");
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            postInvalidate();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Log.d(TAG, "onTouchEvent + event = " + event.getAction());
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
        final int action = event.getAction();
        final float x = event.getX();
        switch (action) {
        case MotionEvent.ACTION_DOWN:
            Log.e(TAG, "event down!");
            if (!mScroller.isFinished()) {
                mScroller.abortAnimation();
            }
            mLastMotionX = x;
            break;

        case MotionEvent.ACTION_MOVE:
            if (getChildCount() > 5) {
                int deltaX = (int) (mLastMotionX - x);
                mLastMotionX = x;
                final int screenWidth = this.getWidth() * (this.getMaxCount() - 1);
                // when Fling first or Last screen ,wo reduce deltax = deltax/3
                // deltaX = (int) ((mScrollX < 0 || this.mScrollX >= screenWidth) ?
                // deltaX / 2.5f
                // : deltaX);
                int moveX = mScrollX + deltaX;
                if (moveX >= 0 && moveX <= screenWidth) {
                    scrollBy(deltaX, 0);
                }
            }
            break;
        case MotionEvent.ACTION_UP:
            onTouchUpEvent();
            break;
        case MotionEvent.ACTION_CANCEL:
            onTouchUpEvent();
            break;
        }
        return true;
    }

    private void onTouchUpEvent() {
        Log.d(TAG, "onTouchUpEvent");
        final VelocityTracker velocityTracker = mVelocityTracker;
        velocityTracker.computeCurrentVelocity(1000);
        int velocityX = (int) velocityTracker.getXVelocity();
        if (velocityX > SNAP_VELOCITY && mCurScreen > 0) {
            // Fling enough to move left
            Log.e(TAG, "snap left");
            snapToScreen(mCurScreen - 1);
        } else if (velocityX < -SNAP_VELOCITY
                && mCurScreen < getChildCount() - 1) {
            // Fling enough to move right
            Log.e(TAG, "snap right");
            snapToScreen(mCurScreen + 1);
        } else {
            snapToDestination();
        }
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }

        mTouchState = TOUCH_STATE_REST;
    }

    public View getChildView(int rId) {
        return mChildCache.get(rId);
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Log.d(TAG, "onDetachedFromWindow mAttached = " + mAttached);
        if (mAttached) {
            mToggleListener.unregisterReceiver();
            mAttached = false;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Log.d(TAG, "onAttachedToWindow mAttached = " + mAttached);
        if (!mAttached) {
            mAttached = true;
            mToggleListener.registerReceiver();
            mToggleListener.init();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {

        final int action = ev.getAction();
        Log.d(TAG, "onInterceptTouchEvent action = " + action);
        if ((action == MotionEvent.ACTION_MOVE) && (mTouchState != TOUCH_STATE_REST)) {
            Log.d(TAG, "onInterceptTouchEvent return true");
            return true;
        }

        final float x = ev.getX();
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                if (getChildCount() > 5) {
                    final int xDiff = (int) Math.abs(mLastMotionX - x);
                    if (xDiff > mTouchSlop) {
                        mTouchState = TOUCH_STATE_SCROLLING;
                    }
                }
                break;

            case MotionEvent.ACTION_DOWN:
                mLastMotionX = x;
                mTouchState = mScroller.isFinished() ? TOUCH_STATE_REST : TOUCH_STATE_SCROLLING;
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mTouchState = TOUCH_STATE_REST;
                break;
        }
        Log.d(TAG, "mTouchState != TOUCH_STATE_REST = " + (mTouchState != TOUCH_STATE_REST));
        return mTouchState != TOUCH_STATE_REST;
    }

}
