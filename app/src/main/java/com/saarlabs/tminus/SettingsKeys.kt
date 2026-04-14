package com.saarlabs.tminus

internal object SettingsKeys {
    const val PREFS = "tminus_settings"
    const val KEY_V3_API = "mbta_v3_api_key"
    /** When true, times in pickers and summaries use 24-hour clock; when false, 12-hour with AM/PM. */
    const val KEY_USE_24_HOUR = "use_24_hour_time"
    /** Parent stop ids the user starred; used to pin stations to the top of stop lists. */
    const val KEY_FAVORITE_STOP_IDS = "favorite_stop_ids"
}
