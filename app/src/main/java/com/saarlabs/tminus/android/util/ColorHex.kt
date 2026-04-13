package com.saarlabs.tminus.android.util

import androidx.compose.ui.graphics.Color

internal fun colorFromHex(hex: String): Color {
    val clean = hex.trim().removePrefix("#")
    val v = clean.toLong(16)
    return Color(0xFF000000L or v)
}
