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
import com.tfcode.comparetout.model.dao.HeatPumpDAO;
import com.tfcode.comparetout.model.scenario.HeatPump;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.Scenario2HeatPump;

import java.util.List;

/**
 * Heat-pump orchestration, moved verbatim from ScenarioDAO's concrete
 * {@code @Transaction} methods (mega-refactor C5). Each body is the original
 * wrapped in {@code db.runInTransaction} — identical atomicity.
 * linkHeatPumpFromScenario stays on ScenarioDAO until C9 (called by the
 * cross-domain linkAllComponentsFromScenario).
 */
public class HeatPumpOps {

    private final ToutcDB db;
    private final ScenarioDAO scenarioDAO;
    private final HeatPumpDAO heatPumpDAO;

    public HeatPumpOps(ToutcDB db) {
        this.db = db;
        this.scenarioDAO = db.scenarioDAO();
        this.heatPumpDAO = db.heatPumpDAO();
    }

    public void saveHeatPumpForScenario(Long scenarioID, HeatPump heatPump) {
        db.runInTransaction(() -> {
            long heatPumpID = heatPump.getHeatPumpIndex();
            if (heatPumpID == 0) {
                heatPumpID = scenarioDAO.addNewHeatPump(heatPump);
                Scenario2HeatPump s2hp = new Scenario2HeatPump();
                s2hp.setScenarioID(scenarioID);
                s2hp.setHeatPumpID(heatPumpID);
                scenarioDAO.addNewScenario2HeatPump(s2hp);

                // Guard against a bad scenarioID: never NPE here.
                Scenario scenario = scenarioDAO.getScenario(scenarioID);
                if (scenario != null) {
                    scenario.setHasHeatPump(true);
                    scenarioDAO.updateScenario(scenario);
                }
            }
            else {
                heatPumpDAO.updateHeatPump(heatPump);
            }
        });
    }

    public void copyHeatPumpFromScenario(long fromScenarioID, Long toScenarioID) {
        db.runInTransaction(() -> {
            List<HeatPump> heatPumps = scenarioDAO.getHeatPumpsForScenarioID(fromScenarioID);
            for (HeatPump heatPump : heatPumps) {
                heatPump.setHeatPumpIndex(0L);
                long newHeatPumpID = scenarioDAO.addNewHeatPump(heatPump);

                Scenario2HeatPump s2hp = new Scenario2HeatPump();
                s2hp.setScenarioID(toScenarioID);
                s2hp.setHeatPumpID(newHeatPumpID);
                scenarioDAO.addNewScenario2HeatPump(s2hp);

                Scenario toScenario = scenarioDAO.getScenario(toScenarioID);
                toScenario.setHasHeatPump(true);
                scenarioDAO.updateScenario(toScenario);
            }

            scenarioDAO.deleteOrphanHeatPumps();
        });
    }
}
