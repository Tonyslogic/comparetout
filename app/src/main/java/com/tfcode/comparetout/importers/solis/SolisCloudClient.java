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
 *
 * The request-signing procedure and endpoint contract implemented here were
 * written against the SolisCloud Platform API Document V2.0 and verified
 * against the community Python client "soliscloud_api"
 * (https://github.com/hultenvp/soliscloud_api, Copyright (c) 2023 hultenvp,
 * MIT License). No source code from that project is included in this file;
 * its MIT notice is reproduced in THIRD_PARTY_LICENSES.md at the repository
 * root.
 */

package com.tfcode.comparetout.importers.solis;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.tfcode.comparetout.importers.solis.responses.SolisEnvelope;
import com.tfcode.comparetout.importers.solis.responses.StationDayEnergyResponse;
import com.tfcode.comparetout.importers.solis.responses.StationDayResponse;
import com.tfcode.comparetout.importers.solis.responses.StationListResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Synchronous client for the SolisCloud Platform API V2.0 (worker threads
 * only, matching the OpenAlphaESSClient / OctopusRestClient precedent).
 *
 * Every call is an HTTPS POST with an HMAC-SHA1 signature over
 * {@code POST\n{Content-MD5}\n{Content-Type}\n{Date}\n{path}}. The MD5 is
 * computed over the exact body bytes that are sent — the body is serialised
 * once and posted as a raw byte array so no second serialisation can break
 * the byte-identity the signature depends on. (For the same reason the
 * byte[] RequestBody overload is used: the String overload appends
 * "; charset=utf-8" to the Content-Type header, which would then differ
 * from the signed Content-Type string.)
 *
 * Rate limiting: the documented ceiling is 2 requests/second per endpoint;
 * the client enforces a global minimum of 1 second between any two requests.
 * Transient failures (I/O, HTTP 5xx, non-"0" envelope codes that are not
 * fatal) are retried in-call with 5s/10s/20s/40s backoff, at most
 * {@value #MAX_ATTEMPTS} attempts, then surface as SolisCloudException for
 * the worker to convert into {@code Result.retry()}. Fatal conditions —
 * HTTP 408 (clock skew), auth rejection (R0000 / HTTP 401/403), malformed
 * envelope — are thrown immediately as their distinct subtypes.
 */
public class SolisCloudClient {

    private static final String BASE_URL = "https://www.soliscloud.com:13333";
    /**
     * Used verbatim both as the Content-Type header and inside the signed
     * string — the two must be the same string or the server rejects the
     * signature. The community client proves the charset-less form works.
     */
    static final String CONTENT_TYPE = "application/json";
    private static final int PAGE_SIZE = 100;
    private static final long MIN_REQUEST_INTERVAL_MS = 1000L;
    private static final int MAX_ATTEMPTS = 5;
    private static final long[] BACKOFF_MS = {5_000L, 10_000L, 20_000L, 40_000L};

    private final String mKeyId;
    private final String mSecret;
    private final String mBaseUrl;
    private final OkHttpClient mHttpClient;
    private final Gson mGson = new Gson();
    private long mLastRequestAt = 0L;

    public SolisCloudClient(String keyId, String secret) {
        this(keyId, secret, BASE_URL);
    }

    /** Package-private: unit tests point the client at a MockWebServer. */
    SolisCloudClient(String keyId, String secret, String baseUrl) {
        mKeyId = keyId;
        mSecret = secret;
        mBaseUrl = baseUrl;
        mHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    // ── endpoints ───────────────────────────────────────────────────────────

    /**
     * All stations under the account, across pagination. Doubles as the
     * credential probe: a bad key/secret throws SolisCloudAuthException.
     */
    public List<StationListResponse.Station> getStationList() throws SolisCloudException {
        List<StationListResponse.Station> all = new ArrayList<>();
        int pageNo = 1;
        while (true) {
            JsonObject body = new JsonObject();
            body.addProperty("pageNo", pageNo);
            body.addProperty("pageSize", PAGE_SIZE);
            StationListResponse page = mGson.fromJson(
                    post("/v1/api/userStationList", body), StationListResponse.class);
            if (null == page || null == page.page || null == page.page.records) break;
            all.addAll(page.page.records);
            if (page.page.records.size() < PAGE_SIZE) break;
            pageNo++;
        }
        return all;
    }

    /**
     * The ~5-minute power curve for one station on one day.
     *
     * @param stationId the station id as a String (19-digit ids exceed
     *                  double precision; the API accepts string ids)
     * @param date      yyyy-MM-dd in the station's local day
     * @param tzHours   the station's UTC offset in hours for that date
     * @param currency  currency code for the money fields, e.g. "EUR"
     */
    public List<StationDayResponse> getStationDay(String stationId, String date,
                                                  int tzHours, String currency)
            throws SolisCloudException {
        JsonObject body = new JsonObject();
        body.addProperty("id", stationId);
        body.addProperty("money", currency);
        body.addProperty("time", date);
        body.addProperty("timeZone", tzHours);
        StationDayResponse[] samples = mGson.fromJson(
                post("/v1/api/stationDay", body), StationDayResponse[].class);
        return null == samples ? new ArrayList<>() : Arrays.asList(samples);
    }

    /**
     * Daily energy totals for ALL stations under the account on one date,
     * keyed by station id. One call serves every station in a run — cache
     * per date.
     */
    public Map<String, StationDayEnergyResponse.Record> getStationDayEnergyTotals(String date)
            throws SolisCloudException {
        Map<String, StationDayEnergyResponse.Record> byStation = new HashMap<>();
        int pageNo = 1;
        while (true) {
            JsonObject body = new JsonObject();
            body.addProperty("pageNo", String.valueOf(pageNo));
            body.addProperty("pageSize", String.valueOf(PAGE_SIZE));
            body.addProperty("time", date);
            StationDayEnergyResponse page = mGson.fromJson(
                    post("/v1/api/stationDayEnergyList", body), StationDayEnergyResponse.class);
            if (null == page || null == page.page || null == page.page.records) break;
            for (StationDayEnergyResponse.Record record : page.page.records) {
                if (null != record.id) byStation.put(record.id, record);
            }
            if (page.page.records.size() < PAGE_SIZE) break;
            pageNo++;
        }
        return byStation;
    }

    // ── plumbing ────────────────────────────────────────────────────────────

    private com.google.gson.JsonElement post(String path, JsonObject bodyObject)
            throws SolisCloudException {
        // Serialise ONCE; sign and send these exact bytes.
        byte[] bodyBytes = mGson.toJson(bodyObject).getBytes(StandardCharsets.UTF_8);
        SolisCloudException lastTransient = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(BACKOFF_MS[Math.min(attempt - 1, BACKOFF_MS.length - 1)]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new SolisCloudException("Interrupted while backing off");
                }
            }
            pace();
            try {
                return executeOnce(path, bodyBytes);
            } catch (SolisCloudAuthException | SolisCloudClockSkewException fatal) {
                throw fatal;
            } catch (SolisCloudException transientFailure) {
                // No android.util.Log here — the class stays JVM-pure so the
                // signing/envelope unit tests run without the Android runtime.
                lastTransient = transientFailure;
            }
        }
        throw new SolisCloudException("SolisCloud unreachable after " + MAX_ATTEMPTS
                + " attempts: " + (null == lastTransient ? "unknown" : lastTransient.getMessage()));
    }

    private com.google.gson.JsonElement executeOnce(String path, byte[] bodyBytes)
            throws SolisCloudException {
        String contentMd5 = contentMd5(bodyBytes);
        String date = gmtNow();
        String authorization = "API " + mKeyId + ":" + sign(mSecret, contentMd5, date, path);

        Request request = new Request.Builder()
                .url(mBaseUrl + path)
                .header("Content-MD5", contentMd5)
                .header("Date", date)
                .header("Authorization", authorization)
                .post(RequestBody.create(bodyBytes, MediaType.parse(CONTENT_TYPE)))
                .build();

        String responseBody;
        try (Response response = mHttpClient.newCall(request).execute()) {
            if (response.code() == 408)
                throw new SolisCloudClockSkewException(
                        "SolisCloud rejected the request time (HTTP 408) — check the device clock");
            if (response.code() == 401 || response.code() == 403)
                throw new SolisCloudAuthException(
                        "SolisCloud rejected the API credentials (HTTP " + response.code() + ")");
            if (!response.isSuccessful())
                throw new SolisCloudException("SolisCloud HTTP " + response.code() + " for " + path);
            ResponseBody body = response.body();
            if (null == body) throw new SolisCloudException("Empty response for " + path);
            responseBody = body.string();
        } catch (IOException ioe) {
            throw new SolisCloudException("Network error for " + path + ": " + ioe.getMessage());
        }

        return parseEnvelope(mGson, responseBody, path);
    }

    /**
     * Unwraps the {@code {success, code, msg, data}} envelope. Package-private
     * so the error-mapping unit tests exercise it directly.
     */
    static com.google.gson.JsonElement parseEnvelope(Gson gson, String responseBody, String path)
            throws SolisCloudException {
        SolisEnvelope envelope;
        try {
            envelope = gson.fromJson(responseBody, SolisEnvelope.class);
        } catch (JsonSyntaxException jse) {
            envelope = null;
        }
        if (null == envelope || null == envelope.code)
            // Not the documented wrapper at all — retrying can't fix a
            // contract mismatch, so fail fatally (worker → Result.failure()).
            throw new SolisCloudAuthException("Malformed SolisCloud response for " + path);
        if (!"0".equals(envelope.code)) {
            String message = "SolisCloud error code=" + envelope.code
                    + " msg=" + envelope.msg + " for " + path;
            if ("R0000".equals(envelope.code)) throw new SolisCloudAuthException(message);
            throw new SolisCloudException(message);
        }
        return envelope.data;
    }

    /** Global pacing: at least 1s between any two requests (limit is 2/s). */
    private void pace() throws SolisCloudException {
        long wait = mLastRequestAt + MIN_REQUEST_INTERVAL_MS - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new SolisCloudException("Interrupted while pacing requests");
            }
        }
        mLastRequestAt = System.currentTimeMillis();
    }

    // Package-private and deterministic for unit-test vectors.

    /** base64(MD5(body bytes)) — over the exact bytes that will be posted. */
    @NonNull
    static String contentMd5(byte[] bodyBytes) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            // java.util.Base64 (minSdk 28 > API 26) — unlike android.util.Base64
            // it is real in JVM unit tests, so the signing vectors can run there.
            return java.util.Base64.getEncoder().encodeToString(md5.digest(bodyBytes));
        } catch (Exception e) {
            // MD5 is mandatory on every Android/JVM; unreachable in practice.
            throw new IllegalStateException("MD5 unavailable", e);
        }
    }

    /** base64(HmacSHA1(secret, "POST\nmd5\ncontent-type\ndate\npath")). */
    @NonNull
    static String sign(String secret, String contentMd5, String date, String path) {
        String toSign = "POST\n" + contentMd5 + "\n" + CONTENT_TYPE + "\n" + date + "\n" + path;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return java.util.Base64.getEncoder().encodeToString(
                    mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA1 unavailable", e);
        }
    }

    /**
     * RFC-1123 GMT with a zero-padded day of month ("Sat, 05 Jul 2026 ..."),
     * matching the format the server is known to accept. The server rejects
     * with HTTP 408 when this is more than ±15 minutes off its own clock.
     */
    @NonNull
    static String gmtDate(Date when) {
        SimpleDateFormat format =
                new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        return format.format(when);
    }

    @NonNull
    private static String gmtNow() {
        return gmtDate(new Date());
    }
}
