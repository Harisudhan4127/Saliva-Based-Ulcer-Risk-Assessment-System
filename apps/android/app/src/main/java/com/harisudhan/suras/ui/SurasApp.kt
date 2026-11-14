package com.harisudhan.suras.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.harisudhan.suras.R
import com.harisudhan.suras.ui.screens.about.AboutScreen
import com.harisudhan.suras.ui.screens.assessment.AssessmentViewModel
import com.harisudhan.suras.ui.screens.assessment.BiomarkerScreen
import com.harisudhan.suras.ui.screens.assessment.NewPatientScreen
import com.harisudhan.suras.ui.screens.consent.ConsentScreen
import com.harisudhan.suras.ui.screens.export.ExportScreen
import com.harisudhan.suras.ui.screens.history.HistoryScreen
import com.harisudhan.suras.ui.screens.home.HomeScreen
import com.harisudhan.suras.ui.screens.result.ResultScreen
import com.harisudhan.suras.ui.screens.result.ResultViewModel
import com.harisudhan.suras.ui.screens.settings.SettingsScreen
import com.harisudhan.suras.ui.screens.splash.SplashScreen
import com.harisudhan.suras.ui.screens.splash.StartDestination

object Routes {
    const val SPLASH = "splash"
    const val CONSENT = "consent"
    const val HOME = "home"
    const val NEW_PATIENT = "new_patient"
    const val BIOMARKER = "biomarker"
    const val RESULT = ResultViewModel.ROUTE
    const val HISTORY = "history"
    const val EXPORT = "export"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    TopLevelDestination(Routes.HISTORY, R.string.nav_history, Icons.Filled.History),
    TopLevelDestination(Routes.EXPORT, R.string.nav_export, Icons.Filled.IosShare),
    TopLevelDestination(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

@Composable
fun SurasApp(startAtSettings: Boolean) {
    val navController = rememberNavController()
    val assessmentViewModel: AssessmentViewModel = hiltViewModel()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = topLevelDestinations.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateTopLevel(destination.route) },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(
                    startAtSettings = startAtSettings,
                    onReady = { destination ->
                        when (destination) {
                            StartDestination.Consent ->
                                navController.navigate(Routes.CONSENT) {
                                    popUpTo(Routes.SPLASH) { inclusive = true }
                                }
                            StartDestination.Home ->
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.SPLASH) { inclusive = true }
                                }
                            StartDestination.Settings -> {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.SPLASH) { inclusive = true }
                                }
                                navController.navigate(Routes.SETTINGS) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    },
                )
            }

            composable(Routes.CONSENT) {
                ConsentScreen(
                    onAccepted = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.CONSENT) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.HOME) {
                HomeScreen(
                    onNewAssessment = {
                        assessmentViewModel.reset()
                        navController.navigate(Routes.NEW_PATIENT)
                    },
                    onHistory = { navController.navigateTopLevel(Routes.HISTORY) },
                    onExport = { navController.navigateTopLevel(Routes.EXPORT) },
                    onSettings = { navController.navigateTopLevel(Routes.SETTINGS) },
                    onAbout = { navController.navigate(Routes.ABOUT) },
                    onOpenResult = { patientId ->
                        navController.navigate(ResultViewModel.route(patientId))
                    },
                )
            }

            composable(Routes.NEW_PATIENT) {
                NewPatientScreen(
                    onBack = { navController.popBackStack() },
                    onContinue = { navController.navigate(Routes.BIOMARKER) },
                    viewModel = assessmentViewModel,
                )
            }

            composable(Routes.BIOMARKER) {
                BiomarkerScreen(
                    onBack = { navController.popBackStack() },
                    onResult = { patientId ->
                        navController.navigate(ResultViewModel.route(patientId))
                    },
                    viewModel = assessmentViewModel,
                )
            }

            composable(
                route = Routes.RESULT,
                arguments = listOf(
                    navArgument(ResultViewModel.ARG_PATIENT_ID) { type = NavType.StringType },
                ),
            ) {
                ResultScreen(
                    onBack = { navController.popBackStack() },
                    onDone = {
                        assessmentViewModel.reset()
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME)
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(Routes.HISTORY) {
                HistoryScreen(
                    onOpenResult = { patientId ->
                        navController.navigate(ResultViewModel.route(patientId))
                    },
                )
            }

            composable(Routes.EXPORT) {
                ExportScreen()
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onAbout = { navController.navigate(Routes.ABOUT) },
                )
            }

            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        if (currentDestination?.route != route) {
            popUpTo(Routes.HOME) { saveState = true }
        }
        launchSingleTop = true
        restoreState = true
    }
}
