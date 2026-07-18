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
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/**
 * Manual, interactive diagnostic for the FusionSolar (Huawei) web-portal API —
 * <b>NOT an automated test</b>. It has a {@code main} method and lives in the
 * test source set only because it is a developer tool for the planned
 * FusionSolar data source and depends on nothing but the JDK; JUnit never runs
 * it (it declares no {@code @Test}), and it makes real network calls and
 * prompts for real credentials, so it must only ever be launched by hand.
 *
 * <h2>Why it exists</h2>
 * The FusionSolar integration ({@code plans/source/huawei.md}) is built on the
 * <em>unofficial</em> API the FusionSolar portal itself uses, with
 * <a href="https://github.com/jgriss/FusionSolarPy">FusionSolarPy</a> (MIT) as
 * the protocol oracle. Every endpoint, parameter and field name in the plan is
 * therefore an assumption until exercised against a live account. This probe
 * is the plan's "Phase 0": a tester who owns a Huawei plant runs it once, and
 * the redacted report it writes settles each assumption — including the ones
 * the plan explicitly leaves open (exact station-list path, grid buy/feed
 * field names, captcha endpoint, login-encryption details). The integration
 * can then be built (or later fixed) against the report rather than guesswork.
 * Re-run it whenever Huawei is suspected of changing the portal.
 *
 * <h2>What it confirms (assumption IDs appear in the report's verdict table)</h2>
 * <ul>
 *   <li><b>A1</b> — {@code GET /unisso/pubkey} returns the RSA public key,
 *       {@code timeStamp}, {@code version} and {@code enableEncrypt}.</li>
 *   <li><b>A2</b> — RSA-OAEP(SHA-384) password encryption with the pubkey
 *       {@code version} suffix is accepted (an interactive fallback retry with
 *       PKCS#1 v1.5 is offered if the portal rejects the credentials).</li>
 *   <li><b>A3</b> — {@code POST /unisso/v3/validateUser.action} is the live
 *       login path (the v2 variant is tried as fallback), and what its
 *       error codes look like for captcha / bad-password.</li>
 *   <li><b>A4</b> — the captcha image endpoint ({@code /unisso/verifycode})
 *       exists; when the portal demands one, the probe saves the image and
 *       lets the tester type the code interactively.</li>
 *   <li><b>A5</b> — session establishment / cross-region redirect: which host
 *       the account actually lands on (the client must persist it).</li>
 *   <li><b>A6</b> — {@code GET /rest/dpcloud/auth/v1/keep-alive} returns the
 *       {@code payload} that must be echoed as the {@code roarand} header.</li>
 *   <li><b>A7</b> — {@code GET /rest/dpcloud/auth/v1/is-session-alive} works
 *       as a liveness probe.</li>
 *   <li><b>A8</b> — the station list ({@code POST
 *       /rest/pvms/web/station/v1/station/station-list}) returns {@code dn}
 *       and name per plant.</li>
 *   <li><b>A9</b> — {@code GET /rest/pvms/web/station/v1/overview/energy-balance}
 *       with {@code timeDim=2} returns yesterday's 5-minute day curve: the
 *       report lists <em>every</em> field name with per-series statistics
 *       (length, missing-{@code "--"} count, sum and sum/12 ≈ kWh) plus all
 *       scalar daily totals, so grid-series naming (§1.3 of the plan) and
 *       sign orientation (§3.4) can be settled offline from the report.</li>
 *   <li><b>A10</b> — whether explicit grid buy/feed series exist, or the
 *       power-balance derivation is needed.</li>
 *   <li><b>A11</b> — the same call works for an arbitrary <em>historical</em>
 *       day (~1 year back) — the entire reason the portal API was chosen
 *       over the quota-starved official OpenAPI.</li>
 * </ul>
 *
 * <h2>Safety</h2>
 * The password is RSA-encrypted before it leaves the machine and is sent only
 * to {@code *.fusionsolar.huawei.com}; it is never written to the report. The
 * username is never logged. Plant identifiers ({@code dn}) and station names
 * are masked; tokens and cookies appear only as names / {@code present
 * (len=N)}. Per-slot power values and daily kWh totals <em>are</em> included
 * (they are what the analysis needs) — testers should be told the report
 * reveals their plant's production figures for two days, nothing more. The
 * written report is otherwise safe to share. Exactly one login is performed
 * per run (plus at most one interactive retry), and all requests are paced
 * ≥ 1 second apart.
 *
 * <h2>Running it</h2>
 * Needs any JDK 11+ ({@code java.net.http} + single-file source launch) — no
 * build, no Gradle, no Android SDK. From a clone of this repository:
 * <pre>{@code
 *   java app/src/test/java/com/tfcode/comparetout/importers/fusionsolar/FusionSolarPhase0Probe.java
 * }</pre>
 * (On Windows with Android Studio installed, its JBR works:
 * {@code "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" <same path>}.)
 * It prompts for the FusionSolar username, password (hidden when a real
 * console is attached) and region subdomain (Enter accepts the
 * {@code region01eu5} default; the probe follows any redirect to the
 * account's true region and records it). The report is written to
 * {@code fusionsolar-phase0-report.txt} in the working directory; a captcha
 * image, if one is demanded, to {@code fusionsolar-captcha.png}.
 */
public class FusionSolarPhase0Probe {

    static final String DOMAIN = ".fusionsolar.huawei.com";
    static final String DEFAULT_SUBDOMAIN = "region01eu5";
    static final String UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_3) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36";

    // Manual cookie jar shared across all *.fusionsolar.huawei.com hosts
    // (the portal's cookies are domain-scoped; sharing also survives the
    // cross-region redirect).
    static final Map<String, String> cookies = new LinkedHashMap<>();
    static final Set<String> everSet = new LinkedHashSet<>();

    static final StringBuilder report = new StringBuilder();
    static final Map<String, String> verdicts = new LinkedHashMap<>();

    static HttpClient client;
    static String roarand = null;

    public static void main(String[] args) throws Exception {
        client = HttpClient.newBuilder()
                // HTTP/1.1 for the same reason as the ESBN probe: some
                // front-ends mishandle the JDK client's h2 negotiation.
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER) // followed by hand to log hops
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        String user = prompt("FusionSolar username: ");
        if (user == null) user = "";
        String password = promptSecret("FusionSolar password: ");
        String sub = prompt("Region subdomain [" + DEFAULT_SUBDOMAIN + "]: ");
        if (sub == null || sub.isBlank()) sub = DEFAULT_SUBDOMAIN;
        sub = sub.trim();

        log("FusionSolar Phase 0 probe - " + LocalDateTime.now());
        log("Login host: https://" + sub + DOMAIN);
        log("One login is performed (plus at most one retry you approve).");
        log("Report is redacted (no username/password/tokens; plant ids masked);");
        log("it DOES include your plant's power figures for two days.");
        log("================================================================\n");

        // ── A1: pubkey ────────────────────────────────────────────────────
        final String entrySub = sub;
        String pubKeyPem = null, keyVersion = null;
        boolean enableEncrypt = true;
        String timeStamp = null;
        for (int tries = 1; tries <= 2; tries++) {
            try {
                // The entry subdomain may 302 to the account's real front-end
                // (seen live: region01eu5 -> eu5) — and the redirect target is a
                // login PAGE, not the pubkey path, so on a host change we adopt
                // the landed host and re-request /unisso/pubkey there directly.
                HttpResponse<byte[]> r = getFollow(url(sub, "/unisso/pubkey"));
                String b = body(r);
                log("STEP 1" + (tries > 1 ? "b" : " ") + " GET /unisso/pubkey on '" + sub + "'   [A1]");
                if (!hops.isEmpty()) log("  redirect hops  : " + hops);
                String landed = r.uri().getHost();
                boolean hostChanged = false;
                if (landed != null && landed.endsWith(DOMAIN)) {
                    String landedSub = landed.substring(0, landed.length() - DOMAIN.length());
                    if (!landedSub.equals(sub)) {
                        log("  >> host redirect: '" + sub + "' -> '" + landedSub
                                + "' (all further calls use it)");
                        sub = landedSub;
                        hostChanged = true;
                    }
                }
                log("  status         : " + r.statusCode());
                log("  content-type   : " + ct(r));
                Map<String, Object> j = asObj(Json.tryParse(b));
                if (j == null) {
                    log("  body           : !! not JSON - first 300 chars:");
                    log(indent(trunc(redact(b), 300)));
                    if (hostChanged && tries == 1) {
                        log("  >> retrying pubkey directly on the landed host");
                        log("");
                        pace();
                        continue;
                    }
                } else {
                    log("  JSON keys      : " + j.keySet());
                    pubKeyPem = strOf(j.get("pubKey"));
                    keyVersion = strOf(j.get("version"));
                    timeStamp = strOf(j.get("timeStamp"));
                    Object ee = j.get("enableEncrypt");
                    if (ee instanceof Boolean) enableEncrypt = (Boolean) ee;
                    log("  pubKey         : " + presence(pubKeyPem)
                            + (pubKeyPem != null && pubKeyPem.contains("BEGIN PUBLIC KEY") ? "  [PEM, ok]" : ""));
                    log("  version        : " + presence(keyVersion));
                    log("  timeStamp      : " + (timeStamp == null ? "MISSING" : "present"));
                    log("  enableEncrypt  : " + enableEncrypt);
                }
                verdict("A1", "pubkey endpoint shape", j != null && pubKeyPem != null);
            } catch (Exception e) {
                log("STEP 1  FAILED: " + e);
                verdict("A1", "pubkey endpoint shape", false);
            }
            break;
        }
        log("");
        pace();

        // ── A2/A3: validateUser (with optional captcha / padding retry) ───
        if (!enableEncrypt || pubKeyPem == null) {
            log("NOTE: enableEncrypt=false or no pubkey - the password will be sent");
            log("      unencrypted in the request body (over TLS).");
        }

        boolean loggedIn = false;
        boolean v3Worked = false;
        List<String> regionCandidates = new ArrayList<>();
        String redirectURL = null;
        String padding = "OAEP-SHA384";
        String verifyCode = null;
        int attempts = 0;
        while (attempts < 3 && !loggedIn) {
            attempts++;
            String encPw;
            try {
                if (enableEncrypt && pubKeyPem != null) {
                    encPw = encryptPassword(pubKeyPem, keyVersion, password, padding);
                } else {
                    encPw = password;
                }
            } catch (Exception e) {
                log("LOGIN   password encryption FAILED (" + padding + "): " + e);
                break;
            }
            StringBuilder jb = new StringBuilder();
            jb.append("{\"organizationName\":\"\",\"username\":").append(jstr(user))
                    .append(",\"password\":").append(jstr(encPw));
            if (verifyCode != null) jb.append(",\"verifycode\":").append(jstr(verifyCode));
            jb.append('}');

            String qs = timeStamp == null ? "" :
                    "?timeStamp=" + enc(timeStamp) + "&nonce=" + enc(timeStamp);
            Map<String, String> h = new LinkedHashMap<>();
            h.put("Content-Type", "application/json");
            h.put("Accept", "application/json, text/plain, */*");

            Map<String, Object> j = null;
            int status = -1;
            String path = "/unisso/v3/validateUser.action";
            try {
                HttpResponse<byte[]> r = postFollowRepost(url(sub, path + qs), h,
                        jb.toString().getBytes(StandardCharsets.UTF_8));
                status = r.statusCode();
                List<String> v3Hops = hops;
                if (status == 404 || status == 405) {
                    log("STEP 2  POST " + path + " -> " + status + "; trying v2 fallback  [A3]");
                    pace();
                    path = "/unisso/v2/validateUser.action";
                    String v2qs = "?decision=1&service=" + enc("https://" + sub + DOMAIN
                            + "/unisess/v1/auth?service=/netecowebext/home/index.html");
                    r = postFollowRepost(url(sub, path + v2qs), h,
                            jb.toString().getBytes(StandardCharsets.UTF_8));
                    status = r.statusCode();
                    v3Hops = hops;
                } else {
                    v3Worked = true;
                }
                String b = body(r);
                j = asObj(Json.tryParse(b));
                log("STEP 2  POST " + path + "  (attempt " + attempts + ", " + padding + ")  [A2/A3]");
                if (!v3Hops.isEmpty()) log("  redirect hops  : " + v3Hops + " (body re-POSTed)");
                if (isRedirect(status))
                    log("  !! still a redirect after follow cap; location: " + location(r));
                log("  status         : " + status);
                log("  content-type   : " + ct(r));
                log("  cookies now    : " + cookies.keySet());
                if (j == null) {
                    log("  body           : !! not JSON - first 300 chars:");
                    log(indent(trunc(redact(b), 300)));
                } else {
                    log("  JSON keys      : " + j.keySet());
                    Object ec = j.get("errorCode");
                    Object em = j.get("errorMsg");
                    log("  errorCode      : " + ec);
                    log("  errorMsg       : " + em);
                    if (j.containsKey("verifyCodeCreate"))
                        log("  verifyCodeCreate : " + j.get("verifyCodeCreate")
                                + "  [captcha-demand flag?]");
                    if (j.containsKey("twoFactorStatus"))
                        log("  twoFactorStatus  : " + j.get("twoFactorStatus"));
                    String ru = strOf(j.get("redirectURL"));
                    if (ru != null) {
                        redirectURL = ru;
                        log("  redirectURL    : " + hostPath(ru) + "  [session-establishment URL]");
                    }
                    String su = strOf(j.get("serviceUrl"));
                    if (su != null) log("  serviceUrl     : " + hostPath(su));
                    if (j.get("multiRegions") != null)
                        log("  multiRegions   : " + trunc(redact(String.valueOf(j.get("multiRegions"))), 200));
                    Object multi = j.get("respMultiRegionName");
                    if (multi instanceof List) {
                        log("  respMultiRegionName : " + multi + "  [region routing hint]");
                        for (Object o : (List<?>) multi) {
                            String s = strOf(o);
                            if (s != null && s.matches("[a-z0-9]+")) regionCandidates.add(s);
                        }
                    }
                    boolean noError = ec == null || "0".equals(strOf(ec)) || "".equals(strOf(ec));
                    if (status == 200 && noError) {
                        loggedIn = true;
                        log("  >> login looks SUCCESSFUL (no errorCode).");
                    }
                }
            } catch (Exception e) {
                log("STEP 2  FAILED: " + e);
                break;
            }
            log("");
            if (loggedIn) break;

            // Failed — offer the captcha round-trip (validates A4) and,
            // separately, one padding fallback. Tester stays in control so
            // we never burn login attempts unattended.
            pace();
            boolean captchaAvailable = probeCaptcha(sub, true);
            if (captchaAvailable) {
                String code = prompt("A captcha image was saved to fusionsolar-captcha.png.\n"
                        + "Open it and type the code to retry, or press Enter to skip: ");
                if (code != null && !code.isBlank()) {
                    verifyCode = code.trim();
                    continue;
                }
            }
            if ("OAEP-SHA384".equals(padding)) {
                String yn = prompt("Login was rejected. Retry ONCE with PKCS#1 v1.5 padding\n"
                        + "instead of OAEP (in case the encryption assumption is wrong)? [y/N]: ");
                if (yn != null && yn.trim().toLowerCase().startsWith("y")) {
                    padding = "PKCS1-v1.5";
                    verifyCode = null;
                    continue;
                }
            }
            break;
        }
        verdict("A2", "password encryption accepted (" + padding + ")", loggedIn);
        verdict("A3", "validateUser v3 path live", v3Worked);
        if (!verdicts.containsKey("A4")) {
            pace();
            probeCaptcha(sub, false); // even on success, confirm the endpoint exists
        }
        log("");
        pace();

        // ── A5: session establishment / region redirect ───────────────────
        String dataHost = sub;
        try {
            // Prefer the redirectURL the login response handed back (the
            // portal's own next hop); the /unisess/v1/auth path is only the
            // oracle-derived guess for when it is absent.
            String authUrl = redirectURL != null
                    ? URI.create(url(sub, "/")).resolve(redirectURL).toString()
                    : url(sub, "/unisess/v1/auth?service="
                        + enc("/netecowebext/home/index.html"));
            HttpResponse<byte[]> r = getFollow(authUrl);
            String landed = r.uri().getHost();
            log("STEP 3  GET " + (redirectURL != null
                    ? "login redirectURL (" + hostPath(authUrl) + ")"
                    : "/unisess/v1/auth (oracle guess)")
                    + " (session/region establishment)  [A5]");
            log("  final status   : " + r.statusCode());
            log("  redirect chain : " + hops);
            log("  final host     : " + landed);
            log("  cookies now    : " + cookies.keySet());
            if (landed != null && landed.endsWith(DOMAIN))
                dataHost = landed.substring(0, landed.length() - DOMAIN.length());
            if (!dataHost.equals(sub))
                log("  >> region redirect: data host is '" + dataHost + "' (client must persist this).");
            verdict("A5", "session/region establishment (landed: " + dataHost + ")", loggedIn);
        } catch (Exception e) {
            log("STEP 3  FAILED: " + e);
            verdict("A5", "session/region establishment", false);
        }
        log("");
        pace();

        // ── A6: keep-alive → roarand ──────────────────────────────────────
        // The SSO front-end (e.g. 'eu5') 404s /rest/dpcloud/* — the entry
        // region host is kept as a candidate in case data lives back there.
        if (!regionCandidates.contains(entrySub)) regionCandidates.add(entrySub);
        int kaBest = tryKeepAlive(dataHost);
        String kaBestHost = dataHost;
        if (kaBest < 2) {
            for (String cand : regionCandidates) {
                if (cand.equals(dataHost)) continue;
                log("  retrying keep-alive on region candidate '" + cand + "'");
                pace();
                int s = tryKeepAlive(cand);
                if (s > kaBest) { kaBest = s; kaBestHost = cand; }
                if (kaBest == 2) break;
            }
        }
        dataHost = kaBestHost;
        boolean keepAliveOk = kaBest == 2;
        if (kaBest == 1)
            log("  >> keep-alive endpoint EXISTS on '" + dataHost + "' but no payload"
                    + " (no session) - remaining probes use this host.");
        verdict("A6", "keep-alive returns roarand payload (host: " + dataHost + ")", keepAliveOk);
        log("");
        pace();

        // ── A7: is-session-alive ──────────────────────────────────────────
        try {
            HttpResponse<byte[]> r = getFollow(
                    url(dataHost, "/rest/dpcloud/auth/v1/is-session-alive"), roarandHeader());
            String b = body(r);
            log("STEP 5  GET /rest/dpcloud/auth/v1/is-session-alive  [A7]");
            if (!hops.isEmpty()) log("  redirect hops  : " + hops);
            log("  status         : " + r.statusCode());
            log("  body           : " + trunc(redact(b), 200));
            verdict("A7", "is-session-alive liveness probe", r.statusCode() == 200);
        } catch (Exception e) {
            log("STEP 5  FAILED: " + e);
            verdict("A7", "is-session-alive liveness probe", false);
        }
        log("");
        pace();

        if (!loggedIn && !keepAliveOk) {
            log("Login did not succeed and no session is alive - skipping data steps.");
            finish();
            return;
        }

        // ── A8: station list ──────────────────────────────────────────────
        String dn = null, dnMasked = null;
        try {
            ZoneId zone = ZoneId.systemDefault();
            long now = System.currentTimeMillis();
            String bodyJson = "{\"curPage\":1,\"pageSize\":10,\"gridConnectedTime\":\"\","
                    + "\"queryTime\":" + dayStartMillis(LocalDate.now(zone), zone) + ","
                    + "\"timeZone\":" + tzHours(LocalDate.now(zone), zone) + ","
                    + "\"sortId\":\"createTime\",\"sortDir\":\"DESC\",\"locale\":\"en_US\"}";
            Map<String, String> h = roarandHeader();
            h.put("Content-Type", "application/json");
            HttpResponse<byte[]> r = postFollowRepost(
                    url(dataHost, "/rest/pvms/web/station/v1/station/station-list"), h,
                    bodyJson.getBytes(StandardCharsets.UTF_8));
            String b = body(r);
            log("STEP 6  POST /rest/pvms/web/station/v1/station/station-list  [A8]");
            if (!hops.isEmpty()) log("  redirect hops  : " + hops + " (body re-POSTed)");
            log("  status         : " + r.statusCode());
            log("  content-type   : " + ct(r));
            Map<String, Object> j = asObj(Json.tryParse(b));
            if (j == null) {
                log("  body           : !! not JSON - first 400 chars:");
                log(indent(trunc(redact(b), 400)));
            } else {
                log("  JSON keys      : " + j.keySet());
                log("  success/failCode : " + j.get("success") + " / " + j.get("failCode"));
                Object data = j.get("data");
                List<Object> list = null;
                if (data instanceof List) list = asList(data);
                else if (asObj(data) != null) list = asList(asObj(data).get("list"));
                if (list == null) {
                    log("  data           : !! no station array found - data keys: "
                            + (asObj(data) == null ? "n/a" : asObj(data).keySet()));
                } else {
                    log("  stations       : " + list.size());
                    for (Object o : list) {
                        Map<String, Object> st = asObj(o);
                        if (st == null) continue;
                        String thisDn = strOf(st.get("dn"));
                        if (dn == null && thisDn != null) { dn = thisDn; dnMasked = maskId(thisDn); }
                        log("    - keys: " + st.keySet());
                        log("      dn=" + maskId(thisDn)
                                + " name=" + presence(strOf(st.get("name")))
                                + " capacity=" + st.get("installedCapacity"));
                    }
                }
            }
            verdict("A8", "station list returns dn", dn != null);
        } catch (Exception e) {
            log("STEP 6  FAILED: " + e);
            verdict("A8", "station list returns dn", false);
        }
        log("");
        pace();

        // ── A9/A10: energy-balance, yesterday ─────────────────────────────
        if (dn == null) {
            log("STEP 7  SKIPPED - no station dn from step 6.");
            verdict("A9", "energy-balance day curve", false);
            verdict("A10", "explicit grid series present", false);
            verdict("A11", "historical day retrievable", false);
        } else {
            ZoneId zone = ZoneId.systemDefault();
            LocalDate yesterday = LocalDate.now(zone).minusDays(1);
            Map<String, Object> data = fetchEnergyBalance(dataHost, dn, dnMasked, yesterday, zone,
                    "STEP 7", "yesterday " + yesterday + "  [A9/A10]");
            boolean curveOk = false, gridExplicit = false;
            if (data != null) {
                curveOk = data.containsKey("productPower")
                        && (data.containsKey("usePower") || data.containsKey("selfUsePower"));
                for (String k : data.keySet()) {
                    String lk = k.toLowerCase();
                    if (data.get(k) instanceof List
                            && (lk.contains("buy") || lk.contains("ongrid") || lk.contains("grid")))
                        gridExplicit = true;
                }
            }
            verdict("A9", "energy-balance day curve (expected series present)", curveOk);
            verdict("A10", "explicit grid buy/feed series present", gridExplicit);
            if (!gridExplicit && data != null)
                log("  >> no grid-named series - the section 3 balance derivation path will be needed.");
            log("");
            pace();

            // ── A11: historical day ───────────────────────────────────────
            LocalDate historic = LocalDate.now(zone).minusDays(365);
            Map<String, Object> hist = fetchEnergyBalance(dataHost, dn, dnMasked, historic, zone,
                    "STEP 8", "historical " + historic + " (~1 year back)  [A11]");
            boolean histOk = false;
            if (hist != null) {
                for (Object v : hist.values())
                    if (v instanceof List && numericCount(asList(v)) > 0) { histOk = true; break; }
            }
            verdict("A11", "historical day retrievable (non-empty series)", histOk);
            if (hist != null && !histOk)
                log("  >> historical response had no numeric samples - plant may simply be"
                        + " newer than " + historic + "; re-run mentally with plant age in mind.");
        }
        log("");
        finish();
    }

    // ── step helpers ────────────────────────────────────────────────────────

    /** GET the captcha endpoint; save the image when {@code save}. Records A4. */
    static boolean probeCaptcha(String sub, boolean save) {
        try {
            HttpResponse<byte[]> r = getFollow(
                    url(sub, "/unisso/verifycode?timestamp=" + System.currentTimeMillis()));
            String type = ct(r);
            boolean isImage = r.statusCode() == 200 && type.startsWith("image");
            log("CAPTCHA GET /unisso/verifycode  [A4]");
            if (!hops.isEmpty()) log("  redirect hops  : " + hops);
            log("  status         : " + r.statusCode());
            log("  content-type   : " + type);
            log("  bytes          : " + r.body().length);
            if (isImage && save) {
                Files.write(Path.of("fusionsolar-captcha.png"), r.body());
                log("  saved to       : fusionsolar-captcha.png");
            }
            verdict("A4", "captcha image endpoint", isImage);
            return isImage;
        } catch (Exception e) {
            log("CAPTCHA probe FAILED: " + e);
            verdict("A4", "captcha image endpoint", false);
            return false;
        }
    }

    /** @return 2 = payload (roarand captured), 1 = JSON but no payload, 0 = failed. */
    static int tryKeepAlive(String host) {
        try {
            HttpResponse<byte[]> r = getFollow(url(host, "/rest/dpcloud/auth/v1/keep-alive"));
            String b = body(r);
            log("STEP 4  GET /rest/dpcloud/auth/v1/keep-alive on '" + host + "'  [A6]");
            if (!hops.isEmpty()) log("  redirect hops  : " + hops);
            log("  status         : " + r.statusCode());
            log("  content-type   : " + ct(r));
            Map<String, Object> j = asObj(Json.tryParse(b));
            if (j == null) {
                log("  body           : " + trunc(redact(b), 200));
                return 0;
            }
            log("  JSON keys      : " + j.keySet());
            if (j.containsKey("code") || j.containsKey("message"))
                log("  code/message   : " + j.get("code") + " / " + j.get("message"));
            String payload = strOf(j.get("payload"));
            log("  payload (roarand) : " + presence(payload));
            if (payload != null) { roarand = payload; return 2; }
            return 1;
        } catch (Exception e) {
            log("STEP 4  keep-alive FAILED on '" + host + "': " + e);
            return 0;
        }
    }

    /** Fetch + analyse one energy-balance day; returns the {@code data} map or null. */
    static Map<String, Object> fetchEnergyBalance(String host, String dn, String dnMasked,
                                                  LocalDate day, ZoneId zone,
                                                  String step, String label) {
        try {
            String u = url(host, "/rest/pvms/web/station/v1/overview/energy-balance"
                    + "?stationDn=" + enc(dn)
                    + "&timeDim=2"
                    + "&queryTime=" + dayStartMillis(day, zone)
                    + "&timeZone=" + tzHours(day, zone)
                    + "&timeZoneStr=" + enc(zone.getId())
                    + "&_=" + System.currentTimeMillis());
            HttpResponse<byte[]> r = getFollow(u, roarandHeader());
            String b = body(r);
            log(step + "  GET energy-balance - " + label);
            if (!hops.isEmpty()) log("  redirect hops  : " + hops);
            log("  stationDn      : " + dnMasked + "   zone " + zone.getId()
                    + " (offset " + tzHours(day, zone) + "h on " + day + ")");
            log("  status         : " + r.statusCode());
            log("  content-type   : " + ct(r));
            Map<String, Object> j = asObj(Json.tryParse(b));
            if (j == null) {
                log("  body           : !! not JSON - first 400 chars:");
                log(indent(trunc(redact(b), 400)));
                return null;
            }
            log("  JSON keys      : " + j.keySet());
            log("  success/failCode : " + j.get("success") + " / " + j.get("failCode"));
            Map<String, Object> data = asObj(j.get("data"));
            if (data == null) {
                log("  data           : !! missing/not an object");
                return null;
            }
            log("  data keys      : " + data.keySet());
            for (Map.Entry<String, Object> e : data.entrySet()) {
                Object v = e.getValue();
                if (v instanceof List) {
                    List<Object> l = asList(v);
                    if ("xAxis".equals(e.getKey())) {
                        log("    xAxis        : " + l.size() + " stamps, first="
                                + strOf(l.isEmpty() ? null : l.get(0))
                                + " last=" + strOf(l.isEmpty() ? null : l.get(l.size() - 1))
                                + (l.size() == 288 ? "  [288 = 5-min, ok]" : "  [!! expected 288]"));
                    } else {
                        int n = l.size(), num = numericCount(l), dashes = dashCount(l);
                        double sum = numericSum(l);
                        log("    " + pad(e.getKey(), 22) + ": len=" + n
                                + " numeric=" + num + " \"--\"=" + dashes
                                + String.format(" sum=%.3f sum/12=%.3f", sum, sum / 12.0)
                                + " first=" + firstNumeric(l));
                    }
                } else {
                    // Scalar (daily totals, flags). Mask anything id-shaped.
                    String key = e.getKey();
                    String val = String.valueOf(v);
                    if (key.toLowerCase().contains("dn")) val = maskId(val);
                    log("    " + pad(key, 22) + "= " + val);
                }
            }
            return data;
        } catch (Exception e) {
            log(step + "  FAILED: " + e);
            return null;
        }
    }

    // ── crypto ──────────────────────────────────────────────────────────────

    static String encryptPassword(String pem, String version, String password, String padding)
            throws Exception {
        String b64 = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
        byte[] der = Base64.getMimeDecoder().decode(b64);
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        Cipher c;
        if ("PKCS1-v1.5".equals(padding)) {
            c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            c.init(Cipher.ENCRYPT_MODE, key);
        } else {
            // FusionSolarPy: PKCS1_OAEP with SHA-384. JCE defaults MGF1 to
            // SHA-1 for this transformation string, so pass the spec explicitly.
            c = Cipher.getInstance("RSA/ECB/OAEPWithSHA-384AndMGF1Padding");
            c.init(Cipher.ENCRYPT_MODE, key, new OAEPParameterSpec("SHA-384", "MGF1",
                    MGF1ParameterSpec.SHA384, PSource.PSpecified.DEFAULT));
        }
        byte[] out = c.doFinal(password.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(out) + (version == null ? "" : version);
    }

    // ── HTTP (manual redirects + cookies, as in EsbnPortalPhase0Probe) ──────

    static String url(String subdomain, String pathAndQuery) {
        return "https://" + subdomain + DOMAIN + pathAndQuery;
    }

    static HttpResponse<byte[]> send(String method, String url, Map<String, String> headers,
                                     byte[] bodyBytes) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", UA)
                .header("Accept-Language", "en-IE,en;q=0.9")
                .header("Accept-Encoding", "identity"); // JDK client does not auto-gunzip
        if (headers == null || !headers.containsKey("Accept"))
            b.header("Accept", "application/json, text/html;q=0.9, image/*;q=0.8, */*;q=0.7");
        if (headers != null) for (Map.Entry<String, String> e : headers.entrySet())
            b.header(e.getKey(), e.getValue());
        String cookieHeader = cookieHeader();
        if (!cookieHeader.isEmpty()) b.header("Cookie", cookieHeader);
        if (bodyBytes == null) b.method(method, HttpRequest.BodyPublishers.noBody());
        else b.method(method, HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
        HttpResponse<byte[]> r = client.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        storeCookies(r);
        return r;
    }

    static List<String> hops = new ArrayList<>();

    static HttpResponse<byte[]> getFollow(String url) throws Exception {
        return getFollow(url, null);
    }

    static HttpResponse<byte[]> getFollow(String url, Map<String, String> headers) throws Exception {
        hops = new ArrayList<>();
        HttpResponse<byte[]> r = send("GET", url, headers, null);
        int n = 0;
        while (isRedirect(r.statusCode()) && n++ < 10) {
            String loc = r.headers().firstValue("location").orElse(null);
            if (loc == null) break;
            url = URI.create(url).resolve(loc).toString();
            hops.add(hostPath(url));
            r = send("GET", url, headers, null);
        }
        return r;
    }

    /**
     * Follow redirects on a POST by re-POSTing the same body to the target.
     * The portal's front-end 302s API calls to the account's real host (seen
     * live: region01eu5 -> eu5); converting to GET there would just 404.
     */
    static HttpResponse<byte[]> postFollowRepost(String url, Map<String, String> headers,
                                                 byte[] bodyBytes) throws Exception {
        hops = new ArrayList<>();
        HttpResponse<byte[]> r = send("POST", url, headers, bodyBytes);
        int n = 0;
        while (isRedirect(r.statusCode()) && n++ < 5) {
            String loc = r.headers().firstValue("location").orElse(null);
            if (loc == null) break;
            url = URI.create(url).resolve(loc).toString();
            hops.add(hostPath(url));
            r = send("POST", url, headers, bodyBytes);
        }
        return r;
    }

    static String location(HttpResponse<byte[]> r) {
        return r.headers().firstValue("location").orElse("(no location header)");
    }

    static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    static String safeHost(String url) {
        try { return URI.create(url).getHost(); } catch (Exception e) { return "(unparseable)"; }
    }

    /** host + path of a URL — query strings (which may carry tickets) dropped. */
    static String hostPath(String url) {
        try {
            URI u = URI.create(url);
            return (u.getHost() == null ? "(relative)" : u.getHost()) + u.getPath();
        } catch (Exception e) {
            return "(unparseable)";
        }
    }

    static Map<String, String> roarandHeader() {
        Map<String, String> h = new LinkedHashMap<>();
        if (roarand != null) h.put("roarand", roarand);
        return h;
    }

    static void storeCookies(HttpResponse<byte[]> r) {
        for (String sc : r.headers().allValues("set-cookie")) {
            String first = sc.split(";", 2)[0].trim();
            int eq = first.indexOf('=');
            if (eq < 0) continue;
            String name = first.substring(0, eq).trim();
            String val = first.substring(eq + 1).trim();
            String lower = sc.toLowerCase();
            boolean expired = lower.contains("max-age=0") || val.isEmpty()
                    || lower.contains("expires=thu, 01 jan 1970");
            if (expired) cookies.remove(name);
            else {
                cookies.put(name, val);
                everSet.add(name);
            }
        }
    }

    static String cookieHeader() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : cookies.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    static String body(HttpResponse<byte[]> r) {
        return new String(r.body(), StandardCharsets.UTF_8);
    }

    static String ct(HttpResponse<byte[]> r) {
        return r.headers().firstValue("content-type").orElse("(none)");
    }

    static void pace() {
        try { Thread.sleep(1000); } catch (InterruptedException ignored) { }
    }

    // ── time helpers ────────────────────────────────────────────────────────

    static long dayStartMillis(LocalDate day, ZoneId zone) {
        return day.atStartOfDay(zone).toInstant().toEpochMilli();
    }

    static int tzHours(LocalDate day, ZoneId zone) {
        return zone.getRules().getOffset(day.atStartOfDay(zone).toInstant())
                .getTotalSeconds() / 3600;
    }

    // ── series analysis ─────────────────────────────────────────────────────

    static Double toNumber(Object o) {
        if (o instanceof Double) return (Double) o;
        if (o instanceof String) {
            String s = ((String) o).trim();
            if (s.isEmpty() || "--".equals(s)) return null;
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    static int numericCount(List<Object> l) {
        int n = 0;
        for (Object o : l) if (toNumber(o) != null) n++;
        return n;
    }

    static int dashCount(List<Object> l) {
        int n = 0;
        for (Object o : l) if ("--".equals(o)) n++;
        return n;
    }

    static double numericSum(List<Object> l) {
        double s = 0;
        for (Object o : l) { Double d = toNumber(o); if (d != null) s += d; }
        return s;
    }

    static String firstNumeric(List<Object> l) {
        for (Object o : l) if (toNumber(o) != null) return String.valueOf(o);
        return "(none)";
    }

    // ── redaction / formatting ──────────────────────────────────────────────

    static String presence(String s) {
        return s == null ? "MISSING" : "present (len=" + s.length() + ")";
    }

    /** Mask a plant/device id like {@code NE=33554678}: keep shape, hide digits. */
    static String maskId(String id) {
        if (id == null) return "MISSING";
        if (id.length() <= 5) return "***";
        return id.substring(0, 3) + "…" + id.substring(id.length() - 2)
                + " (len=" + id.length() + ")";
    }

    /** Strip anything token-shaped (long alnum runs) from bodies we dump for shape. */
    static String redact(String s) {
        return s.replaceAll("[A-Za-z0-9_\\-]{24,}", "<redacted>");
    }

    static String trunc(String s, int n) {
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() <= n ? s : s.substring(0, n) + " …[+" + (s.length() - n) + " chars]";
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

    static String enc(String s) {
        return java.net.URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    /** JSON string literal (quotes + escapes). */
    static String jstr(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : (s == null ? "" : s).toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    static void verdict(String id, String desc, boolean ok) {
        verdicts.put(id, (ok ? "ok    " : "FAIL  ") + desc);
    }

    // ── minimal JSON (parse only; Maps/Lists/String/Double/Boolean/null) ────

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
            if (d == Math.rint(d)) return String.valueOf((long) d);
        }
        return String.valueOf(o);
    }

    static final class Json {
        private final String s;
        private int i;

        private Json(String s) { this.s = s; }

        /** Returns Map/List/String/Double/Boolean/null, or null when unparseable. */
        static Object tryParse(String s) {
            if (s == null) return null;
            try {
                Json j = new Json(s.trim());
                Object v = j.value();
                return v;
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
            i++; // {
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
            i++; // [
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
        // Verdicts print on every exit path so even an aborted run reports.
        if (!verdicts.isEmpty()) {
            log("================================================================");
            log("ASSUMPTION VERDICTS (see plans/source/huawei.md Phase 0)");
            for (Map.Entry<String, String> e : verdicts.entrySet())
                log("  " + e.getKey() + "  " + e.getValue());
            log("================================================================");
        }
        Path out = Path.of("fusionsolar-phase0-report.txt");
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
        return stdin.readLine();
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
