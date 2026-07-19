/*
 * Copyright (c) 2026. Tony Finnerty
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

package com.tfcode.comparetout.model.ops;

import com.tfcode.comparetout.model.ScenarioDAO;
import com.tfcode.comparetout.model.ToutcDB;
import com.tfcode.comparetout.model.dao.BatteryDAO;
import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.DischargeToGrid;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.Scenario2Battery;
import com.tfcode.comparetout.model.scenario.Scenario2DischargeToGrid;
import com.tfcode.comparetout.model.scenario.Scenario2LoadShift;

import java.util.List;

/**
 * Battery / load-shift / discharge orchestration, moved verbatim from
 * ScenarioDAO's concrete {@code @Transaction} methods (mega-refactor C2). Each
 * body is the original wrapped in {@code db.runInTransaction} — identical
 * atomicity. The link*FromScenario helpers stay on ScenarioDAO until C9
 * (called by the cross-domain linkAllComponentsFromScenario transaction).
 */
public class BatteryOps {

    private final ToutcDB db;
    private final ScenarioDAO scenarioDAO;
    private final BatteryDAO batteryDAO;

    public BatteryOps(ToutcDB db) {
        this.db = db;
        this.scenarioDAO = db.scenarioDAO();
        this.batteryDAO = db.batteryDAO();
    }

    // ---- Battery ----

    public void saveBatteryForScenario(Long scenarioID, Battery battery) {
        db.runInTransaction(() -> {
            long batteryID = battery.getBatteryIndex();
            if (batteryID == 0) {
                batteryID = scenarioDAO.addNewBattery(battery);
                Scenario2Battery s2b = new Scenario2Battery();
                s2b.setScenarioID(scenarioID);
                s2b.setBatteryID(batteryID);
                scenarioDAO.addNewScenario2Battery(s2b);

                // Guard against a bad scenarioID: never NPE here.
                Scenario scenario = scenarioDAO.getScenario(scenarioID);
                if (scenario != null) {
                    scenario.setHasBatteries(true);
                    scenarioDAO.updateScenario(scenario);
                }
            }
            else {
                batteryDAO.updateBattery(battery);
            }
        });
    }

    public void copyBatteryFromScenario(long fromScenarioID, Long toScenarioID) {
        db.runInTransaction(() -> {
            List<Battery> batteries = scenarioDAO.getBatteriesForScenarioID(fromScenarioID);
            for (Battery battery: batteries) {
                battery.setBatteryIndex(0L);
                long newBatteryID = scenarioDAO.addNewBattery(battery);

                Scenario2Battery s2b = new Scenario2Battery();
                s2b.setScenarioID(toScenarioID);
                s2b.setBatteryID(newBatteryID);
                scenarioDAO.addNewScenario2Battery(s2b);

                Scenario toScenario = scenarioDAO.getScenario(toScenarioID);
                toScenario.setHasBatteries(true);
                scenarioDAO.updateScenario(toScenario);
            }

            scenarioDAO.deleteOrphanBatteries();
        });
    }

    // ---- LoadShift ----

    public void saveLoadShiftForScenario(Long scenarioID, LoadShift loadShift) {
        db.runInTransaction(() -> {
            long loadShiftIndex = loadShift.getLoadShiftIndex();
            if (loadShiftIndex == 0) {
                loadShiftIndex = scenarioDAO.addNewLoadShift(loadShift);
                Scenario2LoadShift s2ls = new Scenario2LoadShift();
                s2ls.setScenarioID(scenarioID);
                s2ls.setLoadShiftID(loadShiftIndex);
                scenarioDAO.addNewScenario2LoadShift(s2ls);

                Scenario scenario = scenarioDAO.getScenario(scenarioID);
                scenario.setHasLoadShifts(true);
                scenarioDAO.updateScenario(scenario);
            }
            else {
                batteryDAO.updateLoadShift(loadShift);
            }
        });
    }

    public void copyLoadShiftFromScenario(long fromScenarioID, Long toScenarioID) {
        db.runInTransaction(() -> {
            List<LoadShift> loadShifts = scenarioDAO.getLoadShiftsForScenarioID(fromScenarioID);
            for (LoadShift loadShift: loadShifts) {
                loadShift.setLoadShiftIndex(0L);
                long newLoadShiftID = scenarioDAO.addNewLoadShift(loadShift);

                Scenario2LoadShift s2ls = new Scenario2LoadShift();
                s2ls.setScenarioID(toScenarioID);
                s2ls.setLoadShiftID(newLoadShiftID);
                scenarioDAO.addNewScenario2LoadShift(s2ls);

                Scenario toScenario = scenarioDAO.getScenario(toScenarioID);
                toScenario.setHasLoadShifts(true);
                scenarioDAO.updateScenario(toScenario);
            }

            scenarioDAO.deleteOrphanLoadShifts();
        });
    }

    // ---- DischargeToGrid ----

    public void saveDischargeForScenario(Long scenarioID, DischargeToGrid dischargeToGrid) {
        db.runInTransaction(() -> {
            long dischargeIndex = dischargeToGrid.getD2gIndex();
            if (dischargeIndex == 0) {
                dischargeIndex = scenarioDAO.addNewDischarge(dischargeToGrid);
                Scenario2DischargeToGrid s2d = new Scenario2DischargeToGrid();
                s2d.setScenarioID(scenarioID);
                s2d.setDischargeID(dischargeIndex);
                scenarioDAO.addNewScenario2Discharge(s2d);

                Scenario scenario = scenarioDAO.getScenario(scenarioID);
                scenario.setHasDischarges(true);
                scenarioDAO.updateScenario(scenario);
            }
            else {
                batteryDAO.updateDischarge(dischargeToGrid);
            }
        });
    }

    public void copyDischargeFromScenario(long fromScenarioID, Long toScenarioID) {
        db.runInTransaction(() -> {
            List<DischargeToGrid> discharges = scenarioDAO.getDischargesForScenarioID(fromScenarioID);
            for (DischargeToGrid discharge: discharges) {
                discharge.setD2gIndex(0L);
                long newD2GID = scenarioDAO.addNewDischarge(discharge);

                Scenario2DischargeToGrid s2d = new Scenario2DischargeToGrid();
                s2d.setScenarioID(toScenarioID);
                s2d.setDischargeID(newD2GID);
                scenarioDAO.addNewScenario2Discharge(s2d);

                Scenario toScenario = scenarioDAO.getScenario(toScenarioID);
                toScenario.setHasDischarges(true);
                scenarioDAO.updateScenario(toScenario);
            }

            scenarioDAO.deleteOrphanDischarges();
        });
    }
}
