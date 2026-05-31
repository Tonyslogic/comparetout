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

package com.tfcode.comparetout.ui2

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkContinuation
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tfcode.comparetout.CostingWorker
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.json.JsonTools
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile
import com.tfcode.comparetout.model.json.scenario.ScenarioJsonFile
import com.tfcode.comparetout.model.priceplan.DayRate
import com.tfcode.comparetout.scenario.SimulationWorker
import com.tfcode.comparetout.scenario.loadprofile.GenerateMissingLoadDataWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the bundled "Try with sample data" payload (one scenario + two
 * supplier plans) and triggers the same PVGIS-fetch + simulation work
 * the wizard's Save-with-runSimulation path enqueues.
 *
 * Used by the Dashboard empty-state and the drawer menu entry. Idempotent:
 * if any plan or scenario with the [SAMPLE_PREFIX] name already exists,
 * the load is a no-op so a second tap doesn't pile up duplicates.
 *
 * All work is on [Dispatchers.IO]. Backend-edit boundary: this loader
 * only calls the public surface of [ToutcRepository] — no DAO touch.
 */
@Singleton
class SampleDataLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ToutcRepository
) {

    sealed class Result {
        data object AlreadyLoaded : Result()
        data class Loaded(
            val scenarioId: Long,
            val planCount: Int,
            val pvgisFetchCount: Int
        ) : Result()
        data class Failed(val error: Throwable) : Result()
    }

    /**
     * One-shot stream of scenario IDs that should become the active dashboard
     * selection. Emitted only after a successful load. `UI2SharedViewModel`
     * collects this and calls `setActiveSimulationId(id)` so the dashboard
     * auto-navigates to the seeded scenario.
     *
     * `replay = 0` + `extraBufferCapacity = 1`: alive collectors at emit-time
     * all receive; ViewModels created later don't replay (which would clobber
     * any explicit navigation the user has done since). emit doesn't suspend.
     */
    private val _selectScenario = MutableSharedFlow<Long>(
        replay = 0, extraBufferCapacity = 1
    )
    val selectScenario: SharedFlow<Long> = _selectScenario.asSharedFlow()

    suspend fun load(): Result = withContext(Dispatchers.IO) {
        try {
            val existingPlans = repository.allPricePlansNow.orEmpty()
            val existingScenarios = repository.scenarios.orEmpty()
            val alreadyLoaded =
                existingPlans.any { it.planName?.startsWith(SAMPLE_PREFIX) == true } ||
                existingScenarios.any { it.scenarioName?.startsWith(SAMPLE_PREFIX) == true }
            if (alreadyLoaded) return@withContext Result.AlreadyLoaded

            val plans: List<PricePlanJsonFile> =
                Gson().fromJson(
                    readAsset(SAMPLE_PLANS_ASSET),
                    object : TypeToken<List<PricePlanJsonFile>>() {}.type
                )
            plans.forEach { pp ->
                val plan = JsonTools.createPricePlan(pp)
                val drs = ArrayList<DayRate>()
                pp.rates?.forEach { drs.add(JsonTools.createDayRate(it)) }
                repository.insert(plan, drs, false)
            }

            val scenarios: List<ScenarioJsonFile> =
                Gson().fromJson(
                    readAsset(SAMPLE_SCENARIO_ASSET),
                    object : TypeToken<List<ScenarioJsonFile>>() {}.type
                )
            val components = JsonTools.createScenarioComponentList(ArrayList(scenarios))
            val first = components.firstOrNull()
                ?: return@withContext Result.Failed(IllegalStateException(
                    "Sample scenario asset parsed to empty list"))
            val scenarioId = repository.insertScenarioAndReturnID(first, false)

            // Panels are inserted as part of insertScenarioAndReturnID. Look them
            // back up to get the autoGenerated panelIndex values so the PVGIS
            // workers we build below carry the right `panelID` input.
            val panels = repository.getPanelsForScenario(scenarioId).orEmpty()
            enqueueSamplePipeline(panels.map { it.panelIndex })

            _selectScenario.emit(scenarioId)
            Result.Loaded(scenarioId, plans.size, panels.size)
        } catch (e: Exception) {
            Result.Failed(e)
        }
    }

    /**
     * Build a single WorkManager chain that fetches PVGIS data for every panel
     * BEFORE running the simulation pipeline:
     *
     *     [PVGIS_1, PVGIS_2, …] → GenerateLoad → Simulate → Cost
     *
     * Why not just `SimulatorLauncher.simulateIfNeeded` like the wizard does?
     * Because `SimulationWorker` skips any scenario whose panels don't yet have
     * `paneldata` rows (see `SimulationWorker.java:153-171`). The wizard's
     * existing flow enqueues PVGIS fetch on `"PVGIS_$id"` unique work and the
     * sim chain on `"Simulation"` unique work — two independent chains, so
     * simulation runs before PVGIS finishes and skips. (The user has to press
     * Run a second time once panel data is in.) For one-tap onboarding that
     * race isn't acceptable, so we put both stages in the same chain.
     *
     * REPLACE policy on `"Simulation"` cancels any in-flight simulation work
     * — appropriate for the sample-load case which is always a fresh start.
     */
    private fun enqueueSamplePipeline(panelIds: List<Long>) {
        val wm = WorkManager.getInstance(context)
        wm.pruneWork()

        val pvgisRequests: List<OneTimeWorkRequest> = panelIds.map { id ->
            OneTimeWorkRequestBuilder<PVGISDirectFetchWorker>()
                .setInputData(Data.Builder().putLong("panelID", id).build())
                .addTag("pvgis_direct_$id")
                .build()
        }
        val generateLoad = OneTimeWorkRequestBuilder<GenerateMissingLoadDataWorker>().build()
        val simulate     = OneTimeWorkRequestBuilder<SimulationWorker>().build()
        val cost         = OneTimeWorkRequestBuilder<CostingWorker>().build()

        val afterPvgis: WorkContinuation = if (pvgisRequests.isEmpty()) {
            wm.beginUniqueWork("Simulation", ExistingWorkPolicy.REPLACE, generateLoad)
        } else {
            wm.beginUniqueWork("Simulation", ExistingWorkPolicy.REPLACE, pvgisRequests)
                .then(generateLoad)
        }
        afterPvgis.then(simulate).then(cost).enqueue()
    }

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    companion object {
        const val SAMPLE_PREFIX = "Sample · "
        private const val SAMPLE_SCENARIO_ASSET = "samples/sample_scenario.json"
        private const val SAMPLE_PLANS_ASSET = "samples/sample_price_plans.json"
    }
}

/** Hilt EntryPoint so Composables can resolve [SampleDataLoader] without an injected ViewModel. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SampleDataLoaderEntryPoint {
    fun sampleDataLoader(): SampleDataLoader
}
