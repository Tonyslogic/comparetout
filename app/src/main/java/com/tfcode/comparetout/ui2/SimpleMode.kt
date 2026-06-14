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

/** DataStore key holding the row id of the single scenario simple mode manages. */
const val SIMPLE_SCENARIO_ID_KEY = "simple_scenario_id"

/** The reserved name of simple mode's scenario, so it can be found/cleaned even
 * if the stored id is lost. */
const val SIMPLE_SCENARIO_NAME = "My home (simple)"

/** The system-serial key simple mode's ESBN HDF rows are stored under (the
 * import worker keys data by the SN we pass, not by the file's MPRN). */
const val SIMPLE_ESBN_SYSTEM_SN = "ESBN HDF"

/** DataStore key holding the remembered quick-mode inputs (JSON). */
const val SIMPLE_INPUTS_KEY = "simple_inputs_json"

/** Read the stored simple-scenario id, or null if none. Blocking — off-main-thread. */
fun storedSimpleScenarioId(app: TOUTCApplication): Long? =
    runCatching { app.getStringValueFromDataStore(SIMPLE_SCENARIO_ID_KEY) }
        .getOrDefault("").toLongOrNull()

/** Persist the simple-scenario id ("" clears it). Blocking — off-main-thread. */
fun setStoredSimpleScenarioId(app: TOUTCApplication, id: Long?) {
    runCatching {
        app.putStringValueIntoDataStore(SIMPLE_SCENARIO_ID_KEY, id?.toString() ?: "")
    }
}

/**
 * Resolve whether the app should open in simple ("quick") mode.
 *
 * Default is **full UI2** — a fresh install lands on the normal dashboard (whose
 * empty state offers Quick mode as one of its choices). Quick mode is opt-in via
 * that button or the drawer, and is then remembered ("true"). Only an explicit
 * stored "true" selects it.
 *
 * Blocking — call on a background dispatcher.
 */
fun resolveSimpleMode(app: TOUTCApplication): Boolean =
    runCatching { app.getStringValueFromDataStore(SIMPLE_MODE_KEY) }.getOrDefault("") == "true"

/** Persist the simple-mode flag. Blocking — call on a background dispatcher. */
fun setSimpleMode(app: TOUTCApplication, enabled: Boolean) {
    runCatching { app.putStringValueIntoDataStore(SIMPLE_MODE_KEY, enabled.toString()) }
}
