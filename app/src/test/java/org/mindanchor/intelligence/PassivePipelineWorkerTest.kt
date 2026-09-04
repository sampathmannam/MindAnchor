package org.mindanchor.intelligence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.testing.TestListenableWorkerBuilder
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.PassiveRawProvenanceEntity
import org.mindanchor.data.db.PassiveRawSampleEntity
import org.mindanchor.data.db.withResearchImmutability
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PassivePipelineWorkerTest {
    private lateinit var context: Context
    private lateinit var database: AnchorDatabase
    private lateinit var worker: PassivePipelineWorker

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .withResearchImmutability()
            .allowMainThreadQueries()
            .build()
        val request = OneTimeWorkRequestBuilder<PassivePipelineWorker>().build()
        worker = TestListenableWorkerBuilder.from(context, request).build() as PassivePipelineWorker
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `completed denied and unavailable run succeeds and prunes samples older than fourteen days`() = runBlocking {
        val now = 20L * DAY_MILLIS
        seedRawSample("old", 5L * DAY_MILLIS)
        seedRawSample("boundary", 6L * DAY_MILLIS)
        val repository = repository(
            listOf(
                PassiveReadState.PERMISSION_DENIED,
                PassiveReadState.UNAVAILABLE,
                PassiveReadState.READ_FAILURE_PERMANENT,
            ),
        )

        val result = worker.run(repository, database.passive(), now, ZoneId.of("UTC"))

        assertEquals(ListenableWorker.Result.success()::class, result::class)
        assertEquals(listOf("boundary"), database.passive().rawRecords(0L, now).map { it.provenance.id })
        assertEquals(setOf("old", "boundary"), database.passive().rawProvenanceNow().map { it.id }.toSet())
        assertEquals(1, database.passive().pipelineRunsNow().size)
    }

    @Test
    fun `transient run retries without pruning raw samples`() = runBlocking {
        val now = 20L * DAY_MILLIS
        seedRawSample("old", 5L * DAY_MILLIS)
        val repository = repository(listOf(PassiveReadState.READ_FAILURE_TRANSIENT))

        val result = worker.run(repository, database.passive(), now, ZoneId.of("UTC"))

        assertEquals(ListenableWorker.Result.retry()::class, result::class)
        assertEquals(listOf("old"), database.passive().rawRecords(0L, now).map { it.provenance.id })
    }

    @Test
    fun `worker does not catch cancellation`() {
        val repository = PassivePipelineRepository(
            database = database,
            healthSource = object : PassiveRecordSource {
                override suspend fun read(range: PassiveReadRange): List<PassiveSourceRead> {
                    throw CancellationException("cancelled")
                }
            },
            usageSource = FixedSource(emptyList()),
            historyPermissionGranted = { false },
            ensureCurrentPhase = {},
            refreshProvenanceAfterCommit = {},
        )

        assertThrows(CancellationException::class.java) {
            runBlocking { worker.run(repository, database.passive(), 20L * DAY_MILLIS, ZoneId.of("UTC")) }
        }
    }

    @Test
    fun `DAO exposes no delete for provenance or long-term operational history`() {
        val source = moduleFile("src/main/java/org/mindanchor/data/db/PassiveDao.kt").readText()
        val deletes = Regex("DELETE FROM (passive_[a-z_]+)", RegexOption.IGNORE_CASE)
            .findAll(source)
            .map { it.groupValues[1].lowercase() }
            .toList()

        assertEquals(listOf("passive_raw_samples"), deletes)
        assertTrue(
            "raw sample retention must remain exactly fourteen days",
            PassivePipelineWorker.RAW_RETENTION_MILLIS == 14L * DAY_MILLIS,
        )
    }

    private fun repository(states: List<PassiveReadState>) = PassivePipelineRepository(
        database = database,
        healthSource = object : PassiveRecordSource {
            override suspend fun read(range: PassiveReadRange): List<PassiveSourceRead> =
                states.mapIndexed { index, state ->
                    PassiveSourceRead(
                        sourceFamily = PassiveSourceFamily.entries[index],
                        state = state,
                        range = range,
                        attemptedAt = range.endExclusive,
                        errorCode = state.name.takeUnless { state == PassiveReadState.SUCCESS },
                    )
                }
        },
        usageSource = FixedSource(emptyList()),
        historyPermissionGranted = { false },
        ensureCurrentPhase = {},
        refreshProvenanceAfterCommit = {},
    )

    private suspend fun seedRawSample(id: String, ingestedAt: Long) {
        database.passive().insertRawProvenance(
            listOf(
                PassiveRawProvenanceEntity(
                    id = id,
                    sourceFamily = PassiveSourceFamily.STEPS.name,
                    recordKind = PassiveRecordKind.STEPS_INTERVAL.name,
                    eventStart = ingestedAt,
                    eventEnd = ingestedAt + 1L,
                    unit = "count",
                    dataOriginPackage = "test",
                    deviceManufacturer = null,
                    deviceModel = null,
                    deviceType = null,
                    sourceUpdatedTime = ingestedAt,
                    ingestedAt = ingestedAt,
                    zoneId = "UTC",
                    zoneOffsetSeconds = 0,
                    recordId = id,
                    recordVersion = 1L,
                ),
            ),
        )
        database.passive().insertRawSamples(listOf(PassiveRawSampleEntity(id, 1.0, ingestedAt)))
    }

    private fun moduleFile(relativePath: String): File = File(relativePath).takeIf(File::exists)
        ?: File("app/$relativePath")

    private class FixedSource(private val reads: List<PassiveSourceRead>) : PassiveRecordSource {
        override suspend fun read(range: PassiveReadRange): List<PassiveSourceRead> = reads
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
