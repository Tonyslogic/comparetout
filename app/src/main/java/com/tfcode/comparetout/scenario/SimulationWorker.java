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

package com.tfcode.comparetout.scenario;

import static com.tfcode.comparetout.MainActivity.CHANNEL_ID;
import static java.lang.Double.max;
import static java.lang.Double.min;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.ChargeModel;
import com.tfcode.comparetout.model.scenario.DischargeToGrid;
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

/**
 * SimulationWorker is a background Worker that simulates all scenarios requiring simulation.
 * It orchestrates the retrieval of scenario components, runs the simulation logic, manages notifications,
 * and persists simulation results. The simulation models energy flows, battery usage, PV generation,
 * and user-configured diversions for each scenario.
 */
public class SimulationWorker extends Worker {

    private final ToutcRepository mToutcRepository;
    private final Context mContext;
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

    /**
     * Constructs a SimulationWorker.
     * @param context The application context.
     * @param workerParams Worker parameters.
     */
    public SimulationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mToutcRepository = new ToutcRepository((Application) context);
        mContext = context;
    }

    /**
     * Called when the worker is stopped. Used for cleanup if needed.
     */
    @Override
    public void onStopped(){
        super.onStopped();
    }

    /**
     * Main entry point for the simulation work.
     * Retrieves all scenarios needing simulation, processes each scenario by:
     * - Gathering scenario components and user inputs
     * - Running the simulation for each time step
     * - Managing progress notifications
     * - Saving simulation results
     * Returns success or failure based on execution.
     */
    @NonNull
    @Override
    public Result doWork() {
        List<Long> scenarioIDs = mToutcRepository.getAllScenariosThatNeedSimulation();

        Context context = getApplicationContext();
        String  title= context.getString(R.string.simulate_notification_title);
        String text = context.getString(R.string.simulate_notification_text);

        try {
            if (!scenarioIDs.isEmpty()) {
                /*
                 * NOTIFICATION SETUP
                 * Set up notification infrastructure to provide user feedback on simulation progress.
                 * Progress is updated as each scenario is processed.
                 */
                int notificationId = 1;
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
                NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID);
                builder.setContentTitle(title)
                        .setContentText(text)
                        .setSmallIcon(R.drawable.housetick)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setTimeoutAfter(20000)
                        .setSilent(true);
                // Issue the initial notification with zero progress
                int PROGRESS_MAX = 100;
                int PROGRESS_CURRENT = 0;
                int PROGRESS_CHUNK = PROGRESS_MAX;
                if (!scenarioIDs.isEmpty()) {
                    PROGRESS_CHUNK = PROGRESS_MAX / (scenarioIDs.size() + 1);
                    builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
                    sendNotification(notificationManager, notificationId, builder);
                }

                long notifyTime = System.nanoTime();
                for (long scenarioID : scenarioIDs) {
                    /*
                     * SCENARIO COMPONENT RETRIEVAL
                     * Retrieve all relevant components for the scenario (inverters, batteries, panels, etc.).
                     * This ensures the simulation is based on the latest user configuration.
                     */
                    ScenarioComponents scenarioComponents = mToutcRepository.getScenarioComponentsForScenarioID(scenarioID);
                    double exportMax = scenarioComponents.loadProfile.getGridExportMax();
                    Scenario scenario = scenarioComponents.scenario;
                    if (scenario.isHasPanels()) {
                        /*
                         * PANEL DATA CHECK
                         * Ensure all required panel data is present before simulation.
                         * If data is missing, skip simulation for this scenario.
                         */
                        boolean hasData = mToutcRepository.checkForMissingPanelData(scenarioID);
                        // NOTIFICATION PROGRESS
                        PROGRESS_CURRENT += PROGRESS_CHUNK;
                        builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
                        if (System.nanoTime() - notifyTime > 1e+9) {
                            notifyTime = System.nanoTime();
                            sendNotification(notificationManager, notificationId, builder);
                        }
                        if (!hasData) {
                            builder.setContentText("Skipping " + scenario.getScenarioName());
                            notificationManager.notify(notificationId, builder.build());
                            PROGRESS_CURRENT += PROGRESS_CHUNK;
                            builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
                            if (System.nanoTime() - notifyTime > 1e+9) {
                                notifyTime = System.nanoTime();
                                sendNotification(notificationManager, notificationId, builder);
                            }
                            continue;
                        }
                    }
                    builder.setContentText("Getting data: " + scenario.getScenarioName());
                    if (System.nanoTime() - notifyTime > 1e+9) {
                        notifyTime = System.nanoTime();
                        sendNotification(notificationManager, notificationId, builder);
                    }

                    int rowsToProcess = 0;
                    Map<Inverter, InputData> inputDataMap = new HashMap<>();

                    /*
                     * INPUT DATA PREPARATION
                     * For each inverter, gather simulation input data (load, PV, battery, schedules, etc.).
                     * This block builds the InputData map, which centralizes all scenario factors for simulation.
                     */
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
                            ForceDischargeToGrid connectedDischarge = null;
                            ChargeFromGrid chargeFromGrid = null;
                            if (scenario.isHasBatteries()) {
                                for (Battery battery : scenarioComponents.batteries)
                                    if (battery.getInverter().equals(inverter.getInverterName()))
                                        connectedBattery = battery;
                                if (scenario.isHasLoadShifts()) {
                                    chargeFromGrid = new ChargeFromGrid(scenarioComponents.loadShifts, rowsToProcess);
                                }
                                if (scenario.isHasDischarges()) {
                                    List<DischargeToGrid> connectedDischarges = new ArrayList<>();
                                    for (DischargeToGrid dischargeToGrid : scenarioComponents.discharges) {
                                        if (dischargeToGrid.getInverter().equals(inverter.getInverterName()))
                                            connectedDischarges.add(dischargeToGrid);
                                    }
                                    if (!(connectedDischarges.isEmpty()))
                                        connectedDischarge = new ForceDischargeToGrid(connectedDischarges, rowsToProcess);
                                }
                            }
                            // Get the hot water related components
                            HWSystem configuredHotWater = null;
                            boolean hotWaterDivert = false;
                            List<HWSchedule> hotWaterSchedules = null;
                            if (scenario.isHasHWSystem()) {
                                configuredHotWater = scenarioComponents.hwSystem;
                                if (scenario.isHasHWDivert()) hotWaterDivert = scenarioComponents.hwDivert.isActive();
                                if (scenario.isHasHWSchedules() && !scenarioComponents.hwSchedules.isEmpty()) hotWaterSchedules = scenarioComponents.hwSchedules;
                            }
                            // Get the EV related components
                            List<EVCharge> evCharges = null;
                            List<EVDivert> evDiverts = null;
                            if (scenario.isHasEVCharges() && !scenarioComponents.evCharges.isEmpty()) evCharges = scenarioComponents.evCharges;
                            if (scenario.isHasEVDivert() && !scenarioComponents.evDiverts.isEmpty()) evDiverts = scenarioComponents.evDiverts;
                            // Associate the inverter and the load for use in simulation
                            InputData iData = new InputData(inverter, simulationInputData,
                                    connectedBattery, chargeFromGrid,
                                    configuredHotWater, hotWaterDivert, hotWaterSchedules,
                                    evCharges, evDiverts, connectedDischarge, exportMax);
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
                            if (scenario.isHasHWSchedules() && !scenarioComponents.hwSchedules.isEmpty()) hotWaterSchedules = scenarioComponents.hwSchedules;
                        }
                        List<EVCharge> evCharges = null;
                        if (scenario.isHasEVCharges() && !scenarioComponents.evCharges.isEmpty()) evCharges = scenarioComponents.evCharges;
                        InputData idata = new InputData(inverter, mToutcRepository.getSimulationInputNoSolar(scenarioID),
                                null, null,
                                configuredHotWater, null, hotWaterSchedules,
                                evCharges, null, null, exportMax);
                        inputDataMap.put(inverter, idata);
                        rowsToProcess = idata.simulationInputData.size();
                    }

                    builder.setContentText("Simulating: " + scenario.getScenarioName());
                    if (System.nanoTime() - notifyTime > 1e+9) {
                        notifyTime = System.nanoTime();
                        sendNotification(notificationManager, notificationId, builder);
                    }

                    /*
                     * SIMULATION EXECUTION
                     * For each time step, run the simulation logic to model energy flows, battery usage,
                     * and diversions. Results are collected for later storage.
                     */
                    ArrayList<ScenarioSimulationData> outputRows = new ArrayList<>();
                    for (int row = 0; row < rowsToProcess; row++) {
                        processOneRow(scenarioID, outputRows, row, inputDataMap);
                    }

                    /*
                     * RESULT STORAGE
                     * Save the simulation results for this scenario to the database.
                     * This makes the results available for user review and further analysis.
                     */
                    builder.setContentText("Saving data");
                    if (System.nanoTime() - notifyTime > 1e+9) {
                        notifyTime = System.nanoTime();
                        sendNotification(notificationManager, notificationId, builder);
                    }

                    mToutcRepository.saveSimulationDataForScenario(outputRows);

                    // NOTIFICATION PROGRESS
                    PROGRESS_CURRENT += PROGRESS_CHUNK;
                    builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
                    if (System.nanoTime() - notifyTime > 1e+9) {
                        notifyTime = System.nanoTime();
                        sendNotification(notificationManager, notificationId, builder);
                    }
                }

                /*
                 * NOTIFICATION COMPLETE
                 * Notify the user that all simulations are complete.
                 */
                if (!scenarioIDs.isEmpty()) {
                    builder.setContentText("Simulation complete")
                            .setProgress(0, 0, false);
                    sendNotification(notificationManager, notificationId, builder);
                }
            }
        }
        catch (Exception e) {
            /*
             * ERROR HANDLING
             * If any exception occurs during simulation, log the error and mark the work as failed.
             */
            System.out.println("!!!!!!!!!!!!!!!!!!! SimulationWorker has crashed, marking as failure !!!!!!!!!!!!!!!!!!!!!");
            e.printStackTrace();
            System.out.println("!!!!!!!!!!!!!!!!!!! SimulationWorker has crashed, marking as failure !!!!!!!!!!!!!!!!!!!!!");
            return Result.failure();
        }
        return Result.success();
    }

    /**
     * Merges PV (photovoltaic) data into the simulation input data for each time step.
     * Handles daylight saving time adjustments by shifting PV data as needed.
     * @param rowsToProcess Number of time steps to process.
     * @param simulationInputData List of simulation input data objects.
     * @param inverterPV List of PV values for the inverter.
     */
    private void mergePVWithSimulationInputData(int rowsToProcess, List<SimulationInputData> simulationInputData, List<Double> inverterPV) {
        for (int row = 0; row < rowsToProcess; row++) {
            if (row < DST_BEGIN)
                simulationInputData.get(row).setTpv(inverterPV.get(row));
            if (DST_BEGIN <= row &&  row <= DST_END)
                simulationInputData.get(row).setTpv(0);
            if (row > DST_END) {
                simulationInputData.get(row).setTpv(inverterPV.get(row - 12));
            }
        }
    }

    /**
     * Aggregates PV generation for a given inverter by summing or maximizing panel outputs
     * depending on connection mode (parallel or optimized).
     * @param scenarioComponents Scenario components containing panels.
     * @param rowsToProcess Number of time steps.
     * @param inverter The inverter to aggregate PV for.
     * @param inverterPV Output list to store aggregated PV values.
     */
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

    /**
     * Processes a single simulation time step for all inverters in the scenario.
     * Calculates energy flows (PV, battery, grid, diversions), updates state of charge,
     * and records the results for later storage.
     * @param scenarioID The scenario ID.
     * @param outputRows List to append simulation output data.
     * @param row The time step index.
     * @param inputDataMap Map of inverters to their input data and state.
     */
    public static void processOneRow(long scenarioID, ArrayList<ScenarioSimulationData> outputRows, int row, Map<Inverter, InputData> inputDataMap) {

        /*
         * INPUT AND STATE INITIALIZATION
         * Set up references to input data, hot water and EV configuration, and initialize
         * battery state of charge (SOC) and other per-row variables.
         */
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
                if (null == inputRow) inputRow = iData.simulationInputData.get(row);
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
                inputLoad += iData.simulationInputData.get(row).getLoad();
            }
        }
        else { // Set initial SOC
            for (Map.Entry<Inverter, InputData> entry: inputDataMap.entrySet()) {
                InputData iData = entry.getValue();
                if (null == firstInputData) firstInputData = iData;
                if (null == inputRow) inputRow = iData.simulationInputData.get(row);
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
                inputLoad += iData.simulationInputData.get(row).getLoad();
            }
        }
        if (null == inputRow) return;

        /*
          PV AGGREGATION
          Calculate total and effective PV for all inverters, accounting for inverter losses.
          This value is used to determine available local energy for the time step.
         */
        double tPV = 0;
        double effectivePV = 0;
        for (Map.Entry<Inverter,InputData> entry: inputDataMap.entrySet()) {
            SimulationInputData sid = entry.getValue().simulationInputData.get(row);
            tPV += sid.tpv;
            effectivePV += sid.tpv * entry.getValue().dc2acLoss;
        }

        /*
          GRID CHARGING (LOAD SHIFT)
          If grid charging is scheduled, charge batteries from the grid and update the extra load.
          This models user-configured load shifting behavior.
         */
        double locallyAvailable = effectivePV + batteryAvailableForDischarge;
        double purchaseShiftingLoad = chargeBatteriesFromGridIfNeeded(inputDataMap, row);

        /*
         * OUTPUT DATA INITIALIZATION
         * Prepare the output data structure for this time step, copying over basic input values.
         */
        ScenarioSimulationData outputRow = new ScenarioSimulationData();
        outputRow.setScenarioID(scenarioID);
        outputRow.setDate(inputRow.getDate());
        outputRow.setMinuteOfDay(inputRow.getMod());
        outputRow.setDayOfWeek(inputRow.getDow());
        outputRow.setDayOf2001((inputRow.getDo2001()));
        outputRow.setGridToBattery(purchaseShiftingLoad);

        /*
         * SCHEDULED EV AND HOT WATER LOADS
         * Determine if scheduled EV charging or hot water heating should be applied for this time step,
         * and update the load accordingly.
         */
        int month = Integer.parseInt(inputRow.getDate().split("-")[1]);

        EVCharge evCharge = firstInputData.isEVCharging(inputRow.getDow(), month, inputRow.mod);
        double scheduledEVChargeLoad = 0;
        if (!(null == evCharge)) scheduledEVChargeLoad = evCharge.getDraw() / 12d;
        outputRow.setDirectEVcharge(scheduledEVChargeLoad);

        double previousWaterTemp = 0;
        double scheduledWaterLoad = 0;
        if (!outputRows.isEmpty()) previousWaterTemp = outputRows.get(row -1).getWaterTemp();
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

        /*
         * ENERGY FLOW SIMULATION
         * Simulate the flow of energy for this time step:
         * - If load exceeds local supply, buy from grid and discharge batteries as needed.
         * - If local supply is sufficient, use PV and batteries to meet load, and charge batteries/feed excess.
         * - Update battery SOC and record energy flows.
         */
        double buy = purchaseShiftingLoad;
        double feed = 0;
        double b2g = 0;
        double pv2charge = 0;
        double pv2load;
        double bat2Load = 0;
        double totalSOC;

        if (inputLoad >= locallyAvailable) {
            buy += inputLoad - locallyAvailable;
            pv2load = effectivePV;
            double [] discharge = dischargeBatteries(inputDataMap, batteryAvailableForDischarge, row);
            totalSOC = discharge[0];
            bat2Load = discharge[1];
        }
        else { // we cover the load without the grid
            if (inputLoad >= effectivePV) {
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
                    ScenarioSimulationData scenarioSimulationData = null;
                    if (row > 0) scenarioSimulationData = outputRows.get(row - 1);
                    if (!(null == scenarioSimulationData)) totalSOC = scenarioSimulationData.getSOC();
                    else totalSOC = firstInputData.soc;
                }
            }
        }

        outputRow.setBuy(buy);
        outputRow.setPvToCharge(pv2charge);
        outputRow.setPvToLoad(pv2load);
        outputRow.setBatToLoad(bat2Load);

        /*
         * DIVERSIONS (EV AND HOT WATER)
         * If excess PV is available, divert it to EV charging or hot water heating as per user schedules.
         * This models user-configured diversion priorities and daily limits.
         */
        double divertedToWater = 0;
        double divertedToEV = 0;
        // ELECTRIC VEHICLE
        double totalEVDivertedThisDay = firstInputData.mEVDivertDailyTotals.computeIfAbsent(inputRow.do2001, k -> 0D);
        EVDivert evDivert = firstInputData.getEVDivertOrNull(inputRow.getDow(), month, inputRow.mod);
        if (!(null == evDivert)) {
            double maxEVDivert = evDivert.getDailyMax() - totalEVDivertedThisDay;
            if (evDivert.isActive()) {
                if (evDivert.isEv1st()) {
                    if (feed > evDivert.getMinimum()/12d) {
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
                        if (feed > evDivert.getMinimum()/12d) { // there may be some feed left fo the EV
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
        outputRow.setWaterTemp(nowWaterTemp);
        outputRow.setKWHDivToWater(divertedToWater);
        outputRow.setImmersionLoad(scheduledWaterLoad);
        outputRow.setKWHDivToEV(divertedToEV);

        /*
         * FORCED DISCHARGE TO GRID
         * If forced discharge to grid is scheduled, discharge batteries to the grid up to the allowed export limit.
         * This models user-configured forced export events.
         */
        double maxExport = firstInputData.exportMax / 12D; // 5 minutes
        double availableExport = Math.max(0d, maxExport - feed);
        // Which inverters/batteries are set to discharge now
        for (Map.Entry<Inverter, InputData> entry: inputDataMap.entrySet()) {
            InputData inputData = entry.getValue();
            ForceDischargeToGrid forceDischargeToGrid = inputData.mForceDischargeToGrid;
            if (!(null == forceDischargeToGrid) && !(null == forceDischargeToGrid.mD2G) && forceDischargeToGrid.mD2G.get(row)){
                double dischargeStopPercent = forceDischargeToGrid.mStopAt.get(row); // %
                double dischargeStopKWh = (dischargeStopPercent / 100d) * inputData.mBattery.getBatterySize();
                double currentSOC = inputData.soc; // kWh

                // Discharge if there is capacity to export and the battery soc is above reserve
                if (availableExport > 0 && currentSOC > dischargeStopKWh) {
                    double wantedDischargeRate = forceDischargeToGrid.mRate.get(row) / 12D; // kW 5 minutes, max
                    double batteryAvailable = Math.max(0d, currentSOC - dischargeStopKWh);
                    double amountToDischarge = Math.min(Math.min(availableExport, batteryAvailable), wantedDischargeRate);
                    double discharge = forceDischargeBatteries(inputData, amountToDischarge);

                    totalSOC -= discharge;
                    b2g += discharge;
                    availableExport -= discharge;
                    availableExport = Math.max(0d, availableExport);
                    feed = feed + b2g;
                }
            }
        }

        outputRow.setBattery2Grid(b2g);
        outputRow.setSOC(totalSOC);
        outputRow.setFeed(feed);

        /*
         * FINALIZE OUTPUT
         * Record the results for this time step, including all calculated flows and updated SOC.
         */
        outputRows.add(outputRow);
    }

    /**
     * Sends a notification using the provided NotificationManager and builder.
     * Used to update the user on simulation progress.
     * @param notificationManager The NotificationManagerCompat instance.
     * @param notificationId The notification ID.
     * @param builder The notification builder.
     */
    private void sendNotification(NotificationManagerCompat notificationManager, int notificationId, NotificationCompat.Builder builder) {
        if (ActivityCompat.checkSelfPermission(
                mContext, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        notificationManager.notify(notificationId, builder.build());
    }

    /**
     * Charges batteries in the scenario by distributing the available charge proportionally
     * based on each battery's capacity. Updates state of charge for each battery.
     * @param inputDataMap Map of inverters to their input data.
     * @param charge Total charge to distribute (kWh).
     * @return The sum of all batteries' state of charge after charging.
     */
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

    /**
     * Charges batteries from the grid if scheduled and needed, based on user load shift schedules.
     * Only charges batteries that are below their stop threshold.
     * @param inputDataMap Map of inverters to their input data.
     * @param row The time step index.
     * @return The total extra load added from grid charging.
     */
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

    /**
     * Discharges batteries to meet load, distributing the discharge proportionally
     * based on each battery's available discharge capacity.
     * Updates state of charge for each battery.
     * @param inputDataMap Map of inverters to their input data.
     * @param discharge Total discharge required (kWh).
     * @param row The time step index.
     * @return Array: [total SOC after discharge, total discharge amount].
     */
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

    /**
     * Forces discharge of a single battery by a specified amount, used for forced export to grid.
     * Applies storage loss to the discharge.
     * @param inputData The input data for the inverter/battery.
     * @param amountToDischarge The amount to discharge (kWh).
     * @return The effective discharge applied.
     */
    private static double forceDischargeBatteries(InputData inputData, double amountToDischarge) {
        double ret;
        double effectiveDischarge = amountToDischarge * (1 + inputData.storageLoss/100d);
        inputData.soc -= effectiveDischarge;
        ret = effectiveDischarge;

        return ret;
    }

    /**
     * InputData encapsulates all relevant data and state for a single inverter during simulation.
     * It aggregates user-configured scenario inputs (battery, hot water, EV, schedules, etc.)
     * and maintains the current simulation state (e.g., state of charge).
     * This class is essential for pulling together all factors that impact the simulation
     * as input by the user for the given scenario, enabling the simulation engine to
     * reason about each inverter's context and constraints at every time step.
     */
    static class InputData {
        long id;
        double dc2acLoss;
        double ac2dcLoss;
        double dc2dcLoss;
        double storageLoss;
        double exportMax;
        List<SimulationInputData> simulationInputData;
        Battery mBattery;
        ChargeFromGrid mChargeFromGrid;
        ForceDischargeToGrid mForceDischargeToGrid;

        HWSystem mHWSystem;
        Boolean mHWDivert;
        List<HWSchedule> mHWSchedules;

        List<EVCharge> mEVCharges;
        List<EVDivert> mEVDiverts;
        Map<Integer, Double> mEVDivertDailyTotals;

        // Volatile state members
        double soc = 0d;

        /**
         * Constructs InputData for an inverter and its associated scenario components.
         * Gathers all user-configured factors (battery, hot water, EV, schedules, etc.)
         * that affect simulation outcomes for this inverter.
         * @param inverter The inverter.
         * @param iData Simulation input data (load, PV, etc.).
         * @param battery Associated battery (nullable).
         * @param chargeFromGrid Charge from grid schedule (nullable).
         * @param hwSystem Hot water system (nullable).
         * @param hwDivert Hot water divert flag (nullable).
         * @param hotWaterSchedules Hot water schedules (nullable).
         * @param evCharges EV charge schedules (nullable).
         * @param evDiverts EV divert schedules (nullable).
         * @param forceDischargeToGrid Forced discharge schedule (nullable).
         * @param exportMax Maximum export value for this inverter.
         */
        InputData(Inverter inverter, List<SimulationInputData> iData,
                  Battery battery, ChargeFromGrid chargeFromGrid,
                  HWSystem hwSystem, Boolean hwDivert, List<HWSchedule> hotWaterSchedules,
                  List<EVCharge> evCharges, List<EVDivert> evDiverts, ForceDischargeToGrid forceDischargeToGrid, double exportMax) {
            id = inverter.getInverterIndex();
            dc2acLoss = (100d - inverter.getDc2acLoss()) / 100d;
            ac2dcLoss = (100d - inverter.getAc2dcLoss()) / 100d;
            dc2dcLoss = (100d - inverter.getDc2dcLoss()) / 100d;
            this.exportMax = exportMax;
            storageLoss = (null == battery) ? 0 : battery.getStorageLoss();
            simulationInputData = iData;
            mBattery = battery;
            mChargeFromGrid = chargeFromGrid;
            mForceDischargeToGrid = forceDischargeToGrid;
            mHWSystem = hwSystem;
            mHWDivert = hwDivert;
            mHWSchedules = hotWaterSchedules;
            mEVCharges = evCharges;
            mEVDiverts = evDiverts;
            mEVDivertDailyTotals = new HashMap<>();
        }

        /**
         * Determines if hot water heating is scheduled for the given time.
         * Used to decide if immersion heating should be active in the simulation.
         * @param dayOfWeek Day of week (0=Sunday).
         * @param monthOfYear Month of year.
         * @param minuteOfDay Minute of day.
         * @return true if heating is scheduled, false otherwise.
         */
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

        /**
         * Checks if EV charging is scheduled for the given time.
         * Used to determine if scheduled EV charging load should be applied.
         * @param dayOfWeek Day of week (0=Sunday).
         * @param monthOfYear Month of year.
         * @param minuteOfDay Minute of day.
         * @return The EVCharge if scheduled, null otherwise.
         */
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

        /**
         * Gets the EVDivert scheduled for the given time, if any.
         * Used to determine if excess PV should be diverted to EV charging.
         * @param dayOfWeek Day of week (0=Sunday).
         * @param monthOfYear Month of year.
         * @param minuteOfDay Minute of day.
         * @return The EVDivert if scheduled, null otherwise.
         */
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

        /**
         * Gets the discharge stop threshold for the battery, in kWh.
         * Used to prevent battery from discharging below user reserve.
         * @return The discharge stop value in kWh.
         */
        public double getDischargeStop() {
            return (mBattery.getDischargeStop() / 100d) * mBattery.getBatterySize();
        }

        /**
         * Gets the available charge capacity for the battery at the current SOC.
         * Used to limit charging to battery's constraints.
         * @return The charge capacity in kWh.
         */
        public double getChargeCapacity() {
            if (null == mBattery) return 0D;
            return min((mBattery.getBatterySize() - soc),
                    InputData.getMaxChargeForSOC(soc, mBattery));
        }

        /**
         * Gets the available discharge capacity for the battery at the given time step.
         * Used to limit discharging to battery's constraints and grid charging status.
         * @param row The time step index.
         * @return The discharge capacity in kWh.
         */
        public double getDischargeCapacity(int row) {
            if (isCFG(row)) return 0D;
            if (null == mBattery) return 0D;
            else return min(mBattery.getMaxDischarge(),
                    max(0, (soc - getDischargeStop() )));
        }

        /**
         * Checks if charging from grid is scheduled at the given time step.
         * Used to determine if battery should be charged from grid.
         * @param row The time step index.
         * @return true if charging from grid is scheduled, false otherwise.
         */
        public boolean isCFG(int row) {
            boolean cfg = false;
            if (!(null == mChargeFromGrid)) cfg = mChargeFromGrid.mCFG.get(row);
            return cfg;
        }

        /**
         * Gets the maximum charge allowed for the battery at the given SOC.
         * Used to model battery charge tapering as it fills.
         * @param batterySOC The current state of charge.
         * @param battery The battery.
         * @return The maximum charge allowed in kWh.
         */
        public static double getMaxChargeForSOC(double batterySOC, Battery battery) {
            double ret = 0;
            if (null == battery) return ret;
            ChargeModel cm = battery.getChargeModel();
            double maxCharge = battery.getMaxCharge();
            double batteryPercentSOC = (batterySOC / battery.getBatterySize()) * 100d;
            if (batteryPercentSOC <= 12) ret = (maxCharge * cm.percent0) / 100d;
            else if (batteryPercentSOC <= 90) ret = (maxCharge * cm.percent12) / 100d;
            if (batteryPercentSOC > 90) ret = (maxCharge * cm.percent90) / 100d;
            if (batteryPercentSOC == 100) ret = (maxCharge * cm.percent100) / 100d;
            return ret;
        }
    }

    /**
     * ChargeFromGrid manages the schedule for charging batteries from the grid,
     * based on user-configured load shift schedules. It determines, for each time step,
     * whether grid charging is active and the stop threshold.
     */
    public static class ChargeFromGrid {

        List<Boolean> mCFG;
        List<Double> mStopAt;

        /**
         * Constructs a ChargeFromGrid schedule from load shifts.
         * @param loadShifts List of load shift schedules.
         * @param rowsToProcess Number of time steps.
         */
        public ChargeFromGrid(List<LoadShift> loadShifts, int rowsToProcess) {
            if (rowsToProcess == 0) {
                mCFG = new ArrayList<>();
                mStopAt = new ArrayList<>();
                return;
            }
            mCFG = new ArrayList<>(Collections.nCopies(rowsToProcess, false));
            mStopAt = new ArrayList<>(Collections.nCopies(rowsToProcess, 0D));
            Map<Integer, List<LoadShift>> groupedLoadShifts = sortLoadShifts(loadShifts);
            populateCFG(groupedLoadShifts);
        }

        /**
         * Populates the charge from grid schedule based on grouped load shifts.
         * For each time step, sets whether grid charging is active and the stop threshold.
         * @param groupedLoadShifts Map of grouped load shifts.
         */
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

        /**
         * Groups load shifts by date and inverter for efficient schedule lookup.
         * @param loadShifts List of load shifts.
         * @return Map of grouped load shifts.
         */
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

    /**
     * ForceDischargeToGrid manages the schedule for forced battery discharge to the grid,
     * based on user-configured discharge schedules. For each time step, it determines
     * whether forced discharge is active, the stop threshold, and the discharge rate.
     */
    public static class ForceDischargeToGrid {

        List<Boolean> mD2G;
        List<Double> mStopAt;
        List<Double> mRate;

        /**
         * Constructs a ForceDischargeToGrid schedule from discharge schedules.
         * @param dischargeToGrids List of discharge to grid schedules.
         * @param rowsToProcess Number of time steps.
         */
        public ForceDischargeToGrid(List<DischargeToGrid> dischargeToGrids, int rowsToProcess) {
            mD2G = new ArrayList<>(Collections.nCopies(rowsToProcess, false));
            mStopAt = new ArrayList<>(Collections.nCopies(rowsToProcess, 0D));
            mRate = new ArrayList<>(Collections.nCopies(rowsToProcess, 0D));
            Map<Integer, List<DischargeToGrid>> groupedDischarges = sortDischarges(dischargeToGrids);
            populateFD2G(groupedDischarges);
        }

        /**
         * Populates the forced discharge schedule based on grouped discharges.
         * For each time step, sets whether forced discharge is active, the stop threshold, and rate.
         * @param groupedDischarges Map of grouped discharges.
         */
        private void populateFD2G(Map<Integer, List<DischargeToGrid>> groupedDischarges) {
            LocalDateTime active = LocalDateTime.of(2001, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2002, 1, 1, 0, 0);
            int row = 0;
            while (active.isBefore(end)) {
                int month = active.getMonthValue();
                int day = active.getDayOfWeek().getValue();
                if (day == 7) day = 0;
                for (Map.Entry<Integer, List<DischargeToGrid>> aGroup: groupedDischarges.entrySet()) {
                    if (!(null == aGroup.getValue()) && !aGroup.getValue().isEmpty() ) {
                        if (aGroup.getValue().get(0).getDays().ints.contains(day) &&
                                aGroup.getValue().get(0).getMonths().months.contains(month)) {
                            int hour = active.getHour();
                            for (DischargeToGrid discharge : aGroup.getValue()) {
                                if ((discharge.getBegin() <= hour) && (hour <= discharge.getEnd()) ) {
                                    mD2G.set(row, true);
                                    // There could be multiple entries (connected batteries) for this inverter
                                    // They may have different rates and stopAts, but we just pick the biggest
                                    double oldStop = mStopAt.get(row);
                                    double oldRate = mRate.get(row);
                                    mStopAt.set(row, Math.max(discharge.getStopAt(), oldStop));
                                    mRate.set(row, Math.max(discharge.getRate(), oldRate));
                                }
                            }
                        }
                    }
                }
                active = active.plusMinutes(5);
                row++;
            }
        }

        /**
         * Groups discharge schedules by date and inverter for efficient schedule lookup.
         * @param dischargeToGrids List of discharge to grid schedules.
         * @return Map of grouped discharges.
         */
        private static Map<Integer, List<DischargeToGrid>> sortDischarges(List<DischargeToGrid> dischargeToGrids) {
            Map<Integer, List<DischargeToGrid>> groupedDischarges = new HashMap<>();
            Integer maxKey = null;
            for (DischargeToGrid discharge : dischargeToGrids) {
                boolean sorted = false;
                for (Map.Entry<Integer, List<DischargeToGrid>> tabContent: groupedDischarges.entrySet()) {
                    if (maxKey == null) maxKey = tabContent.getKey();
                    else if (maxKey < tabContent.getKey()) maxKey = tabContent.getKey();
                    if (tabContent.getValue().get(0) != null) {
                        if (tabContent.getValue().get(0).equalDateAndInverter(discharge)) {
                            tabContent.getValue().add(discharge);
                            sorted = true;
                            break; // stop looking in the map, exit inner loop
                        }
                    }
                }
                if (!sorted){
                    if (null == maxKey) maxKey = 0;
                    List<DischargeToGrid> newGroupDischarges = new ArrayList<>();
                    newGroupDischarges.add(discharge);
                    groupedDischarges.put(maxKey, newGroupDischarges);
                    maxKey++;
                }
            }
            return groupedDischarges;
        }
    }
}
