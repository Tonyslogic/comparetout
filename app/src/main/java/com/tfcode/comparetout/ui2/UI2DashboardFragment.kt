package com.tfcode.comparetout.ui2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.MainActivity
import com.tfcode.comparetout.R
import com.tfcode.comparetout.TOUTCApplication
import com.tfcode.comparetout.profile.AppProfiles
import com.tfcode.comparetout.region.RegionProfiles
import com.tfcode.comparetout.model.costings.Costings
import com.tfcode.comparetout.model.scenario.LoadProfile
import com.tfcode.comparetout.model.scenario.Panel
import com.tfcode.comparetout.model.scenario.PanelPVSummary
import com.tfcode.comparetout.model.scenario.ScenarioComponents
import com.tfcode.comparetout.model.scenario.SimKPIs
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.time.LocalDate

private val StatusGreen = Color(0xFF0B8043)
private val StatusRed = Color(0xFFD32F2F)
private val StatusAmber = Color(0xFFF9A825)

// ── Simulation readiness ────────────────────────────────────────────────────
// A scenario's dashboard is only fully meaningful once it has usage data, PV
// data for its panels, and a completed costing run. Until then the individual
// accordions show a red triangle / a missing sun — easy to miss. We collect the
// outstanding reasons into one "Needs attention" accordion at the top so the
// user sees *why* the dashboard looks incomplete and how to fix it.

private enum class IssueSeverity { WARNING, INFO }

private data class SimReadinessIssue(
    val title: String,
    val hint: String,
    val severity: IssueSeverity,
    val wizardSection: String? = null   // non-null → row taps through to the wizard
)

@Composable
private fun simReadinessIssues(
    hasLoadProfile: Boolean,
    hasPanels: Boolean,
    hasPanelData: Boolean,
    hpWeatherMissing: Boolean,
    resultsReady: Boolean
): List<SimReadinessIssue> {
    val issues = mutableListOf<SimReadinessIssue>()
    if (!hasLoadProfile) {
        issues += SimReadinessIssue(
            title = stringResource(R.string.ui2_dash_issue_no_usage_title),
            hint = stringResource(R.string.ui2_dash_issue_no_usage_hint),
            severity = IssueSeverity.WARNING,
            wizardSection = "load"
        )
    }
    if (hasPanels && !hasPanelData) {
        issues += SimReadinessIssue(
            title = stringResource(R.string.ui2_dash_issue_solar_title),
            hint = stringResource(R.string.ui2_dash_issue_solar_hint),
            severity = IssueSeverity.WARNING,
            wizardSection = "pv"
        )
    }
    if (hpWeatherMissing) {
        issues += SimReadinessIssue(
            title = stringResource(R.string.ui2_dash_issue_weather_title),
            hint = stringResource(R.string.ui2_dash_issue_weather_hint),
            severity = IssueSeverity.WARNING,
            wizardSection = "heatpump"
        )
    }
    if (!resultsReady) {
        issues += SimReadinessIssue(
            title = stringResource(R.string.ui2_dash_issue_results_title),
            hint = stringResource(R.string.ui2_dash_issue_results_hint),
            severity = IssueSeverity.INFO
        )
    }
    return issues
}

@Composable
private fun SimulationStatusAccordion(
    issues: List<SimReadinessIssue>,
    onFix: (String) -> Unit
) {
    if (issues.isEmpty()) return
    val anyWarning = issues.any { it.severity == IssueSeverity.WARNING }
    ExpandableCard(
        title = stringResource(R.string.ui2_dash_needs_attention),
        initiallyExpanded = true,
        leadingIcon = {
            Icon(
                painterResource(R.drawable.ic_baseline_warning_24), null,
                Modifier.size(24.dp),
                tint = if (anyWarning) StatusAmber else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = { _ ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = (if (anyWarning) StatusAmber else MaterialTheme.colorScheme.onSurfaceVariant)
                    .copy(alpha = 0.15f)
            ) {
                Text(
                    issues.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (anyWarning) StatusAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        },
        showEdit = false
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            issues.forEach { issue ->
                val tint = if (issue.severity == IssueSeverity.WARNING) StatusAmber
                           else MaterialTheme.colorScheme.onSurfaceVariant
                val iconRes = if (issue.severity == IssueSeverity.WARNING)
                    R.drawable.ic_baseline_warning_24 else R.drawable.ic_baseline_info_24
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (issue.wizardSection != null)
                                Modifier.clickable { onFix(issue.wizardSection) }
                            else Modifier
                        ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(painterResource(iconRes), null, Modifier.size(18.dp), tint = tint)
                    Column(Modifier.weight(1f)) {
                        Text(issue.title, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(issue.hint, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (issue.wizardSection != null) {
                            Text(stringResource(R.string.ui2_dash_tap_wizard),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

// ── Solar-data-needs-refreshing banner ───────────────────────────────────────
// After the millis rollout (PanelDataRefreshWorker, plans/sim/timezone-and-rollout.md), PVGIS panels are
// auto-refetched but source-derived panels can't be — their serials are written to the
// `paneldata_needs_regen_sources` DataStore key. This app-wide banner surfaces them so the user knows to
// re-import those sources (their solar otherwise reads as zero). Dismiss clears the key.
@Composable
private fun NeedsRegenBanner(refreshKey: Any?) {
    val context = LocalContext.current
    val app = context.applicationContext as TOUTCApplication
    val scope = rememberCoroutineScope()
    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return
    val sources by produceState(initialValue = emptyList<String>(), refreshKey) {
        value = withContext(Dispatchers.IO) {
            runCatching { app.getStringValueFromDataStore("paneldata_needs_regen_sources") }
                .getOrDefault("")
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
    if (sources.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = StatusAmber.copy(alpha = 0.12f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(painterResource(R.drawable.ic_baseline_warning_24), null,
                    Modifier.size(22.dp), tint = StatusAmber)
                Text(stringResource(R.string.ui2_dash_regen_title),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.ui2_dash_regen_body, sources.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    context.startActivity(Intent(context, UI2DataSourceManagementActivity::class.java))
                }) { Text(stringResource(R.string.ui2_dash_manage_sources)) }
                OutlinedButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        runCatching { app.putStringValueIntoDataStore("paneldata_needs_regen_sources", "") }
                    }
                    dismissed = true
                }) { Text(stringResource(R.string.ui2_dismiss)) }
            }
        }
    }
}

// ── One-time data-migration status banner ────────────────────────────────────
// The millis rollout runs two one-time background workers (TimezoneRestampWorker = tz_restamp_v1,
// PanelDataRefreshWorker = paneldata_refresh_v1) from Application.onCreate. This banner gives an IN-APP signal of
// their progress: it shows each one that is still pending/running (with live %), and hides once both are done —
// so an absent banner means "fully migrated". "Done" is taken from the persisted DataStore flags (authoritative
// even after WorkManager prunes finished work) OR a live SUCCEEDED WorkInfo.
@Composable
private fun MigrationStatusBanner(refreshKey: Any?) {
    val context = LocalContext.current
    val app = context.applicationContext as TOUTCApplication

    val tzInfos by remember(context) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData("tz_restamp_v1")
    }.observeAsState(emptyList())
    val pdInfos by remember(context) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData("paneldata_refresh_v1")
    }.observeAsState(emptyList())

    // Re-read the persisted done flags whenever the dashboard refreshes or a worker's state changes.
    val flags by produceState(initialValue = false to false, refreshKey, tzInfos, pdInfos) {
        value = withContext(Dispatchers.IO) {
            val tz = runCatching { app.getStringValueFromDataStore("tz_restamp_v1_done") }.getOrDefault("")
            val pd = runCatching { app.getStringValueFromDataStore("paneldata_refresh_v1_done") }.getOrDefault("")
            (tz == "true") to (pd == "true")
        }
    }

    // Templates resolved here — statusLine is a plain local fun, not composable.
    val tplPct = stringResource(R.string.ui2_dash_mig_in_progress_pct)
    val tplRunning = stringResource(R.string.ui2_dash_mig_in_progress)
    val tplQueued = stringResource(R.string.ui2_dash_mig_queued)
    val tplPending = stringResource(R.string.ui2_dash_mig_pending)
    fun statusLine(label: String, doneFlag: Boolean, infos: List<WorkInfo>): String? {
        if (doneFlag || infos.any { it.state == WorkInfo.State.SUCCEEDED }) return null  // complete → hide
        val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
        return when {
            running != null -> {
                val pct = running.progress.getInt("pct", -1)
                if (pct >= 0) tplPct.format(label, pct) else tplRunning.format(label)
            }
            infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED } ->
                tplQueued.format(label)
            else -> tplPending.format(label)
        }
    }

    val lines = listOfNotNull(
        statusLine(stringResource(R.string.ui2_dash_mig_tz), flags.first, tzInfos),
        statusLine(stringResource(R.string.ui2_dash_mig_pd), flags.second, pdInfos)
    )
    if (lines.isEmpty()) return  // both migrations complete → nothing to show

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = StatusAmber.copy(alpha = 0.12f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.ui2_dash_mig_title),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            lines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.ui2_dash_mig_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Grid-import (MIC) soft-cap breach flag (item 4c) ─────────────────────────
// Shown in the simulation "Explore data" accordion when the run drew more from the grid than the scenario's
// Maximum Import Capacity in one or more intervals. The model doesn't clamp grid import (the grid will deliver),
// so this is surfaced as a fault to investigate. Tapping opens the worst breaching times so the user can open
// the graphs for those days. Derived from stored data — no schema change.
@Composable
private fun MICBreachFlag(info: MICBreachInfo, showHints: Boolean) {
    var showDialog by remember { mutableStateOf(false) }
    val kwDf = remember { DecimalFormat("#,##0.0") }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = StatusAmber.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth().clickable { showDialog = true }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(painterResource(R.drawable.ic_baseline_warning_24), null, Modifier.size(18.dp), tint = StatusAmber)
            Column(Modifier.weight(1f)) {
                Text(pluralStringResource(R.plurals.ui2_dash_mic_flag, info.count, info.count),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.ui2_dash_mic_tap),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (showHints) {
        Text(
            stringResource(R.string.ui2_dash_mic_hint, kwDf.format(info.micKw)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }

    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.ui2_dash_mic_title),
                        style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        pluralStringResource(R.plurals.ui2_dash_mic_body, info.count,
                            kwDf.format(info.micKw), info.count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.ui2_dash_mic_worst),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Column(
                        modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        info.sample.forEach { b ->
                            val hh = b.minuteOfDay / 60
                            val mm = b.minuteOfDay % 60
                            Text(stringResource(R.string.ui2_dash_mic_row,
                                    b.date, hh, mm, kwDf.format(b.kw), kwDf.format(info.micKw)),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (info.count > info.sample.size) {
                            Text(stringResource(R.string.ui2_dash_more_n,
                                    info.count - info.sample.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showDialog = false }) {
                            Text(stringResource(R.string.ui2_close))
                        }
                    }
                }
            }
        }
    }
}

@AndroidEntryPoint
class UI2DashboardFragment : Fragment() {

    private val viewModel: UI2DashboardViewModel by viewModels()
    private val sharedViewModel: UI2SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("UI2", "UI2DashboardFragment.onCreateView")
        val onSwitchLegacy: () -> Unit = {
            CoroutineScope(Dispatchers.IO).launch {
                val app = requireActivity().application as TOUTCApplication
                app.putStringValueIntoDataStore("use_ui2", "false")
                val intent = Intent(requireContext(), MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                requireActivity().startActivity(intent)
            }
        }
        val onLaunchGraphs: () -> Unit = {
            findNavController().navigate(R.id.action_dashboard_to_graphs)
        }
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                UI2Theme {
                    DashboardScreen(viewModel = viewModel, onSwitchLegacy = onSwitchLegacy, onLaunchGraphs = onLaunchGraphs)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Picks up changes made on side trips (e.g. toggling a plan active in
        // UI2PricePlanListActivity, edits in UI2WizardActivity) without
        // requiring the user to re-select the active subject.
        viewModel.refresh()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("UI2", "UI2DashboardFragment.onViewCreated — observing dashboardData and sharedVM")

        viewModel.dashboardData.observe(viewLifecycleOwner) { data ->
            Log.d("UI2", "UI2DashboardFragment: dashboardData emitted — scenarioName=${data?.scenarioComponents?.scenario?.scenarioName ?: "null"} bestCosting=${data?.bestCosting?.net ?: "null"}")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.activeSelection.collect { sel ->
                    Log.d("UI2", "UI2DashboardFragment: activeSelection=$sel")
                    when (sel) {
                        is UI2SharedViewModel.ActiveSelection.Simulation ->
                            viewModel.setActiveSimulationId(sel.id)
                        is UI2SharedViewModel.ActiveSelection.DataSource ->
                            viewModel.setActiveDataSource(sel.sysSn, sel.importerType, sel.startDate, sel.endDate)
                        UI2SharedViewModel.ActiveSelection.None ->
                            viewModel.clearActive()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun DashboardScreen(viewModel: UI2DashboardViewModel, onSwitchLegacy: () -> Unit, onLaunchGraphs: () -> Unit) {
    // Component-visibility gating (App settings) — mirrors the wizard: a hidden
    // component's accordion is not rendered, its data is untouched.
    val uiVis = rememberUiVisibility()
    val dashboardData by viewModel.dashboardData.observeAsState(initial = null)
    val panelSummary by viewModel.panelPVSummary.observeAsState(emptyList())
    val explorePeriod  by viewModel.explorePeriod.observeAsState(DataSourcePeriod.ALL)
    val exploreAnchor  by viewModel.exploreAnchor.observeAsState(LocalDate.now())
    val exploreTotals  by viewModel.exploreTotals.observeAsState(null)
    val usagePeriod        by viewModel.usagePeriod.observeAsState(DataSourcePeriod.ALL)
    val usageAnchor        by viewModel.usageAnchor.observeAsState(LocalDate.now())
    val usageTotals        by viewModel.usageTotals.observeAsState(null)
    val usageDistribution  by viewModel.usageDistribution.observeAsState(null)
    val tariffPeriod   by viewModel.tariffPeriod.observeAsState(DataSourcePeriod.ALL)
    val tariffAnchor   by viewModel.tariffAnchor.observeAsState(LocalDate.now())
    val tariffCostings by viewModel.tariffCostings.observeAsState(null)
    val kpiPeriod      by viewModel.kpiPeriod.observeAsState(DataSourcePeriod.MONTH)
    val kpiAnchor      by viewModel.kpiAnchor.observeAsState(LocalDate.now())
    val kpiMonthFilter by viewModel.kpiMonthFilter.observeAsState(LocalDate.now().monthValue)
    val kpiSummary     by viewModel.kpiSummary.observeAsState(null)
    val kpiMonths      by viewModel.kpiMonths.observeAsState(null)
    val scenarioTariffPeriod   by viewModel.scenarioTariffPeriod.observeAsState(DataSourcePeriod.ALL)
    val scenarioTariffAnchor   by viewModel.scenarioTariffAnchor.observeAsState(LocalDate.now())
    val scenarioTariffCostings by viewModel.scenarioTariffCostings.observeAsState(null)
    val dataBounds             by viewModel.dataBounds.observeAsState(null)
    val favouritePlanId        by viewModel.favouritePlanId.observeAsState(null)
    // initial=true to avoid the empty-state card flashing on screens that already
    // have scenarios — once the LiveData emits the real value, this drops to its
    // proper state. Only matters for ≤1 recompose, but the flash is visible.
    val hasScenarios           by viewModel.hasScenarios.observeAsState(initial = true)
    // initial=true for the same reason — assume there's a pinned subject during
    // the first recompose so we don't flash NoActiveSubjectCard while restore
    // is still in flight. Drops to false only after a confirmed None.
    val hasActiveItem          by viewModel.hasActiveItem.observeAsState(initial = true)
    // Re-pull DB-backed dashboard surfaces when the Simulation/Cost work chain
    // (PVGIS → GenerateLoad → Simulate → Cost, kicked off by the wizard or by
    // SampleDataLoader) reaches a successful terminal state. Without this the
    // dashboard would sit on its pre-simulation snapshot — missing panel data,
    // missing costings — until the user explicitly re-selected the scenario.
    val context = LocalContext.current
    val simulationWorkInfos by remember(context) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData("Simulation")
    }.observeAsState(emptyList())
    val simChainFinished = simulationWorkInfos.isNotEmpty() &&
        simulationWorkInfos.all { it.state.isFinished } &&
        simulationWorkInfos.any { it.state == WorkInfo.State.SUCCEEDED }
    LaunchedEffect(simChainFinished, simulationWorkInfos.size) {
        if (simChainFinished) viewModel.refresh()
    }
    var showDrawer by remember { mutableStateOf(false) }
    val (showHints, toggleShowHints) = rememberShowHints()
    val df = remember { DecimalFormat("#,##0.00") }
    val kwhDf = remember { DecimalFormat("#,##0.0") }

    SideEffect {
        Log.d("UI2", "DashboardScreen recompose: scenarioName=${dashboardData?.scenarioComponents?.scenario?.scenarioName ?: "null"}")
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val title = dashboardData?.dataSourceInfo?.run { "$sysSn  ·  $displayTypeName" }
        ?: dashboardData?.scenarioComponents?.scenario?.scenarioName
        ?: stringResource(R.string.ui2_dash_select_sim)

    Scaffold(
        modifier = Modifier
            .nestedScroll(rememberNestedScrollInteropConnection())
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    IconButton(onClick = { showDrawer = true }) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.ui2_menu))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    // Stable resourceId so the Robo walkthrough can ELEMENT_SCROLL_INTO_VIEW
                    // deep accordions (e.g. Visual overview) into view before tapping.
                    .semantics { testTagsAsResourceId = true }
                    .testTag("dashboard_scroll")
                    .verticalScroll(rememberScrollState())
            ) {
            // App-wide one-time-migration status: visible while the rollout workers run, gone once migrated.
            MigrationStatusBanner(refreshKey = dashboardData)
            // App-wide: sources whose solar couldn't be auto-refreshed after the millis rollout. Re-reads
            // when the dashboard refreshes (dashboardData changes after the rollout re-sim completes).
            NeedsRegenBanner(refreshKey = dashboardData)
            val pvPeriod    by viewModel.pvPeriod.observeAsState(DataSourcePeriod.ALL)
    val pvAnchor    by viewModel.pvAnchor.observeAsState(LocalDate.now())
    val pvChartData by viewModel.pvChartData.observeAsState(null)

            // First-run vs deleted-active-subject empty states.
            //
            // [hasActiveItem] = the dashboard VM has a pinned subject. Goes
            // false on first launch (before restore) and when the deletion
            // guards in UI2SharedViewModel clear the saved subject because the
            // scenario or data source it pointed at no longer exists.
            //
            // If nothing is pinned: show the original sample-data welcome card
            // when no scenarios exist at all, otherwise direct the user to
            // pick a subject from the navigation drawer. Profiles without
            // scenarios have no sample/wizard to offer — their welcome card
            // points at data-source management instead.
            if (!hasActiveItem) {
                when {
                    // Whether first-run or deleted-subject, the remedy in the
                    // source edition is the same: connect/pick a data source.
                    !AppProfiles.current.hasScenarios -> EmptyDashboardConnectSourceCard()
                    !hasScenarios -> EmptyDashboardSampleCard()
                    else -> NoActiveSubjectCard()
                }
            } else {
                // Gate the entire dashboard content on hasActiveItem rather than
                // on dashboardData's shape. clearActive() nulls _activeItem
                // synchronously, but dashboardData is downstream of a Flow chain
                // — there's a brief window where it still holds the previous
                // subject's data, and we'd otherwise render its accordions for
                // a frame between the empty-card swap and the Flow re-emitting
                // DashboardData(null, null, null). Skipping the whole block
                // until hasActiveItem flips back to true avoids that.

            val dsInfo = dashboardData?.dataSourceInfo
            if (dsInfo != null) {
                // ── Data source mode ──────────────────────────────────────────
                // Tariff Plan first — pricing is the most-asked-after answer
                // when a real meter is selected.
                ExpandableCard(
                    title = stringResource(R.string.ui2_dash_tariff_plan),
                    leadingIcon = {
                        Icon(painterResource(R.drawable.ic_baseline_euro_symbol_24), null,
                            Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
                    },
                    trailingContent = { _ ->
                        if (tariffCostings != null)
                            Icon(painterResource(R.drawable.tick), null, Modifier.size(18.dp), tint = Color.Unspecified)
                    },
                    onEdit = {
                        context.startActivity(Intent(context, UI2PricePlanListActivity::class.java))
                    }
                ) {
                    PeriodSelector(
                        selectedPeriod = tariffPeriod,
                        anchorDate     = tariffAnchor,
                        dataStart      = dsInfo.startDate,
                        dataEnd        = dsInfo.endDate,
                        onPeriodChange = { p, a, adv -> viewModel.setTariffPeriod(p, a, adv) },
                        onNavigate     = { fwd, adv -> viewModel.navigateTariff(fwd, adv) }
                    )
                    Spacer(Modifier.height(8.dp))
                    DataSourceCostingsTable(
                        costings = tariffCostings,
                        df       = df,
                        favouritePlanId = favouritePlanId
                    )
                }

                ExpandableCard(
                    title = stringResource(R.string.ui2_dash_explore),
                    leadingIcon = {
                        Icon(painterResource(R.drawable.ic_baseline_download_24), null,
                            Modifier.size(24.dp), tint = MaterialTheme.colorScheme.secondary)
                    },
                    trailingContent = { expanded ->
                        if (expanded) {
                            Spacer(Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .clickable { onLaunchGraphs() }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(painterResource(R.drawable.barchart),
                                    stringResource(R.string.ui2_dash_view_graphs),
                                    Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    showEdit = false
                ) {
                    Text("${dsInfo.displayTypeName}  ·  ${dsInfo.startDate} – ${dsInfo.endDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp))
                    PeriodSelector(
                        selectedPeriod = explorePeriod,
                        anchorDate     = exploreAnchor,
                        dataStart      = dsInfo.startDate,
                        dataEnd        = dsInfo.endDate,
                        onPeriodChange = { p, a, adv -> viewModel.setExplorePeriod(p, a, adv) },
                        onNavigate     = { fwd, adv -> viewModel.navigateExplore(fwd, adv) }
                    )
                    Spacer(Modifier.height(8.dp))
                    DataSourceExplorePies(
                        importerType = dsInfo.importerType,
                        periodTotals = exploreTotals
                    )
                }

                // KPI accordion — only meaningful for sources that record PV + load.
                // ESBN / Octopus are import/export only, so the self-* KPIs would be nonsense.
                if (dsInfo.importerType != ComparisonUIViewModel.Importer.ESBNHDF &&
                    dsInfo.importerType != ComparisonUIViewModel.Importer.OCTOPUS) {
                    KpiAccordion(
                        period = kpiPeriod,
                        anchor = kpiAnchor,
                        monthFilter = kpiMonthFilter,
                        summary = kpiSummary,
                        months = kpiMonths,
                        bounds = dataBounds,
                        onPeriodChange = { p, a -> viewModel.setKpiPeriod(p, a) },
                        onNavigate = { fwd -> viewModel.navigateKpi(fwd) },
                        onMonthFilterChange = { viewModel.setKpiMonthFilter(it) },
                        df = df,
                        kwhDf = kwhDf,
                        showHints = showHints
                    )
                }

                ExpandableCard(
                    title = stringResource(R.string.ui2_dash_usage_data),
                    leadingIcon = {
                        Icon(painterResource(R.drawable.house), null, Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface)
                    },
                    trailingContent = { _ ->
                        if (usageTotals != null)
                            Icon(painterResource(R.drawable.tick), null, Modifier.size(18.dp), tint = Color.Unspecified)
                    },
                    showEdit = false
                ) {
                    PeriodSelector(
                        selectedPeriod = usagePeriod,
                        anchorDate     = usageAnchor,
                        dataStart      = dsInfo.startDate,
                        dataEnd        = dsInfo.endDate,
                        onPeriodChange = { p, a, adv -> viewModel.setUsagePeriod(p, a, adv) },
                        onNavigate     = { fwd, adv -> viewModel.navigateUsage(fwd, adv) }
                    )
                    Spacer(Modifier.height(8.dp))
                    val isEsbn = dsInfo.importerType == ComparisonUIViewModel.Importer.ESBNHDF ||
                        dsInfo.importerType == ComparisonUIViewModel.Importer.OCTOPUS
                    val totals = usageTotals
                    when {
                        totals == null -> CircularProgressIndicator(
                            modifier = Modifier.padding(4.dp).size(20.dp), strokeWidth = 2.dp)
                        else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (!isEsbn && totals.load > 0)
                                PeriodTotalRow(stringResource(R.string.ui2_graphs_load), totals.load, kwhDf)
                            if (totals.buy  > 0)
                                PeriodTotalRow(stringResource(R.string.ui2_dash_import), totals.buy, kwhDf)
                            if (totals.feed > 0)
                                PeriodTotalRow(stringResource(R.string.ui2_graphs_export), totals.feed, kwhDf)
                            if (!isEsbn && totals.pv   > 0)
                                PeriodTotalRow(stringResource(R.string.ui2_graphs_solar), totals.pv, kwhDf)
                            if (!isEsbn && totals.charging    > 0)
                                PeriodTotalRow(stringResource(R.string.ui2_dash_charging), totals.charging, kwhDf)
                            if (!isEsbn && totals.discharging > 0)
                                PeriodTotalRow(stringResource(R.string.ui2_dash_discharging), totals.discharging, kwhDf)
                        }
                    }
                    val dist = usageDistribution
                    if (dist != null) {
                        Spacer(Modifier.height(8.dp))
                        DataSourceDistributionCharts(distribution = dist)
                    }
                }

                if (dsInfo.importerType != ComparisonUIViewModel.Importer.ESBNHDF &&
                    dsInfo.importerType != ComparisonUIViewModel.Importer.OCTOPUS) {
                    ExpandableCard(
                        title = stringResource(R.string.ui2_dash_pv_system),
                        leadingIcon = {
                            Icon(painterResource(R.drawable.solarpanel), null,
                                Modifier.size(24.dp), tint = Color.Unspecified)
                        },
                        trailingContent = { _ ->
                            if (pvChartData?.isNotEmpty() == true)
                                Icon(painterResource(R.drawable.ic_baseline_wb_sunny_36), null,
                                    Modifier.size(18.dp), tint = StatusGreen)
                        },
                        showEdit = false
                    ) {
                        PeriodSelector(
                            selectedPeriod = pvPeriod,
                            anchorDate     = pvAnchor,
                            dataStart      = dsInfo.startDate,
                            dataEnd        = dsInfo.endDate,
                            onPeriodChange = { p, a, adv -> viewModel.setPvPeriod(p, a, adv) },
                            onNavigate     = { fwd, adv -> viewModel.navigatePv(fwd, adv) }
                        )
                        Spacer(Modifier.height(8.dp))
                        when {
                            pvChartData == null ->
                                CircularProgressIndicator(modifier = Modifier.padding(4.dp).size(20.dp), strokeWidth = 2.dp)
                            pvChartData!!.isEmpty() ->
                                Text(stringResource(R.string.ui2_dash_no_solar_period),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else -> DataSourcePVBarChart(pvData = pvChartData!!)
                        }
                    }
                }

            } else {
                // ── Simulation mode ───────────────────────────────────────────
                dashboardData?.bestCosting?.let { costing ->
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.ui2_dash_best_cost,
                                    RegionProfiles.current.currencySymbol,
                                    df.format(costing.net / 100.0)),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(text = costing.fullPlanName ?: "", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                dashboardData?.scenarioComponents?.let { sc ->
                    val costing = dashboardData?.bestCosting
                    val kpis = dashboardData?.simKPIs
                    val ctx = LocalContext.current
                    val scenarioId = sc.scenario?.scenarioIndex ?: -1L

                    // 0. Needs attention — only rendered while the simulation is
                    //    incomplete. Collects the reasons the dashboard looks
                    //    half-empty (no usage data / PV data not generated /
                    //    results still calculating) into one place with fixes.
                    val readiness = simReadinessIssues(
                        hasLoadProfile = sc.loadProfile != null,
                        hasPanels      = sc.panels.orEmpty().isNotEmpty(),
                        hasPanelData   = dashboardData?.hasPanelData == true,
                        hpWeatherMissing = dashboardData?.hpWeatherMissing == true,
                        resultsReady   = kpis != null && costing != null
                    )
                    SimulationStatusAccordion(readiness) { section ->
                        if (scenarioId != -1L) {
                            ctx.startActivity(
                                Intent(ctx, UI2WizardActivity::class.java)
                                    .putExtra("ScenarioID", scenarioId)
                                    .putExtra("WizardSection", section)
                            )
                        }
                    }

                    // 1. Tariff Plan — moved to the top per the redesign, with a
                    //    period picker so the user can dial in monthly / yearly
                    //    costings instead of just the simulation's annual total.
                    ExpandableCard(
                        title = stringResource(R.string.ui2_dash_tariff_plan),
                        leadingIcon = {
                            Icon(painterResource(R.drawable.ic_baseline_euro_symbol_24), null,
                                Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
                        },
                        trailingContent = { _ ->
                            if (costing != null) {
                                Icon(painterResource(R.drawable.tick), null, Modifier.size(18.dp), tint = Color.Unspecified)
                            }
                        },
                        onEdit = {
                            ctx.startActivity(Intent(ctx, UI2PricePlanListActivity::class.java))
                        }
                    ) {
                        // Use the simulation's actual date range (typically year
                        // 2001) so the picker doesn't extend out to 1976 just
                        // because today's date is the cosmetic centre.
                        val bnd = dataBounds
                        val cosStart = (bnd?.first ?: scenarioTariffAnchor).toString()
                        val cosEnd   = (bnd?.second ?: scenarioTariffAnchor).toString()
                        PeriodSelector(
                            selectedPeriod = scenarioTariffPeriod,
                            anchorDate     = scenarioTariffAnchor,
                            dataStart      = cosStart,
                            dataEnd        = cosEnd,
                            advanced       = false,
                            onPeriodChange = { p, a, _ -> viewModel.setScenarioTariffPeriod(p, a) },
                            onNavigate     = { fwd, _ -> viewModel.navigateScenarioTariff(fwd) }
                        )
                        Spacer(Modifier.height(8.dp))
                        val periodRows = scenarioTariffCostings
                        val allCostings = dashboardData?.allCostings ?: emptyList()
                        if (scenarioTariffPeriod == DataSourcePeriod.ALL && periodRows == null
                            && allCostings.isNotEmpty()) {
                            // First render: fall back to the precomputed annual costing.
                            AllCostingsTable(
                                costings = allCostings,
                                planStandingCharges = dashboardData?.planStandingCharges ?: emptyMap(),
                                simDays = dashboardData?.simDays ?: 365L,
                                df = df,
                                favouritePlanId = favouritePlanId,
                                planActive = dashboardData?.planActive ?: emptyMap()
                            )
                        } else if (periodRows == null) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else if (periodRows.isEmpty()) {
                            Text(stringResource(R.string.ui2_dash_no_results_yet))
                        } else {
                            DataSourceCostingsTable(
                                costings = periodRows,
                                df       = df,
                                favouritePlanId = favouritePlanId
                            )
                        }
                    }

                    // 2. Explore data
                    ExpandableCard(
                        title = stringResource(R.string.ui2_dash_explore),
                        leadingIcon = {
                            Icon(painterResource(R.drawable.piechart_25), null, Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurface)
                        },
                        trailingContent = { expanded ->
                            val isError = sc.panels.isNotEmpty() && dashboardData?.hasPanelData != true
                            when {
                                isError -> Icon(painterResource(R.drawable.ic_baseline_warning_24), null,
                                    Modifier.size(18.dp), tint = StatusRed)
                                kpis != null -> Icon(painterResource(R.drawable.tick), null,
                                    Modifier.size(18.dp), tint = Color.Unspecified)
                                else -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            }
                            if (expanded && kpis != null) {
                                Spacer(Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .clickable { onLaunchGraphs() }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(painterResource(R.drawable.barchart),
                                        stringResource(R.string.ui2_dash_view_graphs),
                                        Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        },
                        showEdit = false
                    ) {
                        val pvMissing = sc.panels.orEmpty().isNotEmpty() &&
                            dashboardData?.hasPanelData != true
                        when {
                            kpis == null -> Text(
                                stringResource(R.string.ui2_dash_results_pending),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            else -> {
                                if (pvMissing) {
                                    Text(
                                        stringResource(R.string.ui2_dash_solar_generating),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StatusAmber,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                SimulationPieCharts(kpis = kpis)
                            }
                        }
                        // MIC (Maximum Import Capacity) is an IE grid concept —
                        // other editions never show the breach flag.
                        if (RegionProfiles.current.hasMIC) {
                            dashboardData?.micBreaches?.let { micb ->
                                Spacer(Modifier.height(8.dp))
                                MICBreachFlag(micb, showHints)
                            }
                        }
                    }

                    // 3. KPI — same style/content as ImportKeyStatsFragment.
                    KpiAccordion(
                        period = kpiPeriod,
                        anchor = kpiAnchor,
                        monthFilter = kpiMonthFilter,
                        summary = kpiSummary,
                        months = kpiMonths,
                        bounds = dataBounds,
                        onPeriodChange = { p, a -> viewModel.setKpiPeriod(p, a) },
                        onNavigate = { fwd -> viewModel.navigateKpi(fwd) },
                        onMonthFilterChange = { viewModel.setKpiMonthFilter(it) },
                        df = df,
                        kwhDf = kwhDf,
                        showHints = showHints
                    )

                    // 4. Visual overview
                    ExpandableCard(
                        title = stringResource(R.string.ui2_dash_visual_overview),
                        leadingIcon = {
                            Icon(Icons.Default.Visibility, null, Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurface)
                        },
                        showEdit = false
                    ) {
                        TopologyDiagram(sc = sc)
                        Spacer(Modifier.height(6.dp))
                        TopologyLegend()
                    }

                    // 5. Usage Data
                    ExpandableCard(
                        title = stringResource(R.string.ui2_dash_usage_data),
                        leadingIcon = {
                            Icon(painterResource(R.drawable.house), null, Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurface)
                        },
                        trailingContent = { _ ->
                            if (sc.loadProfile != null) {
                                Icon(painterResource(R.drawable.tick), null, Modifier.size(18.dp), tint = Color.Unspecified)
                            }
                        },
                        onEdit = if (scenarioId != -1L) ({
                            ctx.startActivity(
                                Intent(ctx, UI2WizardActivity::class.java)
                                    .putExtra("ScenarioID", scenarioId)
                                    .putExtra("WizardSection", "load")
                            )
                        }) else null
                    ) {
                        val lp = sc.loadProfile
                        if (lp != null) {
                            Text(stringResource(R.string.ui2_dash_source, lp.distributionSource))
                            Text(stringResource(R.string.ui2_dash_annual_usage,
                                "%.0f".format(lp.annualUsage)))
                            Spacer(Modifier.height(8.dp))
                            LoadDistributionCharts(lp = lp)
                        } else {
                            Text(stringResource(R.string.ui2_dash_issue_no_usage_title))
                        }
                    }

                    // 6. Inverter
                    if (uiVis.inverter) ExpandableCard(
                        title = stringResource(R.string.ui2_component_inverter),
                        leadingIcon = {
                            Icon(painterResource(R.drawable.inverter), null, Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurface)
                        },
                        trailingContent = { _ ->
                            if (sc.inverters.isNotEmpty()) {
                                Icon(painterResource(R.drawable.tick), null, Modifier.size(18.dp), tint = Color.Unspecified)
                            }
                        },
                        onEdit = if (scenarioId != -1L) ({
                            ctx.startActivity(
                                Intent(ctx, UI2WizardActivity::class.java)
                                    .putExtra("ScenarioID", scenarioId)
                                    .putExtra("WizardSection", "inverters")
                            )
                        }) else null
                    ) {
                        if (sc.inverters.isEmpty()) Text(stringResource(R.string.ui2_dash_no_inverters))
                        else sc.inverters.forEach { inv ->
                            Text(stringResource(R.string.ui2_dash_inverter_row,
                                inv.inverterName, inv.maxInverterLoad.toString(), inv.mpptCount))
                        }
                    }

                    // 7. PV System
                    if (uiVis.panels) ExpandableCard(
                        title = stringResource(R.string.ui2_dash_pv_system),
                        leadingIcon = {
                            Icon(painterResource(R.drawable.solarpanel), null, Modifier.size(24.dp), tint = Color.Unspecified)
                        },
                        trailingContent = { _ ->
                            val panelsConfigured = sc.panels.orEmpty().isNotEmpty()
                            val pvDataReady = dashboardData?.hasPanelData == true
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (panelsConfigured) {
                                    if (pvDataReady) {
                                        // Sun = solar yield generated and ready.
                                        Icon(painterResource(R.drawable.ic_baseline_wb_sunny_36),
                                            stringResource(R.string.ui2_dash_solar_ready),
                                            Modifier.size(18.dp), tint = StatusGreen)
                                    } else {
                                        // Cloud = panels exist but their solar data
                                        // hasn't been generated yet — a clearer "no sun
                                        // (yet)" signal than simply omitting the sun.
                                        Icon(painterResource(R.drawable.cloud),
                                            stringResource(R.string.ui2_dash_issue_solar_title),
                                            Modifier.size(18.dp),
                                            tint = StatusAmber)
                                    }
                                    Icon(painterResource(R.drawable.tick), null, Modifier.size(18.dp), tint = Color.Unspecified)
                                }
                            }
                        },
                        onEdit = if (scenarioId != -1L) ({
                            ctx.startActivity(
                                Intent(ctx, UI2WizardActivity::class.java)
                                    .putExtra("ScenarioID", scenarioId)
                                    .putExtra("WizardSection", "pv")
                            )
                        }) else null
                    ) {
                        if (sc.panels.isEmpty()) Text(stringResource(R.string.ui2_dash_no_panels))
                        else {
                            if (dashboardData?.hasPanelData != true) {
                                Text(stringResource(R.string.ui2_dash_solar_not_generated),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusAmber,
                                    modifier = Modifier.padding(bottom = 6.dp))
                            }
                            sc.panels.forEach { p ->
                                Text(stringResource(R.string.ui2_dash_panel_row,
                                    p.panelName, p.panelCount.toString(), p.panelkWp.toString(),
                                    p.azimuth.toString(), p.slope.toString()))
                            }
                        }
                        if (panelSummary.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            PVSummaryBarChart(panelSummary = panelSummary, panels = sc.panels)
                        }
                    }

                    // 8. Battery
                    if (uiVis.battery) ExpandableCard(
                        title = stringResource(R.string.ui2_component_battery),
                        leadingIcon = {
                            Icon(painterResource(R.drawable.battery1), null, Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurface)
                        },
                        trailingContent = { _ ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (sc.batteries.isNotEmpty()) {
                                    Icon(painterResource(R.drawable.ic_baseline_settings_24), null,
                                        Modifier.size(18.dp), tint = StatusGreen)
                                }
                                if (sc.discharges.isNotEmpty()) {
                                    Icon(painterResource(R.drawable.baseline_file_upload_24), null,
                                        Modifier.size(18.dp), tint = StatusGreen)
                                }
                                if (sc.loadShifts.isNotEmpty()) {
                                    Icon(painterResource(R.drawable.ic_baseline_access_time_24), null,
                                        Modifier.size(18.dp), tint = StatusGreen)
                                }
                            }
                        },
                        onEdit = if (scenarioId != -1L) ({
                            ctx.startActivity(
                                Intent(ctx, UI2WizardActivity::class.java)
                                    .putExtra("ScenarioID", scenarioId)
                                    .putExtra("WizardSection", "battery")
                            )
                        }) else null
                    ) {
                        if (sc.batteries.isEmpty()) Text(stringResource(R.string.ui2_dash_no_batteries))
                        else sc.batteries.forEach { b ->
                            Text(stringResource(R.string.ui2_dash_battery_row,
                                b.batterySize.toString(), b.dischargeStop.toString(),
                                b.maxCharge.toString(), b.maxDischarge.toString()))
                        }
                    }

                    // 9. Hot Water
                    if (uiVis.hotWater) ExpandableCard(
                        title = stringResource(R.string.ui2_graphs_hot_water),
                        leadingIcon = {
                            val res = if (sc.hwSystem != null) R.drawable.waterwarm else R.drawable.watercold
                            Icon(painterResource(res), null, Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurface)
                        },
                        trailingContent = { _ ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (sc.hwSystem != null) {
                                    Icon(painterResource(R.drawable.ic_baseline_settings_24), null,
                                        Modifier.size(18.dp), tint = StatusGreen)
                                }
                                if (sc.hwDivert != null) {
                                    Icon(painterResource(R.drawable.ic_baseline_call_split_24), null,
                                        Modifier.size(18.dp), tint = StatusGreen)
                                }
                                if (sc.hwSchedules.isNotEmpty()) {
                                    Icon(painterResource(R.drawable.ic_baseline_access_time_24), null,
                                        Modifier.size(18.dp), tint = StatusGreen)
                                }
                            }
                        },
                        onEdit = if (scenarioId != -1L) ({
                            ctx.startActivity(
                                Intent(ctx, UI2WizardActivity::class.java)
                                    .putExtra("ScenarioID", scenarioId)
                                    .putExtra("WizardSection", "hotwater")
                            )
                        }) else null
                    ) {
                        val hw = sc.hwSystem
                        if (hw == null) Text(stringResource(R.string.ui2_dash_no_hw))
                        else {
                            Text(stringResource(R.string.ui2_dash_hw_tank,
                                hw.hwCapacity.toString(), hw.hwUsage.toString()))
                            Text(stringResource(R.string.ui2_dash_hw_target,
                                hw.hwTarget.toString(), hw.hwRate.toString()))
                        }
                    }

                    // 10. EV
                    if (uiVis.ev) ExpandableCard(
                        title = stringResource(R.string.ui2_component_ev),
                        leadingIcon = {
                            val res = if (sc.evCharges.isNotEmpty() || sc.evDiverts.isNotEmpty())
                                R.drawable.ev_on else R.drawable.ev_off
                            Icon(painterResource(res), null, Modifier.size(24.dp), tint = Color.Unspecified)
                        },
                        trailingContent = { _ ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (sc.evCharges.isNotEmpty()) {
                                    Icon(painterResource(R.drawable.ic_baseline_access_time_24), null,
                                        Modifier.size(18.dp), tint = StatusGreen)
                                }
                                if (sc.evDiverts.isNotEmpty()) {
                                    Icon(painterResource(R.drawable.ic_baseline_call_split_24), null,
                                        Modifier.size(18.dp), tint = StatusGreen)
                                }
                            }
                        },
                        onEdit = if (scenarioId != -1L) ({
                            ctx.startActivity(
                                Intent(ctx, UI2WizardActivity::class.java)
                                    .putExtra("ScenarioID", scenarioId)
                                    .putExtra("WizardSection", "ev")
                            )
                        }) else null
                    ) {
                        if (sc.evCharges.isEmpty() && sc.evDiverts.isEmpty()) {
                            Text(stringResource(R.string.ui2_dash_no_ev))
                        } else {
                            if (sc.evCharges.isNotEmpty()) {
                                Text(stringResource(R.string.ui2_dash_schedules),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline)
                                sc.evCharges.forEach { ev ->
                                    Text(stringResource(R.string.ui2_dash_ev_row,
                                        ev.name, ev.begin.toString(), ev.end.toString(),
                                        ev.draw.toString()))
                                }
                            }
                            if (sc.evDiverts.isNotEmpty()) {
                                if (sc.evCharges.isNotEmpty()) Spacer(Modifier.height(6.dp))
                                Text(stringResource(R.string.ui2_dash_solar_diverts),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline)
                                sc.evDiverts.forEach { d ->
                                    val row = stringResource(R.string.ui2_dash_divert_row,
                                        d.name, d.begin.toString(), d.end.toString(),
                                        d.dailyMax.toString())
                                    Text(if (!d.isActive)
                                        "$row  ·  ${stringResource(R.string.ui2_dash_off_suffix)}"
                                    else row)
                                }
                            }
                        }
                    }

                    // 11. Heat Pump (mirrors the wizard/dashboard order — after EV)
                    val heatPumps = sc.heatPumps.orEmpty()
                    if (uiVis.heatPump) ExpandableCard(
                        title = stringResource(R.string.ui2_graphs_heat_pump),
                        leadingIcon = {
                            Text(if (heatPumps.isNotEmpty()) "♨️" else "❄️",
                                style = MaterialTheme.typography.titleLarge)
                        },
                        trailingContent = { _ ->
                            if (heatPumps.isNotEmpty()) {
                                Icon(painterResource(R.drawable.tick), null,
                                    Modifier.size(18.dp), tint = Color.Unspecified)
                            }
                        },
                        onEdit = if (scenarioId != -1L) ({
                            ctx.startActivity(
                                Intent(ctx, UI2WizardActivity::class.java)
                                    .putExtra("ScenarioID", scenarioId)
                                    .putExtra("WizardSection", "heatpump")
                            )
                        }) else null
                    ) {
                        if (heatPumps.isEmpty()) Text(stringResource(R.string.ui2_dash_no_hp))
                        else heatPumps.forEach { hp ->
                            if (hp.fuelType == "None") {
                                Text(stringResource(R.string.ui2_dash_hp_newbuild,
                                    hp.floorAreaM2.toInt().toString(), hp.heatLossIndex.toString()))
                            } else {
                                val unit = if (hp.fuelType == "Natural gas") "kWh" else "L"
                                Text(stringResource(R.string.ui2_dash_hp_fuel,
                                    hp.fuelType, hp.fuelAnnual.toInt().toString(), unit))
                            }
                            Text(stringResource(R.string.ui2_dash_hp_cop,
                                hp.copRated.toString(), hp.scop.toString(), hp.capacityKw.toString()))
                            Text(stringResource(R.string.ui2_dash_hp_weather,
                                    if (hp.weatherSource == "cds") "CDS"
                                    else stringResource(R.string.ui2_dash_weather_2001)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    // 8. (Tariff Plan moved to the top of the scenario list — pricing is
                    //     the headline answer once a scenario has been simulated.)
                }
            }
            }   // end else of !hasActiveItem
            Spacer(Modifier.height(80.dp))
        }   // end Column

            // Scrim
            AnimatedVisibility(visible = showDrawer, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
                Box(modifier = Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { showDrawer = false })
            }

            // Right-side drawer (the global app menu)
            AnimatedVisibility(
                visible = showDrawer,
                enter = slideInHorizontally(tween(220)) { it },
                exit = slideOutHorizontally(tween(220)) { it },
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(280.dp)
            ) {
                Surface(tonalElevation = 8.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxSize()) {
                    UI2DrawerContent(
                        showHints = showHints,
                        onShowHintsChange = { if (it != showHints) toggleShowHints() },
                        onSwitchLegacy = { showDrawer = false; onSwitchLegacy() },
                        onClose = { showDrawer = false }
                    )
                }
            }
        }   // end Box
    }
}

// ─── Bar chart helpers ──────────────────────────────────────────────────────

private fun styleDistBarChart(chart: BarChart, labelColor: Int, gridColor: Int) {
    chart.description.isEnabled = false
    chart.setDrawGridBackground(false)
    chart.setDrawBarShadow(false)
    chart.setFitBars(true)
    chart.setNoDataTextColor(labelColor)
    chart.setTouchEnabled(false)
    chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
    chart.xAxis.granularity = 1f
    chart.xAxis.setDrawGridLines(false)
    chart.xAxis.textColor = labelColor
    chart.xAxis.textSize = 9f
    chart.axisLeft.isEnabled = true
    chart.axisLeft.setLabelCount(2, true)   // shows actual min + max of the data range
    chart.axisLeft.setDrawGridLines(false)
    chart.axisLeft.textColor = labelColor
    chart.axisLeft.textSize = 8f
    chart.axisLeft.resetAxisMinimum()       // auto-scale to data, not forced to zero
    chart.axisLeft.valueFormatter = object : ValueFormatter() {
        override fun getFormattedValue(value: Float) = "%.1f%%".format(value)
    }
    chart.axisRight.isEnabled = false
    chart.legend.isEnabled = false
    chart.setScaleEnabled(false)
}

private fun stylePVBarChart(chart: BarChart, labelColor: Int, gridColor: Int) {
    chart.description.isEnabled = false
    chart.setDrawGridBackground(false)
    chart.setDrawBarShadow(false)
    chart.setFitBars(true)
    chart.setNoDataTextColor(labelColor)
    chart.setTouchEnabled(false)
    chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
    chart.xAxis.granularity = 1f
    chart.xAxis.setDrawGridLines(false)
    chart.xAxis.textColor = labelColor
    chart.xAxis.textSize = 9f
    chart.axisLeft.textColor = labelColor
    chart.axisLeft.gridColor = gridColor
    chart.axisLeft.axisMinimum = 0f
    chart.axisRight.isEnabled = false
    chart.legend.isEnabled = false
    chart.setScaleEnabled(false)
}

// ─── Load distribution charts ──────────────────────────────────────────────

@Composable
fun LoadDistributionCharts(lp: LoadProfile) {
    var zoomedIdx by remember { mutableIntStateOf(-1) }

    val hourlyDist  = remember(lp) { lp.hourlyDist?.dist ?: emptyList<Double>() }
    val dailyDist   = remember(lp) { lp.dowDist?.dowDist ?: emptyList<Double>() }
    val monthlyDist = remember(lp) { lp.monthlyDist?.monthlyDist ?: emptyList<Double>() }

    val hourLabels  = remember { (0..23).map { "%02d".format(it) } }
    val dayLabels   = stringArrayResource(R.array.ui2_days_short_sun_first).toList()
    val monthLabels = stringArrayResource(R.array.ui2_months_short).toList()

    val hourlyTitle  = stringResource(R.string.ui2_dash_hourly_pct)
    val dailyTitle   = stringResource(R.string.ui2_dash_daily_pct)
    val monthlyTitle = stringResource(R.string.ui2_dash_monthly_pct)
    val charts = remember(lp, hourlyTitle) {
        listOf(
            Triple(hourlyTitle,  hourlyDist,  hourLabels),
            Triple(dailyTitle,   dailyDist,   dayLabels),
            Triple(monthlyTitle, monthlyDist, monthLabels)
        )
    }

    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        charts.forEachIndexed { idx, (title, dist, labels) ->
            if (dist.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().clickable { zoomedIdx = idx }) {
                    Text("$title  ↗", style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 2.dp))
                    SimpleDistBarChart(dist = dist, labels = labels,
                        modifier = Modifier.fillMaxWidth().height(80.dp))
                }
            }
        }
    }

    if (zoomedIdx >= 0) {
        val (title, dist, labels) = charts[zoomedIdx]
        val size = with(density) { (minOf(containerSize.width, containerSize.height) * 1.0f).toDp() }
        Dialog(onDismissRequest = { zoomedIdx = -1 },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.size(size), shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                Column(
                    modifier = Modifier.padding(12.dp).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    SimpleDistBarChart(dist = dist, labels = labels,
                        modifier = Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SimpleDistBarChart(
    dist: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    val labelColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColorArgb  = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f).toArgb()
    val barColorArgb   = MaterialTheme.colorScheme.primary.toArgb()

    AndroidView(
        factory = { ctx ->
            BarChart(ctx).apply { styleDistBarChart(this, labelColorArgb, gridColorArgb) }
        },
        update = { chart ->
            styleDistBarChart(chart, labelColorArgb, gridColorArgb)
            val entries = dist.mapIndexed { i, v -> BarEntry(i.toFloat(), v.toFloat()) }
            val ds = BarDataSet(entries, "").apply {
                color = barColorArgb
                setDrawValues(false)
            }
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) =
                    labels.getOrElse(value.toInt()) { "" }
            }
            chart.data = BarData(ds)
            chart.invalidate()
        },
        modifier = modifier
    )
}

// ─── PV string monthly bar charts (one per panel string) ──────────────────

@Composable
fun PVSummaryBarChart(panelSummary: List<PanelPVSummary>, panels: List<Panel>) {
    var zoomedIdx by remember { mutableIntStateOf(-1) }
    val monthLabels = stringArrayResource(R.array.ui2_months_short).toList()

    val grouped  = remember(panelSummary) { panelSummary.groupBy { it.panelID } }
    // Only show strings that belong to this scenario's panel configurations
    val scenarioPanelIds = remember(panels) { panels.map { it.panelIndex }.toSet() }
    val panelIds = remember(grouped, scenarioPanelIds) { grouped.keys.filter { it in scenarioPanelIds }.sorted() }

    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current

    Text(stringResource(R.string.ui2_dash_pv_monthly), style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(bottom = 4.dp))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        panelIds.forEachIndexed { idx, panelId ->
            val name     = panels.firstOrNull { it.panelIndex == panelId }?.panelName
                ?: stringResource(R.string.ui2_dash_panel_n, panelId)
            val monthMap = grouped[panelId]?.associate { (it.month.toIntOrNull() ?: 1) to it.tot } ?: emptyMap()
            val dist     = (1..12).map { m -> monthMap[m] ?: 0.0 }
            Column(modifier = Modifier.fillMaxWidth().clickable { zoomedIdx = idx }) {
                Text("$name  ↗", style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 2.dp))
                PVStringBarChart(dist = dist, labels = monthLabels,
                    modifier = Modifier.fillMaxWidth().height(100.dp))
            }
        }
    }

    if (zoomedIdx >= 0) {
        val panelId  = panelIds[zoomedIdx]
        val name     = panels.firstOrNull { it.panelIndex == panelId }?.panelName
            ?: stringResource(R.string.ui2_dash_panel_n, panelId)
        val monthMap = grouped[panelId]?.associate { (it.month.toIntOrNull() ?: 1) to it.tot } ?: emptyMap()
        val dist     = (1..12).map { m -> monthMap[m] ?: 0.0 }
        val size     = with(density) { (minOf(containerSize.width, containerSize.height) * 1.0f).toDp() }
        Dialog(onDismissRequest = { zoomedIdx = -1 },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.size(size), shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                Column(
                    modifier = Modifier.padding(12.dp).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    PVStringBarChart(dist = dist, labels = monthLabels,
                        modifier = Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PVStringBarChart(
    dist: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    val labelColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColorArgb  = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f).toArgb()
    val barColorArgb   = "#F44336".toColorInt()

    AndroidView(
        factory = { ctx ->
            BarChart(ctx).apply { stylePVBarChart(this, labelColorArgb, gridColorArgb) }
        },
        update = { chart ->
            stylePVBarChart(chart, labelColorArgb, gridColorArgb)
            val entries = dist.mapIndexed { i, v -> BarEntry((i + 1).toFloat(), v.toFloat()) }
            val ds = BarDataSet(entries, "").apply {
                color = barColorArgb
                setDrawValues(false)
            }
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) =
                    labels.getOrElse(value.toInt() - 1) { "" }
            }
            chart.xAxis.labelCount = 12
            chart.data = BarData(ds)
            chart.invalidate()
        },
        modifier = modifier
    )
}

// ─── All-plans costing table ────────────────────────────────────────────────

private val TARIFF_PIE_COLORS = listOf(
    Color(0xFF304567), Color(0xFF2ECC71), Color(0xFFE74C3C), Color(0xFF9B59B6),
    Color(0xFF3498DB), Color(0xFFF39C12), Color(0xFF1ABC9C), Color(0xFF34495E),
    Color(0xFFE67E22), Color(0xFF27AE60)
)

/**
 * Costings table + tariff-band breakdown pie, shared by [AllCostingsTable] and
 * [DataSourceCostingsTable] (which differ only in row type and accessors).
 *
 * COMPACT: table is full width; tapping a row opens the band pie in a dialog
 * (unchanged behaviour). WIDE+: the table sits on the left and a permanent band
 * pie shows on the right, defaulting to the top (best) row and updating when
 * another row is tapped — the dialog stays as the COMPACT fallback.
 */
@Composable
private fun <R> CostingsTableWithBandPie(
    rows: List<R>,
    rowId: (R) -> Any,
    pinnedCell: @Composable (R) -> Unit,
    columns: List<PinnedScrollColumn<R>>,
    rowBackground: @Composable (R, Int) -> Color,
    planName: (R) -> String,
    bandSlices: (R) -> List<PieSlice>,
) {
    // Track selection by stable id, not row instance — the costings list is
    // re-created on each recomposition and Costings has no structural equality,
    // so keying on the instance would reset the selection every frame.
    var zoomedId by remember { mutableStateOf<Any?>(null) }
    var selectedId by remember { mutableStateOf<Any?>(null) }
    val selected = rows.firstOrNull { rowId(it) == selectedId } ?: rows.firstOrNull()
    val zoomed = rows.firstOrNull { rowId(it) == zoomedId }
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val wide = maxWidth >= AdaptiveLayout.WIDTH_WIDE_AT
        // Highlight the row whose tariff-band pie is currently on screen. WIDE:
        // the side pie is always visible and defaults to the top row, so
        // highlight `selected`. COMPACT: the pie is a dialog opened on tap, so
        // only highlight once a row has been tapped — we set selectedId on that
        // tap too, keeping the row highlighted while the dialog is up and after
        // it's dismissed.
        val highlightId: Any? = if (wide) selected?.let { rowId(it) } else selectedId
        val highlightColor = MaterialTheme.colorScheme.secondaryContainer
        val effectiveRowBackground: @Composable (R, Int) -> Color = { r, idx ->
            if (highlightId != null && rowId(r) == highlightId) highlightColor
            else rowBackground(r, idx)
        }
        val table: @Composable () -> Unit = {
            PinnedScrollTable(
                rows = rows,
                pinnedHeader = stringResource(R.string.ui2_cmp_plan),
                pinnedWeight = 2f,
                pinnedCell = pinnedCell,
                columns = columns,
                rowBackground = effectiveRowBackground,
                onRowClick = {
                    selectedId = rowId(it)
                    if (!wide) zoomedId = rowId(it)
                },
                footer = {
                    Text(
                        stringResource(if (wide) R.string.ui2_dash_tap_row_wide
                                       else R.string.ui2_dash_tap_row_compact),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.weight(1.6f)) { table() }
                Box(Modifier.weight(1f)) {
                    val r = selected
                    if (r != null) {
                        val slices = bandSlices(r)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(planName(r), style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            if (slices.isNotEmpty()) {
                                PieChart(slices = slices,
                                    modifier = Modifier.size(160.dp), isDonut = true)
                                Spacer(Modifier.height(8.dp))
                                PieLegend(slices = slices)
                            } else {
                                Text(stringResource(R.string.ui2_dash_no_bands),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else {
            table()
        }
    }

    val z = zoomed
    if (z != null) {
        val slices = bandSlices(z)
        val size = with(density) { (minOf(containerSize.width, containerSize.height) * 0.9f).toDp() }
        Dialog(onDismissRequest = { zoomedId = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.size(size), shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(planName(z), style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    PieChart(slices = slices, modifier = Modifier.size(size * 0.55f), isDonut = true)
                    Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        PieLegend(slices = slices)
                    }
                }
            }
        }
    }
}

@Composable
private fun AllCostingsTable(
    costings: List<Costings>,
    planStandingCharges: Map<Long, Double>,
    simDays: Long,
    df: DecimalFormat,
    favouritePlanId: Long? = null,
    planActive: Map<Long, Boolean> = emptyMap()
) {
    // Only active plans appear in the dashboard's Tariff Plan table — match
    // DataSourceCostingsTable. Plans missing from the map (pre-existing
    // costings rows for since-deleted plans) default to active so they don't
    // silently vanish.
    val visible = costings.filter { planActive[it.pricePlanID] ?: true }

    if (visible.isEmpty() && costings.isNotEmpty()) {
        Text(
            stringResource(R.string.ui2_dash_no_active_plans),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val firstId = visible.firstOrNull()?.pricePlanID
    val columns = listOf(
        PinnedScrollColumn<Costings>(
            header = stringResource(R.string.ui2_dash_net),
            accent = { it.pricePlanID == firstId },
            cell = { c ->
                Text(df.format(c.net / 100.0),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                    color = if (c.pricePlanID == firstId)
                        MaterialTheme.colorScheme.primary else Color.Unspecified)
            }
        ),
        PinnedScrollColumn<Costings>(header = stringResource(R.string.ui2_dash_buy), cell = { c ->
            Text(df.format(c.buy / 100.0),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }),
        PinnedScrollColumn<Costings>(header = stringResource(R.string.ui2_dash_sell), cell = { c ->
            Text(df.format(c.sell / 100.0),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }),
        PinnedScrollColumn<Costings>(header = stringResource(R.string.ui2_cmp_fixed), cell = { c ->
            val fixed = (planStandingCharges[c.pricePlanID] ?: 0.0) * (simDays / 365.0)
            Text(df.format(fixed),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        })
    )

    CostingsTableWithBandPie(
        rows = visible,
        rowId = { it.pricePlanID },
        pinnedCell = { c ->
            val isFav = favouritePlanId != null && favouritePlanId == c.pricePlanID
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isFav) {
                    Icon(Icons.Default.Star,
                        contentDescription = stringResource(R.string.ui2_dash_current_plan_cd),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                }
                Text(c.fullPlanName ?: "",
                    style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        },
        columns = columns,
        rowBackground = { c, idx ->
            val isFav = favouritePlanId != null && favouritePlanId == c.pricePlanID
            when {
                isFav -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                idx == 0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> Color.Transparent
            }
        },
        planName = { it.fullPlanName ?: "" },
        bandSlices = { c ->
            val st = c.subTotals
            if (st == null) emptyList()
            else st.prices.sortedBy { it }.mapIndexed { i, price ->
                val kwh = st.getSubTotalForPrice(price) ?: 0.0
                // Region-aware minor unit ("c" / "p"), not a hardcoded cent.
                PieSlice("%.1f".format(price) + RegionProfiles.current.minorSymbol,
                    kwh, TARIFF_PIE_COLORS[i % TARIFF_PIE_COLORS.size])
            }.filter { it.value > 0 }
        }
    )
}

// ─── Data source PV bar chart (period-aware) ──────────────────────────────

@Composable
private fun DataSourcePVBarChart(pvData: List<Pair<String, Double>>) {
    var zoomed by remember { mutableStateOf(false) }
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val labelColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColorArgb  = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f).toArgb()
    val barColorArgb   = "#F44336".toColorInt()
    val labels = remember(pvData) { pvData.map { it.first } }
    val values = remember(pvData) { pvData.map { it.second } }
    val total  = remember(pvData) { pvData.sumOf { it.second } }
    val kwhDf  = remember { DecimalFormat("#,##0.0") }

    Column(modifier = Modifier.fillMaxWidth().clickable { zoomed = true }) {
        Text(stringResource(R.string.ui2_dash_solar_total, kwhDf.format(total)) + "  ↗",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 2.dp))
        AndroidView(
            factory = { ctx -> BarChart(ctx).apply { stylePVBarChart(this, labelColorArgb, gridColorArgb) } },
            update = { chart ->
                stylePVBarChart(chart, labelColorArgb, gridColorArgb)
                val entries = values.mapIndexed { i, v -> BarEntry(i.toFloat(), v.toFloat()) }
                val ds = BarDataSet(entries, "").apply { color = barColorArgb; setDrawValues(false) }
                chart.xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(v: Float) = labels.getOrElse(v.toInt()) { "" }
                }
                chart.xAxis.labelCount = minOf(labels.size, 8)
                chart.data = BarData(ds); chart.invalidate()
            },
            modifier = Modifier.fillMaxWidth().height(100.dp)
        )
    }
    if (zoomed) {
        val size = with(density) { (minOf(containerSize.width, containerSize.height) * 1.0f).toDp() }
        Dialog(onDismissRequest = { zoomed = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.size(size), shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                Column(modifier = Modifier.padding(12.dp).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.ui2_dash_solar_gen),
                        style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    AndroidView(
                        factory = { ctx -> BarChart(ctx).apply { stylePVBarChart(this, labelColorArgb, gridColorArgb) } },
                        update = { chart ->
                            stylePVBarChart(chart, labelColorArgb, gridColorArgb)
                            val entries = values.mapIndexed { i, v -> BarEntry(i.toFloat(), v.toFloat()) }
                            val ds = BarDataSet(entries, "").apply { color = barColorArgb; setDrawValues(false) }
                            chart.xAxis.valueFormatter = object : ValueFormatter() {
                                override fun getFormattedValue(v: Float) = labels.getOrElse(v.toInt()) { "" }
                            }
                            chart.xAxis.labelCount = minOf(labels.size, 12)
                            chart.data = BarData(ds); chart.invalidate()
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                }
            }
        }
    }
}

// ─── Data source content ──────────────────────────────────────────────────
// The "D / M / Y / *  < >" selector lives in PeriodSelector.kt.

@Composable
private fun DataSourceExplorePies(
    importerType: ComparisonUIViewModel.Importer,
    periodTotals: PeriodTotals?
) {
    if (periodTotals == null) {
        CircularProgressIndicator(modifier = Modifier.padding(4.dp).size(20.dp), strokeWidth = 2.dp)
        return
    }

    val isEsbn = importerType == ComparisonUIViewModel.Importer.ESBNHDF ||
        importerType == ComparisonUIViewModel.Importer.OCTOPUS
    val l = dashPieLabels()
    val charts = remember(periodTotals, isEsbn, l) {
        if (isEsbn) {
            listOf(
                l.importExport to listOf(
                    PieSlice(l.importW, periodTotals.buy,  SeriesColors.gridImport),
                    PieSlice(l.exportW, periodTotals.feed, SeriesColors.export)
                )
            )
        } else {
            // Mirror UI2GraphsFragment.buildGraphPieSpecs — same four-pie set so the
            // dashboard's at-a-glance summary matches what the user sees when they
            // drill into Explore Data. For AlphaESS:
            //   • v1 (pre-migration) rows leave pv2*/bat2*/grid2* fields at 0, so the
            //     extra slices appear degenerate or empty. Once the Migrate worker
            //     stamps v2 they fill out fully.
            //   • Battery Flows is gated on actual battery activity in the data, not
            //     on a scenario flag — AlphaESS has no scenario, so we read it off
            //     PeriodTotals.charging/discharging.
            val gridToLoad = maxOf(0.0, periodTotals.buy - periodTotals.grid2bat)
            val pvUsed = maxOf(0.0, periodTotals.pv2load + periodTotals.pv2bat)
                .takeIf { it > 0 } ?: maxOf(0.0, periodTotals.pv - periodTotals.feed)
            val out = mutableListOf<Pair<String, List<PieSlice>>>()
            out += l.selfConsumption to listOf(
                PieSlice(l.pvUsed, pvUsed, SeriesColors.solar),
                PieSlice(l.exported, periodTotals.feed, SeriesColors.export)
            )
            out += l.loadSource to buildList {
                if (periodTotals.pv2load > 0) add(PieSlice(l.solar,   periodTotals.pv2load, SeriesColors.solar))
                if (periodTotals.bat2load > 0) add(PieSlice(l.battery, periodTotals.bat2load, SeriesColors.battery))
                if (gridToLoad > 0)            add(PieSlice(l.grid,    gridToLoad,           SeriesColors.gridImport))
                // Pre-v2 fallback: if no flow-decomposed slices, fall back to the
                // legacy approximation so the pie is never empty.
                if (isEmpty()) {
                    val approxSolar = maxOf(0.0, periodTotals.load - periodTotals.buy)
                    if (approxSolar > 0) add(PieSlice(l.solar,  approxSolar,        SeriesColors.solar))
                    if (periodTotals.buy > 0) add(PieSlice(l.grid,   periodTotals.buy,   SeriesColors.gridImport))
                }
            }
            if (periodTotals.pv > 0) {
                out += l.solarDistribution to buildList {
                    if (periodTotals.pv2load > 0)  add(PieSlice(l.toLoad,   periodTotals.pv2load, SeriesColors.house))
                    if (periodTotals.pv2bat > 0)   add(PieSlice(l.battery,  periodTotals.pv2bat,  SeriesColors.pvToBattery))
                    if (periodTotals.evActual > 0) add(PieSlice(l.ev,       periodTotals.evActual, SeriesColors.ev))
                    if (periodTotals.feed > 0)     add(PieSlice(l.exported, periodTotals.feed,    SeriesColors.export))
                }
            }
            val batteryActive = periodTotals.charging > 0 || periodTotals.discharging > 0 ||
                    periodTotals.pv2bat > 0 || periodTotals.grid2bat > 0 ||
                    periodTotals.bat2load > 0 || periodTotals.bat2grid > 0
            if (batteryActive) {
                val flows = buildList {
                    if (periodTotals.pv2bat > 0)   add(PieSlice(l.solarIn, periodTotals.pv2bat,   SeriesColors.pvToBattery))
                    if (periodTotals.grid2bat > 0) add(PieSlice(l.gridIn,  periodTotals.grid2bat, SeriesColors.gridToBattery))
                    if (periodTotals.bat2load > 0) add(PieSlice(l.toLoad,  periodTotals.bat2load, SeriesColors.battery))
                    if (periodTotals.bat2grid > 0) add(PieSlice(l.toGrid,  periodTotals.bat2grid, SeriesColors.batteryToGrid))
                    // Pre-v2 fallback: no flow decomposition yet, so show net battery
                    // throughput from the legacy charge/discharge columns. Migration
                    // upgrades this to the four-source breakdown above.
                    if (isEmpty()) {
                        if (periodTotals.charging > 0)    add(PieSlice(l.charge,    periodTotals.charging,    SeriesColors.batteryCharge))
                        if (periodTotals.discharging > 0) add(PieSlice(l.discharge, periodTotals.discharging, SeriesColors.batteryDischarge))
                    }
                }
                if (flows.isNotEmpty()) out += l.batteryFlows to flows
            }
            out
        }
    }

    var zoomedChart by remember { mutableIntStateOf(-1) }
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current

    // Wrap charts into a grid so 3 and 4 pies stay readable. Column count is
    // width-driven (central itemsPerRow): 2 on a phone, 3-4 on a tablet, so
    // landscape / large screens stop wasting half the row. Empty grid cells get
    // a Spacer to keep columns aligned.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        val cols = itemsPerRow(maxWidth, AdaptiveLayout.PIE_CELL_MIN_WIDTH)
            .coerceIn(1, charts.size)
        val rows = (charts.size + cols - 1) / cols
        Column(modifier = Modifier.fillMaxWidth()) {
            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until cols) {
                        val idx = row * cols + col
                        if (idx < charts.size) {
                            val (title, slices) = charts[idx]
                            val visible = slices.filter { it.value > 0 }
                            Column(
                                modifier = Modifier.weight(1f).clickable { zoomedChart = idx }.padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(title, style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center)
                                Spacer(Modifier.height(4.dp))
                                PieChart(slices = visible, modifier = Modifier.size(80.dp))
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    if (zoomedChart >= 0) {
        val (title, slices) = charts[zoomedChart]
        val visible = slices.filter { it.value > 0 }
        val size = with(density) { (minOf(containerSize.width, containerSize.height) * 0.9f).toDp() }
        Dialog(onDismissRequest = { zoomedChart = -1 },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.size(size), shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    PieChart(slices = visible, modifier = Modifier.size(size * 0.55f), isDonut = true)
                    Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        PieLegend(slices = visible)
                    }
                }
            }
        }
    }
}

@Composable
private fun DataSourceCostingsTable(
    costings: List<DataSourceCostingRow>?,
    df: DecimalFormat,
    favouritePlanId: Long? = null
) {
    if (costings == null) {
        CircularProgressIndicator(modifier = Modifier.padding(4.dp).size(20.dp), strokeWidth = 2.dp)
        return
    }
    // Only active plans appear in the dashboard's Tariff Plan table — the
    // Compare tab still evaluates every plan regardless. Toggle via the
    // Supplier Plans screen (edit icon on this accordion).
    val visible = costings.filter { it.active }
    if (visible.isEmpty()) {
        Text(
            stringResource(if (costings.isEmpty()) R.string.ui2_dash_no_plans
                           else R.string.ui2_dash_no_active_plans),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    val firstId = visible.firstOrNull()?.pricePlanId
    val columns = listOf(
        PinnedScrollColumn<DataSourceCostingRow>(
            header = stringResource(R.string.ui2_dash_net),
            accent = { it.pricePlanId == firstId },
            cell = { row ->
                Text(df.format(row.net / 100.0),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                    color = if (row.pricePlanId == firstId)
                        MaterialTheme.colorScheme.primary else Color.Unspecified)
            }
        ),
        PinnedScrollColumn<DataSourceCostingRow>(header = stringResource(R.string.ui2_dash_buy), cell = { row ->
            Text(df.format(row.buy / 100.0),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }),
        PinnedScrollColumn<DataSourceCostingRow>(header = stringResource(R.string.ui2_dash_sell), cell = { row ->
            Text(df.format(row.sell / 100.0),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }),
        PinnedScrollColumn<DataSourceCostingRow>(header = stringResource(R.string.ui2_cmp_fixed), cell = { row ->
            Text(df.format(row.fixed),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        })
    )

    CostingsTableWithBandPie(
        rows = visible,
        rowId = { it.pricePlanId },
        pinnedCell = { row ->
            val isFav = favouritePlanId != null && favouritePlanId == row.pricePlanId
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isFav) {
                    Icon(Icons.Default.Star,
                        contentDescription = stringResource(R.string.ui2_dash_current_plan_cd),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                }
                Text(row.planName,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        },
        columns = columns,
        rowBackground = { row, idx ->
            val isFav = favouritePlanId != null && favouritePlanId == row.pricePlanId
            when {
                isFav -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                idx == 0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> Color.Transparent
            }
        },
        planName = { it.planName },
        bandSlices = { row ->
            val st = row.subTotals
            if (st == null) emptyList()
            else st.prices.sortedBy { it }.mapIndexed { i, price ->
                val kwh = st.getSubTotalForPrice(price) ?: 0.0
                // Region-aware minor unit ("c" / "p"), not a hardcoded cent.
                PieSlice("%.1f".format(price) + RegionProfiles.current.minorSymbol,
                    kwh, TARIFF_PIE_COLORS[i % TARIFF_PIE_COLORS.size])
            }.filter { it.value > 0 }
        }
    )
}

@Composable
private fun PeriodTotalRow(label: String, value: Double, df: DecimalFormat) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text("${df.format(value)} kWh", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DataSourceDistributionCharts(distribution: UsageDistribution) {
    var zoomedIdx by remember { mutableIntStateOf(-1) }

    val hourLabels  = remember { (0..23).map { "%02d".format(it) } }
    val dayLabels   = stringArrayResource(R.array.ui2_days_short_sun_first).toList()
    val monthLabels = stringArrayResource(R.array.ui2_months_short).toList()

    // ESBN has no load series, so its distributions fall back to grid import —
    // label them accordingly. Every other importer buckets actual load.
    val metric = stringResource(
        if (distribution.basedOnLoad) R.string.ui2_graphs_load else R.string.ui2_dash_import)
    val hourlyTitle  = stringResource(R.string.ui2_dash_hourly_pct)
    val dailyTitle   = stringResource(R.string.ui2_dash_daily_pct)
    val monthlyTitle = stringResource(R.string.ui2_dash_monthly_pct)
    val charts = remember(distribution, metric) {
        listOf(
            Triple("$metric $hourlyTitle",  distribution.hourly,   hourLabels),
            Triple("$metric $dailyTitle",   distribution.daily,    dayLabels),
            Triple("$metric $monthlyTitle", distribution.monthly,  monthLabels)
        )
    }

    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current

    // At MEDIUM+ widths render the three distributions side-by-side (Hourly /
    // Daily / Monthly) so landscape phones and tablets stop showing skinny
    // 80-dp bars stacked vertically.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val visible = charts.mapIndexedNotNull { idx, c ->
            if (c.second.any { it > 0 }) idx to c else null
        }
        if (visible.isEmpty()) return@BoxWithConstraints
        if (maxWidth >= AdaptiveLayout.WIDTH_MEDIUM_AT) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                visible.forEach { (idx, c) ->
                    val (title, dist, labels) = c
                    Column(modifier = Modifier.weight(1f).clickable { zoomedIdx = idx }) {
                        Text("$title  ↗", style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(bottom = 2.dp))
                        SimpleDistBarChart(dist = dist, labels = labels,
                            modifier = Modifier.fillMaxWidth().height(80.dp))
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visible.forEach { (idx, c) ->
                    val (title, dist, labels) = c
                    Column(modifier = Modifier.fillMaxWidth().clickable { zoomedIdx = idx }) {
                        Text("$title  ↗", style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(bottom = 2.dp))
                        SimpleDistBarChart(dist = dist, labels = labels,
                            modifier = Modifier.fillMaxWidth().height(80.dp))
                    }
                }
            }
        }
    }

    if (zoomedIdx >= 0) {
        val (title, dist, labels) = charts[zoomedIdx]
        val size = with(density) { (minOf(containerSize.width, containerSize.height) * 1.0f).toDp() }
        Dialog(onDismissRequest = { zoomedIdx = -1 },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.size(size), shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                Column(
                    modifier = Modifier.padding(12.dp).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    SimpleDistBarChart(dist = dist, labels = labels,
                        modifier = Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

data class PieSlice(val label: String, val value: Double, val color: Color)

/** Pie titles/slice names resolved from resources in the composable and handed
 *  to the (non-composable) chart builders inside `remember` blocks. Reuses the
 *  ui2_graphs_* vocabulary so the dashboard pies match the Graphs tab. */
private data class DashPieLabels(
    val selfConsumption: String, val loadSource: String, val solarDistribution: String,
    val batteryFlows: String, val loadDistribution: String, val importExport: String,
    val pvUsed: String, val exported: String, val solar: String, val battery: String,
    val grid: String, val toLoad: String, val toGrid: String, val solarIn: String,
    val gridIn: String, val ev: String, val hotWater: String, val house: String,
    val sold: String, val bought: String, val importW: String, val exportW: String,
    val charge: String, val discharge: String
)

@Composable
private fun dashPieLabels() = DashPieLabels(
    selfConsumption   = stringResource(R.string.ui2_graphs_self_consumption),
    loadSource        = stringResource(R.string.ui2_graphs_load_source),
    solarDistribution = stringResource(R.string.ui2_graphs_solar_distribution),
    batteryFlows      = stringResource(R.string.ui2_graphs_battery_flows),
    loadDistribution  = stringResource(R.string.ui2_dash_load_distribution),
    importExport      = stringResource(R.string.ui2_dash_import_export),
    pvUsed            = stringResource(R.string.ui2_graphs_pv_used),
    exported          = stringResource(R.string.ui2_graphs_exported),
    solar             = stringResource(R.string.ui2_graphs_solar),
    battery           = stringResource(R.string.ui2_graphs_battery),
    grid              = stringResource(R.string.ui2_graphs_grid),
    toLoad            = stringResource(R.string.ui2_graphs_to_load),
    toGrid            = stringResource(R.string.ui2_graphs_to_grid),
    solarIn           = stringResource(R.string.ui2_graphs_solar_in),
    gridIn            = stringResource(R.string.ui2_graphs_grid_in),
    ev                = stringResource(R.string.ui2_graphs_ev),
    hotWater          = stringResource(R.string.ui2_graphs_hot_water),
    house             = stringResource(R.string.ui2_dash_house),
    sold              = stringResource(R.string.ui2_dash_sold),
    bought            = stringResource(R.string.ui2_dash_bought),
    importW           = stringResource(R.string.ui2_dash_import),
    exportW           = stringResource(R.string.ui2_graphs_export),
    charge            = stringResource(R.string.ui2_dash_charge),
    discharge         = stringResource(R.string.ui2_dash_discharge)
)

@Composable
fun PieChart(slices: List<PieSlice>, modifier: Modifier = Modifier, isDonut: Boolean = false) {
    val holeColor = MaterialTheme.colorScheme.surface
    Canvas(modifier = modifier) {
        val total = slices.sumOf { it.value }.toFloat()
        if (total <= 0f) return@Canvas
        var startAngle = -90f
        slices.forEach { slice ->
            val sweep = (slice.value.toFloat() / total) * 360f
            drawArc(color = slice.color, startAngle = startAngle, sweepAngle = sweep, useCenter = true)
            startAngle += sweep
        }
        if (isDonut) {
            drawCircle(color = holeColor, radius = size.minDimension * 0.245f)
        }
    }
}

@Composable
fun PieLegend(slices: List<PieSlice>) {
    Column(modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
        slices.forEach { slice ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Box(modifier = Modifier.size(12.dp).background(slice.color))
                Spacer(Modifier.width(8.dp))
                Text("${slice.label}: ${"%.1f".format(slice.value)} kWh", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun SimulationPieCharts(kpis: SimKPIs) {
    var zoomedChart by remember { mutableIntStateOf(-1) }

    val l = dashPieLabels()
    val charts = remember(kpis, l) {
        listOf(
            l.selfConsumption to listOf(
                PieSlice(l.pvUsed, maxOf(0.0, kpis.generated - kpis.sold), SeriesColors.solar),
                PieSlice(l.sold, kpis.sold, SeriesColors.export)
            ),
            l.loadSource to listOf(
                PieSlice(l.solar, maxOf(0.0, kpis.totalLoad - kpis.bought), SeriesColors.solar),
                PieSlice(l.bought, kpis.bought, SeriesColors.gridImport)
            ),
            l.solarDistribution to listOf(
                PieSlice(l.toLoad, kpis.pvToLoad, SeriesColors.house),
                PieSlice(l.battery, kpis.pvToCharge, SeriesColors.pvToBattery),
                PieSlice(l.ev, kpis.evDiv, SeriesColors.ev),
                PieSlice(l.hotWater, kpis.h2oDiv, SeriesColors.hotWater),
                PieSlice(l.sold, kpis.sold, SeriesColors.export)
            ),
            l.loadDistribution to listOf(
                PieSlice(l.house, kpis.house, SeriesColors.house),
                PieSlice(l.hotWater, kpis.h20, SeriesColors.hotWater),
                PieSlice(l.ev, kpis.ev, SeriesColors.ev)
            )
        )
    }

    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current

    Column(modifier = Modifier.padding(top = 4.dp)) {
        for (row in 0 until 2) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 2) {
                    val idx = row * 2 + col
                    val visible = charts[idx].second.filter { it.value > 0 }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { zoomedChart = idx }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            charts[idx].first,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        PieChart(slices = visible, modifier = Modifier.size(80.dp))
                    }
                }
            }
        }
    }

    if (zoomedChart >= 0) {
        val (title, slices) = charts[zoomedChart]
        val visible = slices.filter { it.value > 0 }
        val size = with(density) { (minOf(containerSize.width, containerSize.height) * 0.9f).toDp() }
        Dialog(onDismissRequest = { zoomedChart = -1 },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.size(size), shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    PieChart(slices = visible, modifier = Modifier.size(size * 0.55f), isDonut = true)
                    Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        PieLegend(slices = visible)
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandableCard(
    title: String,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable (expanded: Boolean) -> Unit)? = null,
    showEdit: Boolean = true,
    initiallyExpanded: Boolean = false,
    onEdit: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize(
                animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
            )
    ) {
        Column(modifier = Modifier.clickable { expanded = !expanded }.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (trailingContent != null) {
                    trailingContent(expanded)
                    Spacer(Modifier.width(4.dp))
                }
                if (expanded && showEdit && onEdit != null) {
                    Box(
                        modifier = Modifier
                            .clickable { onEdit() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.ui2_edit),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.ui2_collapse else R.string.ui2_expand)
                )
            }
            if (expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    content()
                }
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
   Topology diagram — inverter centre, PV strings on top per MPPT,
   consumers (house / hot water / EV) on the left, grid on the right,
   and batteries along the bottom with a shared bus when there are
   multiple. Decorated with schedule / discharge / divert icons.
────────────────────────────────────────────────────────────────── */

@Composable
private fun TopologyDiagram(sc: ScenarioComponents) {
    val inverters = sc.inverters.orEmpty()
    val panels = sc.panels.orEmpty()
    val batteries = sc.batteries.orEmpty()
    val loadShifts = sc.loadShifts.orEmpty()
    val discharges = sc.discharges.orEmpty()
    val evCharges = sc.evCharges.orEmpty()
    val evDiverts = sc.evDiverts.orEmpty()
    val hwSchedules = sc.hwSchedules.orEmpty()
    val hwDivertActive = sc.hwDivert?.isActive == true
    val heatPumps = sc.heatPumps.orEmpty()

    val hasLoad = sc.loadProfile != null
    val hasHw = sc.hwSystem != null
    val hasEv = evCharges.isNotEmpty() || evDiverts.isNotEmpty()
    val hasHeatPump = heatPumps.isNotEmpty()
    val hasInverter = inverters.isNotEmpty()

    val lineColor = MaterialTheme.colorScheme.outline
    val busColor = MaterialTheme.colorScheme.primary

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(380.dp)) {
        val totalW = maxWidth
        val totalH = maxHeight
        val cardW = 80.dp
        val cardH = 56.dp
        val invW = 116.dp
        val invH = 68.dp
        val pvTop = 6.dp
        val battTop = totalH - cardH - 6.dp
        val leftX = 4.dp
        val rightX = totalW - cardW - 4.dp
        val cx = totalW / 2
        val cy = totalH / 2
        val invLeft = cx - invW / 2
        val invTop = cy - invH / 2

        // ── Connection lines ─────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 1.5.dp.toPx()
            val strokeBus = 2.5.dp.toPx()
            val invLeftPx = invLeft.toPx()
            val invTopPx = invTop.toPx()
            val invWPx = invW.toPx()
            val invHPx = invH.toPx()
            val invRightPx = invLeftPx + invWPx
            val invBottomPx = invTopPx + invHPx
            val invMidYPx = invTopPx + invHPx / 2
            val cardWPx = cardW.toPx()
            val cardHPx = cardH.toPx()
            val cxPx = cx.toPx()
            val totalWPx = totalW.toPx()
            val totalHPx = totalH.toPx()
            val leftXPx = leftX.toPx()
            val rightXPx = rightX.toPx()
            val pvTopPx = pvTop.toPx()
            val battTopPx = battTop.toPx()
            val padPx = 4.dp.toPx()

            // PV strings → top of inverter at MPPT entry points
            if (panels.isNotEmpty() && hasInverter) {
                val inv = inverters.first()
                val mpptCount = inv.mpptCount.coerceAtLeast(1)
                val n = panels.size
                val gridW = totalWPx - 2 * padPx
                panels.forEachIndexed { i, p ->
                    val pvCenterX = padPx + gridW * (i + 0.5f) / n
                    val pvBottomY = pvTopPx + cardHPx
                    val mpptIdx = (p.mppt - 1).coerceIn(0, mpptCount - 1)
                    val invTopX = invLeftPx + invWPx * (mpptIdx + 0.5f) / mpptCount
                    drawLine(
                        color = lineColor,
                        start = Offset(pvCenterX, pvBottomY),
                        end = Offset(invTopX, invTopPx),
                        strokeWidth = stroke
                    )
                }
            }

            // Batteries → bottom of inverter (shared bus when >1)
            if (batteries.isNotEmpty() && hasInverter) {
                val n = batteries.size
                val gridW = totalWPx - 2 * padPx
                val busY = battTopPx - 12.dp.toPx()
                val xs = (0 until n).map { i -> padPx + gridW * (i + 0.5f) / n }
                if (n > 1) {
                    drawLine(busColor, Offset(xs.first(), busY), Offset(xs.last(), busY), strokeBus)
                    xs.forEach { x ->
                        drawLine(busColor, Offset(x, busY), Offset(x, battTopPx), stroke)
                    }
                    drawLine(busColor, Offset(cxPx, busY), Offset(cxPx, invBottomPx), strokeBus)
                } else {
                    drawLine(lineColor, Offset(xs[0], battTopPx), Offset(cxPx, invBottomPx), stroke)
                }
            }

            // Consumers (left) → inverter left edge
            val consumers = listOfNotNull(
                if (hasLoad) Unit else null,
                if (hasHw)   Unit else null,
                if (hasEv)   Unit else null,
                if (hasHeatPump) Unit else null
            )
            if (consumers.isNotEmpty() && hasInverter) {
                val cardRightX = leftXPx + cardWPx
                val total = consumers.size
                val topPad = 70.dp.toPx()
                val bottomPad = 90.dp.toPx()
                val span = totalHPx - topPad - bottomPad
                consumers.forEachIndexed { i, _ ->
                    val yCenter = topPad + span * (i + 0.5f) / total
                    drawLine(
                        color = lineColor,
                        start = Offset(cardRightX, yCenter),
                        end = Offset(invLeftPx, invMidYPx),
                        strokeWidth = stroke
                    )
                }
            }

            // Grid (right) → inverter right edge
            if (hasInverter) {
                drawLine(
                    color = busColor,
                    start = Offset(invRightPx, invMidYPx),
                    end = Offset(rightXPx, invMidYPx),
                    strokeWidth = strokeBus
                )
            }
        }

        // ── Card layer ────────────────────────────────────────────
        // Inverter (centre)
        TopologyNode(
            label = inverters.firstOrNull()?.inverterName
                ?: stringResource(R.string.ui2_dash_no_inverter_node),
            subline = if (hasInverter)
                stringResource(R.string.ui2_dash_inv_subline,
                    "%.1f".format(inverters.first().maxInverterLoad),
                    inverters.first().mpptCount)
            else null,
            iconRes = R.drawable.inverter,
            highlight = true,
            modifier = Modifier
                .width(invW).height(invH)
                .offset(x = invLeft, y = invTop)
        )
        if (inverters.size > 1) {
            Text(stringResource(R.string.ui2_dash_n_more, inverters.size - 1),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.offset(x = invLeft + 4.dp, y = invTop + invH + 2.dp))
        }

        // PV strings (top)
        if (panels.isNotEmpty()) {
            val n = panels.size
            panels.forEachIndexed { i, p ->
                val perCol = (totalW - 8.dp) / n
                val xPos = 4.dp + perCol * i + (perCol - cardW) / 2
                TopologyNode(
                    label = p.panelName.takeIf { !it.isNullOrBlank() } ?: "S${i + 1}",
                    subline = "${p.panelCount}×${p.panelkWp}W",
                    iconRes = R.drawable.solarpanel,
                    badge = stringResource(R.string.ui2_dash_mppt_n, p.mppt),
                    modifier = Modifier
                        .width(cardW).height(cardH)
                        .offset(x = xPos, y = pvTop)
                )
            }
        }

        // Batteries (bottom)
        if (batteries.isNotEmpty()) {
            val n = batteries.size
            batteries.forEachIndexed { i, b ->
                val perCol = (totalW - 8.dp) / n
                val xPos = 4.dp + perCol * i + (perCol - cardW) / 2
                val invName = b.inverter ?: ""
                val hasCharge = loadShifts.any { it.inverter == invName }
                val hasDisch = discharges.any { it.inverter == invName }
                TopologyNode(
                    label = "B${i + 1}",
                    subline = "${"%.1f".format(b.batterySize)} kWh",
                    iconRes = R.drawable.battery1,
                    decorationIcons = listOfNotNull(
                        if (hasCharge) R.drawable.ic_baseline_access_time_24 else null,
                        if (hasDisch) R.drawable.baseline_file_upload_24 else null
                    ),
                    modifier = Modifier
                        .width(cardW).height(cardH)
                        .offset(x = xPos, y = battTop)
                )
            }
        }

        // Consumers (left, stacked top-to-bottom)
        run {
            data class C(val label: String, val sub: String?, val icon: Int, val deco: List<Int>,
                         val emoji: String? = null)
            val consumers = mutableListOf<C>()
            if (hasLoad) consumers += C(
                stringResource(R.string.ui2_dash_house),
                sc.loadProfile?.annualUsage?.let { "${"%.0f".format(it)} kWh/y" },
                R.drawable.house,
                emptyList()
            )
            if (hasHw) consumers += C(
                stringResource(R.string.ui2_component_hot_water),
                sc.hwSystem?.let { "${it.hwCapacity} L · ${it.hwRate} kW" },
                R.drawable.waterwarm,
                listOfNotNull(
                    if (hwSchedules.isNotEmpty()) R.drawable.ic_baseline_access_time_24 else null,
                    if (hwDivertActive) R.drawable.ic_baseline_call_split_24 else null
                )
            )
            if (hasEv) consumers += C(
                stringResource(R.string.ui2_component_ev),
                if (evCharges.isNotEmpty())
                    stringResource(R.string.ui2_dash_n_sched, evCharges.size) else null,
                R.drawable.ev_on,
                listOfNotNull(
                    if (evCharges.isNotEmpty()) R.drawable.ic_baseline_access_time_24 else null,
                    if (evDiverts.isNotEmpty()) R.drawable.ic_baseline_call_split_24 else null
                )
            )
            if (hasHeatPump) consumers += C(
                stringResource(R.string.ui2_component_heat_pump),
                heatPumps.first().let {
                    "${"%.1f".format(it.capacityKw)} kW · " + (if (it.weatherSource == "cds") "CDS"
                        else stringResource(R.string.ui2_dash_weather_sample_word))
                },
                0,                       // no drawable — rendered via the emoji below
                emptyList(),
                emoji = "♨️"        // ♨️, matching the heat-pump glyph used elsewhere in the dashboard
            )
            val total = consumers.size
            val topPad = 70.dp
            val bottomPad = 90.dp
            val span = totalH - topPad - bottomPad
            consumers.forEachIndexed { i, c ->
                val yCenter = topPad + span * (i + 0.5f) / total
                TopologyNode(
                    label = c.label,
                    subline = c.sub,
                    iconRes = c.icon,
                    decorationIcons = c.deco,
                    emojiIcon = c.emoji,
                    modifier = Modifier
                        .width(cardW).height(cardH)
                        .offset(x = leftX, y = yCenter - cardH / 2)
                )
            }
        }

        // Grid (right)
        TopologyNode(
            label = stringResource(R.string.ui2_graphs_grid),
            subline = stringResource(R.string.ui2_dash_import_export_sub),
            iconRes = R.drawable.baseline_file_upload_24,
            iconTinted = true,
            modifier = Modifier
                .width(cardW).height(cardH)
                .offset(x = rightX, y = cy - cardH / 2)
        )

        // Empty state
        if (!hasInverter && panels.isEmpty() && batteries.isEmpty() && !hasLoad && !hasHw && !hasEv && !hasHeatPump) {
            Text(
                stringResource(R.string.ui2_dash_nothing_to_draw),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(x = leftX, y = cy - 8.dp)
            )
        }
    }
}

@Composable
private fun TopologyNode(
    label: String,
    subline: String?,
    iconRes: Int,
    modifier: Modifier = Modifier,
    badge: String? = null,
    highlight: Boolean = false,
    iconTinted: Boolean = false,
    decorationIcons: List<Int> = emptyList(),
    emojiIcon: String? = null   // when set, drawn instead of [iconRes] (e.g. ♨️ for the heat pump, which has no drawable)
) {
    val container = if (highlight) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
    val borderColor = if (highlight) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val textColor = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (emojiIcon != null) {
                Text(emojiIcon, style = MaterialTheme.typography.labelMedium)
            } else {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (iconTinted) textColor else Color.Unspecified
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (subline != null) {
            Text(
                text = subline,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (badge != null) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
        if (decorationIcons.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                decorationIcons.forEach { res ->
                    Icon(
                        painter = painterResource(res),
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = StatusGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun TopologyLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        LegendItem(R.drawable.ic_baseline_access_time_24,
            stringResource(R.string.ui2_dash_legend_schedule))
        LegendItem(R.drawable.baseline_file_upload_24,
            stringResource(R.string.ui2_dash_discharge))
        LegendItem(R.drawable.ic_baseline_call_split_24,
            stringResource(R.string.ui2_dash_legend_divert))
    }
}

@Composable
private fun LegendItem(iconRes: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = StatusGreen
        )
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ──────────────────────────────────────────────────────────────────────────
// KPI accordion — same data set the legacy ImportKeyStatsFragment shows
// (self-consumption / sufficiency / max-self-sufficiency, PV total, feed total
// + per-month best/worst/average). The range picker drives the summary;
// the 13-button * J F M A M J J A S O N D row filters only the monthly
// best/worst/avg table — so it sits between the two tables, not next to
// the date picker.
// ──────────────────────────────────────────────────────────────────────────
@Composable
private fun KpiAccordion(
    period: DataSourcePeriod,
    anchor: LocalDate,
    monthFilter: Int,
    summary: KpiSummary?,
    months: List<KpiMonthRow>?,
    bounds: Pair<LocalDate, LocalDate>?,
    onPeriodChange: (DataSourcePeriod, LocalDate) -> Unit,
    onNavigate: (forward: Boolean) -> Unit,
    onMonthFilterChange: (Int) -> Unit,
    df: DecimalFormat,
    kwhDf: DecimalFormat,
    showHints: Boolean
) {
    ExpandableCard(
        title = stringResource(R.string.ui2_dash_kpis),
        leadingIcon = {
            Icon(painterResource(R.drawable.barchart), null, Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface)
        },
        trailingContent = { _ ->
            if (summary != null)
                Icon(painterResource(R.drawable.tick), null, Modifier.size(18.dp), tint = Color.Unspecified)
        },
        showEdit = false
    ) {
        // Use the simulation / source's actual data bounds so the chevron
        // navigation stops at the real data edges and the picker doesn't show
        // anchors decades before any data exists (the old 1976 issue).
        val cosmeticStart = (bounds?.first ?: anchor).toString()
        val cosmeticEnd   = (bounds?.second ?: anchor).toString()
        PeriodSelector(
            selectedPeriod = period,
            anchorDate     = anchor,
            dataStart      = cosmeticStart,
            dataEnd        = cosmeticEnd,
            advanced       = false,
            onPeriodChange = { p, a, _ -> onPeriodChange(p, a) },
            onNavigate     = { fwd, _ -> onNavigate(fwd) }
        )
        Spacer(Modifier.height(8.dp))

        // KPI summary table — driven by the date picker above.
        when (summary) {
            null -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else -> KpiSummaryTable(summary, df, kwhDf, showHints)
        }

        // Month filter sits directly above the per-month table it controls.
        Spacer(Modifier.height(10.dp))
        MonthFilterRow(monthFilter, onMonthFilterChange)
        Spacer(Modifier.height(8.dp))

        // Monthly key stats — filtered by the chip row above.
        when (months) {
            null -> {}
            else -> {
                val filtered = if (monthFilter == 0) months
                               else months.filter { it.monthNumber == monthFilter }
                KpiMonthsTable(filtered, kwhDf)
            }
        }
    }
}

@Composable
private fun MonthFilterRow(selected: Int, onChange: (Int) -> Unit) {
    val labelsShort = listOf("*") + stringArrayResource(R.array.ui2_months_letters)
    val labelsLong  = listOf(stringResource(R.string.ui2_all)) +
        stringArrayResource(R.array.ui2_months_short)
    // AdaptiveChipRow keeps all 13 on one weighted line at normal font, wraps
    // to multiple rows as the font enlarges, and collapses to a dropdown at
    // the largest tier. 3-letter labels appear in landscape (MEDIUM+).
    AdaptiveChipRow(
        items = labelsShort.indices.toList(),
        isSelected = { it == selected },
        onSelect = onChange,
        label = { labelsShort[it] },
        labelLong = { labelsLong[it] }
    )
}

@Composable
private fun KpiSummaryTable(summary: KpiSummary, df: DecimalFormat, kwhDf: DecimalFormat, showHints: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Formula subs stay literal — PV/Feed/Load notation, not prose.
        KpiRow(stringResource(R.string.ui2_dash_kpi_self_consumption),
            "(PV − Feed) / PV", df.format(summary.selfConsumption) + "%", showHints)
        KpiRow(stringResource(R.string.ui2_dash_kpi_self_sufficiency),
            "(PV − Feed) / Load", df.format(summary.selfSufficiency) + "%", showHints)
        KpiRow(stringResource(R.string.ui2_dash_kpi_max_self_sufficiency),
            "PV / Load", df.format(summary.maxSelfSufficiency) + "%", showHints)
        KpiRow(stringResource(R.string.ui2_dash_kpi_generation),
            "PV", kwhDf.format(summary.pv), showHints)
        KpiRow(stringResource(R.string.ui2_dash_kpi_feed),
            "Feed", kwhDf.format(summary.feed), showHints)
    }
}

@Composable
private fun KpiRow(label: String, sub: String, value: String, showHints: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (showHints) {
                Text(sub, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(value, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun KpiMonthsTable(rows: List<KpiMonthRow>, kwhDf: DecimalFormat) {
    if (rows.isEmpty()) {
        Text(stringResource(R.string.ui2_dash_no_filter_data),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp))
        return
    }
    val columns = listOf(
        PinnedScrollColumn<KpiMonthRow>(header = stringResource(R.string.ui2_dash_pv_tot), cell = { row ->
            Text(kwhDf.format(row.pvTotal),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }),
        PinnedScrollColumn<KpiMonthRow>(
            header = stringResource(R.string.ui2_dash_best),
            minWidth = AdaptiveLayout.SCROLL_COL_MIN_MIXED,
            weight = 1.4f,
            cell = { row ->
                // best/worst arrive as "<value> on <dd>" — re-format the leading
                // kWh value to 1 dp; the DB query (AlphaEssDAO) and the sim path
                // (buildMonthRowsFromDoy) both emit 2 dp, but the display layer
                // is the single source of truth so they stay consistent.
                Text(reformatKwhOn(row.best, kwhDf),
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        ),
        PinnedScrollColumn<KpiMonthRow>(
            header = stringResource(R.string.ui2_dash_worst),
            minWidth = AdaptiveLayout.SCROLL_COL_MIN_MIXED,
            weight = 1.4f,
            cell = { row ->
                Text(reformatKwhOn(row.worst, kwhDf),
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        ),
        PinnedScrollColumn<KpiMonthRow>(header = stringResource(R.string.ui2_dash_avg), cell = { row ->
            Text(kwhDf.format(row.average),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        })
    )
    // Single table — at normal size the weighted layout distributes the
    // columns across the full width (including landscape); under font scaling
    // it pins the YY-MM column and scrolls the values.
    PinnedScrollTable(
        rows = rows,
        pinnedHeader = stringResource(R.string.ui2_dash_yymm),
        pinnedWeight = 1f,
        pinnedCell = { row ->
            Text(row.monthLabel, style = MaterialTheme.typography.bodySmall)
        },
        columns = columns
    )
}

/**
 * Re-format a pre-built "<value> on <dd>" string by parsing the leading
 * number and re-emitting it via [kwhDf] (1 dp). Falls back to the original
 * string if the leading token isn't a number — covers "—" and any future
 * format drift gracefully.
 */
private fun reformatKwhOn(s: String, kwhDf: DecimalFormat): String {
    val space = s.indexOf(' ')
    val head = if (space < 0) s else s.substring(0, space)
    val tail = if (space < 0) "" else s.substring(space)
    val v = head.toDoubleOrNull() ?: return s
    return kwhDf.format(v) + tail
}

/**
 * First-run onboarding card shown on the Dashboard when there are no scenarios
 * and no data source is selected. The button text "Try with sample data" is
 * the canonical Robo selector — see plans/roboscript/robo-plan.md Phase 4B/4C.
 */
/**
 * Empty state shown when the dashboard has no pinned subject but the user
 * already has scenarios and/or data sources available — typically reached
 * after the deletion guards in UI2SharedViewModel clear the saved subject
 * because its underlying scenario/sysSn was deleted. Directs the user to
 * pick a different subject from the navigation drawer.
 */
@Composable
private fun NoActiveSubjectCard() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.ui2_dash_pick_subject_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.ui2_dash_pick_subject_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmptyDashboardSampleCard() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.ui2_dash_welcome),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.ui2_dash_pick_start),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            // (a) Sample data — seed a demo scenario + tariffs and simulate.
            Button(
                onClick = {
                    val loader = EntryPointAccessors
                        .fromApplication(context.applicationContext, SampleDataLoaderEntryPoint::class.java)
                        .sampleDataLoader()
                    coroutineScope.launch {
                        val msg = when (val result = loader.load()) {
                            is SampleDataLoader.Result.AlreadyLoaded ->
                                context.getString(R.string.ui2_sample_already_loaded)
                            is SampleDataLoader.Result.Loaded ->
                                context.getString(R.string.ui2_sample_loaded)
                            is SampleDataLoader.Result.Failed ->
                                context.getString(R.string.ui2_sample_failed,
                                    result.error.message
                                        ?: context.getString(R.string.ui2_unknown_error))
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.ui2_drawer_try_sample)) }

            // (b) Quick mode — the simplified single-screen flow.
            OutlinedButton(
                onClick = { relaunchInMode(context, simple = true) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.ui2_simple_title)) }

            // (c) Add a scenario — same entry point as "+ Create new" on the Scenarios tab.
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        android.content.Intent(context, UI2WizardActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.ui2_dash_add_scenario)) }
        }
    }
}

/**
 * The source edition's welcome / no-subject card: no sample scenario, no quick
 * mode, no wizard to offer — the single path is connecting real data, so the
 * one button opens Data Source Management.
 */
@Composable
private fun EmptyDashboardConnectSourceCard() {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.ui2_dash_welcome),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.ui2_dash_connect_source_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = {
                    context.startActivity(android.content.Intent(
                        context, UI2DataSourceManagementActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.ui2_drawer_data_sources)) }
        }
    }
}
