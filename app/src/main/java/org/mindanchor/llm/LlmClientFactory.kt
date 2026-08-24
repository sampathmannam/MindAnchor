package org.mindanchor.llm

object LlmClientFactory {
    fun create(
        provider: LlmProvider,
        apiKey: String,
        model: String,
    ): LlmClient = OpenAiCompatibleClient(
        apiKey = apiKey,
        model = model,
        baseUrl = provider.baseUrl,
    )
}
