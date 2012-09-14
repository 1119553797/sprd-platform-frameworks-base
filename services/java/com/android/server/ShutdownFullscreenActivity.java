/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.server;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Slog;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;

import com.android.internal.app.ShutdownThread;

import android.media.MediaPlayer;
import android.content.ContentResolver;
import android.provider.Settings;
import java.io.IOException;

public class ShutdownFullscreenActivity extends Activity {

    private static final String TAG = "ShutdownFullScreenActivity";
    private boolean mConfirm;
    private int mSeconds = 15;
	private AlertDialog mDialog; 

    private Handler myHandler = new Handler();
    private Runnable myRunnable = new Runnable() {	
		@Override
		public void run() {
			mSeconds --;
			mDialog.setMessage(getString(com.android.internal.R.string.shutdown_after_seconds,mSeconds));

			if(mSeconds <=1){
				myHandler.removeCallbacks(myRunnable);
				Handler h = new Handler();
		        h.post(new Runnable() {
		            public void run() {
		                ShutdownThread.shutdown(ShutdownFullscreenActivity.this, mConfirm);
		            }
		        });
			}
			
			myHandler.postDelayed(myRunnable,1000);
		}
	};

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {	
		@Override
		public void onReceive(Context context, Intent intent) {
			if(Intent.ACTION_BATTERY_OKAY.equals(intent.getAction())|
				Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())){
				ShutDownWakeLock.releaseCpuLock();
				myHandler.removeCallbacks(myRunnable);
				unregisterReceiver(mReceiver);
				finish();
			}
		}
	};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		
        mConfirm = getIntent().getBooleanExtra(Intent.EXTRA_KEY_CONFIRM, false);
        Slog.i(TAG, "onCreate(): confirm=" + mConfirm);

		IntentFilter filter=new IntentFilter(Intent.ACTION_POWER_CONNECTED);
		filter.addAction(Intent.ACTION_BATTERY_OKAY);
        registerReceiver(mReceiver, filter);
		
		requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
		Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
		win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        setContentView(new View(this));
		mDialog=new AlertDialog.Builder(this).create();
		mDialog.setTitle(com.android.internal.R.string.power_off);
		mDialog.setMessage(getString(com.android.internal.R.string.shutdown_after_seconds,mSeconds));
		mDialog.setButton(DialogInterface.BUTTON_NEUTRAL,getText(com.android.internal.R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        myHandler.removeCallbacks(myRunnable);
                        dialog.cancel();
                        unregisterReceiver(mReceiver);
                        finish();
                    }});
        mDialog.setCancelable(false);
		mDialog.show();
        if(mConfirm == false){
        	myHandler.postDelayed(myRunnable, 1000);
        }
		myHandler.post(new Runnable(){
			public void run(){
        		final ContentResolver cr = getContentResolver();
        		String path=Settings.System.getString(cr,Settings.System.NOTIFICATION_SOUND);
				
				MediaPlayer mplayer=new MediaPlayer();
				try{
					mplayer.reset();
					if(null!=path){
						mplayer.setDataSource(path);
					}
					else{
						mplayer.setDataSource("/system/media/audio/notifications/Heaven.ogg");
					}
					mplayer.prepare();
					mplayer.start();
				}
				catch(IOException e){}
        	}
		});
    }
}
