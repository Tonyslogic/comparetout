package com.tfcode.comparetout;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.lookup.RateLookup;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.costings.Costings;
import com.tfcode.comparetout.model.costings.SubTotals;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;

import java.util.List;

public class CostingWorker extends Worker {

    private final ToutcRepository mToutcRepository;

    public CostingWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mToutcRepository = new ToutcRepository((Application) context);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Find distinct scenarios
        List<Long> scenarioIDs = mToutcRepository.getAllScenariosThatNeedCosting();
        System.out.println("Found " + scenarioIDs.size() + " scenarios that need costing");
        // Load PricePlans
        List<PricePlan> plans = mToutcRepository.getAllPricePlansNow();
        System.out.println("Found " + plans.size() + " plans to calculate costs for");
        // For each scenario -> load; For each priceplan -> apply costs
        for (long scenarioID : scenarioIDs) {
            // Get the simulation output
            List<ScenarioSimulationData> scenarioData = mToutcRepository.getSimulationDataForScenario(scenarioID);
            for (PricePlan pp : plans) {
                RateLookup lookup = new RateLookup(
                        mToutcRepository.getAllDayRatesForPricePlanID(pp.getId()));
                Costings costing = new Costings();
                costing.setScenarioID(scenarioID);
                costing.setPricePlanID(pp.getId());
                double buy = 0D;
                double sell = 0D;
                SubTotals subTotals = new SubTotals();
                for (ScenarioSimulationData row : scenarioData) {
                    double price = lookup.getRate(row.getDate(), row.getMinuteOfDay(), row.getDayOfWeek());
                    double rowBuy = price * row.getBuy();
                    buy += rowBuy;
                    sell += pp.getFeed() * row.getFeed();
                    subTotals.addToPrice(price, rowBuy);
                }
                costing.setBuy(buy);
                costing.setSell(sell);
                costing.setSubTotals(subTotals);
                // store in comparison table
                System.out.println("Storing " + costing);
                mToutcRepository.saveCosting(costing);
            }
        }

        return Result.success();
    }
}


