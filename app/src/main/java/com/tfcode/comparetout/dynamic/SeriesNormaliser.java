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

package com.tfcode.comparetout.dynamic;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Normalises parsed auction-day results into one UTC calendar month at a fixed
 * half-hourly grid, in c/kWh:
 * <ul>
 *   <li>hourly-era results expand to half-hourly (price repeated);</li>
 *   <li>EUR/MWh ÷ 10 → c/kWh;</li>
 *   <li>republished auctions overwrite (last parse wins per period);</li>
 *   <li>occasional missing periods are filled by the previous value with a
 *       running count — but a month with more than {@link #MAX_GAP_FRACTION}
 *       of its periods missing is reported as not covered rather than
 *       invented.</li>
 * </ul>
 * DST-day period counts need no special casing: everything is UTC throughout.
 */
public final class SeriesNormaliser {

    private SeriesNormaliser() {}

    public static final long HALF_HOUR_MILLIS = 30L * 60L * 1000L;
    /** A month missing more than this fraction of periods is "not covered". */
    public static final double MAX_GAP_FRACTION = 0.10;

    /** The normalised half-hourly grid of one UTC calendar month. */
    public static final class MonthSeries {
        public final int year;
        public final int month;
        public final long[] utcMillis;
        public final double[] centsPerKwh;
        public final int gapFilled;

        MonthSeries(int year, int month, long[] utcMillis, double[] centsPerKwh, int gapFilled) {
            this.year = year;
            this.month = month;
            this.utcMillis = utcMillis;
            this.centsPerKwh = centsPerKwh;
            this.gapFilled = gapFilled;
        }
    }

    /** The normalised half-hourly grid of an arbitrary UTC half-open span. */
    public static final class GridSeries {
        public final long[] utcMillis;
        public final double[] centsPerKwh;
        public final int gapFilled;

        GridSeries(long[] utcMillis, double[] centsPerKwh, int gapFilled) {
            this.utcMillis = utcMillis;
            this.centsPerKwh = centsPerKwh;
            this.gapFilled = gapFilled;
        }
    }

    /** UTC epoch millis of the first instant of a month. */
    public static long monthStartMillis(int year, int month) {
        return LocalDate.of(year, month, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    /** UTC epoch millis of the first instant of the following month. */
    public static long monthEndMillis(int year, int month) {
        LocalDate first = LocalDate.of(year, month, 1).plusMonths(1);
        return first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    /**
     * Assemble one UTC month from day results (auction days straddle months —
     * out-of-month periods are simply dropped, so callers pass the auctions of
     * the last day of the previous month through the last day of this month).
     *
     * @return the month grid, or {@code null} when coverage is below the
     *         {@link #MAX_GAP_FRACTION} threshold.
     */
    public static MonthSeries assembleMonth(int year, int month,
                                            List<SemopxDayResultCsv.DayResult> days) {
        GridSeries grid = assembleRange(monthStartMillis(year, month),
                monthEndMillis(year, month), days);
        if (null == grid) return null;
        return new MonthSeries(year, month, grid.utcMillis, grid.centsPerKwh, grid.gapFilled);
    }

    /**
     * Assemble an arbitrary UTC half-open span {@code [startMillis, endMillis)}
     * (a whole number of half-hours) onto the half-hourly grid — the same rules
     * as {@link #assembleMonth} (hourly→half-hourly expansion, EUR/MWh ÷ 10,
     * republication-overwrite, previous-value gap-fill, {@link #MAX_GAP_FRACTION}
     * coverage floor). Out-of-span periods in the day results are dropped, so a
     * partial-month boundary of a rolling window carries only the days it needs.
     *
     * @return the grid, or {@code null} when coverage is below the threshold.
     */
    public static GridSeries assembleRange(long startMillis, long endMillis,
                                           List<SemopxDayResultCsv.DayResult> days) {
        long start = startMillis;
        long end = endMillis;
        NavigableMap<Long, Double> byPeriod = new TreeMap<>();
        for (SemopxDayResultCsv.DayResult day : days) {
            for (int i = 0; i < day.utcMillis.length; i++) {
                long t = day.utcMillis[i];
                double cKwh = day.eurPerMwh[i] / 10d;
                if (day.resolutionMinutes == 60) {
                    put(byPeriod, t, cKwh, start, end);
                    put(byPeriod, t + HALF_HOUR_MILLIS, cKwh, start, end);
                } else if (day.resolutionMinutes == 30) {
                    put(byPeriod, t, cKwh, start, end);
                }
                // Other resolutions (none published to date) are ignored rather
                // than mis-gridded; the gap threshold below surfaces the loss.
            }
        }
        int periods = (int) ((end - start) / HALF_HOUR_MILLIS);
        int missing = periods - byPeriod.size();
        if (missing > periods * MAX_GAP_FRACTION) return null;

        long[] millis = new long[periods];
        double[] cents = new double[periods];
        int gapFilled = 0;
        Double previous = null;
        // A leading gap has no previous value; backfill it from the first known
        // price once found (missing is bounded by the threshold above).
        int leading = 0;
        for (int i = 0; i < periods; i++) {
            long t = start + i * HALF_HOUR_MILLIS;
            millis[i] = t;
            Double p = byPeriod.get(t);
            if (null == p) {
                if (null == previous) {
                    leading++;
                    continue;
                }
                p = previous;
                gapFilled++;
            } else if (leading > 0) {
                for (int j = 0; j < leading; j++) cents[j] = p;
                gapFilled += leading;
                leading = 0;
            }
            cents[i] = p;
            previous = p;
        }
        if (null == previous) return null; // nothing at all (defensive; threshold catches this)
        return new GridSeries(millis, cents, gapFilled);
    }

    private static void put(NavigableMap<Long, Double> map, long t, double v, long start, long end) {
        if (t >= start && t < end) map.put(t, v);
    }
}
