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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Bundle;

import android.os.RemoteException;
import android.os.ServiceManager;
//Language Setting Add Start
import android.app.ActivityManager.RunningTaskInfo;
import android.app.backup.BackupManager;
import android.app.IActivityManager;
import android.content.res.Configuration;

import java.util.List;
import java.util.Locale;
import android.app.ActivityManagerNative;
import android.os.RemoteException;
//Language Setting Add End
import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.gsm.SimCard;
import com.android.internal.telephony.gsm.SIMFileHandler;
import com.android.internal.telephony.gsm.SIMRecords;

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
  NEXT_ACTION_INDICATOR(0x18),
  EVENT_LIST(0x19),
  ICON_ID(0x1e),
  ITEM_ICON_ID_LIST(0x1f),
  DATE_TIME_TIMEZONE(0x26),
  IMMEDIATE_RESPONSE(0x2b),
  //Deal With DTMF Message Start
  DTMF(0x2c),
  //Deal With DTMF Message End
  LANGUAGE(0x2d),
  URL(0x31),
  BROWSER_TERMINATION_CAUSE(0x34),
  BEARER_DESCRIPTION(0x35),
  CHANNEL_DATA(0x36),
  CHANNEL_DATA_LENGTH(0x37),
  CHANNEL_STATUS(0x38),
  BUFFER_SIZE(0x39),
  TRANSPORT_LEVEL(0x3c),
  OTHER_ADDRESS(0x3e),
  NETWORK_ACCESS_NAME(0x47),
  TEXT_ATTRIBUTE(0x50);

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
    private SIMRecords mSimRecords;

    // Service members.
    private static StkService sInstance;
    private CommandsInterface mCmdIf;
    private Context mContext;
    private StkCmdMessage mCurrntCmd = null;
    private StkCmdMessage mMenuCmd = null;

    private RilMessageDecoder mMsgDecoder = null;
    private int mPhoneId;
    private boolean mStkActive = false;

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
    //Deal With Serial Message Start
    private static final int MSG_ID_SEND_SERIAL_DTMF = 31;

    private static final int MSG_ID_EVENT_DOWNLOAD = 40;

    private static final int SEND_DTMF_INTERVAL = 2500;
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
    private Configuration mConfigBak = null;

    private String lastCmd = null;

    /* Intentionally private for singleton */
    private StkService(CommandsInterface ci, SIMRecords sr, Context context,
            SIMFileHandler fh, SimCard sc) {
        if (ci == null || sr == null || context == null || fh == null
                || sc == null) {
            throw new NullPointerException(
                    "Service: Input parameters must not be null");
        }
        mPhoneId = ci.getPhoneId();
        StkLog.d(this, "StkService: mPhoneId=" + mPhoneId);
        mCmdIf = ci;
        mContext = context;

        // Get the RilMessagesDecoder for decoding the messages.
        mMsgDecoder = new RilMessageDecoder(this, fh, mPhoneId);
        mMsgDecoder.start();

        // Register ril events handling.
        mCmdIf.setOnStkSessionEnd(this, MSG_ID_SESSION_END, null);
        mCmdIf.setOnStkProactiveCmd(this, MSG_ID_PROACTIVE_COMMAND, null);
        mCmdIf.setOnStkEvent(this, MSG_ID_EVENT_NOTIFY, null);
        mCmdIf.setOnStkCallSetUp(this, MSG_ID_CALL_SETUP, null);
        //mCmdIf.registerForSIMReady(this, MSG_ID_SIM_LOADED, null);
        mCmdIf.setOnStkStin(this, MSG_ID_REFRESH_STIN, null);
       //mCmdIf.setOnSimRefresh(this, MSG_ID_REFRESH, null);

        mSimRecords = sr;

        // Register for SIM ready event.
        //mSimRecords.registerForRecordsLoaded(this, MSG_ID_SIM_LOADED, null);

        mEventList = new boolean[EVENT_NUMBER];
        for (int i = 0; i < EVENT_NUMBER; i++) {
            mEventList[i] = false;
        }

        StkLog.d(this, "<" + mPhoneId + ">" + "[stk]active STK mStkActive = " + mStkActive);
        if (!mStkActive) {
            mStkActive = true;
            mCmdIf.reportStkServiceIsRunning(null);
        } else {
            StkLog.d(this, "<" + mPhoneId + ">" + "[stk]STK has been activated" );
        }

        StkLog.d(this, "<" + mPhoneId + ">" + "StkService: is running");
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
        StkLog.d(this,  "<" + mPhoneId + ">" + "Service finalized");
    }

    private void handleRilMsg(RilMessage rilMsg) {
        if (rilMsg == null) {
            StkLog.d(this,  "<" + mPhoneId + ">" + "rilMsg is null");
            return;
        }

        // dispatch messages
        CommandParams cmdParams = null;
        StkLog.d(this,  "<" + mPhoneId + ">" + "handleRilMsg rilMsg.mId = " + rilMsg.mId + " mResCode = " + rilMsg.mResCode );
        switch (rilMsg.mId) {
        case MSG_ID_EVENT_NOTIFY:
            if (rilMsg.mResCode == ResultCode.OK) {
                cmdParams = (CommandParams) rilMsg.mData;
                if (cmdParams != null) {
                    handleProactiveCommand(cmdParams);
                } else {
                    StkLog.d(this,  "<" + mPhoneId + ">" + "MSG_ID_EVENT_NOTIFY: cmdParams is null");
                }
            }
            break;
        case MSG_ID_PROACTIVE_COMMAND:
            cmdParams = (CommandParams) rilMsg.mData;
            if (cmdParams != null) {
                if (rilMsg.mResCode == ResultCode.OK ||
                    // ignore icon problem
                    rilMsg.mResCode == ResultCode.PRFRMD_ICON_NOT_DISPLAYED) {
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
            StkLog.d(this,  "<" + mPhoneId + ">" + "[stk] MSG_ID_CALL_SETUP rescode = "+rilMsg.mResCode);
            if (rilMsg.mResCode == ResultCode.OK) {
                cmdParams = (CommandParams) rilMsg.mData;
                if (cmdParams != null) {
                    handleProactiveCommand(cmdParams);
                } else {
                    StkLog.d(this,  "<" + mPhoneId + ">" + "[stk] cmdParams is NULL");
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
        StkLog.d(this,  "<" + mPhoneId + ">" + cmdParams.getCommandType().name());

        StkCmdMessage cmdMsg = new StkCmdMessage(cmdParams);
        DeviceIdentities deviceIdentities = null;
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
            if (cmdMsg.geTextMessage().isHighPriority == false) {
                StkLog.d(this,  "<" + mPhoneId + ">" + "[stk] DISPLAY_TEXT is normal Priority");
                boolean display_flag = isCurrentCanDisplayText();
                StkLog.d(this,  "<" + mPhoneId + ">" + "[stkapp]display_flag = " + display_flag);
                if (!display_flag) {
                    sendTerminalResponse(cmdParams.cmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS,
                                         true, AddinfoMeProblem.SCREEN_BUSY.value(), null);
                    return;
                }
            }
            break;
        case SET_UP_IDLE_MODE_TEXT:
            StkLog.d(this,  "<" + mPhoneId + ">" +
                    "icon = "                 + ((DisplayTextParams)cmdParams).textMsg.icon +
                    " iconSelfExplanatory = " + ((DisplayTextParams)cmdParams).textMsg.iconSelfExplanatory +
                    " text = "                + ((DisplayTextParams)cmdParams).textMsg.text);
            if(((DisplayTextParams)cmdParams).textMsg.icon != null
                    && ((DisplayTextParams)cmdParams).textMsg.iconSelfExplanatory == false
                    && ((DisplayTextParams)cmdParams).textMsg.text == null){
                sendTerminalResponse(cmdParams.cmdDet, ResultCode.CMD_DATA_NOT_UNDERSTOOD, false,
                        0, null);
                return;
            } else {
                sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false,
                        0, null);
            }
            break;
        //Deal With DTMF Message Start
        case SEND_DTMF:
            DtmfMessage dtmf = cmdMsg.getDtmfMessage();
            retrieveDtmfString(cmdParams,dtmf.mdtmfString);
            break;
        //Deal With DTMF Message End
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
        //Language Setting Add Start
        case LANGUAGE_NOTIFACTION:
            System.out.println("LANGUAGE_NOTIFACTION start");
            String language = cmdMsg.getLanguageMessage().languageString;
            String country = StkLanguageDecoder.getInstance().getCountryFromLanguage(language);
            try {
                IActivityManager am = ActivityManagerNative.getDefault();
                Configuration config = am.getConfiguration();
                if(country != null) {
                    //wangls
                    mConfigBak = new Configuration(config);
                    //wangsl
                    Locale locale = new Locale(language,country);
                    config.locale = locale;
                    config.userSetLocale = true;
                    StkLog.d(this,  "<" + mPhoneId + ">" + "LANGUAGE_NOTIFACTION country = " + country + " locale = " + locale);
                } else {
                    StkLog.d(this,  "<" + mPhoneId + ">" + "LANGUAGE_NOTIFACTION country is null");
                    if (mConfigBak != null) {
                        config = mConfigBak;
                        StkLog.d(this,  "<" + mPhoneId + ">" + "LANGUAGE_NOTIFACTION use backup config. locale = " + config.locale);
                    } else {
                        StkLog.d(this,  "<" + mPhoneId + ">" + "LANGUAGE_NOTIFACTION mConfigBak is null, do nothing");
                    }
                }
                am.updateConfiguration(config);
                BackupManager.dataChanged("com.android.providers.settings");
            }catch(RemoteException e) {
                StkLog.d(this,  "<" + mPhoneId + ">" + "LANGUAGE_NOTIFACTION exception: " + e);
                System.out.println("LANGUAGE_NOTIFACTION exception");
            }
            sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false, 0,
                    null);
        return;
        //Language Setting Add End
        case LAUNCH_BROWSER:
        case SELECT_ITEM:
        case GET_INPUT:
        case GET_INKEY:
        case SEND_SMS:
        case SEND_SS:
        case SEND_USSD:
        case PLAY_TONE:
        case SET_UP_CALL:
        case REFRESH:
        case OPEN_CHANNEL:
        case RECEIVE_DATA:
        case GET_CHANNEL_STATUS:
            // nothing to do on telephony!
            break;
        case SET_UP_EVENT_LIST:
            AppInterface.EventListType[] eventList = cmdMsg.getEventList();
            StkLog.d(this,  "<" + mPhoneId + ">" + "[stk] handleProactiveCommand: SET_UP_EVENT_LIST");

            for (int i = 0; i < EVENT_NUMBER; i++) {
                setEventEnabled(i, false);
            }
            if (eventList != null) {
                for (int i = 0; i < eventList.length; i++) {

                    if(eventList[i] != null){
                        setEventEnabled(eventList[i].value(), true);
                    }
                }
                IWindowManager wm = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));
                if(isValidEvent(AppInterface.EventListType.Event_UserActivity.value())) {
                    try {
                        wm.setEventUserActivityNeeded(true);
                    } catch (RemoteException e) {
                        StkLog.d(this, "<" + mPhoneId + ">" + "Exception when set EventDownloadNeeded flag in WindowManager");
                    } catch (NullPointerException e2) {
                          StkLog.d(this, "<" + mPhoneId + ">" + "wm is null");
                    }
                }
                if(isValidEvent(AppInterface.EventListType.Event_IdleScreenAvailable.value())) {
                    try {
                        wm.setEventIdleScreenNeeded(true);
                    } catch (RemoteException e) {
                        StkLog.d(this, "<" + mPhoneId + ">" + "Exception when set EventDownloadNeeded flag in WindowManager");
                    } catch (NullPointerException e2) {
                          StkLog.d(this, "<" + mPhoneId + ">" + "wm is null");
                    }
                }
            }
            sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false, 0, null);
            return;
        case CLOSE_CHANNEL:
            StkLog.d(this, "<" + mPhoneId + ">" + "handleProactiveCommand: CLOSE_CHANNEL");
            deviceIdentities = cmdMsg.getDeviceIdentities();
            if (deviceIdentities != null) {
                int channelId = deviceIdentities.destinationId & 0x0f;
                StkLog.d(this, "<" + mPhoneId + ">" + "CLOSE_CHANNEL channelId = " + channelId);
                if (channelId != AppInterface.DEFAULT_CHANNELID) {
                    StkLog.d(this, "<" + mPhoneId + ">" + "CLOSE_CHANNEL CHANNEL_ID_INVALID");
                    sendTerminalResponse(cmdParams.cmdDet, ResultCode.BIP_ERROR, true,
                            AddinfoBIPProblem.CHANNEL_ID_INVALID.value(), null);
                    return;
                }
            } else {
                StkLog.d(this, "<" + mPhoneId + ">" + "deviceIdentities is null, send CHANNEL_ID_INVALID");
                sendTerminalResponse(cmdParams.cmdDet, ResultCode.BIP_ERROR, true,
                        AddinfoBIPProblem.CHANNEL_ID_INVALID.value(), null);
                return;
            }
            break;
        case SEND_DATA:
            StkLog.d(this, "<" + mPhoneId + ">" + "handleProactiveCommand: SEND_DATA");
            deviceIdentities = cmdMsg.getDeviceIdentities();
            if (deviceIdentities != null) {
                int channelId = deviceIdentities.destinationId & 0x0f;
                StkLog.d(this, "<" + mPhoneId + ">" + "SEND_DATA channelId = " + channelId);
                if (channelId != AppInterface.DEFAULT_CHANNELID) {
                    sendTerminalResponse(cmdParams.cmdDet, ResultCode.BIP_ERROR, true,
                            AddinfoBIPProblem.CHANNEL_ID_INVALID.value(), null);
                    return;
                }
            } else {
                StkLog.d(this, "<" + mPhoneId + ">" + "deviceIdentities is null, do nothing");
            }
            break;
        default:
            StkLog.d(this, "<" + mPhoneId + ">" + "Unsupported command");
            return;
        }
        mCurrntCmd = cmdMsg;
        Intent intent = new Intent(AppInterface.STK_CMD_ACTION);
        intent.putExtra("STK CMD", cmdMsg);
        intent.putExtra("phone_id", mPhoneId);
        mContext.sendBroadcast(intent);
    }

    /**
     * Handles RIL_UNSOL_STK_SESSION_END unsolicited command from RIL.
     *
     */
    private void handleSessionEnd() {
        StkLog.d(this, "<" + mPhoneId + ">" + "SESSION END");

        mCurrntCmd = mMenuCmd;
        Intent intent = new Intent(AppInterface.STK_SESSION_END_ACTION);
        intent.putExtra("phone_id", mPhoneId);
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
            StkLog.d(this, "<" + mPhoneId + ">" + "TERMINAL RESPONSE: " + hexString);
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
            HandlerThread thread = new HandlerThread("Stk Telephony service");
            thread.start();
            sInstance = new StkService(ci, sr, context, fh, sc);
            StkLog.d(sInstance, "NEW sInstance");

        return sInstance;
    }

    public void update(CommandsInterface ci) {
//    	if ((sr != null) && (mSimRecords != sr)) {
        if ((ci != null) && (mCmdIf != ci)) {
            StkLog.d(sInstance, "<" + mPhoneId + ">" + "Reinitialize the Service with SIMRecords");
//            mSimRecords = sr;
//            mSimRecords.registerForRecordsLoaded(sInstance, MSG_ID_SIM_LOADED, null);
            mCmdIf = ci;
            //mCmdIf.registerForSIMReady(sInstance, MSG_ID_SIM_LOADED, null);
            StkLog.d(this, "<" + mPhoneId + ">" + "[stk]active STK mStkActive = " + mStkActive);
            if (!mStkActive) {
                mStkActive = true;
                mCmdIf.reportStkServiceIsRunning(null);
            } else {
                StkLog.d(this, "<" + mPhoneId + ">" + "[stk]STK has been activated" );
            }
            StkLog.d(sInstance, "<" + mPhoneId + ">" + "sr changed reinitialize and return current sInstance");

    	}
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
            StkLog.d(this, "<" + mPhoneId + ">" + "[stk]ril message arrived = " + msg.what);
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
            // ignore invalid duplicate commands, NEWMS00210093
            if (lastCmd != null && data != null && lastCmd.equals(data)) {
                StkLog.d(this, "<" + mPhoneId + ">" + "duplicate command, ignored !!");
                break;
            }
            if (data != null) {
                lastCmd = new String(data);
            } else {
                lastCmd = null;
            }

            mMsgDecoder.sendStartDecodingMessageParams(new RilMessage(msg.what, data));
            break;
//        case MSG_ID_CALL_SETUP:
//            mMsgDecoder.sendStartDecodingMessageParams(new RilMessage(msg.what, null));
//            break;
        case MSG_ID_SIM_LOADED:
            StkLog.d(this, "<" + mPhoneId + ">" + "[stk]active STK mStkActive = " + mStkActive);
            if (!mStkActive) {
                mStkActive = true;
                mCmdIf.reportStkServiceIsRunning(null);
            } else {
                StkLog.d(this, "<" + mPhoneId + ">" + "[stk]STK has been activated" );
            }
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
			break;
        //CR120717 Modify Start
        case MSG_ID_SEND_SERIAL_DTMF:
           AsyncResult dtmfAr = (AsyncResult) msg.obj;

           Message msgAr = (Message)dtmfAr.userObj;
           CommandParams cmdParamAr = (CommandParams)msgAr.obj;
           String arStr = msgAr.getData().getString("dtmf");

           retrieveDtmfString(cmdParamAr,arStr);
           break;
        //CR120717 Modify end
        case MSG_ID_REFRESH_STIN:
            StkLog.d(this, "<" + mPhoneId + ">" + "[stk]MSG_ID_REFRESH_STIN" );
            int result = 1;
            if (msg.obj != null) {
                AsyncResult ar = (AsyncResult) msg.obj;
                if (ar != null && ar.result != null) {
                    int[] params = (int[])ar.result;
                    result = params[0];
                    StkLog.d(this, "<" + mPhoneId + ">" + "[stk]MSG_ID_REFRESH_STIN result = " + result);
                    if (0 == result) {
                        mSimRecords.onRefresh(true, null);
                    }
                    handleRefreshCmdResponse(result);
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
            StkLog.d(this, "<" + mPhoneId + ">" + "[stk] validateResponse mCurrntCmd is null");
        }
        return false;
    }

    private boolean removeMenu(Menu menu) {
        try {
            if (menu.items.size() == 1 && menu.items.get(0) == null) {
                return true;
            }
        } catch (NullPointerException e) {
            StkLog.d(this, "<" + mPhoneId + ">" + "Unable to get Menu's items size");
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
            StkLog.d(this, "<" + mPhoneId + ">" + "handleEventDownload is inValid Event");
            return;
        }

        StkLog.d(this, "<" + mPhoneId + ">" + "handleEventDownload event = " + type);
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
                try {
                    wm.setEventUserActivityNeeded(false);
                } catch (RemoteException e) {
                    StkLog.d(this, "<" + mPhoneId + ">" + "Exception when set EventDownloadNeeded flag in WindowManager");
                } catch (NullPointerException e2) {
                    StkLog.d(this, "<" + mPhoneId + ">" + "wm is null");
                }
                break;
            case Event_IdleScreenAvailable:
                oneShot = true;
                sourceId = DEV_ID_DISPLAY;
                try {
                    wm.setEventIdleScreenNeeded(false);
                } catch (RemoteException e) {
                    StkLog.d(this, "<" + mPhoneId + ">" + "Exception when set EventDownloadNeeded flag in WindowManager");
                } catch (NullPointerException e2) {
                    StkLog.d(this, "<" + mPhoneId + ">" + "wm is null");
                }
                break;
            case Event_BrowserTermination:
                tag = 0x80 | ComprehensionTlvTag.BROWSER_TERMINATION_CAUSE.value();
                buf.write(tag); // tag
                buf.write(1); // length
                buf.write(BROWER_TERMINATION_USER);
                additionalInfo = buf.toByteArray();
                break;
            case Event_DataAvailable:
                tag = 0x80 | ComprehensionTlvTag.CHANNEL_STATUS.value();
                buf.write(tag); // tag
                buf.write(2); // length
                buf.write(resMsg.ChannelId | (resMsg.LinkStatus ? 0x80 : 0));
                buf.write(resMsg.mMode);
                tag = 0x80 | ComprehensionTlvTag.CHANNEL_DATA_LENGTH.value();
                buf.write(tag);
                buf.write(1);
                buf.write(resMsg.channelDataLen);
                additionalInfo = buf.toByteArray();
                break;
            case Event_ChannelStatus:
                tag = 0x80 | ComprehensionTlvTag.CHANNEL_STATUS.value();
                buf.write(tag); // tag
                buf.write(2); // length
                buf.write(resMsg.ChannelId | (resMsg.LinkStatus ? 0x80 : 0));
                buf.write(resMsg.mMode);
                additionalInfo = buf.toByteArray();
                break;
            default:
                StkLog.d(this, "<" + mPhoneId + ">" + "unknown event");
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
            StkLog.d(this, "<" + mPhoneId + ">" + "[stk] validateResponse fail!! ");
            if (mCurrntCmd != null) {
                StkLog.d(this, "<" + mPhoneId + ">" + "[stk] mCurrntCmd = " +
                         AppInterface.CommandType.fromInt(mCurrntCmd.mCmdDet.typeOfCommand));
            }
            if (resMsg != null) {
                AppInterface.CommandType type = AppInterface.CommandType.fromInt(resMsg.cmdDet.typeOfCommand);
                StkLog.d(this, "<" + mPhoneId + ">" + "[stk] resMsg cmd = " + type);
                if (mCurrntCmd != null) {
                    AppInterface.CommandType curType = AppInterface.CommandType.fromInt(mCurrntCmd.mCmdDet.typeOfCommand);
                    if (curType == AppInterface.CommandType.SET_UP_MENU &&
                           type == AppInterface.CommandType.DISPLAY_TEXT) {
                        StkLog.d(this, "<" + mPhoneId + ">" + "[stk] ignore display_text cmd check!! ");
                    } else {
                        StkLog.d(this, "<" + mPhoneId + ">" + "[stk] validateResponse fail, return! ");
                        return;
                    }
                } else if (type == AppInterface.CommandType.SET_UP_MENU && mCurrntCmd == null) {
                    StkLog.d(this, "<" + mPhoneId + ">" + "Warning: force mCurrntCmd to mMenuCmd!!");
                    mCurrntCmd = mMenuCmd;
                    return;
                }
            } else {
                StkLog.d(this, "<" + mPhoneId + ">" + "[stk] resMsg is null, Return!! ");
                return;
            }
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
                if(mCurrntCmd == null) {
                    StkLog.d(this, "mCurrntCmd is null");
                    return;
                }
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
            case OPEN_CHANNEL:
                StkLog.d(this, "<" + mPhoneId + ">" + "OPEN_CHANNEL RES OK");
                resp = new OpenChannelResponseData(resMsg.BearerType, resMsg.BearerParam,
                        resMsg.bufferSize, resMsg.ChannelId, resMsg.LinkStatus);
                break;
            case SEND_DATA:
                StkLog.d(this, "<" + mPhoneId + ">" + "SEND_DATA RES OK");
                resp = new SendDataResponseData(resMsg.channelDataLen);
                break;
            case RECEIVE_DATA:
                StkLog.d(this, "<" + mPhoneId + ">" + "RECEIVE_DATA RES OK");
                resp = new ReceiveDataResponseData(resMsg.channelDataLen, resMsg.channelData);
                break;
            case GET_CHANNEL_STATUS:
                StkLog.d(this, "<" + mPhoneId + ">" + "GET_CHANNEL_STATUS RES OK");
                resp = new ChannelStatusResponseData(resMsg.ChannelId, resMsg.LinkStatus);
                break;
            case SET_UP_CALL:
                StkLog.d(this, "<" + mPhoneId + ">" + "[stk] handleCmdResponse MSG_ID_CALL_SETUP");
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
                StkLog.d(this, "<" + mPhoneId + ">" + "SET_UP_CALL TERMINAL_CRNTLY_UNABLE_TO_PROCESS");
                AddInfo = true;
                additionalInfo = AddinfoMeProblem.BUSY_ON_CALL.value();
                break;
            case OPEN_CHANNEL:
                StkLog.d(this, "<" + mPhoneId + ">" + "OPEN_CHANNEL TERMINAL_CRNTLY_UNABLE_TO_PROCESS");
                AddInfo = true;
                additionalInfo = AddinfoMeProblem.BUSY_ON_CALL.value();
                resp = new OpenChannelResponseData(resMsg.BearerType, resMsg.BearerParam,
                        resMsg.bufferSize, resMsg.ChannelId, resMsg.LinkStatus);
                break;
            }
            break;
        case NETWORK_CRNTLY_UNABLE_TO_PROCESS:
            switch (AppInterface.CommandType.fromInt(cmdDet.typeOfCommand)) {
            case SET_UP_CALL:
                StkLog.d(this, "<" + mPhoneId + ">" + "[stk] SET_UP_CALL NETWORK_CRNTLY_UNABLE_TO_PROCESS");
            }
            break;
        case BEYOND_TERMINAL_CAPABILITY:
            switch (AppInterface.CommandType.fromInt(cmdDet.typeOfCommand)) {
            case OPEN_CHANNEL:
                StkLog.d(this, "<" + mPhoneId + ">" + "OPEN_CHANNEL BEYOND_TERMINAL_CAPABILITY");
                resp = new OpenChannelResponseData(resMsg.BearerType, resMsg.BearerParam,
                        resMsg.bufferSize, resMsg.ChannelId, resMsg.LinkStatus);
                break;
            case SEND_DATA:
                StkLog.d(this, "<" + mPhoneId + ">" + "SEND_DATA BEYOND_TERMINAL_CAPABILITY");
                AddInfo = true;
                additionalInfo = AddinfoBIPProblem.TRANSPORT_LEVEL_NOT_AVAILABLE.value();
                break;
            case RECEIVE_DATA:
                StkLog.d(this, "<" + mPhoneId + ">" + "RECEIVE_DATA BEYOND_TERMINAL_CAPABILITY");
                AddInfo = true;
                additionalInfo = AddinfoBIPProblem.NO_SPECIFIC_CAUSE.value();
                break;
            }
            break;
        case BIP_ERROR:
            switch (AppInterface.CommandType.fromInt(cmdDet.typeOfCommand)) {
            case SEND_DATA:
                StkLog.d(this, "<" + mPhoneId + ">" + "SEND_DATA BIP_ERROR");
                AddInfo = true;
                additionalInfo = AddinfoBIPProblem.CHANNEL_ID_INVALID.value();
                break;
            case CLOSE_CHANNEL:
                StkLog.d(this, "<" + mPhoneId + ">" + "CLOSE_CHANNEL BIP_ERROR");
                AddInfo = true;
                additionalInfo = AddinfoBIPProblem.CHANNEL_CLOSED.value();
                break;
            }
            break;
        case USER_NOT_ACCEPT:
            switch (AppInterface.CommandType.fromInt(cmdDet.typeOfCommand)) {
            case OPEN_CHANNEL:
                StkLog.d(this, "<" + mPhoneId + ">" + "OPEN_CHANNEL USER_NOT_ACCEPT");
                resp = new OpenChannelResponseData(resMsg.BearerType, resMsg.BearerParam,
                        resMsg.bufferSize, resMsg.ChannelId, resMsg.LinkStatus);
                break;
            }
            break;
        default:
            return;
        }
        sendTerminalResponse(cmdDet, resMsg.resCode, AddInfo, additionalInfo, resp);
        mCurrntCmd = null;
    }

    //Deal With DTMF Message Start
    private void retrieveDtmfString(CommandParams cmdParams,String dtmf) {
        if(!isInCall()) {
            //CR120728 Modify Start
            sendTerminalResponse(cmdParams.cmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS, true,
                    7, null);
            //CR120728 Modify End
        } else {
            String dtmfTemp = new String(dtmf);
            if(dtmfTemp != null && dtmfTemp.length() > 0) {
                String firstStr = dtmfTemp.substring(0,1);
       
                Message msg = new Message();
                Bundle bundle = new Bundle();
                
                bundle.putString("dtmf", dtmf.substring(1, dtmf.length()));
                msg.what = MSG_ID_SEND_SECOND_DTMF;
                msg.obj = cmdParams;
                msg.setData(bundle);
                if(firstStr.equals("P")) {
                    this.sendMessageDelayed(msg, SEND_DTMF_INTERVAL);
                    return;
                }else {
                    //CR120717 Modify Start
                    mCmdIf.sendDtmf(firstStr.charAt(0),obtainMessage(
                        MSG_ID_SEND_SERIAL_DTMF, msg));
                    //dtmfTemp = dtmfTemp.substring(1,dtmfTemp.length());
                    //CR120717 Modify End
                }
            }else {
                sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false,
                    0, null);
            }
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
        return ITelephony.Stub.asInterface(ServiceManager.checkService(PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE, mPhoneId)));
    }
    //Deal With DTMF Message End

    private void handleRefreshCmdResponse(int result) {
        if (mCurrntCmd == null) {
            StkLog.d(this, "<" + mPhoneId + ">" + "[stk]handleRefreshCmdResponse mCurrntCmd is NULL" );
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
            StkLog.d(this, "<" + mPhoneId + ">" + "[stk]handleRefreshCmdResponse CommandType is wrong" );
            return;
        }
    }

    private boolean isInIdleScreen() {
        boolean ret = false;
        IWindowManager wm = getWindowInterface();
        try {
            ret = wm.isInIdleScreen();
        } catch (RemoteException e) {
            // no fallback; do nothing.
        }
        return ret;
    }

    private boolean isCurrentCanDisplayText() {
        try {
            List<RunningTaskInfo> mRunningTaskInfoList = (List<RunningTaskInfo>)ActivityManagerNative.getDefault().getTasks(1, 0, null);
            int mListSize = mRunningTaskInfoList.size();
            StkLog.d(this, "<" + mPhoneId + ">" + "[stk]isCurrentCanDisplayText trace mListSize = " + mListSize);
            if(mListSize > 0) {
                ComponentName cn = mRunningTaskInfoList.get(0).topActivity;
                StkLog.d(this, "<" + mPhoneId + ">" + "[stk]isCurrentCanDisplayText cn is " + cn);
                boolean result = ((cn.getClassName().indexOf("com.android.stk") != -1))
                        || (cn.getClassName().equals("com.android.launcher2.Launcher"))
                        || isInIdleScreen();
                return result;
            }
        } catch (RemoteException e) {
            StkLog.d(this, "<" + mPhoneId + ">" + "[stk]isCurrentCanDisplayText exception");
        }
        return false;
    }
}
