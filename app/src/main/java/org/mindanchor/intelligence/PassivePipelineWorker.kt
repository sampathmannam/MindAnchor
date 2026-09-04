package org.mindanchor.intelligence

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import java.time.ZoneId
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.PassiveDao

class PassivePipelineWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        return run(
            pipeline = PassivePipelineRepository.build(applicationContext),
            dao = AnchorDatabase.get(applicationContext).passive(),
            now = now,
            zone = ZoneId.systemDefault(),
        )
    }

    internal suspend fun run(
        pipeline: PassivePipelineRepository,
        dao: PassiveDao,
        now: Long,
        zone: ZoneId,
    ): ListenableWorker.Result = when (pipeline.run(now, zone)) {
        is PassivePipelineResult.Retry -> Result.retry()
        is PassivePipelineResult.Completed -> {
            dao.pruneRawSamples(now - RAW_RETENTION_MILLIS)
            Result.success()
        }
    }

    companion object {
        const val RAW_RETENTION_MILLIS = 14L * 24L * 60L * 60L * 1_000L
    }
}
