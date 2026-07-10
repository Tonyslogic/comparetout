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
     * Fetch (cache-first) the normalised half-hourly series for one calendar year.
     * Never throws for partial coverage — missing months are reported on the
     * returned series; throws only when nothing can be fetched at all.
     */
    RateSeries fetch(int year) throws IOException;
}
