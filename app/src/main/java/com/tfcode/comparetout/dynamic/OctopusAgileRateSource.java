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

import com.tfcode.comparetout.importers.octopus.OctopusException;
import com.tfcode.comparetout.importers.octopus.OctopusRestClient;
import com.tfcode.comparetout.importers.octopus.responses.ProductDetailResponse;
import com.tfcode.comparetout.importers.octopus.responses.ProductsResponse;
import com.tfcode.comparetout.importers.octopus.responses.RatesResponse;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Octopus Agile as a {@link HistoricalRateSource} (plan Phase 8 — GB). The
 * public tariff API needs no credentials and publishes each Agile product's
 * half-hourly unit rates for its whole life, so a historical year assembles
 * from the products that were live then (the original AGILE-18-02-21 carries
 * the deep history; the relaunches carry the recent years).
 *
 * <p>Prices are per GSP region (A..P), so the market id embeds the region:
 * {@code GB-AGILE-C}. Unlike SEMOpx, the published values are already the
 * retail price in pence/kWh inc VAT — plan terms are ×1 +0 and the series is
 * stored in the app's minor-units convention directly.
 *
 * <p>Month assembly reuses {@link SeriesNormaliser} by presenting the month's
 * points as one 30-minute {@link SemopxDayResultCsv.DayResult} with values
 * ×10 — the normaliser's EUR/MWh→c/kWh ÷10 lands back on pence, so the grid /
 * republication / gap-fill rules stay identical across markets.
 */
public final class OctopusAgileRateSource implements HistoricalRateSource {

    public static final String MARKET_PREFIX = "GB-AGILE-";
    private static final long POLITE_DELAY_MS = 300;

    /** The slice of the Octopus API this source needs — canned in tests. */
    interface AgileApi {
        List<ProductsResponse.Product> products() throws IOException;

        ProductDetailResponse productDetail(String code) throws IOException;

        List<RatesResponse.Rate> unitRates(String productCode, String tariffCode,
                                           String fromIso, String toIso) throws IOException;
    }

    private final File cacheDir;
    private final String region;    // "A".."P"
    private final String marketId;  // "GB-AGILE-<region>"
    private final AgileApi api;
    private final Clock clock;
    private final long politeDelayMs;

    /** Agile import products, newest available_from first; loaded on first use. */
    private List<ProductsResponse.Product> agileProducts;
    /** Per product: the region's tariff code, or "" when the region has none. */
    private final Map<String, String> tariffCodes = new HashMap<>();

    public OctopusAgileRateSource(Context context, String region) {
        this(DynamicPriceCache.cacheDir(context), region,
                new RestClientApi(), Clock.systemUTC(), POLITE_DELAY_MS);
    }

    OctopusAgileRateSource(File cacheDir, String region, AgileApi api,
                           Clock clock, long politeDelayMs) {
        this.cacheDir = cacheDir;
        this.region = region.toUpperCase();
        this.marketId = MARKET_PREFIX + this.region;
        this.api = api;
        this.clock = clock;
        this.politeDelayMs = politeDelayMs;
    }

    @Override
    public String marketId() {
        return marketId;
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
                    DynamicPriceCache.load(cacheDir, marketId, year, month);
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
                // Genuinely-not-covered month (report) vs a transient fetch error
                // (must not masquerade as a permanent gap — see SemopxRateSource).
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
            throw new IOException("No Octopus Agile data available for the window starting "
                    + startYear + "-" + String.format(java.util.Locale.ROOT, "%02d", startMonth)
                    + " in region " + region);
        }
        // A month errored rather than being genuinely absent — surface it so the
        // worker retries the whole window instead of reporting a spurious gap.
        if (!(null == lastFailure)) throw lastFailure;
        String sourceRef = "Octopus Energy public tariff API (" + String.join(" + ", sources)
                + "), GSP region " + region + "; fetched " + LocalDate.now(clock)
                + "; prices are retail inc-VAT pence/kWh";
        return new RateSeries(marketId, startYear, entries, missing, gapFilled, sourceRef);
    }

    /** Fetch one UTC month from the newest product that has data for it; null = uncovered. */
    private DynamicPriceCache.MonthChunk fetchMonth(int year, int month) throws IOException {
        long monthStart = SeriesNormaliser.monthStartMillis(year, month);
        long monthEnd = SeriesNormaliser.monthEndMillis(year, month);
        String fromIso = Instant.ofEpochMilli(monthStart).toString();
        String toIso = Instant.ofEpochMilli(monthEnd).toString();

        for (ProductsResponse.Product product : agileProducts()) {
            // Cheap availability pre-filter — a product can't publish rates
            // for months before it existed. (No upper bound: closed products
            // keep publishing until switched off, which only the rates
            // request itself can reveal.)
            Long from = parseInstantMillis(product.availableFrom);
            if (!(null == from) && from > monthEnd) continue;

            String tariffCode = tariffCodeFor(product.code);
            if (tariffCode.isEmpty()) continue;
            politePause();
            List<RatesResponse.Rate> rates =
                    api.unitRates(product.code, tariffCode, fromIso, toIso);

            TreeMap<Long, Double> byMillis = new TreeMap<>();
            for (RatesResponse.Rate rate : rates) {
                if (!(null == rate.paymentMethod)
                        && !"DIRECT_DEBIT".equalsIgnoreCase(rate.paymentMethod)) continue;
                Long millis = parseInstantMillis(rate.validFrom);
                if (null == millis || millis < monthStart || millis >= monthEnd) continue;
                byMillis.put(millis, rate.valueIncVat);
            }
            if (byMillis.isEmpty()) continue;

            long[] millis = new long[byMillis.size()];
            double[] tensOfPence = new double[byMillis.size()];
            int i = 0;
            for (Map.Entry<Long, Double> point : byMillis.entrySet()) {
                millis[i] = point.getKey();
                // ×10 so the normaliser's EUR/MWh→minor-units ÷10 lands back
                // on the API's pence/kWh.
                tensOfPence[i] = point.getValue() * 10d;
                i++;
            }
            SeriesNormaliser.MonthSeries series = SeriesNormaliser.assembleMonth(year, month,
                    java.util.Collections.singletonList(
                            new SemopxDayResultCsv.DayResult(30, millis, tensOfPence)));
            if (null == series) continue; // too sparse from this product — try an older one

            DynamicPriceCache.MonthChunk chunk = new DynamicPriceCache.MonthChunk();
            chunk.marketId = marketId;
            chunk.year = year;
            chunk.month = month;
            chunk.source = product.code;
            chunk.utcMillis = series.utcMillis;
            chunk.centsPerKwh = series.centsPerKwh;
            chunk.gapFilled = series.gapFilled;
            return chunk;
        }
        return null;
    }

    /** All Agile import products, newest first, loaded once. */
    private List<ProductsResponse.Product> agileProducts() throws IOException {
        if (!(null == agileProducts)) return agileProducts;
        List<ProductsResponse.Product> candidates = new ArrayList<>();
        for (ProductsResponse.Product product : api.products()) {
            if (null == product.code || !product.code.startsWith("AGILE")) continue;
            if (!"IMPORT".equalsIgnoreCase(product.direction)) continue;
            if (product.isPrepay || product.isBusiness || product.isRestricted) continue;
            candidates.add(product);
        }
        candidates.sort(Comparator.comparing(
                (ProductsResponse.Product p) -> {
                    Long from = parseInstantMillis(p.availableFrom);
                    return (null == from) ? Long.MIN_VALUE : from;
                }).reversed());
        agileProducts = candidates;
        return agileProducts;
    }

    /** The region's single-register tariff code for a product ("" = not offered there). */
    private String tariffCodeFor(String productCode) throws IOException {
        String cached = tariffCodes.get(productCode);
        if (!(null == cached)) return cached;
        String code = "";
        ProductDetailResponse detail = api.productDetail(productCode);
        Map<String, ProductDetailResponse.Tariff> regional =
                (null == detail.singleRegisterElectricityTariffs) ? null
                        : detail.singleRegisterElectricityTariffs.get("_" + region);
        if (!(null == regional)) {
            ProductDetailResponse.Tariff tariff = regional.get("direct_debit_monthly");
            if (null == tariff && !regional.isEmpty()) tariff = regional.values().iterator().next();
            if (!(null == tariff) && !(null == tariff.code)) code = tariff.code;
        }
        tariffCodes.put(productCode, code);
        return code;
    }

    /** Lenient ISO-8601 → epoch millis; null when absent or unparseable. */
    private static Long parseInstantMillis(String iso) {
        if (null == iso || iso.isEmpty()) return null;
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (Exception e) {
            try {
                return OffsetDateTime.parse(iso).toInstant().toEpochMilli();
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private void politePause() {
        if (politeDelayMs <= 0) return;
        try {
            Thread.sleep(politeDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Production {@link AgileApi} over the shared public REST client. */
    private static final class RestClientApi implements AgileApi {
        private final OctopusRestClient client = new OctopusRestClient();

        @Override
        public List<ProductsResponse.Product> products() throws IOException {
            try {
                return client.getProducts();
            } catch (OctopusException e) {
                throw new IOException(e.getMessage(), e);
            }
        }

        @Override
        public ProductDetailResponse productDetail(String code) throws IOException {
            try {
                return client.getProductDetail(code);
            } catch (OctopusException e) {
                throw new IOException(e.getMessage(), e);
            }
        }

        @Override
        public List<RatesResponse.Rate> unitRates(String productCode, String tariffCode,
                                                  String fromIso, String toIso) throws IOException {
            try {
                return client.getStandardUnitRates(productCode, tariffCode, fromIso, toIso);
            } catch (OctopusException e) {
                throw new IOException(e.getMessage(), e);
            }
        }
    }
}
