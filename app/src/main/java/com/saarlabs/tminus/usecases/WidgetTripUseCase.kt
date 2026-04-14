package com.saarlabs.tminus.usecases

import com.saarlabs.tminus.model.RouteType
import com.saarlabs.tminus.model.Stop
import com.saarlabs.tminus.model.WidgetTripData
import com.saarlabs.tminus.model.WidgetTripOutput
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.model.response.GlobalData
import com.saarlabs.tminus.model.response.ScheduleResponse
import com.saarlabs.tminus.network.MbtaV3Client
import com.saarlabs.tminus.util.EasternTimeInstant

/**
 * Next trip between two stops (ported from [mbta/mobile_app#1593](https://github.com/mbta/mobile_app/pull/1593)),
 * using the public MBTA V3 API instead of the MBTA Go backend.
 */
public class WidgetTripUseCase(private val client: MbtaV3Client) {

    public suspend fun getNextTrip(
        globalData: GlobalData,
        fromStopId: String,
        toStopId: String,
        now: EasternTimeInstant = EasternTimeInstant.now(),
    ): ApiResult<WidgetTripOutput> {
        val fromStop = globalData.getStop(fromStopId) ?: return ApiResult.Ok(WidgetTripOutput(null))
        val toStop = globalData.getStop(toStopId) ?: return ApiResult.Ok(WidgetTripOutput(null))

        val fromStopIds =
            listOf(fromStopId) + fromStop.childStopIds.filter { globalData.stops.containsKey(it) }
        val toStopIds =
            listOf(toStopId) + toStop.childStopIds.filter { globalData.stops.containsKey(it) }
        val requestStopIds = (fromStopIds + toStopIds).distinct()

        val scheduleResult = client.fetchScheduleForStops(requestStopIds, now)
        val scheduleResponse =
            when (scheduleResult) {
                is ApiResult.Ok -> scheduleResult.data
                is ApiResult.Error ->
                    return ApiResult.Error(
                        code = scheduleResult.code,
                        message = scheduleResult.message,
                    )
            }

        val tripData =
            findNextTrip(scheduleResponse, globalData, fromStopId, toStopId, now)
        return ApiResult.Ok(WidgetTripOutput(tripData))
    }

    private fun findNextTrip(
        response: ScheduleResponse,
        globalData: GlobalData,
        fromStopId: String,
        toStopId: String,
        now: EasternTimeInstant,
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

        val nextTrip =
            tripPairs
                .filter { (from, _) ->
                    val depTime = from.departureTime ?: from.arrivalTime ?: return@filter false
                    depTime >= now
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
