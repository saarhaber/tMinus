package com.mbta.tid.mbta_app.model

import com.mbta.tid.mbta_app.utils.EasternTimeInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class Schedule(
    override val id: String,
    @SerialName("arrival_time") val arrivalTime: EasternTimeInstant?,
    @SerialName("departure_time") val departureTime: EasternTimeInstant?,
    @SerialName("stop_headsign") val stopHeadsign: String?,
    @SerialName("stop_sequence") val stopSequence: Int,
    @SerialName("stop_id") val stopId: String,
    @SerialName("trip_id") val tripId: String,
) : BackendObject<String>
