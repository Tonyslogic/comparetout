package com.tfcode.comparetout.ui2

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tfcode.comparetout.R
import com.tfcode.comparetout.TOUTCApplication
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shared DataStore key for the "Show hints" preference (a.k.a. novice mode). */
private const val SHOW_HINTS_KEY = "wizard_novice_mode"

/**
 * Reads the global "Show hints" flag from DataStore and returns a (value, toggle)
 * pair. For screens whose ViewModel doesn't already own the flag — every tab needs
 * to be able to read and flip it, even if the screen itself ignores the value.
 */
@Composable
fun rememberShowHints(): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    var hints by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val stored = withContext(Dispatchers.IO) {
            runCatching {
                (context.applicationContext as TOUTCApplication)
                    .getStringValueFromDataStore(SHOW_HINTS_KEY)
            }.getOrDefault("")
        }
        hints = stored != "false"
    }
    val toggle: () -> Unit = {
        val newValue = !hints
        hints = newValue
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                (context.applicationContext as TOUTCApplication)
                    .putStringValueIntoDataStore(SHOW_HINTS_KEY, newValue.toString())
            }
        }
    }
    return hints to toggle
}

/**
 * The one-and-only app menu, slid in from the right on every UI2 tab.
 *
 * Hosts the global "Show hints" preference plus standalone entries to manage
 * data outside any single screen (supplier plans, units, timezone, data
 * sources). Each screen wires `showHints` to its own ViewModel-backed flag so
 * the toggle flips the live UI immediately as well as persisting.
 */
@Composable
fun UI2DrawerContent(
    showHints: Boolean,
    onShowHintsChange: (Boolean) -> Unit,
    onSwitchLegacy: () -> Unit,
    onClose: () -> Unit,
    simpleMode: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Menu", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
        }
        HorizontalDivider()
        ShowHintsRow(showHints, onShowHintsChange)
        HorizontalDivider()
        // Supplier Plans (the price-plan editor) stays available in simple mode —
        // the user needs it to verify/correct downloaded tariffs.
        UI2DrawerItem(R.drawable.ic_baseline_euro_symbol_24,  "Supplier Plans") {
            onClose()
            context.startActivity(
                android.content.Intent(context, UI2PricePlanListActivity::class.java))
        }
        // Data-management, import/export, and sample onboarding are full-UI only —
        // simple mode hides them to keep to a single focused flow.
        if (!simpleMode) {
            UI2DrawerItem(R.drawable.ic_baseline_call_split_24,   "Data Source Management") {
                onClose()
                context.startActivity(
                    android.content.Intent(context, UI2DataSourceManagementActivity::class.java))
            }
            UI2DrawerItem(R.drawable.ic_baseline_download_24,     "Import / Export") {
                onClose()
                context.startActivity(
                    android.content.Intent(context, UI2ImportExportActivity::class.java))
            }
            // One-tap onboarding: seed a sample scenario + two demo plans, then kick
            // off the same PVGIS-fetch + simulation pipeline the wizard would. Idempotent
            // (subsequent taps are no-ops). A separate front door from simple mode:
            // it plays with dummy data across the whole app.
            UI2DrawerItem(R.drawable.ic_baseline_download_24,     "Try with sample data") {
                onClose()
                val loader = EntryPointAccessors
                    .fromApplication(context.applicationContext, SampleDataLoaderEntryPoint::class.java)
                    .sampleDataLoader()
                coroutineScope.launch {
                    val msg = when (val result = loader.load()) {
                        is SampleDataLoader.Result.AlreadyLoaded ->
                            "Sample data already loaded"
                        is SampleDataLoader.Result.Loaded ->
                            "Sample loaded · simulation running in background"
                        is SampleDataLoader.Result.Failed ->
                            "Couldn't load sample data: ${result.error.message ?: "unknown error"}"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
        // Download the community-maintained real Irish supplier tariffs. Always
        // paired with the "may be out of date" caveat (the list is public and
        // can drift; the price-plan editor stays available to correct it).
        UI2DrawerItem(R.drawable.ic_baseline_download_24,     "Refresh tariffs") {
            onClose()
            val downloader = EntryPointAccessors
                .fromApplication(context.applicationContext, PricePlanDownloaderEntryPoint::class.java)
                .pricePlanDownloader()
            coroutineScope.launch {
                val msg = when (val result = downloader.download()) {
                    is PricePlanDownloader.Result.Loaded ->
                        "Downloaded ${result.added} tariff${if (result.added == 1) "" else "s"} " +
                            "· these are community-maintained and may be out of date"
                    is PricePlanDownloader.Result.Empty ->
                        "No tariffs found in the published list"
                    is PricePlanDownloader.Result.NoNetwork ->
                        "No connection — couldn't download tariffs"
                    is PricePlanDownloader.Result.Failed ->
                        "Couldn't download tariffs: ${result.error.message ?: "unknown error"}"
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        // Preferences — global formatting choices.
        UI2DrawerItem(R.drawable.ic_baseline_settings_24,     "Units",                  onClose)
        UI2DrawerItem(R.drawable.ic_baseline_access_time_24,  "Timezone") {
            onClose()
            context.startActivity(
                android.content.Intent(context, UI2TimezoneActivity::class.java))
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        if (simpleMode) {
            // Leave the persistent single-screen mode for the full 4-tab UI.
            UI2DrawerItem(R.drawable.ic_baseline_settings_24, "Switch to full UI") {
                onClose()
                relaunchInMode(context, simple = false)
            }
        } else {
            // Enter the persistent single-screen simple mode. Self-contained: flips
            // the flag and relaunches UI2MainActivity (CLEAR_TASK) so it works from
            // any host activity the drawer appears in, landing on the simple screen.
            UI2DrawerItem(R.drawable.ic_baseline_settings_24, "Switch to quick UI") {
                onClose()
                relaunchInMode(context, simple = true)
            }
            UI2DrawerItem(R.drawable.ic_baseline_settings_24, "Switch to Legacy UI", onSwitchLegacy)
        }
    }
}

/** Flip the simple-mode flag and relaunch the UI2 shell (CLEAR_TASK) so it
 * rebuilds on the right start destination from any host activity. */
internal fun relaunchInMode(context: android.content.Context, simple: Boolean) {
    CoroutineScope(Dispatchers.IO).launch {
        setSimpleMode(context.applicationContext as TOUTCApplication, simple)
        withContext(Dispatchers.Main) {
            val intent = android.content.Intent(context, UI2MainActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            context.startActivity(intent)
        }
    }
}

@Composable
private fun ShowHintsRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("Show hints", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Inline help text on each tab",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun UI2DrawerItem(iconRes: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(iconRes), null, Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
