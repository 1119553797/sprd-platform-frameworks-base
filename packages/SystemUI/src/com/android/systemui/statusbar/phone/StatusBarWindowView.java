/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemProperties;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewRootImpl;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextSwitcher;

import com.android.systemui.ExpandHelper;
import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.policy.NotificationRowLayout;


public class StatusBarWindowView extends FrameLayout
{
    public static final String TAG = "StatusBarWindowView";
    public static final boolean DEBUG = BaseStatusBar.DEBUG;

    private ExpandHelper mExpandHelper;
    private NotificationRowLayout latestItems;
    private NotificationPanelView mNotificationPanel;
    private ScrollView mScrollView;

    /* SPRD：ADD for universe_ui_support on 20130831 @{ */
    protected boolean isUniverseSupport = false;
    private static String universeSupportKey = "universe_ui_support";
    private ExpandHelper mLatesExpandHelper;
    private ExpandHelper mOngoingExpandHelper;
    private NotificationRowLayout mLatestItems;
    private NotificationRowLayout mOngoingItems;
    private  boolean mCurrentIsLatestPile = true;
    /* @} */

    PhoneStatusBar mService;

    public StatusBarWindowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // SPRD：ADD for universe_ui_support
        isUniverseSupport = SystemProperties.getBoolean(universeSupportKey, false);
        setMotionEventSplittingEnabled(false);
        setWillNotDraw(!DEBUG);
    }

    @Override
    protected void onAttachedToWindow () {
        super.onAttachedToWindow();
        /* SPRD：ADD for universe_ui_support on 20130831 @{ */
        if (isUniverseSupport) {
            mLatestItems = (NotificationRowLayout)findViewById(R.id.custom_latestItems);
            mOngoingItems = (NotificationRowLayout)findViewById(R.id.custom_ongoingItems);
        } else {
            latestItems = (NotificationRowLayout) findViewById(R.id.latestItems);
        }
        mScrollView = (ScrollView) findViewById(R.id.scroll);
        mNotificationPanel = (NotificationPanelView) findViewById(R.id.notification_panel);
        int minHeight = getResources().getDimensionPixelSize(R.dimen.notification_row_min_height);
        int maxHeight = getResources().getDimensionPixelSize(R.dimen.notification_row_max_height);
        if (isUniverseSupport) {
            mLatesExpandHelper = new ExpandHelper(mContext, mLatestItems, minHeight, maxHeight);
            mLatesExpandHelper.setEventSource(this);
            mLatesExpandHelper.setScrollView(mScrollView);
            mOngoingExpandHelper = new ExpandHelper(mContext, mOngoingItems, minHeight, maxHeight);
            mOngoingExpandHelper.setEventSource(this);
            mOngoingExpandHelper.setScrollView(mScrollView);
        } else {
            mExpandHelper = new ExpandHelper(mContext, latestItems, minHeight, maxHeight);
            mExpandHelper.setEventSource(this);
            mExpandHelper.setScrollView(mScrollView);
        }
        /* @} */

        // We really need to be able to animate while window animations are going on
        // so that activities may be started asynchronously from panel animations
        final ViewRootImpl root = getViewRootImpl();
        if (root != null) {
            root.setDrawDuringWindowsAnimating(true);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        switch (event.getKeyCode()) {
        case KeyEvent.KEYCODE_BACK:
            if (!down) {
                mService.animateCollapsePanels();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean intercept = false;
        /* SPRD：ADD for universe_ui_support on 20130831 @{ */
        if (isUniverseSupport) {
            if (mCurrentIsLatestPile) {
                if (mNotificationPanel.isFullyExpanded()
                        && mScrollView.getVisibility() == View.VISIBLE) {
                    intercept = mLatesExpandHelper.onInterceptTouchEvent(ev);
                }
                if (!intercept) {
                    super.onInterceptTouchEvent(ev);
                }
                if (intercept) {
                    MotionEvent cancellation = MotionEvent.obtain(ev);
                    cancellation.setAction(MotionEvent.ACTION_CANCEL);
                    mLatestItems.onInterceptTouchEvent(cancellation);
                    cancellation.recycle();
                }
                return intercept;
            } else {
                if (mNotificationPanel.isFullyExpanded()
                        && mScrollView.getVisibility() == View.VISIBLE) {
                    intercept = mOngoingExpandHelper.onInterceptTouchEvent(ev);
                }
                if (!intercept) {
                    super.onInterceptTouchEvent(ev);
                }
                if (intercept) {
                    MotionEvent cancellation = MotionEvent.obtain(ev);
                    cancellation.setAction(MotionEvent.ACTION_CANCEL);
                    mOngoingItems.onInterceptTouchEvent(cancellation);
                    cancellation.recycle();
                }
                return intercept;
            }
        } else {
            if (mNotificationPanel.isFullyExpanded()
                    && mScrollView.getVisibility() == View.VISIBLE) {
                intercept = mExpandHelper.onInterceptTouchEvent(ev);
            }
            if (!intercept) {
                super.onInterceptTouchEvent(ev);
            }
            if (intercept) {
                MotionEvent cancellation = MotionEvent.obtain(ev);
                cancellation.setAction(MotionEvent.ACTION_CANCEL);
                latestItems.onInterceptTouchEvent(cancellation);
                cancellation.recycle();
            }
            return intercept;
        }
        /* @} */
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean handled = false;
        /* SPRD：ADD for universe_ui_support on 20130831 @{ */
        if (isUniverseSupport) {
            if (mCurrentIsLatestPile) {
                if (mNotificationPanel.isFullyExpanded()) {
                    handled = mLatesExpandHelper.onTouchEvent(ev);
                }
                if (!handled) {
                    handled = super.onTouchEvent(ev);
                }
                return handled;
            } else {
                if (mNotificationPanel.isFullyExpanded()) {
                    handled = mOngoingExpandHelper.onTouchEvent(ev);
                }
                if (!handled) {
                    handled = super.onTouchEvent(ev);
                }
                return handled;
            }
        } else {
            if (mNotificationPanel.isFullyExpanded()) {
                handled = mExpandHelper.onTouchEvent(ev);
            }
            if (!handled) {
                handled = super.onTouchEvent(ev);
            }
            return handled;
        }
        /* @} */
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (DEBUG) {
            Paint pt = new Paint();
            pt.setColor(0x80FFFF00);
            pt.setStrokeWidth(12.0f);
            pt.setStyle(Paint.Style.STROKE);
            canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), pt);
        }
    }

    public void cancelExpandHelper() {
        if (mExpandHelper != null) {
            mExpandHelper.cancel();
        }
    }

    /**
      * SPRD：set current pile @{ 
      * param value the currentpile
      */
    public void setCurrentIsLatestPile(boolean value) {
        mCurrentIsLatestPile = value;
    }
    /** @} */
}

