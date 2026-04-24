package com.saarlabs.tminus.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.saarlabs.tminus.SettingsKeys

/**
 * Multiplies any `sp` text size in the app (and widgets) so the user's preferred reading size wins
 * even when specific composables hard-code sizes. 1.0f = default. Range [0.80, 1.60].
 */
public val LocalAppFontScale: androidx.compose.runtime.ProvidableCompositionLocal<Float> =
    staticCompositionLocalOf { 1.0f }

/** Reads the persisted font scale from prefs, with live updates when the user changes it. */
@Composable
public fun rememberUserFontScale(): Float {
    val context = LocalContext.current
    val prefs =
        remember(context) {
            context.getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)
        }
    var percent by remember {
        mutableStateOf(
            prefs.getInt(
                SettingsKeys.KEY_FONT_SCALE_PERCENT,
                SettingsKeys.FONT_SCALE_DEFAULT_PERCENT,
            ),
        )
    }
    DisposableEffect(prefs) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { shared, key ->
                if (key == SettingsKeys.KEY_FONT_SCALE_PERCENT) {
                    percent =
                        shared.getInt(
                            SettingsKeys.KEY_FONT_SCALE_PERCENT,
                            SettingsKeys.FONT_SCALE_DEFAULT_PERCENT,
                        )
                }
            }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val clamped =
        percent.coerceIn(
            SettingsKeys.FONT_SCALE_MIN_PERCENT,
            SettingsKeys.FONT_SCALE_MAX_PERCENT,
        )
    return clamped / 100f
}

/** Synchronous read for non-Compose contexts (widgets, workers). */
public fun readFontScale(context: Context): Float {
    val percent =
        context.getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)
            .getInt(
                SettingsKeys.KEY_FONT_SCALE_PERCENT,
                SettingsKeys.FONT_SCALE_DEFAULT_PERCENT,
            )
    val clamped =
        percent.coerceIn(
            SettingsKeys.FONT_SCALE_MIN_PERCENT,
            SettingsKeys.FONT_SCALE_MAX_PERCENT,
        )
    return clamped / 100f
}
