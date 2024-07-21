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

package com.tfcode.comparetout.importers.esbn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.tfcode.comparetout.R;
import com.tfcode.comparetout.importers.esbn.responses.ESBNException;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class ESBNFetchService extends Service {


    private static final String TAG = ESBNFetchService.class.getName();
    private ToutcRepository mToutcRepository;
    private NotificationManager mNotificationManager;
    private static final int mNotificationId = 3;
    private Intent mIntent;
    private Handler mHandler;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final DateTimeFormatter MIN_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public static final String KEY_SYSTEM_SN = "KEY_SYSTEM_SN";
    public static final String KEY_APP_ID = "KEY_APP_ID";
    public static final String KEY_APP_SECRET = "KEY_APP_SECRET";
    public static final String KEY_START_DATE = "KEY_START_DATE";

    public static final String PROGRESS = "PROGRESS";

    public ESBNFetchService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (!(null == getApplication()))
            mToutcRepository = new ToutcRepository(getApplication());
        mNotificationManager = (NotificationManager)
                this.getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createChannel();
        mHandler = new Handler(Looper.getMainLooper());
        Notification notification = getNotification("Starting Fetch", true);
        startForeground(mNotificationId, notification);

        this.mIntent = intent;

        executeTask();

        return START_NOT_STICKY;
    }

    private void executeTask() {
        // Ensure this runs on a background thread
        new Thread(() -> {
            performTask();
            stopSelf();  // Stop the service when done
        }).start();
    }

    private void performTask() {
        Log.i(TAG, "ESBNFetchService:doWork invoked ");
        ESBNHDFClient esbnHDFClient = new ESBNHDFClient(
                mIntent.getStringExtra(KEY_APP_ID),
                mIntent.getStringExtra(KEY_APP_SECRET));
        String systemSN = mIntent.getStringExtra(KEY_SYSTEM_SN);
//        String startDate = mIntent.getStringExtra(KEY_START_DATE);
//        if (null == startDate) startDate = mToutcRepository.getLatestDateForSn(systemSN);
        esbnHDFClient.setSelectedMPRN(systemSN);

//        LocalDate current = LocalDate.parse(startDate, DATE_FORMAT);
//        LocalDate end = LocalDate.now();

        // Mark the Worker as important
        mHandler.post(() -> mNotificationManager.notify(mNotificationId, getNotification("Starting Fetch", true)));

        // Do some work
        Map<LocalDateTime, Pair<Double, Double>> timeAlignedEntries = new HashMap<>();
        AtomicReference<LocalDateTime> last = new AtomicReference<>(LocalDateTime.of(1970,1, 1, 0, 0));
        {
            // we need to download the HDF
            try {
                esbnHDFClient.fetchSmartMeterDataHDF((type, ldt, value) -> {
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
                                timeAlignedEntries.put(ldt, new Pair<>(importExport.first, value/2D));
                            break;
                    }
                });
            } catch (ESBNException e) {
                e.printStackTrace();
                String finalProgress2 = e.getMessage() == null ? "Failed for unknown reason. Consider files" : e.getMessage();
                mHandler.post(() -> mNotificationManager.notify(mNotificationId, getNotification(finalProgress2, false)));
                return;
            }
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

        mHandler.post(() -> mNotificationManager.notify(mNotificationId, getNotification("All done importing ", true)));

//        if (mStopped) mNotificationManager.cancel(mNotificationId);
    }

    @NonNull
    private Notification getNotification(@NonNull String progress, boolean autoCancel) {
        // Build a notification using bytesRead and contentLength

        Context context = getApplicationContext();
        String id = context.getString(R.string.esbn_channel_id);
        String title = context.getString(R.string.fetch_esbn_notification_title);
//        String cancel = context.getString(R.string.cancel_fetch_alpha);
        // This PendingIntent can be used to cancel the worker
//        PendingIntent intent = WorkManager.getInstance(context)
//                .createCancelPendingIntent(getId());

        Intent importESBNActivity = new Intent(context, ImportESBNActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addNextIntentWithParentStack(importESBNActivity);
        PendingIntent activityPendingIntent = stackBuilder.getPendingIntent(0,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(context, id)
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(progress)
                .setSmallIcon(R.drawable.ic_baseline_download_24)
                .setOngoing(true)
                .setAutoCancel(autoCancel)
                .setSilent(true)
                .setContentIntent(activityPendingIntent)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                // Add the cancel action to the notification which can
                // be used to cancel the worker
                //   .addAction(android.R.drawable.ic_delete, cancel, intent)
                .build();
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