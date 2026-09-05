package org.mindanchor.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * T-2.1 -- the domain half of people-first favorites.
 *
 * The launcher's favorites are apps. The evidence this phase rests on is
 * about *people* (Holt-Lunstad 2010; Verduyn 2017), so a favorite here is a
 * person plus the way they are reached, and tapping one opens the
 * conversation rather than the app that happens to host it.
 *
 * Kept free of Android types so the decisions that matter -- which intent,
 * and what counts as reachable -- are testable without a device.
 */
class PersonFavoriteTest {

    @Test
    fun `a person with a number and a channel is reachable`() {
        val mum = PersonFavorite.of(name = "Mum", phone = "098765 43210", channel = TalkChannel.CALL)
        assertEquals(PersonFavorite("Mum", "098765 43210", TalkChannel.CALL), mum)
    }

    @Test
    fun `a blank name is not a person`() {
        // "Talk to  " is not a thing the home surface can offer.
        assertNull(PersonFavorite.of("   ", "098765 43210", TalkChannel.CALL))
    }

    @Test
    fun `something too short to be a number is not reachable`() {
        // The bar is PhoneMatch's: fewer significant digits than that and
        // the launcher cannot claim it can reach anyone.
        assertNull(PersonFavorite.of("Mum", "123", TalkChannel.CALL))
    }

    @Test
    fun `the number keeps the shape the person typed`() {
        // Normalisation is for deciding reachability, not for storage: the
        // dialer should show what they entered, spacing and all.
        val mum = PersonFavorite.of("Mum", "+91 98765 43210", TalkChannel.TEXT)
        assertEquals("+91 98765 43210", mum?.phone)
    }

    @Test
    fun `the name is trimmed but not otherwise rewritten`() {
        assertEquals("Mum", PersonFavorite.of("  Mum  ", "098765 43210", TalkChannel.CALL)?.name)
    }

    @Test
    fun `calling opens the dialer rather than placing the call`() {
        // ACTION_DIAL, never ACTION_CALL. Placing a call the moment a tile
        // is touched is the wrong default for a launcher built around
        // pauses, and it is why this needs no CALL_PHONE permission. The
        // crisis card already makes this same choice.
        val mum = requireNotNull(PersonFavorite.of("Mum", "098765 43210", TalkChannel.CALL))
        assertEquals(
            TalkIntent("android.intent.action.DIAL", "tel:098765%2043210"),
            TalkTo.intentFor(mum),
        )
    }

    @Test
    fun `texting opens a conversation with that person already chosen`() {
        val sam = requireNotNull(PersonFavorite.of("Sam", "098765 43210", TalkChannel.TEXT))
        assertEquals(
            TalkIntent("android.intent.action.SENDTO", "smsto:098765%2043210"),
            TalkTo.intentFor(sam),
        )
    }

    @Test
    fun `a number carrying a plus survives into the intent`() {
        // '+' is meaningful in a tel: URI and must not be dropped or
        // treated as an encoded space.
        val mum = requireNotNull(PersonFavorite.of("Mum", "+919876543210", TalkChannel.CALL))
        assertEquals("tel:%2B919876543210", TalkTo.intentFor(mum).uri)
    }

    @Test
    fun `the label reads as the action, not as an app name`() {
        val mum = requireNotNull(PersonFavorite.of("Mum", "098765 43210", TalkChannel.CALL))
        assertEquals("Talk to Mum", mum.label())
    }
}
