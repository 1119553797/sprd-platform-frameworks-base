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

package com.android.internal.telephony.cat;

/**
 * Interface for communication between STK App and CAT Telephony
 *
 * {@hide}
 */
public interface AppInterface {

    /*
     * Intent's actions which are broadcasted by the Telephony once a new CAT
     * proactive command, session end arrive.
     */
    public static final String CAT_CMD_ACTION =
                                    "android.intent.action.stk.command";
    public static final String CAT_SESSION_END_ACTION =
                                    "android.intent.action.stk.session_end";
    public static final String CAT_CMD_EVENT =
                                    "android.intent.action.stk.event";

    public static final int DEFAULT_CHANNELID = 0x01;
    /*
     * Callback function from app to telephony to pass a result code and user's
     * input back to the ICC.
     */
    void onCmdResponse(CatResponseMessage resMsg);

    /*
     * Callback function from app to telephony to pass a event message
     * back to the SIM.
     */
    void onEventResponse(CatResponseMessage resMsg);

    /*
     * Enumeration for representing "Type of Command" of proactive commands.
     * Those are the only commands which are supported by the Telephony. Any app
     * implementation should support those.
     * Refer to ETSI TS 102.223 section 9.4
     */
    public static enum CommandType {
        REFRESH(0x01),
        SET_UP_EVENT_LIST(0x05),
        SET_UP_CALL(0x10),
        SEND_SS(0x11),
        SEND_USSD(0x12),
        SEND_SMS(0x13),
        SEND_DTMF(0x14),
        LAUNCH_BROWSER(0x15),
        PLAY_TONE(0x20),
        DISPLAY_TEXT(0x21),
        GET_INKEY(0x22),
        GET_INPUT(0x23),
        SELECT_ITEM(0x24),
        SET_UP_MENU(0x25),
        PROVIDE_LOCAL_INFORMATION(0x26),
        SET_UP_IDLE_MODE_TEXT(0x28),
        //Language Setting Add Start
        LANGUAGE_NOTIFACTION(0x35),
        //Language Setting Add End
        OPEN_CHANNEL(0x40),
        CLOSE_CHANNEL(0x41),
        RECEIVE_DATA(0x42),
        SEND_DATA(0x43),
        GET_CHANNEL_STATUS(0x44);

        private int mValue;

        CommandType(int value) {
            mValue = value;
        }

        public int value() {
            return mValue;
        }

        /**
         * Create a CommandType object.
         *
         * @param value Integer value to be converted to a CommandType object.
         * @return CommandType object whose "Type of Command" value is {@code
         *         value}. If no CommandType object has that value, null is
         *         returned.
         */
        public static CommandType fromInt(int value) {
            for (CommandType e : CommandType.values()) {
                if (e.mValue == value) {
                    return e;
                }
            }
            return null;
        }
    }

    public static enum EventListType {
        Event_MTCall(0x00),              //terminal response OK
        Event_CallConnected(0x01),       //terminal response OK
        Event_CallDisconnected(0x02),    //terminal response OK
        Event_LocationStatus(0x03),      //terminal response OK
        Event_UserActivity(0x04),        //先回terminal response OK。当用户按键时，发envelope命令给AT,这是一次性的事件
        Event_IdleScreenAvailable(0x05), //先回terminal response OK。当屏幕空闲时，发envelope命令给AT,这是一次性的事件
        Event_CardReaderStatus(0x06),    //(if support class "a")terminal response OK
        Event_LanguageSelection(0x07),   //先回terminal response OK。当选择的语言发生变化时，发envelope命令给AT
        Event_BrowserTermination(0x08),  //先回terminal response OK。当浏览器关闭时，发envelope命令给AT
        Event_DataAvailable(0x09),       //(if support class "e")先回terminal response OK。当从网络端收到GPRS数据时，发envelope命令给AT
        Event_ChannelStatus(0x0a),       //(if support class "e")先回terminal response OK。当GPRS 通道状态发生变化时，发envelope命令给AT
        Event_Unknown(0xff);

        private int mValue;

        EventListType(int value) {
            mValue = value;
        }

        public int value() {
            return mValue;
        }
        public static EventListType fromInt(int value) {
            for (EventListType e : EventListType.values()) {
                if (e.mValue == value) {
                    return e;
                }
            }
            return null;
        }
    }

    public static enum NextActionInd {
        NAI_SET_UP_CALL(0x10),
        NAI_SEND_SS(0x11),
        NAI_SEND_USSD(0x12),
        NAI_SEND_SMS(0x13),
        NAI_PLAY_TONE(0x20),
        NAI_DISPLAY_TEXT(0x21),
        NAI_GET_INKEY(0x22),
        NAI_GET_INPUT(0x23),
        NAI_SELECT_ITEM(0x24),
        NAI_SET_UP_MENU(0x25),
        NAI_SET_UP_IDLE_MODE_TEXT(0x28),
        NAI_PERFORM_CARD_APDU(0x30),
        NAI_POWER_ON_CARD(0x31),
        NAI_POWER_OFF_CARD(0x32),
        NAI_GET_READER_STATUS(0x33),
        NAI_OPEN_CHANNEL(0x40),
        NAI_CLOSE_CHANNEL(0x41),
        NAI_RECEIVE_DATA(0x42),
        NAI_SEND_DATA(0x43),
        NAI_GET_CHANNEL_STATUS(0x44),
        NAI_PROVIDE_LOCAL_INFORMATION(0x26),
        NAI_LAUNCH_BROWSER(0x15),
        NAI_RESERVE_TIA_EIA_136(0x60);

        private int mValue;

        NextActionInd(int value) {
            mValue = value;
        }

        public int value() {
            return mValue;
        }

        public static NextActionInd fromInt(int value) {
            for (NextActionInd e : NextActionInd.values()) {
                if (e.mValue == value) {
                    return e;
                }
            }
            return null;
        }

        public static String NaiToString(NextActionInd nai)
        {
            if (nai == null) {
                return null;
            }
            switch(nai) {
            case NAI_SET_UP_CALL: return "Set Up Call";
            case NAI_SEND_SS: return "Send SS";
            case NAI_SEND_USSD: return "Send USSD";
            case NAI_SEND_SMS: return "Send Short Message";
            case NAI_PLAY_TONE: return "Play Tone";
            case NAI_DISPLAY_TEXT: return "Display Text";
            case NAI_GET_INKEY: return "Get INKEY";
            case NAI_GET_INPUT: return "Get INPUT";
            case NAI_SELECT_ITEM: return "Select Item";
            case NAI_SET_UP_MENU: return "Set Up Menu";
            case NAI_SET_UP_IDLE_MODE_TEXT: return "Set Up Idle Mode Text";
            case NAI_PERFORM_CARD_APDU: return "Perform Card APDU";
            case NAI_POWER_ON_CARD: return "Power On Card";
            case NAI_POWER_OFF_CARD: return "Power Off Card";
            case NAI_GET_READER_STATUS: return "Get Reader Status";
            case NAI_OPEN_CHANNEL: return "Open Channel";
            case NAI_CLOSE_CHANNEL: return "Close Channel";
            case NAI_RECEIVE_DATA: return "Receive Data";
            case NAI_SEND_DATA: return "Send Data";
            case NAI_GET_CHANNEL_STATUS: return "Get Channel Status";
            case NAI_RESERVE_TIA_EIA_136: return "Reserve TIA EIA 136";
            case NAI_PROVIDE_LOCAL_INFORMATION: return "Provide Local Information";
            case NAI_LAUNCH_BROWSER: return "Launch Brower";
            default:  return null;
            }
        }
    }
}
