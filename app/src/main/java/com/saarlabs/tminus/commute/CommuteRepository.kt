package com.saarlabs.tminus.commute

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.saarlabs.tminus.commute.worker.NotificationScheduler
import com.saarlabs.tminus.commute.worker.TminusNotificationWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val PREFS = "commute_profiles"
private const val KEY_LIST = "profiles_json"

internal class CommuteRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    suspend fun loadProfiles(): List<CommuteProfile> =
        withContext(Dispatchers.IO) {
            val raw = prefs.getString(KEY_LIST, null) ?: return@withContext emptyList()
            runCatching {
                json.decodeFromString(ListSerializer(CommuteProfile.serializer()), raw)
            }.getOrElse { emptyList() }
        }

    suspend fun saveProfiles(profiles: List<CommuteProfile>) =
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(
                    KEY_LIST,
                    json.encodeToString(ListSerializer(CommuteProfile.serializer()), profiles),
                )
                .apply()
            scheduleWorker()
            NotificationScheduler.enqueueImmediateRun(context)
        }

    private fun scheduleWorker() {
        val work =
            PeriodicWorkRequestBuilder<TminusNotificationWorker>(15, TimeUnit.MINUTES)
                .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                TminusNotificationWorker.UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                work,
            )
    }

    companion object {
        fun ensureWorkerScheduled(context: Context) {
            val work =
                PeriodicWorkRequestBuilder<TminusNotificationWorker>(15, TimeUnit.MINUTES)
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    TminusNotificationWorker.UNIQUE_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    work,
                )
        }
    }
}
