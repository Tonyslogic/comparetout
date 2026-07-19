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

package com.tfcode.comparetout.model.json;

import android.util.Pair;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.model.IntHolder;
import com.tfcode.comparetout.model.json.priceplan.DayRateJson;
import com.tfcode.comparetout.model.json.priceplan.DynamicTermsJson;
import com.tfcode.comparetout.model.json.priceplan.MinuteRangeCostJson;
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile;
import com.tfcode.comparetout.model.json.priceplan.RestrictionEntryJson;
import com.tfcode.comparetout.model.json.priceplan.RestrictionJson;
import com.tfcode.comparetout.model.json.scenario.BatteryJson;
import com.tfcode.comparetout.model.json.scenario.HeatPumpJson;
import com.tfcode.comparetout.model.json.scenario.ChargeModelJson;
import com.tfcode.comparetout.model.json.scenario.DOWDistribution;
import com.tfcode.comparetout.model.json.scenario.DischargeToGridJson;
import com.tfcode.comparetout.model.json.scenario.EVChargeJson;
import com.tfcode.comparetout.model.json.scenario.EVDivertJson;
import com.tfcode.comparetout.model.json.scenario.HWDivertJson;
import com.tfcode.comparetout.model.json.scenario.HWScheduleJson;
import com.tfcode.comparetout.model.json.scenario.HWSystemJson;
import com.tfcode.comparetout.model.json.scenario.InverterJson;
import com.tfcode.comparetout.model.json.scenario.LoadProfileJson;
import com.tfcode.comparetout.model.json.scenario.LoadShiftJson;
import com.tfcode.comparetout.model.json.scenario.MonthlyDistribution;
import com.tfcode.comparetout.model.json.scenario.PanelJson;
import com.tfcode.comparetout.model.json.scenario.ScenarioJsonFile;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.DoubleHolder;
import com.tfcode.comparetout.model.priceplan.DynamicTerms;
import com.tfcode.comparetout.model.priceplan.MinuteRateRange;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.priceplan.RangeRate;
import com.tfcode.comparetout.model.priceplan.Restriction;
import com.tfcode.comparetout.model.priceplan.Restrictions;
import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.ChargeModel;
import com.tfcode.comparetout.model.scenario.DOWDist;
import com.tfcode.comparetout.model.scenario.DischargeToGrid;
import com.tfcode.comparetout.model.scenario.EVCharge;
import com.tfcode.comparetout.model.scenario.EVDivert;
import com.tfcode.comparetout.model.scenario.HWDivert;
import com.tfcode.comparetout.model.scenario.HWSchedule;
import com.tfcode.comparetout.model.scenario.HWSystem;
import com.tfcode.comparetout.model.scenario.HWUse;
import com.tfcode.comparetout.model.scenario.HeatPump;
import com.tfcode.comparetout.model.scenario.HourlyDist;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.MonthHolder;
import com.tfcode.comparetout.model.scenario.MonthlyDist;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility class for JSON serialization and deserialization operations.
 * <p>
 * This class provides a comprehensive set of static methods for converting between
 * JSON representations and domain model objects. It serves as the central hub for
 * all JSON operations within the application, handling both import and export
 * scenarios for configuration data.
 * <p>
 * The class supports bidirectional conversion for:
 * - Price plans and rate schedules
 * - Energy system scenarios and components  
 * - Hardware configurations (inverters, batteries, panels)
 * - Load profiles and energy consumption patterns
 * - System schedules and operational constraints
 * <p>
 * Key design principles:
 * - All methods are static for utility access
 * - Null safety is maintained throughout conversion processes
 * - Default values are provided for missing JSON properties
 * - Complex nested objects are handled recursively
 * 
 * @see Gson for underlying JSON processing
 * @see TypeToken for generic type handling
 */
public class JsonTools {

    // Mega-refactor C10: JsonTools is now a thin facade. Implementations live in
    // PricePlanJsonTools / ScenarioJsonTools; these one-line delegations keep all
    // existing JsonTools.* call-sites untouched.

    public static PricePlan createPricePlan(PricePlanJsonFile pp) {
        return PricePlanJsonTools.createPricePlan(pp);
    }

    public static DayRate createDayRate(DayRateJson drj) {
        return PricePlanJsonTools.createDayRate(drj);
    }

    public static String createPricePlanJson(Map<PricePlan, List<DayRate>> pricePlans) {
        return PricePlanJsonTools.createPricePlanJson(pricePlans);
    }

    public static String createSinglePricePlanJsonObject(PricePlan pp, List<DayRate> rates) {
        return PricePlanJsonTools.createSinglePricePlanJsonObject(pp, rates);
    }

    public static Scenario createScenario(ScenarioJsonFile sjf) {
        return ScenarioJsonTools.createScenario(sjf);
    }

    public static List<Inverter> createInverterList(List<InverterJson> ijs) {
        return ScenarioJsonTools.createInverterList(ijs);
    }

    public static Inverter createInverter(InverterJson ij) {
        return ScenarioJsonTools.createInverter(ij);
    }

    public static List<Battery> createBatteryList(List<BatteryJson> jsons) {
        return ScenarioJsonTools.createBatteryList(jsons);
    }

    public static Battery createBattery(BatteryJson bj) {
        return ScenarioJsonTools.createBattery(bj);
    }

    public static List<HeatPump> createHeatPumpList(List<HeatPumpJson> jsons) {
        return ScenarioJsonTools.createHeatPumpList(jsons);
    }

    public static HeatPump createHeatPump(HeatPumpJson hpj) {
        return ScenarioJsonTools.createHeatPump(hpj);
    }

    public static List<Panel> createPanelList(List<PanelJson> jsons) {
        return ScenarioJsonTools.createPanelList(jsons);
    }

    public static Panel createPanel(PanelJson pj) {
        return ScenarioJsonTools.createPanel(pj);
    }

    public static HWSystem createHWSystem(HWSystemJson hwj) {
        return ScenarioJsonTools.createHWSystem(hwj);
    }

    public static LoadProfile createLoadProfile(LoadProfileJson lpj) {
        return ScenarioJsonTools.createLoadProfile(lpj);
    }

    public static List<LoadShift> createLoadShiftList(List<LoadShiftJson> jsons) {
        return ScenarioJsonTools.createLoadShiftList(jsons);
    }

    public static LoadShift createLoadShift(LoadShiftJson loadShiftJson) {
        return ScenarioJsonTools.createLoadShift(loadShiftJson);
    }

    public static List<DischargeToGrid> createDischargeList(List<DischargeToGridJson> jsons) {
        return ScenarioJsonTools.createDischargeList(jsons);
    }

    public static DischargeToGrid createDischarge(DischargeToGridJson dischargeJson) {
        return ScenarioJsonTools.createDischarge(dischargeJson);
    }

    public static List<EVCharge> createEVChargeList(List<EVChargeJson> jsons) {
        return ScenarioJsonTools.createEVChargeList(jsons);
    }

    public static EVCharge createEVCharge(EVChargeJson evChargeJson) {
        return ScenarioJsonTools.createEVCharge(evChargeJson);
    }

    public static List<HWSchedule> createHWScheduleList(List<HWScheduleJson> jsons) {
        return ScenarioJsonTools.createHWScheduleList(jsons);
    }

    public static HWSchedule createHWSchedule(HWScheduleJson hwScheduleJson) {
        return ScenarioJsonTools.createHWSchedule(hwScheduleJson);
    }

    public static HWDivert createHWDivert(HWDivertJson hwDivertJson) {
        return ScenarioJsonTools.createHWDivert(hwDivertJson);
    }

    public static List<EVDivert> createEVDivertList(List<EVDivertJson> jsons, EVDivertJson evDivert) {
        return ScenarioJsonTools.createEVDivertList(jsons, evDivert);
    }

    public static EVDivert createEVDivert(EVDivertJson evDivertJson) {
        return ScenarioJsonTools.createEVDivert(evDivertJson);
    }

    public static String createSingleScenarioJsonString(ScenarioJsonFile sjf) {
        return ScenarioJsonTools.createSingleScenarioJsonString(sjf);
    }

    public static ScenarioJsonFile createSingleScenarioJson(
            Scenario scenario,
            List<Inverter> inverters,
            List<Battery> batteries,
            List<Panel> panels,
            HWSystem hwSystem,
            LoadProfile loadProfile,
            List<LoadShift> loadShifts,
            List<DischargeToGrid> discharges,
            List<EVCharge> evCharges,
            List<HWSchedule> hwSchedules,
            HWDivert hwDivert,
            List<EVDivert> evDiverts,
            List<HeatPump> heatPumps) {
        return ScenarioJsonTools.createSingleScenarioJson(scenario, inverters, batteries, panels, hwSystem, loadProfile, loadShifts, discharges, evCharges, hwSchedules, hwDivert, evDiverts, heatPumps);
    }

    public static ArrayList<EVDivertJson> createEVDivertJson(List<EVDivert> evDiverts) {
        return ScenarioJsonTools.createEVDivertJson(evDiverts);
    }

    public static ArrayList<HWScheduleJson> createHWScheduleJson(List<HWSchedule> hwSchedules) {
        return ScenarioJsonTools.createHWScheduleJson(hwSchedules);
    }

    public static ArrayList<EVChargeJson> createEVChargeJson(List<EVCharge> evCharges) {
        return ScenarioJsonTools.createEVChargeJson(evCharges);
    }

    public static ArrayList<LoadShiftJson> createLoadShiftJson(List<LoadShift> loadShifts) {
        return ScenarioJsonTools.createLoadShiftJson(loadShifts);
    }

    public static ArrayList<DischargeToGridJson> createDischargeJson(List<DischargeToGrid> dischargeToGrids) {
        return ScenarioJsonTools.createDischargeJson(dischargeToGrids);
    }

    public static LoadProfileJson createLoadProfileJson(LoadProfile loadProfile) {
        return ScenarioJsonTools.createLoadProfileJson(loadProfile);
    }

    public static HWSystemJson createHWSystemJson(HWSystem hwSystem) {
        return ScenarioJsonTools.createHWSystemJson(hwSystem);
    }

    public static ArrayList<PanelJson> createPanelListJson(List<Panel> panels) {
        return ScenarioJsonTools.createPanelListJson(panels);
    }

    public static ArrayList<BatteryJson> createBatteryListJson(List<Battery> batteries) {
        return ScenarioJsonTools.createBatteryListJson(batteries);
    }

    public static ArrayList<HeatPumpJson> createHeatPumpListJson(List<HeatPump> heatPumps) {
        return ScenarioJsonTools.createHeatPumpListJson(heatPumps);
    }

    public static ArrayList<InverterJson> createInverterListJson(List<Inverter> inverters) {
        return ScenarioJsonTools.createInverterListJson(inverters);
    }

    public static InverterJson createInverterJson(Inverter inverter) {
        return ScenarioJsonTools.createInverterJson(inverter);
    }

    public static String createScenarioList(List<ScenarioComponents> scenarios) {
        return ScenarioJsonTools.createScenarioList(scenarios);
    }

    public static List<ScenarioComponents> createScenarioComponentList(List<ScenarioJsonFile> scenarioJsonFiles) {
        return ScenarioJsonTools.createScenarioComponentList(scenarioJsonFiles);
    }
}
