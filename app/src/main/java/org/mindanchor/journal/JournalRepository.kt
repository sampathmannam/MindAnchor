package org.mindanchor.journal

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.mindanchor.continuity.ContinuityPrefs
import org.mindanchor.continuity.ContinuityWorkScheduler
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.ContinuityChangeEntity
import org.mindanchor.research.ResearchProvenanceCoordinator

/**
 * Owns Journal authorship (the entry itself) and its derived structural
 * context. The two are committed separately and in that order: the entry
 * and its continuity change first, then context in a fail-soft block after
 * — a failure deriving context must never cost the user their words.
 */
class JournalRepository(
    private val context: Context,
    private val database: AnchorDatabase,
    private val deviceIdentity: DeviceIdentityStore,
    private val extractor: JournalContextExtractor,
    private val provenance: ResearchProvenanceCoordinator,
) {
    private val dao = database.journal()

    suspend fun create(title: String, body: String, now: Long, localDate: LocalDate): JournalEntry {
        ensurePhase(now)
        val entry = JournalEntry.create(
            title = title,
            body = body,
            now = now,
            localDate = localDate,
            sourceDeviceId = deviceIdentity.id(),
        )
        database.withTransaction {
            dao.insertEntry(entry.toEntity())
            dao.insertChange(
                ContinuityChangeEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = "JOURNAL_ENTRY",
                    entityId = entry.id,
                    operation = ChangeOperation.CREATE.name,
                    occurredAt = now,
                    acknowledgedSnapshotId = null,
                ),
            )
        }
        // Task 10: the full checkpoint captures this entry's current
        // state; requested after the transaction above already
        // committed, purely additive.
        ContinuityWorkScheduler.requestCheckpoint(context)
        deriveContext(entry, now)
        return entry
    }

    fun entries(): Flow<List<JournalEntry>> = dao.entries().map { rows -> rows.map { it.toDomain() } }

    fun context(entryId: String): Flow<List<JournalContext>> = flow {
        emit(dao.allContext().filter { it.entryId == entryId }.map { it.toDomain() })
    }

    /** Re-runs context derivation for an entry whose extraction failed or was never attempted. */
    suspend fun retryContext(entryId: String) {
        val entry = dao.entry(entryId)?.toDomain() ?: return
        deriveContext(entry, System.currentTimeMillis())
    }

    /** Tombstones an entry — Program 0 never physically deletes Journal content. */
    suspend fun delete(entryId: String, now: Long) {
        database.withTransaction {
            dao.tombstone(entryId, deletedAt = now, updatedAt = now)
            dao.insertChange(
                ContinuityChangeEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = "JOURNAL_ENTRY",
                    entityId = entryId,
                    operation = ChangeOperation.DELETE.name,
                    occurredAt = now,
                    acknowledgedSnapshotId = null,
                ),
            )
        }
        ContinuityWorkScheduler.requestCheckpoint(context)
    }

    /**
     * Opens a study phase so the entry about to be written falls inside
     * one, using the same [now] the entry will carry — `phaseAt` is
     * inclusive of `startedAt`, so equal timestamps attribute correctly.
     *
     * Fail-soft, and deliberately *before* the entry rather than after:
     * running it first is what guarantees attribution when it succeeds,
     * and swallowing its failure is what guarantees the person never
     * loses their words when it does not. An entry with no phase is
     * honest — it simply predates any recorded phase — where an entry
     * timestamped before the phase that supposedly covers it would not be.
     */
    private suspend fun ensurePhase(now: Long) {
        runCatching { provenance.ensureCurrentPhase(now) }.onFailure { thrown ->
            // Never swallow a cancellation: the coroutine is being torn
            // down, and turning that into "carry on" would break the
            // caller's structured concurrency.
            if (thrown is CancellationException) throw thrown
            // A device stuck failing to open a phase would otherwise write
            // Journal entries attributed to nothing, silently, forever.
            Log.w("JournalRepository", "could not open a study phase for this entry", thrown)
        }
    }

    /**
     * Fail-soft: a thrown exception here (including a DataStore read
     * failure on the kill switch below) must never roll back or block
     * the entry `create()`/`retryContext` already committed — this is
     * why the flag check lives inside the same `runCatching` as
     * extraction itself, not as an earlier, separately-failing guard.
     */
    private suspend fun deriveContext(entry: JournalEntry, now: Long) {
        runCatching {
            if (!ContinuityPrefs(context).contextExtractionEnabled.first()) return@runCatching
            val context = extractor.extract(entry, now)
            if (context.isNotEmpty()) {
                database.withTransaction {
                    dao.upsertContext(context.map { it.toEntity() })
                    dao.insertChange(
                        ContinuityChangeEntity(
                            id = UUID.randomUUID().toString(),
                            entityType = "JOURNAL_CONTEXT",
                            entityId = entry.id,
                            operation = ChangeOperation.CREATE.name,
                            occurredAt = now,
                            acknowledgedSnapshotId = null,
                        ),
                    )
                }
            }
        }
    }
}
