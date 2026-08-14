@file:Suppress("MaxLineLength", "FunctionNaming", "MagicNumber")
package org.mindanchor.watch

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.smsToneCheckStore by preferencesDataStore(name = "sms_tone_check")

/**
 * v0.26.1 §3.3: the SMS tone-check side-channel.
 *
 * When an incoming SMS arrives, the [SmsInterceptor] captures the
 * sender + first ~280 chars of the body, writes a record here, and
 * posts a high-importance notification with a deep-link to
 * [org.mindanchor.friction.BeforeYouSendInterstitial] carrying the
 * SMS context as extras.
 *
 * The store is the audit log: every intercepted SMS is appended, the
 * notification is the prompt. The user can dismiss the notification
 * without opening the interstitial, and the record stays.
 *
 * The field is the *minimum* needed to surface the prompt: the
 * sender is what the user has to think about, and the body excerpt
 * is what they might have said. The full body is never stored —
 * storing the body of someone else's message on someone else's
 * behalf, before the user has chosen to forward it, is a privacy
 * boundary the §3.3 brief is explicit about.
 */
data class SmsToneCheck(
    val atMillis: Long,
    val sender: String,
    val bodyExcerpt: String,
)

internal object SmsToneCheckLedger {

    private const val SEP = "\t"
    private const val MAX_BODY_CHARS = 280

    fun encode(s: SmsToneCheck): String =
        listOf(
            s.atMillis.toString(),
            s.sender.replace("\n", " ").replace("\t", " "),
            s.bodyExcerpt.replace("\n", " ").replace("\t", " "),
        ).joinToString(SEP)

    fun decode(raw: String): List<SmsToneCheck> = raw.lineSequence()
        .mapNotNull(::decodeLine)
        .sortedBy { it.atMillis }
        .toList()

    private fun decodeLine(line: String): SmsToneCheck? {
        if (line.isBlank()) return null
        val parts = line.split(SEP)
        if (parts.size < 3) return null
        val at = parts[0].toLongOrNull() ?: return null
        return SmsToneCheck(
            atMillis = at,
            sender = parts[1],
            bodyExcerpt = parts[2],
        )
    }

    fun encodeAll(records: List<SmsToneCheck>): String =
        records.joinToString("\n", postfix = "\n") { encode(it) }

    /**
     * Truncate a raw SMS body to the storage excerpt.
     *
     * A full SMS can be hundreds of characters and may contain
     * personally identifying information the user has not
     * consented to keep on disk. The excerpt is what fits the
     * interstitial prompt; storing more is a privacy over-reach.
     */
    fun excerpt(raw: String): String =
        raw.replace("\n", " ").replace("\r", " ").trim().take(MAX_BODY_CHARS)
}

/**
 * The SMS tone-check DataStore. One key, `records`, holding the
 * newline-separated ledger. Append on each intercepted SMS.
 */
class SmsToneCheckPrefs(private val context: Context) {

    private val recordsKey = stringPreferencesKey("records")

    val records: Flow<List<SmsToneCheck>> =
        context.smsToneCheckStore.data.map { SmsToneCheckLedger.decode(it[recordsKey].orEmpty()) }

    suspend fun append(record: SmsToneCheck) {
        context.smsToneCheckStore.edit { prefs ->
            val current = SmsToneCheckLedger.decode(prefs[recordsKey].orEmpty())
            prefs[recordsKey] = SmsToneCheckLedger.encodeAll(current + record)
        }
    }

    suspend fun clear() {
        context.smsToneCheckStore.edit { it.remove(recordsKey) }
    }
}
