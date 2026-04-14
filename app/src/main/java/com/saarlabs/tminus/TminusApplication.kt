package com.saarlabs.tminus

import android.app.Application
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.model.response.GlobalData
import com.saarlabs.tminus.network.MbtaV3Client
import com.saarlabs.tminus.usecases.WidgetTripUseCase
import com.saarlabs.tminus.commute.CommuteRepository
import com.saarlabs.tminus.features.LastTrainRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public class TminusApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        refreshNetworking()
        CommuteRepository.ensureWorkerScheduled(this)
        LastTrainRepository.ensureWorker(this)
    }

    public companion object {
        lateinit var instance: TminusApplication
            private set

        lateinit var widgetTripUseCase: WidgetTripUseCase
            private set

        fun refreshNetworking() {
            val prefs = instance.getSharedPreferences(SettingsKeys.PREFS, MODE_PRIVATE)
            val v3 = prefs.getString(SettingsKeys.KEY_V3_API, null)
            val client = MbtaV3Client(v3)
            widgetTripUseCase = WidgetTripUseCase(client)
            GlobalDataStore.client = client
        }
    }
}

internal object GlobalDataStore {
    lateinit var client: MbtaV3Client

    private val mutex = Mutex()
    private var cached: GlobalData? = null

    suspend fun getOrLoad(): ApiResult<GlobalData> =
        mutex.withLock {
            cached?.let { return ApiResult.Ok(it) }
            when (val r = client.fetchGlobalData()) {
                is ApiResult.Ok -> {
                    cached = r.data
                    r
                }
                is ApiResult.Error -> r
            }
        }

    fun invalidate() {
        cached = null
    }
}
