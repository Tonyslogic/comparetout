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

package com.tfcode.comparetout.importers.esbn;

import static android.content.Context.NOTIFICATION_SERVICE;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.net.Uri;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.R;
import com.tfcode.comparetout.importers.esbn.responses.ESBNException;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class ESBNImportWorker extends Worker {

    private final ToutcRepository mToutcRepository;
    private final NotificationManager mNotificationManager;
    private static final int mNotificationId = 3;
    private boolean mStopped = false;


    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MIN_FORMAT = DateTimeFormatter.ofPattern("HH:mm");


    public static final String KEY_SYSTEM_SN = "KEY_SYSTEM_SN";
    public static final String KEY_URI = "KEY_URI";

    public static final String PROGRESS = "PROGRESS";

    public ESBNImportWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mToutcRepository = new ToutcRepository((Application) context);
        mNotificationManager = (NotificationManager)
                context.getSystemService(NOTIFICATION_SERVICE);
        setProgressAsync(new Data.Builder().putString(PROGRESS, "Starting import from file").build());
        System.out.println("Import worker created");
    }

    @Override
    public void onStopped(){
        super.onStopped();
        mStopped = true;
    }

    @NonNull
    @Override
    public Result doWork() {
        System.out.println("ESBNImportWorker:doWork invoked ");
        Data inputData = getInputData();
        String systemSN = inputData.getString(KEY_SYSTEM_SN);
        String uriString = inputData.getString(KEY_URI);
        Uri fileUri = Uri.parse(uriString);

        // Mark the Worker as important
        String progress = "Importing energy";
        setProgressAsync(new Data.Builder().putString(PROGRESS, progress).build());
        ForegroundInfo foregroundInfo = createForegroundInfo("Importing HDF file");
        mNotificationManager.notify(mNotificationId, foregroundInfo.getNotification());
        
        // Do some work
        Map<LocalDateTime, Pair<Double, Double>> timeAlignedEntries = new HashMap<>();
        AtomicReference<LocalDateTime> last = new AtomicReference<>(LocalDateTime.of(1970,1, 1, 0, 0));
        try (InputStream is = getApplicationContext().getContentResolver().openInputStream(fileUri)){
            ESBNHDFClient.readEntriesFromFile(is, (type, ldt, value) -> {
                Pair<Double, Double> importExport = timeAlignedEntries.get(ldt);
                if (ldt.isAfter(last.get())) last.set(ldt);
                switch (type) {
                    case IMPORT:
                        if ((null == importExport))
                            timeAlignedEntries.put(ldt, new Pair<>(value/2D, 0D));
                        else
                            timeAlignedEntries.put(ldt, new Pair<>(value/2D, importExport.second));
                        break;
                    case EXPORT:
                        if ((null == importExport))
                            timeAlignedEntries.put(ldt, new Pair<>(0D, value/2D));
                        else
                            timeAlignedEntries.put(ldt, new Pair<>(importExport.second, value/2D));
                        break;
                }
            });

        } catch (IOException | ESBNException e) {
            e.printStackTrace();
        }
        // Check and Remove the last day if missing more than 18 entries
        LocalDate lastDay = last.get().toLocalDate();
        int count = 0;
        for (LocalDateTime key : timeAlignedEntries.keySet()) {
            if (key.toLocalDate().equals(lastDay)) {
                count++;
            }
        }
        if (count < 31) {
            timeAlignedEntries.entrySet().removeIf(entry -> entry.getKey().toLocalDate().equals(lastDay));
        }
        // Store transformed data
        List<AlphaESSTransformedData> normalizedEntityList = new ArrayList<>();
        for (Map.Entry<LocalDateTime, Pair<Double, Double>> entry: timeAlignedEntries.entrySet()) {
            AlphaESSTransformedData dbEntry = new AlphaESSTransformedData();
            dbEntry.setBuy(entry.getValue().first);
            dbEntry.setFeed(entry.getValue().second);
            dbEntry.setPv(0D);
            dbEntry.setLoad(0D);
            dbEntry.setSysSn(systemSN != null ? systemSN : "Not set");
            dbEntry.setDate(entry.getKey().format(DATE_FORMAT));
            dbEntry.setMinute(entry.getKey().format(MIN_FORMAT));
            normalizedEntityList.add(dbEntry);
        }
        mToutcRepository.addTransformedData(normalizedEntityList);

        progress = "All done importing " + systemSN;
        setProgressAsync(new Data.Builder().putString(PROGRESS, progress).build());
        foregroundInfo = createForegroundInfo(progress);
        mNotificationManager.notify(mNotificationId, foregroundInfo.getNotification());

        if (mStopped) mNotificationManager.cancel(mNotificationId);

        return Result.success();
    }

    @NonNull
    private ForegroundInfo createForegroundInfo(@NonNull String progress) {
        // Build a notification using bytesRead and contentLength

        Context context = getApplicationContext();
        String id = context.getString(R.string.esbn_channel_id);
        String title = context.getString(R.string.esbn_import_notification_title);
        String cancel = context.getString(R.string.cancel_fetch_alpha);
        // This PendingIntent can be used to cancel the worker
        PendingIntent intent = WorkManager.getInstance(context)
                .createCancelPendingIntent(getId());

        createChannel();

        Notification notification = new NotificationCompat.Builder(context, id)
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(progress)
                .setSmallIcon(R.drawable.ic_baseline_download_24)
                .setOngoing(true)
                .setAutoCancel(true)
                .setSilent(true)
                // Add the cancel action to the notification which can
                // be used to cancel the worker
                .addAction(android.R.drawable.ic_delete, cancel, intent)
                .build();

        return new ForegroundInfo(mNotificationId, notification);
    }

    private void createChannel() {
        // Create a Notification channel
        CharSequence name = getApplicationContext().getString(R.string.esbn_channel_name);
        String description = getApplicationContext().getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(
                getApplicationContext().getString(R.string.esbn_channel_id), name, importance);
        channel.setDescription(description);
        // Register the channel with the system. You can't change the importance
        // or other notification behaviors after this.
        NotificationManager notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }
}
