@file:Suppress("MaxLineLength", "FunctionNaming", "MagicNumber", "ReturnCount")
package org.mindanchor.ifs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ifsStore by preferencesDataStore(name = "ifs_store")

/**
 * v0.26.1 §3.4: "Which part is loud?" IFS picker.
 *
 * One entry per pick: which part the user named and when. The list
 * is append-only; the picker screen reads the *most recent* pick to
 * show what the user last selected. The export reads the full
 * history. A blank part name is dropped on read so a half-finished
 * pick cannot survive a process restart and look like a meaningful
 * one.
 */
data class IfsPick(
    val atMillis: Long,
    val partName: String,
)

internal object IfsLedger {

    private const val SEP = "\t"

    fun encode(p: IfsPick): String =
        "${p.atMillis}${SEP}${p.partName.replace("\n", " ").replace("\t", " ")}"

    fun decode(raw: String): List<IfsPick> = raw.lineSequence()
        .mapNotNull(::decodeLine)
        .sortedBy { it.atMillis }
        .toList()

    private fun decodeLine(line: String): IfsPick? {
        if (line.isBlank()) return null
        val tab = line.indexOf(SEP)
        if (tab <= 0) return null
        val at = line.substring(0, tab).toLongOrNull() ?: return null
        val name = line.substring(tab + 1).trim()
        if (name.isEmpty()) return null
        return IfsPick(atMillis = at, partName = name)
    }

    fun encodeAll(picks: List<IfsPick>): String =
        picks.joinToString("\n", postfix = "\n") { encode(it) }
}

/**
 * The IFS picker DataStore. One key, `picks`, holding the
 * newline-separated ledger. Append on save, full replace on clear.
 */
class IfsPickerPrefs(private val context: Context) {

    private val picksKey = stringPreferencesKey("picks")

    val picks: Flow<List<IfsPick>> =
        context.ifsStore.data.map { IfsLedger.decode(it[picksKey].orEmpty()) }

    /** The most recent pick, or null when no pick is on file. */
    val latest: Flow<IfsPick?> = picks.map { it.lastOrNull() }

    suspend fun append(pick: IfsPick) {
        context.ifsStore.edit { prefs ->
            val current = IfsLedger.decode(prefs[picksKey].orEmpty())
            prefs[picksKey] = IfsLedger.encodeAll(current + pick)
        }
    }

    suspend fun clear() {
        context.ifsStore.edit { it.remove(picksKey) }
    }

    companion object {

        /**
         * The default parts the picker offers.
         *
         * The names are deliberately short and recognisable from
         * the IFS literature (Schwartz 1995, *Internal Family
         * Systems Therapy*). They are defaults, not requirements:
         * the picker is a grid of named parts the user can pick
         * from. The store keeps the picked name verbatim, so a
         * future translator adding localised names is a one-line
         * change in this constant plus a string-resource lookup.
         */
        val DEFAULT_PARTS: List<String> = listOf(
            "The angry part",
            "The scared part",
            "The part that wants to disappear",
            "The critic part",
            "The protector part",
            "The critic's critic",
            "The one who notices",
        )
    }
}
