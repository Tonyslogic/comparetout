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
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;

/**
 * Threshold rule: charge under X, export over Y — with the spread guard
 * keeping a mis-set Y from cycling the battery at a loss. Includes the
 * plan's Phase-5 checkpoints: a flat-price day produces no windows; a
 * two-level day charges only in the cheap band.
 */
public class ThresholdStrategyTest {

    /** 10 kWh, 20% floor, 1.5 kWh/half-hour each way, 10% loss, 5 kW export cap. */
    private static BatterySpec spec() {
        return new BatterySpec(10, 20, 1.5, 1.5, 10, 5, 0);
    }

    private static double[] flat(double value) {
        double[] a = new double[48];
        Arrays.fill(a, value);
        return a;
    }

    private static DayContext day(double[] buy, double[] sell) {
        return new DayContext(LocalDate.of(2001, 6, 15), buy, sell, null,
                flat(0.25), spec().floorKwh(), spec());
    }

    @Test
    public void flatPricesProduceNoWindows() {
        ThresholdStrategy strategy = new ThresholdStrategy(15, 25, 2);
        DayDecisions decisions = strategy.decideDay(day(flat(20), flat(10)));
        assertTrue(decisions.chargeWindows.isEmpty());
        assertTrue(decisions.dischargeWindows.isEmpty());
    }

    @Test
    public void twoLevelDayChargesOnlyInTheCheapBand() {
        double[] buy = flat(25);
        for (int i = 0; i < 8; i++) buy[i] = 8; // 00:00–04:00 cheap
        ThresholdStrategy strategy = new ThresholdStrategy(10, 40, 2);
        DayDecisions decisions = strategy.decideDay(day(buy, flat(10)));
        assertEquals(1, decisions.chargeWindows.size());
        DayDecisions.Window w = decisions.chargeWindows.get(0);
        assertEquals(0, w.beginMinute);
        assertEquals(240, w.endMinute);
        assertEquals(100d, w.stopAtPercent, 0d);
        assertTrue(decisions.dischargeWindows.isEmpty());
    }

    @Test
    public void dischargeNeedsThresholdAndSpreadOverTheCheapestCharge() {
        double[] buy = flat(25);
        for (int i = 0; i < 4; i++) buy[i] = 8;
        double[] sell = flat(10);
        for (int i = 34; i < 38; i++) sell[i] = 30; // 17:00–19:00 spike
        ThresholdStrategy strategy = new ThresholdStrategy(10, 25, 2);
        DayDecisions decisions = strategy.decideDay(day(buy, sell));
        // break-even = 8 / 0.9 + 2 ≈ 10.9 — the 30c spike clears it easily.
        assertEquals(1, decisions.dischargeWindows.size());
        DayDecisions.Window w = decisions.dischargeWindows.get(0);
        assertEquals(17 * 60, w.beginMinute);
        assertEquals(19 * 60, w.endMinute);
        assertEquals(20d, w.stopAtPercent, 0d);       // discharge down to the floor
        assertEquals(3d, w.rateKw, 1e-9);             // 1.5 kWh/half-hour = 3 kW < 5 kW cap
    }

    @Test
    public void spreadGuardBlocksAThinMargin() {
        // Cheapest charge is 24c: break-even = 24 / 0.9 + 2 ≈ 28.7, so a
        // 26c export slot passes the naive Y=25 threshold but not the guard.
        double[] sell = flat(10);
        sell[36] = 26;
        ThresholdStrategy strategy = new ThresholdStrategy(10, 25, 2);
        DayDecisions decisions = strategy.decideDay(day(flat(24), sell));
        assertTrue(decisions.dischargeWindows.isEmpty());
    }

    @Test
    public void negativePriceSlotChargesEvenWhenSellSpikes() {
        double[] buy = flat(20);
        buy[10] = -5;
        double[] sell = flat(10);
        sell[10] = 50; // both sides qualify — charging must win
        ThresholdStrategy strategy = new ThresholdStrategy(10, 25, 2);
        DayDecisions decisions = strategy.decideDay(day(buy, sell));
        assertEquals(1, decisions.chargeWindows.size());
        assertEquals(300, decisions.chargeWindows.get(0).beginMinute);
        assertTrue(decisions.dischargeWindows.isEmpty());
    }

    @Test
    public void socEstimateReflectsAFullChargeDay() {
        ThresholdStrategy strategy = new ThresholdStrategy(30, 90, 2);
        DayDecisions decisions = strategy.decideDay(day(flat(20), flat(10)));
        // Every slot charges (20 < 30): the battery must end the day full.
        assertEquals(spec().capacityKwh, decisions.socEndKwh, 1e-9);
    }
}
