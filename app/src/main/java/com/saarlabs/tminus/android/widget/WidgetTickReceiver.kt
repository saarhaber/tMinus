package com.saarlabs.tminus.android.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Consumes each exact-alarm tick emitted by [LiveUpdateManager]: recomposes the trip widget and
 * queues the next alarm. We use [goAsync] so the Glance update can run on a coroutine without the
 * system killing the process before the broadcast returns, and we always call
 * [android.content.BroadcastReceiver.PendingResult.finish] so the process can return to its idle
 * power state between ticks.
 */
public class WidgetTickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LiveUpdateManager.ACTION_TICK) return

        val appCtx = context.applicationContext

        // Honour a stop() that raced the alarm: don't update and don't chain.
        if (!LiveUpdateManager.isRunning(appCtx)) {
            LiveUpdateManager.stop(appCtx)
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                MBTATripWidget().updateAll(appCtx)
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "updateAll failed", t)
            } finally {
                try {
                    LiveUpdateManager.scheduleNext(appCtx)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private companion object {
        private const val TAG = "WidgetTickReceiver"
    }
}
