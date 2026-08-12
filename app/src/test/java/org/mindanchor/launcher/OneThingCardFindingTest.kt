package org.mindanchor.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.25.5 WP-F: the "today's one thing" micro-action card.
 *
 * Martell 2013 review of goal-setting: a single, narrow, today's-
 * action text outperforms a list of goals on follow-through. The
 * affordance is one line, one tap, and silent when nothing is
 * set. A regression that grew it into a list (the obvious
 * "improvement" the moment the affordance is shipped) would
 * invert the finding.
 *
 * The five tests below pin the data layer + the file shape of
 * the composable. The data layer is the contract; the composable
 * is the user-facing payoff.
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
    fun `OneThingCard is silent when the text is null`() {
        // The file-shape pin: the composable returns early (no
        // rendering) when the text is null. A regression that
        // rendered the label with an empty value would clutter
        // the home corner with a permanent affordance the user
        // never asked for.
        val source = readSource("HomeScreen.kt")
        assertNotNull("HomeScreen.kt must be readable for the file-shape pin", source)
        assertTrue(
            "OneThingCard is a private composable on HomeScreen.kt",
            source!!.contains("private fun OneThingCard("),
        )
        assertTrue(
            "OneThingCard returns early when text is null",
            source.contains("if (text == null) {"),
        )
    }

    @Test
    fun `OneThingCard renders a 'Done with it' button when text is set`() {
        // The "Done with it" button is the affordance that lets
        // the user mark the day's one thing as done. A regression
        // that made the card read-only (no clear button) would
        // trap the user with whatever they typed yesterday,
        // which is the opposite of "today's one thing".
        val source = readSource("HomeScreen.kt")
        assertNotNull(source)
        assertTrue(
            "OneThingCard has a 'Done with it' branch with onClick = onClear",
            source!!.contains("Text(stringResource(R.string.one_thing_done)") &&
                source.contains("onClick = onClear"),
        )
    }

    @Test
    fun `LauncherViewModel exposes oneThing as a StateFlow and has setOneThing`() {
        // The wire-through from the home card to the store goes
        // through the ViewModel. A regression that put the
        // oneThing on the home screen as a direct DataStore
        // collectAsState would couple the screen to the store
        // shape, and a future refactor would have to touch the
        // home composable.
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

    @Test
    fun `HomeScreen passes oneThing through to the OneThingCard`() {
        // The home screen wires the ViewModel's oneThing into
        // the OneThingCard. A regression that left either
        // callback unwired would produce a card that does
        // nothing on tap, which is the silent-failure mode the
        // senior-tester audit has flagged before.
        val source = readSource("HomeScreen.kt")
        assertNotNull(source)
        assertTrue(
            "HomeScreen passes oneThing + callbacks into OneThingCard",
            source!!.contains("oneThing = oneThing,") &&
                source.contains("onSet = onOneThingSet,") &&
                source.contains("onClear = onOneThingClear,"),
        )
        // The ViewModel callback is wired to the actual store write.
        assertTrue(
            "HomeScreen ties the OneThingCard callbacks to viewModel.setOneThing",
            source.contains("onOneThingSet = viewModel::setOneThing") &&
                source.contains("onOneThingClear = { viewModel.setOneThing(null) }"),
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
