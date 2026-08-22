@file:Suppress("FunctionNaming", "MagicNumber")
package org.mindanchor.support

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import org.mindanchor.ui.CalmBackground

/**
 * v0.29.0: shared scaffold for the nine Support group activities
 * (Interpersonal, RadicalAcceptance, SelfCompassion, OppositeAction,
 * DistressThermometer, Accepts, LetterToPart, DiaryCard, Values).
 *
 * Before v0.29.0 each activity repeated the same 8-line boilerplate:
 *
 *   class XxxActivity : ComponentActivity() {
 *       override fun onCreate(savedInstanceState: Bundle?) {
 *           super.onCreate(savedInstanceState)
 *           setContent {
 *               CalmBackground { _ ->
 *                   XxxScreen(onDone = { finish() })
 *               }
 *           }
 *       }
 *   }
 *
 * The duplication was harmless (9 × ~10 lines = ~90 lines) but caused
 * three real problems:
 *   1. Any change to the wrapping (e.g. add IME padding, add
 *      windowSoftInputMode, add a back-handler, add windowSoftInputMode,
 *      add a system-bar insets policy) had to be applied in 9
 *      places. A miss would not show up in unit tests.
 *   2. Each v0.27.0 and v0.28.0 release commit repeated the same
 *      scaffold, bloating the diffs.
 *   3. A new support activity (e.g. the v0.29.0 ACT values surface)
 *      would have to re-derive the scaffold from a copy-paste of
 *      an existing activity, which is exactly the failure mode this
 *      refactor prevents.
 *
 * v0.29.0 folds the scaffold into this abstract class. Each
 * activity now declares only the surface, not the lifecycle.
 *
 * ## Subclassing
 *
 * ```kotlin
 * class OppositeActionActivity : SupportSurfaceActivity() {
 *     @Composable
 *     override fun Surface(onDone: () -> Unit) {
 *         OppositeActionScreen(onDone = onDone)
 *     }
 * }
 * ```
 *
 * Activities that need an extra callback (e.g. DistressThermometer
 * opens SupportActivity from its 86+ band) define it inside
 * `Surface()`:
 *
 * ```kotlin
 * class DistressThermometerActivity : SupportSurfaceActivity() {
 *     @Composable
 *     override fun Surface(onDone: () -> Unit) {
 *         DistressThermometerScreen(
 *             onDone = onDone,
 *             onOpenSupport = {
 *                 startActivity(
 *                     Intent(this, SupportActivity::class.java)
 *                         .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
 *                 )
 *                 finish()
 *             },
 *         )
 *     }
 * }
 * ```
 *
 * ## BPD-safety
 *
 * The `CalmBackground` wrapping applies the project's standard
 * sky/weather theme. It is not a permission-gate, not a crisis-
 * detection screen, not a log surface. The base class is
 * *infrastructure only*; each activity's own screen is responsible
 * for its own BPD-safe copy.
 */
abstract class SupportSurfaceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalmBackground { _ ->
                Surface(onDone = { finish() })
            }
        }
    }

    /**
     * Render the activity's screen. Called once per composition
     * inside the standard CalmBackground wrapper. The `onDone`
     * lambda dismisses the activity when invoked (Done button,
     * back gesture via the back button, or any other dismiss
     * affordance the screen provides).
     */
    @Composable
    abstract fun Surface(onDone: () -> Unit)
}
