package org.mindanchor.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mindanchor.data.db.CrisisContact
import org.mindanchor.data.db.PulseResult
import org.mindanchor.data.db.SafetyPlan

/**
 * Reading and writing a MindAnchor backup file.
 *
 * ## Why this exists
 *
 * Cloud backup is refused outright, because a safety plan has no business
 * on someone else's server. The honest consequence is that a lost or reset
 * phone takes the safety plan, the chosen contacts and the mood history
 * with it, permanently — and those are exactly the things a person wrote
 * down while calm so that a future, worse version of themselves would not
 * have to remember them.
 *
 * So the copy is made deliberately, by the person, to a location they pick
 * through the system file picker. Nothing is automatic, nothing is
 * uploaded, and no storage permission is involved. The privacy promise is
 * unchanged: data leaves this device only when someone chooses to move it.
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
    ) {
        companion object {
            const val NOTE =
                "This file contains your safety plan and the people you chose to call. " +
                    "Keep it somewhere only you can reach."
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
}
