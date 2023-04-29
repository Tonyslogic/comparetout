/*
 * Copyright (c) 2023. Tony Finnerty
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

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.model.json.scenario.ScenarioJsonFile;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class JsonToolsTest {
    private List<ScenarioJsonFile> scenarioList;
    private static String testData = "";

    @Before public void setUp() throws IOException {
    loadTestData();
}

    @Test
    public void roundTrip() {
        List<ScenarioComponents> jsons = JsonTools.createScenarioComponentList(scenarioList);
        String string = JsonTools.createScenarioList(jsons);
        String after = testData.replaceAll("\t", "  ").replaceAll("\\r\\n?", "\n");
        assertEquals(after, string);
    }

    private void loadTestData() throws IOException {
        Path resourceDirectory = Paths.get("src","debug","res", "raw", "scenarios.json");
        testData = readFile(resourceDirectory.toFile().getAbsolutePath());
        Type type = new TypeToken<List<ScenarioJsonFile>>() {}.getType();
        scenarioList = new Gson().fromJson(testData, type);
    }

    static String readFile(String path) throws IOException
    {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, StandardCharsets.UTF_8);
    }
}