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

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Manual, interactive diagnostic for the SolisCloud Platform API —
 * <b>NOT an automated test</b>. It has a {@code main} method and lives in the
 * test source set only because it is a developer tool for
 * {@code SolisCloudClient} and depends on nothing but the JDK; JUnit never
 * runs it (it declares no {@code @Test}), and it makes real network calls and
 * prompts for real API credentials, so it must only ever be launched by hand.
 *
 * <h2>Why it exists</h2>
 * The Solis integration ({@code plans/sources/solis.md}) is implemented
 * against the SolisCloud Platform API Document V2.0 with the community
 * <a href="https://github.com/hultenvp/soliscloud_api">soliscloud_api</a>
 * (MIT) client as protocol oracle — but the sign conventions of the day
 * curve are undocumented and Solis can change the service without notice.
 * Like its siblings ({@code EsbnPortalPhase0Probe},
 * {@code FusionSolarPhase0Probe}) this probe lets anyone with SolisCloud API
 * credentials exercise the live API once and produce a redacted, shareable
 * report that verifies — or pinpoints drift in — every assumption
 * {@code SolisCloudClient} and {@code SolisDataMassager} bake in. Run it to
 * qualify a new tester's plant, or re-run it whenever Solis is suspected of
 * changing the API.
 *
 * <h2>What it confirms (verdict IDs appear in the report's summary table)</h2>
 * <ul>
 *   <li><b>S1</b> — the signing recipe is accepted:
 *       {@code Authorization: API keyId:base64(HmacSHA1(secret,
 *       "POST\nMD5\napplication/json\nDate\npath"))} with the charset-less
 *       Content-Type and zero-padded RFC-1123 GMT date, exactly as
 *       {@code SolisCloudClient} sends it (byte-identical bodies).</li>
 *   <li><b>S2</b> — responses use the {@code {success, code, msg, data}}
 *       envelope with {@code code "0"} for success; the auth / error
 *       vocabulary (HTTP 401/403, code R0000) is captured verbatim.</li>
 *   <li><b>S3</b> — {@code /v1/api/userStationList} pages as
 *       {@code data.page.records[]} carrying {@code id} (19-digit, must
 *       survive as a string), {@code stationName}, {@code capacity},
 *       {@code type}, {@code timeZone}/{@code timeZoneName}, {@code money}.</li>
 *   <li><b>S4</b> — {@code /v1/api/stationDay} returns yesterday's sample
 *       array at ~5-minute (irregular) cadence with the fields the massager
 *       consumes ({@code power}, {@code familyLoadPower},
 *       {@code bypassLoadPower}, {@code batteryPower}, {@code psum} and the
 *       {@code …Zheng}/{@code …Fu} direction splits); the report dumps every
 *       field with per-series statistics and a time-weighted integral
 *       (unit·hours) so units and orientation can be judged offline.</li>
 *   <li><b>S5</b> — {@code /v1/api/stationDayEnergyList} returns the daily
 *       totals ({@code energy}, {@code gridPurchasedEnergy},
 *       {@code gridSellEnergy}, {@code homeLoadEnergy},
 *       {@code batteryChargeEnergy}, {@code batteryDischargeEnergy}) that
 *       drive total-normalisation and the dynamic sign pairing; the report
 *       prints a pairing-aid table (series integrals vs totals).</li>
 *   <li><b>S6</b> — the same calls work for an arbitrary <em>historical</em>
 *       day (~1 year back) — the premise of backfill.</li>
 *   <li><b>S7</b> — a deliberately skewed {@code Date} header (−30 min)
 *       draws HTTP 408, the condition {@code SolisCloudClient} maps to
 *       {@code SolisCloudClockSkewException}.</li>
 *   <li><b>S8</b> — one request signed with the real KeyID but a
 *       deliberately corrupted KeySecret, to capture how the service rejects
 *       a bad <em>signature</em> for a valid key (HTTP 401/403 or the
 *       {@code R0000} envelope). A bogus-credential dry-run cannot reach
 *       this: an unknown KeyID fails key lookup first ({@code 403
 *       "appid invalid…"}) before the signature is even checked, so only a
 *       run with real credentials reveals which rejection shape
 *       {@code SolisCloudClient}'s {@code R0000} mapping actually meets.</li>
 * </ul>
 *
 * <h2>Safety</h2>
 * The KeyID appears only as a masked prefix and the KeySecret never leaves
 * the machine except inside HMAC signatures; neither is written to the
 * report. Station ids and names are masked. Per-sample power values and
 * daily kWh totals <em>are</em> included (they are what the analysis needs) —
 * testers should be told the report reveals their plant's production figures
 * for two days, nothing more. Requests are paced ≥ 1 second apart (the
 * documented ceiling is 2/s); a full run makes at most 8 requests and the
 * deliberate skew and bad-signature requests are expected to be rejected, so
 * the probe is harmless to the account.
 *
 * <h2>Running it</h2>
 * Needs a JDK <b>12+</b> (single-file source launch plus permission to set
 * the {@code Date} header, which JDK 11 still restricts; on 11 add
 * {@code -Djdk.httpclient.allowRestrictedHeaders=date}). No build, no
 * Gradle, no Android SDK. From a clone of this repository:
 * <pre>{@code
 *   java app/src/test/java/com/tfcode/comparetout/importers/solis/SolisCloudPhase0Probe.java
 * }</pre>
 * (On Windows with Android Studio installed, its JBR works:
 * {@code "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" <same path>}.)
 * It prompts for the SolisCloud API KeyID and KeySecret — obtained from
 * SolisCloud &gt; basic settings &gt; API management (Solis enables API
 * access on request) — and an optional API host override (Enter accepts
 * {@code https://www.soliscloud.com:13333}). The redacted report is written
 * to {@code soliscloud-phase0-report.txt} in the working directory.
 */
public class SolisCloudPhase0Probe {

    static final String DEFAULT_BASE = "https://www.soliscloud.com:13333";
    /** Charset-less on purpose — it is part of the signed string (see client). */
    static final String CONTENT_TYPE = "application/json";

    static final StringBuilder report = new StringBuilder();
    static final Map<String, String> verdicts = new LinkedHashMap<>();

    static HttpClient client;
    static String base;
    static String keyId;
    static String secret;

    public static void main(String[] args) throws Exception {
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        keyId = prompt("SolisCloud API KeyID: ");
        if (keyId == null) keyId = "";
        keyId = keyId.trim();
        secret = promptSecret("SolisCloud API KeySecret: ");
        if (secret == null) secret = "";
        secret = secret.trim();
        base = prompt("API host [" + DEFAULT_BASE + "]: ");
        if (base == null || base.isBlank()) base = DEFAULT_BASE;
        base = base.trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);

        log("SolisCloud Phase 0 probe - " + LocalDateTime.now());
        log("API host: " + base);
        log("KeyID   : " + maskKeyId(keyId));
        log("At most 8 paced requests are made; the KeySecret is used only to");
        log("sign them and never appears in this report. The report DOES include");
        log("your plant's power figures for two days.");
        log("================================================================\n");

        ZoneId zone = ZoneId.systemDefault();
        LocalDate yesterday = LocalDate.now(zone).minusDays(1);
        LocalDate historic = LocalDate.now(zone).minusDays(365);

        // ── S1/S2/S3: userStationList (signing + envelope + station shape) ─
        String stationId = null, stationIdMasked = null, money = "EUR";
        Integer tzFromStation = null;
        {
            String body = "{\"pageNo\":1,\"pageSize\":100}";
            Result r = post("/v1/api/userStationList", body, 0, "STEP 1", "[S1/S2/S3]");
            boolean signed = r != null && "0".equals(r.code);
            verdict("S1", "HMAC signing accepted (envelope code 0)", signed);
            verdict("S2", "{success,code,msg,data} envelope shape", r != null && r.code != null);
            if (r != null && r.data != null) {
                Map<String, Object> data = asObj(r.data);
                Map<String, Object> page = data == null ? null : asObj(data.get("page"));
                List<Object> records = page == null ? null : asList(page.get("records"));
                if (page != null)
                    log("  page           : current=" + page.get("current")
                            + " pages=" + page.get("pages") + " total=" + page.get("total"));
                boolean shapeOk = false;
                if (records == null) {
                    log("  records        : !! missing - data keys: "
                            + (data == null ? "n/a" : data.keySet()));
                } else {
                    log("  stations       : " + records.size());
                    for (Object o : records) {
                        Map<String, Object> st = asObj(o);
                        if (st == null) continue;
                        String id = strOf(st.get("id"));
                        if (stationId == null && id != null) {
                            stationId = id;
                            stationIdMasked = maskId(id);
                            String m = strOf(st.get("money"));
                            if (m != null && !m.isBlank()) money = m;
                            Double tz = numOf(st.get("timeZone"));
                            if (tz != null) {
                                if (tz != Math.rint(tz))
                                    log("  !! fractional station timeZone (" + tz
                                            + ") - client passes int hours; flag for review");
                                tzFromStation = (int) Math.rint(tz);
                            }
                            shapeOk = id.length() >= 15; // 19-digit id survived as string
                        }
                        log("    - id=" + maskId(id)
                                + " name=" + presence(strOf(st.get("stationName")))
                                + " capacity=" + st.get("capacity")
                                + " type=" + st.get("type")
                                + " timeZone=" + st.get("timeZone")
                                + " tzName=" + st.get("timeZoneName")
                                + " money=" + st.get("money"));
                    }
                }
                verdict("S3", "station records carry id/name/capacity/type/tz/money", shapeOk);
            } else {
                verdict("S3", "station records carry id/name/capacity/type/tz/money", false);
            }
        }
        log("");

        int tzHours = tzFromStation != null ? tzFromStation
                : zone.getRules().getOffset(yesterday.atStartOfDay(zone).toInstant())
                        .getTotalSeconds() / 3600;

        // ── S4: stationDay, yesterday ─────────────────────────────────────
        Map<String, SeriesStats> daySeries = null;
        if (stationId == null) {
            log("STEP 2  SKIPPED - no station id from step 1.");
            verdict("S4", "stationDay sample shape + ~5-min cadence", false);
        } else {
            daySeries = probeStationDay("STEP 2", yesterday, stationId, stationIdMasked,
                    money, tzHours, "[S4]");
            boolean ok = daySeries != null
                    && daySeries.containsKey("power")
                    && daySeries.containsKey("time")
                    && cadenceOk(daySeries.get("time"));
            verdict("S4", "stationDay sample shape + ~5-min cadence", ok);
        }
        log("");

        // ── S5: stationDayEnergyList, yesterday ───────────────────────────
        Map<String, Object> totals = null;
        if (stationId == null) {
            log("STEP 3  SKIPPED - no station id.");
            verdict("S5", "stationDayEnergyList daily totals present", false);
        } else {
            totals = probeEnergyList("STEP 3", yesterday, stationId, stationIdMasked, "[S5]");
            boolean ok = totals != null && totals.containsKey("energy");
            verdict("S5", "stationDayEnergyList daily totals present", ok);
            if (daySeries != null && totals != null) pairingAid(daySeries, totals);
        }
        log("");

        // ── S6: historical day (~1 year back) ─────────────────────────────
        if (stationId == null) {
            log("STEP 4  SKIPPED - no station id.");
            verdict("S6", "historical day retrievable", false);
        } else {
            Map<String, SeriesStats> hist = probeStationDay("STEP 4", historic, stationId,
                    stationIdMasked, money, tzHours, "[S6]");
            probeEnergyList("STEP 4b", historic, stationId, stationIdMasked, "[S6]");
            boolean ok = hist != null && hist.containsKey("time")
                    && hist.get("time").count > 0;
            verdict("S6", "historical day retrievable (non-empty samples)", ok);
            if (hist != null && !ok)
                log("  >> empty historical day - plant may simply be newer than " + historic);
        }
        log("");

        // ── S7: deliberate clock skew → expect HTTP 408 ───────────────────
        {
            String body = "{\"pageNo\":1,\"pageSize\":100}";
            log("STEP 5  userStationList with Date skewed -30 min  [S7]");
            log("  (expected to be REJECTED - this validates the client's");
            log("   HTTP-408 -> clock-skew mapping; it does not harm the account)");
            Result r = post("/v1/api/userStationList", body, -30 * 60 * 1000L, null, null);
            if (r == null) {
                verdict("S7", "skewed Date drawing HTTP 408 (clock-skew tell)", false);
            } else {
                log("  status         : " + r.httpStatus
                        + (r.httpStatus == 408 ? "  [408 as the client assumes]" : ""));
                if (r.code != null) log("  envelope code  : " + r.code + " msg=" + r.msg);
                verdict("S7", "skewed Date drawing HTTP 408 (clock-skew tell)",
                        r.httpStatus == 408);
                if (r.httpStatus != 408)
                    log("  >> NOT 408 - record this: the client's clock-skew detection"
                            + " keys on 408.");
            }
        }
        log("");

        // ── S8: valid KeyID, corrupted secret → bad-signature vocabulary ──
        {
            String body = "{\"pageNo\":1,\"pageSize\":100}";
            log("STEP 6  userStationList signed with a corrupted KeySecret  [S8]");
            log("  (expected to be REJECTED - a bogus-KeyID run cannot reach this:");
            log("   unknown KeyIDs fail lookup first, 403 'appid invalid...', before");
            log("   the signature is checked. This reveals the bad-SIGNATURE shape");
            log("   the client's 401/403/R0000 mapping must catch.)");
            Result r = post("/v1/api/userStationList", body, 0, null, null, secret + "X");
            if (r == null) {
                verdict("S8", "bad-signature rejection shape captured", false);
            } else {
                log("  status         : " + r.httpStatus);
                if (r.code != null) log("  envelope code  : " + r.code
                        + " msg=" + maskKeyIdIn(r.msg));
                boolean rejected = r.httpStatus == 401 || r.httpStatus == 403
                        || "R0000".equals(r.code)
                        || (r.code != null && !"0".equals(r.code));
                verdict("S8", "bad-signature rejection shape captured "
                        + "(http=" + r.httpStatus
                        + (r.code == null ? "" : ", code=" + r.code) + ")", rejected);
                if (!rejected)
                    log("  !! ACCEPTED a bad signature - record this, it would mean"
                            + " the service is not verifying signatures at all.");
            }
        }
        log("");
        finish();
    }

    // ── step helpers ────────────────────────────────────────────────────────

    /** POST stationDay for one day; logs everything; returns per-field stats. */
    static Map<String, SeriesStats> probeStationDay(String step, LocalDate day, String id,
                                                    String idMasked, String money, int tzHours,
                                                    String tag) {
        String body = "{\"id\":\"" + id + "\",\"money\":\"" + money + "\",\"time\":\""
                + day + "\",\"timeZone\":" + tzHours + "}";
        Result r = post("/v1/api/stationDay", body, 0,
                step + "  stationDay " + day + " (station " + idMasked + ", money=" + money
                        + ", timeZone=" + tzHours + ")", tag);
        if (r == null || r.data == null) return null;
        List<Object> samples = asList(r.data);
        if (samples == null) {
            log("  data           : !! not an array - " + typeOf(r.data));
            return null;
        }
        log("  samples        : " + samples.size());
        // Union of keys, stats per numeric field, time-weighted integrals.
        Map<String, SeriesStats> stats = new LinkedHashMap<>();
        List<Long> times = new ArrayList<>();
        for (Object o : samples) {
            Map<String, Object> s = asObj(o);
            if (s == null) continue;
            Double t = numOf(s.get("time"));
            times.add(t == null ? null : t.longValue());
            for (Map.Entry<String, Object> e : s.entrySet()) {
                Double v = numOf(e.getValue());
                SeriesStats st = stats.computeIfAbsent(e.getKey(), k -> new SeriesStats());
                if (v != null) st.add(v);
                else if (e.getValue() instanceof String && st.example == null)
                    st.example = (String) e.getValue();
            }
        }
        // integrals need time deltas
        for (Map.Entry<String, SeriesStats> e : stats.entrySet())
            e.getValue().integral = integral(samples, e.getKey(), times);
        long avgIntervalS = avgIntervalSeconds(times);
        log("  cadence        : avg interval " + avgIntervalS + "s across "
                + times.size() + " stamps"
                + (avgIntervalS >= 200 && avgIntervalS <= 600 ? "  [~5-min, ok]"
                : "  [!! expected ~300s]"));
        if (!times.isEmpty() && times.get(0) != null) {
            log("  first/last     : " + firstNonNullStr(samples, "timeStr")
                    + " .. " + lastNonNullStr(samples, "timeStr"));
        }
        for (Map.Entry<String, SeriesStats> e : stats.entrySet()) {
            SeriesStats st = e.getValue();
            if (st.count == 0) {
                log("    " + pad(e.getKey(), 22) + ": non-numeric"
                        + (st.example == null ? "" : ", e.g. \"" + trunc(st.example, 40) + "\""));
            } else if ("time".equals(e.getKey()) || "timeZone".equals(e.getKey())) {
                log("    " + pad(e.getKey(), 22) + ": n=" + st.count
                        + " min=" + fmt(st.min) + " max=" + fmt(st.max));
            } else {
                log("    " + pad(e.getKey(), 22) + ": n=" + st.count
                        + " min=" + fmt(st.min) + " max=" + fmt(st.max)
                        + " sum=" + fmt(st.sum)
                        + " integral=" + fmt(st.integral) + " unit*h");
            }
        }
        SeriesStats timeStats = stats.get("time");
        if (timeStats != null) timeStats.avgIntervalSeconds = avgIntervalS;
        return stats;
    }

    /** POST stationDayEnergyList for a date; returns the record for [id]. */
    static Map<String, Object> probeEnergyList(String step, LocalDate day, String id,
                                               String idMasked, String tag) {
        // NOTE: pageNo/pageSize are STRINGS here (mirroring SolisCloudClient).
        String body = "{\"pageNo\":\"1\",\"pageSize\":\"100\",\"time\":\"" + day + "\"}";
        Result r = post("/v1/api/stationDayEnergyList", body, 0,
                step + "  stationDayEnergyList " + day, tag);
        if (r == null || r.data == null) return null;
        Map<String, Object> data = asObj(r.data);
        Map<String, Object> page = data == null ? null : asObj(data.get("page"));
        List<Object> records = page == null ? null : asList(page.get("records"));
        if (records == null) {
            log("  records        : !! missing - data keys: "
                    + (data == null ? "n/a" : data.keySet()));
            return null;
        }
        log("  records        : " + records.size());
        for (Object o : records) {
            Map<String, Object> rec = asObj(o);
            if (rec == null) continue;
            if (!id.equals(strOf(rec.get("id")))) continue;
            log("  record for station " + idMasked + " (all fields):");
            for (Map.Entry<String, Object> e : rec.entrySet()) {
                String k = e.getKey();
                String v = "id".equals(k) ? maskId(strOf(e.getValue()))
                        : "stationName".equals(k) ? presence(strOf(e.getValue()))
                        : String.valueOf(e.getValue());
                log("    " + pad(k, 26) + "= " + v);
            }
            return rec;
        }
        log("  !! no record matched the station id");
        return null;
    }

    /** Series-integral vs daily-total table for offline sign/unit pairing. */
    static void pairingAid(Map<String, SeriesStats> series, Map<String, Object> totals) {
        log("");
        log("  PAIRING AID (series time-weighted integral, unit*h vs daily totals):");
        pair(series, "power", totals, "energy");
        pair(series, "familyLoadPower", totals, "homeLoadEnergy");
        pair(series, "bypassLoadPower", totals, null);
        pair(series, "psumZheng", totals, "gridPurchasedEnergy / gridSellEnergy");
        pair(series, "psumFu", totals, "gridPurchasedEnergy / gridSellEnergy");
        pair(series, "psum", totals, "(signed net grid)");
        pair(series, "batteryPowerZheng", totals, "batteryChargeEnergy / batteryDischargeEnergy");
        pair(series, "batteryPowerFu", totals, "batteryChargeEnergy / batteryDischargeEnergy");
        pair(series, "batteryPower", totals, "(signed battery)");
        log("  >> match by magnitude: integral/1000 vs kWh total identifies units AND");
        log("     which direction each Zheng/Fu series carries (the massager's dynamic");
        log("     pairing assumption).");
    }

    static void pair(Map<String, SeriesStats> series, String seriesKey,
                     Map<String, Object> totals, String totalKey) {
        SeriesStats st = series.get(seriesKey);
        if (st == null || st.count == 0) {
            log("    " + pad(seriesKey, 22) + ": (absent)");
            return;
        }
        String vs = "";
        if (totalKey != null) {
            if (totalKey.contains("/") || totalKey.startsWith("(")) {
                vs = "  vs " + totalKey;
            } else {
                vs = "  vs " + totalKey + "=" + totals.get(totalKey);
            }
        }
        log("    " + pad(seriesKey, 22) + ": integral=" + fmt(st.integral) + vs);
    }

    // ── signed POST ─────────────────────────────────────────────────────────

    static final class Result {
        int httpStatus;
        String code;
        String msg;
        Object data;
    }

    /**
     * One signed POST, exactly as SolisCloudClient sends it. When [stepLabel]
     * is non-null, logs the exchange. [skewMs] shifts the Date header.
     */
    static Result post(String path, String body, long skewMs, String stepLabel, String tag) {
        return post(path, body, skewMs, stepLabel, tag, secret);
    }

    /** As above, but signing with [signingSecret] (S8 corrupts it on purpose). */
    static Result post(String path, String body, long skewMs, String stepLabel, String tag,
                       String signingSecret) {
        try {
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            String contentMd5 = contentMd5(bodyBytes);
            String date = gmtDate(new Date(System.currentTimeMillis() + skewMs));
            String toSign = "POST\n" + contentMd5 + "\n" + CONTENT_TYPE + "\n" + date + "\n" + path;
            String signature = hmacSha1(signingSecret, toSign);
            String authorization = "API " + keyId + ":" + signature;

            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-MD5", contentMd5)
                    .header("Content-Type", CONTENT_TYPE)
                    .header("Authorization", authorization)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
            try {
                b.header("Date", date);
            } catch (IllegalArgumentException restricted) {
                log("!! this JDK restricts the Date header - use JDK 12+ or add");
                log("   -Djdk.httpclient.allowRestrictedHeaders=date");
                return null;
            }
            pace();
            HttpResponse<byte[]> r = client.send(b.build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            String rb = new String(r.body(), StandardCharsets.UTF_8);

            if (stepLabel != null) {
                log(stepLabel + "  " + (tag == null ? "" : tag));
                log("  POST " + path);
                log("  body           : " + redactBody(body));
                log("  Content-MD5    : present (len=" + contentMd5.length() + ")");
                log("  Date           : " + date + (skewMs != 0 ? "  [skewed]" : ""));
                log("  signature      : present (len=" + signature.length() + ")");
                log("  status         : " + r.statusCode());
                log("  content-type   : "
                        + r.headers().firstValue("content-type").orElse("(none)"));
            }
            Result res = new Result();
            res.httpStatus = r.statusCode();
            Map<String, Object> env = asObj(Json.tryParse(rb));
            if (env == null) {
                if (stepLabel != null) {
                    log("  body           : !! not a JSON object - first 300 chars:");
                    log(indent(trunc(redact(rb), 300)));
                }
                return res;
            }
            res.code = strOf(env.get("code"));
            res.msg = strOf(env.get("msg"));
            res.data = env.get("data");
            if (stepLabel != null) {
                log("  envelope keys  : " + env.keySet());
                log("  success/code   : " + env.get("success") + " / " + res.code
                        + ("0".equals(res.code) ? "  [ok]" : "  [!! non-zero]"));
                if (res.msg != null && !"success".equalsIgnoreCase(res.msg))
                    log("  msg            : " + maskKeyIdIn(res.msg));
                // Spring-style rejections use "message", and the service echoes
                // the KeyID inside it ("appid invalid<keyId>") - mask it.
                String springMsg = strOf(env.get("message"));
                if (springMsg != null)
                    log("  message        : " + maskKeyIdIn(springMsg));
                if (r.statusCode() == 401 || r.statusCode() == 403 || "R0000".equals(res.code))
                    log("  >> auth rejection - matches the client's"
                            + " SolisCloudAuthException mapping.");
            }
            return res;
        } catch (Exception e) {
            log((stepLabel != null ? stepLabel : "request") + "  FAILED: " + e);
            return null;
        }
    }

    // ── signing primitives (mirroring SolisCloudClient byte-for-byte) ───────

    static String contentMd5(byte[] bodyBytes) throws Exception {
        return Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("MD5").digest(bodyBytes));
    }

    static String hmacSha1(String secret, String toSign) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(
                mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8)));
    }

    /** RFC-1123 GMT with zero-padded day, as the client sends. */
    static String gmtDate(Date when) {
        SimpleDateFormat format =
                new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        return format.format(when);
    }

    static long lastRequestAt = 0L;

    /** ≥1 s between requests (documented ceiling is 2/s). */
    static void pace() {
        long wait = lastRequestAt + 1000L - System.currentTimeMillis();
        if (wait > 0) {
            try { Thread.sleep(wait); } catch (InterruptedException ignored) { }
        }
        lastRequestAt = System.currentTimeMillis();
    }

    // ── series analysis ─────────────────────────────────────────────────────

    static final class SeriesStats {
        int count;
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE, sum;
        double integral; // value * hours, time-weighted
        long avgIntervalSeconds;
        String example;

        void add(double v) {
            count++;
            sum += v;
            if (v < min) min = v;
            if (v > max) max = v;
        }
    }

    /** Σ value_i * Δt_i(hours) using each sample's own time stamp. */
    static double integral(List<Object> samples, String key, List<Long> times) {
        double total = 0;
        for (int i = 0; i < samples.size(); i++) {
            Map<String, Object> s = asObj(samples.get(i));
            if (s == null) continue;
            Double v = numOf(s.get(key));
            Long t = i < times.size() ? times.get(i) : null;
            if (v == null || t == null) continue;
            Long next = null;
            for (int k = i + 1; k < times.size() && next == null; k++) next = times.get(k);
            long dtMs = next != null ? next - t : 300_000L; // last sample: assume 5 min
            if (dtMs <= 0 || dtMs > 3_600_000L) dtMs = 300_000L; // guard gaps
            total += v * (dtMs / 3_600_000.0);
        }
        return total;
    }

    static long avgIntervalSeconds(List<Long> times) {
        long sum = 0;
        int n = 0;
        Long prev = null;
        for (Long t : times) {
            if (t == null) continue;
            if (prev != null && t > prev) { sum += (t - prev); n++; }
            prev = t;
        }
        return n == 0 ? 0 : sum / n / 1000;
    }

    static boolean cadenceOk(SeriesStats timeStats) {
        return timeStats != null && timeStats.count > 0
                && timeStats.avgIntervalSeconds >= 200 && timeStats.avgIntervalSeconds <= 600;
    }

    static String firstNonNullStr(List<Object> samples, String key) {
        for (Object o : samples) {
            Map<String, Object> s = asObj(o);
            if (s != null && s.get(key) instanceof String) return (String) s.get(key);
        }
        return "(none)";
    }

    static String lastNonNullStr(List<Object> samples, String key) {
        for (int i = samples.size() - 1; i >= 0; i--) {
            Map<String, Object> s = asObj(samples.get(i));
            if (s != null && s.get(key) instanceof String) return (String) s.get(key);
        }
        return "(none)";
    }

    // ── redaction / formatting ──────────────────────────────────────────────

    static String presence(String s) {
        return s == null ? "MISSING" : "present (len=" + s.length() + ")";
    }

    static String maskKeyId(String k) {
        if (k == null || k.length() < 6) return "***";
        return k.substring(0, 4) + "…" + " (len=" + k.length() + ")";
    }

    /** The service echoes the KeyID inside error text - mask any occurrence. */
    static String maskKeyIdIn(String s) {
        if (s == null || keyId == null || keyId.length() < 6) return s;
        return s.replace(keyId, maskKeyId(keyId));
    }

    /** Mask a 19-digit station id: keep shape, hide the middle. */
    static String maskId(String id) {
        if (id == null) return "MISSING";
        if (id.length() <= 6) return "***";
        return id.substring(0, 3) + "…" + id.substring(id.length() - 2)
                + " (len=" + id.length() + ")";
    }

    /** Station ids in request bodies are masked before logging. */
    static String redactBody(String body) {
        return body.replaceAll("\\d{10,}", "<id>");
    }

    static String redact(String s) {
        return s.replaceAll("[A-Za-z0-9+/=_\\-]{24,}", "<redacted>");
    }

    static String trunc(String s, int n) {
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() <= n ? s : s.substring(0, n) + " ...[+" + (s.length() - n) + " chars]";
    }

    static String indent(String s) {
        StringBuilder sb = new StringBuilder();
        for (String line : s.split("\n")) sb.append("    ").append(line).append('\n');
        return sb.toString().stripTrailing();
    }

    static String pad(String s, int n) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < n) sb.append(' ');
        return sb.toString();
    }

    static String fmt(double d) {
        return String.format(Locale.US, "%.3f", d);
    }

    static String typeOf(Object o) {
        return o == null ? "null" : o.getClass().getSimpleName();
    }

    static void verdict(String id, String desc, boolean ok) {
        verdicts.put(id, (ok ? "ok    " : "FAIL  ") + desc);
    }

    // ── minimal JSON (parse only) ───────────────────────────────────────────

    @SuppressWarnings("unchecked")
    static Map<String, Object> asObj(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    static List<Object> asList(Object o) {
        return o instanceof List ? (List<Object>) o : null;
    }

    static String strOf(Object o) {
        if (o == null) return null;
        if (o instanceof Double) {
            double d = (Double) o;
            if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
        }
        return String.valueOf(o);
    }

    static Double numOf(Object o) {
        if (o instanceof Double) return (Double) o;
        if (o instanceof String) {
            try { return Double.parseDouble(((String) o).trim()); }
            catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    static final class Json {
        private final String s;
        private int i;

        private Json(String s) { this.s = s; }

        static Object tryParse(String s) {
            if (s == null) return null;
            try {
                return new Json(s.trim()).value();
            } catch (RuntimeException e) {
                return null;
            }
        }

        private Object value() {
            ws();
            char c = peek();
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (c == 't') { expect("true"); return Boolean.TRUE; }
            if (c == 'f') { expect("false"); return Boolean.FALSE; }
            if (c == 'n') { expect("null"); return null; }
            return number();
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            ws();
            if (peek() == '}') { i++; return m; }
            while (true) {
                ws();
                String k = string();
                ws();
                if (peek() != ':') throw err();
                i++;
                m.put(k, value());
                ws();
                char c = peek();
                if (c == ',') { i++; continue; }
                if (c == '}') { i++; return m; }
                throw err();
            }
        }

        private List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++;
            ws();
            if (peek() == ']') { i++; return l; }
            while (true) {
                l.add(value());
                ws();
                char c = peek();
                if (c == ',') { i++; continue; }
                if (c == ']') { i++; return l; }
                throw err();
            }
        }

        private String string() {
            if (peek() != '"') throw err();
            i++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                            break;
                        default: throw err();
                    }
                } else sb.append(c);
            }
        }

        private Double number() {
            int start = i;
            if (peek() == '-') i++;
            while (i < s.length() && "0123456789.eE+-".indexOf(s.charAt(i)) >= 0) i++;
            if (i == start) throw err();
            return Double.parseDouble(s.substring(start, i));
        }

        private void expect(String lit) {
            if (!s.startsWith(lit, i)) throw err();
            i += lit.length();
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private char peek() {
            if (i >= s.length()) throw err();
            return s.charAt(i);
        }

        private RuntimeException err() {
            return new RuntimeException("JSON parse error at " + i);
        }
    }

    // ── IO ──────────────────────────────────────────────────────────────────

    static void log(String line) {
        System.out.println(line);
        report.append(line).append('\n');
    }

    static void finish() throws IOException {
        if (!verdicts.isEmpty()) {
            log("================================================================");
            log("VERDICTS (see SolisCloudPhase0Probe javadoc for the S-ids)");
            for (Map.Entry<String, String> e : verdicts.entrySet())
                log("  " + e.getKey() + "  " + e.getValue());
            log("================================================================");
        }
        Path out = Path.of("soliscloud-phase0-report.txt");
        Files.writeString(out, report.toString(), StandardCharsets.UTF_8);
        System.out.println("\nReport written to " + out.toAbsolutePath());
        System.out.println("Please share the whole file (it is redacted; it does include");
        System.out.println("your plant's power figures for the two probed days).");
    }

    // One shared reader: a fresh BufferedReader per prompt would read ahead
    // and silently swallow the rest of stdin when input is piped/IDE-run.
    static BufferedReader stdin;

    static String readStdinLine() throws IOException {
        if (stdin == null) stdin = new BufferedReader(new InputStreamReader(System.in));
        String s = stdin.readLine();
        // Piped input written by Windows tools may start with a UTF-8 BOM,
        // which would silently corrupt the KeyID (and the Authorization header).
        if (s != null && !s.isEmpty() && s.charAt(0) == 0xFEFF) s = s.substring(1);
        return s;
    }

    static String prompt(String label) throws IOException {
        System.out.print(label);
        System.out.flush();
        Console c = System.console();
        if (c != null) return c.readLine();
        return readStdinLine();
    }

    static String promptSecret(String label) throws IOException {
        Console c = System.console();
        if (c != null) {
            char[] pw = c.readPassword(label);
            return pw == null ? "" : new String(pw);
        }
        System.out.print(label + "(input will be visible) ");
        System.out.flush();
        String s = readStdinLine();
        return s == null ? "" : s;
    }
}
