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

package com.tfcode.comparetout.dynamic.strategy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The rolling day-ahead loop: 365 days of 2001, SOC carried between days,
 * tomorrow's prices visible except on the last day — and the end-to-end
 * Phase-5 checkpoints through runner + strategy + emitter.
 */
public class StrategyYearRunnerTest {

    private static BatterySpec spec() {
        return new BatterySpec(10, 20, 1.5, 1.5, 10, 5, 0);
    }

    private static StrategyYearRunner.HalfHourlyProvider flat(double value) {
        return date -> {
            double[] a = new double[48];
            Arrays.fill(a, value);
            return a;
        };
    }

    @Test
    public void runsEveryDayOfTheSimYearInOrder() {
        Map<LocalDate, DayDecisions> decisions = StrategyYearRunner.run(
                new ThresholdStrategy(15, 25, 2), spec(),
                flat(20), flat(10), flat(0.25));
        assertEquals(365, decisions.size());
        List<LocalDate> dates = new ArrayList<>(decisions.keySet());
        assertEquals(LocalDate.of(2001, 1, 1), dates.get(0));
        assertEquals(LocalDate.of(2001, 12, 31), dates.get(364));
    }

    @Test
    public void socCarriesBetweenDaysAndNextDayPricesEndAtTheYearBoundary() {
        List<Double> socStarts = new ArrayList<>();
        List<double[]> nextDays = new ArrayList<>();
        DispatchStrategy recorder = new DispatchStrategy() {
            @Override
            public String name() {
                return "Recorder";
            }

            @Override
            public DayDecisions decideDay(DayContext ctx) {
                socStarts.add(ctx.socStartKwh);
                nextDays.add(ctx.nextDayBuy);
                // Claim one half-hour of charge happened.
                return new DayDecisions(new ArrayList<>(), new ArrayList<>(),
                        ctx.socStartKwh + ctx.battery.chargeKwhPerHalfHour);
            }
        };
        StrategyYearRunner.run(recorder, spec(), flat(20), flat(10), flat(0));

        assertEquals(spec().floorKwh(), socStarts.get(0), 1e-9);
        assertEquals(spec().floorKwh() + spec().chargeKwhPerHalfHour, socStarts.get(1), 1e-9);
        // The claimed SOC is clamped to capacity as it carries.
        assertEquals(spec().capacityKwh, socStarts.get(364), 1e-9);
        assertNotNull(nextDays.get(0));
        assertNotNull(nextDays.get(363));
        assertNull(nextDays.get(364)); // Dec 31 has no tomorrow in the sim year
    }

    @Test
    public void flatPriceYearEmitsNoRows() {
        Map<LocalDate, DayDecisions> decisions = StrategyYearRunner.run(
                new ThresholdStrategy(15, 25, 2), spec(),
                flat(20), flat(10), flat(0.25));
        ScheduleEmitter.Emitted emitted = ScheduleEmitter.emit(
                decisions, "Threshold", "AlphaESS",
                java.util.Collections.singletonMap("AlphaESS", 2.7));
        assertTrue(emitted.loadShifts.isEmpty());
        assertTrue(emitted.discharges.isEmpty());
    }

    @Test
    public void aRepeatingDayCoalescesToASingleYearRow() {
        StrategyYearRunner.HalfHourlyProvider buy = date -> {
            double[] a = new double[48];
            Arrays.fill(a, 25);
            for (int i = 0; i < 8; i++) a[i] = 8; // same cheap band every night
            return a;
        };
        Map<LocalDate, DayDecisions> decisions = StrategyYearRunner.run(
                new ThresholdStrategy(10, 40, 2), spec(),
                buy, flat(10), flat(0.25));
        ScheduleEmitter.Emitted emitted = ScheduleEmitter.emit(
                decisions, "Threshold", "AlphaESS",
                java.util.Collections.singletonMap("AlphaESS", 2.7));
        assertEquals(1, emitted.loadShifts.size());
        assertEquals("01/01", emitted.loadShifts.get(0).getStartDate());
        assertEquals("12/31", emitted.loadShifts.get(0).getEndDate());
        assertEquals(0, emitted.loadShifts.get(0).getEffectiveBeginMinute());
        assertEquals(240, emitted.loadShifts.get(0).getEffectiveEndMinute());
    }
}
