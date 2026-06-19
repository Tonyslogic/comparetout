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

import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.DischargeToGrid;
import com.tfcode.comparetout.model.scenario.EVCharge;
import com.tfcode.comparetout.model.scenario.EVDivert;
import com.tfcode.comparetout.model.scenario.HWSchedule;
import com.tfcode.comparetout.model.scenario.HWSystem;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;
import com.tfcode.comparetout.model.scenario.SimulationInputData;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Golden-master / characterization tests for {@link SimulationEngine#processOneRow}.
 *
 * <p>Phase 0 of the simulation-engine refactor (see {@code plans/sim/refactor.md}). These tests do
 * NOT assert specific numbers; they pin the engine's current full-output over representative,
 * multi-interval scenarios via {@link GoldenMaster}. They are the safety net for the refactor:
 * Phase 1 must keep every approved snapshot identical, and Phases 2–3 will change specific snapshots
 * deliberately (with the diff reviewed).</p>
 *
 * <p>The simulated period uses the engine's hardcoded 2001 calendar (see {@link SimSeries}); the
 * window is one day (288 five-minute intervals) so that stateful behaviour — battery SOC carry,
 * hourly hot-water steps, daily EV-divert totals — is exercised. Scenarios that use the schedule
 * builders ({@code ChargeFromGrid} / {@code ForceDischargeToGrid}) size those schedules to the full
 * year (110000 rows) because the builders populate them by iterating the entire 2001 calendar,
 * exactly as the existing unit tests do.</p>
 *
 * <p>Inputs are assembled into a {@link LinkedHashMap} rather than a {@link java.util.HashMap} so the
 * "first inverter" the engine reads for shared decisions (hot water, EV schedules, initial SOC
 * reference) is deterministic. See the note in the summary about the latent non-determinism this
 * works around in the current engine.</p>
 */
public class SimulationGoldenMasterTest {

    private static final long SCENARIO_ID = 1L;
    /** One simulated day at 5-minute resolution. */
    private static final int ROWS = 288;
    /** Schedule lists must span the whole 2001 calendar the builders iterate. */
    private static final int SCHEDULE_ROWS = 110000;

    private static final double FLAT_LOAD = 0.15;   // kWh per 5-minute interval (~1.8 kW)
    private static final double EXPORT_MAX = 6.0;    // kW

    @Test
    public void single_inverter_no_battery() {
        Inverter inverter = InverterBuilder.anInverter().index(1).name("INV1").build();
        List<SimulationInputData> series = SimSeries.generated(ROWS, i -> FLAT_LOAD,
                SimulationGoldenMasterTest::bellPV);

        Map<Inverter, SimulationEngine.InputData> map = new LinkedHashMap<>();
        map.put(inverter, input(inverter, series, null, null, null));

        GoldenMaster.verify("single_inverter_no_battery", GoldenMaster.serialize(run(noExtras(), map)));
    }

    @Test
    public void single_inverter_with_battery() {
        Inverter inverter = InverterBuilder.anInverter().index(1).name("INV1").build();
        Battery battery = BatteryBuilder.aBattery().index(1).size(5.0)
                .dischargeStopPercent(10).maxChargeDischarge(0.3, 0.3).storageLossPercent(1)
                .inverter("INV1").build();
        List<SimulationInputData> series = SimSeries.generated(ROWS, i -> FLAT_LOAD,
                SimulationGoldenMasterTest::bellPV);

        Map<Inverter, SimulationEngine.InputData> map = new LinkedHashMap<>();
        map.put(inverter, input(inverter, series, battery, null, null));

        GoldenMaster.verify("single_inverter_with_battery", GoldenMaster.serialize(run(noExtras(), map)));
    }

    /**
     * Two inverters, each with its own battery, sharing one load profile. This deliberately captures
     * the CURRENT multi-inverter behaviour — including the load-replication bug (each inverter holds
     * its own copy of the scenario load, which the engine then sums). Phase 2's fix will change this
     * snapshot on purpose.
     */
    @Test
    public void two_inverters_two_batteries() {
        Inverter inv1 = InverterBuilder.anInverter().index(1).name("INV1").build();
        Inverter inv2 = InverterBuilder.anInverter().index(2).name("INV2").build();
        Battery bat1 = BatteryBuilder.aBattery().index(1).size(5.0).dischargeStopPercent(10)
                .maxChargeDischarge(0.3, 0.3).storageLossPercent(1).inverter("INV1").build();
        Battery bat2 = BatteryBuilder.aBattery().index(2).size(5.0).dischargeStopPercent(10)
                .maxChargeDischarge(0.3, 0.3).storageLossPercent(1).inverter("INV2").build();

        // As in production, each inverter carries its own copy of the scenario load + its own PV.
        List<SimulationInputData> series1 = SimSeries.generated(ROWS, i -> FLAT_LOAD,
                SimulationGoldenMasterTest::bellPV);
        List<SimulationInputData> series2 = SimSeries.copyOf(series1);

        Map<Inverter, SimulationEngine.InputData> map = new LinkedHashMap<>();
        map.put(inv1, input(inv1, series1, bat1, null, null));
        map.put(inv2, input(inv2, series2, bat2, null, null));

        GoldenMaster.verify("two_inverters_two_batteries", GoldenMaster.serialize(run(noExtras(), map)));
    }

    /** Always-on load-shift: the battery charges from the grid up to the stop-at threshold. */
    @Test
    public void single_battery_load_shift() {
        Inverter inverter = InverterBuilder.anInverter().index(1).name("INV1").build();
        Battery battery = BatteryBuilder.aBattery().index(1).size(5.0)
                .dischargeStopPercent(10).maxChargeDischarge(0.3, 0.3).storageLossPercent(1)
                .inverter("INV1").build();

        LoadShift loadShift = new LoadShift();
        loadShift.setInverter("INV1");
        loadShift.setBegin(0);
        loadShift.setEnd(24);
        loadShift.setStopAt(80d);
        SimulationEngine.ChargeFromGrid cfg =
                new SimulationEngine.ChargeFromGrid(Collections.singletonList(loadShift), SCHEDULE_ROWS);

        // Low PV so charging is driven by the grid, not solar.
        List<SimulationInputData> series = SimSeries.constant(ROWS, FLAT_LOAD, 0d);

        Map<Inverter, SimulationEngine.InputData> map = new LinkedHashMap<>();
        map.put(inverter, input(inverter, series, battery, cfg, null));

        GoldenMaster.verify("single_battery_load_shift", GoldenMaster.serialize(run(noExtras(), map)));
    }

    /** Forced discharge to grid (all-day window), exporting battery energy down to the stop-at. */
    @Test
    public void single_battery_force_discharge() {
        Inverter inverter = InverterBuilder.anInverter().index(1).name("INV1").build();
        Battery battery = BatteryBuilder.aBattery().index(1).size(5.0)
                .dischargeStopPercent(50).maxChargeDischarge(0.3, 0.3).storageLossPercent(1)
                .inverter("INV1").build();

        DischargeToGrid d2g = new DischargeToGrid();
        d2g.setInverter("INV1");
        d2g.setBegin(0);
        d2g.setEnd(23);
        d2g.setStopAt(20d);
        d2g.setRate(5d);
        SimulationEngine.ForceDischargeToGrid fd2g =
                new SimulationEngine.ForceDischargeToGrid(Collections.singletonList(d2g), SCHEDULE_ROWS);

        List<SimulationInputData> series = SimSeries.constant(ROWS, FLAT_LOAD, 0d);

        Map<Inverter, SimulationEngine.InputData> map = new LinkedHashMap<>();
        map.put(inverter, input(inverter, series, battery, null, fd2g));

        GoldenMaster.verify("single_battery_force_discharge", GoldenMaster.serialize(run(noExtras(), map)));
    }

    /** Scheduled immersion heating between 02:00 and 06:00. */
    @Test
    public void hot_water_schedule() {
        Inverter inverter = InverterBuilder.anInverter().index(1).name("INV1").build();
        HWSystem hwSystem = new HWSystem();
        HWSchedule hwSchedule = new HWSchedule();
        hwSchedule.setBegin(2);
        hwSchedule.setEnd(6);
        List<HWSchedule> hwSchedules = Collections.singletonList(hwSchedule);

        List<SimulationInputData> series = SimSeries.generated(ROWS, i -> FLAT_LOAD,
                SimulationGoldenMasterTest::bellPV);

        Map<Inverter, SimulationEngine.InputData> map = new LinkedHashMap<>();
        map.put(inverter, input(inverter, series, null, null, null));
        ScenarioInputs scenario = new ScenarioInputs(hwSystem, false, hwSchedules, null, null, EXPORT_MAX);

        GoldenMaster.verify("hot_water_schedule", GoldenMaster.serialize(run(scenario, map)));
    }

    /** Scheduled EV charging between 02:00 and 06:00. */
    @Test
    public void ev_charge_schedule() {
        Inverter inverter = InverterBuilder.anInverter().index(1).name("INV1").build();
        EVCharge evCharge = new EVCharge();
        evCharge.setBegin(2);
        evCharge.setEnd(6);
        evCharge.setDraw(7.5);
        List<EVCharge> evCharges = Collections.singletonList(evCharge);

        List<SimulationInputData> series = SimSeries.generated(ROWS, i -> FLAT_LOAD,
                SimulationGoldenMasterTest::bellPV);

        Map<Inverter, SimulationEngine.InputData> map = new LinkedHashMap<>();
        map.put(inverter, input(inverter, series, null, null, null));
        ScenarioInputs scenario = new ScenarioInputs(null, false, null, evCharges, null, EXPORT_MAX);

        GoldenMaster.verify("ev_charge_schedule", GoldenMaster.serialize(run(scenario, map)));
    }

    /**
     * Surplus PV diverted with EV-divert <b>and</b> hot-water-divert both active, EV-first (water mops the
     * residual). With a modest daily EV cap, the cap binds mid-day and later surplus spills to water — so
     * this exercises the daily-total carry and the ordered single-pass divert. Pins corrected behaviour
     * introduced with the divert-ordering strategy (see plans/sim/component.md).
     */
    @Test
    public void ev_divert_ev_first() {
        GoldenMaster.verify("ev_divert_ev_first",
                GoldenMaster.serialize(run(divertScenario(true), divertMap())));
    }

    /**
     * As above but water-first (not ev1st): hot water takes the surplus, EV takes the residual, each once.
     * This is the path the legacy engine double-heated (water absorbed twice, feed double-debited); the
     * single-pass divert fixes it. Pins the corrected behaviour.
     */
    @Test
    public void ev_divert_water_first() {
        GoldenMaster.verify("ev_divert_water_first",
                GoldenMaster.serialize(run(divertScenario(false), divertMap())));
    }

    // --- helpers -------------------------------------------------------------------------------

    /** One inverter, no battery, bell-curve PV over a flat load — surplus to divert at midday. */
    private static Map<Inverter, SimulationEngine.InputData> divertMap() {
        Inverter inverter = InverterBuilder.anInverter().index(1).name("INV1").build();
        List<SimulationInputData> series = SimSeries.generated(ROWS, i -> FLAT_LOAD,
                SimulationGoldenMasterTest::bellPV);
        Map<Inverter, SimulationEngine.InputData> map = new LinkedHashMap<>();
        map.put(inverter, input(inverter, series, null, null, null));
        return map;
    }

    /** Hot-water divert on (no immersion schedule) + an all-day active EV divert with a modest daily cap. */
    private static ScenarioInputs divertScenario(boolean evFirst) {
        EVDivert evDivert = new EVDivert();
        evDivert.setBegin(0);
        evDivert.setEnd(24);
        evDivert.setActive(true);
        evDivert.setEv1st(evFirst);
        evDivert.setMinimum(0d);
        evDivert.setDailyMax(5d);
        return new ScenarioInputs(new HWSystem(), true, null, null,
                Collections.singletonList(evDivert), EXPORT_MAX);
    }

    /** A daily PV bell-curve: zero overnight, peaking at midday (kWh per 5-minute interval). */
    private static double bellPV(double rowIndex) {
        int i = (int) rowIndex;
        int start = 72;  // 06:00
        int end = 216;   // 18:00
        if (i < start || i > end) return 0d;
        return 0.5 * Math.sin(Math.PI * (i - start) / (double) (end - start));
    }

    /** Scenario-level inputs with no hot water / EV, using the scenario's export limit. */
    private static ScenarioInputs noExtras() {
        return new ScenarioInputs(null, false, null, null, null, EXPORT_MAX);
    }

    private static List<ScenarioSimulationData> run(ScenarioInputs scenario, Map<Inverter, SimulationEngine.InputData> map) {
        ArrayList<ScenarioSimulationData> out = new ArrayList<>();
        for (int row = 0; row < ROWS; row++) {
            SimulationEngine.processOneRow(SCENARIO_ID, scenario, out, row, map);
        }
        return out;
    }

    private static SimulationEngine.InputData input(
            Inverter inverter, List<SimulationInputData> series, Battery battery,
            SimulationEngine.ChargeFromGrid cfg, SimulationEngine.ForceDischargeToGrid fd2g) {
        return new SimulationEngine.InputData(inverter, series, battery, cfg, fd2g);
    }
}
