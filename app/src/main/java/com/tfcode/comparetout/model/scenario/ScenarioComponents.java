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

package com.tfcode.comparetout.model.scenario;

import java.util.List;

public class ScenarioComponents {
    public Scenario scenario;
    public List<Inverter> inverters;
    public List<Battery> batteries;
    public List<Panel> panels;
    public HWSystem hwSystem;
    public LoadProfile loadProfile;
    public List<LoadShift> loadShifts;
    public List<EVCharge> evCharges;
    public List<HWSchedule> hwSchedules;
    public HWDivert hwDivert;
    public List<EVDivert> evDiverts;

    public ScenarioComponents (
            Scenario scenario,
            List<Inverter> inverters,
            List<Battery> batteries,
            List<Panel> panels,
            HWSystem hwSystem,
            LoadProfile loadProfile,
            List<LoadShift> loadShifts,
            List<EVCharge> evCharges,
            List<HWSchedule> hwSchedules,
            HWDivert hwDivert,
            List<EVDivert> evDiverts) {
        this.scenario = scenario;
        this.inverters = inverters;
        this.batteries = batteries;
        this.panels = panels;
        this.hwSystem = hwSystem;
        this.loadProfile = loadProfile;
        this.loadShifts = loadShifts;
        this.evCharges = evCharges;
        this.hwSchedules = hwSchedules;
        this.hwDivert = hwDivert;
        this.evDiverts = evDiverts;
    }

}
