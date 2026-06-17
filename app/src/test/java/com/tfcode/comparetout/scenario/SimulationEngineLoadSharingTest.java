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

import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;
import com.tfcode.comparetout.model.scenario.SimulationInputData;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Regression test for Bug 1: load was replicated, not shared, across inverters.
 *
 * <p>Phase 2 of the simulation-engine refactor (see {@code plans/sim/refactor.md}). Each inverter's
 * {@link SimulationEngine.InputData} carries the same scenario-level load series, and the old engine
 * summed it once per inverter — so a two-inverter scenario drew twice the real load from the grid.
 * The fix counts load exactly once. These assertions fail on the pre-fix engine and pass after it.</p>
 */
public class SimulationEngineLoadSharingTest {

    private static final long SCENARIO_ID = 1L;
    private static final double LOAD = 0.4;   // kWh per interval
    private static final double EXPORT_MAX = 6.0;

    /** Baseline: a single inverter with no PV and no battery buys exactly the load. */
    @Test
    public void singleInverter_buysTheLoadOnce() {
        Inverter inv = InverterBuilder.anInverter().index(1).name("INV1").lossless().build();
        Map<Inverter, SimulationEngine.InputData> map = new LinkedHashMap<>();
        map.put(inv, noBatteryInput(inv));

        assertEquals(LOAD, runRow0(map).getBuy(), 1e-9);
    }

    /**
     * Two inverters sharing one load profile (no PV, no battery) must still buy the load exactly
     * once. Before the fix this returned 2 x LOAD.
     */
    @Test
    public void twoInverters_doNotDoubleCountLoad() {
        Inverter inv1 = InverterBuilder.anInverter().index(1).name("INV1").lossless().build();
        Inverter inv2 = InverterBuilder.anInverter().index(2).name("INV2").lossless().build();
        Map<Inverter, SimulationEngine.InputData> map = new LinkedHashMap<>();
        map.put(inv1, noBatteryInput(inv1));
        map.put(inv2, noBatteryInput(inv2));

        assertEquals("two inverters must share, not replicate, the scenario load",
                LOAD, runRow0(map).getBuy(), 1e-9);
    }

    /** The recorded load figure is the scenario load, regardless of inverter count. */
    @Test
    public void recordedLoadIsSceneLoad_notMultiplied() {
        Inverter inv1 = InverterBuilder.anInverter().index(1).name("INV1").lossless().build();
        Inverter inv2 = InverterBuilder.anInverter().index(2).name("INV2").lossless().build();
        Map<Inverter, SimulationEngine.InputData> map = new LinkedHashMap<>();
        map.put(inv1, noBatteryInput(inv1));
        map.put(inv2, noBatteryInput(inv2));

        assertEquals(LOAD, runRow0(map).getLoad(), 1e-9);
    }

    private static SimulationEngine.InputData noBatteryInput(Inverter inverter) {
        List<SimulationInputData> series = SimSeries.constant(2, LOAD, 0d); // no PV
        return new SimulationEngine.InputData(inverter, series, null, null,
                null, false, null, null, null, null, EXPORT_MAX);
    }

    private static ScenarioSimulationData runRow0(Map<Inverter, SimulationEngine.InputData> map) {
        ArrayList<ScenarioSimulationData> out = new ArrayList<>();
        SimulationEngine.processOneRow(SCENARIO_ID, out, 0, map);
        return out.get(0);
    }
}
