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
    public EVDivert evDivert;

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
            EVDivert evDivert) {
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
        this.evDivert = evDivert;
    }

}
