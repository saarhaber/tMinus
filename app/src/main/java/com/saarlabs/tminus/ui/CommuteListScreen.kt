package com.saarlabs.tminus.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.saarlabs.tminus.MainActivity
import com.saarlabs.tminus.R
import com.saarlabs.tminus.commute.CommuteProfile
import com.saarlabs.tminus.commute.CommuteRepository
import kotlinx.coroutines.launch
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun CommuteListScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repo = remember(context) { CommuteRepository(context) }
    var profiles by remember { mutableStateOf<List<CommuteProfile>>(emptyList()) }
    val scope = rememberCoroutineScope()

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        profiles = repo.loadProfiles()
    }

    LaunchedEffect(profiles) {
        if (profiles.any { it.enabled } && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val reorderState =
        rememberReorderableLazyListState(onMove = { from, to ->
            profiles =
                profiles.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                }
            scope.launch {
                repo.saveProfiles(profiles)
            }
        })

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.commute_list_title)) },
                navigationIcon = {
                    TextButton(onClick = { navController.popBackStack() }) {
                        Text(stringResource(R.string.commute_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("${MainActivity.ROUTE_COMMUTE_EDIT}/new") },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.commute_add))
            }
        },
    ) { padding ->
        if (profiles.isEmpty()) {
            Column(
                modifier =
                    Modifier.padding(padding)
                        .padding(24.dp)
                        .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.commute_list_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            Column(
                modifier =
                    Modifier.padding(padding)
                        .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.commute_list_reorder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyColumn(
                    state = reorderState.listState,
                    modifier =
                        Modifier.fillMaxSize()
                            .reorderable(reorderState)
                            .detectReorderAfterLongPress(reorderState),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(profiles, key = { it.id }) { p ->
                        ReorderableItem(reorderState, key = p.id) { isDragging ->
                            val elevation by animateDpAsState(
                                if (isDragging) 8.dp else 0.dp,
                                label = "commute_card_elevation",
                            )
                            val dismissState =
                                rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value == SwipeToDismissBoxValue.EndToStart) {
                                            scope.launch {
                                                profiles = profiles.filter { it.id != p.id }
                                                repo.saveProfiles(profiles)
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                )
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.CenterEnd,
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.commute_list_delete),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(end = 24.dp),
                                        )
                                    }
                                },
                            ) {
                                Card(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .shadow(elevation)
                                            .clickable {
                                                navController.navigate(
                                                    "${MainActivity.ROUTE_COMMUTE_EDIT}/${p.id}",
                                                )
                                            },
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${p.fromLabel.ifBlank { p.fromStopId }} → ${p.toLabel.ifBlank { p.toStopId }}",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            stringResource(
                                                R.string.commute_list_summary,
                                                formatTime(p.targetMinutesFromMidnight),
                                                p.notifyLeadMinutes,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(minutesFromMidnight: Int): String {
    val h = minutesFromMidnight / 60
    val m = minutesFromMidnight % 60
    return "%d:%02d".format(h, m)
}
