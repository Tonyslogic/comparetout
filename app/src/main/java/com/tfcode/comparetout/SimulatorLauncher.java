package com.tfcode.comparetout;

import android.content.Context;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.tfcode.comparetout.scenario.SimulationWorker;
import com.tfcode.comparetout.scenario.loadprofile.GenerateMissingLoadDataWorker;
import com.tfcode.comparetout.scenario.panel.PVGISLoader;

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
                .beginUniqueWork("Simulation", ExistingWorkPolicy.APPEND,  generateLoadData)
                .then(simulate)
                .then(cost)
                .enqueue();
    }

    public static void storePVGISData(Context context, Long panelID) {

        System.out.println("Starting panel storage for " + panelID);
        Data.Builder data = new Data.Builder();
        data.putLong("panelID", panelID);

        OneTimeWorkRequest storePVGIS =
                new OneTimeWorkRequest.Builder(PVGISLoader.class)
                        .setInputData(data.build())
                        .build();

        WorkManager.getInstance(context).pruneWork();
        WorkManager
                .getInstance(context)
                .beginUniqueWork("PVGIS", ExistingWorkPolicy.APPEND,  storePVGIS)
                .enqueue();
    }
}
