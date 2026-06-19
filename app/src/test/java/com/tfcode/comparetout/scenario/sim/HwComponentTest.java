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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.tfcode.comparetout.model.scenario.HWSchedule;
import com.tfcode.comparetout.model.scenario.HWSystem;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

/**
 * Phase B isolation tests for {@link HwComponent}: scheduled immersion demand, surplus divert, the
 * cross-interval water-temperature state it now owns, and the fact that every heating in an interval is
 * computed from the SAME previous temperature (the basis of the preserved double-heat quirk). The
 * end-to-end byte-identical behaviour is guarded by the golden master. See {@code plans/sim/component.md}.
 */
public class HwComponentTest {

    // minuteOfDay not divisible by 60 -> skips HWSystem's hourly loss/usage branch (deterministic heating).
    private static IntervalContext ctx() {
        return new IntervalContext(0L, 1, 1, 12 * 60 + 5, 0, 1d / 12d);
    }

    private static List<HWSchedule> allDaySchedule() {
        HWSchedule s = new HWSchedule();
        s.setBegin(0);
        s.setEnd(24);
        return Collections.singletonList(s);
    }

    @Test
    public void nullSystem_producesZeroDemandAndNoDivert() {
        HwComponent hw = new HwComponent(null, null, null);
        DemandResult demand = hw.demand(ctx());
        assertEquals(0d, demand.kWh, 0d);
        assertEquals(0d, demand.outputs.get(OutputChannel.IMMERSION_LOAD), 0d);
        assertFalse(hw.canDivert());
        assertEquals(0d, hw.absorb(1.0, ctx()), 0d);
        assertEquals(0d, hw.commitWaterTemp(), 0d);
    }

    @Test
    public void scheduledImmersion_contributesDemand_routesImmersionLoad_andCannotDivert() {
        HwComponent hw = new HwComponent(new HWSystem(), true, allDaySchedule());
        DemandResult demand = hw.demand(ctx());

        // Default rate 2.5 kW -> 2.5/12 kWh available, fully drawn into cold (15C) water.
        assertEquals(2.5 / 12d, demand.kWh, 1e-9);
        assertEquals(2.5 / 12d, demand.outputs.get(OutputChannel.IMMERSION_LOAD), 1e-9);
        assertFalse("immersion on -> no divert this interval", hw.canDivert());
        assertEquals("divert is suppressed while immersion is scheduled", 0d, hw.absorb(1.0, ctx()), 0d);
    }

    @Test
    public void divertWithoutImmersion_absorbsFromIntervalStartTemperature() {
        // hwDivert on, no schedule -> immersion off, divert on.
        HwComponent hw = new HwComponent(new HWSystem(), true, null);
        hw.demand(ctx()); // resolves the per-interval flags; no scheduled load
        assertTrue(hw.canDivert());

        // absorb heats from the interval's starting temperature (prevTemp), not a running value, so two
        // calls with the same feed are equal. The engine calls absorb at most once per interval (single
        // ordered divert pass), so the legacy double-heat cannot recur; this pins the underlying property.
        double first = hw.absorb(0.5, ctx());
        double second = hw.absorb(0.5, ctx());
        assertEquals(2.5 / 12d, first, 1e-9);
        assertEquals(first, second, 0d);
    }

    @Test
    public void waterTemperatureCarriesAcrossIntervals_andResetClearsIt() {
        HwComponent hw = new HwComponent(new HWSystem(), true, allDaySchedule());

        hw.demand(ctx());
        double t1 = hw.commitWaterTemp();
        assertTrue("water warmed above the 15C intake", t1 > 15d);

        // Next interval starts from t1 (carried), so it warms further.
        hw.demand(ctx());
        double t2 = hw.commitWaterTemp();
        assertTrue("temperature carried into the next interval", t2 > t1);

        // A fresh run resets to a zero previous temperature -> heats from intake again, matching t1.
        hw.resetWaterTemp();
        hw.demand(ctx());
        double afterReset = hw.commitWaterTemp();
        assertEquals(t1, afterReset, 1e-9);
    }
}
