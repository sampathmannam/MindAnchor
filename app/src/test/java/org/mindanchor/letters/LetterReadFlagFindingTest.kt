package org.mindanchor.letters

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Finding test for v0.25.3-WP-C (per-letter `read` flag).
 *
 * v0.25.2's `unreadLetterCount` was a stand-in: it counted letters
 * dated after the user's install date. A real "unread" badge
 * should decrement as the user reads. WP-C ships the per-letter
 * `read: Boolean` flag, sourced from a separate `Set<LocalDate>`
 * in DataStore so the wire format doesn't change.
 *
 * What this test pins:
 *  1. `LetterStore` has a `readDates: Flow<Set<LocalDate>>` field.
 *  2. `LetterStore` has a `suspend fun setRead(date, read)` method.
 *  3. `SettingsViewModel.unreadLetterCount` derives from both
 *     `letterStore.letters` and `letterStore.readDates` via
 *     `combine`, not from the install-date stand-in.
 *  4. `HomeScreen`'s `LetterScreen.onSelect` callback also calls
 *     `letterStore.setRead(date, true)` so a row tap decrements
 *     the unread count.
 *  5. `LetterLedger` wire format is unchanged (separate key, no
 *     column added) — a v0.25.2 install can read its data after
 *     WP-C ships.
 */
class LetterReadFlagFindingTest {

    private fun fileAt(relative: String): File {
        val candidates = listOf(relative, "../$relative", "../../$relative")
        return candidates.map(::File).firstOrNull { it.isFile }
            ?: error("$relative not found from working directory ${File(".").absolutePath}.")
    }

    private val store: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/letters/LetterStore.kt",
        ).readText()

    // v0.25.7+ moved LetterLedger (and the encode logic) to its
    // own file. The wire-format check below follows the move so
    // the same intent (no read column) is still pinned.
    private val ledger: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/letters/LetterLedger.kt",
        ).readText()

    private val viewModel: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/settings/SettingsViewModel.kt",
        ).readText()

    private val home: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/launcher/HomeScreen.kt",
        ).readText()

    @Test
    fun `LetterStore exposes readDates as a Flow of Set of LocalDate`() {
        // The wire type must be Set<LocalDate> (not Set<String>) so
        // consumers can compare against Letter.date without
        // re-parsing on every emission.
        assertTrue(
            "LetterStore must expose `val readDates: Flow<Set<LocalDate>>`. " +
                "The unread-letter count depends on a reactive read of " +
                "this set, and the date type must be LocalDate for " +
                "`letters.count { it.date !in readDates }` to compile.",
            store.contains("val readDates: Flow<Set<LocalDate>>"),
        )
    }

    @Test
    fun `LetterStore has a suspend setRead date read method`() {
        // The writer is the only public surface that mutates the
        // read set; the read set is otherwise consumed reactively
        // via readDates. Pin both the keyword (suspend) and the
        // parameter shape (date: LocalDate, read: Boolean) so a
        // future refactor that breaks the onSelect call site
        // fails at compile time, not at runtime.
        assertTrue(
            "LetterStore must have a `suspend fun setRead(date: LocalDate, read: Boolean)` " +
                "method. The onSelect callback in HomeScreen calls " +
                "`letterStore.setRead(date, true)` to decrement the " +
                "unread count; a missing or differently-shaped method " +
                "breaks the v0.25.3-WP-C contract.",
            store.contains("suspend fun setRead(date: LocalDate, read: Boolean)"),
        )
    }

    @Test
    fun `unreadLetterCount combines letters and readDates (not the install-date stand-in)`() {
        // The v0.25.2 stand-in was `list.count { it.date >= installDate() }`.
        // WP-C replaces it with `letters.count { it.date !in readDates }`
        // via a `combine` flow so the count re-emits when either side
        // changes. The install-date branch must be GONE.
        val unreadBlock = Regex(
            """val\s+unreadLetterCount\s*:\s*StateFlow<Int>\s*=[\s\S]{0,800}?\.stateIn\(""",
        ).find(viewModel)?.value ?: error(
            "Could not locate unreadLetterCount declaration in SettingsViewModel.kt",
        )
        assertTrue(
            "unreadLetterCount must combine letterStore.letters and " +
                "letterStore.readDates (not use the installDate() stand-in " +
                "from v0.25.2). Block: $unreadBlock",
            unreadBlock.contains("letterStore.readDates") &&
                unreadBlock.contains("letterStore.letters") &&
                unreadBlock.contains("combine("),
        )
        assertTrue(
            "unreadLetterCount must not use the installDate() stand-in " +
                "from v0.25.2 (that branch was replaced by the real " +
                "per-letter read flag in WP-C). Block: $unreadBlock",
            !unreadBlock.contains("installDate()") &&
                !unreadBlock.contains("it.date >= installDate"),
        )
    }

    @Test
    fun `HomeScreen onSelect marks the tapped letter as read`() {
        // When the user taps a row, the callback must do two things:
        // (a) set letterSelectedDate so the reader opens for that
        // date, and (b) call letterStore.setRead(date, true) so the
        // unread count decrements. The order doesn't matter; both
        // statements must be present.
        val onSelectIdx = home.indexOf("onSelect = { date ->")
        assertTrue(
            "LetterScreen.onSelect callback must be present in " +
                "HomeScreen.kt's Letter surface branch. " +
                "onSelectIdx=$onSelectIdx",
            onSelectIdx >= 0,
        )
        // The setRead call must be inside the same onSelect block.
        // Slice 600 chars to cover the lambda body.
        val slice = home.substring(onSelectIdx, minOf(onSelectIdx + 600, home.length))
        assertTrue(
            "HomeScreen.LetterSurface.onSelect must also call " +
                "letterStore.setRead(date, true) so a row tap decrements " +
                "the unread count. Slice: $slice",
            slice.contains("letterStore.setRead(date, true)"),
        )
    }

    @Test
    fun `LetterLedger wire format is unchanged (no read column added)`() {
        // v0.25.2 letters must still decode after WP-C ships. The
        // wire format is `date<TAB>body<NEWLINE>` per letter. Adding
        // a read column would invalidate every existing user's
        // inbox. Pin that the encode body still uses the
        // `date` and `body` fields (in either order, with
        // whatever transform the body needs) and there is no read
        // column sneaking in.
        //
        // v0.25.7+ moved LetterLedger out of LetterStore.kt into
        // its own file, so the check follows the move and reads
        // LetterLedger.kt instead. The new encode lambda is named
        // `letter` (not `it`), so the check matches `letter.date`
        // / `letter.body` to cover either naming the future code
        // may use.
        val usesDateAndBody = (ledger.contains("it.date") || ledger.contains("letter.date")) &&
            (ledger.contains("it.body") || ledger.contains("letter.body"))
        val hasReadColumn = ledger.contains("read\tbody") ||
            ledger.contains("body\tread")
        assertTrue(
            "LetterLedger.encode must still produce the v0.25.2 " +
                "wire format using the date and body fields (no read " +
                "column added). The read flag is stored as a " +
                "separate `letters_read_dates` set in the same " +
                "DataStore. usesDateAndBody=$usesDateAndBody, " +
                "hasReadColumn=$hasReadColumn",
            usesDateAndBody && !hasReadColumn,
        )
    }
}
