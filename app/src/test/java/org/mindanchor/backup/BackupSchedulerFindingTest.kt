package org.mindanchor.backup

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * File-shape pinning for [BackupScheduler].
 * v0.25.4 (WP-D).
 *
 * The scheduler is the per-type routing layer
 * between the data sources and the outbound
 * [BackupTarget]. A contributor who edits the
 * file can silently change the contract (e.g.
 * drop the on-write trigger, swap the
 * per-type dispatch, route the wrong entries
 * to the wrong target) and the round-trip test
 * would still pass with the wrong behaviour.
 * These five tests pin the shape that the rest
 * of v0.25.4 depends on.
 */
class BackupSchedulerFindingTest {

    private val sourcePath = "src/main/java/org/mindanchor/backup/BackupScheduler.kt"
    private val source by lazy { File(sourcePath).readText() }

    @Test fun `file is in the backup package and class is public`() {
        assertTrue("package must be org.mindanchor.backup", source.contains("package org.mindanchor.backup"))
        assertTrue("class must be public", source.contains("class BackupScheduler"))
    }

    @Test fun `class takes a BackupTarget per content type (notes + letters)`() {
        // The per-type routing is the v0.25.4
        // contract: one target per type, picked
        // by the type at dispatch time. A single
        // shared target would not match the
        // per-type file model.
        assertTrue("must take notesTarget: BackupTarget", source.contains("private val notesTarget: BackupTarget"))
        assertTrue("must take lettersTarget: BackupTarget", source.contains("private val lettersTarget: BackupTarget"))
    }

    @Test fun `class exposes backupAll and start methods`() {
        // Two trigger surfaces: the "Back up
        // now" full-reupload (backupAll) and the
        // on-write incremental trigger (start).
        // Either can be called independently;
        // a future iteration can drop one and
        // the rest of v0.25.4 would not notice
        // until this test re-fails.
        assertTrue("must expose suspend fun backupAll", source.contains("suspend fun backupAll()"))
        assertTrue("must expose fun start(scope)", source.contains("fun start(scope: CoroutineScope)"))
    }

    @Test fun `class wires EncryptedBackupCodec and JSON for the wire format`() {
        // The wire format is JSON (one line per
        // entry) wrapped in AES-256-GCM via
        // [EncryptedBackupCodec]. The serializer
        // must use no-pretty-print (each entry is
        // one line; a newline in the JSON body
        // would break the line-based restore).
        val needs = listOf(
            "EncryptedBackupCodec.wrap",
            "Json {",
            "prettyPrint = false",
            "BackupEntry.serializer()",
        )
        for (needle in needs) {
            assertTrue("must reference $needle", source.contains(needle))
        }
    }

    @Test fun `class uses distinctUntilChanged + scan for the on-write diff`() {
        // The on-write trigger diffs each
        // emission against the previous one;
        // `distinctUntilChanged` filters out
        // DataStore's metadata-only re-emissions.
        // A future contributor who replaces the
        // diff with a simpler "fire on every
        // emission" pattern would surface here.
        assertTrue("must use distinctUntilChanged", source.contains("distinctUntilChanged"))
        assertTrue("must use scan for the diff", source.contains(".scan("))
    }
}
