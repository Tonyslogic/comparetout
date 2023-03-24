package com.tfcode.comparetout.scenario;

import static com.tfcode.comparetout.MainActivity.CHANNEL_ID;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.scenario.LoadProfileData;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;

import java.util.ArrayList;
import java.util.List;

public class SimulationWorker extends Worker {

    private final ToutcRepository mToutcRepository;

    public SimulationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mToutcRepository = new ToutcRepository((Application) context);
    }

    @NonNull
    @Override
    public Result doWork() {
        List<Long> scenarioIDs = mToutcRepository.getAllScenariosThatNeedSimulation();
        System.out.println("Found " + scenarioIDs.size() + " scenarios that need simulation");

        // NOTIFICATION SETUP
        int notificationId = 1;
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID);
        builder.setContentTitle("Simulating scenarios")
                .setContentText("Simulation in progress")
                .setSmallIcon(R.drawable.housetick)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setTimeoutAfter(20000);
        // Issue the initial notification with zero progress
        int PROGRESS_MAX = 100;
        int PROGRESS_CURRENT = 0;
        int PROGRESS_CHUNK = PROGRESS_MAX;
        if (scenarioIDs.size() > 0) {
            PROGRESS_CHUNK = PROGRESS_MAX / scenarioIDs.size();
            builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
            notificationManager.notify(notificationId, builder.build());
        }

        long startTime = System.nanoTime();
        for (long scenarioID: scenarioIDs) {
            Scenario scenario = mToutcRepository.getScenarioForID(scenarioID);
            System.out.println("Working on scenario " + scenario.getScenarioName());
            List<LoadProfileData> inputRows = mToutcRepository.getSimulationInput(scenarioID);
            //TODO Load from the DB all the places electricity can come from and go to
            ArrayList<ScenarioSimulationData> outputRows = new ArrayList<>();
            for (LoadProfileData inputRow : inputRows) {
                ScenarioSimulationData outputRow = new ScenarioSimulationData();
                outputRow.setScenarioID(scenarioID);
                outputRow.setDate(inputRow.getDate());
                outputRow.setMinuteOfDay(inputRow.getMod());
                outputRow.setDayOfWeek(inputRow.getDow());
                outputRow.setDayOf2001((inputRow.getDo2001()));
                // TODO use the 'places electricity can come from and go to'
                outputRow.setFeed(0);
                outputRow.setBuy(inputRow.getLoad());
                outputRow.setSOC(0);
                outputRow.setDirectEVcharge(0);
                outputRow.setWaterTemp(0);
                outputRow.setKWHDivToWater(0);
                outputRow.setKWHDivToEV(0);
                outputRow.setPvToCharge(0);
                outputRow.setPvToLoad(0);
                outputRow.setBatToLoad(0);
                outputRow.setPv(0);
                outputRow.setImmersionLoad(0);

                outputRows.add(outputRow);
            }
            System.out.println("adding " + outputRows.size() + " rows to DB for simulation: " + scenario.getScenarioName());
            mToutcRepository.saveSimulationDataForScenario(outputRows);

            // NOTIFICATION PROGRESS
            PROGRESS_CURRENT += PROGRESS_CHUNK;
            builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
            notificationManager.notify(notificationId, builder.build());
        }
        long endTime = System.nanoTime();
        System.out.println("Took " + (endTime-startTime)/1000000 + "mS to simulate " + scenarioIDs.size() + " scenarios" );

        if (scenarioIDs.size() > 0) {
            // NOTIFICATION COMPLETE
            builder.setContentText("Simulation complete")
                    .setProgress(0, 0, false);
            notificationManager.notify(notificationId, builder.build());
        }
        return Result.success();
    }
}
