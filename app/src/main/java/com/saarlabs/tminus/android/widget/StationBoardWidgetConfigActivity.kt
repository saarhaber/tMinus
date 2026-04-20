package com.saarlabs.tminus.android.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.saarlabs.tminus.ui.theme.TminusTheme
import com.saarlabs.tminus.ui.theme.rememberUserDarkTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saarlabs.tminus.FavoriteStopsStore
import com.saarlabs.tminus.GlobalDataStore
import com.saarlabs.tminus.R
import com.saarlabs.tminus.model.Route
import com.saarlabs.tminus.model.Stop
import com.saarlabs.tminus.model.WidgetStationBoardConfig
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.model.response.GlobalData
import com.saarlabs.tminus.sortStopsWithFavoritesFirst
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

public class StationBoardWidgetConfigActivity : ComponentActivity() {

    private val widgetPreferences: WidgetPreferences by lazy {
        WidgetPreferences(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val extraFromIntent =
            intent?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        var appWidgetId = extraFromIntent
        val usedPendingFallback = appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID
        if (usedPendingFallback) {
            appWidgetId = widgetPreferences.getAndClearPendingStationBoardConfigWidgetId()
        }
        // #region agent log
        AgentDebugLog.log(
            "StationBoardWidgetConfigActivity.kt:onCreate",
            "station configure id resolution",
            "H1",
            mapOf(
                "activity" to "station_board",
                "extraFromIntent" to extraFromIntent,
                "usedPendingFallback" to usedPendingFallback,
                "finalAppWidgetId" to appWidgetId,
                "willFinishInvalid" to (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID),
            ),
        )
        // #endregion

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            val darkTheme = rememberUserDarkTheme()
            TminusTheme(darkTheme = darkTheme) {
                StationBoardWidgetConfigScreen(
                    appWidgetId = appWidgetId,
                    widgetPreferences = widgetPreferences,
                    onComplete = {
                        val resultIntent =
                            Intent().apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    },
                    onCancel = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationBoardWidgetConfigScreen(
    appWidgetId: Int,
    widgetPreferences: WidgetPreferences,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val favoriteStore = remember(context) { FavoriteStopsStore(context) }
    var favoriteIds by remember { mutableStateOf(favoriteStore.getIds()) }
    val coroutineScope = rememberCoroutineScope()
    var globalResponse by remember { mutableStateOf<GlobalData?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loadingTimeout by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loadError = null
        val result = withContext(Dispatchers.IO) { GlobalDataStore.getOrLoad() }
        when (result) {
            is ApiResult.Ok -> globalResponse = result.data
            is ApiResult.Error -> loadError = result.message
        }
    }

    LaunchedEffect(globalResponse) {
        if (globalResponse == null) {
            delay(12_000)
            loadingTimeout = true
        } else {
            loadingTimeout = false
        }
    }

    var selectedStop by remember { mutableStateOf<Stop?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var routes by remember { mutableStateOf<List<Route>?>(null) }
    var routesError by remember { mutableStateOf<String?>(null) }
    var selectedRouteId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedStop) {
        val stop = selectedStop
        routes = null
        routesError = null
        selectedRouteId = null
        if (stop == null) return@LaunchedEffect
        when (val r = withContext(Dispatchers.IO) { GlobalDataStore.client.fetchRoutesForStop(stop.id) }) {
            is ApiResult.Ok -> routes = r.data
            is ApiResult.Error -> {
                routes = emptyList()
                routesError = r.message
            }
        }
    }

    val selectableStops =
        remember(globalResponse, searchQuery, favoriteIds) {
            globalResponse?.let { global ->
                val query = searchQuery.trim().lowercase()
                val stops = global.getParentStopsForSelection()
                val filtered =
                    if (query.isEmpty()) {
                        stops
                    } else {
                        stops.filter { it.name.lowercase().contains(query) }
                    }
                sortStopsWithFavoritesFirst(filtered, favoriteIds, global.stops)
            } ?: emptyList()
        }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_station_board_configure_title)) },
                navigationIcon = {
                    if (selectedStop != null) {
                        IconButton(onClick = { selectedStop = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier.padding(padding)
                    .fillMaxSize(),
        ) {
            LazyColumn(
                modifier =
                    Modifier.weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (selectedStop != null) {
                    item(key = "stop_chip") {
                        WidgetStopChip(
                            label = stringResource(R.string.widget_station_board_selecting_stop),
                            stopName = selectedStop!!.resolveParent(globalResponse!!.stops).name,
                            onClear = { selectedStop = null },
                        )
                    }
                    item(key = "routes") {
                        RouteFilterSection(
                            routes = routes,
                            routesError = routesError,
                            selectedRouteId = selectedRouteId,
                            onSelectAll = { selectedRouteId = null },
                            onSelectRoute = { selectedRouteId = it },
                        )
                    }
                    item(key = "save") {
                        Button(
                            onClick = {
                                val global = globalResponse ?: return@Button
                                val stop = selectedStop ?: return@Button
                                val resolved = stop.resolveParent(global.stops)
                                val config =
                                    WidgetStationBoardConfig(
                                        stopId = resolved.id,
                                        stopLabel = resolved.name,
                                        routeId = selectedRouteId,
                                    )
                                coroutineScope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            widgetPreferences.setStationBoardConfig(appWidgetId, config)
                                        }
                                        val appContext = context.applicationContext
                                        onComplete()
                                        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                                            updateStationBoardWidgetWithRetry(appContext, appWidgetId)
                                            WidgetUpdateWorker.enqueueRefresh(appContext, intArrayOf(appWidgetId))
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("StationBoardWidgetConfig", "save failed", e)
                                        Toast.makeText(
                                                context,
                                                context.getString(R.string.widget_save_error),
                                                Toast.LENGTH_LONG,
                                            )
                                            .show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        ) {
                            Text(stringResource(R.string.widget_station_board_save))
                        }
                    }
                }

                if (selectedStop == null) {
                    item(key = "search") {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            placeholder = { Text(stringResource(R.string.widget_station_board_select_stop_placeholder)) },
                            singleLine = true,
                        )
                    }

                    when {
                        globalResponse == null && loadError != null -> {
                            item(key = "load_error") {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.widget_loading_timeout_tminus),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = loadError!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        globalResponse == null && loadingTimeout -> {
                            item(key = "loading_timeout") {
                                Text(
                                    text = stringResource(R.string.widget_loading_timeout_tminus),
                                    modifier = Modifier.padding(32.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        globalResponse == null -> {
                            item(key = "loading") {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    CircularProgressIndicator()
                                    Text(
                                        text = stringResource(R.string.loading),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                        else -> {
                            val global = globalResponse!!
                            item(key = "header") {
                                Text(
                                    text = stringResource(R.string.widget_station_board_selecting_stop),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                            items(selectableStops, key = { it.id }) { stop ->
                                val resolved = stop.resolveParent(global.stops)
                                val fav = resolved.id in favoriteIds
                                Row(
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                RoundedCornerShape(8.dp),
                                            )
                                            .padding(vertical = 4.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(
                                        onClick = {
                                            favoriteStore.toggle(resolved.id)
                                            favoriteIds = favoriteStore.getIds()
                                        },
                                    ) {
                                        Icon(
                                            imageVector = if (fav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                            contentDescription =
                                                context.getString(
                                                    if (fav) R.string.stop_unfavorite else R.string.stop_favorite,
                                                ),
                                            tint =
                                                if (fav) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = resolved.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier =
                                            Modifier.weight(1f)
                                                .clickable {
                                                    selectedStop = resolved
                                                    searchQuery = ""
                                                }
                                                .padding(vertical = 8.dp, horizontal = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun RouteFilterSection(
    routes: List<Route>?,
    routesError: String?,
    selectedRouteId: String?,
    onSelectAll: () -> Unit,
    onSelectRoute: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.widget_station_board_route_section),
            style = MaterialTheme.typography.titleSmall,
        )
        if (routes == null && routesError == null) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.widget_station_board_loading_routes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (routesError != null) {
            Text(
                text = routesError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        routes?.let { list ->
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedRouteId == null,
                    onClick = onSelectAll,
                    label = { Text(stringResource(R.string.widget_station_board_route_all)) },
                )
                for (route in list) {
                    FilterChip(
                        selected = selectedRouteId == route.id,
                        onClick = { onSelectRoute(route.id) },
                        label = { Text(route.label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetStopChip(label: String, stopName: String, onClear: () -> Unit) {
    Row(
        modifier =
            Modifier.padding(vertical = 4.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp),
                )
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "$label: $stopName", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onClear) {
            Text(stringResource(R.string.widget_clear_stop))
        }
    }
}
