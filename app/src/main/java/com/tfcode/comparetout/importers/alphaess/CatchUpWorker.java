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

package com.tfcode.comparetout.importers.alphaess;

import static android.content.Context.NOTIFICATION_SERVICE;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.importers.alphaess.responses.GetOneDayEnergyResponse;
import com.tfcode.comparetout.importers.alphaess.responses.GetOneDayPowerResponse;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.ui2.UserTimezoneStore;

import java.time.ZoneId;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawEnergy;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawPower;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.ui2.UI2NotificationLaunch;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@SuppressWarnings("BusyWait")
public class CatchUpWorker extends Worker {

    private static final String TAG = "AlphaESSImporter";

    private final ToutcRepository mToutcRepository;
    private final NotificationManager mNotificationManager;
    // Distinct notification slot per worker class — see
    // plans/eventual-bouncing-hare.md.
    private static final int mNotificationId = 6;
    private boolean mStopped = false;
    private boolean mUseUI2 = false;
    private String mSelectedSysSn = null;
    private long mLastNotifyAt = 0L;
    private static final long MIN_NOTIFY_INTERVAL_MS = 250L;
    // AlphaESS OpenAPI returns 6053 (rate limit) under tight loops. The
    // Python repo's README documents 10s minimum polling interval; 5s here
    // is a pragmatic middle ground that nearly eliminates the rate-limit
    // skips while keeping a year-long backfill under an hour.
    private static final long SAMPLE_LIMIT_MILLIS = 5000L;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static final String KEY_SYSTEM_SN = "KEY_SYSTEM_SN";
    public static final String KEY_APP_ID = "KEY_APP_ID";
    public static final String KEY_APP_SECRET = "KEY_APP_SECRET";
    public static final String KEY_START_DATE = "KEY_START_DATE";

    public static final String PROGRESS = "PROGRESS";

    public CatchUpWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
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
        Log.i(TAG, "CatchUpWorker:doWork invoked");
        Data inputData = getInputData();
        OpenAlphaESSClient mOpenAlphaESSClient = new OpenAlphaESSClient(
                inputData.getString(KEY_APP_ID),
                inputData.getString(KEY_APP_SECRET));
        String startDate = inputData.getString(KEY_START_DATE);
        String systemSN = inputData.getString(KEY_SYSTEM_SN);
        mOpenAlphaESSClient.setSerial(systemSN);
        mSelectedSysSn = systemSN;
        mUseUI2 = UI2NotificationLaunch.isUI2Enabled(getApplicationContext());

        LocalDate current = LocalDate.parse(startDate, DATE_FORMAT);
        LocalDate end = LocalDate.now();

        // v2: remember whether this SN had any processed rows before we started. If it did,
        // some of those rows may be pre-v2 and we shouldn't stamp v2 at the end (the user
        // still needs the Migrate button to upgrade the historical rows).
        String latestBefore = mToutcRepository.getLatestDateForSn(systemSN);
        boolean snHadNoRowsBefore = (latestBefore == null || latestBefore.isEmpty());

        publishProgress(current.toString(), true);
        while (current.isBefore(end) && !mStopped) {
            if (mToutcRepository.checkSysSnForDataOnDate(systemSN, current.format(DATE_FORMAT))) {
                Log.i(TAG, "CatchUpWorker skipping " + current);
                current = current.plusDays(1);
                continue;
            }
            else try {
                Log.i(TAG, "CatchupWorker fetching data for " + current);
                // Get the data from AlphaESS (a) power, (b) energy
                GetOneDayPowerResponse oneDayPowerBySn = mOpenAlphaESSClient.getOneDayPowerBySn(current.format(DATE_FORMAT));
                GetOneDayEnergyResponse oneDayEnergyBySn = mOpenAlphaESSClient.getOneDayEnergyBySn(current.format(DATE_FORMAT));

                if (!(null == oneDayPowerBySn) && !(null == oneDayEnergyBySn) && !(null == oneDayPowerBySn.data)) {
                    // Interpret/stamp source timestamps in the saved zone (Phase 1, timezone-and-rollout.md).
                    ZoneId zone = UserTimezoneStore.resolvedZone(getApplicationContext());
                    // Fix the power-data (5 minute alignment and missing entries)
                    List<DataMassager.DataPoint> points = DataMassager.getDataPointsForPowerResponse(oneDayPowerBySn, zone);
                    Map<Long, FiveMinuteEnergies> fixed = DataMassager.oneDayDataInFiveMinuteIntervals(points, zone);
                    // Get the total load (ePV - eOutput) + eInput
                    double ePV = oneDayEnergyBySn.data.epv;
                    double eLoad = (ePV - oneDayEnergyBySn.data.eOutput) + oneDayEnergyBySn.data.eInput;
                    double eFeed = oneDayEnergyBySn.data.eOutput;
                    double eBuy = oneDayEnergyBySn.data.eInput;
                    // Unitize and scale power (in kWh 5 minute intervals)
                    Map<Long, FiveMinuteEnergies> massaged = DataMassager.massage(fixed, ePV, eLoad, eFeed, eBuy, zone);
                    Log.i(TAG, "CatchupWorker storing data for " + current);
                    // Store raw energy
                    AlphaESSRawEnergy energyEntity = AlphaESSEntityUtil.getEnergyRowFromJson(oneDayEnergyBySn);
                    mToutcRepository.addRawEnergy(energyEntity);
                    // Store raw power
                    List<AlphaESSRawPower> powerEntityList = AlphaESSEntityUtil.getPowerRowsFromJson(oneDayPowerBySn);
                    mToutcRepository.addRawPower(powerEntityList);
                    // v2: per-interval EV charger kWh (scaled to daily total), used by the new transform.
                    Map<Long, Double> evByInterval = DataMassager.evIn5MinIntervals(powerEntityList, oneDayEnergyBySn.data.eChargingPile, zone);
                    // Store transformed data
                    List<AlphaESSTransformedData> normalizedEntityList = AlphaESSEntityUtil.getTransformedDataRows(massaged, evByInterval, systemSN, zone);
                    mToutcRepository.addTransformedData(normalizedEntityList);
                    Log.i(TAG, "CatchupWorker storing normalizedEntityList " + normalizedEntityList.size());

                    // Sleep to avoid API limits
                    Thread.sleep(SAMPLE_LIMIT_MILLIS);
                }
                else {
                    Log.w(TAG, "CatchupWorker got null data for " + current);
                    Thread.sleep(SAMPLE_LIMIT_MILLIS);
                    continue;
                }
            } catch (AlphaESSException e) {
                // check to see if we are exceeding limits and retry
                e.printStackTrace();
                Log.w(TAG, "CatchupWorker got an error for " + current, e);
                if (!(null == e.getMessage()) && e.getMessage().startsWith("err.code=6053"))
                    continue;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Log.i(TAG, "CatchUpWorker finished with " + current);
            publishProgress("Done catching up with " + current, false);
            current = current.plusDays(1);
        }
        // v2: only stamp if the SN started empty (so all our rows are v2) or was already v2.
        // Otherwise we may have left v1 historical rows untouched and the Migrate button
        // should keep surfacing.
        mToutcRepository.stampAlphaESSTransformCurrentIfSafe(systemSN, snHadNoRowsBefore);

        if (mStopped) mNotificationManager.cancel(mNotificationId);

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

    @NonNull
    private Notification getNotification(@NonNull String progress) {
        // Build a notification using bytesRead and contentLength

        Context context = getApplicationContext();
        String id = context.getString(R.string.alphaess_channel_id);
        String title = context.getString(R.string.fetch_alpha_notification_title);
        String cancel = context.getString(R.string.cancel_fetch_alpha);
        // This PendingIntent can be used to cancel the worker
        PendingIntent intent = WorkManager.getInstance(context)
                .createCancelPendingIntent(getId());

        PendingIntent activityPendingIntent = UI2NotificationLaunch.contentIntent(
                context, mUseUI2, ComparisonUIViewModel.Importer.ALPHAESS,
                mSelectedSysSn, ImportAlphaActivity.class);

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
        CharSequence name = getApplicationContext().getString(R.string.alphaess_channel_name);
        String description = getApplicationContext().getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(
                getApplicationContext().getString(R.string.alphaess_channel_id), name, importance);
        channel.setDescription(description);
        // Register the channel with the system. You can't change the importance
        // or other notification behaviors after this.
        NotificationManager notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }
}
