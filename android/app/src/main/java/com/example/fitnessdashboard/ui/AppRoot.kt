package com.example.fitnessdashboard.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.fitnessdashboard.ui.activities.ActivityTypeScreen
import com.example.fitnessdashboard.ui.lifting.LiftingScreen
import com.example.fitnessdashboard.ui.stats.StatsScreen

private sealed class Dest(val route: String, val label: String, val icon: ImageVector) {
    data object Running : Dest("running", "Running", Icons.Filled.DirectionsRun)
    data object Swimming : Dest("swimming", "Swimming", Icons.Filled.Pool)
    data object Lifting : Dest("lifting", "Lifting", Icons.Filled.FitnessCenter)
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val destinations = listOf(Dest.Running, Dest.Swimming, Dest.Lifting)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStack by nav.currentBackStackEntryAsState()
                val current = backStack?.destination?.route
                destinations.forEach { dest ->
                    NavigationBarItem(
                        selected = current == dest.route,
                        onClick = {
                            nav.navigate(dest.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Dest.Running.route,
            modifier = Modifier.padding(padding),
        ) {
            val openStats = { nav.navigate("stats") }
            composable(Dest.Running.route) {
                ActivityTypeScreen(sport = "run", title = "Running", onOpenStats = openStats)
            }
            composable(Dest.Swimming.route) {
                ActivityTypeScreen(sport = "swim", title = "Swimming", onOpenStats = openStats)
            }
            composable(Dest.Lifting.route) { LiftingScreen(onOpenStats = openStats) }
            composable("stats") { StatsScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
