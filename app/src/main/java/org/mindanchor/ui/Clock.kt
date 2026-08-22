package org.mindanchor.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * The current time, refreshed exactly on each minute boundary and again the
 * moment the launcher resumes.
 *
 * The naive version — a `while (true) { delay(30_000) }` loop — was wrong in
 * two ways a tester notices immediately: after the phone had been asleep the
 * clock could show a time up to half a minute stale on wake, and the minute
 * flipped at an arbitrary offset rather than when the real minute changed.
 */
@Composable
fun rememberMinuteTick(): LocalTime {
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now = LocalTime.now()
                // Sleep only until the wall clock's next minute.
                delay(60_000L - System.currentTimeMillis() % 60_000L)
            }
        }
    }
    return now
}

/**
 * v0.62.6 (F3 from top-50 audit, Visibility of
 * System Status): a per-second tick used by the
 * home subtitle to show "Saved Xs ago" — the
 * v0.53.0 [rememberMinuteTick] only updates every
 * minute, which is too coarse for the
 * just-saved window (60s). The "Saved 12s ago"
 * copy must update every second so the
 * "data is safe" signal stays accurate.
 *
 * Used alongside [rememberMinuteTick] — the
 * minute tick powers the clock display (which
 * only needs minute precision); the second tick
 * powers the subtitle (which needs second
 * precision in the just-saved window).
 *
 * Sleeps 1s between updates. The cost is one
 * recomposition per second for any Composable
 * that reads it; the home subtitle is the only
 * consumer for now.
 */
@Composable
fun rememberSecondTick(): Long {
    val lifecycleOwner = LocalLifecycleOwner.current
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                nowMs = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }
    return nowMs
}

/** Honours the device's 12/24-hour setting rather than assuming 24. */
@Composable
fun rememberClockFormat(): String =
    if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm"
