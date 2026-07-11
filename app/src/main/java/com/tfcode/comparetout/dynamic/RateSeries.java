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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A normalised historical wholesale price series for a 12-month backtest window
 * (which may span two calendar years): ordered half-hourly UTC entries in c/kWh
 * (EUR/MWh ÷ 10 — the millis/UTC ingestion convention, no timezone maths
 * downstream). Hourly market eras are expanded to half-hourly on ingestion
 * (price repeated), so consumers see one resolution regardless of source vintage.
 * <p>
 * A series may be incomplete: {@link #getMissingMonths()} lists months (1-12) no
 * public source could supply (unambiguous — a ≤12-month window holds each month
 * at most once). The materialiser refuses windows with missing months; the UI
 * reports them instead of silently zero-filling. {@link #getYear()} is the
 * window's first calendar year.
 */
public class RateSeries {

    /** One half-hour period: UTC epoch millis of period start, import price c/kWh. */
    public static class Entry {
        public final long utcMillis;
        public final double importCentsPerKwh;

        public Entry(long utcMillis, double importCentsPerKwh) {
            this.utcMillis = utcMillis;
            this.importCentsPerKwh = importCentsPerKwh;
        }
    }

    private final String marketId;
    private final int year;
    private final List<Entry> entries;
    private final List<Integer> missingMonths;
    private final int gapFilledCount;
    private final String sourceRef;

    public RateSeries(String marketId, int year, List<Entry> entries,
                      List<Integer> missingMonths, int gapFilledCount, String sourceRef) {
        this.marketId = marketId;
        this.year = year;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.missingMonths = Collections.unmodifiableList(new ArrayList<>(missingMonths));
        this.gapFilledCount = gapFilledCount;
        this.sourceRef = sourceRef;
    }

    public String getMarketId() {
        return marketId;
    }

    public int getYear() {
        return year;
    }

    /** Chronological half-hourly entries for the parts of the year that are covered. */
    public List<Entry> getEntries() {
        return entries;
    }

    /** Months (1-12) with no data from any source; empty = complete year. */
    public List<Integer> getMissingMonths() {
        return missingMonths;
    }

    public boolean isComplete() {
        return missingMonths.isEmpty();
    }

    /** Periods filled by carrying the previous value across a source gap. */
    public int getGapFilledCount() {
        return gapFilledCount;
    }

    /** Provenance: dataset/report identifiers and fetch date — copied into plan references. */
    public String getSourceRef() {
        return sourceRef;
    }
}
