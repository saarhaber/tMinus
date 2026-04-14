package com.saarlabs.tminus.model.response

/** Minimal alert payload for accessibility / elevator notifications. */
public data class MbtaAlertSummary(
    val id: String,
    val header: String,
    val effect: String?,
)
