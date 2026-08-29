package org.mindanchor.continuity

import java.io.File
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The consent dialog has to describe what the export actually contains.
 *
 * This exists because it once did not. The dialog said "your Journal
 * entries and the structural facts derived from them" while the file had
 * grown to carry morning-measure ratings, the whole research log —
 * including the person's own words about illness and medication changes —
 * a day-by-day record of what they had and had not logged, and device
 * identifiers. Consent given for one dataset does not cover a larger one,
 * and a plaintext file handed to a clinician or an insurer is not
 * recoverable.
 *
 * So every content-bearing field of [ResearchExport] is mapped here to a
 * phrase the disclosure must contain, and a reflection check fails the
 * build when a field is added to neither the map nor the exemption list.
 * The next person to widen the export cannot do it without deciding, in
 * this file, what the person is told.
 */
class ResearchExportDisclosureTest {

    /** The gradle test working directory is the `app` module. */
    private val strings = File("src/main/res/values/strings.xml")

    /**
     * Fields that carry no personal content: the version identifiers and
     * integrity hashes that describe the document rather than the person.
     * `dataDictionary` is exempt because it describes the columns, not the
     * values, and the columns are covered by the fields they belong to.
     */
    private val exemptFromDisclosure = setOf(
        // Identical on every device and derived from no one's data: the
        // catalogue of protocol definitions and the descriptions of how
        // MindAnchor derives what it derives. Naming them in a consent
        // dialog about personal disclosure would pad it without telling
        // the person anything about themselves.
        "protocolRegistry",
        "transformations",
        "ledgerHighWaterCount",
        "dataDictionaryVersion",
        "exportedAt",
        "appVersionCode",
        "appVersionName",
        "contentSha256",
        "ledgerHeadHash",
        "ledgerEventCount",
        "ledgerIntegrity",
        "protocolCatalogSha256",
        "transformationSetVersion",
        "missingDataPolicyVersion",
        "missingDataStatement",
        "dataDictionary",
        "dataDictionarySha256",
    )

    /** What the person must be told about, per content field. */
    private val disclosedAs = mapOf(
        "journalEntries" to "Journal entries in full",
        "contextFacts" to "structural facts derived from them",
        "contextInferences" to "structural facts derived from them",
        "morningMeasures" to "morning check-in ratings",
        "ledgerEvents" to "notes you wrote about illness, medication changes",
        "studyPhases" to "study phases, version identifiers and device identifiers",
        "missingData" to "day-by-day list of what you did and did not record",
        "missingDataWindowStart" to "day-by-day list of what you did and did not record",
        "missingDataWindowThrough" to "day-by-day list of what you did and did not record",
    )

    /**
     * Groups whose members legitimately share one phrase, because one
     * sentence describes them all accurately: the two structural-context
     * lists, and the missing-data report with the window it covers. Every
     * other disclosed field must contribute words no other field supplies.
     */
    private val shareAPhrase = listOf(
        setOf("contextFacts", "contextInferences"),
        setOf("missingData", "missingDataWindowStart", "missingDataWindowThrough"),
    )

    @Test
    fun `each disclosed phrase names something no other phrase supplies`() {
        // Substring, not equality -- "version identifiers" is contained in
        // another field's phrase, so an exact-match guard once waved a
        // field through while telling the person nothing new. Groups are
        // collapsed to one representative phrase first, so a group cannot
        // hide behind its own members, and each group's phrase is then
        // checked against every phrase outside the group.
        shareAPhrase.forEach { group ->
            val phrases = group.map { requireNotNull(disclosedAs[it]) { "`$it` is not disclosed" } }
            assertEquals("the fields in $group must share one phrase", 1, phrases.toSet().size)
        }
        val representatives = shareAPhrase.associate { it.first() to requireNotNull(disclosedAs[it.first()]) }
        val grouped = shareAPhrase.flatten().toSet()
        val checked = disclosedAs.filterKeys { it !in grouped } + representatives
        checked.forEach { (field, phrase) ->
            val others = checked.filterKeys { it != field }.values
            assertTrue(
                "`$field` is disclosed only by \"$phrase\", which another field already supplies",
                others.none { it.contains(phrase) },
            )
        }
    }

    private fun disclosure(): String {
        assertTrue("strings.xml must be readable from the test working directory", strings.isFile)
        val text = strings.readText(Charsets.UTF_8)
        val match = Regex(
            """<string name="continuity_export_research_privacy_body">(.*?)</string>""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(text)
        assertTrue("the export privacy disclosure must exist", match != null)
        return requireNotNull(match).groupValues[1]
    }

    @Test
    fun `every content field of the export is named in the disclosure`() {
        val body = disclosure()
        disclosedAs.forEach { (field, phrase) ->
            assertTrue(
                "the export carries `$field` but the consent dialog never mentions \"$phrase\"",
                body.contains(phrase),
            )
        }
    }

    @Test
    fun `no export field escapes both the disclosure map and the exemption list`() {
        val declared = ResearchExport::class.java.declaredFields
            .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()
        assertEquals(
            "a field was added to ResearchExport without deciding what the person is told about it: " +
                "add it to `disclosedAs` with the phrase the dialog must contain, or to " +
                "`exemptFromDisclosure` if it carries nothing personal",
            declared,
            disclosedAs.keys + exemptFromDisclosure,
        )
    }

    @Test
    fun `the disclosure says the file is unencrypted and readable by anyone who opens it`() {
        val body = disclosure()
        assertTrue("the file's plaintext nature must be stated", body.contains("plain, unencrypted text"))
        assertTrue(
            "the person must be told anyone who opens the file can read it",
            body.contains("Anyone who can open the file can read all of it"),
        )
    }

    @Test
    fun `the disclosure does not promise something the export cannot keep`() {
        val body = disclosure().lowercase()
        // "nothing reads your notes" is true of interpretation and false of
        // distribution; a consent dialog is the wrong place for it.
        listOf("private", "confidential", "secure", "encrypted file", "only you").forEach { claim ->
            assertTrue(
                "the disclosure must not imply confidentiality it cannot provide: '$claim'",
                !body.contains(claim),
            )
        }
    }
}
