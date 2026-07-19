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

/** Price-plan JSON conversions, moved verbatim from JsonTools (mega-refactor C10). */
public class PricePlanJsonTools {

    public static PricePlan createPricePlan(PricePlanJsonFile pp) {
        PricePlan p = new PricePlan();
        p.setPlanName(pp.plan);
        p.setSupplier(pp.supplier);
        // Scalars are null-guarded so a terms-only dynamic file (which may carry
        // little beyond Supplier/Plan/Dynamic) imports without unboxing NPEs.
        p.setFeed(null == pp.feed ? 0d : pp.feed);
        p.setStandingCharges(null == pp.standingCharges ? 0d : pp.standingCharges);
        p.setSignUpBonus(null == pp.bonus ? 0d : pp.bonus);
        if (!(null == pp.active)) p.setActive(pp.active);
        if (!(null == pp.lastUpdate)) p.setLastUpdate(pp.lastUpdate);
        if (!(null == pp.reference)) p.setReference(pp.reference);
        p.setDeemedExport(null == pp.deemedExport ? false : pp.deemedExport);
        if (!(null == pp.location)) p.setLocation(pp.location);
        if (!(null == pp.dynamic)) {
            DynamicTerms dt = new DynamicTerms();
            dt.setMarket(pp.dynamic.market);
            dt.setYear(pp.dynamic.year);
            dt.setPeriodStartMonth(pp.dynamic.periodStartMonth);
            dt.setPeriodStartDay(pp.dynamic.periodStartDay);
            dt.setAutoWindow(pp.dynamic.autoWindow);
            dt.setMultiplier(pp.dynamic.multiplier);
            dt.setAdder(pp.dynamic.adder);
            dt.setCap(pp.dynamic.cap);
            dt.setFloor(pp.dynamic.floor);
            dt.setFeedMultiplier(pp.dynamic.feedMultiplier);
            dt.setFeedAdder(pp.dynamic.feedAdder);
            dt.setSourceRef(pp.dynamic.sourceRef);
            p.setDynamicTerms(dt);
        }
        Restrictions restrictions = new Restrictions();
        RestrictionJson rj = pp.restrictions;
        if (!(null == rj)) {
            restrictions.setActive(rj.active);
            ArrayList<Restriction> rjs = new ArrayList<>();
            for (RestrictionEntryJson rje : rj.restrictionEntries) {
                Restriction r = new Restriction();
                r.addEntry(Restriction.RestrictionType.fromValue(rje.period), rje.scope, rje.limit, rje.excessCost);
                rjs.add(r);
            }
            restrictions.setRestrictions(rjs);
        }
        p.setRestrictions(restrictions);
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
        MinuteRateRange mrr = new MinuteRateRange();
        if (!(null == drj.minuteRange)) {
            for (MinuteRangeCostJson mrcj : drj.minuteRange) {
                mrr.add(mrcj.startMinute, mrcj.endMinute, mrcj.cost);
            }
        }
        else {
           mrr = MinuteRateRange.fromHours(dh);
        }
        dr.setMinuteRateRange(mrr);
        dr.setRateType("sell".equalsIgnoreCase(drj.rateType)
                ? DayRate.RATE_SELL : DayRate.RATE_BUY);
        if (drj.dbID == null) dr.setDayRateIndex(0L);
        else dr.setDayRateIndex(drj.dbID);
        return dr;
    }

    private static DynamicTermsJson createDynamicTermsJson(DynamicTerms dt) {
        if (null == dt) return null;
        DynamicTermsJson dj = new DynamicTermsJson();
        dj.market = dt.getMarket();
        dj.year = dt.getYear();
        dj.periodStartMonth = dt.getPeriodStartMonth();
        dj.periodStartDay = dt.getPeriodStartDay();
        dj.autoWindow = dt.getAutoWindow();
        dj.multiplier = dt.getMultiplier();
        dj.adder = dt.getAdder();
        dj.cap = dt.getCap();
        dj.floor = dt.getFloor();
        dj.feedMultiplier = dt.getFeedMultiplier();
        dj.feedAdder = dt.getFeedAdder();
        dj.sourceRef = dt.getSourceRef();
        return dj;
    }

    public static String createPricePlanJson(Map<PricePlan, List<DayRate>> pricePlans) {
        ArrayList<PricePlanJsonFile> ppList = new ArrayList<>();
            for (Map.Entry<PricePlan, List<DayRate>> entry : pricePlans.entrySet()) {
                ArrayList<DayRateJson> dayRateJsons = new ArrayList<>();
                // Dynamic plans export terms-only: their rates are a derived artefact,
                // regenerated locally, and market-derived prices must not be redistributed.
                if (!entry.getKey().isDynamic()) for (DayRate dr : entry.getValue()){
                    DayRateJson drj = new DayRateJson();
                    drj.startDate = dr.getStartDate();
                    drj.endDate = dr.getEndDate();
                    drj.days = (ArrayList<Integer>) dr.getDays().ints;
                    drj.hours = (ArrayList<Double>) dr.getHours().doubles;
                    drj.minuteRange = new ArrayList<>();
                    if (!(null == dr.getMinuteRateRange()) && !dr.getMinuteRateRange().getRates().isEmpty()) {
                        for (RangeRate mrr : dr.getMinuteRateRange().getRates()) {
                            drj.minuteRange.add(new MinuteRangeCostJson(mrr.getBegin(), mrr.getEnd(),mrr.getPrice()));
                        }
                    }
                    else {
                        // Same hours-fallback as the single-plan exporter so a
                        // bulk export of a plan with no MinuteRateRange survives
                        // round-tripping back into the wizard.
                        MinuteRateRange synth = MinuteRateRange.fromHours(dr.getHours());
                        for (RangeRate rr : synth.getRates()) {
                            drj.minuteRange.add(new MinuteRangeCostJson(rr.getBegin(), rr.getEnd(), rr.getPrice()));
                        }
                    }
                    // Absent for BUY rates, so pre-v16 exports are byte-identical.
                    drj.rateType = (dr.getRateType() == DayRate.RATE_SELL) ? "sell" : null;
                    drj.dbID = dr.getDayRateIndex();
                    dayRateJsons.add(drj);
                }
                PricePlanJsonFile ppj = new PricePlanJsonFile();
                ppj.rates = entry.getKey().isDynamic() ? null : dayRateJsons;
                ppj.dynamic = createDynamicTermsJson(entry.getKey().getDynamicTerms());
                ppj.active = entry.getKey().isActive();
                ppj.plan = entry.getKey().getPlanName();
                ppj.bonus = entry.getKey().getSignUpBonus();
                ppj.feed = entry.getKey().getFeed();
                ppj.lastUpdate = entry.getKey().getLastUpdate();
                ppj.standingCharges = entry.getKey().getStandingCharges();
                ppj.reference = entry.getKey().getReference();
                ppj.supplier = entry.getKey().getSupplier();
                ppj.deemedExport = entry.getKey().isDeemedExport();
                ppj.location = entry.getKey().getLocation();
                RestrictionJson restrictions = new RestrictionJson();
                restrictions.active = entry.getKey().getRestrictions().isActive();
                restrictions.restrictionEntries = new ArrayList<>();
                for (Restriction r : entry.getKey().getRestrictions().getRestrictions()) {
                    RestrictionEntryJson rje = new RestrictionEntryJson();
                    rje.period = r.getPeriodicity().getValue();
                    Map<String, Pair<Integer, Double>> restrictionEntries = r.getRestrictionEntries();
                    for (Map.Entry<String, Pair<Integer, Double>> restrictionEntry : restrictionEntries.entrySet()) {
                        rje.scope = restrictionEntry.getKey();
                        rje.limit = restrictionEntry.getValue().first;
                        rje.excessCost = restrictionEntry.getValue().second;
                    }
                    restrictions.restrictionEntries.add(rje);
                }
                ppj.restrictions = restrictions;
                ppList.add(ppj);
            }
        Type type = new TypeToken<List<PricePlanJsonFile>>(){}.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(ppList,type);
    }

    public static String createSinglePricePlanJsonObject(PricePlan pp, List<DayRate> rates) {
        ArrayList<DayRateJson> dayRateJsons = new ArrayList<>();
        // Dynamic plans export terms-only: their rates are a derived artefact,
        // regenerated locally, and market-derived prices must not be redistributed.
        if (!pp.isDynamic()) for (DayRate dr : rates) {
            DayRateJson drj = new DayRateJson();
            drj.startDate = dr.getStartDate();
            drj.endDate = dr.getEndDate();
            drj.days = (ArrayList<Integer>) dr.getDays().ints;
            drj.hours = (ArrayList<Double>) dr.getHours().doubles;
            drj.minuteRange = new ArrayList<>();
            if (!(null == dr.getMinuteRateRange()) && !dr.getMinuteRateRange().getRates().isEmpty()) {
                for (RangeRate mrr : dr.getMinuteRateRange().getRates()) {
                    drj.minuteRange.add(new MinuteRangeCostJson(mrr.getBegin(), mrr.getEnd(), mrr.getPrice()));
                }
            }
            else {
                // Synthesize a minuteRange from the hourly snapshot. Use
                // MinuteRateRange.fromHours so adjacent same-price hours are
                // merged and every range stays within [0, 1440] — the earlier
                // hand-rolled loop emitted 25 raw hour-buckets, the last one
                // running past midnight to 1500.
                MinuteRateRange synth = MinuteRateRange.fromHours(dr.getHours());
                for (RangeRate rr : synth.getRates()) {
                    drj.minuteRange.add(new MinuteRangeCostJson(rr.getBegin(), rr.getEnd(), rr.getPrice()));
                }
            }
            // Absent for BUY rates, so pre-v16 exports are byte-identical.
            drj.rateType = (dr.getRateType() == DayRate.RATE_SELL) ? "sell" : null;
            drj.dbID = dr.getDayRateIndex();
            dayRateJsons.add(drj);
        }
        PricePlanJsonFile ppj = new PricePlanJsonFile();
        ppj.rates = pp.isDynamic() ? null : dayRateJsons;
        ppj.dynamic = createDynamicTermsJson(pp.getDynamicTerms());
        ppj.active = pp.isActive();
        ppj.plan = pp.getPlanName();
        ppj.bonus = pp.getSignUpBonus();
        ppj.feed = pp.getFeed();
        ppj.lastUpdate = pp.getLastUpdate();
        ppj.standingCharges = pp.getStandingCharges();
        ppj.reference = pp.getReference();
        ppj.supplier = pp.getSupplier();
        ppj.deemedExport = pp.isDeemedExport();
        ppj.location = pp.getLocation();
        RestrictionJson restrictions = new RestrictionJson();
        if (!(null == pp.getRestrictions())) {
            restrictions.active = pp.getRestrictions().isActive();
            restrictions.restrictionEntries = new ArrayList<>();
            for (Restriction r : pp.getRestrictions().getRestrictions()) {
                Map<String, Pair<Integer, Double>> restrictionEntries = r.getRestrictionEntries();
                for (Map.Entry<String, Pair<Integer, Double>> restrictionEntry : restrictionEntries.entrySet()) {
                    RestrictionEntryJson rje = new RestrictionEntryJson();
                    rje.period = r.getPeriodicity().getValue();
                    rje.scope = restrictionEntry.getKey();
                    rje.limit = restrictionEntry.getValue().first;
                    rje.excessCost = restrictionEntry.getValue().second;
                    restrictions.restrictionEntries.add(rje);
                }
            }
        }
        ppj.restrictions = restrictions;

        Type type = new TypeToken<PricePlanJsonFile>(){}.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(ppj, type);
    }
}
