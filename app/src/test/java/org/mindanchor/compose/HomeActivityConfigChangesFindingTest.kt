package org.mindanchor.compose

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.25.9 BUG-014: HomeActivity must declare
 * `fontScale|locale|uiMode|density` in `configChanges`
 * so the activity is not recreated on every font size,
 * locale, dark-mode or density change.
 *
 * v0.25.5 added four home cards (OneThingCard,
 * OpenLoopCard, QuickNotesCard, BedtimeListCard) that
 * hold form state in `remember`. A config-change
 * recreation reset all of that state silently. The user
 * typed a one-thing, then changed the font size, then
 * came back to find the input cleared.
 *
 * `CheckInActivity` already declares the full set (the
 * project's correct default). HomeActivity was the
 * inconsistency.
 */
class HomeActivityConfigChangesFindingTest {

    @Test
    fun `HomeActivity configChanges covers fontScale, locale, uiMode, density`() {
        val manifest = readManifest()
        assertNotNull(manifest)
        // Find the HomeActivity <activity> element
        val homeActivity = Regex(
            "<activity\\s+[^>]*android:name=\"\\.HomeActivity\"[^>]*>",
            RegexOption.DOT_MATCHES_ALL,
        ).find(manifest!!)?.value
        assertNotNull(
            "AndroidManifest.xml must declare <activity android:name=\".HomeActivity\" ...>",
            homeActivity,
        )
        // Extract the configChanges attribute
        val configChanges = Regex(
            "android:configChanges=\"([^\"]+)\"",
        ).find(homeActivity!!)?.groupValues?.get(1)
        assertNotNull(
            "HomeActivity must declare android:configChanges",
            configChanges,
        )
        val tokens = configChanges!!.split("|")
        val required = listOf("fontScale", "locale", "uiMode", "density")
        val missing = required.filter { it !in tokens }
        assertTrue(
            "HomeActivity configChanges must include fontScale, locale, uiMode, density " +
                "so form state in v0.25.5 home cards survives config changes. tokens=" +
                "$tokens missing=$missing.",
            missing.isEmpty(),
        )
    }

    private fun readManifest(): String? = runCatching {
        val candidates = listOf(
            "app/src/main/AndroidManifest.xml",
            "../app/src/main/AndroidManifest.xml",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()
}
