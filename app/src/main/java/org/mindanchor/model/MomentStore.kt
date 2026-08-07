package org.mindanchor.model

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ema")

/**
 * Turns a [Moment] into one line of text and back.
 *
 * Pure on purpose, and it follows
 * [org.mindanchor.friction.GateLedger] exactly: text rather than JSON
 * because a line is the only unit this is ever read or written in, and a
 * corrupt line must cost a day's label, never the home screen. Fields
 * that fail [Moment.isValid] or fall outside a real minute-of-day are
 * treated the same as a line that will not parse at all — skipped, not
 * thrown on.
 */
object MomentLedger {

    fun encode(moments: List<Moment>): String =
        moments.joinToString("\n") { "${it.valence}\t${it.arousal}\t${it.atMinuteOfDay}\t${it.day}" }

    fun decode(raw: String): List<Moment> = raw.lineSequence().mapNotNull(::decodeLine).toList()

    private fun decodeLine(line: String): Moment? {
        val parts = line.split('\t')
        if (parts.size < 4) return null
        val valence = parts[0].toIntOrNull() ?: return null
        val arousal = parts[1].toIntOrNull() ?: return null
        val atMinuteOfDay = parts[2].toIntOrNull() ?: return null
        val day = parts[3]
        if (!Moment.isValid(valence) || !Moment.isValid(arousal)) return null
        if (atMinuteOfDay !in 0..1439) return null
        if (day.isBlank()) return null
        return Moment(valence, arousal, atMinuteOfDay, day)
    }
}

/**
 * Every check-in the person has answered, and whether the feature is on
 * at all.
 *
 * Same shape as [org.mindanchor.data.FrictionPrefs]: a thin DataStore
 * layer over [MomentLedger], which carries all the format knowledge. One
 * more line is appended per answered check-in and nothing is ever
 * rewritten or pruned — at four a day this is still a small text file
 * after years, and "no Room migration" was the brief.
 */
class MomentStore(private val context: Context) {

    private val enabledKey = booleanPreferencesKey("ema_enabled")
    private val momentsKey = stringPreferencesKey("moments")

    /** Off until asked for, like everything else in this app. */
    val enabled: Flow<Boolean> = context.dataStore.data.map { it[enabledKey] ?: false }

    suspend fun setEnabled(value: Boolean) {
        context.dataStore.edit { it[enabledKey] = value }
    }

    /** Every check-in ever answered, oldest first. */
    val moments: Flow<List<Moment>> =
        context.dataStore.data.map { MomentLedger.decode(it[momentsKey].orEmpty()) }

    /** How many so far — the number [EmaSchedule.promptsPerDay] tapers against. */
    val count: Flow<Int> = moments.map { it.size }

    suspend fun append(moment: Moment) {
        context.dataStore.edit { prefs ->
            val current = MomentLedger.decode(prefs[momentsKey].orEmpty())
            prefs[momentsKey] = MomentLedger.encode(current + moment)
        }
    }
}
