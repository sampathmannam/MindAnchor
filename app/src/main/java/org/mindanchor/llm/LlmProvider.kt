package org.mindanchor.llm

enum class LlmProvider(
    val displayName: String,
    val signupUrl: String,
    val baseUrl: String,
    val defaultModel: String,
    val isFree: Boolean,
    val suggestedModels: List<String>,
) {
    GOOGLE_AI_STUDIO(
        displayName = "Google AI Studio",
        signupUrl = "https://aistudio.google.com/app/apikey",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
        defaultModel = "gemini-2.0-flash",
        isFree = true,
        suggestedModels = listOf("gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-1.5-flash"),
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        signupUrl = "https://openrouter.ai/keys",
        baseUrl = "https://openrouter.ai/api/v1/",
        defaultModel = "meta-llama/llama-3.3-70b-instruct:free",
        isFree = true,
        suggestedModels = listOf(
            "meta-llama/llama-3.3-70b-instruct:free",
            "google/gemini-2.0-flash-exp:free",
            "qwen/qwen-2.5-72b-instruct:free",
        ),
    ),
    GROQ(
        displayName = "Groq",
        signupUrl = "https://console.groq.com/keys",
        baseUrl = "https://api.groq.com/openai/v1/",
        defaultModel = "llama-3.3-70b-versatile",
        isFree = false,
        suggestedModels = listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "meta-llama/llama-4-maverick-17b-128e-instruct",
        ),
    ),
}
