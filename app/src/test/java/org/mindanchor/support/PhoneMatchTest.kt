package org.mindanchor.support

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneMatchTest {

    private val ana = CrisisContactRef("Ana", "+91 98765 43210")
    private val doctor = CrisisContactRef("Dr Rao", "020 7946 0958")

    @Test
    fun `formatting and country codes do not defeat matching`() {
        assertTrue(PhoneMatch.sameNumber("+91 98765 43210", "098765 43210"))
        assertTrue(PhoneMatch.sameNumber("tel:+919876543210", "9876543210"))
        assertTrue(PhoneMatch.sameNumber("(020) 7946-0958", "02079460958"))
    }

    @Test
    fun `different numbers do not match`() {
        assertFalse(PhoneMatch.sameNumber("9876543210", "9876543211"))
        assertFalse(PhoneMatch.sameNumber("9876543210", null))
        assertFalse(PhoneMatch.sameNumber(null, null))
    }

    @Test
    fun `too few digits is not a number`() {
        assertNull(PhoneMatch.normalize("112"))
        assertNull(PhoneMatch.normalize(""))
        assertNull(PhoneMatch.normalize("Ana"))
    }

    @Test
    fun `a tel uri from a notification matches the saved contact`() {
        assertTrue(
            PhoneMatch.mentionsAny(listOf("tel:+919876543210"), listOf(ana, doctor)),
        )
    }

    @Test
    fun `a display name matches when the number is absent`() {
        assertTrue(PhoneMatch.mentionsAny(listOf("Ana"), listOf(ana)))
        assertTrue(PhoneMatch.mentionsAny(listOf("name:ana"), listOf(ana)))
    }

    @Test
    fun `unrelated senders never match`() {
        assertFalse(PhoneMatch.mentionsAny(listOf("tel:+911111111111"), listOf(ana)))
        assertFalse(PhoneMatch.mentionsAny(listOf("Amazon"), listOf(ana, doctor)))
        assertFalse(PhoneMatch.mentionsAny(emptyList(), listOf(ana)))
        assertFalse(PhoneMatch.mentionsAny(listOf("Ana"), emptyList()))
    }

    @Test
    fun `very short contact names cannot match by accident`() {
        val initial = CrisisContactRef("Jo", "5551234567")
        assertFalse(PhoneMatch.mentionsAny(listOf("Jo"), listOf(initial)))
        assertTrue(PhoneMatch.mentionsAny(listOf("tel:5551234567"), listOf(initial)))
    }
}
