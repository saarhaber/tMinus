package com.saarlabs.tminus.android.util

import com.saarlabs.tminus.util.EasternTimeInstant

internal fun EasternTimeInstant.formattedTime(): String {
    val h = local.hour
    val min = local.minute
    val am = h < 12
    val h12 = ((h + 11) % 12) + 1
    return "%d:%02d %s".format(h12, min, if (am) "AM" else "PM")
}
