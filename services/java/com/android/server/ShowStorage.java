
package com.android.server;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.ServiceManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.Slog;
import android.view.View;
import android.view.View.OnClickListener;

/**
 * 1.when /data filesystem free space is less than 5 percent
 * this actiity will be start automaticly by DeviceStorageMonitorService
 * 2.when /data filesystem free space is less than 10 percent
 * this actiity will be start with a notification by DeviceStorageMonitorService
 */
public class ShowStorage extends Activity {

    private static final String TAG = "ShowStorage";

    private float mTotalSize = -1;

    private long mLastApplicationSize = -1;

    private long mLastMailSize = -1;

    private long mLastSmsMmsSize = -1;

    private long mLastSystemSize = -1;

    private long mLastFreeSize = -1;

    private int mHeight = -1;

    private int mAlertMessageHeight = -1;

    private int mTotalStatusHeight = -1;

    private TextView mTextView;

    private boolean mStatePaused = false;
    private boolean mDataInitialized = false;

    private Button mApplicationBtn, mMailBtn, mSmsMmsBtn;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Slog.i(TAG, "finish for get ActivityResult");
        if (requestCode == 0) {
            switch (resultCode) {
                case RESULT_OK:
                    // ShowStorage.this.finish();
                default:
                    break;
            }
        }
    }

    private void sendAppManageIntent() {
        Intent intent = new Intent(ShowStorage.this, com.android.server.AppManage.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void sendMailManageIntent() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        ComponentName mCp = new ComponentName("com.android.email",
                "com.android.email.activity.Welcome");
        intent.setComponent(mCp);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void sendSmsAndMmsIntent() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        ComponentName mCp = new ComponentName("com.android.mms",
                "com.android.mms.ui.ConversationList");
        intent.setComponent(mCp);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.android.internal.R.layout.showstorage);

        final int layoutDevisionNumber = 4;

        mHeight = getWindowManager().getDefaultDisplay().getHeight();
        mAlertMessageHeight = mHeight / layoutDevisionNumber;
        mTotalStatusHeight = mHeight - mAlertMessageHeight;

        setAlertMessage();
        initButton();

        Bundle data = getIntent().getExtras();
        if (data != null) {
            long[] lastSize = (long[])data.getSerializable("lastSize");
            getLastSizeFromArray(lastSize);
        }
    }

    @Override
    public void onDestroy() {

        super.onDestroy();
    }

    private void setAlertMessage() {
        // TODO Auto-generated method stub
        LinearLayout.LayoutParams mLinearParams;
        mTextView = (TextView) findViewById(com.android.internal.R.id.Low_Memory_Alert);

        mLinearParams = (LinearLayout.LayoutParams) mTextView.getLayoutParams();
        mLinearParams.height = mAlertMessageHeight;
        mTextView.setLayoutParams(mLinearParams);
    }

    private void setAllStatusBar(float otherRatio, float applicationRatio, float mailRatio,
            float smsMmsRatio, float systemRatio) {
        // TODO Auto-generated method stub

        int mOtherHeight = -1;
        int mApplicationHeight = -1;
        int mMailHeight = -1;
        int mSmsMmsHeight = -1;
        int mSystemHeight = -1;

        final int mShortestHeight = 10; // minimum of status bar height
        final int mStatusBarNumber = 5;
        int myTotalStatusHeight = mTotalStatusHeight - (mStatusBarNumber * mShortestHeight);

        mApplicationHeight = (int) (myTotalStatusHeight * applicationRatio) + mShortestHeight;
        mMailHeight = (int) (myTotalStatusHeight * mailRatio) + mShortestHeight;
        mSmsMmsHeight = (int) (myTotalStatusHeight * smsMmsRatio) + mShortestHeight;
        mOtherHeight = (int) (myTotalStatusHeight * otherRatio) + mShortestHeight;
        mSystemHeight = myTotalStatusHeight - mApplicationHeight - mMailHeight - mSmsMmsHeight
                - mOtherHeight;

        mTextView = (TextView) findViewById(com.android.internal.R.id.Image_Free_Id);
        setStatusBar(mTextView, mOtherHeight);
        mTextView = (TextView) findViewById(com.android.internal.R.id.Image_Application_Id);
        setStatusBar(mTextView, mApplicationHeight);
        mTextView = (TextView) findViewById(com.android.internal.R.id.Image_Mail_Id);
        setStatusBar(mTextView, mMailHeight);
        mTextView = (TextView) findViewById(com.android.internal.R.id.Image_Sms_Mms_Id);
        setStatusBar(mTextView, mSmsMmsHeight);
        mTextView = (TextView) findViewById(com.android.internal.R.id.Image_System_Id);
        setStatusBar(mTextView, mSystemHeight);
    }

    private void setStatusBar(TextView mTextView, int mStatusHeight) {
        // TODO Auto-generated method stub
        LinearLayout.LayoutParams mLinearParams;
        mLinearParams = (LinearLayout.LayoutParams) mTextView.getLayoutParams();
        mLinearParams.height = mStatusHeight;
        mTextView.setLayoutParams(mLinearParams);
    }

    private void initButton() {
        // TODO Auto-generated method stub
        mApplicationBtn = (Button) findViewById(com.android.internal.R.id.Application_Manage_Button);
        mMailBtn = (Button) findViewById(com.android.internal.R.id.Mail_Manage_Button);
        mSmsMmsBtn = (Button) findViewById(com.android.internal.R.id.Sms_Mms_Button);

        mApplicationBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                sendAppManageIntent();
            }
        });
        mMailBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                sendMailManageIntent();
            }
        });
        mSmsMmsBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                sendSmsAndMmsIntent();
            }
        });
    }

    private void setAllMemoryViewText() {
        // TODO Auto-generated method stub

        mTextView = (TextView) findViewById(com.android.internal.R.id.Low_Memory_Free_Id);
        setMemoryViewText(mTextView, mLastFreeSize);
        mTextView = (TextView) findViewById(com.android.internal.R.id.Low_Memory_Application_Id);
        setMemoryViewText(mTextView, mLastApplicationSize);
        mTextView = (TextView) findViewById(com.android.internal.R.id.Low_Memory_Mail_Id);
        setMemoryViewText(mTextView, mLastMailSize);
        mTextView = (TextView) findViewById(com.android.internal.R.id.Low_Memory_Sms_Mms_Id);
        setMemoryViewText(mTextView, mLastSmsMmsSize);
        mTextView = (TextView) findViewById(com.android.internal.R.id.Low_Memory_System_Id);
        setMemoryViewText(mTextView, mLastSystemSize);
    }

    private void setMemoryViewText(TextView myTextView, long mMemorySize) {
        // TODO Auto-generated method stub
        long mySizeOfKb;
        float mySizeOfMb;
        long sLen;
        String mStr;
        String[] mStrArry;
        String mText;

        final int mKiloSize = 1024;
        final int mMaxSizeLimit = 1000; // change Size format when Size is large
                                        // than 1000.
        final int iDecimalBaseNuber = 10;
        final float fDecimalBaseNuber = 10f;

        mySizeOfKb = mMemorySize;
        mText = (String.valueOf(mTextView.getText()).split(" "))[0];
        if (mySizeOfKb < mMaxSizeLimit) {
            mStr = mText + " " + String.valueOf(mySizeOfKb) + "KB";
        } else {
            mySizeOfMb = (float) (mySizeOfKb / mKiloSize);
            mySizeOfMb = (int) Math.round(mySizeOfMb * iDecimalBaseNuber) / fDecimalBaseNuber;
            String myString = String.valueOf(mySizeOfMb);
            mStrArry = myString.split("\\.");

            sLen = mStrArry.length;

            if (sLen == 2) {
                if (mStrArry[1].equals("0")) {
                    mStr = mText + " " + mStrArry[0] + "MB";
                } else {
                    mStr = mText + " " + myString + "MB";
                }
            } else {
                mStr = mText + " " + String.valueOf(mySizeOfMb) + "MB";
            }
        }

        myTextView.setText(mStr);
    }

    @Override
    public void onPause() {
        mStatePaused = true;
        super.onPause();
    }

    private void getLastSizeFromArray(long[] lastSize)
    {
        final int mTOTALLOCATION = 0;
        final int mFREELOCATION = 1;
        final int mAPPLICATIONLOCATION = 2;
        final int mMAILLOCATION = 3;
        final int mSMSMMSLOCATION = 4;
        final int mSYSTEMLOCATION = 5;

        if (lastSize == null)
            return;

        mTotalSize = lastSize[mTOTALLOCATION];
        mLastFreeSize = lastSize[mFREELOCATION];
        mLastApplicationSize = lastSize[mAPPLICATIONLOCATION];
        mLastMailSize = lastSize[mMAILLOCATION];
        mLastSmsMmsSize = lastSize[mSMSMMSLOCATION];
        mLastSystemSize = lastSize[mSYSTEMLOCATION];
        mDataInitialized = true;
    }

    @Override
    public void onResume() {
        if (mStatePaused || !mDataInitialized) {
            long[] lastSize = null;

            DeviceStorageMonitorService dsm = (DeviceStorageMonitorService) ServiceManager
                    .getService(DeviceStorageMonitorService.SERVICE);
            if (dsm != null) {
                dsm.updateMemory();
                lastSize = dsm.callOK();
                if (dsm.getCallOKSuccuess()) {
                    getLastSizeFromArray(lastSize);
                }
            }

            mStatePaused = false;
        }
        if(mLastApplicationSize == 0){
            mApplicationBtn.setEnabled(false);
        }
        if(mLastMailSize == 0){
            mMailBtn.setEnabled(false);
        }
        if(mLastSmsMmsSize == 0){
            mSmsMmsBtn.setEnabled(false);
        }

        setAllStatusBar(mLastFreeSize / mTotalSize, mLastApplicationSize / mTotalSize,
                mLastMailSize / mTotalSize, mLastSmsMmsSize / mTotalSize, mLastSystemSize
                        / mTotalSize);
        setAllMemoryViewText();

        super.onResume();
    }
}
