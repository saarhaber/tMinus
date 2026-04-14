package com.saarlabs.tminus.features

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

private val featureJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

internal class LastTrainRepository(private val context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("last_train_profiles", Context.MODE_PRIVATE)

    suspend fun load(): List<LastTrainProfile> =
        withContext(Dispatchers.IO) {
            val raw = prefs.getString("list", null) ?: return@withContext emptyList()
            runCatching {
                featureJson.decodeFromString(ListSerializer(LastTrainProfile.serializer()), raw)
            }.getOrElse { emptyList() }
        }

    suspend fun save(list: List<LastTrainProfile>) =
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(
                    "list",
                    featureJson.encodeToString(ListSerializer(LastTrainProfile.serializer()), list),
                )
                .apply()
            scheduleWorker()
            NotificationScheduler.enqueueImmediateRun(context.applicationContext)
        }

    private fun scheduleWorker() {
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                TminusNotificationWorker.UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<TminusNotificationWorker>(15, TimeUnit.MINUTES).build(),
            )
    }

    companion object {
        fun ensureWorker(context: Context) {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    TminusNotificationWorker.UNIQUE_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<TminusNotificationWorker>(15, TimeUnit.MINUTES).build(),
                )
        }
    }
}

internal class AccessibilityRepository(private val context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("accessibility_watches", Context.MODE_PRIVATE)

    suspend fun load(): List<AccessibilityWatch> =
        withContext(Dispatchers.IO) {
            val raw = prefs.getString("list", null) ?: return@withContext emptyList()
            runCatching {
                featureJson.decodeFromString(ListSerializer(AccessibilityWatch.serializer()), raw)
            }.getOrElse { emptyList() }
        }

    suspend fun save(list: List<AccessibilityWatch>) =
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(
                    "list",
                    featureJson.encodeToString(ListSerializer(AccessibilityWatch.serializer()), list),
                )
                .apply()
            LastTrainRepository.ensureWorker(context.applicationContext)
            NotificationScheduler.enqueueImmediateRun(context.applicationContext)
        }
}
