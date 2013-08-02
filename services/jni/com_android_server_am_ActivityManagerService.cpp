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
#define LOG_TAG "ActivityManagerService"

#include <utils/Log.h>
#include <utils/misc.h>

#include <fcntl.h>
#include <string.h>
#include <stdlib.h>

#include "jni.h"
#include "JNIHelp.h"

#include "cutils/log.h"

#define MYLOGV(...) ((void)__android_log_print(ANDROID_LOG_VERBOSE,LOG_TAG, __VA_ARGS__))

#define READ_BUFFER_SIZE 1024

namespace android {

static long read_little_file_to_buffer(JNIEnv* env, const char *fn, char *buffer, int buffer_len)
{
    int fd;
    long read_len = 0;
    fd = open(fn, O_RDONLY);
    if (fd < 0) {
        sprintf(buffer, "open file %s error", fn);
        jniThrowException(env, "java/io/FileNotFoundException", buffer);
        return fd;
    }

    read_len = read(fd, buffer, buffer_len);
    if (read_len < 0) {
        sprintf(buffer, "read file %s error", fn);
        jniThrowException(env, "java/io/IOException", buffer);
    }
    close(fd);

    return read_len;
}

static jlong android_server_am_ActivityManagerService_readAvailMemNative(JNIEnv* env, jobject obj)
{
    char buffer[READ_BUFFER_SIZE];
    char *line;
    int res = 0;
    jlong memFree = 0;

    //MYLOGV("readAvailMemNative: \n");
    res = read_little_file_to_buffer(env, "/proc/meminfo", buffer, READ_BUFFER_SIZE);
    if (res < 0) {
        return 0L;
    }

    line = strtok(buffer, "\n");
    while(line) {
        if (line[0] == '\a')
            line += 1;
        if (!strncmp("MemFree:", line, 8)) {
            memFree += strtol(line + 8, NULL, 10);
        } else if (!strncmp("Cached:", line, 7)) {
            memFree += strtol(line + 7, NULL, 10);
        }
        line = strtok(NULL, "\n");
    }

    return memFree;
}

static const char *nexttoksep(char **strp, const char *sep)
{
    char *p = strsep(strp,sep);
    return (p == 0) ? "" : p;
}
static const char *nexttok(char **strp)
{
    return nexttoksep(strp, " ");
}

static jlong android_server_am_ActivityManagerService_getAppUsedMemoryNative(JNIEnv* env, jobject obj, jint pid)
{
    char fn[128];
    char buffer[READ_BUFFER_SIZE];
    char *ptr;
    int res = 0;
    jlong procRss = 0;

    //MYLOGV("getAppUsedMemoryNative: pid is %d\n", pid);
    sprintf(fn, "/proc/%d/stat", pid);
    //MYLOGV("getAppUsedMemoryNative: open file %s\n", fn);

    res = read_little_file_to_buffer(env, fn, buffer, READ_BUFFER_SIZE);
    if (res < 0) {
        MYLOGV("getAppUsedMemoryNative: open or read file error!\n");
        return 0L;
    }

    // fixme:copy form ps->toolbox, if statline format changed, this should be modified
    ptr = buffer;

    nexttok(&ptr); // 1 skip pid
    ptr++;          // skip "("

    //name = ptr;     // 2 name
    ptr = strrchr(ptr, ')'); // Skip to *last* occurence of ')',
    *ptr++ = '\0';           // and null-terminate name.

    ptr++;         // skip " "
    nexttok(&ptr); // 3 state
    nexttok(&ptr); // 4 ppid
    nexttok(&ptr); // 5 pgrp
    nexttok(&ptr); // 6 sid
    nexttok(&ptr); // 7 tty

    nexttok(&ptr); // 8 tpgid
    nexttok(&ptr); // 9 flags
    nexttok(&ptr); // 10 minflt
    nexttok(&ptr); // 11 cminflt
    nexttok(&ptr); // 12 majflt
    nexttok(&ptr); // 13 cmajflt
    nexttok(&ptr); // 14 utime
    nexttok(&ptr); // 15 stime
    nexttok(&ptr); // 16 cutime
    nexttok(&ptr); // 17 cstime
    nexttok(&ptr); // 18 prio
    nexttok(&ptr); // 19 nice
    nexttok(&ptr); // 20 threads
    nexttok(&ptr); // 21 itrealvalue
    nexttok(&ptr); // 22 starttime
    nexttok(&ptr); // 23 vsize
    procRss = strtol(nexttok(&ptr), NULL, 10); // 24 rss
    //MYLOGV("getAppUsedMemoryNative: rss is %lld!\n", procRss);
    //nexttok(&ptr); // rlim
    //nexttok(&ptr); // startcode
    //nexttok(&ptr); // endcode
    //nexttok(&ptr); // startstack
    //nexttok(&ptr); // kstkesp
    //nexttok(&ptr); // kstkeip
    //nexttok(&ptr); // signal
    //nexttok(&ptr); // blocked
    //nexttok(&ptr); // sigignore
    //nexttok(&ptr); // sigcatch
    //nexttok(&ptr); // wchan
    //nexttok(&ptr); // nswap
    //nexttok(&ptr); // cnswap
    //nexttok(&ptr); // exit signal
    //nexttok(&ptr); // processor
    //nexttok(&ptr); // rt_priority
    //nexttok(&ptr); // scheduling policy

    //nexttok(&ptr); // tty

    return procRss;
}

/*
 * JNI registration.
 */
static JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    { "readAvailMemNative", "()J", (void*) android_server_am_ActivityManagerService_readAvailMemNative },
//    { "getAppUsedMemoryNative", "(I)J", (void*) android_server_am_ActivityManagerService_getAppUsedMemoryNative },
};

int register_android_server_ActivityManagerService(JNIEnv* env)
{
    return jniRegisterNativeMethods(env, "com/android/server/am/ActivityManagerService",
            gMethods, NELEM(gMethods));
}

}; // namespace android


