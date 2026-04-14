package com.saarlabs.tminus.network

import com.saarlabs.tminus.model.LocationType
import com.saarlabs.tminus.model.Route
import com.saarlabs.tminus.model.RouteType
import com.saarlabs.tminus.model.Stop
import com.saarlabs.tminus.model.Trip
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.model.response.DepartureLookupResult
import com.saarlabs.tminus.model.response.GlobalData
import com.saarlabs.tminus.model.response.ScheduleResponse
import com.saarlabs.tminus.util.EasternTimeInstant
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
            expectSuccess = true
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
                    pageLimit = 500,
                    configure = {
                        parameter("filter[location_type]", "0,1")
                    },
                ) { doc ->
                    mergeIncludedStops(doc, stops)
                    for (el in doc.dataArrayElements()) {
                        val o = el.asJsonObjectOrNull() ?: continue
                        if (o["type"]?.jsonPrimitive?.content != "stop") continue
                        val id = o["id"]?.jsonPrimitive?.content ?: continue
                        parseStopFromResource(o)?.let { stops[id] = it }
                    }
                }

                val routes = linkedMapOf<String, Route>()
                paginateJsonApi(
                    path = "routes",
                    pageLimit = 100,
                    configure = {},
                ) { doc ->
                    for (el in doc.dataArrayElements()) {
                        val o = el.asJsonObjectOrNull() ?: continue
                        if (o["type"]?.jsonPrimitive?.content != "route") continue
                        val id = o["id"]?.jsonPrimitive?.content ?: continue
                        parseRoute(o)?.let { routes[id] = it }
                    }
                }

                ApiResult.Ok(GlobalData(stops = stops, routes = routes))
            } catch (e: Throwable) {
                ApiResult.Error(message = describeMbtaRequestFailure(e))
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
                    pageLimit = 50,
                    configure = {
                        parameter("filter[stop]", fromStopId)
                    },
                ) { doc ->
                    for (el in doc.dataArrayElements()) {
                        val patternObj = el.asJsonObjectOrNull() ?: continue
                        val patternId = patternObj["id"]?.jsonPrimitive?.content ?: continue
                        val repTripId =
                            patternObj.jsonApiRelationshipDataId("representative_trip")
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

                        val schedData = schedDoc.get("data")?.asJsonArrayOrNull() ?: continue
                        val orderedStopIds =
                            schedData
                                .mapNotNull { it.asJsonObjectOrNull() }
                                .sortedBy {
                                    it["attributes"]
                                        ?.asJsonObjectOrNull()
                                        ?.get("stop_sequence")
                                        ?.jsonPrimitive
                                        ?.intOrNull ?: 0
                                }
                                .mapNotNull { sObj ->
                                    sObj.jsonApiRelationshipDataId("stop")
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
                ApiResult.Error(message = describeMbtaRequestFailure(e))
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
        return tripsDoc.get("data")
            ?.asJsonArrayOrNull()
            ?.firstOrNull()
            ?.asJsonObjectOrNull()
            ?.get("id")
            ?.jsonPrimitive
            ?.content
            ?.takeIf { !it.startsWith("canonical-") }
    }

    public suspend fun fetchScheduleForStops(
        stopIds: List<String>,
        now: EasternTimeInstant,
    ): ApiResult<ScheduleResponse> {
        val minTime =
            "${now.local.hour.toString().padStart(2, '0')}:${now.local.minute.toString().padStart(2, '0')}"
        return fetchScheduleForStopsInWindow(stopIds, minTime, "23:59")
    }

    /**
     * Schedules at [fromStopIds] with departure times between [minTime] and [maxTime] (HH:mm, Eastern).
     */
    public suspend fun fetchScheduleForStopsInWindow(
        stopIds: List<String>,
        minTime: String,
        maxTime: String,
    ): ApiResult<ScheduleResponse> =
        withContext(Dispatchers.IO) {
            try {
                val doc =
                    httpClient
                        .get("schedules") {
                            parameter("filter[stop]", stopIds.joinToString(","))
                            parameter("filter[min_time]", minTime)
                            parameter("filter[max_time]", maxTime)
                            parameter("include", "trip,route")
                            parameter("page[limit]", "500")
                        }
                        .body<JsonObject>()
                parseScheduleDocument(doc)
            } catch (e: Throwable) {
                ApiResult.Error(message = describeMbtaRequestFailure(e))
            }
        }

    private fun parseScheduleDocument(doc: JsonObject): ApiResult<ScheduleResponse> {
        val schedules = mutableListOf<com.saarlabs.tminus.model.Schedule>()
        val trips = mutableMapOf<String, Trip>()

        for (el in doc.dataArrayElements()) {
            val sObj = el.asJsonObjectOrNull() ?: continue
            val id = sObj["id"]?.jsonPrimitive?.content ?: continue
            val attrs = sObj["attributes"]?.asJsonObjectOrNull() ?: continue
            val tripId = sObj.jsonApiRelationshipDataId("trip") ?: continue

            val depStr =
                attrs["departure_time"]?.jsonPrimitive?.content
                    ?: attrs["arrival_time"]?.jsonPrimitive?.content
            val arrStr =
                attrs["arrival_time"]?.jsonPrimitive?.content
                    ?: attrs["departure_time"]?.jsonPrimitive?.content
            val dep = depStr?.let { EasternTimeInstant(Instant.parse(it)) }
            val arr = arrStr?.let { EasternTimeInstant(Instant.parse(it)) }
            val stopId = sObj.jsonApiRelationshipDataId("stop") ?: continue
            val seq = attrs["stop_sequence"]?.jsonPrimitive?.intOrNull ?: continue
            val headsign = attrs["stop_headsign"]?.jsonPrimitive?.content

            schedules.add(
                com.saarlabs.tminus.model.Schedule(
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

        return ApiResult.Ok(ScheduleResponse(schedules = schedules, trips = trips))
    }

    /**
     * Active alerts for a route (paginated). Used for elevator / accessibility watches.
     */
    public suspend fun fetchAlertsForRoute(routeId: String): ApiResult<List<com.saarlabs.tminus.model.response.MbtaAlertSummary>> =
        withContext(Dispatchers.IO) {
            try {
                val out = mutableListOf<com.saarlabs.tminus.model.response.MbtaAlertSummary>()
                paginateJsonApi(
                    path = "alerts",
                    pageLimit = 50,
                    configure = {
                        parameter("filter[route]", routeId)
                    },
                ) { doc ->
                    for (el in doc.dataArrayElements()) {
                        val o = el.asJsonObjectOrNull() ?: continue
                        if (o["type"]?.jsonPrimitive?.content != "alert") continue
                        val id = o["id"]?.jsonPrimitive?.content ?: continue
                        val attrs = o["attributes"]?.asJsonObjectOrNull() ?: continue
                        val header = attrs["header"]?.jsonPrimitive?.content ?: continue
                        val effect = attrs["effect"]?.jsonPrimitive?.content
                        out.add(
                            com.saarlabs.tminus.model.response.MbtaAlertSummary(
                                id = id,
                                header = header,
                                effect = effect,
                            ),
                        )
                    }
                }
                ApiResult.Ok(out)
            } catch (e: Throwable) {
                ApiResult.Error(message = describeMbtaRequestFailure(e))
            }
        }

    /**
     * Latest scheduled departure at [stopId] for [routeId] in [directionId] within [minTime]–[maxTime] (HH:mm ET).
     */
    public suspend fun fetchLastDepartureInWindow(
        routeId: String,
        directionId: Int,
        stopId: String,
        minTime: String,
        maxTime: String,
    ): ApiResult<DepartureLookupResult> =
        withContext(Dispatchers.IO) {
            try {
                val doc =
                    httpClient
                        .get("schedules") {
                            parameter("filter[route]", routeId)
                            parameter("filter[direction_id]", directionId.toString())
                            parameter("filter[stop]", stopId)
                            parameter("filter[min_time]", minTime)
                            parameter("filter[max_time]", maxTime)
                            parameter("page[limit]", "500")
                        }
                        .body<JsonObject>()
                var latest: EasternTimeInstant? = null
                for (el in doc.dataArrayElements()) {
                    val attrs =
                        el.asJsonObjectOrNull()?.get("attributes")?.asJsonObjectOrNull() ?: continue
                    val depStr =
                        attrs["departure_time"]?.jsonPrimitive?.content
                            ?: attrs["arrival_time"]?.jsonPrimitive?.content
                            ?: continue
                    val et = EasternTimeInstant(Instant.parse(depStr))
                    if (latest == null || et > latest) latest = et
                }
                ApiResult.Ok(DepartureLookupResult(latest))
            } catch (e: Throwable) {
                ApiResult.Error(message = describeMbtaRequestFailure(e))
            }
        }

    /**
     * Earliest scheduled departure in window (for first-train style alerts).
     */
    public suspend fun fetchFirstDepartureInWindow(
        routeId: String,
        directionId: Int,
        stopId: String,
        minTime: String,
        maxTime: String,
    ): ApiResult<DepartureLookupResult> =
        withContext(Dispatchers.IO) {
            try {
                val doc =
                    httpClient
                        .get("schedules") {
                            parameter("filter[route]", routeId)
                            parameter("filter[direction_id]", directionId.toString())
                            parameter("filter[stop]", stopId)
                            parameter("filter[min_time]", minTime)
                            parameter("filter[max_time]", maxTime)
                            parameter("page[limit]", "500")
                        }
                        .body<JsonObject>()
                var earliest: EasternTimeInstant? = null
                for (el in doc.dataArrayElements()) {
                    val attrs =
                        el.asJsonObjectOrNull()?.get("attributes")?.asJsonObjectOrNull() ?: continue
                    val depStr =
                        attrs["departure_time"]?.jsonPrimitive?.content
                            ?: attrs["arrival_time"]?.jsonPrimitive?.content
                            ?: continue
                    val et = EasternTimeInstant(Instant.parse(depStr))
                    if (earliest == null || et < earliest) earliest = et
                }
                ApiResult.Ok(DepartureLookupResult(earliest))
            } catch (e: Throwable) {
                ApiResult.Error(message = describeMbtaRequestFailure(e))
            }
        }

    /**
     * Paginates with `page[offset]` and `page[limit]` on each request. We do not follow `links.next`
     * because those URLs contain unescaped `[` / `]` in query names, which breaks URL parsing on
     * Android/Ktor and caused incomplete stop/route loads (empty search after a failed fetch).
     */
    private suspend inline fun paginateJsonApi(
        path: String,
        pageLimit: Int,
        crossinline configure: HttpRequestBuilder.() -> Unit = {},
        crossinline eachPage: suspend (JsonObject) -> Unit,
    ) {
        var offset = 0
        var guard = 0
        while (guard++ < 500) {
            val doc =
                httpClient.get(path) {
                    configure()
                    parameter("page[limit]", pageLimit.toString())
                    parameter("page[offset]", offset.toString())
                }.body<JsonObject>()
            eachPage(doc)
            val count = doc.get("data")?.asJsonArrayOrNull()?.size ?: 0
            if (count < pageLimit) break
            offset += pageLimit
        }
    }

    private fun mergeIncludedStops(doc: JsonObject, stops: MutableMap<String, Stop>) {
        for (el in doc.includedArrayElements()) {
            val o = el.asJsonObjectOrNull() ?: continue
            if (o["type"]?.jsonPrimitive?.content != "stop") continue
            val id = o["id"]?.jsonPrimitive?.content ?: continue
            parseStopFromResource(o)?.let { stops[id] = it }
        }
    }

    private fun findIncluded(doc: JsonObject, type: String, id: String): JsonObject? {
        for (el in doc.includedArrayElements()) {
            val o = el.asJsonObjectOrNull() ?: continue
            if (o["type"]?.jsonPrimitive?.content == type && o["id"]?.jsonPrimitive?.content == id) {
                return o
            }
        }
        return null
    }

    private fun parseRoute(resource: JsonObject): Route? {
        val id = resource["id"]?.jsonPrimitive?.content ?: return null
        val attrs = resource["attributes"]?.asJsonObjectOrNull() ?: return null
        val typeInt = attrs["type"]?.jsonPrimitive?.intOrNull ?: return null
        return Route(
            id = id,
            type = RouteType.fromGtfsType(typeInt),
            color = attrs["color"]?.jsonPrimitive?.content ?: "000000",
            directionNames =
                attrs["direction_names"]?.asJsonArrayOrNull()?.map { it.jsonPrimitive.content }
                    ?: emptyList(),
            directionDestinations =
                attrs["direction_destinations"]?.asJsonArrayOrNull()?.map { it.jsonPrimitive.content }
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
        val attrs = resource["attributes"]?.asJsonObjectOrNull() ?: return null
        val routeId = resource.jsonApiRelationshipDataId("route") ?: return null
        return Trip(
            id = id,
            directionId = attrs["direction_id"]?.jsonPrimitive?.intOrNull ?: 0,
            headsign = attrs["headsign"]?.jsonPrimitive?.content ?: "",
            routeId = routeId,
            routePatternId = resource.jsonApiRelationshipDataId("route_pattern"),
        )
    }

    private fun parseStopFromResource(resource: JsonObject): Stop? {
        val id = resource["id"]?.jsonPrimitive?.content ?: return null
        val attrs = resource["attributes"]?.asJsonObjectOrNull() ?: return null
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
            attrs["child_stop_ids"]?.asJsonArrayOrNull()?.map { it.jsonPrimitive.content }
                ?: emptyList()
        val parentId = resource.jsonApiRelationshipDataId("parent_station")
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

    private fun describeMbtaRequestFailure(e: Throwable): String {
        when (e) {
            is ClientRequestException -> {
                val status = e.response.status
                return when (status) {
                    HttpStatusCode.Forbidden ->
                        "MBTA API rejected the key (403). Clear the key in Settings or paste the full key from api-v3.mbta.com."
                    HttpStatusCode.Unauthorized ->
                        "MBTA API unauthorized (401). Check your V3 API key in Settings."
                    HttpStatusCode.TooManyRequests ->
                        "MBTA API rate limit (429). Try again in a few minutes."
                    else ->
                        "MBTA API error ${status.value}${e.message?.let { ": $it" } ?: ""}"
                }
            }
            is ResponseException ->
                return "MBTA API error ${e.response.status.value}${e.message?.let { ": $it" } ?: ""}"
            else -> return e.message ?: e.toString()
        }
    }
}
