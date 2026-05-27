package com.tfcode.comparetout.ui2

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tfcode.comparetout.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UI2MainActivity : AppCompatActivity() {

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

        bottomNav.selectedItemId = R.id.ui2DashboardFragment

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
                    val shouldShow = dest.id != R.id.ui2GraphsFragment
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
    }
}
