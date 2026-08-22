@file:Suppress("MagicNumber")
package org.mindanchor.support

import androidx.compose.runtime.Composable

/**
 * v0.28.0: a letter to the part of you that is loudest right now
 * (IFS, Schwartz 1995). The activity hosts three sub-screens
 * (pick a part, write to it, optionally write from it back to
 * you) via a state var. The letter is not saved; it is an act,
 * not a record.
 *
 * v0.29.0: the activity's lifecycle / theme scaffold is in
 * [SupportSurfaceActivity]. This class only declares what the
 * surface renders, not how the activity is wired into the
 * Android lifecycle.
 *
 * ## What this is and what it is not
 *
 * The activity is a quiet, focused space for the user to *speak*
 * to the part of them that is loud. It is based on the IFS
 * "letter to a part" exercise (Schwartz 1995; Earley 2009),
 * which is the standard IFS in-session writing practice. The
 * writing is local; nothing leaves the device. The Done button
 * dismisses without saving.
 *
 * ## BPD-safety
 *
 * No directive language. No "you should...". The pick list is
 * the same 5 named parts the IFS picker uses (Schwartz 1995):
 * angry / scared / wants to disappear / critic / protector.
 * The "from the part" view is an *invitation*, not a requirement
 * — the user can dismiss at any time.
 */
class LetterToPartActivity : SupportSurfaceActivity() {
    @Composable
    override fun Surface(onDone: () -> Unit) {
        LetterToPartScreen(onDone = onDone)
    }
}
