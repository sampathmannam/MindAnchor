package org.mindanchor.support

/**
 * Matching phone numbers well enough to recognise a chosen person.
 *
 * A notification may identify someone as "tel:+91 98765 43210" while the
 * saved contact reads "098765 43210". Comparing the significant digits from
 * the right handles country codes, trunk prefixes and formatting without
 * pulling in a full phone-number library — and a false negative here means
 * a person's message waits for the next batch, which for someone with
 * rejection sensitivity is the failure that actually hurts.
 */
object PhoneMatch {

    /** Enough digits to be confident, short enough to survive country codes. */
    private const val SIGNIFICANT_DIGITS = 7

    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.filter { it.isDigit() }
        if (digits.length < SIGNIFICANT_DIGITS) return null
        return digits.takeLast(SIGNIFICANT_DIGITS)
    }

    /** True when both identifiers plausibly denote the same person. */
    fun sameNumber(a: String?, b: String?): Boolean {
        val left = normalize(a) ?: return false
        val right = normalize(b) ?: return false
        return left == right
    }

    /**
     * True if any identifier attached to a notification — a `tel:` URI, a
     * raw number, or a display name — belongs to one of [contacts].
     */
    fun mentionsAny(
        identifiers: Collection<String>,
        contacts: Collection<CrisisContactRef>,
    ): Boolean {
        if (identifiers.isEmpty() || contacts.isEmpty()) return false
        val normalizedContacts = contacts.mapNotNull { normalize(it.phone) }.toSet()
        val contactNames = contacts
            .map { it.name.trim().lowercase() }
            .filter { it.length >= 3 }
            .toSet()

        return identifiers.any { identifier ->
            val number = normalize(identifier)
            if (number != null && number in normalizedContacts) return@any true
            val text = identifier.substringAfter(':').trim().lowercase()
            text.isNotEmpty() && text in contactNames
        }
    }
}

/** The fields of a crisis contact this matcher needs, free of Room types. */
data class CrisisContactRef(val name: String, val phone: String)
