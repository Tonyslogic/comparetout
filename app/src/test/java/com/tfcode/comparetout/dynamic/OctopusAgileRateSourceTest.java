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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.tfcode.comparetout.importers.octopus.responses.ProductDetailResponse;
import com.tfcode.comparetout.importers.octopus.responses.ProductsResponse;
import com.tfcode.comparetout.importers.octopus.responses.RatesResponse;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The GB Agile source against a canned API: pence units survive the shared
 * normaliser, the cache short-circuits the network, future months stay
 * missing, and deep history falls back to the closed original product.
 */
public class OctopusAgileRateSourceTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** Canned {@link OctopusAgileRateSource.AgileApi}. Open for per-test overrides. */
    private static class CannedApi implements OctopusAgileRateSource.AgileApi {
        interface RatesFn {
            List<RatesResponse.Rate> rates(String productCode, String fromIso, String toIso);
        }

        final List<ProductsResponse.Product> products = new ArrayList<>();
        final Map<String, ProductDetailResponse> details = new HashMap<>();
        final List<String> rateCalls = new ArrayList<>();
        RatesFn ratesFn = (p, f, t) -> Collections.emptyList();

        @Override
        public List<ProductsResponse.Product> products() {
            return products;
        }

        @Override
        public ProductDetailResponse productDetail(String code) throws IOException {
            ProductDetailResponse detail = details.get(code);
            if (null == detail) throw new IOException("no detail for " + code);
            return detail;
        }

        @Override
        public List<RatesResponse.Rate> unitRates(String productCode, String tariffCode,
                                                  String fromIso, String toIso) {
            rateCalls.add(productCode);
            return ratesFn.rates(productCode, fromIso, toIso);
        }
    }

    private static ProductsResponse.Product product(String code, String from, String to) {
        ProductsResponse.Product p = new ProductsResponse.Product();
        p.code = code;
        p.direction = "IMPORT";
        p.availableFrom = from;
        p.availableTo = to;
        return p;
    }

    private static ProductDetailResponse detailWithLondonTariff(String tariffCode) {
        ProductDetailResponse detail = new ProductDetailResponse();
        ProductDetailResponse.Tariff tariff = new ProductDetailResponse.Tariff();
        tariff.code = tariffCode;
        Map<String, ProductDetailResponse.Tariff> byPayment = new HashMap<>();
        byPayment.put("direct_debit_monthly", tariff);
        detail.singleRegisterElectricityTariffs = new HashMap<>();
        detail.singleRegisterElectricityTariffs.put("_C", byPayment);
        return detail;
    }

    /** A full half-hourly month at a constant pence/kWh. */
    private static List<RatesResponse.Rate> fullMonth(String fromIso, String toIso, double pence) {
        long start = Instant.parse(fromIso).toEpochMilli();
        long end = Instant.parse(toIso).toEpochMilli();
        List<RatesResponse.Rate> out = new ArrayList<>();
        for (long t = start; t < end; t += 1_800_000L) {
            RatesResponse.Rate rate = new RatesResponse.Rate();
            rate.validFrom = Instant.ofEpochMilli(t).toString();
            rate.valueIncVat = pence;
            out.add(rate);
        }
        return out;
    }

    private static Clock fixed(String isoInstant) {
        return Clock.fixed(Instant.parse(isoInstant), ZoneOffset.UTC);
    }

    @Test
    public void assemblesAYearInPenceFromTheCurrentProduct() throws Exception {
        CannedApi api = new CannedApi();
        api.products.add(product("AGILE-24-10-01", "2024-10-01T00:00:00Z", null));
        api.details.put("AGILE-24-10-01", detailWithLondonTariff("E-1R-AGILE-24-10-01-C"));
        api.ratesFn = (p, from, to) -> fullMonth(from, to, 20.5);

        OctopusAgileRateSource source = new OctopusAgileRateSource(
                tmp.getRoot(), "C", api, fixed("2026-07-10T00:00:00Z"), 0);
        RateSeries series = source.fetch(2025);

        assertEquals("GB-AGILE-C", source.marketId());
        assertTrue(series.isComplete());
        assertEquals(365 * 48, series.getEntries().size());
        // Values survive the shared normaliser in pence (the ×10 / ÷10 pairing).
        assertEquals(20.5, series.getEntries().get(0).importCentsPerKwh, 1e-9);
        assertTrue(series.getSourceRef().contains("AGILE-24-10-01"));
        assertTrue(series.getSourceRef().contains("region C"));
    }

    @Test
    public void cachedMonthsNeedNoApiAtAll() throws Exception {
        for (int month = 1; month <= 12; month++) {
            DynamicPriceCache.MonthChunk chunk = new DynamicPriceCache.MonthChunk();
            chunk.marketId = "GB-AGILE-C";
            chunk.year = 2025;
            chunk.month = month;
            chunk.source = "AGILE-24-10-01";
            chunk.utcMillis = new long[]{SeriesNormaliser.monthStartMillis(2025, month)};
            chunk.centsPerKwh = new double[]{18.0};
            chunk.gapFilled = 0;
            DynamicPriceCache.store(tmp.getRoot(), chunk);
        }
        CannedApi api = new CannedApi() {
            @Override
            public List<ProductsResponse.Product> products() {
                fail("cache-first fetch must not touch the API");
                return null;
            }
        };
        OctopusAgileRateSource source = new OctopusAgileRateSource(
                tmp.getRoot(), "C", api, fixed("2026-07-10T00:00:00Z"), 0);
        RateSeries series = source.fetch(2025);
        assertTrue(series.isComplete());
        assertEquals(12, series.getEntries().size());
        assertTrue(api.rateCalls.isEmpty());
    }

    @Test
    public void unfinishedMonthsStayMissingWithoutFetching() throws Exception {
        CannedApi api = new CannedApi();
        api.products.add(product("AGILE-24-10-01", "2024-10-01T00:00:00Z", null));
        api.details.put("AGILE-24-10-01", detailWithLondonTariff("E-1R-AGILE-24-10-01-C"));
        api.ratesFn = (p, from, to) -> fullMonth(from, to, 20.5);

        OctopusAgileRateSource source = new OctopusAgileRateSource(
                tmp.getRoot(), "C", api, fixed("2025-07-02T00:00:00Z"), 0);
        RateSeries series = source.fetch(2025);

        assertFalse(series.isComplete());
        // June is the last finished month on 2025-07-02... July onwards missing.
        for (int month = 7; month <= 12; month++) {
            assertTrue("month " + month + " must be missing",
                    series.getMissingMonths().contains(month));
        }
        assertFalse(series.getMissingMonths().contains(6));
    }

    @Test
    public void deepHistoryFallsBackToTheClosedOriginalProduct() throws Exception {
        CannedApi api = new CannedApi();
        // Newest first after sorting; the 2024 product can't cover 2019 and
        // must be pre-filtered by its available_from without a rates call.
        api.products.add(product("AGILE-18-02-21", "2018-02-21T00:00:00Z", "2023-12-01T00:00:00Z"));
        api.products.add(product("AGILE-24-10-01", "2024-10-01T00:00:00Z", null));
        api.details.put("AGILE-18-02-21", detailWithLondonTariff("E-1R-AGILE-18-02-21-C"));
        api.details.put("AGILE-24-10-01", detailWithLondonTariff("E-1R-AGILE-24-10-01-C"));
        api.ratesFn = (p, from, to) ->
                "AGILE-18-02-21".equals(p) ? fullMonth(from, to, 12.3) : Collections.emptyList();

        OctopusAgileRateSource source = new OctopusAgileRateSource(
                tmp.getRoot(), "C", api, fixed("2026-07-10T00:00:00Z"), 0);
        RateSeries series = source.fetch(2019);

        assertTrue(series.isComplete());
        assertEquals(12.3, series.getEntries().get(0).importCentsPerKwh, 1e-9);
        for (String called : api.rateCalls) {
            assertEquals("AGILE-18-02-21", called);
        }
        assertTrue(series.getSourceRef().contains("AGILE-18-02-21"));
    }
}
