package com.saarlabs.tminus

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.saarlabs.tminus.ui.SettingsContent

/** Standalone entry for deep links or shortcuts; main flow uses the Settings tab in [MainActivity]. */
public class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences(SettingsKeys.PREFS, MODE_PRIVATE)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SettingsContent(
                        initialV3 = prefs.getString(SettingsKeys.KEY_V3_API, "") ?: "",
                        onSave = { v3 ->
                            prefs.edit()
                                .putString(SettingsKeys.KEY_V3_API, v3.ifBlank { null })
                                .commit()
                            GlobalDataStore.invalidate()
                            TminusApplication.refreshNetworking()
                            Toast.makeText(
                                this@SettingsActivity,
                                getString(R.string.settings_api_key_saved_snackbar),
                                Toast.LENGTH_SHORT,
                            ).show()
                            finish()
                        },
                    )
                }
            }
        }
    }
}
