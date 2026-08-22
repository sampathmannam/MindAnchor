package org.mindanchor.llm

/**
 * One message in a chat-completion request. Sealed so the
 * LLM client can be exhaustive on the wire-format mapping
 * (no `when` exhaustiveness warnings, no "default" branch
 * hiding a missing case).
 *
 * The content is plain text — multimodal is out of scope
 * for v0.25.7.
 */
sealed class LlmMessage {
    abstract val content: String

    data class System(override val content: String) : LlmMessage()
    data class User(override val content: String) : LlmMessage()
    data class Assistant(override val content: String) : LlmMessage()
}
