/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.media;

import android.content.ContentValues;
import android.content.IContentProvider;
import android.content.IContentProviderEx;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.lang.IllegalArgumentException;

/**
 * A MediaScanner helper class which enables us to do lazy Deleting on the
 * given provider. This class manages buffers internally and flushes when they
 * are full. Note that you should call flushAll() after using this class.
 * {@hide}
 */
public class MediaDeleter {
    private static class DeleteData {
        Uri mUri;
        String mUserWhere;
		String[] mWhereArgs;
        public DeleteData(Uri uri, String userWhere, String[] whereArgs) {
            mUri = uri;
            mUserWhere = userWhere;
            mWhereArgs = whereArgs;
        }
    }

    ArrayList<DeleteData> mDeleteList = new ArrayList<DeleteData>();
    
    private IContentProviderEx mProvider;
    private int mBufferSizePerUri;

    public MediaDeleter(IContentProvider provider, int bufferSizePerUri) {
        if (!(provider instanceof IContentProviderEx))
            throw new IllegalArgumentException("MediaDeleter only accept a provider which implements IContentProviderEx");
        mProvider = (IContentProviderEx)provider;
        mBufferSizePerUri = bufferSizePerUri;
    }

    public void delete(Uri uri, String userWhere, String[] whereArgs) throws RemoteException {
        List<DeleteData> list = mDeleteList;
        list.add(new DeleteData(uri, userWhere, whereArgs));
        if (list.size() >= mBufferSizePerUri) {
            flushAll();
        }
    }

    public void flushAll() throws RemoteException {
        Log.d("MediaDeleter", "flashing");
        List<DeleteData> list = mDeleteList;
        int length = 0;
        if (!list.isEmpty()) {
			length = list.size();
            Uri[] urisArray = new Uri[length];
            String[] userWhereArray = new String[length];
			String[][] whereArgsArray = new String[length][];
            for (int i = 0; i < length; i++) {
                urisArray[i] = list.get(i).mUri;
                userWhereArray[i] = list.get(i).mUserWhere;
				whereArgsArray[i] = list.get(i).mWhereArgs;
            }
            mProvider.bulkDelete(urisArray, userWhereArray, whereArgsArray);
            list.clear();
        }
        Log.d("MediaDeleter", "" + length + " operations flushed");
    }

}
