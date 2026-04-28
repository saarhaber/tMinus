package com.saarlabs.tminus.model

import kotlinx.serialization.Serializable

@Serializable
public data class WidgetStationBoardConfig(
    val stopId: String,
    val stopLabel: String = "",
    /** When null, show all routes serving the stop. */
    val routeId: String? = null,
    /**
     * When non-null and non-blank, only trips toward this terminal/direction (matches schedule /
     * trip headsign). Taken from the route's MBTA `direction_destinations`, e.g. South Station or Worcester.
     */
    val destinationHeadsign: String? = null,
)
