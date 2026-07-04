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

package com.tfcode.comparetout.importers.octopus;

import com.google.gson.Gson;
import com.tfcode.comparetout.importers.octopus.responses.AccountResponse;
import com.tfcode.comparetout.importers.octopus.responses.ConsumptionResponse;
import com.tfcode.comparetout.importers.octopus.responses.GridSupplyPointsResponse;
import com.tfcode.comparetout.importers.octopus.responses.ProductDetailResponse;
import com.tfcode.comparetout.importers.octopus.responses.ProductsResponse;
import com.tfcode.comparetout.importers.octopus.responses.RatesResponse;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Thin client for the Octopus Energy REST API (https://developer.octopus.energy/).
 *
 * Auth is HTTP Basic with the customer's API key as the username and a blank
 * password; only /accounts/ and /consumption/ need it. The /products/,
 * tariff-rates and grid-supply-points endpoints are public, so the client can
 * be constructed with a null key for the Supplier Plans (no-login) flow.
 *
 * All calls are synchronous — callers are workers/background threads, matching
 * the OpenAlphaESSClient / ESBNHDFClient precedent.
 */
public class OctopusRestClient {

    private static final String BASE_URL = "https://api.octopus.energy/v1";
    /** Consumption pages: a year of half-hours is ~17.5k rows; 5000/page keeps
     *  the request count low without oversized payloads. */
    private static final int CONSUMPTION_PAGE_SIZE = 5000;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 5000;

    private final OkHttpClient mClient;
    private final Gson mGson = new Gson();
    private final String mAuthHeader; // null => public-only client

    /** Public-endpoint client (products / rates / grid-supply-points). */
    public OctopusRestClient() {
        this(null);
    }

    /** @param apiKey the customer's API key, or null for public endpoints only. */
    public OctopusRestClient(String apiKey) {
        mAuthHeader = (null == apiKey || apiKey.isEmpty())
                ? null : Credentials.basic(apiKey, "");
        mClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    // ── account / consumption (authenticated) ──────────────────────────────

    /** Validates the key and returns the account topology (MPANs, serials, agreements). */
    public AccountResponse getAccount(String accountNumber) throws OctopusException {
        if (null == mAuthHeader) throw new OctopusException("An API key is required for account access");
        String url = BASE_URL + "/accounts/" + accountNumber + "/";
        return fetch(url, AccountResponse.class, true);
    }

    /**
     * One page of half-hourly consumption, oldest first. Pass the previous
     * page's {@code next} URL to continue, or null for the first page.
     * {@code periodFrom}/{@code periodTo} are ISO-8601 (e.g. "2026-06-01T00:00:00Z").
     */
    public ConsumptionResponse getConsumptionPage(String mpan, String serial,
                                                  String periodFrom, String periodTo,
                                                  String nextUrl) throws OctopusException {
        if (null == mAuthHeader) throw new OctopusException("An API key is required for consumption access");
        String url;
        if (null != nextUrl) {
            url = nextUrl;
        } else {
            HttpUrl.Builder b = HttpUrl.get(BASE_URL
                            + "/electricity-meter-points/" + mpan
                            + "/meters/" + serial + "/consumption/").newBuilder()
                    .addQueryParameter("order_by", "period")
                    .addQueryParameter("page_size", String.valueOf(CONSUMPTION_PAGE_SIZE));
            if (null != periodFrom) b.addQueryParameter("period_from", periodFrom);
            if (null != periodTo) b.addQueryParameter("period_to", periodTo);
            url = b.build().toString();
        }
        return fetch(url, ConsumptionResponse.class, true);
    }

    // ── products / tariffs (public) ─────────────────────────────────────────

    /** All products across pagination. */
    public List<ProductsResponse.Product> getProducts() throws OctopusException {
        List<ProductsResponse.Product> all = new ArrayList<>();
        String url = BASE_URL + "/products/";
        while (null != url) {
            ProductsResponse page = fetch(url, ProductsResponse.class, false);
            if (null != page.results) all.addAll(page.results);
            url = page.next;
        }
        return all;
    }

    public ProductDetailResponse getProductDetail(String productCode) throws OctopusException {
        return fetch(BASE_URL + "/products/" + productCode + "/", ProductDetailResponse.class, false);
    }

    /**
     * Unit rates for a tariff over [periodFrom, periodTo] (ISO-8601), across
     * pagination. For time-of-use tariffs a single day yields the daily
     * window pattern; for fixed tariffs one open-ended entry comes back.
     */
    public List<RatesResponse.Rate> getStandardUnitRates(String productCode, String tariffCode,
                                                         String periodFrom, String periodTo)
            throws OctopusException {
        return fetchRates(productCode, tariffCode, "standard-unit-rates", periodFrom, periodTo);
    }

    public List<RatesResponse.Rate> getStandingCharges(String productCode, String tariffCode,
                                                       String periodFrom, String periodTo)
            throws OctopusException {
        return fetchRates(productCode, tariffCode, "standing-charges", periodFrom, periodTo);
    }

    public List<RatesResponse.Rate> getDayUnitRates(String productCode, String tariffCode,
                                                    String periodFrom, String periodTo)
            throws OctopusException {
        return fetchRates(productCode, tariffCode, "day-unit-rates", periodFrom, periodTo);
    }

    public List<RatesResponse.Rate> getNightUnitRates(String productCode, String tariffCode,
                                                      String periodFrom, String periodTo)
            throws OctopusException {
        return fetchRates(productCode, tariffCode, "night-unit-rates", periodFrom, periodTo);
    }

    private List<RatesResponse.Rate> fetchRates(String productCode, String tariffCode,
                                                String series, String periodFrom, String periodTo)
            throws OctopusException {
        List<RatesResponse.Rate> all = new ArrayList<>();
        HttpUrl.Builder b = HttpUrl.get(BASE_URL + "/products/" + productCode
                        + "/electricity-tariffs/" + tariffCode + "/" + series + "/").newBuilder();
        if (null != periodFrom) b.addQueryParameter("period_from", periodFrom);
        if (null != periodTo) b.addQueryParameter("period_to", periodTo);
        String url = b.build().toString();
        while (null != url) {
            RatesResponse page = fetch(url, RatesResponse.class, false);
            if (null != page.results) all.addAll(page.results);
            url = page.next;
        }
        return all;
    }

    /** Resolves a UK postcode to its GSP region group id (e.g. "_C"), or null if unknown. */
    public String getGridSupplyPointGroup(String postcode) throws OctopusException {
        HttpUrl url = HttpUrl.get(BASE_URL + "/industry/grid-supply-points/").newBuilder()
                .addQueryParameter("postcode", postcode)
                .build();
        GridSupplyPointsResponse response =
                fetch(url.toString(), GridSupplyPointsResponse.class, false);
        if (null == response.results || response.results.isEmpty()) return null;
        return response.results.get(0).groupId;
    }

    // ── plumbing ────────────────────────────────────────────────────────────

    private <T> T fetch(String url, Class<T> type, boolean authenticated) throws OctopusException {
        IOException lastIo = null;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            Request.Builder rb = new Request.Builder().url(url).get();
            if (authenticated && null != mAuthHeader) rb.header("Authorization", mAuthHeader);
            try (Response response = mClient.newCall(rb.build()).execute()) {
                if (response.code() == 429) {
                    // Rate limited — back off politely and retry.
                    try {
                        Thread.sleep(RETRY_BACKOFF_MS * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new OctopusException("Interrupted while rate-limited");
                    }
                    continue;
                }
                if (response.code() == 401 || response.code() == 403)
                    throw new OctopusException("Octopus rejected the API key (HTTP " + response.code() + ")");
                if (response.code() == 404)
                    throw new OctopusException("Not found: " + url);
                if (!response.isSuccessful())
                    throw new OctopusException("Octopus API error HTTP " + response.code());
                ResponseBody body = response.body();
                if (null == body) throw new OctopusException("Empty response from Octopus API");
                T parsed = mGson.fromJson(body.charStream(), type);
                if (null == parsed) throw new OctopusException("Unparseable response from Octopus API");
                return parsed;
            } catch (UnknownHostException uhe) {
                throw new OctopusException("The network was not available");
            } catch (IOException ioe) {
                lastIo = ioe;
            }
        }
        throw new OctopusException("Octopus API unreachable: "
                + (null == lastIo ? "rate limited" : lastIo.getMessage()));
    }
}
