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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * I-SEM day-ahead prices from SEMOpx's public publications (no credentials —
 * verified 2026-07: the reports API and the historical bulk archive are both
 * unauthenticated). Licensing position: on-device fetch for the user's own
 * analysis; derived prices are never redistributed (terms-only export).
 * <p>
 * A calendar year is assembled month-by-month, cache-first
 * ({@link DynamicPriceCache}), from two publications:
 * <ul>
 *   <li><b>EA-001 "Market Results" catalog</b> (reports.semopx.com) — one CSV
 *       per auction day, but only a rolling ~12-month retention window
 *       (verified: oldest bulk item tracks ~1 year behind today);</li>
 *   <li><b>DAM-IDM-Market-Results.zip</b> (semopx.com general publications) —
 *       the same daily CSVs from market go-live (2018-10). Individual CSVs are
 *       pulled out of the ~115&nbsp;MB archive with byte-range requests
 *       ({@link RangedZipReader}), never a full download;</li>
 *   <li><b>Lookback2_mkt.xlsx</b> (semopx.com general publications) — the
 *       aggregated look-back workbook covering months the other two drop
 *       (verified 2026-07: DAM hourly, 2021-01 → the workbook's build month).
 *       Downloaded once (~23&nbsp;MB) only when a month is absent from both
 *       daily feeds, kept in the price-cache directory (surfaced and deletable
 *       in the Data sources cache view), parsed by {@link SemopxLookbackXlsx}.</li>
 * </ul>
 * The publications still may not cover every month (SEMOpx refreshes the bulk
 * files sporadically); months absent from all three are reported as missing on
 * the {@link RateSeries} rather than invented.
 */
public class SemopxRateSource implements HistoricalRateSource {

    public static final String MARKET_ID = "ISEM-DAM";

    static final String DEFAULT_CATALOG_BASE = "https://reports.semopx.com";
    static final String DEFAULT_BULK_ZIP_URL =
            "https://www.semopx.com/documents/general-publications/DAM-IDM-Market-Results.zip";
    static final String DEFAULT_LOOKBACK_XLSX_URL =
            "https://www.semopx.com/documents/general-publications/Lookback2_mkt.xlsx";
    /**
     * Local name of the cached look-back workbook. It lives beside the month
     * chunks in the dynamic-price-cache directory so the Data sources cache
     * view can list and delete it like any other cached price data.
     */
    public static final String LOOKBACK_FILE_NAME = "Lookback2_mkt.xlsx";
    /** Pause between successive report downloads (OctopusTariffPlans precedent). */
    static final long POLITE_DELAY_MS = 300;
    private static final String SEM_DA_PREFIX = "MarketResult_SEM-DA_";

    private final File cacheDir;
    private final OkHttpClient http;
    private final String catalogBase;
    private final String bulkZipUrl;
    private final String lookbackXlsxUrl;
    private final Clock clock;
    private final long politeDelayMs;

    /** SEM-DA bulk-archive entries by auction date (yyyyMMdd), loaded on first use. */
    private Map<String, RangedZipReader.Entry> bulkEntries;
    private HttpByteRangeFetcher bulkFetcher;
    /** DAM series of the look-back workbook, loaded (downloading if needed) on first use. */
    private java.util.NavigableMap<Long, Double> lookbackPrices;
    /** First look-back load failure — cached so one bad download isn't retried 12× per year. */
    private IOException lookbackFailure;

    public SemopxRateSource(File cacheDir) {
        this(cacheDir,
                new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .build(),
                DEFAULT_CATALOG_BASE, DEFAULT_BULK_ZIP_URL, DEFAULT_LOOKBACK_XLSX_URL,
                Clock.systemUTC(), POLITE_DELAY_MS);
    }

    SemopxRateSource(File cacheDir, OkHttpClient http, String catalogBase, String bulkZipUrl,
                     String lookbackXlsxUrl, Clock clock, long politeDelayMs) {
        this.cacheDir = cacheDir;
        this.http = http;
        this.catalogBase = catalogBase;
        this.bulkZipUrl = bulkZipUrl;
        this.lookbackXlsxUrl = lookbackXlsxUrl;
        this.clock = clock;
        this.politeDelayMs = politeDelayMs;
    }

    @Override
    public String marketId() {
        return MARKET_ID;
    }

    @Override
    public boolean needsCredentials() {
        return false;
    }

    @Override
    public RateSeries fetchWindow(int startYear, int startMonth, int months) throws IOException {
        List<RateSeries.Entry> entries = new ArrayList<>(366 * 48);
        List<Integer> missing = new ArrayList<>();
        Set<String> sources = new LinkedHashSet<>();
        int gapFilled = 0;
        long now = clock.millis();
        IOException lastFailure = null;

        for (int i = 0; i < months; i++) {
            // Real (year, month) of this window slot — a 12-month window may cross
            // a calendar-year boundary, so the actual year advances after December.
            int year = startYear + (startMonth - 1 + i) / 12;
            int month = (startMonth - 1 + i) % 12 + 1;
            if (SeriesNormaliser.monthEndMillis(year, month) > now) {
                missing.add(month); // month not finished yet — never partial-fill
                continue;
            }
            DynamicPriceCache.MonthChunk chunk =
                    DynamicPriceCache.load(cacheDir, MARKET_ID, year, month);
            boolean fetchErrored = false;
            if (null == chunk) {
                try {
                    chunk = fetchMonth(year, month);
                } catch (IOException e) {
                    lastFailure = e;
                    chunk = null;
                    fetchErrored = true;
                }
                if (!(null == chunk)) DynamicPriceCache.store(cacheDir, chunk);
            }
            if (null == chunk) {
                // Distinguish "genuinely not covered by any source" (fetchMonth
                // returned null after trying catalog + archive + workbook) — a real
                // missing month — from a transient fetch error (HTTP blip, workbook
                // download failure). A missing month is reported; a transient error
                // must NOT masquerade as a permanent gap, or the worker treats the
                // window as terminally incomplete and never retries.
                if (!fetchErrored) missing.add(month);
                continue;
            }
            sources.add(chunk.source);
            gapFilled += chunk.gapFilled;
            for (int j = 0; j < chunk.utcMillis.length; j++) {
                entries.add(new RateSeries.Entry(chunk.utcMillis[j], chunk.centsPerKwh[j]));
            }
        }
        if (entries.isEmpty()) {
            if (!(null == lastFailure)) throw lastFailure;
            throw new IOException("No SEMOpx data available for the window starting "
                    + startYear + "-" + String.format(java.util.Locale.ROOT, "%02d", startMonth));
        }
        // A month errored (rather than being genuinely absent): surface it so the
        // worker retries the whole window instead of reporting a spurious gap that
        // a later attempt would fill.
        if (!(null == lastFailure)) throw lastFailure;
        String sourceRef = "SEMOpx " + String.join(" + ", sources)
                + "; fetched " + LocalDate.now(clock)
                + "; provided AS IS by SEMOpx, not redistributed";
        return new RateSeries(MARKET_ID, startYear, entries, missing, gapFilled, sourceRef);
    }

    /** Fetch one UTC month from whichever publication covers it; null = not covered. */
    private DynamicPriceCache.MonthChunk fetchMonth(int year, int month) throws IOException {
        // Auction on day D clears delivery from ~22/23:00Z on D, so the month
        // needs the auctions of (first day − 1) .. (last day of month).
        LocalDate firstAuction = LocalDate.of(year, month, 1).minusDays(1);
        LocalDate lastAuction = LocalDate.of(year, month, 1).plusMonths(1).minusDays(1);

        // The catalog only retains ~12 months; prefer it when the month could
        // still be inside the window, otherwise go straight to the archive.
        boolean maybeInCatalog = SeriesNormaliser.monthStartMillis(year, month)
                >= Instant.ofEpochMilli(clock.millis()).atZone(ZoneOffset.UTC)
                        .minusMonths(13).toInstant().toEpochMilli();

        List<SemopxDayResultCsv.DayResult> days;
        String source;
        if (maybeInCatalog) {
            days = fetchDaysFromCatalog(firstAuction, lastAuction);
            source = "EA-001 Market Results";
            if (days.isEmpty()) {
                days = fetchDaysFromBulk(firstAuction, lastAuction);
                source = "DAM-IDM-Market-Results archive";
            }
        } else {
            days = fetchDaysFromBulk(firstAuction, lastAuction);
            source = "DAM-IDM-Market-Results archive";
            if (days.isEmpty()) {
                days = fetchDaysFromCatalog(firstAuction, lastAuction);
                source = "EA-001 Market Results";
            }
        }
        // Both daily feeds say "not covered" (catalog retention passed, archive
        // snapshot ends): the aggregated look-back workbook is the last resort.
        if (days.isEmpty()) {
            days = fetchDaysFromLookback(firstAuction, lastAuction);
            source = "Lookback2 workbook";
        }
        if (days.isEmpty()) return null;

        SeriesNormaliser.MonthSeries series = SeriesNormaliser.assembleMonth(year, month, days);
        if (null == series) return null;
        DynamicPriceCache.MonthChunk chunk = new DynamicPriceCache.MonthChunk();
        chunk.marketId = MARKET_ID;
        chunk.year = year;
        chunk.month = month;
        chunk.source = source;
        chunk.utcMillis = series.utcMillis;
        chunk.centsPerKwh = series.centsPerKwh;
        chunk.gapFilled = series.gapFilled;
        return chunk;
    }

    /** Walk the EA-001 catalog for SEM-DA results with auction dates in [from, to]. */
    List<SemopxDayResultCsv.DayResult> fetchDaysFromCatalog(LocalDate from, LocalDate to)
            throws IOException {
        // One resource per auction date; republications overwrite (name order = publish order).
        TreeMap<String, String> resourceByDate = new TreeMap<>();
        int page = 1;
        int totalPages = 1;
        while (page <= totalPages) {
            String url = catalogBase + "/api/v1/documents/static-reports?DPuG_ID=EA-001"
                    + "&Date=%3E%3D" + from + "&sort_by=Date&order_by=ASC"
                    + "&page_size=200&page=" + page;
            JsonObject body = JsonParser.parseString(httpGetString(url)).getAsJsonObject();
            JsonObject pagination = body.getAsJsonObject("pagination");
            if (!(null == pagination) && pagination.has("totalPages"))
                totalPages = pagination.get("totalPages").getAsInt();
            JsonArray items = body.getAsJsonArray("items");
            if (null == items || items.isEmpty()) break;
            boolean pastEnd = false;
            for (JsonElement el : items) {
                JsonObject item = el.getAsJsonObject();
                String resource = item.has("ResourceName")
                        ? item.get("ResourceName").getAsString() : "";
                if (!resource.startsWith(SEM_DA_PREFIX)) continue;
                String auctionDate = auctionDateOf(resource);
                if (null == auctionDate) continue;
                LocalDate date = parseYyyyMmDd(auctionDate);
                if (date.isAfter(to)) {
                    pastEnd = true;
                    break;
                }
                if (!date.isBefore(from)) {
                    String prev = resourceByDate.get(auctionDate);
                    if (null == prev || resource.compareTo(prev) > 0)
                        resourceByDate.put(auctionDate, resource);
                }
            }
            if (pastEnd) break;
            page++;
        }
        List<SemopxDayResultCsv.DayResult> days = new ArrayList<>(resourceByDate.size());
        for (String resource : resourceByDate.values()) {
            politePause();
            days.add(SemopxDayResultCsv.parse(
                    httpGetString(catalogBase + "/documents/" + resource)));
        }
        return days;
    }

    /** Pull SEM-DA results with auction dates in [from, to] out of the bulk archive. */
    List<SemopxDayResultCsv.DayResult> fetchDaysFromBulk(LocalDate from, LocalDate to)
            throws IOException {
        loadBulkDirectory();
        List<SemopxDayResultCsv.DayResult> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            RangedZipReader.Entry entry = bulkEntries.get(yyyyMmDd(d));
            if (null == entry) continue;
            politePause();
            byte[] csv = RangedZipReader.read(bulkFetcher, entry);
            days.add(SemopxDayResultCsv.parse(
                    new String(csv, java.nio.charset.StandardCharsets.UTF_8)));
        }
        return days;
    }

    /**
     * Slice the look-back workbook's DAM series for delivery days [from, to].
     * The workbook is delivery-stamped (not auction-stamped), so the window is
     * simply the days themselves; {@link SeriesNormaliser#assembleMonth} clips
     * out-of-month periods either way.
     */
    List<SemopxDayResultCsv.DayResult> fetchDaysFromLookback(LocalDate from, LocalDate to)
            throws IOException {
        ensureLookbackLoaded();
        long fromMillis = from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long toMillis = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        return SemopxLookbackXlsx.toDayResults(
                lookbackPrices.subMap(fromMillis, true, toMillis, false));
    }

    /**
     * Load the workbook's DAM series, downloading the ~23 MB file first when it
     * is not already in the cache directory. A cached file that fails to parse
     * is treated as corrupt (truncated download, SEMOpx re-published): deleted
     * and fetched once more. Failures are remembered for this source instance
     * so a year fetch doesn't retry the download for all twelve months.
     */
    private void ensureLookbackLoaded() throws IOException {
        if (!(null == lookbackPrices)) return;
        if (!(null == lookbackFailure)) throw lookbackFailure;
        File file = new File(cacheDir, LOOKBACK_FILE_NAME);
        try {
            if (!file.isFile()) downloadLookback(file);
            try {
                lookbackPrices = SemopxLookbackXlsx.readDamPrices(file);
            } catch (IOException corrupt) {
                if (!file.delete()) throw corrupt;
                downloadLookback(file);
                lookbackPrices = SemopxLookbackXlsx.readDamPrices(file);
            }
        } catch (IOException e) {
            lookbackFailure = e;
            throw e;
        }
    }

    /** Stream the workbook to disk (tmp + rename, mirroring DynamicPriceCache writes). */
    private void downloadLookback(File target) throws IOException {
        File parent = target.getParentFile();
        if (!(null == parent) && !parent.exists() && !parent.mkdirs())
            throw new IOException("Cannot create " + parent);
        File tmp = new File(parent, target.getName() + ".tmp");
        Request request = new Request.Builder().url(lookbackXlsxUrl).build();
        try (Response response = http.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || null == body)
                throw new IOException("HTTP " + response.code() + " for " + lookbackXlsxUrl);
            try (java.io.InputStream in = body.byteStream();
                 java.io.OutputStream out = new java.io.FileOutputStream(tmp)) {
                byte[] buffer = new byte[65536];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw e;
        }
        if (!tmp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("Cannot move " + tmp + " into place");
        }
    }

    private void loadBulkDirectory() throws IOException {
        if (!(null == bulkEntries)) return;
        bulkFetcher = new HttpByteRangeFetcher(bulkZipUrl);
        Map<String, RangedZipReader.Entry> byDate = new HashMap<>();
        for (RangedZipReader.Entry entry : RangedZipReader.centralDirectory(bulkFetcher)) {
            String base = entry.name.substring(entry.name.lastIndexOf('/') + 1);
            if (!base.startsWith(SEM_DA_PREFIX)) continue;
            String auctionDate = auctionDateOf(base);
            if (null == auctionDate) continue;
            RangedZipReader.Entry prev = byDate.get(auctionDate);
            // Duplicated auction days exist (republications) — keep the latest name.
            if (null == prev || base.compareTo(prev.name.substring(prev.name.lastIndexOf('/') + 1)) > 0)
                byDate.put(auctionDate, entry);
        }
        bulkEntries = byDate;
    }

    /** The auction date (yyyyMMdd) encoded in a MarketResult resource name, or null. */
    static String auctionDateOf(String resourceName) {
        // MarketResult_SEM-DA_PWR-MRC-D+1_<yyyyMMddHHmmss>_<yyyyMMddHHmmss>.csv
        String[] bits = resourceName.split("_");
        if (bits.length < 5) return null;
        String stamp = bits[3];
        if (stamp.length() < 8) return null;
        String date = stamp.substring(0, 8);
        for (int i = 0; i < 8; i++) if (!Character.isDigit(date.charAt(i))) return null;
        return date;
    }

    private static LocalDate parseYyyyMmDd(String yyyyMmDd) {
        return LocalDate.of(Integer.parseInt(yyyyMmDd.substring(0, 4)),
                Integer.parseInt(yyyyMmDd.substring(4, 6)),
                Integer.parseInt(yyyyMmDd.substring(6, 8)));
    }

    private static String yyyyMmDd(LocalDate d) {
        return String.format(java.util.Locale.ROOT, "%04d%02d%02d",
                d.getYear(), d.getMonthValue(), d.getDayOfMonth());
    }

    private void politePause() {
        if (politeDelayMs <= 0) return;
        try {
            Thread.sleep(politeDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String httpGetString(String url) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = http.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || null == body)
                throw new IOException("HTTP " + response.code() + " for " + url);
            return body.string();
        }
    }

    /** HTTP Range implementation of the zip fetch seam. */
    private class HttpByteRangeFetcher implements RangedZipReader.ByteRangeFetcher {
        private final String url;
        private long cachedLength = -1;

        HttpByteRangeFetcher(String url) {
            this.url = url;
        }

        @Override
        public long length() throws IOException {
            if (cachedLength >= 0) return cachedLength;
            Request request = new Request.Builder().url(url).head().build();
            try (Response response = http.newCall(request).execute()) {
                if (!response.isSuccessful())
                    throw new IOException("HTTP " + response.code() + " for HEAD " + url);
                String len = response.header("Content-Length");
                if (null == len) throw new IOException("No Content-Length for " + url);
                cachedLength = Long.parseLong(len);
                return cachedLength;
            }
        }

        @Override
        public byte[] fetch(long start, long endInclusive) throws IOException {
            Request request = new Request.Builder().url(url)
                    .header("Range", "bytes=" + start + "-" + endInclusive).build();
            try (Response response = http.newCall(request).execute()) {
                ResponseBody body = response.body();
                if (response.code() != 206 || null == body)
                    throw new IOException("Range request not honoured (HTTP "
                            + response.code() + ") for " + url);
                return body.bytes();
            }
        }
    }
}
