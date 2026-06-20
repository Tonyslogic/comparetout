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

package com.tfcode.comparetout.model.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;
import com.tfcode.comparetout.model.json.scenario.ScenarioJsonFile;

import org.junit.Test;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reproduces the user-reported clipboard import: a single scenario carrying one battery, a load-shift, and an
 * empty {@code "HWSystem": {}}. The user observed that on import the load-shift survived but the battery was
 * lost (and a phantom hot-water system appeared). This drives the SAME model-layer path the wizard's IMPORT
 * uses — {@code Gson -> List<ScenarioJsonFile> -> JsonTools.createScenarioComponentList} — over the user's exact
 * JSON (in {@code resources/scenario-import/pasted-scenario.json}) to localise the loss.
 */
public class ScenarioImportParseTest {

    private static List<ScenarioJsonFile> parse() throws Exception {
        Type type = new TypeToken<List<ScenarioJsonFile>>() {}.getType();
        try (InputStreamReader r = new InputStreamReader(
                ScenarioImportParseTest.class.getResourceAsStream("/scenario-import/pasted-scenario.json"),
                StandardCharsets.UTF_8)) {
            return new Gson().fromJson(r, type);
        }
    }

    @Test
    public void gsonParsesTheBatteryArray() throws Exception {
        ScenarioJsonFile f = parse().get(0);
        assertNotNull("Batteries array must parse", f.batteries);
        assertEquals(1, f.batteries.size());
        assertEquals(5.7, f.batteries.get(0).batterySize, 0d);
        assertNotNull(f.batteries.get(0).chargeModel);
    }

    @Test
    public void createScenarioComponentListKeepsTheBattery() throws Exception {
        ScenarioComponents c =
                JsonTools.createScenarioComponentList(new ArrayList<>(parse())).get(0);
        // The load-shift the user saw survive:
        assertNotNull(c.loadShifts);
        assertEquals("load-shift should survive (user confirmed it did)", 1, c.loadShifts.size());
        // The battery the user saw vanish:
        assertNotNull(c.batteries);
        assertEquals("the battery must reach ScenarioComponents", 1, c.batteries.size());
        Battery b = c.batteries.get(0);
        assertEquals(5.7, b.getBatterySize(), 0d);
        assertEquals("<New inverter>", b.getInverter());
    }

    /**
     * Documents the phantom-hot-water bug: an empty {@code "HWSystem": {}} must NOT become a fully-defaulted
     * hot-water system. {@code createHWSystem} currently returns a default-constructed
     * {@link com.tfcode.comparetout.model.scenario.HWSystem} (the first null-field setter NPEs and the catch
     * hands back the default object), so the import gains a hot-water config the user never specified.
     */
    @Test
    public void emptyHwSystemObjectDoesNotBecomeADefaultSystem() throws Exception {
        ScenarioComponents c =
                JsonTools.createScenarioComponentList(new ArrayList<>(parse())).get(0);
        assertNull("an empty \"HWSystem\": {} must not import a hot-water system", c.hwSystem);
    }
}
