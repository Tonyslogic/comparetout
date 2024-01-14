/*
 * Copyright (c) 2023-2024. Tony Finnerty
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

package com.tfcode.comparetout;

import static com.tfcode.comparetout.MainActivity.CHANNEL_ID;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.util.RateLookup;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.costings.Costings;
import com.tfcode.comparetout.model.costings.SubTotals;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CostingWorker extends Worker {

    private final ToutcRepository mToutcRepository;
    private final Map<Long, RateLookup> mLookups;

    public CostingWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mToutcRepository = new ToutcRepository((Application) context);
        mLookups = new HashMap<>();
    }

    @NonNull
    @Override
    public Result doWork() {
        // Find distinct scenarios
        mToutcRepository.pruneCostings();
        List<Long> scenarioIDs = mToutcRepository.getAllScenariosThatMayNeedCosting();

        try {
            if (scenarioIDs.size() > 0) {
                // NOTIFICATION SETUP
                int notificationId = 1;
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
                NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID);
                builder.setContentTitle("Calculating costs")
                        .setContentText("Calculation in progress")
                        .setSmallIcon(R.drawable.housetick)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setTimeoutAfter(30000)
                        .setSilent(true);
                int PROGRESS_MAX = 100;
                int PROGRESS_CURRENT = 0;

                // Load PricePlans
                List<PricePlan> plans = mToutcRepository.getAllPricePlansNow();
                int PROGRESS_CHUNK = PROGRESS_MAX;
                if ((scenarioIDs.size() > 0) && (plans.size() > 0)) {
                    PROGRESS_CHUNK = PROGRESS_MAX / (scenarioIDs.size() * plans.size());
                    // Issue the initial notification with zero progress
                    builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
                    notificationManager.notify(notificationId, builder.build());
                }

                // For each scenario -> load; For each price plan -> apply costs
                for (long scenarioID : scenarioIDs) {
                    // Get the simulation output
                    builder.setContentText("Loading data");
                    notificationManager.notify(notificationId, builder.build());
                    List<ScenarioSimulationData> scenarioData = mToutcRepository.getSimulationDataForScenario(scenarioID);
                    double gridExportMax = mToutcRepository.getGridExportMaxForScenario(scenarioID);
                    if (scenarioData.size() > 0) {
                        long notifyTime = System.nanoTime();
                        for (PricePlan pp : plans) {
                            // Confirm the need for costing
                            if (mToutcRepository.costingExists(scenarioID, pp.getPricePlanIndex()))
                                continue;
                            builder.setContentText(pp.getPlanName());
                            if (System.nanoTime() - notifyTime > 1e+9){
                                notifyTime = System.nanoTime();
                                notificationManager.notify(notificationId, builder.build());
                            }
                            RateLookup lookup = mLookups.get(pp.getPricePlanIndex());
                            if (null == lookup) {
                                lookup = new RateLookup(
                                        mToutcRepository.getAllDayRatesForPricePlanID(pp.getPricePlanIndex()));
                                mLookups.put(pp.getPricePlanIndex(), lookup);
                            }
                            Costings costing = new Costings();
                            costing.setScenarioID(scenarioID);
                            Scenario scenario = mToutcRepository.getScenarioForID(scenarioID);
                            costing.setScenarioName(scenario.getScenarioName());
                            costing.setPricePlanID(pp.getPricePlanIndex());
                            costing.setFullPlanName(pp.getSupplier() + ":" + pp.getPlanName());
                            double buy = 0D;
                            double sell = 0D;
                            double net;
                            SubTotals subTotals = new SubTotals();
                            for (ScenarioSimulationData row : scenarioData) {
                                double price = lookup.getRate(row.getDayOf2001(), row.getMinuteOfDay(), row.getDayOfWeek());
                                double rowBuy = price * row.getBuy();
                                buy += rowBuy;
                                sell += pp.getFeed() * row.getFeed();
                                subTotals.addToPrice(price, row.getBuy()); // This is the number of units
                            }
                            costing.setBuy(buy);
                            costing.setSell(sell);
                            costing.setSubTotals(subTotals);
                            double days = 365; // TODO look at the biggest & smallest dates in the sim data
                            if (pp.isDeemedExport() && scenario.isHasInverters()) {
                                sell = gridExportMax * 0.8148 * days * pp.getFeed();
                                costing.setSell(sell);
                            }
                            net = ((buy - sell) + (pp.getStandingCharges() * 100 * (days / 365))) - (pp.getSignUpBonus() * 100);
                            costing.setNet(net);
                            // store in comparison table
                            builder.setContentText("Saving data");
                            if (System.nanoTime() - notifyTime > 1e+9) {
                                notifyTime = System.nanoTime();
                                notificationManager.notify(notificationId, builder.build());
                            }
                            mToutcRepository.saveCosting(costing);
                            // NOTIFICATION PROGRESS
                            PROGRESS_CURRENT += PROGRESS_CHUNK;
                            builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
                            builder.setContentText("Data saved");
                            if (System.nanoTime() - notifyTime > 1e+9) {
                                notifyTime = System.nanoTime();
                                notificationManager.notify(notificationId, builder.build());
                            }
                        }
                        long endTime = System.nanoTime();
                    } else {
                        builder.setContentText("Missing panel data");
                        notificationManager.notify(notificationId, builder.build());
                        PROGRESS_CURRENT += PROGRESS_CHUNK;
                        builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
                        notificationManager.notify(notificationId, builder.build());
                    }
                }

                // NOTIFICATION COMPLETE
                builder.setContentText("Calculation complete")
                        .setProgress(0, 0, false);
                notificationManager.notify(notificationId, builder.build());
            }
        }
        catch (Exception e) {
            System.out.println("!!!!!!!!!!!!!!!!!!! CostingWorker has crashed, marking as failure !!!!!!!!!!!!!!!!!!!!!");
            e.printStackTrace();
            System.out.println("!!!!!!!!!!!!!!!!!!! CostingWorker has crashed, marking as failure !!!!!!!!!!!!!!!!!!!!!");
            return Result.failure();
        }
        return Result.success();
    }
}


