package org.mindanchor.corpus

import kotlin.math.ln

/**
 * One retrievable piece of the research corpus.
 *
 * [source] is not decoration. Every sentence the report generates has to
 * be traceable to the passage it came from, so that a claim can be
 * checked rather than believed. A passage with no source is not usable.
 */
data class Passage(
    val id: String,
    val source: String,
    val text: String,
)

/** A passage and how well it matched, highest first. */
data class Hit(val passage: Passage, val score: Double)

/**
 * Finds the passages of the research corpus relevant to a person's own
 * situation.
 *
 * ## Why BM25 and not embeddings
 *
 * The obvious modern answer is a sentence-embedding model and cosine
 * similarity. That would mean a second model on the phone, another few
 * hundred megabytes, another thing to import over USB, and a scoring
 * function nobody can inspect.
 *
 * BM25 is the classical ranking function, it is strong on corpora of this
 * size, it needs no model at all, and — the part that matters here — its
 * score is arithmetic anyone can follow. When the report says "this is
 * why that passage came up", the answer is legible rather than an appeal
 * to a vector space.
 *
 * It is also deterministic, which means the whole retrieval layer is
 * testable without a device, a model, or a network.
 *
 * ## What retrieval is for
 *
 * Not to decide anything. The research is allowed to say *what a signal
 * is* — that HRV reflects vagal tone, that sleep regularity predicts more
 * than duration — and never what it means for this particular person.
 * Retrieval fetches the explanation; the personal model, fitted to
 * somebody's own labels, is the only thing allowed to say the signal
 * moved.
 */
object Retrieval {

    /** Standard BM25 term-frequency saturation. */
    const val K1 = 1.5

    /** Standard BM25 length normalisation. */
    const val B = 0.75

    /**
     * Words carrying no retrieval signal.
     *
     * Deliberately short. An aggressive stop list on a corpus this
     * specialised throws away real query terms — "rest" and "low" are
     * ordinary English and also exactly what somebody is searching for.
     */
    private val STOP = setOf(
        "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
        "has", "have", "in", "is", "it", "its", "of", "on", "or", "that",
        "the", "to", "was", "were", "will", "with",
    )

    /**
     * Splits text into comparable terms.
     *
     * Lowercased, non-letters treated as separators so "HRV," and "hrv"
     * match, single characters dropped as noise. Digits are kept: "0.82"
     * and "5262" are meaningful in this corpus.
     */
    fun tokenise(text: String): List<String> =
        text.lowercase()
            .split(*NON_WORD)
            .filter { it.length > 1 && it !in STOP }

    private val NON_WORD = charArrayOf(
        ' ', '\n', '\t', '\r', ',', '.', ';', ':', '!', '?', '(', ')',
        '[', ']', '{', '}', '"', '\'', '/', '\\', '—', '–', '-', '…',
        '*', '_', '=', '+', '<', '>', '|', '~', '#', '&', '%', '$',
    )

    /**
     * Ranks [passages] against [query], best first.
     *
     * Returns an empty list rather than a bad guess when nothing matches
     * a single query term. A report that pads itself with irrelevant
     * research to look thorough is worse than one that says less.
     */
    fun search(query: String, passages: List<Passage>, limit: Int = 5): List<Hit> {
        if (passages.isEmpty() || limit <= 0) return emptyList()
        val terms = tokenise(query)
        if (terms.isEmpty()) return emptyList()

        val tokenised = passages.map { it to tokenise(it.text) }
        val averageLength = tokenised.sumOf { it.second.size }.toDouble() / tokenised.size
        if (averageLength <= 0.0) return emptyList()

        val documentFrequency = mutableMapOf<String, Int>()
        tokenised.forEach { (_, tokens) ->
            tokens.toSet().forEach { documentFrequency[it] = (documentFrequency[it] ?: 0) + 1 }
        }
        val total = tokenised.size

        return tokenised
            .map { (passage, tokens) ->
                val counts = tokens.groupingBy { it }.eachCount()
                val score = terms.distinct().sumOf { term ->
                    val frequency = counts[term] ?: return@sumOf 0.0
                    val df = documentFrequency[term] ?: return@sumOf 0.0
                    // Standard BM25 idf with the +1 that keeps it positive
                    // even for a term appearing in every passage — without
                    // it a common term contributes a negative score and can
                    // push a genuinely matching passage below one that
                    // matched nothing at all.
                    val idf = ln(1.0 + (total - df + 0.5) / (df + 0.5))
                    val norm = frequency * (K1 + 1) /
                        (frequency + K1 * (1 - B + B * tokens.size / averageLength))
                    idf * norm
                }
                Hit(passage, score)
            }
            .filter { it.score > 0.0 }
            .sortedWith(compareByDescending<Hit> { it.score }.thenBy { it.passage.id })
            .take(limit)
    }
}
