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

package com.tfcode.comparetout.importers.homeassistant;

import static android.content.Context.NOTIFICATION_SERVICE;
import static com.tfcode.comparetout.importers.homeassistant.ImportHAOverview.HA_COBAT_KEY;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.TOUTCApplication;
import com.tfcode.comparetout.importers.homeassistant.messages.HAMessage;
import com.tfcode.comparetout.importers.homeassistant.messages.StatsForPeriodRequest;
import com.tfcode.comparetout.importers.homeassistant.messages.authorization.AuthInvalid;
import com.tfcode.comparetout.importers.homeassistant.messages.authorization.AuthOK;
import com.tfcode.comparetout.importers.homeassistant.messages.statsForPeriodResult.StatsForPeriodResult;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.reactivex.Single;

public class HACatchupWorker extends Worker {
    private static final Logger LOGGER = Logger.getLogger(HACatchupWorker.class.getName());
    public static final String KEY_HOST = "KEY_HOST";
    public static final String KEY_TOKEN = "KEY_TOKEN";
    public static final String KEY_START_DATE = "KEY_START_DATE";
    public static final String KEY_SENSORS = "KEY_SENSORS";

    public static final String PROGRESS = "PROGRESS";
    private boolean isWorkCompleted = false;

    private String mProgress = "";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter INPUT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final DateTimeFormatter NOTIFY_FORMAT = DateTimeFormatter.ofPattern("yy-MM");

    private final Object lock = new Object();

    Context mContext;
    private final ToutcRepository mToutcRepository;

    private EnergySensors mEnergySensors = null;
    private final NotificationManager mNotificationManager;
    private static final int mNotificationId = 4;
    private boolean mStopped = false;
    public HACatchupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mContext = context;
        mToutcRepository = new ToutcRepository((Application) context);
        mNotificationManager = (NotificationManager)
                context.getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    public void onStopped(){
        super.onStopped();
        mStopped = true;
    }

    @NonNull
    @Override
    public Result doWork() {
        System.out.println("HACatchupWorker:doWork invoked ");
        Data inputData = getInputData();
        HADispatcher mHAClient = new HADispatcher(
                inputData.getString(KEY_HOST),
                inputData.getString(KEY_TOKEN));
        String startDate = inputData.getString(KEY_START_DATE);
        // No start date is provided for daily runs, default to yesterday
        if (null == startDate) {
            startDate = LocalDateTime.now().minusDays(1).format(DATE_FORMAT);
        }
        String sensors = inputData.getString(KEY_SENSORS);
        mEnergySensors = new Gson().fromJson(sensors, new TypeToken<EnergySensors>(){}.getType());

        // Mark the Worker as important
        LocalDate current = LocalDate.parse(startDate, INPUT_DATE_FORMAT);
        mProgress = current.format(NOTIFY_FORMAT);
        String progress = "Starting Fetch";
        setForegroundAsync(createForegroundInfo(progress));
        setProgressAsync(new Data.Builder().putString(PROGRESS, mProgress).build());
        ForegroundInfo foregroundInfo = createForegroundInfo("Importing HomeAssistant data");
        mNotificationManager.notify(mNotificationId, foregroundInfo.getNotification());

        mHAClient.registerHandler("auth_ok", new HACatchupWorker.AuthOKHandler(mHAClient, startDate));
        mHAClient.registerHandler("auth_invalid", new HACatchupWorker.AuthNotOKHandler(mHAClient));
        mHAClient.start();
        waitWorkCompletion();

        LOGGER.info("HACatchupWorker:doWork finished");
        progress = "All done importing HomeAssistant data";
        setProgressAsync(new Data.Builder().putString(PROGRESS, progress).build());
        foregroundInfo = createForegroundInfo(progress);
        mNotificationManager.notify(mNotificationId, foregroundInfo.getNotification());

        return Result.success();
    }

    private class StatsForPeriodResultHandler  implements MessageHandler<HAMessage> {
        private final HADispatcher mHAClient;
        private final LocalDateTime finishLDT;
        private final LocalDateTime startLDT;

        public StatsForPeriodResultHandler(HADispatcher mHAClient, LocalDateTime startLDT, LocalDateTime finishLDT) {
            this.finishLDT = finishLDT;
            this.mHAClient = mHAClient;
            this.startLDT = startLDT.plusDays(1);
        }

        @Override
        public void handleMessage(HAMessage message) {
            StatsForPeriodResult result = (StatsForPeriodResult) message;
            if (result.isSuccess()) {
                LOGGER.info("StatsForPeriodResultHandler.handleMessage.success");
                Map<LocalDateTime, Map<String, Double>> pivotedResult = result.pivotStatsForPeriodResult();
                List<AlphaESSTransformedData> dbRows = result.calculateAndAddLoad(
                        "HomeAssistant", mEnergySensors, pivotedResult);
                mToutcRepository.addTransformedData(dbRows);
                updateBatteryCapacities(result);
                LocalDateTime anyDate = null;
                if (!pivotedResult.keySet().isEmpty()) {
                    anyDate = pivotedResult.keySet().iterator().next();
                }

                if (anyDate != null) {
                    String processedDate = anyDate.format(NOTIFY_FORMAT);
                    if (!processedDate.equals(mProgress)) {
                        mProgress = processedDate;
                        setProgressAsync(new Data.Builder().putString(PROGRESS, "Working on " + mProgress).build());
                        ForegroundInfo foregroundInfo = createForegroundInfo("Working on " + mProgress);
                        mNotificationManager.notify(mNotificationId, foregroundInfo.getNotification());
                    }
                }
            }
            else {
                LOGGER.info("StatsForPeriodResultHandler.handleMessage.failure");
            }
            if (startLDT.isAfter(finishLDT.minusDays(1)) || mStopped) {
                mHAClient.stop();
                synchronized (lock) {
                    isWorkCompleted = true;
                    lock.notifyAll(); // Notify any waiting threads
                }
            }
            else {
                LOGGER.info("StatsForPeriodResultHandler.handleMessage.next: " + startLDT.format(DATE_FORMAT));
                StatsForPeriodRequest request = new StatsForPeriodRequest(mEnergySensors.getSenorList());
                request.setStartAndEndTimes(startLDT, startLDT.plusDays(1), mHAClient.generateId());
                mHAClient.sendMessage(request, new StatsForPeriodResultHandler(mHAClient, startLDT, finishLDT));
            }
        }

        private void updateBatteryCapacities(StatsForPeriodResult result) {
            List<Double> estimatedBatteryCapacity = result.getEstimatedBatteryCapacity();
            TOUTCApplication application = (TOUTCApplication) mContext;
            if (!(null == application)) {
                Preferences.Key<String> systemList = PreferencesKeys.stringKey(HA_COBAT_KEY);
                Single<String> value4 = application.getDataStore()
                        .data().firstOrError()
                        .map(prefs -> prefs.get(systemList)).onErrorReturnItem("0.0");
                List<Double> previouslyEstimatedBatteryCapacity =
                        Arrays.stream(value4.blockingGet().split(","))
                        .map(Double::valueOf)
                        .collect(Collectors.toList());
                int index = 0;
                int prevSize = previouslyEstimatedBatteryCapacity.size();
                List<Double> updatedEstimatedBatteryCapacity = new ArrayList<>();
                for (double d : estimatedBatteryCapacity) {
                    double prev = (prevSize > index) ? previouslyEstimatedBatteryCapacity.get(index) : 0.0;
                    updatedEstimatedBatteryCapacity.add(index, Math.max(prev, d));
                    index++;
                }
                if (updatedEstimatedBatteryCapacity.isEmpty())
                    updatedEstimatedBatteryCapacity = previouslyEstimatedBatteryCapacity;
                boolean x = application.putStringValueIntoDataStore(HA_COBAT_KEY,
                        updatedEstimatedBatteryCapacity.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(",")));
                if (!x)
                    System.out.println("HACatchupWorker::StatsForPeriodResultHandler, " +
                            "failed to store estimatedBatteryCapacity");
            }
        }

        @Override
        public Class<? extends HAMessage> getMessageClass() {
            return StatsForPeriodResult.class;
        }
    }

    private class AuthOKHandler implements MessageHandler<HAMessage> {
        private final HADispatcher mHAClient;
        private final String startDate;

        public AuthOKHandler(HADispatcher mHAClient, String startDate) {
            this.mHAClient = mHAClient;
            this.startDate = startDate;
        }

        @Override
        public void handleMessage(HAMessage message) {
            LOGGER.info("AuthOKHandler.handleMessage");
            mHAClient.setAuthorized(true);
            // OK, authenticated, now fetch the energy stats
            StatsForPeriodRequest request = new StatsForPeriodRequest(mEnergySensors.getSenorList());
            LocalDateTime startLDT = LocalDateTime.parse(startDate + " 00:00", DATE_FORMAT);
            request.setStartAndEndTimes(startLDT, startLDT.plusDays(1), mHAClient.generateId());
            LocalDateTime finishLDT = LocalDateTime.of(LocalDateTime.now().toLocalDate(), LocalTime.MIDNIGHT);
            mHAClient.sendMessage(request, new StatsForPeriodResultHandler(mHAClient, startLDT, finishLDT));
        }

        @Override
        public Class<? extends HAMessage> getMessageClass() {return AuthOK.class;}
    }

    private class AuthNotOKHandler implements MessageHandler<HAMessage> {
        private final HADispatcher mHAClient;

        public AuthNotOKHandler(HADispatcher mHAClient) {
            this.mHAClient = mHAClient;
        }

        @Override
        public void handleMessage(HAMessage message) {
            LOGGER.info("AuthInvalidHandler.handleMessage");
            mHAClient.setAuthorized(false);
            mHAClient.stop();
            synchronized (lock) {
                isWorkCompleted = true;
                lock.notifyAll(); // Notify any waiting threads
            }
        }

        @Override
        public Class<? extends HAMessage> getMessageClass() {
            return AuthInvalid.class;
        }
    }

    public void waitWorkCompletion() {
        synchronized (lock) {
            while (!isWorkCompleted) {
                try {
                    lock.wait(); // Wait until the async operation is completed
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("Async operation has finished, continue with the rest of the code.");
    }


    @NonNull
    private ForegroundInfo createForegroundInfo(@NonNull String progress) {
        // Build a notification using bytesRead and contentLength

        Context context = getApplicationContext();
        String id = context.getString(R.string.ha_channel_id);
        String title = context.getString(R.string.fetch_ha_notification_title);
        String cancel = context.getString(R.string.cancel_fetch_alpha);
        // This PendingIntent can be used to cancel the worker
        PendingIntent intent = WorkManager.getInstance(context)
                .createCancelPendingIntent(getId());

        Intent importESBNActivity = new Intent(context, ImportHomeAssistantActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addNextIntentWithParentStack(importESBNActivity);
        PendingIntent activityPendingIntent = stackBuilder.getPendingIntent(0,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        createChannel();

        Notification notification = new NotificationCompat.Builder(context, id)
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(progress)
                .setSmallIcon(R.drawable.ic_baseline_download_24)
                .setOngoing(true)
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(activityPendingIntent)
                // Add the cancel action to the notification which can
                // be used to cancel the worker
                .addAction(android.R.drawable.ic_delete, cancel, intent)
                .build();

        return new ForegroundInfo(mNotificationId, notification);
    }

    private void createChannel() {
        // Create a Notification channel
        CharSequence name = getApplicationContext().getString(R.string.ha_channel_name);
        String description = getApplicationContext().getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(
                getApplicationContext().getString(R.string.ha_channel_id), name, importance);
        channel.setDescription(description);
        // Register the channel with the system. You can't change the importance
        // or other notification behaviors after this.
        NotificationManager notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }
}
