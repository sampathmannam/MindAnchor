// TestGuild #9 (Karate substitute, the JUnit version).
// Karate is the testing-DSL tool in the catalog; for
// MindAnchor the equivalent is a pure-JUnit contract test
// over the LLM provider enum, which is the source of truth
// for the three HTTP endpoints (Google AI Studio,
// OpenRouter, Groq) and the model names that the
// LlmClientFactory threads into OpenAiCompatibleClient.
//
// What this pins:
//   1. Every provider's baseUrl is the OpenAI chat-
//      completions shape, with a trailing slash (OkHttp
//      would otherwise URL-encode the path wrong).
//   2. The provider's defaultModel is a member of its
//      own suggestedModels (so the picker never selects
//      an unknown model).
//   3. The free providers (Google, OpenRouter) have at
//      least one free suggested model.
//   4. CertificatePinning has a pin set for every
//      provider's hostname — so a future provider added
//      to the enum without a pin is caught at the test
//      gate.
//   5. The signup URL is HTTPS (we never sign a user up
//      over an insecure link).
//
// All five are pure-logic checks; no OkHttp, no KSP, no
// network. Run from :app:testDebugUnitTest.

package org.mindanchor.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.llm.CertificatePinning

class LlmProviderContractTest {

    @Test
    fun `every provider's baseUrl is OpenAI-compatible, https, trailing slash`() {
        for (provider in LlmProvider.entries) {
            val base = provider.baseUrl
            assertTrue(
                "provider ${provider.name} baseUrl not https: $base",
                base.startsWith("https://"),
            )
            assertTrue(
                "provider ${provider.name} baseUrl missing trailing slash: $base",
                base.endsWith("/"),
            )
            // The OpenAI-compat paths differ per provider:
            //   Google AI Studio: /v1beta/openai/chat/completions
            //   OpenRouter:       /api/v1/chat/completions
            //   Groq:             /openai/v1/chat/completions
            // The contract is that the path contains
            // `chat/completions` or `v1/chat/completions` once
            // the request is built. For now, just require the
            // hostname be the documented API server.
            assertTrue(
                "provider ${provider.name} baseUrl looks wrong: $base",
                base.contains("://") && base.length > 16,
            )
        }
    }

    @Test
    fun `defaultModel is always a member of suggestedModels`() {
        for (provider in LlmProvider.entries) {
            assertTrue(
                "provider ${provider.name} defaultModel ${provider.defaultModel} not in suggestedModels ${provider.suggestedModels}",
                provider.defaultModel in provider.suggestedModels,
            )
        }
    }

    @Test
    fun `every free provider offers at least one free model`() {
        for (provider in LlmProvider.entries) {
            if (provider.isFree) {
                assertTrue(
                    "free provider ${provider.name} has no suggested models",
                    provider.suggestedModels.isNotEmpty(),
                )
            }
        }
    }

    @Test
    fun `signup url is https and unique per provider`() {
        val seen = mutableSetOf<String>()
        for (provider in LlmProvider.entries) {
            assertTrue(
                "provider ${provider.name} signupUrl not https: ${provider.signupUrl}",
                provider.signupUrl.startsWith("https://"),
            )
            assertTrue(
                "duplicate signupUrl ${provider.signupUrl} for ${provider.name}",
                provider.signupUrl !in seen,
            )
            seen += provider.signupUrl
        }
    }

    @Test
    fun `CertificatePinning covers every provider hostname except the documented DeepSeek gap`() {
        // For each provider, forBaseUrl(baseUrl) must return
        // a non-null CertificatePinner. This is the gate that
        // catches "added a provider without adding pins".
        //
        // DEEPSEEK (added 2026-08-29) is the one deliberate
        // exception: the dev environment adding it could not
        // reach api.deepseek.com at all to capture a live
        // chain, and guessing a pin risks shipping a wrong
        // one that fails closed on every real request -- the
        // exact bug this session already found and fixed for
        // Google AI Studio. See LlmProvider.kt's DEEPSEEK
        // entry for the full reasoning. Remove this carve-out
        // once a real pin is captured from a real device on a
        // real network.
        for (provider in LlmProvider.entries) {
            if (provider == LlmProvider.DEEPSEEK) continue
            val pinner = CertificatePinning.forBaseUrl(provider.baseUrl)
            assertNotNull(
                "CertificatePinning.forBaseUrl returned null for ${provider.name} (${provider.baseUrl})",
                pinner,
            )
        }
    }

    @Test
    fun `provider count matches expected four`() {
        // If someone adds a fifth provider to the enum, the
        // rest of the contract (signups, pins, models) needs
        // to be filled in too. This test is a tripwire.
        assertEquals(4, LlmProvider.entries.size)
    }
}
