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

package com.tfcode.comparetout.importers.solis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.tfcode.comparetout.importers.solis.responses.StationDayEnergyResponse;
import com.tfcode.comparetout.importers.solis.responses.StationDayResponse;
import com.tfcode.comparetout.importers.solis.responses.StationListResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Signing vectors were generated with the community Python client's
 * algorithm (hultenvp/soliscloud_api, MIT) as the oracle:
 * base64(md5(compact-json)), HMAC-SHA1 over
 * "POST\nmd5\napplication/json\ndate\npath".
 */
public class SolisCloudClientTest {

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

    // ── signing vectors ─────────────────────────────────────────────────────

    @Test
    public void contentMd5MatchesOracle() {
        assertEquals("U0Xj//qmRi3zoyapfAAuXw==", SolisCloudClient.contentMd5(
                "{\"pageNo\":1,\"pageSize\":100}".getBytes(StandardCharsets.UTF_8)));
        assertEquals("mZFLkyvTelC5g8XnyQrpOw==", SolisCloudClient.contentMd5(
                "{}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void signatureMatchesOracle() {
        String sign = SolisCloudClient.sign(
                "TestSecret123",
                "U0Xj//qmRi3zoyapfAAuXw==",
                "Fri, 26 Jun 2026 09:00:00 GMT",
                "/v1/api/userStationList");
        assertEquals("/79BPgeWccB+RQDicF44a2Owh4o=", sign);
    }

    @Test
    public void gsonCompactFormMatchesSignedBodyShape() {
        // The signature is over the exact bytes sent; this documents that
        // Gson's compact output matches the oracle vector's body string.
        JsonObject body = new JsonObject();
        body.addProperty("pageNo", 1);
        body.addProperty("pageSize", 100);
        assertEquals("{\"pageNo\":1,\"pageSize\":100}", new Gson().toJson(body));
    }

    @Test
    public void gmtDateIsZeroPaddedRfc1123() {
        // Zero-padded day-of-month, GMT — the form the server accepts.
        assertEquals("Thu, 01 Jan 1970 00:00:00 GMT", SolisCloudClient.gmtDate(new Date(0L)));
    }

    // ── envelope error mapping ──────────────────────────────────────────────

    @Test
    public void envelopeCodeZeroUnwrapsData() throws Exception {
        assertEquals(42, SolisCloudClient.parseEnvelope(new Gson(),
                        "{\"success\":true,\"code\":\"0\",\"msg\":\"success\",\"data\":42}", "/p")
                .getAsInt());
    }

    @Test
    public void envelopeNonZeroCodeIsTransient() {
        SolisCloudException e = assertThrows(SolisCloudException.class,
                () -> SolisCloudClient.parseEnvelope(new Gson(),
                        "{\"success\":false,\"code\":\"B0011\",\"msg\":\"boom\",\"data\":null}", "/p"));
        assertTrue(e.getMessage().contains("B0011"));
    }

    @Test
    public void envelopeAuthCodeIsFatal() {
        assertThrows(SolisCloudAuthException.class,
                () -> SolisCloudClient.parseEnvelope(new Gson(),
                        "{\"success\":false,\"code\":\"R0000\",\"msg\":\"no authority\",\"data\":null}", "/p"));
    }

    @Test
    public void malformedEnvelopeIsFatal() {
        assertThrows(SolisCloudAuthException.class,
                () -> SolisCloudClient.parseEnvelope(new Gson(), "<html>gateway</html>", "/p"));
    }

    // ── HTTP status mapping (fatal paths only — they throw without retrying,
    //    so no backoff sleeps slow the test) ─────────────────────────────────

    @Test
    public void http408MapsToClockSkew() {
        mServer.enqueue(new MockResponse().setResponseCode(408));
        SolisCloudClient client = client();
        assertThrows(SolisCloudClockSkewException.class, client::getStationList);
    }

    @Test
    public void http401MapsToAuth() {
        mServer.enqueue(new MockResponse().setResponseCode(401));
        SolisCloudClient client = client();
        assertThrows(SolisCloudAuthException.class, client::getStationList);
    }

    // ── pagination + request shape ──────────────────────────────────────────

    @Test
    public void stationListPaginatesAndPreservesIdPrecision() throws Exception {
        // Page 1: exactly pageSize records forces a second request.
        StringBuilder records = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            if (i > 0) records.append(',');
            records.append("{\"id\":\"12984919194486318").append(String.format("%02d", i))
                    .append("\",\"stationName\":\"S").append(i).append("\"}");
        }
        mServer.enqueue(new MockResponse().setBody(
                "{\"success\":true,\"code\":\"0\",\"msg\":\"success\",\"data\":{\"page\":{"
                        + "\"records\":[" + records + "],\"current\":1,\"pages\":2,\"total\":101}}}"));
        mServer.enqueue(new MockResponse().setBody(
                "{\"success\":true,\"code\":\"0\",\"msg\":\"success\",\"data\":{\"page\":{"
                        + "\"records\":[{\"id\":\"1298491919448631901\",\"stationName\":\"last\","
                        + "\"capacity\":8.2,\"type\":1,\"timeZone\":1.0,\"money\":\"EUR\"}],"
                        + "\"current\":2,\"pages\":2,\"total\":101}}}"));

        SolisCloudClient client = client();
        List<StationListResponse.Station> stations = client.getStationList();

        assertEquals(101, stations.size());
        // 19-digit ids survive exactly (the String-id rule; a double would corrupt them).
        assertEquals("1298491919448631800", stations.get(0).id);
        assertEquals("1298491919448631901", stations.get(100).id);
        assertEquals(Double.valueOf(8.2), stations.get(100).capacity);

        RecordedRequest first = mServer.takeRequest();
        assertEquals("/v1/api/userStationList", first.getPath());
        assertEquals("{\"pageNo\":1,\"pageSize\":100}", first.getBody().readUtf8());
        // The signed Content-Type must reach the wire unmodified (no
        // charset suffix appended by OkHttp).
        assertEquals("application/json", first.getHeader("Content-Type"));
        assertEquals("U0Xj//qmRi3zoyapfAAuXw==", first.getHeader("Content-MD5"));
        String auth = first.getHeader("Authorization");
        assertNotNull(auth);
        assertTrue(auth.startsWith("API testKey:"));

        RecordedRequest second = mServer.takeRequest();
        assertEquals("{\"pageNo\":2,\"pageSize\":100}", second.getBody().readUtf8());
    }

    @Test
    public void stationDayParsesSamples() throws Exception {
        mServer.enqueue(new MockResponse().setBody(
                "{\"success\":true,\"code\":\"0\",\"msg\":\"success\",\"data\":["
                        + "{\"time\":1750921860000,\"timeStr\":\"2025-06-26 07:11:00\","
                        + "\"power\":1.234,\"powerStr\":\"kW\",\"familyLoadPower\":0.5,"
                        + "\"batteryPower\":0.7,\"batteryPowerZheng\":0.7,\"batteryPowerFu\":0,"
                        + "\"psum\":-0.4,\"psumZheng\":0,\"psumFu\":0.4,\"timeZone\":1}]}"));

        SolisCloudClient client = client();
        List<StationDayResponse> samples =
                client.getStationDay("1298491919448631809", "2025-06-26", 1, "EUR");

        assertEquals(1, samples.size());
        assertEquals(Long.valueOf(1750921860000L), samples.get(0).time);
        assertEquals(Double.valueOf(1.234), samples.get(0).power);
        assertEquals(Double.valueOf(0.4), samples.get(0).psumFu);

        RecordedRequest request = mServer.takeRequest();
        assertEquals("/v1/api/stationDay", request.getPath());
        assertEquals("{\"id\":\"1298491919448631809\",\"money\":\"EUR\","
                + "\"time\":\"2025-06-26\",\"timeZone\":1}", request.getBody().readUtf8());
    }

    @Test
    public void energyTotalsKeyedByStationId() throws Exception {
        mServer.enqueue(new MockResponse().setBody(
                "{\"success\":true,\"code\":\"0\",\"msg\":\"success\",\"data\":{\"page\":{"
                        + "\"records\":[{\"id\":\"1298491919448631809\",\"energy\":12.3,"
                        + "\"energyStr\":\"kWh\",\"gridPurchasedEnergy\":4.5,\"gridSellEnergy\":2.1,"
                        + "\"homeLoadEnergy\":10.0,\"batteryChargeEnergy\":3.0,"
                        + "\"batteryDischargeEnergy\":2.5,\"date\":\"2025-06-26\"}],"
                        + "\"current\":1,\"pages\":1,\"total\":1}}}"));

        SolisCloudClient client = client();
        Map<String, StationDayEnergyResponse.Record> totals =
                client.getStationDayEnergyTotals("2025-06-26");

        assertEquals(1, totals.size());
        StationDayEnergyResponse.Record record = totals.get("1298491919448631809");
        assertNotNull(record);
        assertEquals(Double.valueOf(12.3), record.energy);
        assertEquals(Double.valueOf(2.1), record.gridSellEnergy);
    }

    private SolisCloudClient client() {
        String base = mServer.url("/").toString();
        // Strip the trailing slash: paths already start with /v1/api/...
        return new SolisCloudClient("testKey", "TestSecret123",
                base.substring(0, base.length() - 1));
    }
}
