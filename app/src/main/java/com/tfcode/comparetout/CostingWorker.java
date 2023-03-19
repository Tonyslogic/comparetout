package com.tfcode.comparetout;

import static com.tfcode.comparetout.MainActivity.CHANNEL_ID;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
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

        // NOTIFICATION SETUP
        int notificationId = 1;
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID);
        builder.setContentTitle("Calculating costs")
                .setContentText("Calculation in progress")
                .setSmallIcon(R.drawable.house)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setTimeoutAfter(20000);
        int PROGRESS_MAX = 100;
        int PROGRESS_CURRENT = 0;

        // Load PricePlans
        List<PricePlan> plans = mToutcRepository.getAllPricePlansNow();
        System.out.println("Found " + plans.size() + " plans to calculate costs for");
        int PROGRESS_CHUNK = PROGRESS_MAX;
        if ((scenarioIDs.size() > 0) && (plans.size() > 0)) {
            PROGRESS_CHUNK = PROGRESS_MAX / (scenarioIDs.size() * plans.size());
            // Issue the initial notification with zero progress
            builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
            notificationManager.notify(notificationId, builder.build());
        }

        // For each scenario -> load; For each priceplan -> apply costs
        for (long scenarioID : scenarioIDs) {
            // Get the simulation output
            List<ScenarioSimulationData> scenarioData = mToutcRepository.getSimulationDataForScenario(scenarioID);
            for (PricePlan pp : plans) {
                RateLookup lookup = new RateLookup(
                        mToutcRepository.getAllDayRatesForPricePlanID(pp.getId()));
                Costings costing = new Costings();
                costing.setScenarioID(scenarioID);
                costing.setScenarioName(mToutcRepository.getScenarioForID(scenarioID).getScenarioName());
                costing.setPricePlanID(pp.getId());
                costing.setFullPlanName(pp.getSupplier() + ":" + pp.getPlanName());
                double buy = 0D;
                double sell = 0D;
                double nett = 0D;
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
                int days = 365; // TODO look at the biggest & smallest dates in the simdata
                nett = buy - sell + pp.getStandingCharges() * 100 * (days/365) - pp.getSignUpBonus() * 100;
                costing.setNett(nett);
                // store in comparison table
                System.out.println("Storing " + costing);
                mToutcRepository.saveCosting(costing);
                // NOTIFICATION PROGRESS
                PROGRESS_CURRENT += PROGRESS_CHUNK;
                builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
                notificationManager.notify(notificationId, builder.build());
            }
        }

        if ((scenarioIDs.size() > 0) && (plans.size() > 0)) {
            // NOTIFICATION COMPLETE
            builder.setContentText("Calculation complete")
                    .setProgress(0, 0, false);
            notificationManager.notify(notificationId, builder.build());
        }
        return Result.success();
    }
}


