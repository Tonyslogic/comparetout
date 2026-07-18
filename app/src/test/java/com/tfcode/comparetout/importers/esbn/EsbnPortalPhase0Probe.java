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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manual, interactive diagnostic for the ESB Networks customer-portal scrape —
 * <b>NOT an automated test</b>. It has a {@code main} method and lives in the
 * test source set only because it is a developer tool for {@link ESBNHDFClient}
 * and depends on nothing but the JDK; JUnit never runs it (it declares no
 * {@code @Test}), and it makes real network calls and prompts for real
 * credentials, so it must only ever be launched by hand.
 *
 * <h2>Why it exists</h2>
 * ESB publishes no API, so {@link ESBNHDFClient} signs in by scraping the
 * Azure AD B2C portal the way a browser does — a flow ESB has changed without
 * notice before (the Nov-2024 hardening) and can change again. This probe is
 * the "Phase 0" verification step of {@code plans/source/esbn.md}: before
 * trusting a reworked client, sign in <b>once</b> against a real account, walk
 * every login step plus the HDF download, and record what the live portal
 * actually does so the client's assumptions can be checked against reality.
 * Re-run it whenever a future ESB change is suspected of breaking cloud sync;
 * the report tells you which step drifted.
 *
 * <h2>What it confirms</h2>
 * <ul>
 *   <li>Step 1 — the redirect chain lands on {@code login.esbnetworks.ie} and
 *       {@code var SETTINGS} yields csrf / transId / tenant / policy.</li>
 *   <li>Step 2 — {@code SelfAsserted} returns {@code {"status":"200"}}.</li>
 *   <li>Steps 3–4 — the no-JavaScript {@code form#auto} posts back to the
 *       portal's {@code signin-oidc} and the {@code .AspNetCore.Cookies} auth
 *       cookie is set.</li>
 *   <li>Step 6 — the <b>exact</b> {@code /af/t} response shape (the field the
 *       client reads as the {@code X-Xsrf-Token}).</li>
 *   <li>Step 7 — whether {@code DataHub/DownloadHdfPeriodic} accepts a JSON
 *       body + {@code X-Xsrf-Token} with HTTP 200 (the fix for the historic
 *       400) and returns real HDF CSV.</li>
 *   <li>whether the abandoned {@code datahub/GetHdfContent} endpoint still
 *       exists, and whether the Outages MPRN-scrape selectors still match.</li>
 * </ul>
 *
 * <h2>Login-budget & interstitial capture</h2>
 * Each run performs exactly <b>one</b> login, to stay inside ESB's
 * ~2-logins-per-IP-per-day limit. To capture the human-verification /
 * rate-limit interstitial deliberately, run it a third time within the same
 * day: steps 2/3 will fail the happy-path shape and the tool dumps the
 * (redacted) interstitial body — that capture is what defines
 * {@code ESBNVerificationException} detection.
 *
 * <h2>Safety</h2>
 * Cookies are managed by hand here (a plain name→value jar sent to both
 * {@code esbnetworks.ie} hosts) so the probe does <b>not</b> reproduce the
 * {@code java.net.CookieManager} mis-filing bug the production client had to
 * design around — a probe failure therefore reflects the portal, not the jar.
 * Secrets never reach the report: the password is never logged; csrf and
 * anti-forgery tokens appear only as {@code present (len=N)}; cookies are
 * listed by name; the MPRN is masked. The written report is safe to share.
 *
 * <h2>Running it</h2>
 * Needs JDK 11+ (for {@code java.net.http} and single-file source launch); the
 * Android Studio JBR is JDK 21 and works. The default {@code java} on Windows
 * is often 8, which cannot source-launch — invoke the JBR explicitly:
 * <pre>{@code
 *   "/c/Program Files/Android/Android Studio/jbr/bin/java.exe" \
 *       app/src/test/java/com/tfcode/comparetout/importers/esbn/EsbnPortalPhase0Probe.java
 * }</pre>
 * It prompts for the ESB Networks email, password (hidden when a real console
 * is attached), and — optionally — an MPRN (recommended, since MPRN
 * auto-discovery via the Outages page was found dead in Phase 0; it is on your
 * bill and in the HDF's first column). The redacted report is written to
 * {@code esbn-phase0-report.txt} in the working directory.
 */
public class EsbnPortalPhase0Probe {

    static final String MY = "https://myaccount.esbnetworks.ie/";
    static final String LOGIN = "https://login.esbnetworks.ie";
    static final String UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_3) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36";

    static final Pattern SETTINGS = Pattern.compile(
            "var\\s+SETTINGS\\s*=\\s*(\\{.*?\\})\\s*;", Pattern.DOTALL);

    // Manual cookie jar shared across both hosts (see class javadoc, "Safety").
    static final Map<String, String> cookies = new LinkedHashMap<>();
    // Cookie names we have seen set at any point — for the step-4 auth-cookie check.
    static final Set<String> everSet = new LinkedHashSet<>();

    static final StringBuilder report = new StringBuilder();

    static HttpClient client;

    public static void main(String[] args) throws Exception {
        client = HttpClient.newBuilder()
                // HTTP/1.1, not the default HTTP/2: the portal's front-end
                // mishandles the JDK client's h2 negotiation and drops the
                // connection ("EOF reached while reading").
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER) // we follow by hand to track cookies
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        String user = prompt("ESB Networks email: ");
        String password = promptSecret("ESB Networks password: ");
        String mprnIn = prompt("MPRN (optional; blank = try to discover from Outages page): ");

        log("ESBN Phase 0 probe — " + LocalDateTime.now());
        log("Only ONE login is performed. Report is redacted; safe to share.");
        log("================================================================\n");

        String csrf = null, transId = null, tenant = null, policy = null;

        // ── Step 1: portal root → B2C page; scrape SETTINGS ───────────────
        try {
            HttpResponse<byte[]> r = getFollow(MY);
            String finalHost = r.uri().getHost();
            String html = body(r);
            log("STEP 1  GET " + MY);
            log("  final URL host : " + finalHost
                    + (finalHost.contains("login.esbnetworks.ie") ? "  [ok: on B2C]" : "  [!! expected login.esbnetworks.ie]"));
            log("  final status   : " + r.statusCode());
            log("  cookies so far : " + cookies.keySet());
            String settings = extract(html, SETTINGS);
            if (settings == null) {
                log("  SETTINGS       : !! NOT FOUND — dumping first 400 chars of page:");
                log(indent(trunc(html, 400)));
            } else {
                csrf = jsonStr(settings, "csrf");
                transId = jsonStr(settings, "transId");
                tenant = jsonStr(settings, "tenant");
                policy = jsonStr(settings, "policy");
                log("  SETTINGS.csrf    : " + presence(csrf));
                log("  SETTINGS.transId : " + presence(transId));
                log("  SETTINGS.tenant  : " + (tenant == null ? "MISSING" : tenant));
                log("  SETTINGS.policy  : " + (policy == null ? "MISSING" : policy));
            }
        } catch (Exception e) {
            log("STEP 1  FAILED: " + e);
        }
        log("");

        if (csrf == null || transId == null || tenant == null || policy == null) {
            log("Cannot continue past step 1 without SETTINGS. Stopping.");
            finish();
            return;
        }

        // ── Step 2: POST credentials (XHR-style) ──────────────────────────
        try {
            String url = LOGIN + tenant + "/SelfAsserted?tx=" + enc(transId) + "&p=" + enc(policy);
            String form = "signInName=" + enc(user) + "&password=" + enc(password)
                    + "&request_type=RESPONSE";
            Map<String, String> h = new LinkedHashMap<>();
            h.put("x-csrf-token", csrf);
            h.put("X-Requested-With", "XMLHttpRequest");
            h.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            HttpResponse<byte[]> r = send("POST", url, h, form.getBytes(StandardCharsets.UTF_8));
            String b = body(r);
            log("STEP 2  POST SelfAsserted");
            log("  status         : " + r.statusCode());
            log("  content-type   : " + ct(r));
            boolean looksJson = b.trim().startsWith("{");
            log("  body is JSON   : " + looksJson);
            if (looksJson) {
                log("  JSON keys      : " + jsonKeys(b));
                String status = jsonStr(b, "status");
                log("  status field   : " + status);
                String message = jsonStr(b, "message");
                if (message != null) log("  message field  : " + message);
                if (!"200".equals(status))
                    log("  >> non-200 status → bad credentials OR verification. See message above.");
            } else {
                log("  >> NOT JSON. This is the human-verification/rate-limit interstitial.");
                log("  >> Interstitial body (first 800 chars, redacted):");
                log(indent(trunc(redactHtml(b), 800)));
                log("Stopping (login did not complete).");
                finish();
                return;
            }
        } catch (Exception e) {
            log("STEP 2  FAILED: " + e);
            finish();
            return;
        }
        log("");

        // ── Step 3: confirm → form#auto ───────────────────────────────────
        String actionUrl = null, state = null, clientInfo = null, code = null;
        try {
            String url = LOGIN + tenant + "/api/CombinedSigninAndSignup/confirmed"
                    + "?rememberMe=false&csrf_token=" + enc(csrf)
                    + "&tx=" + enc(transId) + "&p=" + enc(policy);
            HttpResponse<byte[]> r = getFollow(url);
            String html = body(r);
            log("STEP 3  GET CombinedSigninAndSignup/confirmed");
            log("  status         : " + r.statusCode());
            actionUrl = attr(html, "form", "id", "auto", "action");
            if (actionUrl == null) {
                log("  form#auto      : !! MISSING — this is the step-3 verification tell.");
                log("  >> page (first 800 chars, redacted):");
                log(indent(trunc(redactHtml(html), 800)));
                finish();
                return;
            }
            state = hidden(html, "state");
            clientInfo = hidden(html, "client_info");
            code = hidden(html, "code");
            log("  form#auto      : present");
            log("  action host    : " + safeHost(actionUrl)
                    + (safeHost(actionUrl).contains("myaccount.esbnetworks.ie") ? "  [ok: portal]" : "  [!! expected myaccount]"));
            log("  input state       : " + presence(state));
            log("  input client_info : " + presence(clientInfo));
            log("  input code        : " + presence(code));
        } catch (Exception e) {
            log("STEP 3  FAILED: " + e);
            finish();
            return;
        }
        log("");

        // ── Step 4: hand the code back to the portal ──────────────────────
        try {
            String form = "state=" + enc(state) + "&client_info=" + enc(clientInfo)
                    + "&code=" + enc(code);
            Map<String, String> h = new LinkedHashMap<>();
            h.put("Content-Type", "application/x-www-form-urlencoded");
            HttpResponse<byte[]> r = postFollow(actionUrl, h, form.getBytes(StandardCharsets.UTF_8));
            log("STEP 4  POST signin-oidc (form#auto)");
            log("  final status   : " + r.statusCode());
            log("  cookies now    : " + cookies.keySet());
            boolean hasAuth = false;
            for (String name : everSet) if (name.startsWith(".AspNetCore")) hasAuth = true;
            log("  .AspNetCore*   : " + (hasAuth ? "seen [ok]" : "!! never set — auth cookie missing"));
        } catch (Exception e) {
            log("STEP 4  FAILED: " + e);
            finish();
            return;
        }
        log("");

        // ── Step 5: welcome page settles the auth cookie ──────────────────
        try {
            HttpResponse<byte[]> r = getFollow(MY);
            log("STEP 5  GET welcome (myaccount root)");
            log("  final status   : " + r.statusCode());
            log("  final URL host : " + r.uri().getHost());
        } catch (Exception e) {
            log("STEP 5  FAILED: " + e);
        }
        log("");

        // ── Step 6: /af/t anti-forgery token — EXACT SHAPE ────────────────
        String antiForgery = null;
        try {
            HttpResponse<byte[]> r = send("GET", MY + "af/t", null, null);
            String b = body(r);
            log("STEP 6  GET /af/t   (main unknown)");
            log("  status         : " + r.statusCode());
            log("  content-type   : " + ct(r));
            log("  body length    : " + b.length());
            boolean looksJson = b.trim().startsWith("{");
            log("  body is JSON   : " + looksJson);
            if (looksJson) {
                log("  JSON keys      : " + jsonKeys(b));
                antiForgery = jsonStr(b, "token");
                log("  token field    : " + presence(antiForgery)
                        + (antiForgery == null ? "  [!! client expects \"token\"]" : "  [ok]"));
            } else {
                // Might be a bare string. Confirm the real shape.
                log("  >> not a JSON object. First 120 chars (redacted): "
                        + trunc(redactHtml(b), 120));
                if (b.trim().length() > 0 && !b.contains("<")) {
                    antiForgery = b.trim().replaceAll("^\"|\"$", "");
                    log("  >> looks like a bare token string; will try it in step 7.");
                }
            }
        } catch (Exception e) {
            log("STEP 6  FAILED: " + e);
        }
        log("");

        // ── MPRN discovery (Outages scrape) ───────────────────────────────
        String mprn = mprnIn == null ? "" : mprnIn.trim();
        try {
            HttpResponse<byte[]> r = getFollow(MY + "Outages");
            String html = body(r);
            log("MPRN    GET Outages (discovery selector check)");
            log("  status         : " + r.statusCode());
            log("  contains 'MPRN:'      : " + html.contains("MPRN:"));
            log("  contains id=display-data : " + html.contains("display-data"));
            boolean stepClass = Pattern.compile("class\\s*=\\s*['\"][^'\"]*step").matcher(html).find();
            log("  has class~=*step*     : " + stepClass);
            Matcher m = Pattern.compile("\\b(\\d{11})\\b").matcher(html);
            if (m.find()) {
                String found = m.group(1);
                log("  first 11-digit id     : " + maskMprn(found) + "  (looks like an MPRN)");
                if (mprn.isEmpty()) mprn = found;
            } else {
                log("  first 11-digit id     : none found");
            }
        } catch (Exception e) {
            log("MPRN    Outages scrape FAILED: " + e);
        }
        log("");

        // ── Does the old GetHdfContent endpoint still exist? ──────────────
        try {
            String url = MY + "datahub/GetHdfContent?mprn=" + enc(mprn.isEmpty() ? "0" : mprn)
                    + "&startDate=2024-01-01";
            HttpResponse<byte[]> r = send("GET", url, null, null);
            log("LEGACY  GET datahub/GetHdfContent");
            log("  status         : " + r.statusCode()
                    + (r.statusCode() == 404 || r.statusCode() == 410 ? "  [gone — safe to delete]" : ""));
            log("  content-type   : " + ct(r));
        } catch (Exception e) {
            log("LEGACY  GetHdfContent probe FAILED: " + e);
        }
        log("");

        // ── Step 7: DownloadHdfPeriodic — JSON body + X-Xsrf-Token ─────────
        if (mprn.isEmpty()) {
            log("STEP 7  SKIPPED — no MPRN (pass one at the prompt to test the download).");
        } else if (antiForgery == null) {
            log("STEP 7  SKIPPED — no anti-forgery token from step 6.");
        } else {
            try {
                String bodyJson = "{\"mprn\":\"" + mprn + "\",\"searchType\":\"intervalkw\"}";
                Map<String, String> h = new LinkedHashMap<>();
                h.put("Content-Type", "application/json");
                h.put("X-Xsrf-Token", antiForgery);
                HttpResponse<byte[]> r = send("POST", MY + "DataHub/DownloadHdfPeriodic",
                        h, bodyJson.getBytes(StandardCharsets.UTF_8));
                String b = body(r);
                log("STEP 7  POST DataHub/DownloadHdfPeriodic  (the 400-error fix)");
                log("  request body   : {\"mprn\":\"" + maskMprn(mprn) + "\",\"searchType\":\"intervalkw\"}");
                log("  status         : " + r.statusCode()
                        + (r.statusCode() == 200 ? "  [ok]" : "  [!! JSON body/token not accepted]"));
                log("  content-type   : " + ct(r));
                log("  body length    : " + b.length());
                String firstLine = b.split("\\r?\\n", 2)[0];
                boolean looksCsv = firstLine.toUpperCase().contains("MPRN")
                        && firstLine.toUpperCase().contains("READ");
                log("  looks like HDF CSV : " + looksCsv);
                log("  header row     : " + trunc(firstLine, 200));
                long rows = b.lines().count();
                log("  total lines    : " + rows);
                if (!looksCsv) {
                    log("  >> body not CSV — first 300 chars (redacted):");
                    log(indent(trunc(redactHtml(b), 300)));
                }
            } catch (Exception e) {
                log("STEP 7  FAILED: " + e);
            }
        }
        log("");

        log("Reminder: to capture the verification interstitial, re-run a 3rd time");
        log("today — steps 2/3 will fail the happy shape and dump the redacted body.");
        finish();
    }

    // ── HTTP helpers (manual redirect + cookie handling) ────────────────────

    static HttpResponse<byte[]> send(String method, String url, Map<String, String> headers,
                                     byte[] bodyBytes) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", UA)
                // Browser-like defaults; note we do NOT request gzip — the JDK
                // client does not auto-decompress, so an Accept-Encoding of
                // "identity" keeps bodies readable.
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,"
                        + "application/json;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-IE,en;q=0.9")
                .header("Accept-Encoding", "identity");
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

    static HttpResponse<byte[]> getFollow(String url) throws Exception {
        return follow(send("GET", url, null, null), url);
    }

    static HttpResponse<byte[]> postFollow(String url, Map<String, String> h, byte[] body)
            throws Exception {
        return follow(send("POST", url, h, body), url);
    }

    static HttpResponse<byte[]> follow(HttpResponse<byte[]> r, String url) throws Exception {
        int hops = 0;
        while (isRedirect(r.statusCode()) && hops++ < 10) {
            String loc = r.headers().firstValue("location").orElse(null);
            if (loc == null) break;
            url = URI.create(url).resolve(loc).toString();
            r = send("GET", url, null, null);
        }
        return r;
    }

    static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
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

    // ── parsing helpers ─────────────────────────────────────────────────────

    static String extract(String s, Pattern p) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    /** First JSON string value for a key, anywhere in the blob. */
    static String jsonStr(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    static String jsonKeys(String json) {
        Set<String> keys = new LinkedHashSet<>();
        Matcher m = Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:").matcher(json);
        while (m.find()) keys.add(m.group(1));
        return keys.toString();
    }

    /** value of [wantAttr] on a &lt;tag&gt; whose [idAttr] == [idVal]. Quote-agnostic. */
    static String attr(String html, String tag, String idAttr, String idVal, String wantAttr) {
        Matcher m = Pattern.compile("<" + tag + "\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        while (m.find()) {
            String t = m.group();
            if (idVal.equals(attrOf(t, idAttr))) return attrOf(t, wantAttr);
        }
        return null;
    }

    /** Attribute value in EITHER single or double quotes (the portal uses '). */
    static String attrOf(String tag, String name) {
        Matcher m = Pattern.compile("\\b" + name + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')",
                Pattern.CASE_INSENSITIVE).matcher(tag);
        if (!m.find()) return null;
        return m.group(1) != null ? m.group(1) : m.group(2);
    }

    static String hidden(String html, String name) {
        Matcher m = Pattern.compile("<input\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        while (m.find()) {
            String t = m.group();
            if (name.equals(attrOf(t, "name"))) return attrOf(t, "value");
        }
        return null;
    }

    static String safeHost(String url) {
        try { return URI.create(url).getHost(); } catch (Exception e) { return "(unparseable)"; }
    }

    // ── redaction ───────────────────────────────────────────────────────────

    static String presence(String s) {
        return s == null ? "MISSING" : "present (len=" + s.length() + ")";
    }

    static String maskMprn(String m) {
        if (m == null || m.length() < 6) return "***";
        return m.substring(0, 4) + "*****" + m.substring(m.length() - 2);
    }

    /** Strip anything token-shaped (long alnum runs) from HTML we dump for shape. */
    static String redactHtml(String s) {
        String r = s.replaceAll("[A-Za-z0-9_\\-]{24,}", "<redacted>");
        return r.replaceAll("value\\s*=\\s*\"[^\"]{12,}\"", "value=\"<redacted>\"");
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

    static String enc(String s) {
        return java.net.URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    // ── IO ──────────────────────────────────────────────────────────────────

    static void log(String line) {
        System.out.println(line);
        report.append(line).append('\n');
    }

    static void finish() throws IOException {
        Path out = Path.of("esbn-phase0-report.txt");
        Files.writeString(out, report.toString(), StandardCharsets.UTF_8);
        System.out.println("\nReport written to " + out.toAbsolutePath());
    }

    static String prompt(String label) throws IOException {
        System.out.print(label);
        System.out.flush();
        Console c = System.console();
        if (c != null) return c.readLine();
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        return br.readLine();
    }

    static String promptSecret(String label) throws IOException {
        Console c = System.console();
        if (c != null) {
            char[] pw = c.readPassword(label);
            return pw == null ? "" : new String(pw);
        }
        // No console (e.g. IDE run): fall back to visible input.
        System.out.print(label + "(input will be visible) ");
        System.out.flush();
        return new BufferedReader(new InputStreamReader(System.in)).readLine();
    }
}
