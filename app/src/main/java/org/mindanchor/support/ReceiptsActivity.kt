@file:Suppress("MagicNumber")
package org.mindanchor.support

import androidx.compose.runtime.Composable

/**
 * v0.38.0: DBT PLEASE mastery log (Linehan 1993 ch. 9).
 * One-line-per-day "what I did, however small." Date-stamped,
 * no streak, no score, list view.
 *
 * The screen is the new "Receipts" surface in the support
 * hub, listed in the reflective section after the diary
 * card (a "what I noticed" practice) and before the
 * interpersonal skills (a "what I did with another person"
 * practice). Receipts are a "what I did" practice, and they
 * sit between the two.
 */
class ReceiptsActivity : SupportSurfaceActivity() {
    @Composable
    override fun Surface(onDone: () -> Unit) {
        ReceiptsScreen(onDone = onDone)
    }
}
