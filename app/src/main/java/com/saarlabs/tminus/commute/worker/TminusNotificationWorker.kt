package com.saarlabs.tminus.commute.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.util.EasternTimeInstant
import com.saarlabs.tminus.GlobalDataStore
import com.saarlabs.tminus.MainActivity
import com.saarlabs.tminus.R
import com.saarlabs.tminus.commute.CommuteRepository
import com.saarlabs.tminus.commute.CommuteTripPlanner
import com.saarlabs.tminus.features.AccessibilityRepository
import com.saarlabs.tminus.features.AccessibilityWatch
import com.saarlabs.tminus.features.LastTrainMode
import com.saarlabs.tminus.features.LastTrainProfile
import com.saarlabs.tminus.features.LastTrainRepository
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Single periodic worker: commutes, last/first train, accessibility route alerts.
 */
public class TminusNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            // Process start can run WorkManager before [TminusApplication.refreshNetworking] assigns [GlobalDataStore.client].
            GlobalDataStore.awaitClientReady(timeoutMs = 15_000L)
            if (!GlobalDataStore.isClientReady()) {
                // Retry so we evaluate alerts after the app finishes initializing (not a permanent skip).
                return@withContext Result.retry()
            }
            val prefs = applicationContext.getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE)

            runCommuteNotifications(prefs)
            runLastTrainNotifications(prefs)
            runAccessibilityNotifications(prefs)

            Result.success()
        }

    private suspend fun runCommuteNotifications(prefs: android.content.SharedPreferences) {
        val repo = CommuteRepository(applicationContext)
        val profiles = repo.loadProfiles().filter { it.enabled }
        if (profiles.isEmpty()) return

        val globalResult = GlobalDataStore.getOrLoad()
        val global =
            when (globalResult) {
                is ApiResult.Ok -> globalResult.data
                is ApiResult.Error -> return
            }

        val tz = EasternTimeInstant.timeZone
        val nowEt = EasternTimeInstant.now()
        val today = nowEt.local.date
        val todayDow = isoDayOfWeek(today)

        for (profile in profiles) {
            if (profile.daysOfWeek.isEmpty()) continue
            if (!profile.daysOfWeek.contains(todayDow)) continue

            val fromStop = global.getStop(profile.fromStopId) ?: continue
            val toStop = global.getStop(profile.toStopId) ?: continue
            val stopIds =
                (global.stopIdsForScheduleFilter(fromStop) + global.stopIdsForScheduleFilter(toStop))
                    .distinct()

            val targetMinutes = profile.targetMinutesFromMidnight
            val windowStart = max(0, targetMinutes - profile.windowMinutesBefore)
            val windowEnd = min(24 * 60 - 1, targetMinutes + profile.windowMinutesAfter)

            val minTime = minutesToHHmm(windowStart)
            val maxTime = minutesToHHmm(windowEnd)

            val schedResult = GlobalDataStore.client.fetchScheduleForStopsInWindow(stopIds, minTime, maxTime)
            val schedule =
                when (schedResult) {
                    is ApiResult.Ok -> schedResult.data
                    is ApiResult.Error -> continue
                }

            val windowStartEt = atMinutesOnDate(today, windowStart, tz)
            val windowEndEt = atMinutesOnDate(today, windowEnd, tz)

            val trip =
                CommuteTripPlanner.findNextTripInWindow(
                    response = schedule,
                    globalData = global,
                    fromStopId = profile.fromStopId,
                    toStopId = profile.toStopId,
                    now = nowEt,
                    windowStart = windowStartEt,
                    windowEnd = windowEndEt,
                )
                    ?: continue

            val fromName = profile.fromLabel.ifBlank { trip.fromStop.name }
            val leaveKey = "leave_${profile.id}_${trip.tripId}_${trip.departureTime.toEpochMilliseconds()}"
            val arrivalKey = "arr_${profile.id}_${trip.tripId}_${trip.arrivalTime.toEpochMilliseconds()}"

            val leadMs = profile.notifyLeadMinutes * 60_000L
            val leaveAtMs = trip.departureTime.toEpochMilliseconds() - leadMs
            val nowMs = nowEt.toEpochMilliseconds()

            if (leaveAtMs <= nowMs && nowMs < leaveAtMs + WINDOW_MS) {
                if (!prefs.getBoolean(leaveKey, false)) {
                    prefs.edit().putBoolean(leaveKey, true).apply()
                    notify(
                        id = leaveKey.hashCode(),
                        title = applicationContext.getString(R.string.notif_leave_title),
                        text =
                            applicationContext.getString(
                                R.string.notif_leave_body,
                                profile.name,
                                trip.route.label,
                                fromName,
                                trip.minutesUntil,
                            ),
                        backgroundArgb = argbFromHexOrNull(trip.route.color),
                        contentArgb = argbFromHexOrNull(trip.route.textColor),
                    )
                }
            }

            if (profile.notifyOnArrival) {
                val arrMs = trip.arrivalTime.toEpochMilliseconds()
                if (arrMs <= nowMs && nowMs < arrMs + ARRIVAL_WINDOW_MS) {
                    if (!prefs.getBoolean(arrivalKey, false)) {
                        prefs.edit().putBoolean(arrivalKey, true).apply()
                        val toName = profile.toLabel.ifBlank { trip.toStop.name }
                        notify(
                            id = arrivalKey.hashCode(),
                            title = applicationContext.getString(R.string.notif_arrival_title),
                            text =
                                applicationContext.getString(
                                    R.string.notif_arrival_body,
                                    profile.name,
                                    toName,
                                ),
                            backgroundArgb = argbFromHexOrNull(trip.route.color),
                            contentArgb = argbFromHexOrNull(trip.route.textColor),
                        )
                    }
                }
            }
        }
    }

    private suspend fun runLastTrainNotifications(prefs: android.content.SharedPreferences) {
        val repo = LastTrainRepository(applicationContext)
        val profiles = repo.load().filter { it.enabled }
        if (profiles.isEmpty()) return

        val globalResult = GlobalDataStore.getOrLoad()
        val global =
            when (globalResult) {
                is ApiResult.Ok -> globalResult.data
                is ApiResult.Error -> return
            }

        val nowEt = EasternTimeInstant.now()
        val today = nowEt.local.date
        val todayDow = isoDayOfWeek(today)

        for (p in profiles) {
            if (p.daysOfWeek.isEmpty()) continue
            if (!p.daysOfWeek.contains(todayDow)) continue

            val stop = global.getStop(p.stopId) ?: continue
            val label = p.stopLabel.ifBlank { stop.name }

            val depResult =
                when (p.mode) {
                    LastTrainMode.LAST ->
                        GlobalDataStore.client.fetchLastDepartureInWindow(
                            p.routeId,
                            p.directionId,
                            p.stopId,
                            minutesToHHmm(p.windowStartMinutes),
                            minutesToHHmm(p.windowEndMinutes),
                        )
                    LastTrainMode.FIRST ->
                        GlobalDataStore.client.fetchFirstDepartureInWindow(
                            p.routeId,
                            p.directionId,
                            p.stopId,
                            minutesToHHmm(p.firstWindowStartMinutes),
                            minutesToHHmm(p.firstWindowEndMinutes),
                        )
                }

            val dep =
                when (depResult) {
                    is ApiResult.Ok -> depResult.data.departure ?: continue
                    is ApiResult.Error -> continue
                }

            val notifyAt = dep.toEpochMilliseconds() - p.notifyMinutesBefore * 60_000L
            val nowMs = nowEt.toEpochMilliseconds()
            val key = "lt_${p.id}_${dep.toEpochMilliseconds()}"

            if (notifyAt <= nowMs && nowMs < notifyAt + WINDOW_MS) {
                if (!prefs.getBoolean(key, false)) {
                    prefs.edit().putBoolean(key, true).apply()
                    val modeLabel =
                        when (p.mode) {
                            LastTrainMode.LAST ->
                                applicationContext.getString(R.string.notif_last_train_title)
                            LastTrainMode.FIRST ->
                                applicationContext.getString(R.string.notif_first_train_title)
                        }
                    val route = global.getRoute(p.routeId)
                    notify(
                        id = key.hashCode(),
                        title = modeLabel,
                        text =
                            applicationContext.getString(
                                R.string.notif_last_train_body,
                                p.name,
                                label,
                                dep.local.toString().take(16),
                            ),
                        backgroundArgb = argbFromHexOrNull(route?.color.orEmpty()),
                        contentArgb = argbFromHexOrNull(route?.textColor.orEmpty()),
                    )
                }
            }
        }
    }

    private suspend fun runAccessibilityNotifications(prefs: android.content.SharedPreferences) {
        val repo = AccessibilityRepository(applicationContext)
        val watches = repo.load().filter { it.enabled }
        if (watches.isEmpty()) return

        val globalResult = GlobalDataStore.getOrLoad()
        val global =
            when (globalResult) {
                is ApiResult.Ok -> globalResult.data
                is ApiResult.Error -> return
            }

        val relevantEffects =
            setOf(
                "ELEVATOR_CLOSURE",
                "ESCALATOR_CLOSURE",
                "STOP_CLOSURE",
            )

        for (w in watches) {
            val stop = global.getStop(w.stopId) ?: continue
            val stopName = w.stopLabel.ifBlank { stop.name }
            val tokens =
                stopName
                    .lowercase()
                    .split(Regex("\\s+"))
                    .filter { it.length >= 4 }

            val alertsResult = GlobalDataStore.client.fetchAlertsForRoute(w.routeId)
            val alerts =
                when (alertsResult) {
                    is ApiResult.Ok -> alertsResult.data
                    is ApiResult.Error -> continue
                }

            for (alert in alerts) {
                val effect = alert.effect ?: continue
                if (effect !in relevantEffects) continue

                val headerLower = alert.header.lowercase()
                val matchesStation =
                    headerLower.contains(stopName.lowercase()) ||
                        tokens.any { headerLower.contains(it) }
                if (!matchesStation) continue

                val key = "acc_${w.id}_${alert.id}"
                if (prefs.getBoolean(key, false)) continue
                prefs.edit().putBoolean(key, true).apply()

                val route = global.getRoute(w.routeId)
                notify(
                    id = key.hashCode(),
                    title = applicationContext.getString(R.string.notif_accessibility_title),
                    text =
                        applicationContext.getString(
                            R.string.notif_accessibility_body,
                            w.name,
                            alert.header,
                        ),
                    backgroundArgb = argbFromHexOrNull(route?.color.orEmpty()),
                    contentArgb = argbFromHexOrNull(route?.textColor.orEmpty()),
                )
            }
        }
    }

    private fun isoDayOfWeek(date: LocalDate): Int =
        when (date.dayOfWeek) {
            kotlinx.datetime.DayOfWeek.MONDAY -> 1
            kotlinx.datetime.DayOfWeek.TUESDAY -> 2
            kotlinx.datetime.DayOfWeek.WEDNESDAY -> 3
            kotlinx.datetime.DayOfWeek.THURSDAY -> 4
            kotlinx.datetime.DayOfWeek.FRIDAY -> 5
            kotlinx.datetime.DayOfWeek.SATURDAY -> 6
            kotlinx.datetime.DayOfWeek.SUNDAY -> 7
        }

    private fun minutesToHHmm(minutes: Int): String {
        val h = (minutes / 60).coerceIn(0, 23)
        val m = (minutes % 60).coerceIn(0, 59)
        return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    }

    private fun atMinutesOnDate(
        date: LocalDate,
        minutesFromMidnight: Int,
        tz: TimeZone,
    ): EasternTimeInstant {
        val h = minutesFromMidnight / 60
        val m = minutesFromMidnight % 60
        val ldt = LocalDateTime(date, LocalTime(h, m, 0, 0))
        return EasternTimeInstant(ldt.toInstant(tz))
    }

    private fun notify(
        id: Int,
        title: String,
        text: String,
        backgroundArgb: Int? = null,
        contentArgb: Int? = null,
    ) {
        ensureChannel(applicationContext)
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pi =
            PendingIntent.getActivity(
                applicationContext,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val bg = backgroundArgb ?: applicationContext.getColor(R.color.fill2)
        val fg = contentArgb ?: applicationContext.getColor(R.color.key)
        fun buildRemoteViews(): RemoteViews =
            RemoteViews(applicationContext.packageName, R.layout.notification_commute).apply {
                setTextViewText(R.id.notification_title, title)
                setTextViewText(R.id.notification_text, text)
                setInt(R.id.notification_root, "setBackgroundColor", bg)
                setTextColor(R.id.notification_title, fg)
                setTextColor(R.id.notification_text, fg)
            }
        val custom = buildRemoteViews()
        val notif =
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_tminus)
                .setContentTitle(title)
                .setContentText(text)
                .setCustomContentView(custom)
                .setCustomBigContentView(custom)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()

        NotificationManagerCompat.from(applicationContext).notify(id, notif)
    }

    private fun argbFromHexOrNull(hex: String): Int? =
        runCatching {
            val clean = hex.trim().removePrefix("#")
            val v = clean.toLong(16)
            (0xFF000000L or v).toInt()
        }.getOrNull()

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_commute),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    setShowBadge(true)
                },
            )
        }
    }

    public companion object {
        public const val UNIQUE_NAME: String = "TminusNotifications"
        private const val PREFS_STATE = "tminus_notif_state"
        /** Bumped so existing installs pick up [NotificationManager.IMPORTANCE_HIGH] without stale channel settings. */
        private const val CHANNEL_ID = "commute_v2"
        /** Wide enough for 15-minute periodic checks plus clock skew (commute / last-train lead times). */
        private const val WINDOW_MS = 60_000L * 45
        private const val ARRIVAL_WINDOW_MS = 60_000L * 15
    }
}
