package com.saarlabs.tminus.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.saarlabs.tminus.model.Route
import com.saarlabs.tminus.model.Stop
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.model.response.GlobalData
import com.saarlabs.tminus.GlobalDataStore
import com.saarlabs.tminus.R
import com.saarlabs.tminus.features.AccessibilityWatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun AccessibilityEditorScreen(
    initial: AccessibilityWatch?,
    onSave: (AccessibilityWatch) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var routeId by remember { mutableStateOf(initial?.routeId ?: "Orange") }
    var stop by remember { mutableStateOf<Stop?>(null) }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }
    var showStopDialog by remember { mutableStateOf(false) }
    var globalData by remember { mutableStateOf<GlobalData?>(null) }
    var routeMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        when (val r = withContext(Dispatchers.IO) { GlobalDataStore.getOrLoad() }) {
            is ApiResult.Ok -> {
                globalData = r.data
                if (initial != null) {
                    stop = r.data.getStop(initial.stopId)?.resolveParent(r.data.stops)
                }
            }
            is ApiResult.Error -> {}
        }
    }

    val routeList: List<Route> =
        remember(globalData) { globalData?.let { routesForDropdown(it.routes) } ?: emptyList() }
    val selectedRoute: Route? = routeList.find { it.id == routeId }

    Column(
        modifier =
            Modifier.verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        Text(stringResource(R.string.access_editor_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.access_help), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.access_name)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (routeList.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = routeMenuExpanded,
                onExpandedChange = { routeMenuExpanded = !routeMenuExpanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = selectedRoute?.label ?: routeId,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.access_route)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = routeMenuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    singleLine = true,
                )
                ExposedDropdownMenu(
                    expanded = routeMenuExpanded,
                    onDismissRequest = { routeMenuExpanded = false },
                ) {
                    routeList.forEach { r ->
                        DropdownMenuItem(
                            text = { Text("${r.label} (${r.id})") },
                            onClick = {
                                routeId = r.id
                                routeMenuExpanded = false
                            },
                        )
                    }
                }
            }
            if (selectedRoute == null) {
                Text(
                    stringResource(R.string.last_train_route_unknown, routeId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            OutlinedTextField(
                value = routeId,
                onValueChange = { routeId = it },
                label = { Text(stringResource(R.string.access_route)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        Button(onClick = { showStopDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    R.string.access_stop_picked,
                    stop?.name ?: "—",
                ),
            )
        }
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.commute_enabled))
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val s = stop ?: return@Button
                onSave(
                    AccessibilityWatch(
                        id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.trim(),
                        routeId = routeId.trim(),
                        stopId = s.id,
                        stopLabel = s.name,
                        enabled = enabled,
                    ),
                )
            },
            enabled =
                name.isNotBlank() &&
                    stop != null &&
                    routeId.isNotBlank() &&
                    (routeList.isEmpty() || selectedRoute != null),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.commute_save))
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.commute_cancel))
        }
    }

    if (showStopDialog) {
        Dialog(onDismissRequest = { showStopDialog = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(500.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(Modifier.padding(8.dp)) {
                    TextButton(onClick = { showStopDialog = false }) {
                        Text(stringResource(R.string.commute_cancel))
                    }
                    StopSearchPicker(
                        onStopChosen = {
                            stop = it
                            showStopDialog = false
                        },
                    )
                }
            }
        }
    }
}
