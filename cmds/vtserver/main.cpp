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
#define SPRD_SD_TESTFILE "/sdcard/test.txt"
#define SPRD_FM_SDPATH "/sdcard/fmtest.txt"
#define SPRD_FM_DATAPATH "/data/data/com.spreadst.validationtools/fm.txt"
#define SPRD_FM_NOFMFLAG "nofm"
#define SPRD_SD_TAG "SDCardTest"

int write_file_tag(char* filename, char* buf)
{
	FILE *fp = fopen(filename, "w");
	if(fp == NULL)
	{
		LOGE("=== open file to write failed! ===\n");
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
		LOGE("=== open file to read failed! ===\n");
		return 1;
	}
	fscanf(fp, "%s", buf);
	fclose(fp);
	return 0;
}

int main(int argc, char** argv)
{
	LOGI("=== vtserver start! ===\n");
	char ch;

	FILE *fp = fopen("/data/data/com.spreadst.validationtools/vt.txt", "rb");
	if(fp == NULL)
	{
		LOGE("=== open file /data/data/com.spreadst.validationtools/vt.txt failed! ===\n");
		return 1;
	}
	fread(&ch, 1, 1, fp);
	fclose(fp);

	if(ch == 'f')
	{
		char buf[20] = {0};
		int ret_r = read_file_tag(SPRD_FM_DATAPATH, buf);
		if(ret_r != 0 || strlen(buf) == 0 || strcmp(buf, SPRD_FM_NOFMFLAG) == 0)
		{
			//no fm recorder in /data/data/com.spreadst.validationtools,
			//so try sync SPRD_FM_SDPATH to SPRD_FM_DATAPATH
			LOGI("sync SPRD_FM_SDPATH to SPRD_FM_DATAPATH\n");
			memset(buf, 0 , sizeof(buf));
			ret_r = read_file_tag(SPRD_FM_SDPATH, buf);
			if(ret_r != 0 || strlen(buf) == 0 || strcmp(buf, SPRD_FM_NOFMFLAG) == 0)
			{
				//get fm recorder failed
				LOGI("get fm recorder failed\n");
				write_file_tag(SPRD_FM_DATAPATH, SPRD_FM_NOFMFLAG);
			}
			else
			{
				//get fm recorder success
				LOGI("get fm recorder success\n");
				write_file_tag(SPRD_FM_DATAPATH, buf);
			}
		}
		else
		{
			//sync SPRD_FM_SDPATH to SPRD_FM_DATAPATH
			LOGI("sync SPRD_FM_SDPATH to SPRD_FM_DATAPATH\n");
			write_file_tag(SPRD_FM_SDPATH, buf);
		}
	}
	else
	{
		if(ch == '0')
		//property_get(CTP_TEST, buf, "1");
		//if(strcmp(buf, "0") == 0)
		{
			LOGE("=== receive CTP test requirement! ===\n");
			LOGE("=== CTP test start! ===\n");
			int error = system("echo 1 > /sys/devices/platform/sc8810-i2c.2/i2c-2/2-005c/calibrate");
			if(error == -1 || error == 127)
			{
				LOGE("=== CTP test failed! ===\n");
				ch = '2';
				//property_set(CTP_TEST, "-1");
			}
			else
			{
				LOGE("=== CTP test succeed! ===\n");
				ch = '1';
				//property_set(CTP_TEST, "1");
			}
		}
		else if(ch == '3')
		{
			//property_get(BTUT_TEST, buf, "1");
			//if(strcmp(buf, "0") == 0)
			if(ch == '3')
			{
				LOGE("=== receive BTUT test requirement! ===\n");
				LOGE("=== BTUT test start! ===\n");
				int error = system("hciconfig hci0 up");
				if(error == -1 || error == 127)
				{
					LOGE("=== BTUT test failed on cmd 1! ===\n");
					ch = '5';
					//property_set(BTUT_TEST, "-1");
				}
				else
				{
					error = system("hcitool cmd 0x03 0x0005 0x02 0x00 0x02");
					if(error == -1 || error == 127)
					{
						LOGE("=== BTUT test failed on cmd 2! ===\n");
						ch = '5';
						//property_set(BTUT_TEST, "-1");
					}
					else
					{
						error = system("hcitool cmd 0x03 0x001A 0x03");
						if(error == -1 || error == 127)
						{
							LOGE("=== BTUT test failed on cmd 3! ===\n");
							ch = '5';
							//property_set(BTUT_TEST, "-1");
						}
						else
						{
							error = system("hcitool cmd 0x06 0x0003");
							if(error == -1 || error == 127)
							{
								LOGE("=== BTUT test failed on cmd 4! ===\n");
								ch = '5';
								//property_set(BTUT_TEST, "-1");
							}
							else
							{
								LOGE("=== BTUT test succeed! ===\n");
								ch = '4';
								//property_set(BTUT_TEST, "1");
							}
						}
					}
				}
			}
		}
		else if(ch == '6')
		{
			int ret_w = write_file_tag(SPRD_SD_TESTFILE, SPRD_SD_TAG);
			char buf[20] = {0};
			int ret_r = read_file_tag(SPRD_SD_TESTFILE, buf);
			if(ret_w != 0 || ret_r != 0 || strcmp(buf, SPRD_SD_TAG) != 0)
			{
				LOGE("=== SDCard test failed! ===\n");
				ch = '8';
			}
			else
			{
				LOGE("=== SDCard test succeed! ===\n");
				ch = '7';
			}
		}

		fp = fopen("/data/data/com.spreadst.validationtools/vt.txt", "wb");
		if(fp == NULL)
		{
			LOGE("=== open file /data/data/com.spreadst.validationtools/vt.txt failed! ===\n");
			return 1;
		}
		fwrite(&ch, 1, 1, fp);
		fclose(fp);
	}
	return 0;
}

/*
int main(int argc, char** argv)
{
	LOGE("=== vtserver start! ===\n");

    int socket = 0;
    char buf[10] = {0};
    int ret = 0;
    int fd = 0;

	socket = android_get_control_socket("vtsocket");
	if (socket < 0)
    {
		LOGE("=== create socket failed! ===\n");
		exit(-1);
    }
	ret = listen(socket, 1);
	if (ret < 0)
	{
		LOGE("=== listen socket failed! ===\n");
		exit(-1);
    }

	fd_set rdfds;
	FD_ZERO(&rdfds);
	FD_SET(socket, &rdfds);
	while(1)
	{
		memset(buf, 0x00, sizeof(buf));
		int client_sockfd = accept(socket, NULL, NULL);
		if(client_sockfd >= 0)
		{
			recv(client_sockfd, buf, sizeof(buf)-1, 0);
			if(!strcmp(buf, "CTP"))
			{
				LOGE("=== receive CTP test requirement! ===\n");
				LOGE("=== CTP test start! ===\n");
				int error = system("echo 1 > /sys/devices/platform/sc8810-i2c.2/i2c-2/2-005c/calibrate");
				if(error == -1 || error == 127)
				{
					LOGE("=== CTP test failed! ===\n");
					send(client_sockfd, "0", 1, 0);
				}
				else
				{
					LOGE("=== CTP test succeed! ===\n");
					send(client_sockfd, "1", 1, 0);
				}
			}
			else if(!strcmp(buf, "BTUT"))
			{
				LOGE("=== receive BTUT test requirement! ===\n");
				LOGE("=== BTUT test start! ===\n");
				int error = system("hciconfig hci0 up");
				if(error == -1 || error == 127)
				{
					LOGE("=== BTUT test failed on cmd 1! ===\n");
					send(client_sockfd, "0", 1, 0);
				}
				else
				{
					error = system("hcitool cmd 0x03 0x0005 0x02 0x00 0x02");
					if(error == -1 || error == 127)
					{
						LOGE("=== BTUT test failed on cmd 2! ===\n");
						send(client_sockfd, "0", 1, 0);
					}
					else
					{
						error = system("hcitool cmd 0x03 0x001A 0x03");
						if(error == -1 || error == 127)
						{
							LOGE("=== BTUT test failed on cmd 3! ===\n");
							send(client_sockfd, "0", 1, 0);
						}
						else
						{
							error = system("hcitool cmd 0x06 0x0003");
							if(error == -1 || error == 127)
							{
								LOGE("=== BTUT test failed on cmd 4! ===\n");
								send(client_sockfd, "0", 1, 0);
							}
							else
							{
								LOGE("=== BTUT test succeed! ===\n");
								send(client_sockfd, "1", 1, 0);
							}
						}
					}
				}
			}
		}
		close(client_sockfd);
	}
	close(socket);
	FD_CLR(socket, &rdfds);
	return 0;
}
*/
