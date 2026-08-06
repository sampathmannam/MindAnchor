package org.mindanchor.launcher

import org.mindanchor.data.InstalledApp

/** An app ready for display: renames applied, hidden state resolved. */
data class DisplayApp(
    val component: String,
    val label: String,
    val isFavorite: Boolean,
    val isHidden: Boolean,
)

/**
 * Pure presentation logic for the launcher, kept free of Android types so it
 * is trivially unit-testable.
 */
object AppFiltering {

    fun toDisplayApps(
        apps: List<InstalledApp>,
        favorites: List<String>,
        hidden: Set<String>,
        renames: Map<String, String>,
    ): List<DisplayApp> =
        apps.map { app ->
            DisplayApp(
                component = app.component,
                label = renames[app.component] ?: app.defaultLabel,
                isFavorite = app.component in favorites,
                isHidden = app.component in hidden,
            )
        }.sortedBy { it.label.lowercase() }

    /** Drawer contents: visible apps, alphabetical. */
    fun drawer(apps: List<DisplayApp>): List<DisplayApp> =
        apps.filter { !it.isHidden }

    /** Ordered favorites for the home surface (order comes from the prefs list). */
    fun favorites(apps: List<DisplayApp>, favoriteOrder: List<String>): List<DisplayApp> {
        val byComponent = apps.associateBy { it.component }
        return favoriteOrder.mapNotNull { byComponent[it] }.filter { !it.isHidden }
    }

    /**
     * Search: prefix matches first, then substring matches, both
     * case-insensitive; hidden apps stay findable only when the query
     * matches them exactly (so hiding reduces cues without deleting access).
     */
    fun search(apps: List<DisplayApp>, query: String): List<DisplayApp> {
        if (query.isBlank()) return drawer(apps)
        val q = query.trim().lowercase()
        val visible = apps.filter { !it.isHidden }
        val prefix = visible.filter { it.label.lowercase().startsWith(q) }
        val substring = visible.filter {
            !it.label.lowercase().startsWith(q) && it.label.lowercase().contains(q)
        }
        val hiddenExact = apps.filter { it.isHidden && it.label.lowercase() == q }
        return prefix + substring + hiddenExact
    }
}
