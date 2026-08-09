package org.mindanchor.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mindanchor.model.CheckIn
import org.mindanchor.model.CheckInState
import org.mindanchor.model.CheckInStore

/**
 * The on-device check-in DataStore. v0.20.1 round 5
 * (docs/research/26-notes-and-check-in.md).
 *
 * The DataStore is *separate* from the notes and
 * friction DataStores: check-ins are launcher-gated
 * EMA responses, not user-authored free text and not
 * friction configuration. Mixing them would conflate
 * three independent concerns; the sealed-codecs HMAC
 * layer would invalidate the friction data on any
 * check-in write, and check-in rate-limit semantics
 * are different from notes.
 *
 * The on-disk format is plain text; the sealed-codecs
 * wrapper on work/codec-hmac adds the HMAC layer (item
 * D threat model). Note: the *rate-limit state* (the
 * `lastAcceptedMillis` / `acceptedToday` / etc.) is
 * *transient* and never written to disk — only the
 * accepted check-ins themselves are persisted. The
 * launcher prefers a missed check-in over a permanent
 * record of "user said no 47 times" (the no-mood-
 * inference rule, brief §B3/B6).
 */
private val Context.checkInsDataStore by preferencesDataStore(name = "checkins")

/**
 * The check-in prefs. Thin DataStore layer over
 * [CheckInStore], which carries all the format
 * knowledge. Same pattern as
 * [org.mindanchor.model.MomentStore] / [MomentLedger]
 * and [NotesPrefs].
 */
class CheckInPrefs(private val context: Context) {

    private val checkInsKey = stringPreferencesKey("checkins")

    /**
     * The user's accepted check-ins, in append-order.
     * The rate-limit windowing happens in the engine,
     * not the store.
     */
    val checkIns: Flow<CheckInState> =
        context.checkInsDataStore.data.map {
            CheckInState(CheckInStore.decode(it[checkInsKey].orEmpty()))
        }

    /**
     * Append a new accepted check-in. The check-in
     * is appended to the end of the list; the
     * engine computes daily windows.
     */
    suspend fun add(checkIn: CheckIn) {
        context.checkInsDataStore.edit { prefs ->
            val current = CheckInStore.decode(prefs[checkInsKey].orEmpty())
            prefs[checkInsKey] = CheckInStore.encode(current + checkIn)
        }
    }

    /**
     * Clear all accepted check-ins. Not currently
     * exposed in the UI; present for the future
     * "clear data" affordance.
     */
    suspend fun clear() {
        context.checkInsDataStore.edit { prefs ->
            prefs[checkInsKey] = CheckInStore.encode(emptyList())
        }
    }
}
