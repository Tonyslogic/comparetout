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

package com.tfcode.comparetout.importers.fusionsolar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonParser;
import com.tfcode.comparetout.importers.fusionsolar.responses.EnergyBalanceResponse;
import com.tfcode.comparetout.importers.fusionsolar.responses.StationListResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.MGF1ParameterSpec;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * The login/endpoint contract was reconstructed from FusionSolarPy (MIT) and
 * the Phase 0 live-portal probe. These tests drive the client against a
 * MockWebServer standing in for both the SSO and data hosts.
 */
public class FusionSolarClientTest {

    private MockWebServer mServer;
    private KeyPair mKeyPair;
    private String mPubKeyPem;

    @Before
    public void setUp() throws Exception {
        mServer = new MockWebServer();
        mServer.start();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        mKeyPair = generator.generateKeyPair();
        // Plain (unbroken) base64 — no CRLF line wrapping to escape into JSON;
        // the client's MIME decoder reads it fine either way.
        mPubKeyPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(mKeyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }

    @After
    public void tearDown() throws Exception {
        mServer.shutdown();
    }

    // ── password encryption ─────────────────────────────────────────────────

    @Test
    public void encryptPasswordIsOaepSha384AndAppendsVersion() throws Exception {
        String encrypted = FusionSolarClient.encryptPassword(mPubKeyPem, "0004", "s3cret!");
        // The version marker is appended verbatim after the base64 ciphertext.
        assertTrue(encrypted.endsWith("0004"));
        String cipherText = encrypted.substring(0, encrypted.length() - "0004".length());

        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-384AndMGF1Padding");
        cipher.init(Cipher.DECRYPT_MODE, mKeyPair.getPrivate(), new OAEPParameterSpec(
                "SHA-384", "MGF1", MGF1ParameterSpec.SHA384, PSource.PSpecified.DEFAULT));
        byte[] clear = cipher.doFinal(Base64.getDecoder().decode(cipherText));
        assertEquals("s3cret!", new String(clear, StandardCharsets.UTF_8));
    }

    @Test
    public void encryptPasswordWithNoVersionHasNoSuffix() throws Exception {
        String encrypted = FusionSolarClient.encryptPassword(mPubKeyPem, null, "pw");
        // Pure base64 — decodes cleanly with nothing trailing.
        Base64.getDecoder().decode(encrypted);
    }

    // ── login error mapping ─────────────────────────────────────────────────

    @Test
    public void badCredentialsErrorCodeMapsToAuth() {
        enqueuePubkey();
        // errorCode 406 = the live portal's bad-credentials vocabulary.
        mServer.enqueue(new MockResponse().setBody(
                "{\"errorCode\":\"406\",\"errorMsg\":\"Login failed.\",\"verifyCodeCreate\":false}"));
        FusionSolarClient client = client();
        assertThrows(FusionSolarAuthException.class, client::login);
    }

    @Test
    public void captchaDemandMapsToCaptchaRequired() {
        enqueuePubkey();
        mServer.enqueue(new MockResponse().setBody(
                "{\"errorCode\":\"1403\",\"errorMsg\":\"verify\",\"verifyCodeCreate\":true}"));
        // The captcha image fetch that the exception carries.
        mServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "image/jpeg").setBody("JPEGBYTES"));
        FusionSolarClient client = client();
        FusionSolarCaptchaRequiredException e = assertThrows(
                FusionSolarCaptchaRequiredException.class, client::login);
        assertNotNull(e.getCaptchaImage());
    }

    // ── the multi-region (470) login path ────────────────────────────────────
    //
    // Live run 2026-07-20: errorCode 470 is SUCCESS — the credentials were
    // accepted and the account belongs to another region, with a single-use
    // CAS ticket riding along in respMultiRegionName. The client originally
    // treated any non-zero errorCode as a rejection, which would have locked
    // out every multi-region account (i.e. most real ones). These tests pin
    // that down.

    /** The array exactly as the portal sent it (host/ticket values scrubbed). */
    private String liveMultiRegionArray(String host) {
        return "[\"-5\","
                + "\"/rest/dp/web/v1/auth/on-sso-credential-ready"
                + "?ticket=ST-1246250-abc&regionName=region004\","
                + "\"" + host + "&&TGTX--F1049035165-1246249-def\"]";
    }

    @Test
    public void multiRegionHopIsParsedByShapeNotIndex() {
        FusionSolarClient client = client();
        // Element order is contracted nowhere, so the parse must not rely on
        // it. Both orderings must yield the same hop.
        String host = mServer.url("/").host();
        FusionSolarClient.MultiRegionHop forward = client.parseMultiRegion(
                JsonParser.parseString(liveMultiRegionArray(host)));
        assertNotNull(forward);
        assertEquals(host, forward.host);
        assertTrue(forward.path.startsWith("/rest/dp/web/v1/auth/on-sso-credential-ready"));
        assertTrue(forward.path.contains("ticket=ST-1246250-abc"));
        // The TGT is stripped — it is not ours to use.
        org.junit.Assert.assertFalse(forward.host.contains("TGT"));

        FusionSolarClient.MultiRegionHop reversed = client.parseMultiRegion(
                JsonParser.parseString("[\"" + host + "&&TGTX--x\","
                        + "\"/rest/dp/web/v1/auth/on-sso-credential-ready?ticket=T1\",\"-5\"]"));
        assertNotNull(reversed);
        assertEquals(forward.host, reversed.host);
    }

    @Test
    public void multiRegionHopRejectsUntrustedHostAndJunk() {
        FusionSolarClient client = client();
        // A ticket is a session credential: never hand it to a host outside
        // Huawei's domain, however well-formed the rest of the array is.
        org.junit.Assert.assertNull(client.parseMultiRegion(
                JsonParser.parseString(
                        "[\"-5\",\"/x?ticket=T\",\"evil.example.com&&TGT\"]")));
        // No ticket path → no hop.
        org.junit.Assert.assertNull(client.parseMultiRegion(
                JsonParser.parseString(
                        "[\"-5\",\"" + mServer.url("/").host() + "&&TGT\"]")));
        // Absent / wrong type → no hop, no crash.
        org.junit.Assert.assertNull(client.parseMultiRegion(null));
        org.junit.Assert.assertNull(client.parseMultiRegion(
                JsonParser.parseString("\"nope\"")));
    }

    @Test
    public void errorCode470IsSuccessAndConsumesTheRegionTicket() throws Exception {
        enqueuePubkey();
        // validateUser answers 470 + the region ticket. This is a SUCCESS.
        mServer.enqueue(new MockResponse().setBody(
                "{\"errorCode\":\"470\",\"errorMsg\":null,\"verifyCodeCreate\":false,"
                        + "\"respMultiRegionName\":"
                        + liveMultiRegionArray(mServer.url("/").host()) + "}"));
        // The ticket GET that mints the session.
        mServer.enqueue(new MockResponse().setBody("{}"));
        mServer.enqueue(new MockResponse().setBody("{\"code\":0,\"payload\":\"R-470\"}"));
        mServer.enqueue(new MockResponse().setBody(
                "{\"success\":true,\"failCode\":0,\"data\":{\"list\":["
                        + "{\"dn\":\"NE=1\",\"name\":\"Home\"}]}}"));

        FusionSolarClient client = client();
        // Must not throw: 470 is not a credential rejection.
        List<StationListResponse.Station> stations = client.getStationList();
        assertEquals(1, stations.size());

        mServer.takeRequest(); // pubkey
        mServer.takeRequest(); // validateUser
        // Session establishment goes to the ticket path, NOT the /unisess
        // oracle guess (which live probing only ever saw 404).
        RecordedRequest ticket = mServer.takeRequest();
        assertTrue("must consume the CAS ticket, got " + ticket.getPath(),
                ticket.getPath().startsWith("/rest/dp/web/v1/auth/on-sso-credential-ready"));
        assertTrue(ticket.getPath().contains("ticket=ST-1246250-abc"));

        RecordedRequest keepAlive = mServer.takeRequest();
        assertTrue(keepAlive.getPath().contains("/rest/dpcloud/auth/v1/keep-alive"));
        RecordedRequest stationList = mServer.takeRequest();
        assertEquals("R-470", stationList.getHeader("roarand"));
    }

    @Test
    public void errorCode470WithoutAParseableHopStillFails() {
        enqueuePubkey();
        // 470 but nothing usable to act on — we must not silently sail on as
        // if logged in; that would surface later as a confusing data error.
        mServer.enqueue(new MockResponse().setBody(
                "{\"errorCode\":\"470\",\"respMultiRegionName\":[\"-5\"]}"));
        FusionSolarClient client = client();
        assertThrows(FusionSolarAuthException.class, client::login);
    }

    // ── happy path ───────────────────────────────────────────────────────────

    @Test
    public void loginThenStationListThenEnergyBalance() throws Exception {
        enqueuePubkey();
        // validateUser success (no errorCode).
        mServer.enqueue(new MockResponse().setBody(
                "{\"errorCode\":null,\"redirectURL\":\"/unisess/v1/auth?service=/x\"}"));
        // auth redirect landing.
        mServer.enqueue(new MockResponse().setBody("{}"));
        // keep-alive with the roarand payload.
        mServer.enqueue(new MockResponse().setBody(
                "{\"code\":0,\"payload\":\"ROARAND-TOKEN\"}"));
        // station-list.
        mServer.enqueue(new MockResponse().setBody(
                "{\"success\":true,\"failCode\":0,\"data\":{\"list\":["
                        + "{\"dn\":\"NE=33554678\",\"name\":\"Home\",\"installedCapacity\":6.6}]}}"));
        // energy-balance.
        mServer.enqueue(new MockResponse().setBody(
                "{\"success\":true,\"failCode\":0,\"data\":{"
                        + "\"xAxis\":[\"00:00\",\"00:05\"],"
                        + "\"productPower\":[\"1.0\",\"--\"],"
                        + "\"totalProductPower\":\"1.0\"}}"));

        FusionSolarClient client = client();
        List<StationListResponse.Station> stations = client.getStationList();
        assertEquals(1, stations.size());
        assertEquals("NE=33554678", stations.get(0).dn);
        assertEquals(Double.valueOf(6.6), stations.get(0).installedCapacity);

        EnergyBalanceResponse balance =
                client.getEnergyBalance("NE=33554678", LocalDate.of(2026, 1, 15),
                        ZoneId.of("Europe/Dublin"));
        assertEquals(2, balance.xAxis().size());
        assertEquals(Double.valueOf(1.0), balance.scalar("totalProductPower"));
        assertEquals(Double.valueOf(1.0), balance.series("productPower")[0]);
        // "--" → null (absent), not zero.
        org.junit.Assert.assertNull(balance.series("productPower")[1]);

        // Login sends the encrypted password to validateUser, never the clear one.
        mServer.takeRequest(); // pubkey
        RecordedRequest validate = mServer.takeRequest();
        assertTrue(validate.getPath().startsWith("/unisso/v3/validateUser.action"));
        String body = validate.getBody().readUtf8();
        assertTrue(body.contains("\"username\":\"tester\""));
        org.junit.Assert.assertFalse("clear password must not be sent",
                body.contains("\"password\":\"portalpass\""));

        // Data calls carry the roarand header captured from keep-alive.
        mServer.takeRequest(); // auth redirect
        mServer.takeRequest(); // keep-alive
        RecordedRequest stationList = mServer.takeRequest();
        assertEquals("ROARAND-TOKEN", stationList.getHeader("roarand"));
    }

    @Test
    public void deadSessionCodeTriggersOneReLogin() throws Exception {
        // First login.
        enqueuePubkey();
        mServer.enqueue(new MockResponse().setBody("{\"errorCode\":null}"));
        mServer.enqueue(new MockResponse().setBody("{}"));
        mServer.enqueue(new MockResponse().setBody("{\"code\":0,\"payload\":\"R1\"}"));
        // station-list answers "Bad session" (code 1201) → re-login.
        mServer.enqueue(new MockResponse().setBody(
                "{\"code\":1201,\"message\":\"Bad session.\",\"payload\":null}"));
        // Re-login sequence.
        enqueuePubkey();
        mServer.enqueue(new MockResponse().setBody("{\"errorCode\":null}"));
        mServer.enqueue(new MockResponse().setBody("{}"));
        mServer.enqueue(new MockResponse().setBody("{\"code\":0,\"payload\":\"R2\"}"));
        // station-list retried, now good.
        mServer.enqueue(new MockResponse().setBody(
                "{\"success\":true,\"failCode\":0,\"data\":{\"list\":[]}}"));

        FusionSolarClient client = client();
        List<StationListResponse.Station> stations = client.getStationList();
        assertTrue(stations.isEmpty());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void enqueuePubkey() {
        mServer.enqueue(new MockResponse().setBody(
                "{\"pubKey\":\"" + mPubKeyPem.replace("\n", "\\n") + "\","
                        + "\"version\":\"0004\",\"timeStamp\":\"1700000000000\","
                        + "\"enableEncrypt\":true}"));
    }

    private FusionSolarClient client() {
        String base = mServer.url("/").toString();
        String trimmed = base.substring(0, base.length() - 1);
        // Both hosts point at the one mock server.
        return new FusionSolarClient("tester", "portalpass", trimmed, trimmed);
    }
}
