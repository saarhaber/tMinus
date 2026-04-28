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
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * MBTA V3 JSON:API ([https://www.mbta.com/developers/v3-api](https://www.mbta.com/developers/v3-api)).
 * Optional [apiKey] maps to the V3 portal ([https://api-v3.mbta.com/](https://api-v3.mbta.com/)).
 */
public class MbtaV3Client(private val apiKey: String?) {

    /**
     * With a V3 API key (1000 req/min tier), paginated endpoints fetch several pages concurrently.
     * Without a key, requests stay sequential to stay within the lower anonymous limit.
     */
    private companion object {
        const val KEYED_PARALLEL_PAGES = 8
    }

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

    /** Release the Ktor engine; call before replacing the client (e.g. after API key changes). */
    public fun close() {
        httpClient.close()
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
                        if (o["type"]?.asJsonPrimitiveOrNull()?.content != "stop") continue
                        val id = o["id"]?.asJsonPrimitiveOrNull()?.content ?: continue
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
                        if (o["type"]?.asJsonPrimitiveOrNull()?.content != "route") continue
                        val id = o["id"]?.asJsonPrimitiveOrNull()?.content ?: continue
                        parseRoute(o)?.let { routes[id] = it }
                    }
                }

                ApiResult.Ok(GlobalData(stops = stops, routes = routes))
            } catch (e: Throwable) {
                ApiResult.Error(message = describeMbtaRequestFailure(e))
            }
        }

    /**
     * Routes that serve [stopId] (for pickers and fallbacks).
     */
    public suspend fun fetchRoutesForStop(stopId: String): ApiResult<List<Route>> =
        withContext(Dispatchers.IO) {
            try {
                val out = linkedMapOf<String, Route>()
                paginateJsonApi(
                    path = "routes",
                    pageLimit = 100,
                    configure = {
                        parameter("filter[stop]", stopId)
                    },
                ) { doc ->
                    for (el in doc.dataArrayElements()) {
                        val o = el.asJsonObjectOrNull() ?: continue
                        if (o["type"]?.asJsonPrimitiveOrNull()?.content != "route") continue
                        val id = o["id"]?.asJsonPrimitiveOrNull()?.content ?: continue
                        parseRoute(o)?.let { out[id] = it }
                    }
                }
                ApiResult.Ok(out.values.sorted())
            } catch (e: Throwable) {
                ApiResult.Error(message = describeMbtaRequestFailure(e))
            }
        }

    /**
     * Parent stations on [routeId] (merged into [stops] for any stops missing from cache).
     */
    private suspend fun fetchParentStopsForRoute(
        routeId: String,
        stops: MutableMap<String, Stop>,
    ): List<Stop> {
        val fromResponse = mutableListOf<Stop>()
        paginateJsonApi(
            path = "stops",
            pageLimit = 500,
            configure = {
                parameter("filter[route]", routeId)
            },
        ) { doc ->
            mergeIncludedStops(doc, stops)
            for (el in doc.dataArrayElements()) {
                val o = el.asJsonObjectOrNull() ?: continue
                if (o["type"]?.asJsonPrimitiveOrNull()?.content != "stop") continue
                val id = o["id"]?.asJsonPrimitiveOrNull()?.content ?: continue
                val parsed = parseStopFromResource(o)?.also { stops[id] = it } ?: continue
                if (parsed.parentStationId != null) continue
                if (parsed.locationType != LocationType.STATION && parsed.locationType != LocationType.STOP) {
                    continue
                }
                fromResponse.add(parsed)
            }
        }
        return fromResponse.sortedBy { it.name }
    }

    /**
     * When route-pattern reachability is empty (e.g. some commuter-rail patterns), fall back to
     * “other stations on the same route(s)” so pairs like Natick Center → Back Bay still work.
     */
    private suspend fun fetchReachableDestinationsViaSharedRoutes(
        fromStopId: String,
        allStops: Map<String, Stop>,
    ): List<Stop> {
        val routesResult = fetchRoutesForStop(fromStopId)
        val routeIds =
            when (routesResult) {
                is ApiResult.Ok -> routesResult.data.map { it.id }
                is ApiResult.Error -> return emptyList()
            }
        if (routeIds.isEmpty()) return emptyList()

        val mutableStops = allStops.toMutableMap()
        val parents = mutableSetOf<String>()
        val fromParent = allStops[fromStopId]?.resolveParent(allStops)?.id ?: fromStopId

        for (rid in routeIds) {
            for (s in fetchParentStopsForRoute(rid, mutableStops)) {
                val p = s.resolveParent(mutableStops).id
                if (p != fromParent) parents.add(p)
            }
        }

        return parents
            .mapNotNull { mutableStops[it] ?: allStops[it] }
            .map { it.resolveParent(mutableStops) }
            .distinctBy { it.id }
            .sortedBy { it.name }
    }

    /**
     * Parent stations on the same route-pattern line as [fromStopId] without transfers, using
     * sample trips from route patterns through that stop. Stops both *after* and *before* the
     * origin on the pattern are included so bidirectional commuter-rail pairs (e.g. Natick Center
     * and Back Bay on Worcester Line patterns ordered toward Worcester) still appear as
     * destinations. If that yields no destinations, falls back to stations on the same route(s).
     */
    public suspend fun fetchReachableDestinationStops(
        fromStopId: String,
        allStops: Map<String, Stop>,
    ): ApiResult<List<Stop>> =
        withContext(Dispatchers.IO) {
            val primaryResult =
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
                            val patternId = patternObj["id"]?.asJsonPrimitiveOrNull()?.content ?: continue
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
                                            ?.asJsonPrimitiveOrNull()
                                            ?.intOrNull ?: 0
                                    }
                                    .mapNotNull { sObj ->
                                        sObj.jsonApiRelationshipDataId("stop")
                                    }

                            val fromIdx =
                                orderedStopIds.indexOfFirst { sid ->
                                    Stop.equalOrFamily(sid, fromStopId, allStops)
                                }
                            if (fromIdx < 0) continue
                            for (i in orderedStopIds.indices) {
                                if (i == fromIdx) continue
                                val sid = orderedStopIds[i]
                                if (Stop.equalOrFamily(sid, fromStopId, allStops)) continue
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

            val primaryList =
                when (primaryResult) {
                    is ApiResult.Ok -> primaryResult.data
                    is ApiResult.Error -> emptyList()
                }
            if (primaryList.isNotEmpty()) return@withContext primaryResult

            val fallback = fetchReachableDestinationsViaSharedRoutes(fromStopId, allStops)
            if (fallback.isNotEmpty()) {
                return@withContext ApiResult.Ok(fallback)
            }
            when (primaryResult) {
                is ApiResult.Error -> primaryResult
                is ApiResult.Ok -> ApiResult.Ok(emptyList())
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
            ?.asJsonPrimitiveOrNull()
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
     * When [serviceDate] is null, the API defaults to the current schedule day (typically today).
     * Pass an explicit [serviceDate] to load that calendar day’s trips in the window (needed when the
     * window already passed earlier the same day).
     * Paginates until all rows are loaded — a single page (500) is often incomplete for commuter rail.
     */
    public suspend fun fetchScheduleForStopsInWindow(
        stopIds: List<String>,
        minTime: String,
        maxTime: String,
        serviceDate: LocalDate? = null,
    ): ApiResult<ScheduleResponse> =
        withContext(Dispatchers.IO) {
            try {
                val allSchedules = mutableListOf<com.saarlabs.tminus.model.Schedule>()
                val allTrips = mutableMapOf<String, Trip>()
                var offset = 0
                var guard = 0
                val pageLimit = 500
                while (guard++ < 500) {
                    val doc =
                        httpClient
                            .get("schedules") {
                                parameter("filter[stop]", stopIds.joinToString(","))
                                parameter("filter[min_time]", minTime)
                                parameter("filter[max_time]", maxTime)
                                if (serviceDate != null) {
                                    parameter("filter[date]", serviceDate.toString())
                                }
                                parameter("include", "trip,route")
                                parameter("page[limit]", pageLimit.toString())
                                parameter("page[offset]", offset.toString())
                            }
                            .body<JsonObject>()
                    val page = parseSchedulePage(doc)
                    allSchedules.addAll(page.schedules)
                    page.trips.forEach { (id, trip) -> allTrips.putIfAbsent(id, trip) }
                    val count = doc.dataArrayElements().size
                    if (count < pageLimit) break
                    offset += pageLimit
                }
                ApiResult.Ok(ScheduleResponse(schedules = allSchedules, trips = allTrips))
            } catch (e: Throwable) {
                ApiResult.Error(message = describeMbtaRequestFailure(e))
            }
        }

    private fun parseSchedulePage(doc: JsonObject): ScheduleResponse {
        val schedules = mutableListOf<com.saarlabs.tminus.model.Schedule>()
        val trips = mutableMapOf<String, Trip>()

        for (el in doc.dataArrayElements()) {
            val sObj = el.asJsonObjectOrNull() ?: continue
            val id = sObj["id"]?.asJsonPrimitiveOrNull()?.content ?: continue
            val attrs = sObj["attributes"]?.asJsonObjectOrNull() ?: continue
            val tripId = sObj.jsonApiRelationshipDataId("trip") ?: continue

            val depStr =
                attrs["departure_time"]?.asJsonPrimitiveOrNull()?.content
                    ?: attrs["arrival_time"]?.asJsonPrimitiveOrNull()?.content
            val arrStr =
                attrs["arrival_time"]?.asJsonPrimitiveOrNull()?.content
                    ?: attrs["departure_time"]?.asJsonPrimitiveOrNull()?.content
            val dep = parseMbtaScheduleInstant(depStr)
            val arr = parseMbtaScheduleInstant(arrStr)
            if (dep == null && arr == null) continue
            val stopId = sObj.jsonApiRelationshipDataId("stop") ?: continue
            val seq = attrs["stop_sequence"]?.asJsonPrimitiveOrNull()?.intOrNull ?: continue
            val headsign = attrs["stop_headsign"]?.asJsonPrimitiveOrNull()?.content

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

        return ScheduleResponse(schedules = schedules, trips = trips)
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
                        if (o["type"]?.asJsonPrimitiveOrNull()?.content != "alert") continue
                        val id = o["id"]?.asJsonPrimitiveOrNull()?.content ?: continue
                        val attrs = o["attributes"]?.asJsonObjectOrNull() ?: continue
                        val header = attrs["header"]?.asJsonPrimitiveOrNull()?.content ?: continue
                        val effect = attrs["effect"]?.asJsonPrimitiveOrNull()?.content
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
                        attrs["departure_time"]?.asJsonPrimitiveOrNull()?.content
                            ?: attrs["arrival_time"]?.asJsonPrimitiveOrNull()?.content
                            ?: continue
                    val et = parseMbtaScheduleInstant(depStr) ?: continue
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
                        attrs["departure_time"]?.asJsonPrimitiveOrNull()?.content
                            ?: attrs["arrival_time"]?.asJsonPrimitiveOrNull()?.content
                            ?: continue
                    val et = parseMbtaScheduleInstant(depStr) ?: continue
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
     *
     * When [apiKey] is set, loads the first page alone then fetches up to [KEYED_PARALLEL_PAGES]
     * pages per wave in parallel so large catalog pulls finish faster within the higher rate tier.
     */
    private suspend fun paginateJsonApi(
        path: String,
        pageLimit: Int,
        configure: HttpRequestBuilder.() -> Unit = {},
        eachPage: suspend (JsonObject) -> Unit,
    ) {
        if (apiKey.isNullOrBlank()) {
            paginateJsonApiSequential(path, pageLimit, configure, eachPage)
        } else {
            paginateJsonApiParallel(path, pageLimit, configure, eachPage)
        }
    }

    private suspend fun fetchJsonApiPage(
        path: String,
        pageLimit: Int,
        offset: Int,
        configure: HttpRequestBuilder.() -> Unit,
    ): JsonObject =
        httpClient.get(path) {
            configure()
            parameter("page[limit]", pageLimit.toString())
            parameter("page[offset]", offset.toString())
        }.body<JsonObject>()

    private suspend fun paginateJsonApiSequential(
        path: String,
        pageLimit: Int,
        configure: HttpRequestBuilder.() -> Unit,
        eachPage: suspend (JsonObject) -> Unit,
    ) {
        var offset = 0
        var guard = 0
        while (guard++ < 500) {
            val doc = fetchJsonApiPage(path, pageLimit, offset, configure)
            eachPage(doc)
            val count = doc.get("data")?.asJsonArrayOrNull()?.size ?: 0
            if (count < pageLimit) break
            offset += pageLimit
        }
    }

    private suspend fun paginateJsonApiParallel(
        path: String,
        pageLimit: Int,
        configure: HttpRequestBuilder.() -> Unit,
        eachPage: suspend (JsonObject) -> Unit,
    ) {
        val first = fetchJsonApiPage(path, pageLimit, 0, configure)
        eachPage(first)
        val firstCount = first.get("data")?.asJsonArrayOrNull()?.size ?: 0
        if (firstCount < pageLimit) return

        var offset = pageLimit
        var guard = 0
        while (guard++ < 500) {
            val offsets = (0 until KEYED_PARALLEL_PAGES).map { offset + it * pageLimit }
            val docs =
                coroutineScope {
                    offsets
                        .map { off ->
                            async(Dispatchers.IO) {
                                off to fetchJsonApiPage(path, pageLimit, off, configure)
                            }
                        }.awaitAll()
                }.sortedBy { it.first }

            var sawPartial = false
            for ((_, doc) in docs) {
                eachPage(doc)
                val count = doc.get("data")?.asJsonArrayOrNull()?.size ?: 0
                if (count < pageLimit) {
                    sawPartial = true
                    break
                }
            }
            if (sawPartial) break
            offset += KEYED_PARALLEL_PAGES * pageLimit
        }
    }

    private fun mergeIncludedStops(doc: JsonObject, stops: MutableMap<String, Stop>) {
        for (el in doc.includedArrayElements()) {
            val o = el.asJsonObjectOrNull() ?: continue
            if (o["type"]?.asJsonPrimitiveOrNull()?.content != "stop") continue
            val id = o["id"]?.asJsonPrimitiveOrNull()?.content ?: continue
            parseStopFromResource(o)?.let { stops[id] = it }
        }
    }

    private fun findIncluded(doc: JsonObject, type: String, id: String): JsonObject? {
        for (el in doc.includedArrayElements()) {
            val o = el.asJsonObjectOrNull() ?: continue
            if (o["type"]?.asJsonPrimitiveOrNull()?.content == type && o["id"]?.asJsonPrimitiveOrNull()?.content == id) {
                return o
            }
        }
        return null
    }

    private fun parseRoute(resource: JsonObject): Route? {
        val id = resource["id"]?.asJsonPrimitiveOrNull()?.content ?: return null
        val attrs = resource["attributes"]?.asJsonObjectOrNull() ?: return null
        val typeInt = attrs["type"]?.asJsonPrimitiveOrNull()?.intOrNull ?: return null
        return Route(
            id = id,
            type = RouteType.fromGtfsType(typeInt),
            color = attrs["color"]?.asJsonPrimitiveOrNull()?.content ?: "000000",
            directionNames =
                attrs["direction_names"]?.asJsonArrayOrNull()?.map { it.jsonPrimitive.content }
                    ?: emptyList(),
            directionDestinations =
                attrs["direction_destinations"]?.asJsonArrayOrNull()?.map { it.jsonPrimitive.content }
                    ?: emptyList(),
            isListedRoute =
                attrs["listed_route"]?.asJsonPrimitiveOrNull()?.content?.toBooleanStrictOrNull() ?: true,
            longName = attrs["long_name"]?.asJsonPrimitiveOrNull()?.content ?: "",
            shortName = attrs["short_name"]?.asJsonPrimitiveOrNull()?.content ?: "",
            sortOrder = attrs["sort_order"]?.asJsonPrimitiveOrNull()?.intOrNull ?: 0,
            textColor = attrs["text_color"]?.asJsonPrimitiveOrNull()?.content ?: "FFFFFF",
        )
    }

    private fun parseTrip(resource: JsonObject): Trip? {
        val id = resource["id"]?.asJsonPrimitiveOrNull()?.content ?: return null
        val attrs = resource["attributes"]?.asJsonObjectOrNull() ?: return null
        val routeId = resource.jsonApiRelationshipDataId("route") ?: return null
        return Trip(
            id = id,
            directionId = attrs["direction_id"]?.asJsonPrimitiveOrNull()?.intOrNull ?: 0,
            headsign = attrs["headsign"]?.asJsonPrimitiveOrNull()?.content ?: "",
            routeId = routeId,
            routePatternId = resource.jsonApiRelationshipDataId("route_pattern"),
        )
    }

    private fun parseStopFromResource(resource: JsonObject): Stop? {
        val id = resource["id"]?.asJsonPrimitiveOrNull()?.content ?: return null
        val attrs = resource["attributes"]?.asJsonObjectOrNull() ?: return null
        val locTypeInt = attrs["location_type"]?.asJsonPrimitiveOrNull()?.intOrNull ?: return null
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
        val vehicleTypeInt = attrs["vehicle_type"]?.asJsonPrimitiveOrNull()?.intOrNull
        return Stop(
            id = id,
            latitude = attrs["latitude"]?.asJsonPrimitiveOrNull()?.content?.toDoubleOrNull() ?: 0.0,
            longitude = attrs["longitude"]?.asJsonPrimitiveOrNull()?.content?.toDoubleOrNull() ?: 0.0,
            name = attrs["name"]?.asJsonPrimitiveOrNull()?.content ?: id,
            locationType = locationType,
            platformCode = attrs["platform_code"]?.asJsonPrimitiveOrNull()?.content,
            vehicleType = vehicleTypeInt?.let { RouteType.fromGtfsType(it) },
            childStopIds = childIds,
            parentStationId = parentId,
        )
    }

    /**
     * MBTA schedule attributes are sometimes the literal string `"null"` or otherwise unparsable;
     * [Instant.parse] then throws ("Failed to parse an instant from 'null'").
     */
    private fun parseMbtaScheduleInstant(raw: String?): EasternTimeInstant? {
        val s = raw?.trim() ?: return null
        if (s.isEmpty() || s.equals("null", ignoreCase = true)) return null
        return try {
            EasternTimeInstant(Instant.parse(s))
        } catch (_: Throwable) {
            null
        }
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
