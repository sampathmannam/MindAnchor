package org.mindanchor.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.25.5 WP-F: the "today's one thing" data model.
 *
 * Martell 2013 review of goal-setting: a single, narrow, today's-
 * action text outperforms a list of goals on follow-through.
 *
 * v0.28.0 (BPD-strict cut): the OneThingCard Composable was
 * removed from the home surface (the first question the home
 * asks is "how is it right now?", not "what's the one thing
 * today?"). The data model — the prefs field, the StateFlow,
 * the setOneThing method — is preserved for the export
 * payload and any future re-introduction.
 *
 * The two tests below pin the data model:
 *  1. LauncherPrefs.MAX_ONE_THING_LENGTH caps the stored text.
 *  2. LauncherViewModel exposes oneThing as a StateFlow and has
 *     setOneThing.
 *
 * The original three Composable-shape tests (silent-when-null,
 * Done-with-it button, HomeScreen wiring) were removed in
 * v0.28.0 because the Composable no longer exists. The data
 * model is the contract that survives the cut.
 */
class OneThingCardFindingTest {

    @Test
    fun `LauncherPrefs MAX_ONE_THING_LENGTH caps the stored text at 140 chars`() {
        // 140 is the same cap as the OpenLoop card: long enough
        // for a sentence, short enough not to become a project.
        // A regression that removed the cap would let the
        // launcher render an essay in a card designed for one
        // line.
        assertEquals(140, org.mindanchor.data.LauncherPrefs.MAX_ONE_THING_LENGTH)
    }

    @Test
    fun `LauncherViewModel exposes oneThing as a StateFlow and has setOneThing`() {
        // The data layer is the contract that survives the
        // v0.28.0 Composable cut. A regression that put the
        // oneThing on a different surface as a direct DataStore
        // collectAsState would couple that surface to the store
        // shape, and a future refactor would have to touch the
        // surface composable.
        val source = readSource("LauncherViewModel.kt")
        assertNotNull(source)
        assertTrue(
            "LauncherViewModel exposes oneThing: StateFlow",
            source!!.contains("val oneThing: StateFlow<String?>") &&
                source.contains("prefs.oneThing") &&
                source.contains(".stateIn(viewModelScope,"),
        )
        assertTrue(
            "LauncherViewModel has setOneThing that writes to the store",
            source.contains("fun setOneThing(text: String?)") &&
                source.contains("prefs.setOneThing(text)"),
        )
    }

    private fun readSource(filename: String): String? = runCatching {
        val candidates = listOf(
            "app/src/main/java/org/mindanchor/launcher/$filename",
            "../app/src/main/java/org/mindanchor/launcher/$filename",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()
}
