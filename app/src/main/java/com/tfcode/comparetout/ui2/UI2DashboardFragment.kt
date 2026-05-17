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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.model.costings.Costings
import com.tfcode.comparetout.model.scenario.LoadProfile
import com.tfcode.comparetout.model.scenario.Panel
import com.tfcode.comparetout.model.scenario.PanelPVSummary
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tfcode.comparetout.MainActivity
import com.tfcode.comparetout.R
import com.tfcode.comparetout.TOUTCApplication
import com.tfcode.comparetout.model.scenario.SimKPIs
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val StatusGreen = Color(0xFF0B8043)
private val StatusRed = Color(0xFFD32F2F)

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
    var showDrawer by remember { mutableStateOf(false) }
    val df = remember { DecimalFormat("#,##0.00") }
    val kwhDf = remember { DecimalFormat("#,##0.0") }

    SideEffect {
        Log.d("UI2", "DashboardScreen recompose: scenarioName=${dashboardData?.scenarioComponents?.scenario?.scenarioName ?: "null"}")
    }

    Scaffold { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showDrawer = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                    val title = dashboardData?.dataSourceInfo?.run { "$sysSn  ·  $displayTypeName" }
                        ?: dashboardData?.scenarioComponents?.scenario?.scenarioName
                        ?: "Select a Simulation"
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    )
                }

            val pvPeriod    by viewModel.pvPeriod.observeAsState(DataSourcePeriod.ALL)
    val pvAnchor    by viewModel.pvAnchor.observeAsState(LocalDate.now())
    val pvChartData by viewModel.pvChartData.observeAsState(null)

            val dsInfo = dashboardData?.dataSourceInfo
            if (dsInfo != null) {
                // ── Data source mode ──────────────────────────────────────────
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
                    PeriodNavigator(
                        selectedPeriod   = explorePeriod,
                        anchorDate       = exploreAnchor,
                        dataStart        = dsInfo.startDate,
                        dataEnd          = dsInfo.endDate,
                        onPeriodSelected = { viewModel.setExplorePeriod(it) },
                        onNavigate       = { viewModel.navigateExplore(it) }
                    )
                    Spacer(Modifier.height(8.dp))
                    DataSourceExplorePies(
                        importerType = dsInfo.importerType,
                        periodTotals = exploreTotals
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
                    PeriodNavigator(
                        selectedPeriod   = usagePeriod,
                        anchorDate       = usageAnchor,
                        dataStart        = dsInfo.startDate,
                        dataEnd          = dsInfo.endDate,
                        onPeriodSelected = { viewModel.setUsagePeriod(it) },
                        onNavigate       = { viewModel.navigateUsage(it) }
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
                        PeriodNavigator(
                            selectedPeriod   = pvPeriod,
                            anchorDate       = pvAnchor,
                            dataStart        = dsInfo.startDate,
                            dataEnd          = dsInfo.endDate,
                            onPeriodSelected = { viewModel.setPvPeriod(it) },
                            onNavigate       = { viewModel.navigatePv(it) }
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
                    PeriodNavigator(
                        selectedPeriod   = tariffPeriod,
                        anchorDate       = tariffAnchor,
                        dataStart        = dsInfo.startDate,
                        dataEnd          = dsInfo.endDate,
                        onPeriodSelected = { viewModel.setTariffPeriod(it) },
                        onNavigate       = { viewModel.navigateTariff(it) }
                    )
                    Spacer(Modifier.height(8.dp))
                    DataSourceCostingsTable(
                        costings = tariffCostings,
                        df       = df
                    )
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

                    // 1. Explore data
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
                        if (kpis == null) Text("No simulation results yet")
                        else SimulationPieCharts(kpis = kpis)
                    }

                    // 2. Usage Data
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

                    // 3. Inverter
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
                        }
                    ) {
                        if (sc.inverters.isEmpty()) Text("No inverters configured")
                        else sc.inverters.forEach { inv ->
                            Text("${inv.inverterName}: max ${inv.maxInverterLoad} kW, ${inv.mpptCount} MPPT")
                        }
                    }

                    // 4. PV System
                    ExpandableCard(
                        title = "PV System",
                        leadingIcon = {
                            Icon(painterResource(R.drawable.solarpanel), null, Modifier.size(24.dp), tint = Color.Unspecified)
                        },
                        trailingContent = { _ ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (sc.panels.isNotEmpty() && dashboardData?.hasPanelData == true) {
                                    Icon(painterResource(R.drawable.ic_baseline_wb_sunny_36), null,
                                        Modifier.size(18.dp), tint = StatusGreen)
                                }
                                if (sc.panels.isNotEmpty()) {
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
                        else sc.panels.forEach { p ->
                            Text("${p.panelName}: ${p.panelCount} × ${p.panelkWp}W, ${p.azimuth}° azimuth, ${p.slope}° slope")
                        }
                        if (panelSummary.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            PVSummaryBarChart(panelSummary = panelSummary, panels = sc.panels)
                        }
                    }

                    // 5. Battery
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
                        }
                    ) {
                        if (sc.batteries.isEmpty()) Text("No batteries configured")
                        else sc.batteries.forEach { b ->
                            Text("${b.batterySize} kWh, stop at ${b.dischargeStop}%, charge ${b.maxCharge} kW / discharge ${b.maxDischarge} kW")
                        }
                    }

                    // 6. Hot Water
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
                        }
                    ) {
                        val hw = sc.hwSystem
                        if (hw == null) Text("No hot water system configured")
                        else {
                            Text("Tank: ${hw.hwCapacity} L, usage ${hw.hwUsage} L/day")
                            Text("Target: ${hw.hwTarget}°C, heater ${hw.hwRate} kW")
                        }
                    }

                    // 7. EV
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

                    // 8. Tariff Plan
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
                        val allCostings = dashboardData?.allCostings ?: emptyList()
                        if (allCostings.isEmpty()) {
                            Text("No simulation results yet")
                        } else {
                            AllCostingsTable(
                                costings = allCostings,
                                planStandingCharges = dashboardData?.planStandingCharges ?: emptyMap(),
                                simDays = dashboardData?.simDays ?: 365L,
                                df = df
                            )
                        }
                    }
                }
            }
        }   // end Column

            // Scrim
            AnimatedVisibility(visible = showDrawer, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
                Box(modifier = Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { showDrawer = false })
            }

            // Left drawer
            AnimatedVisibility(
                visible = showDrawer,
                enter = slideInHorizontally(tween(220)) { -it },
                exit = slideOutHorizontally(tween(220)) { -it },
                modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().width(280.dp)
            ) {
                Surface(tonalElevation = 8.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxSize()) {
                    UI2DrawerContent(
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
    var zoomedIdx by remember { mutableStateOf(-1) }

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
    var zoomedIdx by remember { mutableStateOf(-1) }
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
    val barColorArgb   = android.graphics.Color.parseColor("#F44336")

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
    df: DecimalFormat
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { zoomedCosting = c }
                .background(
                    if (isBest) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else Color.Transparent
                )
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(c.fullPlanName ?: "", Modifier.weight(2.5f),
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
            st.getPrices().sortedBy { it }.mapIndexed { i, price ->
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
    val barColorArgb   = android.graphics.Color.parseColor("#F44336")
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

// ─── Data source period navigation + content ──────────────────────────────

@Composable
private fun PeriodNavigator(
    selectedPeriod: DataSourcePeriod,
    anchorDate: LocalDate,
    dataStart: String,
    dataEnd: String,
    onPeriodSelected: (DataSourcePeriod) -> Unit,
    onNavigate: (forward: Boolean) -> Unit
) {
    val dataStartDate = remember(dataStart) { LocalDate.parse(dataStart) }
    val dataEndDate   = remember(dataEnd)   { LocalDate.parse(dataEnd) }
    val showNav = selectedPeriod != DataSourcePeriod.ALL
    val dateLabel = if (showNav) when (selectedPeriod) {
        DataSourcePeriod.YESTERDAY -> anchorDate.format(DateTimeFormatter.ofPattern("dd/MM/yy"))
        DataSourcePeriod.MONTH     -> anchorDate.format(DateTimeFormatter.ofPattern("MMM yy"))
        DataSourcePeriod.YEAR      -> anchorDate.year.toString()
        else -> ""
    } else ""
    val atStart = showNav && anchorDate <= dataStartDate
    val atEnd   = showNav && anchorDate >= dataEndDate

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        DataSourcePeriod.values().forEach { period ->
            FilterChip(
                selected = period == selectedPeriod,
                onClick = { onPeriodSelected(period) },
                label = { Text(period.label, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(28.dp)
            )
            Spacer(Modifier.width(2.dp))
        }
        if (showNav) {
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = { onNavigate(false) },
                enabled = !atStart,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowLeft, null, Modifier.size(18.dp))
            }
            Text(dateLabel, Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall)
            IconButton(
                onClick = { onNavigate(true) },
                enabled = !atEnd,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowRight, null, Modifier.size(18.dp))
            }
        }
    }
}

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

    var zoomedChart by remember { mutableStateOf(-1) }
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
    df: DecimalFormat
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { zoomedRow = row }
                .background(
                    if (isBest) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else Color.Transparent
                )
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(row.planName, Modifier.weight(2.5f),
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
            st.getPrices().sortedBy { it }.mapIndexed { i, price ->
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
    var zoomedIdx by remember { mutableStateOf(-1) }

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
    var zoomedChart by remember { mutableStateOf(-1) }

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
    onEdit: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

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
