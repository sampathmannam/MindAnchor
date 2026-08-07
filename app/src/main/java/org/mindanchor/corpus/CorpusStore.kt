package org.mindanchor.corpus

import android.content.Context
import android.net.Uri
import java.io.File

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
 * The one caller that matters most, [org.mindanchor.report.ReportScheduler],
 * runs unattended at night with nobody watching for a crash. A missing
 * asset, a permission hiccup, anything at all reading the file — none of
 * it is grounds for taking the nightly run down; it is grounds for reporting
 * nothing, the same as a night with too little history to say anything
 * about. See [org.mindanchor.grayscale.Grayscale] for the same
 * "never throws" convention used for the same reason.
 */
object CorpusStore {

    private const val ASSET_PATH = "corpus.tsv"

    private const val IMPORTED_NAME = "corpus-imported.tsv"

    /**
     * Cached after the first successful load.
     *
     * The corpus is bundled with the app and only ever changes when the
     * app updates or a future file-picker import replaces it — never
     * between two runs of the nightly report — so re-reading and
     * re-parsing forty-odd lines every single night buys nothing.
     * [Volatile] because [load] can legitimately be called from more than
     * one coroutine dispatcher (a settings screen and a background
     * alarm, say) and the cache is only ever replaced, never mutated in
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
    fun parse(raw: String): List<Passage> = parseCounting(raw).passages

    /**
     * [parse], but keeping count of the rows it threw away.
     *
     * Silence is right for the bundled corpus, which is known-good and
     * loaded on a background thread at 3am. It is wrong for a file
     * somebody has just picked off their own phone: "imported 40
     * passages" and "imported 40 passages, ignored 300 rows I could not
     * read" are very different pieces of news, and only one of them
     * tells them their file is in the wrong format.
     */
    fun parseCounting(raw: String): Parsed {
        val passages = mutableListOf<Passage>()
        var skipped = 0
        raw.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val passage = parseLine(rawLine)
            if (passage == null) skipped++ else passages += passage
        }
        return Parsed(passages, skipped)
    }

    /** Passages read, and rows that looked like passages but were not. */
    data class Parsed(val passages: List<Passage>, val skippedRows: Int)

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
     * Loads and caches the corpus — the bundled seed, plus whatever has
     * been imported on top of it — or an empty list on any failure at
     * all: missing asset, IO error, anything. An empty corpus is not a
     * special case downstream: [Retrieval.search] already returns nothing
     * for an empty passage list, and [org.mindanchor.report.ReportComposer]
     * already treats "no research on file" as ordinary.
     *
     * The imported file failing to read costs the imported passages and
     * nothing else. Falling back to the seed is always better than
     * falling back to nothing, and an import gone bad must never be able
     * to take the built-in research down with it.
     */
    fun load(context: Context): List<Passage> {
        cached?.let { return it }
        val seed = seed(context)
        val imported = runCatching {
            importedFile(context).takeIf { it.exists() }?.readText()
        }.getOrNull()
        val passages = if (imported == null) seed else CorpusImport.merge(seed, imported).corpus
        cached = passages
        return passages
    }

    /** The corpus that ships with the app, and nothing else. */
    private fun seed(context: Context): List<Passage> = runCatching {
        context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
    }.getOrNull()?.let(::parse).orEmpty()

    /** Where an imported corpus lives: app-private, so nothing else can read it. */
    internal fun importedFile(context: Context) = File(context.filesDir, IMPORTED_NAME)

    /**
     * Stores everything in [merged] that is not already in the bundled
     * seed, and drops the cache.
     *
     * ## Why the difference rather than the whole thing
     *
     * Writing the merged corpus entire would be simpler and would make
     * the stored file self-contained. It would also freeze the seed: an
     * app update that corrected a bundled passage would be shadowed
     * forever by the copy written on the day somebody first imported
     * anything, and there would be no way to tell from the outside that
     * it had happened.
     *
     * Storing the difference keeps both halves live. Unchanged seed
     * passages keep tracking the app; genuinely new ones, and deliberate
     * corrections to seed passages — which by definition differ from the
     * seed and so survive this filter — keep tracking the person. The
     * one thing given up is that a correction to a passage the app later
     * rewords stays a correction, which is the right way round: it was
     * chosen, and the seed's version was not.
     */
    fun saveImported(context: Context, merged: List<Passage>): Boolean =
        runCatching {
            val bundled = seed(context).toSet()
            val ours = merged.filterNot { it in bundled }
            val file = importedFile(context)
            // Nothing of our own left is the same state as never having
            // imported, and it should look like it — an empty file that
            // still counts as "imported" leaves a "back to the built-in
            // set" button offering to undo nothing.
            if (ours.isEmpty()) file.delete() else file.writeText(CorpusImport.render(ours))
            cached = null
            true
        }.getOrDefault(false)

    /** Back to the bundled seed alone. */
    fun clearImported(context: Context): Boolean =
        runCatching {
            val file = importedFile(context)
            if (file.exists()) file.delete()
            cached = null
            true
        }.getOrDefault(false)

    /** Whether anything of the person's own is on file on top of the seed. */
    fun hasImported(context: Context): Boolean =
        runCatching { importedFile(context).length() > 0L }.getOrDefault(false)

    /**
     * Reads a file the person picked, as text.
     *
     * Returns null on anything at all — a revoked permission, a file that
     * has moved, a picker that handed back something unreadable. The
     * caller reports "could not read that file", which is the whole truth
     * available and better than a crash on top of a bad tap.
     */
    fun readPicked(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            // Bounded rather than readText(): the picker will hand over
            // whatever was tapped, including a film, and this runs on the
            // way to holding the whole thing in memory.
            val buffer = CharArray(CorpusImport.MAX_CHARACTERS)
            val builder = StringBuilder()
            var remaining = CorpusImport.MAX_CHARACTERS
            while (remaining > 0) {
                val read = reader.read(buffer, 0, minOf(buffer.size, remaining))
                if (read <= 0) break
                builder.appendRange(buffer, 0, read)
                remaining -= read
            }
            builder.toString()
        }
    }.getOrNull()
}
