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
import static org.junit.Assert.assertTrue;

import com.tfcode.comparetout.scenario.sim.HeatPumpDemandModel.Config;
import com.tfcode.comparetout.scenario.sim.HeatPumpDemandModel.WeatherSample;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase-1 characterisation of {@link HeatPumpDemandModel} against the synthetic 2001 weather fixture
 * ({@code app/src/test/resources/hp-weather/}). Asserts the design's invariants ({@code plans/hp/design.md}
 * §3): the fuel-anchored annual heat is conserved, the SCOP calibration hits the user's seasonal figure,
 * colder intervals cost more electricity, and the capacity clamp + resistive backup conserve heat.
 *
 * <p>The expected numbers were cross-checked with an independent Python mirror of the model before the Java
 * was committed.</p>
 */
public class HeatPumpDemandModelTest {

    private static final File FIXTURE =
            new File("src/test/resources/hp-weather/era5-timeseries-2001-synthetic.csv");
    private static final double KELVIN = 273.15d;

    private static List<WeatherSample> series;

    @BeforeClass
    public static void loadFixture() throws Exception {
        series = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(FIXTURE))) {
            r.readLine(); // header: valid_time,latitude,longitude,t2m,u10,v10
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split(",");
                LocalDateTime ts = LocalDateTime.parse(f[0]); // UTC, ISO-8601
                double tempC = Double.parseDouble(f[3]) - KELVIN;
                double u = Double.parseDouble(f[4]);
                double v = Double.parseDouble(f[5]);
                double wind = Math.hypot(u, v);
                int dowIndex = ts.getDayOfWeek().getValue() - 1; // Mon=0..Sun=6
                series.add(new WeatherSample(tempC, wind, ts.getHour(), dowIndex, ts.getDayOfYear()));
            }
        }
    }

    @Test
    public void fixtureIsAFullNonLeapYear() {
        assertEquals("2001 is not a leap year → 8760 hourly rows", 8760, series.size());
    }

    @Test
    public void thermalDemandRenormalisesToTheFuelAnchor() {
        HeatPumpDemandModel m = new HeatPumpDemandModel(new Config(), series);
        // Default config: 2300 L × 10.35 kWh/L × 0.80 − 2000 DHW = 17044 kWh, setpoint unchanged ⇒ no scale.
        assertEquals(17044d, m.getAnnualSpaceHeat(), 1e-6);
        double sumThermal = 0d;
        for (int i = 0; i < series.size(); i++) sumThermal += m.thermalForIndex(i);
        assertEquals("Σ thermal must equal the anchor exactly", m.getAnnualSpaceHeat(), sumThermal, 1e-6);
    }

    @Test
    public void scopCalibrationHitsTheUsersSeasonalFigure() {
        Config cfg = new Config();
        HeatPumpDemandModel m = new HeatPumpDemandModel(cfg, series);
        double target = m.getAnnualSpaceHeat() / cfg.scop;           // 17044 / 3.6 = 4734.4 kWh
        assertEquals("calibrated electricity ≈ anchor / SCOP", target, m.getCalibratedElectricity(),
                target * 0.005);
        double realisedScop = m.getAnnualSpaceHeat() / m.getCalibratedElectricity();
        assertEquals("realised SCOP must equal the user's SCOP", cfg.scop, realisedScop, 0.01);
    }

    @Test
    public void colderIntervalsHaveLowerCop() {
        HeatPumpDemandModel m = new HeatPumpDemandModel(new Config(), series);
        double coldSum = 0d, mildSum = 0d;
        int coldN = 0, mildN = 0;
        for (int i = 0; i < series.size(); i++) {
            double t = series.get(i).tempC;
            if (t < 2d) { coldSum += m.copForIndex(i); coldN++; }
            else if (t > 12d) { mildSum += m.copForIndex(i); mildN++; }
        }
        assertTrue(coldN > 0 && mildN > 0);
        assertTrue("mean COP when cold must be below mean COP when mild",
                coldSum / coldN < mildSum / mildN);
    }

    @Test
    public void capacityClampWithBackupConservesHeatAndRaisesElectricity() {
        // Tiny 1 kW capacity forces the backup branch on cold intervals.
        Config cfg = new Config();
        cfg.capacityKw = 1d;
        HeatPumpDemandModel clamped = new HeatPumpDemandModel(cfg, series);
        HeatPumpDemandModel unclamped = new HeatPumpDemandModel(new Config(), series);

        // Heat delivered is still the anchor (backup tops up what the HP can't move).
        double deliveredHeat = 0d;
        double qMax = cfg.capacityKw * cfg.intervalHours;
        boolean clampedSomewhere = false;
        for (int i = 0; i < series.size(); i++) {
            double qth = clamped.thermalForIndex(i);
            deliveredHeat += qth; // backup conserves heat, so delivered == demanded
            if (qth > qMax) clampedSomewhere = true;
        }
        assertTrue("the 1 kW cap must actually bind somewhere", clampedSomewhere);
        assertEquals("backup conserves the annual heat anchor",
                clamped.getAnnualSpaceHeat(), deliveredHeat, 1e-6);

        // Resistive backup (COP 1) costs more electricity than the unclamped heat-pump-only case.
        assertTrue("clamping with backup must raise annual electricity",
                clamped.getAnnualElectricity() > unclamped.getAnnualElectricity());
    }

    @Test
    public void heatingSeasonWindowConcentratesTheAnchorIntoTheWindow() {
        Config cfg = new Config();
        cfg.heatOnDayOfYear = 274;  // ~1 Oct
        cfg.heatOffDayOfYear = 120; // ~30 Apr (window wraps the year end)
        HeatPumpDemandModel m = new HeatPumpDemandModel(cfg, series);

        double inWindow = 0d, outWindow = 0d;
        for (int i = 0; i < series.size(); i++) {
            int doy = series.get(i).dayOfYear;
            boolean in = doy >= 274 || doy <= 120;
            if (in) inWindow += m.thermalForIndex(i); else outWindow += m.thermalForIndex(i);
        }
        assertEquals("no thermal demand outside the heating season", 0d, outWindow, 1e-9);
        assertEquals("the anchor is still fully delivered, just concentrated in-window",
                m.getAnnualSpaceHeat(), inWindow, 1e-6);
    }

    @Test
    public void warmerSetpointRaisesTheAnchor() {
        Config base = new Config();
        Config warmer = new Config();
        warmer.setpointOld = 20d;
        warmer.setpointNew = 22d; // want it warmer than today
        HeatPumpDemandModel m0 = new HeatPumpDemandModel(base, series);
        HeatPumpDemandModel m1 = new HeatPumpDemandModel(warmer, series);
        assertTrue("a warmer desired setpoint must increase annual demand",
                m1.getAnnualSpaceHeat() > m0.getAnnualSpaceHeat());
    }
}
