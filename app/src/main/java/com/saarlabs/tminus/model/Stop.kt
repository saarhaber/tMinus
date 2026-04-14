package com.saarlabs.tminus.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class Stop(
    override val id: String,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    @SerialName("location_type") val locationType: LocationType,
    @SerialName("platform_code") val platformCode: String? = null,
    @SerialName("vehicle_type") val vehicleType: RouteType? = null,
    @SerialName("child_stop_ids") val childStopIds: List<String> = emptyList(),
    @SerialName("parent_station_id") val parentStationId: String? = null,
) : BackendObject<String> {

    internal fun resolveParent(stops: Map<String, Stop>): Stop {
        if (parentStationId == null) return this
        val parentStation = stops[parentStationId] ?: return this
        return parentStation.resolveParent(stops)
    }


    public companion object {
        public fun equalOrFamily(stopId1: String, stopId2: String, stops: Map<String, Stop>): Boolean {
            if (stopId1 == stopId2) return true
            val stop1 = stops[stopId1] ?: return false
            val stop2 = stops[stopId2] ?: return false
            val parent1 = stop1.resolveParent(stops)
            val parent2 = stop2.resolveParent(stops)
            return parent1.id == parent2.id
        }
    }
}
