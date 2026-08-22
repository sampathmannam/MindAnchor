package org.mindanchor.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The round-trip for [BackupScheduler]. v0.25.4
 * (WP-D).
 *
 * The scheduler reads from
 * [org.mindanchor.data.NotesPrefs] and
 * [org.mindanchor.letters.LetterStore], wraps
 * each entry in AES-256-GCM, and dispatches to
 * the per-type [BackupTarget].
 *
 * Robolectric does not back the Android Keystore
 * (the [org.mindanchor.data.NotesPrefs.add] path
 * uses an HMAC over the Keystore), so the
 * "populate then backup" round-trip is not
 * feasible in a JVM-only test. The empty-data
 * case is verifiable: the scheduler makes zero
 * HTTP calls, the result reports zero appends,
 * and the wiring (auth, target, scheduler) is
 * exercised end-to-end. The per-type dispatch
 * shape is pinned by [BackupSchedulerFindingTest].
 *
 * The integration test for the populated path
 * is the v0.25.4-WP-F manual smoke on a real
 * device, which the plan acknowledges.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupSchedulerTest {

    private lateinit var server: MockWebServer
    private lateinit var notesTarget: GoogleDriveBackupTarget
    private lateinit var lettersTarget: GoogleDriveBackupTarget
    private lateinit var scheduler: BackupScheduler

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val rewritten = original.newBuilder()
                    .url(
                        original.url.newBuilder()
                            .host(server.hostName)
                            .port(server.port)
                            .scheme("http")
                            .build(),
                    )
                    .build()
                chain.proceed(rewritten)
            }
            .build()
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val tokenStore = TokenStore(
            ctx.getSharedPreferences("test_drive_auth_sched", Context.MODE_PRIVATE),
        )
        tokenStore.write("test-access-token")
        val auth = GoogleDriveAuth(ctx, tokenStore)
        notesTarget = GoogleDriveBackupTarget(
            client = client,
            auth = auth,
            type = ContentType.Notes,
            allowInsecureForTest = GoogleDriveBackupTarget.AllowInsecureForTest.INSTANCE,
        )
        lettersTarget = GoogleDriveBackupTarget(
            client = client,
            auth = auth,
            type = ContentType.Letters,
            allowInsecureForTest = GoogleDriveBackupTarget.AllowInsecureForTest.INSTANCE,
        )
        scheduler = BackupScheduler(
            context = ctx,
            notesTarget = notesTarget,
            lettersTarget = lettersTarget,
        )
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun `backupAll with no data makes no HTTP calls`() = runBlocking {
        val result = scheduler.backupAll()
        assertTrue("empty backup must be ok", result.ok)
        assertEquals("no notes appended", 0, result.notesAppended)
        assertEquals("no letters appended", 0, result.lettersAppended)
        assertEquals("no HTTP calls", 0, server.requestCount)
    }

    @Test fun `backupAll result reports ok and counts`() = runBlocking {
        val result = scheduler.backupAll()
        // The shape of the result: the four
        // counts, and the `ok` shortcut. The
        // empty-data case has all zeros.
        assertTrue("ok must be true on no failures", result.ok)
        assertEquals(0, result.notesAppended + result.notesFailed)
        assertEquals(0, result.lettersAppended + result.lettersFailed)
    }
}
