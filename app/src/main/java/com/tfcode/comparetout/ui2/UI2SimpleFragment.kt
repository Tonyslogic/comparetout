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

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.tfcode.comparetout.R
import com.tfcode.comparetout.TOUTCApplication
import com.tfcode.comparetout.importers.esbn.ESBNImportWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Host for simple mode's single screen ([UI2SimpleScreen]). Owns the bits that
 * need an Android entry point: location permission + fetch, graphs navigation,
 * and the switch back to the full UI.
 */
@AndroidEntryPoint
class UI2SimpleFragment : Fragment() {

    private val viewModel: UI2SimpleViewModel by viewModels()
    private val sharedViewModel: UI2SharedViewModel by activityViewModels()

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // In-UI2 ESBN HDF import: pick a file and run the same ESBNImportWorker the
    // legacy importer uses (no jump to the legacy screen). The worker writes the
    // meter data; the VM's ESBN-source flow then picks it up.
    private val importHdfLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            val inputData = Data.Builder()
                .putString(ESBNImportWorker.KEY_SYSTEM_SN, SIMPLE_ESBN_SYSTEM_SN)
                .putString(ESBNImportWorker.KEY_URI, uri.toString())
                .build()
            val request = OneTimeWorkRequestBuilder<ESBNImportWorker>()
                .setInputData(inputData)
                .addTag(SIMPLE_ESBN_SYSTEM_SN + "Import")
                .build()
            val wm = WorkManager.getInstance(requireContext().applicationContext)
            wm.pruneWork()
            wm.beginUniqueWork(IMPORT_WORK, ExistingWorkPolicy.REPLACE, request).enqueue()
            viewModel.markHdfImporting()
        }

    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) fetchLocation()
            else Toast.makeText(
                requireContext(),
                "Location is needed to estimate solar yield",
                Toast.LENGTH_LONG
            ).show()
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Deterministic import feedback: reflect the worker's state into the VM
        // (spinner while running, read the file in on success) rather than
        // relying on the meter-data LiveData emitting in time.
        WorkManager.getInstance(requireContext().applicationContext)
            .getWorkInfosForUniqueWorkLiveData(IMPORT_WORK)
            .observe(viewLifecycleOwner) { infos ->
                if (infos.isNullOrEmpty()) return@observe
                when {
                    infos.any {
                        it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                    } -> viewModel.markHdfImporting()
                    infos.any { it.state == WorkInfo.State.SUCCEEDED } -> viewModel.onHdfImported()
                    infos.any { it.state == WorkInfo.State.FAILED } -> viewModel.onHdfImportFailed()
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("UI2", "UI2SimpleFragment.onCreateView")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                UI2Theme {
                    UI2SimpleScreen(
                        viewModel = viewModel,
                        onRequestLocation = ::requestLocation,
                        onLaunchGraphs = ::launchGraphs,
                        onSwitchToFullUi = ::switchToFullUi,
                        onImportHdf = ::importHdf
                    )
                }
            }
        }
    }

    private fun requestLocation() {
        val ctx = requireContext()
        val hasCoarse = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasFine = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCoarse || hasFine) {
            fetchLocation()
        } else {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                viewModel.setLocation(location.latitude, location.longitude)
            } else {
                Toast.makeText(
                    requireContext(),
                    "Couldn't get a location fix — try again outdoors",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /** Point the shared dashboard/graphs selection at the simple scenario, then
     * open the existing Graphs screen. */
    private fun launchGraphs() {
        val app = requireActivity().application as TOUTCApplication
        CoroutineScope(Dispatchers.IO).launch {
            val id = storedSimpleScenarioId(app)
            withContext(Dispatchers.Main) {
                if (id != null) {
                    sharedViewModel.setActiveSimulationId(id)
                    findNavController().navigate(R.id.ui2GraphsFragment)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Calculate first to create a scenario",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /** Pick an HDF file and import it in-place (no legacy-UI jump). When the
     * worker finishes, the VM's ESBN-source flow updates and the screen reads
     * the yearly total from it. */
    private fun importHdf() {
        importHdfLauncher.launch("*/*")
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

    companion object {
        /** Unique-work name for the in-place HDF import (observed for progress). */
        private const val IMPORT_WORK = "simple_esbn_import"
    }
}
