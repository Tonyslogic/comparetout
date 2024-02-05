/*
 * Copyright (c) 2024. Tony Finnerty
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

import com.google.gson.Gson;
import com.opencsv.CSVReader;
import com.tfcode.comparetout.importers.esbn.responses.ESBNException;
import com.tfcode.comparetout.importers.esbn.responses.FetchRangeResponse;
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
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ESBNHDFClient {

    private static final String MY_ACCOUNT_URL = "https://myaccount.esbnetworks.ie/";
    private static final String LOGIN_URL = "https://login.esbnetworks.ie";
    private static final String LOGIN_URL_SUFFIX = "/SelfAsserted";
    private static final String LOGIN_CONFIRM = "/api/CombinedSigninAndSignup/confirmed";
    private static final String FETCH_URL = "datahub/GetHdfContent";
    private static final String FETCH_HDF_URL = "DataHub/DownloadHdf";
    private static final String FETCH_MPRN_URL = "Outages";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36";

    private static final int READ_VALUE = 2;
    private static final int READ_TYPE = 3;
    private static final int READ_DATETIME = 4;
    private static final String EXPORT_READ_TYPE = "Export";
    private static final DateTimeFormatter HDF_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final DateTimeFormatter RANGE_FORMAT_X = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private String mprn;
    private final String user;
    private final String password;

    private boolean mLoggedIn = false;

    private final OkHttpClient mClient;
    private final OkHttpClient mNoRedirectClient;
    CookieManager mCM = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    CookieFixInterceptor mCFI = new CookieFixInterceptor();
    List<String> getLoginPageResponseCookies;
    List<String> postLoginResponseCookies;
    List<String> getLoginConfirmResponseCookies;
    List<String> postNoJSResponseCookies;

    private SettingsResponse mSettings = null;
    private NoJavaScript mNoJavaScript = null;

    public ESBNHDFClient(String user, String password) {
        this.user = user;
        this.password = password;

        CookieHandler mCookieHandler = mCM;

//        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
//        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS);

        mClient = new OkHttpClient.Builder()
                .cookieJar(new JavaNetCookieJar(mCookieHandler))
                .addNetworkInterceptor(mCFI)
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
//                .addNetworkInterceptor(loggingInterceptor)
                .build();
        mNoRedirectClient = new OkHttpClient.Builder()
                .cookieJar(new JavaNetCookieJar(mCookieHandler))
                .addNetworkInterceptor(mCFI)
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
//                .addNetworkInterceptor(loggingInterceptor)
                .followRedirects(false)
                .build();
    }

    public void readEntriesFromFile(InputStream inputStream, ESBNImportExportEntry processor)
            throws ESBNException {
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            CSVReader csvReader = new CSVReader(reader);
            processHDF(processor, csvReader);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ESBNException("Unable to read file. Is the file valid?.");
        }catch (DateTimeParseException | NumberFormatException e) {
            throw new ESBNException("Unable to parse file data. Is the file valid?.");
        }
    }

    public void setSelectedMPRN(String mprn) {
        this.mprn = mprn;
    }

    public List<String> fetchMPRNs() throws ESBNException {
        System.out.println("fetchMPRNs");
        List<String> ret = new ArrayList<>();
        try {

            if (!mLoggedIn) {
                logIn();
            }
            if (!mLoggedIn) return ret;
            System.out.println("fetchMPRNs:: loggedIn");

            String url = MY_ACCOUNT_URL + FETCH_MPRN_URL;

            Request mprnPageRequest = new Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build();

            mCFI.clearCookies();

            Response mprnPageResponse = mClient.newCall(mprnPageRequest).execute();
            printOutDebug("GET MPRNs code: ", mprnPageResponse, mprnPageRequest);

            if (mprnPageResponse.code() != 200) {
                throw new ESBNException("Failed to get a list of mprns");
            }
            String mprnPageHtml = "";
            if (!(null == mprnPageResponse.body())) {
                mprnPageHtml = Objects.requireNonNull(mprnPageResponse.body()).string();
            }
            mprnPageResponse.close();
//            System.out.println("fetchMPRNs:: gotResponse");
//            System.out.println(mprnPageHtml);

            Document doc = Jsoup.parse(mprnPageHtml);

            // Select card-body elements that contain an h2 element with the text "MPRN: "
            Elements mprnCardBodies = doc.select("div.card-body:has(h2.card-title.h6-style:containsOwn(MPRN:))");

//            System.out.println("fetchMPRNs:: card count=" + mprnCardBodies.size());

            // Loop through the selected card-body elements
            for (Element cardBody : mprnCardBodies) {
                // Extract and print the MPRN value
                Element mprnElement = cardBody.select("h2.card-title.h6-style + p#display-data").first();
                if (mprnElement != null) {
                    String mprnValue = mprnElement.text();
                    ret.add(mprnValue);
                    System.out.println("MPRN: " + mprnValue);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new ESBNException("IO issue. Consider using file or try again later.");
        }
        return ret;
    }

    public void fetchSmartMeterDataHDF(ESBNImportExportEntry processor) throws ESBNException {
        try {
            if (!mLoggedIn) logIn();
            if (!mLoggedIn) return;
        } catch (IOException e) {
            e.printStackTrace();
            throw new ESBNException("IO issue. Consider using file or try again later.");
        }

        String url = MY_ACCOUNT_URL + FETCH_HDF_URL + "?mprn=" + mprn;
        Request fetchHDFRequest = new Request.Builder().url(url).build();
        try (Response fetchHDFResponse = mClient.newCall(fetchHDFRequest).execute()) {
            if (!fetchHDFResponse.isSuccessful()) {
                System.out.println("HDF Download failed");
                return;
            }
            if (null == fetchHDFResponse.body()) {
                System.out.println("Failed to fetch HDF");
                mLoggedIn = false;
            }
            else try (InputStream inputStream = Objects.requireNonNull(fetchHDFResponse.body()).byteStream();
                      InputStreamReader reader = new InputStreamReader(inputStream);
                      BufferedReader bufferedReader = new BufferedReader(reader)) {

                CSVReader csvReader = new CSVReader(bufferedReader);
                processHDF(processor, csvReader);
            } catch (IOException e) {
                mLoggedIn = false;
                e.printStackTrace();
                throw new ESBNException("IO issue. Consider using file or try again later.");
            } catch (DateTimeParseException | NumberFormatException e) {
                throw new ESBNException("Unable to parse incoming data. Consider using file.");
            }

        } catch (IOException e) {
            mLoggedIn = false;
            e.printStackTrace();
            throw new ESBNException("IO issue. Consider using file or try again later.");
        }
    }

    private void processHDF(ESBNImportExportEntry processor, CSVReader csvReader)
            throws IOException {
        // skip header row
        csvReader.readNext();
        String[] nextLine;
        while ((nextLine = csvReader.readNext()) != null) {
            // nextLine[] is an array of values from the line
            String dt = nextLine[READ_DATETIME].split("\\+")[0];
            LocalDateTime readTime = LocalDateTime.parse(dt, HDF_FORMAT);
            Double reading = Double.parseDouble(nextLine[READ_VALUE]);
            boolean export = nextLine[READ_TYPE].contains(EXPORT_READ_TYPE);
            if (export) processor.processLine(ESBNImportExportEntry.HDFLineType.EXPORT, readTime, reading);
            else processor.processLine(ESBNImportExportEntry.HDFLineType.IMPORT, readTime, reading);
        }
    }

    public void fetchSmartMeterDataFromDate(String fromDate, ESBNImportExportEntry processor)
            throws ESBNException {
        try {
            if (!mLoggedIn) logIn();
            if (!mLoggedIn) return;

            // GET SOME DATA
            {
                Request fetchHDFDataRequest = new Request.Builder()
                        .url(MY_ACCOUNT_URL + FETCH_URL +
                                "?mprn=" + mprn +
                                "&startDate=" + fromDate)
                        .header("User-Agent", USER_AGENT)
                        .build();

                Response fetchHDFDataResponse = mClient.newCall(fetchHDFDataRequest).execute();
                String fetchRangeJSON;
                if (!(null == fetchHDFDataResponse.body())) {
                    fetchRangeJSON = Objects.requireNonNull(fetchHDFDataResponse.body()).string();
                    FetchRangeResponse fetchRangeResponse = new Gson().fromJson(fetchRangeJSON, FetchRangeResponse.class);
                    for (FetchRangeResponse.ImportItem item : fetchRangeResponse.imports) {
                        LocalDateTime readTime = LocalDateTime.parse(item.x, RANGE_FORMAT_X);
                        Double reading = item.y;
                        processor.processLine(ESBNImportExportEntry.HDFLineType.IMPORT, readTime, reading);
                    }
                    for (FetchRangeResponse.ExportItem item : fetchRangeResponse.exports) {
                        LocalDateTime readTime = LocalDateTime.parse(item.x, RANGE_FORMAT_X);
                        Double reading = item.y;
                        processor.processLine(ESBNImportExportEntry.HDFLineType.EXPORT, readTime, reading);
                    }
                }
                else {
                    System.out.println("Fetch range failed");
                    mLoggedIn = false;
                }
                fetchHDFDataResponse.close();
            }
        } catch (IOException e) {
            mLoggedIn = false;
            e.printStackTrace();
            throw new ESBNException("IO issue. Consider using file or try again later.");
        }catch (DateTimeParseException | NumberFormatException e) {
            e.printStackTrace();
            throw new ESBNException("Unable to parse incoming data. Consider using file.");
        }
    }

    private void logIn() throws IOException, ESBNException {
        getLoginPage();
        postLoginRequest();
        confirmLogin();
        postContinueWithoutJavaScript();
        mLoggedIn = true;
    }

    private void postContinueWithoutJavaScript() throws IOException, ESBNException {
        // POST CONFIRM NO JAVASCRIPT
        {
            RequestBody confirmNoJSRequestBody = new FormBody.Builder()
                    .add("state", mNoJavaScript.state)
                    .add("client_info", mNoJavaScript.client_info)
                    .add("code", mNoJavaScript.code)
                    .build();

            Request confirmNoJSRequest = new Request.Builder()
                    .url(mNoJavaScript.url)
                    .header("x-csrf-token", mSettings.csrf)
                    .post(confirmNoJSRequestBody)
                    .build();

            mCFI.clearCookies();
            Response confirmNoJSResponse = mClient.newCall(confirmNoJSRequest).execute();
            postNoJSResponseCookies = mCFI.getResponseCookies();
            List<String> filteredList = new ArrayList<>();
            getLoginPageResponseCookies.stream()
                    .filter(s -> s.startsWith(".AspNetCore"))
                    .forEach(filteredList::add);
            for (String s: filteredList) {
                List<HttpCookie> cookies = HttpCookie.parse(s);
                for (HttpCookie cookie: cookies)
                    mCM.getCookieStore().add(URI.create("http://myaccount.esbnetworks.ie"), cookie);
            }

            printOutDebug("POST confirm no JS response code: ", confirmNoJSResponse, confirmNoJSRequest);

            if (confirmNoJSResponse.code() != 200)
                throw new ESBNException("Unable to continue without JavaScript. Consider using file.");
            confirmNoJSResponse.close();
        }
    }

    private void confirmLogin() throws IOException, ESBNException {
        // GET CONFIRM THE LOGIN
        {
            // Make the confirm login GET request
            String url = LOGIN_URL + mSettings.hosts.tenant + LOGIN_CONFIRM;

            Request confirmLoginRequest = new Request.Builder()
                    .url(url + "?rememberMe=False" +
                            "&csrf_token=" + mSettings.csrf +
                            "&tx=" + mSettings.transId +
                            "&p=" + mSettings.hosts.policy)
                    .header("x-csrf-token", mSettings.csrf)
                    .header("User-Agent", USER_AGENT)
                    .build();

            mCFI.clearCookies();

            Response confirmLoginResponse = mClient.newCall(confirmLoginRequest).execute();
            getLoginConfirmResponseCookies = mCFI.getResponseCookies();

            if (confirmLoginResponse.isSuccessful()) {
                // Parse the HTML content using Jsoup
                String htmlContent;
                if (!(null == confirmLoginResponse.body())) {
                    htmlContent = Objects.requireNonNull(confirmLoginResponse.body()).string();
                }
                else throw new ESBNException("Can't find needed data after login. Consider using file.");
                Document document = Jsoup.parse(htmlContent);

                // Find the form with id 'auto'
                Element form = document.selectFirst("form[id=auto]");

                // Extract properties needed to confirm no JS
                mNoJavaScript = new NoJavaScript(form);

            } else {
                System.err.println("Error: " + confirmLoginResponse.code());
                throw new ESBNException("Can't find needed data after login. Consider using file.");
            }
            confirmLoginResponse.close();

            printOutDebug("GET confirm login: ", confirmLoginResponse, confirmLoginRequest);
        }
    }

    private void postLoginRequest() throws IOException, ESBNException {
        // POST THE LOGIN CREDENTIALS
        RequestBody loginRequestBody = new FormBody.Builder()
                .add("signInName", user)
                .add("password", password)
                .add("request_type", "RESPONSE")
                .build();

        String url = LOGIN_URL + mSettings.hosts.tenant + LOGIN_URL_SUFFIX +
                "?tx=" + mSettings.transId +
                "&p=" + mSettings.hosts.policy;

        mCFI.clearCookies();

        Request loginRequest = new Request.Builder()
                .url(url)
                .header("x-csrf-token", mSettings.csrf)
                .header("User-Agent", USER_AGENT)
                .post(loginRequestBody)
                .build();

        Response loginResponse = mNoRedirectClient.newCall(loginRequest).execute();
        postLoginResponseCookies = mCFI.getResponseCookies();

        printOutDebug("POST login response code: ", loginResponse, loginRequest);
        if (loginResponse.code() != 200) throw new ESBNException("Failed to login. Try again later");

        String loginResponseHtml;
        if (!(null == loginResponse.body())) {
            loginResponseHtml = Objects.requireNonNull(loginResponse.body()).string();
        }
        else throw new ESBNException("Unable to confirm the login. Consider using file");
        loginResponse.close();
        LoginResponse loginResponseJSON = new Gson().fromJson(loginResponseHtml, LoginResponse.class);
        if (!"200".equals(loginResponseJSON.status)) {
            String reason = (null == loginResponseJSON.message) ? "Login failed for an unreported reason" : loginResponseJSON.message;
            System.out.println(reason);
            throw new ESBNException(reason);
        }
    }

    private void getLoginPage() throws IOException, ESBNException {
        // GET THE LOGIN PAGE
        {
            Request loginPageRequest = new Request.Builder()
                    .url(MY_ACCOUNT_URL)
                    .header("User-Agent", USER_AGENT)
                    .build();

            Response loginPageResponse = mClient.newCall(loginPageRequest).execute();
            getLoginPageResponseCookies = mCFI.getResponseCookies();
            // Workaround for android not dealing with domains starting with a .
            List<String> filteredList = new ArrayList<>();
            getLoginPageResponseCookies.stream()
                    .filter(s -> s.startsWith(".AspNetCore"))
                    .forEach(filteredList::add);
            for (String s: filteredList) {
                List<HttpCookie> cookies = HttpCookie.parse(s);
                for (HttpCookie cookie: cookies)
                    mCM.getCookieStore().add(URI.create("http://myaccount.esbnetworks.ie"), cookie);
            }

            printOutDebug("GET login page code: ", loginPageResponse, loginPageRequest);

            String loginPageHtml;
            if (!(null == loginPageResponse.body())) {
                loginPageHtml = Objects.requireNonNull(loginPageResponse.body()).string();
            }
            else throw new ESBNException("Failed to get the ESBN login page. Consider using file");
            loginPageResponse.close();
            Document document = Jsoup.parse(loginPageHtml);

            // Select the script element containing the variable
            List<Element> scripts = document.select("script");
            String variableValue = null;
            for (Element scriptElement : scripts) {

                // Get the script content
                String scriptContent = scriptElement.html();

                // Extract the value of the variable
                String variableName = "SETTINGS";
                variableValue = extractVariableValue(scriptContent, variableName);
                if (null == variableValue) continue;

                // Print the result
                // System.out.println("Value of " + variableName + ": " + variableValue);

                break;
            }
            // Parse the SETTINGS JSON string into SettingsResponse
            if (!(null == variableValue)) {
                mSettings = new Gson().fromJson(variableValue, SettingsResponse.class);
            }
            else throw new ESBNException("Failed to get the ESBN login page settings. Consider using file");
        }
    }

    private void printOutDebug(String message, Response loginPageResponse, Request loginPageRequest) {
//        System.out.println ("=========================================================================");
//        System.out.println ("=========================================================================");
        System.out.println (message + loginPageResponse.code());
//        System.out.println(loginPageRequest.url());
//        List<Cookie> cookies = mClient.cookieJar().loadForRequest(loginPageRequest.url());
//        cookies.stream().forEach(System.out::println);
//        System.out.println ("=========================================================================");
//        for( URI uri : mCM.getCookieStore().getURIs()) {
//            System.out.println(uri);
//            mCM.getCookieStore().get(uri).stream().forEach(System.out::println);
//        }
//        System.out.println ("-------------------------------------------------------------------------");
//        mCM.getCookieStore().getCookies().stream().forEach(System.out::println);
    }

    private static String extractVariableValue(String scriptContent, String variableName) {
        String ret = null;
        // Trim the script content and remove any leading/trailing whitespace
        scriptContent = scriptContent.trim();
        String[] lines = scriptContent.split("\\r?\\n");
        for (String line : lines) {
            if (line.startsWith("var " + variableName + " ") || scriptContent.startsWith("var " + variableName + "=")) {
                // Find the position of the variable assignment and extract the value
                int startIndex = line.indexOf("{",variableName.length() + 1);
                int endIndex = line.indexOf(';', startIndex);
                // Extract the value
                if (endIndex != -1) {
                    ret =  line.substring(startIndex, endIndex).trim();
                }
            }
        }
        return ret;
    }



    private static class NoJavaScript {
        public String url;
        public String state;
        public String client_info;
        public String code;

        public NoJavaScript (Element form) {
            if (!(null == form)) {
                url = form.attr("action");
                // Find the input element with name 'state'
                Element inputElement = form.select("input[name=state]").first();
                state = inputElement != null ? inputElement.attr("value") : null;
                if ((null == state)) System.out.println("state= null" );
                inputElement = form.select("input[name=client_info]").first();
                client_info = inputElement != null ? inputElement.attr("value") : null;
                if ((null == client_info)) System.out.println("client_info= null");
                inputElement = form.select("input[name=code]").first();
                code = inputElement != null ? inputElement.attr("value") : null;
                if ((null == code)) System.out.println("code= null");
            }
        }
    }

}