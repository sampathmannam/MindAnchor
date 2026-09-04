package org.mindanchor.continuity

import org.mindanchor.backup.BackupCodec

/**
 * One fully-populated Program 0 payload whose version-1 content hash is
 * frozen by [ContinuityHashVersionTest].
 *
 * The freeze is the reason this fixture exists. Program 1 appends fields
 * to [ContinuityPayload], and appending a field changes the JSON that
 * [ContinuityContentHasher] digests even when the new lists are empty. If
 * that change reached the version-1 projection, every encrypted checkpoint
 * a Program 0 build ever wrote would fail its restore verification. Pinning
 * the hash here means that regression fails a unit test rather than a
 * replacement phone.
 *
 * Every list holds at least two elements, supplied **out of canonical
 * order**, so the freeze covers `ContinuityContentHasher.sorted`'s sort
 * keys and not only its field set. Every nullable field is exercised in
 * both directions.
 */
object ProgramZeroPayloadFixture {

    fun payload(): ContinuityPayload = ContinuityContentHasher.sorted(
        ContinuityPayload(
            journalEntries = journalEntries(),
            contextRows = contextRows(),
            morningMeasures = morningMeasures(),
            notes = notes(),
            letters = letters(),
            readLetterDates = listOf("2026-08-26", "2026-08-24"),
            frictionedApps = listOf("com.example.social", "com.example.news"),
            alwaysOpenApps = listOf("com.android.dialer", "com.android.mms"),
            continuityChanges = continuityChanges(),
            legacyBackupJson = legacyBackupJson(),
        ),
    )

    private fun journalEntries(): List<JournalEntryDto> = listOf(
        JournalEntryDto(
            id = "entry-2",
            createdAt = 2_000L,
            updatedAt = 2_100L,
            localDate = "2026-08-28",
            title = "",
            body = "Body two",
            kind = "EXPRESSIVE_WRITING",
            sourceDeviceId = "device-b",
            deletedAt = 2_200L,
        ),
        JournalEntryDto(
            id = "entry-1",
            createdAt = 1_000L,
            updatedAt = 1_100L,
            localDate = "2026-08-27",
            title = "Morning",
            body = "Body one",
            kind = "DAILY",
            sourceDeviceId = "device-a",
            deletedAt = null,
        ),
    )

    private fun contextRows(): List<JournalContextDto> = listOf(
        JournalContextDto(
            id = "context-2",
            entryId = "entry-1",
            recordType = "FACT",
            key = "user_title",
            value = "Morning",
            sourceStart = 0,
            sourceEnd = 7,
            confidence = 1.0,
            extractorVersion = "structural-v1",
            createdAt = 1_010L,
        ),
        JournalContextDto(
            id = "context-1",
            entryId = "entry-1",
            recordType = "FACT",
            key = "word_count",
            value = "2",
            sourceStart = null,
            sourceEnd = null,
            confidence = 1.0,
            extractorVersion = "structural-v1",
            createdAt = 1_000L,
        ),
    )

    private fun morningMeasures(): List<MorningMeasureDto> = listOf(
        MorningMeasureDto(
            id = "measure-2",
            localDate = "2026-08-28",
            createdAt = 1_900L,
            updatedAt = 1_950L,
            mood = 5,
            anxiety = 1,
            angerUrge = 2,
            energyFunction = 5,
            sleepQuality = 4,
            instrumentVersion = "morning-v1",
            sourceDeviceId = "device-b",
        ),
        MorningMeasureDto(
            id = "measure-1",
            localDate = "2026-08-27",
            createdAt = 900L,
            updatedAt = 900L,
            mood = 3,
            anxiety = 2,
            angerUrge = 1,
            energyFunction = 4,
            sleepQuality = 3,
            instrumentVersion = "morning-v1",
            sourceDeviceId = "device-a",
        ),
    )

    private fun notes(): List<NoteDto> = listOf(
        NoteDto(id = 9L, body = "Another note", createdAt = 700L, updatedAt = 750L, pinned = false, type = "TASK"),
        NoteDto(id = 7L, body = "A note", createdAt = 500L, updatedAt = 600L, pinned = true, type = null),
    )

    private fun letters(): List<LetterDto> = listOf(
        LetterDto(
            date = "2026-08-26",
            body = "A letter",
            provider = null,
            model = null,
            promptTokens = null,
            completionTokens = null,
            durationMs = null,
        ),
        LetterDto(
            date = "2026-08-24",
            body = "An earlier letter",
            provider = "openrouter",
            model = "a-model",
            promptTokens = 120,
            completionTokens = 240,
            durationMs = 1_500L,
        ),
    )

    private fun continuityChanges(): List<ContinuityChangeDto> = listOf(
        ContinuityChangeDto(
            id = "change-2",
            entityType = "MORNING_MEASURE",
            entityId = "measure-1",
            operation = "UPDATE",
            occurredAt = 1_500L,
            acknowledgedSnapshotId = "snapshot-a",
        ),
        ContinuityChangeDto(
            id = "change-1",
            entityType = "JOURNAL_ENTRY",
            entityId = "entry-1",
            operation = "CREATE",
            occurredAt = 1_000L,
            acknowledgedSnapshotId = null,
        ),
    )

    private fun legacyBackupJson(): String = BackupCodec.encode(
        BackupCodec.Backup(
            savedAt = 1_234L,
            plan = BackupCodec.Plan(warningSigns = "restless"),
            contacts = listOf(BackupCodec.Contact(name = "Ana", phone = "5551234567")),
            pulses = listOf(BackupCodec.Pulse(score = 60, takenAt = 800L)),
            favorites = listOf("com.android.dialer"),
        ),
    )
}
