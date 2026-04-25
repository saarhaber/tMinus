package com.saarlabs.tminus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.saarlabs.tminus.FavoriteStopsStore
import com.saarlabs.tminus.sortStopsWithFavoritesFirst
import com.saarlabs.tminus.model.Stop
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.model.response.GlobalData
import com.saarlabs.tminus.GlobalDataStore
import com.saarlabs.tminus.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
public fun StopSearchPicker(
    onStopChosen: (Stop) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val favoriteStore = remember(context) { FavoriteStopsStore(context) }
    var favoriteIds by remember { mutableStateOf(favoriteStore.getIds()) }
    var global by remember { mutableStateOf<GlobalData?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        loadError = null
        when (val r = withContext(Dispatchers.IO) { GlobalDataStore.getOrLoad() }) {
            is ApiResult.Ok -> global = r.data
            is ApiResult.Error -> loadError = r.message
        }
    }

    val stops =
        remember(global, query, favoriteIds) {
            global?.getParentStopsForSelection()?.let { list ->
                val q = query.trim().lowercase()
                val filtered = if (q.isEmpty()) list else list.filter { it.name.lowercase().contains(q) }
                sortStopsWithFavoritesFirst(filtered, favoriteIds, global!!.stops)
            } ?: emptyList()
        }

    if (global == null) {
        if (loadError != null) {
            Column(modifier = modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.widget_loading_timeout_tminus),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            CircularProgressIndicator(modifier = modifier.padding(24.dp))
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text(stringResource(R.string.stop_search_hint)) },
            singleLine = true,
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(stops, key = { it.id }) { stop ->
                val g = global!!
                val resolved = stop.resolveParent(g.stops)
                val fav = resolved.id in favoriteIds
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                                stringResource(if (fav) R.string.stop_unfavorite else R.string.stop_favorite),
                            tint =
                                if (fav) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = resolved.name,
                        modifier =
                            Modifier.weight(1f)
                                .clickable { onStopChosen(resolved) }
                                .padding(top = 16.dp, bottom = 16.dp, end = 16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
