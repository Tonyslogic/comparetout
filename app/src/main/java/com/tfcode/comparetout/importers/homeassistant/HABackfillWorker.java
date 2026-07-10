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

package com.tfcode.comparetout.importers.homeassistant;

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
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.importers.homeassistant.messages.GetStatisticsMetadataRequest;
import com.tfcode.comparetout.importers.homeassistant.messages.GetStatisticsMetadataResult;
import com.tfcode.comparetout.importers.homeassistant.messages.HAMessage;
import com.tfcode.comparetout.importers.homeassistant.messages.HAMessageWithID;
import com.tfcode.comparetout.importers.homeassistant.messages.ImportStatisticsRequest;
import com.tfcode.comparetout.importers.homeassistant.messages.ImportStatisticsResult;
import com.tfcode.comparetout.importers.homeassistant.messages.RepairStatForTimeRequest;
import com.tfcode.comparetout.importers.homeassistant.messages.RepairStatForTimeResult;
import com.tfcode.comparetout.importers.homeassistant.messages.StatsForPeriodRequest;
import com.tfcode.comparetout.importers.homeassistant.messages.authorization.AuthInvalid;
import com.tfcode.comparetout.importers.homeassistant.messages.authorization.AuthOK;
import com.tfcode.comparetout.importers.homeassistant.messages.statsForPeriodResult.SensorData;
import com.tfcode.comparetout.importers.homeassistant.messages.statsForPeriodResult.StatsForPeriodResult;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.ui2.UI2NotificationLaunch;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToDoubleFunction;
import java.util.logging.Logger;

/**
 * Push a locally stored source series (AlphaESS/ESBN/HA history in {@code
 * alphaESSTransformedData}) into Home Assistant as hourly long-term statistics
 * (plans/ha/design.md, Enhancement 2).
 * <p>
 * Two targets, chosen by the user in the UI2 backfill sheet:
 * <ul>
 *   <li><b>Real entity statistics</b> (default goal: fix incomplete/missed HA data) — writes to
 *   the user's own energy-dashboard sensor ids with {@code source: "recorder"}. Sums are
 *   anchored to the last stored total before the range, and after the import every later total
 *   is shifted by the net kWh delta via {@code adjust_sum_statistics} so history stays
 *   consistent.</li>
 *   <li><b>App-owned external statistics</b> ({@code comparetout:*}) — non-destructive
 *   side-by-side series.</li>
 * </ul>
 * Imports are chunked per month and idempotent (points overwrite by id+hour; the post-range
 * adjustment re-computes from live HA state, so a re-run adjusts by zero). A connection loss
 * surfaces as {@link Result#retry()}, bounded by {@link #MAX_RUN_ATTEMPTS}.
 */
public class HABackfillWorker extends Worker {
    private static final Logger LOGGER = Logger.getLogger(HABackfillWorker.class.getName());

    public static final String KEY_HOST = "KEY_HOST";
    public static final String KEY_TOKEN = "KEY_TOKEN";
    public static final String KEY_SOURCE_SYS_SN = "KEY_SOURCE_SYS_SN";
    public static final String KEY_FROM = "KEY_FROM"; // yyyy-MM-dd (inclusive)
    public static final String KEY_TO = "KEY_TO";     // yyyy-MM-dd (inclusive)
    public static final String KEY_TARGET_EXTERNAL = "KEY_TARGET_EXTERNAL";
    public static final String KEY_SENSORS = "KEY_SENSORS"; // EnergySensors JSON (real-entity mapping)
    public static final String KEY_SERIES = "KEY_SERIES";   // csv of: buy,feed,pv,charge,discharge
    public static final String KEY_HOUR = "KEY_HOUR";       // epoch-millis hour start; -1 = whole range
    public static final String PROGRESS = "PROGRESS";

    public static final String EXTERNAL_SOURCE = "comparetout";

    // Distinct notification slot per worker class — see plans/eventual-bouncing-hare.md.
    private static final int mNotificationId = 15;
    private static final int MAX_RUN_ATTEMPTS = 3;
    private static final long REQUEST_TIMEOUT_S = 60;
    private static final long HOUR_MILLIS = 3_600_000L;
    // Sum-anchoring windows around the backfill range: lookback finds the pre-range total,
    // lookahead finds the first post-range point for the consistency adjustment. A post-range
    // hole longer than the lookahead skips the adjustment (nothing near enough to shift).
    private static final int ANCHOR_LOOKBACK_DAYS = 30;
    private static final int POST_RANGE_SCAN_DAYS = 90;

    private final Context mContext;
    private final ToutcRepository mToutcRepository;
    private final NotificationManager mNotificationManager;
    private boolean mStopped = false;
    private boolean mUseUI2 = false;
    private long mLastNotifyAt = 0L;
    private static final long MIN_NOTIFY_INTERVAL_MS = 250L;

    private HADispatcher mClient;
    private volatile boolean mConnectionLost = false;
    private final Object mAuthLock = new Object();
    private Boolean mAuthResult = null; // null = pending, true = ok, false = invalid

    public HABackfillWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mContext = context;
        mToutcRepository = new ToutcRepository((Application) context);
        mNotificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        createChannel();
    }

    @Override
    public void onStopped() {
        super.onStopped();
        mStopped = true;
    }

    /** One series to push: local column → HA statistic id. */
    private static class SeriesTarget {
        final String seriesKey;
        final String statisticId;
        final String displayName;
        final ToDoubleFunction<AlphaESSTransformedData> extractor;

        SeriesTarget(String seriesKey, String statisticId, String displayName,
                     ToDoubleFunction<AlphaESSTransformedData> extractor) {
            this.seriesKey = seriesKey;
            this.statisticId = statisticId;
            this.displayName = displayName;
            this.extractor = extractor;
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        Data inputData = getInputData();
        String host = inputData.getString(KEY_HOST);
        String token = inputData.getString(KEY_TOKEN);
        String sourceSysSn = inputData.getString(KEY_SOURCE_SYS_SN);
        String from = inputData.getString(KEY_FROM);
        String to = inputData.getString(KEY_TO);
        boolean external = inputData.getBoolean(KEY_TARGET_EXTERNAL, true);
        String seriesCsv = inputData.getString(KEY_SERIES);
        String sensorsJson = inputData.getString(KEY_SENSORS);
        long hourStart = inputData.getLong(KEY_HOUR, -1L);
        mUseUI2 = UI2NotificationLaunch.isUI2Enabled(getApplicationContext());

        if (null == host || null == token || null == sourceSysSn || null == from || null == to
                || null == seriesCsv || seriesCsv.isEmpty()) {
            return Result.failure();
        }
        EnergySensors sensors = null;
        if (!(null == sensorsJson)) {
            try {
                sensors = new Gson().fromJson(sensorsJson, new TypeToken<EnergySensors>() {}.getType());
            } catch (Exception ignored) {
            }
        }
        List<SeriesTarget> targets = buildTargets(seriesCsv, external, sensors);
        if (targets.isEmpty()) {
            publishProgress("Nothing to backfill (no matching series)", true);
            return Result.failure();
        }

        publishProgress("Loading " + sourceSysSn + " data", true);
        List<AlphaESSTransformedData> rows =
                mToutcRepository.getAlphaESSTransformedData(sourceSysSn, from, to);
        if (rows.isEmpty()) {
            publishProgress("No source data in the selected range", true);
            return Result.failure();
        }

        try {
            mClient = connectAndAuthenticate(host, token);
            if (null == mClient) {
                if (Boolean.FALSE.equals(mAuthResult)) {
                    publishProgress("HomeAssistant authentication failed", true);
                    return Result.failure();
                }
                return retryOrFail("HomeAssistant unreachable");
            }

            // Unit alignment (OQ-6): sums must be written in each target statistic's own unit.
            Map<String, String> unitById = fetchUnits(targets);

            long rangeStartMillis = LocalDate.parse(from).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
            long rangeEndMillis = LocalDate.parse(to).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
            if (hourStart >= 0) {
                // Hour-scoped repair (the wizard's drill-down): write only this
                // hour's point; sum anchoring/adjustment logic is unchanged.
                rangeStartMillis = hourStart;
                rangeEndMillis = hourStart + HOUR_MILLIS;
            }

            for (SeriesTarget target : targets) {
                if (mStopped) break;
                String unit = unitById.getOrDefault(target.statisticId, "kWh");
                double scale = scaleFromKwh(unit);
                if (scale <= 0) {
                    LOGGER.warning("HABackfillWorker: unsupported unit '" + unit
                            + "' for " + target.statisticId + ", skipping");
                    continue;
                }
                backfillSeries(target, rows, rangeStartMillis, rangeEndMillis, unit, scale, external);
            }

            if (mStopped) {
                publishProgress("Backfill cancelled", true);
                return Result.success();
            }
            if (!external) {
                // The repaired sensor statistics are the source of truth for the
                // local "HomeAssistant" rows — resync the backfilled range so the
                // graphs and Compare reflect the corrected values without a manual
                // re-fetch (ingestion REPLACEs by (sysSn, date, minute)).
                Data resyncInput = new Data.Builder()
                        .putString(HACatchupWorker.KEY_HOST, host)
                        .putString(HACatchupWorker.KEY_TOKEN, token)
                        .putString(HACatchupWorker.KEY_SENSORS, sensorsJson)
                        .putString(HACatchupWorker.KEY_START_DATE, from)
                        .build();
                OneTimeWorkRequest resync = new OneTimeWorkRequest.Builder(HACatchupWorker.class)
                        .setInputData(resyncInput)
                        .addTag("HomeAssistant")
                        .build();
                WorkManager.getInstance(mContext).beginUniqueWork(
                        "HomeAssistant", ExistingWorkPolicy.APPEND_OR_REPLACE, resync).enqueue();
                publishProgress("Backfill complete — resyncing from Home Assistant", true);
            } else {
                publishProgress("Backfill complete", true);
            }
            return Result.success();
        } catch (BackfillException e) {
            LOGGER.warning("HABackfillWorker: " + e.getMessage());
            if (e.retryable && !mStopped) return retryOrFail(e.getMessage());
            publishProgress("Backfill failed: " + e.getMessage(), true);
            return Result.failure();
        } finally {
            if (!(null == mClient)) {
                try {
                    mClient.stop();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private Result retryOrFail(String reason) {
        if (getRunAttemptCount() < MAX_RUN_ATTEMPTS) {
            publishProgress(reason + " — will retry", true);
            return Result.retry();
        }
        publishProgress(reason + " — giving up", true);
        return Result.failure();
    }

    private List<SeriesTarget> buildTargets(String seriesCsv, boolean external, EnergySensors sensors) {
        List<SeriesTarget> targets = new ArrayList<>();
        for (String key : seriesCsv.split(",")) {
            String series = key.trim();
            ToDoubleFunction<AlphaESSTransformedData> extractor;
            String externalId;
            String name;
            switch (series) {
                case "buy":
                    extractor = AlphaESSTransformedData::getBuy;
                    externalId = EXTERNAL_SOURCE + ":ha_grid_import";
                    name = "Grid import (Eco Power Optimiser)";
                    break;
                case "feed":
                    extractor = AlphaESSTransformedData::getFeed;
                    externalId = EXTERNAL_SOURCE + ":ha_grid_export";
                    name = "Grid export (Eco Power Optimiser)";
                    break;
                case "pv":
                    extractor = AlphaESSTransformedData::getPv;
                    externalId = EXTERNAL_SOURCE + ":ha_solar";
                    name = "Solar generation (Eco Power Optimiser)";
                    break;
                case "charge":
                    extractor = r -> Math.max(0, r.getCharge());
                    externalId = EXTERNAL_SOURCE + ":ha_battery_charge";
                    name = "Battery charge (Eco Power Optimiser)";
                    break;
                case "discharge":
                    extractor = r -> Math.max(0, -r.getCharge());
                    externalId = EXTERNAL_SOURCE + ":ha_battery_discharge";
                    name = "Battery discharge (Eco Power Optimiser)";
                    break;
                default:
                    continue;
            }
            if (external) {
                targets.add(new SeriesTarget(series, externalId, name, extractor));
            } else {
                String realId = realEntityIdFor(series, sensors);
                if (!(null == realId) && !realId.isEmpty()) {
                    targets.add(new SeriesTarget(series, realId, name, extractor));
                }
            }
        }
        return targets;
    }

    /** Map a series key onto the user's configured energy-dashboard sensor id. */
    private String realEntityIdFor(String series, EnergySensors sensors) {
        if (null == sensors) return null;
        switch (series) {
            case "buy":
                return (null == sensors.gridImports || sensors.gridImports.isEmpty())
                        ? null : sensors.gridImports.get(0);
            case "feed":
                return (null == sensors.gridExports || sensors.gridExports.isEmpty())
                        ? null : sensors.gridExports.get(0);
            case "pv":
                return (null == sensors.solarGeneration || sensors.solarGeneration.isEmpty())
                        ? null : sensors.solarGeneration.get(0);
            case "charge":
                return (null == sensors.batteries || sensors.batteries.isEmpty())
                        ? null : sensors.batteries.get(0).batteryCharging;
            case "discharge":
                return (null == sensors.batteries || sensors.batteries.isEmpty())
                        ? null : sensors.batteries.get(0).batteryDischarging;
            default:
                return null;
        }
    }

    // ------------------------------------------------------------------
    // Per-series backfill
    // ------------------------------------------------------------------

    private void backfillSeries(SeriesTarget target, List<AlphaESSTransformedData> rows,
            long rangeStartMillis, long rangeEndMillis, String unit, double scale,
            boolean external) throws BackfillException {

        // Hour-aligned UTC buckets of the source series (kWh per hour).
        TreeMap<Long, Double> hourly = new TreeMap<>();
        for (AlphaESSTransformedData row : rows) {
            Long millis = row.getMillisSinceEpoch();
            if (null == millis) continue;
            double kwh = target.extractor.applyAsDouble(row);
            if (kwh <= 0) continue;
            long hourStart = millis - (millis % HOUR_MILLIS);
            hourly.merge(hourStart, kwh, Double::sum);
        }
        // The write window may be narrower than the loaded rows (hour-scoped repair).
        hourly = new TreeMap<>(hourly.subMap(rangeStartMillis, rangeEndMillis));
        if (rangeEndMillis - rangeStartMillis == HOUR_MILLIS && hourly.isEmpty()) {
            // An hour-scoped repair must be able to zero out a spurious HA value
            // even when the source recorded no energy — write the flat sum point.
            hourly.put(rangeStartMillis, 0D);
        }
        if (hourly.isEmpty()) {
            LOGGER.info("HABackfillWorker: no " + target.seriesKey + " energy in range, skipping");
            return;
        }

        // Existing sums around the range: anchor before, last-in-range and first point after.
        TreeMap<Long, Double> existingSums = fetchSums(target.statisticId,
                rangeStartMillis - TimeUnit.DAYS.toMillis(ANCHOR_LOOKBACK_DAYS),
                rangeEndMillis + TimeUnit.DAYS.toMillis(POST_RANGE_SCAN_DAYS));
        Map.Entry<Long, Double> anchorEntry = existingSums.lowerEntry(rangeStartMillis);
        double anchor = (null == anchorEntry) ? 0D : anchorEntry.getValue();
        Map.Entry<Long, Double> lastInRange = existingSums.lowerEntry(rangeEndMillis);
        double oldDelta = (null == lastInRange || lastInRange.getKey() < rangeStartMillis)
                ? 0D : lastInRange.getValue() - anchor;
        Map.Entry<Long, Double> firstAfter = existingSums.ceilingEntry(rangeEndMillis);

        ImportStatisticsRequest.Metadata metadata = new ImportStatisticsRequest.Metadata(
                target.statisticId,
                external ? EXTERNAL_SOURCE : "recorder",
                target.displayName,
                unit,
                "energy");

        // Month-chunked import of monotonic cumulative sums (in the statistic's own unit).
        double running = anchor;
        YearMonth currentMonth = null;
        List<ImportStatisticsRequest.StatisticPoint> chunk = new ArrayList<>();
        for (Map.Entry<Long, Double> hour : hourly.entrySet()) {
            if (mStopped) return;
            YearMonth month = YearMonth.from(
                    Instant.ofEpochMilli(hour.getKey()).atZone(ZoneOffset.UTC).toLocalDate());
            if (!month.equals(currentMonth)) {
                sendChunk(target, metadata, chunk, currentMonth);
                currentMonth = month;
            }
            running += hour.getValue() * scale;
            chunk.add(new ImportStatisticsRequest.StatisticPoint(hour.getKey(), running, null));
        }
        sendChunk(target, metadata, chunk, currentMonth);

        // Overwriting a mid-history range changes the range's total; shift every later stored
        // sum by the net delta so post-range history stays consistent (real entities only —
        // a fresh external series has nothing after the range on first run, and re-runs
        // recompute a zero adjustment either way).
        if (!(null == firstAfter)) {
            double newDelta = running - anchor;
            double adjustment = newDelta - oldDelta;
            if (Math.abs(adjustment) > 1e-6) {
                RepairStatForTimeRequest repair = new RepairStatForTimeRequest(
                        target.statisticId, firstAfter.getKey(), adjustment, unit);
                repair.setId(mClient.generateId());
                HAMessageWithID result = syncRequest(repair, RepairStatForTimeResult.class);
                if (!result.isSuccess()) {
                    throw new BackfillException("adjust_sum_statistics failed for "
                            + target.statisticId + ": " + result.getErrorDescription(), false);
                }
            }
        }
    }

    private void sendChunk(SeriesTarget target, ImportStatisticsRequest.Metadata metadata,
            List<ImportStatisticsRequest.StatisticPoint> chunk, YearMonth month)
            throws BackfillException {
        if (chunk.isEmpty()) return;
        ImportStatisticsRequest request =
                new ImportStatisticsRequest(metadata, chunk, mClient.generateId());
        HAMessageWithID result = syncRequest(request, ImportStatisticsResult.class);
        if (!result.isSuccess()) {
            throw new BackfillException("import_statistics failed for " + target.statisticId
                    + ": " + result.getErrorDescription(), false);
        }
        publishProgress("Backfilled " + target.seriesKey
                + ((null == month) ? "" : " " + month), false);
        chunk.clear();
    }

    private Map<String, String> fetchUnits(List<SeriesTarget> targets) throws BackfillException {
        List<String> ids = new ArrayList<>();
        for (SeriesTarget target : targets) ids.add(target.statisticId);
        GetStatisticsMetadataRequest request =
                new GetStatisticsMetadataRequest(ids, mClient.generateId());
        GetStatisticsMetadataResult result =
                (GetStatisticsMetadataResult) syncRequest(request, GetStatisticsMetadataResult.class);
        Map<String, String> units = new java.util.HashMap<>();
        if (result.isSuccess() && !(null == result.getResult())) {
            for (GetStatisticsMetadataResult.StatisticMetadata meta : result.getResult()) {
                if (!(null == meta.getStatisticId()) && !(null == meta.getUnitOfMeasurement())) {
                    units.put(meta.getStatisticId(), meta.getUnitOfMeasurement());
                }
            }
        }
        return units; // missing ids (fresh external series) default to kWh at the caller
    }

    /** Multiplier converting a source kWh value into the target unit; ≤0 marks unsupported. */
    private static double scaleFromKwh(String unit) {
        if (null == unit) return 1D;
        switch (unit) {
            case "kWh":
                return 1D;
            case "Wh":
                return 1000D;
            case "MWh":
                return 0.001D;
            default:
                return -1D;
        }
    }

    private TreeMap<Long, Double> fetchSums(String statisticId, long fromMillis, long toMillis)
            throws BackfillException {
        StatsForPeriodRequest request =
                new StatsForPeriodRequest(Collections.singletonList(statisticId));
        request.requestSums();
        request.useNativeUnits();
        request.setStartAndEndUtc(Instant.ofEpochMilli(fromMillis), Instant.ofEpochMilli(toMillis),
                mClient.generateId());
        StatsForPeriodResult result =
                (StatsForPeriodResult) syncRequest(request, StatsForPeriodResult.class);
        TreeMap<Long, Double> sums = new TreeMap<>();
        if (result.isSuccess() && !(null == result.getResult())) {
            List<SensorData> data = result.getResult().get(statisticId);
            if (!(null == data)) for (SensorData point : data) {
                if (!(null == point.getSum())) sums.put(point.getStart(), point.getSum());
            }
        }
        return sums;
    }

    // ------------------------------------------------------------------
    // Synchronous request plumbing over the async dispatcher
    // ------------------------------------------------------------------

    private static class BackfillException extends Exception {
        final boolean retryable;

        BackfillException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }
    }

    private HADispatcher connectAndAuthenticate(String host, String token) {
        CountDownLatch authLatch = new CountDownLatch(1);
        HADispatcher client = new HADispatcher(host, token);
        client.registerHandler("auth_ok", new MessageHandler<HAMessage>() {
            @Override
            public void handleMessage(HAMessage message) {
                client.setAuthorized(true);
                synchronized (mAuthLock) {
                    mAuthResult = true;
                }
                authLatch.countDown();
            }

            @Override
            public Class<? extends HAMessage> getMessageClass() {
                return AuthOK.class;
            }
        });
        client.registerHandler("auth_invalid", new MessageHandler<HAMessage>() {
            @Override
            public void handleMessage(HAMessage message) {
                client.setAuthorized(false);
                synchronized (mAuthLock) {
                    mAuthResult = false;
                }
                authLatch.countDown();
            }

            @Override
            public Class<? extends HAMessage> getMessageClass() {
                return AuthInvalid.class;
            }
        });
        client.setConnectionListener(reason -> {
            mConnectionLost = true;
            authLatch.countDown();
            releasePendingRequest();
        });
        client.start();
        try {
            if (!authLatch.await(30, TimeUnit.SECONDS)) return null;
        } catch (InterruptedException e) {
            return null;
        }
        return Boolean.TRUE.equals(mAuthResult) ? client : null;
    }

    private final AtomicReference<CountDownLatch> mPendingLatch = new AtomicReference<>();

    private void releasePendingRequest() {
        CountDownLatch latch = mPendingLatch.get();
        if (!(null == latch)) latch.countDown();
    }

    /** Send one id-bearing request and block for its result (or connection loss / timeout). */
    private HAMessageWithID syncRequest(HAMessageWithID request,
            Class<? extends HAMessageWithID> resultClass) throws BackfillException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<HAMessageWithID> resultRef = new AtomicReference<>();
        mPendingLatch.set(latch);
        mClient.sendMessage(request, new MessageHandler<HAMessage>() {
            @Override
            public void handleMessage(HAMessage message) {
                resultRef.set((HAMessageWithID) message);
                latch.countDown();
            }

            @Override
            public Class<? extends HAMessage> getMessageClass() {
                return resultClass;
            }
        });
        try {
            if (!latch.await(REQUEST_TIMEOUT_S, TimeUnit.SECONDS)) {
                throw new BackfillException("request timed out", true);
            }
        } catch (InterruptedException e) {
            throw new BackfillException("interrupted", false);
        } finally {
            mPendingLatch.set(null);
        }
        if (mConnectionLost || null == resultRef.get()) {
            throw new BackfillException("connection lost", true);
        }
        return resultRef.get();
    }

    // ------------------------------------------------------------------
    // Notifications (silent channel, cancel action — worker conventions)
    // ------------------------------------------------------------------

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
        String id = context.getString(R.string.ha_channel_id);
        String title = context.getString(R.string.backfill_ha_notification_title);
        String cancel = context.getString(R.string.cancel_fetch_alpha);
        PendingIntent intent = WorkManager.getInstance(context)
                .createCancelPendingIntent(getId());

        PendingIntent activityPendingIntent = UI2NotificationLaunch.contentIntent(
                context, mUseUI2, ComparisonUIViewModel.Importer.HOME_ASSISTANT,
                "HomeAssistant", ImportHomeAssistantActivity.class);

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
        CharSequence name = getApplicationContext().getString(R.string.ha_channel_name);
        String description = getApplicationContext().getString(R.string.channel_description);
        // DEFAULT (not LOW): builders silence via setSilent(true); a below-DEFAULT "silent" channel hides the status-bar icon
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(
                getApplicationContext().getString(R.string.ha_channel_id), name, importance);
        channel.setDescription(description);
        NotificationManager notificationManager =
                getApplicationContext().getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }
}
