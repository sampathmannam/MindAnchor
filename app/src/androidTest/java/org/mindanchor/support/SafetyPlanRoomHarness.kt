package org.mindanchor.support

import android.content.Context
import androidx.room.Room
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.SafetyDao
import org.mindanchor.data.db.withResearchImmutability

internal class SafetyPlanRoomHarness(context: Context) : AutoCloseable {
    private val databaseName = "support-plan-${UUID.randomUUID()}.db"
    private val appContext = context.applicationContext
    internal val transactionExecutor = Executors.newSingleThreadExecutor()
    internal val queryExecutor = Executors.newSingleThreadExecutor()
    internal val database = Room.databaseBuilder(appContext, AnchorDatabase::class.java, databaseName)
        .setTransactionExecutor(transactionExecutor)
        .setQueryExecutor(queryExecutor)
        .withResearchImmutability()
        .build()
    internal val dao: SafetyDao = database.safety()

    fun installWriteCounter() {
        val sql = database.openHelper.writableDatabase
        sql.execSQL("CREATE TABLE support_test_safety_writes (count INTEGER NOT NULL)")
        sql.execSQL("INSERT INTO support_test_safety_writes VALUES (0)")
        sql.execSQL(
            "CREATE TRIGGER support_test_count_safety_plan AFTER INSERT ON safety_plan " +
                "BEGIN UPDATE support_test_safety_writes SET count = count + 1; END",
        )
    }

    fun installAbortInsertTrigger() = database.openHelper.writableDatabase.execSQL(
        "CREATE TRIGGER support_test_abort_safety_plan BEFORE INSERT ON safety_plan " +
            "BEGIN SELECT RAISE(ABORT, 'support test abort'); END",
    )

    fun installIgnoreInsertTrigger() = database.openHelper.writableDatabase.execSQL(
        "CREATE TRIGGER support_test_ignore_safety_plan BEFORE INSERT ON safety_plan " +
            "BEGIN SELECT RAISE(IGNORE); END",
    )

    fun writeCount(): Int = database.openHelper.readableDatabase
        .query("SELECT count FROM support_test_safety_writes")
        .use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    fun drainTransactions() {
        val drained = CountDownLatch(1)
        transactionExecutor.execute(drained::countDown)
        check(drained.await(10, TimeUnit.SECONDS)) { "transaction executor did not drain" }
    }

    override fun close() {
        database.close()
        transactionExecutor.shutdownNow()
        queryExecutor.shutdownNow()
        appContext.deleteDatabase(databaseName)
    }
}
