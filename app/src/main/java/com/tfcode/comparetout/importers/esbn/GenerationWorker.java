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

package com.tfcode.comparetout.importers.esbn;

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
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.importers.IntervalRow;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.model.scenario.DOWDist;
import com.tfcode.comparetout.model.scenario.HourlyDist;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.model.scenario.LoadProfileData;
import com.tfcode.comparetout.model.scenario.MonthlyDist;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GenerationWorker extends Worker {

    private final ToutcRepository mToutcRepository;
    private final NotificationManager mNotificationManager;
    private static final int mNotificationId = 2;
    private boolean mStopped = false;

    public static final String KEY_SYSTEM_SN = "KEY_SYSTEM_SN";
    public static final String LP = "LP";
    public static final String FROM = "FROM";
    public static final String TO = "TO";
    public static final String SCENARIO_ID = "SCENARIO_ID";
    public static final String LOAD_PROFILE_ID = "LOAD_PROFILE_ID";

    public static final String PROGRESS = "PROGRESS";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MIN_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public GenerationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mToutcRepository = new ToutcRepository((Application) context);
        mNotificationManager = (NotificationManager)
                context.getSystemService(NOTIFICATION_SERVICE);
        setProgressAsync(new Data.Builder().putString(PROGRESS, "Starting GenerationWorker").build());
        System.out.println("GenerationWorker created");
    }

    @Override
    public void onStopped(){
        super.onStopped();
        mStopped = true;
    }

    @NonNull
    @Override
    public Result doWork() {
        System.out.println("GenerationWorker:doWork invoked ");

        // Load the input data
        Data inputData = getInputData();
        String mSystemSN = inputData.getString(KEY_SYSTEM_SN);
        boolean mLP = inputData.getBoolean(LP, false);
        String mFrom = inputData.getString(FROM);
        String mTo = inputData.getString(TO);
        long mScenarioID = inputData.getLong(SCENARIO_ID, 0L);
        long mLoadProfileID = inputData.getLong(LOAD_PROFILE_ID, 0L);

        // check for mandatory members
        if (null == mToutcRepository) return Result.failure();
        if (null == mSystemSN) return Result.failure();

        List<Scenario> scenarios = mToutcRepository.getScenarios();
        Map<Long, String> mScenarioNames = new HashMap<>();
        for (Scenario scenario : scenarios) mScenarioNames.put(scenario.getScenarioIndex(), scenario.getScenarioName());

        long createdLoadProfileID = 0;

        // Create a scenario && get its id
        long assignedScenarioID;
        String finalScenarioName;

        if (mScenarioID == 0) {
            report(getString(R.string.creating_usage));
            Scenario scenario = new Scenario();
            String scenarioName = mSystemSN;
            int suffix = 1;
            while (mScenarioNames.containsValue(scenarioName)) {
                scenarioName = scenarioName + "_" + suffix;
                suffix++;
            }
            finalScenarioName = scenarioName;
            scenario.setScenarioName(scenarioName);
            ScenarioComponents scenarioComponents = new ScenarioComponents(scenario,
                    null, null, null, null, null,
                    null, null, null, null, null);
            assignedScenarioID = mToutcRepository.insertScenarioAndReturnID(scenarioComponents, false);
        }
        else {
            assignedScenarioID = mScenarioID;
            finalScenarioName = mScenarioNames.get(mScenarioID);
        }
        List<AlphaESSTransformedData> dbRows;

        if (mLoadProfileID != 0) {
            mToutcRepository.deleteLoadProfileData(mLoadProfileID);
        }

        // Create & store a load profile
        if (mLP) {
            report(getString(R.string.gen_load_profile));
            List<IntervalRow> hourly = mToutcRepository.getSumHour(mSystemSN, mFrom, mTo);
            List<IntervalRow> weekly = mToutcRepository.getSumDOW(mSystemSN, mFrom, mTo);
            List<IntervalRow> monthly = mToutcRepository.getAvgMonth(mSystemSN, mFrom, mTo);
            // TODO get base load for esbn
            double baseLoad = 300D; mToutcRepository.getBaseLoad(mSystemSN, mFrom, mTo);

            Double totalLoad = 0D;
            for (IntervalRow row : weekly) totalLoad += row.buy;

            LoadProfile loadProfile = new LoadProfile();
            loadProfile.setLoadProfileIndex(mLoadProfileID);
            loadProfile.setAnnualUsage(totalLoad);
            loadProfile.setDistributionSource(mSystemSN);
            loadProfile.setHourlyBaseLoad(baseLoad);
            HourlyDist hd = new HourlyDist();
            List<Double> hourOfDayDist = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                Double hv = hourly.get(i).buy;
                hourOfDayDist.add((hv / totalLoad) * 100);
            }
            hd.dist = hourOfDayDist;
            loadProfile.setHourlyDist(hd);
            DOWDist dd = new DOWDist();
            List<Double> dowDist = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                Double dv = weekly.get(i).buy;
                dowDist.add((dv / totalLoad) * 100);
            }
            dd.dowDist = dowDist;
            loadProfile.setDowDist(dd);
            MonthlyDist md = new MonthlyDist();
            List<Double> moyDist = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                Double mv = monthly.get(i).buy;
                moyDist.add((mv / totalLoad) * 100);
            }
            md.monthlyDist = moyDist;
            loadProfile.setMonthlyDist(md);
            createdLoadProfileID = mToutcRepository.saveLoadProfileAndReturnID(assignedScenarioID, loadProfile);
        }

        // Create and store load profile data
        if (mLP) {
            report(getString(R.string.adding_data));

            dbRows = mToutcRepository.getAlphaESSTransformedData(mSystemSN, mFrom, mTo);
            Map<Integer, Map<String, AlphaESSTransformedData>> dbLookup = new HashMap<>();
            for (AlphaESSTransformedData dbRow : dbRows) {
                LocalDate dbDate = LocalDate.parse(dbRow.getDate(), DATE_FORMAT);
                String dbTime = dbRow.getMinute();
                Map<String, AlphaESSTransformedData> entry = dbLookup.get(dbDate.getDayOfYear());
                if (null == entry) {
                    entry = new HashMap<>();
                    entry.put(dbTime, dbRow);
                    dbLookup.put(dbDate.getDayOfYear(), entry);
                }
                else entry.put(dbTime, dbRow);
            }
            report("Loaded data");

            ArrayList<LoadProfileData> rows = new ArrayList<>();
            LocalDateTime active = LocalDateTime.of(2001, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2002, 1, 1, 0, 0);
            while (active.isBefore(end)) {
                LoadProfileData row = new LoadProfileData();
                row.setDo2001(active.getDayOfYear());
                row.setLoadProfileID(createdLoadProfileID);
                row.setDate(active.format(DATE_FORMAT));
                row.setMinute(active.format(MIN_FORMAT));
                row.setDow(active.getDayOfWeek().getValue());
                row.setMod(active.getHour() * 60 + active.getMinute());
                // Not every 30 minute interval has data uploaded to AlphaESS
                Integer doy = active.getDayOfYear();
                LocalDateTime activeLookup;
                int minute = active.getMinute();
                if (minute > 30) activeLookup = active.plusHours(1).withMinute(0);
                else activeLookup = active.withMinute(30);
                String hhmm = activeLookup.format(MIN_FORMAT);
                double loadToSet = 0D;
                Map<String, AlphaESSTransformedData> aDay = dbLookup.get(doy);
                if (!(null == aDay)) {
                    AlphaESSTransformedData aHalfHour = aDay.get(hhmm);
                    if (!(null == aHalfHour)) {
                        loadToSet = aHalfHour.getBuy() / 6D;
                    }
                }
                row.setLoad(loadToSet);

                rows.add(row);
                active = active.plusMinutes(5);
            }
            report("Storing data");

            mToutcRepository.createLoadProfileDataEntries(rows);
            report("Stored data");
        }

        // Done :-)
        report(getString(R.string.completed, finalScenarioName));

        
        if (mStopped) mNotificationManager.cancel(mNotificationId);
        return Result.success();
    }

    private String getString(int resource_id) {
        return getApplicationContext().getString(resource_id);
    }

    private String getString(int resource_id, String templateValue) {
        return getApplicationContext().getString(resource_id, templateValue);
    }

    private void report(String theReport) {
        setProgressAsync(new Data.Builder().putString(PROGRESS, theReport).build());
        ForegroundInfo foregroundInfo = createForegroundInfo(theReport);
        mNotificationManager.notify(mNotificationId, foregroundInfo.getNotification());
    }

    @NonNull
    private ForegroundInfo createForegroundInfo(@NonNull String progress) {
        // Build a notification using bytesRead and contentLength

        Context context = getApplicationContext();
        String id = context.getString(R.string.fetch_alpha_channel_id);
        String title = context.getString(R.string.generate_usage);
        String cancel = context.getString(R.string.cancel_fetch_alpha);
        // This PendingIntent can be used to cancel the worker
        PendingIntent intent = WorkManager.getInstance(context)
                .createCancelPendingIntent(getId());

        createChannel();

        Notification notification = new NotificationCompat.Builder(context, id)
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(progress)
                .setSmallIcon(R.drawable.ic_baseline_content_copy_24)
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
                getApplicationContext().getString(R.string.fetch_alpha_channel_id), name, importance);
        channel.setDescription(description);
        // Register the channel with the system. You can't change the importance
        // or other notification behaviors after this.
        NotificationManager notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }
}