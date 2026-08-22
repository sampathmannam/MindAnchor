package org.mindanchor.letters

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.letterLogDataStore by preferencesDataStore(name = "letter_generation_log")

/**
 * One generation attempt, recorded for audit. No letter
 * body, no journal content, no notes — just the metadata.
 * Used by future analysis ("how often does the user hit
 * rate limits?", "is Llama-3.3 70B's quality good enough
 * or should we switch?") and pinned by
 * [LetterGenerationLogTest].
 *
 * @property date the day the letter was written FOR
 * @property provider the LLM provider id (e.g. "groq")
 * @property model the LLM model id
 * @property promptTokens input token count, null on error
 * @property completionTokens output token count, null on error
 * @property durationMs total request time
 * @property errorClass the [org.mindanchor.llm.LetterError]
 *   variant simpleName, null on success
 * @property errorMessage the user-facing message, null on success
 * @property timestampMillis epoch millis when the generation
 *   finished (success or error)
 */
data class LetterLogEntry(
    val date: LocalDate,
    val provider: String,
    val model: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val durationMs: Long,
    val errorClass: String?,
    val errorMessage: String?,
    val timestampMillis: Long,
)

/**
 * Append-only DataStore list of generation attempts.
 * The wire format is one line per entry, tab-separated:
 * `date	provider	model	promptTokens	completionTokens	durationMs	errorClass	errorMessage	timestamp`.
 * Empty trailing fields are stored as empty strings and
 * decoded as null per the [LetterLedger] pattern.
 */
class LetterGenerationLog(private val context: Context) {

    private val entriesKey = stringPreferencesKey("entries")

    val entries: Flow<List<LetterLogEntry>> = context.letterLogDataStore.data.map { prefs ->
        prefs[entriesKey].orEmpty()
            .lineSequence()
            .mapNotNull(::decodeLine)
            .toList()
    }

    suspend fun append(entry: LetterLogEntry) {
        context.letterLogDataStore.edit { prefs ->
            val current = prefs[entriesKey].orEmpty()
            prefs[entriesKey] = if (current.isEmpty()) encode(entry) + "\n"
            else current + encode(entry) + "\n"
        }
    }

    /**
     * Clears every key. Test-only — same pattern as
     * [LetterStore.reset].
     */
    internal suspend fun reset() {
        context.letterLogDataStore.edit { it.clear() }
    }

    private fun encode(e: LetterLogEntry): String = listOf(
        e.date.toString(),
        e.provider,
        e.model,
        e.promptTokens?.toString().orEmpty(),
        e.completionTokens?.toString().orEmpty(),
        e.durationMs.toString(),
        e.errorClass.orEmpty(),
        e.errorMessage.orEmpty(),
        e.timestampMillis.toString(),
    ).joinToString(separator = "\t")

    private fun decodeLine(line: String): LetterLogEntry? {
        if (line.isBlank()) return null
        val parts = line.split('\t')
        if (parts.size < 9) return null
        val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return null
        return LetterLogEntry(
            date = date,
            provider = parts[1],
            model = parts[2],
            promptTokens = parts[3].toIntOrNull(),
            completionTokens = parts[4].toIntOrNull(),
            durationMs = parts[5].toLongOrNull() ?: 0L,
            errorClass = parts[6].takeIf { it.isNotEmpty() },
            errorMessage = parts[7].takeIf { it.isNotEmpty() },
            timestampMillis = parts[8].toLongOrNull() ?: 0L,
        )
    }
}
