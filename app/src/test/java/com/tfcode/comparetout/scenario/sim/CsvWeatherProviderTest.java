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
 * Phase-3 alignment proof for {@link CsvWeatherProvider}: the hourly ERA5 fixture interpolates onto the sim
 * grid exactly — exact hours return the CSV value, midpoints are the linear mean, out-of-range instants
 * clamp to the nearest endpoint (never zero-fill) — and the {@link HeatPumpComponent#build} factory aligns
 * the weather onto a grid consistently with how the engine derives interval calendar fields.
 */
public class CsvWeatherProviderTest {

    private static final File FIXTURE =
            new File("src/test/resources/hp-weather/era5-timeseries-2001-synthetic.csv");
    private static final double KELVIN = 273.15d;
    private static final long HOUR = 3_600_000L;

    private static CsvWeatherProvider provider;
    private static long[] hourMillis;        // every hourly instant in the fixture
    private static List<WeatherSample> raw;  // the fixture as model samples (for the factory cross-check)
    private static double t0, t1, w0, w1;    // first two rows' °C / m/s

    @BeforeClass
    public static void load() throws Exception {
        provider = new CsvWeatherProvider(new FileReader(FIXTURE));

        raw = new ArrayList<>();
        List<Long> ms = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(FIXTURE))) {
            r.readLine();
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split(",");
                LocalDateTime ts = LocalDateTime.parse(f[0]);
                double c = Double.parseDouble(f[3]) - KELVIN;
                double wind = Math.hypot(Double.parseDouble(f[4]), Double.parseDouble(f[5]));
                raw.add(new WeatherSample(c, wind, ts.getHour(), ts.getDayOfWeek().getValue() - 1,
                        ts.getDayOfYear()));
                ms.add(ts.toInstant(ZoneOffset.UTC).toEpochMilli());
            }
        }
        hourMillis = new long[ms.size()];
        for (int i = 0; i < hourMillis.length; i++) hourMillis[i] = ms.get(i);
        t0 = raw.get(0).tempC; t1 = raw.get(1).tempC;
        w0 = raw.get(0).windSpeed; w1 = raw.get(1).windSpeed;
    }

    @Test
    public void parsesEveryHourOfTheYear() {
        assertEquals(8760, provider.size());
    }

    @Test
    public void exactHourReturnsTheCsvValue() {
        assertEquals(t0, provider.temperatureAt(hourMillis[0]), 1e-9);
        assertEquals(w0, provider.windSpeedAt(hourMillis[0]), 1e-9);
        assertEquals(t1, provider.temperatureAt(hourMillis[1]), 1e-9);
    }

    @Test
    public void midpointIsTheLinearMeanOfBracketingHours() {
        long mid = hourMillis[0] + HOUR / 2;
        assertEquals((t0 + t1) / 2d, provider.temperatureAt(mid), 1e-9);
        assertEquals((w0 + w1) / 2d, provider.windSpeedAt(mid), 1e-9);
    }

    @Test
    public void beforeRangeClampsToFirstNotZero() {
        double t = provider.temperatureAt(hourMillis[0] - HOUR);
        assertEquals(t0, t, 0d);
        assertTrue("clamp must not zero-fill", t != 0d);
    }

    @Test
    public void afterRangeClampsToLastNotZero() {
        long last = hourMillis[hourMillis.length - 1];
        assertEquals(provider.temperatureAt(last), provider.temperatureAt(last + HOUR), 0d);
        assertEquals(provider.windSpeedAt(last), provider.windSpeedAt(last + HOUR), 0d);
    }

    @Test
    public void fiveMinuteGridNeverZeroFillsAndStaysWithinBracketingHours() {
        // Walk a full day on the 5-minute grid; every interpolated value must lie between the two
        // bracketing hourly samples (so it is a real interpolation, never a zero-filled gap).
        long base = hourMillis[0];
        for (int k = 0; k < 24 * 12; k++) {
            long m = base + k * 300_000L;
            long h0 = base + (k / 12) * HOUR;
            double a = provider.temperatureAt(h0);
            double b = provider.temperatureAt(h0 + HOUR);
            double t = provider.temperatureAt(m);
            assertTrue("interpolated temp within bracketing hours",
                    t >= Math.min(a, b) - 1e-9 && t <= Math.max(a, b) + 1e-9);
        }
    }

    @Test
    public void factoryAlignsAtHoursIdenticallyToADirectModel() {
        // On the fixture's own hourly grid the interpolation is the identity, and the factory derives the
        // calendar fields (UTC) the same way the reference samples were built — so the factory-built
        // component's per-interval load must equal a directly-built model's.
        HeatPumpComponent component = HeatPumpComponent.build(new Config(), provider, hourMillis);
        HeatPumpDemandModel reference = new HeatPumpDemandModel(new Config(), raw);
        for (int i = 0; i < hourMillis.length; i += 500) {
            IntervalContext ctx = new IntervalContext(hourMillis[i], 1, 1, 0, 0, 1d);
            assertEquals(reference.loadForIndex(i), component.demand(ctx).kWh, 1e-9);
        }
    }
}
