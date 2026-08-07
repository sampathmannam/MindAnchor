package org.mindanchor.report

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mindanchor.corpus.Passage

private val Context.dataStore by preferencesDataStore(name = "report")

/**
 * A stored [Report] together with the paragraph, if any, a
 * [org.mindanchor.narrate.Narrator] wrote about it.
 *
 * Kept apart from [Report] itself rather than adding a field there.
 * [org.mindanchor.report.ReportComposer] never produces a narration — a
 * narrator only ever runs afterwards, on the finished report — so every
 * caller that builds a bare [Report], test or otherwise, would either
 * have to thread a null through for a field it has no opinion about, or
 * get a default that invites a future caller to forget the field exists.
 * [StoredReport] exists at the one layer that actually knows about
 * narration: storage.
 */
data class StoredReport(val report: Report, val narration: String?)

/**
 * Turns a [StoredReport] into one line-oriented block of text and back.
 *
 * Same discipline as [org.mindanchor.friction.GateLedger] and
 * [org.mindanchor.model.MomentLedger]: plain tab-separated lines rather
 * than JSON, because this is always read and written as a whole file
 * rather than queried, and a corrupt byte must cost this one report,
 * never the screen trying to show it.
 *
 * ## Shape
 *
 * One header line — `REPORT<TAB>day<TAB>notYetKnown` — optionally
 * followed by one `NARRATION<TAB>text` line, then zero or more section
 * blocks. Each block is one `SECTION` line describing an [Observation],
 * followed by zero or more `PASSAGE` lines naming the research it drew
 * on. A `PASSAGE` line only ever attaches to the `SECTION` line
 * immediately above it in the file.
 *
 * The `NARRATION` line is written immediately after the header and only
 * ever read there — a line shaped like one appearing after the first
 * `SECTION` is treated as an unrecognised record and ignored, the same
 * as it would be for any format this version has never heard of. It is
 * entirely optional: a report saved before narration existed, or one a
 * [org.mindanchor.narrate.Narrator] declined to write anything about, has
 * no `NARRATION` line at all, and decodes exactly as it always did, with
 * [StoredReport.narration] simply null.
 *
 * A `SECTION` line that fails to parse — an unrecognised [Signal] name
 * from some future version, say — drops only that section, and any
 * `PASSAGE` lines under it, without disturbing the sections before or
 * after it. There is deliberately no single corrupt line that can cost a
 * whole night's report.
 */
object ReportLedger {

    private const val HEADER = "REPORT"
    private const val NARRATION = "NARRATION"
    private const val SECTION = "SECTION"
    private const val PASSAGE = "PASSAGE"
    private const val NARRATION_PREFIX = "$NARRATION\t"
    private const val SECTION_PREFIX = "$SECTION\t"
    private const val PASSAGE_PREFIX = "$PASSAGE\t"

    fun encode(stored: StoredReport): String {
        val report = stored.report
        val lines = mutableListOf<String>()
        lines += listOf(HEADER, report.day, report.notYetKnown.joinToString(",") { it.name })
            .joinToString("\t")
        // Immediately after the header, never anywhere else — see the
        // class KDoc for why decode() only ever looks for it there.
        //
        // Flattened onto one line first. This format is line-oriented, so
        // a paragraph containing a newline would be written as two lines,
        // and the second would come back as an unrecognised record and be
        // dropped — the narration would silently lose its tail, which is
        // the worst of the available failures because nothing downstream
        // could tell half a paragraph from a whole one. A model asked for
        // one short paragraph should not produce newlines anyway; this is
        // the guarantee rather than the hope.
        stored.narration?.let { lines += "$NARRATION\t${flatten(it)}" }
        report.sections.forEach { section ->
            val observation = section.observation
            lines += listOf(
                SECTION,
                observation.signal.name,
                observation.direction.name,
                observation.today.toString(),
                observation.usual.toString(),
            ).joinToString("\t")
            section.passages.forEach { passage ->
                lines += listOf(PASSAGE, passage.id, passage.source, passage.text).joinToString("\t")
            }
        }
        return lines.joinToString("\n")
    }

    /**
     * Returns null for anything that is not recognisably a report at
     * all — empty input, or a first line that is not a `REPORT` header
     * with a real day on it. Below that, decoding is as forgiving as
     * possible: a single bad section or passage is dropped, never the
     * whole thing.
     */
    fun decode(raw: String): StoredReport? {
        val lines = raw.lineSequence().iterator()
        if (!lines.hasNext()) return null
        val headerParts = lines.next().split('\t')
        if (headerParts.size < 2 || headerParts[0] != HEADER) return null
        val day = headerParts[1]
        if (day.isBlank()) return null
        val notYetKnown = headerParts.getOrNull(2).orEmpty()
            .split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { name -> runCatching { Signal.valueOf(name) }.getOrNull() }

        val sections = mutableListOf<ReportSection>()
        var pendingObservation: Observation? = null
        var pendingPassages = mutableListOf<Passage>()
        var narration: String? = null
        var sawSection = false

        fun flush() {
            pendingObservation?.let { sections += ReportSection(it, pendingPassages.toList()) }
        }

        while (lines.hasNext()) {
            val line = lines.next()
            when {
                // Only recognised before the first SECTION — an old report
                // with no NARRATION line at all never enters this branch
                // and simply leaves narration null, which is exactly the
                // backward-compatible reading it should get.
                line.startsWith(NARRATION_PREFIX) && !sawSection -> {
                    narration = line.removePrefix(NARRATION_PREFIX)
                }
                line.startsWith(SECTION_PREFIX) -> {
                    sawSection = true
                    flush()
                    pendingPassages = mutableListOf()
                    pendingObservation = decodeObservation(line.removePrefix(SECTION_PREFIX))
                }
                line.startsWith(PASSAGE_PREFIX) && pendingObservation != null -> {
                    decodePassage(line.removePrefix(PASSAGE_PREFIX))?.let { pendingPassages += it }
                }
                // A blank line, a passage with nothing to attach to (a
                // corrupt section above it, or none at all), or some
                // future record type this version has never heard of —
                // none of it is this format's business.
                else -> Unit
            }
        }
        flush()

        return StoredReport(
            report = Report(day = day, sections = sections, notYetKnown = notYetKnown),
            narration = narration,
        )
    }

    /**
     * One paragraph on one line: newlines become spaces, runs of
     * whitespace collapse, and the result is trimmed.
     *
     * A tab is left alone deliberately — the narration is read back with
     * `removePrefix`, so everything after the first tab is the text, tabs
     * and all, exactly as passage text is.
     */
    private fun flatten(text: String): String =
        text.replace('\n', ' ').replace('\r', ' ').replace(Regex(" +"), " ").trim()

    private fun decodeObservation(rest: String): Observation? {
        val parts = rest.split('\t')
        if (parts.size < 4) return null
        val signal = runCatching { Signal.valueOf(parts[0]) }.getOrNull() ?: return null
        val direction = runCatching { Direction.valueOf(parts[1]) }.getOrNull() ?: return null
        val today = parts[2].toDoubleOrNull() ?: return null
        val usual = parts[3].toDoubleOrNull() ?: return null
        return Observation(signal = signal, direction = direction, today = today, usual = usual)
    }

    private fun decodePassage(rest: String): Passage? {
        val parts = rest.split('\t', limit = 3)
        if (parts.size < 3) return null
        val (id, source, text) = parts
        if (id.isBlank() || source.isBlank() || text.isBlank()) return null
        return Passage(id = id, source = source, text = text)
    }
}

/**
 * Holds exactly one thing: the most recent nightly [Report], the day it
 * was actually generated, and whether the feature is switched on at all.
 *
 * Deliberately not a history. [ReportScheduler] overwrites the single stored
 * report every time it runs, and nothing here ever accumulates a log of
 * past nights — a growing archive of "what was unusual about you, night
 * by night" is exactly the kind of record this app has chosen not to
 * keep. Anyone wanting more than last night's look already has it: the
 * report is rebuilt from [org.mindanchor.vitals.HealthConnectSource] and
 * [org.mindanchor.model.MomentStore], neither of which this deletes from.
 */
class ReportStore(private val context: Context) {

    private val enabledKey = booleanPreferencesKey("report_enabled")
    private val reportKey = stringPreferencesKey("latest_report")
    private val generatedDayKey = stringPreferencesKey("report_generated_day")

    /** Off until asked for, like everything else in this app. */
    val enabled: Flow<Boolean> = context.dataStore.data.map { it[enabledKey] ?: false }

    suspend fun setEnabled(value: Boolean) {
        context.dataStore.edit { it[enabledKey] = value }
    }

    /**
     * The most recent report, together with its narration if it has one,
     * or null when none has ever been generated — or the stored one no
     * longer parses, which this treats exactly the same way, as though
     * nothing had been generated yet, never as a crash.
     */
    val stored: Flow<StoredReport?> = context.dataStore.data.map { prefs ->
        prefs[reportKey]?.let { ReportLedger.decode(it) }
    }

    /**
     * The calendar day [ReportScheduler] last actually ran, distinct from
     * [Report.day] (the night the report is *about*, one day earlier in
     * the ordinary case). Kept mainly so a future screen could notice the
     * worker has stopped running at all — a stale [Report.day] with no
     * accompanying explanation reads as "nothing happened last night"
     * rather than "the worker hasn't run in three weeks", and those are
     * very different things to tell somebody.
     */
    val generatedDay: Flow<String?> = context.dataStore.data.map { it[generatedDayKey] }

    /**
     * Overwrites the single stored report. There is never more than one.
     *
     * [narration] is whatever a [org.mindanchor.narrate.Narrator] wrote —
     * or null, which is the ordinary outcome; see that interface's KDoc.
     */
    suspend fun save(report: Report, narration: String?, generatedDay: String) {
        context.dataStore.edit { prefs ->
            prefs[reportKey] = ReportLedger.encode(StoredReport(report, narration))
            prefs[generatedDayKey] = generatedDay
        }
    }
}
