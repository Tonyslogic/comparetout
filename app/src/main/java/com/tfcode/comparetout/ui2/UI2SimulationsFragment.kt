package com.tfcode.comparetout.ui2

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.tfcode.comparetout.MainActivity
import com.tfcode.comparetout.R
import com.tfcode.comparetout.TOUTCApplication
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
    var showDrawer by remember { mutableStateOf(false) }
    val (showHints, toggleShowHints) = rememberShowHints()

    val simItems = remember(items) { items.filterIsInstance<UI2SimulationsViewModel.SimListItem.Simulation>() }
    val dataSourceItems = remember(items) { items.filterIsInstance<UI2SimulationsViewModel.SimListItem.DataSource>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scenarios") },
                actions = {
                    IconButton(onClick = { showDrawer = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                context.startActivity(Intent(context, UI2WizardActivity::class.java))
            }) {
                Icon(Icons.Default.Add, contentDescription = "New simulation")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn {
                if (simItems.isNotEmpty()) {
                    stickyHeader(key = "header_simulations") {
                        SectionHeader("Scenarios")
                    }
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
                            }
                        )
                    }
                }

                if (dataSourceItems.isNotEmpty()) {
                    stickyHeader(key = "header_datasources") {
                        SectionHeader("Sources")
                    }
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

    showDeleteDialog?.let { sim ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete simulation?") },
            text = { Text("\"${sim.scenario.scenarioName}\" will be permanently deleted.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteSimulation(sim.scenario.scenarioIndex.toInt())
                    showDeleteDialog = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SimulationCard(
    item: UI2SimulationsViewModel.SimListItem.Simulation,
    onView: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val scenario = item.scenario
    val costFmt = remember { DecimalFormat("#,##0") }

    ListItem(
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.barchart),
                contentDescription = "Simulation",
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
                        "€${costFmt.format(cost)}/yr",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("View") },
                            leadingIcon = { Icon(Icons.Default.Visibility, null) },
                            onClick = { menuExpanded = false; onView() }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menuExpanded = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
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

@Composable
private fun DataSourceCard(
    item: UI2SimulationsViewModel.SimListItem.DataSource,
    onView: () -> Unit
) {
    ListItem(
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_download_24),
                contentDescription = "Data source",
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
                Text("View")
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
