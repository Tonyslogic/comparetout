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

package com.tfcode.comparetout.importers.esbn.responses;

/**
 * ESB Networks' Nov-2024 hardening: a human-verification interstitial or the
 * ~2-logins-per-IP-per-day rate limit (plans/source/esbn.md §3). Detected as
 * a non-JSON / message-less step-2 response or a step-3 page without
 * {@code form#auto}. FATAL for the current run — callers must surface the
 * message and never retry automatically: retrying burns the daily login
 * budget and looks like a bot.
 */
public class ESBNVerificationException extends ESBNException {

    public static final String DEFAULT_MESSAGE = "ESB Networks is asking for human "
            + "verification or has rate-limited this connection. Try again tomorrow, "
            + "sign in once in a browser, or download and import the HDF file.";

    public ESBNVerificationException() {
        super(DEFAULT_MESSAGE);
    }

    public ESBNVerificationException(String message) {
        super(message);
    }
}
