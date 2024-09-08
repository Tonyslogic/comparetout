/*
 * Copyright (c) 2024. Tony Finnerty
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.tfcode.comparetout.util;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.documentfile.provider.DocumentFile;

import com.tfcode.comparetout.TOUTCApplication;

import java.util.List;

import io.reactivex.rxjava3.core.Single;

public abstract class AbstractEPOFolderActivity extends AppCompatActivity {

    public static final String EPO_FILE_LOCATION_KEY = "files_uri";

    private static final String TAG = "AbstractEPOFolderActivity";
    private Uri mEPOFolderUri; // Store the URI of the subfolder
    private boolean mWritable = false;
    private EPOFolderCreatedCallback mCallback;

    protected Uri getEPOFolderUri() {
        return mEPOFolderUri;
    }

    protected void pickFolderWithPermission(EPOFolderCreatedCallback callback) {
        Log.i(TAG, "Picking folder and asking for permission");
        mCallback = callback;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        pickFolderAndGetPermission.launch(intent);
    }

    protected boolean isWriteAccessPresent() {
        if (null == mEPOFolderUri) return false;
        mWritable = false; // we need to assume not in case the permission has been revoked

        // Use DocumentFile to check if the folder is a directory
        DocumentFile documentFile = DocumentFile.fromTreeUri(this, mEPOFolderUri);
        boolean isDir = documentFile != null && documentFile.isDirectory();

        if (isDir) {
            final List<UriPermission> persistedUriPermissions = getContentResolver().getPersistedUriPermissions();
            for (UriPermission persistedUriPermission : persistedUriPermissions) {
                Uri permissionUri = persistedUriPermission.getUri();
                if (mEPOFolderUri.equals(permissionUri) && persistedUriPermission.isWritePermission()) {
                    mWritable = true;
                    break;
                }
                if (isDescendantOf(mEPOFolderUri, permissionUri) && persistedUriPermission.isWritePermission()) {
                    mWritable = true;
                    break;
                }
            }
        }
        Log.i(TAG, "Folder: " + mEPOFolderUri);
        Log.i(TAG, "Writable: " + mWritable);

        return mWritable;
    }

    private boolean isDescendantOf(Uri childUri, Uri parentUri) {
        String childPath = childUri.getPath();
        String parentPath = parentUri.getPath();
        return childPath != null && parentPath != null && childPath.startsWith(parentPath);
    }

    private void setEPOFolderUri(Uri uri) {
        mEPOFolderUri = uri;
        mWritable = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadFolderFromDataStore();
    }

    private void loadFolderFromDataStore() {
        new Thread(() -> {
            Preferences.Key<String> location_uri_string = PreferencesKeys.stringKey(EPO_FILE_LOCATION_KEY);
            TOUTCApplication application = ((TOUTCApplication)getApplication());
            Single<String> value = application.getDataStore()
                    .data().firstOrError()
                    .map(prefs -> prefs.get(location_uri_string))
                    .onErrorReturnItem("content://com.android.externalstorage.documents/tree/primary%3ADownload%2FEPO");
            String ret =  value.blockingGet();
            System.out.println("Got a value from the dataStore:" + ret);
            setEPOFolderUri(Uri.parse(ret));
        }).start();
    }

    private void saveFolderToDataStore(Uri treeUri) {
        setEPOFolderUri(treeUri);
        TOUTCApplication application = ((TOUTCApplication)getApplication());
        application.putStringValueIntoDataStore(EPO_FILE_LOCATION_KEY, mEPOFolderUri.toString());
    }

    private final ActivityResultLauncher<Intent> pickFolderAndGetPermission = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                Intent data = result.getData();
                if (data != null) {
                    Uri treeUri = data.getData();
                    grantPersistentAccess(treeUri);
                    findOrCreateSubfolder(treeUri);
                }
                else { // The user OK'd the intent without picking a folder
                    mCallback.folderCreatedWithPermission(false);
                }
            }
            else {// The user cancelled the intent
                mCallback.folderCreatedWithPermission(false);
            }
        }
    );

    private void grantPersistentAccess(Uri uri) {
        ContentResolver resolver = getContentResolver();
        resolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }

    private void findOrCreateSubfolder(Uri treeUri) {
            setEPOFolderUri(treeUri);
            saveFolderToDataStore(mEPOFolderUri);
            boolean writeCheck = isWriteAccessPresent();
            new Thread(() -> mCallback.folderCreatedWithPermission(writeCheck)).start();
    }
}
