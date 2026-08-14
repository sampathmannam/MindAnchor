@file:Suppress(
    "SwallowedException",
    "MaxLineLength",
    "LoopWithTooManyJumpStatements",
    "UnusedPrivateMember",
)

package org.mindanchor.launcher

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SOTA v2 bug-hunt BUG-017 (v0.25.16 fix).
 *
 * The pre-v0.25.16 HomeScreen held the `modelFits` value
 * as a Composable-level stub:
 *
 *   `val modelFits = remember { mutableStateOf(false) }`
 *
 * The value was *always* `false`, so the letter inbox's
 * "Generate now" affordance was permanently disabled. The
 * user could open Letters, see "Phi-4 isn't installed",
 * and never get the path forward.
 *
 * v0.25.16: `LauncherViewModel.modelFits: StateFlow<Boolean>`
 * reads `phi-4-mini-q4.gguf` from the app's internal storage
 * on first composition. The HomeScreen collects the flow
 * with `collectAsStateWithLifecycle` (BUG-004) and passes
 * the value through to `LetterScreen.modelFits`.
 *
 * The two tests below pin the surface:
 *
 * 1. `LauncherViewModel.kt` exposes a `StateFlow<Boolean>`
 *    named `modelFits` (positive pin for the new VM field).
 * 2. `HomeScreen.kt` no longer has the Composable-level
 *    `remember { mutableStateOf(false) }` stub (negative pin
 *    for the old shape). The new call site is
 *    `val modelFits by viewModel.modelFits.collectAsStateWithLifecycle()`.
 */
class ModelFitsWiringFindingTest {

    @Test
    fun `BUG-017 LauncherViewModel exposes modelFits as a StateFlow Boolean`() {
        val source = readSource("LauncherViewModel.kt", "launcher")
        assertNotNull("LauncherViewModel.kt must be readable", source)
        val src = source!!
        // v0.25.16: `val modelFits: StateFlow<Boolean> = _modelFits.asStateFlow()`.
        // The v0.25.16 fix is the existence of this field — a
        // regression that removes the field would re-collapse
        // the LetterScreen Generate-now button to the
        // always-disabled state.
        assertTrue(
            "LauncherViewModel must expose `val modelFits: StateFlow<Boolean>` " +
                "(v0.25.16 fix). The pre-fix shape had no such field and the " +
                "HomeScreen held a Composable-level `remember { mutableStateOf(false) }` " +
                "stub. source=\n$src",
            src.contains("val modelFits: StateFlow<Boolean>"),
        )
    }

    @Test
    fun `BUG-017 HomeScreen letter surface no longer holds modelFits in remember mutableStateOf`() {
        val source = readSource("HomeScreen.kt", "launcher")
        assertNotNull(source)
        val src = source!!
        // v0.25.16: the Composable-level stub
        // `val modelFits = remember { mutableStateOf(false) }`
        // is gone. The new shape is
        // `val modelFits by viewModel.modelFits.collectAsStateWithLifecycle()`.
        val fnIdx = src.indexOf("LauncherSurface.Letter ->")
        assertTrue(
            "HomeScreen letter surface dispatcher must be present",
            fnIdx >= 0,
        )
        val letterBlock = src.substring(fnIdx)
        assertTrue(
            "HomeScreen letter surface must NOT hold modelFits in `remember { mutableStateOf(false) }` " +
                "(v0.25.16 fix). The pre-fix shape was exactly that stub. source=\n$letterBlock",
            !letterBlock.contains("modelFits = remember { mutableStateOf(false) }") &&
                !letterBlock.contains("val modelFits = remember {"),
        )
        // The new shape uses collectAsStateWithLifecycle.
        assertTrue(
            "HomeScreen letter surface must collect `viewModel.modelFits` with " +
                "`collectAsStateWithLifecycle` (v0.25.16 fix). source=\n$letterBlock",
            letterBlock.contains("viewModel.modelFits.collectAsStateWithLifecycle()"),
        )
    }

    private fun readSource(filename: String, pkg: String): String? = runCatching {
        val candidates = buildList {
            if (pkg.isNotEmpty()) {
                add("app/src/main/java/org/mindanchor/$pkg/$filename")
                add("../app/src/main/java/org/mindanchor/$pkg/$filename")
            }
            add("app/src/main/java/org/mindanchor/$filename")
            add("../app/src/main/java/org/mindanchor/$filename")
        }
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()
}
