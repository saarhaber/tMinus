package com.saarlabs.tminus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.saarlabs.tminus.model.Route
import com.saarlabs.tminus.model.Stop
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.model.response.GlobalData
import com.saarlabs.tminus.GlobalDataStore
import com.saarlabs.tminus.R
import com.saarlabs.tminus.features.LastTrainMode
import com.saarlabs.tminus.features.LastTrainProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
public fun LastTrainEditorScreen(
    initial: LastTrainProfile?,
    onSave: (LastTrainProfile) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var routeId by remember { mutableStateOf(initial?.routeId ?: "Orange") }
    var directionId by remember { mutableStateOf(initial?.directionId ?: 0) }
    var mode by remember { mutableStateOf(initial?.mode ?: LastTrainMode.LAST) }
    var stop by remember { mutableStateOf<Stop?>(null) }
    var notifyMin by remember { mutableStateOf((initial?.notifyMinutesBefore ?: 45).toString()) }
    var winStart by remember { mutableStateOf((initial?.windowStartMinutes ?: 18 * 60).toString()) }
    var winEnd by remember { mutableStateOf((initial?.windowEndMinutes ?: 23 * 60 + 59).toString()) }
    var firstStart by remember { mutableStateOf((initial?.firstWindowStartMinutes ?: 4 * 60).toString()) }
    var firstEnd by remember { mutableStateOf((initial?.firstWindowEndMinutes ?: 10 * 60).toString()) }
    var days by remember {
        mutableStateOf((initial?.daysOfWeek ?: listOf(1, 2, 3, 4, 5, 6, 7)).toSet())
    }
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.last_train_editor_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.last_train_help), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.last_train_name)) },
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
                    label = { Text(stringResource(R.string.last_train_route)) },
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
                label = { Text(stringResource(R.string.last_train_route)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        Text(stringResource(R.string.last_train_direction), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 1).forEach { d ->
                androidx.compose.material3.FilterChip(
                    selected = directionId == d,
                    onClick = { directionId = d },
                    label = { Text(directionLabelForRoute(selectedRoute, d)) },
                )
            }
        }

        Text(stringResource(R.string.last_train_mode_label), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.FilterChip(
                selected = mode == LastTrainMode.LAST,
                onClick = { mode = LastTrainMode.LAST },
                label = { Text(stringResource(R.string.last_train_mode_last)) },
            )
            androidx.compose.material3.FilterChip(
                selected = mode == LastTrainMode.FIRST,
                onClick = { mode = LastTrainMode.FIRST },
                label = { Text(stringResource(R.string.last_train_mode_first)) },
            )
        }

        Button(onClick = { showStopDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    R.string.last_train_stop_picked,
                    stop?.name ?: "—",
                ),
            )
        }

        OutlinedTextField(
            value = notifyMin,
            onValueChange = { notifyMin = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(R.string.last_train_notify_before)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        when (mode) {
            LastTrainMode.LAST -> {
                OutlinedTextField(
                    value = winStart,
                    onValueChange = { winStart = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.last_train_window_start_min)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = winEnd,
                    onValueChange = { winEnd = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.last_train_window_end_min)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LastTrainMode.FIRST -> {
                OutlinedTextField(
                    value = firstStart,
                    onValueChange = { firstStart = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.last_train_first_window_start)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = firstEnd,
                    onValueChange = { firstEnd = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.last_train_first_window_end)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Text(stringResource(R.string.commute_days), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                1 to R.string.day_mon,
                2 to R.string.day_tue,
                3 to R.string.day_wed,
                4 to R.string.day_thu,
                5 to R.string.day_fri,
                6 to R.string.day_sat,
                7 to R.string.day_sun,
            ).forEach { (dow, labelRes) ->
                androidx.compose.material3.FilterChip(
                    selected = days.contains(dow),
                    onClick = {
                        days = if (days.contains(dow)) days - dow else days + dow
                    },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }

        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.commute_enabled))
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val s = stop ?: return@Button
                val profile =
                    LastTrainProfile(
                        id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.trim(),
                        routeId = routeId.trim(),
                        directionId = directionId,
                        stopId = s.id,
                        stopLabel = s.name,
                        mode = mode,
                        daysOfWeek = days.sorted(),
                        notifyMinutesBefore = notifyMin.toIntOrNull()?.coerceIn(5, 180) ?: 45,
                        windowStartMinutes = winStart.toIntOrNull()?.coerceIn(0, 24 * 60) ?: 18 * 60,
                        windowEndMinutes = winEnd.toIntOrNull()?.coerceIn(0, 24 * 60 - 1) ?: 23 * 60 + 59,
                        firstWindowStartMinutes = firstStart.toIntOrNull()?.coerceIn(0, 24 * 60) ?: 4 * 60,
                        firstWindowEndMinutes = firstEnd.toIntOrNull()?.coerceIn(0, 24 * 60) ?: 10 * 60,
                        enabled = enabled,
                    )
                onSave(profile)
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
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
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
