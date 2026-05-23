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
    val sources: Set<String> = emptySet(),     // sysSn
    val sims: Set<Long> = emptySet(),          // scenarioIndex
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

/** Stable id for a subject (used as the per-subject range map key). */
fun sourceSubjectId(sysSn: String) = "src:$sysSn"
fun simSubjectId(scenarioId: Long) = "sim:$scenarioId"

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
     * Keep state self-consistent: drop filter series that no longer apply to the
     * chosen comparison type, and (un-synced) seed a range for every subject.
     */
    private fun normalize(s: CompareState): CompareState {
        var r = s
        val valid = validSeriesIds(s.what)
        if (!s.series.all { it in valid }) {
            r = r.copy(series = s.series.filterTo(mutableSetOf()) { it in valid })
        }
        if (!r.sync) {
            val ids = r.sources.map { sourceSubjectId(it) } + r.sims.map { simSubjectId(it) }
            if (ids.any { it !in r.perSubjectRanges }) {
                val filled = r.perSubjectRanges.toMutableMap()
                ids.forEach { id ->
                    if (id !in filled) filled[id] = SubjectRange(r.globalGran, r.globalAnchor)
                }
                r = r.copy(perSubjectRanges = filled)
            }
        }
        return r
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
        val ids = s.sources.map { sourceSubjectId(it) } + s.sims.map { simSubjectId(it) }
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
        s.what, s.sources.sorted(), s.sims.sorted(), s.plans.sorted(),
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
        val sources = sourceItems.value.filter { it.sysSn in s.sources }
        val sims    = simItems.value.filter { it.scenarioId in s.sims }
        val plans   = _plans.value.filter { it.pricePlanIndex in s.plans }

        val wantCost  = s.what != CompareWhat.USAGE
        val wantUsage = s.what != CompareWhat.COST

        val costRows  = mutableListOf<CompareCostRow>()
        val usageRows = mutableListOf<CompareUsageRow>()

        for (src in sources) {
            val (gran, anchor) = resolveRange(s, sourceSubjectId(src.sysSn)) ?: continue
            if (wantUsage) usageRows += sourceUsage(src, gran, anchor, s.advanced)
            if (wantCost)  costRows  += sourceCosts(src, gran, anchor, s.advanced, plans)
        }
        for (sim in sims) {
            val (gran, anchor) = resolveRange(s, simSubjectId(sim.scenarioId)) ?: continue
            if (wantUsage) usageRows += simUsage(sim, gran, anchor, s.advanced)
            if (wantCost)  costRows  += simCosts(sim, plans)
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
        src: CompareSourceItem, gran: DataSourcePeriod, anchor: LocalDate, advanced: Boolean
    ): CompareUsageRow {
        val (from, to) = dateRange(gran, anchor, advanced, src.startDate, src.finishDate)
        val rows = repository.getSumDOY(src.sysSn, from, to)
        val monthlyRows = repository.getSumMonth(src.sysSn, from, to)
        return CompareUsageRow(
            subjectId = sourceSubjectId(src.sysSn),
            subjectName = src.sysSn,
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
        plans: List<PricePlan>
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
                subjectId = sourceSubjectId(src.sysSn),
                subjectName = src.sysSn,
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
        sim: CompareSimItem, gran: DataSourcePeriod, anchor: LocalDate, advanced: Boolean
    ): CompareUsageRow {
        val dr = repository.getSimDateRanges(sim.scenarioId.toString())
        val (from, to) = dateRange(
            gran, anchor, advanced,
            dr?.startDate ?: "2001-01-01", dr?.finishDate ?: "2001-12-31"
        )
        val rows = repository.getSimSumDOY(sim.scenarioId.toString(), from, to)
        val monthlyRows = repository.getSimSumMonth(sim.scenarioId.toString(), from, to)
        return CompareUsageRow(
            subjectId = simSubjectId(sim.scenarioId),
            subjectName = sim.name,
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

    private fun simCosts(sim: CompareSimItem, plans: List<PricePlan>): List<CompareCostRow> {
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
                subjectId = simSubjectId(sim.scenarioId),
                subjectName = sim.name,
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
            sources = strSet("sources"),
            sims = longSet("sims"),
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
