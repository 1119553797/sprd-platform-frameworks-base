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

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;
import android.telephony.PhoneNumberUtils;

import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.gsm.SIMFileHandler;
import com.android.internal.R;

import java.util.Iterator;
import java.util.List;

/**
 * Factory class, used for decoding raw byte arrays, received from baseband,
 * into a CommandParams object.
 *
 */
class CommandParamsFactory extends Handler {
    private static CommandParamsFactory sInstance = null;
    private IconLoader mIconLoader;
    private CommandParams mCmdParams = null;
    private int mIconLoadState = LOAD_NO_ICON;
    private RilMessageDecoder mCaller = null;
    private int mPhoneId;

    // constants
    static final int MSG_ID_LOAD_ICON_DONE = 1;

    // loading icons state parameters.
    static final int LOAD_NO_ICON           = 0;
    static final int LOAD_SINGLE_ICON       = 1;
    static final int LOAD_MULTI_ICONS       = 2;

    // Command Qualifier values for refresh command
    static final int REFRESH_NAA_INIT_AND_FULL_FILE_CHANGE  = 0x00;
    static final int REFRESH_FILE_CHANGE                    = 0x01;
    static final int REFRESH_NAA_INIT_AND_FILE_CHANGE       = 0x02;
    static final int REFRESH_NAA_INIT                       = 0x03;
    static final int REFRESH_UICC_RESET                     = 0x04;

    // zhanglj delete begin 2011-05-25 for cancel single instance
/*    static synchronized CommandParamsFactory getInstance(RilMessageDecoder caller,
            SIMFileHandler fh) {
        if (sInstance != null) {
            return sInstance;
        }
        if (fh != null) {
            return new CommandParamsFactory(caller, fh);
        }
        return null;
    }*/
    // zhanglj delete end

    public CommandParamsFactory(RilMessageDecoder caller, SIMFileHandler fh, int phoneId) {
        mCaller = caller;
        mIconLoader = IconLoaderProxy.getInstance(this, fh,phoneId);
        mPhoneId = phoneId;
    }

    private CommandDetails processCommandDetails(List<ComprehensionTlv> ctlvs) {
        CommandDetails cmdDet = null;

        if (ctlvs != null) {
            // Search for the Command Details object.
            ComprehensionTlv ctlvCmdDet = searchForTag(
                    ComprehensionTlvTag.COMMAND_DETAILS, ctlvs);
            if (ctlvCmdDet != null) {
                try {
                    cmdDet = ValueParser.retrieveCommandDetails(ctlvCmdDet);
                } catch (ResultException e) {
                    StkLog.d(this, "Failed to procees command details");
                }
            }
        }
        return cmdDet;
    }

    void make(BerTlv berTlv) {
        if (berTlv == null) {
            StkLog.d(this, "make() berTlv is null!!!!!!");
            sendCmdParams(ResultCode.CMD_TYPE_NOT_UNDERSTOOD);
            return;
        }
        // reset global state parameters.
        mCmdParams = null;
        mIconLoadState = LOAD_NO_ICON;
        // only proactive command messages are processed.
        if (berTlv.getTag() != BerTlv.BER_PROACTIVE_COMMAND_TAG) {
            sendCmdParams(ResultCode.CMD_TYPE_NOT_UNDERSTOOD);
            return;
        }
        boolean cmdPending = false;
        List<ComprehensionTlv> ctlvs = berTlv.getComprehensionTlvs();
        // process command dtails from the tlv list.
        CommandDetails cmdDet = processCommandDetails(ctlvs);
        if (cmdDet == null) {
            sendCmdParams(ResultCode.CMD_TYPE_NOT_UNDERSTOOD);
            return;
        }

        // extract command type enumeration from the raw value stored inside
        // the Command Details object.
        AppInterface.CommandType cmdType = AppInterface.CommandType
                .fromInt(cmdDet.typeOfCommand);
        if (cmdType == null) {
            // send a terminal response to modem
            mCmdParams = new CommandParams(cmdDet);
            sendCmdParams(ResultCode.CMD_TYPE_NOT_UNDERSTOOD);
            return;
        }

        try {
            switch (cmdType) {
            case SET_UP_MENU:
                cmdPending = processSelectItem(cmdDet, ctlvs);
                break;
            case SELECT_ITEM:
                cmdPending = processSelectItem(cmdDet, ctlvs);
                break;
            case DISPLAY_TEXT:
                cmdPending = processDisplayText(cmdDet, ctlvs);
                break;
             case SET_UP_IDLE_MODE_TEXT:
                 cmdPending = processSetUpIdleModeText(cmdDet, ctlvs);
                 break;
             case GET_INKEY:
                cmdPending = processGetInkey(cmdDet, ctlvs);
                break;
             case GET_INPUT:
                 cmdPending = processGetInput(cmdDet, ctlvs);
                 break;
             case SEND_DTMF:
                 //Deal With DTMF Message Start
                 cmdPending = processDtmfNotify(cmdDet, ctlvs);
                 break;
                 //Deal With DTMF Message End
             
             case SEND_SS:
                 //Icon Display Add Start
                 cmdPending = processSendSsNotify(cmdDet, ctlvs);
                 break;
                 //Icon Display Add End

             case SEND_SMS:
                 cmdPending = processEventNotify(cmdDet, ctlvs);
                 break;
             case SEND_USSD:
                 cmdPending = processEventNotifyUssd(cmdDet, ctlvs);
                 break;
             case SET_UP_CALL:
                 cmdPending = processSetupCall(cmdDet, ctlvs);
                 break;
             case REFRESH:
                processRefresh(cmdDet, ctlvs);
                cmdPending = false;
                break;
             case LAUNCH_BROWSER:
                 cmdPending = processLaunchBrowser(cmdDet, ctlvs);
                 break;
             case PLAY_TONE:
                cmdPending = processPlayTone(cmdDet, ctlvs);
                break;
             case SET_UP_EVENT_LIST:
                processSetUpEventList(cmdDet, ctlvs);
                break;
	     case PROVIDE_LOCAL_INFORMATION:
                cmdPending = false;
                mCmdParams = new CommandParams(cmdDet);
                break;
            //Language Setting Add Start
             case LANGUAGE_NOTIFACTION:
                processLanguageNotify(cmdDet, ctlvs);
                break;
            //Language Setting Add End
             case OPEN_CHANNEL:
                 cmdPending = processOpenChannel(cmdDet, ctlvs);
                 break;
             case CLOSE_CHANNEL:
                 cmdPending = processCloseChannel(cmdDet, ctlvs);
                 break;
             case RECEIVE_DATA:
                 cmdPending = processReceiveData(cmdDet, ctlvs);
                 break;
             case SEND_DATA:
                 cmdPending = processSendData(cmdDet, ctlvs);
                 break;
             case GET_CHANNEL_STATUS:
                 processGetChannelStatus(cmdDet, ctlvs);
                 break;
             default:
                // unsupported proactive commands
                mCmdParams = new CommandParams(cmdDet);
                sendCmdParams(ResultCode.CMD_TYPE_NOT_UNDERSTOOD);
                return;
            }
        } catch (ResultException e) {
            mCmdParams = new CommandParams(cmdDet);
            sendCmdParams(e.result());
            return;
        }
        if (!cmdPending) {
            sendCmdParams(ResultCode.OK);
        }
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
        case MSG_ID_LOAD_ICON_DONE:
            sendCmdParams(setIcons(msg.obj));
            break;
        }
    }

    private ResultCode setIcons(Object data) {
        Bitmap[] icons = null;
        int iconIndex = 0;

        if (data == null) {
            return ResultCode.PRFRMD_ICON_NOT_DISPLAYED;
        }
        switch(mIconLoadState) {
        case LOAD_SINGLE_ICON:
            mCmdParams.setIcon((Bitmap) data);
            break;
        case LOAD_MULTI_ICONS:
            icons = (Bitmap[]) data;
            // set each item icon.
            for (Bitmap icon : icons) {
                mCmdParams.setIcon(icon);
            }
            break;
        }
        return ResultCode.OK;
    }

    private void sendCmdParams(ResultCode resCode) {
        mCaller.sendMsgParamsDecoded(resCode, mCmdParams);
    }

    /**
     * Search for a COMPREHENSION-TLV object with the given tag from a list
     *
     * @param tag A tag to search for
     * @param ctlvs List of ComprehensionTlv objects used to search in
     *
     * @return A ComprehensionTlv object that has the tag value of {@code tag}.
     *         If no object is found with the tag, null is returned.
     */
    private ComprehensionTlv searchForTag(ComprehensionTlvTag tag,
            List<ComprehensionTlv> ctlvs) {
        Iterator<ComprehensionTlv> iter = ctlvs.iterator();
        return searchForNextTag(tag, iter);
    }

    /**
     * Search for the next COMPREHENSION-TLV object with the given tag from a
     * list iterated by {@code iter}. {@code iter} points to the object next to
     * the found object when this method returns. Used for searching the same
     * list for similar tags, usually item id.
     *
     * @param tag A tag to search for
     * @param iter Iterator for ComprehensionTlv objects used for search
     *
     * @return A ComprehensionTlv object that has the tag value of {@code tag}.
     *         If no object is found with the tag, null is returned.
     */
    private ComprehensionTlv searchForNextTag(ComprehensionTlvTag tag,
            Iterator<ComprehensionTlv> iter) {
        int tagValue = tag.value();
        while (iter.hasNext()) {
            ComprehensionTlv ctlv = iter.next();
            if (ctlv.getTag() == tagValue) {
                return ctlv;
            }
        }
        return null;
    }

    private ComprehensionTlv searchForLastTag(ComprehensionTlvTag tag,
            List<ComprehensionTlv> ctlvs) {
        Iterator<ComprehensionTlv> iter = ctlvs.iterator();
        int tagValue = tag.value();
        ComprehensionTlv lastctlv = null;
        while (iter.hasNext()) {
            ComprehensionTlv ctlv = iter.next();
            if (ctlv.getTag() == tagValue) {
                lastctlv = ctlv;
            }
        }
        return lastctlv;
    }

    /**
     * Processes DISPLAY_TEXT proactive command from the SIM card.
     *
     * @param cmdDet Command Details container object.
     * @param ctlvs List of ComprehensionTlv objects following Command Details
     *        object and Device Identities object within the proactive command
     * @return true if the command is processing is pending and additional
     *         asynchronous processing is required.
     * @throws ResultException
     */
    private boolean processDisplayText(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs)
            throws ResultException {

        StkLog.d(this, "process DisplayText");

        TextMessage textMsg = new TextMessage();
        IconId iconId = null;

        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.TEXT_STRING,
                ctlvs);
        if (ctlv != null) {
            textMsg.text = ValueParser.retrieveTextString(ctlv);
            StkLog.d(this, "text = " + textMsg.text);
        }
        // If the tlv object doesn't exist or the it is a null object reply
        // with command not understood.
        if (textMsg.text == null) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }

        ctlv = searchForTag(ComprehensionTlvTag.IMMEDIATE_RESPONSE, ctlvs);
        if (ctlv != null) {
            textMsg.responseNeeded = false;
        }
        // parse icon identifier
        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            iconId = ValueParser.retrieveIconId(ctlv);
            textMsg.iconSelfExplanatory = iconId.selfExplanatory;
        }
        // parse tone duration
        ctlv = searchForTag(ComprehensionTlvTag.DURATION, ctlvs);
        if (ctlv != null) {
            textMsg.duration = ValueParser.retrieveDuration(ctlv);
        }

        // Parse command qualifier parameters.
        textMsg.isHighPriority = (cmdDet.commandQualifier & 0x01) != 0;
        textMsg.userClear = (cmdDet.commandQualifier & 0x80) != 0;

        mCmdParams = new DisplayTextParams(cmdDet, textMsg);

        if (iconId != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            mIconLoader.loadIcon(iconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }
        return false;
    }

    /**
     * Processes SET_UP_IDLE_MODE_TEXT proactive command from the SIM card.
     *
     * @param cmdDet Command Details container object.
     * @param ctlvs List of ComprehensionTlv objects following Command Details
     *        object and Device Identities object within the proactive command
     * @return true if the command is processing is pending and additional
     *         asynchronous processing is required.
     * @throws ResultException
     */
    private boolean processSetUpIdleModeText(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        StkLog.d(this, "process SetUpIdleModeText");

        TextMessage textMsg = new TextMessage();
        IconId iconId = null;

        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.TEXT_STRING,
                ctlvs);
        if (ctlv != null) {
            textMsg.text = ValueParser.retrieveTextString(ctlv);
        }

        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            iconId = ValueParser.retrieveIconId(ctlv);
            textMsg.iconSelfExplanatory = iconId.selfExplanatory;
        }

        mCmdParams = new DisplayTextParams(cmdDet, textMsg);

        if (iconId != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            mIconLoader.loadIcon(iconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }
        return false;
    }

    /**
     * Processes GET_INKEY proactive command from the SIM card.
     *
     * @param cmdDet Command Details container object.
     * @param ctlvs List of ComprehensionTlv objects following Command Details
     *        object and Device Identities object within the proactive command
     * @return true if the command is processing is pending and additional
     *         asynchronous processing is required.
     * @throws ResultException
     */
    private boolean processGetInkey(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        StkLog.d(this, "process GetInkey");

        Input input = new Input();
        IconId iconId = null;

        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.TEXT_STRING,
                ctlvs);
        if (ctlv != null) {
            input.text = ValueParser.retrieveTextString(ctlv);
        } else {
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        }
        // parse icon identifier
        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            iconId = ValueParser.retrieveIconId(ctlv);
            if(iconId.selfExplanatory) {
                input.text = "";
            }
        }

        input.minLen = 1;
        input.maxLen = 1;

        input.digitOnly = (cmdDet.commandQualifier & 0x01) == 0;
        input.ucs2 = (cmdDet.commandQualifier & 0x02) != 0;
        input.yesNo = (cmdDet.commandQualifier & 0x04) != 0;
        input.helpAvailable = (cmdDet.commandQualifier & 0x80) != 0;

        mCmdParams = new GetInputParams(cmdDet, input);

        if (iconId != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            mIconLoader.loadIcon(iconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }
        return false;
    }

    /**
     * Processes GET_INPUT proactive command from the SIM card.
     *
     * @param cmdDet Command Details container object.
     * @param ctlvs List of ComprehensionTlv objects following Command Details
     *        object and Device Identities object within the proactive command
     * @return true if the command is processing is pending and additional
     *         asynchronous processing is required.
     * @throws ResultException
     */
    private boolean processGetInput(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        StkLog.d(this, "process GetInput");

        Input input = new Input();
        IconId iconId = null;

        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.TEXT_STRING,
                ctlvs);
        if (ctlv != null) {
            input.text = ValueParser.retrieveTextString(ctlv);
        } else {
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        }

        ctlv = searchForTag(ComprehensionTlvTag.RESPONSE_LENGTH, ctlvs);
        if (ctlv != null) {
            try {
                byte[] rawValue = ctlv.getRawValue();
                int valueIndex = ctlv.getValueIndex();
                input.minLen = rawValue[valueIndex] & 0xff;
                input.maxLen = rawValue[valueIndex + 1] & 0xff;
            } catch (IndexOutOfBoundsException e) {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }
        } else {
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        }

        ctlv = searchForTag(ComprehensionTlvTag.DEFAULT_TEXT, ctlvs);
        if (ctlv != null) {
            input.defaultText = ValueParser.retrieveTextString(ctlv);
        }
        // parse icon identifier
        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            iconId = ValueParser.retrieveIconId(ctlv);
            if(iconId.selfExplanatory) {
                input.text = "";
            }
        }

        input.digitOnly = (cmdDet.commandQualifier & 0x01) == 0;
        input.ucs2 = (cmdDet.commandQualifier & 0x02) != 0;
        input.echo = (cmdDet.commandQualifier & 0x04) == 0;
        input.packed = (cmdDet.commandQualifier & 0x08) != 0;
        input.helpAvailable = (cmdDet.commandQualifier & 0x80) != 0;

        if (input.ucs2 == true) {
            // the max text length should be less than 120 usc2 characters as the whole terminal
            // response data length is less than 255 and the header may has 15 bytes.
            if (input.minLen > 120) {
                input.minLen = 120;
            }
            if (input.maxLen > 120) {
                input.maxLen = 120;
            }
        }

        mCmdParams = new GetInputParams(cmdDet, input);

        if (iconId != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            mIconLoader.loadIcon(iconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }
        return false;
    }

    /**
     * Processes REFRESH proactive command from the SIM card.
     *
     * @param cmdDet Command Details container object.
     * @param ctlvs List of ComprehensionTlv objects following Command Details
     *        object and Device Identities object within the proactive command
     */
    private boolean processRefresh(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) {

        StkLog.d(this, "processRefresh command = " + cmdDet.commandQualifier);
        TextMessage textMsg = new TextMessage();
        Resources r = Resources.getSystem();

        switch (cmdDet.commandQualifier) {
        case REFRESH_NAA_INIT_AND_FULL_FILE_CHANGE:
        case REFRESH_FILE_CHANGE:
        case REFRESH_NAA_INIT_AND_FILE_CHANGE:
        case REFRESH_NAA_INIT:
            textMsg.text = r.getString(R.string.stk_refresh_sim_init_message);
            mCmdParams = new DisplayTextParams(cmdDet, textMsg);
            break;
        case REFRESH_UICC_RESET:
            textMsg.text = r.getString(R.string.stk_refresh_sim_reset_message);
            mCmdParams = new DisplayTextParams(cmdDet, textMsg);
            break;
        default:
            StkLog.d(this, "processRefresh: wrong commandQualifier");
            break;
        }
        return false;
    }

    /**
     * Processes SELECT_ITEM proactive command from the SIM card.
     *
     * @param cmdDet Command Details container object.
     * @param ctlvs List of ComprehensionTlv objects following Command Details
     *        object and Device Identities object within the proactive command
     * @return true if the command is processing is pending and additional
     *         asynchronous processing is required.
     * @throws ResultException
     */
    private boolean processSelectItem(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        StkLog.d(this, "process SelectItem");

        Menu menu = new Menu();
        IconId titleIconId = null;
        ItemsIconId itemsIconId = null;
        Iterator<ComprehensionTlv> iter = ctlvs.iterator();
        byte[] nextActionData = null;

        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID,
                ctlvs);
        if (ctlv != null) {
            menu.title = ValueParser.retrieveAlphaId(ctlv);
        }

        ctlv = searchForTag(ComprehensionTlvTag.NEXT_ACTION_INDICATOR, ctlvs);
        if (ctlv != null) {
            StkLog.d(this, "SelectItem found NEXT_ACTION_INDICATOR");
            nextActionData = ValueParser.retrieveByteArray(ctlv, 0);
            if (nextActionData != null) {
                StkLog.d(this, "SelectItem NEXT_ACTION = "
                         + IccUtils.bytesToHexString(nextActionData));
            }
        }

        int i = 0;
        while (true) {
            ctlv = searchForNextTag(ComprehensionTlvTag.ITEM, iter);
            if (ctlv != null) {
                String naiString = null;
                if (nextActionData != null && i < nextActionData.length) {
                    AppInterface.NextActionInd naiId = AppInterface.NextActionInd.fromInt(nextActionData[i]);
                    naiString = AppInterface.NextActionInd.NaiToString(naiId);
                }
                menu.items.add(ValueParser.retrieveItem(ctlv, naiString));
                i ++;
            } else {
                break;
            }
        }

        // We must have at least one menu item.
        if (menu.items.size() == 0) {
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        }

        ctlv = searchForTag(ComprehensionTlvTag.ITEM_ID, ctlvs);
        if (ctlv != null) {
            // STK items are listed 1...n while list start at 0, need to
            // subtract one.
            menu.defaultItem = ValueParser.retrieveItemId(ctlv) - 1;
        }

        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            titleIconId = ValueParser.retrieveIconId(ctlv);
            menu.titleIconSelfExplanatory = titleIconId.selfExplanatory;
        }

        ctlv = searchForTag(ComprehensionTlvTag.ITEM_ICON_ID_LIST, ctlvs);
        if (ctlv != null) {
            mIconLoadState = LOAD_MULTI_ICONS;
            itemsIconId = ValueParser.retrieveItemsIconId(ctlv);
            menu.itemsIconSelfExplanatory = itemsIconId.selfExplanatory;
        }

        boolean presentTypeSpecified = (cmdDet.commandQualifier & 0x01) != 0;
        if (presentTypeSpecified) {
            if ((cmdDet.commandQualifier & 0x02) == 0) {
                menu.presentationType = PresentationType.DATA_VALUES;
            } else {
                menu.presentationType = PresentationType.NAVIGATION_OPTIONS;
            }
        }
        menu.softKeyPreferred = (cmdDet.commandQualifier & 0x04) != 0;
        menu.helpAvailable = (cmdDet.commandQualifier & 0x80) != 0;

        mCmdParams = new SelectItemParams(cmdDet, menu, titleIconId != null);

        // Load icons data if needed.
        switch(mIconLoadState) {
        case LOAD_NO_ICON:
            return false;
        case LOAD_SINGLE_ICON:
            mIconLoader.loadIcon(titleIconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            break;
        case LOAD_MULTI_ICONS:
            int[] recordNumbers = itemsIconId.recordNumbers;
            if (titleIconId != null) {
                // Create a new array for all the icons (title and items).
                recordNumbers = new int[itemsIconId.recordNumbers.length + 1];
                recordNumbers[0] = titleIconId.recordNumber;
                System.arraycopy(itemsIconId.recordNumbers, 0, recordNumbers,
                        1, itemsIconId.recordNumbers.length);
            }
            mIconLoader.loadIcons(recordNumbers, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            break;
        }
        return true;
    }

    //Language Setting Add Start
    private boolean processLanguageNotify(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        StkLog.d(this, "processLanguageNotify start");
        System.out.println("wangsl processLanguageNotify start");
        String language = null;

        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.LANGUAGE,
                ctlvs);
        if (ctlv != null) {
             language = ValueParser.retrieveLanguage(ctlv);
        } else {
            StkLog.d(this, "processLanguageNotify language is null");
            //throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
            //CR119858 Modify Start
            //language = StkLanguageDecoder.getInstance().getDefaultLanguage();
            //CR119858 Modify End
		}

        mCmdParams = new LanguageParams(cmdDet, language);

        return false;
    }
    //language


    //Icon Display Add Start
    private boolean processSendSsNotify(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        StkLog.d(this, "processSendSsNotify start");

        TextMessage textMsg = new TextMessage();
        IconId iconId = null;

        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID,
                ctlvs);
        if (ctlv != null) {
            textMsg.text = ValueParser.retrieveAlphaId(ctlv);
        } 

        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            iconId = ValueParser.retrieveIconId(ctlv);
            textMsg.iconSelfExplanatory = iconId.selfExplanatory;
        }

        textMsg.responseNeeded = false;
        mCmdParams = new DisplayTextParams(cmdDet, textMsg);

        if (iconId != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            mIconLoader.loadIcon(iconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }
        return false;
    }
    //Icon Display Add End
    /**
     * Processes EVENT_NOTIFY message from baseband.
     *
     * @param cmdDet Command Details container object.
     * @param ctlvs List of ComprehensionTlv objects following Command Details
     *        object and Device Identities object within the proactive command
     * @return true if the command is processing is pending and additional
     *         asynchronous processing is required.
     */
    private boolean processEventNotify(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        StkLog.d(this, "process EventNotify");

        TextMessage textMsg = new TextMessage();
        IconId iconId = null;

        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID,
                ctlvs);
        if (ctlv != null) {
            textMsg.text = ValueParser.retrieveAlphaId(ctlv);
        } else {
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
            //textMsg.text = Resources.getSystem().getString(R.string.sms_control_title);
            //StkLog.d(this, "alpha id null, use default="+textMsg.text);
        }

        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            iconId = ValueParser.retrieveIconId(ctlv);
            textMsg.iconSelfExplanatory = iconId.selfExplanatory;
        }

        textMsg.responseNeeded = false;
        mCmdParams = new DisplayTextParams(cmdDet, textMsg);

        if (iconId != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            mIconLoader.loadIcon(iconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }
        return false;
    }

    private boolean processEventNotifyUssd(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        StkLog.d(this, "process EventNotify");

        TextMessage textMsg = new TextMessage();
        IconId iconId = null;

        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID,
                ctlvs);
        if (ctlv != null) {
            textMsg.text = ValueParser.retrieveAlphaId(ctlv);
        } else {
            //throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
            StkLog.d(this, "EventNotify no alpha id, make the default string");
            textMsg.text = "Send ussd";
        }

        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            iconId = ValueParser.retrieveIconId(ctlv);
            textMsg.iconSelfExplanatory = iconId.selfExplanatory;
        }

        textMsg.responseNeeded = false;
        mCmdParams = new DisplayTextParams(cmdDet, textMsg);

        if (iconId != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            mIconLoader.loadIcon(iconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }
        return false;
    }

    //Deal With DTMF Message Start
    private boolean processDtmfNotify(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        System.out.println("wangsl processDtmfNotify start");
        String dtmfString = null;
        TextMessage textMsg = new TextMessage();
        IconId iconId = null;

        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.DTMF,
                ctlvs);
        if (ctlv != null) {
            dtmfString = ValueParser.retrieveDTMF(ctlv);
            System.out.println("wangsl dtmfString is:" + dtmfString);
        } else {
            System.out.println("wangsl retrieve dtmf error");
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        }

        ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID,
                ctlvs);
        if (ctlv != null) {
            textMsg.text = ValueParser.retrieveAlphaId(ctlv);
        }

        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            iconId = ValueParser.retrieveIconId(ctlv);
            textMsg.iconSelfExplanatory = iconId.selfExplanatory;
        }

        textMsg.responseNeeded = false;
        mCmdParams = new DtmfParams(cmdDet, textMsg,dtmfString);

        if (iconId != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            mIconLoader.loadIcon(iconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }
        return false;
    }
    //Deal With DTMF Message End
    /**
     * Processes SET_UP_EVENT_LIST proactive command from the SIM card.
     *
     * @param cmdDet Command Details object retrieved.
     * @param ctlvs List of ComprehensionTlv objects following Command Details
     *        object and Device Identities object within the proactive command
     * @return true if the command is processing is pending and additional
     *         asynchronous processing is required.
     */
    private boolean processSetUpEventList(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) {

        StkLog.d(this, "processSetUpEventList");
        AppInterface.EventListType[] eventList = null;

         ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.EVENT_LIST, ctlvs);
         if (ctlv != null) {
             try {
                 byte[] rawValue = ctlv.getRawValue();
                 int valueIndex = ctlv.getValueIndex();
                 int valueLen = ctlv.getLength();
                 StkLog.d(this, "processSetUpEventList valueLen = " + valueLen);
                 if (valueLen > 0) {
                     eventList = new AppInterface.EventListType[valueLen];
                     for (int i = 0;i < valueLen;i ++) {
                         eventList[i] = AppInterface.EventListType.fromInt(rawValue[valueIndex+i] & 0xff);
                         StkLog.d(this, "processSetUpEventList:eventtype = " + eventList[i]);
                     }
                 } else {
                     StkLog.d(this, "processSetUpEventList empty list");
                 }
             } catch (IndexOutOfBoundsException e) {
                 StkLog.d(this, "ProcessSetUpEventList exception = " + e);
             }
         } else {
             StkLog.d(this, "processSetUpEventList:get ctlv Failed");
         }
         mCmdParams = new EventListParams(cmdDet, eventList);
        return true;
    }

    /**
     * Processes LAUNCH_BROWSER proactive command from the SIM card.
     *
     * @param cmdDet Command Details container object.
     * @param ctlvs List of ComprehensionTlv objects following Command Details
     *        object and Device Identities object within the proactive command
     * @return true if the command is processing is pending and additional
     *         asynchronous processing is required.
     * @throws ResultException
     */
     private boolean processLaunchBrowser(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        StkLog.d(this, "process LaunchBrowser");

        TextMessage confirmMsg = new TextMessage();
        IconId iconId = null;
        String url = null;

        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.URL, ctlvs);
        if (ctlv != null) {
            try {
                byte[] rawValue = ctlv.getRawValue();
                int valueIndex = ctlv.getValueIndex();
                int valueLen = ctlv.getLength();
                if (valueLen > 0) {
                    url = GsmAlphabet.gsm8BitUnpackedToString(rawValue,
                            valueIndex, valueLen);
                } else {
                    url = null;
                }
            } catch (IndexOutOfBoundsException e) {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }
        }

        // parse alpha identifier.
        ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs);
        if (ctlv != null) {
            confirmMsg.text = ValueParser.retrieveAlphaId(ctlv);
        }
        // parse icon identifier
        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            iconId = ValueParser.retrieveIconId(ctlv);
            confirmMsg.iconSelfExplanatory = iconId.selfExplanatory;
        }

        // parse command qualifier value.
        LaunchBrowserMode mode;
        switch (cmdDet.commandQualifier) {
        case 0x00:
        default:
            mode = LaunchBrowserMode.LAUNCH_IF_NOT_ALREADY_LAUNCHED;
            break;
        case 0x02:
            mode = LaunchBrowserMode.USE_EXISTING_BROWSER;
            break;
        case 0x03:
            mode = LaunchBrowserMode.LAUNCH_NEW_BROWSER;
            break;
        }

        mCmdParams = new LaunchBrowserParams(cmdDet, confirmMsg, url, mode);

        if (iconId != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            mIconLoader.loadIcon(iconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }
        return false;
    }

     /**
     * Processes PLAY_TONE proactive command from the SIM card.
     *
     * @param cmdDet Command Details container object.
     * @param ctlvs List of ComprehensionTlv objects following Command Details
     *        object and Device Identities object within the proactive command
     * @return true if the command is processing is pending and additional
     *         asynchronous processing is required.t
     * @throws ResultException
     */
     private boolean processPlayTone(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        StkLog.d(this, "process PlayTone");

        Tone tone = null;
        TextMessage textMsg = new TextMessage();
        Duration duration = null;
        IconId iconId = null;

        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.TONE, ctlvs);
        if (ctlv != null) {
            // Nothing to do for null objects.
            if (ctlv.getLength() > 0) {
                try {
                    byte[] rawValue = ctlv.getRawValue();
                    int valueIndex = ctlv.getValueIndex();
                    int toneVal = rawValue[valueIndex];
                    tone = Tone.fromInt(toneVal);
                } catch (IndexOutOfBoundsException e) {
                    throw new ResultException(
                            ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                }
            }
        }
        // parse alpha identifier
        ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs);
        if (ctlv != null) {
            textMsg.text = ValueParser.retrieveAlphaId(ctlv);
        }
        // parse tone duration
        ctlv = searchForTag(ComprehensionTlvTag.DURATION, ctlvs);
        if (ctlv != null) {
            duration = ValueParser.retrieveDuration(ctlv);
        }
        // parse icon identifier
        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            iconId = ValueParser.retrieveIconId(ctlv);
            textMsg.iconSelfExplanatory = iconId.selfExplanatory;
        }

        boolean vibrate = (cmdDet.commandQualifier & 0x01) != 0x00;

        textMsg.responseNeeded = false;
        mCmdParams = new PlayToneParams(cmdDet, textMsg, tone, duration, vibrate);

        if (iconId != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            mIconLoader.loadIcon(iconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }
        return false;
    }

    /**
     * Processes SETUP_CALL proactive command from the SIM card.
     *
     * @param cmdDet Command Details object retrieved from the proactive command
     *        object
     * @param ctlvs List of ComprehensionTlv objects following Command Details
     *        object and Device Identities object within the proactive command
     * @return true if the command is processing is pending and additional
     *         asynchronous processing is required.
     */
     private boolean processSetupCall(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {
        StkLog.d(this, "process SetupCall");

        Iterator<ComprehensionTlv> iter = ctlvs.iterator();
        ComprehensionTlv ctlv = null;
        // User confirmation phase message.
        TextMessage confirmMsg = new TextMessage();
        // Call set up phase message.
        TextMessage callMsg = new TextMessage();
        // Call address message.
        TextMessage callAddress = new TextMessage();
        IconId confirmIconId = null;
        IconId callIconId = null;

        // get confirmation message string.
        ctlv = searchForNextTag(ComprehensionTlvTag.ALPHA_ID, iter);
        if (ctlv != null) {
            confirmMsg.text = ValueParser.retrieveAlphaId(ctlv);
            StkLog.d(this, "process SetupCall confirmMsg =" + confirmMsg.text);
        }

        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            confirmIconId = ValueParser.retrieveIconId(ctlv);
            confirmMsg.iconSelfExplanatory = confirmIconId.selfExplanatory;
        }

        // get call set up message string.
        ctlv = searchForNextTag(ComprehensionTlvTag.ALPHA_ID, iter);
        if (ctlv != null) {
            callMsg.text = ValueParser.retrieveAlphaId(ctlv);
            StkLog.d(this, "process SetupCall callMsg =" + callMsg.text);
        }

        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            callIconId = ValueParser.retrieveIconId(ctlv);
            callMsg.iconSelfExplanatory = callIconId.selfExplanatory;
        }

        // get call set up address.
        ctlv = searchForTag(ComprehensionTlvTag.ADDRESS, ctlvs);
        if (ctlv != null) {
            byte[] rawValue = ctlv.getRawValue();
            int valueIndex = ctlv.getValueIndex();
            int length = ctlv.getLength();
            StkLog.d(this, "process SetupCall call valueIndex="+valueIndex+" length="+length);
            if ( length > 0) {
                callAddress.text = PhoneNumberUtils.calledPartyBCDToString(rawValue, valueIndex, length);
                StkLog.d(this, "process SetupCall call address ="+callAddress.text);
            } else {
                StkLog.d(this, "process SetupCall call address is NULL");
            }
        } else {
            StkLog.d(this, "process SetupCall call ctlv not found");
        }

        mCmdParams = new CallSetupParams(cmdDet, confirmMsg, callMsg, callAddress);

        if (confirmIconId != null || callIconId != null) {
            mIconLoadState = LOAD_MULTI_ICONS;
            int[] recordNumbers = new int[2];
            recordNumbers[0] = confirmIconId != null
                    ? confirmIconId.recordNumber : -1;
            recordNumbers[1] = callIconId != null ? callIconId.recordNumber
                    : -1;

            mIconLoader.loadIcons(recordNumbers, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }
        return false;
    }

     /**
      * Processes OPEN_CHANNEL proactive command from the SIM card.
      *
      * @param cmdDet Command Details object retrieved from the proactive command
      *        object
      * @param ctlvs List of ComprehensionTlv objects following Command Details
      *        object and Device Identities object within the proactive command
      * @return true if the command is processing is pending and additional
      *         asynchronous processing is required.
      */
     private boolean processOpenChannel(CommandDetails cmdDet,
             List<ComprehensionTlv> ctlvs) throws ResultException {
         StkLog.d(this, "process OpenChannel");

         Iterator<ComprehensionTlv> iter = ctlvs.iterator();
         IconId iconId = null;
         OpenChannelData openchanneldata = new OpenChannelData();
         // Alpha identifier
         ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs);
         if (ctlv != null) {
             openchanneldata.text = ValueParser.retrieveAlphaId(ctlv);
             if (openchanneldata.text != null) {
                 openchanneldata.isNullAlphaId = false;
                 StkLog.d(this, "OpenChannel Alpha identifier done");
             } else {
                 openchanneldata.isNullAlphaId = true;
                 StkLog.d(this, "OpenChannel null Alpha id");
             }
         }
         // Icon identifier
         ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
         if (ctlv != null) {
             iconId = ValueParser.retrieveIconId(ctlv);
             openchanneldata.iconSelfExplanatory = iconId.selfExplanatory;
             StkLog.d(this, "OpenChannel Icon identifier done");
         }
         // Bearer description
         ctlv = searchForTag(ComprehensionTlvTag.BEARER_DESCRIPTION, ctlvs);
         if (ctlv != null) {
             try {
                 byte[] rawValue = ctlv.getRawValue();
                 int valueIndex = ctlv.getValueIndex();
                 openchanneldata.BearerType = rawValue[valueIndex];
                 int length = ctlv.getLength();
                 if (length > 1) {
                     openchanneldata.BearerParam = IccUtils.bytesToHexString(ValueParser.retrieveByteArray(ctlv, 1));
                     StkLog.d(this, "OpenChannel Bearer description done");
                 }
             } catch (IndexOutOfBoundsException e) {
                 StkLog.d(this, "OpenChannel BEARER_DESCRIPTION IndexOutOfBoundsException");
                 throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
             }
         } else {
             StkLog.d(this, "OpenChannel BEARER_DESCRIPTION ctlv is null");
             throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
         }
         // Buffer size
         ctlv = searchForTag(ComprehensionTlvTag.BUFFER_SIZE, ctlvs);
         if (ctlv != null) {
             try {
                 byte[] rawValue = ctlv.getRawValue();
                 int valueIndex = ctlv.getValueIndex();
                 openchanneldata.bufferSize = ((rawValue[valueIndex] & 0xff) << 8) |
                                                rawValue[valueIndex + 1] & 0xff;
                 StkLog.d(this, "OpenChannel Buffer size done");
             } catch (IndexOutOfBoundsException e) {
                 StkLog.d(this, "OpenChannel BUFFER_SIZE IndexOutOfBoundsException");
                 throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
             }
         } else {
             StkLog.d(this, "OpenChannel BUFFER_SIZE ctlv is null");
             throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
         }
         // Network Access Name
         ctlv = searchForTag(ComprehensionTlvTag.NETWORK_ACCESS_NAME, ctlvs);
         if (ctlv != null) {
                 //openchanneldata.NetAccessName = IccUtils.bytesToHexString(ValueParser.retrieveByteArray(ctlv, 0));
                 byte[] raw = ValueParser.retrieveByteArray(ctlv, 0);
                 openchanneldata.NetAccessName = convNetworkAccessName(raw);
                 StkLog.d(this, "OpenChannel Network Access Name done");
         }
         // Other address (local address)
         ctlv = searchForTag(ComprehensionTlvTag.OTHER_ADDRESS, ctlvs);
         if (ctlv != null) {
             try {
                 byte[] rawValue = ctlv.getRawValue();
                 int valueIndex = ctlv.getValueIndex();
                 int length = ctlv.getLength();
                 if (length > 1) {
                     openchanneldata.OtherAddressType = rawValue[valueIndex];
                     byte [] address = ValueParser.retrieveByteArray(ctlv, 1);
                     if (openchanneldata.OtherAddressType == OpenChannelData.ADDRESS_TYPE_IPV4 && address.length == 4) {
                         openchanneldata.OtherAddress = convIpv4Address(address);
                         StkLog.d(this, "OpenChannel local address done");
                     } else {
                         StkLog.d(this, "OpenChannel local Address is not ipv4 format");
                         openchanneldata.OtherAddress = "";
                         openchanneldata.OtherAddressType = 0;
                     }
                 } else {
                     StkLog.d(this, "OpenChannel local address tag length error");
                 }
             } catch (IndexOutOfBoundsException e) {
                 StkLog.d(this, "OpenChannel OtherAddress IndexOutOfBoundsException");
             }
         }
         // Text String (User login)
         ctlv = searchForNextTag(ComprehensionTlvTag.TEXT_STRING, iter);
         if (ctlv != null) {
             openchanneldata.LoginStr = ValueParser.retrieveTextString(ctlv);
             StkLog.d(this, "OpenChannel User login done");
         }
         // Text String (User password)
         ctlv = searchForNextTag(ComprehensionTlvTag.TEXT_STRING, iter);
         if (ctlv != null) {
             openchanneldata.PwdStr = ValueParser.retrieveTextString(ctlv);
             StkLog.d(this, "OpenChannel User password done");
         }
         // SIM ME interface transport level
         ctlv = searchForTag(ComprehensionTlvTag.TRANSPORT_LEVEL, ctlvs);
         if (ctlv != null) {
             try {
                 byte[] rawValue = ctlv.getRawValue();
                 int valueIndex = ctlv.getValueIndex();
                 openchanneldata.transportType = rawValue[valueIndex];
                 openchanneldata.portNumber = ((rawValue[valueIndex+1] & 0xff) << 8) |
                                                rawValue[valueIndex + 2] & 0xff;
                 StkLog.d(this, "OpenChannel transport level done");
             } catch (IndexOutOfBoundsException e) {
                 StkLog.d(this, "OpenChannel TRANSPORT_LEVEL IndexOutOfBoundsException");
             }
         }
         // Data destination address
         ctlv = searchForLastTag(ComprehensionTlvTag.OTHER_ADDRESS, ctlvs);
         if (ctlv != null) {
             try {
                 byte[] rawValue = ctlv.getRawValue();
                 int valueIndex = ctlv.getValueIndex();
                 int length = ctlv.getLength();
                 if (length > 1) {
                     openchanneldata.DataDstAddressType = rawValue[valueIndex];
                     byte [] address = ValueParser.retrieveByteArray(ctlv, 1);
                     if (openchanneldata.DataDstAddressType == OpenChannelData.ADDRESS_TYPE_IPV4 && address.length == 4) {
                         openchanneldata.DataDstAddress = convIpv4Address(address);
                         StkLog.d(this, "OpenChannel Data destination address done");
                     } else {
                         StkLog.d(this, "OpenChannel Data destination address is not ipv4 format");
                         openchanneldata.DataDstAddress = "";
                         openchanneldata.DataDstAddressType = 0;
                     }
                 } else {
                     StkLog.d(this, "OpenChannel Data destination address tag length error");
                 }
             } catch (IndexOutOfBoundsException e) {
                 StkLog.d(this, "OpenChannel DataDstAddress IndexOutOfBoundsException");
             }
         }

         mCmdParams = new OpenChannelDataParams(cmdDet, openchanneldata);
         if (iconId != null) {
             mIconLoadState = LOAD_SINGLE_ICON;
             mIconLoader.loadIcon(iconId.recordNumber, this
                     .obtainMessage(MSG_ID_LOAD_ICON_DONE));
             return true;
         }
         StkLog.d(this, "Alpha id: " + openchanneldata.text +
                        ", NetAccessName: " + openchanneldata.NetAccessName +
                        ", bufferSize: " + openchanneldata.bufferSize +
                        ", BearerType: " + openchanneldata.BearerType + "\n" +
                        ", BearerParam: " + openchanneldata.BearerParam +
                        ", LocalAddressType: " + openchanneldata.OtherAddressType +
                        ", LocalAddress: " + openchanneldata.OtherAddress +
                        ", LoginStr: " + openchanneldata.LoginStr + "\n" +
                        ", PwdStr: " + openchanneldata.PwdStr +
                        ", transportType: " + openchanneldata.transportType +
                        ", portNumber: " + openchanneldata.portNumber +
                        ", DataDstAddressType: " + openchanneldata.DataDstAddressType + "\n" +
                        ", DataDstAddress: " + openchanneldata.DataDstAddress);
         return false;
     }

    private String convIpv4Address(byte[] address) {
        StringBuffer sb = new StringBuffer("");
        for(int i = 0; i < 3; i ++) {
            sb.append(String.valueOf(address[i] & 0xff));
            sb.append(".");
        }
        sb.append(String.valueOf(address[3] & 0xff));
        return sb.toString();
    }

    private String convNetworkAccessName(byte[] apn) {
        if (apn == null)
            return null;

        int len = apn.length;
        int temp_len = 0;
        int index = 0;
        StringBuilder ret = new StringBuilder(2 * len);

        while (len > 1) {
            temp_len = apn[index++];
            if (temp_len < len) {
                for (int i = 0; i < temp_len; i++) {
                    ret.append((char) apn[index]);
                    index++;
                }
                len = len - (temp_len + 1);
                if (len > 1) {
                    ret.append('.');
                } else {
                    break;
                }
            } else {
                for (int i = 0; i < len - 1; i++) {
                    ret.append((char) apn[index]);
                    index++;
                }
                break;
            }
        }

        return ret.toString();
    }

     private boolean processCloseChannel(CommandDetails cmdDet,
             List<ComprehensionTlv> ctlvs) throws ResultException {
         StkLog.d(this, "process CloseChannel");
         DeviceIdentities deviceIdentities = null;
         IconId iconId = null;
         CloseChannelData closechanneldata = new CloseChannelData();
         //Device identities
         ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.DEVICE_IDENTITIES, ctlvs);
         if (ctlv != null) {
             deviceIdentities = ValueParser.retrieveDeviceIdentities(ctlv);
         }
         // Alpha identifier
         ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs);
         if (ctlv != null) {
             closechanneldata.text = ValueParser.retrieveAlphaId(ctlv);
         }
         // Icon identifier
         ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
         if (ctlv != null) {
             iconId = ValueParser.retrieveIconId(ctlv);
             closechanneldata.iconSelfExplanatory = iconId.selfExplanatory;
         }

         mCmdParams = new CloseChannelDataParams(cmdDet, closechanneldata, deviceIdentities);
         if (iconId != null) {
             mIconLoadState = LOAD_SINGLE_ICON;
             mIconLoader.loadIcon(iconId.recordNumber, this
                     .obtainMessage(MSG_ID_LOAD_ICON_DONE));
             return true;
         }
         return false;
     }

     private boolean processReceiveData(CommandDetails cmdDet,
             List<ComprehensionTlv> ctlvs) throws ResultException {
         StkLog.d(this, "process ReceiveData");

         IconId iconId = null;
         ReceiveChannelData receivedata = new ReceiveChannelData();
         // Alpha identifier
         ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs);
         if (ctlv != null) {
             receivedata.text = ValueParser.retrieveAlphaId(ctlv);
         }
         // Icon identifier
         ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
         if (ctlv != null) {
             iconId = ValueParser.retrieveIconId(ctlv);
             receivedata.iconSelfExplanatory = iconId.selfExplanatory;
         }
         // Channel data length
         ctlv = searchForTag(ComprehensionTlvTag.CHANNEL_DATA_LENGTH, ctlvs);
         if (ctlv != null) {
             try {
                 byte[] rawValue = ctlv.getRawValue();
                 int valueIndex = ctlv.getValueIndex();
                 receivedata.channelDataLength = rawValue[valueIndex] & 0xff;
             } catch (IndexOutOfBoundsException e) {
                 StkLog.d(this, "ReceiveData CHANNEL_DATA_LENGTH IndexOutOfBoundsException");
                 throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
             }
         } else {
             StkLog.d(this, "ReceiveData CHANNEL_DATA_LENGTH ctlv is null");
             throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
         }

         mCmdParams = new ReceiveChannelDataParams(cmdDet, receivedata);
         if (iconId != null) {
             mIconLoadState = LOAD_SINGLE_ICON;
             mIconLoader.loadIcon(iconId.recordNumber, this
                     .obtainMessage(MSG_ID_LOAD_ICON_DONE));
             return true;
         }
         return false;
     }

     private boolean processSendData(CommandDetails cmdDet,
             List<ComprehensionTlv> ctlvs) throws ResultException {
         StkLog.d(this, "process SendData");

         IconId iconId = null;
         DeviceIdentities deviceIdentities = null;
         SendChannelData senddata = new SendChannelData();
         //Device identities
         ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.DEVICE_IDENTITIES, ctlvs);
         if (ctlv != null) {
             deviceIdentities = ValueParser.retrieveDeviceIdentities(ctlv);
         }
         // Alpha identifier
         ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs);
         if (ctlv != null) {
             senddata.text = ValueParser.retrieveAlphaId(ctlv);
         }
         // Icon identifier
         ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
         if (ctlv != null) {
             iconId = ValueParser.retrieveIconId(ctlv);
             senddata.iconSelfExplanatory = iconId.selfExplanatory;
         }
         // Channel data
         ctlv = searchForTag(ComprehensionTlvTag.CHANNEL_DATA, ctlvs);
         if (ctlv != null) {
             senddata.sendDataStr = IccUtils.bytesToHexString(ValueParser.retrieveByteArray(ctlv, 0));
         } else {
             StkLog.d(this, "SendData CHANNEL_DATA ctlv is null");
             throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
         }
         StkLog.d(this, "Alpha id: " + senddata.text + " senddata: " + senddata.sendDataStr);

         mCmdParams = new SendChannelDataParams(cmdDet, senddata, deviceIdentities);
         if (iconId != null) {
             mIconLoadState = LOAD_SINGLE_ICON;
             mIconLoader.loadIcon(iconId.recordNumber, this
                     .obtainMessage(MSG_ID_LOAD_ICON_DONE));
             return true;
         }
         return false;
     }

     private void processGetChannelStatus(CommandDetails cmdDet,
             List<ComprehensionTlv> ctlvs) throws ResultException {
         StkLog.d(this, "process GetChannelStatus");

         GetChannelStatus channelstatus = new GetChannelStatus();
         mCmdParams = new GetChannelStatusParams(cmdDet, channelstatus);
         return;
     }
}
