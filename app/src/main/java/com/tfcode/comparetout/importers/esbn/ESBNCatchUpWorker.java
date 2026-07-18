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
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.SimulatorLauncher;
import com.tfcode.comparetout.importers.CredentialStore;
import com.tfcode.comparetout.importers.esbn.responses.ESBNAuthException;
import com.tfcode.comparetout.importers.esbn.responses.ESBNException;
import com.tfcode.comparetout.importers.esbn.responses.ESBNVerificationException;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.ui2.UserTimezoneStore;

import java.time.ZoneId;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.ui2.UI2NotificationLaunch;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cloud-fetch of the full ESBN HDF for one MPRN — EXPERIMENTAL (scraped
 * portal flow, plans/source/esbn.md). One class serves both the one-shot
 * catch-up and the WEEKLY periodic run (the schedule is weekly, not daily,
 * because every run spends one login from ESB's ~2-logins-per-IP-per-day
 * budget and HDF data is day-granular history, not live telemetry).
 * Exactly one login + one download POST per run; failures never retry
 * automatically — retrying burns the login budget and looks like a bot.
 */
public class ESBNCatchUpWorker extends Worker {

    private final ToutcRepository mToutcRepository;
    private final NotificationManager mNotificationManager;
    // Distinct notification slot per worker class — see
    // plans/eventual-bouncing-hare.md.
    private static final int mNotificationId = 8;
    private boolean mStopped = false;
    // Mirrors the other importer workers: cached on the worker thread and
    // consumed by getNotification() so the notification's content-intent
    // routes through UI2NotificationLaunch when the user has opted in.
    private boolean mUseUI2 = false;
    private String mSelectedSysSn = null;
    private long mLastNotifyAt = 0L;
    private static final long MIN_NOTIFY_INTERVAL_MS = 250L;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final DateTimeFormatter MIN_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    // Input carries the MPRN ONLY — credentials never enter worker Data
    // (plans/source/security.md §1); they are resolved via CredentialStore.
    public static final String KEY_SYSTEM_SN = "KEY_SYSTEM_SN";

    public static final String PROGRESS = "PROGRESS";

    public ESBNCatchUpWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
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
        Data inputData = getInputData();
        String systemSN = inputData.getString(KEY_SYSTEM_SN);
        mSelectedSysSn = systemSN;
        mUseUI2 = UI2NotificationLaunch.isUI2Enabled(getApplicationContext());

        CredentialStore.Credentials credentials = CredentialStore.get(
                getApplicationContext(), CredentialStore.Source.ESBN);
        if (null == credentials) {
            publishProgress("ESB Networks credentials unavailable — re-enter them", true, false);
            return Result.failure();
        }
        ESBNHDFClient esbnHDFClient = new ESBNHDFClient(credentials.first, credentials.second);
        esbnHDFClient.setSelectedMPRN(systemSN);

        publishProgress("Starting Fetch", true, true);

        // One login + one full-HDF download per run (the run is the unit the
        // §3 login budget is spent in). The full HDF re-covers everything, so
        // no start date / per-day skip logic is needed — stores are idempotent.
        Map<LocalDateTime, Pair<Double, Double>> timeAlignedEntries = new HashMap<>();
        try {
            esbnHDFClient.fetchSmartMeterDataHDF((calc, type, ldt, value) -> {
                Pair<Double, Double> importExport = timeAlignedEntries.get(ldt);
                switch (type) {
                    case IMPORT:
                        if ((null == importExport))
                            timeAlignedEntries.put(ldt, new Pair<>(value/(calc ? 1D : 2D), 0D));
                        else
                            timeAlignedEntries.put(ldt, new Pair<>(value/(calc ? 1D : 2D), importExport.second));
                        break;
                    case EXPORT:
                        if ((null == importExport))
                            timeAlignedEntries.put(ldt, new Pair<>(0D, value/(calc ? 1D : 2D)));
                        else
                            timeAlignedEntries.put(ldt, new Pair<>(importExport.first, value/(calc ? 1D : 2D)));
                        break;
                }
            });
        } catch (ESBNVerificationException e) {
            // FATAL, never auto-retried: another attempt burns the daily
            // login budget and looks like a bot (plans/source/esbn.md §3).
            publishProgress(e.getMessage(), true, false);
            return Result.failure();
        } catch (ESBNAuthException e) {
            publishProgress("ESB Networks rejected the sign-in — re-enter the credentials. ("
                    + e.getMessage() + ")", true, false);
            return Result.failure();
        } catch (ESBNException e) {
            String finalProgress = e.getMessage() == null ? "Failed for unknown reason. Consider files" : e.getMessage();
            publishProgress(finalProgress, true, false);
            return Result.failure();
        }

        // The HDF's most recent day has historically arrived incomplete and,
        // once stored, interfered with later merges — drop it unconditionally;
        // the next weekly run re-fetches it complete (plans/source/esbn.md §4).
        // Cloud fetch only: the user-driven FILE import keeps every row.
        ESBNHDFClient.pruneLatestDay(timeAlignedEntries);

        // Store transformed data. ESBN HDF read-times are local wall-clock; interpret them in the saved zone
        // to stamp the canonical UTC millis (Phase 1, timezone-and-rollout.md). The date/minute strings stay
        // the wall-clock the file provided (already the source's local time), which is what Compare renders.
        ZoneId zone = UserTimezoneStore.resolvedZone(getApplicationContext());
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
            dbEntry.setMillisSinceEpoch(entry.getKey().atZone(zone).toInstant().toEpochMilli());
            normalizedEntityList.add(dbEntry);
        }
        mToutcRepository.addTransformedData(normalizedEntityList);

        publishProgress("All done importing " + systemSN, true, true);

        if (mStopped) mNotificationManager.cancel(mNotificationId);

        // New readings may unblock flagged sim/costing work (no-op when
        // nothing is flagged, and in the SOURCE profile).
        SimulatorLauncher.simulateIfNeeded(getApplicationContext());

        return Result.success();
    }

    /**
     * Publish the worker's progress to WorkManager + the notification
     * shade. NotificationManager.notify is thread-safe, so no main-thread
     * hop is required (the earlier handler.post approach raced the worker's
     * own completion). [force]=true bypasses the in-loop throttle and is
     * used for the first/last updates so terminal state always lands.
     * [autoCancel] is forwarded to getNotification — error notifications
     * use false so the user can still see them after taps elsewhere.
     */
    private void publishProgress(@NonNull String progress, boolean force, boolean autoCancel) {
        setProgressAsync(new Data.Builder().putString(PROGRESS, progress).build());
        long now = System.currentTimeMillis();
        if (force || now - mLastNotifyAt > MIN_NOTIFY_INTERVAL_MS) {
            mLastNotifyAt = now;
            mNotificationManager.notify(mNotificationId, getNotification(progress, autoCancel));
        }
    }

    @NonNull
    private Notification getNotification(@NonNull String progress, boolean autoCancel) {
        // Build a notification using bytesRead and contentLength

        Context context = getApplicationContext();
        String id = context.getString(R.string.esbn_channel_id);
        String title = context.getString(R.string.fetch_esbn_notification_title);
        String cancel = context.getString(R.string.cancel_fetch_alpha);
        // This PendingIntent can be used to cancel the worker
        PendingIntent intent = WorkManager.getInstance(context)
                .createCancelPendingIntent(getId());

        PendingIntent activityPendingIntent = UI2NotificationLaunch.contentIntent(
                context, mUseUI2, ComparisonUIViewModel.Importer.ESBNHDF,
                mSelectedSysSn, ImportESBNActivity.class);


        return new NotificationCompat.Builder(context, id)
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(progress)
                .setSmallIcon(R.drawable.ic_baseline_download_24)
                .setOngoing(true)
                .setAutoCancel(autoCancel)
                .setSilent(true)
                .setContentIntent(activityPendingIntent)
                // Add the cancel action to the notification which can
                // be used to cancel the worker
                .addAction(android.R.drawable.ic_delete, cancel, intent)
                .build();
    }

    private void createChannel() {
        // Create a Notification channel
        CharSequence name = getApplicationContext().getString(R.string.esbn_channel_name);
        String description = getApplicationContext().getString(R.string.channel_description);
        // DEFAULT (not LOW): builders silence via setSilent(true); a below-DEFAULT "silent" channel hides the status-bar icon
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
