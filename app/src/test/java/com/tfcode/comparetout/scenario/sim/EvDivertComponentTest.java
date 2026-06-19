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

package com.tfcode.comparetout.scenario.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import com.tfcode.comparetout.model.scenario.EVDivert;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Phase B isolation tests for {@link EvDivertComponent}: the per-day cap, the minimum-feed gate, and
 * the shared daily accumulator carrying across intervals. The ordering relative to hot water (the
 * {@code isEv1st} flow) and the double-heat quirk are engine orchestration, guarded byte-identically by
 * the golden master. See {@code plans/sim/component.md}.
 */
public class EvDivertComponentTest {

    private static final int DAY = 100; // arbitrary UTC epoch-day

    private static IntervalContext ctx() {
        return ctxOnDay(DAY);
    }

    private static IntervalContext ctxOnDay(int evDivertDay) {
        return new IntervalContext(0L, 1, 1, 12 * 60, evDivertDay, 1d / 12d);
    }

    /** An all-day, active divert with the given minimum (kW) and daily cap (kWh). */
    private static EVDivert divert(double minimumKW, double dailyMaxKWh, boolean active) {
        EVDivert ev = new EVDivert();
        ev.setBegin(0);
        ev.setEnd(24);
        ev.setActive(active);
        ev.setMinimum(minimumKW);
        ev.setDailyMax(dailyMaxKWh);
        return ev;
    }

    @Test
    public void absorbsUpToFeed_whenUnderCapAndOverMinimum() {
        Map<Integer, Double> totals = new HashMap<>();
        EvDivertComponent ev = new EvDivertComponent(
                Collections.singletonList(divert(0d, 100d, true)), totals);

        // minimum is 0 -> any positive feed passes the feed > minimum/12 gate; cap is generous.
        assertEquals(0.5, ev.absorb(0.5, ctx()), 0d);
        assertEquals("daily accumulator updated", 0.5, totals.get(DAY), 0d);
    }

    @Test
    public void cappedByRemainingDailyMax_acrossIntervals() {
        Map<Integer, Double> totals = new HashMap<>();
        EvDivertComponent ev = new EvDivertComponent(
                Collections.singletonList(divert(0d, 0.7d, true)), totals);

        assertEquals(0.5, ev.absorb(0.5, ctx()), 0d);          // 0.5 of 0.7 used
        assertEquals(0.2, ev.absorb(0.5, ctx()), 1e-9);        // only 0.2 left under the cap
        assertEquals(0.0, ev.absorb(0.5, ctx()), 0d);          // cap exhausted -> nothing
        assertEquals(0.7, totals.get(DAY), 1e-9);
    }

    @Test
    public void feedAtOrBelowMinimumIsNotDiverted() {
        Map<Integer, Double> totals = new HashMap<>();
        // minimum 6 kW -> 6/12 = 0.5 kWh threshold; strictly greater is required.
        EvDivertComponent ev = new EvDivertComponent(
                Collections.singletonList(divert(6d, 100d, true)), totals);

        assertEquals("feed == minimum/12 does not divert", 0d, ev.absorb(0.5, ctx()), 0d);
        assertEquals(0.6 - 0.0, ev.absorb(0.6, ctx()), 1e-9); // just over threshold -> all of it
    }

    @Test
    public void inactiveDivertIsIgnored() {
        Map<Integer, Double> totals = new HashMap<>();
        EvDivertComponent ev = new EvDivertComponent(
                Collections.singletonList(divert(0d, 100d, false)), totals);

        assertNull("inactive -> activeDivertOrNull returns null", ev.activeDivertOrNull(ctx()));
        assertEquals(0d, ev.absorb(0.5, ctx()), 0d);
    }

    @Test
    public void dailyCapResetsOnANewDay() {
        Map<Integer, Double> totals = new HashMap<>();
        EvDivertComponent ev = new EvDivertComponent(
                Collections.singletonList(divert(0d, 0.5d, true)), totals);

        assertEquals("day 10 uses its cap", 0.5, ev.absorb(0.5, ctxOnDay(10)), 0d);
        assertEquals("day 10 now exhausted", 0.0, ev.absorb(0.5, ctxOnDay(10)), 0d);
        assertEquals("day 11 gets a fresh cap", 0.5, ev.absorb(0.5, ctxOnDay(11)), 0d);
        assertEquals(0.5, totals.get(10), 0d);
        assertEquals(0.5, totals.get(11), 0d);
    }

    @Test
    public void activeDivertOrNull_reflectsScheduleAndActiveFlag() {
        EvDivertComponent active = new EvDivertComponent(
                Collections.singletonList(divert(0d, 100d, true)), new HashMap<>());
        assertNotNull(active.activeDivertOrNull(ctx()));

        EvDivertComponent none = new EvDivertComponent(Collections.emptyList(), new HashMap<>());
        assertNull(none.activeDivertOrNull(ctx()));
    }
}
