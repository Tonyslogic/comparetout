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
import java.util.ArrayList;
import java.util.List;

/**
 * The outlook builder: D+2..D+10, decaying confidence, deterministic
 * perturbation, honest gaps (year end, uncovered months, missing wind).
 */
public class LayerBOutlookTest {

    private static WindPriceCalibration fullYearCalibration() {
        List<WindPriceCalibration.DaySample> days = new ArrayList<>();
        LocalDate d = LocalDate.of(2001, 1, 1);
        int i = 0;
        while (d.getYear() == 2001) {
            double wind = 1 + (i % 20);
            double[] buy = new double[48];
            for (int s = 0; s < 48; s++) buy[s] = 50 - wind;
            days.add(new WindPriceCalibration.DaySample(d, wind, buy));
            d = d.plusDays(1);
            i++;
        }
        return WindPriceCalibration.calibrate(days);
    }

    private static final LayerBOutlook.DailyWind STEADY_WIND = date -> 10.0;

    @Test
    public void coversDPlus2ToDPlus10WithDecayingConfidence() {
        List<DayOutlook> outlook = LayerBOutlook.outlookFor(
                LocalDate.of(2001, 6, 1), fullYearCalibration(), STEADY_WIND, 1.0);
        assertEquals(9, outlook.size()); // D+2 .. D+10
        assertEquals(LocalDate.of(2001, 6, 3), outlook.get(0).date);
        assertEquals(LocalDate.of(2001, 6, 11), outlook.get(8).date);
        for (int i = 1; i < outlook.size(); i++) {
            assertTrue("confidence must decay with horizon",
                    outlook.get(i).confidence < outlook.get(i - 1).confidence);
        }
        assertEquals(Math.pow(0.85, 1), outlook.get(0).confidence, 1e-9);
    }

    @Test
    public void horizonClipsAtTheYearEnd() {
        List<DayOutlook> outlook = LayerBOutlook.outlookFor(
                LocalDate.of(2001, 12, 28), fullYearCalibration(), STEADY_WIND, 1.0);
        assertEquals(2, outlook.size()); // only Dec 30 and Dec 31 remain
        assertEquals(LocalDate.of(2001, 12, 31), outlook.get(1).date);
    }

    @Test
    public void missingWindDaysAreSkippedNotInvented() {
        LayerBOutlook.DailyWind patchy = date -> (date.getDayOfMonth() % 2 == 0) ? null : 10.0;
        List<DayOutlook> outlook = LayerBOutlook.outlookFor(
                LocalDate.of(2001, 6, 1), fullYearCalibration(), patchy, 1.0);
        for (DayOutlook day : outlook) {
            assertTrue(day.date.getDayOfMonth() % 2 == 1);
        }
        assertTrue(outlook.size() < 9);
    }

    @Test
    public void deterministicForTheSameInputs() {
        List<DayOutlook> a = LayerBOutlook.outlookFor(
                LocalDate.of(2001, 3, 15), fullYearCalibration(), STEADY_WIND, 1.0);
        List<DayOutlook> b = LayerBOutlook.outlookFor(
                LocalDate.of(2001, 3, 15), fullYearCalibration(), STEADY_WIND, 1.0);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).confidence, b.get(i).confidence, 0d);
            for (int s = 0; s < 48; s++) {
                assertEquals(a.get(i).expectedBuy[s], b.get(i).expectedBuy[s], 0d);
            }
        }
    }

    @Test
    public void confidenceHaircutScalesEveryDay() {
        List<DayOutlook> full = LayerBOutlook.outlookFor(
                LocalDate.of(2001, 6, 1), fullYearCalibration(), STEADY_WIND, 1.0);
        List<DayOutlook> haircut = LayerBOutlook.outlookFor(
                LocalDate.of(2001, 6, 1), fullYearCalibration(), STEADY_WIND, 0.5);
        assertEquals(full.size(), haircut.size());
        for (int i = 0; i < full.size(); i++) {
            assertEquals(full.get(i).confidence * 0.5, haircut.get(i).confidence, 1e-12);
        }
    }
}
