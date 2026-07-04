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
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.opencsv.CSVReader;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.TOUTCApplication;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.ui2.UI2NotificationLaunch;
import com.tfcode.comparetout.ui2.UserTimezoneStore;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Offline fallback (OQ-6): imports the consumption CSV a user can download
 * from the Octopus dashboard without creating an API key. The file has no
 * MPAN, so rows are stored under the selected system — or the "Octopus-CSV"
 * fallback namespace, which is registered in the system list on first use.
 *
 * Expected columns (order-independent, matched on the header row):
 * "Consumption (kwh)", "Start" (ISO-8601 with offset). Rows are import-only —
 * the download does not include export.
 */
public class OctopusCsvImportWorker extends Worker {

    public static final String CSV_FALLBACK_SYS_SN = OctopusSystem.SYS_SN_PREFIX + "CSV";

    private final ToutcRepository mToutcRepository;
    private final NotificationManager mNotificationManager;
    // Distinct notification slot per worker class — see
    // plans/eventual-bouncing-hare.md. 2-9, 11-13 are taken.
    private static final int mNotificationId = 14;
    private boolean mStopped = false;
    private boolean mUseUI2 = false;
    private String mSelectedSysSn = null;
    private long mLastNotifyAt = 0L;
    private static final long MIN_NOTIFY_INTERVAL_MS = 250L;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MIN_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public static final String KEY_SYSTEM_SN = "KEY_SYSTEM_SN";
    public static final String KEY_URI = "KEY_URI";

    public static final String PROGRESS = "PROGRESS";

    public OctopusCsvImportWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mToutcRepository = new ToutcRepository((Application) context);
        mNotificationManager = (NotificationManager)
                context.getSystemService(NOTIFICATION_SERVICE);
        createChannel();
        setProgressAsync(new Data.Builder().putString(PROGRESS, "Starting import from file").build());
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
        String systemSN = inputData.getString(KEY_SYSTEM_SN);
        String uriString = inputData.getString(KEY_URI);
        Uri fileUri = Uri.parse(uriString);
        if (null == systemSN) systemSN = CSV_FALLBACK_SYS_SN;
        mSelectedSysSn = systemSN;
        mUseUI2 = UI2NotificationLaunch.isUI2Enabled(getApplicationContext());

        publishProgress("Importing energy", true);

        Map<Long, Double> importByMillis = new TreeMap<>();
        try (InputStream is = getApplicationContext().getContentResolver().openInputStream(fileUri);
             CSVReader reader = new CSVReader(new InputStreamReader(is))) {
            String[] header = reader.readNext();
            if (null == header) {
                publishProgress("Empty file", true);
                return Result.success();
            }
            int consumptionCol = -1;
            int startCol = -1;
            for (int i = 0; i < header.length; i++) {
                String col = header[i].trim().toLowerCase();
                if (col.startsWith("consumption")) consumptionCol = i;
                if (col.equals("start") || col.startsWith("interval start")) startCol = i;
            }
            if (consumptionCol < 0 || startCol < 0) {
                publishProgress("Not an Octopus consumption CSV (missing Consumption/Start columns)", true);
                return Result.success();
            }
            String[] line;
            int count = 0;
            while ((line = reader.readNext()) != null) {
                if (mStopped) break;
                if (line.length <= Math.max(consumptionCol, startCol)) continue;
                try {
                    double kwh = Double.parseDouble(line[consumptionCol].trim());
                    long millis = OffsetDateTime.parse(line[startCol].trim()).toInstant().toEpochMilli();
                    importByMillis.merge(millis, kwh, Double::sum);
                } catch (Exception badRow) {
                    continue;
                }
                count++;
                if (count % 500 == 0) publishProgress("Read " + count + " rows", false);
            }
        } catch (Exception e) {
            publishProgress("Import failed: " + e.getMessage(), true);
            return Result.success();
        }

        if (importByMillis.isEmpty()) {
            publishProgress("No readings found in file", true);
            return Result.success();
        }

        // Store transformed data: canonical UTC millis from the CSV's own
        // offsets; date/minute display strings rendered in the saved zone.
        ZoneId zone = UserTimezoneStore.resolvedZone(getApplicationContext());
        List<AlphaESSTransformedData> normalizedEntityList = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : importByMillis.entrySet()) {
            ZonedDateTime local = java.time.Instant.ofEpochMilli(entry.getKey()).atZone(zone);
            AlphaESSTransformedData dbEntry = new AlphaESSTransformedData();
            dbEntry.setBuy(entry.getValue());
            dbEntry.setFeed(0D);
            dbEntry.setPv(0D);
            dbEntry.setLoad(0D);
            dbEntry.setSysSn(systemSN);
            dbEntry.setDate(local.format(DATE_FORMAT));
            dbEntry.setMinute(local.format(MIN_FORMAT));
            dbEntry.setMillisSinceEpoch(entry.getKey());
            normalizedEntityList.add(dbEntry);
        }
        mToutcRepository.addTransformedData(normalizedEntityList);

        // Register the fallback namespace so overview/graphs can select it.
        if (CSV_FALLBACK_SYS_SN.equals(systemSN)) registerFallbackSystem();

        publishProgress("All done importing " + systemSN, true);

        if (mStopped) mNotificationManager.cancel(mNotificationId);

        return Result.success();
    }

    private void registerFallbackSystem() {
        TOUTCApplication application = (TOUTCApplication) getApplicationContext();
        String systemListJson = application.getStringValueFromDataStore(
                ImportOctopusOverview.OCTOPUS_SYSTEM_LIST_KEY);
        List<OctopusSystem> systems = null;
        if (null != systemListJson && !systemListJson.isEmpty()) {
            systems = new Gson().fromJson(systemListJson,
                    new TypeToken<List<OctopusSystem>>(){}.getType());
        }
        if (null == systems) systems = new ArrayList<>();
        for (OctopusSystem system : systems) {
            if (CSV_FALLBACK_SYS_SN.equals(system.getSysSn())) return;
        }
        OctopusSystem fallback = new OctopusSystem();
        fallback.importMpan = "CSV";
        systems.add(fallback);
        boolean x = application.putStringValueIntoDataStore(
                ImportOctopusOverview.OCTOPUS_SYSTEM_LIST_KEY, new Gson().toJson(systems));
        if (!x) System.out.println("OctopusCsvImportWorker::registerFallbackSystem, failed to store list");
        x = application.putStringValueIntoDataStore(
                ImportOctopusOverview.OCTOPUS_PREVIOUS_SELECTED_KEY, CSV_FALLBACK_SYS_SN);
        if (!x) System.out.println("OctopusCsvImportWorker::registerFallbackSystem, failed to update selection");
    }

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
        Context context = getApplicationContext();
        String id = context.getString(R.string.octopus_channel_id);
        String title = context.getString(R.string.octopus_import_notification_title);
        String cancel = context.getString(R.string.cancel_fetch_alpha);
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
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(activityPendingIntent)
                .addAction(android.R.drawable.ic_delete, cancel, intent)
                .build();
    }

    private void createChannel() {
        CharSequence name = getApplicationContext().getString(R.string.octopus_channel_name);
        String description = getApplicationContext().getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(
                getApplicationContext().getString(R.string.octopus_channel_id), name, importance);
        channel.setDescription(description);
        NotificationManager notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }
}
