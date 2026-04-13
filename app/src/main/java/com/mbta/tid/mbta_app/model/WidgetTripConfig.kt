package com.mbta.tid.mbta_app.model

import kotlinx.serialization.Serializable

@Serializable
public data class WidgetTripConfig(
    val fromStopId: String,
    val toStopId: String,
    val fromLabel: String = "",
    val toLabel: String = "",
)
