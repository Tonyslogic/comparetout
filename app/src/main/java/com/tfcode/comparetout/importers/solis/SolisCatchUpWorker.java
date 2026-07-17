/*
 * Copyright (c) 2026. Tony Finnerty
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

package com.tfcode.comparetout.importers.solis;

import static android.content.Context.NOTIFICATION_SERVICE;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.importers.CredentialStore;
import com.tfcode.comparetout.importers.solis.responses.StationDayEnergyResponse;
import com.tfcode.comparetout.importers.solis.responses.StationDayResponse;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.ui2.UI2MainActivity;
import com.tfcode.comparetout.ui2.UI2NotificationLaunch;
import com.tfcode.comparetout.ui2.UserTimezoneStore;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches station-level SolisCloud data day-by-day into the shared
 * alphaESSTransformedData table under the "Solis-&lt;stationId&gt;" sysSn
 * namespace: per day, the stationDay 5-minute curve plus the (per-date
 * cached) stationDayEnergyList totals go through SolisDataMassager.
 *
 * One class serves both the one-shot catch-up (KEY_START_DATE set) and the
 * periodic daily run (absent ⇒ yesterday) — the Home Assistant / Octopus
 * simplification. Days already stored are skipped, so re-runs are cheap and
 * a retry resumes from the first missing day. Request pacing/backoff lives
 * in SolisCloudClient; fatal auth/clock-skew failures surface here as
 * distinct notifications and {@code Result.failure()}, transient exhaustion
 * as {@code Result.retry()} (enqueue with EXPONENTIAL backoff criteria).
 */
public class SolisCatchUpWorker extends Worker {

    private final ToutcRepository mToutcRepository;
    private final NotificationManager mNotificationManager;
    // Distinct notification slot per worker class — see
    // plans/eventual-bouncing-hare.md. 2-15 and 4242 are taken.
    private static final int mNotificationId = 16;
    private boolean mStopped = false;
    // Cached on the worker thread, consumed by getNotification() so the
    // content intent routes through UI2NotificationLaunch. Solis is a
    // UI2-only source, so the "legacy" fallback is the UI2 shell too.
    private boolean mUseUI2 = false;
    private String mSelectedSysSn = null;
    private long mLastNotifyAt = 0L;
    private static final long MIN_NOTIFY_INTERVAL_MS = 250L;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static final String KEY_KEY_ID = "KEY_KEY_ID";
    public static final String KEY_SECRET = "KEY_SECRET";
    public static final String KEY_STATION_ID = "KEY_STATION_ID";
    public static final String KEY_STATION_NAME = "KEY_STATION_NAME";
    public static final String KEY_START_DATE = "KEY_START_DATE";
    /** Currency code for the stationDay request (money fields are unused). */
    public static final String KEY_CURRENCY = "KEY_CURRENCY";

    public static final String PROGRESS = "PROGRESS";

    public SolisCatchUpWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mToutcRepository = new ToutcRepository((Application) context);
        mNotificationManager = (NotificationManager)
                context.getSystemService(NOTIFICATION_SERVICE);
        createChannel();
    }

    @Override
    public void onStopped() {
        super.onStopped();
        mStopped = true;
    }

    @NonNull
    @Override
    public Result doWork() {
        Data inputData = getInputData();
        String keyId = inputData.getString(KEY_KEY_ID);
        String secret = inputData.getString(KEY_SECRET);
        if (null == keyId || null == secret) {
            // Secrets no longer travel in worker Data (plans/source/security.md §1);
            // the Data keys above are honoured only for specs enqueued by older
            // app versions. Normal path: resolve from the encrypted DataStore.
            CredentialStore.Credentials credentials = CredentialStore.get(
                    getApplicationContext(), CredentialStore.Source.SOLIS);
            if (null == credentials) {
                publishProgress("SolisCloud credentials unavailable — re-enter them", true, false);
                return Result.failure();
            }
            keyId = credentials.first;
            secret = credentials.second;
        }
        String stationId = inputData.getString(KEY_STATION_ID);
        String stationName = inputData.getString(KEY_STATION_NAME);
        String startDate = inputData.getString(KEY_START_DATE);
        String currency = inputData.getString(KEY_CURRENCY);
        if (null == currency || currency.isEmpty()) currency = "EUR";
        if (null == stationId) {
            publishProgress("Solis station not configured", true, false);
            return Result.success();
        }
        String sysSn = "Solis-" + stationId;
        mSelectedSysSn = sysSn;
        if (null == stationName) stationName = sysSn;
        mUseUI2 = UI2NotificationLaunch.isUI2Enabled(getApplicationContext());

        // Daily mode: no start date ⇒ (re)fetch yesterday. The loop never
        // touches today — its data is still accumulating server-side and a
        // partial day would be skipped as "already stored" forever after.
        if (null == startDate) startDate = LocalDate.now().minusDays(1).format(DATE_FORMAT);

        publishProgress("Starting Fetch", true, true);

        ZoneId zone = UserTimezoneStore.resolvedZone(getApplicationContext());
        SolisCloudClient client = new SolisCloudClient(keyId, secret);
        // stationDayEnergyList covers ALL stations for a date — cache per
        // date so parallel per-station chains in the same run could share it.
        Map<String, Map<String, StationDayEnergyResponse.Record>> totalsByDate = new HashMap<>();

        LocalDate current = LocalDate.parse(startDate, DATE_FORMAT);
        LocalDate end = LocalDate.now();
        int storedRows = 0;
        try {
            while (current.isBefore(end) && !mStopped) {
                String dateString = current.format(DATE_FORMAT);
                if (mToutcRepository.checkSysSnForDataOnDate(sysSn, dateString)) {
                    current = current.plusDays(1);
                    continue;
                }
                Map<String, StationDayEnergyResponse.Record> totals = totalsByDate.get(dateString);
                if (null == totals) {
                    totals = client.getStationDayEnergyTotals(dateString);
                    totalsByDate.put(dateString, totals);
                }
                StationDayEnergyResponse.Record dayTotals = totals.get(stationId);
                if (null == dayTotals) {
                    // Station too new / API gap: move on, a later run
                    // re-fetches because the day stays missing locally.
                    publishProgress("No totals for " + dateString + ", skipped", false, true);
                    current = current.plusDays(1);
                    continue;
                }
                // The station's UTC offset in hours for THIS date (DST-correct).
                int tzHours = zone.getRules()
                        .getOffset(current.atStartOfDay(zone).toInstant())
                        .getTotalSeconds() / 3600;
                List<StationDayResponse> samples =
                        client.getStationDay(stationId, dateString, tzHours, currency);
                List<AlphaESSTransformedData> rows =
                        SolisDataMassager.massage(sysSn, current, zone, samples, dayTotals);
                if (!rows.isEmpty()) {
                    mToutcRepository.addTransformedData(rows);
                    storedRows += rows.size();
                }
                publishProgress("Fetched " + dateString, false, true);
                current = current.plusDays(1);
            }
        } catch (SolisCloudClockSkewException e) {
            publishProgress("SolisCloud rejected the request time — check the device clock",
                    true, false);
            return Result.failure();
        } catch (SolisCloudAuthException e) {
            publishProgress("SolisCloud rejected the credentials — re-enter the API details",
                    true, false);
            return Result.failure();
        } catch (SolisCloudException e) {
            publishProgress("SolisCloud unreachable, will retry — stored "
                    + storedRows + " readings so far", true, false);
            return Result.retry();
        }

        if (mStopped) {
            mNotificationManager.cancel(mNotificationId);
            return Result.success();
        }

        publishProgress("All done importing " + stationName
                + " (" + storedRows + " readings)", true, true);
        return Result.success();
    }

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
        Context context = getApplicationContext();
        String id = context.getString(R.string.solis_channel_id);
        String title = context.getString(R.string.fetch_solis_notification_title);
        String cancel = context.getString(R.string.cancel_fetch_alpha);
        // This PendingIntent can be used to cancel the worker
        PendingIntent intent = WorkManager.getInstance(context)
                .createCancelPendingIntent(getId());

        PendingIntent activityPendingIntent = UI2NotificationLaunch.contentIntent(
                context, mUseUI2, ComparisonUIViewModel.Importer.SOLIS,
                mSelectedSysSn, UI2MainActivity.class);

        return new NotificationCompat.Builder(context, id)
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(progress)
                .setSmallIcon(R.drawable.ic_baseline_download_24)
                .setOngoing(true)
                .setAutoCancel(autoCancel)
                .setSilent(true)
                .setContentIntent(activityPendingIntent)
                .addAction(android.R.drawable.ic_delete, cancel, intent)
                .build();
    }

    private void createChannel() {
        CharSequence name = getApplicationContext().getString(R.string.solis_channel_name);
        String description = getApplicationContext().getString(R.string.channel_description);
        // DEFAULT (not LOW): builders silence via setSilent(true); a below-DEFAULT "silent" channel hides the status-bar icon
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(
                getApplicationContext().getString(R.string.solis_channel_id), name, importance);
        channel.setDescription(description);
        NotificationManager notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }
}
