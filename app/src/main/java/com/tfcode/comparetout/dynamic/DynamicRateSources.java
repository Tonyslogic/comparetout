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

import android.content.Context;

/**
 * Maps a {@code DynamicMarket.id} (from {@code RegionProfile.dynamicMarkets})
 * to its {@link HistoricalRateSource}. Adding a market/region = add a case
 * here and a registry entry on the region profile.
 */
public final class DynamicRateSources {

    private DynamicRateSources() {}

    /** The source for a market id, or {@code null} when this build has none. */
    public static HistoricalRateSource forMarket(String marketId, Context context) {
        if (SemopxRateSource.MARKET_ID.equals(marketId)) {
            return new SemopxRateSource(DynamicPriceCache.cacheDir(context));
        }
        // GB Agile market ids carry the GSP region: "GB-AGILE-C" (import) and
        // "GB-AGILE-EXPORT-C" (Outgoing Agile).
        //
        // The EXPORT prefix must be tested FIRST: it contains the import prefix,
        // so the import branch would match "GB-AGILE-EXPORT-C", then fail its
        // single-letter region check and return null — and a null source surfaces
        // as a plan stuck "pending" forever with no error anywhere.
        if (!(null == marketId)
                && marketId.startsWith(OctopusAgileRateSource.EXPORT_MARKET_PREFIX)) {
            String region = marketId.substring(
                    OctopusAgileRateSource.EXPORT_MARKET_PREFIX.length());
            if (isGspRegion(region)) {
                return new OctopusAgileRateSource(context, region, /* exportSide = */ true);
            }
            return null;
        }
        if (!(null == marketId) && marketId.startsWith(OctopusAgileRateSource.MARKET_PREFIX)) {
            String region = marketId.substring(OctopusAgileRateSource.MARKET_PREFIX.length());
            if (isGspRegion(region)) {
                return new OctopusAgileRateSource(context, region);
            }
        }
        return null;
    }

    /** A single GSP letter, "A".."P". */
    private static boolean isGspRegion(String region) {
        return region.length() == 1 && region.charAt(0) >= 'A' && region.charAt(0) <= 'P';
    }
}
