package org.mindanchor.advisory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Program 3 Task 1 — the build itself is the outermost gate.
 *
 * Nothing here is a preference a person can flip. An ordinary build
 * compiles with an empty allowlist, so there is no protocol for the rest
 * of Program 3 to deliver even if every later gate were somehow opened.
 * A personal research build may name exactly one frozen tuple, and naming
 * it is still not permission to deliver it: operational evidence, the
 * master opt-in, and the delivery switch are separate and all default
 * closed.
 */
class AdvisoryBuildAuthorizationTest {

    private val cyclic = ProtocolKey(
        protocolId = "cyclic-sighing",
        protocolVersion = 1,
        definitionSha256 = "1298bdfeab7d10263ca41c47a7982231181e3eb95c38eaf0465463baba1cdae0",
    )

    @Test
    fun `ordinary builds expose zero deliverable protocols`() {
        val auth = AdvisoryBuildAuthorization.forFlags(
            personalResearchBuild = false,
            operationalEvidenceApproved = false,
        )
        assertEquals(AdvisoryBuildMode.ORDINARY, auth.buildMode)
        assertFalse(auth.operationalEvidenceApproved)
        assertTrue(auth.protocolAllowlist.isEmpty())
    }

    @Test
    fun `personal build remains closed without operational evidence`() {
        val auth = AdvisoryBuildAuthorization.forFlags(true, false)
        assertEquals(AdvisoryBuildMode.PERSONAL_RESEARCH, auth.buildMode)
        assertFalse(auth.operationalEvidenceApproved)
        assertEquals(setOf(cyclic), auth.protocolAllowlist)
    }

    @Test
    fun `only the exact frozen cyclic sighing tuple is allowlisted`() {
        val auth = AdvisoryBuildAuthorization.forFlags(true, true)
        assertEquals(setOf(cyclic), auth.protocolAllowlist)
        assertEquals(
            "9f71a3690bf4b0b07ade1ef6963ca8d36c4e6227342cb1911f27dbb4f2cf44ee",
            AdvisoryBuildAuthorization.PROGRAM_THREE_CATALOG_SHA256,
        )
        assertFalse(
            auth.protocolAllowlist.any { it.protocolId != "cyclic-sighing" || it.protocolVersion != 1 },
        )
    }

    @Test
    fun `an ordinary build cannot inherit operational evidence approval`() {
        // The ordinary branch discards the flag rather than storing it:
        // a mis-set property on a public build must not leave a true
        // value sitting in the authorization for a later gate to read.
        val auth = AdvisoryBuildAuthorization.forFlags(
            personalResearchBuild = false,
            operationalEvidenceApproved = true,
        )
        assertEquals(AdvisoryBuildMode.ORDINARY, auth.buildMode)
        assertFalse(auth.operationalEvidenceApproved)
        assertTrue(auth.protocolAllowlist.isEmpty())
    }
}
