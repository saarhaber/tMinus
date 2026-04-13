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
import com.saarlabs.tminus.ui.CommuteEditorRoute
import com.saarlabs.tminus.ui.CommuteListScreen
import com.saarlabs.tminus.ui.RoadmapScreen
import com.saarlabs.tminus.ui.SettingsContent

public class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences(SettingsKeys.PREFS, MODE_PRIVATE)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val rootNav = rememberNavController()
                    NavHost(
                        navController = rootNav,
                        startDestination = ROUTE_MAIN_TABS,
                    ) {
                        composable(ROUTE_MAIN_TABS) {
                            TminusApp(
                                rootNavController = rootNav,
                                initialV3 = prefs.getString(SettingsKeys.KEY_V3_API, "") ?: "",
                                initialGtfs = prefs.getString(SettingsKeys.KEY_GTFS_RT, "") ?: "",
                                onSaveSettings = { v3, gtfs ->
                                    prefs.edit()
                                        .putString(SettingsKeys.KEY_V3_API, v3.ifBlank { null })
                                        .putString(SettingsKeys.KEY_GTFS_RT, gtfs.ifBlank { null })
                                        .apply()
                                    GlobalDataStore.invalidate()
                                    TminusApplication.refreshNetworking()
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
                    }
                }
            }
        }
    }

    public companion object {
        public const val ROUTE_MAIN_TABS: String = "main_tabs"
        public const val ROUTE_COMMUTE_LIST: String = "commute_list"
        public const val ROUTE_COMMUTE_EDIT: String = "commute_edit"
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
    initialGtfs: String,
    onSaveSettings: (String, String) -> Unit,
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
                )
            }
            composable(MainDestination.Roadmap.route) {
                RoadmapScreen()
            }
            composable(MainDestination.Settings.route) {
                SettingsContent(
                    initialV3 = initialV3,
                    initialGtfs = initialGtfs,
                    onSave = onSaveSettings,
                )
            }
        }
    }
}

@Composable
private fun HomeTab(onOpenCommutes: () -> Unit) {
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
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.home_hint_tabs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
