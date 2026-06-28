/*
 * Copyright (c) 2023-2024. Tony Finnerty
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

package com.tfcode.comparetout.importers.alphaess;

import static android.content.Context.NOTIFICATION_SERVICE;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.importers.alphaess.responses.GetOneDayEnergyResponse;
import com.tfcode.comparetout.importers.alphaess.responses.GetOneDayPowerResponse;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawEnergy;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawPower;
import com.tfcode.comparetout.model.json.AlphaESSJsonTools;
import com.tfcode.comparetout.ui2.UI2NotificationLaunch;
import com.tfcode.comparetout.util.ContractFileUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ExportWorker extends Worker {

    private final ToutcRepository mToutcRepository;
    private final NotificationManager mNotificationManager;
    // Distinct notification slot per worker class — see
    // plans/eventual-bouncing-hare.md. Before this refactor every AlphaESS
    // worker used slot 2 and stomped on each other when concurrent.
    private static final int mNotificationId = 5;
    private boolean mStopped = false;
    private final Context mContext;
    // Same cached-flag pattern the other importer workers use so the
    // notification's content-intent lands on UI2 when the user has opted in.
    private boolean mUseUI2 = false;
    private String mSelectedSysSn = null;
    // The 30-day milestone fires fast enough on a multi-year export to
    // outrun Android's notification update cap (~5/sec). Throttle the
    // in-loop notifies so they never get coalesced away — the terminal
    // milestones use force=true to guarantee the user sees the final state.
    private long mLastNotifyAt = 0L;
    private static final long MIN_NOTIFY_INTERVAL_MS = 250L;

    private static final String TAG = "ExportWorker";

    public static final String KEY_SYSTEM_SN = "KEY_SYSTEM_SN";
    public static final String KEY_FOLDER = "KEY_FOLDER";

    public static final String PROGRESS = "PROGRESS";

    public ExportWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mContext = context;
        mToutcRepository = new ToutcRepository((Application) context);
        mNotificationManager = (NotificationManager)
                context.getSystemService(NOTIFICATION_SERVICE);
        createChannel();
        setProgressAsync(new Data.Builder().putString(PROGRESS, "Starting export").build());
        System.out.println("Export worker created");
    }

    @Override
    public void onStopped(){
        super.onStopped();
        mStopped = true;
    }

    @NonNull
    @Override
    public Result doWork() {
        System.out.println("ExportWorker:doWork invoked ");
        Data inputData = getInputData();
        String systemSN = inputData.getString(KEY_SYSTEM_SN);
        String folder = inputData.getString(KEY_FOLDER);
        Uri folderUri = Uri.parse(folder);
        mSelectedSysSn = systemSN;
        mUseUI2 = UI2NotificationLaunch.isUI2Enabled(getApplicationContext());

        publishProgress("Exporting energy", true);

        String fileName = systemSN + ".json";
        ContentResolver resolver = mContext.getContentResolver();

        List<String> dates = mToutcRepository.getExportDatesForSN(systemSN);
        List<AlphaESSRawEnergy> energyList = mToutcRepository.getAlphaESSEnergyForSharing(systemSN);
        List<GetOneDayEnergyResponse.DataItem> energyListJson = AlphaESSJsonTools.getEnergyDataItems(energyList);

        OutputStreamWriter fileWriter = null;
        JsonWriter jsonWriter = null;
        Uri destinationFileUri;
        OutputStream outputStream = null;
        try {
            destinationFileUri = ContractFileUtils.createJSONFileInEPOFolder(resolver, folderUri, fileName);
            if (null == destinationFileUri) return Result.success();
            outputStream = mContext.getContentResolver().openOutputStream(destinationFileUri);
            fileWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            jsonWriter = new JsonWriter(fileWriter);
            jsonWriter.setIndent("  ");
            jsonWriter.beginObject();
            jsonWriter.name("energy");
            jsonWriter.beginArray();
            for (GetOneDayEnergyResponse.DataItem dataItem : energyListJson) {
                Gson gson = new Gson();
                gson.toJson(dataItem, GetOneDayEnergyResponse.DataItem.class, jsonWriter);
            }
            jsonWriter.endArray();
            jsonWriter.flush();

            publishProgress("Exported energy", true);

            jsonWriter.name("power");
            jsonWriter.beginArray();
            int processed = 0;
            if (!mStopped) for (String date : dates) {
                List<AlphaESSRawPower> powerList = mToutcRepository.getAlphaESSPowerForSharing(
                        systemSN, date);
                List<GetOneDayPowerResponse.DataItem> powerListJson = AlphaESSJsonTools.getPowerDataItems(powerList);
                for (GetOneDayPowerResponse.DataItem dataItem : powerListJson) {
                    Gson gson = new Gson();
                    gson.toJson(dataItem, GetOneDayPowerResponse.DataItem.class, jsonWriter);
                    jsonWriter.flush();
                }
                processed++;

                if ((processed % 30) == 0) {
                    publishProgress("Exporting power: " + processed + "/" + dates.size(), false);
                }
                if (mStopped) break;
            }
            jsonWriter.endArray();
            jsonWriter.endObject();
            jsonWriter.flush();
            jsonWriter.close();
            fileWriter.close();

            String filename = ContractFileUtils.getFileNameFromUri(mContext, destinationFileUri);
            publishProgress("Exported complete: " + filename, true);

        } catch (Exception e) {
            if (e instanceof FileNotFoundException) Log.i(TAG, "FileNotFoundException when creating a file for download");
            if (e instanceof IllegalArgumentException) Log.e(TAG, "IllegalArgumentException when creating a file for download");
            if (e instanceof SecurityException) Log.e(TAG, "SecurityException when creating a file for download");
            if (e instanceof IOException) Log.e(TAG, "IOException when creating a file for download");
            e.printStackTrace();
            publishProgress("Export abandoned: Missing permission or file exists", true);
        } finally {
            if (!(null == jsonWriter)) {
                try {
                    jsonWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (!(null == fileWriter)) {
                try {
                    fileWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (!(null == outputStream)) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        
        if (mStopped) mNotificationManager.cancel(mNotificationId);

        return Result.success();
    }

    /**
     * Publish the worker's progress to WorkManager + the notification
     * shade. NotificationManager.notify is thread-safe, so no main-thread
     * hop is required (the earlier handler.post approach raced the worker's
     * own completion). [force]=true bypasses the in-loop throttle and is
     * used for the first/last updates so terminal state always lands.
     */
    private void publishProgress(@NonNull String progress, boolean force) {
        setProgressAsync(new Data.Builder().putString(PROGRESS, progress).build());
        long now = System.currentTimeMillis();
        if (force || now - mLastNotifyAt > MIN_NOTIFY_INTERVAL_MS) {
            mLastNotifyAt = now;
            mNotificationManager.notify(mNotificationId, getNotification(progress));
        }
    }

    @NonNull
    private Notification getNotification(@NonNull String progress) {
        // Build a notification using bytesRead and contentLength

        Context context = getApplicationContext();
        String id = context.getString(R.string.alphaess_channel_id);
        String title = context.getString(R.string.alphaess_export_notification_title);
        String cancel = context.getString(R.string.cancel_fetch_alpha);
        // This PendingIntent can be used to cancel the worker
        PendingIntent cancelPendingIntent = WorkManager.getInstance(context)
                .createCancelPendingIntent(getId());

        PendingIntent activityPendingIntent = UI2NotificationLaunch.contentIntent(
                context, mUseUI2, ComparisonUIViewModel.Importer.ALPHAESS,
                mSelectedSysSn, ImportAlphaActivity.class);

        return new NotificationCompat.Builder(context, id)
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(progress)
                .setSmallIcon(R.drawable.ic_baseline_save_24)
                .setOngoing(true)
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(activityPendingIntent)
                // Add the cancel action to the notification which can
                // be used to cancel the worker
                .addAction(android.R.drawable.ic_delete, cancel, cancelPendingIntent)
                .build();
    }

    private void createChannel() {
        // Create a Notification channel
        CharSequence name = getApplicationContext().getString(R.string.alphaess_channel_name);
        String description = getApplicationContext().getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(
                getApplicationContext().getString(R.string.alphaess_channel_id), name, importance);
        channel.setDescription(description);
        // Register the channel with the system. You can't change the importance
        // or other notification behaviors after this.
        NotificationManager notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }
}
