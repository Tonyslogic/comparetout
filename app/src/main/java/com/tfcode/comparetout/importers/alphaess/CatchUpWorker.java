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
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.R;
import com.tfcode.comparetout.importers.alphaess.responses.GetOneDayEnergyResponse;
import com.tfcode.comparetout.importers.alphaess.responses.GetOneDayPowerResponse;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawEnergy;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawPower;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@SuppressWarnings("BusyWait")
public class CatchUpWorker extends Worker {

    private final ToutcRepository mToutcRepository;
    private final NotificationManager mNotificationManager;
    private static final int mNotificationId = 2;
    private boolean mStopped = false;

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
    }

    @Override
    public void onStopped(){
        super.onStopped();
        mStopped = true;
    }

    @NonNull
    @Override
    public Result doWork() {
        System.out.println("CatchUpWorker:doWork invoked ");
        Data inputData = getInputData();
        OpenAlphaESSClient mOpenAlphaESSClient = new OpenAlphaESSClient(
                inputData.getString(KEY_APP_ID),
                inputData.getString(KEY_APP_SECRET));
        String startDate = inputData.getString(KEY_START_DATE);
        String systemSN = inputData.getString(KEY_SYSTEM_SN);
        mOpenAlphaESSClient.setSerial(systemSN);

        LocalDate current = LocalDate.parse(startDate, DATE_FORMAT);
        LocalDate end = LocalDate.now();

        // Mark the Worker as important
        String progress = "Starting Fetch";
        setForegroundAsync(createForegroundInfo(progress));
        setProgressAsync(new Data.Builder().putString(PROGRESS, current.toString()).build());
        while (current.isBefore(end) && !mStopped) {
            if (mToutcRepository.checkSysSnForDataOnDate(systemSN, current.format(DATE_FORMAT))) {
                System.out.println("CatchUpWorker skipping " + current);
                current = current.plusDays(1);
                continue;
            }
            else try {
                System.out.println("CatchupWorker fetching data for " + current);
                // Get the data from AlphaESS (a) power, (b) energy
                GetOneDayPowerResponse oneDayPowerBySn = mOpenAlphaESSClient.getOneDayPowerBySn(current.format(DATE_FORMAT));
                GetOneDayEnergyResponse oneDayEnergyBySn = mOpenAlphaESSClient.getOneDayEnergyBySn(current.format(DATE_FORMAT));

                if (!(null == oneDayPowerBySn) && !(null == oneDayEnergyBySn) && !(null == oneDayPowerBySn.data)) {
                    // Fix the power-data (5 minute alignment and missing entries)
                    List<DataMassager.DataPoint> points = DataMassager.getDataPointsForPowerResponse(oneDayPowerBySn);
                    Map<Long, FiveMinuteEnergies> fixed = DataMassager.oneDayDataInFiveMinuteIntervals(points);
                    // Get the total load (ePV - eOutput) + eInput
                    double ePV = oneDayEnergyBySn.data.epv;
                    double eLoad = (ePV - oneDayEnergyBySn.data.eOutput) + oneDayEnergyBySn.data.eInput;
                    double eFeed = oneDayEnergyBySn.data.eOutput;
                    double eBuy = oneDayEnergyBySn.data.eInput;
                    // Unitize and scale power (in kWh 5 minute intervals)
                    Map<Long, FiveMinuteEnergies> massaged = DataMassager.massage(fixed, ePV, eLoad, eFeed, eBuy);
                    System.out.println("CatchupWorker storing data for " + current);
                    // Store raw energy
                    AlphaESSRawEnergy energyEntity = AlphaESSEntityUtil.getEnergyRowFromJson(oneDayEnergyBySn);
                    mToutcRepository.addRawEnergy(energyEntity);
                    // Store raw power
                    List<AlphaESSRawPower> powerEntityList = AlphaESSEntityUtil.getPowerRowsFromJson(oneDayPowerBySn);
                    mToutcRepository.addRawPower(powerEntityList);
                    // Store transformed data
                    List<AlphaESSTransformedData> normalizedEntityList = AlphaESSEntityUtil.getTransformedDataRows(massaged, systemSN);
                    mToutcRepository.addTransformedData(normalizedEntityList);
                    System.out.println("CatchupWorker storing normalizedEntityList " + normalizedEntityList.size());

                    // Sleep to avoid API limits
                    Thread.sleep(1200);
                }
                else {
                    System.out.println("CatchupWorker got null data for " + current);
                    Thread.sleep(1200);
                    continue;
                }
            } catch (AlphaESSException e) {
                // check to see if we are exceeding limits and retry
                e.printStackTrace();
                System.out.println("CatchupWorker got a rate limit for " + current);
                if (!(null == e.getMessage()) && e.getMessage().startsWith("err.code=6053"))
                    continue;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println("CatchUpWorker finished with " + current);
            setProgressAsync(new Data.Builder().putString(PROGRESS, current.toString()).build());
            ForegroundInfo foregroundInfo = createForegroundInfo("Done catching up with " + current);
            mNotificationManager.notify(mNotificationId, foregroundInfo.getNotification());
            current = current.plusDays(1);
        }
        if (mStopped) mNotificationManager.cancel(mNotificationId);

        return Result.success();
    }

    @NonNull
    private ForegroundInfo createForegroundInfo(@NonNull String progress) {
        // Build a notification using bytesRead and contentLength

        Context context = getApplicationContext();
        String id = context.getString(R.string.alphaess_channel_id);
        String title = context.getString(R.string.fetch_alpha_notification_title);
        String cancel = context.getString(R.string.cancel_fetch_alpha);
        // This PendingIntent can be used to cancel the worker
        PendingIntent intent = WorkManager.getInstance(context)
                .createCancelPendingIntent(getId());

        Intent importAlphaActivity = new Intent(context, ImportAlphaActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addNextIntentWithParentStack(importAlphaActivity);
        PendingIntent activityPendingIntent = stackBuilder.getPendingIntent(0,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        createChannel();

        Notification notification = new NotificationCompat.Builder(context, id)
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

        return new ForegroundInfo(mNotificationId, notification);
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
