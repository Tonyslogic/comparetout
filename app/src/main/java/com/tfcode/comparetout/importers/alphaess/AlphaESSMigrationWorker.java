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

package com.tfcode.comparetout.importers.alphaess;

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
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.ui2.UserTimezoneStore;

import java.time.ZoneId;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawEnergy;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawPower;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.ui2.UI2NotificationLaunch;

import java.util.List;
import java.util.Map;

/**
 * Re-runs the v2 AlphaESS transform across every (sysSn, date) that already has
 * raw data, so the new flow-decomposition columns (pv2load, pv2bat, pv2grid,
 * bat2load, bat2grid, grid2load, grid2bat, batChargeIn, batDischargeOut) and
 * {@code evActual} get populated for historical rows that pre-date Phase 1.
 *
 * The worker is idempotent per-day: re-transforming a day with the same raw
 * inputs produces the same processed rows, so cancel + restart picks up cleanly.
 *
 * Stamps {@code AlphaESSTransformMeta.TRANSFORM_VERSION_CURRENT} only on
 * successful full completion. A cancellation leaves the meta alone so the
 * Migrate button keeps surfacing in the UI.
 */
public class AlphaESSMigrationWorker extends Worker {

    private final ToutcRepository mToutcRepository;
    private final NotificationManager mNotificationManager;
    // Distinct notification slot per worker class. The flicker plan
    // (plans/eventual-bouncing-hare.md) allocated IDs 2–11; 12 is the next free.
    private static final int mNotificationId = 12;
    private boolean mStopped = false;
    private boolean mUseUI2 = false;
    private String mSelectedSysSn = null;
    // Throttle in-loop progress notifications so Android's ~5/sec coalescing
    // never drops them silently. Terminal states use force=true.
    private long mLastNotifyAt = 0L;
    private static final long MIN_NOTIFY_INTERVAL_MS = 250L;

    public static final String KEY_SYSTEM_SN = "KEY_SYSTEM_SN";
    public static final String PROGRESS = "PROGRESS";

    public AlphaESSMigrationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mToutcRepository = new ToutcRepository((Application) context);
        mNotificationManager = (NotificationManager)
                context.getSystemService(NOTIFICATION_SERVICE);
        createChannel();
        setProgressAsync(new Data.Builder().putString(PROGRESS, "Starting migration").build());
    }

    @Override
    public void onStopped() {
        super.onStopped();
        mStopped = true;
    }

    @NonNull
    @Override
    public Result doWork() {
        System.out.println("AlphaESSMigrationWorker:doWork invoked");
        Data inputData = getInputData();
        String systemSN = inputData.getString(KEY_SYSTEM_SN);
        if (systemSN == null || systemSN.isEmpty()) {
            return Result.failure();
        }
        mSelectedSysSn = systemSN;
        mUseUI2 = UI2NotificationLaunch.isUI2Enabled(getApplicationContext());

        publishProgress("Starting", true);

        List<String> dates = mToutcRepository.getExportDatesForSN(systemSN);
        if (dates == null || dates.isEmpty()) {
            // Nothing raw to migrate — stamp v2 anyway so the Migrate button hides.
            mToutcRepository.stampAlphaESSTransformCurrent(systemSN);
            publishProgress("Nothing to migrate", true);
            return Result.success();
        }

        String lastDate = dates.get(dates.size() - 1);
        for (String date : dates) {
            if (mStopped) break;
            publishProgress("Re-processing " + date + " of " + lastDate, false);

            AlphaESSRawEnergy energy = mToutcRepository.getAlphaESSEnergyForDate(systemSN, date);
            if (energy == null) continue;
            List<AlphaESSRawPower> powerList = mToutcRepository.getAlphaESSPowerForSharing(systemSN, date);
            if (powerList == null || powerList.isEmpty()) continue;

            // Interpret/stamp source timestamps in the saved zone (Phase 1, timezone-and-rollout.md).
            ZoneId zone = UserTimezoneStore.resolvedZone(getApplicationContext());
            // Fix the power-data (5 minute alignment and missing entries).
            List<DataMassager.DataPoint> points = DataMassager.getDataPointsForPowerResponse(powerList, zone);
            Map<Long, FiveMinuteEnergies> fixed = DataMassager.oneDayDataInFiveMinuteIntervals(points, zone);
            // Daily totals — same formulae as ImportWorker/CatchUpWorker.
            double ePV = energy.getEnergypv();
            double eLoad = (ePV - energy.getEnergyOutput()) + energy.getEnergyInput();
            double eFeed = energy.getEnergyOutput();
            double eBuy = energy.getEnergyInput();
            if (eBuy < 0D) eBuy = 0D;
            // Unitize and scale.
            Map<Long, FiveMinuteEnergies> massaged = DataMassager.massage(fixed, ePV, eLoad, eFeed, eBuy, zone);
            // v2: per-interval EV charger kWh, scaled to daily total.
            Map<Long, Double> evByInterval = DataMassager.evIn5MinIntervals(powerList, energy.getEnergyChargingPile(), zone);

            // Re-write transformed data (REPLACE on conflict — fully idempotent).
            List<AlphaESSTransformedData> normalized =
                    AlphaESSEntityUtil.getTransformedDataRows(massaged, evByInterval, systemSN, zone);
            mToutcRepository.addTransformedData(normalized);
        }

        if (mStopped) {
            // Cancellation: leave the meta untouched so the Migrate button stays visible.
            publishProgress("Cancelled", true);
            mNotificationManager.cancel(mNotificationId);
            return Result.success();
        }

        // Full completion: stamp v2 so the Migrate button hides.
        mToutcRepository.stampAlphaESSTransformCurrent(systemSN);
        publishProgress("AlphaESS data re-processed", true);
        return Result.success();
    }

    /**
     * Same pattern as the other importer workers (see ExportWorker). Direct
     * notify from the worker thread — NotificationManager.notify is thread-safe
     * and avoiding handler.post prevents the late-drain race that gave us
     * "stuck at X/Y" notifications.
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
        Context context = getApplicationContext();
        String id = context.getString(R.string.alphaess_channel_id);
        String title = context.getString(R.string.alphaess_migrate_notification_title);
        String cancel = context.getString(R.string.cancel_fetch_alpha);
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
                .addAction(android.R.drawable.ic_delete, cancel, cancelPendingIntent)
                .build();
    }

    private void createChannel() {
        CharSequence name = getApplicationContext().getString(R.string.alphaess_channel_name);
        String description = getApplicationContext().getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(
                getApplicationContext().getString(R.string.alphaess_channel_id), name, importance);
        channel.setDescription(description);
        NotificationManager notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }
}
