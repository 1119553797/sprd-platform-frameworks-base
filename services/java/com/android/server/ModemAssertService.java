
package com.android.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;

public class ModemAssertService extends IModemAssertManager.Stub {
    private final Context mContext;

    public ModemAssertService(Context context) {
        mContext = context;
//        ServiceManager.addService("com.android.server.ModemAssertService", this);
        mContext.registerReceiver(mStartAssertReceiver, null);
    }

    private final BroadcastReceiver mStartAssertReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {
                String value;
                value = SystemProperties.get("persist.sys.sprd.modemreset", "default");
                if (value.equals("1")) {
                    ActivityManager am = (ActivityManager) context
                            .getSystemService(Context.ACTIVITY_SERVICE);
                    am.forceStopPackage("com.android.modemassert");
                    return;
                }
                Intent mIntent;
                mIntent = new Intent(context, ModemAssert.class);
                context.startService(mIntent);
            }
        }
    };
    
    private class ModemAssert extends Service {
        private NotificationManager mNm;
        private final String MTAG = "ModemClient";
        private static final int MODEM_ASSERT_ID = 1;
        private static final String MODEM_SOCKET_NAME = "modemd";
        private LocalSocket mSocket;
        private LocalSocketAddress mSocketAddr;
        private CharSequence AssertInfo;
        
        @Override
        public IBinder onBind(Intent intent) {
            // TODO Auto-generated method stub
            return mBinder;
        }
        public void onCreate() {
            LocalSocket s = null;
            LocalSocketAddress l = null;
            
            try {
                s = new LocalSocket();
                l = new LocalSocketAddress(MODEM_SOCKET_NAME,
                        LocalSocketAddress.Namespace.ABSTRACT);
            } catch (Exception ex){
                if (s == null)
                    Log.w(MTAG, "create client socket error\n");
            }
            mSocket = s;
            mSocketAddr = l;
            Thread thr = new Thread(null, mTask, "ModemAssert");
            thr.start();
        }
        
        public void onDestroy() {
            Log.d(MTAG, "modem assert service destroyed\n");
        }
        private final IBinder mBinder = new Binder(){
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply,
                    int flag) throws RemoteException {
                return super.onTransact(code, data, reply, flag);
            }
        };
        
        final private int BUF_SIZE = 128;
        Runnable mTask = new Runnable(){
            public void run(){
                byte[] buf = new byte[BUF_SIZE];
                for (;;) {
                    try {
                        mSocket.connect(mSocketAddr);
                        break;
                    } catch(IOException ioe){
                        Log.w(MTAG, "connect server error Exception"+ioe);
                        Log.w(MTAG, "connect server error\n");
                        SystemClock.sleep(10000);
                        continue;
                    }
                }
                
                synchronized(mBinder) {
                    for (;;){
                        int cnt = 0;
                        try{
                            InputStream is = mSocket.getInputStream();
                            Log.d(MTAG, "read from socket: \n");
                            cnt = is.read(buf, 0, BUF_SIZE);
                        } catch (IOException e){
                            Log.w(MTAG, "read exception\n");
                        }
                        String tempStr = null;
                        try {
                            tempStr = new String(buf, 0, cnt, "US-ASCII");
                        } catch (UnsupportedEncodingException e) {
                            Log.w(MTAG, "buf transfer char fail\n");
                        }
                        AssertInfo = tempStr;
                        showNotefication();
                        Log.v(MTAG, "exit modemassert app\n");
                        SystemClock.sleep(50000);
                        break;
                    }
                    ModemAssert.this.stopSelf();
                }
            }
        };
        
        private void showNotefication() {
            int icon = com.android.internal.R.drawable.modem_assert;
            CharSequence thicktext = "modem assert";
            mNm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            long when = System.currentTimeMillis();
            Notification notification = new Notification(icon, thicktext, when);
            
            Context context = getApplicationContext();
            CharSequence contentTitle = "modem assert";
            CharSequence contentText = AssertInfo;
            Intent notificationIntent = new Intent(this, ModemAssert.class);
            PendingIntent contentIntent =  PendingIntent.getService(context, 0, notificationIntent, 0);
            

            Log.e(MTAG, "Modem Assert!!!!\n");
            Log.e(MTAG, "" + contentText.toString());
            long[] vibrate = {0, 10000};
            notification.vibrate = vibrate;

            notification.defaults |= Notification.DEFAULT_SOUND;

            notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
            mNm.notify(MODEM_ASSERT_ID, notification);
        }
    }
}
