#define LOG_NDEBUG 0
#define LOG_TAG "[INNO/SockChannel/SockChannel.cpp]"

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

#define SOCK_DEBUG_LOG if(0)LOGI
#define GOFAILED(msg) { LOGI msg; goto FAILED;}
enum {
    SET_SERVICEID = 1,
};

static int writeBytes(int fd, unsigned char*buff, unsigned long len)
{
    char *p = (char *)buff;
    unsigned long index = 0;
    while(index <len)
    {
        int  wlen = write(fd, p+index, len-index);
        if(wlen < 0)
            return 0;
        index += wlen;
    }
    return 1;
}

static int writeInt(int fd, unsigned long data)
{
    data = htonl(data);
    return writeBytes(fd, (unsigned char*)(&data), 4);
}

static int writeShort(int fd, unsigned short data)
{
    data = htons(data);
    return writeBytes(fd, (unsigned char*)(&data), 2);
}

static int writeByte(int fd, unsigned char data)
{
    return writeBytes(fd, (unsigned char*)(&data), 1);
}

static int readBytes(int fd, unsigned char*buff, unsigned long len)
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

static int readInt(int fd, unsigned long *data)
{
    int ret = readBytes(fd, (unsigned char*)(data), 4);
    *data = ntohl(*data);
    return ret;
}

static int readShort(int fd, unsigned short *data)
{
    int ret = readBytes(fd, (unsigned char*)(data), 2);
    *data = ntohs(*data);
    return ret;
}

static int readByte(int fd, unsigned char *data)
{
    return readBytes(fd, (unsigned char*)(data), 1);
}

static void *local_re_malloc(void *p, unsigned long size)
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

static void local_free(void *p)
{
    unsigned long *ptemp = (unsigned long *)p;
    ptemp--;
    free(ptemp);
}

static int checkSocketDataReadable(int fd)
{
    int ret;
    fd_set readfd;
    struct timeval timeout;
    timeout.tv_sec=0;
    timeout.tv_usec=2*1000*1000;
    FD_ZERO(&readfd);
    FD_SET(fd,&readfd);
    ret=select(fd+1, &readfd, NULL, NULL, &timeout);
    if(ret < 0) {
        LOGI("checkSocketDataReadable========================================%d!", ret);        
        return ret;
    }
    if(FD_ISSET(fd, &readfd))
        return 1;
    LOGI("checkSocketDataReadable========================================%d!", ret);        
    return 0;
}


static int createSocket(const char *serverip, int port)
{
    int fd = -1;
    {
        struct sockaddr_in server_addr;
        if (inet_aton(serverip, &server_addr.sin_addr) == 0)
        {
            SOCK_DEBUG_LOG("=======createSocket inet_aton(\"127.0.0.1\", &server_addr.sin_addr) error!");        
            return -1;
        }
        fd = socket(AF_INET,SOCK_STREAM,0);
        if(fd == -1)
        {
            SOCK_DEBUG_LOG("=======createSocket socket(AF_INET,SOCK_STREAM,0) error!:%s:%d!", serverip, port);
            return -1;
        }

        int nRecvBuf=115200*45;//\u8bbe\u7f6e\u4e3a32K
        int re = setsockopt(fd,SOL_SOCKET,SO_RCVBUF,(const char*)&nRecvBuf,sizeof(int));
        LOGI("==========================setsockopt:%d!", re);
        socklen_t optlen = sizeof(nRecvBuf);
        re = getsockopt(fd,SOL_SOCKET,SO_RCVBUF, &nRecvBuf, &optlen);
        LOGI("==========================getsockopt:%d, %d!", re, nRecvBuf);
        server_addr.sin_family = AF_INET;
        server_addr.sin_port = htons(port);
        if(connect(fd,(struct sockaddr *)&server_addr,sizeof(server_addr)) != 0)
        {
            SOCK_DEBUG_LOG("=======createSocket connect(fd,(struct sockaddr *)&server_addr,sizeof(server_addr)) error!:%s:%d!", serverip, port);
            close(fd);
            return -1;
        }
        SOCK_DEBUG_LOG("=======createSocket OK:%s:%d!", serverip, port);
    }
    return fd;
}

typedef struct {
    char *frame_buff;
    int av_fd;
}SOCK_CONNET_S;

void *sock_connectAVServer(int port)
{
    SOCK_CONNET_S *scs = (SOCK_CONNET_S *)malloc(sizeof(SOCK_CONNET_S));
    if(scs == NULL)
        return NULL;
    scs->av_fd = createSocket("127.0.0.1", port);
    if(scs->av_fd < 0) {
        free(scs);
        return NULL;
    }
    scs->frame_buff = NULL;
    return scs;
 }

void sock_disconnectAVServer(void *ph)
{
    SOCK_CONNET_S *scs = (SOCK_CONNET_S *)ph;
    if(scs->av_fd != -1)
    {
        shutdown(scs->av_fd, SHUT_WR);
        close(scs->av_fd);
    }
    if(scs->frame_buff)
    {
        local_free(scs->frame_buff);
    }
}

int sock_sendSIDtoAVServer(void *ph, int serviceid)
{
    SOCK_CONNET_S *scs = (SOCK_CONNET_S *)ph;
    if(!writeInt(scs->av_fd, 8)) GOFAILED(("=======sock_sendSIDtoAVServer write size error!"));
    if(!writeInt(scs->av_fd, SET_SERVICEID)) GOFAILED(("=======sock_sendSIDtoAVServer write SET_SERVICEID error!"));
    if(!writeInt(scs->av_fd, serviceid)) GOFAILED(("=======sock_sendSIDtoAVServer write serviceid error!"));
    LOGI("=======sock_sendSIDtoAVServer OK!");
    return 0;
FAILED:
    return -1;
}

int sock_readVideoData(void *ph, void **p, 
    unsigned long *pid, 
    unsigned long *timeStamp, 
    unsigned short *sid)
{
    unsigned char type;
    unsigned short size;
    SOCK_CONNET_S *scs = (SOCK_CONNET_S *)ph;
    if(scs->av_fd == -1)
    {
        return -1;
    }

    SOCK_DEBUG_LOG("=======sock_readVideoData start---- !");
    int fd = scs->av_fd;
    int statu = checkSocketDataReadable(fd);
    if(statu <= 0)
        return statu;

    if(!readInt(fd, pid)) GOFAILED(("=======sock_readVideoData read(fd, pid, sizeof(*pid)) error!"));

    if(!readByte(fd, &type))GOFAILED(("=======sock_readVideoData read(fd, &type, sizeof(type)) error!"));

    if(!readInt(fd, timeStamp))GOFAILED(("=======sock_readVideoData read(fd, timeStamp, sizeof(*timeStamp)) error!"));

    if(!readShort(fd, sid))GOFAILED(("=======sock_readVideoData read(fd, flag, sizeof(*flag)) error!"));

    if(!readShort(fd, &size))GOFAILED(("=======sock_readVideoData read(fd, &size, sizeof(size) error!"));

    scs->frame_buff = (char *)local_re_malloc(scs->frame_buff, size);
    if(!scs->frame_buff) GOFAILED(("=======sock_readVideoData local_re_malloc(size) error!"));

    if(!readBytes(fd, (unsigned char *)(scs->frame_buff), size))GOFAILED(("=======sock_readVideoData read(fd, frame_buff, size) error!"));

    SOCK_DEBUG_LOG("=======sock_readVideoData sock_readVideoData OK %d:%u!", size, *timeStamp);

    *p = scs->frame_buff;
    return size;
FAILED:
    shutdown(scs->av_fd, SHUT_WR);
    close(scs->av_fd);
    scs->av_fd = -1;
    return -1;
}

int sock_readAudioData(void *ph, void **p, 
    unsigned long *pid, 
    unsigned long *timeStamp, 
    unsigned char *unittype, 
    unsigned short *rate,
    unsigned short *sid)
{
    unsigned char type;
    unsigned short size;
    SOCK_CONNET_S *scs = (SOCK_CONNET_S *)ph;
    if(scs->av_fd == -1)
    {
        return -1;
    }

    int fd = scs->av_fd;

    SOCK_DEBUG_LOG("=======sock_readAudioData start---- !");
    int statu = checkSocketDataReadable(fd);
    if(statu <= 0)
        return statu;

    if(!readInt(fd, pid)) GOFAILED(("=======sock_readAudioData read(fd, pid, sizeof(*pid)) error!"));

    if(!readByte(fd, &type)) GOFAILED(("=======sock_readVideoData read(fd, &type, sizeof(type)) error!"));

    if(!readInt(fd, timeStamp)) GOFAILED(("=======sock_readAudioData read(fd, timeStamp, sizeof(*timeStamp)) error!"));

    if(!readByte(fd, unittype)) GOFAILED(("=======sock_readAudioData read(fd, unittype, sizeof(*unittype)) error!"));

    if(!readShort(fd, rate)) GOFAILED(("=======sock_readAudioData read(fd, rate, sizeof(*rate)) error!"));

    if(!readShort(fd, sid)) GOFAILED(("=======sock_readAudioData read(fd, flag, sizeof(*flag)) error!"));

    if(!readShort(fd, &size)) GOFAILED(("=======sock_readAudioData read(fd, &size, sizeof(size) error!"));

    scs->frame_buff = (char *)local_re_malloc(scs->frame_buff, size);
    if(!scs->frame_buff) GOFAILED(("=======sock_readAudioData local_re_malloc(size) error!"));

    if(!readBytes(fd, (unsigned char *)scs->frame_buff, size)) GOFAILED(("=======sock_readAudioData read(fd, frame_buff, size) error!"));

    *p = scs->frame_buff;

    SOCK_DEBUG_LOG("=======sock_readAudioData sock_readVideoData OK %d!", size);
    return size;
FAILED:
    shutdown(scs->av_fd, SHUT_WR);
    close(scs->av_fd);
    scs->av_fd = -1;
    return -1;
        
}

int sock_checkReadble(void *ph)
{
    SOCK_CONNET_S *scs = (SOCK_CONNET_S *)ph;
    int nbytes;
    if(ioctl(scs->av_fd, FIONREAD, &nbytes) == 0)
        return nbytes;
    return -1;
}

} //namespace android

