package com.tfcode.comparetout.ui2

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.tfcode.comparetout.R
import com.tfcode.comparetout.TOUTCApplication
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.scenario.Scenario
import com.tfcode.comparetout.model.scenario.Scenario2Battery
import com.tfcode.comparetout.model.scenario.Scenario2DischargeToGrid
import com.tfcode.comparetout.model.scenario.Scenario2EVCharge
import com.tfcode.comparetout.model.scenario.Scenario2EVDivert
import com.tfcode.comparetout.model.scenario.Scenario2HWSchedule
import com.tfcode.comparetout.model.scenario.Scenario2HWSystem
import com.tfcode.comparetout.model.scenario.Scenario2Inverter
import com.tfcode.comparetout.model.scenario.Scenario2LoadShift
import com.tfcode.comparetout.model.scenario.Scenario2Panel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ──────────────────────────────────────────────────────────────────────────
// Directors tab — manages components SHARED by 2+ scenarios.
//
// Editing is limited to what the existing DAO supports (link / copy / delete);
// every edit is cached in the UI and only written to the database on Save.
//
// One "group" maps to the dashboard / wizard sections (Usage → Inverter → PV
// → Battery → Hot Water → EV). Each group contains 1+ "subjects" — the
// concrete component kinds the user can share: a Battery group, for example,
// contains Settings (Battery), Schedule (LoadShift) and Discharge schedule
// (DischargeToGrid) panels.
// ──────────────────────────────────────────────────────────────────────────

/** Top-level Directors accordion. Mirrors the dashboard and wizard section order. */
enum class DirectorGroup(
    val label: String,
    val iconRes: Int,
    val wizardSection: String,
    val tintIcon: Boolean,
    /** When set, rendered as an emoji glyph instead of [iconRes] (the heat pump has no vector asset). */
    val emojiIcon: String? = null
) {
    USAGE(   "Usage Data", R.drawable.house,      "load",      true),
    INVERTER("Inverter",   R.drawable.inverter,   "inverters", true),
    PV(      "PV System",  R.drawable.solarpanel, "pv",        false),
    BATTERY( "Battery",    R.drawable.battery1,   "battery",   true),
    HW(      "Hot Water",  R.drawable.waterwarm,  "hotwater",  true),
    EV(      "EV",         R.drawable.ev_on,      "ev",        false),
    HEAT_PUMP("Heat Pump", R.drawable.waterwarm,  "heatpump",  true, emojiIcon = "♨️")
}

/** Component kind the user can share. */
enum class DirectorSubject(
    val label: String,
    val group: DirectorGroup,
    val supportsUnlink: Boolean
) {
    USAGE_PROFILE    ("Usage profile",      DirectorGroup.USAGE,    false),
    INVERTER_SETTINGS("Inverter",           DirectorGroup.INVERTER, true),
    PV_PANEL         ("PV string",          DirectorGroup.PV,       true),
    BATTERY_SETTINGS ("Battery settings",   DirectorGroup.BATTERY,  true),
    BATTERY_SCHEDULE ("Battery schedule",   DirectorGroup.BATTERY,  true),
    BATTERY_DISCHARGE("Discharge schedule", DirectorGroup.BATTERY,  true),
    HW_SETTINGS      ("Hot water settings", DirectorGroup.HW,       false),
    HW_SCHEDULE      ("Hot water schedule", DirectorGroup.HW,       true),
    EV_SCHEDULE      ("EV schedule",        DirectorGroup.EV,       true),
    EV_DIVERT        ("EV divert",          DirectorGroup.EV,       true),
    HEAT_PUMP_SETTINGS("Heat pump",         DirectorGroup.HEAT_PUMP, true);

    companion object {
        fun subjectsFor(g: DirectorGroup): List<DirectorSubject> =
            entries.filter { it.group == g }
    }
}

data class DirectorScenarioRef(val id: Long, val name: String)

data class DirectorInstance(
    val subject: DirectorSubject,
    val componentId: Long,
    val name: String,
    val specs: List<Pair<String, String>>,
    val linked: Set<Long>                       // committed linked scenario ids
) {
    /** Lowest-id linked scenario — used as the deep-link target for the wizard. */
    val lowestScenarioId: Long? get() = linked.minOrNull()
}

enum class DirectorEditOp { LINK, UNLINK, FORK }

data class DirectorEditKey(
    val subject: DirectorSubject,
    val componentId: Long,
    val scenarioId: Long
)

/** A component the user chose to "seed" — shown even though it has only 1 scenario. */
data class DirectorSeedKey(val subject: DirectorSubject, val componentId: Long)

data class DirectorUiState(
    val instances: List<DirectorInstance> = emptyList(),
    val scenarios: List<DirectorScenarioRef> = emptyList(),
    val edits: Map<DirectorEditKey, DirectorEditOp> = emptyMap(),
    val seeded: Set<DirectorSeedKey> = emptySet(),
    val loading: Boolean = true,
    val saving: Boolean = false
) {
    val dirty: Boolean get() = edits.isNotEmpty() || seeded.isNotEmpty()
}

private const val NOVICE_MODE_KEY = "wizard_novice_mode"

@HiltViewModel
class UI2DirectorViewModel @Inject constructor(
    application: Application,
    private val repository: ToutcRepository
) : AndroidViewModel(application) {

    private val _noviceMode = MutableStateFlow(true)
    val noviceMode = _noviceMode.asLiveData()

    fun toggleNoviceMode() {
        val newValue = !_noviceMode.value
        _noviceMode.value = newValue
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                (getApplication<Application>() as TOUTCApplication)
                    .putStringValueIntoDataStore(NOVICE_MODE_KEY, newValue.toString())
            }
        }
    }

    private val _panelRel     = MutableStateFlow<List<Scenario2Panel>>(emptyList())
    private val _batteryRel   = MutableStateFlow<List<Scenario2Battery>>(emptyList())
    private val _inverterRel  = MutableStateFlow<List<Scenario2Inverter>>(emptyList())
    private val _evChargeRel  = MutableStateFlow<List<Scenario2EVCharge>>(emptyList())
    private val _evDivertRel  = MutableStateFlow<List<Scenario2EVDivert>>(emptyList())
    private val _hwRel        = MutableStateFlow<List<Scenario2HWSystem>>(emptyList())
    private val _hwSchedRel   = MutableStateFlow<List<Scenario2HWSchedule>>(emptyList())
    private val _loadShiftRel = MutableStateFlow<List<Scenario2LoadShift>>(emptyList())
    private val _dischargeRel = MutableStateFlow<List<Scenario2DischargeToGrid>>(emptyList())
    private val _scenarios    = MutableStateFlow<List<Scenario>>(emptyList())

    private val _instances = MutableStateFlow<List<DirectorInstance>>(emptyList())
    private val _edits     = MutableStateFlow<Map<DirectorEditKey, DirectorEditOp>>(emptyMap())
    private val _seeded    = MutableStateFlow<Set<DirectorSeedKey>>(emptySet())
    private val _loading   = MutableStateFlow(true)
    private val _saving    = MutableStateFlow(false)

    private val _core = combine(_instances, _edits, _seeded) { i, e, s -> Triple(i, e, s) }

    val uiState: StateFlow<DirectorUiState> = combine(
        _core, _scenarios, _loading, _saving
    ) { core, scenarios, loading, saving ->
        DirectorUiState(
            instances = core.first,
            scenarios = scenarios.map { DirectorScenarioRef(it.scenarioIndex, it.scenarioName) }
                .sortedBy { it.name.lowercase() },
            edits = core.second,
            seeded = core.third,
            loading = loading,
            saving = saving
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DirectorUiState())

    init {
        Log.d("UI2", "UI2DirectorViewModel created")
        viewModelScope.launch(Dispatchers.IO) {
            _noviceMode.value =
                runCatching {
                    (getApplication<Application>() as TOUTCApplication)
                        .getStringValueFromDataStore(NOVICE_MODE_KEY)
                }.getOrDefault("") != "false"
        }
        viewModelScope.launch(Dispatchers.Main) {
            repository.allPanelRelations.asFlow().collect { _panelRel.value = it ?: emptyList() }
        }
        viewModelScope.launch(Dispatchers.Main) {
            repository.allBatteryRelations.asFlow().collect { _batteryRel.value = it ?: emptyList() }
        }
        viewModelScope.launch(Dispatchers.Main) {
            repository.allInverterRelations.asFlow().collect { _inverterRel.value = it ?: emptyList() }
        }
        viewModelScope.launch(Dispatchers.Main) {
            repository.allEVChargeRelations.asFlow().collect { _evChargeRel.value = it ?: emptyList() }
        }
        viewModelScope.launch(Dispatchers.Main) {
            repository.allEVDivertRelations.asFlow().collect { _evDivertRel.value = it ?: emptyList() }
        }
        viewModelScope.launch(Dispatchers.Main) {
            repository.allHWSystemRelations.asFlow().collect { _hwRel.value = it ?: emptyList() }
        }
        viewModelScope.launch(Dispatchers.Main) {
            repository.allHWScheduleRelations.asFlow().collect { _hwSchedRel.value = it ?: emptyList() }
        }
        viewModelScope.launch(Dispatchers.Main) {
            repository.allLoadShiftRelations.asFlow().collect { _loadShiftRel.value = it ?: emptyList() }
        }
        viewModelScope.launch(Dispatchers.Main) {
            repository.allDischargeRelations.asFlow().collect { _dischargeRel.value = it ?: emptyList() }
        }
        viewModelScope.launch(Dispatchers.Main) {
            repository.allScenarios.asFlow().collect { _scenarios.value = it ?: emptyList() }
        }
        // collectLatest (not collect): a fresh relation change cancels an in-flight rebuild, so two rebuilds
        // racing off one save can't finish out of order and leave _instances holding the stale result.
        viewModelScope.launch(Dispatchers.Main) {
            combine(_panelRel, _batteryRel, _inverterRel, _evChargeRel, _evDivertRel) { _, _, _, _, _ -> }
                .collectLatest { rebuild() }
        }
        viewModelScope.launch(Dispatchers.Main) {
            combine(_hwRel, _hwSchedRel, _loadShiftRel, _dischargeRel) { _, _, _, _ -> }
                .collectLatest { rebuild() }
        }
        viewModelScope.launch(Dispatchers.Main) {
            _scenarios.collectLatest { rebuild() }
        }
    }

    // ── pending-edit / seed API (cached until save) ─────────────────────────

    fun queueEdit(subject: DirectorSubject, componentId: Long, scenarioId: Long, op: DirectorEditOp) {
        _edits.value += (DirectorEditKey(subject, componentId, scenarioId) to op)
    }

    fun cancelEdit(subject: DirectorSubject, componentId: Long, scenarioId: Long) {
        _edits.value -= DirectorEditKey(subject, componentId, scenarioId)
    }

    /** Reveal a not-yet-shared component so scenarios can be linked to it. */
    fun seed(subject: DirectorSubject, componentId: Long) {
        _seeded.value += DirectorSeedKey(subject, componentId)
    }

    fun discardAll() {
        _edits.value = emptyMap()
        _seeded.value = emptySet()
    }

    fun save() {
        val edits = _edits.value
        if ((edits.isEmpty() && _seeded.value.isEmpty()) || _saving.value) return
        val instances = _instances.value
        _saving.value = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                edits.forEach { (key, op) ->
                    val inst = instances.firstOrNull {
                        it.subject == key.subject && it.componentId == key.componentId
                    }
                    val source = inst?.linked?.firstOrNull { it != key.scenarioId }
                        ?: inst?.linked?.firstOrNull()
                    when (op) {
                        DirectorEditOp.UNLINK ->
                            if (key.subject.supportsUnlink)
                                deleteFor(key.subject, key.componentId, key.scenarioId)
                        DirectorEditOp.LINK ->
                            // PV shares per-string: link the SPECIFIC panel idempotently (the scenario-level
                            // linkFor links every source panel and can duplicate the junction row → double PV).
                            if (key.subject == DirectorSubject.PV_PANEL)
                                repository.linkPanelToScenario(key.componentId, key.scenarioId)
                            else if (source != null)
                                linkFor(key.subject, source, key.scenarioId)
                        DirectorEditOp.FORK ->
                            if (key.subject == DirectorSubject.PV_PANEL) {
                                repository.copyPanelToScenario(key.componentId, key.scenarioId)
                                deleteFor(key.subject, key.componentId, key.scenarioId)
                            } else if (source != null) {
                                copyFor(key.subject, source, key.scenarioId)
                                if (key.subject.supportsUnlink)
                                    deleteFor(key.subject, key.componentId, key.scenarioId)
                            }
                    }
                }
            }
            _edits.value = emptyMap()
            _seeded.value = emptySet()
            // The relation LiveData refreshes the view when the (async) writes commit, but recompute now too so
            // edits that only changed an un-observed path still land without leaving + re-entering the screen.
            rebuild()
            _saving.value = false
        }
    }

    private fun deleteFor(s: DirectorSubject, componentId: Long, scenarioId: Long) = when (s) {
        DirectorSubject.PV_PANEL          -> repository.deletePanelFromScenario(componentId, scenarioId)
        DirectorSubject.BATTERY_SETTINGS  -> repository.deleteBatteryFromScenario(componentId, scenarioId)
        DirectorSubject.BATTERY_SCHEDULE  -> repository.deleteLoadShiftFromScenario(componentId, scenarioId)
        DirectorSubject.BATTERY_DISCHARGE -> repository.deleteDischargeFromScenario(componentId, scenarioId)
        DirectorSubject.INVERTER_SETTINGS -> repository.deleteInverterFromScenario(componentId, scenarioId)
        DirectorSubject.EV_SCHEDULE       -> repository.deleteEVChargeFromScenario(componentId, scenarioId)
        DirectorSubject.EV_DIVERT         -> repository.deleteEVDivertFromScenario(componentId, scenarioId)
        DirectorSubject.HW_SCHEDULE       -> repository.deleteHWScheduleFromScenario(componentId, scenarioId)
        DirectorSubject.HEAT_PUMP_SETTINGS -> repository.deleteHeatPumpFromScenario(componentId, scenarioId)
        else -> Unit                                      // USAGE_PROFILE, HW_SETTINGS: not supported by DAO
    }

    private fun linkFor(s: DirectorSubject, from: Long, to: Long) = when (s) {
        DirectorSubject.PV_PANEL          -> repository.linkPanelFromScenario(from, to)
        DirectorSubject.BATTERY_SETTINGS  -> repository.linkBatteryFromScenario(from, to)
        DirectorSubject.BATTERY_SCHEDULE  -> repository.linkLoadShiftFromScenario(from, to)
        DirectorSubject.BATTERY_DISCHARGE -> repository.linkDischargeFromScenario(from, to)
        DirectorSubject.INVERTER_SETTINGS -> repository.linkInverterFromScenario(from, to)
        DirectorSubject.EV_SCHEDULE       -> repository.linkEVChargeFromScenario(from, to)
        DirectorSubject.EV_DIVERT         -> repository.linkEVDivertFromScenario(from, to)
        DirectorSubject.HW_SETTINGS       -> repository.linkHWSystemFromScenario(from, to)
        DirectorSubject.HW_SCHEDULE       -> repository.linkHWScheduleFromScenario(from, to)
        DirectorSubject.HEAT_PUMP_SETTINGS -> repository.linkHeatPumpFromScenario(from, to)
        DirectorSubject.USAGE_PROFILE     -> repository.linkLoadProfileFromScenario(from, to)
    }

    private fun copyFor(s: DirectorSubject, from: Long, to: Long) = when (s) {
        DirectorSubject.PV_PANEL          -> repository.copyPanelFromScenario(from, to)
        DirectorSubject.BATTERY_SETTINGS  -> repository.copyBatteryFromScenario(from, to)
        DirectorSubject.BATTERY_SCHEDULE  -> repository.copyLoadShiftFromScenario(from, to)
        DirectorSubject.BATTERY_DISCHARGE -> repository.copyDischargeFromScenario(from, to)
        DirectorSubject.INVERTER_SETTINGS -> repository.copyInverterFromScenario(from, to)
        DirectorSubject.EV_SCHEDULE       -> repository.copyEVChargeFromScenario(from, to)
        DirectorSubject.EV_DIVERT         -> repository.copyEVDivertFromScenario(from, to)
        DirectorSubject.HW_SETTINGS       -> repository.copyHWSettingsFromScenario(from, to)
        DirectorSubject.HW_SCHEDULE       -> repository.copyHWScheduleFromScenario(from, to)
        DirectorSubject.HEAT_PUMP_SETTINGS -> repository.copyHeatPumpFromScenario(from, to)
        DirectorSubject.USAGE_PROFILE     -> repository.copyLoadProfileFromScenario(from, to)
    }

    // ── build the component list from the relation tables ───────────────────

    private suspend fun rebuild() = withContext(Dispatchers.IO) {
        val out = mutableListOf<DirectorInstance>()

        out += instancesOf(DirectorSubject.PV_PANEL,
            _panelRel.value.map { it.scenarioID to it.panelID }) { id, sid ->
            repository.getPanelsForScenario(sid).firstOrNull { it.panelIndex == id }?.let { p ->
                p.panelName.ifBlank { "PV string" } to listOf(
                    "Panels" to "${p.panelCount}", "Power" to "${p.panelkWp} W",
                    "Azimuth" to "${p.azimuth}°", "Slope" to "${p.slope}°")
            }
        }
        out += instancesOf(DirectorSubject.BATTERY_SETTINGS,
            _batteryRel.value.map { it.scenarioID to it.batteryID }) { id, sid ->
            repository.getBatteriesForScenarioID(sid).firstOrNull { it.batteryIndex == id }?.let { b ->
                "%.1f kWh battery".format(b.batterySize) to listOf(
                    "Capacity" to "%.1f kWh".format(b.batterySize),
                    "Max charge" to "%.1f kW".format(b.maxCharge),
                    "Max discharge" to "%.1f kW".format(b.maxDischarge),
                    "Discharge stop" to "%.0f%%".format(b.dischargeStop))
            }
        }
        out += instancesOf(DirectorSubject.BATTERY_SCHEDULE,
            _loadShiftRel.value.map { it.scenarioID to it.loadShiftID }) { id, sid ->
            repository.getLoadShiftsForScenarioID(sid).firstOrNull { it.loadShiftIndex == id }?.let { ls ->
                ls.name.ifBlank { "Charge schedule" } to listOf(
                    "Window" to "${hours(ls.begin)}–${hours(ls.end)}",
                    "Stop at" to "%.0f%%".format(ls.stopAt),
                    "Inverter" to ls.inverter)
            }
        }
        out += instancesOf(DirectorSubject.BATTERY_DISCHARGE,
            _dischargeRel.value.map { it.scenarioID to it.dischargeID }) { id, sid ->
            repository.getDischargesForScenario(sid).firstOrNull { it.d2gIndex == id }?.let { d ->
                d.name.ifBlank { "Discharge schedule" } to listOf(
                    "Window" to "${hours(d.begin)}–${hours(d.end)}",
                    "Rate" to "%.1f kW".format(d.rate),
                    "Stop at" to "%.0f%%".format(d.stopAt),
                    "Inverter" to d.inverter)
            }
        }
        out += instancesOf(DirectorSubject.INVERTER_SETTINGS,
            _inverterRel.value.map { it.scenarioID to it.inverterID }) { id, sid ->
            repository.getInvertersForScenario(sid).firstOrNull { it.inverterIndex == id }?.let { v ->
                v.inverterName.ifBlank { "Inverter" } to listOf(
                    "Rating" to "%.1f kW".format(v.maxInverterLoad),
                    "MPPT inputs" to "${v.mpptCount}",
                    "AC→DC loss" to "${v.ac2dcLoss}%", "DC→AC loss" to "${v.dc2acLoss}%")
            }
        }
        out += instancesOf(DirectorSubject.EV_SCHEDULE,
            _evChargeRel.value.map { it.scenarioID to it.evChargeID }) { id, sid ->
            repository.getEVChargesForScenario(sid).firstOrNull { it.evChargeIndex == id }?.let { e ->
                e.name.ifBlank { "EV schedule" } to listOf(
                    "Charge draw" to "%.1f kW".format(e.draw),
                    "Window" to "${hours(e.begin)}–${hours(e.end)}")
            }
        }
        out += instancesOf(DirectorSubject.EV_DIVERT,
            _evDivertRel.value.map { it.scenarioID to it.evDivertID }) { id, sid ->
            repository.getEVDivertsForScenario(sid).firstOrNull { it.evDivertIndex == id }?.let { d ->
                d.name.ifBlank { "EV divert" } to listOf(
                    "Window" to "${hours(d.begin)}–${hours(d.end)}",
                    "Daily max" to "%.1f kWh".format(d.dailyMax),
                    "EV 1st" to if (d.isEv1st) "Yes" else "No",
                    "Active" to if (d.isActive) "Yes" else "No")
            }
        }
        out += instancesOf(DirectorSubject.HW_SETTINGS,
            _hwRel.value.map { it.scenarioID to it.hwSystemID }) { id, sid ->
            repository.getHWSystemForScenarioID(sid)?.takeIf { it.hwSystemIndex == id }?.let { h ->
                "${h.hwCapacity} L hot water" to listOf(
                    "Tank" to "${h.hwCapacity} L", "Target" to "${h.hwTarget}°C",
                    "Daily use" to "${h.hwUsage} L", "Loss" to "${h.hwLoss}%")
            }
        }
        out += instancesOf(DirectorSubject.HW_SCHEDULE,
            _hwSchedRel.value.map { it.scenarioID to it.hwScheduleID }) { id, sid ->
            repository.getHWSchedulesForScenario(sid).firstOrNull { it.hwScheduleIndex == id }?.let { hs ->
                hs.name.ifBlank { "Hot water schedule" } to listOf(
                    "Window" to "${hours(hs.begin)}–${hours(hs.end)}")
            }
        }
        // Usage Data — no relation table; group scenarios by their load-profile id.
        val usageRows = _scenarios.value.mapNotNull { s ->
            val lp = runCatching {
                repository.getScenarioComponentsForScenarioID(s.scenarioIndex)?.loadProfile
            }.getOrNull()
            if (lp != null) s.scenarioIndex to lp.loadProfileIndex else null
        }
        out += instancesOf(DirectorSubject.USAGE_PROFILE, usageRows) { id, sid ->
            val lp = runCatching {
                repository.getScenarioComponentsForScenarioID(sid)?.loadProfile
            }.getOrNull()
            if (lp != null && lp.loadProfileIndex == id) {
                lp.distributionSource.ifBlank { "Usage profile" } to listOf(
                    "Source" to lp.distributionSource.ifBlank { "—" },
                    "Annual" to "%.0f kWh".format(lp.annualUsage),
                    "Import max" to "%.1f kW".format(lp.gridImportMax),
                    "Export max" to "%.1f kW".format(lp.gridExportMax))
            } else null
        }

        // Heat Pump — no relation LiveData wired; resolve per scenario via its components
        // (same approach as Usage Data). A scenario can hold 1 heat pump; share it like HW.
        val heatPumpRows = _scenarios.value.flatMap { s ->
            val hps = runCatching {
                repository.getScenarioComponentsForScenarioID(s.scenarioIndex)?.heatPumps
            }.getOrNull() ?: emptyList()
            hps.map { s.scenarioIndex to it.heatPumpIndex }
        }
        out += instancesOf(DirectorSubject.HEAT_PUMP_SETTINGS, heatPumpRows) { id, sid ->
            val hp = runCatching {
                repository.getScenarioComponentsForScenarioID(sid)?.heatPumps
            }.getOrNull()?.firstOrNull { it.heatPumpIndex == id }
            if (hp != null) {
                "%.1f kW heat pump".format(hp.capacityKw) to listOf(
                    "Capacity" to "%.1f kW".format(hp.capacityKw),
                    "SCOP" to "%.1f".format(hp.scop),
                    "Rated COP" to "%.1f".format(hp.copRated),
                    "Backup" to if (hp.isBackupHeater) "Yes" else "No")
            } else null
        }

        _instances.value = out
        _loading.value = false
    }

    /** Group relation rows by component id (keeps every component with 1+ scenarios). */
    private fun instancesOf(
        subject: DirectorSubject,
        rows: List<Pair<Long, Long>>,                       // (scenarioId, componentId)
        resolve: (componentId: Long, sampleScenario: Long) -> Pair<String, List<Pair<String, String>>>?
    ): List<DirectorInstance> {
        return rows.groupBy({ it.second }, { it.first }).entries.mapNotNull { (componentId, scs) ->
            val linked = scs.toSet()
            val resolved = runCatching { resolve(componentId, linked.first()) }.getOrNull()
                ?: return@mapNotNull null
            DirectorInstance(subject, componentId, resolved.first, resolved.second, linked)
        }.sortedBy { it.name.lowercase() }
    }

    private fun hours(h: Int): String = "%02d:00".format(h)
}
