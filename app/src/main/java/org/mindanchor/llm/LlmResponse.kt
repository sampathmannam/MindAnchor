package org.mindanchor.llm

/**
 * The chat-completion response. [content] is the letter
 * body; the three counters are the metadata the reader
 * shows in the footer line
 * (Groq · llama-3.3-70b · 1240 input / 380 output · 1.2s).
 */
data class LlmResponse(
    val content: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val durationMs: Long,
)
