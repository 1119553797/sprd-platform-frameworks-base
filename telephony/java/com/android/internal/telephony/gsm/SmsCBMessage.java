/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.internal.telephony.gsm;

import com.android.internal.telephony.GsmAlphabet;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;

import android.database.Cursor;
import android.database.SQLException;

import static android.telephony.SmsMessage.ENCODING_7BIT;
import static android.telephony.SmsMessage.ENCODING_8BIT;
import static android.telephony.SmsMessage.ENCODING_16BIT;
import static android.telephony.SmsMessage.ENCODING_UNKNOWN;

import com.android.internal.telephony.GsmAlphabet;
import android.net.Uri;
import com.android.internal.util.HexDump;
import android.database.sqlite.SqliteWrapper;
import android.util.Log;
import java.io.UnsupportedEncodingException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import com.android.internal.telephony.IccUtils;

public class SmsCBMessage {
	private static final boolean LOCAL_DEBUG = true;
	private static final String TAG = "SmsCBMessage";

	public class LanguageClass {

		static final int GERMAN_ID = 0;
		static final int ENGLISH_ID = 1;
		static final int ITALIAN_ID = 2;
		static final int FRENCH_ID = 3;
		static final int SPANISH_ID = 4;
		static final int DUTCH_ID = 5;
		static final int SWEDISH_ID = 6;
		static final int DANISH_ID = 7;
		static final int PORTUGUESE_ID = 8;
		static final int FINNISH_ID = 9;
		static final int NORWEGIAN_ID = 10;
		static final int GREEK_ID = 11;
		static final int TURKISH = 12;
		static final int HUNGARIAN_ID = 13;
		static final int POLISH_ID = 14;
		static final int LANGUAGE_UNSPECIFIED = 15;
		static final int CHINESE_ID = 48;
	}

	public static final int MN_CB_MAX_PAGE_NUM = 15;
	/*
	 * (see TS 23.041)
	 */
	public static final int SMSCB_PAGE_LENGTH = 88;

	public static final int MAX_USER_DATA_BYTES = 93;

	public static final int CB_GS_CELL_WIDE = 0;

	public static final int CB_GS_PLMN_WIDE = 1;

	public static final int CB_GS_LAC_WIDE = 2;

	private static Context mContext;
	private static ContentResolver mContentResolver;
	private static SmsCBDcs mDcs;
	private static SmsCBPage mPage;
	private static ParseValue mRet;
	private static int mPhoneID;
	private static final char COMMA = ',';
	private static final char SEMICOLON = ';';
	private static final char CR = '\n';
	private static final char LF = '\t';
	private static boolean mIsPdu = true;
	private static final char CARRIAGE_RETURN = 0x0d;

	public SmsCBMessage(Context context, ContentResolver contentResolver) {
		mContext = context;
		mContentResolver = contentResolver;
	}

	public static class SmsCBPage {

		/*
		 * serial number ,see TS23.041
		 */
		public int gs;

		public int messageCode;

		public int updateNum;
		/*
		 * messge indentifier ,see TS23.041
		 */
		public int msgId;

		/*
		 * Data Coding Scheme ,see TS23.041
		 */
		public int dcs;

		/*
		 * it indicates page number within that sequence ,see TS23.041
		 */
		public int sequenceNum;

		/*
		 * it indicates the total number of pages of the CBS message ,see
		 * TS23.041
		 */
		public int totalNum;

		/*
		 * it indicates content of message ,see TS23.041
		 */
		public String content;
		public int langId;

		public byte[] decodedData;

	};

	public static class SmsCBDcs {

		public boolean classIsPresent;
		public int classType;
		public int alphabetType;
		public boolean languageIdPresent;
		public int languageId;
		public int bitOffset;
		public int byteOffset;

	};

	private static class ParseValue {
		public int ret;
		public int index;
	};

	protected String messageBody;

	private static ParseValue getValue(byte[] msg) {
		String log = new String(msg);
		mRet = new ParseValue();
		Log.i(TAG, "getValue msg :" + log);

		int j = 0;
		byte[] data = new byte[5];
		for (int i = 0; i < msg.length-1; i++) {
			Log.i(TAG, "getValue msg :" + msg[i]);
			if (msg[i] == COMMA ) {

				if (i == 0) {
					continue;
				} else {
					mRet.index = i;
					break;
				}
			} else {
                if(msg[i] == CR &&  msg[i+1] == LF){
                	
                	break;
                }
				data[j] = msg[i];
				j++;
			}

		}
		
		byte[] retData = new byte[j];
		
		for(int i=0;i<j; i++){
			
			retData[i] = (byte)(data[i] -0x30);
			
		}
		mRet.ret = 0;
		
	    for(int i=0;i<j; i++){
			
	    	mRet.ret = mRet.ret *10+(byte)(data[i] -0x30);
			
		}
			
	
		Log.i(TAG, "getValue ret :" + mRet.ret + "index  :" + mRet.index);
		return mRet;
	}

	private static SmsCBPage getOneSmsCBPage(String msg, int length ) {
		Log.i(TAG, "getSmsCBPage length" + msg.length());
		String midDecodeData;
		byte[] pages = msg.getBytes();

		ParseValue retSn = getValue(pages);

		String mid = msg.substring(retSn.index);

		byte[] byteMid = mid.getBytes();

		ParseValue retMid = getValue(byteMid);

		String midDcs = mid.substring(retMid.index);

		byte[] byteDcs = midDcs.getBytes();

		ParseValue retDcs = getValue(byteDcs);

		String midPage = midDcs.substring(retDcs.index);

		byte[] bytePage = midPage.getBytes();

		ParseValue retPage = getValue(bytePage);

		String midPages = midPage.substring(retPage.index);

		byte[] bytePages = midPages.getBytes();

		ParseValue retPages = getValue(bytePages);

		int contentPos = 0;
		for (int i = 0; i < pages.length - 1; i++) {

			if (pages[i] == CR && pages[i + 1] == LF) {

				contentPos = i + 2;
				break;
			}
		}

		Log.i(TAG, "getSmsCBPage pos" + contentPos);
				
		Log.i(TAG, "getSmsCBPage pos" + contentPos);
		String DecodeData = msg.substring(contentPos);
		byte[] byteDecodeData = DecodeData.getBytes();
		
		
	
		int encodingType = ENCODING_UNKNOWN;
		mPage = new SmsCBPage();

		byte[] byteSn = new byte[2];
		
		byteSn[1] = (byte)(retSn.ret &0xff);
		byteSn[0] = (byte)(retSn.ret>>0x8);
			
		

		mPage.gs = byteSn[0] >> 6 & 0x3;
		mPage.messageCode = ((byteSn[0] & 0x3F) <<4)| ((byteSn[1] >> 4) & 0xF);
		mPage.updateNum = byteSn[1] & 0xF;
		
		byte[] byteMsgId = new byte[2];
			
		byteMsgId[0] =	(byte)(retMid.ret&0xff);

		byteMsgId[1] = (byte)(retMid.ret>>0x08);
		
		mPage.msgId = byteMsgId[0] | (byteMsgId[1] << 8);

		mPage.dcs =  retDcs.ret;
	

		mPage.sequenceNum = retPage.ret;

	

		mPage.totalNum =  retPages.ret;

	   
		mPage.decodedData = byteDecodeData;
		Log.i(TAG, "getOneSmsCBPage gs :" + mPage.gs + "messageCode : "+mPage.messageCode + "updateNum: "+mPage.updateNum+
				"msgId: "+mPage.msgId+"dcs : "+mPage.dcs+"sequenceNum: "+mPage.sequenceNum +"totalNum: "+mPage.totalNum);
		

		mDcs = decodeSmsCBCds(mPage.dcs, mPage.decodedData, false);
		mPage.langId = mDcs.languageId;
		midDecodeData = msg.substring(contentPos);
		
		if(mDcs.byteOffset > 0 ){
			
			int newContentPos = 0;
			int i =0;
			for (i = contentPos; i < contentPos+3 ; i++) {

				if (pages[i] == CR ) {

					newContentPos = i+1;
					break;
				}
			}
			
			if(i< contentPos+3){
				
				midDecodeData = msg.substring(newContentPos);
			}
				
				
			
			
			
		}
		Log.v(TAG, "getOneSmsCBPage  midDecodeData: " +midDecodeData );
		
		//byte[] byteDecodeData =  IccUtils.hexStringToBytes(midDecodeData);	
		//Log.v(TAG, "getOneSmsCBPage ENCODING_16BIT byteDecodeData.length: " +byteDecodeData.length );
		//mPage.decodedData = byteDecodeData;
		
		Log.i(TAG, "getOneSmsCBPage encodingType" + encodingType);

		switch (mDcs.alphabetType) {

		case ENCODING_UNKNOWN:
		case ENCODING_8BIT:
			mPage.content = null;
			break;

		case ENCODING_7BIT:		
		    mPage.content = midDecodeData;//new String(mPage.decodedData);     
		    
		    
			break;

		case ENCODING_16BIT:
			
			byte atContentData[] = IccUtils.hexStringToBytes(midDecodeData);	
			
			for(int i=0; i<atContentData.length; i++){
				
				Log.v(TAG, "getOneSmsCBPage ENCODING_16BIT atContentData: " +atContentData[i] );
			}
			mPage.content = getUserDataUCS2(atContentData, 0);// (mDcs.byteOffset)
			break;
		}

		Log.v(TAG, "getOneSmsCBPage SMS message body (raw): " + mPage.content);

		return mPage;

	}

	/*
	 * get page information in pdu mode ,see TS 23.041 9.4.1.2
	 */
	public static byte[][] getSmsCBPage(String msg, int length,Context context, ContentResolver contentResolver) {

		Log.i(TAG, "getSmsCBPage length" + length);


		boolean isPdu = mIsPdu;

		mContext = context;
		mContentResolver = contentResolver;
		
		if (isPdu == false) {
			getOneSmsCBPage(msg, msg.length());
		} else {
						
			byte[] pages = msg.getBytes();
			
			
			
			int contentPos = 0;
			for(int i= 0; i<pages.length-1; i++){
				
				if (pages[i] == CR && pages[i + 1] == LF) {

					contentPos = i + 2;
					break;
				}
				
			}
			
			Log.i(TAG, "getSmsCBPage contentPos" + contentPos);
			
			int dataLen =  pages.length -contentPos;
			int j=0;
			byte contentPages[] = new byte[dataLen];
			
			for (int i=contentPos; i<pages.length; i++)
			{
				contentPages[j] = pages[i];
				
				//Log.i(TAG, "getSmsCBPage contentPages" + contentPages[j]);
				j++;
			}
			
			String atString = new String(contentPages);
			Log.i(TAG, "getSmsCBPage atString" + atString);
			byte atContentData[] = IccUtils.hexStringToBytes(atString);	
	

			mDcs = decodeSmsCBCds(atContentData[4], atContentData, true);
			
			Log.i(TAG, "getSmsCBPage mDcs" + mDcs.toString());
	
			
			
			parseUserData(atContentData, atContentData.length, mDcs);
		}
		return processSmsCBPage(mPage);

	}

/*
	 * get page information in pdu mode ,see TS 23.041 9.4.1.2
	 */
	public static byte[][] getSmsCBPage(String msg, int length,Context context, ContentResolver contentResolver, int phoneID) {

		Log.i(TAG, "getSmsCBPage length" + length);


		boolean isPdu = mIsPdu;

		mContext = context;
		mContentResolver = contentResolver;
                mPhoneID =  phoneID;
		
		if (isPdu == false) {
			getOneSmsCBPage(msg, msg.length());
		} else {
						
			byte[] pages = msg.getBytes();
			
			
			
			int contentPos = 0;
			for(int i= 0; i<pages.length-1; i++){
				
				if (pages[i] == CR && pages[i + 1] == LF) {

					contentPos = i + 2;
					break;
				}
				
			}
			
			Log.i(TAG, "getSmsCBPage contentPos" + contentPos);
			
			int dataLen =  pages.length -contentPos;
			int j=0;
			byte contentPages[] = new byte[dataLen];
			
			for (int i=contentPos; i<pages.length; i++)
			{
				contentPages[j] = pages[i];
				
				//Log.i(TAG, "getSmsCBPage contentPages" + contentPages[j]);
				j++;
			}
			
			String atString = new String(contentPages);
			Log.i(TAG, "getSmsCBPage atString" + atString);
			byte atContentData[] = IccUtils.hexStringToBytes(atString);	
	

			mDcs = decodeSmsCBCds(atContentData[4], atContentData, true);
			
			Log.i(TAG, "getSmsCBPage mDcs" + mDcs.toString());
	
			
			
			parseUserData(atContentData, atContentData.length, mDcs);
		}
		return processSmsCBPage(mPage,phoneID);

	}
	
	public static void setSmsCBMode(boolean isPdu){
		
	       Log.i(TAG, "getSmsCBPage mDcs" + isPdu);		
               mIsPdu = isPdu;
		
	}

	public static boolean getSmsCBMode(){
		
	       Log.i(TAG, "getSmsCBPage mDcs" + mIsPdu);		
               return mIsPdu ;
		
	}


	private static byte[][] convertToBytes(SmsCBPage[] pages, int totalNum) {
		byte[][] bytePages = new byte[totalNum][];

		for (int i = 0; i < totalNum; i++) {

			ByteArrayOutputStream baos = new ByteArrayOutputStream(400);

			baos.write(pages[i].gs);
			baos.write(pages[i].messageCode);
			baos.write(pages[i].updateNum);
			byte high =(byte) (pages[i].msgId >>0x08);
			baos.write(high);
			byte low = (byte)(pages[i].msgId &0xFF);
			baos.write(low);
			Log.i(TAG, "convertToBytes high " +high + "low :"
					+ low);
			//baos.write(pages[i].msgId);
			baos.write(pages[i].dcs);
			baos.write(pages[i].sequenceNum);
			baos.write(pages[i].totalNum);
			Log.i(TAG, "convertToBytes otput " + pages[i].content + "content :"
					+ pages[i].content + "msgId " + pages[i].msgId);
			byte[] byteContent = new byte[pages[i].content.getBytes().length];
			byteContent = pages[i].content.getBytes();
			Log.i(TAG, "convertToBytes otput " + byteContent
					+ "length of bytes array :" + byteContent.length);
			
			high =(byte) (pages[i].langId >>0x08);
			baos.write(high);
			low = (byte)(pages[i].langId &0xFF);
			baos.write(low);
			Log.i(TAG, "convertToBytes high " +high + "low :"
					+ low);
			
			baos.write(byteContent.length);
			baos.write(byteContent, 0, byteContent.length);
			// dos.writeBytes(pages[i].content);
			// dos.writeInt(pages[i].decodedData.length);
			// dos.write(pages[i].decodedData, 0, pages[i].decodedData.length);

			/**
			 * TODO(cleanup) -- The mPdu field is managed in a fragile manner,
			 * and it would be much nicer if accessing the serialized
			 * representation used a less fragile mechanism. Maybe the getPdu
			 * method could generate a representation if there was not yet one?
			 */

			bytePages[i] = baos.toByteArray();

		}

		return bytePages;

	}

	public static byte[][] processSmsCBPage(SmsCBPage page) {
		Uri RawUri = Uri.parse("content://sms/cbsmsraw");

		String[] CB_RAW_PROJECTION = new String[] { "gs", "message_code",
				"update_num", "message_id", "dcs", "count", "sequence","langId",
				"content" };

		SmsCBPage[] pages;

		// Lookup all other related parts
		StringBuilder where = new StringBuilder("gs =");
		where.append(page.gs);
	
		where.append(" AND message_code=");
		where.append(page.messageCode);
	
		where.append(" AND message_id=");
		where.append(page.msgId);
		
		StringBuilder whereArgs = new StringBuilder(page.msgId);
		
	    String [] ww = new String[]{whereArgs.toString()}; 
		Cursor cursor = null;
		int cursorCount = 0;
		boolean isNew = true;
		int i = 0;

		if(page.content == null){
			
			Log.v(TAG, "processSmsCBPage SMS message body is null !!! ");
			return null;
			
		}
		
		try {
			if (page.totalNum > 1) {

				cursor = SqliteWrapper.query(mContext, mContentResolver,
						RawUri, CB_RAW_PROJECTION, where.toString(), null, null);//ww
				//cursor = SqliteWrapper.query(mContext, mContentResolver,
				//		RawUri, CB_RAW_PROJECTION, null, null, null);

				if (cursor != null ) {
					cursorCount = cursor.getCount();
					
					Log.i(TAG, "processSmsCBPage cursorCount " + cursorCount + "page.totalNum "+page.totalNum);
					pages = new SmsCBPage[cursorCount + 1];


					for (i = 0; i < cursorCount; i++) {
						cursor.moveToNext();
						pages[i] = new SmsCBPage();

						int gsColumn = cursor.getColumnIndex("gs");

						int messageCodeColumn = cursor
								.getColumnIndex("message_code");

						int updateNumColumn = cursor.getColumnIndex("update_num");

						int messageColumn = cursor.getColumnIndex("message_id");

						int dcsColumn = cursor.getColumnIndex("dcs");

						int countColumn = cursor.getColumnIndex("count");

						int sequenceColumn = cursor.getColumnIndex("sequence");
						int langColumn = cursor.getColumnIndex("langId");
						int contentColumn = cursor.getColumnIndex("content");
						
						pages[i].gs = (byte) cursor.getInt(gsColumn);
						pages[i].messageCode = (byte) cursor
								.getInt(messageCodeColumn);
						pages[i].updateNum = (byte) cursor
								.getInt(updateNumColumn);
						pages[i].msgId = cursor.getInt(messageColumn);
						pages[i].totalNum = cursor.getInt(countColumn);
						pages[i].sequenceNum = cursor.getInt(sequenceColumn);
						pages[i].decodedData = (cursor.getString(contentColumn))
								.getBytes();
						pages[i].dcs = cursor.getInt(dcsColumn);
						pages[i].langId = cursor.getInt(langColumn);
						pages[i].content = cursor.getString(contentColumn);
						if (pages[i].gs == page.gs
								&& pages[i].messageCode == page.messageCode
								&& pages[i].updateNum == page.updateNum
								&& pages[i].msgId == page.msgId && pages[i].sequenceNum == page.sequenceNum) {

							isNew = false;
							return null;
						}

					}
					Log.i(TAG, "convertToBytes i " + i);
					pages[i] = new SmsCBPage();
					pages[i].gs = page.gs;
					pages[i].messageCode = page.messageCode;
					pages[i].updateNum = page.updateNum;
					pages[i].msgId = page.msgId;
					pages[i].totalNum = page.totalNum;
					pages[i].sequenceNum = page.sequenceNum;
					pages[i].decodedData = page.decodedData;
					pages[i].content = page.content;
					pages[i].langId = page.langId;
					
					
					if (cursorCount + 1 == page.totalNum) {
						
						
						SqliteWrapper
						.delete(mContext, mContentResolver, RawUri, where.toString(), null);// 
						return convertToBytes(pages, cursorCount + 1);
					}

				}else{
					
					
					
					
				}
			} else {
				pages = new SmsCBPage[1];
				pages[0] = new SmsCBPage();
				pages[0].gs = page.gs;
				pages[0].messageCode = page.messageCode;
				pages[0].updateNum = page.updateNum;
				pages[0].msgId = page.msgId;
				pages[0].totalNum = page.totalNum;
				pages[0].sequenceNum = page.sequenceNum;
				pages[0].decodedData = page.decodedData;
				pages[0].langId = page.langId;				
				pages[0].content = page.content;
				Log.i(TAG, "convertToBytes input " + "page.content :"
						+ page.content);
				Log.v(TAG, "processSmsCBPage SMS message count  "+ cursorCount);
				
			
				
				return convertToBytes(pages, 1);
			}
            
			if (isNew && cursorCount != page.totalNum) {
				// We don't have all the parts yet, store this one away
				ContentValues values = new ContentValues();
				values.put("gs", page.gs);
				values.put("message_code", page.messageCode);
				values.put("update_num", page.updateNum);
				values.put("message_id", page.msgId);
				values.put("dcs", page.dcs);
				values.put("count", page.totalNum);
				values.put("sequence", page.sequenceNum);
				values.put("langId", page.langId);
				values.put("content", page.content);

				Uri insertedUri = SqliteWrapper.insert(mContext,
						mContentResolver, RawUri, values);
				Log.i(TAG, " processSmsCBPage page number " + page.sequenceNum);
				return null;
			}
		

		} catch (SQLException e) {
			Log.e(TAG, "Can't access multipart SMS database", e);
			// TODO: Would OUT_OF_MEMORY be more appropriate?
			return null;
		} finally {
			if (cursor != null)
				cursor.close();
		}

		return null;
	}

public static byte[][] processSmsCBPage(SmsCBPage page, int phoneID) {
		Uri RawUri = Uri.parse("content://sms/cbsmsraw");

		String[] CB_RAW_PROJECTION = new String[] { "gs", "message_code",
				"update_num", "message_id", "dcs", "count", "sequence","langId",
				"content","phone_id" };

		SmsCBPage[] pages;

		// Lookup all other related parts
		StringBuilder where = new StringBuilder("gs =");
		where.append(page.gs);
	
		where.append(" AND message_code=");
		where.append(page.messageCode);
	
		where.append(" AND message_id=");
		where.append(page.msgId);
		
                where.append(" AND phone_id=");
		where.append(mPhoneID);
		/*StringBuilder whereArgs = new StringBuilder(page.msgId);
		
	    String [] ww = new String[]{whereArgs.toString()}; */
		Cursor cursor = null;
		int cursorCount = 0;
		boolean isNew = true;
		int i = 0;

		if(page.content == null){
			
			Log.v(TAG, "processSmsCBPage SMS message body is null !!! ");
			return null;
			
		}
		
		try {
			if (page.totalNum > 1) {

				cursor = SqliteWrapper.query(mContext, mContentResolver,
						RawUri, CB_RAW_PROJECTION, where.toString(), null, null);//ww
				//cursor = SqliteWrapper.query(mContext, mContentResolver,
				//		RawUri, CB_RAW_PROJECTION, null, null, null);

				if (cursor != null ) {
					cursorCount = cursor.getCount();
					
					Log.i(TAG, "processSmsCBPage cursorCount " + cursorCount + "page.totalNum "+page.totalNum);
					pages = new SmsCBPage[cursorCount + 1];


					for (i = 0; i < cursorCount; i++) {
						cursor.moveToNext();
						pages[i] = new SmsCBPage();

						int gsColumn = cursor.getColumnIndex("gs");

						int messageCodeColumn = cursor
								.getColumnIndex("message_code");

						int updateNumColumn = cursor.getColumnIndex("update_num");

						int messageColumn = cursor.getColumnIndex("message_id");

						int dcsColumn = cursor.getColumnIndex("dcs");

						int countColumn = cursor.getColumnIndex("count");

						int sequenceColumn = cursor.getColumnIndex("sequence");
						int langColumn = cursor.getColumnIndex("langId");
						int contentColumn = cursor.getColumnIndex("content");
						
						pages[i].gs = (byte) cursor.getInt(gsColumn);
						pages[i].messageCode = (byte) cursor
								.getInt(messageCodeColumn);
						pages[i].updateNum = (byte) cursor
								.getInt(updateNumColumn);
						pages[i].msgId = cursor.getInt(messageColumn);
						pages[i].totalNum = cursor.getInt(countColumn);
						pages[i].sequenceNum = cursor.getInt(sequenceColumn);
						pages[i].decodedData = (cursor.getString(contentColumn))
								.getBytes();
						pages[i].dcs = cursor.getInt(dcsColumn);
						pages[i].langId = cursor.getInt(langColumn);
						pages[i].content = cursor.getString(contentColumn);
						if (pages[i].gs == page.gs
								&& pages[i].messageCode == page.messageCode
								&& pages[i].updateNum == page.updateNum
								&& pages[i].msgId == page.msgId && pages[i].sequenceNum == page.sequenceNum) {

							isNew = false;
							return null;
						}

					}
					Log.i(TAG, "convertToBytes i " + i);
					pages[i] = new SmsCBPage();
					pages[i].gs = page.gs;
					pages[i].messageCode = page.messageCode;
					pages[i].updateNum = page.updateNum;
					pages[i].msgId = page.msgId;
					pages[i].totalNum = page.totalNum;
					pages[i].sequenceNum = page.sequenceNum;
					pages[i].decodedData = page.decodedData;
					pages[i].content = page.content;
					pages[i].langId = page.langId;
					
					
					if (cursorCount + 1 == page.totalNum) {
						
						
						SqliteWrapper
						.delete(mContext, mContentResolver, RawUri, where.toString(), null);// 
						return convertToBytes(pages, cursorCount + 1);
					}

				}else{
					
					
					
					
				}
			} else {
				pages = new SmsCBPage[1];
				pages[0] = new SmsCBPage();
				pages[0].gs = page.gs;
				pages[0].messageCode = page.messageCode;
				pages[0].updateNum = page.updateNum;
				pages[0].msgId = page.msgId;
				pages[0].totalNum = page.totalNum;
				pages[0].sequenceNum = page.sequenceNum;
				pages[0].decodedData = page.decodedData;
				pages[0].langId = page.langId;				
				pages[0].content = page.content;
				Log.i(TAG, "convertToBytes input " + "page.content :"
						+ page.content);
				Log.v(TAG, "processSmsCBPage SMS message count  "+ cursorCount);
				
			
				
				return convertToBytes(pages, 1);
			}
            
			if (isNew && cursorCount != page.totalNum) {
				// We don't have all the parts yet, store this one away
				ContentValues values = new ContentValues();
				values.put("gs", page.gs);
				values.put("message_code", page.messageCode);
				values.put("update_num", page.updateNum);
				values.put("message_id", page.msgId);
				values.put("dcs", page.dcs);
				values.put("count", page.totalNum);
				values.put("sequence", page.sequenceNum);
				values.put("langId", page.langId);
				values.put("content", page.content);
                                values.put("phone_id", phoneID);

				Uri insertedUri = SqliteWrapper.insert(mContext,
						mContentResolver, RawUri, values);
				Log.i(TAG, " processSmsCBPage page number " + page.sequenceNum);
				return null;
			}
		

		} catch (SQLException e) {
			Log.e(TAG, "Can't access multipart SMS database", e);
			// TODO: Would OUT_OF_MEMORY be more appropriate?
			return null;
		} finally {
			if (cursor != null)
				cursor.close();
		}

		return null;
	}

	private static SmsCBDcs decodeSmsCBCds(int dcsData, byte[] msg,
			boolean isPdu) {

		int lang_id_h = 0;
		int lang_id_l = 0;
		int dcs_data = 0;

		if (isPdu) {
			dcs_data = msg[4];
		} else {
			dcs_data = dcsData;

		}
		Log.i(TAG, "decodeSmsCBCds dcs_data" + dcs_data);
		int coding_group = (dcs_data & 0xC0);
		mDcs = new SmsCBDcs();
		mDcs.byteOffset = 6;
		Log.i(TAG, "decodeSmsCBCds coding_group : "+coding_group);
		mDcs.bitOffset = 0;
		switch (coding_group) {
		// 00xx xxxx
		case 0x00:
			mDcs.classIsPresent = false;
			int tmp = (dcs_data & 0xF0);
			Log.i(TAG, "decodeSmsCBCds (dcs_data & 0xF0) "+tmp);
			switch ((dcs_data & 0xF0)) {
			// 0000 xxxx
			case 0x00:
				mDcs.alphabetType = ENCODING_7BIT;
				mDcs.languageIdPresent = true;
				mDcs.languageId = (dcs_data & 0x0F);
				break;
			// 0001 xxxx
			case 0x10:
				switch (dcs_data & 0x0F) {
				// 0001 0000
				case 0x00:
					// The first 3 characters of the message are a two-character
					// representation
					// of the language encoded according to ISO 639 [12],
					// followed by a CR character.
					// The CR character is then followed by 90 characters of
					// text.
				
					mDcs.byteOffset = 8;
					mDcs.bitOffset = 5;
					mDcs.languageIdPresent = true;
					if (isPdu) {
						lang_id_h = (byte) (msg[6] & 0x7f);
						lang_id_l = (byte) ((msg[7] & 0x3f) << 1)
								| ((msg[6] & 0x80) >> 7);
					} else {
						//lang_id_h = (byte) (msg[0] & 0x7f);
						//lang_id_l = (byte) ((msg[1] & 0x3f) << 1)
							//	| ((msg[0] & 0x80) >> 7);
						lang_id_h = msg[0];
						lang_id_l = msg[1];

					}
					mDcs.languageId = (lang_id_h << 8) | lang_id_l;
					
					mDcs.alphabetType = ENCODING_7BIT;
					Log.d(TAG, "SMSCB:language id =" + lang_id_h + lang_id_l);
					break;
				// 0001 0001
				case 0x01:
					// The message starts with a two GSM 7-bit default alphabet
					// character representation
					// of the language encoded according to ISO 639 [12]. This
					// is padded to the octet
					// boundary with two bits set to 0 and then followed by 40
					// characters of UCS2-encoded message.
					mDcs.byteOffset = 8;
					mDcs.languageIdPresent = true;
					if (isPdu) {
						lang_id_h = (msg[6] & 0x7f);
						lang_id_l = (byte) ((msg[7] & 0x3f) << 1)
								| ((msg[7] & 0x80) >> 7);
					} else {
						//lang_id_h = (msg[0] & 0x7f);
						//lang_id_l = (byte) ((msg[1] & 0x3f) << 1)
							//	| ((msg[1] & 0x80) >> 7);
						lang_id_h = msg[0];
						lang_id_l = msg[1];
					}
					mDcs.languageId = (lang_id_h << 8) | lang_id_l;
					mDcs.alphabetType = ENCODING_16BIT;
					Log.d(TAG, "SMSCB:language id = " + lang_id_h + lang_id_l);
					break;
				default:
					mDcs.languageIdPresent = false;
					mDcs.alphabetType = ENCODING_UNKNOWN;
					break;
				}
				break;
			// 0010 xxxx
			case 0x20:
				switch (dcs_data & 0x0F) {
				// 0010 0000 -- 0010 0100
				case 0x00:
				case 0x01:
				case 0x02:
				case 0x03:
				case 0x04:
					mDcs.alphabetType = ENCODING_7BIT;
					mDcs.languageIdPresent = true;
					mDcs.languageId = dcs_data;
					break;
				default:
					mDcs.alphabetType = ENCODING_UNKNOWN;
					mDcs.languageIdPresent = false;
					break;
				}
				break;
			// 0011 xxxx
			case 0x30:
			default:
				mDcs.languageIdPresent = false;
				mDcs.alphabetType = ENCODING_UNKNOWN;
				break;
			}
			break;
		// 01xx xxxx
		case 0x40:
			mDcs.languageIdPresent = false;

			if (0x00 == (dcs_data & 0x20)) {
				if (0x00 == (dcs_data & 0x10)) {
					mDcs.classIsPresent = false;
				} else {
					mDcs.classIsPresent = true;
					mDcs.classType = (dcs_data & 0x03);
				}
				mDcs.alphabetType = ((dcs_data & 0x0c) >> 2) + 1;
			} else {
				mDcs.classIsPresent = false;
				mDcs.alphabetType = ENCODING_UNKNOWN;
			}
			break;
		// 10xx xxxx
		case 0x80:
			mDcs.languageIdPresent = false;
			switch (dcs_data & 0xF0) {
			// 1001 xxxx
			case 0x90:
				mDcs.classIsPresent = true;
				mDcs.classType = dcs_data & 0x03;
				mDcs.alphabetType = ((dcs_data & 0x0c) >> 2) + 1;
				break;
			// 1000 xxxx,1010..1101 xxxx
			default:
				mDcs.classIsPresent = false;
				mDcs.alphabetType = ENCODING_UNKNOWN;
				break;
			}
			break;
		// 11xx xxxx
		case 0xc0:
			// 1111 xxxx
			if (0x30 == (dcs_data & 0x30)) {
				mDcs.classIsPresent = true;
				mDcs.classType = (dcs_data & 0x03);
				mDcs.alphabetType = ((dcs_data & 0x04) >> 2) + 1;
				break;
			}
			// 1110 xxxx
			else if (0x20 == (dcs_data & 0x30)) {
				Log.d(TAG, "MNSMSCB:code Defined by the WAP Forum.");
			}
			// Else: we handle it as other default value.
			// lint -fallthrough
		default:
			mDcs.classIsPresent = false;
			mDcs.languageIdPresent = false;
			mDcs.alphabetType = ENCODING_7BIT;
			break;
		}
		Log.d(TAG, "mDcs classIsPresent :" + mDcs.classIsPresent
				+ "alphabetType :" + mDcs.alphabetType + "bitoffset :"
				+ mDcs.bitOffset + "byteoffset :" + mDcs.byteOffset);
		
		if(!isPdu){
			
			mDcs.byteOffset = mDcs.byteOffset -6;
			
		}
		return mDcs;

	}

	/**
	 * Interprets the user data payload as UCS2 characters, and decodes them
	 * into a String.
	 * 
	 * @param byteCount
	 *            the number of bytes in the user data payload
	 * @return a String with the decoded characters
	 */
	private static String getUserDataUCS2(byte[] msg, int byteOffset) {
		String ret;
               Log.i(TAG, "getUserDataUCS2 byteOffset " +byteOffset + "msg.length " + msg.length );
		try {
			ret = new String(msg, byteOffset,( msg.length -byteOffset), "utf-16");

			//String test = "4F60597DFF01";
			//byte atContentData[] = IccUtils.hexStringToBytes(test);				
			//ret = new String(atContentData,"utf-16");
		} catch (UnsupportedEncodingException ex) {
			ret = "";
			Log.e(TAG, "implausible UnsupportedEncodingException", ex);
		}
		Log.i(TAG, "getUserDataUCS2"+ret);
		return ret;
	}

	/**
	 * Parses the User Data of an SMS.
	 * 
	 * @param p
	 *            The current PduParser.
	 * @param hasUserDataHeader
	 *            Indicates whether a header is present in the User Data.
	 */
	private static SmsCBPage parseUserData(byte[] msg, int length, SmsCBDcs dcs) {

		int encodingType = ENCODING_UNKNOWN;
	
		mPage = new SmsCBPage();
		mPage.gs = msg[0] >> 6 & 0x3;
		mPage.messageCode =( (msg[0] & 0x3F) <<4) | ((msg[1] >> 4) & 0xF);
		mPage.updateNum = msg[1] & 0xF;
		mPage.msgId = msg[2] <<8 | msg[3] ;
		mPage.dcs = msg[4];
		mPage.sequenceNum = (msg[5] >> 4) & 0x0F;
		mPage.totalNum = msg[5] & 0x0F;
		mPage.langId = mDcs.languageId;
		mPage.decodedData = new byte[MAX_USER_DATA_BYTES];
		Log.i(TAG, "parseUserData length" + length);

		if (length - 6 <= 0) {

			return null;
		}

	//	for (int i = 0; i < length ; i++) {
		//	Log.i(TAG,"parseUserData"+msg[i]);
	//	}
                Log.i(TAG, "parseUserData gs :" + mPage.gs + "messageCode : "+mPage.messageCode + "updateNum: "+mPage.updateNum+
				"msgId: "+mPage.msgId+"dcs : "+mPage.dcs+"sequenceNum: "+mPage.sequenceNum +"totalNum: "+mPage.totalNum);
		
		encodingType = dcs.alphabetType;
		Log.i(TAG, "parseUserData encodingType" + encodingType);

		switch (encodingType) {
		case ENCODING_UNKNOWN:
		case ENCODING_8BIT:
			mPage.content = null;
			break;

		case ENCODING_7BIT:

			//mPage.content = "1234567890123456789012345678901234567890";
			// tmp;
			// mPage.content = GsmAlphabet.gsm7BitPackedToString(msg,
				//	 6, msg.length,// @???
					// 0);
			 int len = ((msg.length-dcs.byteOffset)*8 - dcs.bitOffset)/7;
			 
			 Log.i(TAG, "parseUserData byteoffset  :" +dcs. byteOffset + "bitOffset : " +dcs.bitOffset + "7bit len " + len);
			 
		//1026 for compile	
			 mPage.content = GsmAlphabet.gsm7BitPackedToString(msg,
			 dcs.byteOffset,len,// @???
			 dcs.bitOffset, 0, 0);
			break;

		case ENCODING_16BIT:
			mPage.content = getUserDataUCS2(msg, dcs.byteOffset);// @???
			break;
		}

		Log.v(TAG, "decodeSmsCBCds body (raw): " + mPage.content);

		return mPage;
	}

}
