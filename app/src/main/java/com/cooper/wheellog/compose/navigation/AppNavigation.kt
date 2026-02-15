package com.cooper.wheellog.compose.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cooper.wheellog.compose.WheelViewModel
import com.cooper.wheellog.compose.screens.ChartScreen
import com.cooper.wheellog.compose.screens.DashboardScreen
import com.cooper.wheellog.compose.screens.RidesScreen
import com.cooper.wheellog.compose.screens.ScanScreen
import com.cooper.wheellog.compose.screens.SettingsScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Devices : Screen("devices", "Devices", Icons.Default.Bluetooth)
    data object Rides : Screen("rides", "Rides", Icons.Default.Route)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

private val bottomTabs = listOf(Screen.Devices, Screen.Rides, Screen.Settings)

@Composable
fun AppNavigation(viewModel: WheelViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Hide bottom bar on chart screen
    val showBottomBar = currentRoute != "chart"

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Devices.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Devices.route) {
                val connectionState by viewModel.connectionState.collectAsState()
                if (connectionState.isConnected || connectionState.isConnecting) {
                    DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToChart = { navController.navigate("chart") }
                    )
                } else {
                    ScanScreen(viewModel = viewModel)
                }
            }
            composable(Screen.Rides.route) {
                RidesScreen(viewModel = viewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = viewModel)
            }
            composable("chart") {
                ChartScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
