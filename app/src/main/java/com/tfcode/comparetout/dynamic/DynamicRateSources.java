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
        return null;
    }
}
