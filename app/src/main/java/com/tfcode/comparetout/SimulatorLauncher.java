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

import androidx.work.BackoffPolicy;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import com.tfcode.comparetout.profile.AppProfiles;
import com.tfcode.comparetout.scenario.SimulationWorker;
import com.tfcode.comparetout.scenario.loadprofile.GenerateMissingLoadDataWorker;
import com.tfcode.comparetout.scenario.panel.PVGISLoader;
import com.tfcode.comparetout.ui2.HeatPumpWeatherFetchWorker;

public class SimulatorLauncher {


    public static void simulateIfNeeded(Context context) {
        // Profiles without scenarios have nothing to simulate or cost — this
        // single gate neutralises every poke site (importer success, snapshot
        // import, legacy screens) and, transitively, the CDS weather fetch the
        // SimulationWorker would otherwise enqueue.
        if (!AppProfiles.current.hasScenarios) return;
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
        // KEEP (was APPEND): the workers process ALL currently-flagged scenarios in one pass off the
        // scenario_readiness gate, so concurrent triggers don't each need their own chain — KEEP coalesces
        // them into the one queued/running chain instead of stacking duplicates (the bulk-import storm).
        // CostingWorker's tail (enqueueFollowupPass) backstops any work flagged after the chain started.
        WorkManager
                .getInstance(context)
                .beginUniqueWork("Simulation", ExistingWorkPolicy.KEEP,  generateLoadData)
                .then(simulate)
                .then(cost)
                .enqueue();
    }

    /**
     * Append one more Generate→Simulate→Cost pass, used by {@code CostingWorker}'s tail when the readiness
     * gates still show work the just-finished chain couldn't see (flagged mid-run, or a scenario unblocked
     * by a self-heal fetch). Uses {@link ExistingWorkPolicy#APPEND} (not KEEP) so it is <b>not</b> dropped
     * while the current chain is still completing; it is bounded because every pass clears the flags it
     * completes, and blocked scenarios never appear in the gates.
     */
    public static void enqueueFollowupPass(Context context) {
        if (!AppProfiles.current.hasScenarios) return;
        OneTimeWorkRequest generateLoadData =
                new OneTimeWorkRequest.Builder(GenerateMissingLoadDataWorker.class).build();
        OneTimeWorkRequest simulate =
                new OneTimeWorkRequest.Builder(SimulationWorker.class).build();
        OneTimeWorkRequest cost =
                new OneTimeWorkRequest.Builder(CostingWorker.class).build();

        WorkManager.getInstance(context).pruneWork();
        WorkManager
                .getInstance(context)
                .beginUniqueWork("Simulation", ExistingWorkPolicy.APPEND, generateLoadData)
                .then(simulate)
                .then(cost)
                .enqueue();
    }

    /**
     * Like {@link #simulateIfNeeded(Context)} but inserts a CDS weather fetch <b>between</b> load generation
     * and the simulation, so a heat-pump-on-CDS scenario simulates on freshly-downloaded weather instead of
     * racing it (Phase 6 of {@code plans/hp/plan.md}):
     * <pre>GenerateLoad → HeatPumpWeatherFetch → Simulate → Cost</pre>
     * The fetch must follow load generation because it derives the fetch period from the load grid; it never
     * returns {@code failure()} (it falls back to the sample asset on give-up), so the chained sim always runs.
     */
    public static void simulateWithWeatherFetch(Context context, long scenarioId) {
        if (!AppProfiles.current.hasScenarios) return;
        OneTimeWorkRequest generateLoadData =
                new OneTimeWorkRequest.Builder(GenerateMissingLoadDataWorker.class).build();
        OneTimeWorkRequest weather =
                new OneTimeWorkRequest.Builder(HeatPumpWeatherFetchWorker.class)
                        .setInputData(new Data.Builder().putLong("scenarioID", scenarioId).build())
                        .addTag("hp_weather_" + scenarioId)
                        // Linear 15s backoff (not the default exponential ≈30s→minutes): a transient fetch retry
                        // shouldn't leave the chained simulation blocked for minutes.
                        .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
                        .build();
        OneTimeWorkRequest simulate =
                new OneTimeWorkRequest.Builder(SimulationWorker.class).build();
        OneTimeWorkRequest cost =
                new OneTimeWorkRequest.Builder(CostingWorker.class).build();

        WorkManager.getInstance(context).pruneWork();
        // KEEP (was APPEND) — see simulateIfNeeded; the readiness gate makes one chain cover all flagged work.
        WorkManager
                .getInstance(context)
                .beginUniqueWork("Simulation", ExistingWorkPolicy.KEEP, generateLoadData)
                .then(weather)
                .then(simulate)
                .then(cost)
                .enqueue();
    }

    public static void storePVGISData(Context context, Long panelID, Uri folderUri) {
        // Panels (and their PVGIS data) only exist in scenario-bearing profiles.
        if (!AppProfiles.current.hasScenarios) return;

        Data.Builder data = new Data.Builder();
        data.putLong("panelID", panelID);
        data.putString("folderUri", folderUri.toString());

        OneTimeWorkRequest storePVGIS =
                new OneTimeWorkRequest.Builder(PVGISLoader.class)
                        .addTag(panelID.toString())
                        .setInputData(data.build())
// Setting expedited breaks some older android version as they expect foreground interfaces to be implemented
//                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build();

        WorkManager workManager = WorkManager.getInstance(context);
        workManager.pruneWork();

        workManager
                .beginUniqueWork("PVGIS", ExistingWorkPolicy.APPEND,  storePVGIS)
                .enqueue();
    }
}
