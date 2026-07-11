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

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class SemopxRateSourceTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private MockWebServer mServer;

    @Before
    public void setUp() throws Exception {
        mServer = new MockWebServer();
        mServer.start();
    }

    @After
    public void tearDown() throws Exception {
        mServer.shutdown();
    }

    private SemopxRateSource source(Clock clock) {
        String base = mServer.url("/").toString();
        base = base.substring(0, base.length() - 1); // no trailing slash
        return new SemopxRateSource(folder.getRoot(),
                new OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build(),
                base, base + "/bulk.zip", base + "/" + SemopxRateSource.LOOKBACK_FILE_NAME,
                clock, 0);
    }

    private static String csvFor(String isoStamp, String price) {
        return String.join("\n",
                "Market;ROI-DA",
                "Index prices;30;EUR",
                isoStamp,
                price);
    }

    @Test
    public void catalogWalkKeepsOnlySemDaResourcesInRange() throws IOException {
        String catalog = "{\"pagination\":{\"totalPages\":1},\"items\":["
                + "{\"ResourceName\":\"MarketResult_SEM-DA_PWR-MRC-D+1_20260601100000_a.csv\",\"Date\":\"2026-06-02T10:00:00\"},"
                + "{\"ResourceName\":\"MarketResult_SEM-IDA1_PWR-SEM-GB-D+1_20260601163000_b.csv\",\"Date\":\"2026-06-02T16:30:00\"},"
                + "{\"ResourceName\":\"MarketResult_SEM-DA_PWR-MRC-D+1_20260602100000_c.csv\",\"Date\":\"2026-06-03T10:00:00\"},"
                + "{\"ResourceName\":\"MarketResult_SEM-DA_PWR-MRC-D+1_20260603100000_d.csv\",\"Date\":\"2026-06-04T10:00:00\"}"
                + "]}";
        mServer.enqueue(new MockResponse().setBody(catalog));
        mServer.enqueue(new MockResponse().setBody(csvFor("2026-06-01T22:00:00Z", "100,0")));
        mServer.enqueue(new MockResponse().setBody(csvFor("2026-06-02T22:00:00Z", "200,0")));

        SemopxRateSource src = source(Clock.systemUTC());
        List<SemopxDayResultCsv.DayResult> days = src.fetchDaysFromCatalog(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2));

        // The IDA file and the out-of-range 06-03 auction were skipped.
        assertEquals(2, days.size());
        assertEquals(100.0, days.get(0).eurPerMwh[0], 1e-9);
        assertEquals(200.0, days.get(1).eurPerMwh[0], 1e-9);
        RecordedRequest catalogRequest = takeRequest();
        assertTrue(catalogRequest.getPath().contains("DPuG_ID=EA-001"));
        assertTrue(catalogRequest.getPath().contains("Date=%3E%3D2026-06-01"));
        assertTrue(takeRequest().getPath().endsWith(
                "/documents/MarketResult_SEM-DA_PWR-MRC-D+1_20260601100000_a.csv"));
    }

    @Test
    public void fetchYearIsCacheFirstAndMakesNoRequestsWhenCovered() throws IOException {
        for (int month = 1; month <= 12; month++) {
            DynamicPriceCache.MonthChunk chunk = new DynamicPriceCache.MonthChunk();
            chunk.marketId = SemopxRateSource.MARKET_ID;
            chunk.year = 2025;
            chunk.month = month;
            chunk.source = "EA-001 Market Results";
            chunk.utcMillis = new long[]{SeriesNormaliser.monthStartMillis(2025, month)};
            chunk.centsPerKwh = new double[]{6.0 + month};
            chunk.gapFilled = 0;
            DynamicPriceCache.store(folder.getRoot(), chunk);
        }
        Clock clock = Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZoneOffset.UTC);
        RateSeries series = source(clock).fetch(2025);

        assertTrue(series.isComplete());
        assertEquals(12, series.getEntries().size());
        assertEquals(7.0, series.getEntries().get(0).importCentsPerKwh, 1e-9);
        assertEquals(0, mServer.getRequestCount());
        assertTrue(series.getSourceRef().contains("EA-001"));
        assertTrue(series.getSourceRef().contains("not redistributed"));
    }

    @Test
    public void unfinishedMonthsAreReportedMissingWithoutFetching() throws IOException {
        Clock clock = Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZoneOffset.UTC);
        for (int month = 1; month <= 6; month++) {
            DynamicPriceCache.MonthChunk chunk = new DynamicPriceCache.MonthChunk();
            chunk.marketId = SemopxRateSource.MARKET_ID;
            chunk.year = 2026;
            chunk.month = month;
            chunk.source = "EA-001 Market Results";
            chunk.utcMillis = new long[]{SeriesNormaliser.monthStartMillis(2026, month)};
            chunk.centsPerKwh = new double[]{5.0};
            chunk.gapFilled = 0;
            DynamicPriceCache.store(folder.getRoot(), chunk);
        }
        RateSeries series = source(clock).fetch(2026);
        assertEquals(Arrays.asList(7, 8, 9, 10, 11, 12), series.getMissingMonths());
        assertEquals(0, mServer.getRequestCount());
    }

    @Test
    public void monthMissingFromDailyFeedsIsFilledFromLookbackWorkbook() throws IOException {
        // 2024 with every month but August pre-cached. Clock 2026-07: August
        // 2024 is past the catalog's rolling window, absent from the bulk
        // archive fixture — only the look-back workbook covers it.
        cacheMonthsOf2024ExceptAugust();
        byte[] xlsx = LookbackXlsxFixture.workbook(
                LookbackXlsxFixture.hourlyDamDays("2024-07-31T00:00:00Z", 32, 100.0));
        mServer.setDispatcher(feedsDispatcher(irrelevantBulkZip(), xlsx));
        Clock clock = Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZoneOffset.UTC);

        RateSeries series = source(clock).fetch(2024);

        assertTrue(series.isComplete());
        assertTrue(series.getSourceRef().contains("Lookback2 workbook"));
        // 11 single-entry cached chunks + 31 days × 48 half-hours of August.
        assertEquals(11 + 31 * 48, series.getEntries().size());
        // The workbook was kept for later fetches and August was chunk-cached.
        assertTrue(new File(folder.getRoot(), SemopxRateSource.LOOKBACK_FILE_NAME).isFile());
        assertTrue(DynamicPriceCache.exists(folder.getRoot(),
                SemopxRateSource.MARKET_ID, 2024, 8));
    }

    @Test
    public void workbookAlreadyOnDiskIsReusedWithoutRedownload() throws IOException {
        cacheMonthsOf2024ExceptAugust();
        byte[] xlsx = LookbackXlsxFixture.workbook(
                LookbackXlsxFixture.hourlyDamDays("2024-07-31T00:00:00Z", 32, 100.0));
        Files.write(new File(folder.getRoot(),
                SemopxRateSource.LOOKBACK_FILE_NAME).toPath(), xlsx);
        // The dispatcher 404s the workbook URL: success proves the disk copy served.
        mServer.setDispatcher(feedsDispatcher(irrelevantBulkZip(), null));
        Clock clock = Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZoneOffset.UTC);

        RateSeries series = source(clock).fetch(2024);

        assertTrue(series.isComplete());
        assertTrue(series.getSourceRef().contains("Lookback2 workbook"));
    }

    private void cacheMonthsOf2024ExceptAugust() throws IOException {
        for (int month = 1; month <= 12; month++) {
            if (month == 8) continue;
            DynamicPriceCache.MonthChunk chunk = new DynamicPriceCache.MonthChunk();
            chunk.marketId = SemopxRateSource.MARKET_ID;
            chunk.year = 2024;
            chunk.month = month;
            chunk.source = "EA-001 Market Results";
            chunk.utcMillis = new long[]{SeriesNormaliser.monthStartMillis(2024, month)};
            chunk.centsPerKwh = new double[]{5.0};
            chunk.gapFilled = 0;
            DynamicPriceCache.store(folder.getRoot(), chunk);
        }
    }

    /** A real (range-servable) bulk zip whose only SEM-DA day is far out of range. */
    private static byte[] irrelevantBulkZip() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("MarketResult_SEM-DA_PWR-MRC-D+1_20180101000000_x.csv"));
            zip.write("irrelevant".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    /**
     * Serves the three SEMOpx endpoints: an empty EA-001 catalog, the bulk zip
     * with honest HEAD/Range semantics, and the look-back workbook ({@code null}
     * = 404, for the no-redownload test).
     */
    private Dispatcher feedsDispatcher(byte[] bulkZip, byte[] lookbackXlsx) {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path == null) return new MockResponse().setResponseCode(404);
                if (path.startsWith("/api/v1/documents/static-reports"))
                    return new MockResponse()
                            .setBody("{\"pagination\":{\"totalPages\":1},\"items\":[]}");
                if (path.equals("/bulk.zip")) {
                    if ("HEAD".equals(request.getMethod()))
                        return new MockResponse()
                                .setHeader("Content-Length", bulkZip.length);
                    String range = request.getHeader("Range");
                    if (range == null || !range.startsWith("bytes="))
                        return new MockResponse().setResponseCode(416);
                    String[] bounds = range.substring("bytes=".length()).split("-");
                    int start = Integer.parseInt(bounds[0]);
                    int end = Math.min(Integer.parseInt(bounds[1]), bulkZip.length - 1);
                    okio.Buffer buffer = new okio.Buffer();
                    buffer.write(bulkZip, start, end - start + 1);
                    return new MockResponse().setResponseCode(206).setBody(buffer);
                }
                if (path.equals("/" + SemopxRateSource.LOOKBACK_FILE_NAME)) {
                    if (lookbackXlsx == null) return new MockResponse().setResponseCode(404);
                    okio.Buffer buffer = new okio.Buffer();
                    buffer.write(lookbackXlsx);
                    return new MockResponse().setBody(buffer);
                }
                return new MockResponse().setResponseCode(404);
            }
        };
    }

    private RecordedRequest takeRequest() {
        try {
            return mServer.takeRequest(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
