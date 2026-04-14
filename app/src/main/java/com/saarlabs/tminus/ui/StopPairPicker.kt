package com.saarlabs.tminus.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saarlabs.tminus.model.Stop
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.model.response.GlobalData
import com.saarlabs.tminus.GlobalDataStore
import com.saarlabs.tminus.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
public fun StopPairPicker(
    onStopsChosen: (from: Stop, to: Stop) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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
    var saveValidationMessage by remember { mutableStateOf<String?>(null) }

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
                if (query.isEmpty()) stops else stops.filter { it.name.lowercase().contains(query) }
            } ?: emptyList()
        }

    Column(modifier = modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.commute_cancel)) }
            Button(
                onClick = {
                    saveValidationMessage = null
                    val f = fromStop
                    val t = toStop
                    val g = globalResponse
                    val issues = mutableListOf<String>()
                    if (g == null) {
                        issues.add(context.getString(R.string.commute_save_need_stops_loading))
                    }
                    if (f == null) {
                        issues.add(context.getString(R.string.commute_validation_need_from_stop))
                    }
                    if (t == null) {
                        issues.add(context.getString(R.string.commute_validation_need_to_stop))
                    }
                    if (issues.isNotEmpty()) {
                        saveValidationMessage = issues.joinToString("\n")
                        return@Button
                    }
                    val fr = g!!.getStop(f!!.id)?.resolveParent(g.stops)
                    val tr = g.getStop(t!!.id)?.resolveParent(g.stops)
                    if (fr == null || tr == null) {
                        saveValidationMessage = context.getString(R.string.commute_save_stops_unresolved)
                        return@Button
                    }
                    onStopsChosen(fr, tr)
                },
            ) {
                Text(stringResource(R.string.commute_use_stops))
            }
        }
        saveValidationMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (fromStop != null) {
            StopChip(
                label = stringResource(R.string.widget_from),
                name = fromStop!!.name,
                onClear = {
                    fromStop = null
                    toStop = null
                },
            )
        }
        if (toStop != null) {
            StopChip(
                label = stringResource(R.string.widget_to),
                name = toStop!!.name,
                onClear = { toStop = null },
            )
        }

        if (fromStop == null || toStop == null) {
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

            when {
                globalResponse == null && loadError != null -> {
                    Column {
                        Text(stringResource(R.string.widget_loading_timeout_tminus))
                        Text(
                            loadError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                globalResponse == null && loadingTimeout -> {
                    Text(stringResource(R.string.widget_loading_timeout_tminus))
                }
                globalResponse == null -> {
                    Column(
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
                else -> {
                    val global = globalResponse!!
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
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
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                                            context.getString(R.string.widget_select_different_stops),
                                                            Toast.LENGTH_SHORT,
                                                        )
                                                        .show()
                                                } else {
                                                    toStop = resolved
                                                    searchQuery = ""
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
                                Text(resolved.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StopChip(label: String, name: String, onClear: () -> Unit) {
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
        Text("$label: $name", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onClear) { Text(stringResource(R.string.widget_clear_stop)) }
    }
}
