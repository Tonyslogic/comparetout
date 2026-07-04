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

package com.tfcode.comparetout.importers.homeassistant;

import static android.content.Context.NOTIFICATION_SERVICE;
import static com.tfcode.comparetout.importers.homeassistant.ImportHAOverview.HA_COBAT_KEY;

import android.app.NotificationManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.TOUTCApplication;
import com.tfcode.comparetout.importers.AbstractGenerationWorker;
import com.tfcode.comparetout.model.IntHolder;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.ChargeModel;
import com.tfcode.comparetout.model.scenario.EVCharge;
import com.tfcode.comparetout.model.scenario.HWSchedule;
import com.tfcode.comparetout.model.scenario.HWSystem;
import com.tfcode.comparetout.model.scenario.MonthHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.Single;

public class GenerationWorker extends AbstractGenerationWorker {

    public GenerationWorker(@NonNull Context context, WorkerParameters workerParams) {
        super(context, workerParams);
        mNotificationManager = (NotificationManager)
                context.getSystemService(NOTIFICATION_SERVICE);
        setProgressAsync(new Data.Builder().putString(PROGRESS, "Starting GenerationWorker").build());
        System.out.println("GenerationWorker created");
    }
    @Override
    protected List<AlphaESSTransformedData> expandHoursIfNeeded(List<AlphaESSTransformedData> dbRows) {
        final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        final DateTimeFormatter MIN_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
        final DateTimeFormatter DB_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd,HH:mm");
        List<AlphaESSTransformedData> expandedRows = new ArrayList<>();
        for (AlphaESSTransformedData dbRow : dbRows) {
            LocalDateTime date = LocalDateTime.parse(dbRow.getDate() + "," + dbRow.getMinute(), DB_FORMAT).minusHours(1);
            for (int i = 0; i < 60; i += 5) {
                AlphaESSTransformedData expandedRow = new AlphaESSTransformedData();
                expandedRow.setSysSn(dbRow.getSysSn());
                expandedRow.setDate(date.format(DATE_FORMAT));
                expandedRow.setMinute(date.format(MIN_FORMAT));
                expandedRow.setPv(dbRow.getPv()/12D);
                expandedRow.setLoad(dbRow.getLoad()/12D);
                expandedRow.setFeed(dbRow.getFeed()/12D);
                expandedRow.setBuy(dbRow.getBuy()/12D);
                expandedRow.setEvActual(dbRow.getEvActual()/12D);
                expandedRow.setHwActual(dbRow.getHwActual()/12D);
                expandedRow.setHpActual(dbRow.getHpActual()/12D);
                expandedRows.add(expandedRow);
                date = date.plusMinutes(5);
            }
        }
        return expandedRows;
    }
    protected int getLoss(String mSystemSN) {
        // TODO: Implement this method
        return 4;
    }

    @NonNull
    @Override
    protected SystemData getSystemData(String mSystemSN) {
        double popv = mToutcRepository.getHAPopv(mSystemSN);
        double poinv = mToutcRepository.getHAPoinv(mSystemSN);
        TOUTCApplication application = (TOUTCApplication)getApplicationContext();
        Preferences.Key<String> systemList = PreferencesKeys.stringKey(HA_COBAT_KEY);
        Single<String> value4 = application.getDataStore()
                .data().firstOrError()
                .map(prefs -> prefs.get(systemList)).onErrorReturnItem("0.0");
        List<Double> estimatedBatteryCapacities =
                Arrays.stream(value4.blockingGet().split(","))
                        .map(Double::valueOf)
                        .collect(Collectors.toList());
        return new SystemData(popv, poinv, estimatedBatteryCapacities);
    }
    @Override
    protected void generateBattery(SystemData theSystemData, String mSystemSN, long assignedScenarioID) {
        for (Double coBat : theSystemData.surplusCobat) {
            Battery battery = new Battery();
            battery.setBatterySize((int)(coBat * 100) / 100D);
            battery.setStorageLoss(1);
            ChargeModel chargeModel = new ChargeModel();

            chargeModel.percent0 = 30;
            chargeModel.percent12 = 100;
            chargeModel.percent90 = 10;
            chargeModel.percent100 = 0;
            battery.setChargeModel(chargeModel);

            double maxDischarge = (int)(((coBat * 0.5) / 12D) * 100) / 100D;
            double maxCharge = (int)(((coBat * 0.5) / 12D) * 100) / 100D;

            battery.setMaxDischarge(maxDischarge);
            battery.setMaxCharge(maxCharge);
            battery.setInverter(mSystemSN);
            report("Storing battery");
            mToutcRepository.saveBatteryForScenario(assignedScenarioID, battery);
        }
    }

    @Override
    protected void generateBatterySchedules(String mSystemSN, String mFrom, String mTo, long assignedScenarioID) {
        // Do nothing, Not supported for Home Assistant
    }

    // ------------------------------------------------------------------
    // Individual-device derivation (plans/ha/design.md, Enhancement 1)
    //
    // The user's classification in the sensor dialog is the opt-in: a role that was
    // classified is modelled explicitly at derivation. The measured slice is removed
    // from the derived load profile (unless it was already removed at ingestion via
    // the per-device adjust flag) and, for EV / hot water, a schedule inferred from
    // the measured pattern re-adds a modelled version. Heat pump is mark-only: the
    // slice stays in the load and no component is auto-created.
    // ------------------------------------------------------------------

    private static final double MIN_INFERRED_TOTAL_KWH = 10D;

    private boolean mRolesResolved = false;
    private boolean mEvClassified = false;
    private boolean mHwClassified = false;
    private boolean mSubtractEv = false;
    private boolean mSubtractHw = false;

    private void ensureDeviceRoles() {
        if (mRolesResolved) return;
        mRolesResolved = true;
        TOUTCApplication application = (TOUTCApplication) getApplicationContext();
        Preferences.Key<String> sensorsKey = PreferencesKeys.stringKey(ImportHAOverview.HA_SENSORS_KEY);
        Single<String> value = application.getDataStore()
                .data().firstOrError()
                .map(prefs -> prefs.get(sensorsKey)).onErrorReturnItem("{}");
        EnergySensors sensors = null;
        try {
            sensors = new Gson().fromJson(value.blockingGet(),
                    new TypeToken<EnergySensors>() {}.getType());
        } catch (Exception e) {
            System.out.println("GenerationWorker::ensureDeviceRoles, failed to load sensors");
        }
        boolean evAdjusted = false;
        boolean hwAdjusted = false;
        if (!(null == sensors)) {
            for (DeviceSensor device : sensors.getClassifiedDevices()) {
                switch (device.role) {
                    case EV:
                        mEvClassified = true;
                        if (device.adjust) evAdjusted = true;
                        break;
                    case HOT_WATER:
                        mHwClassified = true;
                        if (device.adjust) hwAdjusted = true;
                        break;
                    default:
                        break;
                }
            }
        }
        // An adjust-flagged device's slice is already out of the stored load, so subtracting
        // again at derivation would double-count the removal.
        mSubtractEv = mEvClassified && !evAdjusted;
        mSubtractHw = mHwClassified && !hwAdjusted;
    }

    @Override
    protected boolean usesNetLoadAggregates() {
        ensureDeviceRoles();
        return mSubtractEv || mSubtractHw;
    }

    @Override
    protected double loadForProfile(AlphaESSTransformedData row) {
        ensureDeviceRoles();
        double load = row.getLoad();
        if (mSubtractEv) load -= row.getEvActual();
        if (mSubtractHw) load -= row.getHwActual();
        return Math.max(0, load);
    }

    @Override
    protected void generateInferredComponents(String mSystemSN,
            List<AlphaESSTransformedData> dbRows, long assignedScenarioID) {
        ensureDeviceRoles();
        if (mEvClassified) {
            DeviceUseProfile profile = inferUseProfile(dbRows, AlphaESSTransformedData::getEvActual);
            if (!(null == profile)) {
                for (int[] window : profile.windows) {
                    EVCharge evCharge = new EVCharge();
                    evCharge.setName("EV (HomeAssistant)");
                    evCharge.setBegin(window[0]);
                    evCharge.setEnd(window[1]);
                    evCharge.setDraw(profile.drawKw);
                    MonthHolder months = new MonthHolder();
                    months.months = new ArrayList<>(profile.months);
                    evCharge.setMonths(months);
                    IntHolder days = new IntHolder();
                    days.ints = new ArrayList<>(profile.days);
                    evCharge.setDays(days);
                    mToutcRepository.saveEVChargeForScenario(assignedScenarioID, evCharge);
                }
                report("Stored inferred EV charge schedule");
            }
        }
        if (mHwClassified) {
            DeviceUseProfile profile = inferUseProfile(dbRows, AlphaESSTransformedData::getHwActual);
            if (!(null == profile)) {
                HWSystem hwSystem = new HWSystem();
                // Immersion/diverter rating estimated from the busiest observed hour
                hwSystem.setHwRate(Math.max(1D, Math.min(profile.drawKw, 3D)));
                mToutcRepository.saveHWSystemForScenario(assignedScenarioID, hwSystem);
                for (int[] window : profile.windows) {
                    HWSchedule hwSchedule = new HWSchedule();
                    hwSchedule.setName("Hot water (HomeAssistant)");
                    hwSchedule.setBegin(window[0]);
                    hwSchedule.setEnd(window[1]);
                    MonthHolder months = new MonthHolder();
                    months.months = new ArrayList<>(profile.months);
                    hwSchedule.setMonths(months);
                    IntHolder days = new IntHolder();
                    days.ints = new ArrayList<>(profile.days);
                    hwSchedule.setDays(days);
                    mToutcRepository.saveHWScheduleForScenario(assignedScenarioID, hwSchedule);
                }
                report("Stored inferred hot water schedule");
            }
        }
        // Heat pump: mark-only by design — hpActual stays visible on the source rows and in
        // the load; the wizard's heat pump section is where an HP component is modelled.
    }

    /**
     * Inferred usage pattern for one device role: charge/heat window(s), typical draw and
     * the days/months the device is actually used.
     */
    private static class DeviceUseProfile {
        final List<int[]> windows;     // [beginHour, endHourExclusive), split at midnight
        final double drawKw;
        final List<Integer> days;      // 0..6, Sunday = 0 (schedule convention)
        final List<Integer> months;    // 1..12

        DeviceUseProfile(List<int[]> windows, double drawKw,
                List<Integer> days, List<Integer> months) {
            this.windows = windows;
            this.drawKw = drawKw;
            this.days = days;
            this.months = months;
        }
    }

    private DeviceUseProfile inferUseProfile(List<AlphaESSTransformedData> dbRows,
            ToDoubleFunction<AlphaESSTransformedData> slice) {
        double[] hourEnergy = new double[24];
        double[] dowEnergy = new double[7];
        double[] monthEnergy = new double[12];
        Map<String, Double> hourBuckets = new HashMap<>(); // date#hour -> kWh, for the draw estimate
        double total = 0D;
        for (AlphaESSTransformedData row : dbRows) {
            double kwh = slice.applyAsDouble(row);
            if (kwh <= 0) continue;
            LocalDate rowDate = LocalDate.parse(row.getDate(), DATE_FORMAT);
            int hour = Integer.parseInt(row.getMinute().substring(0, 2));
            hourEnergy[hour] += kwh;
            dowEnergy[rowDate.getDayOfWeek().getValue() % 7] += kwh;
            monthEnergy[rowDate.getMonthValue() - 1] += kwh;
            hourBuckets.merge(row.getDate() + "#" + hour, kwh, Double::sum);
            total += kwh;
        }
        if (total < MIN_INFERRED_TOTAL_KWH) return null;

        // Draw: kWh delivered in the busiest single hour approximates the device kW rating.
        double drawKw = 0D;
        for (double kwh : hourBuckets.values()) drawKw = Math.max(drawKw, kwh);
        drawKw = Math.max(1D, Math.round(drawKw * 10D) / 10D);

        // Typical window: the contiguous run of hours (circular) around the peak hour that
        // keeps at least 25% of the peak hour's energy.
        int peak = 0;
        for (int h = 1; h < 24; h++) if (hourEnergy[h] > hourEnergy[peak]) peak = h;
        boolean[] include = new boolean[24];
        for (int h = 0; h < 24; h++) include[h] = hourEnergy[h] >= 0.25 * hourEnergy[peak];
        int left = peak;
        int right = peak;
        int span = 1;
        while (span < 24 && include[(left + 23) % 24]) {
            left = (left + 23) % 24;
            span++;
        }
        while (span < 24 && include[(right + 1) % 24]) {
            right = (right + 1) % 24;
            span++;
        }
        List<int[]> windows = new ArrayList<>();
        if (left <= right) {
            windows.add(new int[]{left, right + 1});
        } else {
            // Crosses midnight: the schedule window check is non-wrapping, so emit two entries.
            windows.add(new int[]{left, 24});
            windows.add(new int[]{0, right + 1});
        }

        // Days/months the device is genuinely used (relative to its own busiest day/month).
        double maxDow = 0D;
        for (double v : dowEnergy) maxDow = Math.max(maxDow, v);
        List<Integer> days = new ArrayList<>();
        for (int d = 0; d < 7; d++) if (dowEnergy[d] >= 0.2 * maxDow) days.add(d);
        double maxMonth = 0D;
        for (double v : monthEnergy) maxMonth = Math.max(maxMonth, v);
        List<Integer> months = new ArrayList<>();
        for (int m = 0; m < 12; m++) if (monthEnergy[m] >= 0.15 * maxMonth) months.add(m + 1);

        return new DeviceUseProfile(windows, drawKw, days, months);
    }
}