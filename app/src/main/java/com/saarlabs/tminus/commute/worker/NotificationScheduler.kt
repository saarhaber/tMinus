package com.saarlabs.tminus.commute.worker

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Runs [TminusNotificationWorker] once as soon as the system allows (e.g. after saving a commute),
 * so alerts are not delayed until the next 15-minute periodic tick.
 */
internal object NotificationScheduler {
    fun enqueueImmediateRun(context: Context) {
        val work = OneTimeWorkRequestBuilder<TminusNotificationWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueue(work)
    }
}
