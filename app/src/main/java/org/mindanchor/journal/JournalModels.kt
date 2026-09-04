package org.mindanchor.journal

import java.time.LocalDate
import java.util.UUID
import org.mindanchor.data.db.JournalContextEntity
import org.mindanchor.data.db.JournalEntryEntity

enum class JournalKind { DAILY, BA, DEAR_MAN, GRATITUDE, EXPRESSIVE_WRITING }
enum class ContextRecordType { FACT, INFERENCE }
enum class ChangeOperation { CREATE, UPDATE, DELETE }

/**
 * A Journal entry, in the author's own words. Nothing here is ever derived
 * or inferred — [create] preserves exactly what the person wrote.
 */
data class JournalEntry(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val localDate: String,
    val title: String,
    val body: String,
    val kind: JournalKind,
    val sourceDeviceId: String,
    val deletedAt: Long?,
) {
    companion object {
        const val MAX_BODY_LENGTH = 20_000

        fun create(
            title: String,
            body: String,
            now: Long,
            localDate: LocalDate,
            sourceDeviceId: String,
            kind: JournalKind = JournalKind.DAILY,
        ): JournalEntry {
            val trimmedTitle = title.trim()
            val trimmedBody = body.trim()
            require(trimmedBody.isNotBlank()) { "Journal entry body must not be blank" }
            require(trimmedBody.length <= MAX_BODY_LENGTH) {
                "Journal entry body exceeds $MAX_BODY_LENGTH characters"
            }
            return JournalEntry(
                id = UUID.randomUUID().toString(),
                createdAt = now,
                updatedAt = now,
                localDate = localDate.toString(),
                title = trimmedTitle,
                body = trimmedBody,
                kind = kind,
                sourceDeviceId = sourceDeviceId,
                deletedAt = null,
            )
        }
    }
}

/** A structural (or, in a future extractor, inferred) fact about a [JournalEntry]. */
data class JournalContext(
    val id: String,
    val entryId: String,
    val recordType: ContextRecordType,
    val key: String,
    val value: String,
    val sourceStart: Int?,
    val sourceEnd: Int?,
    val confidence: Double,
    val extractorVersion: String,
    val createdAt: Long,
)

fun JournalEntry.toEntity(): JournalEntryEntity = JournalEntryEntity(
    id = id,
    createdAt = createdAt,
    updatedAt = updatedAt,
    localDate = localDate,
    title = title,
    body = body,
    kind = kind.name,
    sourceDeviceId = sourceDeviceId,
    deletedAt = deletedAt,
)

fun JournalEntryEntity.toDomain(): JournalEntry = JournalEntry(
    id = id,
    createdAt = createdAt,
    updatedAt = updatedAt,
    localDate = localDate,
    title = title,
    body = body,
    kind = JournalKind.valueOf(kind),
    sourceDeviceId = sourceDeviceId,
    deletedAt = deletedAt,
)

fun JournalContext.toEntity(): JournalContextEntity = JournalContextEntity(
    id = id,
    entryId = entryId,
    recordType = recordType.name,
    key = key,
    value = value,
    sourceStart = sourceStart,
    sourceEnd = sourceEnd,
    confidence = confidence,
    extractorVersion = extractorVersion,
    createdAt = createdAt,
)

fun JournalContextEntity.toDomain(): JournalContext = JournalContext(
    id = id,
    entryId = entryId,
    recordType = ContextRecordType.valueOf(recordType),
    key = key,
    value = value,
    sourceStart = sourceStart,
    sourceEnd = sourceEnd,
    confidence = confidence,
    extractorVersion = extractorVersion,
    createdAt = createdAt,
)
