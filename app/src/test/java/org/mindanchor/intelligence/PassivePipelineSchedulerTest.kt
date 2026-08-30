package org.mindanchor.intelligence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PassivePipelineSchedulerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    @Test
    fun `periodic request is six-hour battery-aware local work`() {
        val constraints = PassivePipelineScheduler.constraints()

        assertEquals(6L, PassivePipelineScheduler.INTERVAL_HOURS)
        assertTrue(constraints.requiresBatteryNotLow())
        assertEquals(NetworkType.NOT_REQUIRED, constraints.requiredNetworkType)
        assertNotNull(PassivePipelineScheduler.buildRequest())
    }

    @Test
    fun `ensureScheduled uses one UPDATE periodic work`() {
        PassivePipelineScheduler.ensureScheduled(context)
        PassivePipelineScheduler.ensureScheduled(context)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(PassivePipelineScheduler.PERIODIC_WORK_NAME)
            .get()

        assertEquals("passive_operational_pipeline", PassivePipelineScheduler.PERIODIC_WORK_NAME)
        assertEquals(1, infos.size)
        val source = moduleFile("src/main/java/org/mindanchor/intelligence/PassivePipelineScheduler.kt").readText()
        assertTrue(source.contains("ExistingPeriodicWorkPolicy.UPDATE"))
    }

    @Test
    fun `scheduler and HomeActivity preserve the local enqueue-only boundary`() {
        val scheduler = moduleFile("src/main/java/org/mindanchor/intelligence/PassivePipelineScheduler.kt").readText()
        val home = moduleFile("src/main/java/org/mindanchor/HomeActivity.kt").readText()

        listOf("NetworkType.CONNECTED", "okhttp", "GoogleDrive", "Coros", "COROS", "Auth").forEach { forbidden ->
            assertFalse("scheduler must not reference $forbidden", scheduler.contains(forbidden, ignoreCase = false))
        }
        assertTrue(home.contains("PassivePipelineScheduler.ensureScheduled(applicationContext)"))
        assertFalse(home.contains("PassivePipelineRepository"))
        assertFalse(home.contains(".run(System.currentTimeMillis()"))
    }

    private fun moduleFile(relativePath: String): File = File(relativePath).takeIf(File::exists)
        ?: File("app/$relativePath")
}
