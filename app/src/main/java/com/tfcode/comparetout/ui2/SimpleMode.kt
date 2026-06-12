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

package com.tfcode.comparetout.ui2

import com.tfcode.comparetout.TOUTCApplication

/**
 * "Simple mode" is a persistent, app-wide flag that collapses the whole UI2
 * shell down to a single combined input + results screen (see
 * `plans/simple_ui.md`). It is distinct from `wizard_novice_mode`
 * ("Show hints") and from `use_ui2` (legacy ↔ UI2 switch).
 *
 * All reads/writes go through the same blocking DataStore surface every other
 * preference uses ([TOUTCApplication.getStringValueFromDataStore] /
 * [TOUTCApplication.putStringValueIntoDataStore]); callers MUST invoke these
 * off the main thread.
 */
const val SIMPLE_MODE_KEY = "simple_mode"

/**
 * Resolve whether the app should open in simple mode, applying the first-run
 * default exactly once.
 *
 * Stored values: "true" / "false" are honoured verbatim. An empty string means
 * the flag has never been written (fresh install or pre-feature upgrade) — in
 * that case the default is **simple when there is no existing data**, and the
 * resolved value is persisted so the decision is stable thereafter.
 *
 * @param hasExistingData whether the user already has scenarios (an existing
 *   user keeps the full UI; a fresh install starts simple). Computed by the
 *   caller off the main thread (the backend-edit rule blocks adding a blocking
 *   source-count getter, so scenarios are the freshness signal).
 *
 * Blocking — call on a background dispatcher.
 */
fun resolveSimpleMode(app: TOUTCApplication, hasExistingData: Boolean): Boolean {
    val stored = runCatching { app.getStringValueFromDataStore(SIMPLE_MODE_KEY) }.getOrDefault("")
    return when (stored) {
        "true" -> true
        "false" -> false
        else -> {
            val default = !hasExistingData
            runCatching { app.putStringValueIntoDataStore(SIMPLE_MODE_KEY, default.toString()) }
            default
        }
    }
}

/** Persist the simple-mode flag. Blocking — call on a background dispatcher. */
fun setSimpleMode(app: TOUTCApplication, enabled: Boolean) {
    runCatching { app.putStringValueIntoDataStore(SIMPLE_MODE_KEY, enabled.toString()) }
}
