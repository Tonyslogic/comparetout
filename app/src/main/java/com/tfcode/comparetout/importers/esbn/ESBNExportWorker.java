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

package com.tfcode.comparetout.importers.esbn;

import static android.content.Context.NOTIFICATION_SERVICE;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.importers.InverterDateRange;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.ui2.UI2NotificationLaunch;
import com.tfcode.comparetout.util.ContractFileUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ESBN per-MPRN export. Mirrors {@link com.tfcode.comparetout.importers.alphaess.ExportWorker}
 * but writes ESBN-HDF compatible CSV (the same shape {@link ESBNHDFClient}
 * parses on import) so the file round-trips through the existing
 * {@link ESBNImportWorker}. One file per MPRN, one row per (timestamp ×
 * import/export) — values written with the {@code (kWh)} suffix so the
 * importer treats them as already-kWh and skips its half-on-non-kWh rescale.
 */
public class ESBNExportWorker extends Worker {

    private static final String TAG = "ESBNExportWorker";
    private static final int NOTIFICATION_ID = 4;

    public static final String KEY_SYSTEM_SN = "KEY_SYSTEM_SN";
    public static final String KEY_FOLDER = "KEY_FOLDER";
    public static final String PROGRESS = "PROGRESS";

    // The importer reads cols 0/2/3/4 — col 1 (meter serial) is unused but we
    // still emit it so the column count matches a real ESBN-supplied HDF.
    private static final String CSV_HEADER =
            "\"MPRN\",\"Meter Serial Number\",\"Read Value\",\"Read Type\",\"Read Date and End Time\"";
    private static final String IMPORT_TYPE = "Active Import (kWh)";
    private static final String EXPORT_TYPE = "Active Export (kWh)";

    private static final DateTimeFormatter HDF_DT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final DateTimeFormatter DB_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DB_MIN =
            DateTimeFormatter.ofPattern("HH:mm");

    private final ToutcRepository mToutcRepository;
    private final NotificationManager mNotificationManager;
    private final Context mContext;
    private boolean mStopped = false;
    private boolean mUseUI2 = false;
    private String mSelectedSysSn = null;
    // The export loop emits a progress milestone every 30 days. On an MPRN
    // with thousands of mostly-empty days that can fire 50+ updates inside
    // a few tens of ms, and Android's notification system coalesces /
    // drops anything past ~5/sec — leaving the visible notification stuck
    // a step or two behind the worker's true position. Throttle the
    // in-loop notifies, but always force-deliver the terminal one.
    private long mLastNotifyAt = 0L;
    private static final long MIN_NOTIFY_INTERVAL_MS = 250L;

    public ESBNExportWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        mContext = context;
        mToutcRepository = new ToutcRepository((Application) context);
        mNotificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        createChannel();
        setProgressAsync(new Data.Builder().putString(PROGRESS, "Starting export").build());
    }

    @Override
    public void onStopped() {
        super.onStopped();
        mStopped = true;
    }

    @NonNull
    @Override
    public Result doWork() {
        Data input = getInputData();
        String mprn = input.getString(KEY_SYSTEM_SN);
        String folder = input.getString(KEY_FOLDER);
        if (mprn == null || folder == null) {
            Log.w(TAG, "Missing MPRN or folder; nothing to do");
            return Result.failure();
        }
        Uri folderUri = Uri.parse(folder);
        mSelectedSysSn = mprn;
        mUseUI2 = UI2NotificationLaunch.isUI2Enabled(getApplicationContext());

        publishProgress("Reading ESBN data", true);

        InverterDateRange range = mToutcRepository.getDateRange(mprn);
        if (range == null || range.startDate == null || range.startDate.isEmpty()
                || range.finishDate == null || range.finishDate.isEmpty()) {
            publishProgress("No data to export for " + mprn, true);
            return Result.success();
        }

        String fileName = mprn + ".csv";
        ContentResolver resolver = mContext.getContentResolver();
        OutputStream out = null;
        OutputStreamWriter writer = null;
        try {
            Uri parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
                    folderUri, DocumentsContract.getTreeDocumentId(folderUri));
            Uri destUri = DocumentsContract.createDocument(resolver, parentDocUri, "text/csv", fileName);
            if (destUri == null) {
                Log.e(TAG, "Failed to create destination file");
                publishProgress("Export abandoned: could not create file", true);
                return Result.failure();
            }
            out = resolver.openOutputStream(destUri);
            if (out == null) {
                Log.e(TAG, "Could not open output stream");
                return Result.failure();
            }
            writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            writer.write(CSV_HEADER);
            writer.write("\n");

            LocalDate start = LocalDate.parse(range.startDate, DB_DATE);
            LocalDate end = LocalDate.parse(range.finishDate, DB_DATE);
            long totalDays = end.toEpochDay() - start.toEpochDay() + 1L;
            long processed = 0L;
            LocalDate cursor = start;
            while (!cursor.isAfter(end) && !mStopped) {
                String day = cursor.format(DB_DATE);
                List<AlphaESSTransformedData> rows =
                        mToutcRepository.getAlphaESSTransformedData(mprn, day, day);
                for (AlphaESSTransformedData row : rows) {
                    if (mStopped) break;
                    LocalDateTime ts = LocalDateTime.of(
                            LocalDate.parse(row.getDate(), DB_DATE),
                            java.time.LocalTime.parse(row.getMinute(), DB_MIN));
                    String dt = ts.format(HDF_DT);
                    // Buy → import line. Feed → export line. The importer is fine
                    // with zero-valued rows so we round-trip the source schema
                    // (and timestamps for which only one side is non-zero in
                    // real HDFs would otherwise lose presence on re-import).
                    writeRow(writer, mprn, row.getBuy(), IMPORT_TYPE, dt);
                    writeRow(writer, mprn, row.getFeed(), EXPORT_TYPE, dt);
                }
                processed++;
                if ((processed % 30) == 0 || processed == totalDays) {
                    // Throttled — the terminal completion update below uses
                    // force=true so the user always sees the final state.
                    publishProgress("Exporting " + processed + "/" + totalDays + " days",
                            false);
                }
                cursor = cursor.plusDays(1);
            }
            writer.flush();

            String displayName = ContractFileUtils.getFileNameFromUri(mContext, destUri);
            publishProgress("Export complete: " + (displayName != null ? displayName : fileName),
                    true);
        } catch (IOException | SecurityException | IllegalArgumentException e) {
            Log.e(TAG, "ESBN export failed", e);
            publishProgress("Export abandoned: " + e.getClass().getSimpleName(), true);
            return Result.failure();
        } finally {
            closeQuietly(writer);
            closeQuietly(out);
        }

        if (mStopped) mNotificationManager.cancel(NOTIFICATION_ID);
        return Result.success();
    }

    /**
     * Publish the worker's progress to WorkManager + the notification
     * shade. NotificationManager.notify is thread-safe, so no main-thread
     * hop is required (the earlier handler.post approach raced the worker's
     * own completion). [force]=true bypasses the in-loop throttle and is
     * used for the first and last updates so terminal state always lands.
     */
    private void publishProgress(String progress, boolean force) {
        setProgressAsync(new Data.Builder().putString(PROGRESS, progress).build());
        long now = System.currentTimeMillis();
        if (force || now - mLastNotifyAt > MIN_NOTIFY_INTERVAL_MS) {
            mLastNotifyAt = now;
            mNotificationManager.notify(NOTIFICATION_ID, getNotification(progress));
        }
    }

    private static void writeRow(OutputStreamWriter w, String mprn,
                                  double value, String type, String dt) throws IOException {
        w.write("\"" + mprn + "\",\"\",\"" + value + "\",\"" + type + "\",\"" + dt + "\"\n");
    }

    private static void closeQuietly(AutoCloseable c) {
        if (c == null) return;
        try { c.close(); } catch (Exception ignored) { /* best effort */ }
    }

    @NonNull
    private Notification getNotification(@NonNull String progress) {
        Context ctx = getApplicationContext();
        String id = ctx.getString(R.string.esbn_channel_id);
        String title = ctx.getString(R.string.esbn_export_notification_title);
        String cancel = ctx.getString(R.string.cancel_fetch_alpha);
        PendingIntent cancelPI = WorkManager.getInstance(ctx).createCancelPendingIntent(getId());
        PendingIntent contentPI = UI2NotificationLaunch.contentIntent(
                ctx, mUseUI2, ComparisonUIViewModel.Importer.ESBNHDF,
                mSelectedSysSn, ImportESBNActivity.class);
        return new NotificationCompat.Builder(ctx, id)
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(progress)
                .setSmallIcon(R.drawable.ic_baseline_save_24)
                .setOngoing(true)
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(contentPI)
                .addAction(android.R.drawable.ic_delete, cancel, cancelPI)
                .build();
    }

    private void createChannel() {
        CharSequence name = getApplicationContext().getString(R.string.esbn_channel_name);
        String description = getApplicationContext().getString(R.string.channel_description);
        NotificationChannel channel = new NotificationChannel(
                getApplicationContext().getString(R.string.esbn_channel_id),
                name, NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(description);
        NotificationManager nm = getApplicationContext().getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel);
    }
}
