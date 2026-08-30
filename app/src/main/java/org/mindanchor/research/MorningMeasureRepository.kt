package org.mindanchor.research

import android.content.Context
import androidx.room.withTransaction
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mindanchor.continuity.ContinuityWorkScheduler
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.ContinuityChangeEntity
import org.mindanchor.journal.ChangeOperation
import org.mindanchor.journal.DeviceIdentityStore

/**
 * Owns the daily morning research measure. [save] upserts by local date —
 * an accidental duplicate tap for the same day updates the existing record
 * in place (preserving its original `createdAt`) rather than creating a
 * second one, so `morning_measures` never holds more than one row per date.
 *
 * The measure itself is unchanged from Program 0: same five items, same
 * table, same instrument version, no total and no threshold anywhere.
 * Program 1 adds only the study-phase attribution.
 */
class MorningMeasureRepository(
    private val context: Context,
    private val database: AnchorDatabase,
    private val deviceIdentity: DeviceIdentityStore,
    private val provenance: ResearchProvenanceCoordinator,
) {
    private val dao = database.journal()

    suspend fun save(
        localDate: LocalDate,
        now: Long,
        mood: Int,
        anxiety: Int,
        angerUrge: Int,
        energyFunction: Int,
        sleepQuality: Int,
    ): MorningMeasure {
        val measure = database.withTransaction {
            val existing = dao.morningMeasureByDate(localDate.toString())
            // Inside the same transaction, and before the write: a measure
            // that fell outside every recorded phase could not be
            // attributed to the software that produced it.
            val phase = provenance.ensureCurrentPhase(now)
            val recordedAt = maxOf(now, phase.startedAt)
            val candidate = MorningMeasure.create(
                localDate = localDate,
                now = recordedAt,
                mood = mood,
                anxiety = anxiety,
                angerUrge = angerUrge,
                energyFunction = energyFunction,
                sleepQuality = sleepQuality,
                sourceDeviceId = deviceIdentity.id(),
                id = existing?.id ?: UUID.randomUUID().toString(),
                createdAt = existing?.createdAt ?: recordedAt,
            )
            dao.upsertMorningMeasure(candidate.toEntity())
            dao.insertChange(
                ContinuityChangeEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = "MORNING_MEASURE",
                    entityId = candidate.id,
                    operation = if (existing == null) ChangeOperation.CREATE.name else ChangeOperation.UPDATE.name,
                    occurredAt = recordedAt,
                    acknowledgedSnapshotId = null,
                ),
            )
            candidate
        }
        // After the transaction: a morning measure can open a study
        // phase, which appends to the ledger, and the high-water mark has
        // to be raised from a committed count.
        provenance.refreshAfterCommit()
        ContinuityWorkScheduler.requestCheckpoint(context)
        return measure
    }

    /** The measure already saved for [localDate], if any — lets the card show today's saved values. */
    fun forDate(localDate: LocalDate): Flow<MorningMeasure?> =
        dao.morningMeasures().map { rows -> rows.firstOrNull { it.localDate == localDate.toString() }?.toDomain() }
}
