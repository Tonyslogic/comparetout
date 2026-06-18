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
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
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
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class DailyWorker extends Worker {

    private static final String TAG = "AlphaESSImporter";

    private final ToutcRepository mToutcRepository;
    private final NotificationManager mNotificationManager;
    // Distinct notification slot per worker class — see
    // plans/eventual-bouncing-hare.md.
    private static final int mNotificationId = 7;
    // Separate slot for the "data not yet available" terminal notification
    // posted on the second consecutive empty-data miss.
    private static final int mNoDataNotificationId = 12;
    private boolean mStopped = false;
    private boolean mUseUI2 = false;
    private String mSelectedSysSn = null;
    private long mLastNotifyAt = 0L;
    private static final long MIN_NOTIFY_INTERVAL_MS = 250L;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static final String KEY_SYSTEM_SN = "KEY_SYSTEM_SN";
    public static final String KEY_APP_ID = "KEY_APP_ID";
    public static final String KEY_APP_SECRET = "KEY_APP_SECRET";
    // True when this run is the 1-hour delayed retry enqueued after a
    // first AlphaESSNoDataYetException miss. A second miss while this is
    // true gives up and notifies — no infinite retry.
    public static final String KEY_IS_RETRY = "KEY_IS_RETRY";

    public static final String PROGRESS = "PROGRESS";

    public DailyWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mToutcRepository = new ToutcRepository((Application) context);
        mNotificationManager = (NotificationManager)
                context.getSystemService(NOTIFICATION_SERVICE);
        createChannel();
        Log.i(TAG, "Daily worker created");
    }

    @Override
    public void onStopped(){
        super.onStopped();
        mStopped = true;
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "DailyWorker:doWork invoked");
        Data inputData = getInputData();
        OpenAlphaESSClient mOpenAlphaESSClient = new OpenAlphaESSClient(
                inputData.getString(KEY_APP_ID),
                inputData.getString(KEY_APP_SECRET));
        String systemSN = inputData.getString(KEY_SYSTEM_SN);
        mOpenAlphaESSClient.setSerial(systemSN);
        mSelectedSysSn = systemSN;
        mUseUI2 = UI2NotificationLaunch.isUI2Enabled(getApplicationContext());
        boolean isRetry = inputData.getBoolean(KEY_IS_RETRY, false);

        LocalDate yesterday = LocalDate.now().plusDays(-1);

        // v2: remember whether this SN had any processed rows before we started. We only
        // stamp v2 if it did NOT, so the Migrate button stays available when there is v1
        // historical data we haven't touched.
        String latestBefore = mToutcRepository.getLatestDateForSn(systemSN);
        boolean snHadNoRowsBefore = (latestBefore == null || latestBefore.isEmpty());

        if (mToutcRepository.checkSysSnForDataOnDate(systemSN, yesterday.format(DATE_FORMAT))) {
            Log.i(TAG, "DailyWorker skipping " + yesterday);
        }
        else try {
            boolean fetchOK = fetchFromOpenAlphaESS(mOpenAlphaESSClient, systemSN, yesterday);
            // Rumor has it that the 1st call to getOneDay* fails
            if (!fetchOK) fetchFromOpenAlphaESS(mOpenAlphaESSClient, systemSN, yesterday);
            Log.i(TAG, "DailyWorker finished with " + yesterday);
            publishProgress("Done catching up with " + yesterday, true);

        } catch (AlphaESSNoDataYetException e) {
            // The remote API returned code=200 with data=null — yesterday's data
            // hasn't been aggregated yet. First miss schedules a single 1-hour
            // delayed retry; second miss gives up and notifies.
            Log.w(TAG, "DailyWorker got empty data for " + yesterday + " (isRetry=" + isRetry + ")");
            if (!isRetry) {
                enqueueOneHourRetry(systemSN, inputData);
                publishProgress("No data yet for " + yesterday + "; retrying in 1 hour", true);
            } else {
                postNoDataNotification(systemSN, yesterday);
                publishProgress("No data yet for " + yesterday + "; will retry tomorrow", true);
            }
            if (mStopped) mNotificationManager.cancel(mNotificationId);
            return Result.success();
        } catch (AlphaESSException e) {
            // check to see if we are exceeding limits and retry
            e.printStackTrace();
            Log.w(TAG, "DailyWorker got an error for " + yesterday, e);
            if (!(null == e.getMessage()) && e.getMessage().startsWith("err.code=6053"))
                return Result.retry();
            else {
                String errorCode = "UNKNOWN";
                if (!(null == e.getMessage())) errorCode = e.getMessage().substring(0, 12);
                publishProgress("Unable to fetch " + yesterday + ", " + errorCode, true);
                return Result.failure();
            }

        }
        // v2 stamp — safe only when the SN had no historical rows before today's fetch,
        // or its meta is already v2.
        mToutcRepository.stampAlphaESSTransformCurrentIfSafe(systemSN, snHadNoRowsBefore);

        if (mStopped) mNotificationManager.cancel(mNotificationId);
        return Result.success();
    }

    // REPLACE the unique work so a fresh periodic run supersedes any
    // pending retry from the previous day.
    private void enqueueOneHourRetry(@NonNull String systemSN, @NonNull Data baseInput) {
        Data retryInput = new Data.Builder()
                .putString(KEY_SYSTEM_SN, baseInput.getString(KEY_SYSTEM_SN))
                .putString(KEY_APP_ID, baseInput.getString(KEY_APP_ID))
                .putString(KEY_APP_SECRET, baseInput.getString(KEY_APP_SECRET))
                .putBoolean(KEY_IS_RETRY, true)
                .build();
        OneTimeWorkRequest retry = new OneTimeWorkRequest.Builder(DailyWorker.class)
                .setInitialDelay(Duration.ofHours(1))
                .setInputData(retryInput)
                .addTag(systemSN + "daily-retry")
                .build();
        WorkManager.getInstance(getApplicationContext())
                .enqueueUniqueWork(systemSN + "daily-retry", ExistingWorkPolicy.REPLACE, retry);
        Log.i(TAG, "DailyWorker enqueued 1h empty-data retry for " + systemSN);
    }

    private void postNoDataNotification(@NonNull String systemSN, @NonNull LocalDate forDate) {
        Context context = getApplicationContext();
        String channelId = context.getString(R.string.alphaess_channel_id);
        PendingIntent activityPendingIntent = UI2NotificationLaunch.contentIntent(
                context, mUseUI2, ComparisonUIViewModel.Importer.ALPHAESS,
                systemSN, ImportAlphaActivity.class);
        Notification notification = new NotificationCompat.Builder(context, channelId)
                .setContentTitle("AlphaESS data not yet available")
                .setContentText(forDate + " for " + systemSN + " isn't available yet; will retry on the next daily run")
                .setSmallIcon(R.drawable.ic_baseline_download_24)
                .setAutoCancel(true)
                .setContentIntent(activityPendingIntent)
                .build();
        mNotificationManager.notify(mNoDataNotificationId, notification);
    }

    private boolean fetchFromOpenAlphaESS(OpenAlphaESSClient mOpenAlphaESSClient, String systemSN, LocalDate yesterday) throws AlphaESSException {
        boolean ret = false;
        Log.i(TAG, "DailyWorker fetching data for " + yesterday);
        publishProgress(yesterday.toString(), true);

        // Get the data from AlphaESS (a) power, (b) energy
        GetOneDayPowerResponse oneDayPowerBySn = mOpenAlphaESSClient.getOneDayPowerBySn(yesterday.format(DATE_FORMAT));
        GetOneDayEnergyResponse oneDayEnergyBySn = mOpenAlphaESSClient.getOneDayEnergyBySn(yesterday.format(DATE_FORMAT));

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
            Log.i(TAG, "DailyWorker storing data for " + yesterday);
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
            Log.i(TAG, "DailyWorker storing normalizedEntityList " + normalizedEntityList.size());
            ret = true;
        }
        else {
            Log.w(TAG, "DailyWorker got null data for " + yesterday);
        }
        return ret;
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
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(
                getApplicationContext().getString(R.string.alphaess_channel_id), name, importance);
        channel.setDescription(description);
        // Register the channel with the system. You can't change the importance
        // or other notification behaviors after this.
        NotificationManager notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }
}
