package org.mindanchor.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LlmClientFactoryTest {

    @Test
    fun `create returns an OpenAiCompatibleClient for GOOGLE_AI_STUDIO`() {
        val client: LlmClient = LlmClientFactory.create(
            provider = LlmProvider.GOOGLE_AI_STUDIO,
            apiKey = "test-key",
            model = LlmProvider.GOOGLE_AI_STUDIO.defaultModel,
        )
        assertNotNull(client)
        assertEquals(OpenAiCompatibleClient::class.java, client::class.java)
    }

    @Test
    fun `create returns an OpenAiCompatibleClient for OPENROUTER`() {
        val client: LlmClient = LlmClientFactory.create(
            provider = LlmProvider.OPENROUTER,
            apiKey = "test-key",
            model = LlmProvider.OPENROUTER.defaultModel,
        )
        assertNotNull(client)
        assertEquals(OpenAiCompatibleClient::class.java, client::class.java)
    }

    @Test
    fun `create returns an OpenAiCompatibleClient for GROQ`() {
        val client: LlmClient = LlmClientFactory.create(
            provider = LlmProvider.GROQ,
            apiKey = "test-key",
            model = LlmProvider.GROQ.defaultModel,
        )
        assertNotNull(client)
        assertEquals(OpenAiCompatibleClient::class.java, client::class.java)
    }

    @Test
    fun `every LlmProvider value is wired in the factory`() {
        for (p in LlmProvider.values()) {
            assertNotNull(
                "Factory must handle provider $p",
                LlmClientFactory.create(
                    provider = p,
                    apiKey = "test-key",
                    model = p.defaultModel,
                ),
            )
        }
    }
}
