package org.mindanchor.corpus

/**
 * Growing the research corpus from a file on the phone.
 *
 * ## Why this exists at all
 *
 * The bundled corpus is twenty-six passages. That is a seed, not a
 * library, and the thing it feeds — [Retrieval] — gets better the more
 * there is to retrieve from. Everything else about this app can be
 * improved by shipping an update; the research it draws on should not
 * have to wait on one, and should not be limited to what one person
 * thought to include.
 *
 * ## Why the merge, rather than a replacement
 *
 * An import adds to the seed rather than replacing it, so importing one
 * file about sleep does not silently cost every passage about heart-rate
 * variability. Where an imported passage carries an id already in the
 * seed it wins outright — that is the escape hatch for a seed passage
 * somebody thinks is wrong, and being able to correct the built-in
 * research is the point of letting them bring their own.
 *
 * ## What this deliberately does not do
 *
 * It does not read the passages. An imported passage is shown verbatim
 * next to an observation, exactly like a bundled one, and nothing here
 * checks whether it interprets rather than explains. The queries in
 * [org.mindanchor.report.Signal] are what keep the *app* from asking the
 * research what a change means; they cannot keep a file somebody chose
 * from answering a question it was not asked. Whoever imports a file is
 * choosing what they will be shown, and the settings copy says so.
 */
object CorpusImport {

    /**
     * The most passages the corpus will hold.
     *
     * [Retrieval] scores every passage against every query and does it on
     * a phone; five thousand is comfortably more research than anyone
     * will assemble by hand and still fast enough to run through seven
     * signals in a background worker. Past it, the import keeps the first
     * five thousand and says so rather than quietly dropping the tail.
     */
    const val MAX_PASSAGES = 5_000

    /**
     * The most text the import will read from a picked file.
     *
     * A file picker will hand over anything, including a video somebody
     * tapped by mistake. This is the point at which reading further stops
     * being generous and starts being a way to run the phone out of
     * memory on a file that was never a corpus.
     */
    const val MAX_CHARACTERS = 4 * 1024 * 1024

    /**
     * What an import did, in the terms somebody would want it reported.
     *
     * [skippedRows] counts rows that were not comments, not blank, and
     * still could not be read — the number that means "your file is in
     * the wrong format", and the reason [CorpusStore.parseCounting]
     * exists.
     */
    data class Outcome(
        val corpus: List<Passage>,
        val added: Int,
        val replaced: Int,
        val skippedRows: Int,
        val truncated: Boolean,
    ) {
        /** Nothing usable came out of the file. */
        val isEmpty: Boolean get() = added == 0 && replaced == 0
    }

    /**
     * Merges the text of a picked file into [seed].
     *
     * Duplicate ids *within* the imported file resolve last-wins, the
     * same rule as an imported passage beating a seed one: a file is read
     * top to bottom and the later line is the more recent edit.
     */
    fun merge(seed: List<Passage>, importedRaw: String): Outcome {
        val text = if (importedRaw.length > MAX_CHARACTERS) {
            // Cut at a line boundary rather than mid-passage, so the last
            // thing kept is a whole passage instead of half a sentence
            // presented as research.
            importedRaw.substring(0, MAX_CHARACTERS).substringBeforeLast('\n', "")
        } else {
            importedRaw
        }
        val overLength = text.length < importedRaw.length

        val parsed = CorpusStore.parseCounting(text)

        // LinkedHashMap: the seed's order is preserved, a replacement
        // stays where the seed passage was, and genuinely new passages
        // land in the order the file listed them. Retrieval breaks score
        // ties by id rather than by position, so this is for the benefit
        // of anyone reading the merged file, not the ranking.
        val byId = LinkedHashMap<String, Passage>()
        seed.forEach { byId[it.id] = it }
        val seedIds = byId.keys.toSet()

        var added = 0
        var replaced = 0
        var overCapacity = false
        parsed.passages.forEach { passage ->
            when {
                passage.id in byId -> {
                    if (passage.id in seedIds && byId[passage.id] != passage) replaced++
                    byId[passage.id] = passage
                }
                byId.size >= MAX_PASSAGES -> overCapacity = true
                else -> {
                    byId[passage.id] = passage
                    added++
                }
            }
        }

        return Outcome(
            corpus = byId.values.toList(),
            added = added,
            replaced = replaced,
            skippedRows = parsed.skippedRows,
            truncated = overLength || overCapacity,
        )
    }

    /**
     * Renders a merged corpus back to the same tab-separated format it was
     * read from, so what gets stored is a file somebody could open, read
     * and hand to somebody else.
     *
     * A passage whose text contains a tab or a newline is dropped here
     * rather than written out mangled: it would be silently torn in two
     * on the next read, and a corpus that changes shape every time it is
     * saved is worse than one passage short.
     */
    fun render(corpus: List<Passage>): String =
        corpus
            .filterNot { it.id.hasSeparators() || it.source.hasSeparators() || it.text.hasNewline() }
            .joinToString("\n") { "${it.id}\t${it.source}\t${it.text}" }

    private fun String.hasSeparators() = '\t' in this || '\n' in this || '\r' in this

    private fun String.hasNewline() = '\n' in this || '\r' in this
}
