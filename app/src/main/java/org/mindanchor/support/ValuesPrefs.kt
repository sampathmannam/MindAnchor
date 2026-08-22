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

private val Context.valuesStore by preferencesDataStore(name = "act_values")

/**
 * v0.29.0: persistence for the ACT values card.
 *
 * A single entry per user, keyed by `act_values_card`. Stored
 * as a JSON string in a preferencesDataStore. The user can
 * revisit the surface and see their previously-written values;
 * the screen reads the saved card on first composition and
 * seeds the input fields with it.
 *
 * ## What is and is not stored
 *
 * The card stores the user's own words — one short sentence
 * per value domain. It does NOT store a score, a comparison,
 * a chart series, or anything that would imply an
 * interpretation the project is not allowed to make. The
 * values are the user's chosen life directions, full stop.
 *
 * ## Why a single card, not per-domain
 *
 * The ACT values exercise is *one* card (Wilson & Murrell
 * 2004; Hayes et al. 1999/2004). The 8 domains are the
 * standard ACT taxonomy; the values live together. A user
 * revisits the card, sees their words, and either keeps
 * them or rewrites them. Per-domain persistence would
 * imply that the domains are independent — they are not,
 * they are aspects of a single life.
 */
class ValuesPrefs(private val context: Context) {

    private val cardKey = stringPreferencesKey("act_values_card")

    suspend fun save(card: ValuesCard) {
        val json = Json.encodeToString(ValuesCard.serializer(), card)
        context.valuesStore.edit { prefs ->
            prefs[cardKey] = json
        }
    }

    /**
     * Load the saved values card. Returns an empty
     * [ValuesCard] if the user has never saved one.
     *
     * Errors in the stored JSON (e.g. a future version's
     * schema) are swallowed and treated as "no saved card" —
     * the user sees an empty form to fill in again. The
     * alternative (crash) would be wrong for a single-user
     * app where the user is the only person who can fix it.
     */
    suspend fun load(): ValuesCard {
        val prefs = context.valuesStore.data.first()
        val raw = prefs[cardKey] ?: return ValuesCard()
        return runCatching { Json.decodeFromString(ValuesCard.serializer(), raw) }
            .getOrNull() ?: ValuesCard()
    }

    /** Live stream of the saved card. Emits an empty
     *  [ValuesCard] if the user has never saved one. */
    fun loadFlow(): Flow<ValuesCard> = context.valuesStore.data.map {
        val raw = it[cardKey] ?: return@map ValuesCard()
        runCatching { Json.decodeFromString(ValuesCard.serializer(), raw) }
            .getOrNull() ?: ValuesCard()
    }
}

/**
 * v0.29.0: the eight value domains from the standard ACT
 * values taxonomy (Wilson & Murrell 2004; Hayes et al.
 * 1999/2004). Each field is the user's *one sentence* in
 * that domain — "in this corner of my life, what do I want
 * it to be about?" All fields are optional and default to
 * an empty string.
 *
 * The eight domains are:
 *  - **relationships** — the people I want in my life, the
 *    kind of person I want to be to them
 *  - **health** — how I want to look after my body (sleep,
 *    food, movement, rest)
 *  - **work** — the contribution I want to make, the skills
 *    I want to use, the kind of colleague I want to be
 *  - **growth** — what I want to learn, the person I want
 *    to be becoming
 *  - **leisure** — how I want to play, what I want to do
 *    for no reason at all
 *  - **spirituality** — whatever the user means by this
 *    (faith, awe, nature, meaning)
 *  - **community** — the world outside my door, the people
 *    I have not met, the place I live
 *  - **parenting** — if the user has or wants children, the
 *    parent they want to be
 */
@Serializable
data class ValuesCard(
    val relationships: String = "",
    val health: String = "",
    val work: String = "",
    val growth: String = "",
    val leisure: String = "",
    val spirituality: String = "",
    val community: String = "",
    val parenting: String = "",
)
