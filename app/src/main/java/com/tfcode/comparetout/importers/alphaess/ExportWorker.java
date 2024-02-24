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
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.importers.alphaess.responses.GetOneDayEnergyResponse;
import com.tfcode.comparetout.importers.alphaess.responses.GetOneDayPowerResponse;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawEnergy;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawPower;
import com.tfcode.comparetout.model.json.AlphaESSJsonTools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class ExportWorker extends Worker {

    private final ToutcRepository mToutcRepository;
    private final NotificationManager mNotificationManager;
    private static final int mNotificationId = 2;
    private boolean mStopped = false;


    public static final String KEY_SYSTEM_SN = "KEY_SYSTEM_SN";

    public static final String PROGRESS = "PROGRESS";

    public ExportWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mToutcRepository = new ToutcRepository((Application) context);
        mNotificationManager = (NotificationManager)
                context.getSystemService(NOTIFICATION_SERVICE);
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

        // Mark the Worker as important
        String progress = "Exporting energy";
        setProgressAsync(new Data.Builder().putString(PROGRESS, progress).build());
        ForegroundInfo foregroundInfo = createForegroundInfo("Exporting energy");
        mNotificationManager.notify(mNotificationId, foregroundInfo.getNotification());
        
        // Do some work

        File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(folder, systemSN + ".json");
        List<String> dates = mToutcRepository.getExportDatesForSN(systemSN);
        List<AlphaESSRawEnergy> energyList = mToutcRepository.getAlphaESSEnergyForSharing(systemSN);
        List<GetOneDayEnergyResponse.DataItem> energyListJson = AlphaESSJsonTools.getEnergyDataItems(energyList);

        FileWriter fileWriter = null;
        JsonWriter jsonWriter = null;
        try {
            fileWriter = new FileWriter(file);
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

            progress = "Exported energy";
            setProgressAsync(new Data.Builder().putString(PROGRESS, progress).build());
            foregroundInfo = createForegroundInfo("Exported energy");
            mNotificationManager.notify(mNotificationId, foregroundInfo.getNotification());

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
                    progress = "Exporting power: " + processed + "/" + dates.size();
                    setProgressAsync(new Data.Builder().putString(PROGRESS, progress).build());
                    foregroundInfo = createForegroundInfo(progress);
                    mNotificationManager.notify(mNotificationId, foregroundInfo.getNotification());
                }
                if (mStopped) break;
            }
            jsonWriter.endArray();
            jsonWriter.endObject();
            jsonWriter.flush();
            jsonWriter.close();
            fileWriter.close();

            progress = "Exported power: " + processed + "/" + dates.size();
            setProgressAsync(new Data.Builder().putString(PROGRESS, progress).build());
            foregroundInfo = createForegroundInfo(progress);
            mNotificationManager.notify(mNotificationId, foregroundInfo.getNotification());

        } catch (IOException e) {
            e.printStackTrace();
            progress = "Export abandoned: Missing permission or file exists";
            setProgressAsync(new Data.Builder().putString(PROGRESS, progress).build());
            foregroundInfo = createForegroundInfo(progress);
            mNotificationManager.notify(mNotificationId, foregroundInfo.getNotification());
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
        }
        
        if (mStopped) mNotificationManager.cancel(mNotificationId);

        return Result.success();
    }

    @NonNull
    private ForegroundInfo createForegroundInfo(@NonNull String progress) {
        // Build a notification using bytesRead and contentLength

        Context context = getApplicationContext();
        String id = context.getString(R.string.alphaess_channel_id);
        String title = context.getString(R.string.alphaess_export_notification_title);
        String cancel = context.getString(R.string.cancel_fetch_alpha);
        // This PendingIntent can be used to cancel the worker
        PendingIntent cancelPendingIntent = WorkManager.getInstance(context)
                .createCancelPendingIntent(getId());

        Intent importAlphaActivity = new Intent(context, ImportAlphaActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addNextIntentWithParentStack(importAlphaActivity);
        PendingIntent activityPendingIntent = stackBuilder.getPendingIntent(0,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        createChannel();

        Notification notification = new NotificationCompat.Builder(context, id)
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

        return new ForegroundInfo(mNotificationId, notification);
    }

    private void createChannel() {
        // Create a Notification channel
        CharSequence name = getApplicationContext().getString(R.string.alphaess_channel_name);
        String description = getApplicationContext().getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(
                getApplicationContext().getString(R.string.alphaess_channel_id), name, importance);
        channel.setDescription(description);
        // Register the channel with the system. You can't change the importance
        // or other notification behaviors after this.
        NotificationManager notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }
}
