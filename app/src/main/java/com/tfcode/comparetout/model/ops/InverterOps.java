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
import com.tfcode.comparetout.model.dao.InverterDAO;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.Scenario2Inverter;

import java.util.List;

/**
 * Inverter-domain orchestration, moved verbatim from ScenarioDAO's concrete
 * {@code @Transaction} methods (mega-refactor C1). Each method body is the
 * original wrapped in {@code db.runInTransaction} — identical atomicity.
 */
public class InverterOps {

    private final ToutcDB db;
    private final ScenarioDAO scenarioDAO;
    private final InverterDAO inverterDAO;

    public InverterOps(ToutcDB db) {
        this.db = db;
        this.scenarioDAO = db.scenarioDAO();
        this.inverterDAO = db.inverterDAO();
    }

    public long saveInverter(Long scenarioID, Inverter inverter) {
        final long[] result = new long[1];
        db.runInTransaction(() -> {
            long inverterID = inverter.getInverterIndex();
            if (inverterID == 0) {
                inverterID = scenarioDAO.addNewInverter(inverter);
                Scenario2Inverter s2i = new Scenario2Inverter();
                s2i.setScenarioID(scenarioID);
                s2i.setInverterID(inverterID);
                scenarioDAO.addNewScenario2Inverter(s2i);

                Scenario scenario = scenarioDAO.getScenario(scenarioID);
                scenario.setHasInverters(true);
                scenarioDAO.updateScenario(scenario);
            }
            else {
                inverterDAO.updateInverter(inverter);
            }
            result[0] = inverterID;
        });
        return result[0];
    }

    public void deleteInverterFromScenario(Long inverterID, Long scenarioID) {
        db.runInTransaction(() -> {
            inverterDAO.removeScenario2Inverter(inverterID, scenarioID);
            scenarioDAO.deleteOrphanInverters();
            List<Inverter> inverters = scenarioDAO.getInvertersForScenarioID(scenarioID);
            if (inverters.isEmpty()) {
                Scenario scenario = scenarioDAO.getScenario(scenarioID);
                scenario.setHasInverters(false);
                scenarioDAO.updateScenario(scenario);
            }
        });
    }

    public void copyInverterFromScenario(long fromScenarioID, Long toScenarioID) {
        db.runInTransaction(() -> {
            List<Inverter> inverters = scenarioDAO.getInvertersForScenarioID(fromScenarioID);
            for (Inverter inverter: inverters) {
                inverter.setInverterIndex(0L);
                long newInverterID = scenarioDAO.addNewInverter(inverter);

                Scenario2Inverter s2i = new Scenario2Inverter();
                s2i.setScenarioID(toScenarioID);
                s2i.setInverterID(newInverterID);
                scenarioDAO.addNewScenario2Inverter(s2i);

                Scenario toScenario = scenarioDAO.getScenario(toScenarioID);
                toScenario.setHasInverters(true);
                scenarioDAO.updateScenario(toScenario);
            }

            scenarioDAO.deleteOrphanInverters();
        });
    }

    // NOTE: linkInverterFromScenario deliberately NOT moved here. It is called
    // by ScenarioDAO.linkAllComponentsFromScenario (a cross-domain lifecycle
    // @Transaction that stays on ScenarioDAO until phase C9), so it remains on
    // ScenarioDAO until then to avoid a same-transaction DAO→Ops hop.
}
