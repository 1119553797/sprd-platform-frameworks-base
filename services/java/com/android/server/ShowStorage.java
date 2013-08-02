
package com.android.server;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.ServiceManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.Slog;
import android.view.View;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
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

    private long mLastApplicationSize = 0;

    private long mLastMailSize = 0;

    private long mLastSmsMmsSize =0;

    private long mLastSystemSize = 0;

    private long mLastFreeSize = 0;

    private int mHeight = -1;

    private int mAlertMessageHeight = -1;

    private int mTotalStatusHeight = -1;
    private Dialog mAlertDialog;
    private ProgressDialog mGetDetailsDialog;

    private boolean mDisplayDetails = false;

    private TextView mTextView;

    private boolean mStatePaused = false;
    private boolean mDataInitialized = false;
    private boolean mNeedUpdate = true;

    // used for reflash details info
    private Handler mHandler = new Handler();

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
	else {
		showAlertDialog();
	}

    }
    private void showAlertDialog() {
        if (mAlertDialog == null || !mAlertDialog.isShowing()) {
            mAlertDialog = new AlertDialog.Builder(ShowStorage.this)
                .setTitle(com.android.internal.R.string.low_internal_storage_view_title)
                .setMessage(com.android.internal.R.string.Low_Memory_Alert)
                .setPositiveButton(com.android.internal.R.string.low_internal_storage_display_details,
                    new DialogInterface.OnClickListener(){
                        public void onClick(DialogInterface dialog, int whichButton) {
                            mDisplayDetails = true;
                            getDetailsInfo();
                        }
                    })
                .setNegativeButton(com.android.internal.R.string.low_internal_storage_ignore,
                    new DialogInterface.OnClickListener(){
                        public void onClick(DialogInterface dialog, int whichButton) {
                            ShowStorage.this.finish();
                        }
                    })
                .setCancelable(false)
                .create();

            mAlertDialog.show();
        }
    }

    private void getDetailsInfo() {
        if (mGetDetailsDialog == null || !mGetDetailsDialog.isShowing()) {
            mGetDetailsDialog =
                ProgressDialog.show(ShowStorage.this,
                                    getText(com.android.internal.R.string.low_internal_storage_wait),
                                    getText(com.android.internal.R.string.low_internal_storage_updating_details),
                                    true,
                                    false);
            new Thread() {
                public void run() {
                    DeviceStorageMonitorService dsm =
                        (DeviceStorageMonitorService)ServiceManager.getService(DeviceStorageMonitorService.SERVICE);
                    long[] lastSize = null;
                    if (dsm != null) {
                        try {
                            dsm.beginGetShowStorageData();
                            lastSize = dsm.getShowStorageDataOK();
                            while(lastSize == null) {
                                Thread.sleep(500);
                                lastSize = dsm.getShowStorageDataOK();
                            }
                            getLastSizeFromArray(lastSize);
                            updateUI();
                        } catch (InterruptedException e) {
                        } catch (Exception e) {
                            Slog.e(TAG, "getDetailsInfo error: " + e);
                        }
                    }
                    mGetDetailsDialog.dismiss();
                }
            }.start();
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
	double mySizeOfKb;
	double mySizeOfMb;
	long sLen;
	String mStr;
	String[] mStrArry;
	String mText;

	final double mKiloSize = 1024.0;
	final double mMaxSizeLimit = 1000.0; // change Size format when Size is large
										 // than 1000.
	final double decimalBaseNuber = 10.0;

	mText = (String.valueOf(mTextView.getText()).split(" "))[0];
	if (mMemorySize < (int)mMaxSizeLimit) {
		mStr = mText + " " + String.valueOf(mMemorySize) + "Bytes";
	} else {
		mySizeOfKb = (double) (mMemorySize / mKiloSize);
		mySizeOfKb = (int) Math.round(mySizeOfKb * decimalBaseNuber) / decimalBaseNuber;
		if (mySizeOfKb < mMaxSizeLimit) {
			mStr = mText + " " + String.valueOf((int)mySizeOfKb) + "KB";
		} else {
			mySizeOfMb = (double) (mySizeOfKb / mKiloSize);
			mySizeOfMb = (int) Math.round(mySizeOfMb * decimalBaseNuber) / decimalBaseNuber;
			mStr = mText + " " + String.valueOf((int)mySizeOfMb) + "MB";
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
	mNeedUpdate = true;
    }

    @Override
    public void onResume() {
        if (mStatePaused || !mDataInitialized) {
            if (mDisplayDetails) {
                getDetailsInfo();
            }

            mStatePaused = false;
        }
        realUpdateUI();

        super.onResume();
  }

private void updateUI() {
	mHandler.post(new Runnable(){
		public void run() {
			realUpdateUI();
		}
	});
}
private void realUpdateUI() {
	if (mNeedUpdate == false) {
		return;
	}

	mApplicationBtn.setEnabled(mLastApplicationSize != 0);
	mMailBtn.setEnabled(mLastMailSize != 0);
	mSmsMmsBtn.setEnabled(mLastSmsMmsSize != 0);

	setAllStatusBar(mLastFreeSize / mTotalSize, mLastApplicationSize / mTotalSize,
					mLastMailSize / mTotalSize, mLastSmsMmsSize / mTotalSize, mLastSystemSize
					/ mTotalSize);
	setAllMemoryViewText();
	mNeedUpdate = false;
}

}


