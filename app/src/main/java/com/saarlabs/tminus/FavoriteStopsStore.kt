package com.saarlabs.tminus

import android.content.Context
import com.saarlabs.tminus.model.Stop

/** Persists favorite MBTA stations (parent stop ids) for ordering and quick access in pickers. */
public class FavoriteStopsStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)

    public fun getIds(): Set<String> =
        prefs.getStringSet(SettingsKeys.KEY_FAVORITE_STOP_IDS, emptySet())?.toSet() ?: emptySet()

    /** @return true if the stop is now a favorite after the toggle. */
    public fun toggle(parentStopId: String): Boolean {
        val next = getIds().toMutableSet()
        val nowFavorite =
            if (parentStopId in next) {
                next.remove(parentStopId)
                false
            } else {
                next.add(parentStopId)
                true
            }
        prefs.edit().putStringSet(SettingsKeys.KEY_FAVORITE_STOP_IDS, next).apply()
        return nowFavorite
    }
}

/** Favorites first (by name), then the rest (by name), using resolved parent ids for matching. */
public fun sortStopsWithFavoritesFirst(
    stops: List<Stop>,
    favoriteParentIds: Set<String>,
    allStops: Map<String, Stop>,
): List<Stop> {
    return stops.sortedWith(
        compareBy<Stop> { stop ->
            val parentId = stop.resolveParent(allStops).id
            if (parentId in favoriteParentIds) 0 else 1
        }.thenBy { it.resolveParent(allStops).name.lowercase() },
    )
}
