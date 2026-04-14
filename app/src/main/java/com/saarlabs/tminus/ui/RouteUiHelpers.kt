package com.saarlabs.tminus.ui

import com.saarlabs.tminus.model.Route

/**
 * Human-readable label for an MBTA [direction_id] (0 or 1), using API metadata when present.
 */
internal fun directionLabelForRoute(route: Route?, directionId: Int): String {
    val idx = directionId.coerceIn(0, 1)
    val dest = route?.directionDestinations?.getOrNull(idx)?.takeIf { it.isNotBlank() }
    val name = route?.directionNames?.getOrNull(idx)?.takeIf { it.isNotBlank() }
    return when {
        dest != null && name != null -> "$name · $dest"
        dest != null -> dest
        name != null -> name
        else -> "Direction $idx"
    }
}

internal fun routesForDropdown(globalRoutes: Map<String, Route>): List<Route> =
    globalRoutes.values.filter { it.isListedRoute }.sorted()
