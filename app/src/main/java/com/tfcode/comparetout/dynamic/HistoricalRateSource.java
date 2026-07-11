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

import java.io.IOException;

/**
 * A pluggable fetcher of historical wholesale prices for one market
 * (the region registry {@code RegionProfile.dynamicMarkets} maps market ids
 * to implementations). Implementations fetch on-device from the market
 * operator's public publications, normalise on ingestion (UTC millis,
 * half-hourly, c/kWh) and cache month chunks in {@link DynamicPriceCache} so
 * an interrupted year-fetch resumes from the first missing month.
 */
public interface HistoricalRateSource {

    /** Stable market descriptor id, e.g. "ISEM-DAM" — matches DynamicTerms.market. */
    String marketId();

    /** Whether the operator requires registered credentials (drives credential UX). */
    boolean needsCredentials();

    /**
     * Fetch (cache-first) the normalised half-hourly series for a window of
     * {@code months} consecutive calendar months starting at
     * {@code (startYear, startMonth)} — a 12-month window may span two calendar
     * years. Never throws for partial coverage — missing months are reported on
     * the returned series (as month numbers 1-12, unambiguous because a ≤12-month
     * window holds each month at most once); throws only when nothing can be
     * fetched at all.
     */
    RateSeries fetchWindow(int startYear, int startMonth, int months) throws IOException;

    /**
     * Fetch one whole calendar year (Jan–Dec) — the back-compat shape used by
     * legacy year-bound plans and GB Agile. Delegates to {@link #fetchWindow}.
     */
    default RateSeries fetch(int year) throws IOException {
        return fetchWindow(year, 1, 12);
    }
}
