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

import com.android.internal.telephony.cat.AppInterface.EventListType;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Class used to pass CAT messages from telephony to application. Application
 * should call getXXX() to get commands's specific values.
 *
 */
public class CatCmdMessage implements Parcelable {
    // members
    CommandDetails mCmdDet;
    private TextMessage mTextMsg;
    private Menu mMenu;
    private Input mInput;
    private BrowserSettings mBrowserSettings = null;
    private ToneSettings mToneSettings = null;
    private CallSettings mCallSettings = null;
   //Deal With DTMF Message Start
    private DtmfMessage mDtmfMessage;
    //Deal With DTMF Message End
    private EventListType[] mEventList = null;
    //Language Setting Add Start
    private LanguageMessage mLanguageMessage;
    //Language Setting Add End
    private OpenChannelData mOpenChannel;
    private CloseChannelData mCloseChannel;
    private ReceiveChannelData mReceiveData;
    private SendChannelData mSendData;
    private GetChannelStatus mChannelStatus;
    private DeviceIdentities mDeviceIdentities = null;

    /*
     * Container for Launch Browser command settings.
     */
    public class BrowserSettings {
        public String url;
        public LaunchBrowserMode mode;
    }

    /*
     * Container for Call Setup command settings.
     */
    public class CallSettings {
        public TextMessage confirmMsg;
        public TextMessage callMsg;
        public TextMessage calladdress;
    }

    CatCmdMessage(CommandParams cmdParams) {
        mCmdDet = cmdParams.cmdDet;
        switch(getCmdType()) {
        case SET_UP_MENU:
        case SELECT_ITEM:
            mMenu = ((SelectItemParams) cmdParams).menu;
            break;
        //Deal With DTMF Message Start
        case SEND_DTMF:
            mDtmfMessage = new DtmfMessage();
            mDtmfMessage.mdtmfString = ((DtmfParams)cmdParams).dtmfString;
            mTextMsg = ((DtmfParams)cmdParams).textMsg;
            break;
        //Deal With DTMF Message End
        case DISPLAY_TEXT:
        case SET_UP_IDLE_MODE_TEXT:
        case SEND_SMS:
        case SEND_SS:
        case SEND_USSD:
        case REFRESH:
            mTextMsg = ((DisplayTextParams) cmdParams).textMsg;
            break;
        case GET_INPUT:
        case GET_INKEY:
            mInput = ((GetInputParams) cmdParams).input;
            break;
        case LAUNCH_BROWSER:
            mTextMsg = ((LaunchBrowserParams) cmdParams).confirmMsg;
            mBrowserSettings = new BrowserSettings();
            mBrowserSettings.url = ((LaunchBrowserParams) cmdParams).url;
            mBrowserSettings.mode = ((LaunchBrowserParams) cmdParams).mode;
            break;
        case PLAY_TONE:
            PlayToneParams params = (PlayToneParams) cmdParams;
            mToneSettings = params.settings;
            mTextMsg = params.textMsg;
            break;
        case SET_UP_CALL:
            mCallSettings = new CallSettings();
            mCallSettings.confirmMsg = ((CallSetupParams) cmdParams).confirmMsg;
            mCallSettings.callMsg = ((CallSetupParams) cmdParams).callMsg;
            mCallSettings.calladdress = ((CallSetupParams) cmdParams).calladdress;
            break;
        case SET_UP_EVENT_LIST:
            mEventList = ((EventListParams) cmdParams).eventList;
            break;
        //Language Setting Add Start
        case LANGUAGE_NOTIFACTION:
            mLanguageMessage = new LanguageMessage();
            mLanguageMessage.languageString = ((LanguageParams)cmdParams).languageString;
            break;
        //Language Setting Add End
        case OPEN_CHANNEL:
            mOpenChannel = ((OpenChannelDataParams) cmdParams).openchanneldata;
            break;
        case CLOSE_CHANNEL:
            mCloseChannel = ((CloseChannelDataParams) cmdParams).closechanneldata;
            mDeviceIdentities = ((CloseChannelDataParams) cmdParams).deviceIdentities;
            break;
        case RECEIVE_DATA:
            mReceiveData = ((ReceiveChannelDataParams) cmdParams).receivedata;
            break;
        case SEND_DATA:
            mSendData = ((SendChannelDataParams) cmdParams).senddata;
            mDeviceIdentities = ((SendChannelDataParams) cmdParams).deviceIdentities;
            break;
        case GET_CHANNEL_STATUS:
            mChannelStatus = ((GetChannelStatusParams) cmdParams).channelstatus;
            break;
        }
    }

    public CatCmdMessage(Parcel in) {
        mCmdDet = in.readParcelable(null);
        mTextMsg = in.readParcelable(null);
        mMenu = in.readParcelable(null);
        mInput = in.readParcelable(null);
        switch (getCmdType()) {
        case LAUNCH_BROWSER:
            mBrowserSettings = new BrowserSettings();
            mBrowserSettings.url = in.readString();
            mBrowserSettings.mode = LaunchBrowserMode.values()[in.readInt()];
            break;
        case PLAY_TONE:
            mToneSettings = in.readParcelable(null);
            break;
        case SET_UP_CALL:
            mCallSettings = new CallSettings();
            mCallSettings.confirmMsg = in.readParcelable(null);
            mCallSettings.callMsg = in.readParcelable(null);
            mCallSettings.calladdress = in.readParcelable(null);
            break;
        //Deal With DTMF Message Start
        case SEND_DTMF:
            mDtmfMessage = in.readParcelable(null);
            break;
        //Deal With DTMF Message End
        //Language Setting Add Start
        case LANGUAGE_NOTIFACTION:
            mLanguageMessage = in.readParcelable(null);
            break;
        //Language Setting Add End
        case OPEN_CHANNEL:
            mOpenChannel = in.readParcelable(null);
            break;
        case CLOSE_CHANNEL:
            mCloseChannel = in.readParcelable(null);
            break;
        case RECEIVE_DATA:
            mReceiveData = in.readParcelable(null);
            break;
        case SEND_DATA:
            mSendData = in.readParcelable(null);
            break;
        case GET_CHANNEL_STATUS:
            mChannelStatus = in.readParcelable(null);
            break;
        }
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mCmdDet, 0);
        dest.writeParcelable(mTextMsg, 0);
        dest.writeParcelable(mMenu, 0);
        dest.writeParcelable(mInput, 0);
        switch(getCmdType()) {
        case LAUNCH_BROWSER:
            dest.writeString(mBrowserSettings.url);
            dest.writeInt(mBrowserSettings.mode.ordinal());
            break;
        case PLAY_TONE:
            dest.writeParcelable(mToneSettings, 0);
            break;
        case SET_UP_CALL:
            dest.writeParcelable(mCallSettings.confirmMsg, 0);
            dest.writeParcelable(mCallSettings.callMsg, 0);
            dest.writeParcelable(mCallSettings.calladdress, 0);
            break;
        //Deal With DTMF Message Start
        case SEND_DTMF:
            dest.writeParcelable(mDtmfMessage, 0);
            break;
        //Deal With DTMF Message End
        //Language Setting Add Start
        case LANGUAGE_NOTIFACTION:
            dest.writeParcelable(mLanguageMessage, 0);
            break;
        //Language Setting Add End
        case OPEN_CHANNEL:
            dest.writeParcelable(mOpenChannel, 0);
            break;
        case CLOSE_CHANNEL:
            dest.writeParcelable(mCloseChannel, 0);
            break;
        case RECEIVE_DATA:
            dest.writeParcelable(mReceiveData, 0);
            break;
        case SEND_DATA:
            dest.writeParcelable(mSendData, 0);
            break;
        case GET_CHANNEL_STATUS:
            dest.writeParcelable(mChannelStatus, 0);
            break;
        }
    }

    public static final Parcelable.Creator<CatCmdMessage> CREATOR = new Parcelable.Creator<CatCmdMessage>() {
        public CatCmdMessage createFromParcel(Parcel in) {
            return new CatCmdMessage(in);
        }

        public CatCmdMessage[] newArray(int size) {
            return new CatCmdMessage[size];
        }
    };

    public int describeContents() {
        return 0;
    }

    /* external API to be used by application */
    public AppInterface.CommandType getCmdType() {
        return AppInterface.CommandType.fromInt(mCmdDet.typeOfCommand);
    }

    public Menu getMenu() {
        return mMenu;
    }

    public Input geInput() {
        return mInput;
    }

    public TextMessage geTextMessage() {
        return mTextMsg;
    }

    public BrowserSettings getBrowserSettings() {
        return mBrowserSettings;
    }

    public ToneSettings getToneSettings() {
        return mToneSettings;
    }

    public CallSettings getCallSettings() {
        return mCallSettings;
    }
    //Deal With DTMF Message Start
    public DtmfMessage getDtmfMessage() {
        return mDtmfMessage;
    }
    //Deal With DTMF Message Start

    public AppInterface.EventListType[] getEventList() {
        return mEventList;
    }

    public CommandDetails getCmdDet() {
        return mCmdDet;
    }
    //Language Setting Add Start
    public LanguageMessage getLanguageMessage() {
        return mLanguageMessage;
    }
    //Language Setting Add End

    public OpenChannelData getOpenChannelData() {
        return mOpenChannel;
    }

    public CloseChannelData getCloseChannelData() {
        return mCloseChannel;
    }

    public ReceiveChannelData getReceiveChannelData() {
        return mReceiveData;
    }

    public SendChannelData getSendChannelData() {
        return mSendData;
    }

    public GetChannelStatus getChannelStatus() {
        return mChannelStatus;
    }

    public DeviceIdentities getDeviceIdentities() {
        return mDeviceIdentities;
    }
}
