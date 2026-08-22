package org.mindanchor.backup

/**
 * The per-type routing dimension for the v0.25.4
 * backup path. Each value maps to exactly one file in
 * the user's Drive root: the [fileName] is the file
 * the [GoogleDriveBackupTarget] finds-or-creates on
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
 * Adding a new content type is a clinical-review
 * decision (each value is a new file in the user's
 * Drive, with a new name they may be surprised to
 * find). The v0.25.4 surface is the two that
 * v0.25.2's letter feature and the home-screen
 * quick-capture produce; future values
 * (`CheckIns`, `Patterns`, etc.) opt in by adding
 * a new case here and a new producer at the call
 * site.
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
     * The v0.25.2 daily letter. One file per
     * MindAnchor install, append-only, plain text
     * format. The letter body is wrapped in
     * [EncryptedBackupCodec] before transport; the
     * wrapper is on the inside, the line-per-append
     * shape is on the outside.
     */
    data object Letters : ContentType {
        override val fileName: String = "MindAnchor-Letters.txt"
    }
}
