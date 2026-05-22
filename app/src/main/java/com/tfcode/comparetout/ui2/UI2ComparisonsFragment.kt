package com.tfcode.comparetout.ui2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.tfcode.comparetout.MainActivity
import com.tfcode.comparetout.TOUTCApplication
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class UI2ComparisonsFragment : Fragment() {

    private val viewModel: UI2CompareViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("UI2", "UI2ComparisonsFragment.onCreateView")
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
                    CompareScreen(viewModel = viewModel, onSwitchLegacy = onSwitchLegacy)
                }
            }
        }
    }
}
