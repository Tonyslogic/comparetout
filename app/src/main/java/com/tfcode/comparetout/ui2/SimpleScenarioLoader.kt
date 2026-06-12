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
import com.tfcode.comparetout.CostingWorker
import com.tfcode.comparetout.TOUTCApplication
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.scenario.Battery
import com.tfcode.comparetout.model.scenario.ChargeModel
import com.tfcode.comparetout.model.scenario.DOWDist
import com.tfcode.comparetout.model.IntHolder
import com.tfcode.comparetout.model.scenario.HourlyDist
import com.tfcode.comparetout.model.scenario.Inverter
import com.tfcode.comparetout.model.scenario.LoadProfile
import com.tfcode.comparetout.model.scenario.LoadShift
import com.tfcode.comparetout.model.scenario.MonthHolder
import com.tfcode.comparetout.model.scenario.MonthlyDist
import com.tfcode.comparetout.model.scenario.Panel
import com.tfcode.comparetout.model.scenario.Scenario
import com.tfcode.comparetout.model.scenario.ScenarioComponents
import com.tfcode.comparetout.scenario.SimulationWorker
import com.tfcode.comparetout.scenario.loadprofile.GenerateMissingLoadDataWorker
import com.tfcode.comparetout.scenario.loadprofile.StandardLoadProfiles
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds (and rebuilds) the single scenario that simple mode manages, then runs
 * the same PVGIS-fetch → simulate → cost pipeline the wizard's "Run" path and
 * [SampleDataLoader] use.
 *
 * It assembles a [ScenarioComponents] from a handful of inputs + sensible hidden
 * defaults and inserts it through the public [ToutcRepository] surface — no
 * DAO/Java-model edits. The DAO derives the scenario's `has*` flags from the
 * component lists, so only the lists need populating.
 *
 * Lifecycle: simple mode owns exactly one scenario (named
 * [SIMPLE_SCENARIO_NAME], id cached in DataStore). Each rebuild deletes the
 * previous one outright (plus its stale simulation/costing rows — the recompute
 * pipeline is missing-only) and creates a fresh one, so there is never partial
 * or stale state.
 */
@Singleton
class SimpleScenarioLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ToutcRepository
) {

    /** Where the daily usage shape comes from. */
    sealed class UsageSource {
        /** A Standard Load Profile by name (e.g. [StandardLoadProfiles.URBAN_SMART]). */
        data class Slp(val profileName: String) : UsageSource()
        /** Pre-aggregated distributions (e.g. derived from an imported HDF source). */
        data class Derived(
            val hourly: List<Double>,
            val daily: List<Double>,
            val monthly: List<Double>,
            val sourceLabel: String
        ) : UsageSource()
    }

    data class SimpleInputs(
        val annualKwh: Double,
        val usage: UsageSource,
        val hasSolar: Boolean,
        val latitude: Double,
        val longitude: Double,
        val azimuthDegrees: Int,
        /** 0 means no battery. Expected values: 0 / 5 / 10 / 15 kWh. */
        val batteryKwh: Double,
        val nightCharge: Boolean
    )

    sealed class Result {
        /** Scenario built and the pipeline enqueued. [pvgisPanels] = panel
         * strings whose PVGIS data is being fetched (0 when no solar). */
        data class Built(val scenarioId: Long, val pvgisPanels: Int) : Result()
        data class Failed(val error: Throwable) : Result()
    }

    suspend fun buildAndRun(inputs: SimpleInputs): Result = withContext(Dispatchers.IO) {
        try {
            val app = context.applicationContext as TOUTCApplication

            val (hourly, daily, monthly, sourceLabel) = resolveDistribution(inputs.usage)
                ?: return@withContext Result.Failed(
                    IllegalStateException("Could not resolve a usage distribution"))

            // Remove the previous simple scenario (and any orphan by name) so we
            // never accumulate duplicates or leave stale sim/costing rows.
            clearPreviousScenario(app)

            val components = buildComponents(inputs, hourly, daily, monthly, sourceLabel)
            val scenarioId = repository.insertScenarioAndReturnID(components, false)
            setStoredSimpleScenarioId(app, scenarioId)

            // Panels are inserted as part of the scenario; look them back up to
            // get the auto-generated ids the PVGIS worker needs.
            val panelIds = if (inputs.hasSolar) {
                repository.getPanelsForScenario(scenarioId).orEmpty().map { it.panelIndex }
            } else emptyList()

            enqueuePipeline(panelIds)
            Result.Built(scenarioId, panelIds.size)
        } catch (e: Exception) {
            Result.Failed(e)
        }
    }

    // ── Component assembly ────────────────────────────────────────────────

    private fun buildComponents(
        inputs: SimpleInputs,
        hourly: List<Double>,
        daily: List<Double>,
        monthly: List<Double>,
        sourceLabel: String
    ): ScenarioComponents {
        val scenario = Scenario().also { it.scenarioName = SIMPLE_SCENARIO_NAME }

        val inverter = Inverter().also { inv ->
            inv.inverterName = INVERTER_NAME
            // Sized above the fixed 7 kWp array so PV isn't clipped at the
            // wizard's default 5 kW — simple mode is about seeing solar's value.
            inv.maxInverterLoad = 8.0
            inv.mpptCount = 2
            inv.minExcess = 0.008
            inv.ac2dcLoss = 5
            inv.dc2acLoss = 5
            inv.dc2dcLoss = 0
        }

        val loadProfile = LoadProfile().also { lp ->
            lp.annualUsage = inputs.annualKwh
            lp.hourlyBaseLoad = 0.3
            lp.gridImportMax = 15.0
            lp.gridExportMax = 5.0
            lp.distributionSource = sourceLabel
            lp.hourlyDist = HourlyDist().also { it.dist = ArrayList(hourly) }
            lp.dowDist = DOWDist().also { it.dowDist = ArrayList(daily) }
            lp.monthlyDist = MonthlyDist().also { it.monthlyDist = ArrayList(monthly) }
        }

        val panels = if (inputs.hasSolar) listOf(
            Panel().also { p ->
                p.panelName = "Solar"
                // Fixed ≈7 kWp system (20 × 350 W); only orientation + location vary.
                p.panelCount = SIMPLE_PANEL_COUNT
                p.panelkWp = SIMPLE_PANEL_WP
                p.azimuth = inputs.azimuthDegrees
                p.slope = SIMPLE_PANEL_SLOPE
                p.latitude = inputs.latitude
                p.longitude = inputs.longitude
                p.inverter = INVERTER_NAME
                p.mppt = 1
                p.connectionMode = 0
            }
        ) else emptyList()

        val batteries = if (inputs.batteryKwh > 0.0) listOf(
            Battery().also { b ->
                b.batterySize = inputs.batteryKwh
                b.dischargeStop = 19.6
                b.maxCharge = inputs.batteryKwh / 24.0
                b.maxDischarge = inputs.batteryKwh / 24.0
                b.storageLoss = 1.0
                b.inverter = INVERTER_NAME
                b.chargeModel = ChargeModel().also { cm ->
                    cm.percent0 = 30
                    cm.percent12 = 100
                    cm.percent90 = 10
                }
            }
        ) else emptyList()

        val loadShifts = if (inputs.batteryKwh > 0.0 && inputs.nightCharge) listOf(
            LoadShift().also { s ->
                s.name = "Night charge"
                s.begin = NIGHT_CHARGE_BEGIN
                s.end = NIGHT_CHARGE_END
                s.stopAt = 100.0   // fill on the cheap night window
                s.inverter = INVERTER_NAME
                s.days = IntHolder().also { it.ints = ArrayList((0..6).toList()) }
                s.months = MonthHolder().also { it.months = ArrayList((1..12).toList()) }
            }
        ) else emptyList()

        return ScenarioComponents(
            scenario,
            listOf(inverter),
            batteries,
            panels,
            null,                 // no HW system in simple mode
            loadProfile,
            loadShifts,
            emptyList(),          // no discharge-to-grid
            emptyList(),          // no EV charges
            emptyList(),          // no HW schedules
            null,                 // no HW divert
            emptyList()           // no EV diverts
        )
    }

    // ── Usage distribution ────────────────────────────────────────────────

    private data class Distribution(
        val hourly: List<Double>,
        val daily: List<Double>,
        val monthly: List<Double>,
        val sourceLabel: String
    )

    private fun resolveDistribution(usage: UsageSource): Distribution? = when (usage) {
        is UsageSource.Derived ->
            Distribution(usage.hourly, usage.daily, usage.monthly, usage.sourceLabel)
        is UsageSource.Slp -> {
            val json = slpJsonFor(usage.profileName) ?: return null
            runCatching {
                val obj = JSONObject(json)
                val hourlyArr = obj.getJSONArray("HourlyDistribution")
                val hourly = (0 until hourlyArr.length()).map { hourlyArr.getDouble(it) }
                val dow = obj.getJSONObject("DayOfWeekDistribution")
                val daily = listOf(
                    dow.getDouble("Mon"), dow.getDouble("Tue"), dow.getDouble("Wed"),
                    dow.getDouble("Thu"), dow.getDouble("Fri"), dow.getDouble("Sat"),
                    dow.getDouble("Sun")
                )
                val mon = obj.getJSONObject("MonthlyDistribution")
                val monthly = listOf(
                    mon.getDouble("Jan"), mon.getDouble("Feb"), mon.getDouble("Mar"),
                    mon.getDouble("Apr"), mon.getDouble("May"), mon.getDouble("Jun"),
                    mon.getDouble("Jul"), mon.getDouble("Aug"), mon.getDouble("Sep"),
                    mon.getDouble("Oct"), mon.getDouble("Nov"), mon.getDouble("Dec")
                )
                Distribution(hourly, daily, monthly, usage.profileName)
            }.getOrNull()
        }
    }

    /** Mirror of the wizard's SLP name → JSON mapping ([UI2WizardViewModel.loadSLPProfile]). */
    private fun slpJsonFor(name: String): String? = when (name) {
        StandardLoadProfiles.URBAN_24 -> StandardLoadProfiles.SLP_24hr_urban
        StandardLoadProfiles.RURAL_24 -> StandardLoadProfiles.SLP_24hr_rural
        StandardLoadProfiles.URBAN_NIGHT -> StandardLoadProfiles.SLP_NightSaver_urban
        StandardLoadProfiles.RURAL_NIGHT -> StandardLoadProfiles.SLP_NightSaver_rural
        StandardLoadProfiles.URBAN_SMART -> StandardLoadProfiles.SLP_Smart_urban
        StandardLoadProfiles.RURAL_SMART -> StandardLoadProfiles.SLP_Smart_rural
        else -> null
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    private fun clearPreviousScenario(app: TOUTCApplication) {
        val ids = buildSet {
            storedSimpleScenarioId(app)?.let { add(it) }
            // Defensive: also catch any orphan left by the reserved name.
            repository.scenarios.orEmpty()
                .filter { it.scenarioName == SIMPLE_SCENARIO_NAME }
                .forEach { add(it.scenarioIndex) }
        }
        ids.forEach { id ->
            runCatching {
                repository.deleteSimulationDataForScenarioID(id)
                repository.deleteCostingDataForScenarioID(id)
                repository.deleteScenario(id.toInt())
            }
        }
        setStoredSimpleScenarioId(app, null)
    }

    // ── Pipeline ──────────────────────────────────────────────────────────

    /**
     * Single chained unique-work pipeline so PVGIS panel data lands *before*
     * the simulation runs (the simulator skips scenarios whose panels have no
     * data yet — see [SampleDataLoader]'s note):
     *
     *     [PVGIS_1, …] → GenerateLoad → Simulate → Cost
     */
    private fun enqueuePipeline(panelIds: List<Long>) {
        val wm = WorkManager.getInstance(context)
        wm.pruneWork()

        val pvgisRequests: List<OneTimeWorkRequest> = panelIds.map { id ->
            OneTimeWorkRequestBuilder<PVGISDirectFetchWorker>()
                .setInputData(Data.Builder().putLong("panelID", id).build())
                .addTag("pvgis_direct_$id")
                .build()
        }
        val generateLoad = OneTimeWorkRequestBuilder<GenerateMissingLoadDataWorker>().build()
        val simulate = OneTimeWorkRequestBuilder<SimulationWorker>().build()
        val cost = OneTimeWorkRequestBuilder<CostingWorker>().build()

        val afterPvgis: WorkContinuation = if (pvgisRequests.isEmpty()) {
            wm.beginUniqueWork("Simulation", ExistingWorkPolicy.REPLACE, generateLoad)
        } else {
            wm.beginUniqueWork("Simulation", ExistingWorkPolicy.REPLACE, pvgisRequests)
                .then(generateLoad)
        }
        afterPvgis.then(simulate).then(cost).enqueue()
    }

    companion object {
        private const val INVERTER_NAME = "Inverter"
        // Fixed ≈7 kWp array (20 × 350 W); panelkWp is watts-per-panel.
        private const val SIMPLE_PANEL_COUNT = 20
        private const val SIMPLE_PANEL_WP = 350
        private const val SIMPLE_PANEL_SLOPE = 40
        private const val NIGHT_CHARGE_BEGIN = 2
        private const val NIGHT_CHARGE_END = 5
    }
}

/** Hilt EntryPoint so Composables can resolve [SimpleScenarioLoader] without an injected ViewModel. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SimpleScenarioLoaderEntryPoint {
    fun simpleScenarioLoader(): SimpleScenarioLoader
}
