package org.mindanchor.reader

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v0.25.2-B stub; replaced in Task 13.
 *
 * Persists the user's chosen letter-reading text size across
 * launches. Task 9 wires the settings view model to the
 * read/write surface; Task 13 widens the value-class, the SCALE
 * list (SMALL / MEDIUM / LARGE / XLARGE) and the sp-per-step
 * mapping. This stub is deliberately *just* enough for Task 9
 * to compile and run: a single [ReadingSize] held in
 * [SharedPreferences], exposed as a [StateFlow] so the view
 * model can `collectAsState` it without a DataStore round-trip.
 *
 * The default is [ReadingSize.MEDIUM] — matching the v0.25.2-B
 * reader copy so the wire-through is invisible until the user
 * opts in to a different size.
 *
 * The underlying storage is a private-mode [SharedPreferences]
 * file (not the encrypted store): reading text size is not
 * sensitive, and the DataStore dependency would be a Task-13
 * concern that this stub does not want to import.
 */
class ReaderPrefs(private val context: Context) {

    private val store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _size = MutableStateFlow(read())

    /** The user's chosen letter-reading text size, in sp. */
    val size: StateFlow<ReadingSize> = _size.asStateFlow()

    /**
     * Persists [newSize] and publishes it to [size]. No
     * validation here — the [ReadingSize] value-class is the
     * guard, and the v0.25.2-B stub only has [ReadingSize.MEDIUM]
     * anyway. Task 13 will widen both sides.
     */
    suspend fun setSize(newSize: ReadingSize) {
        store.edit().putInt(KEY_SIZE, newSize.sp).apply()
        _size.value = newSize
    }

    private fun read(): ReadingSize {
        val sp = store.getInt(KEY_SIZE, ReadingSize.MEDIUM.sp)
        return ReadingSize(sp = sp)
    }

    private companion object {
        const val PREFS = "reader_prefs"
        const val KEY_SIZE = "size_sp"
    }
}
