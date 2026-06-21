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

package com.tfcode.comparetout.scenario;

import android.content.Context;

import com.tfcode.comparetout.model.scenario.SimulationInputData;
import com.tfcode.comparetout.scenario.sim.SimTime;

import java.io.File;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The on-disk contract for cached CDS/ERA5 weather downloads (Phase 6 of {@code plans/hp/plan.md}).
 *
 * <p>The fetch worker ({@code HeatPumpWeatherFetchWorker}) and the simulation ({@code SimulationWorker}) must
 * agree, byte-for-byte, on <b>where</b> a fetched series lives. This class is that single source of truth: a
 * deterministic file name derived from the heat-pump <b>location</b> (lat/lon) and the <b>grid period</b>
 * (min/max sim-grid instant). Both modes the user described fall out of the period automatically:</p>
 * <ul>
 *   <li><b>Synthetic / PVGIS scenarios</b> — the grid sits on the 2001 reference year, so the period is
 *       {@code 2001-01-01 … 2001-12-31} and we fetch ERA5 for 2001 (CDS covers 1940→present).</li>
 *   <li><b>Importer scenarios</b> — the grid sits on the user's actual data year(s), so the period is that
 *       real span and the weather lines up with the imported PV/load on the same UTC millis grid.</li>
 * </ul>
 *
 * <p>The cached file is the <b>raw ERA5 time-series CSV</b> — the very shape {@link
 * com.tfcode.comparetout.scenario.sim.CsvWeatherProvider} already parses for the offline sample asset. No
 * grid alignment is baked into the file: the provider interpolates onto the sim grid by millis at read time,
 * so caching the raw hourly CSV is sufficient and identical for both byte sources.</p>
 */
public final class HeatPumpWeatherCache {

    private HeatPumpWeatherCache() {}

    /** Sub-directory of {@link Context#getFilesDir()} holding the cached CSV downloads. */
    public static final String CACHE_DIR = "hp-weather-cache";

    private static final DecimalFormat LATLON = new DecimalFormat("#.000");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** The cache directory (created if absent). */
    public static File cacheDir(Context context) {
        File dir = new File(context.getFilesDir(), CACHE_DIR);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    /**
     * The deterministic cache file for a (location, period) pair. Period dates are the inclusive UTC calendar
     * days of the grid span — e.g. {@code cds_53.490_-10.015_2001-01-01_2001-12-31.csv}.
     */
    public static File cacheFile(Context context, double latitude, double longitude,
                                 String startIsoDate, String endIsoDate) {
        // Key on the ERA5 grid NODE (0.25°) nearest the point, not the raw user coords. The time-series
        // dataset returns the nearest-node series and the CDS webform itself snaps the point, so naming the
        // cache by the node keeps the file matching what was actually fetched (and lets two nearby points
        // share one download). The fetch worker snaps the request the same way, so worker and sim agree.
        String name = "cds_" + LATLON.format(snapToEra5Grid(latitude)) + "_"
                + LATLON.format(snapToEra5Grid(longitude)) + "_" + startIsoDate + "_" + endIsoDate + ".csv";
        return new File(cacheDir(context), name);
    }

    /** ERA5 single-levels sits on a 0.25° grid; snap an arbitrary coordinate to the nearest node. */
    public static double snapToEra5Grid(double coord) {
        return Math.round(coord / 0.25d) * 0.25d;
    }

    /** Convenience overload keyed directly off the grid millis (min/max → inclusive UTC days). */
    public static File cacheFile(Context context, double latitude, double longitude, long[] gridMillis) {
        long[] span = spanMillis(gridMillis);
        return cacheFile(context, latitude, longitude, isoDate(span[0]), isoDate(span[1]));
    }

    /**
     * Build the sim-grid instants from the load-profile rows, mirroring the exact convention {@code
     * SimulationWorker.buildHeatPumpComponent} uses (explicit {@code millisSinceEpoch} when present, else the
     * UTC reconstruction from date + minute-of-day). Returned in row order (ascending by construction).
     */
    public static long[] gridMillis(List<SimulationInputData> gridRows) {
        long[] millis = new long[gridRows.size()];
        for (int i = 0; i < gridRows.size(); i++) {
            SimulationInputData r = gridRows.get(i);
            millis[i] = (r.getMillisSinceEpoch() != null) ? r.getMillisSinceEpoch()
                    : SimTime.fromDateAndMinuteOfDay(r.getDate(), r.getMod(), ZoneOffset.UTC);
        }
        return millis;
    }

    /** {min, max} of a grid-millis array (used for the fetch period + the cache key). */
    public static long[] spanMillis(long[] gridMillis) {
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (long m : gridMillis) {
            if (m < min) min = m;
            if (m > max) max = m;
        }
        return new long[]{min, max};
    }

    /** UTC calendar day ({@code yyyy-MM-dd}) of an epoch-millis instant. */
    public static String isoDate(long millis) {
        return ISO_DATE.format(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate());
    }
}
