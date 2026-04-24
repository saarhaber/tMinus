package com.saarlabs.tminus.android.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

/**
 * Drives a 60 s refresh cadence for the trip widget via a self-chaining exact alarm, which bypasses
 * WorkManager's 15 min [androidx.work.PeriodicWorkRequest] floor. Each tick is fired by
 * [WidgetTickReceiver]; after the receiver updates the widget it calls [scheduleNext] to queue the
 * following alarm, so the process can go idle between ticks instead of holding a foreground state.
 *
 * On API 31+ the user (or the system) may revoke `SCHEDULE_EXACT_ALARM` at any time, so
 * [canScheduleExactAlarms] is consulted before every schedule call to avoid [SecurityException].
 */
public object LiveUpdateManager {

    internal const val TICK_INTERVAL_MS: Long = 60_000L

    /** Hard cap on how long a single live session is allowed to run without being explicitly renewed. */
    public const val DEFAULT_DURATION_MS: Long = 30L * 60_000L

    public const val ACTION_TICK: String = "com.saarlabs.tminus.action.WIDGET_LIVE_TICK"

    private const val PREFS_NAME = "widget_live_update"
    private const val KEY_ACTIVE = "active"
    private const val KEY_DEADLINE_ELAPSED = "deadline_elapsed"
    private const val REQUEST_CODE = 0xB7E7

    /**
     * Starts the live-update chain. Returns `false` if the OS currently denies exact alarms, in which
     * case the caller should surface the [android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM]
     * screen. A [durationMs] of `0` disables the deadline, but callers are expected to pair that with
     * an explicit [stop] to keep battery usage bounded.
     */
    public fun start(context: Context, durationMs: Long = DEFAULT_DURATION_MS): Boolean {
        val appCtx = context.applicationContext
        if (!canScheduleExactAlarms(appCtx)) return false
        val deadline = if (durationMs > 0L) SystemClock.elapsedRealtime() + durationMs else 0L
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVE, true)
            .putLong(KEY_DEADLINE_ELAPSED, deadline)
            .apply()
        scheduleNext(appCtx)
        return true
    }

    /**
     * Cancels any pending tick and clears the active flag. Safe to call repeatedly and from any
     * thread; the receiver re-checks [isRunning] before chaining, so a stop while a tick is in
     * flight will simply end the chain.
     */
    public fun stop(context: Context) {
        val appCtx = context.applicationContext
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVE, false)
            .putLong(KEY_DEADLINE_ELAPSED, 0L)
            .apply()
        val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(appCtx))
    }

    public fun isRunning(context: Context): Boolean {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return false
        val deadline = prefs.getLong(KEY_DEADLINE_ELAPSED, 0L)
        return deadline == 0L || SystemClock.elapsedRealtime() < deadline
    }

    /**
     * Pre-API 31 this is always allowed. On API 31+ the permission is revocable at runtime and must
     * be re-checked right before each schedule call.
     */
    public fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.applicationContext
            .getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    /**
     * Queues the next tick. Called from [WidgetTickReceiver] after each update to keep the cadence;
     * the receiver — not this manager — is what holds the process alive during a tick.
     */
    internal fun scheduleNext(context: Context) {
        val appCtx = context.applicationContext
        if (!isRunning(appCtx)) {
            stop(appCtx)
            return
        }
        if (!canScheduleExactAlarms(appCtx)) {
            // Permission revoked mid-chain. Stop silently rather than crashing with SecurityException.
            stop(appCtx)
            return
        }
        val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = SystemClock.elapsedRealtime() + TICK_INTERVAL_MS
        // setExactAndAllowWhileIdle still fires under Doze; the system rate-limits it to ~9 min per
        // app in deep idle, which is fine for foreground widget use and recovers on unlock.
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            buildPendingIntent(appCtx),
        )
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetTickReceiver::class.java).apply {
            action = ACTION_TICK
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
