/*
 * Copyright (C) 2010 The Android Open Source Project
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

#ifndef MY_HANDLER_H_

#define MY_HANDLER_H_

#define LOG_NDEBUG 0
#define LOG_TAG "MyHandler"
#include <utils/Log.h>

#include "APacketSource.h"
#include "ARTPConnection.h"
#include "ARTSPConnection.h"
#include "ASessionDescription.h"

#include <ctype.h>
#include <cutils/properties.h>

#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/ALooper.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/MediaErrors.h>

#include <arpa/inet.h>
#include <sys/socket.h>
#include <netdb.h>

// If no access units are received within 3 secs, assume that the rtp
// stream has ended and signal end of stream.
static int64_t kAccessUnitTimeoutUs = 5000000ll;//andrew modify 3sec to 5sec

// If no access units arrive for the first 10 secs after starting the
// stream, assume none ever will and signal EOS or switch transports.
static int64_t kStartupTimeoutUs = 10000000ll;

static int64_t kDefaultKeepAliveTimeoutUs = 60000000ll;


namespace android {

static void MakeUserAgentString(AString *s) {
    s->setTo("stagefright/1.1 (Linux;Android ");

#if (PROPERTY_VALUE_MAX < 8)
#error "PROPERTY_VALUE_MAX must be at least 8"
#endif

    char value[PROPERTY_VALUE_MAX];
    property_get("ro.build.version.release", value, "Unknown");
    s->append(value);
    s->append(")");
}

static bool GetAttribute(const char *s, const char *key, AString *value) {
    value->clear();

    size_t keyLen = strlen(key);

    for (;;) {
        while (isspace(*s)) {
            ++s;
        }

        const char *colonPos = strchr(s, ';');

        size_t len =
            (colonPos == NULL) ? strlen(s) : colonPos - s;

        if (len >= keyLen + 1 && s[keyLen] == '=' && !strncmp(s, key, keyLen)) {
            value->setTo(&s[keyLen + 1], len - keyLen - 1);
            return true;
        }

        if (colonPos == NULL) {
            return false;
        }

        s = colonPos + 1;
    }
}

struct MyHandler : public AHandler {
    MyHandler(const char *url, const sp<ALooper> &looper)
        : mLooper(looper),
          mNetLooper(new ALooper),
          mConn(new ARTSPConnection),
          mRTPConn(new ARTPConnection),
          mOriginalSessionURL(url),
          mSessionURL(url),
          mSetupTracksSuccessful(false),

	      mSeekPending(false),
		  mPauseed (false) ,
		  mPendingCmd(0),
          
          mNTPAnchorUs(-1),
          mMediaAnchorUs(-1),
          mLastMediaTimeUs(0),
	      mFirstAccessUnit(true),
	      mFirstAccessUnitNTP(0),
          mNumAccessUnitsReceived(0),
          mCheckPending(false),
          mCheckGeneration(0),
          mGenCmd(0),
          mTryTCPInterleaving(false),
          mTryFakeRTCP(false),
          mReceivedFirstRTCPPacket(false),
          mReceivedFirstRTPPacket(false),
          mServerException(false),
          mSeekable(false),
          
          mKeepAliveTimeoutUs(kDefaultKeepAliveTimeoutUs),
          mKeepAliveGeneration(0),
          mSeekingTime(0),
          mCmdSending(false),
          mDiscting(false),
          mlocalTimestamps(false){
          
		if (!strncasecmp("rtsp://127.0.0.1:8554/CMMBAudioVideo",mSessionURL.c_str(),35)) //@Hong. SpeedupCMMB
		{
			mRTPConn->setlocalTimestamps(true);
			mlocalTimestamps = true ;
		
	        mNetLooper->setName("rtsp net");
	        mNetLooper->start(false /* runOnCallingThread */,
	                          false /* canCallJava */,
	                          ANDROID_PRIORITY_AUDIO); //HIGHEST @hong
		}
		else
		{
		    mNetLooper->setName("rtsp net");
	        mNetLooper->start(false /* runOnCallingThread */,
	                          false /* canCallJava */,
	                          PRIORITY_HIGHEST); //HIGHEST @hong
		}
        // Strip any authentication info from the session url, we don't
        // want to transmit user/pass in cleartext.
        AString host, path, user, pass;
        unsigned port;
        CHECK(ARTSPConnection::ParseURL(
                    mSessionURL.c_str(), &host, &port, &path, &user, &pass));

        if (user.size() > 0) {
            mSessionURL.clear();
            mSessionURL.append("rtsp://");
            mSessionURL.append(host);
            mSessionURL.append(":");
            mSessionURL.append(StringPrintf("%u", port));
            mSessionURL.append(path);

            LOGI("rewritten session url: '%s'", mSessionURL.c_str());
        }

        mSessionHost = host;
    }

    void connect(const sp<AMessage> &doneMsg) {
        mDoneMsg = doneMsg;

        mLooper->registerHandler(this);
        mLooper->registerHandler(mConn);
        (1 ? mNetLooper : mLooper)->registerHandler(mRTPConn);

        sp<AMessage> notify = new AMessage('biny', id());
        mConn->observeBinaryData(notify);

        sp<AMessage> reply = new AMessage('conn', id());
        mConn->connect(mOriginalSessionURL.c_str(), reply);
	sp<AMessage> reply1 = new AMessage('expt', id());  //@handle server exception.
	mConn->serverexception(reply1);
	
	LOGI("connecting %s",mSessionURL.c_str());
    }

    void disconnect(const sp<AMessage> &doneMsg) {
        mDoneMsg = doneMsg;
		mDiscting = true ;
    	LOGI("disconnect.enter...");
        (new AMessage('abor', id()))->post();
    }

	void processPendCmd()
	{
		LOGI(" processPendCmd %d",mPendingCmd);
		switch (mPendingCmd)
		{
			case 1:
				seek(mSeekingTime,mPendDoneMsg);
				break;
			case 2:
				if(!mPauseed)
			      pause(-1,mPendDoneMsg);
			    break;
			case 3:
				if(mPauseed)
				{
			    	play(mResumeTime,mPendDoneMsg);
				}
				break;
		}
		mPendingCmd = 0;
			
	}

    void seek(int64_t timeUs, const sp<AMessage> &doneMsg) {
        sp<AMessage> msg = new AMessage('seek', id());

		LOGI(" myhand seek to %lld",timeUs);
		mSeekingTime = timeUs ;
	    if (mCmdSending){

		    LOGI("mCmdSending....so pend cmd.");
			mPendingCmd = 1;
			mSeekingTime = timeUs ;
			mPendDoneMsg = doneMsg;
			return ;
        }
		mCmdSending = true;

		for (size_t i = 0; i < mTracks.size(); ++i) {
            mTracks.editItemAt(i).mPacketSource->flushQueue();
        }
		sp<AMessage> reply1 = new AMessage('expt', id());  //@handle server exception.
		reply1->setMessage("doneMsg", doneMsg);
		mConn->serverexception(reply1);

		msg->setInt64("time", timeUs);
        msg->setMessage("doneMsg", doneMsg);
        msg->post(200000ll);
    }

    void pause(int64_t timeUs, const sp<AMessage> &doneMsg) {

	     LOGE("myhand pause.time %lld",timeUs);

		 if (mCmdSending)
		 {
			 LOGI("mCmdSending....so pend cmd.");
			 mPendingCmd = 2;
			 mPendDoneMsg = doneMsg;
		     return ;  
		 }
		mCmdSending = true ;
		sp<AMessage> msg = new AMessage('pause', id());
		
		sp<AMessage> reply1 = new AMessage('expt', id());  //@handle server exception.
		reply1->setMessage("doneMsg", doneMsg);
		mConn->serverexception(reply1);
		
        msg->setInt64("time", timeUs);
        msg->setMessage("doneMsg", doneMsg);
        msg->post(200000ll);
    }
   void play(int64_t timeUs, const sp<AMessage> &doneMsg) {
		    
		 if (mCmdSending)
		 {
		    LOGI("mCmdSending....so pend cmd.");
			mPendingCmd = 3;
			mResumeTime = timeUs ;
			mPendDoneMsg = doneMsg;
			return ;  
		 }
		 
		mCmdSending = true ;
		
	    sp<AMessage> msg = new AMessage('resume', id());
		
		sp<AMessage> reply1 = new AMessage('expt', id());  //@handle server exception.
		reply1->setMessage("doneMsg", doneMsg);
		mConn->serverexception(reply1);
        msg->setInt64("time", timeUs);
        msg->setMessage("doneMsg", doneMsg);
        msg->post(200000ll);
    }

    int64_t getNormalPlayTimeUs() {
        int64_t maxTimeUs = 0;
        for (size_t i = 0; i < mTracks.size(); ++i) {
            int64_t timeUs = mTracks.editItemAt(i).mPacketSource
                ->getNormalPlayTimeUs();

            if (i == 0 || timeUs > maxTimeUs) {
                maxTimeUs = timeUs;
            }
        }

        return maxTimeUs;
    }

    bool getSeekable() {
		 return true;
	}
	
    static void addRR(const sp<ABuffer> &buf) {
        uint8_t *ptr = buf->data() + buf->size();
        ptr[0] = 0x80 | 0;
        ptr[1] = 201;  // RR
        ptr[2] = 0;
        ptr[3] = 1;
        ptr[4] = 0xde;  // SSRC
        ptr[5] = 0xad;
        ptr[6] = 0xbe;
        ptr[7] = 0xef;

        buf->setRange(0, buf->size() + 8);
    }

    static void addSDES(int s, const sp<ABuffer> &buffer) {
        struct sockaddr_in addr;
        socklen_t addrSize = sizeof(addr);
        CHECK_EQ(0, getsockname(s, (sockaddr *)&addr, &addrSize));

        uint8_t *data = buffer->data() + buffer->size();
        data[0] = 0x80 | 1;
        data[1] = 202;  // SDES
        data[4] = 0xde;  // SSRC
        data[5] = 0xad;
        data[6] = 0xbe;
        data[7] = 0xef;

        size_t offset = 8;

        data[offset++] = 1;  // CNAME

        AString cname = "stagefright@";
        cname.append(inet_ntoa(addr.sin_addr));
        data[offset++] = cname.size();

        memcpy(&data[offset], cname.c_str(), cname.size());
        offset += cname.size();

        data[offset++] = 6;  // TOOL

        AString tool;
        MakeUserAgentString(&tool);

        data[offset++] = tool.size();

        memcpy(&data[offset], tool.c_str(), tool.size());
        offset += tool.size();

        data[offset++] = 0;

        if ((offset % 4) > 0) {
            size_t count = 4 - (offset % 4);
            switch (count) {
                case 3:
                    data[offset++] = 0;
                case 2:
                    data[offset++] = 0;
                case 1:
                    data[offset++] = 0;
            }
        }

        size_t numWords = (offset / 4) - 1;
        data[2] = numWords >> 8;
        data[3] = numWords & 0xff;

        buffer->setRange(buffer->offset(), buffer->size() + offset);
    }

    // In case we're behind NAT, fire off two UDP packets to the remote
    // rtp/rtcp ports to poke a hole into the firewall for future incoming
    // packets. We're going to send an RR/SDES RTCP packet to both of them.
    bool pokeAHole(int rtpSocket, int rtcpSocket, const AString &transport) {
        struct sockaddr_in addr;
        memset(addr.sin_zero, 0, sizeof(addr.sin_zero));
        addr.sin_family = AF_INET;

        AString source;
        AString server_port;
        if (!GetAttribute(transport.c_str(),
                          "source",
                          &source)) {
            LOGW("Missing 'source' field in Transport response. Using "
                 "RTSP endpoint address.");

            struct hostent *ent = gethostbyname(mSessionHost.c_str());
            if (ent == NULL) {
                LOGE("Failed to look up address of session host '%s'",
                     mSessionHost.c_str());

                return false;
            }

            addr.sin_addr.s_addr = *(in_addr_t *)ent->h_addr;
        } else {
            addr.sin_addr.s_addr = inet_addr(source.c_str());
        }

        if (!GetAttribute(transport.c_str(),
                                 "server_port",
                                 &server_port)) {
            LOGI("Missing 'server_port' field in Transport response.");
            return false;
        }

        int rtpPort, rtcpPort;
        if (sscanf(server_port.c_str(), "%d-%d", &rtpPort, &rtcpPort) != 2
                || rtpPort <= 0 || rtpPort > 65535
                || rtcpPort <=0 || rtcpPort > 65535
                || rtcpPort != rtpPort + 1) {
            LOGE("Server picked invalid RTP/RTCP port pair %s,"
                 " RTP port must be even, RTCP port must be one higher.",
                 server_port.c_str());

            return false;
        }

        if (rtpPort & 1) {
            LOGW("Server picked an odd RTP port, it should've picked an "
                 "even one, we'll let it pass for now, but this may break "
                 "in the future.");
        }

        if (addr.sin_addr.s_addr == INADDR_NONE) {
            return true;
        }

        if (IN_LOOPBACK(ntohl(addr.sin_addr.s_addr))) {
            // No firewalls to traverse on the loopback interface.
            return true;
        }

        // Make up an RR/SDES RTCP packet.
        sp<ABuffer> buf = new ABuffer(65536);
        buf->setRange(0, 0);
        addRR(buf);
        addSDES(rtpSocket, buf);

        addr.sin_port = htons(rtpPort);

        ssize_t n = sendto(
                rtpSocket, buf->data(), buf->size(), 0,
                (const sockaddr *)&addr, sizeof(addr));

        if (n < (ssize_t)buf->size()) {
            LOGE("failed to poke a hole for RTP packets");
            return false;
        }

        addr.sin_port = htons(rtcpPort);

        n = sendto(
                rtcpSocket, buf->data(), buf->size(), 0,
                (const sockaddr *)&addr, sizeof(addr));

        if (n < (ssize_t)buf->size()) {
            LOGE("failed to poke a hole for RTCP packets");
            return false;
        }

        LOGV("successfully poked holes.");

        return true;
    }

    virtual void onMessageReceived(const sp<AMessage> &msg) {
	AString ua;
        switch (msg->what()) {
            case 'conn':
            {
                int32_t result;
                CHECK(msg->findInt32("result", &result));
	    struct timeval tv;  //@hong add timecheck.
	    gettimeofday(&tv, NULL);
                LOGI("connection request completed time %d with result %d (%s)", tv.tv_sec,
                     result, strerror(-result));

                if (result == OK) {
                    AString request;
                    request = "DESCRIBE ";
                    request.append(mSessionURL);
                    request.append(" RTSP/1.0\r\n");
                    request.append("Accept: application/sdp\r\n");
					

                    request.append("\r\n");

                    sp<AMessage> reply = new AMessage('desc', id());
                    mConn->sendRequest(request.c_str(), reply);
                } else {
                    (new AMessage('disc', id()))->post();
                }
                break;
            }

            case 'disc':
            {
                int32_t reconnect;
                ++mKeepAliveGeneration;

				if (msg->findInt32("reconnect", &reconnect) && reconnect) {
                    sp<AMessage> reply = new AMessage('conn', id());
			        LOGI("dic for reconnect");
                    mConn->connect(mOriginalSessionURL.c_str(), reply);
                } else {
                	 LOGI("dic for quit ");
                    (new AMessage('quit', id()))->post();
                }
                break;
            }

            case 'desc':
            {
                int32_t result;
                CHECK(msg->findInt32("result", &result));
	    struct timeval tv;
	    gettimeofday(&tv, NULL);
	LOGI("describle time:%d ms", tv.tv_sec*1000+tv.tv_usec/1000);

                LOGI("DESCRIBE completed with result %d (%s)",
                     result, strerror(-result));

                if (result == OK) {
                    sp<RefBase> obj;
                    CHECK(msg->findObject("response", &obj));
                    sp<ARTSPResponse> response =
                        static_cast<ARTSPResponse *>(obj.get());

                    if (response->mStatusCode == 302) {
                        ssize_t i = response->mHeaders.indexOfKey("location");
                        CHECK_GE(i, 0);

                        mSessionURL = response->mHeaders.valueAt(i);

                        AString request;
                        request = "DESCRIBE ";
                        request.append(mSessionURL);
                        request.append(" RTSP/1.0\r\n");
                        request.append("Accept: application/sdp\r\n");

                        request.append("\r\n");

                        sp<AMessage> reply = new AMessage('desc', id());
                        mConn->sendRequest(request.c_str(), reply);
                        break;
                    }

                    if (response->mStatusCode != 200) {
                        result = UNKNOWN_ERROR;
                    } else {
                        mSessionDesc = new ASessionDescription;

                        mSessionDesc->setTo(
                                response->mContent->data(),
                                response->mContent->size());

                        if (!mSessionDesc->isValid()) {
                            LOGE("Failed to parse session description.");
                            result = ERROR_MALFORMED;
                        } else {
                            ssize_t i = response->mHeaders.indexOfKey("content-base");
                            if (i >= 0) {
                                mBaseURL = response->mHeaders.valueAt(i);
                            } else {
                                i = response->mHeaders.indexOfKey("content-location");
                                if (i >= 0) {
                                    mBaseURL = response->mHeaders.valueAt(i);
                                } else {
                                    mBaseURL = mSessionURL;
                                }
                            }

                            if (!mBaseURL.startsWith("rtsp://")) {
                                // Some misbehaving servers specify a relative
                                // URL in one of the locations above, combine
                                // it with the absolute session URL to get
                                // something usable...

                                LOGW("Server specified a non-absolute base URL"
                                     ", combining it with the session URL to "
                                     "get something usable...");

                                AString tmp;
                                CHECK(MakeURL(
                                            mSessionURL.c_str(),
                                            mBaseURL.c_str(),
                                            &tmp));

                                mBaseURL = tmp;
                            }

                            CHECK_GT(mSessionDesc->countTracks(), 1u);
                            setupTrack(1);
                        }
                    }
                }

                if (result != OK) {
                    sp<AMessage> reply = new AMessage('disc', id());
                    mConn->disconnect(reply);
                }
                break;
            }

            case 'setu':
            {
                size_t index;
                CHECK(msg->findSize("index", &index));
	    struct timeval tv;
	    gettimeofday(&tv, NULL);
	LOGI("setup time:%d ms", tv.tv_sec*1000+tv.tv_usec/1000);

                TrackInfo *track = NULL;
                size_t trackIndex;
                if (msg->findSize("track-index", &trackIndex)) {
                    track = &mTracks.editItemAt(trackIndex);
                }

                int32_t result;
                CHECK(msg->findInt32("result", &result));

                LOGI("SETUP(%d) completed with result %d (%s)",
                     index, result, strerror(-result));

                if (result == OK) {
                    CHECK(track != NULL);

                    sp<RefBase> obj;
                    CHECK(msg->findObject("response", &obj));
                    sp<ARTSPResponse> response =
                        static_cast<ARTSPResponse *>(obj.get());

                    if (response->mStatusCode != 200) {
                        result = UNKNOWN_ERROR;
                    } else {
                        ssize_t i = response->mHeaders.indexOfKey("session");
                        CHECK_GE(i, 0);

                        mSessionID = response->mHeaders.valueAt(i);
						
                        mKeepAliveTimeoutUs = kDefaultKeepAliveTimeoutUs;

						AString timeoutStr;
                        if (GetAttribute(
                                    mSessionID.c_str(), "timeout", &timeoutStr)) {
                            char *end;
                            unsigned long timeoutSecs =
                                strtoul(timeoutStr.c_str(), &end, 10);

                            if (end == timeoutStr.c_str() || *end != '\0') {
                                LOGW("server specified malformed timeout '%s'",
                                     timeoutStr.c_str());

                                mKeepAliveTimeoutUs = kDefaultKeepAliveTimeoutUs;
                            } else if (timeoutSecs < 15) {
                                LOGW("server specified too short a timeout "
                                     "(%lu secs), using default.",
                                     timeoutSecs);

                                mKeepAliveTimeoutUs = kDefaultKeepAliveTimeoutUs;
                            } else {
                                mKeepAliveTimeoutUs = timeoutSecs * 1000000ll;

                                LOGI("server specified timeout of %lu secs.",
                                     timeoutSecs);
                            }
                        }
						
                        i = mSessionID.find(";");
                        if (i >= 0) {
                            // Remove options, i.e. ";timeout=90"
                            mSessionID.erase(i, mSessionID.size() - i);
                        }

                        sp<AMessage> notify = new AMessage('accu', id());
                        notify->setSize("track-index", trackIndex);

                        i = response->mHeaders.indexOfKey("transport");
                        CHECK_GE(i, 0);

                        if (!track->mUsingInterleavedTCP) {
                            AString transport = response->mHeaders.valueAt(i);

                            // We are going to continue even if we were
                            // unable to poke a hole into the firewall...
                            pokeAHole(
                                    track->mRTPSocket,
                                    track->mRTCPSocket,
                                    transport);
                        }

                        mRTPConn->addStream(
                                track->mRTPSocket, track->mRTCPSocket,
                                mSessionDesc, index,
                                notify, track->mUsingInterleavedTCP);

                        mSetupTracksSuccessful = true;
                    }
                }

                if (result != OK) {
                    if (track) {
                        if (!track->mUsingInterleavedTCP) {
                            close(track->mRTPSocket);
                            close(track->mRTCPSocket);
                        }

                        mTracks.removeItemsAt(trackIndex);
                    }
                }

                ++index;
                if (index < mSessionDesc->countTracks()) {
                    setupTrack(index);
                } else if (mSetupTracksSuccessful) {

				    ++mKeepAliveGeneration;
                    postKeepAlive();
					
                    AString request = "PLAY ";
                    request.append(mSessionURL);
                    request.append(" RTSP/1.0\r\n");

                    request.append("Session: ");
                    request.append(mSessionID);
                    request.append("\r\n");
			
                    request.append("\r\n");

                    sp<AMessage> reply = new AMessage('play', id());
                    mConn->sendRequest(request.c_str(), reply);
                } else {
                    sp<AMessage> reply = new AMessage('disc', id());
                    mConn->disconnect(reply);
                }
                break;
            }

            case 'play':
            {
                int32_t result;
                CHECK(msg->findInt32("result", &result));
	    struct timeval tv;
	    gettimeofday(&tv, NULL);
	LOGI("play time:%d ms", tv.tv_sec*1000+tv.tv_usec/1000);
                LOGI("PLAY completed with result %d (%s)",
                     result, strerror(-result));

                if (result == OK) {
                    sp<RefBase> obj;
                    CHECK(msg->findObject("response", &obj));
                    sp<ARTSPResponse> response =
                        static_cast<ARTSPResponse *>(obj.get());

                    if (response->mStatusCode != 200) {
                        result = UNKNOWN_ERROR;
                    } else {
                        parsePlayResponse(response);

                        sp<AMessage> timeout = new AMessage('tiou', id());
			if (!strncasecmp("rtsp://127.0.0.1:8554/CMMBAudioVideo",mSessionURL.c_str(),35)) //@Hong. SpeedupCMMB
                        timeout->post(kStartupTimeoutUs/4);  //only use 2sec.
			else			
                        timeout->post(kStartupTimeoutUs);
                    }
                }

                if (result != OK) {
                    sp<AMessage> reply = new AMessage('disc', id());
                    mConn->disconnect(reply);
                }

                break;
            }
			case 'aliv':
			   {
				   int32_t generation;
				   CHECK(msg->findInt32("generation", &generation));
			
				   if (generation != mKeepAliveGeneration) {
					   // obsolete event.
					   LOGI("aliv obsolete event.");
					   break;
				   }
			
				   AString request;
				   request.append("OPTIONS ");
				   request.append(mSessionURL);
				   request.append(" RTSP/1.0\r\n");
				   request.append("Session: ");
				   request.append(mSessionID);
				   request.append("\r\n");
				   request.append("\r\n");
			
				   sp<AMessage> reply = new AMessage('opts', id());
				   reply->setInt32("generation", mKeepAliveGeneration);
				   mConn->sendRequest(request.c_str(), reply);
				   
				   break;
			   }
			
			   case 'opts':
			   {
				   int32_t result;
				   CHECK(msg->findInt32("result", &result));
			
				   LOGI("OPTIONS completed with result %d (%s)",
						result, strerror(-result));
			
				   int32_t generation;
				   CHECK(msg->findInt32("generation", &generation));
			
				   if (generation != mKeepAliveGeneration) {
					   // obsolete event.
					   LOGI("OPTIONS obsolete event.");
					   break;
				   }
				   postKeepAlive();
				   break;
			   }

            case 'abor':
            {
		LOGI("abor received...");
                for (size_t i = 0; i < mTracks.size(); ++i) {
                    TrackInfo *info = &mTracks.editItemAt(i);
				    if (!mFirstAccessUnit) {
                    info->mPacketSource->signalEOS(ERROR_END_OF_STREAM);
				    }

                    if (!info->mUsingInterleavedTCP) {
                        mRTPConn->removeStream(info->mRTPSocket, info->mRTCPSocket);

                        close(info->mRTPSocket);
                        close(info->mRTCPSocket);
                    }
                }
                mTracks.clear();
                mSetupTracksSuccessful = false;
                mSeekPending = false;
                mCheckPending = false ;
                mFirstAccessUnit = true;
                mNTPAnchorUs = -1;
                mMediaAnchorUs = -1;
				mSeekingTime = 0 ;
                mFirstAccessUnitNTP = 0;
                mNumAccessUnitsReceived = 0;
                mReceivedFirstRTCPPacket = false;
                mReceivedFirstRTPPacket = false;
                mSeekable = false;

                sp<AMessage> reply = new AMessage('tear', id());

                int32_t reconnect;
                if (msg->findInt32("reconnect", &reconnect) && reconnect) {
                    reply->setInt32("reconnect", true);
                }
				else
				{
				    mCheckPending = true;
					mCheckGeneration++ ;
				}

                AString request;
                request = "TEARDOWN ";

                // XXX should use aggregate url from SDP here...
                request.append(mSessionURL);
                request.append(" RTSP/1.0\r\n");

                request.append("Session: ");
                request.append(mSessionID);
                request.append("\r\n");

		
                request.append("\r\n");

                mConn->sendRequest(request.c_str(), reply);
                if(mDiscting)
                {
				  mConn->disconnect(reply);
			  	  mDoneMsg->post();
				  LOGI("fast disc");
                }
		    	LOGI("abor over sending teardown...");
				
                break;
            }

            case 'tear':
            {
                int32_t result;
		LOGI(" teardown enter...");
                CHECK(msg->findInt32("result", &result));
				mCmdSending = false ;

                LOGI("TEARDOWN completed with result %d (%s)",
                     result, strerror(-result));

                sp<AMessage> reply = new AMessage('disc', id());

                int32_t reconnect;
                if (msg->findInt32("reconnect", &reconnect) && reconnect) {
                    reply->setInt32("reconnect", true);
                }

                mConn->disconnect(reply);
		LOGI(" teardown over");
                break;
            }

            case 'quit':
            {
                if (mDoneMsg != NULL) {
					LOGI("quit done  for UNKNOWN_ERROR");
                    mDoneMsg->setInt32("result", UNKNOWN_ERROR);
                    if(mlocalTimestamps)
					{
					   mDoneMsg->post();
					}
					else
					{
  					  // mDoneMsg->post(4000000ll);
  					    mDoneMsg->post();
					}               
                 
                    mDoneMsg = NULL;
                }
			    break;
            }

            case 'chek':
            {
                int32_t generation;
                CHECK(msg->findInt32("generation", &generation));
                if (generation != mCheckGeneration) {
                     break;
                }

                if (mNumAccessUnitsReceived == 0 ){  

                    LOGI("stream ended? aborting.");
                   (new AMessage('abor', id()))->post();
                    break;
                }

                mNumAccessUnitsReceived = 0;
                msg->post(kAccessUnitTimeoutUs);
                break;
            }
			
			case 'chekcmd':
			{	 
			   int32_t gencmd;

			   if(!mCmdSending)
			   {
				   LOGW("mCmdSending. return.");
				   return ;
			   }
			   CHECK(msg->findInt32("gencmd", &gencmd));
               if (gencmd != mGenCmd) {
                    // This is an outdated message. Ignore.
                    LOGW("This is an outdated message. Ignore.");
                    break;
               }
			   LOGW("cmdtiou, disconnecting.");
			   sp<AMessage> doneMsg = NULL;
               msg->findMessage("doneMsg", &doneMsg); 
			   sp<AMessage> reply = new AMessage('disc', id());
               if(doneMsg != NULL)
               {
			   	reply->setMessage("doneMsg", doneMsg);
               }
               mConn->disconnect(reply);
			   return ;
			}

            case 'accu':
            {
                int32_t timeUpdate;
                if (msg->findInt32("time-update", &timeUpdate) && timeUpdate) {
                    size_t trackIndex;
                    CHECK(msg->findSize("track-index", &trackIndex));

                    uint32_t rtpTime;
                    uint64_t ntpTime;
                    CHECK(msg->findInt32("rtp-time", (int32_t *)&rtpTime));
                    CHECK(msg->findInt64("ntp-time", (int64_t *)&ntpTime));

                   // onTimeUpdate(trackIndex, rtpTime, ntpTime);
                    break;
                }

                int32_t first;

		//LOGI("accu received");
		
                if (msg->findInt32("first-rtcp", &first)) {
                    mReceivedFirstRTCPPacket = true;
			LOGI("accu received first rtcp");
	    struct timeval tv;
	    gettimeofday(&tv, NULL);
	LOGI("accu firstrtcp time:%d ms", tv.tv_sec*1000+tv.tv_usec/1000);
			
                    break;
                }

                if (msg->findInt32("first-rtp", &first)) {
                    mReceivedFirstRTPPacket = true;
			LOGI("accu received first rtp");
	    struct timeval tv;
	    gettimeofday(&tv, NULL);
	LOGI("accu firstrtp time:%d ms", tv.tv_sec*1000+tv.tv_usec/1000);
                    break;
                }

                ++mNumAccessUnitsReceived;
                postAccessUnitTimeoutCheck();

                size_t trackIndex;
                CHECK(msg->findSize("track-index", &trackIndex));

                if (trackIndex >= mTracks.size()) {
                    LOGI("late packets ignored.");
                    break;
                }

                TrackInfo *track = &mTracks.editItemAt(trackIndex);

                int32_t eos;
                if (msg->findInt32("eos", &eos)) {
                    LOGI("received BYE on track index %d", trackIndex);
#if 0
                    track->mPacketSource->signalEOS(ERROR_END_OF_STREAM);
#endif
                    return;
                }

                sp<RefBase> obj;
                CHECK(msg->findObject("access-unit", &obj));

                sp<ABuffer> accessUnit = static_cast<ABuffer *>(obj.get());

                uint32_t seqNum = (uint32_t)accessUnit->int32Data();

                if (mSeekPending) {
                    LOGI("we're seeking, dropping stale packet.");
                    break;
                }

                if (seqNum < track->mFirstSeqNumInSegment) {
                    LOGI("dropping stale access-unit (%d < %d)",
                         seqNum, track->mFirstSeqNumInSegment);
                    break;
                }

                uint64_t ntpTime;
                CHECK(accessUnit->meta()->findInt64(
                            "ntp-time", (int64_t *)&ntpTime));

                uint32_t rtpTime;
                CHECK(accessUnit->meta()->findInt32(
                            "rtp-time", (int32_t *)&rtpTime));

                if (track->mNewSegment) {
                    track->mNewSegment = false;

		if (!strncasecmp("rtsp://127.0.0.1:8554/CMMBAudioVideo",mSessionURL.c_str(),35)) //@Hong. SpeedupCMMB					
		mReceivedFirstRTCPPacket = true;	//@hong
//	    struct timeval tv;
//	    gettimeofday(&tv, NULL);
//	LOGI("first segment unit  time:%d ms", tv.tv_sec*1000+tv.tv_usec/1000);
	
                    LOGI("first segment unit ntpTime=0x%016llx rtpTime=%u seq=%d",
                         ntpTime, rtpTime, seqNum);
                }
			    
                if(!mlocalTimestamps)
				{
				   onAccessUnitComplete(trackIndex, accessUnit);
				   return ;
				}
                if (mFirstAccessUnit) {
                    mDoneMsg->setInt32("result", OK);
                    mDoneMsg->post();
                    mDoneMsg = NULL;
                    mFirstAccessUnit = false;
                    mFirstAccessUnitNTP = ntpTime;
		            mConn->serverexception(NULL);  //@hong
                }

                if (ntpTime >= mFirstAccessUnitNTP) {
                    ntpTime -= mFirstAccessUnitNTP;
                } else {
                    ntpTime = 0;
                }

                int64_t timeUs = (int64_t)(ntpTime * 1E6 / (1ll << 32));

                accessUnit->meta()->setInt64("timeUs", timeUs);

#if 0
                int32_t damaged;
                if (accessUnit->meta()->findInt32("damaged", &damaged)
                        && damaged != 0) {
                    LOGI("ignoring damaged AU");
                } else
#endif
                {
                    TrackInfo *track = &mTracks.editItemAt(trackIndex);
                    track->mPacketSource->queueAccessUnit(accessUnit);
                }
                break;
            }

            case 'seek':
            {
                sp<AMessage> doneMsg;
                CHECK(msg->findMessage("doneMsg", &doneMsg));

                if (!mSeekable || mPauseed) {
                    LOGI("This is a live stream or stream mPauseed , seek fake.mPauseed ",mPauseed);
				 	mSeekPending = false;
				    mCmdSending = false ;
				    doneMsg->setInt32("result", NO_ERROR);
                    doneMsg->post();
				    if(mPendingCmd != 0)
				    {
				      processPendCmd();
				    }
                    break;
                }

                int64_t timeUs;
                CHECK(msg->findInt64("time", &timeUs));
               // Disable the access unit timeout until we resumed
                // playback again.
                 mSeekPending = true;
			   
                 mCheckPending = true;
                ++mCheckGeneration;

                AString request = "PAUSE ";
                request.append(mSessionURL);
                request.append(" RTSP/1.0\r\n");

                request.append("Session: ");
                request.append(mSessionID);
                request.append("\r\n");
                request.append("\r\n");

      
                sp<AMessage> reply = new AMessage('see1', id());
                reply->setInt64("time", timeUs);
                reply->setMessage("doneMsg", doneMsg);
				if (!mPauseed)
				{

					LOGI("seek when playing.first pause rtsp to %lld",timeUs);
					mConn->sendRequest(request.c_str(), reply);
				}
				else
				{
			       LOGI("seeking when pause ,seek fake");
				   
				   mSeekPending = false;
				   mCmdSending = false ;
             	   doneMsg->setInt32("result", NO_ERROR);
		           doneMsg->post();
				   if(mPendingCmd != 0)
				   {
				      processPendCmd();
				   }
				   
				}
				
                break;
            }

            case 'see1':
            {
                // Session is paused now.
        #if 0        
                for (size_t i = 0; i < mTracks.size(); ++i) {
                    TrackInfo *info = &mTracks.editItemAt(i);
                   // info->mRTPAnchor = 0;
                  //  info->mNTPAnchorUs = -1;
                } 
				mNTPAnchorUs = -1;
		#endif		
       	    	LOGI("seek have paused then play ");
                   int32_t result;
			   CHECK(msg->findInt32("result", &result));
      
      			 LOGE("pause1 completed with result %d (%s) mPendingCmd %d",
      				  result, strerror(-result),mPendingCmd);
			    int64_t timeUs;
                CHECK(msg->findInt64("time", &timeUs));
			
                AString request = "PLAY ";
                request.append(mSessionURL);
                request.append(" RTSP/1.0\r\n");

                request.append("Session: ");
                request.append(mSessionID);
                request.append("\r\n");

                request.append(
                        StringPrintf(
                            "Range: npt=%lld-\r\n", timeUs / 1000000ll));

                request.append("\r\n");

                sp<AMessage> doneMsg;
                CHECK(msg->findMessage("doneMsg", &doneMsg)); 

                sp<AMessage> reply = new AMessage('see2', id());
                reply->setMessage("doneMsg", doneMsg);
                mConn->sendRequest(request.c_str(), reply);
			    break;
            }

            case 'see2':
            {
                CHECK(mSeekPending);

                int32_t result;
                CHECK(msg->findInt32("result", &result));

                LOGI("seek PLAY completed with result %d (%s)",
                     result, strerror(-result));
	            mCheckPending = false;
                postAccessUnitTimeoutCheck();

                if (result == OK) {
                    sp<RefBase> obj;
                    CHECK(msg->findObject("response", &obj));
                    sp<ARTSPResponse> response =
                        static_cast<ARTSPResponse *>(obj.get());

                    if (response->mStatusCode != 200) {
                        result = UNKNOWN_ERROR;
                    } else {
                        parsePlayResponse(response);

                        LOGI("seek completed.");
                    }
                }
				sp<AMessage> doneMsg;
                CHECK(msg->findMessage("doneMsg", &doneMsg));

                if (result != OK) {
                       LOGE("seek failed, aborting.");
               	       mDoneMsg = doneMsg;
	                  (new AMessage('abor', id()))->post();
		     		   mSeekPending = false;
					   return ;
                }

				sp<AMessage> seedMess = new AMessage('seeD', id());  //@handle server exception.
				seedMess->setMessage("doneMsg", doneMsg);
			    mSeekPending = false;
				seedMess->post(4000000ll);
			    break;
            }
			
			case 'seeD':
			{
			      LOGI("seek completed. processPendCmd %d",mPendingCmd);
				  sp<AMessage> doneMsg;
				  CHECK(msg->findMessage("doneMsg", &doneMsg));
				  //mSeekPending = false;
				  mCmdSending = false ;
				  doneMsg->setInt32("result", NO_ERROR);
		          doneMsg->post();
				  if(mPendingCmd != 0)
				  {
				     processPendCmd();
				  }
				  break ;
			}
				

			

			 case 'pause':
			 {
			    sp<AMessage> doneMsg;
                CHECK(msg->findMessage("doneMsg", &doneMsg));
				// Session is paused now.
		        if (!mSeekable || mPauseed ) {
                    LOGE("This is a live stream or stream paused, pause fake mPauseed %d",mPauseed);
				    doneMsg->setInt32("result", NO_ERROR);
                    doneMsg->post();
	                if(mPendingCmd != 0)
				    {
				     processPendCmd();
				    }
                    break;
                }

                int64_t timeUs;
                CHECK(msg->findInt64("time", &timeUs));

				LOGE("pause.time %lld",timeUs);

                // Disable the access unit timeout until we resumed
                // playback again.
                mCheckPending = true;
                ++mCheckGeneration;

                AString request = "PAUSE ";
                request.append(mSessionURL);
                request.append(" RTSP/1.0\r\n");

                request.append("Session: ");
                request.append(mSessionID);
                request.append("\r\n");
				
				request.append("\r\n");
        
                sp<AMessage> reply = new AMessage('pause1', id());
                reply->setInt64("time", timeUs);
                reply->setMessage("doneMsg", doneMsg);
				mConn->sendRequest(request.c_str(), reply);
	            break;
			 }

			 case 'pause1':
			 {
				 int32_t result;
				 CHECK(msg->findInt32("result", &result));
      
      			 LOGE("pause1 completed with result %d (%s) mPendingCmd %d",
      				  result, strerror(-result),mPendingCmd);

			     mCmdSending = false ;
				 mPauseed = true ;
				 sp<AMessage> doneMsg;
      			 CHECK(msg->findMessage("doneMsg", &doneMsg));
				 
				 if (result != OK) {
                   	  LOGE("pause1 fail, aborting.");
                 	  mDoneMsg = doneMsg;
	                 (new AMessage('abor', id()))->post();
					  break;
                 }
				 doneMsg->setInt32("result", NO_ERROR);
				 doneMsg->post();
		
				 if(mPendingCmd != 0)
				 {
				   processPendCmd();
				 }
			     break;
			 }
			 

			 case 'resume':
			 {

				 int64_t timeUs;
				 sp<AMessage> doneMsg;
			     CHECK(msg->findMessage("doneMsg", &doneMsg));

                if (!mSeekable ||!mPauseed) {
                    LOGE("This is a live stream or stream not paused, resume fake mPauseed %d",mPauseed);
				    mCmdSending = false ;
				    doneMsg->setInt32("result", NO_ERROR);
                    doneMsg->post();
				    if(mPendingCmd != 0)
					{
				 	  processPendCmd();
					}
					
                    break;
                }

				CHECK(msg->findInt64("time", &timeUs));
				LOGE("resume timeUs %lld",timeUs);
                AString request = "PLAY ";
                request.append(mSessionURL);
                request.append(" RTSP/1.0\r\n");
				
                request.append("Session: ");
                request.append(mSessionID);
                request.append("\r\n");

                request.append(
                        StringPrintf(
                            "Range: npt=%lld-\r\n", timeUs / 1000000ll));

                request.append("\r\n");

                sp<AMessage> reply = new AMessage('resume1', id());
                reply->setMessage("doneMsg", doneMsg);
                mConn->sendRequest(request.c_str(), reply);
			
                break;
			 }
			 case 'resume1':
			 {
                int32_t result;
                CHECK(msg->findInt32("result", &result));

                LOGE("resume PLAY completed with result %d (%s)",
                     result, strerror(-result));
				mCmdSending = false;

				mPauseed = false ;
				mCheckPending = false;
                postAccessUnitTimeoutCheck();

			    if (result == OK) {
                    sp<RefBase> obj;
                    CHECK(msg->findObject("response", &obj));
                    sp<ARTSPResponse> response =
                        static_cast<ARTSPResponse *>(obj.get());

                    if (response->mStatusCode != 200) {
                        result = UNKNOWN_ERROR;
                    } else {
                        parsePlayResponse(response);

                        LOGI("resume completed.");
                    }
                }
			    sp<AMessage> doneMsg;
                CHECK(msg->findMessage("doneMsg", &doneMsg));

				if (result != OK) {
                   	  LOGE("resume1 fail, aborting.");
                 	  mDoneMsg = doneMsg;
	                 (new AMessage('abor', id()))->post();
					  break;
                }
			    doneMsg->setInt32("result", NO_ERROR);
			    doneMsg->post();
			    if(mPendingCmd != 0)
				{
				   processPendCmd();
				}
			    break;
			 }

            case 'biny':
            {
                sp<RefBase> obj;
                CHECK(msg->findObject("buffer", &obj));
                sp<ABuffer> buffer = static_cast<ABuffer *>(obj.get());

                int32_t index;
                CHECK(buffer->meta()->findInt32("index", &index));
				LOGI("mRTPConn->injectPacket");
                mRTPConn->injectPacket(index, buffer);
                break;
            }

            case 'tiou':
            {
                if (!mReceivedFirstRTCPPacket) {
                    if (mReceivedFirstRTPPacket && !mTryFakeRTCP) {
                        LOGW("We received RTP packets but no RTCP packets, "
                             "using fake timestamps.");

                        mTryFakeRTCP = true;

                        mReceivedFirstRTCPPacket = true;

                        fakeTimestamps();
                    } else if (!mReceivedFirstRTPPacket && !mTryTCPInterleaving) {
                        LOGW("Never received any data, switching transports.");

                        mTryTCPInterleaving = true;

                        sp<AMessage> msg = new AMessage('abor', id());
                        msg->setInt32("reconnect", true);
                        msg->post();
                    } else {
                        LOGW("Never received any data, disconnecting.");
					    sp<AMessage> msg = new AMessage('abor', id());
                        msg->post();
                    }
                }
                break;
            }
	   case 'expt':  //@hong server exception
	   		{
			 LOGI("server exception error.");
             sp<AMessage> msg = new AMessage('abor', id());
             msg->post();
    	     break;
	   		}
	        default:
                TRESPASS();
                break;
        }
    }

	void postKeepAlive() {
		  sp<AMessage> msg = new AMessage('aliv', id());
		  msg->setInt32("generation", mKeepAliveGeneration);
		  msg->post((mKeepAliveTimeoutUs * 9) / 10);
	  }

    void postAccessUnitTimeoutCheck() {
        if (mCheckPending) {
            return;
        }

        mCheckPending = true;
        sp<AMessage> check = new AMessage('chek', id());
        check->setInt32("generation", mCheckGeneration);
        check->post(kAccessUnitTimeoutUs);
    }
    void postCmdTimeoutCheck(const sp<AMessage> &dnoeMsg) {

		mGenCmd++;
        sp<AMessage> check = new AMessage('chekcmd', id());
		check->setInt32("gencmd", mGenCmd);
		check->setMessage("doneMsg",dnoeMsg);
		
        check->post(kAccessUnitTimeoutUs);
    }

    static void SplitString(
            const AString &s, const char *separator, List<AString> *items) {
        items->clear();
        size_t start = 0;
        while (start < s.size()) {
            ssize_t offset = s.find(separator, start);

            if (offset < 0) {
                items->push_back(AString(s, start, s.size() - start));
                break;
            }

            items->push_back(AString(s, start, offset - start));
            start = offset + strlen(separator);
        }
    }

    void parsePlayResponse(const sp<ARTSPResponse> &response) {
        mSeekable = false;

        ssize_t i = response->mHeaders.indexOfKey("range");
        if (i < 0) {
            // Server doesn't even tell use what range it is going to
            // play, therefore we won't support seeking.
            return;
        }

        AString range = response->mHeaders.valueAt(i);
        LOGV("Range: %s", range.c_str());

        AString val;
        CHECK(GetAttribute(range.c_str(), "npt", &val));
        float npt1, npt2;
        if (!ASessionDescription::parseNTPRange(val.c_str(), &npt1, &npt2)) {
            // This is a live stream and therefore not seekable.
            return;
        }

        i = response->mHeaders.indexOfKey("rtp-info");
        CHECK_GE(i, 0);

        AString rtpInfo = response->mHeaders.valueAt(i);
        List<AString> streamInfos;
        SplitString(rtpInfo, ",", &streamInfos);

        int n = 1;
        for (List<AString>::iterator it = streamInfos.begin();
             it != streamInfos.end(); ++it) {
            (*it).trim();
            LOGI("streamInfo[%d] = %s", n, (*it).c_str());

            CHECK(GetAttribute((*it).c_str(), "url", &val));
		LOGI("streamInfo val = %s", val.c_str());
            size_t trackIndex = 0;
            while (trackIndex < mTracks.size()
      //              && !(val == mTracks.editItemAt(trackIndex).mURL)) { //@hong change for compatible.
		      && (mTracks.editItemAt(trackIndex).mURL.find(val.c_str())== -1)){
                ++trackIndex;
            }
            CHECK_LT(trackIndex, mTracks.size());

            CHECK(GetAttribute((*it).c_str(), "seq", &val));

            char *end;
            unsigned long seq = strtoul(val.c_str(), &end, 10);

            TrackInfo *info = &mTracks.editItemAt(trackIndex);
            info->mFirstSeqNumInSegment = seq;
            info->mNewSegment = true;

            CHECK(GetAttribute((*it).c_str(), "rtptime", &val));

            uint32_t rtpTime = strtoul(val.c_str(), &end, 10);

			LOGI("track #%d: rtpTime=%u <=> npt=%.2f,mFirstSeqNumInSegment %d", n, rtpTime, npt1,info->mFirstSeqNumInSegment);
            info->mPacketSource->setNormalPlayTimeMapping(
                    rtpTime, (int64_t)(npt1 * 1E6));
            ++n;
			if(mSeekingTime > npt1 * 1E6)
			{
				onTimeUpdate(trackIndex,rtpTime,mSeekingTime);
			}
			else
			{
			    onTimeUpdate(trackIndex,rtpTime,(int64_t)(npt1 * 1E6));
			}
		
        }

        mSeekable = true;
    }

    sp<APacketSource> getPacketSource(size_t index) {
        CHECK_GE(index, 0u);
        CHECK_LT(index, mTracks.size());

        return mTracks.editItemAt(index).mPacketSource;
    }

    size_t countTracks() const {
        return mTracks.size();
    }

private:
    bool mServerException;
    struct TrackInfo {
        AString mURL;
        int mRTPSocket;
        int mRTCPSocket;
        bool mUsingInterleavedTCP;
        uint32_t mFirstSeqNumInSegment;
        bool mNewSegment;

        uint32_t mRTPAnchor;
        int64_t mNTPAnchorUs;
        int32_t mTimeScale;

        sp<APacketSource> mPacketSource;

        // Stores packets temporarily while no notion of time
        // has been established yet.
        List<sp<ABuffer> > mPackets;
    };
    sp<ALooper> mLooper;
    sp<ALooper> mNetLooper;
    sp<ARTSPConnection> mConn;
    sp<ARTPConnection> mRTPConn;
    sp<ASessionDescription> mSessionDesc;
    AString mOriginalSessionURL;  // This one still has user:pass@
    AString mSessionURL;
    AString mSessionHost;
    AString mBaseURL;
    AString mSessionID;
    bool mSetupTracksSuccessful;

	bool mSeekPending;
    int64_t mResumeTime;

	bool mPauseed;
	int32_t mPendingCmd;
	int64_t mSeekingTime;
	bool   mdisconnecting;

	sp<AMessage> mPendDoneMsg ;

    int64_t mNTPAnchorUs;
    int64_t mMediaAnchorUs;
    int64_t mLastMediaTimeUs;
	bool mlocalTimestamps ;

	bool mFirstAccessUnit ;
    uint64_t mFirstAccessUnitNTP;
    int64_t mNumAccessUnitsReceived;
    bool mCheckPending;
	bool mCmdSending ;
    int32_t mCheckGeneration;
	int32_t mGenCmd;
    bool mTryTCPInterleaving;
    bool mTryFakeRTCP;
    bool mReceivedFirstRTCPPacket;
    bool mReceivedFirstRTPPacket;
    bool mSeekable;

	int64_t mKeepAliveTimeoutUs;
    int32_t mKeepAliveGeneration;

    Vector<TrackInfo> mTracks;

    sp<AMessage> mDoneMsg;

	bool mDiscting;

    void setupTrack(size_t index) {
        sp<APacketSource> source =
            new APacketSource(mSessionDesc, index);

        if (source->initCheck() != OK) {
            LOGW("Unsupported format. Ignoring track #%d.", index);

            sp<AMessage> reply = new AMessage('setu', id());
            reply->setSize("index", index);
            reply->setInt32("result", ERROR_UNSUPPORTED);
            reply->post();
            return;
        }

        AString url;
        CHECK(mSessionDesc->findAttribute(index, "a=control", &url));

        AString trackURL;
        CHECK(MakeURL(mBaseURL.c_str(), url.c_str(), &trackURL));

        mTracks.push(TrackInfo());
        TrackInfo *info = &mTracks.editItemAt(mTracks.size() - 1);
        info->mURL = trackURL;
        info->mPacketSource = source;
        info->mUsingInterleavedTCP = false;
        info->mFirstSeqNumInSegment = 0;
        info->mNewSegment = true;
        info->mRTPAnchor = 0;
        info->mNTPAnchorUs = 0;

        unsigned long PT;
        AString formatDesc;
        AString formatParams;
        mSessionDesc->getFormatType(index, &PT, &formatDesc, &formatParams);

        int32_t timescale;
        int32_t numChannels;
        ASessionDescription::ParseFormatDesc(
                formatDesc.c_str(), &timescale, &numChannels);

        info->mTimeScale = timescale;

        LOGI("track #%d URL=%s mTimeScale =%d", mTracks.size(), trackURL.c_str(),timescale);

        AString request = "SETUP ";
        request.append(trackURL);
        request.append(" RTSP/1.0\r\n");

        if (!mTryTCPInterleaving) {
            size_t interleaveIndex = 2 * (mTracks.size() - 1);
            info->mUsingInterleavedTCP = true;
            info->mRTPSocket = interleaveIndex;
            info->mRTCPSocket = interleaveIndex + 1;

            request.append("Transport: RTP/AVP/TCP;interleaved=");
            request.append(interleaveIndex);
            request.append("-");
            request.append(interleaveIndex + 1);
        } else {
            unsigned rtpPort;
            ARTPConnection::MakePortPair( 
                    &info->mRTPSocket, &info->mRTCPSocket, &rtpPort);

            request.append("Transport: RTP/AVP/UDP;unicast;client_port=");
            request.append(rtpPort);
            request.append("-");
            request.append(rtpPort + 1);
        }

        request.append("\r\n");

        if (index > 1) {
            request.append("Session: ");
            request.append(mSessionID);
            request.append("\r\n");
        }

        request.append("\r\n");

        sp<AMessage> reply = new AMessage('setu', id());
        reply->setSize("index", index);
        reply->setSize("track-index", mTracks.size() - 1);
        mConn->sendRequest(request.c_str(), reply);
    }

    static bool MakeURL(const char *baseURL, const char *url, AString *out) {
        out->clear();

        if (strncasecmp("rtsp://", baseURL, 7)) {
            // Base URL must be absolute
            return false;
        }

        if (!strncasecmp("rtsp://", url, 7)) {
            // "url" is already an absolute URL, ignore base URL.
            out->setTo(url);
            return true;
        }

        size_t n = strlen(baseURL);
        if (baseURL[n - 1] == '/') {
            out->setTo(baseURL);
            out->append(url);
        } else {
            const char *slashPos = strrchr(baseURL, '/');

            if (slashPos > &baseURL[6]) {
                out->setTo(baseURL, slashPos - baseURL);
            } else {
                out->setTo(baseURL);
            }

            out->append("/");
            out->append(url);
        }

        return true;
    }

    void fakeTimestamps() {
        for (size_t i = 0; i < mTracks.size(); ++i) {
           onTimeUpdate(i, 0, 0ll);
        }
    }

    void onTimeUpdate(int32_t trackIndex, uint32_t rtpTime, uint64_t ntpTime) {
        LOGI("onTimeUpdate track %d, rtpTime = %d, ntpTime = %lld,mNTPAnchorUs =%lld",
             trackIndex, rtpTime, ntpTime,mNTPAnchorUs);

       // int64_t ntpTimeUs = (int64_t)(ntpTime * 1E6 / (1ll << 32));

        TrackInfo *track = &mTracks.editItemAt(trackIndex);

        track->mRTPAnchor = rtpTime;
        track->mNTPAnchorUs = ntpTime;

        if (mNTPAnchorUs < 0) {
            mNTPAnchorUs = ntpTime;
		    mMediaAnchorUs = mLastMediaTimeUs;
        }
    }

    void onAccessUnitComplete(
            int32_t trackIndex, const sp<ABuffer> &accessUnit) {
        LOGI("onAccessUnitComplete track %d", trackIndex);

        if (mFirstAccessUnit) {
            mDoneMsg->setInt32("result", OK);
            mDoneMsg->post();
            mDoneMsg = NULL;
			
		
		   for (size_t i = 0; i < mTracks.size(); ++i) {
		      TrackInfo *info = &mTracks.editItemAt(i);
				info->mPacketSource->setNormalPlayTimeMapping(							
						info->mRTPAnchor, info->mNTPAnchorUs);
				
		     if(!mSeekable)
		     {
			  onTimeUpdate(i,0,0);
		     }
		   }
           mFirstAccessUnit = false;
        }

        TrackInfo *track = &mTracks.editItemAt(trackIndex);

        if (mNTPAnchorUs < 0 || mMediaAnchorUs < 0 || track->mNTPAnchorUs < 0) {
            LOGI("storing accessUnit, no time established yet");
            track->mPackets.push_back(accessUnit);
            return;
        }

        while (!track->mPackets.empty()) {
            sp<ABuffer> accessUnit = *track->mPackets.begin();
            track->mPackets.erase(track->mPackets.begin());

            if (addMediaTimestamp(trackIndex, track, accessUnit)) {
                track->mPacketSource->queueAccessUnit(accessUnit);
            }
        }

        if (addMediaTimestamp(trackIndex, track, accessUnit)) {
            track->mPacketSource->queueAccessUnit(accessUnit);
        }
    }

    bool addMediaTimestamp(
            int32_t trackIndex, TrackInfo *track,
            const sp<ABuffer> &accessUnit) {
        uint32_t rtpTime;
        CHECK(accessUnit->meta()->findInt32(
                    "rtp-time", (int32_t *)&rtpTime));
	    if(track->mRTPAnchor == 0 )
		{
		  track->mRTPAnchor	= rtpTime ;
		}

	    LOGI("addMediaTimestamp track %d, rtpTime = %d, track->mNTPAnchorUs = %lld,mNTPAnchorUs =%lld,mMediaAnchorUs =%lld",
             trackIndex, rtpTime, track->mNTPAnchorUs,mNTPAnchorUs,mMediaAnchorUs);

        int64_t relRtpTimeUs =
            (((int64_t)rtpTime - (int64_t)track->mRTPAnchor) * 1000000ll)
                / track->mTimeScale;

        int64_t ntpTimeUs = track->mNTPAnchorUs + relRtpTimeUs;

        int64_t mediaTimeUs = mMediaAnchorUs + ntpTimeUs ;//- mNTPAnchorUs;

        if (mediaTimeUs > mLastMediaTimeUs) {
            mLastMediaTimeUs = mediaTimeUs;
        }

        if (mediaTimeUs < 0) {
            LOGV("dropping early accessUnit.");
            return false;
        }

        LOGI("track %d rtpTime=%d mediaTimeUs = %lld us (%.2f secs)",
             trackIndex, rtpTime, mediaTimeUs, mediaTimeUs / 1E6);

        accessUnit->meta()->setInt64("timeUs", mediaTimeUs);

        return true;
    }

    DISALLOW_EVIL_CONSTRUCTORS(MyHandler);
};

}  // namespace android

#endif  // MY_HANDLER_H_
