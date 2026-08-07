package org.mindanchor

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards `strings.xml` against the class of mistake that costs a full CI
 * round trip to diagnose.
 *
 * An unescaped apostrophe in a string value makes aapt reject the *entire*
 * values file with "Can not extract resource from ParsedResource@456df5be"
 * and a path into the build directory — naming neither the offending string
 * nor the file it came from. The XML stays perfectly well-formed, so a
 * well-formedness check sails past it. That happened here, and this test is
 * the thing that would have caught it in seconds.
 *
 * Lint's `UnescapedEntity` also knows about this, but every severity in
 * `lint.xml` is deliberately set to "warning", so lint can report it and
 * cannot stop it. This can.
 */
class StringResourcesTest {

    private val stringsFile: File
        get() {
            // Gradle runs unit tests with the module directory as the working
            // directory, but that is a default rather than a guarantee, and a
            // test that quietly finds no file would pass while checking
            // nothing — the exact failure this class exists to prevent.
            val candidates = listOf(
                "src/main/res/values/strings.xml",
                "app/src/main/res/values/strings.xml",
                "../app/src/main/res/values/strings.xml",
            )
            return candidates.map(::File).firstOrNull { it.isFile }
                ?: error(
                    "strings.xml not found from working directory " +
                        "${File(".").absolutePath}. This test must never " +
                        "silently pass; fix the path.",
                )
        }

    /**
     * Every value in the file, keyed by string name. Uses a real XML parser
     * rather than a regex so that the check sees exactly what aapt will:
     * text content after entity expansion, which is where `&#39;` turns back
     * into a bare apostrophe.
     */
    private fun values(): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(stringsFile)
        val out = mutableMapOf<String, String>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val node = strings.item(i)
            val name = node.attributes?.getNamedItem("name")?.nodeValue ?: continue
            out[name] = node.textContent ?: ""
        }
        val plurals = doc.getElementsByTagName("item")
        for (i in 0 until plurals.length) {
            val node = plurals.item(i)
            val quantity = node.attributes?.getNamedItem("quantity")?.nodeValue ?: continue
            val parent = node.parentNode?.attributes?.getNamedItem("name")?.nodeValue ?: continue
            out["$parent[$quantity]"] = node.textContent ?: ""
        }
        return out
    }

    /** True when [index] in [text] is preceded by an odd run of backslashes. */
    private fun isEscaped(text: String, index: Int): Boolean {
        var slashes = 0
        var i = index - 1
        while (i >= 0 && text[i] == '\\') {
            slashes++
            i--
        }
        return slashes % 2 == 1
    }

    @Test
    fun `the file parses and is not empty`() {
        // Insurance for the check below: if values() ever returns nothing,
        // every other test here would pass vacuously.
        assertTrue("strings.xml yielded no strings", values().size > 50)
    }

    @Test
    fun `no string value contains an unescaped apostrophe`() {
        val offenders = values().filter { (_, value) ->
            value.indices.any { value[it] == '\'' && !isEscaped(value, it) }
        }
        assertTrue(
            "These strings contain an apostrophe that aapt will reject. " +
                "Write it as \\' — note that &#39; does not work, because it " +
                "decodes to a bare apostrophe before aapt sees it:\n" +
                offenders.entries.joinToString("\n") { "  ${it.key}: ${it.value}" },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `no string value contains an unescaped double quote`() {
        // Same rule, same failure: aapt rejects the whole file.
        val offenders = values().filter { (_, value) ->
            value.indices.any { value[it] == '"' && !isEscaped(value, it) }
        }
        assertTrue(
            "These strings contain a double quote that aapt will reject. " +
                "Write it as \\\":\n" +
                offenders.entries.joinToString("\n") { "  ${it.key}: ${it.value}" },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `positional format arguments are numbered`() {
        // A bare %s in a string with more than one argument is a crash at
        // the moment the string is shown, which in this app is often the
        // moment somebody is having a hard time.
        val bare = Regex("""%[-+ #0]*\d*(?:\.\d+)?[sdf]""")
        val offenders = values().filter { (_, value) ->
            bare.findAll(value).any { !it.value.contains('$') } &&
                Regex("""%\d+\$""").containsMatchIn(value)
        }
        assertTrue(
            "These strings mix numbered and unnumbered format arguments:\n" +
                offenders.entries.joinToString("\n") { "  ${it.key}: ${it.value}" },
            offenders.isEmpty(),
        )
    }
}
