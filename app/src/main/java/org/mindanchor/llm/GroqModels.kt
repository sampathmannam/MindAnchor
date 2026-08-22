package org.mindanchor.llm

/**
 * The three Groq model IDs the user can pick from in
 * Settings → Reading → Daily letter (LLM). All three are
 * available on Groq's permanent free tier
 * (30 RPM / 500K TPD; 1.5K TPD cap per request).
 *
 * The default is `llama-3.3-70b-versatile` — the best
 * quality + speed trade-off for the 200-300 word daily
 * letter. `llama-3.1-8b-instant` is the fallback for users
 * who hit the 70B rate limit. `llama-4-scout-17b-16e-instruct`
 * is a newer mid-size model — kept as an option but not the
 * default until the 70B's quality is independently verified.
 *
 * Verified August 2026: all three IDs are accepted by
 * https://api.groq.com/openai/v1/models with a valid free-tier key.
 */
object GroqModels {
    const val DEFAULT = "llama-3.3-70b-versatile"
    const val LLAMA_70B = "llama-3.3-70b-versatile"
    const val LLAMA_SCOUT = "llama-4-scout-17b-16e-instruct"
    const val LLAMA_8B = "llama-3.1-8b-instant"

    val ALL: List<String> = listOf(LLAMA_70B, LLAMA_SCOUT, LLAMA_8B)
}
