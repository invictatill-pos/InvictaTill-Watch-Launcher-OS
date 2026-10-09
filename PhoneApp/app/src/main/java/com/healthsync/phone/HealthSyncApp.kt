package com.healthsync.phone

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.healthsync.phone.data.FitnessRepository
import javax.inject.Inject
import kotlinx.coroutines.*

@HiltAndroidApp
class HealthSyncApp : Application() {
    @Inject lateinit var repository: FitnessRepository
    private val housekeeping = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        housekeeping.launch {
            try { repository.pruneOldData() }
            catch (e: Exception) { android.util.Log.w("HealthSync", "History cleanup postponed", e) }
        }
    }
}
