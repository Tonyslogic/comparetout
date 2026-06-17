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

import com.tfcode.comparetout.model.scenario.SimulationInputData;
import com.tfcode.comparetout.scenario.sim.SimTime;

import org.junit.Test;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * b2.1 of the simulation-engine refactor (see {@code plans/sim/refactor.md}): the millis-keyed PV/load merge
 * that replaces the old positional merge with its 2001 DST magic (Bug 4). These exercise the pure static
 * helpers on {@link SimulationWorker} without needing an Android {@code Worker} instance.
 */
public class SimulationWorkerMergeTest {

    private static final double TOL = 1e-9;
    private static final long FIVE_MIN = 300_000L;

    private static SimulationInputData row(String date, int mod, Long millis) {
        SimulationInputData r = new SimulationInputData();
        r.setDate(date);
        r.setMod(mod);
        r.setMillisSinceEpoch(millis);
        return r;
    }

    @Test
    public void millisOf_usesStoredValueWhenPresent() {
        assertEquals(12_345L, SimulationWorker.millisOf(row("2001-06-15", 0, 12_345L)));
    }

    @Test
    public void millisOf_derivesUtcFromDateAndModWhenNull() {
        long expected = SimTime.fromDateAndMinuteOfDay("2001-06-15", 5, ZoneOffset.UTC);
        assertEquals(expected, SimulationWorker.millisOf(row("2001-06-15", 5, null)));
    }

    @Test
    public void mergePVByMillis_alignsByMillisNotPosition() {
        long t0 = SimTime.fromDateAndMinuteOfDay("2001-06-15", 0, ZoneOffset.UTC);
        List<SimulationInputData> load = new ArrayList<>();
        load.add(row("2001-06-15", 0, t0));
        load.add(row("2001-06-15", 5, t0 + FIVE_MIN));
        load.add(row("2001-06-15", 10, t0 + 2 * FIVE_MIN));

        // PV supplied out of order and sparse (no value for the middle slot).
        Map<Long, Double> pv = new HashMap<>();
        pv.put(t0 + 2 * FIVE_MIN, 2.0);
        pv.put(t0, 1.0);

        SimulationWorker.mergePVByMillis(load, pv);

        assertEquals(1.0, load.get(0).getTpv(), TOL);
        assertEquals(0.0, load.get(1).getTpv(), TOL); // no PV for this instant -> zero
        assertEquals(2.0, load.get(2).getTpv(), TOL);
    }

    @Test
    public void mergePVByMillis_noDstShiftOrZeroing() {
        // The old merge zeroed/shifted PV around the 2001 spring-forward window. By-millis it passes straight
        // through: equal UTC instants line up, no DST fudge.
        long t = SimTime.fromDateAndMinuteOfDay("2001-03-25", 60, ZoneOffset.UTC);
        List<SimulationInputData> load = new ArrayList<>();
        load.add(row("2001-03-25", 60, t));
        Map<Long, Double> pv = new HashMap<>();
        pv.put(t, 3.3);

        SimulationWorker.mergePVByMillis(load, pv);

        assertEquals(3.3, load.get(0).getTpv(), TOL);
    }

    @Test
    public void mergePVByMillis_derivesNullLoadMillis() {
        // Legacy load rows (null millis) derive UTC from date+mod and still match PV keyed by the same instant.
        List<SimulationInputData> load = new ArrayList<>();
        load.add(row("2001-06-15", 0, null));
        Map<Long, Double> pv = new HashMap<>();
        pv.put(SimTime.fromDateAndMinuteOfDay("2001-06-15", 0, ZoneOffset.UTC), 4.2);

        SimulationWorker.mergePVByMillis(load, pv);

        assertEquals(4.2, load.get(0).getTpv(), TOL);
    }
}
