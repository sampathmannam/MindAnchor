package org.mindanchor.data

import android.content.Context
import androidx.datastore.preferences.core.edit
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

    companion object {
        const val MAX_FAVORITES = 6
        private const val SEPARATOR = "|"
    }
}
