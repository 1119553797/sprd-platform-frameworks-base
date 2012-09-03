#define LOG_NDEBUG 0
#define LOG_TAG "MxdCmmbClient"

#include <utils/Log.h>
#include <media/stagefright/MediaDebug.h>

namespace android
{

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
#include<sys/ioctl.h>
#include <fcntl.h>

	int g_MxdSocketRecvBufSize = 0;

	static int prefetchSocketByteNum(int fd)
	{
		int ret;
		fd_set readfd;
		struct timeval timeout;
		timeout.tv_sec=0;
		timeout.tv_usec= 0;/*1*1000*1000;*/
		FD_ZERO(&readfd);
		FD_SET(fd,&readfd);
		ret=select(fd+1, &readfd, NULL, NULL, &timeout);
		if(ret < 0) {

			return ret;
		}
		if(FD_ISSET(fd, &readfd))
			return 1;

		return 0;
	}


	static int createClientSocket(const char *serverip, int port)
	{
		int fd = -1;
		{
			struct sockaddr_in server_addr;
			if (inet_aton(serverip, &server_addr.sin_addr) == 0)
			{
				LOGI("inet_aton failed!");        
				return -1;
			}
			fd = socket(AF_INET,SOCK_STREAM,0);
			if(fd == -1)
			{
				LOGI("crate cmmb client stream socket failed");
				return -1;
			}

			int nRecvBuf=115200*45;//\u8bbe\u7f6e\u4e3a32K
			int re = setsockopt(fd,SOL_SOCKET,SO_RCVBUF,(const char*)&nRecvBuf,sizeof(int));

			socklen_t optlen = sizeof(nRecvBuf);
			re = getsockopt(fd,SOL_SOCKET,SO_RCVBUF, &nRecvBuf, &optlen);
			g_MxdSocketRecvBufSize = nRecvBuf;
			server_addr.sin_family = AF_INET;
			server_addr.sin_port = htons(port);
			if(connect(fd,(struct sockaddr *)&server_addr,sizeof(server_addr)) != 0)
			{
				LOGI("connect server failed!");
				close(fd);
				return -1;
			}
			LOGI("client create socket success!");
		}
		return fd;
	}

	typedef struct {
		char *frame_buff;
		int av_fd;
	}SOCK_CONNET_S;

	void *connectCmmbServer(int port)
	{
		SOCK_CONNET_S *scs = (SOCK_CONNET_S *)malloc(sizeof(SOCK_CONNET_S));
		if(scs == NULL)
			return NULL;
		scs->av_fd = createClientSocket("127.0.0.1", port);
		if(scs->av_fd < 0) {
			free(scs);
			return NULL;
		}
		scs->frame_buff = NULL;
		return scs;
	}


	void *mem_malloc(void *p, unsigned long size)
	{
		unsigned long *ptemp = NULL;
		if(p)
		{
			ptemp = (unsigned long *)p;
			ptemp--;
			if(*ptemp >= size)
				return p;
			free(ptemp);
		}
		ptemp = NULL;
		p = malloc(size+sizeof(unsigned long));
		if(p)
		{
			ptemp = (unsigned long *)p;
			*ptemp = size;
			ptemp++;
		}
		return ptemp;
	}

	void mem_free(void *p)
	{
		unsigned long *ptemp = (unsigned long *)p;
		ptemp--;
		free(ptemp);
	}

	void teardownCmmbServer(void *ph)
	{
		SOCK_CONNET_S *scs = (SOCK_CONNET_S *)ph;
		if(scs->av_fd != -1)
		{
			shutdown(scs->av_fd, SHUT_WR);
			close(scs->av_fd);
		}
		if(scs->frame_buff)
		{
			mem_free(scs->frame_buff);
		}
	}


	static int getBytes(int fd, unsigned char*buff, unsigned long len)
	{
		char *p = (char *)buff;
		unsigned long index = 0;
		while(index <len)
		{
			int  wlen = read(fd, p+index, len-index);
			if(wlen <= 0)
				return 0;
			index += wlen;
		}
		return 1;
	}

	static int getInt32(int fd, unsigned long *data)
	{
		int ret = getBytes(fd, (unsigned char*)(data), 4);
		*data = ntohl(*data);
		return ret;
	}

	static int getShort16(int fd, unsigned short *data)
	{
		int ret = getBytes(fd, (unsigned char*)(data), 2);
		*data = ntohs(*data);
		return ret;
	}


#define GOTO_ERROR_PROC(info) { LOGI info; goto READERROR;}
	int getVideoData(void *ph, void **p, unsigned long *timeStamp)
	{
		unsigned char type;
		unsigned short size;
		SOCK_CONNET_S *scs = (SOCK_CONNET_S *)ph;
		if(scs->av_fd == -1)
		{
			return -1;
		}

		int fd = scs->av_fd;
		/*
		int statu = prefetchSocketByteNum(fd);
		if(statu <= 0)
		return statu;
		*/


		if(!getInt32(fd, timeStamp))GOTO_ERROR_PROC(("get timestamp field error!"));
		if(!getShort16(fd, &size))GOTO_ERROR_PROC(("get datasize field error!"));
		scs->frame_buff = (char *)mem_malloc(scs->frame_buff, size);
		if(!scs->frame_buff) GOTO_ERROR_PROC(("malloc data memory error!"));

		if(!getBytes(fd, (unsigned char *)(scs->frame_buff), size))GOTO_ERROR_PROC(("get data field error!"));

		*p = scs->frame_buff;
		return size;

READERROR:
		shutdown(scs->av_fd, SHUT_WR);
		close(scs->av_fd);
		scs->av_fd = -1;
		return -1;
	}

	int getAudioData(void *ph, void **p, unsigned long *timeStamp )
	{
		unsigned char type;
		unsigned short size;
		SOCK_CONNET_S *scs = (SOCK_CONNET_S *)ph;
		if(scs->av_fd == -1)
		{
			return -1;
		}

		int fd = scs->av_fd;
		/*
		int statu = prefetchSocketByteNum(fd);
		if(statu <= 0)
		return statu;
		*/


		if(!getInt32(fd, timeStamp)) GOTO_ERROR_PROC(("get timestamp field error!"));
		if(!getShort16(fd, &size)) GOTO_ERROR_PROC(("get datasize field error!"));
		scs->frame_buff = (char *)mem_malloc(scs->frame_buff, size);
		if(!scs->frame_buff) GOTO_ERROR_PROC(("malloc data memory error!"));

		if(!getBytes(fd, (unsigned char *)scs->frame_buff, size)) GOTO_ERROR_PROC(("get data field error!"));

		*p = scs->frame_buff;

		return size;
READERROR:
		shutdown(scs->av_fd, SHUT_WR);
		close(scs->av_fd);
		scs->av_fd = -1;
		return -1;

	}

	int socketCacheByteNum(void *ph)
	{
		SOCK_CONNET_S *scs = (SOCK_CONNET_S *)ph;
		int nbytes;
		if(ioctl(scs->av_fd, FIONREAD, &nbytes) == 0)
			return nbytes;
		return -1;
	}

} //namespace android

