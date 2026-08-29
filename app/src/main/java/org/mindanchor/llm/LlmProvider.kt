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
    DEEPSEEK(
        displayName = "DeepSeek",
        signupUrl = "https://platform.deepseek.com/api_keys",
        baseUrl = "https://api.deepseek.com/v1/",
        defaultModel = "deepseek-chat",
        isFree = false,
        // v0.72+ (2026-08-29) — api.deepseek.com has no
        // CertificatePinning entry: this dev environment
        // could not reach the host at all to capture a
        // live chain (unlike generativelanguage.googleapis.com,
        // which was reachable but through a proxy that
        // showed a different, wrong chain — the exact
        // failure mode that produced the stale Google
        // pins this session had to fix). Shipping a
        // guessed pin risks repeating that bug closed;
        // CertificatePinning.forBaseUrl returns null for
        // this host, so calls fall back to the platform
        // trust store (the same posture every host had
        // before pinning existed). Add a real pin once
        // captured from a real device on a real network.
        suggestedModels = listOf("deepseek-chat", "deepseek-reasoner"),
    ),
}
