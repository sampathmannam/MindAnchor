package org.mindanchor.llm

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The factory's job is dispatch, not construction: each
 * branch returns *some* [LlmClient] impl. The actual
 * GroqClient construction is covered by GroqClientTest
 * once that class lands.
 */
class LlmClientFactoryTest {

    @Test
    fun `create returns a non-null LlmClient for GROQ`() {
        val client: LlmClient = LlmClientFactory.create(
            provider = LlmProvider.GROQ,
            apiKey = "test-key-not-used",
            model = GroqModels.DEFAULT,
        )
        assertNotNull(client)
    }

    @Test
    fun `create returns a LlmClient for GROQ that fails closed when invoked`() {
        // The stub's complete() / testConnection() must
        // return Result.failure, never throw. The caller
        // (LetterViewModel) treats any throw as a
        // programmer error; the test pins the closed-fail
        // contract.
        val client = LlmClientFactory.create(
            provider = LlmProvider.GROQ,
            apiKey = "test-key-not-used",
            model = "no-such-model",
        )
        val result = kotlinx.coroutines.runBlocking { client.testConnection() }
        assertTrue("testConnection must return Result, not throw", result.isFailure)
    }
}
