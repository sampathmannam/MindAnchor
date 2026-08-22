package org.mindanchor.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mindanchor.data.db.CrisisContact
import org.mindanchor.data.db.PulseResult
import org.mindanchor.data.db.SafetyPlan
import org.mindanchor.model.Moment
import org.mindanchor.vitals.Measurement

/**
 * Reading and writing a MindAnchor backup file.
 *
 * ## Why this exists
 *
 * The default save destination is the system file picker: a person
 * chooses the location, the file lands there, no upload happens
 * automatically, and no storage permission is involved. This is the
 * same posture the launcher has had since v0.17.0 — a safety plan has
 * no business on a server the user did not pick.
 *
 * v0.25.4 added a second, opt-in outbound channel: a Google Drive bridge
 * to the user's own account, per-type file routing
 * (`MindAnchor-Notes.txt`, `MindAnchor-Letters.txt`), AES-256-GCM
 * wrapped, off until the user signs in with Google in Settings. The
 * v0.23.0 WebDAV bridge has been removed; the architecture is
 * "user's own account" — not a third-party backup service. See
 * `## Outbound channels` below.
 *
 * The honest consequence of the local-only default is still true: a lost
 * or reset phone that has no WebDAV bridge armed takes the safety plan,
 * the chosen contacts and the mood history with it, permanently — and
 * those are exactly the things a person wrote down while calm so that
 * a future, worse version of themselves would not have to remember them.
 * The opt-in WebDAV bridge is the answer for a person who would rather
 * not rely on a remembered file path.
 *
 * ## Outbound channels
 *
 * Two paths leave the device. Both are deliberate, both are gated on a
 * user gesture, neither is automatic.
 *
 *  - **Local (default, always on).** The system file picker. The user
 *    picks the folder, the file lands there as plain JSON, no network
 *    call is made, no storage permission is required. The privacy
 *    promise is: data leaves this device only when someone chooses to
 *    move it.
 *
 *  - **Google Drive (opt-in, off by default, v0.25.4+).** The user
 *    signs in with Google in Settings → Reading → Google Drive, the
 *    launcher requests the narrowest per-file scope
 *    (`drive.file`), and every new note / letter is wrapped in
 *    AES-256-GCM ([org.mindanchor.backup.EncryptedBackupCodec]) and
 *    appended to the per-type file in the user's Drive root. The
 *    cloud sees an opaque 12-byte IV prefix, a ciphertext, and an
 *    auth tag — no information about the plaintext. The transport
 *    ([org.mindanchor.backup.GoogleDriveBackupTarget]) uses raw
 *    Drive REST over HTTPS, with the OAuth bearer from
 *    [org.mindanchor.backup.GoogleDriveAuth]. Per-type routing is
 *    the v0.25.4 model: one Drive file per
 *    [org.mindanchor.backup.ContentType] value (Notes, Letters for
 *    v0.25.4). The v0.23.0 WebDAV bridge has been removed.
 *
 * ## What is in it, and what is not
 *
 * Held notifications are excluded. They are other people's messages,
 * they age out within hours, and nobody restoring a phone wants last
 * Tuesday's shopping app alert back. Everything else a person authored or
 * chose is included.
 *
 * ## Format
 *
 * JSON, with a version number as the first field. It is meant to be
 * readable: someone who wants to check what they are carrying around
 * should be able to open the file and see plain text rather than a blob.
 * That readability is also why the file must be treated as sensitive —
 * [Backup.NOTE] says so inside the file itself.
 */
object BackupCodec {

    /**
     * Bumped only when the shape changes incompatibly. [decode] accepts any
     * version it knows, so an older file keeps working after an upgrade —
     * a backup that cannot be restored is not a backup.
     */
    const val CURRENT_VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class Backup(
        val version: Int = CURRENT_VERSION,
        val note: String = NOTE,
        val savedAt: Long = 0L,
        val plan: Plan = Plan(),
        val contacts: List<Contact> = emptyList(),
        val pulses: List<Pulse> = emptyList(),
        val favorites: List<String> = emptyList(),
        val hidden: List<String> = emptyList(),
        val frictioned: List<String> = emptyList(),
        val renames: Map<String, String> = emptyMap(),
        // The three latecomers, and the reason this file matters most.
        // Check-ins are the labels the personal model is fitted to and
        // accumulate at a few per day at best; readings are deliberate
        // ninety-second acts; corpus additions are hand-curated research.
        // None of the three can be regenerated, and until they were here,
        // "keeping a copy" quietly kept the replaceable parts and lost
        // the irreplaceable ones. Defaults keep every older file
        // readable: absent fields simply decode empty.
        val checkIns: List<CheckIn> = emptyList(),
        val readings: List<Reading> = emptyList(),
        val corpusAdditions: String = "",
        // What the phone worked out about its own days — the screen
        // rhythm ledger. Not authored by the person, but it took months
        // to accumulate and the event log it came from forgets in a
        // week, so losing it to a reset costs the baselines their
        // memory. Same Reading shape and the same default-empty
        // discipline that keeps every older file readable.
        val inferred: List<Reading> = emptyList(),
    ) {
        companion object {
            const val NOTE =
                "This file contains your safety plan, the people you chose to call, " +
                    "and your check-in history. Keep it somewhere only you can reach."
        }
    }

    @Serializable
    data class Plan(
        val warningSigns: String = "",
        val copingSteps: String = "",
        val distractions: String = "",
        val reasonsForLiving: String = "",
        val environmentSafety: String = "",
    )

    @Serializable
    data class Contact(
        val name: String = "",
        val phone: String = "",
        val isProfessional: Boolean = false,
    )

    @Serializable
    data class Pulse(val score: Int = 0, val takenAt: Long = 0L)

    @Serializable
    data class CheckIn(
        val valence: Int = 0,
        val arousal: Int = 0,
        val minuteOfDay: Int = 0,
        val day: String = "",
    )

    @Serializable
    data class Reading(val day: String = "", val key: String = "", val value: Double = 0.0)

    fun encode(backup: Backup): String = json.encodeToString(Backup.serializer(), backup)

    /**
     * Parses [text], or returns null if it is not a backup this build can
     * read.
     *
     * Deliberately forgiving about unknown fields and missing ones, and
     * deliberately strict about the version: restoring a file written by a
     * newer build could silently drop whatever that build added, and
     * silently losing part of a safety plan is worse than refusing.
     */
    fun decode(text: String): Backup? {
        val parsed = runCatching { json.decodeFromString(Backup.serializer(), text) }.getOrNull()
            ?: return null
        if (parsed.version !in 1..CURRENT_VERSION) return null
        return parsed
    }

    // --- Conversions to and from the app's own types ----------------------

    fun planOf(plan: SafetyPlan) = Plan(
        warningSigns = plan.warningSigns,
        copingSteps = plan.copingSteps,
        distractions = plan.distractions,
        reasonsForLiving = plan.reasonsForLiving,
        environmentSafety = plan.environmentSafety,
    )

    fun toSafetyPlan(plan: Plan, updatedAt: Long) = SafetyPlan(
        warningSigns = plan.warningSigns,
        copingSteps = plan.copingSteps,
        distractions = plan.distractions,
        reasonsForLiving = plan.reasonsForLiving,
        environmentSafety = plan.environmentSafety,
        updatedAt = updatedAt,
    )

    fun contactOf(contact: CrisisContact) =
        Contact(contact.name, contact.phone, contact.isProfessional)

    /**
     * Contacts with no number are dropped on the way back in, for the same
     * reason they cannot be added: a contact that cannot be called is a
     * dead button at the top of the crisis card.
     */
    fun toCrisisContacts(contacts: List<Contact>): List<CrisisContact> =
        contacts.filter { it.phone.isNotBlank() }
            .map { CrisisContact(name = it.name, phone = it.phone, isProfessional = it.isProfessional) }

    fun pulseOf(result: PulseResult) = Pulse(result.score, result.takenAt)

    fun toPulseResults(pulses: List<Pulse>): List<PulseResult> =
        pulses.map { PulseResult(score = it.score, takenAt = it.takenAt) }

    fun checkInOf(moment: Moment) = CheckIn(
        valence = moment.valence,
        arousal = moment.arousal,
        minuteOfDay = moment.atMinuteOfDay,
        day = moment.day,
    )

    /**
     * A check-in that fails [Moment]'s own 1..5 validity is dropped on the
     * way back in rather than let into the label history — an
     * out-of-range label from a hand-edited file would quietly bend every
     * baseline and pattern fitted after it.
     */
    fun toMoments(checkIns: List<CheckIn>): List<Moment> =
        checkIns.filter { Moment.isValid(it.valence) && Moment.isValid(it.arousal) && it.day.isNotBlank() }
            .map { Moment(valence = it.valence, arousal = it.arousal, atMinuteOfDay = it.minuteOfDay, day = it.day) }

    fun readingOf(measurement: Measurement) =
        Reading(day = measurement.day, key = measurement.key, value = measurement.value)

    fun toMeasurements(readings: List<Reading>): List<Measurement> =
        readings.filter { it.day.isNotBlank() && it.key.isNotBlank() }
            .map { Measurement(day = it.day, key = it.key, value = it.value) }
}
