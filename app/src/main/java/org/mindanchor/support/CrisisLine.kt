package org.mindanchor.support

/**
 * One crisis line, one number, one purpose.
 *
 * ## Why this exists
 *
 * `docs/CLINICAL_REVIEW.md` R1 records that the project deliberately
 * removed every hardcoded crisis line, on the reasoning that "prominent
 * hotline numbers can frighten people and clutter a screen meant to feel
 * calm". `docs/research/14` reviewed the primary safety literature
 * (Stanley & Brown 2012 SPI Step 5; WHO mhGAP 2023; SAMHSA 988; APA
 * Digital Mental Health 101; Dwyer 2025 *Psychiatr Serv*; NHS Design
 * Patterns for Mental Health) and concluded that:
 *
 *  - The "prominent helplines frighten people" rationale is **not** what
 *    the safety literature says. WHO 2023 *Reporting on Suicide* and the
 *    Hong Kong CSRP both ask for **prominent** display of helpline
 *    information; they caution against prominent display of the *suicide
 *    story / method*, not the helpline.
 *  - Stanley & Brown 2012 SPI Step 5 *requires* a 24/7 professional
 *    contact; a safety plan without one is not a complete plan.
 *  - Dwyer et al. 2025 (*Psychiatr Serv* 76:867–871) audited 302 US
 *    mental-health apps ≥1 year after 988 launched: only **15%** referred
 *    users to 988, and **14 apps with combined >3.5 million downloads
 *    contained broken hotlines**. The harm the original R1 decision
 *    tried to prevent is exactly the harm the evidence documents when
 *    lines are *absent* or *broken*.
 *
 * The clinical reviewer flagged R1 as the largest open clinical risk in
 * the app. This file is the narrow, evidence-respecting fix: a small,
 * hand-curated list of country-known-good helplines, surfaceable *on
 * demand* through a single calm affordance, with the exact wording the
 * NHS / Samaritans / Wysa / Headspace / NOCD / Calm / Woebot consensus
 * converges on — calm by default, *available* in distress.
 *
 * ## Why this is a hand-curated list, not a runtime lookup
 *
 * `findahelpline.com` (IASP / ThroughLine) is the gold-standard runtime
 * source (1,300+ lines, 130+ countries, verified daily), and a v1.1
 * integration there is the right move long-term. For v1, a bundled JSON
 * of the most-needed countries is what the Dwyer 2025 broken-hotline
 * paper explicitly recommends: lines that ship inside the app are
 * reviewed on every release, whereas a runtime lookup is only as good as
 * the network it depends on, and a non-functional `INTERNET` permission
 * is structural to this app's no-backend promise.
 *
 * The list is intentionally *small and conservative*. New countries are
 * added one release at a time, with the line's source, hours, and
 * contact method documented. Adding a line is a code review, not a wiki
 * edit.
 */
data class CrisisLine(
    /** "United States" / "United Kingdom" / "India" — display label. */
    val country: String,
    /** ISO 3166-1 alpha-2, lowercased — used to match. */
    val isoCode: String,
    /** "Suicide & Crisis Lifeline" / "Samaritans" / "Tele-MANAS". */
    val name: String,
    /** "Call or text 988" / "116 123" / "14416". */
    val number: String,
    /** Phone, SMS, or both — what the line accepts. */
    val contact: Contact,
    /** When the line is staffed. 24/7 unless stated. */
    val hours: String = "24/7",
    /** Primary source / authority for this line. Cited inline in the audit log. */
    val source: String,
) {
    enum class Contact { PHONE, SMS, PHONE_OR_SMS, WEB }
}

/**
 * The bundled, audited, smallest-possible list of crisis lines.
 *
 * **This is a code-review edit, not a wiki edit.** Every line below is
 * one a clinician or peer-reviewed source has named, and every release
 * must verify the line is still operational. A broken hotline is a
 * documented harm (Dwyer 2025), and the audit log on the next release is
 * what keeps the harm out of this app.
 */
object CrisisLines {

    val ALL: List<CrisisLine> = listOf(
        // United States — 988 Suicide & Crisis Lifeline, SAMHSA-funded,
        // operational since July 2022. Phone and SMS.
        CrisisLine(
            country = "United States",
            isoCode = "us",
            name = "988 Suicide & Crisis Lifeline",
            number = "988",
            contact = CrisisLine.Contact.PHONE_OR_SMS,
            source = "SAMHSA, https://www.samhsa.gov/find-help/helplines",
        ),

        // United Kingdom — Samaritans, the canonical UK line.
        CrisisLine(
            country = "United Kingdom",
            isoCode = "gb",
            name = "Samaritans",
            number = "116 123",
            contact = CrisisLine.Contact.PHONE,
            source = "Samaritans, https://www.samaritans.org/how-we-can-help/contact-samaritan/",
        ),
        // UK crisis text line.
        CrisisLine(
            country = "United Kingdom",
            isoCode = "gb",
            name = "SHOUT (text)",
            number = "85258",
            contact = CrisisLine.Contact.SMS,
            source = "Shout, https://giveusashout.org/",
        ),

        // Canada — Talk Suicide Canada (multilingual) and the 9-8-8
        // transition number, operational since 2023.
        CrisisLine(
            country = "Canada",
            isoCode = "ca",
            name = "9-8-8: Suicide Crisis Helpline",
            number = "988",
            contact = CrisisLine.Contact.PHONE_OR_SMS,
            source = "Centre for Addiction and Mental Health, https://988.ca/",
        ),

        // India — Tele-MANAS, the Government of India national mental-health
        // helpline. 14416 is the toll-free number. The brief cited this
        // line in the project README as the country's crisis line; the
        // project owner has explicitly chosen to keep Tele-MANAS bundled.
        CrisisLine(
            country = "India",
            isoCode = "in",
            name = "Tele-MANAS",
            number = "14416",
            contact = CrisisLine.Contact.PHONE,
            source = "Ministry of Health and Family Welfare, Government of India",
        ),
        // India — iCall, a long-running non-governmental line.
        CrisisLine(
            country = "India",
            isoCode = "in",
            name = "iCall",
            number = "+91 9152987821",
            contact = CrisisLine.Contact.PHONE,
            source = "iCall, https://icallhelpline.org/",
        ),

        // Australia — Lifeline, 13 11 14.
        CrisisLine(
            country = "Australia",
            isoCode = "au",
            name = "Lifeline",
            number = "13 11 14",
            contact = CrisisLine.Contact.PHONE,
            source = "Lifeline Australia, https://www.lifeline.org.au/",
        ),

        // European Union emergency services.
        CrisisLine(
            country = "European Union",
            isoCode = "eu",
            name = "Emergency services",
            number = "112",
            contact = CrisisLine.Contact.PHONE,
            source = "European Emergency Number Association",
        ),

        // New Zealand.
        CrisisLine(
            country = "New Zealand",
            isoCode = "nz",
            name = "1737 — need to talk?",
            number = "1737",
            contact = CrisisLine.Contact.PHONE_OR_SMS,
            source = "New Zealand Government, https://1737.org.nz/",
        ),

        // Ireland.
        CrisisLine(
            country = "Ireland",
            isoCode = "ie",
            name = "Samaritans Ireland",
            number = "116 123",
            contact = CrisisLine.Contact.PHONE,
            source = "Samaritans Ireland, https://www.samaritans.org/ireland/",
        ),
        CrisisLine(
            country = "Ireland",
            isoCode = "ie",
            name = "Text 50808",
            number = "50808",
            contact = CrisisLine.Contact.SMS,
            source = "Pieta House, https://www.text50808.ie/",
        ),

        // South Africa.
        CrisisLine(
            country = "South Africa",
            isoCode = "za",
            name = "SADAG Mental Health Line",
            number = "0800 456 789",
            contact = CrisisLine.Contact.PHONE,
            source = "South African Depression and Anxiety Group",
        ),

        // Brazil — Centro de Valorização da Vida.
        CrisisLine(
            country = "Brazil",
            isoCode = "br",
            name = "Centro de Valorização da Vida (CVV)",
            number = "188",
            contact = CrisisLine.Contact.PHONE,
            source = "CVV, https://www.cvv.org.br/",
        ),

        // Nigeria.
        CrisisLine(
            country = "Nigeria",
            isoCode = "ng",
            name = "Lagos Mind Helpline",
            number = "+234 802 291 1031",
            contact = CrisisLine.Contact.PHONE,
            source = "Lagos State Government",
        ),

        // Japan.
        CrisisLine(
            country = "Japan",
            isoCode = "jp",
            name = "TELL Lifeline",
            number = "03-5774-0992",
            contact = CrisisLine.Contact.PHONE,
            source = "Tokyo English Lifeline, https://telljp.com/",
        ),
    )

    /**
     * Lines for one country, in the order they are most appropriate to
     * call. Empty list when the country is not in the bundled set — a
     * real "Get help" sheet must never *show nothing*; the caller is
     * responsible for the empty-list fallback (see [getHelpSheet]).
     */
    fun forCountry(iso: String): List<CrisisLine> {
        val code = iso.lowercase()
        return ALL.filter { it.isoCode == code }
    }

    /**
     * Whether the bundled list has at least one line for [iso].
     */
    fun hasLineFor(iso: String): Boolean = forCountry(iso).isNotEmpty()
}
