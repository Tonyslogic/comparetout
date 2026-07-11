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
import static org.junit.Assert.assertThrows;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

public class SemopxLookbackXlsxTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File write(byte[] xlsx) throws IOException {
        File file = folder.newFile("lookback.xlsx");
        Files.write(file.toPath(), xlsx);
        return file;
    }

    @Test
    public void readsDamRowsAndSkipsHeaderAndIntradayAuctions() throws IOException {
        List<LookbackXlsxFixture.Row> rows = Arrays.asList(
                new LookbackXlsxFixture.Row("DAM", "2023-08-01T00:00:00Z", 95.5),
                new LookbackXlsxFixture.Row("IDA1", "2023-08-01T00:00:00Z", 9999.0),
                new LookbackXlsxFixture.Row("DAM", "2023-08-01T01:00:00Z", -12.25),
                new LookbackXlsxFixture.Row("IDA2", "2023-08-01T01:30:00Z", 8888.0));
        NavigableMap<Long, Double> prices =
                SemopxLookbackXlsx.readDamPrices(write(LookbackXlsxFixture.workbook(rows)));

        assertEquals(2, prices.size());
        assertEquals(95.5,
                prices.get(Instant.parse("2023-08-01T00:00:00Z").toEpochMilli()), 1e-9);
        assertEquals(-12.25,
                prices.get(Instant.parse("2023-08-01T01:00:00Z").toEpochMilli()), 1e-9);
    }

    @Test
    public void resolvesSheetThroughWorkbookRelsWhateverThePartIsCalled() throws IOException {
        List<LookbackXlsxFixture.Row> rows = Arrays.asList(
                new LookbackXlsxFixture.Row("DAM", "2024-02-01T00:00:00Z", 80.0));
        byte[] xlsx = LookbackXlsxFixture.workbook(rows, "auctions_to", "worksheets/data99.xml");

        NavigableMap<Long, Double> prices = SemopxLookbackXlsx.readDamPrices(write(xlsx));
        assertEquals(1, prices.size());
    }

    @Test
    public void missingAuctionsSheetIsAnError() throws IOException {
        byte[] xlsx = LookbackXlsxFixture.workbook(
                Arrays.asList(new LookbackXlsxFixture.Row("DAM", "2024-02-01T00:00:00Z", 80.0)),
                "renamed_table", "worksheets/sheet5.xml");
        File file = write(xlsx);
        assertThrows(IOException.class, () -> SemopxLookbackXlsx.readDamPrices(file));
    }

    @Test
    public void acceptsExcelSerialTimestampsAsFallback() throws IOException {
        // 44197.5 = 2021-01-01T12:00:00Z in the 1900 date system.
        List<LookbackXlsxFixture.Row> rows = Arrays.asList(
                new LookbackXlsxFixture.Row("DAM", "44197.5", 70.0, true));
        NavigableMap<Long, Double> prices =
                SemopxLookbackXlsx.readDamPrices(write(LookbackXlsxFixture.workbook(rows)));

        assertEquals(1, prices.size());
        assertEquals(70.0,
                prices.get(Instant.parse("2021-01-01T12:00:00Z").toEpochMilli()), 1e-9);
    }

    @Test
    public void toDayResultsSplitsRunsOnHolesAndResolutionChanges() {
        NavigableMap<Long, Double> prices = new TreeMap<>();
        long base = Instant.parse("2024-06-01T00:00:00Z").toEpochMilli();
        long hour = 3_600_000L;
        // Hourly run of 3, then a 2-hour hole, then a half-hourly run of 4.
        prices.put(base, 10.0);
        prices.put(base + hour, 11.0);
        prices.put(base + 2 * hour, 12.0);
        long later = base + 5 * hour;
        prices.put(later, 20.0);
        prices.put(later + hour / 2, 21.0);
        prices.put(later + hour, 22.0);
        prices.put(later + 3 * hour / 2, 23.0);

        List<SemopxDayResultCsv.DayResult> days = SemopxLookbackXlsx.toDayResults(prices);

        assertEquals(2, days.size());
        assertEquals(60, days.get(0).resolutionMinutes);
        assertEquals(3, days.get(0).utcMillis.length);
        assertEquals(12.0, days.get(0).eurPerMwh[2], 1e-9);
        assertEquals(30, days.get(1).resolutionMinutes);
        assertEquals(4, days.get(1).utcMillis.length);
        assertEquals(23.0, days.get(1).eurPerMwh[3], 1e-9);
    }
}
