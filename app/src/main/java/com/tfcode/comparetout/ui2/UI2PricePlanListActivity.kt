package com.tfcode.comparetout.ui2

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

// ──────────────────────────────────────────────────────────────────────────
// Supplier-plan list. Compact accordion per plan — collapsed header shows
// only the name plus a favourite star; expansion exposes details and the
// Edit / Delete / Favourite controls. The favourite ID persists across runs
// via FavouritePlanStore and highlights matching rows in every Tariff Plan
// accordion in the app.
// ──────────────────────────────────────────────────────────────────────────

@AndroidEntryPoint
class UI2PricePlanListActivity : AppCompatActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UI2Theme {
                PricePlanListScreen(onClose = { finish() })
            }
        }
    }
}

private val moneyFmt = DecimalFormat("#,##0.00")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PricePlanListScreen(
    viewModel: UI2PricePlanListViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val rows by viewModel.rows.collectAsState()
    val favouriteId by viewModel.favouriteId.observeAsState(null)
    val (showHints, toggleShowHints) = rememberShowHints()
    var pendingDelete by remember { mutableStateOf<PricePlanListRow?>(null) }
    var showDeleteAll by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<List<PricePlanJsonFile>?>(null) }
    var showDrawer by remember { mutableStateOf(false) }
    val shareScope = rememberCoroutineScope()

    fun runPlanImport(list: List<PricePlanJsonFile>, clobber: Boolean) {
        pendingImport = null
        val noun = if (list.size == 1) "plan" else "plans"
        shareScope.launch {
            val outcome = runCatching { viewModel.importPlansFromList(list, clobber) }.getOrNull()
            val msg = outcome?.summary(noun) ?: "Import failed"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    val openWizard: (Long?) -> Unit = { planId ->
        val intent = Intent(context, UI2PricePlanWizardActivity::class.java)
        if (planId != null) intent.putExtra("PricePlanID", planId)
        context.startActivity(intent)
    }

    val onSwitchLegacy: () -> Unit = {
        // The list is launched from the in-app drawer; switching to legacy
        // means finishing back to the host UI2 navigation, which then routes
        // through MainActivity.
        onClose()
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .nestedScroll(rememberNestedScrollInteropConnection())
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Supplier Plans") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDrawer = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item("header") {
                    ListHeader(
                        count = rows.size,
                        showHints = showHints,
                        onCreate = { openWizard(null) },
                        onImport = { showImport = true },
                        onDeleteAll = if (rows.isNotEmpty()) ({ showDeleteAll = true }) else null
                    )
                }
                if (showHints) {
                    item("hint") { ListHintCard() }
                }
                if (rows.isEmpty()) {
                    item("empty") { EmptyListMessage(showHints) }
                } else {
                    items(rows, key = { it.planId }) { row ->
                        PricePlanAccordion(
                            row = row,
                            isFavourite = favouriteId == row.planId,
                            showHints = showHints,
                            onEdit = { openWizard(row.planId) },
                            onDelete = { pendingDelete = row },
                            onToggleFavourite = { viewModel.toggleFavourite(row.planId) },
                            onToggleActive = { viewModel.setActive(row.planId, !row.active) },
                            onShare = {
                                // Serialise on IO, fire the share intent on Main. The
                                // chooser is launched from the Activity context so any
                                // downstream lifecycle is handled by the system.
                                shareScope.launch {
                                    val json = viewModel.buildPlanJson(row.planId)
                                    if (!json.isNullOrEmpty()) {
                                        context.shareText(
                                            payload = json,
                                            format = ShareFormat.JSON,
                                            subject = "${row.supplier} — ${row.planName}"
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Scrim + right-side drawer — matches every other UI2 screen.
            AnimatedVisibility(visible = showDrawer, enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180))) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                    .clickable { showDrawer = false })
            }
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

    pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete supplier plan?") },
            text = {
                Text(
                    "\"${row.supplier} · ${row.planName}\" will be permanently deleted. " +
                        "Any cached costing results for this plan will also be removed.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.delete(row.planId)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            title = { Text("Delete all supplier plans?") },
            text = {
                Text(
                    "All ${rows.size} plan" + (if (rows.size == 1) "" else "s") +
                        " will be permanently deleted, along with any cached costing results. " +
                        "There is no undo.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAll()
                        showDeleteAll = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete all") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAll = false }) { Text("Cancel") }
            }
        )
    }

    if (showImport) {
        UI2ImportSheet(
            title = "Import supplier plans",
            hint = "Accepts the JSON shape produced by the Share button on a plan, " +
                "or a bulk export.",
            applyLabel = "Continue",
            communityUrl = PricePlanDownloader.RATES_URL,
            communityNote = "Community-maintained Irish supplier tariffs — may be out of " +
                "date. You can edit any plan after importing.",
            llmPrompt = PricePlanDownloader.LLM_PROMPT,
            extraSourceLabel = "Octopus tariffs",
            extraSourceContent = { OctopusTariffFetchPane() },
            parse = ::parsePricePlansJson,
            onApply = {
                pendingImport = it
                showImport = false
            },
            onDismiss = { showImport = false }
        )
    }

    pendingImport?.let { list ->
        val countLabel = if (list.size == 1) "1 supplier plan" else "${list.size} supplier plans"
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Import $countLabel") },
            text = {
                Text(
                    "If a plan with the same name already exists, should the imported " +
                        "version replace it, or be kept alongside the existing one?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = { runPlanImport(list, clobber = true) }) { Text("Replace existing") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { runPlanImport(list, clobber = false) }) { Text("Keep both") }
                    TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun ListHeader(
    count: Int,
    showHints: Boolean,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onDeleteAll: (() -> Unit)?
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "$count plan" + if (count == 1) "" else "s",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (showHints) {
                        Text(
                            "Tap a row to see its details · Star marks your current contract",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Hints off: actions sit inline beside the count. Hints on: the
                // longer hint text needs the width, so the actions drop to a
                // second row below.
                if (!showHints) {
                    HeaderActions(onCreate, onImport, onDeleteAll)
                }
            }
            if (showHints) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeaderActions(onCreate, onImport, onDeleteAll)
                }
            }
        }
    }
}

/** The Create / Import / Delete-all actions, shared by the inline (hints off)
 *  and wrapped (hints on) header layouts. Rendered inside a RowScope. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderActions(
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onDeleteAll: (() -> Unit)?
) {
    if (onDeleteAll != null) {
        IconButton(onClick = onDeleteAll) {
            Icon(Icons.Default.Delete, contentDescription = "Delete all plans",
                tint = MaterialTheme.colorScheme.error)
        }
    }
    TextButton(onClick = onImport) {
        Text("Import…", style = MaterialTheme.typography.labelLarge)
    }
    TextButton(onClick = onCreate) {
        Icon(Icons.Default.Add, contentDescription = null,
            modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("Create new", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ListHintCard() {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("About supplier plans",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            Text(
                "Every cost in the app — Compare tab totals, dashboard Tariff Plan " +
                    "tables, KPIs — is calculated against one of these plans. " +
                    "Tap the ★ on a plan to mark it as the tariff you're currently " +
                    "contracted to; it will be highlighted everywhere it appears.\n\n" +
                    "The green ✓ marks a plan as active. Active only narrows the dashboard's " +
                    "Tariff Plan table — the Compare tab still evaluates every plan regardless " +
                    "of its active state. Open a plan and tap the ✓ to toggle: grey → green " +
                    "activates it, green → grey deactivates it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PricePlanAccordion(
    row: PricePlanListRow,
    isFavourite: Boolean,
    showHints: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleActive: () -> Unit,
    onShare: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val borderColor = if (isFavourite)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    else if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Card(
        modifier = Modifier.fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column {
            // ── Collapsed header: just enough to scan the list at a glance.
            // heightIn(min = MIN_TOUCH) keeps the tap surface a comfortable
            // 48 dp even when the supplier/plan text wraps to 3 lines at
            // accessibility-scale fonts.
            Row(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(min = AdaptiveLayout.MIN_TOUCH)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isFavourite) Icons.Default.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isFavourite) "Current plan" else null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isFavourite) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        row.supplier,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        row.planName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (row.active) {
                    // When the accordion is open the tick is clickable so the user
                    // can deactivate the plan; collapsed view is purely indicator.
                    val activeModifier = if (expanded)
                        Modifier.size(18.dp).clickable(onClick = onToggleActive)
                    else
                        Modifier.size(18.dp)
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(
                            com.tfcode.comparetout.R.drawable.tick),
                        contentDescription = if (expanded)
                            "Active — tap to deactivate for the dashboard"
                        else
                            "Active — included in dashboard tariff tables",
                        modifier = activeModifier,
                        tint = Color.Unspecified
                    )
                } else if (expanded) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(
                            com.tfcode.comparetout.R.drawable.tick),
                        contentDescription = "Inactive — tap to activate for the dashboard",
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(onClick = onToggleActive),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                                  else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                if (borderColor != Color.Transparent) {
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (row.reference.isNotBlank() && row.reference != "<REFERENCE>") {
                        Text(
                            row.reference,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Spec strip — standing / feed / bonus / day-rate count.
                    // Routed through AdaptiveCellRow so the strip wraps to 2/1
                    // cells per row under font scaling instead of clipping.
                    val specs = listOf(
                        "Standing" to "€${moneyFmt.format(row.standingCharges)}/yr",
                        "Feed-in" to "${moneyFmt.format(row.feed)} c/kWh",
                        "Bonus" to "€${moneyFmt.format(row.signUpBonus)}",
                        "Rates" to "${row.rateCount} day-rate" +
                            if (row.rateCount == 1) "" else "s"
                    )
                    AdaptiveCellRow(items = specs) { (label, value) ->
                        SpecCell(label, value)
                    }

                    if (row.deemedExport) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                            shape = CircleShape
                        ) {
                            Text("Deemed export",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                        }
                    }

                    if (showHints) {
                        Text(
                            if (isFavourite)
                                "★ This is your current plan — highlighted in every Tariff Plan view."
                            else
                                "Tap ★ to mark this as your current plan.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Action buttons — icon-only to keep four affordances comfortably
                    // visible on narrow screens. Each icon doubles as the
                    // contentDescription so a long-press / a11y still names it,
                    // and the legend below labels them when Show hints is on.
                    //
                    // Buttons + legend share the same 4→2→2 per-row layout so the
                    // legend stays aligned beneath the buttons in every tier.
                    data class ActionSpec(
                        val icon: androidx.compose.ui.graphics.vector.ImageVector,
                        val label: String,
                        val legend: String,
                        val tint: Color,
                        val onClick: () -> Unit,
                    )
                    val starIcon = if (isFavourite) Icons.Default.Star else Icons.Outlined.StarBorder
                    val actions = listOf(
                        ActionSpec(starIcon,
                            if (isFavourite) "Current plan" else "Mark as my plan",
                            if (isFavourite) "Current" else "My plan",
                            MaterialTheme.colorScheme.primary, onToggleFavourite),
                        ActionSpec(Icons.Default.Edit, "Edit", "Edit",
                            MaterialTheme.colorScheme.onSurface, onEdit),
                        ActionSpec(Icons.Default.Share, "Share", "Share",
                            MaterialTheme.colorScheme.onSurface, onShare),
                        ActionSpec(Icons.Default.Delete, "Delete", "Delete",
                            MaterialTheme.colorScheme.error, onDelete),
                    )
                    // ActionRowCenter caps the action row at 480 dp on tablets
                    // so the four icon buttons don't isolate themselves across a
                    // foot of screen width.
                    ActionRowCenter {
                        AdaptiveCellRow(
                            items = actions,
                            perRowAtA = 4, perRowAtB = 2, perRowAtC = 2
                        ) { spec ->
                            ActionIconButton(
                                icon = spec.icon, label = spec.label,
                                tint = spec.tint, onClick = spec.onClick
                            )
                        }
                    }

                    // Legend — only rendered when Show hints is on. Mirrors the
                    // action row layout so each label sits underneath its icon.
                    if (showHints) {
                        ActionRowCenter {
                            AdaptiveCellRow(
                                items = actions,
                                perRowAtA = 4, perRowAtB = 2, perRowAtC = 2
                            ) { spec ->
                                ActionLegendCell(spec.legend)
                            }
                        }
                    }

                    if (row.lastUpdate.isNotBlank()) {
                        Text(
                            "Last updated ${row.lastUpdate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecCell(label: String, value: String, modifier: Modifier = Modifier) {
    // heightIn(min = 46.dp) instead of a fixed height so wrapped rows at
    // tier B/C can grow with the cell's content (label + value at larger
    // font sizes) instead of clipping.
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.heightIn(min = 46.dp).fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Icon-only OutlinedButton with no inner padding — the icon is the affordance,
 *  the legend row below provides the textual label when Show hints is on.
 *  `label` is used as the accessibility `contentDescription`. */
@Composable
private fun ActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    OutlinedButton(
        onClick = onClick,
        // heightIn(min = MIN_TOUCH) keeps each button at a 48 dp tap target
        // even when AdaptiveCellRow stacks them at higher font scales.
        modifier = modifier.fillMaxWidth().heightIn(min = AdaptiveLayout.MIN_TOUCH),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label,
            modifier = Modifier.size(18.dp), tint = tint)
    }
}

/** A single centred caption beneath the action row, used only when Show hints
 *  is on. Width-aligned with its button via the caller's `weight(1f)`. */
@Composable
private fun ActionLegendCell(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
private fun EmptyListMessage(showHints: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("No supplier plans yet", style = MaterialTheme.typography.titleSmall)
            if (showHints) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tap “+ Create new” to enter your supplier's tariff. Plans drive every " +
                        "cost calculation in the app — at least one is needed before the Compare " +
                        "tab can show prices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Octopus tariff fetch (public API, no login) ────────────────────────
//
// The Octopus /products/ and tariff-rate endpoints need no credentials, so
// any user can pull the currently-open Octopus tariffs for their GSP region
// into the plan list. Region comes from a postcode lookup (public endpoint),
// with a manual A–P region picker as the fallback. Dynamic tariffs
// (Agile/Tracker) are skipped — the repeating DayRate model cannot hold
// per-day prices — and the export rate on every generated plan assumes
// Outgoing Fixed (noted on the plan's reference).

private val GSP_REGIONS = listOf(
    "A" to "Eastern England", "B" to "East Midlands", "C" to "London",
    "D" to "Merseyside & N Wales", "E" to "West Midlands", "F" to "North East England",
    "G" to "North West England", "H" to "Southern England", "J" to "South East England",
    "K" to "South Wales", "L" to "South West England", "M" to "Yorkshire",
    "N" to "South Scotland", "P" to "North Scotland"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OctopusTariffFetchPane() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tariffPlans = remember {
        EntryPointAccessors
            .fromApplication(context.applicationContext, OctopusTariffPlansEntryPoint::class.java)
            .octopusTariffPlans()
    }
    var postcode by remember { mutableStateOf("") }
    var region by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    fun runFetch() {
        scope.launch {
            busy = true
            status = null
            val outcome = withContext(Dispatchers.IO) {
                val resolved = region
                    ?: postcode.takeIf { it.isNotBlank() }?.let { pc ->
                        runCatching { tariffPlans.resolveRegionBlocking(pc.trim()) }.getOrNull()
                    }
                if (resolved == null) {
                    "Couldn't resolve a region — check the postcode or pick a region below."
                } else {
                    region = resolved
                    when (val r = tariffPlans.generateForRegionBlocking(resolved)) {
                        is OctopusTariffPlans.Result.Loaded ->
                            "Added ${r.added} plan" + (if (r.added == 1) "" else "s") +
                                " for region $resolved" +
                                (if (r.existing > 0) " (${r.existing} already present)" else "") +
                                (if (r.skipped > 0) " (${r.skipped} not representable)" else "")
                        is OctopusTariffPlans.Result.NoRegion ->
                            "Couldn't resolve a region — pick one below."
                        is OctopusTariffPlans.Result.Failed ->
                            r.error.message ?: "Octopus fetch failed"
                    }
                }
            }
            status = outcome
            busy = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Fetches every Octopus Energy tariff open for sign-up in your region and adds " +
                "each as a supplier plan. Dynamic tariffs (Agile/Tracker) are skipped; the " +
                "export rate assumes Outgoing Fixed — edit any plan after importing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = postcode,
            onValueChange = { postcode = it; region = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("UK postcode (resolves your region)") },
            singleLine = true
        )
        Text(
            "Or pick your region:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GSP_REGIONS.forEach { (letter, name) ->
                FilterChip(
                    selected = region == letter,
                    onClick = { region = if (region == letter) null else letter },
                    label = { Text("$letter · $name", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Fetching tariffs — this walks every open product…",
                    style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Button(
                onClick = { runFetch() },
                enabled = region != null || postcode.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Fetch tariffs") }
        }
        status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium)
        }
    }
}
