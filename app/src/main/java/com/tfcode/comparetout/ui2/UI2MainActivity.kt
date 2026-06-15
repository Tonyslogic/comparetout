package com.tfcode.comparetout.ui2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.R
import com.tfcode.comparetout.TOUTCApplication
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class UI2MainActivity : AppCompatActivity() {

    private val sharedViewModel: UI2SharedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        Log.d("UI2", "UI2MainActivity.onCreate")
        setContentView(R.layout.activity_ui2_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as androidx.navigation.fragment.NavHostFragment
        val navController = navHostFragment.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav_view)

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bars.bottom)
            insets
        }

        // Hidden until the mode resolves, so the bar never flashes in front of
        // the simple screen on a fresh install.
        bottomNav.visibility = View.GONE

        val setupNavListener = object : androidx.navigation.NavController.OnDestinationChangedListener {
            override fun onDestinationChanged(controller: androidx.navigation.NavController, destination: androidx.navigation.NavDestination, arguments: Bundle?) {
                Log.d("UI2", "NavController initial destination: ${destination.label}")
                controller.removeOnDestinationChangedListener(this)
                bottomNav.setOnItemSelectedListener { item ->
                    if (controller.currentDestination != null && controller.currentDestination?.id != item.itemId) {
                        Log.d("UI2", "BottomNav navigating to itemId=${item.itemId}")
                        controller.navigate(item.itemId)
                    }
                    true
                }
                controller.addOnDestinationChangedListener { _, dest, _ ->
                    Log.d("UI2", "NavController destination changed: ${dest.label}")
                    // The Graphs and Simple screens are full-bleed — no bottom bar.
                    val shouldShow = dest.id != R.id.ui2GraphsFragment &&
                        dest.id != R.id.ui2SimpleFragment
                    bottomNav.visibility = if (shouldShow) View.VISIBLE else View.GONE
                    // The HideBottomViewOnScrollBehavior hides the bar by
                    // translating it offscreen rather than toggling visibility.
                    // After navigating to a new destination, also tell the
                    // behavior to slide the bar back up — otherwise a list
                    // that scrolled the bar away leaves it stranded below the
                    // screen even though visibility says VISIBLE, and the
                    // next screen has no scroll content to bring it back.
                    if (shouldShow) {
                        val params = bottomNav.layoutParams as? CoordinatorLayout.LayoutParams
                        @Suppress("UNCHECKED_CAST")
                        val behavior = params?.behavior as? HideBottomViewOnScrollBehavior<View>
                        behavior?.slideUp(bottomNav)
                    }
                    if (bottomNav.selectedItemId != dest.id) {
                        bottomNav.selectedItemId = dest.id
                    }
                }
            }
        }
        navController.addOnDestinationChangedListener(setupNavListener)

        // The nav graph is inflated here (not via app:navGraph in XML) so the
        // start destination can depend on the resolved simple-mode flag. Reading
        // the flag + scenario count blocks on the DataStore/DB, so do it off the
        // main thread, then set the graph (which navigates to the start dest).
        lifecycleScope.launch {
            val app = application as TOUTCApplication
            val simple = withContext(Dispatchers.IO) { resolveSimpleMode(app) }
            Log.d("UI2", "UI2MainActivity simpleMode=$simple")
            val graph = navController.navInflater.inflate(R.navigation.nav_ui2)
            graph.setStartDestination(
                if (simple) R.id.ui2SimpleFragment else R.id.ui2DashboardFragment
            )
            navController.graph = graph

            // The ongoing visibility listener only fires on *subsequent*
            // destination changes, so set the bar's initial state for the start
            // destination here: visible on the full-UI dashboard, gone in simple
            // mode (and on Graphs, which is never the start destination).
            if (simple) {
                bottomNav.visibility = View.GONE
            } else {
                bottomNav.visibility = View.VISIBLE
                bottomNav.selectedItemId = R.id.ui2DashboardFragment
                // Notification-launched source selection only makes sense in the
                // full UI (the simple screen has no source list / dashboard tab).
                handleSourceSelectionIntent(intent, bottomNav)
            }
        }

        // First-use disclaimer now lives here (UI2 is the default entry point),
        // shown over UI2 rather than the legacy screen.
        maybeShowDisclaimer()
    }

    /** Show the one-time legal disclaimer on first launch, then record it. */
    private fun maybeShowDisclaimer() {
        val app = application as TOUTCApplication
        lifecycleScope.launch {
            val seen = withContext(Dispatchers.IO) {
                runCatching { app.getStringValueFromDataStore(FIRST_USE_KEY) }.getOrDefault("")
            } == "False"
            if (seen || isFinishing) return@launch
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this@UI2MainActivity)
                .setMessage(DISCLAIMER_TEXT)
                .setCancelable(false)
                .setPositiveButton("Ok") { _, _ ->
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        runCatching { app.putStringValueIntoDataStore(FIRST_USE_KEY, "False") }
                    }
                }
                .show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav_view)
        handleSourceSelectionIntent(intent, bottomNav)
    }

    /**
     * When an importer notification launches the app with a source in its
     * extras, switch to the dashboard tab and ask the shared VM to select
     * that data source. The extras are cleared after handling so a later
     * config-change / re-create doesn't re-trigger the selection.
     */
    private fun handleSourceSelectionIntent(intent: Intent?, bottomNav: BottomNavigationView) {
        val sysSn = intent?.getStringExtra(UI2NotificationLaunch.EXTRA_DS_SYSSN) ?: return
        val importerName = intent.getStringExtra(UI2NotificationLaunch.EXTRA_DS_IMPORTER) ?: return
        val importer = runCatching {
            ComparisonUIViewModel.Importer.valueOf(importerName)
        }.getOrNull() ?: return

        Log.d("UI2", "handleSourceSelectionIntent sysSn=$sysSn importer=$importer")
        bottomNav.selectedItemId = R.id.ui2DashboardFragment
        sharedViewModel.selectDataSourceBySn(sysSn, importer)

        // Consume the extras so they don't fire again on recreate.
        intent.removeExtra(UI2NotificationLaunch.EXTRA_DS_SYSSN)
        intent.removeExtra(UI2NotificationLaunch.EXTRA_DS_IMPORTER)
    }

    companion object {
        /** Matches TOUTCApplication.FIRST_USE (package-private in the legacy package). */
        private const val FIRST_USE_KEY = "first_use"

        private const val DISCLAIMER_TEXT =
            "Solar data is variable. This app uses historical solar data in estimations.\n\n" +
            "Solar panels may be shaded. This app makes no attempt to consider shading.\n\n" +
            "Price plan accuracy determines calculation accuracy. Check price plan details.\n\n" +
            "Price plans do not include Public Services Obligations. Estimates will not include this.\n\n" +
            "Price plans do not include rate usage limitations or complex contracts. Estimates will be " +
            "wrong where limitations are exceeded or conditions triggered.\n\n" +
            "All estimates are based on user input. If the input is bad, the output will be too.\n\n" +
            "This app provides the ability to explore possibilities, estimations are not advice " +
            "(financial or otherwise). The app is provided as-is, no representation or warranty of any " +
            "kind, express or implied, regarding the accuracy, adequacy, validity, reliability, " +
            "availability, or completeness of any information is made.\n\n" +
            "Enjoy!"
    }
}
