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
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class RangedZipReaderTest {

    /** Byte-range access over an in-memory archive; counts fetched bytes. */
    private static final class ArrayFetcher implements RangedZipReader.ByteRangeFetcher {
        final byte[] data;
        long bytesFetched = 0;

        ArrayFetcher(byte[] data) {
            this.data = data;
        }

        @Override
        public long length() {
            return data.length;
        }

        @Override
        public byte[] fetch(long start, long endInclusive) {
            int from = (int) start;
            int to = (int) Math.min(endInclusive + 1, data.length);
            bytesFetched += (to - from);
            byte[] out = new byte[to - from];
            System.arraycopy(data, from, out, 0, out.length);
            return out;
        }
    }

    private static byte[] buildZip(boolean withStoredEntry) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("folder/MarketResult_SEM-DA_a_20190115110000_x.csv"));
            zip.write("Market Area;ROI-DA\nIndex prices;60;EUR\n2019-01-15T23:00:00Z\n56,850"
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("folder/MarketResult_SEM-IDA1_b_20190115170000_x.csv"));
            zip.write("intraday".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (withStoredEntry) {
                byte[] stored = "stored-content".getBytes(StandardCharsets.UTF_8);
                ZipEntry entry = new ZipEntry("stored.txt");
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(stored.length);
                CRC32 crc = new CRC32();
                crc.update(stored);
                entry.setCrc(crc.getValue());
                zip.putNextEntry(entry);
                zip.write(stored);
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test
    public void listsTheCentralDirectory() throws IOException {
        ArrayFetcher fetcher = new ArrayFetcher(buildZip(false));
        List<RangedZipReader.Entry> entries = RangedZipReader.centralDirectory(fetcher);
        assertEquals(2, entries.size());
        assertEquals("folder/MarketResult_SEM-DA_a_20190115110000_x.csv", entries.get(0).name);
    }

    @Test
    public void readsADeflatedEntryWithoutTouchingTheRest() throws IOException {
        byte[] zip = buildZip(false);
        ArrayFetcher fetcher = new ArrayFetcher(zip);
        List<RangedZipReader.Entry> entries = RangedZipReader.centralDirectory(fetcher);
        long directoryBytes = fetcher.bytesFetched;
        byte[] content = RangedZipReader.read(fetcher, entries.get(0));
        String csv = new String(content, StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("Market Area;ROI-DA"));
        // The point of ranged reading: far less than the archive re-fetched per entry.
        assertTrue(fetcher.bytesFetched - directoryBytes < zip.length);
    }

    @Test
    public void readsAStoredEntry() throws IOException {
        ArrayFetcher fetcher = new ArrayFetcher(buildZip(true));
        List<RangedZipReader.Entry> entries = RangedZipReader.centralDirectory(fetcher);
        RangedZipReader.Entry stored = entries.get(2);
        assertEquals("stored.txt", stored.name);
        assertEquals("stored-content",
                new String(RangedZipReader.read(fetcher, stored), StandardCharsets.UTF_8));
    }

    @Test
    public void parsedEntryDatesMatchTheSourceHelper() throws IOException {
        // The auction-date helper is what SemopxRateSource keys the archive by.
        assertEquals("20190115", SemopxRateSource.auctionDateOf(
                "MarketResult_SEM-DA_PWR-MRC-D+1_20190115110000_20190115113007.csv"));
        assertEquals("20260708", SemopxRateSource.auctionDateOf(
                "MarketResult_SEM-DA_PWR-MRC-D+1_20260708100000_20260708105501.csv"));
        assertEquals(null, SemopxRateSource.auctionDateOf("not-a-market-result.csv"));
    }
}
