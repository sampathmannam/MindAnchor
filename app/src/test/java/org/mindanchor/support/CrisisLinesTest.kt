package org.mindanchor.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ordering the crisis card is safety-critical enough to pin down: the
 * wrong number at the top of that card is a person dialling something
 * that does not answer, at the worst possible moment.
 */
class CrisisLinesTest {

    @Test
    fun theLocalLineComesFirst() {
        assertEquals("GB", CrisisLines.forCountry("GB").first().country)
        assertEquals("AU", CrisisLines.forCountry("AU").first().country)
        assertEquals("IN", CrisisLines.forCountry("IN").first().country)
    }

    @Test
    fun aLowercaseOrPaddedCountryStillMatches() {
        // Locale country codes are supposed to be uppercase, but this is
        // not the place to be strict about input hygiene.
        assertEquals("NZ", CrisisLines.forCountry("nz").first().country)
        assertEquals("NZ", CrisisLines.forCountry(" nz ").first().country)
    }

    @Test
    fun nothingIsEverDroppedForAnyCountry() {
        // Reordering is a hint, not a filter. Someone travelling must
        // still be able to reach the line for home.
        val countries = CrisisLines.ALL.map { it.country } + listOf("ZZ", "", null)
        countries.forEach { country ->
            assertEquals(
                "the full list must survive country=$country",
                CrisisLines.ALL.size,
                CrisisLines.forCountry(country).size,
            )
            assertEquals(
                "no line may be lost or duplicated for country=$country",
                CrisisLines.ALL.toSet(),
                CrisisLines.forCountry(country).toSet(),
            )
        }
    }

    @Test
    fun anUnknownCountryLeavesTheOrderAlone() {
        assertEquals(CrisisLines.ALL, CrisisLines.forCountry("ZZ"))
        assertEquals(CrisisLines.ALL, CrisisLines.forCountry(null))
        assertEquals(CrisisLines.ALL, CrisisLines.forCountry("   "))
    }

    @Test
    fun everyLineIsDialable() {
        CrisisLines.ALL.forEach { line ->
            assertTrue(
                "${line.country} has no digits to dial",
                line.number.any { it.isDigit() },
            )
            assertTrue(
                "${line.country} carries characters a dialer cannot use",
                line.number.all { it.isDigit() || it == ' ' || it == '-' || it == '+' },
            )
        }
    }

    @Test
    fun countryCodesAreUniqueSoOrderingIsDeterministic() {
        val codes = CrisisLines.ALL.map { it.country }
        assertEquals("duplicate country codes make 'local first' ambiguous", codes.size, codes.toSet().size)
    }
}
