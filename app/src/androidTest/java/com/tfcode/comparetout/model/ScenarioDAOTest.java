/*
 * Copyright (c) 2023-2024. Tony Finnerty
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

package com.tfcode.comparetout.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.scenario.BatteryJson;
import com.tfcode.comparetout.model.json.scenario.DischargeToGridJson;
import com.tfcode.comparetout.model.json.scenario.EVChargeJson;
import com.tfcode.comparetout.model.json.scenario.EVDivertJson;
import com.tfcode.comparetout.model.json.scenario.HWScheduleJson;
import com.tfcode.comparetout.model.json.scenario.InverterJson;
import com.tfcode.comparetout.model.json.scenario.LoadShiftJson;
import com.tfcode.comparetout.model.json.scenario.PanelJson;
import com.tfcode.comparetout.model.json.scenario.ScenarioJsonFile;
import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.DischargeToGrid;
import com.tfcode.comparetout.model.scenario.EVCharge;
import com.tfcode.comparetout.model.scenario.EVDivert;
import com.tfcode.comparetout.model.scenario.HWDivert;
import com.tfcode.comparetout.model.scenario.HWSchedule;
import com.tfcode.comparetout.model.scenario.HWSystem;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class ScenarioDAOTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private volatile ToutcDB toutcDB;
    private ScenarioDAO scenarioDAO;

    @Before
    public void setUp()  {
        Context context = ApplicationProvider.getApplicationContext();
        context.startForegroundService(new Intent(context, ServedService.class));
        toutcDB = Room.inMemoryDatabaseBuilder(context, ToutcDB.class)
                .allowMainThreadQueries()
                .build();
        scenarioDAO = toutcDB.scenarioDAO();
    }

    @After
    public void tearDown() {
        toutcDB.close();
    }

    @Test
    public void copyScenario() throws InterruptedException {
        Scenario scenario1 = new Scenario();
        scenario1.setScenarioName("First");
        LoadProfile loadProfile = new LoadProfile();
        loadProfile.setAnnualUsage(100.00);
        scenarioDAO.addNewScenarioWithComponents(scenario1, new ScenarioComponents(
                scenario1, null, null, null, null, loadProfile,
                null, null, null, null, null, null), false);

        List<Scenario> scenarioOutList = LiveDataTestUtil.getValue(scenarioDAO.loadScenarios());
        Scenario scenario1o = scenarioOutList.get(0);
        long scenario1ID = scenario1o.getScenarioIndex();
        assertEquals("First", scenario1.getScenarioName());
        assertTrue(scenario1o.isHasLoadProfiles());

        LoadProfile loadProfile1o = LiveDataTestUtil.getValue(scenarioDAO.getLoadProfile(scenario1ID));
        long loadProfile1ID = loadProfile1o.getLoadProfileIndex();
        assertEquals(100.00, loadProfile1o.getAnnualUsage(), 0);

        Scenario scenario2 = new Scenario();
        scenario2.setScenarioName("Second");
        scenarioDAO.addNewScenarioWithComponents(scenario2, new ScenarioComponents(
                scenario2, null, null, null, null, null,
                null, null, null, null, null, null), false);

        scenarioOutList = LiveDataTestUtil.getValue(scenarioDAO.loadScenarios());
        Scenario scenario2o = scenarioOutList.get(1);
        long scenario2ID = scenario2o.getScenarioIndex();
        assertEquals("Second", scenario2.getScenarioName());
        LoadProfile loadProfile2o = LiveDataTestUtil.getValue(scenarioDAO.getLoadProfile(scenario2ID));
        assertNull(loadProfile2o);

        scenarioDAO.copyLoadProfileFromScenario(scenario1ID, scenario2ID);

        scenarioOutList = LiveDataTestUtil.getValue(scenarioDAO.loadScenarios());
        Scenario scenario2o2 = scenarioOutList.get(1);
        long scenario22ID = scenario2o2.getScenarioIndex();
        assertEquals("Second", scenario2.getScenarioName());
        assertEquals(scenario22ID, scenario2ID);
        LoadProfile loadProfile2o2 = LiveDataTestUtil.getValue(scenarioDAO.getLoadProfile(scenario22ID));
        assertEquals(100.00, loadProfile2o2.getAnnualUsage(), 0);
        assertNotEquals(loadProfile2o2.getLoadProfileIndex(), loadProfile1o.getLoadProfileIndex());
    }

    @Test
    public void updateLP() throws InterruptedException {
        Scenario scenario1 = new Scenario();
        scenario1.setScenarioName("First");
        LoadProfile loadProfile = new LoadProfile();
        loadProfile.setAnnualUsage(100.00);
        scenarioDAO.addNewScenarioWithComponents(scenario1, new ScenarioComponents(
                scenario1, null, null, null, null, loadProfile,
                null, null, null, null, null, null), false);


        List<Scenario> scenarioOutList = LiveDataTestUtil.getValue(scenarioDAO.loadScenarios());
        Scenario scenario1o = scenarioOutList.get(0);
        long scenario1ID = scenario1o.getScenarioIndex();
        assertEquals("First", scenario1.getScenarioName());
        assertTrue(scenario1o.isHasLoadProfiles());

        LoadProfile loadProfile1o = LiveDataTestUtil.getValue(scenarioDAO.getLoadProfile(scenario1ID));
        assertEquals(100.00, loadProfile1o.getAnnualUsage(), 0);

        loadProfile1o.setAnnualUsage(200.00);
        scenarioDAO.updateLoadProfile(loadProfile1o);

        loadProfile1o = LiveDataTestUtil.getValue(scenarioDAO.getLoadProfile(scenario1ID));
        assertEquals(200.00, loadProfile1o.getAnnualUsage(), 0);
    }

    @Test
    public void linkScenario() throws InterruptedException {
        Scenario scenario1 = new Scenario();
        scenario1.setScenarioName("First");
        LoadProfile loadProfile = new LoadProfile();
        loadProfile.setAnnualUsage(100.00);
        scenarioDAO.addNewScenarioWithComponents(scenario1, new ScenarioComponents(
                scenario1, null, null, null, null, loadProfile,
                null, null, null, null, null, null), false);

        List<Scenario> scenarioOutList = LiveDataTestUtil.getValue(scenarioDAO.loadScenarios());
        Scenario scenario1o = scenarioOutList.get(0);
        long scenario1ID = scenario1o.getScenarioIndex();
        assertEquals("First", scenario1.getScenarioName());
        assertTrue(scenario1o.isHasLoadProfiles());

        LoadProfile loadProfile1o = LiveDataTestUtil.getValue(scenarioDAO.getLoadProfile(scenario1ID));
        long loadProfile1ID = loadProfile1o.getLoadProfileIndex();
        assertEquals(100.00, loadProfile1o.getAnnualUsage(), 0);

        Scenario scenario2 = new Scenario();
        scenario2.setScenarioName("Second");
        scenarioDAO.addNewScenarioWithComponents(scenario2, new ScenarioComponents(
                scenario2, null, null, null, null, null,
                null, null, null, null, null, null), false);

        scenarioOutList = LiveDataTestUtil.getValue(scenarioDAO.loadScenarios());
        Scenario scenario2o = scenarioOutList.get(1);
        long scenario2ID = scenario2o.getScenarioIndex();
        assertEquals("Second", scenario2.getScenarioName());
        LoadProfile loadProfile2o = LiveDataTestUtil.getValue(scenarioDAO.getLoadProfile(scenario2ID));
        assertNull(loadProfile2o);

        scenarioDAO.linkLoadProfileFromScenario(scenario1ID, scenario2ID);

        scenarioOutList = LiveDataTestUtil.getValue(scenarioDAO.loadScenarios());
        Scenario scenario1o2 = scenarioOutList.get(0);
        assertEquals(scenario1o2.getScenarioIndex(), scenario1ID);
        LoadProfile loadProfile1o2 = LiveDataTestUtil.getValue(scenarioDAO.getLoadProfile(scenario1ID));
        assertEquals(loadProfile1o2.getLoadProfileIndex(), loadProfile1ID);
        assertEquals(100.00, loadProfile1o2.getAnnualUsage(), 0);

        Scenario scenario2o2 = scenarioOutList.get(1);
        long scenario22ID = scenario2o2.getScenarioIndex();
        assertEquals("Second", scenario2o2.getScenarioName());
        assertEquals(scenario22ID, scenario2ID);
        LoadProfile loadProfile2o2 = LiveDataTestUtil.getValue(scenarioDAO.getLoadProfile(scenario22ID));
        System.out.println("LPID " + loadProfile2o2.getLoadProfileIndex() + " found for scenario " + scenario22ID);
        assertEquals(100.00, loadProfile2o2.getAnnualUsage(), 0);

        assertEquals(loadProfile2o2.getLoadProfileIndex(), loadProfile1o2.getLoadProfileIndex());

        // Confirm that a change made to the LP retrieved from Scenario2 is visible from Scenario1
        loadProfile2o2.setAnnualUsage(200.00);
        scenarioDAO.updateLoadProfile(loadProfile2o2);

        scenarioOutList = LiveDataTestUtil.getValue(scenarioDAO.loadScenarios());
        Scenario scenario1o3 = scenarioOutList.get(0);
        assertEquals(scenario1o3.getScenarioIndex(), scenario1ID);
        LoadProfile loadProfile1o3 = LiveDataTestUtil.getValue(scenarioDAO.getLoadProfile(scenario1ID));
        assertEquals(loadProfile1o3.getLoadProfileIndex(), loadProfile1ID);
        assertEquals(200.00, loadProfile1o3.getAnnualUsage(), 0);
    }

    @Test
    public void roundTrip() throws InterruptedException {
        Type type = new TypeToken<List<ScenarioJsonFile>>() {}.getType();
        List<ScenarioJsonFile> scenarioList = new Gson().fromJson(testData, type);
        List<ScenarioComponents> jsons = JsonTools.createScenarioComponentList(scenarioList);
        ScenarioComponents json = jsons.get(0);
        scenarioDAO.addNewScenarioWithComponents(json.scenario, json, false);
        List<Scenario> scenarioOutList = LiveDataTestUtil.getValue(scenarioDAO.loadScenarios());
        Scenario scenario = scenarioOutList.get(0);
        long scenarioID = scenario.getScenarioIndex();
        List<Inverter> inverters = scenarioDAO.getInvertersForScenarioID(scenarioID);
        List<Battery> batteries = scenarioDAO.getBatteriesForScenarioID(scenarioID);
        List<Panel> panels = scenarioDAO.getPanelsForScenarioID(scenarioID);
        HWSystem hwSystem = scenarioDAO.getHWSystemForScenarioID(scenarioID);
        LoadProfile loadProfile = scenarioDAO.getLoadProfileForScenarioID(scenarioID);
        List<LoadShift> loadShifts = scenarioDAO.getLoadShiftsForScenarioID(scenarioID);
        List<DischargeToGrid> discharges = scenarioDAO.getDischargesForScenarioID(scenarioID);
        List<EVCharge> evCharges = scenarioDAO.getEVChargesForScenarioID(scenarioID);
        List<HWSchedule> hwSchedules = scenarioDAO.getHWSchedulesForScenarioID(scenarioID);
        HWDivert hwDivert = scenarioDAO.getHWDivertForScenarioID(scenarioID);
        List<EVDivert> evDiverts = scenarioDAO.getEVDivertForScenarioID(scenarioID);

        ScenarioComponents fromDB = new ScenarioComponents(scenario, inverters, batteries,
                panels, hwSystem, loadProfile, loadShifts, discharges, evCharges, hwSchedules, hwDivert,
                evDiverts);
        ArrayList<ScenarioComponents> fromDBList = new ArrayList<>();
        fromDBList.add(fromDB);
        String outString = JsonTools.createScenarioList(fromDBList);

        Gson gson = new Gson();
        type = new TypeToken<List<Map<String, Object>>>(){}.getType();

        List<Map<String, Object>> left = gson.fromJson(testData, type);
        List<Map<String, Object>> right = gson.fromJson(outString, type);
        Map<String, Object> leftmap = left.get(0);
        Map<String, Object> rightmap = right.get(0);

        boolean equal = leftmap.entrySet().stream()
                .allMatch(e -> e.getValue().equals(rightmap.get(e.getKey())));

        assertTrue(equal);

        System.out.println(testData);
        System.out.println("===================================================================================");
        System.out.println(outString);
        // Cannot rely on json order -- need to remove commas, split and sort, then compare
//        outString = outString.replaceAll(",","");
//        String[] outStringArray = outString.split("\n");
//        Arrays.sort(outStringArray);
//
//        // The original also needs to have whitespace removed
//        String inString = testData.replaceAll("\t", "").replaceAll(",", "");
//        String[] inStringArray = inString.split("\n");
//        Arrays.sort(inStringArray);
//
//        for (int index = 0; index < outStringArray.length; index++){
//            assertEquals(inStringArray[index], outStringArray[index]);
//        }
    }

    @Test
    public void loadScenarios() throws InterruptedException {
        loadTestData();
        List<Scenario> scenarioList = LiveDataTestUtil.getValue(scenarioDAO.loadScenarios());
        assertEquals(1, scenarioList.size());
        long scenarioID = scenarioList.get(0).getScenarioIndex();
        System.out.println("1st Scenario ID = " + scenarioID);
        List<Inverter> inverters = scenarioDAO.getInvertersForScenarioID(scenarioID);
        assertEquals(1, inverters.size());
        assertEquals("AlphaESS", inverters.get(0).getInverterName());
    }

    private void loadTestData() {
        Type type = new TypeToken<List<ScenarioJsonFile>>() {}.getType();
        List<ScenarioJsonFile> scenarioList = new Gson().fromJson(ScenarioDAOTest.testData, type);

        Scenario scenario;
        ArrayList<Inverter> inverters = null;
        ArrayList<Battery> batteries = null;
        ArrayList<Panel> panels = null;
        HWSystem hwSystem = null;
        LoadProfile loadProfile = null;
        ArrayList<LoadShift> loadShifts = null;
        ArrayList<DischargeToGrid> discharges = null;
        ArrayList<EVCharge> evCharges = null;
        ArrayList<HWSchedule> hwSchedules = null;
        HWDivert hwDivert = null;
        List<EVDivert> evDiverts = null;

        for (ScenarioJsonFile sjf : scenarioList) {
            scenario = JsonTools.createScenario(sjf);
            if (!(null == sjf.inverters)) {
                inverters = new ArrayList<>();
                for (InverterJson inverterJ : sjf.inverters) {
                    Inverter inverter = JsonTools.createInverter(inverterJ);
                    System.out.println("Created inverter from json " + inverter.getInverterName());
                    inverters.add(inverter);
                }
            }
            if (!(null == sjf.batteries)) {
                batteries = new ArrayList<>();
                for (BatteryJson batteryJ : sjf.batteries) {
                    Battery battery = JsonTools.createBattery(batteryJ);
                    batteries.add(battery);
                }
            }
            if (!(null == sjf.panels)) {
                panels = new ArrayList<>();
                for (PanelJson panelJson : sjf.panels) {
                    Panel panel = JsonTools.createPanel(panelJson);
                    panels.add(panel);
                }
            }
            if (!(null == sjf.hwSystem)){
                hwSystem = JsonTools.createHWSystem(sjf.hwSystem);
            }
            if (!(null == sjf.loadProfile)){
                loadProfile = JsonTools.createLoadProfile(sjf.loadProfile);
            }
            if (!(null == sjf.loadShifts)) {
                loadShifts = new ArrayList<>();
                for (LoadShiftJson loadShiftJson : sjf.loadShifts) {
                    LoadShift loadShift = JsonTools.createLoadShift(loadShiftJson);
                    loadShifts.add(loadShift);
                }
            }
            if (!(null == sjf.dischargeToGrids)) {
                discharges = new ArrayList<>();
                for (DischargeToGridJson dischargeToGridJson : sjf.dischargeToGrids) {
                    DischargeToGrid discharge = JsonTools.createDischarge(dischargeToGridJson);
                    discharges.add(discharge);
                }
            }
            if (!(null == sjf.evCharges)) {
                evCharges = new ArrayList<>();
                for (EVChargeJson evChargeJson : sjf.evCharges) {
                    EVCharge evCharge = JsonTools.createEVCharge(evChargeJson);
                    evCharges.add(evCharge);
                }
            }
            if (!(null == sjf.hwSchedules)) {
                hwSchedules = new ArrayList<>();
                for (HWScheduleJson hwScheduleJson : sjf.hwSchedules) {
                    HWSchedule hwSchedule = JsonTools.createHWSchedule(hwScheduleJson);
                    hwSchedules.add(hwSchedule);
                }
            }
            if (!(null == sjf.hwDivert)){
                hwDivert = JsonTools.createHWDivert(sjf.hwDivert);
            }
            if (!(null == sjf.evDivert)){
                evDiverts = new ArrayList<>();
                for (EVDivertJson evDivertJson: sjf.evDiverts) {
                    EVDivert evDivert = JsonTools.createEVDivert(evDivertJson);
                    evDiverts.add(evDivert);
                }
            }

            ScenarioComponents scenarioComponents =
                    new ScenarioComponents(scenario, inverters, batteries, panels, hwSystem,
                            loadProfile, loadShifts, discharges, evCharges, hwSchedules, hwDivert, evDiverts);
            scenarioDAO.addNewScenarioWithComponents(scenario, scenarioComponents, false);
        }
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
            "        \"MPPT\": 1,\n" +
			"        \"PanelName\": \"Bottom\",\n" +
            "        \"Optimized\": false\n" +
            "      },\n" +
            "      {\n" +
            "        \"PanelCount\": 7,\n" +
            "        \"PanelkWp\": 325,\n" +
            "        \"Azimuth\": 136,\n" +
            "        \"Slope\": 24,\n" +
            "        \"Latitude\": 53.49,\n" +
            "        \"Longitude\": -10.015,\n" +
            "        \"Inverter\": \"AlphaESS\",\n" +
            "        \"MPPT\": 2,\n" +
            "        \"PanelName\": \"Top\",\n" +
            "        \"Optimized\": false\n" +
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
            "      \"GridImportMax\": 15.0,\n" +
            "      \"GridExportMax\": 6.0,\n" +
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
            "    \"DischargeToGrid\": [\n" +
            "      {\n" +
            "        \"Name\": \"Night Dump\",\n" +
            "        \"begin\": 22,\n" +
            "        \"end\": 1,\n" +
            "        \"stop at\": 10.0,\n" +
            "        \"rate\": 0.3,\n" +
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
            "    \"EVDiverts\": [\n" +
            "      {\n" +
            "      \"Name\": \"Afternoon nap\",\n" +
            "      \"active\": true,\n" +
            "      \"ev1st\": true,\n" +
            "      \"begin\": 11,\n" +
            "      \"end\": 16,\n" +
            "      \"dailyMax\": 16.0,\n" +
            "      \"minimum\": 0.0,\n" +
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
            "   ]\n" +
            "  }\n" +
            "]";
}
