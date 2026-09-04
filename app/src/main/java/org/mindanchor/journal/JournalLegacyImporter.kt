package org.mindanchor.journal

import androidx.room.withTransaction
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.JournalDao
import org.mindanchor.data.db.JournalEntryEntity
import org.mindanchor.letters.JournalStore

/**
 * One-time importer that copies every entry out of the legacy
 * protective-writing [JournalStore] DataStore (BA / DEAR MAN / gratitude /
 * expressive writing, keyed by `<tag>:<date>`) into Room's
 * `journal_entries` table, then derives structural context for each
 * imported entry the same way [JournalRepository.create]'s fail-soft
 * second phase does.
 *
 * Idempotent by construction: each legacy (kind, date) pair maps to the
 * same deterministic id every time
 * (`UUID.nameUUIDFromBytes("legacy:<tag>:<date>")`), and the insert uses
 * IGNORE-on-conflict semantics ([org.mindanchor.data.db.JournalDao.insertEntriesIgnoreDuplicates]),
 * so re-running the import can never overwrite or duplicate a row.
 *
 * The legacy DataStore itself is never touched or deleted — it remains a
 * permanent rollback copy, per the Program 0 continuity design.
 */
class JournalLegacyImporter(
    private val journalStore: JournalStore,
    private val database: AnchorDatabase,
    private val migrationPrefs: JournalMigrationPrefs,
    private val extractor: JournalContextExtractor,
    // Defaults to the real DAO; overridable so a test can substitute a
    // failing fake to prove the completion flag is only set once the
    // entry-insert transaction has durably succeeded, without corrupting a
    // real database to force that failure. Same seam shape as
    // JournalRepository's JournalContextExtractor injection in Task 3.
    private val dao: JournalDao = database.journal(),
) {

    suspend fun importIfNeeded() {
        if (migrationPrefs.isLegacyImportComplete()) return

        val legacyEntries = journalStore.allEntries()
        val entities = legacyEntries.map { it.toLegacyEntity() }

        // The entry-insert phase must be durable before anything else
        // happens: this is the transaction the completion flag is gated
        // on. A failure here propagates and the flag is never set.
        database.withTransaction {
            dao.insertEntriesIgnoreDuplicates(entities)
        }

        // Context derivation is best-effort, exactly like
        // JournalRepository.create's fail-soft second phase: one entry's
        // extraction failure must never block another's, and none of it
        // may gate the completion flag below.
        entities.forEach { entity ->
            runCatching {
                val context = extractor.extract(entity.toDomain(), entity.updatedAt)
                if (context.isNotEmpty()) {
                    dao.upsertContext(context.map { it.toEntity() })
                }
            }
        }

        migrationPrefs.markLegacyImportComplete()
    }

    private fun JournalStore.Entry.toLegacyEntity(): JournalEntryEntity {
        val epochMillis = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return JournalEntryEntity(
            id = legacyId(kind, date),
            createdAt = epochMillis,
            updatedAt = epochMillis,
            localDate = date.toString(),
            title = "",
            body = body,
            kind = mapKind(kind).name,
            sourceDeviceId = LEGACY_SOURCE_DEVICE_ID,
            deletedAt = null,
        )
    }

    private fun mapKind(kind: JournalStore.Kind): JournalKind = when (kind) {
        JournalStore.Kind.BA -> JournalKind.BA
        JournalStore.Kind.DEAR_MAN -> JournalKind.DEAR_MAN
        JournalStore.Kind.GRATITUDE -> JournalKind.GRATITUDE
        JournalStore.Kind.EXPRESSIVE_WRITING -> JournalKind.EXPRESSIVE_WRITING
    }

    companion object {
        const val LEGACY_SOURCE_DEVICE_ID = "legacy-datastore"

        private fun legacyId(kind: JournalStore.Kind, date: LocalDate): String =
            UUID.nameUUIDFromBytes("legacy:${kind.tag}:$date".encodeToByteArray()).toString()
    }
}
