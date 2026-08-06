package org.mindanchor.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
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
}

@Database(
    entities = [HeldNotification::class, PulseResult::class],
    version = 2,
    exportSchema = false,
)
abstract class AnchorDatabase : RoomDatabase() {

    abstract fun heldNotifications(): HeldNotificationDao

    abstract fun pulses(): PulseDao

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

        @Volatile
        private var instance: AnchorDatabase? = null

        fun get(context: Context): AnchorDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnchorDatabase::class.java,
                    "mindanchor.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
