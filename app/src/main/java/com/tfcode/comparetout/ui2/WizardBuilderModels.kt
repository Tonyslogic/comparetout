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
import com.tfcode.comparetout.R
import com.tfcode.comparetout.TOUTCApplication
import com.tfcode.comparetout.model.IntHolder
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.json.JsonTools
import com.tfcode.comparetout.model.json.scenario.ScenarioJsonFile
import com.tfcode.comparetout.model.scenario.Battery
import com.tfcode.comparetout.model.scenario.HeatPump
import com.tfcode.comparetout.model.scenario.ChargeModel
import com.tfcode.comparetout.model.scenario.DOWDist
import com.tfcode.comparetout.model.scenario.DischargeToGrid
import com.tfcode.comparetout.model.scenario.EVCharge
import com.tfcode.comparetout.model.scenario.EVDivert
import com.tfcode.comparetout.model.scenario.HWDivert
import com.tfcode.comparetout.model.scenario.HWSchedule
import com.tfcode.comparetout.model.scenario.HWSystem
import com.tfcode.comparetout.model.scenario.HWUse
import com.tfcode.comparetout.model.scenario.HourlyDist
import com.tfcode.comparetout.model.scenario.Inverter
import com.tfcode.comparetout.model.scenario.LoadProfile
import com.tfcode.comparetout.model.scenario.LoadProfileData
import com.tfcode.comparetout.model.scenario.LoadShift
import com.tfcode.comparetout.model.scenario.MonthHolder
import com.tfcode.comparetout.model.scenario.MonthlyDist
import com.tfcode.comparetout.model.scenario.Panel
import com.tfcode.comparetout.model.scenario.PanelPVSummary
import com.tfcode.comparetout.model.scenario.Scenario
import com.tfcode.comparetout.model.scenario.ScenarioComponents
import com.tfcode.comparetout.scenario.loadprofile.StandardLoadProfiles
import com.tfcode.comparetout.scenario.sim.SimTime
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

/*
 * Wizard builder models: per-component wizard entries with their entity
 * mappers, WizardBuilder, and WizardSaveResult — extracted verbatim from
 * UI2WizardViewModel.kt (mega-refactor B4a). Imports inherited; unused are cosmetic.
 */

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
        d.name = name
        d.isActive = active
        d.isEv1st = ev1st
        d.begin = beginHour
        d.end = endHour
        d.dailyMax = dailyMax
        d.minimum = minimum
        d.days = IntHolder().also { it.ints = ArrayList(days) }
        d.months = MonthHolder().also { it.months = ArrayList(months) }
    }
}

internal fun EVDivert.toWizardEvDivertEntry() = WizardEvDivertEntry(
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

internal fun EVCharge.toWizardEvEntry() = WizardEvEntry(
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

internal fun Inverter.toWizardInverterEntry() = WizardInverterEntry(
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

internal fun Battery.toWizardBatteryEntry() = WizardBatteryEntry(
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

/**
 * Wizard state for a heat pump (Phase 5 of plans/hp/plan.md). Mirrors the entity 1:1 so {@code toHeatPump}
 * is mechanical. The hourly/day-of-week heating profiles are carried for round-trip but not yet editable in
 * the wizard (deferred affordance — they default to flat); everything else is exposed in the section.
 */
data class WizardHeatPumpEntry(
    val id: String = UUID.randomUUID().toString(),
    val heatPumpIndex: Long = 0L,
    val fuelType: String = "Kerosene/Oil",
    // User-editable numerics are Strings (raw text in state, parsed on save) — matches the rest of the wizard.
    val fuelAnnual: String = "2300",
    val calorificValue: Double = 10.35,   // internal: set by fuel type, not exposed in the UI
    val boilerEfficiencyPct: String = "80",
    val dhwAnnualKWh: Double = 2000.0,    // internal: fixed DHW carve-out for basic mode
    val spaceHeatingPct: String = "",     // advanced: blank ⇒ use the DHW default
    val floorAreaM2: String = "150",      // new build (fuelType "None"): heated floor area (m²)
    val heatLossIndex: String = "1.0",    // new build: whole-house HLI (W/K/m², fabric + ventilation)
    val desiredIndoorTemp: String = "20",
    val currentIndoorTemp: String = "20",
    val balancePoint: String = "15.5",
    val alphaWind: Double = 0.03,         // set by the Basic-tab "When the wind blows…" chip group
    val heatingSeasonStart: Int? = null,
    val heatingSeasonEnd: Int? = null,
    val copRated: String = "4.2",
    val copRefTemp: String = "7",
    val copSlope: String = "0.08",
    val scop: String = "3.6",
    val capacityKw: String = "7",
    val backupHeater: Boolean = true,
    val latitude: Double = 53.49,
    val longitude: Double = -10.015,
    val weatherSource: String = "sample",
    val hourlyDist: List<Double>? = null,
    val dowDist: List<Double>? = null
) {
    private fun String.d(default: Double) = toDoubleOrNull() ?: default

    fun toHeatPump(): HeatPump = HeatPump().also { hp ->
        if (heatPumpIndex > 0L) hp.heatPumpIndex = heatPumpIndex
        hp.fuelType = fuelType
        hp.fuelAnnual = fuelAnnual.d(2300.0)
        hp.calorificValue = calorificValue
        hp.boilerEfficiency = boilerEfficiencyPct.d(80.0) / 100.0
        hp.dhwAnnualKWh = dhwAnnualKWh
        hp.spaceHeatingFraction = spaceHeatingPct.toDoubleOrNull()?.takeIf { it > 0.0 }?.let { it / 100.0 }
        // Fabric anchor only applies to a new build; zero it for fuel scenarios so the engine's
        // "area & HLI both > 0" presence check never fires on a stale value.
        val newBuild = fuelType == "None"
        hp.floorAreaM2 = if (newBuild) floorAreaM2.d(0.0) else 0.0
        hp.heatLossIndex = if (newBuild) heatLossIndex.d(0.0) else 0.0
        hp.desiredIndoorTemp = desiredIndoorTemp.d(20.0)
        hp.currentIndoorTemp = currentIndoorTemp.d(20.0)
        hp.balancePoint = balancePoint.d(15.5)
        hp.alphaWind = alphaWind
        hp.heatingSeasonStart = heatingSeasonStart
        hp.heatingSeasonEnd = heatingSeasonEnd
        hp.copRated = copRated.d(4.2)
        hp.copRefTemp = copRefTemp.d(7.0)
        hp.copSlope = copSlope.d(0.08)
        hp.scop = scop.d(3.6)
        hp.capacityKw = capacityKw.d(7.0)
        hp.isBackupHeater = backupHeater
        hp.latitude = latitude
        hp.longitude = longitude
        hp.weatherSource = weatherSource
        hourlyDist?.let { hp.hourlyDist = HourlyDist().also { d -> d.dist = ArrayList(it) } }
        dowDist?.let { hp.dowDist = DOWDist().also { d -> d.dowDist = ArrayList(it) } }
    }
}

internal fun fmtNum(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

internal fun HeatPump.toWizardHeatPumpEntry() = WizardHeatPumpEntry(
    id = if (heatPumpIndex > 0) heatPumpIndex.toString() else UUID.randomUUID().toString(),
    heatPumpIndex = heatPumpIndex,
    fuelType = fuelType ?: "Kerosene/Oil",
    fuelAnnual = fmtNum(fuelAnnual),
    calorificValue = calorificValue,
    boilerEfficiencyPct = fmtNum(boilerEfficiency * 100.0),
    dhwAnnualKWh = dhwAnnualKWh,
    spaceHeatingPct = spaceHeatingFraction?.let { fmtNum(it * 100.0) } ?: "",
    floorAreaM2 = if (floorAreaM2 > 0) fmtNum(floorAreaM2) else "150",
    heatLossIndex = if (heatLossIndex > 0) fmtNum(heatLossIndex) else "1.0",
    desiredIndoorTemp = fmtNum(desiredIndoorTemp),
    currentIndoorTemp = fmtNum(currentIndoorTemp),
    balancePoint = fmtNum(balancePoint),
    alphaWind = alphaWind,
    heatingSeasonStart = heatingSeasonStart,
    heatingSeasonEnd = heatingSeasonEnd,
    copRated = fmtNum(copRated),
    copRefTemp = fmtNum(copRefTemp),
    copSlope = fmtNum(copSlope),
    scop = fmtNum(scop),
    capacityKw = fmtNum(capacityKw),
    backupHeater = isBackupHeater,
    latitude = latitude,
    longitude = longitude,
    weatherSource = weatherSource ?: "sample",
    hourlyDist = hourlyDist?.dist,
    dowDist = dowDist?.dowDist
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

internal fun LoadShift.toWizardBatteryChargeEntry() = WizardBatteryChargeEntry(
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

internal fun DischargeToGrid.toWizardBatteryDischargeEntry() = WizardBatteryDischargeEntry(
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

internal fun HWSystem.toWizardHwSystemEntry() = WizardHwSystemEntry(
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

internal fun HWSchedule.toWizardHwScheduleEntry() = WizardHwScheduleEntry(
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
    fun toHwDivert(): HWDivert = HWDivert().also { d -> d.isActive = active }
}

internal fun HWDivert.toWizardHwDivertEntry() = WizardHwDivertEntry(active = isActive)

enum class PanelDataSource { NONE, PVGIS, SOURCE }

data class WizardPanelEntry(
    val id: String = UUID.randomUUID().toString(),
    val panelIndex: Long = 0L,
    val panelName: String = "String 1",
    val panelCount: Int = 7,
    val panelkWp: Int = 325,
    // PVGIS system loss % (non-temperature: cabling, inverter, soiling…); PVGIS does the temperature derate.
    // Per-panel so a change re-fetches via panelFetchInputsChanged. 14 = PVGIS default.
    val systemLoss: Int = 14,
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
        p.systemLoss = systemLoss
        p.azimuth = azimuth
        p.slope = slope
        p.latitude = latitude
        p.longitude = longitude
        p.inverter = inverterName.ifBlank { "Inverter" }
        p.mppt = mppt
        p.connectionMode = connectionMode
        // Persist the data-source provenance (DB v11). A historical "Source" records its real window, which
        // drives the heat-pump CDS weather dates; PVGIS/None stay on the 2001 reference year. Without this the
        // panel reverted to the PVGIS default on every save and CDS never used the source's real dates.
        when (pvDataSource) {
            PanelDataSource.SOURCE -> {
                p.dataSource = pvSourceSysSn.ifBlank { "Source" }
                p.dataStartDate = pvSourceFrom.ifBlank { "2001-01-01" }
                p.dataEndDate = pvSourceTo.ifBlank { "2001-12-31" }
            }
            PanelDataSource.PVGIS -> {
                p.dataSource = "PVGIS"; p.dataStartDate = "2001-01-01"; p.dataEndDate = "2001-12-31"
            }
            PanelDataSource.NONE -> {
                p.dataSource = "None"; p.dataStartDate = "2001-01-01"; p.dataEndDate = "2001-12-31"
            }
        }
    }
}

internal fun Panel.toWizardPanelEntry(): WizardPanelEntry {
    // Reselect the PV data-source from what's persisted (DB v11): PVGIS / None / a historical Source.
    val label = dataSource ?: "PVGIS"
    val source = when {
        label.equals("PVGIS", ignoreCase = true) -> PanelDataSource.PVGIS
        label.isBlank() || label.equals("None", true) || label.equals("Unknown", true) -> PanelDataSource.NONE
        else -> PanelDataSource.SOURCE
    }
    return WizardPanelEntry(
        id = panelIndex.toString(),
        panelIndex = panelIndex,
        panelName = panelName ?: "String 1",
        panelCount = panelCount,
        panelkWp = panelkWp,
        systemLoss = systemLoss,
        azimuth = azimuth,
        slope = slope,
        latitude = latitude,
        longitude = longitude,
        inverterName = inverter ?: "",
        mppt = mppt,
        connectionMode = connectionMode,
        pvDataSource = source,
        pvSourceSysSn = if (source == PanelDataSource.SOURCE) label else "",
        pvSourceFrom = if (source == PanelDataSource.SOURCE) (dataStartDate ?: "") else "",
        pvSourceTo = if (source == PanelDataSource.SOURCE) (dataEndDate ?: "") else ""
    )
}

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
    // Absolute-year load: when set, save() materialises the source's REAL half-hourly/5-min series for
    // [loadSourceFrom]..[loadSourceTo] onto the 2001 grid as loadprofiledata (in addition to the distribution),
    // so the sim runs on the actual measured load instead of a synthesised distribution.
    val loadAbsoluteYear: Boolean = false,
    val loadSourceSysSn: String = "",
    val loadSourceImporter: ComparisonUIViewModel.Importer? = null,
    val loadSourceFrom: String = "",
    val loadSourceTo: String = "",
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
    val hwDivert: WizardHwDivertEntry = WizardHwDivertEntry(),
    // Heat Pump
    val heatPumpEntries: List<WizardHeatPumpEntry> = emptyList()
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
        ).also { it.heatPumps = heatPumpEntries.map { e -> e.toHeatPump() } }
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
        ).also { it.heatPumps = heatPumpEntries.map { e -> e.toHeatPump() } }
    }
}


sealed class WizardSaveResult {
    object Idle : WizardSaveResult()
    object Saving : WizardSaveResult()
    data class Done(val scenarioId: Long, val runSimulation: Boolean, val pvgisStringsQueued: Int = 0,
                    val needsWeatherFetch: Boolean = false) : WizardSaveResult()
    /** The save could not be completed (e.g. a duplicate name). Surfaced to the user instead of dismissing silently. */
    data class Failed(val message: String) : WizardSaveResult()
}
