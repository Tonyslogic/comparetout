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
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;
import com.tfcode.comparetout.model.scenario.SimulationInputData;
import com.tfcode.comparetout.scenario.sim.DemandContributor;
import com.tfcode.comparetout.scenario.sim.DemandResult;
import com.tfcode.comparetout.scenario.sim.DispatchStrategy;
import com.tfcode.comparetout.scenario.sim.IntervalContext;
import com.tfcode.comparetout.scenario.sim.InverterComponent;
import com.tfcode.comparetout.scenario.sim.OutputChannel;
import com.tfcode.comparetout.scenario.sim.SimTime;
import com.tfcode.comparetout.scenario.sim.SurplusSink;
import com.tfcode.comparetout.scenario.sim.TimeAxis;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
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

    /**
     * Runs the simulation over a {@link TimeAxis}, returning one output row per interval that has stored data
     * (Phase 4b / b2.3). Each axis interval is resolved to its input row by <b>UTC millis</b> — the interval's
     * instant is looked up against the input series' own millis, so the axis need not start at row 0 of the
     * stored series. Window semantics (the chosen model): an axis interval whose instant has no stored data
     * produces no row. The per-interval time-of-day and schedule logic is derived from that instant inside
     * {@link #processOneRow}; the daily EV-divert accumulator is keyed by UTC epoch-day.
     *
     * <p>With the default axis (built from the input series' own millis range at the 5-minute cadence) every
     * interval matches a stored row in order, so {@code indexByMillis.get(m)} equals the loop counter and the
     * historical row-by-row run is reproduced exactly — golden-master byte-identical. A sub-range axis (a
     * window within the stored period) simply resolves to the corresponding contiguous slice of stored rows.</p>
     *
     * <p>The resolved index also addresses the 2001-indexed {@code ChargeFromGrid}/{@code ForceDischargeToGrid}
     * schedules: under window-within-2001 semantics the stored series <i>is</i> the 2001 grid, so the series
     * index equals the 2001 index and the fixed-2001 schedule precompute remains exactly correct (tiling /
     * multi-year spans, which would need axis-walked schedules, are out of scope per the captured decision).</p>
     */
    static ArrayList<ScenarioSimulationData> simulate(long scenarioID, ScenarioInputs scenario,
                                                      TimeAxis axis, Map<Inverter, InputData> inputDataMap) {
        ArrayList<ScenarioSimulationData> outputRows = new ArrayList<>();
        if (inputDataMap.isEmpty()) return outputRows;

        // Map each stored interval's UTC millis to its index in the input series. The inverters share one
        // load grid (Bug 1 fix), so the representative series' index applies to every inverter and to the
        // 2001-indexed schedules. Resolving by instant (not position) is what lets the axis be a window.
        List<SimulationInputData> reference = inputDataMap.values().iterator().next().simulationInputData;
        Map<Long, Integer> indexByMillis = new HashMap<>();
        for (int i = 0; i < reference.size(); i++) indexByMillis.put(millisOf(reference.get(i)), i);

        int count = axis.intervalCount();
        for (int i = 0; i < count; i++) {
            Integer seriesIndex = indexByMillis.get(axis.intervalAt(i).getStartMillis());
            if (seriesIndex == null) continue; // window: no stored data for this instant -> no row
            processOneRow(scenarioID, scenario, null, outputRows, seriesIndex, inputDataMap);
        }
        return outputRows;
    }

    /** UTC millis for a row: the stored value, or derived from date + minute-of-day for legacy NULL rows. */
    static long millisOf(SimulationInputData row) {
        Long millis = row.getMillisSinceEpoch();
        return (millis != null) ? millis
                : SimTime.fromDateAndMinuteOfDay(row.getDate(), row.getMod(), ZoneOffset.UTC);
    }

    /**
     * Scenario-level overload (production path). Each inverter uses its OWN dispatch strategy, read from its
     * persisted dispatch mode (Phase 4). Inverters default to load &rarr; battery &rarr; grid, so existing
     * scenarios are unchanged.
     */
    static void processOneRow(long scenarioID, ScenarioInputs scenario, ArrayList<ScenarioSimulationData> outputRows, int row, Map<Inverter, InputData> inputDataMap) {
        processOneRow(scenarioID, scenario, null, outputRows, row, inputDataMap);
    }

    /**
     * Processes a single simulation time step for all inverters in the scenario.
     * Resolves each inverter's DC/AC bus (Phase 3) feeding a shared AC bus, then writes the row.
     * @param scenarioID The scenario ID.
     * @param scenario The scenario-level inputs (load export limit, hot water, EV) shared by all inverters.
     * @param forcedStrategy If non-null, overrides every inverter's own dispatch strategy with this one (used
     *        by tests to isolate a strategy). If null, each inverter uses its own {@link InputData#strategy}.
     * @param outputRows List to append simulation output data.
     * @param row The time step index.
     * @param inputDataMap Map of inverters to their input data and state.
     */
    static void processOneRow(long scenarioID, ScenarioInputs scenario, DispatchStrategy forcedStrategy, ArrayList<ScenarioSimulationData> outputRows, int row, Map<Inverter, InputData> inputDataMap) {

        /*
         * INPUT AND STATE INITIALIZATION
         * Set up references to input data and initialize battery state of charge (SOC) and other
         * per-row variables. Hot water and EV are scenario-level (see ScenarioInputs), not per-inverter,
         * and are now resolved through the component registry rather than inline here.
         */
        SimulationInputData inputRow = null;

        // Initialise battery state: on the FIRST simulated interval each battery starts at its discharge-stop
        // floor; a missing battery is replaced by the shared null battery so the rest of the code treats it
        // uniformly. "First interval" is keyed off the output list, not the absolute row index, so a windowed
        // axis (whose first interval may be a stored row K>0) still initialises correctly (Phase 4b/b2.3).
        boolean firstInterval = outputRows.isEmpty();
        for (InverterComponent inv : inputDataMap.values()) {
            if (null == inputRow) inputRow = inv.inputRow(row);
            inv.prepareForRun(firstInterval);
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
        for (InverterComponent inv : inputDataMap.values())
            tPV += inv.dcGeneration(row);

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
        // The canonical UTC instant for this interval: the stored value, or derived from date+mod for legacy
        // NULL rows. ALL time-of-day logic below is derived from this instant (month, day-of-week, minute-of-
        // day, and the per-day EV-divert key) so the engine is driven by milliseconds rather than the row's
        // wall-clock strings. No zone conversion happens in the sim — the grid is already UTC. The output
        // wall-clock fields are still copied from the input row, so behaviour is unchanged for the 2001 grid.
        long millis = (inputRow.getMillisSinceEpoch() != null) ? inputRow.getMillisSinceEpoch()
                : SimTime.fromDateAndMinuteOfDay(inputRow.getDate(), inputRow.getMod(), ZoneOffset.UTC);
        LocalDateTime intervalTime = SimTime.toLocalDateTime(millis, ZoneOffset.UTC);
        int month = intervalTime.getMonthValue();
        int dayOfWeek = intervalTime.getDayOfWeek().getValue();    // 1 (Mon) .. 7 (Sun), matching the model
        int minuteOfDay = intervalTime.getHour() * 60 + intervalTime.getMinute();
        int evDivertDay = (int) Math.floorDiv(millis, 86_400_000L); // UTC epoch-day: per-day EV-divert key
        outputRow.setMillisSinceEpoch(millis);
        outputRow.setGridToBattery(purchaseShiftingLoad);

        // Read-only per-interval context handed to components (plans/sim/component.md).
        IntervalContext ctx = new IntervalContext(millis, month, dayOfWeek, minuteOfDay, evDivertDay, 1d / 12d);

        // On the first simulated interval the hot water component starts from a zero previous temperature,
        // reproducing the legacy "no prior output row -> previousWaterTemp = 0" (and keeping a re-run or a
        // reused scenario deterministic). On later intervals the component carries its own temperature, so
        // the engine no longer reads waterTemp back from the previous output row.
        if (firstInterval) scenario.registry.hotWater().resetWaterTemp();

        /*
         * SCHEDULED DEMAND (Phase A/B): hot water immersion and EV scheduled charge contribute via the
         * component registry. Contributors are ordered water-then-EV, and the load is recorded before the
         * loop, so the accumulation order (load, then water, then EV) is byte-identical with the legacy
         * engine. Each contributor routes its own output column (immersionLoad / directEVcharge).
         */
        outputRow.setLoad(inputLoad); // Record the input load before extras -- there are separate counters for extras
        for (DemandContributor contributor : scenario.registry.demandContributors()) {
            DemandResult demandResult = contributor.demand(ctx);
            inputLoad += demandResult.kWh;
            applyOutputs(outputRow, demandResult.outputs);
        }
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

        List<InverterComponent> sortedInverters = new ArrayList<>(inputDataMap.values());
        sortedInverters.sort(Comparator.comparingLong(InverterComponent::inverterIndex));

        // Remaining AC throughput headroom per inverter this interval (Bug 3 fix: the AC rating now binds).
        Map<InverterComponent, Double> acRoom = new HashMap<>();
        for (InverterComponent d : sortedInverters)
            acRoom.put(d, d.maxInverterLoad() * h);

        // PASS 1: PV -> load, PV -> own battery (DC-DC), PV -> feed.
        for (InverterComponent d : sortedInverters) {
            double room = acRoom.get(d);
            double pvDc = d.dcGeneration(row);

            // (1) PV -> AC -> shared load
            double toLoad = min(min(pvDc * d.dc2acLoss(), room), remLoad);
            if (toLoad > 0) {
                pv2load += toLoad;
                remLoad -= toLoad;
                room -= toLoad;
                pvDc -= toLoad / d.dc2acLoss();
            }

            // (2) PV surplus (DC) -> own battery via DC-DC (captures PV the AC stage cannot pass)
            double dcToBatt = min(pvDc, d.getChargeCapacity());
            if (dcToBatt > 0) {
                d.adjustSoc(dcToBatt * d.dc2dcLoss()); // dc2dcLoss is the kept fraction
                pv2charge += dcToBatt;
                pvDc -= dcToBatt;
            }

            // (3) PV surplus -> AC -> feed, within AC headroom and the shared export cap.
            //     minExcess suppresses micro-export (kept from the original model).
            double pvAcFeed = min(min(pvDc * d.dc2acLoss(), room), remExport);
            if (pvAcFeed > d.minExcess()) {
                feed += pvAcFeed;
                room -= pvAcFeed;
                remExport -= pvAcFeed;
            }
            // any remaining PV is curtailed (clipped)
            acRoom.put(d, room);
        }

        // PASS 2: remaining load from battery (per the inverter's own strategy), then grid.
        // Each inverter decides independently whether to discharge its battery before the grid covers the
        // residual load; a non-null forcedStrategy overrides them all (test isolation).
        for (InverterComponent d : sortedInverters) {
            if (remLoad <= 0) break;
            DispatchStrategy eff = (forcedStrategy != null) ? forcedStrategy : d.dispatchStrategy();
            if (eff.dischargeBatteryForLoad()) {
                double room = acRoom.get(d);
                if (room <= 0) continue;
                double dcAvail = d.getDischargeCapacity(row); // DC kWh available above the discharge stop
                double acFromBatt = min(min(dcAvail * d.dc2dcLoss() * d.dc2acLoss(), room), remLoad);
                if (acFromBatt > 0) {
                    // DC drawn from the cells to deliver acFromBatt at AC, incl. storage loss (D2).
                    double dcDrawn = (acFromBatt / (d.dc2dcLoss() * d.dc2acLoss())) * (1 + d.storageLoss() / 100d);
                    d.adjustSoc(-dcDrawn);
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
         * DIVERSIONS (EV AND HOT WATER) — surplus PV (feed) diverted to EV charging / hot water.
         *
         * The mechanics and per-component state (water temperature, EV daily cap) live in the sinks; the
         * ORDER is a strategy resolved per interval (registry.divertOrder, the divert analogue of the
         * dispatch strategy): a config flag (EVDivert.isEv1st()) selects water-first vs EV-first, and the
         * engine makes a SINGLE ordered pass. Each sink absorbs once, so the legacy double-heat bug (water
         * absorbed twice) cannot occur; residual after a higher-priority sink flows to the next. Each sink
         * reports its absorbed energy in its own output channel. (immersionLoad was routed in the demand
         * phase above; waterTemp is committed by the hot-water component after the pass.)
         */
        Map<OutputChannel, Double> divertOutputs = new EnumMap<>(OutputChannel.class);
        divertOutputs.put(OutputChannel.DIV_TO_WATER, 0d);
        divertOutputs.put(OutputChannel.DIV_TO_EV, 0d);
        for (SurplusSink sink : scenario.registry.divertOrder(ctx)) {
            double absorbed = sink.absorb(feed, ctx);
            feed -= absorbed;
            divertOutputs.put(sink.divertChannel(), absorbed);
        }
        divertOutputs.put(OutputChannel.WATER_TEMP, scenario.registry.hotWater().commitWaterTemp());
        applyOutputs(outputRow, divertOutputs);

        /*
         * FORCED DISCHARGE TO GRID
         * Battery -> grid export for inverters scheduled to discharge now. Export draws on the shared export
         * headroom that remains after PV feed and on the inverter's remaining AC headroom (D3); the cells give
         * up DC including the DC-DC + DC->AC conversion and storage loss (D2).
         */
        for (InverterComponent d : sortedInverters) {
            if (!d.isForcedDischargeToGrid(row)) continue;
            double room = acRoom.get(d);
            double stopKWh = (d.forcedDischargeStopAtPercent(row) / 100d) * d.batterySize();
            double soc = d.soc();
            if (remExport <= 0 || room <= 0 || soc <= stopKWh) continue;
            double wantedRate = d.forcedDischargeRate(row) / 12D;                 // AC kWh this interval (max)
            // DC available above the stop, expressed as deliverable AC.
            double acFromAvailable = (soc - stopKWh) / (1 + d.storageLoss() / 100d) * d.dc2dcLoss() * d.dc2acLoss();
            double acExport = min(min(min(remExport, room), wantedRate), acFromAvailable);
            if (acExport > 0) {
                double dcDrawn = (acExport / (d.dc2dcLoss() * d.dc2acLoss())) * (1 + d.storageLoss() / 100d);
                d.adjustSoc(-dcDrawn);
                b2g += acExport;
                feed += acExport;
                remExport -= acExport;
                acRoom.put(d, room - acExport);
            }
        }

        double totalSOC = 0;
        for (InverterComponent d : sortedInverters) totalSOC += d.soc();

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
     * Routes a component's {@link OutputChannel} contributions onto the output row, so the engine does
     * not hardcode which component writes which column (component-registration refactor). A future
     * component's new channel is handled by adding a case here, not by editing the per-interval loop.
     */
    private static void applyOutputs(ScenarioSimulationData outputRow, Map<OutputChannel, Double> outputs) {
        for (Map.Entry<OutputChannel, Double> e : outputs.entrySet()) {
            switch (e.getKey()) {
                case DIRECT_EV_CHARGE: outputRow.setDirectEVcharge(e.getValue()); break;
                case IMMERSION_LOAD:   outputRow.setImmersionLoad(e.getValue());  break;
                case DIV_TO_WATER:     outputRow.setKWHDivToWater(e.getValue());  break;
                case DIV_TO_EV:        outputRow.setKWHDivToEV(e.getValue());     break;
                case WATER_TEMP:       outputRow.setWaterTemp(e.getValue());      break;
            }
        }
    }

    /**
     * Charges batteries from the grid if scheduled and needed, based on user load shift schedules.
     * Only charges batteries that are below their stop threshold.
     * @param inputDataMap Map of inverters to their input data.
     * @param row The time step index.
     * @return The total extra load added from grid charging.
     */
    private static double chargeBatteriesFromGridIfNeeded(Map<Inverter, InputData> inputDataMap, int row) {
        double totalExtraLoad = 0;
        for (InverterComponent inv : inputDataMap.values()) {
            // isChargeFromGrid implies a load-shift schedule exists, so the stop-at lookup below is safe;
            // gating on it first is byte-identical (the stop-at computation has no side effect otherwise).
            if (inv.isChargeFromGrid(row)) {
                double stopAt = (inv.chargeFromGridStopAtPercent(row) / 100d) * inv.batterySize();
                if (inv.soc() < stopAt) {
                    // D5: grid charging crosses AC->DC then DC-DC, so less is stored than is bought.
                    double chargeCapacity = inv.getChargeCapacity();          // AC drawn from the grid
                    inv.adjustSoc(chargeCapacity * inv.ac2dcLoss() * inv.dc2dcLoss());
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
    static class InputData implements InverterComponent {
        final long id;
        final double dc2acLoss;
        final double ac2dcLoss;
        final double dc2dcLoss;
        final double storageLoss;
        final double maxInverterLoad;
        final double minExcess;
        final DispatchStrategy strategy;
        List<SimulationInputData> simulationInputData;
        Battery mBattery;
        ChargeFromGrid mChargeFromGrid;
        final ForceDischargeToGrid mForceDischargeToGrid;

        // Volatile state members
        double soc = 0d;

        /**
         * Constructs InputData for an inverter and its inverter-bound state. Hot water and EV are
         * scenario-level (see {@link ScenarioInputs}) and are not held here.
         * @param inverter The inverter.
         * @param iData Simulation input data (load, PV, etc.).
         * @param battery Associated battery (nullable).
         * @param chargeFromGrid Charge-from-grid (load shift) schedule (nullable).
         * @param forceDischargeToGrid Forced discharge-to-grid schedule (nullable).
         */
        InputData(Inverter inverter, List<SimulationInputData> iData,
                  Battery battery, ChargeFromGrid chargeFromGrid, ForceDischargeToGrid forceDischargeToGrid) {
            id = inverter.getInverterIndex();
            dc2acLoss = (100d - inverter.getDc2acLoss()) / 100d;
            ac2dcLoss = (100d - inverter.getAc2dcLoss()) / 100d;
            dc2dcLoss = (100d - inverter.getDc2dcLoss()) / 100d;
            storageLoss = (null == battery) ? 0 : battery.getStorageLoss();
            maxInverterLoad = inverter.getMaxInverterLoad();
            minExcess = inverter.getMinExcess();
            strategy = DispatchStrategy.fromMode(inverter.getDispatchMode());
            simulationInputData = iData;
            mBattery = battery;
            mChargeFromGrid = chargeFromGrid;
            mForceDischargeToGrid = forceDischargeToGrid;
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

        // ---- Capability interface (Phase C). These expose the inverter's bus capabilities so the engine
        //      consults methods rather than fields. The package-private fields above are retained for the
        //      white-box unit tests; each accessor simply returns the corresponding field/derived value, so
        //      routing the bus solve through these methods is byte-identical. ----

        @Override public SimulationInputData inputRow(int row) { return simulationInputData.get(row); }
        @Override public double dcGeneration(int row) { return simulationInputData.get(row).tpv; }

        @Override public double soc() { return soc; }
        @Override public void adjustSoc(double deltaKWh) { soc += deltaKWh; }
        @Override public void setSoc(double socKWh) { soc = socKWh; }
        @Override public double storageLoss() { return storageLoss; }
        @Override public double batterySize() { return mBattery.getBatterySize(); }

        @Override public long inverterIndex() { return id; }
        @Override public double maxInverterLoad() { return maxInverterLoad; }
        @Override public double minExcess() { return minExcess; }
        @Override public double dc2acLoss() { return dc2acLoss; }
        @Override public double ac2dcLoss() { return ac2dcLoss; }
        @Override public double dc2dcLoss() { return dc2dcLoss; }
        @Override public DispatchStrategy dispatchStrategy() { return strategy; }

        @Override public boolean isChargeFromGrid(int row) { return isCFG(row); }
        @Override public double chargeFromGridStopAtPercent(int row) { return mChargeFromGrid.mStopAt.get(row); }

        @Override public boolean isForcedDischargeToGrid(int row) {
            ForceDischargeToGrid fd = mForceDischargeToGrid;
            return !(null == fd) && !(null == fd.mD2G) && fd.mD2G.get(row);
        }
        @Override public double forcedDischargeStopAtPercent(int row) { return mForceDischargeToGrid.mStopAt.get(row); }
        @Override public double forcedDischargeRate(int row) { return mForceDischargeToGrid.mRate.get(row); }

        @Override public void prepareForRun(boolean firstInterval) {
            if (null == mBattery) {
                soc = 0;
                mBattery = M_NULL_BATTERY;
            }
            if (firstInterval) soc = getDischargeStop();
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
