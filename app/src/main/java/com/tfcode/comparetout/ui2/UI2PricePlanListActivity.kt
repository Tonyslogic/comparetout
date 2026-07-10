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
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tfcode.comparetout.R
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile
import com.tfcode.comparetout.region.RegionProfiles
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
    val allRows by viewModel.rows.collectAsState()
    // Location filter: suppliers tagged with a different country than the phone
    // are hidden (and auto-deactivated by the VM) unless the user reveals them.
    var showOtherLocations by remember { mutableStateOf(false) }
    val hiddenByLocation = remember(allRows) {
        allRows.count { it.locationMismatch(viewModel.deviceCountry) }
    }
    val rows = remember(allRows, showOtherLocations) {
        if (showOtherLocations) allRows
        else allRows.filterNot { it.locationMismatch(viewModel.deviceCountry) }
    }
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
        val noun = context.resources.getQuantityString(R.plurals.ui2_noun_plan, list.size)
        shareScope.launch {
            val outcome = runCatching { viewModel.importPlansFromList(list, clobber) }.getOrNull()
            val msg = outcome?.summary(context, noun)
                ?: context.getString(R.string.ui2_import_failed)
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
                title = { Text(stringResource(R.string.ui2_drawer_supplier_plans)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.ui2_back))
                    }
                },
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
                if (hiddenByLocation > 0 || showOtherLocations) {
                    item("locationFilter") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showOtherLocations = !showOtherLocations }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = showOtherLocations,
                                onCheckedChange = { showOtherLocations = it }
                            )
                            Text(
                                if (!showOtherLocations && hiddenByLocation > 0)
                                    stringResource(R.string.ui2_ppl_show_other_locations_hidden,
                                        hiddenByLocation, viewModel.deviceCountry)
                                else stringResource(R.string.ui2_ppl_show_other_locations),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
                            onRetryPending = {
                                DynamicTariffWorker.enqueueForPlan(
                                    context, row.planId, row.planName,
                                    row.dynamicYear ?: (java.time.LocalDate.now().year - 1))
                                Toast.makeText(context,
                                    context.getString(R.string.ui2_ppl_dyn_pending_queued, row.planName),
                                    Toast.LENGTH_SHORT).show()
                            },
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
            title = { Text(stringResource(R.string.ui2_ppl_delete_title)) },
            text = {
                Text(
                    stringResource(R.string.ui2_ppl_delete_body,
                        "${row.supplier} · ${row.planName}"),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.delete(row.planId)
                    pendingDelete = null
                }) { Text(stringResource(R.string.ui2_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            title = { Text(stringResource(R.string.ui2_ppl_delete_all_title)) },
            text = {
                Text(
                    pluralStringResource(R.plurals.ui2_ppl_delete_all_body, rows.size, rows.size),
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
                ) { Text(stringResource(R.string.ui2_scenarios_delete_all)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAll = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    if (showImport) {
        // Region-gated sources: the community feed URL/wording comes from the
        // edition's profile (null hides the entry), and the Octopus tariff
        // browser is only offered where Octopus operates (GB).
        val region = RegionProfiles.current
        UI2ImportSheet(
            title = stringResource(R.string.ui2_ie_import_plans),
            hint = stringResource(R.string.ui2_ppl_import_hint),
            applyLabel = stringResource(R.string.ui2_continue),
            communityUrl = region.pricePlanFeedUrl,
            // Note is paired with the URL in the profile; when the URL is null
            // the community source never renders, so "" is never seen.
            communityNote = region.pricePlanFeedNote ?: "",
            llmPrompt = PricePlanDownloader.LLM_PROMPT,
            // One extra self-contained source per edition: the Octopus tariff
            // browser (GB) or the dynamic-tariff generator (regions with a
            // wholesale market registered, IE first).
            extraSourceLabel = when {
                region.hasOctopus -> stringResource(R.string.ui2_ppl_octopus_tariffs)
                region.dynamicMarkets.isNotEmpty() ->
                    stringResource(R.string.ui2_ppl_dynamic_tariff)
                else -> null
            },
            extraSourceContent = when {
                region.hasOctopus -> ({ OctopusTariffFetchPane() })
                region.dynamicMarkets.isNotEmpty() -> ({
                    DynamicTariffPane(onQueued = { showImport = false })
                })
                else -> null
            },
            parse = { parsePricePlansJson(context, it) },
            onApply = {
                pendingImport = it
                showImport = false
            },
            onDismiss = { showImport = false }
        )
    }

    pendingImport?.let { list ->
        val countLabel = pluralStringResource(R.plurals.ui2_ie_count_plans, list.size, list.size)
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.ui2_import_count_title, countLabel)) },
            text = {
                Text(
                    stringResource(R.string.ui2_clobber_body_plan),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = { runPlanImport(list, clobber = true) }) {
                    Text(stringResource(R.string.ui2_replace_existing))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { runPlanImport(list, clobber = false) }) {
                        Text(stringResource(R.string.ui2_keep_both))
                    }
                    TextButton(onClick = { pendingImport = null }) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
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
                        pluralStringResource(R.plurals.ui2_ppl_count, count, count),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (showHints) {
                        Text(
                            stringResource(R.string.ui2_ppl_header_hint),
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
            Icon(Icons.Default.Delete,
                contentDescription = stringResource(R.string.ui2_ppl_delete_all_cd),
                tint = MaterialTheme.colorScheme.error)
        }
    }
    TextButton(onClick = onImport) {
        Text(stringResource(R.string.ui2_ppl_import_action),
            style = MaterialTheme.typography.labelLarge)
    }
    TextButton(onClick = onCreate) {
        Icon(Icons.Default.Add, contentDescription = null,
            modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.ui2_ppl_create_new),
            style = MaterialTheme.typography.labelLarge)
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
            Text(stringResource(R.string.ui2_ppl_about_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.ui2_ppl_about_body),
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
    onRetryPending: () -> Unit,
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
                    contentDescription = if (isFavourite)
                        stringResource(R.string.ui2_ppl_current_plan) else null,
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
                        contentDescription = stringResource(if (expanded)
                            R.string.ui2_ppl_active_expanded_cd
                        else
                            R.string.ui2_ppl_active_cd),
                        modifier = activeModifier,
                        tint = Color.Unspecified
                    )
                } else if (expanded) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(
                            com.tfcode.comparetout.R.drawable.tick),
                        contentDescription = stringResource(R.string.ui2_ppl_inactive_cd),
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
                    val cur = RegionProfiles.current.currencySymbol
                    val specs = listOf(
                        stringResource(R.string.ui2_ppl_spec_standing)
                            to "$cur${moneyFmt.format(row.standingCharges)}/yr",
                        stringResource(R.string.ui2_ppl_spec_feed_in)
                            to "${moneyFmt.format(row.feed)} ${RegionProfiles.current.rateUnit}",
                        stringResource(R.string.ui2_ppl_spec_bonus)
                            to "$cur${moneyFmt.format(row.signUpBonus)}",
                        stringResource(R.string.ui2_ppl_spec_rates)
                            to pluralStringResource(R.plurals.ui2_ppl_day_rates,
                                row.rateCount, row.rateCount)
                    )
                    AdaptiveCellRow(items = specs) { (label, value) ->
                        SpecCell(label, value)
                    }

                    // Deemed export is an IE-only concept — other editions never
                    // surface the badge even if an imported plan carries the flag.
                    if (row.deemedExport && RegionProfiles.current.hasDeemedExport) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                            shape = CircleShape
                        ) {
                            Text(stringResource(R.string.deemed_export_calculation),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                        }
                    }

                    if (row.hasRestrictions) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                            shape = CircleShape
                        ) {
                            Text(stringResource(R.string.ui2_ppl_restrictions_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                        }
                    }

                    // Dynamic plans are generated artefacts — badge them so a
                    // 365-rate plan is recognisably not hand-entered. Pending =
                    // terms imported but prices not yet downloaded; tap retries.
                    if (row.isPending) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = CircleShape,
                            modifier = Modifier.clickable(onClick = onRetryPending)
                        ) {
                            Text(stringResource(R.string.ui2_ppl_dyn_pending_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                        }
                    } else if (row.isDynamic) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                            shape = CircleShape
                        ) {
                            Text(stringResource(R.string.ui2_ppl_dyn_badge) +
                                    (row.dynamicYear?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                        }
                    }

                    if (row.location.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = CircleShape
                        ) {
                            Text(stringResource(R.string.ui2_ppl_location, row.location),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                        }
                    }

                    if (showHints) {
                        Text(
                            stringResource(if (isFavourite)
                                R.string.ui2_ppl_fav_hint_current
                            else
                                R.string.ui2_ppl_fav_hint_tap),
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
                            stringResource(if (isFavourite) R.string.ui2_ppl_current_plan
                                           else R.string.ui2_ppl_mark_my_plan),
                            stringResource(if (isFavourite) R.string.ui2_ppl_legend_current
                                           else R.string.ui2_ppl_legend_my_plan),
                            MaterialTheme.colorScheme.primary, onToggleFavourite),
                        ActionSpec(Icons.Default.Edit,
                            stringResource(R.string.ui2_edit), stringResource(R.string.ui2_edit),
                            MaterialTheme.colorScheme.onSurface, onEdit),
                        ActionSpec(Icons.Default.Share,
                            stringResource(R.string.ui2_share), stringResource(R.string.ui2_share),
                            MaterialTheme.colorScheme.onSurface, onShare),
                        ActionSpec(Icons.Default.Delete,
                            stringResource(R.string.ui2_delete), stringResource(R.string.ui2_delete),
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
                            stringResource(R.string.ui2_ppl_last_updated, row.lastUpdate),
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
            Text(stringResource(R.string.ui2_ppl_empty_title),
                style = MaterialTheme.typography.titleSmall)
            if (showHints) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.ui2_ppl_empty_body),
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
                    context.getString(R.string.ui2_ppl_octo_no_region_postcode)
                } else {
                    region = resolved
                    when (val r = tariffPlans.generateForRegionBlocking(resolved)) {
                        is OctopusTariffPlans.Result.Loaded ->
                            context.resources.getQuantityString(
                                R.plurals.ui2_ppl_octo_added, r.added, r.added, resolved) +
                                (if (r.existing > 0)
                                    " " + context.getString(R.string.ui2_ppl_octo_existing, r.existing)
                                 else "") +
                                (if (r.skipped > 0)
                                    " " + context.getString(R.string.ui2_ppl_octo_skipped, r.skipped)
                                 else "")
                        is OctopusTariffPlans.Result.NoRegion ->
                            context.getString(R.string.ui2_ppl_octo_no_region_pick)
                        is OctopusTariffPlans.Result.Failed ->
                            r.error.message ?: context.getString(R.string.ui2_ppl_octo_fetch_failed)
                    }
                }
            }
            status = outcome
            busy = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.ui2_ppl_octo_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = postcode,
            onValueChange = { postcode = it; region = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.ui2_ppl_octo_postcode)) },
            singleLine = true
        )
        Text(
            stringResource(R.string.ui2_ppl_octo_pick_region),
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
                Text(stringResource(R.string.ui2_ppl_octo_fetching),
                    style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Button(
                onClick = { runFetch() },
                enabled = region != null || postcode.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.ui2_ppl_octo_fetch)) }
        }
        status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium)
        }
    }
}

// ── Dynamic tariff generator (wholesale-tracking offers, IE/I-SEM first) ──
//
// Collects the supplier's published terms (multiplier/adder/cap over the
// day-ahead price, standing charge, export rate) plus a historical backtest
// year, then hands off to DynamicTariffWorker: the fetch is minutes on a
// first run, so it happens in the background with a notification, not here.

@Composable
private fun DynamicTariffPane(onQueued: () -> Unit) {
    val context = LocalContext.current
    val region = RegionProfiles.current
    val market = region.dynamicMarkets.first()
    val lastCompleteYear = remember { java.time.LocalDate.now().year - 1 }
    var year by remember { mutableStateOf(lastCompleteYear.toString()) }
    var multiplier by remember { mutableStateOf("1.0") }
    var adder by remember { mutableStateOf("") }
    var cap by remember { mutableStateOf("") }
    var standing by remember { mutableStateOf("0") }
    var feed by remember { mutableStateOf("0") }

    fun parsed(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
    val yearValue = year.trim().toIntOrNull()
    // The SEM went live 2018-10; a future/current year can't be complete.
    val ready = yearValue != null && yearValue in 2018..lastCompleteYear &&
            parsed(multiplier) != null && parsed(adder) != null

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.ui2_ppl_dyn_desc, market.displayName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = year, onValueChange = { year = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.ui2_ppl_dyn_year)) }
        )
        OutlinedTextField(
            value = multiplier, onValueChange = { multiplier = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.ui2_ppl_dyn_multiplier)) }
        )
        OutlinedTextField(
            value = adder, onValueChange = { adder = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.ui2_ppl_dyn_adder, region.rateUnit)) }
        )
        OutlinedTextField(
            value = cap, onValueChange = { cap = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.ui2_ppl_dyn_cap, region.rateUnit)) }
        )
        OutlinedTextField(
            value = standing, onValueChange = { standing = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.ui2_ppl_dyn_standing, region.currencySymbol)) }
        )
        OutlinedTextField(
            value = feed, onValueChange = { feed = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.ui2_ppl_dyn_feed, region.rateUnit)) }
        )
        Button(
            onClick = {
                val supplier = market.displayName.substringBefore(" (")
                val digest = "×$multiplier +${adder}c" +
                        (parsed(cap)?.let { ", cap ${cap.trim()}c" } ?: "")
                val ppj = PricePlanJsonFile()
                ppj.supplier = supplier
                ppj.plan = "DA $year $digest"
                ppj.standingCharges = parsed(standing) ?: 0.0
                ppj.feed = parsed(feed) ?: 0.0
                ppj.bonus = 0.0
                ppj.active = true
                ppj.location = region.regionCode
                val terms = com.tfcode.comparetout.model.json.priceplan.DynamicTermsJson()
                terms.market = market.id
                terms.year = yearValue
                terms.multiplier = parsed(multiplier)
                terms.adder = parsed(adder)
                terms.cap = parsed(cap)
                ppj.dynamic = terms
                DynamicTariffWorker.enqueue(
                    context, com.google.gson.Gson().toJson(ppj), ppj.plan, yearValue ?: lastCompleteYear)
                Toast.makeText(context,
                    context.getString(R.string.ui2_ppl_dyn_queued, ppj.plan),
                    Toast.LENGTH_LONG).show()
                onQueued()
            },
            enabled = ready,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.ui2_ppl_dyn_generate)) }
        Text(
            market.attribution,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
