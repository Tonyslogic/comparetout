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

private const val KEY_NOVICE_MODE = "wizard_novice_mode"


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
                // The shared ranges query returns every sysSn namespace; classify
                // via the central registry (Importer.forSysSn).
                if (seen.add(r.sysSn)) out.add(SourceDateRange(r.sysSn, r.startDate, r.finishDate,
                    ComparisonUIViewModel.Importer.forSysSn(r.sysSn)))
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
                    ComparisonUIViewModel.Importer.SOLIS           -> "Solis: $sysSn"
                    ComparisonUIViewModel.Importer.FUSION_SOLAR    -> "FusionSolar: $sysSn"
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
                    throw IllegalStateException(
                        context.getString(R.string.ui2_wiz_save_err_name_in_use))
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
                _saveResult.value = WizardSaveResult.Failed(
                    e.message ?: context.getString(R.string.ui2_wiz_save_err_generic))
            }
        }
    }
}
