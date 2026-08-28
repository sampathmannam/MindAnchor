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
 * (WP-D); expanded to four content types and
 * made encryption-free in v0.70.7.
 *
 * The scheduler reads from
 * [org.mindanchor.data.NotesPrefs],
 * [org.mindanchor.letters.LetterStore],
 * [org.mindanchor.model.MomentStore], and
 * [org.mindanchor.vitals.MeasuredStore], and
 * dispatches to the per-type [BackupTarget].
 *
 * Robolectric does not back the Android Keystore
 * that some other stores in this app rely on for
 * their own integrity seal, so the "populate then
 * backup" round-trip for those types is not
 * feasible in a JVM-only test. The empty-data
 * case is verifiable: with nothing on any of the
 * four local stores, [BackupScheduler.syncNotes]
 * and its three siblings short-circuit before
 * ever calling [BackupTarget.download], so the
 * scheduler makes zero HTTP calls, the result
 * reports zero appends, and the wiring (auth,
 * target, scheduler) is exercised end-to-end. The
 * per-type dispatch shape is pinned by
 * [BackupSchedulerFindingTest].
 *
 * The integration test for the populated path
 * is the v0.25.4-WP-F manual smoke on a real
 * device, which the plan acknowledges.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupSchedulerTest {

    private lateinit var server: MockWebServer
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
        fun target(type: ContentType) = GoogleDriveBackupTarget(
            client = client,
            auth = auth,
            type = type,
            allowInsecureForTest = GoogleDriveBackupTarget.AllowInsecureForTest.INSTANCE,
        )
        scheduler = BackupScheduler(
            context = ctx,
            targets = BackupTargets(
                notes = target(ContentType.Notes),
                letters = target(ContentType.Letters),
                checkIns = target(ContentType.CheckIns),
                wellness = target(ContentType.WellnessReadings),
            ),
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
        assertEquals("no check-ins appended", 0, result.checkInsAppended)
        assertEquals("no wellness readings appended", 0, result.wellnessAppended)
        assertEquals("no HTTP calls", 0, server.requestCount)
    }

    @Test fun `backupAll result reports ok and counts`() = runBlocking {
        val result = scheduler.backupAll()
        // The shape of the result: the eight
        // counts, and the `ok` shortcut. The
        // empty-data case has all zeros.
        assertTrue("ok must be true on no failures", result.ok)
        assertEquals(0, result.notesAppended + result.notesFailed)
        assertEquals(0, result.lettersAppended + result.lettersFailed)
        assertEquals(0, result.checkInsAppended + result.checkInsFailed)
        assertEquals(0, result.wellnessAppended + result.wellnessFailed)
    }

    @Test fun `restoreAll with nothing in Drive makes four HTTP calls and restores nothing`() = runBlocking {
        // One download per content type, each a files.list query.
        // A real Drive files.list call that matches nothing is a
        // successful (200) query with an empty array — findFileId
        // parses no "id" out of it and returns null, so download()
        // returns null without a second (downloadFile) call. Exactly
        // one enqueued response per type is consumed.
        repeat(4) {
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("""{"files": []}""").setResponseCode(200))
        }
        val result = scheduler.restoreAll()
        assertEquals("nothing to restore from an empty Drive", 0, result.total)
        assertEquals("one files.list query per content type", 4, server.requestCount)
    }
}
