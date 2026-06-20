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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase-2 characterisation of {@link HeatPumpComponent} (the {@link DemandContributor} adapter) against the
 * synthetic 2001 weather fixture. Proves the component faithfully exposes {@link HeatPumpDemandModel} per
 * interval — total demand equals the model's annual electricity, each interval routes its load to
 * {@link OutputChannel#HEAT_PUMP_LOAD}, colder intervals draw more, and an uncovered instant draws zero.
 */
public class HeatPumpComponentTest {

    private static final File FIXTURE =
            new File("src/test/resources/hp-weather/era5-timeseries-2001-synthetic.csv");
    private static final double KELVIN = 273.15d;

    private static List<WeatherSample> series;
    private static long[] millis;
    private static HeatPumpDemandModel model;
    private static HeatPumpComponent component;

    @BeforeClass
    public static void buildFromFixture() throws Exception {
        series = new ArrayList<>();
        List<Long> ms = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(FIXTURE))) {
            r.readLine(); // header
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split(",");
                LocalDateTime ts = LocalDateTime.parse(f[0]);
                double tempC = Double.parseDouble(f[3]) - KELVIN;
                double wind = Math.hypot(Double.parseDouble(f[4]), Double.parseDouble(f[5]));
                int dowIndex = ts.getDayOfWeek().getValue() - 1;
                series.add(new WeatherSample(tempC, wind, ts.getHour(), dowIndex, ts.getDayOfYear()));
                ms.add(ts.toInstant(ZoneOffset.UTC).toEpochMilli());
            }
        }
        millis = new long[ms.size()];
        for (int i = 0; i < millis.length; i++) millis[i] = ms.get(i);
        model = new HeatPumpDemandModel(new Config(), series);
        component = new HeatPumpComponent(model, millis);
    }

    private static IntervalContext at(long m) {
        // The component reads only ctx.millis; the rest is immaterial here.
        return new IntervalContext(m, 1, 1, 0, 0, 1d);
    }

    @Test
    public void demandOverAllIntervalsEqualsModelAnnualElectricity() {
        double total = 0d;
        for (int i = 0; i < millis.length; i++) {
            DemandResult result = component.demand(at(millis[i]));
            total += result.kWh;
            // The kWh and the routed output channel must agree, every interval.
            assertEquals(result.kWh, result.outputs.get(OutputChannel.HEAT_PUMP_LOAD), 0d);
        }
        assertEquals(model.getAnnualElectricity(), total, 1e-6);
    }

    @Test
    public void eachIntervalReturnsItsModelLoad() {
        // Spot-check the mapping millis -> model index across the year.
        for (int i = 0; i < millis.length; i += 1000) {
            DemandResult result = component.demand(at(millis[i]));
            assertEquals(model.loadForIndex(i), result.kWh, 0d);
        }
    }

    @Test
    public void colderIntervalDrawsMoreThanMilderInterval() {
        int coldest = 0, warmest = 0;
        for (int i = 1; i < series.size(); i++) {
            if (series.get(i).tempC < series.get(coldest).tempC) coldest = i;
            if (series.get(i).tempC > series.get(warmest).tempC) warmest = i;
        }
        double coldLoad = component.demand(at(millis[coldest])).kWh;
        double warmLoad = component.demand(at(millis[warmest])).kWh;
        assertTrue("the coldest interval must draw more electricity than the warmest",
                coldLoad > warmLoad);
    }

    @Test
    public void emitsAllSixChannelsConsistentWithModel() {
        for (int i = 0; i < millis.length; i += 1000) {
            DemandResult r = component.demand(at(millis[i]));
            assertEquals(model.loadForIndex(i), r.outputs.get(OutputChannel.HEAT_PUMP_LOAD), 0d);
            assertEquals(model.backupForIndex(i), r.outputs.get(OutputChannel.HEAT_PUMP_BACKUP_LOAD), 0d);
            assertEquals(model.heatDeliveredForIndex(i), r.outputs.get(OutputChannel.HEAT_PUMP_HEAT), 0d);
            assertEquals(model.copForIndex(i), r.outputs.get(OutputChannel.HEAT_PUMP_COP), 0d);
            assertEquals(series.get(i).tempC, r.outputs.get(OutputChannel.HEAT_PUMP_OUTDOOR_TEMP), 0d);
            assertEquals(series.get(i).windSpeed, r.outputs.get(OutputChannel.HEAT_PUMP_WIND_SPEED), 0d);
            // The backup is a subset of the total load.
            assertTrue("backup ≤ total load",
                    r.outputs.get(OutputChannel.HEAT_PUMP_BACKUP_LOAD)
                            <= r.outputs.get(OutputChannel.HEAT_PUMP_LOAD) + 1e-9);
        }
    }

    @Test
    public void effectiveCopEqualsHpCopWhenNoBackup() {
        // The default 7 kW capacity never clamps this synthetic series, so backup is zero throughout and the
        // derivable effective COP (heat ÷ load) collapses to the heat-pump COP.
        for (int i = 0; i < millis.length; i += 1000) {
            DemandResult r = component.demand(at(millis[i]));
            double load = r.outputs.get(OutputChannel.HEAT_PUMP_LOAD);
            double heat = r.outputs.get(OutputChannel.HEAT_PUMP_HEAT);
            assertEquals("no backup at 7 kW", 0d, r.outputs.get(OutputChannel.HEAT_PUMP_BACKUP_LOAD), 0d);
            if (load > 0d) {
                assertEquals(r.outputs.get(OutputChannel.HEAT_PUMP_COP), heat / load, 1e-9);
            }
        }
    }

    @Test
    public void instantNotInTheSeriesDrawsZero() {
        DemandResult result = component.demand(at(millis[millis.length - 1] + 3_600_000L)); // 1 h past the series
        assertEquals(0d, result.kWh, 0d);
        assertEquals(0d, result.outputs.get(OutputChannel.HEAT_PUMP_LOAD), 0d);
    }
}
