package com.tfcode.comparetout.ui2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.tfcode.comparetout.MainActivity
import com.tfcode.comparetout.R
import com.tfcode.comparetout.TOUTCApplication
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class UI2SimulationsFragment : Fragment() {

    private val simulationsViewModel: UI2SimulationsViewModel by viewModels()
    private val sharedViewModel: UI2SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d("UI2", "UI2SimulationsFragment.onCreateView")
        val onSwitchLegacy: () -> Unit = {
            CoroutineScope(Dispatchers.IO).launch {
                val app = requireActivity().application as TOUTCApplication
                app.putStringValueIntoDataStore("use_ui2", "false")
                val intent = Intent(requireContext(), MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                requireActivity().startActivity(intent)
            }
        }
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                UI2Theme {
                    SimulationsScreen(
                        simulationsViewModel = simulationsViewModel,
                        onSimulationClick = { scenarioId ->
                            Log.d("UI2", "Simulation clicked: scenarioId=$scenarioId")
                            sharedViewModel.setActiveSimulationId(scenarioId)
                            findNavController().navigate(R.id.ui2DashboardFragment)
                        },
                        onSwitchLegacy = onSwitchLegacy
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("UI2", "UI2SimulationsFragment.onViewCreated — observing scenarios LiveData")
        simulationsViewModel.scenarios.observe(viewLifecycleOwner) { list ->
            Log.d("UI2", "UI2SimulationsFragment: scenarios LiveData emitted ${list?.size ?: "null"} items")
            list?.forEachIndexed { i, s -> Log.d("UI2", "  [$i] id=${s.scenarioIndex} name=${s.scenarioName}") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulationsScreen(
    simulationsViewModel: UI2SimulationsViewModel,
    onSimulationClick: (Long) -> Unit,
    onSwitchLegacy: () -> Unit
) {
    val scenarios by simulationsViewModel.scenarios.observeAsState(initial = emptyList())
    var menuExpanded by remember { mutableStateOf(false) }

    SideEffect {
        Log.d("UI2", "SimulationsScreen recompose: ${scenarios.size} scenarios")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Eco Power Optimiser") },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menuExpanded = false })
                        DropdownMenuItem(text = { Text("Units") }, onClick = { menuExpanded = false })
                        DropdownMenuItem(text = { Text("Timezone") }, onClick = { menuExpanded = false })
                        DropdownMenuItem(
                            text = { Text("Switch to Legacy UI") },
                            onClick = { menuExpanded = false; onSwitchLegacy() }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(contentPadding = paddingValues) {
            items(scenarios) {
                ListItem(
                    headlineContent = { Text(it.scenarioName) },
                    modifier = Modifier.clickable { onSimulationClick(it.scenarioIndex) }
                )
            }
        }
    }
}
