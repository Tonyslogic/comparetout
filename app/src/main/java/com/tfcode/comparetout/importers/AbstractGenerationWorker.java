/*
 * Copyright (c) 2024. Tony Finnerty
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

package com.tfcode.comparetout.importers;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.util.Pair;

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
import com.tfcode.comparetout.model.importers.alphaess.MaxCalcRow;
import com.tfcode.comparetout.model.importers.alphaess.ScheduleRIInput;
import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.ChargeModel;
import com.tfcode.comparetout.model.scenario.DOWDist;
import com.tfcode.comparetout.model.scenario.HourlyDist;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.model.scenario.LoadProfileData;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.MonthlyDist;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.PanelData;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

public abstract class AbstractGenerationWorker extends Worker {
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
    public static final String PROGRESS = "PROGRESS";
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter MIN_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int mNotificationId = 2;
    protected final ToutcRepository mToutcRepository;
    protected NotificationManager mNotificationManager;
    private boolean mStopped = false;

    public AbstractGenerationWorker(@NonNull Context context, WorkerParameters workerParams) {
        super(context, workerParams);
        mToutcRepository = new ToutcRepository((Application) context);
    }

    @Override
    public void onStopped() {
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

        SystemData theSystemData = getSystemData(mSystemSN);

        // check for mandatory members
        if (null == mToutcRepository) return Result.failure();
        if (null == mSystemSN) return Result.failure();

        List<Scenario> scenarios = mToutcRepository.getScenarios();
        List<String> mScenarioNames = new ArrayList<>();
        for (Scenario scenario : scenarios) mScenarioNames.add(scenario.getScenarioName());

        long createdLoadProfileID = 0;

        // Create a scenario && get its id
        report(getString(R.string.creating_usage));
        ScenarionKeys scenarioKeys = generateScenario(mSystemSN, mScenarioNames);

        Map<Integer, Map<String, AlphaESSTransformedData>> dbLookup = null;

        // Create & store a load profile
        if (mLP) {
            createdLoadProfileID = generateLoadProfile(mSystemSN, mFrom, mTo, scenarioKeys.assignedScenarioID);
        }

        // Create and store load profile data
        if (mLP) dbLookup = generateLoadProfileData(mSystemSN, mFrom, mTo, createdLoadProfileID);

        // Inverter
        if (mINV && mLP)
            generateInverter(mSystemSN, theSystemData, mpptCount, scenarioKeys.assignedScenarioID);

        // Panels
        if (mPNL && mINV && mLP) {
            double totalPV = theSystemData.popv * 1000;
            int totalPanelCount = 0;
            for (int i = 0; i < mpptCount; i++) totalPanelCount += mStringPanelCount.get(i);
            for (int i = 0; i < mpptCount; i++) {
                PanelSet panelSet = getPanelSet(mStringPanelCount, i, mSystemSN, totalPV, totalPanelCount, scenarioKeys.assignedScenarioID);

                // Panel data
                if (mPNLD)
                    generatePanelData((double) panelSet.stringSize, (double) totalPanelCount, panelSet.panelID, dbLookup);
            }
        }

        // Battery
        if (mBAT) {
            generateBattery(theSystemData, mSystemSN, scenarioKeys.assignedScenarioID);
            // Battery schedules
            if (mBATS)
                generateBatterySchedules(mSystemSN, mFrom, mTo, scenarioKeys.assignedScenarioID);
        }

        // Done :-)
        report(getString(R.string.completed, scenarioKeys.finalScenarioName));

        if (mStopped) mNotificationManager.cancel(mNotificationId);
        return Result.success();
    }

    @NonNull
    private ScenarionKeys generateScenario(String mSystemSN, List<String> mScenarioNames) {
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
        long assignedScenarioID = mToutcRepository.insertScenarioAndReturnID(scenarioComponents, false);
        ScenarionKeys scenarioKeys = new ScenarionKeys(finalScenarioName, assignedScenarioID);
        return scenarioKeys;
    }

    private long generateLoadProfile(String mSystemSN, String mFrom, String mTo, long assignedScenarioID) {
        long createdLoadProfileID;
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
        return createdLoadProfileID;
    }

    @NonNull
    private Map<Integer, Map<String, AlphaESSTransformedData>> generateLoadProfileData(
            String mSystemSN, String mFrom, String mTo, long createdLoadProfileID) {
        Map<Integer, Map<String, AlphaESSTransformedData>> dbLookup;
        List<AlphaESSTransformedData> dbRows;
        report(getString(R.string.adding_data));

        dbRows = mToutcRepository.getAlphaESSTransformedData(mSystemSN, mFrom, mTo);
        dbRows = expandHoursIfNeeded(dbRows);
        dbLookup = new HashMap<>();
        for (AlphaESSTransformedData dbRow : dbRows) {
            LocalDate dbDate = LocalDate.parse(dbRow.getDate(), DATE_FORMAT);
            String dbTime = dbRow.getMinute();
            Map<String, AlphaESSTransformedData> entry = dbLookup.get(dbDate.getDayOfYear());
            if (null == entry) {
                entry = new HashMap<>();
                entry.put(dbTime, dbRow);//(dbTimeLT.format(MIN_FORMAT), dbRow);
                dbLookup.put(dbDate.getDayOfYear(), entry);
            } else entry.put(dbTime, dbRow);
        }
        report("Loaded load data");

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
            // Not every 5 minute interval has data uploaded to AlphaESS
            Integer doy = active.getDayOfYear();
            String hhmm = row.getMinute();
            double loadToSet = 0D;
            Map<String, AlphaESSTransformedData> aDay = dbLookup.get(doy);
            if (!(null == aDay)) {
                AlphaESSTransformedData a5MinutePeriod = aDay.get(hhmm);
                if (!(null == a5MinutePeriod)) {
                    loadToSet = a5MinutePeriod.getLoad();
                }
            }
            row.setLoad(loadToSet);

            rows.add(row);
            active = active.plusMinutes(5);
        }
        report("Storing data");

        mToutcRepository.createLoadProfileDataEntries(rows);
        report("Stored data");
        return dbLookup;
    }

    protected List<AlphaESSTransformedData> expandHoursIfNeeded(List<AlphaESSTransformedData> dbRows) {
        return dbRows;
    }

    private void generateInverter(String mSystemSN, SystemData theSystemData, int mpptCount, long assignedScenarioID) {
        Inverter inverter = new Inverter();
        inverter.setInverterName(mSystemSN);
        inverter.setMinExcess(0.008D);
        inverter.setMaxInverterLoad(theSystemData.poinv);
        inverter.setMpptCount(mpptCount);
        int loss = getLoss(mSystemSN);
        inverter.setAc2dcLoss(loss);
        inverter.setDc2acLoss(loss);
        inverter.setDc2dcLoss(0);

        mToutcRepository.saveInverter(assignedScenarioID, inverter);
        report("Stored inverter");
    }

    abstract protected int getLoss(String mSystemSN);

    @NonNull
    private PanelSet getPanelSet(List<Integer> mStringPanelCount, int i, String mSystemSN, double totalPV, int totalPanelCount, long assignedScenarioID) {
        Integer stringSize = mStringPanelCount.get(i);
        Panel panel = new Panel();
        panel.setPanelCount(stringSize);
        panel.setPanelName("String-" + (i + 1));
        panel.setInverter(mSystemSN);
        panel.setMppt(i + 1);
        panel.setPanelkWp((int) (totalPV / totalPanelCount));
        long panelID = mToutcRepository.savePanel(assignedScenarioID, panel);
        return new PanelSet(stringSize, panelID);
    }

    private void generatePanelData(double stringSize, double totalPanelCount, long panelID, Map<Integer, Map<String, AlphaESSTransformedData>> dbLookup) {
        report("Loaded raw data");

        double proportionOfPV = stringSize / totalPanelCount;

        ArrayList<PanelData> rows = new ArrayList<>();
        LocalDateTime active = LocalDateTime.of(2001, 1, 1, 0, 1);
        LocalDateTime dbActive = active.plusMinutes(-1);
        LocalDateTime end = LocalDateTime.of(2002, 1, 1, 0, 0);
        while (active.isBefore(end)) {
            PanelData row = new PanelData();
            row.setDo2001(active.getDayOfYear());
            row.setPanelID(panelID);
            row.setDate(active.format(DATE_FORMAT));
            row.setMinute(active.format(MIN_FORMAT));
            row.setDow(active.getDayOfWeek().getValue());
            row.setMod(active.getHour() * 60 + active.getMinute());
            // Not every 5 minute interval has data uploaded to AlphaESS
            // The solar data is one minute ahead of the load and normalized data
            Integer doy = dbActive.getDayOfYear();
            String hhmm = dbActive.format(MIN_FORMAT);
            double pvToSet = 0D;
            Map<String, AlphaESSTransformedData> aDay = dbLookup.get(doy);
            if (!(null == aDay)) {
                AlphaESSTransformedData a5MinutePeriod = aDay.get(hhmm);
                if (!(null == a5MinutePeriod)) {
                    pvToSet = a5MinutePeriod.getPv() * proportionOfPV;
                }
            }
            row.setPv(pvToSet);

            rows.add(row);
            active = active.plusMinutes(5);
            dbActive = dbActive.plusMinutes(5);
        }
        report("Storing PV data");

        mToutcRepository.savePanelData(rows);
    }

    abstract protected void generateBattery(SystemData theSystemData, String mSystemSN, long assignedScenarioID);

    abstract protected void generateBatterySchedules(String mSystemSN, String mFrom, String mTo, long assignedScenarioID);

    @NonNull
    protected abstract SystemData getSystemData(String mSystemSN);

    private String getString(int resource_id) {
        return getApplicationContext().getString(resource_id);
    }

    private String getString(int resource_id, String templateValue) {
        return getApplicationContext().getString(resource_id, templateValue);
    }

    protected void report(String theReport) {
        setProgressAsync(new Data.Builder().putString(PROGRESS, theReport).build());
        ForegroundInfo foregroundInfo = createForegroundInfo(theReport);
        mNotificationManager.notify(mNotificationId, foregroundInfo.getNotification());
    }

    @NonNull
    private ForegroundInfo createForegroundInfo(@NonNull String progress) {
        // Build a notification using bytesRead and contentLength

        Context context = getApplicationContext();
        String id = context.getString(R.string.alphaess_channel_id);
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

    private static class ScenarionKeys {
        public final String finalScenarioName;
        public final long assignedScenarioID;

        public ScenarionKeys(String finalScenarioName, long assignedScenarioID) {
            this.finalScenarioName = finalScenarioName;
            this.assignedScenarioID = assignedScenarioID;
        }
    }

    private static class PanelSet {
        public final Integer stringSize;
        public final long panelID;

        public PanelSet(Integer stringSize, long panelID) {
            this.stringSize = stringSize;
            this.panelID = panelID;
        }
    }

    public static class SystemData {
        public double popv;
        public double poinv;
        public double surplusCobat;

        public SystemData(double popv, double poinv, double surplusCobat) {
            this.popv = popv;
            this.poinv = poinv;
            this.surplusCobat = surplusCobat;
        }

    }
}