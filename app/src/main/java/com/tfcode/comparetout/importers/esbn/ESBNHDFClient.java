/*
 * Copyright (c) 2024-2026. Tony Finnerty
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
 * The post-Nov-2024 portal flow implemented here (welcome-page settle,
 * /af/t anti-forgery token, JSON-body HDF download) was verified against the
 * protocol documented by badger707/esb-smart-meter-reading-automation. That
 * project carries no license, so it was used strictly as documentation of the
 * live portal's behaviour — no code from it is included here.
 */

package com.tfcode.comparetout.importers.esbn;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.tfcode.comparetout.importers.esbn.responses.AntiForgeryTokenResponse;
import com.tfcode.comparetout.importers.esbn.responses.ESBNAuthException;
import com.tfcode.comparetout.importers.esbn.responses.ESBNException;
import com.tfcode.comparetout.importers.esbn.responses.ESBNVerificationException;
import com.tfcode.comparetout.importers.esbn.responses.LoginResponse;
import com.tfcode.comparetout.importers.esbn.responses.SettingsResponse;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Scrapes the ESB Networks customer portal the way a browser does (there is
 * no public API): Azure AD B2C login, then the Harmonised Data File (HDF)
 * download. EXPERIMENTAL by nature — ESB has changed this flow before
 * (Nov-2024 hardening) and can again without notice; the file-import path
 * ({@link #readEntriesFromFile}) is the always-works fallback.
 * <p>
 * The eight steps (plans/source/esbn.md §1):
 * <ol>
 *   <li>GET the portal root, follow redirects to the B2C page, scrape
 *       {@code var SETTINGS} for csrf/transId/tenant/policy;</li>
 *   <li>POST credentials to {@code SelfAsserted} (XHR-style);</li>
 *   <li>GET {@code CombinedSigninAndSignup/confirmed} → the no-JS
 *       {@code form#auto};</li>
 *   <li>POST that form back to the portal ({@code signin-oidc});</li>
 *   <li>GET the portal welcome page to settle the auth cookie;</li>
 *   <li>GET {@code /af/t} → the portal anti-forgery token;</li>
 *   <li>POST {@code DataHub/DownloadHdfPeriodic} with a JSON body and
 *       {@code X-Xsrf-Token} → the HDF CSV;</li>
 *   <li>parse the CSV ({@link #processHDF}).</li>
 * </ol>
 * Login budget is ~2 clean logins per IP per day (Nov-2024 hardening) —
 * callers must treat {@link ESBNVerificationException} as fatal and never
 * retry automatically.
 */
public class ESBNHDFClient {

    private static final String MY_ACCOUNT_URL = "https://myaccount.esbnetworks.ie/";
    private static final String LOGIN_URL = "https://login.esbnetworks.ie";
    private static final String LOGIN_URL_SUFFIX = "/SelfAsserted";
    private static final String LOGIN_CONFIRM = "/api/CombinedSigninAndSignup/confirmed";
    private static final String FETCH_HDF_URL = "DataHub/DownloadHdfPeriodic";
    private static final String TOKEN_URL = "af/t";
    private static final String FETCH_MPRN_URL = "Outages";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36";

    private static final int MPRN_COL = 0;
    private static final int READ_VALUE = 2;
    private static final int READ_TYPE = 3;
    private static final int READ_DATETIME = 4;
    private static final String EXPORT_READ_PATTERN = ".*Export.*";
    private static final String READ_KWH_PATTERN = ".*\\(kWh\\)";
    private static final DateTimeFormatter HDF_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    // Tolerates minification: the assignment may share a line with the rest
    // of the script, so match across newlines up to the first "};" — the
    // nested objects inside SETTINGS close with "}," or "}}", never "};".
    private static final Pattern SETTINGS_PATTERN =
            Pattern.compile("var\\s+SETTINGS\\s*=\\s*(\\{.*?\\})\\s*;", Pattern.DOTALL);

    private String mprn;
    private final String user;
    private final String password;
    private final String mMyAccountUrl;
    private final String mLoginUrl;

    private boolean mLoggedIn = false;

    private final OkHttpClient mClient;
    private final OkHttpClient mNoRedirectClient;

    private SettingsResponse mSettings = null;
    private NoJavaScript mNoJavaScript = null;
    private String mXsrfToken = null;

    public ESBNHDFClient(String user, String password) {
        this(user, password, MY_ACCOUNT_URL, LOGIN_URL);
    }

    /** Package-private: unit tests point the client at MockWebServers. */
    ESBNHDFClient(String user, String password, String myAccountUrl, String loginUrl) {
        this.user = user;
        this.password = password;
        this.mMyAccountUrl = myAccountUrl;
        this.mLoginUrl = loginUrl;

        // One shared in-memory jar; OkHttp's own Cookie parsing/matching
        // handles the cases the old CookieManager plumbing mis-filed
        // (leading-dot names like .AspNetCore.Cookies, host-vs-domain
        // matching across login. and myaccount.).
        CookieJar jar = new InMemoryCookieJar();

        mClient = new OkHttpClient.Builder()
                .cookieJar(jar)
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        mNoRedirectClient = new OkHttpClient.Builder()
                .cookieJar(jar)
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(false)
                .build();
    }

    /**
     * Store-on-response / host-match-on-request cookie jar. Keyed by the
     * cookie's domain so a re-issued cookie (same name) overwrites its
     * predecessor; expired re-issues act as deletions.
     */
    static class InMemoryCookieJar implements CookieJar {
        private final Map<String, Map<String, Cookie>> mStore = new HashMap<>();

        @Override
        public synchronized void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            for (Cookie cookie : cookies) {
                Map<String, Cookie> byName = mStore.get(cookie.domain());
                if (null == byName) {
                    byName = new HashMap<>();
                    mStore.put(cookie.domain(), byName);
                }
                if (cookie.expiresAt() < System.currentTimeMillis())
                    byName.remove(cookie.name());
                else byName.put(cookie.name(), cookie);
            }
        }

        @Override
        public synchronized List<Cookie> loadForRequest(HttpUrl url) {
            List<Cookie> matched = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (Map<String, Cookie> byName : mStore.values())
                for (Cookie cookie : byName.values())
                    if (cookie.expiresAt() >= now && cookie.matches(url))
                        matched.add(cookie);
            return matched;
        }
    }

    public static String readEntriesFromFile(InputStream inputStream, ESBNImportExportEntry processor)
            throws ESBNException {
        String mprnFromFile;
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            CSVReader csvReader = new CSVReader(reader);
            mprnFromFile = processHDF(processor, csvReader);
        } catch (IOException e) {
            throw new ESBNException("Unable to read file. Is the file valid?.");
        } catch (DateTimeParseException | NumberFormatException e) {
            throw new ESBNException("Unable to parse file data. Is the file valid?.");
        }
        return mprnFromFile;
    }

    /**
     * Drop every entry belonging to the latest calendar date present —
     * unconditionally, not by row-counting (a trailing day that looks full
     * at download time can still be revised server-side). Cloud-fetch
     * ingestion only; the user-driven file import keeps what the file holds.
     */
    public static <T> void pruneLatestDay(Map<LocalDateTime, T> entries) {
        LocalDate latest = null;
        for (LocalDateTime key : entries.keySet()) {
            LocalDate day = key.toLocalDate();
            if (null == latest || day.isAfter(latest)) latest = day;
        }
        if (null == latest) return;
        final LocalDate latestDay = latest;
        entries.entrySet().removeIf(entry -> entry.getKey().toLocalDate().equals(latestDay));
    }

    public void setSelectedMPRN(String mprn) {
        this.mprn = mprn;
    }

    /**
     * The MPRNs on the account, scraped from the Outages page. Doubles as the
     * credential probe: performs one login when not already logged in, so a
     * successful return (even empty) means the credentials are good.
     * <p>
     * MPRN discovery is BEST-EFFORT: Phase 0 (2026-07-18) found the Outages
     * page now answers GET with HTTP 405, and it had already broken once
     * before. Rather than fail the login over a dead scrape, a non-200 page
     * or an unparseable one yields an empty list — the MPRN is carried in the
     * HDF itself (column 0), so a one-off file import seeds it and cloud sync
     * then works for that MPRN. Only genuine login failures (verification /
     * bad credentials, thrown from {@link #logIn()}) propagate.
     */
    public List<String> fetchMPRNs() throws ESBNException {
        List<String> ret = new ArrayList<>();
        if (!mLoggedIn) logInWrappingIO();

        Request mprnPageRequest = new Request.Builder()
                .url(mMyAccountUrl + FETCH_MPRN_URL)
                .header("User-Agent", USER_AGENT)
                .build();

        try (Response mprnPageResponse = mClient.newCall(mprnPageRequest).execute()) {
            if (mprnPageResponse.code() != 200) return ret; // scrape dead → best-effort empty
            ResponseBody mprnBody = mprnPageResponse.body();
            String mprnPageHtml = (null == mprnBody) ? "" : mprnBody.string();

            Document doc = Jsoup.parse(mprnPageHtml);
            // card-body elements containing "MPRN:" text, value in a sibling
            // p#display-data. Kept for the day ESB restores the page.
            Elements mprnCardBodies = doc.select("[class~=.*step.*]:containsOwn(MPRN:)");
            for (Element cardBody : mprnCardBodies) {
                Element parent = cardBody.parent();
                if (parent == null) continue;
                Element mprnElement = parent.select("p#display-data").first();
                if (mprnElement != null) ret.add(mprnElement.text());
            }
        } catch (IOException e) {
            // Logged in already; MPRN discovery is best-effort — don't fail
            // the probe over a network hiccup on this one page.
            return ret;
        }
        return ret;
    }

    private void logInWrappingIO() throws ESBNException {
        try {
            logIn();
        } catch (IOException e) {
            mLoggedIn = false;
            throw new ESBNException("IO issue. Consider using file or try again later.");
        }
    }

    /**
     * Download and parse the full HDF for the selected MPRN — one login (if
     * needed) plus one download POST per call.
     */
    public void fetchSmartMeterDataHDF(ESBNImportExportEntry processor) throws ESBNException {
        try {
            if (!mLoggedIn) logIn();
        } catch (IOException e) {
            throw new ESBNException("IO issue. Consider using file or try again later.");
        }

        // JSON body + the /af/t anti-forgery token — the pre-2025 form-body
        // POST authenticated with the B2C csrf token is what earned the 400.
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("mprn", mprn);
        bodyJson.addProperty("searchType", "intervalkw");
        byte[] bodyBytes = new Gson().toJson(bodyJson).getBytes(StandardCharsets.UTF_8);

        Request fetchHDFRequest = new Request.Builder()
                .url(mMyAccountUrl + FETCH_HDF_URL)
                .header("User-Agent", USER_AGENT)
                .header("X-Xsrf-Token", mXsrfToken)
                .post(RequestBody.create(bodyBytes, MediaType.parse("application/json")))
                .build();

        try (Response fetchHDFResponse = mClient.newCall(fetchHDFRequest).execute()) {
            if (!fetchHDFResponse.isSuccessful()) {
                mLoggedIn = false;
                throw new ESBNException("HDF download failed (HTTP " + fetchHDFResponse.code()
                        + "). Consider using file or try again later.");
            }
            ResponseBody fetchHDFBody = fetchHDFResponse.body();
            if (fetchHDFBody == null) {
                mLoggedIn = false;
                throw new ESBNException("No body. Consider using file or try again later.");
            }
            try (InputStream inputStream = fetchHDFBody.byteStream();
                 InputStreamReader reader = new InputStreamReader(inputStream);
                 BufferedReader bufferedReader = new BufferedReader(reader)) {
                CSVReader csvReader = new CSVReader(bufferedReader);
                processHDF(processor, csvReader);
            } catch (DateTimeParseException | NumberFormatException e) {
                throw new ESBNException("Unable to parse incoming data. Consider using file.");
            }
        } catch (IOException e) {
            mLoggedIn = false;
            throw new ESBNException("IO issue. Consider using file or try again later.");
        }
    }

    private static String processHDF(ESBNImportExportEntry processor, CSVReader csvReader)
            throws IOException {
        String mprnFromFile = "";
        // skip header row
        try {
            csvReader.readNext();
            String[] nextLine;
            while ((nextLine = csvReader.readNext()) != null) {
                mprnFromFile = nextLine[MPRN_COL];
                String dt = nextLine[READ_DATETIME].split("\\+")[0];
                LocalDateTime readTime = LocalDateTime.parse(dt, HDF_FORMAT);
                Double reading = Double.parseDouble(nextLine[READ_VALUE]);
                String text = nextLine[READ_TYPE];
                boolean calculated = Pattern.matches(READ_KWH_PATTERN, text);
                boolean export = Pattern.matches(EXPORT_READ_PATTERN, text);
                if (export)
                    processor.processLine(calculated, ESBNImportExportEntry.HDFLineType.EXPORT, readTime, reading);
                else
                    processor.processLine(calculated, ESBNImportExportEntry.HDFLineType.IMPORT, readTime, reading);
            }
        } catch (CsvValidationException ve) {
            // Do nothing
        }
        return mprnFromFile;
    }

    // ── the login sequence ──────────────────────────────────────────────────

    private void logIn() throws IOException, ESBNException {
        getLoginPage();
        postLoginRequest();
        confirmLogin();
        postContinueWithoutJavaScript();
        getWelcomePage();
        fetchAntiForgeryToken();
        mLoggedIn = true;
    }

    /** Step 1: portal root → B2C page; scrape {@code var SETTINGS}. */
    private void getLoginPage() throws IOException, ESBNException {
        Request loginPageRequest = new Request.Builder()
                .url(mMyAccountUrl)
                .header("User-Agent", USER_AGENT)
                .build();

        String loginPageHtml;
        try (Response loginPageResponse = mClient.newCall(loginPageRequest).execute()) {
            ResponseBody loginPageBody = loginPageResponse.body();
            if (loginPageBody == null)
                throw new ESBNException("Failed to get the ESBN login page. Consider using file");
            loginPageHtml = loginPageBody.string();
        }

        Document document = Jsoup.parse(loginPageHtml);
        String settingsJson = null;
        for (Element scriptElement : document.select("script")) {
            settingsJson = extractSettingsJson(scriptElement.html());
            if (settingsJson != null) break;
        }
        if (settingsJson == null)
            throw new ESBNException("Failed to get the ESBN login page settings. Consider using file");
        try {
            mSettings = new Gson().fromJson(settingsJson, SettingsResponse.class);
        } catch (JsonSyntaxException e) {
            mSettings = null;
        }
        if (mSettings == null || mSettings.csrf == null || mSettings.transId == null
                || mSettings.hosts == null || mSettings.hosts.tenant == null
                || mSettings.hosts.policy == null)
            throw new ESBNException("Failed to get the ESBN login page settings. Consider using file");
    }

    /** The {@code var SETTINGS = {…};} payload, or null. Static for unit tests. */
    static String extractSettingsJson(String scriptContent) {
        Matcher matcher = SETTINGS_PATTERN.matcher(scriptContent);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Step 2: POST the credentials, XHR-style. */
    private void postLoginRequest() throws IOException, ESBNException {
        RequestBody loginRequestBody = new FormBody.Builder()
                .add("signInName", user)
                .add("password", password)
                .add("request_type", "RESPONSE")
                .build();

        String url = mLoginUrl + mSettings.hosts.tenant + LOGIN_URL_SUFFIX +
                "?tx=" + mSettings.transId +
                "&p=" + mSettings.hosts.policy;

        Request loginRequest = new Request.Builder()
                .url(url)
                .header("x-csrf-token", mSettings.csrf)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("User-Agent", USER_AGENT)
                .post(loginRequestBody)
                .build();

        String loginResponseText;
        try (Response loginResponse = mNoRedirectClient.newCall(loginRequest).execute()) {
            if (loginResponse.code() != 200)
                throw new ESBNException("Failed to login. Try again later");
            ResponseBody loginBody = loginResponse.body();
            if (loginBody == null)
                throw new ESBNException("Unable to confirm the login. Consider using file");
            loginResponseText = loginBody.string();
        }

        LoginResponse loginResponseJSON;
        try {
            loginResponseJSON = new Gson().fromJson(loginResponseText, LoginResponse.class);
        } catch (JsonSyntaxException e) {
            loginResponseJSON = null;
        }
        // Not the expected JSON at all → the human-verification interstitial
        // (or another portal change). Distinct from bad credentials, which
        // arrive as JSON with an explanatory message.
        if (loginResponseJSON == null || loginResponseJSON.status == null)
            throw new ESBNVerificationException();
        if (!"200".equals(loginResponseJSON.status)) {
            if (loginResponseJSON.message != null && !loginResponseJSON.message.isEmpty())
                throw new ESBNAuthException(loginResponseJSON.message);
            throw new ESBNVerificationException();
        }
    }

    /** Step 3: confirm → the no-JavaScript hand-back form. */
    private void confirmLogin() throws IOException, ESBNException {
        String url = mLoginUrl + mSettings.hosts.tenant + LOGIN_CONFIRM;

        Request confirmLoginRequest = new Request.Builder()
                .url(url + "?rememberMe=false" +
                        "&csrf_token=" + mSettings.csrf +
                        "&tx=" + mSettings.transId +
                        "&p=" + mSettings.hosts.policy)
                .header("User-Agent", USER_AGENT)
                .build();

        String htmlContent;
        try (Response confirmLoginResponse = mClient.newCall(confirmLoginRequest).execute()) {
            if (!confirmLoginResponse.isSuccessful())
                throw new ESBNException("Can't find needed data after login. Consider using file.");
            ResponseBody confirmLoginBody = confirmLoginResponse.body();
            if (confirmLoginBody == null)
                throw new ESBNException("Can't find needed data after login. Consider using file.");
            htmlContent = confirmLoginBody.string();
        }

        Element form = Jsoup.parse(htmlContent).selectFirst("form[id=auto]");
        // A confirmed page without form#auto is the human-verification /
        // rate-limit tell (plans/source/esbn.md §3).
        if (form == null) throw new ESBNVerificationException();
        mNoJavaScript = new NoJavaScript(form);
        if (mNoJavaScript.url == null || mNoJavaScript.state == null
                || mNoJavaScript.client_info == null || mNoJavaScript.code == null)
            throw new ESBNVerificationException();
    }

    /** Step 4: hand the B2C code back to the portal (plain form POST). */
    private void postContinueWithoutJavaScript() throws IOException, ESBNException {
        RequestBody confirmNoJSRequestBody = new FormBody.Builder()
                .add("state", mNoJavaScript.state)
                .add("client_info", mNoJavaScript.client_info)
                .add("code", mNoJavaScript.code)
                .build();

        Request confirmNoJSRequest = new Request.Builder()
                .url(mNoJavaScript.url)
                .header("User-Agent", USER_AGENT)
                .post(confirmNoJSRequestBody)
                .build();

        try (Response confirmNoJSResponse = mClient.newCall(confirmNoJSRequest).execute()) {
            if (confirmNoJSResponse.code() != 200)
                throw new ESBNException("Unable to continue without JavaScript. Consider using file.");
        }
    }

    /** Step 5: settle the portal auth cookie. */
    private void getWelcomePage() throws IOException, ESBNException {
        Request welcomeRequest = new Request.Builder()
                .url(mMyAccountUrl)
                .header("User-Agent", USER_AGENT)
                .build();
        try (Response welcomeResponse = mClient.newCall(welcomeRequest).execute()) {
            if (!welcomeResponse.isSuccessful())
                throw new ESBNException("Portal sign-in did not complete. Consider using file.");
        }
    }

    /** Step 6: the portal anti-forgery token for the download POST. */
    private void fetchAntiForgeryToken() throws IOException, ESBNException {
        Request tokenRequest = new Request.Builder()
                .url(mMyAccountUrl + TOKEN_URL)
                .header("User-Agent", USER_AGENT)
                .build();
        String tokenJson;
        try (Response tokenResponse = mClient.newCall(tokenRequest).execute()) {
            ResponseBody tokenBody = tokenResponse.body();
            if (!tokenResponse.isSuccessful() || tokenBody == null)
                throw new ESBNException("Could not get a download token. Consider using file.");
            tokenJson = tokenBody.string();
        }
        AntiForgeryTokenResponse token;
        try {
            token = new Gson().fromJson(tokenJson, AntiForgeryTokenResponse.class);
        } catch (JsonSyntaxException e) {
            token = null;
        }
        if (token == null || token.token == null || token.token.isEmpty())
            throw new ESBNException("Could not get a download token. Consider using file.");
        mXsrfToken = token.token;
    }

    private static class NoJavaScript {
        public String url;
        public String state;
        public String client_info;
        public String code;

        public NoJavaScript(Element form) {
            url = form.attr("action");
            if (url.isEmpty()) url = null;
            Element inputElement = form.select("input[name=state]").first();
            state = inputElement != null ? inputElement.attr("value") : null;
            inputElement = form.select("input[name=client_info]").first();
            client_info = inputElement != null ? inputElement.attr("value") : null;
            inputElement = form.select("input[name=code]").first();
            code = inputElement != null ? inputElement.attr("value") : null;
        }
    }
}
