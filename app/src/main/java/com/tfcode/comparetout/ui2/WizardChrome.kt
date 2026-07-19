package com.tfcode.comparetout.ui2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.R
import com.tfcode.comparetout.SimulatorLauncher
import com.tfcode.comparetout.TOUTCApplication
import com.tfcode.comparetout.model.json.scenario.ScenarioJsonFile
import com.tfcode.comparetout.model.scenario.PanelPVSummary
import com.tfcode.comparetout.model.scenario.Scenario
import com.tfcode.comparetout.scenario.HeatPumpWeatherCache
import com.tfcode.comparetout.scenario.loadprofile.StandardLoadProfiles
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/*
 * Wizard chrome: import-scope enum, progress strip, hint banner, accordion
 * shell, schedule-overflow gate, footer — extracted verbatim from
 * UI2WizardActivity.kt (mega-refactor B2a). Imports inherited; unused are cosmetic.
 */

/** Each per-accordion Import button writes one of these into the wizard's
 *  scaffold state to open the matching sheet. */
internal enum class WizardImportScope { USAGE, INVERTERS, PV, BATTERY, HW, EV, HEATPUMP }

/* ──────────────────────────────────────────────────────────────────
   Progress strip
────────────────────────────────────────────────────────────────── */

@Composable
internal fun WizardProgressStrip(builder: WizardBuilder, uiVis: UiVisibility) {
    // One entry per wizard section. `visible` mirrors the accordion gating in the
    // form (Start + Usage are always shown; the rest follow App-settings component
    // visibility). The denominator counts only VISIBLE sections, while the
    // numerator counts every DEFINED section — so a component that is defined but
    // hidden still contributes, giving e.g. "7 of 5".
    data class SectionState(val complete: Boolean, val visible: Boolean)
    val sections = listOf(
        SectionState(builder.isStartComplete, true),
        SectionState(builder.isLoadComplete, true),
        SectionState(builder.inverterEntries.isNotEmpty(), uiVis.inverter),
        SectionState(builder.panelEntries.isNotEmpty(), uiVis.panels),
        SectionState(builder.batteryEntries.isNotEmpty(), uiVis.battery),
        SectionState(builder.hwSystem != null || builder.hwSchedules.isNotEmpty() || builder.hwDivert.active, uiVis.hotWater),
        SectionState(builder.evEntries.isNotEmpty() || builder.evDivertEntries.isNotEmpty(), uiVis.ev),
        SectionState(builder.heatPumpEntries.isNotEmpty(), uiVis.heatPump)
    )
    val done = sections.count { it.complete }
    val total = sections.count { it.visible }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            sections.filter { it.visible }.forEach { section ->
                Box(modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (section.complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.ui2_wiz_n_of_m, done, total),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ──────────────────────────────────────────────────────────────────
   Hint banner
────────────────────────────────────────────────────────────────── */

@Composable
internal fun WizardHintBanner(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp).padding(top = 1.dp),
            tint = MaterialTheme.colorScheme.secondary)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/* ──────────────────────────────────────────────────────────────────
   Generic accordion section
────────────────────────────────────────────────────────────────── */

@Composable
internal fun WizardAccordionSection(
    id: String,
    iconContent: @Composable () -> Unit,
    title: String,
    isLinked: Boolean,
    subtitle: String?,
    isComplete: Boolean,
    isLocked: Boolean,
    lockedHint: String? = null,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = if (isExpanded)
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            // Stable selector for Play Console Robo / FTL automation: each accordion
            // toggle is matched by `contentDescription="Wizard section: $title"`. Robo
            // can't see Compose testTags, so this is the canonical way to expose the
            // toggle. See plans/roboscript/robo-plan.md (Phase 4A).
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
                .semantics {
                    contentDescription = "Wizard section: $title"
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.width(42.dp).height(32.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                iconContent()
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (isLinked) {
                        Icon(Icons.Default.Link,
                            contentDescription = stringResource(R.string.ui2_wiz_linked_cd),
                            modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                isComplete -> Icon(Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.ui2_ppw_complete_cd),
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                isLocked   -> Icon(Icons.Default.Lock,
                    contentDescription = stringResource(R.string.ui2_wiz_locked_cd),
                    tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
            }
            Icon(imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                if (isLocked && lockedHint != null) {
                    Row(modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                        .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Text(lockedHint, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                content()
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
   Schedule overflow gate
────────────────────────────────────────────────────────────────── */

/** Lists longer than this collapse behind a count + "Show all" prompt. */
internal const val SCHEDULE_OVERFLOW_THRESHOLD = 5

/**
 * A dynamic-tariff optimised scenario can carry dozens of schedule rows. When a
 * list has more than [SCHEDULE_OVERFLOW_THRESHOLD] entries, collapse it behind a
 * "N <noun> — Show all" prompt so the accordion stays scannable; [content] (the
 * cards) is only composed once the user opts in.
 *
 * Adding an entry (the count grows) auto-expands the list so the new row is
 * visible and editable rather than hidden — only a fresh load of an
 * already-large list starts collapsed. The wrapper is always present (even at or
 * below the threshold) so this state survives crossing the threshold via an add.
 */
@Composable
internal fun ScheduleOverflowGate(
    count: Int,
    noun: String,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var previousCount by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        if (count > previousCount) expanded = true
        previousCount = count
    }
    val collapsible = count > SCHEDULE_OVERFLOW_THRESHOLD
    if (collapsible) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.ui2_wiz_sched_overflow_count, count, noun),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(if (expanded) R.string.ui2_wiz_sched_hide
                                   else R.string.ui2_wiz_sched_show_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
    if (!collapsible || expanded) content()
}

/* ──────────────────────────────────────────────────────────────────
   Footer
────────────────────────────────────────────────────────────────── */

internal sealed class SimButtonState {
    object Idle    : SimButtonState()
    object Queued  : SimButtonState()
    object Running : SimButtonState()
    object Done    : SimButtonState()
    object Failed  : SimButtonState()
}

@Composable
internal fun WizardFooter(
    canRun: Boolean,
    canSave: Boolean,
    isSaving: Boolean,
    simButtonState: SimButtonState,
    showSavedTick: Boolean,
    noviceMode: Boolean,
    onSave: () -> Unit,
    onRun: () -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (noviceMode) {
                Text(stringResource(R.string.ui2_wiz_run_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.ui2_wiz_run_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                // Gate Save on the name check too (not just Run): saving a duplicate name violates the
                // scenarios.scenarioName UNIQUE index, which the DAO swallows into a bogus id and then NPEs
                // in savePanel — the whole save fails silently. Blocking it here is the user-visible fix.
                onClick = onSave,
                enabled = canSave && !isSaving,
                modifier = Modifier.weight(1f)
            ) {
                if (showSavedTick) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.ui2_ppw_saved))
                } else {
                    Text(stringResource(R.string.ui2_save))
                }
            }
            Button(
                onClick = onRun,
                enabled = canRun && !isSaving &&
                    simButtonState != SimButtonState.Queued &&
                    simButtonState != SimButtonState.Running
            ) {
                when {
                    isSaving -> Text(stringResource(R.string.ui2_ppw_saving))
                    simButtonState == SimButtonState.Running -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ui2_wiz_running))
                    }
                    simButtonState == SimButtonState.Done -> {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ui2_done))
                    }
                    simButtonState == SimButtonState.Failed ->
                        Text(stringResource(R.string.ui2_wiz_failed_retry))
                    simButtonState == SimButtonState.Queued -> {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ui2_wiz_queued))
                    }
                    else -> Text(stringResource(R.string.ui2_director_run_sim))
                }
            }
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.ui2_close))
            }
        }
        }
    }
}

