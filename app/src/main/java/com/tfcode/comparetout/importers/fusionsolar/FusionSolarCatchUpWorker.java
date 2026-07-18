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

package com.tfcode.comparetout.importers.fusionsolar;

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
import com.tfcode.comparetout.SimulatorLauncher;
import com.tfcode.comparetout.importers.CredentialStore;
import com.tfcode.comparetout.importers.fusionsolar.responses.EnergyBalanceResponse;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.ui2.UI2MainActivity;
import com.tfcode.comparetout.ui2.UI2NotificationLaunch;
import com.tfcode.comparetout.ui2.UserTimezoneStore;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Fetches plant-level FusionSolar data day-by-day into the shared
 * alphaESSTransformedData table under the "FusionSolar-&lt;dn&gt;" sysSn
 * namespace: per day, one energy-balance call goes through
 * FusionSolarDataMassager.
 *
 * One class serves both the one-shot catch-up (KEY_START_DATE set) and the
 * periodic daily run (absent ⇒ yesterday) — the Home Assistant / Octopus /
 * Solis simplification. Days already stored are skipped, so re-runs are
 * cheap and a retry resumes from the first missing day. Request
 * pacing/backoff lives in FusionSolarClient; fatal auth failures surface
 * here as distinct notifications and {@code Result.failure()} — a captcha
 * demand during unattended re-login gets its own "sign in again" wording —
 * transient exhaustion as {@code Result.retry()} (enqueue with EXPONENTIAL
 * backoff criteria).
 *
 * No credentials travel in the input {@code Data} (WorkManager persists
 * inputs unencrypted): the worker resolves the portal username/password via
 * {@link CredentialStore} at run time.
 */
public class FusionSolarCatchUpWorker extends Worker {

    private final ToutcRepository mToutcRepository;
    private final NotificationManager mNotificationManager;
    // Distinct notification slot per worker class — see
    // plans/eventual-bouncing-hare.md. 2-16 and 4242 are taken.
    private static final int mNotificationId = 17;
    private boolean mStopped = false;
    // Cached on the worker thread, consumed by getNotification() so the
    // content intent routes through UI2NotificationLaunch. FusionSolar is a
    // UI2-only source, so the "legacy" fallback is the UI2 shell too.
    private boolean mUseUI2 = false;
    private String mSelectedSysSn = null;
    private long mLastNotifyAt = 0L;
    private static final long MIN_NOTIFY_INTERVAL_MS = 250L;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** The persisted region host ("region01eu5.fusionsolar.huawei.com"). */
    public static final String KEY_HOST = "KEY_HOST";
    /** The plant's raw dn ("NE=…") — what every portal data call takes. */
    public static final String KEY_STATION_DN = "KEY_STATION_DN";
    public static final String KEY_STATION_NAME = "KEY_STATION_NAME";
    public static final String KEY_START_DATE = "KEY_START_DATE";

    public static final String PROGRESS = "PROGRESS";

    public FusionSolarCatchUpWorker(@NonNull Context context,
                                    @NonNull WorkerParameters workerParams) {
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
        CredentialStore.Credentials credentials = CredentialStore.get(
                getApplicationContext(), CredentialStore.Source.FUSION_SOLAR);
        if (null == credentials) {
            publishProgress("FusionSolar credentials unavailable — sign in again", true, false);
            return Result.failure();
        }
        String host = inputData.getString(KEY_HOST);
        String dn = inputData.getString(KEY_STATION_DN);
        String stationName = inputData.getString(KEY_STATION_NAME);
        String startDate = inputData.getString(KEY_START_DATE);
        if (null == dn) {
            publishProgress("FusionSolar plant not configured", true, false);
            return Result.success();
        }
        String sysSn = FusionSolarDataMassager.sysSnFor(dn);
        mSelectedSysSn = sysSn;
        if (null == stationName) stationName = sysSn;
        mUseUI2 = UI2NotificationLaunch.isUI2Enabled(getApplicationContext());

        // Daily mode: no start date ⇒ (re)fetch yesterday. The loop never
        // touches today — its data is still accumulating server-side and a
        // partial day would be skipped as "already stored" forever after.
        if (null == startDate) startDate = LocalDate.now().minusDays(1).format(DATE_FORMAT);

        publishProgress("Starting Fetch", true, true);

        ZoneId zone = UserTimezoneStore.resolvedZone(getApplicationContext());
        FusionSolarClient client = new FusionSolarClient(
                credentials.first, credentials.second, host);

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
                EnergyBalanceResponse balance = client.getEnergyBalance(dn, current, zone);
                List<AlphaESSTransformedData> rows =
                        FusionSolarDataMassager.massage(sysSn, current, zone, balance);
                if (rows.isEmpty()) {
                    // Plant offline all day: store nothing and move on — a
                    // later run re-fetches because the day stays missing.
                    publishProgress("No data for " + dateString + ", skipped", false, true);
                } else {
                    mToutcRepository.addTransformedData(rows);
                    storedRows += rows.size();
                    publishProgress("Fetched " + dateString, false, true);
                }
                current = current.plusDays(1);
            }
        } catch (FusionSolarCaptchaRequiredException e) {
            publishProgress(getApplicationContext()
                    .getString(R.string.fusionsolar_sign_in_again), true, false);
            return Result.failure();
        } catch (FusionSolarAuthException e) {
            publishProgress("FusionSolar rejected the credentials — re-enter the sign-in details",
                    true, false);
            return Result.failure();
        } catch (FusionSolarException e) {
            publishProgress("FusionSolar unreachable, will retry — stored "
                    + storedRows + " readings so far", true, false);
            return Result.retry();
        }

        if (mStopped) {
            mNotificationManager.cancel(mNotificationId);
            return Result.success();
        }

        publishProgress("All done importing " + stationName
                + " (" + storedRows + " readings)", true, true);
        // Self-heal convention: data-prep success re-triggers any waiting
        // simulation work (missing-only; no-ops in the SOURCE profile).
        SimulatorLauncher.simulateIfNeeded(getApplicationContext());
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
        String id = context.getString(R.string.fusionsolar_channel_id);
        String title = context.getString(R.string.fetch_fusionsolar_notification_title);
        String cancel = context.getString(R.string.cancel_fetch_alpha);
        // This PendingIntent can be used to cancel the worker
        PendingIntent intent = WorkManager.getInstance(context)
                .createCancelPendingIntent(getId());

        PendingIntent activityPendingIntent = UI2NotificationLaunch.contentIntent(
                context, mUseUI2, ComparisonUIViewModel.Importer.FUSION_SOLAR,
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
        CharSequence name = getApplicationContext().getString(R.string.fusionsolar_channel_name);
        String description = getApplicationContext().getString(R.string.channel_description);
        // DEFAULT (not LOW): builders silence via setSilent(true); a below-DEFAULT "silent" channel hides the status-bar icon
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(
                getApplicationContext().getString(R.string.fusionsolar_channel_id), name, importance);
        channel.setDescription(description);
        NotificationManager notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }
}
