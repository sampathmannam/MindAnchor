package org.mindanchor.reader

/**
 * v0.25.2-B stub; replaced in Task 13.
 *
 * Minimal type so the v0.25.2 letter-inbox / reader screens can
 * reference `ReadingSize` in their public signatures. The real
 * value-class, the SCALE list (SMALL / MEDIUM / LARGE / XLARGE)
 * and the sp-per-step mapping are added by Task 13's
 * `ReaderPrefs` + size-thread wiring.
 *
 * Keep this stub *just* enough to compile the v0.25.2 letter
 * surface — Task 13 will replace it wholesale, so do not extend
 * it here.
 */
data class ReadingSize(val sp: Int) {
    companion object {
        // v0.25.2-B stub; replaced in Task 13. The default value for
        // the v0.25.2-B reader-mode text size — chosen to match the
        // existing body copy so Task 15's wire-through is invisible
        // until the user opts in to a different size.
        val MEDIUM = ReadingSize(sp = 18)
    }
}
