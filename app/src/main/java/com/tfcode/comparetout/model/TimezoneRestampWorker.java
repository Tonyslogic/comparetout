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

package com.tfcode.comparetout.model;

import static com.tfcode.comparetout.MainActivity.CHANNEL_ID;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.R;
import com.tfcode.comparetout.TOUTCApplication;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.ui2.UserTimezoneStore;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Phase 2 of the saved-timezone rollout (see {@code plans/sim/timezone-and-rollout.md}): a one-time pass that
 * re-anchors the already-imported {@code alphaESSTransformedData} rows to the saved zone.
 *
 * <p>Each row's {@code date}/{@code minute} strings are the source system's local wall-clock (preserved through
 * the old device-zone round-trip for AlphaESS, or the raw HDF wall-clock for ESBN) and are what Compare renders;
 * they are <b>left unchanged</b> (the PK is {@code (sysSn, date, minute)}, so changing them would mean a risky
 * delete+reinsert). Instead the canonical {@code millisSinceEpoch} is recomputed as that wall-clock interpreted
 * in {@link UserTimezoneStore#resolvedZone}. This conforms existing data's instant to the saved zone and, in the
 * same pass, backfills ESBN rows whose millis was never set.</p>
 *
 * <p>Lives in {@code com.tfcode.comparetout.model} so it can reach the package-private
 * {@link ToutcDB#getDatabase} (mirroring {@link SnapshotExporter}) without editing the repository or DAO surface
 * beyond the read/update queries it needs. Guarded by a DataStore flag so it runs exactly once.</p>
 */
public class TimezoneRestampWorker extends Worker {

    /** DataStore flag: set once the re-stamp has completed, so it never runs again. */
    public static final String DONE_KEY = "tz_restamp_v1_done";
    private static final String UNIQUE_WORK = "tz_restamp_v1";
    private static final int BATCH = 5000;

    public TimezoneRestampWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    /** Enqueue the one-time re-stamp (no-op if already scheduled or already done). Safe to call on every start. */
    public static void enqueue(@NonNull Context context) {
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(TimezoneRestampWorker.class).build());
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        TOUTCApplication app = (TOUTCApplication) context;
        if ("true".equals(app.getStringValueFromDataStore(DONE_KEY))) return Result.success();

        ZoneId zone = UserTimezoneStore.resolvedZone(context);
        AlphaEssDAO dao = ToutcDB.getDatabase(context).alphaEssDAO();

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText("Aligning imported data to your timezone")
                .setSmallIcon(R.drawable.housetick)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setSilent(true);

        try {
            List<String> sysSns = dao.getTransformedDataSysSns();
            int total = sysSns.size();
            int done = 0;
            for (String sysSn : sysSns) {
                builder.setProgress(Math.max(total, 1), done, false)
                        .setContentText("Aligning timezone: " + sysSn);
                notify(notificationManager, builder);

                List<AlphaESSTransformedData> rows = dao.getAllTransformedDataForSysSn(sysSn);
                for (AlphaESSTransformedData row : rows) {
                    row.setMillisSinceEpoch(toMillis(row.getDate(), row.getMinute(), zone));
                }
                // Update in batches to bound transaction/memory size on large sources.
                for (int i = 0; i < rows.size(); i += BATCH) {
                    dao.updateTransformedData(rows.subList(i, Math.min(i + BATCH, rows.size())));
                }
                done++;
            }
            app.putStringValueIntoDataStore(DONE_KEY, "true");
            builder.setProgress(0, 0, false).setContentText("Timezone alignment complete");
            notify(notificationManager, builder);
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            // Not marked done — it will retry on a later start.
            return Result.retry();
        }
    }

    /** UTC millis for a stored wall-clock ({@code yyyy-MM-dd} + {@code HH:mm}) interpreted in {@code zone}. */
    private static long toMillis(String date, String minute, ZoneId zone) {
        LocalDateTime ldt = LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(minute));
        return ldt.atZone(zone).toInstant().toEpochMilli();
    }

    private void notify(NotificationManagerCompat manager, NotificationCompat.Builder builder) {
        if (ActivityCompat.checkSelfPermission(getApplicationContext(),
                android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        manager.notify(2, builder.build());
    }
}
