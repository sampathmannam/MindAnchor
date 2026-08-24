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
    ],
    version = 3,
    exportSchema = false,
)
abstract class AnchorDatabase : RoomDatabase() {

    abstract fun heldNotifications(): HeldNotificationDao

    abstract fun pulses(): PulseDao

    abstract fun safety(): SafetyDao

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

        /** Exposed so instrumented tests can walk an old database forward. */
        fun migrations(): Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

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
