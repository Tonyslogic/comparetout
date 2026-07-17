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

package com.tfcode.comparetout.importers.octopus;

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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.TOUTCApplication;
import com.tfcode.comparetout.importers.CredentialStore;
import com.tfcode.comparetout.importers.octopus.responses.ConsumptionResponse;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.ui2.OctopusTariffPlans;
import com.tfcode.comparetout.ui2.OctopusTariffPlansEntryPoint;
import com.tfcode.comparetout.ui2.UI2NotificationLaunch;
import com.tfcode.comparetout.ui2.UserTimezoneStore;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import dagger.hilt.android.EntryPointAccessors;

/**
 * Fetches half-hourly import (buy) and export (feed) consumption from the
 * Octopus REST API and stores it in the shared alphaESSTransformedData table
 * under the "Octopus-&lt;MPAN&gt;" sysSn namespace.
 *
 * Also used as the daily periodic sync: with no KEY_START_DATE it resumes
 * from the latest stored date. After a successful data sync it refreshes the
 * generated Octopus price plans for the system's region (favouriting the
 * current tariff's plan when no favourite is set) — plan-generation failures
 * never fail the data sync.
 */
public class OctopusCatchUpWorker extends Worker {

    private final ToutcRepository mToutcRepository;
    private final NotificationManager mNotificationManager;
    // Distinct notification slot per worker class — see
    // plans/eventual-bouncing-hare.md. 2-9, 11, 12 are taken.
    private static final int mNotificationId = 13;
    private boolean mStopped = false;
    // Mirrors the other importer workers: cached on the worker thread and
    // consumed by getNotification() so the notification's content-intent
    // routes through UI2NotificationLaunch when the user has opted in.
    private boolean mUseUI2 = false;
    private String mSelectedSysSn = null;
    private long mLastNotifyAt = 0L;
    private static final long MIN_NOTIFY_INTERVAL_MS = 250L;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MIN_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public static final String KEY_SYSTEM_SN = "KEY_SYSTEM_SN";
    public static final String KEY_APP_ID = "KEY_APP_ID";         // account number
    public static final String KEY_APP_SECRET = "KEY_APP_SECRET"; // API key
    public static final String KEY_START_DATE = "KEY_START_DATE";

    public static final String PROGRESS = "PROGRESS";

    public OctopusCatchUpWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
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
        String apiKey = inputData.getString(KEY_APP_SECRET);
        if (null == apiKey) {
            // Secrets no longer travel in worker Data (plans/source/security.md §1);
            // the Data key above is honoured only for specs enqueued by older
            // app versions. Normal path: resolve from the encrypted DataStore.
            CredentialStore.Credentials credentials = CredentialStore.get(
                    getApplicationContext(), CredentialStore.Source.OCTOPUS);
            if (null == credentials) {
                publishProgress("Octopus credentials unavailable — re-enter them", true, false);
                return Result.failure();
            }
            apiKey = credentials.second;
        }
        String systemSN = inputData.getString(KEY_SYSTEM_SN);
        String startDate = inputData.getString(KEY_START_DATE);
        mSelectedSysSn = systemSN;
        mUseUI2 = UI2NotificationLaunch.isUI2Enabled(getApplicationContext());

        OctopusSystem system = loadSystemFromPreferences(systemSN);
        if (null == system) {
            publishProgress("Octopus system not configured: " + systemSN, true, false);
            return Result.success();
        }

        if (null == startDate) startDate = mToutcRepository.getLatestDateForSn(systemSN);
        if (null == startDate) startDate = LocalDate.now().minusYears(1).format(DATE_FORMAT);

        publishProgress("Starting Fetch", true, true);

        OctopusRestClient client = new OctopusRestClient(apiKey);
        String periodFrom = LocalDate.parse(startDate, DATE_FORMAT)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toString();

        // Merge import (buy) and export (feed) readings by interval instant.
        Map<Long, double[]> merged = new TreeMap<>();
        try {
            for (String serial : system.importSerials) {
                if (mStopped) break;
                fetchSeries(client, system.importMpan, serial, periodFrom, merged, 0, "import");
            }
            if (null != system.exportMpan) {
                for (String serial : system.exportSerials) {
                    if (mStopped) break;
                    fetchSeries(client, system.exportMpan, serial, periodFrom, merged, 1, "export");
                }
            }
        } catch (OctopusException e) {
            String finalProgress = e.getMessage() == null ? "Failed for unknown reason" : e.getMessage();
            publishProgress(finalProgress, true, false);
            return Result.success();
        }

        if (mStopped) {
            mNotificationManager.cancel(mNotificationId);
            return Result.success();
        }

        // Store transformed data: canonical UTC millis from the reading's own
        // offset; date/minute display strings rendered in the saved zone (the
        // same convention as the HA importer).
        ZoneId zone = UserTimezoneStore.resolvedZone(getApplicationContext());
        List<AlphaESSTransformedData> normalizedEntityList = new ArrayList<>();
        for (Map.Entry<Long, double[]> entry : merged.entrySet()) {
            ZonedDateTime local = java.time.Instant.ofEpochMilli(entry.getKey()).atZone(zone);
            AlphaESSTransformedData dbEntry = new AlphaESSTransformedData();
            dbEntry.setBuy(entry.getValue()[0]);
            dbEntry.setFeed(entry.getValue()[1]);
            dbEntry.setPv(0D);
            dbEntry.setLoad(0D);
            dbEntry.setSysSn(systemSN != null ? systemSN : "Not set");
            dbEntry.setDate(local.format(DATE_FORMAT));
            dbEntry.setMinute(local.format(MIN_FORMAT));
            dbEntry.setMillisSinceEpoch(entry.getKey());
            normalizedEntityList.add(dbEntry);
        }
        mToutcRepository.addTransformedData(normalizedEntityList);
        publishProgress("Stored " + normalizedEntityList.size() + " readings", true, true);

        // Refresh generated price plans for the region (OQ-4): all open
        // products, current agreement favourited when no favourite is set.
        if (null != system.region) {
            try {
                publishProgress("Updating Octopus price plans", true, true);
                OctopusTariffPlans planGenerator = EntryPointAccessors
                        .fromApplication(getApplicationContext(), OctopusTariffPlansEntryPoint.class)
                        .octopusTariffPlans();
                planGenerator.generateForRegionBlocking(system.region, system.currentTariffCode);
            } catch (Exception e) {
                publishProgress("Price-plan update skipped: " + e.getMessage(), true, true);
            }
        }

        publishProgress("All done importing " + systemSN, true, true);

        if (mStopped) mNotificationManager.cancel(mNotificationId);

        return Result.success();
    }

    private void fetchSeries(OctopusRestClient client, String mpan, String serial,
                             String periodFrom, Map<Long, double[]> merged,
                             int index, String label) throws OctopusException {
        String next = null;
        int total = 0;
        do {
            if (mStopped) return;
            ConsumptionResponse page =
                    client.getConsumptionPage(mpan, serial, periodFrom, null, next);
            if (null != page.results) {
                for (ConsumptionResponse.Reading reading : page.results) {
                    long millis;
                    try {
                        millis = OffsetDateTime.parse(reading.intervalStart).toInstant().toEpochMilli();
                    } catch (Exception badTimestamp) {
                        continue;
                    }
                    double[] row = merged.computeIfAbsent(millis, k -> new double[2]);
                    row[index] += reading.consumption;
                }
                total += page.results.size();
                publishProgress("Fetched " + total + " " + label + " readings", false, true);
            }
            next = page.next;
        } while (null != next);
    }

    private OctopusSystem loadSystemFromPreferences(String systemSN) {
        TOUTCApplication application = (TOUTCApplication) getApplicationContext();
        String systemListJson = application.getStringValueFromDataStore(
                ImportOctopusOverview.OCTOPUS_SYSTEM_LIST_KEY);
        if (null == systemListJson || systemListJson.isEmpty()) return null;
        List<OctopusSystem> systems = new Gson().fromJson(systemListJson,
                new TypeToken<List<OctopusSystem>>(){}.getType());
        if (null == systems) return null;
        for (OctopusSystem system : systems) {
            if (system.getSysSn().equals(systemSN)) return system;
        }
        return null;
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
        String id = context.getString(R.string.octopus_channel_id);
        String title = context.getString(R.string.fetch_octopus_notification_title);
        String cancel = context.getString(R.string.cancel_fetch_alpha);
        // This PendingIntent can be used to cancel the worker
        PendingIntent intent = WorkManager.getInstance(context)
                .createCancelPendingIntent(getId());

        PendingIntent activityPendingIntent = UI2NotificationLaunch.contentIntent(
                context, mUseUI2, ComparisonUIViewModel.Importer.OCTOPUS,
                mSelectedSysSn, ImportOctopusActivity.class);

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
        CharSequence name = getApplicationContext().getString(R.string.octopus_channel_name);
        String description = getApplicationContext().getString(R.string.channel_description);
        // DEFAULT (not LOW): builders silence via setSilent(true); a below-DEFAULT "silent" channel hides the status-bar icon
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(
                getApplicationContext().getString(R.string.octopus_channel_id), name, importance);
        channel.setDescription(description);
        NotificationManager notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }
}
