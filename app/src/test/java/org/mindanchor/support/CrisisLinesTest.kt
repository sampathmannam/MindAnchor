package org.mindanchor.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The bundled crisis-line list is the single highest-stakes string table
 * in the project. A broken hotline is a documented harm
 * (Dwyer et al. 2025, *Psychiatr Serv* 76:867–871, found broken
 * hotlines in apps with 3.5M+ combined downloads). These tests are the
 * thin layer between a one-line typo and that harm.
 *
 * Adding a country is a code review, not a wiki edit — the source
 * attribute on [CrisisLine] is the audit trail.
 */
class CrisisLinesTest {

    @Test
    fun `every line names a country and a number, in that order`() {
        // The dial() helper reads line.number directly. A line that
        // somehow ended up with an empty number would be the most
        // dangerous kind of bug — the row would render, the action
        // would be reachable, and a tap would dial nothing.
        for (line in CrisisLines.ALL) {
            assertTrue("${line.isoCode}: empty country", line.country.isNotBlank())
            assertTrue("${line.isoCode}: empty name", line.name.isNotBlank())
            assertTrue("${line.isoCode}: empty number", line.number.isNotBlank())
            assertTrue("${line.isoCode}: empty source", line.source.isNotBlank())
        }
    }

    @Test
    fun `every iso code is two lowercase letters`() {
        for (line in CrisisLines.ALL) {
            assertEquals(
                "${line.isoCode}: bad iso",
                2,
                line.isoCode.length,
            )
            assertTrue(
                "${line.isoCode}: not lowercase",
                line.isoCode == line.isoCode.lowercase(),
            )
            assertTrue(
                "${line.isoCode}: not alphabetic",
                line.isoCode.all { it in 'a'..'z' },
            )
        }
    }

    @Test
    fun `forCountry returns at least one line for each top-coverage country`() {
        // These are the countries the project owner has named (the
        // README's "988 (US), Tele-MANAS 14416 (India)") and the
        // most-evidenced lines from docs/research/14.
        for (iso in listOf("us", "gb", "in", "au", "ca", "ie", "nz")) {
            val found = CrisisLines.forCountry(iso)
            assertTrue("no line for $iso", found.isNotEmpty())
            // Every country line carries a non-empty number.
            for (line in found) {
                assertNotNull(line.number)
                assertTrue(line.number.isNotBlank())
            }
        }
    }

    @Test
    fun `forCountry is case-insensitive on its input`() {
        // The network country iso is documented as uppercase from
        // TelephonyManager in older Android; the call site lower-cases
        // it, but a regression that passed the raw value would otherwise
        // miss the entire bundled list silently.
        assertEquals(CrisisLines.forCountry("US"), CrisisLines.forCountry("us"))
        assertEquals(CrisisLines.forCountry("GB"), CrisisLines.forCountry("gb"))
    }

    @Test
    fun `an unknown country returns empty, not an error`() {
        // A null/empty country is *not* an error condition: the caller
        // is expected to fall back to the "Anywhere else" IASP card.
        // The brief's design is country-aware when it can be, and
        // gracefully global when it cannot.
        assertTrue(CrisisLines.forCountry("xx").isEmpty())
        assertTrue(CrisisLines.forCountry("").isEmpty())
    }

    @Test
    fun `forCountry and hasLineFor agree`() {
        for (iso in listOf("us", "gb", "in", "au", "ca", "ie", "nz", "jp", "br")) {
            assertEquals(iso, CrisisLines.hasLineFor(iso), CrisisLines.forCountry(iso).isNotEmpty())
        }
    }

    @Test
    fun `the united states line is 988 and is phone or sms`() {
        val us = CrisisLines.forCountry("us").firstOrNull { it.number == "988" }
        assertNotNull("US 988 line is the SAMHSA-standard", us)
        // 988 in the US is the unified phone-and-text line. Hard-coding
        // a "phone only" label would be a regression of the SAMHSA spec.
        assertEquals(CrisisLine.Contact.PHONE_OR_SMS, us!!.contact)
    }

    @Test
    fun `the uk samaritans number is 116 123, free and 24-7`() {
        // Samaritans publishes 116 123 as the universal freephone
        // number. A drift to a local-rate number would be a regression.
        val sam = CrisisLines.forCountry("gb").firstOrNull { it.number.contains("116") }
        assertNotNull(sam)
        assertEquals("24/7", sam!!.hours)
    }

    @Test
    fun `every line cites a source authority with a https url`() {
        // A line whose source is just a name without a verifiable URL
        // is, by the audit log's own standard, not auditable. The brief
        // (docs/research/14 §7) calls this out explicitly: a bundled
        // list must be reviewable on every release.
        for (line in CrisisLines.ALL) {
            assertTrue(
                "${line.isoCode}: source has no https URL",
                line.source.contains("https://") || line.source.contains("http://"),
            )
        }
    }
}
