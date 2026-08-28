package org.mindanchor.journal

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.letters.JournalStore
import org.mindanchor.research.MorningMeasureRepository
import org.mindanchor.ui.MindAnchorTheme

/**
 * Hosts the Task 6 Journal experience (Today / Entries / Patterns).
 *
 * A standalone activity, deliberately not folded into
 * [org.mindanchor.launcher.LauncherRoot] / `HomeActivity`: writing is a
 * single concern with its own full-screen surface (same rationale as
 * [org.mindanchor.model.NoteActivity]), and a crash while writing must
 * never take the dependable launcher escape surface down with it.
 *
 * Back is left to the system default (finish when there is nothing else on
 * the task's back stack) — nothing here overrides it.
 */
class JournalActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val application = applicationContext as Application
        val database = AnchorDatabase.get(application)
        val deviceIdentity = DeviceIdentityStore(application)
        val extractor = StructuralContextExtractor()
        val migrationPrefs = JournalMigrationPrefs(application)

        // This is the one place in the whole plan the one-time legacy
        // import (Task 4's JournalLegacyImporter) actually gets wired up
        // to run — see JournalViewModel's init block, which calls this
        // fire-and-forget on construction.
        val legacyImporter = JournalLegacyImporter(
            journalStore = JournalStore(application),
            database = database,
            migrationPrefs = migrationPrefs,
            extractor = extractor,
        )

        val viewModel = JournalViewModel(
            journalRepository = JournalRepository(database, deviceIdentity, extractor),
            morningMeasureRepository = MorningMeasureRepository(database, deviceIdentity),
            draftStore = JournalDraftStore(application),
            database = database,
            legacyImporter = legacyImporter,
        )

        setContent {
            MindAnchorTheme {
                JournalScreen(viewModel = viewModel, onBack = { finish() })
            }
        }
    }
}
