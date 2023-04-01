package com.tfcode.comparetout.model.json;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.ChargeModel;
import com.tfcode.comparetout.model.scenario.DOWDist;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.DoubleHolder;
import com.tfcode.comparetout.model.scenario.EVCharge;
import com.tfcode.comparetout.model.scenario.EVDivert;
import com.tfcode.comparetout.model.scenario.HWDivert;
import com.tfcode.comparetout.model.scenario.HWSchedule;
import com.tfcode.comparetout.model.scenario.HWSystem;
import com.tfcode.comparetout.model.scenario.HWUse;
import com.tfcode.comparetout.model.scenario.HourlyDist;
import com.tfcode.comparetout.model.IntHolder;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.MonthHolder;
import com.tfcode.comparetout.model.scenario.MonthlyDist;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;
import com.tfcode.comparetout.model.json.priceplan.DayRateJson;
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile;
import com.tfcode.comparetout.model.json.scenario.BatteryJson;
import com.tfcode.comparetout.model.json.scenario.ChargeModelJson;
import com.tfcode.comparetout.model.json.scenario.DOWDistribution;
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

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JsonTools {


    public static PricePlan createPricePlan(PricePlanJsonFile pp) {
        PricePlan p = new PricePlan();
        p.setPlanName(pp.plan);
        p.setSupplier(pp.supplier);
        p.setFeed(pp.feed);
        p.setStandingCharges(pp.standingCharges);
        p.setSignUpBonus(pp.bonus);
        p.setActive(pp.active);
        p.setLastUpdate(pp.lastUpdate);
        p.setReference(pp.reference);
        return p;
    }

    public static DayRate createDayRate(DayRateJson drj){
        DayRate dr = new DayRate();
        if (drj.endDate == null) dr.setEndDate("12/31");
        else dr.setEndDate(drj.endDate);
        if (drj.startDate == null) dr.setStartDate("01/01");
        else dr.setStartDate(drj.startDate);
        IntHolder ih = new IntHolder();
        ih.ints = drj.days;
        dr.setDays(ih);
        DoubleHolder dh = new DoubleHolder();
        dh.doubles = drj.hours;
        dr.setHours(dh);
        if (drj.dbID == null) dr.setDayRateIndex(0L);
        else dr.setDayRateIndex(drj.dbID);
        return dr;
    }

    public static String createPricePlanJson(Map<PricePlan, List<DayRate>> pricePlans) {
        ArrayList<PricePlanJsonFile> ppList = new ArrayList<>();
            for (Map.Entry<PricePlan, List<DayRate>> entry : pricePlans.entrySet()) {
                ArrayList<DayRateJson> dayRateJsons = new ArrayList<>();
                for (DayRate dr : entry.getValue()){
                    DayRateJson drj = new DayRateJson();
                    drj.startDate = dr.getStartDate();
                    drj.endDate = dr.getEndDate();
                    drj.days = (ArrayList<Integer>) dr.getDays().ints;
                    drj.hours = (ArrayList<Double>) dr.getHours().doubles;
                    drj.dbID = dr.getDayRateIndex();
                    dayRateJsons.add(drj);
                }
                PricePlanJsonFile ppj = new PricePlanJsonFile();
                ppj.rates = dayRateJsons;
                ppj.active = entry.getKey().isActive();
                ppj.plan = entry.getKey().getPlanName();
                ppj.bonus = entry.getKey().getSignUpBonus();
                ppj.feed = entry.getKey().getFeed();
                ppj.lastUpdate = entry.getKey().getLastUpdate();
                ppj.standingCharges = entry.getKey().getStandingCharges();
                ppj.reference = entry.getKey().getReference();
                ppj.supplier = entry.getKey().getSupplier();
                ppList.add(ppj);
            }
        Type type = new TypeToken<List<PricePlanJsonFile>>(){}.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(ppList,type);
    }

    public static String createSinglePricePlanJsonObject(PricePlan pp, List<DayRate> rates) {
        ArrayList<DayRateJson> dayRateJsons = new ArrayList<>();
        for (DayRate dr : rates) {
            DayRateJson drj = new DayRateJson();
            drj.startDate = dr.getStartDate();
            drj.endDate = dr.getEndDate();
            drj.days = (ArrayList<Integer>) dr.getDays().ints;
            drj.hours = (ArrayList<Double>) dr.getHours().doubles;
            drj.dbID = dr.getDayRateIndex();
            dayRateJsons.add(drj);
        }
        PricePlanJsonFile ppj = new PricePlanJsonFile();
        ppj.rates = dayRateJsons;
        ppj.active = pp.isActive();
        ppj.plan = pp.getPlanName();
        ppj.bonus = pp.getSignUpBonus();
        ppj.feed = pp.getFeed();
        ppj.lastUpdate = pp.getLastUpdate();
        ppj.standingCharges = pp.getStandingCharges();
        ppj.reference = pp.getReference();
        ppj.supplier = pp.getSupplier();

        Type type = new TypeToken<PricePlanJsonFile>(){}.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(ppj, type);
    }

    /* ****************************************************************
    // Scenario conversions
    ***************************************************************** */

    public static Scenario createScenario(ScenarioJsonFile sjf){
        Scenario scenario = new Scenario();
        scenario.setScenarioName(sjf.name);
        return scenario;
    }

    public static List<Inverter> createInverterList(List<InverterJson> ijs) {
        ArrayList<Inverter> inverters = new ArrayList<>();
        if (!(null == ijs)){
            for (InverterJson ij : ijs) {
                Inverter inverter = createInverter(ij);
                inverters.add(inverter);
            }
        }
        return inverters;
    }

    public static Inverter createInverter(InverterJson ij) {
        Inverter inverter = new Inverter();
        inverter.setInverterName(ij.name);
        inverter.setMaxInverterLoad(ij.maxInverterLoad);
        inverter.setAc2dcLoss(ij.ac2dcLoss);
        inverter.setDc2acLoss(ij.dc2acLoss);
        inverter.setDc2dcLoss(ij.dc2dcLoss);
        inverter.setMpptCount(ij.mPPTCount);
        inverter.setMinExcess(ij.minExcess);
        return inverter;
    }

    public static List<Battery> createBatteryList(List<BatteryJson> jsons) {
        ArrayList<Battery> batteries = new ArrayList<>();
        if (!(null == jsons)){
            for (BatteryJson json : jsons) {
                Battery battery = createBattery(json);
                batteries.add(battery);
            }
        }
        return batteries;
    }

    public static Battery createBattery(BatteryJson bj) {
        Battery battery = new Battery();
        battery.setBatterySize(bj.batterySize);
        battery.setDischargeStop(bj.dischargeStop);
        ChargeModel chargeModel = new ChargeModel();
        chargeModel.percent0 = bj.chargeModel.percent0;
        chargeModel.percent12 = bj.chargeModel.percent12;
        chargeModel.percent90 = bj.chargeModel.percent90;
        chargeModel.percent100 = bj.chargeModel.percent100;
        battery.setChargeModel(chargeModel);
        battery.setMaxDischarge(bj.maxDischarge);
        battery.setMaxCharge(bj.maxCharge);
        battery.setStorageLoss(bj.storageLoss);
        battery.setInverter(bj.inverter);
        return battery;
    }

    public static List<Panel> createPanelList(List<PanelJson> jsons) {
        ArrayList<Panel> entityList = new ArrayList<>();
        if (!(null == jsons)){
            for (PanelJson json : jsons) {
                Panel entity = createPanel(json);
                entityList.add(entity);
            }
        }
        return entityList;
    }

    public static Panel createPanel(PanelJson pj) {
        Panel panel = new Panel();
        panel.setPanelCount(pj.panelCount);
        panel.setPanelkWp(pj.panelkWp);
        panel.setAzimuth(pj.azimuth);
        panel.setSlope(pj.slope);
        panel.setLatitude(pj.latitude);
        panel.setLongitude(pj.longitude);
        panel.setInverter(pj.inverter);
        panel.setMppt(pj.mppt);
        return panel;
    }

    public static HWSystem createHWSystem(HWSystemJson hwj) {
        HWSystem hwSystem = new HWSystem();
        hwSystem.setHwCapacity(hwj.hwCapacity);
        hwSystem.setHwUsage(hwj.hwUsage);
        hwSystem.setHwIntake(hwj.hwIntake);
        hwSystem.setHwTarget(hwj.hwTarget);
        hwSystem.setHwLoss(hwj.hwLoss);
        hwSystem.setHwRate(hwj.hwRate);
        HWUse hwUse = new HWUse();
        hwUse.setUsage(hwj.hwUse);
        hwSystem.setHwUse(hwUse);
        return hwSystem;
    }

    public static LoadProfile createLoadProfile(LoadProfileJson lpj) {
        LoadProfile loadProfile = new LoadProfile();
        loadProfile.setAnnualUsage(lpj.annualUsage);
        loadProfile.setHourlyBaseLoad(lpj.hourlyBaseLoad);
        loadProfile.setGridImportMax(lpj.gridImportMax);
        loadProfile.setGridExportMax(lpj.gridExportMax);
        HourlyDist hourlyDist = new HourlyDist();
        hourlyDist.dist = lpj.hourlyDistribution;
        loadProfile.setHourlyDist(hourlyDist);
        DOWDist dowDist = new DOWDist();
        dowDist.dowDist.add(0,lpj.dayOfWeekDistribution.sun);
        dowDist.dowDist.add(1,lpj.dayOfWeekDistribution.mon);
        dowDist.dowDist.add(2,lpj.dayOfWeekDistribution.tue);
        dowDist.dowDist.add(3,lpj.dayOfWeekDistribution.wed);
        dowDist.dowDist.add(4,lpj.dayOfWeekDistribution.thu);
        dowDist.dowDist.add(5,lpj.dayOfWeekDistribution.fri);
        dowDist.dowDist.add(6,lpj.dayOfWeekDistribution.sat);
        loadProfile.setDowDist(dowDist);
        MonthlyDist monthlyDist = new MonthlyDist();
        monthlyDist.monthlyDist.set(0, lpj.monthlyDistribution.jan);
        monthlyDist.monthlyDist.set(1, lpj.monthlyDistribution.feb);
        monthlyDist.monthlyDist.set(2, lpj.monthlyDistribution.mar);
        monthlyDist.monthlyDist.set(3, lpj.monthlyDistribution.apr);
        monthlyDist.monthlyDist.set(4, lpj.monthlyDistribution.may);
        monthlyDist.monthlyDist.set(5, lpj.monthlyDistribution.jun);
        monthlyDist.monthlyDist.set(6, lpj.monthlyDistribution.jul);
        monthlyDist.monthlyDist.set(7, lpj.monthlyDistribution.aug);
        monthlyDist.monthlyDist.set(8, lpj.monthlyDistribution.sep);
        monthlyDist.monthlyDist.set(9, lpj.monthlyDistribution.oct);
        monthlyDist.monthlyDist.set(10, lpj.monthlyDistribution.nov);
        monthlyDist.monthlyDist.set(11, lpj.monthlyDistribution.dec);
        loadProfile.setMonthlyDist(monthlyDist);
        return loadProfile;
    }

    public static List<LoadShift> createLoadShiftList(List<LoadShiftJson> jsons) {
        ArrayList<LoadShift> entityList = new ArrayList<>();
        if (!(null == jsons)){
            for (LoadShiftJson json : jsons) {
                LoadShift entity = createLoadShift(json);
                entityList.add(entity);
            }
        }
        return entityList;
    }

    public static LoadShift createLoadShift(LoadShiftJson lsj) {
        LoadShift loadShift = new LoadShift();
        loadShift.setName(lsj.name);
        loadShift.setBegin(lsj.begin);
        loadShift.setEnd(lsj.end);
        loadShift.setStopAt(lsj.stopAt);
        MonthHolder monthHolder = new MonthHolder();
        monthHolder.months = lsj.months;
        loadShift.setMonths(monthHolder);
        IntHolder intHolder = new IntHolder();
        intHolder.ints = lsj.days;
        loadShift.setDays(intHolder);
        return loadShift;
    }

    public static List<EVCharge> createEVChargeList(List<EVChargeJson> jsons) {
        ArrayList<EVCharge> entityList = new ArrayList<>();
        if (!(null == jsons)){
            for (EVChargeJson json : jsons) {
                EVCharge entity = createEVCharge(json);
                entityList.add(entity);
            }
        }
        return entityList;
    }

    public static EVCharge createEVCharge(EVChargeJson evcj) {
        EVCharge evCharge = new EVCharge();
        evCharge.setName(evcj.name);
        evCharge.setBegin(evcj.begin);
        evCharge.setEnd(evcj.end);
        evCharge.setDraw(evcj.draw);
        MonthHolder monthHolder = new MonthHolder();
        monthHolder.months = evcj.months;
        evCharge.setMonths(monthHolder);
        IntHolder intHolder = new IntHolder();
        intHolder.ints = evcj.days;
        evCharge.setDays(intHolder);
        return evCharge;
    }

    public static List<HWSchedule> createHWScheduleList(List<HWScheduleJson> jsons) {
        ArrayList<HWSchedule> entityList = new ArrayList<>();
        if (!(null == jsons)){
            for (HWScheduleJson json : jsons) {
                HWSchedule entity = createHWSchedule(json);
                entityList.add(entity);
            }
        }
        return entityList;
    }

    public static HWSchedule createHWSchedule(HWScheduleJson hwsj) {
        HWSchedule hwSchedule = new HWSchedule();
        hwSchedule.setName(hwsj.name);
        hwSchedule.setBegin(hwsj.begin);
        hwSchedule.setEnd(hwsj.end);
        MonthHolder monthHolder = new MonthHolder();
        monthHolder.months = hwsj.months;
        hwSchedule.setMonths(monthHolder);
        IntHolder intHolder = new IntHolder();
        intHolder.ints = hwsj.days;
        hwSchedule.setDays(intHolder);
        return hwSchedule;
    }

    public static HWDivert createHWDivert(HWDivertJson hwdj) {
        HWDivert hwDivert = new HWDivert();
        hwDivert.setActive(hwdj.active);
        return hwDivert;
    }

    public static EVDivert createEVDivert(EVDivertJson evdj) {
        EVDivert evDivert =  new EVDivert();
        evDivert.setName(evdj.name);
        evDivert.setActive(evdj.active);
        evDivert.setEv1st(evdj.ev1st);
        evDivert.setBegin(evdj.begin);
        evDivert.setEnd(evdj.end);
        evDivert.setDailyMax(evdj.dailyMax);
        MonthHolder monthHolder = new MonthHolder();
        monthHolder.months = evdj.months;
        evDivert.setMonths(monthHolder);
        IntHolder intHolder = new IntHolder();
        intHolder.ints = evdj.days;
        evDivert.setDays(intHolder);
        return evDivert;
    }

    public static String createSingleScenarioJsonString(ScenarioJsonFile sjf) {
        Type type = new TypeToken<ScenarioJsonFile>(){}.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(sjf, type);
    }

    public static ScenarioJsonFile createSingleScenarioJson(
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

        ScenarioJsonFile sjf = new ScenarioJsonFile();
        sjf.name = scenario.getScenarioName();
        sjf.inverters = createInverterListJson(inverters);
        sjf.batteries = createBatteryListJson(batteries);
        sjf.panels = createPanelListJson(panels);
        sjf.hwSystem = createHWSystemJson(hwSystem);
        sjf.loadProfile = createLoadProfileJson(loadProfile);
        sjf.loadShifts = createLoadShiftJson(loadShifts);
        sjf.evCharges = createEVChargeJson(evCharges);
        sjf.hwSchedules = createHWScheduleJson(hwSchedules);
        sjf.hwDivert = createHWDivertJson(hwDivert);
        sjf.evDivert = createEVDivertJson(evDivert);
        return sjf;
    }

    private static EVDivertJson createEVDivertJson(EVDivert evDivert) {
        EVDivertJson evdj = new EVDivertJson();
        if (!(null == evDivert)) {
            evdj.name = evDivert.getName();
            evdj.active = evDivert.isActive();
            evdj.ev1st = evDivert.isEv1st();
            evdj.begin = evDivert.getBegin();
            evdj.end = evDivert.getEnd();
            evdj.dailyMax = evDivert.getDailyMax();
            evdj.months = (ArrayList<Integer>) evDivert.getMonths().months;
            evdj.days = (ArrayList<Integer>) evDivert.getDays().ints;
        }
        return evdj;
    }

    private static HWDivertJson createHWDivertJson(HWDivert hwDivert) {
        HWDivertJson hwdj = new HWDivertJson();
        if (!(null == hwDivert)) hwdj.active = hwDivert.isActive();
        else hwdj.active = false;
        return hwdj;
    }

    private static ArrayList<HWScheduleJson> createHWScheduleJson(List<HWSchedule> hwSchedules) {
        ArrayList<HWScheduleJson> hwssj = new ArrayList<>();
        if (!(null == hwSchedules)){
            for (HWSchedule hws : hwSchedules) {
                HWScheduleJson hwsj = new HWScheduleJson();
                hwsj.name = hws.getName();
                hwsj.begin = hws.getBegin();
                hwsj.end = hws.getEnd();
                hwsj.months = (ArrayList<Integer>) hws.getMonths().months;
                hwsj.days = (ArrayList<Integer>) hws.getDays().ints;
                hwssj.add(hwsj);
            }
        }
        return hwssj;
    }

    private static ArrayList<EVChargeJson> createEVChargeJson(List<EVCharge> evCharges) {
        ArrayList<EVChargeJson> evcsj = new ArrayList<>();
        if (!(null == evCharges)){
            for (EVCharge evc : evCharges) {
                EVChargeJson evcj = new EVChargeJson();
                evcj.name = evc.getName();
                evcj.begin = evc.getBegin();
                evcj.end = evc.getEnd();
                evcj.draw = evc.getDraw();
                evcj.months = (ArrayList<Integer>) evc.getMonths().months;
                evcj.days = (ArrayList<Integer>) evc.getDays().ints;
                evcsj.add(evcj);
            }
        }
        return evcsj;
    }

    private static ArrayList<LoadShiftJson> createLoadShiftJson(List<LoadShift> loadShifts) {
        ArrayList<LoadShiftJson> lssj = new ArrayList<>();
        if (!(null == loadShifts)) {
            for (LoadShift ls : loadShifts) {
                LoadShiftJson lsj = new LoadShiftJson();
                lsj.name = ls.getName();
                lsj.begin = ls.getBegin();
                lsj.end = ls.getEnd();
                lsj.stopAt = ls.getStopAt();
                lsj.months = (ArrayList<Integer>) ls.getMonths().months;
                lsj.days = (ArrayList<Integer>) ls.getDays().ints;
                lssj.add(lsj);
            }
        }
        return lssj;
    }

    public static LoadProfileJson createLoadProfileJson(LoadProfile loadProfile) {
        LoadProfileJson lpj = new LoadProfileJson();
        if (!(null == loadProfile)){
            lpj.annualUsage = loadProfile.getAnnualUsage();
            lpj.hourlyBaseLoad = loadProfile.getHourlyBaseLoad();
            lpj.gridImportMax = loadProfile.getGridImportMax();
            lpj.gridExportMax = loadProfile.getGridExportMax();
            lpj.hourlyDistribution = (ArrayList<Double>) loadProfile.getHourlyDist().dist;
            DOWDistribution dowd = new DOWDistribution();
            dowd.sun = loadProfile.getDowDist().dowDist.get(0);
            dowd.mon = loadProfile.getDowDist().dowDist.get(1);
            dowd.tue = loadProfile.getDowDist().dowDist.get(2);
            dowd.wed = loadProfile.getDowDist().dowDist.get(3);
            dowd.thu = loadProfile.getDowDist().dowDist.get(4);
            dowd.fri = loadProfile.getDowDist().dowDist.get(5);
            dowd.sat = loadProfile.getDowDist().dowDist.get(6);
            lpj.dayOfWeekDistribution = dowd;
            MonthlyDistribution md = new MonthlyDistribution();
            md.jan = loadProfile.getMonthlyDist().monthlyDist.get(0);
            md.feb = loadProfile.getMonthlyDist().monthlyDist.get(1);
            md.mar = loadProfile.getMonthlyDist().monthlyDist.get(2);
            md.apr = loadProfile.getMonthlyDist().monthlyDist.get(3);
            md.may = loadProfile.getMonthlyDist().monthlyDist.get(4);
            md.jun = loadProfile.getMonthlyDist().monthlyDist.get(5);
            md.jul = loadProfile.getMonthlyDist().monthlyDist.get(6);
            md.aug = loadProfile.getMonthlyDist().monthlyDist.get(7);
            md.sep = loadProfile.getMonthlyDist().monthlyDist.get(8);
            md.oct = loadProfile.getMonthlyDist().monthlyDist.get(9);
            md.nov = loadProfile.getMonthlyDist().monthlyDist.get(10);
            md.dec = loadProfile.getMonthlyDist().monthlyDist.get(11);
            lpj.monthlyDistribution = md;
        }
        return lpj;
    }

    private static HWSystemJson createHWSystemJson(HWSystem hwSystem) {
        HWSystemJson hwsj = new HWSystemJson();
        if (!(null == hwSystem)) {
            hwsj.hwCapacity = hwSystem.getHwCapacity();
            hwsj.hwUsage = hwSystem.getHwUsage();
            hwsj.hwIntake = hwSystem.getHwIntake();
            hwsj.hwTarget = hwSystem.getHwTarget();
            hwsj.hwLoss = hwSystem.getHwLoss();
            hwsj.hwRate = hwSystem.getHwRate();
            hwsj.hwUse = hwSystem.getHwUse().getUsage();
        }
        return hwsj;
    }

    private static ArrayList<PanelJson> createPanelListJson(List<Panel> panels) {
        ArrayList<PanelJson> pjs = new ArrayList<>();
        if (!(null == panels)){
            for (Panel p : panels){
                PanelJson pj = new PanelJson();
                pj.panelCount = p.getPanelCount();
                pj.panelkWp = p.getPanelkWp();
                pj.azimuth = p.getAzimuth();
                pj.slope = p.getSlope();
                pj.latitude = p.getLatitude();
                pj.longitude = p.getLongitude();
                pj.inverter = p.getInverter();
                pj.mppt = p.getMppt();
                pjs.add(pj);
            }
        }
        return pjs;
    }

    private static ArrayList<BatteryJson> createBatteryListJson(List<Battery> batteries) {
        ArrayList<BatteryJson> bjs = new ArrayList<>();
        if (!(null == batteries)) {
            for (Battery b : batteries) {
                BatteryJson bj = new BatteryJson();
                bj.batterySize = b.getBatterySize();
                bj.dischargeStop = b.getDischargeStop();
                ChargeModelJson cmj = new ChargeModelJson();
                cmj.percent0 = b.getChargeModel().percent0;
                cmj.percent12 = b.getChargeModel().percent12;
                cmj.percent90 = b.getChargeModel().percent90;
                cmj.percent100 = b.getChargeModel().percent100;
                bj.chargeModel = cmj;
                bj.maxDischarge = b.getMaxDischarge();
                bj.maxCharge = b.getMaxCharge();
                bj.storageLoss = b.getStorageLoss();
                bj.inverter = b.getInverter();
                bjs.add(bj);
            }
        }
        return bjs;
    }

    public static ArrayList<InverterJson> createInverterListJson(List<Inverter> inverters) {
        ArrayList<InverterJson> ijs = new ArrayList<>();
        if (!(null == inverters)) {
            for (Inverter i : inverters) {
                InverterJson ij = createInverterJson(i);
                ijs.add(ij);
            }
        }
        return ijs;
    }

    @NonNull
    public static InverterJson createInverterJson(Inverter i) {
        InverterJson ij = new InverterJson();
        ij.name = i.getInverterName();
        ij.minExcess = i.getMinExcess();
        ij.maxInverterLoad = i.getMaxInverterLoad();
        ij.mPPTCount = i.getMpptCount();
        ij.ac2dcLoss = i.getAc2dcLoss();
        ij.dc2acLoss = i.getDc2acLoss();
        ij.dc2dcLoss = i.getDc2dcLoss();
        return ij;
    }

    public static String createScenarioList(List<ScenarioComponents> scenarios) {
        ArrayList<ScenarioJsonFile> sjfs = new ArrayList<>();
        for (ScenarioComponents sc : scenarios){
            sjfs.add(createSingleScenarioJson(sc.scenario, sc.inverters, sc.batteries, sc.panels,
                    sc.hwSystem, sc.loadProfile, sc.loadShifts, sc.evCharges, sc.hwSchedules,
                    sc.hwDivert, sc.evDivert));
        }

        Type type = new TypeToken<List<ScenarioJsonFile>>(){}.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(sjfs, type);
    }

    public static List<ScenarioComponents> createScenarioComponentList(List<ScenarioJsonFile> sjfs) {
        ArrayList<ScenarioComponents> scenarioComponents = new ArrayList<>();
        for (ScenarioJsonFile sjf : sjfs){
            ScenarioComponents sc = new ScenarioComponents(
                    createScenario(sjf),
                    createInverterList(sjf.inverters),
                    createBatteryList(sjf.batteries),
                    createPanelList(sjf.panels),
                    createHWSystem(sjf.hwSystem),
                    createLoadProfile(sjf.loadProfile),
                    createLoadShiftList(sjf.loadShifts),
                    createEVChargeList(sjf.evCharges),
                    createHWScheduleList(sjf.hwSchedules),
                    createHWDivert(sjf.hwDivert),
                    createEVDivert(sjf.evDivert));
            scenarioComponents.add(sc);
        }
        return scenarioComponents;
    }
}
