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
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.SimulationInputData;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimulationWorkerHelperTest {

    private SimulationInputData createSID(double load, double tpv) {
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

    private SimulationWorker.InputData createInputData(Inverter inverter, Battery battery, double soc) {
        List<SimulationInputData> inputData = new ArrayList<>();
        inputData.add(createSID(1.0, 2.0));
        
        SimulationWorker.InputData iData = new SimulationWorker.InputData(
                inverter, inputData, battery, null, null, null, null, null, null, null, 0.0);
        iData.soc = soc;
        return iData;
    }

    // ====== Tests for static helper methods ======

    /**
     * Tests battery charging logic with a single battery configuration.
     * Verifies proper SOC increase and capacity handling during PV excess periods.
     * 
     * SIMULATION ASSUMPTION: Framework requires at least 2 input rows for proper execution.
     * Test adapted to provide minimum required data rows.
     */
    @Test
    public void testChargeBatteriesWithSingleBattery() {
        // Test the private static method chargeBatteries via processOneRow
        // This tests the charging logic when there's excess PV
        
        Inverter inverter = new Inverter();
        Battery battery = new Battery();
        battery.setBatterySize(10.0);
        battery.setMaxCharge(2.0);
        battery.setDischargeStop(100.0); // Prevent discharge
        
        ChargeModel chargeModel = new ChargeModel();
        chargeModel.percent0 = 100;
        chargeModel.percent12 = 100;
        chargeModel.percent90 = 50;
        chargeModel.percent100 = 0;
        battery.setChargeModel(chargeModel);

        Map<Inverter, SimulationWorker.InputData> inputDataMap = new HashMap<>();
        SimulationWorker.InputData iData = createInputData(inverter, battery, 5.0); // 50% SOC
        inputDataMap.put(inverter, iData);

        // Create a scenario with excess PV
        List<SimulationInputData> simulationInputData = new ArrayList<>();
        simulationInputData.add(createSID(1.0, 5.0)); // 5kW PV, 1kW load = 4kW excess
        simulationInputData.add(createSID(1.2, 4.8)); // Second row as required by framework
        iData.simulationInputData = simulationInputData;

        // Call processOneRow to test charging logic
        ArrayList<com.tfcode.comparetout.model.scenario.ScenarioSimulationData> outputRows = new ArrayList<>();
        SimulationWorker.processOneRow(1L, outputRows, 0, inputDataMap);

        // Verify that charging occurred
        assertTrue("PV to charge should be > 0", outputRows.get(0).getPvToCharge() > 0);
        assertTrue("SOC should increase", outputRows.get(0).getSOC() > 5.0);
    }

    /**
     * Tests battery charging logic with multiple batteries in the system.
     * Verifies proper charge distribution and individual battery SOC management.
     */
    @Test
    public void testChargeBatteriesWithMultipleBatteries() {
        // Test charging distribution across multiple batteries
        
        Inverter inverter1 = new Inverter();
        Battery battery1 = new Battery();
        battery1.setBatterySize(10.0);
        battery1.setMaxCharge(2.0);
        battery1.setDischargeStop(100.0);
        
        Inverter inverter2 = new Inverter();
        Battery battery2 = new Battery();
        battery2.setBatterySize(5.0);
        battery2.setMaxCharge(1.0);
        battery2.setDischargeStop(100.0);

        ChargeModel chargeModel = new ChargeModel();
        chargeModel.percent0 = 100;
        chargeModel.percent12 = 100;
        chargeModel.percent90 = 50;
        chargeModel.percent100 = 0;
        battery1.setChargeModel(chargeModel);
        battery2.setChargeModel(chargeModel);

        Map<Inverter, SimulationWorker.InputData> inputDataMap = new HashMap<>();
        
        // Both batteries at 50% SOC
        SimulationWorker.InputData iData1 = createInputData(inverter1, battery1, 5.0);
        SimulationWorker.InputData iData2 = createInputData(inverter2, battery2, 2.5);
        
        // Create simulation data with excess PV
        List<SimulationInputData> simulationInputData1 = new ArrayList<>();
        simulationInputData1.add(createSID(1.0, 3.0)); // 3kW PV
        iData1.simulationInputData = simulationInputData1;
        
        List<SimulationInputData> simulationInputData2 = new ArrayList<>();
        simulationInputData2.add(createSID(0.0, 2.0)); // 2kW PV
        iData2.simulationInputData = simulationInputData2;
        
        inputDataMap.put(inverter1, iData1);
        inputDataMap.put(inverter2, iData2);

        // Test the charging distribution
        ArrayList<com.tfcode.comparetout.model.scenario.ScenarioSimulationData> outputRows = new ArrayList<>();
        SimulationWorker.processOneRow(1L, outputRows, 0, inputDataMap);

        // Verify that charging occurred and total SOC increased
        assertTrue("SOC should increase", outputRows.get(0).getSOC() > 7.5); // 5.0 + 2.5
        assertTrue("PV to charge should be > 0", outputRows.get(0).getPvToCharge() > 0);
    }

    /**
     * Tests battery discharge logic with a single battery configuration.
     * Verifies proper SOC reduction and discharge threshold behavior during shortage periods.
     * 
     * SIMULATION ASSUMPTION: Framework requires at least 2 input rows for proper execution.
     * Test adapted to provide minimum required data rows.
     */
    @Test
    public void testDischargeBatteriesWithSingleBattery() {
        // Test battery discharge when load exceeds PV
        
        Inverter inverter = new Inverter();
        Battery battery = new Battery();
        battery.setBatterySize(10.0);
        battery.setMaxDischarge(3.0);
        battery.setDischargeStop(20.0); // Can discharge to 20%
        battery.setStorageLoss(5.0);

        Map<Inverter, SimulationWorker.InputData> inputDataMap = new HashMap<>();
        SimulationWorker.InputData iData = createInputData(inverter, battery, 8.0); // 80% SOC
        
        // Create scenario where load exceeds PV
        List<SimulationInputData> simulationInputData = new ArrayList<>();
        simulationInputData.add(createSID(4.0, 1.0)); // 4kW load, 1kW PV = 3kW shortage
        simulationInputData.add(createSID(3.8, 1.1)); // Second row as required by framework
        iData.simulationInputData = simulationInputData;
        
        inputDataMap.put(inverter, iData);

        ArrayList<com.tfcode.comparetout.model.scenario.ScenarioSimulationData> outputRows = new ArrayList<>();
        SimulationWorker.processOneRow(1L, outputRows, 0, inputDataMap);

        // Verify that discharge occurred
        assertTrue("Battery should discharge", outputRows.get(0).getBatToLoad() > 0);
        assertTrue("SOC should decrease", outputRows.get(0).getSOC() < 8.0);
        assertTrue("Should still need to buy some power", outputRows.get(0).getBuy() > 0);
    }

    /**
     * Tests battery discharge logic with multiple batteries in the system.
     * Verifies proper discharge distribution and coordinated battery management.
     */
    @Test
    public void testDischargeBatteriesMultipleBatteries() {
        // Test discharge distribution across multiple batteries
        
        Inverter inverter1 = new Inverter();
        Battery battery1 = new Battery();
        battery1.setBatterySize(10.0);
        battery1.setMaxDischarge(2.0);
        battery1.setDischargeStop(20.0);
        battery1.setStorageLoss(5.0);
        
        Inverter inverter2 = new Inverter();
        Battery battery2 = new Battery();
        battery2.setBatterySize(5.0);
        battery2.setMaxDischarge(1.5);
        battery2.setDischargeStop(10.0);
        battery2.setStorageLoss(3.0);

        Map<Inverter, SimulationWorker.InputData> inputDataMap = new HashMap<>();
        
        // Both batteries at high SOC
        SimulationWorker.InputData iData1 = createInputData(inverter1, battery1, 8.0);
        SimulationWorker.InputData iData2 = createInputData(inverter2, battery2, 4.0);
        
        // Create scenario with insufficient PV
        List<SimulationInputData> simulationInputData1 = new ArrayList<>();
        simulationInputData1.add(createSID(3.0, 0.5)); // 3kW load, 0.5kW PV
        iData1.simulationInputData = simulationInputData1;
        
        List<SimulationInputData> simulationInputData2 = new ArrayList<>();
        simulationInputData2.add(createSID(0.0, 0.0)); // No additional load/PV
        iData2.simulationInputData = simulationInputData2;
        
        inputDataMap.put(inverter1, iData1);
        inputDataMap.put(inverter2, iData2);

        ArrayList<com.tfcode.comparetout.model.scenario.ScenarioSimulationData> outputRows = new ArrayList<>();
        SimulationWorker.processOneRow(1L, outputRows, 0, inputDataMap);

        // Verify that both batteries discharged
        assertTrue("Batteries should discharge", outputRows.get(0).getBatToLoad() > 0);
        assertTrue("Total SOC should decrease", outputRows.get(0).getSOC() < 12.0); // 8.0 + 4.0
    }

    /**
     * Tests Charge From Grid (CFG) functionality when batteries need charging.
     * Verifies grid-to-battery charging during load shifting periods.
     * 
     * SIMULATION ASSUMPTION: Framework requires at least 2 input rows for proper execution.
     * Test adapted to provide minimum required data rows.
     */
    @Test
    public void testChargeBatteriesFromGridIfNeeded() {
        // Test grid charging when load shift is active
        
        Inverter inverter = new Inverter();
        Battery battery = new Battery();
        battery.setBatterySize(10.0);
        battery.setMaxCharge(2.0);
        
        ChargeModel chargeModel = new ChargeModel();
        chargeModel.percent0 = 100;
        chargeModel.percent12 = 100;
        chargeModel.percent90 = 50;
        chargeModel.percent100 = 0;
        battery.setChargeModel(chargeModel);

        // Create load shift schedule
        List<LoadShift> loadShifts = new ArrayList<>();
        LoadShift loadShift = new LoadShift();
        loadShift.setBegin(0);
        loadShift.setEnd(24);
        loadShift.setStopAt(90.0); // Charge to 90%
        loadShifts.add(loadShift);
        
        SimulationWorker.ChargeFromGrid cfg = new SimulationWorker.ChargeFromGrid(loadShifts, 105120);

        Map<Inverter, SimulationWorker.InputData> inputDataMap = new HashMap<>();
        SimulationWorker.InputData iData = createInputData(inverter, battery, 5.0); // 50% SOC
        iData.mChargeFromGrid = cfg;
        
        // Create scenario with regular load
        List<SimulationInputData> simulationInputData = new ArrayList<>();
        simulationInputData.add(createSID(2.0, 1.0)); // 2kW load, 1kW PV
        simulationInputData.add(createSID(2.1, 0.9)); // Second row as required by framework
        iData.simulationInputData = simulationInputData;
        
        inputDataMap.put(inverter, iData);

        ArrayList<com.tfcode.comparetout.model.scenario.ScenarioSimulationData> outputRows = new ArrayList<>();
        SimulationWorker.processOneRow(1L, outputRows, 0, inputDataMap);

        // Verify that grid charging occurred
        assertTrue("Grid to battery should be > 0", outputRows.get(0).getGridToBattery() > 0);
        assertTrue("Buy should include grid charging", outputRows.get(0).getBuy() > 1.0);
    }

    /**
     * Tests behavior when battery reaches discharge stop threshold.
     * Verifies that battery discharge ceases at configured minimum SOC level.
     * 
     * SIMULATION ASSUMPTION: Framework requires at least 2 input rows for proper execution.
     * Test adapted to provide minimum required data rows.
     */
    @Test
    public void testBatteryAtDischargeStop() {
        // Test that battery doesn't discharge below discharge stop
        
        Inverter inverter = new Inverter();
        Battery battery = new Battery();
        battery.setBatterySize(10.0);
        battery.setMaxDischarge(3.0);
        battery.setDischargeStop(20.0); // Stop at 20%
        battery.setStorageLoss(5.0);

        Map<Inverter, SimulationWorker.InputData> inputDataMap = new HashMap<>();
        SimulationWorker.InputData iData = createInputData(inverter, battery, 2.0); // Exactly at discharge stop
        
        // Create scenario with high load
        List<SimulationInputData> simulationInputData = new ArrayList<>();
        simulationInputData.add(createSID(5.0, 0.0)); // 5kW load, no PV
        simulationInputData.add(createSID(4.8, 0.0)); // Second row as required by framework
        iData.simulationInputData = simulationInputData;
        
        inputDataMap.put(inverter, iData);

        ArrayList<com.tfcode.comparetout.model.scenario.ScenarioSimulationData> outputRows = new ArrayList<>();
        SimulationWorker.processOneRow(1L, outputRows, 0, inputDataMap);

        // Verify that battery doesn't discharge further
        assertEquals("Battery should not discharge below stop", 0.0, outputRows.get(0).getBatToLoad(), 0.001);
        assertEquals("SOC should remain at discharge stop", 2.0, outputRows.get(0).getSOC(), 0.001);
        assertEquals("Should buy all power from grid", 5.0, outputRows.get(0).getBuy(), 0.001);
    }

    /**
     * Tests behavior when battery is fully charged (100% SOC).
     * Verifies that battery cannot accept additional charge when at maximum capacity.
     * 
     * SIMULATION ASSUMPTION: Framework requires at least 2 input rows for proper execution.
     * Test adapted to provide minimum required data rows.
     */
    @Test
    public void testBatteryFullyCharged() {
        // Test behavior when battery is fully charged
        
        Inverter inverter = new Inverter();
        Battery battery = new Battery();
        battery.setBatterySize(10.0);
        battery.setMaxCharge(2.0);
        battery.setDischargeStop(100.0); // Prevent discharge
        
        ChargeModel chargeModel = new ChargeModel();
        chargeModel.percent0 = 100;
        chargeModel.percent12 = 100;
        chargeModel.percent90 = 50;
        chargeModel.percent100 = 0; // Can't charge at 100%
        battery.setChargeModel(chargeModel);

        Map<Inverter, SimulationWorker.InputData> inputDataMap = new HashMap<>();
        SimulationWorker.InputData iData = createInputData(inverter, battery, 10.0); // 100% SOC
        
        // Create scenario with excess PV
        List<SimulationInputData> simulationInputData = new ArrayList<>();
        simulationInputData.add(createSID(1.0, 5.0)); // 1kW load, 5kW PV = 4kW excess
        simulationInputData.add(createSID(1.1, 4.9)); // Second row as required by framework
        iData.simulationInputData = simulationInputData;
        
        inputDataMap.put(inverter, iData);

        ArrayList<com.tfcode.comparetout.model.scenario.ScenarioSimulationData> outputRows = new ArrayList<>();
        SimulationWorker.processOneRow(1L, outputRows, 0, inputDataMap);

        // Verify that no charging occurred and excess went to feed
        assertEquals("No PV should go to charging", 0.0, outputRows.get(0).getPvToCharge(), 0.001);
        assertEquals("SOC should remain the same", 10.0, outputRows.get(0).getSOC(), 0.001);
        assertTrue("Excess should feed to grid", outputRows.get(0).getFeed() > 0);
    }

    /**
     * Tests scenario with zero PV generation and high load demand.
     * Verifies system behavior during periods with no solar generation.
     * 
     * SIMULATION ASSUMPTION: Framework requires at least 2 input rows for proper execution.
     * Test adapted to provide minimum required data rows.
     */
    @Test
    public void testZeroPVHighLoad() {
        // Test scenario with no PV and high load
        
        Inverter inverter = new Inverter();
        Battery battery = new Battery();
        battery.setBatterySize(10.0);
        battery.setMaxDischarge(3.0);
        battery.setDischargeStop(20.0);
        battery.setStorageLoss(5.0);

        Map<Inverter, SimulationWorker.InputData> inputDataMap = new HashMap<>();
        SimulationWorker.InputData iData = createInputData(inverter, battery, 8.0); // 80% SOC
        
        // Create scenario with no PV and high load
        List<SimulationInputData> simulationInputData = new ArrayList<>();
        simulationInputData.add(createSID(6.0, 0.0)); // 6kW load, no PV
        simulationInputData.add(createSID(5.8, 0.0)); // Second row as required by framework
        iData.simulationInputData = simulationInputData;
        
        inputDataMap.put(inverter, iData);

        ArrayList<com.tfcode.comparetout.model.scenario.ScenarioSimulationData> outputRows = new ArrayList<>();
        SimulationWorker.processOneRow(1L, outputRows, 0, inputDataMap);

        // Verify that battery discharges and remaining is bought from grid
        assertTrue("Battery should discharge", outputRows.get(0).getBatToLoad() > 0);
        assertTrue("Should buy power from grid", outputRows.get(0).getBuy() > 0);
        assertEquals("No PV", 0.0, outputRows.get(0).getPv(), 0.001);
        assertEquals("No feed to grid", 0.0, outputRows.get(0).getFeed(), 0.001);
        assertEquals("No PV to charge", 0.0, outputRows.get(0).getPvToCharge(), 0.001);
    }

    /**
     * Tests balanced scenario where PV generation exactly matches load demand.
     * Verifies system behavior when there's no excess or shortage of energy.
     * 
     * SIMULATION ASSUMPTION: Framework requires at least 2 input rows for proper execution.
     * Test adapted to provide minimum required data rows.
     */
    @Test
    public void testPVExactlyMatchesLoad() {
        // Test scenario where PV exactly matches load
        
        Inverter inverter = new Inverter();
        Battery battery = new Battery();
        battery.setBatterySize(10.0);
        battery.setDischargeStop(100.0); // Prevent discharge

        Map<Inverter, SimulationWorker.InputData> inputDataMap = new HashMap<>();
        SimulationWorker.InputData iData = createInputData(inverter, battery, 5.0); // 50% SOC
        
        // Create scenario where PV exactly matches load
        List<SimulationInputData> simulationInputData = new ArrayList<>();
        simulationInputData.add(createSID(2.0, 2.0)); // 2kW load, 2kW PV
        simulationInputData.add(createSID(2.1, 2.1)); // Second row as required by framework
        iData.simulationInputData = simulationInputData;
        
        inputDataMap.put(inverter, iData);

        ArrayList<com.tfcode.comparetout.model.scenario.ScenarioSimulationData> outputRows = new ArrayList<>();
        SimulationWorker.processOneRow(1L, outputRows, 0, inputDataMap);

        // Verify that no grid interaction occurs
        assertEquals("No grid purchase", 0.0, outputRows.get(0).getBuy(), 0.001);
        assertEquals("No feed to grid", 0.0, outputRows.get(0).getFeed(), 0.001);
        assertEquals("No battery discharge", 0.0, outputRows.get(0).getBatToLoad(), 0.001);
        assertEquals("No PV to charge", 0.0, outputRows.get(0).getPvToCharge(), 0.001);
        assertEquals("SOC unchanged", 5.0, outputRows.get(0).getSOC(), 0.001);
    }
}