/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.content;

import android.net.Uri;
import android.os.RemoteException;

/**
 * The ipc interface extends IContentProvider to provider bulkUpdate and bulkDelete
 * @hide
 */
public interface IContentProviderEx extends IContentProvider {
    public int bulkDelete(Uri[] uri, String[] userWheres, String[][] whereArgs) throws RemoteException;

    public int bulkUpdate(Uri[] uri, ContentValues[] initialValues, String[] userWheres,
            String[][] whereArgs) throws RemoteException;
    public int resetSequence() throws RemoteException;
}
