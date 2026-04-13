package com.saarlabs.tminus.commute.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mbta.tid.mbta_app.model.response.ApiResult
import com.mbta.tid.mbta_app.utils.EasternTimeInstant
import com.saarlabs.tminus.GlobalDataStore
import com.saarlabs.tminus.MainActivity
import com.saarlabs.tminus.R
import com.saarlabs.tminus.commute.CommuteRepository
import com.saarlabs.tminus.commute.CommuteTripPlanner
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

public class CommuteNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val repo = CommuteRepository(applicationContext)
            val profiles = repo.loadProfiles().filter { it.enabled }
            if (profiles.isEmpty()) return@withContext Result.success()

            val globalResult = GlobalDataStore.getOrLoad()
            val global =
                when (globalResult) {
                    is ApiResult.Ok -> globalResult.data
                    is ApiResult.Error -> return@withContext Result.retry()
                }

            val tz = EasternTimeInstant.timeZone
            val nowEt = EasternTimeInstant.now()
            val today = nowEt.local.date
            val todayDow =
                when (today.dayOfWeek) {
                    kotlinx.datetime.DayOfWeek.MONDAY -> 1
                    kotlinx.datetime.DayOfWeek.TUESDAY -> 2
                    kotlinx.datetime.DayOfWeek.WEDNESDAY -> 3
                    kotlinx.datetime.DayOfWeek.THURSDAY -> 4
                    kotlinx.datetime.DayOfWeek.FRIDAY -> 5
                    kotlinx.datetime.DayOfWeek.SATURDAY -> 6
                    kotlinx.datetime.DayOfWeek.SUNDAY -> 7
                }

            val prefs = applicationContext.getSharedPreferences("commute_notif_state", Context.MODE_PRIVATE)

            for (profile in profiles) {
                if (profile.daysOfWeek.isEmpty()) continue
                if (!profile.daysOfWeek.contains(todayDow)) continue

                val fromStop = global.getStop(profile.fromStopId) ?: continue
                val toStop = global.getStop(profile.toStopId) ?: continue
                val fromIds =
                    listOf(profile.fromStopId) +
                        fromStop.childStopIds.filter { global.stops.containsKey(it) }
                val toIds =
                    listOf(profile.toStopId) + toStop.childStopIds.filter { global.stops.containsKey(it) }
                val stopIds = (fromIds + toIds).distinct()

                val targetMinutes = profile.targetMinutesFromMidnight
                val windowStart =
                    max(0, targetMinutes - profile.windowMinutesBefore)
                val windowEnd =
                    min(24 * 60 - 1, targetMinutes + profile.windowMinutesAfter)

                val minH = windowStart / 60
                val minM = windowStart % 60
                val maxH = windowEnd / 60
                val maxM = windowEnd % 60
                val minTime =
                    "${minH.toString().padStart(2, '0')}:${minM.toString().padStart(2, '0')}"
                val maxTime =
                    "${maxH.toString().padStart(2, '0')}:${maxM.toString().padStart(2, '0')}"

                val schedResult =
                    GlobalDataStore.client.fetchScheduleForStopsInWindow(stopIds, minTime, maxTime)
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
                val toName = profile.toLabel.ifBlank { trip.toStop.name }

                val leaveKey = "leave_${profile.id}_${trip.tripId}_${trip.departureTime.toEpochMilliseconds()}"
                val arrivalKey = "arr_${profile.id}_${trip.tripId}_${trip.arrivalTime.toEpochMilliseconds()}"

                val leadMs = profile.notifyLeadMinutes * 60_000L
                val leaveAtMs = trip.departureTime.toEpochMilliseconds() - leadMs
                val nowMs = nowEt.toEpochMilliseconds()

                if (leaveAtMs <= nowMs && nowMs < leaveAtMs + 60_000 * 20) {
                    if (!prefs.getBoolean(leaveKey, false)) {
                        prefs.edit().putBoolean(leaveKey, true).apply()
                        showNotification(
                            context = applicationContext,
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
                        )
                    }
                }

                if (profile.notifyOnArrival) {
                    val arrMs = trip.arrivalTime.toEpochMilliseconds()
                    if (arrMs <= nowMs && nowMs < arrMs + 60_000 * 15) {
                        if (!prefs.getBoolean(arrivalKey, false)) {
                            prefs.edit().putBoolean(arrivalKey, true).apply()
                            showNotification(
                                context = applicationContext,
                                id = arrivalKey.hashCode(),
                                title = applicationContext.getString(R.string.notif_arrival_title),
                                text =
                                    applicationContext.getString(
                                        R.string.notif_arrival_body,
                                        profile.name,
                                        toName,
                                    ),
                            )
                        }
                    }
                }
            }

            Result.success()
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

    private fun showNotification(
        context: Context,
        id: Int,
        title: String,
        text: String,
    ) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java)
        val pi =
            PendingIntent.getActivity(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notif =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()

        NotificationManagerCompat.from(context).notify(id, notif)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_commute),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }

    companion object {
        const val UNIQUE_NAME: String = "CommuteNotification"
        private const val CHANNEL_ID = "commute"
    }
}
