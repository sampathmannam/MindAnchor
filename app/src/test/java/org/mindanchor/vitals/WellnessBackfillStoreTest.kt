package org.mindanchor.vitals

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.vitals.coros.CorosDaily
import org.mindanchor.vitals.coros.CorosHrv
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The persisted half of the wearable backfill: the
 * [WellnessHistoryStore.backfill] write and the
 * [WellnessRepository.backfillFromWearable] orchestration that
 * feeds it from the COROS bridge shapes plus the measured-here
 * store.
 *
 * The pure rules are pinned in [WellnessBackfillTest] and the
 * end-to-end contract in the sim harness's
 * WearableBackfillSimulationTest; this class pins that the rules
 * survive the round trip through the actual DataStore files.
 *
 * Note the ledger DataStores are JVM singletons, so state is
 * shared across the test methods here — each test works on its
 * own disjoint days and asserts per-day, the same discipline as
 * [org.mindanchor.letters.JournalStoreTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class WellnessBackfillStoreTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun rhrOn(
        history: List<WellnessDayValue>,
        day: LocalDate,
    ): List<Double> = history.filter { it.day == day }.map { it.value }

    @Test
    fun `backfill persists bridge days and an existing day survives`() = runBlocking {
        val store = WellnessHistoryStore(context())
        val recorded = LocalDate.of(2026, 8, 2)
        val filled = LocalDate.of(2026, 8, 1)
        store.recordAll(recorded, mapOf(WellnessSignal.RESTING_HEART_RATE to 52.0))

        store.backfill(
            wearable = listOf(
                WellnessLedger.Entry(WellnessSignal.RESTING_HEART_RATE, recorded, 60.0),
                WellnessLedger.Entry(WellnessSignal.RESTING_HEART_RATE, filled, 57.0),
            ),
        )

        val history = store.historyFor(WellnessSignal.RESTING_HEART_RATE)
        assertEquals(listOf(52.0), rhrOn(history, recorded))
        assertEquals(listOf(57.0), rhrOn(history, filled))
    }

    @Test
    fun `a later live read still replaces a backfilled day — the retake rule`() = runBlocking {
        val store = WellnessHistoryStore(context())
        val today = LocalDate.of(2026, 8, 10)
        store.backfill(
            wearable = listOf(
                WellnessLedger.Entry(WellnessSignal.RESTING_HEART_RATE, today, 60.0),
            ),
        )

        store.recordAll(today, mapOf(WellnessSignal.RESTING_HEART_RATE to 55.0))

        val history = store.historyFor(WellnessSignal.RESTING_HEART_RATE)
        assertEquals(listOf(55.0), rhrOn(history, today))
    }

    @Test
    fun `repository backfill applies the measured-here veto from the MeasuredStore`() = runBlocking {
        val ctx = context()
        val night = LocalDate.of(2026, 8, 20)
        // A deliberate camera-PPG HRV on file for the night the
        // bridge also has an opinion about.
        MeasuredStore(ctx).record(night, org.mindanchor.report.Signal.HRV.name, 52.0)

        WellnessRepository(ctx).backfillFromWearable(
            hrv = listOf(CorosHrv(date = night.toString(), rmssd = 40.0)),
            daily = listOf(CorosDaily(date = night.toString(), rhr = 55.0)),
        )

        val store = WellnessHistoryStore(ctx)
        assertEquals(
            listOf(52.0),
            store.historyFor(WellnessSignal.HRV).filter { it.day == night }.map { it.value },
        )
        assertEquals(
            listOf(55.0),
            rhrOn(store.historyFor(WellnessSignal.RESTING_HEART_RATE), night),
        )
    }
}
