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

package com.tfcode.comparetout.testdata;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.scenario.ScenarioJsonFile;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Loads the committed full-fat scenario fixture — one scenario with every
 * component type populated with non-default values — for the backend
 * characterization tests (plans/source/mega-refactor.md, Phase 0).
 *
 * <p>Every accessor re-parses the resource, so tests can freely mutate the
 * returned objects without cross-test bleed.
 */
public final class FullScenarioFixture {

    public static final String RESOURCE = "/fixtures/full-scenario.json";

    private FullScenarioFixture() {}

    /** The raw fixture JSON text. */
    public static String json() {
        try (InputStream in = FullScenarioFixture.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("Missing test resource " + RESOURCE);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed reading " + RESOURCE, e);
        }
    }

    /** The fixture parsed to its JSON model (list form, as importers see it). */
    public static List<ScenarioJsonFile> jsonFiles() {
        Type type = new TypeToken<List<ScenarioJsonFile>>() {}.getType();
        try (Reader r = new InputStreamReader(
                FullScenarioFixture.class.getResourceAsStream(RESOURCE), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(r, type);
        } catch (IOException e) {
            throw new IllegalStateException("Failed reading " + RESOURCE, e);
        }
    }

    /** The fixture as entity objects, via the production JsonTools path. */
    public static ScenarioComponents components() {
        return JsonTools.createScenarioComponentList(jsonFiles()).get(0);
    }
}
