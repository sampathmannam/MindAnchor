package org.mindanchor.advisory

import org.mindanchor.BuildConfig

/**
 * Program 3 Task 1 — the outermost gate, decided at compile time.
 *
 * Every other Program 3 gate is a value someone could change at runtime.
 * This one is not: an ordinary build compiles with an empty allowlist,
 * so there is no protocol for the rest of the path to deliver however
 * the later gates are set. That is what makes "public builds deliver
 * nothing" a property of the artifact rather than a promise about its
 * configuration.
 *
 * Naming a protocol is still not permission to deliver it. Operational
 * evidence, the person's master opt-in, and the delivery switch are
 * separate and all default closed, and the catalogue's protocol remains
 * `NOT_REVIEWED`.
 */
data class AdvisoryBuildAuthorization(
    val buildMode: AdvisoryBuildMode,
    val operationalEvidenceApproved: Boolean,
    val protocolAllowlist: Set<ProtocolKey>,
) {
    companion object {
        /**
         * The catalogue this authorization was written against. A
         * catalogue that no longer hashes to this value has gained,
         * lost, or edited a protocol since the allowlist below was
         * approved.
         */
        const val PROGRAM_THREE_CATALOG_SHA256 =
            "9f71a3690bf4b0b07ade1ef6963ca8d36c4e6227342cb1911f27dbb4f2cf44ee"

        private val personalAllowlist = setOf(
            ProtocolKey(
                protocolId = "cyclic-sighing",
                protocolVersion = 1,
                definitionSha256 = "1298bdfeab7d10263ca41c47a7982231181e3eb95c38eaf0465463baba1cdae0",
            ),
        )
        private val ordinaryAllowlist = emptySet<ProtocolKey>()

        /**
         * The ordinary branch discards [operationalEvidenceApproved]
         * rather than storing it: a mis-set property on a public build
         * must not leave a true value sitting here for a later gate to
         * read.
         */
        fun forFlags(
            personalResearchBuild: Boolean,
            operationalEvidenceApproved: Boolean,
        ): AdvisoryBuildAuthorization = if (personalResearchBuild) {
            AdvisoryBuildAuthorization(
                buildMode = AdvisoryBuildMode.PERSONAL_RESEARCH,
                operationalEvidenceApproved = operationalEvidenceApproved,
                protocolAllowlist = personalAllowlist,
            )
        } else {
            AdvisoryBuildAuthorization(
                buildMode = AdvisoryBuildMode.ORDINARY,
                operationalEvidenceApproved = false,
                protocolAllowlist = ordinaryAllowlist,
            )
        }

        fun current(): AdvisoryBuildAuthorization = forFlags(
            personalResearchBuild = BuildConfig.PROGRAM3_PERSONAL_RESEARCH,
            operationalEvidenceApproved = BuildConfig.PROGRAM3_OPERATIONAL_EVIDENCE_APPROVED,
        )
    }
}
