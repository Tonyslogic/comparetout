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

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.R;
import com.tfcode.comparetout.ui2.HeatPumpWeatherFetchWorker;
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
import com.tfcode.comparetout.model.scenario.HeatPump;
import com.tfcode.comparetout.model.scenario.SimulationInputData;
import com.tfcode.comparetout.scenario.sim.CsvWeatherProvider;
import com.tfcode.comparetout.scenario.sim.HeatPumpComponent;
import com.tfcode.comparetout.scenario.sim.HeatPumpDemandModel;
import com.tfcode.comparetout.scenario.sim.SimTime;
import com.tfcode.comparetout.scenario.sim.TimeAxis;
import com.tfcode.comparetout.scenario.sim.WeatherProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.ZoneOffset;
import java.util.ArrayList;
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

                    // Heat pump (scenario-level demand): built once from its config + weather, aligned to the
                    // grid (Phase 4 of plans/hp/plan.md). Null when no heat pump ⇒ nothing registered.
                    HeatPumpComponent heatPumpComponent = null;
                    if (scenario.isHasHeatPump() && !(null == scenarioComponents.heatPumps)
                            && !scenarioComponents.heatPumps.isEmpty()) {
                        HeatPump hp = scenarioComponents.heatPumps.get(0);
                        List<SimulationInputData> hpGrid = mToutcRepository.getSimulationInputNoSolar(scenarioID);
                        // A historical PV import (AlphaESS / Home Assistant) drives the weather to its real year
                        // (cached on the source-period key, content realigned to 2001 by the fetch worker);
                        // PVGIS/legacy/no-PV ⇒ null ⇒ the load-grid period, exactly as before.
                        String[] pvPeriod = HeatPumpWeatherCache.pvSourcePeriod(scenarioComponents.panels);
                        // CDS weather behaves like PV data: if the real weather hasn't been fetched yet we must
                        // NOT silently simulate on the bundled sample asset. Kick off the fetch and skip this
                        // scenario (leaving it "needs simulation" + flagged on the dashboard); the fetch worker
                        // re-runs the simulation once the weather lands.
                        if ("cds".equals(hp.getWeatherSource())) {
                            boolean cached = (pvPeriod != null)
                                    ? HeatPumpWeatherCache.cacheExists(getApplicationContext(),
                                        hp.getLatitude(), hp.getLongitude(), pvPeriod[0], pvPeriod[1])
                                    : HeatPumpWeatherCache.cacheExists(getApplicationContext(),
                                        hp.getLatitude(), hp.getLongitude(),
                                        HeatPumpWeatherCache.gridMillis(hpGrid));
                            if (!cached) {
                                enqueueWeatherFetch(scenarioID);
                                builder.setContentText("Skipping " + scenario.getScenarioName()
                                        + " — heat-pump weather not ready");
                                notificationManager.notify(notificationId, builder.build());
                                continue;
                            }
                        }
                        heatPumpComponent = buildHeatPumpComponent(hp, hpGrid, pvPeriod);
                    }

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
                                evCharges, evDiverts, exportMax, heatPumpComponent);
                        for (Inverter inverter : scenarioComponents.inverters) {
                            // Get some load simulation data to start with
                            List<SimulationInputData> simulationInputData = mToutcRepository.getSimulationInputNoSolar(scenarioID);
                            rowsToProcess = simulationInputData.size();

                            // Aggregate this inverter's PV keyed by UTC millis, then merge it onto the load
                            // series by matching millis (replaces the old positional + DST-magic merge).
                            Map<Long, Double> inverterPVByMillis = getPVForInverterByMillis(scenarioComponents, inverter);
                            mergePVByMillis(simulationInputData, inverterPVByMillis);
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
                                evCharges, null, exportMax, heatPumpComponent);
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
                     * Drive the engine over a UTC TimeAxis covering the input series at the 5-minute cadence.
                     * The axis is built from the data's own millis range, so this reproduces the historical
                     * full-year run; the engine itself is now period-agnostic and millis-driven.
                     */
                    List<SimulationInputData> axisSeries = inputDataMap.values().iterator().next().simulationInputData;
                    long axisStart = millisOf(axisSeries.get(0));
                    TimeAxis axis = TimeAxis.fiveMinute(axisStart,
                            axisStart + (long) rowsToProcess * TimeAxis.FIVE_MINUTES_MILLIS);
                    ArrayList<ScenarioSimulationData> outputRows =
                            SimulationEngine.simulate(scenarioID, scenarioInputs, axis, inputDataMap);

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
     * Merges aggregated PV onto the load series by matching UTC millis (replaces the old positional merge
     * with its 2001 DST magic — Bug 4). Both grids are UTC after ingestion, so equal instants line up; a load
     * row with no PV for its instant gets zero. PV stored device-local before the UTC change won't match and
     * should be re-loaded from PVGIS.
     * @param simulationInputData The load series to populate with PV.
     * @param pvByMillis Aggregated inverter PV keyed by UTC millis.
     */
    static void mergePVByMillis(List<SimulationInputData> simulationInputData, Map<Long, Double> pvByMillis) {
        for (SimulationInputData row : simulationInputData) {
            row.setTpv(pvByMillis.getOrDefault(millisOf(row), 0d));
        }
    }

    /** UTC millis for a row: the stored value, or derived from date + minute-of-day for legacy NULL rows. */
    static long millisOf(SimulationInputData row) {
        Long millis = row.getMillisSinceEpoch();
        return (millis != null) ? millis
                : SimTime.fromDateAndMinuteOfDay(row.getDate(), row.getMod(), ZoneOffset.UTC);
    }

    /**
     * Aggregates a single inverter's PV generation keyed by UTC millis. Within an MPPT, parallel panels add and
     * optimized panels take the max; the MPPTs are then summed. Each panel row is keyed by its UTC millis.
     * @param scenarioComponents Scenario components containing panels.
     * @param inverter The inverter to aggregate PV for.
     * @return PV (kWh per interval) keyed by UTC millis.
     */
    /**
     * Builds the heat-pump demand component for a scenario: derives the sim grid millis from the load rows
     * exactly as the engine derives {@code ctx.millis}, loads the weather (the offline sample asset for v1;
     * the CDS-fetched series is Phase 6), and aligns/calibrates the model onto that grid. Returns null if the
     * weather can't be loaded, so the rest of the simulation is unaffected.
     */
    /**
     * Enqueue the CDS weather fetch for a scenario whose real weather isn't cached yet. Unique-per-scenario
     * with {@link ExistingWorkPolicy#KEEP} so repeated recompute passes don't pile up duplicate fetches; the
     * fetch worker re-runs the simulation once the download lands.
     */
    private void enqueueWeatherFetch(long scenarioID) {
        OneTimeWorkRequest fetch = new OneTimeWorkRequest.Builder(HeatPumpWeatherFetchWorker.class)
                .setInputData(new Data.Builder().putLong("scenarioID", scenarioID).build())
                .addTag("hp_weather_" + scenarioID)
                .build();
        WorkManager.getInstance(getApplicationContext())
                .enqueueUniqueWork("hp_weather_" + scenarioID, ExistingWorkPolicy.KEEP, fetch);
    }

    private HeatPumpComponent buildHeatPumpComponent(HeatPump hp, List<SimulationInputData> gridRows,
                                                     String[] pvPeriod) {
        long[] gridMillis = HeatPumpWeatherCache.gridMillis(gridRows);
        WeatherProvider weather = loadWeather(hp, gridMillis, pvPeriod);
        if (weather == null) return null; // weather unavailable ⇒ no heat-pump contribution
        return HeatPumpComponent.build(configFromHeatPump(hp), weather, gridMillis);
    }

    /**
     * Resolve the outdoor-weather series for the heat pump. {@code weatherSource == "cds"} reads the cached
     * ERA5 CSV the fetch worker downloaded for this (location, grid period); any other value — or a missing /
     * unreadable cache — falls back to the offline sample asset. Both paths feed the <b>same</b>
     * {@link CsvWeatherProvider}, so the cache is a drop-in for the asset (Phase 6 of plans/hp/plan.md).
     */
    private WeatherProvider loadWeather(HeatPump hp, long[] gridMillis, String[] pvPeriod) {
        if ("cds".equals(hp.getWeatherSource())) {
            // Same key the fetch worker wrote: the historical source period when PV was imported, else the
            // load-grid span. The cached content is already on the 2001 grid either way.
            File cache = (pvPeriod != null)
                    ? HeatPumpWeatherCache.cacheFile(getApplicationContext(),
                        hp.getLatitude(), hp.getLongitude(), pvPeriod[0], pvPeriod[1])
                    : HeatPumpWeatherCache.cacheFile(
                        getApplicationContext(), hp.getLatitude(), hp.getLongitude(), gridMillis);
            if (cache.exists()) {
                try (InputStream is = new FileInputStream(cache)) {
                    return new CsvWeatherProvider(new InputStreamReader(is));
                } catch (IOException e) {
                    // Unreadable cache ⇒ treat as missing (don't substitute the sample). doWork() already
                    // gates CDS scenarios on cacheExists() and skips when absent, so we only reach here on a
                    // genuinely corrupt file; returning null omits the HP rather than faking real weather.
                    android.util.Log.e("HeatPump", "CDS weather cache unreadable ("
                            + cache.getName() + ") — skipping heat pump", e);
                }
            }
            return null; // CDS selected but no usable real weather ⇒ no HP contribution (never the sample asset)
        }
        try (InputStream is = getApplicationContext().getAssets()
                .open("hp-weather/era5-timeseries-2001-synthetic.csv")) {
            return new CsvWeatherProvider(new InputStreamReader(is));
        } catch (IOException e) {
            android.util.Log.e("HeatPump", "weather asset load failed — HP will be absent from the sim", e);
            return null;
        }
    }

    /** Maps a persisted {@link HeatPump} onto the pure model's {@link HeatPumpDemandModel.Config}. */
    private static HeatPumpDemandModel.Config configFromHeatPump(HeatPump hp) {
        HeatPumpDemandModel.Config c = new HeatPumpDemandModel.Config();
        c.fuelAnnual = hp.getFuelAnnual();
        c.calorificValue = hp.getCalorificValue();
        c.boilerEfficiency = hp.getBoilerEfficiency();
        c.dhwAnnualKWh = hp.getDhwAnnualKWh();
        c.spaceHeatingFraction = hp.getSpaceHeatingFraction();
        c.floorAreaM2 = hp.getFloorAreaM2();       // new-build fabric anchor (0 ⇒ use the fuel anchor above)
        c.heatLossIndex = hp.getHeatLossIndex();
        c.setpointNew = hp.getDesiredIndoorTemp();
        c.setpointOld = hp.getCurrentIndoorTemp();
        c.balancePoint = hp.getBalancePoint();
        c.alphaWind = hp.getAlphaWind();
        if (!(null == hp.getHourlyDist())) c.hourlyProfile = toWeights(hp.getHourlyDist().dist, 24);
        if (!(null == hp.getDowDist())) c.dowProfile = toWeights(hp.getDowDist().dowDist, 7);
        c.heatOnDayOfYear = hp.getHeatingSeasonStart();
        c.heatOffDayOfYear = hp.getHeatingSeasonEnd();
        c.copRated = hp.getCopRated();
        c.copRefTemp = hp.getCopRefTemp();
        c.copSlope = hp.getCopSlope();
        c.scop = hp.getScop();
        c.capacityKw = hp.getCapacityKw();
        c.backupHeater = hp.isBackupHeater();
        c.intervalHours = 1d / 12d; // 5-minute sim grid
        return c;
    }

    private static double[] toWeights(List<Double> list, int n) {
        double[] a = new double[n];
        for (int i = 0; i < n; i++) {
            a[i] = (!(null == list) && i < list.size() && !(null == list.get(i))) ? list.get(i) : 1d;
        }
        return a;
    }

    private Map<Long, Double> getPVForInverterByMillis(ScenarioComponents scenarioComponents, Inverter inverter) {
        Map<Long, Double> inverterPV = new HashMap<>();
        for (int mppt = 1; mppt <= inverter.getMpptCount(); mppt++) {
            Map<Long, Double> mpptPV = new HashMap<>();
            for (Panel panel : scenarioComponents.panels) {
                if (panel.getMppt() == mppt && panel.getInverter().equals(inverter.getInverterName())) {
                    List<SimulationInputData> panelPV = mToutcRepository.getPVRowsForPanel(panel.getPanelIndex());
                    boolean parallel = panel.getConnectionMode() == Panel.PARALLEL;
                    for (SimulationInputData pvRow : panelPV) {
                        long millis = millisOf(pvRow);
                        if (parallel) mpptPV.merge(millis, pvRow.getTpv(), Double::sum);
                        else mpptPV.merge(millis, pvRow.getTpv(), Math::max);
                    }
                }
            }
            for (Map.Entry<Long, Double> e : mpptPV.entrySet())
                inverterPV.merge(e.getKey(), e.getValue(), Double::sum);
        }
        return inverterPV;
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
