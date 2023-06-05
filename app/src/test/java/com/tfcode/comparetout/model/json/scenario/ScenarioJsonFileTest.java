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

package com.tfcode.comparetout.model.json.scenario;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class ScenarioJsonFileTest {

    private List<ScenarioJsonFile> scenarioList;

    @Before
    public void setUp() throws IOException {
        loadTestData();
    }

    @Test
    public void testJsonParsing() {
        assertEquals(1, scenarioList.size());
        assertEquals("Default", scenarioList.get(0).name);
        assertEquals("AlphaESS", scenarioList.get(0).inverters.get(0).name);
        assertEquals(2, scenarioList.get(0).inverters.get(0).mPPTCount.longValue());
        assertEquals(1, scenarioList.get(0).batteries.size());
        assertEquals(30, scenarioList.get(0).batteries.get(0).chargeModel.percent0.longValue());
        assertEquals(2, scenarioList.get(0).panels.size());
        assertEquals(2, scenarioList.get(0).panels.get(1).mppt.longValue());
        assertEquals("AlphaESS", scenarioList.get(0).panels.get(0).inverter);
        assertEquals(53.490, scenarioList.get(0).panels.get(0).latitude, 0);
        assertEquals(0.3, scenarioList.get(0).loadProfile.hourlyBaseLoad, 0);
        assertEquals(15.0, scenarioList.get(0).loadProfile.gridImportMax, 0);
        assertEquals(13.658400042963337,
                scenarioList.get(0).loadProfile.dayOfWeekDistribution.fri, 0);
        assertTrue(scenarioList.get(0).loadShifts.get(0).days.contains(0));
        assertEquals(6, scenarioList.get(0).loadShifts.get(0).days.size());
        assertTrue(scenarioList.get(0).loadShifts.get(0).months.contains(1));
        assertEquals(12, scenarioList.get(0).loadShifts.get(0).months.size());
        assertEquals(7.5, scenarioList.get(0).evCharges.get(0).draw, 0);
        assertTrue(scenarioList.get(0).hwSchedules.get(0).months.contains(6));
        assertTrue(scenarioList.get(0).hwDivert.active);
        assertTrue(scenarioList.get(0).evDiverts.get(0).ev1st);
        assertEquals(16.0, scenarioList.get(0).evDiverts.get(0).dailyMax, 0);

    }

    @Test
    public void testMissingComponents() {
        Type type = new TypeToken<List<ScenarioJsonFile>>() {}.getType();
        scenarioList = new Gson().fromJson(minimalScenario, type);
        assertEquals(1, scenarioList.size());
        assertEquals(2, scenarioList.get(0).inverters.get(0).mPPTCount.longValue());
        assertNull(scenarioList.get(0).evDivert);
    }

    private void loadTestData() throws IOException {
        Path resourceDirectory = Paths.get("src","debug","res", "raw", "scenarios.json");
        String testData = readFile(resourceDirectory.toFile().getAbsolutePath());
        Type type = new TypeToken<List<ScenarioJsonFile>>() {}.getType();
        scenarioList = new Gson().fromJson(testData, type);
    }

    static String readFile(String path)
            throws IOException
    {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, StandardCharsets.UTF_8);
    }

    String minimalScenario = "[{\"Name\": \"Empty\",\"Inverters\": [{\n" +
            "\t\t\t\"Name\": \"AlphaESS\",\n" +
            "\t\t\t\"MinExcess\": 0.008,\n" +
            "\t\t\t\"MaxInverterLoad\": 5.0,\n" +
            "\t\t\t\"MPPTCount\": 2,\n" +
            "\t\t\t\"AC2DCLoss\": 5,\n" +
            "\t\t\t\"DC2ACLoss\": 5,\n" +
            "\t\t\t\"DC2DCLoss\": 0\n" +
            "\t\t}]}]";
}