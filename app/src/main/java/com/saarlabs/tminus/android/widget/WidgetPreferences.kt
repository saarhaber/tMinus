package com.saarlabs.tminus.android.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import com.saarlabs.tminus.model.WidgetStationBoardConfig
import com.saarlabs.tminus.model.WidgetTripConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "widget_config"
private const val KEY_PENDING_CONFIG_WIDGET_ID = "pending_config_widget_id"
private const val KEY_PENDING_STATION_BOARD_WIDGET_ID = "pending_station_board_widget_id"

internal class WidgetPreferences(private val context: Context) {

    private val prefs
        get() = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun configKey(appWidgetId: Int) = "config_$appWidgetId"

    fun setPendingConfigWidgetId(appWidgetId: Int) {
        prefs.edit().putInt(KEY_PENDING_CONFIG_WIDGET_ID, appWidgetId).commit()
    }

    fun getAndClearPendingConfigWidgetId(): Int {
        val id = prefs.getInt(KEY_PENDING_CONFIG_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        prefs.edit().remove(KEY_PENDING_CONFIG_WIDGET_ID).commit()
        return id
    }

    fun setPendingStationBoardConfigWidgetId(appWidgetId: Int) {
        prefs.edit().putInt(KEY_PENDING_STATION_BOARD_WIDGET_ID, appWidgetId).commit()
    }

    fun getAndClearPendingStationBoardConfigWidgetId(): Int {
        val id = prefs.getInt(KEY_PENDING_STATION_BOARD_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        prefs.edit().remove(KEY_PENDING_STATION_BOARD_WIDGET_ID).commit()
        return id
    }

    private fun getConfigSync(appWidgetId: Int): WidgetTripConfig? {
        val raw = prefs.getString(configKey(appWidgetId), null) ?: return null
        val lines = raw.split("\n")
        if (lines.size < 4) return null
        return WidgetTripConfig(
            fromStopId = lines[0].trim(),
            toStopId = lines[1].trim(),
            fromLabel = lines.getOrNull(2)?.trim() ?: "",
            toLabel = lines.getOrNull(3)?.trim() ?: "",
        )
    }

    suspend fun getConfigOnce(appWidgetId: Int): WidgetTripConfig? =
        withContext(Dispatchers.IO) { getConfigSync(appWidgetId) }

    suspend fun setConfig(appWidgetId: Int, config: WidgetTripConfig) =
        withContext(Dispatchers.IO) {
            val value =
                listOf(config.fromStopId, config.toStopId, config.fromLabel, config.toLabel)
                    .joinToString("\n")
            prefs.edit().putString(configKey(appWidgetId), value).commit()
        }

    suspend fun removeConfig(appWidgetId: Int) =
        withContext(Dispatchers.IO) { prefs.edit().remove(configKey(appWidgetId)).commit() }

    private fun stationBoardConfigKey(appWidgetId: Int) = "station_board_config_$appWidgetId"

    private fun getStationBoardConfigSync(appWidgetId: Int): WidgetStationBoardConfig? {
        val raw = prefs.getString(stationBoardConfigKey(appWidgetId), null) ?: return null
        val lines = raw.split("\n")
        if (lines.isEmpty()) return null
        val stopId = lines[0].trim()
        if (stopId.isEmpty()) return null
        val stopLabel = lines.getOrNull(1)?.trim() ?: ""
        val routeLine = lines.getOrNull(2)?.trim().orEmpty()
        val routeId = routeLine.takeIf { it.isNotEmpty() }
        return WidgetStationBoardConfig(stopId = stopId, stopLabel = stopLabel, routeId = routeId)
    }

    suspend fun getStationBoardConfigOnce(appWidgetId: Int): WidgetStationBoardConfig? =
        withContext(Dispatchers.IO) { getStationBoardConfigSync(appWidgetId) }

    suspend fun setStationBoardConfig(appWidgetId: Int, config: WidgetStationBoardConfig) =
        withContext(Dispatchers.IO) {
            val value =
                listOf(config.stopId, config.stopLabel, config.routeId ?: "").joinToString("\n")
            prefs.edit().putString(stationBoardConfigKey(appWidgetId), value).commit()
        }

    suspend fun removeStationBoardConfig(appWidgetId: Int) =
        withContext(Dispatchers.IO) { prefs.edit().remove(stationBoardConfigKey(appWidgetId)).commit() }
}
