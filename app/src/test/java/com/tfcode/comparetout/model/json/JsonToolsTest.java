package com.tfcode.comparetout.model.json;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.model.json.scenario.ScenarioJsonFile;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Type;
import java.util.List;

public class JsonToolsTest {
    private List<ScenarioJsonFile> scenarioList;

    @Before public void setUp() {
    loadTestData();
}

    @Test
    public void roundTrip() {
        List<ScenarioComponents> jsons = JsonTools.createScenarioComponentList(scenarioList);
        String string = JsonTools.createScenarioList(jsons);
        String after = testData.replaceAll("\t", "  ");
        assertEquals(after, string);
    }

    private void loadTestData() {
        Type type = new TypeToken<List<ScenarioJsonFile>>() {}.getType();
        scenarioList = new Gson().fromJson(testData, type);
    }
    private static final String testData = "[\n" +
            "  {\n" +
            "    \"Name\": \"Default\",\n" +
            "    \"Inverters\": [\n" +
            "      {\n" +
            "        \"Name\": \"AlphaESS\",\n" +
            "        \"MinExcess\": 0.008,\n" +
            "        \"MaxInverterLoad\": 5.0,\n" +
            "        \"MPPTCount\": 2,\n" +
            "        \"AC2DCLoss\": 5,\n" +
            "        \"DC2ACLoss\": 5,\n" +
            "        \"DC2DCLoss\": 0\n" +
            "      }\n" +
            "    ],\n" +
            "    \"Batteries\": [\n" +
            "      {\n" +
            "        \"Battery Size\": 5.7,\n" +
            "        \"Discharge stop\": 19.6,\n" +
            "        \"ChargeModel\": {\n" +
            "          \"0\": 30,\n" +
            "          \"12\": 100,\n" +
            "          \"90\": 10,\n" +
            "          \"100\": 0\n" +
            "        },\n" +
            "        \"Max discharge\": 0.225,\n" +
            "        \"Max charge\": 0.225,\n" +
            "        \"StorageLoss\": 1.0,\n" +
            "        \"Inverter\": \"AlphaESS\"\n" +
            "      }\n" +
            "    ],\n" +
            "    \"Panels\": [\n" +
            "      {\n" +
            "        \"PanelCount\": 7,\n" +
            "        \"PanelkWp\": 325,\n" +
            "        \"Azimuth\": 136,\n" +
            "        \"Slope\": 24,\n" +
            "        \"Latitude\": 53.49,\n" +
            "        \"Longitude\": -10.015,\n" +
            "        \"Inverter\": \"AlphaESS\",\n" +
            "        \"MPPT\": 1\n" +
            "      },\n" +
            "      {\n" +
            "        \"PanelCount\": 7,\n" +
            "        \"PanelkWp\": 325,\n" +
            "        \"Azimuth\": 136,\n" +
            "        \"Slope\": 24,\n" +
            "        \"Latitude\": 53.49,\n" +
            "        \"Longitude\": -10.015,\n" +
            "        \"Inverter\": \"AlphaESS\",\n" +
            "        \"MPPT\": 2\n" +
            "      }\n" +
            "    ],\n" +
            "    \"HWSystem\": {\n" +
            "      \"HWCapacity\": 165,\n" +
            "      \"HWUsage\": 200,\n" +
            "      \"HWIntake\": 15,\n" +
            "      \"HWTarget\": 75,\n" +
            "      \"HWLoss\": 8,\n" +
            "      \"HWRate\": 2.5,\n" +
            "      \"HWUse\": [\n" +
            "        [\n" +
            "          8.0,\n" +
            "          75.0\n" +
            "        ],\n" +
            "        [\n" +
            "          14.0,\n" +
            "          10.0\n" +
            "        ],\n" +
            "        [\n" +
            "          20.0,\n" +
            "          15.0\n" +
            "        ]\n" +
            "      ]\n" +
            "    },\n" +
            "    \"LoadProfile\": {\n" +
            "      \"AnnualUsage\": 6144.789999999933,\n" +
            "      \"HourlyBaseLoad\": 0.3,\n" +
            "      \"HourlyDistribution\": [\n" +
            "        3.0056773610654353,\n" +
            "        2.824252131263466,\n" +
            "        3.4755195625398114,\n" +
            "        3.6248283037336013,\n" +
            "        2.093251894140612,\n" +
            "        2.106981341607451,\n" +
            "        4.149806425114616,\n" +
            "        3.843753314428644,\n" +
            "        3.417200999859707,\n" +
            "        3.425837430707642,\n" +
            "        3.9928786092224255,\n" +
            "        5.452673766064777,\n" +
            "        5.748837165015935,\n" +
            "        4.826126764091809,\n" +
            "        4.9637045161974696,\n" +
            "        4.360966086002315,\n" +
            "        4.648544388192286,\n" +
            "        8.002641709566198,\n" +
            "        6.471674103070658,\n" +
            "        4.120896376909735,\n" +
            "        3.9126648710120393,\n" +
            "        3.852714108738398,\n" +
            "        4.005308302203635,\n" +
            "        3.673260469252478\n" +
            "      ],\n" +
            "      \"DayOfWeekDistribution\": {\n" +
            "        \"Sun\": 15.021017805327919,\n" +
            "        \"Mon\": 14.665269276899732,\n" +
            "        \"Tue\": 13.095484141850466,\n" +
            "        \"Wed\": 13.72967994024214,\n" +
            "        \"Thu\": 14.019193495628194,\n" +
            "        \"Fri\": 13.658400042963336,\n" +
            "        \"Sat\": 15.810955297089196\n" +
            "      },\n" +
            "      \"MonthlyDistribution\": {\n" +
            "        \"Oct\": 7.748027190514326,\n" +
            "        \"Nov\": 7.521168339357488,\n" +
            "        \"Dec\": 8.94741724290018,\n" +
            "        \"Jan\": 9.354591450643655,\n" +
            "        \"Feb\": 7.068101595009833,\n" +
            "        \"Mar\": 8.110936256568662,\n" +
            "        \"Apr\": 8.134533482836767,\n" +
            "        \"May\": 8.085711635385511,\n" +
            "        \"Jun\": 8.621775520400304,\n" +
            "        \"Jul\": 8.487678179400852,\n" +
            "        \"Aug\": 9.310489048446023,\n" +
            "        \"Sep\": 8.609570058537491\n" +
            "      }\n" +
            "    },\n" +
            "    \"LoadShift\": [\n" +
            "      {\n" +
            "        \"Name\": \"Smart night\",\n" +
            "        \"begin\": 2,\n" +
            "        \"end\": 4,\n" +
            "        \"stop at\": 80.0,\n" +
            "        \"months\": [\n" +
            "          1,\n" +
            "          2,\n" +
            "          3,\n" +
            "          4,\n" +
            "          5,\n" +
            "          6,\n" +
            "          7,\n" +
            "          8,\n" +
            "          9,\n" +
            "          10,\n" +
            "          11,\n" +
            "          12\n" +
            "        ],\n" +
            "        \"days\": [\n" +
            "          0,\n" +
            "          1,\n" +
            "          2,\n" +
            "          3,\n" +
            "          4,\n" +
            "          5\n" +
            "        ]\n" +
            "      }\n" +
            "    ],\n" +
            "    \"EVCharge\": [\n" +
            "      {\n" +
            "        \"Name\": \"Smart night\",\n" +
            "        \"begin\": 2,\n" +
            "        \"end\": 4,\n" +
            "        \"draw\": 7.5,\n" +
            "        \"months\": [\n" +
            "          1,\n" +
            "          2,\n" +
            "          3,\n" +
            "          4,\n" +
            "          5,\n" +
            "          6,\n" +
            "          7,\n" +
            "          8,\n" +
            "          9,\n" +
            "          10,\n" +
            "          11,\n" +
            "          12\n" +
            "        ],\n" +
            "        \"days\": [\n" +
            "          0,\n" +
            "          1,\n" +
            "          2,\n" +
            "          3,\n" +
            "          4,\n" +
            "          5,\n" +
            "          6\n" +
            "        ]\n" +
            "      }\n" +
            "    ],\n" +
            "    \"HWSchedule\": [\n" +
            "      {\n" +
            "        \"Name\": \"Smart night\",\n" +
            "        \"begin\": 3,\n" +
            "        \"end\": 6,\n" +
            "        \"months\": [\n" +
            "          1,\n" +
            "          2,\n" +
            "          3,\n" +
            "          4,\n" +
            "          5,\n" +
            "          6,\n" +
            "          7,\n" +
            "          8,\n" +
            "          9,\n" +
            "          10,\n" +
            "          11,\n" +
            "          12\n" +
            "        ],\n" +
            "        \"days\": [\n" +
            "          0,\n" +
            "          1,\n" +
            "          2,\n" +
            "          3,\n" +
            "          4,\n" +
            "          5,\n" +
            "          6\n" +
            "        ]\n" +
            "      }\n" +
            "    ],\n" +
            "    \"HWDivert\": {\n" +
            "      \"active\": true\n" +
            "    },\n" +
            "    \"EVDivert\": {\n" +
            "      \"Name\": \"Afternoon nap\",\n" +
            "      \"active\": true,\n" +
            "      \"ev1st\": true,\n" +
            "      \"begin\": 11,\n" +
            "      \"end\": 16,\n" +
            "      \"dailyMax\": 16.0,\n" +
            "      \"months\": [\n" +
            "        7\n" +
            "      ],\n" +
            "      \"days\": [\n" +
            "        0,\n" +
            "        1,\n" +
            "        2,\n" +
            "        3,\n" +
            "        4,\n" +
            "        5,\n" +
            "        6\n" +
            "      ]\n" +
            "    }\n" +
            "  }\n" +
            "]";


}