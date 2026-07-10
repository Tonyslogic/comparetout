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
 * Rank-N: N cheapest half-hours charge, M dearest export — gated by the
 * break-even bar and the day's SOC energy budget.
 */
public class RankNStrategyTest {

    private static double[] flat(double value) {
        double[] a = new double[48];
        Arrays.fill(a, value);
        return a;
    }

    private static DayContext day(BatterySpec spec, double[] buy, double[] sell) {
        return new DayContext(LocalDate.of(2001, 6, 15), buy, sell, null,
                flat(0.25), spec.floorKwh(), spec);
    }

    @Test
    public void nCheapestContiguousSlotsMergeIntoOneWindow() {
        BatterySpec spec = new BatterySpec(10, 20, 1.5, 1.5, 10, 5, 0);
        double[] buy = flat(20);
        for (int i = 4; i < 10; i++) buy[i] = 5; // 02:00–05:00 cheap
        RankNStrategy strategy = new RankNStrategy(6, 4, 2);
        DayDecisions decisions = strategy.decideDay(day(spec, buy, flat(1)));
        assertEquals(1, decisions.chargeWindows.size());
        assertEquals(120, decisions.chargeWindows.get(0).beginMinute);
        assertEquals(300, decisions.chargeWindows.get(0).endMinute);
        assertTrue(decisions.dischargeWindows.isEmpty()); // 1c sell clears no bar
    }

    @Test
    public void dischargeTakesTheDearestSlotsAboveBreakEven() {
        BatterySpec spec = new BatterySpec(10, 20, 1.5, 1.5, 10, 5, 0);
        double[] buy = flat(20);
        for (int i = 0; i < 6; i++) buy[i] = 9; // avg charge cost 9 → break-even 9/0.9+2 = 12
        double[] sell = flat(5);
        sell[35] = 30;
        sell[36] = 28;
        sell[37] = 11; // below the bar — must not be taken even though M allows it
        RankNStrategy strategy = new RankNStrategy(6, 4, 2);
        DayDecisions decisions = strategy.decideDay(day(spec, buy, sell));
        assertEquals(1, decisions.dischargeWindows.size());
        assertEquals(35 * 30, decisions.dischargeWindows.get(0).beginMinute);
        assertEquals(37 * 30, decisions.dischargeWindows.get(0).endMinute);
    }

    @Test
    public void socBudgetCapsTheDischargeSlots() {
        // 2 kWh battery, 50% floor → only 1 kWh usable; 1 kWh/half-hour
        // discharge → a single export slot no matter how many M allows.
        BatterySpec spec = new BatterySpec(2, 50, 1, 1, 10, 5, 0);
        double[] buy = flat(20);
        buy[0] = 5;
        double[] sell = flat(5);
        for (int i = 30; i < 40; i++) sell[i] = 40;
        RankNStrategy strategy = new RankNStrategy(1, 6, 2);
        DayDecisions decisions = strategy.decideDay(day(spec, buy, sell));
        int slots = 0;
        for (DayDecisions.Window w : decisions.dischargeWindows) {
            slots += (w.endMinute - w.beginMinute) / 30;
        }
        assertEquals(1, slots);
    }

    @Test
    public void negativeSlotsChargeBeyondN() {
        BatterySpec spec = new BatterySpec(10, 20, 1.5, 1.5, 10, 5, 0);
        double[] buy = flat(20);
        buy[2] = -1;
        buy[3] = -2;
        buy[4] = -3;
        buy[5] = -4; // four negative slots, N = 2
        RankNStrategy strategy = new RankNStrategy(2, 0, 2);
        DayDecisions decisions = strategy.decideDay(day(spec, buy, flat(1)));
        assertEquals(1, decisions.chargeWindows.size());
        assertEquals(60, decisions.chargeWindows.get(0).beginMinute);
        assertEquals(180, decisions.chargeWindows.get(0).endMinute); // all 4, not just 2
    }
}
