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

package com.tfcode.comparetout;

import android.content.Context;
import android.net.Uri;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;

import com.tfcode.comparetout.scenario.SimulationWorker;
import com.tfcode.comparetout.scenario.loadprofile.GenerateMissingLoadDataWorker;
import com.tfcode.comparetout.scenario.panel.PVGISLoader;

public class SimulatorLauncher {


    public static void simulateIfNeeded(Context context) {
        OneTimeWorkRequest generateLoadData =
                new OneTimeWorkRequest.Builder(GenerateMissingLoadDataWorker.class)
                        .build();
        OneTimeWorkRequest simulate =
                new OneTimeWorkRequest.Builder(SimulationWorker.class)
                        .build();
        OneTimeWorkRequest cost =
                new OneTimeWorkRequest.Builder(CostingWorker.class)
                        .build();

        WorkManager.getInstance(context).pruneWork();
        WorkManager
                .getInstance(context)
                .beginUniqueWork("Simulation", ExistingWorkPolicy.APPEND,  generateLoadData)
                .then(simulate)
                .then(cost)
                .enqueue();
    }

    public static void storePVGISData(Context context, Long panelID, Uri folderUri) {

        Data.Builder data = new Data.Builder();
        data.putLong("panelID", panelID);
        data.putString("folderUri", folderUri.toString());

        OneTimeWorkRequest storePVGIS =
                new OneTimeWorkRequest.Builder(PVGISLoader.class)
                        .addTag(panelID.toString())
                        .setInputData(data.build())
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build();

        WorkManager workManager = WorkManager.getInstance(context);
        workManager.pruneWork();

        workManager
                .beginUniqueWork("PVGIS", ExistingWorkPolicy.APPEND,  storePVGIS)
                .enqueue();
    }
}
