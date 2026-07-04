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

private fun HeatPump.toWizardHeatPumpEntry() = WizardHeatPumpEntry(
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
    fun toHwDivert(): HWDivert = HWDivert().also { d -> d.isActive = active }
}

private fun HWDivert.toWizardHwDivertEntry() = WizardHwDivertEntry(active = isActive)

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

private fun Panel.toWizardPanelEntry(): WizardPanelEntry {
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

private const val KEY_NOVICE_MODE = "wizard_novice_mode"

sealed class WizardSaveResult {
    object Idle : WizardSaveResult()
    object Saving : WizardSaveResult()
    data class Done(val scenarioId: Long, val runSimulation: Boolean, val pvgisStringsQueued: Int = 0,
                    val needsWeatherFetch: Boolean = false) : WizardSaveResult()
    /** The save could not be completed (e.g. a duplicate name). Surfaced to the user instead of dismissing silently. */
    data class Failed(val message: String) : WizardSaveResult()
}

@HiltViewModel
class UI2WizardViewModel @Inject constructor(
    private val repository: ToutcRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Mutable so a successful Save can adopt the new id and flip the wizard into edit mode. Without this,
    // after saving a brand-new (import/NEW) scenario the wizard stayed "new": the saved name now exists in
    // the list, so the uniqueness check flagged a false "name already in use", and a second Save would
    // re-INSERT and hit the scenarioName UNIQUE constraint. Exposed as a flow so the name check recomposes
    // when it changes.
    private val _scenarioId = MutableStateFlow(savedStateHandle["ScenarioID"] ?: -1L)
    val scenarioIdFlow: StateFlow<Long> = _scenarioId
    val scenarioId: Long get() = _scenarioId.value
    val isEditMode: Boolean get() = _scenarioId.value != -1L
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
    val panelPvSummary: LiveData<List<PanelPVSummary>> = repository.panelDataSummary

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
    val allScenarios: LiveData<List<Scenario>> = repository.allScenarios

    // Combined list of available data sources (AlphaESS + ESBN + HA)
    private val _availableSources = MediatorLiveData<List<SourceDateRange>>()
    val availableSources: LiveData<List<SourceDateRange>> = _availableSources

    init {
        val alphaRanges = repository.liveDateRanges
        val esbnRanges  = repository.esbnLiveDateRanges
        val haRanges    = repository.haLiveDateRanges

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
                // The shared ranges query returns every sysSn namespace; classify by name.
                val type = when {
                    r.sysSn == "HomeAssistant" -> ComparisonUIViewModel.Importer.HOME_ASSISTANT
                    r.sysSn.startsWith("Octopus-") -> ComparisonUIViewModel.Importer.OCTOPUS
                    else -> ComparisonUIViewModel.Importer.ESBNHDF
                }
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
            val stored = app.dataStore
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
                val name = prevName.ifBlank { _builder.value.scenarioName }
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
            hwDivert = c.hwDivert?.toWizardHwDivertEntry() ?: WizardHwDivertEntry(),
            heatPumpEntries = c.heatPumps?.map { it.toWizardHeatPumpEntry() } ?: emptyList()
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

    fun replaceHeatPumpsFromJson(
        jsons: List<com.tfcode.comparetout.model.json.scenario.HeatPumpJson>
    ) {
        val hpDomain = JsonTools.createHeatPumpList(ArrayList(jsons))
        updateBuilder { b ->
            b.copy(heatPumpEntries = hpDomain.map { it.toWizardHeatPumpEntry() })
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

    // Heat pump entries (v1 is a single heat pump, but kept list-shaped to mirror the other components).
    fun addHeatPumpEntry() = updateBuilder { b ->
        // Reuse the PV location if one was entered, so the weather aligns with the solar by default.
        val pvLat = b.panelEntries.firstOrNull()?.latitude
        val pvLon = b.panelEntries.firstOrNull()?.longitude
        b.copy(heatPumpEntries = b.heatPumpEntries + WizardHeatPumpEntry(
            latitude = pvLat ?: 53.49,
            longitude = pvLon ?: -10.015
        ))
    }

    fun removeHeatPumpEntry(id: String) =
        updateBuilder { it.copy(heatPumpEntries = it.heatPumpEntries.filter { e -> e.id != id }) }

    fun updateHeatPumpEntry(id: String, transform: (WizardHeatPumpEntry) -> WizardHeatPumpEntry) =
        updateBuilder { b ->
            b.copy(heatPumpEntries = b.heatPumpEntries.map { if (it.id == id) transform(it) else it })
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

    /**
     * True if the fetch-relevant inputs differ between the persisted panel and the about-to-save one — i.e. the
     * user changed the data source, its date range, the orientation or the array size this session. Compared on
     * [WizardPanelEntry.toPanel] output so it round-trips exactly for an untouched reloaded panel (→ no fetch).
     */
    private fun panelFetchInputsChanged(before: com.tfcode.comparetout.model.scenario.Panel?,
                                        after: com.tfcode.comparetout.model.scenario.Panel): Boolean {
        if (before == null) return true
        return before.latitude != after.latitude ||
            before.longitude != after.longitude ||
            before.slope != after.slope ||
            before.azimuth != after.azimuth ||
            before.panelCount != after.panelCount ||
            before.panelkWp != after.panelkWp ||
            before.systemLoss != after.systemLoss ||
            before.dataSource != after.dataSource ||
            before.dataStartDate != after.dataStartDate ||
            before.dataEndDate != after.dataEndDate
    }

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
        fillGaps: Boolean,
        absoluteYear: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isDeriving.value = true
            try {
                val rows = repository.getAlphaESSTransformedData(sysSn, from.toString(), to.toString())
                if (rows.isEmpty()) return@launch

                // HA sources with classified individual devices derive net of the EV / hot-water
                // slices (they come back as modelled components) — plans/ha/design.md §1c.
                val haRoles = if (importerType == ComparisonUIViewModel.Importer.HOME_ASSISTANT)
                    haDeviceRoles() else null

                val hourlySum  = DoubleArray(24)
                val dailySum   = DoubleArray(7)
                val monthlySum = DoubleArray(12)
                var totalEnergy = 0.0

                rows.forEach { row ->
                    val e = when {
                        importerType == ComparisonUIViewModel.Importer.ESBNHDF ||
                            importerType == ComparisonUIViewModel.Importer.OCTOPUS -> row.buy
                        haRoles != null -> (row.load -
                                (if (haRoles.subtractEv) row.evActual else 0.0) -
                                (if (haRoles.subtractHw) row.hwActual else 0.0)).coerceAtLeast(0.0)
                        else -> row.load
                    }
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
                    ComparisonUIViewModel.Importer.OCTOPUS         -> "Octopus: $sysSn"
                    else -> sysSn
                }

                _builder.value = _builder.value.copy(
                    loadSource         = LoadSource.SOURCE,
                    isLoadLinked       = false,
                    distributionSource = if (absoluteYear) "$sourceName ($from→$to, actual)" else sourceName,
                    annualUsage        = "%.1f".format(scaledAnnual),
                    patchWithSlp       = fillGaps,
                    loadProfileHourly  = hourly,
                    loadProfileDaily   = daily,
                    loadProfileMonthly = monthly,
                    // Absolute-year mode keeps the source identity so save() can write the REAL series; averages
                    // mode clears it so an earlier absolute selection can't leak into a plain distribution.
                    loadAbsoluteYear   = absoluteYear,
                    loadSourceSysSn    = if (absoluteYear) sysSn else "",
                    loadSourceImporter = if (absoluteYear) importerType else null,
                    loadSourceFrom     = if (absoluteYear) from.toString() else "",
                    loadSourceTo       = if (absoluteYear) to.toString() else ""
                )

                // Re-add the removed slices as modelled components: infer the schedule from
                // the measured pattern (OQ-1) — only into empty sections, never over a user's
                // own entries.
                if (haRoles != null) prefillHaDeviceComponents(haRoles, rows)
            } finally {
                _isDeriving.value = false
            }
        }
    }

    /**
     * Classified HA "Individual devices": whether EV / hot-water roles exist and whether their
     * slices still need subtracting from a derived load (an adjust-flagged device was already
     * removed at ingestion, so subtracting again would double-count).
     */
    private data class HaDeviceRoles(
        val evClassified: Boolean,
        val hwClassified: Boolean,
        val subtractEv: Boolean,
        val subtractHw: Boolean
    )

    private fun haDeviceRoles(): HaDeviceRoles {
        val none = HaDeviceRoles(
            evClassified = false, hwClassified = false, subtractEv = false, subtractHw = false)
        val app = context.applicationContext as? com.tfcode.comparetout.TOUTCApplication ?: return none
        val json = try {
//            val key = androidx.datastore.preferences.core.PreferencesKeys.stringKey("ha_sensors")
            val key = stringPreferencesKey("ha_sensors")
            app.dataStore.data().firstOrError()
                .map { prefs -> prefs[key]!! }
                .onErrorReturnItem("{}")
                .blockingGet()
        } catch (e: Exception) {
            return none
        }
        val sensors = runCatching {
            com.google.gson.Gson().fromJson(
                json, com.tfcode.comparetout.importers.homeassistant.EnergySensors::class.java)
        }.getOrNull() ?: return none
        var ev = false
        var hw = false
        var evAdjusted = false
        var hwAdjusted = false
        sensors.classifiedDevices.forEach { device ->
            when (device.role) {
                com.tfcode.comparetout.importers.homeassistant.DeviceSensor.Role.EV -> {
                    ev = true
                    if (device.adjust) evAdjusted = true
                }
                com.tfcode.comparetout.importers.homeassistant.DeviceSensor.Role.HOT_WATER -> {
                    hw = true
                    if (device.adjust) hwAdjusted = true
                }
                else -> {}
            }
        }
        return HaDeviceRoles(ev, hw, ev && !evAdjusted, hw && !hwAdjusted)
    }

    /** Inferred usage pattern of one HA device role (same heuristics as the HA GenerationWorker). */
    private data class HaUseProfile(
        val windows: List<Pair<Int, Int>>,  // beginHour to endHourExclusive, split at midnight
        val drawKw: Double,
        val days: List<Int>,                // 0..6, Sunday = 0 (schedule convention)
        val months: List<Int>               // 1..12
    )

    private fun inferHaUseProfile(
        rows: List<com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData>,
        slice: (com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData) -> Double
    ): HaUseProfile? {
        val hourEnergy = DoubleArray(24)
        val dowEnergy = DoubleArray(7)
        val monthEnergy = DoubleArray(12)
        val hourBuckets = HashMap<String, Double>()
        var total = 0.0
        rows.forEach { row ->
            val kwh = slice(row)
            if (kwh <= 0.0) return@forEach
            val date = runCatching { LocalDate.parse(row.date) }.getOrNull() ?: return@forEach
            val hour = row.minute.substringBefore(":").toIntOrNull() ?: return@forEach
            hourEnergy[hour] += kwh
            dowEnergy[date.dayOfWeek.value % 7] += kwh
            monthEnergy[date.monthValue - 1] += kwh
            hourBuckets.merge("${row.date}#$hour", kwh) { a, b -> a + b }
            total += kwh
        }
        if (total < 10.0) return null

        var drawKw = 0.0
        hourBuckets.values.forEach { drawKw = maxOf(drawKw, it) }
        drawKw = maxOf(1.0, Math.round(drawKw * 10.0) / 10.0)

        var peak = 0
        for (h in 1 until 24) if (hourEnergy[h] > hourEnergy[peak]) peak = h
        val include = BooleanArray(24) { hourEnergy[it] >= 0.25 * hourEnergy[peak] }
        var left = peak
        var right = peak
        var span = 1
        while (span < 24 && include[(left + 23) % 24]) { left = (left + 23) % 24; span++ }
        while (span < 24 && include[(right + 1) % 24]) { right = (right + 1) % 24; span++ }
        val windows = if (left <= right) listOf(left to right + 1)
        else listOf(left to 24, 0 to right + 1) // schedule windows don't wrap midnight

        val maxDow = dowEnergy.maxOrNull() ?: 0.0
        val days = (0..6).filter { dowEnergy[it] >= 0.2 * maxDow }
        val maxMonth = monthEnergy.maxOrNull() ?: 0.0
        val months = (0..11).filter { monthEnergy[it] >= 0.15 * maxMonth }.map { it + 1 }
        return HaUseProfile(windows, drawKw, days, months)
    }

    private fun prefillHaDeviceComponents(
        roles: HaDeviceRoles,
        rows: List<com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData>
    ) {
        if (roles.evClassified && _builder.value.evEntries.isEmpty()) {
            inferHaUseProfile(rows) { it.evActual }?.let { p ->
                _builder.value = _builder.value.copy(evEntries = p.windows.map { (b, e) ->
                    WizardEvEntry(name = "EV (Home Assistant)", startHour = b, endHour = e,
                        drawKw = p.drawKw, days = p.days, months = p.months)
                })
            }
        }
        if (roles.hwClassified && _builder.value.hwSchedules.isEmpty()) {
            inferHaUseProfile(rows) { it.hwActual }?.let { p ->
                _builder.value = _builder.value.copy(
                    hwSystem = _builder.value.hwSystem
                        ?: WizardHwSystemEntry(rate = p.drawKw.coerceIn(1.0, 3.0)),
                    hwSchedules = p.windows.map { (b, e) ->
                        WizardHwScheduleEntry(name = "Hot water (Home Assistant)",
                            beginHour = b, endHour = e, days = p.days, months = p.months)
                    })
            }
        }
    }

    /**
     * Write the source's REAL measured load for [WizardBuilder.loadSourceFrom]..[WizardBuilder.loadSourceTo]
     * onto the 2001 simulation grid as loadprofiledata — energy normalised to 2001 by day-of-year +
     * time-of-day, on the canonical UTC millis axis, the same mapping the importer's generateLoadProfileData
     * uses. Called on save for an absolute-year load profile so the sim runs on the actual measured series
     * (in addition to the distribution already on the LoadProfile). Create-path only: the profile is fresh,
     * so the plain @Insert can't collide and the load-data generator skips a profile that already has rows.
     */
    private fun materializeAbsoluteYearLoadData(scenarioId: Long, b: WizardBuilder) {
        val lpId = repository.getScenarioComponentsForScenarioID(scenarioId)?.loadProfile?.loadProfileIndex
            ?: return
        val rows = repository.getAlphaESSTransformedData(b.loadSourceSysSn, b.loadSourceFrom, b.loadSourceTo)
        if (rows.isEmpty()) return
        // Meter-only sources (ESBN, Octopus) have no load channel, so estimate
        // consumption from grid import (same as the averages path).
        val esbn = b.loadSourceImporter == ComparisonUIViewModel.Importer.ESBNHDF ||
                b.loadSourceImporter == ComparisonUIViewModel.Importer.OCTOPUS
        // HA absolute-year data is written net of modelled device slices, matching the
        // distribution derived in deriveLoadProfileFromSource.
        val haRoles = if (b.loadSourceImporter == ComparisonUIViewModel.Importer.HOME_ASSISTANT)
            haDeviceRoles() else null
        // Index real readings by day-of-year → (HH:mm → load); the 2001 walk below maps by the same key.
        val byDoy = HashMap<Int, HashMap<String, Double>>()
        rows.forEach { r ->
            val e = when {
                esbn -> r.buy
                haRoles != null -> (r.load -
                        (if (haRoles.subtractEv) r.evActual else 0.0) -
                        (if (haRoles.subtractHw) r.hwActual else 0.0)).coerceAtLeast(0.0)
                else -> r.load
            }
            val d = runCatching { LocalDate.parse(r.date) }.getOrNull() ?: return@forEach
            byDoy.getOrPut(d.dayOfYear) { HashMap() }[r.minute] = e
        }
        val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val minFmt  = DateTimeFormatter.ofPattern("HH:mm")
        val out = ArrayList<LoadProfileData>()
        var active = LocalDateTime.of(2001, 1, 1, 0, 0)
        val end    = LocalDateTime.of(2002, 1, 1, 0, 0)
        while (active.isBefore(end)) {
            val hhmm = active.format(minFmt)
            val row = LoadProfileData()
            row.loadProfileID    = lpId
            row.do2001           = active.dayOfYear
            row.date             = active.format(dateFmt)
            row.minute           = hhmm
            row.dow              = active.dayOfWeek.value
            row.mod              = active.hour * 60 + active.minute
            row.millisSinceEpoch = SimTime.toEpochMillis(active, ZoneOffset.UTC)
            // No reading for this 2001 slot (gap, or a coarser source than 5-min) → 0, matching the importer.
            row.load             = byDoy[active.dayOfYear]?.get(hhmm) ?: 0.0
            out.add(row)
            active = active.plusMinutes(5)
        }
        repository.createLoadProfileDataEntries(out)
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
                    "batteryEntries=${b.batteryEntries.size} chargeEntries=${b.batteryChargeEntries.size} " +
                    "hwSystem=${b.hwSystem != null} " +
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
                            sc.isHasEVCharges = b.evEntries.isNotEmpty()
                            sc.isHasEVDivert = b.evDivertEntries.isNotEmpty()
                            sc.isHasInverters = b.inverterEntries.isNotEmpty()
                            sc.isHasPanels = b.panelEntries.isNotEmpty()
                            sc.isHasBatteries = b.batteryEntries.isNotEmpty()
                            sc.isHasLoadShifts = b.expandedLoadShifts().isNotEmpty()
                            sc.isHasDischarges = b.expandedDischarges().isNotEmpty()
                            sc.isHasHWSystem = b.hwSystem != null
                            sc.isHasHWSchedules = b.hwSchedules.isNotEmpty()
                            sc.isHasHWDivert = b.hwDivert.active
                            sc.isHasHeatPump = b.heatPumpEntries.isNotEmpty()
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
                        // Replace batteries. The delete above removes the scenario2battery JUNCTION (not the
                        // battery row). saveBatteryForScenario only (re)creates that junction on its insert
                        // (index == 0) branch; an entry that still carries its old batteryIndex takes the
                        // update-only branch and is left ORPHANED — the battery then vanishes from the scenario
                        // (e.g. after a PVGIS edit-save). Reset the index so it is re-inserted with a fresh
                        // junction, exactly as load-shift/discharge already do (their toX() never carry an index).
                        existing.batteries?.forEach { bat ->
                            repository.deleteBatteryFromScenario(bat.batteryIndex, scenarioId)
                        }
                        b.batteryEntries.forEach { entry ->
                            repository.saveBatteryForScenario(scenarioId, entry.toBattery().also { it.batteryIndex = 0 })
                        }
                        // Replace heat pumps. Reset the index so the scenario2heatpump junction is re-created
                        // on the insert branch (same orphan-avoidance reason as batteries above).
                        existing.heatPumps?.forEach { hp ->
                            repository.deleteHeatPumpFromScenario(hp.heatPumpIndex, scenarioId)
                        }
                        b.heatPumpEntries.forEach { entry ->
                            repository.saveHeatPumpForScenario(scenarioId, entry.toHeatPump().also { it.heatPumpIndex = 0 })
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
                                val before = existing.panels?.firstOrNull { it.panelIndex == entry.panelIndex }
                                val after = entry.toPanel()
                                repository.updatePanel(after)
                                // Only (re)fetch when the source or its inputs actually changed this session.
                                // Reloaded panels now round-trip their data-source (DB v11), so without this
                                // guard every unrelated edit-save would needlessly re-download PVGIS/source data.
                                if (entry.pvDataSource != PanelDataSource.NONE &&
                                    panelFetchInputsChanged(before, after)) {
                                    triggerPanelDataFetch(entry, entry.panelIndex)
                                }
                            }
                        b.panelEntries.filter { it.panelIndex == 0L }
                            .forEach { entry ->
                                val panelId = repository.savePanel(scenarioId, entry.toPanel())
                                triggerPanelDataFetch(entry, panelId)
                            }
                        // Editing a scenario in place changes its energy flows, so
                        // any previously computed simulation + costing rows are now
                        // stale. The simulator only runs when no sim rows exist and
                        // CostingWorker skips existing costings, so they must be
                        // cleared here to force a full recompute — mirrors what the
                        // legacy per-component activities did on save. (NEW / COPY /
                        // LINKED paths create fresh scenarios with no such rows.)
                        repository.deleteSimulationDataForScenarioID(scenarioId)
                        repository.deleteCostingDataForScenarioID(scenarioId)
                        scenarioId
                    }
                    b.isLinked -> {
                        // Full link: create shell scenario, then link load profile, EV, inverters,
                        // panels, batteries and battery schedules. insertScenarioAndReturnID returns 0 if the
                        // name collides (UNIQUE index) — guard the per-component work so we never link to a
                        // non-existent scenario id.
                        val newId = repository.insertScenarioAndReturnID(b.toScenarioShell(), false)
                        if (newId > 0L) {
                            // One atomic transaction (was ten fire-and-forget tasks racing on the 8-thread
                            // write executor): a source missing an optional component — e.g. no hot-water
                            // system — no longer NPEs mid-race and crashes before the load-profile/inverter
                            // links commit. The HW divert is replayed from builder state inside the same
                            // transaction. See ScenarioDAO.linkAllComponentsFromScenario.
                            repository.linkAllComponentsFromScenario(
                                b.basedOnId, newId,
                                if (b.hwDivert.active) b.hwDivert.toHwDivert() else null
                            )
                        }
                        newId
                    }
                    b.loadSource == LoadSource.LINKED -> {
                        // Load-profile link only: create scenario with EV+inverters+panels from builder, link load
                        val newId = repository.insertScenarioAndReturnID(b.toScenarioShellWithEV(), false)
                        if (newId > 0L) {
                            repository.linkLoadProfileFromScenario(b.basedOnId, newId)
                            b.panelEntries.forEach { entry ->
                                val panelId = repository.savePanel(newId, entry.toPanel())
                                triggerPanelDataFetch(entry, panelId)
                            }
                        }
                        newId
                    }
                    else -> {
                        // NEW / COPY / IMPORT: save panels separately to capture their IDs for data fetch.
                        val newId = repository.insertScenarioAndReturnID(b.toScenarioComponents(), false)
                        if (newId > 0L) {
                            val isCopy = b.scenarioMode == ScenarioMode.COPY
                            // Source panels (by id) so a copied panel whose fetch inputs the user changed in
                            // the wizard is re-fetched, while an unchanged one just clones the existing data.
                            val sourcePanels = if (isCopy)
                                repository.getPanelsForScenario(b.basedOnId).associateBy { it.panelIndex }
                            else emptyMap()
                            b.panelEntries.forEach { entry ->
                                if (isCopy) {
                                    // A COPY entry carries the SOURCE panel's id. Force a NEW panel (index 0)
                                    // so savePanel INSERTs + links it to the copy — passing the source id would
                                    // hit savePanel's else-branch (updatePanel), leaving the copy with no panel
                                    // AND overwriting the original.
                                    val sourcePanelId = entry.panelIndex
                                    val before = sourcePanels[sourcePanelId]
                                    val after = entry.toPanel().also { it.panelIndex = 0L }
                                    val newPanelId = repository.savePanel(newId, after)
                                    when {
                                        // Edited fetch inputs (or a panel with no source row) → fetch fresh data
                                        // that matches the new config, but only when the source is fetchable.
                                        entry.pvDataSource != PanelDataSource.NONE &&
                                                panelFetchInputsChanged(before, after) ->
                                            triggerPanelDataFetch(entry, newPanelId)
                                        // Unchanged copy → clone the source's generated data so the copy keeps
                                        // its PV without a needless re-download (mirrors legacy copyScenario).
                                        sourcePanelId > 0L ->
                                            repository.copyPanelData(sourcePanelId, newPanelId)
                                        // A NONE-source panel freshly added during the copy: nothing to do.
                                        else -> { }
                                    }
                                } else {
                                    val panelId = repository.savePanel(newId, entry.toPanel())
                                    triggerPanelDataFetch(entry, panelId)
                                }
                            }
                        }
                        newId
                    }
                }
                // A non-positive id means the insert failed (a duplicate name violated the UNIQUE index, now
                // returned as 0 by the DAO rather than a dead id). Fail loudly instead of adopting id 0 and
                // running the cleanup/notify path. The name check in the UI normally prevents reaching here.
                if (savedId <= 0L) {
                    throw IllegalStateException("Scenario not saved — the name is already in use.")
                }
                // Absolute-year load: write the source's REAL series onto the 2001 grid now. Create paths only
                // (a fresh load profile has no loadprofiledata, so the plain @Insert can't collide and the
                // GenerateMissingLoadDataWorker will skip a profile that already has rows). The loadSource/sysSn
                // guards mean a stale absolute flag (left if the user later switched to SLP/Copy/Link) is ignored.
                if (b.loadAbsoluteYear && b.loadSource == LoadSource.SOURCE &&
                    b.loadSourceSysSn.isNotBlank() && !isEditMode) {
                    materializeAbsoluteYearLoadData(savedId, b)
                }
                // An edit-save deletes-and-reinserts the scenario's components, leaving the old rows
                // unreferenced. Prune all such orphans (battery, inverter, schedules, …) in one pass — a no-op
                // for the NEW/COPY/LINK paths, which don't orphan anything.
                repository.deleteOrphanComponents()
                android.util.Log.i(PanelSourceFetchWorker.TAG, "save(): completed savedId=$savedId")
                // Adopt the saved id: the wizard is now editing THIS scenario. Stops the false "name already
                // in use" (the just-saved name no longer counts against itself) and routes any further Save
                // through the in-place edit path instead of a second INSERT that would hit the UNIQUE name index.
                _scenarioId.value = savedId
                val needsWeatherFetch = b.heatPumpEntries.any { it.weatherSource == "cds" }
                _saveResult.value = WizardSaveResult.Done(savedId, runSimulation, pvgisCount, needsWeatherFetch)
            } catch (e: Exception) {
                android.util.Log.e(PanelSourceFetchWorker.TAG, "save() failed", e)
                _saveResult.value = WizardSaveResult.Failed(e.message ?: "Couldn't save the scenario.")
            }
        }
    }
}
