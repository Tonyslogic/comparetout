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
import com.tfcode.comparetout.model.scenario.EVCharge
import com.tfcode.comparetout.model.scenario.Inverter
import com.tfcode.comparetout.scenario.loadprofile.StandardLoadProfiles
import com.tfcode.comparetout.model.scenario.DOWDist
import com.tfcode.comparetout.model.scenario.HourlyDist
import com.tfcode.comparetout.model.scenario.LoadProfile
import com.tfcode.comparetout.model.scenario.MonthHolder
import com.tfcode.comparetout.model.scenario.MonthlyDist
import com.tfcode.comparetout.model.scenario.Scenario
import com.tfcode.comparetout.model.scenario.ScenarioComponents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

enum class ScenarioMode { NEW, COPY, LINK }
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
    // Inverters
    val inverterEntries: List<WizardInverterEntry> = emptyList()
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

    fun toScenarioComponents(): ScenarioComponents {
        val sc = Scenario().also { it.scenarioName = scenarioName }
        return ScenarioComponents(
            sc, inverterEntries.map { it.toInverter() }, emptyList(), emptyList(), null, toLoadProfile(),
            emptyList(), emptyList(), evEntries.map { it.toEvCharge() },
            emptyList(), null, emptyList()
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

    // Shell with EV+inverters from builder but no load profile — used for load-linked saves
    fun toScenarioShellWithEV(): ScenarioComponents {
        val sc = Scenario().also { it.scenarioName = scenarioName }
        return ScenarioComponents(
            sc, inverterEntries.map { it.toInverter() }, emptyList(), emptyList(), null, LoadProfile(),
            emptyList(), emptyList(), evEntries.map { it.toEvCharge() },
            emptyList(), null, emptyList()
        )
    }
}

private const val KEY_NOVICE_MODE = "wizard_novice_mode"

sealed class WizardSaveResult {
    object Idle : WizardSaveResult()
    object Saving : WizardSaveResult()
    data class Done(val scenarioId: Long, val runSimulation: Boolean) : WizardSaveResult()
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
                if (seen.add(r.sysSn)) out.add(SourceDateRange(r.sysSn, r.startDate, r.finishDate, ComparisonUIViewModel.Importer.ESBNHDF))
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
            _expandedSections.value = setOf("start", wizardSection)
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

    private fun populateBuilderFrom(fromId: Long, isLinked: Boolean, keepName: Boolean) {
        val c = repository.getScenarioComponentsForScenarioID(fromId)
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
            inverterEntries = c.inverters?.map { it.toWizardInverterEntry() } ?: emptyList()
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

    fun addEvEntry() = updateBuilder { it.copy(evEntries = it.evEntries + WizardEvEntry()) }

    fun removeEvEntry(id: String) =
        updateBuilder { it.copy(evEntries = it.evEntries.filter { e -> e.id != id }) }

    fun updateEvEntry(id: String, transform: (WizardEvEntry) -> WizardEvEntry) =
        updateBuilder { b ->
            b.copy(evEntries = b.evEntries.map { if (it.id == id) transform(it) else it })
        }

    fun addInverterEntry() = updateBuilder { it.copy(inverterEntries = it.inverterEntries + WizardInverterEntry()) }

    fun removeInverterEntry(id: String) =
        updateBuilder { it.copy(inverterEntries = it.inverterEntries.filter { e -> e.id != id }) }

    fun updateInverterEntry(id: String, transform: (WizardInverterEntry) -> WizardInverterEntry) =
        updateBuilder { b ->
            b.copy(inverterEntries = b.inverterEntries.map { if (it.id == id) transform(it) else it })
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
                val savedId: Long = when {
                    isEditMode -> {
                        // Update existing scenario in place
                        val existing = repository.getScenarioComponentsForScenarioID(scenarioId)
                        existing.scenario?.also { sc ->
                            sc.scenarioName = b.scenarioName
                            sc.setHasEVCharges(b.evEntries.isNotEmpty())
                            sc.setHasInverters(b.inverterEntries.isNotEmpty())
                        }?.let { repository.updateScenario(it) }
                        repository.saveLoadProfile(scenarioId, b.toLoadProfile())
                        // Replace EV charges
                        existing.evCharges?.forEach { ev ->
                            repository.deleteEVChargeFromScenario(ev.evChargeIndex, scenarioId)
                        }
                        b.evEntries.forEach { entry ->
                            repository.saveEVChargeForScenario(scenarioId, entry.toEvCharge())
                        }
                        // Replace inverters (panels are managed separately via the legacy UI)
                        existing.inverters?.forEach { inv ->
                            repository.deleteInverterFromScenario(inv.inverterIndex, scenarioId)
                        }
                        b.inverterEntries.forEach { entry ->
                            repository.saveInverter(scenarioId, entry.toInverter())
                        }
                        scenarioId
                    }
                    b.isLinked -> {
                        // Full link: create shell scenario, then link load profile, EV, and inverters
                        val newId = repository.insertScenarioAndReturnID(b.toScenarioShell(), false)
                        repository.linkLoadProfileFromScenario(b.basedOnId, newId)
                        repository.linkEVChargeFromScenario(b.basedOnId, newId)
                        repository.linkInverterFromScenario(b.basedOnId, newId)
                        newId
                    }
                    b.loadSource == LoadSource.LINKED -> {
                        // Load-profile link only: create scenario with EV from builder, link load
                        val newId = repository.insertScenarioAndReturnID(b.toScenarioShellWithEV(), false)
                        repository.linkLoadProfileFromScenario(b.basedOnId, newId)
                        newId
                    }
                    else -> {
                        // NEW or COPY: all data is in the builder already
                        repository.insertScenarioAndReturnID(b.toScenarioComponents(), false)
                    }
                }
                _saveResult.value = WizardSaveResult.Done(savedId, runSimulation)
            } catch (_: Exception) {
                _saveResult.value = WizardSaveResult.Idle
            }
        }
    }
}
