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
import com.tfcode.comparetout.scenario.sim.SimTime;
import com.tfcode.comparetout.scenario.sim.TimeAxis;

import org.junit.Test;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * b2 of the simulation-engine refactor (see {@code plans/sim/refactor.md}): the engine derives its
 * time-of-day / schedule logic from each interval's UTC millis, not from the row's wall-clock strings.
 * This pins that contract — and that {@link SimulationEngine#simulate} drives the run from a {@link TimeAxis}.
 */
public class SimulationEngineMillisDrivenTest {

    private static final long ID = 1L;
    private static final double TOL = 1e-9;

    /**
     * A row whose date/mod strings say 10:00 (outside the 02:00–06:00 EV window) but whose stored millis say
     * 03:00 (inside it). The engine must schedule the charge from the millis, while still echoing the string
     * fields onto the output row unchanged.
     */
    @Test
    public void scheduleUsesStoredMillisNotDateModStrings() {
        SimulationInputData r = new SimulationInputData();
        r.setDate("2001-06-15");
        r.setMinute("10:00");
        r.setMod(600);     // 10:00 by the string fields — outside the EV charge window
        r.setDow(5);       // Friday
        r.setDo2001(166);
        r.setLoad(0.15);
        r.setTpv(0.0);
        // ...but the canonical instant is 03:00 UTC — inside the window.
        r.setMillisSinceEpoch(SimTime.fromDateAndMinuteOfDay("2001-06-15", 180, ZoneOffset.UTC));

        Inverter inverter = InverterBuilder.anInverter().index(1).lossless().build();
        Map<Inverter, SimulationEngine.InputData> map = new LinkedHashMap<>();
        map.put(inverter, new SimulationEngine.InputData(inverter,
                Collections.singletonList(r), null, null, null));

        EVCharge evCharge = new EVCharge();
        evCharge.setBegin(2);
        evCharge.setEnd(6);
        evCharge.setDraw(7.5);
        ScenarioInputs scenario = new ScenarioInputs(null, false, null,
                Collections.singletonList(evCharge), null, 6.0);

        ArrayList<ScenarioSimulationData> out = new ArrayList<>();
        SimulationEngine.processOneRow(ID, scenario, out, 0, map);
        ScenarioSimulationData row = out.get(0);

        // Scheduled by the 03:00 millis, not the 10:00 strings.
        assertEquals(7.5 / 12d, row.getDirectEVcharge(), TOL);
        // Output wall-clock fields still echo the input strings (behaviour-preserving for the 2001 grid).
        assertEquals(600, row.getMinuteOfDay());
        assertEquals("2001-06-15", row.getDate());
    }

    /** {@link SimulationEngine#simulate} produces one output row per interval of the axis. */
    @Test
    public void simulateProducesOneRowPerInterval() {
        List<SimulationInputData> series = SimSeries.constant(3, 0.15, 0.0);
        Inverter inverter = InverterBuilder.anInverter().index(1).lossless().build();
        Map<Inverter, SimulationEngine.InputData> map = new LinkedHashMap<>();
        map.put(inverter, new SimulationEngine.InputData(inverter, series, null, null, null));

        long start = SimulationWorker.millisOf(series.get(0));
        TimeAxis axis = TimeAxis.fiveMinute(start, start + 3 * TimeAxis.FIVE_MINUTES_MILLIS);

        ScenarioInputs scenario = new ScenarioInputs(null, false, null, null, null, 6.0);
        List<ScenarioSimulationData> out = SimulationEngine.simulate(ID, scenario, axis, map);

        assertEquals(3, out.size());
    }
}
