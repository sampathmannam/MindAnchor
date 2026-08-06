package org.mindanchor.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.data.InstalledApp

class AppFilteringTest {

    private val apps = listOf(
        InstalledApp("com.example.mail/.Main", "Mail"),
        InstalledApp("com.example.maps/.Main", "Maps"),
        InstalledApp("com.example.feed/.Main", "Feedly"),
        InstalledApp("com.example.cam/.Main", "Camera"),
        InstalledApp("com.example.cinema/.Main", "Cinema"),
    )

    @Test
    fun `renames replace labels and list stays alphabetical`() {
        val display = AppFiltering.toDisplayApps(
            apps,
            favorites = emptyList(),
            hidden = emptySet(),
            renames = mapOf("com.example.feed/.Main" to "Za Feed"),
        )
        assertEquals(
            listOf("Camera", "Cinema", "Mail", "Maps", "Za Feed"),
            display.map { it.label },
        )
    }

    @Test
    fun `hidden apps are excluded from the drawer`() {
        val display = AppFiltering.toDisplayApps(
            apps,
            favorites = emptyList(),
            hidden = setOf("com.example.feed/.Main"),
            renames = emptyMap(),
        )
        val drawer = AppFiltering.drawer(display)
        assertTrue(drawer.none { it.component == "com.example.feed/.Main" })
        assertEquals(4, drawer.size)
    }

    @Test
    fun `favorites keep their stored order`() {
        val order = listOf("com.example.cam/.Main", "com.example.mail/.Main")
        val display = AppFiltering.toDisplayApps(apps, order, emptySet(), emptyMap())
        val favorites = AppFiltering.favorites(display, order)
        assertEquals(listOf("Camera", "Mail"), favorites.map { it.label })
    }

    @Test
    fun `search ranks prefix matches before substring matches`() {
        val display = AppFiltering.toDisplayApps(apps, emptyList(), emptySet(), emptyMap())
        val results = AppFiltering.search(display, "ma")
        assertEquals(listOf("Mail", "Maps", "Cinema"), results.map { it.label })
    }

    @Test
    fun `hidden apps are findable only by exact name`() {
        val display = AppFiltering.toDisplayApps(
            apps,
            favorites = emptyList(),
            hidden = setOf("com.example.feed/.Main"),
            renames = emptyMap(),
        )
        assertTrue(AppFiltering.search(display, "feed").none { it.label == "Feedly" })
        assertEquals(listOf("Feedly"), AppFiltering.search(display, "feedly").map { it.label })
    }

    @Test
    fun `blank query returns full drawer`() {
        val display = AppFiltering.toDisplayApps(apps, emptyList(), emptySet(), emptyMap())
        assertEquals(5, AppFiltering.search(display, " ").size)
    }
}
