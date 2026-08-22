@file:Suppress("MagicNumber")
package org.mindanchor.support

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val Context.receiptsStore by preferencesDataStore(name = "receipts")

/**
 * v0.38.0: persistence for the DBT "PLEASE mastery" log
 * (Linehan 1993 ch. 9). One short, one-line entry per day —
 * "what I did, however small." No score, no streak, no chart.
 *
 * The term "receipts" comes from Marsha Linehan's DBT
 * training: when a person with BPD faces a hard moment,
 * the receipts are the literal evidence that they have
 * handled hard moments before. They are not a journal of
 * feelings; they are a log of accomplished things, however
 * small ("walked to the corner", "ate breakfast", "answered
 * one email").
 *
 * What is NOT stored: a count, a streak, a score, a comparison
 * to yesterday, anything that would imply an evaluation.
 * The list is a list.
 *
 * Storage shape: one JSON-encoded [Receipt] per ISO date,
 * keyed by `receipts_<date>`. The user's words live
 * unencrypted on disk (same policy as the notes and diary
 * card stores); the threat model is a one-time forensic
 * seizure of the device, not a remote attacker.
 */
class ReceiptsPrefs(private val context: Context) {

    private fun keyFor(date: LocalDate): String = "receipts_${date}"

    suspend fun save(date: LocalDate, text: String) {
        val entry = Receipt(date = date.toString(), text = text.trim())
        val json = Json.encodeToString(Receipt.serializer(), entry)
        context.receiptsStore.edit { prefs ->
            prefs[stringPreferencesKey(keyFor(date))] = json
        }
    }

    suspend fun load(date: LocalDate): Receipt? {
        val prefs = context.receiptsStore.data.first()
        val raw = prefs[stringPreferencesKey(keyFor(date))] ?: return null
        return runCatching { Json.decodeFromString(Receipt.serializer(), raw) }
            .getOrNull()
    }

    /** All saved receipts, newest first. */
    suspend fun list(): List<Receipt> {
        val prefs = context.receiptsStore.data.first()
        return prefs.asMap().entries
            .mapNotNull { (key, value) ->
                if (key.name.startsWith("receipts_") && value is String) {
                    runCatching {
                        Json.decodeFromString(Receipt.serializer(), value)
                    }.getOrNull()
                } else null
            }
            .sortedByDescending { it.date }
    }
}

@Serializable
data class Receipt(
    val date: String,
    val text: String,
)
