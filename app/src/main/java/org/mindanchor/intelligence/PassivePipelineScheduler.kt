package org.mindanchor.intelligence

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object PassivePipelineScheduler {
    const val PERIODIC_WORK_NAME = "passive_operational_pipeline"
    const val INTERVAL_HOURS = 6L

    internal fun constraints(): Constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
        .build()

    internal fun buildRequest(): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<PassivePipelineWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints())
            .build()

    fun ensureScheduled(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            buildRequest(),
        )
    }
}
