package com.saarlabs.tminus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.saarlabs.tminus.model.Stop
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.util.EasternTimeInstant
import com.saarlabs.tminus.GlobalDataStore
import com.saarlabs.tminus.R
import com.saarlabs.tminus.commute.CommuteProfile
import com.saarlabs.tminus.commute.CommuteTripPlanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.toInstant
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun CommuteEditorScreen(
    initial: CommuteProfile?,
    onSave: (CommuteProfile) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var days by remember {
        mutableStateOf((initial?.daysOfWeek ?: listOf(1, 2, 3, 4, 5)).toSet())
    }
    var hourStr by remember {
        mutableStateOf(((initial?.targetMinutesFromMidnight ?: 8 * 60 + 30) / 60).toString())
    }
    var minStr by remember {
        mutableStateOf(((initial?.targetMinutesFromMidnight ?: 8 * 60 + 30) % 60).toString().padStart(2, '0'))
    }
    var winBefore by remember { mutableStateOf((initial?.windowMinutesBefore ?: 45).toString()) }
    var winAfter by remember { mutableStateOf((initial?.windowMinutesAfter ?: 45).toString()) }
    var leadMin by remember { mutableStateOf((initial?.notifyLeadMinutes ?: 12).toString()) }
    var notifyArrival by remember { mutableStateOf(initial?.notifyOnArrival ?: true) }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }

    var fromStop by remember { mutableStateOf<Stop?>(null) }
    var toStop by remember { mutableStateOf<Stop?>(null) }
    var pickingStops by remember { mutableStateOf(initial == null) }

    var previewText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(initial) {
        if (initial != null) {
            val g = GlobalDataStore.getOrLoad()
            if (g is ApiResult.Ok) {
                fromStop = g.data.getStop(initial.fromStopId)?.resolveParent(g.data.stops)
                toStop = g.data.getStop(initial.toStopId)?.resolveParent(g.data.stops)
            }
        }
    }

    if (pickingStops) {
        StopPairPicker(
            onStopsChosen = { f, t ->
                fromStop = f
                toStop = t
                pickingStops = false
            },
            onCancel = onCancel,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.commute_editor_title), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.commute_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        TextButton(onClick = { pickingStops = true }) {
            Text(
                stringResource(
                    R.string.commute_stops_summary,
                    fromStop?.name ?: "—",
                    toStop?.name ?: "—",
                ),
            )
        }

        Text(stringResource(R.string.commute_days), style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val dayLabels =
                listOf(
                    1 to R.string.day_mon,
                    2 to R.string.day_tue,
                    3 to R.string.day_wed,
                    4 to R.string.day_thu,
                    5 to R.string.day_fri,
                    6 to R.string.day_sat,
                    7 to R.string.day_sun,
                )
            dayLabels.forEach { (dow, labelRes) ->
                FilterChip(
                    selected = days.contains(dow),
                    onClick = {
                        days =
                            if (days.contains(dow)) days - dow else days + dow
                    },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }

        Text(stringResource(R.string.commute_target_time), style = MaterialTheme.typography.titleSmall)
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = hourStr,
                onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) hourStr = it },
                label = { Text(stringResource(R.string.commute_hour)) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = minStr,
                onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) minStr = it },
                label = { Text(stringResource(R.string.commute_minute)) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        OutlinedTextField(
            value = winBefore,
            onValueChange = { winBefore = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(R.string.commute_window_before)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = winAfter,
            onValueChange = { winAfter = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(R.string.commute_window_after)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = leadMin,
            onValueChange = { leadMin = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(R.string.commute_lead_minutes)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.commute_notify_arrival))
            Switch(checked = notifyArrival, onCheckedChange = { notifyArrival = it })
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
                scope.launch {
                    val f = fromStop
                    val t = toStop
                    if (f == null || t == null) {
                        previewText = context.getString(R.string.commute_preview_need_stops)
                        return@launch
                    }
                    val globalResult = withContext(Dispatchers.IO) { GlobalDataStore.getOrLoad() }
                    val global =
                        when (globalResult) {
                            is ApiResult.Ok -> globalResult.data
                            is ApiResult.Error -> {
                                previewText = globalResult.message
                                return@launch
                            }
                        }
                    val hour = hourStr.toIntOrNull()?.coerceIn(0, 23) ?: 8
                    val minute = minStr.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    val targetMinutes = hour * 60 + minute
                    val wb = winBefore.toIntOrNull()?.coerceIn(0, 180) ?: 45
                    val wa = winAfter.toIntOrNull()?.coerceIn(0, 180) ?: 45
                    val windowStart = max(0, targetMinutes - wb)
                    val windowEnd = min(24 * 60 - 1, targetMinutes + wa)
                    val minH = windowStart / 60
                    val minM = windowStart % 60
                    val maxH = windowEnd / 60
                    val maxM = windowEnd % 60
                    val minTime =
                        "${minH.toString().padStart(2, '0')}:${minM.toString().padStart(2, '0')}"
                    val maxTime =
                        "${maxH.toString().padStart(2, '0')}:${maxM.toString().padStart(2, '0')}"
                    val fromIds =
                        listOf(f.id) + f.childStopIds.filter { global.stops.containsKey(it) }
                    val toIds =
                        listOf(t.id) + t.childStopIds.filter { global.stops.containsKey(it) }
                    val stopIds = (fromIds + toIds).distinct()
                    val schedResult =
                        withContext(Dispatchers.IO) {
                            GlobalDataStore.client.fetchScheduleForStopsInWindow(
                                stopIds,
                                minTime,
                                maxTime,
                            )
                        }
                    val schedule =
                        when (schedResult) {
                            is ApiResult.Ok -> schedResult.data
                            is ApiResult.Error -> {
                                previewText = schedResult.message
                                return@launch
                            }
                        }
                    val now = EasternTimeInstant.now()
                    val tz = EasternTimeInstant.timeZone
                    val today = now.local.date
                    val windowStartEt =
                        EasternTimeInstant(
                            kotlinx.datetime.LocalDateTime(
                                today,
                                kotlinx.datetime.LocalTime(minH, minM, 0, 0),
                            ).toInstant(tz),
                        )
                    val windowEndEt =
                        EasternTimeInstant(
                            kotlinx.datetime.LocalDateTime(
                                today,
                                kotlinx.datetime.LocalTime(maxH, maxM, 0, 0),
                            ).toInstant(tz),
                        )
                    val trip =
                        CommuteTripPlanner.findNextTripInWindow(
                            schedule,
                            global,
                            f.id,
                            t.id,
                            now,
                            windowStartEt,
                            windowEndEt,
                        )
                    val lead = leadMin.toIntOrNull()?.coerceIn(1, 120) ?: 12
                    previewText =
                        if (trip != null) {
                            val leave = trip.departureTime.minus(lead.minutes)
                            "${trip.route.label} · ${trip.headsign ?: trip.tripId}\n" +
                                "${context.getString(R.string.commute_preview_dep)} ${trip.departureTime.local}\n" +
                                "${context.getString(R.string.commute_preview_arr)} ${trip.arrivalTime.local}\n" +
                                "${context.getString(R.string.commute_preview_leave)} ${leave.local}"
                        } else {
                            context.getString(R.string.commute_preview_none)
                        }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.commute_preview_button))
        }

        RowHorizontalButtons(onCancel = onCancel, onSave = {
            val f = fromStop
            val t = toStop
            if (f == null || t == null || name.isBlank()) return@RowHorizontalButtons
            val hour = hourStr.toIntOrNull()?.coerceIn(0, 23) ?: 8
            val minute = minStr.toIntOrNull()?.coerceIn(0, 59) ?: 0
            val targetMinutes = hour * 60 + minute
            val profile =
                CommuteProfile(
                    id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                    name = name.trim(),
                    fromStopId = f.id,
                    toStopId = t.id,
                    fromLabel = f.name,
                    toLabel = t.name,
                    daysOfWeek = days.sorted(),
                    targetMinutesFromMidnight = targetMinutes,
                    windowMinutesBefore = winBefore.toIntOrNull()?.coerceIn(5, 180) ?: 45,
                    windowMinutesAfter = winAfter.toIntOrNull()?.coerceIn(5, 180) ?: 45,
                    notifyLeadMinutes = leadMin.toIntOrNull()?.coerceIn(1, 120) ?: 12,
                    notifyOnArrival = notifyArrival,
                    enabled = enabled,
                )
            onSave(profile)
        })
    }

    previewText?.let { msg ->
        AlertDialog(
            onDismissRequest = { previewText = null },
            confirmButton = {
                TextButton(onClick = { previewText = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            title = { Text(stringResource(R.string.commute_preview_title)) },
            text = { Text(msg) },
        )
    }
}

@Composable
private fun RowHorizontalButtons(onCancel: () -> Unit, onSave: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.commute_cancel))
        }
        Button(onClick = onSave, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.commute_save))
        }
    }
}
