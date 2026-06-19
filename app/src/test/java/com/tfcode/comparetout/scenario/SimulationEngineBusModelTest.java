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

import static org.junit.Assert.assertEquals;

import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.DischargeToGrid;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;
import com.tfcode.comparetout.model.scenario.SimulationInputData;
import com.tfcode.comparetout.scenario.sim.DispatchStrategy;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 3 physics tests: the per-inverter DC/AC bus model and the dispatch Strategy
 * (see {@code plans/sim/phase3-design.md}). These pin the intended new behaviour that drives the
 * re-seeded golden deltas: the AC rating binds (Bug 3), surplus PV charges the own battery via DC-DC
 * before being curtailed, DC-DC loss is symmetric (Bug 2), batteries only charge from their own PV,
 * and the strategy controls battery-vs-grid ordering.
 */
public class SimulationEngineBusModelTest {

    private static final long ID = 1L;
    private static final double TOL = 1e-6;

    private static ScenarioInputs noScenarioExtras(double exportMax) {
        return new ScenarioInputs(null, null, null, null, null, exportMax);
    }

    private static SimulationEngine.InputData input(Inverter inv, Battery battery, double load, double pv) {
        List<SimulationInputData> series = SimSeries.constant(2, load, pv);
        return new SimulationEngine.InputData(inv, series, battery, null, null);
    }

    /** AC rating binds: PV (×dc2ac) above the inverter's AC throughput is capped; the rest is curtailed. */
    @Test
    public void acRatingBindsAndCurtailsSurplusWithoutBattery() {
        Inverter inv = InverterBuilder.anInverter().index(1).maxInverterLoad(5.0).lossless().build();
        Map<Inverter, SimulationEngine.InputData> map = new LinkedHashMap<>();
        map.put(inv, input(inv, null, 0.1, 1.0)); // PV 1.0 kWh DC, far above 5kW/12 = 0.41667

        ArrayList<ScenarioSimulationData> out = new ArrayList<>();
        SimulationEngine.processOneRow(ID, noScenarioExtras(10.0), out, 0, map);
        ScenarioSimulationData r = out.get(0);

        double acCap = 5.0 / 12d;
        assertEquals(0.1, r.getPvToLoad(), TOL);
        assertEquals("feed capped so load+feed equals the AC rating", acCap - 0.1, r.getFeed(), TOL);
        assertEquals(0.0, r.getBuy(), TOL);
    }

    /** Surplus PV beyond the AC rating charges the OWN battery via DC-DC rather than being clipped. */
    @Test
    public void dcDcChargingGrabsPvBeyondAcRating() {
        Inverter inv = InverterBuilder.anInverter().index(1).maxInverterLoad(5.0).lossless().build();
        Battery battery = BatteryBuilder.aBattery().index(1).size(10.0).dischargeStopPercent(0)
                .maxChargeDischarge(1.0, 1.0).storageLossPercent(0).build();
        Map<Inverter, SimulationEngine.InputData> map = new LinkedHashMap<>();
        SimulationEngine.InputData d = input(inv, battery, 0.1, 1.0);
        map.put(inv, d);

        ArrayList<ScenarioSimulationData> out = new ArrayList<>();
        SimulationEngine.processOneRow(ID, noScenarioExtras(0.0), out, 0, map); // export 0: no feed
        d.setSoc(5.0);                                                            // mid SOC: flat charge region
        SimulationEngine.processOneRow(ID, noScenarioExtras(0.0), out, 1, map);
        ScenarioSimulationData r = out.get(1);

        // PV 1.0 DC, 0.1 to load (DC 0.1) leaves 0.9 DC; battery accepts maxCharge 1.0 ⇒ all 0.9 charges,
        // even though that is well beyond the 0.41667 AC cap.
        assertEquals(0.9, r.getPvToCharge(), TOL);
        assertEquals(5.9, d.soc(), TOL);
        assertEquals(0.0, r.getFeed(), TOL);
        assertEquals(0.0, r.getBuy(), TOL);
    }

    /** dc2dcLoss applies symmetrically: 10% loss on charge stores 0.9× the DC routed to the battery. */
    @Test
    public void dcDcLossIsAppliedOnCharge() {
        Inverter inv = InverterBuilder.anInverter().index(1).maxInverterLoad(50.0).losses(0, 0, 10).build();
        Battery battery = BatteryBuilder.aBattery().index(1).size(10.0).dischargeStopPercent(0)
                .maxChargeDischarge(1.0, 1.0).storageLossPercent(0).build();
        Map<Inverter, SimulationEngine.InputData> map = new LinkedHashMap<>();
        SimulationEngine.InputData d = input(inv, battery, 0.0, 1.0);
        map.put(inv, d);

        ArrayList<ScenarioSimulationData> out = new ArrayList<>();
        SimulationEngine.processOneRow(ID, noScenarioExtras(0.0), out, 0, map);
        d.setSoc(5.0);
        SimulationEngine.processOneRow(ID, noScenarioExtras(0.0), out, 1, map);

        assertEquals(1.0, out.get(1).getPvToCharge(), TOL); // 1.0 DC routed to battery
        assertEquals(5.0 + 0.9, d.soc(), TOL);                // only 0.9 stored (dc2dc 0.9)
    }

    /** dc2dcLoss also applies on discharge (Bug 2): delivering 0.5 AC draws 0.5/0.9 DC from the cells. */
    @Test
    public void dcDcLossIsAppliedOnDischarge() {
        Inverter inv = InverterBuilder.anInverter().index(1).maxInverterLoad(50.0).losses(0, 0, 10).build();
        Battery battery = BatteryBuilder.aBattery().index(1).size(10.0).dischargeStopPercent(0)
                .maxChargeDischarge(1.0, 1.0).storageLossPercent(0).build();
        Map<Inverter, SimulationEngine.InputData> map = new LinkedHashMap<>();
        SimulationEngine.InputData d = input(inv, battery, 0.5, 0.0); // load 0.5, no PV
        map.put(inv, d);

        ArrayList<ScenarioSimulationData> out = new ArrayList<>();
        SimulationEngine.processOneRow(ID, noScenarioExtras(0.0), out, 0, map);
        d.setSoc(5.0);
        SimulationEngine.processOneRow(ID, noScenarioExtras(0.0), out, 1, map);
        ScenarioSimulationData r = out.get(1);

        assertEquals(0.5, r.getBatToLoad(), TOL);
        assertEquals(0.0, r.getBuy(), TOL);
        assertEquals(5.0 - 0.5 / 0.9, d.soc(), TOL); // dc2ac=1.0, storageLoss=0 ⇒ DC drawn = 0.5/0.9
    }

    /** A battery only charges from its OWN inverter's PV (per-inverter DC coupling). */
    @Test
    public void batteryChargesOnlyFromOwnPv() {
        Inverter inv1 = InverterBuilder.anInverter().index(1).maxInverterLoad(5.0).lossless().build();
        Inverter inv2 = InverterBuilder.anInverter().index(2).maxInverterLoad(5.0).lossless().build();
        Battery b1 = BatteryBuilder.aBattery().index(1).size(10.0).dischargeStopPercent(20)
                .maxChargeDischarge(1.0, 1.0).storageLossPercent(0).build();
        Battery b2 = BatteryBuilder.aBattery().index(2).size(10.0).dischargeStopPercent(20)
                .maxChargeDischarge(1.0, 1.0).storageLossPercent(0).build();
        SimulationEngine.InputData d1 = input(inv1, b1, 0.1, 1.0); // PV only on inverter 1
        SimulationEngine.InputData d2 = input(inv2, b2, 0.1, 0.0);
        Map<Inverter, SimulationEngine.InputData> map = new LinkedHashMap<>();
        map.put(inv1, d1);
        map.put(inv2, d2);

        ArrayList<ScenarioSimulationData> out = new ArrayList<>();
        SimulationEngine.processOneRow(ID, noScenarioExtras(0.0), out, 0, map);
        d1.setSoc(5.0);
        d2.setSoc(5.0);
        SimulationEngine.processOneRow(ID, noScenarioExtras(0.0), out, 1, map);

        // Inverter 1 served the shared load and charged its own battery; inverter 2's battery is untouched.
        org.junit.Assert.assertTrue("own battery charged", d1.soc() > 5.0);
        assertEquals("other battery unchanged", 5.0, d2.soc(), TOL);
    }

    /** Dispatch strategy controls battery-vs-grid: LoadGridBattery preserves the battery for load. */
    @Test
    public void dispatchStrategyControlsBatteryUseForLoad() {
        // LoadBatteryGrid (default): battery serves the load. Large AC rating so the cap doesn't bind here.
        Inverter invA = InverterBuilder.anInverter().index(1).maxInverterLoad(50.0).lossless().build();
        Battery batA = BatteryBuilder.aBattery().index(1).size(10.0).dischargeStopPercent(0)
                .maxChargeDischarge(1.0, 1.0).storageLossPercent(0).build();
        SimulationEngine.InputData dA = input(invA, batA, 0.5, 0.0);
        Map<Inverter, SimulationEngine.InputData> mapA = new LinkedHashMap<>();
        mapA.put(invA, dA);
        ArrayList<ScenarioSimulationData> outA = new ArrayList<>();
        SimulationEngine.processOneRow(ID, noScenarioExtras(0.0), DispatchStrategy.LOAD_BATTERY_GRID, outA, 0, mapA);
        dA.setSoc(5.0);
        SimulationEngine.processOneRow(ID, noScenarioExtras(0.0), DispatchStrategy.LOAD_BATTERY_GRID, outA, 1, mapA);
        assertEquals(0.5, outA.get(1).getBatToLoad(), TOL);
        assertEquals(0.0, outA.get(1).getBuy(), TOL);

        // LoadGridBattery: battery preserved, load met from grid.
        Inverter invB = InverterBuilder.anInverter().index(1).maxInverterLoad(50.0).lossless().build();
        Battery batB = BatteryBuilder.aBattery().index(1).size(10.0).dischargeStopPercent(0)
                .maxChargeDischarge(1.0, 1.0).storageLossPercent(0).build();
        SimulationEngine.InputData dB = input(invB, batB, 0.5, 0.0);
        Map<Inverter, SimulationEngine.InputData> mapB = new LinkedHashMap<>();
        mapB.put(invB, dB);
        ArrayList<ScenarioSimulationData> outB = new ArrayList<>();
        SimulationEngine.processOneRow(ID, noScenarioExtras(0.0), DispatchStrategy.LOAD_GRID_BATTERY, outB, 0, mapB);
        dB.setSoc(5.0);
        SimulationEngine.processOneRow(ID, noScenarioExtras(0.0), DispatchStrategy.LOAD_GRID_BATTERY, outB, 1, mapB);
        assertEquals(0.0, outB.get(1).getBatToLoad(), TOL);
        assertEquals(0.5, outB.get(1).getBuy(), TOL);
        assertEquals(5.0, dB.soc(), TOL);
    }

    /**
     * Phase 4: each inverter uses its OWN persisted dispatch mode (no forced strategy). One inverter set to
     * load→battery→grid discharges its battery; a sibling set to load→grid→battery preserves its battery and
     * lets the grid cover the residual load — within a single shared simulation.
     */
    @Test
    public void perInverterDispatchModeIsHonoured() {
        // inv1: battery-before-grid (default). inv2: grid-before-battery. Large AC rating so the cap is moot.
        Inverter inv1 = InverterBuilder.anInverter().index(1).maxInverterLoad(50.0).lossless()
                .dispatchMode(Inverter.DISPATCH_LOAD_BATTERY_GRID).build();
        Inverter inv2 = InverterBuilder.anInverter().index(2).maxInverterLoad(50.0).lossless()
                .dispatchMode(Inverter.DISPATCH_LOAD_GRID_BATTERY).build();
        Battery b1 = BatteryBuilder.aBattery().index(1).size(10.0).dischargeStopPercent(0)
                .maxChargeDischarge(1.0, 1.0).storageLossPercent(0).build();
        Battery b2 = BatteryBuilder.aBattery().index(2).size(10.0).dischargeStopPercent(0)
                .maxChargeDischarge(1.0, 1.0).storageLossPercent(0).build();
        // Shared load 1.5, no PV. The load series is carried by both inverters but counted once by the engine.
        SimulationEngine.InputData d1 = input(inv1, b1, 1.5, 0.0);
        SimulationEngine.InputData d2 = input(inv2, b2, 1.5, 0.0);
        Map<Inverter, SimulationEngine.InputData> map = new LinkedHashMap<>();
        map.put(inv1, d1);
        map.put(inv2, d2);

        ArrayList<ScenarioSimulationData> out = new ArrayList<>();
        SimulationEngine.processOneRow(ID, noScenarioExtras(0.0), out, 0, map); // production overload: per-inverter mode
        d1.setSoc(5.0);
        d2.setSoc(5.0);
        SimulationEngine.processOneRow(ID, noScenarioExtras(0.0), out, 1, map);
        ScenarioSimulationData r = out.get(1);

        // inv1 discharges its battery for 1.0 (its maxDischarge); inv2 preserves its battery, grid covers 0.5.
        assertEquals(1.0, r.getBatToLoad(), TOL);
        assertEquals(0.5, r.getBuy(), TOL);
        assertEquals("battery-before-grid inverter discharged", 4.0, d1.soc(), TOL);
        assertEquals("grid-before-battery inverter preserved its battery", 5.0, d2.soc(), TOL);
    }

    /**
     * Forced discharge to grid: a scheduled inverter exports its battery at the configured rate (rate
     * binds below the export cap and the available headroom), the export shows as feed + battery2grid, and
     * the SOC is drawn down accordingly. Schedules span the full 2001 calendar (105120 rows).
     */
    @Test
    public void forcedDischargeExportsBatteryToGridAtRate() {
        Inverter inv = InverterBuilder.anInverter().index(1).maxInverterLoad(50.0).lossless().build();
        Battery battery = BatteryBuilder.aBattery().index(1).size(10.0).dischargeStopPercent(50) // seeds SOC 5.0
                .maxChargeDischarge(1.0, 1.0).storageLossPercent(0).build();
        DischargeToGrid d2g = new DischargeToGrid();
        d2g.setBegin(0);
        d2g.setEnd(23);
        d2g.setStopAt(20.0); // floor at 2.0 kWh
        d2g.setRate(5.0);    // 5 kW -> 0.41667 kWh / interval
        SimulationEngine.ForceDischargeToGrid fd2g =
                new SimulationEngine.ForceDischargeToGrid(Collections.singletonList(d2g), 105120);

        SimulationEngine.InputData d = new SimulationEngine.InputData(
                inv, SimSeries.constant(2, 0.0, 0.0), battery, null, fd2g); // no load, no PV
        Map<Inverter, SimulationEngine.InputData> map = new LinkedHashMap<>();
        map.put(inv, d);

        ArrayList<ScenarioSimulationData> out = new ArrayList<>();
        SimulationEngine.processOneRow(ID, noScenarioExtras(10.0), out, 0, map); // export cap 10kW: not binding
        ScenarioSimulationData r = out.get(0);

        double rate = 5.0 / 12d;
        assertEquals("exports at the configured rate", rate, r.getBattery2Grid(), TOL);
        assertEquals("the export is also recorded as feed", rate, r.getFeed(), TOL);
        assertEquals("SOC drawn down by the exported energy (lossless)", 5.0 - rate, d.soc(), TOL);
        assertEquals(0.0, r.getBuy(), TOL);
    }

    /**
     * The export cap is shared across inverters: two inverters that each have feedable surplus cannot
     * together export more than {@code exportMax}. Before the shared accounting this would have exported
     * twice the cap.
     */
    @Test
    public void sharedExportCapLimitsTotalFeedAcrossInverters() {
        Inverter inv1 = InverterBuilder.anInverter().index(1).maxInverterLoad(50.0).lossless().build();
        Inverter inv2 = InverterBuilder.anInverter().index(2).maxInverterLoad(50.0).lossless().build();
        // Batteries full (discharge stop 100% -> seeded full) so PV cannot be absorbed by charging.
        Battery b1 = BatteryBuilder.aBattery().index(1).size(10.0).dischargeStopPercent(100)
                .maxChargeDischarge(1.0, 1.0).storageLossPercent(0).build();
        Battery b2 = BatteryBuilder.aBattery().index(2).size(10.0).dischargeStopPercent(100)
                .maxChargeDischarge(1.0, 1.0).storageLossPercent(0).build();
        Map<Inverter, SimulationEngine.InputData> map = new LinkedHashMap<>();
        map.put(inv1, input(inv1, b1, 0.0, 1.0)); // PV surplus on both
        map.put(inv2, input(inv2, b2, 0.0, 1.0));

        ArrayList<ScenarioSimulationData> out = new ArrayList<>();
        SimulationEngine.processOneRow(ID, noScenarioExtras(3.0), out, 0, map); // shared cap = 3kW -> 0.25 kWh
        assertEquals("total feed is bounded by the shared export cap", 3.0 / 12d, out.get(0).getFeed(), TOL);
    }
}
