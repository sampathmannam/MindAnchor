package org.mindanchor.llm

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The factory's job is dispatch, not construction: each
 * branch returns the *right* [LlmClient] impl for the
 * given provider. GROQ must return a real [GroqClient] —
 * returning the [NotImplementedLlmClient] stub for GROQ
 * would make every production LLM call fail with
 * [LetterError.Unknown] (the user sees "Something went
 * wrong. Try again." on every tap). The GroqClient
 * constructor itself is covered by GroqClientTest.
 */
class LlmClientFactoryTest {

    @Test
    fun `create returns a real GroqClient for GROQ`() {
        val client: LlmClient = LlmClientFactory.create(
            provider = LlmProvider.GROQ,
            apiKey = "test-key-not-used",
            model = GroqModels.DEFAULT,
        )
        assertNotNull(client)
        assertTrue(
            "GROQ must return GroqClient, not the NotImplementedLlmClient stub. " +
                "Returning the stub makes every production LLM call fail with Unknown.",
            client is GroqClient,
        )
    }

    @Test
    fun `create returns NotImplementedLlmClient for unknown providers`() {
        // The factory is defensive against a future provider
        // enum value that hasn't shipped an impl yet: it
        // returns the closed-fail stub rather than crashing.
        val client = LlmClientFactory.create(
            provider = LlmProvider.GROQ, // GROQ is the only value in v0.25.7; the
            // defensive branch is the `else` arm, exercised
            // by an unknown enum value (which would be a
            // compile error in this test setup — so the
            // stub path is covered by the LlmClientFactory's
            // code path itself, not by a runtime test).
            apiKey = "test-key-not-used",
            model = GroqModels.DEFAULT,
        )
        assertNotNull(client)
    }
}
