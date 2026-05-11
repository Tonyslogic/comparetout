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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
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
    var showDrawer by remember { mutableStateOf(false) }
    val df = remember { DecimalFormat("#,##0.00") }

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
                    Text("Type: ${dsInfo.displayTypeName}")
                    Text("Date range: ${dsInfo.startDate} – ${dsInfo.endDate}")
                }

                ExpandableCard(
                    title = "Usage Data",
                    leadingIcon = {
                        Icon(painterResource(R.drawable.house), null, Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface)
                    },
                    trailingContent = { _ ->
                        Icon(painterResource(R.drawable.tick), null, Modifier.size(18.dp), tint = Color.Unspecified)
                    },
                    showEdit = false
                ) {
                    Text("Source: ${dsInfo.sysSn}")
                    Text("Type: ${dsInfo.displayTypeName}")
                }

                ExpandableCard(
                    title = "Tariff Plan",
                    leadingIcon = {
                        Icon(painterResource(R.drawable.ic_baseline_euro_symbol_24), null,
                            Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
                    },
                    showEdit = false
                ) {
                    Text("No tariff plan linked to this data source")
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

                    // 1. Simulation
                    ExpandableCard(
                        title = "Simulation",
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
                        }
                    ) {
                        val lp = sc.loadProfile
                        if (lp != null) {
                            Text("Source: ${lp.distributionSource}")
                            Text("Annual usage: ${"%.0f".format(lp.annualUsage)} kWh")
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
                                if (dashboardData?.hasPanelData == true) {
                                    Icon(painterResource(R.drawable.ic_baseline_wb_sunny_36), null,
                                        Modifier.size(18.dp), tint = StatusGreen)
                                }
                                if (sc.panels.isNotEmpty()) {
                                    Icon(painterResource(R.drawable.tick), null, Modifier.size(18.dp), tint = Color.Unspecified)
                                }
                            }
                        }
                    ) {
                        if (sc.panels.isEmpty()) Text("No panels configured")
                        else sc.panels.forEach { p ->
                            Text("${p.panelName}: ${p.panelCount} × ${p.panelkWp}W, ${p.azimuth}° azimuth, ${p.slope}° slope")
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
                            val res = if (sc.evCharges.isNotEmpty()) R.drawable.ev_on else R.drawable.ev_off
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
                        }
                    ) {
                        if (sc.evCharges.isEmpty()) Text("No EV charging configured")
                        else sc.evCharges.forEach { ev ->
                            Text("${ev.name}: ${ev.begin}:00–${ev.end}:00 @ ${ev.draw} kW")
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
                        if (costing != null) {
                            Text("Plan: ${costing.fullPlanName}")
                            Text("Buy: €${df.format(costing.buy / 100.0)}")
                            Text("Sell: €${df.format(costing.sell / 100.0)}")
                            Text("Net: €${df.format(costing.net / 100.0)}")
                        } else {
                            Text("No simulation results yet")
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
                    DrawerContent(
                        onSwitchLegacy = { showDrawer = false; onSwitchLegacy() },
                        onClose = { showDrawer = false }
                    )
                }
            }
        }   // end Box
    }
}

@Composable
private fun DrawerContent(onSwitchLegacy: () -> Unit, onClose: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Menu", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
        }
        HorizontalDivider()
        DrawerItem(R.drawable.ic_baseline_euro_symbol_24,     "Supplier Plans",         onClose)
        DrawerItem(R.drawable.ic_baseline_settings_24,        "Units",                  onClose)
        DrawerItem(R.drawable.ic_baseline_access_time_24,     "Timezone",               onClose)
        DrawerItem(R.drawable.ic_baseline_call_split_24,      "Data Source Management", onClose)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        DrawerItem(R.drawable.ic_baseline_settings_24,        "Switch to Legacy UI",    onSwitchLegacy)
    }
}

@Composable
private fun DrawerItem(iconRes: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(iconRes), null, Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

data class PieSlice(val label: String, val value: Double, val color: Color)

@Composable
fun PieChart(slices: List<PieSlice>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val total = slices.sumOf { it.value }.toFloat()
        if (total <= 0f) return@Canvas
        var startAngle = -90f
        slices.forEach { slice ->
            val sweep = (slice.value.toFloat() / total) * 360f
            drawArc(color = slice.color, startAngle = startAngle, sweepAngle = sweep, useCenter = true)
            startAngle += sweep
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

    if (zoomedChart >= 0) {
        val (title, slices) = charts[zoomedChart]
        val visible = slices.filter { it.value > 0 }
        val cfg = LocalConfiguration.current
        val pieSize = minOf(cfg.screenWidthDp, cfg.screenHeightDp).dp * 0.9f
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { zoomedChart = -1 }
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            PieChart(slices = visible, modifier = Modifier.size(pieSize))
            PieLegend(slices = visible)
        }
    } else {
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
    }
}

@Composable
fun ExpandableCard(
    title: String,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable (expanded: Boolean) -> Unit)? = null,
    showEdit: Boolean = true,
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
                if (expanded && showEdit) {
                    Box(
                        modifier = Modifier
                            .clickable { /* edit placeholder */ }
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
