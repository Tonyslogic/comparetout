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
 * The login flow and endpoint contract implemented here were reconstructed
 * from the community Python client "FusionSolarPy"
 * (https://github.com/jgriss/FusionSolarPy, Copyright (c) jgriss, MIT
 * License) and adjusted against live-portal observations captured by
 * FusionSolarPhase0Probe (plans/source/huawei.md Phase 0). No source code
 * from that project is included in this file; its MIT notice is reproduced
 * in THIRD_PARTY_LICENSES.md at the repository root.
 */

package com.tfcode.comparetout.importers.fusionsolar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.tfcode.comparetout.importers.fusionsolar.responses.EnergyBalanceResponse;
import com.tfcode.comparetout.importers.fusionsolar.responses.StationListResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Synchronous client for the FusionSolar <b>web-portal</b> API — the
 * unofficial API the portal/app itself uses, chosen over the quota-starved
 * official OpenAPI (plans/source/huawei.md). Worker threads only, matching
 * the SolisCloudClient precedent.
 *
 * <h3>Login and the host split</h3>
 * Accounts live on regional subdomains. Live-portal probing showed the SSO
 * host and the data host can differ ({@code region01eu5} 302s SSO paths to
 * {@code eu5} while {@code /rest/dpcloud/*} exists only on the region host),
 * so the client tracks them separately: the SSO host is adopted from
 * wherever {@code /unisso/pubkey} lands, and the data host is resolved after
 * login by scoring {@code keep-alive} across the candidates — the region host
 * named in the login response first (it is told to us, not guessed), then the
 * entry host and the landed SSO host. The resolved data host is exposed for
 * persistence so later runs start on the right region.
 *
 * <p>Login itself: {@code GET /unisso/pubkey} → RSA-OAEP(SHA-384, MGF1-SHA384)
 * encrypt the password + append the pubkey {@code version} suffix →
 * {@code POST /unisso/v3/validateUser.action?timeStamp&nonce}. Its
 * {@code errorCode} vocabulary, as settled by live probing:
 * <ul>
 *   <li>{@code 0}/absent — signed in on this host already.</li>
 *   <li><b>{@code 470} — success, not failure</b>: the credentials were
 *       accepted and the account belongs to another region.
 *       {@code respMultiRegionName} carries a single-use CAS service ticket
 *       and the region host ({@link MultiRegionHop}); GETting that path there
 *       is what mints the session. This is the ordinary path for a real
 *       account, so nothing about it is exceptional.</li>
 *   <li>{@code 406} — genuinely bad username/password.</li>
 * </ul>
 * A captcha demand is signalled by {@code verifyCodeCreate} and throws
 * {@link FusionSolarCaptchaRequiredException} carrying the captcha image for
 * the credential sheet; a captcha code is only ever sent when the portal
 * asked for one. Bad credentials throw {@link FusionSolarAuthException}. The
 * session lives in cookies (shared across {@code *.fusionsolar.huawei.com});
 * data calls echo the keep-alive payload back as the {@code roarand} header
 * (CSRF).
 *
 * <h3>Redirects</h3>
 * The portal routes with redirects, including on POSTs. OkHttp's transparent
 * following would convert a redirected POST to a GET (and lose the body), so
 * redirects are followed manually and POST bodies are re-sent to the target.
 *
 * <h3>Pacing and errors</h3>
 * No documented quota (it is the portal's own API) — be more polite than
 * required: a global minimum of 1 second between any two requests. Transient
 * failures (I/O, HTTP 5xx, malformed body) are retried in-call with
 * 5s/10s/20s/40s backoff, at most {@value #MAX_ATTEMPTS} attempts, then
 * surface as {@link FusionSolarException} for the worker to convert into
 * {@code Result.retry()}. A dead session on a data call triggers exactly one
 * unattended re-login; if that re-login demands a captcha, the distinct
 * {@link FusionSolarCaptchaRequiredException} reaches the worker's
 * "sign in again" notification path.
 */
public class FusionSolarClient {

    static final String DOMAIN = ".fusionsolar.huawei.com";
    public static final String DEFAULT_SUBDOMAIN = "region01eu5";

    private static final MediaType JSON = MediaType.parse("application/json");
    private static final long MIN_REQUEST_INTERVAL_MS = 1000L;
    private static final int MAX_ATTEMPTS = 5;
    private static final long[] BACKOFF_MS = {5_000L, 10_000L, 20_000L, 40_000L};
    private static final int MAX_REDIRECTS = 6;
    private static final int PAGE_SIZE = 50;
    /** validateUser's "credentials good, claim your session on your region". */
    private static final String ERROR_MULTI_REGION = "470";

    private final String mUsername;
    private final String mPassword;
    private final OkHttpClient mHttpClient;
    private final Gson mGson = new Gson();
    /** Cookies shared across every portal host, like the browser does. */
    private final Map<String, Cookie> mCookies = new LinkedHashMap<>();

    private String mSsoBase;
    private String mDataBase;
    /** Non-null only in tests: every portal host collapses onto this base. */
    private String mCollapsedBase;
    private String mRoarand;
    private boolean mLoggedIn = false;
    private long mLastRequestAt = 0L;

    /**
     * @param host the region host: a bare subdomain ("region01eu5"), a full
     *             host name, or null/empty for the default EU host
     */
    public FusionSolarClient(String username, String password, @Nullable String host) {
        mUsername = username;
        mPassword = password;
        String base = baseFor(host);
        mSsoBase = base;
        mDataBase = base;
        mHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                // Redirects are followed manually: OkHttp would turn a
                // redirected POST into a GET, which breaks validateUser.
                .followRedirects(false)
                .retryOnConnectionFailure(false)
                .cookieJar(new SharedCookieJar())
                .build();
    }

    /** Package-private: unit tests point both hosts at a MockWebServer. */
    FusionSolarClient(String username, String password, String ssoBase, String dataBase) {
        this(username, password, (String) null);
        mSsoBase = trimSlash(ssoBase);
        mDataBase = trimSlash(dataBase);
        mCollapsedBase = mDataBase;
    }

    private static String baseFor(@Nullable String host) {
        if (null == host || host.trim().isEmpty()) host = DEFAULT_SUBDOMAIN;
        host = host.trim();
        if (host.startsWith("https://") || host.startsWith("http://")) return trimSlash(host);
        if (!host.contains(".")) host = host + DOMAIN;
        return "https://" + host;
    }

    private static String trimSlash(String base) {
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /**
     * The host name data calls resolved to (e.g. "region01eu5.fusionsolar.huawei.com")
     * — persist it so the next run starts on the account's own region.
     */
    public String getResolvedHost() {
        return URI.create(mDataBase).getHost();
    }

    // ── login ───────────────────────────────────────────────────────────────

    /** Login without a captcha code — see {@link #login(String)}. */
    public void login() throws FusionSolarException {
        login(null);
    }

    /**
     * Full portal login. Establishes the session cookies, resolves the data
     * host and captures the first {@code roarand}.
     *
     * @param verifycode a captcha code typed by the user, when re-submitting
     *                   after {@link FusionSolarCaptchaRequiredException}
     */
    public void login(@Nullable String verifycode) throws FusionSolarException {
        mLoggedIn = false;
        mRoarand = null;

        // 1. pubkey — adopting whatever host the portal lands us on (the
        //    login redirect is how accounts are routed to their region).
        JsonObject pubkey = fetchPubkey();
        String pem = stringOf(pubkey.get("pubKey"));
        String version = stringOf(pubkey.get("version"));
        String timeStamp = stringOf(pubkey.get("timeStamp"));
        boolean enableEncrypt = !Boolean.FALSE.equals(booleanOf(pubkey.get("enableEncrypt")));
        if (null == pem)
            throw new FusionSolarAuthException("FusionSolar pubkey response had no key");

        // 2. validateUser with the encrypted password.
        JsonObject body = new JsonObject();
        body.addProperty("organizationName", "");
        body.addProperty("username", mUsername);
        body.addProperty("password", enableEncrypt
                ? encryptPassword(pem, version, mPassword) : mPassword);
        if (null != verifycode && !verifycode.isEmpty())
            body.addProperty("verifycode", verifycode);
        String qs = null == timeStamp ? ""
                : "?timeStamp=" + urlEncode(timeStamp) + "&nonce=" + urlEncode(timeStamp);

        RawResponse response = postWithRetry(mSsoBase + "/unisso/v3/validateUser.action" + qs,
                mGson.toJson(body), null);
        mSsoBase = response.landedBase;
        JsonObject login = asObject(response.body);
        if (null == login)
            throw new FusionSolarAuthException(
                    "FusionSolar login response was not JSON (HTTP " + response.code + ")");

        String errorCode = stringOf(login.get("errorCode"));
        // 470 is NOT a rejection: it is "credentials accepted, now claim your
        // session on your own region host", and the CAS service ticket to do
        // it with rides along in respMultiRegionName. Treating it as a failure
        // (as this client first did) locks out every multi-region account.
        MultiRegionHop hop = parseMultiRegion(login.get("respMultiRegionName"));
        boolean multiRegion = ERROR_MULTI_REGION.equals(errorCode) && null != hop;
        boolean failed = response.code != 200 || (!multiRegion
                && null != errorCode && !errorCode.isEmpty() && !"0".equals(errorCode));
        if (failed) {
            if (Boolean.TRUE.equals(booleanOf(login.get("verifyCodeCreate"))))
                throw new FusionSolarCaptchaRequiredException(
                        "FusionSolar demands a captcha to sign in", fetchCaptchaQuietly());
            throw new FusionSolarAuthException("FusionSolar rejected the login (errorCode="
                    + errorCode + " " + stringOf(login.get("errorMsg")) + ")");
        }

        // 3. Session establishment: consume the multi-region ticket on the
        //    region host when there is one, else follow the portal's own next
        //    hop. (/unisess/v1/auth is a last-resort guess — live probing only
        //    ever saw it 404, so it is never the preferred path.)
        String authUrl;
        if (multiRegion) {
            authUrl = regionBase(hop.host) + hop.path;
        } else {
            String redirectURL = stringOf(login.get("redirectURL"));
            authUrl = null != redirectURL
                    ? URI.create(mSsoBase + "/").resolve(redirectURL).toString()
                    : mSsoBase + "/unisess/v1/auth?service="
                            + urlEncode("/netecowebext/home/index.html");
        }
        getWithRetry(authUrl, null);

        // 4. Data host: score keep-alive across the candidates; the host that
        //    answers with a payload is where /rest/* lives for this account.
        //    The multi-region host is tried first — it is told to us, not
        //    guessed, so it is the account's real region by construction.
        Set<String> candidates = new LinkedHashSet<>();
        if (multiRegion) candidates.add(regionBase(hop.host));
        candidates.add(mDataBase);
        candidates.add(mSsoBase);
        JsonElement regions = login.get("respMultiRegionName");
        if (null != regions && regions.isJsonArray())
            for (JsonElement region : regions.getAsJsonArray()) {
                String name = stringOf(region);
                if (null != name && name.matches("[a-z0-9]+"))
                    candidates.add("https://" + name + DOMAIN);
            }
        String payloadHost = null, aliveHost = null;
        for (String candidate : candidates) {
            String roarand = tryKeepAlive(candidate);
            if (null != roarand) {
                payloadHost = candidate;
                mRoarand = roarand;
                break;
            }
            if (null == aliveHost && null != mLastKeepAliveCode) aliveHost = candidate;
        }
        if (null != payloadHost) mDataBase = payloadHost;
        else if (null != aliveHost) mDataBase = aliveHost;
        else throw new FusionSolarException(
                    "FusionSolar session did not establish on any region host");
        mLoggedIn = true;
    }

    /**
     * The region hop carried by {@code respMultiRegionName} alongside
     * {@code errorCode 470}. Observed live as a three-element array whose
     * order is not contracted anywhere, so each element is classified by
     * shape rather than by index:
     * <pre>
     *   [ "-5",
     *     "/rest/dp/web/v1/auth/on-sso-credential-ready?ticket=ST-…&amp;regionName=region004",
     *     "uni004eu5.fusionsolar.huawei.com&amp;&amp;TGTX--F…" ]
     * </pre>
     * The path carries a single-use CAS service ticket; GETting it on the
     * host from the third element is what converts the validated credentials
     * into session cookies. The trailing {@code &&TGT…} is the ticket-granting
     * ticket, which the portal also sets as a cookie — it is stripped here and
     * not otherwise used.
     */
    static final class MultiRegionHop {
        final String host;
        final String path;

        private MultiRegionHop(String host, String path) {
            this.host = host;
            this.path = path;
        }
    }

    /**
     * Pull the region hop out of a login response's {@code respMultiRegionName}.
     * The host is accepted only if we trust it: the ticket is a session
     * credential, so it is never sent anywhere but Huawei's own domain.
     *
     * @return the hop, or null when absent, unrecognised or untrusted
     */
    @Nullable
    MultiRegionHop parseMultiRegion(@Nullable JsonElement regions) {
        if (null == regions || !regions.isJsonArray()) return null;
        String host = null, path = null;
        for (JsonElement element : regions.getAsJsonArray()) {
            String value = stringOf(element);
            if (null == value) continue;
            value = value.trim();
            if (null == path && value.startsWith("/") && value.contains("ticket="))
                path = value;
            // The host element is "host&&TGT…"; the TGT is not ours to use.
            if (null == host) {
                String candidate = value.split("&&", 2)[0].trim();
                if (isTrustedPortalHost(candidate)) host = candidate;
            }
        }
        if (null == host || null == path) return null;
        return new MultiRegionHop(host, path);
    }

    private boolean isTrustedPortalHost(String host) {
        if (host.endsWith(DOMAIN)) return true;
        // Tests collapse every portal host onto one local MockWebServer.
        return null != mCollapsedBase && host.equals(URI.create(mCollapsedBase).getHost());
    }

    /** Where to send the region ticket. */
    private String regionBase(String host) {
        return null != mCollapsedBase ? mCollapsedBase : "https://" + host;
    }

    private JsonObject fetchPubkey() throws FusionSolarException {
        RawResponse response = getWithRetry(mSsoBase + "/unisso/pubkey", null);
        JsonObject pubkey = asObject(response.body);
        if (null == pubkey || null == pubkey.get("pubKey")) {
            // Landed on an HTML login page after a cross-host redirect —
            // adopt the landed host and ask it directly (probe finding A1).
            if (!response.landedBase.equals(mSsoBase)) {
                mSsoBase = response.landedBase;
                response = getWithRetry(mSsoBase + "/unisso/pubkey", null);
                pubkey = asObject(response.body);
            }
        } else {
            mSsoBase = response.landedBase;
        }
        if (null == pubkey)
            throw new FusionSolarException("FusionSolar pubkey endpoint did not return JSON");
        return pubkey;
    }

    /** The captcha image bytes, for the credential sheet's retry round-trip. */
    @Nullable
    public byte[] fetchCaptcha() throws FusionSolarException {
        RawResponse response = getWithRetry(mSsoBase
                + "/unisso/verifycode?timestamp=" + System.currentTimeMillis(), null);
        if (response.code == 200 && null != response.contentType
                && response.contentType.startsWith("image")) return response.rawBody;
        return null;
    }

    @Nullable
    private byte[] fetchCaptchaQuietly() {
        try {
            return fetchCaptcha();
        } catch (FusionSolarException e) {
            return null;
        }
    }

    // ── session maintenance ─────────────────────────────────────────────────

    private String mLastKeepAliveCode;

    /** keep-alive on [base]; returns the roarand payload, or null. */
    @Nullable
    private String tryKeepAlive(String base) {
        mLastKeepAliveCode = null;
        try {
            RawResponse response = getWithRetry(base + "/rest/dpcloud/auth/v1/keep-alive", null);
            JsonObject json = asObject(response.body);
            if (null == json) return null;
            mLastKeepAliveCode = stringOf(json.get("code"));
            return stringOf(json.get("payload"));
        } catch (FusionSolarException e) {
            return null;
        }
    }

    /**
     * A session-scoped call wrapper: on evidence of a dead session the client
     * re-logins once (unattended — a captcha demand escapes as its own
     * exception) and replays the call.
     */
    private RawResponse sessionCall(Call call) throws FusionSolarException {
        if (!mLoggedIn) login();
        RawResponse response = call.execute();
        if (!looksLoggedOut(response)) return response;
        login();
        response = call.execute();
        if (looksLoggedOut(response))
            throw new FusionSolarException("FusionSolar session died and re-login did not stick");
        return response;
    }

    /** A data response that is really the SSO login page / bad-session code. */
    private boolean looksLoggedOut(RawResponse response) {
        if (null != response.landedPath && response.landedPath.contains("/unisso/")) return true;
        JsonObject json = asObject(response.body);
        if (null == json) return null != response.contentType
                && response.contentType.contains("text/html");
        String code = stringOf(json.get("code"));
        return "1201".equals(code);
    }

    private interface Call {
        RawResponse execute() throws FusionSolarException;
    }

    private Map<String, String> roarandHeader() {
        Map<String, String> headers = new LinkedHashMap<>();
        if (null != mRoarand) headers.put("roarand", mRoarand);
        return headers;
    }

    // ── endpoints ───────────────────────────────────────────────────────────

    /**
     * All plants under the account, across pagination. Doubles as the
     * credential probe: bad credentials throw FusionSolarAuthException (or
     * FusionSolarCaptchaRequiredException) out of the implicit login.
     */
    public List<StationListResponse.Station> getStationList() throws FusionSolarException {
        List<StationListResponse.Station> all = new ArrayList<>();
        int curPage = 1;
        while (true) {
            final String body = "{\"curPage\":" + curPage + ",\"pageSize\":" + PAGE_SIZE + ","
                    + "\"gridConnectedTime\":\"\",\"queryTime\":" + System.currentTimeMillis()
                    + ",\"timeZone\":0,\"sortId\":\"createTime\",\"sortDir\":\"DESC\","
                    + "\"locale\":\"en_US\"}";
            RawResponse response = sessionCall(() -> postWithRetry(
                    mDataBase + "/rest/pvms/web/station/v1/station/station-list",
                    body, roarandHeader()));
            StationListResponse page = parse(response.body, StationListResponse.class,
                    "station-list");
            if (null == page || Boolean.FALSE.equals(page.success))
                throw new FusionSolarException("FusionSolar station-list failed (failCode="
                        + (null == page ? "unparseable" : page.failCode) + ")");
            List<StationListResponse.Station> stations = page.stations();
            all.addAll(stations);
            if (stations.size() < PAGE_SIZE) break;
            curPage++;
        }
        return all;
    }

    /**
     * One day's 5-minute energy balance for one plant. {@code queryTime} is
     * local midnight of [day] in [zone]; {@code timeZone} is the zone's UTC
     * offset in hours on that date (DST-correct per day).
     *
     * @param dn the plant's device name from the station list (e.g. "NE=…")
     */
    public EnergyBalanceResponse getEnergyBalance(String dn, LocalDate day, ZoneId zone)
            throws FusionSolarException {
        long queryTime = day.atStartOfDay(zone).toInstant().toEpochMilli();
        int tzHours = zone.getRules().getOffset(day.atStartOfDay(zone).toInstant())
                .getTotalSeconds() / 3600;
        final String url = mDataBase + "/rest/pvms/web/station/v1/overview/energy-balance"
                + "?stationDn=" + urlEncode(dn)
                + "&timeDim=2"
                + "&queryTime=" + queryTime
                + "&timeZone=" + tzHours
                + "&timeZoneStr=" + urlEncode(zone.getId())
                + "&_=" + System.currentTimeMillis();
        RawResponse response = sessionCall(() -> getWithRetry(url, roarandHeader()));
        EnergyBalanceResponse balance = parse(response.body, EnergyBalanceResponse.class,
                "energy-balance");
        if (null == balance || Boolean.FALSE.equals(balance.success))
            throw new FusionSolarException("FusionSolar energy-balance failed (failCode="
                    + (null == balance ? "unparseable" : balance.failCode) + ")");
        return balance;
    }

    // ── crypto ──────────────────────────────────────────────────────────────

    /**
     * base64(RSA-OAEP-SHA384(password)) + pubkey version suffix. The JCE
     * transformation string alone defaults MGF1 to SHA-1, so the parameter
     * spec pins MGF1-SHA384 explicitly (FusionSolarPy behaviour).
     * Package-private and deterministic-shaped for unit tests.
     */
    static String encryptPassword(String pem, @Nullable String version, String password)
            throws FusionSolarException {
        try {
            String b64 = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
            byte[] der = Base64.getMimeDecoder().decode(b64);
            PublicKey key = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(der));
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-384AndMGF1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new OAEPParameterSpec("SHA-384", "MGF1",
                    MGF1ParameterSpec.SHA384, PSource.PSpecified.DEFAULT));
            byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted)
                    + (null == version ? "" : version);
        } catch (Exception e) {
            throw new FusionSolarException("Password encryption failed: " + e.getMessage());
        }
    }

    // ── HTTP plumbing ───────────────────────────────────────────────────────

    /** One completed exchange after manual redirect following. */
    private static class RawResponse {
        int code;
        String contentType;
        String body;
        byte[] rawBody;
        String landedBase;
        String landedPath;
    }

    private RawResponse getWithRetry(String url, @Nullable Map<String, String> headers)
            throws FusionSolarException {
        return withRetry(() -> executeOnce("GET", url, headers, null));
    }

    private RawResponse postWithRetry(String url, String jsonBody,
                                      @Nullable Map<String, String> headers)
            throws FusionSolarException {
        byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        return withRetry(() -> executeOnce("POST", url, headers, bodyBytes));
    }

    private interface Attempt {
        RawResponse execute() throws FusionSolarException;
    }

    private RawResponse withRetry(Attempt attempt) throws FusionSolarException {
        FusionSolarException lastTransient = null;
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            if (i > 0) {
                try {
                    Thread.sleep(BACKOFF_MS[Math.min(i - 1, BACKOFF_MS.length - 1)]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new FusionSolarException("Interrupted while backing off");
                }
            }
            try {
                return attempt.execute();
            } catch (FusionSolarAuthException | FusionSolarCaptchaRequiredException fatal) {
                throw fatal;
            } catch (FusionSolarException transientFailure) {
                // No android.util.Log — the class stays JVM-pure so the
                // login/envelope unit tests run without the Android runtime.
                lastTransient = transientFailure;
            }
        }
        throw new FusionSolarException("FusionSolar unreachable after " + MAX_ATTEMPTS
                + " attempts: " + (null == lastTransient ? "unknown" : lastTransient.getMessage()));
    }

    /**
     * One request with manual redirect following: GETs re-GET the target,
     * POSTs re-POST the same body (302-as-GET would 404 on the portal).
     * HTTP 5xx throws transient; everything else is returned for the caller
     * to interpret.
     */
    private RawResponse executeOnce(String method, String url,
                                    @Nullable Map<String, String> headers,
                                    @Nullable byte[] bodyBytes) throws FusionSolarException {
        String currentUrl = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            pace();
            Request.Builder builder = new Request.Builder().url(currentUrl)
                    .header("Accept", "application/json, text/plain, */*");
            if (null != headers)
                for (Map.Entry<String, String> header : headers.entrySet())
                    builder.header(header.getKey(), header.getValue());
            if ("POST".equals(method)) {
                builder.header("Content-Type", "application/json");
                builder.post(RequestBody.create(
                        null == bodyBytes ? new byte[0] : bodyBytes, JSON));
            }
            try (Response response = mHttpClient.newCall(builder.build()).execute()) {
                if (response.isRedirect()) {
                    String location = response.header("Location");
                    if (null == location)
                        throw new FusionSolarException("Redirect without Location for " + url);
                    currentUrl = URI.create(currentUrl).resolve(location).toString();
                    continue;
                }
                if (response.code() >= 500)
                    throw new FusionSolarException(
                            "FusionSolar HTTP " + response.code() + " for " + url);
                RawResponse raw = new RawResponse();
                raw.code = response.code();
                raw.contentType = response.header("Content-Type");
                ResponseBody body = response.body();
                raw.rawBody = null == body ? new byte[0] : body.bytes();
                raw.body = new String(raw.rawBody, StandardCharsets.UTF_8);
                URI landed = URI.create(currentUrl);
                raw.landedBase = landed.getScheme() + "://" + landed.getAuthority();
                raw.landedPath = landed.getPath();
                return raw;
            } catch (IOException ioe) {
                throw new FusionSolarException("Network error for " + url + ": "
                        + ioe.getMessage());
            }
        }
        throw new FusionSolarException("Redirect loop for " + url);
    }

    /** Global pacing: at least 1s between any two requests (portal courtesy). */
    private void pace() throws FusionSolarException {
        long wait = mLastRequestAt + MIN_REQUEST_INTERVAL_MS - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new FusionSolarException("Interrupted while pacing requests");
            }
        }
        mLastRequestAt = System.currentTimeMillis();
    }

    /**
     * Browser-like cookie sharing: the portal's session cookies are
     * domain-scoped and must survive the cross-region redirects, so every
     * cookie is offered to every portal host (and to the single mock host in
     * tests).
     */
    private class SharedCookieJar implements CookieJar {
        @Override
        public void saveFromResponse(@NonNull HttpUrl url, @NonNull List<Cookie> cookies) {
            for (Cookie cookie : cookies) mCookies.put(cookie.name(), cookie);
        }

        @NonNull
        @Override
        public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
            List<Cookie> matched = new ArrayList<>();
            for (Cookie cookie : mCookies.values())
                matched.add(new Cookie.Builder()
                        .name(cookie.name()).value(cookie.value())
                        .domain(url.host()).build());
            return matched;
        }
    }

    // ── small helpers ───────────────────────────────────────────────────────

    private <T> T parse(String body, Class<T> type, String what) throws FusionSolarException {
        try {
            return mGson.fromJson(body, type);
        } catch (JsonSyntaxException jse) {
            throw new FusionSolarException("Malformed FusionSolar " + what + " response");
        }
    }

    @Nullable
    private JsonObject asObject(String body) {
        try {
            JsonElement element = mGson.fromJson(body, JsonElement.class);
            return null != element && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (JsonSyntaxException jse) {
            return null;
        }
    }

    @Nullable
    private static String stringOf(@Nullable JsonElement element) {
        if (null == element || !element.isJsonPrimitive()) return null;
        return element.getAsString();
    }

    @Nullable
    private static Boolean booleanOf(@Nullable JsonElement element) {
        if (null == element || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isBoolean()) return null;
        return element.getAsBoolean();
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 unavailable", e);
        }
    }
}
