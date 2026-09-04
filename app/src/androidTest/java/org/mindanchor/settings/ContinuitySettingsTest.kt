package org.mindanchor.settings

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.continuity.ContinuityErrorCode
import org.mindanchor.continuity.ContinuityPrefs
import org.mindanchor.continuity.RestoreActivity
import org.mindanchor.continuity.crypto.RecoveryKey
import org.mindanchor.continuity.crypto.RecoveryKeyCodec
import org.mindanchor.continuity.crypto.RecoveryKeyStore
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.withResearchImmutability
import org.mindanchor.data.db.JournalEntryEntity
import org.mindanchor.ui.MindAnchorTheme

/**
 * Task 12: real-interaction Compose tests for the continuity settings
 * surface, driven on a real device/emulator against
 * [GoogleDriveBackupSettingsSectionContent] (the seam that accepts an
 * injected [ContinuitySettingsViewModel]). Uses an in-memory [AnchorDatabase]
 * and recording lambdas for the [org.mindanchor.continuity.ContinuityWorkScheduler]
 * calls — no real network, no real WorkManager, no real Google sign-in —
 * the same "fakes at the seams, real logic everywhere else" shape
 * [org.mindanchor.continuity.RestoreScreenTest] already established for
 * this package.
 */
@RunWith(AndroidJUnit4::class)
class ContinuitySettingsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var db: AnchorDatabase
    private lateinit var continuityPrefs: ContinuityPrefs
    private lateinit var recoveryKeyStore: RecoveryKeyStore

    private var requestCheckpointCalled = false
    private var ensureNightlyScheduledCalled = false
    private var cancelAllCalled = false
    private var lastLaunchedRestoreIntent: Intent? = null

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .withResearchImmutability()
            .build()
        continuityPrefs = ContinuityPrefs(context)
        recoveryKeyStore = RecoveryKeyStore.create(context)
        continuityPrefs.reset()
        recoveryKeyStore.clear()
        requestCheckpointCalled = false
        ensureNightlyScheduledCalled = false
        cancelAllCalled = false
        lastLaunchedRestoreIntent = null
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        db.close()
        continuityPrefs.reset()
        recoveryKeyStore.clear()
        Unit
    }

    private fun verifiedKey(): RecoveryKey {
        val key = RecoveryKeyCodec.generate()
        recoveryKeyStore.save(key)
        recoveryKeyStore.markVerified()
        return key
    }

    private fun launch(signedIn: Boolean = false): ContinuitySettingsViewModel {
        val viewModel = ContinuitySettingsViewModel(
            context = context,
            signedInEmail = MutableStateFlow(if (signedIn) "person@example.com" else null),
            continuityPrefs = continuityPrefs,
            recoveryKeyStore = recoveryKeyStore,
            database = db,
            requestCheckpoint = { requestCheckpointCalled = true },
            ensureNightlyScheduled = { ensureNightlyScheduledCalled = true },
            cancelAllWork = { cancelAllCalled = true },
            launchRestore = { _, intent -> lastLaunchedRestoreIntent = intent },
        )
        rule.setContent {
            MindAnchorTheme {
                GoogleDriveBackupSettingsSectionContent(viewModel = viewModel)
            }
        }
        rule.waitForIdle()
        return viewModel
    }

    private fun waitForCondition(timeoutMillis: Long = 15_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            if (predicate()) return
            Thread.sleep(100)
        }
        throw AssertionError("timed out waiting for condition")
    }

    private fun SemanticsNodeInteraction.text(): String =
        fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(separator = "") { it.text }

    // --- Gate: backup cannot be enabled before sign-in + a verified key ---

    @Test
    fun backupSwitchIsOffAndDisabledWhenSignedOut() {
        launch(signedIn = false)
        rule.onNodeWithTag("continuity_backup_switch").assertIsOff().assertIsNotEnabled()
    }

    @Test
    fun backupSwitchIsOffAndDisabledWhenSignedInButKeyIsNotVerified() {
        launch(signedIn = true)
        rule.onNodeWithTag("continuity_backup_switch").assertIsOff().assertIsNotEnabled()
    }

    @Test
    fun backupSwitchEnablesAndSchedulesWorkOnlyOnceSignedInAndVerified() {
        verifiedKey()
        launch(signedIn = true)
        rule.onNodeWithTag("continuity_backup_switch").assertIsOff()
        rule.onNodeWithTag("continuity_backup_switch").performClick()

        waitForCondition { runBlocking { continuityPrefs.backupEnabled.first() } }
        waitForCondition { requestCheckpointCalled }
        waitForCondition { ensureNightlyScheduledCalled }
        rule.onNodeWithTag("continuity_backup_switch").assertIsOn()
    }

    // --- Recovery key: shown once, verified only by full re-entry ---

    @Test
    fun generatedKeyIsShownOnceAndRequiresTheFullKeyToVerify() {
        launch(signedIn = true)

        rule.onNodeWithTag("continuity_recovery_key_generate_button").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("continuity_recovery_key_generated_value").assertIsDisplayed()
        val humanKey = rule.onNodeWithTag("continuity_recovery_key_generated_value").text()
        assert(humanKey.startsWith("MA1-")) { "expected a human-readable MA1- key, got '$humanKey'" }

        // Dismiss — the once-shown key must not reappear.
        rule.onNodeWithTag("continuity_recovery_key_dismiss_button").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithTag("continuity_recovery_key_generated_value")
            .fetchSemanticsNodes()
            .let { nodes -> assert(nodes.isEmpty()) { "the generated key must not be shown again after dismiss" } }
        rule.onNodeWithTag("continuity_recovery_key_status").assertIsDisplayed()

        // A wrong retype is rejected, and does NOT verify the key.
        rule.onNodeWithTag("continuity_recovery_key_generate_button").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("continuity_recovery_key_verify_field").performTextInput("this is not a valid recovery key")
        rule.onNodeWithTag("continuity_recovery_key_verify_button").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("continuity_recovery_key_verify_error").assertIsDisplayed()
        assert(recoveryKeyStore.isVerified().not()) { "a mismatched retype must never verify the key" }

        // The full, correct key does verify it.
        val freshKey = rule.onNodeWithTag("continuity_recovery_key_generated_value").text()
        rule.onNodeWithTag("continuity_recovery_key_verify_field").performTextClearance()
        rule.onNodeWithTag("continuity_recovery_key_verify_field").performTextInput(freshKey)
        rule.onNodeWithTag("continuity_recovery_key_verify_button").performClick()
        rule.waitForIdle()
        assert(recoveryKeyStore.isVerified()) { "retyping the full, correct key must verify it" }
        rule.onNodeWithTag("continuity_recovery_key_status").assertIsDisplayed()
    }

    // --- Disabling backup cancels work, never deletes data ---

    @Test
    fun disablingBackupCancelsScheduledWorkWithoutDeletingLocalData() = runBlocking {
        db.journal().upsertEntries(
            listOf(
                JournalEntryEntity(
                    id = "e-1", createdAt = 1_000, updatedAt = 1_000, localDate = "2026-01-01",
                    title = "t", body = "b", kind = "DAILY", sourceDeviceId = "dev-a", deletedAt = null,
                ),
            ),
        )
        verifiedKey()
        val viewModel = launch(signedIn = true)
        viewModel.setBackupEnabled(true, signedIn = true)
        waitForCondition { runBlocking { continuityPrefs.backupEnabled.first() } }

        rule.onNodeWithTag("continuity_backup_switch").performClick()
        waitForCondition { cancelAllCalled }
        waitForCondition { runBlocking { !continuityPrefs.backupEnabled.first() } }

        assert(db.journal().entriesNow().size == 1) { "disabling backup must never delete local data" }
    }

    // --- Context extraction kill switch: flag flips, Journal writing unaffected ---

    @Test
    fun contextExtractionSwitchFlipsTheFlagWithoutAffectingJournalWriting() = runBlocking {
        launch(signedIn = false)
        rule.onNodeWithTag("continuity_context_extraction_switch").assertIsOn()

        rule.onNodeWithTag("continuity_context_extraction_switch").performClick()
        waitForCondition { runBlocking { !continuityPrefs.contextExtractionEnabled.first() } }

        // Journal writing itself has no dependency on the flag — the DAO
        // still accepts a write regardless of its value.
        db.journal().upsertEntries(
            listOf(
                JournalEntryEntity(
                    id = "e-2", createdAt = 2_000, updatedAt = 2_000, localDate = "2026-01-02",
                    title = "t2", body = "b2", kind = "DAILY", sourceDeviceId = "dev-a", deletedAt = null,
                ),
            ),
        )
        assert(db.journal().entriesNow().size == 1) { "Journal writing must be unaffected by the kill switch" }
    }

    // --- Back up now: schedules, never claims a completed backup ---

    @Test
    fun backUpNowSchedulesACheckpointAndShowsNonBackedUpConfirmation() {
        launch(signedIn = false)
        rule.onNodeWithTag("continuity_back_up_now_button").performClick()

        waitForCondition { requestCheckpointCalled }
        rule.onNodeWithTag("continuity_message").assertIsDisplayed()
        val message = rule.onNodeWithTag("continuity_message").text()
        assert(!message.lowercase().contains("backed up")) { "must never claim a scheduled checkpoint was 'backed up': '$message'" }
        assert(message == "Checkpoint requested.") { "expected the exact checkpoint-requested copy, got '$message'" }
    }

    // --- Restore launches RestoreActivity ---

    @Test
    fun restoreButtonLaunchesRestoreActivity() {
        launch(signedIn = false)
        rule.onNodeWithTag("continuity_restore_button").performClick()
        rule.waitForIdle()

        val launched = lastLaunchedRestoreIntent
        assert(launched != null) { "restore button must launch an Intent" }
        assert(launched?.component?.className == RestoreActivity::class.java.name) {
            "expected an Intent targeting RestoreActivity, got component=${launched?.component}"
        }
    }

    // --- Health copy: one exact string per BackupHealth variant ---

    @Test
    fun healthShowsPendingWhenBackupIsOff() {
        launch(signedIn = false)
        rule.onNodeWithTag("continuity_health_state").assertIsDisplayed()
        assert(rule.onNodeWithTag("continuity_health_state").text() == "Pending — no verified checkpoint yet.")
    }

    @Test
    fun healthShowsRecoveryKeyRequiredWhenBackupOnButKeyUnverified() = runBlocking {
        continuityPrefs.setBackupEnabled(true)
        launch(signedIn = true)
        assert(rule.onNodeWithTag("continuity_health_state").text() == "Recovery key required — automatic backup is off.")
    }

    @Test
    fun healthShowsNeedsSignInOnAuthError() = runBlocking {
        verifiedKey()
        continuityPrefs.setBackupEnabled(true)
        continuityPrefs.recordError(ContinuityErrorCode.AUTH)
        launch(signedIn = true)
        assert(rule.onNodeWithTag("continuity_health_state").text() == "Needs sign-in — nothing is retrying in the background.")
    }

    @Test
    fun healthShowsVerificationFailedWithoutImplyingDataLoss() = runBlocking {
        verifiedKey()
        continuityPrefs.setBackupEnabled(true)
        continuityPrefs.recordError(ContinuityErrorCode.VERIFY_FAILED)
        launch(signedIn = true)
        val text = rule.onNodeWithTag("continuity_health_state").text()
        assert(text == "Verification didn't match — your last verified backup is still safe.") { "got '$text'" }
    }

    @Test
    fun healthShowsVerifiedWithTheLastCheckpointTime() = runBlocking {
        verifiedKey()
        continuityPrefs.setBackupEnabled(true)
        continuityPrefs.recordCheckpointVerified(at = 1_700_000_000_000L, snapshotId = "snap-1", contentHash = "hash-1")
        launch(signedIn = true)
        val text = rule.onNodeWithTag("continuity_health_state").text()
        assert(text.startsWith("Verified ")) { "got '$text'" }
    }
}
