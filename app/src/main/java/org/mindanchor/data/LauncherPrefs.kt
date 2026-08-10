package org.mindanchor.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "launcher")

/**
 * Local-only launcher preferences. Apps are identified by their flattened
 * ComponentName ("package/class"). Nothing here ever leaves the device.
 */
class LauncherPrefs(private val context: Context) {

    private val favoritesKey = stringPreferencesKey("favorites_ordered")
    private val hiddenKey = stringSetPreferencesKey("hidden")
    private val renamesKey = stringPreferencesKey("renames")

    /**
     * How many times the home surface has been displayed.
     *
     * v0.22.0 (WP-10 step 2): the launcher uses this to surface the
     * "what makes this different" callout for the first
     * [INTRO_CALLOUT_LAUNCHES] launches, then hides it forever. A
     * counter rather than a boolean is the right shape: the same
     * counter can be reused to count, e.g., how many launches
     * happened with no favorites pinned (a different "have you
     * tried X?" affordance), and a "show this once" boolean is
     * the kind of state that gets out of sync with the rest of
     * the launcher's UI when the reset path is forgotten.
     */
    private val launchCountKey = intPreferencesKey("home_launch_count")

    /** Ordered favorites, most important first. Capped at [MAX_FAVORITES]. */
    val favorites: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[favoritesKey]?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
    }

    val hidden: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[hiddenKey] ?: emptySet()
    }

    /** Map of component -> user-chosen label. */
    val renames: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        (prefs[renamesKey] ?: "")
            .lineSequence()
            .mapNotNull { line ->
                val idx = line.indexOf('\t')
                if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
            }
            .toMap()
    }

    suspend fun toggleFavorite(component: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[favoritesKey]?.split(SEPARATOR)?.filter { it.isNotBlank() }
                ?: emptyList()
            val updated = if (component in current) {
                current - component
            } else {
                (current + component).takeLast(MAX_FAVORITES)
            }
            prefs[favoritesKey] = updated.joinToString(SEPARATOR)
        }
    }

    suspend fun setHidden(component: String, hide: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[hiddenKey] ?: emptySet()
            prefs[hiddenKey] = if (hide) current + component else current - component
        }
    }

    // --- Bulk replacement, used only when restoring a backup -------------
    //
    // Restoring is the one time wholesale replacement is right: the person
    // is deliberately saying "make this phone look like that one". Every
    // other path through this class edits a single entry.

    suspend fun replaceFavorites(components: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[favoritesKey] = components
                .filter { it.isNotBlank() }
                .takeLast(MAX_FAVORITES)
                .joinToString(SEPARATOR)
        }
    }

    suspend fun replaceHidden(components: Set<String>) {
        context.dataStore.edit { prefs -> prefs[hiddenKey] = components.filter { it.isNotBlank() }.toSet() }
    }

    suspend fun replaceRenames(renames: Map<String, String>) {
        context.dataStore.edit { prefs ->
            prefs[renamesKey] = renames.entries
                .filter { it.key.isNotBlank() && it.value.isNotBlank() }
                // A tab or newline in a label would corrupt the next read,
                // and a rename comes from a text field the user controls.
                .joinToString("\n") { "${it.key}\t${it.value.replace('\t', ' ').replace('\n', ' ')}" }
        }
    }

    suspend fun rename(component: String, label: String?) {
        context.dataStore.edit { prefs ->
            val current = (prefs[renamesKey] ?: "")
                .lineSequence()
                .filter { it.isNotBlank() && !it.startsWith("$component\t") }
                .toMutableList()
            if (!label.isNullOrBlank()) {
                current += "$component\t$label"
            }
            prefs[renamesKey] = current.joinToString("\n")
        }
    }

    // --- Intro callout -----------------------------------------------------
    //
    // The callout is "what makes this different" — one line at the top of
    // the home surface that points at the friction gate (the headline
    // feature). It shows for the first 3 launches and never again. The
    // 3-launch window is the WP-10 acceptance target: long enough for
    // a participant in the 3rd-party walkthrough to see it, short
    // enough that a real user does not feel nagged.

    /**
     * How many times the home surface has been displayed so far. The
     * callout is shown while this is strictly less than
     * [INTRO_CALLOUT_LAUNCHES]. Defaults to 0 for a fresh install.
     */
    val launchCount: Flow<Int> = context.dataStore.data.map { it[launchCountKey] ?: 0 }

    /**
     * Whether the intro callout should be visible right now. Pure
     * derivation of [launchCount]: shown strictly fewer than
     * [INTRO_CALLOUT_LAUNCHES] times. Exposed as its own Flow so
     * the home screen can `collectAsState()` it without doing
     * the arithmetic.
     */
    val showIntroCallout: Flow<Boolean> = context.dataStore.data.map {
        (it[launchCountKey] ?: 0) < INTRO_CALLOUT_LAUNCHES
    }

    /**
     * Increments the launch count. Called once per home-surface
     * display (the natural place to count, since the callout lives
     * on the home surface and one display = one chance to see it).
     */
    suspend fun recordHomeLaunch() {
        context.dataStore.edit { prefs ->
            val current = prefs[launchCountKey] ?: 0
            prefs[launchCountKey] = current + 1
        }
    }

    companion object {
        const val MAX_FAVORITES = 6
        private const val SEPARATOR = "|"

        /** The number of home-surface displays after which the intro callout hides. */
        const val INTRO_CALLOUT_LAUNCHES = 3
    }
}
