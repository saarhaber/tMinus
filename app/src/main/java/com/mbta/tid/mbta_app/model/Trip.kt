package com.mbta.tid.mbta_app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class Trip(
    override val id: String,
    @SerialName("direction_id") val directionId: Int,
    val headsign: String,
    @SerialName("route_id") val routeId: String,
    @SerialName("route_pattern_id") val routePatternId: String? = null,
) : BackendObject<String>
