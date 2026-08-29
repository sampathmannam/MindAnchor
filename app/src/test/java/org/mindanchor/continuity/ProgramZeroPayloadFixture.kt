package org.mindanchor.continuity

import org.mindanchor.backup.BackupCodec

/**
 * One fully-populated Program 0 payload — every list non-empty, every
 * nullable field exercised in at least one direction — whose version-1
 * content hash is frozen by [ContinuityHashVersionTest].
 *
 * The freeze is the reason this fixture exists. Program 1 appends fields
 * to [ContinuityPayload], and appending a field changes the JSON that
 * [ContinuityContentHasher] digests even when the new lists are empty. If
 * that change reached the version-1 projection, every encrypted checkpoint
 * a Program 0 build ever wrote would fail its restore verification. Pinning
 * the hash here means that regression fails a unit test rather than a
 * replacement phone.
 */
object ProgramZeroPayloadFixture {

    fun payload(): ContinuityPayload = ContinuityContentHasher.sorted(
        ContinuityPayload(
            journalEntries = listOf(
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
            ),
            contextRows = listOf(
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
            ),
            morningMeasures = listOf(
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
            ),
            notes = listOf(
                NoteDto(id = 7L, body = "A note", createdAt = 500L, updatedAt = 600L, pinned = true, type = null),
            ),
            letters = listOf(
                LetterDto(
                    date = "2026-08-26",
                    body = "A letter",
                    provider = null,
                    model = null,
                    promptTokens = null,
                    completionTokens = null,
                    durationMs = null,
                ),
            ),
            readLetterDates = listOf("2026-08-26"),
            frictionedApps = listOf("com.example.social"),
            alwaysOpenApps = listOf("com.android.dialer"),
            continuityChanges = listOf(
                ContinuityChangeDto(
                    id = "change-1",
                    entityType = "JOURNAL_ENTRY",
                    entityId = "entry-1",
                    operation = "CREATE",
                    occurredAt = 1_000L,
                    acknowledgedSnapshotId = null,
                ),
            ),
            legacyBackupJson = BackupCodec.encode(
                BackupCodec.Backup(
                    savedAt = 1_234L,
                    plan = BackupCodec.Plan(warningSigns = "restless"),
                    contacts = listOf(BackupCodec.Contact(name = "Ana", phone = "5551234567")),
                    pulses = listOf(BackupCodec.Pulse(score = 60, takenAt = 800L)),
                    favorites = listOf("com.android.dialer"),
                ),
            ),
        ),
    )
}
