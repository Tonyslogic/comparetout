package com.tfcode.comparetout.ui2

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tfcode.comparetout.MainActivity
import com.tfcode.comparetout.R
import com.tfcode.comparetout.TOUTCApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UI2MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ui2_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as androidx.navigation.fragment.NavHostFragment
        val navController = navHostFragment.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav_view)
        // Set selected item to match start destination
        bottomNav.selectedItemId = R.id.ui2DashboardFragment
        // Attach a one-time destination changed listener to set up nav only after first destination is set
        val setupNavListener = object : androidx.navigation.NavController.OnDestinationChangedListener {
            override fun onDestinationChanged(controller: androidx.navigation.NavController, destination: androidx.navigation.NavDestination, arguments: Bundle?) {
                // Remove this listener after first call
                controller.removeOnDestinationChangedListener(this)
                // Now safe to set up bottom nav listener
                bottomNav.setOnItemSelectedListener { item ->
                    if (controller.currentDestination != null && controller.currentDestination?.id != item.itemId) {
                        controller.navigate(item.itemId)
                    }
                    true
                }
                controller.addOnDestinationChangedListener { _, dest, _ ->
                    if (bottomNav.selectedItemId != dest.id) {
                        bottomNav.selectedItemId = dest.id
                    }
                }
            }
        }
        navController.addOnDestinationChangedListener(setupNavListener)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.ui2_main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_switch_legacy -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val app = application as TOUTCApplication
                    app.putStringValueIntoDataStore("use_ui2", "false")
                    val intent = Intent(this@UI2MainActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
