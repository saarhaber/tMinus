package com.saarlabs.tminus

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.saarlabs.tminus.ui.theme.TminusTheme
import com.saarlabs.tminus.ui.theme.rememberUserDarkTheme
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
import com.saarlabs.tminus.ui.NotificationPermissionGate
import com.saarlabs.tminus.ui.SettingsContent
import com.saarlabs.tminus.android.widget.WidgetUpdateWorker

public class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences(SettingsKeys.PREFS, MODE_PRIVATE)
        setContent {
            val darkTheme = rememberUserDarkTheme()
            TminusTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    NotificationPermissionGate()
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
                                    runCatching { TminusApplication.refreshNetworking() }
                                        .onFailure {
                                            Log.e("MainActivity", "refreshNetworking failed", it)
                                        }
                                    settingsV3 = prefs.getString(SettingsKeys.KEY_V3_API, "") ?: ""
                                    settingsUse24Hour =
                                        prefs.getBoolean(SettingsKeys.KEY_USE_24_HOUR, false)
                                    runCatching {
                                        WidgetUpdateWorker.enqueueRefresh(
                                            this@MainActivity,
                                            appWidgetIds = null,
                                        )
                                    }.onFailure { Log.e("MainActivity", "enqueueRefresh failed", it) }
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

    data object Settings : MainDestination("settings", R.string.nav_settings, Icons.Default.Settings)

    companion object {
        val entries = listOf(Home, Settings)
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
                    val tabLabel = stringResource(dest.labelRes)
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
                        icon = { Icon(dest.icon, contentDescription = tabLabel) },
                        label = { Text(tabLabel) },
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
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        HomeHeader()
        Spacer(Modifier.height(24.dp))

        FeatureCard(
            title = stringResource(R.string.home_commutes_button),
            subtitle = stringResource(R.string.home_feature_commutes_desc),
            icon = Icons.Filled.DirectionsTransit,
            gradient =
                Brush.linearGradient(
                    listOf(
                        scheme.primary,
                        scheme.primaryContainer,
                    ),
                ),
            iconTint = scheme.onPrimary,
            titleColor = scheme.onPrimary,
            subtitleColor = scheme.onPrimary.copy(alpha = 0.85f),
            onClick = onOpenCommutes,
        )
        Spacer(Modifier.height(12.dp))
        FeatureCard(
            title = stringResource(R.string.home_last_train_button),
            subtitle = stringResource(R.string.home_feature_last_train_desc),
            icon = Icons.Filled.NightsStay,
            gradient =
                Brush.linearGradient(
                    listOf(
                        scheme.tertiary,
                        scheme.tertiaryContainer,
                    ),
                ),
            iconTint = scheme.onTertiary,
            titleColor = scheme.onTertiary,
            subtitleColor = scheme.onTertiary.copy(alpha = 0.85f),
            onClick = onOpenLastTrain,
        )
        Spacer(Modifier.height(12.dp))
        FeatureCard(
            title = stringResource(R.string.home_access_button),
            subtitle = stringResource(R.string.home_feature_access_desc),
            icon = Icons.Filled.Accessible,
            gradient =
                Brush.linearGradient(
                    listOf(
                        scheme.secondary,
                        scheme.secondaryContainer,
                    ),
                ),
            iconTint = scheme.onSecondary,
            titleColor = scheme.onSecondary,
            subtitleColor = scheme.onSecondary.copy(alpha = 0.85f),
            onClick = onOpenAccessibility,
        )

        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.home_tip_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.home_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.home_hint_tabs),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HomeHeader() {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)),
                    ),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.DirectionsTransit,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                color = scheme.onSurface,
            )
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: Brush,
    iconTint: Color,
    titleColor: Color,
    subtitleColor: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(gradient)
                    .padding(PaddingValues(horizontal = 18.dp, vertical = 18.dp)),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.20f)),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = titleColor,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = iconTint,
                )
            }
        }
    }
}
