package com.tfcode.comparetout.ui2

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ──────────────────────────────────────────────────────────────────────────
// Directors tab — recreated from the touc-director handoff design.
//
// One accordion per dashboard group (Usage → Inverter → PV → Battery → HW
// → EV), each with 1+ panels showing component instances shared by 2+
// scenarios. Link / Unlink / Fork and freshly-seeded components are cached
// in the UI and committed by Save.
// ──────────────────────────────────────────────────────────────────────────

private const val SCROLL_THRESHOLD = 4

private enum class ChipKind { LINKED, FORKED, NEW }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectorScreen(
    viewModel: UI2DirectorViewModel,
    onSwitchLegacy: () -> Unit,
    onClose: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val novice by viewModel.noviceMode.observeAsState(true)
    val context = LocalContext.current

    var openGroups by remember { mutableStateOf(setOf<DirectorGroup>()) }
    var showDrawer by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<RemoveTarget?>(null) }
    var linkTarget by remember { mutableStateOf<DirectorInstance?>(null) }
    var seedSubject by remember { mutableStateOf<DirectorSubject?>(null) }
    var pickSubjectFor by remember { mutableStateOf<DirectorGroup?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val closeRequested = {
        if (state.dirty) confirmDiscard = true else onClose()
    }

    fun isSeeded(i: DirectorInstance) =
        state.seeded.contains(DirectorSeedKey(i.subject, i.componentId))
    fun visibleInstances(g: DirectorGroup) = state.instances
        .filter { it.subject.group == g && (displayedChips(it, state).size > 1 || isSeeded(it)) }
        .sortedWith(compareBy({ it.subject.ordinal }, { it.name.lowercase() }))

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .nestedScroll(rememberNestedScrollInteropConnection())
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Directors") },
                actions = {
                    IconButton(onClick = { showDrawer = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else {
                // Browser-style collapsing header: hides when the user drags content up and
                // reappears the moment they drag content down. Always shown at the very top.
                val listState = rememberLazyListState()
                var actionBarVisible by remember { mutableStateOf(true) }
                var lastIndex by remember { mutableIntStateOf(0) }
                var lastOffset by remember { mutableIntStateOf(0) }
                LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
                    val idx = listState.firstVisibleItemIndex
                    val off = listState.firstVisibleItemScrollOffset
                    val atTop = idx == 0 && off == 0
                    val deltaIdx = idx - lastIndex
                    val deltaOff = off - lastOffset
                    when {
                        atTop -> actionBarVisible = true
                        deltaIdx > 0 || (deltaIdx == 0 && deltaOff >  SCROLL_THRESHOLD) -> actionBarVisible = false
                        deltaIdx < 0 || deltaOff < -SCROLL_THRESHOLD -> actionBarVisible = true
                    }
                    lastIndex = idx
                    lastOffset = off
                }

                Column(Modifier.fillMaxSize()) {
                    AnimatedVisibility(visible = actionBarVisible) {
                        Column(
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            DirectorActionBar(
                                dirty = state.dirty, saving = state.saving, novice = novice,
                                onSave = { viewModel.save() },
                                onRun = { viewModel.save() },
                                onClose = closeRequested
                            )
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 84.dp)
                    ) {
                        if (novice) {
                            item("hint") {
                                Text(
                                    "Components shared by 2 or more scenarios. Editing one updates every " +
                                        "linked scenario; changes are cached until you Save.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                        DirectorGroup.values().forEach { group ->
                    item(key = "group-$group") {
                        GroupAccordion(
                            group = group,
                            open = openGroups.contains(group),
                            instances = visibleInstances(group),
                            state = state,
                            onToggle = {
                                openGroups = if (openGroups.contains(group))
                                    openGroups - group else openGroups + group
                            },
                            onEditScenarioId = { sid ->
                                context.startActivity(
                                    Intent(context, UI2WizardActivity::class.java)
                                        .putExtra("ScenarioID", sid)
                                        .putExtra("WizardSection", group.wizardSection)
                                )
                            },
                            onChipClick = { inst, scenarioId ->
                                val key = DirectorEditKey(inst.subject, inst.componentId, scenarioId)
                                if (state.edits[key] != null) {
                                    viewModel.cancelEdit(inst.subject, inst.componentId, scenarioId)
                                } else {
                                    removeTarget = RemoveTarget(inst, scenarioId)
                                }
                            },
                            onLink = { linkTarget = it },
                            onAddNew = { pickSubjectFor = group }
                        )
                    }
                        }
                    }
                }
            }

            if (state.saving) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                    Alignment.Center) { CircularProgressIndicator() }
            }

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
                        showHints = novice,
                        onShowHintsChange = { if (it != novice) viewModel.toggleNoviceMode() },
                        onSwitchLegacy = { showDrawer = false; onSwitchLegacy() },
                        onClose = { showDrawer = false }
                    )
                }
            }
        }
    }

    // Remove / Fork dialog
    removeTarget?.let { target ->
        val scenarioName = state.scenarios.firstOrNull { it.id == target.scenarioId }?.name ?: "?"
        val canUnlink = target.instance.subject.supportsUnlink
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Unlink · $scenarioName") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Stop sharing \"${target.instance.name}\" with $scenarioName?",
                        style = MaterialTheme.typography.bodyMedium)
                    if (canUnlink) {
                        DialogAction(
                            "Remove component from scenario",
                            "$scenarioName will no longer have this ${target.instance.subject.label.lowercase()}.",
                            Color(0xFFE15A52)
                        ) {
                            viewModel.queueEdit(target.instance.subject, target.instance.componentId,
                                target.scenarioId, DirectorEditOp.UNLINK)
                            removeTarget = null
                        }
                    }
                    DialogAction(
                        "Fork (copy) for this scenario",
                        "Give this scenario its own editable copy of the component.",
                        Color(0xFFF3A93B)
                    ) {
                        viewModel.queueEdit(target.instance.subject, target.instance.componentId,
                            target.scenarioId, DirectorEditOp.FORK)
                        removeTarget = null
                    }
                    if (!canUnlink) {
                        Text("This component type cannot be unlinked from the Directors — fork it instead.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancel") } }
        )
    }

    // Link-scenario picker
    linkTarget?.let { inst ->
        val shown = displayedChips(inst, state).map { it.first.id }.toSet()
        val available = state.scenarios.filter { it.id !in shown }
        PickerDialog(
            title = "Link ${inst.subject.label}",
            subtitle = "Add \"${inst.name}\" to a scenario:",
            rows = available.map { it.name to { viewModel.queueEdit(
                inst.subject, inst.componentId, it.id, DirectorEditOp.LINK); linkTarget = null } },
            emptyText = "Already linked to every scenario.",
            onDismiss = { linkTarget = null }
        )
    }

    // Subject picker — choose which kind of component to seed inside a group
    pickSubjectFor?.let { group ->
        val choices = DirectorSubject.subjectsFor(group)
        if (choices.size == 1) {
            // single-subject groups (Usage / Inverter / PV) — skip straight to seed picker
            seedSubject = choices.first()
            pickSubjectFor = null
        } else {
            PickerDialog(
                title = "Link new ${group.label.lowercase()}",
                subtitle = "Which ${group.label.lowercase()} item would you like to share?",
                rows = choices.map { subj ->
                    subj.label to {
                        seedSubject = subj
                        pickSubjectFor = null
                    }
                },
                emptyText = "No subjects available.",
                onDismiss = { pickSubjectFor = null }
            )
        }
    }

    // Seed picker — choose a scenario whose component becomes the seed
    seedSubject?.let { subj ->
        val candidates = state.instances.filter {
            it.subject == subj && it.linked.size == 1 &&
                !state.seeded.contains(DirectorSeedKey(it.subject, it.componentId))
        }
        PickerDialog(
            title = "Seed shared ${subj.label.lowercase()}",
            subtitle = "Pick a scenario whose ${subj.label.lowercase()} to share. " +
                "It becomes the seed — link more scenarios to it afterwards.",
            rows = candidates.map { inst ->
                val sName = state.scenarios.firstOrNull { it.id == inst.linked.first() }?.name ?: "?"
                "$sName  ·  ${inst.name}" to {
                    viewModel.seed(inst.subject, inst.componentId)
                    openGroups = openGroups + inst.subject.group
                    seedSubject = null
                }
            },
            emptyText = "No scenario has an unshared ${subj.label.lowercase()} to share.",
            onDismiss = { seedSubject = null }
        )
    }

    // Discard-changes confirmation
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard unsaved changes?") },
            text = {
                Text(
                    "You have pending link / unlink / fork edits. Closing now will discard them.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.discardAll()
                    confirmDiscard = false
                    onClose()
                }) { Text("Discard & close") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") }
            }
        )
    }
}

private data class RemoveTarget(val instance: DirectorInstance, val scenarioId: Long)

/** The chips to render for an instance after overlaying the cached edits. */
private fun displayedChips(
    inst: DirectorInstance, state: DirectorUiState
): List<Pair<DirectorScenarioRef, ChipKind>> {
    val byId = state.scenarios.associateBy { it.id }
    val result = LinkedHashMap<Long, ChipKind>()
    inst.linked.forEach { sid ->
        when (state.edits[DirectorEditKey(inst.subject, inst.componentId, sid)]) {
            DirectorEditOp.UNLINK -> {}
            DirectorEditOp.FORK   -> result[sid] = ChipKind.FORKED
            else                  -> result[sid] = ChipKind.LINKED
        }
    }
    state.edits.forEach { (key, op) ->
        if (op == DirectorEditOp.LINK && key.subject == inst.subject && key.componentId == inst.componentId) {
            result[key.scenarioId] = ChipKind.NEW
        }
    }
    return result.mapNotNull { (sid, kind) -> byId[sid]?.let { it to kind } }
}

// ── action bar (Save / Run simulation / Close — mirrors the wizard footer) ──
@Composable
private fun DirectorActionBar(
    dirty: Boolean, saving: Boolean, novice: Boolean,
    onSave: () -> Unit, onRun: () -> Unit, onClose: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(10.dp)) {
            if (novice) {
                Text("Commit shared-component changes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (dirty) {
                    Button(onClick = onSave, enabled = !saving, modifier = Modifier.weight(1f)) {
                        Box(Modifier.size(7.dp).background(
                            MaterialTheme.colorScheme.onPrimary, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text("Save")
                    }
                } else {
                    OutlinedButton(onClick = onSave, enabled = false, modifier = Modifier.weight(1f)) {
                        Text("Save")
                    }
                }
                Button(onClick = onRun, enabled = !saving, modifier = Modifier.weight(1.4f)) {
                    Text("Run simulation")
                }
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Close") }
            }
        }
    }
}

@Composable
private fun GroupAccordion(
    group: DirectorGroup,
    open: Boolean,
    instances: List<DirectorInstance>,
    state: DirectorUiState,
    onToggle: () -> Unit,
    onEditScenarioId: (Long) -> Unit,
    onChipClick: (DirectorInstance, Long) -> Unit,
    onLink: (DirectorInstance) -> Unit,
    onAddNew: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.clickable { onToggle() }.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(group.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (group.tintIcon) MaterialTheme.colorScheme.onSurface else Color.Unspecified
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(group.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${instances.size} ${if (instances.size == 1) "linked group" else "linked groups"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (open) {
                    Box(
                        modifier = Modifier.clickable { onAddNew() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Link new",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Icon(
                    imageVector = if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (open) "Collapse" else "Expand"
                )
            }
            if (open) {
                Column(modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (instances.isEmpty()) {
                        Text(
                            "No shared ${group.label.lowercase()} components. Tap + to start sharing one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else instances.forEach { inst ->
                        GroupPanel(
                            instance = inst,
                            chips = displayedChips(inst, state),
                            onEdit = {
                                val sid = inst.lowestScenarioId ?: return@GroupPanel
                                onEditScenarioId(sid)
                            },
                            onChipClick = { sid -> onChipClick(inst, sid) },
                            onLink = { onLink(inst) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupPanel(
    instance: DirectorInstance,
    chips: List<Pair<DirectorScenarioRef, ChipKind>>,
    onEdit: () -> Unit,
    onChipClick: (Long) -> Unit,
    onLink: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        instance.subject.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        instance.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit in wizard",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SpecGrid(instance.specs)
            Spacer(Modifier.height(10.dp))
            LinkedScenariosPanel(chips, onChipClick, onLink)
        }
    }
}

@Composable
private fun SpecGrid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                row.forEach { (k, v) ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(10.dp)) {
                            Text(k.uppercase(), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkedScenariosPanel(
    chips: List<Pair<DirectorScenarioRef, ChipKind>>,
    onChipClick: (Long) -> Unit,
    onLink: () -> Unit
) {
    Text("LINKED SCENARIOS · ${chips.size}",
        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { (scenario, kind) ->
            ScenarioChip(scenario, kind) { onChipClick(scenario.id) }
        }
        Surface(
            shape = CircleShape, color = Color.Transparent,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
            modifier = Modifier.height(32.dp).clickable { onLink() }
        ) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("Link scenario", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ScenarioChip(scenario: DirectorScenarioRef, kind: ChipKind, onClose: () -> Unit) {
    val accent = when (kind) {
        ChipKind.FORKED -> Color(0xFFF3A93B)
        ChipKind.NEW    -> MaterialTheme.colorScheme.primary
        ChipKind.LINKED -> MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = CircleShape,
        color = if (kind == ChipKind.LINKED) MaterialTheme.colorScheme.surface
                else accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
        modifier = Modifier.height(32.dp)
    ) {
        Row(Modifier.padding(start = 12.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(scenario.name, style = MaterialTheme.typography.labelLarge)
            if (kind == ChipKind.FORKED) {
                Spacer(Modifier.width(4.dp))
                Text("FORK", style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, color = Color(0xFFF3A93B))
            }
            if (kind == ChipKind.NEW) {
                Spacer(Modifier.width(4.dp))
                Text("NEW", style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Clear, contentDescription = "Remove",
                    modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DialogAction(title: String, sub: String, color: Color, onClick: () -> Unit) {
    Surface(
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = color,
                fontWeight = FontWeight.Medium)
            Text(sub, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A scrollable list-of-rows picker dialog (used for Link scenario and Seed). */
@Composable
private fun PickerDialog(
    title: String,
    subtitle: String,
    rows: List<Pair<String, () -> Unit>>,
    emptyText: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (rows.isEmpty()) {
                    Text(emptyText, style = MaterialTheme.typography.bodyMedium)
                } else {
                    rows.forEach { (label, action) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { action() }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
