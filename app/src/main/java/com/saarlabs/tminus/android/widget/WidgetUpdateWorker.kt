package com.saarlabs.tminus.android.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

/**
 * Updates a single Glance widget instance. Tries [GlanceAppWidgetManager.getGlanceIdBy] first; if Glance
 * has not registered that appWidgetId yet (common right after configuration), falls back to scanning
 * [GlanceAppWidgetManager.getGlanceIds] and matching [GlanceAppWidgetManager.getAppWidgetId].
 */
private suspend fun tryUpdateGlanceWidget(
    context: Context,
    glanceAppWidgetManager: GlanceAppWidgetManager,
    appWidgetId: Int,
    widget: GlanceAppWidget,
): Boolean {
    val appCtx = context.applicationContext
    try {
        val glanceId = glanceAppWidgetManager.getGlanceIdBy(appWidgetId)
        widget.update(appCtx, glanceId)
        return true
    } catch (_: IllegalArgumentException) {
        // Registry not ready — fall through.
    }
    return try {
        val glanceIds = glanceAppWidgetManager.getGlanceIds(widget.javaClass)
        for (glanceId in glanceIds) {
            try {
                if (glanceAppWidgetManager.getAppWidgetId(glanceId) == appWidgetId) {
                    widget.update(appCtx, glanceId)
                    return true
                }
            } catch (_: IllegalArgumentException) {
                continue
            }
        }
        false
    } catch (_: IllegalArgumentException) {
        false
    }
}

/**
 * Forces one trip widget instance to recompose after prefs change (e.g. configuration). Uses the same
 * retry as [WidgetUpdateWorker] for Glance IDs that are not registered yet.
 */
internal suspend fun updateTripWidgetWithRetry(context: Context, appWidgetId: Int) {
    updateGlanceWidgetWithRetry(
        context = context,
        appWidgetId = appWidgetId,
        widget = MBTATripWidget(),
    )
}

/**
 * Same as [updateTripWidgetWithRetry] for the station board widget.
 */
internal suspend fun updateStationBoardWidgetWithRetry(context: Context, appWidgetId: Int) {
    updateGlanceWidgetWithRetry(
        context = context,
        appWidgetId = appWidgetId,
        widget = MBTAStationBoardWidget(),
    )
}

private suspend fun updateGlanceWidgetWithRetry(
    context: Context,
    appWidgetId: Int,
    widget: GlanceAppWidget,
) {
    val glanceAppWidgetManager = GlanceAppWidgetManager(context.applicationContext)
    repeat(WidgetUpdateWorker.MAX_RETRIES) { attempt ->
        if (tryUpdateGlanceWidget(context, glanceAppWidgetManager, appWidgetId, widget)) {
            // #region agent log
            AgentDebugLog.log(
                "WidgetUpdateWorker.kt:updateGlanceWidgetWithRetry",
                "glance update ok",
                "H5",
                mapOf(
                    "appWidgetId" to appWidgetId,
                    "widgetClass" to widget::class.java.simpleName,
                    "attempt" to (attempt + 1),
                ),
            )
            // #endregion
            return
        }
        if (attempt < WidgetUpdateWorker.MAX_RETRIES - 1) delay(WidgetUpdateWorker.RETRY_DELAY_MS)
    }
    // #region agent log
    AgentDebugLog.log(
        "WidgetUpdateWorker.kt:updateGlanceWidgetWithRetry",
        "glance update failed after retries",
        "H5",
        mapOf(
            "appWidgetId" to appWidgetId,
            "widgetClass" to widget::class.java.simpleName,
        ),
    )
    // #endregion
}

public class WidgetUpdateWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val requestedIds = inputData.getIntArray(KEY_APP_WIDGET_IDS)
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)

        val tripComponent = ComponentName(applicationContext, MBTATripWidgetReceiver::class.java)
        val stationComponent = ComponentName(applicationContext, MBTAStationBoardWidgetReceiver::class.java)

        val tripReceiverName = MBTATripWidgetReceiver::class.java.name
        val stationReceiverName = MBTAStationBoardWidgetReceiver::class.java.name

        val tripOnlyIds = mutableListOf<Int>()
        val stationOnlyIds = mutableListOf<Int>()
        val ambiguousIds = mutableListOf<Int>()

        if (requestedIds != null) {
            for (id in requestedIds) {
                when (appWidgetManager.getAppWidgetInfo(id)?.provider?.className) {
                    tripReceiverName -> tripOnlyIds.add(id)
                    stationReceiverName -> stationOnlyIds.add(id)
                    else -> ambiguousIds.add(id)
                }
            }
        } else {
            tripOnlyIds.addAll(appWidgetManager.getAppWidgetIds(tripComponent).toList())
            stationOnlyIds.addAll(appWidgetManager.getAppWidgetIds(stationComponent).toList())
        }

        if (tripOnlyIds.isEmpty() && stationOnlyIds.isEmpty() && ambiguousIds.isEmpty()) {
            return Result.success()
        }

        if (tripOnlyIds.isNotEmpty()) {
            val updateIntent =
                Intent(applicationContext, MBTATripWidgetReceiver::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, tripOnlyIds.toIntArray())
                }
            applicationContext.sendBroadcast(updateIntent)
        }

        if (stationOnlyIds.isNotEmpty()) {
            val updateIntent =
                Intent(applicationContext, MBTAStationBoardWidgetReceiver::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, stationOnlyIds.toIntArray())
                }
            applicationContext.sendBroadcast(updateIntent)
        }

        val glanceAppWidgetManager = GlanceAppWidgetManager(applicationContext)

        for (appWidgetId in tripOnlyIds) {
            updateWithRetry(glanceAppWidgetManager, MBTATripWidget(), appWidgetId)
        }
        for (appWidgetId in stationOnlyIds) {
            updateWithRetry(glanceAppWidgetManager, MBTAStationBoardWidget(), appWidgetId)
        }
        for (appWidgetId in ambiguousIds) {
            updateWithRetry(glanceAppWidgetManager, MBTATripWidget(), appWidgetId)
            updateWithRetry(glanceAppWidgetManager, MBTAStationBoardWidget(), appWidgetId)
        }

        return Result.success()
    }

    private suspend fun updateWithRetry(
        glanceManager: GlanceAppWidgetManager,
        widget: GlanceAppWidget,
        appWidgetId: Int,
    ) {
        repeat(MAX_RETRIES) { attempt ->
            if (tryUpdateGlanceWidget(applicationContext, glanceManager, appWidgetId, widget)) {
                return
            }
            if (attempt < MAX_RETRIES - 1) delay(RETRY_DELAY_MS)
        }
    }

    public companion object {
        public const val WORK_NAME: String = "WidgetUpdate"
        public const val PERIODIC_WORK_NAME: String = "WidgetUpdatePeriodic"
        public const val KEY_APP_WIDGET_IDS: String = "appWidgetIds"
        /** Glance id registry can lag right after configuration — extra attempts reduce stuck placeholders. */
        internal const val MAX_RETRIES = 18
        internal const val RETRY_DELAY_MS = 450L

        /**
         * Refreshes all home screen widgets (trip and station board). Pass specific IDs after
         * configuration, or null to update every placed instance (e.g. when returning to the home screen).
         */
        public fun enqueueRefresh(context: Context, appWidgetIds: IntArray? = null) {
            val builder = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            if (appWidgetIds != null) {
                builder.setInputData(workDataOf(KEY_APP_WIDGET_IDS to appWidgetIds))
            }
            WorkManager.getInstance(context.applicationContext).enqueue(builder.build())
        }

        /**
         * Schedules a periodic refresh of every placed widget so the "min until departure" and
         * displayed times stay fresh without the user having to open the app or tap refresh. 15 min
         * is WorkManager's floor for periodic work.
         */
        public fun ensurePeriodicRefresh(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<WidgetUpdateWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
        }
    }
}
