package com.saarlabs.tminus.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.saarlabs.tminus.SettingsKeys

/** Deep navy aligned with launcher canvas [ic_launcher_background] (#0B1426). */
private val BrandNavy = Color(0xFF0B1426)

/** Cool white aligned with surface; avoids Material light defaults (Neutral94 etc.) that read lavender. */
private val LightSurface = Color(0xFFF6F9FE)

private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF1F54A8),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD8E5FB),
        onPrimaryContainer = Color(0xFF001A41),
        secondary = Color(0xFF1B7F3F),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFBFEFCD),
        onSecondaryContainer = Color(0xFF00210F),
        tertiary = Color(0xFFB8227A),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFD7EA),
        onTertiaryContainer = Color(0xFF36002B),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        surface = LightSurface,
        onSurface = Color(0xFF14181F),
        surfaceVariant = Color(0xFFDFE4EE),
        onSurfaceVariant = Color(0xFF434853),
        outline = Color(0xFF727783),
        outlineVariant = Color(0xFFC2C7D2),
        background = LightSurface,
        onBackground = Color(0xFF14181F),
        surfaceTint = Color.Transparent,
        surfaceDim = Color(0xFFE4E8EF),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF1F4FA),
        surfaceContainer = Color(0xFFEAEEF5),
        surfaceContainerHigh = Color(0xFFE4E8F0),
        surfaceContainerHighest = Color(0xFFDDE2EB),
    )

/** Navy-aligned ramp; avoids Material defaults (e.g. Neutral12 #211F26) that read purple-grey. */
private val DarkSurface = Color(0xFF0F1620)

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFADC6FF),
        onPrimary = Color(0xFF002E6A),
        primaryContainer = Color(0xFF0F4594),
        onPrimaryContainer = Color(0xFFD8E5FB),
        secondary = Color(0xFF8DD3A6),
        onSecondary = Color(0xFF003918),
        secondaryContainer = Color(0xFF005427),
        onSecondaryContainer = Color(0xFFBFEFCD),
        tertiary = Color(0xFFFFAED4),
        onTertiary = Color(0xFF5C0047),
        tertiaryContainer = Color(0xFF860261),
        onTertiaryContainer = Color(0xFFFFD7EA),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        surface = DarkSurface,
        onSurface = Color(0xFFE2E6EE),
        surfaceVariant = Color(0xFF2A3140),
        onSurfaceVariant = Color(0xFFC2C7D2),
        outline = Color(0xFF8C919C),
        outlineVariant = Color(0xFF434853),
        background = DarkSurface,
        onBackground = Color(0xFFE2E6EE),
        surfaceTint = Color.Transparent,
        surfaceDim = Color(0xFF0B1118),
        surfaceBright = Color(0xFF232B37),
        surfaceContainerLowest = Color(0xFF070C12),
        surfaceContainerLow = Color(0xFF141B25),
        surfaceContainer = Color(0xFF19212C),
        surfaceContainerHigh = Color(0xFF212A36),
        surfaceContainerHighest = Color(0xFF2C3541),
    )

private fun TextStyle.scaled(scale: Float): TextStyle {
    val raw = fontSize
    val scaled: TextUnit = if (raw.isSp) (raw.value * scale).sp else raw
    return copy(fontSize = scaled)
}

private fun scaledTypography(base: Typography, scale: Float): Typography =
    Typography(
        displayLarge = base.displayLarge.scaled(scale),
        displayMedium = base.displayMedium.scaled(scale),
        displaySmall = base.displaySmall.scaled(scale),
        headlineLarge = base.headlineLarge.scaled(scale),
        headlineMedium = base.headlineMedium.scaled(scale),
        headlineSmall = base.headlineSmall.scaled(scale),
        titleLarge = base.titleLarge.scaled(scale),
        titleMedium = base.titleMedium.scaled(scale),
        titleSmall = base.titleSmall.scaled(scale),
        bodyLarge = base.bodyLarge.scaled(scale),
        bodyMedium = base.bodyMedium.scaled(scale),
        bodySmall = base.bodySmall.scaled(scale),
        labelLarge = base.labelLarge.scaled(scale),
        labelMedium = base.labelMedium.scaled(scale),
        labelSmall = base.labelSmall.scaled(scale),
    )

@Composable
public fun TminusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val fontScale = rememberUserFontScale()
    val baseTypography = MaterialTheme.typography
    val scaledTypo = remember(fontScale, baseTypography) { scaledTypography(baseTypography, fontScale) }
    CompositionLocalProvider(LocalAppFontScale provides fontScale) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = scaledTypo,
            content = content,
        )
    }
}

/**
 * Resolves whether to use the dark palette from [SettingsKeys.KEY_THEME_MODE], and recomposes when
 * that preference changes (e.g. from Settings).
 */
@Composable
public fun rememberUserDarkTheme(): Boolean {
    val context = LocalContext.current
    val prefs =
        remember(context) {
            context.getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)
        }
    var mode by remember {
        mutableStateOf(
            prefs.getString(SettingsKeys.KEY_THEME_MODE, SettingsKeys.THEME_SYSTEM)
                ?: SettingsKeys.THEME_SYSTEM,
        )
    }
    DisposableEffect(prefs) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { shared, key ->
                if (key == SettingsKeys.KEY_THEME_MODE) {
                    mode =
                        shared.getString(SettingsKeys.KEY_THEME_MODE, SettingsKeys.THEME_SYSTEM)
                            ?: SettingsKeys.THEME_SYSTEM
                }
            }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val isSystemDark = isSystemInDarkTheme()
    return when (mode) {
        SettingsKeys.THEME_LIGHT -> false
        SettingsKeys.THEME_DARK -> true
        else -> isSystemDark
    }
}
