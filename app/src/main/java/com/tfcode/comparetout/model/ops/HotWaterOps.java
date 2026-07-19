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
import com.tfcode.comparetout.model.dao.HotWaterDAO;
import com.tfcode.comparetout.model.scenario.HWSchedule;
import com.tfcode.comparetout.model.scenario.HWSystem;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.Scenario2HWSchedule;
import com.tfcode.comparetout.model.scenario.Scenario2HWSystem;

import java.util.List;

/**
 * Hot-water (system + schedule) orchestration, moved verbatim from ScenarioDAO's
 * concrete {@code @Transaction} methods (mega-refactor C3). Each body is the
 * original wrapped in {@code db.runInTransaction} — identical atomicity. The
 * link*FromScenario helpers and saveHWDivert/updateHWDivert stay on ScenarioDAO
 * until C9 (called by the cross-domain linkAllComponentsFromScenario).
 */
public class HotWaterOps {

    private final ToutcDB db;
    private final ScenarioDAO scenarioDAO;
    private final HotWaterDAO hotWaterDAO;

    public HotWaterOps(ToutcDB db) {
        this.db = db;
        this.scenarioDAO = db.scenarioDAO();
        this.hotWaterDAO = db.hotWaterDAO();
    }

    // ---- HW system ----

    public void saveHWSystemForScenario(Long scenarioID, HWSystem hwSystem) {
        db.runInTransaction(() -> {
            long hwSystemID = hwSystem.getHwSystemIndex();
            if (hwSystemID == 0) {
                hwSystemID = scenarioDAO.addNewHWSystem(hwSystem);
                scenarioDAO.deleteHWSystemRelationsForScenario(Math.toIntExact(scenarioID));
                Scenario2HWSystem s2hws = new Scenario2HWSystem();
                s2hws.setScenarioID(scenarioID);
                s2hws.setHwSystemID(hwSystemID);
                scenarioDAO.addNewScenario2HWSystem(s2hws);

                Scenario scenario = scenarioDAO.getScenario(scenarioID);
                scenario.setHasHWSystem(true);
                scenarioDAO.updateScenario(scenario);
            }
            else {
                hotWaterDAO.updateHWSystem(hwSystem);
            }
        });
    }

    public void copyHWSettingsFromScenario(long fromScenarioID, Long toScenarioID) {
        db.runInTransaction(() -> {
            HWSystem hwSystem = scenarioDAO.getHWSystemForScenarioID(fromScenarioID);
            hwSystem.setHwSystemIndex(0L);
            long newHWSystemID = scenarioDAO.addNewHWSystem(hwSystem);

            scenarioDAO.deleteHWSystemRelationsForScenario(Math.toIntExact(toScenarioID));
            Scenario2HWSystem s2hws = new Scenario2HWSystem();
            s2hws.setScenarioID(toScenarioID);
            s2hws.setHwSystemID(newHWSystemID);
            scenarioDAO.addNewScenario2HWSystem(s2hws);

            Scenario toScenario = scenarioDAO.getScenario(toScenarioID);
            toScenario.setHasHWSystem(true);
            scenarioDAO.updateScenario(toScenario);

            scenarioDAO.deleteOrphanHWSystems();
        });
    }

    // ---- HW schedule ----

    public void deleteHWScheduleFromScenario(Long hwScheduleID, Long scenarioID) {
        db.runInTransaction(() -> {
            hotWaterDAO.deleteHWScheduleFromScenario1(hwScheduleID, scenarioID);
            List<HWSchedule> hwSchedules = scenarioDAO.getHWSchedulesForScenarioID(scenarioID);
            if (hwSchedules.isEmpty()) {
                Scenario scenario = scenarioDAO.getScenario(scenarioID);
                scenario.setHasHWSchedules(false);
                scenarioDAO.updateScenario(scenario);
            }
        });
    }

    public void saveHWScheduleForScenario(Long scenarioID, HWSchedule hwSchedule) {
        db.runInTransaction(() -> {
            long hwScheduleIndex = hwSchedule.getHwScheduleIndex();
            if (hwScheduleIndex == 0) {
                hwScheduleIndex = scenarioDAO.addNewHWSchedule(hwSchedule);
                Scenario2HWSchedule scenario2HWSchedule = new Scenario2HWSchedule();
                scenario2HWSchedule.setScenarioID(scenarioID);
                scenario2HWSchedule.setHwScheduleID(hwScheduleIndex);
                scenarioDAO.addNewScenario2HWSchedule(scenario2HWSchedule);

                Scenario scenario = scenarioDAO.getScenario(scenarioID);
                scenario.setHasHWSchedules(true);
                scenarioDAO.updateScenario(scenario);
            }
            else {
                hotWaterDAO.updateHWSchedule(hwSchedule);
            }
        });
    }

    public void copyHWScheduleFromScenario(long fromScenarioID, Long toScenarioID) {
        db.runInTransaction(() -> {
            List<HWSchedule> hwSchedules = scenarioDAO.getHWSchedulesForScenarioID(fromScenarioID);
            for (HWSchedule hwSchedule: hwSchedules) {
                hwSchedule.setHwScheduleIndex(0L);
                long newHWScheduleID = scenarioDAO.addNewHWSchedule(hwSchedule);

                Scenario2HWSchedule scenario2HWSchedule = new Scenario2HWSchedule();
                scenario2HWSchedule.setScenarioID(toScenarioID);
                scenario2HWSchedule.setHwScheduleID(newHWScheduleID);
                scenarioDAO.addNewScenario2HWSchedule(scenario2HWSchedule);

                Scenario toScenario = scenarioDAO.getScenario(toScenarioID);
                toScenario.setHasHWSchedules(true);
                scenarioDAO.updateScenario(toScenario);
            }

            scenarioDAO.deleteOrphanHWSchedules();
        });
    }
}
