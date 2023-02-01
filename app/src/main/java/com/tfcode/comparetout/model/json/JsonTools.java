package com.tfcode.comparetout.model.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.model.DayRate;
import com.tfcode.comparetout.model.DoubleHolder;
import com.tfcode.comparetout.model.IntHolder;
import com.tfcode.comparetout.model.PricePlan;
import com.tfcode.comparetout.priceplan.DayRateJson;
import com.tfcode.comparetout.priceplan.PricePlanJsonFile;

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
}
