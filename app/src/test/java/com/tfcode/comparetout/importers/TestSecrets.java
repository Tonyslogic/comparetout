/*
 * Copyright (c) 2023-2024. Tony Finnerty
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

package com.tfcode.comparetout.importers;

public class TestSecrets {

    /**
     * Live integration tests (real AlphaESS / Home Assistant / ESBN endpoints,
     * using the credentials below) run only when explicitly opted in — set
     * {@code -DrunLiveTests=true} or the {@code RUN_LIVE_TESTS=true} environment
     * variable, and replace the placeholder values in this file with real
     * ones. Off by default so the suite is green on a fresh checkout and in CI.
     */
    public static boolean liveTestsEnabled() {
        return Boolean.parseBoolean(System.getProperty("runLiveTests"))
                || "true".equals(System.getenv("RUN_LIVE_TESTS"));
    }

    // The serial number of the AlphaESS system to use for testing
    public static final String SERIAL = "AL1234567891911";

    // The AppID or Developer ID from open.alphaess.com (registration required
    public static final String APPID = "alpha****************";

    // The AppSecret or Developer Secret from open.alphaess.com (registration required
    public static final String SECRET = "********************************";

    public static final String ESBN_USER = "user@email.com";

    public static final String ESBN_PASSWORD = "password";

    public static final String ESBN_MPRN = "12345678910";

    public static final String HA_TOKEN = "sjngasjhngasjngb;pajsng;kjdsanfb;p";

    public static final String HA_IP = "192.168.1.1";
}
