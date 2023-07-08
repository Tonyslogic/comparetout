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

package com.tfcode.comparetout.scenario;

import static com.tfcode.comparetout.MainActivity.CHANNEL_ID;

import static java.lang.Double.max;
import static java.lang.Double.min;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.ChargeModel;
import com.tfcode.comparetout.model.scenario.EVCharge;
import com.tfcode.comparetout.model.scenario.EVDivert;
import com.tfcode.comparetout.model.scenario.HWSchedule;
import com.tfcode.comparetout.model.scenario.HWSystem;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;
import com.tfcode.comparetout.model.scenario.SimulationInputData;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimulationWorker extends Worker {

    private final ToutcRepository mToutcRepository;
    private static final Battery M_NULL_BATTERY = new Battery();
    static {
        M_NULL_BATTERY.setBatterySize(0);
        M_NULL_BATTERY.setDischargeStop(100);
        M_NULL_BATTERY.setMaxDischarge(0);
        M_NULL_BATTERY.setMaxCharge(0);
        M_NULL_BATTERY.setInverter("");
        M_NULL_BATTERY.setStorageLoss(0);
    }

    // TODO: update DST if the simulation year or duration changes
    private static final int DST_BEGIN = 25634;
    private static final int DST_END = 25645;

    public SimulationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mToutcRepository = new ToutcRepository((Application) context);

    }

    @Override
    public void onStopped(){
        super.onStopped();
    }

    @NonNull
    @Override
    public Result doWork() {
        List<Long> scenarioIDs = mToutcRepository.getAllScenariosThatNeedSimulation();

        try {
            if (scenarioIDs.size() > 0) {
                // NOTIFICATION SETUP
                int notificationId = 1;
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
                NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID);
                builder.setContentTitle("Simulating scenarios")
                        .setContentText("Simulation in progress")
                        .setSmallIcon(R.drawable.housetick)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setTimeoutAfter(20000)
                        .setSilent(true);
                // Issue the initial notification with zero progress
                int PROGRESS_MAX = 100;
                int PROGRESS_CURRENT = 0;
                int PROGRESS_CHUNK = PROGRESS_MAX;
                if (scenarioIDs.size() > 0) {
                    PROGRESS_CHUNK = PROGRESS_MAX / (scenarioIDs.size() + 1);
                    builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
                    notificationManager.notify(notificationId, builder.build());
                }

                long notifyTime = System.nanoTime();
                for (long scenarioID : scenarioIDs) {
                    ScenarioComponents scenarioComponents = mToutcRepository.getScenarioComponentsForScenarioID(scenarioID);
                    Scenario scenario = scenarioComponents.scenario;
                    if (scenario.isHasPanels()) {
                        // Check for panel data
                        boolean hasData = mToutcRepository.checkForMissingPanelData(scenarioID);// NOTIFICATION PROGRESS
                        PROGRESS_CURRENT += PROGRESS_CHUNK;
                        builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
                        if (System.nanoTime() - notifyTime > 1e+9) {
                            notifyTime = System.nanoTime();
                            notificationManager.notify(notificationId, builder.build());
                        }
                        if (!hasData) {
                            builder.setContentText("Skipping " + scenario.getScenarioName());
                            notificationManager.notify(notificationId, builder.build());
                            PROGRESS_CURRENT += PROGRESS_CHUNK;
                            builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
                            if (System.nanoTime() - notifyTime > 1e+9) {
                                notifyTime = System.nanoTime();
                                notificationManager.notify(notificationId, builder.build());
                            }
                            continue;
                        }
                    }
                    builder.setContentText("Getting data: " + scenario.getScenarioName());
                    if (System.nanoTime() - notifyTime > 1e+9) {
                        notifyTime = System.nanoTime();
                        notificationManager.notify(notificationId, builder.build());
                    }

                    int rowsToProcess = 0;
                    Map<Inverter, InputData> inputDataMap = new HashMap<>();

                    // GET BASIC LOAD & PV FROM DB
                    if (scenario.isHasInverters()) {
                        for (Inverter inverter : scenarioComponents.inverters) {
                            // Get some load simulation data to start with
                            List<SimulationInputData> simulationInputData = mToutcRepository.getSimulationInputNoSolar(scenarioID);
                            rowsToProcess = simulationInputData.size();

                            List<Double> inverterPV = new ArrayList<>(Collections.nCopies(rowsToProcess, 0d));
                            // get the total PV from all the panels related to the inverter
                            getPVForInverter(scenarioComponents, rowsToProcess, inverter, inverterPV);
                            // Populate the PV in the SimulationInputData and associate with inverter
                            mergePVWithSimulationInputData(rowsToProcess, simulationInputData, inverterPV);
                            // Get connected battery (if any, and max 1)
                            Battery connectedBattery = null;
                            ChargeFromGrid chargeFromGrid = null;
                            if (scenario.isHasBatteries()) {
                                for (Battery battery : scenarioComponents.batteries)
                                    if (battery.getInverter().equals(inverter.getInverterName()))
                                        connectedBattery = battery;
                                if (scenario.isHasLoadShifts()) {
                                    chargeFromGrid = new ChargeFromGrid(scenarioComponents.loadShifts, rowsToProcess);
                                }
                            }
                            // Get the hot water related components
                            HWSystem configuredHotWater = null;
                            boolean hotWaterDivert = false;
                            List<HWSchedule> hotWaterSchedules = null;
                            if (scenario.isHasHWSystem()) {
                                configuredHotWater = scenarioComponents.hwSystem;
                                if (scenario.isHasHWDivert()) hotWaterDivert = scenarioComponents.hwDivert.isActive();
                                if (scenario.isHasHWSchedules() && scenarioComponents.hwSchedules.size() > 0) hotWaterSchedules = scenarioComponents.hwSchedules;
                            }
                            // Get the EV related components
                            List<EVCharge> evCharges = null;
                            List<EVDivert> evDiverts = null;
                            if (scenario.isHasEVCharges() && scenarioComponents.evCharges.size() > 0) evCharges = scenarioComponents.evCharges;
                            if (scenario.isHasEVDivert() && scenarioComponents.evDiverts.size() > 0) evDiverts = scenarioComponents.evDiverts;
                            // Associate the inverter and the load for use in simulation
                            InputData iData = new InputData(inverter, simulationInputData,
                                    connectedBattery, chargeFromGrid,
                                    configuredHotWater, hotWaterDivert, hotWaterSchedules,
                                    evCharges, evDiverts);
                            inputDataMap.put(inverter, iData);
                        }
                    } else { // No solar simulation, but we need a 'perfect' inverter
                        Inverter inverter = new Inverter();
                        inverter.setInverterIndex(0);
                        inverter.setDc2acLoss(0);
                        inverter.setDc2dcLoss(0);
                        inverter.setAc2dcLoss(0);
                        inverter.setMinExcess(0);

                        // Get the hot water related components
                        HWSystem configuredHotWater = null;
                        List<HWSchedule> hotWaterSchedules = null;
                        if (scenario.isHasHWSystem()) {
                            configuredHotWater = scenarioComponents.hwSystem;
                            if (scenario.isHasHWSchedules() && scenarioComponents.hwSchedules.size() > 0) hotWaterSchedules = scenarioComponents.hwSchedules;
                        }
                        List<EVCharge> evCharges = null;
                        if (scenario.isHasEVCharges() && scenarioComponents.evCharges.size() > 0) evCharges = scenarioComponents.evCharges;
                        InputData idata = new InputData(inverter, mToutcRepository.getSimulationInputNoSolar(scenarioID),
                                null, null,
                                configuredHotWater, null, hotWaterSchedules,
                                evCharges, null);
                        inputDataMap.put(inverter, idata);
                        rowsToProcess = idata.inputData.size();
                    }

                    builder.setContentText("Simulating: " + scenario.getScenarioName());
                    if (System.nanoTime() - notifyTime > 1e+9) {
                        notifyTime = System.nanoTime();
                        notificationManager.notify(notificationId, builder.build());
                    }

                    // SIMULATE POWER DISTRIBUTION
                    ArrayList<ScenarioSimulationData> outputRows = new ArrayList<>();
                    for (int row = 0; row < rowsToProcess; row++) {
                        processOneRow(scenarioID, outputRows, row, inputDataMap);
                    }

                    // TODO: APPLY DIVERSION FOR EV

                    // STORE THE SIMULATION RESULT

                    builder.setContentText("Saving data");
                    if (System.nanoTime() - notifyTime > 1e+9) {
                        notifyTime = System.nanoTime();
                        notificationManager.notify(notificationId, builder.build());
                    }

                    mToutcRepository.saveSimulationDataForScenario(outputRows);

                    // NOTIFICATION PROGRESS
                    PROGRESS_CURRENT += PROGRESS_CHUNK;
                    builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
                    if (System.nanoTime() - notifyTime > 1e+9) {
                        notifyTime = System.nanoTime();
                        notificationManager.notify(notificationId, builder.build());
                    }
                }
                long endTime = System.nanoTime();

                if (scenarioIDs.size() > 0) {
                    // NOTIFICATION COMPLETE
                    builder.setContentText("Simulation complete")
                            .setProgress(0, 0, false);
                    notificationManager.notify(notificationId, builder.build());
                }
            }
        }
        catch (Exception e) {
            System.out.println("!!!!!!!!!!!!!!!!!!! SimulationWorker has crashed, marking as failure !!!!!!!!!!!!!!!!!!!!!");
            e.printStackTrace();
            System.out.println("!!!!!!!!!!!!!!!!!!! SimulationWorker has crashed, marking as failure !!!!!!!!!!!!!!!!!!!!!");
            return Result.failure();
        }
        return Result.success();
    }

    private void mergePVWithSimulationInputData(int rowsToProcess, List<SimulationInputData> simulationInputData, List<Double> inverterPV) {
//        double totalPV = 0d;
        for (int row = 0; row < rowsToProcess; row++) {
            if (row < DST_BEGIN)
                simulationInputData.get(row).setTpv(inverterPV.get(row));
            if (DST_BEGIN <= row &&  row <= DST_END)
                simulationInputData.get(row).setTpv(0);
            if (row > DST_END) {
                simulationInputData.get(row).setTpv(inverterPV.get(row - 12));
            }
//            totalPV += simulationInputData.get(row).getTpv();
        }
//        System.out.println("Calculated " + totalPV + " for inverter: " + inverter.getInverterName());
    }

    private void getPVForInverter(ScenarioComponents scenarioComponents, int rowsToProcess, Inverter inverter, List<Double> inverterPV) {
        for (int mppt = 1; mppt <= inverter.getMpptCount(); mppt++) {
            List<Double> mpptPV = new ArrayList<>(Collections.nCopies(rowsToProcess, 0d));
            for (Panel panel : scenarioComponents.panels) {
                if (panel.getMppt() == mppt && panel.getInverter().equals(inverter.getInverterName())) {
                    List<Double> panelPV = mToutcRepository.getSimulationInputForPanel(panel.getPanelIndex());
                    if (panel.getConnectionMode() == Panel.PARALLEL)
                        for (int row = 0; row < panelPV.size(); row++)
                            mpptPV.set(row, mpptPV.get(row) + panelPV.get(row));
                    else // Optimized
                        for (int row = 0; row < panelPV.size(); row++)
                            mpptPV.set(row, max(mpptPV.get(row), panelPV.get(row)));
                }
            }
            for (int row = 0; row < mpptPV.size(); row++) {
                inverterPV.set(row, inverterPV.get(row) + mpptPV.get(row));
            }
        }
    }

    public static void processOneRow(long scenarioID, ArrayList<ScenarioSimulationData> outputRows, int row, Map<Inverter, InputData> inputDataMap) {

        SimulationInputData inputRow = null;
        HWSystem hwSystem = null;
        Boolean hwDivert = null;
        InputData firstInputData = null;
        double inputLoad = 0d;

        // SETUP SOC AND BATTERY
        double batteryAvailableForDischarge = 0;
        double batteryAvailableForCharge = 0;
        double absoluteMinExcess = 0;
        double totalMaxInverterLoad = 0;
        if (row > 0) {
            for (Map.Entry<Inverter,InputData> entry: inputDataMap.entrySet()) {
                InputData iData = entry.getValue();
                if (null == firstInputData) firstInputData = iData;
                if (null == inputRow) inputRow = iData.inputData.get(row);
                if (null == hwSystem) hwSystem = iData.mHWSystem;
                if (null == hwDivert) hwDivert = iData.mHWDivert;
                if (null == iData.mBattery) {
                    iData.soc = 0;
                    iData.mBattery = M_NULL_BATTERY;
                }
                batteryAvailableForDischarge += iData.getDischargeCapacity(row);
                batteryAvailableForCharge += iData.getChargeCapacity();
                double iMinExcess = entry.getKey().getMinExcess();
                if (iMinExcess > 0 && absoluteMinExcess == 0) absoluteMinExcess = iMinExcess;
                else absoluteMinExcess = min( absoluteMinExcess, iMinExcess);
                totalMaxInverterLoad += entry.getKey().getMaxInverterLoad();
                inputLoad += iData.inputData.get(row).getLoad();
            }
        }
        else { // Set initial SOC
            for (Map.Entry<Inverter,InputData> entry: inputDataMap.entrySet()) {
                InputData iData = entry.getValue();
                if (null == firstInputData) firstInputData = iData;
                if (null == inputRow) inputRow = iData.inputData.get(row);
                if (null == hwSystem) hwSystem = iData.mHWSystem;
                if (null == hwDivert) hwDivert = iData.mHWDivert;
                if (null == iData.mBattery) {
                    iData.soc = 0;
                    iData.mBattery = M_NULL_BATTERY;
                }
                iData.soc = iData.getDischargeStop();
                batteryAvailableForDischarge += iData.getDischargeCapacity(row);
                batteryAvailableForCharge += iData.getChargeCapacity();
                double iMinExcess = entry.getKey().getMinExcess();
                if (iMinExcess > 0 && absoluteMinExcess == 0) absoluteMinExcess = iMinExcess;
                else absoluteMinExcess = min( absoluteMinExcess, iMinExcess);
                totalMaxInverterLoad += entry.getKey().getMaxInverterLoad();
                inputLoad += iData.inputData.get(row).getLoad();
            }
        }
        if (null == inputRow) return;

        // SETUP TOTAL AND EFFECTIVE PV
        double tPV = 0;
        double effectivePV = 0;
        for (Map.Entry<Inverter,InputData> entry: inputDataMap.entrySet()) {
            SimulationInputData sid = entry.getValue().inputData.get(row);
            tPV += sid.tpv;
            effectivePV += sid.tpv * entry.getValue().dc2acLoss;
        }

        double locallyAvailable = effectivePV + batteryAvailableForDischarge;
        double purchaseShiftingLoad = chargeBatteriesFromGridIfNeeded(inputDataMap, row);

        // COPY THE BASICS TO THE OUTPUT
        ScenarioSimulationData outputRow = new ScenarioSimulationData();
        outputRow.setScenarioID(scenarioID);
        outputRow.setDate(inputRow.getDate());
        outputRow.setMinuteOfDay(inputRow.getMod());
        outputRow.setDayOfWeek(inputRow.getDow());
        outputRow.setDayOf2001((inputRow.getDo2001()));
        outputRow.setGridToBattery(purchaseShiftingLoad);

        int month = Integer.parseInt(inputRow.getDate().split("-")[1]);

        EVCharge evCharge = firstInputData.isEVCharging(inputRow.getDow(), month, inputRow.mod);
        double scheduledEVChargeLoad = 0;
        if (!(null == evCharge)) scheduledEVChargeLoad = evCharge.getDraw() / 12d;
        outputRow.setDirectEVcharge(scheduledEVChargeLoad);

        double previousWaterTemp = 0;
        double scheduledWaterLoad = 0;
        if (outputRows.size() > 0) previousWaterTemp = outputRows.get(row -1).getWaterTemp();
        double nowWaterTemp = previousWaterTemp;
        boolean immersionIsOn = firstInputData.isHotWaterHeatingScheduled(inputRow.getDow(), month, inputRow.mod);
        boolean hwDiversionIsOn = !(null == hwSystem) && !(null == hwDivert) && hwDivert;
        double draw = 0d;
        if (immersionIsOn && !(null == hwSystem)) draw = hwSystem.getHwRate() / 12d;
        if (immersionIsOn || !hwDiversionIsOn ) {
            if (!(null == hwSystem)){
                HWSystem.Heat heat = hwSystem.heatWater(inputRow.mod, previousWaterTemp, draw);
                scheduledWaterLoad = heat.kWhUsed;
                nowWaterTemp = heat.temperature;
            }
        }

        outputRow.setLoad(inputLoad); // Record the input load before extras -- there are separate counters for extras
        inputLoad += scheduledWaterLoad; // But capture the extras in case the solar can cover it
        inputLoad += scheduledEVChargeLoad;
        outputRow.setPv(tPV);

        // SIMULATE WHERE STUFF GOES

        double buy = purchaseShiftingLoad;
        double feed = 0;
        double pv2charge = 0;
        double pv2load;
        double bat2Load = 0;
        double totalSOC;

        if (inputLoad > locallyAvailable) {
            buy += inputLoad - locallyAvailable;
            pv2load = effectivePV;
            double [] discharge = dischargeBatteries(inputDataMap, batteryAvailableForDischarge, row);
            totalSOC = discharge[0];
            bat2Load = discharge[1];
        }
        else { // we cover the load without the grid
            if (inputLoad > effectivePV) {
                pv2load = effectivePV;
                double [] discharge = dischargeBatteries(inputDataMap, (inputLoad - effectivePV), row);
                totalSOC = discharge[0];
                bat2Load = discharge[1];
            }
            else { // there is extra pv to charge/feed
                pv2load = inputLoad;
                if ((effectivePV - inputLoad) > absoluteMinExcess){
                    double charge = min((tPV - inputLoad), batteryAvailableForCharge);
                    pv2charge = charge;
                    totalSOC = chargeBatteries(inputDataMap, charge);
                    feed = effectivePV - inputLoad - charge;
                    if (totalMaxInverterLoad < (feed + charge)) {
                        feed = totalMaxInverterLoad - charge;
                    }
                    feed = max(0, feed );
                }
                else {
                    totalSOC = outputRows.get(row -1).getSOC();
                }
            }
        }

        outputRow.setBuy(buy);
        outputRow.setSOC(totalSOC);
        outputRow.setPvToCharge(pv2charge);
        outputRow.setPvToLoad(pv2load);
        outputRow.setBatToLoad(bat2Load);

        // DIVERSIONS
        double divertedToWater = 0;
        double divertedToEV = 0;
        // ELECTRIC VEHICLE
        double totalEVDivertedThisDay = firstInputData.mEVDivertDailyTotals.computeIfAbsent(inputRow.do2001, k -> 0D);
        EVDivert evDivert = firstInputData.getEVDivertOrNull(inputRow.getDow(), month, inputRow.mod);
        if (!(null == evDivert)) {
            double maxEVDivert = evDivert.getDailyMax() - totalEVDivertedThisDay;
            if (evDivert.isActive()) {
                if (evDivert.isEv1st()) {
                    if (feed > evDivert.getMinimum()) {
                        if (maxEVDivert > 0) {
                            divertedToEV = min (feed, maxEVDivert);
                            firstInputData.mEVDivertDailyTotals.put(inputRow.do2001, totalEVDivertedThisDay + divertedToEV);
                            feed = feed - divertedToEV;
                        }
                    }
                    // else we can ignore this and let the later water divert take it
                }
                else { // Water diversion must happen to reduce the feed
                    if ((!immersionIsOn) && hwDiversionIsOn) {
                        HWSystem.Heat heat = hwSystem.heatWater(inputRow.mod, previousWaterTemp, feed);
                        feed = feed - heat.kWhUsed;
                        nowWaterTemp = heat.temperature;
                        divertedToWater = heat.kWhUsed;
                        if (feed > evDivert.getMinimum()) { // there may be some feed left fo the EV
                            if (maxEVDivert > 0) {
                                divertedToEV = min (feed, maxEVDivert);
                                firstInputData.mEVDivertDailyTotals.put(inputRow.do2001, totalEVDivertedThisDay + divertedToEV);
                                feed = feed - divertedToEV;
                            }
                        }
                    }
                }
            }
        }
        // WATER
        if ((!immersionIsOn) && hwDiversionIsOn) {
            HWSystem.Heat heat = hwSystem.heatWater(inputRow.mod, previousWaterTemp, feed);
            feed = feed - heat.kWhUsed;
            nowWaterTemp = heat.temperature;
            divertedToWater = heat.kWhUsed;
        }
        outputRow.setFeed(feed);

        outputRow.setWaterTemp(nowWaterTemp);
        outputRow.setKWHDivToWater(divertedToWater);
        outputRow.setImmersionLoad(scheduledWaterLoad);
        outputRow.setKWHDivToEV(divertedToEV);

        // RECORD THE OUTPUT
        outputRows.add(outputRow);
    }

    private static double chargeBatteries(Map<Inverter, InputData> inputDataMap, double charge) {
        double lastSOC = 0;
        double totalChargeCapacity = 0d;
        for (Map.Entry<Inverter, InputData> entry: inputDataMap.entrySet())
            totalChargeCapacity += entry.getValue().getChargeCapacity();

        for (Map.Entry<Inverter, InputData> entry: inputDataMap.entrySet()) {
            InputData iData = entry.getValue();
            double batteryShare = 0;
            if (totalChargeCapacity != 0) batteryShare = iData.getChargeCapacity() / totalChargeCapacity;
            iData.soc += charge * batteryShare * iData.dc2dcLoss;
            lastSOC += iData.soc;
        }

        return lastSOC;
    }

    private static double chargeBatteriesFromGridIfNeeded(Map<Inverter, InputData> inputDataMap, int row) {
        double chargeCapacity;
        double totalExtraLoad = 0;
        for (Map.Entry<Inverter, InputData> entry: inputDataMap.entrySet()) {
            InputData inputData = entry.getValue();
            if (!(null == inputData.mChargeFromGrid)) {
                double stopAtPercentage = inputData.mChargeFromGrid.mStopAt.get(row);
                double stopAt = (stopAtPercentage / 100d) * inputData.mBattery.getBatterySize();
                if (inputData.isCFG(row) && (inputData.soc < stopAt)) {
                    chargeCapacity = inputData.getChargeCapacity();
                    inputData.soc += chargeCapacity;
                    totalExtraLoad += chargeCapacity;
                }
            }
        }
        return totalExtraLoad;
    }

    private static double[] dischargeBatteries(Map<Inverter, InputData> inputDataMap, double discharge, int row) {
        double[] ret = {0,0};
        // Get the current charge landscape inverters with batteries & their current state
        // Cannot discharge a battery that is empty!
        // Allocate % of discharge to each battery
        double totalDischargeCapacity = 0d;
        for (Map.Entry<Inverter, InputData> entry: inputDataMap.entrySet())
            totalDischargeCapacity += entry.getValue().getDischargeCapacity(row);

        for (Map.Entry<Inverter, InputData> entry: inputDataMap.entrySet()) {
            InputData iData = entry.getValue();
            double batteryShare = 0;
            if (totalDischargeCapacity != 0) batteryShare = iData.getDischargeCapacity(row) / totalDischargeCapacity;
            double effectiveDischarge = discharge * batteryShare * (1 + entry.getValue().storageLoss/100d);
            iData.soc -= effectiveDischarge;
            ret[0] += iData.soc;
            ret[1] += effectiveDischarge;
        }

        return ret;
    }

    static class InputData {
        long id;
        double dc2acLoss;
        double ac2dcLoss;
        double dc2dcLoss;
        double storageLoss;
        List<SimulationInputData> inputData;
        Battery mBattery;
        ChargeFromGrid mChargeFromGrid;

        HWSystem mHWSystem;
        Boolean mHWDivert;
        List<HWSchedule> mHWSchedules;

        List<EVCharge> mEVCharges;
        List<EVDivert> mEVDiverts;
        Map<Integer, Double> mEVDivertDailyTotals;

        // Volatile state members
        double soc = 0d;

        InputData(Inverter inverter, List<SimulationInputData> iData,
                  Battery battery, ChargeFromGrid chargeFromGrid,
                  HWSystem hwSystem, Boolean hwDivert, List<HWSchedule> hotWaterSchedules,
                  List<EVCharge> evCharges, List<EVDivert> evDiverts) {
            id = inverter.getInverterIndex();
            dc2acLoss = (100d - inverter.getDc2acLoss()) / 100d;
            ac2dcLoss = (100d - inverter.getAc2dcLoss()) / 100d;
            dc2dcLoss = (100d - inverter.getDc2dcLoss()) / 100d;
            storageLoss = (null == battery) ? 0 : battery.getStorageLoss();
            inputData = iData;
            mBattery = battery;
            mChargeFromGrid = chargeFromGrid;
            mHWSystem = hwSystem;
            mHWDivert = hwDivert;
            mHWSchedules = hotWaterSchedules;
            mEVCharges = evCharges;
            mEVDiverts = evDiverts;
            mEVDivertDailyTotals = new HashMap<>();
        }

        public boolean isHotWaterHeatingScheduled(int dayOfWeek, int monthOfYear, int minuteOfDay) {
            boolean ret = false;
            if (dayOfWeek == 7) dayOfWeek = 0;
            if (!(null == mHWSchedules)) for (HWSchedule hwSchedule: mHWSchedules) {
                if (hwSchedule.getMonths().months.contains(monthOfYear) &&
                    hwSchedule.getDays().ints.contains(dayOfWeek) &&
                    hwSchedule.getBegin() * 60 <= minuteOfDay &&
                    hwSchedule.getEnd() * 60 > minuteOfDay) {
                    ret = true;
                    break; //
                }
            }
            return ret;
        }

        public EVCharge isEVCharging(int dayOfWeek, int monthOfYear, int minuteOfDay) {
            EVCharge ret = null;
            if (dayOfWeek == 7) dayOfWeek = 0;
            if (!(null == mEVCharges)) for (EVCharge evCharge: mEVCharges) {
                if (evCharge.getMonths().months.contains(monthOfYear) &&
                        evCharge.getDays().ints.contains(dayOfWeek) &&
                        evCharge.getBegin() * 60 <= minuteOfDay &&
                        evCharge.getEnd() * 60 > minuteOfDay) {
                    ret = evCharge;
                    break; //
                }
            }
            return ret;
        }

        public EVDivert getEVDivertOrNull(int dayOfWeek, int monthOfYear, int minuteOfDay) {
            EVDivert ret = null;
            if (dayOfWeek == 7) dayOfWeek = 0;
            if (!(null == mEVDiverts)) for (EVDivert evDivert: mEVDiverts) {
                if (evDivert.getMonths().months.contains(monthOfYear) &&
                        evDivert.getDays().ints.contains(dayOfWeek) &&
                        evDivert.getBegin() * 60 <= minuteOfDay &&
                        evDivert.getEnd() * 60 > minuteOfDay) {
                    ret = evDivert;
                    break; //
                }
            }
            return ret;
        }

        public double getDischargeStop() {
            return (mBattery.getDischargeStop() / 100d) * mBattery.getBatterySize();
        }

        public double getChargeCapacity() {
            return min((mBattery.getBatterySize() - soc),
                    InputData.getMaxChargeForSOC(soc, mBattery));
        }

        public double getDischargeCapacity(int row) {
            if (isCFG(row)) return 0D;
            else return min(mBattery.getMaxDischarge(),
                    max(0, (soc - getDischargeStop() )));
        }

        public boolean isCFG(int row) {
            boolean cfg = false;
            if (!(null == mChargeFromGrid)) cfg = mChargeFromGrid.mCFG.get(row);
            return cfg;
        }

        public static double getMaxChargeForSOC(double batterySOC, Battery battery) {
            double ret = 0;
            if (null == battery) return ret;
            ChargeModel cm = battery.getChargeModel();
            double maxCharge = battery.getMaxCharge();
            double batteryPercentSOC = (batterySOC / battery.getBatterySize()) * 100d;
            if (batteryPercentSOC <= 12) ret = (maxCharge * cm.percent0) / 100d;
            if (batteryPercentSOC <= 90) ret = (maxCharge * cm.percent12) / 100d;
            if (batteryPercentSOC > 90) ret = (maxCharge * cm.percent90) / 100d;
            if (batteryPercentSOC == 100) ret = (maxCharge * cm.percent100) / 100d;
            return ret;
        }
    }

    public static class ChargeFromGrid {

        List<Boolean> mCFG;
        List<Double> mStopAt;

        public ChargeFromGrid(List<LoadShift> loadShifts, int rowsToProcess) {
            mCFG = new ArrayList<>(Collections.nCopies(rowsToProcess, false));
            mStopAt = new ArrayList<>(Collections.nCopies(rowsToProcess, 0D));
            Map<Integer, List<LoadShift>> groupedLoadShifts = sortLoadShifts(loadShifts);
            populateCFG(groupedLoadShifts);
        }

        private void populateCFG(Map<Integer, List<LoadShift>> groupedLoadShifts) {
            LocalDateTime active = LocalDateTime.of(2001, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2002, 1, 1, 0, 0);
            int row = 0;
            while (active.isBefore(end)) {
                int month = active.getMonthValue();
                int day = active.getDayOfWeek().getValue();
                if (day == 7) day = 0;
                for (Map.Entry<Integer, List<LoadShift>> aGroup: groupedLoadShifts.entrySet()) {
                    if (!(null == aGroup.getValue()) && !aGroup.getValue().isEmpty() ) {
                        if (aGroup.getValue().get(0).getDays().ints.contains(day) &&
                                aGroup.getValue().get(0).getMonths().months.contains(month)) {
                            int hour = active.getHour();
                            for (LoadShift loadShift : aGroup.getValue()) {
                                if ((loadShift.getBegin() <= hour) && (hour <= loadShift.getEnd()) ) {
                                    mCFG.set(row, true);
                                    mStopAt.set(row, loadShift.getStopAt());
                                    break; // One true is enough
                                }
                            }
                        }
                    }
                }
                active = active.plusMinutes(5);
                row++;
            }
        }

        private static Map<Integer, List<LoadShift>> sortLoadShifts(List<LoadShift> loadShifts) {
            Map<Integer, List<LoadShift>> groupedLoadShifts = new HashMap<>();
            Integer maxKey = null;
            for (LoadShift loadShift : loadShifts) {
                boolean sorted = false;
                for (Map.Entry<Integer, List<LoadShift>> tabContent: groupedLoadShifts.entrySet()) {
                    if (maxKey == null) maxKey = tabContent.getKey();
                    else if (maxKey < tabContent.getKey()) maxKey = tabContent.getKey();
                    if (tabContent.getValue().get(0) != null) {
                        if (tabContent.getValue().get(0).equalDateAndInverter(loadShift)) {
                            tabContent.getValue().add(loadShift);
                            sorted = true;
                            break; // stop looking in the map, exit inner loop
                        }
                    }
                }
                if (!sorted){
                    if (null == maxKey) maxKey = 0;
                    List<LoadShift> newGroupLoadShifts = new ArrayList<>();
                    newGroupLoadShifts.add(loadShift);
                    groupedLoadShifts.put(maxKey, newGroupLoadShifts);
                    maxKey++;
                }
            }
            return groupedLoadShifts;
        }
    }
}
