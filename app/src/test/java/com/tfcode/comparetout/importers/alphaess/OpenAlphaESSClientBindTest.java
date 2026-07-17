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

package com.tfcode.comparetout.importers.alphaess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.google.gson.Gson;
import com.tfcode.comparetout.importers.alphaess.responses.BindSnResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Bind-SN flow tests (plans/source/alpha.md §1/§5 phase 1): the POST bodies,
 * the signed headers (same SHA-512 scheme the GETs use), and the envelope
 * passthrough the add-inverter UI branches on (6003 already-bound, 6004
 * check-code, in-band verification code shapes).
 */
public class OpenAlphaESSClientBindTest {

    private static final String APP_ID = "alpha_test_app";
    private static final String SECRET = "s3cret";

    private MockWebServer mServer;
    private OpenAlphaESSClient mClient;

    @Before
    public void setUp() throws Exception {
        mServer = new MockWebServer();
        mServer.start();
        mClient = new OpenAlphaESSClient(APP_ID, SECRET, mServer.url("/").toString());
    }

    @After
    public void tearDown() throws Exception {
        mServer.shutdown();
    }

    private static String sha512Hex(String s) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-512")
                .digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    // ── request shape ───────────────────────────────────────────────────
    //
    // The verbs are MIXED, each field-verified against the live server
    // (2026-07-17): it answers HTTP 405 to POST getVerificationCode (the
    // Python client's form) AND to GET bindSn (the Postman collection's
    // form). These tests pin the surviving combination.

    @Test
    public void getVerificationCodeIsASignedQueryGet() throws Exception {
        mServer.enqueue(new MockResponse().setBody(
                "{\"code\":200,\"msg\":\"Success\",\"data\":null}"));
        mClient.getVerificationCode("AL2002321010043", "X4J7");

        RecordedRequest req = mServer.takeRequest();
        assertEquals("GET", req.getMethod());
        assertEquals("/api/getVerificationCode?sysSn=AL2002321010043&checkCode=X4J7",
                req.getPath());
        // Signed exactly like the data GETs: sign = SHA-512(appId + secret + timeStamp).
        String timeStamp = req.getHeader("timeStamp");
        assertNotNull(timeStamp);
        assertEquals(APP_ID, req.getHeader("appId"));
        assertEquals(sha512Hex(APP_ID + SECRET + timeStamp), req.getHeader("sign"));
    }

    @Test
    public void bindSnPostsSnAndCodeBody() throws Exception {
        mServer.enqueue(new MockResponse().setBody(
                "{\"code\":200,\"msg\":\"Success\",\"data\":true}"));
        mClient.bindSn("AL2002321010043", "123456");

        RecordedRequest req = mServer.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/api/bindSn", req.getPath());
        assertEquals("{\"sysSn\":\"AL2002321010043\",\"code\":\"123456\"}",
                req.getBody().readUtf8());
    }

    // ── envelope passthrough ────────────────────────────────────────────

    @Test
    public void nonSuccessEnvelopeCodesComeBackNotThrow() throws Exception {
        mServer.enqueue(new MockResponse().setBody(
                "{\"code\":6003,\"msg\":\"You have bound this SN\",\"data\":null}"));
        BindSnResponse alreadyBound = mClient.bindSn("AL1", "000000");
        assertEquals(6003, alreadyBound.code);

        mServer.enqueue(new MockResponse().setBody(
                "{\"code\":6004,\"msg\":\"CheckCode error\",\"data\":null}"));
        BindSnResponse badCheck = mClient.getVerificationCode("AL1", "WRONG");
        assertEquals(6004, badCheck.code);
    }

    @Test
    public void httpFailureThrows() {
        mServer.enqueue(new MockResponse().setResponseCode(500));
        assertThrows(AlphaESSException.class,
                () -> mClient.getVerificationCode("AL1", "X4J7"));
    }

    // ── in-band verification code shapes (payload undocumented — cover
    //    string, number, wrapped-object, and the not-a-code cases) ────────

    private static BindSnResponse parse(String json) {
        return new Gson().fromJson(json, BindSnResponse.class);
    }

    @Test
    public void inBandCodeAsBareString() {
        assertEquals("482913", parse(
                "{\"code\":200,\"msg\":\"Success\",\"data\":\"482913\"}").inBandCode());
    }

    @Test
    public void inBandCodeAsNumber() {
        assertEquals("482913", parse(
                "{\"code\":200,\"msg\":\"Success\",\"data\":482913}").inBandCode());
    }

    @Test
    public void inBandCodeWrappedInObject() {
        assertEquals("482913", parse(
                "{\"code\":200,\"msg\":\"Success\",\"data\":{\"code\":\"482913\"}}")
                .inBandCode());
        assertEquals("482913", parse(
                "{\"code\":200,\"msg\":\"Success\",\"data\":{\"verificationCode\":\"482913\"}}")
                .inBandCode());
    }

    @Test
    public void successMarkersAreNotCodes() {
        // Emailed-code path: data carries no code → the UI shows the entry field.
        assertNull(parse("{\"code\":200,\"msg\":\"Success\",\"data\":null}").inBandCode());
        assertNull(parse("{\"code\":200,\"msg\":\"Success\",\"data\":true}").inBandCode());
        assertNull(parse("{\"code\":200,\"msg\":\"Success\",\"data\":\"\"}").inBandCode());
        assertNull(parse("{\"code\":200,\"msg\":\"Success\"}").inBandCode());
    }
}
