package com.saarlabs.tminus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.saarlabs.tminus.ui.SettingsContent

/** Standalone entry for deep links or shortcuts; main flow uses the Settings tab in [MainActivity]. */
public class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences(SettingsKeys.PREFS, MODE_PRIVATE)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsContent(
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
