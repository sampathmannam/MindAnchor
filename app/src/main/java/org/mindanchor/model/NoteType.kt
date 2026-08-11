package org.mindanchor.model

/**
 * The kind of note a [Note] is. Set by the on-device
 * classifier (v0.25.0) on save; edited notes are
 * re-classified. The launcher is type-less by design;
 * the type is a *label*, not a separate field the user
 * has to fill in.
 *
 * ## Why a single type per note
 *
 * v0.25.0 decision: each note is exactly one of these.
 * "Call Mom tomorrow" gets classified as either TASK
 * or REMINDER, not both. The chip-on-the-row UI is
 * simpler with one type, the filter is "show notes of
 * this type" rather than "show notes that include
 * this type", and the v0.25.0 spec explicitly chose
 * single over multi-tag.
 *
 * ## Why four and not more
 *
 * The user said "general notes, tasks, reminders as
 * well as my journal" in the brainstorm. The four
 * cover the four kinds the user types. Worry and
 * reflection stay as separate stores (v0.24.0 ships
 * them that way); they are *not* types on a note.
 *
 * ## Why the order matters
 *
 * The wire format uses the enum name. Adding a new
 * type later is a migration (the existing notes
 * decode fails on the unknown name and the line is
 * skipped — the codec is fail-closed). The four
 * types are the final four; if a fifth is needed,
 * it is a follow-up release with a documented
 * migration.
 */
enum class NoteType {
    /**
     * Catch-all: anything that isn't a task, reminder,
     * or journal entry. The default; also what a
     * malformed classifier output is folded into.
     */
    GENERAL,

    /**
     * "I need to do this." Verbs, intent, no specific
     * time bound. A task may have a deadline in the
     * user's words but it is not date-anchored in the
     * same way a reminder is.
     */
    TASK,

    /**
     * "Don't let me forget X." Time-bound, date-
     * anchored, or contains a time-relative phrase
     * ("tomorrow", "next week", "after the meeting").
     */
    REMINDER,

    /**
     * "What happened today / how I felt." First-person,
     * reflective, present-tense or past-tense. The
     * letter pulls from this bucket when the
     * night-report data is sparse.
     */
    JOURNAL,
}
