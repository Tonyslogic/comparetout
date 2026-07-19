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
import com.tfcode.comparetout.ui2.charts.ExpandableCard
import com.tfcode.comparetout.ui2.charts.LoadDistributionCharts
import com.tfcode.comparetout.ui2.charts.PVSummaryBarChart
import com.tfcode.comparetout.ui2.charts.PieChart
import com.tfcode.comparetout.ui2.charts.PieLegend
import com.tfcode.comparetout.ui2.charts.PieSlice
import com.tfcode.comparetout.ui2.charts.SimulationPieCharts
import com.tfcode.comparetout.ui2.charts.SimpleDistBarChart
import com.tfcode.comparetout.ui2.charts.dashPieLabels
import com.tfcode.comparetout.ui2.charts.stylePVBarChart
import com.tfcode.comparetout.ui2.dashboard.MICBreachFlag
import com.tfcode.comparetout.ui2.dashboard.MigrationStatusBanner
import com.tfcode.comparetout.ui2.dashboard.NeedsRegenBanner
import com.tfcode.comparetout.ui2.dashboard.SimulationStatusAccordion
import com.tfcode.comparetout.ui2.dashboard.StatusAmber
import com.tfcode.comparetout.ui2.dashboard.StatusGreen
import com.tfcode.comparetout.ui2.dashboard.StatusRed
import com.tfcode.comparetout.ui2.dashboard.simReadinessIssues
import com.tfcode.comparetout.ui2.dashboard.AllCostingsTable
import com.tfcode.comparetout.ui2.dashboard.DataSourceCostingsTable
import com.tfcode.comparetout.ui2.dashboard.DataSourceDistributionCharts
import com.tfcode.comparetout.ui2.dashboard.DataSourceExplorePies
import com.tfcode.comparetout.ui2.dashboard.DataSourcePVBarChart
import com.tfcode.comparetout.ui2.dashboard.PeriodTotalRow
import com.tfcode.comparetout.ui2.dashboard.TopologyDiagram
import com.tfcode.comparetout.ui2.dashboard.TopologyLegend
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.time.LocalDate


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
