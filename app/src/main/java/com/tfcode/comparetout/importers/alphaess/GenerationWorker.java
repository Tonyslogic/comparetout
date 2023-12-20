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

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.TOUTCApplication;
import com.tfcode.comparetout.importers.alphaess.responses.GetEssListResponse;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.model.importers.alphaess.IntervalRow;
import com.tfcode.comparetout.model.scenario.DOWDist;
import com.tfcode.comparetout.model.scenario.HourlyDist;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.model.scenario.LoadProfileData;
import com.tfcode.comparetout.model.scenario.MonthlyDist;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.PanelData;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import io.reactivex.Single;

public class GenerationWorker extends Worker {

    private final ToutcRepository mToutcRepository;
    private final NotificationManager mNotificationManager;
    private static final int mNotificationId = 2;
    private boolean mStopped = false;

    private static final String SYSTEM_LIST_KEY = "system_list";

    public static final String KEY_SYSTEM_SN = "KEY_SYSTEM_SN";
    public static final String LP = "LP";
    public static final String INV = "INV";
    public static final String PAN = "PAN";
    public static final String PAN_D = "PAN_D";
    public static final String BAT = "BAT";
    public static final String BAT_SCH = "BAT_SCH";
    public static final String FROM = "FROM";
    public static final String TO = "TO";
    public static final String PANEL_COUNTS = "PANEL_COUNTS";
    public static final String MPPT_COUNT = "MPPT_COUNT";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static final String PROGRESS = "PROGRESS";

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
        boolean mINV = inputData.getBoolean(INV, false);
        boolean mPNL = inputData.getBoolean(PAN, false);
        boolean mPNLD = inputData.getBoolean(PAN_D, false);
        boolean mBAT = inputData.getBoolean(BAT, false);
        boolean mBATS = inputData.getBoolean(BAT_SCH, false);
        int mpptCount = inputData.getInt(MPPT_COUNT, 0);
        String mFrom = inputData.getString(FROM);
        String mTo = inputData.getString(TO);
        List<Integer> mStringPanelCount = Arrays.stream(
                Objects.requireNonNull(inputData.getString(PANEL_COUNTS)).split(","))
                .map(Integer::parseInt)
                .collect(Collectors.toList());

        TOUTCApplication application = (TOUTCApplication)getApplicationContext(); Preferences.Key<String> systemList = PreferencesKeys.stringKey(SYSTEM_LIST_KEY);
        Single<String> value4 = application.getDataStore()
                .data().firstOrError()
                .map(prefs -> prefs.get(systemList)).onErrorReturnItem("{\"code\": 200, \"msg\": \"Success\", \"expMsg\": null, \"data\": []}");
        String systemListJsonString =  value4.blockingGet();
        GetEssListResponse getEssListResponse = new Gson().fromJson(systemListJsonString, GetEssListResponse.class);
        GetEssListResponse.DataItem theSystemData = new GetEssListResponse.DataItem();
        if (!(null == getEssListResponse) && !(getEssListResponse.data.isEmpty())) {
            for (GetEssListResponse.DataItem system : getEssListResponse.data) {
                if (!(null == mSystemSN) && mSystemSN.equals(system.sysSn)) {
                    theSystemData = system;
                    break;
                }
            }
        }

        // check for mandatory members
        if (null == mToutcRepository) return Result.failure();
        if (null == mSystemSN) return Result.failure();

        List<Scenario> scenarios = mToutcRepository.getScenarios();
        List<String> mScenarioNames = new ArrayList<>();
        for (Scenario scenario : scenarios) mScenarioNames.add(scenario.getScenarioName());

        long createdLoadProfileID = 0;

        // Create a scenario && get its id
        report(getString(R.string.creating_usage));
        Scenario scenario = new Scenario();
        String scenarioName = mSystemSN;
        int suffix = 1;
        while (mScenarioNames.contains(scenarioName)) {
            scenarioName = scenarioName + "_" + suffix;
            suffix++;
        }
        String finalScenarioName = scenarioName;
        scenario.setScenarioName(scenarioName);
        ScenarioComponents scenarioComponents = new ScenarioComponents(scenario,
                null, null, null, null, null,
                null, null, null, null, null);
        long assignedScenarioID = mToutcRepository.insertScenarioAndReturnID(scenarioComponents);
        long inverterID;
        List<AlphaESSTransformedData> dbRows = null;

        // Create & store a load profile
        if (mLP) {
            report(getString(R.string.gen_load_profile));
            List<IntervalRow> hourly = mToutcRepository.getSumHour(mSystemSN, mFrom, mTo);
            List<IntervalRow> weekly = mToutcRepository.getSumDOW(mSystemSN, mFrom, mTo);
            List<IntervalRow> monthly = mToutcRepository.getAvgMonth(mSystemSN, mFrom, mTo);
            Double baseLoad = mToutcRepository.getBaseLoad(mSystemSN, mFrom, mTo);

            Double totalLoad = 0D;
            for (IntervalRow row : weekly) totalLoad += row.load;

            LoadProfile loadProfile = new LoadProfile();
            loadProfile.setAnnualUsage(totalLoad);
            loadProfile.setDistributionSource(mSystemSN);
            loadProfile.setHourlyBaseLoad(baseLoad);
            HourlyDist hd = new HourlyDist();
            List<Double> hourOfDayDist = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                Double hv = hourly.get(i).load;
                hourOfDayDist.add((hv / totalLoad) * 100);
            }
            hd.dist = hourOfDayDist;
            loadProfile.setHourlyDist(hd);
            DOWDist dd = new DOWDist();
            List<Double> dowDist = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                Double dv = weekly.get(i).load;
                dowDist.add((dv / totalLoad) * 100);
            }
            dd.dowDist = dowDist;
            loadProfile.setDowDist(dd);
            MonthlyDist md = new MonthlyDist();
            List<Double> moyDist = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                Double mv = monthly.get(i).load;
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
            int dbRowIndex = 0;
            report("Loaded data");

            ArrayList<LoadProfileData> rows = new ArrayList<>();
            LocalDateTime active = LocalDateTime.of(2001, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2002, 1, 1, 0, 0);
            DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            DateTimeFormatter minFormat = DateTimeFormatter.ofPattern("HH:mm");
            while (active.isBefore(end)) {
                LoadProfileData row = new LoadProfileData();
                row.setDo2001(active.getDayOfYear());
                row.setLoadProfileID(createdLoadProfileID);
                row.setDate(active.format(dateFormat));
                row.setMinute(active.format(minFormat));
                row.setDow(active.getDayOfWeek().getValue());
                row.setMod(active.getHour() * 60 + active.getMinute());
                // Not every 5 minute interval has data uploaded to AlphaESS
                if (row.getMinute().equals(dbRows.get(dbRowIndex).getMinute())) {
                    row.setLoad(dbRows.get(dbRowIndex).getLoad());
                    dbRowIndex++;
                }
                else {
                    // A value is needed to ensure the simulation algorithm works correctly
                    row.setLoad(0D);
                }
                rows.add(row);
                active = active.plusMinutes(5);
            }
            report("Storing data");

            mToutcRepository.createLoadProfileDataEntries(rows);
            report("Stored data");
        }

        // Inverter
        if (mINV && mLP) {
            Inverter inverter = new Inverter();
            inverter.setInverterName(mSystemSN);
            inverter.setMinExcess(0.008D);
            inverter.setMaxInverterLoad(theSystemData.poinv);
            inverter.setMpptCount(mpptCount);
            int loss = (int) (Math.round(mToutcRepository.getLosses(mSystemSN) * 100.0) / 100.0);
            inverter.setAc2dcLoss(loss);
            inverter.setDc2acLoss(loss);
            inverter.setDc2dcLoss(0);

            inverterID = mToutcRepository.saveInverter(assignedScenarioID, inverter);
            report("Stored inverter");
        }

        // Panels
        if (mPNL && mINV && mLP) {
            double totalPV = theSystemData.popv * 1000;
            int totalPanelCount = 0;
            for (int i = 0; i < mpptCount; i++) totalPanelCount += mStringPanelCount.get(i);
            for (int i = 0; i < mpptCount; i++) {
                Integer stringSize = mStringPanelCount.get(i);
                Panel panel = new Panel();
                panel.setPanelCount(stringSize);
                panel.setPanelName("String-" + (i+1));
                panel.setInverter(mSystemSN);
                panel.setMppt(i+1);
                panel.setPanelkWp((int) (totalPV/totalPanelCount));
                long panelID = mToutcRepository.savePanel(assignedScenarioID, panel);

                // Panel data
                if (mPNLD) {

                    int dbRowIndex = 0;
                    report("Loaded data");

                    double proportionOfPV = (double)stringSize/(double)totalPanelCount;

                    ArrayList<PanelData> rows = new ArrayList<>();
                    LocalDateTime active = LocalDateTime.of(2001, 1, 1, 0, 1);
                    LocalDateTime dbActive = active.plusMinutes(-1);
                    LocalDateTime end = LocalDateTime.of(2002, 1, 1, 0, 0);
                    DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                    DateTimeFormatter minFormat = DateTimeFormatter.ofPattern("HH:mm");
                    while (active.isBefore(end)) {
                        PanelData row = new PanelData();
                        row.setDo2001(active.getDayOfYear());
                        row.setPanelID(panelID);
                        row.setDate(active.format(dateFormat));
                        row.setMinute(active.format(minFormat));
                        row.setDow(active.getDayOfWeek().getValue());
                        row.setMod(active.getHour() * 60 + active.getMinute());
                        // Not every 5 minute interval has data uploaded to AlphaESS
                        // The solar data is one minute ahead of the load and normalized data
                        if (dbActive.format(minFormat).equals(dbRows.get(dbRowIndex).getMinute())) {
                            row.setPv(dbRows.get(dbRowIndex).getPv() * proportionOfPV);
                            dbRowIndex++;
                        }
                        else {
                            // A value is needed to ensure the simulation algorithm works correctly
                            row.setPv(0D);
                        }
                        rows.add(row);
                        active = active.plusMinutes(5);
                        dbActive = dbActive.plusMinutes(5);
                    }
                    report("Storing PV data");

                    mToutcRepository.savePanelData(rows);
                    report("Stored PV data");
                }
            }
        }

        // Battery
        if (mBAT) {

        }

        // Battery schedules
        if (mBATS) {

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
