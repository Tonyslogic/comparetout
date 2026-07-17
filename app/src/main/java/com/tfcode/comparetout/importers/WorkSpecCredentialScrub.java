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

package com.tfcode.comparetout.importers;

import android.util.Log;

import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.TOUTCApplication;
import com.tfcode.comparetout.importers.alphaess.DailyWorker;
import com.tfcode.comparetout.importers.alphaess.responses.GetEssListResponse;
import com.tfcode.comparetout.importers.homeassistant.HACatchupWorker;
import com.tfcode.comparetout.importers.octopus.OctopusCatchUpWorker;
import com.tfcode.comparetout.importers.solis.SolisCatchUpWorker;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.work.BackoffPolicy;

/**
 * One-time migration (plans/source/security.md §1): periodic daily fetch specs
 * enqueued by pre-security-plan app versions carry plaintext credentials in
 * their input {@code Data}, persisted indefinitely in WorkManager's database.
 * This scrub re-enqueues every live daily worker with credential-free inputs
 * (CANCEL_AND_REENQUEUE — the workers now resolve secrets via
 * {@link CredentialStore}) and prunes finished work records, after which no
 * plaintext spec remains on the device.
 * <p>
 * Idempotent and cheap: guarded by a DataStore flag (the
 * TimezoneRestampWorker pattern), and a fresh install has nothing scheduled,
 * so it just sets the guard. Runs off the main thread from
 * {@code TOUTCApplication.onCreate}.
 * <p>
 * The re-enqueued specs keep each source's conventions (period, tags, unique
 * names, Solis backoff). The initial delay re-aims at the usual 01:00–02:00
 * slot, so the only visible effect is the next run's time-of-day.
 */
public final class WorkSpecCredentialScrub {

    private static final String TAG = "WorkSpecCredScrub";
    public static final String DONE_KEY = "workspec_cred_scrub_done";

    private WorkSpecCredentialScrub() {
    }

    /** Run the scrub once; subsequent calls no-op via the DataStore guard. */
    public static void runOnce(TOUTCApplication app) {
        try {
            if ("true".equals(app.getStringValueFromDataStore(DONE_KEY))) return;
            WorkManager wm = WorkManager.getInstance(app);
            Gson gson = new Gson();

            // AlphaESS — one daily spec per SN in the persisted system list.
            String alphaRaw = app.getStringValueFromDataStore("system_list");
            if (!isBlank(alphaRaw)) {
                try {
                    GetEssListResponse alpha = gson.fromJson(alphaRaw, GetEssListResponse.class);
                    if (alpha != null && alpha.data != null) {
                        for (GetEssListResponse.DataItem system : alpha.data) {
                            if (system.sysSn == null || !isScheduled(wm, system.sysSn)) continue;
                            Data input = new Data.Builder()
                                    .putString(DailyWorker.KEY_SYSTEM_SN, system.sysSn)
                                    .build();
                            reEnqueue(wm, system.sysSn, DailyWorker.class, input, false);
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "AlphaESS scrub skipped: " + e.getMessage());
                }
            }

            // Home Assistant — single synthetic SN; sensors ride along (not secret).
            if (isScheduled(wm, "HomeAssistant")) {
                String sensors = app.getStringValueFromDataStore("ha_sensors");
                Data.Builder input = new Data.Builder();
                if (!isBlank(sensors)) input.putString(HACatchupWorker.KEY_SENSORS, sensors);
                reEnqueue(wm, "HomeAssistant", HACatchupWorker.class, input.build(), false);
            }

            // Octopus — one daily spec per system in the persisted list.
            String octopusRaw = app.getStringValueFromDataStore("octopus_system_list");
            if (!isBlank(octopusRaw)) {
                try {
                    List<com.tfcode.comparetout.importers.octopus.OctopusSystem> systems =
                            gson.fromJson(octopusRaw,
                                    new TypeToken<List<com.tfcode.comparetout.importers.octopus.OctopusSystem>>() {
                                    }.getType());
                    if (systems != null) {
                        for (com.tfcode.comparetout.importers.octopus.OctopusSystem system : systems) {
                            String sysSn = system.getSysSn();
                            if (sysSn == null || !isScheduled(wm, sysSn)) continue;
                            Data input = new Data.Builder()
                                    .putString(OctopusCatchUpWorker.KEY_SYSTEM_SN, sysSn)
                                    .build();
                            reEnqueue(wm, sysSn, OctopusCatchUpWorker.class, input, false);
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Octopus scrub skipped: " + e.getMessage());
                }
            }

            // Solis — one daily spec per station; station id/name/currency ride along.
            String solisRaw = app.getStringValueFromDataStore("solis_station_list");
            if (!isBlank(solisRaw)) {
                try {
                    List<SolisStationRef> stations = gson.fromJson(solisRaw,
                            new TypeToken<List<SolisStationRef>>() {
                            }.getType());
                    if (stations != null) {
                        for (SolisStationRef station : stations) {
                            if (station.id == null) continue;
                            String sysSn = "Solis-" + station.id;
                            if (!isScheduled(wm, sysSn)) continue;
                            Data input = new Data.Builder()
                                    .putString(SolisCatchUpWorker.KEY_STATION_ID, station.id)
                                    .putString(SolisCatchUpWorker.KEY_STATION_NAME, station.name)
                                    .putString(SolisCatchUpWorker.KEY_CURRENCY, station.money)
                                    .build();
                            reEnqueue(wm, sysSn, SolisCatchUpWorker.class, input, true);
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Solis scrub skipped: " + e.getMessage());
                }
            }

            // Finished records may still carry old plaintext inputs — drop them.
            wm.pruneWork();
            app.putStringValueIntoDataStore(DONE_KEY, "true");
            Log.i(TAG, "Work-spec credential scrub complete");
        } catch (Exception e) {
            // Never take the app down for a hygiene migration; retry next start.
            Log.w(TAG, "Scrub failed, will retry on next start", e);
        }
    }

    /** Is a not-yet-finished periodic worker live under [sysSn]+"daily"? */
    private static boolean isScheduled(WorkManager wm, String sysSn) {
        try {
            List<WorkInfo> infos = wm.getWorkInfosByTag(sysSn + "daily").get();
            for (WorkInfo info : infos) {
                if (!info.getState().isFinished()) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void reEnqueue(WorkManager wm, String sysSn,
                                  Class<? extends androidx.work.ListenableWorker> worker,
                                  Data input, boolean withBackoff) {
        long initialDelayHours = 25 - LocalDateTime.now().getHour();
        PeriodicWorkRequest.Builder builder =
                new PeriodicWorkRequest.Builder(worker, 1, TimeUnit.DAYS)
                        .setInputData(input)
                        .setInitialDelay(initialDelayHours, TimeUnit.HOURS)
                        .addTag(sysSn + "daily");
        if (withBackoff) {
            builder.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS);
        }
        wm.enqueueUniquePeriodicWork(sysSn + "daily",
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, builder.build());
        Log.i(TAG, "Re-enqueued credential-free daily spec for " + sysSn);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty() || "null".equals(s);
    }

    /** Minimal mirror of the persisted Solis station-list entries. */
    private static class SolisStationRef {
        String id;
        String name;
        String money;
    }
}
