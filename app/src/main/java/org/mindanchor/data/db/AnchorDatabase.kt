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
    ],
    // v0.70.x (Tier 2 audit finding): the on-device DB
    // created by v0.69.x sits at version 4 with a 'tier'
    // column on held_notifications (the v0.69.x Phase-2
    // G-5 retention tier field). v0.70.0 ships without tier
    // (the tier metadata moved to a separate DataStore key
    // when held_notifications was collapsed from a single
    // row per (tier, packet) to a single row per packet).
    // Bumping to version 5 + adding MIGRATION_4_5 to drop
    // the now-orphan column resolves the on-device crash
    // `Room cannot verify the data integrity. Expected
    // identity hash: 1fc7ea00..., found: 5e78fa6f...`. The
    // new installation starts at v5 (the column never
    // existed); the upgrade installation runs the drop
    // column migration in-place. The tier data is in the
    // DataStore key (NotificationPrefs.tier), so the column
    // drop is safe — it was just denormalised data.
    version = 6,
    exportSchema = true,
)
abstract class AnchorDatabase : RoomDatabase() {

    abstract fun heldNotifications(): HeldNotificationDao

    abstract fun pulses(): PulseDao

    abstract fun safety(): SafetyDao

    abstract fun journal(): JournalDao

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

        /** Exposed so instrumented tests can walk an old database forward. */
        fun migrations(): Array<Migration> =
            arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

        @Volatile
        private var instance: AnchorDatabase? = null

        fun get(context: Context): AnchorDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnchorDatabase::class.java,
                    "mindanchor.db",
                ).addMigrations(*migrations()).build().also { instance = it }
            }
    }
}
