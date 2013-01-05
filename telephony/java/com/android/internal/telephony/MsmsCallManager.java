package com.android.internal.telephony;

import java.util.ArrayList;

import android.util.Log;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.RegistrantList;
import android.os.Registrant;

import com.android.internal.telephony.gsm.TDPhone;

public class MsmsCallManager extends CallManager {

    // add for video call
    private static final int EVENT_NEW_RINGING_VIDEO_CALL = 200;
    private static final int EVENT_PRECISE_VIDEO_CALL_STATE_CHANGED = 201;
    private static final int EVENT_VIDEO_CALL_DISCONNECT = 202;
    private static final int EVENT_INCOMING_RING_VIDEO_CALL = 203;
    private static final int EVENT_VIDEO_CALL_FALL_BACK = 204;
    private static final int EVENT_VIDEO_CALL_FAIL = 205;
    private static final int EVENT_VIDEO_CALL_CODEC  = 206;

    // add for video phone start
    protected final RegistrantList mNewRingingVideoCallRegistrants
    = new RegistrantList();

    protected final RegistrantList mPreciseVideoCallStateRegistrants
    = new RegistrantList();

    protected final RegistrantList mVideoCallDisconnectRegistrants
    = new RegistrantList();

    protected final RegistrantList mIncomingRingVideoCallRegistrants
    = new RegistrantList();

    protected final RegistrantList mVideoCallFallBackRegistrants
    = new RegistrantList();

    protected final RegistrantList mVideoCallFailRegistrants
    = new RegistrantList();

    protected final RegistrantList mVideoCallCodecRegistrants
    = new RegistrantList();
    // add for video phone end

    static {
        INSTANCE = new MsmsCallManager();
    }

    private MsmsCallManager() {

    }

    public static CallManager getInstance() {
        return INSTANCE;
    }

    /**
     * Register phone to CallManager
     * @param phone to be registered
     * @return true if register successfully
     */
    public boolean registerPhone(Phone phone) {
        Phone basePhone = getPhoneBase(phone);

        if (basePhone != null && !mPhones.contains(basePhone)) {

            if (DBG) {
                Log.d(LOG_TAG, "registerPhone(" +
                        phone.getPhoneName() + " " + phone + ")");
            }

            if (mPhones.isEmpty()) {
                mDefaultPhone = basePhone;
            }
            mPhones.add(basePhone);

         if (basePhone instanceof TDPhone) {
            TDPhone tdPhone = (TDPhone)basePhone;
            ArrayList<Call> calls = tdPhone.getRingingCalls();
            for (Call call : calls) {
                mRingingCalls.add(call);
            }
            calls = tdPhone.getBackgroundCalls();
            for (Call call : calls) {
                mBackgroundCalls.add(call);
            }
            calls = tdPhone.getForegroundCalls();
            for (Call call : calls) {
                mForegroundCalls.add(call);
            }
        } else {
            mRingingCalls.add(basePhone.getRingingCall());
            mBackgroundCalls.add(basePhone.getBackgroundCall());
            mForegroundCalls.add(basePhone.getForegroundCall());
        }
            registerForPhoneStates(basePhone);

            // for events supported only by 3G Phone
            phone.registerForNewRingingVideoCall(mMsmsHandler, EVENT_NEW_RINGING_VIDEO_CALL, null);
            phone.registerForPreciseVideoCallStateChanged(mMsmsHandler, EVENT_PRECISE_VIDEO_CALL_STATE_CHANGED, null);
            phone.registerForVideoCallDisconnect(mMsmsHandler, EVENT_VIDEO_CALL_DISCONNECT, null);
            phone.registerForIncomingRingVideoCall(mMsmsHandler, EVENT_INCOMING_RING_VIDEO_CALL, null);
            phone.registerForVideoCallFallBack(mMsmsHandler, EVENT_VIDEO_CALL_FALL_BACK, null);
            phone.registerForVideoCallFail(mMsmsHandler, EVENT_VIDEO_CALL_FAIL, null);
            phone.registerForVideoCallCodec(mMsmsHandler, EVENT_VIDEO_CALL_CODEC, phone);
            return true;
        }

        return false;
    }

    private Handler mMsmsHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_NEW_RINGING_VIDEO_CALL:
                    if (VDBG) Log.d(LOG_TAG, " handleMessage (EVENT_NEW_RINGING_VIDEO_CALL)");
                    mNewRingingVideoCallRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case EVENT_PRECISE_VIDEO_CALL_STATE_CHANGED:
                    if (VDBG) Log.d(LOG_TAG, " handleMessage (EVENT_PRECISE_VIDEO_CALL_STATE_CHANGED)");
                    mPreciseVideoCallStateRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case EVENT_VIDEO_CALL_DISCONNECT:
                    if (VDBG) Log.d(LOG_TAG, " handleMessage (EVENT_VIDEO_CALL_DISCONNECT)");
                    mVideoCallDisconnectRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case EVENT_INCOMING_RING_VIDEO_CALL:
                    if (VDBG) Log.d(LOG_TAG, " handleMessage (EVENT_INCOMING_RING_VIDEO_CALL)");
                    mIncomingRingVideoCallRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case EVENT_VIDEO_CALL_FALL_BACK:
                    if (VDBG) Log.d(LOG_TAG, " handleMessage (EVENT_VIDEO_CALL_FALL_BACK)");
                    mVideoCallFallBackRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case EVENT_VIDEO_CALL_FAIL:
                    if (VDBG) Log.d(LOG_TAG, " handleMessage (EVENT_VIDEO_CALL_FAIL)");
                    mVideoCallFailRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case EVENT_VIDEO_CALL_CODEC:
                    if (VDBG) Log.d(LOG_TAG, " handleMessage (EVENT_VIDEO_CALL_CODEC)");
                    mVideoCallCodecRegistrants.notifyRegistrants(new AsyncResult(null, (AsyncResult)msg.obj, null));
                    break;
            }
        }
    };
    // add for video phone start
    /**
     * Notifies when a voice connection has disconnected, either due to local
     * or remote hangup or error.
     *
     *  Messages received from this will have the following members:<p>
     *  <ul><li>Message.obj will be an AsyncResult</li>
     *  <li>AsyncResult.userObj = obj</li>
     *  <li>AsyncResult.result = a Connection object that is
     *  no longer connected.</li></ul>
     */
    public void registerForVideoCallDisconnect(Handler h, int what, Object obj) {
        mVideoCallDisconnectRegistrants.addUnique(h, what, obj);
    }

    /**
     * Unregisters for voice disconnection notification.
     * Extraneous calls are tolerated silently
     */
    public void unregisterForVideoCallDisconnect(Handler h){
        mVideoCallDisconnectRegistrants.remove(h);
    }

    /**
     * Register for getting notifications for change in the Call State {@link Call.State}
     * This is called PreciseCallState because the call state is more precise than the
     * {@link Phone.State} which can be obtained using the {@link PhoneStateListener}
     *
     * Resulting events will have an AsyncResult in <code>Message.obj</code>.
     * AsyncResult.userData will be set to the obj argument here.
     * The <em>h</em> parameter is held only by a weak reference.
     */
    public void registerForPreciseVideoCallStateChanged(Handler h, int what, Object obj){
        mPreciseVideoCallStateRegistrants.addUnique(h, what, obj);
    }

    /**
     * Unregisters for voice call state change notifications.
     * Extraneous calls are tolerated silently.
     */
    public void unregisterForPreciseVideoCallStateChanged(Handler h){
        mPreciseVideoCallStateRegistrants.remove(h);
    }

    /**
     * Notifies when a new ringing or waiting connection has appeared.<p>
     *
     *  Messages received from this:
     *  Message.obj will be an AsyncResult
     *  AsyncResult.userObj = obj
     *  AsyncResult.result = a Connection. <p>
     *  Please check Connection.isRinging() to make sure the Connection
     *  has not dropped since this message was posted.
     *  If Connection.isRinging() is true, then
     *   Connection.getCall() == Phone.getRingingCall()
     */
    public void registerForNewRingingVideoCall(Handler h, int what, Object obj){
        mNewRingingVideoCallRegistrants.addUnique(h, what, obj);
    }

    /**
     * Unregisters for new ringing connection notification.
     * Extraneous calls are tolerated silently
     */

    public void unregisterForNewRingingVideoCall(Handler h){
        mNewRingingVideoCallRegistrants.remove(h);
    }

    /**
     * Notifies when an incoming call rings.<p>
     *
     *  Messages received from this:
     *  Message.obj will be an AsyncResult
     *  AsyncResult.userObj = obj
     *  AsyncResult.result = a Connection. <p>
     */
    public void registerForIncomingRingVideoCall(Handler h, int what, Object obj){
        mIncomingRingVideoCallRegistrants.addUnique(h, what, obj);
    }

    /**
     * Unregisters for ring notification.
     * Extraneous calls are tolerated silently
     */

    public void unregisterForIncomingRingVideoCall(Handler h){
        mIncomingRingVideoCallRegistrants.remove(h);
    }

    public void registerForVideoCallFallBack(Handler h, int what, Object obj){
        mVideoCallFallBackRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForVideoCallFallBack(Handler h){
        mVideoCallFallBackRegistrants.remove(h);
    }

    public void registerForVideoCallFail(Handler h, int what, Object obj){
        mVideoCallFailRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForVideoCallFail(Handler h){
        mVideoCallFailRegistrants.remove(h);
    }

    public void registerForVideoCallCodec(Handler h, int what, Object obj){
        mVideoCallCodecRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForVideoCallCodec(Handler h){
        mVideoCallCodecRegistrants.remove(h);
    }
    // add for video phone end

}
