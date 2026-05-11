package com.tfcode.comparetout.ui2

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tfcode.comparetout.R

@Composable
fun UI2DrawerContent(onSwitchLegacy: () -> Unit, onClose: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Menu", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
        }
        HorizontalDivider()
        UI2DrawerItem(R.drawable.ic_baseline_euro_symbol_24,  "Supplier Plans",         onClose)
        UI2DrawerItem(R.drawable.ic_baseline_settings_24,     "Units",                  onClose)
        UI2DrawerItem(R.drawable.ic_baseline_access_time_24,  "Timezone",               onClose)
        UI2DrawerItem(R.drawable.ic_baseline_call_split_24,   "Data Source Management", onClose)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        UI2DrawerItem(R.drawable.ic_baseline_settings_24,     "Switch to Legacy UI",    onSwitchLegacy)
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
