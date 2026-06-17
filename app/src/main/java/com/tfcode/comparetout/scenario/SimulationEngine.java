/*
 * Copyright (c) 2026. Tony Finnerty
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

import static java.lang.Double.max;
import static java.lang.Double.min;

import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.ChargeModel;
import com.tfcode.comparetout.model.scenario.DischargeToGrid;
import com.tfcode.comparetout.model.scenario.EVCharge;
import com.tfcode.comparetout.model.scenario.EVDivert;
import com.tfcode.comparetout.model.scenario.HWSchedule;
import com.tfcode.comparetout.model.scenario.HWSystem;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;
import com.tfcode.comparetout.model.scenario.SimulationInputData;
import com.tfcode.comparetout.scenario.sim.DispatchStrategy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure (Android-free) simulation engine for a single scenario.
 *
 * <p>Phase 1 of the simulation-engine refactor (see {@code plans/sim/refactor.md}). This class was
 * extracted verbatim from {@link SimulationWorker}: the per-interval energy-flow logic
 * ({@link #processOneRow}), its battery charge/discharge helpers, and the per-inverter state and
 * schedule classes ({@link InputData}, {@link ChargeFromGrid}, {@link ForceDischargeToGrid}). The
 * extraction is behaviour-preserving — guarded by the golden-master tests — and carries no Android,
 * Room, WorkManager, or LiveData dependency, so it can be unit-tested directly.</p>
 *
 * <p>It lives in package {@code com.tfcode.comparetout.scenario} (rather than {@code scenario.sim})
 * so that the existing white-box unit tests — which read package-private state such as
 * {@code InputData.soc} and {@code ChargeFromGrid.mCFG} — keep compiling without widening visibility.
 * The new {@code scenario.sim} package holds the pure abstractions introduced by the refactor
 * (TimeAxis, SimTime, and, in later phases, the dispatch Strategy, component roles, and DC/AC bus
 * model). Once those phases replace the white-box tests with black-box golden coverage, this engine
 * can be relocated into {@code scenario.sim} with tightened visibility.</p>
 */
public class SimulationEngine {

    static final Battery M_NULL_BATTERY = new Battery();
    static {
        M_NULL_BATTERY.setBatterySize(0);
        M_NULL_BATTERY.setDischargeStop(100);
        M_NULL_BATTERY.setMaxDischarge(0);
        M_NULL_BATTERY.setMaxCharge(0);
        M_NULL_BATTERY.setInverter("");
        M_NULL_BATTERY.setStorageLoss(0);
    }

    /** Scenario-level overload using the default dispatch strategy (load &rarr; battery &rarr; grid). */
    static void processOneRow(long scenarioID, ScenarioInputs scenario, ArrayList<ScenarioSimulationData> outputRows, int row, Map<Inverter, InputData> inputDataMap) {
        processOneRow(scenarioID, scenario, DispatchStrategy.LOAD_BATTERY_GRID, outputRows, row, inputDataMap);
    }

    /**
     * Processes a single simulation time step for all inverters in the scenario.
     * Resolves each inverter's DC/AC bus (Phase 3) feeding a shared AC bus, then writes the row.
     * @param scenarioID The scenario ID.
     * @param scenario The scenario-level inputs (load export limit, hot water, EV) shared by all inverters.
     * @param strategy The dispatch strategy for meeting load PV can't cover (battery-before-grid by default).
     * @param outputRows List to append simulation output data.
     * @param row The time step index.
     * @param inputDataMap Map of inverters to their input data and state.
     */
    static void processOneRow(long scenarioID, ScenarioInputs scenario, DispatchStrategy strategy, ArrayList<ScenarioSimulationData> outputRows, int row, Map<Inverter, InputData> inputDataMap) {

        /*
         * INPUT AND STATE INITIALIZATION
         * Set up references to input data and initialize battery state of charge (SOC) and other
         * per-row variables. Hot water and EV are scenario-level (see ScenarioInputs), not per-inverter.
         */
        SimulationInputData inputRow = null;
        HWSystem hwSystem = scenario.mHWSystem;
        Boolean hwDivert = scenario.mHWDivert;

        // Initialise battery state: at row 0 each battery starts at its discharge-stop floor; a missing
        // battery is replaced by the shared null battery so the rest of the code treats it uniformly.
        for (Map.Entry<Inverter, InputData> entry : inputDataMap.entrySet()) {
            InputData iData = entry.getValue();
            if (null == inputRow) inputRow = iData.simulationInputData.get(row);
            if (null == iData.mBattery) {
                iData.soc = 0;
                iData.mBattery = M_NULL_BATTERY;
            }
            if (row == 0) iData.soc = iData.getDischargeStop();
        }
        if (null == inputRow) return;

        /*
         * LOAD (Phase 2 — Bug 1 fix): load is scenario-level. Every inverter's InputData carries the SAME
         * load series, so count it exactly once from the representative inputRow. PV, by contrast, is
         * genuinely per-inverter and is resolved per inverter in the energy-flow pass below.
         */
        double inputLoad = inputRow.getLoad();

        // Total PV (DC) for the output row; per-inverter PV is consumed in the energy-flow pass.
        double tPV = 0;
        for (Map.Entry<Inverter, InputData> entry : inputDataMap.entrySet())
            tPV += entry.getValue().simulationInputData.get(row).tpv;

        // Grid charging (load shift): charge batteries from the grid where scheduled.
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

        EVCharge evCharge = scenario.isEVCharging(inputRow.getDow(), month, inputRow.mod);
        double scheduledEVChargeLoad = 0;
        if (!(null == evCharge)) scheduledEVChargeLoad = evCharge.getDraw() / 12d;
        outputRow.setDirectEVcharge(scheduledEVChargeLoad);

        double previousWaterTemp = 0;
        double scheduledWaterLoad = 0;
        if (!outputRows.isEmpty()) previousWaterTemp = outputRows.get(row -1).getWaterTemp();
        double nowWaterTemp = previousWaterTemp;
        boolean immersionIsOn = scenario.isHotWaterHeatingScheduled(inputRow.getDow(), month, inputRow.mod);
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
         * ENERGY FLOW — per-inverter DC/AC bus resolution feeding a shared AC bus (Phase 3).
         * For each inverter, in ascending index order: (1) PV serves the shared load via DC->AC, capped by
         * the inverter's AC rating; (2) surplus PV charges its OWN battery via DC-DC FIRST, so PV beyond the
         * AC rating is captured rather than clipped; (3) remaining PV feeds the grid within AC headroom and
         * the shared export cap. Then, per the dispatch strategy, any remaining load is met from the
         * batteries (DC-DC, DC->AC, within AC headroom) and/or the grid. See plans/sim/phase3-design.md.
         */
        final double h = 1d / 12d;                 // 5-minute interval as a fraction of an hour
        double exportCap = scenario.exportMax * h; // shared export headroom (kWh)

        double buy = purchaseShiftingLoad;
        double feed = 0;
        double b2g = 0;
        double pv2charge = 0;
        double pv2load = 0;
        double bat2Load = 0;

        double remLoad = inputLoad;   // shared AC load still to serve
        double remExport = exportCap; // shared export headroom remaining

        List<Map.Entry<Inverter, InputData>> sortedInverters = new ArrayList<>(inputDataMap.entrySet());
        sortedInverters.sort(Comparator.comparingLong(en -> en.getKey().getInverterIndex()));

        // Remaining AC throughput headroom per inverter this interval (Bug 3 fix: the AC rating now binds).
        Map<InputData, Double> acRoom = new HashMap<>();
        for (Map.Entry<Inverter, InputData> en : sortedInverters)
            acRoom.put(en.getValue(), en.getKey().getMaxInverterLoad() * h);

        // PASS 1: PV -> load, PV -> own battery (DC-DC), PV -> feed.
        for (Map.Entry<Inverter, InputData> en : sortedInverters) {
            InputData d = en.getValue();
            double room = acRoom.get(d);
            double pvDc = d.simulationInputData.get(row).tpv;

            // (1) PV -> AC -> shared load
            double toLoad = min(min(pvDc * d.dc2acLoss, room), remLoad);
            if (toLoad > 0) {
                pv2load += toLoad;
                remLoad -= toLoad;
                room -= toLoad;
                pvDc -= toLoad / d.dc2acLoss;
            }

            // (2) PV surplus (DC) -> own battery via DC-DC (captures PV the AC stage cannot pass)
            double dcToBatt = min(pvDc, d.getChargeCapacity());
            if (dcToBatt > 0) {
                d.soc += dcToBatt * d.dc2dcLoss; // dc2dcLoss is the kept fraction
                pv2charge += dcToBatt;
                pvDc -= dcToBatt;
            }

            // (3) PV surplus -> AC -> feed, within AC headroom and the shared export cap.
            //     minExcess suppresses micro-export (kept from the original model).
            double pvAcFeed = min(min(pvDc * d.dc2acLoss, room), remExport);
            if (pvAcFeed > en.getKey().getMinExcess()) {
                feed += pvAcFeed;
                room -= pvAcFeed;
                remExport -= pvAcFeed;
            }
            // any remaining PV is curtailed (clipped)
            acRoom.put(d, room);
        }

        // PASS 2: remaining load from battery (per strategy), then grid.
        if (strategy.dischargeBatteryForLoad()) {
            for (Map.Entry<Inverter, InputData> en : sortedInverters) {
                if (remLoad <= 0) break;
                InputData d = en.getValue();
                double room = acRoom.get(d);
                if (room <= 0) continue;
                double dcAvail = d.getDischargeCapacity(row); // DC kWh available above the discharge stop
                double acFromBatt = min(min(dcAvail * d.dc2dcLoss * d.dc2acLoss, room), remLoad);
                if (acFromBatt > 0) {
                    // DC drawn from the cells to deliver acFromBatt at AC, incl. storage loss (D2).
                    double dcDrawn = (acFromBatt / (d.dc2dcLoss * d.dc2acLoss)) * (1 + d.storageLoss / 100d);
                    d.soc -= dcDrawn;
                    bat2Load += acFromBatt;
                    remLoad -= acFromBatt;
                    acRoom.put(d, room - acFromBatt);
                }
            }
        }
        buy += remLoad; // grid meets remaining load (soft-capped by gridImportMax — see design D4)

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
        double totalEVDivertedThisDay = scenario.mEVDivertDailyTotals.computeIfAbsent(inputRow.do2001, k -> 0D);
        EVDivert evDivert = scenario.getEVDivertOrNull(inputRow.getDow(), month, inputRow.mod);
        if (!(null == evDivert)) {
            double maxEVDivert = evDivert.getDailyMax() - totalEVDivertedThisDay;
            if (evDivert.isActive()) {
                if (evDivert.isEv1st()) {
                    if (feed > evDivert.getMinimum()/12d) {
                        if (maxEVDivert > 0) {
                            divertedToEV = min (feed, maxEVDivert);
                            scenario.mEVDivertDailyTotals.put(inputRow.do2001, totalEVDivertedThisDay + divertedToEV);
                            feed -= divertedToEV;
                        }
                    }
                    // else we can ignore this and let the later water divert take it
                }
                else { // Water diversion must happen to reduce the feed
                    if ((!immersionIsOn) && hwDiversionIsOn) {
                        HWSystem.Heat heat = hwSystem.heatWater(inputRow.mod, previousWaterTemp, feed);
                        feed -= heat.kWhUsed;
                        nowWaterTemp = heat.temperature;
                        divertedToWater = heat.kWhUsed;
                        if (feed > evDivert.getMinimum()/12d) { // there may be some feed left fo the EV
                            if (maxEVDivert > 0) {
                                divertedToEV = min (feed, maxEVDivert);
                                scenario.mEVDivertDailyTotals.put(inputRow.do2001, totalEVDivertedThisDay + divertedToEV);
                                feed -= divertedToEV;
                            }
                        }
                    }
                }
            }
        }
        // WATER
        if ((!immersionIsOn) && hwDiversionIsOn) {
            HWSystem.Heat heat = hwSystem.heatWater(inputRow.mod, previousWaterTemp, feed);
            feed -= heat.kWhUsed;
            nowWaterTemp = heat.temperature;
            divertedToWater = heat.kWhUsed;
        }
        outputRow.setWaterTemp(nowWaterTemp);
        outputRow.setKWHDivToWater(divertedToWater);
        outputRow.setImmersionLoad(scheduledWaterLoad);
        outputRow.setKWHDivToEV(divertedToEV);

        /*
         * FORCED DISCHARGE TO GRID
         * Battery -> grid export for inverters scheduled to discharge now. Export draws on the shared export
         * headroom that remains after PV feed and on the inverter's remaining AC headroom (D3); the cells give
         * up DC including the DC-DC + DC->AC conversion and storage loss (D2).
         */
        for (Map.Entry<Inverter, InputData> en : sortedInverters) {
            InputData d = en.getValue();
            ForceDischargeToGrid fd = d.mForceDischargeToGrid;
            if (null == fd || null == fd.mD2G || !fd.mD2G.get(row)) continue;
            double room = acRoom.get(d);
            double stopKWh = (fd.mStopAt.get(row) / 100d) * d.mBattery.getBatterySize();
            double soc = d.soc;
            if (remExport <= 0 || room <= 0 || soc <= stopKWh) continue;
            double wantedRate = fd.mRate.get(row) / 12D;                          // AC kWh this interval (max)
            // DC available above the stop, expressed as deliverable AC.
            double acFromAvailable = (soc - stopKWh) / (1 + d.storageLoss / 100d) * d.dc2dcLoss * d.dc2acLoss;
            double acExport = min(min(min(remExport, room), wantedRate), acFromAvailable);
            if (acExport > 0) {
                double dcDrawn = (acExport / (d.dc2dcLoss * d.dc2acLoss)) * (1 + d.storageLoss / 100d);
                d.soc -= dcDrawn;
                b2g += acExport;
                feed += acExport;
                remExport -= acExport;
                acRoom.put(d, room - acExport);
            }
        }

        double totalSOC = 0;
        for (Map.Entry<Inverter, InputData> en : sortedInverters) totalSOC += en.getValue().soc;

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
     * Legacy/compatibility entry point retained for the existing white-box unit tests. It derives the
     * scenario-level inputs ({@link ScenarioInputs}) from the inverter map exactly as the pre-Phase-2b
     * engine did — hot water / EV taken from the first inverter's {@link InputData} — then delegates.
     * Production code ({@link SimulationWorker}) builds a {@link ScenarioInputs} explicitly so that load,
     * hot water and EV are modelled at scenario level (and free of {@code HashMap}-order sensitivity).
     *
     * @deprecated prefer {@link #processOneRow(long, ScenarioInputs, java.util.ArrayList, int, java.util.Map)}.
     */
    @Deprecated
    static void processOneRow(long scenarioID, ArrayList<ScenarioSimulationData> outputRows, int row, Map<Inverter, InputData> inputDataMap) {
        if (inputDataMap.isEmpty()) return;
        InputData first = inputDataMap.values().iterator().next();
        // Preserve the pre-2b extraction: hwSystem / hwDivert came from the first NON-NULL inverter,
        // while schedules / EV / exportMax came from the first inverter entry.
        HWSystem hwSystem = null;
        Boolean hwDivert = null;
        for (InputData d : inputDataMap.values()) {
            if (null == hwSystem) hwSystem = d.mHWSystem;
            if (null == hwDivert) hwDivert = d.mHWDivert;
        }
        ScenarioInputs scenario = new ScenarioInputs(hwSystem, hwDivert, first.mHWSchedules,
                first.mEVCharges, first.mEVDiverts, first.mEVDivertDailyTotals, first.exportMax);
        processOneRow(scenarioID, scenario, outputRows, row, inputDataMap);
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
                    // D5: grid charging crosses AC->DC then DC-DC, so less is stored than is bought.
                    chargeCapacity = inputData.getChargeCapacity();           // AC drawn from the grid
                    inputData.soc += chargeCapacity * inputData.ac2dcLoss * inputData.dc2dcLoss;
                    totalExtraLoad += chargeCapacity;
                }
            }
        }
        return totalExtraLoad;
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
        final long id;
        final double dc2acLoss;
        final double ac2dcLoss;
        final double dc2dcLoss;
        final double storageLoss;
        final double exportMax;
        List<SimulationInputData> simulationInputData;
        Battery mBattery;
        ChargeFromGrid mChargeFromGrid;
        final ForceDischargeToGrid mForceDischargeToGrid;

        final HWSystem mHWSystem;
        final Boolean mHWDivert;
        final List<HWSchedule> mHWSchedules;

        final List<EVCharge> mEVCharges;
        final List<EVDivert> mEVDiverts;
        final Map<Integer, Double> mEVDivertDailyTotals;

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

        final List<Boolean> mCFG;
        final List<Double> mStopAt;

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

        final List<Boolean> mD2G;
        final List<Double> mStopAt;
        final List<Double> mRate;

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
