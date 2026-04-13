package com.mbta.tid.mbta_app.model

/** GTFS route types as returned by the MBTA V3 API on [Route.type]. */
public enum class RouteType {
    LIGHT_RAIL,
    HEAVY_RAIL,
    COMMUTER_RAIL,
    BUS,
    FERRY,
    ;

    public fun isSubway(): Boolean = this === HEAVY_RAIL || this === LIGHT_RAIL

    public companion object {
        public fun fromGtfsType(type: Int): RouteType =
            when (type) {
                0 -> LIGHT_RAIL
                1 -> HEAVY_RAIL
                2 -> COMMUTER_RAIL
                3 -> BUS
                4 -> FERRY
                else -> BUS
            }
    }
}
