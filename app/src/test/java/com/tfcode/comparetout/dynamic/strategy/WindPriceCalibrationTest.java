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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The quantile mapping: a calibration year where high wind means cheap
 * power must predict high prices for calm days and low prices for windy
 * ones — while preserving the within-day band structure.
 */
public class WindPriceCalibrationTest {

    /**
     * A synthetic June: 30 days, wind 1..30 m/s, day price = 60 − wind
     * (strict negative correlation) with a +10c evening band on top.
     */
    private static List<WindPriceCalibration.DaySample> syntheticJune() {
        List<WindPriceCalibration.DaySample> days = new ArrayList<>();
        for (int d = 1; d <= 30; d++) {
            double wind = d;
            double[] buy = new double[48];
            for (int s = 0; s < 48; s++) {
                buy[s] = (60 - wind) + ((s >= 34 && s < 40) ? 10 : 0);
            }
            days.add(new WindPriceCalibration.DaySample(LocalDate.of(2001, 6, d), wind, buy));
        }
        return days;
    }

    @Test
    public void calmDaysPredictDearerPowerThanWindyDays() {
        WindPriceCalibration cal = WindPriceCalibration.calibrate(syntheticJune());
        // Slot 6 (03:00) sits in a band untouched by the evening bump.
        double calm = cal.expectedPrices(6, 0.0)[6];
        double windy = cal.expectedPrices(6, 1.0)[6];
        assertTrue("calm (" + calm + ") must beat windy (" + windy + ")", calm > windy);
        // The extremes of a 1..30 m/s year map to 60−wind of the calmest /
        // windiest neighbourhoods (5-day averages: winds 1..5 and 26..30).
        assertEquals(57, calm, 1e-9);
        assertEquals(32, windy, 1e-9);
    }

    @Test
    public void bandStructureSurvivesTheMapping() {
        WindPriceCalibration cal = WindPriceCalibration.calibrate(syntheticJune());
        double[] expected = cal.expectedPrices(6, 0.5);
        // The evening band (slots 36..47 include the +10c 17:00–20:00 bump)
        // must sit above the morning band.
        double morning = expected[6];   // 03:00 — band 0
        double evening = expected[38];  // 19:00 — band 3
        assertTrue(evening > morning);
    }

    @Test
    public void windPercentileRanksWithinTheMonth() {
        WindPriceCalibration cal = WindPriceCalibration.calibrate(syntheticJune());
        assertEquals(0.0, cal.windPercentile(6, 0.5), 1e-9);   // calmer than every day
        assertEquals(1.0, cal.windPercentile(6, 99), 1e-9);    // windier than every day
        double mid = cal.windPercentile(6, 15.5);              // 15 days below
        assertEquals(15d / 29d, mid, 1e-9);
    }

    @Test
    public void uncoveredMonthsReportNoCoverage() {
        WindPriceCalibration cal = WindPriceCalibration.calibrate(syntheticJune());
        assertTrue(cal.covers(6));
        assertFalse(cal.covers(7));
        // And predicting an uncovered month degrades to zeros, not a throw.
        assertEquals(0d, cal.expectedPrices(7, 0.5)[0], 0d);
    }
}
