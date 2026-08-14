@file:Suppress("MaxLineLength", "FunctionNaming", "MagicNumber", "ReturnCount")
package org.mindanchor.chain

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.chainStore by preferencesDataStore(name = "chain_store")

/**
 * v0.26.1 §3.4: the "What just happened?" five-field chain capture.
 *
 * The five fields the user fills in for a single chain entry are
 * encoded as a single newline-separated line of `key\tvalue` pairs.
 * One line per capture. Append-only — entries are never edited, only
 * added or (rarely) cleared by the user. A blank entry is dropped
 * on read so a half-finished capture cannot survive a process
 * restart and then look like a meaningful one.
 *
 * The fields are: event / interpretation / part / want / part-to-bring.
 * The names are deliberately short — they are labels, not
 * instructions. The screen tells the user what each field is for in
 * plain English; the storage layer does not have to know.
 */
data class ChainCapture(
    val atMillis: Long,
    val event: String,
    val interpretation: String,
    val part: String,
    val want: String,
    val partToBring: String,
)

internal object ChainLedger {

    private const val SEP = "\t"
    private const val NL = "\n"

    fun encode(c: ChainCapture): String =
        // The at-millis stamp is the first field so a chronological
        // sort is a string sort on the stored file. Newlines in
        // user input are squashed — the entries are line-oriented.
        listOf(
            c.atMillis.toString(),
            c.event,
            c.interpretation,
            c.part,
            c.want,
            c.partToBring,
        ).joinToString(SEP) { it.replace("\n", " ").replace("\t", " ") }

    fun decode(raw: String): List<ChainCapture> = raw.lineSequence()
        .mapNotNull(::decodeLine)
        .sortedBy { it.atMillis }
        .toList()

    private fun decodeLine(line: String): ChainCapture? {
        if (line.isBlank()) return null
        val parts = line.split(SEP)
        if (parts.size < 6) return null
        val atMillis = parts[0].toLongOrNull() ?: return null
        // A blank any-field is treated as "this entry was half
        // written" and the line is dropped. The screen would
        // not have saved a half-empty entry, but a corrupt
        // file must not surface an empty row in the export.
        if (parts.drop(1).all { it.isBlank() }) return null
        return ChainCapture(
            atMillis = atMillis,
            event = parts[1],
            interpretation = parts[2],
            part = parts[3],
            want = parts[4],
            partToBring = parts[5],
        )
    }

    fun encodeAll(captures: List<ChainCapture>): String =
        captures.joinToString(NL, postfix = NL) { encode(it) }
}

/**
 * The chain-capture DataStore. One key, `captures`, holding the
 * newline-separated ledger. Append on save, full replace on clear.
 *
 * Same pattern as the rest of the app's append-only stores
 * (NotesPrefs, MomentStore) — a text-encoded blob behind a single
 * DataStore key, so a corrupt line costs one entry, never the
 * surface.
 */
class ChainCapturePrefs(private val context: Context) {

    private val capturesKey = stringPreferencesKey("captures")

    val captures: Flow<List<ChainCapture>> =
        context.chainStore.data.map { ChainLedger.decode(it[capturesKey].orEmpty()) }

    suspend fun append(capture: ChainCapture) {
        context.chainStore.edit { prefs ->
            val current = ChainLedger.decode(prefs[capturesKey].orEmpty())
            prefs[capturesKey] = ChainLedger.encodeAll(current + capture)
        }
    }

    suspend fun clear() {
        context.chainStore.edit { it.remove(capturesKey) }
    }
}
