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

package com.tfcode.comparetout.ui2

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.tfcode.comparetout.TOUTCApplication
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Simple mode's single combined input + results screen.
 *
 * Phase 1 placeholder: it only proves the app-shell gating (no bottom nav, this
 * is the start destination when [SIMPLE_MODE_KEY] is true) and the round-trip
 * back to the full UI. The real inputs/results land in Phase 4.
 */
@AndroidEntryPoint
class UI2SimpleFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("UI2", "UI2SimpleFragment.onCreateView")
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                UI2Theme {
                    SimplePlaceholderScreen(onSwitchToFullUi = ::switchToFullUi)
                }
            }
        }
    }

    /** Persist simple-mode = false, then recreate so the shell rebuilds on the
     * full 4-tab nav. */
    private fun switchToFullUi() {
        val app = requireActivity().application as TOUTCApplication
        CoroutineScope(Dispatchers.IO).launch {
            setSimpleMode(app, false)
            withContext(Dispatchers.Main) { requireActivity().recreate() }
        }
    }
}

@Composable
private fun SimplePlaceholderScreen(onSwitchToFullUi: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Simple mode",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                "A quick way to see whether solar and a battery would pay off " +
                    "for you. The inputs and results land here next — for now " +
                    "this confirms the simplified shell.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = onSwitchToFullUi) {
                Text("Switch to full UI")
            }
        }
    }
}
