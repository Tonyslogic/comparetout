package com.tfcode.comparetout.scenario;

import static org.junit.Assert.*;

import static java.lang.Double.min;

import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;
import com.tfcode.comparetout.model.scenario.SimulationInputData;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimulationWorkerTest {

    @Test
    public void processOneRow() {
        long scenarioID = 1;
        ArrayList<ScenarioSimulationData> outputRows = new ArrayList<>();
        Map<Inverter, SimulationWorker.InputData> inputDataMap = new HashMap<>();

        Inverter inverter = new Inverter();
        Battery battery = new Battery();
        battery.setDischargeStop(100d);

        List<SimulationInputData> simulationInputData = new ArrayList<>();
        SimulationInputData sid = createSID(1.1, 2.1);
        simulationInputData.add(sid);

        SimulationWorker.InputData idata = new SimulationWorker.InputData(inverter, simulationInputData, battery);

        inputDataMap.put(inverter, idata);

        // FULL BATTERY, NO DISCHARGE; INITIAL ROW; SOLAR > LOAD
        int row = 0;
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        ScenarioSimulationData aRow = outputRows.get(row);

        assertEquals(0, aRow.getBuy(), 0);
        assertEquals(1.0 * .95, aRow.getFeed(), 0);
        assertEquals(5.7, aRow.getSOC(), 0);
        assertEquals(0, aRow.getPvToCharge(), 0);
        assertEquals(0, aRow.getBatToLoad(), 0);
        assertEquals(1.1, aRow.getLoad(), 0);
        assertEquals(2.1, aRow.getPv(), 0);

        // FULL BATTERY, NO DISCHARGE; 2ND ROW; NO SOLAR
        row++;
        simulationInputData.add(createSID(1.1, 0));
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        aRow = outputRows.get(row);

        assertEquals(1.1, aRow.getBuy(), 0);
        assertEquals(0, aRow.getFeed(), 0);
        assertEquals(5.7, aRow.getSOC(), 0);
        assertEquals(0, aRow.getPvToCharge(), 0);
        assertEquals(0, aRow.getBatToLoad(), 0);
        assertEquals(1.1, aRow.getLoad(), 0);
        assertEquals(0, aRow.getPv(), 0);

        // FULL BATTERY, DISCHARGE; 2ND ROW; NO SOLAR
        row++;
        simulationInputData.add(createSID(1.1, 0));
        battery.setDischargeStop(20.0);
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        aRow = outputRows.get(row);

        assertEquals(1.1 - 0.225, aRow.getBuy(), 0);
        assertEquals(0, aRow.getFeed(), 0);
        assertEquals(5.7 - (0.225 * 1.01), aRow.getSOC(), 0);
        assertEquals(0, aRow.getPvToCharge(), 0);
        assertEquals(0.225 * 1.01, aRow.getBatToLoad(), 0);
        assertEquals(1.1, aRow.getLoad(), 0);
        assertEquals(0, aRow.getPv(), 0);

        // 90%+ FULL BATTERY, DISCHARGE; 2ND ROW; NO SOLAR
        row++;
        simulationInputData.add(createSID(0, 1));
        SimulationWorker.processOneRow(scenarioID, outputRows, row, inputDataMap);
        aRow = outputRows.get(row);

        double lastSOC = 5.7 - (0.225 * 1.01);

        assertEquals(0, aRow.getBuy(), 0);
        assertEquals((1 - 0.225 * .1) * 0.95, aRow.getFeed(), 0);
        assertEquals(lastSOC + 0.225 * .1, aRow.getSOC(), 0);
        assertEquals(0.0225, aRow.getPvToCharge(), 0);
        assertEquals(0, aRow.getBatToLoad(), 0);
        assertEquals(0, aRow.getLoad(), 0);
        assertEquals(1, aRow.getPv(), 0);

        lastSOC = lastSOC + 0.225 * .1;

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