package org.mindanchor.friction

/**
 * The per-app *time-box* default. A user who picks "Open
 * for 10 minutes" for Instagram can have that choice
 * remembered for the next reach.
 *
 * This is a *default*, not a *cap*. The friction gate
 * surfaces the stored value as a pre-selected option in
 * the existing 5/10/20 button row; the user can still
 * pick any of 5, 10, 20, or "open untimed" with one tap.
 *
 * ## Evidence
 *
 * The evidence base is in `docs/research/22`. Summary:
 *  - Lally 2010 (Eur J Soc Psychol 40(6):998–1009):
 *    habit formation is context-dependent. Per-app
 *    consistency is the mechanism.
 *  - Adhikari & Alessandretti 2023 PNAS
 *    (doi:10.1073/pnas.2213114120): 36% of opens are
 *    dismissed by a 1-sec pause. The 5/10/20 buttons
 *    serve the same function; a per-app default
 *    reduces the *decision cost* at the moment of pause.
 *  - Gollwitzer 1999 (American Psychologist
 *    54(7):493–503): the *time* in an implementation
 *    intention is part of the binding. A per-app length
 *    is the no-plan version of that.
 *  - Wood & Neal 2007 (Psychol Rev 114(4):843–863):
 *    habits are context-specific, so per-app defaults
 *    align with the underlying psychology.
 *
 * The data layer is independent of the if-then plan
 * (item E) for two reasons:
 *  1. A user may want a per-app default *without* writing
 *     a complete if-then plan (cue + action both filled).
 *  2. Wiring the default through the plan's
 *     `defaultMinutes` field would force the gate to
 *     re-check `isComplete` on every reach, which is
 *     unnecessary.
 */
data class PerAppSessionLength(
    /**
     * The per-app map of `package name -> minutes`.
     * Minutes are clamped to `[1, 120]`. A package
     * name is non-blank.
     */
    val perAppMinutes: Map<String, Long> = emptyMap(),
) {
    /**
     * The default minutes for a given package. Returns
     * the stored value if the user has picked one, or
     * [FALLBACK_MINUTES] otherwise. Never null.
     */
    fun defaultMinutes(pkg: String): Long {
        if (pkg.isBlank()) return FALLBACK_MINUTES
        return perAppMinutes[pkg] ?: FALLBACK_MINUTES
    }

    /**
     * Record a new choice for [pkg]. Returns a new
     * [PerAppSessionLength] with the entry added (or
     * overwritten). The minutes are clamped to
     * `[1, 120]`. A blank package name is a no-op.
     */
    fun record(pkg: String, minutes: Long): PerAppSessionLength {
        if (pkg.isBlank()) return this
        val clamped = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        return copy(perAppMinutes = perAppMinutes + (pkg to clamped))
    }

    /**
     * Forget a package. Returns a new [PerAppSessionLength]
     * without the entry. A blank package name is a no-op.
     */
    fun forget(pkg: String): PerAppSessionLength {
        if (pkg.isBlank()) return this
        return copy(perAppMinutes = perAppMinutes - pkg)
    }

    companion object {
        /**
         * The minutes returned by [defaultMinutes] when
         * the user has never picked a length for the
         * package. The middle of the 5/10/20 button row
         * is the most-tapped research time-box, and
         * matches the if-then plan's
         * `defaultMinutes` precedent.
         */
        const val FALLBACK_MINUTES: Long = 10L

        /**
         * The minimum and maximum a user can record.
         * Below 1 minute is not useful (the breath gate
         * itself is 6 seconds). Above 2 hours is not
         * "a time-box" in the literature sense (Lally
         * 2010 measured habits at the daily level, not
         * the 2-hour level).
         */
        const val MIN_MINUTES: Long = 1L
        const val MAX_MINUTES: Long = 120L
    }
}

/**
 * Storage codec for [PerAppSessionLength]. Same shape as
 * [IfThenPlanStore]: one app per line, tab-separated
 * `package<TAB>minutes`. Plain-text round trip; no JSON;
 * no migration.
 *
 * The codec is a *dumb* text parser. Validation against
 * installed packages, rejection of blank keys, clamping
 * of out-of-range minutes — all of that is the caller's
 * job (this layer is a pure function and is unit-tested
 * with fixture input).
 */
object PerAppSessionLengthStore {

    /**
     * Encode a [PerAppSessionLength] to a stable text
     * form. The form is `package<TAB>minutes\n` per
     * entry, sorted by package name for diff stability
     * in the data store.
     */
    fun encode(state: PerAppSessionLength): String =
        state.perAppMinutes.entries
            .filter { it.key.isNotBlank() }
            .sortedBy { it.key }
            .joinToString("\n") { (pkg, minutes) ->
                val clamped = minutes.coerceIn(
                    PerAppSessionLength.MIN_MINUTES,
                    PerAppSessionLength.MAX_MINUTES,
                )
                "$pkg\t$clamped"
            }

    /**
     * Decode a text form back into a [PerAppSessionLength].
     * Blank lines, malformed lines, and out-of-range
     * minutes are silently skipped (the codec is
     * *dumb*; a malformed entry cannot poison the
     * rest of the data).
     */
    fun decode(raw: String): PerAppSessionLength {
        val map = LinkedHashMap<String, Long>()
        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split('\t')
            if (parts.size < 2) continue
            val pkg = parts[0].trim()
            if (pkg.isEmpty()) continue
            val minutes = parts[1].toLongOrNull() ?: continue
            val clamped = minutes.coerceIn(
                PerAppSessionLength.MIN_MINUTES,
                PerAppSessionLength.MAX_MINUTES,
            )
            map[pkg] = clamped
        }
        return PerAppSessionLength(map)
    }
}
