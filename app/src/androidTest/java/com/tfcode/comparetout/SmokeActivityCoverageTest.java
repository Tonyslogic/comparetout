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

import com.tfcode.comparetout.importers.alphaess.ImportAlphaActivity;
import com.tfcode.comparetout.importers.esbn.ImportESBNActivity;
import com.tfcode.comparetout.importers.homeassistant.ImportHomeAssistantActivity;
import com.tfcode.comparetout.ui2.UI2DataSourceManagementActivity;
import com.tfcode.comparetout.ui2.UI2ImportExportActivity;
import com.tfcode.comparetout.ui2.UI2MainActivity;
import com.tfcode.comparetout.ui2.UI2PricePlanListActivity;
import com.tfcode.comparetout.ui2.UI2TimezoneActivity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.runner.RunWith;

/**
 * Cold-launches each top-level Activity that does not require pre-seeded
 * Intent extras (scenario ID, plan ID, etc) and verifies it survives
 * onCreate without throwing. Each activity is launched and torn down
 * independently so one failure doesn't block coverage of the rest —
 * the ErrorCollector aggregates all failures into one test report.
 *
 * Activities that need prior DB state (wizards keyed by ID, scenario
 * detail screens, individual price plan editors) are deliberately
 * excluded; they're covered by the integration test that seeds data
 * first.
 */
@RunWith(AndroidJUnit4.class)
public class SmokeActivityCoverageTest {

    @Rule
    public final ErrorCollector errors = new ErrorCollector();

    @SuppressWarnings("unchecked")
    private static final Class<? extends Activity>[] ACTIVITIES = new Class[] {
            // Legacy launcher
            MainActivity.class,
            // UI2 top-level surfaces (state-free or self-bootstrapping)
            UI2MainActivity.class,
            UI2PricePlanListActivity.class,
            UI2DataSourceManagementActivity.class,
            UI2ImportExportActivity.class,
            UI2TimezoneActivity.class,
            // Importer landing screens
            ImportAlphaActivity.class,
            ImportESBNActivity.class,
            ImportHomeAssistantActivity.class,
    };

    @Test
    public void everyTopLevelActivityLaunches() {
        Context ctx = ApplicationProvider.getApplicationContext();
        for (Class<? extends Activity> activityClass : ACTIVITIES) {
            errors.checkSucceeds(() -> {
                Intent intent = new Intent(ctx, activityClass);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try (ActivityScenario<? extends Activity> scenario =
                             ActivityScenario.launch(intent)) {
                    // Surviving launch + non-null scenario is the assertion;
                    // try-with-resources tears the activity down cleanly.
                    assertNotNull("ActivityScenario.launch returned null", scenario);
                }
                return null;
            });
        }
    }
}
