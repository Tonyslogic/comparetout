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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Locale;

/**
 * A {@link WeatherProvider} that parses the raw ERA5 single-levels time-series CSV and interpolates it onto
 * the sim grid. Phase 3 of {@code plans/hp/plan.md}. Columns are resolved <b>by name</b> from the header —
 * {@code valid_time} (UTC), {@code t2m} (Kelvin), {@code u10}/{@code v10} (m/s) — because the live CDS output
 * does not match the documented column order (observed order {@code valid_time,u10,v10,t2m,latitude,longitude})
 * and the synthetic fixture uses yet another. The timestamp accepts both the CDS {@code "yyyy-MM-dd HH:mm:ss"}
 * (space) and ISO-8601 {@code 'T'} forms.
 *
 * <p>This is the <b>one parser for both byte sources</b> the design promised: the offline fixture / shipped
 * sample asset and the live CDS download (Phase 6) emit the identical CSV, so the conversions here
 * (K→°C, {@code √(u²+v²)}, {@code valid_time}→millis) are exercised by the fixture and reused unchanged for
 * CDS. It takes a {@link Reader} so the Android layer can open an asset/download stream without this class
 * depending on Android.</p>
 *
 * <p>The series is hourly and sorted ascending; lookups binary-search the bracketing hours and linearly
 * interpolate, clamping to the nearest endpoint outside the range (never zero-filling).</p>
 */
public final class CsvWeatherProvider implements WeatherProvider {

    private static final double KELVIN = 273.15d;

    private final long[] hourMillis; // ascending, one per hourly sample
    private final double[] tempC;    // °C, parallel to hourMillis
    private final double[] windMs;   // m/s, parallel to hourMillis

    public CsvWeatherProvider(Reader csv) throws IOException {
        int count = 0;
        long[] ms = new long[8784]; // a full (leap) year of hours; grown if needed
        double[] t = new double[8784];
        double[] w = new double[8784];
        try (BufferedReader r = new BufferedReader(csv)) {
            String header = r.readLine();
            if (header == null) throw new IOException("empty weather CSV");
            // Resolve columns by NAME, not position. The live CDS time-series CSV does NOT match the
            // documented column order (header observed 2026-06-21:
            // "valid_time,u10,v10,t2m,latitude,longitude") and could add columns; the synthetic fixture uses
            // a different order again. Name-mapping makes the one parser tolerant of every layout.
            String[] cols = header.split(",");
            int tIdx = -1, tempIdx = -1, uIdx = -1, vIdx = -1;
            for (int i = 0; i < cols.length; i++) {
                switch (cols[i].trim().toLowerCase(Locale.ROOT)) {
                    case "valid_time": tIdx = i; break;
                    case "t2m": tempIdx = i; break;
                    case "u10": uIdx = i; break;
                    case "v10": vIdx = i; break;
                    default: break;
                }
            }
            if (tIdx < 0 || tempIdx < 0 || uIdx < 0 || vIdx < 0) {
                throw new IOException("weather CSV missing a required column (valid_time/t2m/u10/v10): "
                        + header);
            }
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) continue;
                String[] f = line.split(",");
                // CDS stamps "yyyy-MM-dd HH:mm:ss" (space separator); the fixture uses ISO-8601 'T'. Accept
                // both by normalising the space to 'T' before LocalDateTime.parse.
                long millis = LocalDateTime.parse(f[tIdx].trim().replace(' ', 'T'))
                        .toInstant(ZoneOffset.UTC).toEpochMilli();
                double celsius = Double.parseDouble(f[tempIdx]) - KELVIN;
                double u = Double.parseDouble(f[uIdx]);
                double v = Double.parseDouble(f[vIdx]);
                if (count == ms.length) {
                    ms = Arrays.copyOf(ms, ms.length * 2);
                    t = Arrays.copyOf(t, t.length * 2);
                    w = Arrays.copyOf(w, w.length * 2);
                }
                ms[count] = millis;
                t[count] = celsius;
                w[count] = Math.hypot(u, v);
                count++;
            }
        }
        if (count == 0) throw new IOException("empty weather CSV");
        this.hourMillis = Arrays.copyOf(ms, count);
        this.tempC = Arrays.copyOf(t, count);
        this.windMs = Arrays.copyOf(w, count);
    }

    @Override
    public double temperatureAt(long millis) {
        return interpolate(millis, tempC);
    }

    @Override
    public double windSpeedAt(long millis) {
        return interpolate(millis, windMs);
    }

    /** Number of hourly samples parsed (for tests / diagnostics). */
    public int size() {
        return hourMillis.length;
    }

    private double interpolate(long millis, double[] values) {
        int idx = Arrays.binarySearch(hourMillis, millis);
        if (idx >= 0) return values[idx];               // exact hour
        int hi = -idx - 1;                              // insertion point
        if (hi == 0) return values[0];                  // before the first sample → clamp
        if (hi == hourMillis.length) return values[values.length - 1]; // after the last → clamp
        int lo = hi - 1;
        long m0 = hourMillis[lo], m1 = hourMillis[hi];
        double frac = (double) (millis - m0) / (double) (m1 - m0);
        return values[lo] + (values[hi] - values[lo]) * frac;
    }
}
