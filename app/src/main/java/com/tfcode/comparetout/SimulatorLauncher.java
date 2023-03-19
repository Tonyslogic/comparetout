package com.tfcode.comparetout;

import android.content.Context;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.tfcode.comparetout.scenario.SimulationWorker;
import com.tfcode.comparetout.scenario.loadprofile.GenerateMissingLoadDataWorker;

public class SimulatorLauncher {


    public static void simulateIfNeeded(Context context) {
        System.out.println("************* looking for scenarios to simulate *************");
        System.out.println("*************************************************************");
        OneTimeWorkRequest generateLoadData =
                new OneTimeWorkRequest.Builder(GenerateMissingLoadDataWorker.class)
                        .build();
        OneTimeWorkRequest simulate =
                new OneTimeWorkRequest.Builder(SimulationWorker.class)
                        .build();
        OneTimeWorkRequest cost =
                new OneTimeWorkRequest.Builder(CostingWorker.class)
                        .build();

        WorkManager
                .getInstance(context)
                .beginUniqueWork("Simulation", ExistingWorkPolicy.APPEND_OR_REPLACE,  generateLoadData)
                .then(simulate)
                .then(cost)
                .enqueue();
    }
}
