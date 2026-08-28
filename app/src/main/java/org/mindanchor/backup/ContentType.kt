package org.mindanchor.backup

/**
 * The per-type routing dimension for the Google
 * Drive backup. Each value maps to exactly one file
 * in the user's Drive root: the [fileName] is the
 * file [GoogleDriveBackupTarget] finds-or-creates on
 * the first append, then appends to for every
 * subsequent entry of that type.
 *
 * The model is "one file per content type" because
 * the user's spec was "single document for each like
 * whole journal though im giving in multiple it
 * should be stored in google drive in one document
 * file" — `each` refers to the type, not to the
 * individual entry. A note added at 8 AM and a note
 * added at 8 PM both go into `MindAnchor-Notes.txt`,
 * one line per append, in append order. The file is
 * a growing log of the user's own content; the
 * launcher never reads it back except on an explicit
 * restore.
 *
 * v0.70.7: extended from the original two types
 * (Notes, Letters) to also cover check-ins and
 * wellness readings — the same user later asked for
 * "all the data... and their analysis" to be backed
 * up too, not just notes. Reports/patterns are a
 * deliberately deferred fifth type (see
 * [org.mindanchor.backup.BackupScheduler]'s KDoc):
 * the local store keeps only the single latest
 * report, which is a different shape from "back up
 * everything currently on file" and needs its own
 * archive-on-each-run logic. The safety plan and
 * crisis contact list are deliberately excluded —
 * the user's own choice, matching how this app treats
 * that data everywhere else (see
 * `data_extraction_rules.xml`).
 *
 * Adding a new content type is a clinical-review
 * decision (each value is a new file in the user's
 * Drive, with a new name they may be surprised to
 * find).
 */
sealed interface ContentType {
    /**
     * The Drive file basename. Pinned here, not
     * derived, so a rename in one place ripples to
     * every backup target and finding test.
     */
    val fileName: String

    /**
     * The home-screen quick-capture. One file per
     * MindAnchor install, append-only, plain text
     * format. See [GoogleDriveBackupTarget] for the
     * wire format.
     */
    data object Notes : ContentType {
        override val fileName: String = "MindAnchor-Notes.txt"
    }

    /**
     * The daily letter (either the modern cloud-LLM
     * path or an existing pre-v0.70.6 legacy entry).
     * One file per MindAnchor install, append-only,
     * plain text format.
     */
    data object Letters : ContentType {
        override val fileName: String = "MindAnchor-Letters.txt"
    }

    /**
     * The EMA check-ins ([org.mindanchor.model.Moment]):
     * valence, arousal, and when they were logged.
     * This is the person's own labelled data — the
     * "analysis" side of "the data and their
     * analysis" the Drive sync now covers.
     */
    data object CheckIns : ContentType {
        override val fileName: String = "MindAnchor-CheckIns.txt"
    }

    /**
     * The wellness signal history
     * ([org.mindanchor.vitals.Measurement]): HRV,
     * resting heart rate, sleep minutes, steps,
     * mindfulness minutes — whatever Health Connect or
     * a bridged wearable has supplied, one entry per
     * (day, signal). This is the raw "data" half of
     * "the data and their analysis".
     */
    data object WellnessReadings : ContentType {
        override val fileName: String = "MindAnchor-Wellness.txt"
    }
}
