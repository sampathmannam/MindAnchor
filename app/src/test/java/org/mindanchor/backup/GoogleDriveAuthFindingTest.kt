package org.mindanchor.backup

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * File-shape pinning for [GoogleDriveAuth]. v0.25.4
 * (WP-A). The class is the OAuth entry point for
 * the Drive backup path: a contributor who edits
 * the file can silently change the contract (e.g.
 * swap the scope, drop the encrypted-prefs store,
 * remove the sign-out path) and nothing else
 * would notice. These five tests pin the shape
 * that the rest of the v0.25.4 build depends on.
 *
 * The path is resolved relative to the Gradle
 * working directory (`app/`), which is the
 * `jvmTest` task's default. If the file is moved,
 * every test fails — by design.
 */
class GoogleDriveAuthFindingTest {

    private val sourcePath = "src/main/java/org/mindanchor/backup/GoogleDriveAuth.kt"
    private val contentTypePath = "src/main/java/org/mindanchor/backup/ContentType.kt"
    private val backupTargetPath = "src/main/java/org/mindanchor/backup/BackupTarget.kt"
    private val source by lazy { File(sourcePath).readText() }

    @Test fun `file is in the backup package and class is public`() {
        assertTrue("package must be org.mindanchor.backup", source.contains("package org.mindanchor.backup"))
        assertTrue("class must be public — no visibility modifier", source.contains("class GoogleDriveAuth"))
    }

    @Test fun `class uses the narrowest drive_file scope (NOT full drive)`() {
        // The scope is the user-trust posture: drive.file
        // only grants access to files the app created, not
        // the user's whole Drive. A scope change is a
        // clinical-review-surface change, not a
        // refactor.
        assertTrue(
            "must reference the drive.file scope literally",
            source.contains("https://www.googleapis.com/auth/drive.file"),
        )
        // Make sure the wider `drive` scope (i.e. the
        // exact URL with no `.file` suffix) is NOT
        // referenced. The check has to look for the URL
        // followed by a closing quote (the constant
        // form) — `drive.file` contains `drive` as a
        // substring, so a naive check is a false
        // positive. The Drive REST API has the broader
        // `https://www.googleapis.com/auth/drive`
        // scope; a contributor who pasted the wrong
        // URL into DRIVE_FILE_SCOPE would surface
        // here.
        assertTrue(
            "must not reference the full-drive scope (drive.file is the only allowed suffix)",
            !source.contains("\"https://www.googleapis.com/auth/drive\""),
            )
    }

    @Test fun `class wires GoogleSignIn + GoogleAuthUtil + EncryptedSharedPreferences`() {
        // The three primitives the OAuth flow needs:
        //  - GoogleSignIn (the account picker)
        //  - GoogleAuthUtil (the access-token fetch)
        //  - EncryptedSharedPreferences (the at-rest
        //    store for the access token)
        val needs = listOf(
            "import com.google.android.gms.auth.api.signin.GoogleSignIn",
            "import com.google.android.gms.auth.api.signin.GoogleSignInClient",
            "import com.google.android.gms.auth.GoogleAuthUtil",
            "import com.google.android.gms.auth.api.signin.GoogleSignInOptions",
            "import androidx.security.crypto.EncryptedSharedPreferences",
            "import androidx.security.crypto.MasterKey",
        )
        for (needle in needs) {
            assertTrue("must import $needle", source.contains(needle))
        }
    }

    @Test fun `class exposes signInIntent + handleSignInResult + currentAccessToken + signOut`() {
        // The four methods the Settings sub-section
        // (WP-C) and the BackupTarget (WP-B) call.
        // A rename is a contract break; the finding
        // test catches the rename before the call
        // sites do.
        val needs = listOf(
            "fun signInIntent(): Intent",
            "fun handleSignInResult",
            "fun currentAccessToken",
            "fun signOut()",
            "val signedInEmailFlow",
        )
        for (needle in needs) {
            assertTrue("must expose $needle", source.contains(needle))
        }
    }

    @Test fun `ContentType and BackupTarget companions exist with the v0_25_4 surface`() {
        // WP-A is the foundation. WP-B / WP-C / WP-D
        // build on the ContentType enum and the
        // BackupTarget interface. Their existence is
        // pinned here so a contributor cannot delete
        // one without the finding test failing.
        val ct = File(contentTypePath).readText()
        val bt = File(backupTargetPath).readText()
        assertTrue("ContentType must be a sealed interface", ct.contains("sealed interface ContentType"))
        assertTrue(
            "ContentType must define Notes and Letters",
            ct.contains("data object Notes") && ct.contains("data object Letters"),
        )
        assertTrue("ContentType must pin a fileName per value", ct.contains("override val fileName"))
        assertTrue(
            "BackupTarget must be an interface with append",
            bt.contains("interface BackupTarget") && bt.contains("fun append"),
        )
        assertTrue("BackupTarget must define AppendResult", bt.contains("sealed interface AppendResult"))
    }
}
