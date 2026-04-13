package com.mbta.tid.mbta_app.model

import com.mbta.tid.mbta_app.utils.EasternTimeInstant

public data class WidgetTripData(
    val fromStop: Stop,
    val toStop: Stop,
    val route: Route,
    val tripId: String,
    val departureTime: EasternTimeInstant,
    val arrivalTime: EasternTimeInstant,
    val minutesUntil: Int,
    val fromPlatform: String?,
    val toPlatform: String?,
    val headsign: String?,
)
