package org.mindanchor.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * File-shape pinning for [BackupScheduler].
 * v0.25.4 (WP-D); re-pinned for the v0.70.7
 * four-type, encryption-free, delta-sync
 * rewrite.
 *
 * The scheduler is the per-type routing layer
 * between the data sources and the outbound
 * [BackupTarget]. A contributor who edits the
 * file can silently change the contract (e.g.
 * bring back a full reupload every night, drop a
 * content type, route the wrong entries to the
 * wrong target) and the round-trip test would
 * still pass with the wrong behaviour — Robolectric
 * cannot back the Android Keystore other stores in
 * this app rely on, so [BackupSchedulerTest] only
 * ever exercises the empty-data path. These tests
 * pin the shape that path can't cover.
 */
class BackupSchedulerFindingTest {

    private val sourcePath = "src/main/java/org/mindanchor/backup/BackupScheduler.kt"
    private val source by lazy { File(sourcePath).readText() }

    @Test fun `file is in the backup package and class is public`() {
        assertTrue("package must be org.mindanchor.backup", source.contains("package org.mindanchor.backup"))
        assertTrue("class must be public", source.contains("class BackupScheduler"))
    }

    @Test fun `class takes a BackupTarget per content type`() {
        // The per-type routing is the contract: one target per
        // type, picked by the type at dispatch time. A single
        // shared target would not match the per-type file model,
        // and dropping a target from the constructor would mean
        // that content type quietly stops being backed up.
        assertTrue("must take notesTarget: BackupTarget", source.contains("private val notesTarget: BackupTarget"))
        assertTrue("must take lettersTarget: BackupTarget", source.contains("private val lettersTarget: BackupTarget"))
        assertTrue(
            "must take checkInsTarget: BackupTarget",
            source.contains("private val checkInsTarget: BackupTarget"),
        )
        assertTrue(
            "must take wellnessTarget: BackupTarget",
            source.contains("private val wellnessTarget: BackupTarget"),
        )
    }

    @Test fun `class exposes both backupAll and restoreAll`() {
        // Two directions on the same four types: backupAll is
        // the upload side (Settings "Back up now" and the
        // nightly alarm), restoreAll is the download side (the
        // Settings restore button, and what a new phone signed
        // into the same account needs to pick up where the old
        // one left off). Losing either half silently breaks one
        // direction of "don't lose my notes when I change
        // phones" without any test elsewhere noticing.
        assertTrue("must expose suspend fun backupAll", source.contains("suspend fun backupAll()"))
        assertTrue("must expose suspend fun restoreAll", source.contains("suspend fun restoreAll()"))
    }

    @Test fun `wire format is plain JSON with no encryption layer`() {
        // v0.70.7: the payload used to be wrapped in
        // EncryptedBackupCodec (an Android Keystore-backed
        // AES-256-GCM key). That key cannot follow the user to a
        // new phone, which made "restore this on my new phone"
        // and "encrypted with a key that can't leave this phone"
        // a direct contradiction — see GoogleDriveBackupTarget's
        // KDoc. The user chose continuity over that layer, so a
        // reintroduction of the encrypted codec here would silently
        // break every restore again. Checked as a call, not a bare
        // name match, since this class's own KDoc mentions
        // EncryptedBackupCodec by name to explain that history.
        assertFalse("must not call EncryptedBackupCodec.wrap", source.contains("EncryptedBackupCodec.wrap("))
        val needs = listOf(
            "Json {",
            "prettyPrint = false",
            "BackupEntry.serializer()",
            "CheckInEntry.serializer()",
            "WellnessEntry.serializer()",
        )
        for (needle in needs) {
            assertTrue("must reference $needle", source.contains(needle))
        }
    }

    @Test fun `backupAll diffs against downloaded Drive content instead of reuploading everything`() {
        // GoogleDriveBackupTarget.append has no native append to
        // build on — every call downloads the whole file, adds
        // one line, and reuploads the whole file. A nightly job
        // that called append once per *existing* entry, forever,
        // would get slower and more expensive every night and
        // would write the same content on top of itself. The
        // fix is downloadedKeys: each sync* helper downloads its
        // type's current Drive content once and only appends
        // local entries not already in it. Losing this silently
        // reintroduces the unbounded-growth problem the v0.70.7
        // rewrite exists to close.
        assertTrue("must define downloadedKeys", source.contains("private suspend fun downloadedKeys("))
        assertTrue("must call target.download", source.contains(".download(type)"))
        val syncFns = listOf("syncNotes", "syncLetters", "syncCheckIns", "syncWellness")
        for (fn in syncFns) {
            assertTrue("must define $fn", source.contains("private suspend fun $fn("))
        }
    }
}
