# MindAnchor Program 0: Production Spine and Continuity Proof Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan.

**Goal:** Ship the smallest dependable MindAnchor release that can create a text Journal entry and a morning research measure, preserve authorship separately from derived context, survive process death and offline use, back up to Google Drive with a recovery key, and restore an identical verified record on a replacement phone.

**Architecture:** Keep the launcher as the stable shell and isolate Program 0 in a separate Journal activity plus a continuity subsystem. Store Journal, context, morning measures, and pending continuity changes transactionally in Room. Build one canonical snapshot from those records plus the existing logical backup, Notes, Letters, and protected-app settings. Encrypt snapshots with a portable user-held recovery key, upload a verified latest checkpoint after important changes, create a versioned nightly snapshot, and restore through a staged, idempotent, resumable coordinator. All cloud work is optional and network-constrained; all writing and reading remain available offline.

**Tech Stack:** Kotlin 2.0.21, Android/Compose Material 3, Room 2.6.1, Preferences DataStore 1.1.1, WorkManager 2.9.1, kotlinx.serialization 1.7.3, OkHttp 4.12.0, Google Sign-In with `drive.file`, AES-256-GCM, JUnit 4, Robolectric, AndroidX instrumentation, MockWebServer, GitHub Actions.

**Global Constraints:**

- Program 0 adds no diagnosis, treatment claim, autonomous intervention, app blocking, wearable interpretation, or LLM-authored mental-health guidance.
- Program 0 context extraction is structural only: entry kind, local date, word count, and user-authored title. It produces no semantic or clinical inference. This is useful provenance and proves separation without inventing an unsupported classifier.
- Program 0 Journal entries are text-only. Photos, audio, activity attachments, calendar browsing, and semantic Patterns are deferred until replacement-phone restoration is proven on real hardware.
- `JournalEntry` content and derived `JournalContext` rows are separate. A context recomputation must never modify the entry body.
- Optional context extraction must never block or roll back user-authored Journal text. Commit the original entry and its pending backup change first; derive context afterward and fail soft.
- Quick Notes remain quick capture; Journal remains deliberate reflection. Do not merge their screens or storage models.
- “Incremental sync” in Program 0 means a new full encrypted checkpoint is requested after every important durable change. Differential remote event logs are deferred because current data volume is small and whole checkpoints are easier to verify and restore correctly.
- Every successful upload must be downloaded immediately and byte-verified before local changes are acknowledged. Upload success alone is not backup success.
- The recovery key must work on a different phone. A device-Keystore-only key is not acceptable for continuity.
- Official `v*` releases must be signed by one stable release certificate. A tag workflow must fail rather than silently publish a debug-signed APK.
- Room migrations are forward-only. Never add `fallbackToDestructiveMigration`; repair a bad release with a higher-version migration or restore a verified snapshot into a clean install.
- Phone, SMS, WhatsApp, and user-designated always-open applications are data that must survive restore. Program 0 does not change their availability.
- Existing user data must be preserved. The old protective-writing `JournalStore` is imported once into the new Journal; the legacy Drive files remain untouched for compatibility.
- No cloud request may run during ordinary offline startup. WorkManager owns all automatic network work and applies a connected-network constraint.
- Program 0 replacement restore targets a fresh MindAnchor data profile. If meaningful local user data already exists, stop and require a local export/backup; do not silently merge two histories or delete either one.
- No task may be called complete solely because unit tests pass. Program 0 exits only after repeated physical replacement-phone restores produce matching content hashes.

## Program 0 release boundary

The releasable vertical loop is:

1. Open Journal from the launcher.
2. Write and save a text entry; close or force-stop during a draft and recover it.
3. Store structural context separately from the original entry.
4. Complete the five-item morning measure.
5. Continue using both surfaces without a network connection.
6. Create an encrypted Drive checkpoint after each save and a versioned nightly snapshot.
7. Export a structured research record.
8. Install MindAnchor on another phone, sign in to the same Google account, enter the recovery key, and restore.
9. Export again and verify the same canonical content hash.

Program 0 intentionally does not include wearable ingestion, state estimation, protocol selection, autonomous control, rich Journal attachments, or N-of-1 analysis. Those remain in later programs from the approved design.

---

### Task 1: Establish a green baseline and pin the continuity contract

**Files:**

- Create: `docs/backup/program-0-data-inventory.md`
- Create: `app/src/main/java/org/mindanchor/continuity/ContinuityContract.kt`
- Create: `app/src/test/java/org/mindanchor/continuity/ContinuityContractTest.kt`
- Test: existing full JVM suite

**Step 1: Run the untouched baseline**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: PASS. If it fails, record the pre-existing failure in the task report and stop; do not mix an unrelated fix into Program 0.

**Step 2: Write the failing contract test**

Create `ContinuityContractTest.kt` with exact file-name and version contracts so backup/restore code cannot drift silently:

```kotlin
package org.mindanchor.continuity

import org.junit.Assert.assertEquals
import org.junit.Test

class ContinuityContractTest {
    @Test
    fun `program zero wire constants stay stable`() {
        assertEquals(1, ContinuityContract.SNAPSHOT_FORMAT_VERSION)
        assertEquals(1, ContinuityContract.ENVELOPE_FORMAT_VERSION)
        assertEquals("MindAnchor-Continuity-Latest.mab", ContinuityContract.LATEST_FILE_NAME)
        assertEquals("mindanchor-research-v1", ContinuityContract.RESEARCH_DICTIONARY_VERSION)
    }
}
```

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*ContinuityContractTest'
```

Expected: FAIL because `ContinuityContract` does not exist.

**Step 3: Write the data inventory before code**

Document every existing store under one of three headings:

- Protected in Program 0: Room safety plan/contacts/pulses, launcher favorites/hidden/renames, EMA moments, measured/inferred readings, corpus additions, Quick Notes, Letters/read dates, new Journal/context/morning measures, frictioned apps, and always-open apps.
- Deliberately device-local: Google OAuth token, current device identifier, Android permission grants, wearable pairing credentials, imported model binaries, caches, current WorkManager jobs.
- Deferred with an explicit reason: nonessential UI counters and temporary rate-limit state.

Include the current source path and logical import/export method for every protected store. This inventory is the review checklist for Task 7; a store may not be silently omitted.

**Step 4: Add the minimum stable contract and make the test pass**

Create:

```kotlin
package org.mindanchor.continuity

object ContinuityContract {
    const val SNAPSHOT_FORMAT_VERSION = 1
    const val ENVELOPE_FORMAT_VERSION = 1
    const val LATEST_FILE_NAME = "MindAnchor-Continuity-Latest.mab"
    const val RESEARCH_DICTIONARY_VERSION = "mindanchor-research-v1"
}
```

Tasks 7–9 must reference these constants rather than restating literal values.

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*ContinuityContractTest'
```

Expected: PASS.

**Step 5: Commit**

```bash
git add docs/backup/program-0-data-inventory.md app/src/main/java/org/mindanchor/continuity/ContinuityContract.kt app/src/test/java/org/mindanchor/continuity/ContinuityContractTest.kt
git commit -m "test: pin Program 0 continuity contract"
```

---

### Task 2: Repair the Room migration chain and add the Program 0 ledger

**Files:**

- Create: `app/src/main/java/org/mindanchor/data/db/JournalEntities.kt`
- Create: `app/src/main/java/org/mindanchor/data/db/JournalDao.kt`
- Modify: `app/src/main/java/org/mindanchor/data/db/AnchorDatabase.kt`
- Modify: `app/src/androidTest/java/org/mindanchor/data/db/MigrationTest.kt`
- Modify: `app/build.gradle.kts`
- Create after schema generation: `app/schemas/org.mindanchor.data.db.AnchorDatabase/6.json`

**Step 1: Write failing migration and DAO tests**

Add tests that create exact historical schemas and open them through `AnchorDatabase.migrations()`:

```kotlin
@Test
fun aVersion3DatabaseUpgradesThroughTheMissingTierMigration() = runBlocking {
    createVersion3()
    openCurrent().use { db ->
        assertTrue(db.heldNotifications().journal().first().isEmpty())
        assertTrue(db.journal().entries().first().isEmpty())
    }
}

@Test
fun aVersion4DatabaseDropsTierAndCreatesProgramZeroTables() = runBlocking {
    createVersion4WithTier()
    openCurrent().use { db ->
        db.journal().insertEntry(
            JournalEntryEntity(
                id = "entry-1",
                createdAt = 1_000L,
                updatedAt = 1_000L,
                localDate = "2026-08-28",
                title = "A day",
                body = "Original words",
                kind = "DAILY",
                sourceDeviceId = "device-a",
                deletedAt = null,
            ),
        )
        assertEquals("Original words", db.journal().entry("entry-1")?.body)
    }
}

@Test
fun aVersion5DatabaseKeepsExistingRowsWhenProgramZeroTablesAreAdded() = runBlocking {
    createVersion5WithNotification()
    openCurrent().use { db ->
        assertEquals(1, db.heldNotifications().journal().first().size)
        assertTrue(db.journal().entries().first().isEmpty())
    }
}
```

Add DAO tests for one-entry-per-id, one morning measure per local date, separate context rows, and pending-change acknowledgement.

Run:

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: FAIL because the entities and DAO do not exist.

**Step 2: Add exact Room entities**

Create these entities in `JournalEntities.kt`:

```kotlin
@Entity(tableName = "journal_entries", indices = [Index("localDate"), Index("createdAt")])
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val localDate: String,
    val title: String,
    val body: String,
    val kind: String,
    val sourceDeviceId: String,
    val deletedAt: Long?,
)

@Entity(
    tableName = "journal_context",
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId"), Index("recordType")],
)
data class JournalContextEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val recordType: String,
    val key: String,
    val value: String,
    val sourceStart: Int?,
    val sourceEnd: Int?,
    val confidence: Double,
    val extractorVersion: String,
    val createdAt: Long,
)

@Entity(tableName = "morning_measures", indices = [Index(value = ["localDate"], unique = true)])
data class MorningMeasureEntity(
    @PrimaryKey val id: String,
    val localDate: String,
    val createdAt: Long,
    val updatedAt: Long,
    val mood: Int,
    val anxiety: Int,
    val angerUrge: Int,
    val energyFunction: Int,
    val sleepQuality: Int,
    val instrumentVersion: String,
    val sourceDeviceId: String,
)

@Entity(tableName = "continuity_changes", indices = [Index("occurredAt"), Index("acknowledgedSnapshotId")])
data class ContinuityChangeEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val occurredAt: Long,
    val acknowledgedSnapshotId: String?,
)
```

Use strings on disk, not Room enum converters, so adding an enum in a later app version does not make an old row unreadable.

**Step 3: Add the DAO and transaction primitives**

`JournalDao` must expose:

```kotlin
@Insert(onConflict = OnConflictStrategy.ABORT)
suspend fun insertEntry(entry: JournalEntryEntity)

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertEntries(entries: List<JournalEntryEntity>)

@Query("SELECT * FROM journal_entries WHERE deletedAt IS NULL ORDER BY createdAt DESC")
fun entries(): Flow<List<JournalEntryEntity>>

@Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
suspend fun entry(id: String): JournalEntryEntity?

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertContext(rows: List<JournalContextEntity>)

@Query("SELECT * FROM journal_context ORDER BY createdAt, id")
suspend fun allContext(): List<JournalContextEntity>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertMorningMeasure(measure: MorningMeasureEntity)

@Query("SELECT * FROM morning_measures ORDER BY localDate DESC")
fun morningMeasures(): Flow<List<MorningMeasureEntity>>

@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertChange(change: ContinuityChangeEntity)

@Query("SELECT * FROM continuity_changes WHERE acknowledgedSnapshotId IS NULL ORDER BY occurredAt, id")
suspend fun pendingChanges(): List<ContinuityChangeEntity>

@Query("UPDATE continuity_changes SET acknowledgedSnapshotId = :snapshotId WHERE acknowledgedSnapshotId IS NULL")
suspend fun acknowledgePending(snapshotId: String)
```

Add one-shot sorted queries for snapshot export rather than calling `.first()` on UI flows.

**Step 4: Restore the missing 3→4 migration and add 5→6**

Reintroduce the historical migration exactly as it shipped:

```kotlin
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE held_notifications " +
                "ADD COLUMN tier TEXT NOT NULL DEFAULT 'MACHINE'",
        )
    }
}
```

Add `MIGRATION_5_6` with SQL matching the four entities and their indices exactly. Set database version to 6, register the four entities, add `abstract fun journal(): JournalDao`, and return all five migrations in order:

```kotlin
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS journal_entries (" +
                "id TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, " +
                "localDate TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, " +
                "kind TEXT NOT NULL, sourceDeviceId TEXT NOT NULL, deletedAt INTEGER, " +
                "PRIMARY KEY(id))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_journal_entries_localDate ON journal_entries(localDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_journal_entries_createdAt ON journal_entries(createdAt)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS journal_context (" +
                "id TEXT NOT NULL, entryId TEXT NOT NULL, recordType TEXT NOT NULL, " +
                "`key` TEXT NOT NULL, value TEXT NOT NULL, sourceStart INTEGER, sourceEnd INTEGER, " +
                "confidence REAL NOT NULL, extractorVersion TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                "PRIMARY KEY(id), FOREIGN KEY(entryId) REFERENCES journal_entries(id) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_journal_context_entryId ON journal_context(entryId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_journal_context_recordType ON journal_context(recordType)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS morning_measures (" +
                "id TEXT NOT NULL, localDate TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                "updatedAt INTEGER NOT NULL, mood INTEGER NOT NULL, anxiety INTEGER NOT NULL, " +
                "angerUrge INTEGER NOT NULL, energyFunction INTEGER NOT NULL, sleepQuality INTEGER NOT NULL, " +
                "instrumentVersion TEXT NOT NULL, sourceDeviceId TEXT NOT NULL, PRIMARY KEY(id))",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_morning_measures_localDate " +
                "ON morning_measures(localDate)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS continuity_changes (" +
                "id TEXT NOT NULL, entityType TEXT NOT NULL, entityId TEXT NOT NULL, " +
                "operation TEXT NOT NULL, occurredAt INTEGER NOT NULL, " +
                "acknowledgedSnapshotId TEXT, PRIMARY KEY(id))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_continuity_changes_occurredAt ON continuity_changes(occurredAt)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_continuity_changes_acknowledgedSnapshotId " +
                "ON continuity_changes(acknowledgedSnapshotId)",
        )
    }
}
```

Then register the complete chain:

```kotlin
fun migrations(): Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
)
```

**Step 5: Enable committed Room schemas**

In `app/build.gradle.kts`:

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

Set `exportSchema = true`, build once, and commit the generated version-6 schema. Never hand-edit the schema JSON.

**Step 6: Run migration tests**

Run:

```bash
./gradlew :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

Expected: all v1, v3, v4, v5, and fresh-install migration tests PASS.

**Step 7: Commit**

```bash
git add app/src/main/java/org/mindanchor/data/db app/src/androidTest/java/org/mindanchor/data/db/MigrationTest.kt app/build.gradle.kts app/schemas
git commit -m "feat: add Program 0 continuity ledger"
```

---

### Task 3: Preserve Journal authorship before deriving structural context

**Files:**

- Create: `app/src/main/java/org/mindanchor/journal/JournalModels.kt`
- Create: `app/src/main/java/org/mindanchor/journal/StructuralContextExtractor.kt`
- Create: `app/src/main/java/org/mindanchor/journal/DeviceIdentityStore.kt`
- Create: `app/src/main/java/org/mindanchor/journal/JournalRepository.kt`
- Create: `app/src/test/java/org/mindanchor/journal/StructuralContextExtractorTest.kt`
- Create: `app/src/androidTest/java/org/mindanchor/journal/JournalRepositoryTest.kt`

**Step 1: Write failing extractor tests**

```kotlin
class StructuralContextExtractorTest {
    private val extractor = StructuralContextExtractor()

    @Test
    fun `extracts only structural facts`() {
        val entry = JournalEntry(
            id = "e1",
            createdAt = 1_000L,
            updatedAt = 1_000L,
            localDate = "2026-08-28",
            title = "Difficult shift",
            body = "I had an argument. I took a walk afterward.",
            kind = JournalKind.DAILY,
            sourceDeviceId = "d1",
            deletedAt = null,
        )

        val facts = extractor.extract(entry, now = 2_000L)

        assertEquals(setOf("entry_kind", "local_date", "word_count", "user_title"), facts.map { it.key }.toSet())
        assertTrue(facts.all { it.recordType == ContextRecordType.FACT })
        assertTrue(facts.none { it.value.contains("anxiety", ignoreCase = true) })
        assertTrue(facts.all { it.extractorVersion == "structural-v1" })
    }
}
```

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*StructuralContextExtractorTest'
```

Expected: FAIL.

**Step 2: Add constrained domain models**

Use exact stable enums:

```kotlin
enum class JournalKind { DAILY, BA, DEAR_MAN, GRATITUDE, EXPRESSIVE_WRITING }
enum class ContextRecordType { FACT, INFERENCE }
enum class ChangeOperation { CREATE, UPDATE, DELETE }
```

`JournalEntry.create` must trim title/body, reject a blank body, cap body at 20,000 characters, preserve timestamps, and generate a UUID. Do not parse mental-health state from body text.

**Step 3: Implement the structural extractor**

The extractor returns only:

- `entry_kind`: enum name
- `local_date`: stored ISO local date
- `word_count`: whitespace-token count
- `user_title`: only when the user supplied a title

Every row has `recordType=FACT`, `confidence=1.0`, `sourceStart=null`, `sourceEnd=null`, and `extractorVersion="structural-v1"`. Do not add sentiment, diagnosis, relationship intent, risk labels, or inferred emotions.

**Step 4: Implement stable device identity**

`DeviceIdentityStore` uses a dedicated DataStore named `program_zero_device` and generates one UUID on first read. The current-device ID is deliberately not restored; entry-level source IDs are restored as data.

**Step 5: Write the failing durability tests**

The Android test must use an in-memory Room database and prove that entry plus pending backup change commit together. Force the extractor to throw in a second test and assert that the original entry and pending change still exist while context is empty. Derived processing must never cost the user’s words.

Run:

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: FAIL because `JournalRepository` does not exist.

**Step 6: Implement `JournalRepository.create`**

Build the entry, commit the original plus its pending backup change first, then derive structural context in a fail-soft second transaction:

```kotlin
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
    return entry
}
```

Context IDs must be deterministic from `entryId|recordType|key|extractorVersion` so a retry replaces the same fact instead of duplicating it. Expose flows for entries and context. Add `retryContext(entryId)` for a failed or missing extraction. Add `delete` as a tombstone update plus a `DELETE` continuity change; never physically delete Journal content in Program 0.

**Step 7: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests '*StructuralContextExtractorTest'
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.journal.JournalRepositoryTest
```

Expected: PASS.

**Step 8: Commit**

```bash
git add app/src/main/java/org/mindanchor/journal app/src/test/java/org/mindanchor/journal app/src/androidTest/java/org/mindanchor/journal
git commit -m "feat: preserve Journal authorship and context"
```

---

### Task 4: Import the old protective-writing Journal and persist drafts

**Files:**

- Modify: `app/src/main/java/org/mindanchor/letters/JournalStore.kt`
- Create: `app/src/main/java/org/mindanchor/journal/JournalLegacyImporter.kt`
- Create: `app/src/main/java/org/mindanchor/journal/JournalMigrationPrefs.kt`
- Create: `app/src/main/java/org/mindanchor/journal/JournalDraftStore.kt`
- Create: `app/src/test/java/org/mindanchor/letters/JournalStoreAllEntriesTest.kt`
- Create: `app/src/androidTest/java/org/mindanchor/journal/JournalLegacyImporterTest.kt`
- Create: `app/src/test/java/org/mindanchor/journal/JournalDraftStoreTest.kt`

**Step 1: Write failing legacy enumeration tests**

Save BA and gratitude entries on dates more than 365 days apart, call the new `allEntries()`, and assert both return. The existing `entries` flow only probes the latest 365 days, so this test must fail first.

**Step 2: Add `JournalStore.allEntries()`**

Read `Preferences.asMap()`, parse keys with the exact `<kind-tag>:<ISO-date>` format, reject malformed keys, and sort by date then kind. Do not change existing `save` or `readOne` behavior.

**Step 3: Write failing idempotent import tests**

The importer test must prove:

- each old entry becomes one Room Journal row;
- `Kind` maps to the same `JournalKind` name;
- a deterministic UUID derived from `legacy:<kind>:<date>` prevents duplicates;
- context facts are generated separately;
- the completion flag is written only after the Room transaction succeeds;
- running the importer twice does not duplicate rows.

**Step 4: Implement the one-time importer**

Use `UUID.nameUUIDFromBytes("legacy:${kind.tag}:$date".encodeToByteArray())`, local noon for the historical timestamp, source device `legacy-datastore`, and `INSERT OR IGNORE`. Do not delete the old DataStore after import; it remains a rollback copy.

**Step 5: Write failing draft recovery tests**

Cover blank initial state, save/read round trip, clear-after-commit, and a draft containing tabs, newlines, and Unicode.

**Step 6: Implement `JournalDraftStore`**

Use a dedicated `journal_draft` DataStore with separate `title`, `body`, and `updated_at` keys. Save on each accepted UI change, cap with the same Journal limits, and clear only after `JournalRepository.create` returns successfully. A failed save must leave the draft intact.

**Step 7: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests '*JournalStoreAllEntriesTest' --tests '*JournalDraftStoreTest'
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.journal.JournalLegacyImporterTest
```

Expected: PASS.

**Step 8: Commit**

```bash
git add app/src/main/java/org/mindanchor/letters/JournalStore.kt app/src/main/java/org/mindanchor/journal app/src/test/java/org/mindanchor/letters app/src/test/java/org/mindanchor/journal app/src/androidTest/java/org/mindanchor/journal
git commit -m "feat: migrate legacy writing and recover drafts"
```

---

### Task 5: Add the five-item morning research measure

**Files:**

- Create: `app/src/main/java/org/mindanchor/research/MorningMeasure.kt`
- Create: `app/src/main/java/org/mindanchor/research/MorningMeasureRepository.kt`
- Create: `app/src/main/java/org/mindanchor/journal/MorningMeasureCard.kt`
- Create: `app/src/test/java/org/mindanchor/research/MorningMeasureTest.kt`
- Create: `app/src/androidTest/java/org/mindanchor/research/MorningMeasureRepositoryTest.kt`
- Create: `app/src/androidTest/java/org/mindanchor/journal/MorningMeasureCardTest.kt`

**Step 1: Write failing domain tests**

```kotlin
class MorningMeasureTest {
    @Test
    fun `all five values must be in one to five`() {
        assertThrows(IllegalArgumentException::class.java) {
            MorningMeasure.create(
                localDate = LocalDate.parse("2026-08-28"),
                now = 1_000L,
                mood = 0,
                anxiety = 3,
                angerUrge = 3,
                energyFunction = 3,
                sleepQuality = 3,
                sourceDeviceId = "d1",
            )
        }
    }

    @Test
    fun `instrument version is frozen`() {
        assertEquals("morning-v1", MorningMeasure.INSTRUMENT_VERSION)
    }
}
```

Run and expect FAIL.

**Step 2: Implement the model and repository**

Use one 1–5 value for mood, anxiety/tension, anger or urge to react, energy/ability to function, and perceived sleep quality. The repository upserts by local date so an accidental duplicate tap cannot create two daily records. It preserves the original `createdAt`, updates `updatedAt`, and inserts a `MORNING_MEASURE` continuity change in the same Room transaction.

The UI copy must say this is a personal research measure, not a diagnosis or clinical score. Do not add thresholds, red/green interpretations, or “good/bad” totals.

**Step 3: Implement the under-30-second card**

Use five compact 1–5 segmented rows with plain-language endpoints and one Save button. Place it in Journal Today, not in an autonomous protocol. If today already has a record, show the saved values and an explicit Edit action.

**Step 4: Test repository and UI**

The repository test proves measure + pending change atomicity and one-row-per-date behavior. The Compose test selects all five values, saves once, and confirms the callback receives the exact values.

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*MorningMeasureTest'
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=org.mindanchor.research
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.journal.MorningMeasureCardTest
```

Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/org/mindanchor/research app/src/main/java/org/mindanchor/journal/MorningMeasureCard.kt app/src/test/java/org/mindanchor/research app/src/androidTest/java/org/mindanchor/research app/src/androidTest/java/org/mindanchor/journal/MorningMeasureCardTest.kt
git commit -m "feat: add morning research measure"
```

---

### Task 6: Build the Journal Today, Entries, and Patterns experience

**Files:**

- Create: `app/src/main/java/org/mindanchor/journal/JournalActivity.kt`
- Create: `app/src/main/java/org/mindanchor/journal/JournalViewModel.kt`
- Create: `app/src/main/java/org/mindanchor/journal/JournalScreen.kt`
- Create: `app/src/main/java/org/mindanchor/journal/JournalToday.kt`
- Create: `app/src/main/java/org/mindanchor/journal/JournalEntries.kt`
- Create: `app/src/main/java/org/mindanchor/journal/JournalPatterns.kt`
- Modify: `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/androidTest/java/org/mindanchor/journal/JournalScreenTest.kt`
- Create: `app/src/androidTest/java/org/mindanchor/journal/JournalDraftRecoveryTest.kt`

**Step 1: Write failing navigation and draft tests**

Compose tests must verify:

- Today, Entries, and Patterns are visible as three stable destinations;
- Today displays the current date, title, body, morning measure, and Save;
- saving clears the draft only after success;
- a failed repository save leaves the draft visible;
- activity recreation restores the title and body;
- Entries lists saved originals chronologically and searches body/title case-insensitively;
- Patterns never displays a context row as original writing and never labels structural data as diagnosis.

Run:

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: FAIL because the screen does not exist.

**Step 2: Implement a separate activity**

Use a separate `JournalActivity` instead of adding another branch to the already-large `LauncherRoot`. This contains a Journal crash to its own activity and preserves the launcher as the dependable escape surface.

Register:

```xml
<activity
    android:name=".journal.JournalActivity"
    android:exported="false"
    android:stateNotNeeded="false"
    android:theme="@style/Theme.MindAnchor" />
```

The activity uses one `JournalViewModel`, application-context repositories, and the existing `MindAnchorTheme`. Back closes the activity and returns to the launcher.

**Step 3: Implement the three destinations**

- Today: Apple Journal-inspired calm date header and writing card, but use MindAnchor typography/colors and do not copy Apple assets. Persist drafts as the user types. Save original text first; show “Context prepared” only after the Room transaction succeeds.
- Entries: chronological text cards, search, and entry detail. Program 0 does not add calendar, media, sharing, or AI summaries.
- Patterns: show transparent counts only—days written, words written, morning-measure history, and the structural fact keys. Render two headings, “From your writing” and “Inferences.” The inference section says “No inferences are created in Program 0.”

Do not add streaks, rewards, distress coloring, or judgmental labels.

**Step 4: Wire the launcher affordance**

Add an `onOpenJournal` callback beside the existing Notes and Letter affordances. The callback starts `JournalActivity` with `runCatching`, matching the current Notes activity pattern. Do not reorder Phone, SMS, WhatsApp, favorites, drawer, or settings.

**Step 5: Run UI and recreation tests**

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=org.mindanchor.journal
```

Expected: PASS.

**Step 6: Manual force-stop check**

On an emulator:

1. Open Journal and type a multi-line unsaved draft.
2. Run `adb shell am force-stop org.mindanchor`.
3. Relaunch MindAnchor and open Journal.
4. Confirm the complete draft returns.
5. Save, force-stop again, and confirm the draft is gone while the saved entry remains.

**Step 7: Commit**

```bash
git add app/src/main/java/org/mindanchor/journal app/src/main/java/org/mindanchor/launcher/HomeScreen.kt app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml app/src/androidTest/java/org/mindanchor/journal
git commit -m "feat: add dependable Journal experience"
```

---

### Task 7: Define one canonical continuity snapshot and research export

**Files:**

- Create: `app/src/main/java/org/mindanchor/continuity/ContinuitySnapshot.kt`
- Create: `app/src/main/java/org/mindanchor/continuity/ContinuitySnapshotCodec.kt`
- Create: `app/src/main/java/org/mindanchor/continuity/ContinuitySnapshotRepository.kt`
- Create: `app/src/main/java/org/mindanchor/continuity/ContinuityContentHasher.kt`
- Create: `app/src/main/java/org/mindanchor/continuity/ResearchExport.kt`
- Create: `app/src/main/java/org/mindanchor/continuity/ResearchExportCodec.kt`
- Modify: `app/src/main/java/org/mindanchor/data/NotesPrefs.kt`
- Modify: `app/src/main/java/org/mindanchor/letters/LetterStore.kt`
- Modify: `app/src/main/java/org/mindanchor/data/FrictionPrefs.kt`
- Create: `app/src/test/java/org/mindanchor/continuity/ContinuitySnapshotCodecTest.kt`
- Create: `app/src/androidTest/java/org/mindanchor/continuity/ContinuitySnapshotRepositoryTest.kt`
- Create: `app/src/test/java/org/mindanchor/continuity/ResearchExportCodecTest.kt`

**Step 1: Write failing canonical-codec tests**

Tests must prove:

- capture → encode → decode preserves every field;
- list order does not change `contentSha256` because repository capture sorts every collection;
- one changed Journal character changes the hash;
- an unknown `formatVersion` is rejected with a typed error;
- corrupt JSON is rejected without throwing into the launcher;
- export JSON keeps original entries, facts, and inferences in separate arrays.

Run and expect FAIL.

**Step 2: Add exact serializable snapshot types**

The top-level type is:

```kotlin
@Serializable
data class ContinuitySnapshot(
    val formatVersion: Int,
    val snapshotId: String,
    val createdAt: Long,
    val appVersionCode: Int,
    val appVersionName: String,
    val sourceDeviceId: String,
    val payload: ContinuityPayload,
    val contentSha256: String,
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = ContinuityContract.SNAPSHOT_FORMAT_VERSION
    }
}
```

`ContinuityPayload` contains exact DTO lists for Journal entries, context rows, morning measures, Notes, Letters, read-letter dates, frictioned apps, always-open apps, continuity changes, and one `legacyBackupJson` string produced by the existing `BackupRepository.export(now)`. The legacy JSON carries the safety plan, contacts, WHO-5 pulses, launcher favorites/hidden/renames, EMA moments, measured and inferred readings, and corpus additions without duplicating those codecs.

All DTOs use primitive/string fields. Do not annotate existing domain models solely for backup.

**Step 3: Canonicalize before hashing**

`ContinuityContentHasher` must:

- sort Journal/context/measure/change DTOs by stable ID;
- sort Notes by ID;
- sort Letters and read dates by ISO date;
- sort package lists;
- normalize `legacyBackupJson` by decoding it and re-encoding with `savedAt=0`;
- exclude snapshot ID, creation time, current source device ID, and remote acknowledgement IDs from the content hash.

Use lowercase SHA-256 hex. The same logical data restored on a new phone must produce the same hash.

**Step 4: Add idempotent merge APIs**

Add:

```kotlin
suspend fun NotesPrefs.mergeRestored(incoming: List<Note>)
suspend fun LetterStore.mergeRestored(incoming: List<Letter>, incomingReadDates: Set<LocalDate>)
suspend fun FrictionPrefs.replaceFlagged(packageNames: Set<String>)
suspend fun FrictionPrefs.replaceAlwaysOpen(packageNames: Set<String>)
```

Notes deduplicate by ID and keep the larger `updatedAt`. Letters deduplicate by date and keep the incoming record only when no local record exists during a clean replacement-phone restore; a nonempty local record wins in additive restore. Protected/flagged package replacements remove blank values.

**Step 5: Implement snapshot capture**

`ContinuitySnapshotRepository.capture(now)` reads Room one-shot queries and the current DataStore snapshots on `Dispatchers.IO`, sorts them, obtains the legacy backup JSON, computes `contentSha256`, and returns the immutable snapshot. It performs no network call.

**Step 6: Implement the structured research export**

`ResearchExport` contains:

```kotlin
@Serializable
data class ResearchExport(
    val dataDictionaryVersion: String = DATA_DICTIONARY_VERSION,
    val exportedAt: Long,
    val appVersionCode: Int,
    val appVersionName: String,
    val contentSha256: String,
    val journalEntries: List<JournalEntryDto>,
    val contextFacts: List<JournalContextDto>,
    val contextInferences: List<JournalContextDto>,
    val morningMeasures: List<MorningMeasureDto>,
) {
    companion object {
        const val DATA_DICTIONARY_VERSION = ContinuityContract.RESEARCH_DICTIONARY_VERSION
    }
}
```

The export’s `contentSha256` is computed over the canonical research payload only: Journal entries, separated context facts/inferences, and morning measures. It excludes export time and app/device metadata, so Device A and Device B research exports can be compared directly. The full continuity snapshot retains its separate whole-profile content hash.

The export is explicit plaintext initiated by the user through Android’s document picker. It must show a privacy warning before writing. The cloud backup never uses this plaintext export.

**Step 7: Verify inventory coverage**

Update `docs/backup/program-0-data-inventory.md` with the exact snapshot DTO field that protects each store. If a protected row has no destination, stop and add it before continuing.

**Step 8: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests '*ContinuitySnapshotCodecTest' --tests '*ResearchExportCodecTest'
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.continuity.ContinuitySnapshotRepositoryTest
```

Expected: PASS.

**Step 9: Commit**

```bash
git add app/src/main/java/org/mindanchor/continuity app/src/main/java/org/mindanchor/data/NotesPrefs.kt app/src/main/java/org/mindanchor/letters/LetterStore.kt app/src/main/java/org/mindanchor/data/FrictionPrefs.kt app/src/test/java/org/mindanchor/continuity app/src/androidTest/java/org/mindanchor/continuity docs/backup/program-0-data-inventory.md
git commit -m "feat: define canonical continuity snapshot"
```

---

### Task 8: Add a portable recovery key and authenticated backup envelope

**Files:**

- Create: `app/src/main/java/org/mindanchor/continuity/crypto/RecoveryKey.kt`
- Create: `app/src/main/java/org/mindanchor/continuity/crypto/RecoveryKeyCodec.kt`
- Create: `app/src/main/java/org/mindanchor/continuity/crypto/RecoveryKeyStore.kt`
- Create: `app/src/main/java/org/mindanchor/continuity/crypto/BackupEnvelope.kt`
- Create: `app/src/main/java/org/mindanchor/continuity/crypto/BackupEnvelopeCodec.kt`
- Create: `app/src/test/java/org/mindanchor/continuity/crypto/RecoveryKeyCodecTest.kt`
- Create: `app/src/test/java/org/mindanchor/continuity/crypto/BackupEnvelopeCodecTest.kt`
- Create: `app/src/test/java/org/mindanchor/continuity/crypto/RecoveryKeyStoreTest.kt`

**Step 1: Write failing recovery-key tests**

Cover generation, formatting, normalization, checksum rejection, one-character corruption, stable key ID, verified-state round trip, and clearing the local copy.

The human form is exactly:

```text
MA1-xxxxxx-xxxxxx-xxxxxx-xxxxxx-xxxxxx-xxxxxx-xxxxxx-xxxxxx
```

The payload is 32 random bytes plus the first four SHA-256 checksum bytes, base64url without padding, grouped for transcription. The prefix and checksum are format/integrity controls, not a password strength claim.

**Step 2: Implement `RecoveryKeyCodec`**

Use `SecureRandom` in production and an injected byte source in tests. Decode must reject the wrong prefix, alphabet, byte count, or checksum. Derive `keyId` from the first eight bytes of SHA-256(key) as lowercase hex.

**Step 3: Implement local protected storage**

`RecoveryKeyStore` stores the decoded 32-byte key in `EncryptedSharedPreferences` under the existing Android Keystore-backed `MasterKey`. It stores a separate verified Boolean. Provide a constructor accepting ordinary `SharedPreferences` for Robolectric tests, matching the existing `TokenStore` pattern.

The key is considered verified only after the user re-enters the full generated key. Copying it is not verification.

**Step 4: Write failing envelope tests**

Prove:

- encrypt/decrypt round trip;
- wrong recovery key returns `WrongKey`;
- one-byte IV/ciphertext/tag modification returns `Corrupt`;
- plaintext snapshot hash mismatch returns `Corrupt`;
- format version mismatch returns `UnsupportedVersion`;
- no Journal text appears in the envelope JSON.

**Step 5: Implement the envelope**

Use a serializable JSON envelope:

```kotlin
@Serializable
data class BackupEnvelope(
    val formatVersion: Int,
    val keyId: String,
    val createdAt: Long,
    val ivBase64: String,
    val ciphertextBase64: String,
    val plaintextSha256: String,
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = ContinuityContract.ENVELOPE_FORMAT_VERSION
    }
}
```

Encrypt UTF-8 snapshot JSON with `AES/GCM/NoPadding`, a new 12-byte IV per envelope, a 128-bit tag, and AAD `MindAnchorBackup|1|<keyId>`. Verify both GCM authentication and `plaintextSha256` before decoding the snapshot.

Do not reuse `EncryptedBackupCodec` for new continuity files: it binds encryption to the source phone’s Keystore and therefore cannot restore on a replacement phone. Leave it only for reading/writing the legacy notes/letters backup path until that path is retired separately.

**Step 6: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests '*RecoveryKeyCodecTest' --tests '*BackupEnvelopeCodecTest' --tests '*RecoveryKeyStoreTest'
```

Expected: PASS, including the Task 1 contract test’s envelope constant.

**Step 7: Commit**

```bash
git add app/src/main/java/org/mindanchor/continuity/crypto app/src/test/java/org/mindanchor/continuity/crypto
git commit -m "feat: add portable encrypted backup key"
```

---

### Task 9: Replace fragile encrypted append files with a verified Drive object store

**Files:**

- Create: `app/src/main/java/org/mindanchor/continuity/ContinuityFiles.kt`
- Create: `app/src/main/java/org/mindanchor/backup/RemoteBackupStore.kt`
- Create: `app/src/main/java/org/mindanchor/backup/GoogleDriveObjectStore.kt`
- Create: `app/src/test/java/org/mindanchor/backup/GoogleDriveObjectStoreTest.kt`
- Modify: `app/src/test/java/org/mindanchor/goinglight/NetworkCallsForbiddenTest.kt`
- Modify: `app/src/main/java/org/mindanchor/backup/GoogleDriveBackupTarget.kt` (KDoc deprecation only)

**Step 1: Write failing object-store tests**

MockWebServer tests must cover:

- auth missing makes zero HTTP calls and returns `AuthExpired`;
- exact-name lookup with JSON containing normal whitespace;
- create latest file;
- replace latest file;
- download bytes unchanged;
- list versioned snapshots with IDs, names, sizes, and modified times;
- HTTP 401 maps to `AuthExpired`;
- HTTP 429/5xx and socket failure map to retryable network errors;
- user text never appears in request metadata or logs.

Run and expect FAIL.

**Step 2: Define the narrow interface**

```kotlin
interface RemoteBackupStore {
    suspend fun put(name: String, bytes: ByteArray): RemoteResult<RemoteObject>
    suspend fun get(name: String): RemoteResult<ByteArray?>
    suspend fun list(prefix: String): RemoteResult<List<RemoteObject>>
}

sealed interface RemoteResult<out T> {
    data class Ok<T>(val value: T) : RemoteResult<T>
    data object AuthExpired : RemoteResult<Nothing>
    data class Retryable(val code: String) : RemoteResult<Nothing>
    data class Permanent(val code: String) : RemoteResult<Nothing>
}
```

`RemoteObject` contains only Drive ID, name, size, and modified time.

**Step 3: Implement robust Drive JSON and byte transport**

Use `kotlinx.serialization.json` to parse list/create responses; do not use substring searches. Keep the existing `drive.file` scope and raw OkHttp REST approach. Escape Drive query names and JSON metadata through serializers.

Use these names:

```kotlin
object ContinuityFiles {
    const val LATEST = ContinuityContract.LATEST_FILE_NAME
    const val SNAPSHOT_PREFIX = "MindAnchor-Continuity-Snapshot-"

    fun versioned(createdAt: Instant, snapshotId: String): String =
        "$SNAPSHOT_PREFIX${DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC).format(createdAt)}-$snapshotId.mab"
}
```

Program 0 does not delete old versioned snapshots. Text snapshots are small, and retaining verified repair points is safer than introducing cloud deletion before real-world size data exists.

**Step 4: Quarantine the legacy append path**

Update KDoc on `GoogleDriveBackupTarget` to state that raw binary newline-delimiting is not a restorable framing format because AES-GCM ciphertext may contain `0x0A`. Do not delete or rewrite existing user files. New settings and workers must use `GoogleDriveObjectStore` only.

**Step 5: Preserve the network allowlist**

Add only `GoogleDriveObjectStore.kt` to the existing Drive-network allowlist in `NetworkCallsForbiddenTest`; keep serializers, workers, Journal, and restore code network-free. Update exact allowlist-size assertions.

**Step 6: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests '*GoogleDriveObjectStoreTest' --tests '*NetworkCallsForbiddenTest'
```

Expected: PASS.

**Step 7: Commit**

```bash
git add app/src/main/java/org/mindanchor/continuity/ContinuityFiles.kt app/src/main/java/org/mindanchor/backup/RemoteBackupStore.kt app/src/main/java/org/mindanchor/backup/GoogleDriveObjectStore.kt app/src/main/java/org/mindanchor/backup/GoogleDriveBackupTarget.kt app/src/test/java/org/mindanchor/backup/GoogleDriveObjectStoreTest.kt app/src/test/java/org/mindanchor/goinglight/NetworkCallsForbiddenTest.kt
git commit -m "feat: add verified Drive object storage"
```

---

### Task 10: Schedule verified checkpoints and nightly snapshots without losing offline changes

**Files:**

- Create: `app/src/main/java/org/mindanchor/continuity/ContinuityPrefs.kt`
- Create: `app/src/main/java/org/mindanchor/continuity/BackupHealth.kt`
- Create: `app/src/main/java/org/mindanchor/continuity/ContinuityBackupCoordinator.kt`
- Create: `app/src/main/java/org/mindanchor/continuity/ContinuityWorkScheduler.kt`
- Create: `app/src/main/java/org/mindanchor/continuity/CheckpointBackupWorker.kt`
- Create: `app/src/main/java/org/mindanchor/continuity/NightlySnapshotWorker.kt`
- Modify: `app/src/main/java/org/mindanchor/HomeActivity.kt`
- Modify: `app/src/main/java/org/mindanchor/journal/JournalRepository.kt`
- Modify: `app/src/main/java/org/mindanchor/research/MorningMeasureRepository.kt`
- Modify: `app/src/main/java/org/mindanchor/data/NotesPrefs.kt`
- Modify: `app/src/main/java/org/mindanchor/letters/LetterStore.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/test/java/org/mindanchor/continuity/ContinuityWorkSchedulerTest.kt`
- Create: `app/src/test/java/org/mindanchor/continuity/ContinuityBackupCoordinatorTest.kt`
- Create: `app/src/test/java/org/mindanchor/continuity/BackupHealthTest.kt`

**Step 1: Add WorkManager test support and failing scheduling tests**

Add `androidx.work:work-testing` under the existing WorkManager version. Tests must assert:

- checkpoint work requires connected network and uses unique-name replacement;
- nightly work requires connected network and battery-not-low;
- next-night delay handles DST and never becomes negative;
- disabling continuity cancels both unique works;
- startup scheduling makes no HTTP call;
- a save made while offline leaves at least one unacknowledged `continuity_changes` row.

Run and expect FAIL.

**Step 2: Implement local flags and health**

`ContinuityPrefs` is a DataStore with:

- `backupEnabled` default false;
- `contextExtractionEnabled` default true;
- `nightlySnapshotsEnabled` default true once backup is enabled;
- last verified checkpoint time/ID/hash;
- last verified nightly time/ID/hash;
- last restore time/hash;
- `dirtySince`;
- last error code from a closed enum (`NONE`, `AUTH`, `NETWORK`, `KEY_MISSING`, `VERIFY_FAILED`, `DECODE_FAILED`).

Do not store exception messages, Journal text, access tokens, or recovery-key material in health state or logs.

**Step 3: Implement checkpoint coordination**

After a Journal, morning-measure, Note, or Letter write succeeds, record/retain dirty state and call:

```kotlin
ContinuityWorkScheduler.requestCheckpoint(context)
```

Journal and morning rows already have transactional `continuity_changes`. Notes and Letters request work after their DataStore write; the full checkpoint captures their current state even if no separate event row exists.

Use `ExistingWorkPolicy.REPLACE`: if a new save occurs during upload, cancel the old worker and recapture the complete current state. No bounded payload queue is needed, and no oldest entry is dropped. Keep the legacy `PendingBackup` queue only for the quarantined old path.

**Step 4: Implement the worker algorithm**

`CheckpointBackupWorker.doWork()` must:

1. Exit success without network if backup is disabled.
2. Record `KEY_MISSING` and exit success if no verified recovery key exists.
3. Capture a canonical snapshot from local stores.
4. Encrypt it into a `BackupEnvelope`.
5. `put(ContinuityFiles.LATEST, envelopeBytes)`.
6. `get(ContinuityFiles.LATEST)` immediately.
7. Compare exact SHA-256 of uploaded and downloaded envelope bytes.
8. Decrypt the downloaded envelope and verify the snapshot content hash.
9. In one local transaction, acknowledge pending Room changes with this snapshot ID.
10. Update backup health only after steps 7–9 succeed.

Map auth/key/permanent configuration problems to `Result.success()` plus visible health state so WorkManager does not retry forever. Map network/429/5xx to `Result.retry()` with exponential backoff.

**Step 5: Implement nightly versioned snapshots**

Schedule unique one-time work for the next local 02:00, with connected-network and battery-not-low constraints. Android may defer the exact time; the UI must say “scheduled overnight,” not promise exactly 02:00. On completion, upload and read-verify a versioned file, refresh Latest with the same verified envelope, update health, and schedule the next night. `HomeActivity.onCreate` also calls `ensureNightlyScheduled` so process death between completion and rescheduling self-repairs.

**Step 6: Keep startup offline**

`HomeActivity` may schedule WorkManager and resume local restore state, but it may not construct a token or call Drive directly. Add a source-level test if needed to pin this boundary.

**Step 7: Run worker tests**

```bash
./gradlew :app:testDebugUnitTest --tests '*ContinuityWorkSchedulerTest' --tests '*ContinuityBackupCoordinatorTest' --tests '*BackupHealthTest'
```

Expected: PASS.

**Step 8: Commit**

```bash
git add app/src/main/java/org/mindanchor/continuity app/src/main/java/org/mindanchor/HomeActivity.kt app/src/main/java/org/mindanchor/journal/JournalRepository.kt app/src/main/java/org/mindanchor/research/MorningMeasureRepository.kt app/src/main/java/org/mindanchor/data/NotesPrefs.kt app/src/main/java/org/mindanchor/letters/LetterStore.kt gradle/libs.versions.toml app/build.gradle.kts app/src/test/java/org/mindanchor/continuity
git commit -m "feat: verify incremental continuity checkpoints"
```

---

### Task 11: Make replacement-phone restore staged, idempotent, and resumable

**Files:**

- Create: `app/src/main/java/org/mindanchor/continuity/RestoreStateStore.kt`
- Create: `app/src/main/java/org/mindanchor/continuity/RestoreCoordinator.kt`
- Create: `app/src/main/java/org/mindanchor/continuity/RestoreCandidate.kt`
- Create: `app/src/main/java/org/mindanchor/continuity/RestoreActivity.kt`
- Create: `app/src/main/java/org/mindanchor/continuity/RestoreScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/org/mindanchor/HomeActivity.kt`
- Create: `app/src/test/java/org/mindanchor/continuity/RestoreCoordinatorTest.kt`
- Create: `app/src/androidTest/java/org/mindanchor/continuity/RestoreResumeTest.kt`
- Create: `app/src/androidTest/java/org/mindanchor/continuity/RestoreScreenTest.kt`

**Step 1: Write failing restore-state tests**

Use these exact stages:

```kotlin
enum class RestoreStage {
    NONE,
    DOWNLOADED,
    DECRYPTED,
    ROOM_MERGED,
    DATASTORES_MERGED,
    VERIFIED,
}
```

Tests must prove that a failure after each nonterminal stage can resume without duplicate Journal entries, context, morning measures, Notes, Letters, contacts, pulses, or EMA moments.

**Step 2: Stage before mutating**

After download, write envelope bytes to `filesDir/continuity/restore-staged.mab.tmp`, flush/close, and atomically rename to `restore-staged.mab`. Then persist stage `DOWNLOADED`, remote name, envelope SHA-256, and expected content hash. Never write decrypted Journal text to a staging file.

**Step 3: Select a restore candidate safely**

Candidate order is:

1. `MindAnchor-Continuity-Latest.mab`;
2. versioned snapshots from newest to oldest.

Download, decrypt, and verify before showing a preview. A corrupt Latest automatically falls back to the newest decryptable versioned snapshot and clearly tells the user which backup was selected. Wrong recovery key is distinct from corruption and does not try to import anything.

**Step 4: Implement idempotent merge phases**

`RestoreCoordinator.resume()` performs:

- DECRYPTED: parse/verify snapshot and keep only its hash/stage in prefs;
- ROOM_MERGED: one Room transaction upserts Journal/context/morning/change rows by stable IDs;
- DATASTORES_MERGED: call existing legacy `BackupRepository.import`, then Notes/Letters merges and friction/always-open replacement;
- VERIFIED: recapture local logical content, compute `ContinuityContentHasher`, and compare with expected content hash.

Write each stage only after its phase succeeds. Every phase is safe to repeat. Delete `restore-staged.mab` only after `VERIFIED` is durable.

If the final hash differs, keep the staged encrypted file, set health `VERIFY_FAILED`, do not claim success, and offer retry/export diagnostics. Do not delete local data to force a match.

Before the first stage on a new restore, run a local-data preflight. Allow a replacement restore only when Journal, context, morning measures, Notes, Letters, safety-plan/contact/pulse history, and user launcher choices are empty. If meaningful data exists, stop before download/import and ask the user to create a local encrypted copy. A resumed staged restore bypasses this preflight because its idempotent phases have already begun.

**Step 5: Resume on ordinary launcher start**

`HomeActivity.onCreate` launches only local `RestoreCoordinator.resumeIfPending()` work. It does not download automatically. Because the recovery key is already stored on that phone after the user enters it, local phase resumption requires no user prompt.

**Step 6: Build the replacement-phone flow**

`RestoreActivity` shows:

1. Google sign-in status;
2. recovery-key entry;
3. newest verified candidate timestamp, app version, Journal count, measure count, and source device ID;
4. explicit Restore confirmation;
5. per-stage progress;
6. final matching content hash.

Never show raw Journal text on the confirmation screen. Register the activity as non-exported.

**Step 7: Run interruption and UI tests**

```bash
./gradlew :app:testDebugUnitTest --tests '*RestoreCoordinatorTest'
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.continuity.RestoreResumeTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.continuity.RestoreScreenTest
```

Expected: PASS.

**Step 8: Commit**

```bash
git add app/src/main/java/org/mindanchor/continuity app/src/main/java/org/mindanchor/HomeActivity.kt app/src/main/AndroidManifest.xml app/src/test/java/org/mindanchor/continuity app/src/androidTest/java/org/mindanchor/continuity
git commit -m "feat: restore continuity after phone replacement"
```

---

### Task 12: Replace the misleading Drive settings with backup health, recovery, and kill switches

**Files:**

- Rewrite: `app/src/main/java/org/mindanchor/settings/GoogleDriveBackupSettingsSection.kt`
- Modify: `app/src/main/java/org/mindanchor/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/org/mindanchor/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/org/mindanchor/settings/GoogleDriveBackupSettingsSectionFindingTest.kt`
- Create: `app/src/androidTest/java/org/mindanchor/settings/ContinuitySettingsTest.kt`

**Step 1: Write failing settings tests**

Tests must assert:

- old Notes/Letters toggle callbacks are no longer no-ops;
- Drive backup cannot be enabled before Google sign-in and recovery-key verification;
- generated recovery key is shown once and must be fully re-entered;
- disabling backup cancels automatic work without deleting local or remote data;
- context extraction can be disabled locally without disabling Journal writing;
- “Back up now” schedules a checkpoint rather than directly performing network work on the UI scope;
- “Restore on this phone” opens `RestoreActivity`;
- health shows pending, last verified checkpoint, last verified nightly snapshot, last restore, and a concise error state;
- no string says “backed up” when only an upload, not read-back verification, occurred.

Run and expect FAIL.

**Step 2: Replace the legacy surface**

Keep the existing Google sign-in flow but replace per-type Notes/Letters controls with one continuity section:

- Google account
- recovery key status: Not created / Needs verification / Verified
- automatic continuity backup switch
- nightly snapshots switch
- structural context extraction local kill switch
- Back up now
- Restore on this phone
- Save encrypted copy to a chosen file
- Export research JSON with privacy warning
- Forget account

Do not add notification permissions or background-location permissions.

**Step 3: Make health language exact**

Use these state meanings:

- `Pending`: local changes exist after the last verified checkpoint.
- `Verified <time>`: upload, download, envelope authentication, and content hash succeeded.
- `Needs sign-in`: no retry loop is running.
- `Recovery key required`: automatic backup remains off.
- `Verification failed`: remote data was not acknowledged; the previous verified snapshot remains the repair point.

Do not infer mental-health status from backup state.

**Step 4: Expose Journal research export**

Wire the same document-picker export from Journal Patterns and Settings. The exported filename is `mindanchor-research-YYYY-MM-DD.json`. Include the canonical hash in the visible success message so Device A and Device B exports can be compared without opening private text.

**Step 5: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests '*GoogleDriveBackupSettingsSectionFindingTest'
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.settings.ContinuitySettingsTest
```

Expected: PASS.

**Step 6: Commit**

```bash
git add app/src/main/java/org/mindanchor/settings app/src/main/res/values/strings.xml app/src/test/java/org/mindanchor/settings/GoogleDriveBackupSettingsSectionFindingTest.kt app/src/androidTest/java/org/mindanchor/settings/ContinuitySettingsTest.kt
git commit -m "feat: surface verified backup health"
```

---

### Task 13: Make official releases stable, signed, reproducible, and upgrade-tested

**Files:**

- Modify: `.github/workflows/release.yml`
- Modify: `.github/workflows/ci.yml`
- Create: `tools/verify-reproducible-release.sh`
- Create: `app/src/test/java/org/mindanchor/release/ReleaseSafetyTest.kt`
- Modify: `app/build.gradle.kts`
- Modify: `docs/RELEASING.md`
- Create: `docs/qa/program-0-upgrade-runbook.md`

**Step 1: Write failing release-safety tests**

The source-level test must assert:

- `AnchorDatabase` does not contain `fallbackToDestructiveMigration`;
- tag releases contain no debug-build fallback;
- release workflow checks all four signing secrets before building;
- `versionCode` is greater than 94;
- Room schema export remains enabled.

Run and expect FAIL.

**Step 2: Make tag releases fail closed**

Rewrite the release workflow so a `v*` tag or manual official release exits nonzero when any signing secret is missing. Remove the debug APK publication branch. Contributors still receive debug builds from ordinary CI, but no GitHub Release may be called official with a debug certificate.

After build, run:

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

Compare the SHA-256 certificate digest to a repository variable or documented expected fingerprint. Never store the keystore or passwords in the repository.

**Step 3: Add a reproducibility check**

`tools/verify-reproducible-release.sh` performs two `clean assembleRelease` builds with the same `SOURCE_DATE_EPOCH`, copies each APK before cleaning, computes SHA-256, and fails when hashes differ. Use a temporary directory created by `mktemp -d` and a trap for cleanup.

Run this in the release workflow before publishing. If signed APK output is not byte-identical, compare unsigned release APKs and separately verify the certificate; document the exact reproducibility boundary instead of weakening the check silently.

**Step 4: Strengthen CI**

In `.github/workflows/ci.yml`:

- fix the existing `findures` typo to `findings` so Semgrep findings actually fail the job;
- add an API-35 emulator job for `connectedDebugAndroidTest`;
- upload unit and instrumentation XML reports on failure;
- run migration tests in the emulator job;
- keep the JVM suite and coverage gate.

This Semgrep typo is in scope because a production spine cannot rely on a security gate that always compares an unset variable.

**Step 5: Pin the Program 0 version**

After all feature tasks pass, set:

```kotlin
versionCode = 95
versionName = "0.71.0"
```

Do not bump earlier; one version bump belongs to the complete releasable slice.

**Step 6: Complete the signing operator step**

The repository cannot create or store the owner’s secret key. The owner must:

1. create one release keystore once;
2. store two offline copies outside the phone and repository;
3. configure `MINDANCHOR_KEYSTORE_BASE64`, `MINDANCHOR_KEYSTORE_PASSWORD`, `MINDANCHOR_KEY_ALIAS`, and `MINDANCHOR_KEY_PASSWORD` in GitHub Secrets;
4. record only the public certificate SHA-256 fingerprint in `docs/RELEASING.md`;
5. install two consecutive signed builds over each other and verify Android accepts the upgrade.

Do not mark this step complete until the fingerprint from the built APK matches the documented one.

**Step 7: Write and run the upgrade runbook**

The runbook covers:

- install the last signed v0.70.x APK;
- create notification, safety-plan, JournalStore, Note, and Letter fixtures;
- install v0.71.0 with `adb install -r`;
- verify Room 3→4→5→6 and 4→5→6 paths on fixture APKs/databases;
- confirm all old data and new Journal import remain;
- confirm no network is needed for first post-upgrade launch;
- export the continuity content hash before and after where the older build supports it.

**Step 8: Run release checks**

```bash
./gradlew :app:testDebugUnitTest --tests '*ReleaseSafetyTest'
./tools/verify-reproducible-release.sh
```

Expected: PASS with the release-signing environment configured.

**Step 9: Commit**

```bash
git add .github/workflows tools/verify-reproducible-release.sh app/src/test/java/org/mindanchor/release/ReleaseSafetyTest.kt app/build.gradle.kts docs/RELEASING.md docs/qa/program-0-upgrade-runbook.md
git commit -m "build: harden Program 0 releases"
```

---

### Task 14: Prove offline, background, battery, process-death, and replacement-phone behavior

**Files:**

- Create: `docs/qa/program-0-continuity-runbook.md`
- Create: `docs/qa/program-0-continuity-log.md`
- Create: `docs/qa/program-0-battery-log.md`
- Create: `app/src/androidTest/java/org/mindanchor/continuity/ContinuityRoundTripTest.kt`
- Create: `app/src/test/java/org/mindanchor/continuity/OfflineStartupBoundaryTest.kt`

**Step 1: Write the failing in-app round-trip test**

The instrumentation test uses a test database and test DataStores to:

1. create one multiline Journal entry;
2. verify the original and four structural facts are separate;
3. create one morning measure;
4. add a Quick Note, Letter/read date, frictioned app, and always-open app;
5. capture and encrypt a snapshot;
6. wipe the destination test stores;
7. restore through `RestoreCoordinator`;
8. recapture;
9. assert equal logical content hashes and exact field equality;
10. run restore a second time and assert no duplicates.

Run and expect FAIL until every previous task is wired.

**Step 2: Pin offline startup**

`OfflineStartupBoundaryTest` scans startup code and asserts that `HomeActivity`, Journal, snapshot capture, and restore resumption do not reference OkHttp, `GoogleDriveObjectStore`, or `currentAccessToken`. Only workers and the explicit sign-in/restore UI may cross the network boundary.

**Step 3: Run the automated verification set**

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:koverXmlReportDebug
./gradlew :app:lintDebug
```

Expected: PASS.

**Step 4: Execute the offline/device-loss runbook**

On Device A:

1. Install the signed v0.71.0 APK and make it the launcher.
2. Generate and verify the recovery key; store it outside Device A.
3. Sign into Drive and enable continuity.
4. Turn on airplane mode.
5. Cold-start MindAnchor, write a Journal entry, complete the morning measure, and force-stop mid-draft once.
6. Confirm launcher/Journal work, the draft returns, and backup health says Pending without blocking or repeated dialogs.
7. Reconnect, wait for checkpoint work, and confirm health says Verified only after read-back verification.
8. Trigger Back up now and record the visible content hash.

On Device B or a fully wiped test installation:

1. Install the same signed APK.
2. Sign into the same Google account.
3. Enter the externally stored recovery key.
4. Restore the newest verified candidate.
5. Confirm Journal originals, structural facts, morning measure, Note, Letter, safety-plan data, launcher favorites, frictioned apps, and always-open apps.
6. Export the research record and compare its content hash with Device A.

Repeat the entire A→B restore three times. A single successful run does not close Program 0.

**Step 5: Execute process-death and corrupt-latest cases**

- Force-stop after restore download, after Room merge, and after DataStore merge; relaunch and verify resume/no duplicates.
- Upload a deliberately corrupted Latest file in a test Drive account; verify restore falls back to the newest valid versioned snapshot and reports the fallback.
- Enter a wrong recovery key; verify zero local data mutation.
- Revoke Google access; verify local use remains normal and health says Needs sign-in without a retry storm.

**Step 6: Measure battery/background behavior**

For one 24-hour period on the real phone:

- capture `dumpsys batterystats` before and after;
- record checkpoint count, nightly-work completion, total bytes uploaded, and any deferred work;
- verify no wake lock, exact alarm, foreground service, or new runtime permission was added;
- record device model, Android version, battery optimization state, and network conditions.

Do not invent a battery threshold after seeing the result. Record the observation; define a formal threshold before Program 2 sensor work.

**Step 7: Record evidence, not conclusions**

`program-0-continuity-log.md` includes date/time, app commit, APK hash, signing certificate fingerprint, source/destination device, source/restore content hashes, selected snapshot, duration, failures, and repair result. Never paste Journal text or the recovery key into the log.

**Step 8: Commit test/runbook changes**

```bash
git add docs/qa/program-0-continuity-runbook.md docs/qa/program-0-continuity-log.md docs/qa/program-0-battery-log.md app/src/androidTest/java/org/mindanchor/continuity/ContinuityRoundTripTest.kt app/src/test/java/org/mindanchor/continuity/OfflineStartupBoundaryTest.kt
git commit -m "test: prove Program 0 continuity loop"
```

---

### Task 15: Final review and Program 0 release gate

**Files:**

- Modify: `docs/backup/program-0-data-inventory.md`
- Modify: `docs/qa/program-0-continuity-log.md`
- Modify: `docs/RELEASING.md`
- Create: `RELEASE_NOTES_v0.71.0.md`

**Step 1: Review every Program 0 constraint**

Check and record:

- no diagnosis/autonomy/wearable interpretation was introduced;
- context rows cannot overwrite Journal entries;
- no legacy user store was deleted;
- no cloud work occurs at ordinary startup;
- no device-only key is required to restore cloud snapshots;
- no successful upload is acknowledged before read-back verification;
- no bounded queue drops unsynced Journal data;
- no destructive Room migration fallback exists;
- protected apps survive restore;
- wrong key/corrupt snapshot cannot partially mutate local data;
- recovery key and private Journal text never appear in logs/tests/release artifacts.

**Step 2: Run the final automated gate from a clean checkout**

```bash
./gradlew clean :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:koverXmlReportDebug
./tools/verify-reproducible-release.sh
```

Expected: PASS.

**Step 3: Require physical evidence**

Confirm `docs/qa/program-0-continuity-log.md` contains three successful signed Device A→Device B restore runs with matching hashes. If hardware, Google sign-in, or signing secrets prevent this, Program 0 is blocked—not complete—and v0.71.0 must not be tagged.

**Step 4: Request code review**

Use the `requesting-code-review` skill and review the full diff against this plan. Resolve only Program 0 findings; log unrelated issues separately.

**Step 5: Write release notes**

Explain:

- text Journal with Today, Entries, Patterns;
- separate structural context, with no diagnosis or semantic inference;
- morning personal research measure;
- verified encrypted Drive continuity and external recovery key;
- replacement-phone restore;
- known Program 0 limits: no media, no wearable-driven interpretation, no autonomous control, no clinical claims.

**Step 6: Commit after all gates pass; tag and push only with explicit owner approval**

Prepare and commit the release documentation locally. Then show the final diff, test evidence, APK hash, and certificate fingerprint to the owner. Do not create a tag, push a branch, or publish a GitHub Release until the owner explicitly authorizes those external changes.

```bash
git add docs/backup/program-0-data-inventory.md docs/qa/program-0-continuity-log.md docs/RELEASING.md RELEASE_NOTES_v0.71.0.md
git commit -m "docs: close Program 0 continuity proof"
```

After explicit approval:

```bash
git tag v0.71.0
git push origin HEAD
git push origin v0.71.0
```

Expected: the official release workflow builds one certificate-verified, reproducibility-checked APK and publishes its SHA-256.

## Self-review checklist for the implementing agent

Before declaring this plan executed, inspect the code—not just test names—and answer each item with a path and line number:

- Which transaction guarantees the original entry and pending backup change before optional context runs?
- Which code keeps semantic inference out of Program 0?
- Which persisted row survives an offline save until a worker can retry?
- Which exact check prevents an unverified upload from being acknowledged?
- Which code makes the recovery key portable across phones?
- Which restore phases are idempotent after process death?
- Which content is included in the canonical hash, and which metadata is excluded?
- Which tests walk database versions 1, 3, 4, and 5 to version 6?
- Which workflow line prevents a debug-signed official release?
- Where are the three physical replacement-phone results recorded?

If any answer is “the architecture implies it,” the implementation is incomplete. The answer must point to executable code, a test, or a recorded physical verification result.
