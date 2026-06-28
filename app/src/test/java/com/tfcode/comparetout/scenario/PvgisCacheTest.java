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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Locale;

/**
 * Pure-JVM cover for {@link PvgisCache}'s cache-key (filename) round-trip and the per-interval scaling.
 *
 * <p>The cache key is {@code (lat, lon, slope, az, loss)} — array size is deliberately excluded because one
 * reference download (1 kWp) is re-scaled to any panel. The filename must use a {@code '.'} decimal on every
 * device locale (it feeds both the file system and, indirectly, the {@code ROUND(lat,3)} match in the DB).</p>
 */
public class PvgisCacheTest {

    @Test
    public void fileNameEncodesLocationOrientationLossWithDotDecimals() {
        Locale prev = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY); // would render commas without Locale.ROOT
            assertEquals("pvgis_53.490_-10.000_24_136_14.json",
                    PvgisCache.fileName(53.49, -10.0, 24, 136, 14));
        } finally {
            Locale.setDefault(prev);
        }
    }

    @Test
    public void parseRoundTripsAllFields() {
        String name = PvgisCache.fileName(53.49, -10.015, 24, 136, 14);
        PvgisCache.Key k = PvgisCache.parse(name);
        assertNotNull(k);
        assertEquals(53.490, k.latitude, 1e-9);
        assertEquals(-10.015, k.longitude, 1e-9);
        assertEquals(24, k.slope);
        assertEquals(136, k.azimuth);
        assertEquals(14, k.lossPct);
    }

    @Test
    public void parseRejectsForeignAndMalformedNames() {
        assertNull("a CDS file is not a PVGIS file",
                PvgisCache.parse("cds_53.490_-10.000_2001-01-01_2001-12-31.csv"));
        assertNull("too few fields (no loss)", PvgisCache.parse("pvgis_53.490_-10.000_24_136.json"));
        assertNull("non-numeric coordinate", PvgisCache.parse("pvgis_x_y_24_136_14.json"));
        assertNull(PvgisCache.parse(null));
    }

    @Test
    public void intervalKwhIsLinearInArraySize() {
        // The cached P is per 1 kWp; a 14×325 Wp array yields exactly double a 7×325 Wp array at the same
        // location/loss — the linearity the file cache relies on to avoid a per-array fetch.
        double pPerKWp = 800.0; // PVGIS P (W) for 1 kWp at some hour
        double small = PvgisCache.intervalKwh(pPerKWp, 7 * 325 / 1000.0);
        double big = PvgisCache.intervalKwh(pPerKWp, 14 * 325 / 1000.0);
        assertEquals(2.0, big / small, 1e-9);
        // And the absolute value matches the old direct formula P_array / 12 / 1000 with P_array = P*kWp.
        assertEquals(800.0 * 2.275 / 12d / 1000d, small, 1e-12);
    }
}
