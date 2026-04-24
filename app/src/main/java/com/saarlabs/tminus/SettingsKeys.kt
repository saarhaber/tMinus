package com.saarlabs.tminus

internal object SettingsKeys {
    const val PREFS = "tminus_settings"
    const val KEY_V3_API = "mbta_v3_api_key"
    /** When true, times in pickers and summaries use 24-hour clock; when false, 12-hour with AM/PM. */
    const val KEY_USE_24_HOUR = "use_24_hour_time"
    /** [THEME_SYSTEM], [THEME_LIGHT], or [THEME_DARK] — controls app Material theme vs system night mode. */
    const val KEY_THEME_MODE = "theme_mode"
    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    /** Parent stop ids the user starred; used to pin stations to the top of stop lists. */
    const val KEY_FAVORITE_STOP_IDS = "favorite_stop_ids"
    /**
     * Text scale multiplier (stored as Int percent, 80..160) applied across the app and home-screen
     * widgets. Defaults to 100 (= 1.0x).
     */
    const val KEY_FONT_SCALE_PERCENT = "font_scale_percent"
    const val FONT_SCALE_DEFAULT_PERCENT = 100
    const val FONT_SCALE_MIN_PERCENT = 80
    const val FONT_SCALE_MAX_PERCENT = 160
}
