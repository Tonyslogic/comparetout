package com.tfcode.comparetout.ui2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
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
import com.tfcode.comparetout.model.costings.Costings
import com.tfcode.comparetout.model.scenario.LoadProfile
import com.tfcode.comparetout.model.scenario.Panel
import com.tfcode.comparetout.model.scenario.PanelPVSummary
import com.tfcode.comparetout.model.scenario.ScenarioComponents
import com.tfcode.comparetout.model.scenario.SimKPIs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

private fun simReadinessIssues(
    hasLoadProfile: Boolean,
    hasPanels: Boolean,
    hasPanelData: Boolean,
    resultsReady: Boolean
): List<SimReadinessIssue> {
    val issues = mutableListOf<SimReadinessIssue>()
    if (!hasLoadProfile) {
        issues += SimReadinessIssue(
            title = "No usage data linked",
            hint = "Add a load profile in the Usage Data step so the simulation has consumption to model.",
            severity = IssueSeverity.WARNING,
            wizardSection = "load"
        )
    }
    if (hasPanels && !hasPanelData) {
        issues += SimReadinessIssue(
            title = "Solar data not generated yet",
            hint = "Solar yield is fetched per panel from its location. Open PV System to generate it, " +
                "or wait if a simulation is already running.",
            severity = IssueSeverity.WARNING,
            wizardSection = "pv"
        )
    }
    if (!resultsReady) {
        issues += SimReadinessIssue(
            title = "Results aren't ready yet",
            hint = "Costs and charts appear once the simulation finishes calculating. " +
                "It runs automatically after you edit a simulation or its tariffs.",
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
        title = "Needs attention",
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
                            Text("Tap to open the wizard",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
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
                        UI2SharedViewModel.ActiveSelection.None -> {}
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: UI2DashboardViewModel, onSwitchLegacy: () -> Unit, onLaunchGraphs: () -> Unit) {
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
        ?: "Select a Simulation"

    Scaffold(
        modifier = Modifier
            .nestedScroll(rememberNestedScrollInteropConnection())
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    IconButton(onClick = { showDrawer = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
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
                    .verticalScroll(rememberScrollState())
            ) {
            val pvPeriod    by viewModel.pvPeriod.observeAsState(DataSourcePeriod.ALL)
    val pvAnchor    by viewModel.pvAnchor.observeAsState(LocalDate.now())
    val pvChartData by viewModel.pvChartData.observeAsState(null)

            val dsInfo = dashboardData?.dataSourceInfo
            if (dsInfo != null) {
                // ── Data source mode ──────────────────────────────────────────
                // Tariff Plan first — pricing is the most-asked-after answer
                // when a real meter is selected.
                ExpandableCard(
                    title = "Tariff Plan",
                    leadingIcon = {
                        Icon(painterResource(R.drawable.ic_baseline_euro_symbol_24), null,
                            Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
                    },
                    trailingContent = { _ ->
                        if (tariffCostings != null)
                            Icon(painterResource(R.drawable.tick), null, Modifier.size(18.dp), tint = Color.Unspecified)
                    },
                    showEdit = false
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
                    title = "Explore data",
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
                                Icon(painterResource(R.drawable.barchart), "View graphs",
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
                // ESBN is import/export only, so the self-* KPIs would be nonsense.
                if (dsInfo.importerType != ComparisonUIViewModel.Importer.ESBNHDF) {
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
                        df = df
                    )
                }

                ExpandableCard(
                    title = "Usage Data",
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
                    val isEsbn = dsInfo.importerType == ComparisonUIViewModel.Importer.ESBNHDF
                    val totals = usageTotals
                    when {
                        totals == null -> CircularProgressIndicator(
                            modifier = Modifier.padding(4.dp).size(20.dp), strokeWidth = 2.dp)
                        else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (!isEsbn && totals.load > 0) PeriodTotalRow("Load",   totals.load,  kwhDf)
                            if (totals.buy  > 0) PeriodTotalRow("Import", totals.buy,   kwhDf)
                            if (totals.feed > 0) PeriodTotalRow("Export", totals.feed,  kwhDf)
                            if (!isEsbn && totals.pv   > 0) PeriodTotalRow("Solar",      totals.pv,          kwhDf)
                            if (!isEsbn && totals.charging    > 0) PeriodTotalRow("Charging",    totals.charging,    kwhDf)
                            if (!isEsbn && totals.discharging > 0) PeriodTotalRow("Discharging", totals.discharging, kwhDf)
                        }
                    }
                    val dist = usageDistribution
                    if (dist != null) {
                        Spacer(Modifier.height(8.dp))
                        DataSourceDistributionCharts(distribution = dist)
                    }
                }

                if (dsInfo.importerType != ComparisonUIViewModel.Importer.ESBNHDF) {
                    ExpandableCard(
                        title = "PV System",
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
                                Text("No solar data for this period",
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
                                text = "Best Cost/Year: €${df.format(costing.net / 100.0)}",
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
                        title = "Tariff Plan",
                        leadingIcon = {
                            Icon(painterResource(R.drawable.ic_baseline_euro_symbol_24), null,
                                Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
                        },
                        trailingContent = { _ ->
                            if (costing != null) {
                                Icon(painterResource(R.drawable.tick), null, Modifier.size(18.dp), tint = Color.Unspecified)
                            }
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
                                favouritePlanId = favouritePlanId
                            )
                        } else if (periodRows == null) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else if (periodRows.isEmpty()) {
                            Text("No simulation results yet")
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
                        title = "Explore data",
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
                                    Icon(painterResource(R.drawable.barchart), "View graphs",
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
                                "Results aren't ready yet — the breakdown appears here once the " +
                                    "simulation finishes calculating. See \"Needs attention\" above for what's outstanding.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            else -> {
                                if (pvMissing) {
                                    Text(
                                        "Solar data is still being generated, so these figures may be incomplete.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StatusAmber,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                SimulationPieCharts(kpis = kpis)
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
                        df = df
                    )

                    // 4. Visual overview
                    ExpandableCard(
                        title = "Visual overview",
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
                        title = "Usage Data",
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
                            Text("Source: ${lp.distributionSource}")
                            Text("Annual usage: ${"%.0f".format(lp.annualUsage)} kWh")
                            Spacer(Modifier.height(8.dp))
                            LoadDistributionCharts(lp = lp)
                        } else {
                            Text("No usage data linked")
                        }
                    }

                    // 6. Inverter
                    ExpandableCard(
                        title = "Inverter",
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
                        if (sc.inverters.isEmpty()) Text("No inverters configured")
                        else sc.inverters.forEach { inv ->
                            Text("${inv.inverterName}: max ${inv.maxInverterLoad} kW, ${inv.mpptCount} MPPT")
                        }
                    }

                    // 7. PV System
                    ExpandableCard(
                        title = "PV System",
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
                                            "Solar data ready", Modifier.size(18.dp), tint = StatusGreen)
                                    } else {
                                        // Cloud = panels exist but their solar data
                                        // hasn't been generated yet — a clearer "no sun
                                        // (yet)" signal than simply omitting the sun.
                                        Icon(painterResource(R.drawable.cloud),
                                            "Solar data not generated yet", Modifier.size(18.dp),
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
                        if (sc.panels.isEmpty()) Text("No panels configured")
                        else {
                            if (dashboardData?.hasPanelData != true) {
                                Text("Solar data hasn't been generated for these panels yet. " +
                                    "It's fetched automatically when the simulation runs.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusAmber,
                                    modifier = Modifier.padding(bottom = 6.dp))
                            }
                            sc.panels.forEach { p ->
                                Text("${p.panelName}: ${p.panelCount} × ${p.panelkWp}W, ${p.azimuth}° azimuth, ${p.slope}° slope")
                            }
                        }
                        if (panelSummary.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            PVSummaryBarChart(panelSummary = panelSummary, panels = sc.panels)
                        }
                    }

                    // 8. Battery
                    ExpandableCard(
                        title = "Battery",
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
                        if (sc.batteries.isEmpty()) Text("No batteries configured")
                        else sc.batteries.forEach { b ->
                            Text("${b.batterySize} kWh, stop at ${b.dischargeStop}%, charge ${b.maxCharge} kW / discharge ${b.maxDischarge} kW")
                        }
                    }

                    // 9. Hot Water
                    ExpandableCard(
                        title = "Hot Water",
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
                        if (hw == null) Text("No hot water system configured")
                        else {
                            Text("Tank: ${hw.hwCapacity} L, usage ${hw.hwUsage} L/day")
                            Text("Target: ${hw.hwTarget}°C, heater ${hw.hwRate} kW")
                        }
                    }

                    // 10. EV
                    ExpandableCard(
                        title = "EV",
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
                            Text("No EV configured")
                        } else {
                            if (sc.evCharges.isNotEmpty()) {
                                Text("Schedules",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline)
                                sc.evCharges.forEach { ev ->
                                    Text("${ev.name}: ${ev.begin}:00–${ev.end}:00 @ ${ev.draw} kW")
                                }
                            }
                            if (sc.evDiverts.isNotEmpty()) {
                                if (sc.evCharges.isNotEmpty()) Spacer(Modifier.height(6.dp))
                                Text("Solar Diverts",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline)
                                sc.evDiverts.forEach { d ->
                                    val activeTag = if (!d.isActive) "  ·  off" else ""
                                    Text("${d.name}: ${d.begin}:00–${d.end}:00  ·  ${d.dailyMax} kWh/day$activeTag")
                                }
                            }
                        }
                    }

                    // 8. (Tariff Plan moved to the top of the scenario list — pricing is
                    //     the headline answer once a scenario has been simulated.)
                }
            }
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
    val dayLabels   = remember { listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat") }
    val monthLabels = remember { listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec") }

    val charts = remember(lp) {
        listOf(
            Triple("Hourly (%)",   hourlyDist,  hourLabels),
            Triple("Daily (%)",    dailyDist,   dayLabels),
            Triple("Monthly (%)",  monthlyDist, monthLabels)
        )
    }

    val cfg = LocalConfiguration.current

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
        val size = (minOf(cfg.screenWidthDp, cfg.screenHeightDp) * 1.0f).dp
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
    val monthLabels = remember { listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec") }

    val grouped  = remember(panelSummary) { panelSummary.groupBy { it.panelID } }
    // Only show strings that belong to this scenario's panel configurations
    val scenarioPanelIds = remember(panels) { panels.map { it.panelIndex }.toSet() }
    val panelIds = remember(grouped, scenarioPanelIds) { grouped.keys.filter { it in scenarioPanelIds }.sorted() }

    val cfg = LocalConfiguration.current

    Text("PV Monthly Generation (kWh)", style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(bottom = 4.dp))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        panelIds.forEachIndexed { idx, panelId ->
            val name     = panels.firstOrNull { it.panelIndex == panelId }?.panelName ?: "Panel $panelId"
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
        val name     = panels.firstOrNull { it.panelIndex == panelId }?.panelName ?: "Panel $panelId"
        val monthMap = grouped[panelId]?.associate { (it.month.toIntOrNull() ?: 1) to it.tot } ?: emptyMap()
        val dist     = (1..12).map { m -> monthMap[m] ?: 0.0 }
        val size     = (minOf(cfg.screenWidthDp, cfg.screenHeightDp) * 1.0f).dp
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

@Composable
private fun AllCostingsTable(
    costings: List<Costings>,
    planStandingCharges: Map<Long, Double>,
    simDays: Long,
    df: DecimalFormat,
    favouritePlanId: Long? = null
) {
    var zoomedCosting by remember { mutableStateOf<Costings?>(null) }
    val cfg = LocalConfiguration.current

    // Header row
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("Plan", Modifier.weight(2.5f), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Net", Modifier.weight(1f), textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Buy", Modifier.weight(1f), textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Sell", Modifier.weight(1f), textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Fixed", Modifier.weight(1f), textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()

    costings.forEachIndexed { idx, c ->
        val fixed = (planStandingCharges[c.pricePlanID] ?: 0.0) * (simDays / 365.0)
        val isBest = idx == 0
        val isFav = favouritePlanId != null && favouritePlanId == c.pricePlanID
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { zoomedCosting = c }
                .background(
                    when {
                        isFav -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        isBest -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else -> Color.Transparent
                    }
                )
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isFav) {
                Icon(Icons.Default.Star, contentDescription = "Your current plan",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
            }
            Text(c.fullPlanName ?: "",
                Modifier.weight(if (isFav) 2.3f else 2.5f),
                style = MaterialTheme.typography.bodySmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(df.format(c.net / 100.0), Modifier.weight(1f), textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodySmall,
                color = if (isBest) MaterialTheme.colorScheme.primary else Color.Unspecified)
            Text(df.format(c.buy / 100.0), Modifier.weight(1f), textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodySmall)
            Text(df.format(c.sell / 100.0), Modifier.weight(1f), textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodySmall)
            Text(df.format(fixed), Modifier.weight(1f), textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
    }
    Spacer(Modifier.height(4.dp))
    Text("Tap a row to see tariff band breakdown  ↗",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)

    if (zoomedCosting != null) {
        val c = zoomedCosting!!
        val slices = remember(c) {
            val st = c.subTotals ?: return@remember emptyList<PieSlice>()
            st.prices.sortedBy { it }.mapIndexed { i, price ->
                val kwh = st.getSubTotalForPrice(price) ?: 0.0
                PieSlice("%.1fc".format(price), kwh, TARIFF_PIE_COLORS[i % TARIFF_PIE_COLORS.size])
            }.filter { it.value > 0 }
        }
        val size = (minOf(cfg.screenWidthDp, cfg.screenHeightDp) * 0.9f).dp
        Dialog(onDismissRequest = { zoomedCosting = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.size(size), shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(c.fullPlanName ?: "", style = MaterialTheme.typography.titleSmall,
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

// ─── Data source PV bar chart (period-aware) ──────────────────────────────

@Composable
private fun DataSourcePVBarChart(pvData: List<Pair<String, Double>>) {
    var zoomed by remember { mutableStateOf(false) }
    val cfg = LocalConfiguration.current
    val labelColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColorArgb  = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f).toArgb()
    val barColorArgb   = "#F44336".toColorInt()
    val labels = remember(pvData) { pvData.map { it.first } }
    val values = remember(pvData) { pvData.map { it.second } }
    val total  = remember(pvData) { pvData.sumOf { it.second } }
    val kwhDf  = remember { DecimalFormat("#,##0.0") }

    Column(modifier = Modifier.fillMaxWidth().clickable { zoomed = true }) {
        Text("Solar: ${kwhDf.format(total)} kWh  ↗", style = MaterialTheme.typography.labelSmall,
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
        val size = (minOf(cfg.screenWidthDp, cfg.screenHeightDp) * 1.0f).dp
        Dialog(onDismissRequest = { zoomed = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.size(size), shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                Column(modifier = Modifier.padding(12.dp).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Solar Generation (kWh)", style = MaterialTheme.typography.titleSmall)
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

    val isEsbn = importerType == ComparisonUIViewModel.Importer.ESBNHDF
    val charts = remember(periodTotals, isEsbn) {
        if (isEsbn) {
            listOf(
                "Import vs Export" to listOf(
                    PieSlice("Import", periodTotals.buy,  Color(0xFFF44336)),
                    PieSlice("Export", periodTotals.feed, Color(0xFF2196F3))
                )
            )
        } else {
            listOf(
                "Load Source" to listOf(
                    PieSlice("Solar",  maxOf(0.0, periodTotals.load - periodTotals.buy), Color(0xFF4CAF50)),
                    PieSlice("Bought", periodTotals.buy,  Color(0xFFF44336))
                ),
                "Self Consumption" to listOf(
                    PieSlice("PV Used",  maxOf(0.0, periodTotals.pv - periodTotals.feed), Color(0xFF4CAF50)),
                    PieSlice("Exported", periodTotals.feed, Color(0xFF2196F3))
                )
            )
        }
    }

    var zoomedChart by remember { mutableIntStateOf(-1) }
    val cfg = LocalConfiguration.current

    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        charts.forEachIndexed { idx, (title, slices) ->
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
        }
    }

    if (zoomedChart >= 0) {
        val (title, slices) = charts[zoomedChart]
        val visible = slices.filter { it.value > 0 }
        val size = (minOf(cfg.screenWidthDp, cfg.screenHeightDp) * 0.9f).dp
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
    if (costings.isEmpty()) {
        Text("No price plans configured",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    var zoomedRow by remember { mutableStateOf<DataSourceCostingRow?>(null) }
    val cfg = LocalConfiguration.current

    // Header
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("Plan",  Modifier.weight(2.5f), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Net",   Modifier.weight(1f), textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Buy",   Modifier.weight(1f), textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Sell",  Modifier.weight(1f), textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Fixed", Modifier.weight(1f), textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()

    costings.forEachIndexed { idx, row ->
        val isBest = idx == 0
        val isFav = favouritePlanId != null && favouritePlanId == row.pricePlanId
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { zoomedRow = row }
                .background(
                    when {
                        isFav -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        isBest -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else -> Color.Transparent
                    }
                )
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isFav) {
                Icon(Icons.Default.Star, contentDescription = "Your current plan",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
            }
            Text(row.planName, Modifier.weight(if (isFav) 2.3f else 2.5f),
                style = MaterialTheme.typography.bodySmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(df.format(row.net / 100.0), Modifier.weight(1f), textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodySmall,
                color = if (isBest) MaterialTheme.colorScheme.primary else Color.Unspecified)
            Text(df.format(row.buy / 100.0), Modifier.weight(1f), textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodySmall)
            Text(df.format(row.sell / 100.0), Modifier.weight(1f), textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodySmall)
            Text(df.format(row.fixed), Modifier.weight(1f), textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
    }
    Spacer(Modifier.height(4.dp))
    Text("Tap a row to see tariff band breakdown  ↗",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)

    if (zoomedRow != null) {
        val r = zoomedRow!!
        val slices = remember(r) {
            val st = r.subTotals ?: return@remember emptyList<PieSlice>()
            st.prices.sortedBy { it }.mapIndexed { i, price ->
                val kwh = st.getSubTotalForPrice(price) ?: 0.0
                PieSlice("%.1fc".format(price), kwh, TARIFF_PIE_COLORS[i % TARIFF_PIE_COLORS.size])
            }.filter { it.value > 0 }
        }
        val size = (minOf(cfg.screenWidthDp, cfg.screenHeightDp) * 0.9f).dp
        Dialog(onDismissRequest = { zoomedRow = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.size(size), shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(r.planName, style = MaterialTheme.typography.titleSmall,
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
    val dayLabels   = remember { listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat") }
    val monthLabels = remember { listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec") }

    val charts = remember(distribution) {
        listOf(
            Triple("Import Hourly (%)",   distribution.hourly,   hourLabels),
            Triple("Import Daily (%)",    distribution.daily,    dayLabels),
            Triple("Import Monthly (%)",  distribution.monthly,  monthLabels)
        )
    }

    val cfg = LocalConfiguration.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        charts.forEachIndexed { idx, (title, dist, labels) ->
            if (dist.any { it > 0 }) {
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
        val size = (minOf(cfg.screenWidthDp, cfg.screenHeightDp) * 1.0f).dp
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

    val charts = remember(kpis) {
        listOf(
            "Self Consumption" to listOf(
                PieSlice("PV Used", maxOf(0.0, kpis.generated - kpis.sold), Color(0xFF4CAF50)),
                PieSlice("Sold", kpis.sold, Color(0xFF2196F3))
            ),
            "Load Source" to listOf(
                PieSlice("Solar", maxOf(0.0, kpis.totalLoad - kpis.bought), Color(0xFF4CAF50)),
                PieSlice("Bought", kpis.bought, Color(0xFFF44336))
            ),
            "Solar Distribution" to listOf(
                PieSlice("To Load", kpis.pvToLoad, Color(0xFF4CAF50)),
                PieSlice("Battery", kpis.pvToCharge, Color(0xFF2196F3)),
                PieSlice("EV", kpis.evDiv, Color(0xFFFF9800)),
                PieSlice("Hot Water", kpis.h2oDiv, Color(0xFF9C27B0)),
                PieSlice("Sold", kpis.sold, Color(0xFF00BCD4))
            ),
            "Load Distribution" to listOf(
                PieSlice("House", kpis.house, Color(0xFF607D8B)),
                PieSlice("Hot Water", kpis.h20, Color(0xFF00BCD4)),
                PieSlice("EV", kpis.ev, Color(0xFFFF9800))
            )
        )
    }

    val cfg = LocalConfiguration.current

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
        val size = (minOf(cfg.screenWidthDp, cfg.screenHeightDp) * 0.9f).dp
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
                            contentDescription = "Edit",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand"
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

    val hasLoad = sc.loadProfile != null
    val hasHw = sc.hwSystem != null
    val hasEv = evCharges.isNotEmpty() || evDiverts.isNotEmpty()
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
                if (hasEv)   Unit else null
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
            label = inverters.firstOrNull()?.inverterName ?: "(no inverter)",
            subline = if (hasInverter)
                "${"%.1f".format(inverters.first().maxInverterLoad)} kW · ${inverters.first().mpptCount} MPPT"
            else null,
            iconRes = R.drawable.inverter,
            highlight = true,
            modifier = Modifier
                .width(invW).height(invH)
                .offset(x = invLeft, y = invTop)
        )
        if (inverters.size > 1) {
            Text("+${inverters.size - 1} more",
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
                    badge = "MPPT ${p.mppt}",
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
            data class C(val label: String, val sub: String?, val icon: Int, val deco: List<Int>)
            val consumers = mutableListOf<C>()
            if (hasLoad) consumers += C(
                "House",
                sc.loadProfile?.annualUsage?.let { "${"%.0f".format(it)} kWh/y" },
                R.drawable.house,
                emptyList()
            )
            if (hasHw) consumers += C(
                "Hot water",
                sc.hwSystem?.let { "${it.hwCapacity} L · ${it.hwRate} kW" },
                R.drawable.waterwarm,
                listOfNotNull(
                    if (hwSchedules.isNotEmpty()) R.drawable.ic_baseline_access_time_24 else null,
                    if (hwDivertActive) R.drawable.ic_baseline_call_split_24 else null
                )
            )
            if (hasEv) consumers += C(
                "EV",
                if (evCharges.isNotEmpty()) "${evCharges.size} sched" else null,
                R.drawable.ev_on,
                listOfNotNull(
                    if (evCharges.isNotEmpty()) R.drawable.ic_baseline_access_time_24 else null,
                    if (evDiverts.isNotEmpty()) R.drawable.ic_baseline_call_split_24 else null
                )
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
                    modifier = Modifier
                        .width(cardW).height(cardH)
                        .offset(x = leftX, y = yCenter - cardH / 2)
                )
            }
        }

        // Grid (right)
        TopologyNode(
            label = "Grid",
            subline = "Import / Export",
            iconRes = R.drawable.baseline_file_upload_24,
            iconTinted = true,
            modifier = Modifier
                .width(cardW).height(cardH)
                .offset(x = rightX, y = cy - cardH / 2)
        )

        // Empty state
        if (!hasInverter && panels.isEmpty() && batteries.isEmpty() && !hasLoad && !hasHw && !hasEv) {
            Text(
                "Nothing to draw yet — configure components in the wizard.",
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
    decorationIcons: List<Int> = emptyList()
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
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (iconTinted) textColor else Color.Unspecified
            )
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
        LegendItem(R.drawable.ic_baseline_access_time_24, "Schedule")
        LegendItem(R.drawable.baseline_file_upload_24, "Discharge")
        LegendItem(R.drawable.ic_baseline_call_split_24, "Divert")
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
// + per-month best/worst/average). The range picker drives the summary; the
// 12-button J/F/M/A/… row filters the monthly table.
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
    df: DecimalFormat
) {
    ExpandableCard(
        title = "KPIs",
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

        // Month filter: 13 buttons — ALL + J F M A M J J A S O N D.
        MonthFilterRow(monthFilter, onMonthFilterChange)
        Spacer(Modifier.height(8.dp))

        // KPI summary table — same five rows the legacy fragment shows.
        when (summary) {
            null -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else -> KpiSummaryTable(summary, df)
        }

        // Monthly key stats — filtered by the chip row above.
        Spacer(Modifier.height(10.dp))
        when (months) {
            null -> {}
            else -> {
                val filtered = if (monthFilter == 0) months
                               else months.filter { it.monthNumber == monthFilter }
                KpiMonthsTable(filtered, df)
            }
        }
    }
}

@Composable
private fun MonthFilterRow(selected: Int, onChange: (Int) -> Unit) {
    val labels = listOf("*", "J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        labels.forEachIndexed { idx, label ->
            val isOn = idx == selected
            Surface(
                color = if (isOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f).height(32.dp)
                    .clickable { onChange(idx) }
            ) {
                Box(contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()) {
                    Text(label, style = MaterialTheme.typography.labelMedium,
                        color = if (isOn) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun KpiSummaryTable(summary: KpiSummary, df: DecimalFormat) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        KpiRow("Self consumption",  "(PV − Feed) / PV", df.format(summary.selfConsumption) + "%")
        KpiRow("Self sufficiency",  "(PV − Feed) / Load", df.format(summary.selfSufficiency) + "%")
        KpiRow("Max self sufficiency", "PV / Load",      df.format(summary.maxSelfSufficiency) + "%")
        KpiRow("Generation (kWh)",  "PV",   df.format(summary.pv))
        KpiRow("Feed (kWh)",        "Feed", df.format(summary.feed))
    }
}

@Composable
private fun KpiRow(label: String, sub: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(sub, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun KpiMonthsTable(rows: List<KpiMonthRow>, df: DecimalFormat) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text("YY-MM",   style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text("PV Tot",  style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text("Best",    style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.4f))
            Text("Worst",   style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.4f))
            Text("Avg",     style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        }
        if (rows.isEmpty()) {
            Text("No data for the selected filter.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp))
        } else rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(row.monthLabel,  style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(df.format(row.pvTotal), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                // best/worst arrive as "<value> on <dd>" — keep the DB string intact so
                // the user sees which day produced it (legacy KPI fragment behaviour).
                Text(row.best,    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.4f))
                Text(row.worst,   style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.4f))
                Text(df.format(row.average), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        }
    }
}
