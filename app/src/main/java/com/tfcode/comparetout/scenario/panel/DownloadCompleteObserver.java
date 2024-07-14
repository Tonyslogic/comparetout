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

package com.tfcode.comparetout.scenario.panel;

import static com.tfcode.comparetout.scenario.panel.PVGISActivity.STATE_LOADING_DB;

import android.app.DownloadManager;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

public class DownloadCompleteObserver extends ContentObserver {

    private final Context context;
    private final Handler handler;
    private final PVGISActivity pvgisActivity;
    private long downloadId;
    private static final String TAG = "DownloadCompleteObserver";
    private File tempFile;
    private DocumentFile newFile;

    public DownloadCompleteObserver(Context context, Handler handler, PVGISActivity pvgisActivity) {
        super(handler);
        this.context = context;
        this.handler = handler;
        this.pvgisActivity = pvgisActivity;
    }

    @Override
    public void onChange(boolean selfChange) {
        super.onChange(selfChange);

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadId);

        Cursor cursor = downloadManager.query(query);
        if (cursor != null && cursor.moveToFirst()) {
            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int status = cursor.getInt(statusIndex);

            switch (status) {
                case DownloadManager.STATUS_SUCCESSFUL:
                    // Handle successful download completion
                    if (!(null == tempFile) && !(null == newFile))
                        copyFileToDocumentFile(context, tempFile, newFile);
                    Log.i(TAG, "Download completed");
                    pvgisActivity.scheduleLoad(context);
                    handler.post(() -> pvgisActivity.updateStatusView(STATE_LOADING_DB));
                    break;
                case DownloadManager.STATUS_FAILED:
                    // Handle download failure
                    Log.i(TAG, "Download failed");
                    break;
                case DownloadManager.STATUS_PAUSED:
                    // Handle download paused
                    Log.i(TAG, "Download paused");
                    break;
                case DownloadManager.STATUS_PENDING:
                    // Handle download pending
                    Log.i(TAG, "Download pending");
                    break;
                case DownloadManager.STATUS_RUNNING:
                    // Handle download in progress
                    Log.i(TAG, "Download in progress");
                    break;
            }
        }
        if (!(null == cursor) && !cursor.isClosed()) cursor.close();
    }

    public void register(long downloadId, File tempFile, DocumentFile newFile) {
        this.downloadId = downloadId;
        this.tempFile = tempFile;
        this.newFile = newFile;
        Uri downloadUri = Uri.parse("content://downloads/my_downloads/" + downloadId); // Construct URI with download ID
//        Uri downloadUri = Uri.fromFile(tempFile);
        context.getContentResolver().registerContentObserver(downloadUri, true, this);
    }

    public void unregister() {
        context.getContentResolver().unregisterContentObserver(this);
    }

    private static void copyFileToDocumentFile(Context context, File sourceFile, DocumentFile targetFile){
        try (InputStream inputStream = Files.newInputStream(sourceFile.toPath());
            OutputStream outputStream = context.getContentResolver().openOutputStream(targetFile.getUri())) {
            byte[] buffer = new byte[4096];
            int length;
            if (!(null == inputStream) && !(null == outputStream))
                while ((length = inputStream.read(buffer)) > 0)
                    outputStream.write(buffer, 0, length);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found", e);
        } catch (IOException e) {
            Log.e(TAG, "Error copying file", e);
        }
    }
}
