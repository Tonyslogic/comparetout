package com.tfcode.comparetout.scenario;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Type;
import java.util.List;

public class ScenarioJsonFileTest {

    private List<ScenarioJsonFile> scenarioList;

    @Before
    public void setUp() {
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
        assertEquals(2, scenarioList.get(0).panels.get(1).mppt.mppt.longValue());
        assertEquals("AlphaESS", scenarioList.get(0).panels.get(0).mppt.inverter);
        assertEquals(53.490, scenarioList.get(0).panels.get(0).latitude, 0);
        assertEquals(0.3, scenarioList.get(0).loadProfile.hourlyBaseLoad, 0);
        assertEquals(13.658400042963337,
                scenarioList.get(0).loadProfile.dayOfWeekDistribution.fri, 0);
        assertTrue(scenarioList.get(0).loadShifts.get(0).days.contains(0));
        assertEquals(6, scenarioList.get(0).loadShifts.get(0).days.size());
        assertTrue(scenarioList.get(0).loadShifts.get(0).months.contains(1));
        assertEquals(12, scenarioList.get(0).loadShifts.get(0).months.size());
        assertEquals(7.5, scenarioList.get(0).evCharges.get(0).draw, 0);
        assertTrue(scenarioList.get(0).hwSchedules.get(0).months.contains(6));
        assertTrue(scenarioList.get(0).hwDivert.active);
        assertTrue(scenarioList.get(0).evDivert.ev1st);
        assertEquals(16.0, scenarioList.get(0).evDivert.dailyMax, 0);

    }


    private void loadTestData() {
        Type type = new TypeToken<List<ScenarioJsonFile>>() {}.getType();
        scenarioList = new Gson().fromJson(testData, type);
    }
    private static final String testData = "[\n" +
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
            "\t\t\t\"Azimith\" : 136,\n" +
            "\t\t\t\"Slope\": 24,\n" +
            "\t\t\t\"Latitude\": \"53.490\",\n" +
            "\t\t\t\"Longitude\": \"-10.015\",\n" +
            "\t\t\t\"MPPT\": {\n" +
            "\t\t\t\t\"Inverter\": \"AlphaESS\",\n" +
            "\t\t\t\t\"MPPT\": \"1\"\n" +
            "\t\t\t}},\n" +
            "\t\t\t{\n" +
            "\t\t\t\"PanelCount\": 7,\n" +
            "\t\t\t\"PanelkWp\" : 325,\n" +
            "\t\t\t\"Azimith\" : 136,\n" +
            "\t\t\t\"Slope\": 24,\n" +
            "\t\t\t\"Latitude\": \"53.490\",\n" +
            "\t\t\t\"Longditude\": \"-10.015\",\n" +
            "\t\t\t\"MPPT\": {\n" +
            "\t\t\t\t\"Inverter\": \"AlphaESS\",\n" +
            "\t\t\t\t\"MPPT\": \"2\"\n" +
            "\t\t\t}\n" +
            "\t\t}],\n" +
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