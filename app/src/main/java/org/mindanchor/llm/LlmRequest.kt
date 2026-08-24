package org.mindanchor.llm

/**
 * The chat-completion request. [model] is the per-provider
 * model ID — see [LlmProvider.suggestedModels] for the
 * surfaced list and [LlmProvider.defaultModel] for the
 * fallback. [messages] is the conversation (system + user;
 * assistant is unused for v0.25.7 because the launcher
 * doesn't keep a multi-turn history). [temperature] is
 * 0.7 (the OpenAI-compatible recommendation for creative
 * but grounded text); [maxTokens] is 600 (≈ 450 words,
 * gives the 200-300 word target 2x headroom).
 */
data class LlmRequest(
    val model: String,
    val messages: List<LlmMessage>,
    val temperature: Double = 0.7,
    val maxTokens: Int = 600,
)
