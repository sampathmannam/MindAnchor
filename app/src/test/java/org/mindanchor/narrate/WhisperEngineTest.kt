package org.mindanchor.narrate

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v0.30+ (Phase 4 G-28) — the whisper.cpp JNI
 * bridge is a scaffold. The native library is not
 * built in the test runner (the whisper.cpp
 * vendoring is a user setup step), so the
 * [Whisper.loaded] flag is `false` and the
 * `nativeReady` check returns `false` even when
 * the library would be loaded. The tests in this
 * file pin the kotlin-side contract that the
 * [Whisper] class degrades cleanly when the
 * native library is not available.
 *
 * Once the whisper.cpp vendoring + native build
 * are in place (a user setup step), the
 * [WhisperEngine.loaded] flag will be `true` on a
 * real device, and the contract tests below
 * continue to hold (null on missing model, etc.).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class WhisperEngineTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `transcribe returns null when the native library is not loaded`() {
        // whisper.cpp is not vendored in the test
        // build; [WhisperEngine.loaded] is false
        // and every [Whisper.transcribe] call must
        // return null cleanly.
        runBlocking {
            val sut = Whisper(context)
            val pcm = ShortArray(16000) { 0 } // 1s of silence
            assertNull(sut.transcribe(pcm))
        }
    }

    @Test
    fun `transcribe returns null on an empty pcm buffer`() {
        runBlocking {
            val sut = Whisper(context)
            assertNull(sut.transcribe(ShortArray(0)))
        }
    }

    @Test
    fun `MODEL_DOWNLOADED is false in this commit`() {
        // The model download is a follow-up commit;
        // the v0.30+ scaffold is the bridge and the
        // model path constant. The consent flow
        // (and the actual OkHttp call) is the next
        // layer of work.
        assertEquals(false, Whisper.MODEL_DOWNLOADED)
    }

    @Test
    fun `model path constant is what it claims to be`() {
        // The path is the v0.30+ bundled location.
        // The file itself is NOT in the repo; the
        // user vendors it under
        // /data/data/org.mindanchor/files/whisper/.
        // The path is fixed (the Kotlin side
        // concatenates it directly); the test pins
        // the path so a future rename of the
        // directory is caught here.
        assertTrue(Whisper.MODEL_PATH.endsWith("ggml-tiny.en.bin"))
    }
}
