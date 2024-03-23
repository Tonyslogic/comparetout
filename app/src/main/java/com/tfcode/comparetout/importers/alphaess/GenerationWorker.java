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
import com.tfcode.comparetout.importers.AbstractGenerationWorker;
import com.tfcode.comparetout.importers.alphaess.responses.GetEssListResponse;
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

import io.reactivex.Single;

public class GenerationWorker extends AbstractGenerationWorker {

    private static final String SYSTEM_LIST_KEY = "system_list";

    public GenerationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mNotificationManager = (NotificationManager)
                context.getSystemService(NOTIFICATION_SERVICE);
        setProgressAsync(new Data.Builder().putString(PROGRESS, "Starting GenerationWorker").build());
        System.out.println("GenerationWorker created");
    }
    protected int getLoss(String mSystemSN) {
        return (int) (Math.round(mToutcRepository.getLosses(mSystemSN) * 100.0) / 100.0);
    }

    @Override
    @NonNull
    protected SystemData getSystemData(String mSystemSN) {
        TOUTCApplication application = (TOUTCApplication)getApplicationContext();
        Preferences.Key<String> systemList = PreferencesKeys.stringKey(SYSTEM_LIST_KEY);
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
        List<Double> coBats = new ArrayList<>();
        coBats.add(theSystemData.surplusCobat);
        return new SystemData(theSystemData.popv, theSystemData.poinv, coBats);
    }

    @Override
    protected void generateBattery(SystemData theSystemData, String mSystemSN, long assignedScenarioID) {
        Battery battery = new Battery();
        double coBat = theSystemData.surplusCobat.stream().mapToDouble(Double::doubleValue).sum();
        battery.setBatterySize(coBat);
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
            dischargeStop = minimum;
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
        double maxCD = ((coBat / 2d / 12d) / coBat) * 100;

        while (rowIterator.hasNext()) {
            MaxCalcRow current = rowIterator.next();
            // Contiguous
            if (current.longtime == previous.longtime + 300) {
                // cBat increasing or decreasing (charging or discharging)
                if (current.cbat > previous.cbat) { // Charging
                    double charge = current.cbat - previous.cbat;
                    if (charge > maxBatCharge && charge <= maxCD) {
                        maxBatCharge = charge;
                    }
                } else { // Discharging
                    double discharge = previous.cbat - current.cbat;
                    if (discharge > maxBatDischarge && discharge <= maxCD) {
                        maxBatDischarge = discharge;
                    }
                }
            }
            previous = current;
        }
        // load converted to percentage
        maxDischarge = (coBat / 100d) * maxBatDischarge;
        maxCharge = (coBat / 100d) * maxBatCharge;

        maxDischarge = ((int) (maxDischarge * 10000)) / 10000d;
        maxCharge = ((int) (maxCharge * 10000)) / 10000d;

        battery.setMaxDischarge(maxDischarge);
        battery.setMaxCharge(maxCharge);
        battery.setInverter(mSystemSN);
        report("Storing battery");
        mToutcRepository.saveBatteryForScenario(assignedScenarioID, battery);
    }
    @Override
    protected void generateBatterySchedules(String mSystemSN, String mFrom, String mTo, long assignedScenarioID) {
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
                    && (current.hour - 1 != previousInput.hour)
                    && (current.cbat < previousInput.cbat)) {
                List<Pair<Integer, Integer>> scheduleInputs = schedules.computeIfAbsent(bes, k -> new ArrayList<>());
                scheduleInputs.add(new Pair<>(previousInput.month, previousInput.dow));
                // Reset
                bes = new BES();
            }
            // Range conditions: (1) begin is set; (2) contiguous hours; (3) at least equal cbat
            if ((bes.begin != -1)
                    && (current.hour - 1 == previousInput.hour)
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

