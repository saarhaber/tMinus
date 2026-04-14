package com.saarlabs.tminus.commute

import com.saarlabs.tminus.model.RouteType
import com.saarlabs.tminus.model.Stop
import com.saarlabs.tminus.model.WidgetTripData
import com.saarlabs.tminus.model.response.GlobalData
import com.saarlabs.tminus.model.response.ScheduleResponse
import com.saarlabs.tminus.util.EasternTimeInstant

/**
 * Finds the next trip between two stops whose departure falls in
 * [max(now, windowStart), windowEnd] (for commute windows).
 */
internal object CommuteTripPlanner {

    fun findNextTripInWindow(
        response: ScheduleResponse,
        globalData: GlobalData,
        fromStopId: String,
        toStopId: String,
        now: EasternTimeInstant,
        windowStart: EasternTimeInstant,
        windowEnd: EasternTimeInstant,
    ): WidgetTripData? {
        val stops = globalData.stops
        val fromSchedules =
            response.schedules.filter { Stop.equalOrFamily(it.stopId, fromStopId, stops) }
        val toSchedules =
            response.schedules.filter { Stop.equalOrFamily(it.stopId, toStopId, stops) }

        val tripPairs =
            fromSchedules.flatMap { fromSchedule ->
                toSchedules
                    .filter {
                        it.tripId == fromSchedule.tripId &&
                            it.stopSequence > fromSchedule.stopSequence
                    }
                    .map { toSchedule -> fromSchedule to toSchedule }
            }

        val lower = if (now > windowStart) now else windowStart

        val nextTrip =
            tripPairs
                .filter { (from, _) ->
                    val depTime = from.departureTime ?: from.arrivalTime ?: return@filter false
                    depTime >= lower && depTime <= windowEnd
                }
                .minByOrNull { (from, _) ->
                    val depTime = from.departureTime ?: from.arrivalTime!!
                    depTime.instant
                } ?: return null

        val (fromSchedule, toSchedule) = nextTrip
        val trip = response.trips[fromSchedule.tripId] ?: return null
        val route = globalData.getRoute(trip.routeId) ?: return null

        val fromResolved = globalData.getStop(fromStopId)!!.resolveParent(globalData.stops)
        val toResolved = globalData.getStop(toStopId)!!.resolveParent(globalData.stops)
        val fromScheduleStop = stops[fromSchedule.stopId] ?: fromResolved
        val toScheduleStop = stops[toSchedule.stopId] ?: toResolved

        val departureTime = fromSchedule.departureTime ?: fromSchedule.arrivalTime ?: return null
        val arrivalTime = toSchedule.arrivalTime ?: toSchedule.departureTime ?: return null

        val minutesUntil = (departureTime - now).inWholeMinutes.toInt().coerceAtLeast(0)

        val fromPlatform =
            if (fromScheduleStop.vehicleType == RouteType.COMMUTER_RAIL)
                fromScheduleStop.platformCode
            else null
        val toPlatform =
            if (toScheduleStop.vehicleType == RouteType.COMMUTER_RAIL) toScheduleStop.platformCode
            else null

        return WidgetTripData(
            fromStop = fromResolved,
            toStop = toResolved,
            route = route,
            tripId = trip.id,
            departureTime = departureTime,
            arrivalTime = arrivalTime,
            minutesUntil = minutesUntil,
            fromPlatform = fromPlatform,
            toPlatform = toPlatform,
            headsign = fromSchedule.stopHeadsign ?: trip.headsign,
        )
    }
}
