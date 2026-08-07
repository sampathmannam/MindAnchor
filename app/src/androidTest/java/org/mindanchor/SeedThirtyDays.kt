package org.mindanchor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.CrisisContact
import org.mindanchor.data.db.HeldNotification
import org.mindanchor.data.db.PulseResult
import org.mindanchor.data.db.SafetyPlan
import org.mindanchor.model.Moment
import org.mindanchor.model.MomentStore
import org.mindanchor.onboarding.Goal
import org.mindanchor.onboarding.OnboardingPrefs
import org.mindanchor.report.Signal
import org.mindanchor.usage.InferredStore
import org.mindanchor.vitals.MeasuredStore

/**
 * Fills the app with thirty days of one plausible person's history, so
 * that screens which say "still building a picture" on a fresh install can
 * be looked at with something behind them.
 *
 * Not a test — a fixture with a `@Test` annotation so the instrumentation
 * runner will execute it against the app under test. Everything is written
 * through the same store classes the app itself writes through, so nothing
 * here can encode a format the production code would disagree with.
 *
 * The history is deterministic (fixed seed) and carries one deliberate
 * relationship: **each day's reported valence follows the previous
 * night's sleep**. That is the lagged shape `PatternFinder` looks for, so
 * a run over this data exercises the finding path rather than only the
 * "not enough yet" path.
 */
@Fixture
@RunWith(AndroidJUnit4::class)
class SeedThirtyDays {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val rng = Random(20260808)

    /** Oldest first, ending today. */
    private val days: List<LocalDate> =
        (DAYS - 1 downTo 0).map { LocalDate.now(ZoneId.systemDefault()).minusDays(it.toLong()) }

    @Test
    fun seed() = runBlocking {
        val measured = MeasuredStore(context)
        val inferred = InferredStore(context)
        val moments = MomentStore(context)
        val db = AnchorDatabase.get(context)

        // Sleep first: it drives HRV, resting heart rate and the next
        // day's mood, so every other series is derived from it rather
        // than rolled independently.
        val sleepMinutes = days.mapIndexed { i, day -> i to sleepFor(i, day) }.toMap()

        days.forEachIndexed { i, day ->
            val sleep = sleepMinutes.getValue(i)

            measured.record(day, Signal.SLEEP_MINUTES.name, sleep.toDouble())
            measured.record(day, Signal.SLEEP_ONSET.name, onsetAfterSixPm(i, day).toDouble())
            // Short nights suppress HRV and lift resting heart rate; both
            // are ordinary next-morning physiology, and both are what the
            // report's baseline is meant to notice drifting.
            measured.record(day, Signal.HRV.name, round1(30.0 + (sleep - 300) / 11.0 + noise(3.0)))
            measured.record(
                day,
                Signal.RESTING_HEART_RATE.name,
                round1(62.0 - (sleep - 420) / 38.0 + noise(1.8)),
            )
            measured.record(day, Signal.STEPS.name, stepsFor(i, day).toDouble())

            inferred.record(day, Signal.FIRST_UNLOCK.name, firstUnlockFor(day).toDouble())
            // A rough night tends to be followed by a longer day on the
            // phone, which is the direction the attention literature this
            // app cites would predict.
            inferred.record(
                day,
                Signal.SCREEN_TIME.name,
                (195 + (420 - sleep) / 4 + noise(22.0)).coerceIn(70.0, 420.0),
            )

            // Four check-ins a day at scattered times, following the
            // previous night rather than the current one — the lag is the
            // whole point of the pairing PatternFinder builds.
            val previousSleep = sleepMinutes[i - 1]
            repeat(CHECK_INS_PER_DAY) { n ->
                moments.append(
                    Moment(
                        valence = valenceFor(previousSleep),
                        arousal = arousalFor(previousSleep),
                        atMinuteOfDay = checkInMinute(n),
                        day = day.toString(),
                    ),
                )
            }
        }
        moments.setEnabled(true)

        // A WHO-5 every fortnight, drifting upward the way somebody who
        // started sleeping a little better would.
        listOf(1 to 48, 15 to 56, 29 to 64).forEach { (dayIndex, score) ->
            db.pulses().insert(
                PulseResult(
                    takenAt = days[dayIndex].atTime(20, 30)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    score = score,
                ),
            )
        }

        seedNotifications(db)
        seedSafety(db)

        // Past the first-run screens, with every goal named, so the
        // settings sections all carry their "you came here for this"
        // marking and no screen is unreachable behind onboarding.
        OnboardingPrefs(context).complete(Goal.entries.toSet())
    }

    /** A batch waiting to be read, plus older ones already released. */
    private suspend fun seedNotifications(db: AnchorDatabase) {
        val today = days.last()
        val pending = listOf(
            Triple("com.example.shop", "Marketplace", "Your order has shipped"),
            Triple("com.example.shop", "Marketplace", "Rate your recent purchase"),
            Triple("com.example.news", "The Daily", "Five things to know this morning"),
            Triple("com.example.news", "The Daily", "Evening briefing"),
            Triple("com.example.social", "Fieldnote", "3 people replied to your post"),
            Triple("com.example.social", "Fieldnote", "Someone you know just joined"),
            Triple("com.example.bank", "Ledger", "Statement ready"),
            Triple("com.example.games", "Tilecraft", "Your lives have refilled"),
        )
        pending.forEachIndexed { i, (pkg, label, title) ->
            db.heldNotifications().insert(
                HeldNotification(
                    packageName = pkg,
                    appLabel = label,
                    title = title,
                    text = "Held for the next batch.",
                    postedAt = today.atTime(7, 5).plusMinutes(i * 37L)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                ),
            )
        }
        // Yesterday's, already delivered — the journal keeps these so the
        // batcher can prove nothing was ever dropped.
        val yesterday = days[days.lastIndex - 1]
        repeat(6) { i ->
            val postedAt = yesterday.atTime(9, 0).plusMinutes(i * 71L)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            db.heldNotifications().insert(
                HeldNotification(
                    packageName = "com.example.news",
                    appLabel = "The Daily",
                    title = "Yesterday's headline ${i + 1}",
                    text = "Released in the 18:00 batch.",
                    postedAt = postedAt,
                    releasedAt = postedAt + 3_600_000L,
                ),
            )
        }
    }

    private suspend fun seedSafety(db: AnchorDatabase) {
        db.safety().savePlan(
            SafetyPlan(
                warningSigns = "Skipping meals. Reading the same message over and over. " +
                    "Staying up past 2am for no reason.",
                copingSteps = "Put the phone in the kitchen. Ten slow breaths. " +
                    "Walk to the end of the road and back.",
                distractions = "Cook something that takes both hands. " +
                    "The long playlist. Sit in the park by the library.",
                reasonsForLiving = "Priya. The garden in spring. " +
                    "Finishing the thing I started in March.",
                environmentSafety = "Tablets stay at my sister's. " +
                    "Nothing sharp in the bedroom.",
                updatedAt = days[20].atTime(21, 15)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            ),
        )
        db.safety().addContact(CrisisContact(name = "Priya", phone = "+15550142", isProfessional = false))
        db.safety().addContact(CrisisContact(name = "Dr Menon", phone = "+15550178", isProfessional = true))
    }

    // ---- the shape of one person's month -------------------------------

    private fun isWeekend(day: LocalDate) = day.dayOfWeek.value >= 6

    /** Weekends run long, and four nights in the month go badly. */
    private fun sleepFor(index: Int, day: LocalDate): Int {
        if (index in BAD_NIGHTS) return (300 + noise(18.0)).toInt()
        val base = if (isWeekend(day)) 482.0 else 421.0
        return (base + noise(26.0)).toInt()
    }

    private fun onsetAfterSixPm(index: Int, day: LocalDate): Int {
        // Bedtime as a minute of day, then reframed exactly the way
        // Deviation.minutesAfterSixPm does it, so a night crossing
        // midnight increases rather than wrapping to zero.
        val bedtime = when {
            index in BAD_NIGHTS -> 96 // 01:36
            isWeekend(day) -> 40 // 00:40
            else -> 1400 // 23:20
        } + noise(20.0).toInt()
        return ((bedtime - 18 * 60) + 1440) % 1440
    }

    private fun stepsFor(index: Int, day: LocalDate): Int = when {
        index in QUIET_DAYS -> (2400 + noise(400.0)).toInt()
        isWeekend(day) -> (9500 + noise(1500.0)).toInt()
        else -> (7200 + noise(1100.0)).toInt()
    }

    private fun firstUnlockFor(day: LocalDate): Int =
        ((if (isWeekend(day)) 550.0 else 440.0) + noise(28.0)).toInt()

    /**
     * The planted relationship: how last night went is what today's
     * check-ins report. Null on the first day, which has no night before
     * it on file — a genuine gap, left as one rather than filled in.
     */
    private fun valenceFor(previousSleep: Int?): Int {
        val sleep = previousSleep ?: return 3
        val base = when {
            sleep < 340 -> 2.0
            sleep < 400 -> 3.0
            sleep < 460 -> 4.0
            else -> 4.6
        }
        return (base + noise(0.45)).toInt().coerceIn(Moment.MIN, Moment.MAX)
    }

    /** Short nights read as wired rather than calm, not as sluggish. */
    private fun arousalFor(previousSleep: Int?): Int {
        val sleep = previousSleep ?: return 3
        val base = if (sleep < 340) 4.1 else 2.9
        return (base + noise(0.5)).toInt().coerceIn(Moment.MIN, Moment.MAX)
    }

    /** Scattered inside blocks rather than on the hour — the EMA convention. */
    private fun checkInMinute(n: Int): Int {
        val block = listOf(9 * 60, 13 * 60, 17 * 60, 21 * 60)[n]
        return (block + noise(38.0)).toInt().coerceIn(0, 1439)
    }

    private fun noise(spread: Double) = (rng.nextDouble() - 0.5) * 2 * spread

    private fun round1(v: Double) = Math.round(v * 10.0) / 10.0

    private companion object {
        const val DAYS = 30
        const val CHECK_INS_PER_DAY = 4

        /** Indexes into [days] — a rough patch mid-month and one late one. */
        val BAD_NIGHTS = setOf(6, 13, 14, 25)
        val QUIET_DAYS = setOf(7, 14, 26)
    }
}
