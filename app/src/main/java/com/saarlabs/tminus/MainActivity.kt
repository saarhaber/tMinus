package com.saarlabs.tminus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.saarlabs.tminus.ui.AccessibilityEditorRoute
import com.saarlabs.tminus.ui.AccessibilityListScreen
import com.saarlabs.tminus.ui.CommuteEditorRoute
import com.saarlabs.tminus.ui.CommuteListScreen
import com.saarlabs.tminus.ui.LastTrainEditorRoute
import com.saarlabs.tminus.ui.LastTrainListScreen
import com.saarlabs.tminus.ui.RoadmapScreen
import com.saarlabs.tminus.ui.SettingsContent
import com.saarlabs.tminus.android.widget.WidgetUpdateWorker

public class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences(SettingsKeys.PREFS, MODE_PRIVATE)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val rootNav = rememberNavController()
                    NavHost(
                        navController = rootNav,
                        startDestination = ROUTE_MAIN_TABS,
                    ) {
                        composable(ROUTE_MAIN_TABS) {
                            var settingsV3 by remember {
                                mutableStateOf(prefs.getString(SettingsKeys.KEY_V3_API, "") ?: "")
                            }
                            var settingsUse24Hour by remember {
                                mutableStateOf(prefs.getBoolean(SettingsKeys.KEY_USE_24_HOUR, false))
                            }
                            TminusApp(
                                rootNavController = rootNav,
                                initialV3 = settingsV3,
                                initialUse24Hour = settingsUse24Hour,
                                onSaveSettings = { v3, use24Hour ->
                                    prefs.edit()
                                        .putString(SettingsKeys.KEY_V3_API, v3.ifBlank { null })
                                        .putBoolean(SettingsKeys.KEY_USE_24_HOUR, use24Hour)
                                        .commit()
                                    GlobalDataStore.invalidate()
                                    TminusApplication.refreshNetworking()
                                    settingsV3 = prefs.getString(SettingsKeys.KEY_V3_API, "") ?: ""
                                    settingsUse24Hour =
                                        prefs.getBoolean(SettingsKeys.KEY_USE_24_HOUR, false)
                                    WidgetUpdateWorker.enqueueRefresh(this@MainActivity, appWidgetIds = null)
                                },
                            )
                        }
                        composable(ROUTE_COMMUTE_LIST) {
                            CommuteListScreen(navController = rootNav)
                        }
                        composable("$ROUTE_COMMUTE_EDIT/{profileId}") { entry ->
                            val id = entry.arguments?.getString("profileId") ?: "new"
                            CommuteEditorRoute(navController = rootNav, profileId = id)
                        }
                        composable(ROUTE_LAST_TRAIN_LIST) {
                            LastTrainListScreen(navController = rootNav)
                        }
                        composable("$ROUTE_LAST_TRAIN_EDIT/{profileId}") { entry ->
                            val id = entry.arguments?.getString("profileId") ?: "new"
                            LastTrainEditorRoute(navController = rootNav, profileId = id)
                        }
                        composable(ROUTE_ACCESS_LIST) {
                            AccessibilityListScreen(navController = rootNav)
                        }
                        composable("$ROUTE_ACCESS_EDIT/{id}") { entry ->
                            val id = entry.arguments?.getString("id") ?: "new"
                            AccessibilityEditorRoute(navController = rootNav, id = id)
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // When the user leaves the app (e.g. returns to the home screen), refresh trip widgets
        // so they are not stuck on a loading or stale state.
        WidgetUpdateWorker.enqueueRefresh(this, appWidgetIds = null)
    }

    public companion object {
        public const val ROUTE_MAIN_TABS: String = "main_tabs"
        public const val ROUTE_COMMUTE_LIST: String = "commute_list"
        public const val ROUTE_COMMUTE_EDIT: String = "commute_edit"
        public const val ROUTE_LAST_TRAIN_LIST: String = "last_train_list"
        public const val ROUTE_LAST_TRAIN_EDIT: String = "last_train_edit"
        public const val ROUTE_ACCESS_LIST: String = "access_list"
        public const val ROUTE_ACCESS_EDIT: String = "access_edit"
    }
}

private sealed class MainDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    data object Home : MainDestination("home", R.string.nav_home, Icons.Default.Home)

    data object Roadmap : MainDestination("roadmap", R.string.nav_roadmap, Icons.AutoMirrored.Filled.List)

    data object Settings : MainDestination("settings", R.string.nav_settings, Icons.Default.Settings)

    companion object {
        val entries = listOf(Home, Roadmap, Settings)
    }
}

@Composable
private fun TminusApp(
    rootNavController: NavHostController,
    initialV3: String,
    initialUse24Hour: Boolean,
    onSaveSettings: (String, Boolean) -> Unit,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val current = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainDestination.entries.forEach { dest ->
                    val selected = current?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = MainDestination.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(MainDestination.Home.route) {
                HomeTab(
                    onOpenCommutes = {
                        rootNavController.navigate(MainActivity.ROUTE_COMMUTE_LIST)
                    },
                    onOpenLastTrain = {
                        rootNavController.navigate(MainActivity.ROUTE_LAST_TRAIN_LIST)
                    },
                    onOpenAccessibility = {
                        rootNavController.navigate(MainActivity.ROUTE_ACCESS_LIST)
                    },
                )
            }
            composable(MainDestination.Roadmap.route) {
                RoadmapScreen()
            }
            composable(MainDestination.Settings.route) {
                SettingsContent(
                    initialV3 = initialV3,
                    initialUse24Hour = initialUse24Hour,
                    onSave = onSaveSettings,
                )
            }
        }
    }
}

@Composable
private fun HomeTab(
    onOpenCommutes: () -> Unit,
    onOpenLastTrain: () -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.home_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenCommutes) {
            Text(stringResource(R.string.home_commutes_button))
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onOpenLastTrain) {
            Text(stringResource(R.string.home_last_train_button))
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onOpenAccessibility) {
            Text(stringResource(R.string.home_access_button))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.home_hint_tabs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
