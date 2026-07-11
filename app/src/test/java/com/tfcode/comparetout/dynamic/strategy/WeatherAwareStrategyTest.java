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
import java.util.Collections;
import java.util.List;

/**
 * Pre-positioning: the decorator holds today's export when the outlook's
 * confidence-weighted expected spike beats today's best sell price, and is a
 * strict pass-through otherwise.
 */
public class WeatherAwareStrategyTest {

    private static BatterySpec spec() {
        return new BatterySpec(10, 20, 1.5, 1.5, 10, 5, 0);
    }

    private static double[] flat(double value) {
        double[] a = new double[48];
        Arrays.fill(a, value);
        return a;
    }

    /**
     * A day the base Threshold strategy both charges (cheap night) and
     * exports (30c evening). Load is light (0.05 kWh/half-hour) so the SOC
     * comparison between exporting and holding is not masked by the battery
     * hitting its floor either way.
     */
    private static DayContext day(List<DayOutlook> outlook) {
        double[] buy = flat(25);
        for (int i = 0; i < 4; i++) buy[i] = 8;
        double[] sell = flat(10);
        for (int i = 34; i < 38; i++) sell[i] = 30;
        return new DayContext(LocalDate.of(2001, 6, 15), buy, sell, null,
                flat(0.05), spec().floorKwh(), spec(), outlook);
    }

    private static DayOutlook spike(double peakBuy, double confidence) {
        return new DayOutlook(LocalDate.of(2001, 6, 18), flat(peakBuy), confidence);
    }

    @Test
    public void emptyOutlookPassesThroughUnchanged() {
        ThresholdStrategy base = new ThresholdStrategy(10, 25, 2);
        WeatherAwareStrategy aware = new WeatherAwareStrategy(base);
        DayDecisions plain = base.decideDay(day(Collections.emptyList()));
        DayDecisions decorated = aware.decideDay(day(Collections.emptyList()));
        assertEquals(plain.chargeWindows, decorated.chargeWindows);
        assertEquals(plain.dischargeWindows, decorated.dischargeWindows);
        assertEquals(plain.socEndKwh, decorated.socEndKwh, 0d);
    }

    @Test
    public void confidentSpikeHoldsTheExportAndKeepsTheCharge() {
        WeatherAwareStrategy aware = new WeatherAwareStrategy(new ThresholdStrategy(10, 25, 2));
        // 60c expected at 0.9 confidence = 54 effective > today's best sell 30.
        DayDecisions decisions = aware.decideDay(day(
                Collections.singletonList(spike(60, 0.9))));
        assertTrue(decisions.dischargeWindows.isEmpty());
        assertEquals(1, decisions.chargeWindows.size());
    }

    @Test
    public void holdingRaisesTheEndOfDaySoc() {
        ThresholdStrategy base = new ThresholdStrategy(10, 25, 2);
        WeatherAwareStrategy aware = new WeatherAwareStrategy(base);
        double exporting = base.decideDay(day(Collections.emptyList())).socEndKwh;
        double holding = aware.decideDay(day(
                Collections.singletonList(spike(60, 0.9)))).socEndKwh;
        assertTrue("held SOC (" + holding + ") must exceed exported SOC (" + exporting + ")",
                holding > exporting);
    }

    @Test
    public void lowConfidenceSpikeIsIgnored() {
        WeatherAwareStrategy aware = new WeatherAwareStrategy(new ThresholdStrategy(10, 25, 2));
        // 60c at 0.3 confidence = 18 effective < today's 30c sell — export as planned.
        DayDecisions decisions = aware.decideDay(day(
                Collections.singletonList(spike(60, 0.3))));
        assertEquals(1, decisions.dischargeWindows.size());
    }

    @Test
    public void nameCarriesTheWeatherMark() {
        assertEquals("Threshold ☁",
                new WeatherAwareStrategy(new ThresholdStrategy(10, 25, 2)).name());
    }
}
