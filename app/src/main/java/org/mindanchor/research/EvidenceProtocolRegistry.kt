package org.mindanchor.research

import java.security.MessageDigest
import java.util.Collections
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The result of checking a protocol against §4.4's contract. */
sealed class ProtocolValidation {
    data object Valid : ProtocolValidation()

    /** [field] is the property name that failed, so a failure message points at a line of code. */
    data class Invalid(val field: String, val reason: String) : ProtocolValidation()
}

/**
 * The evidence protocol registry.
 *
 * Two jobs, both narrow:
 *
 *  - **Refuse incomplete evidence.** [validate] checks every field §4.4
 *    requires, in a fixed order, and returns the first failure. [of]
 *    throws rather than building a registry that contains one, so an
 *    incomplete protocol never exists as a registered object.
 *  - **Make a definition tamper-evident.** [definitionSha256] and
 *    [catalogSha256] are content hashes. The catalogue hash is part of the
 *    provenance version vector, so editing a protocol without bumping its
 *    version both fails the frozen-hash test at build time and — if it
 *    somehow shipped — opens a new study phase rather than silently
 *    reinterpreting history.
 *
 * Program 1 does not select, schedule, deliver, or evaluate a protocol.
 */
class EvidenceProtocolRegistry private constructor(val protocols: List<EvidenceProtocol>) {

    /** The exact `(id, version)` definition, or null. */
    fun find(id: String, version: Int): EvidenceProtocol? =
        protocols.firstOrNull { it.id == id && it.version == version }

    /** The highest registered version of [id], or null. */
    fun latest(id: String): EvidenceProtocol? =
        protocols.filter { it.id == id }.maxByOrNull { it.version }

    /**
     * SHA-256 over every `id@version:definitionSha256` line, sorted, so the
     * hash describes the catalogue's content and not the order it happened
     * to be declared in.
     */
    val catalogSha256: String by lazy {
        val lines = protocols
            .map { "${it.id}@${it.version}:${definitionSha256(it)}" }
            .sorted()
            .joinToString(separator = "\n")
        sha256Hex(lines)
    }

    companion object {

        /**
         * Pinned: this configuration is part of the definition hash. Adding
         * `prettyPrint`, changing `encodeDefaults`, or renaming a field of
         * [CanonicalProtocol] changes every `definitionSha256` and
         * `catalogSha256` without any protocol having changed. Treat such a
         * change the way a wire-format change is treated — the catalogue
         * freeze test in `EvidenceProtocolCatalogTest` goes red, and the
         * answer is a protocol version bump, not a re-pinned constant.
         */
        private val json = Json {
            encodeDefaults = true
            prettyPrint = false
            explicitNulls = true
        }

        private val ID_PATTERN = Regex("^[a-z][a-z0-9]*(-[a-z0-9]+)*$")

        /**
         * A reference has to be something a reader can actually follow.
         * Without this, `reference = "trust me"` passes, and the field
         * whose whole purpose is verifiability becomes the one taken on
         * trust — which is also the practical way round the excluded
         * source types.
         */
        private val REFERENCE_PATTERN = Regex("""^(https?://\S+|10\.\d{4,9}/\S+)$""")

        /**
         * Checks [protocol] against §4.4's contract and returns the first
         * failure. The check order is fixed so the same incomplete protocol
         * always reports the same field — a moving target would make the
         * failure message useless as a fix instruction.
         */
        @Suppress("CyclomaticComplexMethod", "ReturnCount")
        fun validate(protocol: EvidenceProtocol): ProtocolValidation {
            if (!ID_PATTERN.matches(protocol.id)) {
                return ProtocolValidation.Invalid("id", "must be lowercase-kebab, was '${protocol.id}'")
            }
            if (protocol.version < 1) {
                return ProtocolValidation.Invalid("version", "must be 1 or greater, was ${protocol.version}")
            }
            blankFailure("targetState", protocol.targetState)?.let { return it }
            blankFailure("intendedPopulation", protocol.intendedPopulation)?.let { return it }
            listFailure("exclusions", protocol.exclusions)?.let { return it }
            evidenceFailure(protocol.evidenceSources)?.let { return it }
            blankFailure("mechanism", protocol.mechanism)?.let { return it }
            blankFailure("expectedOutcome", protocol.expectedOutcome)?.let { return it }
            listFailure("eligibilityRules", protocol.eligibilityRules)?.let { return it }
            listFailure("contraindicationRules", protocol.contraindicationRules)?.let { return it }
            stepFailure(protocol.steps)?.let { return it }
            if (protocol.permittedModalities.isEmpty()) {
                return ProtocolValidation.Invalid("permittedModalities", "must name at least one modality")
            }
            if (protocol.maxDurationSeconds <= 0) {
                return ProtocolValidation.Invalid("maxDurationSeconds", "must be positive")
            }
            // Cross-field: a protocol whose steps cannot fit inside its own
            // maximum describes a run that can never complete.
            if (protocol.steps.sumOf { it.durationSeconds.toLong() } > protocol.maxDurationSeconds.toLong()) {
                return ProtocolValidation.Invalid(
                    "maxDurationSeconds",
                    "must be at least the sum of the step durations",
                )
            }
            if (protocol.stopRules.isEmpty()) {
                return ProtocolValidation.Invalid("stopRules", "must name at least one stop rule")
            }
            if (protocol.cooldownSeconds < 0) {
                return ProtocolValidation.Invalid("cooldownSeconds", "must not be negative")
            }
            if (protocol.outcomeWindowSeconds <= 0) {
                return ProtocolValidation.Invalid("outcomeWindowSeconds", "must be positive")
            }
            blankFailure("successInterpretation", protocol.successInterpretation)?.let { return it }
            blankFailure("userFacingExplanation", protocol.userFacingExplanation)?.let { return it }
            return ProtocolValidation.Valid
        }

        /**
         * SHA-256 over [protocol]'s canonical form. Sets become sorted name
         * lists (a set has no meaningful order); every authored list keeps
         * the order it was written in, because reordering an author's
         * exclusions or steps *is* a definition change.
         */
        fun definitionSha256(protocol: EvidenceProtocol): String =
            sha256Hex(json.encodeToString(canonical(protocol)))

        /**
         * Copies [protocols] defensively. A `List` in Kotlin is read-only,
         * not immutable — holding the caller's reference would let a
         * caller append an unvalidated protocol after registration and,
         * because [catalogSha256] is lazy, have the catalogue report a
         * hash computed before the mutation. That is exactly the
         * "changed without a version bump, undetected" failure the hash
         * exists to prevent.
         *
         * @throws IllegalArgumentException if [protocols] is empty, on the
         *   first invalid protocol, or on a duplicate `(id, version)`.
         *   There is no partial registration.
         */
        fun of(protocols: List<EvidenceProtocol>): EvidenceProtocolRegistry {
            require(protocols.isNotEmpty()) {
                "an empty registry would still produce a plausible-looking catalogue hash"
            }
            // The message is built where the smart cast is available, not
            // inside `require`'s lambda: downcasting there would throw a
            // ClassCastException instead of the diagnostic if
            // ProtocolValidation ever gained a third case.
            val firstFailure = protocols.firstNotNullOfOrNull { protocol ->
                val result = validate(protocol)
                if (result is ProtocolValidation.Invalid) {
                    "protocol '${protocol.id}' is not registrable: ${result.field} ${result.reason}"
                } else {
                    null
                }
            }
            require(firstFailure == null) { firstFailure.orEmpty() }

            // The `id@version` line format below is also the catalogue
            // hash's line format; ID_PATTERN excludes ':' and newlines,
            // which is what keeps those lines unambiguous.
            val duplicateKey = protocols
                .map { "${it.id}@${it.version}" }
                .groupingBy { it }
                .eachCount()
                .entries
                .firstOrNull { it.value > 1 }
                ?.key
            require(duplicateKey == null) { "duplicate protocol registration: $duplicateKey" }

            return EvidenceProtocolRegistry(
                Collections.unmodifiableList(protocols.map { it.defensiveCopy() }),
            )
        }

        private fun EvidenceProtocol.defensiveCopy(): EvidenceProtocol = copy(
            exclusions = Collections.unmodifiableList(exclusions.toList()),
            evidenceSources = Collections.unmodifiableList(evidenceSources.map { it.copy() }),
            eligibilityRules = Collections.unmodifiableList(eligibilityRules.toList()),
            contraindicationRules = Collections.unmodifiableList(contraindicationRules.toList()),
            steps = Collections.unmodifiableList(steps.map { it.copy() }),
            permittedModalities = Collections.unmodifiableSet(permittedModalities.toSet()),
            stopRules = Collections.unmodifiableSet(stopRules.toSet()),
        )

        private fun blankFailure(field: String, value: String): ProtocolValidation.Invalid? =
            if (value.isBlank()) ProtocolValidation.Invalid(field, "must not be blank") else null

        private fun listFailure(field: String, values: List<String>): ProtocolValidation.Invalid? = when {
            values.isEmpty() -> ProtocolValidation.Invalid(field, "must not be empty")
            values.any { it.isBlank() } -> ProtocolValidation.Invalid(field, "must not contain a blank entry")
            else -> null
        }

        private fun evidenceFailure(sources: List<EvidenceSource>): ProtocolValidation.Invalid? = when {
            sources.isEmpty() ->
                ProtocolValidation.Invalid("evidenceSources", "must cite at least one source")
            sources.any { it.title.isBlank() || it.citation.isBlank() || it.reference.isBlank() } ->
                ProtocolValidation.Invalid(
                    "evidenceSources",
                    "every source needs a title, a citation and a reference",
                )
            sources.any { !REFERENCE_PATTERN.containsMatchIn(it.reference) } ->
                ProtocolValidation.Invalid(
                    "evidenceSources",
                    "every reference must be a resolvable URL or DOI a reader can check",
                )
            sources.any { !it.sourceType.isPermitted } ->
                ProtocolValidation.Invalid(
                    "evidenceSources",
                    "blog, influencer, marketing, and AI-generated sources are excluded",
                )
            else -> null
        }

        private fun stepFailure(steps: List<ProtocolStep>): ProtocolValidation.Invalid? = when {
            steps.isEmpty() -> ProtocolValidation.Invalid("steps", "must have at least one step")
            steps.any { it.instruction.isBlank() } ->
                ProtocolValidation.Invalid("steps", "every step needs an instruction")
            steps.any { it.durationSeconds <= 0 } ->
                ProtocolValidation.Invalid("steps", "every step needs a positive duration")
            steps.map { it.ordinal } != steps.indices.map { it + 1 } ->
                ProtocolValidation.Invalid("steps", "ordinals must run 1..n in order")
            else -> null
        }

        private fun canonical(protocol: EvidenceProtocol) = CanonicalProtocol(
            id = protocol.id,
            version = protocol.version,
            targetState = protocol.targetState,
            intendedPopulation = protocol.intendedPopulation,
            exclusions = protocol.exclusions,
            evidenceSources = protocol.evidenceSources,
            mechanism = protocol.mechanism,
            expectedOutcome = protocol.expectedOutcome,
            eligibilityRules = protocol.eligibilityRules,
            contraindicationRules = protocol.contraindicationRules,
            steps = protocol.steps,
            permittedModalities = protocol.permittedModalities.map { it.name }.sorted(),
            maxDurationSeconds = protocol.maxDurationSeconds,
            stopRules = protocol.stopRules.map { it.name }.sorted(),
            cooldownSeconds = protocol.cooldownSeconds,
            outcomeWindowSeconds = protocol.outcomeWindowSeconds,
            successInterpretation = protocol.successInterpretation,
            clinicalReviewStatus = protocol.clinicalReviewStatus.name,
            userFacingExplanation = protocol.userFacingExplanation,
        )

        private fun sha256Hex(text: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(text.encodeToByteArray())
                .joinToString(separator = "") { "%02x".format(it) }

        @Serializable
        private data class CanonicalProtocol(
            val id: String,
            val version: Int,
            val targetState: String,
            val intendedPopulation: String,
            val exclusions: List<String>,
            val evidenceSources: List<EvidenceSource>,
            val mechanism: String,
            val expectedOutcome: String,
            val eligibilityRules: List<String>,
            val contraindicationRules: List<String>,
            val steps: List<ProtocolStep>,
            val permittedModalities: List<String>,
            val maxDurationSeconds: Int,
            val stopRules: List<String>,
            val cooldownSeconds: Int,
            val outcomeWindowSeconds: Int,
            val successInterpretation: String,
            val clinicalReviewStatus: String,
            val userFacingExplanation: String,
        )
    }
}
