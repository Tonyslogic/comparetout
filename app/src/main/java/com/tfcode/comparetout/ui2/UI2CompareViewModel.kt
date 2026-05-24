package com.tfcode.comparetout.ui2

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.TOUTCApplication
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.costings.SubTotals
import com.tfcode.comparetout.model.importers.InverterDateRange
import com.tfcode.comparetout.model.priceplan.PricePlan
import com.tfcode.comparetout.model.scenario.Scenario
import com.tfcode.comparetout.util.RateLookup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** DataStore keys — the Compare tab's prefs live beside the other UI prefs. */
private const val COMPARE_STATE_KEY = "ui2_compare_state"
private const val NOVICE_MODE_KEY = "wizard_novice_mode"   // shared app-wide novice flag

// ──────────────────────────────────────────────────────────────────────────
// The Compare tab — compares "subjects" (real data sources and/or saved
// simulations) priced against supplier plans, over a chosen timeframe.
//
// The handoff design speaks of Sources × Simulations × Plans, but in the data
// model a source and a simulation are both independent comparison subjects
// (each gets priced against plans). So a cost result is (subject × plan) and a
// usage result is (subject); the live count is (#sources + #sims) × #plans.
// ──────────────────────────────────────────────────────────────────────────

enum class CompareWhat { COST, USAGE, BOTH }

enum class CompareMode(val label: String) {
    TABLE("Table"), BAR("Bar"), STACK("Stacked"),
    LINE("Line"), AREA("Area"), PIE("Pie")
}

enum class CompareLayout { MERGED, SPLIT }

/** A real meter install the user can compare. */
data class CompareSourceItem(
    val sysSn: String,
    val importerType: ComparisonUIViewModel.Importer,
    val startDate: String,
    val finishDate: String
) {
    val typeName: String get() = when (importerType) {
        ComparisonUIViewModel.Importer.ALPHAESS       -> "AlphaESS"
        ComparisonUIViewModel.Importer.ESBNHDF        -> "ESBN HDF"
        ComparisonUIViewModel.Importer.HOME_ASSISTANT -> "Home Assistant"
        else -> importerType.name
    }
}

/** A saved scenario/simulation. */
data class CompareSimItem(val scenarioId: Long, val name: String)

/** A supplier tariff. */
data class ComparePlanItem(val planId: Long, val supplier: String, val planName: String) {
    val fullName: String get() = "$supplier · $planName"
}

/** A day / month / year / all-time granularity anchored at a date (null = not picked). */
data class SubjectRange(val gran: DataSourcePeriod?, val anchor: LocalDate)

/** Everything the accordions and select-sheets read from. */
data class CompareState(
    val what: CompareWhat = CompareWhat.COST,
    // sources/sims are ordered lists, not sets — a sysSn or scenarioId can
    // appear more than once so the user can compare the same subject against
    // itself across different time ranges. Slots distinguish themselves by
    // their position (first occurrence keeps the legacy "src:<sn>" subject id
    // for state-file backward compat; later copies become "src:<sn>#2", "#3").
    val sources: List<String> = emptyList(),   // sysSn — duplicates allowed
    val sims: List<Long> = emptyList(),        // scenarioIndex — duplicates allowed
    val plans: Set<Long> = emptySet(),         // pricePlanIndex
    val series: Set<String> = emptySet(),
    // timeframe
    val advanced: Boolean = false,             // Basic = calendar units, Advanced = trailing windows
    val sync: Boolean = true,                  // one range for all, vs one per subject
    val globalGran: DataSourcePeriod? = DataSourcePeriod.ALL,  // default timeframe is "*"
    val globalAnchor: LocalDate = LocalDate.now(),
    val perSubjectRanges: Map<String, SubjectRange> = emptyMap(),  // subjectId → range (un-synced)
    // display
    val mode: CompareMode = CompareMode.TABLE,
    val layout: CompareLayout = CompareLayout.MERGED
)

/** One priced (subject × plan) result. Money values are in euro. */
data class CompareCostRow(
    val subjectId: String,
    val subjectName: String,
    val isSimulation: Boolean,
    val planId: Long,
    val planName: String,
    val available: Boolean,        // false → costing not yet computed
    val net: Double,
    val buy: Double,
    val sell: Double,
    val fixed: Double,
    val bonus: Double,
    val buyBands: List<Double>,        // buy cost split by tariff rate band (euro, cheapest first)
    val buyBandRates: List<Double>,    // the c/kWh rate for each band (same length & order as buyBands)
    val monthlyNet: List<Double>       // 12 values, for line/area
)

/** One subject's energy totals over the timeframe. kWh values. */
data class CompareUsageRow(
    val subjectId: String,
    val subjectName: String,
    val isSimulation: Boolean,
    val load: Double,
    val buy: Double,
    val feed: Double,
    val pv: Double,
    val pv2load: Double,
    val bat2load: Double,
    val grid2bat: Double,
    val monthly: Map<String, List<Double>>   // series id → 12 monthly values
)

data class CompareResults(
    val cost: List<CompareCostRow> = emptyList(),
    val usage: List<CompareUsageRow> = emptyList()
)

/**
 * Stable id for a subject slot. First occurrence keeps the legacy un-suffixed
 * form so old persisted [CompareState.perSubjectRanges] keys still resolve;
 * the Nth duplicate (1-indexed) gets a "#N" suffix.
 */
fun sourceSubjectId(sysSn: String, occurrence: Int = 0) =
    if (occurrence == 0) "src:$sysSn" else "src:$sysSn#${occurrence + 1}"
fun simSubjectId(scenarioId: Long, occurrence: Int = 0) =
    if (occurrence == 0) "sim:$scenarioId" else "sim:$scenarioId#${occurrence + 1}"

/** One slot in the selection list. [occurrence] is the 0-based index *among slots that share the same key*. */
data class SourceSlot(val sysSn: String, val occurrence: Int, val subjectId: String, val displayName: String)
data class SimSlot(val scenarioId: Long, val occurrence: Int, val subjectId: String, val displayName: String)

/** Walk [list] and emit a [SourceSlot] per entry, tracking how many times each sysSn has been seen. */
internal fun buildSourceSlots(list: List<String>, displayFor: (String) -> String): List<SourceSlot> {
    val counts = HashMap<String, Int>()
    return list.map { sn ->
        val occ = counts.getOrDefault(sn, 0)
        counts[sn] = occ + 1
        val baseName = displayFor(sn)
        val name = if (occ == 0) baseName else "$baseName #${occ + 1}"
        SourceSlot(sn, occ, sourceSubjectId(sn, occ), name)
    }
}

internal fun buildSimSlots(list: List<Long>, displayFor: (Long) -> String): List<SimSlot> {
    val counts = HashMap<Long, Int>()
    return list.map { id ->
        val occ = counts.getOrDefault(id, 0)
        counts[id] = occ + 1
        val baseName = displayFor(id)
        val name = if (occ == 0) baseName else "$baseName #${occ + 1}"
        SimSlot(id, occ, simSubjectId(id, occ), name)
    }
}

@HiltViewModel
class UI2CompareViewModel @Inject constructor(
    private val repository: ToutcRepository,
    private val application: Application
) : ViewModel() {

    companion object {
        val COST_SERIES = listOf(
            "net" to "Net", "buy" to "Buy", "sell" to "Sell",
            "bonus" to "Bonus", "fixed" to "Fixed"
        )
        val USAGE_SERIES = listOf(
            "load" to "Load", "buy" to "Buy", "feed" to "Feed", "pv" to "PV",
            "pv2load" to "PV → Load", "bat2load" to "Battery → Load",
            "grid2bat" to "Grid → Battery"
        )
    }

    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val rowFmt  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    // ── selectable catalogue ────────────────────────────────────────────────
    private val _scenarios    = MutableStateFlow<List<Scenario>>(emptyList())
    private val _alphaSources = MutableStateFlow<List<InverterDateRange>>(emptyList())
    private val _esbnSources  = MutableStateFlow<List<InverterDateRange>>(emptyList())
    private val _haSources    = MutableStateFlow<List<InverterDateRange>>(emptyList())
    private val _plans        = MutableStateFlow<List<PricePlan>>(emptyList())

    val sourceItems: StateFlow<List<CompareSourceItem>> = combine(
        _alphaSources, _esbnSources, _haSources
    ) { alpha, esbn, ha ->
        val seen = mutableSetOf<String>()
        buildList {
            alpha.forEach {
                if (seen.add(it.sysSn))
                    add(CompareSourceItem(it.sysSn, ComparisonUIViewModel.Importer.ALPHAESS, it.startDate, it.finishDate))
            }
            esbn.forEach { r ->
                val type = if (r.sysSn == "HomeAssistant") ComparisonUIViewModel.Importer.HOME_ASSISTANT
                           else ComparisonUIViewModel.Importer.ESBNHDF
                if (seen.add(r.sysSn))
                    add(CompareSourceItem(r.sysSn, type, r.startDate, r.finishDate))
            }
            ha.forEach {
                if (seen.add(it.sysSn))
                    add(CompareSourceItem(it.sysSn, ComparisonUIViewModel.Importer.HOME_ASSISTANT, it.startDate, it.finishDate))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val simItems: StateFlow<List<CompareSimItem>> = _scenarios
        .map { list -> list.map { CompareSimItem(it.scenarioIndex, it.scenarioName) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val planItems: StateFlow<List<ComparePlanItem>> = _plans
        .map { list -> list.map { ComparePlanItem(it.pricePlanIndex, it.supplier, it.planName) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── user selection / configuration ──────────────────────────────────────
    private val _state = MutableStateFlow(CompareState())
    val state: StateFlow<CompareState> = _state.asStateFlow()

    // ── novice mode (drives whether help text is shown) ─────────────────────
    private val _noviceMode = MutableStateFlow(true)
    val noviceMode = _noviceMode.asLiveData()

    // ── computed results ────────────────────────────────────────────────────
    private val _results = MutableStateFlow<CompareResults?>(null)
    val results = _results.asLiveData()
    private val _computing = MutableStateFlow(false)
    val computing = _computing.asLiveData()

    private var computeJob: Job? = null
    private var lastComputeKey: String? = null
    private var saveJob: Job? = null

    init {
        Log.d("UI2", "UI2CompareViewModel created")
        viewModelScope.launch(Dispatchers.IO) {
            _noviceMode.value =
                runCatching { (application as TOUTCApplication).getStringValueFromDataStore(NOVICE_MODE_KEY) }
                    .getOrDefault("") != "false"
            val restored = runCatching { decodeState(loadPersistedJson()) }.getOrNull()
            if (restored != null) withContext(Dispatchers.Main) {
                val clean = normalize(restored)
                _state.value = clean
                recompute(clean)
            }
        }
        viewModelScope.launch(Dispatchers.Main) {
            repository.getAllScenarios().asFlow().collect { _scenarios.value = it ?: emptyList() }
        }
        viewModelScope.launch(Dispatchers.Main) {
            repository.getLiveDateRanges().asFlow().collect { _alphaSources.value = it ?: emptyList() }
        }
        viewModelScope.launch(Dispatchers.Main) {
            repository.getESBNLiveDateRanges().asFlow().collect { _esbnSources.value = it ?: emptyList() }
        }
        viewModelScope.launch(Dispatchers.Main) {
            repository.getHALiveDateRanges().asFlow().collect { _haSources.value = it ?: emptyList() }
        }
        viewModelScope.launch(Dispatchers.IO) {
            _plans.value = repository.getAllPricePlansNow() ?: emptyList()
        }
        // Recompute whenever the selectable catalogue changes.
        viewModelScope.launch(Dispatchers.Main) {
            combine(sourceItems, simItems, planItems) { _, _, _ -> Unit }.collect {
                lastComputeKey = null
                recompute(_state.value)
            }
        }
    }

    /** Apply a state change, persist it, and recompute results if ready. */
    fun update(transform: (CompareState) -> CompareState) {
        val next = normalize(transform(_state.value))
        _state.value = next
        persist(next)
        recompute(next)
    }

    /** Public view of slot rows for the UI. Computed off the current selection. */
    fun sourceSlots(s: CompareState): List<SourceSlot> {
        val byKey = sourceItems.value.associateBy { it.sysSn }
        return buildSourceSlots(s.sources) { sn -> byKey[sn]?.sysSn ?: sn }
    }

    fun simSlots(s: CompareState): List<SimSlot> {
        val byKey = simItems.value.associateBy { it.scenarioId }
        return buildSimSlots(s.sims) { id -> byKey[id]?.name ?: "Sim #$id" }
    }

    /**
     * Append another instance of the source/sim referenced by [subjectId]. The
     * new slot is seeded with the source slot's current range so the user only
     * has to change one thing — its timeframe — to make the comparison useful.
     */
    fun duplicateSubject(subjectId: String) = update { s ->
        val srcSlot = sourceSlots(s).firstOrNull { it.subjectId == subjectId }
        if (srcSlot != null) {
            val newOccurrence = s.sources.count { it == srcSlot.sysSn }
            val newId = sourceSubjectId(srcSlot.sysSn, newOccurrence)
            val seed = s.perSubjectRanges[subjectId]
                ?: SubjectRange(s.globalGran, s.globalAnchor)
            s.copy(
                sources = s.sources + srcSlot.sysSn,
                perSubjectRanges = s.perSubjectRanges + (newId to seed)
            )
        } else {
            val simSlot = simSlots(s).firstOrNull { it.subjectId == subjectId } ?: return@update s
            val newOccurrence = s.sims.count { it == simSlot.scenarioId }
            val newId = simSubjectId(simSlot.scenarioId, newOccurrence)
            val seed = s.perSubjectRanges[subjectId]
                ?: SubjectRange(s.globalGran, s.globalAnchor)
            s.copy(
                sims = s.sims + simSlot.scenarioId,
                perSubjectRanges = s.perSubjectRanges + (newId to seed)
            )
        }
    }

    /**
     * Remove exactly one slot (the one whose [subjectId] matches). When the
     * primary copy is removed, later duplicates renumber down — their range
     * keys move accordingly so the user doesn't see their picked range jump
     * to a different row.
     */
    fun removeSubjectSlot(subjectId: String) = update { s ->
        // Source path
        val srcSlot = sourceSlots(s).firstOrNull { it.subjectId == subjectId }
        if (srcSlot != null) {
            val newSources = s.sources.toMutableList().apply {
                // Walk to find the slot index for this (sysSn, occurrence) and remove that one entry.
                var seen = 0
                val idx = indexOfFirst { it == srcSlot.sysSn && (seen++) == srcSlot.occurrence }
                if (idx >= 0) removeAt(idx)
            }
            return@update renumberRanges(s.copy(sources = newSources))
        }
        val simSlot = simSlots(s).firstOrNull { it.subjectId == subjectId } ?: return@update s
        val newSims = s.sims.toMutableList().apply {
            var seen = 0
            val idx = indexOfFirst { it == simSlot.scenarioId && (seen++) == simSlot.occurrence }
            if (idx >= 0) removeAt(idx)
        }
        renumberRanges(s.copy(sims = newSims))
    }

    /**
     * After [s.sources]/[s.sims] mutate, rebuild [perSubjectRanges] so its keys
     * reflect the new occurrence numbering. Without this, removing the primary
     * copy would leave its range pointing at "src:sn" while the surviving
     * duplicate is now also "src:sn" — they'd silently swap ranges.
     */
    private fun renumberRanges(s: CompareState): CompareState {
        val oldRanges = s.perSubjectRanges
        // Rebuild the slot ids for the *post-mutation* state, but using the
        // *pre-mutation* perSubjectRanges keyed by the same (sysSn, occurrence)
        // pairs. Trick: we don't know the pre-mutation occurrence, so instead
        // we project each surviving slot's range from whichever key it had
        // before — falling back to (oldKey for sysSn+occurrence) if present.
        // In practice the slot order is preserved on removal, so the i-th slot
        // after deletion is the i-th slot from before (skipping the removed
        // one). We re-derive ids and copy ranges across by position.
        val newSrcSlots = buildSourceSlots(s.sources) { it }
        val newSimSlots = buildSimSlots(s.sims) { it.toString() }
        val rebuilt = HashMap<String, SubjectRange>()
        // Source-side ranges: pair pre/post slot lists element-by-element. We
        // can't see the pre list any more (we just mutated it), but the
        // perSubjectRanges map carries the old keys, so just look up via the
        // new slot's (sysSn, occurrence) → if a range exists for that key we
        // keep it; otherwise seed from the previous occurrence for the same sn.
        newSrcSlots.forEach { slot ->
            val direct = oldRanges[slot.subjectId]
            if (direct != null) {
                rebuilt[slot.subjectId] = direct
            } else {
                // Look for any "src:sn" / "src:sn#N" key for the same sn whose
                // range we should adopt — closest higher occurrence first.
                val fallback = oldRanges.entries
                    .firstOrNull { it.key.startsWith("src:${slot.sysSn}") }
                    ?.value
                if (fallback != null) rebuilt[slot.subjectId] = fallback
            }
        }
        newSimSlots.forEach { slot ->
            val direct = oldRanges[slot.subjectId]
            if (direct != null) {
                rebuilt[slot.subjectId] = direct
            } else {
                val fallback = oldRanges.entries
                    .firstOrNull { it.key.startsWith("sim:${slot.scenarioId}") }
                    ?.value
                if (fallback != null) rebuilt[slot.subjectId] = fallback
            }
        }
        return s.copy(perSubjectRanges = rebuilt)
    }

    fun toggleNoviceMode() {
        val newValue = !_noviceMode.value
        _noviceMode.value = newValue
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                (application as TOUTCApplication)
                    .putStringValueIntoDataStore(NOVICE_MODE_KEY, newValue.toString())
            }
        }
    }

    /** Series ids that are meaningful for the current comparison type. */
    private fun validSeriesIds(what: CompareWhat): Set<String> = when (what) {
        CompareWhat.COST  -> COST_SERIES.map { it.first }.toSet()
        CompareWhat.USAGE -> USAGE_SERIES.map { it.first }.toSet()
        CompareWhat.BOTH  -> USAGE_SERIES.map { it.first }.toSet() +
                             COST_SERIES.map { "c_${it.first}" }.toSet()
    }

    /**
     * Keep state self-consistent: drop filter series that no longer apply to
     * the chosen comparison type, seed (un-synced) ranges for every slot, and
     * prune perSubjectRanges entries that no longer have a backing slot —
     * important when slots are removed or the duplicates are reduced.
     */
    private fun normalize(s: CompareState): CompareState {
        var r = s
        val valid = validSeriesIds(s.what)
        if (!s.series.all { it in valid }) {
            r = r.copy(series = s.series.filterTo(mutableSetOf()) { it in valid })
        }
        val currentIds = slotIds(r)
        if (!r.sync) {
            if (currentIds.any { it !in r.perSubjectRanges }) {
                val filled = r.perSubjectRanges.toMutableMap()
                currentIds.forEach { id ->
                    if (id !in filled) filled[id] = SubjectRange(r.globalGran, r.globalAnchor)
                }
                r = r.copy(perSubjectRanges = filled)
            }
        }
        // Always prune stale range entries — they'd otherwise outlive the slot
        // that owns them (e.g. user removes a duplicate) and clog the JSON
        // state file forever.
        if (r.perSubjectRanges.keys.any { it !in currentIds }) {
            r = r.copy(
                perSubjectRanges = r.perSubjectRanges.filterKeys { it in currentIds }
            )
        }
        return r
    }

    /** Set of subject ids currently backed by a slot. */
    private fun slotIds(s: CompareState): Set<String> {
        val ids = HashSet<String>(s.sources.size + s.sims.size)
        val srcCounts = HashMap<String, Int>()
        s.sources.forEach { sn ->
            val occ = srcCounts.getOrDefault(sn, 0); srcCounts[sn] = occ + 1
            ids += sourceSubjectId(sn, occ)
        }
        val simCounts = HashMap<Long, Int>()
        s.sims.forEach { id ->
            val occ = simCounts.getOrDefault(id, 0); simCounts[id] = occ + 1
            ids += simSubjectId(id, occ)
        }
        return ids
    }

    // ── persistence ─────────────────────────────────────────────────────────

    private fun loadPersistedJson(): String = runCatching {
        (application as TOUTCApplication).getStringValueFromDataStore(COMPARE_STATE_KEY)
    }.getOrDefault("")

    private fun persist(s: CompareState) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                (application as TOUTCApplication)
                    .putStringValueIntoDataStore(COMPARE_STATE_KEY, encodeState(s))
            }
        }
    }

    // ── readiness ───────────────────────────────────────────────────────────

    /** Resolve a subject's effective range, or null when it has no granularity. */
    private fun resolveRange(s: CompareState, subjectId: String): Pair<DataSourcePeriod, LocalDate>? {
        val sr = if (s.sync) SubjectRange(s.globalGran, s.globalAnchor)
                 else s.perSubjectRanges[subjectId] ?: SubjectRange(s.globalGran, s.globalAnchor)
        val gran = sr.gran ?: return null
        return gran to sr.anchor
    }

    fun timeframeReady(s: CompareState): Boolean {
        val ids = slotIds(s)
        return if (s.sync) s.globalGran != null
        else ids.isNotEmpty() && ids.all { resolveRange(s, it) != null }
    }

    private fun isReady(s: CompareState): Boolean {
        val hasSubjects = s.sources.isNotEmpty() || s.sims.isNotEmpty()
        val hasPlans = s.what == CompareWhat.USAGE || s.plans.isNotEmpty()
        return hasSubjects && hasPlans && s.series.isNotEmpty() && timeframeReady(s)
    }

    // ── recompute scheduling ────────────────────────────────────────────────

    private fun computeKey(s: CompareState): String = listOf(
        s.what,
        // Ordered lists so a duplicate slot counts toward the key — two
        // copies of the same source are different work and must invalidate
        // the cache.
        s.sources, s.sims, s.plans.sorted(),
        s.advanced, s.sync, s.globalGran, s.globalAnchor,
        s.perSubjectRanges.toSortedMap().toString()
    ).joinToString("|")

    private fun recompute(s: CompareState) {
        if (!isReady(s)) {
            computeJob?.cancel()
            _results.value = null
            _computing.value = false
            lastComputeKey = null
            return
        }
        val key = computeKey(s)
        if (key == lastComputeKey && _results.value != null) return
        lastComputeKey = key
        computeJob?.cancel()
        _computing.value = true
        computeJob = viewModelScope.launch(Dispatchers.IO) {
            val res = runCatching { computeResults(s) }.getOrElse { CompareResults() }
            _results.value = res
            _computing.value = false
        }
    }

    // ── computation ─────────────────────────────────────────────────────────

    private fun computeResults(s: CompareState): CompareResults {
        val sourcesByKey = sourceItems.value.associateBy { it.sysSn }
        val simsByKey    = simItems.value.associateBy { it.scenarioId }
        val plans        = _plans.value.filter { it.pricePlanIndex in s.plans }

        // Expand each slot independently so duplicates produce distinct rows.
        val srcSlots = buildSourceSlots(s.sources) { sn -> sourcesByKey[sn]?.sysSn ?: sn }
        val simSlots = buildSimSlots(s.sims) { id -> simsByKey[id]?.name ?: "Sim #$id" }

        val wantCost  = s.what != CompareWhat.USAGE
        val wantUsage = s.what != CompareWhat.COST

        val costRows  = mutableListOf<CompareCostRow>()
        val usageRows = mutableListOf<CompareUsageRow>()

        for (slot in srcSlots) {
            val src = sourcesByKey[slot.sysSn] ?: continue
            val (gran, anchor) = resolveRange(s, slot.subjectId) ?: continue
            if (wantUsage) usageRows += sourceUsage(src, gran, anchor, s.advanced, slot.subjectId, slot.displayName)
            if (wantCost)  costRows  += sourceCosts(src, gran, anchor, s.advanced, plans, slot.subjectId, slot.displayName)
        }
        for (slot in simSlots) {
            val sim = simsByKey[slot.scenarioId] ?: continue
            val (gran, anchor) = resolveRange(s, slot.subjectId) ?: continue
            if (wantUsage) usageRows += simUsage(sim, gran, anchor, s.advanced, slot.subjectId, slot.displayName)
            if (wantCost)  costRows  += simCosts(sim, plans, slot.subjectId, slot.displayName)
        }
        return CompareResults(cost = costRows.sortedBy { it.net }, usage = usageRows)
    }

    /** Resolve a granularity + anchor to a concrete [from, to] clamped to the data. */
    private fun dateRange(
        gran: DataSourcePeriod, anchor: LocalDate, advanced: Boolean, start: String, finish: String
    ): Pair<String, String> {
        val dataStart = parseOr(start, LocalDate.now().minusYears(5))
        val dataEnd   = parseOr(finish, LocalDate.now())
        val (from, to) = periodDateRange(gran, anchor, advanced, dataStart, dataEnd)
        return from.coerceIn(dataStart, dataEnd).format(dateFmt) to
               to.coerceIn(dataStart, dataEnd).format(dateFmt)
    }

    private fun parseOr(s: String, fallback: LocalDate): LocalDate =
        runCatching { LocalDate.parse(s, dateFmt) }.getOrDefault(fallback)

    // ── data-source computation ─────────────────────────────────────────────

    private fun sourceUsage(
        src: CompareSourceItem, gran: DataSourcePeriod, anchor: LocalDate, advanced: Boolean,
        subjectId: String, subjectName: String
    ): CompareUsageRow {
        val (from, to) = dateRange(gran, anchor, advanced, src.startDate, src.finishDate)
        val rows = repository.getSumDOY(src.sysSn, from, to)
        val monthlyRows = repository.getSumMonth(src.sysSn, from, to)
        return CompareUsageRow(
            subjectId = subjectId,
            subjectName = subjectName,
            isSimulation = false,
            load = rows.sumOf { it.load },
            buy = rows.sumOf { it.buy },
            feed = rows.sumOf { it.feed },
            pv = rows.sumOf { it.pv },
            pv2load = rows.sumOf { it.pv2load },
            bat2load = rows.sumOf { it.bat2load },
            grid2bat = rows.sumOf { it.grid2bat },
            monthly = monthlyBuckets(monthlyRows)
        )
    }

    private fun sourceCosts(
        src: CompareSourceItem, gran: DataSourcePeriod, anchor: LocalDate, advanced: Boolean,
        plans: List<PricePlan>, subjectId: String, subjectName: String
    ): List<CompareCostRow> {
        val (from, to) = dateRange(gran, anchor, advanced, src.startDate, src.finishDate)
        val hourly = repository.getSelectedAlphaESSData(src.sysSn, from, to)
        val days = parseOr(to, LocalDate.now()).toEpochDay() - parseOr(from, LocalDate.now()).toEpochDay() + 1
        return plans.map { plan ->
            val dayRates = repository.getAllDayRatesForPricePlanID(plan.pricePlanIndex)
            val lookup = RateLookup(plan, dayRates)
            lookup.setStartDOY(parseOr(from, LocalDate.now()).dayOfYear)
            var buy = 0.0; var sell = 0.0
            val monthly = DoubleArray(12)
            val subTotals = SubTotals()
            hourly.forEach { row ->
                val ldt = LocalDateTime.parse(row.dateTime, rowFmt)
                val dow = ldt.dayOfWeek.value.let { if (it == 7) 0 else it }
                val price = lookup.getRate(ldt.dayOfYear, ldt.hour * 60 + ldt.minute, dow, row.buy)
                val rowBuy = price * row.buy
                val rowSell = plan.feed * row.feed
                buy += rowBuy
                sell += rowSell
                monthly[ldt.monthValue - 1] += (rowBuy - rowSell) / 100.0
                subTotals.addToPrice(price, row.buy)
            }
            // buy cost split by tariff rate band: price × kWh-at-that-price.
            // Keep the cheapest-first ordering of bands AND the matching rate list
            // (c/kWh) so the pie labels can show the rate that produced each slice.
            val sortedRates = subTotals.getPrices().sorted()
            val buyBands = sortedRates
                .map { p -> p * (subTotals.getSubTotalForPrice(p) ?: 0.0) / 100.0 }
            val fixed = plan.standingCharges * (days / 365.0)
            val coveredMonths = monthly.count { it != 0.0 }.coerceAtLeast(1)
            for (i in monthly.indices) if (monthly[i] != 0.0) monthly[i] += fixed / coveredMonths
            val net = (buy - sell) / 100.0 + fixed
            CompareCostRow(
                subjectId = subjectId,
                subjectName = subjectName,
                isSimulation = false,
                planId = plan.pricePlanIndex,
                planName = "${plan.supplier} · ${plan.planName}",
                available = true,
                net = net,
                buy = buy / 100.0,
                sell = sell / 100.0,
                fixed = fixed,
                bonus = plan.signUpBonus,
                buyBands = buyBands.ifEmpty { listOf(buy / 100.0) },
                buyBandRates = sortedRates.ifEmpty { emptyList() },
                monthlyNet = monthly.toList()
            )
        }
    }

    // ── simulation computation ──────────────────────────────────────────────

    private fun simUsage(
        sim: CompareSimItem, gran: DataSourcePeriod, anchor: LocalDate, advanced: Boolean,
        subjectId: String, subjectName: String
    ): CompareUsageRow {
        val dr = repository.getSimDateRanges(sim.scenarioId.toString())
        val (from, to) = dateRange(
            gran, anchor, advanced,
            dr?.startDate ?: "2001-01-01", dr?.finishDate ?: "2001-12-31"
        )
        val rows = repository.getSimSumDOY(sim.scenarioId.toString(), from, to)
        val monthlyRows = repository.getSimSumMonth(sim.scenarioId.toString(), from, to)
        return CompareUsageRow(
            subjectId = subjectId,
            subjectName = subjectName,
            isSimulation = true,
            load = rows.sumOf { it.load },
            buy = rows.sumOf { it.buy },
            feed = rows.sumOf { it.feed },
            pv = rows.sumOf { it.pv },
            pv2load = rows.sumOf { it.pv2load },
            bat2load = rows.sumOf { it.bat2load },
            grid2bat = rows.sumOf { it.grid2bat },
            monthly = monthlyBuckets(monthlyRows)
        )
    }

    private fun simCosts(
        sim: CompareSimItem, plans: List<PricePlan>,
        subjectId: String, subjectName: String
    ): List<CompareCostRow> {
        // Simulations carry pre-computed annual costings (CostingWorker output).
        val costings = repository.getAllCostingsForScenario(sim.scenarioId)
        return plans.map { plan ->
            val c = costings.firstOrNull { it.pricePlanID == plan.pricePlanIndex }
            val net = (c?.net ?: 0.0) / 100.0
            val buy = (c?.buy ?: 0.0) / 100.0
            val st = c?.subTotals
            val sortedRates = if (st != null && st.getPrices().isNotEmpty())
                st.getPrices().sorted() else emptyList()
            val buyBands =
                if (sortedRates.isNotEmpty())
                    sortedRates.map { p -> p * (st!!.getSubTotalForPrice(p) ?: 0.0) / 100.0 }
                else listOf(buy)
            CompareCostRow(
                subjectId = subjectId,
                subjectName = subjectName,
                isSimulation = true,
                planId = plan.pricePlanIndex,
                planName = "${plan.supplier} · ${plan.planName}",
                available = c != null,
                net = net,
                buy = buy,
                sell = (c?.sell ?: 0.0) / 100.0,
                fixed = plan.standingCharges,
                bonus = plan.signUpBonus,
                buyBands = buyBands,
                buyBandRates = sortedRates,
                monthlyNet = List(12) { net / 12.0 }
            )
        }
    }

    /** Bucket "yyyyMM" interval rows into 12 calendar-month slots. */
    private fun monthlyBuckets(
        rows: List<com.tfcode.comparetout.model.importers.IntervalRow>
    ): Map<String, List<Double>> {
        val load = DoubleArray(12); val buy = DoubleArray(12)
        val feed = DoubleArray(12); val pv = DoubleArray(12)
        rows.forEach { row ->
            val s = row.interval ?: return@forEach
            val month = if (s.length == 6)
                s.substring(4).toIntOrNull()?.takeIf { it in 1..12 } else null
            val idx = (month ?: return@forEach) - 1
            load[idx] += row.load; buy[idx] += row.buy
            feed[idx] += row.feed; pv[idx] += row.pv
        }
        return mapOf(
            "load" to load.toList(), "buy" to buy.toList(),
            "feed" to feed.toList(), "pv" to pv.toList()
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────
// CompareState ⇄ JSON  (stored as a single string in the shared UI prefs)
// ──────────────────────────────────────────────────────────────────────────

private fun encodeState(s: CompareState): String = JSONObject().apply {
    put("what", s.what.name)
    put("sources", JSONArray(s.sources.toList()))
    put("sims", JSONArray(s.sims.toList()))
    put("plans", JSONArray(s.plans.toList()))
    put("series", JSONArray(s.series.toList()))
    put("advanced", s.advanced)
    put("sync", s.sync)
    put("globalGran", s.globalGran?.name ?: JSONObject.NULL)
    put("globalAnchor", s.globalAnchor.toString())
    put("perSubjectRanges", JSONObject().apply {
        s.perSubjectRanges.forEach { (id, r) ->
            put(id, JSONObject().apply {
                put("gran", r.gran?.name ?: JSONObject.NULL)
                put("anchor", r.anchor.toString())
            })
        }
    })
    put("mode", s.mode.name)
    put("layout", s.layout.name)
}.toString()

private fun decodeState(json: String): CompareState? {
    if (json.isBlank()) return null
    return runCatching {
        val o = JSONObject(json)
        fun strSet(k: String): Set<String> {
            val a = o.optJSONArray(k) ?: return emptySet()
            return (0 until a.length()).map { a.getString(it) }.toSet()
        }
        fun longSet(k: String): Set<Long> {
            val a = o.optJSONArray(k) ?: return emptySet()
            return (0 until a.length()).map { a.getLong(it) }.toSet()
        }
        fun strList(k: String): List<String> {
            val a = o.optJSONArray(k) ?: return emptyList()
            return (0 until a.length()).map { a.getString(it) }
        }
        fun longList(k: String): List<Long> {
            val a = o.optJSONArray(k) ?: return emptyList()
            return (0 until a.length()).map { a.getLong(it) }
        }
        fun granOf(s: String?): DataSourcePeriod? =
            if (s.isNullOrEmpty()) null
            else runCatching { DataSourcePeriod.valueOf(s) }.getOrNull()
        fun dateOf(s: String?, def: LocalDate): LocalDate =
            runCatching { LocalDate.parse(s) }.getOrDefault(def)
        val perSubject = mutableMapOf<String, SubjectRange>()
        o.optJSONObject("perSubjectRanges")?.let { pr ->
            pr.keys().forEach { k ->
                val e = pr.getJSONObject(k)
                perSubject[k] = SubjectRange(
                    gran = granOf(if (e.isNull("gran")) null else e.optString("gran")),
                    anchor = dateOf(e.optString("anchor"), LocalDate.now())
                )
            }
        }
        CompareState(
            what = runCatching { CompareWhat.valueOf(o.getString("what")) }.getOrDefault(CompareWhat.COST),
            // Sources/sims are ordered List now — the old persisted shape is a
            // JSON array of the same primitives so reads still work, and a
            // pre-duplicate-feature state file simply has no repeats.
            sources = strList("sources"),
            sims = longList("sims"),
            plans = longSet("plans"),
            series = strSet("series"),
            advanced = o.optBoolean("advanced", false),
            sync = o.optBoolean("sync", true),
            globalGran = granOf(if (o.isNull("globalGran")) null else o.optString("globalGran")),
            globalAnchor = dateOf(o.optString("globalAnchor"), LocalDate.now()),
            perSubjectRanges = perSubject,
            mode = runCatching { CompareMode.valueOf(o.getString("mode")) }.getOrDefault(CompareMode.TABLE),
            layout = runCatching { CompareLayout.valueOf(o.getString("layout")) }.getOrDefault(CompareLayout.MERGED)
        )
    }.getOrNull()
}
