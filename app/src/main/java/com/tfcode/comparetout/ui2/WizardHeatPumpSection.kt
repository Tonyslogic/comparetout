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
 * Wizard heat-pump section content (fuel anchoring, CDS gating, wind levels)
 * — extracted verbatim from UI2WizardActivity.kt (mega-refactor B2e).
 * Imports inherited; unused are cosmetic.
 */

/* ──────────────────────────────────────────────────────────────────
   Heat Pump section content (Phase 5 of plans/hp/plan.md)
   ────────────────────────────────────────────────────────────────── */

internal fun defaultCalorific(fuel: String): Double = when (fuel) {
    "Natural gas" -> 1.0      // entered in kWh already
    "LPG" -> 7.08
    else -> 10.35             // kerosene / home-heating oil
}

// A sensible default annual use per fuel — switching fuel changes the unit (litres ↔ kWh), so the figure
// is reset to avoid a nonsensical implied result (e.g. 2300 litres carried into a kWh gas field → ~0).
internal fun defaultAnnualUse(fuel: String): String = when (fuel) {
    "Natural gas" -> "11000"  // kWh / yr
    "LPG" -> "2000"           // litres / yr
    else -> "2300"            // litres / yr
}

@Composable
internal fun HeatPumpSectionContent(
    entries: List<WizardHeatPumpEntry>,
    noviceMode: Boolean,
    cdsDateRange: Pair<String, String>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onUpdate: (String, (WizardHeatPumpEntry) -> WizardHeatPumpEntry) -> Unit,
    onImport: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (noviceMode && entries.isEmpty()) {
            Text(stringResource(R.string.ui2_wiz_hp_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        entries.forEach { hp -> HeatPumpCard(hp, noviceMode, cdsDateRange, onRemove, onUpdate) }
        if (entries.isEmpty()) {
            FilledTonalButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ui2_wiz_add_hp))
            }
        }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.ui2_wiz_import_hp_json))
        }
    }
}

@Composable
internal fun HpNumberField(
    label: String, value: String, hint: String?, novice: Boolean,
    modifier: Modifier = Modifier, onValue: (String) -> Unit
) {
    // Raw text in state, parsed on save — matches every other wizard input box.
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        supportingText = if (novice && hint != null) ({ Text(hint) }) else null
    )
}

@Composable
internal fun HpDoubleField(
    label: String, value: Double, hint: String?, novice: Boolean,
    modifier: Modifier = Modifier, onValue: (Double) -> Unit
) {
    // lat/long are Doubles in the entry (not String like the other fields). Keep the raw text locally so a
    // partial edit ("-", "53.") doesn't fight the committed Double; commit only on a valid parse.
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; it.toDoubleOrNull()?.let(onValue) },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        supportingText = if (novice && hint != null) ({ Text(hint) }) else null
    )
}

/**
 * True if a CDS Personal Access Token is stored (and decryptable). Mirrors the
 * gate in [UI2DataSourceManagementViewModel] (DataStore key "cds_key"); the
 * literal key is duplicated here because the VM's constant is file-private.
 * Blocking DataStore read — call from a background dispatcher.
 */
internal fun cdsCredentialsPresent(context: android.content.Context): Boolean {
    val app = context.applicationContext as TOUTCApplication
    val raw = app.getStringValueFromDataStore("cds_key")
    if (raw.isNullOrEmpty()) return false
    return runCatching { TOUTCApplication.decryptString(raw) }.getOrNull()?.isNotEmpty() == true
}

/**
 * Plain-language wind-sensitivity levels for the "Heat energy required" sub-section. Each maps to the model's
 * `alphaWind` (wind-infiltration coefficient): higher ⇒ a draughtier home, which concentrates heat
 * demand onto cold + windy peaks and pushes more onto the expensive backup heater. The default
 * `0.03` lands in [WIND_LEVELS]`[1]` so existing scenarios open unchanged.
 */
internal data class WindLevel(val labelRes: Int, val shortRes: Int, val alphaWind: Double)
internal val WIND_LEVELS = listOf(
    WindLevel(R.string.ui2_wiz_wind_same, R.string.ui2_wiz_wind_same_short, 0.0),
    WindLevel(R.string.ui2_wiz_wind_cools, R.string.ui2_wiz_wind_cools_short, 0.04),
    WindLevel(R.string.ui2_wiz_wind_drafts, R.string.ui2_wiz_wind_drafts_short, 0.10)
)
/** Snap a stored `alphaWind` to the nearest [WindLevel] (bucket midpoints 0.02 / 0.07). */
internal fun windLevelFor(alphaWind: Double): WindLevel = when {
    alphaWind < 0.02 -> WIND_LEVELS[0]
    alphaWind < 0.07 -> WIND_LEVELS[1]
    else             -> WIND_LEVELS[2]
}

/**
 * Representative annual heating degree-days for Ireland at a 15.5 °C base — used **only** for the card's
 * live "≈ kWh" headline on a new build (`area × HLI × 24 × HDD ÷ 1000`). The simulation itself derives the
 * real HDD from the chosen weather series, so this constant just makes the on-screen estimate sensible.
 */
internal const val IRELAND_HDD_15_5 = 2200.0

/**
 * The (start, end) ISO dates the heat-pump CDS weather query will use — the wizard-side mirror of
 * `HeatPumpWeatherCache.pvSourcePeriod`: a historical PV "Source" range drives the dates, otherwise the 2001
 * reference year. min-start / max-end across any sourced strings (PVGIS/None panels stay on 2001).
 */
internal fun cdsQueryDates(panels: List<WizardPanelEntry>): Pair<String, String> {
    val sourced = panels.filter {
        it.pvDataSource == PanelDataSource.SOURCE &&
            it.pvSourceFrom.isNotBlank() && it.pvSourceTo.isNotBlank() &&
            !(it.pvSourceFrom == "2001-01-01" && it.pvSourceTo == "2001-12-31")
    }
    if (sourced.isEmpty()) return "2001-01-01" to "2001-12-31"
    return sourced.minOf { it.pvSourceFrom } to sourced.maxOf { it.pvSourceTo }
}

/**
 * A lightweight expandable sub-section used inside [HeatPumpCard] (Heat energy required / HP characteristics
 * / Location & weather). Deliberately not [WizardAccordionSection], which is the heavy top-level card.
 */
@Composable
internal fun HpSubSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                // Summary of the section's current values, shown only while collapsed.
                if (!expanded && subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                content()
            }
        }
    }
}

@Composable
internal fun HeatPumpCard(
    hp: WizardHeatPumpEntry,
    novice: Boolean,
    cdsDateRange: Pair<String, String>,
    onRemove: (String) -> Unit,
    onUpdate: (String, (WizardHeatPumpEntry) -> WizardHeatPumpEntry) -> Unit
) {
    fun update(fn: (WizardHeatPumpEntry) -> WizardHeatPumpEntry) = onUpdate(hp.id, fn)

    // CDS weather is gated on credentials configured in Data Source Management.
    // We re-check on each tap (not a cached flag) so credentials set during a
    // round-trip to that screen are picked up without re-entering the wizard.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCdsAlert by remember { mutableStateOf(false) }

    // Basic/Advanced tab (mirrors the Hot-Water card): keeps advanced inputs + their hints out of the
    // novice's way. Independent of noviceMode, which only governs the basic-field hints.
    var showAdvanced by remember { mutableStateOf(false) }
    // Three single-open sub-sections, none open by default.
    var openSection by remember { mutableStateOf("") }
    fun toggle(id: String) { openSection = if (openSection == id) "" else id }
    val newBuild = hp.fuelType == "None"

    // Shared derived values — also feed the collapsed sub-section summaries.
    val scopN = hp.scop.toDoubleOrNull() ?: 3.6
    val estHeat = if (newBuild) {
        // Fabric estimate for the headline only: area·HLI·24·HDD/1000 with a representative Irish HDD.
        // The simulation derives the real HDD from the chosen weather, so this is a ballpark.
        val area = hp.floorAreaM2.toDoubleOrNull() ?: 0.0
        val hli = hp.heatLossIndex.toDoubleOrNull() ?: 0.0
        area * hli * 24.0 * IRELAND_HDD_15_5 / 1000.0
    } else {
        val fuelN = hp.fuelAnnual.toDoubleOrNull() ?: 0.0
        val effN = (hp.boilerEfficiencyPct.toDoubleOrNull() ?: 80.0) / 100.0
        val gross = fuelN * hp.calorificValue * effN
        val frac = hp.spaceHeatingPct.toDoubleOrNull()?.takeIf { it > 0.0 }?.let { it / 100.0 }
        frac?.let { gross * it } ?: (gross - hp.dhwAnnualKWh).coerceAtLeast(0.0)
    }
    val fuelShort = when (hp.fuelType) {
        "Kerosene/Oil" -> stringResource(R.string.ui2_wiz_fuel_kerosene)
        "Natural gas" -> stringResource(R.string.ui2_wiz_fuel_gas)
        "LPG" -> "LPG"
        "None" -> stringResource(R.string.ui2_wiz_fuel_newbuild)
        else -> hp.fuelType
    }
    val weatherCached = remember(hp.latitude, hp.longitude, hp.weatherSource) {
        hp.weatherSource == "cds" &&
            HeatPumpWeatherCache.hasAnyCacheForLocation(context, hp.latitude, hp.longitude)
    }
    val heatSummary = "$fuelShort · ≈ ${estHeat.roundToInt()} kWh/yr"
    val hpSummary = "${hp.capacityKw} kW · SCOP ${hp.scop}"
    val weatherSummary = if (hp.weatherSource == "cds")
        stringResource(R.string.ui2_wiz_cds) +
            (if (weatherCached) " · " + stringResource(R.string.ui2_wiz_cached) else "")
    else stringResource(R.string.ui2_dash_weather_2001)

    // Per-section novice hints — shown above each sub-accordion (matches the other wizard sections).
    val heatHint = stringResource(R.string.ui2_wiz_heat_hint)
    val hpHint = stringResource(R.string.ui2_wiz_hp_char_hint)
    val weatherHint = stringResource(R.string.ui2_wiz_weather_hint)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        PrimaryTabRow(selectedTabIndex = if (showAdvanced) 1 else 0) {
            Tab(selected = !showAdvanced, onClick = { showAdvanced = false },
                text = { Text(stringResource(R.string.ui2_cmp_basic)) })
            Tab(selected = showAdvanced, onClick = { showAdvanced = true },
                text = { Text(stringResource(R.string.ui2_cmp_advanced)) })
        }

        // ── 1) Heat energy required — what the home needs (fuel history or new-build fabric) ──
        if (novice) {
            Text(heatHint, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HpSubSection(stringResource(R.string.ui2_wiz_heat_required),
            openSection == "heat", { toggle("heat") },
            subtitle = heatSummary) {
            // Fuel type (or None for a new build with no boiler history). AdaptiveChipRow gives short labels in
            // portrait, full labels on wide screens, and a dropdown at large font — all managed centrally.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.ui2_wiz_current_fuel),
                    style = MaterialTheme.typography.labelMedium)
                // Resolved outside the label lambdas — AdaptiveChipRow's label
                // callbacks are not composable. fuelType values stay literal
                // (persisted data).
                val keroShort = stringResource(R.string.ui2_wiz_fuel_kero_short)
                val gasShort = stringResource(R.string.ui2_wiz_fuel_gas)
                val noneWord = stringResource(R.string.ui2_wiz_none)
                val noneLong = stringResource(R.string.ui2_wiz_none_newbuild)
                AdaptiveChipRow(
                    items = listOf("Kerosene/Oil", "Natural gas", "LPG", "None"),
                    isSelected = { it == hp.fuelType },
                    onSelect = { fuel ->
                        if (fuel == "None") update { it.copy(fuelType = "None", alphaWind = 0.0) }
                        else update { it.copy(
                            fuelType = fuel,
                            calorificValue = defaultCalorific(fuel),
                            fuelAnnual = defaultAnnualUse(fuel)
                        ) }
                    },
                    label = { when (it) { "Kerosene/Oil" -> keroShort; "Natural gas" -> gasShort; "LPG" -> "LPG"; else -> noneWord } },
                    labelLong = { if (it == "None") noneLong else it },
                    shortItemWidth = 60.dp, longItemWidth = 112.dp
                )
                if (newBuild) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HpNumberField(stringResource(R.string.ui2_wiz_floor_area), hp.floorAreaM2,
                            stringResource(R.string.ui2_wiz_floor_area_hint),
                            novice, Modifier.weight(1f)) { v -> update { it.copy(floorAreaM2 = v) } }
                        HpNumberField(stringResource(R.string.ui2_wiz_hli), hp.heatLossIndex,
                            stringResource(R.string.ui2_wiz_hli_hint),
                            novice, Modifier.weight(1f)) { v -> update { it.copy(heatLossIndex = v) } }
                    }
                } else {
                    val unit = stringResource(if (hp.fuelType == "Natural gas")
                        R.string.ui2_wiz_kwh_yr_unit else R.string.ui2_wiz_litres_yr)
                    HpNumberField(stringResource(R.string.ui2_wiz_annual_fuel, unit), hp.fuelAnnual,
                        stringResource(R.string.ui2_wiz_annual_fuel_hint),
                        novice, Modifier.fillMaxWidth()) { v -> update { it.copy(fuelAnnual = v) } }
                }
            }

            // ── Implied result (always on — a live sanity-check, not a hint) ──
            val elec = if (scopN > 0) estHeat / scopN else 0.0
            Box(Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.ui2_wiz_implied,
                            estHeat.roundToInt(), elec.roundToInt(), fmtNum(scopN)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary)
                    if (novice) {
                        Text(stringResource(R.string.ui2_wiz_ballpark),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HpNumberField(stringResource(R.string.ui2_wiz_desired_temp), hp.desiredIndoorTemp,
                stringResource(R.string.ui2_wiz_desired_temp_hint), novice, Modifier.fillMaxWidth()) {
                    v -> update { it.copy(desiredIndoorTemp = v) }
            }

            // ── Advanced (demand) — hidden until the Advanced tab is selected ──
            if (showAdvanced) {
                if (!newBuild) {
                    HpNumberField(stringResource(R.string.ui2_wiz_space_heating), hp.spaceHeatingPct,
                        stringResource(R.string.ui2_wiz_space_heating_hint),
                        novice, Modifier.fillMaxWidth()) { v -> update { it.copy(spaceHeatingPct = v) } }
                    HpNumberField(stringResource(R.string.ui2_wiz_boiler_eff), hp.boilerEfficiencyPct,
                        stringResource(R.string.ui2_wiz_boiler_eff_hint),
                        novice, Modifier.fillMaxWidth()) { v -> update { it.copy(boilerEfficiencyPct = v) } }
                }
                HpNumberField(stringResource(R.string.ui2_wiz_current_temp), hp.currentIndoorTemp,
                    stringResource(R.string.ui2_wiz_current_temp_hint),
                    novice, Modifier.fillMaxWidth()) { v -> update { it.copy(currentIndoorTemp = v) } }
                HpNumberField(stringResource(R.string.ui2_wiz_balance_point), hp.balancePoint,
                    stringResource(R.string.ui2_wiz_balance_point_hint),
                    novice, Modifier.fillMaxWidth()) { v -> update { it.copy(balancePoint = v) } }
            }
        }

        // ── 2) HP characteristics — the unit itself ──
        if (novice) {
            Text(hpHint, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HpSubSection(stringResource(R.string.ui2_wiz_hp_characteristics),
            openSection == "hp", { toggle("hp") },
            subtitle = hpSummary) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HpNumberField(stringResource(R.string.ui2_wiz_rated_cop), hp.copRated,
                    stringResource(R.string.ui2_wiz_rated_cop_hint),
                    novice, Modifier.weight(1f)) { v -> update { it.copy(copRated = v) } }
                HpNumberField(stringResource(R.string.ui2_wiz_scop), hp.scop,
                    stringResource(R.string.ui2_wiz_scop_hint),
                    novice, Modifier.weight(1f)) { v -> update { it.copy(scop = v) } }
            }
            HpNumberField(stringResource(R.string.ui2_wiz_capacity), hp.capacityKw,
                stringResource(R.string.ui2_wiz_capacity_hint),
                novice, Modifier.fillMaxWidth()) { v -> update { it.copy(capacityKw = v) } }

            // ── Advanced (HP unit) — hidden until the Advanced tab is selected ──
            if (showAdvanced) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HpNumberField(stringResource(R.string.ui2_wiz_cop_ref), hp.copRefTemp,
                        stringResource(R.string.ui2_wiz_cop_ref_hint),
                        novice, Modifier.weight(1f)) { v -> update { it.copy(copRefTemp = v) } }
                    HpNumberField(stringResource(R.string.ui2_wiz_cop_slope), hp.copSlope,
                        stringResource(R.string.ui2_wiz_cop_slope_hint),
                        novice, Modifier.weight(1f)) { v -> update { it.copy(copSlope = v) } }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = hp.backupHeater, onCheckedChange = { c -> update { it.copy(backupHeater = c) } })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui2_wiz_backup_heater))
                }
            }
        }

        // ── 3) Location & weather ──
        if (novice) {
            Text(weatherHint, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HpSubSection(stringResource(R.string.ui2_wiz_location_weather),
            openSection == "weather", { toggle("weather") },
            subtitle = weatherSummary) {
            // ── Wind sensitivity (maps to alphaWind) — how fast the home loses heat on cold, windy days ──
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.ui2_wiz_wind_blows),
                    style = MaterialTheme.typography.labelMedium)
                // Labels resolved here — AdaptiveChipRow's label callbacks are
                // not composable.
                data class WindUi(val level: WindLevel, val label: String, val short: String)
                val windUi = WIND_LEVELS.map {
                    WindUi(it, stringResource(it.labelRes), stringResource(it.shortRes))
                }
                AdaptiveChipRow(
                    items = windUi,
                    isSelected = { windLevelFor(hp.alphaWind) == it.level },
                    onSelect = { ui -> update { it.copy(alphaWind = ui.level.alphaWind) } },
                    label = { it.short },
                    labelLong = { it.label },
                    shortItemWidth = 76.dp, longItemWidth = 150.dp
                )
                if (novice) {
                    Text(stringResource(R.string.ui2_wiz_wind_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.ui2_wiz_weather_data),
                    style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = hp.weatherSource == "sample",
                        onClick = { update { it.copy(weatherSource = "sample") } },
                        label = { Text(stringResource(R.string.ui2_dash_weather_2001)) })
                    FilterChip(selected = hp.weatherSource == "cds",
                        onClick = {
                            // Gate: only switch to CDS if credentials are present.
                            // Otherwise alert + point the user at Data Source Management.
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { cdsCredentialsPresent(context) }
                                if (ok) update { it.copy(weatherSource = "cds") }
                                else showCdsAlert = true
                            }
                        },
                        label = { Text(stringResource(R.string.ui2_wiz_cds)) })
                }
                if (novice) {
                    Text(stringResource(R.string.ui2_wiz_weather_sample_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (hp.weatherSource == "cds") {
                    // Surface what CDS will fetch — the criteria live in the HP section (design §4.1a-5) even
                    // though location defaults from the PV array and the period follows the load data.
                    Text(stringResource(R.string.ui2_wiz_cds_location),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HpDoubleField(stringResource(R.string.ui2_wiz_latitude),
                            hp.latitude, null, novice, Modifier.weight(1f)) { v ->
                            update { it.copy(latitude = v) } }
                        HpDoubleField(stringResource(R.string.ui2_wiz_longitude),
                            hp.longitude, null, novice, Modifier.weight(1f)) { v ->
                            update { it.copy(longitude = v) } }
                    }
                    val onRefYear = cdsDateRange.first == "2001-01-01" && cdsDateRange.second == "2001-12-31"
                    Text(stringResource(R.string.ui2_wiz_cds_query,
                            cdsDateRange.first, cdsDateRange.second) + " " +
                        stringResource(if (onRefYear) R.string.ui2_wiz_ref_year
                                       else R.string.ui2_wiz_from_pv_source),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 2.dp))
                    Text(stringResource(R.string.ui2_wiz_era5_node,
                            HeatPumpWeatherCache.snapToEra5Grid(hp.latitude),
                            HeatPumpWeatherCache.snapToEra5Grid(hp.longitude)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (novice) {
                        Text(stringResource(if (onRefYear) R.string.ui2_wiz_ref_year_hint
                                            else R.string.ui2_wiz_pv_period_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.ui2_wiz_location_default_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (showCdsAlert) {
            AlertDialog(
                onDismissRequest = { showCdsAlert = false },
                title = { Text(stringResource(R.string.ui2_wiz_cds_setup_title)) },
                text = {
                    Text(stringResource(R.string.ui2_wiz_cds_setup_body))
                },
                confirmButton = {
                    TextButton(onClick = {
                        showCdsAlert = false
                        context.startActivity(
                            android.content.Intent(context, UI2DataSourceManagementActivity::class.java))
                    }) { Text(stringResource(R.string.ui2_wiz_open_dsm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showCdsAlert = false }) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                }
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onRemove(hp.id) }) {
                Text(stringResource(R.string.ui2_wiz_remove_hp),
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
