package org.mindanchor.letters

import java.time.LocalDate

/**
 * One letter, stored as a flat string. Pre-v0.25.7 letters
 * have only [date] and [body]; v0.25.7+ letters also carry
 * the 5 nullable metadata fields populated by the LLM
 * client (provider, model, promptTokens, completionTokens,
 * durationMs). The codec preserves the round-trip for both
 * shapes — old letters read back with all metadata null
 * and render the same in the reader.
 *
 * @property date the day the letter was written FOR
 * @property body the letter's text, 2-3 paragraphs
 * @property provider the LLM provider id (e.g. "groq") for
 *   LLM-driven letters; null for canned (pre-v0.25.7) letters
 * @property model the LLM model id (e.g. "llama-3.3-70b-versatile")
 * @property promptTokens the input token count, for the
 *   reader's metadata footer
 * @property completionTokens the output token count
 * @property durationMs how long the generation took
 */
data class Letter(
    val date: LocalDate,
    val body: String,
    val provider: String? = null,
    val model: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val durationMs: Long? = null,
)

/**
 * The wire format for letters on disk. Pre-v0.25.7 lines
 * are `date\tbody`; v0.25.7+ lines are
 * `date\tbody\tprovider\tmodel\tpromptTokens\tcompletionTokens\tdurationMs`.
 * The decoder splits on `\t` and uses the field count to
 * know which fields are present — old lines (2 fields)
 * decode to a Letter with all metadata null.
 */
object LetterLedger {

    fun encode(letters: List<Letter>): String =
        letters.joinToString(separator = "\n", postfix = "\n") { letter ->
            val base = "${letter.date}\t${letter.body.replace("\n", " ")}"
            if (letter.provider == null) {
                base // pre-v0.25.7 shape
            } else {
                listOf(
                    base,
                    letter.provider,
                    letter.model.orEmpty(),
                    letter.promptTokens?.toString().orEmpty(),
                    letter.completionTokens?.toString().orEmpty(),
                    letter.durationMs?.toString().orEmpty(),
                ).joinToString(separator = "\t")
            }
        }

    fun decode(raw: String): List<Letter> = raw.lineSequence()
        .mapNotNull(::decodeLine)
        .sortedBy { it.date }
        .toList()

    private fun decodeLine(line: String): Letter? {
        if (line.isBlank()) return null
        val parts = line.split('\t')
        if (parts.size < 2) return null
        val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return null
        val body = parts[1].trim()
        if (body.isEmpty()) return null
        if (parts.size == 2) {
            // Pre-v0.25.7 shape: no metadata.
            return Letter(date = date, body = body)
        }
        // v0.25.7+ shape: 5 metadata fields, all optional.
        val provider = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }
        val model = parts.getOrNull(3)?.takeIf { it.isNotEmpty() }
        val promptTokens = parts.getOrNull(4)?.toIntOrNull()
        val completionTokens = parts.getOrNull(5)?.toIntOrNull()
        val durationMs = parts.getOrNull(6)?.toLongOrNull()
        return Letter(
            date = date,
            body = body,
            provider = provider,
            model = model,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            durationMs = durationMs,
        )
    }
}
