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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.FileNotFoundException;

public class ContractFileUtils {

    private static final String TAG = "ContractFileUtils";

    public static Uri findFileInFolderTree(Context context, Uri folderUri, String baseFileName) {
        // Get content resolver
        ContentResolver contentResolver = context.getContentResolver();

        // Build URI for children of the folder
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, DocumentsContract.getTreeDocumentId(folderUri));

        // Define the columns we want from the query (e.g., document ID, display name, and MIME type)
        String[] projection = new String[] {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };

        // Query the folder for its children
        Cursor cursor = contentResolver.query(childrenUri, projection, null, null, null);

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    // Get document ID, display name, and MIME type of each child
                    int idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
                    int nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                    int typeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
                    if ( (idIndex < 0) || (nameIndex < 0) || (typeIndex < 0) ) continue;
                    String documentId = cursor.getString(idIndex);
                    String displayName = cursor.getString(nameIndex);
                    String mimeType = cursor.getString(typeIndex);

                    // Build URI for this child
                    Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, documentId);

                    // Check if this is a directory (MIME type "vnd.android.document/directory")
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                        // Recursive search in this subfolder
                        Uri result = findFileInFolderTree(context, documentUri, baseFileName);
                        if (result != null) {
                            return result; // Found the file in a subfolder
                        }
                    } else {
                        // Check if file matches the base name with or without .json extension
                        if (displayName.equals(baseFileName) || displayName.equals(baseFileName + ".json")) {
                            return documentUri; // Found the file
                        }
                    }
                }
            } finally {
                cursor.close();
            }
        }

        // Return null if file not found in this folder or any subfolders
        return null;
    }

    public static String getFileNameFromUri(Context context, Uri fileUri) {
        String displayName = null;

        // Define the projection to specify the columns you want to retrieve (DISPLAY_NAME in this case)
        String[] projection = { DocumentsContract.Document.COLUMN_DISPLAY_NAME };

        // Query the Uri using ContentResolver
        Cursor cursor = context.getContentResolver().query(fileUri, projection, null, null, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    // Get the DISPLAY_NAME column's value (which is the filename)
                    displayName = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }

        return displayName;
    }

    public static @Nullable Uri createJSONFileInEPOFolder(ContentResolver resolver, Uri folderUri, String fileName) throws FileNotFoundException {
        //Create the file
        // Convert the tree URI to a document URI
        Uri parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
                folderUri, DocumentsContract.getTreeDocumentId(folderUri));
        Uri destinationFileUri = DocumentsContract.createDocument(resolver, parentDocumentUri, "application/json", fileName);
        if (destinationFileUri != null) {
            Log.i(TAG, "File created successfully: " + destinationFileUri);
        } else {
            Log.e(TAG, "File creation failed.");
        }
        return destinationFileUri;
    }
}
