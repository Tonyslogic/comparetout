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
import com.tfcode.comparetout.model.dao.EvDAO;
import com.tfcode.comparetout.model.scenario.EVCharge;
import com.tfcode.comparetout.model.scenario.EVDivert;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.Scenario2EVCharge;
import com.tfcode.comparetout.model.scenario.Scenario2EVDivert;

import java.util.List;

/**
 * EV (charge + divert) orchestration, moved verbatim from ScenarioDAO
 * (mega-refactor C4). The save* methods were {@code @Transaction} and are
 * wrapped in {@code db.runInTransaction}; the copy and linkEVDivert methods
 * were NOT {@code @Transaction} and are copied unwrapped (no atomicity added).
 * linkEVChargeFromScenario stays on ScenarioDAO until C9 (called by the
 * cross-domain linkAllComponentsFromScenario).
 */
public class EvOps {

    private final ToutcDB db;
    private final ScenarioDAO scenarioDAO;
    private final EvDAO evDAO;

    public EvOps(ToutcDB db) {
        this.db = db;
        this.scenarioDAO = db.scenarioDAO();
        this.evDAO = db.evDAO();
    }

    // ---- EV charge ----

    public void saveEVChargeForScenario(Long scenarioID, EVCharge evCharge) {
        db.runInTransaction(() -> {
            long evScheduleIndex = evCharge.getEvChargeIndex();
            if (evScheduleIndex == 0) {
                evScheduleIndex = scenarioDAO.addNewEVCharge(evCharge);
                Scenario2EVCharge scenario2EVCharge = new Scenario2EVCharge();
                scenario2EVCharge.setScenarioID(scenarioID);
                scenario2EVCharge.setEvChargeID(evScheduleIndex);
                scenarioDAO.addNewScenario2EVCharge(scenario2EVCharge);

                Scenario scenario = scenarioDAO.getScenario(scenarioID);
                scenario.setHasEVCharges(true);
                scenarioDAO.updateScenario(scenario);
            }
            else {
                evDAO.updateEVCharge(evCharge);
            }
        });
    }

    public void copyEVChargeFromScenario(long fromScenarioID, Long toScenarioID) {
        List<EVCharge> evCharges = scenarioDAO.getEVChargesForScenarioID(fromScenarioID);
        for (EVCharge evCharge: evCharges) {
            evCharge.setEvChargeIndex(0L);
            long newEVChargeID = scenarioDAO.addNewEVCharge(evCharge);

            Scenario2EVCharge scenario2EVCharge = new Scenario2EVCharge();
            scenario2EVCharge.setScenarioID(toScenarioID);
            scenario2EVCharge.setEvChargeID(newEVChargeID);
            scenarioDAO.addNewScenario2EVCharge(scenario2EVCharge);

            Scenario toScenario = scenarioDAO.getScenario(toScenarioID);
            toScenario.setHasEVCharges(true);
            scenarioDAO.updateScenario(toScenario);
        }

        scenarioDAO.deleteOrphanEVCharges();
    }

    // ---- EV divert ----

    public void saveEVDivertForScenario(Long scenarioID, EVDivert evDivert) {
        db.runInTransaction(() -> {
            long evScheduleIndex = evDivert.getEvDivertIndex();
            if (evScheduleIndex == 0) {
                evScheduleIndex = scenarioDAO.addNewEVDivert(evDivert);
                Scenario2EVDivert scenario2EVDivert = new Scenario2EVDivert();
                scenario2EVDivert.setScenarioID(scenarioID);
                scenario2EVDivert.setEvDivertID(evScheduleIndex);
                scenarioDAO.addNewScenario2EVDivert(scenario2EVDivert);

                Scenario scenario = scenarioDAO.getScenario(scenarioID);
                scenario.setHasEVDivert(true);
                scenarioDAO.updateScenario(scenario);
            }
            else {
                evDAO.updateEVDivert(evDivert);
            }
        });
    }

    public void copyEVDivertFromScenario(long fromScenarioID, Long toScenarioID) {
        List<EVDivert> evDiverts = scenarioDAO.getEVDivertForScenarioID(fromScenarioID);
        for (EVDivert evDivert: evDiverts) {
            evDivert.setEvDivertIndex(0L);
            long newEVDivertID = scenarioDAO.addNewEVDivert(evDivert);

            Scenario2EVDivert scenario2EVDivert = new Scenario2EVDivert();
            scenario2EVDivert.setScenarioID(toScenarioID);
            scenario2EVDivert.setEvDivertID(newEVDivertID);
            scenarioDAO.addNewScenario2EVDivert(scenario2EVDivert);

            Scenario toScenario = scenarioDAO.getScenario(toScenarioID);
            toScenario.setHasEVDivert(true);
            scenarioDAO.updateScenario(toScenario);
        }

        scenarioDAO.deleteOrphanEVDiverts();
    }

    public void linkEVDivertFromScenario(long fromScenarioID, Long toScenarioID) {
        List<EVDivert> evDiverts = scenarioDAO.getEVDivertForScenarioID(fromScenarioID);
        for (EVDivert evDivert: evDiverts) {

            Scenario2EVDivert scenario2EVDivert = new Scenario2EVDivert();
            scenario2EVDivert.setScenarioID(toScenarioID);
            scenario2EVDivert.setEvDivertID(evDivert.getEvDivertIndex());
            scenarioDAO.addNewScenario2EVDivert(scenario2EVDivert);

            Scenario toScenario = scenarioDAO.getScenario(toScenarioID);
            toScenario.setHasEVDivert(true);
            scenarioDAO.updateScenario(toScenario);
        }

        scenarioDAO.deleteOrphanEVDiverts();
    }
}
