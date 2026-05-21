package dev.ambitionsoftware.tymeboxed.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.ambitionsoftware.tymeboxed.ui.screens.profile.BlockedAppsPickerScreen
import dev.ambitionsoftware.tymeboxed.ui.screens.profile.BlockedDomainsPickerScreen
import dev.ambitionsoftware.tymeboxed.ui.screens.profile.ProfileEditViewModel
import dev.ambitionsoftware.tymeboxed.data.prefs.AppPreferences
import dev.ambitionsoftware.tymeboxed.ui.screens.home.HomeScreen
import dev.ambitionsoftware.tymeboxed.ui.screens.intro.IntroScreen
import dev.ambitionsoftware.tymeboxed.ui.screens.permissions.PermissionsScreen
import dev.ambitionsoftware.tymeboxed.ui.screens.profile.ProfileEditScreen
import dev.ambitionsoftware.tymeboxed.ui.screens.profile.SchedulePickerScreen
import dev.ambitionsoftware.tymeboxed.ui.screens.inapp.InAppBlockingScreen
import dev.ambitionsoftware.tymeboxed.ui.screens.settings.SettingsScreen

/**
 * Top-level navigation graph. Single-activity app — this is hosted inside
 * `MainActivity` below [TbTheme].
 *
 * Initial destination is [Routes.intro] on first launch (`introCompleted == false`)
 * and [Routes.HOME] otherwise. We read the flag synchronously once to pick the
 * starting route; the rest of the app uses Flow-based observation.
 */
@Composable
fun TymeBoxedNavHost(
    prefs: AppPreferences,
) {
    val navController = rememberNavController()
    val introCompleted by prefs.introCompleted.collectAsState(initial = null)

    // Wait until we know the flag before deciding the start destination.
    // While null we render an empty graph.
    if (introCompleted == null) return

    val startRoute = if (introCompleted == true) Routes.HOME else Routes.intro(0)

    NavHost(
        navController = navController,
        startDestination = startRoute,
    ) {
        composable(
            route = Routes.INTRO,
            arguments = listOf(
                navArgument("initialStep") {
                    type = NavType.IntType
                    defaultValue = 0
                },
            ),
        ) { entry ->
            val initialStep = entry.arguments?.getInt("initialStep") ?: 0
            IntroScreen(
                prefs = prefs,
                initialStep = initialStep,
                onIntroComplete = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(entry.destination.id) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onCreateProfile = { navController.navigate(Routes.profileEdit()) },
                onEditProfile = { id -> navController.navigate(Routes.profileEdit(id)) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenFullPermissions = { navController.navigate(Routes.PERMISSIONS) },
                onOpenInAppBlocking = { navController.navigate(Routes.IN_APP_BLOCKING) },
                onAccountDeleted = {
                    navController.navigate(Routes.intro(1)) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = true
                        }
                    }
                },
            )
        }

        composable(Routes.IN_APP_BLOCKING) {
            InAppBlockingScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.PERMISSIONS) {
            PermissionsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.PROFILE_EDIT,
            arguments = listOf(
                navArgument("profileId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId") ?: "new"
            ProfileEditScreen(
                profileId = profileId,
                onBack = { navController.popBackStack() },
                onNavigateToProfile = { newId ->
                    navController.navigate(Routes.profileEdit(newId)) {
                        popUpTo(Routes.profileEdit(profileId)) { inclusive = true }
                    }
                },
                onOpenBlockedApps = {
                    navController.navigate(Routes.profileEditSelectApps(profileId))
                },
                onOpenBlockedDomains = {
                    navController.navigate(Routes.profileEditSelectDomains(profileId))
                },
                onOpenSchedule = {
                    navController.navigate(Routes.profileEditSchedule(profileId))
                },
            )
        }

        composable(
            route = Routes.PROFILE_EDIT_SELECT_APPS,
            arguments = listOf(
                navArgument("profileId") { type = NavType.StringType },
            ),
        ) { childEntry ->
            val profileId = childEntry.arguments?.getString("profileId") ?: "new"
            val parentEntry = remember(childEntry) {
                navController.getBackStackEntry(Routes.profileEdit(profileId))
            }
            val vm: ProfileEditViewModel = hiltViewModel(parentEntry)
            BlockedAppsPickerScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.PROFILE_EDIT_SELECT_DOMAINS,
            arguments = listOf(
                navArgument("profileId") { type = NavType.StringType },
            ),
        ) { childEntry ->
            val profileId = childEntry.arguments?.getString("profileId") ?: "new"
            val parentEntry = remember(childEntry) {
                navController.getBackStackEntry(Routes.profileEdit(profileId))
            }
            val vm: ProfileEditViewModel = hiltViewModel(parentEntry)
            BlockedDomainsPickerScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.PROFILE_EDIT_SCHEDULE,
            arguments = listOf(
                navArgument("profileId") { type = NavType.StringType },
            ),
        ) { childEntry ->
            val profileId = childEntry.arguments?.getString("profileId") ?: "new"
            val parentEntry = remember(childEntry) {
                navController.getBackStackEntry(Routes.profileEdit(profileId))
            }
            val vm: ProfileEditViewModel = hiltViewModel(parentEntry)
            SchedulePickerScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
