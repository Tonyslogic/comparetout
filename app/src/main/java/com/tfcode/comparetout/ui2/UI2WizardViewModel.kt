package com.tfcode.comparetout.ui2

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.TOUTCApplication
import com.tfcode.comparetout.model.IntHolder
import com.tfcode.comparetout.model.ToutcRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import com.tfcode.comparetout.model.scenario.Battery
import com.tfcode.comparetout.model.scenario.ChargeModel
import com.tfcode.comparetout.model.scenario.DischargeToGrid
import com.tfcode.comparetout.model.scenario.EVCharge
import com.tfcode.comparetout.model.scenario.EVDivert
import com.tfcode.comparetout.model.scenario.Inverter
import com.tfcode.comparetout.model.scenario.LoadShift
import com.tfcode.comparetout.model.scenario.Panel
import com.tfcode.comparetout.model.scenario.PanelPVSummary
import com.tfcode.comparetout.scenario.loadprofile.StandardLoadProfiles
import com.tfcode.comparetout.model.scenario.DOWDist
import com.tfcode.comparetout.model.scenario.HourlyDist
import com.tfcode.comparetout.model.scenario.HWDivert
import com.tfcode.comparetout.model.scenario.HWSchedule
import com.tfcode.comparetout.model.scenario.HWSystem
import com.tfcode.comparetout.model.scenario.HWUse
import com.tfcode.comparetout.model.scenario.LoadProfile
import com.tfcode.comparetout.model.scenario.MonthHolder
import com.tfcode.comparetout.model.scenario.MonthlyDist
import com.tfcode.comparetout.model.scenario.Scenario
import com.tfcode.comparetout.model.scenario.ScenarioComponents
import com.tfcode.comparetout.model.json.JsonTools
import com.tfcode.comparetout.model.json.scenario.ScenarioJsonFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.ArrayList
import java.util.UUID
import javax.inject.Inject

enum class ScenarioMode { NEW, COPY, LINK, IMPORT }
enum class LoadSource { SOURCE, SLP, COPY_PROFILE, HAND, LINKED }

data class SourceDateRange(
    val sysSn: String,
    val startDate: String,
    val finishDate: String,
    val importerType: ComparisonUIViewModel.Importer
)

data class WizardEvEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "EV Schedule",
    val startHour: Int = 2,
    val endHour: Int = 6,
    val drawKw: Double = 7.5,
    val days: List<Int> = (0..6).toList(),      // 0=Mon…6=Sun, matches IntHolder
    val months: List<Int> = (1..12).toList()    // 1=Jan…12=Dec, matches MonthHolder
) {
    fun toEvCharge(): EVCharge = EVCharge().also { ev ->
        ev.name = name
        ev.begin = startHour
        ev.end = endHour
        ev.draw = drawKw
        ev.days = IntHolder().also { it.ints = ArrayList(days) }
        ev.months = MonthHolder().also { it.months = ArrayList(months) }
    }
}

data class WizardEvDivertEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Afternoon-nap",
    val active: Boolean = false,
    val ev1st: Boolean = true,
    val beginHour: Int = 11,
    val endHour: Int = 16,
    val dailyMax: Double = 16.0,
    val minimum: Double = 1.4,
    val days: List<Int> = (0..6).toList(),
    val months: List<Int> = (1..12).toList()
) {
    fun toEvDivert(): EVDivert = EVDivert().also { d ->
        d.setName(name)
        d.setActive(active)
        d.setEv1st(ev1st)
        d.setBegin(beginHour)
        d.setEnd(endHour)
        d.setDailyMax(dailyMax)
        d.setMinimum(minimum)
        d.setDays(IntHolder().also { it.ints = ArrayList(days) })
        d.setMonths(MonthHolder().also { it.months = ArrayList(months) })
    }
}

private fun EVDivert.toWizardEvDivertEntry() = WizardEvDivertEntry(
    id = if (evDivertIndex > 0) evDivertIndex.toString() else UUID.randomUUID().toString(),
    name = name ?: "Afternoon-nap",
    active = isActive,
    ev1st = isEv1st,
    beginHour = begin,
    endHour = end,
    dailyMax = dailyMax,
    minimum = minimum,
    days = days?.ints?.sorted() ?: (0..6).toList(),
    months = months?.months?.sorted() ?: (1..12).toList()
)

private fun EVCharge.toWizardEvEntry() = WizardEvEntry(
    id = if (evChargeIndex > 0) evChargeIndex.toString() else UUID.randomUUID().toString(),
    name = name ?: "EV Schedule",
    startHour = begin,
    endHour = end,
    drawKw = draw,
    days = days?.ints?.sorted() ?: (0..6).toList(),
    months = months?.months?.sorted() ?: (1..12).toList()
)

data class WizardInverterEntry(
    val id: String = UUID.randomUUID().toString(),
    val inverterName: String = "Inverter",
    val maxInverterLoad: Double = 5.0,
    val mpptCount: Int = 2,
    val minExcess: Double = 0.008,
    val ac2dcLoss: Int = 5,
    val dc2acLoss: Int = 5,
    val dc2dcLoss: Int = 0
) {
    fun toInverter(): Inverter = Inverter().also { inv ->
        inv.inverterName = inverterName
        inv.maxInverterLoad = maxInverterLoad
        inv.mpptCount = mpptCount
        inv.minExcess = minExcess
        inv.ac2dcLoss = ac2dcLoss
        inv.dc2acLoss = dc2acLoss
        inv.dc2dcLoss = dc2dcLoss
    }
}

private fun Inverter.toWizardInverterEntry() = WizardInverterEntry(
    id = if (inverterIndex > 0) inverterIndex.toString() else UUID.randomUUID().toString(),
    inverterName = inverterName ?: "Inverter",
    maxInverterLoad = maxInverterLoad,
    mpptCount = mpptCount,
    minExcess = minExcess,
    ac2dcLoss = ac2dcLoss,
    dc2acLoss = dc2acLoss,
    dc2dcLoss = dc2dcLoss
)

/* ──────────────────────────────────────────────────────────────────
   Battery
   - Battery entity: size + inverter binding + BMS (advanced)
   - LoadShift          → charge schedule (per-inverter binding in model;
                          wizard shows one logical list and replicates
                          per battery inverter on save)
   - DischargeToGrid    → discharge schedule (same replication pattern)
────────────────────────────────────────────────────────────────── */

data class WizardBatteryEntry(
    val id: String = UUID.randomUUID().toString(),
    val batteryIndex: Long = 0L,
    val inverterName: String = "",
    val batterySize: Double = 5.7,        // kWh
    val dischargeStop: Double = 19.6,     // % SOC floor
    val maxCharge: Double = 5.7 / 24.0,   // kWh per 5-min ≈ 0.5C
    val maxDischarge: Double = 5.7 / 24.0,
    val storageLoss: Double = 1.0,        // % round-trip
    val cmPercent0: Int = 30,             // 0–12% SOC charge rate %
    val cmPercent12: Int = 100,           // 12–90% SOC charge rate %
    val cmPercent90: Int = 10             // 90–100% SOC charge rate %
) {
    fun toBattery(): Battery = Battery().also { b ->
        if (batteryIndex > 0L) b.batteryIndex = batteryIndex
        b.batterySize = batterySize
        b.dischargeStop = dischargeStop
        b.maxCharge = maxCharge
        b.maxDischarge = maxDischarge
        b.storageLoss = storageLoss
        b.inverter = inverterName.ifBlank { "AlphaESS" }
        b.chargeModel = ChargeModel().also { cm ->
            cm.percent0 = cmPercent0
            cm.percent12 = cmPercent12
            cm.percent90 = cmPercent90
        }
    }
}

private fun Battery.toWizardBatteryEntry() = WizardBatteryEntry(
    id = if (batteryIndex > 0) batteryIndex.toString() else UUID.randomUUID().toString(),
    batteryIndex = batteryIndex,
    inverterName = inverter ?: "AlphaESS",
    batterySize = batterySize,
    dischargeStop = dischargeStop,
    maxCharge = maxCharge,
    maxDischarge = maxDischarge,
    storageLoss = storageLoss,
    cmPercent0 = chargeModel?.percent0 ?: 30,
    cmPercent12 = chargeModel?.percent12 ?: 100,
    cmPercent90 = chargeModel?.percent90 ?: 10
)

data class WizardBatteryChargeEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Cheap-rate",
    val inverterName: String = "",          // schedule applies to all batteries on this inverter
    val beginHour: Int = 2,
    val endHour: Int = 5,
    val stopAt: Double = 80.0,              // target SOC %
    val days: List<Int> = (0..6).toList(),
    val months: List<Int> = (1..12).toList()
) {
    fun toLoadShift(): LoadShift = LoadShift().also { s ->
        s.name = name
        s.begin = beginHour
        s.end = endHour
        s.stopAt = stopAt
        s.inverter = inverterName.ifBlank { "AlphaESS" }
        s.days = IntHolder().also { it.ints = ArrayList(days) }
        s.months = MonthHolder().also { it.months = ArrayList(months) }
    }
}

private fun LoadShift.toWizardBatteryChargeEntry() = WizardBatteryChargeEntry(
    id = if (loadShiftIndex > 0) loadShiftIndex.toString() else UUID.randomUUID().toString(),
    name = name ?: "Cheap-rate",
    inverterName = inverter ?: "",
    beginHour = begin,
    endHour = end,
    stopAt = stopAt,
    days = days?.ints?.sorted() ?: (0..6).toList(),
    months = months?.months?.sorted() ?: (1..12).toList()
)

data class WizardBatteryDischargeEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Peak-export",
    val inverterName: String = "",
    val beginHour: Int = 17,
    val endHour: Int = 19,
    val stopAt: Double = 20.0,              // SOC floor %
    val rate: Double = 2.5,                 // kW export rate
    val days: List<Int> = (0..6).toList(),
    val months: List<Int> = (1..12).toList()
) {
    fun toDischargeToGrid(): DischargeToGrid = DischargeToGrid().also { d ->
        d.name = name
        d.begin = beginHour
        d.end = endHour
        d.stopAt = stopAt
        d.rate = rate
        d.inverter = inverterName.ifBlank { "AlphaESS" }
        d.days = IntHolder().also { it.ints = ArrayList(days) }
        d.months = MonthHolder().also { it.months = ArrayList(months) }
    }
}

private fun DischargeToGrid.toWizardBatteryDischargeEntry() = WizardBatteryDischargeEntry(
    id = if (d2gIndex > 0) d2gIndex.toString() else UUID.randomUUID().toString(),
    name = name ?: "Peak-export",
    inverterName = inverter ?: "",
    beginHour = begin,
    endHour = end,
    stopAt = stopAt,
    rate = rate,
    days = days?.ints?.sorted() ?: (0..6).toList(),
    months = months?.months?.sorted() ?: (1..12).toList()
)

/* ──────────────────────────────────────────────────────────────────
   Hot Water
   - HWSystem  : single tank — size, daily usage, heater rate; advanced:
                 intake temp, target temp, daily heat-loss °C
   - HWSchedule: heater on-window (defaults to a cheap-rate night slot)
   - HWDivert  : single boolean — divert excess solar to the immersion
────────────────────────────────────────────────────────────────── */

/** One draw moment in the day — (hour, percent-of-daily-usage). */
data class WizardHwUsePoint(
    val hour: Int = 8,
    val percent: Double = 50.0
)

data class WizardHwSystemEntry(
    val capacity: Int = 165,        // litres
    val usage: Int = 200,           // litres/day
    val rate: Double = 2.5,         // kW heater
    val intake: Int = 15,           // °C cold-feed
    val target: Int = 75,           // °C cylinder thermostat
    val loss: Int = 8,              // daily °C drop with no draw
    val usagePattern: List<WizardHwUsePoint> = listOf(
        WizardHwUsePoint(8, 75.0),
        WizardHwUsePoint(14, 10.0),
        WizardHwUsePoint(20, 15.0)
    )
) {
    fun toHwSystem(): HWSystem = HWSystem().also { s ->
        s.hwCapacity = capacity
        s.hwUsage = usage
        s.hwRate = rate
        s.hwIntake = intake
        s.hwTarget = target
        s.hwLoss = loss
        s.hwUse = HWUse().also { hu ->
            // Replace constructor defaults with the user's pattern.
            hu.usage = ArrayList(usagePattern.map { p ->
                ArrayList<Double>().apply { add(p.hour.toDouble()); add(p.percent) }
            })
        }
    }
}

private fun HWSystem.toWizardHwSystemEntry() = WizardHwSystemEntry(
    capacity = hwCapacity,
    usage = hwUsage,
    rate = hwRate,
    intake = hwIntake,
    target = hwTarget,
    loss = hwLoss,
    usagePattern = hwUse?.usage
        ?.mapNotNull { row ->
            val h = row.getOrNull(0)?.toInt() ?: return@mapNotNull null
            val p = row.getOrNull(1) ?: return@mapNotNull null
            WizardHwUsePoint(h, p)
        }
        ?.takeIf { it.isNotEmpty() }
        ?: listOf(
            WizardHwUsePoint(8, 75.0),
            WizardHwUsePoint(14, 10.0),
            WizardHwUsePoint(20, 15.0)
        )
)

data class WizardHwScheduleEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Midnight-water",
    val beginHour: Int = 2,
    val endHour: Int = 6,
    val days: List<Int> = (0..6).toList(),
    val months: List<Int> = (1..12).toList()
) {
    fun toHwSchedule(): HWSchedule = HWSchedule().also { h ->
        h.name = name
        h.begin = beginHour
        h.end = endHour
        h.days = IntHolder().also { it.ints = ArrayList(days) }
        h.months = MonthHolder().also { it.months = ArrayList(months) }
    }
}

private fun HWSchedule.toWizardHwScheduleEntry() = WizardHwScheduleEntry(
    id = if (hwScheduleIndex > 0) hwScheduleIndex.toString() else UUID.randomUUID().toString(),
    name = name ?: "Midnight-water",
    beginHour = begin,
    endHour = end,
    days = days?.ints?.sorted() ?: (0..6).toList(),
    months = months?.months?.sorted() ?: (1..12).toList()
)

data class WizardHwDivertEntry(
    val active: Boolean = false
) {
    fun toHwDivert(): HWDivert = HWDivert().also { d -> d.setActive(active) }
}

private fun HWDivert.toWizardHwDivertEntry() = WizardHwDivertEntry(active = isActive)

enum class PanelDataSource { NONE, PVGIS, SOURCE }

data class WizardPanelEntry(
    val id: String = UUID.randomUUID().toString(),
    val panelIndex: Long = 0L,
    val panelName: String = "String 1",
    val panelCount: Int = 7,
    val panelkWp: Int = 325,
    val azimuth: Int = 136,
    val slope: Int = 24,
    val latitude: Double = 53.490,
    val longitude: Double = -10.015,
    val inverterName: String = "",
    val mppt: Int = 1,
    val connectionMode: Int = 0,
    val pvDataSource: PanelDataSource = PanelDataSource.NONE,
    val pvSourceSysSn: String = "",
    val pvSourceFrom: String = "",
    val pvSourceTo: String = "",
    // Advanced source-processing: 0.0 means "match string total" (panelCount * panelkWp / 1000)
    val pvSourceKwp: Double = 0.0,
    // Apply azimuth conversion from pvSourceAzimuth -> azimuth using clear-sky model
    val pvUseAzimuthFactor: Boolean = false,
    // -1 means "match string azimuth" (effectively no conversion)
    val pvSourceAzimuth: Int = -1
) {
    fun toPanel(): Panel = Panel().also { p ->
        if (panelIndex > 0L) p.panelIndex = panelIndex
        p.panelName = panelName
        p.panelCount = panelCount
        p.panelkWp = panelkWp
        p.azimuth = azimuth
        p.slope = slope
        p.latitude = latitude
        p.longitude = longitude
        p.inverter = inverterName.ifBlank { "Inverter" }
        p.mppt = mppt
        p.connectionMode = connectionMode
    }
}

private fun Panel.toWizardPanelEntry() = WizardPanelEntry(
    id = panelIndex.toString(),
    panelIndex = panelIndex,
    panelName = panelName ?: "String 1",
    panelCount = panelCount,
    panelkWp = panelkWp,
    azimuth = azimuth,
    slope = slope,
    latitude = latitude,
    longitude = longitude,
    inverterName = inverter ?: "",
    mppt = mppt,
    connectionMode = connectionMode
)

data class WizardBuilder(
    // Start
    val scenarioMode: ScenarioMode = ScenarioMode.NEW,
    val scenarioName: String = "",
    val basedOnId: Long = -1L,
    val isLinked: Boolean = false,
    val isLoadLinked: Boolean = false,
    // Usage Data (Load Profile)
    val loadSource: LoadSource = LoadSource.SOURCE,
    val distributionSource: String = "",
    val annualUsage: String = "6200",
    val hourlyBaseLoad: String = "0.3",
    val gridImportMax: String = "15.0",
    val gridExportMax: String = "5.0",
    val patchWithSlp: Boolean = true,
    val slpProfile: String = "",
    // Distribution charts (populated when load profile has real data)
    val loadProfileHourly: List<Double>? = null,
    val loadProfileDaily: List<Double>? = null,
    val loadProfileMonthly: List<Double>? = null,
    // EV
    val evEntries: List<WizardEvEntry> = emptyList(),
    val evDivertEntries: List<WizardEvDivertEntry> = emptyList(),
    // Inverters
    val inverterEntries: List<WizardInverterEntry> = emptyList(),
    // Panels
    val panelEntries: List<WizardPanelEntry> = emptyList(),
    // Battery
    val batteryEntries: List<WizardBatteryEntry> = emptyList(),
    val batteryChargeEntries: List<WizardBatteryChargeEntry> = emptyList(),
    val batteryDischargeEntries: List<WizardBatteryDischargeEntry> = emptyList(),
    // Hot Water
    val hwSystem: WizardHwSystemEntry? = null,
    val hwSchedules: List<WizardHwScheduleEntry> = emptyList(),
    val hwDivert: WizardHwDivertEntry = WizardHwDivertEntry()
) {
    val isStartComplete: Boolean get() = scenarioName.isNotBlank()
    val isLoadComplete: Boolean get() = annualUsage.toDoubleOrNull()?.let { it > 0.0 } == true
    val isRunnable: Boolean get() = isStartComplete && isLoadComplete
    val hasDistributionData: Boolean get() = loadProfileHourly != null

    fun toLoadProfile(): LoadProfile = LoadProfile().also { lp ->
        lp.annualUsage = annualUsage.toDoubleOrNull() ?: 6200.0
        lp.hourlyBaseLoad = hourlyBaseLoad.toDoubleOrNull() ?: 0.3
        lp.gridImportMax = gridImportMax.toDoubleOrNull() ?: 15.0
        lp.gridExportMax = gridExportMax.toDoubleOrNull() ?: 5.0
        lp.distributionSource = when (loadSource) {
            LoadSource.SOURCE -> distributionSource.ifBlank { "Custom" }
            LoadSource.SLP -> slpProfile.ifBlank { "Custom" }
            else -> distributionSource.ifBlank { "Custom" }
        }
        loadProfileHourly?.let  { lp.hourlyDist  = HourlyDist().also  { d -> d.dist        = ArrayList(it) } }
        loadProfileDaily?.let   { lp.dowDist     = DOWDist().also     { d -> d.dowDist      = ArrayList(it) } }
        loadProfileMonthly?.let { lp.monthlyDist = MonthlyDist().also { d -> d.monthlyDist  = ArrayList(it) } }
    }

    /** Inverter names that have at least one battery attached. */
    fun batteryInverterNames(): List<String> =
        batteryEntries.map { it.inverterName.ifBlank { "AlphaESS" } }.distinct()

    /** LoadShifts ready for the DB: one per entry, dropping entries with no inverter target. */
    fun expandedLoadShifts(): List<LoadShift> =
        batteryChargeEntries
            .filter { it.inverterName.isNotBlank() }
            .map { it.toLoadShift() }

    /** DischargeToGrids ready for the DB: one per entry, dropping entries with no inverter target. */
    fun expandedDischarges(): List<DischargeToGrid> =
        batteryDischargeEntries
            .filter { it.inverterName.isNotBlank() }
            .map { it.toDischargeToGrid() }

    fun toScenarioComponents(): ScenarioComponents {
        val sc = Scenario().also { it.scenarioName = scenarioName }
        return ScenarioComponents(
            sc,
            inverterEntries.map { it.toInverter() },
            batteryEntries.map { it.toBattery() },
            emptyList(),
            hwSystem?.toHwSystem(),
            toLoadProfile(),
            expandedLoadShifts(),
            expandedDischarges(),
            evEntries.map { it.toEvCharge() },
            hwSchedules.map { it.toHwSchedule() },
            if (hwDivert.active) hwDivert.toHwDivert() else null,
            evDivertEntries.map { it.toEvDivert() }
        )
    }

    // Shell with no load profile and no EV — used for full-link saves
    fun toScenarioShell(): ScenarioComponents {
        val sc = Scenario().also { it.scenarioName = scenarioName }
        return ScenarioComponents(
            sc, emptyList(), emptyList(), emptyList(), null, LoadProfile(),
            emptyList(), emptyList(), emptyList(), emptyList(), null, emptyList()
        )
    }

    // Shell with EV+inverters+batteries from builder but no load profile — used for load-linked saves
    fun toScenarioShellWithEV(): ScenarioComponents {
        val sc = Scenario().also { it.scenarioName = scenarioName }
        return ScenarioComponents(
            sc,
            inverterEntries.map { it.toInverter() },
            batteryEntries.map { it.toBattery() },
            emptyList(),
            hwSystem?.toHwSystem(),
            LoadProfile(),
            expandedLoadShifts(),
            expandedDischarges(),
            evEntries.map { it.toEvCharge() },
            hwSchedules.map { it.toHwSchedule() },
            if (hwDivert.active) hwDivert.toHwDivert() else null,
            evDivertEntries.map { it.toEvDivert() }
        )
    }
}

private const val KEY_NOVICE_MODE = "wizard_novice_mode"

sealed class WizardSaveResult {
    object Idle : WizardSaveResult()
    object Saving : WizardSaveResult()
    data class Done(val scenarioId: Long, val runSimulation: Boolean, val pvgisStringsQueued: Int = 0) : WizardSaveResult()
}

@HiltViewModel
class UI2WizardViewModel @Inject constructor(
    private val repository: ToutcRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val scenarioId: Long = savedStateHandle["ScenarioID"] ?: -1L
    val isEditMode: Boolean get() = scenarioId != -1L
    private val wizardSection: String? = savedStateHandle["WizardSection"]

    private val _builder = MutableStateFlow(WizardBuilder())
    val builder = _builder.asLiveData()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asLiveData()

    private val _noviceMode = MutableStateFlow(true)
    val noviceMode = _noviceMode.asLiveData()

    private val _expandedSections = MutableStateFlow(setOf("start"))
    val expandedSections = _expandedSections.asLiveData()

    private val _isDeriving = MutableStateFlow(false)
    val isDeriving = _isDeriving.asLiveData()

    private val _saveResult = MutableStateFlow<WizardSaveResult>(WizardSaveResult.Idle)
    val saveResult = _saveResult.asLiveData()

    // Monthly PV totals per panel — used to display already-fetched PVGIS data (ID-based, edit mode)
    val panelPvSummary: LiveData<List<PanelPVSummary>> = repository.getPanelDataSummary()

    // Parameter-based PVGIS data check: entry.id → hasData
    private val _pvgisParamCheck = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val pvgisParamCheck = _pvgisParamCheck.asLiveData()

    fun checkPvgisParams(entryId: String, lat: Double, lon: Double, azimuth: Int, slope: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val hasData = repository.hasPvgisDataForParameters(lat, lon, azimuth, slope)
            _pvgisParamCheck.update { it + (entryId to hasData) }
        }
    }

    // All existing scenarios for the copy/link picker and name-uniqueness check
    val allScenarios = repository.getAllScenarios()

    // Combined list of available data sources (AlphaESS + ESBN + HA)
    private val _availableSources = MediatorLiveData<List<SourceDateRange>>()
    val availableSources: LiveData<List<SourceDateRange>> = _availableSources

    init {
        val alphaRanges = repository.getLiveDateRanges()
        val esbnRanges  = repository.getESBNLiveDateRanges()
        val haRanges    = repository.getHALiveDateRanges()

        fun rebuild() {
            // Deduplicate by sysSn — alpha takes priority, then esbn, then ha.
            // getLiveDateRanges/getESBNLiveDateRanges/getHALiveDateRanges all query the same
            // underlying table so every AlphaESS sysSn would otherwise appear three times.
            val seen = mutableSetOf<String>()
            val out = mutableListOf<SourceDateRange>()
            alphaRanges.value?.forEach { r ->
                if (seen.add(r.sysSn)) out.add(SourceDateRange(r.sysSn, r.startDate, r.finishDate, ComparisonUIViewModel.Importer.ALPHAESS))
            }
            esbnRanges.value?.forEach { r ->
                val type = if (r.sysSn == "HomeAssistant") ComparisonUIViewModel.Importer.HOME_ASSISTANT
                           else ComparisonUIViewModel.Importer.ESBNHDF
                if (seen.add(r.sysSn)) out.add(SourceDateRange(r.sysSn, r.startDate, r.finishDate, type))
            }
            haRanges.value?.forEach { r ->
                if (seen.add(r.sysSn)) out.add(SourceDateRange(r.sysSn, r.startDate, r.finishDate, ComparisonUIViewModel.Importer.HOME_ASSISTANT))
            }
            _availableSources.value = out
        }

        _availableSources.addSource(alphaRanges) { rebuild() }
        _availableSources.addSource(esbnRanges)  { rebuild() }
        _availableSources.addSource(haRanges)    { rebuild() }

        viewModelScope.launch(Dispatchers.IO) {
            val app = context.applicationContext as TOUTCApplication
            val stored = app.getDataStore()
                .data()
                .firstOrError()
                .map { prefs -> prefs[stringPreferencesKey(KEY_NOVICE_MODE)] ?: "true" }
                .onErrorReturnItem("true")
                .blockingGet()
            _noviceMode.value = stored != "false"
        }

        if (wizardSection != null) {
            _expandedSections.value = setOf(wizardSection)
        }

        if (isEditMode) loadExisting()
    }

    private fun loadExisting() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            populateBuilderFrom(scenarioId, isLinked = false, keepName = false)
            _isLoading.value = false
        }
    }

    /** Load another scenario's components into the builder for COPY mode (no DB write). */
    fun loadForCopy(fromId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val prevName = _builder.value.scenarioName
            populateBuilderFrom(fromId, isLinked = false, keepName = false)
            // Append _copy suffix if the user hasn't typed a name yet
            if (prevName.isBlank()) {
                _builder.value = _builder.value.copy(
                    scenarioMode = ScenarioMode.COPY,
                    scenarioName = _builder.value.scenarioName + "_copy"
                )
            } else {
                _builder.value = _builder.value.copy(scenarioMode = ScenarioMode.COPY, scenarioName = prevName)
            }
            _isLoading.value = false
        }
    }

    /** Load another scenario's components into the builder for LINK mode (no DB write). */
    fun loadForLink(fromId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val prevName = _builder.value.scenarioName
            populateBuilderFrom(fromId, isLinked = true, keepName = false)
            _builder.value = _builder.value.copy(
                scenarioMode = ScenarioMode.LINK,
                scenarioName = prevName   // keep the user's chosen name
            )
            _isLoading.value = false
        }
    }

    /**
     * Load a parsed [ScenarioJsonFile] into the builder for IMPORT mode (no DB
     * write). The JSON is converted to the same [ScenarioComponents] shape the
     * COPY/LINK paths consume so the rest of the wizard is none the wiser.
     *
     * `basedOnId = 0` marks the builder as having no parent scenario — Save
     * will create a fresh row, identical in semantics to a COPY of a deleted
     * source. If the imported file has the same name as an existing scenario
     * the user-typed `scenarioName` is appended with " (imported)" so we never
     * silently clobber.
     */
    fun loadFromJson(file: ScenarioJsonFile) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val components = runCatching {
                JsonTools.createScenarioComponentList(arrayListOf(file))
            }.getOrNull()?.firstOrNull()
            if (components != null) {
                val prevName = _builder.value.scenarioName
                applyComponentsToBuilder(components, fromId = 0L, isLinked = false, keepName = false)
                // Keep IMPORT in the mode chip; preserve any name the user
                // already typed (mirrors COPY's prevName handling).
                val name = if (prevName.isNotBlank()) prevName else _builder.value.scenarioName
                _builder.value = _builder.value.copy(
                    scenarioMode = ScenarioMode.IMPORT,
                    scenarioName = name
                )
            }
            _isLoading.value = false
        }
    }

    private fun populateBuilderFrom(fromId: Long, isLinked: Boolean, keepName: Boolean) {
        val c = repository.getScenarioComponentsForScenarioID(fromId)
        applyComponentsToBuilder(c, fromId, isLinked, keepName)
    }

    /**
     * Shared between [populateBuilderFrom] (DB-sourced) and [loadFromJson]
     * (JSON-sourced). Pulls every component list off the [c] bundle and maps
     * it into Wizard*Entry rows.
     */
    private fun applyComponentsToBuilder(
        c: ScenarioComponents,
        fromId: Long,
        isLinked: Boolean,
        keepName: Boolean
    ) {
        val lp = c.loadProfile
        val name = if (keepName) _builder.value.scenarioName
        else c.scenario?.scenarioName ?: ""
        _builder.value = _builder.value.copy(
            basedOnId = fromId,
            isLinked = isLinked,
            isLoadLinked = false,
            scenarioName = name,
            distributionSource = lp?.distributionSource ?: "",
            annualUsage = lp?.annualUsage?.toString() ?: "6200",
            hourlyBaseLoad = lp?.hourlyBaseLoad?.toString() ?: "0.3",
            gridImportMax = lp?.gridImportMax?.toString() ?: "15.0",
            gridExportMax = lp?.gridExportMax?.toString() ?: "5.0",
            loadProfileHourly = lp?.hourlyDist?.dist,
            loadProfileDaily = lp?.dowDist?.dowDist,
            loadProfileMonthly = lp?.monthlyDist?.monthlyDist,
            evEntries = c.evCharges?.map { it.toWizardEvEntry() } ?: emptyList(),
            evDivertEntries = c.evDiverts?.map { it.toWizardEvDivertEntry() } ?: emptyList(),
            inverterEntries = c.inverters?.map { it.toWizardInverterEntry() } ?: emptyList(),
            panelEntries = c.panels?.map { it.toWizardPanelEntry() } ?: emptyList(),
            batteryEntries = c.batteries?.map { it.toWizardBatteryEntry() } ?: emptyList(),
            batteryChargeEntries = c.loadShifts?.map { it.toWizardBatteryChargeEntry() } ?: emptyList(),
            batteryDischargeEntries = c.discharges?.map { it.toWizardBatteryDischargeEntry() } ?: emptyList(),
            hwSystem = c.hwSystem?.toWizardHwSystemEntry(),
            hwSchedules = c.hwSchedules
                ?.distinctBy { it.hwScheduleIndex }
                ?.map { it.toWizardHwScheduleEntry() }
                ?: emptyList(),
            hwDivert = c.hwDivert?.toWizardHwDivertEntry() ?: WizardHwDivertEntry()
        )
    }

    fun toggleNoviceMode() {
        val newValue = !_noviceMode.value
        _noviceMode.value = newValue
        viewModelScope.launch(Dispatchers.IO) {
            (context.applicationContext as TOUTCApplication)
                .putStringValueIntoDataStore(KEY_NOVICE_MODE, newValue.toString())
        }
    }

    fun toggleSection(id: String) {
        _expandedSections.value = _expandedSections.value.toMutableSet().also {
            if (it.contains(id)) it.remove(id) else it.add(id)
        }
    }

    fun updateBuilder(transform: (WizardBuilder) -> WizardBuilder) {
        _builder.value = transform(_builder.value)
    }

    // ── per-accordion JSON import (Phase D3) ────────────────────────────────
    //
    // Each setter is the inverse of the per-section export the user can do
    // from a saved scenario — replace the in-progress draft slice with the
    // contents of a parsed JSON fragment. None of these write to the DB; the
    // user is still inside the wizard and Save handles persistence.

    fun replaceLoadProfileFromJson(json: com.tfcode.comparetout.model.json.scenario.LoadProfileJson) {
        // createLoadProfile NPEs if dayOfWeekDistribution or monthlyDistribution
        // is missing; guard runCatching mirrors the legacy importers' tolerance
        // of partial JSON.
        val lp = runCatching { JsonTools.createLoadProfile(json) }.getOrNull() ?: return
        updateBuilder { b ->
            b.copy(
                annualUsage = lp.annualUsage?.toString() ?: b.annualUsage,
                hourlyBaseLoad = lp.hourlyBaseLoad?.toString() ?: b.hourlyBaseLoad,
                gridImportMax = lp.gridImportMax?.toString() ?: b.gridImportMax,
                gridExportMax = lp.gridExportMax?.toString() ?: b.gridExportMax,
                loadProfileHourly = lp.hourlyDist?.dist ?: b.loadProfileHourly,
                loadProfileDaily = lp.dowDist?.dowDist ?: b.loadProfileDaily,
                loadProfileMonthly = lp.monthlyDist?.monthlyDist ?: b.loadProfileMonthly,
                distributionSource = lp.distributionSource ?: ""
            )
        }
    }

    fun replaceInvertersFromJson(
        list: List<com.tfcode.comparetout.model.json.scenario.InverterJson>
    ) {
        val domain = JsonTools.createInverterList(ArrayList(list))
        updateBuilder { b ->
            b.copy(inverterEntries = domain.map { it.toWizardInverterEntry() })
        }
    }

    fun replacePanelsFromJson(
        list: List<com.tfcode.comparetout.model.json.scenario.PanelJson>
    ) {
        val domain = JsonTools.createPanelList(ArrayList(list))
        updateBuilder { b ->
            b.copy(panelEntries = domain.map { it.toWizardPanelEntry() })
        }
    }

    /**
     * Battery import is compound: the three cross-referencing draft slices
     * (batteries, charge schedules, discharge schedules) are replaced together
     * because the schedules reference battery/inverter names. Passing only one
     * of them would leave dangling references.
     */
    fun replaceBatteryGroupFromJson(
        batteries: List<com.tfcode.comparetout.model.json.scenario.BatteryJson>?,
        loadShifts: List<com.tfcode.comparetout.model.json.scenario.LoadShiftJson>?,
        discharges: List<com.tfcode.comparetout.model.json.scenario.DischargeToGridJson>?
    ) {
        val battDomain = JsonTools.createBatteryList(ArrayList(batteries ?: emptyList()))
        val shiftDomain = JsonTools.createLoadShiftList(ArrayList(loadShifts ?: emptyList()))
        val dischDomain = JsonTools.createDischargeList(ArrayList(discharges ?: emptyList()))
        updateBuilder { b ->
            b.copy(
                batteryEntries = battDomain.map { it.toWizardBatteryEntry() },
                batteryChargeEntries = shiftDomain.map { it.toWizardBatteryChargeEntry() },
                batteryDischargeEntries = dischDomain.map { it.toWizardBatteryDischargeEntry() }
            )
        }
    }

    fun replaceHwGroupFromJson(
        system: com.tfcode.comparetout.model.json.scenario.HWSystemJson?,
        schedules: List<com.tfcode.comparetout.model.json.scenario.HWScheduleJson>?,
        divert: com.tfcode.comparetout.model.json.scenario.HWDivertJson?
    ) {
        val systemDomain = system?.let { JsonTools.createHWSystem(it) }
        val schedDomain = JsonTools.createHWScheduleList(ArrayList(schedules ?: emptyList()))
        val divertDomain = divert?.let { JsonTools.createHWDivert(it) }
        updateBuilder { b ->
            b.copy(
                hwSystem = systemDomain?.toWizardHwSystemEntry(),
                hwSchedules = schedDomain
                    .distinctBy { it.hwScheduleIndex }
                    .map { it.toWizardHwScheduleEntry() },
                hwDivert = divertDomain?.toWizardHwDivertEntry() ?: WizardHwDivertEntry()
            )
        }
    }

    fun replaceEvGroupFromJson(
        charges: List<com.tfcode.comparetout.model.json.scenario.EVChargeJson>?,
        diverts: List<com.tfcode.comparetout.model.json.scenario.EVDivertJson>?,
        legacyDivert: com.tfcode.comparetout.model.json.scenario.EVDivertJson?
    ) {
        val chargeDomain = JsonTools.createEVChargeList(ArrayList(charges ?: emptyList()))
        // EVDivertList accepts both the new list shape ("EVDiverts") and the
        // legacy single-object shape ("EVDivert") — same as legacy imports.
        val divertDomain = JsonTools.createEVDivertList(
            ArrayList(diverts ?: emptyList()),
            legacyDivert
        )
        updateBuilder { b ->
            b.copy(
                evEntries = chargeDomain.map { it.toWizardEvEntry() },
                evDivertEntries = divertDomain.map { it.toWizardEvDivertEntry() }
            )
        }
    }

    fun addEvEntry() = updateBuilder { it.copy(evEntries = it.evEntries + WizardEvEntry()) }

    fun removeEvEntry(id: String) =
        updateBuilder { it.copy(evEntries = it.evEntries.filter { e -> e.id != id }) }

    fun updateEvEntry(id: String, transform: (WizardEvEntry) -> WizardEvEntry) =
        updateBuilder { b ->
            b.copy(evEntries = b.evEntries.map { if (it.id == id) transform(it) else it })
        }

    fun addEvDivertEntry() = updateBuilder { it.copy(evDivertEntries = it.evDivertEntries + WizardEvDivertEntry()) }

    fun removeEvDivertEntry(id: String) =
        updateBuilder { it.copy(evDivertEntries = it.evDivertEntries.filter { e -> e.id != id }) }

    fun updateEvDivertEntry(id: String, transform: (WizardEvDivertEntry) -> WizardEvDivertEntry) =
        updateBuilder { b ->
            b.copy(evDivertEntries = b.evDivertEntries.map { if (it.id == id) transform(it) else it })
        }

    fun addBatteryEntry() {
        updateBuilder { b ->
            // Auto-select inverter when exactly one exists
            val inv = if (b.inverterEntries.size == 1) b.inverterEntries[0].inverterName else ""
            val size = 5.7
            val maxRate = size / 24.0   // 0.5C per 5-min interval
            b.copy(batteryEntries = b.batteryEntries + WizardBatteryEntry(
                inverterName = inv,
                batterySize = size,
                maxCharge = maxRate,
                maxDischarge = maxRate
            ))
        }
    }

    fun removeBatteryEntry(id: String) =
        updateBuilder { it.copy(batteryEntries = it.batteryEntries.filter { e -> e.id != id }) }

    fun updateBatteryEntry(id: String, transform: (WizardBatteryEntry) -> WizardBatteryEntry) =
        updateBuilder { b ->
            b.copy(batteryEntries = b.batteryEntries.map { if (it.id == id) transform(it) else it })
        }

    fun addBatteryChargeEntry() = updateBuilder { b ->
        val defaultInv = b.batteryInverterNames().firstOrNull() ?: ""
        b.copy(batteryChargeEntries = b.batteryChargeEntries + WizardBatteryChargeEntry(inverterName = defaultInv))
    }

    fun removeBatteryChargeEntry(id: String) =
        updateBuilder { it.copy(batteryChargeEntries = it.batteryChargeEntries.filter { e -> e.id != id }) }

    fun updateBatteryChargeEntry(id: String, transform: (WizardBatteryChargeEntry) -> WizardBatteryChargeEntry) =
        updateBuilder { b ->
            b.copy(batteryChargeEntries = b.batteryChargeEntries.map { if (it.id == id) transform(it) else it })
        }

    fun addBatteryDischargeEntry() = updateBuilder { b ->
        val defaultInv = b.batteryInverterNames().firstOrNull() ?: ""
        b.copy(batteryDischargeEntries = b.batteryDischargeEntries + WizardBatteryDischargeEntry(inverterName = defaultInv))
    }

    fun removeBatteryDischargeEntry(id: String) =
        updateBuilder { it.copy(batteryDischargeEntries = it.batteryDischargeEntries.filter { e -> e.id != id }) }

    fun updateBatteryDischargeEntry(id: String, transform: (WizardBatteryDischargeEntry) -> WizardBatteryDischargeEntry) =
        updateBuilder { b ->
            b.copy(batteryDischargeEntries = b.batteryDischargeEntries.map { if (it.id == id) transform(it) else it })
        }

    fun enableHwSystem() = updateBuilder {
        if (it.hwSystem == null) it.copy(hwSystem = WizardHwSystemEntry()) else it
    }

    fun removeHwSystem() = updateBuilder {
        it.copy(hwSystem = null, hwSchedules = emptyList(), hwDivert = WizardHwDivertEntry())
    }

    fun updateHwSystem(transform: (WizardHwSystemEntry) -> WizardHwSystemEntry) =
        updateBuilder { b -> b.copy(hwSystem = b.hwSystem?.let(transform)) }

    fun addHwSchedule() = updateBuilder {
        it.copy(hwSchedules = it.hwSchedules + WizardHwScheduleEntry())
    }

    fun removeHwSchedule(id: String) =
        updateBuilder { it.copy(hwSchedules = it.hwSchedules.filter { e -> e.id != id }) }

    fun updateHwSchedule(id: String, transform: (WizardHwScheduleEntry) -> WizardHwScheduleEntry) =
        updateBuilder { b ->
            b.copy(hwSchedules = b.hwSchedules.map { if (it.id == id) transform(it) else it })
        }

    fun updateHwDivert(transform: (WizardHwDivertEntry) -> WizardHwDivertEntry) =
        updateBuilder { b -> b.copy(hwDivert = transform(b.hwDivert)) }

    fun addInverterEntry() = updateBuilder { it.copy(inverterEntries = it.inverterEntries + WizardInverterEntry()) }

    fun removeInverterEntry(id: String) =
        updateBuilder { it.copy(inverterEntries = it.inverterEntries.filter { e -> e.id != id }) }

    fun updateInverterEntry(id: String, transform: (WizardInverterEntry) -> WizardInverterEntry) =
        updateBuilder { b ->
            b.copy(inverterEntries = b.inverterEntries.map { if (it.id == id) transform(it) else it })
        }

    fun addPanelEntry() {
        val count = _builder.value.panelEntries.size + 1
        updateBuilder { it.copy(panelEntries = it.panelEntries + WizardPanelEntry(panelName = "String $count")) }
    }

    fun removePanelEntry(id: String) =
        updateBuilder { it.copy(panelEntries = it.panelEntries.filter { e -> e.id != id }) }

    fun updatePanelEntry(id: String, transform: (WizardPanelEntry) -> WizardPanelEntry) =
        updateBuilder { b ->
            b.copy(panelEntries = b.panelEntries.map { if (it.id == id) transform(it) else it })
        }

    // GPS location for panel lat/lon
    private val _pendingLocationRequest = MutableStateFlow<String?>(null)
    val pendingLocationRequest = _pendingLocationRequest.asLiveData()

    fun requestLocation(entryId: String) { _pendingLocationRequest.value = entryId }
    fun locationRetrieved(entryId: String, lat: Double, lon: Double) {
        updatePanelEntry(entryId) { it.copy(latitude = lat, longitude = lon) }
        _pendingLocationRequest.value = null
    }
    fun locationRequestDismissed() { _pendingLocationRequest.value = null }

    private fun triggerPanelDataFetch(entry: WizardPanelEntry, panelId: Long) {
        android.util.Log.i(PanelSourceFetchWorker.TAG,
            "triggerPanelDataFetch: panelId=$panelId panelName='${entry.panelName}' " +
            "dataSource=${entry.pvDataSource} sysSn='${entry.pvSourceSysSn}' " +
            "from='${entry.pvSourceFrom}' to='${entry.pvSourceTo}' " +
            "sourceKwp=${entry.pvSourceKwp} useAz=${entry.pvUseAzimuthFactor} srcAz=${entry.pvSourceAzimuth} " +
            "panelCount=${entry.panelCount} panelkWp=${entry.panelkWp} azimuth=${entry.azimuth}")
        when (entry.pvDataSource) {
            PanelDataSource.PVGIS -> {
                android.util.Log.i(PanelSourceFetchWorker.TAG, "→ PVGIS branch — enqueueing PVGISDirectFetchWorker")
                PVGISDirectFetchWorker.enqueue(context, panelId)
            }
            PanelDataSource.SOURCE -> {
                if (entry.pvSourceSysSn.isNotBlank() && entry.pvSourceFrom.isNotBlank() && entry.pvSourceTo.isNotBlank()) {
                    android.util.Log.i(PanelSourceFetchWorker.TAG, "→ SOURCE branch — preconditions OK, will enqueue")
                    val stringKwp = entry.panelCount * entry.panelkWp / 1000.0
                    val srcKwp = if (entry.pvSourceKwp > 0.0) entry.pvSourceKwp else stringKwp
                    val srcAz = if (entry.pvSourceAzimuth in 0..360) entry.pvSourceAzimuth.toDouble() else entry.azimuth.toDouble()
                    PanelSourceFetchWorker.enqueue(
                        context = context,
                        panelId = panelId,
                        sysSn = entry.pvSourceSysSn,
                        from = entry.pvSourceFrom,
                        to = entry.pvSourceTo,
                        sourceKwp = srcKwp,
                        targetKwp = stringKwp,
                        useAzimuthFactor = entry.pvUseAzimuthFactor,
                        sourceAzDeg = srcAz,
                        targetAzDeg = entry.azimuth.toDouble(),
                        lat = entry.latitude,
                        lon = entry.longitude,
                        tiltDeg = entry.slope.toDouble(),
                        panelLabel = entry.panelName
                    )
                } else {
                    android.util.Log.w(PanelSourceFetchWorker.TAG,
                        "→ SOURCE branch SKIPPED — blank precondition " +
                        "(sysSn='${entry.pvSourceSysSn}', from='${entry.pvSourceFrom}', to='${entry.pvSourceTo}')")
                }
            }
            PanelDataSource.NONE -> {
                android.util.Log.i(PanelSourceFetchWorker.TAG, "→ NONE branch — no fetch")
            }
        }
    }

    fun loadSLPProfile(name: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val json = when (name) {
                StandardLoadProfiles.URBAN_24    -> StandardLoadProfiles.SLP_24hr_urban
                StandardLoadProfiles.RURAL_24    -> StandardLoadProfiles.SLP_24hr_rural
                StandardLoadProfiles.URBAN_NIGHT -> StandardLoadProfiles.SLP_NightSaver_urban
                StandardLoadProfiles.RURAL_NIGHT -> StandardLoadProfiles.SLP_NightSaver_rural
                StandardLoadProfiles.URBAN_SMART -> StandardLoadProfiles.SLP_Smart_urban
                StandardLoadProfiles.RURAL_SMART -> StandardLoadProfiles.SLP_Smart_rural
                else -> return@launch
            }
            try {
                val (hourly, daily, monthly) = parseSLPJson(json)
                val annualUsage = org.json.JSONObject(json).getDouble("AnnualUsage").toString()
                _builder.value = _builder.value.copy(
                    loadSource = LoadSource.SLP,
                    slpProfile = name,
                    distributionSource = name,
                    annualUsage = annualUsage,
                    isLoadLinked = false,
                    loadProfileHourly = hourly,
                    loadProfileDaily = daily,
                    loadProfileMonthly = monthly
                )
            } catch (_: Exception) { }
        }
    }

    private fun parseSLPJson(json: String): Triple<List<Double>, List<Double>, List<Double>> {
        val obj = org.json.JSONObject(json)
        val hourlyArr = obj.getJSONArray("HourlyDistribution")
        val hourly = (0 until hourlyArr.length()).map { hourlyArr.getDouble(it) }
        val dowObj = obj.getJSONObject("DayOfWeekDistribution")
        val daily = listOf(
            dowObj.getDouble("Mon"), dowObj.getDouble("Tue"), dowObj.getDouble("Wed"),
            dowObj.getDouble("Thu"), dowObj.getDouble("Fri"), dowObj.getDouble("Sat"),
            dowObj.getDouble("Sun")
        )
        val monObj = obj.getJSONObject("MonthlyDistribution")
        val monthly = listOf(
            monObj.getDouble("Jan"), monObj.getDouble("Feb"), monObj.getDouble("Mar"),
            monObj.getDouble("Apr"), monObj.getDouble("May"), monObj.getDouble("Jun"),
            monObj.getDouble("Jul"), monObj.getDouble("Aug"), monObj.getDouble("Sep"),
            monObj.getDouble("Oct"), monObj.getDouble("Nov"), monObj.getDouble("Dec")
        )
        return Triple(hourly, daily, monthly)
    }

    fun loadProfileForLink(fromId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val c = repository.getScenarioComponentsForScenarioID(fromId)
            val lp = c.loadProfile
            _builder.value = _builder.value.copy(
                loadSource = LoadSource.LINKED,
                isLoadLinked = true,
                distributionSource = c.scenario?.scenarioName ?: "",
                annualUsage = lp?.annualUsage?.toString() ?: _builder.value.annualUsage,
                hourlyBaseLoad = lp?.hourlyBaseLoad?.toString() ?: _builder.value.hourlyBaseLoad,
                gridImportMax = lp?.gridImportMax?.toString() ?: _builder.value.gridImportMax,
                gridExportMax = lp?.gridExportMax?.toString() ?: _builder.value.gridExportMax,
                loadProfileHourly = lp?.hourlyDist?.dist,
                loadProfileDaily = lp?.dowDist?.dowDist,
                loadProfileMonthly = lp?.monthlyDist?.monthlyDist
            )
            _isLoading.value = false
        }
    }

    fun loadProfileForCopy(fromId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val c = repository.getScenarioComponentsForScenarioID(fromId)
            val lp = c.loadProfile
            _builder.value = _builder.value.copy(
                loadSource = LoadSource.COPY_PROFILE,
                isLoadLinked = false,
                distributionSource = "Copied from ${c.scenario?.scenarioName ?: ""}",
                annualUsage = lp?.annualUsage?.toString() ?: _builder.value.annualUsage,
                hourlyBaseLoad = lp?.hourlyBaseLoad?.toString() ?: _builder.value.hourlyBaseLoad,
                gridImportMax = lp?.gridImportMax?.toString() ?: _builder.value.gridImportMax,
                gridExportMax = lp?.gridExportMax?.toString() ?: _builder.value.gridExportMax,
                loadProfileHourly = lp?.hourlyDist?.dist,
                loadProfileDaily = lp?.dowDist?.dowDist,
                loadProfileMonthly = lp?.monthlyDist?.monthlyDist
            )
            _isLoading.value = false
        }
    }

    fun initHandCraft() {
        val flat24 = List(24) { 100.0 / 24.0 }
        val flat7  = List(7)  { 100.0 / 7.0 }
        val flat12 = List(12) { 100.0 / 12.0 }
        _builder.value = _builder.value.copy(
            loadSource = LoadSource.HAND,
            isLoadLinked = false,
            loadProfileHourly  = _builder.value.loadProfileHourly  ?: flat24,
            loadProfileDaily   = _builder.value.loadProfileDaily    ?: flat7,
            loadProfileMonthly = _builder.value.loadProfileMonthly ?: flat12,
            distributionSource = "Hand-crafted"
        )
    }

    fun deriveLoadProfileFromSource(
        sysSn: String,
        importerType: ComparisonUIViewModel.Importer,
        from: LocalDate,
        to: LocalDate,
        fillGaps: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isDeriving.value = true
            try {
                val rows = repository.getAlphaESSTransformedData(sysSn, from.toString(), to.toString())
                if (rows.isEmpty()) return@launch

                val hourlySum  = DoubleArray(24)
                val dailySum   = DoubleArray(7)
                val monthlySum = DoubleArray(12)
                var totalEnergy = 0.0

                rows.forEach { row ->
                    val e = if (importerType == ComparisonUIViewModel.Importer.ESBNHDF) row.buy else row.load
                    if (e <= 0.0) return@forEach
                    val date       = LocalDate.parse(row.date)
                    val hour       = row.minute.substringBefore(":").toIntOrNull() ?: return@forEach
                    val dayOfWeek  = date.dayOfWeek.value - 1   // Mon=0..Sun=6
                    val month      = date.monthValue - 1         // Jan=0..Nov=11
                    hourlySum[hour]    += e
                    dailySum[dayOfWeek] += e
                    monthlySum[month]  += e
                    totalEnergy        += e
                }

                if (totalEnergy <= 0.0) return@launch

                val hourly  = hourlySum.map  { it / totalEnergy * 100.0 }
                val daily   = dailySum.map   { it / totalEnergy * 100.0 }
                val monthly = monthlySum.map { it / totalEnergy * 100.0 }

                val actualDays    = ChronoUnit.DAYS.between(from, to) + 1
                val scaledAnnual  = totalEnergy * (365.0 / actualDays)

                val sourceName = when (importerType) {
                    ComparisonUIViewModel.Importer.ALPHAESS        -> "AlphaESS: $sysSn"
                    ComparisonUIViewModel.Importer.ESBNHDF         -> "ESBN: $sysSn"
                    ComparisonUIViewModel.Importer.HOME_ASSISTANT  -> "Home Assistant: $sysSn"
                    else -> sysSn
                }

                _builder.value = _builder.value.copy(
                    loadSource         = LoadSource.SOURCE,
                    isLoadLinked       = false,
                    distributionSource = sourceName,
                    annualUsage        = "%.1f".format(scaledAnnual),
                    patchWithSlp       = fillGaps,
                    loadProfileHourly  = hourly,
                    loadProfileDaily   = daily,
                    loadProfileMonthly = monthly
                )
            } finally {
                _isDeriving.value = false
            }
        }
    }

    fun save(runSimulation: Boolean) {
        if (_saveResult.value == WizardSaveResult.Saving) return
        viewModelScope.launch(Dispatchers.IO) {
            _saveResult.value = WizardSaveResult.Saving
            try {
                val b = _builder.value
                android.util.Log.i(PanelSourceFetchWorker.TAG,
                    "save(): scenarioName='${b.scenarioName}' isEditMode=$isEditMode " +
                    "isLinked=${b.isLinked} loadSource=${b.loadSource} " +
                    "panelEntries=${b.panelEntries.size} " +
                    "[" + b.panelEntries.joinToString { "${it.panelName}/${it.pvDataSource}/sysSn='${it.pvSourceSysSn}'" } + "]")
                val pvgisCount = when {
                    isEditMode -> b.panelEntries.count { it.panelIndex == 0L && it.pvDataSource == PanelDataSource.PVGIS }
                    b.isLinked -> 0
                    else       -> b.panelEntries.count { it.pvDataSource == PanelDataSource.PVGIS }
                }
                val savedId: Long = when {
                    isEditMode -> {
                        // Update existing scenario in place
                        val existing = repository.getScenarioComponentsForScenarioID(scenarioId)
                        existing.scenario?.also { sc ->
                            sc.scenarioName = b.scenarioName
                            sc.setHasEVCharges(b.evEntries.isNotEmpty())
                            sc.setHasEVDivert(b.evDivertEntries.isNotEmpty())
                            sc.setHasInverters(b.inverterEntries.isNotEmpty())
                            sc.setHasPanels(b.panelEntries.isNotEmpty())
                            sc.setHasBatteries(b.batteryEntries.isNotEmpty())
                            sc.setHasLoadShifts(b.expandedLoadShifts().isNotEmpty())
                            sc.setHasDischarges(b.expandedDischarges().isNotEmpty())
                            sc.setHasHWSystem(b.hwSystem != null)
                            sc.setHasHWSchedules(b.hwSchedules.isNotEmpty())
                            sc.setHasHWDivert(b.hwDivert.active)
                        }?.let { repository.updateScenario(it) }
                        repository.saveLoadProfile(scenarioId, b.toLoadProfile())
                        // Replace EV charges
                        existing.evCharges?.forEach { ev ->
                            repository.deleteEVChargeFromScenario(ev.evChargeIndex, scenarioId)
                        }
                        b.evEntries.forEach { entry ->
                            repository.saveEVChargeForScenario(scenarioId, entry.toEvCharge())
                        }
                        // Replace EV diverts
                        existing.evDiverts?.forEach { d ->
                            repository.deleteEVDivertFromScenario(d.evDivertIndex, scenarioId)
                        }
                        b.evDivertEntries.forEach { entry ->
                            repository.saveEVDivertForScenario(scenarioId, entry.toEvDivert())
                        }
                        // Replace inverters
                        existing.inverters?.forEach { inv ->
                            repository.deleteInverterFromScenario(inv.inverterIndex, scenarioId)
                        }
                        b.inverterEntries.forEach { entry ->
                            repository.saveInverter(scenarioId, entry.toInverter())
                        }
                        // Replace batteries
                        existing.batteries?.forEach { bat ->
                            repository.deleteBatteryFromScenario(bat.batteryIndex, scenarioId)
                        }
                        b.batteryEntries.forEach { entry ->
                            repository.saveBatteryForScenario(scenarioId, entry.toBattery())
                        }
                        // Replace battery charge schedules (LoadShift) — replicated per inverter
                        existing.loadShifts?.forEach { ls ->
                            repository.deleteLoadShiftFromScenario(ls.loadShiftIndex, scenarioId)
                        }
                        b.expandedLoadShifts().forEach { ls ->
                            repository.saveLoadShiftForScenario(scenarioId, ls)
                        }
                        // Replace battery discharge schedules (DischargeToGrid) — replicated per inverter
                        existing.discharges?.forEach { d ->
                            repository.deleteDischargeFromScenario(d.d2gIndex, scenarioId)
                        }
                        b.expandedDischarges().forEach { d ->
                            repository.saveDischargeForScenario(scenarioId, d)
                        }
                        // Replace hot-water system, schedules, and divert
                        if (b.hwSystem != null) {
                            repository.saveHWSystemForScenario(scenarioId, b.hwSystem.toHwSystem())
                        }
                        existing.hwSchedules?.forEach { hs ->
                            repository.deleteHWScheduleFromScenario(hs.hwScheduleIndex, scenarioId)
                        }
                        b.hwSchedules.forEach { entry ->
                            repository.saveHWScheduleForScenario(scenarioId, entry.toHwSchedule())
                        }
                        if (b.hwDivert.active) {
                            repository.saveHWDivert(scenarioId, b.hwDivert.toHwDivert())
                        }
                        // Panels: update existing (preserving data), delete removed, insert new
                        val keptIds = b.panelEntries.filter { it.panelIndex > 0L }.map { it.panelIndex }.toSet()
                        (existing.panels ?: emptyList())
                            .filter { it.panelIndex !in keptIds }
                            .forEach { repository.deletePanelFromScenario(it.panelIndex, scenarioId) }
                        b.panelEntries.filter { it.panelIndex > 0L }
                            .forEach { entry ->
                                repository.updatePanel(entry.toPanel())
                                // pvDataSource / source config are wizard-only fields (not persisted
                                // on the Panel row). If the user has configured a fetchable source
                                // for an existing panel in this session, kick off the data fetch.
                                if (entry.pvDataSource != PanelDataSource.NONE) {
                                    triggerPanelDataFetch(entry, entry.panelIndex)
                                }
                            }
                        b.panelEntries.filter { it.panelIndex == 0L }
                            .forEach { entry ->
                                val panelId = repository.savePanel(scenarioId, entry.toPanel())
                                triggerPanelDataFetch(entry, panelId)
                            }
                        scenarioId
                    }
                    b.isLinked -> {
                        // Full link: create shell scenario, then link load profile, EV, inverters,
                        // panels, batteries and battery schedules
                        val newId = repository.insertScenarioAndReturnID(b.toScenarioShell(), false)
                        repository.linkLoadProfileFromScenario(b.basedOnId, newId)
                        repository.linkEVChargeFromScenario(b.basedOnId, newId)
                        repository.linkInverterFromScenario(b.basedOnId, newId)
                        repository.linkPanelFromScenario(b.basedOnId, newId)
                        repository.linkBatteryFromScenario(b.basedOnId, newId)
                        repository.linkLoadShiftFromScenario(b.basedOnId, newId)
                        repository.linkDischargeFromScenario(b.basedOnId, newId)
                        repository.linkHWSystemFromScenario(b.basedOnId, newId)
                        repository.linkHWScheduleFromScenario(b.basedOnId, newId)
                        // No linkHWDivert in repository — replay from builder state instead
                        if (b.hwDivert.active) {
                            repository.saveHWDivert(newId, b.hwDivert.toHwDivert())
                        }
                        newId
                    }
                    b.loadSource == LoadSource.LINKED -> {
                        // Load-profile link only: create scenario with EV+inverters+panels from builder, link load
                        val newId = repository.insertScenarioAndReturnID(b.toScenarioShellWithEV(), false)
                        repository.linkLoadProfileFromScenario(b.basedOnId, newId)
                        b.panelEntries.forEach { entry ->
                            val panelId = repository.savePanel(newId, entry.toPanel())
                            triggerPanelDataFetch(entry, panelId)
                        }
                        newId
                    }
                    else -> {
                        // NEW or COPY: save panels separately to capture their IDs for data fetch
                        val newId = repository.insertScenarioAndReturnID(b.toScenarioComponents(), false)
                        b.panelEntries.forEach { entry ->
                            val panelId = repository.savePanel(newId, entry.toPanel())
                            triggerPanelDataFetch(entry, panelId)
                        }
                        newId
                    }
                }
                android.util.Log.i(PanelSourceFetchWorker.TAG, "save(): completed savedId=$savedId")
                _saveResult.value = WizardSaveResult.Done(savedId, runSimulation, pvgisCount)
            } catch (e: Exception) {
                android.util.Log.e(PanelSourceFetchWorker.TAG, "save() threw — wizard will silently dismiss", e)
                _saveResult.value = WizardSaveResult.Idle
            }
        }
    }
}
