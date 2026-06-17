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

import com.tfcode.comparetout.model.scenario.EVCharge;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;
import com.tfcode.comparetout.model.scenario.SimulationInputData;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests the scenario-level {@code processOneRow} overload added in Phase 2b.
 *
 * <p>Hot water and EV are scenario-level (see {@link ScenarioInputs}). Because the engine now reads
 * them from a {@code ScenarioInputs} rather than from "whichever inverter is first in the map", the
 * result no longer depends on inverter map order — the latent {@code HashMap}-order non-determinism is
 * gone. This test pins that: the same scenario inputs produce identical output whatever the inverter
 * iteration order.</p>
 */
public class SimulationEngineScenarioInputsTest {

    private static final long SCENARIO_ID = 1L;
    private static final double EXPORT_MAX = 6.0;
    private static final double LOAD = 0.2;

    @Test
    public void scheduledEvIsScenarioLevel_andOrderIndependent() {
        // EV charging scheduled all day at scenario level (6 kW draw -> 0.5 kWh per 5-minute interval).
        EVCharge ev = new EVCharge();
        ev.setBegin(0);
        ev.setEnd(24);
        ev.setDraw(6.0);
        ScenarioInputs scenario = new ScenarioInputs(null, null, null,
                Collections.singletonList(ev), null, EXPORT_MAX);

        Inverter a = InverterBuilder.anInverter().index(1).name("A").lossless().build();
        Inverter b = InverterBuilder.anInverter().index(2).name("B").lossless().build();

        ScenarioSimulationData ab = runRow0(scenario, order(a, b));
        ScenarioSimulationData ba = runRow0(scenario, order(b, a));

        assertEquals("scheduled EV charge applied once from scenario inputs", 6.0 / 12d, ab.getDirectEVcharge(), 1e-9);
        assertEquals("result is independent of inverter map order", ab.getDirectEVcharge(), ba.getDirectEVcharge(), 1e-9);
        assertEquals(ab.getBuy(), ba.getBuy(), 1e-9);
        assertEquals(ab.getLoad(), ba.getLoad(), 1e-9);
    }

    private static Map<Inverter, SimulationEngine.InputData> order(Inverter first, Inverter second) {
        Map<Inverter, SimulationEngine.InputData> map = new LinkedHashMap<>();
        map.put(first, noBatteryInput(first));
        map.put(second, noBatteryInput(second));
        return map;
    }

    private static SimulationEngine.InputData noBatteryInput(Inverter inverter) {
        List<SimulationInputData> series = SimSeries.constant(2, LOAD, 0d);
        return new SimulationEngine.InputData(inverter, series, null, null, null);
    }

    private static ScenarioSimulationData runRow0(ScenarioInputs scenario, Map<Inverter, SimulationEngine.InputData> map) {
        ArrayList<ScenarioSimulationData> out = new ArrayList<>();
        SimulationEngine.processOneRow(SCENARIO_ID, scenario, out, 0, map);
        return out.get(0);
    }
}
