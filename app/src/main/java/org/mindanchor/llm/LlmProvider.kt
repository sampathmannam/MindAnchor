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
        // v0.72.x: OpenRouter retired the `:free` slug on
        // the Llama 3.3 70B model in 2026. The free tier
        // is now sold separately as the bare slug
        // `meta-llama/llama-3.3-70b-instruct`. The other
        // free slugs (`google/gemini-2.0-flash-exp:free`,
        // `qwen/qwen-2.5-72b-instruct:free`) still exist;
        // if those retire too, drop them and keep only
        // the bare slug.
        defaultModel = "meta-llama/llama-3.3-70b-instruct",
        isFree = true,
        suggestedModels = listOf(
            "meta-llama/llama-3.3-70b-instruct",
            "google/gemini-2.0-flash-exp:free",
            "qwen/qwen-2.5-72b-instruct:free",
        ),
    ),
    GROQ(
        displayName = "Groq",
        signupUrl = "https://console.groq.com/keys",
        baseUrl = "https://api.groq.com/openai/v1/",
        // v0.72.x: Groq retired the llama-3.x and
        // llama-4-maverick preview models for most
        // accounts in 2026; what's actually on the
        // /v1/models list for free-tier and standard
        // keys is the OpenAI-gpt-oss-20b / 120b pair
        // plus groq/compound. Free accounts get
        // gpt-oss-20b by default; paid accounts get
        // gpt-oss-120b. The Groq team will move things
        // around again — re-run /v1/models against
        // your key and update this list if the
        // default 404s.
        defaultModel = "openai/gpt-oss-20b",
        isFree = true,
        suggestedModels = listOf(
            "openai/gpt-oss-20b",
            "openai/gpt-oss-120b",
            "groq/compound-mini",
        ),
    ),
}
