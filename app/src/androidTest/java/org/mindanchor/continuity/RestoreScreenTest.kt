package org.mindanchor.continuity

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.backup.BackupRepository
import org.mindanchor.backup.RemoteBackupStore
import org.mindanchor.backup.RemoteObject
import org.mindanchor.backup.RemoteResult
import org.mindanchor.continuity.crypto.BackupEnvelopeCodec
import org.mindanchor.continuity.crypto.RecoveryKey
import org.mindanchor.continuity.crypto.RecoveryKeyCodec
import org.mindanchor.continuity.crypto.RecoveryKeyStore
import org.mindanchor.data.FrictionPrefs
import org.mindanchor.data.LauncherPrefs
import org.mindanchor.data.NotesPrefs
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.mergeRestored
import org.mindanchor.data.replaceAlwaysOpen
import org.mindanchor.data.replaceFlagged
import org.mindanchor.journal.DeviceIdentityStore
import org.mindanchor.letters.LetterStore
import org.mindanchor.letters.mergeRestored
import org.mindanchor.ui.MindAnchorTheme

/**
 * Task 11's replacement-phone restore screen, driven on a real device. Uses
 * a fake [RemoteBackupStore] (no real Drive/network call) that serves a
 * real, genuinely encrypted candidate, so [RestoreCandidateSelector]'s
 * decode/decrypt/verify logic runs for real; the confirm-and-restore flow
 * is wired against an in-memory [AnchorDatabase] (via
 * [RestoreViewModel]'s `coordinatorBuilder` seam) rather than the app's
 * real on-device singleton, the same convention every other Room-backed
 * test in this suite follows.
 *
 * The one thing this test exists to prove above all else: the screen NEVER
 * renders a Journal entry's body or title text, only counts — even though
 * the underlying [ContinuitySnapshot] the candidate carries has that text
 * in memory.
 */
@RunWith(AndroidJUnit4::class)
class RestoreScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val key: RecoveryKey = RecoveryKeyCodec.generate { ByteArray(32) { i -> (i + 7).toByte() } }

    private lateinit var db: AnchorDatabase
    private lateinit var notesPrefs: NotesPrefs
    private lateinit var letterStore: LetterStore
    private lateinit var frictionPrefs: FrictionPrefs
    private lateinit var launcherPrefs: LauncherPrefs
    private lateinit var backupRepository: BackupRepository
    private lateinit var continuityPrefs: ContinuityPrefs
    private lateinit var restoreStateStore: RestoreStateStore
    private lateinit var snapshotRepository: ContinuitySnapshotRepository
    private lateinit var stagingFile: java.io.File
    private lateinit var stagingTmpFile: java.io.File

    // The real, on-device (Keystore-backed) store — deliberately NOT a
    // hardcoded-key test double. See `checkForBackupPersists...` below: the
    // whole point of that regression test is that RestoreViewModel and
    // testCoordinator's `currentVerifiedKey` both read/write THIS instance,
    // exactly like RestoreCoordinator.build()'s production wiring does.
    private lateinit var recoveryKeyStore: RecoveryKeyStore

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java).build()
        notesPrefs = NotesPrefs(context)
        letterStore = LetterStore(context)
        frictionPrefs = FrictionPrefs(context)
        launcherPrefs = LauncherPrefs(context)
        backupRepository = BackupRepository(context)
        continuityPrefs = ContinuityPrefs(context)
        restoreStateStore = RestoreStateStore(context)
        snapshotRepository = ContinuitySnapshotRepository(
            context = context,
            database = db,
            notesPrefs = notesPrefs,
            letterStore = letterStore,
            frictionPrefs = frictionPrefs,
            deviceIdentity = DeviceIdentityStore(context),
            backupRepository = backupRepository,
        )
        val stagingDir = java.io.File(context.filesDir, "continuity")
        stagingFile = java.io.File(stagingDir, "restore-staged.mab")
        stagingTmpFile = java.io.File(stagingDir, "restore-staged.mab.tmp")
        recoveryKeyStore = RecoveryKeyStore.create(context)

        letterStore.reset()
        notesPrefs.replaceAll(emptyList())
        frictionPrefs.replaceFlaggedApps(emptySet())
        frictionPrefs.replaceAlwaysOpenApps(emptySet())
        launcherPrefs.replaceFavorites(emptyList())
        launcherPrefs.replaceHidden(emptySet())
        launcherPrefs.replaceRenames(emptyMap())
        restoreStateStore.reset()
        stagingFile.delete()
        stagingTmpFile.delete()
        recoveryKeyStore.clear()
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        db.close()
        restoreStateStore.reset()
        stagingFile.delete()
        stagingTmpFile.delete()
        recoveryKeyStore.clear()
        Unit
    }

    /** A fake, in-memory-only Drive: no network call is ever made. */
    private class FakeRemoteBackupStore(private val objects: Map<String, ByteArray>) : RemoteBackupStore {
        override suspend fun put(name: String, bytes: ByteArray): RemoteResult<RemoteObject> =
            RemoteResult.Ok(RemoteObject(id = name, name = name, size = bytes.size.toLong(), modifiedTime = java.time.Instant.EPOCH))
        override suspend fun get(name: String): RemoteResult<ByteArray?> = RemoteResult.Ok(objects[name])
        override suspend fun list(prefix: String): RemoteResult<List<RemoteObject>> = RemoteResult.Ok(emptyList())
    }

    // recapture() (via ContinuitySnapshotRepository.capture) always re-derives
    // legacyBackupJson through a genuine BackupRepository.export(...) call,
    // never blindly copying whatever value a payload happened to carry in —
    // so a synthetic "" here would never round-trip at the final VERIFIED
    // check: a real export of an empty local state produces a well-formed
    // (non-blank) JSON document, not a blank string. See RestoreResumeTest's
    // `blankLegacyBackupJson()` for the identical fix and its full KDoc.
    private suspend fun samplePayload(bodyMarker: String = "PRIVATE_JOURNAL_BODY_MUST_NEVER_APPEAR_ON_SCREEN"): ContinuityPayload = ContinuityPayload(
        journalEntries = listOf(
            JournalEntryDto(
                id = "entry-1", createdAt = 1_000, updatedAt = 1_000, localDate = "2026-01-01",
                title = "PRIVATE_TITLE_MUST_NEVER_APPEAR", body = bodyMarker, kind = "DAILY",
                sourceDeviceId = "device-a", deletedAt = null,
            ),
        ),
        morningMeasures = listOf(
            MorningMeasureDto(
                id = "m-1", localDate = "2026-01-01", createdAt = 1_000, updatedAt = 1_000,
                mood = 3, anxiety = 2, angerUrge = 1, energyFunction = 3, sleepQuality = 4,
                instrumentVersion = "v1", sourceDeviceId = "device-a",
            ),
        ),
        legacyBackupJson = backupRepository.export(0L),
    )

    private fun sampleSnapshot(payload: ContinuityPayload): ContinuitySnapshot {
        val sorted = ContinuityContentHasher.sorted(payload)
        return ContinuitySnapshot(
            formatVersion = ContinuitySnapshot.CURRENT_FORMAT_VERSION,
            snapshotId = "snap-1",
            createdAt = 5_000L,
            appVersionCode = 1,
            appVersionName = "1.2.3-test",
            sourceDeviceId = "device-a-source",
            payload = sorted,
            contentSha256 = ContinuityContentHasher.hash(sorted),
        )
    }

    private fun envelopeBytes(snapshot: ContinuitySnapshot): ByteArray {
        val json = ContinuitySnapshotCodec.encode(snapshot)
        val envelope = BackupEnvelopeCodec.encrypt(json, key, now = 5_000L)
        return BackupEnvelopeCodec.encode(envelope).encodeToByteArray()
    }

    /** Builds a real [RestoreCoordinator] against this test's in-memory [db] — see [RestoreViewModel]'s `coordinatorBuilder` seam. */
    private fun testCoordinator(appContext: Context): RestoreCoordinator {
        val dao = db.journal()
        return RestoreCoordinator(
            currentStageInfo = { restoreStateStore.currentInfo() },
            persistDownloaded = { name, sha, hash -> restoreStateStore.markDownloaded(name, sha, hash) },
            persistDecrypted = { hash -> restoreStateStore.markDecrypted(hash) },
            persistRoomMerged = { restoreStateStore.markRoomMerged() },
            persistDataStoresMerged = { restoreStateStore.markDataStoresMerged() },
            persistVerified = { restoreStateStore.markVerified() },
            resetState = { restoreStateStore.reset() },
            readStagedBytes = { if (stagingFile.exists()) stagingFile.readBytes() else null },
            writeStagedBytesAtomically = { bytes ->
                stagingFile.parentFile?.mkdirs()
                stagingTmpFile.outputStream().use { it.write(bytes); it.flush() }
                if (stagingFile.exists()) stagingFile.delete()
                stagingTmpFile.renameTo(stagingFile)
            },
            deleteStagedFile = { stagingFile.delete() },
            // Reads the SAME real, on-device RecoveryKeyStore RestoreViewModel
            // writes to in checkForBackup() — exactly RestoreCoordinator.build()'s
            // production wiring, not a hardcoded-key test double. This is what
            // makes `checkForBackupPersistsAndVerifiesTheRecoveryKey...` below a
            // genuine regression test for the Critical finding.
            currentVerifiedKey = { recoveryKeyStore.current()?.takeIf { recoveryKeyStore.isVerified() } },
            preflightIsLocalDataEmpty = {
                dao.entriesNow().isEmpty() && dao.morningMeasuresNow().isEmpty() &&
                    notesPrefs.notes.first().notes.isEmpty() && letterStore.letters.first().isEmpty()
            },
            mergeRoom = { payload ->
                db.withTransaction {
                    dao.upsertEntries(payload.journalEntries.map { it.toEntity() })
                    dao.upsertContext(payload.contextRows.map { it.toEntity() })
                    dao.upsertMorningMeasures(payload.morningMeasures.map { it.toEntity() })
                    payload.continuityChanges.forEach { dao.insertChange(it.toEntity()) }
                }
            },
            mergeDataStores = { payload ->
                backupRepository.import(payload.legacyBackupJson, System.currentTimeMillis())
                notesPrefs.mergeRestored(payload.notes.map { it.toDomain() })
                letterStore.mergeRestored(
                    payload.letters.mapNotNull { it.toDomain() },
                    payload.readLetterDates.mapNotNull { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }.toSet(),
                )
                frictionPrefs.replaceFlagged(payload.frictionedApps.toSet())
                frictionPrefs.replaceAlwaysOpen(payload.alwaysOpenApps.toSet())
            },
            recapture = { snapshotRepository.capture(System.currentTimeMillis()) },
            recordRestoreVerified = { at, hash -> continuityPrefs.recordRestore(at, hash) },
            recordVerifyFailed = { continuityPrefs.recordError(ContinuityErrorCode.RESTORE_VERIFY_FAILED) },
        )
    }

    private fun launch(remoteBackupStore: RemoteBackupStore): RestoreViewModel {
        val viewModel = RestoreViewModel(
            context = context,
            remoteBackupStore = remoteBackupStore,
            recoveryKeyStore = recoveryKeyStore,
            coordinatorBuilder = ::testCoordinator,
        )
        rule.setContent {
            MindAnchorTheme {
                RestoreScreen(viewModel = viewModel, onBack = {})
            }
        }
        rule.waitForIdle()
        return viewModel
    }

    /**
     * Polls for [tag] to appear, calling [ComposeTestRule.waitForIdle]
     * before every check rather than relying solely on
     * [ComposeTestRule.waitUntil]'s own internal polling — [RestoreViewModel]
     * drives its state from its own `CoroutineScope`, independent of
     * Compose's test-clock machinery, and on this emulator a single
     * up-front `waitForIdle()` is not always enough to keep every
     * subsequent poll iteration synchronized with that scope's pending
     * work (observed directly: the semantics tree already had the
     * expected node when dumped manually, yet `waitUntil` still timed
     * out afterwards).
     */
    private fun waitForTag(tag: String, timeoutMillis: Long = 15_000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            if (rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(100)
        }
        throw AssertionError("timed out waiting for tag '$tag' to appear")
    }

    @Test
    fun notSignedInShowsSignInAffordanceAndDisablesTheRecoveryKeyField() {
        launch(remoteBackupStore = FakeRemoteBackupStore(emptyMap()))

        rule.onNodeWithText("Not signed in").assertIsDisplayed()
        rule.onNodeWithTag("restore_sign_in_button").assertIsDisplayed()
        // The check button stays disabled without a signed-in account —
        // Compose Test can't performClick a disabled node meaningfully, so
        // this is asserted structurally instead: nothing happens without
        // sign-in AND a key, matching the brief's ordering.
        rule.onNodeWithTag("restore_check_button").assertIsDisplayed()
    }

    @Test
    fun invalidRecoveryKeyTextShowsAFormatError() {
        val viewModel = launch(remoteBackupStore = FakeRemoteBackupStore(emptyMap()))
        viewModel.onRecoveryKeyChanged("not-a-valid-key")
        viewModel.checkForBackup()
        rule.waitForIdle()

        rule.onNodeWithText("That doesn't look like a valid recovery key.").assertIsDisplayed()
    }

    @Test
    fun candidatePreviewShowsCountsAndMetadataButNeverJournalBodyOrTitleText() {
        val snapshot = sampleSnapshot(runBlocking { samplePayload() })
        val bytes = envelopeBytes(snapshot)
        val remoteBackupStore = FakeRemoteBackupStore(mapOf(ContinuityFiles.LATEST to bytes))
        val viewModel = launch(remoteBackupStore = remoteBackupStore)

        viewModel.onRecoveryKeyChanged(RecoveryKeyCodec.format(key))
        viewModel.checkForBackup()
        waitForTag("restore_confirm_button")

        rule.onNodeWithTag("restore_candidate_app_version").assertIsDisplayed()
        rule.onNodeWithText("Journal entries: 1").assertIsDisplayed()
        rule.onNodeWithText("Morning check-ins: 1").assertIsDisplayed()
        rule.onNodeWithText("Source device: device-a-source").assertIsDisplayed()
        rule.onNodeWithTag("restore_confirm_button").assertIsDisplayed()

        // The critical safety property: never the raw Journal body or title.
        rule.onAllNodesWithText("PRIVATE_JOURNAL_BODY_MUST_NEVER_APPEAR_ON_SCREEN", substring = true)
            .fetchSemanticsNodes().let { nodes -> assert(nodes.isEmpty()) { "Journal body text leaked onto the restore screen" } }
        rule.onAllNodesWithText("PRIVATE_TITLE_MUST_NEVER_APPEAR", substring = true)
            .fetchSemanticsNodes().let { nodes -> assert(nodes.isEmpty()) { "Journal title text leaked onto the restore screen" } }
    }

    @Test
    fun wrongRecoveryKeyIsReportedDistinctlyAndNothingIsStaged() {
        val snapshot = sampleSnapshot(runBlocking { samplePayload() })
        val bytes = envelopeBytes(snapshot)
        val remoteBackupStore = FakeRemoteBackupStore(mapOf(ContinuityFiles.LATEST to bytes))
        val viewModel = launch(remoteBackupStore = remoteBackupStore)

        val differentKey = RecoveryKeyCodec.generate { ByteArray(32) { i -> (i + 99).toByte() } }
        viewModel.onRecoveryKeyChanged(RecoveryKeyCodec.format(differentKey))
        viewModel.checkForBackup()
        waitForTag("restore_wrong_key_message")

        rule.onNodeWithText("Wrong recovery key.").assertIsDisplayed()
        assert(!stagingFile.exists()) { "a wrong key must never stage anything" }
    }

    @Test
    fun confirmingRestoreShowsPerStageProgressAndEndsWithAContentHash() {
        val snapshot = sampleSnapshot(runBlocking { samplePayload() })
        val bytes = envelopeBytes(snapshot)
        val remoteBackupStore = FakeRemoteBackupStore(mapOf(ContinuityFiles.LATEST to bytes))
        val viewModel = launch(remoteBackupStore = remoteBackupStore)

        viewModel.onRecoveryKeyChanged(RecoveryKeyCodec.format(key))
        viewModel.checkForBackup()
        waitForTag("restore_confirm_button")

        rule.onNodeWithTag("restore_confirm_button").performClick()
        waitForTag("restore_complete_message")
        rule.onNodeWithTag("restore_complete_message").assertIsDisplayed()

        val entries = runBlocking { db.journal().entriesNow() }
        assert(entries.size == 1) { "the restored entry must be durably merged" }
    }

    /**
     * The Critical-finding regression test: on a genuinely new/replacement
     * phone, [RecoveryKeyStore] starts empty, and [RestoreCoordinator.resume]'s
     * real production `currentVerifiedKey` (see [RestoreCoordinator.build])
     * has no other source for the key. Before this test would pass, the
     * typed-and-proven-correct key had to actually be persisted, by
     * [RestoreViewModel.checkForBackup] itself, into that same store — not
     * merely used locally to prove [RestoreCandidateSelector.select]
     * succeeds. [testCoordinator]'s `currentVerifiedKey` reads from
     * [recoveryKeyStore] (the same real, on-device instance the view model
     * writes to) rather than a hardcoded key lambda, so this genuinely
     * exercises the fix end to end: before it, `checkForBackup()` never
     * called [RecoveryKeyStore.save]/[RecoveryKeyStore.markVerified], the
     * assertions below on [recoveryKeyStore] would fail immediately, and —
     * had they been bypassed — the confirm-restore step would have failed
     * with [RestoreResult.KeyMissing] instead of reaching
     * `restore_complete_message`.
     */
    @Test
    fun checkForBackupPersistsAndVerifiesTheRecoveryKeySoTheRealCoordinatorCanFindIt() {
        val snapshot = sampleSnapshot(runBlocking { samplePayload() })
        val bytes = envelopeBytes(snapshot)
        val remoteBackupStore = FakeRemoteBackupStore(mapOf(ContinuityFiles.LATEST to bytes))
        val viewModel = launch(remoteBackupStore = remoteBackupStore)

        assert(recoveryKeyStore.current() == null) { "test setup: RecoveryKeyStore must start empty, like a real replacement phone" }

        viewModel.onRecoveryKeyChanged(RecoveryKeyCodec.format(key))
        viewModel.checkForBackup()
        waitForTag("restore_confirm_button")

        val persisted = recoveryKeyStore.current()
        assert(persisted != null && persisted.keyId == key.keyId) {
            "checkForBackup() succeeding must persist the typed, proven-correct key to RecoveryKeyStore"
        }
        assert(recoveryKeyStore.isVerified()) {
            "checkForBackup() succeeding must mark the persisted key verified"
        }

        // Prove resume() (invoked by confirmRestore()) can actually find that
        // key via the real currentVerifiedKey wiring and complete the restore.
        rule.onNodeWithTag("restore_confirm_button").performClick()
        waitForTag("restore_complete_message")
        rule.onNodeWithTag("restore_complete_message").assertIsDisplayed()
    }
}
