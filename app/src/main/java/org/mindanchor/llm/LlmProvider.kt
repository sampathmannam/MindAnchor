package org.mindanchor.llm

/**
 * The LLM provider. v0.25.7 ships GROQ only; ANTHROPIC is
 * reserved for a follow-up version (the [LlmClient] interface
 * is the seam — see [LlmClientFactory]).
 */
enum class LlmProvider {
    GROQ,
    // ANTHROPIC, // v0.25.8+ — not in the Settings picker yet
}
