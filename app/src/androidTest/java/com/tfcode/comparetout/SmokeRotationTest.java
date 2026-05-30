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

import android.app.Activity;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.tfcode.comparetout.ui2.UI2MainActivity;
import com.tfcode.comparetout.ui2.UI2PricePlanListActivity;
import com.tfcode.comparetout.ui2.UI2ImportExportActivity;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.runner.RunWith;

/**
 * Drives the main UI2 surfaces through a portrait → landscape → portrait
 * rotation cycle to catch crashes from configuration-change state loss.
 * Each rotation triggers Activity recreation, so any field that relies on
 * non-saved state (Compose remember-without-saveable, mutable view-model
 * references that aren't survived correctly) will surface here.
 */
@RunWith(AndroidJUnit4.class)
public class SmokeRotationTest {

    @Rule
    public final ErrorCollector errors = new ErrorCollector();

    private static final Class<? extends Activity>[] ROTATABLE_ACTIVITIES = new Class[] {
            UI2MainActivity.class,
            UI2PricePlanListActivity.class,
            UI2ImportExportActivity.class,
    };

    private UiDevice device;

    @After
    public void restoreOrientation() {
        if (device != null) {
            try {
                device.setOrientationNatural();
            } catch (Exception ignored) {
                // device may have already torn down
            }
        }
    }

    @Test
    public void rotateEachMainSurface() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        for (Class<? extends Activity> activityClass : ROTATABLE_ACTIVITIES) {
            errors.checkSucceeds(() -> {
                try (ActivityScenario<? extends Activity> scenario =
                             ActivityScenario.launch(activityClass)) {
                    // portrait → landscape
                    device.setOrientationLeft();
                    sleep(500);
                    // landscape → portrait
                    device.setOrientationNatural();
                    sleep(500);
                }
                return null;
            });
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
