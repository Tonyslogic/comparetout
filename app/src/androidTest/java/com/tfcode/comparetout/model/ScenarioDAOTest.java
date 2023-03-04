package com.tfcode.comparetout.model;

import static org.junit.Assert.assertEquals;

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
import com.tfcode.comparetout.model.json.scenario.EVChargeJson;
import com.tfcode.comparetout.model.json.scenario.HWScheduleJson;
import com.tfcode.comparetout.model.json.scenario.InverterJson;
import com.tfcode.comparetout.model.json.scenario.LoadShiftJson;
import com.tfcode.comparetout.model.json.scenario.PanelJson;
import com.tfcode.comparetout.model.json.scenario.ScenarioJsonFile;
import com.tfcode.comparetout.model.scenario.Battery;
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
        scenarioDAO = toutcDB.sceanrioDAO();
    }

    @After
    public void tearDown() {
        toutcDB.close();
    }

    @Test
    public void loadScenarios() throws InterruptedException {
        loadTestData(testData);
        List<Scenario> scenarioList = LiveDataTestUtil.getValue(scenarioDAO.loadScenarios());
        assertEquals(1, scenarioList.size());
        long scenarioID = scenarioList.get(0).getId();
        System.out.println("1st Scenario ID = " + scenarioID);
        List<Inverter> inverters = scenarioDAO.getInvertersForScenarioID(scenarioID);
        assertEquals(1, inverters.size());
        assertEquals("AlphaESS", inverters.get(0).getInverterName());
    }

    private void loadTestData(String dataToLoad) {
        Type type = new TypeToken<List<ScenarioJsonFile>>() {}.getType();
        List<ScenarioJsonFile> scenarioList = new Gson().fromJson(dataToLoad, type);

        Scenario scenario;
        ArrayList<Inverter> inverters = null;
        ArrayList<Battery> batteries = null;
        ArrayList<Panel> panels = null;
        HWSystem hwSystem = null;
        LoadProfile loadProfile = null;
        ArrayList<LoadShift> loadShifts = null;
        ArrayList<EVCharge> evCharges = null;
        ArrayList<HWSchedule> hwSchedules = null;
        HWDivert hwDivert = null;
        EVDivert evDivert = null;

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
                evDivert = JsonTools.createEVDivert(sjf.evDivert);
            }

            ScenarioComponents scenarioComponents =
                    new ScenarioComponents(scenario, inverters, batteries, panels, hwSystem,
                            loadProfile, loadShifts, evCharges, hwSchedules, hwDivert, evDivert);
            scenarioDAO.addNewScenarioWithComponents(scenario, scenarioComponents);
        }
    }

    private final String testData = "[\n" +
            "\t{\n" +
            "\t\t\"Name\": \"Default\",\n" +
            "\t\t\"Inverters\": [{\n" +
            "\t\t\t\"Name\": \"AlphaESS\",\n" +
            "\t\t\t\"MinExcess\": 0.008,\n" +
            "\t\t\t\"MaxInverterLoad\": 5.0,\n" +
            "\t\t\t\"MPPTCount\": 2,\n" +
            "\t\t\t\"AC2DCLoss\": 5,\n" +
            "\t\t\t\"DC2ACLoss\": 5,\n" +
            "\t\t\t\"DC2DCLoss\": 0\n" +
            "\t\t}],\n" +
            "\t\t\"Batteries\": [{\n" +
            "\t\t\t\"Battery Size\": 5.7,\n" +
            "\t\t\t\"Discharge stop\": 19.6,\n" +
            "\t\t\t\"ChargeModel\": {\n" +
            "\t\t\t\t\"0\": 30,\n" +
            "\t\t\t\t\"12\": 100,\n" +
            "\t\t\t\t\"90\": 10,\n" +
            "\t\t\t\t\"100\": 0\n" +
            "\t\t\t},\n" +
            "\t\t\t\"Max discharge\": 0.225,\n" +
            "\t\t\t\"Max charge\": 0.225,\n" +
            "\t\t\t\"StorageLoss\": 1,\n" +
            "\t\t\t\"Inverter\": \"AlphaESS\"\n" +
            "\t\t}],\n" +
            "\t\t\"Panels\": [{\n" +
            "\t\t\t\"PanelCount\": 7,\n" +
            "\t\t\t\"PanelkWp\" : 325,\n" +
            "\t\t\t\"Azimuth\" : 136,\n" +
            "\t\t\t\"Slope\": 24,\n" +
            "\t\t\t\"Latitude\": \"53.490\",\n" +
            "\t\t\t\"Longitude\": \"-10.015\",\n" +
            "\t\t\t\"Inverter\": \"AlphaESS\",\n" +
            "\t\t\t\"MPPT\": \"1\"\n" +
            "\t\t\t},\n" +
            "\t\t\t{\n" +
            "\t\t\t\"PanelCount\": 7,\n" +
            "\t\t\t\"PanelkWp\" : 325,\n" +
            "\t\t\t\"Azimuth\" : 136,\n" +
            "\t\t\t\"Slope\": 24,\n" +
            "\t\t\t\"Latitude\": \"53.490\",\n" +
            "\t\t\t\"Longitude\": \"-10.015\",\n" +
            "\t\t\t\"Inverter\": \"AlphaESS\",\n" +
            "\t\t\t\"MPPT\": \"2\"\n" +
            "\t\t\t}],\n" +
            "\t\t\"HWSystem\": {\t\t\t\n" +
            "\t\t\t\"HWCapacity\": 165,\n" +
            "\t\t\t\"HWUsage\": 200,\n" +
            "\t\t\t\"HWIntake\": 15,\n" +
            "\t\t\t\"HWTarget\": 75,\n" +
            "\t\t\t\"HWLoss\": 8,\n" +
            "\t\t\t\"HWRate\": 2.5,\n" +
            "\t\t\t\"HWUse\": [\n" +
            "\t\t\t\t[\n" +
            "\t\t\t\t\t8.0,\n" +
            "\t\t\t\t\t75.0\n" +
            "\t\t\t\t],\n" +
            "\t\t\t\t[\n" +
            "\t\t\t\t\t14,\n" +
            "\t\t\t\t\t10\n" +
            "\t\t\t\t],\n" +
            "\t\t\t\t[\n" +
            "\t\t\t\t\t20,\n" +
            "\t\t\t\t\t15\n" +
            "\t\t\t\t]\n" +
            "\t\t\t]\n" +
            "\t\t},\n" +
            "\t\t\"LoadProfile\": {\n" +
            "\t\t\t\"AnnualUsage\": 6144.789999999933,\n" +
            "\t\t\t\"HourlyBaseLoad\": 0.3,\n" +
            "\t\t\t\"HourlyDistribution\": [\n" +
            "\t\t\t\t3.0056773610654354,\n" +
            "\t\t\t\t2.824252131263466,\n" +
            "\t\t\t\t3.4755195625398116,\n" +
            "\t\t\t\t3.6248283037336015,\n" +
            "\t\t\t\t2.093251894140612,\n" +
            "\t\t\t\t2.106981341607451,\n" +
            "\t\t\t\t4.149806425114616,\n" +
            "\t\t\t\t3.843753314428644,\n" +
            "\t\t\t\t3.417200999859707,\n" +
            "\t\t\t\t3.425837430707642,\n" +
            "\t\t\t\t3.9928786092224257,\n" +
            "\t\t\t\t5.452673766064777,\n" +
            "\t\t\t\t5.748837165015935,\n" +
            "\t\t\t\t4.826126764091809,\n" +
            "\t\t\t\t4.9637045161974699,\n" +
            "\t\t\t\t4.360966086002315,\n" +
            "\t\t\t\t4.648544388192286,\n" +
            "\t\t\t\t8.002641709566199,\n" +
            "\t\t\t\t6.471674103070658,\n" +
            "\t\t\t\t4.120896376909735,\n" +
            "\t\t\t\t3.9126648710120395,\n" +
            "\t\t\t\t3.852714108738398,\n" +
            "\t\t\t\t4.005308302203635,\n" +
            "\t\t\t\t3.673260469252478\n" +
            "\t\t\t],\n" +
            "\t\t\t\"DayOfWeekDistribution\": {\n" +
            "\t\t\t\t\"Sun\": 15.021017805327919,\n" +
            "\t\t\t\t\"Mon\": 14.665269276899732,\n" +
            "\t\t\t\t\"Tue\": 13.095484141850467,\n" +
            "\t\t\t\t\"Wed\": 13.72967994024214,\n" +
            "\t\t\t\t\"Thu\": 14.019193495628194,\n" +
            "\t\t\t\t\"Fri\": 13.658400042963337,\n" +
            "\t\t\t\t\"Sat\": 15.810955297089196\n" +
            "\t\t\t},\n" +
            "\t\t\t\"MonthlyDistribution\": {\n" +
            "\t\t\t\t\t\"Oct\": 7.748027190514326,\n" +
            "\t\t\t\t\t\"Nov\": 7.521168339357488,\n" +
            "\t\t\t\t\t\"Dec\": 8.94741724290018,\n" +
            "\t\t\t\t\t\"Jan\": 9.354591450643655,\n" +
            "\t\t\t\t\t\"Feb\": 7.068101595009833,\n" +
            "\t\t\t\t\t\"Mar\": 8.110936256568662,\n" +
            "\t\t\t\t\t\"Apr\": 8.134533482836768,\n" +
            "\t\t\t\t\t\"May\": 8.085711635385512,\n" +
            "\t\t\t\t\t\"Jun\": 8.621775520400304,\n" +
            "\t\t\t\t\t\"Jul\": 8.487678179400853,\n" +
            "\t\t\t\t\t\"Aug\": 9.310489048446023,\n" +
            "\t\t\t\t\t\"Sep\": 8.609570058537491\n" +
            "\t\t\t}\n" +
            "\t\t},\n" +
            "\t\t\"LoadShift\": [{\n" +
            "\t\t\t\"Name\": \"Smart night\",\n" +
            "\t\t\t\"begin\": 2,\n" +
            "\t\t\t\"end\": 4,\n" +
            "\t\t\t\"stop at\": 80,\n" +
            "\t\t\t\"months\": [\n" +
            "\t\t\t\t1,\n" +
            "\t\t\t\t2,\n" +
            "\t\t\t\t3,\n" +
            "\t\t\t\t4,\n" +
            "\t\t\t\t5,\n" +
            "\t\t\t\t6,\n" +
            "\t\t\t\t7,\n" +
            "\t\t\t\t8,\n" +
            "\t\t\t\t9,\n" +
            "\t\t\t\t10,\n" +
            "\t\t\t\t11,\n" +
            "\t\t\t\t12\n" +
            "\t\t\t],\n" +
            "\t\t\t\"days\": [\n" +
            "\t\t\t\t0,\n" +
            "\t\t\t\t1,\n" +
            "\t\t\t\t2,\n" +
            "\t\t\t\t3,\n" +
            "\t\t\t\t4,\n" +
            "\t\t\t\t5\n" +
            "\t\t\t]\n" +
            "\t\t}],\n" +
            "\t\t\"EVCharge\": [{\n" +
            "\t\t\t\"Name\": \"Smart night\",\n" +
            "\t\t\t\"begin\": 2,\n" +
            "\t\t\t\"end\": 4,\n" +
            "\t\t\t\"draw\": 7.5,\n" +
            "\t\t\t\"months\": [\n" +
            "\t\t\t\t1,\n" +
            "\t\t\t\t2,\n" +
            "\t\t\t\t3,\n" +
            "\t\t\t\t4,\n" +
            "\t\t\t\t5,\n" +
            "\t\t\t\t6,\n" +
            "\t\t\t\t7,\n" +
            "\t\t\t\t8,\n" +
            "\t\t\t\t9,\n" +
            "\t\t\t\t10,\n" +
            "\t\t\t\t11,\n" +
            "\t\t\t\t12\n" +
            "\t\t\t],\n" +
            "\t\t\t\"days\": [\n" +
            "\t\t\t\t0,\n" +
            "\t\t\t\t1,\n" +
            "\t\t\t\t2,\n" +
            "\t\t\t\t3,\n" +
            "\t\t\t\t4,\n" +
            "\t\t\t\t5,\n" +
            "\t\t\t\t6\n" +
            "\t\t\t]\n" +
            "\t\t}],\n" +
            "\t\t\"HWSchedule\": [{\n" +
            "\t\t\t\"Name\": \"Smart night\",\n" +
            "\t\t\t\"begin\": 3,\n" +
            "\t\t\t\"end\": 6,\n" +
            "\t\t\t\"months\": [\n" +
            "\t\t\t\t1,\n" +
            "\t\t\t\t2,\n" +
            "\t\t\t\t3,\n" +
            "\t\t\t\t4,\n" +
            "\t\t\t\t5,\n" +
            "\t\t\t\t6,\n" +
            "\t\t\t\t7,\n" +
            "\t\t\t\t8,\n" +
            "\t\t\t\t9,\n" +
            "\t\t\t\t10,\n" +
            "\t\t\t\t11,\n" +
            "\t\t\t\t12\n" +
            "\t\t\t],\n" +
            "\t\t\t\"days\": [\n" +
            "\t\t\t\t0,\n" +
            "\t\t\t\t1,\n" +
            "\t\t\t\t2,\n" +
            "\t\t\t\t3,\n" +
            "\t\t\t\t4,\n" +
            "\t\t\t\t5,\n" +
            "\t\t\t\t6\n" +
            "\t\t\t]\n" +
            "\t\t}],\n" +
            "\t\t\"HWDivert\": {\n" +
            "\t\t\t\"active\": true\n" +
            "\t\t},\n" +
            "\t\t\"EVDivert\":{\n" +
            "\t\t\t\"Name\": \"Afternoon nap\",\n" +
            "\t\t\t\"active\": true,\n" +
            "\t\t\t\"ev1st\": true,\n" +
            "\t\t\t\"begin\": 11,\n" +
            "\t\t\t\"end\": 16,\n" +
            "\t\t\t\"dailyMax\": 16.0,\n" +
            "\t\t\t\"months\": [\n" +
            "\t\t\t\t7\n" +
            "\t\t\t],\n" +
            "\t\t\t\"days\": [\n" +
            "\t\t\t\t0,\n" +
            "\t\t\t\t1,\n" +
            "\t\t\t\t2,\n" +
            "\t\t\t\t3,\n" +
            "\t\t\t\t4,\n" +
            "\t\t\t\t5,\n" +
            "\t\t\t\t6\n" +
            "\t\t\t]\n" +
            "\t\t}\n" +
            "\t}\n" +
            "]";
}
