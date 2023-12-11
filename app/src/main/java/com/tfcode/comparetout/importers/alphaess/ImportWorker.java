/*
 * Copyright (c) 2023. Tony Finnerty
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
import android.net.Uri;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.importers.alphaess.responses.GetOneDayEnergyResponse;
import com.tfcode.comparetout.importers.alphaess.responses.GetOneDayPowerResponse;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawEnergy;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawPower;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.model.json.AlphaESSJsonTools;
import com.tfcode.comparetout.model.json.importers.alphaess.AlphaESSExportJsonFile;
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ImportWorker extends Worker {

    private final ToutcRepository mToutcRepository;
    private final NotificationManager mNotificationManager;
    private static final int mNotificationId = 2;
    private boolean mStopped = false;


    public static final String KEY_SYSTEM_SN = "KEY_SYSTEM_SN";
    public static final String KEY_URI = "KEY_URI";

    public static final String PROGRESS = "PROGRESS";

    public ImportWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mToutcRepository = new ToutcRepository((Application) context);
        mNotificationManager = (NotificationManager)
                context.getSystemService(NOTIFICATION_SERVICE);
        setProgressAsync(new Data.Builder().putString(PROGRESS, "Starting import from file").build());
        System.out.println("Import worker created");
    }

    @Override
    public void onStopped(){
        super.onStopped();
        mStopped = true;
    }

    @NonNull
    @Override
    public Result doWork() {
        System.out.println("ImportWorker:doWork invoked ");
        Data inputData = getInputData();
        String systemSN = inputData.getString(KEY_SYSTEM_SN);
        String uriString = inputData.getString(KEY_URI);
        Uri fileUri = Uri.parse(uriString);

        // Mark the Worker as important
        String progress = "Importing energy";
        setProgressAsync(new Data.Builder().putString(PROGRESS, progress).build());
        ForegroundInfo foregroundInfo = createForegroundInfo("Importing energy");
        mNotificationManager.notify(mNotificationId, foregroundInfo.getNotification());
        
        // Do some work
        InputStream is = null;
        try {
            is = getApplicationContext().getContentResolver().openInputStream(fileUri);
            InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
            JsonReader jsonReader = new JsonReader(reader);
            Gson gson = new Gson();
            List<AlphaESSRawEnergy> energyList = new ArrayList<>();
            List<AlphaESSRawPower> powerList = new ArrayList<>(100);

            jsonReader.beginObject();
            while (jsonReader.hasNext()) {
                if (mStopped) break;
                String name = jsonReader.nextName();
                if("energy".equals(name)) {
                    jsonReader.beginArray();
                    while (jsonReader.hasNext()) {
                        GetOneDayEnergyResponse.DataItem item = gson.fromJson(jsonReader, GetOneDayEnergyResponse.DataItem.class);
                        energyList.add(AlphaESSEntityUtil.getEnergyRowFromJsonDataItem(item));
                        if (mStopped) break;
                    }
                    jsonReader.endArray();
                    for (AlphaESSRawEnergy energy: energyList) {
                        mToutcRepository.addRawEnergy(energy);
                        if (mStopped) break;
                    }
                    progress = "Imported energy";
                    setProgressAsync(new Data.Builder().putString(PROGRESS, progress).build());
                    foregroundInfo = createForegroundInfo("Imported energy");
                    mNotificationManager.notify(mNotificationId, foregroundInfo.getNotification());
                }
                else if ("power".equals(name)){
                    int batchTrigger = 288;
                    int batchSize = 0;
                    jsonReader.beginArray();
                    while (jsonReader.hasNext()) {
                        if (mStopped) break;
                        GetOneDayPowerResponse.DataItem item = gson.fromJson(jsonReader, GetOneDayPowerResponse.DataItem.class);
                        powerList.add(AlphaESSEntityUtil.getPowerRowFromJsonDataItem(item));
                        batchSize++;
                        if ((batchSize % batchTrigger) == 0) {
                            mToutcRepository.addRawPower(powerList);
                            powerList = new ArrayList<>(100);
                            if ((batchSize % batchTrigger * 3) == 0) {
                                progress = "Importing power: " + batchSize + "/" + energyList.size() * 288;
                                setProgressAsync(new Data.Builder().putString(PROGRESS, progress).build());
                                foregroundInfo = createForegroundInfo(progress);
                                mNotificationManager.notify(mNotificationId, foregroundInfo.getNotification());
                            }
                        }
                    }
                    jsonReader.endArray();
                    mToutcRepository.addRawPower(powerList);
                    progress = "Imported power done: " + batchSize + " entries";
                    setProgressAsync(new Data.Builder().putString(PROGRESS, progress).build());
                    foregroundInfo = createForegroundInfo(progress);
                    mNotificationManager.notify(mNotificationId, foregroundInfo.getNotification());
                }
            }
            jsonReader.endObject();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (!(null == is)) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        long notifyTime = System.nanoTime();
        List<String> dates = mToutcRepository.getExportDatesForSN(systemSN);
        for (String date : dates) {
            AlphaESSRawEnergy energy = mToutcRepository.getAlphaESSEnergyForDate(systemSN, date);
            List<AlphaESSRawPower> powerList = mToutcRepository.getAlphaESSPowerForSharing(systemSN, date);
            if (System.nanoTime() - notifyTime > 1e+9) {
                notifyTime = System.nanoTime();
                progress = "Unitizing and scaling: " + date + " of " + dates.get(dates.size() -1);
                setProgressAsync(new Data.Builder().putString(PROGRESS, progress).build());
                foregroundInfo = createForegroundInfo(progress);
                mNotificationManager.notify(mNotificationId, foregroundInfo.getNotification());
            }

            // Fix the power-data (5 minute alignment and missing entries)
            List<DataMassager.DataPoint> points = DataMassager.getDataPointsForPowerResponse(powerList);
            Map<Long, FiveMinuteEnergies> fixed = DataMassager.oneDayDataInFiveMinuteIntervals(points);
            // Get the total load (ePV - eOutput) + eInput
            double ePV = energy.getEnergypv();
            double eLoad = (ePV - energy.getEnergyOutput()) + energy.getEnergyInput();
            double eFeed = energy.getEnergyOutput();
            double eBuy = energy.getEnergyInput();
            // Unitize and scale power (in kWh 5 minute intervals)
            Map<Long, FiveMinuteEnergies> massaged = DataMassager.massage(fixed, ePV, eLoad, eFeed, eBuy);

            // Store transformed data
            List<AlphaESSTransformedData> normalizedEntityList = AlphaESSEntityUtil.getTransformedDataRows(massaged, systemSN);
            mToutcRepository.addTransformedData(normalizedEntityList);
        }

        progress = "All done importing " + systemSN;
        setProgressAsync(new Data.Builder().putString(PROGRESS, progress).build());
        foregroundInfo = createForegroundInfo(progress);
        mNotificationManager.notify(mNotificationId, foregroundInfo.getNotification());

        if (mStopped) mNotificationManager.cancel(mNotificationId);

        return Result.success();
    }

    @NonNull
    private ForegroundInfo createForegroundInfo(@NonNull String progress) {
        // Build a notification using bytesRead and contentLength

        Context context = getApplicationContext();
        String id = context.getString(R.string.export_alpha_channel_id);
        String title = context.getString(R.string.alphaess_import_notification_title);
        String cancel = context.getString(R.string.cancel_fetch_alpha);
        // This PendingIntent can be used to cancel the worker
        PendingIntent intent = WorkManager.getInstance(context)
                .createCancelPendingIntent(getId());

        createChannel();

        Notification notification = new NotificationCompat.Builder(context, id)
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(progress)
                .setSmallIcon(R.drawable.ic_baseline_download_24)
                .setOngoing(true)
                .setAutoCancel(true)
                .setSilent(true)
                // Add the cancel action to the notification which can
                // be used to cancel the worker
                .addAction(android.R.drawable.ic_delete, cancel, intent)
                .build();

        return new ForegroundInfo(mNotificationId, notification);
    }

    private void createChannel() {
        // Create a Notification channel
        CharSequence name = getApplicationContext().getString(R.string.channel_name);
        String description = getApplicationContext().getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(
                getApplicationContext().getString(R.string.export_alpha_channel_id), name, importance);
        channel.setDescription(description);
        // Register the channel with the system. You can't change the importance
        // or other notification behaviors after this.
        NotificationManager notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }
}
