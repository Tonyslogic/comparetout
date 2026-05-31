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

package com.tfcode.comparetout;

import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Minimal smoke test that launches the app's main launcher activity and
 * verifies it survives onCreate without crashing. Surviving the
 * ActivityScenario.launch call IS the assertion — any uncaught exception
 * during onCreate propagates here as a test failure.
 */
@RunWith(AndroidJUnit4.class)
public class SmokeLaunchTest {

    @Test
    public void appLaunchesWithoutCrashing() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent launchIntent = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        if (launchIntent == null) {
            throw new IllegalStateException(
                    "No launcher activity declared for " + context.getPackageName());
        }
        try (ActivityScenario<Activity> scenario = ActivityScenario.launch(launchIntent)) {
            // Non-null + no exception during onCreate is the assertion;
            // try-with-resources tears the activity down cleanly.
            assertNotNull("ActivityScenario.launch returned null", scenario);
        }
    }
}
