/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.internal.telephony.gsm.stk;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Bundle;

import android.os.RemoteException;
import android.os.ServiceManager;
import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.gsm.SimCard;
import com.android.internal.telephony.gsm.SIMFileHandler;
import com.android.internal.telephony.gsm.SIMRecords;
import com.android.internal.telephony.gsm.stk.AppInterface.EventListType;


import com.android.internal.telephony.ITelephony;


import android.util.Config;
import android.view.IWindowManager;

import java.io.ByteArrayOutputStream;

/**
 * Enumeration for representing the tag value of COMPREHENSION-TLV objects. If
 * you want to get the actual value, call {@link #value() value} method.
 *
 * {@hide}
 */
enum ComprehensionTlvTag {
  COMMAND_DETAILS(0x01),
  DEVICE_IDENTITIES(0x02),
  RESULT(0x03),
  DURATION(0x04),
  ALPHA_ID(0x05),
  ADDRESS(0x06),
  USSD_STRING(0x0a),
  TEXT_STRING(0x0d),
  TONE(0x0e),
  ITEM(0x0f),
  ITEM_ID(0x10),
  RESPONSE_LENGTH(0x11),
  FILE_LIST(0x12),
  HELP_REQUEST(0x15),
  DEFAULT_TEXT(0x17),
  EVENT_LIST(0x19),
  ICON_ID(0x1e),
  ITEM_ICON_ID_LIST(0x1f),
  DATE_TIME_TIMEZONE(0x26),
  IMMEDIATE_RESPONSE(0x2b),
  LANGUAGE(0x2d),
  URL(0x31),
  BROWSER_TERMINATION_CAUSE(0x34),
  TEXT_ATTRIBUTE(0x50),
  //Deal With DTMF Message Start
  DTMF(0x2c);
  //Deal With DTMF Message End

    private int mValue;

    ComprehensionTlvTag(int value) {
        mValue = value;
    }

    /**
     * Returns the actual value of this COMPREHENSION-TLV object.
     *
     * @return Actual tag value of this object
     */
        public int value() {
            return mValue;
        }

    public static ComprehensionTlvTag fromInt(int value) {
        for (ComprehensionTlvTag e : ComprehensionTlvTag.values()) {
            if (e.mValue == value) {
                return e;
            }
        }
        return null;
    }
}

class RilMessage {
    int mId;
    Object mData;
    ResultCode mResCode;

    RilMessage(int msgId, String rawData) {
        mId = msgId;
        mData = rawData;
    }

    RilMessage(RilMessage other) {
        this.mId = other.mId;
        this.mData = other.mData;
        this.mResCode = other.mResCode;
    }
}

/**
 * Class that implements SIM Toolkit Telephony Service. Interacts with the RIL
 * and application.
 *
 * {@hide}
 */
public class StkService extends Handler implements AppInterface {

    // Class members
    private static SIMRecords mSimRecords;

    // Service members.
    private static StkService sInstance;
    private static CommandsInterface mCmdIf;
    private Context mContext;
    private StkCmdMessage mCurrntCmd = null;
    private StkCmdMessage mMenuCmd = null;

    private RilMessageDecoder mMsgDecoder = null;

    // Service constants.
    static final int MSG_ID_SESSION_END              = 1;
    static final int MSG_ID_PROACTIVE_COMMAND        = 2;
    static final int MSG_ID_EVENT_NOTIFY             = 3;
    static final int MSG_ID_CALL_SETUP               = 4;
    static final int MSG_ID_REFRESH                  = 5;
    static final int MSG_ID_RESPONSE                 = 6;
    static final int MSG_ID_REFRESH_STIN             = 7;

    static final int MSG_ID_RIL_MSG_DECODED          = 10;

    // Events to signal SIM presence or absent in the device.
    private static final int MSG_ID_SIM_LOADED       = 20;

    //Deal With DTMF Message Start
    private static final int MSG_ID_SEND_SECOND_DTMF = 30;

    private static final int MSG_ID_EVENT_DOWNLOAD = 40;

    private static final int SEND_DTMF_INTERVAL = 2*1000;
    //Deal With DTMF Message End
    private static final int DEV_ID_KEYPAD      = 0x01;
    private static final int DEV_ID_DISPLAY     = 0x02;
    private static final int DEV_ID_EARPIECE    = 0x03;
    private static final int DEV_ID_UICC        = 0x81;
    private static final int DEV_ID_TERMINAL    = 0x82;
    private static final int DEV_ID_NETWORK     = 0x83;

    private static final int BROWER_TERMINATION_USER     = 0;
    private static final int BROWER_TERMINATION_ERROR    = 1;

    private static final int EVENT_NUMBER = 11;
    private boolean[] mEventList = null;

    /* Intentionally private for singleton */
    private StkService(CommandsInterface ci, SIMRecords sr, Context context,
            SIMFileHandler fh, SimCard sc) {
        if (ci == null || sr == null || context == null || fh == null
                || sc == null) {
            throw new NullPointerException(
                    "Service: Input parameters must not be null");
        }
        mCmdIf = ci;
        mContext = context;

        // Get the RilMessagesDecoder for decoding the messages.
        mMsgDecoder = RilMessageDecoder.getInstance(this, fh);

        // Register ril events handling.
        mCmdIf.setOnStkSessionEnd(this, MSG_ID_SESSION_END, null);
        mCmdIf.setOnStkProactiveCmd(this, MSG_ID_PROACTIVE_COMMAND, null);
        mCmdIf.setOnStkEvent(this, MSG_ID_EVENT_NOTIFY, null);
        mCmdIf.setOnStkCallSetUp(this, MSG_ID_CALL_SETUP, null);
        mCmdIf.registerForSIMReady(this, MSG_ID_SIM_LOADED, null);
        mCmdIf.setOnStkStin(this, MSG_ID_REFRESH_STIN, null);
       //mCmdIf.setOnSimRefresh(this, MSG_ID_REFRESH, null);

        mSimRecords = sr;

        // Register for SIM ready event.
        //mSimRecords.registerForRecordsLoaded(this, MSG_ID_SIM_LOADED, null);

        mEventList = new boolean[EVENT_NUMBER];
        for (int i = 0; i < EVENT_NUMBER; i++) {
            mEventList[i] = false;
        }
        //mCmdIf.reportStkServiceIsRunning(null);
        StkLog.d(this, "StkService: is running");
    }

    public void dispose() {
        //mSimRecords.unregisterForRecordsLoaded(this);
        mCmdIf.unregisterForSIMReady(this);
        mCmdIf.unSetOnStkSessionEnd(this);
        mCmdIf.unSetOnStkProactiveCmd(this);
        mCmdIf.unSetOnStkEvent(this);
        mCmdIf.unSetOnStkCallSetUp(this);
        mCmdIf.unsetOnStkStin(this);

        this.removeCallbacksAndMessages(null);
    }

    protected void finalize() {
        StkLog.d(this, "Service finalized");
    }

    private void handleRilMsg(RilMessage rilMsg) {
        if (rilMsg == null) {
            StkLog.d(this, "rilMsg is null");
            return;
        }

        // dispatch messages
        CommandParams cmdParams = null;
        StkLog.d(this, "handleRilMsg rilMsg.mId = " + rilMsg.mId + " mResCode = " + rilMsg.mResCode );
        switch (rilMsg.mId) {
        case MSG_ID_EVENT_NOTIFY:
            if (rilMsg.mResCode == ResultCode.OK) {
                cmdParams = (CommandParams) rilMsg.mData;
                if (cmdParams != null) {
                    handleProactiveCommand(cmdParams);
                } else {
                    StkLog.d(this, "MSG_ID_EVENT_NOTIFY: cmdParams is null");
                }
            }
            break;
        case MSG_ID_PROACTIVE_COMMAND:
            cmdParams = (CommandParams) rilMsg.mData;
            if (cmdParams != null) {
                if (rilMsg.mResCode == ResultCode.OK) {
                    handleProactiveCommand(cmdParams);
                } else {
                    // for proactive commands that couldn't be decoded
                    // successfully respond with the code generated by the
                    // message decoder.
                    sendTerminalResponse(cmdParams.cmdDet, rilMsg.mResCode,
                            false, 0, null);
                }
            }
            break;
        case MSG_ID_REFRESH:
            cmdParams = (CommandParams) rilMsg.mData;
            if (cmdParams != null) {
                handleProactiveCommand(cmdParams);
            }
            break;
        case MSG_ID_SESSION_END:
            handleSessionEnd();
            break;
        case MSG_ID_CALL_SETUP:
            // prior event notify command supplied all the information
            // needed for set up call processing.
            StkLog.d(this, "[stk] MSG_ID_CALL_SETUP rescode = "+rilMsg.mResCode);
            if (rilMsg.mResCode == ResultCode.OK) {
                cmdParams = (CommandParams) rilMsg.mData;
                if (cmdParams != null) {
                    handleProactiveCommand(cmdParams);
                } else {
                    StkLog.d(this, "[stk] cmdParams is NULL");
                }
            }
            break;
        }
    }

    /**
     * Handles RIL_UNSOL_STK_PROACTIVE_COMMAND unsolicited command from RIL.
     * Sends valid proactive command data to the application using intents.
     *
     */
    private void handleProactiveCommand(CommandParams cmdParams) {
        StkLog.d(this, cmdParams.getCommandType().name());

        StkCmdMessage cmdMsg = new StkCmdMessage(cmdParams);
        switch (cmdParams.getCommandType()) {
        case SET_UP_MENU:
            if (removeMenu(cmdMsg.getMenu())) {
                mMenuCmd = null;
            } else {
                mMenuCmd = cmdMsg;
            }
            sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false, 0,
                    null);
            break;
        case DISPLAY_TEXT:
            // when application is not required to respond, send an immediate
            // response.
            if (!cmdMsg.geTextMessage().responseNeeded) {
                sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false,
                        0, null);
            }
            break;
//        case REFRESH:
            // ME side only handles refresh commands which meant to remove IDLE
            // MODE TEXT.
//            cmdParams.cmdDet.typeOfCommand = CommandType.SET_UP_IDLE_MODE_TEXT
//                    .value();
//            break;
        case SET_UP_IDLE_MODE_TEXT:
            sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false,
                    0, null);
            break;

        //Deal With DTMF Message Start
        case SEND_DTMF:
            DtmfMessage dtmf = cmdMsg.getDtmfMessage();
            retrieveDtmfString(cmdParams,dtmf.mdtmfString);
        break;
        case PROVIDE_LOCAL_INFORMATION:
            ResponseData resp;
            switch (cmdParams.cmdDet.commandQualifier) {
                case 0x3:// date time and timezone
                    resp = new DateTimeResponseData();
                    sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false, 0, resp);
                    break;
                case 0x4:// language setting
                    Configuration config = mContext.getResources().getConfiguration();
                    String langCode = config.locale.getLanguage();
                    resp = new LanguageResponseData(langCode);
                    sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false, 0, resp);
                    break;
                default:
                    break;
            }
        return;
        //Deal With DTMF Message Start
        case LAUNCH_BROWSER:
        case SELECT_ITEM:
        case GET_INPUT:
        case GET_INKEY:
        case SEND_SMS:
        case SEND_SS:
        case SEND_USSD:
        case PLAY_TONE:
        case SET_UP_CALL:
            // nothing to do on telephony!
            break;
        case REFRESH:
            sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false,
                    0, null);
            break;
        case SET_UP_EVENT_LIST:
            AppInterface.EventListType[] eventList = cmdMsg.getEventList();
            StkLog.d(this, "[stk] handleProactiveCommand: SET_UP_EVENT_LIST");

            for (int i = 0; i < EVENT_NUMBER; i++) {
                setEventEnabled(i, false);
            }
            if (eventList != null) {
                for (int i = 0; i < eventList.length; i++) {
                    setEventEnabled(eventList[i].value(), true);
                }
//                IWindowManager wm = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));
//                if(isValidEvent(AppInterface.EventListType.Event_UserActivity.value())) {
//                    try {
//                        wm.setEventUserActivityNeeded(true);
//                    } catch (RemoteException e) {
//                        StkLog.d(this, "<" + mPhoneId + ">" + "Exception when set EventDownloadNeeded flag in WindowManager");
//                    } catch (NullPointerException e2) {
//                          StkLog.d(this, "<" + mPhoneId + ">" + "wm is null");
//                    }
//                }
//                if(isValidEvent(AppInterface.EventListType.Event_IdleScreenAvailable.value())) {
//                    try {
//                        wm.setEventIdleScreenNeeded(true);
//                    } catch (RemoteException e) {
//                        StkLog.d(this, "<" + mPhoneId + ">" + "Exception when set EventDownloadNeeded flag in WindowManager");
//                    } catch (NullPointerException e2) {
//                          StkLog.d(this, "<" + mPhoneId + ">" + "wm is null");
//                    }
//                }
            }
            sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false, 0, null);
            return;
        default:
            StkLog.d(this, "Unsupported command");
            return;
        }
        mCurrntCmd = cmdMsg;
        Intent intent = new Intent(AppInterface.STK_CMD_ACTION);
        intent.putExtra("STK CMD", cmdMsg);
        mContext.sendBroadcast(intent);
    }

    /**
     * Handles RIL_UNSOL_STK_SESSION_END unsolicited command from RIL.
     *
     */
    private void handleSessionEnd() {
        StkLog.d(this, "SESSION END");

        mCurrntCmd = mMenuCmd;
        Intent intent = new Intent(AppInterface.STK_SESSION_END_ACTION);
        mContext.sendBroadcast(intent);
    }

    private void sendTerminalResponse(CommandDetails cmdDet,
            ResultCode resultCode, boolean includeAdditionalInfo,
            int additionalInfo, ResponseData resp) {

        if (cmdDet == null) {
            return;
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        // command details
        int tag = ComprehensionTlvTag.COMMAND_DETAILS.value();
        if (cmdDet.compRequired) {
            tag |= 0x80;
        }
        buf.write(tag);
        buf.write(0x03); // length
        buf.write(cmdDet.commandNumber);
        buf.write(cmdDet.typeOfCommand);
        buf.write(cmdDet.commandQualifier);

        // device identities
        tag = 0x80 | ComprehensionTlvTag.DEVICE_IDENTITIES.value();
        buf.write(tag);
        buf.write(0x02); // length
        buf.write(DEV_ID_TERMINAL); // source device id
        buf.write(DEV_ID_UICC); // destination device id

        // result
        tag = 0x80 | ComprehensionTlvTag.RESULT.value();
        buf.write(tag);
        int length = includeAdditionalInfo ? 2 : 1;
        buf.write(length);
        buf.write(resultCode.value());

        // additional info
        if (includeAdditionalInfo) {
            buf.write(additionalInfo);
        }

        // Fill optional data for each corresponding command
        if (resp != null) {
            resp.format(buf);
        }

        byte[] rawData = buf.toByteArray();
        String hexString = IccUtils.bytesToHexString(rawData);
        if (Config.LOGD) {
            StkLog.d(this, "TERMINAL RESPONSE: " + hexString);
        }

        mCmdIf.sendTerminalResponse(hexString, null);
    }


    private void sendMenuSelection(int menuId, boolean helpRequired) {

        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        // tag
        int tag = BerTlv.BER_MENU_SELECTION_TAG;
        buf.write(tag);

        // length
        buf.write(0x00); // place holder

        // device identities
        tag = 0x80 | ComprehensionTlvTag.DEVICE_IDENTITIES.value();
        buf.write(tag);
        buf.write(0x02); // length
        buf.write(DEV_ID_KEYPAD); // source device id
        buf.write(DEV_ID_UICC); // destination device id

        // item identifier
        tag = 0x80 | ComprehensionTlvTag.ITEM_ID.value();
        buf.write(tag);
        buf.write(0x01); // length
        buf.write(menuId); // menu identifier chosen

        // help request
        if (helpRequired) {
            tag = ComprehensionTlvTag.HELP_REQUEST.value();
            buf.write(tag);
            buf.write(0x00); // length
        }

        byte[] rawData = buf.toByteArray();

        // write real length
        int len = rawData.length - 2; // minus (tag + length)
        rawData[1] = (byte) len;

        String hexString = IccUtils.bytesToHexString(rawData);

        mCmdIf.sendEnvelope(hexString, null);
    }

    private void eventDownload(int event, int sourceId, int destinationId,
            byte[] additionalInfo) {

        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        // tag
        int tag = BerTlv.BER_EVENT_DOWNLOAD_TAG;
        buf.write(tag);

        // length
        buf.write(0x00); // place holder, assume length < 128.

        // event list
        tag = 0x80 | ComprehensionTlvTag.EVENT_LIST.value();
        buf.write(tag);
        buf.write(0x01); // length
        buf.write(event); // event value

        // device identities
        tag = 0x80 | ComprehensionTlvTag.DEVICE_IDENTITIES.value();
        buf.write(tag);
        buf.write(0x02); // length
        buf.write(sourceId); // source device id
        buf.write(destinationId); // destination device id

        // additional information
        if (additionalInfo != null) {
            for (byte b : additionalInfo) {
                buf.write(b);
            }
        }

        byte[] rawData = buf.toByteArray();

        // write real length
        int len = rawData.length - 2; // minus (tag + length)
        rawData[1] = (byte) len;

        String hexString = IccUtils.bytesToHexString(rawData);

        mCmdIf.sendEnvelope(hexString, null);
    }

    /**
     * Used for instantiating/updating the Service from the GsmPhone constructor.
     *
     * @param ci CommandsInterface object
     * @param sr SIMRecords object
     * @param context phone app context
     * @param fh SIM file handler
     * @param sc GSM SIM card
     * @return The only Service object in the system
     */
    public static StkService getInstance(CommandsInterface ci, SIMRecords sr,
            Context context, SIMFileHandler fh, SimCard sc) {
        if (sInstance == null) {
            if (ci == null || sr == null || context == null || fh == null
                    || sc == null) {
                return null;
            }
            HandlerThread thread = new HandlerThread("Stk Telephony service");
            thread.start();
            sInstance = new StkService(ci, sr, context, fh, sc);
            StkLog.d(sInstance, "NEW sInstance");
//      } else if ((sr != null) && (mSimRecords != sr)) {
        } else if ((ci != null) && (mCmdIf != ci)) {
            StkLog.d(sInstance, "Reinitialize the Service with SIMRecords");
            //mSimRecords = sr;

            // re-Register for SIM ready event.
            //mSimRecords.registerForRecordsLoaded(sInstance, MSG_ID_SIM_LOADED, null);
            mCmdIf = ci;
            mCmdIf.registerForSIMReady(sInstance, MSG_ID_SIM_LOADED, null);
            StkLog.d(sInstance, "sr changed reinitialize and return current sInstance");
        } else {
            StkLog.d(sInstance, "Return current sInstance");
        }
        return sInstance;
    }

    /**
     * Used by application to get an AppInterface object.
     *
     * @return The only Service object in the system
     */
    public static AppInterface getInstance() {
        return getInstance(null, null, null, null, null);
    }

    @Override
    public void handleMessage(Message msg) {

        switch (msg.what) {
        case MSG_ID_SESSION_END:
        case MSG_ID_PROACTIVE_COMMAND:
        case MSG_ID_EVENT_NOTIFY:
        case MSG_ID_REFRESH:
        case MSG_ID_CALL_SETUP:
//            StkLog.d(this, "ril message arrived");
            StkLog.d(this, "[stk]ril message arrived = " + msg.what);
            String data = null;
            if (msg.obj != null) {
                AsyncResult ar = (AsyncResult) msg.obj;
                if (ar != null && ar.result != null) {
                    try {
                        data = (String) ar.result;
                    } catch (ClassCastException e) {
                        break;
                    }
                }
            }
            mMsgDecoder.sendStartDecodingMessageParams(new RilMessage(msg.what, data));
            break;
//        case MSG_ID_CALL_SETUP:
//            mMsgDecoder.sendStartDecodingMessageParams(new RilMessage(msg.what, null));
//            break;
        case MSG_ID_SIM_LOADED:
            mCmdIf.reportStkServiceIsRunning(null);
            break;
        case MSG_ID_RIL_MSG_DECODED:
            handleRilMsg((RilMessage) msg.obj);
            break;
        case MSG_ID_RESPONSE:
            handleCmdResponse((StkResponseMessage) msg.obj);
            break;
        //Deal With DTMF Message Start
        case MSG_ID_SEND_SECOND_DTMF:
           CommandParams cmdParams = (CommandParams)msg.obj;
           String str = msg.getData().getString("dtmf");
           retrieveDtmfString(cmdParams,str);
           break;
        //Deal With DTMF Message End
        case MSG_ID_EVENT_DOWNLOAD:
            handleEventDownload((StkResponseMessage)msg.obj);
        case MSG_ID_REFRESH_STIN:
            StkLog.d(this, "[stk]MSG_ID_REFRESH_STIN" );
            int result = 1;
            if (msg.obj != null) {
                AsyncResult ar = (AsyncResult) msg.obj;
                if (ar != null && ar.result != null) {
                    int[] params = (int[])ar.result;
                    result = params[0];
                    StkLog.d(this, "[stk]MSG_ID_REFRESH_STIN result = " + result);
                    if (0 == result) {
                        mSimRecords.onRefresh(true, null);
                    }
                    //handleRefreshCmdResponse(result);
                }
            }
            break;
        default:
            throw new AssertionError("Unrecognized STK command: " + msg.what);
        }
    }

    public synchronized void onCmdResponse(StkResponseMessage resMsg) {
        if (resMsg == null) {
            return;
        }
        // queue a response message.
        Message msg = this.obtainMessage(MSG_ID_RESPONSE, resMsg);
        msg.sendToTarget();
    }

    public synchronized void onEventResponse(StkResponseMessage resMsg) {
        if (resMsg == null) {
            return;
        }
        // queue a event message.
        Message msg = this.obtainMessage(MSG_ID_EVENT_DOWNLOAD, resMsg);
        msg.sendToTarget();
    }

    private boolean validateResponse(StkResponseMessage resMsg) {
        if (mCurrntCmd != null) {
            return (resMsg.cmdDet.compareTo(mCurrntCmd.mCmdDet));
        } else {
            StkLog.d(this, "[stk] validateResponse mCurrntCmd is null");
        }
        return false;
    }

    private boolean removeMenu(Menu menu) {
        try {
            if (menu.items.size() == 1 && menu.items.get(0) == null) {
                return true;
            }
        } catch (NullPointerException e) {
            StkLog.d(this, "Unable to get Menu's items size");
            return true;
        }
        return false;
    }
    private boolean isValidEvent(int type) {
        if (type >= 0 && type < EVENT_NUMBER) {
            return mEventList[type];
        }
        return false;
    }

    private void setEventEnabled(int type, boolean value) {
        if (type >= 0 && type < EVENT_NUMBER) {
            mEventList[type] = value;
        }
    }

    private IWindowManager getWindowInterface() {
        return IWindowManager.Stub.asInterface(ServiceManager.getService("window"));
    }

    private void handleEventDownload(StkResponseMessage resMsg) {
        EventListType type = resMsg.event;
        if (!isValidEvent(type.value())) {
            StkLog.d(this, "handleEventDownload is inValid Event");
            return;
        }

        StkLog.d(this, "handleEventDownload event = " + type);
        int sourceId = DEV_ID_TERMINAL;
        int destinationId = DEV_ID_UICC;
        byte[] additionalInfo = null;
        boolean oneShot = false;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int tag;
        IWindowManager wm = getWindowInterface();

        switch(type) {
            case Event_LanguageSelection:
                Configuration config = mContext.getResources().getConfiguration();
                String langCode = config.locale.getLanguage();
                tag = ComprehensionTlvTag.LANGUAGE.value();
                buf.write(tag); // tag
                buf.write(2); // length
                buf.write(langCode.charAt(0));
                buf.write(langCode.charAt(1));
                additionalInfo = buf.toByteArray();
                break;
            case Event_UserActivity:
                oneShot = true;
//                try {
//                    wm.setEventUserActivityNeeded(false);
//                } catch (RemoteException e) {
//                    StkLog.d(this, "Exception when set EventDownloadNeeded flag in WindowManager");
//                } catch (NullPointerException e2) {
//                    StkLog.d(this, "wm is null");
//                }
                break;
            case Event_IdleScreenAvailable:
                oneShot = true;
                sourceId = DEV_ID_DISPLAY;
//                try {
//                    wm.setEventIdleScreenNeeded(false);
//                } catch (RemoteException e) {
//                    StkLog.d(this, "Exception when set EventDownloadNeeded flag in WindowManager");
//                } catch (NullPointerException e2) {
//                    StkLog.d(this, "wm is null");
//                }
                break;
            case Event_BrowserTermination:
                tag = 0x80 | ComprehensionTlvTag.BROWSER_TERMINATION_CAUSE.value();
                buf.write(tag); // tag
                buf.write(1); // length
                buf.write(BROWER_TERMINATION_USER);
                additionalInfo = buf.toByteArray();
                break;
            case Event_DataAvailable:
//                tag = 0x80 | ComprehensionTlvTag.CHANNEL_STATUS.value();
//                buf.write(tag); // tag
//                buf.write(2); // length
//                buf.write(resMsg.ChannelId | (resMsg.LinkStatus ? 0x80 : 0));
//                buf.write(resMsg.mMode);
//                tag = 0x80 | ComprehensionTlvTag.CHANNEL_DATA_LENGTH.value();
//                buf.write(tag);
//                buf.write(1);
//                buf.write(resMsg.channelDataLen);
//                additionalInfo = buf.toByteArray();
                break;
            case Event_ChannelStatus:
//                tag = 0x80 | ComprehensionTlvTag.CHANNEL_STATUS.value();
//                buf.write(tag); // tag
//                buf.write(2); // length
//                buf.write(resMsg.ChannelId | (resMsg.LinkStatus ? 0x80 : 0));
//                buf.write(resMsg.mMode);
//                additionalInfo = buf.toByteArray();
                break;
            default:
                StkLog.d(this, "unknown event");
                return;
        }

        eventDownload(type.value(), sourceId, destinationId, additionalInfo);

        if (oneShot) {
            setEventEnabled(type.value(), false);
        }
    }

    private void handleCmdResponse(StkResponseMessage resMsg) {
        // Make sure the response details match the last valid command. An invalid
        // response is a one that doesn't have a corresponding proactive command
        // and sending it can "confuse" the baseband/ril.
        // One reason for out of order responses can be UI glitches. For example,
        // if the application launch an activity, and that activity is stored
        // by the framework inside the history stack. That activity will be
        // available for relaunch using the latest application dialog
        // (long press on the home button). Relaunching that activity can send
        // the same command's result again to the StkService and can cause it to
        // get out of sync with the SIM.
        if (!validateResponse(resMsg)) {
            StkLog.d(this, "[stk] validateResponse fail!! ");
            if (mCurrntCmd != null) {
                StkLog.d(this, "[stk] mCurrntCmd = " +
                         AppInterface.CommandType.fromInt(mCurrntCmd.mCmdDet.typeOfCommand));
            }
            if (resMsg != null) {
                AppInterface.CommandType type = AppInterface.CommandType.fromInt(resMsg.cmdDet.typeOfCommand);
                StkLog.d(this, "[stk] resMsg cmd = " + type);

                if (type == AppInterface.CommandType.SET_UP_MENU && mCurrntCmd == null) {
                    StkLog.d(this, "Warning: force mCurrntCmd to mMenuCmd!!");
                    mCurrntCmd = mMenuCmd;
                }
            }
            return;
        }
        ResponseData resp = null;
        boolean helpRequired = false;
        int additionalInfo = 0;
        boolean AddInfo = false;
        CommandDetails cmdDet = resMsg.getCmdDetails();

        switch (resMsg.resCode) {
        case HELP_INFO_REQUIRED:
            helpRequired = true;
            // fall through
        case OK:
        case PRFRMD_WITH_PARTIAL_COMPREHENSION:
        case PRFRMD_WITH_MISSING_INFO:
        case PRFRMD_WITH_ADDITIONAL_EFS_READ:
        case PRFRMD_ICON_NOT_DISPLAYED:
        case PRFRMD_MODIFIED_BY_NAA:
        case PRFRMD_LIMITED_SERVICE:
        case PRFRMD_WITH_MODIFICATION:
        case PRFRMD_NAA_NOT_ACTIVE:
        case PRFRMD_TONE_NOT_PLAYED:
            switch (AppInterface.CommandType.fromInt(cmdDet.typeOfCommand)) {
            case SET_UP_MENU:
                helpRequired = resMsg.resCode == ResultCode.HELP_INFO_REQUIRED;
                sendMenuSelection(resMsg.usersMenuSelection, helpRequired);
                return;
            case SELECT_ITEM:
                resp = new SelectItemResponseData(resMsg.usersMenuSelection);
                break;
            case GET_INPUT:
            case GET_INKEY:
                Input input = mCurrntCmd.geInput();
                if (!input.yesNo) {
                    // when help is requested there is no need to send the text
                    // string object.
                    if (!helpRequired) {
                        resp = new GetInkeyInputResponseData(resMsg.usersInput,
                                input.ucs2, input.packed);
                    }
                } else {
                    resp = new GetInkeyInputResponseData(
                            resMsg.usersYesNoSelection);
                }
                break;
            case DISPLAY_TEXT:
            case LAUNCH_BROWSER:
                break;
            case SET_UP_CALL:
                StkLog.d(this, "[stk] handleCmdResponse MSG_ID_CALL_SETUP");
                mCmdIf.handleCallSetupRequestFromSim(resMsg.usersConfirm, null);
                // No need to send terminal response for SET UP CALL. The user's
                // confirmation result is send back using a dedicated ril message
                // invoked by the CommandInterface call above.
                mCurrntCmd = null;
                return;
            }
            break;
        case NO_RESPONSE_FROM_USER:
        case UICC_SESSION_TERM_BY_USER:
        case BACKWARD_MOVE_BY_USER:
            resp = null;
            break;
        case TERMINAL_CRNTLY_UNABLE_TO_PROCESS:
            switch (AppInterface.CommandType.fromInt(cmdDet.typeOfCommand)) {
            case SET_UP_CALL:
                StkLog.d(this, "[stk] SET_UP_CALL set additionalInfo");
                AddInfo = true;
                additionalInfo = 2;  // ME currently busy on call;
            }
            break;
        default:
            return;
        }
        sendTerminalResponse(cmdDet, resMsg.resCode, AddInfo, additionalInfo, resp);
        mCurrntCmd = null;
    }

    private void handleRefreshCmdResponse(int result) {
        if (mCurrntCmd == null) {
            StkLog.d(this, "[stk]handleRefreshCmdResponse mCurrntCmd is NULL" );
            return;
        }
        CommandDetails cmdDet = mCurrntCmd.getCmdDet();

        switch (AppInterface.CommandType.fromInt(cmdDet.typeOfCommand)) {
        case REFRESH:
            ResultCode resCode = (0 == result) ? ResultCode.OK : ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS;
            sendTerminalResponse(cmdDet, resCode, false, 0, null);
            mCurrntCmd = null;
            break;
        default:
            StkLog.d(this, "[stk]handleRefreshCmdResponse CommandType is wrong" );
            return;
        }
    }
    //Deal With DTMF Message Start
    private void retrieveDtmfString(CommandParams cmdParams,String dtmf) {
        if(!isInCall()) {
            sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, true,
                    20, null);
        } else {
            String dtmfTemp = new String(dtmf);
            while(dtmfTemp.length() != 0) {
                String firstStr = dtmfTemp.substring(0,1);
        
                if(firstStr.equals("P")) {
                    
                    Message msg = new Message();
                    Bundle bundle = new Bundle();
            
                    bundle.putString("dtmf", dtmf.substring(1, dtmf.length()));
                    msg.what = MSG_ID_SEND_SECOND_DTMF;
                    msg.obj = cmdParams;
                    msg.setData(bundle);
            
                    this.sendMessageDelayed(msg, SEND_DTMF_INTERVAL);
                    return;
                }else {
                    mCmdIf.sendDtmf(firstStr.charAt(0),null);
                    dtmfTemp = dtmfTemp.substring(1,dtmfTemp.length());
                }
            }
            sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false,
                    0, null);
        }
    }

    private boolean isInCall() {
        final ITelephony phone = getPhoneInterface();
        if (phone == null) {
            return false;
        } try {
            return phone.isOffhook();
        } catch (RemoteException e) {
            return false;
        }
    }

    private ITelephony getPhoneInterface() {
        return ITelephony.Stub.asInterface(ServiceManager.checkService(Context.TELEPHONY_SERVICE));
    }
    //Deal With DTMF Message End
}
