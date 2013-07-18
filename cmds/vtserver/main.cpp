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

#include <unistd.h>
#include <fcntl.h>
#include <cutils/properties.h>
#include <cutils/sockets.h>
#include <sys/socket.h>
#include <utils/Log.h>

#define CTP_TEST "ctp.test"
#define BTUT_TEST "btut.test"
//for 8810ea
#define SPRD_SD_TESTFILE "/sdcard/test.txt"
#define SPRD_SD_TAG "SDCardTest"
#define SPRD_READ_FM_TESTFILE "/data/data/com.spreadst.validationtools/fm2.txt"

int write_file_tag(char* filename, char* buf)
{
	FILE *fp = fopen(filename, "w");
	if(fp == NULL)
	{
		ALOGE("=== open file to write failed! ===\n");
		return 1;
	} 
	fprintf(fp, "%s", buf);
	fclose(fp);
	return 0;
}

int read_file_tag(char* filename, char* buf)
{
	FILE *fp = fopen(filename, "r");
	if(fp == NULL)
	{
		ALOGE("=== open file to read failed! ===\n");
		return 1;
	} 
	fscanf(fp, "%s", buf);
	fclose(fp);
	return 0;
}
static int rfkill_id = -1;
static char *rfkill_state_path = NULL;
static int init_rfkill() {
    char path[64];
    char buf[16];
    int fd;
    int sz;
    int id;
    for (id = 0; ; id++) {
        snprintf(path, sizeof(path), "/sys/class/rfkill/rfkill%d/type", id);
        fd = open(path, O_RDONLY);
        if (fd < 0) {
		ALOGI("BTUT init_rfkill open fd error\n");
            return -1;
        }
        sz = read(fd, &buf, sizeof(buf));
        close(fd);
        if (sz >= 9 && memcmp(buf, "bluetooth", 9) == 0) {
            rfkill_id = id;
            break;
        }
    }

    asprintf(&rfkill_state_path, "/sys/class/rfkill/rfkill%d/state", rfkill_id);
    return 0;
}

static int set_bluetooth_power(int on) {
    int sz;
    int fd = -1;
    int ret = -1;
    const char buffer = (on ? '1' : '0');

    if (rfkill_id == -1) {
        if (init_rfkill()) goto out;
    }

    fd = open(rfkill_state_path, O_WRONLY);
    if (fd < 0) {
	ALOGI("BTUT set_bluetooth_power open fd error\n");
        goto out;
    }
    sz = write(fd, &buffer, 1);
    if (sz < 0) {
	ALOGI("BTUT set_bluetooth_power write fd error\n");
        goto out;
    }
    ret = 0;

out:
    if (fd >= 0) close(fd);
    return ret;
}
int main(int argc, char** argv)
{
	ALOGE("=== vtserver start! ===\n");
	
        char ch;
    
	FILE *fp = fopen("/data/data/com.spreadst.validationtools/vt.txt", "rb");
	if(fp == NULL)
	{
		ALOGE("=== open file /data/data/com.spreadst.validationtools/vt.txt failed! ===\n");
		return 1;
	}
	fread(&ch, 1, 1, fp);
	fclose(fp);
	
	if(ch == '0')
//    property_get(CTP_TEST, buf, "1");
//    if(strcmp(buf, "0") == 0)
    {
		ALOGE("=== receive CTP test requirement! ===\n");
		ALOGE("=== CTP test start! ===\n");
		int error = system("echo 1 > /sys/devices/platform/sc8810-i2c.2/i2c-2/2-005c/calibrate");
		if(error == -1 || error == 127)
		{
			ALOGE("=== CTP test failed! ===\n");
			ch = '2';
//			property_set(CTP_TEST, "-1");
		}
		else
		{
			ALOGE("=== CTP test succeed! ===\n");
			ch = '1';
//			property_set(CTP_TEST, "1");
		}
    }
    else if(ch == '3')
    {
#ifdef BOARD_HAVE_BLUETOOTH_BK
        ALOGE("=== receive BTUT test requirement! ===\n");
        ALOGE("=== BTUT test start! ===\n");
        int error;
        ALOGE("=== BTUT test stop bluetoothd! ===\n");
        error = system("setprop ctl.stop bluetoothd");
        ALOGE("=== BTUT test stop hciattach! ===\n");
        error = system("setprop ctl.stop hciattach");
        ALOGE("=== BTUT test close BT power! ===\n");
        if (set_bluetooth_power(0))
        {
            ALOGI("===BTUT test POWOFF BT failed ===\n");
        }
        //msleep(100);
        ALOGE("=== BTUT test Write EUT mode! ===\n");
        system("echo 1 > /data/bteut.txt");
        system("chmod 777 /data/bteut.txt");
        ALOGE("=== BTUT test open BT power! ===\n");
        if (set_bluetooth_power(1) < 0) 
        {
            ALOGI("===BTUT test POWON BT failed ===\n");
            system("rm /data/bteut.txt");
            return NULL;
        }	
        ALOGE("=== BTUT test start hciattach! ===\n");
        error = system("setprop ctl.start hciattach");
        sleep(2);
	 system("rm /data/bteut.txt");
        ALOGE("=== BTUT test hciconfig up! ===\n");
        error = system("hciconfig hci0 up");
        if(error == -1 || error == 127)
        {
            ALOGE("=== BTUT test failed on cmd 1! ===\n");
            ch = '5';
        }
        else
        {
            error = system("hcitool cmd 0x03 0x0005 0x02 0x00 0x02");
	     ALOGE("Alex error1==%d\n",error);
            if(error == -1 || error == 127)
            {
                ALOGE("=== BTUT test failed on cmd 2! ===\n");
                ch = '5';
            }
            else
            {
                error = system("hcitool cmd 0x03 0x001A 0x03");
		  ALOGE("Alex error2==%d\n",error);
                if(error == -1 || error == 127)
                {
                    ALOGE("=== BTUT test failed on cmd 3! ===\n");
                    ch = '5';
                }
                else
                {
                    error = system("hcitool cmd 0x06 0x0003");
		      ALOGE("Alex error3==%d\n",error);
                    if(error == -1 || error == 127)
                    {
                        ALOGE("=== BTUT test failed on cmd 4! ===\n");
                        ch = '5';
                    }
                    else
                    {
                        if(error == -1 || error == 127)
                        {
                            ALOGE("=== BTUT test failed on cmd 5! ===\n");
                            ch = '5';
                        } 
                        else
                        {
                            ALOGE("=== BTUT test succeed! ===\n");
                            ch = '4';
                            system("rm /data/bteut.txt");
                        }
                    }
                }
            }
        }
#else
			ALOGE("=== receive BTUT test requirement! ===\n");
			ALOGE("=== BTUT test start! ===\n");
			int error = system("hciconfig hci0 up");
			if(error == -1 || error == 127)
			{
				ALOGE("=== BTUT test failed on cmd 1! ===\n");
				ch = '5';
//				property_set(BTUT_TEST, "-1");
			}
			else
			{
				//error = system("hcitool cmd 0x03 0x0005 0x02 0x00 0x02");
                                //step1
                                error = system("hcitool cmd 0x03 0x03");
				if(error == -1 || error == 127)
				{
					ALOGE("=== BTUT test failed on cmd 2! ===\n");
					ch = '5';
//					property_set(BTUT_TEST, "-1");
				}
				else
				{
					//error = system("hcitool cmd 0x03 0x001A 0x03");
                                        //step2
                                        error = system("hcitool cmd 0x03 0x1a 0x03");
					if(error == -1 || error == 127)
					{
						ALOGE("=== BTUT test failed on cmd 3! ===\n");
						ch = '5';
//						property_set(BTUT_TEST, "-1");
					}
					else
					{
						//error = system("hcitool cmd 0x06 0x0003");
                                                //step3
                                                error = system("hcitool cmd 0x03 0x05  0x02 0x00 0x02");
						if(error == -1 || error == 127)
						{
							ALOGE("=== BTUT test failed on cmd 4! ===\n");
							ch = '5';
//							property_set(BTUT_TEST, "-1");
						}
                                                else
                                                {
                                                      //step4
                                                       error = system("hcitool cmd 0x06 0x03");
						       if(error == -1 || error == 127)
						       {
							     ALOGE("=== BTUT test failed on cmd 5! ===\n");
							     ch = '5';
//							     property_set(BTUT_TEST, "-1");
						        } 
						        else
						        {
							       ALOGE("=== BTUT test succeed! ===\n");
							       ch = '4';
//							       property_set(BTUT_TEST, "1");
						        }
                                                   }
					}
				}
			}

#endif
    }
    else if(ch == 'a')
    {
		ALOGE("=== receive PhoneLoopBack test requirement! ===\n");
		ALOGE("=== PhoneLoopBack test start! ===\n");
		int error = system("echo 1,2,100 > /dev/pipe/mmi.audio.ctrl");
		if (error == -1 || error == 127) {
			ALOGE("=== PhoneLoopBack test failed on cmd 1! ===\n");
			ch = '5';
		} else{
			ALOGE("=== PhoneLoopBack test  OK! ===\n");
		    ch = '4';
		}
	}
    else if(ch == 'b')
    {
		ALOGE("=== receive PhoneLoopBack test ROLLBACK start===\n");
		int error = system("echo 0,0,0 > /dev/pipe/mmi.audio.ctrl");
		if (error == -1 || error == 127) {
			ALOGE("=== PhoneLoopBack test ROLLBACK failed! ===\n");
			ch = '5';
		} else {
			ALOGE("=== PhoneLoopBack test ROLLBACK OK! ===\n");
			ch = '4';
		}
	}
   else if(ch == 'c')
    {
		ALOGE("=== receive PhoneLoopBack test requirement! ===\n");
		ALOGE("=== PhoneLoopBack test start! ===\n");
		int error = system("echo 1,4,100 > /dev/pipe/mmi.audio.ctrl");
		if (error == -1 || error == 127) {
			ALOGE("=== PhoneLoopBack test failed on cmd 1! ===\n");
			ch = '5';
		} else{
			ALOGE("=== PhoneLoopBack test  OK! ===\n");
		    ch = '4';
		}
	}

    else if(ch == '6')
    {
        char* path = strcat(getenv("EXTERNAL_STORAGE"),"/test.txt");
    	int ret_w = write_file_tag(path, SPRD_SD_TAG);
    	char buf[20] = {0};
    	int ret_r = read_file_tag(path, buf);
    	if(ret_w != 0 || ret_r != 0 || strcmp(buf, SPRD_SD_TAG) != 0)
    	{
			ALOGE("=== SDCard test failed! ===\n");
			ch = '8';
    	}
    	else
    	{
			ALOGE("=== SDCard test succeed! ===\n");
			ch = '7';
    	}    	
    }

    else if(ch == 'd')
    {
	char* path = strcat(getenv("SECONDARY_STORAGE"),"/test.txt");
	ALOGE(" SDCard  path:%s\n",path);
        int ret_w = write_file_tag(path, SPRD_SD_TAG);
        char buf[20] = {0};
        int ret_r = read_file_tag(path, buf);
        if(ret_w != 0 || ret_r != 0 || strcmp(buf, SPRD_SD_TAG) != 0)
        {
			ALOGE("=== SDCard test failed! ===\n");
			ch = '8';
        }
        else
        {
			ALOGE("=== SDCard test succeed! ===\n");
			ch = '7';
        }
    }

    
    else if(ch == 's')
    {
        char* type = getenv("SECOND_STORAGE_TYPE");
        char* path;
        if(strcmp(type, "0") == 0 || strcmp(type, "1") == 0)
        {
		path = strcat(getenv("EXTERNAL_STORAGE"),"/fm2.txt");
        }
        else
	{
		path = strcat(getenv("SECONDARY_STORAGE"),"/fm2.txt");
	}
	char buf0[20] = {0};
	int rr = read_file_tag(SPRD_READ_FM_TESTFILE, buf0);
        int ret_w = write_file_tag(path, buf0);

	char buf[20] = {0};
        int ret_r = read_file_tag(path, buf);
        if(ret_w != 0 || ret_r != 0 || strcmp(buf, buf0) != 0)
        {
			ALOGE("=== SDCard save fm failed! ===\n");
			ch = '8';
        }
        else
        {
			ALOGE("=== SDCard save fm succeed! ===\n");
			ch = '7';
        }
    }

    else if(ch == 'f')
    {
        char* type = getenv("SECOND_STORAGE_TYPE");
        char* path;
       if(strcmp(type, "0") == 0 || strcmp(type, "1") == 0)
        {
		path = strcat(getenv("EXTERNAL_STORAGE"),"/fm2.txt");
        }
        else
	{
		path = strcat(getenv("SECONDARY_STORAGE"),"/fm2.txt");
	}
	char buf0[20] = {0};
	int rr = read_file_tag(path, buf0);
	if(rr == 0)
	{
		int ret_w = write_file_tag(SPRD_READ_FM_TESTFILE, buf0);

		char buf[20] = {0};
                int ret_r = read_file_tag(SPRD_READ_FM_TESTFILE, buf);
                if(ret_w != 0 || ret_r != 0 || strcmp(buf, buf0) != 0)
                {
			ALOGE("=== SDCard read fm failed! ===\n");
			ch = '8';
                }
                else
                {
			ALOGE("=== SDCard read fm succeed! ===\n");
			ch = '7';
                }
	}
	else
	{
		ALOGE("=== SDCard read fm failed! ===\n");
		ch = '8';
	}

    }
   
    fp = fopen("/data/data/com.spreadst.validationtools/vt.txt", "wb");
    if(fp == NULL)
    {
	ALOGE("=== open file /data/data/com.spreadst.validationtools/vt.txt failed! ===\n");
	return 1;
    }	
    fwrite(&ch, 1, 1, fp);
    fclose(fp);
    
    return 0;
}
