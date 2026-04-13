package com.mbta.tid.mbta_app.network

import com.mbta.tid.mbta_app.model.LocationType
import com.mbta.tid.mbta_app.model.Route
import com.mbta.tid.mbta_app.model.RouteType
import com.mbta.tid.mbta_app.model.Stop
import com.mbta.tid.mbta_app.model.Trip
import com.mbta.tid.mbta_app.model.response.ApiResult
import com.mbta.tid.mbta_app.model.response.GlobalData
import com.mbta.tid.mbta_app.model.response.ScheduleResponse
import com.mbta.tid.mbta_app.utils.EasternTimeInstant
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * MBTA V3 JSON:API ([https://www.mbta.com/developers/v3-api](https://www.mbta.com/developers/v3-api)).
 * Optional [apiKey] maps to the V3 portal ([https://api-v3.mbta.com/](https://api-v3.mbta.com/)).
 */
public class MbtaV3Client(private val apiKey: String?) {

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

    private val httpClient =
        HttpClient(Android) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
            }
            defaultRequest {
                url("https://api-v3.mbta.com/")
                header(HttpHeaders.Accept, "application/vnd.api+json")
                apiKey?.takeIf { it.isNotBlank() }?.let { header("x-api-key", it) }
            }
        }

    public suspend fun fetchGlobalData(): ApiResult<GlobalData> =
        withContext(Dispatchers.IO) {
            try {
                val stops = linkedMapOf<String, Stop>()
                paginateJsonApi(
                    path = "stops",
                    configure = {
                        parameter("filter[location_type]", "0,1")
                        parameter("page[limit]", "500")
                    },
                ) { doc ->
                    mergeIncludedStops(doc, stops)
                    for (el in doc["data"]?.jsonArray.orEmpty()) {
                        val o = el.jsonObject
                        if (o["type"]?.jsonPrimitive?.content != "stop") continue
                        val id = o["id"]?.jsonPrimitive?.content ?: continue
                        parseStopFromResource(o)?.let { stops[id] = it }
                    }
                }

                val routes = linkedMapOf<String, Route>()
                paginateJsonApi(
                    path = "routes",
                    configure = { parameter("page[limit]", "100") },
                ) { doc ->
                    for (el in doc["data"]?.jsonArray.orEmpty()) {
                        val o = el.jsonObject
                        if (o["type"]?.jsonPrimitive?.content != "route") continue
                        val id = o["id"]?.jsonPrimitive?.content ?: continue
                        parseRoute(o)?.let { routes[id] = it }
                    }
                }

                ApiResult.Ok(GlobalData(stops = stops, routes = routes))
            } catch (e: Throwable) {
                ApiResult.Error(message = e.message ?: e.toString())
            }
        }

    /**
     * Parent stations/stops reachable from [fromStopId] without transfers, using route patterns
     * through that stop (same idea as PR #1593).
     */
    public suspend fun fetchReachableDestinationStops(
        fromStopId: String,
        allStops: Map<String, Stop>,
    ): ApiResult<List<Stop>> =
        withContext(Dispatchers.IO) {
            try {
                val reachable = mutableSetOf<String>()
                paginateJsonApi(
                    path = "route_patterns",
                    configure = {
                        parameter("filter[stop]", fromStopId)
                        parameter("page[limit]", "50")
                    },
                ) { doc ->
                    for (el in doc["data"]?.jsonArray.orEmpty()) {
                        val patternObj = el.jsonObject
                        val patternId = patternObj["id"]?.jsonPrimitive?.content ?: continue
                        val repTripId =
                            patternObj["relationships"]
                                ?.jsonObject
                                ?.get("representative_trip")
                                ?.jsonObject
                                ?.get("data")
                                ?.jsonObject
                                ?.get("id")
                                ?.jsonPrimitive
                                ?.content
                        val tripId =
                            resolveSampleTripId(patternId, repTripId) ?: continue

                        val schedDoc =
                            httpClient
                                .get("schedules") {
                                    parameter("filter[trip]", tripId)
                                    parameter("include", "stop")
                                    parameter("page[limit]", "200")
                                }
                                .body<JsonObject>()

                        mergeIncludedStops(schedDoc, allStops.toMutableMap())

                        val schedData = schedDoc["data"]?.jsonArray ?: continue
                        val orderedStopIds =
                            schedData
                                .mapNotNull { it.jsonObject }
                                .sortedBy {
                                    it["attributes"]
                                        ?.jsonObject
                                        ?.get("stop_sequence")
                                        ?.jsonPrimitive
                                        ?.intOrNull ?: 0
                                }
                                .mapNotNull { sObj ->
                                    sObj["relationships"]
                                        ?.jsonObject
                                        ?.get("stop")
                                        ?.jsonObject
                                        ?.get("data")
                                        ?.jsonObject
                                        ?.get("id")
                                        ?.jsonPrimitive
                                        ?.content
                                }

                        val fromIdx =
                            orderedStopIds.indexOfFirst { sid ->
                                Stop.equalOrFamily(sid, fromStopId, allStops)
                            }
                        if (fromIdx < 0 || fromIdx >= orderedStopIds.lastIndex) continue
                        for (i in (fromIdx + 1) until orderedStopIds.size) {
                            val sid = orderedStopIds[i]
                            val raw = allStops[sid] ?: continue
                            val parent = raw.resolveParent(allStops)
                            reachable.add(parent.id)
                        }
                    }
                }

                ApiResult.Ok(
                    reachable
                        .mapNotNull { allStops[it] }
                        .filter { it.parentStationId == null }
                        .sortedBy { it.name },
                )
            } catch (e: Throwable) {
                ApiResult.Error(message = e.message ?: e.toString())
            }
        }

    private suspend fun resolveSampleTripId(
        patternId: String,
        representativeTripId: String?,
    ): String? {
        if (representativeTripId != null && !representativeTripId.startsWith("canonical-")) {
            return representativeTripId
        }
        val tripsDoc =
            httpClient
                .get("trips") {
                    parameter("filter[route_pattern]", patternId)
                    parameter("page[limit]", "5")
                }
                .body<JsonObject>()
        return tripsDoc["data"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("id")
            ?.jsonPrimitive
            ?.content
            ?.takeIf { !it.startsWith("canonical-") }
    }

    public suspend fun fetchScheduleForStops(
        stopIds: List<String>,
        now: EasternTimeInstant,
    ): ApiResult<ScheduleResponse> =
        withContext(Dispatchers.IO) {
            try {
                val minTime =
                    "${now.local.hour.toString().padStart(2, '0')}:${now.local.minute.toString().padStart(2, '0')}"
                val doc =
                    httpClient
                        .get("schedules") {
                            parameter("filter[stop]", stopIds.joinToString(","))
                            parameter("filter[min_time]", minTime)
                            parameter("filter[max_time]", "23:59")
                            parameter("include", "trip,route")
                            parameter("page[limit]", "500")
                        }
                        .body<JsonObject>()

                val schedules = mutableListOf<com.mbta.tid.mbta_app.model.Schedule>()
                val trips = mutableMapOf<String, Trip>()

                for (el in doc["data"]?.jsonArray.orEmpty()) {
                    val sObj = el.jsonObject
                    val id = sObj["id"]?.jsonPrimitive?.content ?: continue
                    val attrs = sObj["attributes"]?.jsonObject ?: continue
                    val tripId =
                        sObj["relationships"]
                            ?.jsonObject
                            ?.get("trip")
                            ?.jsonObject
                            ?.get("data")
                            ?.jsonObject
                            ?.get("id")
                            ?.jsonPrimitive
                            ?.content ?: continue

                    val depStr =
                        attrs["departure_time"]?.jsonPrimitive?.content
                            ?: attrs["arrival_time"]?.jsonPrimitive?.content
                    val arrStr =
                        attrs["arrival_time"]?.jsonPrimitive?.content
                            ?: attrs["departure_time"]?.jsonPrimitive?.content
                    val dep = depStr?.let { EasternTimeInstant(Instant.parse(it)) }
                    val arr = arrStr?.let { EasternTimeInstant(Instant.parse(it)) }
                    val stopId =
                        sObj["relationships"]
                            ?.jsonObject
                            ?.get("stop")
                            ?.jsonObject
                            ?.get("data")
                            ?.jsonObject
                            ?.get("id")
                            ?.jsonPrimitive
                            ?.content ?: continue
                    val seq = attrs["stop_sequence"]?.jsonPrimitive?.intOrNull ?: continue
                    val headsign = attrs["stop_headsign"]?.jsonPrimitive?.content

                    schedules.add(
                        com.mbta.tid.mbta_app.model.Schedule(
                            id = id,
                            arrivalTime = arr,
                            departureTime = dep,
                            stopHeadsign = headsign,
                            stopSequence = seq,
                            stopId = stopId,
                            tripId = tripId,
                        ),
                    )

                    val tripIncluded = findIncluded(doc, "trip", tripId)
                    if (tripIncluded != null && !trips.containsKey(tripId)) {
                        parseTrip(tripIncluded)?.let { trips[tripId] = it }
                    }
                }

                ApiResult.Ok(ScheduleResponse(schedules = schedules, trips = trips))
            } catch (e: Throwable) {
                ApiResult.Error(message = e.message ?: e.toString())
            }
        }

    private suspend inline fun paginateJsonApi(
        path: String,
        crossinline configure: HttpRequestBuilder.() -> Unit = {},
        crossinline eachPage: suspend (JsonObject) -> Unit,
    ) {
        var next: String? = null
        var isFirst = true
        var guard = 0
        while (isFirst || next != null) {
            if (guard++ > 500) break
            val doc =
                if (isFirst) {
                    isFirst = false
                    httpClient.get(path, configure).body<JsonObject>()
                } else {
                    val u = next ?: break
                    httpClient.get { url(u) }.body<JsonObject>()
                }
            eachPage(doc)
            next = doc["links"]?.jsonObject?.get("next")?.jsonPrimitive?.content
        }
    }

    private fun mergeIncludedStops(doc: JsonObject, stops: MutableMap<String, Stop>) {
        for (el in doc["included"]?.jsonArray.orEmpty()) {
            val o = el.jsonObject
            if (o["type"]?.jsonPrimitive?.content != "stop") continue
            val id = o["id"]?.jsonPrimitive?.content ?: continue
            parseStopFromResource(o)?.let { stops[id] = it }
        }
    }

    private fun findIncluded(doc: JsonObject, type: String, id: String): JsonObject? {
        for (el in doc["included"]?.jsonArray.orEmpty()) {
            val o = el.jsonObject
            if (o["type"]?.jsonPrimitive?.content == type && o["id"]?.jsonPrimitive?.content == id) {
                return o
            }
        }
        return null
    }

    private fun parseRoute(resource: JsonObject): Route? {
        val id = resource["id"]?.jsonPrimitive?.content ?: return null
        val attrs = resource["attributes"]?.jsonObject ?: return null
        val typeInt = attrs["type"]?.jsonPrimitive?.intOrNull ?: return null
        return Route(
            id = id,
            type = RouteType.fromGtfsType(typeInt),
            color = attrs["color"]?.jsonPrimitive?.content ?: "000000",
            directionNames =
                attrs["direction_names"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            directionDestinations =
                attrs["direction_destinations"]?.jsonArray?.map { it.jsonPrimitive.content }
                    ?: emptyList(),
            isListedRoute =
                attrs["listed_route"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
            longName = attrs["long_name"]?.jsonPrimitive?.content ?: "",
            shortName = attrs["short_name"]?.jsonPrimitive?.content ?: "",
            sortOrder = attrs["sort_order"]?.jsonPrimitive?.intOrNull ?: 0,
            textColor = attrs["text_color"]?.jsonPrimitive?.content ?: "FFFFFF",
        )
    }

    private fun parseTrip(resource: JsonObject): Trip? {
        val id = resource["id"]?.jsonPrimitive?.content ?: return null
        val attrs = resource["attributes"]?.jsonObject ?: return null
        val routeId =
            resource["relationships"]
                ?.jsonObject
                ?.get("route")
                ?.jsonObject
                ?.get("data")
                ?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.content
                ?: return null
        return Trip(
            id = id,
            directionId = attrs["direction_id"]?.jsonPrimitive?.intOrNull ?: 0,
            headsign = attrs["headsign"]?.jsonPrimitive?.content ?: "",
            routeId = routeId,
            routePatternId =
                resource["relationships"]
                    ?.jsonObject
                    ?.get("route_pattern")
                    ?.jsonObject
                    ?.get("data")
                    ?.jsonObject
                    ?.get("id")
                    ?.jsonPrimitive
                    ?.content,
        )
    }

    private fun parseStopFromResource(resource: JsonObject): Stop? {
        val id = resource["id"]?.jsonPrimitive?.content ?: return null
        val attrs = resource["attributes"]?.jsonObject ?: return null
        val locTypeInt = attrs["location_type"]?.jsonPrimitive?.intOrNull ?: return null
        val locationType =
            when (locTypeInt) {
                0 -> LocationType.STOP
                1 -> LocationType.STATION
                2 -> LocationType.ENTRANCE_EXIT
                3 -> LocationType.GENERIC_NODE
                4 -> LocationType.BOARDING_AREA
                else -> LocationType.STOP
            }
        val childIds =
            attrs["child_stop_ids"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val parentId =
            resource["relationships"]
                ?.jsonObject
                ?.get("parent_station")
                ?.jsonObject
                ?.get("data")
                ?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.content
        val vehicleTypeInt = attrs["vehicle_type"]?.jsonPrimitive?.intOrNull
        return Stop(
            id = id,
            latitude = attrs["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
            longitude = attrs["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
            name = attrs["name"]?.jsonPrimitive?.content ?: id,
            locationType = locationType,
            platformCode = attrs["platform_code"]?.jsonPrimitive?.content,
            vehicleType = vehicleTypeInt?.let { RouteType.fromGtfsType(it) },
            childStopIds = childIds,
            parentStationId = parentId,
        )
    }
}

private fun kotlinx.serialization.json.JsonArray?.orEmpty(): kotlinx.serialization.json.JsonArray =
    this ?: kotlinx.serialization.json.JsonArray(emptyList())
