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

package com.tfcode.comparetout.importers.esbn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.tfcode.comparetout.importers.esbn.responses.ESBNAuthException;
import com.tfcode.comparetout.importers.esbn.responses.ESBNVerificationException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * The current (Jan-2025 rework) portal flow, mocked across two hosts:
 * "127.0.0.1:portalPort" stands in for myaccount.esbnetworks.ie and
 * "localhost:loginPort" for login.esbnetworks.ie — deliberately different
 * host names so the cookie-jar host matching is exercised for real (a B2C
 * session cookie must not leak onto portal requests, and the portal auth
 * cookie must ride every portal request).
 */
public class ESBNHDFClientTest {

    private static final String CSRF = "CSRF-TOKEN";
    private static final String TRANS_ID = "StateProperties=TX123";
    private static final String TENANT = "/tenantpath/B2C_1A_signup_signin";
    private static final String POLICY = "B2C_1A_signup_signin";
    private static final String AF_TOKEN = "AF-TOKEN-123";
    private static final String MPRN = "10001234567";

    private MockWebServer mPortal;
    private MockWebServer mLogin;

    @Before
    public void setUp() throws Exception {
        mPortal = new MockWebServer();
        mPortal.start();
        mLogin = new MockWebServer();
        mLogin.start();
    }

    @After
    public void tearDown() throws Exception {
        mPortal.shutdown();
        mLogin.shutdown();
    }

    private String portalBase() {
        return "http://127.0.0.1:" + mPortal.getPort() + "/";
    }

    private String loginBase() {
        return "http://localhost:" + mLogin.getPort();
    }

    private ESBNHDFClient client() {
        return new ESBNHDFClient("user@example.com", "hunter2", portalBase(), loginBase());
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private static final String SETTINGS_PRETTY =
            "var SETTINGS = {\n" +
            "  \"csrf\": \"" + CSRF + "\",\n" +
            "  \"transId\": \"" + TRANS_ID + "\",\n" +
            "  \"hosts\": { \"tenant\": \"" + TENANT + "\", \"policy\": \"" + POLICY + "\" }\n" +
            "};\n";

    private String loginPageHtml() {
        return "<html><head><script>" + SETTINGS_PRETTY + "</script></head><body></body></html>";
    }

    private String autoFormHtml() {
        return "<html><body><form id=\"auto\" method=\"post\" action=\""
                + portalBase() + "signin-oidc\">"
                + "<input type=\"hidden\" name=\"state\" value=\"STATE1\"/>"
                + "<input type=\"hidden\" name=\"client_info\" value=\"CLIENTINFO1\"/>"
                + "<input type=\"hidden\" name=\"code\" value=\"CODE1\"/>"
                + "</form></body></html>";
    }

    private static final String HDF_CSV =
            "MPRN,Meter Serial Number,Read Value,Read Type,Read Date and End Time\n" +
            MPRN + ",MSN001,0.25,Active Import Interval (kW),11-07-2026 23:30\n" +
            MPRN + ",MSN001,0.5,Active Export Interval (kW),11-07-2026 23:30\n" +
            MPRN + ",MSN001,0.75,Active Import Interval (kW),12-07-2026 00:30\n";

    /** Steps 1–3 on the two servers; the portal's own responses vary per test. */
    private void enqueueLoginServerHappyPath() {
        // Step 1 (redirect target): the B2C page with SETTINGS + session cookie.
        mLogin.enqueue(new MockResponse()
                .addHeader("Set-Cookie", "x-ms-cpim-trans=B2CCOOKIE; Path=/")
                .setBody(loginPageHtml()));
        // Step 2: credentials accepted.
        mLogin.enqueue(new MockResponse().setBody("{\"status\":\"200\"}"));
        // Step 3: the no-JS hand-back form.
        mLogin.enqueue(new MockResponse().setBody(autoFormHtml()));
    }

    private void enqueuePortalRedirectToLogin() {
        mPortal.enqueue(new MockResponse()
                .setResponseCode(302)
                .addHeader("Location", loginBase() + "/loginpage"));
    }

    private void enqueuePortalPostLoginHappyPath() {
        // Step 4: signin-oidc lands the portal auth cookie and bounces home.
        mPortal.enqueue(new MockResponse()
                .setResponseCode(302)
                .addHeader("Set-Cookie", ".AspNetCore.Cookies=authcookie; Path=/; HttpOnly")
                .addHeader("Location", "/"));
        mPortal.enqueue(new MockResponse().setBody("redirect landing"));
        // Step 5: explicit welcome GET.
        mPortal.enqueue(new MockResponse().setBody("welcome"));
        // Step 6: anti-forgery token.
        mPortal.enqueue(new MockResponse().setBody("{\"token\":\"" + AF_TOKEN + "\"}"));
    }

    /** Collects processed HDF lines for assertion. */
    private static class Collected {
        final boolean calculated;
        final ESBNImportExportEntry.HDFLineType type;
        final LocalDateTime when;
        final double value;

        Collected(boolean calculated, ESBNImportExportEntry.HDFLineType type,
                  LocalDateTime when, double value) {
            this.calculated = calculated;
            this.type = type;
            this.when = when;
            this.value = value;
        }
    }

    // ── SETTINGS extraction ─────────────────────────────────────────────────

    @Test
    public void settingsExtractionPrettyPrinted() {
        String json = ESBNHDFClient.extractSettingsJson(SETTINGS_PRETTY);
        assertNotNull(json);
        assertTrue(json.contains("\"csrf\": \"" + CSRF + "\""));
        assertTrue(json.trim().endsWith("}"));
    }

    @Test
    public void settingsExtractionMinified() {
        String script = "function f(){}var SETTINGS={\"csrf\":\"" + CSRF
                + "\",\"transId\":\"" + TRANS_ID
                + "\",\"hosts\":{\"tenant\":\"" + TENANT + "\",\"policy\":\"" + POLICY
                + "\"}};var OTHER={\"x\":1};";
        String json = ESBNHDFClient.extractSettingsJson(script);
        assertEquals("{\"csrf\":\"" + CSRF + "\",\"transId\":\"" + TRANS_ID
                + "\",\"hosts\":{\"tenant\":\"" + TENANT + "\",\"policy\":\"" + POLICY
                + "\"}}", json);
    }

    @Test
    public void settingsExtractionAbsentReturnsNull() {
        assertNull(ESBNHDFClient.extractSettingsJson("var OTHER = { \"x\": 1 };"));
    }

    // ── the full login + download sequence ──────────────────────────────────

    @Test
    public void happyPathLoginAndHdfDownload() throws Exception {
        enqueuePortalRedirectToLogin();
        enqueueLoginServerHappyPath();
        enqueuePortalPostLoginHappyPath();
        // Step 7: the HDF itself.
        mPortal.enqueue(new MockResponse().setBody(HDF_CSV));

        ESBNHDFClient client = client();
        client.setSelectedMPRN(MPRN);
        List<Collected> lines = new ArrayList<>();
        client.fetchSmartMeterDataHDF((calc, type, ldt, value) ->
                lines.add(new Collected(calc, type, ldt, value)));

        // Parsed rows arrived intact.
        assertEquals(3, lines.size());
        assertEquals(ESBNImportExportEntry.HDFLineType.IMPORT, lines.get(0).type);
        assertEquals(0.25, lines.get(0).value, 1e-9);
        assertEquals(LocalDateTime.of(2026, 7, 11, 23, 30), lines.get(0).when);
        assertEquals(ESBNImportExportEntry.HDFLineType.EXPORT, lines.get(1).type);

        // Login host, step 2: XHR-style credential POST with the B2C csrf
        // token AND the B2C session cookie set by step 1.
        mLogin.takeRequest(); // step-1 page
        RecordedRequest selfAsserted = mLogin.takeRequest();
        assertTrue(selfAsserted.getPath().startsWith(TENANT + "/SelfAsserted"));
        assertTrue(selfAsserted.getPath().contains("tx=" + TRANS_ID));
        assertEquals(CSRF, selfAsserted.getHeader("x-csrf-token"));
        assertEquals("XMLHttpRequest", selfAsserted.getHeader("X-Requested-With"));
        assertNotNull(selfAsserted.getHeader("Cookie"));
        assertTrue(selfAsserted.getHeader("Cookie").contains("x-ms-cpim-trans=B2CCOOKIE"));
        String loginBody = selfAsserted.getBody().readUtf8();
        assertTrue(loginBody.contains("signInName=user%40example.com"));
        assertTrue(loginBody.contains("request_type=RESPONSE"));

        // Login host, step 3: the confirm GET still carries the session cookie.
        RecordedRequest confirmed = mLogin.takeRequest();
        assertTrue(confirmed.getPath().startsWith(TENANT + "/api/CombinedSigninAndSignup/confirmed"));
        assertTrue(confirmed.getPath().contains("csrf_token=" + CSRF));
        assertNotNull(confirmed.getHeader("Cookie"));
        assertTrue(confirmed.getHeader("Cookie").contains("x-ms-cpim-trans=B2CCOOKIE"));

        // Portal host, step 4: a plain form POST — no B2C csrf header, and the
        // login host's cookie must NOT leak across hosts.
        mPortal.takeRequest(); // initial redirect to login
        RecordedRequest signinOidc = mPortal.takeRequest();
        assertEquals("/signin-oidc", signinOidc.getPath());
        assertNull(signinOidc.getHeader("x-csrf-token"));
        String oidcBody = signinOidc.getBody().readUtf8();
        assertTrue(oidcBody.contains("state=STATE1"));
        assertTrue(oidcBody.contains("client_info=CLIENTINFO1"));
        assertTrue(oidcBody.contains("code=CODE1"));
        String oidcCookies = signinOidc.getHeader("Cookie");
        if (oidcCookies != null) assertTrue(!oidcCookies.contains("x-ms-cpim-trans"));

        mPortal.takeRequest(); // step-4 redirect landing on "/"
        mPortal.takeRequest(); // step-5 welcome

        // Portal host, step 6: /af/t rides on the settled auth cookie.
        RecordedRequest tokenRequest = mPortal.takeRequest();
        assertEquals("/af/t", tokenRequest.getPath());
        assertNotNull(tokenRequest.getHeader("Cookie"));
        assertTrue(tokenRequest.getHeader("Cookie").contains(".AspNetCore.Cookies=authcookie"));

        // Portal host, step 7 — the 400-regression assertions: JSON body,
        // /af/t token echoed as X-Xsrf-Token, auth cookie present.
        RecordedRequest download = mPortal.takeRequest();
        assertEquals("/DataHub/DownloadHdfPeriodic", download.getPath());
        assertEquals(AF_TOKEN, download.getHeader("X-Xsrf-Token"));
        assertTrue(download.getHeader("Content-Type").startsWith("application/json"));
        assertEquals("{\"mprn\":\"" + MPRN + "\",\"searchType\":\"intervalkw\"}",
                download.getBody().readUtf8());
        assertNotNull(download.getHeader("Cookie"));
        assertTrue(download.getHeader("Cookie").contains(".AspNetCore.Cookies=authcookie"));
    }

    // ── verification / auth failure detection ───────────────────────────────

    @Test
    public void step2NonJsonResponseIsVerification() {
        enqueuePortalRedirectToLogin();
        mLogin.enqueue(new MockResponse().setBody(loginPageHtml()));
        // The human-verification interstitial is an HTML page, not the JSON.
        mLogin.enqueue(new MockResponse().setBody("<html>please verify you are human</html>"));

        ESBNHDFClient client = client();
        client.setSelectedMPRN(MPRN);
        assertThrows(ESBNVerificationException.class,
                () -> client.fetchSmartMeterDataHDF((calc, type, ldt, value) -> {}));
    }

    @Test
    public void step2ErrorWithoutMessageIsVerification() {
        enqueuePortalRedirectToLogin();
        mLogin.enqueue(new MockResponse().setBody(loginPageHtml()));
        mLogin.enqueue(new MockResponse().setBody("{\"status\":\"400\"}"));

        ESBNHDFClient client = client();
        client.setSelectedMPRN(MPRN);
        assertThrows(ESBNVerificationException.class,
                () -> client.fetchSmartMeterDataHDF((calc, type, ldt, value) -> {}));
    }

    @Test
    public void step2BadCredentialJsonIsAuthFailure() {
        enqueuePortalRedirectToLogin();
        mLogin.enqueue(new MockResponse().setBody(loginPageHtml()));
        mLogin.enqueue(new MockResponse().setBody(
                "{\"status\":\"409\",\"errorCode\":\"UserError\"," +
                "\"message\":\"Your email or password is incorrect.\"}"));

        ESBNHDFClient client = client();
        client.setSelectedMPRN(MPRN);
        ESBNAuthException e = assertThrows(ESBNAuthException.class,
                () -> client.fetchSmartMeterDataHDF((calc, type, ldt, value) -> {}));
        assertEquals("Your email or password is incorrect.", e.getMessage());
    }

    @Test
    public void step3PageWithoutAutoFormIsVerification() {
        enqueuePortalRedirectToLogin();
        mLogin.enqueue(new MockResponse().setBody(loginPageHtml()));
        mLogin.enqueue(new MockResponse().setBody("{\"status\":\"200\"}"));
        // No form#auto — the step-3 shape of the verification interstitial.
        mLogin.enqueue(new MockResponse().setBody("<html><body>check your device</body></html>"));

        ESBNHDFClient client = client();
        client.setSelectedMPRN(MPRN);
        assertThrows(ESBNVerificationException.class,
                () -> client.fetchSmartMeterDataHDF((calc, type, ldt, value) -> {}));
    }

    // ── HDF parsing (file-import path) ──────────────────────────────────────

    @Test
    public void readEntriesFromFileParsesEveryRowAndReturnsMprn() throws Exception {
        InputStream is = new ByteArrayInputStream(HDF_CSV.getBytes(StandardCharsets.UTF_8));
        List<Collected> lines = new ArrayList<>();
        String mprn = ESBNHDFClient.readEntriesFromFile(is, (calc, type, ldt, value) ->
                lines.add(new Collected(calc, type, ldt, value)));

        assertEquals(MPRN, mprn);
        // File import keeps EVERY row — the trailing-day prune is cloud-only.
        assertEquals(3, lines.size());
        assertEquals(LocalDateTime.of(2026, 7, 12, 0, 30), lines.get(2).when);
    }

    @Test
    public void kwhReadTypeIsFlaggedCalculated() throws Exception {
        String csv = "MPRN,MSN,Read Value,Read Type,Read Date and End Time\n" +
                MPRN + ",MSN001,1.5,Active Import (kWh),11-07-2026 23:30\n";
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        List<Collected> lines = new ArrayList<>();
        ESBNHDFClient.readEntriesFromFile(is, (calc, type, ldt, value) ->
                lines.add(new Collected(calc, type, ldt, value)));
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).calculated);
    }

    @Test
    public void offsetSuffixedTimestampsParse() throws Exception {
        String csv = "MPRN,MSN,Read Value,Read Type,Read Date and End Time\n" +
                MPRN + ",MSN001,0.25,Active Import Interval (kW),11-07-2026 23:30+01:00\n";
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        List<Collected> lines = new ArrayList<>();
        ESBNHDFClient.readEntriesFromFile(is, (calc, type, ldt, value) ->
                lines.add(new Collected(calc, type, ldt, value)));
        assertEquals(LocalDateTime.of(2026, 7, 11, 23, 30), lines.get(0).when);
    }

    // ── trailing-day prune (cloud-fetch ingestion) ──────────────────────────

    @Test
    public void pruneLatestDayDropsExactlyTheLatestDate() {
        Map<LocalDateTime, Double> entries = new HashMap<>();
        entries.put(LocalDateTime.of(2026, 7, 10, 0, 30), 1.0);
        entries.put(LocalDateTime.of(2026, 7, 10, 23, 30), 2.0);
        entries.put(LocalDateTime.of(2026, 7, 11, 0, 30), 3.0);
        // A full-looking trailing day is still dropped — unconditional prune.
        for (int halfHour = 0; halfHour < 48; halfHour++) {
            entries.put(LocalDateTime.of(2026, 7, 12, halfHour / 2, (halfHour % 2) * 30), 4.0);
        }
        ESBNHDFClient.pruneLatestDay(entries);
        assertEquals(3, entries.size());
        assertTrue(entries.containsKey(LocalDateTime.of(2026, 7, 11, 0, 30)));
    }

    @Test
    public void pruneLatestDaySingleDayHdfStoresNothing() {
        Map<LocalDateTime, Double> entries = new HashMap<>();
        entries.put(LocalDateTime.of(2026, 7, 12, 0, 30), 1.0);
        entries.put(LocalDateTime.of(2026, 7, 12, 1, 0), 2.0);
        ESBNHDFClient.pruneLatestDay(entries);
        assertTrue(entries.isEmpty());
    }

    @Test
    public void pruneLatestDayEmptyMapIsNoOp() {
        Map<LocalDateTime, Double> entries = new HashMap<>();
        ESBNHDFClient.pruneLatestDay(entries);
        assertTrue(entries.isEmpty());
    }
}
