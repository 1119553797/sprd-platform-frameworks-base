/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.media;

import java.io.FileOutputStream;
import java.io.FileDescriptor;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
//import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Bundle;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.Registrant;
import android.os.SystemProperties;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.hardware.Camera;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.SecurityException;
import java.util.Set;
import java.lang.ref.WeakReference;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;


public class MediaPhone extends Handler
{
    static {
        System.loadLibrary("media_jni");
        native_init();
    }

    private final static String TAG = "MediaPhone";
    // Name of the remote interface for the media phone. Must be kept
    // in sync with the 2nd parameter of the IMPLEMENT_META_INTERFACE
    // macro invocation in IMediaPhone.cpp
    private final static String IMEDIA_PHONE = "android.media.IMediaPhone";
	private final static String MSG_TAG = "resend";

	private final static String CAMERA_OPEN_STR = "open_:camera_";
	private final static String CAMERA_CLOSE_STR = "close_:camera_";

    private final static boolean DEBUG_WITHOUT_MODEM = false;

    private int mNativeContext; // accessed by native methods
    private int mListenerContext; // accessed by native methods
    private Surface mRemoteSurface; // accessed by native methods
    private Surface mLocalSurface;
    private EventHandler mEventHandler;
    private PowerManager.WakeLock mWakeLock = null;
    private boolean mScreenOnWhilePlaying;
    private boolean mStayAwake;
    private boolean mbCameraLock = false;
    private final Object mCameraLock = new Object();

    private CommandsInterface mCm;	
    private Message msgTracker = null;
    private static int ARG_SKIP_MSGTRACKER = -1;

    private int mCodecCount = 0;
	public enum CodecState {
		CODEC_IDLE ,		
		CODEC_OPEN,	
		CODEC_START, 	
		CODEC_CLOSE;

		public String toString() {
			switch(this) {
				case CODEC_IDLE:
					return "CODEC_IDLE";
				case CODEC_OPEN:
					return "CODEC_OPEN";
				case CODEC_START:
					return "CODEC_START";
				case CODEC_CLOSE:
					return "CODEC_CLOSE";
			}
			return "CODEC_IDLE";
		}
	}

	
   private CodecState mCodecState = CodecState.CODEC_IDLE;

   
   private Thread mThread;
    /**
     * Default constructor. Consider using one of the create() methods for
     * synchronously instantiating a MediaPhone from a Uri or resource.
     * <p>When done with the MediaPhone, you should call  {@link #release()},
     * to free the resources. If not released, too many MediaPhone instances may
     * result in an exception.</p>
     */
    public MediaPhone(CommandsInterface ril) {

        Looper looper;
        if ((looper = Looper.myLooper()) != null) {
            mEventHandler = new EventHandler(this, looper);
        } else if ((looper = Looper.getMainLooper()) != null) {
            mEventHandler = new EventHandler(this, looper);
        } else {
            mEventHandler = null;
        }
        mCm = ril;

        /* Native setup requires a weak reference to our object.
         * It's easier to create it here than in C++.
         */
        native_setup(new WeakReference<MediaPhone>(this));

	mThread = new Thread(new Runnable() {
			public void run() {
        			Log.d(TAG, "mThread E");
				do {
					int ret = native_waitRequestForAT();
                    if(ret == AT_NONE){
	        			Log.d(TAG, "vt_pipe ret error, exit thread");
                        break;
                    } else {
	        			Log.d(TAG, "vt_pipe ret: " + ret);
                    }
					switch (ret) {
						case AT_REPORT_IFRAME:
							mCm.controlIFrame(true, false, null);
							break;
						case AT_REQUEST_IFRAME:
							mCm.controlIFrame(false, true, null);
							break;
						case AT_BOTH_IFRAME:
							mCm.controlIFrame(true, true, null);
							break;
					}
					if (mStopWaitRequestForAT) {
        					Log.d(TAG, "mStopWaitRequestForAT");
						break;
					}
				} while(true);
        			Log.d(TAG, "mThread X");
			}
		});
		mThread.start();
    }

    /**
     * Sets a Camera to use for recording. Use this function to switch
     * quickly between preview and capture mode without a teardown of
     * the camera object. Must call before prepare().
     *
     * @param c the Camera to use for recording
     */
    public native void setCamera(Camera c);

    /*
     * Update the MediaPhone ISurface. Call after updating mRemoteSurface.
     */
    private native void _setRemoteSurface();
	
    private native void _setLocalSurface();

    /**
     * Sets the SurfaceHolder to use for displaying the video portion of the media.
     * This call is optional. Not calling it when playing back a video will
     * result in only the audio track being played.
     *
     * @param sv the SurfaceHolder to use for video display
     */
    public void setRemoteDisplay(Surface sv) {
        mRemoteSurface = sv;
        _setRemoteSurface();
    }

    /**
     * Sets a Surface to show a preview of recorded media (video). Calls this
     * before prepare() to make sure that the desirable preview display is
     * set.
     *
     * @param sv the Surface to use for the preview
     */
    public void setLocalDisplay(Surface sv) {
        mLocalSurface = sv;
		_setLocalSurface();
    }

    /**
     * Convenience method to create a MediaPhone for a given Uri.
     * On success, {@link #prepare()} will already have been called and must not be called again.
     * <p>When done with the MediaPhone, you should call  {@link #release()},
     * to free the resources. If not released, too many MediaPhone instances will
     * result in an exception.</p>
     *
     * @param ril the ril to use
     * @param url the Url from which to get the datasource
     * @return a MediaPhone object, or null if creation failed
     */
     /**
	 * {@hide}
	 */
    public static MediaPhone create(CommandsInterface ril, String url) {
        Log.d(TAG, "create" + url);
        return create (ril, url, null, null);
    }

    /**
     * Convenience method to create a MediaPhone for a given Uri.
     * On success, {@link #prepare()} will already have been called and must not be called again.
     * <p>When done with the MediaPhone, you should call  {@link #release()},
     * to free the resources. If not released, too many MediaPhone instances will
     * result in an exception.</p>
     *
     * @param ril the ril to use
     * @param url the Url from which to get the datasource
     * @param remoteHolder the SurfaceHolder to use for displaying the video
     * @param localSurface the Surface to use for preview the camera
     * @return a MediaPhone object, or null if creation failed
     */
    /**
	 * {@hide}
	 */
    public static MediaPhone create(CommandsInterface ril, String url, Surface remoteSurface, Surface localSurface) {

        try {
            MediaPhone mp = new MediaPhone(ril);
            mp.setComm(url, url);
            if (remoteSurface != null) {
                mp.setRemoteDisplay(remoteSurface);
            }
            if (localSurface != null) {
            	mp.setLocalDisplay(localSurface);
            }
            //mp.mCm.setOnVPData(mp.mEventHandler, MEDIA_UNSOL_DATA, null);
            mp.mCm.setOnVPCodec(mp.mEventHandler, MEDIA_UNSOL_CODEC, null);
            mp.mCm.setOnVPString(mp.mEventHandler, MEDIA_UNSOL_STR, null);
            mp.mCm.setOnVPRemoteMedia(mp.mEventHandler, MEDIA_UNSOL_REMOTE_VIDEO, null);
            mp.mCm.setOnVPMMRing(mp.mEventHandler, MEDIA_UNSOL_MM_RING, null);
            mp.mCm.setOnVPRecordVideo(mp.mEventHandler, MEDIA_UNSOL_RECORD_VIDEO, null);
            mp.mCm.setOnVPMediaStart(mp.mEventHandler, MEDIA_UNSOL_MEDIA_START, null);
            return mp;
        } catch (IOException ex) {
            Log.d(TAG, "create failed:", ex);
            // fall through
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "create failed:", ex);
            // fall through
        } catch (SecurityException ex) {
            Log.d(TAG, "create failed:", ex);
            // fall through
        }
        return null;
    }

    private native void setParameters(String nameValuePair);

    public native void prepare() throws IllegalStateException;

    /**
     * Prepares the player for playback, asynchronously.
     *
     * After setting the datasource and the display surface, you need to either
     * call prepare() or prepareAsync(). For streams, you should call prepareAsync(),
     * which returns immediately, rather than blocking until enough data has been
     * buffered.
     *
     * @throws IllegalStateException if it is called in an invalid state
     */
    public native void prepareAsync() throws IllegalStateException;

    /**
     * Sets the data source as a content Uri.
     *
     * @param uriIn the Content URI of the input data you want to play
     * @param uriOut the Content URI of the output data you want to play
     * @throws IllegalStateException if it is called in an invalid state
     */
    /*
    public void setComm(String uriIn, String uriOut)
        throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
        String pathIn, pathOut;
        String scheme = uriIn.getScheme();
        if (scheme == null || scheme.equals("file")) {
            pathIn = uriIn.getPath();
        } else {
            throw (new IllegalArgumentException("uri is not a file type"));
        }
        scheme = uriOut.getScheme();
        if (scheme == null || scheme.equals("file")) {
            pathOut = uriOut.getPath();
        } else {
            throw (new IllegalArgumentException("uri is not a file type"));
        }

        setComm(pathIn, pathOut);
    }
    */
    /**
     * Sets the data source (file-path or http/rtsp URL) to use.
     *
     * @param path_in the input path of the file, or the http/rtsp URL of the stream you want to play
     * @param path_out the output path of the file, or the http/rtsp URL of the stream you want to play
     * @throws IllegalStateException if it is called in an invalid state
     */
    public native void setComm(String path_in, String path_out) throws IOException, IllegalArgumentException, IllegalStateException;

    /**
     * Starts or resumes playback. If playback had previously been paused,
     * playback will continue from where it was paused. If playback had
     * been stopped, or never started before, playback will start at the
     * beginning.
     *
     * @throws IllegalStateException if it is called in an invalid state
     */
    public void startPlayer() throws IllegalStateException {
        stayAwake(true);
        _startPlayer();
    }

    public void startRecorder() throws IllegalStateException {
        stayAwake(true);
        _startRecorder();
    }

    private native void _startPlayer() throws IllegalStateException;
	
    private native void _startRecorder() throws IllegalStateException;

    /**
     * Stops playback after playback has been stopped or paused.
     *
     * @throws IllegalStateException if the internal player engine has not been
     * initialized.
     */
    public void stop() throws IllegalStateException {
        stayAwake(false);
        _stop();
    }

    private native void _stop() throws IllegalStateException;

	private Message internalGetMessage(Message result, int what) {
		if (result == null) return null;
		
        Message msg = Message.obtain(mEventHandler, what);		
		Bundle bundle = msg.getData();
		bundle.putParcelable(MSG_TAG, result);
		return msg;
	}
	
    public void sendString(String str, Message result) {
        Log.d(TAG, "sendString");
        stayAwake(true);

        mCm.sendVPString(str, internalGetMessage(result, MEDIA_SOL_SENDSTRING));
    }

    public void controlLocalVideo(boolean bEnable, boolean bReplaceImg, Message result) {
        Log.d(TAG, "controlLocalVideo");
        stayAwake(true);

		if (bReplaceImg){
			if (bEnable)
	        	sendString("open_:camera_", Message.obtain(mEventHandler, MEDIA_SOL_SENDSTRING));
			else
				sendString("close_:camera_", Message.obtain(mEventHandler, MEDIA_SOL_SENDSTRING));
		}
			
        mCm.controlVPLocalMedia(1, bEnable?1:0, false, internalGetMessage(result, MEDIA_SOL_CONTROL_LOCALVIDEO));
    }

    public void controlLocalAudio(boolean bEnable, Message result) {
        Log.d(TAG, "controlLocalAudio");
        stayAwake(true);
		
        mCm.controlVPLocalMedia(0, bEnable?1:0, false, internalGetMessage(result, MEDIA_SOL_CONTROL_LOCALAUDIO));
    }

    public void recordVideo(boolean bStart, Message result) {
        Log.d(TAG, "recordVideo");
        stayAwake(true);
		
        mCm.recordVPVideo(bStart, internalGetMessage(result, MEDIA_SOL_RECORD_VIDEO));
    }
	
    public void recordAudio(boolean bStart, int mode, Message result) {
        Log.d(TAG, "recordAudio");
        stayAwake(true);
		
        mCm.recordVPAudio(bStart, mode, internalGetMessage(result, MEDIA_SOL_RECORD_AUDIO));
    }

    public void test(int flag, int value, Message result) {
        Log.d(TAG, "test");
        stayAwake(true);
		
        mCm.testVP(flag, value, internalGetMessage(result, MEDIA_SOL_NOP));
    }	

    public void codec(int type, Bundle param, Message result) {
        Log.d(TAG, "codec " + type);
        //stayAwake(false);

        mCm.codecVP(type, param, internalGetMessage(result, MEDIA_SOL_CODEC));	

        if ((DEBUG_WITHOUT_MODEM) && (msgTracker != null)) {
            msgTracker.sendToTarget();
            msgTracker = null;
        }
    }

    /**
     * Set the low-level power management behavior for this MediaPhone.  This
     * can be used when the MediaPhone is not playing through a SurfaceHolder
     * set with  and thus can use the
     * high-level  feature.
     *
     * <p>This function has the MediaPhone access the low-level power manager
     * service to control the device's power usage while playing is occurring.
     * The parameter is a combination of  wake flags.
     * Use of this method requires 
     * permission.
     * By default, no attempt is made to keep the device awake during playback.
     *
     * @param context the Context to use
     * @param mode    the power/wake mode to set
     * @see android.os.PowerManager
     */
    public void setWakeMode(Context context, int mode) {
        /*boolean washeld = false;
        if (mWakeLock != null) {
            if (mWakeLock.isHeld()) {
                washeld = true;
                mWakeLock.release();
            }
            mWakeLock = null;
        }

        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(mode|PowerManager.ON_AFTER_RELEASE, MediaPhone.class.getName());
        mWakeLock.setReferenceCounted(false);
        if (washeld) {
            mWakeLock.acquire();
        }*/
    }

    private void stayAwake(boolean awake) {
        /*if (mWakeLock != null) {
            if (awake && !mWakeLock.isHeld()) {
                mWakeLock.acquire();
            } else if (!awake && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
        mStayAwake = awake;*/
    }

	public void setSubtitutePic(String fn){
		String oldValue = SystemProperties.get("gsm.camera.picture");
		SystemProperties.set("gsm.camera.picture", fn);
		String newValue = SystemProperties.get("gsm.camera.picture");
		Log.d(TAG, "oldValue: " + oldValue + ", newValue" + newValue);		
	}

    /**
     * Returns the width of the video.
     *
     * @return the width of the video, or 0 if there is no video,
     * no display surface was set, or the width has not been determined
     * yet. The OnVideoSizeChangedListener can be registered via
     * {@link #setOnVideoSizeChangedListener(OnVideoSizeChangedListener)}
     * to provide a notification when the width is available.
     */
    public native int getVideoWidth();

    /**
     * Returns the height of the video.
     *
     * @return the height of the video, or 0 if there is no video,
     * no display surface was set, or the height has not been determined
     * yet. The OnVideoSizeChangedListener can be registered via
     * {@link #setOnVideoSizeChangedListener(OnVideoSizeChangedListener)}
     * to provide a notification when the height is available.
     */
    public native int getVideoHeight();

    /**
     * Releases resources associated with this MediaPhone object.
     * It is considered good practice to call this method when you're
     * done using the MediaPhone.
     */
    public void release() {
        Log.d(TAG, "release");
        stayAwake(false);
	mStopWaitRequestForAT = true;
        mOnErrorListener = null;
        mOnMediaInfoListener = null;
		mOnCallEventListener = null;
        mOnVideoSizeChangedListener = null;

        //mCm.unSetOnVPData(mEventHandler);
        mCm.unSetOnVPCodec(mEventHandler);
        mCm.unSetOnVPString(mEventHandler);
        mCm.unSetOnVPRemoteMedia(mEventHandler);
        mCm.unSetOnVPMMRing(mEventHandler);
        mCm.unSetOnVPRecordVideo(mEventHandler);
        mCm.unSetOnVPMediaStart(mEventHandler);
        _release();
    }

    public void enableRecord(boolean isEnable, int type, String fn) throws IllegalStateException, IOException
	{
        Log.d(TAG, "enableRecord(" + isEnable + ", " + type + ", " + fn);
		if (isEnable){
			if (fn != null) {
	            FileOutputStream fos = new FileOutputStream(fn);
	            try {
					_enableRecord(isEnable, type, fos.getFD());
                    mCm.controlIFrame(false, true, null);
	            } finally {
	                fos.close();
	            }
	        }
		} else {
			_enableRecord(isEnable, type, null);
		}
    }

    public boolean lockCamera() {
	Log.d(TAG, "lockCamera() E");
	synchronized(mCameraLock) {
		if (mbCameraLock) {
			Log.e(TAG, "lockCamera() fail, already locked");
			return false;
		}
		if (mCodecState != CodecState.CODEC_START) {
			Log.e(TAG, "lockCamera() fail, mCodecState: " + mCodecState);
			return false;		
		}
		mbCameraLock = true;
	}
	Log.d(TAG, "lockCamera() X");
	return true;
    }

    public boolean unlockCamera() {
	Log.d(TAG, "unlockCamera() E");
	synchronized(mCameraLock) {
		if (!mbCameraLock) {
			Log.e(TAG, "lockCamera() fail, already unlocked");
			return false;
		}
		mbCameraLock = false;
		mCameraLock.notify();
	}
	Log.d(TAG, "unlockCamera() X");
	return true;
    }
    private native void _release();

    /**
     * Sets the audio stream type for this MediaPhone. See {@link AudioManager}
     * for a list of stream types.
     *
     * @param streamtype the audio stream type
     * @see android.media.AudioManager
     */
    public native void setAudioStreamType(int streamtype);

    /**
     * Sets the volume on this player.
     * This API is recommended for balancing the output of audio streams
     * within an application. Unless you are writing an application to
     * control user settings, this API should be used in preference to
     * {@link AudioManager#setStreamVolume(int, int, int)} which sets the volume of ALL streams of
     * a particular type. Note that the passed volume values are raw scalars.
     * UI controls should be scaled logarithmically.
     *
     * @param leftVolume left volume scalar
     * @param rightVolume right volume scalar
     */
    public native void setVolume(float leftVolume, float rightVolume);

    /**
     * Currently not implemented, returns null.
     * @deprecated
     * @hide
     */
    public native Bitmap getFrameAt(int msec) throws IllegalStateException;

    /**
     * Enable or disable remote video recording.
     *
     * @param isEnable enable or disable
     * @param fn file name for result video file
     */
    private native void _enableRecord(boolean isEnable, int type, FileDescriptor fd);

    /**
     * Start up link data transfer.
     * This API is used to swith the camera between back, front or fake.
     * To switch camera, stopUpLink() is called first, then call setCamera()
     * with the newly created camera instance, finally call startUpLink().
     * This API will return successfully when the up link is already started.
     * The fake camera is used for sending alternative picture or video(not implemented yet) to remote.
     *
     */
    public native void startUpLink();

    /**
     * Stop up link data transfer.
     * This API is used to swith the camera between back, front or fake.
     * To switch camera, stopUpLink() is called first, then call setCamera()
     * with the newly created camera instance, finally call startUpLink().
     * This API will return successfully when the up link is already started.
     * The fake camera is used for sending alternative picture or video(not implemented yet) to remote.
     *
     */
    public native void stopUpLink();
	
    public native void startDownLink();
	
    public native void stopDownLink();

	public native void setDecodeType(int type);
	
	public native void setEncodeType(int type);

	public native void setCameraParam(String key, int value);
	
	public native int getCameraParam(String key);

    private static native final void native_init();
    private native final void native_setup(Object mediaphone_this);
    private native final void native_finalize();

    private static final int AT_NONE = 0;
    private static final int AT_TIMEOUT = -1;
    private static final int AT_SELECT_ERR = -2;
    private static final int AT_REPORT_IFRAME = 1;
    private static final int AT_REQUEST_IFRAME = 2;
    private static final int AT_BOTH_IFRAME = 3;
    private native final int native_waitRequestForAT();
    private boolean mStopWaitRequestForAT = false;

    @Override
    protected void finalize() { native_finalize(); }

    /* Do not change these values without updating their counterparts
     * in include/media/mediaphone.h!
     */
    private static final int MEDIA_PREPARED = 1;
    //private static final int MEDIA_CONNECT_COMPLETE = 2;
    //private static final int MEDIA_DISCONNECT_COMPLETE = 3;
    private static final int MEDIA_SET_VIDEO_SIZE = 2;
    private static final int MEDIA_ERROR = 100;
    private static final int MEDIA_INFO = 200;

    // solicited events
    private static final int MEDIA_SOL_NOP	          = 10;
    private static final int MEDIA_SOL_CODEC         = 11;
	private static final int MEDIA_SOL_SENDSTRING	= 12;
	private static final int MEDIA_SOL_CONTROL_LOCALVIDEO	= 13;
	private static final int MEDIA_SOL_CONTROL_LOCALAUDIO	= 14;
	private static final int MEDIA_SOL_RECORD_VIDEO	= 15;
	private static final int MEDIA_SOL_RECORD_AUDIO	= 16;

    // unsolicited events
    private static final int MEDIA_UNSOL_DATA = 20;
    private static final int MEDIA_UNSOL_CODEC = 21;
    private static final int MEDIA_UNSOL_STR = 22;
    private static final int MEDIA_UNSOL_REMOTE_VIDEO = 23;
    private static final int MEDIA_UNSOL_MM_RING = 24;
    private static final int MEDIA_UNSOL_RECORD_VIDEO = 25;
    private static final int MEDIA_UNSOL_MEDIA_START = 26;

    // codec request type
    public static final int CODEC_OPEN = 1;
    public static final int CODEC_CLOSE = 2;
    public static final int CODEC_SET_PARAM = 3;

    private class EventHandler extends Handler
    {
        private MediaPhone mMediaPhone;

        public EventHandler(MediaPhone mp, Looper looper) {
            super(looper);
            mMediaPhone = mp;
        }

		private void internalResendMessage(Message msg){
			Bundle bundle = msg.getData();
			Message tempMsg = (Message)bundle.getParcelable(MSG_TAG);

			if (tempMsg == null) return;
			
			AsyncResult ar = (AsyncResult) msg.obj;
			//Throwable e = ar.exception;
			tempMsg.obj = ar;
			if (tempMsg != null){
				tempMsg.sendToTarget();
			}
		}
		
        @Override
        public void handleMessage(Message msg) {
            if (mMediaPhone.mNativeContext == 0) {
                Log.w(TAG, "mediaphone went away with unhandled events");
                return;
            }
            Log.d(TAG, "handleMessage " + msg);

            AsyncResult ar;
            ar = (AsyncResult) msg.obj;
			
            switch(msg.what) {
            case MEDIA_PREPARED:
                //todo: call ril send AT
                Log.d(TAG, "receive MEDIA_PREPARED");
	        	codec(CODEC_OPEN, null, null);
				
                if (DEBUG_WITHOUT_MODEM) {
                    Message m = mEventHandler.obtainMessage(MEDIA_UNSOL_CODEC, CODEC_SET_PARAM, 0);
                    mEventHandler.sendMessage(m);
                }
                return;

            case MEDIA_SET_VIDEO_SIZE:
                if (mOnVideoSizeChangedListener != null)
                    mOnVideoSizeChangedListener.onVideoSizeChanged(mMediaPhone, msg.arg1, msg.arg2);
                return;

            case MEDIA_ERROR:
                // For PV specific error values (msg.arg2) look in
                // opencore/pvmi/pvmf/include/pvmf_return_codes.h
                Log.e(TAG, "Error (" + msg.arg1 + "," + msg.arg2 + ")");
                boolean error_was_handled = false;
                if (mOnErrorListener != null) {
                    error_was_handled = mOnErrorListener.onError(mMediaPhone, msg.arg1, msg.arg2);
                }
                stayAwake(false);
                return;

            case MEDIA_INFO:
                // For PV specific code values (msg.arg2) look in
                // opencore/pvmi/pvmf/include/pvmf_return_codes.h
                Log.i(TAG, "Info (" + msg.arg1 + "," + msg.arg2 + ")");
                if (mOnMediaInfoListener != null) {
                    mOnMediaInfoListener.onMediaInfo(mMediaPhone, msg.arg1, msg.arg2);
                }
                // No real default action so far.
                return;
				
            // following is messages from RIL.java
            case MEDIA_SOL_NOP: 
			case MEDIA_SOL_CODEC:
			case MEDIA_SOL_SENDSTRING:				
			case MEDIA_SOL_CONTROL_LOCALVIDEO: 
			case MEDIA_SOL_CONTROL_LOCALAUDIO: 
			case MEDIA_SOL_RECORD_VIDEO: 
			case MEDIA_SOL_RECORD_AUDIO:
				Throwable e = ar.exception;
		        if (e != null) {
			        if (e instanceof CommandException) {
//						mOnErrorListener.onError(mMediaPhone, msg.what, ((CommandException)e).getCommandError());
			        } else {
			            Log.d(TAG, "handleMessage(), other exceptions");
			            return;
			        }
		        }
                internalResendMessage(msg);
                stayAwake(false);
                return;
				
            case MEDIA_UNSOL_DATA: {
                String indication = (String)ar.result;
		  Log.d(TAG, "handleMessage(MEDIA_UNSOL_DATA), indication: " + indication);
                return;
            }

            case MEDIA_UNSOL_CODEC: {
				if (ar == null) {
					Log.d(TAG, "handleMessage(MEDIA_UNSOL_CODEC), ar == null");
					return;
				}
                int[] params = (int[])ar.result;
				Log.d(TAG, "handleMessage(MEDIA_UNSOL_CODEC), params: " + params[0] + ", length: " + params.length + ", mCodecCount: " + mCodecCount);
				if (params[0] == 3){	// config message
					if (params.length >= 4){
						if (params[2] == 1) {	// decode
							setDecodeType(params[3]);
							mCodecCount++;
						} else {	// encode
							setEncodeType(params[3]);
							mCodecCount++;
						}
	                	onCodecRequest(params[0], params[2]);
					}
				} else {
				    mCodecCount = 0;
	                onCodecRequest(params[0], 0);
				}
                return;
            }

            case MEDIA_UNSOL_STR: {
				if (ar == null) {
					Log.d(TAG, "handleMessage(MEDIA_UNSOL_STR), ar == null");
					return;
				}
				
                String str = (String)ar.result;
				Log.d(TAG, "handleMessage(MEDIA_UNSOL_STR), str == " + str);
				
				if (str.equals(CAMERA_OPEN_STR)){
					if (mOnCallEventListener != null){
						mOnCallEventListener.onCallEvent(mMediaPhone, MEDIA_CALLEVENT_CAMERAOPEN, null);
					}
				} else if (str.equals(CAMERA_CLOSE_STR)){
					if (mOnCallEventListener != null){
						mOnCallEventListener.onCallEvent(mMediaPhone, MEDIA_CALLEVENT_CAMERACLOSE, null);
					}
				} else if (str.length() > 0){
					if (mOnCallEventListener != null){
						mOnCallEventListener.onCallEvent(mMediaPhone, MEDIA_CALLEVENT_STRING, str);
					}
				}
                return;
            }

            case MEDIA_UNSOL_REMOTE_VIDEO: {
                int[] params = (int[])ar.result;
                int datatype = params[0];
                int sw = params[1];
                int indication = 0;
				
                if (params.length > 2)
                    indication = params[2];

				if ((datatype == 1) && (sw == 0) && (indication == 0)){
				}
                return;
            }

            case MEDIA_UNSOL_MM_RING: {
                int[] params = (int[])ar.result;
                int timer = params[0];
                return;
            }

            case MEDIA_UNSOL_RECORD_VIDEO: {
                int[] params = (int[])ar.result;
                int indication = params[0];
                return;
            }

            case MEDIA_UNSOL_MEDIA_START: {				
				mOnCallEventListener.onCallEvent(mMediaPhone, MEDIA_CALLEVENT_MEDIA_START, null);
                return;
            }

            default:
                Log.e(TAG, "Unknown message type " + msg.what);
                return;
            }
        }
    }

    /**
     * Called from native code when an interesting event happens.  This method
     * just uses the EventHandler system to post the event back to the main app thread.
     * We use a weak reference to the original MediaPhone object so that the native
     * code is safe from the object disappearing from underneath it.  (This is
     * the cookie passed to native_setup().)
     */
    private static void postEventFromNative(Object mediaphone_ref,
                                            int what, int arg1, int arg2, Object obj)
    {
        MediaPhone mp = (MediaPhone)((WeakReference)mediaphone_ref).get();
        if (mp == null) {
            return;
        }

        if (mp.mEventHandler != null) {
            Message m = mp.mEventHandler.obtainMessage(what, arg1, arg2, obj);
            mp.mEventHandler.sendMessage(m);
        }
    }
	
    public void onCodecRequest(int type, int param)
    {
        Log.d(TAG, "onCodecRequest:" + type + ", " + param + ", mCodecState: " + mCodecState);
        switch (type) {
        case CODEC_OPEN:
		if (mOnCallEventListener != null){
			mOnCallEventListener.onCallEvent(this, MEDIA_CALLEVENT_CODEC_OPEN, null);
		}
            try {
                prepareAsync();
		mCodecState = CodecState.CODEC_OPEN;
            } catch (IllegalStateException ex) {
                Log.d(TAG, "prepareAsync fail " + ex);
            }
	        //codec(CODEC_OPEN, null, null);
            break;

        case CODEC_SET_PARAM:
		if (mOnCallEventListener != null){
			if (param == 1){
				mOnCallEventListener.onCallEvent(this, MEDIA_CALLEVENT_CODEC_SET_PARAM_DECODER, null);
				startPlayer();
			} else {
				if (mOnCallEventListener.onCallEvent(this, MEDIA_CALLEVENT_CODEC_SET_PARAM_ENCODER, null)) {
					startRecorder();
				}
			}
		}
            if ((2 == mCodecCount) && (mCodecState != CodecState.CODEC_START)) { 
	            try {
			mCodecState = CodecState.CODEC_START;
			if (mOnCallEventListener != null) {
				mOnCallEventListener.onCallEvent(this, MEDIA_CALLEVENT_CODEC_START, null);
			}
	            } catch (IllegalStateException ex) {
	                Log.d(TAG, "start fail " + ex);
	            }
            }
	    	codec(CODEC_SET_PARAM, null, null);
            break;

        case CODEC_CLOSE:
	    synchronized(mCameraLock) {
		    if (mbCameraLock) {
			try {
				mCameraLock.wait();
			} catch (InterruptedException e) {
				Log.d(TAG,"interrupted while trying to get mCameraLock");
			}
		    }
	    }
            try {
                mCodecState = CodecState.CODEC_CLOSE;
                stop();
        		if (mOnCallEventListener != null){
        			mOnCallEventListener.onCallEvent(this, MEDIA_CALLEVENT_CODEC_CLOSE, null);
        		}
            } catch (IllegalStateException ex) {
                Log.d(TAG, "stop fail " + ex);
            }
	    	codec(CODEC_CLOSE, null, null);
            break;
        }
    }

   public CodecState getCodecState() {
   	Log.d(TAG, "getCodecState(), " + mCodecState);
   	return mCodecState;
   }
    /**
     * Interface definition of a callback to be invoked when the
     * video size is first known or updated
     */
    public interface OnVideoSizeChangedListener
    {
        /**
         * Called to indicate the video size
         *
         * @param mp        the MediaPhone associated with this callback
         * @param width     the width of the video
         * @param height    the height of the video
         */
        public void onVideoSizeChanged(MediaPhone mp, int width, int height);
    }

    /**
     * Register a callback to be invoked when the video size is
     * known or updated.
     *
     * @param listener the callback that will be run
     */
    public void setOnVideoSizeChangedListener(OnVideoSizeChangedListener listener)
    {
        mOnVideoSizeChangedListener = listener;
    }

    private OnVideoSizeChangedListener mOnVideoSizeChangedListener;

    /* Do not change these values without updating their counterparts
     * in include/media/mediaphone.h!
     */
    /** Unspecified media player error.
     * @see android.media.MediaPhone.OnErrorListener
     */
    public static final int MEDIA_ERROR_UNKNOWN = 1;

    /** Media server died. In this case, the application must release the
     * MediaPhone object and instantiate a new one.
     * @see android.media.MediaPhone.OnErrorListener
     */
    public static final int MEDIA_ERROR_SERVER_DIED = 100;

    /** The video is streamed and its container is not valid for progressive
     * playback i.e the video's index (e.g moov atom) is not at the start of the
     * file.
     * @see android.media.MediaPhone.OnErrorListener
     */
    public static final int MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK = 200;

    /**
     * Interface definition of a callback to be invoked when there
     * has been an error during an asynchronous operation (other errors
     * will throw exceptions at method call time).
     */
    public interface OnErrorListener
    {
        /**
         * Called to indicate an error.
         *
         * @param mp      the MediaPhone the error pertains to
         * @param what    the type of error that has occurred:
         * <ul>
         * <li>{@link #MEDIA_ERROR_UNKNOWN}
         * <li>{@link #MEDIA_ERROR_SERVER_DIED}
         * </ul>
         * @param extra an extra code, specific to the error. Typically
         * implementation dependant.
         * @return True if the method handled the error, false if it didn't.
         * Returning false, or not having an OnErrorListener at all, will
         * cause the OnCompletionListener to be called.
         */
        boolean onError(MediaPhone mp, int what, Object extra);
    }

    /**
     * Register a callback to be invoked when an error has happened
     * during an asynchronous operation.
     *
     * @param listener the callback that will be run
     */
    public void setOnErrorListener(OnErrorListener listener)
    {
        mOnErrorListener = listener;
    }

    private OnErrorListener mOnErrorListener;


    /* Do not change these values without updating their counterparts
     * in include/media/mediaphone.h!
     */
    /** Unspecified media player info.
     * @see android.media.MediaPhone.OnMediaInfoListener
     */
    public static final int MEDIA_INFO_UNKNOWN = 1;

    /** The video is too complex for the decoder: it can't decode frames fast
     *  enough. Possibly only the audio plays fine at this stage.
     * @see android.media.MediaPhone.OnMediaInfoListener
     */
    public static final int MEDIA_INFO_VIDEO_TRACK_LAGGING = 700;

    /** Bad interleaving means that a media has been improperly interleaved or
     * not interleaved at all, e.g has all the video samples first then all the
     * audio ones. Video is playing but a lot of disk seeks may be happening.
     * @see android.media.MediaPhone.OnMediaInfoListener
     */
    public static final int MEDIA_INFO_BAD_INTERLEAVING = 800;

    /** The media cannot be seeked (e.g live stream)
     * @see android.media.MediaPhone.OnMediaInfoListener
     */
    public static final int MEDIA_INFO_NOT_SEEKABLE = 801;

    /** A new set of metadata is available.
     * @see android.media.MediaPhone.OnMediaInfoListener
     */
    public static final int MEDIA_INFO_METADATA_UPDATE = 802;

    /**
     * Interface definition of a callback to be invoked to communicate some
     * info and/or warning about the media or its playback.
     */
    public interface OnMediaInfoListener
    {
        /**
         * Called to indicate an info or a warning.
         *
         * @param mp      the MediaPhone the info pertains to.
         * @param what    the type of info or warning.
         * <ul>
         * <li>{@link #MEDIA_INFO_UNKNOWN}
         * <li>{@link #MEDIA_INFO_VIDEO_TRACK_LAGGING}
         * <li>{@link #MEDIA_INFO_BAD_INTERLEAVING}
         * <li>{@link #MEDIA_INFO_NOT_SEEKABLE}
         * <li>{@link #MEDIA_INFO_METADATA_UPDATE}
         * </ul>
         * @param extra an extra code, specific to the info. Typically
         * implementation dependant.
         * @return True if the method handled the info, false if it didn't.
         * Returning false, or not having an OnErrorListener at all, will
         * cause the info to be discarded.
         */
        boolean onMediaInfo(MediaPhone mp, int what, Object extra);
    }

    /**
     * Register a callback to be invoked when an info/warning is available.
     *
     * @param listener the callback that will be run
     */
    public void setOnMediaInfoListener(OnMediaInfoListener listener)
    {
        mOnMediaInfoListener = listener;
    }

    private OnMediaInfoListener mOnMediaInfoListener;
	
	
	public static final int MEDIA_CALLEVENT_CAMERACLOSE = 100;
	public static final int MEDIA_CALLEVENT_CAMERAOPEN = 101;
	public static final int MEDIA_CALLEVENT_STRING = 102;
	public static final int MEDIA_CALLEVENT_CODEC_OPEN = 103;
	public static final int MEDIA_CALLEVENT_CODEC_SET_PARAM_DECODER = 104;
	public static final int MEDIA_CALLEVENT_CODEC_SET_PARAM_ENCODER = 105;
	public static final int MEDIA_CALLEVENT_CODEC_START = 106;
	public static final int MEDIA_CALLEVENT_CODEC_CLOSE = 107;
	public static final int MEDIA_CALLEVENT_MEDIA_START = 108;
	
    /**
     * Interface definition of a callback to be invoked to communicate some
     * info and/or warning about the h324 or call control.
     */
    public interface OnCallEventListener
    {
        boolean onCallEvent(MediaPhone mp, int what, Object extra);
    }

    /**
     * Register a callback to be invoked when an info/warning is available.
     *
     * @param listener the callback that will be run
     */
    public void setOnCallEventListener(OnCallEventListener listener)
    {
        mOnCallEventListener = listener;
    }

    private OnCallEventListener mOnCallEventListener;

    /**
     * @hide
     */
    //public native static int snoop(short [] outData, int kind);
}
