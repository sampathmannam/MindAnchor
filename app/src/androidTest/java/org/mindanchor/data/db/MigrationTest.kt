package org.mindanchor.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migrations are hand-written SQL, and Room validates them against its own
 * generated schema at open time. If a column type or a primary key differs
 * by so much as a keyword, every existing install crashes on launch — with
 * the user's notification journal inside. These tests walk a v1 database up
 * to the current version the way a real upgrade would.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clean() = context.deleteDatabase(dbName).let { }

    @After
    fun cleanUp() = context.deleteDatabase(dbName).let { }

    /** Creates the schema exactly as version 1 shipped it. */
    private fun createVersion1() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS held_notifications (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "packageName TEXT NOT NULL, " +
                            "appLabel TEXT NOT NULL, " +
                            "title TEXT NOT NULL, " +
                            "text TEXT NOT NULL, " +
                            "postedAt INTEGER NOT NULL, " +
                            "releasedAt INTEGER)",
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        helper.writableDatabase.use { db ->
            db.execSQL(
                "INSERT INTO held_notifications " +
                    "(packageName, appLabel, title, text, postedAt, releasedAt) " +
                    "VALUES ('com.example', 'Example', 'Hello', 'Body', 1000, NULL)",
            )
        }
    }

    /** Creates the schema exactly as version 3 shipped it (MIGRATION_1_2 + MIGRATION_2_3 applied to a v1 base). */
    private fun createVersion3() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS held_notifications (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "packageName TEXT NOT NULL, " +
                            "appLabel TEXT NOT NULL, " +
                            "title TEXT NOT NULL, " +
                            "text TEXT NOT NULL, " +
                            "postedAt INTEGER NOT NULL, " +
                            "releasedAt INTEGER)",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS pulse_results (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "takenAt INTEGER NOT NULL, " +
                            "score INTEGER NOT NULL)",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS safety_plan (" +
                            "id INTEGER NOT NULL, " +
                            "warningSigns TEXT NOT NULL, " +
                            "copingSteps TEXT NOT NULL, " +
                            "distractions TEXT NOT NULL, " +
                            "reasonsForLiving TEXT NOT NULL, " +
                            "environmentSafety TEXT NOT NULL, " +
                            "updatedAt INTEGER NOT NULL, " +
                            "PRIMARY KEY(id))",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS crisis_contacts (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "name TEXT NOT NULL, " +
                            "phone TEXT NOT NULL, " +
                            "isProfessional INTEGER NOT NULL)",
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        helper.writableDatabase.use { }
    }

    /** Creates the schema exactly as version 4 shipped it (MIGRATION_3_4's tier column present). */
    private fun createVersion4WithTier() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS held_notifications (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "packageName TEXT NOT NULL, " +
                            "appLabel TEXT NOT NULL, " +
                            "title TEXT NOT NULL, " +
                            "text TEXT NOT NULL, " +
                            "postedAt INTEGER NOT NULL, " +
                            "releasedAt INTEGER, " +
                            "tier TEXT NOT NULL DEFAULT 'MACHINE')",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS pulse_results (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "takenAt INTEGER NOT NULL, " +
                            "score INTEGER NOT NULL)",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS safety_plan (" +
                            "id INTEGER NOT NULL, " +
                            "warningSigns TEXT NOT NULL, " +
                            "copingSteps TEXT NOT NULL, " +
                            "distractions TEXT NOT NULL, " +
                            "reasonsForLiving TEXT NOT NULL, " +
                            "environmentSafety TEXT NOT NULL, " +
                            "updatedAt INTEGER NOT NULL, " +
                            "PRIMARY KEY(id))",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS crisis_contacts (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "name TEXT NOT NULL, " +
                            "phone TEXT NOT NULL, " +
                            "isProfessional INTEGER NOT NULL)",
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        helper.writableDatabase.use { db ->
            db.execSQL(
                "INSERT INTO held_notifications " +
                    "(packageName, appLabel, title, text, postedAt, releasedAt, tier) " +
                    "VALUES ('com.example', 'Example', 'Hello', 'Body', 1000, NULL, 'HUMAN')",
            )
        }
    }

    /** Creates the schema exactly as version 5 shipped it (MIGRATION_4_5's tier column dropped). */
    private fun createVersion5WithNotification() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS held_notifications (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "packageName TEXT NOT NULL, " +
                            "appLabel TEXT NOT NULL, " +
                            "title TEXT NOT NULL, " +
                            "text TEXT NOT NULL, " +
                            "postedAt INTEGER NOT NULL, " +
                            "releasedAt INTEGER)",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS pulse_results (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "takenAt INTEGER NOT NULL, " +
                            "score INTEGER NOT NULL)",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS safety_plan (" +
                            "id INTEGER NOT NULL, " +
                            "warningSigns TEXT NOT NULL, " +
                            "copingSteps TEXT NOT NULL, " +
                            "distractions TEXT NOT NULL, " +
                            "reasonsForLiving TEXT NOT NULL, " +
                            "environmentSafety TEXT NOT NULL, " +
                            "updatedAt INTEGER NOT NULL, " +
                            "PRIMARY KEY(id))",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS crisis_contacts (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "name TEXT NOT NULL, " +
                            "phone TEXT NOT NULL, " +
                            "isProfessional INTEGER NOT NULL)",
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        helper.writableDatabase.use { db ->
            db.execSQL(
                "INSERT INTO held_notifications " +
                    "(packageName, appLabel, title, text, postedAt, releasedAt) " +
                    "VALUES ('com.example', 'Example', 'Hello', 'Body', 1000, NULL)",
            )
        }
    }

    /**
     * Creates the schema exactly as version 6 shipped it - the complete
     * Program 0 table set - and seeds one row in each Program 0 research
     * table, so the v6 to v7 walk proves the upgrade adds tables without
     * touching anything a person already wrote.
     */
    private fun createVersion6WithProgramZeroData() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    PROGRAM_ZERO_SCHEMA.forEach(db::execSQL)
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        helper.writableDatabase.use { db -> PROGRAM_ZERO_ROWS.forEach(db::execSQL) }
    }

    private fun createVersion7WithLedgerRow() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    PROGRAM_ZERO_SCHEMA.forEach(db::execSQL)
                    RESEARCH_SCHEMA.forEach(db::execSQL)
                    db.execSQL(
                        "INSERT INTO research_ledger_events " +
                            "(id, sequence, kind, occurredAt, recordedAt, localDate, studyPhaseId, " +
                            "sourceDeviceId, note, payloadJson, previousEventHash, eventHash) VALUES " +
                            "('event-before-v8', 1, 'EXERCISE', 1000, 1000, '2026-08-29', " +
                            "'phase-0', 'device-a', 'preserve me', '{}', '', 'event-before-v8')",
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase.use { }
    }

    private fun ledgerEvent() = ResearchLedgerEventEntity(
        id = "event-1",
        sequence = 1L,
        kind = "EXERCISE",
        occurredAt = 1_000L,
        recordedAt = 1_000L,
        localDate = "2026-08-29",
        studyPhaseId = "phase-0",
        sourceDeviceId = "device-a",
        note = "morning run",
        payloadJson = "{}",
        previousEventHash = "",
        eventHash = "event-1",
    )

    private fun openCurrent(): AnchorDatabase =
        Room.databaseBuilder(context, AnchorDatabase::class.java, dbName)
            .addMigrations(*AnchorDatabase.migrations())
            .withResearchImmutability()
            .build()

    private fun runDirectMigrations(
        expectedOldVersion: Int,
        afterStep: (Int, androidx.sqlite.db.SupportSQLiteDatabase) -> Unit,
    ) {
        val migrations = AnchorDatabase.migrations().associateBy { it.startVersion }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) {
                    assertEquals(expectedOldVersion, oldVersion)
                    var version = oldVersion
                    while (version < newVersion) {
                        val migration = requireNotNull(migrations[version]) {
                            "missing direct migration from version $version"
                        }
                        migration.migrate(db)
                        version = migration.endVersion
                        afterStep(version, db)
                    }
                }
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).use { helper ->
            helper.writableDatabase
        }
    }

    private fun triggerNames(db: androidx.sqlite.db.SupportSQLiteDatabase): List<String> =
        db.query("SELECT name FROM sqlite_master WHERE type = 'trigger' ORDER BY name").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

    @Test
    fun aVersion1DatabaseUpgradesWithoutLosingNotifications() = runBlocking {
        createVersion1()

        val db = openCurrent()
        try {
            // Opening validates every migration against the generated schema.
            val journal = db.heldNotifications().journal().first()
            assertEquals("the held notification survived the upgrade", 1, journal.size)
            assertEquals("Hello", journal.first().title)

            // Tables added by later versions must now exist and work.
            db.pulses().insert(PulseResult(takenAt = 2000, score = 60))
            assertEquals(1, db.pulses().history().first().size)

            db.safety().savePlan(SafetyPlan(warningSigns = "restless"))
            assertEquals("restless", db.safety().plan().first()?.warningSigns)

            db.safety().addContact(CrisisContact(name = "Ana", phone = "5551234567"))
            assertEquals(1, db.safety().contacts().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun aFreshInstallCreatesEveryTable() = runBlocking {
        val db = openCurrent()
        try {
            assertTrue(db.heldNotifications().journal().first().isEmpty())
            assertTrue(db.pulses().history().first().isEmpty())
            assertTrue(db.safety().contacts().first().isEmpty())
            // A plan row does not exist until written; the screen tolerates null.
            db.safety().savePlan(SafetyPlan(copingSteps = "walk"))
            assertNotNull(db.safety().plan().first())
        } finally {
            db.close()
        }
    }

    @Test
    fun crisisContactsRoundTripAndDelete() = runBlocking {
        val db = openCurrent()
        try {
            val dao = db.safety()
            dao.addContact(CrisisContact(name = "Ana", phone = "+91 98765 43210"))
            dao.addContact(
                CrisisContact(name = "Dr Rao", phone = "02079460958", isProfessional = true),
            )
            val stored = dao.contacts().first()
            assertEquals(2, stored.size)
            // Ordered with people before professionals.
            assertEquals("Ana", stored.first().name)
            assertEquals(2, dao.contactsNow().size)

            dao.removeContact(stored.first())
            assertEquals(1, dao.contacts().first().size)
        } finally {
            db.close()
        }
    }

    // NOTE: the plan's snippet used `openCurrent().use { db -> ... }`.
    // androidx.room:room-runtime 2.6.1's RoomDatabase does not implement
    // java.io.Closeable (compileDebugAndroidTestKotlin fails to resolve
    // `.use` against it — "receiver type mismatch" against
    // `fun <T : Closeable?, R> T.use(...)`), so these follow the
    // try/finally + db.close() pattern the existing tests in this file
    // already use for the same open-then-close shape.

    @Test
    fun aVersion3DatabaseUpgradesThroughTheMissingTierMigration() = runBlocking {
        createVersion3()
        val db = openCurrent()
        try {
            assertTrue(db.heldNotifications().journal().first().isEmpty())
            assertTrue(db.journal().entries().first().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun aVersion4DatabaseDropsTierAndCreatesProgramZeroTables() = runBlocking {
        createVersion4WithTier()
        val db = openCurrent()
        try {
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
        } finally {
            db.close()
        }
    }

    @Test
    fun aVersion5DatabaseKeepsExistingRowsWhenProgramZeroTablesAreAdded() = runBlocking {
        createVersion5WithNotification()
        val db = openCurrent()
        try {
            assertEquals(1, db.heldNotifications().journal().first().size)
            assertTrue(db.journal().entries().first().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun aVersion6DatabaseKeepsProgramZeroDataAndGainsTheResearchTables() = runBlocking {
        createVersion6WithProgramZeroData()
        val db = openCurrent()
        try {
            // Nothing a person wrote may be lost by an upgrade.
            assertEquals("Original words", db.journal().entry("entry-1")?.body)
            assertEquals(1, db.journal().allContext().size)
            assertEquals(1, db.journal().morningMeasuresNow().size)
            assertEquals(1, db.journal().allChangesNow().size)

            // And the new tables exist and work.
            assertEquals(0, db.research().ledgerEventCount())
            assertEquals(0, db.research().studyPhaseCount())
            db.research().insertLedgerEvents(listOf(ledgerEvent()))
            assertEquals(1, db.research().ledgerEventCount())
        } finally {
            db.close()
        }
    }

    @Test
    fun aVersion7DatabaseKeepsLedgerDataAndGainsEveryPassiveTable() = runBlocking {
        createVersion7WithLedgerRow()
        val db = openCurrent()
        try {
            assertEquals("preserve me", db.research().ledgerEventsNow().single().note)
            val dao = db.passive()
            val provenance = PassiveRawProvenanceEntity(
                "raw-1", "PHYSIOLOGY", "HEART_RATE", 1_000L, 1_000L, "bpm", "watch",
                null, null, null, 1_100L, 1_200L, "UTC", 0, "record-1", 1L,
            )
            assertTrue(dao.insertRawProvenance(listOf(provenance)).single() > 0L)
            assertTrue(dao.insertRawSamples(listOf(PassiveRawSampleEntity("raw-1", 72.0, 1_200L))).single() > 0L)
            assertTrue(dao.insertSourceReads(listOf(PassiveSourceReadEntity("read-1", "run-1", "PHYSIOLOGY", "SUCCESS", 0L, 2_000L, "UTC", 2_000L, 1, null))).single() > 0L)
            assertTrue(dao.insertSourceLags(listOf(PassiveSourceLagEntity("lag-1", "PHYSIOLOGY", 1_000L, 1_100L, 1_200L, 100L, false, 1_200L))).single() > 0L)
            assertTrue(dao.insertBaselineSegment(PassiveBaselineSegmentEntity("segment-1", 1_000L, "{}", "window-v1", "daily-v1")) > 0L)
            assertTrue(dao.insertPipelineRun(PassivePipelineRunEntity("run-1", 1_000L, 2_000L, 0L, 2_000L, "UTC", true, true, "SUCCESS_PERMISSIONED", "{}")) > 0L)
            val windows = listOf(
                PassiveWindowRevisionEntity("window-1", 0L, 900_000L, 2_000L, "UTC", 0, null, "segment-1", "[]", 1.0, true, 0L, "[]", "[]", "[]", "window-v1", 1_100L, 1_200L, false, "INITIAL", "window-hash"),
                PassiveWindowRevisionEntity("window-2", 0L, 900_000L, 2_000L, "UTC", 0, null, "segment-1", "[]", 1.0, true, 0L, "[]", "[]", "[]", "window-v1", 1_100L, 1_200L, false, "BACKFILL", "window-hash"),
            )
            val days = listOf(
                PassiveDailyRevisionEntity("daily-1", "2026-08-30", 2_000L, "OBSERVED", "{}", "{}", "segment-1", 1_100L, 1_200L, "{}", "{}", "{}", "{}", "{}", "window-v1", "daily-v1", 1_100L, "INITIAL", "daily-hash"),
                PassiveDailyRevisionEntity("daily-2", "2026-08-30", 2_000L, "OBSERVED", "{}", "{}", "segment-1", 1_100L, 1_200L, "{}", "{}", "{}", "{}", "{}", "window-v1", "daily-v1", 1_100L, "BACKFILL", "daily-hash"),
            )
            val decisions = listOf(
                PassiveObservationDecisionEntity("decision-1", "2026-08-30", 2_000L, "OBSERVED", "NO_SIGNAL", "segment-1", null, null, null, "{}", "INITIAL", "decision-hash"),
                PassiveObservationDecisionEntity("decision-2", "2026-08-30", 2_000L, "OBSERVED", "NO_SIGNAL", "segment-1", null, null, null, "{}", "BACKFILL", "decision-hash"),
            )
            assertTrue(dao.insertWindowRevisions(windows).all { it > 0L })
            assertTrue(dao.insertDailyRevisions(days).all { it > 0L })
            assertTrue(dao.insertObservationDecisions(decisions).all { it > 0L })
            assertEquals(listOf("INITIAL", "BACKFILL"), dao.windowRevisionsNow().map { it.revisionReason })
            assertEquals(listOf("INITIAL", "BACKFILL"), dao.dailyRevisionsNow().map { it.revisionReason })
            assertEquals(listOf("INITIAL", "BACKFILL"), dao.observationDecisionsNow().map { it.revisionReason })
        } finally {
            db.close()
        }
    }

    @Test
    fun directVersion7To8MigrationInstallsAllTriggersWithoutRoomCallback() {
        createVersion7WithLedgerRow()
        runDirectMigrations(expectedOldVersion = 7) { version, db ->
            assertEquals(8, version)
            assertEquals(ALL_IMMUTABILITY_TRIGGERS, triggerNames(db))
        }
    }

    @Test
    fun directVersion6To7To8MigrationInstallsOnlyExistingThenAllTriggers() {
        createVersion6WithProgramZeroData()
        runDirectMigrations(expectedOldVersion = 6) { version, db ->
            when (version) {
                7 -> assertEquals(RESEARCH_IMMUTABILITY_TRIGGERS, triggerNames(db))
                8 -> assertEquals(ALL_IMMUTABILITY_TRIGGERS, triggerNames(db))
            }
        }
    }

    @Test
    fun anUpgradedDatabaseAlsoRefusesToRewriteTheLedger() = runBlocking {
        createVersion6WithProgramZeroData()
        val db = openCurrent()
        try {
            db.research().insertLedgerEvents(listOf(ledgerEvent()))
            // The triggers must arrive through MIGRATION_6_7 too, not only
            // through the fresh-install callback.
            assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                db.openHelper.writableDatabase.execSQL("DELETE FROM research_ledger_events")
            }
            assertEquals(1, db.research().ledgerEventCount())
        } finally {
            db.close()
        }
    }

    private companion object {
        private const val KEY_COLUMN = "`key`"

        /** The exact DDL version 6 shipped. */
        private val PROGRAM_ZERO_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS held_notifications (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, packageName TEXT NOT NULL, " +
                "appLabel TEXT NOT NULL, title TEXT NOT NULL, text TEXT NOT NULL, " +
                "postedAt INTEGER NOT NULL, releasedAt INTEGER)",
            "CREATE TABLE IF NOT EXISTS pulse_results (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, takenAt INTEGER NOT NULL, " +
                "score INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS safety_plan (" +
                "id INTEGER NOT NULL, warningSigns TEXT NOT NULL, copingSteps TEXT NOT NULL, " +
                "distractions TEXT NOT NULL, reasonsForLiving TEXT NOT NULL, " +
                "environmentSafety TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))",
            "CREATE TABLE IF NOT EXISTS crisis_contacts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                "phone TEXT NOT NULL, isProfessional INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS journal_entries (" +
                "id TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, " +
                "localDate TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, " +
                "kind TEXT NOT NULL, sourceDeviceId TEXT NOT NULL, deletedAt INTEGER, PRIMARY KEY(id))",
            "CREATE INDEX IF NOT EXISTS index_journal_entries_localDate ON journal_entries(localDate)",
            "CREATE INDEX IF NOT EXISTS index_journal_entries_createdAt ON journal_entries(createdAt)",
            "CREATE TABLE IF NOT EXISTS journal_context (" +
                "id TEXT NOT NULL, entryId TEXT NOT NULL, recordType TEXT NOT NULL, " +
                KEY_COLUMN + " TEXT NOT NULL, value TEXT NOT NULL, sourceStart INTEGER, " +
                "sourceEnd INTEGER, confidence REAL NOT NULL, extractorVersion TEXT NOT NULL, " +
                "createdAt INTEGER NOT NULL, PRIMARY KEY(id), " +
                "FOREIGN KEY(entryId) REFERENCES journal_entries(id) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)",
            "CREATE INDEX IF NOT EXISTS index_journal_context_entryId ON journal_context(entryId)",
            "CREATE INDEX IF NOT EXISTS index_journal_context_recordType ON journal_context(recordType)",
            "CREATE TABLE IF NOT EXISTS morning_measures (" +
                "id TEXT NOT NULL, localDate TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                "updatedAt INTEGER NOT NULL, mood INTEGER NOT NULL, anxiety INTEGER NOT NULL, " +
                "angerUrge INTEGER NOT NULL, energyFunction INTEGER NOT NULL, " +
                "sleepQuality INTEGER NOT NULL, instrumentVersion TEXT NOT NULL, " +
                "sourceDeviceId TEXT NOT NULL, PRIMARY KEY(id))",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_morning_measures_localDate " +
                "ON morning_measures(localDate)",
            "CREATE TABLE IF NOT EXISTS continuity_changes (" +
                "id TEXT NOT NULL, entityType TEXT NOT NULL, entityId TEXT NOT NULL, " +
                "operation TEXT NOT NULL, occurredAt INTEGER NOT NULL, " +
                "acknowledgedSnapshotId TEXT, PRIMARY KEY(id))",
            "CREATE INDEX IF NOT EXISTS index_continuity_changes_occurredAt " +
                "ON continuity_changes(occurredAt)",
            "CREATE INDEX IF NOT EXISTS index_continuity_changes_acknowledgedSnapshotId " +
                "ON continuity_changes(acknowledgedSnapshotId)",
        )

        /** One row in each Program 0 research table, so the upgrade has something to lose. */
        private val PROGRAM_ZERO_ROWS = listOf(
            "INSERT INTO journal_entries " +
                "(id, createdAt, updatedAt, localDate, title, body, kind, sourceDeviceId, deletedAt) " +
                "VALUES ('entry-1', 1000, 1000, '2026-08-28', 'A day', 'Original words', " +
                "'DAILY', 'device-a', NULL)",
            "INSERT INTO journal_context " +
                "(id, entryId, recordType, " + KEY_COLUMN + ", value, sourceStart, sourceEnd, " +
                "confidence, extractorVersion, createdAt) " +
                "VALUES ('context-1', 'entry-1', 'FACT', 'word_count', '2', NULL, NULL, 1.0, " +
                "'structural-v1', 1000)",
            "INSERT INTO morning_measures " +
                "(id, localDate, createdAt, updatedAt, mood, anxiety, angerUrge, energyFunction, " +
                "sleepQuality, instrumentVersion, sourceDeviceId) " +
                "VALUES ('measure-1', '2026-08-28', 900, 900, 3, 2, 1, 4, 3, 'morning-v1', 'device-a')",
            "INSERT INTO continuity_changes " +
                "(id, entityType, entityId, operation, occurredAt, acknowledgedSnapshotId) " +
                "VALUES ('change-1', 'JOURNAL_ENTRY', 'entry-1', 'CREATE', 1000, NULL)",
        )

        private val RESEARCH_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS research_ledger_events (id TEXT NOT NULL, sequence INTEGER NOT NULL, kind TEXT NOT NULL, occurredAt INTEGER NOT NULL, recordedAt INTEGER NOT NULL, localDate TEXT NOT NULL, studyPhaseId TEXT NOT NULL, sourceDeviceId TEXT NOT NULL, note TEXT NOT NULL, payloadJson TEXT NOT NULL, previousEventHash TEXT NOT NULL, eventHash TEXT NOT NULL, PRIMARY KEY(id))",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_research_ledger_events_sequence ON research_ledger_events(sequence)",
            "CREATE INDEX IF NOT EXISTS index_research_ledger_events_recordedAt ON research_ledger_events(recordedAt)",
            "CREATE INDEX IF NOT EXISTS index_research_ledger_events_kind ON research_ledger_events(kind)",
            "CREATE INDEX IF NOT EXISTS index_research_ledger_events_studyPhaseId ON research_ledger_events(studyPhaseId)",
            "CREATE INDEX IF NOT EXISTS index_research_ledger_events_localDate ON research_ledger_events(localDate)",
            "CREATE TABLE IF NOT EXISTS study_phases (id TEXT NOT NULL, ordinal INTEGER NOT NULL, startedAt INTEGER NOT NULL, reason TEXT NOT NULL, appVersionCode INTEGER NOT NULL, appVersionName TEXT NOT NULL, protocolCatalogSha256 TEXT NOT NULL, ruleSetVersion TEXT NOT NULL, modelSetVersion TEXT NOT NULL, transformationSetVersion TEXT NOT NULL, missingDataPolicyVersion TEXT NOT NULL, instrumentVersion TEXT NOT NULL, dictionaryVersion TEXT NOT NULL, sourceDeviceId TEXT NOT NULL, PRIMARY KEY(id))",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_study_phases_ordinal ON study_phases(ordinal)",
            "CREATE INDEX IF NOT EXISTS index_study_phases_startedAt ON study_phases(startedAt)",
        )

        private val RESEARCH_IMMUTABILITY_TRIGGERS = listOf(
            "research_ledger_events_no_delete",
            "research_ledger_events_no_update",
            "study_phases_no_delete",
            "study_phases_no_update",
        )

        private val ALL_IMMUTABILITY_TRIGGERS = listOf(
            "passive_baseline_segments_no_delete",
            "passive_baseline_segments_no_update",
            "passive_daily_revisions_no_delete",
            "passive_daily_revisions_no_update",
            "passive_observation_decisions_no_delete",
            "passive_observation_decisions_no_update",
            "passive_pipeline_runs_no_delete",
            "passive_pipeline_runs_no_update",
            "passive_raw_provenance_no_delete",
            "passive_raw_provenance_no_update",
            "passive_source_lags_no_delete",
            "passive_source_lags_no_update",
            "passive_source_reads_no_delete",
            "passive_source_reads_no_update",
            "passive_window_revisions_no_delete",
            "passive_window_revisions_no_update",
            "research_ledger_events_no_delete",
            "research_ledger_events_no_update",
            "study_phases_no_delete",
            "study_phases_no_update",
        )
    }
}
