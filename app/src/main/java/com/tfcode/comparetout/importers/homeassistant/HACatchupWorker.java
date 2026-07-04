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
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.work.Data;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.TOUTCApplication;
import com.tfcode.comparetout.importers.homeassistant.messages.HAMessage;
import com.tfcode.comparetout.importers.homeassistant.messages.StatsForPeriodRequest;
import com.tfcode.comparetout.importers.homeassistant.messages.authorization.AuthInvalid;
import com.tfcode.comparetout.importers.homeassistant.messages.authorization.AuthOK;
import com.tfcode.comparetout.importers.homeassistant.messages.statsForPeriodResult.StatsForPeriodResult;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.ui2.UserTimezoneStore;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.ui2.UI2NotificationLaunch;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.Single;

public class HACatchupWorker extends Worker {
    private static final Logger LOGGER = Logger.getLogger(HACatchupWorker.class.getName());
    public static final String KEY_HOST = "KEY_HOST";
    public static final String KEY_TOKEN = "KEY_TOKEN";
    public static final String KEY_START_DATE = "KEY_START_DATE";
    public static final String KEY_SENSORS = "KEY_SENSORS";

    public static final String PROGRESS = "PROGRESS";
    private boolean isWorkCompleted = false;

    private String mProgress = "";

    // Reconnect/resume support: the fetch walks day-by-day, so on a socket drop we
    // reconnect (bounded) and resume from the last committed day instead of hanging
    // (comms hardening, plans/ha/design.md). Re-fetching the cursor day is idempotent
    // (rows upsert by (sysSn, date, minute)).
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final long RECONNECT_BACKOFF_MS = 5000L;
    private volatile LocalDate mCursorDate;
    private volatile boolean mConnectionLost = false;
    private volatile boolean mAuthFailed = false;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter INPUT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final DateTimeFormatter NOTIFY_FORMAT = DateTimeFormatter.ofPattern("yy-MM");

    private final Object lock = new Object();

    final Context mContext;
    private final ToutcRepository mToutcRepository;

    private EnergySensors mEnergySensors = null;
    private final NotificationManager mNotificationManager;
    // Distinct notification slot per worker class — see
    // plans/eventual-bouncing-hare.md.
    private static final int mNotificationId = 4;
    private boolean mStopped = false;
    private boolean mUseUI2 = false;
    private long mLastNotifyAt = 0L;
    private static final long MIN_NOTIFY_INTERVAL_MS = 250L;
    // HA data is stored under a single synthetic SN (see calculateAndAddLoad).
    private static final String HA_SYS_SN = "HomeAssistant";
    public HACatchupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mContext = context;
        mToutcRepository = new ToutcRepository((Application) context);
        mNotificationManager = (NotificationManager)
                context.getSystemService(NOTIFICATION_SERVICE);
        createChannel();
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
        String host = inputData.getString(KEY_HOST);
        String token = inputData.getString(KEY_TOKEN);
        String startDate = inputData.getString(KEY_START_DATE);
        // No start date is provided for daily runs, default to yesterday
        if (null == startDate) {
            startDate = LocalDateTime.now().minusDays(1).format(INPUT_DATE_FORMAT);
        }
        String sensors = inputData.getString(KEY_SENSORS);
        mEnergySensors = new Gson().fromJson(sensors, new TypeToken<EnergySensors>(){}.getType());
        mUseUI2 = UI2NotificationLaunch.isUI2Enabled(getApplicationContext());

        mCursorDate = LocalDate.parse(startDate, INPUT_DATE_FORMAT);
        mProgress = mCursorDate.format(NOTIFY_FORMAT);
        publishProgress("Importing HomeAssistant data", true);

        // Session loop: each pass opens a fresh socket and fetches from mCursorDate. A
        // connection loss ends the session (via the ConnectionListener) and we reconnect
        // with linear backoff, resuming from the cursor, up to MAX_RECONNECT_ATTEMPTS.
        int attempt = 0;
        while (true) {
            mConnectionLost = false;
            synchronized (lock) {
                isWorkCompleted = false;
            }
            HADispatcher mHAClient = new HADispatcher(host, token);
            mHAClient.registerHandler("auth_ok", new HACatchupWorker.AuthOKHandler(mHAClient));
            mHAClient.registerHandler("auth_invalid", new HACatchupWorker.AuthNotOKHandler(mHAClient));
            mHAClient.setConnectionListener(reason -> {
                mConnectionLost = true;
                synchronized (lock) {
                    isWorkCompleted = true;
                    lock.notifyAll();
                }
            });
            mHAClient.start();
            waitWorkCompletion();

            if (mStopped) break;
            if (mAuthFailed) {
                LOGGER.warning("HACatchupWorker: authentication failed");
                publishProgress("HomeAssistant authentication failed", true);
                return Result.failure();
            }
            if (!mConnectionLost) break; // ran to completion
            attempt++;
            if (attempt > MAX_RECONNECT_ATTEMPTS) {
                LOGGER.warning("HACatchupWorker: connection lost, retries exhausted");
                publishProgress("HomeAssistant unreachable, will retry later", true);
                return Result.retry();
            }
            publishProgress("Connection lost, reconnecting ("
                    + attempt + "/" + MAX_RECONNECT_ATTEMPTS + ")", true);
            try {
                Thread.sleep(RECONNECT_BACKOFF_MS * attempt);
            } catch (InterruptedException ignored) {
            }
        }

        LOGGER.info("HACatchupWorker:doWork finished");
        publishProgress("All done importing HomeAssistant data", true);

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

    private class StatsForPeriodResultHandler  implements MessageHandler<HAMessage> {
        private final HADispatcher mHAClient;
        private final LocalDateTime finishLDT;
        private final LocalDateTime startLDT;

        public StatsForPeriodResultHandler(HADispatcher mHAClient, LocalDateTime startLDT, LocalDateTime finishLDT) {
            this.finishLDT = finishLDT;
            this.mHAClient = mHAClient;
            this.startLDT = startLDT.with(LocalTime.MAX);
        }

        @Override
        public void handleMessage(HAMessage message) {
            StatsForPeriodResult result = (StatsForPeriodResult) message;
            if (result.isSuccess()) {
                LOGGER.info("StatsForPeriodResultHandler.handleMessage.success");
                Map<Long, Map<String, Double>> pivotedResult = result.pivotStatsForPeriodResult();
                List<AlphaESSTransformedData> dbRows = result.calculateAndAddLoad(
                        "HomeAssistant", mEnergySensors, pivotedResult,
                        UserTimezoneStore.resolvedZone(mContext));
                mToutcRepository.addTransformedData(dbRows);
                updateBatteryCapacities(result);
                // Everything before this handler's day is committed; a reconnect resumes here.
                mCursorDate = startLDT.toLocalDate();
                Long anyDate = null;
                if (!pivotedResult.isEmpty()) {
                    anyDate = pivotedResult.keySet().iterator().next();
                }

                if (anyDate != null) {
                    LocalDateTime date = Instant.ofEpochMilli(anyDate)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime();
                    String processedDate = date.format(NOTIFY_FORMAT);
                    if (!processedDate.equals(mProgress)) {
                        mProgress = processedDate;
                        publishProgress("Working on " + mProgress, false);
                    }
                }
            }
            else {
                LOGGER.warning("StatsForPeriodResultHandler.handleMessage.failure: "
                        + result.getErrorDescription());
            }
            if (!startLDT.isBefore(finishLDT) || mStopped) {
                mHAClient.stop();
                synchronized (lock) {
                    isWorkCompleted = true;
                    lock.notifyAll(); // Notify any waiting threads
                }
            }
            else {
                LOGGER.info("StatsForPeriodResultHandler.handleMessage.next: " + startLDT.format(DATE_FORMAT));
                StatsForPeriodRequest request = new StatsForPeriodRequest(mEnergySensors.getSenorList());
                request.setStartAndEndTimes(startLDT.with(LocalTime.MIN), startLDT.with(LocalTime.MAX), mHAClient.generateId());
                mHAClient.sendMessage(request, new StatsForPeriodResultHandler(mHAClient, startLDT.plusDays(1), finishLDT));
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

        public AuthOKHandler(HADispatcher mHAClient) {
            this.mHAClient = mHAClient;
        }

        @Override
        public void handleMessage(HAMessage message) {
            LOGGER.info("AuthOKHandler.handleMessage");
            mHAClient.setAuthorized(true);
            // OK, authenticated, now fetch the energy stats from the resume cursor
            StatsForPeriodRequest request = new StatsForPeriodRequest(mEnergySensors.getSenorList());
            LocalDateTime startLDT = mCursorDate.atStartOfDay();
            request.setStartAndEndTimes(startLDT, startLDT.with(LocalTime.MAX), mHAClient.generateId());
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
            mAuthFailed = true;
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
    private Notification getNotification(@NonNull String progress) {
        // Build a notification using bytesRead and contentLength

        Context context = getApplicationContext();
        String id = context.getString(R.string.ha_channel_id);
        String title = context.getString(R.string.fetch_ha_notification_title);
        String cancel = context.getString(R.string.cancel_fetch_alpha);
        // This PendingIntent can be used to cancel the worker
        PendingIntent intent = WorkManager.getInstance(context)
                .createCancelPendingIntent(getId());

        PendingIntent activityPendingIntent = UI2NotificationLaunch.contentIntent(
                context, mUseUI2, ComparisonUIViewModel.Importer.HOME_ASSISTANT,
                HA_SYS_SN, ImportHomeAssistantActivity.class);

        return new NotificationCompat.Builder(context, id)
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
    }

    private void createChannel() {
        // Create a Notification channel
        CharSequence name = getApplicationContext().getString(R.string.ha_channel_name);
        String description = getApplicationContext().getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(
                getApplicationContext().getString(R.string.ha_channel_id), name, importance);
        channel.setDescription(description);
        // Register the channel with the system. You can't change the importance
        // or other notification behaviors after this.
        NotificationManager notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }
}
