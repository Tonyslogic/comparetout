/*
 * Copyright (c) 2026. Tony Finnerty
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

package com.tfcode.comparetout.model;

import static com.tfcode.comparetout.MainActivity.CHANNEL_ID;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.CostingWorker;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.SimulatorLauncher;
import com.tfcode.comparetout.TOUTCApplication;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.scenario.SimulationWorker;
import com.tfcode.comparetout.scenario.loadprofile.GenerateMissingLoadDataWorker;
import com.tfcode.comparetout.ui2.PVGISDirectFetchWorker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 3 of the saved-timezone / millis rollout (see {@code plans/sim/timezone-and-rollout.md}): a one-time
 * pass that discards the pre-millis PV data (which was written on the wrong grid and now merges to zero) and
 * refreshes it.
 *
 * <p>Sequence:</p>
 * <ol>
 *   <li>Delete <b>all</b> stale/bad output — {@code paneldata}, {@code scenariosimulationdata}, {@code costings}.</li>
 *   <li>Classify each panel by origin: a panel is <b>source-derived</b> iff its {@code inverter} matches a
 *       serial present in {@code alphaESSTransformedData}; otherwise it is <b>PVGIS</b>. (The agreed lat/lon rule
 *       was invalid — {@code Panel} defaults lat/lon to non-zero and importer panels keep the defaults.)</li>
 *   <li><b>PVGIS</b> panels are auto-refreshed by chaining a {@link PVGISDirectFetchWorker} per panel (it
 *       refetches from the panel's lat/lon, now writing the 2001/UTC/millis grid).</li>
 *   <li><b>Source-derived</b> panels cannot be auto-regenerated (the original import window isn't recorded), so
 *       their source serials are recorded in DataStore + surfaced in a notification for the user to re-run
 *       import&rarr;generate.</li>
 *   <li>Re-simulate: the PVGIS refetches are chained <i>before</i> generate-load &rarr; simulate &rarr; cost so
 *       scenarios aren't skipped for missing panel data; scenarios whose only PV is source-derived stay
 *       unsimulated until the user regenerates.</li>
 * </ol>
 *
 * <p>Lives in {@code com.tfcode.comparetout.model} to reach the package-private {@link ToutcDB#getDatabase}
 * (mirroring {@link SnapshotExporter}). Guarded once by a DataStore flag.</p>
 */
public class PanelDataRefreshWorker extends Worker {

    private static final String TAG = "PanelRefresh";
    public static final String DONE_KEY = "paneldata_refresh_v1_done";
    /** CSV of source serials whose panels need the user to re-run import&rarr;generate. */
    public static final String NEEDS_REGEN_KEY = "paneldata_needs_regen_sources";
    private static final String UNIQUE_WORK = "paneldata_refresh_v1";
    private static final String REFRESH_CHAIN = "paneldata_refresh_chain";

    public PanelDataRefreshWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    /**
     * Enqueue the one-time refresh. Uses {@link ExistingWorkPolicy#REPLACE} (not KEEP) so a previous attempt
     * stuck in retry-backoff doesn't block a relaunch; safe because it no-ops immediately once {@link #DONE_KEY}
     * is set (and the work it does — delete + re-fetch + re-sim — is idempotent).
     */
    public static void enqueue(@NonNull Context context) {
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(PanelDataRefreshWorker.class).build());
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        TOUTCApplication app = (TOUTCApplication) context;
        if ("true".equals(app.getStringValueFromDataStore(DONE_KEY))) return Result.success();

        // Runs from Application.onCreate, possibly before MainActivity creates the shared channel — create it
        // defensively (idempotent) or the notification is silently dropped on API 26+.
        ensureChannel(context);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText("Refreshing solar data")
                .setSmallIcon(R.drawable.housetick)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setSilent(true);
        notify(notificationManager, builder);

        try {
            ToutcDB db = ToutcDB.getDatabase(context);
            ScenarioDAO scenarioDAO = db.scenarioDAO();
            AlphaEssDAO alphaEssDAO = db.alphaEssDAO();
            CostingDAO costingDAO = db.costingDAO();

            // 1. Remove ALL stale/bad output. PV data is on the wrong grid; sims/costings are derived from it.
            scenarioDAO.deleteAllPanelData();
            scenarioDAO.deleteAllSimulationData();
            costingDAO.deleteAllCostings();
            // The readiness rows are now stale (they'd still read "up to date" while the output is gone) —
            // clear them so every scenario is re-derived as needing sim/costing by the defensive gate.
            scenarioDAO.deleteAllReadiness();

            // 2. Classify panels by origin.
            Set<String> sourceSerials = new HashSet<>(alphaEssDAO.getTransformedDataSysSns());
            List<OneTimeWorkRequest> refetch = new ArrayList<>();
            Set<String> needsRegen = new LinkedHashSet<>();
            for (Panel panel : scenarioDAO.getAllPanels()) {
                try {
                    if (sourceSerials.contains(panel.getInverter())) {
                        // Source-derived: original generation window is gone — needs the user.
                        needsRegen.add(panel.getInverter());
                    } else {
                        // PVGIS: refetch from lat/lon (no file / no user input).
                        Data input = new Data.Builder().putLong("panelID", panel.getPanelIndex()).build();
                        refetch.add(new OneTimeWorkRequest.Builder(PVGISDirectFetchWorker.class)
                                .setInputData(input).build());
                    }
                } catch (Exception panelError) {
                    // One bad panel must not abort (and then endlessly retry) the whole refresh — skip it.
                    Log.w(TAG, "Skipping panel " + panel.getPanelIndex() + " during refresh: "
                            + panelError.getMessage());
                }
            }

            // 3. Record + surface the source-derived panels that the user must regenerate.
            String regenCsv = String.join(",", needsRegen);
            app.putStringValueIntoDataStore(NEEDS_REGEN_KEY, regenCsv);
            if (!needsRegen.isEmpty()) {
                builder.setContentText(needsRegen.size()
                        + " solar source(s) need re-importing to refresh: " + regenCsv);
                notify(notificationManager, builder);
            }

            // 4. Re-simulate. Chain the PVGIS refetches BEFORE load-gen -> simulate -> cost so refreshed panels
            //    are present when the simulation runs.
            if (refetch.isEmpty()) {
                SimulatorLauncher.simulateIfNeeded(context);
            } else {
                WorkManager.getInstance(context)
                        .beginUniqueWork(REFRESH_CHAIN, ExistingWorkPolicy.REPLACE, refetch)
                        .then(new OneTimeWorkRequest.Builder(GenerateMissingLoadDataWorker.class).build())
                        .then(new OneTimeWorkRequest.Builder(SimulationWorker.class).build())
                        .then(new OneTimeWorkRequest.Builder(CostingWorker.class).build())
                        .enqueue();
            }

            app.putStringValueIntoDataStore(DONE_KEY, "true");
            builder.setContentText(needsRegen.isEmpty()
                    ? "Solar data refresh scheduled"
                    : "Solar data refresh scheduled — " + needsRegen.size() + " source(s) need re-importing");
            notify(notificationManager, builder);
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            // Not marked done — retries on a later start.
            return Result.retry();
        }
    }

    /** Defensively (re)create the shared progress channel — idempotent; safe before MainActivity has run. */
    private void ensureChannel(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        // DEFAULT (not LOW): builders silence via setSilent(true); a below-DEFAULT "silent" channel hides the status-bar icon
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.channel_description));
        nm.createNotificationChannel(channel);
    }

    private void notify(NotificationManagerCompat manager, NotificationCompat.Builder builder) {
        if (ActivityCompat.checkSelfPermission(getApplicationContext(),
                android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        manager.notify(3, builder.build());
    }
}
