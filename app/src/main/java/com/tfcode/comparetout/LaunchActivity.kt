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

package com.tfcode.comparetout

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.tfcode.comparetout.ui2.UI2MainActivity
import kotlin.concurrent.thread

/**
 * Tiny launcher / router activity.
 *
 * Replaces the previous flow where MainActivity (legacy) was the LAUNCHER,
 * fully inflated, then bounced to UI2MainActivity when use_ui2 was true.
 * On older devices that double-inflate was visibly slow. LaunchActivity
 * does only the route decision — a single DataStore read — and goes
 * straight to the chosen home.
 *
 * The AndroidX SplashScreen API holds a stable launch image until the
 * route decision is made, so the user never sees a black flicker. The
 * post-splash theme matches UI2's, so when the splash hands off into the
 * next activity the visual transition is smooth.
 *
 * Routing rules:
 * - profiles without the legacy UI (source edition) → UI2MainActivity,
 *   always — no legacy onboarding detour; UI2's own first-use disclaimer
 *   covers fresh installs.
 * - `use_ui2 == "true"`  → UI2MainActivity (fast path; the common case).
 * - `use_ui2 == "false"` → MainActivity (explicit legacy opt-out).
 * - anything else (`""`, null, unset) → MainActivity so the existing
 *   first-launch disclaimer + UI2 onboarding flow runs once. The next
 *   launch will go straight to UI2 because that flow writes `"true"`.
 */
class LaunchActivity : ComponentActivity() {

    @Volatile private var routeReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen MUST be called before super.onCreate.
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !routeReady }
        super.onCreate(savedInstanceState)

        val app = application as TOUTCApplication
        thread(start = true, isDaemon = true, name = "ui2-route-decision") {
            // getStringValueFromDataStore blocks on the DataStore read — off-main-thread only.
            val ui2 = app.getStringValueFromDataStore("use_ui2") ?: ""
            val target: Class<*> = if (!com.tfcode.comparetout.profile.AppProfiles.current.hasLegacyUi ||
                ui2 == "true") {
                UI2MainActivity::class.java
            } else {
                // "false" stays on legacy permanently; "" lets MainActivity
                // run the disclaimer / UI2 onboarding flow once.
                MainActivity::class.java
            }
            runOnUiThread {
                val intent = Intent(this, target).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)
                // Release the splash and finish without a slide animation so
                // the handoff to the next activity looks like a single screen.
                routeReady = true
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }
    }
}
