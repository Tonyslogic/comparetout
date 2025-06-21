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

import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.ChargeModel;
import com.tfcode.comparetout.model.scenario.DischargeToGrid;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.SimulationInputData;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for edge cases, constants, and boundary conditions in SimulationWorker.
 */
public class SimulationWorkerEdgeCaseTest {

    private SimulationInputData createSID(double load, double tpv) {
        SimulationInputData sid = new SimulationInputData();
        sid.setDate("2001-01-01");
        sid.setMinute("12:00");
        sid.setLoad(load);
        sid.setMod(720);
        sid.setDow(1);
        sid.setDo2001(1);
        sid.setTpv(tpv);
        return sid;
    }

    // ====== Tests for M_NULL_BATTERY constant ======

    /**
     * Tests processOneRow behavior with null battery configuration.
     * Verifies system operates correctly without battery storage components.
     * 
     * SIMULATION ASSUMPTION: Framework requires at least 2 input rows for proper execution.
     * Test operates on second row (index 1) to avoid first-row initialization artifacts where SOC gets overwritten to discharge stop value.
     */
    @Test
    public void testNullBatteryHandlingInProcessOneRow() {
        // Test that null battery is properly replaced with M_NULL_BATTERY
        long scenarioID = 1;
        ArrayList<com.tfcode.comparetout.model.scenario.ScenarioSimulationData> outputRows = new ArrayList<>();
        Map<Inverter, SimulationWorker.InputData> inputDataMap = new HashMap<>();

        Inverter inverter = new Inverter();
        List<SimulationInputData> simulationInputData = new ArrayList<>();
        simulationInputData.add(createSID(2.0, 1.0));
        simulationInputData.add(createSID(2.5, 1.2)); // Second row as required by framework

        // Create InputData with null battery
        SimulationWorker.InputData iData = new SimulationWorker.InputData(
                inverter, simulationInputData, null, null, null, null, null, null, null, null, 0.0);
        
        inputDataMap.put(inverter, iData);

        // First process row 0 to populate outputRows for baseline state
        SimulationWorker.processOneRow(scenarioID, outputRows, 0, inputDataMap);
        SimulationWorker.processOneRow(scenarioID, outputRows, 1, inputDataMap);

        assertEquals("Should have 2 output rows", 2, outputRows.size());
        
        com.tfcode.comparetout.model.scenario.ScenarioSimulationData row = outputRows.get(1); // Get row 1 result
        
        // With null battery, should behave like no battery system
        assertEquals("SOC should be 0", 0.0, row.getSOC(), 0.001);
        assertEquals("No battery discharge", 0.0, row.getBatToLoad(), 0.001);
        assertEquals("No PV to charge", 0.0, row.getPvToCharge(), 0.001);
        assertEquals("Should buy shortage from grid", 1.36, row.getBuy(), 0.01);
    }

    /**
     * Tests InputData initialization with null battery constants.
     * Verifies proper default value handling when battery configuration is missing.
     */
    @Test
    public void testNullBatteryConstants() {
        // Test the properties of M_NULL_BATTERY by using it indirectly
        Inverter inverter = new Inverter();
        List<SimulationInputData> inputData = new ArrayList<>();
        
        SimulationWorker.InputData iData = new SimulationWorker.InputData(
                inverter, inputData, null, null, null, null, null, null, null, null, 0.0);
        
        // When battery is null, M_NULL_BATTERY should be used
        // We can test this indirectly through methods that would use the battery
        assertEquals("Null battery should have 0 SOC", 0.0, iData.soc, 0.001);
        
        // getDischargeCapacity should return 0 for null battery
        assertEquals("Null battery discharge capacity should be 0", 0.0, iData.getDischargeCapacity(0), 0.001);
        
        // getChargeCapacity should return 0 for null battery  
        assertEquals("Null battery charge capacity should be 0", 0.0, iData.getChargeCapacity(), 0.001);
    }

    // ====== Tests for extreme values ======

    /**
     * Tests system behavior with extremely high PV generation values.
     * Verifies numerical stability and proper handling of unrealistic PV inputs.
     * 
     * SIMULATION ASSUMPTION: Framework requires at least 2 input rows for proper execution.
     * Test operates on second row (index 1) to avoid first-row initialization artifacts where SOC gets overwritten to discharge stop value.
     */
    @Test
    public void testExtremelyHighPVValues() {
        long scenarioID = 1;
        ArrayList<com.tfcode.comparetout.model.scenario.ScenarioSimulationData> outputRows = new ArrayList<>();
        Map<Inverter, SimulationWorker.InputData> inputDataMap = new HashMap<>();

        Inverter inverter = new Inverter();
        Battery battery = new Battery();
        battery.setBatterySize(10.0);
        battery.setMaxCharge(2.0);
        battery.setDischargeStop(100.0);
        
        ChargeModel chargeModel = new ChargeModel();
        chargeModel.percent0 = 100;
        chargeModel.percent12 = 100;
        chargeModel.percent90 = 50;
        chargeModel.percent100 = 0;
        battery.setChargeModel(chargeModel);

        List<SimulationInputData> simulationInputData = new ArrayList<>();
        simulationInputData.add(createSID(1.0, 1000.0)); // Extremely high PV
        simulationInputData.add(createSID(1.2, 950.0)); // Second row as required by framework

        SimulationWorker.InputData iData = new SimulationWorker.InputData(
                inverter, simulationInputData, battery, null, null, null, null, null, null, null, 0.0);
        inputDataMap.put(inverter, iData);

        // First process row 0 to populate outputRows for baseline state
        SimulationWorker.processOneRow(scenarioID, outputRows, 0, inputDataMap);
        SimulationWorker.processOneRow(scenarioID, outputRows, 1, inputDataMap);

        assertEquals("Should have 2 output rows", 2, outputRows.size());
        
        com.tfcode.comparetout.model.scenario.ScenarioSimulationData row = outputRows.get(1); // Get row 1 result
        
        // Should handle extreme values gracefully
        assertEquals("PV should be recorded as input", 950.0, row.getPv(), 0.001);
        assertTrue("Should charge battery", row.getPvToCharge() > 0);
        assertTrue("Should feed large amount to grid", row.getFeed() > 890);
        assertEquals("Should not buy from grid", 0.0, row.getBuy(), 0.001);
    }

    /**
     * Tests system behavior with extremely high load demand values.
     * Verifies numerical stability and proper handling of unrealistic load inputs.
     * 
     * SIMULATION ASSUMPTION: Framework requires at least 2 input rows for proper execution.
     * Test operates on second row (index 1) to avoid first-row initialization artifacts where SOC gets overwritten to discharge stop value.
     */
    @Test
    public void testExtremelyHighLoadValues() {
        long scenarioID = 1;
        ArrayList<com.tfcode.comparetout.model.scenario.ScenarioSimulationData> outputRows = new ArrayList<>();
        Map<Inverter, SimulationWorker.InputData> inputDataMap = new HashMap<>();

        Inverter inverter = new Inverter();
        Battery battery = new Battery();
        battery.setBatterySize(10.0);
        battery.setMaxDischarge(3.0);
        battery.setDischargeStop(0.0);
        battery.setStorageLoss(0.0);

        List<SimulationInputData> simulationInputData = new ArrayList<>();
        simulationInputData.add(createSID(1000.0, 5.0)); // Extremely high load
        simulationInputData.add(createSID(950.0, 5.2)); // Second row as required by framework

        SimulationWorker.InputData iData = new SimulationWorker.InputData(
                inverter, simulationInputData, battery, null, null, null, null, null, null, null, 0.0);
        iData.soc = 10.0; // Full battery
        inputDataMap.put(inverter, iData);

        // First process row 0 to populate outputRows for baseline state
        SimulationWorker.processOneRow(scenarioID, outputRows, 0, inputDataMap);
        SimulationWorker.processOneRow(scenarioID, outputRows, 1, inputDataMap);

        assertEquals("Should have 2 output rows", 2, outputRows.size());
        
        com.tfcode.comparetout.model.scenario.ScenarioSimulationData row = outputRows.get(1); // Get row 1 result
        
        // Should handle extreme load gracefully
        assertEquals("Load should be recorded as input", 950.0, row.getLoad(), 0.001);
        assertTrue("Should buy massive amount from grid", row.getBuy() > 940);
        assertTrue("Should discharge battery", row.getBatToLoad() > 0);
        assertEquals("Should not feed to grid", 0.0, row.getFeed(), 0.001);
    }

    /**
     * Tests system behavior with very small PV and load values.
     * Verifies precision handling and proper calculation of minimal energy flows.
     * 
     * SIMULATION ASSUMPTION: Framework requires at least 2 input rows for proper execution.
     * Test operates on second row (index 1) to avoid first-row initialization artifacts where SOC gets overwritten to discharge stop value.
     */
    @Test
    public void testVerySmallValues() {
        long scenarioID = 1;
        ArrayList<com.tfcode.comparetout.model.scenario.ScenarioSimulationData> outputRows = new ArrayList<>();
        Map<Inverter, SimulationWorker.InputData> inputDataMap = new HashMap<>();

        Inverter inverter = new Inverter();
        Battery battery = new Battery();
        battery.setBatterySize(10.0);
        battery.setMaxCharge(2.0);
        battery.setMaxDischarge(2.0);
        battery.setDischargeStop(20.0);
        
        ChargeModel chargeModel = new ChargeModel();
        chargeModel.percent0 = 100;
        chargeModel.percent12 = 100;
        chargeModel.percent90 = 50;
        chargeModel.percent100 = 0;
        battery.setChargeModel(chargeModel);

        List<SimulationInputData> simulationInputData = new ArrayList<>();
        simulationInputData.add(createSID(0.001, 0.002)); // Very small values
        simulationInputData.add(createSID(0.0015, 0.0025)); // Second row as required by framework

        SimulationWorker.InputData iData = new SimulationWorker.InputData(
                inverter, simulationInputData, battery, null, null, null, null, null, null, null, 0.0);
        iData.soc = 5.0; // 50% SOC
        inputDataMap.put(inverter, iData);

        // First process row 0 to populate outputRows for baseline state
        SimulationWorker.processOneRow(scenarioID, outputRows, 0, inputDataMap);
        SimulationWorker.processOneRow(scenarioID, outputRows, 1, inputDataMap);

        assertEquals("Should have 2 output rows", 2, outputRows.size());
        
        com.tfcode.comparetout.model.scenario.ScenarioSimulationData row = outputRows.get(1); // Get row 1 result
        
        // Should handle very small values
        assertEquals("Load should be recorded precisely", 0.0015, row.getLoad(), 0.0001);
        assertEquals("PV should be recorded precisely", 0.0025, row.getPv(), 0.0001);
        assertTrue("Should not buy much or any from grid", row.getBuy() <= 0.001);
    }

    // ====== Tests for battery SOC boundary conditions ======

    /**
     * Tests battery behavior when at exactly 100% state of charge.
     * Verifies that fully charged battery cannot accept additional energy.
     */
    @Test
    public void testBatteryAt100PercentSOC() {
        Inverter inverter = new Inverter();
        Battery battery = new Battery();
        battery.setBatterySize(10.0);
        battery.setMaxCharge(2.0);
        
        ChargeModel chargeModel = new ChargeModel();
        chargeModel.percent0 = 100;
        chargeModel.percent12 = 100;
        chargeModel.percent90 = 50;
        chargeModel.percent100 = 0; // No charging at 100%
        battery.setChargeModel(chargeModel);

        List<SimulationInputData> inputData = new ArrayList<>();
        SimulationWorker.InputData iData = new SimulationWorker.InputData(
                inverter, inputData, battery, null, null, null, null, null, null, null, 0.0);
        
        iData.soc = 10.0; // 100% SOC
        
        assertEquals("Charge capacity should be 0 at 100%", 0.0, iData.getChargeCapacity(), 0.001);
        
        // Test getMaxChargeForSOC directly
        double maxCharge = SimulationWorker.InputData.getMaxChargeForSOC(10.0, battery);
        assertEquals("Max charge should be 0 at 100% SOC", 0.0, maxCharge, 0.001);
    }

    /**
     * Tests battery behavior at exact boundary conditions.
     * Verifies proper handling at discharge thresholds and capacity limits.
     */
    @Test
    public void testBatteryAtExactBoundaries() {
        Battery battery = new Battery();
        battery.setBatterySize(100.0); // Larger battery for easier percentage calculations
        battery.setMaxCharge(10.0);
        
        ChargeModel chargeModel = new ChargeModel();
        chargeModel.percent0 = 100;
        chargeModel.percent12 = 80;
        chargeModel.percent90 = 30;
        chargeModel.percent100 = 0;
        battery.setChargeModel(chargeModel);

        // Test exact 12% boundary (12.0 kWh)
        double maxCharge = SimulationWorker.InputData.getMaxChargeForSOC(12.0, battery);
        assertEquals("At exactly 12%, should use percent12 rate", 8.0, maxCharge, 0.001);

        // Test exact 90% boundary (90.0 kWh)
        maxCharge = SimulationWorker.InputData.getMaxChargeForSOC(90.0, battery);
        assertEquals("At exactly 90%, should still use percent12 rate", 8.0, maxCharge, 0.001);

        // Test just above 90% (90.1 kWh)
        maxCharge = SimulationWorker.InputData.getMaxChargeForSOC(90.1, battery);
        assertEquals("Just above 90%, should use percent90 rate", 3.0, maxCharge, 0.001);
    }

    // ====== Tests for load shift and discharge scheduling edge cases ======

    /**
     * Tests ChargeFromGrid with zero rows to process.
     * Verifies proper handling of empty simulation datasets.
     */
    @Test
    public void testChargeFromGridWithZeroRowsToProcess() {
        List<LoadShift> loadShifts = new ArrayList<>();
        LoadShift loadShift = new LoadShift();
        loadShifts.add(loadShift);

        SimulationWorker.ChargeFromGrid cfg = new SimulationWorker.ChargeFromGrid(loadShifts, 0);

        assertNotNull(cfg.mCFG);
        assertNotNull(cfg.mStopAt);
        assertEquals("Should handle zero rows", 0, cfg.mCFG.size());
        assertEquals("Should handle zero rows", 0, cfg.mStopAt.size());
    }

    /**
     * Tests ForceDischargeToGrid with zero rows configuration.
     * Verifies proper handling of empty forced discharge schedules.
     */
    @Test
    public void testForceDischargeToGridWithZeroRows() {
        List<DischargeToGrid> discharges = new ArrayList<>();
        DischargeToGrid discharge = new DischargeToGrid();
        discharges.add(discharge);

        SimulationWorker.ForceDischargeToGrid fdtg = new SimulationWorker.ForceDischargeToGrid(discharges, 0);

        assertNotNull(fdtg.mD2G);
        assertNotNull(fdtg.mStopAt);
        assertNotNull(fdtg.mRate);
        assertEquals("Should handle zero rows", 0, fdtg.mD2G.size());
        assertEquals("Should handle zero rows", 0, fdtg.mStopAt.size());
        assertEquals("Should handle zero rows", 0, fdtg.mRate.size());
    }

    // ====== Tests for inverter loss edge cases ======

    /**
     * Tests inverter behavior with 100% efficiency loss configuration.
     * Verifies that complete loss results in zero energy conversion.
     */
    @Test
    public void testInverterWith100PercentLoss() {
        Inverter inverter = new Inverter();
        inverter.setDc2acLoss(100); // 100% loss
        inverter.setAc2dcLoss(100);
        inverter.setDc2dcLoss(100);

        List<SimulationInputData> inputData = new ArrayList<>();
        SimulationWorker.InputData iData = new SimulationWorker.InputData(
                inverter, inputData, null, null, null, null, null, null, null, null, 0.0);

        // All loss factors should be 0 (100% loss)
        assertEquals("DC to AC loss should be 0", 0.0, iData.dc2acLoss, 0.001);
        assertEquals("AC to DC loss should be 0", 0.0, iData.ac2dcLoss, 0.001);
        assertEquals("DC to DC loss should be 0", 0.0, iData.dc2dcLoss, 0.001);
    }

    /**
     * Tests inverter behavior with zero efficiency loss configuration.
     * Verifies perfect energy conversion with no losses applied.
     */
    @Test
    public void testInverterWithZeroLoss() {
        Inverter inverter = new Inverter();
        inverter.setDc2acLoss(0); // No loss
        inverter.setAc2dcLoss(0);
        inverter.setDc2dcLoss(0);

        List<SimulationInputData> inputData = new ArrayList<>();
        SimulationWorker.InputData iData = new SimulationWorker.InputData(
                inverter, inputData, null, null, null, null, null, null, null, null, 0.0);

        // All loss factors should be 1 (no loss)
        assertEquals("DC to AC loss should be 1", 1.0, iData.dc2acLoss, 0.001);
        assertEquals("AC to DC loss should be 1", 1.0, iData.ac2dcLoss, 0.001);
        assertEquals("DC to DC loss should be 1", 1.0, iData.dc2dcLoss, 0.001);
    }

    // ====== Tests for discharge stop edge cases ======

    /**
     * Tests battery with zero discharge stop threshold.
     * Verifies battery can discharge completely to 0% SOC when configured.
     */
    @Test
    public void testBatteryWithZeroDischargeStop() {
        Inverter inverter = new Inverter();
        Battery battery = new Battery();
        battery.setBatterySize(10.0);
        battery.setDischargeStop(0.0); // Allow complete discharge
        battery.setMaxDischarge(5.0);

        List<SimulationInputData> inputData = new ArrayList<>();
        SimulationWorker.InputData iData = new SimulationWorker.InputData(
                inverter, inputData, battery, null, null, null, null, null, null, null, 0.0);

        iData.soc = 1.0; // Low SOC
        
        double dischargeStop = iData.getDischargeStop();
        assertEquals("Discharge stop should be 0", 0.0, dischargeStop, 0.001);
        
        double dischargeCapacity = iData.getDischargeCapacity(0);
        assertEquals("Should be able to discharge full SOC", 1.0, dischargeCapacity, 0.001);
    }

    /**
     * Tests battery with 100% discharge stop threshold.
     * Verifies battery cannot discharge when threshold is set to maximum.
     */
    @Test
    public void testBatteryWith100PercentDischargeStop() {
        Inverter inverter = new Inverter();
        Battery battery = new Battery();
        battery.setBatterySize(10.0);
        battery.setDischargeStop(100.0); // No discharge allowed
        battery.setMaxDischarge(5.0);

        List<SimulationInputData> inputData = new ArrayList<>();
        SimulationWorker.InputData iData = new SimulationWorker.InputData(
                inverter, inputData, battery, null, null, null, null, null, null, null, 0.0);

        iData.soc = 10.0; // Full SOC
        
        double dischargeStop = iData.getDischargeStop();
        assertEquals("Discharge stop should be full battery", 10.0, dischargeStop, 0.001);
        
        double dischargeCapacity = iData.getDischargeCapacity(0);
        assertEquals("Should not be able to discharge", 0.0, dischargeCapacity, 0.001);
    }
}