package org.mindanchor.journal

import android.content.Context
import androidx.room.withTransaction
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.mindanchor.continuity.ContinuityPrefs
import org.mindanchor.continuity.ContinuityWorkScheduler
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.ContinuityChangeEntity

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
) {
    private val dao = database.journal()

    suspend fun create(title: String, body: String, now: Long, localDate: LocalDate): JournalEntry {
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
        // Task 10 (minimal, safe addition): the flag is stored by
        // ContinuityPrefs (Task 10's job); actually gating the
        // extraction call on it is a one-line check, so it is done here
        // rather than leaving the flag unread until a later task.
        if (ContinuityPrefs(context).contextExtractionEnabled.first()) {
            deriveContext(entry, now)
        }
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

    private suspend fun deriveContext(entry: JournalEntry, now: Long) {
        runCatching {
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
