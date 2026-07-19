/*
 * Copyright (c) 2026. Tony Finnerty
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.tfcode.comparetout.ui2.dashboard

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.tfcode.comparetout.R
import com.tfcode.comparetout.TOUTCApplication
import com.tfcode.comparetout.ui2.MICBreachInfo
import com.tfcode.comparetout.ui2.UI2DataSourceManagementActivity
import com.tfcode.comparetout.ui2.charts.ExpandableCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

/*
 * Dashboard status/attention surfaces — extracted verbatim from
 * UI2DashboardFragment.kt (mega-refactor B1b).
 */

internal val StatusGreen = Color(0xFF0B8043)
internal val StatusRed = Color(0xFFD32F2F)
internal val StatusAmber = Color(0xFFF9A825)

// ── Simulation readiness ────────────────────────────────────────────────────
// A scenario's dashboard is only fully meaningful once it has usage data, PV
// data for its panels, and a completed costing run. Until then the individual
// accordions show a red triangle / a missing sun — easy to miss. We collect the
// outstanding reasons into one "Needs attention" accordion at the top so the
// user sees *why* the dashboard looks incomplete and how to fix it.

internal enum class IssueSeverity { WARNING, INFO }

internal data class SimReadinessIssue(
    val title: String,
    val hint: String,
    val severity: IssueSeverity,
    val wizardSection: String? = null   // non-null → row taps through to the wizard
)

@Composable
internal fun simReadinessIssues(
    hasLoadProfile: Boolean,
    hasPanels: Boolean,
    hasPanelData: Boolean,
    hpWeatherMissing: Boolean,
    resultsReady: Boolean
): List<SimReadinessIssue> {
    val issues = mutableListOf<SimReadinessIssue>()
    if (!hasLoadProfile) {
        issues += SimReadinessIssue(
            title = stringResource(R.string.ui2_dash_issue_no_usage_title),
            hint = stringResource(R.string.ui2_dash_issue_no_usage_hint),
            severity = IssueSeverity.WARNING,
            wizardSection = "load"
        )
    }
    if (hasPanels && !hasPanelData) {
        issues += SimReadinessIssue(
            title = stringResource(R.string.ui2_dash_issue_solar_title),
            hint = stringResource(R.string.ui2_dash_issue_solar_hint),
            severity = IssueSeverity.WARNING,
            wizardSection = "pv"
        )
    }
    if (hpWeatherMissing) {
        issues += SimReadinessIssue(
            title = stringResource(R.string.ui2_dash_issue_weather_title),
            hint = stringResource(R.string.ui2_dash_issue_weather_hint),
            severity = IssueSeverity.WARNING,
            wizardSection = "heatpump"
        )
    }
    if (!resultsReady) {
        issues += SimReadinessIssue(
            title = stringResource(R.string.ui2_dash_issue_results_title),
            hint = stringResource(R.string.ui2_dash_issue_results_hint),
            severity = IssueSeverity.INFO
        )
    }
    return issues
}

@Composable
internal fun SimulationStatusAccordion(
    issues: List<SimReadinessIssue>,
    onFix: (String) -> Unit
) {
    if (issues.isEmpty()) return
    val anyWarning = issues.any { it.severity == IssueSeverity.WARNING }
    ExpandableCard(
        title = stringResource(R.string.ui2_dash_needs_attention),
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
                            Text(stringResource(R.string.ui2_dash_tap_wizard),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

// ── Solar-data-needs-refreshing banner ───────────────────────────────────────
// After the millis rollout (PanelDataRefreshWorker, plans/sim/timezone-and-rollout.md), PVGIS panels are
// auto-refetched but source-derived panels can't be — their serials are written to the
// `paneldata_needs_regen_sources` DataStore key. This app-wide banner surfaces them so the user knows to
// re-import those sources (their solar otherwise reads as zero). Dismiss clears the key.
@Composable
internal fun NeedsRegenBanner(refreshKey: Any?) {
    val context = LocalContext.current
    val app = context.applicationContext as TOUTCApplication
    val scope = rememberCoroutineScope()
    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return
    val sources by produceState(initialValue = emptyList<String>(), refreshKey) {
        value = withContext(Dispatchers.IO) {
            runCatching { app.getStringValueFromDataStore("paneldata_needs_regen_sources") }
                .getOrDefault("")
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
    if (sources.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = StatusAmber.copy(alpha = 0.12f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(painterResource(R.drawable.ic_baseline_warning_24), null,
                    Modifier.size(22.dp), tint = StatusAmber)
                Text(stringResource(R.string.ui2_dash_regen_title),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.ui2_dash_regen_body, sources.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    context.startActivity(Intent(context, UI2DataSourceManagementActivity::class.java))
                }) { Text(stringResource(R.string.ui2_dash_manage_sources)) }
                OutlinedButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        runCatching { app.putStringValueIntoDataStore("paneldata_needs_regen_sources", "") }
                    }
                    dismissed = true
                }) { Text(stringResource(R.string.ui2_dismiss)) }
            }
        }
    }
}

// ── One-time data-migration status banner ────────────────────────────────────
// The millis rollout runs two one-time background workers (TimezoneRestampWorker = tz_restamp_v1,
// PanelDataRefreshWorker = paneldata_refresh_v1) from Application.onCreate. This banner gives an IN-APP signal of
// their progress: it shows each one that is still pending/running (with live %), and hides once both are done —
// so an absent banner means "fully migrated". "Done" is taken from the persisted DataStore flags (authoritative
// even after WorkManager prunes finished work) OR a live SUCCEEDED WorkInfo.
@Composable
internal fun MigrationStatusBanner(refreshKey: Any?) {
    val context = LocalContext.current
    val app = context.applicationContext as TOUTCApplication

    val tzInfos by remember(context) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData("tz_restamp_v1")
    }.observeAsState(emptyList())
    val pdInfos by remember(context) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData("paneldata_refresh_v1")
    }.observeAsState(emptyList())

    // Re-read the persisted done flags whenever the dashboard refreshes or a worker's state changes.
    val flags by produceState(initialValue = false to false, refreshKey, tzInfos, pdInfos) {
        value = withContext(Dispatchers.IO) {
            val tz = runCatching { app.getStringValueFromDataStore("tz_restamp_v1_done") }.getOrDefault("")
            val pd = runCatching { app.getStringValueFromDataStore("paneldata_refresh_v1_done") }.getOrDefault("")
            (tz == "true") to (pd == "true")
        }
    }

    // Templates resolved here — statusLine is a plain local fun, not composable.
    val tplPct = stringResource(R.string.ui2_dash_mig_in_progress_pct)
    val tplRunning = stringResource(R.string.ui2_dash_mig_in_progress)
    val tplQueued = stringResource(R.string.ui2_dash_mig_queued)
    val tplPending = stringResource(R.string.ui2_dash_mig_pending)
    fun statusLine(label: String, doneFlag: Boolean, infos: List<WorkInfo>): String? {
        if (doneFlag || infos.any { it.state == WorkInfo.State.SUCCEEDED }) return null  // complete → hide
        val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
        return when {
            running != null -> {
                val pct = running.progress.getInt("pct", -1)
                if (pct >= 0) tplPct.format(label, pct) else tplRunning.format(label)
            }
            infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED } ->
                tplQueued.format(label)
            else -> tplPending.format(label)
        }
    }

    val lines = listOfNotNull(
        statusLine(stringResource(R.string.ui2_dash_mig_tz), flags.first, tzInfos),
        statusLine(stringResource(R.string.ui2_dash_mig_pd), flags.second, pdInfos)
    )
    if (lines.isEmpty()) return  // both migrations complete → nothing to show

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = StatusAmber.copy(alpha = 0.12f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.ui2_dash_mig_title),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            lines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.ui2_dash_mig_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Grid-import (MIC) soft-cap breach flag (item 4c) ─────────────────────────
// Shown in the simulation "Explore data" accordion when the run drew more from the grid than the scenario's
// Maximum Import Capacity in one or more intervals. The model doesn't clamp grid import (the grid will deliver),
// so this is surfaced as a fault to investigate. Tapping opens the worst breaching times so the user can open
// the graphs for those days. Derived from stored data — no schema change.
@Composable
internal fun MICBreachFlag(info: MICBreachInfo, showHints: Boolean) {
    var showDialog by remember { mutableStateOf(false) }
    val kwDf = remember { DecimalFormat("#,##0.0") }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = StatusAmber.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth().clickable { showDialog = true }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(painterResource(R.drawable.ic_baseline_warning_24), null, Modifier.size(18.dp), tint = StatusAmber)
            Column(Modifier.weight(1f)) {
                Text(pluralStringResource(R.plurals.ui2_dash_mic_flag, info.count, info.count),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.ui2_dash_mic_tap),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (showHints) {
        Text(
            stringResource(R.string.ui2_dash_mic_hint, kwDf.format(info.micKw)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }

    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.ui2_dash_mic_title),
                        style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        pluralStringResource(R.plurals.ui2_dash_mic_body, info.count,
                            kwDf.format(info.micKw), info.count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.ui2_dash_mic_worst),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Column(
                        modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        info.sample.forEach { b ->
                            val hh = b.minuteOfDay / 60
                            val mm = b.minuteOfDay % 60
                            Text(stringResource(R.string.ui2_dash_mic_row,
                                    b.date, hh, mm, kwDf.format(b.kw), kwDf.format(info.micKw)),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (info.count > info.sample.size) {
                            Text(stringResource(R.string.ui2_dash_more_n,
                                    info.count - info.sample.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showDialog = false }) {
                            Text(stringResource(R.string.ui2_close))
                        }
                    }
                }
            }
        }
    }
}
