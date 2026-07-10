@file:Suppress("AssignedValueIsNeverRead")

package com.tfcode.comparetout.ui2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.tfcode.comparetout.MainActivity
import com.tfcode.comparetout.R
import com.tfcode.comparetout.TOUTCApplication
import com.tfcode.comparetout.dynamic.strategy.DispatchStrategy
import com.tfcode.comparetout.dynamic.strategy.RankNStrategy
import com.tfcode.comparetout.dynamic.strategy.ThresholdStrategy
import com.tfcode.comparetout.region.RegionProfiles
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.DecimalFormat

@AndroidEntryPoint
class UI2SimulationsFragment : Fragment() {

    private val viewModel: UI2SimulationsViewModel by viewModels()
    private val sharedViewModel: UI2SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("UI2", "UI2SimulationsFragment.onCreateView")
        val onSwitchLegacy: () -> Unit = {
            CoroutineScope(Dispatchers.IO).launch {
                val app = requireActivity().application as TOUTCApplication
                app.putStringValueIntoDataStore("use_ui2", "false")
                val intent = Intent(requireContext(), MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                requireActivity().startActivity(intent)
            }
        }
        val onSimulationView: (Long) -> Unit = { scenarioId ->
            Log.d("UI2", "View simulation: scenarioId=$scenarioId")
            sharedViewModel.setActiveSimulationId(scenarioId)
            findNavController().navigate(R.id.ui2DashboardFragment)
        }
        val onDataSourceView: (UI2SimulationsViewModel.SimListItem.DataSource) -> Unit = { ds ->
            Log.d("UI2", "View data source: sysSn=${ds.sysSn} type=${ds.importerType}")
            sharedViewModel.setActiveDataSource(ds.sysSn, ds.importerType, ds.startDate, ds.finishDate)
            findNavController().navigate(R.id.ui2DashboardFragment)
        }
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                UI2Theme {
                    ScenariosScreen(
                        viewModel = viewModel,
                        onSimulationView = onSimulationView,
                        onDataSourceView = onDataSourceView,
                        onSwitchLegacy = onSwitchLegacy
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScenariosScreen(
    viewModel: UI2SimulationsViewModel,
    onSimulationView: (Long) -> Unit,
    onDataSourceView: (UI2SimulationsViewModel.SimListItem.DataSource) -> Unit,
    onSwitchLegacy: () -> Unit
) {
    val items by viewModel.items.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf<UI2SimulationsViewModel.SimListItem.Simulation?>(null) }
    var strategyDialogFor by remember { mutableStateOf<UI2SimulationsViewModel.SimListItem.Simulation?>(null) }
    var showDeleteAll by remember { mutableStateOf(false) }
    var showDrawer by remember { mutableStateOf(false) }
    val (showHints, toggleShowHints) = rememberShowHints()
    val shareScope = rememberCoroutineScope()

    val simItems = remember(items) { items.filterIsInstance<UI2SimulationsViewModel.SimListItem.Simulation>() }
    // Source-visibility gating (App settings): hidden sources drop out of the
    // list — their imported data stays put.
    val uiVis = rememberUiVisibility()
    val dataSourceItems = remember(items, uiVis) {
        items.filterIsInstance<UI2SimulationsViewModel.SimListItem.DataSource>()
            .filter {
                when (it.importerType) {
                    com.tfcode.comparetout.ComparisonUIViewModel.Importer.ALPHAESS -> uiVis.alphaess
                    com.tfcode.comparetout.ComparisonUIViewModel.Importer.HOME_ASSISTANT -> uiVis.homeassistant
                    com.tfcode.comparetout.ComparisonUIViewModel.Importer.ESBNHDF -> uiVis.esbn
                    com.tfcode.comparetout.ComparisonUIViewModel.Importer.OCTOPUS -> uiVis.octopus
                    com.tfcode.comparetout.ComparisonUIViewModel.Importer.SOLIS -> uiVis.solis
                    else -> true
                }
            }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .nestedScroll(rememberNestedScrollInteropConnection())
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ui2_scenarios_title)) },
                actions = {
                    IconButton(onClick = { showDrawer = true }) {
                        Icon(Icons.Default.Menu,
                            contentDescription = stringResource(R.string.ui2_menu))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                // Scenarios section is always shown — the title's "+ Create new" action
                // is the only entry point for building a scenario (no FAB any more).
                stickyHeader(key = "header_simulations") {
                    SectionHeader(
                        "Scenarios",
                        action = SectionAction("+ Create new") {
                            context.startActivity(Intent(context, UI2WizardActivity::class.java))
                        },
                        onDeleteAll = if (simItems.isNotEmpty()) ({ showDeleteAll = true }) else null
                    )
                }
                if (showHints) {
                    item(key = "hint_simulations") {
                        SectionHint(
                            "Each scenario is a what-if home setup (solar, battery, EV, hot water). " +
                                "Tap “+ Create new” to build one; the dashboard shows its costs."
                        )
                    }
                }
                if (simItems.isEmpty()) {
                    item(key = "empty_simulations") { EmptySectionRow("No scenarios yet — tap “+ Create new”.") }
                } else {
                    items(simItems, key = { it.scenario.scenarioIndex }) { item ->
                        SimulationCard(
                            item = item,
                            onView = { onSimulationView(item.scenario.scenarioIndex) },
                            onDelete = { showDeleteDialog = item },
                            onEdit = {
                                context.startActivity(
                                    Intent(context, UI2WizardActivity::class.java)
                                        .putExtra("ScenarioID", item.scenario.scenarioIndex)
                                )
                            },
                            onShare = {
                                shareScope.launch {
                                    val json = viewModel.buildScenarioJson(item.scenario.scenarioIndex)
                                    if (!json.isNullOrEmpty()) {
                                        context.shareText(
                                            payload = json,
                                            format = ShareFormat.JSON,
                                            subject = "Scenario: ${item.scenario.scenarioName}"
                                        )
                                    }
                                }
                            },
                            // Strategy generation needs a battery to dispatch; the menu
                            // item is hidden (not greyed) for battery-less scenarios.
                            onGenerateStrategy = if (item.scenario.isHasBatteries) {
                                { strategyDialogFor = item }
                            } else null
                        )
                    }
                }

                // Sources are imported via the legacy data-source management screens,
                // so this section is read-only here — no add action.
                stickyHeader(key = "header_datasources") {
                    SectionHeader("Sources")
                }
                if (showHints) {
                    item(key = "hint_datasources") {
                        SectionHint(
                            "Sources are real meter/inverter data (AlphaESS, ESBN HDF, Home " +
                                "Assistant). See the menu → Data Source Management to add or remove them."
                        )
                    }
                }
                if (dataSourceItems.isEmpty()) {
                    item(key = "empty_datasources") {
                        EmptySectionRow("No data sources imported. Import via Menu → Data Source Management.")
                    }
                } else {
                    items(dataSourceItems, key = { it.sysSn }) { item ->
                        DataSourceCard(
                            item = item,
                            onView = { onDataSourceView(item) }
                        )
                    }
                }
            }

            // Scrim
            AnimatedVisibility(
                visible = showDrawer,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { showDrawer = false }
                )
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
        }
    }

    strategyDialogFor?.let { sim ->
        StrategyGenerateDialog(
            viewModel = viewModel,
            scenarioId = sim.scenario.scenarioIndex,
            scenarioName = sim.scenario.scenarioName,
            onDismiss = { strategyDialogFor = null }
        )
    }

    showDeleteDialog?.let { sim ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.ui2_scenarios_delete_sim_title)) },
            text = { Text(stringResource(R.string.ui2_scenarios_delete_sim_body,
                sim.scenario.scenarioName)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteSimulation(sim.scenario.scenarioIndex.toInt())
                    showDeleteDialog = null
                }) { Text(stringResource(R.string.ui2_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            title = { Text(stringResource(R.string.ui2_scenarios_delete_all_title)) },
            text = {
                Text(pluralStringResource(R.plurals.ui2_scenarios_delete_all_body,
                    simItems.size, simItems.size))
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllScenarios()
                        showDeleteAll = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.ui2_scenarios_delete_all)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAll = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

/** Optional trailing action rendered on the right of a [SectionHeader]. */
private data class SectionAction(val label: String, val onClick: () -> Unit)

@Composable
private fun SectionHeader(
    title: String,
    action: SectionAction? = null,
    onDeleteAll: (() -> Unit)? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onDeleteAll != null) {
                IconButton(onClick = onDeleteAll) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.ui2_scenarios_delete_all_cd),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (action != null) {
                TextButton(onClick = action.onClick) {
                    Text(action.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun SectionHint(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun EmptySectionRow(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SimulationCard(
    item: UI2SimulationsViewModel.SimListItem.Simulation,
    onView: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onGenerateStrategy: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val scenario = item.scenario
    val costFmt = remember { DecimalFormat("#,##0") }

    ListItem(
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.barchart),
                contentDescription = stringResource(R.string.ui2_scenarios_cd_simulation),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        headlineContent = {
            Text(scenario.scenarioName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (scenario.isHasPanels) ComponentBadge("Solar", Color(0xFFF44336))
                    if (scenario.isHasBatteries) ComponentBadge("Battery", Color(0xFF4CAF50))
                    if (scenario.isHasEVCharges) ComponentBadge("EV", Color(0xFFFF9800))
                    if (scenario.isHasHWSystem) ComponentBadge("HW", Color(0xFF9C27B0))
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.bestCostPerYear?.let { cost ->
                    Text(
                        "${RegionProfiles.current.currencySymbol}${costFmt.format(cost)}/yr",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.ui2_options))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui2_view)) },
                            leadingIcon = { Icon(Icons.Default.Visibility, null) },
                            onClick = { menuExpanded = false; onView() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui2_edit)) },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menuExpanded = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui2_share)) },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = { menuExpanded = false; onShare() }
                        )
                        if (onGenerateStrategy != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui2_strategy_menu)) },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                                onClick = { menuExpanded = false; onGenerateStrategy() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui2_delete)) },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
        },
        modifier = Modifier.clickable { onView() }
    )
    HorizontalDivider()
}

/**
 * Parameter sheet for a generated strategy scenario: pick a materialised
 * dynamic plan, a strategy (Threshold or Rank-N) and its numbers, then hand
 * off to [UI2SimulationsViewModel.generateStrategyScenario]. The result
 * arrives as a toast; the new scenario appears in the list on its own via
 * the LiveData refresh.
 */
@Composable
private fun StrategyGenerateDialog(
    viewModel: UI2SimulationsViewModel,
    scenarioId: Long,
    scenarioName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var plans by remember { mutableStateOf<List<UI2SimulationsViewModel.DynamicPlanOption>?>(null) }
    var selectedPlan by remember { mutableStateOf<UI2SimulationsViewModel.DynamicPlanOption?>(null) }
    var useRankN by remember { mutableStateOf(false) }
    var chargeBelow by remember { mutableStateOf("12") }
    var dischargeAbove by remember { mutableStateOf("25") }
    var minSpread by remember { mutableStateOf("2") }
    var chargeSlots by remember { mutableStateOf("6") }
    var dischargeSlots by remember { mutableStateOf("4") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loaded = viewModel.materialisedDynamicPlans()
        plans = loaded
        selectedPlan = loaded.firstOrNull()
    }

    val ready = !busy && selectedPlan != null && minSpread.toDoubleOrNull() != null &&
        if (useRankN) {
            chargeSlots.toIntOrNull()?.let { it in 1..48 } == true &&
                dischargeSlots.toIntOrNull()?.let { it in 0..48 } == true
        } else {
            chargeBelow.toDoubleOrNull() != null && dischargeAbove.toDoubleOrNull() != null
        }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.ui2_strategy_dialog_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    stringResource(R.string.ui2_strategy_dialog_body, scenarioName),
                    style = MaterialTheme.typography.bodySmall
                )
                val loadedPlans = plans
                when {
                    loadedPlans == null -> Text("…")
                    loadedPlans.isEmpty() -> Text(
                        stringResource(R.string.ui2_strategy_no_plans),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    else -> {
                        Text(
                            stringResource(R.string.ui2_strategy_plan_label),
                            style = MaterialTheme.typography.labelMedium
                        )
                        loadedPlans.forEach { option ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPlan = option }
                            ) {
                                RadioButton(
                                    selected = selectedPlan == option,
                                    onClick = { selectedPlan = option }
                                )
                                Text(
                                    option.label + (option.year?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !useRankN,
                                onClick = { useRankN = false },
                                label = { Text(stringResource(R.string.ui2_strategy_threshold)) }
                            )
                            FilterChip(
                                selected = useRankN,
                                onClick = { useRankN = true },
                                label = { Text(stringResource(R.string.ui2_strategy_rankn)) }
                            )
                        }
                        if (useRankN) {
                            OutlinedTextField(
                                value = chargeSlots, onValueChange = { chargeSlots = it },
                                label = { Text(stringResource(R.string.ui2_strategy_charge_slots)) },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = dischargeSlots, onValueChange = { dischargeSlots = it },
                                label = { Text(stringResource(R.string.ui2_strategy_discharge_slots)) },
                                singleLine = true
                            )
                        } else {
                            OutlinedTextField(
                                value = chargeBelow, onValueChange = { chargeBelow = it },
                                label = { Text(stringResource(R.string.ui2_strategy_charge_below)) },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = dischargeAbove, onValueChange = { dischargeAbove = it },
                                label = { Text(stringResource(R.string.ui2_strategy_discharge_above)) },
                                singleLine = true
                            )
                        }
                        OutlinedTextField(
                            value = minSpread, onValueChange = { minSpread = it },
                            label = { Text(stringResource(R.string.ui2_strategy_min_spread)) },
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = ready,
                onClick = {
                    val strategy: DispatchStrategy = if (useRankN) {
                        RankNStrategy(chargeSlots.toInt(), dischargeSlots.toInt(),
                            minSpread.toDouble())
                    } else {
                        ThresholdStrategy(chargeBelow.toDouble(), dischargeAbove.toDouble(),
                            minSpread.toDouble())
                    }
                    busy = true
                    viewModel.generateStrategyScenario(
                        scenarioId, selectedPlan!!.id, strategy
                    ) { result ->
                        val message = when (result) {
                            is StrategyScenarioGenerator.Result.Generated ->
                                context.getString(R.string.ui2_strategy_generated,
                                    result.scenarioName, result.chargeRows, result.dischargeRows)
                            is StrategyScenarioGenerator.Result.Failed ->
                                context.getString(R.string.ui2_strategy_failed, result.reason)
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        onDismiss()
                    }
                }
            ) { Text(stringResource(R.string.ui2_strategy_generate)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
private fun DataSourceCard(
    item: UI2SimulationsViewModel.SimListItem.DataSource,
    onView: () -> Unit
) {
    ListItem(
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_download_24),
                contentDescription = stringResource(R.string.ui2_scenarios_cd_data_source),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
        },
        headlineContent = {
            Text(item.sysSn, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                "${item.displayTypeName}  ·  ${item.startDate} → ${item.finishDate}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            TextButton(onClick = onView) {
                Icon(Icons.Default.Visibility, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.ui2_view))
            }
        },
        modifier = Modifier.clickable { onView() }
    )
    HorizontalDivider()
}

@Composable
private fun ComponentBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
