/*
 * Copyright (c) 2023-2026. Tony Finnerty
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
import com.tfcode.comparetout.model.scenario.DischargeToGrid;
import com.tfcode.comparetout.model.scenario.EVCharge;
import com.tfcode.comparetout.model.scenario.EVDivert;
import com.tfcode.comparetout.model.scenario.HWSchedule;
import com.tfcode.comparetout.model.scenario.HWSystem;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;
import com.tfcode.comparetout.model.scenario.SimulationInputData;

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
 *
 * <p>As of Phase 1 of the simulation-engine refactor (see {@code plans/sim/refactor.md}), this class
 * is a thin adapter around the pure {@link SimulationEngine}: it reads scenario data through the
 * repository, assembles the per-inverter {@link SimulationEngine.InputData}, drives the engine
 * row-by-row, and persists the results. All Android, Room, and WorkManager concerns live here; the
 * energy-flow computation lives in the engine and is unit-tested without these dependencies.</p>
 */
public class SimulationWorker extends Worker {

    private final ToutcRepository mToutcRepository;
    private final Context mContext;

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
                    Map<Inverter, SimulationEngine.InputData> inputDataMap = new HashMap<>();
                    // Scenario-level inputs (load export limit, hot water, EV) — shared by all inverters.
                    ScenarioInputs scenarioInputs;

                    /*
                     * INPUT DATA PREPARATION
                     * For each inverter, gather simulation input data (load, PV, battery, schedules, etc.).
                     * This block builds the InputData map, which centralizes all scenario factors for simulation.
                     */
                    if (scenario.isHasInverters()) {
                        // Hot water and EV are scenario-level (not inverter-bound): gather once.
                        HWSystem configuredHotWater = null;
                        Boolean hotWaterDivert = false;
                        List<HWSchedule> hotWaterSchedules = null;
                        if (scenario.isHasHWSystem()) {
                            configuredHotWater = scenarioComponents.hwSystem;
                            if (scenario.isHasHWDivert()) hotWaterDivert = scenarioComponents.hwDivert.isActive();
                            if (scenario.isHasHWSchedules() && !scenarioComponents.hwSchedules.isEmpty()) hotWaterSchedules = scenarioComponents.hwSchedules;
                        }
                        List<EVCharge> evCharges = null;
                        List<EVDivert> evDiverts = null;
                        if (scenario.isHasEVCharges() && !scenarioComponents.evCharges.isEmpty()) evCharges = scenarioComponents.evCharges;
                        if (scenario.isHasEVDivert() && !scenarioComponents.evDiverts.isEmpty()) evDiverts = scenarioComponents.evDiverts;
                        scenarioInputs = new ScenarioInputs(configuredHotWater, hotWaterDivert, hotWaterSchedules,
                                evCharges, evDiverts, exportMax);
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
                            SimulationEngine.ForceDischargeToGrid connectedDischarge = null;
                            SimulationEngine.ChargeFromGrid chargeFromGrid = null;
                            if (scenario.isHasBatteries()) {
                                for (Battery battery : scenarioComponents.batteries)
                                    if (battery.getInverter().equals(inverter.getInverterName()))
                                        connectedBattery = battery;
                                if (scenario.isHasLoadShifts()) {
                                    chargeFromGrid = new SimulationEngine.ChargeFromGrid(scenarioComponents.loadShifts, rowsToProcess);
                                }
                                if (scenario.isHasDischarges()) {
                                    List<DischargeToGrid> connectedDischarges = new ArrayList<>();
                                    for (DischargeToGrid dischargeToGrid : scenarioComponents.discharges) {
                                        if (dischargeToGrid.getInverter().equals(inverter.getInverterName()))
                                            connectedDischarges.add(dischargeToGrid);
                                    }
                                    if (!(connectedDischarges.isEmpty()))
                                        connectedDischarge = new SimulationEngine.ForceDischargeToGrid(connectedDischarges, rowsToProcess);
                                }
                            }
                            // Associate the inverter with its inverter-bound state only. Hot water / EV
                            // are scenario-level (see scenarioInputs) and are not passed per inverter.
                            SimulationEngine.InputData iData = new SimulationEngine.InputData(inverter, simulationInputData,
                                    connectedBattery, chargeFromGrid, connectedDischarge);
                            inputDataMap.put(inverter, iData);
                        }
                    } else { // No solar simulation, but we need a 'perfect' inverter
                        Inverter inverter = new Inverter();
                        inverter.setInverterIndex(0);
                        inverter.setDc2acLoss(0);
                        inverter.setDc2dcLoss(0);
                        inverter.setAc2dcLoss(0);
                        inverter.setMinExcess(0);

                        // Scenario-level inputs. With no inverters there is no PV excess to divert, so
                        // (as before) hot-water divert and EV divert are not engaged here.
                        HWSystem configuredHotWater = null;
                        List<HWSchedule> hotWaterSchedules = null;
                        if (scenario.isHasHWSystem()) {
                            configuredHotWater = scenarioComponents.hwSystem;
                            if (scenario.isHasHWSchedules() && !scenarioComponents.hwSchedules.isEmpty()) hotWaterSchedules = scenarioComponents.hwSchedules;
                        }
                        List<EVCharge> evCharges = null;
                        if (scenario.isHasEVCharges() && !scenarioComponents.evCharges.isEmpty()) evCharges = scenarioComponents.evCharges;
                        scenarioInputs = new ScenarioInputs(configuredHotWater, null, hotWaterSchedules,
                                evCharges, null, exportMax);
                        SimulationEngine.InputData idata = new SimulationEngine.InputData(inverter, mToutcRepository.getSimulationInputNoSolar(scenarioID),
                                null, null, null);
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
                        SimulationEngine.processOneRow(scenarioID, scenarioInputs, outputRows, row, inputDataMap);
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
}
