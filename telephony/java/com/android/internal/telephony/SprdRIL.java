
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

	protected Registrant mVPDataRegistrant;
	protected RegistrantList mVPCodecRegistrants = new RegistrantList();
	protected Registrant mVPStrsRegistrant;
	protected Registrant mVPRemoteMediaRegistrant;
	protected Registrant mVPMMRingRegistrant;
	protected Registrant mVPRecordVideoRegistrant;
	protected Registrant mVPFallBackRegistrant;
	protected Registrant mVPFailRegistrant;
	protected Registrant mVPRemoteCameraRegistrant;
    protected Registrant mVPMediaStartRegistrant;
	protected RegistrantList mVideoCallStateRegistrants = new RegistrantList();
    protected Registrant mStkStinRegistrant;

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
	public
	SprdRIL(Context context) {
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
    queryFacilityLock (String facility, String password, int serviceClass,
                            Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_QUERY_FACILITY_LOCK, response);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest));

        // count strings
        rr.mp.writeInt(3);

        rr.mp.writeString(facility);
        rr.mp.writeString(password);

        rr.mp.writeString(Integer.toString(serviceClass));

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest)
                            + ", facility:" + facility + ", serviceClass:" + serviceClass);

        send(rr);
    }

    public void
    setFacilityLock (String facility, boolean lockState, String password,
                        int serviceClass, Message response) {
        String lockString;
         RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SET_FACILITY_LOCK, response);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest));

        // count strings
        rr.mp.writeInt(4);

        rr.mp.writeString(facility);
        lockString = (lockState)?"1":"0";
        rr.mp.writeString(lockString);
        rr.mp.writeString(password);
        rr.mp.writeString(Integer.toString(serviceClass));
		
        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest)
                            + ", facility:" + facility + ", lockState:" + lockState + ", serviceClass:" + serviceClass);

        send(rr);

    }
	
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
		 RILRequest rr
				 = RILRequest.obtain(RIL_REQUEST_VIDEOPHONE_ANSWER, result);
	
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

		if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

		send(rr);
	}	
	
	public void
	setGprsDetach(Message response) {
		RILRequest rr
			= RILRequest.obtain(RIL_REQUEST_GPRS_DETACH,
					response);

		if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

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

	public void controlVPAudio(boolean bEnable,Message result){
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_VIDEOPHONE_CONTROL_AUDIO, result);
		
		// count ints		
		rr.mp.writeInt(1);	
		rr.mp.writeInt(bEnable?1:0);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest));

        send(rr);
    }
	
	public void controlIFrame(boolean isIFrame, boolean needIFrame,Message result){
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_VIDEOPHONE_CONTROL_IFRAME, result);
		
		// count ints		
		rr.mp.writeInt(2);	
		rr.mp.writeInt(isIFrame?1:0);
		rr.mp.writeInt(needIFrame?1:0);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest));

        send(rr);
    }


    public void setOnVPData(Handler h, int what, Object obj) {
        mVPDataRegistrant = new Registrant (h, what, obj);
    }

    public void unSetOnVPData(Handler h) {
        mVPDataRegistrant.clear();
    }

    public void setOnVPCodec(Handler h, int what, Object obj) {
	   Registrant r = new Registrant (h, what, obj);
   
	   mVPCodecRegistrants.add(r);
   }

    public void unSetOnVPCodec(Handler h) {
	   mVPCodecRegistrants.remove(h);
   }
	
    public void setOnVPString(Handler h, int what, Object obj) {
        mVPStrsRegistrant = new Registrant (h, what, obj);
    }

    public void unSetOnVPString(Handler h) {
        mVPStrsRegistrant.clear();
    }
	
    public void setOnVPRemoteMedia(Handler h, int what, Object obj) {
        mVPRemoteMediaRegistrant = new Registrant (h, what, obj);
    }

    public void unSetOnVPRemoteMedia(Handler h) {
        mVPRemoteMediaRegistrant.clear();
    }
	
    public void setOnVPMMRing(Handler h, int what, Object obj) {
        mVPMMRingRegistrant = new Registrant (h, what, obj);
    }

    public void unSetOnVPMMRing(Handler h) {
        mVPMMRingRegistrant.clear();
    }
	
    public void setOnVPRecordVideo(Handler h, int what, Object obj) {
        mVPRecordVideoRegistrant = new Registrant (h, what, obj);
    }

    public void unSetOnVPRecordVideo(Handler h) {
        mVPRecordVideoRegistrant.clear();
    }
	
	   public void setOnVPFallBack(Handler h, int what, Object obj){
        mVPFallBackRegistrant = new Registrant (h, what, obj);
    }
	   
	   public void unSetOnVPFallBack(Handler h){
        mVPFallBackRegistrant.clear();
    }
	   
	   public void setOnVPFail(Handler h, int what, Object obj){
        mVPFailRegistrant = new Registrant (h, what, obj);
    }
	   
	   public void unSetOnVPFail(Handler h){
        mVPFailRegistrant.clear();
    }
	   
	   public void setOnVPRemoteCamera(Handler h, int what, Object obj){
        mVPRemoteCameraRegistrant = new Registrant (h, what, obj);
    }
	   
	   public void unSetOnVPRemoteCamera(Handler h){
        mVPRemoteCameraRegistrant.clear();
    }

    public void setOnVPMediaStart(Handler h, int what, Object obj) {
        mVPMediaStartRegistrant = new Registrant (h, what, obj);
    }

    public void unSetOnVPMediaStart(Handler h) {
        mVPMediaStartRegistrant.clear();
    }

	   public void registerForVideoCallStateChanged(Handler h, int what, Object obj) {
		   Registrant r = new Registrant (h, what, obj);
	   
		   mVideoCallStateRegistrants.add(r);
	   }
	   
	   public void unregisterForVideoCallStateChanged(Handler h) {
		   mVideoCallStateRegistrants.remove(h);
	   }

    public void setOnStkStin(Handler h, int what, Object obj) {
        mStkStinRegistrant = new Registrant (h, what, obj);
    }

    public void unsetOnStkStin(Handler h) {
        mStkStinRegistrant.clear();
    }

/*
	   protected void
	   processSolicited (Parcel p) {
		   int serial, error;
		   boolean found = false;
		   Parcel tmpParcel = Parcel.obtain();
		   byte[] tmpRaw = p.marshall();
		   tmpParcel.unmarshall(tmpRaw,0,tmpRaw.length);
		   tmpParcel.setDataPosition(0);
	
		   serial = p.readInt();
		   error = p.readInt();
		   //Log.e(LOG_TAG, "processSolicited: " + serial + ", length: " + tmpRaw.length);
	
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
			   try {switch (rr.mRequest) {
		            case RIL_REQUEST_QUERY_FACILITY_LOCK: ret =  responseInts(p); break;
		            case RIL_REQUEST_SET_FACILITY_LOCK: ret =  responseInts(p); break;
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
			   default:
			           //p.setDataPosition(0);
				   //super.processSolicited(p);
				   super.processSolicited(tmpParcel);
                		   tmpParcel.recycle();
				   return;
			   //break;
			   }} catch (Throwable tr) {
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
			   AsyncResult.forMessage(rr.mResult, ret, null);
			   rr.mResult.sendToTarget();
		   }
	
		   rr.release();
	}

 protected void
    processUnsolicited (Parcel p) {
        int response;
        Object ret;
	Parcel tmpParcel = Parcel.obtain();
	byte[] tmpRaw = p.marshall();
	tmpParcel.unmarshall(tmpRaw,0,tmpRaw.length);
	tmpParcel.setDataPosition(0);

        response = p.readInt();
	//Log.e(LOG_TAG, "processUnsolicited: " + response + ", length: " + tmpRaw.length);
        try {switch(response) {

	   case RIL_UNSOL_VIDEOPHONE_DATA: ret = responseInts(p); break;
            case RIL_UNSOL_VIDEOPHONE_CODEC: ret = responseInts(p); break;
            case RIL_REQUEST_VIDEOPHONE_STRING: ret = responseString(p); break;
            case RIL_UNSOL_VIDEOPHONE_REMOTE_MEDIA: ret = responseInts(p); break;
            case RIL_UNSOL_VIDEOPHONE_MM_RING: ret = responseInts(p); break;
            case RIL_UNSOL_VIDEOPHONE_RECORD_VIDEO: ret = responseInts(p); break;
	   case RIL_UNSOL_VIDEOPHONE_DSCI: ret = responseInts(p); break;
	   case RIL_UNSOL_RESPONSE_VIDEOCALL_STATE_CHANGED:ret =  responseVoid(p); break;

            default:
		//p.setDataPosition(0);
		//super.processUnsolicited(p);
		super.processUnsolicited(tmpParcel);
                tmpParcel.recycle();
		return;
            //break; (implied)
        }} catch (Throwable tr) {
            Log.e(LOG_TAG, "Exception processing unsol response: " + response +
                "Exception:" + tr.toString());
            return;
        }

        switch(response) {
	case RIL_UNSOL_VIDEOPHONE_DATA:		{		
                if (RILJ_LOGD) unsljLogRet(response, ret);

                int[] params = (int[])ret;

                if(params.length == 1) {
                    if (mVPDataRegistrant != null) {
                        mVPDataRegistrant
							.notifyRegistrant(new AsyncResult(null, params, null));
                    }
                } else {
                    if (RILJ_LOGD) riljLog(" RIL_UNSOL_VIDEOPHONE_DATA ERROR with wrong length "
                            + params.length);
                }
            	break;
			}
	case RIL_UNSOL_VIDEOPHONE_CODEC:{		
                if (RILJ_LOGD) unsljLogRet(response, ret);

                int[] params = (int[])ret;
                if(params.length == 1) {
                    if (mVPCodecRegistrant != null) {
                        mVPCodecRegistrant
							.notifyRegistrant(new AsyncResult(null, params, null));
                    }
                } else {
                    if (RILJ_LOGD) riljLog(" RIL_UNSOL_VIDEOPHONE_CODEC ERROR with wrong length "
                            + params.length);
                }
            	break;
		}
            case RIL_REQUEST_VIDEOPHONE_STRING: 
                if (RILJ_LOGD) unsljLog(response);
				
                if (mVPStrsRegistrant != null) {
                    mVPStrsRegistrant
                        .notifyRegistrant(new AsyncResult(null, ret, null));
                }
            	break;
            case RIL_UNSOL_VIDEOPHONE_REMOTE_MEDIA: {
                if (RILJ_LOGD) unsljLogRet(response, ret);

                int[] params = (int[])ret;

                if(params.length >= 2) {
                    if (mVPRemoteMediaRegistrant != null) {
                        mVPRemoteMediaRegistrant
							.notifyRegistrant(new AsyncResult(null, params, null));
                    }
                } else {
                    if (RILJ_LOGD) riljLog(" RIL_UNSOL_VIDEOPHONE_REMOTE_MEDIA ERROR with wrong length "
                            + params.length);
                }
            	break;
            }
            case RIL_UNSOL_VIDEOPHONE_MM_RING: {
                if (RILJ_LOGD) unsljLogRet(response, ret);

                int[] params = (int[])ret;

                if(params.length == 1) {
                    if (mVPMMRingRegistrant != null) {
                        mVPMMRingRegistrant
							.notifyRegistrant(new AsyncResult(null, params, null));
                    }
                } else {
                    if (RILJ_LOGD) riljLog(" RIL_UNSOL_VIDEOPHONE_MM_RING ERROR with wrong length "
                            + params.length);
                }
            	break;
            }
            case RIL_UNSOL_VIDEOPHONE_RECORD_VIDEO: {
                if (RILJ_LOGD) unsljLogRet(response, ret);

                int[] params = (int[])ret;

                if(params.length == 1) {
                    if (mVPRecordVideoRegistrant != null) {
                        mVPRecordVideoRegistrant
							.notifyRegistrant(new AsyncResult(null, params, null));
                    }
                } else {
                    if (RILJ_LOGD) riljLog(" RIL_UNSOL_VIDEOPHONE_RECORD_VIDEO ERROR with wrong length "
                            + params.length);
                }
            	break;
            }	    
	    case RIL_UNSOL_VIDEOPHONE_DSCI:{		
                if (RILJ_LOGD) unsljLogRet(response, ret);

                int[] params = (int[])ret;
                if(params.length == 1) {
		   if ((params[0] == 47) || (params[0] == 57) || (params[0] == 58) || (params[0] == 88)) {
                    if (mVPFallBackRegistrant != null) {
                        mVPFallBackRegistrant
							.notifyRegistrant(new AsyncResult(null, params, null));
                    }
		   }
                } else {
                    if (RILJ_LOGD) riljLog(" RIL_UNSOL_VIDEOPHONE_DSCI ERROR with wrong length "
                            + params.length);
                }
            	break;
		}	
            case RIL_UNSOL_RESPONSE_VIDEOCALL_STATE_CHANGED:
                if (RILJ_LOGD) unsljLog(response);

                mVideoCallStateRegistrants
                    .notifyRegistrants(new AsyncResult(null, null, null));
            break;
        }
    }*/


 	protected void
	 processSolicited (Parcel p) {
		 int serial, error;
		 boolean found = false;

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
			 try {switch (rr.mRequest) {
				 case RIL_REQUEST_GET_SIM_STATUS: ret =  responseIccCardStatus(p); break;
				 case RIL_REQUEST_ENTER_SIM_PIN: ret =	responseInts(p); break;
				 case RIL_REQUEST_ENTER_SIM_PUK: ret =	responseInts(p); break;
				 case RIL_REQUEST_ENTER_SIM_PIN2: ret =  responseInts(p); break;
				 case RIL_REQUEST_ENTER_SIM_PUK2: ret =  responseInts(p); break;
				 case RIL_REQUEST_CHANGE_SIM_PIN: ret =  responseInts(p); break;
				 case RIL_REQUEST_CHANGE_SIM_PIN2: ret =  responseInts(p); break;
				 case RIL_REQUEST_ENTER_NETWORK_DEPERSONALIZATION: ret =  responseInts(p); break;
				 case RIL_REQUEST_GET_CURRENT_CALLS: ret =	responseCallList(p); break;
				 case RIL_REQUEST_DIAL: ret =  responseVoid(p); break;
				 case RIL_REQUEST_GET_IMSI: ret =  responseString(p); break;
				 case RIL_REQUEST_HANGUP: ret =  responseVoid(p); break;
				 case RIL_REQUEST_HANGUP_WAITING_OR_BACKGROUND: ret =  responseVoid(p); break;
				 case RIL_REQUEST_HANGUP_FOREGROUND_RESUME_BACKGROUND: ret =  responseVoid(p); break;
				 case RIL_REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE: ret =  responseVoid(p); break;
				 case RIL_REQUEST_CONFERENCE: ret =  responseVoid(p); break;
				 case RIL_REQUEST_UDUB: ret =  responseVoid(p); break;
				 case RIL_REQUEST_LAST_CALL_FAIL_CAUSE: ret =  responseInts(p); break;
				 case RIL_REQUEST_SIGNAL_STRENGTH: ret =  responseSignalStrength(p); break;
				 case RIL_REQUEST_REGISTRATION_STATE: ret =  responseStrings(p); break;
				 case RIL_REQUEST_GPRS_REGISTRATION_STATE: ret =  responseStrings(p); break;
				 case RIL_REQUEST_OPERATOR: ret =  responseOperatorString(p.readStringArray(),0); break;
				 case RIL_REQUEST_RADIO_POWER: ret =  responseVoid(p); break;
				 case RIL_REQUEST_DTMF: ret =  responseVoid(p); break;
				 case RIL_REQUEST_SEND_SMS: ret =  responseSMS(p); break;
				 case RIL_REQUEST_SEND_SMS_EXPECT_MORE: ret =  responseSMS(p); break;
				 case RIL_REQUEST_SETUP_DATA_CALL: ret =  responseStrings(p); break;
				 case RIL_REQUEST_SIM_IO: ret =  responseICC_IO(p); break;
				 case RIL_REQUEST_SEND_USSD: ret =	responseVoid(p); break;
				 case RIL_REQUEST_CANCEL_USSD: ret =  responseVoid(p); break;
				 case RIL_REQUEST_GET_CLIR: ret =  responseInts(p); break;
				 case RIL_REQUEST_SET_CLIR: ret =  responseVoid(p); break;
				 case RIL_REQUEST_QUERY_CALL_FORWARD_STATUS: ret =	responseCallForward(p); break;
				 //case RIL_REQUEST_SET_CALL_FORWARD: ret =  responseVoid(p); break;
				 case RIL_REQUEST_SET_CALL_FORWARD: ret =  responseCallForward(p); break;
				 case RIL_REQUEST_QUERY_CALL_WAITING: ret =  responseCallWaiting(p); break;
				 case RIL_REQUEST_SET_CALL_WAITING: ret =  responseVoid(p); break;
				 case RIL_REQUEST_SMS_ACKNOWLEDGE: ret =  responseVoid(p); break;
				 case RIL_REQUEST_GET_IMEI: ret =  responseString(p); break;
				 case RIL_REQUEST_GET_IMEISV: ret =  responseString(p); break;
				 case RIL_REQUEST_ANSWER: ret =  responseVoid(p); break;
				 case RIL_REQUEST_DEACTIVATE_DATA_CALL: ret =  responseVoid(p); break;
				 case RIL_REQUEST_CHANGE_BARRING_PASSWORD: ret =  responseVoid(p); break;
				 case RIL_REQUEST_QUERY_NETWORK_SELECTION_MODE: ret =  responseInts(p); break;
				 case RIL_REQUEST_SET_NETWORK_SELECTION_AUTOMATIC: ret =  responseVoid(p); break;
				 case RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL: ret =  responseVoid(p); break;
				 case RIL_REQUEST_QUERY_AVAILABLE_NETWORKS : ret =	responseNetworkInfos(p); break;
				 case RIL_REQUEST_DTMF_START: ret =  responseVoid(p); break;
				 case RIL_REQUEST_DTMF_STOP: ret =	responseVoid(p); break;
				 case RIL_REQUEST_BASEBAND_VERSION: ret =  responseString(p); break;
				 case RIL_REQUEST_SEPARATE_CONNECTION: ret =  responseVoid(p); break;
				 case RIL_REQUEST_SET_MUTE: ret =  responseVoid(p); break;
				 case RIL_REQUEST_GET_MUTE: ret =  responseInts(p); break;
				 case RIL_REQUEST_QUERY_CLIP: ret =  responseInts(p); break;
				 case RIL_REQUEST_QUERY_COLP: ret =  responseInts(p); break;
				 case RIL_REQUEST_QUERY_COLR: ret =  responseInts(p); break;
				 case RIL_REQUEST_LAST_DATA_CALL_FAIL_CAUSE: ret =	responseInts(p); break;
				 case RIL_REQUEST_DATA_CALL_LIST: ret =  responseDataCallList(p); break;
				 case RIL_REQUEST_RESET_RADIO: ret =  responseVoid(p); break;
				 case RIL_REQUEST_OEM_HOOK_RAW: ret =  responseRaw(p); break;
				 case RIL_REQUEST_OEM_HOOK_STRINGS: ret =  responseStrings(p); break;
				 case RIL_REQUEST_SCREEN_STATE: ret =  responseVoid(p); break;
				 case RIL_REQUEST_SET_SUPP_SVC_NOTIFICATION: ret =	responseVoid(p); break;
				 case RIL_REQUEST_WRITE_SMS_TO_SIM: ret =  responseInts(p); break;
				 case RIL_REQUEST_DELETE_SMS_ON_SIM: ret =	responseVoid(p); break;
				 case RIL_REQUEST_SET_BAND_MODE: ret =	responseVoid(p); break;
				 case RIL_REQUEST_QUERY_AVAILABLE_BAND_MODE: ret =	responseInts(p); break;
				 case RIL_REQUEST_STK_GET_PROFILE: ret =  responseString(p); break;
				 case RIL_REQUEST_STK_SET_PROFILE: ret =  responseVoid(p); break;
				 case RIL_REQUEST_STK_SEND_ENVELOPE_COMMAND: ret =	responseString(p); break;
				 case RIL_REQUEST_STK_SEND_TERMINAL_RESPONSE: ret =  responseVoid(p); break;
				 case RIL_REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM: ret =  responseInts(p); break;
				 case RIL_REQUEST_EXPLICIT_CALL_TRANSFER: ret =  responseVoid(p); break;
				 case RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE: ret =  responseVoid(p); break;
				 case RIL_REQUEST_GET_PREFERRED_NETWORK_TYPE: ret =  responseInts(p); break;
				 case RIL_REQUEST_GET_NEIGHBORING_CELL_IDS: ret = responseCellList(p); break;
				 case RIL_REQUEST_SET_LOCATION_UPDATES: ret =  responseVoid(p); break;
				 case RIL_REQUEST_CDMA_SET_SUBSCRIPTION: ret =	responseVoid(p); break;
				 case RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE: ret =  responseVoid(p); break;
				 case RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE: ret =	responseInts(p); break;
				 case RIL_REQUEST_SET_TTY_MODE: ret =  responseVoid(p); break;
				 case RIL_REQUEST_QUERY_TTY_MODE: ret =  responseInts(p); break;
				 case RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE: ret =	responseVoid(p); break;
				 case RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE: ret =  responseInts(p); break;
				 case RIL_REQUEST_CDMA_FLASH: ret =  responseVoid(p); break;
				 case RIL_REQUEST_CDMA_BURST_DTMF: ret =  responseVoid(p); break;
				 case RIL_REQUEST_CDMA_SEND_SMS: ret =	responseSMS(p); break;
				 case RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE: ret =  responseVoid(p); break;
				 case RIL_REQUEST_GSM_GET_BROADCAST_CONFIG: ret =  responseGmsBroadcastConfig(p); break;
				 case RIL_REQUEST_GSM_SET_BROADCAST_CONFIG: ret =  responseVoid(p); break;
				 case RIL_REQUEST_GSM_BROADCAST_ACTIVATION: ret =  responseVoid(p); break;
				 case RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG: ret = responseCdmaBroadcastConfig(p); break;
				 case RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG: ret = responseVoid(p); break;
				 case RIL_REQUEST_CDMA_BROADCAST_ACTIVATION: ret = responseVoid(p); break;
				 case RIL_REQUEST_CDMA_VALIDATE_AND_WRITE_AKEY: ret =  responseVoid(p); break;
				 case RIL_REQUEST_CDMA_SUBSCRIPTION: ret = responseStrings(p); break;
				 case RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM: ret =  responseInts(p); break;
				 case RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM: ret =  responseVoid(p); break;
				 case RIL_REQUEST_DEVICE_IDENTITY: ret =  responseStrings(p); break;
				 case RIL_REQUEST_GET_SMSC_ADDRESS: ret = responseSMSCString(p); break;
				 case RIL_REQUEST_SET_SMSC_ADDRESS: ret = responseVoid(p); break;
				 case RIL_REQUEST_EXIT_EMERGENCY_CALLBACK_MODE: ret = responseVoid(p); break;
				 case RIL_REQUEST_REPORT_SMS_MEMORY_STATUS: ret = responseVoid(p); break;
				 case RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING: ret = responseVoid(p); break;
				 case RIL_REQUEST_QUERY_FACILITY_LOCK: ret =  responseInts(p); break;
				 case RIL_REQUEST_SET_FACILITY_LOCK: ret =  responseInts(p); break;
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
				 case RIL_REQUEST_GPRS_ATTACH: ret = responseGprsAttached(p); break;
				 case RIL_REQUEST_GPRS_DETACH: ret = responseVoid(p); break;
				 case RIL_REQUEST_GET_REMAIN_TIMES: ret =  responseInts(p); break;
				 case RIL_REQUEST_GET_SIM_CAPACITY: ret =  responseStrings(p); break;
				 case RIL_REQUEST_MMI_ENTER_SIM: ret =	responseInts(p); break;
                 case RIL_REQUEST_SET_CMMS: ret =  responseVoid(p); break;
                 case RIL_REQUEST_SIM_POWER: ret =  responseVoid(p); break;
                 case RIL_REQUEST_HANGUP_ALL_CALLS: ret =  responseVoid(p); break;
				 default:
				 	throw new RuntimeException("Unrecognized solicited response: " + rr.mRequest);
							       //break;
			 }} catch (Throwable tr) {
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
			if (ret == null) return "";

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
			} else{
				return super.retToString(req, ret);
			}
			return s;
		}

	 
	protected void
	processUnsolicited (Parcel p) {
			int response;
			Object ret;

			response = p.readInt();

			try {switch(response) {

				case RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED: ret =  responseVoid(p); break;
				case RIL_UNSOL_RESPONSE_CALL_STATE_CHANGED: ret =	responseVoid(p); break;
				case RIL_UNSOL_RESPONSE_NETWORK_STATE_CHANGED: ret =  responseVoid(p); break;
				case RIL_UNSOL_RESPONSE_NEW_SMS: ret =  responseString(p); break;
				case RIL_UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT: ret =  responseString(p); break;
				case RIL_UNSOL_RESPONSE_NEW_SMS_ON_SIM: ret =	responseInts(p); break;
				//add by liguxiang 07-15-11 for MS253993 begin
				//case RIL_UNSOL_ON_USSD: ret =	responseStrings(p); break;
				case RIL_UNSOL_ON_USSD: ret =	responseUnsolUssdStrings(p); break;
				//add by liguxiang 07-15-11 for MS253993 end
				case RIL_UNSOL_NITZ_TIME_RECEIVED: ret =  responseString(p); break;
				case RIL_UNSOL_SIGNAL_STRENGTH: ret = responseSignalStrength(p); break;
				case RIL_UNSOL_DATA_CALL_LIST_CHANGED: ret = responseDataCallList(p);break;
				case RIL_UNSOL_SUPP_SVC_NOTIFICATION: ret = responseSuppServiceNotification(p); break;
				case RIL_UNSOL_STK_SESSION_END: ret = responseVoid(p); break;
				case RIL_UNSOL_STK_PROACTIVE_COMMAND: ret = responseString(p); break;
				case RIL_UNSOL_STK_EVENT_NOTIFY: ret = responseString(p); break;
			     // case RIL_UNSOL_STK_CALL_SETUP: ret = responseInts(p); break;
				case RIL_UNSOL_STK_CALL_SETUP: ret = responseString(p); break;
				case RIL_UNSOL_SIM_SMS_STORAGE_FULL: ret =  responseVoid(p); break;
				case RIL_UNSOL_SIM_REFRESH: ret =	responseInts(p); break;
				case RIL_UNSOL_CALL_RING: ret =  responseCallRing(p); break;
				case RIL_UNSOL_RESTRICTED_STATE_CHANGED: ret = responseInts(p); break;
				case RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED:  ret =  responseVoid(p); break;
				case RIL_UNSOL_RESPONSE_CDMA_NEW_SMS:	ret =  responseCdmaSms(p); break;
				case RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS:  ret =	responseString(p); break;
				case RIL_UNSOL_CDMA_RUIM_SMS_STORAGE_FULL:  ret =	responseVoid(p); break;
				case RIL_UNSOL_ENTER_EMERGENCY_CALLBACK_MODE: ret = responseVoid(p); break;
				case RIL_UNSOL_CDMA_CALL_WAITING: ret = responseCdmaCallWaiting(p); break;
				case RIL_UNSOL_CDMA_OTA_PROVISION_STATUS: ret = responseInts(p); break;
				case RIL_UNSOL_CDMA_INFO_REC: ret = responseCdmaInformationRecord(p); break;
				case RIL_UNSOL_OEM_HOOK_RAW: ret = responseRaw(p); break;
				case RIL_UNSOL_RINGBACK_TONE: ret = responseInts(p); break;
				case RIL_UNSOL_RESEND_INCALL_MUTE: ret = responseVoid(p); break;
				case RIL_UNSOL_VIDEOPHONE_DATA: ret = responseString(p); break;
				case RIL_UNSOL_VIDEOPHONE_CODEC: ret = responseInts(p); break;
				case RIL_UNSOL_VIDEOPHONE_STRING: ret = responseString(p); break;
				case RIL_UNSOL_VIDEOPHONE_REMOTE_MEDIA: ret = responseInts(p); break;
				case RIL_UNSOL_VIDEOPHONE_RELEASING: ret = responseString(p); break;
				case RIL_UNSOL_VIDEOPHONE_RECORD_VIDEO: ret = responseInts(p); break;
				case RIL_UNSOL_VIDEOPHONE_DSCI: ret = responseDSCI(p); break;
                case RIL_UNSOL_VIDEOPHONE_MEDIA_START: ret = responseInts(p); break;
				case RIL_UNSOL_RESPONSE_VIDEOCALL_STATE_CHANGED:ret =  responseVoid(p); break;
				case RIL_UNSOL_ON_STIN:ret = responseInts(p); break;
				case RIL_UNSOL_SIM_SMS_READY:ret = responseVoid(p); break;
				case RIL_UNSOL_SYNC_IND: ret =  responseVoid(p); break;
				default:
					throw new RuntimeException("Unrecognized unsol response: " + response);
										//break; (implied)
			}} catch (Throwable tr) {
				Log.e(LOG_TAG, "Exception processing unsol response: " + response +
						"Exception:" + tr.toString());
				return;
			}

			switch(response) {
				case RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED:

					setRadioStateFromRILInt(p.readInt());

					if (RILJ_LOGD) unsljLogMore(response, mState.toString());
					break;
				case RIL_UNSOL_RESPONSE_CALL_STATE_CHANGED:
					if (RILJ_LOGD) unsljLog(response);

					mCallStateRegistrants
						.notifyRegistrants(new AsyncResult(null, null, null));
					break;
				case RIL_UNSOL_RESPONSE_NETWORK_STATE_CHANGED:
					if (RILJ_LOGD) unsljLog(response);

					mNetworkStateRegistrants
						.notifyRegistrants(new AsyncResult(null, null, null));
					break;
				case RIL_UNSOL_RESPONSE_NEW_SMS: {
					if (RILJ_LOGD) unsljLog(response);

					 // FIXME this should move up a layer
					 String a[] = new String[2];
					 a[1] = (String)ret;

					 SmsMessage sms;

					 sms = SmsMessage.newFromCMT(a);
					 if (mSMSRegistrant != null) {
						 mSMSRegistrant
						 .notifyRegistrant(new AsyncResult(null, sms, null));
					 }
					 break;
				 }
				case RIL_UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT:
								 if (RILJ_LOGD) unsljLogRet(response, ret);

								 if (mSmsStatusRegistrant != null) {
									 mSmsStatusRegistrant.notifyRegistrant(
											 new AsyncResult(null, ret, null));
								 }
								 break;
				case RIL_UNSOL_RESPONSE_NEW_SMS_ON_SIM:
								 if (RILJ_LOGD) unsljLogRet(response, ret);

								 int[] smsIndex = (int[])ret;

								 if(smsIndex.length == 1) {
									 if (mSmsOnSimRegistrant != null) {
										 mSmsOnSimRegistrant.
											 notifyRegistrant(new AsyncResult(null, smsIndex, null));
									 }
								 } else {
									 if (RILJ_LOGD) riljLog(" NEW_SMS_ON_SIM ERROR with wrong length "
											 + smsIndex.length);
								 }
								 break;
				case RIL_UNSOL_ON_USSD:
								 String[] resp = (String[])ret;

								 if (resp.length < 2) {
									 resp = new String[2];
									 resp[0] = ((String[])ret)[0];
									 resp[1] = null;
								 }
								 if (RILJ_LOGD) unsljLogMore(response, resp[0]);
								 if (mUSSDRegistrant != null) {
									 mUSSDRegistrant.notifyRegistrant(
											 new AsyncResult (null, resp, null));
								 }
								 break;
				case RIL_UNSOL_NITZ_TIME_RECEIVED:
								 if (RILJ_LOGD) unsljLogRet(response, ret);

								 // has bonus long containing milliseconds since boot that the NITZ
								 // time was received
								 long nitzReceiveTime = p.readLong();

								 Object[] result = new Object[2];

								 result[0] = ret;
								 result[1] = Long.valueOf(nitzReceiveTime);

								 if (mNITZTimeRegistrant != null) {

									 mNITZTimeRegistrant
										 .notifyRegistrant(new AsyncResult (null, result, null));
								 } else {
									 // in case NITZ time registrant isnt registered yet
									 mLastNITZTimeInfo = result;
								 }
								 break;

				case RIL_UNSOL_SIGNAL_STRENGTH:
								 // Note this is set to "verbose" because it happens
								 // frequently
								 if (RILJ_LOGV) unsljLogvRet(response, ret);

								 if (mSignalStrengthRegistrant != null) {
									 mSignalStrengthRegistrant.notifyRegistrant(
											 new AsyncResult (null, ret, null));
								 }
								 break;
				case RIL_UNSOL_DATA_CALL_LIST_CHANGED:
								 if (RILJ_LOGD) unsljLogRet(response, ret);

								 mDataConnectionRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
								 break;

				case RIL_UNSOL_SUPP_SVC_NOTIFICATION:
								 if (RILJ_LOGD) unsljLogRet(response, ret);

								 if (mSsnRegistrant != null) {
									 mSsnRegistrant.notifyRegistrant(
											 new AsyncResult (null, ret, null));
								 }
								 break;

				case RIL_UNSOL_STK_SESSION_END:
								 if (RILJ_LOGD) unsljLog(response);

								 if (mStkSessionEndRegistrant != null) {
									 mStkSessionEndRegistrant.notifyRegistrant(
											 new AsyncResult (null, ret, null));
								 }
								 break;

				case RIL_UNSOL_STK_PROACTIVE_COMMAND:
								 if (RILJ_LOGD) unsljLogRet(response, ret);

								 if (mStkProCmdRegistrant != null) {
									 mStkProCmdRegistrant.notifyRegistrant(
											 new AsyncResult (null, ret, null));
								 }
								 break;

				case RIL_UNSOL_STK_EVENT_NOTIFY:
								 if (RILJ_LOGD) unsljLogRet(response, ret);

								 if (mStkEventRegistrant != null) {
									 mStkEventRegistrant.notifyRegistrant(
											 new AsyncResult (null, ret, null));
								 }
								 break;

				case RIL_UNSOL_STK_CALL_SETUP:
								 if (RILJ_LOGD) unsljLogRet(response, ret);

								 if (mStkCallSetUpRegistrant != null) {
									 mStkCallSetUpRegistrant.notifyRegistrant(
											 new AsyncResult (null, ret, null));
								 }
								 break;

				case RIL_UNSOL_SIM_SMS_STORAGE_FULL:
								 if (RILJ_LOGD) unsljLog(response);

								 if (mIccSmsFullRegistrant != null) {
									 mIccSmsFullRegistrant.notifyRegistrant();
								 }
								 break;

				case RIL_UNSOL_SIM_REFRESH:
								 if (RILJ_LOGD) unsljLogRet(response, ret);

								 if (mIccRefreshRegistrant != null) {
									 mIccRefreshRegistrant.notifyRegistrant(
											 new AsyncResult (null, ret, null));
								 }
								 break;

				case RIL_UNSOL_CALL_RING:
								 if (RILJ_LOGD) unsljLogRet(response, ret);

								 if (mRingRegistrant != null) {
									 mRingRegistrant.notifyRegistrant(
											 new AsyncResult (null, ret, null));
								 }
								 break;

	            case RIL_UNSOL_SIM_SMS_READY:
	                if (RILJ_LOGD) unsljLogRet(response, ret);

	                if (mSimSmsReadyRegistrant != null) {
	                    mSimSmsReadyRegistrant.notifyRegistrant(
	                            new AsyncResult (null, ret, null));
	                }
	                break;

				case RIL_UNSOL_RESTRICTED_STATE_CHANGED:
								 if (RILJ_LOGD) unsljLogvRet(response, ret);
								 if (mRestrictedStateRegistrant != null) {
									 mRestrictedStateRegistrant.notifyRegistrant(
											 new AsyncResult (null, ret, null));
								 }
								 break;

				case RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED:
								 if (RILJ_LOGD) unsljLog(response);

								 if (mIccStatusChangedRegistrants != null) {
									 mIccStatusChangedRegistrants.notifyRegistrants();
								 }
								 break;

				case RIL_UNSOL_RESPONSE_CDMA_NEW_SMS:
								 if (RILJ_LOGD) unsljLog(response);

								 SmsMessage sms = (SmsMessage) ret;

								 if (mSMSRegistrant != null) {
									 mSMSRegistrant
										 .notifyRegistrant(new AsyncResult(null, sms, null));
								 }
								 break;

				case RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS:
								 if (RILJ_LOGD) unsljLog(response);

								 Log.i("RILJ","new cbsms >>>>>>>>"+ret);


								 if (mGsmBroadcastSmsRegistrant != null) {
									 mGsmBroadcastSmsRegistrant
										 .notifyRegistrant(new AsyncResult(null, ret, null));
								 }
								 break;

				case RIL_UNSOL_CDMA_RUIM_SMS_STORAGE_FULL:
								 if (RILJ_LOGD) unsljLog(response);

								 if (mIccSmsFullRegistrant != null) {
									 mIccSmsFullRegistrant.notifyRegistrant();
								 }
								 break;

				case RIL_UNSOL_ENTER_EMERGENCY_CALLBACK_MODE:
								 if (RILJ_LOGD) unsljLog(response);

								 if (mEmergencyCallbackModeRegistrant != null) {
									 mEmergencyCallbackModeRegistrant.notifyRegistrant();
								 }
								 break;

				case RIL_UNSOL_CDMA_CALL_WAITING:
								 if (RILJ_LOGD) unsljLogRet(response, ret);

								 if (mCallWaitingInfoRegistrants != null) {
									 mCallWaitingInfoRegistrants.notifyRegistrants(
											 new AsyncResult (null, ret, null));
								 }
								 break;

				case RIL_UNSOL_CDMA_OTA_PROVISION_STATUS:
								 if (RILJ_LOGD) unsljLogRet(response, ret);

								 if (mOtaProvisionRegistrants != null) {
									 mOtaProvisionRegistrants.notifyRegistrants(
											 new AsyncResult (null, ret, null));
								 }
								 break;

				case RIL_UNSOL_CDMA_INFO_REC:
								 ArrayList<CdmaInformationRecords> listInfoRecs;

								 try {
									 listInfoRecs = (ArrayList<CdmaInformationRecords>)ret;
								 } catch (ClassCastException e) {
									 Log.e(LOG_TAG, "Unexpected exception casting to listInfoRecs", e);
									 break;
								 }

								 for (CdmaInformationRecords rec : listInfoRecs) {
									 if (RILJ_LOGD) unsljLogRet(response, rec);
									 notifyRegistrantsCdmaInfoRec(rec);
								 }
								 break;

				case RIL_UNSOL_OEM_HOOK_RAW:
								 if (RILJ_LOGD) unsljLogvRet(response, IccUtils.bytesToHexString((byte[])ret));
								 if (mUnsolOemHookRawRegistrant != null) {
									 mUnsolOemHookRawRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
								 }
								 break;

				case RIL_UNSOL_RINGBACK_TONE:
								 if (RILJ_LOGD) unsljLogvRet(response, ret);
								 if (mRingbackToneRegistrants != null) {
									 boolean playtone = (((int[])ret)[0] == 1);
									 mRingbackToneRegistrants.notifyRegistrants(
											 new AsyncResult (null, playtone, null));
								 }
								 break;

				case RIL_UNSOL_RESEND_INCALL_MUTE:
								 if (RILJ_LOGD) unsljLogRet(response, ret);

								 if (mResendIncallMuteRegistrants != null) {
									 mResendIncallMuteRegistrants.notifyRegistrants(
											 new AsyncResult (null, ret, null));
								 }
								 break; 		
				case RIL_UNSOL_VIDEOPHONE_DATA:{		
								       if (RILJ_LOGD) unsljLogRet(response, ret);

								       /*int[] params = (int[])ret;

								       if(params.length == 1) {
									       if (mVPDataRegistrant != null) {
										       mVPDataRegistrant
											       .notifyRegistrant(new AsyncResult(null, params, null));
									       }
								       } else {
									       if (RILJ_LOGD) riljLog(" RIL_UNSOL_VIDEOPHONE_DATA ERROR with wrong length "
											       + params.length);
								       }*/
									if (mVPDataRegistrant != null) {
										mVPDataRegistrant
											.notifyRegistrant(new AsyncResult(null, ret, null));
									}
								       break;
							       }
				case RIL_UNSOL_VIDEOPHONE_CODEC:{		
									if (RILJ_LOGD) unsljLogRet(response, ret);

									int[] params = (int[])ret;
								       mVPCodecRegistrants
									       .notifyRegistrants(new AsyncResult(null, params, null));
									break;
								}
				case RIL_UNSOL_VIDEOPHONE_STRING: 
								if (RILJ_LOGD) unsljLog(response);

								if (mVPStrsRegistrant != null) {
									mVPStrsRegistrant
										.notifyRegistrant(new AsyncResult(null, ret, null));
								}
								if (mVPRemoteCameraRegistrant != null) {
									String str = (String)ret;
									if (str.equals("open_:camera_")){
										mVPRemoteCameraRegistrant.notifyRegistrant(new AsyncResult(null, true, null));
									} else if (str.equals("close_:camera_")){
										mVPRemoteCameraRegistrant.notifyRegistrant(new AsyncResult(null, false, null));
									}
								}
								break;
				case RIL_UNSOL_VIDEOPHONE_REMOTE_MEDIA: {
										if (RILJ_LOGD) unsljLogRet(response, ret);

										int[] params = (int[])ret;

										if(params.length >= 2) {
											if (mVPRemoteMediaRegistrant != null) {
												mVPRemoteMediaRegistrant
													.notifyRegistrant(new AsyncResult(null, params, null));
											}
										} else {
											if (RILJ_LOGD) riljLog(" RIL_UNSOL_VIDEOPHONE_REMOTE_MEDIA ERROR with wrong length "
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
										if (RILJ_LOGD) unsljLogRet(response, ret);

										int[] params = (int[])ret;

										if(params.length == 1) {
											if (mVPRecordVideoRegistrant != null) {
												mVPRecordVideoRegistrant
													.notifyRegistrant(new AsyncResult(null, params, null));
											}
										} else {
											if (RILJ_LOGD) riljLog(" RIL_UNSOL_VIDEOPHONE_RECORD_VIDEO ERROR with wrong length "
													+ params.length);
										}
										break;
									}	    
				case RIL_UNSOL_VIDEOPHONE_DSCI:{		
								       if (RILJ_LOGD) unsljLogRet(response, ret);

								      /* int[] params = (int[])ret;
								       if (params.length >= 4){
									       if (params[3] == 1){
										       if (params.length == 9){
											       Integer cause = new Integer(params[8]);

											       if (RILJ_LOGD) riljLog(" RIL_UNSOL_VIDEOPHONE_DSCI cause: " + cause);
											       if ((cause == 47) || (cause == 57) || (cause == 58) || (cause == 88)) {
												       if (mVPFallBackRegistrant != null) {
													       mVPFallBackRegistrant.notifyRegistrant(new AsyncResult(null, cause, null));
												       }
											       } else {
												       if (mVPFailRegistrant != null) {
													       mVPFailRegistrant.notifyRegistrant(new AsyncResult(null, cause, null));
												       }
											       }
										       } else {
											       if (RILJ_LOGD) riljLog(" RIL_UNSOL_VIDEOPHONE_DSCI ERROR with wrong length "
													       + params.length);
										       }
									       }
								       } else {
									       if (RILJ_LOGD) riljLog(" RIL_UNSOL_VIDEOPHONE_DSCI ERROR with wrong length "
											       + params.length);
								       }*/
									   
								       DSCIInfo info = (DSCIInfo)ret;
								       if (info.cause > 0) {
									       if (RILJ_LOGD) riljLog(" RIL_UNSOL_VIDEOPHONE_DSCI number: " + info.number + ", cause: " + info.cause);
									       if ((info.cause == 47) || (info.cause == 57) || (info.cause == 58) || (info.cause == 88)) {
										       if (mVPFallBackRegistrant != null) {
											       mVPFallBackRegistrant.notifyRegistrant(new AsyncResult(null, new AsyncResult(info.number, info.cause, null), null));
										       }
									       } else {
										       if (mVPFailRegistrant != null) {
											       mVPFailRegistrant.notifyRegistrant(new AsyncResult(null, new AsyncResult(info.number, info.cause, null), null));
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
							       if (RILJ_LOGD) unsljLog(response);

							       mVideoCallStateRegistrants
								       .notifyRegistrants(new AsyncResult(null, null, null));
							       break; 

				case RIL_UNSOL_ON_STIN:
								 if (RILJ_LOGD) unsljLogRet(response, ret);

								 if (mStkStinRegistrant != null) {
									 mStkStinRegistrant.notifyRegistrant(
											 new AsyncResult (null, ret, null));
								 }
								 break;

                case RIL_UNSOL_SYNC_IND :
                    if (RILJ_LOGD) unsljLog(response);

                    mSycnIndRegistrants
                        .notifyRegistrants(new AsyncResult(null, null, null));
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
			 case RIL_REQUEST_QUERY_FACILITY_LOCK: return "QUERY_FACILITY_LOCK";
			 case RIL_REQUEST_SET_FACILITY_LOCK: return "SET_FACILITY_LOCK";
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
			 case RIL_REQUEST_GET_REMAIN_TIMES: return "REMAIN_TIMES";
			 case RIL_REQUEST_GET_SIM_CAPACITY: return "GET_SIM_CAPACITY";
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
		            case RIL_UNSOL_VIDEOPHONE_RELEASING: return "RIL_UNSOL_VIDEOPHONE_RELEASING";
		            case RIL_UNSOL_VIDEOPHONE_RECORD_VIDEO: return "UNSOL_VIDEOPHONE_RECORD_VIDEO";
            case RIL_UNSOL_VIDEOPHONE_MEDIA_START: return "UNSOL_VIDEOPHONE_MEDIA_START";
			     case RIL_UNSOL_RESPONSE_VIDEOCALL_STATE_CHANGED: return "UNSOL_RESPONSE_VIDEOCALL_STATE_CHANGED";
			     case RIL_UNSOL_SIM_SMS_READY: return "UNSOL_SIM_SMS_READY";
                 case RIL_UNSOL_SYNC_IND: return "UNSOL_SYNC_IND";
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
	 
	 public void mmiEnterSim(String pukNewPin, Message result){

		 RILRequest rr = RILRequest.obtain(RIL_REQUEST_MMI_ENTER_SIM, result);
         
		 rr.mp.writeString(pukNewPin);
		 
		 if (RILJ_LOGD) riljLog(rr.serialString() + "> " + sprdRequestToString(rr.mRequest));

		 send(rr);

	 }

	   
	 
 }

