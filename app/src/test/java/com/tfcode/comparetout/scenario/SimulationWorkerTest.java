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

package com.tfcode.comparetout.scenario;

import static org.junit.Assert.*;

import static java.lang.Double.max;
import static java.lang.Double.min;

import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.ChargeModel;
import com.tfcode.comparetout.model.scenario.DischargeToGrid;
import com.tfcode.comparetout.model.scenario.EVCharge;
import com.tfcode.comparetout.model.scenario.EVDivert;
import com.tfcode.comparetout.model.scenario.HWSchedule;
import com.tfcode.comparetout.model.scenario.HWSystem;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;
import com.tfcode.comparetout.model.scenario.SimulationInputData;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimulationWorkerTest {

    /**
     * Tests basic simulation with one inverter, one battery, and always-active load shift.
     * Verifies PV excess feeds to grid, battery stays full when CFG is enabled, and
     * discharge behavior when discharge threshold is lowered.
     * 
     * SIMULATION ASSUMPTION: Test operates on second row (index 1) to avoid first-row
     * initialization artifacts where SOC gets overwritten to discharge stop value.
     */
    @Test
    public void processOneRow_OneInverter_OneBattery_AlwaysLoadShift() {
        long scenarioID = 1;
        ArrayList<ScenarioSimulationData> outputRows = new ArrayList<>();
        Map<Inverter, SimulationWorker.InputData> inputDataMap = new HashMap<>();

        Inverter inverter = new Inverter();
        Battery battery = new Battery();
        battery.setDischargeStop(100d);
        List<LoadShift> loadShifts = new ArrayList<>();
        LoadShift loadShift = new LoadShift();
        loadShift.setBegin(0);
        loadShift.setEnd(24);
        loadShifts.add(loadShift);
        SimulationWorker.ChargeFromGrid cfg = new SimulationWorker.ChargeFromGrid(loadShifts, 110000);

        List<SimulationInputData> simulationInputData = new ArrayList<>();
        double load = 1.1;
        double tpv = 2.1;
        SimulationInputData sid = createSID(load, tpv);
        simulationInputData.add(sid);
        // Add second row required by framework for row index 1
        SimulationInputData sid2 = createSID(load + 0.1, tpv + 0.1);
        simulationInputData.add(sid2);

        SimulationWorker.InputData idata = new SimulationWorker.InputData(inverter, simulationInputData, battery, cfg, null, null, null, null, null, null, 0);
        // Set battery to full SOC for this test
        idata.soc = battery.getBatterySize();

        inputDataMap.put(inverter, idata);

        // FULL BATTERY, NO DISCHARGE; ROW 1; SOLAR > LOAD; LS=Always
        // First process row 0 to populate outputRows for baseline state
        SimulationWorker.processOneRow(scenarioID, outputRows, 0, inputDataMap);
        // Intermediate assertion: Check that row 0 result exists
        assertEquals(1, outputRows.size());
        assertNotNull("Row 0 output should exist", outputRows.get(0));
        
        int row = 1;
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        // Intermediate assertion: Check that row 1 result exists
        assertEquals(2, outputRows.size());
        ScenarioSimulationData aRow = outputRows.get(1); // Get row 1 output

        assertEquals(0, aRow.getBuy(), 0);
        double dc2acLoss = (100d - inverter.getDc2acLoss()) / 100d;
        double expected = ((tpv + 0.1) * dc2acLoss - (load + 0.1)) - 0 ;
        assertEquals(expected, aRow.getFeed(), 0);
        expected = battery.getBatterySize();
        assertEquals(expected, aRow.getSOC(), 0);
        assertEquals(0, aRow.getPvToCharge(), 0);
        assertEquals(0, aRow.getBatToLoad(), 0);
        assertEquals(load + 0.1, aRow.getLoad(), 0); // Load from sid2 (load + 0.1)
        assertEquals(tpv + 0.1, aRow.getPv(), 0); // PV from sid2 (tpv + 0.1)

        // FULL BATTERY, DISCHARGE; 2ND ROW; SOLAR > LOAD; LS=Always
        row++;
        battery.setDischargeStop(20.0D);
        simulationInputData.add(createSID(load, tpv));
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        // Intermediate assertion: Check that row 2 result exists
        assertEquals(3, outputRows.size());
        aRow = outputRows.get(2); // Get row 2 output

        expected = 0D; // Excess PV
        assertEquals(expected, aRow.getBuy(), 0);
        dc2acLoss = (100d - inverter.getDc2acLoss())/100d;
        expected = ((tpv * dc2acLoss - load)) ; // effective PV - load (row 2 values)
        assertEquals(expected, aRow.getFeed(), 0);
        expected = battery.getBatterySize(); // Full battery (CFG enabled)
        assertEquals(expected, aRow.getSOC(), 0);
        assertEquals(0, aRow.getPvToCharge(), 0); // No charge CFG enabled
        expected = 0D;
        assertEquals(expected, aRow.getBatToLoad(), 0);
        assertEquals(load, aRow.getLoad(), 0);
        assertEquals(tpv, aRow.getPv(), 0);

        // 50% BATTERY, 3rd ROW; NO SOLAR; LS=ALWAYS
        row++;
        load = 1.0;
        tpv = 0.0;
        idata.soc = 2.85;
        simulationInputData.add(createSID(load, tpv));
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        // Intermediate assertion: Check that row 3 result exists
        assertEquals(4, outputRows.size());
        aRow = outputRows.get(3); // Get row 3 output

        expected = load + idata.mBattery.getMaxCharge();
        assertEquals(expected, aRow.getBuy(), 0);
        expected = 0D;
        assertEquals(expected, aRow.getFeed(), 0);
        expected = 2.85 + idata.mBattery.getMaxCharge();
        assertEquals(expected, aRow.getSOC(), 0);
        expected = 0D; // No charge CFG enabled
        assertEquals(expected, aRow.getPvToCharge(), 0);
        assertEquals(expected, aRow.getBatToLoad(), 0);
        expected = load;
        assertEquals(expected, aRow.getLoad(), 0);
        assertEquals(tpv, aRow.getPv(), 0);

        // 90% BATTERY (above LS stop); 4th ROW; NO SOLAR; LS=ALWAYS
        row++;
        idata.soc = 5.13;
        simulationInputData.add(createSID(load, tpv));
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        // Intermediate assertion: Check that row 4 result exists
        assertEquals(5, outputRows.size());
        aRow = outputRows.get(4); // Get row 4 output

//        expected = load;
        assertEquals(expected, aRow.getBuy(), 0);
        expected = 0D;
        assertEquals(expected, aRow.getFeed(), 0);
        expected = 5.13; // Passed stopAT, no discharge due to CFG
        assertEquals(expected, aRow.getSOC(), 0);
        expected = 0D; // No charge CFG enabled
        assertEquals(expected, aRow.getPvToCharge(), 0);
        assertEquals(expected, aRow.getBatToLoad(), 0);
        expected = load;
        assertEquals(expected, aRow.getLoad(), 0);
        assertEquals(tpv, aRow.getPv(), 0);
    }

    /**
     * Tests simulation with two inverters sharing one battery.
     * Verifies proper load distribution and battery management across multiple inverters.
     * 
     * SIMULATION ASSUMPTION: Test operates on second row (index 1) to avoid first-row
     * initialization artifacts where SOC gets overwritten to discharge stop value.
     */
    @Test
    public void processOneRow_TwoInvertersOneBattery() {
        long scenarioID = 1;
        ArrayList<ScenarioSimulationData> outputRows = new ArrayList<>();
        Map<Inverter, SimulationWorker.InputData> inputDataMap = new HashMap<>();

        List<SimulationInputData> simulationInputData1 = new ArrayList<>();
        Inverter inverter1 = new Inverter();
        Battery battery1 = new Battery();
        battery1.setDischargeStop(100d);
        SimulationWorker.InputData iData1 = new SimulationWorker.InputData(inverter1, simulationInputData1, battery1, null, null, null, null, null, null, null, 0);
        // Set battery to full SOC for this test  
        iData1.soc = battery1.getBatterySize();
        inputDataMap.put(inverter1, iData1);

        List<SimulationInputData> simulationInputData2 = new ArrayList<>();
        Inverter inverter2 = new Inverter();
        SimulationWorker.InputData iData2 = new SimulationWorker.InputData(inverter2, simulationInputData2, null, null, null, null, null, null, null, null, 0);
        inputDataMap.put(inverter2, iData2);
        double load = 1.1;
        double tpv1 = 1.1;
        double tpv2 = 1.0;
        SimulationInputData sid1 = createSID(load, tpv1);
        simulationInputData1.add(sid1);
        // Add second row required by framework for row index 1
        simulationInputData1.add(createSID(load + 0.1, tpv1 + 0.1));
        
        SimulationInputData sid2 = createSID(0, tpv2);
        simulationInputData2.add(sid2);
        // Add second row required by framework for row index 1
        simulationInputData2.add(createSID(0.1, tpv2 + 0.1));

        // FULL BATTERY, NO DISCHARGE; ROW 1; SOLAR > LOAD
        // First process row 0 to populate outputRows for baseline state
        SimulationWorker.processOneRow(scenarioID, outputRows, 0, inputDataMap);
        // Intermediate assertion: Check that row 0 result exists
        assertEquals(1, outputRows.size());
        
        int row = 1;
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        // Intermediate assertion: Check that row 1 result exists
        assertEquals(2, outputRows.size());
        ScenarioSimulationData aRow = outputRows.get(1); // Get row 1 output

        assertEquals((load + 0.1) + 0.1, aRow.getLoad(), 0); // Total load from row 1: (load + 0.1) + 0.1
        double expected = (tpv1 + 0.1) + (tpv2 + 0.1); // PV from row 1 of both inverters
        assertEquals(expected, aRow.getPv(), 0);
        assertEquals(0, aRow.getBuy(), 0);
        double dc2acLoss = (100d - inverter1.getDc2acLoss()) / 100d;
        expected = (((tpv1 + 0.1) + (tpv2 + 0.1)) * dc2acLoss - ((load + 0.1) + 0.1)) - 0 ; // Row 1 total load
        assertEquals(expected, aRow.getFeed(), 0);
        expected = battery1.getBatterySize();
        assertEquals(expected, aRow.getSOC(), 0);
        assertEquals(0, aRow.getPvToCharge(), 0);
        assertEquals(0, aRow.getBatToLoad(), 0);
    }

    /**
     * Tests simulation with two separate batteries in the system.
     * Verifies independent battery operation and proper charge/discharge distribution.
     * 
     * SIMULATION ASSUMPTION: Test operates on second row (index 1) to avoid first-row
     * initialization artifacts where SOC gets overwritten to discharge stop value.
     */
    @Test
    public void processOneRow_TwoBatteries() {
        long scenarioID = 1;
        ArrayList<ScenarioSimulationData> outputRows = new ArrayList<>();
        Map<Inverter, SimulationWorker.InputData> inputDataMap = new HashMap<>();

        List<SimulationInputData> simulationInputData1 = new ArrayList<>();
        Inverter inverter1 = new Inverter();
        Battery battery1 = new Battery();
        battery1.setDischargeStop(100d);
        SimulationWorker.InputData iData1 = new SimulationWorker.InputData(inverter1, simulationInputData1, battery1, null, null, null, null, null, null, null, 0);
        // Set battery to full SOC for this test  
        iData1.soc = battery1.getBatterySize();
        inputDataMap.put(inverter1, iData1);

        List<SimulationInputData> simulationInputData2 = new ArrayList<>();
        Inverter inverter2 = new Inverter();
        Battery battery2 = new Battery();
        battery2.setDischargeStop(100d);
        SimulationWorker.InputData iData2 = new SimulationWorker.InputData(inverter2, simulationInputData2, battery2, null, null, null, null, null, null, null, 0);
        // Set battery to full SOC for this test  
        iData2.soc = battery2.getBatterySize();
        inputDataMap.put(inverter2, iData2);

        double load = 1.1;
        double tpv1 = 1.1;
        double tpv2 = 1.0;
        SimulationInputData sid1 = createSID(load, tpv1);
        simulationInputData1.add(sid1);
        // Add second row required by framework for row index 1
        SimulationInputData sid1_row2 = createSID(load + 0.1, tpv1 + 0.1);
        simulationInputData1.add(sid1_row2);
        
        SimulationInputData sid2 = createSID(0, tpv2);
        simulationInputData2.add(sid2);
        // Add second row required by framework for row index 1
        SimulationInputData sid2_row2 = createSID(0.1, tpv2 + 0.1);
        simulationInputData2.add(sid2_row2);

        // FULL BATTERY, NO DISCHARGE; ROW 1; SOLAR > LOAD
        // First process row 0 to populate outputRows for baseline state
        SimulationWorker.processOneRow(scenarioID, outputRows, 0, inputDataMap);
        // Intermediate assertion: Check that row 0 result exists
        assertEquals(1, outputRows.size());
        
        int row = 1;
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        // Intermediate assertion: Check that row 1 result exists
        assertEquals(2, outputRows.size());
        ScenarioSimulationData aRow = outputRows.get(1); // Get row 1 output

        assertEquals(0, aRow.getBuy(), 0);
        double dc2acLoss = (100d - inverter1.getDc2acLoss()) / 100d;
        double expected = (((tpv1 + 0.1) + (tpv2 + 0.1)) * dc2acLoss - ((load + 0.1) + 0.1)) - 0 ; // Row 1 values
        assertEquals(expected, aRow.getFeed(), 0);
        expected = battery1.getBatterySize() + battery2.getBatterySize();
        assertEquals(expected, aRow.getSOC(), 0);
        assertEquals(0, aRow.getPvToCharge(), 0);
        assertEquals(0, aRow.getBatToLoad(), 0);
        assertEquals((load + 0.1) + 0.1, aRow.getLoad(), 0); // Combined load from row 1
        expected = (tpv1 + 0.1) + (tpv2 + 0.1); // Combined PV from row 1
        assertEquals(expected, aRow.getPv(), 0);

        // FULL BATTERY, NO DISCHARGE; 2ND ROW; NO SOLAR
        row++;
        tpv1 = 0;
        tpv2 = 0;
        simulationInputData1.add(createSID(load,tpv1));
        simulationInputData2.add(createSID(0, tpv2));

        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        // Intermediate assertion: Check that row 2 result exists
        assertEquals(3, outputRows.size());
        aRow = outputRows.get(2); // Get row 2 output

        assertEquals(load, aRow.getBuy(), 0);
        expected = max(0, (tpv1 + tpv2 - load));
        assertEquals(expected, aRow.getFeed(), 0);
        expected = battery1.getBatterySize() + battery2.getBatterySize();
        assertEquals(expected, aRow.getSOC(), 0);
        assertEquals(0, aRow.getPvToCharge(), 0);
        assertEquals(0, aRow.getBatToLoad(), 0);
        assertEquals(load, aRow.getLoad(), 0);
        expected = tpv1 + tpv2;
        assertEquals(expected, aRow.getPv(), 0);

        // FULL BATTERY, DISCHARGE; 2ND ROW; NO SOLAR
        row++;
        tpv1 = 0;
        tpv2 = 0;
        double dischargeStop = 20.0;
        simulationInputData1.add(createSID(load, tpv1));
        simulationInputData2.add(createSID(0, tpv2));
        battery1.setDischargeStop(dischargeStop);
        battery2.setDischargeStop(dischargeStop);
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        // Intermediate assertion: Check that row 3 result exists
        assertEquals(4, outputRows.size());
        aRow = outputRows.get(3); // Get row 3 output


        assertEquals(load, aRow.getLoad(), 0);
        expected = tpv1 + tpv2;
        assertEquals(expected, aRow.getPv(), 0);
        expected = load - (battery1.getMaxDischarge() + battery2.getMaxDischarge());
        assertEquals(expected, aRow.getBuy(), 0);
        expected = max(0, ((tpv1 + tpv2) - load));
        assertEquals(expected, aRow.getFeed(), 0);
        expected = (battery1.getBatterySize() - (battery1.getMaxCharge() * (1 + battery1.getStorageLoss()/100d))) * 2;
        assertEquals(expected, aRow.getSOC(), 0);
        assertEquals(0, aRow.getPvToCharge(), 0);
        expected = (battery1.getMaxCharge() * (1 + battery1.getStorageLoss()/100d)) * 2;
        assertEquals(expected, aRow.getBatToLoad(), 0);

    }

    /**
     * Tests basic single battery operation with various charge/discharge scenarios.
     * Validates SOC management and energy flow calculations.
     * 
     * SIMULATION ASSUMPTION: Test operates on second row (index 1) to avoid first-row
     * initialization artifacts where SOC gets overwritten to discharge stop value.
     */
    @Test
    public void processOneRow_OneBattery() {
        long scenarioID = 1;
        ArrayList<ScenarioSimulationData> outputRows = new ArrayList<>();
        Map<Inverter, SimulationWorker.InputData> inputDataMap = new HashMap<>();

        Inverter inverter = new Inverter();
        Battery battery = new Battery();
        battery.setDischargeStop(100d);

        List<SimulationInputData> simulationInputData = new ArrayList<>();
        double load = 1.1;
        double tpv = 2.1;
        SimulationInputData sid = createSID(load, tpv);
        simulationInputData.add(sid);
        // Add second row required by framework for row index 1
        SimulationInputData sid2 = createSID(load + 0.1, tpv + 0.1);
        simulationInputData.add(sid2);

        SimulationWorker.InputData idata = new SimulationWorker.InputData(inverter, simulationInputData, battery, null, null, null, null, null, null, null, 0);
        // Set battery to full SOC for this test  
        idata.soc = battery.getBatterySize();

        inputDataMap.put(inverter, idata);

        // FULL BATTERY, NO DISCHARGE; ROW 1; SOLAR > LOAD
        // First process row 0 to populate outputRows for baseline state
        SimulationWorker.processOneRow(scenarioID, outputRows, 0, inputDataMap);
        // Intermediate assertion: Check that row 0 result exists
        assertEquals(1, outputRows.size());
        
        int row = 1;
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        // Intermediate assertion: Check that row 1 result exists
        assertEquals(2, outputRows.size());
        ScenarioSimulationData aRow = outputRows.get(1); // Get row 1 output

        assertEquals(0, aRow.getBuy(), 0);
        double dc2acLoss = (100d - inverter.getDc2acLoss()) / 100d;
        double expected = ((tpv + 0.1) * dc2acLoss - (load + 0.1)) - 0 ;
        assertEquals(expected, aRow.getFeed(), 0);
        expected = battery.getBatterySize();
        assertEquals(expected, aRow.getSOC(), 0);
        assertEquals(0, aRow.getPvToCharge(), 0);
        assertEquals(0, aRow.getBatToLoad(), 0);
        assertEquals(load + 0.1, aRow.getLoad(), 0); // Load from sid2 (load + 0.1)
        assertEquals(tpv + 0.1, aRow.getPv(), 0); // PV from sid2 (tpv + 0.1)

        // FULL BATTERY, NO DISCHARGE; 2ND ROW; NO SOLAR
        row++;
        tpv = 0;
        simulationInputData.add(createSID(load,tpv));
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        // Intermediate assertion: Check that row 2 result exists
        assertEquals(3, outputRows.size());
        aRow = outputRows.get(2); // Get row 2 output

        assertEquals(load, aRow.getBuy(), 0);
        expected = max(0, (tpv - load));
        assertEquals(expected, aRow.getFeed(), 0);
        expected = battery.getBatterySize();
        assertEquals(expected, aRow.getSOC(), 0);
        assertEquals(0, aRow.getPvToCharge(), 0);
        assertEquals(0, aRow.getBatToLoad(), 0);
        assertEquals(load, aRow.getLoad(), 0);
        assertEquals(tpv, aRow.getPv(), 0);

        // FULL BATTERY, DISCHARGE; 2ND ROW; NO SOLAR
        row++;
        tpv = 0;
        double dischargeStop = 20.0;
        simulationInputData.add(createSID(load, tpv));
        battery.setDischargeStop(dischargeStop);
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        // Intermediate assertion: Check that row 3 result exists
        assertEquals(4, outputRows.size());
        aRow = outputRows.get(3); // Get row 3 output

        expected = load - battery.getMaxDischarge();
        assertEquals(expected, aRow.getBuy(), 0);
        expected = max(0, (tpv - load));
        assertEquals(expected, aRow.getFeed(), 0);
        expected = battery.getBatterySize() - (battery.getMaxCharge() * (1 + battery.getStorageLoss()/100d));
        assertEquals(expected, aRow.getSOC(), 0);
        assertEquals(0, aRow.getPvToCharge(), 0);
        expected = battery.getMaxCharge() * (1 + battery.getStorageLoss()/100d);
        assertEquals(expected, aRow.getBatToLoad(), 0);
        assertEquals(load, aRow.getLoad(), 0);
        assertEquals(tpv, aRow.getPv(), 0);

        // 90%+ FULL BATTERY, DISCHARGE; 2ND ROW; NO SOLAR
        row++;
        load = 0;
        tpv = 1;
        simulationInputData.add(createSID(load, tpv));
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        // Intermediate assertion: Check that row 4 result exists
        assertEquals(5, outputRows.size());
        aRow = outputRows.get(4); // Get row 4 output

        double lastSOC =  battery.getBatterySize() - (battery.getMaxCharge() * (1 + battery.getStorageLoss()/100d));

        assertEquals(0, aRow.getBuy(), 0);
        double maxCharge = SimulationWorker.InputData.getMaxChargeForSOC(lastSOC, battery);
        dc2acLoss = (100d - inverter.getDc2acLoss())/100d;
        expected = (tpv * dc2acLoss - maxCharge) ;
        assertEquals(expected, aRow.getFeed(), 0);
        expected = lastSOC + maxCharge * (1d - (inverter.getDc2dcLoss()/100d));
        assertEquals(expected, aRow.getSOC(), 0);
        expected = maxCharge;
        assertEquals(expected, aRow.getPvToCharge(), 0);
        assertEquals(0, aRow.getBatToLoad(), 0);
        assertEquals(load, aRow.getLoad(), 0);
        assertEquals(tpv, aRow.getPv(), 0);

        // NO BATTERY, DISCHARGE; 2ND ROW; NO SOLAR
        row++;
        load = 0;
        inputDataMap.entrySet().iterator().next().getValue().mBattery = null;
        simulationInputData.add(createSID(load, tpv));
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        // Intermediate assertion: Check that row 5 result exists
        assertEquals(6, outputRows.size());
        aRow = outputRows.get(5); // Get row 5 output

        expected = max (0, min(load, load - tpv));
        assertEquals(expected, aRow.getBuy(), 0);
        expected = tpv * (100 - inverter.getDc2acLoss())/100d;
        assertEquals(expected, aRow.getFeed(), 0);
        assertEquals(0, aRow.getSOC(), 0);
        assertEquals(0, aRow.getPvToCharge(), 0);
        assertEquals(0, aRow.getBatToLoad(), 0);
        assertEquals(load, aRow.getLoad(), 0);
        assertEquals(tpv, aRow.getPv(), 0);

    }

    private static SimulationInputData createSID(double load, double tpv) {
        SimulationInputData sid = new SimulationInputData();
        sid.setDate("2001-01-01");
        sid.setMinute("12:00");
        sid.setLoad(load);
        sid.setMod(700);
        sid.setDow(3);
        sid.setDo2001(1);
        sid.setTpv(tpv);

        return sid;
    }

    // ====== Tests for InputData class methods ======

    /**
     * Tests InputData constructor with complete inverter and battery setup.
     * Verifies correct initialization of loss factors and capacity calculations.
     */
    @Test
    public void testInputDataConstructor() {
        Inverter inverter = new Inverter();
        inverter.setInverterIndex(1);
        inverter.setDc2acLoss(5);
        inverter.setAc2dcLoss(3);
        inverter.setDc2dcLoss(2);

        Battery battery = new Battery();
        battery.setStorageLoss(1.5);

        List<SimulationInputData> inputData = new ArrayList<>();
        inputData.add(createSID(1.0, 2.0));

        SimulationWorker.InputData iData = new SimulationWorker.InputData(
                inverter, inputData, battery, null, null, null, null, null, null, null, 5.0);

        assertEquals(1, iData.id);
        assertEquals(0.95, iData.dc2acLoss, 0.001); // (100-5)/100
        assertEquals(0.97, iData.ac2dcLoss, 0.001); // (100-3)/100
        assertEquals(0.98, iData.dc2dcLoss, 0.001); // (100-2)/100
        assertEquals(1.5, iData.storageLoss, 0.001);
        assertEquals(5.0, iData.exportMax, 0.001);
        assertEquals(inputData, iData.simulationInputData);
        assertEquals(battery, iData.mBattery);
        assertNotNull(iData.mEVDivertDailyTotals);
    }

    /**
     * Tests InputData constructor behavior when battery is null.
     * Verifies proper handling of missing battery configuration.
     */
    @Test
    public void testInputDataConstructorWithNullBattery() {
        Inverter inverter = new Inverter();
        List<SimulationInputData> inputData = new ArrayList<>();

        SimulationWorker.InputData iData = new SimulationWorker.InputData(
                inverter, inputData, null, null, null, null, null, null, null, null, 0.0);

        assertEquals(0.0, iData.storageLoss, 0.001);
    }

    /**
     * Tests getDischargeStop method with various SOC levels.
     * Verifies discharge threshold calculation and SOC-based stopping behavior.
     */
    @Test
    public void testGetDischargeStop() {
        Inverter inverter = new Inverter();
        Battery battery = new Battery();
        battery.setDischargeStop(20.0); // 20%
        battery.setBatterySize(10.0); // 10 kWh

        List<SimulationInputData> inputData = new ArrayList<>();
        SimulationWorker.InputData iData = new SimulationWorker.InputData(
                inverter, inputData, battery, null, null, null, null, null, null, null, 0.0);

        double expected = (20.0 / 100.0) * 10.0; // 2.0 kWh
        assertEquals(expected, iData.getDischargeStop(), 0.001);
    }

    /**
     * Tests getChargeCapacity method for different SOC levels and PV availability.
     * Verifies maximum charging capacity calculation based on battery state.
     */
    @Test
    public void testGetChargeCapacity() {
        Inverter inverter = new Inverter();
        Battery battery = new Battery();
        battery.setBatterySize(10.0);
        battery.setMaxCharge(2.0);
        ChargeModel chargeModel = new ChargeModel();
        chargeModel.percent0 = 100; // At 0-12% SOC, 100% of max charge
        chargeModel.percent12 = 100;
        chargeModel.percent90 = 50; // At 90%+ SOC, 50% of max charge
        chargeModel.percent100 = 0; // At 100% SOC, 0% charge
        battery.setChargeModel(chargeModel);

        List<SimulationInputData> inputData = new ArrayList<>();
        SimulationWorker.InputData iData = new SimulationWorker.InputData(
                inverter, inputData, battery, null, null, null, null, null, null, null, 0.0);

        // Test at 50% SOC (5 kWh)
        iData.soc = 5.0;
        double expected = min(10.0 - 5.0, 2.0); // min(remaining capacity, max charge for SOC)
        assertEquals(expected, iData.getChargeCapacity(), 0.001);

        // Test at 95% SOC (9.5 kWh)
        iData.soc = 9.5;
        double maxChargeFor95Percent = (2.0 * 50) / 100.0; // 1.0 kWh
        expected = min(10.0 - 9.5, maxChargeFor95Percent); // min(0.5, 1.0) = 0.5
        assertEquals(expected, iData.getChargeCapacity(), 0.001);
    }

    /**
     * Tests getDischargeCapacity method for various battery SOC states.
     * Verifies discharge capacity calculation considering battery limits and thresholds.
     */
    @Test
    public void testGetDischargeCapacity() {
        Inverter inverter = new Inverter();
        Battery battery = new Battery();
        battery.setBatterySize(10.0);
        battery.setMaxDischarge(3.0);
        battery.setDischargeStop(20.0); // 20%

        List<SimulationInputData> inputData = new ArrayList<>();
        SimulationWorker.InputData iData = new SimulationWorker.InputData(
                inverter, inputData, battery, null, null, null, null, null, null, null, 0.0);

        // Test at 50% SOC (5 kWh), no CFG
        iData.soc = 5.0;
        double dischargeStop = (20.0 / 100.0) * 10.0; // 2.0 kWh
        double expected = min(3.0, max(0, 5.0 - dischargeStop)); // min(3.0, 3.0) = 3.0
        assertEquals(expected, iData.getDischargeCapacity(0), 0.001);

        // Test at 100% SOC (10 kWh), no CFG
        iData.soc = 10.0;
        expected = min(3.0, max(0, 10.0 - dischargeStop)); // min(3.0, 8.0) = 3.0
        assertEquals(expected, iData.getDischargeCapacity(0), 0.001);

        // Test at discharge stop level (2 kWh)
        iData.soc = 2.0;
        expected = min(3.0, max(0, 2.0 - dischargeStop)); // min(3.0, 0) = 0
        assertEquals(expected, iData.getDischargeCapacity(0), 0.001);
    }

    /**
     * Tests getDischargeCapacity method when Charge From Grid (CFG) is active.
     * Verifies that CFG prevents battery discharge during load shifting periods.
     */
    @Test
    public void testGetDischargeCapacityWithCFG() {
        Inverter inverter = new Inverter();
        Battery battery = new Battery();
        battery.setBatterySize(10.0);
        battery.setMaxDischarge(3.0);

        List<LoadShift> loadShifts = new ArrayList<>();
        LoadShift loadShift = new LoadShift();
        loadShift.setBegin(0);
        loadShift.setEnd(24);
        loadShifts.add(loadShift);
        SimulationWorker.ChargeFromGrid cfg = new SimulationWorker.ChargeFromGrid(loadShifts, 105120);

        List<SimulationInputData> inputData = new ArrayList<>();
        SimulationWorker.InputData iData = new SimulationWorker.InputData(
                inverter, inputData, battery, cfg, null, null, null, null, null, null, 0.0);

        // When CFG is active, discharge capacity should be 0
        iData.soc = 5.0;
        assertEquals(0.0, iData.getDischargeCapacity(0), 0.001);
    }

    /**
     * Tests isCFG method to verify Charge From Grid scheduling logic.
     * Validates time-based CFG activation during specified periods.
     */
    @Test
    public void testIsCFG() {
        Inverter inverter = new Inverter();
        List<SimulationInputData> inputData = new ArrayList<>();

        // Test with null ChargeFromGrid
        SimulationWorker.InputData iData = new SimulationWorker.InputData(
                inverter, inputData, null, null, null, null, null, null, null, null, 0.0);
        assertFalse(iData.isCFG(0));

        // Test with ChargeFromGrid
        List<LoadShift> loadShifts = new ArrayList<>();
        LoadShift loadShift = new LoadShift();
        loadShift.setBegin(0);
        loadShift.setEnd(24);
        loadShifts.add(loadShift);
        SimulationWorker.ChargeFromGrid cfg = new SimulationWorker.ChargeFromGrid(loadShifts, 105120);

        iData = new SimulationWorker.InputData(
                inverter, inputData, null, cfg, null, null, null, null, null, null, 0.0);
        assertTrue(iData.isCFG(0)); // Should be true for 24/7 schedule
    }

    /**
     * Tests getMaxChargeForSOC static method for various SOC levels.
     * Verifies maximum charge calculation based on battery state of charge.
     */
    @Test
    public void testGetMaxChargeForSOC() {
        Battery battery = new Battery();
        battery.setBatterySize(10.0);
        battery.setMaxCharge(2.0);
        ChargeModel chargeModel = new ChargeModel();
        chargeModel.percent0 = 100;  // 0-12%: 100% of max charge
        chargeModel.percent12 = 80;  // 12-90%: 80% of max charge
        chargeModel.percent90 = 50;  // 90-100%: 50% of max charge
        chargeModel.percent100 = 0;  // 100%: 0% charge
        battery.setChargeModel(chargeModel);

        // Test at 5% SOC (0.5 kWh)
        double result = SimulationWorker.InputData.getMaxChargeForSOC(0.5, battery);
        assertEquals(1.6, result, 0.001); // 80% of 2.0

        // Test at 50% SOC (5.0 kWh)
        result = SimulationWorker.InputData.getMaxChargeForSOC(5.0, battery);
        assertEquals(1.6, result, 0.001); // 80% of 2.0

        // Test at 95% SOC (9.5 kWh)
        result = SimulationWorker.InputData.getMaxChargeForSOC(9.5, battery);
        assertEquals(1.0, result, 0.001); // 50% of 2.0

        // Test at 100% SOC (10.0 kWh)
        result = SimulationWorker.InputData.getMaxChargeForSOC(10.0, battery);
        assertEquals(0.0, result, 0.001); // 0% of 2.0

        // Test with null battery
        result = SimulationWorker.InputData.getMaxChargeForSOC(5.0, null);
        assertEquals(0.0, result, 0.001);
    }

    // ====== Tests for ChargeFromGrid class ======

    /**
     * Tests ChargeFromGrid constructor with load shift schedules.
     * Verifies proper initialization of CFG periods and row counts.
     */
    @Test
    public void testChargeFromGridConstructor() {
        List<LoadShift> loadShifts = new ArrayList<>();
        LoadShift loadShift = new LoadShift();
        // Create a minimal LoadShift configuration for testing
        loadShift.setBegin(10); // 10 AM
        loadShift.setEnd(14);   // 2 PM
        loadShifts.add(loadShift);

        int rowsToProcess = 105120;
        SimulationWorker.ChargeFromGrid cfg = new SimulationWorker.ChargeFromGrid(loadShifts, rowsToProcess);

        assertNotNull(cfg.mCFG);
        assertNotNull(cfg.mStopAt);
        assertEquals(rowsToProcess, cfg.mCFG.size());
        assertEquals(rowsToProcess, cfg.mStopAt.size());
    }

    /**
     * Tests ChargeFromGrid constructor with empty load shift list.
     * Verifies proper handling of configurations without CFG periods.
     */
    @Test
    public void testChargeFromGridEmptyLoadShifts() {
        List<LoadShift> loadShifts = new ArrayList<>();
        int rowsToProcess = 105120;
        SimulationWorker.ChargeFromGrid cfg = new SimulationWorker.ChargeFromGrid(loadShifts, rowsToProcess);

        assertNotNull(cfg.mCFG);
        assertNotNull(cfg.mStopAt);
        assertEquals(rowsToProcess, cfg.mCFG.size());
        assertEquals(rowsToProcess, cfg.mStopAt.size());

        // All values should be false/0 for empty load shifts
        for (int i = 0; i < rowsToProcess; i++) {
            assertFalse("CFG should be false for index " + i, cfg.mCFG.get(i));
            assertEquals("StopAt should be 0 for index " + i, 0.0, cfg.mStopAt.get(i), 0.001);
        }
    }

    // ====== Tests for ForceDischargeToGrid class ======

    /**
     * Tests ForceDischargeToGrid constructor with discharge schedules.
     * Verifies proper initialization of forced discharge periods.
     */
    @Test
    public void testForceDischargeToGridConstructor() {
        List<DischargeToGrid> discharges = new ArrayList<>();
        DischargeToGrid discharge = new DischargeToGrid();
        // Note: We would need to set up minimal discharge configuration
        // This tests the constructor doesn't crash with empty list
        discharges.add(discharge);

        int rowsToProcess = 105120;
        SimulationWorker.ForceDischargeToGrid fdtg = new SimulationWorker.ForceDischargeToGrid(discharges, rowsToProcess);

        assertNotNull(fdtg.mD2G);
        assertNotNull(fdtg.mStopAt);
        assertNotNull(fdtg.mRate);
        assertEquals(rowsToProcess, fdtg.mD2G.size());
        assertEquals(rowsToProcess, fdtg.mStopAt.size());
        assertEquals(rowsToProcess, fdtg.mRate.size());
    }

    /**
     * Tests ForceDischargeToGrid constructor with empty discharge list.
     * Verifies proper handling of configurations without forced discharge periods.
     */
    @Test
    public void testForceDischargeToGridEmptyDischarges() {
        List<DischargeToGrid> discharges = new ArrayList<>();
        int rowsToProcess = 105120;
        SimulationWorker.ForceDischargeToGrid fdtg = new SimulationWorker.ForceDischargeToGrid(discharges, rowsToProcess);

        assertNotNull(fdtg.mD2G);
        assertNotNull(fdtg.mStopAt);
        assertNotNull(fdtg.mRate);
        assertEquals(rowsToProcess, fdtg.mD2G.size());
        assertEquals(rowsToProcess, fdtg.mStopAt.size());
        assertEquals(rowsToProcess, fdtg.mRate.size());

        // All values should be false/0 for empty discharges
        for (int i = 0; i < rowsToProcess; i++) {
            assertFalse("D2G should be false for index " + i, fdtg.mD2G.get(i));
            assertEquals("StopAt should be 0 for index " + i, 0.0, fdtg.mStopAt.get(i), 0.001);
            assertEquals("Rate should be 0 for index " + i, 0.0, fdtg.mRate.get(i), 0.001);
        }
    }

    // ====== Tests for additional processOneRow scenarios ======

    /**
     * Tests simulation behavior when no battery is present in the system.
     * Verifies direct PV-to-grid feed and load-from-grid scenarios without battery storage.
     * 
     * SIMULATION ASSUMPTION: Test operates on second row (index 1) to avoid first-row
     * initialization artifacts where SOC gets overwritten to discharge stop value.
     */
    @Test
    public void processOneRow_NoBattery() {
        long scenarioID = 1;
        ArrayList<ScenarioSimulationData> outputRows = new ArrayList<>();
        Map<Inverter, SimulationWorker.InputData> inputDataMap = new HashMap<>();

        Inverter inverter = new Inverter();
        List<SimulationInputData> simulationInputData = new ArrayList<>();
        double load = 1.5;
        double tpv = 2.0;
        SimulationInputData sid = createSID(load, tpv);
        simulationInputData.add(sid);
        // Add second row required by framework for row index 1
        SimulationInputData sid2 = createSID(load + 0.1, tpv + 0.1);
        simulationInputData.add(sid2);

        // No battery (null)
        SimulationWorker.InputData idata = new SimulationWorker.InputData(
                inverter, simulationInputData, null, null, null, null, null, null, null, null, 0);
        inputDataMap.put(inverter, idata);

        // First process row 0 to populate outputRows for baseline state
        SimulationWorker.processOneRow(scenarioID, outputRows, 0, inputDataMap);
        // Intermediate assertion: Check that row 0 result exists
        assertEquals(1, outputRows.size());
        
        int row = 1;
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        // Intermediate assertion: Check that row 1 result exists
        assertEquals(2, outputRows.size());
        ScenarioSimulationData aRow = outputRows.get(1); // Get row 1 output

        assertEquals(0, aRow.getBuy(), 0.001);
        double dc2acLoss = (100d - inverter.getDc2acLoss()) / 100d;
        double expected = ((tpv + 0.1) * dc2acLoss) - (load + 0.1);
        assertEquals(expected, aRow.getFeed(), 0.001);
        assertEquals(0, aRow.getSOC(), 0.001);
        assertEquals(0, aRow.getPvToCharge(), 0.001);
        assertEquals(0, aRow.getBatToLoad(), 0.001);
        assertEquals(load + 0.1, aRow.getLoad(), 0.001); // Load from sid2 (load + 0.1)
        assertEquals(tpv + 0.1, aRow.getPv(), 0.001); // PV from sid2 (tpv + 0.1)
    }

    /**
     * Tests simulation scenario where load demand exceeds PV generation.
     * Verifies proper grid purchase calculation and battery discharge behavior.
     * 
     * SIMULATION ASSUMPTION: Test operates on second row (index 1) to avoid first-row
     * initialization artifacts where SOC gets overwritten to discharge stop value.
     */
    @Test
    public void processOneRow_LoadExceedsPV() {
        long scenarioID = 1;
        ArrayList<ScenarioSimulationData> outputRows = new ArrayList<>();
        Map<Inverter, SimulationWorker.InputData> inputDataMap = new HashMap<>();

        Inverter inverter = new Inverter();
        Battery battery = new Battery();
        battery.setDischargeStop(20.0);
        battery.setBatterySize(10.0);
        battery.setMaxDischarge(3.0);

        List<SimulationInputData> simulationInputData = new ArrayList<>();
        double load = 3.0;
        double tpv = 1.0; // PV less than load
        SimulationInputData sid = createSID(load, tpv);
        simulationInputData.add(sid);
        // Add second row required by framework for row index 1
        SimulationInputData sid2 = createSID(load + 0.1, tpv + 0.1);
        simulationInputData.add(sid2);

        SimulationWorker.InputData idata = new SimulationWorker.InputData(
                inverter, simulationInputData, battery, null, null, null, null, null, null, null, 0);
        inputDataMap.put(inverter, idata);

        // First process row 0 to populate outputRows for baseline state
        SimulationWorker.processOneRow(scenarioID, outputRows, 0, inputDataMap);
        // Intermediate assertion: Check that row 0 result exists
        assertEquals(1, outputRows.size());
        
        int row = 1;
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        // Intermediate assertion: Check that row 1 result exists
        assertEquals(2, outputRows.size());
        ScenarioSimulationData aRow = outputRows.get(1); // Get row 1 output

        // Should buy from grid since load exceeds available local supply
        assertTrue("Should buy from grid when load exceeds PV+battery", aRow.getBuy() > 0);
        assertEquals(3.1, aRow.getLoad(), 0.001); // Load from sid2 (load + 0.1)
        assertEquals(1.1, aRow.getPv(), 0.001); // PV from sid2 (tpv + 0.1)
    }

    /**
     * Tests minimum excess threshold functionality in the inverter.
     * Verifies that PV excess below minimum threshold doesn't trigger battery charging.
     * 
     * SIMULATION ASSUMPTION: Test operates on second row (index 1) to avoid first-row
     * initialization artifacts where SOC gets overwritten to discharge stop value.
     */
    @Test
    public void processOneRow_MinExcessTest() {
        long scenarioID = 1;
        ArrayList<ScenarioSimulationData> outputRows = new ArrayList<>();
        Map<Inverter, SimulationWorker.InputData> inputDataMap = new HashMap<>();

        Inverter inverter = new Inverter();
        inverter.setMinExcess(0.5); // Set minimum excess
        Battery battery = new Battery();
        battery.setDischargeStop(100.0);
        battery.setBatterySize(10.0);

        List<SimulationInputData> simulationInputData = new ArrayList<>();
        double load = 1.0;
        double tpv = 1.3; // Small excess, below min excess
        SimulationInputData sid = createSID(load, tpv);
        simulationInputData.add(sid);
        // Add second row required by framework for row index 1
        SimulationInputData sid2 = createSID(load + 0.1, tpv + 0.1);
        simulationInputData.add(sid2);

        SimulationWorker.InputData idata = new SimulationWorker.InputData(
                inverter, simulationInputData, battery, null, null, null, null, null, null, null, 0);
        inputDataMap.put(inverter, idata);

        // First process row 0 to populate outputRows for baseline state
        SimulationWorker.processOneRow(scenarioID, outputRows, 0, inputDataMap);
        // Intermediate assertion: Check that row 0 result exists
        assertEquals(1, outputRows.size());
        
        int row = 1;
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        // Intermediate assertion: Check that row 1 result exists
        assertEquals(2, outputRows.size());
        ScenarioSimulationData aRow = outputRows.get(1); // Get row 1 output

        // With small excess below min excess, should not charge battery
        assertEquals(0, aRow.getPvToCharge(), 0.001);
        assertEquals(load + 0.1, aRow.getLoad(), 0.001); // Load from sid2 (load + 0.1)
        assertEquals(tpv + 0.1, aRow.getPv(), 0.001); // PV from sid2 (tpv + 0.1)
    }
}