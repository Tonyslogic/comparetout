package com.tfcode.comparetout.ui2.dashboard

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.MainActivity
import com.tfcode.comparetout.R
import com.tfcode.comparetout.TOUTCApplication
import com.tfcode.comparetout.profile.AppProfiles
import com.tfcode.comparetout.region.RegionProfiles
import com.tfcode.comparetout.model.costings.Costings
import com.tfcode.comparetout.model.scenario.LoadProfile
import com.tfcode.comparetout.model.scenario.Panel
import com.tfcode.comparetout.model.scenario.PanelPVSummary
import com.tfcode.comparetout.model.scenario.ScenarioComponents
import com.tfcode.comparetout.model.scenario.SimKPIs
import com.tfcode.comparetout.ui2.charts.ExpandableCard
import com.tfcode.comparetout.ui2.charts.LoadDistributionCharts
import com.tfcode.comparetout.ui2.charts.PVSummaryBarChart
import com.tfcode.comparetout.ui2.charts.PieChart
import com.tfcode.comparetout.ui2.charts.PieLegend
import com.tfcode.comparetout.ui2.charts.PieSlice
import com.tfcode.comparetout.ui2.charts.SimulationPieCharts
import com.tfcode.comparetout.ui2.charts.SimpleDistBarChart
import com.tfcode.comparetout.ui2.charts.dashPieLabels
import com.tfcode.comparetout.ui2.charts.stylePVBarChart
import com.tfcode.comparetout.ui2.SampleDataLoader
import com.tfcode.comparetout.ui2.SampleDataLoaderEntryPoint
import com.tfcode.comparetout.ui2.UI2WizardActivity
import com.tfcode.comparetout.ui2.UI2DataSourceManagementActivity
import com.tfcode.comparetout.ui2.relaunchInMode
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.time.LocalDate

/*
 * Dashboard empty states — extracted verbatim from UI2DashboardFragment.kt
 * (mega-refactor B1e). Imports inherited; unused entries are cosmetic only.
 */

/**
 * First-run onboarding card shown on the Dashboard when there are no scenarios
 * and no data source is selected. The button text "Try with sample data" is
 * the canonical Robo selector — see plans/roboscript/robo-plan.md Phase 4B/4C.
 */
/**
 * Empty state shown when the dashboard has no pinned subject but the user
 * already has scenarios and/or data sources available — typically reached
 * after the deletion guards in UI2SharedViewModel clear the saved subject
 * because its underlying scenario/sysSn was deleted. Directs the user to
 * pick a different subject from the navigation drawer.
 */
@Composable
internal fun NoActiveSubjectCard() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.ui2_dash_pick_subject_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.ui2_dash_pick_subject_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun EmptyDashboardSampleCard() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.ui2_dash_welcome),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.ui2_dash_pick_start),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            // (a) Sample data — seed a demo scenario + tariffs and simulate.
            Button(
                onClick = {
                    val loader = EntryPointAccessors
                        .fromApplication(context.applicationContext, SampleDataLoaderEntryPoint::class.java)
                        .sampleDataLoader()
                    coroutineScope.launch {
                        val msg = when (val result = loader.load()) {
                            is SampleDataLoader.Result.AlreadyLoaded ->
                                context.getString(R.string.ui2_sample_already_loaded)
                            is SampleDataLoader.Result.Loaded ->
                                context.getString(R.string.ui2_sample_loaded)
                            is SampleDataLoader.Result.Failed ->
                                context.getString(R.string.ui2_sample_failed,
                                    result.error.message
                                        ?: context.getString(R.string.ui2_unknown_error))
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.ui2_drawer_try_sample)) }

            // (b) Quick mode — the simplified single-screen flow.
            OutlinedButton(
                onClick = { relaunchInMode(context, simple = true) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.ui2_simple_title)) }

            // (c) Add a scenario — same entry point as "+ Create new" on the Scenarios tab.
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        android.content.Intent(context, UI2WizardActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.ui2_dash_add_scenario)) }
        }
    }
}

/**
 * The source edition's welcome / no-subject card: no sample scenario, no quick
 * mode, no wizard to offer — the single path is connecting real data, so the
 * one button opens Data Source Management.
 */
@Composable
internal fun EmptyDashboardConnectSourceCard() {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.ui2_dash_welcome),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.ui2_dash_connect_source_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = {
                    context.startActivity(android.content.Intent(
                        context, UI2DataSourceManagementActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.ui2_drawer_data_sources)) }
        }
    }
}
