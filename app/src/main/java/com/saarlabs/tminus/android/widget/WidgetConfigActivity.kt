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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.saarlabs.tminus.model.Stop
import com.saarlabs.tminus.model.WidgetTripConfig
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.model.response.GlobalData
import com.saarlabs.tminus.GlobalDataStore
import com.saarlabs.tminus.R
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

public class WidgetConfigActivity : ComponentActivity() {

    private val widgetPreferences: WidgetPreferences by lazy {
        WidgetPreferences(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var appWidgetId =
            intent?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            appWidgetId = widgetPreferences.getAndClearPendingConfigWidgetId()
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                WidgetConfigScreen(
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
private fun WidgetConfigScreen(
    appWidgetId: Int,
    widgetPreferences: WidgetPreferences,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var globalResponse by remember { mutableStateOf<GlobalData?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loadingTimeout by remember { mutableStateOf(false) }
    var reachableToStops by remember { mutableStateOf<List<Stop>?>(null) }
    var reachableLoadError by remember { mutableStateOf<String?>(null) }

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

    var fromStop by remember { mutableStateOf<Stop?>(null) }
    var toStop by remember { mutableStateOf<Stop?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(fromStop, globalResponse) {
        val g = globalResponse
        val from = fromStop
        reachableToStops = null
        reachableLoadError = null
        if (g != null && from != null) {
            when (
                val r =
                    withContext(Dispatchers.IO) {
                        GlobalDataStore.client.fetchReachableDestinationStops(from.id, g.stops)
                    }
            ) {
                is ApiResult.Ok -> reachableToStops = r.data
                is ApiResult.Error -> {
                    reachableToStops = emptyList()
                    reachableLoadError = r.message
                }
            }
        }
    }

    val selectableStops =
        remember(globalResponse, searchQuery, fromStop, reachableToStops, reachableLoadError) {
            globalResponse?.let { global ->
                val query = searchQuery.trim().lowercase()
                val stops =
                    if (fromStop == null) {
                        global.getParentStopsForSelection()
                    } else {
                        when {
                            reachableToStops == null -> emptyList()
                            reachableLoadError != null -> global.getParentStopsForSelection()
                            else -> reachableToStops!!
                        }
                    }
                if (query.isEmpty()) {
                    stops
                } else {
                    stops.filter { it.name.lowercase().contains(query) }
                }
            } ?: emptyList()
        }

    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_configure_title)) },
                navigationIcon = {
                    when {
                        toStop != null -> {
                            IconButton(onClick = { toStop = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            }
                        }
                        fromStop != null -> {
                            IconButton(onClick = {
                                fromStop = null
                                toStop = null
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            }
                        }
                        else -> {}
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
                if (fromStop != null) {
                    item(key = "from_chip") {
                        WidgetStopChip(
                            label = stringResource(R.string.widget_from),
                            stopName = fromStop!!.name,
                            onClear = {
                                fromStop = null
                                toStop = null
                            },
                        )
                    }
                }
                if (toStop != null) {
                    item(key = "to_chip") {
                        WidgetStopChip(
                            label = stringResource(R.string.widget_to),
                            stopName = toStop!!.name,
                            onClear = { toStop = null },
                        )
                    }
                }

                if (fromStop == null || toStop == null) {
                    item(key = "search") {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            placeholder = {
                                Text(
                                    stringResource(
                                        if (fromStop == null) R.string.widget_select_from_stop
                                        else R.string.widget_select_to_stop
                                    )
                                )
                            },
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
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.widget_loading_timeout_tminus),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
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
                            item(key = "section_header") {
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        text =
                                            stringResource(
                                                if (fromStop == null) R.string.widget_selecting_from
                                                else R.string.widget_selecting_to
                                            ),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    if (fromStop != null && reachableToStops == null && reachableLoadError == null) {
                                        Row(
                                            modifier = Modifier.padding(top = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                            )
                                            Text(
                                                text = stringResource(R.string.widget_loading_destinations),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    if (fromStop != null && reachableLoadError != null) {
                                        Text(
                                            text =
                                                stringResource(
                                                    R.string.widget_destinations_unavailable,
                                                    reachableLoadError!!,
                                                ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 6.dp),
                                        )
                                    }
                                }
                            }
                            items(selectableStops, key = { it.id }) { stop ->
                                val resolved = stop.resolveParent(global.stops)
                                Row(
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .clickable {
                                                if (fromStop == null) {
                                                    fromStop = resolved
                                                    searchQuery = ""
                                                } else {
                                                    if (resolved.id == fromStop!!.id) {
                                                        Toast.makeText(
                                                                context,
                                                                context.getString(
                                                                    R.string.widget_select_different_stops
                                                                ),
                                                                Toast.LENGTH_SHORT,
                                                            )
                                                            .show()
                                                    } else {
                                                        toStop = resolved
                                                        searchQuery = ""

                                                        val fromResolved =
                                                            global
                                                                .getStop(fromStop!!.id)
                                                                ?.resolveParent(global.stops)
                                                        val toResolved =
                                                            global
                                                                .getStop(toStop!!.id)
                                                                ?.resolveParent(global.stops)
                                                        if (
                                                            fromResolved != null && toResolved != null
                                                        ) {
                                                            val config =
                                                                WidgetTripConfig(
                                                                    fromStopId = fromResolved.id,
                                                                    toStopId = toResolved.id,
                                                                    fromLabel = fromResolved.name,
                                                                    toLabel = toResolved.name,
                                                                )
                                                            coroutineScope.launch {
                                                                try {
                                                                    withContext(Dispatchers.IO) {
                                                                        widgetPreferences.setConfig(
                                                                            appWidgetId,
                                                                            config,
                                                                        )
                                                                        MBTATripWidget()
                                                                            .updateAll(
                                                                                context.applicationContext,
                                                                            )
                                                                    }
                                                                    WidgetUpdateWorker.enqueueRefresh(
                                                                        context,
                                                                        intArrayOf(appWidgetId),
                                                                    )
                                                                    onComplete()
                                                                } catch (e: Exception) {
                                                                    android.util.Log.e(
                                                                        "WidgetConfig",
                                                                        "Failed to save widget config",
                                                                        e,
                                                                    )
                                                                    Toast.makeText(
                                                                            context,
                                                                            context.getString(
                                                                                R.string.widget_save_error
                                                                            ),
                                                                            Toast.LENGTH_LONG,
                                                                        )
                                                                        .show()
                                                                }
                                                            }
                                                        } else {
                                                            Toast.makeText(
                                                                    context,
                                                                    context.getString(
                                                                        R.string.widget_save_error
                                                                    ),
                                                                    Toast.LENGTH_LONG,
                                                                )
                                                                .show()
                                                        }
                                                    }
                                                }
                                            }
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                RoundedCornerShape(8.dp),
                                            )
                                            .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = resolved.name,
                                        style = MaterialTheme.typography.bodyLarge,
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
        TextButton(onClick = onClear) { Text(stringResource(R.string.widget_clear_stop)) }
    }
}
