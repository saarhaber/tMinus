package com.saarlabs.tminus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mbta.tid.mbta_app.model.Stop
import com.mbta.tid.mbta_app.model.response.ApiResult
import com.mbta.tid.mbta_app.model.response.GlobalData
import com.saarlabs.tminus.GlobalDataStore
import com.saarlabs.tminus.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
public fun StopSearchPicker(
    onStopChosen: (Stop) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        remember(global, query) {
            global?.getParentStopsForSelection()?.let { list ->
                val q = query.trim().lowercase()
                if (q.isEmpty()) list else list.filter { it.name.lowercase().contains(q) }
            } ?: emptyList()
        }

    if (global == null) {
        if (loadError != null) {
            Column(modifier = modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.widget_loading_timeout_tminus),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    loadError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
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
                val resolved = stop.resolveParent(global!!.stops)
                Text(
                    text = resolved.name,
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable { onStopChosen(resolved) }
                            .padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
