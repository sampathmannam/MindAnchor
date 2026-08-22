package org.mindanchor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.data.NotesPrefs
import org.mindanchor.model.Note
import org.mindanchor.model.NoteType
import org.mindanchor.note.ReminderScheduler

/**
 * v0.45.0: fills the Notes tab with 100 plausible notes so
 * the UI can be exercised against a populated store —
 * sort order, pinning, type chips, due time, reminder
 * time, and the "done" state on tasks.
 *
 * Not a test — a fixture with a `@Test` annotation so the
 * instrumentation runner will execute it against the app
 * under test. Like the other fixtures, it is invoked by
 * hand via
 * `adb shell am instrument -w -e class org.mindanchor.SeedNotes \
 *   org.mindanchor.test/androidx.test.runner.AndroidJUnitRunner`
 * and is excluded from the regular CI run by the
 * `@Fixture` filter in `app/build.gradle.kts`.
 *
 * Deterministic. The same seed (20260818) is used on every
 * run, so the note ids, timestamps, body choices, and
 * pinned / done / dueAt selection are identical on every
 * invocation. The reminder alarm scheduling depends on
 * the device clock at the time of the run (`reminderAt`
 * is "now + N minutes"), but the *offsets* are fixed.
 *
 * ## Distribution (target)
 *
 *  - 40 Quick notes (`type = null`, body only)
 *  - 35 Task notes (`type = NoteType.TASK`, some with
 *    `dueAt`, some `done`, some pinned)
 *  - 25 Reminder notes (`type = NoteType.REMINDER`, all
 *    with a `reminderAt` in the next 5 minutes to 3 days,
 *    all alarms scheduled via [ReminderScheduler])
 *  - ~10 pinned (mix of types)
 *  - ~5 tasks done
 *  - ~20 tasks with a due time
 *
 * `createdAt` / `updatedAt` are spread over the last 60
 * days, with half the notes in the last 7 days. The exact
 * distribution is sampled with a fixed `Random(SEED)`.
 */
@Fixture
@RunWith(AndroidJUnit4::class)
class SeedNotes {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val rng = Random(SEED)

    private val quickCount = 40
    private val taskCount = 35
    private val reminderCount = 25
    private val pinnedTarget = 10
    private val doneTarget = 5
    private val dueAtTarget = 20
    private val total = quickCount + taskCount + reminderCount

    /**
     * The body pool. Pulled deterministically per note via
     * the fixed `Random`. Roughly half are short, the rest
     * are a paragraph; one in six has a multi-line body; a
     * few have em-dashes, in the way real quick notes do.
     * The em-dash is U+2014, three UTF-8 bytes — the codec
     * round-trips it as base64 of those three bytes.
     */
    private val bodyPool: List<String> = listOf(
        "Buy milk",
        "Call mom back about the trip",
        "Project: write up the findings — three paragraphs by Friday",
        "Note from the morning standup: the latency on /search is down to 40ms but the result count is off",
        "Reminder: take medication at 8am with food — not on empty stomach",
        "Things to do this weekend\n1. Rake the leaves\n2. Fix the kitchen tap\n3. Write a letter",
        "Sketch the new landing-page wireframe tonight",
        "Drop the parcel at the post office on the way back",
        "Water the plants before going out",
        "Pick up dry cleaning on Thursday",
        "Ask Rahul about the WiFi router — he said he'd check on the weekend",
        "Coffee with Aisha at 4 — bring the book she lent",
        "Look up the recipe for the lemon-olive-oil cake",
        "Renew library books by Saturday\nReturn: Two Lives\nReturn: A House for Mr B\nReturn: Salt Houses",
        "Send the contract over to legal — they want the signed copy by Tuesday",
        "Doctor's appointment at 11:30, then lunch with the team",
        "Read the long email from Pradeep about the migration — at least the first three paragraphs",
        "Meds this week: morning, then evening. Don't skip the evening one",
        "Workshop prep: print the handouts and check the projector",
        "Piano practice — work on the second movement for thirty minutes",
        "Plan the trip to Coorg — check trains, look at the homestay options",
        "Yoga at 7. Phone in the locker. No checking it during the class",
        "Send a message to dad. Just a short one. The cricket score is enough",
        "Tidy the desk. The pile of papers by the window needs to go",
        "Write down three things that went well today before sleeping",
        "Refill the kettle filter",
        "Tax filing — gather the rent receipts and the medical bills",
        "Replace the shower head. The old one leaks at the joint",
        "Pick up the suit from the tailor on the way home from work",
        "The garden needs a trim before Sunday lunch",
        "Get the bike serviced — the chain is making a noise on the third gear",
        "Top up the Ola Money wallet before the auto shows up at 6",
        "Mango pickle from the store in Ulsoor — the one near the bus stop",
        "Pay the electricity bill before the 25th. Last time it was a 200-rupee late fee",
        "Reply to the LinkedIn message from the recruiter. Just a polite no",
        "Set the rice to soak. Two hours is enough",
        "Update the family WhatsApp photo. The current one is from the Diwali trip",
        "File the GST return for the quarter. The CA sent the draft last week",
        "Book the cab for Sunday morning. 5:30 pickup, Tidel Park to the airport",
        "Carry the umbrella. The weather says it might rain in the evening",
        "Check the tyre pressure on the scooter. It felt low on the way back yesterday",
        "Birthday gift for Meera — the silver earrings she saw at the shop on 12th Main",
        "Renew the domain name for the project. It expires on the 22nd",
        "Watch the 30-minute tutorial on the new editor. The shortcuts alone are worth it",
        "Stretch for ten minutes before bed. The back has been stiff all week",
        "Wash the curtains. They smell like the kitchen",
        "Print two copies of the lease. One for the file, one for the landlord",
        "Schedule the plumber for the dripping tap — any time after Wednesday",
        "Sort the photos from the Ladakh trip into a folder. Don't leave them in the camera roll",
    )

    @Test
    fun seed() = runBlocking {
        val prefs = NotesPrefs(context)

        // Clear any existing notes so the fixture is
        // idempotent — running it twice gives the same
        // 100 notes, not 200. There is no clearAll on
        // NotesPrefs; iterate the current store and
        // delete by id. Typical device state is empty or
        // near-empty so this loop is short.
        val existing = prefs.notes.first()
        existing.notes.forEach { prefs.delete(it.id) }

        val now = System.currentTimeMillis()
        val dayMs = TimeUnit.DAYS.toMillis(1)

        // Pre-pick which notes are pinned / done / have
        // a dueAt. Sampling ahead of the loop keeps the
        // counts exact rather than the expected value of
        // a Bernoulli trial, and the order of `rng` calls
        // is fixed so the choice is reproducible.
        val pinnedIdx: Set<Int> =
            (0 until total).shuffled(rng).take(pinnedTarget).toSet()
        val taskRange = quickCount until (quickCount + taskCount)
        val doneIdx: Set<Int> =
            taskRange.toList().shuffled(rng).take(doneTarget).toSet()
        val dueAtIdx: Set<Int> =
            taskRange.toList().shuffled(rng).take(dueAtTarget).toSet()

        // Date distribution: half the notes are in the
        // last 7 days, half are 7-60 days old. The mix is
        // shuffled so the recent/older split is
        // independent of the note type — a Reminder can
        // land in the last week, a Quick note can land
        // older, etc. The same shuffle order is used
        // every run because the rng is fixed.
        val recentOffsets = List(total / 2) { rng.nextInt(7) }
        val olderOffsets = List(total / 2) { 7 + rng.nextInt(54) }
        val dayOffsets: IntArray =
            (recentOffsets + olderOffsets).shuffled(rng).toIntArray()

        // Reminder times: spread from "in 5 min" to
        // "in 3 days" for the 25 reminder notes. Pulled
        // up front so the order of `rng` calls is fixed
        // before we start the per-note loop.
        val reminderOffsetsMs: LongArray = LongArray(reminderCount) {
            val minutes = 5L + rng.nextLong(3L * 24 * 60 - 5L + 1L)
            TimeUnit.MINUTES.toMillis(minutes)
        }

        var quickActual = 0
        var taskActual = 0
        var reminderActual = 0
        var pinnedActual = 0
        var doneActual = 0
        var reminderCursor = 0

        for (i in 0 until total) {
            val body = bodyPool[rng.nextInt(bodyPool.size)]
            // createdAt: random millisecond inside the
            // i-th day-slot. now is a 13-digit epoch so
            // the result is comfortably positive.
            val createdAt = now - dayOffsets[i] * dayMs - rng.nextLong(dayMs)
            // updatedAt: 0..119 minutes after createdAt.
            // Note.sanitised() coerces upward if the
            // jitter rolls to 0, so this is safe.
            val updatedAt = createdAt + (rng.nextInt(120) * 60_000L)
            val pinned = i in pinnedIdx

            val note: Note = when {
                i < quickCount -> {
                    Note(
                        id = prefs.nextNoteId(),
                        body = body,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        pinned = pinned,
                    )
                }
                i < quickCount + taskCount -> {
                    // 1 hour to 14 days from now, so the
                    // row shows a future due time and
                    // exercises the "due in N days" UI.
                    val dueAt = if (i in dueAtIdx) {
                        now + TimeUnit.HOURS.toMillis(1L) +
                            rng.nextLong(14L * 24 * 60 * 60 * 1000L)
                    } else {
                        null
                    }
                    Note(
                        id = prefs.nextNoteId(),
                        body = body,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        pinned = pinned,
                        type = NoteType.TASK,
                        dueAt = dueAt,
                        done = i in doneIdx,
                    )
                }
                else -> {
                    // Reminder. reminderAt is always
                    // future (5 min to 3 days from now)
                    // and the alarm is scheduled via the
                    // same path LauncherViewModel uses.
                    val at = now + reminderOffsetsMs[reminderCursor]
                    reminderCursor++
                    Note(
                        id = prefs.nextNoteId(),
                        body = body,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        pinned = pinned,
                        type = NoteType.REMINDER,
                        reminderAt = at,
                    )
                }
            }

            prefs.add(note)
            when (note.type) {
                NoteType.TASK -> {
                    taskActual++
                    if (note.done) doneActual++
                }
                NoteType.REMINDER -> {
                    reminderActual++
                    try {
                        ReminderScheduler.schedule(context, note.id, note.reminderAt!!)
                    } catch (security: SecurityException) {
                        // SCHEDULE_EXACT_ALARM may be
                        // revoked on a restricted device;
                        // the note is saved, the alarm
                        // may be late. Same fallback the
                        // production path uses.
                    }
                }
                else -> quickActual++
            }
            if (pinned) pinnedActual++
        }

        println(
            "Seeded $total notes: $quickActual Quick, " +
                "$taskActual Task, $reminderActual Reminder, " +
                "$pinnedActual pinned, $doneActual done",
        )
    }

    private companion object {
        const val SEED = 20260818
    }
}
