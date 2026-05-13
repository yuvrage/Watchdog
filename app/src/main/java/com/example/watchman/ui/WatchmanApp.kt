package com.example.watchman.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.watchman.ui.screens.DeviceBreakdownScreen
import com.example.watchman.ui.screens.HomeScreen
import com.example.watchman.ui.screens.ResultDetailScreen
import com.example.watchman.ui.screens.ResultsScreen
import com.example.watchman.ui.screens.ScanScreen
import com.example.watchman.ui.screens.SystemAppsScreen

object Routes {
    const val HOME = "home"
    const val SCAN = "scan"
    const val RESULTS = "results"
    const val RESULT_DETAIL = "resultDetail"
    const val SYSTEM_APPS = "systemApps"
    const val BREAKDOWN = "breakdown"
}

@Composable
fun WatchmanApp() {
    val navController = rememberNavController()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onScanApk = { navController.navigate(Routes.SCAN) },
                    onScanDeviceApps = {
                        navController.navigate(Routes.SYSTEM_APPS)
                    },
                    onViewResult = { navController.navigate(Routes.RESULTS) },
                    onViewBreakdown = { navController.navigate(Routes.BREAKDOWN) },
                    onShowAllApps = { navController.navigate(Routes.BREAKDOWN) },
                )
            }

            composable(Routes.SYSTEM_APPS) {
                SystemAppsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenResult = { taskId ->
                        navController.navigate("${Routes.RESULT_DETAIL}/$taskId")
                    },
                )
            }

            composable(Routes.BREAKDOWN) {
                DeviceBreakdownScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.SCAN) {
                ScanScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToResult = { taskId ->
                        navController.navigate("${Routes.RESULT_DETAIL}/$taskId")
                    },
                )
            }

            composable(Routes.RESULTS) {
                ResultsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTask = { taskId ->
                        navController.navigate("${Routes.RESULT_DETAIL}/$taskId")
                    },
                )
            }

            composable(
                route = "${Routes.RESULT_DETAIL}/{taskId}",
                arguments = listOf(
                    navArgument("taskId") { type = NavType.StringType },
                ),
            ) { entry ->
                val taskId = entry.arguments?.getString("taskId").orEmpty()
                ResultDetailScreen(
                    taskId = taskId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}


