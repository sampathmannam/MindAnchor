package org.mindanchor.corpus

import android.content.Context

/**
 * Loads the research corpus — `assets/corpus.tsv` — into the [Passage]s
 * [Retrieval] searches.
 *
 * ## Why the parser is a free function of a plain [String]
 *
 * Everything that can go wrong here — a stray tab, a half-written line, a
 * comment that forgot its `#` — is a text problem, not an Android
 * problem. Keeping [parse] independent of [android.content.Context] means
 * every one of those cases is a fast JVM test rather than something that
 * only shows up once a phone is involved.
 *
 * ## Why loading never throws
 *
 * The one caller that matters most, [org.mindanchor.report.ReportWorker],
 * runs unattended at night with nobody watching for a crash. A missing
 * asset, a permission hiccup, anything at all reading the file — none of
 * it is grounds for taking the worker down; it is grounds for reporting
 * nothing, the same as a night with too little history to say anything
 * about. See [org.mindanchor.grayscale.Grayscale] for the same
 * "never throws" convention used for the same reason.
 */
object CorpusStore {

    private const val ASSET_PATH = "corpus.tsv"

    /**
     * Cached after the first successful load.
     *
     * The corpus is bundled with the app and only ever changes when the
     * app updates or a future file-picker import replaces it — never
     * between two runs of the nightly worker — so re-reading and
     * re-parsing forty-odd lines every single night buys nothing.
     * [Volatile] because [load] can legitimately be called from more than
     * one coroutine dispatcher (a settings screen and a background
     * worker, say) and the cache is only ever replaced, never mutated in
     * place.
     */
    @Volatile
    private var cached: List<Passage>? = null

    /**
     * Parses one corpus.tsv's worth of text into [Passage]s.
     *
     * Format: `id<TAB>source<TAB>text`, one passage per line. Blank lines
     * and lines starting with `#` (after trimming leading whitespace) are
     * comments and are skipped, not errors. A line with fewer than three
     * tab-separated fields, or with a blank id, source, or text once
     * trimmed, is silently skipped rather than thrown on: the corpus is
     * optional research, and one malformed row must cost that one
     * passage, never the whole load.
     *
     * [text] is everything after the second tab, tabs and all — a
     * passage is allowed to contain further tab characters without
     * being torn apart, though the shipped corpus never does.
     */
    fun parse(raw: String): List<Passage> =
        raw.lineSequence().mapNotNull(::parseLine).toList()

    private fun parseLine(rawLine: String): Passage? {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) return null
        val parts = line.split('\t', limit = 3)
        if (parts.size < 3) return null
        val id = parts[0].trim()
        val source = parts[1].trim()
        val text = parts[2].trim()
        if (id.isEmpty() || source.isEmpty() || text.isEmpty()) return null
        return Passage(id = id, source = source, text = text)
    }

    /**
     * Loads and caches the corpus from assets, or an empty list on any
     * failure at all — missing asset, IO error, anything. An empty corpus
     * is not a special case downstream: [Retrieval.search] already
     * returns nothing for an empty passage list, and
     * [org.mindanchor.report.ReportComposer] already treats "no research
     * on file" as ordinary.
     */
    fun load(context: Context): List<Passage> {
        cached?.let { return it }
        val text = runCatching {
            context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        }.getOrNull()
        val passages = text?.let(::parse) ?: emptyList()
        cached = passages
        return passages
    }
}
