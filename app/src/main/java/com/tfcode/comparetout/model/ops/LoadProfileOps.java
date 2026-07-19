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
import com.tfcode.comparetout.model.dao.LoadProfileDAO;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.Scenario2LoadProfile;

/**
 * Load-profile orchestration, moved verbatim from ScenarioDAO's concrete
 * {@code @Transaction} methods (mega-refactor C7). Each body is the original
 * wrapped in {@code db.runInTransaction} — identical atomicity.
 * linkLoadProfileFromScenario stays on ScenarioDAO until C9 (called by the
 * cross-domain linkAllComponentsFromScenario); copyLoadProfileData and
 * getLoadProfileForScenarioID also stay on ScenarioDAO, so the copy op calls
 * them via scenarioDAO.
 */
public class LoadProfileOps {

    private final ToutcDB db;
    private final ScenarioDAO scenarioDAO;
    private final LoadProfileDAO loadProfileDAO;

    public LoadProfileOps(ToutcDB db) {
        this.db = db;
        this.scenarioDAO = db.scenarioDAO();
        this.loadProfileDAO = db.loadProfileDAO();
    }

    public long saveLoadProfile(Long scenarioID, LoadProfile loadProfile) {
        final long[] result = new long[1];
        db.runInTransaction(() -> {
            long loadProfileID = loadProfile.getLoadProfileIndex();
            if (loadProfileID == 0) {
                loadProfileID = scenarioDAO.addNewLoadProfile(loadProfile);
                scenarioDAO.deleteLoadProfileRelationsForScenario(Math.toIntExact(scenarioID));
            }
            else {
                loadProfileDAO.updateLoadProfile(loadProfile);
            }
            Scenario2LoadProfile s2lp = new Scenario2LoadProfile();
            s2lp.setScenarioID(scenarioID);
            s2lp.setLoadProfileID(loadProfileID);
            scenarioDAO.addNewScenario2LoadProfile(s2lp);

            Scenario scenario = scenarioDAO.getScenario(scenarioID);
            scenario.setHasLoadProfiles(true);
            scenarioDAO.updateScenario(scenario);
            result[0] = loadProfileID;
        });
        return result[0];
    }

    public void copyLoadProfileFromScenario(long fromScenarioID, Long toScenarioID) {
        db.runInTransaction(() -> {
            LoadProfile lp = scenarioDAO.getLoadProfileForScenarioID(fromScenarioID);
            long oldLoadProfileID = lp.getLoadProfileIndex();
            lp.setLoadProfileIndex(0L);
            long newLoadProfileID = scenarioDAO.addNewLoadProfile(lp);

            scenarioDAO.deleteLoadProfileRelationsForScenario(Math.toIntExact(toScenarioID));
            Scenario2LoadProfile s2lp = new Scenario2LoadProfile();
            s2lp.setScenarioID(toScenarioID);
            s2lp.setLoadProfileID(newLoadProfileID);
            scenarioDAO.addNewScenario2LoadProfile(s2lp);

            Scenario toScenario = scenarioDAO.getScenario(toScenarioID);
            toScenario.setHasLoadProfiles(true);
            scenarioDAO.updateScenario(toScenario);
            scenarioDAO.copyLoadProfileData(oldLoadProfileID, newLoadProfileID);

            scenarioDAO.deleteOrphanLoadProfiles();
        });
    }
}
