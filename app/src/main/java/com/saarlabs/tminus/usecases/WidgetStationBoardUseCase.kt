package com.saarlabs.tminus.usecases

import com.saarlabs.tminus.model.RouteType
import com.saarlabs.tminus.model.Schedule
import com.saarlabs.tminus.model.Stop
import com.saarlabs.tminus.model.Trip
import com.saarlabs.tminus.model.WidgetStationBoardDeparture
import com.saarlabs.tminus.model.WidgetStationBoardOutput
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.model.response.GlobalData
import com.saarlabs.tminus.model.response.ScheduleResponse
import com.saarlabs.tminus.network.MbtaV3Client
import com.saarlabs.tminus.util.EasternTimeInstant

public class WidgetStationBoardUseCase(private val client: MbtaV3Client) {

    public suspend fun getDepartures(
        globalData: GlobalData,
        stopId: String,
        routeFilter: String?,
        destinationFilter: String?,
        now: EasternTimeInstant,
        limit: Int,
    ): ApiResult<WidgetStationBoardOutput> {
        val stop = globalData.getStop(stopId) ?: return ApiResult.Ok(WidgetStationBoardOutput(emptyList()))
        val stopIds = globalData.stopIdsForScheduleFilter(stop)

        val scheduleResult = client.fetchScheduleForStops(stopIds, now)
        val scheduleResponse =
            when (scheduleResult) {
                is ApiResult.Ok -> scheduleResult.data
                is ApiResult.Error ->
                    return ApiResult.Error(
                        code = scheduleResult.code,
                        message = scheduleResult.message,
                    )
            }

        val departures =
            buildDepartures(
                scheduleResponse = scheduleResponse,
                globalData = globalData,
                configuredStopId = stopId,
                routeFilter = routeFilter,
                destinationFilter = destinationFilter,
                now = now,
                limit = limit,
            )
        return ApiResult.Ok(WidgetStationBoardOutput(departures))
    }

    private fun buildDepartures(
        scheduleResponse: ScheduleResponse,
        globalData: GlobalData,
        configuredStopId: String,
        routeFilter: String?,
        destinationFilter: String?,
        now: EasternTimeInstant,
        limit: Int,
    ): List<WidgetStationBoardDeparture> {
        val stops = globalData.stops
        val relevant =
            scheduleResponse.schedules.filter { schedule ->
                Stop.equalOrFamily(schedule.stopId, configuredStopId, stops)
            }

        val candidates = mutableListOf<Triple<Schedule, Trip, EasternTimeInstant>>()

        for (schedule in relevant) {
            val depTime = schedule.departureTime ?: schedule.arrivalTime ?: continue
            if (depTime < now) continue
            val trip = scheduleResponse.trips[schedule.tripId] ?: continue
            if (routeFilter != null && trip.routeId != routeFilter) continue
            val headsignForFilter =
                schedule.stopHeadsign?.takeIf { it.isNotBlank() } ?: trip.headsign
            if (!matchesDestinationFilter(headsignForFilter, destinationFilter)) continue
            candidates.add(Triple(schedule, trip, depTime))
        }

        val byTripId =
            candidates
                .groupBy { it.second.id }
                .mapValues { (_, list) ->
                    list.minBy { (s, _, t) -> t.instant }
                }
                .values
                .sortedBy { it.third.instant }

        val out = mutableListOf<WidgetStationBoardDeparture>()
        for ((schedule, trip, depTime) in byTripId) {
            if (out.size >= limit) break
            val route = globalData.getRoute(trip.routeId) ?: continue
            val scheduleStop = stops[schedule.stopId] ?: globalData.getStop(configuredStopId)!!.resolveParent(stops)
            val platform =
                if (scheduleStop.vehicleType == RouteType.COMMUTER_RAIL) scheduleStop.platformCode
                else null
            val headsign = schedule.stopHeadsign?.takeIf { it.isNotBlank() } ?: trip.headsign
            val minutesUntil = (depTime - now).inWholeMinutes.toInt().coerceAtLeast(0)
            out.add(
                WidgetStationBoardDeparture(
                    route = route,
                    headsign = headsign,
                    departureTime = depTime,
                    minutesUntil = minutesUntil,
                    platform = platform,
                ),
            )
        }
        return out
    }

    /** Matches MBTA headsign text to a chosen direction destination (case-insensitive). */
    private fun matchesDestinationFilter(headsign: String, filter: String?): Boolean {
        if (filter.isNullOrBlank()) return true
        val f = filter.trim().lowercase()
        val h = headsign.trim().lowercase()
        if (h == f) return true
        val hPrimary = h.substringBefore(" - ").trim()
        if (hPrimary == f) return true
        if (h.startsWith(f)) return true
        return false
    }
}
