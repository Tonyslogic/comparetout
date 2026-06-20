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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.R;
import com.tfcode.comparetout.TOUTCApplication;
import com.tfcode.comparetout.ui2.UserTimezoneStore;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Phase 2 of the saved-timezone rollout (see {@code plans/sim/timezone-and-rollout.md}): a one-time pass that
 * re-anchors the already-imported {@code alphaESSTransformedData} rows to the saved zone by recomputing
 * {@code millisSinceEpoch} from each row's {@code date}+{@code minute} wall-clock interpreted in
 * {@link UserTimezoneStore#resolvedZone}. The wall-clock strings (the PK, and what Compare renders) are left
 * unchanged. In the same pass this also fills rows whose millis was never set (e.g. ESBN).
 *
 * <p><b>Why a separate SQLite connection (not Room's).</b> Writing through Room's own connection fires Room's
 * <em>TEMP</em> invalidation triggers ({@code room_table_modification_log}), which break on WAL pooled
 * connections and, on a bulk migration, fire per-row and churn every observer (the {@code SnapshotImporter}
 * learned this the hard way). So we open a plain {@link SQLiteDatabase} — TEMP triggers are per-connection, so a
 * separate connection neither fires them nor disturbs Room's observers (Compare picks the new values up on its
 * next query). The earlier {@code SQLITE_BUSY} this caused (it competes with the sim/cost workers for the WAL
 * write lock) is handled by {@code PRAGMA busy_timeout} — the connection waits for the lock instead of throwing.</p>
 *
 * <p><b>Large-dataset safe / resumable.</b> Rows are paged by {@code rowid} (never loading the whole table), each
 * batch committed and a {@code rowid} high-water mark persisted to DataStore, so a kill (time limit / debugger
 * restart) resumes from the cursor and the run converges. Progress + completion are logged under tag
 * {@code TzRestamp} and reported via {@link #setProgressAsync} (independent of notification permission).
 * Completion = the unique work {@code tz_restamp_v1} SUCCEEDED, or the {@code tz_restamp_v1_done} flag.</p>
 */
public class TimezoneRestampWorker extends Worker {

    private static final String TAG = "TzRestamp";
    /** DataStore flag: set once the re-stamp has fully completed, so it never runs again. */
    public static final String DONE_KEY = "tz_restamp_v1_done";
    /** DataStore high-water rowid: the last alphaESSTransformedData row re-stamped, for resume. */
    public static final String CURSOR_KEY = "tz_restamp_v1_cursor";
    private static final String UNIQUE_WORK = "tz_restamp_v1";
    private static final int BATCH = 2000;
    private static final int NOTIFICATION_ID = 2;
    /** Wait this long for the WAL write lock before giving up (vs. throwing SQLITE_BUSY immediately). */
    private static final long BUSY_TIMEOUT_MS = 30_000L;

    /** WorkManager progress keys — readable from the Background Task Inspector / a WorkInfo observer,
     *  independent of POST_NOTIFICATIONS (notifications are silently suppressed when it isn't granted). */
    public static final String PROGRESS_PCT = "pct";
    public static final String PROGRESS_DONE = "done";
    public static final String PROGRESS_TOTAL = "total";

    /** Max rowid touched by the most recent {@link #restampBatch} (rows are read in rowid order). */
    private long lastBatchRowid;

    public TimezoneRestampWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    /**
     * Enqueue the one-time re-stamp. Uses {@link ExistingWorkPolicy#REPLACE} (not KEEP): a previous attempt
     * sitting in retry-backoff is non-terminal and would block a KEEP enqueue indefinitely (the backoff grows
     * to hours). REPLACE clears any stuck/backing-off instance on each launch and starts fresh — safe because
     * the run resumes from its persisted {@link #CURSOR_KEY} and no-ops immediately once {@link #DONE_KEY} is set.
     */
    public static void enqueue(@NonNull Context context) {
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(TimezoneRestampWorker.class).build());
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        TOUTCApplication app = (TOUTCApplication) context;
        if ("true".equals(app.getStringValueFromDataStore(DONE_KEY))) {
            Log.i(TAG, "Already complete — nothing to do.");
            return Result.success();
        }

        ZoneId zone = UserTimezoneStore.resolvedZone(context);
        ToutcDB room = ToutcDB.getDatabase(context);
        String dbPath = context.getDatabasePath(room.getOpenHelper().getDatabaseName()).getAbsolutePath();

        ensureChannel(context);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText("Aligning imported data to your timezone")
                .setSmallIcon(R.drawable.housetick)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setSilent(true);

        // Request WAL on this connection (the DB is already in WAL via Room). Without it, openDatabase tries to
        // flip the journal mode to TRUNCATE, which fails against Room's live connection ("database is locked")
        // and needlessly fights for the lock. Requesting WAL is a no-op on an already-WAL DB, so no switch.
        SQLiteDatabase db = SQLiteDatabase.openDatabase(dbPath, null,
                SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING);
        try {
            // Wait for the WAL write lock rather than failing fast — this connection competes with the
            // sim/cost workers (and a running import) that write through Room. Resolves the SQLITE_BUSY churn.
            // NB: `PRAGMA busy_timeout = N` returns the new value as a row, so it MUST go through rawQuery —
            // execSQL rejects any statement that yields rows ("Queries can be performed using query or rawQuery
            // methods only"), which previously crashed the worker into an endless RETRY loop.
            try (Cursor pragma = db.rawQuery("PRAGMA busy_timeout = " + BUSY_TIMEOUT_MS, null)) {
                pragma.moveToFirst(); // force the statement to execute (rawQuery is lazy)
            }

            long total = count(db, "SELECT COUNT(*) FROM alphaESSTransformedData", null);
            long cursor = parseLong(app.getStringValueFromDataStore(CURSOR_KEY));
            long remaining = count(db, "SELECT COUNT(*) FROM alphaESSTransformedData WHERE rowid > ?",
                    new String[]{Long.toString(cursor)});
            long done = total - remaining;
            Log.i(TAG, "Start: total=" + total + " alreadyDone=" + done + " resumeFromRowid=" + cursor
                    + " zone=" + zone);
            setProgressAsync(progress(done, total));

            while (true) {
                if (isStopped()) {
                    Log.i(TAG, "Stopped at rowid=" + cursor + " (done=" + done + "/" + total
                            + ") — will resume on next run.");
                    return Result.retry();
                }
                int n = restampBatch(db, zone, cursor);
                if (n == 0) break;          // no more rows after the cursor → finished
                cursor = lastBatchRowid;    // max rowid the batch just committed
                done += n;
                app.putStringValueIntoDataStore(CURSOR_KEY, Long.toString(cursor));

                int pct = total > 0 ? (int) Math.min(100, (done * 100) / total) : 0;
                Log.i(TAG, "Batch committed: rowid=" + cursor + " done=" + done + "/" + total + " (" + pct + "%)");
                setProgressAsync(progress(done, total));
                builder.setProgress(100, pct, false)
                        .setContentText("Aligning timezone… " + done + " / " + total);
                notify(notificationManager, builder);
            }

            app.putStringValueIntoDataStore(DONE_KEY, "true");
            app.putStringValueIntoDataStore(CURSOR_KEY, "");
            Log.i(TAG, "Complete: re-stamped " + total + " rows.");
            builder.setProgress(0, 0, false).setOngoing(false)
                    .setContentText("Timezone alignment complete");
            notify(notificationManager, builder);
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Re-stamp failed; will retry (cursor persisted).", e);
            return Result.retry();   // not marked done — resumes from the persisted cursor
        } finally {
            db.close();
        }
    }

    /**
     * Re-stamp up to {@link #BATCH} rows whose rowid is greater than {@code afterRowid}, in one transaction.
     * Returns the number of rows updated (0 when none remain). Bounded memory: only the page is read.
     */
    private int restampBatch(SQLiteDatabase db, ZoneId zone, long afterRowid) {
        int n = 0;
        db.beginTransaction();
        try (Cursor c = db.rawQuery(
                "SELECT rowid, date, minute FROM alphaESSTransformedData WHERE rowid > ? ORDER BY rowid LIMIT ?",
                new String[]{Long.toString(afterRowid), Integer.toString(BATCH)})) {
            while (c.moveToNext()) {
                long rid = c.getLong(0);
                String date = c.getString(1);
                String minute = c.getString(2);
                try {
                    long millis = LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(minute))
                            .atZone(zone).toInstant().toEpochMilli();
                    db.execSQL("UPDATE alphaESSTransformedData SET millisSinceEpoch = ? WHERE rowid = ?",
                            new Object[]{millis, rid});
                } catch (Exception rowError) {
                    // A malformed date/minute must not poison the whole migration — skip it (leaving its
                    // millis as-is) and advance, so the run still converges. Logged for diagnosis.
                    Log.w(TAG, "Skipping unparseable row rowid=" + rid + " date='" + date
                            + "' minute='" + minute + "': " + rowError.getMessage());
                }
                lastBatchRowid = rid;   // rows are ordered by rowid, so this ends as the batch max
                n++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return n;
    }

    private long count(SQLiteDatabase db, String sql, String[] args) {
        try (Cursor c = db.rawQuery(sql, args)) {
            return c.moveToFirst() ? c.getLong(0) : 0L;
        }
    }

    private long parseLong(String s) {
        if (s == null || s.isEmpty()) return 0L;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0L; }
    }

    private Data progress(long done, long total) {
        int pct = total > 0 ? (int) Math.min(100, (done * 100) / total) : 0;
        return new Data.Builder()
                .putInt(PROGRESS_PCT, pct)
                .putLong(PROGRESS_DONE, done)
                .putLong(PROGRESS_TOTAL, total)
                .build();
    }

    /** Defensively (re)create the shared progress channel — idempotent; safe before MainActivity has run. */
    private void ensureChannel(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.channel_description));
        nm.createNotificationChannel(channel);
    }

    private void notify(NotificationManagerCompat manager, NotificationCompat.Builder builder) {
        if (ActivityCompat.checkSelfPermission(getApplicationContext(),
                android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        manager.notify(NOTIFICATION_ID, builder.build());
    }
}
