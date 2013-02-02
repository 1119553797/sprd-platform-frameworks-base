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
//	    property_get(BTUT_TEST, buf, "1");
//	    if(strcmp(buf, "0") == 0)
		if(ch == '3')
	    {
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
	    }
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
    	int ret_w = write_file_tag(SPRD_SD_TESTFILE, SPRD_SD_TAG);
    	char buf[20] = {0};
    	int ret_r = read_file_tag(SPRD_SD_TESTFILE, buf);
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
