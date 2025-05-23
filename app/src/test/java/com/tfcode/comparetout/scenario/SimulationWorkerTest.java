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

        SimulationWorker.InputData idata = new SimulationWorker.InputData(inverter, simulationInputData, battery, cfg, null, null, null, null, null, null, 0);

        inputDataMap.put(inverter, idata);

        // FULL BATTERY, NO DISCHARGE; INITIAL ROW; SOLAR > LOAD; LS=Always
        int row = 0;
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        ScenarioSimulationData aRow = outputRows.get(row);

        assertEquals(0, aRow.getBuy(), 0);
        double ac2dcLoss = 1 - inverter.getDc2acLoss()/ 100d;
        double expected = (tpv * ac2dcLoss - load) - 0 ;
        assertEquals(expected, aRow.getFeed(), 0);
        expected = battery.getBatterySize();
        assertEquals(expected, aRow.getSOC(), 0);
        assertEquals(0, aRow.getPvToCharge(), 0);
        assertEquals(0, aRow.getBatToLoad(), 0);
        assertEquals(load, aRow.getLoad(), 0);
        assertEquals(tpv, aRow.getPv(), 0);

        // FULL BATTERY, DISCHARGE; 2ND ROW; SOLAR > LOAD; LS=Always
        row++;
        battery.setDischargeStop(20.0D);
        simulationInputData.add(createSID(load, tpv));
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        aRow = outputRows.get(row);

        expected = 0D; // Excess PV
        assertEquals(expected, aRow.getBuy(), 0);
        double dc2acLoss = (100d - inverter.getDc2acLoss())/100d;
        expected = ((tpv * dc2acLoss - load) ) ; // effective PV - load
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
        aRow = outputRows.get(row);

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
        aRow = outputRows.get(row);

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
        SimulationInputData sid2 = createSID(0, tpv2);
        simulationInputData2.add(sid2);

        // FULL BATTERY, NO DISCHARGE; INITIAL ROW; SOLAR > LOAD
        int row = 0;
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        ScenarioSimulationData aRow = outputRows.get(row);


        assertEquals(load, aRow.getLoad(), 0);
        double expected = tpv1 + tpv2;
        assertEquals(expected, aRow.getPv(), 0);assertEquals(0, aRow.getBuy(), 0);
        double ac2dcLoss = 1 - inverter1.getDc2acLoss()/ 100d;
        expected = ((tpv1 + tpv2) * ac2dcLoss - load) - 0 ;
        assertEquals(expected, aRow.getFeed(), 0);
        expected = battery1.getBatterySize();
        assertEquals(expected, aRow.getSOC(), 0);
        assertEquals(0, aRow.getPvToCharge(), 0);
        assertEquals(0, aRow.getBatToLoad(), 0);
    }

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
        inputDataMap.put(inverter1, iData1);

        List<SimulationInputData> simulationInputData2 = new ArrayList<>();
        Inverter inverter2 = new Inverter();
        Battery battery2 = new Battery();
        battery2.setDischargeStop(100d);SimulationWorker.InputData iData2 = new SimulationWorker.InputData(inverter2, simulationInputData2, battery2, null, null, null, null, null, null, null, 0);
        inputDataMap.put(inverter2, iData2);

        double load = 1.1;
        double tpv1 = 1.1;
        double tpv2 = 1.0;
        SimulationInputData sid1 = createSID(load, tpv1);
        simulationInputData1.add(sid1);
        SimulationInputData sid2 = createSID(0, tpv2);
        simulationInputData2.add(sid2);

        // FULL BATTERY, NO DISCHARGE; INITIAL ROW; SOLAR > LOAD
        int row = 0;
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        ScenarioSimulationData aRow = outputRows.get(row);

        assertEquals(0, aRow.getBuy(), 0);
        double ac2dcLoss = 1 - inverter1.getDc2acLoss()/ 100d;
        double expected = ((tpv1 + tpv2) * ac2dcLoss - load) - 0 ;
        assertEquals(expected, aRow.getFeed(), 0);
        expected = battery1.getBatterySize() + battery2.getBatterySize();
        assertEquals(expected, aRow.getSOC(), 0);
        assertEquals(0, aRow.getPvToCharge(), 0);
        assertEquals(0, aRow.getBatToLoad(), 0);
        assertEquals(load, aRow.getLoad(), 0);
        expected = tpv1 + tpv2;
        assertEquals(expected, aRow.getPv(), 0);

        // FULL BATTERY, NO DISCHARGE; 2ND ROW; NO SOLAR
        row++;
        tpv1 = 0;
        tpv2 = 0;
        simulationInputData1.add(createSID(load,tpv1));
        simulationInputData2.add(createSID(0, tpv2));

        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        aRow = outputRows.get(row);

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
        aRow = outputRows.get(row);


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

        SimulationWorker.InputData idata = new SimulationWorker.InputData(inverter, simulationInputData, battery, null, null, null, null, null, null, null, 0);

        inputDataMap.put(inverter, idata);

        // FULL BATTERY, NO DISCHARGE; INITIAL ROW; SOLAR > LOAD
        int row = 0;
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        ScenarioSimulationData aRow = outputRows.get(row);

        assertEquals(0, aRow.getBuy(), 0);
        double ac2dcLoss = 1 - inverter.getDc2acLoss()/ 100d;
        double expected = (tpv * ac2dcLoss - load) - 0 ;
        assertEquals(expected, aRow.getFeed(), 0);
        expected = battery.getBatterySize();
        assertEquals(expected, aRow.getSOC(), 0);
        assertEquals(0, aRow.getPvToCharge(), 0);
        assertEquals(0, aRow.getBatToLoad(), 0);
        assertEquals(load, aRow.getLoad(), 0);
        assertEquals(tpv, aRow.getPv(), 0);

        // FULL BATTERY, NO DISCHARGE; 2ND ROW; NO SOLAR
        row++;
        tpv = 0;
        simulationInputData.add(createSID(load,tpv));
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        aRow = outputRows.get(row);

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
        aRow = outputRows.get(row);

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
        aRow = outputRows.get(row);

        double lastSOC =  battery.getBatterySize() - (battery.getMaxCharge() * (1 + battery.getStorageLoss()/100d));

        assertEquals(0, aRow.getBuy(), 0);
        double maxCharge = SimulationWorker.InputData.getMaxChargeForSOC(lastSOC, battery);
        double dc2acLoss = (100d - inverter.getDc2acLoss())/100d;
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
        aRow = outputRows.get(row);

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
}