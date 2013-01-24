
package com.android.internal.telephony;

import static com.android.internal.telephony.RILConstants.*;

import android.content.Context;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.RegistrantList;
import android.os.Registrant;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.SmsResponse;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.cdma.CdmaCallWaitingNotification;
import com.android.internal.telephony.cdma.CdmaInformationRecords;

import java.io.IOException;
import java.util.ArrayList;

/**
 * RIL implementation of the CommandsInterface.
 * FIXME public only for testing
 *
 * {@hide}
 */
public final class SprdRIL extends RIL {

    static final String LOG_TAG = "SprdRILJ";

    private final class DSCIInfo {
        int id;
        int idr;
        int stat;
        int type;
        int mpty;
        String number;
        int num_type;
        int bs_type;
        int cause;
    }

	//***** Constructors
    public SprdRIL(Context context) {
        this(context, RILConstants.PREFERRED_NETWORK_MODE,
                RILConstants.PREFERRED_CDMA_SUBSCRIPTION);
    }

    public SprdRIL(Context context, int networkMode, int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
    }

    // zhanglj add begin 2010-05-20
    public SprdRIL(Context context, int networkMode, int cdmaSubscription, int phoneId) {
        super(context, networkMode, cdmaSubscription, phoneId);
    }
    // zhanglj add end

    public void
	dialVP (String address, String sub_address, int clirMode, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_VIDEOPHONE_DIAL/*RIL_REQUEST_DIALVIDEO*/, result);

        rr.mp.writeString(address);
        rr.mp.writeString(sub_address);
        rr.mp.writeInt(clirMode);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest));

        send(rr);
    }

    public void
    hangupVP(Message result, int reason) {
        RILRequest rr = RILRequest.obtain(
                         RIL_REQUEST_VIDEOPHONE_HANGUP,
                                         result);
        rr.mp.writeInt(1);
        rr.mp.writeInt(reason);
        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest));

        send(rr);
	 }

    public void
    acceptVP(Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_VIDEOPHONE_ANSWER, result);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest));

        send(rr);
	 }

    public void fallBackVP(Message result) {
        RILRequest rr
                 = RILRequest.obtain(RIL_REQUEST_VIDEOPHONE_FALLBACK, result);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest));

        send(rr);
    }

    public void sendVPString(String str, Message result) {
        RILRequest rr
                 = RILRequest.obtain(RIL_REQUEST_VIDEOPHONE_STRING, result);

        // count ints
        rr.mp.writeString(str);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest)
                     + " " + str);

        send(rr);
    }

    public void controlVPLocalMedia(int datatype, int sw, boolean bReplaceImg, Message result) {
        RILRequest rr
                 = RILRequest.obtain(RIL_REQUEST_VIDEOPHONE_LOCAL_MEDIA, result);

        // count ints
//		 if ((datatype == 1) && (sw == 0))
			 rr.mp.writeInt(3);
//		 else
//			 rr.mp.writeInt(2);

        rr.mp.writeInt(datatype);
        rr.mp.writeInt(sw);

//		 if ((datatype == 1) && (sw == 0))
        rr.mp.writeInt(bReplaceImg?1:0);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest)
                     + " " + datatype + " " + sw + " " + bReplaceImg);

        send(rr);
    }

    public void recordVPVideo(boolean bStart, Message result) {
        RILRequest rr
                 = RILRequest.obtain(RIL_REQUEST_VIDEOPHONE_RECORD_VIDEO, result);

        // count ints
        rr.mp.writeInt((bStart)?1:0);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest)
                     + " " + bStart);

        send(rr);
    }

    public void recordVPAudio(boolean bStart, int mode, Message result) {
        RILRequest rr
                 = RILRequest.obtain(RIL_REQUEST_VIDEOPHONE_RECORD_AUDIO, result);

        // count ints
        if (mode >= 0)
            rr.mp.writeInt(2);
        else
            rr.mp.writeInt(1);

        rr.mp.writeInt((bStart)?1:0);

        if (mode >= 0)
            rr.mp.writeInt(mode);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest)
                     + " " + bStart);

        send(rr);
    }

    public void testVP(int flag, int value, Message result) {
        RILRequest rr
                 = RILRequest.obtain(RIL_REQUEST_VIDEOPHONE_TEST, result);

        // count ints
        rr.mp.writeInt(2);
        rr.mp.writeInt(flag);
        rr.mp.writeInt(value);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest)
                     + " " + flag + " " + value);

        send(rr);
    }

    public void
    setGprsAttach(Message response) {
        RILRequest rr
			= RILRequest.obtain(RIL_REQUEST_GPRS_ATTACH,
					response);

		if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest));

		send(rr);
	}

	public void
	setGprsDetach(Message response) {
		RILRequest rr
			= RILRequest.obtain(RIL_REQUEST_GPRS_DETACH,
					response);

		if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest));

		send(rr);
	}

    public void
    setSIMPower(boolean on, Message result) {
        RILRequest rr
            = RILRequest.obtain(RIL_REQUEST_SIM_POWER, result);

        rr.mp.writeInt(1);
        rr.mp.writeInt(on ? 1 : 0);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest));

        send(rr);
    }

	public void codecVP(int type, Bundle param, Message result){
		 RILRequest rr
				 = RILRequest.obtain(RIL_REQUEST_VIDEOPHONE_CODEC, result);

			 rr.mp.writeInt(type);
		 rr.mp.writeBundle(param);

		 if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest)
					 + " " + type + " " + param);

		 send(rr);
	 }

	public void getCurrentVideoCalls (Message result){
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_GET_CURRENT_VIDEOCALLS, result);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest));

        send(rr);
    }

	public void controlVPCamera (boolean bEnable, Message result){
       sendVPString(bEnable?"open_:camera_":"close_:camera_", result);
    }

    public void controlVPAudio(boolean bEnable, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_VIDEOPHONE_CONTROL_AUDIO, result);

		// count ints
		rr.mp.writeInt(1);
		rr.mp.writeInt(bEnable?1:0);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest));

        send(rr);
    }

    public void controlIFrame(boolean isIFrame, boolean needIFrame, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_VIDEOPHONE_CONTROL_IFRAME, result);

        // count ints
        rr.mp.writeInt(2);
        rr.mp.writeInt(isIFrame ? 1 : 0);
        rr.mp.writeInt(needIFrame ? 1 : 0);

        if (RILJ_LOGD)
            riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest));

        send(rr);
    }

    /*type: 0 - record both downlink and uplink audio
                  1 - record only downlink audio
                  2 - record only upliink audio*/
	public void setVoiceRecordType(int type, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_VIDEOPHONE_SET_VOICERECORDTYPE, result);

		// count ints
		rr.mp.writeInt(1);
		rr.mp.writeInt(type);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest));

        send(rr);
    }

    protected void
    processSolicited (Parcel p) {
        int serial, error;
        boolean found = false;
		 int position = p.dataPosition();

        serial = p.readInt();
        error = p.readInt();

        RILRequest rr;

        rr = findAndRemoveRequestFromList(serial);

        if (rr == null) {
            Log.w(LOG_TAG, "Unexpected solicited response! sn: "
                            + serial + " error: " + error);
            return;
        }

        Object ret = null;

        if (error == 0 || p.dataAvail() > 0) {
            // either command succeeds or command fails but with data payload
            try {
                switch (rr.mRequest) {
                    case RIL_REQUEST_GET_SMSC_ADDRESS: ret = responseSMSCString(p); break;
                    case RIL_REQUEST_VIDEOPHONE_DIAL: ret = responseVoid(p); break;
                    case RIL_REQUEST_VIDEOPHONE_CODEC: ret = responseVoid(p); break;
                    case RIL_REQUEST_VIDEOPHONE_HANGUP: ret = responseVoid(p); break;
                    case RIL_REQUEST_VIDEOPHONE_ANSWER: ret = responseVoid(p); break;
                    case RIL_REQUEST_VIDEOPHONE_FALLBACK: ret = responseVoid(p); break;
                    case RIL_REQUEST_VIDEOPHONE_STRING: ret = responseVoid(p); break;
                    case RIL_REQUEST_VIDEOPHONE_LOCAL_MEDIA: ret = responseVoid(p); break;
                    case RIL_REQUEST_VIDEOPHONE_RECORD_VIDEO: ret = responseVoid(p); break;
                    case RIL_REQUEST_VIDEOPHONE_RECORD_AUDIO: ret = responseVoid(p); break;
                    case RIL_REQUEST_VIDEOPHONE_TEST: ret = responseVoid(p); break;
                    case RIL_REQUEST_GET_CURRENT_VIDEOCALLS: ret =  responseCallList(p); break;
                    case RIL_REQUEST_VIDEOPHONE_CONTROL_IFRAME: ret = responseVoid(p); break;
                    case RIL_REQUEST_MBBMS_GSM_AUTHEN: ret = responseString(p); break;
                    case RIL_REQUEST_MBBMS_USIM_AUTHEN: ret = responseString(p); break;
                    case RIL_REQUEST_MBBMS_SIM_TYPE: ret = responseString(p); break;
                    case RIL_REQUEST_GPRS_ATTACH: ret = responseVoid(p); break;
                    case RIL_REQUEST_GPRS_DETACH: ret = responseVoid(p); break;
                    case RIL_REQUEST_GET_REMAIN_TIMES: ret =  responseInts(p); break;
                    case RIL_REQUEST_GET_SIM_CAPACITY: ret =  responseStrings(p); break;
                    case RIL_REQUEST_SET_CMMS: ret =  responseVoid(p); break;
                    case RIL_REQUEST_SIM_POWER: ret =  responseVoid(p); break;
                    default:
                        synchronized (mRequestsList) {
                            mRequestsList.add(rr);
                            mRequestMessagesWaiting++;
                        }
                        p.setDataPosition(position);
                        super.processSolicited(p);
                        return;
                }
            } catch (Throwable tr) {
                // Exceptions here usually mean invalid RIL responses

                Log.w(LOG_TAG, rr.serialString() + "< "
                        + sprdRequestToString(rr.mRequest)
                        + " exception, possible invalid RIL response", tr);

                if (rr.mResult != null) {
                    AsyncResult.forMessage(rr.mResult, null, tr);
                    rr.mResult.sendToTarget();
                }
                rr.release();
                return;
            }
        }

        if (error != 0) {
            rr.onError(error, ret);
            rr.release();
            return;
        }

        if (RILJ_LOGD) riljLog(rr.serialString() + "< " + sprdRequestToString(rr.mRequest)
            + " " + retToString(rr.mRequest, ret));

        if (rr.mResult != null) {
            if (RILJ_LOGD)
                riljLog("SprdRIL:processSolicited: " + rr.serialString() + "< send result: " + rr.mResult.what);
            AsyncResult.forMessage(rr.mResult, ret, null);
            rr.mResult.sendToTarget();
        }

        rr.release();
    }

	protected String
	retToString(int req, Object ret) {
        if (ret == null)
            return "";

        StringBuilder sb;
        String s;
        int length;
        if (req == RIL_REQUEST_GET_CURRENT_VIDEOCALLS) {
            ArrayList<DriverCall> calls = (ArrayList<DriverCall>) ret;
            sb = new StringBuilder(" ");
            for (DriverCall dc : calls) {
                sb.append("[").append(dc).append("] ");
            }
            s = sb.toString();
        } else {
            return super.retToString(req, ret);
        }
        return s;
    }

    protected void
    processUnsolicited (Parcel p) {
        int response;
        Object ret;
        int position = p.dataPosition();

        response = p.readInt();

        try {
            switch(response) {
                case RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS:  ret =	responseString(p); break;
                case RIL_UNSOL_VIDEOPHONE_DATA: ret = responseString(p); break;
                case RIL_UNSOL_VIDEOPHONE_CODEC: ret = responseInts(p); break;
                case RIL_UNSOL_VIDEOPHONE_STRING: ret = responseString(p); break;
                case RIL_UNSOL_VIDEOPHONE_REMOTE_MEDIA: ret = responseInts(p); break;
                case RIL_UNSOL_VIDEOPHONE_MM_RING: ret = responseInts(p); break;
				case RIL_UNSOL_VIDEOPHONE_RELEASING: ret = responseString(p); break;
                case RIL_UNSOL_VIDEOPHONE_RECORD_VIDEO: ret = responseInts(p); break;
                case RIL_UNSOL_VIDEOPHONE_DSCI: ret = responseDSCI(p); break;
                case RIL_UNSOL_VIDEOPHONE_MEDIA_START: ret = responseInts(p); break;
                case RIL_UNSOL_RESPONSE_VIDEOCALL_STATE_CHANGED:ret =  responseVoid(p); break;
                case RIL_UNSOL_ON_STIN:ret = responseInts(p); break;
                case RIL_UNSOL_SIM_SMS_READY:ret = responseVoid(p); break;
                case RIL_UNSOL_STK_CALL_SETUP: ret = responseString(p); break;
                default:
                    p.setDataPosition(position);
                    super.processUnsolicited(p);
                    return;
            }
        } catch (Throwable tr) {
            Log.e(LOG_TAG, "Exception processing unsol response: " + response +
                "Exception:" + tr.toString());
            return;
        }

        switch(response) {

            case RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS:
                if (RILJ_LOGD) unsljLog(response);

                if (mGsmBroadcastSmsRegistrant != null) {
                    mGsmBroadcastSmsRegistrant
                        .notifyRegistrant(new AsyncResult(null, ret, null));
                }
            break;

            case RIL_UNSOL_VIDEOPHONE_DATA: {
                if (RILJ_LOGD)
                    unsljLogRet(response, ret);

                /*
                 * int[] params = (int[])ret; if(params.length == 1) { if
                 * (mVPDataRegistrant != null) { mVPDataRegistrant
                 * .notifyRegistrant(new AsyncResult(null, params, null)); } }
                 * else { if (RILJ_LOGD)
                 * riljLog(" RIL_UNSOL_VIDEOPHONE_DATA ERROR with wrong length "
                 * + params.length); }
                 */
                if (mVPDataRegistrant != null) {
                    mVPDataRegistrant
                                            .notifyRegistrant(new AsyncResult(null, ret, null));
                }
                break;
            }
            case RIL_UNSOL_VIDEOPHONE_CODEC: {
                if (RILJ_LOGD)
                    unsljLogRet(response, ret);

                int[] params = (int[]) ret;
                mVPCodecRegistrants
                                           .notifyRegistrants(new AsyncResult(null, params, null));
                break;
            }
            case RIL_UNSOL_VIDEOPHONE_STRING:
                if (RILJ_LOGD)
                    unsljLog(response);

                if (mVPStrsRegistrant != null) {
                    mVPStrsRegistrant
                                        .notifyRegistrant(new AsyncResult(null, ret, null));
                }
                if (mVPRemoteCameraRegistrant != null) {
                    String str = (String) ret;
                    if (str.equals("open_:camera_")) {
                        mVPRemoteCameraRegistrant
                                .notifyRegistrant(new AsyncResult(null, true, null));
                    } else if (str.equals("close_:camera_")) {
                        mVPRemoteCameraRegistrant.notifyRegistrant(new AsyncResult(null, false,
                                null));
                    }
                }
                break;
            case RIL_UNSOL_VIDEOPHONE_REMOTE_MEDIA: {
                if (RILJ_LOGD)
                    unsljLogRet(response, ret);

                int[] params = (int[]) ret;

                if (params.length >= 2) {
                    if (mVPRemoteMediaRegistrant != null) {
                        mVPRemoteMediaRegistrant
                                                    .notifyRegistrant(new AsyncResult(null, params,
                                                            null));
                    }
                } else {
                    if (RILJ_LOGD)
                        riljLog(" RIL_UNSOL_VIDEOPHONE_REMOTE_MEDIA ERROR with wrong length "
                                                    + params.length);
                }
                break;
            }
            case RIL_UNSOL_VIDEOPHONE_MM_RING: {
                if (RILJ_LOGD)
                    unsljLogRet(response, ret);

                int[] params = (int[]) ret;

                if (params.length == 1) {
                    if (mVPMMRingRegistrant != null) {
                        mVPMMRingRegistrant
                                                   .notifyRegistrant(new AsyncResult(null, params,
                                                           null));
                    }
                } else {
                    if (RILJ_LOGD)
                        riljLog(" RIL_UNSOL_VIDEOPHONE_MM_RING ERROR with wrong length "
                                                   + params.length);
                }
                break;
            }
			case RIL_UNSOL_VIDEOPHONE_RELEASING: {
								   if (RILJ_LOGD) unsljLogRet(response, ret);

								   String str = (String)ret;
								   if (mVPFailRegistrant != null) {
									       mVPFailRegistrant.notifyRegistrant(new AsyncResult(null, new AsyncResult(str, 1000, null), null));
								   }
								   break;
							   }
            case RIL_UNSOL_VIDEOPHONE_RECORD_VIDEO: {
                if (RILJ_LOGD)
                    unsljLogRet(response, ret);

                int[] params = (int[]) ret;

                if (params.length == 1) {
                    if (mVPRecordVideoRegistrant != null) {
                        mVPRecordVideoRegistrant
                                                    .notifyRegistrant(new AsyncResult(null, params,
                                                            null));
                    }
                } else {
                    if (RILJ_LOGD)
                        riljLog(" RIL_UNSOL_VIDEOPHONE_RECORD_VIDEO ERROR with wrong length "
                                                    + params.length);
                }
                break;
            }
            case RIL_UNSOL_VIDEOPHONE_DSCI: {
                if (RILJ_LOGD)
                    unsljLogRet(response, ret);

                /*
                 * int[] params = (int[])ret; if (params.length >= 4){ if
                 * (params[3] == 1){ if (params.length == 9){ Integer cause =
                 * new Integer(params[8]); if (RILJ_LOGD)
                 * riljLog(" RIL_UNSOL_VIDEOPHONE_DSCI cause: " + cause); if
                 * ((cause == 47) || (cause == 57) || (cause == 58) || (cause ==
                 * 88)) { if (mVPFallBackRegistrant != null) {
                 * mVPFallBackRegistrant.notifyRegistrant(new AsyncResult(null,
                 * cause, null)); } } else { if (mVPFailRegistrant != null) {
                 * mVPFailRegistrant.notifyRegistrant(new AsyncResult(null,
                 * cause, null)); } } } else { if (RILJ_LOGD)
                 * riljLog(" RIL_UNSOL_VIDEOPHONE_DSCI ERROR with wrong length "
                 * + params.length); } } } else { if (RILJ_LOGD)
                 * riljLog(" RIL_UNSOL_VIDEOPHONE_DSCI ERROR with wrong length "
                 * + params.length); }
                 */

                DSCIInfo info = (DSCIInfo) ret;
                if (info.cause > 0) {
                    if (RILJ_LOGD)
                        riljLog(" RIL_UNSOL_VIDEOPHONE_DSCI number: " + info.number + ", cause: "
                                + info.cause);
                    if ((info.cause == 47) || (info.cause == 57) || (info.cause == 58)
                            || (info.cause == 88)) {
                        if (mVPFallBackRegistrant != null) {
                            mVPFallBackRegistrant.notifyRegistrant(new AsyncResult(null,
                                    new AsyncResult(info.number, info.cause, null), null));
                        }
                    } else {
                        if (mVPFailRegistrant != null) {
                            mVPFailRegistrant.notifyRegistrant(new AsyncResult(null,
                                    new AsyncResult(info.number, info.cause, null), null));
                        }
                    }
                }
                break;

            }
            case RIL_UNSOL_VIDEOPHONE_MEDIA_START:
                if (RILJ_LOGD)
                    unsljLog(response);

                if (mVPMediaStartRegistrant != null) {
                    int[] params = (int[]) ret;
                    if (params[0] == 1) {
                        mVPMediaStartRegistrant.notifyRegistrant(new AsyncResult(null, params, null));
                    }
                }
                break;

            case RIL_UNSOL_RESPONSE_VIDEOCALL_STATE_CHANGED:
                if (RILJ_LOGD)
                    unsljLog(response);

                mVideoCallStateRegistrants
                                       .notifyRegistrants(new AsyncResult(null, null, null));
                break;

            case RIL_UNSOL_ON_STIN:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mStkStinRegistrant != null) {
                    mStkStinRegistrant.notifyRegistrant(new AsyncResult (null, ret, null));
                }
                break;

            case RIL_UNSOL_SIM_SMS_READY:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mSimSmsReadyRegistrant != null) {
                    mSimSmsReadyRegistrant.notifyRegistrant(
                            new AsyncResult (null, ret, null));
                }
                break;

            case RIL_UNSOL_STK_CALL_SETUP:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mCatCallSetUpRegistrant != null) {
                    mCatCallSetUpRegistrant.notifyRegistrant(
                                        new AsyncResult (null, ret, null));
                }
                break;
        }
    }

	 private String
	 sprdRequestToString(int request) {
		 /*
		    cat libs/telephony/ril_commands.h \
		    | egrep "^ *{RIL_" \
		    | sed -re 's/\{RIL_([^,]+),[^,]+,([^}]+).+/case RIL_\1: return "\1";/'
		    */
		 switch(request) {
			 case RIL_REQUEST_VIDEOPHONE_DIAL: return "VIDEOPHONE_DIAL";
			 case RIL_REQUEST_VIDEOPHONE_CODEC: return "VIDEOPHONE_CODEC";
			 case RIL_REQUEST_VIDEOPHONE_HANGUP: return "VIDEOPHONE_HANGUP";
			 case RIL_REQUEST_VIDEOPHONE_ANSWER: return "VIDEOPHONE_ANSWER";
			 case RIL_REQUEST_VIDEOPHONE_FALLBACK: return "VIDEOPHONE_FALLBACK";
			 case RIL_REQUEST_VIDEOPHONE_STRING: return "VIDEOPHONE_STRING";
			 case RIL_REQUEST_VIDEOPHONE_LOCAL_MEDIA: return "VIDEOPHONE_LOCAL_MEDIA";
			 case RIL_REQUEST_VIDEOPHONE_RECORD_VIDEO: return "VIDEOPHONE_RECORD_VIDEO";
			 case RIL_REQUEST_VIDEOPHONE_RECORD_AUDIO: return "VIDEOPHONE_RECORD_AUDIO";
			 case RIL_REQUEST_VIDEOPHONE_TEST: return "VIDEOPHONE_TEST";
			 case RIL_REQUEST_GET_CURRENT_VIDEOCALLS: return "GET_CURRENT_VIDEOCALLS";
			 case RIL_REQUEST_VIDEOPHONE_CONTROL_AUDIO: return "VIDEOPHONE_CONTROL_AUDIO";
			 case RIL_REQUEST_VIDEOPHONE_CONTROL_IFRAME: return "VIDEOPHONE_CONTROL_IFRAME";
			 case RIL_REQUEST_MBBMS_GSM_AUTHEN: return "MBBMS_GSM_AUTHEN";
			 case RIL_REQUEST_MBBMS_USIM_AUTHEN: return "MBBMS_USIM_AUTHEN";
			 case RIL_REQUEST_MBBMS_SIM_TYPE: return "MBBMS_SIM_TYPE";
			 case RIL_REQUEST_GPRS_ATTACH: return "GPRS_ATTACH";
			 case RIL_REQUEST_GPRS_DETACH: return "GPRS_DETACH";
			 case RIL_REQUEST_GET_SIM_CAPACITY: return "GET_SIM_CAPACITY";
			 case RIL_REQUEST_GET_REMAIN_TIMES: return "REMAIN_TIMES";
			 default: return requestToString(request);
		 }
	 }

	protected Object
	responseDSCI(Parcel p) {
	    DSCIInfo info = new DSCIInfo();

	    info.id = p.readInt();//id
	    info.idr = p.readInt();//idr
	    info.stat = p.readInt();//stat
	    if (info.stat == 6) {
		    info.type = p.readInt();//type
		    info.mpty = p.readInt();//mpty
		    info.number = p.readString();//number
		    info.num_type = p.readInt();//num_type
		    info.bs_type = p.readInt();//bs_type
		    if (info.type == 1) {
			info.cause = p.readInt();
		    }
	    }
	    riljLog("responseDSCI(), number: " + info.number + ", status: " + info.stat + ", type: " + info.type + ", cause: " + info.cause);

	    return info;
	}

	 protected String
	 responseToString(int request)
	 {
		 /*
		    cat libs/telephony/ril_unsol_commands.h \
		    | egrep "^ *{RIL_" \
		    | sed -re 's/\{RIL_([^,]+),[^,]+,([^}]+).+/case RIL_\1: return "\1";/'
		    */
        switch(request) {
            case RIL_UNSOL_VIDEOPHONE_DATA: return "UNSOL_VIDEOPHONE_DATA";
            case RIL_UNSOL_VIDEOPHONE_CODEC: return "UNSOL_VIDEOPHONE_CODEC";
            case RIL_UNSOL_VIDEOPHONE_DCPI: return "UNSOL_VIDEOPHONE_DCPI";
            case RIL_UNSOL_VIDEOPHONE_DSCI: return "UNSOL_VIDEOPHONE_DSCI";
            case RIL_UNSOL_VIDEOPHONE_STRING: return "UNSOL_VIDEOPHONE_STRING";
            case RIL_UNSOL_VIDEOPHONE_REMOTE_MEDIA: return "UNSOL_VIDEOPHONE_REMOTE_MEDIA";
            case RIL_UNSOL_VIDEOPHONE_MM_RING: return "UNSOL_VIDEOPHONE_MM_RING";
		    case RIL_UNSOL_VIDEOPHONE_RELEASING: return "RIL_UNSOL_VIDEOPHONE_RELEASING";
            case RIL_UNSOL_VIDEOPHONE_RECORD_VIDEO: return "UNSOL_VIDEOPHONE_RECORD_VIDEO";
            case RIL_UNSOL_VIDEOPHONE_MEDIA_START: return "UNSOL_VIDEOPHONE_MEDIA_START";
            case RIL_UNSOL_RESPONSE_VIDEOCALL_STATE_CHANGED: return "UNSOL_RESPONSE_VIDEOCALL_STATE_CHANGED";
            case RIL_UNSOL_SIM_SMS_READY: return "UNSOL_SIM_SMS_READY";
            default: return super.responseToString(request);
		 }
	 }

    // zhanglj add 2010-05-24
    public int getPhoneId(){
        return mPhoneId;
    }

    public void  Mbbms_Gsm_Authenticate(String nonce, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_MBBMS_GSM_AUTHEN, result);

        rr.mp.writeString(nonce);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest));

        send(rr);
	 }

    public void  Mbbms_USim_Authenticate(String nonce, String autn, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_MBBMS_USIM_AUTHEN, result);
        rr.mp.writeInt(2);
        rr.mp.writeString(nonce);
        rr.mp.writeString(autn);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest));

        send(rr);

    }

    public void  getSimType(Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_MBBMS_SIM_TYPE, result);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest));

        send(rr);

    }

    public void getRemainTimes(int type, Message result){

        RILRequest rr = RILRequest.obtain(RIL_REQUEST_GET_REMAIN_TIMES, result);
        rr.mp.writeInt(1);
		 rr.mp.writeInt(type);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest));

        send(rr);

    }

    public void getSimCapacity(Message result){

        RILRequest rr = RILRequest.obtain(RIL_REQUEST_GET_SIM_CAPACITY, result);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest));

        send(rr);

    }

}

