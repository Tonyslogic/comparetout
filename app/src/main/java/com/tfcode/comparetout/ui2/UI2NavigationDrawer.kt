package com.tfcode.comparetout.ui2

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tfcode.comparetout.R
import com.tfcode.comparetout.TOUTCApplication
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
    onClose: () -> Unit
) {
    val context = LocalContext.current
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
        // Data — supplier plans & meter sources live together.
        UI2DrawerItem(R.drawable.ic_baseline_euro_symbol_24,  "Supplier Plans") {
            onClose()
            context.startActivity(
                android.content.Intent(context, UI2PricePlanListActivity::class.java))
        }
        UI2DrawerItem(R.drawable.ic_baseline_call_split_24,   "Data Source Management") {
            onClose()
            context.startActivity(
                android.content.Intent(context, UI2DataSourceManagementActivity::class.java))
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        // Preferences — global formatting choices.
        UI2DrawerItem(R.drawable.ic_baseline_settings_24,     "Units",                  onClose)
        UI2DrawerItem(R.drawable.ic_baseline_access_time_24,  "Timezone",               onClose)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        UI2DrawerItem(R.drawable.ic_baseline_settings_24,     "Switch to Legacy UI",    onSwitchLegacy)
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
