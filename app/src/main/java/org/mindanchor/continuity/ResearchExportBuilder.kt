package org.mindanchor.continuity

import android.content.Context
import android.net.Uri
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mindanchor.backup.BackupRepository
import org.mindanchor.data.db.AnchorDatabase

/**
 * Task 12: the Journal research export, built once here so both the
 * Settings screen and a later Journal Patterns wiring call the exact same
 * logic rather than duplicating it inline in a Composable — see the Task
 * 12 brief's Step 4.
 *
 * Plaintext, unlike [org.mindanchor.continuity.crypto.BackupEnvelopeCodec]'s
 * encrypted continuity snapshot copy: this is the structured research JSON
 * a person (or a researcher, with explicit consent) can read directly.
 * Callers are responsible for their own privacy warning/consent step
 * before invoking [export] — this object only builds and writes the file.
 */
object ResearchExportBuilder {

    /** `mindanchor-research-YYYY-MM-DD.json`, from [today]'s local date (ISO-8601, e.g. "2026-08-29"). */
    fun fileName(today: LocalDate = LocalDate.now()): String = "mindanchor-research-$today.json"

    /** The first 12 hex characters of a content hash, for a short human-comparable success message. */
    fun truncatedHash(contentSha256: String): String = contentSha256.take(TRUNCATED_HASH_LENGTH)

    sealed class ExportOutcome {
        /** [contentSha256] is the FULL hash; callers show [truncatedHash] of it. */
        data class Success(val contentSha256: String) : ExportOutcome()
        data object WriteFailed : ExportOutcome()
    }

    /**
     * Builds a [ResearchExport] from [database]'s Journal tables, encodes
     * it to JSON via [ResearchExportCodec], and writes it to [uri]. The
     * whole thing runs on [Dispatchers.IO]: the Room read and the file
     * write are both blocking I/O.
     */
    suspend fun export(
        context: Context,
        database: AnchorDatabase,
        uri: Uri,
        now: Long = System.currentTimeMillis(),
    ): ExportOutcome = withContext(Dispatchers.IO) {
        val dao = database.journal()
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val export = ResearchExportCodec.buildFrom(
            entries = dao.entriesNow().map { it.toDto() },
            context = dao.allContext().map { it.toDto() },
            measures = dao.morningMeasuresNow().map { it.toDto() },
            now = now,
            appVersionCode = packageInfo.longVersionCode.toInt(),
            appVersionName = packageInfo.versionName.orEmpty(),
        )
        val wrote = BackupRepository.write(context, uri, ResearchExportCodec.encode(export))
        if (wrote) ExportOutcome.Success(export.contentSha256) else ExportOutcome.WriteFailed
    }

    private const val TRUNCATED_HASH_LENGTH = 12
}
