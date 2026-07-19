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
 * Wizard per-accordion import sheet + JSON slice parsers — extracted verbatim
 * from UI2WizardActivity.kt (mega-refactor B2g; same package by design, see
 * plans/source/mega-refactor-status.md). Imports inherited; unused are cosmetic.
 */

/**
 * Per-accordion import sheet. Each [scope] selects the parser and apply
 * lambda. Every parser also accepts a *whole* [ScenarioJsonFile] paste and
 * slices the relevant keys — so a single scenario JSON can feed any accordion
 * one at a time.
 */
@Composable
internal fun WizardImportSheet(
    scope: WizardImportScope,
    onDismiss: () -> Unit,
    onApplied: () -> Unit,
    viewModel: UI2WizardViewModel
) {
    val context = LocalContext.current
    when (scope) {
        WizardImportScope.USAGE -> UI2ImportSheet(
            title = stringResource(R.string.ui2_wiz_imp_load_title),
            hint = stringResource(R.string.ui2_wiz_imp_load_hint),
            parse = { parseLoadProfileImport(context, it) },
            onApply = { viewModel.replaceLoadProfileFromJson(it); onApplied() },
            onDismiss = onDismiss
        )
        WizardImportScope.INVERTERS -> UI2ImportSheet(
            title = stringResource(R.string.ui2_wiz_imp_inv_title),
            hint = stringResource(R.string.ui2_wiz_imp_inv_hint),
            parse = { parseInvertersImport(context, it) },
            onApply = { viewModel.replaceInvertersFromJson(it); onApplied() },
            onDismiss = onDismiss
        )
        WizardImportScope.PV -> UI2ImportSheet(
            title = stringResource(R.string.ui2_wiz_imp_pv_title),
            hint = stringResource(R.string.ui2_wiz_imp_pv_hint),
            parse = { parsePanelsImport(context, it) },
            onApply = { viewModel.replacePanelsFromJson(it); onApplied() },
            onDismiss = onDismiss
        )
        WizardImportScope.BATTERY -> UI2ImportSheet(
            title = stringResource(R.string.ui2_wiz_imp_batt_title),
            hint = stringResource(R.string.ui2_wiz_imp_batt_hint),
            parse = { parseBatteryImport(context, it) },
            onApply = {
                viewModel.replaceBatteryGroupFromJson(it.batteries, it.loadShifts, it.discharges)
                onApplied()
            },
            onDismiss = onDismiss
        )
        WizardImportScope.HW -> UI2ImportSheet(
            title = stringResource(R.string.ui2_wiz_imp_hw_title),
            hint = stringResource(R.string.ui2_wiz_imp_hw_hint),
            parse = { parseHwImport(context, it) },
            onApply = {
                viewModel.replaceHwGroupFromJson(it.system, it.schedules, it.divert)
                onApplied()
            },
            onDismiss = onDismiss
        )
        WizardImportScope.EV -> UI2ImportSheet(
            title = stringResource(R.string.ui2_wiz_imp_ev_title),
            hint = stringResource(R.string.ui2_wiz_imp_ev_hint),
            parse = { parseEvImport(context, it) },
            onApply = {
                viewModel.replaceEvGroupFromJson(it.charges, it.diverts, it.legacyDivert)
                onApplied()
            },
            onDismiss = onDismiss
        )
        WizardImportScope.HEATPUMP -> UI2ImportSheet(
            title = stringResource(R.string.ui2_wiz_imp_hp_title),
            hint = stringResource(R.string.ui2_wiz_imp_hp_hint),
            parse = { parseHeatPumpImport(context, it) },
            onApply = { viewModel.replaceHeatPumpsFromJson(it); onApplied() },
            onDismiss = onDismiss
        )
    }
}

/**
 * Try to deserialise [text] as either [target] directly, or as a whole
 * [ScenarioJsonFile] from which [extract] pulls the relevant slice. Returns
 * null if both attempts fail. Both routes are wrapped in their own
 * `runCatching` so a JSON shape that decodes into one but not the other still
 * succeeds.
 */
internal inline fun <reified Target, R> tryParseSliceOrScenario(
    text: String,
    extract: (ScenarioJsonFile) -> R?,
    decodeTarget: (String) -> Target?,
    convertTarget: (Target) -> R?
): R? {
    runCatching {
        decodeTarget(text)?.let { convertTarget(it) }
    }.getOrNull()?.let { return it }
    runCatching {
        Gson().fromJson(text, ScenarioJsonFile::class.java)?.let(extract)
    }.getOrNull()?.let { return it }
    return null
}

internal fun parseLoadProfileImport(context: android.content.Context, text: String): ParsedPreview<com.tfcode.comparetout.model.json.scenario.LoadProfileJson> = try {
    val gson = Gson()
    val lp = tryParseSliceOrScenario(
        text,
        extract = { it.loadProfile },
        decodeTarget = { gson.fromJson(it, com.tfcode.comparetout.model.json.scenario.LoadProfileJson::class.java) },
        convertTarget = { it.takeIf { lp -> lp.annualUsage != null || lp.hourlyDistribution != null } }
    )
    if (lp == null) ParsedPreview.Err(context.getString(R.string.ui2_wiz_parse_no_load))
    else ParsedPreview.Ok(lp,
        context.getString(R.string.ui2_wiz_parse_load_ok,
            "${lp.annualUsage ?: 0.0}", "${lp.hourlyBaseLoad ?: 0.0}"))
} catch (e: Throwable) {
    ParsedPreview.Err(e.message ?: context.getString(R.string.ui2_parse_failed))
}

internal fun parseInvertersImport(context: android.content.Context, text: String): ParsedPreview<List<com.tfcode.comparetout.model.json.scenario.InverterJson>> = try {
    val gson = Gson()
    val listType = object : TypeToken<List<com.tfcode.comparetout.model.json.scenario.InverterJson>>() {}.type
    val raw = tryParseSliceOrScenario(
        text,
        extract = { it.inverters?.toList() },
        decodeTarget = { gson.fromJson<List<com.tfcode.comparetout.model.json.scenario.InverterJson>?>(it, listType) },
        convertTarget = { it.takeIf { l -> l.isNotEmpty() } }
    )
    if (raw.isNullOrEmpty()) ParsedPreview.Err(context.getString(R.string.ui2_wiz_parse_no_inverters))
    else ParsedPreview.Ok(raw, context.resources.getQuantityString(
        R.plurals.ui2_wiz_parse_n_inverters, raw.size, raw.size))
} catch (e: Throwable) {
    ParsedPreview.Err(e.message ?: context.getString(R.string.ui2_parse_failed))
}

internal fun parsePanelsImport(context: android.content.Context, text: String): ParsedPreview<List<com.tfcode.comparetout.model.json.scenario.PanelJson>> = try {
    val gson = Gson()
    val listType = object : TypeToken<List<com.tfcode.comparetout.model.json.scenario.PanelJson>>() {}.type
    val raw = tryParseSliceOrScenario(
        text,
        extract = { it.panels?.toList() },
        decodeTarget = { gson.fromJson<List<com.tfcode.comparetout.model.json.scenario.PanelJson>?>(it, listType) },
        convertTarget = { it.takeIf { l -> l.isNotEmpty() } }
    )
    if (raw.isNullOrEmpty()) ParsedPreview.Err(context.getString(R.string.ui2_wiz_parse_no_panels))
    else ParsedPreview.Ok(raw, context.resources.getQuantityString(
        R.plurals.ui2_wiz_parse_n_panels, raw.size, raw.size))
} catch (e: Throwable) {
    ParsedPreview.Err(e.message ?: context.getString(R.string.ui2_parse_failed))
}

internal fun parseHeatPumpImport(context: android.content.Context, text: String): ParsedPreview<List<com.tfcode.comparetout.model.json.scenario.HeatPumpJson>> = try {
    val gson = Gson()
    val listType = object : TypeToken<List<com.tfcode.comparetout.model.json.scenario.HeatPumpJson>>() {}.type
    val raw = tryParseSliceOrScenario(
        text,
        extract = { it.heatPumps?.toList() },
        decodeTarget = { gson.fromJson<List<com.tfcode.comparetout.model.json.scenario.HeatPumpJson>?>(it, listType) },
        convertTarget = { it.takeIf { l -> l.isNotEmpty() && l.any { hp -> hp.fuelAnnual != null } } }
    )
    if (raw.isNullOrEmpty()) ParsedPreview.Err(context.getString(R.string.ui2_wiz_parse_no_hp))
    else ParsedPreview.Ok(raw, context.getString(R.string.ui2_wiz_parse_hp_ok, raw.first().fuelType ?: "?"))
} catch (e: Throwable) {
    ParsedPreview.Err(e.message ?: context.getString(R.string.ui2_parse_failed))
}

/** Carries the three battery-related lists together since they cross-reference. */
internal data class BatteryImportSlice(
    val batteries: List<com.tfcode.comparetout.model.json.scenario.BatteryJson>?,
    val loadShifts: List<com.tfcode.comparetout.model.json.scenario.LoadShiftJson>?,
    val discharges: List<com.tfcode.comparetout.model.json.scenario.DischargeToGridJson>?
)

internal fun parseBatteryImport(context: android.content.Context, text: String): ParsedPreview<BatteryImportSlice> = try {
    val scenario = runCatching { Gson().fromJson(text, ScenarioJsonFile::class.java) }.getOrNull()
    val slice = scenario?.let {
        BatteryImportSlice(it.batteries?.toList(), it.loadShifts?.toList(), it.dischargeToGrids?.toList())
    }
    if (slice == null || (slice.batteries.isNullOrEmpty()
            && slice.loadShifts.isNullOrEmpty()
            && slice.discharges.isNullOrEmpty())) {
        ParsedPreview.Err(context.getString(R.string.ui2_wiz_parse_no_battery))
    } else {
        val battCount = slice.batteries?.size ?: 0
        val shiftCount = slice.loadShifts?.size ?: 0
        val dischCount = slice.discharges?.size ?: 0
        ParsedPreview.Ok(slice, context.resources.getQuantityString(
            R.plurals.ui2_wiz_parse_batt_ok, battCount, battCount, shiftCount, dischCount))
    }
} catch (e: Throwable) {
    ParsedPreview.Err(e.message ?: context.getString(R.string.ui2_parse_failed))
}

internal data class HwImportSlice(
    val system: com.tfcode.comparetout.model.json.scenario.HWSystemJson?,
    val schedules: List<com.tfcode.comparetout.model.json.scenario.HWScheduleJson>?,
    val divert: com.tfcode.comparetout.model.json.scenario.HWDivertJson?
)

internal fun parseHwImport(context: android.content.Context, text: String): ParsedPreview<HwImportSlice> = try {
    val scenario = runCatching { Gson().fromJson(text, ScenarioJsonFile::class.java) }.getOrNull()
    val slice = scenario?.let {
        HwImportSlice(it.hwSystem, it.hwSchedules?.toList(), it.hwDivert)
    }
    if (slice == null || (slice.system == null && slice.schedules.isNullOrEmpty() && slice.divert == null)) {
        ParsedPreview.Err(context.getString(R.string.ui2_wiz_parse_no_hw))
    } else {
        val schedCount = slice.schedules?.size ?: 0
        // Pick the with/without-system variant, then append the self-contained
        // divert fragment — same restructuring pattern as the timezone status suffix.
        val base = context.resources.getQuantityString(
            if (slice.system != null) R.plurals.ui2_wiz_parse_hw_sys else R.plurals.ui2_wiz_parse_hw_only,
            schedCount, schedCount)
        ParsedPreview.Ok(slice,
            base + (if (slice.divert?.active == true)
                context.getString(R.string.ui2_wiz_parse_divert_active) else ""))
    }
} catch (e: Throwable) {
    ParsedPreview.Err(e.message ?: context.getString(R.string.ui2_parse_failed))
}

internal data class EvImportSlice(
    val charges: List<com.tfcode.comparetout.model.json.scenario.EVChargeJson>?,
    val diverts: List<com.tfcode.comparetout.model.json.scenario.EVDivertJson>?,
    val legacyDivert: com.tfcode.comparetout.model.json.scenario.EVDivertJson?
)

internal fun parseEvImport(context: android.content.Context, text: String): ParsedPreview<EvImportSlice> = try {
    val scenario = runCatching { Gson().fromJson(text, ScenarioJsonFile::class.java) }.getOrNull()
    val slice = scenario?.let {
        EvImportSlice(it.evCharges?.toList(), it.evDiverts?.toList(), it.evDivert)
    }
    if (slice == null || (slice.charges.isNullOrEmpty() && slice.diverts.isNullOrEmpty() && slice.legacyDivert == null)) {
        ParsedPreview.Err(context.getString(R.string.ui2_wiz_parse_no_ev))
    } else {
        val chargeCount = slice.charges?.size ?: 0
        val divertCount = (slice.diverts?.size ?: 0) + (if (slice.legacyDivert != null) 1 else 0)
        ParsedPreview.Ok(slice,
            context.resources.getQuantityString(
                R.plurals.ui2_wiz_parse_ev_charges, chargeCount, chargeCount) + " · " +
                context.resources.getQuantityString(
                    R.plurals.ui2_wiz_parse_ev_diverts, divertCount, divertCount))
    }
} catch (e: Throwable) {
    ParsedPreview.Err(e.message ?: context.getString(R.string.ui2_parse_failed))
}

/**
 * Parse text that the user pasted (or file content read) into a list of
 * scenarios. Accepts both the bulk-export shape (`[…]`) and the single-object
 * shape produced by Phase A's per-scenario Share button (`{…}`). Returns a
 * [ParsedPreview.Err] for malformed input — the sheet renders it in red.
 */
internal fun parseScenarioImportJson(context: android.content.Context, text: String): ParsedPreview<List<ScenarioJsonFile>> = try {
    val gson = Gson()
    val trimmed = text.trimStart()
    val raw: List<ScenarioJsonFile>? = if (trimmed.startsWith("[")) {
        val type = object : TypeToken<List<ScenarioJsonFile>>() {}.type
        gson.fromJson<List<ScenarioJsonFile>?>(text, type)
    } else {
        gson.fromJson(text, ScenarioJsonFile::class.java)?.let { listOf(it) }
    }
    val valid = (raw ?: emptyList()).filter { !it.name.isNullOrBlank() }
    if (valid.isEmpty()) ParsedPreview.Err(context.getString(R.string.ui2_parse_no_scenarios))
    else ParsedPreview.Ok(
        valid,
        when (valid.size) {
            1 -> {
                val s = valid.first()
                val inv = s.inverters?.size ?: 0
                val panel = s.panels?.size ?: 0
                val batt = s.batteries?.size ?: 0
                context.getString(R.string.ui2_wiz_parse_one_scenario, s.name, inv, panel, batt)
            }
            else -> context.getString(R.string.ui2_wiz_parse_n_scenarios, valid.size)
        }
    )
} catch (e: JsonSyntaxException) {
    ParsedPreview.Err(e.message ?: context.getString(R.string.ui2_parse_malformed))
} catch (e: Throwable) {
    ParsedPreview.Err(e.message ?: context.getString(R.string.ui2_parse_failed))
}
