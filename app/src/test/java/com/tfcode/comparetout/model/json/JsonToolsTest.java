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