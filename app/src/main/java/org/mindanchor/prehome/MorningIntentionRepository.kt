package org.mindanchor.prehome

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.intentionDataStore by preferencesDataStore(name = "morning_intention")

/**
 * v0.26+ (spec Phase 1) — the morning intention
 * store. One free-text intention per day; the
 * `PreHomeActivity` shows it on the moment-of-pause
 * surface, the same one the user wrote when they
 * woke up. The store is keyed on the date so the
 * user can read yesterday's intention without it
 * overwriting today's, and the lock-screen widget
 * (a follow-up) reads from the same store.
 *
 * ## Why a separate DataStore
 *
 * The intention is not a letter, not a note, and not
 * a friction data point. Mixing it into any of the
 * existing stores would either inflate the existing
 * shape (the Letter data class is dated but is
 * about the LLM letter) or be lost when the user
 * empties the underlying store (Notes, Open Loops).
 * A 1-key DataStore is the right shape.
 *
 * ## Why per-day
 *
 * The intention is a morning-of thing; yesterday's
 * intention is not today's. Keeping the key date-bound
 * also means the lock-screen widget can show the most
 * recent (yesterday's) intention as a fallback when
 * the user has not yet set one today, with no extra
 * "is this today?" logic.
 */
class MorningIntentionRepository(private val context: Context) {

    private fun intentionKey(date: LocalDate) =
        stringPreferencesKey("intention:$date")

    private fun askedKey(date: LocalDate) =
        stringPreferencesKey("asked:$date")

    /**
     * The user's intention for the given date, or
     * null when none was written. The first-time
     * experience is the empty string; the
     * `Promptly.notEmpty` check decides whether to
     * show the prompt.
     */
    suspend fun read(date: LocalDate): String? {
        val prefs = context.intentionDataStore.data.first()
        return prefs[intentionKey(date)]?.takeIf { it.isNotBlank() }
    }

    /**
     * Writes the intention. A blank value is rejected
     * (the store is not the place for empty strings;
     * callers should leave the key absent instead).
     */
    suspend fun write(date: LocalDate, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        context.intentionDataStore.edit { prefs ->
            prefs[intentionKey(date)] = clean
        }
    }

    /**
     * Whether the launcher has already asked the user
     * for today's intention. The "Skip to home" affordance
     * is one-tap and the launcher does not nag, so the
     * question is asked at most once per day. The flag is
     * independent of the intention body: a user can
     * skip without writing, or write and then re-open
     * the launcher.
     */
    val asked: Flow<Boolean> = context.intentionDataStore.data.map { prefs ->
        prefs[askedKey(LocalDate.now())] == "1"
    }

    suspend fun markAsked(date: LocalDate) {
        context.intentionDataStore.edit { prefs ->
            prefs[askedKey(date)] = "1"
        }
    }

    /**
     * The most recent non-empty intention across the
     * last 30 days, paired with its date. The lock-
     * screen widget (a follow-up) reads this to fall
     * back to yesterday's intention when the user has
     * not yet set one today.
     */
    val mostRecent: Flow<Pair<LocalDate, String>?> =
        context.intentionDataStore.data.map { prefs ->
            (0L..30L)
                .map { LocalDate.now().minusDays(it) }
                .firstNotNullOfOrNull { date ->
                    prefs[intentionKey(date)]?.takeIf { it.isNotBlank() }?.let { date to it }
                }
        }
}
