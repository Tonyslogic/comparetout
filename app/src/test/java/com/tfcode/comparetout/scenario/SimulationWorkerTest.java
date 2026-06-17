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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static java.lang.Double.max;
import static java.lang.Double.min;

import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.ChargeModel;
import com.tfcode.comparetout.model.scenario.DischargeToGrid;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;
import com.tfcode.comparetout.model.scenario.SimulationInputData;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimulationWorkerTest {

    // Phase 3c: scenario-level inputs (no hot water/EV) for white-box processOneRow tests.
    private static final ScenarioInputs NO_EXTRAS = new ScenarioInputs(null, null, null, null, null, 0d);





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

        SimulationEngine.InputData iData = new SimulationEngine.InputData(inverter, inputData, battery, null, null);

        assertEquals(1, iData.id);
        assertEquals(0.95, iData.dc2acLoss, 0.001); // (100-5)/100
        assertEquals(0.97, iData.ac2dcLoss, 0.001); // (100-3)/100
        assertEquals(0.98, iData.dc2dcLoss, 0.001); // (100-2)/100
        assertEquals(1.5, iData.storageLoss, 0.001);
        assertEquals(inputData, iData.simulationInputData);
        assertEquals(battery, iData.mBattery);
    }

    /**
     * Tests InputData constructor behavior when battery is null.
     * Verifies proper handling of missing battery configuration.
     */
    @Test
    public void testInputDataConstructorWithNullBattery() {
        Inverter inverter = new Inverter();
        List<SimulationInputData> inputData = new ArrayList<>();

        SimulationEngine.InputData iData = new SimulationEngine.InputData(inverter, inputData, null, null, null);

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
        SimulationEngine.InputData iData = new SimulationEngine.InputData(inverter, inputData, battery, null, null);

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
        SimulationEngine.InputData iData = new SimulationEngine.InputData(inverter, inputData, battery, null, null);

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
        SimulationEngine.InputData iData = new SimulationEngine.InputData(inverter, inputData, battery, null, null);

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
        SimulationEngine.ChargeFromGrid cfg = new SimulationEngine.ChargeFromGrid(loadShifts, 105120);

        List<SimulationInputData> inputData = new ArrayList<>();
        SimulationEngine.InputData iData = new SimulationEngine.InputData(inverter, inputData, battery, cfg, null);

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
        SimulationEngine.InputData iData = new SimulationEngine.InputData(inverter, inputData, null, null, null);
        assertFalse(iData.isCFG(0));

        // Test with ChargeFromGrid
        List<LoadShift> loadShifts = new ArrayList<>();
        LoadShift loadShift = new LoadShift();
        loadShift.setBegin(0);
        loadShift.setEnd(24);
        loadShifts.add(loadShift);
        SimulationEngine.ChargeFromGrid cfg = new SimulationEngine.ChargeFromGrid(loadShifts, 105120);

        iData = new SimulationEngine.InputData(inverter, inputData, null, cfg, null);
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
        double result = SimulationEngine.InputData.getMaxChargeForSOC(0.5, battery);
        assertEquals(2.0, result, 0.001); // 100% of 2.0

        // Test at 50% SOC (5.0 kWh)
        result = SimulationEngine.InputData.getMaxChargeForSOC(5.0, battery);
        assertEquals(1.6, result, 0.001); // 80% of 2.0

        // Test at 95% SOC (9.5 kWh)
        result = SimulationEngine.InputData.getMaxChargeForSOC(9.5, battery);
        assertEquals(1.0, result, 0.001); // 50% of 2.0

        // Test at 100% SOC (10.0 kWh)
        result = SimulationEngine.InputData.getMaxChargeForSOC(10.0, battery);
        assertEquals(0.0, result, 0.001); // 0% of 2.0

        // Test with null battery
        result = SimulationEngine.InputData.getMaxChargeForSOC(5.0, null);
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
        SimulationEngine.ChargeFromGrid cfg = new SimulationEngine.ChargeFromGrid(loadShifts, rowsToProcess);

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
        SimulationEngine.ChargeFromGrid cfg = new SimulationEngine.ChargeFromGrid(loadShifts, rowsToProcess);

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
        SimulationEngine.ForceDischargeToGrid fdtg = new SimulationEngine.ForceDischargeToGrid(discharges, rowsToProcess);

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
        SimulationEngine.ForceDischargeToGrid fdtg = new SimulationEngine.ForceDischargeToGrid(discharges, rowsToProcess);

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
     * Tests simulation scenario where load demand exceeds PV generation.
     * Verifies proper grid purchase calculation and battery discharge behavior.
     * <p>
     * SIMULATION ASSUMPTION: Test operates on second row (index 1) to avoid first-row
     * initialization artifacts where SOC gets overwritten to discharge stop value.
     */
    @Test
    public void processOneRow_LoadExceedsPV() {
        long scenarioID = 1;
        ArrayList<ScenarioSimulationData> outputRows = new ArrayList<>();
        Map<Inverter, SimulationEngine.InputData> inputDataMap = new HashMap<>();

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

        SimulationEngine.InputData idata = new SimulationEngine.InputData(inverter, simulationInputData, battery, null, null);
        inputDataMap.put(inverter, idata);

        // First process row 0 to populate outputRows for baseline state
        SimulationEngine.processOneRow(scenarioID, NO_EXTRAS, outputRows, 0, inputDataMap);
        // Intermediate assertion: Check that row 0 result exists
        assertEquals(1, outputRows.size());
        
        int row = 1;
        SimulationEngine.processOneRow(scenarioID, NO_EXTRAS, outputRows, row, inputDataMap);
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
     * <p>
     * SIMULATION ASSUMPTION: Test operates on second row (index 1) to avoid first-row
     * initialization artifacts where SOC gets overwritten to discharge stop value.
     */
    @Test
    public void processOneRow_MinExcessTest() {
        long scenarioID = 1;
        ArrayList<ScenarioSimulationData> outputRows = new ArrayList<>();
        Map<Inverter, SimulationEngine.InputData> inputDataMap = new HashMap<>();

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

        SimulationEngine.InputData idata = new SimulationEngine.InputData(inverter, simulationInputData, battery, null, null);
        inputDataMap.put(inverter, idata);

        // First process row 0 to populate outputRows for baseline state
        SimulationEngine.processOneRow(scenarioID, NO_EXTRAS, outputRows, 0, inputDataMap);
        // Intermediate assertion: Check that row 0 result exists
        assertEquals(1, outputRows.size());
        
        int row = 1;
        SimulationEngine.processOneRow(scenarioID, NO_EXTRAS, outputRows, row, inputDataMap);
        // Intermediate assertion: Check that row 1 result exists
        assertEquals(2, outputRows.size());
        ScenarioSimulationData aRow = outputRows.get(1); // Get row 1 output

        // With small excess below min excess, should not charge battery
        assertEquals(0, aRow.getPvToCharge(), 0.001);
        assertEquals(load + 0.1, aRow.getLoad(), 0.001); // Load from sid2 (load + 0.1)
        assertEquals(tpv + 0.1, aRow.getPv(), 0.001); // PV from sid2 (tpv + 0.1)
    }
}
