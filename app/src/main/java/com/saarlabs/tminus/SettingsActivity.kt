package com.saarlabs.tminus

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** Persisted keys — also used from [TminusApplication]. */
internal fun getV3ApiKey(context: android.content.Context): String? =
    context.getSharedPreferences(SettingsKeys.PREFS, android.content.Context.MODE_PRIVATE)
        .getString(SettingsKeys.KEY_V3_API, null)

internal fun getGtfsRtKey(context: android.content.Context): String? =
    context.getSharedPreferences(SettingsKeys.PREFS, android.content.Context.MODE_PRIVATE)
        .getString(SettingsKeys.KEY_GTFS_RT, null)

public class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences(SettingsKeys.PREFS, MODE_PRIVATE)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(
                        initialV3 = prefs.getString(SettingsKeys.KEY_V3_API, "") ?: "",
                        initialGtfs = prefs.getString(SettingsKeys.KEY_GTFS_RT, "") ?: "",
                        onSave = { v3, gtfs ->
                            prefs.edit()
                                .putString(SettingsKeys.KEY_V3_API, v3.ifBlank { null })
                                .putString(SettingsKeys.KEY_GTFS_RT, gtfs.ifBlank { null })
                                .apply()
                            GlobalDataStore.invalidate()
                            TminusApplication.refreshNetworking()
                            finish()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    initialV3: String,
    initialGtfs: String,
    onSave: (v3: String, gtfs: String) -> Unit,
) {
    var v3 by remember { mutableStateOf(initialV3) }
    var gtfs by remember { mutableStateOf(initialGtfs) }
    val context = LocalContext.current

    Column(
        modifier =
            Modifier.padding(20.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "API keys",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text =
                "Tminus uses the MBTA V3 API for schedules and stops. " +
                    "Optionally add keys from both MBTA developer sites below (recommended for higher rate limits). " +
                    "The GTFS Realtime key is reserved for future widgets (alerts, live feeds).",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))

        DocLink(
            label = "V3 API portal — request a free key",
            url = "https://api-v3.mbta.com/",
        )
        Spacer(Modifier.height(8.dp))
        DocLink(
            label = "V3 API reference (Swagger)",
            url = "https://api-v3.mbta.com/docs/swagger/index.html",
        )
        Spacer(Modifier.height(8.dp))
        DocLink(
            label = "GTFS Realtime (MBTA developers)",
            url = "https://www.mbta.com/developers/gtfs-realtime",
        )
        Spacer(Modifier.height(8.dp))
        DocLink(
            label = "GTFS Realtime feeds (cdn.mbta.com)",
            url = "https://www.mbta.com/developers/gtfs-realtime",
        )

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = v3,
            onValueChange = { v3 = it },
            label = { Text("V3 API key (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = gtfs,
            onValueChange = { gtfs = it },
            label = { Text("GTFS Realtime / second key (optional, future use)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onSave(v3, gtfs) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save")
        }
    }
}

@Composable
private fun DocLink(label: String, url: String) {
    val context = LocalContext.current
    val ann =
        buildAnnotatedString {
            pushStringAnnotation(tag = "URL", annotation = url)
            withStyle(
                style =
                    SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                    ),
            ) {
                append(label)
            }
            pop()
        }
    ClickableText(
        text = ann,
        onClick = { offset ->
            ann.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()
                ?.let {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.item)))
                }
        },
    )
}
