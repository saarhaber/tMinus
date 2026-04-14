package com.saarlabs.tminus.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class Route(
    override val id: String,
    val type: RouteType,
    val color: String,
    @SerialName("direction_names") val directionNames: List<String?>,
    @SerialName("direction_destinations") val directionDestinations: List<String?>,
    @SerialName("listed_route") val isListedRoute: Boolean = true,
    @SerialName("long_name") val longName: String,
    @SerialName("short_name") val shortName: String,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("text_color") val textColor: String,
) : BackendObject<String>, Comparable<Route> {

    override fun compareTo(other: Route): Int = sortOrder.compareTo(other.sortOrder)

    val label: String
        get() =
            when (type) {
                RouteType.BUS -> shortName
                RouteType.COMMUTER_RAIL -> longName.replace("/", " / ")
                else -> longName
            }
}
