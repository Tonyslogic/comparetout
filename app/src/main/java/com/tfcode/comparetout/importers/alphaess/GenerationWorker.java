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
import android.util.Pair;

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
        long assignedScenarioID = mToutcRepository.insertScenarioAndReturnID(scenarioComponents, false);
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

            mToutcRepository.saveInverter(assignedScenarioID, inverter);
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
                    report("Loaded raw data");

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
                }
            }
        }

        // Battery
        if (mBAT) {
            Battery battery = new Battery();
            battery.setBatterySize(theSystemData.surplusCobat);
            battery.setStorageLoss(1);
            ChargeModel chargeModel = new ChargeModel();
            report("Creating battery settings");
            {
                double maxRate = 0;
                List<Double> cmInput0_13 = mToutcRepository.getChargeModelInput(mSystemSN, 0, 13);
                double total = 0d;
                double count = 0d;
                for (int i = (int) (cmInput0_13.size() * 0.2); i < (int) (cmInput0_13.size() * 0.8); i++) {
                    double rate = cmInput0_13.get(i);
                    total += rate;
                    count++;
                    if (maxRate < rate) maxRate = rate;
                }
                double average_0_13 = total / count;

                List<Double> cmInput13_90 = mToutcRepository.getChargeModelInput(mSystemSN, 13, 90);
                total = 0d;
                count = 0d;
                for (int i = (int) (cmInput13_90.size() * 0.2); i < (int) (cmInput13_90.size() * 0.8); i++) {
                    double rate = cmInput13_90.get(i);
                    if (total < rate) total = rate;
                    count++;
                    if (maxRate < rate) maxRate = rate;
                }
                double average_13_90 = total;

                List<Double> cmInput90_100 = mToutcRepository.getChargeModelInput(mSystemSN, 90, 101);
                total = 0d;
                count = 0d;
                for (int i = (int) (cmInput90_100.size() * 0.2); i < (int) (cmInput90_100.size() * 0.8); i++) {
                    double rate = cmInput90_100.get(i);
                    total += rate;
                    count++;
                    if (maxRate < rate) maxRate = rate;
                }
                double average_90_100 = total / count;

                chargeModel.percent0 = (int) ((average_0_13 / maxRate) * 100);
                chargeModel.percent12 = (int) ((average_13_90 / maxRate) * 100);
                chargeModel.percent90 = (int) ((average_90_100 / maxRate) * 100);
                chargeModel.percent100 = 0;
            }
            battery.setChargeModel(chargeModel);
            report("Finding battery kpis");
            //Discharge stop
            double dischargeStop;
            {
                List<Double> minCharges = mToutcRepository.getDischargeStopInput(mSystemSN);

                double minimum = 10000000000d;
                for (int i = (int) (minCharges.size() * 0.2); i < (int) (minCharges.size() * 0.8); i++) {
                    double rate = minCharges.get(i);
                    if (minimum > rate) minimum = rate;
                }
                dischargeStop = minimum ;
            }
            battery.setDischargeStop(dischargeStop);
            double maxDischarge;
            double maxCharge;
            double maxBatDischarge = 0;
            double maxBatCharge = 0;
            List<MaxCalcRow> maxCalcRows = mToutcRepository.getMaxCalcInput(mSystemSN);
            Iterator<MaxCalcRow> rowIterator = maxCalcRows.listIterator();
            MaxCalcRow previous = rowIterator.next();
            // assuming max charge and discharge is 0.5 C, we need to see the max for a 5min period
            // as a percentage of full capacity
            double maxCD = ((theSystemData.surplusCobat / 2d / 12d) / theSystemData.surplusCobat) * 100;

            while (rowIterator.hasNext()) {
                MaxCalcRow current = rowIterator.next();
                // Contiguous
                if (current.longtime == previous.longtime + 300) {
                    // cBat increasing or decreasing (charging or discharging)
                    if ( current.cbat > previous.cbat) { // Charging
                        double charge = current.cbat - previous.cbat;
                        if (charge > maxBatCharge && charge <= maxCD) {
                            maxBatCharge = charge;
                        }
                    }
                    else { // Discharging
                        double discharge = previous.cbat - current.cbat;
                        if (discharge > maxBatDischarge && discharge <= maxCD) {
                            maxBatDischarge = discharge;
                        }
                    }
                }
                previous = current;
            }
            // load converted to percentage
            maxDischarge = (theSystemData.surplusCobat/100d) * maxBatDischarge;
            maxCharge =  (theSystemData.surplusCobat/100d) * maxBatCharge;

            maxDischarge = ((int) (maxDischarge * 10000)) / 10000d;
            maxCharge = ((int) (maxCharge * 10000)) / 10000d;

            battery.setMaxDischarge(maxDischarge);
            battery.setMaxCharge(maxCharge);
            battery.setInverter(mSystemSN);
            report("Storing battery");
            mToutcRepository.saveBatteryForScenario(assignedScenarioID, battery);


            // Battery schedules
            if (mBATS) {
                report("Finding schedules");

                // Get input from the DB
                List<ScheduleRIInput> scheduleRIInputs = mToutcRepository.getScheduleRIInput(mSystemSN, mFrom, mTo);

                Iterator<ScheduleRIInput> scheduleRIInputIterator = scheduleRIInputs.listIterator();
                ScheduleRIInput previousInput = null;
                if (scheduleRIInputIterator.hasNext()) previousInput = scheduleRIInputIterator.next();
                Map<BES, List<Pair<Integer, Integer>>> schedules = new HashMap<>();
                BES bes = new BES();
                if (!(null == previousInput)) while (scheduleRIInputIterator.hasNext()) {
                    // Search for the begin/end and stop cbat %
                    // Also the months, and days
                    ScheduleRIInput current = scheduleRIInputIterator.next();
                    // Reset conditions: (1) begin is set; (2) non-contiguous hours; (3) reducing cbat
                    if ((bes.begin != -1)
                            && (current.hour-1 != previousInput.hour)
                            && (current.cbat < previousInput.cbat)) {
                        List<Pair<Integer, Integer>> scheduleInputs = schedules.computeIfAbsent(bes, k -> new ArrayList<>());
                        scheduleInputs.add(new Pair<>(previousInput.month, previousInput.dow));
                        // Reset
                        bes = new BES();
                    }
                    // Range conditions: (1) begin is set; (2) contiguous hours; (3) at least equal cbat
                    if ((bes.begin != -1)
                            && (current.hour-1 == previousInput.hour)
                            && (current.cbat >= previousInput.cbat)) {
                        bes.end = current.hour + 1;
                        bes.stop = current.cbat;
                    }
                    // Start conditions (1) Begin is unset; (2) CFG > 0; (3) CFG > previousCFG
                    if ((bes.begin == -1) && (current.cfg > 0) && (current.cfg > previousInput.cfg)) {
                        bes.begin = current.hour;
                        bes.end = current.hour + 1;
                        bes.stop = current.cbat;
                    }
                    previousInput = current;
                }
                int loadShiftNameSuffix = 1;
                for (Map.Entry<BES, List<Pair<Integer, Integer>>> schedule : schedules.entrySet()) {
                    if (schedule.getKey().stop > 100d) continue;
                    if (schedule.getKey().begin == schedule.getKey().end) continue;
                    LoadShift loadShift = new LoadShift();
                    loadShift.setName("Generated_" + loadShiftNameSuffix);
                    loadShift.setInverter(mSystemSN);
                    loadShift.setBegin((int) schedule.getKey().begin);
                    loadShift.setEnd((int) schedule.getKey().end);
                    loadShift.setStopAt(schedule.getKey().stop);
                    Set<Integer> days = new HashSet<>();
                    Set<Integer> months = new HashSet<>();
                    for (Pair<Integer, Integer> monthsDays : schedule.getValue()) {
                        days.add(monthsDays.second);
                        months.add(monthsDays.first);
                    }
                    if (days.size() == 1 && months.size() == 1) continue;
                    loadShift.getMonths().months = months.stream().sorted().collect(Collectors.toList());
                    loadShift.getDays().ints = days.stream().sorted().collect(Collectors.toList());

                    System.out.println(loadShift);
                    mToutcRepository.saveLoadShiftForScenario(assignedScenarioID, loadShift);
                    loadShiftNameSuffix++;
                }
            }
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

class BES implements Comparable<BES> {
    long begin = -1;
    long end;
    double stop;

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        BES other = (BES) o;

        return begin == other.begin && end == other.end && Double.compare(other.stop, stop) == 0;
    }


    @Override
    public int compareTo(BES o) {
        if (begin == o.begin) {
            if (end == o.end) {
                return Double.compare(stop, o.stop);
            }
            return Long.compare(end, o.end);
        }
        return Long.compare(begin, o.begin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(begin, end, stop);
    }

}
