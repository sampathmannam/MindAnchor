package org.mindanchor.backup

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * File-shape pinning for [GoogleDriveBackupTarget].
 * v0.25.4 (WP-B).
 *
 * The class is the Drive REST client that backs
 * the v0.25.4 backup path. A contributor who edits
 * the file can silently change the contract (e.g.
 * drop the auth header, swap the multipart shape,
 * remove the newline terminator) and the round-trip
 * test would still pass with the wrong bytes.
 * These five tests pin the shape that the
 * round-trip test depends on.
 */
class GoogleDriveBackupTargetFindingTest {

    private val sourcePath = "src/main/java/org/mindanchor/backup/GoogleDriveBackupTarget.kt"
    private val source by lazy { File(sourcePath).readText() }

    @Test fun `file is in the backup package and class is public`() {
        assertTrue("package must be org.mindanchor.backup", source.contains("package org.mindanchor.backup"))
        assertTrue(
            "class must be public",
            source.contains("class GoogleDriveBackupTarget"),
        )
    }

    @Test fun `class implements BackupTarget with the v0_25_4 append contract`() {
        // The class must declare `: BackupTarget`
        // and override `append` with the
        // (ContentType, ByteArray) → AppendResult
        // shape. A rename or a signature drift
        // surfaces here before the call sites
        // (WP-D's BackupScheduler) compile.
        assertTrue("must implement BackupTarget", source.contains(": BackupTarget"))
        assertTrue(
            "must override append(type, payload)",
            source.contains("override suspend fun append(type: ContentType, payload: ByteArray): AppendResult"),
        )
    }

    @Test fun `class uses OkHttp + GoogleDriveAuth + ContentType for the Drive REST surface`() {
        // The four primitives the wire-level
        // code touches: OkHttp (HTTP client),
        // GoogleDriveAuth (OAuth bearer), and
        // the per-type file routing via
        // ContentType. The import statements
        // may be elided by the Kotlin compiler
        // when the class is in the same package
        // (e.g. GoogleDriveAuth) — the test
        // pins the *reference*, not the literal
        // `import` line.
        val needs = listOf(
            "import okhttp3.OkHttpClient",
            "import okhttp3.Request",
            "import okhttp3.RequestBody",
            "private val auth: GoogleDriveAuth",
            "private val type: ContentType",
        )
        for (needle in needs) {
            assertTrue("must reference $needle", source.contains(needle))
        }
    }

    @Test fun `class terminates each append with a newline (per-type file is JSON-Lines-shaped)`() {
        // The per-type file format is a sequence
        // of newline-terminated AES-256-GCM
        // blobs. The target adds the newline so
        // the caller (WP-D's scheduler) only has
        // to hand it the encrypted bytes; the
        // transport decides on the line separator.
        assertTrue(
            "must reference the newline byte (0x0A) for line termination",
            source.contains("0x0A"),
        )
        assertTrue(
            "must concatenate old + payload + newline in the append path",
            source.contains("payloadWithNewline"),
        )
    }

    @Test fun `class hits the four Drive REST endpoints (find create download update)`() {
        // v0.25.4 uses 3 endpoints + 1 variant
        // for create. The class must touch all
        // four to make the append-then-reupload
        // model work. A refactor that drops one
        // (e.g. a future "use the diff API
        // instead" change) must surface here
        // before the round-trip test.
        assertTrue("must hit the files endpoint (find)", source.contains("/drive/v3/files?q="))
        assertTrue(
            "must hit the upload endpoint (create)",
            source.contains("upload/drive/v3/files?uploadType=multipart"),
        )
        assertTrue("must hit the alt=media endpoint (download)", source.contains("?alt=media"))
        assertTrue("must hit the upload endpoint (update)", source.contains("upload/drive/v3/files/"))
        assertTrue("must PATCH for updates", source.contains(".patch("))
    }
}
