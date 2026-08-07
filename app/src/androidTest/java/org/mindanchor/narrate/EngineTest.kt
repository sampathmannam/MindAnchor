package org.mindanchor.narrate

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the engine exists on a real device: the library loads, its
 * symbols resolve, and the backend initialises. Everything further —
 * actual generation — needs a multi-gigabyte model file no emulator
 * job should carry, and the surrounding logic is already unit-tested
 * against fakes in GuardedNarratorTest.
 *
 * This is the difference between "the engine compiled" and "the engine
 * runs here". The x86_64 ABI is packaged for exactly this test's sake.
 */
@RunWith(AndroidJUnit4::class)
class EngineTest {

    @Test
    fun theNativeLibraryLoadsAndItsBackendComesUp() {
        assertTrue("libmindanchor_llama failed to load", LlamaEngine.loaded)
        assertTrue("backend did not initialise", LlamaEngine().nativeReady())
    }

    @Test
    fun aMissingModelFileGeneratesNothingRatherThanCrashing() {
        assertTrue(LlamaEngine.loaded)
        val bytes = LlamaEngine().nativeGenerate(
            modelPath = InstrumentationRegistry.getInstrumentation()
                .targetContext.filesDir.resolve("no-such-model.gguf").absolutePath,
            system = "s",
            prompt = "p",
            contextTokens = 512,
            maxNewTokens = 8,
            seed = 42L,
            threads = 2,
        )
        assertNull("a missing model must yield nothing, not something", bytes)
    }
}
