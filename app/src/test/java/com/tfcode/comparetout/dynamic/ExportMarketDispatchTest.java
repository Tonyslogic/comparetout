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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Market-id → rate-source dispatch for Outgoing Agile
 * (plans/region/import-plans.md §7.1b).
 *
 * The export prefix CONTAINS the import prefix, which is a live trap: tested in
 * the wrong order, {@code GB-AGILE-EXPORT-C} matches the import branch, fails
 * its single-letter region check and returns null — and a null source surfaces
 * as a dynamic plan stuck "pending" forever, with no error raised anywhere.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ExportMarketDispatchTest {

    private Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    @Test
    public void theExportPrefixContainsTheImportPrefix() {
        // The reason this test exists. If this ever stops being true the
        // ordering constraint in DynamicRateSources can be relaxed.
        assertTrue(OctopusAgileRateSource.EXPORT_MARKET_PREFIX
                .startsWith(OctopusAgileRateSource.MARKET_PREFIX));
    }

    @Test
    public void anExportMarketIdResolvesToAnExportSource() {
        HistoricalRateSource source = DynamicRateSources.forMarket("GB-AGILE-EXPORT-C", context());
        assertNotNull("export Agile must resolve, not fall through to null", source);
        assertEquals("GB-AGILE-EXPORT-C", source.marketId());
    }

    @Test
    public void anImportMarketIdStillResolvesToAnImportSource() {
        HistoricalRateSource source = DynamicRateSources.forMarket("GB-AGILE-C", context());
        assertNotNull(source);
        assertEquals("GB-AGILE-C", source.marketId());
    }

    @Test
    public void everyGspRegionResolvesOnBothSides() {
        for (char r = 'A'; r <= 'P'; r++) {
            assertNotNull("import " + r,
                    DynamicRateSources.forMarket("GB-AGILE-" + r, context()));
            assertNotNull("export " + r,
                    DynamicRateSources.forMarket("GB-AGILE-EXPORT-" + r, context()));
        }
    }

    @Test
    public void anUnknownRegionResolvesToNullOnBothSides() {
        assertNull(DynamicRateSources.forMarket("GB-AGILE-Z", context()));
        assertNull(DynamicRateSources.forMarket("GB-AGILE-EXPORT-Z", context()));
        assertNull(DynamicRateSources.forMarket("GB-AGILE-EXPORT-CC", context()));
    }

    @Test
    public void unrelatedMarketIdsAreUnaffected() {
        assertNull(DynamicRateSources.forMarket("NOT-A-MARKET", context()));
        assertNull(DynamicRateSources.forMarket(null, context()));
        assertNotNull("the Irish market still resolves",
                DynamicRateSources.forMarket(SemopxRateSource.MARKET_ID, context()));
    }
}
