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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class DynamicPriceCacheTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static DynamicPriceCache.MonthChunk chunk(int year, int month) {
        DynamicPriceCache.MonthChunk chunk = new DynamicPriceCache.MonthChunk();
        chunk.marketId = "ISEM-DAM";
        chunk.year = year;
        chunk.month = month;
        chunk.source = "test";
        chunk.utcMillis = new long[]{1000L, 2000L};
        chunk.centsPerKwh = new double[]{6.5, 7.5};
        chunk.gapFilled = 1;
        return chunk;
    }

    @Test
    public void storeAndLoadRoundTrips() throws IOException {
        File dir = folder.getRoot();
        assertFalse(DynamicPriceCache.exists(dir, "ISEM-DAM", 2025, 3));
        DynamicPriceCache.store(dir, chunk(2025, 3));
        assertTrue(DynamicPriceCache.exists(dir, "ISEM-DAM", 2025, 3));
        DynamicPriceCache.MonthChunk back = DynamicPriceCache.load(dir, "ISEM-DAM", 2025, 3);
        assertNotNull(back);
        assertEquals(2025, back.year);
        assertEquals(3, back.month);
        assertEquals(2, back.utcMillis.length);
        assertEquals(7.5, back.centsPerKwh[1], 1e-9);
        assertEquals(1, back.gapFilled);
        assertEquals("ISEM-DAM_2025_03.json", DynamicPriceCache.fileName("ISEM-DAM", 2025, 3));
    }

    @Test
    public void corruptChunkReadsAsAMiss() throws IOException {
        File dir = folder.getRoot();
        DynamicPriceCache.store(dir, chunk(2025, 4));
        Files.write(DynamicPriceCache.chunkFile(dir, "ISEM-DAM", 2025, 4).toPath(),
                "{not json".getBytes(StandardCharsets.UTF_8));
        assertNull(DynamicPriceCache.load(dir, "ISEM-DAM", 2025, 4));
    }

    @Test
    public void listFiltersByMarket() throws IOException {
        File dir = folder.getRoot();
        DynamicPriceCache.store(dir, chunk(2025, 1));
        DynamicPriceCache.MonthChunk other = chunk(2025, 1);
        other.marketId = "GB-AGILE";
        DynamicPriceCache.store(dir, other);
        assertEquals(1, DynamicPriceCache.listCacheFiles(dir, "ISEM-DAM").length);
        assertEquals(1, DynamicPriceCache.listCacheFiles(dir, "GB-AGILE").length);
    }
}
