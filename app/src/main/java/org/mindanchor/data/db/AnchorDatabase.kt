package org.mindanchor.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * A notification we held back for the next batch. This journal is the
 * batcher's safety net: even if posting the digest fails, nothing is ever
 * lost — the full record stays here until the user clears it.
 */
@Entity(tableName = "held_notifications")
data class HeldNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val releasedAt: Long? = null,
)

@Dao
interface HeldNotificationDao {

    @Insert
    suspend fun insert(notification: HeldNotification)

    @Query("SELECT * FROM held_notifications WHERE releasedAt IS NULL ORDER BY postedAt DESC")
    suspend fun pending(): List<HeldNotification>

    @Query("SELECT COUNT(*) FROM held_notifications WHERE releasedAt IS NULL")
    fun pendingCount(): Flow<Int>

    /**
     * The number of held notifications that have been
     * released since the [since] epoch-millis timestamp.
     * The home-screen "notification diet" card uses this
     * with `since = now - 7 days` to report the user's
     * weekly batch count, multiplied by the Mark 2005
     * 23-minute attention-recovery cost to estimate the
     * attention saved (citations in the home card KDoc).
     *
     * Released notifications stay in the table until
     * [clearReleased] is called by the digest UI, so this
     * query is the right basis for the analytics — no
     * separate "demote log" table is needed. A user who
     * clears the digest loses the historical record by
     * design, the same way a person who clears a paper
     * notebook loses the history.
     */
    @Query("SELECT COUNT(*) FROM held_notifications WHERE releasedAt IS NOT NULL AND releasedAt >= :since")
    fun releasedCountSince(since: Long): Flow<Int>

    @Query("SELECT * FROM held_notifications ORDER BY postedAt DESC LIMIT 300")
    fun journal(): Flow<List<HeldNotification>>

    @Query("UPDATE held_notifications SET releasedAt = :releasedAt WHERE releasedAt IS NULL")
    suspend fun markAllReleased(releasedAt: Long)

    @Query("DELETE FROM held_notifications WHERE releasedAt IS NOT NULL")
    suspend fun clearReleased()

    /**
     * v0.30+ (spec Phase 2) — auto-prune held
     * notifications older than the [cutoff]
     * epoch-millis timestamp. Called from
     * [org.mindanchor.notifications.AnchorNotificationListenerService.onListenerConnected]
     * with the cutoff derived from the user's
     * `heldRetentionDays` setting. The DELETE
     * returns the number of rows removed so the
     * caller can log the prune without a separate
     * count query.
     */
    @Query("DELETE FROM held_notifications WHERE postedAt < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long): Int
}

/** One completed WHO-5 wellbeing pulse (score 0–100). */
@Entity(tableName = "pulse_results")
data class PulseResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val takenAt: Long,
    val score: Int,
)

@Dao
interface PulseDao {

    @Insert
    suspend fun insert(result: PulseResult)

    @Query("SELECT * FROM pulse_results ORDER BY takenAt DESC LIMIT 30")
    fun history(): Flow<List<PulseResult>>

    /**
     * The most recent pulse, for re-arming the fortnightly reminder after
     * a reboot. One-shot rather than a Flow: the caller is a broadcast
     * receiver with a few seconds to live, not a screen.
     */
    @Query("SELECT * FROM pulse_results ORDER BY takenAt DESC LIMIT 1")
    suspend fun latest(): PulseResult?
}

/**
 * A safety plan in the Stanley & Brown sense: the steps a person writes for
 * themselves, while calm, to be read when they are not. Kept on the device
 * and nowhere else.
 */
@Entity(tableName = "safety_plan")
data class SafetyPlan(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val warningSigns: String = "",
    val copingSteps: String = "",
    val distractions: String = "",
    val reasonsForLiving: String = "",
    val environmentSafety: String = "",
    val updatedAt: Long = 0L,
) {
    val isEmpty: Boolean
        get() = listOf(
            warningSigns, copingSteps, distractions, reasonsForLiving, environmentSafety,
        ).all { it.isBlank() }

    companion object {
        const val SINGLETON_ID = 1
    }
}

/** Someone this person has chosen to be reachable in a crisis. */
@Entity(tableName = "crisis_contacts")
data class CrisisContact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val isProfessional: Boolean = false,
)

@Dao
interface SafetyDao {

    @Query("SELECT * FROM safety_plan WHERE id = ${SafetyPlan.SINGLETON_ID}")
    fun plan(): Flow<SafetyPlan?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlan(plan: SafetyPlan)

    @Query("SELECT * FROM crisis_contacts ORDER BY isProfessional, name")
    fun contacts(): Flow<List<CrisisContact>>

    @Query("SELECT * FROM crisis_contacts")
    suspend fun contactsNow(): List<CrisisContact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addContact(contact: CrisisContact)

    @Delete
    suspend fun removeContact(contact: CrisisContact)
}

@Database(
    entities = [
        HeldNotification::class,
        PulseResult::class,
        SafetyPlan::class,
        CrisisContact::class,
        JournalEntryEntity::class,
        JournalContextEntity::class,
        MorningMeasureEntity::class,
        ContinuityChangeEntity::class,
        ResearchLedgerEventEntity::class,
        StudyPhaseEntity::class,
    ],
    // v7 (Program 1): the append-only research ledger and
    // study phases. See MIGRATION_6_7 for what the upgrade
    // does and MIGRATION_4_5 for the v4/v5 tier history.
    version = 7,
    exportSchema = true,
)
abstract class AnchorDatabase : RoomDatabase() {

    abstract fun heldNotifications(): HeldNotificationDao

    abstract fun pulses(): PulseDao

    abstract fun safety(): SafetyDao

    abstract fun journal(): JournalDao

    abstract fun research(): ResearchDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pulse_results (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "takenAt INTEGER NOT NULL, " +
                        "score INTEGER NOT NULL)",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE held_notifications " +
                        "ADD COLUMN tier TEXT NOT NULL DEFAULT 'MACHINE'",
                )
            }
        }

        // v0.70.x (Tier 2 audit): on-device DBs from
        // v0.69.x have a `tier` column on held_notifications
        // that v0.70.0 dropped. The column is a denormalised
        // copy of a key in NotificationPrefs and is safe to
        // drop. SQLite ALTER TABLE supports DROP COLUMN from
        // 3.35.0 (Android 12+, Room 2.5+). We add the
        // migration in a way that tolerates older engines
        // (the 12-step re-create dance) so the upgrade
        // works on Android 11 too.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQLite ≥ 3.35 supports DROP COLUMN.
                val cursor = db.query("SELECT sqlite_version()")
                val version = cursor.use { if (it.moveToFirst()) it.getString(0) else "0" }
                cursor.close()
                val parts = version.split(".").mapNotNull { it.toIntOrNull() }
                val has = parts.size >= 2 && (parts[0] > 3 || (parts[0] == 3 && parts[1] >= 35))
                if (has) {
                    db.execSQL("ALTER TABLE held_notifications DROP COLUMN tier")
                } else {
                    // 12-step rename-create-copy dance for older engines.
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS held_notifications_new (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "packageName TEXT NOT NULL, " +
                            "appLabel TEXT NOT NULL, " +
                            "title TEXT NOT NULL, " +
                            "text TEXT NOT NULL, " +
                            "postedAt INTEGER NOT NULL, " +
                            "releasedAt INTEGER)",
                    )
                    db.execSQL(
                        "INSERT INTO held_notifications_new (id, packageName, appLabel, title, text, postedAt, releasedAt) " +
                            "SELECT id, packageName, appLabel, title, text, postedAt, releasedAt FROM held_notifications",
                    )
                    db.execSQL("DROP TABLE held_notifications")
                    db.execSQL("ALTER TABLE held_notifications_new RENAME TO held_notifications")
                }
            }
        }

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

        // Program 1 (Task 7): the immutable research ledger and the
        // append-only study phases. Purely additive - two CREATE TABLE
        // statements, their indices, and four triggers. No Program 0
        // column is dropped, renamed, or retyped, and no existing row is
        // read or written, so an upgrade cannot lose anything.
        //
        // The triggers are what make "immutable" a property of the
        // database rather than a claim about the code above it. Room does
        // not validate triggers, so they do not affect the schema identity
        // hash; they also are not part of Room's generated
        // createAllTables, which is why installResearchImmutability is
        // called both here and from the onCreate callback that a fresh
        // install takes.
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS research_ledger_events (" +
                        "id TEXT NOT NULL, sequence INTEGER NOT NULL, kind TEXT NOT NULL, " +
                        "occurredAt INTEGER NOT NULL, recordedAt INTEGER NOT NULL, localDate TEXT NOT NULL, " +
                        "studyPhaseId TEXT NOT NULL, sourceDeviceId TEXT NOT NULL, note TEXT NOT NULL, " +
                        "payloadJson TEXT NOT NULL, previousEventHash TEXT NOT NULL, " +
                        "eventHash TEXT NOT NULL, PRIMARY KEY(id))",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_research_ledger_events_sequence " +
                        "ON research_ledger_events(sequence)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_research_ledger_events_recordedAt " +
                        "ON research_ledger_events(recordedAt)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_research_ledger_events_kind " +
                        "ON research_ledger_events(kind)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_research_ledger_events_studyPhaseId " +
                        "ON research_ledger_events(studyPhaseId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_research_ledger_events_localDate " +
                        "ON research_ledger_events(localDate)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS study_phases (" +
                        "id TEXT NOT NULL, ordinal INTEGER NOT NULL, startedAt INTEGER NOT NULL, " +
                        "reason TEXT NOT NULL, appVersionCode INTEGER NOT NULL, " +
                        "appVersionName TEXT NOT NULL, protocolCatalogSha256 TEXT NOT NULL, " +
                        "ruleSetVersion TEXT NOT NULL, modelSetVersion TEXT NOT NULL, " +
                        "transformationSetVersion TEXT NOT NULL, missingDataPolicyVersion TEXT NOT NULL, " +
                        "instrumentVersion TEXT NOT NULL, dictionaryVersion TEXT NOT NULL, " +
                        "sourceDeviceId TEXT NOT NULL, PRIMARY KEY(id))",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_study_phases_ordinal ON study_phases(ordinal)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_study_phases_startedAt ON study_phases(startedAt)",
                )
                installResearchImmutability(db)
            }
        }

        /**
         * Installs the BEFORE UPDATE / BEFORE DELETE triggers that make the
         * two research tables append-only at the database level.
         *
         * Idempotent (IF NOT EXISTS), and called from both paths a
         * database can reach version 7 by: MIGRATION_6_7 for an upgrade,
         * and the onCreate callback for a fresh install, whose schema Room
         * generates from the entities and which therefore contains no
         * triggers of its own.
         */
        internal fun installResearchImmutability(db: SupportSQLiteDatabase) {
            listOf("research_ledger_events", "study_phases").forEach { table ->
                listOf("UPDATE", "DELETE").forEach { operation ->
                    db.execSQL(
                        "CREATE TRIGGER IF NOT EXISTS ${table}_no_${operation.lowercase()} " +
                            "BEFORE $operation ON $table " +
                            "BEGIN SELECT RAISE(ABORT, '$table is append-only'); END",
                    )
                }
            }
        }

        /** Exposed so instrumented tests can walk an old database forward. */
        fun migrations(): Array<Migration> =
            arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)

        /**
         * The callback **every** [AnchorDatabase] builder must add — use
         * [withResearchImmutability] rather than referencing this
         * directly, and see `ResearchBuilderCallbackTest`, which fails the
         * build if a builder anywhere in this repository forgets.
         *
         * Two hooks, for two different holes:
         *
         *  - `onCreate`: a fresh install never runs MIGRATION_6_7, and
         *    Room's generated `createAllTables` carries no triggers, so
         *    without this a brand-new database would have the research
         *    tables and none of their immutability.
         *  - `onOpen`: self-healing. A database created by a build that
         *    lacked this callback would otherwise stay mutable forever,
         *    because `onCreate` never runs twice and MIGRATION_6_7 never
         *    runs on a database already at version 7. The statements are
         *    `IF NOT EXISTS`, so re-running them costs nothing.
         *
         * `onOpen` also turns on recursive triggers. Without it, SQLite
         * performs `INSERT OR REPLACE`'s implicit delete *without* firing
         * DELETE triggers — so a stray `OnConflictStrategy.REPLACE` would
         * silently overwrite an immutable, hash-chained row with no error
         * at all, which is exactly the outcome the triggers exist to
         * prevent.
         *
         * That pragma is connection-scoped and Android's connection pool
         * does not replay custom pragmas when it recreates a connection,
         * so treat it as defence in depth. The primary guarantee is
         * `ResearchDao` declaring no `REPLACE` at all, checked by
         * `ResearchDaoAppendOnlyTest` against this repository's source.
         */
        internal val researchImmutabilityCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                installResearchImmutability(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA recursive_triggers = ON")
                installResearchImmutability(db)
            }
        }

        @Volatile
        private var instance: AnchorDatabase? = null

        fun get(context: Context): AnchorDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnchorDatabase::class.java,
                    "mindanchor.db",
                ).addMigrations(*migrations())
                    .withResearchImmutability()
                    .build()
                    .also { instance = it }
            }
    }
}

/**
 * Adds the research-immutability callback. **Every** [AnchorDatabase]
 * builder in this repository must call it — production and test alike.
 *
 * A test database without it has `research_ledger_events` and
 * `study_phases` that are not append-only, which would let a restore or
 * merge path quietly rewrite the ledger and let every test still pass.
 * `ResearchBuilderCallbackTest` reads this repository's own source and
 * fails if a builder forgets.
 */
internal fun <T : AnchorDatabase> RoomDatabase.Builder<T>.withResearchImmutability(): RoomDatabase.Builder<T> =
    addCallback(AnchorDatabase.researchImmutabilityCallback)
