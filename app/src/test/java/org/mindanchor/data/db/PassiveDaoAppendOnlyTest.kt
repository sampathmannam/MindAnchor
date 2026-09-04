package org.mindanchor.data.db

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassiveDaoAppendOnlyTest {

    private val daoSource = File("src/main/java/org/mindanchor/data/db/PassiveDao.kt")
    private val entitySource = File("src/main/java/org/mindanchor/data/db/PassiveEntities.kt")
    private val databaseSource = File("src/main/java/org/mindanchor/data/db/AnchorDatabase.kt")
    private val schema = File("schemas/org.mindanchor.data.db.AnchorDatabase/8.json")

    @Test
    fun `operational history DAO is insert-only except raw value pruning`() {
        val source = daoSource.readText()
        assertFalse(Regex("@(Update|Delete)\\b").containsMatchIn(source))
        assertFalse(Regex("UPDATE\\s+passive_", RegexOption.IGNORE_CASE).containsMatchIn(source))
        assertEquals(1, Regex("DELETE FROM passive_raw_samples").findAll(source).count())
        assertFalse(source.contains("OnConflictStrategy.REPLACE"))
        assertEquals(9, Regex("@Insert\\(onConflict = OnConflictStrategy.IGNORE\\)").findAll(source).count())
    }

    @Test
    fun `schema declares the nine exact passive tables and only raw samples cascade`() {
        val source = entitySource.readText()
        val tables = Regex("tableName = \\\"(passive_[a-z_]+)\\\"")
            .findAll(source)
            .map { it.groupValues[1] }
            .toSet()
        assertEquals(EXACT_TABLES, tables)
        assertEquals(1, Regex("onDelete = ForeignKey.CASCADE").findAll(source).count())
        assertTrue(source.contains("childColumns = [\"provenanceId\"]"))
    }

    @Test
    fun `revision content indexes preserve append history and DAO ties follow rowid`() {
        val entities = entitySource.readText()
        val migration = databaseSource.readText()
        val generatedSchema = schema.readText()
        val compoundIndexNames = listOf(
            "index_passive_window_revisions_windowStart_contentHash",
            "index_passive_daily_revisions_localDate_contentHash",
            "index_passive_observation_decisions_localDate_contentHash",
        )

        assertEquals(0, Regex("Index\\(value = \\[.*contentHash.*unique = true").findAll(entities).count())
        compoundIndexNames.forEach { name ->
            assertFalse(migration.contains("CREATE UNIQUE INDEX IF NOT EXISTS $name"))
            assertTrue(migration.contains("CREATE INDEX IF NOT EXISTS $name"))
            val indexJson = generatedSchema.substringAfter("\"name\": \"$name\"").substringBefore("}")
            assertTrue(indexJson.contains("\"unique\": false"))
        }
        assertFalse(daoSource.readText().contains("asOfTime DESC, id DESC"))
        assertTrue(daoSource.readText().contains("asOfTime DESC, rowid DESC"))
    }

    private companion object {
        val EXACT_TABLES = setOf(
            "passive_raw_provenance",
            "passive_raw_samples",
            "passive_source_reads",
            "passive_source_lags",
            "passive_baseline_segments",
            "passive_pipeline_runs",
            "passive_window_revisions",
            "passive_daily_revisions",
            "passive_observation_decisions",
        )
    }
}
