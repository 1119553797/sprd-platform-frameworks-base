/*
 * Main entry of app process.
 * 
 * Starts the interpreted runtime, then starts up the application.
 * 
 */

#define LOG_TAG "appproc"
#define LAST_SHUTDOWN_FILE "/data/last_shutdown_flag"
#define DEXPREOPT_PATH "/data/dalvik-cache"

#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>
#include <utils/Log.h>
#include <cutils/process_name.h>
#include <cutils/memory.h>
#include <android_runtime/AndroidRuntime.h>

#include <stdio.h>
#include <unistd.h>

#include <stdlib.h>
#include <dirent.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <string.h>

namespace android {

void app_usage()
{
    fprintf(stderr,
        "Usage: app_process [java-options] cmd-dir start-class-name [options]\n");
}

status_t app_init(const char* className, int argc, const char* const argv[])
{
    LOGV("Entered app_init()!\n");

    AndroidRuntime* jr = AndroidRuntime::getRuntime();
    jr->callMain(className, argc, argv);
    
    LOGV("Exiting app_init()!\n");
    return NO_ERROR;
}

class AppRuntime : public AndroidRuntime
{
public:
    AppRuntime()
        : mParentDir(NULL)
        , mClassName(NULL)
        , mArgC(0)
        , mArgV(NULL)
    {
    }

#if 0
    // this appears to be unused
    const char* getParentDir() const
    {
        return mParentDir;
    }
#endif

    const char* getClassName() const
    {
        return mClassName;
    }

    virtual void onStarted()
    {
        sp<ProcessState> proc = ProcessState::self();
        if (proc->supportsProcesses()) {
            LOGV("App process: starting thread pool.\n");
            proc->startThreadPool();
        }
        
        app_init(mClassName, mArgC, mArgV);

        if (ProcessState::self()->supportsProcesses()) {
            IPCThreadState::self()->stopProcess();
        }
    }

    virtual void onZygoteInit()
    {
        sp<ProcessState> proc = ProcessState::self();
        if (proc->supportsProcesses()) {
            LOGV("App process: starting thread pool.\n");
            proc->startThreadPool();
        }       
    }

    virtual void onExit(int code)
    {
        if (mClassName == NULL) {
            // if zygote
            if (ProcessState::self()->supportsProcesses()) {
                IPCThreadState::self()->stopProcess();
            }
        }

        AndroidRuntime::onExit(code);
    }

    
    const char* mParentDir;
    const char* mClassName;
    int mArgC;
    const char* const* mArgV;
};

}

using namespace android;

/*
 * sets argv0 to as much of newArgv0 as will fit
 */
static void setArgv0(const char *argv0, const char *newArgv0)
{
    strlcpy(const_cast<char *>(argv0), newArgv0, strlen(argv0));
}


//delete files in /data/dalvik-cache dir
void doDelCache()
{
    char file_path[PATH_MAX];
    DIR *d;
    struct dirent *file;
    if (access(DEXPREOPT_PATH, F_OK) == -1)
    {
        return;
    }
    if(!(d = opendir(DEXPREOPT_PATH)))
    {
        return;
    }

    while ((file = readdir(d)) != NULL)
    {
        if (strncmp(file->d_name, ".", 1) == 0 || strncmp(file->d_name, "..", 1) == 0)
            continue;

        strcpy(file_path, DEXPREOPT_PATH);
        strcat(file_path, "/");
        strcat(file_path, file->d_name);

        remove(file_path);
    }
}

void doLastShutDownCheck()
{
    FILE *fp;

    // check whether the file exist
    if (access(LAST_SHUTDOWN_FILE, F_OK) == -1)
    {//last shutdown normal.last_shutdown_file is deleted in anroid_os_Power.
        if ((fp = fopen(LAST_SHUTDOWN_FILE, "w+")) != NULL)
        {
            fputs("This is a flag for last shutdown check.Please dont del me!!!", fp);
        }
        fclose(fp);
    } else { //last shutdown unnormal .
        doDelCache();
    }
}

int main(int argc, const char* const argv[])
{
    // These are global variables in ProcessState.cpp
    mArgC = argc;
    mArgV = argv;
    
    mArgLen = 0;
    for (int i=0; i<argc; i++) {
        mArgLen += strlen(argv[i]) + 1;
    }
    mArgLen--;

    AppRuntime runtime;
    const char *arg;
    const char *argv0;

    argv0 = argv[0];

    // Process command line arguments
    // ignore argv[0]
    argc--;
    argv++;

    // Everything up to '--' or first non '-' arg goes to the vm
    
    int i = runtime.addVmArguments(argc, argv);

    // Next arg is parent directory
    if (i < argc) {
        runtime.mParentDir = argv[i++];
    }

    // Next arg is startup classname or "--zygote"
    if (i < argc) {
        arg = argv[i++];
        if (0 == strcmp("--zygote", arg)) {
            bool startSystemServer = (i < argc) ? 
                    strcmp(argv[i], "--start-system-server") == 0 : false;
            setArgv0(argv0, "zygote");
            set_process_name("zygote");
            // do last shutdown check
            doLastShutDownCheck();
            runtime.start("com.android.internal.os.ZygoteInit",
                startSystemServer);
        } else {
            set_process_name(argv0);

            runtime.mClassName = arg;

            // Remainder of args get passed to startup class main()
            runtime.mArgC = argc-i;
            runtime.mArgV = argv+i;

            LOGV("App process is starting with pid=%d, class=%s.\n",
                 getpid(), runtime.getClassName());
            runtime.start();
        }
    } else {
        LOG_ALWAYS_FATAL("app_process: no class name or --zygote supplied.");
        fprintf(stderr, "Error: no class name or --zygote supplied.\n");
        app_usage();
        return 10;
    }

}
