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
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.costings.Costings;
import com.tfcode.comparetout.model.costings.SubTotals;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;
import com.tfcode.comparetout.util.RateLookup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CostingWorker extends Worker {

    private final ToutcRepository mToutcRepository;
    private final Map<Long, RateLookup> mLookups;
    private final Context mContext;

    public CostingWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mToutcRepository = new ToutcRepository((Application) context);
        mLookups = new HashMap<>();
        mContext = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        // Find distinct scenarios
        mToutcRepository.pruneCostings();
        List<Long> scenarioIDs = mToutcRepository.getAllScenariosThatMayNeedCosting();

        Context context = getApplicationContext();
        String title = context.getString(R.string.cost_notification_title); //"Calculating costs"
        String text = context.getString(R.string.cost_notification_text); //"Calculation in progress"

        // NOTIFICATION SETUP
        int notificationId = 1;
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID);
        builder.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.housetick)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setTimeoutAfter(30000)
                .setSilent(true);

        try {
            if (!scenarioIDs.isEmpty()) {

                int PROGRESS_MAX = 100;
                int PROGRESS_CURRENT = 0;

                // Load PricePlans
                List<PricePlan> plans = mToutcRepository.getAllPricePlansNow();
                int PROGRESS_CHUNK = PROGRESS_MAX;
                if ((!scenarioIDs.isEmpty()) && (!plans.isEmpty())) {
                    PROGRESS_CHUNK = PROGRESS_MAX / (scenarioIDs.size() * plans.size());
                    // Issue the initial notification with zero progress
                    builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
                    sendNotification(notificationManager, notificationId, builder);
                }

                // For each scenario -> load; For each price plan -> apply costs
                for (long scenarioID : scenarioIDs) {
                    Scenario scenario = mToutcRepository.getScenarioForID(scenarioID);
                    // Get the simulation output
                    builder.setContentText("Loading data: " + scenario.getScenarioName());
                    sendNotification(notificationManager, notificationId, builder);
                    List<ScenarioSimulationData> scenarioData = mToutcRepository.getSimulationDataForScenario(scenarioID);
                    double gridExportMax = mToutcRepository.getGridExportMaxForScenario(scenarioID);
                    if (!scenarioData.isEmpty()) {
                        long notifyTime = System.nanoTime();
                        for (PricePlan pp : plans) {
                            // Confirm the need for costing
                            if (mToutcRepository.costingExists(scenarioID, pp.getPricePlanIndex()))
                                continue;
                            builder.setContentText(pp.getPlanName());
                            if (System.nanoTime() - notifyTime > 1e+9) {
                                notifyTime = System.nanoTime();
                                sendNotification(notificationManager, notificationId, builder);
                            }
                            RateLookup lookup = mLookups.get(pp.getPricePlanIndex());
                            if (null == lookup) {
                                lookup = new RateLookup(pp,
                                        mToutcRepository.getAllDayRatesForPricePlanID(pp.getPricePlanIndex()));
                                mLookups.put(pp.getPricePlanIndex(), lookup);
                            }
                            Costings costing = new Costings();
                            costing.setScenarioID(scenarioID);
                            costing.setScenarioName(scenario.getScenarioName());
                            costing.setPricePlanID(pp.getPricePlanIndex());
                            costing.setFullPlanName(pp.getSupplier() + ":" + pp.getPlanName());
                            double buy = 0D;
                            double sell = 0D;
                            double net;
                            SubTotals subTotals = new SubTotals();
                            for (ScenarioSimulationData row : scenarioData) {
                                double price = lookup.getRate(row.getDayOf2001(), row.getMinuteOfDay(),
                                        (row.getDayOfWeek() == 7) ? 0 : row.getDayOfWeek(), row.getBuy());
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
                                sendNotification(notificationManager, notificationId, builder);
                            }
                            mToutcRepository.saveCosting(costing);
                            // NOTIFICATION PROGRESS
                            PROGRESS_CURRENT += PROGRESS_CHUNK;
                            builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
                            builder.setContentText("Data saved");
                            if (System.nanoTime() - notifyTime > 1e+9) {
                                notifyTime = System.nanoTime();
                                sendNotification(notificationManager, notificationId, builder);
                            }
                        }
                    } else {
                        builder.setContentText("Missing panel data");
                        sendNotification(notificationManager, notificationId, builder);
                        PROGRESS_CURRENT += PROGRESS_CHUNK;
                        builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
                        sendNotification(notificationManager, notificationId, builder);
                    }
                }

                // NOTIFICATION COMPLETE
                builder.setContentText("Calculation complete")
                        .setProgress(0, 0, false);
                sendNotification(notificationManager, notificationId, builder);
            }
        } catch (Exception e) {
            System.out.println("!!!!!!!!!!!!!!!!!!! CostingWorker has crashed, marking as failure !!!!!!!!!!!!!!!!!!!!!");
            e.printStackTrace();
            System.out.println("!!!!!!!!!!!!!!!!!!! CostingWorker has crashed, marking as failure !!!!!!!!!!!!!!!!!!!!!");
            builder.setContentText("Calculation failed, " + e.getMessage())
                    .setProgress(0, 0, false);
            sendNotification(notificationManager, notificationId, builder);
            return Result.success();
        }
        return Result.success();
    }

    private void sendNotification(NotificationManagerCompat notificationManager, int notificationId, NotificationCompat.Builder builder) {
        if (ActivityCompat.checkSelfPermission(
                mContext, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        notificationManager.notify(notificationId, builder.build());

    }
}


