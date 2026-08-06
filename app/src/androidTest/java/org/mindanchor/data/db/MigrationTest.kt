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

    private fun openCurrent(): AnchorDatabase =
        Room.databaseBuilder(context, AnchorDatabase::class.java, dbName)
            .addMigrations(*AnchorDatabase.migrations())
            .build()

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
}
