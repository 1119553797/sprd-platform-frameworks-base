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

package com.android.internal.telephony.cat;

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
//Language Setting Add End
import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccFileHandler;
import com.android.internal.telephony.IccRecords;

import com.android.internal.telephony.ITelephony;

import android.util.Config;
import android.view.IWindowManager;

import java.io.ByteArrayOutputStream;

import android.os.SystemProperties;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;

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
public class CatService extends Handler implements AppInterface {

    // Class members
    private IccRecords mIccRecords;

    // Service members.
    // Protects singleton instance lazy initialization.
    //private static final Object sInstanceLock = new Object();
    private static CatService sInstance;
    private CommandsInterface mCmdIf;
    private Context mContext;
    private CatCmdMessage mCurrntCmd = null;
    private CatCmdMessage mMenuCmd = null;

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
    static final int MSG_ID_SIM_READY                = 7;
    // Use RIL_UNSOL_SIM_REFRESH instead
    // Add for send session end. NEWMS00205430
    static final int MSG_ID_REFRESH_STIN             = 8;

    static final int MSG_ID_RIL_MSG_DECODED          = 10;

    // Events to signal SIM presence or absent in the device.
    private static final int MSG_ID_ICC_RECORDS_LOADED       = 20;

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

    static final String STK_DEFAULT = "Defualt Message";

    private static final int EVENT_NUMBER = 11;
    private boolean[] mEventList = null;
    private Configuration mConfigBak = null;

    private String lastCmd = null;

    //save setup menu data
    private static final String SETUP_MEMU_PREFIX = "ril.setupmenu.sim";
    private static final String SETUP_MEMU_DATA_LEN = "_len";
    private static final String SETUP_MEMU_DATA = "_dat";
    private static int mSetupMenuFlag = 0;    

    /* Intentionally private for singleton */
    private CatService(CommandsInterface ci, IccRecords ir, Context context,
            IccFileHandler fh, IccCard ic) {
        if (ci == null || ir == null || context == null || fh == null
                || ic == null) {
            throw new NullPointerException(
                    "Service: Input parameters must not be null");
        }
        mPhoneId = ci.getPhoneId();
        CatLog.d(this, "StkService: mPhoneId=" + mPhoneId);
        mCmdIf = ci;
        mContext = context;

        // Get the RilMessagesDecoder for decoding the messages.
        mMsgDecoder = new RilMessageDecoder(this, fh, mPhoneId);
        mMsgDecoder.start();

        // Register ril events handling.
        mCmdIf.setOnCatSessionEnd(this, MSG_ID_SESSION_END, null);
        mCmdIf.setOnCatProactiveCmd(this, MSG_ID_PROACTIVE_COMMAND, null);
        mCmdIf.setOnCatEvent(this, MSG_ID_EVENT_NOTIFY, null);
        mCmdIf.setOnCatCallSetUp(this, MSG_ID_CALL_SETUP, null);
        // Use RIL_UNSOL_SIM_REFRESH instead
        //mCmdIf.setOnStkStin(this, MSG_ID_REFRESH_STIN, null);
        //mCmdIf.setOnSimRefresh(this, MSG_ID_REFRESH, null);
        // Add for send session end. NEWMS00205430
        mCmdIf.registerForIccRefresh(this, MSG_ID_REFRESH_STIN, null);

        mIccRecords = ir;

        // Register for SIM ready event.
        //mCmdIf.registerForSIMReady(this, MSG_ID_SIM_READY, null);
        //mCmdIf.registerForRUIMReady(this, MSG_ID_SIM_READY, null);
        //mCmdIf.registerForNVReady(this, MSG_ID_SIM_READY, null);
        mIccRecords.registerForRecordsLoaded(this, MSG_ID_ICC_RECORDS_LOADED, null);

        mEventList = new boolean[EVENT_NUMBER];
        for (int i = 0; i < EVENT_NUMBER; i++) {
            mEventList[i] = false;
        }

        CatLog.d(this, "<" + mPhoneId + ">" + "[stk]active STK mStkActive = " + mStkActive);
        if (!mStkActive) {
            mStkActive = true;
            mCmdIf.reportStkServiceIsRunning(null);
        } else {
            CatLog.d(this, "<" + mPhoneId + ">" + "[stk]STK has been activated" );
        }

        CatLog.d(this, "<" + mPhoneId + ">" + "StkService: is running");
        if(SystemProperties.get(SETUP_MEMU_PREFIX+mPhoneId,"0").equals("1")){
            String data = getSetupMenuData();

            if(data != null){
                Message msg = new Message();
                AsyncResult ar = new AsyncResult(null,data,null);

                msg.what = MSG_ID_PROACTIVE_COMMAND;
                msg.obj = ar;
                this.sendMessage(msg);
            }
            mSetupMenuFlag |= (1<<mPhoneId);
        }

    }

    public void dispose() {
        mIccRecords.unregisterForRecordsLoaded(this);
        mCmdIf.unSetOnCatSessionEnd(this);
        mCmdIf.unSetOnCatProactiveCmd(this);
        mCmdIf.unSetOnCatEvent(this);
        mCmdIf.unSetOnCatCallSetUp(this);

        this.removeCallbacksAndMessages(null);
    }

    protected void finalize() {
        CatLog.d(this,  "<" + mPhoneId + ">" + "Service finalized");
    }

    private void handleRilMsg(RilMessage rilMsg) {
        if (rilMsg == null) {
            CatLog.d(this,  "<" + mPhoneId + ">" + "rilMsg is null");
            return;
        }

        // dispatch messages
        CommandParams cmdParams = null;
        CatLog.d(this,  "<" + mPhoneId + ">" + "handleRilMsg rilMsg.mId = " + rilMsg.mId + " mResCode = " + rilMsg.mResCode );
        switch (rilMsg.mId) {
        case MSG_ID_EVENT_NOTIFY:
            if (rilMsg.mResCode == ResultCode.OK) {
                cmdParams = (CommandParams) rilMsg.mData;
                if (cmdParams != null) {
                    handleProactiveCommand(cmdParams);
                } else {
                    CatLog.d(this,  "<" + mPhoneId + ">" + "MSG_ID_EVENT_NOTIFY: cmdParams is null");
                }
            }
            break;
        case MSG_ID_PROACTIVE_COMMAND:
            try {
                cmdParams = (CommandParams) rilMsg.mData;
            } catch (ClassCastException e) {
                // for error handling : cast exception
                CatLog.d(this, "Fail to parse proactive command");
                sendTerminalResponse(mCurrntCmd.mCmdDet, ResultCode.CMD_DATA_NOT_UNDERSTOOD,
                                     false, 0x00, null);
                break;
            }
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
            CatLog.d(this,  "<" + mPhoneId + ">" + "[stk] MSG_ID_CALL_SETUP rescode = "+rilMsg.mResCode);
            if (rilMsg.mResCode == ResultCode.OK) {
                cmdParams = (CommandParams) rilMsg.mData;
                if (cmdParams != null) {
                    handleProactiveCommand(cmdParams);
                } else {
                    CatLog.d(this,  "<" + mPhoneId + ">" + "[stk] cmdParams is NULL");
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
        CatLog.d(this,  "<" + mPhoneId + ">" + cmdParams.getCommandType().name());

        CatCmdMessage cmdMsg = new CatCmdMessage(cmdParams);
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
                CatLog.d(this,  "<" + mPhoneId + ">" + "[stk] DISPLAY_TEXT is normal Priority");
                boolean display_flag = isCurrentCanDisplayText();
                CatLog.d(this,  "<" + mPhoneId + ">" + "[stkapp]display_flag = " + display_flag);
                if (!display_flag) {
                    sendTerminalResponse(cmdParams.cmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS,
                                         true, AddinfoMeProblem.SCREEN_BUSY.value(), null);
                    return;
                }
            }
            break;
        case SET_UP_IDLE_MODE_TEXT:
            CatLog.d(this,  "<" + mPhoneId + ">" +
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
                    CatLog.d(this,  "<" + mPhoneId + ">" + "LANGUAGE_NOTIFACTION country = " + country + " locale = " + locale);
                } else {
                    CatLog.d(this,  "<" + mPhoneId + ">" + "LANGUAGE_NOTIFACTION country is null");
                    if (mConfigBak != null) {
                        config = mConfigBak;
                        CatLog.d(this,  "<" + mPhoneId + ">" + "LANGUAGE_NOTIFACTION use backup config. locale = " + config.locale);
                    } else {
                        CatLog.d(this,  "<" + mPhoneId + ">" + "LANGUAGE_NOTIFACTION mConfigBak is null, do nothing");
                    }
                }
                am.updateConfiguration(config);
                BackupManager.dataChanged("com.android.providers.settings");
            }catch(RemoteException e) {
                CatLog.d(this,  "<" + mPhoneId + ">" + "LANGUAGE_NOTIFACTION exception: " + e);
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
            CatLog.d(this,  "<" + mPhoneId + ">" + "[stk] handleProactiveCommand: SET_UP_EVENT_LIST");

            for (int i = 0; i < EVENT_NUMBER; i++) {
                setEventEnabled(i, false);
            }
            if (eventList != null) {
                for (int i = 0; i < eventList.length; i++) {
                    setEventEnabled(eventList[i].value(), true);
                }
                IWindowManager wm = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));
                if(isValidEvent(AppInterface.EventListType.Event_UserActivity.value())) {
                  /*  try {
                        wm.setEventUserActivityNeeded(true);
                    } catch (RemoteException e) {
                        CatLog.d(this, "<" + mPhoneId + ">" + "Exception when set EventDownloadNeeded flag in WindowManager");
                    } catch (NullPointerException e2) {
                          CatLog.d(this, "<" + mPhoneId + ">" + "wm is null");
                    }*/
                }
                if(isValidEvent(AppInterface.EventListType.Event_IdleScreenAvailable.value())) {
                   /* try {
                        wm.setEventIdleScreenNeeded(true);
                    } catch (RemoteException e) {
                        CatLog.d(this, "<" + mPhoneId + ">" + "Exception when set EventDownloadNeeded flag in WindowManager");
                    } catch (NullPointerException e2) {
                          CatLog.d(this, "<" + mPhoneId + ">" + "wm is null");
                    }*/
                }
            }
            sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false, 0, null);
            return;
        case CLOSE_CHANNEL:
            CatLog.d(this, "<" + mPhoneId + ">" + "handleProactiveCommand: CLOSE_CHANNEL");
            deviceIdentities = cmdMsg.getDeviceIdentities();
            if (deviceIdentities != null) {
                int channelId = deviceIdentities.destinationId & 0x0f;
                CatLog.d(this, "<" + mPhoneId + ">" + "CLOSE_CHANNEL channelId = " + channelId);
                if (channelId != AppInterface.DEFAULT_CHANNELID) {
                    CatLog.d(this, "<" + mPhoneId + ">" + "CLOSE_CHANNEL CHANNEL_ID_INVALID");
                    sendTerminalResponse(cmdParams.cmdDet, ResultCode.BIP_ERROR, true,
                            AddinfoBIPProblem.CHANNEL_ID_INVALID.value(), null);
                    return;
                }
            } else {
                CatLog.d(this, "<" + mPhoneId + ">" + "deviceIdentities is null, send CHANNEL_ID_INVALID");
                sendTerminalResponse(cmdParams.cmdDet, ResultCode.BIP_ERROR, true,
                        AddinfoBIPProblem.CHANNEL_ID_INVALID.value(), null);
                return;
            }
            break;
        case SEND_DATA:
            CatLog.d(this, "<" + mPhoneId + ">" + "handleProactiveCommand: SEND_DATA");
            deviceIdentities = cmdMsg.getDeviceIdentities();
            if (deviceIdentities != null) {
                int channelId = deviceIdentities.destinationId & 0x0f;
                CatLog.d(this, "<" + mPhoneId + ">" + "SEND_DATA channelId = " + channelId);
                if (channelId != AppInterface.DEFAULT_CHANNELID) {
                    sendTerminalResponse(cmdParams.cmdDet, ResultCode.BIP_ERROR, true,
                            AddinfoBIPProblem.CHANNEL_ID_INVALID.value(), null);
                    return;
                }
            } else {
                CatLog.d(this, "<" + mPhoneId + ">" + "deviceIdentities is null, do nothing");
            }
            break;
        default:
            CatLog.d(this, "<" + mPhoneId + ">" + "Unsupported command");
            return;
        }
        mCurrntCmd = cmdMsg;
        Intent intent = new Intent(AppInterface.CAT_CMD_ACTION);
        intent.putExtra("STK CMD", cmdMsg);
        intent.putExtra("phone_id", mPhoneId);
        mContext.sendBroadcast(intent);
        if (cmdParams.getCommandType() == AppInterface.CommandType.SEND_USSD ||
             cmdParams.getCommandType() == AppInterface.CommandType.SEND_SS ||
             cmdParams.getCommandType() == AppInterface.CommandType.SEND_SMS) {
        	mCurrntCmd = null;
        }
    }

    /**
     * Handles RIL_UNSOL_STK_SESSION_END unsolicited command from RIL.
     *
     */
    private void handleSessionEnd() {
        CatLog.d(this, "<" + mPhoneId + ">" + "SESSION END");

        mCurrntCmd = mMenuCmd;
        Intent intent = new Intent(AppInterface.CAT_SESSION_END_ACTION);
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

        Input cmdInput = null;
        if (mCurrntCmd != null) {
            cmdInput = mCurrntCmd.geInput();
        }

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
        // According to TS102.223/TS31.111 section 6.8 Structure of
        // TERMINAL RESPONSE, "For all SIMPLE-TLV objects with Min=N,
        // the ME should set the CR(comprehension required) flag to
        // comprehension not required.(CR=0)"
        // Since DEVICE_IDENTITIES and DURATION TLVs have Min=N,
        // the CR flag is not set.
        tag = ComprehensionTlvTag.DEVICE_IDENTITIES.value();
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
        } else {
            encodeOptionalTags(cmdDet, resultCode, cmdInput, buf);
        }

        byte[] rawData = buf.toByteArray();
        String hexString = IccUtils.bytesToHexString(rawData);
        if (Config.LOGD) {
            CatLog.d(this, "<" + mPhoneId + ">" + "TERMINAL RESPONSE: " + hexString);
        }

        mCmdIf.sendTerminalResponse(hexString, null);
        RemoveLastCmd();
    }

    private void RemoveLastCmd() {
        // Add Start for bug 77920
        if (lastCmd != null) {
            CatLog.d(this, "<" + mPhoneId + ">" + "handleCmdResponse:remove lastCmd");
            lastCmd = null;
        }
        // Add End for bug 77920
    }

    private void encodeOptionalTags(CommandDetails cmdDet,
            ResultCode resultCode, Input cmdInput, ByteArrayOutputStream buf) {
        CommandType cmdType = AppInterface.CommandType.fromInt(cmdDet.typeOfCommand);
        if (cmdType != null) {
            switch (cmdType) {
                case GET_INKEY:
                    // ETSI TS 102 384,27.22.4.2.8.4.2.
                    // If it is a response for GET_INKEY command and the response timeout
                    // occured, then add DURATION TLV for variable timeout case.
                    if ((resultCode.value() == ResultCode.NO_RESPONSE_FROM_USER.value()) &&
                        (cmdInput != null) && (cmdInput.duration != null)) {
                        getInKeyResponse(buf, cmdInput);
                    }
                    break;
//                case PROVIDE_LOCAL_INFORMATION:
//                    if ((cmdDet.commandQualifier == CommandParamsFactory.LANGUAGE_SETTING) &&
//                        (resultCode.value() == ResultCode.OK.value())) {
//                        getPliResponse(buf);
//                    }
//                    break;
                default:
                    CatLog.d(this, "encodeOptionalTags() Unsupported Cmd:" + cmdDet.typeOfCommand);
                    break;
            }
        } else {
            CatLog.d(this, "encodeOptionalTags() bad Cmd:" + cmdDet.typeOfCommand);
        }
    }

    private void getInKeyResponse(ByteArrayOutputStream buf, Input cmdInput) {
        int tag = ComprehensionTlvTag.DURATION.value();

        buf.write(tag);
        buf.write(0x02); // length
        buf.write(cmdInput.duration.timeUnit.SECOND.value()); // Time (Unit,Seconds)
        buf.write(cmdInput.duration.timeInterval); // Time Duration
    }

    private void getPliResponse(ByteArrayOutputStream buf) {

//        // Locale Language Setting
//        String lang = SystemProperties.get("persist.sys.language");
//
//        if (lang != null) {
//            // tag
//            int tag = ComprehensionTlvTag.LANGUAGE.value();
//            buf.write(tag);
//            ResponseData.writeLength(buf, lang.length());
//            buf.write(lang.getBytes(), 0, lang.length());
//        }
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
     * Used for instantiating/updating the Service from the GsmPhone or CdmaPhone constructor.
     *
     * @param ci CommandsInterface object
     * @param ir IccRecords object
     * @param context phone app context
     * @param fh Icc file handler
     * @param ic Icc card
     * @return The only Service object in the system
     */
    public static CatService getInstance(CommandsInterface ci, IccRecords ir,
            Context context, IccFileHandler fh, IccCard ic) {
//        synchronized (sInstanceLock) {
//            if (sInstance == null) {
//                if (ci == null || ir == null || context == null || fh == null
//                        || ic == null) {
//                    return null;
//                }
//                HandlerThread thread = new HandlerThread("Cat Telephony service");
//                thread.start();
//                sInstance = new CatService(ci, ir, context, fh, ic);
//                CatLog.d(sInstance, "NEW sInstance");
//            } else if ((ir != null) && (mIccRecords != ir)) {
//                CatLog.d(sInstance, "Reinitialize the Service with SIMRecords");
//                mIccRecords = ir;
//
//                // re-Register for SIM ready event.
//                mIccRecords.registerForRecordsLoaded(sInstance, MSG_ID_ICC_RECORDS_LOADED, null);
//                CatLog.d(sInstance, "sr changed reinitialize and return current sInstance");
//            } else {
//                CatLog.d(sInstance, "Return current sInstance");
//            }
//            return sInstance;
//        }
        HandlerThread thread = new HandlerThread("Stk Telephony service");
        thread.start();
        sInstance = new CatService(ci, ir, context, fh, ic);
        CatLog.d(sInstance, "NEW sInstance");
        return sInstance;
    }

    public void update(CommandsInterface ci) {
//    	if ((sr != null) && (mSimRecords != sr)) {
        if ((ci != null) && (mCmdIf != ci)) {
            CatLog.d(sInstance, "<" + mPhoneId + ">" + "Reinitialize the Service with SIMRecords");
//            mSimRecords = sr;
//            mSimRecords.registerForRecordsLoaded(sInstance, MSG_ID_SIM_LOADED, null);
            mCmdIf = ci;
            //mCmdIf.registerForSIMReady(sInstance, MSG_ID_SIM_READY, null);
            CatLog.d(this, "<" + mPhoneId + ">" + "[stk]active STK mStkActive = " + mStkActive);
            if (!mStkActive) {
                mStkActive = true;
                mCmdIf.reportStkServiceIsRunning(null);
            } else {
                CatLog.d(this, "<" + mPhoneId + ">" + "[stk]STK has been activated" );
            }

            CatLog.d(sInstance, "<" + mPhoneId + ">" + "sr changed reinitialize and return current sInstance");

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
    
    private boolean isSetupMenuCMD(String data){
        
        BerTlv bertlv = null;
        
        try {
            bertlv = BerTlv.decode(IccUtils.hexStringToBytes(data));  
        } catch (ResultException e) {
            CatLog.d(this, "isSetupMenuCMD BerTlv.decode Exception");
            return false;
        }

        Iterator<ComprehensionTlv> iter = bertlv.getComprehensionTlvs().iterator();
        ComprehensionTlv ctlvCmdDet = null;
   
        while (iter.hasNext()) {
            ComprehensionTlv tmp = iter.next();
            if (tmp.getTag() == ComprehensionTlvTag.COMMAND_DETAILS.value()) {
                ctlvCmdDet = tmp;
                break;
            }
        }
        if(ctlvCmdDet == null) return false;
        
        CommandDetails cmdDet = null;
        
        try {
            cmdDet = ValueParser.retrieveCommandDetails(ctlvCmdDet);
        } catch (ResultException e) {
            CatLog.d(this, "Failed to procees command details");               
            return false;
        }
        return (AppInterface.CommandType.fromInt(cmdDet.typeOfCommand)==AppInterface.CommandType.SET_UP_MENU);
    }
    private void saveSetupMenuData(String data){
        int i = 0;
        int len = data.length();
        int value_max = SystemProperties.PROP_VALUE_MAX;
        int num = (len+value_max-1)/value_max;
        
        SystemProperties.set(SETUP_MEMU_PREFIX+mPhoneId+SETUP_MEMU_DATA_LEN,Integer.toString(len));
        
        int start = 0;
        int end = 0;
        
        while(i < num) {
            end = start+value_max<len?start+value_max:len;
            SystemProperties.set(SETUP_MEMU_PREFIX+mPhoneId+SETUP_MEMU_DATA+i,data.substring(start,end));
            start += value_max;
            i++;
        }
        SystemProperties.set(SETUP_MEMU_PREFIX+mPhoneId,"1");
        mSetupMenuFlag |= (1<<mPhoneId);
    }

    private String getSetupMenuData(){
        int i = 0;
        int len = Integer.parseInt(SystemProperties.get(SETUP_MEMU_PREFIX+mPhoneId+SETUP_MEMU_DATA_LEN,"0"));
        int num = (len+SystemProperties.PROP_VALUE_MAX-1)/SystemProperties.PROP_VALUE_MAX;
        String data = "";
       
        while(i < num) {
            data += SystemProperties.get(SETUP_MEMU_PREFIX+mPhoneId+SETUP_MEMU_DATA+i,"0");
            i++;
        }
        if(data.length()==0 || data.length()!=len) {
            CatLog.d(this,"<"+mPhoneId+">"+"[stk]getSetupMenuData data.length()="+data.length()+",len="+len);
            return null;
        }
        return data;
    }

    @Override
    public void handleMessage(Message msg) {

        switch (msg.what) {
        case MSG_ID_SESSION_END:
        case MSG_ID_PROACTIVE_COMMAND:
        case MSG_ID_EVENT_NOTIFY:
        case MSG_ID_REFRESH:
        case MSG_ID_CALL_SETUP:
            CatLog.d(this, "<" + mPhoneId + ">" + "[stk]ril message arrived = " + msg.what);
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
            // ignore invalid duplicate commands, NEWMS00185824
            if (lastCmd != null && data != null && lastCmd.equals(data)) {
                CatLog.d(this, "<" + mPhoneId + ">" + "duplicate command, ignored !!");
                break;
            }
            if (data != null) {
                lastCmd = new String(data);
            } else {
                lastCmd = null;
            }
            
            if(MSG_ID_PROACTIVE_COMMAND == msg.what && null != data) {
                if((mSetupMenuFlag&(1<<mPhoneId))==0 && isSetupMenuCMD(data)) {
                    saveSetupMenuData(data);
                }
            }

            mMsgDecoder.sendStartDecodingMessageParams(new RilMessage(msg.what, data));
            break;
//        case MSG_ID_CALL_SETUP:
//            mMsgDecoder.sendStartDecodingMessageParams(new RilMessage(msg.what, null));
//            break;
        case MSG_ID_ICC_RECORDS_LOADED:
//            CatLog.d(this, "<" + mPhoneId + ">" + "[stk]active STK mStkActive = " + mStkActive);
//            if (!mStkActive) {
//                mStkActive = true;
//                mCmdIf.reportStkServiceIsRunning(null);
//            } else {
//                CatLog.d(this, "<" + mPhoneId + ">" + "[stk]STK has been activated" );
//            }
            break;
        case MSG_ID_RIL_MSG_DECODED:
            handleRilMsg((RilMessage) msg.obj);
            break;
        case MSG_ID_RESPONSE:
            handleCmdResponse((CatResponseMessage) msg.obj);
            break;
        case MSG_ID_SIM_READY:
            CatLog.d(this, "<" + mPhoneId + ">" + "[stk]active STK mStkActive = " + mStkActive);
            if (!mStkActive) {
                mStkActive = true;
                mCmdIf.reportStkServiceIsRunning(null);
            } else {
                CatLog.d(this, "<" + mPhoneId + ">" + "[stk]STK has been activated" );
            }
            break;

        //Deal With DTMF Message Start
        case MSG_ID_SEND_SECOND_DTMF:
           CommandParams cmdParams = (CommandParams)msg.obj;
           String str = msg.getData().getString("dtmf");
           retrieveDtmfString(cmdParams,str);
           break;
        //Deal With DTMF Message End
        case MSG_ID_EVENT_DOWNLOAD:
            handleEventDownload((CatResponseMessage)msg.obj);
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
        // Use RIL_UNSOL_SIM_REFRESH instead
//        case MSG_ID_REFRESH_STIN:
//            CatLog.d(this, "<" + mPhoneId + ">" + "[stk]MSG_ID_REFRESH_STIN" );
//            int result = 1;
//            if (msg.obj != null) {
//                AsyncResult ar = (AsyncResult) msg.obj;
//                if (ar != null && ar.result != null) {
//                    int[] params = (int[])ar.result;
//                    result = params[0];
//                    CatLog.d(this, "<" + mPhoneId + ">" + "[stk]MSG_ID_REFRESH_STIN result = " + result);
//                    if (0 == result) {
//                        mIccRecords.onRefresh(true, null);
//                    }
//                    //handleRefreshCmdResponse(result);
//                }
//            }
//            break;
        // Add for send session end. NEWMS00205430
        case MSG_ID_REFRESH_STIN:
            CatLog.d(this, "<" + mPhoneId + ">" + "[stk]MSG_ID_REFRESH_STIN" );
            handleRefreshCmdResponse(0);
            //fix bug 106913 by qianqian.tian
            //handleSessionEnd();
            //end bug 106913 by qianqian.tian
            break;
        default:
            throw new AssertionError("Unrecognized CAT command: " + msg.what);
        }
    }

    public synchronized void onCmdResponse(CatResponseMessage resMsg) {
        if (resMsg == null) {
            return;
        }
        // queue a response message.
        Message msg = this.obtainMessage(MSG_ID_RESPONSE, resMsg);
        msg.sendToTarget();
    }

    public synchronized void onEventResponse(CatResponseMessage resMsg) {
        if (resMsg == null) {
            return;
        }
        // queue a event message.
        Message msg = this.obtainMessage(MSG_ID_EVENT_DOWNLOAD, resMsg);
        msg.sendToTarget();
    }

    private boolean validateResponse(CatResponseMessage resMsg) {
        if (mCurrntCmd != null) {
            return (resMsg.cmdDet.compareTo(mCurrntCmd.mCmdDet));
        } else {
            CatLog.d(this, "<" + mPhoneId + ">" + "[stk] validateResponse mCurrntCmd is null");
        }
        return false;
    }

    private boolean removeMenu(Menu menu) {
        try {
            if (menu.items.size() == 1 && menu.items.get(0) == null) {
                return true;
            }
        } catch (NullPointerException e) {
            CatLog.d(this, "<" + mPhoneId + ">" + "Unable to get Menu's items size");
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

    private void handleEventDownload(CatResponseMessage resMsg) {
        EventListType type = resMsg.event;
        if (!isValidEvent(type.value())) {
            CatLog.d(this, "<" + mPhoneId + ">" + "handleEventDownload is inValid Event");
            return;
        }

        CatLog.d(this, "<" + mPhoneId + ">" + "handleEventDownload event = " + type);
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
               /* try {
                    wm.setEventUserActivityNeeded(false);
                } catch (RemoteException e) {
                    CatLog.d(this, "<" + mPhoneId + ">" + "Exception when set EventDownloadNeeded flag in WindowManager");
                } catch (NullPointerException e2) {
                    CatLog.d(this, "<" + mPhoneId + ">" + "wm is null");
                }*/
                break;
            case Event_IdleScreenAvailable:
                oneShot = true;
                sourceId = DEV_ID_DISPLAY;
               /* try {
                    wm.setEventIdleScreenNeeded(false);
                } catch (RemoteException e) {
                    CatLog.d(this, "<" + mPhoneId + ">" + "Exception when set EventDownloadNeeded flag in WindowManager");
                } catch (NullPointerException e2) {
                    CatLog.d(this, "<" + mPhoneId + ">" + "wm is null");
                }*/
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
                CatLog.d(this, "<" + mPhoneId + ">" + "unknown event");
                return;
        }

        eventDownload(type.value(), sourceId, destinationId, additionalInfo);

        if (oneShot) {
            setEventEnabled(type.value(), false);
        }
    }

    private void handleCmdResponse(CatResponseMessage resMsg) {
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
            CatLog.d(this, "<" + mPhoneId + ">" + "[stk] validateResponse fail!! ");
            if (mCurrntCmd != null) {
                CatLog.d(this, "<" + mPhoneId + ">" + "[stk] mCurrntCmd = " +
                         AppInterface.CommandType.fromInt(mCurrntCmd.mCmdDet.typeOfCommand));
            }
            if (resMsg != null) {
                AppInterface.CommandType type = AppInterface.CommandType.fromInt(resMsg.cmdDet.typeOfCommand);
                CatLog.d(this, "<" + mPhoneId + ">" + "[stk] resMsg cmd = " + type);

                if (type == AppInterface.CommandType.SET_UP_MENU && mCurrntCmd == null) {
                    CatLog.d(this, "<" + mPhoneId + ">" + "Warning: force mCurrntCmd to mMenuCmd!!");
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
                RemoveLastCmd();
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
            case OPEN_CHANNEL:
                CatLog.d(this, "<" + mPhoneId + ">" + "OPEN_CHANNEL RES OK");
                resp = new OpenChannelResponseData(resMsg.BearerType, resMsg.BearerParam,
                        resMsg.bufferSize, resMsg.ChannelId, resMsg.LinkStatus);
                break;
            case SEND_DATA:
                CatLog.d(this, "<" + mPhoneId + ">" + "SEND_DATA RES OK");
                resp = new SendDataResponseData(resMsg.channelDataLen);
                break;
            case RECEIVE_DATA:
                CatLog.d(this, "<" + mPhoneId + ">" + "RECEIVE_DATA RES OK");
                resp = new ReceiveDataResponseData(resMsg.channelDataLen, resMsg.channelData);
                break;
            case GET_CHANNEL_STATUS:
                CatLog.d(this, "<" + mPhoneId + ">" + "GET_CHANNEL_STATUS RES OK");
                resp = new ChannelStatusResponseData(resMsg.ChannelId, resMsg.LinkStatus);
                break;
            case SET_UP_CALL:
                CatLog.d(this, "<" + mPhoneId + ">" + "[stk] handleCmdResponse MSG_ID_CALL_SETUP");
                CatLog.d(this, "<" + mPhoneId + ">" + "[stk] send dialStk req");
                mCmdIf.handleCallSetupRequestFromSim(resMsg.usersConfirm, null);
                // No need to send terminal response for SET UP CALL. The user's
                // confirmation result is send back using a dedicated ril message
                // invoked by the CommandInterface call above.
                mCurrntCmd = null;
                RemoveLastCmd();
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
                CatLog.d(this, "<" + mPhoneId + ">" + "SET_UP_CALL TERMINAL_CRNTLY_UNABLE_TO_PROCESS");
                AddInfo = true;
                additionalInfo = AddinfoMeProblem.BUSY_ON_CALL.value();
                break;
            case OPEN_CHANNEL:
                CatLog.d(this, "<" + mPhoneId + ">" + "OPEN_CHANNEL TERMINAL_CRNTLY_UNABLE_TO_PROCESS");
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
                CatLog.d(this, "<" + mPhoneId + ">" + "[stk] SET_UP_CALL NETWORK_CRNTLY_UNABLE_TO_PROCESS");
            }
            break;
        case BEYOND_TERMINAL_CAPABILITY:
            switch (AppInterface.CommandType.fromInt(cmdDet.typeOfCommand)) {
            case OPEN_CHANNEL:
                CatLog.d(this, "<" + mPhoneId + ">" + "OPEN_CHANNEL BEYOND_TERMINAL_CAPABILITY");
                resp = new OpenChannelResponseData(resMsg.BearerType, resMsg.BearerParam,
                        resMsg.bufferSize, resMsg.ChannelId, resMsg.LinkStatus);
                break;
            case SEND_DATA:
                CatLog.d(this, "<" + mPhoneId + ">" + "SEND_DATA BEYOND_TERMINAL_CAPABILITY");
                AddInfo = true;
                additionalInfo = AddinfoBIPProblem.TRANSPORT_LEVEL_NOT_AVAILABLE.value();
                break;
            case RECEIVE_DATA:
                CatLog.d(this, "<" + mPhoneId + ">" + "RECEIVE_DATA BEYOND_TERMINAL_CAPABILITY");
                AddInfo = true;
                additionalInfo = AddinfoBIPProblem.NO_SPECIFIC_CAUSE.value();
                break;
            }
            break;
        case BIP_ERROR:
            switch (AppInterface.CommandType.fromInt(cmdDet.typeOfCommand)) {
            case SEND_DATA:
                CatLog.d(this, "<" + mPhoneId + ">" + "SEND_DATA BIP_ERROR");
                AddInfo = true;
                additionalInfo = AddinfoBIPProblem.CHANNEL_ID_INVALID.value();
                break;
            case CLOSE_CHANNEL:
                CatLog.d(this, "<" + mPhoneId + ">" + "CLOSE_CHANNEL BIP_ERROR");
                AddInfo = true;
                additionalInfo = AddinfoBIPProblem.CHANNEL_CLOSED.value();
                break;
            }
            break;
        case USER_NOT_ACCEPT:
            switch (AppInterface.CommandType.fromInt(cmdDet.typeOfCommand)) {
            case OPEN_CHANNEL:
                CatLog.d(this, "<" + mPhoneId + ">" + "OPEN_CHANNEL USER_NOT_ACCEPT");
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
        CatLog.d(this, "<" + mPhoneId + ">" + "handleRefreshCmdResponse enter" );
        if (mCurrntCmd == null) {
            CatLog.d(this, "<" + mPhoneId + ">" + "[stk]handleRefreshCmdResponse mCurrntCmd is NULL" );
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
            CatLog.d(this, "<" + mPhoneId + ">" + "[stk]handleRefreshCmdResponse CommandType is wrong" );
            return;
        }
    }

    private boolean isInIdleScreen() {
        boolean ret = false;
        IWindowManager wm = getWindowInterface();
       /* try {
            ret = wm.isInIdleScreen();
        } catch (RemoteException e) {
            // no fallback; do nothing.
        }*/
        return ret;
    }

    private boolean isCurrentCanDisplayText() {
        try {
            List<RunningTaskInfo> mRunningTaskInfoList = (List<RunningTaskInfo>)ActivityManagerNative.getDefault().getTasks(1, 0, null);
            int mListSize = mRunningTaskInfoList.size();
            CatLog.d(this, "<" + mPhoneId + ">" + "[stk]isCurrentCanDisplayText trace mListSize = " + mListSize);
            if(mListSize > 0) {
                ComponentName cn = mRunningTaskInfoList.get(0).topActivity;
                CatLog.d(this, "<" + mPhoneId + ">" + "[stk]isCurrentCanDisplayText cn is " + cn);
                boolean result = ((cn.getClassName().indexOf("com.android.stk") != -1))
                        || isHome(cn)
                        || isInIdleScreen();
                return result;
            }
        } catch (RemoteException e) {
            CatLog.d(this, "<" + mPhoneId + ">" + "[stk]isCurrentCanDisplayText exception");
        }
        return false;
    }
    private List<String> getHomes() {
    	List<String> names = new ArrayList<String>();
    	PackageManager packageManager = mContext.getPackageManager();
    	Intent intent = new Intent(Intent.ACTION_MAIN);
    	intent.addCategory(Intent.CATEGORY_HOME);
    	List<ResolveInfo> resolveInfo = packageManager.queryIntentActivities(
    			intent, PackageManager.MATCH_DEFAULT_ONLY);
    	for (ResolveInfo ri : resolveInfo) {
    		names.add(ri.activityInfo.packageName);
    		System.out.println(ri.activityInfo.packageName);
    	}
    	return names;
    }

    public boolean isHome(ComponentName m) {
    	String packagename = m.getPackageName();
    	List<String> name = getHomes();
    	for (String i : name) {
    		if (packagename.equals(i))
    			return true;
    	}
    	return false;
    }
}
