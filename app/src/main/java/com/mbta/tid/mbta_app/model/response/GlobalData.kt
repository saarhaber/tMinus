package com.mbta.tid.mbta_app.model.response

import com.mbta.tid.mbta_app.model.LocationType
import com.mbta.tid.mbta_app.model.Stop

/** Stops and routes loaded from the MBTA V3 API for search and labels. */
public data class GlobalData(
    val stops: Map<String, Stop>,
    val routes: Map<String, com.mbta.tid.mbta_app.model.Route>,
) {
    public fun getStop(id: String?): Stop? = id?.let { stops[it] }

    public fun getRoute(id: String?): com.mbta.tid.mbta_app.model.Route? = id?.let { routes[it] }

    public fun getParentStopsForSelection(): List<Stop> =
        stops.values
            .filter { it.parentStationId == null }
            .filter { it.locationType == LocationType.STATION || it.locationType == LocationType.STOP }
            .sortedBy { it.name }
}
