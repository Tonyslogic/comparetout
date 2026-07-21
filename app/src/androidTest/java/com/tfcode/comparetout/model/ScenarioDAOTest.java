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
import com.tfcode.comparetout.model.dao.LoadProfileDAO;
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
import com.tfcode.comparetout.model.ops.LoadProfileOps;
import com.tfcode.comparetout.model.ops.ScenarioLifecycleOps;
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
    // The mega-refactor (plans/source/mega-refactor.md, Part C) split the
    // scenario facade: load-profile reads/writes moved to LoadProfileDAO, the
    // copy transaction to LoadProfileOps, and scenario creation to
    // ScenarioLifecycleOps. link*/getXForScenarioID stayed on ScenarioDAO.
    private ScenarioDAO scenarioDAO;
    private LoadProfileDAO loadProfileDAO;
    private LoadProfileOps loadProfileOps;
    private ScenarioLifecycleOps lifecycleOps;

    @Before
    public void setUp()  {
        Context context = ApplicationProvider.getApplicationContext();
        context.startForegroundService(new Intent(context, ServedService.class));
        toutcDB = Room.inMemoryDatabaseBuilder(context, ToutcDB.class)
                .allowMainThreadQueries()
                .build();
        scenarioDAO = toutcDB.scenarioDAO();
        loadProfileDAO = toutcDB.loadProfileDAO();
        loadProfileOps = new LoadProfileOps(toutcDB);
        lifecycleOps = new ScenarioLifecycleOps(toutcDB);
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
        lifecycleOps.addNewScenarioWithComponents(scenario1, new ScenarioComponents(
                scenario1, null, null, null, null, loadProfile,
                null, null, null, null, null, null), false);

        List<Scenario> scenarioOutList = LiveDataTestUtil.getValue(scenarioDAO.loadScenarios());
        Scenario scenario1o = scenarioOutList.get(0);
        long scenario1ID = scenario1o.getScenarioIndex();
        assertEquals("First", scenario1.getScenarioName());
        assertTrue(scenario1o.isHasLoadProfiles());

        LoadProfile loadProfile1o = LiveDataTestUtil.getValue(loadProfileDAO.getLoadProfile(scenario1ID));
        assertEquals(100.00, loadProfile1o.getAnnualUsage(), 0);

        Scenario scenario2 = new Scenario();
        scenario2.setScenarioName("Second");
        lifecycleOps.addNewScenarioWithComponents(scenario2, new ScenarioComponents(
                scenario2, null, null, null, null, null,
                null, null, null, null, null, null), false);

        scenarioOutList = LiveDataTestUtil.getValue(scenarioDAO.loadScenarios());
        Scenario scenario2o = scenarioOutList.get(1);
        long scenario2ID = scenario2o.getScenarioIndex();
        assertEquals("Second", scenario2.getScenarioName());
        LoadProfile loadProfile2o = LiveDataTestUtil.getValue(loadProfileDAO.getLoadProfile(scenario2ID));
        assertNull(loadProfile2o);

        loadProfileOps.copyLoadProfileFromScenario(scenario1ID, scenario2ID);

        scenarioOutList = LiveDataTestUtil.getValue(scenarioDAO.loadScenarios());
        Scenario scenario2o2 = scenarioOutList.get(1);
        long scenario22ID = scenario2o2.getScenarioIndex();
        assertEquals("Second", scenario2.getScenarioName());
        assertEquals(scenario22ID, scenario2ID);
        LoadProfile loadProfile2o2 = LiveDataTestUtil.getValue(loadProfileDAO.getLoadProfile(scenario22ID));
        assertEquals(100.00, loadProfile2o2.getAnnualUsage(), 0);
        assertNotEquals(loadProfile2o2.getLoadProfileIndex(), loadProfile1o.getLoadProfileIndex());
    }

    @Test
    public void updateLP() throws InterruptedException {
        Scenario scenario1 = new Scenario();
        scenario1.setScenarioName("First");
        LoadProfile loadProfile = new LoadProfile();
        loadProfile.setAnnualUsage(100.00);
        lifecycleOps.addNewScenarioWithComponents(scenario1, new ScenarioComponents(
                scenario1, null, null, null, null, loadProfile,
                null, null, null, null, null, null), false);


        List<Scenario> scenarioOutList = LiveDataTestUtil.getValue(scenarioDAO.loadScenarios());
        Scenario scenario1o = scenarioOutList.get(0);
        long scenario1ID = scenario1o.getScenarioIndex();
        assertEquals("First", scenario1.getScenarioName());
        assertTrue(scenario1o.isHasLoadProfiles());

        LoadProfile loadProfile1o = LiveDataTestUtil.getValue(loadProfileDAO.getLoadProfile(scenario1ID));
        assertEquals(100.00, loadProfile1o.getAnnualUsage(), 0);

        loadProfile1o.setAnnualUsage(200.00);
        loadProfileDAO.updateLoadProfile(loadProfile1o);

        loadProfile1o = LiveDataTestUtil.getValue(loadProfileDAO.getLoadProfile(scenario1ID));
        assertEquals(200.00, loadProfile1o.getAnnualUsage(), 0);
    }

    @Test
    public void linkScenario() throws InterruptedException {
        Scenario scenario1 = new Scenario();
        scenario1.setScenarioName("First");
        LoadProfile loadProfile = new LoadProfile();
        loadProfile.setAnnualUsage(100.00);
        lifecycleOps.addNewScenarioWithComponents(scenario1, new ScenarioComponents(
                scenario1, null, null, null, null, loadProfile,
                null, null, null, null, null, null), false);

        List<Scenario> scenarioOutList = LiveDataTestUtil.getValue(scenarioDAO.loadScenarios());
        Scenario scenario1o = scenarioOutList.get(0);
        long scenario1ID = scenario1o.getScenarioIndex();
        assertEquals("First", scenario1.getScenarioName());
        assertTrue(scenario1o.isHasLoadProfiles());

        LoadProfile loadProfile1o = LiveDataTestUtil.getValue(loadProfileDAO.getLoadProfile(scenario1ID));
        long loadProfile1ID = loadProfile1o.getLoadProfileIndex();
        assertEquals(100.00, loadProfile1o.getAnnualUsage(), 0);

        Scenario scenario2 = new Scenario();
        scenario2.setScenarioName("Second");
        lifecycleOps.addNewScenarioWithComponents(scenario2, new ScenarioComponents(
                scenario2, null, null, null, null, null,
                null, null, null, null, null, null), false);

        scenarioOutList = LiveDataTestUtil.getValue(scenarioDAO.loadScenarios());
        Scenario scenario2o = scenarioOutList.get(1);
        long scenario2ID = scenario2o.getScenarioIndex();
        assertEquals("Second", scenario2.getScenarioName());
        LoadProfile loadProfile2o = LiveDataTestUtil.getValue(loadProfileDAO.getLoadProfile(scenario2ID));
        assertNull(loadProfile2o);

        scenarioDAO.linkLoadProfileFromScenario(scenario1ID, scenario2ID);

        scenarioOutList = LiveDataTestUtil.getValue(scenarioDAO.loadScenarios());
        Scenario scenario1o2 = scenarioOutList.get(0);
        assertEquals(scenario1o2.getScenarioIndex(), scenario1ID);
        LoadProfile loadProfile1o2 = LiveDataTestUtil.getValue(loadProfileDAO.getLoadProfile(scenario1ID));
        assertEquals(loadProfile1o2.getLoadProfileIndex(), loadProfile1ID);
        assertEquals(100.00, loadProfile1o2.getAnnualUsage(), 0);

        Scenario scenario2o2 = scenarioOutList.get(1);
        long scenario22ID = scenario2o2.getScenarioIndex();
        assertEquals("Second", scenario2o2.getScenarioName());
        assertEquals(scenario22ID, scenario2ID);
        LoadProfile loadProfile2o2 = LiveDataTestUtil.getValue(loadProfileDAO.getLoadProfile(scenario22ID));
        System.out.println("LPID " + loadProfile2o2.getLoadProfileIndex() + " found for scenario " + scenario22ID);
        assertEquals(100.00, loadProfile2o2.getAnnualUsage(), 0);

        assertEquals(loadProfile2o2.getLoadProfileIndex(), loadProfile1o2.getLoadProfileIndex());

        // Confirm that a change made to the LP retrieved from Scenario2 is visible from Scenario1
        loadProfile2o2.setAnnualUsage(200.00);
        loadProfileDAO.updateLoadProfile(loadProfile2o2);

        scenarioOutList = LiveDataTestUtil.getValue(scenarioDAO.loadScenarios());
        Scenario scenario1o3 = scenarioOutList.get(0);
        assertEquals(scenario1o3.getScenarioIndex(), scenario1ID);
        LoadProfile loadProfile1o3 = LiveDataTestUtil.getValue(loadProfileDAO.getLoadProfile(scenario1ID));
        assertEquals(loadProfile1o3.getLoadProfileIndex(), loadProfile1ID);
        assertEquals(200.00, loadProfile1o3.getAnnualUsage(), 0);
    }

    @Test
    public void roundTrip() throws InterruptedException {
        Type type = new TypeToken<List<ScenarioJsonFile>>() {}.getType();
        List<ScenarioJsonFile> scenarioList = new Gson().fromJson(testData, type);
        List<ScenarioComponents> jsons = JsonTools.createScenarioComponentList(scenarioList);
        ScenarioComponents json = jsons.get(0);
        lifecycleOps.addNewScenarioWithComponents(json.scenario, json, false);
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
            lifecycleOps.addNewScenarioWithComponents(scenario, scenarioComponents, false);
        }
    }


    private static final String testData = """
            [
              {
                "Name": "Default",
                "Inverters": [
                  {
                    "Name": "AlphaESS",
                    "MinExcess": 0.008,
                    "MaxInverterLoad": 5.0,
                    "MPPTCount": 2,
                    "AC2DCLoss": 5,
                    "DC2ACLoss": 5,
                    "DC2DCLoss": 0
                  }
                ],
                "Batteries": [
                  {
                    "Battery Size": 5.7,
                    "Discharge stop": 19.6,
                    "ChargeModel": {
                      "0": 30,
                      "12": 100,
                      "90": 10,
                      "100": 0
                    },
                    "Max discharge": 0.225,
                    "Max charge": 0.225,
                    "StorageLoss": 1.0,
                    "Inverter": "AlphaESS"
                  }
                ],
                "Panels": [
                  {
                    "PanelCount": 7,
                    "PanelkWp": 325,
                    "Azimuth": 136,
                    "Slope": 24,
                    "Latitude": 53.49,
                    "Longitude": -10.015,
                    "Inverter": "AlphaESS",
                    "MPPT": 1,
                    "PanelName": "Bottom",
                    "Optimized": false
                  },
                  {
                    "PanelCount": 7,
                    "PanelkWp": 325,
                    "Azimuth": 136,
                    "Slope": 24,
                    "Latitude": 53.49,
                    "Longitude": -10.015,
                    "Inverter": "AlphaESS",
                    "MPPT": 2,
                    "PanelName": "Top",
                    "Optimized": false
                  }
                ],
                "HWSystem": {
                  "HWCapacity": 165,
                  "HWUsage": 200,
                  "HWIntake": 15,
                  "HWTarget": 75,
                  "HWLoss": 8,
                  "HWRate": 2.5,
                  "HWUse": [
                    [
                      8.0,
                      75.0
                    ],
                    [
                      14.0,
                      10.0
                    ],
                    [
                      20.0,
                      15.0
                    ]
                  ]
                },
                "LoadProfile": {
                  "AnnualUsage": 6144.789999999933,
                  "HourlyBaseLoad": 0.3,
                  "GridImportMax": 15.0,
                  "GridExportMax": 6.0,
                  "HourlyDistribution": [
                    3.0056773610654353,
                    2.824252131263466,
                    3.4755195625398114,
                    3.6248283037336013,
                    2.093251894140612,
                    2.106981341607451,
                    4.149806425114616,
                    3.843753314428644,
                    3.417200999859707,
                    3.425837430707642,
                    3.9928786092224255,
                    5.452673766064777,
                    5.748837165015935,
                    4.826126764091809,
                    4.9637045161974696,
                    4.360966086002315,
                    4.648544388192286,
                    8.002641709566198,
                    6.471674103070658,
                    4.120896376909735,
                    3.9126648710120393,
                    3.852714108738398,
                    4.005308302203635,
                    3.673260469252478
                  ],
                  "DayOfWeekDistribution": {
                    "Sun": 15.021017805327919,
                    "Mon": 14.665269276899732,
                    "Tue": 13.095484141850466,
                    "Wed": 13.72967994024214,
                    "Thu": 14.019193495628194,
                    "Fri": 13.658400042963336,
                    "Sat": 15.810955297089196
                  },
                  "MonthlyDistribution": {
                    "Oct": 7.748027190514326,
                    "Nov": 7.521168339357488,
                    "Dec": 8.94741724290018,
                    "Jan": 9.354591450643655,
                    "Feb": 7.068101595009833,
                    "Mar": 8.110936256568662,
                    "Apr": 8.134533482836767,
                    "May": 8.085711635385511,
                    "Jun": 8.621775520400304,
                    "Jul": 8.487678179400852,
                    "Aug": 9.310489048446023,
                    "Sep": 8.609570058537491
                  }
                },
                "LoadShift": [
                  {
                    "Name": "Smart night",
                    "begin": 2,
                    "end": 4,
                    "stop at": 80.0,
                    "months": [
                      1,
                      2,
                      3,
                      4,
                      5,
                      6,
                      7,
                      8,
                      9,
                      10,
                      11,
                      12
                    ],
                    "days": [
                      0,
                      1,
                      2,
                      3,
                      4,
                      5
                    ]
                  }
                ],
                "DischargeToGrid": [
                  {
                    "Name": "Night Dump",
                    "begin": 22,
                    "end": 1,
                    "stop at": 10.0,
                    "rate": 0.3,
                    "months": [
                      1,
                      2,
                      3,
                      4,
                      5,
                      6,
                      7,
                      8,
                      9,
                      10,
                      11,
                      12
                    ],
                    "days": [
                      0,
                      1,
                      2,
                      3,
                      4,
                      5
                    ]
                  }
                ],
                "EVCharge": [
                  {
                    "Name": "Smart night",
                    "begin": 2,
                    "end": 4,
                    "draw": 7.5,
                    "months": [
                      1,
                      2,
                      3,
                      4,
                      5,
                      6,
                      7,
                      8,
                      9,
                      10,
                      11,
                      12
                    ],
                    "days": [
                      0,
                      1,
                      2,
                      3,
                      4,
                      5,
                      6
                    ]
                  }
                ],
                "HWSchedule": [
                  {
                    "Name": "Smart night",
                    "begin": 3,
                    "end": 6,
                    "months": [
                      1,
                      2,
                      3,
                      4,
                      5,
                      6,
                      7,
                      8,
                      9,
                      10,
                      11,
                      12
                    ],
                    "days": [
                      0,
                      1,
                      2,
                      3,
                      4,
                      5,
                      6
                    ]
                  }
                ],
                "HWDivert": {
                  "active": true
                },
                "EVDiverts": [
                  {
                  "Name": "Afternoon nap",
                  "active": true,
                  "ev1st": true,
                  "begin": 11,
                  "end": 16,
                  "dailyMax": 16.0,
                  "minimum": 0.0,
                  "months": [
                    7
                  ],
                  "days": [
                    0,
                    1,
                    2,
                    3,
                    4,
                    5,
                    6
                  ]
                }
               ]
              }
            ]""";
}
